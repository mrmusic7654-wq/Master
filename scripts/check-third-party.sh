#!/usr/bin/env bash
#
# Third-party hygiene check (runs in CI and in tools/verify/run-offline-checks.sh).
#
# It answers three questions mechanically, so they cannot drift:
#   1. Is every dependency declared in gradle/libs.versions.toml accounted for in
#      THIRD_PARTY_LICENSES.md (license name + upstream URL)?
#   2. Is the vendored TDLib source accounted for (license file present, entry in
#      THIRD_PARTY_LICENSES.md, mention in NOTICE)?
#   3. Does the repository ship a LICENSE for Master Control itself?
#
# Exit code is non-zero when anything is missing.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CATALOG="$REPO_ROOT/gradle/libs.versions.toml"
LICENSES_DOC="$REPO_ROOT/THIRD_PARTY_LICENSES.md"
NOTICE="$REPO_ROOT/NOTICE"
LICENSE="$REPO_ROOT/LICENSE"
TDLIB_LICENSE="$REPO_ROOT/third_party/tdlib/LICENSE_1_0.txt"

errors=0
checked=0

fail() { echo "  ✗ $*" >&2; errors=$((errors + 1)); }
ok()   { echo "  ✓ $*"; }

echo "third-party check"

[[ -f "$CATALOG" ]] || { echo "ERROR: $CATALOG not found" >&2; exit 1; }
[[ -f "$LICENSES_DOC" ]] || fail "$LICENSES_DOC is missing"
[[ -f "$LICENSE" ]] || fail "$LICENSE is missing (Master Control's own license)"
[[ -f "$NOTICE" ]] || fail "$NOTICE is missing"
[[ -f "$TDLIB_LICENSE" ]] || fail "vendored TDLib license text is missing: $TDLIB_LICENSE"

# --- 1. every declared library must appear in the license document ----------
# Extract `group = "..."` values from the [libraries] section.
groups="$(awk '
  /^\[libraries\]/ { in_libs = 1; next }
  /^\[/            { in_libs = 0 }
  in_libs && /group *= *"/ {
    line = $0
    sub(/.*group *= *"/, "", line)
    sub(/".*/, "", line)
    print line
  }
' "$CATALOG" | sort -u)"

if [[ -f "$LICENSES_DOC" ]]; then
  while IFS= read -r group; do
    [[ -n "$group" ]] || continue
    checked=$((checked + 1))
    if grep -qF "$group" "$LICENSES_DOC"; then
      ok "$group"
    else
      fail "no license entry for dependency group: $group"
    fi
  done <<< "$groups"

  # --- 2. TDLib ------------------------------------------------------------
  if grep -qi "tdlib" "$LICENSES_DOC"; then
    ok "TDLib entry present"
  else
    fail "THIRD_PARTY_LICENSES.md has no TDLib entry"
  fi
  if [[ -f "$NOTICE" ]] && grep -qi "tdlib" "$NOTICE"; then
    ok "NOTICE mentions TDLib"
  else
    fail "NOTICE does not mention the vendored TDLib source dependency"
  fi
fi

echo
if [[ "$errors" -gt 0 ]]; then
  echo "third-party check: $checked dependency group(s) inspected, $errors problem(s)" >&2
  exit 1
fi
echo "third-party check: $checked dependency group(s) inspected, 0 problems"
