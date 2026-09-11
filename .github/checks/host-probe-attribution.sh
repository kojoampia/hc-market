#!/usr/bin/env bash
# ==============================================================================
#  A REMOTE PROBE MUST SAY WHICH HOP FAILED — decisions.md D75, backlog NEW-33.
#
#  `deploy/deploy-prod.sh`'s host-network preflight was
#
#      ssh -o BatchMode=yes "$HOST" "docker network inspect $net >/dev/null 2>&1" \
#        || die "the '$net' network does not exist on $HOST. $net_hint"
#
#  and `$net_hint` told the reader to create the network or start the stack that owns it. FIVE
#  outcomes reached that one sentence: ssh not reaching the host, ssh being refused, no docker on
#  the host, a daemon that could not be asked, and the network genuinely being absent. Only the last
#  is what the message said, and across a network the first two are the likeliest. It runs three
#  times in a loop.
#
#  EVERY ASSERTION HERE IS ABOUT WHICH CAUSE A REFUSAL NAMES, not about whether it refuses. All of
#  these stop the deploy either way; what the message decides is whether an operator goes and
#  restarts a shared plane four products borrow, or runs ./infra.sh, over a host they cannot reach.
#  That is D66 §7's cost and D71's subject, and this is the sixth instance of the family.
#
#  SEVEN PARTS — trust the numbered list and not this sentence, which is the smallest instance of the
#  thing D71 is about and has been written wrong in this repository four times.
#
#  1. host_run's TWO HOPS, told apart by a sentinel and never by a status. This is the part the
#     taxonomy rests on. MEASURED, OpenSSH 10.2p1: ssh exits 255 when it cannot connect and
#     otherwise exits with the REMOTE command's status — so `exit 255` on the far side is
#     indistinguishable from ssh never arriving, and case 4 below is that exact state. The remote
#     command therefore announces its own status on its own line, and the presence of that line is
#     what establishes a shell ran anything at all.
#
#     Its probes set the remote status with `exit N`, which is deliberate twice over: it is the
#     cheapest way to name a status, and it is the only thing in this file that would notice the
#     far-side subshell being removed — without it, `exit` kills the remote shell before the
#     sentinel is printed and a command that RAN is reported as ssh never arriving.
#
#  2. THE NETWORK ARM, lifted out of preflight and driven through the same stub. Six causes, each
#     asserted by the words it names, plus the positive control and `--dry-run`.
#
#  3. SECRETS.ENV AND ITS TWELVE VALUES, against real fixtures. This part exists because of the one
#     mutation in this whole family whose result is a PASS rather than a wrong message: empty the
#     remote-status arm of that loop and `grep`'s exit 2 — a file the account cannot read — matches
#     nothing, the loop walks all twelve values, and preflight approves a secrets file it never
#     read. Every other defect here is a refusal naming the wrong cause; this one is not a refusal.
#
#  4. THE PREVIOUS TAG A ROLLBACK NEEDS, same treatment. rollback() is where every FAILED deploy
#     lands, so an unreachable host or a wrong --path reported as "no previous deployment recorded"
#     sends somebody hunting for a file that is sitting there intact.
#
#  5. EVERY CALL SITE still goes through host_run. A textual part, deliberately: a site that stops
#     routing through it goes back to folding, and parts 1-4 cannot see a SEVENTH probe growing back
#     beside them. Comments are stripped first with the SHELL stripper — this subject is a shell
#     script and the Java one removes nothing from it while exiting 0.
#
#  6. THE HEALTH GATE, whose 24 polls fold on purpose and whose EXHAUSTION is fatal by way of
#     `rollback` — decisions.md D78, backlog NEW-36. Two assertions pulling opposite ways, which is
#     why this needs driving rather than grepping: a transient that clears on the second poll must
#     still PASS (a status check inside the loop makes a one-second flake a rolled-back deploy), and
#     an exhausted gate must say whether the services never became ready or whether we stopped being
#     able to ask. Only one arm may return into the rollback; the rest are refusals that revert
#     nothing, because a rollback needs the same host the gate just failed to reach. It comes after
#     part 5 because the numbers are names — four parts' messages cite their own.
#
#  7. THE PROGRAM ITSELF, RUN — decisions.md D80, backlog NEW-42. Parts 1-6 lift functions and drive
#     them with call strings this file writes, so they verify a function and never the program: three
#     rounds of findings in this area were each a fail-open in the guard that closed the round before,
#     and every one was a textual assertion ABOUT a call site. This part sources the shipped file and
#     calls `main` and `rollback`, and separately EXECUTES it as a subprocess, against stubbed ssh,
#     scp, docker, curl and git — then asserts on what the stubs were handed. It is what establishes
#     the phase each caller passes and which options each individual ssh receives, neither of which a
#     matcher can reach; two greps that stood in for the first are deleted from part 5 rather than
#     left beside it.
#
#  THE STUB RUNS THE SHIPPED WRAPPING FOR REAL, which is the point of building it this way rather
#  than answering with canned text. A stub that appended the sentinel itself would pass a host_run
#  that had stopped appending one. So `ssh` here executes the wrapped script it was handed, locally,
#  with `docker` stubbed on PATH — the sentinel in every reading below is produced by the code under
#  test. The ssh-hop states are the only ones that answer without running anything, because that is
#  what an ssh that never connected does; their stderr is docker 29.8.0's and OpenSSH 10.2p1's own,
#  read off real throwaway targets on the workstation this was built on (D75 §1).
#
#  WHAT IT DOES NOT ESTABLISH. `deploy-prod.sh` has never been run against a host and nothing here
#  changes that (decisions.md D49). The ssh hop is exercised against a stub in CI and was exercised
#  against a real user-owned sshd on a high port when this was written; what a PRODUCTION host's
#  docker says is not measurable from here, which is why the remote arms key on a status the remote
#  shell reports plus D71's already-measured two-literal absence match, and never on prose nobody
#  here has read.
#
#      ./.github/checks/host-probe-attribution.sh
#      HC_PROD_SCRIPT=/tmp/mutant.sh ./.github/checks/host-probe-attribution.sh
# ==============================================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

SCRIPT="${HC_PROD_SCRIPT:-deploy/deploy-prod.sh}"
# Overridable for one reason: so the test beside this file can point it at nothing and watch the
# missing-stripper branch fire. Absent it, part 5 would read empty text and report every call site
# as routed — the fail-open D62's review found in two checks at once.
STRIP_SH="${HC_STRIP_SH:-$ROOT/.github/checks/strip-sh-comments.awk}"
# The blip arm's premise (part 6) and part 7's staged compose template. Declared here rather than
# inside part 6, where it was until D80: part 7 reads it too, so a control copy of this file with part
# 6 cut out died on an unset variable and reported every case red for the wrong reason — which is what
# a control is FOR, and it would have made the part-6 control unreadable.
PROD_COMPOSE="${HC_PROD_COMPOSE:-deploy/docker/docker-compose.prod.yml}"

P_NET="hc-ci-probe-net"
P_HOST="ci-probe-host"

fail=0
err() { printf '::error::%s\n' "$*"; fail=1; }
ok()  { printf '  ok   %s\n' "$*"; }

[[ -f "$SCRIPT" ]] || { err "$SCRIPT does not exist, so nothing was established about how a remote probe attributes a failure. See decisions.md D75."; printf '\nhost probe attribution: FAILED\n'; exit 1; }
# SELF-CONTAINED ON PARSE. The awk lifts do not cross a syntax break, so a subject that does not
# parse yields functions that look fine in isolation and this check goes green over it. CI's own
# shell-parse step covers the committed file; this covers HC_PROD_SCRIPT pointed anywhere, which is
# how the test beside this file drives it.
bash -n "$SCRIPT" 2>/dev/null \
  || { err "$SCRIPT does not parse, so nothing below is a statement about a script anybody could run — the awk lifts happily carry pieces out of a broken file. See decisions.md D75."; printf '\nhost probe attribution: FAILED\n'; exit 1; }

# The three functions the whole taxonomy lives in, lifted as SHIPPED BYTES. An awk range that
# matches nothing evals nothing and every reading below would then be about an absent function, so
# each lift is checked before anything is run.
lift() { # lift <function name>
  awk -v fn="$1" 'index($0, fn "() {") == 1, /^\}/' "$SCRIPT"
}
LIFT_HOST_RUN="$(lift host_run)"
LIFT_SSH_HINT="$(lift ssh_hint)"
LIFT_NO_DOCKER="$(lift no_docker_on_host)"
for pair in "host_run:$LIFT_HOST_RUN" "ssh_hint:$LIFT_SSH_HINT" "no_docker_on_host:$LIFT_NO_DOCKER"; do
  if [[ -z "${pair#*:}" ]]; then
    err "$SCRIPT declares no function '${pair%%:*}', so this check drove nothing and every probe below would answer the same way for a reason that has nothing to do with attribution. See decisions.md D75."
  fi
done
# The network arm is INLINE in preflight rather than a function of its own, so it is lifted by its
# own two anchors. An empty lift is an error here for the same reason: NEW-33's own line is what
# part 2 is about, and a range that matched nothing would leave part 2 asserting the behaviour of a
# few stubs.
LIFT_NET_ARM="$(awk '/^  log "checking host networks"$/,/^  done$/' "$SCRIPT")"
[[ -n "$LIFT_NET_ARM" ]] \
  || err "$SCRIPT has no host-network loop this check can read between 'log \"checking host networks\"' and its 'done', so part 2 established nothing about the message an operator gets when a network cannot be confirmed. See decisions.md D75 and backlog NEW-33."
# PARTS 3 AND 4's SUBJECTS, lifted the same way and for the reason the review found: part 2 drives
# ONE of the six call sites, and part 5's "routed through host_run" assertion establishes only that a
# probe cannot report an *ssh* failure as a fact about the host. It says nothing about the
# REMOTE-STATUS arms, and emptying one of those is a smaller edit than the defect this check was
# written for — and a worse one. Measured, on the secrets loop: with its `*)` arm gutted, `grep`'s
# exit 2 (the unreadable-0600 state the code's own comment names as live) matches an empty branch,
# the loop proceeds past all twelve values, and preflight PASSES on a secrets file it could not read.
# The original at least died with the wrong message.
LIFT_SECRETS="$(awk 'index($0, "  log \"checking $REMOTE_PATH/$SECRETS_FILE on $HOST\"") == 1, $0 == "  fi"' "$SCRIPT")"
[[ -n "$LIFT_SECRETS" ]] \
  || err "$SCRIPT has no secrets.env block this check can read between 'log \"checking \$REMOTE_PATH/\$SECRETS_FILE on \$HOST\"' and its 'fi', so part 3 established nothing about what an operator is told when the twelve values cannot be read. See decisions.md D75."
LIFT_ROLLBACK="$(awk '$0 == "  local prev", index($0, "  [[ -n \"$prev\" ]]") == 1' "$SCRIPT")"
[[ -n "$LIFT_ROLLBACK" ]] \
  || err "$SCRIPT has no previous-tag read this check can read between 'local prev' and the '[[ -n \"\$prev\" ]]' refusal, so part 4 established nothing about the function every FAILED deploy lands in. See decisions.md D75."
# EACH RANGE MUST HAVE ENDED WHERE IT WAS TOLD TO, and this is a fail-open found by mutating the END
# anchor rather than the start one: an `awk` range whose terminator never matches prints to the END
# OF THE FILE, so the "is the lift empty" guard above passes on a lift that carries the rest of the
# script — the router included. Loud rather than dangerous (`set -u` on an unset DO_ROLLBACK), and
# still a part asserting the behaviour of something other than its subject. The terminator is
# checked, not the length.
lift_ends_with() { # lift_ends_with <lifted text> <marker the last line must carry> <part> <what>
  local last; last="$(printf '%s\n' "$1" | tail -1)"
  [[ "$last" == *"$2"* ]] && return 0
  err "$SCRIPT's $4 does not end at a line carrying '$2' — the range ran on to '$last'. An awk range whose terminator stops matching prints to the end of the file, so $3 would be driving the rest of the script rather than its own subject. See decisions.md D75."
}
lift_ends_with "$LIFT_SECRETS" "fi" "part 3" "secrets.env block"
lift_ends_with "$LIFT_ROLLBACK" '[[ -n "$prev" ]]' "part 4" "previous-tag read"
# The real key list and the real hint, so part 3 exercises what a deploy would: twelve values, and
# the message each one carries. `secret_hint` is called by the not-set arm.
LIFT_KEYS="$(grep -E '^SECRET_KEYS=\(' "$SCRIPT" || true)"$'\n'"$(awk 'index($0, "CONNECTION_KEYS=(") == 1, $0 == ")"' "$SCRIPT")"
LIFT_HINT="$(lift secret_hint)"
case "$LIFT_KEYS" in
  *"SECRET_KEYS=("*"CONNECTION_KEYS=("*) : ;;
  *) err "$SCRIPT no longer declares SECRET_KEYS and CONNECTION_KEYS as top-level arrays, so part 3 could not drive the twelve-value loop at all. See decisions.md D75." ;;
esac
[[ -n "$LIFT_HINT" ]] \
  || err "$SCRIPT declares no secret_hint, so part 3's not-set arm would report a message this check cannot distinguish from the others. See decisions.md D75."
# host_run reads two values declared at the top level beside it, and they are lifted as shipped
# bytes rather than restated here. A restated sentinel is a second definition of the protocol and
# would keep every reading below green while the script's own copy was emptied — which is the shape
# of the fail-open this repository keeps finding. `set -u` is on inside the probe, so an unlifted
# sentinel is loud; the guard is here so it is loud with a reason.
# PART 6's SUBJECTS — decisions.md D78, backlog NEW-36. The gate itself, because the assertion that
# its polls still FOLD is as load-bearing as the one about its exhaustion: a status check inside the
# loop makes a one-second flake fatal, and only driving the loop can see that. `compose_name` and
# `REMOTE_COMPOSE` are one-liners, so they are lifted by grep rather than by `lift` — an awk range
# opened on a line that also closes it runs on to the next function's closing brace.
LIFT_HEALTH_GATE="$(lift health_gate)"
LIFT_GATE_EXHAUSTED="$(lift gate_exhausted)"
LIFT_COMPOSE_NAME="$(grep -E '^compose_name\(\) \{' "$SCRIPT" || true)"
LIFT_REMOTE_COMPOSE="$(grep -E '^REMOTE_COMPOSE=' "$SCRIPT" || true)"
for pair in "health_gate:$LIFT_HEALTH_GATE" "gate_exhausted:$LIFT_GATE_EXHAUSTED" \
            "compose_name:$LIFT_COMPOSE_NAME" "REMOTE_COMPOSE:$LIFT_REMOTE_COMPOSE"; do
  if [[ -z "${pair#*:}" ]]; then
    err "$SCRIPT declares no '${pair%%:*}', so part 6 drove nothing: the health gate's exhaustion is fatal by way of \`rollback\`, and what stands beside it is the only thing telling an operator whether the services never became ready or whether we stopped being able to ask. See decisions.md D78 and backlog NEW-36."
  fi
done
# LIFTED FROM STRIPPED TEXT, WHICH THE FIRST VERSION OF THE SSH_OPTS GUARD BELOW WAS NOT — decisions.md
# D78 §14, and the house question asked of the guard that had just repaired a fail-open: *would a
# comment satisfy this?* Measured, it did —
#
#     SSH_OPTS=(-o BatchMode=yes) # keep -o "ConnectTimeout=..." off while debugging slow links
#
# exits 0 and prints "carries both BatchMode and a ConnectTimeout" for an array carrying NEITHER,
# because the substring lands in the trailing comment. So the two lines are lifted from the shell
# stripper's output; `$STRIPPED_SRC` is computed once here and part 5 reuses it.
#
# THE ASYMMETRY WITH HOST_SENTINEL IS THE THING TO KNOW BEFORE UNDOING THIS, and it is measured
# rather than assumed: `HOST_SENTINEL="" # was "__hc_remote_status__"` is refused even by a raw grep,
# because a blank sentinel breaks host_run's actual BEHAVIOUR and parts 1 and 2 catch it by driving.
# SSH_OPTS' absence changes nothing the stub can observe — an ssh handed no options answers exactly
# as before — so this text matcher is the sole line of defence and has to be exact. Do not "simplify"
# it back to a raw grep on the grounds that the guard beside it uses one.
if [[ ! -f "$STRIP_SH" ]]; then
  err "$STRIP_SH is missing, so nothing below could strip comments: part 5 would read empty text and report every call site as routed, and the SSH_OPTS guard would be satisfied by a trailing comment. One file, two parts of this check, and every text-matching check in this repository. A check that reads nothing is not a check that found nothing."
  printf '\nhost probe attribution: FAILED\n'; exit 1
fi
STRIPPED_SRC="$(awk -f "$STRIP_SH" "$SCRIPT")"
LIFT_GLOBALS="$(printf '%s\n' "$STRIPPED_SRC" | grep -E '^(HOST_SENTINEL|SSH_OPTS)=' || true)"
# THE VALUE, NOT THE NAME. `HOST_SENTINEL=""` satisfies a grep for the assignment and satisfies
# `grep -F "$HOST_SENTINEL "` against any line with a space in it — so the function still refuses,
# and refuses naming NEITHER hop. Measured: every ssh state then answers "answered with a status
# this script cannot read". Fails closed and diagnoses nothing, which is precisely the shape this
# check exists to catch, so the guard reads the value.
SENTINEL_VALUE="$(printf '%s\n' "$LIFT_GLOBALS" | sed -n 's|^HOST_SENTINEL=||p' | tr -d "\"'" | head -1)"
[[ -n "$SENTINEL_VALUE" ]] \
  || err "$SCRIPT declares no non-empty HOST_SENTINEL at the top level. That value IS the mechanism that tells ssh's own failure apart from the remote command's — ssh exits 255 for both — so without it there is nothing here to check, and host_run refuses every probe while naming neither hop. See decisions.md D75."
# AND THE SAME TREATMENT FOR SSH_OPTS, WHICH WAS LIFTED AND ASSERTED BY NOTHING — decisions.md D78 §9,
# found at review. Three states were driven against the shipped check and all three exited **0**:
# `SSH_OPTS=(-o BatchMode=yes)` with the timeout gone, `SSH_OPTS=()` empty, and the array RENAMED so
# the grep above lifts only the sentinel line. The stub does not care how many options it is handed,
# so part 6 stays green in every one of them — and the timeout is the half of NEW-36 taken beyond the
# item: measured, an ssh with no ConnectTimeout spends 136s on a blackholed address against 8s with
# one, which is the difference between a ~20-minute gate and a ~4.5-hour one.
#
# THE VALUE, NOT THE DECLARATION, exactly as the sentinel guard above. `ConnectTimeout` is the whole
# reason the refusal arrives at all, and `BatchMode` is asserted here because the inline ban in part 5
# assumes this array supplies it: without both, "SSH_OPTS is the one place BatchMode and the timeout
# are set" — part 5's own success line — is a sentence about a variable that sets neither.
SSH_OPTS_VALUE="$(printf '%s\n' "$LIFT_GLOBALS" | sed -n 's|^SSH_OPTS=||p' | head -1)"
if [[ -z "$SSH_OPTS_VALUE" ]]; then
  err "$SCRIPT declares no SSH_OPTS at the top level. Every ssh in the file expands it, so an absent or renamed array means every remote probe AND the health gate's 24 polls connect on the TCP default — measured at 136s per attempt against a blackholed address, which is a refusal nobody is still waiting for. See decisions.md D78 §7 and backlog NEW-36."
else
  # THE WHOLE EXPRESSION, NOT THE OPTION'S NAME — third review's finding 2, measured:
  # `ConnectTimeout=${HC_SSH_TIMEOUT:-0}` passed the name check and printed "bounded" for an array
  # that hands ssh `ConnectTimeout=0` whenever the environment is unset, which is UNBOUNDED and is
  # the one value this script's own comment calls "well-formed, accepted, and exactly the defect
  # NEW-36's second half removed". The script's declaration-time guard cannot see it either: that
  # guard validates `${HC_SSH_TIMEOUT:-8}` — its OWN default — so the two defaults could diverge with
  # both green. Pinning the exact byte string is what makes them one fact; it is also the string
  # case 34's `sed` already treats as canonical, so the two cannot drift apart either.
  case "$SSH_OPTS_VALUE" in
    *'ConnectTimeout=${HC_SSH_TIMEOUT:-8}'*) : ;;
    *ConnectTimeout*) err "$SCRIPT's SSH_OPTS names a ConnectTimeout whose value is not \`\${HC_SSH_TIMEOUT:-8}\`: '$SSH_OPTS_VALUE'. The option's NAME being present is not the property that matters — `ConnectTimeout=0` and `ConnectTimeout=\${HC_SSH_TIMEOUT:-0}` both read as bounded and are not (0 means wait for the TCP default, measured at 136s against a blackholed address). The default here must also be the one the script's own HC_SSH_TIMEOUT guard validates, or the two diverge silently. See decisions.md D78 §7 and §15." ;;
    *) err "$SCRIPT's SSH_OPTS carries no ConnectTimeout: '$SSH_OPTS_VALUE'. An unbounded connect is a refusal that arrives minutes late or never — 136s per attempt on a blackholed address, measured, against 8s with the timeout — and the health gate makes 24 × one-per-service of them before it can say anything at all. See decisions.md D78 §7." ;;
  esac
  # ...AND EXACTLY ONE ASSIGNMENT, anchored and unanchored. `sed … | head -1` reads the FIRST
  # declaration and bash executes the LAST, so a second top-level `SSH_OPTS=(-o BatchMode=yes)`
  # appended below the original passed every assertion above (measured), and an INDENTED reassignment
  # inside an arg-parsing branch is invisible to the anchored grep altogether. Both counts are taken
  # from stripped text, so a mention inside a comment is not one of them.
  ssh_opts_anchored="$(printf '%s\n' "$STRIPPED_SRC" | { grep -cE '^SSH_OPTS=' || true; })"
  ssh_opts_any="$(printf '%s\n' "$STRIPPED_SRC" | { grep -cF 'SSH_OPTS=' || true; })"
  (( ssh_opts_anchored == 1 && ssh_opts_any == 1 )) \
    || err "$SCRIPT assigns SSH_OPTS $ssh_opts_any time(s) ($ssh_opts_anchored at the top level), and this check reads the first while bash obeys the last. Exactly one assignment, at the top level: a second one — appended below, or indented inside a branch — silently replaces the options every ssh in the file expands, with every assertion here still green. See decisions.md D78 §15."
  case "$SSH_OPTS_VALUE" in
    *BatchMode*) : ;;
    *) err "$SCRIPT's SSH_OPTS carries no BatchMode: '$SSH_OPTS_VALUE'. Part 5 bans an inline '-o BatchMode=yes' on the grounds that this array is where it is set, so dropping it here makes that ban a guard over nothing — and a deploy that stops halfway waiting for a passphrase is worse than one that does not start. See decisions.md D75 and D78 §9." ;;
  esac
  # THE COUNT IS DERIVED AND PRINTED, because the decision that shipped this listed thirteen LINE
  # NUMBERS and the next commit in the same branch moved every one of them (D78 §7, §14). A number a
  # tool prints on every run cannot rot; one written into a document can.
  #
  # WHAT THE FLOOR BELOW IS WORTH, STATED AT THE FLOOR because two documents quoted the line beside it
  # as though the thirteen were COVERED rather than counted (third review's first note). It is nearly
  # vacuous and is kept for the one thing it does catch: removing the expansion from any ELEVEN of the
  # twelve — the health gate's own poll included, which is NEW-36's second half — leaves the count at
  # two with nothing red, and exactly two is a real collapse to host_run's probe plus one that this
  # passes. It counts LINES carrying the expansion, not invocations, so a future
  # `local opts=("${SSH_OPTS[@]}")` inflates it. Per-site coverage is not available to a text matcher
  # at all, and since D80 it is not this line's job either: PART 7 asks each invocation what it was
  # handed. The floor is kept for what it still does cheaply — a total collapse, refused before a
  # single scenario is run.
  ssh_bounded="$(printf '%s\n' "$STRIPPED_SRC" | { grep -cF 'SSH_OPTS[@]}' || true; })"
  (( ssh_bounded >= 2 )) \
    || err "$SCRIPT expands SSH_OPTS on $ssh_bounded line(s) — a total collapse: host_run's own probe is one of them, so fewer than two means the whole deploy phase (the upload, the pull, the roll, the health gate's poll, the smoke probes, the rollback) has gone back to the TCP default. This floor does NOT establish that any particular one still expands it; part 7 is what does. See decisions.md D78 §7 and D80."
  [[ "$SSH_OPTS_VALUE" == *ConnectTimeout* && "$SSH_OPTS_VALUE" == *BatchMode* ]] \
    && ok "SSH_OPTS carries both BatchMode and a ConnectTimeout, and $ssh_bounded lines expand it (a count, not per-site coverage — part 7 has that)"
fi
if (( fail )); then printf '\nhost probe attribution: FAILED\n'; exit 1; fi

# ---- the instrument ------------------------------------------------------------------------------
#
# A directory on PATH holding a `docker` that answers according to $FAKE_DOCKER, and an `ssh` shell
# function that either runs what it was handed or answers the way a real ssh answers when it never
# connected. Every message below is one measured on this workstation — docker 29.8.0 through a real
# ssh, OpenSSH 10.2p1 against real throwaway targets.
BIN="$(mktemp -d)"
trap 'rm -rf "$BIN"' EXIT
cat > "$BIN/docker" <<'DOCKER_STUB'
#!/usr/bin/env bash
# THE HEALTH GATE'S TWO QUESTIONS — decisions.md D78, backlog NEW-36. `exec` is what the 24 polls
# ask, `ps -a` is what the exhaustion asks instead, and `logs` is the evidence the rollback is about
# to destroy. Dispatched on the compose subcommand first, so every state below the block is exactly
# what parts 1-5 already measured for `network inspect` and is unchanged.
#
# Every status and every sentence here was measured on this workstation against throwaway containers,
# docker 29.8.0: `exec` exits 1 for a port that refuses, 1 for a service that is not running, 1 for a
# service the file does not declare and 1 for a daemon that cannot be asked — four states, one
# status, which is why no status check inside the loop could have attributed anything. `ps -a` exits
# 0 with a line per container, 1 carrying docker's own sentence when the daemon cannot be asked, and
# **0 with nothing at all** when the project has no containers.
sub=""
for a in "$@"; do case "$a" in exec|ps|logs) sub="$a"; break ;; esac; done
# `ps` ANSWERS IN THE ORDER THE FORMAT ASKED FOR, which is not decoration: a stub printing a canned
# `name state health` line stays green while the script's `--format` is REORDERED to
# '{{.Health}} {{.Service}} {{.State}}', and the shipped regex then matches nothing on a real host —
# the blip arm silently dead with every assertion passing. Review's note, closed here rather than
# conceded. render_ps <state1> <health1> <state2> <health2>.
render_ps() {
  local fmt="" tok out
  for a in "$@"; do :; done
  for a in "${ARGV[@]}"; do case "$a" in *'{{.'*) fmt="$a" ;; esac; done
  local -a svc=(hc-market-catalog hc-market-booking) st=("$1" "$3") he=("$2" "$4")
  local i
  for i in 0 1; do
    out=""
    # Walk the format string's tokens in order. An unknown token is printed as itself, so a format
    # this stub does not understand is visible rather than silently dropped.
    for tok in $fmt; do
      case "$tok" in
        '{{.Service}}') out+="${svc[$i]} " ;;
        '{{.State}}')   out+="${st[$i]} " ;;
        '{{.Health}}')  out+="${he[$i]} " ;;
        *)              out+="$tok " ;;
      esac
    done
    printf '%s\n' "${out% }"
  done
}
ARGV=("$@")
if [[ -n "$sub" ]]; then
  case "${FAKE_DOCKER:-ok}:$sub" in
    # Ready on the first poll: the positive control for every refusal below it.
    gate-ready:exec) exit 0 ;;
    gate-ready:ps)   render_ps running healthy running healthy; exit 0 ;;
    # A TRANSIENT. The first poll of each service fails and every later one succeeds, which is the
    # state the folding inside the loop exists for: a status check in there makes this fatal.
    gate-flake:exec)
      n=$(cat "${FAKE_COUNTER:-/dev/null}" 2>/dev/null || printf 0); n=$((n + 1))
      printf '%s' "$n" > "${FAKE_COUNTER:-/dev/null}"
      (( n <= 2 )) && { printf 'bash: line 1: /dev/tcp/localhost/8080: Connection refused\n' >&2; exit 1; }
      exit 0 ;;
    gate-flake:ps)   render_ps running healthy running healthy; exit 0 ;;
    # GENUINELY NOT READY, and the host says so itself. This is the ONE arm that may roll back.
    gate-unready:exec) printf 'bash: line 1: /dev/tcp/localhost/8080: Connection refused\n' >&2; exit 1 ;;
    gate-unready:ps)   render_ps running unhealthy running starting; exit 0 ;;
    gate-unready:logs) printf 'hc-market-catalog  | Caused by: org.postgresql.util.PSQLException: Connection refused\n'; exit 0 ;;
    # THE BLIP: the polls got nothing and docker, running the same readiness probe INSIDE the host,
    # says both services are healthy. Constructed at the docker layer rather than the ssh one because
    # this harness's ssh state is fixed for a run; what is under test is the arm, not the blip's cause.
    gate-blip:exec) printf 'bash: line 1: /dev/tcp/localhost/8080: Connection refused\n' >&2; exit 1 ;;
    gate-blip:ps)   render_ps running healthy running healthy; exit 0 ;;
    # The host answered and knows of no containers at all. Measured: status 0, no output.
    gate-empty:exec) printf 'service "hc-market-catalog" is not running\n' >&2; exit 1 ;;
    gate-empty:ps)   exit 0 ;;
    # The daemon cannot be asked, whichever of the three is asked of it.
    daemon-down:*)
      printf 'failed to connect to the docker API at unix:///nonexistent; check if the path is correct and if the daemon is running: dial unix /nonexistent: connect: no such file or directory\n' >&2
      exit 1 ;;
    cli-not-found:*)
      printf 'bash: line 1: docker: command not found\n' >&2
      exit 127 ;;
    # The daemon answered `ps` and then went quiet for `logs`: the one state whose remedy is a warn
    # rather than a refusal, because unreadiness is established by then.
    gate-logs-lost:exec) printf 'bash: line 1: /dev/tcp/localhost/8080: Connection refused\n' >&2; exit 1 ;;
    gate-logs-lost:ps)   render_ps running unhealthy running starting; exit 0 ;;
    gate-logs-lost:logs)
      printf 'failed to connect to the docker API at unix:///nonexistent; check if the path is correct and if the daemon is running: dial unix /nonexistent: connect: no such file or directory\n' >&2
      exit 1 ;;
    *) exit 0 ;;
  esac
fi
case "${FAKE_DOCKER:-ok}" in
  net-absent)
    printf '[]\n'
    printf 'Error response from daemon: network %s not found\n' "$3" >&2
    exit 1 ;;
  daemon-down)
    printf '[]\n'
    printf 'failed to connect to the docker API at unix:///nonexistent; check if the path is correct and if the daemon is running: dial unix /nonexistent: connect: no such file or directory\n' >&2
    exit 1 ;;
  # No docker CLI on the host at all: 127, the shell's own status for a command it cannot find.
  # Measured through a real ssh with PATH emptied.
  cli-not-found)
    printf 'bash: line 1: docker: command not found\n' >&2
    exit 127 ;;
  # A docker that RAN and could not answer, whose own words carry `not found` and NOT "Error
  # response from daemon" — `DOCKER_HOST=ssh://…` to a host with no docker on it, which docker
  # natively supports. D71's tightening, and the positive control for the absence arm: the loose
  # one-literal match routes this to "the network does not exist" and sends an operator to create a
  # network on a machine whose daemon was never reached. Note the status is 1, not 127 — the CLI
  # that could not be found is on the FAR side of docker's own hop, so the 127 branch above cannot
  # see this and the match is the only thing standing in front of it.
  daemon-not-found)
    printf 'error during connect: Get "http://docker.example/v1.51/networks/%s": command line: /usr/bin/ssh: bash: line 1: docker: command not found\n' "$3" >&2
    exit 1 ;;
  compose-missing)
    printf "docker: 'compose' is not a docker command.\n" >&2
    exit 1 ;;
  *) exit 0 ;;
esac
DOCKER_STUB
chmod +x "$BIN/docker"

# `probe <FAKE_SSH> <FAKE_DOCKER> <what to run>` — the run is either a host_run call or the lifted
# network arm. Output is everything the subject printed, with a DIE line if it refused.
probe() {
  local fake_ssh="$1" fake_docker="$2" body="$3"
  (
    set +e
    export FAKE_DOCKER="$fake_docker"
    export PATH="$BIN:$PATH"
    # REMOTE_PATH and SECRETS_FILE are overridable so parts 3 and 4 can point the SHIPPED remote
    # commands at real fixtures — a real file, a file missing a key, a directory, a path that is not
    # there — and let `test`, `grep`, `cd` and `cut` produce their own statuses rather than have a
    # shim assert what those statuses are.
    HOST="$P_HOST"; DRY_RUN=0
    REMOTE_PATH="${HC_PROBE_PATH:-/srv/healthconnect}"
    SECRETS_FILE="${HC_PROBE_SECRETS:-secrets.env}"
    DATA_COMPOSE_FILE="data-compose.yml"; DATA_STORE_COUNT=5
    # Part 4's first-deploy refusal names the tag that is still running, so `set -u` needs one.
    TAG="9.9.9-probe"
    HC_NETWORK="$P_NET"; HC_DATA_NETWORK="${HC_DATA_NET_OVERRIDE:-$P_NET-data}"
    HC_MONITORING_NETWORK="$P_NET-mon"
    # PART 6's SUBJECT (decisions.md D78). Two services rather than five, so the exhaustion's own
    # table is readable in a failure message; the gate's budget is short and its `sleep` is a no-op,
    # because 24 iterations at ten seconds is not a thing CI can wait for and the count is not what
    # is under test. HEALTH_TIMEOUT of 10 exhausts on the first iteration; 20 gives the transient
    # case a second one, which is the poll's own folding being asserted rather than assumed.
    SERVICES=(catalog booking)
    HEALTH_TIMEOUT="${HC_PROBE_HEALTH_TIMEOUT:-10}"
    APP_COMPOSE_FILE="docker-compose.yml"
    export FAKE_COUNTER="$BIN/flake.count"; printf '0' > "$FAKE_COUNTER"
    sleep() { :; }
    ok() { printf 'OK %s\n' "$*"; }
    log() { :; }
    # WARN PRINTS HERE, unlike the other five shims, because part 6's one non-fatal arm — the host
    # answered and agrees something is unready — says everything it has to say through `warn` and
    # then returns. A silent warn would make that arm indistinguishable from a gate that diagnosed
    # nothing. Prefixed, so no assertion above can match it by accident.
    warn() { printf 'WARN %s\n' "$*"; }
    step() { printf 'STEP %s\n' "$*"; }
    skipped() { printf 'SKIPPED %s\n' "$*"; }
    die() { printf 'DIE %s\n' "$*"; exit 3; }
    c_dim=""; c_reset=""
    # THE STUB RUNS WHAT IT WAS HANDED. `${@: -1}` is the wrapped remote script host_run built, so
    # the sentinel every assertion below reads is produced by the shipped code and not by this
    # function. An ssh that never connected runs nothing and answers 255 with its own words, which
    # is exactly what was measured against an unresolvable name, a closed port, a bad host key and
    # a key BatchMode would not use.
    ssh() {
      local remote="${@: -1}"
      case "$fake_ssh" in
        dns)         printf 'ssh: Could not resolve hostname %s: Name or service not known\n' "$HOST" >&2; return 255 ;;
        unreachable) printf 'ssh: connect to host %s port 22: Connection refused\n' "$HOST" >&2; return 255 ;;
        timeout)     printf 'ssh: connect to host %s port 22: Connection timed out\n' "$HOST" >&2; return 255 ;;
        hostkey)     printf 'Host key for %s has changed and you have requested strict checking.\nHost key verification failed.\n' "$HOST" >&2; return 255 ;;
        keyrefused)  printf 'ci@%s: Permission denied (publickey,password).\n' "$HOST" >&2; return 255 ;;
        # Connected, and the remote command ran. The positive control for all five above: without
        # it a host_run that refused everything would satisfy every refusal assertion here.
        *)           bash -c "$remote"; return $? ;;
      esac
    }
    eval "$LIFT_GLOBALS"
    eval "$LIFT_KEYS"
    eval "$LIFT_HINT"
    eval "$LIFT_SSH_HINT"
    eval "$LIFT_NO_DOCKER"
    eval "$LIFT_HOST_RUN"
    # SHIPPED BYTES, not restatements. compose_name decides the prefix the exhaustion's `ps` answer
    # is matched against, and REMOTE_COMPOSE decides which files compose interpolates — a copy of
    # either here would keep part 6 green while the script's own copy disagreed with the host.
    eval "$LIFT_COMPOSE_NAME"
    eval "$LIFT_REMOTE_COMPOSE"
    eval "$LIFT_GATE_EXHAUSTED"
    eval "$LIFT_HEALTH_GATE"
    eval "$body"
  ) 2>&1 || true   # a refusal is one of the ANSWERS here, not a failure of this check
}
one() { printf '%s' "${1//$'\n'/ }"; }

# ---- 1. The two hops, and the sentinel that tells them apart -------------------------------------
printf '\n%s: host_run — which hop answered\n' "$SCRIPT"
REPORT='host_run "ask a question" "exit ${REMOTE_EXIT:-0}"; printf "STATUS=%s OUT=[%s]\n" "$HOST_STATUS" "${HOST_OUTPUT//$'"'"'\n'"'"'/ }"'

r_ok="$(probe connected ok "$REPORT")"
r_255="$(probe connected ok "REMOTE_EXIT=255; $REPORT")"
r_127="$(probe connected ok "REMOTE_EXIT=127; $REPORT")"
r_dns="$(probe dns ok "$REPORT")"
r_key="$(probe keyrefused ok "$REPORT")"
r_hostkey="$(probe hostkey ok "$REPORT")"
r_out="$(probe connected ok 'host_run "ask a question" "printf stdout-line; printf stderr-line >&2; exit 4"; printf "STATUS=%s OUT=[%s]\n" "$HOST_STATUS" "${HOST_OUTPUT//$'"'"'\n'"'"'/ }"')"
printf '  remote 0:            %s\n  remote 255:          %s\n  remote 127:          %s\n' \
  "$(one "$r_ok")" "$(one "$r_255")" "$(one "$r_127")"
printf '  ssh cannot resolve:  %s\n  ssh key refused:     %s\n  ssh host key:        %s\n  both streams:        %s\n' \
  "$(one "$r_dns")" "$(one "$r_key")" "$(one "$r_hostkey")" "$(one "$r_out")"

case "$r_ok" in
  *"STATUS=0"*) ok "host_run reports the remote status when ssh connected and the command succeeded" ;;
  *) err "$SCRIPT's host_run did not come back cleanly from a remote command that SUCCEEDED: '$(one "$r_ok")'. A probe that refuses the correct state is not a probe, and every refusal asserted below would then be firing for the wrong reason. See decisions.md D75." ;;
esac
# THE ASSERTION THE WHOLE DESIGN EXISTS FOR. ssh exits 255 when it cannot connect and otherwise
# exits with the remote command's status, so this state and the four ssh states above it are
# identical to anything reading the exit code. If host_run ever goes back to reading the status,
# this is the one case that moves — and it moves in the direction of blaming the operator's network
# for a remote command that ran and failed.
case "$r_255" in
  *"STATUS=255"*) ok "host_run does not read a remote command's own 255 as an ssh that never arrived" ;;
  DIE*) err "$SCRIPT's host_run reported an ssh failure for a remote command that RAN and exited 255: '$(one "$r_255")'. ssh's 255 and the remote's 255 are the same number — that is why the remote announces its own status on its own line, and why the presence of that line, never an exit code, is what establishes which hop answered. See decisions.md D75." ;;
  *) err "$SCRIPT's host_run answered '$(one "$r_255")' for a remote command that exited 255; it must report HOST_STATUS=255. See decisions.md D75." ;;
esac
case "$r_127" in
  *"STATUS=127"*) ok "host_run passes 127 through, so 'no docker on the host' stays distinguishable" ;;
  *) err "$SCRIPT's host_run answered '$(one "$r_127")' for a remote command that exited 127 — the shell's status for a command it cannot find, which is how a host with no docker CLI on it is told apart from one whose daemon is down. See decisions.md D75." ;;
esac
for state in "ssh cannot resolve a name:$r_dns:Could not resolve" "ssh refused the key:$r_key:ssh-add" "ssh refused the host key:$r_hostkey:known_hosts"; do
  what="${state%%:*}"; rest="${state#*:}"; got="${rest%:*}"; want="${rest##*:}"
  case "$got" in
    DIE*"no answer from a shell"*)
      case "$got" in
        *"$want"*) ok "host_run names ssh, and its remedy, when $what" ;;
        *) err "$SCRIPT's host_run refused for the right cause but the wrong remedy when $what: '$(one "$got")' does not mention '$want'. ssh's own words are the only evidence available for that hop, and they are produced by the LOCAL client, so keying on them is a measurement rather than a guess. See decisions.md D75." ;;
      esac ;;
    DIE*)
      err "$SCRIPT's host_run refused when $what, but did not say that no answer came back from a shell: '$(one "$got")'. Nothing on the host was asked, so a message about the host's networks, files or containers is a claim about something never looked at — which is backlog NEW-33 exactly. See decisions.md D75." ;;
    *)
      err "$SCRIPT's host_run ACCEPTED an ssh that never connected ($what): '$(one "$got")'. Every branch below it then reads an empty answer as a fact about the host. See decisions.md D75." ;;
  esac
done
case "$r_out" in
  *"STATUS=4"*"stdout-line"*"stderr-line"*) ok "host_run captures both streams, which is what the absence match reads" ;;
  *) err "$SCRIPT's host_run answered '$(one "$r_out")'; it must report status 4 and carry BOTH streams. docker prints '[]' on stdout and its sentence on stderr for an absent network AND for an unanswerable daemon (measured, 29.8.0), so a probe capturing one of them cannot tell those two apart at all. See decisions.md D71 and D75." ;;
esac

# ---- 2. The network arm: five causes, five messages ----------------------------------------------
printf '\n%s: the host-network preflight\n' "$SCRIPT"
n_ok="$(probe connected ok "$LIFT_NET_ARM")"
n_absent="$(probe connected net-absent "$LIFT_NET_ARM")"
n_absent_data="$(HC_DATA_NET_OVERRIDE="$P_NET" probe connected net-absent "$LIFT_NET_ARM")"
n_daemon="$(probe connected daemon-down "$LIFT_NET_ARM")"
n_nodocker="$(probe connected cli-not-found "$LIFT_NET_ARM")"
n_relayed="$(probe connected daemon-not-found "$LIFT_NET_ARM")"
n_ssh="$(probe unreachable ok "$LIFT_NET_ARM")"
n_dry="$(probe connected ok "DRY_RUN=1; $LIFT_NET_ARM")"
printf '  present:             %s\n  absent (host-wide):  %s\n  absent (data):       %s\n' \
  "$(one "$n_ok")" "$(one "$n_absent")" "$(one "$n_absent_data")"
printf '  daemon unanswerable: %s\n  no docker on host:   %s\n' "$(one "$n_daemon")" "$(one "$n_nodocker")"
printf '  docker relays 404:   %s\n  ssh unreachable:     %s\n  dry run:             %s\n' \
  "$(one "$n_relayed")" "$(one "$n_ssh")" "$(one "$n_dry")"

case "$n_ok" in
  DIE*) err "$SCRIPT's host-network preflight refuses three networks that ARE there: '$(one "$n_ok")'. A refusal that fires on the correct state is not a check. See decisions.md D75." ;;
  *"OK network"*) ok "the host-network preflight passes when every network is present" ;;
  *) err "$SCRIPT's host-network preflight neither passed nor refused for three networks that are present: '$(one "$n_ok")'. See decisions.md D75." ;;
esac
# The absence arm is the ONE outcome the old message was right about, and the one place $net_hint
# belongs. Both hints are asked for, because the branch that chooses between them is a comparison
# against HC_DATA_NETWORK and a fix that lost it would still print a plausible remedy.
case "$n_absent" in
  *"DIE the '$P_NET' network does not exist"*)
    case "$n_absent" in
      *"start the owning stack"*) ok "a genuinely absent host-wide network is still reported as absent, with its own remedy" ;;
      *) err "$SCRIPT reports the absent host-wide network correctly and no longer says how to fix it: '$(one "$n_absent")'. infranet and monitoring belong to other stacks on that host; this one does not create them. See decisions.md D75." ;;
    esac ;;
  DIE*) err "$SCRIPT refused an absent network without saying it is absent: '$(one "$n_absent")'. 'could not be asked' about a network docker ANSWERED about is the D71/D75 defect in the other direction — the two are told apart by docker's message, never by its exit status. See decisions.md D71." ;;
  *) err "$SCRIPT ACCEPTED a host network that does not exist: '$(one "$n_absent")'. All three are 'external: true', so 'up' fails outright — after .env has been overwritten and .env.previous rotated. See decisions.md D75." ;;
esac
case "$n_absent_data" in
  *"DIE the '$P_NET' network does not exist"*"infra.sh"*)
    ok "an absent hcmarketnet points at ./infra.sh rather than at somebody else's stack" ;;
  *) err "$SCRIPT does not name ./infra.sh for an absent data network: '$(one "$n_absent_data")'. hcmarketnet is this product's own and carries the five stores (decisions.md D49); telling an operator to start ~/webroot/00-infrastructure for it is a remedy for a different network. See decisions.md D75." ;;
esac
# THE ITEM'S SUBJECT. Both readings are fatal; what changes is whether somebody restarts a shared
# plane four products borrow over a daemon that never answered.
case "$n_daemon" in
  *"DIE docker could not be asked whether the '$P_NET' network exists"*)
    case "$n_daemon" in
      *"infra.sh"*|*"start the owning stack"*)
        err "$SCRIPT names the right cause for an unanswerable daemon and then prints the remedy for the wrong one: '$(one "$n_daemon")'. \$net_hint belongs on the absence arm alone — a remedy printed against a cause it does not fit is the whole of backlog NEW-33 in one line. See decisions.md D75." ;;
      *) ok "an unanswerable daemon is reported as an unanswerable daemon, with no remedy borrowed from the absence arm" ;;
    esac ;;
  DIE*) err "$SCRIPT refused a network it could not ask about, but named the wrong cause: '$(one "$n_daemon")'. docker exits 1 and prints '[]' for an absent network AND for a daemon that cannot be asked (measured, 29.8.0), so this arm has to match docker's SENTENCE — 'Error response from daemon' and 'not found', in that order. Anything else is unestablished, not absent. See decisions.md D75 and backlog NEW-33." ;;
  *) err "$SCRIPT ACCEPTED a network it could not ask docker about: '$(one "$n_daemon")'. See decisions.md D75." ;;
esac
# The positive control for the arm above, and the reason the absence match needs two literals:
# `not found` on its own is what a shell says about a docker that is not installed.
case "$n_nodocker" in
  *"DIE ssh reached $P_HOST and there is no"*"docker"*)
    ok "a host with no docker CLI is named as that, not as a missing network" ;;
  DIE*"does not exist"*)
    err "$SCRIPT blamed the network for a host with no docker command on it: '$(one "$n_nodocker")'. 'command not found' carries the substring 'not found', which is why the absence branch must also require docker's 'Error response from daemon' — otherwise this is backlog NEW-33's cost resurrected through its own fix. See decisions.md D71 and D75." ;;
  DIE*)
    err "$SCRIPT refused a host with no docker command, but did not say so: '$(one "$n_nodocker")'. The status is 127 — the shell's own answer for a command it cannot find, measured through a real ssh — and this script installs neither docker nor compose. See decisions.md D75." ;;
  *)
    err "$SCRIPT ACCEPTED a host it could not run docker on: '$(one "$n_nodocker")'. See decisions.md D75." ;;
esac
# AND THE STATE THE 127 BRANCH CANNOT SEE, which is what makes the absence match's two literals a
# decision rather than a substring. docker supports `DOCKER_HOST=ssh://…`; against a host with no
# docker on it the LOCAL CLI answers, exit 1, relaying the far side's `command not found`. So the
# status is an ordinary failure and the only thing between it and "the network does not exist" is
# the requirement that docker's own "Error response from daemon" appear beside `not found`.
case "$n_relayed" in
  *"DIE docker could not be asked whether the '$P_NET' network exists"*)
    ok "docker relaying somebody else's 'not found' is not read as an absent network" ;;
  DIE*"does not exist"*)
    err "$SCRIPT read a relayed 'command not found' as an absent network: '$(one "$n_relayed")'. That message is what docker prints over DOCKER_HOST=ssh:// to a host with no docker on it — exit 1, not 127, so no status branch can catch it. The absence arm must require 'Error response from daemon' AND 'not found', in that order: the sentence only a daemon that ANSWERED can produce. See decisions.md D71 and D75." ;;
  DIE*)
    err "$SCRIPT refused a daemon that relayed a 'not found', but named neither cause: '$(one "$n_relayed")'. See decisions.md D75." ;;
  *)
    err "$SCRIPT ACCEPTED a network whose daemon answered with an error: '$(one "$n_relayed")'. See decisions.md D75." ;;
esac
case "$n_ssh" in
  DIE*"no answer from a shell"*) ok "an unreachable host is reported as an unreachable host, in the network loop too" ;;
  DIE*"does not exist"*) err "$SCRIPT reports an unreachable host as a missing network: '$(one "$n_ssh")'. This is backlog NEW-33 itself, and it is the likeliest of the outcomes because the probe crosses a network. The remedy printed — create it, or start the owning stack — is for the one cause this is not. See decisions.md D75." ;;
  DIE*) err "$SCRIPT refused when ssh could not reach the host, but named neither hop: '$(one "$n_ssh")'. See decisions.md D75." ;;
  *) err "$SCRIPT ACCEPTED three host networks without reaching the host at all: '$(one "$n_ssh")'. See decisions.md D75." ;;
esac
# --dry-run must contact nothing, and must not read as though it had. Same rule as the two `skipped`
# lines above it in preflight: a tick for a check that was not performed is false confidence in the
# one command somebody runs BEFORE touching production.
case "$n_dry" in
  *"OK network"*) err "$SCRIPT's host-network preflight prints a tick under --dry-run: '$(one "$n_dry")'. It contacted nothing; a success line for a check that was not performed is the false confidence the two `skipped` lines beside it exist to remove." ;;
  *"SKIPPED"*"NOT contacted"*) ok "--dry-run says plainly that the networks were not checked" ;;
  *) err "$SCRIPT's host-network preflight under --dry-run answered '$(one "$n_dry")'; it must say the host was not contacted. See decisions.md D75." ;;
esac

# ---- 3. secrets.env: the twelve-value loop, and the arm that must not be empty --------------------
#
# THE FAIL-OPEN IN THIS FILE, and the only assertion here whose mutant PASSES preflight rather than
# refusing for the wrong reason. Fixtures are real: a file with all twelve values, one missing a key,
# a path that is not there, and a DIRECTORY — which `test -s` answers 0 for and `grep` answers **2**
# for, so it constructs the unreadable-file state without `chmod`, which does not constrain root and
# would make this state unbuildable in a root container (red on a correct tree). Measured both ways.
printf '\n%s: secrets.env, twelve values\n' "$SCRIPT"
FIX="$(mktemp -d)"
trap 'rm -rf "$BIN" "$FIX"' EXIT
mkdir -p "$FIX/full" "$FIX/short" "$FIX/unreadable/secrets.env"
{ printf 'JWT_BASE64_SECRET=x\nHC_PRIVACY_PEPPER=x\nHC_GATEWAY_ADMIN_PASSWORD=x\n'
  printf 'HC_GATEWAY_MONGODB_URI=x\n'
  for s in CATALOG BOOKING MESSAGING PAYOUT; do printf 'HC_%s_DB_URL=x\nHC_%s_DB_PASSWORD=x\n' "$s" "$s"; done
} > "$FIX/full/secrets.env"
grep -v '^HC_PAYOUT_DB_PASSWORD=' "$FIX/full/secrets.env" > "$FIX/short/secrets.env"
printf 'x\n' > "$FIX/unreadable/secrets.env/decoy"

s_all="$(HC_PROBE_PATH="$FIX/full" probe connected ok "$LIFT_SECRETS")"
s_short="$(HC_PROBE_PATH="$FIX/short" probe connected ok "$LIFT_SECRETS")"
s_absent="$(HC_PROBE_PATH="$FIX/nowhere" probe connected ok "$LIFT_SECRETS")"
s_unread="$(HC_PROBE_PATH="$FIX/unreadable" probe connected ok "$LIFT_SECRETS")"
s_ssh="$(HC_PROBE_PATH="$FIX/full" probe keyrefused ok "$LIFT_SECRETS")"
s_dry="$(HC_PROBE_PATH="$FIX/full" probe connected ok "DRY_RUN=1; $LIFT_SECRETS")"
printf '  all twelve present:  %s\n  one missing:         %s\n' \
  "$(one "$s_all")" "$(one "$s_short")"
printf '  file absent:         %s\n  file unreadable:     %s\n  ssh key refused:     %s\n' \
  "$(one "$s_absent")" "$(one "$s_unread")" "$(one "$s_ssh")"

# `*DIE*` AND NOT `DIE*` IN THIS PART, which is a bug fix rather than a style: the loop prints an
# `ok` per value it confirms, so a refusal on the twelfth arrives with eleven lines above it and a
# prefix-anchored pattern misses it entirely. Caught by mutating the not-set arm and watching the
# check report "ACCEPTED a secrets file missing HC_PAYOUT_DB_PASSWORD" about a refusal it was
# looking straight at.
present_count="$(printf '%s\n' "$s_all" | { grep -c ' present$' || true; })"
case "$s_all" in
  *DIE*) err "$SCRIPT's secrets check refuses a file that holds all twelve values: '$(one "$s_all")'. A refusal that fires on the correct state is not a check, and every refusal below would then be firing for the wrong reason. See decisions.md D75." ;;
  *) (( present_count == 12 )) \
      && ok "the secrets check passes, and confirms all twelve values, when they are all there" \
      || err "$SCRIPT's secrets check accepted a file holding all twelve values but reported $present_count of them present. Each value is confirmed by name because the receipt of that loop is what an operator reads before a deploy. See decisions.md D75." ;;
esac
case "$s_short" in
  *"DIE HC_PAYOUT_DB_PASSWORD is not set"*) ok "a value that really is absent is still reported as not set, by name" ;;
  *DIE*) err "$SCRIPT refused a secrets file missing HC_PAYOUT_DB_PASSWORD without saying which value is missing: '$(one "$s_short")'. Twelve values are checked one at a time precisely so the refusal names one. See decisions.md D75." ;;
  *) err "$SCRIPT ACCEPTED a secrets file missing HC_PAYOUT_DB_PASSWORD ('$(one "$s_short")'). Every one of the twelve is ':?' in docker-compose.prod.yml, so the deploy would die at 'up' with .env already overwritten and .env.previous rotated. See decisions.md D75." ;;
esac
case "$s_absent" in
  *"DIE"*"is missing or empty"*) ok "a secrets.env that is not there is reported as missing" ;;
  *DIE*) err "$SCRIPT refused an absent secrets.env with something other than 'missing or empty': '$(one "$s_absent")'. See decisions.md D75." ;;
  *) err "$SCRIPT ACCEPTED a host with no secrets.env at all ('$(one "$s_absent")'). See decisions.md D75." ;;
esac
# THE ONE THAT PASSES WHEN IT BREAKS. grep exits 2 for a file it cannot read; with the `*)` arm
# emptied there is no output at all and the loop walks all twelve values, so this assertion has to
# be about a refusal ARRIVING, not about which words it used.
case "$s_unread" in
  *"could not be read while looking for"*) ok "a secrets.env that cannot be READ is refused, and not reported as a value being unset" ;;
  *DIE*"is not set"*) err "$SCRIPT reported a secrets.env it could not read as a value being unset: '$(one "$s_unread")'. grep answers 2 for a file it cannot read — a 0600 file owned by another account is the live case — and 'add HC_… to this file' is advice about a file the operator cannot open. See decisions.md D75." ;;
  *DIE*) err "$SCRIPT refused an unreadable secrets.env but named neither cause: '$(one "$s_unread")'. See decisions.md D75." ;;
  *) err "$SCRIPT ACCEPTED a secrets.env it could not read, and walked all twelve values past it: '$(one "$s_unread")'. This is WORSE than the message NEW-33 was about: preflight passes, the deploy proceeds, and nothing established that any of the twelve values is there. The remote status arm must refuse anything that is neither 0 nor 1. See decisions.md D75." ;;
esac
case "$s_ssh" in
  *DIE*"no answer from a shell"*) ok "an ssh the host refused is reported as that, not as a missing secrets.env" ;;
  *DIE*) err "$SCRIPT reported an ssh the host refused as a fact about secrets.env: '$(one "$s_ssh")'. Nothing on the host was read, so neither the file nor any value in it is established. See decisions.md D75." ;;
  *) err "$SCRIPT ACCEPTED twelve values it never asked about ('$(one "$s_ssh")'). See decisions.md D75." ;;
esac
case "$s_dry" in
  *" present"*) err "$SCRIPT's secrets check reports values present under --dry-run: '$(one "$s_dry")'. It contacted nothing." ;;
  *"SKIPPED"*"NOT contacted"*) ok "--dry-run says plainly that secrets.env was not read" ;;
  *) err "$SCRIPT's secrets check under --dry-run answered '$(one "$s_dry")'; it must say the host was not contacted. See decisions.md D75." ;;
esac

# ---- 4. The previous tag a rollback needs ---------------------------------------------------------
#
# rollback() is where every FAILED deploy lands, so it is the worst place in the file to be told to
# go and look for a .env.previous that is sitting there intact. The status here is `cut`'s — 0 even
# when grep matched nothing — so the discriminator is the OUTPUT, and what the status arm catches is
# `cd` refusing the directory: a wrong --path wearing a fact about the estate's deployment history.
printf '\n%s: the previous tag\n' "$SCRIPT"
mkdir -p "$FIX/rolled" "$FIX/first"
# CRLF, deliberately: `cut` hands back the carriage return with the tag, and an untrimmed `1.4.0\r`
# is a tag no image in either registry carries — so the rollback would pull nothing and say the
# previous deployment is broken. It is also the only fixture that exercises the trim at all, which is
# why this file writes one rather than the obvious `\n`. A `.env.previous` edited on Windows, or one
# written by a shell that echoed a CR, is the live case.
printf 'HC_TAG=1.4.0\r\nHC_CHANNEL=github\r\n' > "$FIX/rolled/.env.previous"
printf 'HC_CHANNEL=github\n' > "$FIX/first/.env.previous"
REPORT_PREV='printf "PREV=[%s]\n" "$prev"'
p_tag="$(HC_PROBE_PATH="$FIX/rolled" probe connected ok "$LIFT_ROLLBACK"$'\n'"$REPORT_PREV")"
p_none="$(HC_PROBE_PATH="$FIX/first" probe connected ok "$LIFT_ROLLBACK"$'\n'"$REPORT_PREV")"
p_path="$(HC_PROBE_PATH="$FIX/nowhere" probe connected ok "$LIFT_ROLLBACK"$'\n'"$REPORT_PREV")"
p_ssh="$(HC_PROBE_PATH="$FIX/rolled" probe unreachable ok "$LIFT_ROLLBACK"$'\n'"$REPORT_PREV")"
printf '  a previous deploy:   %s\n  a first deploy:      %s\n' "$(one "$p_tag")" "$(one "$p_none")"
printf '  a wrong --path:      %s\n  ssh unreachable:     %s\n' "$(one "$p_path")" "$(one "$p_ssh")"

case "$p_tag" in
  *"PREV=[1.4.0]"*) ok "the previous tag is read off .env.previous and trimmed, CRLF and all" ;;
  DIE*) err "$SCRIPT refuses to read a previous tag that IS there: '$(one "$p_tag")'. Nothing else in this part means anything if the good state refuses. See decisions.md D75." ;;
  *) err "$SCRIPT read '$(one "$p_tag")' from a .env.previous holding HC_TAG=1.4.0; the rollback would then target the wrong tag or none. See decisions.md D75." ;;
esac
case "$p_none" in
  *"DIE no previous deployment recorded"*) ok "a first deploy is still reported as having nothing to roll back to" ;;
  DIE*) err "$SCRIPT refused a first deploy with something other than 'no previous deployment recorded': '$(one "$p_none")'. That message also says the stack is still running what was just rolled onto it, which is the operator's next move. See decisions.md D75." ;;
  *) err "$SCRIPT ACCEPTED an empty previous tag ('$(one "$p_none")') and would roll the stack onto nothing. See decisions.md D75." ;;
esac
# M-b's door. Both readings are fatal; the difference is whether an operator goes hunting for a file
# or fixes --path.
case "$p_path" in
  *"DIE the previous tag could not be read"*) ok "a --path the host has no such directory for is named as that, not as a first deploy" ;;
  DIE*"no previous deployment recorded"*) err "$SCRIPT reported a wrong --path as a first deploy: '$(one "$p_path")'. \`cd\` refused the directory and the status said so; reported as 'nothing to roll back to' it is a deployment-argument fault wearing a fact about this estate's history, in the function every failed deploy reaches. See decisions.md D75." ;;
  DIE*) err "$SCRIPT refused an unreadable previous tag but named neither cause: '$(one "$p_path")'. See decisions.md D75." ;;
  *) err "$SCRIPT ACCEPTED a previous tag it could not read ('$(one "$p_path")'). See decisions.md D75." ;;
esac
case "$p_ssh" in
  DIE*"no answer from a shell"*) ok "an unreachable host is not reported as a first deploy either" ;;
  DIE*"no previous deployment recorded"*) err "$SCRIPT reported an unreachable host as a first deploy: '$(one "$p_ssh")'. This is the fold NEW-33 was about, in the one function a failed deploy is guaranteed to reach — and the remedy it prints is to stop looking for a .env.previous that was never read. See decisions.md D75." ;;
  DIE*) err "$SCRIPT refused when ssh could not reach the host, but named neither hop: '$(one "$p_ssh")'. See decisions.md D75." ;;
  *) err "$SCRIPT ACCEPTED a previous tag from a host it never reached ('$(one "$p_ssh")'). See decisions.md D75." ;;
esac

# ---- 5. Every remote probe in the file goes through host_run --------------------------------------
#
# Part 1 drives host_run and part 2 drives the network arm; neither can see a SIXTH ssh growing back
# beside them, and every site that stops routing through host_run goes straight back to folding an
# ssh failure into a claim about the host. So this is textual, and the count is the assertion: the
# only bare `ssh` invocations left in the file are the ones in the deploy, health-gate, smoke-test
# and rollback-write phases, which are either `run`-wrapped (the command is printed), ERR-trapped
# (the command is printed), or polls — D71 §5's rule, `a die may not fold; a warn and a poll may`.
printf '\n%s: the call sites\n' "$SCRIPT"
# THE STRIPPER'S ABSENCE IS FATAL ABOVE, not handled here: it is one file that two parts of this check
# and every text-matching check in the repository trust, so it is refused once, before anything reads
# anything. This branch used to carry that guard and would now be unreachable — a dead `else` that
# reads as covering something.
{
  # BACKSLASH-CONTINUED LINES ARE JOINED FIRST, and that is a bug fix rather than a flourish: four
  # of the six calls below are written across two lines, so a line-at-a-time grep for the remote
  # command finds the CONTINUATION and reports a perfectly correct call site as unrouted. Watched
  # doing exactly that. Same treatment build.yml's brokerage probe needed for the same reason.
  stripped="$(printf '%s\n' "$STRIPPED_SRC" | sed -e ':a' -e '/\\$/N; s/\\\n//; ta')"
  # The comments in this file quote the OLD folded lines verbatim, so an unstripped count would be
  # counting a paragraph. Measured on a clean tree: 6 raw, 0 stripped, for `-o BatchMode`.
  batch="$(printf '%s\n' "$stripped" | { grep -c 'ssh -o BatchMode=yes' || true; })"
  (( batch == 0 )) \
    && ok "no probe builds its own ssh options — SSH_OPTS is the one place BatchMode and the timeout are set" \
    || err "$SCRIPT has $batch ssh invocation(s) spelling out their own '-o BatchMode=yes' rather than going through host_run and SSH_OPTS. Each is a probe whose failure cannot be attributed to a hop, and an ssh with no ConnectTimeout is a refusal that arrives minutes late or never. See decisions.md D75."
  # ---- THE GATE'S TWO CALLERS WERE ASSERTED HERE, AS TEXT, AND ARE NOT ANY MORE -------------------
  #
  # decisions.md D80, backlog NEW-42. D78 §15's blocking finding was that swapping `health_gate
  # rollback` for `health_gate deploy` in `rollback()` parsed and left this check AND its test at exit
  # 0. Its repair was two stripped-text greps — `rollback()`'s body must contain `health_gate
  # rollback`, the router must read `if health_gate deploy && smoke_test` — and it said at this spot
  # that it was a compromise, because part 6 drives the FUNCTION with call strings this harness writes
  # and can never see what the program passes.
  #
  # PART 7 EXECUTES BOTH CALLERS NOW, so both greps are gone rather than kept beside it. Two
  # mechanisms guarding one property is how one of them rots unnoticed, and the textual one is the one
  # that would: it matched a fixed spelling of a branch, so `if health_gate "$phase" && smoke_test`
  # or any restructuring of the router was red on a correct tree while a swap was green on a broken
  # one. Cases 37 and 38 are the same two mutations, red now through what the shipped program passed
  # rather than through what it says.
  #
  # Every site preflight asks about, by the question it asks. Enumerated, and that is acceptable
  # only because a site that is missing is an ERROR here rather than a skip: the whole point is that
  # a probe stopping short of host_run is invisible to parts 1 and 2.
  sites=(
    "docker compose version:the ssh and compose-v2 gate"
    "{{.Health}}:what state the services are in once the health gate has timed out"
    "logs --no-color --tail:the log lines the rollback is about to destroy"
    "test -s:secrets.env being there at all"
    "grep -qE:each of the twelve values in secrets.env"
    "docker network inspect:the three host networks"
    "ps -a --format:the five stores in the data tier"
    "grep -m1 '^HC_TAG=:the previous tag a rollback needs"
  )
  for site in "${sites[@]}"; do
    needle="${site%%:*}"; what="${site#*:}"
    line="$(printf '%s\n' "$stripped" | { grep -F "$needle" || true; } | head -1)"
    if [[ -z "$line" ]]; then
      err "$SCRIPT no longer asks the host about $what (nothing matching '$needle'), so this check compared against a probe that is not there. Rename or remove a probe and this check must be told, not left asserting nothing. See decisions.md D75."
    elif [[ "$line" == *host_run* ]]; then
      ok "$what is asked through host_run"
    else
      err "$SCRIPT asks the host about $what without going through host_run: '$line'. That probe folds ssh not arriving, the host having no docker, and the answer being no into one message — which is backlog NEW-33, at a different door. See decisions.md D75."
    fi
  done
}

# ---- 6. The health gate: the polls still fold, and the exhaustion no longer does ------------------
#
# decisions.md D78, backlog NEW-36. This part comes after the textual one because the numbers are
# names — four parts' worth of messages cite their own number, and renumbering them to insert a
# driving part in the middle is churn for nothing.
#
# TWO ASSERTIONS THAT PULL IN OPPOSITE DIRECTIONS, which is the whole reason this needs driving
# rather than grepping. The 24 polls MUST keep folding: a status check inside the loop makes a
# one-second flake fatal on the estate's slowest gate, so the transient case below is a positive
# control for the defect NOT being "fixed" too far in. The EXHAUSTION must not fold: it is fatal by
# way of `rollback`, and a host that went away mid-deploy used to be reported as five healthy
# services failing and have the stack rolled back on top of it.
#
# THE ROLLBACK IS THE SUBJECT, not the wording. Only one arm may return — the one where the host
# answered and its own healthcheck agrees something is unready. Every other arm is a `die`, which
# stops the deploy and reverts nothing, because `rollback` needs the same host the gate has just
# failed to reach and reverting a healthy estate is the harm this exists to prevent.
printf '\n%s: the health gate, and what its exhaustion establishes\n' "$SCRIPT"
# THE BLIP ARM'S PREMISE, WHICH LIVES IN ANOTHER FILE — decisions.md D78 §14, review's note. Its
# decidability rests entirely on `{{.Health}}` meaning something: every app service in
# docker-compose.prod.yml inherits a healthcheck that makes the SAME /management/health/readiness
# request the gate makes, run by the daemon inside the host. Drop or rename that block and docker's
# health column goes blank for all five, every blip becomes an established-unready rollback — NEW-36's
# own harm — and every assertion in this part stays green, because the stub answers with a health
# column whatever the compose file says. So the premise is asserted here rather than left implied.
if [[ ! -f "$PROD_COMPOSE" ]]; then
  err "$PROD_COMPOSE is missing, so the health column the blip arm reads is unestablished. See decisions.md D78 §3."
# The ten lines FOLLOWING each `healthcheck:`, not an awk range: the obvious terminator
# (`/^ *[a-z_]+:$/`) matches the `healthcheck:` line itself, so the range closed on its own first line
# and the assertion reported the block missing on a correct tree. Caught by running it.
#
# COMMENTS IN THAT WINDOW ARE BLANKED FIRST — the THIRD comment-satisfies-a-guard on this branch, and
# it arrived inside the commit repairing the second. Measured: replace every readiness path in the
# prod compose with `/management/health` and add `# readiness (/management/health/readiness) dropped:
# …` inside the window, and this printed `ok`. That is not a contrived edit — whoever weakens a
# healthcheck writes down which path they removed.
#
# A LOCAL TWO-LINE AWK, DELIBERATELY NOT THE SHELL STRIPPER. This subject is YAML; `strip-sh-comments
# .awk`'s contract is shell, and the house rule is explicit that a stripper which guesses its language
# from the file is one missing case away from stripping neither. Its stated limit is the same one the
# shell stripper carries: a `#` inside a quoted scalar truncates the line, which fails CLOSED (the
# readiness path would stop matching and this guard would refuse a correct tree loudly). Nothing in
# the window has one today, and if a healthcheck ever needs a `#` in its command the refusal will say
# so rather than pass.
elif ! awk '/^ *healthcheck:/ { n = 10; next } n-- > 0' "$PROD_COMPOSE" \
       | awk '{ sub(/[[:space:]]*#.*$/, ""); print }' \
       | grep -q '/management/health/readiness'; then
  err "$PROD_COMPOSE declares no healthcheck making the /management/health/readiness request, so docker's {{.Health}} column is blank for every service and gate_exhausted's blip arm can never fire. A host that goes away for one poll then reads as five services that failed, and the deploy reverts a healthy estate — which is backlog NEW-36's own harm, arriving through the premise of its fix. Every assertion in part 6 stays green either way, because the stub answers with a health column regardless. See decisions.md D78 §3 and §14."
elif grep -qE '^ *disable: *true' "$PROD_COMPOSE"; then
  # A PER-SERVICE OVERRIDE PUTS THE COLUMN BACK TO BLANK for that service with the anchor intact, so
  # the guard above — which establishes that *a* healthcheck in this file requests readiness — is
  # weaker than the sentence this repository writes about it ("every app service inherits it").
  # Refused rather than stated, because one blank column is enough to make the blip arm mis-read.
  err "$PROD_COMPOSE disables a healthcheck somewhere (\`disable: true\`). A service whose healthcheck is disabled reports a BLANK {{.Health}}, which gate_exhausted reads as unproven — so a host that blinks for one poll takes that service down the established-unready arm and the stack is reverted. The blip arm's premise is that all five inherit the same readiness check. See decisions.md D78 §3 and §15."
else
  ok "the prod compose still healthchecks readiness, and disables it nowhere, so {{.Health}} is a second opinion rather than a blank column"
fi
# A REAL DIRECTORY, because every remote command in this script begins `cd '$REMOTE_PATH' && …` and
# the shipped `cd` is part of what is being driven. Pointed at the default /srv/healthconnect, all
# ten readings below are the `cd` refusing — which is a correct answer to a question this part is not
# asking, and it looked exactly like the gate refusing everything. Found by running it.
mkdir -p "$FIX/stack"
GATE='health_gate deploy; printf "GATE_RC=%s\n" "$?"'
# THE SECOND CALLER, and the reason the phase is a parameter at all (decisions.md D78 §14):
# `rollback` calls this gate AFTER it has already restored .env and rolled the stack, so every
# refusal that says "nothing has been rolled back" is false there — in the one function a failed
# deploy is guaranteed to reach.
GATE_RB='health_gate rollback; printf "GATE_RC=%s\n" "$?"'
GATE_NOPHASE='health_gate; printf "GATE_RC=%s\n" "$?"'
g_ready="$(HC_PROBE_PATH="$FIX/stack" probe connected gate-ready "$GATE")"
g_flake="$(HC_PROBE_PATH="$FIX/stack" HC_PROBE_HEALTH_TIMEOUT=20 probe connected gate-flake "$GATE")"
g_unready="$(HC_PROBE_PATH="$FIX/stack" probe connected gate-unready "$GATE")"
g_blip="$(HC_PROBE_PATH="$FIX/stack" probe connected gate-blip "$GATE")"
g_empty="$(HC_PROBE_PATH="$FIX/stack" probe connected gate-empty "$GATE")"
g_daemon="$(HC_PROBE_PATH="$FIX/stack" probe connected daemon-down "$GATE")"
g_nodocker="$(HC_PROBE_PATH="$FIX/stack" probe connected cli-not-found "$GATE")"
g_ssh="$(HC_PROBE_PATH="$FIX/stack" probe unreachable gate-unready "$GATE")"
g_logs="$(HC_PROBE_PATH="$FIX/stack" probe connected gate-logs-lost "$GATE")"
g_dry="$(HC_PROBE_PATH="$FIX/stack" probe connected gate-unready "DRY_RUN=1; $GATE")"
g_rb_daemon="$(HC_PROBE_PATH="$FIX/stack" probe connected daemon-down "$GATE_RB")"
g_rb_unready="$(HC_PROBE_PATH="$FIX/stack" probe connected gate-unready "$GATE_RB")"
g_nophase="$(HC_PROBE_PATH="$FIX/stack" probe connected gate-unready "$GATE_NOPHASE")"
printf '  ready first poll:    %s\n  a transient:         %s\n' "$(one "$g_ready")" "$(one "$g_flake")"
printf '  really unready:      %s\n' "$(one "$g_unready")"
printf '  a blip, host agrees: %s\n' "$(one "$g_blip")"
printf '  no containers:       %s\n  daemon unanswerable: %s\n' "$(one "$g_empty")" "$(one "$g_daemon")"
printf '  no docker on host:   %s\n  ssh unreachable:     %s\n' "$(one "$g_nodocker")" "$(one "$g_ssh")"
printf '  logs lost after ps:  %s\n  dry run:             %s\n' "$(one "$g_logs")" "$(one "$g_dry")"
printf '  from rollback, daemon gone: %s\n' "$(one "$g_rb_daemon")"
printf '  from rollback, unready:     %s\n  no phase at all:            %s\n' "$(one "$g_rb_unready")" "$(one "$g_nophase")"

# THE POSITIVE CONTROL. Without it every refusal below is satisfied by a gate that refuses
# everything, which is this repository's sixteenth-instance rule applied to the gate itself.
case "$g_ready" in
  *"OK all services report READY"*"GATE_RC=0"*)
    ok "the gate passes, and says so, when every service answers on the first poll" ;;
  *) err "$SCRIPT's health gate did not pass for services that are READY: '$(one "$g_ready")'. Every refusal asserted below would then be firing for the wrong reason. See decisions.md D78." ;;
esac
# AND THE OTHER CONTROL, which is about the fold rather than about the refusal: the first poll of
# each service fails and the second succeeds. A status check moved INSIDE the loop turns this into a
# failed deploy, so this case is what stands between D78's repair and D71 §5's stated reason for
# leaving the loop alone.
case "$g_flake" in
  *"OK all services report READY"*"GATE_RC=0"*)
    ok "a service that fails one poll and answers the next is still a pass — the loop folds, deliberately" ;;
  *DIE*) err "$SCRIPT's health gate DIED on a service that failed its first poll and answered the next: '$(one "$g_flake")'. The 24 polls fold both streams and every status on purpose — an unanswerable daemon or a host that blinked must be a retry, and a status check inside the loop makes a one-second flake a rolled-back deploy. D78 repairs the EXHAUSTION, not the poll. See decisions.md D71 §5 and D78." ;;
  *) err "$SCRIPT's health gate answered '$(one "$g_flake")' for a transient that cleared on the second poll; it must pass. See decisions.md D78." ;;
esac
# THE ONE ARM THAT MAY ROLL BACK. The host answered, and its own healthcheck — the same readiness
# probe, run inside the host — agrees that something is not ready.
case "$g_unready" in
  *"GATE_RC=1"*)
    case "$g_unready" in
      *"host ANSWERED"*"hc-market-catalog running unhealthy"*)
        ok "an exhausted gate against services that really are unready says the host answered, and shows what it said" ;;
      *) err "$SCRIPT's health gate refused services that really are unready without establishing that the HOST answered: '$(one "$g_unready")'. That is the difference between 'they never became ready' and 'we stopped being able to ask', and it is the whole of backlog NEW-36 — the refusal has to name which, from evidence. See decisions.md D78." ;;
    esac ;;
  *DIE*) err "$SCRIPT's health gate DIED against services that are genuinely unready on a host that answered: '$(one "$g_unready")'. This is the one arm that must return, so that the router below rolls the stack back — refusing here leaves a broken deploy in place. See decisions.md D78." ;;
  *) err "$SCRIPT's health gate answered '$(one "$g_unready")' for services that never became ready; it must return 1 so the deploy rolls back. See decisions.md D78." ;;
esac
# THE BLIP, AND THE REASON THE EXHAUSTION ASKS A DIFFERENT QUESTION AT ALL. The polls got nothing
# and docker, running the same readiness probe inside the host, reports both services healthy.
# Rolling a healthy estate back over that is the failure NEW-36 exists to prevent.
case "$g_blip" in
  *DIE*"RUNNING and HEALTHY"*)
    case "$g_blip" in
      *"NOTHING HAS BEEN ROLLED BACK"*) ok "a gate that timed out while docker calls every service healthy refuses, and says nothing was reverted" ;;
      *) err "$SCRIPT's health gate names the blip correctly and does not say the stack was left alone: '$(one "$g_blip")'. The operator's next question is whether $TAG is still on the host, and the answer decides whether they reach for --rollback. See decisions.md D78." ;;
    esac ;;
  *"GATE_RC=1"*) err "$SCRIPT's health gate ROLLED BACK an estate whose every service docker itself reports as running and healthy: '$(one "$g_blip")'. The gate's probes cross an ssh and docker's healthcheck does not, so a host unreachable for one poll puts every service in the bad list while all of them were ready — and the deploy then reverts a working stack. This is backlog NEW-36 exactly. See decisions.md D78." ;;
  *) err "$SCRIPT's health gate answered '$(one "$g_blip")' when docker reported every unready service as healthy. See decisions.md D78." ;;
esac
# The host answered and knows of no containers. Measured: `ps -a` exits 0 with no output at all for a
# project that has none, so this state is invisible to a status check.
case "$g_empty" in
  *DIE*"NO CONTAINERS AT ALL"*) ok "a project the host has no containers for is named as that, not as five services that failed" ;;
  *"GATE_RC=1"*) err "$SCRIPT's health gate reported a project with NO CONTAINERS as services that failed to become ready, and rolled back: '$(one "$g_empty")'. \`ps -a\` exits 0 with empty output there (measured, 29.8.0), so nothing was established to be unready — there is nothing there. An older tag is not the remedy for a --path or a compose project that is not the one just rolled. See decisions.md D78." ;;
  *DIE*) err "$SCRIPT's health gate refused a project with no containers but named neither cause: '$(one "$g_empty")'. See decisions.md D78." ;;
  *) err "$SCRIPT's health gate answered '$(one "$g_empty")' for a project with no containers at all. See decisions.md D78." ;;
esac
# THE ITEM'S OWN SUBJECT, one layer in from the ssh hop: the shell answered and the daemon did not.
case "$g_daemon" in
  *DIE*"could not then be asked"*)
    case "$g_daemon" in
      *"NOT established"*"NOTHING HAS BEEN ROLLED BACK"*) ok "a daemon that cannot be asked at the timeout is refused, and the services are not accused" ;;
      *) err "$SCRIPT's health gate names an unanswerable daemon and then does not say what is unestablished, or does not say the stack was left alone: '$(one "$g_daemon")'. Both halves are the message: the services were not established to have failed, and a rollback needs the same host nobody could ask. See decisions.md D78." ;;
    esac ;;
  *"GATE_RC=1"*) err "$SCRIPT's health gate ROLLED THE STACK BACK over a daemon it could not ask: '$(one "$g_daemon")'. \`compose exec\` exits 1 for a refused port, a stopped service, an undeclared service AND an unanswerable daemon (measured, 29.8.0), so the poll cannot tell them apart and the exhaustion must ask a question whose status is honest. See decisions.md D78 and backlog NEW-36." ;;
  *DIE*) err "$SCRIPT's health gate refused at the timeout but did not say the daemon could not be asked: '$(one "$g_daemon")'. See decisions.md D78." ;;
  *) err "$SCRIPT's health gate answered '$(one "$g_daemon")' with a daemon that could not be asked. See decisions.md D78." ;;
esac
case "$g_nodocker" in
  *DIE*"there is no"*"docker"*) ok "a host with no docker CLI is named as that at the timeout too" ;;
  *"GATE_RC=1"*) err "$SCRIPT's health gate reported a host with no docker command as services that failed to become ready: '$(one "$g_nodocker")'. The status is 127 and this script installs neither docker nor compose. See decisions.md D75 and D78." ;;
  *) err "$SCRIPT's health gate answered '$(one "$g_nodocker")' for a host with no docker CLI on it. See decisions.md D78." ;;
esac
# THE FAILURE THIS ITEM EXISTS FOR. Both readings are fatal; the difference is whether a healthy
# estate gets rolled back over a link that went down.
case "$g_ssh" in
  *DIE*"no answer from a shell"*) ok "an exhausted gate against a host that cannot be reached says so, and does not roll back" ;;
  *"GATE_RC=1"*) err "$SCRIPT's health gate reported an unreachable host as unhealthy services and fell through to a rollback: '$(one "$g_ssh")'. ssh exits 255 when it cannot connect and the poll discards that, so the exhaustion has to ask once more through host_run — which establishes the hop from the answer rather than from a status. This is backlog NEW-36 itself. See decisions.md D75 and D78." ;;
  *DIE*) err "$SCRIPT's health gate refused when ssh could not reach the host, but named neither hop: '$(one "$g_ssh")'. See decisions.md D78." ;;
  *) err "$SCRIPT's health gate answered '$(one "$g_ssh")' without reaching the host at all. See decisions.md D78." ;;
esac
# THE SECOND ROUND TRIP IS EVIDENCE, NOT A DECISION, so losing it must not change the answer. D71
# §5's rule the other way round: unreadiness is established by then, and a `die` here would turn a
# correct rollback into a refusal over a daemon that went quiet one round trip later.
case "$g_logs" in
  *"GATE_RC=1"*)
    case "$g_logs" in
      *"logs could not be read"*) ok "logs that cannot be read are reported as that, and the rollback still happens" ;;
      *) err "$SCRIPT's health gate rolled back correctly but said nothing about the logs it could not read: '$(one "$g_logs")'. The rollback recreates those containers, so a reader who is not told they are gone will look for them afterwards. See decisions.md D78." ;;
    esac ;;
  *DIE*) err "$SCRIPT's health gate DIED because it could not read the failed services' logs: '$(one "$g_logs")'. By then the host has answered and its own healthcheck agrees something is unready, so the diagnosis is complete and the evidence is a bonus — refusing here leaves a broken deploy in place over a second round trip. See decisions.md D78." ;;
  *) err "$SCRIPT's health gate answered '$(one "$g_logs")' when the log read failed after a successful state probe. See decisions.md D78." ;;
esac
# CALLED FROM `rollback`, A ROLLBACK HAS ALREADY HAPPENED. Both readings stop the deploy; what changes
# is whether the operator is told to run a command that has already run.
case "$g_rb_daemon" in
  *DIE*"ALREADY BEEN APPLIED"*)
    case "$g_rb_daemon" in
      *"NOTHING HAS BEEN ROLLED BACK"*) err "$SCRIPT's health gate says a rollback has already been applied AND that nothing has been rolled back, in one message: '$(one "$g_rb_daemon")'. See decisions.md D78 §14." ;;
      *"--rollback --host"*) err "$SCRIPT's health gate offers \`--rollback\` as the remedy when called FROM the rollback: '$(one "$g_rb_daemon")'. That command has just run; the remedy there is a person. See decisions.md D78 §14." ;;
      *) ok "a refusal reached from the rollback says the rollback was already applied, and offers no second one" ;;
    esac ;;
  *DIE*"NOTHING HAS BEEN ROLLED BACK"*)
    err "$SCRIPT's health gate claims NOTHING HAS BEEN ROLLED BACK when it was called from \`rollback\`, which had already restored .env and rolled the stack: '$(one "$g_rb_daemon")'. Both readings are fatal; the difference is that this one sends an operator to run the command that has just run, in the one function every FAILED deploy reaches. A refusal asserting a state it has not established is this family's own defect. See decisions.md D78 §14." ;;
  *DIE*) err "$SCRIPT's health gate refused from the rollback without saying what state the stack is in: '$(one "$g_rb_daemon")'. See decisions.md D78 §14." ;;
  *) err "$SCRIPT's health gate answered '$(one "$g_rb_daemon")' from the rollback with a daemon that could not be asked. See decisions.md D78 §14." ;;
esac
case "$g_rb_unready" in
  *"GATE_RC=1"*)
    case "$g_rb_unready" in
      *"rolling back."*) err "$SCRIPT's health gate says it is rolling back when it was called FROM the rollback: '$(one "$g_rb_unready")'. Its caller refuses on the next line and no second revert happens, so that sentence describes something nobody is going to do. See decisions.md D78 §14." ;;
      *"nothing further to revert to"*) ok "an unready stack reached from the rollback says there is nothing further to revert to" ;;
      *) err "$SCRIPT's health gate returned from the rollback without saying what happens next: '$(one "$g_rb_unready")'. See decisions.md D78 §14." ;;
    esac ;;
  *) err "$SCRIPT's health gate answered '$(one "$g_rb_unready")' for an unready stack reached from the rollback; it must return 1 so its caller can refuse. See decisions.md D78 §14." ;;
esac
# AND NO DEFAULT. A third caller that forgets the argument would otherwise inherit whichever context
# was written first, which is the same wrong claim arriving silently.
case "$g_nophase" in
  *DIE*"called with no phase"*) ok "a gate called with no phase refuses rather than guessing which claim to make" ;;
  *) err "$SCRIPT's health gate accepted a call with NO phase: '$(one "$g_nophase")'. Both of its refusals state whether the stack was left alone or already reverted, so a caller that does not say which gets one of them wrong — silently, and in a message an operator acts on. See decisions.md D78 §14." ;;
esac
case "$g_dry" in
  *"[dry-run] skipped"*)
    case "$g_dry" in
      *DIE*|*"WARN"*) err "$SCRIPT's health gate contacted the host under --dry-run: '$(one "$g_dry")'. The one production-path command that must touch nothing is this one. See decisions.md D75." ;;
      *) ok "--dry-run skips the gate without asking the host anything" ;;
    esac ;;
  *) err "$SCRIPT's health gate under --dry-run answered '$(one "$g_dry")'; it must skip. See decisions.md D78." ;;
esac

# ---- 7. The PROGRAM, run: the phase each caller passes, and the options each ssh receives ---------
#
# decisions.md D80, backlog NEW-42. Parts 1-6 lift functions with `awk` and drive them with call
# strings THIS FILE writes — `GATE='health_gate deploy; …'` — so they verify a function and can never
# verify the program. Three rounds of findings in this area (D78 §13, §14, §15) were each a fail-open
# in the guard that closed the round before, and every one of them was a textual assertion ABOUT a
# call site: the value behind the option name, the code behind the comment, the caller behind the
# phase. §15 wrote the conclusion down — the wrong decision was to guard a call site by reading it —
# and this part is the repair. Its two subjects are the two the item names: which phase each shipped
# caller hands the gate, and which options each individual `ssh` is actually handed.
#
# TWO DRIVERS, and neither subsumes the other:
#
#   7a  the file EXECUTED as a subprocess, against stubbed ssh, scp, docker, curl and git on PATH.
#       This is the only reading that can see whether `main` is INVOKED at all: delete the
#       `[[ "${BASH_SOURCE[0]}" == "$0" ]]` line and the script parses, defines every function and
#       exits 0 having deployed nothing — which no text and no sourced probe can tell apart from a
#       working program.
#   7b  the file SOURCED, then `main` and `rollback` called by hand. That is what gives the array
#       `SSH_OPTS` genuinely holds at run time (after the script's own HC_SSH_TIMEOUT guard has run),
#       a transcript this file can split at the rollback, and a way to reach `rollback` alone.
#
# `run` IS NOT STUBBED and does not need to be, which is the one place this goes further than the
# item costed: every mutating command already goes through it and it ends in `"$@"`, so a shell
# looking up `ssh` finds the stub on PATH. Stubbing it would replace the wrapper that decides whether
# --dry-run prints instead of running.
#
# WHAT THE STUBS RECORD IS THE ARGUMENT VECTOR — one line per invocation, `\037` between arguments —
# so "which options did this site receive" is asked of the arguments and never of a rendered string
# somebody could quote a fragment of. The expected option list is not written here: it is read out of
# the running script, so an option ADDED to the array and not reaching a site is red too.
#
# WHAT THIS STILL DOES NOT ESTABLISH — decisions.md D49, unchanged and unchangeable from here.
# `deploy-prod.sh` has never been run against a host. Executing and sourcing it against stubs
# establishes what the script PASSES; what a production host answers is not measurable from this
# workstation, and nothing below claims otherwise.
printf '\n%s: the program, executed\n' "$SCRIPT"
X7="$(mktemp -d)"
trap 'rm -rf "$BIN" "$FIX" "$X7"' EXIT
B7="$X7/bin"; mkdir -p "$B7"
T7="1.4.0-probe"
case "$SCRIPT" in /*) SCRIPT_ABS="$SCRIPT" ;; *) SCRIPT_ABS="$ROOT/$SCRIPT" ;; esac
case "$PROD_COMPOSE" in /*) PROD_COMPOSE_ABS="$PROD_COMPOSE" ;; *) PROD_COMPOSE_ABS="$ROOT/$PROD_COMPOSE" ;; esac

# `${@: -1}` IS THE REMOTE COMMAND at every one of this script's ssh sites, and running it is the
# same decision part 1's stub made: the sentinel host_run's answers turn on is produced by the
# shipped code, never by this file. No unreachable-ssh arm here — part 1 owns that state, and an arm
# nothing drives is a claim rather than a stub.
# ONE PHYSICAL LINE PER INVOCATION, and the newline substitution is not tidiness: host_run's wrapped
# command is deliberately MULTI-LINE — `($2\n)\nprintf …` — so a logged argument carrying it splits
# into four records, and the reader below then reports `)` as an unrecognised remote invocation. Found
# by running it. \037 separates arguments, \034 stands in for a newline inside one.
cat > "$B7/ssh" <<'S7'
#!/usr/bin/env bash
line="SSH"; for a in "$@"; do line+=$'\037'"$a"; done
printf '%s\n' "${line//$'\n'/$'\034'}" >> "$HC_SITE_LOG"
bash -c "${@: -1}"
S7
cat > "$B7/scp" <<'S7'
#!/usr/bin/env bash
line="SCP"; for a in "$@"; do line+=$'\037'"$a"; done
printf '%s\n' "${line//$'\n'/$'\034'}" >> "$HC_SITE_LOG"
S7
# THE COMPOSE SUBCOMMAND FIRST, exactly as the stub in part 6 does, and `ps` renders in the order the
# `--format` asked for so a reorder is visible rather than answered around (D78 §14's note).
#
# `pull` and `up` SUCCEED in the daemon-gone-after-roll state, and that is the state's name rather
# than a shortcut: what the rollback-phase gate exists for is a host that answers the roll and then
# stops answering. A daemon that REFUSED the roll takes the ERR trap long before the gate — which is
# what the `refused` state below drives, because that sentence was written here as an argument and
# was **false of the sourced driver** until D80's review (finding 1): the driver tested its own
# subshell's status, which disregards errexit and the trap for everything inside it.
cat > "$B7/docker" <<'S7'
#!/usr/bin/env bash
argv="$*"
sub=""
for a in "$@"; do
  case "$a" in exec|ps|logs|pull|up|version|login|info|network|manifest) sub="$a"; break ;; esac
done
gone='failed to connect to the docker API at unix:///nonexistent; check if the path is correct and if the daemon is running: dial unix /nonexistent: connect: no such file or directory'
case "${HC_FAKE_DOCKER:-ready}:$sub" in
  # THE ROLL ITSELF REFUSED — a tag the host cannot pull. The shipped `run ssh …` then returns
  # non-zero and the ERR trap must fire; anything that walks past it reaches the gate and reports a
  # rollback that never happened.
  refused:pull)
    printf 'Error response from daemon: manifest for docker.jojoaddison.net/healthconnect/catalog:1.3.9 not found\n' >&2
    exit 1 ;;
esac
case "$sub" in
  info|network|manifest|pull|up) exit 0 ;;
  login)   cat >/dev/null 2>&1; exit 0 ;;
  version) printf 'Docker Compose version v2.39.4\n'; exit 0 ;;
  logs)    printf 'hc-market-catalog  | Caused by: org.postgresql.util.PSQLException: Connection refused\n'; exit 0 ;;
  exec)
    case "$argv" in
      *'/management/info'*)
        case "$argv" in
          *hc-market-payout*) printf '{"brokerage":{"termsInForce":true,"commissionRate":"0.12"}}\n'; exit 0 ;;
          *) printf '{"build":{"version":"%s"}}\n' "${HC_FAKE_TAG:-0.0.0}"; exit 0 ;;
        esac ;;
    esac
    # ONE STATUS, TWO SENTENCES, AND THE SENTENCE IS UNOBSERVABLE HERE — measured by D78 §1: `exec`
    # exits 1 for a port that refuses, a service that is not running, a service the file does not
    # declare AND a daemon that cannot be asked. The gate discards both streams, so nothing in part 7
    # can read either line; it is written correctly per state anyway, because a stub that models a
    # dead daemon with a port refusal is a stub whose next reader believes the wrong thing.
    # `refused` IS READY HERE, DELIBERATELY, and narrowing it to the `pull` alone is what makes its
    # control land on the arm it is aimed at. With this answering unready too, a driver that walked
    # past the refused roll died at the gate's own refusal instead — red, and through a door that says
    # "neither died nor claimed success" rather than the one naming a revert reported as done. The
    # harm being reproduced is a SUCCESS line for a rollback that never rolled.
    case "${HC_FAKE_DOCKER:-ready}" in
      ready|refused)          exit 0 ;;
      daemon-gone-after-roll) printf '%s\n' "$gone" >&2; exit 1 ;;
      *)                      printf 'bash: line 1: /dev/tcp/localhost/8080: Connection refused\n' >&2; exit 1 ;;
    esac ;;
  ps)
    case "$argv" in
      *data-compose.yml*)
        for s in gateway catalog booking messaging payout; do printf 'hc-market-%s-db running\n' "$s"; done
        exit 0 ;;
    esac
    case "${HC_FAKE_DOCKER:-ready}" in
      daemon-gone-after-roll) printf '%s\n' "$gone" >&2; exit 1 ;;
    esac
    fmt=""
    for a in "$@"; do case "$a" in *'{{.'*) fmt="$a" ;; esac; done
    for s in gateway catalog booking messaging payout; do
      out=""
      for tok in $fmt; do
        case "$tok" in
          '{{.Service}}') out+="hc-market-$s " ;;
          '{{.State}}')   out+="running " ;;
          '{{.Health}}')  out+="unhealthy " ;;
          *)              out+="$tok " ;;
        esac
      done
      printf '%s\n' "${out% }"
    done
    exit 0 ;;
esac
exit 0
S7
printf '#!/usr/bin/env bash\nprintf 7\n' > "$B7/curl"
# NOT A REPO, deliberately: preflight's git probe then takes its warning arm, so the run is the same
# whatever tree this check is invoked from — and the warning is one of part 7's positive controls
# that the stub was reached at all.
printf '#!/usr/bin/env bash\nexit 1\n' > "$B7/git"
printf '#!/usr/bin/env bash\nexit 0\n' > "$B7/sleep"
chmod +x "$B7"/*

# A HOST DIRECTORY PER SCENARIO. `remote_deploy` WRITES — .env.next, .env, .env.previous — through
# the shipped remote commands against a real filesystem, so two scenarios sharing one directory would
# hand the second whatever the first left. `.env` is seeded so the rotation has something to rotate
# and `rollback` has a previous tag to find; without it every scenario ends at "no previous
# deployment recorded" and the rollback-phase gate is never reached at all.
seed_host7() {
  mkdir -p "$1"
  { printf 'JWT_BASE64_SECRET=x\nHC_PRIVACY_PEPPER=x\nHC_GATEWAY_ADMIN_PASSWORD=x\n'
    printf 'HC_GATEWAY_MONGODB_URI=x\n'
    for s in CATALOG BOOKING MESSAGING PAYOUT; do printf 'HC_%s_DB_URL=x\nHC_%s_DB_PASSWORD=x\n' "$s" "$s"; done
  } > "$1/secrets.env"
  printf 'HC_TAG=1.3.9\n'  > "$1/.env"
  printf 'HC_TAG=1.3.9\n'  > "$1/.env.previous"
  printf 'services: {}\n' > "$1/data-compose.yml"
}

# 7a's staging tree. The subprocess cannot be handed COMPOSE_TEMPLATE through the environment — it is
# derived from the script's own directory — so the copy sits in a directory shaped like deploy/. The
# copy is `cmp`d against the subject, which is how a reader can tell this executed the bytes under
# test and not something that drifted from them.
mkdir -p "$X7/stage/deploy/docker"
cp "$SCRIPT_ABS" "$X7/stage/deploy/deploy-prod.sh"; chmod +x "$X7/stage/deploy/deploy-prod.sh"
if [[ -f "$PROD_COMPOSE_ABS" ]]; then cp "$PROD_COMPOSE_ABS" "$X7/stage/deploy/docker/docker-compose.prod.yml"
else printf 'services: {}\n' > "$X7/stage/deploy/docker/docker-compose.prod.yml"; fi
cmp -s "$SCRIPT_ABS" "$X7/stage/deploy/deploy-prod.sh" \
  || err "the copy of $SCRIPT part 7 executes is not byte-identical to $SCRIPT, so nothing it establishes is about the shipped script. See decisions.md D80."

# --host is a name that does not resolve and HC_PUBLIC_URL is an unroutable address, on purpose: if a
# stub is ever missed, the real binary fails fast against nothing rather than reaching a host. The
# registry token is a literal that is not a credential — this repository is public.
#
# THE TWO FALLBACKS ARE NOT EQUALLY HARD, and the weaker one is `--host` (D80's review, note 4).
# `http://127.0.0.1:1` cannot route anywhere by construction; `ci-probe-host` relies on the resolver
# saying no, so on a network with a wildcard DNS answer a missed `ssh` stub would attempt a live
# connect to somebody. Re-measured here with the `ssh` stub made non-executable: PATH falls through
# to the real binary, the run fails fast, and part 7 goes red with **six** named errors — the first
# being "asked the host NOTHING", because nothing reached the log. Fail-closed on this workstation,
# where the name does not resolve. Do not make either value a real name to "test something".
env7=(HC_FAKE_TAG="$T7" HC_REGISTRY_TOKEN=probe-not-a-credential HC_PUBLIC_URL="http://127.0.0.1:1/probe")
run7_exec() { # run7_exec <site log> <FAKE_DOCKER> — EXECUTES the shipped file
  : > "$1"
  timeout 60 env -i PATH="$B7:/usr/bin:/bin" HC_SITE_LOG="$1" HC_FAKE_DOCKER="$2" "${env7[@]}" \
    bash "$X7/stage/deploy/deploy-prod.sh" \
    --tag "$T7" --host "$P_HOST" --path "${1%/*}/host-${1##*/}" --yes 2>&1 || true
}
# A CHILD PROCESS, NOT A SUBSHELL, AND THAT IS THE WHOLE OF D80's REVIEW FINDING 1. This was
# `( source …; eval … ) 2>&1 || true`, and **a compound whose status is TESTED disregards errexit and
# the ERR trap for everything inside it** — including the trap the sourced subject installs at its own
# line 235. Measured here, three states of the same subject (a function whose first command fails):
#
#     ( source …; work )            → TRAP fired, rc=1
#     ( source …; work ) || true    → "CONTINUED PAST THE FAILURE", rc=0     ← what this shipped
#     bash -c 'source …; work' || true → TRAP fired, rc=0                    ← what it does now
#
# So the sourced driver used to let the shipped script walk past a failed command that the EXECUTED
# program dies on: with an ssh refusing the rollback's roll, review watched it continue into the gate
# and print `✓ rolled back to 1.3.9` for a rollback whose roll never happened. Nothing asserted was
# wrong — every scenario's failure path is an explicit `die` or `warn` — but two texts said the trap
# was standing behind them, and the next scenario author would have believed them. A child gets fresh
# errexit semantics whatever the parent does with its status, which is the shape `run7_exec` already
# had. Scenario `refused` below is the permanent assertion that the trap fires here.
#
# EVERYTHING GOES THROUGH THE ENVIRONMENT so the body can be single-quoted: no expansion of this
# check's variables happens inside the child, and nothing in a scenario body may contain a single
# quote.
run7_source() { # run7_source <site log> <FAKE_DOCKER> <what to run after the source returns>
  : > "$1"
  timeout 60 env -i PATH="$B7:/usr/bin:/bin" HC_SITE_LOG="$1" HC_FAKE_DOCKER="$2" "${env7[@]}" \
    HC_P7_SOURCE="$SCRIPT_ABS" HC_P7_TEMPLATE="$PROD_COMPOSE_ABS" HC_P7_TAG="$T7" \
    HC_P7_HOST="$P_HOST" HC_P7_PATH="${1%/*}/host-${1##*/}" HC_P7_BODY="$3" \
    bash -c '
      # THE SOURCE ITSELF IS UNDER TEST. Nothing may be called here: the router lives in `main` and
      # `main` is called only when the file is EXECUTED, so a source that deploys is a defect, and the
      # marker below is what says the source returned rather than exited in the middle of one.
      source "$HC_P7_SOURCE" --tag "$HC_P7_TAG" --host "$HC_P7_HOST" --path "$HC_P7_PATH" --yes
      printf "__hc_source_returned__\n"
      # Three overrides, and each is the harness s rather than the script s: the compose template moves
      # because a mutant copy lives in a temp directory with no docker/ beside it, the budget because 24
      # polls at ten seconds is not a thing a check can wait for, and the service list because two
      # services make the exhaustion s own table readable in a failure message.
      COMPOSE_TEMPLATE="$HC_P7_TEMPLATE"
      HEALTH_TIMEOUT=10
      SERVICES=(catalog booking)
      eval "$HC_P7_BODY"
    ' 2>&1 || true
}
plain7() { printf '%s\n' "$1" | sed -e "s/$(printf '\033')\[[0-9;]*m//g"; }

for h in s0 s1 s2 s3 s4 s5 opts; do seed_host7 "$X7/host-$h"; done
r7_inert="$(run7_source "$X7/s0" ready 'declare -F main >/dev/null && printf "HAS_MAIN\n"')"
r7_opts="$(run7_source "$X7/opts" ready '{ printf "SSHOPTS"; for a in "${SSH_OPTS[@]}"; do printf "\037%s" "$a"; done; printf "\n"; }')"
r7_deploy="$(run7_exec "$X7/s1" ready)"
r7_gate="$(plain7 "$(run7_source "$X7/s2" unready main)")"
r7_rb="$(plain7 "$(run7_source "$X7/s3" daemon-gone-after-roll rollback)")"
r7_daemon="$(plain7 "$(run7_source "$X7/s4" daemon-gone-after-roll main)")"
r7_refused="$(plain7 "$(run7_source "$X7/s5" refused rollback)")"

# ---- 7.1 sourcing is inert, and `main` is there to be called -------------------------------------
# THREE STATES, IN THIS ORDER, AND THE ORDER IS A REVIEW FINDING OF ITS OWN. Written as one case on
# the marker first, a source that DEPLOYED and then died inside preflight reached the "no `main` to
# call" arm — true (it never returned) and the wrong cause named, which is this whole family's defect
# arriving inside its own repair. Found by running the mutation. A source that printed ANYTHING the
# program prints is a source that ran the program, whatever it went on to do.
#
# "PRINTED ANYTHING" MEANS BEFORE THE MARKER, and taking the whole transcript instead was a defect of
# exactly the kind this arm exists to catch. It was `grep -v` over the two marker lines, which was
# sound only while nothing after the source could print: once the driver became a child process the
# subject's ERR trap became live there too, so the probe body's own failure (case 47, `main` renamed)
# printed a trap line and this arm reported *"a sourced deploy script must not deploy"* about a source
# that deployed nothing. Splitting at the marker is what makes the sentence true — and when the marker
# never arrives, everything is "before" it, which is the source-died-mid-deploy case.
sites_at_source="$(wc -l < "$X7/s0" | tr -d ' ')"
said_at_source="$(printf '%s\n' "$r7_inert" | awk '$0=="__hc_source_returned__"{exit} {print}' | tr -d '[:space:]')"
if (( sites_at_source > 0 )) || [[ -n "$said_at_source" ]]; then
  err "sourcing $SCRIPT asked the host $sites_at_source thing(s) and printed '$(one "$r7_inert")' before anything called \`main\`. A sourced deploy script must not deploy: \`. ./deploy-prod.sh\`, typed to read one of its functions, would BE a deployment with \`confirm\` the only thing in its way. The router belongs in \`main\`, called under \`[[ \"\${BASH_SOURCE[0]}\" == \"\$0\" ]]\` — and without that, part 7 cannot drive the router at all. See decisions.md D80 and backlog NEW-42."
elif [[ "$r7_inert" != *"__hc_source_returned__"* ]]; then
  err "sourcing $SCRIPT did not return to the sourcing shell, and printed nothing on the way: '$(one "$r7_inert")'. Something above the router refuses at load time — an argument this check passes, or a guard that reads the environment — so part 7 has nothing to call. See decisions.md D80."
elif [[ "$r7_inert" != *HAS_MAIN* ]]; then
  err "$SCRIPT sources cleanly and declares no \`main\` for part 7 to call: '$(one "$r7_inert")'. The router must be a function named \`main\`, and this is an ERROR rather than a skip for the reason cases 11, 19, 20 and 29 exist — a subject this harness cannot find would otherwise be a green run over nothing. See decisions.md D80."
else
  ok "sourcing $SCRIPT runs no ssh, no scp and no docker, and leaves a \`main\` to call — the router runs only when the file is executed"
fi

# ---- 7.2 the whole program, executed: every stub reached, and `main` actually invoked -------------
sites_deploy="$(wc -l < "$X7/s1" | tr -d ' ')"
printf '  executed, %s remote invocation(s): %s\n' "$sites_deploy" "$(one "$(printf '%s\n' "$r7_deploy" | tail -2)")"
if (( sites_deploy == 0 )); then
  err "executing $SCRIPT with a tag, a host and --yes asked the host NOTHING and printed '$(one "$r7_deploy")'. The likeliest cause is that nothing calls \`main\`: the file then parses, defines every function and exits 0 having deployed nothing, which is the quietest failure this script can have and the one thing no text matcher and no sourced probe can see. See decisions.md D80."
else
  # THE POSITIVE CONTROLS, one per stub, and each is a fact the run produced rather than a line it
  # printed for its own reasons: without them every per-site assertion below is satisfied by a
  # program that stopped early, and a stub nobody called looks exactly like an assertion that passed.
  for control in \
    "the git probe:no git repository under" \
    "the compose upload (scp):uploading compose stack and env" \
    "the data tier probe (docker ps):data tier up — 5 stores running" \
    "the health gate's polls:all services report READY" \
    "the catalogue smoke test (curl):catalogue answering — 7 published professionals" \
    "payout's brokerage probe:payout holds brokerage terms in force — 0.12 commission" \
    "the gateway version probe:gateway container reports version $T7" \
    "the whole deployment:HealthConnect $T7 live on $P_HOST"; do
    what="${control%%:*}"; needle="${control#*:}"
    case "$r7_deploy" in
      *"$needle"*) ok "executed end to end — $what ran" ;;
      *) err "executing $SCRIPT never got as far as $what (nothing matching '$needle' in its output). Every per-site assertion in part 7 is read off a run that reached the end, so a run that stopped early would satisfy them by never asking. Output: '$(one "$r7_deploy")'. See decisions.md D80." ;;
    esac
  done
  case "$(cat "$X7/s1")" in
    *SCP*) ok "executed end to end — the compose file went through scp, which carries SSH_OPTS too" ;;
    *) err "executing $SCRIPT logged no scp invocation at all, so the one non-ssh remote site in the file was never driven. See decisions.md D80." ;;
  esac
fi

# ---- 7.3 the phase each caller passes, established from the sentences the phase composes ----------
#
# THE BLOCKING FINDING OF D78 §15, now driven. `health_gate <phase>` composes `$left` and `$rolling`
# once per phase and interpolates them into every refusal, so the phase a caller passed is readable
# from the transcript — and the transcript is split at `step "Rollback"`, which is what attributes a
# sentence to the CALLER that produced it rather than merely to the arm. Before this, swapping the two
# arguments parsed and left the check and its test at exit 0.
rb_line="$(printf '%s\n' "$r7_gate" | grep -c '^Rollback$' || true)"
before7="$(printf '%s\n' "$r7_gate" | awk '$0=="Rollback"{exit} {print}')"
after7="$(printf '%s\n' "$r7_gate" | awk 'f{print} $0=="Rollback"{f=1}')"
printf '  deploy gate said:    %s\n' "$(one "$(printf '%s\n' "$before7" | grep 'still unhealthy' || printf '«nothing»')")"
printf '  rollback gate said:  %s\n' "$(one "$(printf '%s\n' "$after7" | grep 'still unhealthy' || printf '«nothing»')")"
if (( rb_line != 1 )); then
  err "an exhausted deploy gate against a host that answered did not reach \`rollback\` exactly once ($rb_line): '$(one "$r7_gate")'. That is the one arm that may return, and the router's \`rollback\` is where the second phase is passed — with it unreached, 7.3 establishes nothing about either caller. See decisions.md D78 and D80."
elif [[ "$before7" != *"Health gate"* || "$after7" != *"Health gate"* ]]; then
  err "the gate did not run on both sides of the rollback: '$(one "$r7_gate")'. `rollback` re-runs it to check its own work, and that second run is the caller whose phase nothing was looking at. See decisions.md D80."
else
  case "$before7" in
    *"nothing further to revert to"*)
      err "$SCRIPT's DEPLOY router runs the health gate in the ROLLBACK phase: the gate that fired BEFORE any rollback said '$(one "$(printf '%s\n' "$before7" | grep 'still unhealthy')")'. Nothing has been reverted at that point and \`rollback\` is about to run, so the operator is told a revert has already happened and that none is available. See decisions.md D78 §14, D80." ;;
    *"rolling back."*) ok "the deploy router passes \`deploy\`, so the refusal before any revert says a rollback is what happens next" ;;
    *) err "the gate reached from the deploy router said neither of the two things a phase composes: '$(one "$before7")'. See decisions.md D80." ;;
  esac
  case "$after7" in
    *"rolling back."*)
      err "$SCRIPT's rollback() runs the health gate in the DEPLOY phase: the gate that fired AFTER the revert said '$(one "$(printf '%s\n' "$after7" | grep 'still unhealthy')")'. By then .env is restored and the stack is rolled, its caller refuses on the next line, and no second revert happens — so that sentence describes something nobody is going to do, and its sibling refusals offer \`--rollback\` after the rollback has just run. This is the swap that parsed and left both this check and its test at exit 0. See decisions.md D78 §14, §15 and D80." ;;
    *"nothing further to revert to"*) ok "rollback() passes \`rollback\`, so the gate checking its own work offers no second revert" ;;
    *) err "the gate reached from rollback() said neither of the two things a phase composes: '$(one "$after7")'. See decisions.md D80." ;;
  esac
fi
# AND THE OTHER ARM, IN BOTH PHASES — the pairs D78 §15 removed the contradiction from rather than
# testing, because it could not reach them. A daemon that stops answering between the roll and the
# gate is a refusal, not a rollback, so this arm must also revert nothing when it is the deploy that
# reached it: `rollback` is never entered at all.
printf '  deploy, daemon gone:   %s\n' "$(one "$(printf '%s\n' "$r7_daemon" | tail -1)")"
case "$r7_daemon" in
  *"ALREADY BEEN APPLIED"*)
    err "a DEPLOY whose gate could not ask the daemon at the timeout was told the rollback had already been applied: '$(one "$r7_daemon")'. Nothing had been reverted. See decisions.md D78 §14 and D80." ;;
  *"NOTHING HAS BEEN ROLLED BACK"*)
    case "$r7_daemon" in
      *$'\n'Rollback$'\n'*) err "a deploy whose gate could not ask the daemon reverted the stack anyway: '$(one "$r7_daemon")'. That refusal states that nothing has been rolled back, and a rollback needs the same host that could not be asked. See decisions.md D78." ;;
      *) ok "a deploy gate that cannot ask the daemon refuses, says nothing was reverted, and reverts nothing" ;;
    esac ;;
  *) err "a deploy whose gate could not ask the daemon at the timeout said neither of the two things a phase composes: '$(one "$r7_daemon")'. See decisions.md D80." ;;
esac
# THE SOURCED DRIVER MUST NOT SUPPRESS THE SUBJECT'S OWN FAILURE SEMANTICS — D80's review finding 1,
# and this assertion is what stands behind the argument written at the docker stub. `rollback`'s roll
# is refused, so the shipped `run ssh …` returns non-zero and `deploy-prod.sh`'s ERR trap must fire.
# Measured before the repair: the driver walked past it, ran the health gate and printed
# `✓ rolled back to 1.3.9` for a rollback whose roll never happened. A scenario whose failures are
# left to the trap is only sound while this is green — and every scenario should assert its own
# failures anyway.
printf '  rollback, roll refused: %s\n' "$(one "$(printf '%s\n' "$r7_refused" | tail -1)")"
case "$r7_refused" in
  *"rolled back to"*)
    err "a \`rollback\` whose roll the host REFUSED reported success: '$(one "$r7_refused")'. $SCRIPT installs an ERR trap and the failing command is a bare \`run ssh …\`, so the program must die there — the executed program does. A driver that tests its own compound's status disregards errexit and the trap for everything inside it, including a subject it sourced, so a sourced scenario would walk on into the health gate and report a revert that never happened. Use a child process (\`bash -c 'source …; main'\`), never \`( source …; … ) || true\`. See decisions.md D80 §2 and its review." ;;
  *"failed at line"*)
    ok "a refused roll dies at the subject's own ERR trap in the sourced driver too, so a scenario cannot walk past a failure the executed program stops on" ;;
  *) err "a \`rollback\` whose roll the host refused neither died at the ERR trap nor claimed success: '$(one "$r7_refused")'. This scenario exists to establish that the sourced driver preserves the subject's failure semantics; if the roll no longer goes through \`run ssh\`, or the trap has moved, say so here. See decisions.md D80." ;;
esac
printf '  rollback, daemon gone: %s\n' "$(one "$(printf '%s\n' "$r7_rb" | tail -1)")"
case "$r7_rb" in
  *"NOTHING HAS BEEN ROLLED BACK"*)
    err "\`rollback\` reached its own gate, could not ask the daemon, and reported that NOTHING HAS BEEN ROLLED BACK: '$(one "$r7_rb")'. It had just restored .env and rolled the stack, and the remedy that refusal prints is the command that has this moment run. See decisions.md D78 §14 and D80." ;;
  *"ALREADY BEEN APPLIED"*) ok "\`rollback\`'s own gate, unable to ask the daemon, says the revert has already been applied and offers no second one" ;;
  *) err "\`rollback\`'s own gate, unable to ask the daemon, said neither of the two things a phase composes: '$(one "$r7_rb")'. See decisions.md D80." ;;
esac

# ---- 7.4 per-site: which options each ssh and the scp actually received ---------------------------
#
# THE FLOOR IN THE PREAMBLE COUNTS LINES AND ATTRIBUTES NOTHING (D78 §15's first note): removing the
# expansion from any eleven of the twelve leaves it green. This asks each invocation what it was
# handed, and the expected list is the array the RUNNING script holds — so an option added to
# SSH_OPTS and not reaching a site is red as well as one removed from a site.
# `sed -n s///p` AND NOT `${r7_opts#*SSHOPTS}`: with the array renamed out from under the sourced
# probe the parameter form hands back the WHOLE transcript, which is non-empty, and every site then
# fails to contain it — red, but through a door that says nothing. Absent must read as absent.
opts7="$(printf '%s\n' "$r7_opts" | sed -n 's/^SSHOPTS//p' | head -1)"
if [[ -z "$opts7" ]]; then
  err "part 7 could not read SSH_OPTS out of a sourced $SCRIPT ('$(one "$r7_opts")'), so every per-site assertion below would have compared each invocation against an empty option list and passed. See decisions.md D80."
else
  printf '  SSH_OPTS at run time: %s\n' "$(printf '%s' "$opts7" | tr '\037' ' ')"
  # ONE ENTRY PER REMOTE INVOCATION THIS SCRIPT MAKES, and unmatched in EITHER direction is an error:
  # a site that stops being asked is a probe removed under this check's feet, and an invocation no
  # entry recognises is a seventh probe growing beside the six — which is exactly what parts 1-4
  # cannot see. Ordered, first match wins: the rollback's roll carries `pull` too, and the data
  # tier's `ps` carries `--format` too.
  #
  # WHAT THIS COVERS IS *EXECUTED* INVOCATIONS, AND THE BOUNDARY IS WORTH WRITING DOWN (D80's review,
  # note 3). The four scenarios reach the default deploy path, so an `ssh` added inside
  # `build_and_push`, `build_local_only`, `resolve_tag`'s Maven branch or `confirm` would be invisible
  # here — those need `--build`, an unset `--tag` or an interactive answer — *and* invisible to parts
  # 1-4, which drive named functions. Only the preamble's near-vacuous line-count floor would see it
  # at all. Driving them means stubbing `mvnw` per service and answering a prompt; if a probe ever
  # goes there, that is the work, rather than adding a needle nothing reaches.
  sites7=(
    "the compose file upload (scp):SCP"
    "the ssh and compose-v2 gate:docker compose version"
    "secrets.env being there at all:test -s"
    "each of the twelve values in secrets.env:grep -qE"
    "the three host networks:docker network inspect"
    "the five stores in the data tier:data-compose.yml"
    "the remote directory:mkdir -p"
    "the generated .env:cat > "
    "the .env rotation:mv .env.next .env"
    "the rollback's own roll:cp .env.previous .env"
    "the host's registry login:docker login"
    "the image pull:pull hc-market-"
    "the roll:up -d --remove-orphans"
    "the health gate's own poll:/management/health/readiness"
    "payout's brokerage probe:hc-market-payout bash -c"
    "the gateway's version probe:/management/info"
    "the exhaustion's state probe:ps -a --format"
    "the failed services' logs:logs --no-color --tail"
    "the previous tag a rollback needs:grep -m1 '^HC_TAG="
    "the deployments.log append:deployments.log"
  )
  declare -A seen7=() bare7=()
  unknown7=0
  while IFS= read -r inv; do
    [[ -n "$inv" ]] || continue
    label7=""
    for site in "${sites7[@]}"; do
      case "$inv" in *"${site#*:}"*) label7="${site%%:*}"; break ;; esac
    done
    if [[ -z "$label7" ]]; then
      unknown7=$(( unknown7 + 1 ))
      err "$SCRIPT made a remote invocation part 7 does not recognise: '$(printf '%s' "${inv:0:220}" | tr '\037' ' ')'. Every site is enumerated here so that a SEVENTH probe cannot grow beside the six without this check being told — parts 1 to 4 drive named functions and cannot see one. See decisions.md D75 and D80."
      continue
    fi
    seen7[$label7]=1
    case "$inv" in *"$opts7"*) : ;; *) bare7[$label7]=1 ;; esac
  done < <(cat "$X7/s1" "$X7/s2" "$X7/s3" "$X7/s4")
  missing7=""
  for site in "${sites7[@]}"; do [[ -n "${seen7[${site%%:*}]:-}" ]] || missing7+="; ${site%%:*}"; done
  if [[ -n "$missing7" ]]; then
    err "$SCRIPT no longer asks the host${missing7//;/,} — four scenarios were run (a whole deploy, an exhausted gate that rolled back, a rollback on its own, and a gate that could not ask the daemon) and nothing reached those sites. Either a probe was renamed or removed, or the scenarios stopped reaching it; both leave part 7 asserting the options of an invocation that is not there. See decisions.md D80."
  fi
  bad7=""
  for label7 in "${!bare7[@]}"; do bad7+="; $label7"; done
  if [[ -n "$bad7" ]]; then
    err "$SCRIPT hands these remote invocations something other than the whole of SSH_OPTS, in order${bad7//;/,}. The array is '$(printf '%s' "$opts7" | tr '\037' ' ')'. An ssh with no ConnectTimeout waits 136s per attempt against a host that drops packets (measured) and the health gate makes 24 × one-per-service of them; one with no BatchMode can stop halfway waiting for a passphrase. The preamble's count cannot see this: removing the expansion from any eleven of the twelve leaves it at two and green. See decisions.md D78 §7, §15 and D80."
  elif (( unknown7 == 0 )); then
    ok "all ${#seen7[@]} remote invocation sites received the whole of SSH_OPTS, in order — asked of the arguments each site was handed, per site"
  fi
fi

printf '\n'
if (( fail )); then printf 'host probe attribution: FAILED\n'; else printf 'host probe attribution: ok\n'; fi
exit $fail
