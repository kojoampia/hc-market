#!/usr/bin/env bash
# ==============================================================================
#  verify-outbox-recovery.sh must put back the aliases it severed — decisions.md D68, backlog NEW-26
#
#  THE STATE THIS CATCHES, measured on this host on 2026-09-10 rather than imagined:
#
#      hc-market-quality-booking   hcnet  Aliases=[]
#      hc-market-quality-catalog   hcnet  Aliases=[hc-market-quality-catalog catalog]
#
#  The script severs booking from hcnet and reconnects it — on an EXIT trap and again at the end.
#  A bare `docker network connect` restores the CONNECTION and not the NAMES: compose publishes a
#  container's service name as a DNS alias when it creates it, a manual reconnect publishes only the
#  container's own name. So the script left the estate slightly smaller than it found it, every run.
#
#  It costs nothing TODAY, and the reason it costs nothing is not the reason the item gave — see
#  D68 §3. `quality/compose.yml` addresses `booking` by short name in FOUR places — two gateway
#  routes, catalog's base URL and payout's, out of nine short-name addresses in all; they resolve over
#  the PROJECT network, and since D64 anything outside gets an answer over `qualitynet` instead. The
#  name never went missing, it moved planes. That is a fact about today's membership, which is
#  exactly the kind of fact that stops being true without anybody editing this script.
#
#  IT TESTS THE BYTES IN THE SCRIPT, not a copy of them: aliases_on_net and reconnect are extracted
#  from the file and evaluated here, as quality-project-checkout-test.sh does with
#  check_project_checkout. Sourcing the whole file is not an option — it books a real booking and
#  severs a real container.
#
#  §1–§3 are structural and need no daemon. §4 is the one that matters and asks docker: a throwaway
#  container with a known multi-alias set, severed and reconnected by the SHIPPED function, with the
#  set compared before and after — and a bare connect beside it as the positive control, because an
#  assertion that aliases came back is satisfied for the wrong reason by a probe that never lost
#  them.
#
#      ./.github/checks/outbox-alias-restore-test.sh
# ==============================================================================
set -Eeuo pipefail

HERE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE_DIR/../.." && pwd)"
SCRIPT="$REPO/deploy/verify-outbox-recovery.sh"
STRIP="$HERE_DIR/strip-sh-comments.awk"
[[ -f "$SCRIPT" ]] || { echo "cannot find $SCRIPT" >&2; exit 1; }
# Guarded, and the guard is the point rather than decoration. Absent the stripper, `awk -f` prints
# nothing and every text assertion below passes having read an empty file — the ninth fail-open in
# this family, produced by consolidating the previous eight. See strip-comments.awk's own header.
[[ -f "$STRIP" ]] || { echo "cannot find $STRIP — every match below would read an empty file" >&2; exit 1; }

pass=0; fail=0
check() { # check <name> <actual> <expected>
  if [[ "$2" == "$3" ]]; then printf '  ok   %s\n' "$1"; pass=$((pass + 1));
  else printf '  FAIL %s\n       expected: %s\n       actual:   %s\n' "$1" "$3" "$2" >&2; fail=$((fail + 1)); fi
}

# Comments stripped before anything is matched, and here that is load-bearing rather than a
# precaution: the script's own header now explains this defect in prose and says `docker network
# connect` twice while doing so. MEASURED on both the correct file and the mutant that puts a bare
# connect back in the trap:
#
#                          raw   stripped
#     correct               3        1
#     bare connect in trap  4        2
#
# So unstripped, §2's count is the code plus however many times the prose mentions it, and the
# expectation would have to be maintained in lockstep with a comment — which is "a check whose reach
# depends on prose is not a check" (D53's review) exactly: an editor tightening the header moves the
# number, and the obvious repair is to change the expectation to match, at which point the check is
# asserting the length of a comment. Stripped, both readings are the code alone.
#
# `|| true` on every grep -c — this file sets pipefail, and a grep that matches nothing exits 1,
# which aborts the run with no output and reads as no failures.
STRIPPED="$(mktemp "${TMPDIR:-/tmp}/hc-d68-stripped-XXXXXX")"
trap 'rm -f "$STRIPPED"' EXIT
awk -f "$STRIP" "$SCRIPT" > "$STRIPPED"

echo "verify-outbox-recovery.sh — it restores the aliases it severed"

# The stripper's own control, on the real subject: it must remove the prose AND leave the code. Both
# halves, because "removes everything" satisfies the first on its own — and a file that strips to
# nothing makes every assertion below vacuously true.
check "the comment stripper leaves the file the same length" \
  "$(wc -l < "$STRIPPED")" "$(wc -l < "$SCRIPT")"
check "…removes the header's prose mentions of the call being guarded" \
  "$(( $(grep -c 'docker network connect' "$SCRIPT" || true) > $(grep -c 'docker network connect' "$STRIPPED" || true) ? 1 : 0 ))" "1"
check "…and leaves real code standing" \
  "$(grep -c '^set -uo pipefail$' "$STRIPPED" || true)" "1"

# 1. THE SET IS READ BACK, NOT NAMED. `--alias booking` would be correct for both compose files in
#    this repository today and is a guess about compose's intent everywhere else: the alias set is
#    whatever compose was told, and `networks: <net>: aliases:` makes it something else again.
check "the alias set is read off docker inspect" \
  "$(grep -c 'aliases_on_net() {.*docker inspect' "$STRIPPED" || true)" "1"
check "…and no alias is spelled out on a connect" \
  "$(grep -c 'network connect.*--alias [a-z]' "$STRIPPED" || true)" "0"

# 2. ONE RECONNECT, AND THE TRAP USES IT. Two call sites written out twice is how one of them gets
#    fixed and the other does not — which is the shape of this very defect, since both were bare.
check "there is exactly one 'docker network connect' in the file" \
  "$(grep -c 'docker network connect' "$STRIPPED" || true)" "1"
check "…and it is inside reconnect()" \
  "$(sed -n '/^reconnect() {/,/^}/p' "$STRIPPED" | grep -c 'docker network connect' || true)" "1"
check "…which builds its flags from the captured set" \
  "$(sed -n '/^reconnect() {/,/^}/p' "$STRIPPED" | grep -c 'BOOKING_ALIASES' || true)" "1"
# THE SAME DEMAND ON THE CAPTURE, and its absence was a fail-open in the first version of this file
# (D68 §11, reproduced by the reviewer). `count == 1` was applied to the connect and not to its
# opposite number, and the argument two lines up is the same argument: a **second** capture — the
# shape of a well-meant "refresh it before reconnecting" edit — overwrites the array with the
# now-empty set after the cut, the reconnect restores nothing, and the script's own
# `chk "…carrying the same aliases"` compares "" against "" and passes. Measured green with the
# defect fully back, which is why the count is here and why `ln_of` below takes the LAST match.
check "there is exactly one capture of the alias set" \
  "$(grep -c '^mapfile -t BOOKING_ALIASES' "$STRIPPED" || true)" "1"

# 2b. AND THE PROBE'S STATUS IS CHECKED. `mapfile < <(aliases_on_net)` discards it, so an unaskable
#     docker read as "it has no aliases": the empty-set note fired with the wrong diagnosis and the
#     script went on to sever and bare-reconnect, which is this defect restored with the run green.
#     D65 and D67 both settled this one object along — non-zero from a probe means the question went
#     UNANSWERED, never that the answer is nothing — and D68 §4's argument is about an EMPTY answer,
#     which is a different thing. Both halves asserted, because either alone is satisfiable: the
#     status-checked assignment must be there AND the mapfile must not call the probe again.
check "the probe's exit status is checked before anything is cut" \
  "$(grep -c 'if ! captured_aliases="\$(aliases_on_net)"; then' "$STRIPPED" || true)" "1"
check "…and the capture does not re-run the probe, discarding it" \
  "$(grep -c '^mapfile.*aliases_on_net' "$STRIPPED" || true)" "0"
check "…and a failed probe refuses rather than proceeding" \
  "$(sed -n '/^if ! captured_aliases=/,/^fi$/p' "$STRIPPED" | grep -c 'exit 1' || true)" "1"
check "the EXIT trap reconnects through it rather than calling docker itself" \
  "$(grep -c "^trap .*reconnect; fi' EXIT" "$STRIPPED" || true)" "1"
check "…and so does the normal path" \
  "$(grep -c '^reconnect$' "$STRIPPED" || true)" "1"

# 3. THE ORDERING, which is the half no behavioural test can see. FOUR relations, in one chain:
#
#      declare < trap < capture < disconnect
#
#    The trap can fire at any point after it is installed — check_estate exits 1 on four of them —
#    so the array must be DECLARED above it or the trap refers to a name that does not exist yet.
#    And the capture must be below the trap but above the disconnect, because a disconnected
#    container has no alias list left to read: capture after the cut records nothing and restores
#    nothing, with every assertion in the script still green.
#
#    Never assert a position without asserting what it is positioned against — D67 §11, which found
#    exactly this missing and watched the whole preflight block move above the router to satisfy it.
#
#    `tail -1`, NOT `head -1`, and that is the other half of the count above. `head` reports the
#    FIRST match, so a second capture added after the disconnect left this relation reading the
#    still-correct first one and every assertion here green. Taking the last match makes the
#    ordering relation itself see the defect, so the two guards are independent rather than one
#    guard and a restatement: the count catches a second capture wherever it is, and this catches
#    the last capture being in the wrong place even if somebody merges them back into one.
#    And each of the four anchors is asserted to appear EXACTLY ONCE, which is what makes the
#    arithmetic below well defined at all: `tail -1` on a doubled anchor silently compares one
#    occurrence and says nothing about the other, so "present at all" — what this asserted at
#    review — is the weaker half of the property the relations need. It subsumes presence.
ln_of() { grep -n "$1" "$STRIPPED" | tail -1 | cut -d: -f1 || true; }
n_of() { grep -c "$1" "$STRIPPED" || true; }
decl_re='^BOOKING_ALIASES=()$'; trap_re='^trap .* EXIT$'
cap_re='^mapfile -t BOOKING_ALIASES'; disc_re='^docker network disconnect'
decl_ln="$(ln_of "$decl_re")"; trap_ln="$(ln_of "$trap_re")"
cap_ln="$(ln_of "$cap_re")";   disc_ln="$(ln_of "$disc_re")"
for n in decl:"$decl_re" trap:"$trap_re" capture:"$cap_re" disconnect:"$disc_re"; do
  check "the ${n%%:*} line appears exactly once" "$(n_of "${n#*:}")" "1"
done
check "the array is declared before the trap that reads it" \
  "$([[ -n "$decl_ln" && -n "$trap_ln" ]] && (( decl_ln < trap_ln )) && echo yes || echo no)" "yes"
check "…the capture is after the trap, so a partial run reconnects with what it found" \
  "$([[ -n "$trap_ln" && -n "$cap_ln" ]] && (( trap_ln < cap_ln )) && echo yes || echo no)" "yes"
check "…and BEFORE the disconnect, or there is nothing left to read" \
  "$([[ -n "$cap_ln" && -n "$disc_ln" ]] && (( cap_ln < disc_ln )) && echo yes || echo no)" "yes"

# 4. THE RECONNECT IS ASSERTED BY THE RUN ITSELF, not merely performed. A fix that silently does the
#    right thing tells nobody it is still doing it.
check "the run compares the alias set it got back against the one it captured" \
  "$(grep -c 'chk .*aliases it had before.*BOOKING_ALIASES' "$STRIPPED" || true)" "1"

# 4b. AND THE REFUSAL IS RUN, not merely grepped for. §2b asserts the status-checked assignment is
#     in the file; this executes the shipped block with the probe made to fail, which is the only way
#     to know it refuses rather than, say, warning and carrying on. The block is lifted by line range
#     from `if ! captured_aliases=` to its `fi`, so it is the shipped bytes and not a copy — and it
#     is guarded, because an empty extraction would make the subshell exit 0 and read as a pass.
#     Needs no daemon: `aliases_on_net` is stubbed to fail, which is the case under test.
refusal="$(sed -n '/^if ! captured_aliases=/,/^fi$/p' "$STRIPPED")"
check "the refusal block could be extracted at all" \
  "$([[ -n "$refusal" ]] && echo yes || echo no)" "yes"
refuse_out=""; refuse_rc=0
refuse_out="$( ( set -uo pipefail
                 BOOKING_CTR=probe-ctr; NET=probe-net
                 aliases_on_net() { return 1; }
                 eval "$refusal"
                 echo "REACHED-THE-CODE-AFTER" ) 2>&1 )" || refuse_rc=$?
check "a failed probe exits non-zero" \
  "$([[ "$refuse_rc" != 0 ]] && echo nonzero || echo zero)" "nonzero"
case "$refuse_out" in *REACHED-THE-CODE-AFTER*) r=continued ;; *) r=stopped ;; esac
check "…and stops rather than carrying on to the disconnect" "$r" "stopped"
case "$refuse_out" in *"could not ask docker"*) r=says ;; *) r="$refuse_out" ;; esac
check "…and says the question went unanswered" "$r" "says"
case "$refuse_out" in *"Nothing has been disconnected"*) r=says ;; *) r="$refuse_out" ;; esac
check "…and that nothing has been cut" "$r" "says"
# The positive control: a probe that SUCCEEDS must fall through, or this refuses every run. The stub
# prints the two aliases the docker section uses, so the comparison is against a real-looking answer.
pass_out=""; pass_rc=0
pass_out="$( ( set -uo pipefail
               BOOKING_CTR=probe-ctr; NET=probe-net
               aliases_on_net() { printf 'alpha\nbravo\n'; }
               eval "$refusal"
               echo "REACHED-THE-CODE-AFTER" ) 2>&1 )" || pass_rc=$?
check "a probe that answers falls through" "$pass_rc" "0"
case "$pass_out" in *REACHED-THE-CODE-AFTER*) r=continued ;; *) r="$pass_out" ;; esac
check "…and reaches the code after it" "$r" "continued"

# 5. AND NOW ASK DOCKER. Everything above is text; this runs the shipped functions against a real
#    daemon, on a throwaway container and network of its own. It never looks at, and never touches,
#    hc-market-quality — running this cannot disturb a live stack.
echo
if ! docker info >/dev/null 2>&1; then
  echo "  SKIP the shipped functions against a real daemon — none reachable here."
  echo "       Everything above is a text match, so nothing has established that"
  echo "       'docker network connect --alias' restores an alias set at all. Run this where docker is."
  printf '\n%s passed, %s failed\n' "$pass" "$fail"; exit $(( fail > 0 ))
fi

img="alpine:3"
if ! docker image inspect "$img" >/dev/null 2>&1 && ! docker pull -q "$img" >/dev/null 2>&1; then
  echo "  SKIP the shipped functions against a real daemon — cannot obtain $img."
  echo "       Every assertion above is about the TEXT of the script; a section that creates no"
  echo "       container establishes nothing about what docker does with --alias."
  printf '\n%s passed, %s failed\n' "$pass" "$fail"; exit $(( fail > 0 ))
fi

net="hc-market-d68probe-$$"
ctr="hc-market-d68ctr-$$"
peer="hc-market-d68peer-$$"
# On an EXIT trap, not at the end: an assertion aborting here would otherwise leave containers and a
# network behind. It removes only the names it created.
#
# ALL THREE NAMES ARE KNOWN TO IT BEFORE ANYTHING IS CREATED, which is the point rather than tidiness
# — `peer` used to be named on the line before the trap learned about it, so an abort in that one-line
# window leaked a container (D68 §12, the reviewer's fourth optional). Naming a container the run has
# not created yet costs nothing: `docker rm -f` on an absent name is the `|| true` below.
cleanup() { docker rm -f "$ctr" "$peer" >/dev/null 2>&1 || true
            docker network rm "$net" >/dev/null 2>&1 || true
            rm -f "$STRIPPED"; }
trap cleanup EXIT

docker network create "$net" >/dev/null
# A KNOWN multi-alias set, in a known order, neither of which is the container's own name — so
# "the aliases came back" cannot be satisfied by the name docker publishes for free.
docker run -d --name "$ctr" --network "$net" \
  --network-alias alpha --network-alias bravo "$img" sleep 300 >/dev/null

# The functions under test, lifted verbatim. NET and BOOKING_CTR are the globals they read.
harness() { # harness <what-to-run>
  ( set -uo pipefail
    NET="$net"; BOOKING_CTR="$ctr"; BOOKING_ALIASES=()
    eval "$(sed -n '/^aliases_on_net() {/p;/^reconnect() {/,/^}/p' "$SCRIPT")"
    eval "$1" )
}
# Guarded for the same reason §1's stripper is: if the extraction comes back empty, `aliases_on_net`
# is `command not found`, the subshell prints nothing, and "no aliases" is indistinguishable from a
# clean severance. That would report the defect as fixed.
for fn in aliases_on_net reconnect; do
  check "$fn() could be extracted from the script" \
    "$(harness "type -t $fn" 2>/dev/null)" "function"
done

# Space-joined and trimmed, exactly as the shipped script's own assertion joins them, so this file
# compares what that assertion would compare rather than a second spelling of it.
aliases_now() { local s; s="$(harness 'aliases_on_net' | tr '\n' ' ')"; printf '%s' "${s% }"; }
before="$(aliases_now)"
check "the shipped reader sees the alias set docker holds" "$before" "alpha bravo"

# THE PROBE MUST BE ABLE TO SAY "I COULD NOT ASK", or §2b's status check has nothing to check. Two
# ways docker fails, both measured rather than reasoned: no such container, and no daemon at all.
# `pipefail` is what carries docker's status past `sed`'s success — without it both of these are 0
# and the refusal in the script is unreachable.
rc=0; harness 'BOOKING_CTR=hc-market-d68-no-such-container-'"$$"'; aliases_on_net' >/dev/null 2>&1 || rc=$?
check "the reader fails when the container does not exist" \
  "$([[ "$rc" != 0 ]] && echo nonzero || echo zero)" "nonzero"
rc=0; DOCKER_HOST=unix:///nonexistent-hc-d68.sock harness 'aliases_on_net' >/dev/null 2>&1 || rc=$?
check "…and when the daemon is unreachable" \
  "$([[ "$rc" != 0 ]] && echo nonzero || echo zero)" "nonzero"
# The positive control for the two above: a reader that ALWAYS failed would satisfy both and refuse
# every run. It succeeds on the container that does exist, which `before` already read from.
rc=0; harness 'aliases_on_net' >/dev/null 2>&1 || rc=$?
check "…and succeeds on a container that is there" "$rc" "0"

# THE POSITIVE CONTROL, and it comes first deliberately. `getent hosts alpha` answering is only
# evidence that the fix works if it can be made to STOP answering — every reading in this section is
# of a thing expected to be present, and a probe that cannot show the absent case has proved nothing.
docker run -d --name "$peer" --network "$net" "$img" sleep 300 >/dev/null
resolves() { docker exec "$peer" getent hosts "$1" >/dev/null 2>&1 && echo yes || echo no; }
check "'alpha' resolves on the network to start with" "$(resolves alpha)" "yes"

docker network disconnect "$net" "$ctr"
docker network connect "$net" "$ctr"                       # the bare form — the defect, reproduced
check "a BARE reconnect loses the alias set" "$(aliases_now)" ""
check "…and the name genuinely stops resolving, so the instrument can read absence" \
  "$(resolves alpha)" "no"
check "…while the container's own name still answers, which is what masks it" \
  "$(resolves "$ctr")" "yes"

# And now the shipped one, from the same starting point the script is in: severed, with the set
# captured before the cut.
docker network disconnect "$net" "$ctr" >/dev/null 2>&1 || true
harness 'BOOKING_ALIASES=(alpha bravo); reconnect'
check "the shipped reconnect puts the set back, in order" "$(aliases_now)" "$before"
check "…and the name resolves again" "$(resolves alpha)" "yes"

# RE-SUPPLYING THE CONTAINER'S OWN NAME is not a special case to code around: compose's alias sets
# include it (hc-market-quality-catalog carries [hc-market-quality-catalog catalog]), so the shipped
# function hands it straight back to docker. Measured to be accepted rather than reasoned from docs.
docker network disconnect "$net" "$ctr" >/dev/null 2>&1 || true
rc=0; harness "BOOKING_ALIASES=($ctr alpha bravo); reconnect" || rc=$?
check "an alias set containing the container's own name is accepted" "$rc" "0"
check "…and round-trips unchanged" "$(aliases_now)" "$ctr alpha bravo"

# AN EMPTY SET RESTORES NOTHING, deliberately — D68 §4. Today's quality box is in exactly this state,
# and the script restores what it found rather than inventing an alias set from the compose service
# label. What it must NOT do is fail: an empty array must still produce a valid connect.
docker network disconnect "$net" "$ctr" >/dev/null 2>&1 || true
rc=0; harness 'BOOKING_ALIASES=(); reconnect' || rc=$?
check "an empty captured set still reconnects" "$rc" "0"
check "…leaving the container on the network" \
  "$(docker inspect -f "{{if index .NetworkSettings.Networks \"$net\"}}on{{else}}off{{end}}" "$ctr")" "on"
check "…with no aliases invented for it" "$(aliases_now)" ""

cleanup; trap - EXIT
echo "  (removed the $net throwaway network and its two containers)"

printf '\n%s passed, %s failed\n' "$pass" "$fail"
exit $(( fail > 0 ))
