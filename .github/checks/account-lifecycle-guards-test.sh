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

# --- THIS FILE STILL HOLDS EIGHT `printf | grep -q` PIPELINES, AND THAT IS SCOPED, NOT OVERLOOKED --
#
# The subject it tests is `set -Eeuo pipefail` and now asks nothing through a pipe (decisions.md
# D98). This file is `pipefail` too and its own status reads are pipelines — so the four THIS package
# added are herestrings, and the eight that predate it are **NEW-73's**, named here so the
# inconsistency is a scope boundary rather than a thing nobody noticed.
#
# Two facts make leaving them correct rather than lazy. `run_check`'s output is about 3KB, under the
# ~4.5KB threshold at which a producer can be killed mid-write, so none of them can invert today.
# And their direction is fail-CLOSED: a swallowed match here makes a case report `bad`, which is a
# test going red on a correct tree — annoying, and the opposite of the hole D98 closed in the check.
# `case_refuses`'s first arm is the one to look at first when NEW-73 reaches this file: it is a
# ban-shape, so a swallowed match there changes which message a red run prints.
pass=0; failed=0
report() { if [ "$1" = ok ]; then pass=$((pass+1)); printf 'ok   %s\n' "$2"; else failed=$((failed+1)); printf '::error::case %s\n' "$2"; fi; }

# --- WHY THE SUMMARY IS CLASSIFIED AND NOT SUBTRACTED — decisions.md D98 §8, review round 2 --------
#
# The trailer used to print `$((pass - 2))` "states it exists to refuse", and the constant was wrong
# in both directions at once: it said 33 against 31 actual before NEW-71, and 41 against 37 after,
# because this file mixes cases that assert a REFUSAL with controls that assert a correct tree PASSES
# and the number of the latter has changed three times. Two readers derived two different answers
# from it, which is the argument for deriving it rather than choosing one.
#
# So every case declares which kind it is, and the trailer refuses to print at all if the two
# counters do not reconcile with what `report` actually saw — a new case that forgets to classify
# itself is red rather than silently absorbed into a subtraction. This is D98 §8's own rule landing
# on the commit that made it: a quoted count that happens to still be right is indistinguishable
# from one that is maintained.
refusals=0; controls=0
refusal() { refusals=$((refusals + 1)); }   # asserts the check REFUSES a constructed broken tree
control() { controls=$((controls + 1)); }   # asserts the check PASSES a tree that is correct

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
  refusal
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
control
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
# The arm this trips CHANGED in review round 2 and the case is kept rather than retuned: the mutation
# replaces "3 days" with "14 days", so the DIGIT requirement fires before the other-period ban. Both
# are correct refusals; this asserts the one that actually fires, because a case asserting a message
# no longer produced is a case that passes for the wrong reason.
case_refuses "17d the notice's OWN SECTION drifts from the code" "does not state the window in DIGITS" \
  sed -i 's/^three days later\*\* — 3 days, counted from when you registered./fourteen days later** — 14 days, counted from when you registered./' \
    "$FIXTURE/docs/privacy-notice.md"

# And the section pattern itself must be missing-means-ERROR, not missing-means-skip: a renumbered
# heading otherwise silently turns the whole assertion off, which is the shape of every check in this
# repository that has ever reported success about a file it could not read.
refusal
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

# --- 17f-17i. THE SECOND REVIEW ROUND'S ESCAPES, AND THE SHAPE THAT REPLACED THE WORD LIST -------
#
# Round 1 scoped the window assertion to the section and banned other periods with a word list that
# stopped at fourteen. These two are natural English and both PASSED that version, measured by the
# reviewer and reproduced here before the rewrite:
#
#   17f  "deleted after a fourteen-day period"       — hyphenated, so the grep never matched
#   17g  "We may in future keep it for thirty days"  — past the end of the list
#
# What replaced it does not enumerate wrong spellings: the digit form is REQUIRED (which needs no
# English at all and is what 17f now fails), and other number-like figures are banned from a list
# that reaches a hundred (which is what 17g and 17h fail). The header states what is still ignored.
case_refuses "17f the window is renamed in prose and the digits are dropped" "does not state the window in DIGITS" \
  python3 -c '
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = """three days later** — 3 days, counted from when you registered."""
new = """after a fourteen-day period.** We have kept the same three days internally."""
assert s.count(old) == 1
io.open(p, "w", encoding="utf-8").write(s.replace(old, new))
' "$FIXTURE/docs/privacy-notice.md"

case_refuses "17g a second period is added in words past the old list" "also names a period the estate does not apply" \
  python3 -c '
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = "**If you want to keep the account, confirm your email address.**"
new = "We may in future keep it for thirty days.\n\n" + old
assert s.count(old) == 1
io.open(p, "w", encoding="utf-8").write(s.replace(old, new))
' "$FIXTURE/docs/privacy-notice.md"

# The mixed case: the digits stay correct AND a hyphenated wrong period is added beside them. Neither
# layer catches this alone — the digit requirement is satisfied, so it is the ban that has to fire.
case_refuses "17h a hyphenated wrong period beside the correct digits" "also names a period the estate does not apply" \
  python3 -c '
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = "**If you want to keep the account, confirm your email address.**"
new = "We are moving to a fourteen-day window.\n\n" + old
assert s.count(old) == 1
io.open(p, "w", encoding="utf-8").write(s.replace(old, new))
' "$FIXTURE/docs/privacy-notice.md"

# A PUBLIC DOOR WHOSE PATH IS A CONSTANT. The derivation reads string literals, so a constant is
# invisible to it — the reviewer's point, and the same family as the wrapped call in 17b: the check
# printed ok about a door it could not see. It is an error naming the call now, rather than a limit
# stated in a comment.
case_refuses "17i a permitAll path that is not a literal" "permits a path this check cannot read" \
  sed -i 's#\.pathMatchers("/api/register")\.permitAll()#.pathMatchers("/api/register").permitAll()\n                    .pathMatchers(API_SIGNUP).permitAll()#' \
    "$FIXTURE/java/jojoaddison/config/SecurityConfiguration.java"

# And the control for 17i: the HttpMethod-prefixed form is Spring idiom, is used elsewhere in this
# gateway, and must NOT be refused — otherwise the fix above is a check that refuses correct code.
control
copy_tree "$FIXTURE"
sed -i 's#\.pathMatchers("/api/register")\.permitAll()#.pathMatchers(HttpMethod.POST, "/api/register").permitAll()#' \
  "$FIXTURE/java/jojoaddison/config/SecurityConfiguration.java"
method_form="$(run_check)"
if printf '%s' "$method_form" | grep -q "lifecycle guards: ok"; then
  report ok "17j an HttpMethod-prefixed permitAll is still read as a literal path"
else
  report bad "17j the HttpMethod form was refused: $(printf '%s' "$method_form" | grep '::error' | head -1)"
fi

# --- 17k-17n. ROUND FOUR: ordinals, the postfix position, and the two controls -------------------
#
# Round 3's shape — digits REQUIRED, other number-like figures banned — was escaped twice more, both
# with a correct `3 days` left in place so the requirement was satisfied and only the ban was under
# test (the reviewer's first attempt deleted the digits and got a false "caught" off the requirement):
#
#   17k  "deleted on the 14th day"  — `14th` is neither isdigit() nor a word, so it took the arm
#                                     meant for grammar
#   17l  "deleted at day 14"        — the number on the other side of the unit
#
# Both are closed. The review's conclusion is that this cannot be finished by enumeration, so the
# success line no longer claims the section is clean — see the check's header, and 17m/17n, which are
# the controls that keep the two halves honest in opposite directions.
case_refuses "17k an ORDINAL period the estate does not apply" "14th" \
  python3 -c '
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = "**If you want to keep the account, confirm your email address.**"
assert s.count(old) == 1
io.open(p, "w", encoding="utf-8").write(s.replace(old, "Accounts are deleted on the 14th day.\n\n" + old))
' "$FIXTURE/docs/privacy-notice.md"

case_refuses "17l a period AFTER the unit — 'at day 14'" "also names a period the estate does not apply" \
  python3 -c '
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = "**If you want to keep the account, confirm your email address.**"
assert s.count(old) == 1
io.open(p, "w", encoding="utf-8").write(s.replace(old, "Accounts are deleted at day 14.\n\n" + old))
' "$FIXTURE/docs/privacy-notice.md"

# THE REQUIREMENT STAYS EXACT: the applied number in ordinal form is NOT the canonical spelling, so
# "deleted on the 3rd day" with the digits dropped is red. Without this case the ordinal fix could
# have been made by treating `3rd` as satisfying the requirement, which would have widened the one
# half of this check that cannot be escaped.
case_refuses "17m the applied number in ordinal form only" "does not state the window in DIGITS" \
  python3 -c '
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = "three days later** — 3 days, counted from when you registered."
new = "on the 3rd day** after you registered."
assert s.count(old) == 1
io.open(p, "w", encoding="utf-8").write(s.replace(old, new))
' "$FIXTURE/docs/privacy-notice.md"

# AND THE NEW PATTERN MUST NOT REFUSE A CORRECT STATEMENT. "Deletion happens on day 3" beside the
# canonical form is right, and a ban that flagged it would be a check that refuses accurate prose —
# which is how a check gets deleted rather than fixed.
control
copy_tree "$FIXTURE"
python3 -c '
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = "**If you want to keep the account, confirm your email address.**"
assert s.count(old) == 1
io.open(p, "w", encoding="utf-8").write(s.replace(old, "Deletion happens on day 3.\n\n" + old))
' "$FIXTURE/docs/privacy-notice.md"
postfix_ok="$(run_check)"
if printf '%s' "$postfix_ok" | grep -q "lifecycle guards: ok"; then
  report ok "17n a CORRECT period after the unit is not a finding"
else
  report bad "17n a correct 'on day 3' was refused: $(printf '%s' "$postfix_ok" | grep '::error' | head -1)"
fi

# --- 19. the stripper's absence, and the one thing it must NOT see ------------------------------
#
# Every text-matching check in this repository trusts one file; absent it, the two whose subject is a
# silent gap passed having read nothing. This asserts the guard rather than the behaviour, because
# the behaviour would be "everything passes".
refusal
copy_tree "$FIXTURE"
sed 's#\.github/checks/strip-comments\.awk#/nonexistent/strip-comments.awk#' "$CHECK" > "$WORK/check-nostrip.sh"
missing_out="$( cd "$ROOT" && bash "$WORK/check-nostrip.sh" 2>&1 )" || true
if printf '%s' "$missing_out" | grep -q "is missing"; then
  report ok "19 a missing stripper stops the check rather than passing everything"
else
  report bad "19 a missing stripper did not stop the check: $(printf '%s' "$missing_out" | head -2)"
fi

# --- 20. the control for case 10: a COMMENT about the placeholder must not be a finding ----------
control
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

# --- 21-26. NEW-71: THE SAME BROKEN STATES, PLACED WHERE THE OLD SHAPE COULD NOT SEE THEM -------
#
#  Cases 1, 10 and 16 above all pass against the OLD `producer | grep -q` shape, and they pass for a
#  reason that is nothing to do with what they assert: each one's mutation lands near the END of its
#  file. `grep -q` exits at its first match, the producer then takes SIGPIPE and dies 141, and under
#  `set -o pipefail` the PIPELINE reports that — so a match arrives as a failure and a `&& var=…`
#  never fires. When only a few hundred bytes follow the match the producer has already written
#  everything and exited, there is no SIGPIPE, and the status is 0.
#
#  Measured on this tree (decisions.md D98 §2): match on line 1, varying only how much follows it —
#
#      stripped bytes   1127  3327  4319  4539 | 5527  8827  13227
#      status           0     0     0     0    | 141   141   141      (5 runs each, no variance)
#
#  so the discriminator is the producer's own stdio block, not machine load. THE ITEM AND D97 §8
#  BOTH ATTRIBUTED IT TO LOAD AND THAT IS THE WRONG VARIABLE — see D98 §2.
#
#  Hence these six. Each is one of the three above with the mutation moved to the TOP of the same
#  file, plus the controls for the narrowed pattern. Cases 22, 25 and 26 passed a broken tree before
#  NEW-71; 21 did too, and 23 and 24 are controls — see each one.
#
#  THEY ARE NOT DUPLICATES OF 1/10/16 AND MUST NOT BE "TIDIED" INTO THEM. The pair is the point: the
#  old ones pin that the assertion works at all, these pin that it works wherever the defect lands,
#  and only running both distinguishes a check from a check's lucky position.

# PART 1's BLIND SPOT, AND IT IS A POSITION NO REGENERATION PRODUCES — read this before citing it.
# The old shape could not see a match with more than ~4.5KB following it, and this fixture proves the
# scan now can: 11,600 stripped bytes, match on line 29, old pipeline 141 three runs of three.
#
# BUT `@Scheduled` IS `@Target({METHOD, ANNOTATION_TYPE})` — javap on spring-context — so this
# fixture is NOT legal Java and a generator cannot emit it (D98 §4b). The regeneration case is the
# annotation on `removeNotActivatedUsers`, which case 1 covers and which the OLD pipeline already
# caught (602 bytes following, rc=0 three of three). So what this case guards is a real blind spot at
# a hypothetical position — scheduled work on an early method of a long class — and NOT the two-sweep
# harm the regeneration table describes. Keep it; do not re-justify it with that harm.
case_refuses "21 @Scheduled at the TOP of a long generated class" "carries @Scheduled" \
  python3 -c '
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = "public class UserService {"
assert s.count(old) == 1
io.open(p, "w", encoding="utf-8").write(s.replace(old, "@Scheduled(cron = \"0 0 1 * * ?\")\n" + old))
' "$FIXTURE/java/jojoaddison/service/UserService.java"

# PART 4, THE ITEM'S OWN SUBJECT. Case 10 puts the placeholder at line 119 of a 152-line file, so 33
# lines follow it and the producer finishes: rc=0, reported, green. The real tree's only occurrence
# is line 219 of a 492-line compose file — 273 lines after it — and that one measured 141 five runs
# of five. So the ban was demonstrably unable to report the one file in the repository it is about.
case_refuses "22 the placeholder as a VALUE with a long file after it" "JHipster's placeholder base-url" \
  python3 -c '
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = "    SERVER_PORT: 8080\n"
assert s.count(old) == 1
io.open(p, "w", encoding="utf-8").write(
    s.replace(old, "    PLANTED_BASE_URL: http://my-server-url-to-change\n" + old, 1))
' "$FIXTURE/yml/prod-compose.yml"

# PART 6's http-scope ban, which is a ban and therefore fail-open in the same direction. It is the
# one of the three NOT observed to invert on today's tree — hc-market-app.conf strips to 3,788 bytes,
# inside one stdio block, so the producer always wins — and it is fixed anyway: the file is 299 lines
# and grows, the threshold is ~4.5KB, and a guard that is correct because a file is small today is
# not a guard. What it costs when it goes is every site on the host, because nginx -t refuses the
# whole configuration and the reload is refused for all of them.
#
# ⚠ THIS CASE IS GREEN UNDER THE OLD SHAPE TOO, SO IT IS NOT COVERAGE OF THAT FIX. Measured:
# reverting part 6 alone to a pipeline reddens NO case in this file, this one included. It earns its
# place by asserting the ban works at the top of the file — where the directive would actually be
# written — and the pipe fix at that site is guarded by argument only (decisions.md D98 §4). Do not
# read a green 23 as evidence the shape there is right.
case_refuses "23 an http-scope directive at the TOP of the production snippet" "declares an http-scope directive" \
  python3 -c '
import io, sys
p = sys.argv[1]
lines = io.open(p, encoding="utf-8").read().split("\n")
lines.insert(1, "limit_req_zone $binary_remote_addr zone=oops:1m rate=1r/s;")
io.open(p, "w", encoding="utf-8").write("\n".join(lines))
' "$FIXTURE/nginx/app.conf"

# --- THE NARROWED PATTERN: its exclusion, and the two things it must still catch ----------------
#
# Fixing the pipeline made part 4 able to see docker-compose.prod.yml:219 for the first time, and
# that line is a false positive: the placeholder is named inside the error message of a
# `${HC_MAIL_BASE_URL:? … }` expansion, explaining what the variable replaced. It is prose in a
# parameter expansion and not a base-url, and the shell stripper cannot tell them apart because both
# are YAML content. So the ban now blanks the MESSAGE of a `${VAR:?…}` expansion before matching —
# D94 narrowed this ban once already, from raw text to stripped text, and D98 §4 argues why a second
# narrowing is a different kind of move from the first.
#
# 24 is the exclusion. 25 and 26 are what stops it becoming a hole, and they are the point of it
# being `:?` rather than "a line mentioning the variable": a `:-` DEFAULT carrying the placeholder is
# exactly how it reached the production profile in the first place (part 3's own message says so),
# and it is still red.
control
copy_tree "$FIXTURE"
python3 -c '
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = "    SERVER_PORT: 8080\n"
assert s.count(old) == 1
io.open(p, "w", encoding="utf-8").write(
    s.replace(old, "    A_SECOND_ONE: ${HC_SOMETHING:?it was http://my-server-url-to-change once}\n" + old, 1))
' "$FIXTURE/yml/prod-compose.yml"
refusal_msg="$(run_check)"
if grep -q "lifecycle guards: ok" <<< "$refusal_msg"; then
  report ok "24 the placeholder inside a \${VAR:?…} refusal message is not a finding"
else
  report bad "24 a refusal message explaining the placeholder was read as a value: $(printf '%s' "$refusal_msg" | grep '::error' | head -2)"
fi

case_refuses "25 the placeholder as a \${VAR:-default}, which is how it reached production" "JHipster's placeholder base-url" \
  python3 -c '
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = "    SERVER_PORT: 8080\n"
assert s.count(old) == 1
io.open(p, "w", encoding="utf-8").write(
    s.replace(old, "    A_DEFAULTED_ONE: ${HC_SOMETHING:-http://my-server-url-to-change}\n" + old, 1))
' "$FIXTURE/yml/prod-compose.yml"

# AND THE EXCLUSION MUST NOT SPAN THE LINE. `${A:?msg}` ends at its own `}`; a real value written
# after one on the same line is still a value, and blanking to end of line would hide it.
case_refuses "26 a real value after a \${VAR:?…} on the same line" "JHipster's placeholder base-url" \
  python3 -c '
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = "    SERVER_PORT: 8080\n"
assert s.count(old) == 1
io.open(p, "w", encoding="utf-8").write(
    s.replace(old, "    A_MIXED_ONE: ${HC_SOMETHING:?required} http://my-server-url-to-change\n" + old, 1))
' "$FIXTURE/yml/prod-compose.yml"

# --- 27. AN AGENT WORKTREE IS A SECOND CHECKOUT INSIDE THIS ONE ---------------------------------
#
# Part 4 walks `find "${HC_YML_ROOT:-.}"`, and git worktrees are created at `<repo>/.claude/
# worktrees/agent-*/`, INSIDE the repository. Measured in the main checkout: 135 further .yml files,
# exactly doubling the walk. So without the prune the success line's "in the repository" is a claim
# about two repositories, and a finding in the nested one names a path the operator cannot fix and CI
# — which has no worktrees — will never reproduce. The workspace guide records the same shape biting
# `eslint .` in three sibling products.
control
copy_tree "$FIXTURE"
mkdir -p "$FIXTURE/yml/.claude/worktrees/agent-zz"
printf 'jhipster:\n  mail:\n    base-url: http://my-server-url-to-change\n' \
  > "$FIXTURE/yml/.claude/worktrees/agent-zz/application-prod.yml"
nested="$(run_check)"
if grep -q "lifecycle guards: ok" <<< "$nested"; then
  report ok "27 a placeholder inside a nested agent worktree is not this repository's finding"
else
  report bad "27 a nested worktree was walked as part of the repository: $(printf '%s' "$nested" | grep '::error' | head -2)"
fi

# --- 28. THE STRIPPER THAT IS PRESENT AND DOES NOT WORK -----------------------------------------
#
# Case 19 covers the stripper being ABSENT, which the preamble guard catches. This is the other half
# and it is the one the new has_in exists for: a stripper that is there, runs, and exits non-zero.
# Folding that status would make every assertion in the file a question asked of an empty buffer —
# the ninth fail-open this repository found, which was produced BY consolidating the previous eight.
# BOTH stripper paths become absolute, and that is not convenience. The check derives ROOT from its
# own BASH_SOURCE, so a copy living outside .github/checks/ cannot find EITHER stripper and reproduces
# case 19 instead — which is what the first version of this case did, reporting a pass for the wrong
# guard entirely. Absolute paths leave the java stripper working and only the shell one broken, which
# is the one thing this case is about.
refusal
copy_tree "$FIXTURE"
printf 'this is not a valid awk program {{{\n' > "$WORK/broken-strip.awk"
sed -e "s#^JAVA_STRIP=.*#JAVA_STRIP=\"$ROOT/.github/checks/strip-comments.awk\"#" \
    -e "s#^SH_STRIP=.*#SH_STRIP=\"$WORK/broken-strip.awk\"#" \
    "$CHECK" > "$WORK/check-badstrip.sh"
badstrip="$( cd "$ROOT" && \
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
  bash "$WORK/check-badstrip.sh" 2>&1 )" || true
if grep -q "could not read this file" <<< "$badstrip"; then
  report ok "28 a stripper that runs and FAILS stops the check rather than matching an empty buffer"
elif grep -q "is missing" <<< "$badstrip"; then
  report bad "28 the probe reproduced case 19 instead — the mutant could not find a stripper at all"
else
  report bad "28 a failing stripper was folded into 'no match': $(printf '%s' "$badstrip" | grep -cE '^ok') assertions passed"
fi

printf '\n%s assertions passed, %s failed\n' "$pass" "$failed"

# THE RECONCILIATION, AND IT IS THE POINT OF THE TWO COUNTERS rather than a formality: a case added
# without calling `refusal` or `control` would otherwise be absorbed silently, which is exactly how
# the constant this replaced came to overstate by four. It runs before the failure exit so a
# mis-classified case is reported even on a red run — the summary being wrong and the tree being
# wrong are two different findings and the second must not hide the first.
if [ "$((refusals + controls))" != "$((pass + failed))" ]; then
  printf '::error::%s assertions ran but %s were classified (%s refusals + %s controls). Every case must call `refusal` or `control` so the line below is derived rather than guessed (decisions.md D98 §8).\n' \
    "$((pass + failed))" "$((refusals + controls))" "$refusals" "$controls"
  exit 1
fi

[ "$failed" = 0 ] || exit 1
printf 'account-lifecycle-guards.sh fails on each of the %s broken states it exists to refuse, and passes %s controls\n' \
  "$refusals" "$controls"
