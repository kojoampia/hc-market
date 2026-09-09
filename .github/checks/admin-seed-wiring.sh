#!/usr/bin/env bash
# ==============================================================================
#  The gateway may not seed a well-known administrator — decisions.md D61.
#
#  The generated Mongock changeunit created `admin` and `user` with JHipster's published bcrypt
#  hashes, activated, with no profile condition at all, and `mongock.migration-scan-package` is in
#  the BASE application.yml with no prod override — so it ran on any fresh Mongo, which is exactly
#  what a first production deploy creates. Nothing was red. Nothing ever would have been: the estate
#  comes up perfectly, and the only symptom is that `admin`/`admin` works on market.abofonsa.com
#  against a gateway whose tokens all five services accept.
#
#  FOUR CHECKS, and the first is the one that survives a regeneration:
#
#  1. NO COMMITTED BCRYPT HASH, anywhere a deployment reads. This is the regeneration guard. The
#     seeder is a GENERATED file — `jhipster jdl --force` rewrites it wholesale and puts the
#     changeunit back, hashes and all — so a check on the logic below would go green against a file
#     that no longer has the logic in it. A hash is the one thing the generated version cannot come
#     back without. src/test and *-secret-samples.yml are excluded, because this estate tolerates a
#     committed credential in exactly those two places (CLAUDE.md, "Working here").
#
#  2. The demo accounts are gated on NOT-PRODUCTION. An allow-list of profile names would be the trap
#     this repository has already recorded: a `spring.profiles.group` member is active without ever
#     appearing in SPRING_PROFILES_ACTIVE, so the check demands the negated form specifically.
#
#  3. The seeder REFUSES rather than falling back when prod has no configured password. This is the
#     departure from hc-professional, whose fallback is a warning — and a fallback that only warns is
#     the defect with a log line attached.
#
#  4. The variable is required in the production compose file and checked by deploy-prod.sh's
#     preflight, so an operator meets this at deploy time rather than at boot.
#
#  Checks 2 and 3 match SOURCE TEXT, so they strip comments first — this file's own prose about
#  `acceptsProfiles` would otherwise satisfy the check guarding it, which is the fail-open this
#  repository has now found nine times.
#
#  Inputs are overridable so the test beside this file can build the broken states and watch it fail.
#  A check nobody has seen fail is a check of nothing.
#
#      ./.github/checks/admin-seed-wiring.sh
#      HC_SEEDER=/tmp/x.java HC_PROD_COMPOSE=/tmp/c.yml ./.github/checks/admin-seed-wiring.sh
# ==============================================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

SEEDER="${HC_SEEDER:-gateway/src/main/java/net/jojoaddison/config/dbmigrations/InitialSetupMigration.java}"
PROD_COMPOSE="${HC_PROD_COMPOSE:-deploy/docker/docker-compose.prod.yml}"
DEPLOY_SCRIPT="${HC_DEPLOY_SCRIPT:-deploy/deploy-prod.sh}"
# Where a committed hash would actually be loaded by something. Overridable as one string so the test
# can point the sweep at a fixture tree.
HASH_ROOTS="${HC_HASH_ROOTS:-gateway/src/main catalog/src/main booking/src/main messaging/src/main payout/src/main deploy quality}"
STRIPPER="${HC_STRIPPER:-.github/checks/strip-comments.awk}"

fail=0
err() { printf '::error%s::%s\n' "${2:+ file=$2}" "$1"; fail=1; }

# The shared stripper is a single point of failure for two of the four checks below, and absent it
# they would strip nothing, match nothing and pass. Guarded rather than assumed — consolidating eight
# fail-opens into one file produced a ninth exactly here.
[ -f "$STRIPPER" ] || { err "$STRIPPER is missing — checks 2 and 3 would match against unstripped prose. See decisions.md D56."; exit 1; }

# --- 1. no committed bcrypt hash anywhere a deployment reads -------------------------------------
#
# `$2a$10$…` and its `$2b$`/`$2y$` spellings. The generated changeunit carries two of them.
for root in $HASH_ROOTS; do
  [ -e "$root" ] || { err "$root does not exist — this sweep would scan nothing and pass"; continue; }
  while IFS= read -r hit; do
    err "a bcrypt hash is committed at ${hit%%:*} — a published hash is not a password. Only src/test and *-secret-samples.yml may carry one. See decisions.md D61." "${hit%%:*}"
  done < <(grep -rInE '\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{20,}' "$root" \
             --exclude-dir=target --exclude-dir=node_modules \
             --exclude='*-secret-samples.yml' 2>/dev/null \
           | grep -v '/src/test/' || true)
done

# --- 2 and 3. the seeder's own two decisions -----------------------------------------------------
if [ ! -f "$SEEDER" ]; then
  err "$SEEDER does not exist — the gateway's account seeding is unguarded. If it was renamed, rename it here too."
else
  stripped="$(awk -f "$STRIPPER" "$SEEDER")"

  # The demo accounts must be excluded by NEGATING the production profile. Matched as one expression
  # so that dropping the `!` — which is the whole decision, and a one-character edit that inverts it
  # into "seed the demo accounts in production and nowhere else" — is red.
  printf '%s' "$stripped" | grep -qE '!\s*environment\.acceptsProfiles\(\s*Profiles\.of\(\s*JHipsterConstants\.SPRING_PROFILE_PRODUCTION' \
    || err "$SEEDER must gate the demo accounts on NOT production — !environment.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_PRODUCTION)). An allow-list of profile names is the spring.profiles.group trap. See decisions.md D61." "$SEEDER"

  # And it must REFUSE rather than fall back. Any `throw` will do here; what is being guarded is that
  # the no-password path under prod has no way through, and the unit tests are what pin which
  # exception and when. A grep cannot tell a reachable throw from an unreachable one.
  printf '%s' "$stripped" | grep -qE 'throw new IllegalStateException' \
    || err "$SEEDER must refuse to create an administrator when prod has no configured password, rather than falling back to a derived one. See decisions.md D61." "$SEEDER"
fi

# --- 4. the deployment must ask for it ------------------------------------------------------------
if [ ! -f "$PROD_COMPOSE" ]; then
  err "$PROD_COMPOSE does not exist"
else
  grep -qE 'GATEWAY_ADMIN_PASSWORD: \$\{HC_GATEWAY_ADMIN_PASSWORD:\?' "$PROD_COMPOSE" \
    || err "$PROD_COMPOSE must require HC_GATEWAY_ADMIN_PASSWORD with :? on the gateway. Without a value the gateway refuses to create an administrator, so a fresh estate has no way in. See decisions.md D61." "$PROD_COMPOSE"
fi

if [ ! -f "$DEPLOY_SCRIPT" ]; then
  err "$DEPLOY_SCRIPT does not exist"
else
  # In SECRET_KEYS, not merely mentioned: preflight iterates that array, and a variable named only in
  # a comment is checked by nothing. The operator would then meet the compose `:?` mid-deploy, after
  # .env has been overwritten and .env.previous rotated.
  grep -qE '^SECRET_KEYS=\(.*HC_GATEWAY_ADMIN_PASSWORD' "$DEPLOY_SCRIPT" \
    || err "$DEPLOY_SCRIPT must list HC_GATEWAY_ADMIN_PASSWORD in SECRET_KEYS, so preflight refuses before the running stack is touched. See decisions.md D61." "$DEPLOY_SCRIPT"
fi

[ "$fail" = 0 ] && echo "ok   the gateway seeds no published credential, and production must supply one"
exit "$fail"
