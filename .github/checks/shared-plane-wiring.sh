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
#  FOUR PARTS, and the first two are asked of BOTH pairs — quality and dev — because dev is the shape
#  quality was fixed to copy and a regression there would take the model with it.
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
#  4. PREFLIGHT REFUSES A PLANE THE BROKER IS NOT ON. "Running" and "on the network this stack
#     joins" are different questions and nothing asked the second until D66: measured against a
#     throwaway empty network, the whole of `check_shared_plane` passed and its success line printed
#     "…on hc-market-d66-probe", asserting a membership it had never looked for. Harmless while
#     compose hardcoded `hcnet`; live the moment part 1 started moving the join. Asked behaviourally
#     with `docker` stubbed, including the positive control — a refusal that fires on everything is
#     not a check.
#
#     REACH: the stub answers with a network list, so this catches the refusal being DELETED or
#     weakened and does not catch the Go template being wrong. That direction was measured by hand
#     instead (D66 §3): a template naming a field that does not exist yields nothing, the `grep -Fxq`
#     finds nothing, and preflight refuses — the direction a check about a silent failure has to fail
#     in. `check_shared_plane` also runs against the real daemon on every `startup.sh --local`.
#
#  Inputs are overridable so the test beside this file can construct each broken state and watch this
#  fail — a check nobody has seen fail is a check of nothing.
#
#      ./.github/checks/shared-plane-wiring.sh
#      HC_PAIRS="quality/compose.yml:quality/startup.sh" ./.github/checks/shared-plane-wiring.sh
# ==============================================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# compose-file:script pairs. Both are checked for parts 1 and 2; the quality script alone is checked
# for parts 3 and 4, because it is the only one that resolves and then hands the values to compose
# itself — deploy-dev.sh exports them at the top level and has no env_for_compose to run.
PAIRS="${HC_PAIRS:-quality/compose.yml:quality/startup.sh deploy/docker/docker-compose.dev.yml:deploy/deploy-dev.sh}"
STARTUP="${HC_STARTUP:-quality/startup.sh}"

# The compose key the shared plane is declared under, in both files. Enumerated rather than derived,
# and that is safe here only because an absent key is REFUSED below rather than skipped: both files
# also declare a second external network (`qualitynet`), so "the external one" derives nothing.
NET_KEY="${HC_NET_KEY:-hcnet}"

# Probe values, deliberately unlike any real name on this host or in hc-infra.
P_NET="hc-ci-probe-net"; P_CONSUL="hc-ci-probe-consul"; P_KAFKA="hc-ci-probe-kafka"

fail=0
err() { printf '::error::%s\n' "$*"; fail=1; }
ok()  { printf '  ok   %s\n' "$*"; }

render() { # render <compose-file> [VAR=VAL ...]
  local f="$1"; shift
  env "$@" JWT_BASE64_SECRET=ci-placeholder HC_PRIVACY_PEPPER=ci-placeholder TAG=ci \
    docker compose -f "$f" config --format json
}

# The one distinct value of an environment key across every service that carries it. Zero services
# and two disagreeing values are both refused, and they print differently: an empty subject is how
# six earlier checks in this repository reported "clean" having read nothing.
one_of() { # one_of <json> <ENV_KEY>
  printf '%s' "$1" | jq -r --arg k "$2" \
    '[.services[].environment[$k] // empty] | unique | if length == 1 then .[0] else "«\(length) values»" end'
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
      "") err "$compose renders no service carrying one of the shared-plane addresses, so this check read nothing. See decisions.md D66." ;;
      "«"*) err "$compose renders more than one shared-plane address across its services ($v). One broker, one Consul (decisions.md D27) — a per-service override is how half an estate ends up on a plane nobody chose." ;;
    esac
  done

  # --- 1. Each variable must move its own half, and nothing else's --------------------------------
  m_net="$(render "$compose" "HC_SHARED_NETWORK=$P_NET" | jq -r --arg k "$NET_KEY" '.networks[$k].name // ""')"
  [[ "$m_net" == "$P_NET" ]] \
    && ok "HC_SHARED_NETWORK moves the network $compose joins" \
    || err "with HC_SHARED_NETWORK=$P_NET, $compose still joins '$m_net'. The script inspects the network the variable names and the stack would join another, so preflight passes against a plane nothing uses. See decisions.md D66 and backlog NEW-25."

  set_kafka="$(render "$compose" "HC_SHARED_KAFKA=$P_KAFKA")"
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

  m_consul="$(render "$compose" "HC_SHARED_CONSUL=$P_CONSUL" | jq -r '[.services[].environment.SPRING_CLOUD_CONSUL_HOST // empty] | unique | join(",")')"
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
printf '\n%s: check_shared_plane\n' "$STARTUP"
plane() { # plane <networks the stubbed containers are on, space separated>
  (
    set +e
    on_networks="$1"
    ok() { printf 'OK\n'; }
    die() { printf 'DIE %s\n' "$*"; exit 3; }
    # Stands in for the daemon. `inspect -f` is answered by which template it was handed; everything
    # else succeeds, so the ONLY thing that can refuse below is the membership question.
    docker() {
      case "$1 $2" in
        "network inspect") return 0 ;;
        "inspect -f")
          case "$3" in
            *State.Running*) printf 'true\n' ;;
            *) printf '%s\n' $on_networks ;;
          esac
          return 0 ;;
        *) return 0 ;;
      esac
    }
    SHARED_NETWORK="$P_NET"; SHARED_CONSUL="$P_CONSUL"; SHARED_KAFKA="$P_KAFKA"
    SHARED_INFRA_DIR=/nowhere
    eval "$(awk '/^check_shared_plane\(\) \{/,/^\}/' "$STARTUP")"
    check_shared_plane
  ) 2>&1 || true   # a refusal is one of the two ANSWERS here, not a failure of this script
}
member="$(plane "$P_NET other-net")"
stranger="$(plane "other-net third-net")"
printf '  on the network:  %s\n  off it:          %s\n' "${member%%$'\n'*}" "${stranger%%$'\n'*}"
case "$member" in
  OK*) ok "preflight passes when the broker and Consul are on the network the stack joins" ;;
  *)   err "$STARTUP's check_shared_plane refuses a shared plane that IS on the network the stack joins: '$member'. A refusal that fires on the correct state is not a check. See decisions.md D66." ;;
esac
case "$stranger" in
  DIE*) ok "preflight refuses a shared plane that is not on the network the stack joins" ;;
  *)    err "$STARTUP's check_shared_plane accepted a Consul and a broker that are on neither the network the stack joins ('$stranger'). Running is not the same question as reachable: the five services would come up healthy and publish into nowhere, which is decisions.md D27's silence. See decisions.md D66." ;;
esac

printf '\n'
if (( fail )); then printf 'shared-plane wiring: FAILED\n'; else printf 'shared-plane wiring: ok\n'; fi
exit $fail
