#!/usr/bin/env bash
# ==============================================================================
#  pepper-wiring.sh must fail on the two states its predecessors passed — decisions.md D35.
#
#  Both bad states are BUILT here, not described. A check that has only ever been run against a
#  healthy repository is a check nobody has seen work, and each of these was reached by a plausible
#  edit rather than by sabotage:
#
#  1. messaging moved off the shared YAML anchor onto its own environment block — which is exactly
#     what the per-service DISCOVERY_HOSTNAME and SPRING_DATASOURCE_URL overrides beside it do — with
#     the pepper not carried over. The old per-file grep still matches, on the other four services.
#
#  2. a pepper committed in application-prod.yml in the FLAT spelling Spring also accepts,
#     `healthconnect.privacy.pepper: …`. The old grep anchored on leading whitespace, and that line
#     begins with `h`.
#
#  Each case asserts BOTH: that the old grep passes (so the case really is the escape it claims to
#  be) and that the new check fails.
#
#      ./.github/checks/pepper-wiring-test.sh
# ==============================================================================
set -Eeuo pipefail

HERE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE_DIR/../.." && pwd)"
CHECK="$HERE_DIR/pepper-wiring.sh"

pass=0; fail=0
check() { # check <name> <actual> <expected>
  if [[ "$2" == "$3" ]]; then printf '  ok   %s\n' "$1"; pass=$((pass + 1));
  else printf '  FAIL %s\n       expected: %s\n       actual:   %s\n' "$1" "$3" "$2" >&2; fail=$((fail + 1)); fi
}
outcome() { if "$@" >/dev/null 2>&1; then echo passes; else echo fails; fi; }

# The two greps this replaced, verbatim from build.yml as it stood at cd7aab6.
old_compose_grep() { grep -qE 'HEALTHCONNECT_PRIVACY_PEPPER: \$\{HC_PRIVACY_PEPPER:\?' "$1"; }
old_profile_grep() { grep -qE '^\s+pepper:\s*\S' "$1"; }

work="$(mktemp -d "${TMPDIR:-/tmp}/hc-pepper-wiring-XXXXXX")"
trap 'rm -rf "$work"' EXIT

echo "pepper-wiring.sh — against states its predecessors passed"

# --- 0. the real repository, which must pass -----------------------------------------------------
check "the repository as it stands passes" "$(outcome "$CHECK")" "passes"

# --- 1. messaging moved off the shared anchor, without the pepper --------------------------------
broken="$work/compose-off-anchor.yml"
python3 - "$ROOT/quality/compose.yml" "$broken" <<'PY'
import re, sys
src, dst = sys.argv[1], sys.argv[2]
text = open(src, encoding="utf-8").read()
# messaging's block is `<<: *service-base` plus an `environment:` that merges the shared anchor.
# Replacing that one merge key with a couple of plain values is the edit an engineer makes when a
# service needs something the others do not — and it drops every inherited variable with it.
block = re.search(r"\n  messaging:\n(?:.*\n)*?(?=\n  [a-z])", text).group(0)
patched = block.replace(
    "      <<: *service-env\n",
    "      SERVER_PORT: 8080\n      SPRING_PROFILES_ACTIVE: prod\n",
)
assert patched != block, "the messaging environment no longer merges the shared anchor — update this fixture"
open(dst, "w", encoding="utf-8").write(text.replace(block, patched))
PY
check "the old per-file grep still passes on it" "$(outcome old_compose_grep "$broken")" "passes"
check "the per-service check fails on it" "$(HC_COMPOSE_FILES="$broken" outcome "$CHECK")" "fails"

# --- 2. a committed pepper in the flat spelling --------------------------------------------------
svc="$work/svc"
mkdir -p "$svc/src/main/resources/config"
cp "$ROOT/quality/compose.yml" "$work/compose-ok.yml"
printf 'spring:\n  application:\n    name: x\nhealthconnect.privacy.pepper: a-value-a-real-deployment-would-load\n' \
  > "$svc/src/main/resources/config/application-prod.yml"
# Inverted sense from the compose grep, and that is the point: for this one a MATCH is the error
# report. Not matching is how a real committed pepper would have gone unnoticed.
check "the old profile grep does not see the flat spelling" \
  "$(outcome old_profile_grep "$svc/src/main/resources/config/application-prod.yml")" "fails"
check "the value check fails on it" \
  "$(HC_COMPOSE_FILES="$work/compose-ok.yml" HC_SERVICE_DIRS="$svc" outcome "$CHECK")" "fails"

# --- 3. and it must not fire on the two legitimate shapes ----------------------------------------
printf 'healthconnect:\n  privacy:\n    pepper:\n' > "$svc/src/main/resources/config/application-prod.yml"
check "an empty nested pepper is not a committed secret" \
  "$(HC_COMPOSE_FILES="$work/compose-ok.yml" HC_SERVICE_DIRS="$svc" outcome "$CHECK")" "passes"
printf '# pepper: this-is-prose-about-the-pepper, not a value\nspring:\n  application:\n    name: x\n' \
  > "$svc/src/main/resources/config/application-prod.yml"
check "a comment mentioning a pepper is not a committed secret" \
  "$(HC_COMPOSE_FILES="$work/compose-ok.yml" HC_SERVICE_DIRS="$svc" outcome "$CHECK")" "passes"
# The fixture peppers in src/test/resources are committed on purpose and must stay invisible here.
check "the committed test fixture is not reported" \
  "$(HC_COMPOSE_FILES="$work/compose-ok.yml" outcome "$CHECK")" "passes"

printf '\n%s passed, %s failed\n' "$pass" "$fail"
exit $(( fail > 0 ))
