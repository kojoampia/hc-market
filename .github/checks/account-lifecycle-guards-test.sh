#!/usr/bin/env bash
# ==============================================================================
#  account-lifecycle-guards.sh's own test — decisions.md D94, backlog NEW-47.
#
#  A check nobody has watched fail is a check of nothing, and an AGGREGATE exit status is not enough:
#  one broken state per assertion, and the error the run produces has to be the error that assertion
#  exists to produce. Two of this file's cases were written after watching the check pass on a broken
#  tree, which is why they are here rather than described in a comment:
#
#    case 11  CONNECTION_KEYS emptied of HC_MAIL_BASE_URL while smoke_test's warn STILL NAMES all
#             three in prose. The first version of part 5 greped the whole stripped script and was
#             green — preflight checking nothing, the deploy dying at `up` instead. D78 §13's
#             SSH_OPTS lesson, three keys along.
#    case 18  the placeholder base-url inside a COMMENT. The first version of part 4 greped raw text
#             and reported three files: two of them this decision's own comments explaining the
#             placeholder, one a stale gateway/target copy. A check that cannot tell a value from a
#             paragraph about the value is this repository's ninth fail-open — and this direction of
#             it gets "fixed" by deleting the paragraph.
#
#  Every case restores the fixture by re-copying from a pristine tree rather than by editing back:
#  the workspace rule is `cp` from a pristine copy, never a revert, and nothing here touches the
#  repository's own files at all — the check is pointed at the fixture through its overrides.
# ==============================================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHECK="$ROOT/.github/checks/account-lifecycle-guards.sh"
WORK="$(mktemp -d -t account-lifecycle-guards-XXXXXX)"
PRISTINE="$WORK/pristine"
FIXTURE="$WORK/fixture"
trap 'rm -rf "$WORK"' EXIT

pass=0; failed=0
report() { if [ "$1" = ok ]; then pass=$((pass+1)); printf 'ok   %s\n' "$2"; else failed=$((failed+1)); printf '::error::case %s\n' "$2"; fi; }

# The pristine copy of everything the check reads, as the repository has it.
mkdir -p "$PRISTINE"
copy_tree() {
  local dest="$1"
  rm -rf "$dest"; mkdir -p "$dest"
  mkdir -p "$dest/java" "$dest/yml" "$dest/nginx" "$dest/docs" "$dest/deploy"
  cp -r "$ROOT/gateway/src/main/java/net/jojoaddison" "$dest/java/"
  cp "$ROOT/gateway/src/main/resources/config/application.yml"      "$dest/yml/application.yml"
  cp "$ROOT/gateway/src/main/resources/config/application-prod.yml" "$dest/yml/application-prod.yml"
  cp "$ROOT/deploy/docker/docker-compose.prod.yml"                  "$dest/yml/prod-compose.yml"
  cp "$ROOT/deploy/docker/docker-compose.dev.yml"                   "$dest/yml/dev-compose.yml"
  cp "$ROOT/quality/compose.yml"                                    "$dest/yml/quality-compose.yml"
  cp "$ROOT/deploy/deploy-prod.sh"                                  "$dest/deploy/deploy-prod.sh"
  cp "$ROOT/deploy/prod-server/hc-market-app.conf"                  "$dest/nginx/app.conf"
  cp "$ROOT/deploy/prod-server/nginx-conf.d/hc-market-account.conf" "$dest/nginx/zones.conf"
  cp "$ROOT/quality/host-site.conf"                                 "$dest/nginx/host-site.conf"
  cp "$ROOT/docs/privacy-notice.md"                                 "$dest/docs/privacy-notice.md"
  cp "$ROOT/docs/processing-record.md"                              "$dest/docs/processing-record.md"
}
copy_tree "$PRISTINE"

run_check() {                         # prints the check's output; never exits on failure
  ( cd "$ROOT" && \
    HC_GATEWAY_MAIN="$FIXTURE/java" \
    HC_RETENTION_CLASS="$FIXTURE/java/jojoaddison/service/AccountRetention.java" \
    HC_SECURITY_CONFIG="$FIXTURE/java/jojoaddison/config/SecurityConfiguration.java" \
    HC_GATEWAY_APP_YML="$FIXTURE/yml/application.yml" \
    HC_GATEWAY_PROD_YML="$FIXTURE/yml/application-prod.yml" \
    HC_PROD_COMPOSE="$FIXTURE/yml/prod-compose.yml" \
    HC_DEV_COMPOSE="$FIXTURE/yml/dev-compose.yml" \
    HC_QUALITY_COMPOSE="$FIXTURE/yml/quality-compose.yml" \
    HC_PROD_SCRIPT="$FIXTURE/deploy/deploy-prod.sh" \
    HC_PROD_VHOST="$FIXTURE/nginx/app.conf" \
    HC_PROD_ZONES="$FIXTURE/nginx/zones.conf" \
    HC_QUALITY_VHOST="$FIXTURE/nginx/host-site.conf" \
    HC_PRIVACY_NOTICE="$FIXTURE/docs/privacy-notice.md" \
    HC_PROCESSING_RECORD="$FIXTURE/docs/processing-record.md" \
    HC_YML_ROOT="$FIXTURE/yml" \
    bash "$CHECK" 2>&1 ) || true
}

# $1 = name, $2 = a fragment the refusal must contain, rest = the mutation, run against $FIXTURE
case_refuses() {
  local name="$1" fragment="$2"; shift 2
  copy_tree "$FIXTURE"
  "$@"
  local out; out="$(run_check)"
  if printf '%s' "$out" | grep -q "lifecycle guards: ok"; then
    report bad "$name: the check PASSED a tree it should refuse"
  elif printf '%s' "$out" | grep -qF "$fragment"; then
    report ok "$name — refused, naming '$fragment'"
  else
    report bad "$name: refused, but no message contained '$fragment'. Got: $(printf '%s' "$out" | grep '::error' | head -2)"
  fi
}

# --- 0. the positive control, and it is the case every other one leans on -----------------------
copy_tree "$FIXTURE"
out="$(run_check)"
if printf '%s' "$out" | grep -q "lifecycle guards: ok"; then
  report ok "0 the unmutated tree passes ($(printf '%s' "$out" | grep -c '^ok') assertions)"
else
  report bad "0 the unmutated tree does NOT pass — every case below is meaningless until it does: $(printf '%s' "$out" | grep '::error' | head -3)"
fi

# --- 1. a second sweep, which is what a regeneration brings back --------------------------------
case_refuses "1 @Scheduled restored in the generated UserService" "carries @Scheduled" \
  sed -i 's#    public void removeNotActivatedUsers() {#    @Scheduled(cron = "0 0 1 * * ?")\n    public void removeNotActivatedUsers() {#' \
    "$FIXTURE/java/jojoaddison/service/UserService.java"

# --- 2-4. the window, its schedule, and the two documents that state it -------------------------
case_refuses "2 application.yml loses the retention placeholder" "does not interpolate \${HC_UNACTIVATED_ACCOUNT_RETENTION_DAYS}" \
  sed -i 's/unactivated-retention-days: ${HC_UNACTIVATED_ACCOUNT_RETENTION_DAYS:}/unactivated-retention-days: 3/' \
    "$FIXTURE/yml/application.yml"

case_refuses "3 the sweep's default schedule is changed" "is not \"0 0 1 * * ?\"" \
  sed -i 's/DEFAULT_SWEEP_CRON = "0 0 1 \* \* ?"/DEFAULT_SWEEP_CRON = "0 0 4 * * ?"/' \
    "$FIXTURE/java/jojoaddison/service/AccountRetention.java"

case_refuses "4 the window is widened and the privacy notice is not" "docs/privacy-notice.md" \
  sed -i 's/DEFAULT_RETENTION_DAYS = "3"/DEFAULT_RETENTION_DAYS = "30"/' \
    "$FIXTURE/java/jojoaddison/service/AccountRetention.java"

# --- 5-8. mail, per environment -----------------------------------------------------------------
case_refuses "5 production defaults the mail host instead of requiring it" "not a required" \
  sed -i 's/${HC_MAIL_HOST:?/${HC_MAIL_HOST:-smtp.example.net}#{/' "$FIXTURE/yml/prod-compose.yml"

case_refuses "6 production stops passing the mail port" "does not set SPRING_MAIL_PORT" \
  sed -i '/SPRING_MAIL_PORT/d' "$FIXTURE/yml/prod-compose.yml"

case_refuses "7 quality stops passing the base-url" "does not set JHIPSTER_MAIL_BASE_URL" \
  sed -i '/JHIPSTER_MAIL_BASE_URL: ${HC_MAIL_BASE_URL:-http:\/\/market.healthconnect.local}/d' \
    "$FIXTURE/yml/quality-compose.yml"

# THE CASE THAT CAUGHT THE CATCHER CHECK. Replacing the image leaves the service name and the
# container_name still saying `mailpit`, so a check greping the rendered config for that string is
# green while nothing on the estate can receive a message. What the check asks now is whether
# SPRING_MAIL_HOST names a container in the same file, which this deletion breaks and a typo would
# break too.
case_refuses "8 the dev catcher's container is deleted" "is not a service or container_name" \
  sed -i '/container_name: hc-market-dev-mailpit/d' "$FIXTURE/yml/dev-compose.yml"

case_refuses "8b the gateway points at a host that is not in the file" "is not a service or container_name" \
  sed -i 's#${HC_MAIL_HOST:-hc-market-dev-mailpit}#${HC_MAIL_HOST:-mailpit.example.invalid}#' \
    "$FIXTURE/yml/dev-compose.yml"

# --- 9-10. the prod profile ---------------------------------------------------------------------
case_refuses "9 the prod profile gives the base-url a default" "with NO default" \
  sed -i 's#base-url: ${JHIPSTER_MAIL_BASE_URL}#base-url: ${JHIPSTER_MAIL_BASE_URL:http://my-server.example}#' \
    "$FIXTURE/yml/application-prod.yml"

case_refuses "10 the generator's placeholder comes back as a VALUE" "JHipster's placeholder base-url" \
  sed -i 's#base-url: ${JHIPSTER_MAIL_BASE_URL}#base-url: http://my-server-url-to-change#' \
    "$FIXTURE/yml/application-prod.yml"

# --- 11-12. the deploy --------------------------------------------------------------------------
# THE CASE THAT CAUGHT THE FIRST VERSION. The array loses the key; smoke_test's warn still names all
# three in prose, so a check reading the whole file stays green while preflight checks nothing.
case_refuses "11 CONNECTION_KEYS loses a mail key while the prose still names it" "CONNECTION_KEYS does not hold HC_MAIL_BASE_URL" \
  sed -i 's/^  HC_MAIL_HOST        HC_MAIL_PORT        HC_MAIL_BASE_URL$/  HC_MAIL_HOST        HC_MAIL_PORT/' \
    "$FIXTURE/deploy/deploy-prod.sh"

case_refuses "12 the smoke test stops reading mail.configured" "does not read \"configured\"" \
  sed -i 's/"configured"\[\[:space:\]\]\*:\[\[:space:\]\]\*true/"mailWorks"/' \
    "$FIXTURE/deploy/deploy-prod.sh"

# --- 13-17. the two edges -----------------------------------------------------------------------
case_refuses "13 the account map loses /api/register" "does not rate-limit: /api/register" \
  sed -i '/~\^\/api\/register  *\$binary_remote_addr;/d' "$FIXTURE/nginx/zones.conf"

case_refuses "14 the vhost stops applying the login zone" "declares no 'limit_req zone=hc_market_login'" \
  sed -i '/limit_req  *zone=hc_market_login burst=5 nodelay;/d' "$FIXTURE/nginx/app.conf"

case_refuses "15 the zone declaration is deleted" "does not declare 'zone=hc_market_account'" \
  sed -i '/limit_req_zone \$hc_market_account_key/d' "$FIXTURE/nginx/zones.conf"

# `limit_req_zone` in a file included inside server{} is refused by nginx itself — "directive is not
# allowed here" — and a failed nginx -t refuses the reload for every site on the host, so this is
# caught here rather than by the person typing the reload.
case_refuses "16 the production snippet declares an http-scope directive" "declares an http-scope directive" \
  sed -i 's#    location / {#    limit_req_zone $binary_remote_addr zone=oops:1m rate=1r/s;\n    location / {#' \
    "$FIXTURE/nginx/app.conf"

# A SIXTH PUBLIC DOOR. The derivation is the whole point of part 6: nobody has to remember to add it
# to a list, because the list is the gateway's own security chain.
case_refuses "17 a new permitAll path arrives with no ceiling" "/api/signup" \
  sed -i 's#\.pathMatchers("/api/register")\.permitAll()#.pathMatchers("/api/register").permitAll()\n                    .pathMatchers("/api/signup").permitAll()#' \
    "$FIXTURE/java/jojoaddison/config/SecurityConfiguration.java"

# And the other direction: a path that is limited but is no longer public. The ceiling then applies
# to nothing, or throttles authenticated customers, and both are silent.
case_refuses "18 a limited path stops being permitAll" "which is not a permitAll path" \
  sed -i 's#\.pathMatchers("/api/authenticate")\.permitAll()#.pathMatchers("/api/authenticate").authenticated()#' \
    "$FIXTURE/java/jojoaddison/config/SecurityConfiguration.java"

# --- 17b-17d. THE THREE FAIL-OPENS NEW-47's REVIEW MEASURED -------------------------------------
#
# All three passed a broken tree when this file was first written, and each is here because somebody
# ran the check rather than read it.

# PRETTIER FORMATS JAVA HERE, so this is the shape a new public door arrives in with no intent to
# evade — and part 6's regex was line-bound, so it was invisible while the same path on one line was
# refused. The asymmetry was the harmful one: a wrapped path that IS limited only made noise.
case_refuses "17b a WRAPPED permitAll path arrives with no ceiling" "/api/signup" \
  python3 -c '
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = "                    .pathMatchers(\"/api/register\").permitAll()"
new = old + "\n                    .pathMatchers(\"/api/signup\")\n                    .permitAll()"
assert s.count(old) == 1
io.open(p, "w", encoding="utf-8").write(s.replace(old, new))
' "$FIXTURE/java/jojoaddison/config/SecurityConfiguration.java"

# The same regex also missed a multi-path call, which is the other spelling of one line of Java.
case_refuses "17c a MULTI-PATH permitAll hides a second door" "/api/signup-two" \
  sed -i 's#\.pathMatchers("/api/register")\.permitAll()#.pathMatchers("/api/register", "/api/signup-two").permitAll()#' \
    "$FIXTURE/java/jojoaddison/config/SecurityConfiguration.java"

# THE DOCUMENT-AGREEMENT CHECK WAS DOCUMENT-WIDE. §7.1's headline rewritten to fourteen days while
# the code applies three: the old grep found "three days" in the paragraph BELOW the headline — the
# one explaining the decision — and printed ok. Scoping to the section was not enough on its own for
# the same reason, which is why every period figure in the section now has to be the applied one.
case_refuses "17d the notice's OWN SECTION drifts from the code" "also states a period the estate does not apply" \
  sed -i 's/^three days later\*\* — 3 days, counted from when you registered./fourteen days later** — 14 days, counted from when you registered./' \
    "$FIXTURE/docs/privacy-notice.md"

# And the section pattern itself must be missing-means-ERROR, not missing-means-skip: a renumbered
# heading otherwise silently turns the whole assertion off, which is the shape of every check in this
# repository that has ever reported success about a file it could not read.
copy_tree "$FIXTURE"
# Called directly rather than through run_check, which deliberately fixes its own environment: one
# more variable in that function is one more thing every other case silently inherits.
renumbered="$( cd "$ROOT" && HC_NOTICE_SECTION='^### 9\.9' \
  HC_RETENTION_CLASS="$FIXTURE/java/jojoaddison/service/AccountRetention.java" \
  HC_PRIVACY_NOTICE="$FIXTURE/docs/privacy-notice.md" \
  HC_PROCESSING_RECORD="$FIXTURE/docs/processing-record.md" \
  bash "$CHECK" 2>&1 )" || true
if printf '%s' "$renumbered" | grep -q "has no section matching"; then
  report ok "17e a renumbered section is refused rather than skipped"
else
  report bad "17e a renumbered section did not refuse: $(printf '%s' "$renumbered" | grep -c '^ok') assertions passed"
fi

# --- 19. the stripper's absence, and the one thing it must NOT see ------------------------------
#
# Every text-matching check in this repository trusts one file; absent it, the two whose subject is a
# silent gap passed having read nothing. This asserts the guard rather than the behaviour, because
# the behaviour would be "everything passes".
copy_tree "$FIXTURE"
sed 's#\.github/checks/strip-comments\.awk#/nonexistent/strip-comments.awk#' "$CHECK" > "$WORK/check-nostrip.sh"
missing_out="$( cd "$ROOT" && bash "$WORK/check-nostrip.sh" 2>&1 )" || true
if printf '%s' "$missing_out" | grep -q "is missing"; then
  report ok "19 a missing stripper stops the check rather than passing everything"
else
  report bad "19 a missing stripper did not stop the check: $(printf '%s' "$missing_out" | head -2)"
fi

# --- 20. the control for case 10: a COMMENT about the placeholder must not be a finding ----------
copy_tree "$FIXTURE"
printf '\n# A comment about http://my-server-url-to-change, which is what this file replaced.\n' \
  >> "$FIXTURE/yml/application-prod.yml"
printf '\n#   ~^/api/activate   $binary_remote_addr;   # deliberately NOT limited — D94 §5\n' \
  >> "$FIXTURE/nginx/zones.conf"
out="$(run_check)"
if printf '%s' "$out" | grep -q "lifecycle guards: ok"; then
  report ok "20 comments naming the placeholder and the unlimited path are not findings"
else
  report bad "20 prose was read as configuration: $(printf '%s' "$out" | grep '::error' | head -2)"
fi

printf '\n%s assertions passed, %s failed\n' "$pass" "$failed"
[ "$failed" = 0 ] || exit 1
printf 'account-lifecycle-guards.sh fails on each of the %s states it exists to refuse\n' "$((pass - 2))"
