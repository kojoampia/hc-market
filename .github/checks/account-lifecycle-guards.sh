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
# WHAT THIS CAN AND CANNOT SEE. Four review rounds found one escape family each, and the fourth
# round drew the conclusion that matters more than any of them:
#
#   round 1  the figure anywhere in the document        — "three days" in the paragraph below the drift
#   round 2  scoped to the section, wrong periods banned by a word list stopping at fourteen
#            → "a fourteen-day period" (hyphenated) and "thirty days" (past the list) both passed
#   round 3  the DIGIT REQUIREMENT added, which is the half that cannot be escaped by a spelling
#   round 4  → "on the 14th day" (ordinal) and "at day 14" (the number after the unit) both passed
#
# THE TWO HALVES ARE NOT EQUALLY SOUND, AND SAYING SO IS THE POINT:
#
#   REQUIRED — "the section must state the applied window in DIGITS beside the word day" (`3 days`,
#   `3-day`, `**3 days**`). This needs no English at all. It is a positive requirement for one
#   unambiguous form, so no spelling of anything else can satisfy it, and a section that renames the
#   period in prose and drops the digits is red. **This is the half that works.**
#
#   BEST-EFFORT — the ban on any OTHER number-like figure beside `day`/`days`. It has failed four
#   times, and it cannot be otherwise: banning wrong prose means enumerating wrongness, and English
#   is unbounded. It closes what it closes and the success line must not imply more.
#
# WHAT THE BAN READS: a digit sequence (ordinals stripped, so `14th` counts as 14), a number word
# from the list in the python below (one…twenty plus the tens to a hundred), on either side of the
# unit — `14 days` and `at day 14` both.
#
# WHAT IT KNOWINGLY MISSES, so that nobody has to rediscover it in round five:
#
#   * FULLWIDTH AND NON-ASCII DIGITS — `１４ days` is not matched. Not closed, stated.
#   * EMPHASIS OR MARKUP BETWEEN THE NUMBER AND THE UNIT — `14**&nbsp;**days`, a footnote marker, an
#     HTML tag. A plain NBSP between them IS caught (measured); markup between them is not.
#   * ANY OTHER UNIT — "72 hours", "two weeks", "a month", "one business quarter". This check reads
#     the word `day` and nothing else, so a period expressed in another unit is invisible to it.
#   * A NUMBER WORD OUTSIDE THE LIST, and words that are not number-like at all: "it runs once a
#     day" and "calendar days" are grammar, and flagging grammar is how a check gets weakened until
#     it is deleted.
#
# It over-flags rather than misses where it can: a figure inside a code span or an HTML comment is
# still a finding (measured), because a wrong period explained in prose is still a wrong period a
# data subject reads.
#
# WHAT STANDS BEHIND ALL OF IT is the digit requirement and a person reading the section — not this
# ban. The success line therefore reports what it inspected and makes no claim about what it did
# not: an `ok` that outruns its code is what makes the next reader trust it too far, and this file
# has now removed that twice (`and no other` in round 2, `all of them the applied one` in round 4).
#
# SCOPED TO THE SECTION THAT STATES IT, AND IT WAS DOCUMENT-WIDE UNTIL NEW-47's REVIEW. Measured:
# with §7.1's headline rewritten to "fourteen days later — 14 days" and the code still applying 3,
# the document-wide grep printed `ok … states the window the code applies (3 days)` — because both
# documents mention three days several times elsewhere, including in the paragraph that explains the
# decision. That is what made the drift plausible AND invisible.
#
# A wrong figure in some OTHER paragraph of the same document is still invisible, and so is a section
# renumbered out from under the pattern — which is why a missing section is an error below rather
# than a skip.
section_of() {                        # $1 = file, $2 = heading regex
  awk -v pat="$2" '
    $0 ~ pat { inside = 1; print; next }
    inside && /^#{1,3} / { exit }
    inside { print }
  ' "$1"
}
NOTICE_SECTION="${HC_NOTICE_SECTION:-^### 7\.1}"
RECORD_SECTION="${HC_RECORD_SECTION:-^### 3\.1}"

# Reads a section on stdin and the applied window as $1. Prints `figures <n>`, then one
# `bad <token>` line per number-like figure that is not the applied one, then `digits yes|no`.
# In python because the rule spans lines, hyphens and markdown emphasis, and because the number-word
# list belongs in one place rather than in three greps.
window_figures() {
  # THE PROGRAM GOES ON -c AND NOT ON STDIN. `python3 - <<'PY'` reads the PROGRAM from stdin, so the
  # piped section was consumed by the heredoc and sys.stdin.read() saw nothing: every assertion below
  # would have been made against an empty string. The control caught it — the real documents failed
  # the moment this function was introduced. Part 6 already had this right; this copy did not.
  python3 -c '
import re, sys

applied = sys.argv[1]
text = sys.stdin.read()

WORDS = {
    "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "seven": 7, "eight": 8,
    "nine": 9, "ten": 10, "eleven": 11, "twelve": 12, "thirteen": 13, "fourteen": 14,
    "fifteen": 15, "sixteen": 16, "seventeen": 17, "eighteen": 18, "nineteen": 19, "twenty": 20,
    "thirty": 30, "forty": 40, "fifty": 50, "sixty": 60, "seventy": 70, "eighty": 80,
    "ninety": 90, "hundred": 100,
}

# TWO POSITIONS, because round 4 found the number on the other side of the unit. The first pattern
# is the token immediately BEFORE `day`/`days` — separated by a space or a hyphen, with the word not
# running on into another word, so `daily` is not a period and `fourteen-day` yields `fourteen`. The
# second is `day 14` / `at day 14`, which read as naturally in a retention sentence and which the
# first cannot see. Over-flagging is the accepted direction here, so a phrase like "day 1 of the
# month" would be a finding; neither section says anything of the kind and the control proves it.
PREFIX = re.compile(r"(?<![0-9A-Za-z])([0-9A-Za-z]+)[-–—\s]+days?(?![A-Za-z])", re.I)
POSTFIX = re.compile(r"(?<![A-Za-z])days?[-–—\s]+([0-9]+(?:st|nd|rd|th)?)(?![0-9A-Za-z])", re.I)

# ORDINALS COUNT AS DIGITS — `on the 14th day` passed every earlier version, because `14th` is
# neither isdigit() nor a word in the list, so it took the `continue` arm meant for grammar. It is
# natural English for a deletion date rather than an adversarial spelling, which is why it is closed
# rather than listed as a miss.
ORDINAL = re.compile(r"^([0-9]+)(st|nd|rd|th)$")

figures = 0
bad = []
digits_present = False
for match in list(PREFIX.finditer(text)) + list(POSTFIX.finditer(text)):
    token = match.group(1).lower()
    ordinal = ORDINAL.match(token)
    if ordinal:
        token = ordinal.group(1)
    if token.isdigit():
        value = int(token)
    elif token in WORDS:
        value = WORDS[token]
    else:
        continue                      # grammar, or a number word outside the list — see the header
    figures += 1
    if value == int(applied):
        # An ordinal or a postfix occurrence does NOT satisfy the digit requirement: what that
        # requirement is for is one canonical spelling a reader cannot mistake, and "deleted on the
        # 3rd day" is not it.
        if token.isdigit() and not ordinal and match.re is PREFIX:
            digits_present = True
    else:
        bad.append(match.group(1).lower())

print("figures %d" % figures)
for token in bad:
    print("bad %s" % token)
print("digits %s" % ("yes" if digits_present else "no"))
' "$1"
}

if [ -n "$default_days" ]; then
  for pair in "$PRIVACY_NOTICE:$NOTICE_SECTION" "$PROCESSING_RECORD:$RECORD_SECTION"; do
    doc="${pair%%:*}"; heading="${pair#*:}"
    if [ ! -f "$doc" ]; then
      err "$doc does not exist — the retention period this estate applies is stated in it"
      continue
    fi
    section="$(section_of "$doc" "$heading")"
    if [ -z "$section" ]; then
      err "$doc has no section matching '$heading', so the period this estate applies is stated nowhere this check can find — and a check that cannot see its own subject reports success. That section is where the unactivated-account window is told to a data subject or a regulator (decisions.md D94, D91 §5); if it has been renumbered, move HC_NOTICE_SECTION/HC_RECORD_SECTION with it." "$doc"
      continue
    fi
    report="$(printf '%s\n' "$section" | window_figures "$default_days" || true)"
    if [ -z "$report" ]; then
      err "the window figures in $doc could not be read (python3 missing or failing), so nothing about that document is established." "$doc"
      continue
    fi
    figures="$(printf '%s\n' "$report" | sed -n 's/^figures //p')"
    digits="$(printf '%s\n' "$report" | sed -n 's/^digits //p')"
    others="$(printf '%s\n' "$report" | sed -n 's/^bad //p' | sort -u | tr '\n' ' ')"
    if [ "$digits" != "yes" ]; then
      err "$doc's section at '$heading' does not state the window in DIGITS beside the word day — '$default_days days' or '$default_days-day'. The gateway deletes an unactivated account after $default_days days (AccountRetention.DEFAULT_RETENTION_DAYS) and that section is what tells a data subject or a regulator how long that is (decisions.md D94, D91 §5). The digit form is required because it is the one spelling this check can assert exactly; prose beside it is welcome, prose instead of it is not." "$doc"
    elif [ -n "$others" ]; then
      err "$doc's section at '$heading' also names a period the estate does not apply: $others. The code applies $default_days days (AccountRetention.DEFAULT_RETENTION_DAYS). Two periods in the section that defines one is how a headline drifts from the paragraph under it — measured twice, in two review rounds. If a second period genuinely has to be named there, say so in D94 and widen this deliberately." "$doc"
    else
      # WHAT IT INSPECTED, AND NO CLAIM ABOUT WHAT IT DID NOT. "all of them the applied one" read as
      # "there is no wrong period in this section", which this check cannot know — the same
      # over-claim as round 2's "and no other", in different words. The digit requirement is the
      # part that is exact, so that is what the line asserts; the ban is reported as what it is.
      ok "$doc's section at '$heading' states $default_days days in digits (required, exact); the best-effort ban inspected $figures period figure(s) there and flagged none — it does not establish that the section names no other period, see this file's header"
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
  # AND A PATH THAT IS NOT A STRING LITERAL IS REFUSED RATHER THAN IGNORED — NEW-47 review, round 2.
  # The comment above says "the formatter cannot hide a door", and a formatter cannot; a CONSTANT can.
  # `.pathMatchers(API_REGISTER).permitAll()` yields no literal, so the old derivation simply did not
  # see that door and the whole check printed ok — as the line-bound grep before it did. A path built
  # by concatenation is the same hole: `.pathMatchers(PREFIX + "/signup")` yields "/signup", which does
  # not start with /api/ and is silently dropped.
  #
  # So any argument that is not a plain string literal is an ERROR naming the call. A leading
  # `HttpMethod.X` is the one permitted exception, because it is Spring
  # idiom rather than a hidden path — PaymentWebhookRouteConfiguration uses that form — and it still
  # requires every remaining argument to be a literal. The cost of the strictness is that a future
  # constant has to be inlined or argued; that is the intended direction.
  #
  # WHAT IS STILL ONLY STATED, because "closed rather than stated" is true of the constant and not of
  # this: A DOOR THAT IS NOT A `pathMatchers` CALL AT ALL. `.anyExchange().permitAll()` — or a
  # `securityMatcher` widening what this chain governs, or a second `SecurityWebFilterChain` bean
  # permitting everything — opens every path in one line, derives no path, and leaves this check at
  # exit 0 with nothing to say. The estate backstops it elsewhere and only partly:
  # `InternalApiPermitIT` and `PaymentWebhookRoutePermitIT` ask the running container about specific
  # paths, and D74's measurement of the reactive default-deny is what makes an unmatched exchange a
  # 401 rather than a pass — but nothing here would notice the line. A rate limit is not what would
  # be missing in that case; authentication would be.
  permit_report="$(java_src "$SECURITY_CONFIG" | python3 -c '
import re, sys

src = sys.stdin.read()
paths = []
opaque = []
# [^)] spans newlines and \s* absorbs the wrap, so the formatter cannot hide a door.
for call in re.findall(r"\.pathMatchers\(([^)]*)\)\s*\.permitAll\(\)", src):
    for argument in call.split(","):
        argument = argument.strip()
        if not argument:
            continue
        if re.fullmatch(r"HttpMethod\.[A-Z]+", argument):
            continue
        literal = re.fullmatch(r"\"([^\"]*)\"", argument)
        if not literal:
            opaque.append(argument)
        elif literal.group(1).startswith("/api/"):
            paths.append(literal.group(1))
for argument in sorted(set(opaque)):
    print("opaque %s" % argument)
print("paths %s" % " ".join(sorted(set(paths))))
' || true)"
  for argument in $(printf '%s\n' "$permit_report" | sed -n 's/^opaque //p'); do
    err "$SECURITY_CONFIG permits a path this check cannot read: .pathMatchers($argument).permitAll(). A public door whose path comes from a constant or a concatenation is invisible to the derivation below, so nothing about its rate limit is established — and this check would otherwise print ok. Inline the literal, or widen the derivation deliberately in decisions.md D94." "$SECURITY_CONFIG"
  done
  permit_paths="$(printf '%s\n' "$permit_report" | sed -n 's/^paths //p')"
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
