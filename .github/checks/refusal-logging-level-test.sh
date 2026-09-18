#!/usr/bin/env bash
#
# The D97 refusal-logging check must fail on each of the states it exists to refuse.
#
# WHY THIS EXISTS. `A deliberate refusal may not be logged at ERROR, nor echo its arguments` guards a
# GENERATED file, which is the one class of subject where the defect comes back on its own: `jhipster
# jdl --force` restores `log.error` and `Arrays.toString(joinPoint.getArgs())` in all five services
# and nothing else in the build notices. A check nobody drives is a claim, and the first version of
# this one was already measured passing a broken tree — its positive control greped for the bare name
# `logAfterThrowing`, which a rename to `logAfterThrowingRenamed` satisfies as a substring, so the
# ban ran against a file whose advices it could no longer recognise and reported ok. Case 11 is that
# state.
#
# HOW IT DRIVES THE SHIPPED CODE. The step is lifted out of build.yml by name, exactly as
# filter-chain-precedence-test.sh does, so what runs is the shipped text and not a restatement of it.
# An empty lift is fatal rather than skipped: a renamed or reindented step would otherwise leave every
# assertion below running against an empty script and passing.
#
# It runs against a SYNTHETIC tree rather than the repository, because most of the states are
# breakages — a deleted stripper, a missing aspect, a drifted copy — and a test that mutates the
# checkout it runs in leaves the checkout broken when it fails part-way through.
#
# THE FIXTURES ARE THE SHIPPED FILES, copied in. A hand-written stand-in would let the check and the
# real aspect drift apart in opposite directions while this test stayed green, which is the failure
# this whole family of checks keeps re-finding one layer up.
# NOTE ON `pipefail` AND `grep -q`, because this file sets one and uses the other. `grep -q` exits at
# its first match, its producer takes SIGPIPE and dies 141, and under `pipefail` the pipeline then
# reports a MATCH AS FAILURE — measured on this repository's own `account-lifecycle-guards.sh`, whose
# placeholder ban cannot fire for exactly that reason and answers differently under load (backlog
# NEW-71). Every `grep -q` here therefore reads a FILE or a HERESTRING, never a pipe.
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 1

workflow=.github/workflows/build.yml
stepname="A deliberate refusal may not be logged at ERROR, nor echo its arguments"
aspect_path=src/main/java/net/jojoaddison/aop/logging/LoggingAspect.java
guard_path=src/test/java/net/jojoaddison/aop/logging/LoggingAspectRefusalUnitTest.java
cfg_path=src/main/java/net/jojoaddison/config/LoggingAspectConfiguration.java
fail=0
cases=0

pass() {
  cases=$((cases + 1))
  echo "ok   $1"
}
bad() {
  cases=$((cases + 1))
  echo "::error file=$workflow::$1"
  fail=1
}

for f in "$workflow" .github/checks/strip-comments.awk "payout/$aspect_path" "payout/$guard_path" "payout/$cfg_path"; do
  if [ ! -f "$f" ]; then
    echo "::error::$f does not exist, so this test cannot drive the check it exists to drive. See decisions.md D97."
    exit 1
  fi
done

work=$(mktemp -d)
step="$work/step.sh"
trap 'rm -rf "$work"' EXIT

# Stops at the next step's `- name:` OR at the comment block that introduces it, so what is lifted is
# this step and nothing else.
awk -v want="      - name: $stepname" '
  $0 == want { p = 1; next }
  p && (/^      - name: / || /^      #/) { exit }
  p { print }
' "$workflow" | sed -e '/^ *run: |$/d' -e 's/^          //' > "$step"

if [ ! -s "$step" ]; then
  echo "::error file=$workflow::could not lift the step named '$stepname' out of the workflow — it has been renamed or reindented, and every assertion below would run against an empty script and pass. See decisions.md D97."
  exit 1
fi
if ! grep -q 'strip-comments.awk' "$step"; then
  echo "::error file=$workflow::the lifted step does not call the shared stripper, so either the lift took the wrong block or the check has grown a private stripper. See decisions.md D97 and D56."
  exit 1
fi
pass "lifted $(wc -l < "$step") lines of the shipped step"

# ---------------------------------------------------------------------------------------------------
# The synthetic estate: the five services the JDLs name, each carrying the three real files.
tree=$work/estate
SERVICES="booking catalog gateway messaging payout"

build_estate() { # $1 (optional) space-separated service list, default all five
  local list="${1:-$SERVICES}" svc
  rm -rf "$tree"
  mkdir -p "$tree/.github/checks" "$tree/jdl"
  cp .github/checks/strip-comments.awk "$tree/.github/checks/"
  for svc in $list; do
    mkdir -p "$tree/$svc/$(dirname "$aspect_path")" "$tree/$svc/$(dirname "$guard_path")" "$tree/$svc/$(dirname "$cfg_path")"
    printf 'application {\n  config { baseName healthconnect%s }\n}\n' "$svc" > "$tree/jdl/$svc.jdl"
    cp "payout/$aspect_path" "$tree/$svc/$aspect_path"
    cp "payout/$guard_path" "$tree/$svc/$guard_path"
    cp "payout/$cfg_path" "$tree/$svc/$cfg_path"
  done
}

run_estate() { (cd "$tree" && bash -e "$step" 2>&1); }

expect_pass() { # $1 label
  local out rc
  out=$(run_estate)
  rc=$?
  if [ "$rc" = 0 ]; then
    pass "$1"
  else
    bad "$1 — expected the check to pass and it exited $rc: $(printf '%s' "$out" | grep '^::error' | head -1)"
  fi
}

expect_fail() { # $1 label, $2 substring the message must contain
  local out rc
  out=$(run_estate)
  rc=$?
  if [ "$rc" = 0 ]; then
    bad "$1 — THE CHECK PASSED on a state it exists to refuse"
  elif grep -qF "$2" <<< "$out"; then
    pass "$1 — refused, naming: $2"
  else
    bad "$1 — refused, but the message names neither the file nor the cause: $(printf '%s' "$out" | grep '^::error' | head -1)"
  fi
}

# Applies a sed expression to every copy of the aspect. Used where the case must isolate an assertion
# from the copy diff — mutate one service and the diff fires, and the case then proves nothing about
# the ban it was written for. Both of this test's green-expected mutations need it.
all_copies() { # $1 sed expression
  local svc
  for svc in $SERVICES; do sed -i "$1" "$tree/$svc/$aspect_path"; done
}

# 0. THE CONTROL. The shipped files must pass, or every refusal below is refusing something else.
build_estate
expect_pass "the shipped aspect, guard test and configuration pass in all five services"

# 1. The line NEW-65 named, restored. This is what `--force` does.
build_estate
sed -i 's/log\.warn("Refused in/log.error("Refused in/' "$tree/payout/$aspect_path"
expect_fail "the refusal arm back at ERROR" "ERROR-level call"

# 2. The OTHER advice back at ERROR, which is the half a :113-only fix would have left behind. One
#    IllegalStateException refusal — most of what D95's desk throws — is one ERROR line from here and
#    never reaches the catch at all.
build_estate
sed -i '0,/logger(joinPoint)\.warn(/s//logger(joinPoint).error(/' "$tree/payout/$aspect_path"
expect_fail "logAfterThrowing's dev arm back at ERROR" "ERROR-level call"

# 3. The unreachable not-dev arm back at ERROR. It is dead code while the bean is @Profile("dev") —
#    with the bean present, `dev` is active, so the branch above it cannot be false — which is why a
#    check is the only thing that can see it: no test can reach it and no estate runs it. The second
#    occurrence, counted rather than matched on the message, because the message is prose.
build_estate
awk '
  /logger\(joinPoint\)\.warn\(/ { n++; if (n == 2) sub(/\.warn\(/, ".error(") }
  { print }
' "$tree/payout/$aspect_path" > "$work/arm.java" && mv "$work/arm.java" "$tree/payout/$aspect_path"
expect_fail "the unreachable not-dev arm back at ERROR" "ERROR-level call"

# 3b. SLF4J'S OTHER SPELLING. `log.atError().setMessage(…).log()` contains no `.error(`, so a ban
#     written for the generator's spelling alone lets the fluent API through — found by re-reading
#     the check rather than by a run, and closed while it was free: nothing in the estate uses the
#     fluent API today. The refusal arm is rewritten wholesale here because that is what the edit
#     would look like.
build_estate
sed -i 's|log\.warn("Refused in {}(): {}", joinPoint\.getSignature()\.getName(), e\.getMessage());|log.atError().setMessage("Refused").log();|' "$tree/payout/$aspect_path"
expect_fail "the refusal arm rewritten with slf4j's fluent atError" "ERROR-level call"

# 3c. …and the control for it, because a ban on a bare `atError` substring must not catch the WARN
#     spelling beside it. `atWarn` is a legitimate future edit and this check has no business
#     refusing it.
build_estate
all_copies 's|log\.warn("Refused in {}(): {}", joinPoint\.getSignature()\.getName(), e\.getMessage());|log.atWarn().setMessage("Refused in {}(): {}").addArgument(joinPoint.getSignature().getName()).addArgument(e.getMessage()).log();|'
expect_pass "the refusal arm rewritten with slf4j's fluent atWarn stays green"

# 4. The disclosure half. D44's rule broken again, in the arm it was broken in.
build_estate
sed -i 's|log\.warn("Refused in {}(): {}", joinPoint\.getSignature()\.getName(), e\.getMessage());|log.warn("Refused in {}(): {}", java.util.Arrays.toString(joinPoint.getArgs()), e.getMessage());|' "$tree/payout/$aspect_path"
expect_fail "the arguments rendered in the refusal line again" "getArgs() somewhere other"

# 5. The advice's log line deleted rather than re-levelled — D97's third candidate position, which was
#    rejected: the exception translator answers the CALLER and logs only at DEBUG, so with this gone a
#    refusal leaves no record anywhere in the estate.
build_estate
sed -i '/log\.warn("Refused in/d' "$tree/payout/$aspect_path"
expect_fail "the refusal arm's log line deleted" "logs at neither log.warn( nor atWarn"

# 6. The guard test deleted. It is the only thing that goes red after a regeneration, so deleting it
#    instead of the defect is the cheapest way to make CI green and the estate wrong.
build_estate
rm -f "$tree/payout/$guard_path"
expect_fail "the guard test deleted in one service" "is missing"

# 7. The premise about production. Post-D97 the aspect is safe in any profile, so this arm is about
#    keeping D97's and CLAUDE.md's "prod never registers it" true rather than about a disclosure.
build_estate
sed -i '/@Profile(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)/d' "$tree/payout/$cfg_path"
expect_fail "@Profile(dev) removed from the bean" "no longer gates"

# 8. The sixth verbatim-copy family. One service's answer about what an ERROR means diverging from the
#    rest is the drift nothing else here can see.
build_estate
printf '\n// drifted\n' >> "$tree/payout/$aspect_path"
expect_fail "one copy drifts from the derived reference" "differs from"

# 9. The file every text-matching check in this repository trusts. Absent, the bans match nothing.
build_estate
rm -f "$tree/.github/checks/strip-comments.awk"
expect_fail "the shared comment stripper is gone" "is missing, so every expression"

# 10. …and a stripper that outputs nothing must be caught by the positive control rather than
#     satisfying every ban by emptiness. This is D77's ninth fail-open in this check's own shape.
build_estate
printf '{ }\n' > "$tree/.github/checks/strip-comments.awk"
expect_fail "the stripper outputs nothing" "strips to something with no"

# 11. THE CASE THAT CAUGHT THE FIRST VERSION. Both advices renamed in ALL FIVE copies, so the diff
#     stays clean and only the positive control can see it. `grep -q logAfterThrowing` matched
#     `logAfterThrowingRenamed` as a substring and the check printed ok — measured. It matches the
#     declaration now.
build_estate
all_copies 's/logAfterThrowing/logAfterThrowingRenamed/g'
expect_fail "both advices renamed out of recognition, in every copy" "strips to something with no"

# 12. THE OTHER CONTROL, and it is what makes case 4 mean something. `getArgs()` named in PROSE must
#     stay green: this check bans code, not paragraphs, and the whole reason the shared stripper is
#     mandatory is that eight successive checks here were satisfied by a comment.
build_estate
all_copies 's|// The method and the reason this estate composed|// joinPoint.getArgs() was rendered here once — D97. The method and the reason composed|'
expect_pass "getArgs() named only in a comment stays green"

# 13. The derivation's own subject. No JDL is not "no services to check".
build_estate
rm -f "$tree"/jdl/*.jdl
expect_fail "jdl/*.jdl matches nothing" "would scan no application and pass"

# 14. A service the JDL names with no aspect in it — the generator having renamed the class, or a
#     service added to the estate before anybody asked what it logs a refusal at.
build_estate
rm -f "$tree/messaging/$aspect_path"
expect_fail "a JDL-named service with no LoggingAspect" "does not exist, but"

# 15. A family of one is not being compared with anything, and would pass having diffed nothing.
build_estate payout
expect_fail "a single-service estate" "not being compared with anything"

echo
echo "$cases assertions, $([ "$fail" = 0 ] && echo "all passed" || echo "FAILURES ABOVE")"
exit "$fail"
