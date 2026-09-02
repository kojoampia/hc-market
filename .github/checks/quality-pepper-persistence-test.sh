#!/usr/bin/env bash
# ==============================================================================
#  quality/startup.sh must never mint a second erasure pepper — decisions.md D35
#
#  THE REGRESSION THIS CATCHES, which is a data defect and not a script defect:
#
#      HC_PRIVACY_PEPPER=… ./startup.sh --local     # env wins, nothing is written down
#      ./startup.sh --local                         # the file is still absent, so it GENERATES one
#
#  The second run hands a brand-new random pepper to a stack whose erased_subject rows were written
#  under the first one. Nothing turns red. Messaging starts, because ErasureRegisterGuard detects an
#  ABSENT pepper and this one is present; D35 records that a changed pepper "looks exactly like a
#  right one until something fails to match". Every alias in the register is orphaned from then on,
#  and nothing re-keys an alias once written.
#
#  It tests the bytes in quality/startup.sh rather than a copy of them: the resolve_secret function
#  is extracted from the file and evaluated here. Sourcing the whole script is not an option — it
#  ends by deploying a stack — and restating the logic would test this file against itself.
#
#      ./.github/checks/quality-pepper-persistence-test.sh
# ==============================================================================
set -Eeuo pipefail

HERE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STARTUP="$(cd "$HERE_DIR/../.." && pwd)/quality/startup.sh"
[[ -f "$STARTUP" ]] || { echo "cannot find $STARTUP" >&2; exit 1; }

pass=0; fail=0
check() { # check <name> <actual> <expected>
  if [[ "$2" == "$3" ]]; then printf '  ok   %s\n' "$1"; pass=$((pass + 1));
  else printf '  FAIL %s\n       expected: %s\n       actual:   %s\n' "$1" "$3" "$2" >&2; fail=$((fail + 1)); fi
}

# The function under test, lifted verbatim, with the colour and logging helpers it calls stubbed.
# ACTION is the one other global it reads (a teardown is allowed to proceed on a conflict).
harness() {
  local sandbox="$1"; shift
  (
    set -Eeuo pipefail
    HERE="$sandbox"
    ACTION="${ACTION:-up}"
    log() { printf 'log: %s\n' "$*"; }
    ok() { :; }
    warn() { printf 'warn: %s\n' "$*"; }
    die() { printf 'die: %s\n' "$*" >&2; exit 3; }
    eval "$(sed -n '/^resolve_secret() {/,/^}/p' "$STARTUP")"
    resolve_secret
    printf 'PEPPER=%s\n' "$HC_PRIVACY_PEPPER"
  )
}

sandbox() { mktemp -d "${TMPDIR:-/tmp}/hc-pepper-test-XXXXXX"; }

echo "quality/startup.sh — erasure pepper persistence"

# 1. THE DEFECT ITSELF. An environment value must be written down the first time it is seen, so the
#    next run without it reads the same pepper instead of inventing one.
s="$(sandbox)"
out1="$(HC_PRIVACY_PEPPER=first-run-pepper harness "$s")"
check "an env-provided pepper is persisted" "$(cat "$s/.privacy-pepper" 2>/dev/null || echo '<no file written>')" "first-run-pepper"
out2="$(harness "$s")"
check "the next run without the variable reads the same pepper" \
  "$(printf '%s\n' "$out2" | sed -n 's/^PEPPER=//p')" "first-run-pepper"
rm -rf "$s"

# 2. Two different non-empty values is not a precedence question. One of them matches the aliases in
#    the volumes and this script cannot tell which, so it must refuse rather than choose.
s="$(sandbox)"; printf '%s' "stored-pepper" > "$s/.privacy-pepper"
rc=0; err="$(HC_PRIVACY_PEPPER=different-pepper harness "$s" 2>&1 >/dev/null)" || rc=$?
check "a conflicting env value is fatal" "$rc" "3"
case "$err" in *"differs from"*) r=names ;; *) r="$err" ;; esac
check "…and the refusal explains which two values disagree" "$r" "names"
rm -rf "$s"

# 3. A teardown must not be refused: dropping the volumes is how the operator resolves case 2, and
#    `down`/`clean` write no alias.
s="$(sandbox)"; printf '%s' "stored-pepper" > "$s/.privacy-pepper"
rc=0; out="$(ACTION=clean HC_PRIVACY_PEPPER=different-pepper harness "$s")" || rc=$?
check "a teardown with a conflicting value still runs" "$rc" "0"
check "…using the stored pepper" "$(printf '%s\n' "$out" | sed -n 's/^PEPPER=//p')" "stored-pepper"
rm -rf "$s"

# 4. The two paths that were already correct, kept so a fix cannot pass by refusing everything.
s="$(sandbox)"; printf '%s' "stored-pepper" > "$s/.privacy-pepper"
check "the file is used when the variable is unset" \
  "$(harness "$s" | sed -n 's/^PEPPER=//p')" "stored-pepper"
rm -rf "$s"

s="$(sandbox)"
first="$(harness "$s" | sed -n 's/^PEPPER=//p')"
second="$(harness "$s" | sed -n 's/^PEPPER=//p')"
check "a generated pepper is persisted and reused" "$first" "$second"
check "…and it is not empty" "$([[ -n "$first" ]] && echo yes || echo no)" "yes"
rm -rf "$s"

printf '\n%s passed, %s failed\n' "$pass" "$fail"
exit $(( fail > 0 ))
