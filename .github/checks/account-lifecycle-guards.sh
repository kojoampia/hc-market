#!/usr/bin/env bash
# ==============================================================================
#  The account lifecycle must be configured, rate-limited and stated — decisions.md D94, NEW-47.
#
#  Six parts. Every one of them guards something that was, until D94, silent in production: a front
#  door that answered 201, mailed nowhere, could not be logged into, and deleted the account three
#  days later, with one WARN line as the only trace and every test in the estate green.
#
#      1. the gateway's only scheduled work is the sweep D94 wrote
#      2. the retention window is configurable, defaults to three days, and the two documents agree
#      3. mail is REQUIRED in production and a catcher is present in dev and quality
#      4. application-prod.yml defaults none of it, and nothing anywhere says my-server-url-to-change
#      5. deploy-prod.sh checks the three values and its smoke test reads the answer
#      6. the three public account paths are rate-limited at both edges, and the EXCLUSIONS are exact
#
#  --- WHY PART 6 IS DERIVED AND NOT A LIST -------------------------------------------------------
#
#  NEW-15's root cause was a list: nine generated resources were live on main because the delete
#  table named eight, and no test in the estate could see an omission from a list. So the paths are
#  derived from the gateway's own SecurityConfiguration — every `permitAll` path under /api — and the
#  difference between that set and what the two nginx files limit must be EXACTLY the two paths D94
#  §5 argues. A sixth public door is then red on the day it is added, in the same pull request,
#  rather than on the day somebody audits.
#
#  --- WHY EVERYTHING HERE IS READ THROUGH A STRIPPER ---------------------------------------------
#
#  This file's own subject matter is written about at length in the files it reads: the nginx configs
#  discuss /api/activate in prose, and so does the conf.d header. A check that matched raw text would
#  therefore report the exclusion as covered, out of a paragraph explaining that it is not. Two
#  strippers, because they are two languages: strip-comments.awk is a JAVA stripper and removes
#  nothing at all from a shell or nginx file while exiting 0, which is this repository's fail-open in
#  its purest form. Each caller guards that its stripper exists — one absent file is otherwise a
#  check that reads empty text and passes everything.
#
#  Inputs are overridable so account-lifecycle-guards-test.sh can construct each broken state and
#  watch the relevant assertion fail. A check nobody has seen fail is a check of nothing.
# ==============================================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

GATEWAY_MAIN="${HC_GATEWAY_MAIN:-gateway/src/main/java}"
RETENTION_CLASS="${HC_RETENTION_CLASS:-gateway/src/main/java/net/jojoaddison/service/AccountRetention.java}"
SECURITY_CONFIG="${HC_SECURITY_CONFIG:-gateway/src/main/java/net/jojoaddison/config/SecurityConfiguration.java}"
GATEWAY_APP_YML="${HC_GATEWAY_APP_YML:-gateway/src/main/resources/config/application.yml}"
GATEWAY_PROD_YML="${HC_GATEWAY_PROD_YML:-gateway/src/main/resources/config/application-prod.yml}"
PROD_COMPOSE="${HC_PROD_COMPOSE:-deploy/docker/docker-compose.prod.yml}"
DEV_COMPOSE="${HC_DEV_COMPOSE:-deploy/docker/docker-compose.dev.yml}"
QUALITY_COMPOSE="${HC_QUALITY_COMPOSE:-quality/compose.yml}"
PROD_SCRIPT="${HC_PROD_SCRIPT:-deploy/deploy-prod.sh}"
PROD_VHOST="${HC_PROD_VHOST:-deploy/prod-server/hc-market-app.conf}"
PROD_ZONES="${HC_PROD_ZONES:-deploy/prod-server/nginx-conf.d/hc-market-account.conf}"
QUALITY_VHOST="${HC_QUALITY_VHOST:-quality/host-site.conf}"
PRIVACY_NOTICE="${HC_PRIVACY_NOTICE:-docs/privacy-notice.md}"
PROCESSING_RECORD="${HC_PROCESSING_RECORD:-docs/processing-record.md}"
# The two `permitAll` paths D94 §5 surfaces as a question rather than answering. This is the ONLY
# enumerated list in this file and it is an EXACT-SET assertion rather than an allow-list: a path
# removed from it must be limited, a path added to it is an argued exclusion, and a new permitAll
# path that is in neither is red. hc-patient's equivalent nginx file limits all four of its account
# paths, so widening is house practice and is one line in each of the two maps.
UNLIMITED_BY_DECISION="${HC_UNLIMITED_BY_DECISION:-/api/activate /api/account/reset-password/finish}"

JAVA_STRIP=".github/checks/strip-comments.awk"
SH_STRIP=".github/checks/strip-sh-comments.awk"
for s in "$JAVA_STRIP" "$SH_STRIP"; do
  [ -f "$s" ] || { printf '::error::%s is missing. Every text-matching check in this repository reads through it, and without it this one matches empty text and passes everything.\n' "$s"; exit 1; }
done
java_src() { awk -f "$JAVA_STRIP" "$1"; }
conf_src() { awk -f "$SH_STRIP" "$1"; }

fail=0
err() { printf '::error%s::%s\n' "${2:+ file=$2}" "$1"; fail=1; }
ok()  { printf 'ok   %s\n' "$1"; }

# --- 1. The estate's only scheduled work ---------------------------------------------------------
#
# JHipster generates UserService.removeNotActivatedUsers with @Scheduled(cron = "0 0 1 * * ?") over a
# hard-coded three days. D94 removed the annotation — an edit to a GENERATED file, which is the least
# durable kind in this repository — and put the schedule in UnactivatedAccountSweep, which reads both
# values from configuration. If a regeneration puts it back, the estate has TWO sweeps and the
# generated one deletes at three days on an estate that configured longer, while the configured one
# keeps reporting the operator's window at /management/info. Nothing fails; the documents become
# false. ThereIsOneAccountSweepTest asserts the same thing with ArchUnit at test time; this is the
# half that is read at the moment somebody is running a generator rather than a test suite.
scheduled=""
if [ -d "$GATEWAY_MAIN" ]; then
  while IFS= read -r f; do
    java_src "$f" | grep -q '@Scheduled' && scheduled="$scheduled $f"
  done < <(find "$GATEWAY_MAIN" -name '*.java' | sort)
else
  err "$GATEWAY_MAIN does not exist — nothing was scanned for a second account sweep"
fi
if [ -n "$scheduled" ]; then
  for f in $scheduled; do
    err "$f carries @Scheduled. The gateway's only scheduled work is UnactivatedAccountSweep, which registers a cron task through SchedulingConfigurer so the window and the schedule come from configuration (decisions.md D94). An @Scheduled here is almost certainly the generated UserService.removeNotActivatedUsers coming back, which gives this estate a second sweep on a hard-coded three days." "$f"
  done
else
  ok "no @Scheduled in the gateway's main sources — the sweep is the only scheduled work"
fi

# --- 2. The window is configuration, its default is three days, and the documents agree ----------
[ -f "$GATEWAY_APP_YML" ] || err "$GATEWAY_APP_YML does not exist"
for placeholder in HC_UNACTIVATED_ACCOUNT_RETENTION_DAYS HC_UNACTIVATED_ACCOUNT_SWEEP_CRON; do
  if [ -f "$GATEWAY_APP_YML" ] && grep -q "\${$placeholder" "$GATEWAY_APP_YML"; then
    ok "$GATEWAY_APP_YML interpolates $placeholder"
  else
    err "$GATEWAY_APP_YML does not interpolate \${$placeholder}. That placeholder is the only thing making the retention policy overridable by an estate; without it the Java default is the only value and an operator setting the variable changes nothing, silently (decisions.md D94)." "$GATEWAY_APP_YML"
  fi
done

default_days=""
if [ -f "$RETENTION_CLASS" ]; then
  # `|| true` ON EVERY GREP IN A SUBSTITUTION, and it is not defensive noise: under `set -eo
  # pipefail` a grep that matches nothing exits 1, the substitution inherits it and errexit kills
  # this check DEAD — no error line, no summary, exit 1 with an empty message, which reads as a
  # broken check rather than as the finding it is. Measured: deleting SPRING_MAIL_PORT from the
  # production compose file produced exactly that, and the test beside this file (case 6) is what
  # saw it. Every branch below must be able to say what is missing.
  default_days="$(java_src "$RETENTION_CLASS" | grep -oE 'DEFAULT_RETENTION_DAYS *= *"[0-9]+"' | grep -oE '[0-9]+' | head -1 || true)"
  [ -n "$default_days" ] || err "no DEFAULT_RETENTION_DAYS literal in $RETENTION_CLASS — the estate's stated retention period for an unactivated account could not be read, so nothing below could be checked against it." "$RETENTION_CLASS"
  if java_src "$RETENTION_CLASS" | grep -q 'DEFAULT_SWEEP_CRON *= *"0 0 1 \* \* ?"'; then
    ok "the sweep's default schedule is still the generated 0 0 1 * * ?"
  else
    err "DEFAULT_SWEEP_CRON in $RETENTION_CLASS is not \"0 0 1 * * ?\". D94 kept JHipster's schedule deliberately so that a default estate behaves exactly as every estate that has ever run; changing it is a decision to record, not a tidy-up." "$RETENTION_CLASS"
  fi
else
  err "$RETENTION_CLASS does not exist"
fi

# THE NUMBER IS QUOTED TO A DATA SUBJECT AND TO A REGULATOR, which is why this is a check and not a
# comment. docs/privacy-notice.md 7.1 tells a person their part-finished account is deleted after N
# days and docs/processing-record.md 3.1 tells a regulator the same thing. A change to the Java
# default that does not reach both of them makes two outward-facing documents false, and the whole
# point of D94 is that this deletion is stated rather than inherited.
#
# DIGIT OR ENGLISH WORD, because one of the two documents is written for a person. The privacy
# notice says "deleted three days later" and should: a data subject reads prose. The processing
# record says "**3 days**, swept daily at 01:00 UTC" and should: a regulator reads a table. So the
# check accepts either spelling rather than forcing one register on both.
#
# The word list stops at fourteen, and the failure that causes is LOUD AND IN THE RIGHT DIRECTION:
# change the window to thirty days, write "thirty days" in the notice, and this goes red asking for
# the digit. That is a nuisance on a correct change and it is the only spelling of this check that
# cannot pass a document stating a period the estate does not apply.
number_word() {
  case "$1" in
    1) printf 'one' ;;   2) printf 'two' ;;    3) printf 'three' ;;  4) printf 'four' ;;
    5) printf 'five' ;;  6) printf 'six' ;;    7) printf 'seven' ;;  8) printf 'eight' ;;
    9) printf 'nine' ;; 10) printf 'ten' ;;   11) printf 'eleven' ;; 12) printf 'twelve' ;;
   13) printf 'thirteen' ;; 14) printf 'fourteen' ;;
    *) printf '' ;;
  esac
}
#
# SCOPED TO THE SECTION THAT STATES IT, AND IT WAS DOCUMENT-WIDE UNTIL NEW-47's REVIEW. Measured:
# with §7.1's headline rewritten to "fourteen days later — 14 days" and the code still applying 3,
# the document-wide grep printed `ok … states the window the code applies (3 days)` — because both
# documents mention three days several times elsewhere, including in the paragraph that explains the
# decision. That is what made the drift plausible AND invisible, and the comment here used to claim
# the check "cannot pass a document stating a period the estate does not apply", which was false.
#
# What it can see, stated precisely so the next person does not over-trust it again: the number in
# THE SECTION THAT DEFINES THE PERIOD. A wrong figure in some other paragraph of the same document is
# still invisible to it, and so is a section that has been renumbered out from under the pattern —
# which is why the section is missing-means-error below rather than missing-means-skip.
section_of() {                        # $1 = file, $2 = heading regex
  awk -v pat="$2" '
    $0 ~ pat { inside = 1; print; next }
    inside && /^#{1,3} / { exit }
    inside { print }
  ' "$1"
}
NOTICE_SECTION="${HC_NOTICE_SECTION:-^### 7\.1}"
RECORD_SECTION="${HC_RECORD_SECTION:-^### 3\.1}"

if [ -n "$default_days" ]; then
  word="$(number_word "$default_days")"
  for pair in "$PRIVACY_NOTICE:$NOTICE_SECTION" "$PROCESSING_RECORD:$RECORD_SECTION"; do
    doc="${pair%%:*}"; heading="${pair#*:}"
    if [ ! -f "$doc" ]; then
      err "$doc does not exist — the retention period this estate applies is stated in it"
      continue
    fi
    section="$(section_of "$doc" "$heading")"
    if [ -z "$section" ]; then
      err "$doc has no section matching '$heading', so the period this estate applies is stated nowhere this check can find — and a check that cannot see its own subject reports success. That section is where the unactivated-account window is told to a data subject or a regulator (decisions.md D94, D91 §5); if it has been renumbered, move HC_NOTICE_SECTION/HC_RECORD_SECTION with it." "$doc"
    else
      # EVERY PERIOD IN THE SECTION, NOT "DOES IT MENTION THE RIGHT ONE" — and that is the second
      # tightening, from the same review round. Scoping to §7.1 was not enough on its own: the
      # section legitimately says "we have kept the same three days" in the paragraph explaining the
      # decision, so a headline rewritten to "fourteen days later — 14 days" still left a matching
      # "three days" inside the scope and the check still passed. Measured.
      #
      # So the set of day-figures in the section must be exactly the one the code applies. A section
      # that needs to name a second period — "we used to keep it for 30 days" — goes red, and that
      # is the right direction: the wording then has to be changed deliberately, in the commit that
      # introduces the second number, rather than discovered by a data subject.
      stated=""
      for d in $(printf '%s\n' "$section" | grep -oiE '[0-9]+[[:space:]]+(days|day)' | grep -oE '^[0-9]+' | sort -u); do
        [ "$d" = "$default_days" ] || stated="$stated $d"
      done
      for n in 1 2 3 4 5 6 7 8 9 10 11 12 13 14; do
        w="$(number_word "$n")"
        [ "$n" = "$default_days" ] && continue
        printf '%s\n' "$section" | grep -qiE "(^|[^a-z])$w[[:space:]]+(days|day)" && stated="$stated $w"
      done
      says_it=0
      printf '%s\n' "$section" | grep -qiE "(^|[^0-9a-z])$default_days[[:space:]]*(days|day)" && says_it=1
      [ -n "$word" ] && printf '%s\n' "$section" | grep -qiE "(^|[^a-z])$word[[:space:]]+(days|day)" && says_it=1
      if [ "$says_it" = 0 ]; then
        err "$doc's section at '$heading' states neither '$default_days days' nor '$word days'. The gateway deletes an unactivated account after $default_days days (AccountRetention.DEFAULT_RETENTION_DAYS) and that section is what tells a data subject or a regulator how long that is — decisions.md D94, D91 §5. If the window has changed, both documents change with it." "$doc"
      elif [ -n "$stated" ]; then
        err "$doc's section at '$heading' also states a period the estate does not apply:$stated. The code applies $default_days days (AccountRetention.DEFAULT_RETENTION_DAYS). Two periods in the section that defines one is how a headline drifts from the paragraph under it — measured, that is exactly what the document-wide version of this check passed (decisions.md D94)." "$doc"
      else
        ok "$doc's section at '$heading' states the window the code applies ($default_days days) and no other"
      fi
    fi
  done
fi

# --- 3. Mail: required in production, a catcher in dev and quality -------------------------------
#
# Asked of COMPOSE rather than of the file's text, because all five services share a YAML anchor in
# each file and a grep for a string anywhere in a file cannot tell which service carries it — the
# lesson pepper-wiring.sh already records. `--no-interpolate` leaves the ${...} expressions intact,
# which is the whole point here: what is being checked is that production's are `:?` and cannot be
# defaulted.
gateway_env() {                      # $1 = compose file; prints KEY=<raw value> lines for the gateway
  docker compose -f "$1" config --no-interpolate --format json 2>/dev/null | python3 -c '
import json, sys
doc = json.load(sys.stdin)
for name, spec in (doc.get("services") or {}).items():
    if name == "gateway" or name.endswith("-gateway"):
        env = spec.get("environment") or {}
        if isinstance(env, list):
            env = dict(e.split("=", 1) for e in env if "=" in e)
        for k, v in env.items():
            print("%s=%s" % (k, "" if v is None else v))
'
}

prod_env="$(gateway_env "$PROD_COMPOSE" || true)"
if [ -z "$prod_env" ]; then
  err "could not read the gateway's environment out of $PROD_COMPOSE" "$PROD_COMPOSE"
else
  for pair in "SPRING_MAIL_HOST:HC_MAIL_HOST" "SPRING_MAIL_PORT:HC_MAIL_PORT" "JHIPSTER_MAIL_BASE_URL:HC_MAIL_BASE_URL"; do
    key="${pair%%:*}"; var="${pair##*:}"
    value="$(printf '%s\n' "$prod_env" | grep -E "^$key=" | head -1 | cut -d= -f2- || true)"
    case "$value" in
      *"\${$var:?"*) ok "$PROD_COMPOSE requires $var for $key" ;;
      "")            err "$PROD_COMPOSE's gateway does not set $key at all. No compose file in any environment set a single SPRING_MAIL_* until decisions.md D94, which is why a production customer would have registered, received nothing, been unable to log in, and been deleted three days later." "$PROD_COMPOSE" ;;
      *)             err "$PROD_COMPOSE sets $key to '$value', which is not a required \${$var:?...} variable. A DEFAULT IS EXACTLY HOW http://my-server-url-to-change REACHED THE PRODUCTION PROFILE — mail that goes nowhere must stop a deploy, not be quietly substituted (decisions.md D94)." "$PROD_COMPOSE" ;;
    esac
  done
fi

for f in "$DEV_COMPOSE" "$QUALITY_COMPOSE"; do
  env_lines="$(gateway_env "$f" || true)"
  if [ -z "$env_lines" ]; then
    err "could not read the gateway's environment out of $f" "$f"
    continue
  fi
  for key in SPRING_MAIL_HOST SPRING_MAIL_PORT JHIPSTER_MAIL_BASE_URL; do
    if printf '%s\n' "$env_lines" | grep -qE "^$key=."; then
      ok "$f passes $key to the gateway"
    else
      err "$f's gateway does not set $key. Dev and quality run a mail catcher so that registration, activation and password reset can be walked at all; without these the send fails against the container's own loopback and the only trace is one WARN line (decisions.md D94)." "$f"
    fi
  done
  # THE CATCHER, ASKED AS "DOES SPRING_MAIL_HOST NAME SOMETHING IN THIS FILE" — which is a stronger
  # question than "is there a mailpit service", and the first version asked the weaker one. It greped
  # the rendered config for the string `mailpit`, so replacing the image with alpine:3 left the
  # service NAME and the container_name still saying mailpit and the check green (measured, case 8 of
  # the test beside this file). It would also have passed a typo in the host, which is the failure an
  # operator actually meets: the send then fails against a name that resolves nowhere, and the only
  # trace is one WARN line.
  #
  # So the host's default is lifted out of `${HC_MAIL_HOST:-…}` and must match a container_name or a
  # service name declared in the SAME file. That is the property that makes the estate walkable, and
  # it is the reason the address is a container name rather than the short `mailpit`: this stack
  # shares hcnet with three sibling products, where a short name is somebody else's alias (NEW-26).
  host_default="$(printf '%s\n' "$env_lines" | grep -E '^SPRING_MAIL_HOST=' | head -1 | sed -E 's/.*:-([^}]*)\}.*/\1/' || true)"
  names="$(docker compose -f "$f" config --no-interpolate --format json 2>/dev/null | python3 -c '
import json, sys
doc = json.load(sys.stdin)
for name, spec in (doc.get("services") or {}).items():
    print(name)
    if spec.get("container_name"):
        print(spec["container_name"])
')"
  if [ -n "$host_default" ] && printf '%s\n' "$names" | grep -qxF "$host_default"; then
    ok "$f's SPRING_MAIL_HOST default ($host_default) is a container in the same file"
  else
    err "$f's gateway points SPRING_MAIL_HOST at '${host_default:-<nothing this check could read>}', which is not a service or container_name in that file. Dev and quality run a local catcher precisely so this path can be walked; a host that resolves nowhere fails the send with one WARN line and nothing else (decisions.md D94)." "$f"
  fi
done

# --- 4. The prod profile defaults none of it, and the placeholder is gone estate-wide ------------
if [ -f "$GATEWAY_PROD_YML" ]; then
  for prop in 'host:SPRING_MAIL_HOST' 'port:SPRING_MAIL_PORT' 'base-url:JHIPSTER_MAIL_BASE_URL'; do
    key="${prop%%:*}"; var="${prop##*:}"
    # The `}` immediately after the name is what makes this "defaultless": ${X:-y} and ${X:y} both
    # put a colon there, and either one is a production estate that starts and mails nowhere.
    if grep -qE "^ *$key: \\\$\{$var\}[[:space:]]*$" "$GATEWAY_PROD_YML"; then
      ok "application-prod.yml carries a defaultless $key placeholder"
    else
      err "$GATEWAY_PROD_YML does not carry '$key: \${$var}' with NO default. A defaultless placeholder fails the context while Spring evaluates the mail autoconfiguration and the message names the variable; with a default — or with the property absent — a prod estate starts and mails nowhere, which is how http://my-server-url-to-change survived (decisions.md D94)." "$GATEWAY_PROD_YML"
    fi
  done
else
  err "$GATEWAY_PROD_YML does not exist"
fi

# The literal production shipped with. Estate-wide, because it is never right anywhere and it is one
# copy-paste away from any profile.
#
# READ THROUGH THE STRIPPER, AND THIS ONE WAS MEASURED GOING WRONG: the first version greped raw
# text and reported three files, two of which are this decision's own comments EXPLAINING the
# placeholder and the third a stale gateway/target/classes copy of the pre-D94 file. A check that
# cannot tell a value from a paragraph about the value is the fail-open this repository has now
# found nine times — and here it fails in the direction that is merely annoying, which is exactly how
# it gets "fixed" by deleting the paragraph instead.
#
# target/ is excluded because it is build output: a stale copy of a file that has since been fixed
# is not a claim about the estate, and `mvn clean` is not a remedy anybody should have to know.
placeholders=""
while IFS= read -r f; do
  conf_src "$f" | grep -q 'my-server-url-to-change' && placeholders="$placeholders $f"
done < <(find "${HC_YML_ROOT:-.}" -name target -prune -o -name node_modules -prune -o \( -name '*.yml' -o -name '*.yaml' \) -print | sort)
if [ -n "$placeholders" ]; then
  for f in $placeholders; do
    err "$f contains JHipster's placeholder base-url. Every activation and password-reset link this estate sends would point at a host that does not exist, and the mail would be DELIVERED (decisions.md D94)." "$f"
  done
else
  ok "no .yml in the repository carries my-server-url-to-change"
fi

# --- 5. The deploy checks the three values, and its smoke test reads the answer ------------------
if [ -f "$PROD_SCRIPT" ]; then
  # Stripped ONCE to a file rather than held in a variable, because the variable form disagreed with
  # itself between two runs of this check over an unchanged deploy-prod.sh — a 36KB string through
  # `$( )` and `printf '%s' | grep`, and whatever the cause, an instrument that answers differently
  # twice is not one to reason from (the workspace guide's rule: suspect the instrument before the
  # code). A file and a plain `grep -q` are deterministic and they are what this asserts with.
  script_src="$(mktemp)"
  conf_src "$PROD_SCRIPT" > "$script_src"
  # THE ARRAY'S OWN VALUE, NOT THE NAME ANYWHERE IN THE FILE — D78 §13's lesson about SSH_OPTS. The
  # first version greped the whole (stripped) script, which is satisfied by the warn message inside
  # smoke_test that lists all three names in prose: empty the CONNECTION_KEYS array and preflight
  # checks nothing while this check stays green. So the array is lifted and the names must be inside
  # it. CONNECTION_KEYS rather than SECRET_KEYS deliberately: none of the three is a secret, and
  # HC_MAIL_PASSWORD, which is one, is in neither list because a relay may need no credential at all.
  keys="$(awk '/^CONNECTION_KEYS=\(/{inside=1} inside{print} inside&&/^\)/{exit}' "$script_src")"
  if [ -z "$keys" ]; then
    err "no CONNECTION_KEYS=( … ) array in $PROD_SCRIPT. Preflight checks every required value by name on the host before the stack is touched; without that array it checks none of them, and a deploy rotates .env and then dies at \`up\` on the compose file's own :? — the eleven-key defect of 2026-09-05." "$PROD_SCRIPT"
  else
    for var in HC_MAIL_HOST HC_MAIL_PORT HC_MAIL_BASE_URL; do
      if printf '%s\n' "$keys" | grep -qw "$var"; then
        ok "deploy-prod.sh's CONNECTION_KEYS holds $var"
      else
        err "$PROD_SCRIPT's CONNECTION_KEYS does not hold $var. Preflight checks every required value by NAME on the host before the stack is touched, so a value missing from that array is a deploy that rotates .env and then dies at \`up\` on the compose file's own :? (decisions.md D94)." "$PROD_SCRIPT"
      fi
    done
  fi
  # The smoke test's own question. It reads GET /management/info's mail.configured and FAILS CLOSED,
  # which rolls the deployment back: an estate whose front door swallows people must not ship. A
  # rename of that detail key leaves the grep matching nothing and the deploy passing.
  if grep -qE '"configured"' "$script_src"; then
    ok "deploy-prod.sh's smoke test still reads mail.configured"
  else
    err "$PROD_SCRIPT's smoke test does not read \"configured\" any more. That is the single fact that decides whether a production estate can send an activation mail at all; without it a deploy of an estate that mails nowhere passes every gate (decisions.md D94)." "$PROD_SCRIPT"
  fi
  rm -f "$script_src"
else
  err "$PROD_SCRIPT does not exist"
fi

# --- 6. The public account paths are rate-limited at BOTH edges, and the exclusions are exact ----
#
# limit_req on /api/register is not about this host's load. It is open self-registration on a public
# health-services domain which SENDS MAIL through a relay this estate does not own and writes an
# account row the sweep keeps for three days — an amplifier whether or not the mail works, and until
# D94 the mail did not work, which is the only reason nobody noticed the door was open.
permit_paths=""
if [ -f "$SECURITY_CONFIG" ]; then
  # --- WHY THIS IS NOT A LINE-BOUND GREP, AND IT WAS ONE UNTIL NEW-47'S REVIEW -----------------
  #
  # The first version was `grep -oE '\.pathMatchers\("(/api/[^"]*)"\)[[:space:]]*\.permitAll\(\)'`,
  # which is line-bound, and **prettier formats Java in this repository** — so
  #
  #     .pathMatchers("/api/signup")
  #     .permitAll()
  #
  # was invisible to it. Measured, both directions: with that spelling the whole check printed
  # `account lifecycle guards: ok` while a brand-new unauthenticated public door had no ceiling; the
  # same path on one line was refused. The asymmetry is the harmful one — a wrapped path that IS
  # limited only produces noise, a wrapped path that is NOT limited produces silence.
  #
  # This is D60's alternation in a different language: CLAUDE.md records "that alternation is
  # load-bearing rather than tidy" for the zone-write check, for exactly this reason, and the shape
  # arrives with no intent to evade because a formatter puts it there.
  #
  # Matched in python rather than in grep so the pattern can span lines AND so a multi-path call —
  # `.pathMatchers("/api/a", "/api/b").permitAll()`, which the old regex also missed — yields both
  # paths. Space-separated on the way out, because the `case " $permit_paths " in *" $p "*` tests
  # below use spaces as delimiters and a newline-separated string matched nothing in either of them.
  permit_paths="$(java_src "$SECURITY_CONFIG" | python3 -c '
import re, sys

src = sys.stdin.read()
paths = []
# [^)] spans newlines and \s* absorbs the wrap, so the formatter cannot hide a door.
for call in re.findall(r"\.pathMatchers\(([^)]*)\)\s*\.permitAll\(\)", src):
    for path in re.findall(r"\"([^\"]*)\"", call):
        if path.startswith("/api/"):
            paths.append(path)
print(" ".join(sorted(set(paths))))
' || true)"
  [ -n "$permit_paths" ] || err "no permitAll /api path found in $SECURITY_CONFIG — the set this part derives from could not be read, so nothing about the rate limits was established." "$SECURITY_CONFIG"
else
  err "$SECURITY_CONFIG does not exist"
fi

limited_in() {                       # $1 = an nginx file; prints the paths its limit_req maps key on
  conf_src "$1" | grep -oE '~\^/api/[A-Za-z0-9/._-]+' | sed 's/^~\^//' | sort -u || true
}

for f in "$PROD_ZONES" "$QUALITY_VHOST"; do
  [ -f "$f" ] || { err "$f does not exist — one of the two edges declares no rate-limit zone at all"; continue; }
  limited="$(limited_in "$f")"
  missing=""
  for p in $permit_paths; do
    case " $UNLIMITED_BY_DECISION " in *" $p "*) continue ;; esac
    printf '%s\n' "$limited" | grep -qxF "$p" || missing="$missing $p"
  done
  if [ -n "$missing" ]; then
    err "$f does not rate-limit:$missing. Every permitAll path under /api is an unauthenticated public door; NEW-47 named /api/authenticate, /api/register and /api/account/reset-password/init, and the two exclusions are argued in decisions.md D94 §5. A path that is neither limited nor argued is a door nobody decided to leave open." "$f"
  else
    ok "$f rate-limits every permitAll /api path except the two D94 §5 argues"
  fi
  # THE EXACT SET, not "at least these". A path quietly added to the exclusion list is the same
  # defect as one never limited, and only an exact-set assertion sees it — D67's rule, one file along.
  surplus=""
  for p in $UNLIMITED_BY_DECISION; do
    printf '%s\n' "$limited" | grep -qxF "$p" && surplus="$surplus $p"
  done
  [ -z "$surplus" ] || ok "$f limits$surplus as well, which D94 §5 recommends — update HC_UNLIMITED_BY_DECISION and D94 §5 together"
  for p in $limited; do
    case " $permit_paths " in
      *" $p "*) ;;
      *) err "$f rate-limits '$p', which is not a permitAll path in $SECURITY_CONFIG. Either it is authenticated (and a limit on it throttles logged-in customers) or the path has been renamed and this ceiling now applies to nothing — both are silent." "$f" ;;
    esac
  done
done

# The directives, and the scope they must live in. The zones are http-scope; the production vhost is
# a SNIPPET included inside `server { }`, where limit_req_zone and map are invalid, so its zones live
# in conf.d and install FIRST — a snippet naming an undeclared zone fails nginx -t, and a failed test
# refuses the reload for every site on the host. quality/host-site.conf is a whole site at http scope
# and therefore carries both halves itself, which is why this part checks two different things about
# two files rather than the same thing twice.
for z in hc_market_login hc_market_account; do
  for f in "$PROD_VHOST" "$QUALITY_VHOST"; do
    [ -f "$f" ] || continue
    if conf_src "$f" | grep -qE "limit_req[[:space:]]+zone=$z"; then
      ok "$f applies zone=$z"
    else
      err "$f declares no 'limit_req zone=$z'. The zone existing is not the limit applying: nginx accounts nothing without a limit_req directive in a location the request reaches (decisions.md D94)." "$f"
    fi
  done
  for f in "$PROD_ZONES" "$QUALITY_VHOST"; do
    [ -f "$f" ] || continue
    if conf_src "$f" | grep -qE "limit_req_zone .*zone=$z:"; then
      ok "$f declares zone $z"
    else
      err "$f does not declare 'zone=$z'. Without it nginx refuses the whole configuration — 'zero size shared memory zone' on 1.28.3, 'unknown limit_req zone' on older builds — and the reload is refused for every site on this host." "$f"
    fi
  done
done

if conf_src "$PROD_VHOST" | grep -qE '^[[:space:]]*(limit_req_zone|map)[[:space:]]'; then
  err "$PROD_VHOST declares an http-scope directive (limit_req_zone or map). This file is included INSIDE server { }, so nginx refuses it with 'directive is not allowed here' and the reload fails for every site on the host. Both belong in deploy/prod-server/nginx-conf.d/ (decisions.md D94)." "$PROD_VHOST"
else
  ok "$PROD_VHOST declares no http-scope directive"
fi

if [ "$fail" = 0 ]; then
  printf '\naccount lifecycle guards: ok\n'
else
  printf '\naccount lifecycle guards: FAILED\n'
fi
exit "$fail"
