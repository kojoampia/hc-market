#!/usr/bin/env bash
# ==============================================================================
#  HealthConnect Marketplace — dev / test deployment
#
#  Brings up the whole microservice estate locally: Consul, Kafka, MongoDB for the gateway,
#  one PostgreSQL per domain service, the gateway and the four domain services, then loads
#  demo/seed-data.json through the test,dev seed loader.
#
#  Usage:
#     ./deploy-dev.sh up                      # build, start everything, seed
#     ./deploy-dev.sh up --no-build           # start from existing images
#     ./deploy-dev.sh up --services catalog,booking
#     ./deploy-dev.sh reseed                  # wipe + reload seed-data.json only
#     ./deploy-dev.sh status | logs | restart | down
#     ./deploy-dev.sh down --clean            # also drop volumes (data loss)
#
#  Options:
#     --profiles <list>   Spring profiles           (default: test,dev)
#     --services <list>   Comma-separated subset    (default: all)
#     --seed-file <path>  Seed JSON                 (default: demo/seed-data.json)
#     --no-build          Skip the Maven/Jib build
#     --with-tests        Run `clean verify` before each image (slow: Testcontainers per app)
#     --clean             Remove volumes on down / rebuild from scratch on up
#     --timeout <secs>    Per-service health gate   (default: 180)
#
#  Layout note (decisions.md D6): the five apps are SIBLING DIRECTORIES of this script's parent,
#  each a standalone Maven project with its own ./mvnw — there is no aggregator pom and no Maven
#  reactor, exactly as in hc-admin, hc-patient and hc-professional.
#
#  Discovery is CONSUL (decisions.md D5), not the JHipster Registry. There is no service on 8761.
# ==============================================================================
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"       # the workspace holding gateway/ catalog/ …
cd "$DEPLOY_DIR"

# ------------------------------------------------------------------ defaults --
PROFILES="test,dev"
SEED_FILE="$DEPLOY_DIR/demo/seed-data.json"
COMPOSE_FILE="$DEPLOY_DIR/docker/docker-compose.dev.yml"
PROJECT="healthconnect-dev"
ALL_SERVICES=(gateway catalog booking messaging payout)
SERVICES=("${ALL_SERVICES[@]}")
DO_BUILD=1
DO_CLEAN=0
RUN_TESTS=0
TIMEOUT=180
# Java 25 needs a JDK with a compiler. /usr/lib/jvm/java-25-openjdk-amd64 is a JRE and its failure
# mode is an incremental build that silently passes — see the workspace guide.
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/jdk-25.0.2-oracle-x64}"

case "${1:-}" in -h|--help) sed -n '2,32p' "$0"; exit 0 ;; esac
# the first bare word is the command; anything starting with "-" is an option
if [[ $# -gt 0 && "$1" != -* ]]; then COMMAND="$1"; shift; else COMMAND="up"; fi

# Host ports, overridable so several products can run on one workstation without colliding.
# These MUST match the defaults in docker/docker-compose.dev.yml, which reads the same variables.
declare -A PORTS=(
  [gateway]="${HC_GATEWAY_PORT:-8080}"
  [catalog]="${HC_CATALOG_PORT:-8081}"
  [booking]="${HC_BOOKING_PORT:-8082}"
  [messaging]="${HC_MESSAGING_PORT:-8083}"
  [payout]="${HC_PAYOUT_PORT:-8084}"
)
CONSUL_PORT="${HC_CONSUL_PORT:-8500}"
export HC_GATEWAY_PORT="${PORTS[gateway]}" HC_CATALOG_PORT="${PORTS[catalog]}" \
       HC_BOOKING_PORT="${PORTS[booking]}" HC_MESSAGING_PORT="${PORTS[messaging]}" \
       HC_PAYOUT_PORT="${PORTS[payout]}" HC_CONSUL_PORT="$CONSUL_PORT" 

# --------------------------------------------------------------------- output --
c_reset=$'\033[0m'; c_dim=$'\033[2m'; c_b=$'\033[1m'
c_ok=$'\033[32m'; c_warn=$'\033[33m'; c_err=$'\033[31m'; c_info=$'\033[36m'
log()  { printf '%s▸%s %s\n' "$c_info" "$c_reset" "$*"; }
ok()   { printf '%s✓%s %s\n' "$c_ok" "$c_reset" "$*"; }
warn() { printf '%s!%s %s\n' "$c_warn" "$c_reset" "$*"; }
die()  { printf '%s✗ %s%s\n' "$c_err" "$*" "$c_reset" >&2; exit 1; }
step() { printf '\n%s%s%s\n' "$c_b" "$*" "$c_reset"; }
trap 'die "failed at line $LINENO: ${BASH_COMMAND}"' ERR

# ---------------------------------------------------------------- arg parsing --
while [[ $# -gt 0 ]]; do
  case "$1" in
    --profiles)  PROFILES="$2"; shift 2 ;;
    --services)  IFS=',' read -r -a SERVICES <<< "$2"; shift 2 ;;
    --seed-file) SEED_FILE="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"; shift 2 ;;
    --no-build)   DO_BUILD=0; shift ;;
    --with-tests) RUN_TESTS=1; shift ;;
    --clean)     DO_CLEAN=1; shift ;;
    --timeout)   TIMEOUT="$2"; shift 2 ;;
    -h|--help)   sed -n '2,32p' "$0"; exit 0 ;;
    *)           die "unknown option: $1 (try --help)" ;;
  esac
done

for s in "${SERVICES[@]}"; do
  [[ " ${ALL_SERVICES[*]} " == *" $s "* ]] || die "unknown service '$s' (known: ${ALL_SERVICES[*]})"
done

# ------------------------------------------------------------- prerequisites --
require() { command -v "$1" >/dev/null 2>&1 || die "$1 is required but not on PATH"; }
java_major() {                       # robust: ignores "Picked up JAVA_TOOL_OPTIONS" noise
  local out major
  out="$("$JAVA_HOME/bin/java" -version 2>&1 || true)"
  major="$(printf '%s\n' "$out" | grep -E 'version "' | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  [[ "$major" =~ ^[0-9]+$ ]] || major=0
  printf '%s' "$major"
}
preflight() {
  step "Preflight"
  require docker; require curl; require jq
  docker compose version >/dev/null 2>&1 || die "docker compose v2 plugin is required"
  docker info >/dev/null 2>&1 || die "docker daemon is not reachable"

  if [[ $DO_BUILD -eq 1 ]]; then
    [[ -x "$JAVA_HOME/bin/javac" ]] || die "no javac at $JAVA_HOME — that is a JRE, not a JDK. Set JAVA_HOME to a real JDK 25."
    local jv; jv="$(java_major)"
    (( jv >= 25 )) || die "Java 25+ required for Spring Boot 4 (found ${jv/0/unknown} at $JAVA_HOME)"
    for s in "${SERVICES[@]}"; do
      [[ -x "$ROOT_DIR/$s/mvnw" ]] || die "$ROOT_DIR/$s/mvnw not found — has the app been generated?"
    done
    ok "Java $jv at $JAVA_HOME"
  fi

  [[ -f "$COMPOSE_FILE" ]] || die "compose file not found: $COMPOSE_FILE"
  [[ -f "$SEED_FILE"    ]] || die "seed file not found: $SEED_FILE"
  jq -e '.["$meta"].name == "healthconnect-demo-seed"' "$SEED_FILE" >/dev/null \
    || die "$SEED_FILE does not look like a HealthConnect seed file"
  ok "seed file OK — $(jq '.professionals|length' "$SEED_FILE") professionals, $(jq '.reviews|length' "$SEED_FILE") reviews, $(jq '.sessions|length' "$SEED_FILE") historic sessions"

  case ",$PROFILES," in
    *,prod,*) die "refusing to run the dev script with the 'prod' profile — use deploy-prod.sh" ;;
  esac
  ok "profiles: $PROFILES"
}

compose() { docker compose -p "$PROJECT" -f "$COMPOSE_FILE" "$@"; }

wait_http() {                        # wait_http <name> <url> <timeout>
  local name="$1" url="$2" limit="$3" waited=0
  printf '  %s… ' "$name"
  until curl -fsS --max-time 3 "$url" >/dev/null 2>&1; do
    (( waited += 3 )); sleep 3
    if (( waited >= limit )); then printf '%stimeout%s\n' "$c_err" "$c_reset"; return 1; fi
    printf '.'
  done
  printf '%sup%s (%ss)\n' "$c_ok" "$c_reset" "$waited"
}

# ------------------------------------------------------------------- actions --
# Each app is a standalone Maven project. No reactor, no -pl: we cd into each in turn, exactly as
# every sibling product in this workspace is built.
#
# TESTS ARE SKIPPED WHEN BUILDING IMAGES, deliberately, matching hc-patient/deploy/docker/*.Dockerfile
# ("`./mvnw verify` on a developer machine or in CI"). These apps are generated with Cucumber and
# their tests stand up Testcontainers, so `clean verify` costs minutes per app and needs a Docker
# daemon -- packaging an image is the wrong place to discover that. Run tests explicitly:
#
#     ./deploy-dev.sh up --with-tests      # clean verify before each image
#     (cd ../catalog && ./mvnw clean verify)
build() {
  step "Build"
  export JAVA_HOME
  for s in "${SERVICES[@]}"; do
    if (( RUN_TESTS )); then
      log "verifying $s (Testcontainers -- slow)"
      ( cd "$ROOT_DIR/$s" && ./mvnw -q -ntp clean verify -Pdev ) || die "tests failed for $s"
    else
      log "packaging $s"
      ( cd "$ROOT_DIR/$s" && ./mvnw -q -ntp clean package -DskipTests -Pdev ) || die "build failed for $s"
    fi
    log "jib:dockerBuild healthconnect/$s:local"
    ( cd "$ROOT_DIR/$s" && ./mvnw -q -ntp jib:dockerBuild -DskipTests -Pdev \
        -Djib.to.image="healthconnect/$s:local" ) || die "image build failed for $s"
  done
  ok "images built$( (( RUN_TESTS )) && printf ' (tests passed)' || printf ' (tests skipped)')"
}

infra_up() {
  step "Infrastructure"
  local dbs=()
  for s in "${SERVICES[@]}"; do dbs+=("${s}-db"); done
  compose up -d consul kafka "${dbs[@]}"
  wait_http "consul" "http://localhost:${CONSUL_PORT}/v1/status/leader" 120 || die "Consul did not start"
  log "waiting for Kafka"
  local waited=0
  until docker compose -p "$PROJECT" -f "$COMPOSE_FILE" exec -T kafka \
        /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list >/dev/null 2>&1; do
    (( waited += 3 )); sleep 3
    (( waited >= 120 )) && die "Kafka did not become ready"
  done
  ok "Kafka ready"
  log "ensuring topics"
  for t in booking.requested booking.accepted booking.declined booking.cancelled \
           booking.completed review.published payout.settled notification.raised; do
    docker compose -p "$PROJECT" -f "$COMPOSE_FILE" exec -T kafka \
      /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
      --create --if-not-exists --topic "healthconnect.$t" --partitions 3 --replication-factor 1 >/dev/null
  done
  ok "8 topics present"
}

apps_up() {
  step "Services"
  export SPRING_PROFILES_ACTIVE="$PROFILES"
  SEED_HOST_PATH="$(dirname "$SEED_FILE")"
  export SEED_HOST_PATH
  : "${JWT_BASE64_SECRET:?set JWT_BASE64_SECRET (one key across the estate — see the workspace guide)}"
  export JWT_BASE64_SECRET
  compose up -d "${SERVICES[@]}"
  local failed=0
  for s in "${SERVICES[@]}"; do
    wait_http "$s" "http://localhost:${PORTS[$s]}/management/health" "$TIMEOUT" || failed=1
  done
  (( failed )) && { warn "some services did not come up — showing the last 40 lines"; compose logs --tail=40; die "startup failed"; }
  ok "all services healthy"
}

# Compares what the API reports against what the seed file contains. A mismatch fails the run —
# the same integrity discipline the prototype applied to its charts, applied to the loader.
verify_seed() {
  step "Seed verification"
  local expect_pro expect_rev
  expect_pro="$(jq '.professionals|length' "$SEED_FILE")"
  expect_rev="$(jq '.reviews|length'       "$SEED_FILE")"
  local got_pro got_rev
  got_pro="$(curl -fsS "http://localhost:${PORTS[catalog]}/api/professionals/count" || echo 0)"
  got_rev="$(curl -fsS "http://localhost:${PORTS[catalog]}/api/reviews/count"       || echo 0)"
  printf '  professionals %s/%s\n  reviews       %s/%s\n' "$got_pro" "$expect_pro" "$got_rev" "$expect_rev"
  [[ "$got_pro" == "$expect_pro" && "$got_rev" == "$expect_rev" ]] \
    || die "seed counts do not match $SEED_FILE"

  # The rule the whole design turns on: a rating must equal the average of its own reviews.
  # Cheap to check, and the only check here that would catch a broken professional_rating view.
  local ref rating avg
  ref="$(jq -r '.professionals[0].ref' "$SEED_FILE")"
  rating="$(curl -fsS "http://localhost:${PORTS[catalog]}/api/professionals/$ref" | jq -r '.card.rating')"
  avg="$(jq -r --arg r "$ref" '[.reviews[]|select(.professionalRef==$r)|.stars] | (add/length*10|round)/10' "$SEED_FILE")"
  printf '  %s rating    %s (seed average %s)\n' "$ref" "$rating" "$avg"
  [[ "$rating" == "$avg" ]] || die "derived rating $rating disagrees with the seed's own reviews ($avg)"
  ok "seed loaded, counts and derived rating consistent"
}

banner() {
  cat <<EOF

$c_b HealthConnect Marketplace — dev estate up$c_reset
  Gateway API      http://localhost:${PORTS[gateway]}
  API docs         http://localhost:${PORTS[gateway]}/swagger-ui/index.html
  Consul UI        http://localhost:${CONSUL_PORT}
  Kafka bootstrap  localhost:9092
  Profiles         $PROFILES
  Seed             $SEED_FILE

$c_dim  The prototype at docs/Abofonsa_BridgeCare_Marketplace.html is a CLOSED demo: it has no
  fetch calls and no API_BASE hook, so it cannot be pointed at this estate. Driving it from the
  live API is unbuilt work, not a configuration step.$c_reset

EOF
}

# --------------------------------------------------------------------- router --
case "$COMMAND" in
  up)
    preflight
    (( DO_CLEAN )) && { warn "--clean: removing volumes"; compose down -v --remove-orphans || true; }
    (( DO_BUILD )) && build
    infra_up
    apps_up
    verify_seed
    banner
    ;;
  reseed)
    preflight
    step "Reseed"
    for s in "${SERVICES[@]}"; do
      [[ $s == gateway ]] && continue
      log "truncating + reloading $s"
      curl -fsS -X POST "http://localhost:${PORTS[$s]}/management/healthconnect/reseed" \
        -H 'Content-Type: application/json' >/dev/null \
        || die "reseed endpoint refused on $s (is it running with test,dev?)"
    done
    verify_seed
    ;;
  restart) preflight; compose restart "${SERVICES[@]}"; for s in "${SERVICES[@]}"; do
             wait_http "$s" "http://localhost:${PORTS[$s]}/management/health" "$TIMEOUT" || true; done ;;
  status)  compose ps ;;
  logs)    compose logs -f --tail=120 "${SERVICES[@]}" ;;
  down)
    if (( DO_CLEAN )); then warn "removing containers AND volumes"; compose down -v --remove-orphans;
    else compose down --remove-orphans; fi
    ok "stopped" ;;
  *) die "unknown command '$COMMAND' (up | reseed | restart | status | logs | down)" ;;
esac
