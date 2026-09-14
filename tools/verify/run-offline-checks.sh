#!/usr/bin/env bash
#
# Master Control — offline verification harness.
#
# Runs the checks that do NOT require the Android SDK, Gradle or Google Maven:
#
#   1. Kotlin syntax verification of every .kt file in the repository
#      (real Kotlin front-end PSI parser, no type resolution).
#   2. Compile + execute the pure-JVM :domain and :core:common unit tests.
#   3. Structure/dependency/policy check (tools/verify/structure-check.py):
#      module boundaries, banned patterns, Hilt wiring, unused imports.
#   4. Third-party license hygiene (scripts/check-third-party.sh).
#
# The authoritative build remains `./gradlew` (see BUILD.md). This harness is
# for environments without repository access to Google Maven / Maven Central —
# for example sealed CI runners, air-gapped review machines, or sandboxes.
#
# Requirements (either on PATH or via environment variables):
#   JAVA_BIN    java executable            (default: java)
#   KOTLIN_LIB  Kotlin compiler lib dir    (default: $KOTLIN_HOME/lib, else kotlinc's ../lib)
#
# Offline bootstrap of both, using only npm + PyPI:
#   npm install --prefix /tmp/kc kotlin-compiler@2.4.20
#   KOTLIN_LIB=/tmp/kc/node_modules/kotlin-compiler/lib
#   pip download jdk4py -d /tmp/jdk --no-deps && python3 -m zipfile -e /tmp/jdk/jdk4py-*.whl /tmp/jdk/
#   chmod +x /tmp/jdk/jdk4py/java-runtime/bin/* && JAVA_BIN=/tmp/jdk/jdk4py/java-runtime/bin/java
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

JAVA_BIN="${JAVA_BIN:-java}"
if [[ -z "${KOTLIN_LIB:-}" ]]; then
  if [[ -n "${KOTLIN_HOME:-}" ]]; then
    KOTLIN_LIB="$KOTLIN_HOME/lib"
  elif command -v kotlinc >/dev/null 2>&1; then
    KOTLIN_LIB="$(dirname "$(dirname "$(readlink -f "$(command -v kotlinc)")")")/lib"
  else
    echo "ERROR: no Kotlin compiler found. Set KOTLIN_LIB or KOTLIN_HOME (see tools/verify/README.md)." >&2
    exit 2
  fi
fi

COMPILER_JAR="$KOTLIN_LIB/kotlin-compiler.jar"
STDLIB_JAR="$KOTLIN_LIB/kotlin-stdlib.jar"
COROUTINES_JAR="$(ls "$KOTLIN_LIB"/kotlinx-coroutines-core-jvm*.jar 2>/dev/null | head -1 || true)"
[[ -f "$COMPILER_JAR" ]] || { echo "ERROR: $COMPILER_JAR not found" >&2; exit 2; }

OUT_DIR="${OUT_DIR:-build/offline-verify}"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/harness" "$OUT_DIR/domain" "$OUT_DIR/tests"

JAVA_OPTS=(-Dfile.encoding=UTF-8 -Xmx1g)

kotlinc() {
  "$JAVA_BIN" "${JAVA_OPTS[@]}" -cp "$COMPILER_JAR" \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -nowarn "$@"
}

fail=0

echo "════════════════════════════════════════════════════════════"
echo " Master Control offline verification"
echo "   java     : $("$JAVA_BIN" -version 2>&1 | head -1)"
echo "   kotlin   : $KOTLIN_LIB"
echo "════════════════════════════════════════════════════════════"

# ---------------------------------------------------------------------------
# 1. Harness (syntax checker + test runner)
# ---------------------------------------------------------------------------
echo
echo "[1/5] building verification harness"
kotlinc -cp "$COMPILER_JAR" -d "$OUT_DIR/harness" \
  tools/verify/harness/SyntaxCheck.kt tools/verify/harness/TestRunner.kt

# ---------------------------------------------------------------------------
# 2. Syntax check of every Kotlin source file in the repository
# ---------------------------------------------------------------------------
echo
echo "[2/5] kotlin syntax check (all modules)"
if ! "$JAVA_BIN" "${JAVA_OPTS[@]}" \
      -cp "$COMPILER_JAR:$STDLIB_JAR:$OUT_DIR/harness" \
      mastercontrol.verify.SyntaxCheckKt . ; then
  fail=1
fi

# ---------------------------------------------------------------------------
# 3. Pure-JVM modules: compile + run their unit tests for real
#
# Files that depend on the kotlinx-serialization runtime are excluded: that
# artifact is not part of the Kotlin compiler distribution. They are compiled
# and executed by Gradle (`./gradlew test`) and are listed here so the exclusion
# is explicit and auditable.
# ---------------------------------------------------------------------------
echo
echo "[3/5] pure-JVM modules: compile + unit tests"

if [[ -z "$COROUTINES_JAR" ]]; then
  echo "  SKIP: kotlinx-coroutines-core-jvm.jar not present in $KOTLIN_LIB" >&2
else
  run_jvm_module() {
    local name="$1"; shift
    local out_main="$OUT_DIR/$name-main"
    local out_test="$OUT_DIR/$name-test"
    mkdir -p "$out_main" "$out_test"

    local main_src test_src
    main_src=$(find "$1" -name '*.kt' 2>/dev/null | grep -v -F -f "$2" | sort || true)
    test_src=$(find "$3" -name '*.kt' 2>/dev/null | grep -v -F -f "$2" | sort || true)
    [[ -n "$main_src" ]] || { echo "  SKIP $name: no sources"; return 0; }

    echo
    echo "  ── module $name"
    # shellcheck disable=SC2086
    kotlinc -cp "$STDLIB_JAR:$COROUTINES_JAR" -d "$out_main" \
      tools/verify/stubs/JavaxInjectStub.kt $main_src || { fail=1; return 1; }
    [[ -n "$test_src" ]] || { echo "     (no JVM tests)"; return 0; }
    # shellcheck disable=SC2086
    kotlinc -cp "$STDLIB_JAR:$COROUTINES_JAR:$out_main" -d "$out_test" \
      tools/verify/stubs/JUnitStub.kt tools/verify/harness/TestRunner.kt $test_src || { fail=1; return 1; }
    "$JAVA_BIN" "${JAVA_OPTS[@]}" \
      -cp "$STDLIB_JAR:$COROUTINES_JAR:$out_main:$out_test" \
      mastercontrol.verify.TestRunnerKt "$out_test" || fail=1
  }

  # exclusion lists (one substring per line, matched against file paths)
  cat > "$OUT_DIR/exclude-domain.txt" <<'EXCL'
CatalogTransfer.kt
BackupUseCases.kt
CatalogBackupSerializationTest.kt
EXCL
  : > "$OUT_DIR/exclude-none.txt"

  run_jvm_module "domain"   domain/src/main       "$OUT_DIR/exclude-domain.txt" domain/src/test
  run_jvm_module "common"   core/common/src/main  "$OUT_DIR/exclude-none.txt"   core/common/src/test
fi

# ---------------------------------------------------------------------------
# 4. Structure / dependency / policy check
#
# Verifies module boundaries (imports resolve to declared dependencies), Hilt
# wiring, absence of banned placeholders, required project files and unused
# imports. Needs only python3.
# ---------------------------------------------------------------------------
echo
echo "[4/5] structure check"
if command -v python3 >/dev/null 2>&1; then
  if ! python3 tools/verify/structure-check.py; then fail=1; fi
else
  echo "  SKIP: python3 not available" >&2
fi

# ---------------------------------------------------------------------------
# 5. Third-party license hygiene
# ---------------------------------------------------------------------------
echo
echo "[5/5] third-party license check"
if ! ./scripts/check-third-party.sh; then fail=1; fi

echo
if [[ $fail -ne 0 ]]; then
  echo "OFFLINE VERIFICATION: FAILED"
  exit 1
fi
echo "OFFLINE VERIFICATION: PASSED"
