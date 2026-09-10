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
#  THREE PARTS.
#
#  1. host_run's TWO HOPS, told apart by a sentinel and never by a status. This is the part the
#     taxonomy rests on. MEASURED, OpenSSH 10.2p1: ssh exits 255 when it cannot connect and
#     otherwise exits with the REMOTE command's status — so `exit 255` on the far side is
#     indistinguishable from ssh never arriving, and case 4 below is that exact state. The remote
#     command therefore announces its own status on its own line, and the presence of that line is
#     what establishes a shell ran anything at all.
#
#  2. THE NETWORK ARM, lifted out of preflight and driven through the same stub. Five causes, each
#     asserted by the words it names, plus the positive control.
#
#  3. THE OTHER FOUR CALL SITES still go through host_run. A textual part, deliberately: a call site
#     that stops routing through it goes back to folding, and part 1 cannot see a site it is not
#     driving. Comments are stripped first with the SHELL stripper — this subject is a shell script
#     and the Java one removes nothing from it while exiting 0.
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
# missing-stripper branch fire. Absent it, part 3 would read empty text and report every call site
# as routed — the fail-open D62's review found in two checks at once.
STRIP_SH="${HC_STRIP_SH:-$ROOT/.github/checks/strip-sh-comments.awk}"

P_NET="hc-ci-probe-net"
P_HOST="ci-probe-host"

fail=0
err() { printf '::error::%s\n' "$*"; fail=1; }
ok()  { printf '  ok   %s\n' "$*"; }

[[ -f "$SCRIPT" ]] || { err "$SCRIPT does not exist, so nothing was established about how a remote probe attributes a failure. See decisions.md D75."; printf '\nhost probe attribution: FAILED\n'; exit 1; }

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
# host_run reads two values declared at the top level beside it, and they are lifted as shipped
# bytes rather than restated here. A restated sentinel is a second definition of the protocol and
# would keep every reading below green while the script's own copy was emptied — which is the shape
# of the fail-open this repository keeps finding. `set -u` is on inside the probe, so an unlifted
# sentinel is loud; the guard is here so it is loud with a reason.
LIFT_GLOBALS="$(grep -E '^(HOST_SENTINEL|SSH_OPTS)=' "$SCRIPT" || true)"
# THE VALUE, NOT THE NAME. `HOST_SENTINEL=""` satisfies a grep for the assignment and satisfies
# `grep -F "$HOST_SENTINEL "` against any line with a space in it — so the function still refuses,
# and refuses naming NEITHER hop. Measured: every ssh state then answers "answered with a status
# this script cannot read". Fails closed and diagnoses nothing, which is precisely the shape this
# check exists to catch, so the guard reads the value.
SENTINEL_VALUE="$(printf '%s\n' "$LIFT_GLOBALS" | sed -n 's|^HOST_SENTINEL=||p' | tr -d "\"'" | head -1)"
[[ -n "$SENTINEL_VALUE" ]] \
  || err "$SCRIPT declares no non-empty HOST_SENTINEL at the top level. That value IS the mechanism that tells ssh's own failure apart from the remote command's — ssh exits 255 for both — so without it there is nothing here to check, and host_run refuses every probe while naming neither hop. See decisions.md D75."
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
    HOST="$P_HOST"; REMOTE_PATH="/srv/healthconnect"; DRY_RUN=0
    DATA_COMPOSE_FILE="data-compose.yml"; SECRETS_FILE="secrets.env"; DATA_STORE_COUNT=5
    HC_NETWORK="$P_NET"; HC_DATA_NETWORK="${HC_DATA_NET_OVERRIDE:-$P_NET-data}"
    HC_MONITORING_NETWORK="$P_NET-mon"
    ok() { printf 'OK %s\n' "$*"; }
    log() { :; }; warn() { :; }
    skipped() { printf 'SKIPPED %s\n' "$*"; }
    die() { printf 'DIE %s\n' "$*"; exit 3; }
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
    eval "$LIFT_SSH_HINT"
    eval "$LIFT_NO_DOCKER"
    eval "$LIFT_HOST_RUN"
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
    DIE*"ssh did not reach a shell"*)
      case "$got" in
        *"$want"*) ok "host_run names ssh, and its remedy, when $what" ;;
        *) err "$SCRIPT's host_run refused for the right cause but the wrong remedy when $what: '$(one "$got")' does not mention '$want'. ssh's own words are the only evidence available for that hop, and they are produced by the LOCAL client, so keying on them is a measurement rather than a guess. See decisions.md D75." ;;
      esac ;;
    DIE*)
      err "$SCRIPT's host_run refused when $what, but did not say that ssh never reached a shell: '$(one "$got")'. Nothing on the host was asked, so a message about the host's networks, files or containers is a claim about something never looked at — which is backlog NEW-33 exactly. See decisions.md D75." ;;
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
  DIE*"ssh did not reach a shell"*) ok "an unreachable host is reported as an unreachable host, in the network loop too" ;;
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

# ---- 3. Every remote probe in the file goes through host_run --------------------------------------
#
# Part 1 drives host_run and part 2 drives the network arm; neither can see a SIXTH ssh growing back
# beside them, and every site that stops routing through host_run goes straight back to folding an
# ssh failure into a claim about the host. So this is textual, and the count is the assertion: the
# only bare `ssh` invocations left in the file are the ones in the deploy, health-gate, smoke-test
# and rollback-write phases, which are either `run`-wrapped (the command is printed), ERR-trapped
# (the command is printed), or polls — D71 §5's rule, `a die may not fold; a warn and a poll may`.
printf '\n%s: the call sites\n' "$SCRIPT"
if [[ ! -f "$STRIP_SH" ]]; then
  err "$STRIP_SH is missing, so part 3 could not strip comments and did not run. A check that reads nothing is not a check that found nothing."
else
  # BACKSLASH-CONTINUED LINES ARE JOINED FIRST, and that is a bug fix rather than a flourish: four
  # of the six calls below are written across two lines, so a line-at-a-time grep for the remote
  # command finds the CONTINUATION and reports a perfectly correct call site as unrouted. Watched
  # doing exactly that. Same treatment build.yml's brokerage probe needed for the same reason.
  stripped="$(awk -f "$STRIP_SH" "$SCRIPT" | sed -e ':a' -e '/\\$/N; s/\\\n//; ta')"
  # The comments in this file quote the OLD folded lines verbatim, so an unstripped count would be
  # counting a paragraph. Measured on a clean tree: 6 raw, 0 stripped, for `-o BatchMode`.
  batch="$(printf '%s\n' "$stripped" | { grep -c 'ssh -o BatchMode=yes' || true; })"
  (( batch == 0 )) \
    && ok "no probe builds its own ssh options — SSH_OPTS is the one place BatchMode and the timeout are set" \
    || err "$SCRIPT has $batch ssh invocation(s) spelling out their own '-o BatchMode=yes' rather than going through host_run and SSH_OPTS. Each is a probe whose failure cannot be attributed to a hop, and an ssh with no ConnectTimeout is a refusal that arrives minutes late or never. See decisions.md D75."
  # Every site preflight asks about, by the question it asks. Enumerated, and that is acceptable
  # only because a site that is missing is an ERROR here rather than a skip: the whole point is that
  # a probe stopping short of host_run is invisible to parts 1 and 2.
  sites=(
    "docker compose version:the ssh and compose-v2 gate"
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
fi

printf '\n'
if (( fail )); then printf 'host probe attribution: FAILED\n'; else printf 'host probe attribution: ok\n'; fi
exit $fail
