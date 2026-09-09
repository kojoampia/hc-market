#!/usr/bin/env bash
# ==============================================================================
#  admin-seed-wiring.sh must fail on every state it exists to refuse — decisions.md D61.
#
#  Every bad state below is BUILT, not described, and each one is reached by a plausible edit rather
#  than by sabotage: a regeneration putting the changeunit back, a `!` dropped while refactoring, a
#  refusal softened into a warning "so the estate can still come up", a `:?` relaxed to `:-` because
#  a deploy failed on it. Each guarded thing is mutated SEPARATELY — a battery that fires as one
#  number cannot tell you which door opened.
#
#  Two of the cases are about the check rather than the repository, and they are the ones that have
#  historically been wrong here: a check whose subject file is MISSING must fail rather than pass
#  having read nothing, and a check matching source text must not be satisfiable by a COMMENT saying
#  the right words.
#
#      ./.github/checks/admin-seed-wiring-test.sh
# ==============================================================================
set -Eeuo pipefail

HERE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE_DIR/../.." && pwd)"
CHECK="$HERE_DIR/admin-seed-wiring.sh"
SEEDER="$ROOT/gateway/src/main/java/net/jojoaddison/config/dbmigrations/InitialSetupMigration.java"

pass=0; fail=0
check() { # check <name> <actual> <expected>
  if [[ "$2" == "$3" ]]; then printf '  ok   %s\n' "$1"; pass=$((pass + 1));
  else printf '  FAIL %s\n       expected: %s\n       actual:   %s\n' "$1" "$3" "$2" >&2; fail=$((fail + 1)); fi
}
outcome() { if "$@" >/dev/null 2>&1; then echo passes; else echo fails; fi; }

# Run the check with every input defaulted to something healthy, then override the named ones. A
# subshell rather than `env`, which cannot invoke a shell function — so each case fails for its own
# reason rather than because some other input was left broken.
outcome_with() { # outcome_with VAR=VALUE ... -> passes|fails
  (
    export HC_HASH_ROOTS="$work/emptyroots"
    export HC_SEEDER="$work/clean/InitialSetupMigration.java"
    while [[ "${1:-}" == *=* ]]; do export "${1?}"; shift; done
    outcome "$CHECK"
  )
}

work="$(mktemp -d "${TMPDIR:-/tmp}/hc-admin-seed-XXXXXX")"
trap 'rm -rf "$work"' EXIT

# A clean tree to point the parts of the check we are NOT mutating at, so each case fails for its own
# reason and not because some other input was left broken.
mkdir -p "$work/clean"
cp "$SEEDER" "$work/clean/InitialSetupMigration.java"
mkdir -p "$work/emptyroots"

echo "admin-seed-wiring.sh — against the states it exists to refuse"

# --- 0. the real repository, which must pass -----------------------------------------------------
check "the repository as it stands passes" "$(outcome "$CHECK")" "passes"

# --- 1. a regeneration puts the generated changeunit back ----------------------------------------
#
# The hash is assembled at run time rather than written here: this file is tracked, and the whole
# point of the check is that a bcrypt hash does not belong in one.
gen="$work/regenerated"
mkdir -p "$gen"
printf 'adminUser.setPassword("%s$%s$%s");\n' '$2a' '10' 'gSAhZrxMllrbgj0kkK9UceBPpChGWJA7SYIb1Mqo.n5aNLq1_oRrC' \
  > "$gen/InitialSetupMigration.java"
check "a regenerated changeunit's committed hash is caught" \
  "$(outcome_with HC_HASH_ROOTS="$gen")" "fails"

# ...and a hash inside a COMMENT is still a published hash, so the sweep deliberately does not strip.
printf '// historical: adminUser.setPassword("%s$%s$%s");\n' '$2a' '10' 'gSAhZrxMllrbgj0kkK9UceBPpChGWJA7SYIb1Mqo.n5aNLq1_oRrC' \
  > "$gen/InitialSetupMigration.java"
check "a hash quoted in a comment is caught too" \
  "$(outcome_with HC_HASH_ROOTS="$gen")" "fails"

# ...while ordinary source is not reported, which is the control. Without it every assertion above is
# satisfied by a sweep that reports everything.
printf 'adminUser.setPassword(passwordEncoder.encode(adminPassword()));\n' > "$gen/InitialSetupMigration.java"
check "ordinary source is not reported as a committed hash" \
  "$(outcome_with HC_HASH_ROOTS="$gen")" "passes"

# --- 2. the profile gate loses its negation -------------------------------------------------------
#
# One character, and it inverts the decision into "seed the demo accounts in production, and only
# there". Nothing about that fails to compile and no test outside this repository's own would see it.
sed 's/!environment\.acceptsProfiles/environment.acceptsProfiles/' "$SEEDER" > "$work/unnegated.java"
check "dropping the ! on the production profile test is caught" \
  "$(outcome_with HC_SEEDER="$work/unnegated.java")" "fails"

# --- 3. the refusal is softened back into a fallback ----------------------------------------------
sed 's/throw new IllegalStateException/logger.warn("no password configured"); return derivedPassword/' \
  "$SEEDER" > "$work/warns.java"
check "replacing the refusal with a warning is caught" \
  "$(outcome_with HC_SEEDER="$work/warns.java")" "fails"

# --- 4. the source-text checks must not be satisfiable by PROSE -----------------------------------
#
# The fail-open this repository has now found nine times. A file that only TALKS about the gate must
# be as red as one with no gate at all.
{
  printf 'package x;\n/**\n * This seeder uses !environment.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_PRODUCTION))\n'
  printf ' * and will throw new IllegalStateException when prod has no password.\n */\nclass InitialSetupMigration { }\n'
} > "$work/prose-only.java"
check "a comment describing the gate does not satisfy the check" \
  "$(outcome_with HC_SEEDER="$work/prose-only.java")" "fails"

# --- 5. the subject files go missing --------------------------------------------------------------
check "a missing seeder fails rather than passing having read nothing" \
  "$(outcome_with HC_SEEDER="$work/no-such-file.java")" "fails"
check "a missing hash root fails rather than sweeping nothing" \
  "$(outcome_with HC_HASH_ROOTS="$work/no-such-dir")" "fails"
check "a missing stripper fails rather than matching unstripped prose" \
  "$(outcome_with HC_STRIPPER="$work/no-such.awk")" "fails"

# --- 6. the deployment stops asking for the password ----------------------------------------------
sed 's/GATEWAY_ADMIN_PASSWORD: ${HC_GATEWAY_ADMIN_PASSWORD:?/GATEWAY_ADMIN_PASSWORD: ${HC_GATEWAY_ADMIN_PASSWORD:-/' \
  "$ROOT/deploy/docker/docker-compose.prod.yml" > "$work/compose-optional.yml"
check "relaxing the compose :? to :- is caught" \
  "$(outcome_with HC_PROD_COMPOSE="$work/compose-optional.yml")" "fails"
check "a missing production compose fails" \
  "$(outcome_with HC_PROD_COMPOSE="$work/no-such.yml")" "fails"

sed 's/^SECRET_KEYS=(JWT_BASE64_SECRET HC_PRIVACY_PEPPER HC_GATEWAY_ADMIN_PASSWORD)/SECRET_KEYS=(JWT_BASE64_SECRET HC_PRIVACY_PEPPER)/' \
  "$ROOT/deploy/deploy-prod.sh" > "$work/deploy-without.sh"
check "dropping it from SECRET_KEYS is caught" \
  "$(outcome_with HC_DEPLOY_SCRIPT="$work/deploy-without.sh")" "fails"

# ...and a mention in a comment is not membership of the array, because preflight iterates the array.
printf '# HC_GATEWAY_ADMIN_PASSWORD is required, see D61\nSECRET_KEYS=(JWT_BASE64_SECRET)\n' \
  > "$work/deploy-comment-only.sh"
check "naming it only in a comment does not count as preflight checking it" \
  "$(outcome_with HC_DEPLOY_SCRIPT="$work/deploy-comment-only.sh")" "fails"

printf '\n%s passed, %s failed\n' "$pass" "$fail"
exit $(( fail > 0 ))
