# Offline verification tools

This directory contains the checks that run **without** the Android SDK, Gradle or
access to Google Maven / Maven Central. They exist so the project stays reviewable
and verifiable in sealed environments — air-gapped machines, restricted CI
runners, or a reviewer who only has a shell.

The authoritative build is still `./gradlew` (see [`BUILD.md`](../../BUILD.md)).
Nothing here replaces a real compile of the Android and Compose modules.

---

## What runs

| Step | Tool | Coverage |
| --- | --- | --- |
| 1 | `harness/SyntaxCheck.kt` | Parses every `.kt` file with the real Kotlin front-end (PSI). Catches syntax errors, unbalanced braces, malformed annotations. **No type resolution.** |
| 2 | `harness/TestRunner.kt` | Compiles and executes the pure-JVM test suites: `:domain` and `:core:common`. Files that need artifacts outside the Kotlin compiler distribution (kotlinx-serialization runtime, Room, androidx.sqlite) are excluded by an explicit, auditable list in `run-offline-checks.sh`. |
| 3 | `structure-check.py` | Module boundaries, imports resolvable through declared dependencies, **missing imports of project types**, Hilt wiring, banned placeholders, version-catalog discipline, curated icon allowlist, required project files, unused imports. Python 3 only. |
| 4 | `../../scripts/check-third-party.sh` | Every dependency group declared in `gradle/libs.versions.toml` is documented in `THIRD_PARTY_LICENSES.md`; TDLib vendoring and `NOTICE` are verified. |

Run everything:

```bash
tools/verify/run-offline-checks.sh
```

## Requirements

* `python3` (3.10+) — step 3 and 4.
* A JDK 17+ `java` and a Kotlin compiler library directory — steps 1 and 2.
  Point the script at them with `JAVA_BIN` and `KOTLIN_LIB` (or `KOTLIN_HOME`).

Bootstrap both from npm + PyPI only, with no Maven access:

```bash
npm install --prefix /tmp/kc kotlin-compiler@2.4.20
export KOTLIN_LIB=/tmp/kc/node_modules/kotlin-compiler/lib

pip download jdk4py -d /tmp/jdk --no-deps
python3 -m zipfile -e /tmp/jdk/jdk4py-*.whl /tmp/jdk/
chmod +x /tmp/jdk/java-runtime/bin/*
export JAVA_BIN=/tmp/jdk/java-runtime/bin/java

tools/verify/run-offline-checks.sh
```

Exit code is `0` only when every step passed; step 2 exits with code `2` when the
toolchain itself is missing, so a broken environment never looks like a pass.

## structure-check.py

```bash
python3 tools/verify/structure-check.py                  # report, exit 1 on error
python3 tools/verify/structure-check.py --fix-unused-imports   # maintenance mode
```

What it enforces:

* every module in `settings.gradle.kts` exists with a build file and (for Android
  modules) a manifest; every `project(":x")` edge resolves;
* every `import com.mastercontrol.app.*` resolves to a type declared in a module
  reachable through the declared `api`/`implementation` graph;
* every reference to a project type (`AppError.ValidationError`, `McAction(…)`,
  `: StorageChannel`) is actually imported or in the same package — this is the
  class of bug the syntax check cannot see and the Android modules cannot be
  type-checked for offline;
* Hilt: every `@Binds`/`@Provides` target exists, every injected interface has a
  binding, every `@HiltViewModel` dependency is satisfiable;
* banned patterns (`TODO`, `FIXME`, `XXX`, `NotImplementedError`, "not
  implemented", fake-progress/mock-repository/sample-data wording);
* no hard-coded dependency versions outside `gradle/libs.versions.toml`, and no
  unknown catalog aliases;
* `Icons.<Style>.<Name>` usages appear in [`icons-allowlist.txt`](icons-allowlist.txt);
* required project files exist (docs, `LICENSE`, `NOTICE`, CI workflow, manifest,
  `scripts/build-tdlib.sh`);
* unused imports (warnings, auto-fixable).

### Known limits

* No type checking: arity mismatches, generic errors and Compose API misuse are
  not caught. Grep call sites when you change a signature.
* Composables in Android modules are never compiled here.
* The "declared types" index is built from `main` source sets; test-only helpers
  are not indexed.
* `stubs/` contains the minimal `javax.inject` and JUnit surface the harness needs
  to compile `:domain` and `:core:common`. They are **not** part of the app and
  are never packaged.

## Icons allowlist

`icons-allowlist.txt` lists every Material icon the app may reference, grouped by
purpose. The extended icon set is large; keeping the list explicit means an icon
can only enter the app deliberately, and the reason is written down next to it.
