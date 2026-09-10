#!/usr/bin/env bash
# ==============================================================================
#  A stack must ADDRESS the shared plane it PREFLIGHT-CHECKS — decisions.md D66, backlog NEW-25.
#
#  `HC_SHARED_NETWORK`, `HC_SHARED_CONSUL` and `HC_SHARED_KAFKA` name the one broker and the one
#  Consul four products borrow (D27). Each has two halves and both must move together:
#
#      the SCRIPT half     what preflight inspects, execs and reports
#      the COMPOSE half    the network the stack joins, and the two addresses every service is given
#
#  Until D66 `quality/startup.sh` read all three and `quality/compose.yml` hardcoded all three, so an
#  override moved only the first. That is worse than a variable that does nothing: preflight went
#  green against the plane you named while the stack came up on the default one, which reads as the
#  override not being implemented rather than as it being half-implemented.
#
#  FIVE PARTS, and the first two are asked of BOTH pairs — quality and dev — because dev is the shape
#  quality was fixed to copy and a regression there would take the model with it. It read FOUR from
#  D69, which added the fifth: a header's count of its own sections is the smallest instance of the
#  thing D71 is about, so trust the numbered list below and not this sentence.
#
#  1. INTERPOLATION, RENDERED, NEVER GREPPED. `docker compose config` is asked a second time with
#     each variable set, and the assertion is that the rendered value MOVED. D64's own check began as
#     a grep for `name: ${HC_OTEL_NETWORK:-…}` and failed open the moment the line was commented out
#     — the grep matched the comment while compose rendered the network under its own key. A comment
#     cannot satisfy a render.
#
#  2. ONE DEFAULT, WRITTEN TWICE, ASSERTED EQUAL. The script and the compose file each carry a
#     `:-default`, which is two defaults for one value and is exactly how they eventually disagree.
#     The script side is a `^`-anchored assignment: a `#` comment cannot match a pattern anchored to
#     column 0, so no stripper is needed for this one and adding one would only widen it.
#
#  3. THE SCRIPT EXPORTS WHAT IT RESOLVED, on every action including teardown, so a `--down` cannot
#     address a different plane from the `up` that made the containers. Asked BEHAVIOURALLY, by
#     running `env_for_compose` out of the file and reading the environment it leaves.
#
#     QUALITY ALONE, and D69 re-established the reason rather than inheriting it. `deploy-dev.sh`
#     has no `env_for_compose`: it exports the three at the top level, above the router, so every
#     action carries them. But the property that export buys quality is one dev already has by
#     construction — dev's SHARED_* are resolved from the environment and nowhere else, and every
#     way of putting a value there (`export`, a one-shot `HC_X=… ./deploy-dev.sh up`, or
#     `deploy/.env`, which is sourced under `set -a`) leaves it in the environment compose reads.
#     Delete dev's export line and compose still renders the same value. A check on a subject that
#     cannot fail teaches the wrong lesson about the checks beside it, so there is not one.
#
#  4. PREFLIGHT REFUSES A PLANE THE BROKER IS NOT ON. "Running" and "on the network this stack
#     joins" are different questions and nothing asked the second until D66: measured against a
#     throwaway empty network, the whole of `check_shared_plane` passed and its success line printed
#     "…on hc-market-d66-probe", asserting a membership it had never looked for. Harmless while
#     compose hardcoded `hcnet`; live the moment part 1 started moving the join. Asked behaviourally
#     with `docker` stubbed, including the positive control — a refusal that fires on everything is
#     not a check.
#
#     BOTH SCRIPTS since D69, and the pair is `script:function` rather than a script list because
#     THE FUNCTION NAMES DIFFER — `check_shared_plane` in quality, `shared_plane` in dev. That
#     difference is what made this part quality-only: an `awk` range with one name hard-coded lifts
#     nothing out of the other file, and lifting nothing would have passed. Reproduced against
#     deploy-dev.sh at d3291a5 before the fix, with a throwaway empty network and the real daemon:
#     the whole function passed and printed "…on hc-market-d69-probe" (backlog NEW-29).
#
#     FIVE STATES SINCE D71, and three of them are about WHICH CAUSE the refusal names rather than
#     about whether it refuses. Every failure here is fatal either way — what an operator is told
#     decides whether they go and restart a shared plane with nothing wrong with it, which is the
#     cost D66 §7 grew three separate messages to remove. So the stub can now be told to FAIL a
#     named docker read: the membership `inspect`, the `network inspect`, or neither. Until then it
#     answered everything successfully, so a folded `2>/dev/null | grep` and a status-checked
#     capture were indistinguishable by it — measured rather than read off the stub, by folding
#     D69's fix back out of `deploy-dev.sh` and watching this part stay green (backlog NEW-32).
#
#     The absent-network state is the POSITIVE CONTROL for the unanswerable-network one: docker
#     exits 1 for both and prints `[]` for both, so they are told apart only by its message, and a
#     fix collapsing them into "could not be asked" would pass every other assertion here.
#
#     REACH: the stub answers with a network list, so this catches the refusal being DELETED or
#     weakened and does not catch the Go template being wrong. That direction was measured by hand
#     instead (D66 §3, D69 §2): a template naming a field that does not exist yields nothing, the
#     `grep -Fxq` finds nothing, and preflight refuses — the direction a check about a silent failure
#     has to fail in. Both functions also meet the real daemon on every run of their own script.
#     What it also cannot reach is the daemon being unreachable for REAL: `DOCKER_HOST=unix:///
#     nonexistent`, which is how the pepper guard's docker probe is measured, dies at the network arm
#     and never reaches the membership one — measured on both copies (D71 §2). The stub is the only
#     instrument that can put a working daemon in front of one read and a broken one in front of the
#     next, which is the state a flake, a restart or a container removed mid-check produces.
#
#  5. THE EXACT SET OF ACTIONS THAT REACH THE PLANE CHECK. Part 4's refusal is FATAL, and that is
#     only safe while `down`, `status` and `logs` never call `preflight` — otherwise a broken plane
#     wedges the one command that takes the stack down and the one that would show you why. In
#     quality the equivalent is a router that exits before preflight and is pinned by D67's own test;
#     in dev the router is a `case` at the foot of the file calling `preflight` per branch, so the
#     assertion is about branches.
#
#     AN ALLOW-LIST, `{up, reseed, restart}`, compared as a SET. A per-branch deny-list was written
#     first and both of its holes were reproduced (D69 §10): deleting the `preflight` line under
#     `up)` left it green — an `up` that starts the estate with no plane check at all — and a new
#     `doctor) preflight; compose ps ;;` branch left it green too. It counts, it does not attribute:
#     the same limitation CLAUDE.md already records for the CRUD gate, recreated in a new check. A
#     missing branch, an unreadable router, no labels at all and nothing calling preflight anywhere
#     are each their own error, because every one of them would satisfy a deny-list by reading
#     nothing.
#
#     REACH: what is matched is a DIRECT textual call inside the branch. Indirection is unseen —
#     `plane_gate() { preflight; }` above the router with `plane_gate` in `down)` is green, measured
#     — and the exact-set formulation does not close it, so the ok line says "direct calls" rather
#     than "reaches". Comments are stripped with the SHELL stripper first: this subject is a shell
#     script, and the Java one removes nothing from it while exiting 0.
#
#  TWO LISTS ARE ENUMERATED HERE and neither is derived, which needs its justification stated rather
#  than assumed — this repository's enumerated lists have gone stale or failed open, and the zone-write
#  check is the precedent for saying why one is acceptable. `PLANE_FNS` names two `script:function`
#  pairs and `DEV_PREFLIGHT_BRANCHES` names three actions. Within the files they name, every failure
#  mode exits 1 loudly: a function that cannot be lifted, a router that cannot be read, a branch that
#  is missing, and any difference in the gated set. What neither reaches is a THIRD script growing a
#  plane preflight of its own — there is no derivation available (a shared plane is not declared in a
#  model file the way an entity or a `messageBroker` is), so that day the pair goes in here by hand.
#  Both lists are also the seam the test beside this file drives, which is what keeps them exercised.
#
#  Inputs are overridable so the test beside this file can construct each broken state and watch this
#  fail — a check nobody has seen fail is a check of nothing.
#
#      ./.github/checks/shared-plane-wiring.sh
#      HC_PAIRS="quality/compose.yml:quality/startup.sh" ./.github/checks/shared-plane-wiring.sh
#      HC_PLANE_FNS="quality/startup.sh:check_shared_plane" ./.github/checks/shared-plane-wiring.sh
# ==============================================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# compose-file:script pairs. Both are checked for parts 1 and 2; the quality script alone is checked
# for part 3, because it is the only one that resolves and then hands the values to compose itself —
# deploy-dev.sh exports them at the top level and has no env_for_compose to run. See the header for
# why that is a subject that cannot fail rather than a gap.
PAIRS="${HC_PAIRS:-quality/compose.yml:quality/startup.sh deploy/docker/docker-compose.dev.yml:deploy/deploy-dev.sh}"
STARTUP="${HC_STARTUP:-quality/startup.sh}"

# script:function pairs for part 4. The name differs between the two copies and that is exactly what
# kept dev unguarded until D69 — an `awk` range carrying quality's name lifts nothing out of
# deploy-dev.sh, and a part 4 that lifted nothing would have gone green. Each entry is checked for
# both halves below: an unliftable function is an error, never a skip.
PLANE_FNS="${HC_PLANE_FNS:-quality/startup.sh:check_shared_plane deploy/deploy-dev.sh:shared_plane}"

# Part 5's subject: the dev router, and the EXACT set of actions allowed to reach a fatal preflight.
# An allow-list rather than a list of teardowns, so an action nobody anticipated is red until it is
# argued — see part 5's own comment for the two mutations that made that necessary.
DEV_SCRIPT="${HC_DEV_SCRIPT:-deploy/deploy-dev.sh}"
DEV_PREFLIGHT_BRANCHES="${HC_DEV_PREFLIGHT_BRANCHES:-up reseed restart}"
# Overridable for one reason: so the test beside this file can point it at nothing and watch the
# missing-stripper branch fire. Four checks trust that one file, and absent it two of them once
# printed `ok` having read nothing (D62's review) — a guard nobody has watched is not a guard.
STRIP_SH="${HC_STRIP_SH:-$ROOT/.github/checks/strip-sh-comments.awk}"

# The compose key the shared plane is declared under, in both files. Enumerated rather than derived,
# and that is safe here only because an absent key is REFUSED below rather than skipped: both files
# also declare a second external network (`qualitynet`), so "the external one" derives nothing.
NET_KEY="${HC_NET_KEY:-hcnet}"

# Probe values, deliberately unlike any real name on this host or in hc-infra.
P_NET="hc-ci-probe-net"; P_CONSUL="hc-ci-probe-consul"; P_KAFKA="hc-ci-probe-kafka"

fail=0
err() { printf '::error::%s\n' "$*"; fail=1; }
ok()  { printf '  ok   %s\n' "$*"; }

# TWO NESTED `env`s, AND THE OUTER ONE IS A BUG FIX RATHER THAN A FLOURISH. `env VAR=… docker
# compose` inherits the caller's environment, so with any of the three already exported — which is
# exactly what CLAUDE.md tells people to do, and what the header above documents — the "unset"
# baseline below was rendering the ambient value and the check reported it as the compose file
# disagreeing with the script. Red on a correct tree, with the error naming the wrong cause.
# Reproduced with `HC_SHARED_KAFKA=my-other-kafka`.
#
# Nested rather than `env -u X X=v` in one argv: POSIX says options are processed before
# assignments, so the assignment does win — measured, and measured on **one** `env` (uutils 0.8.0
# here, GNU on the runner). A check that has to be right about that is a check resting on which
# coreutils is installed. The inner `env` never sees a name the outer one unset.
render() { # render <compose-file> [VAR=VAL ...]
  local f="$1"; shift
  env -u HC_SHARED_NETWORK -u HC_SHARED_CONSUL -u HC_SHARED_KAFKA \
    env "$@" JWT_BASE64_SECRET=ci-placeholder HC_PRIVACY_PEPPER=ci-placeholder TAG=ci \
      docker compose -f "$f" config --format json
}

# The one distinct value of an environment key across every service that carries it. Zero services
# and two disagreeing values are both refused, and they print DIFFERENTLY — an empty subject is how
# six earlier checks in this repository reported "clean" having read nothing, and reporting it as
# "more than one value" would describe the opposite of what happened.
one_of() { # one_of <json> <ENV_KEY>
  printf '%s' "$1" | jq -r --arg k "$2" \
    '[.services[].environment[$k] // empty] | unique
     | if length == 1 then .[0] elif length == 0 then "«none»" else "«\(length) values»" end'
}

for pair in $PAIRS; do
  compose="${pair%%:*}"; script="${pair#*:}"
  printf '\n%s + %s\n' "$compose" "$script"
  [[ -f "$compose" ]] || { err "$compose does not exist, so nothing about the shared plane was checked for this pair. See decisions.md D66."; continue; }
  [[ -f "$script" ]]  || { err "$script does not exist, so nothing about the shared plane was checked for this pair. See decisions.md D66."; continue; }

  unset_json="$(render "$compose")" || { err "$compose does not render at all, so this check established nothing about it."; continue; }

  r_net="$(printf '%s' "$unset_json" | jq -r --arg k "$NET_KEY" '.networks[$k].name // ""')"
  r_kafka="$(one_of "$unset_json" SPRING_KAFKA_BOOTSTRAP_SERVERS)"
  r_binder="$(one_of "$unset_json" SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS)"
  r_consul="$(one_of "$unset_json" SPRING_CLOUD_CONSUL_HOST)"
  printf '  unset renders: %s=%s kafka=%s binder=%s consul=%s\n' "$NET_KEY" "$r_net" "$r_kafka" "$r_binder" "$r_consul"

  if [[ -z "$r_net" ]]; then
    err "$compose declares no network under the key '$NET_KEY', so the join half of HC_SHARED_NETWORK was not checked at all. Renaming the key is not a way past this check. See decisions.md D66."
  fi
  for v in "$r_kafka" "$r_binder" "$r_consul"; do
    case "$v" in
      "«none»"|"") err "$compose renders no service carrying one of the shared-plane addresses, so this check read nothing about it. An empty subject is not a clean one. See decisions.md D66." ;;
      "«"*) err "$compose renders more than one shared-plane address across its services ($v). One broker, one Consul (decisions.md D27) — a per-service override is how half an estate ends up on a plane nobody chose." ;;
    esac
  done

  # --- 1. Each variable must move its own half, and nothing else's --------------------------------
  #
  # Every probe render is guarded the way the baseline above is. `set -Eeuo pipefail` is on, so an
  # unguarded `render … | jq` in a command substitution turns a daemon flake mid-run into an ABORT —
  # three `ok` lines, no `::error::`, and every part below silently skipped. That is the same shape
  # this check's own test caught at `s_default`, and it is worth the four lines to not have it twice.
  set_net="$(render "$compose" "HC_SHARED_NETWORK=$P_NET")" \
    || { err "$compose does not render with HC_SHARED_NETWORK set, so the interpolation was not checked."; continue; }
  m_net="$(printf '%s' "$set_net" | jq -r --arg k "$NET_KEY" '.networks[$k].name // ""')"
  [[ "$m_net" == "$P_NET" ]] \
    && ok "HC_SHARED_NETWORK moves the network $compose joins" \
    || err "with HC_SHARED_NETWORK=$P_NET, $compose still joins '$m_net'. The script inspects the network the variable names and the stack would join another, so preflight passes against a plane nothing uses. See decisions.md D66 and backlog NEW-25."

  set_kafka="$(render "$compose" "HC_SHARED_KAFKA=$P_KAFKA")" \
    || { err "$compose does not render with HC_SHARED_KAFKA set, so the interpolation was not checked."; continue; }
  m_kafka="$(one_of "$set_kafka" SPRING_KAFKA_BOOTSTRAP_SERVERS)"
  m_binder="$(one_of "$set_kafka" SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS)"
  # BOTH property names, because hc-market reads the first and its binder reads the second, and
  # moving one leaves the other quietly pointing at the old broker — the compose file says so at the
  # line, and a check that asked about one of them would be the same omission one layer up.
  if [[ "$m_kafka" == "$P_KAFKA:9092" && "$m_binder" == "$P_KAFKA:9092" ]]; then
    ok "HC_SHARED_KAFKA moves both broker properties in $compose"
  else
    err "with HC_SHARED_KAFKA=$P_KAFKA, $compose renders SPRING_KAFKA_BOOTSTRAP_SERVERS='$m_kafka' and SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS='$m_binder'; both must be '$P_KAFKA:9092'. See decisions.md D66."
  fi

  set_consul="$(render "$compose" "HC_SHARED_CONSUL=$P_CONSUL")" \
    || { err "$compose does not render with HC_SHARED_CONSUL set, so the interpolation was not checked."; continue; }
  m_consul="$(printf '%s' "$set_consul" | jq -r '[.services[].environment.SPRING_CLOUD_CONSUL_HOST // empty] | unique | join(",")')"
  [[ "$m_consul" == "$P_CONSUL" ]] \
    && ok "HC_SHARED_CONSUL moves the Consul host in $compose" \
    || err "with HC_SHARED_CONSUL=$P_CONSUL, $compose renders SPRING_CLOUD_CONSUL_HOST='$m_consul'. See decisions.md D66."

  # --- 2. One default, written twice, and the two must be the same string -------------------------
  #
  # `^`-anchored, so a `#` comment cannot match it and no stripper is required. The compose side is
  # part 1's RENDER of the unset case, never a second grep of the same file — which is the fail-open
  # D64's review found in the check this one is modelled on.
  # `|| true` on the grep, and it is not tidiness. `set -Eeuo pipefail` is on: an unmatched grep
  # exits 1, pipefail carries that out of the substitution, and the whole check then ABORTS at this
  # line — every part below it skipped, and no `::error::` printed at all. Watched happening, on the
  # commented-out-default mutation this pairing exists to catch. Empty is an answer here, and the
  # comparison below refuses it by name.
  s_default() { # s_default <VARNAME>
    { grep -oE "^${1#HC_}=\"\\\$\{$1:-[A-Za-z0-9_.-]+\}\"" "$script" || true; } \
      | sed -E "s/.*$1:-([A-Za-z0-9_.-]+)\}\"/\1/" | head -1
  }
  d_net="$(s_default HC_SHARED_NETWORK)"; d_consul="$(s_default HC_SHARED_CONSUL)"; d_kafka="$(s_default HC_SHARED_KAFKA)"
  printf '  script defaults: network=%s consul=%s kafka=%s\n' "${d_net:-«none»}" "${d_consul:-«none»}" "${d_kafka:-«none»}"
  [[ -n "$d_net" && "$d_net" == "$r_net" ]] \
    && ok "$script and $compose default HC_SHARED_NETWORK to the same name" \
    || err "$script defaults HC_SHARED_NETWORK to '${d_net:-«nothing this check can read»}' and $compose renders '$r_net' with it unset. Two defaults for one value is how the two eventually disagree. See decisions.md D66."
  [[ -n "$d_consul" && "$d_consul" == "$r_consul" ]] \
    && ok "$script and $compose default HC_SHARED_CONSUL to the same name" \
    || err "$script defaults HC_SHARED_CONSUL to '${d_consul:-«nothing this check can read»}' and $compose renders '$r_consul' with it unset. See decisions.md D66."
  # The port is appended by compose and is deliberately not part of the variable: inside a container
  # Kafka is always 9092 and Consul always 8500, and a value carrying its own port is not a container
  # name, which is what preflight execs. Comparing against "$default:9092" pins both halves of that.
  [[ -n "$d_kafka" && "$d_kafka:9092" == "$r_kafka" ]] \
    && ok "$script and $compose default HC_SHARED_KAFKA to the same name, port appended by compose" \
    || err "$script defaults HC_SHARED_KAFKA to '${d_kafka:-«nothing this check can read»}' and $compose renders '$r_kafka' with it unset; they must agree, with :9092 appended by compose alone. See decisions.md D66."
done

# --- 3. The script must EXPORT what it resolved, on every action ---------------------------------
#
# Behavioural: env_for_compose is lifted out of the file and run, then the environment it left is
# read. A comment naming the three variables cannot satisfy this, and neither can an export of the
# raw HC_SHARED_* rather than the resolved SHARED_* — the probes below are distinguishable.
printf '\n%s: env_for_compose\n' "$STARTUP"
exported="$(
  set +e
  (
    set -Eeuo pipefail
    IMAGES=published; ROOT="$PWD"; HERE="$PWD/quality"
    GATEWAY_PORT=1; CATALOG_PORT=2; BOOKING_PORT=3; MESSAGING_PORT=4; PAYOUT_PORT=5
    OTEL_NETWORK="$P_NET-otel"
    SHARED_NETWORK="$P_NET"; SHARED_CONSUL="$P_CONSUL"; SHARED_KAFKA="$P_KAFKA"
    eval "$(awk '/^env_for_compose\(\) \{/,/^\}/' "$STARTUP")"
    env_for_compose >/dev/null 2>&1
    printf '%s|%s|%s' "${HC_SHARED_NETWORK:-}" "${HC_SHARED_CONSUL:-}" "${HC_SHARED_KAFKA:-}"
  )
)"
printf '  exports: %s\n' "$exported"
[[ "$exported" == "$P_NET|$P_CONSUL|$P_KAFKA" ]] \
  && ok "env_for_compose exports all three resolved values" \
  || err "$STARTUP's env_for_compose exports '$exported', wanted '$P_NET|$P_CONSUL|$P_KAFKA'. Without all three, the value preflight checked is not the value compose interpolates — and a '--down' can address a different plane from the 'up' that made the containers. See decisions.md D66."

# --- 4. Preflight must refuse a plane the broker and Consul are not on ---------------------------
#
# Both copies, each named with its own function — see the header for why a shared name could not be
# assumed. `lifted` is checked before either state is run: an `awk` range that matches nothing evals
# nothing, the function is then simply absent, and both probes would report the same thing for a
# reason that has nothing to do with the plane.
plane() { # plane <script> <function> <networks the stubbed containers are on> [unanswerable]
  (
    set +e
    script="$1" fn="$2" on_networks="$3" cannot_answer="${4:-}"
    ok() { printf 'OK\n'; }
    die() { printf 'DIE %s\n' "$*"; exit 3; }
    # deploy-dev.sh's shared_plane ends with a note about a running quality stack; these keep it
    # quiet without answering anything the assertions below read. Quality's copy calls neither.
    log() { :; }; warn() { :; }; TOPIC_PREFIX="ci."
    # Stands in for the daemon. `inspect -f` is answered by which template it was handed; everything
    # else succeeds, so the only thing that can refuse below is the question under test.
    #
    # THE FOURTH ARGUMENT IS WHY THIS STUB EXISTS AT ALL SINCE D71. Until then it answered every
    # call successfully, so a folded `2>/dev/null | grep` and a status-checked capture were
    # indistinguishable by it — measured, by folding D69's fix back out of deploy-dev.sh and
    # watching this part stay green (backlog NEW-32). `unanswerable` names WHICH docker read fails,
    # and the messages are docker 29.7.2's own: the network arm must be told apart by its text
    # ("not found" is the network being absent, anything else is the daemon), so a stub answering
    # with the wrong sentence would test the wrong branch.
    docker() {
      case "$1 $2" in
        "network inspect")
          case "$cannot_answer" in
            net-absent)
              printf '[]\n'
              printf 'Error response from daemon: network %s not found\n' "$3" >&2
              return 1 ;;
            net)
              printf '[]\n'
              printf 'failed to connect to the docker API at unix:///nonexistent; check if the path is correct and if the daemon is running\n' >&2
              return 1 ;;
            *) return 0 ;;
          esac ;;
        "inspect -f")
          case "$3" in
            *State.Running*) printf 'true\n' ;;
            *)
              if [[ "$cannot_answer" == nets ]]; then
                printf 'Cannot connect to the Docker daemon at unix:///var/run/docker.sock.\n' >&2
                return 1
              fi
              printf '%s\n' $on_networks ;;
          esac
          return 0 ;;
        *) return 0 ;;
      esac
    }
    SHARED_NETWORK="$P_NET"; SHARED_CONSUL="$P_CONSUL"; SHARED_KAFKA="$P_KAFKA"
    SHARED_INFRA_DIR=/nowhere
    # deploy-dev.sh's one-line `running()` helper, if this script has one. Harmless where it does
    # not: the grep matches nothing and evals nothing.
    eval "$(grep -E '^running\(\) \{.*\}$' "$script" || true)"
    eval "$(awk -v fn="$fn" 'index($0, fn "() {") == 1, /^\}/' "$script")"
    "$fn"
  ) 2>&1 || true   # a refusal is one of the two ANSWERS here, not a failure of this script
}
for entry in $PLANE_FNS; do
  s_plane="${entry%%:*}"; fn_plane="${entry#*:}"
  printf '\n%s: %s\n' "$s_plane" "$fn_plane"
  if [[ ! -f "$s_plane" ]]; then
    err "$s_plane does not exist, so nothing was established about its shared-plane preflight. See decisions.md D69."
    continue
  fi
  lifted="$(awk -v fn="$fn_plane" 'index($0, fn "() {") == 1, /^\}/' "$s_plane")"
  if [[ -z "$lifted" ]]; then
    err "$s_plane declares no function '$fn_plane', so part 4 read nothing about it. The two copies of this preflight are NOT called the same thing (check_shared_plane in quality, shared_plane in dev), and a range that lifts nothing passes both probes below for a reason that has nothing to do with the plane. See decisions.md D69 and backlog NEW-29."
    continue
  fi
  member="$(plane "$s_plane" "$fn_plane" "$P_NET other-net")"
  stranger="$(plane "$s_plane" "$fn_plane" "other-net third-net")"
  no_nets="$(plane "$s_plane" "$fn_plane" "$P_NET other-net" nets)"
  no_net="$(plane "$s_plane" "$fn_plane" "$P_NET other-net" net)"
  gone_net="$(plane "$s_plane" "$fn_plane" "$P_NET other-net" net-absent)"
  # Newlines COLLAPSED rather than truncated at the first one: docker's own error text is quoted
  # into two of these refusals and `[]` is the first line of it, so a `%%$'\n'*` printed a message
  # that appeared to end there and read as the fix having lost the sentence it exists to quote.
  printf '  on the network:      %s\n  off it:              %s\n' "${member//$'\n'/ }" "${stranger//$'\n'/ }"
  printf '  networks unaskable:  %s\n  network unaskable:   %s\n  network absent:      %s\n' \
    "${no_nets//$'\n'/ }" "${no_net//$'\n'/ }" "${gone_net//$'\n'/ }"
  case "$member" in
    OK*) ok "$fn_plane passes when the broker and Consul are on the network the stack joins" ;;
    *)   err "$s_plane's $fn_plane refuses a shared plane that IS on the network the stack joins: '$member'. A refusal that fires on the correct state is not a check. See decisions.md D66." ;;
  esac
  case "$stranger" in
    DIE*) ok "$fn_plane refuses a shared plane that is not on the network the stack joins" ;;
    *)    err "$s_plane's $fn_plane accepted a Consul and a broker that are on neither the network the stack joins ('$stranger'). Running is not the same question as reachable: the five services would come up healthy and publish into nowhere, which is decisions.md D27's silence. See decisions.md D66 and D69." ;;
  esac
  # THE THREE STATES ADDED BY D71, and each is red for a DIFFERENT reason than "it did not refuse".
  # Every one of these is fatal either way, so what is asserted is which CAUSE the refusal names: a
  # daemon that could not answer must not be reported as a broker on the wrong network, or as a
  # network that is not there, because both send an operator to hc-infra to fix a plane that is
  # fine. That is the cost D66 §7 took three separate messages to remove, undone one docker read
  # along. See backlog NEW-32.
  case "$no_nets" in
    "DIE docker could not be asked which networks"*)
      ok "$fn_plane says so when docker cannot be asked which networks the plane is on" ;;
    DIE*)
      err "$s_plane's $fn_plane refused a plane it could not ask about, but named the wrong cause: '$no_nets'. A daemon that cannot answer is not a broker on the wrong network, and the message decides whether whoever reads it restarts hc-infra for nothing. Status-check the docker read behind the membership question — see decisions.md D71 and deploy-dev.sh's copy." ;;
    *)
      err "$s_plane's $fn_plane ACCEPTED a shared plane whose membership docker could not be asked about ('$no_nets'). Whether the stack can reach the broker is then unestablished rather than established, which is decisions.md D27's silence again. See decisions.md D71." ;;
  esac
  case "$no_net" in
    "DIE docker could not be asked whether the shared network"*)
      ok "$fn_plane says so when docker cannot be asked whether the shared network exists" ;;
    DIE*)
      err "$s_plane's $fn_plane refused a network it could not ask about, but named the wrong cause: '$no_net'. This arm is the FIRST thing the function does, so it is the message an unanswerable daemon actually produces — and 'the shared network does not exist' sends whoever reads it to start a plane that is already running. See decisions.md D71." ;;
    *)
      err "$s_plane's $fn_plane ACCEPTED a shared network it could not ask about ('$no_net'). See decisions.md D71." ;;
  esac
  # The positive control for the arm above, and not a courtesy: an absent network and an unanswerable
  # daemon are one exit status apart and are told apart by docker's own words, so a fix that collapsed
  # both into "could not be asked" would satisfy every other assertion here while losing the message
  # NEW-25 wrote — this defect, mirrored.
  case "$gone_net" in
    "DIE the shared network"*)
      ok "$fn_plane still says a shared network that is genuinely absent does not exist" ;;
    DIE*)
      err "$s_plane's $fn_plane refused an absent shared network, but no longer says it is absent: '$gone_net'. 'docker could not be asked' for a network docker answered about is the D71 defect in the other direction — the two are told apart by docker's message, not by its exit status. See decisions.md D71." ;;
    *)
      err "$s_plane's $fn_plane ACCEPTED a shared network that does not exist ('$gone_net'). See decisions.md D66." ;;
  esac
done

# --- 5. The EXACT set of actions that reach that refusal ------------------------------------------
#
# Part 4's refusal is fatal in both scripts, and in deploy-dev.sh that is safe only because the
# router's `down`, `status` and `logs` branches never call `preflight` — a broken plane must not be
# able to wedge the command that takes the stack down, nor the one you would read to find out why.
# Quality's equivalent is asserted by D67's own test (`router < preflight < call`), which is why only
# the dev router is asked here.
#
# THE EXACT SET, not a deny-list, and both directions were reproduced before this was written
# (D69 §10). A per-branch "down does not call preflight" loop counted and did not attribute: deleting
# the one `preflight` line under `up)` left the check GREEN, shipping an `up` that starts the estate
# with no plane check at all — NEW-29's defect in a stronger form, the question never asked rather
# than answered wrongly. And a deny-list cannot see an action nobody anticipated: `doctor) preflight;
# compose ps ;;` added to the router was green too, which is the next diagnostic wedged by the very
# property this part exists to pin. D67's rule, one guard along: the EXACT set is required, not
# "the ones we thought to ask about".
#
# WHAT THIS MATCHES IS A DIRECT TEXTUAL CALL IN THE BRANCH — say it plainly, because the ok line
# used to claim "reach". Indirection is invisible: `plane_gate() { preflight; }` above the router with
# `plane_gate` in `down)` leaves this green (measured), and the exact-set formulation does not close
# it. Keep the call direct. The error message names the same limit from the other side.
printf '\n%s: the router\n' "$DEV_SCRIPT"
if [[ ! -f "$STRIP_SH" ]]; then
  err "$STRIP_SH is missing, so part 5 could not strip comments and did not run. A check that reads nothing is not a check that found nothing."
elif [[ ! -f "$DEV_SCRIPT" ]]; then
  err "$DEV_SCRIPT does not exist, so nothing was established about which of its actions reach preflight. See decisions.md D69."
else
  stripped="$(awk -f "$STRIP_SH" "$DEV_SCRIPT")"
  router="$(printf '%s\n' "$stripped" | awk '/^case "\$COMMAND" in/,/^esac/')"
  # One pass, attributing each call to the branch it is in. A branch runs from its label to the line
  # before the next label — never to the first `;;`, which a nested `case` inside a branch would end
  # early and silently. `preflight` is matched as a CALL and not as the word: it appears in one of
  # shared_plane's own refusal messages, and a subject-exists control satisfied by a string would
  # report a subject for a script that has none.
  branch_calls="$(printf '%s\n' "$router" | awk '
    function emit() { if (label != "") { print label, has } label = ""; has = 0 }
    match($0, /^  [A-Za-z_*|.-]+\)/) {
      emit()
      label = substr($0, 3, RLENGTH - 3)
      rest  = substr($0, RLENGTH + 1)
      if (rest ~ /(^|[^A-Za-z_])preflight([^A-Za-z_]|$)/) has = 1
      next
    }
    /^esac/ { emit(); next }
    label != "" && $0 ~ /(^|[^A-Za-z_])preflight([^A-Za-z_]|$)/ { has = 1 }
    END { emit() }
  ')"
  labels="$(printf '%s\n' "$branch_calls" | awk 'NF {print $1}' | sort | tr '\n' ' ')"
  gated="$(printf '%s\n' "$branch_calls" | awk '$2 == 1 {print $1}' | sort | tr '\n' ' ')"
  want="$(printf '%s\n' $DEV_PREFLIGHT_BRANCHES | sort | tr '\n' ' ')"
  printf '  router branches: %s\n  calling preflight: %s\n  expected: %s\n' \
    "${labels:-«none»}" "${gated:-«none»}" "$want"

  if [[ -z "$router" ]]; then
    err "$DEV_SCRIPT has no 'case \"\$COMMAND\" in' router this check can read, so nothing below was established. See decisions.md D69."
  elif [[ -z "${labels// /}" ]]; then
    err "$DEV_SCRIPT's router yielded no branch labels this check can read, so which actions reach the shared-plane refusal was established by reading nothing. An empty subject is not a clean one. See decisions.md D69."
  elif [[ -z "${gated// /}" ]]; then
    err "$DEV_SCRIPT never calls preflight from any router branch, so 'the teardown actions do not call it' is true of a script that checks no plane on any action — which is backlog NEW-29's defect in a stronger form: the question never asked rather than answered wrongly. See decisions.md D69."
  else
    # Every branch that MUST call it has to exist, or the set comparison is against a ghost: rename
    # `up)` and both halves of the difference are reported rather than one.
    missing_branch=""
    for a in $DEV_PREFLIGHT_BRANCHES; do
      [[ " $labels " == *" $a "* ]] || missing_branch+="$a "
    done
    if [[ -n "$missing_branch" ]]; then
      err "$DEV_SCRIPT's router has no branch for: ${missing_branch% }— so the set of actions that reach the shared-plane refusal was compared against branches that are not there. Rename an action and this check must be told, not left comparing against a ghost. See decisions.md D69."
    fi
    if [[ "$gated" == "$want" ]]; then
      ok "$DEV_SCRIPT's router calls preflight from exactly {${want% }} — direct calls in the branch; see the header on indirection"
    else
      not_gated=""; unexpected=""
      for a in $want;  do [[ " $gated " == *" $a "* ]] || not_gated+="$a "; done
      for a in $gated; do [[ " $want "  == *" $a "* ]] || unexpected+="$a "; done
      err "$DEV_SCRIPT's router calls preflight from {${gated% }}; it must be exactly {${want% }}.${not_gated:+ Must call it and do not: ${not_gated% }.}${unexpected:+ Call it and are not accounted for: ${unexpected% }.} Since decisions.md D69 preflight refuses a shared plane the broker is not on, FATALLY — so an action that gained it can be wedged by a broken plane (a teardown, or the diagnostic you would read to find out why), and an action that lost it starts the estate with no plane check at all. Add it here with an argument, or take it out of the branch."
    fi
  fi
fi

printf '\n'
if (( fail )); then printf 'shared-plane wiring: FAILED\n'; else printf 'shared-plane wiring: ok\n'; fi
exit $fail
