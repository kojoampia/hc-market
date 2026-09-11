#!/usr/bin/env bash
# ==============================================================================
#  shared-plane-wiring.sh's own test — decisions.md D66.
#
#  The check beside this file is green on a correct tree, which says nothing at all: six checks in
#  this repository have reported `ok` having read nothing, and two of them were consolidations of
#  earlier fail-opens. This constructs each broken state on a COPY of the real files, asserts the
#  mutation actually applied before believing its result, and requires the check to go red.
#
#  Every mutation is one this repository has either had or would plausibly write:
#
#    1  the network name hardcoded          — NEW-25's exact shape, and the state `main` was in
#    2  the broker address hardcoded        — the same, one variable along
#    3  ONLY the binder property hardcoded  — the half-move the compose file warns about at the line
#    4  the Consul host hardcoded
#    5  a default renamed in the script     — the two-defaults drift the pairing exists to catch
#    6  the default assignment commented out
#    7  the compose `name:` commented out   — D64's review's fail-open, asked of this check
#    8  env_for_compose stops exporting     — a `--down` addressing a different plane
#    9  the membership refusal deleted      — preflight green against a plane nothing can reach
#   10  the network key renamed             — the enumerated key, which must REFUSE rather than skip
#   11  one service given its own broker    — two planes in one estate, reported as a count
#   12  the compose file absent             — an unreadable subject, which must not read as clean
#
#  Ten more since D69, all about deploy-dev.sh's copy — backlog NEW-29. Trust the list and not the
#  number; this repository has written that count wrong three times. The first two are cases 9 and
#  10's shapes one script along; the rest are part 5, which exists because part 4's refusal is FATAL
#  and that is only safe while a teardown cannot reach it. Cases 20 and 21 are the two that a
#  per-branch DENY-LIST version of part 5 passed — reproduced at review, which is why it is an exact
#  set now (D69 §10):
#
#   13  the DEV membership refusal deleted  — the defect D69 closed, back again
#   14  the dev function renamed            — an awk range that lifts nothing must not pass
#   15  preflight added to `down`           — a broken plane would wedge the teardown
#   16  the dev router renamed              — part 5's subject gone, which must not read as clean
#   17  an expected branch renamed          — the set compared against a branch that is not there
#   18  preflight never called at all       — every teardown assertion true, of a script that checks
#                                             no plane on any action
#   19  the shell stripper absent           — one file every shell matcher in the estate trusts, and
#                                             two of them once printed `ok` having read nothing
#                                             without it. THE COUNT IS DERIVED AND PRINTED BY THIS
#                                             CASE, not written here: this sentence said "four
#                                             checks", the same sentence in
#                                             host-probe-attribution-test.sh said "five", and NEW-38
#                                             — the item raised to fix the first — named five by a
#                                             composition that missed one (decisions.md D82)
#   20  up's own preflight deleted          — an `up` that starts the estate with NO plane check;
#                                             the deny-list passed this, and it is the worse half
#   21  a new branch gains preflight        — `doctor) preflight; …`: the next diagnostic wedged by a
#                                             broken plane, invisible to a deny-list
#   22  the branch labels unreadable        — a set comparison over zero labels, which is this
#                                             family's empty-subject fail-open
#
#  Six more since D71 — backlog NEW-32 — and NONE of them is about a refusal going missing. Every
#  one leaves the function fatal and changes only WHICH CAUSE it names, which is what decides
#  whether whoever reads it goes and restarts a shared plane with nothing wrong with it (D66 §7).
#  Three per copy, because the copy that already had the fix was the one CI could not see it in:
#
#   23  quality's membership status check made vacuous  — an unaskable daemon reported as a broker
#                                                         on the wrong network: NEW-32 itself
#   24  quality's unaskable-network arm made unreachable — the same one docker object along, and it
#                                                         ACCEPTS rather than misnaming
#   25  quality's "not found" arm removed              — the POSITIVE CONTROL: an absent network
#                                                         reported as an unaskable daemon, which is
#                                                         the defect mirrored and passes every
#                                                         other assertion in part 4
#   26  the DEV membership status check made vacuous   — D69's fix, folded back; CI was green on
#                                                         exactly this before D71 (measured)
#   27  the dev unaskable-network arm made unreachable
#   28  the dev "not found" arm removed
#
#  Two more from D71's review, one per copy, and they are the only cases here that pin a match
#  rather than a status check:
#
#   29  quality's absence match loosened to the bare `not found` substring — which an unanswerable
#        docker CLI carries itself, so the loose form routes it to "start hc-infra": NEW-32's cost
#        resurrected one arm along. Measured: the other five states answer identically, which is
#        why the sixth stub state exists
#   30  the same, in deploy-dev.sh
#
#      ./.github/checks/shared-plane-wiring-test.sh
# ==============================================================================
set -Eeuo pipefail

HERE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE_DIR/../.." && pwd)"
CHECK="$HERE_DIR/shared-plane-wiring.sh"
[[ -f "$CHECK" ]] || { echo "cannot find $CHECK" >&2; exit 1; }

pass=0; fail=0
note() { printf '  ok   %s\n' "$*"; pass=$((pass + 1)); }
bad()  { printf '  FAIL %s\n' "$*" >&2; fail=$((fail + 1)); }

WORK="$(mktemp -d "${TMPDIR:-/tmp}/hc-shared-plane-test-XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

fresh() { # fresh <case> -> prints the sandbox directory holding a pristine copy of both files
  local d="$WORK/$1"; mkdir -p "$d"
  cp "$ROOT/quality/compose.yml" "$d/compose.yml"
  cp "$ROOT/quality/startup.sh"  "$d/startup.sh"
  printf '%s' "$d"
}

# D69's sandbox: deploy-dev.sh alone. Parts 1 and 2 are left pointed at the REAL dev pair, because
# every mutation below is inside a function body or the router and neither part reads those — and a
# copy of the compose file would have to be rendered from a directory its relative paths do not
# describe. Part 4 and part 5 are pointed at the copy.
fresh_dev() { # fresh_dev <case> -> prints the sandbox directory holding a pristine deploy-dev.sh
  local d="$WORK/$1"; mkdir -p "$d"
  cp "$ROOT/deploy/deploy-dev.sh" "$d/deploy-dev.sh"
  printf '%s' "$d"
}

# HC_PLANE_FNS as well as HC_STARTUP, or part 4 would silently walk the REAL scripts while the case
# mutated a copy — case 9 would then be green for a reason that has nothing to do with the mutation.
run_check() { # run_check <sandbox> -> exit status of the check over that sandbox only
  ( cd "$ROOT" && HC_PAIRS="$1/compose.yml:$1/startup.sh" HC_STARTUP="$1/startup.sh" \
      HC_PLANE_FNS="$1/startup.sh:check_shared_plane" HC_DEV_SCRIPT="$ROOT/deploy/deploy-dev.sh" \
      bash -e "$CHECK" >"$1/out.txt" 2>&1 ) && return 0 || return $?
}

run_dev_check() { # run_dev_check <sandbox holding deploy-dev.sh>
  ( cd "$ROOT" && HC_PAIRS="deploy/docker/docker-compose.dev.yml:deploy/deploy-dev.sh" \
      HC_STARTUP="quality/startup.sh" \
      HC_PLANE_FNS="$1/deploy-dev.sh:shared_plane" HC_DEV_SCRIPT="$1/deploy-dev.sh" \
      bash -e "$CHECK" >"$1/out.txt" 2>&1 ) && return 0 || return $?
}

RUNNER=run_check

# expect_red <case> <what changed> <a string the mutant must now contain> — plus the control that
# the ORIGINAL text is gone, because a sed that matched nothing leaves a green check reading green
# for the wrong reason. That is not hypothetical: it is how five harnesses in this repository
# reported success while testing nothing.
#
# And an expected fragment of the ERROR, because "red" is not the assertion — a check that is red on
# everything is red here too, and a mutation that trips a neighbouring sub-check reports a defect
# nobody introduced. Each case must fail through the door it was aimed at.
expect_red() { # expect_red <sandbox> <name> <must-be-present> <must-be-absent> <file> <error-fragment>
  local d="$1" name="$2" present="$3" absent="$4" f="$5" want="$6"
  grep -qF -- "$present" "$f" || { bad "$name — the mutation did not apply ('$present' is not in the file)"; return; }
  if [[ -n "$absent" ]] && grep -qF -- "$absent" "$f"; then
    bad "$name — the mutation did not replace the original ('$absent' is still in the file)"; return
  fi
  if [[ "$f" == *.sh ]] && ! bash -n "$f"; then bad "$name — the mutant script does not parse, so the check aborted rather than failing"; return; fi
  if "$RUNNER" "$d"; then
    bad "$name — the check PASSED on a broken tree"
    sed -n '1,200p' "$d/out.txt" >&2
    return
  fi
  if grep -qF -- "$want" "$d/out.txt"; then
    note "$name → red"
  else
    bad "$name — red, but not through the door it was aimed at (no '$want' in the output)"
    grep '::error::' "$d/out.txt" >&2 || true
  fi
}

printf '\nControl: the committed files\n'
d="$(fresh control)"
if run_check "$d"; then note "green on an unmutated copy"; else bad "the check is red on the committed files"; cat "$d/out.txt" >&2; fi

# THE CONTROL THAT MATTERS MOST, because it is a false positive rather than a false negative and it
# was live: `render()` shells out with `env VAR=… docker compose`, which inherits the caller's
# environment, so with any of the three already EXPORTED the "unset" baseline rendered the ambient
# value and the check reported it as the compose file disagreeing with the script — red on a correct
# tree, naming the wrong cause. Not hypothetical: CLAUDE.md tells people to export exactly these
# three together, so whoever runs this locally is the likeliest person to have them set.
#
# All three, one at a time, and the network one separately from the two hostnames because they reach
# the render through different compose constructs and could regress apart.
printf '\nControl: an ambient HC_SHARED_* must not read as a disagreement\n'
for amb in HC_SHARED_NETWORK=ambient-net HC_SHARED_CONSUL=ambient-consul HC_SHARED_KAFKA=ambient-kafka; do
  d="$(fresh "ambient-${amb%%=*}")"
  if ( export "${amb?}"; run_check "$d" ); then
    note "green with $amb exported"
  else
    bad "the check is RED on a correct tree with $amb exported — it is describing its own environment as a defect"
    grep '::error::' "$d/out.txt" >&2 || true
  fi
done

printf '\nCompose: each half hardcoded again\n'
d="$(fresh m1)"; sed -i 's|name: ${HC_SHARED_NETWORK:-hcnet}|name: hcnet|' "$d/compose.yml"
expect_red "$d" "1  the network name hardcoded" 'name: hcnet' 'name: ${HC_SHARED_NETWORK' "$d/compose.yml" "still joins 'hcnet'"

d="$(fresh m2)"; sed -i 's|SPRING_KAFKA_BOOTSTRAP_SERVERS: ${HC_SHARED_KAFKA:-hc-shared-quality-kafka}:9092|SPRING_KAFKA_BOOTSTRAP_SERVERS: hc-shared-quality-kafka:9092|' "$d/compose.yml"
expect_red "$d" "2  the broker address hardcoded" 'SPRING_KAFKA_BOOTSTRAP_SERVERS: hc-shared-quality-kafka:9092' '' "$d/compose.yml" "both must be 'hc-ci-probe-kafka:9092'"

d="$(fresh m3)"; sed -i 's|SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS: ${HC_SHARED_KAFKA:-hc-shared-quality-kafka}:9092|SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS: hc-shared-quality-kafka:9092|' "$d/compose.yml"
expect_red "$d" "3  only the BINDER property hardcoded" 'SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS: hc-shared-quality-kafka:9092' 'BINDER_BROKERS: ${HC_SHARED_KAFKA' "$d/compose.yml" "both must be 'hc-ci-probe-kafka:9092'"

d="$(fresh m4)"; sed -i 's|SPRING_CLOUD_CONSUL_HOST: ${HC_SHARED_CONSUL:-hc-shared-quality-consul}|SPRING_CLOUD_CONSUL_HOST: hc-shared-quality-consul|' "$d/compose.yml"
expect_red "$d" "4  the Consul host hardcoded" 'SPRING_CLOUD_CONSUL_HOST: hc-shared-quality-consul' 'SPRING_CLOUD_CONSUL_HOST: ${HC_SHARED_CONSUL' "$d/compose.yml" "renders SPRING_CLOUD_CONSUL_HOST='hc-shared-quality-consul'"

printf '\nDefaults: the pair that must stay one value\n'
d="$(fresh m5)"; sed -i 's|^SHARED_KAFKA="${HC_SHARED_KAFKA:-hc-shared-quality-kafka}"|SHARED_KAFKA="${HC_SHARED_KAFKA:-hc-shared-dev-kafka}"|' "$d/startup.sh"
expect_red "$d" "5  the script's default renamed" 'hc-shared-dev-kafka' '' "$d/startup.sh" "defaults HC_SHARED_KAFKA to 'hc-shared-dev-kafka'"

d="$(fresh m6)"; sed -i 's|^SHARED_NETWORK="${HC_SHARED_NETWORK:-hcnet}"|# SHARED_NETWORK="${HC_SHARED_NETWORK:-hcnet}"\nSHARED_NETWORK="hcnet"|' "$d/startup.sh"
expect_red "$d" "6  the default assignment commented out, the literal beside it" 'SHARED_NETWORK="hcnet"' '' "$d/startup.sh" "defaults HC_SHARED_NETWORK to '«nothing this check can read»'"

d="$(fresh m7)"; sed -i 's|^    name: ${HC_SHARED_NETWORK:-hcnet}$|    # name: ${HC_SHARED_NETWORK:-hcnet}|' "$d/compose.yml"
expect_red "$d" "7  compose's name: commented out (D64's review's fail-open)" '# name: ${HC_SHARED_NETWORK:-hcnet}' '' "$d/compose.yml" "still joins 'hcnet'"

printf '\nScript behaviour\n'
d="$(fresh m8)"; sed -i 's|^  export HC_SHARED_NETWORK="$SHARED_NETWORK" HC_SHARED_CONSUL="$SHARED_CONSUL" \\$|  : \\|' "$d/startup.sh"
expect_red "$d" "8  env_for_compose stops exporting the three" '  : \' 'export HC_SHARED_NETWORK=' "$d/startup.sh" "env_for_compose exports"

d="$(fresh m9)"; sed -i '/grep -Fxq "\$SHARED_NETWORK"/,+1d;/docker inspect -f .{{range \$k, \$v := \.NetworkSettings\.Networks}}/d' "$d/startup.sh"
expect_red "$d" "9  the membership refusal deleted" 'is not running' 'grep -Fxq' "$d/startup.sh" "accepted a Consul and a broker"

# ------------------------------------------------------------------ D71 / NEW-32, quality's copy --
#
# NOT "the refusal was deleted" — that is case 9. These three leave the function refusing and change
# the CAUSE it names, which is the whole of NEW-32: both readings stop the `up`, and the difference
# is whether an operator is sent to hc-infra to fix a plane that is fine.
#
# Each is a SUBSTITUTION rather than a deletion, so both halves of `expect_red`'s control can be
# fixed strings on one line: the mutant text present, the original gone. A deletion here would leave
# nothing to assert the presence of, and case 14's trap is that a multi-line must-be-absent string
# is split by `grep -F` into an empty first pattern that matches every file.
printf '\nquality/startup.sh: which cause the plane refusal names (decisions.md D71)\n'
# `#` as the delimiter, not `|`: the line being matched ends `)) || die …`, and a `|`-delimited
# expression that stops one character short of it silently becomes six fields and errors out.
d="$(fresh m23)"; sed -i 's#^    (( rc2 == 0 )) #    (( rc2 >= 0 )) #' "$d/startup.sh"
expect_red "$d" "23 the membership status check made vacuous" '(( rc2 >= 0 ))' '(( rc2 == 0 ))' "$d/startup.sh" "refused a plane it could not ask about, but named the wrong cause"

# The arm is made UNREACHABLE rather than deleted, which is why this one accepts instead of
# misnaming: a `case` whose patterns all miss falls through and the function carries on.
d="$(fresh m24)"; sed -i 's|^      \*)$|      *"no docker ever says this"*)|' "$d/startup.sh"
expect_red "$d" "24 the unaskable-network arm made unreachable" '*"no docker ever says this"*)' '' "$d/startup.sh" "ACCEPTED a shared network it could not ask about"

# THE POSITIVE CONTROL, and it is red for the opposite reason to everything above: the fix names two
# outcomes docker separates only by its message, so collapsing them into "could not be asked" keeps
# every other assertion in part 4 green while losing NEW-25's own sentence.
d="$(fresh m25)"; sed -i 's|^      \*"Error response from daemon"\*"not found"\*)$|      *"a network docker will never describe"*)|' "$d/startup.sh"
expect_red "$d" "25 the absent-network arm removed" '*"a network docker will never describe"*)' '*"not found"*)' "$d/startup.sh" "no longer says it is absent"

# 29. THE MATCH LOOSENED, not a status check removed — D71's review. The absence branch requires
#     "Error response from daemon" AND "not found", because the bare substring is one an unanswerable
#     docker CLI produces about itself (`DOCKER_HOST=ssh://…` to a host with no docker). Measured
#     before this case existed: with the loose form the other FIVE states answer identically and only
#     the sixth moves, which is the whole reason that state was added rather than a comment written.
d="$(fresh m29)"; sed -i 's|^      \*"Error response from daemon"\*"not found"\*)$|      *"not found"*)|' "$d/startup.sh"
expect_red "$d" "29 the absence match loosened to a bare substring" '      *"not found"*)' '"Error response from daemon"*"not found"*)' "$d/startup.sh" "blamed the network for a daemon that never answered"

printf '\nSubjects that must be refused rather than skipped\n'
d="$(fresh m10)"; sed -i -e 's|^  hcnet:$|  sharednet:|' -e 's|networks: \[quality, qualitynet, hcnet\]|networks: [quality, qualitynet, sharednet]|' "$d/compose.yml"
expect_red "$d" "10 the network key renamed" '  sharednet:' '' "$d/compose.yml" "declares no network under the key"

d="$(fresh m11)"; sed -i 's|^      SPRING_CLOUD_STREAM_DEFAULT_GROUP: hc-market-payout$|      SPRING_CLOUD_STREAM_DEFAULT_GROUP: hc-market-payout\n      SPRING_CLOUD_CONSUL_HOST: some-other-consul|' "$d/compose.yml"
expect_red "$d" "11 one service given its own Consul" 'SPRING_CLOUD_CONSUL_HOST: some-other-consul' '' "$d/compose.yml" "more than one shared-plane address"

d="$(fresh m12)"; rm -f "$d/compose.yml"
if run_check "$d"; then bad "12 the compose file absent — the check PASSED"; else note "12 the compose file absent → red"; fi

# ---------------------------------------------------------------- deploy-dev.sh, D69 / NEW-29 ----
#
# The subject changes here, so the runner does too: parts 4 and 5 are pointed at a copy of
# deploy-dev.sh while parts 1-3 stay on the real files. RUNNER is restored at the end, because a
# harness that quietly keeps testing the wrong subject is this repository's most repeated defect.
RUNNER=run_dev_check

printf '\ndeploy-dev.sh: the membership refusal (decisions.md D69)\n'
d="$(fresh_dev m13)"
sed -i '/grep -Fxq "\$SHARED_NETWORK"/,+1d;/docker inspect -f .{{range \$k, \$v := \.NetworkSettings\.Networks}}/d' "$d/deploy-dev.sh"
expect_red "$d" "13 the DEV membership refusal deleted" 'exists but is not running' 'grep -Fxq' "$d/deploy-dev.sh" "accepted a Consul and a broker"

# The function names differ between the two copies, which is precisely what left dev unguarded until
# D69: a range carrying the other name lifts nothing, and part 4 would then run both probes against
# an undefined function and could not tell you why.
#
# No must-be-absent string on this one, and that is a limit of the harness rather than a shortcut:
# `expect_red`'s absence test is a fixed-string grep, so the only text that would distinguish the
# renamed definition from the original is `^shared_plane() {` — an anchor -F cannot express, and a
# bare `shared_plane() {` is a substring of the mutant itself. A newline-prefixed pattern is worse
# than useless: grep -F splits on newlines, so the empty first pattern matches every file. What
# stands in for it is that this `sed` substitutes rather than inserts, and the presence assertion
# names the substituted text.
d="$(fresh_dev m14)"; sed -i 's|^shared_plane() {|shared_plane_renamed() {|' "$d/deploy-dev.sh"
expect_red "$d" "14 the dev plane function renamed" 'shared_plane_renamed() {' '' "$d/deploy-dev.sh" "declares no function 'shared_plane'"

# Cases 23-25 in the other copy, and 26 is the one that says what D71 bought: this fix has been in
# deploy-dev.sh since D69 and part 4 was green with it folded back out, measured by doing exactly
# what this case does. The two copies are not a verbatim-copy family (D69 §7), so nothing but a case
# per copy can hold them together here.
printf '\ndeploy-dev.sh: which cause the plane refusal names (decisions.md D71)\n'
d="$(fresh_dev m26)"; sed -i 's#^    (( rc2 == 0 )) #    (( rc2 >= 0 )) #' "$d/deploy-dev.sh"
expect_red "$d" "26 the DEV membership status check made vacuous" '(( rc2 >= 0 ))' '(( rc2 == 0 ))' "$d/deploy-dev.sh" "refused a plane it could not ask about, but named the wrong cause"

d="$(fresh_dev m27)"; sed -i 's|^      \*)$|      *"no docker ever says this"*)|' "$d/deploy-dev.sh"
expect_red "$d" "27 the dev unaskable-network arm made unreachable" '*"no docker ever says this"*)' '' "$d/deploy-dev.sh" "ACCEPTED a shared network it could not ask about"

d="$(fresh_dev m28)"; sed -i 's|^      \*"Error response from daemon"\*"not found"\*)$|      *"a network docker will never describe"*)|' "$d/deploy-dev.sh"
expect_red "$d" "28 the dev absent-network arm removed" '*"a network docker will never describe"*)' '*"not found"*)' "$d/deploy-dev.sh" "no longer says it is absent"

d="$(fresh_dev m30)"; sed -i 's|^      \*"Error response from daemon"\*"not found"\*)$|      *"not found"*)|' "$d/deploy-dev.sh"
expect_red "$d" "30 the dev absence match loosened to a bare substring" '      *"not found"*)' '"Error response from daemon"*"not found"*)' "$d/deploy-dev.sh" "blamed the network for a daemon that never answered"

# PART 5 IS AN EXACT SET, so both directions are constructed: an action that GAINED preflight (which
# a broken plane can then wedge) and one that LOST it (which starts the estate with no plane check at
# all). Cases 20 and 21 are the two the deny-list version of this part passed, reproduced at review
# before it was rewritten — 20 is the one the reviewer found first and the more dangerous of the two.
printf '\ndeploy-dev.sh: the exact set of actions that reach a fatal preflight\n'
d="$(fresh_dev m15)"; sed -i 's|^  down)$|  down)\n    preflight|' "$d/deploy-dev.sh"
expect_red "$d" "15 preflight added to the down branch" '  down)
    preflight' '' "$d/deploy-dev.sh" "are not accounted for: down"

d="$(fresh_dev m16)"; sed -i 's|^case "\$COMMAND" in$|case "${COMMAND}" in|' "$d/deploy-dev.sh"
expect_red "$d" "16 the dev router renamed" 'case "${COMMAND}" in' 'case "$COMMAND" in' "$d/deploy-dev.sh" "router this check can read"

# An action RENAMED, not deleted: deleting `status)` removes an action and is nobody's defect, while
# renaming one this check is told about leaves it comparing the set against a branch that is not
# there. Both halves of the difference must be reported — `up` missing, `start` unaccounted for.
d="$(fresh_dev m17)"; sed -i 's|^  up)$|  start)|' "$d/deploy-dev.sh"
expect_red "$d" "17 an expected branch renamed" '  start)' '  up)' "$d/deploy-dev.sh" "has no branch for: up"

d="$(fresh_dev m18)"; sed -i -e 's|^    preflight$|    :|' -e 's|^  restart) preflight$|  restart) :|' "$d/deploy-dev.sh"
expect_red "$d" "18 preflight never called at all" '  restart) :' '  restart) preflight' "$d/deploy-dev.sh" "never calls preflight from any router branch"

# 20. THE ONE THE DENY-LIST PASSED. `up` is the only action that both joins the plane and is the
#     estate's entry point, so an `up` with no preflight is NEW-29's defect in a stronger form: not a
#     plane checked wrongly but a plane, a JDK, a seed file and a profile never checked at all. Two
#     `    preflight` lines exist (up, reseed); this deletes the FIRST, which is up's.
#     `sed`'s `n` rather than a first-match `awk`, so the line replaced is provably the one after
#     `  up)` and a single-line presence assertion is enough to say WHICH branch lost it. A count
#     assertion stands beside it because a marker's presence cannot say the original went: two
#     `    preflight` lines must become one, and `expect_red`'s absence test is a fixed-string grep
#     that cannot count (nor span lines — see case 14).
d="$(fresh_dev m20)"
sed -i '/^  up)$/{n;s|^    preflight$|    : # D69 test: up no longer preflights|;}' "$d/deploy-dev.sh"
n20="$(grep -c '^    preflight$' "$d/deploy-dev.sh" || true)"
[[ "$n20" == 1 ]] || bad "20 — the mutation did not apply as intended ($n20 bare preflight call lines, wanted 1)"
expect_red "$d" "20 up's preflight deleted (the deny-list passed this)" ': # D69 test: up no longer preflights' '' "$d/deploy-dev.sh" "Must call it and do not: up"

# 21. THE OTHER ONE. A deny-list cannot see an action nobody anticipated, and the next diagnostic
#     added to this router is exactly that: wedged by a broken plane, with CI silent.
d="$(fresh_dev m21)"; sed -i 's|^  status)  compose ps ;;$|  status)  compose ps ;;\n  doctor)  preflight; compose ps ;;|' "$d/deploy-dev.sh"
expect_red "$d" "21 a new branch gains preflight (the deny-list passed this)" '  doctor)  preflight; compose ps ;;' '' "$d/deploy-dev.sh" "are not accounted for: doctor"

# 22. The router is present and its LABELS are not readable — every branch re-indented. A set
#     comparison over zero labels is the empty-subject fail-open this family keeps producing, so it
#     must be its own error rather than "nothing calls preflight".
#     No absence string: a re-indented `      up)` CONTAINS `  up)`, so a fixed-string absence test
#     would report the mutation as unapplied. A count stands in for it — seven two-space labels must
#     become none.
d="$(fresh_dev m22)"
sed -i -E 's|^  ([a-z*]+\))|      \1|' "$d/deploy-dev.sh"
n22="$(grep -cE '^  [a-z*]+\)' "$d/deploy-dev.sh" || true)"
[[ "$n22" == 0 ]] || bad "22 — the mutation did not apply as intended ($n22 two-space labels remain, wanted 0)"
expect_red "$d" "22 the router's branch labels unreadable" '      up)' '' "$d/deploy-dev.sh" "yielded no branch labels"

RUNNER=run_check

# 19. THE SHELL STRIPPER IS ONE FILE EVERY SHELL MATCHER IN THE ESTATE TRUSTS, and absent it two of
#     them once printed `ok` having read nothing (D62's review). Part 5 must exit 1 naming the cause
#     instead — asked by pointing HC_STRIP_SH at nothing, which is the only failure of that file this
#     harness can construct without touching a file the other callers are also reading.
#
#     THE COUNT IS DERIVED AND PRINTED, never written — decisions.md D82, backlog NEW-38. This comment
#     said "four checks"; the same sentence in host-probe-attribution-test.sh said "five"; and NEW-38,
#     the item raised to correct the first, named five by a composition that missed one. Three
#     statements of one number and three different wrong answers, which is why the fix is a line of
#     shell rather than a corrected constant. Printed rather than asserted against an expected value,
#     because a *new* caller is not a defect — what is a defect is a sentence claiming to know how
#     many there are. A floor of 2 is asserted, since the whole point of the case below is that more
#     than one caller depends on this file.
printf '\ndeploy-dev.sh: the stripper part 5 depends on\n'
sh_callers="$(grep -rl 'strip-sh-comments\.awk' "$ROOT/.github/" 2>/dev/null \
  | grep -v 'checks/strip-sh-comments\.awk$' \
  | xargs -r grep -lE "awk[[:space:]]+-f[^|;]*strip-sh-comments|STRIP[A-Z_]*=[^=]*strip-sh-comments|awkfile=[^=]*strip-sh-comments" \
  | sort -u)"
sh_n="$(printf '%s\n' "$sh_callers" | grep -c . || true)"
sh_steps="$(awk '/^      - name: /{n=$0} /strip-sh-comments\.awk/{print n}' \
  "$ROOT/.github/workflows/build.yml" | sort -u | grep -c . || true)"
if (( sh_n >= 2 )); then
  note "the shell stripper is invoked by $sh_n files, and $sh_steps build.yml step(s) name it (two measures, derived)"
  printf '%s\n' "$sh_callers" | sed "s%^$ROOT/%      %"
else
  bad "only $sh_n file invokes strip-sh-comments.awk, so this case guards a dependency nothing has — the derivation found nothing, which reads as a stripper no check trusts"
fi
d="$WORK/m19"; mkdir -p "$d"
if ( cd "$ROOT" && HC_STRIP_SH="$d/does-not-exist.awk" bash -e "$CHECK" >"$d/out.txt" 2>&1 ); then
  bad "19 the shell stripper absent — the check PASSED"
elif grep -qF -- "could not strip comments and did not run" "$d/out.txt"; then
  note "19 the shell stripper absent → red, naming the cause"
else
  bad "19 the shell stripper absent — red, but not through the door it was aimed at"
  grep '::error::' "$d/out.txt" >&2 || true
fi

printf '\n%d ok, %d failed\n' "$pass" "$fail"
exit $(( fail > 0 ))
