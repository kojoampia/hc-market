#!/usr/bin/env bash
# ==============================================================================
#  HealthConnect Marketplace — dev / test deployment
#
#  Brings up the whole microservice estate locally: MongoDB for the gateway, one PostgreSQL per
#  domain service, the gateway and the four domain services, then loads demo/seed-data.json through
#  the test,dev seed loader.
#
#  IT DOES NOT START A BROKER OR A CONSUL, and will not. Both live once, in hc-infra, and every
#  stack in this estate points at them by container name over the shared `hcnet` network. Start
#  that first — this script checks it and refuses to continue without it:
#
#      cd ~/webroot/01-healthconnect/hc-infra && ./startup.sh
#
#  Usage:
#     ./deploy-dev.sh up                      # build, start everything, seed
#     ./deploy-dev.sh up --no-build           # start from existing images
#     ./deploy-dev.sh up --services catalog,booking
#     ./deploy-dev.sh reseed                  # wipe + reload seed-data.json only, ALL seeded services
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
#     --force             Allow `reseed --services <subset>` — see below before you use it
#     --timeout <secs>    Per-service health gate   (default: 180)
#
#  RESEED IS ALL-OR-NOTHING BY DEFAULT (decisions.md D48). Each seeded service shifts every date it
#  writes by `today - $meta.demoToday`, computed when that service seeds, and the four are supposed to
#  agree. `reseed --services catalog` reseeds catalog alone, so catalog is dated today and the other
#  three keep whatever day they were seeded on — days apart, not seconds. Nothing fails: no query
#  spans two services, so the estate simply stops being seed-exact against itself while every check
#  stays green. It is refused unless you pass --force, which is the fast loop when you are working on
#  one seeder and know the others are stale.
#
#  Layout note (decisions.md D6): the five apps are SIBLING DIRECTORIES of this script's parent,
#  each a standalone Maven project with its own ./mvnw — there is no aggregator pom and no Maven
#  reactor, exactly as in hc-admin, hc-patient and hc-professional.
#
#  Discovery is CONSUL (decisions.md D5), not the JHipster Registry. There is no service on 8761.
#  Consul REGISTERS these services; it does not route them — the gateway's routes are static, in
#  docker/docker-compose.dev.yml, exactly as production's are.
#
#  Compose service names are `dev-<service>`; the names below are the ones you type. The prefix
#  exists because compose publishes a service name as a DNS alias on every network it joins, and
#  `catalog` and `booking` are already claimed on hcnet by the quality stack.
# ==============================================================================
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"       # the workspace holding gateway/ catalog/ …
cd "$DEPLOY_DIR"

# --- deploy/.env, and why it has to be sourced rather than left to compose ----------------------
#
# `apps_up` tells the operator to keep HC_PRIVACY_PEPPER in deploy/.env, and until 2026-09-02 that
# instruction could not work: nothing here read the file, and compose auto-loads `.env` from the
# PROJECT DIRECTORY — which for `-f deploy/docker/docker-compose.dev.yml` is deploy/docker/, not
# deploy/. So an operator who did exactly as they were told still hit the compose file's `:?` and
# concluded the script was broken, which is the worst kind of documentation defect: it is followed.
#
# Sourced here, before the defaults, so a value in it can also override the published ports and the
# shared-plane names below. `set -a` exports every assignment, because compose interpolates from the
# environment and an unexported shell variable is invisible to it. Same shape as
# hc-patient/run-local.sh.
#
# It is NOT an error for the file to be absent — every variable in it has another source, and the
# `:?` checks further down still name what is missing.
if [[ -f "$DEPLOY_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091  # operator-supplied, not in the repository
  . "$DEPLOY_DIR/.env"
  set +a
  # The colour helpers are defined further down, after the defaults this file may change.
  printf '▸ sourced %s\n' "$DEPLOY_DIR/.env"
fi

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
FORCE=0
TIMEOUT=180
# Java 25 needs a JDK with a compiler. /usr/lib/jvm/java-25-openjdk-amd64 is a JRE and its failure
# mode is an incremental build that silently passes — see the workspace guide.
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/jdk-25.0.2-oracle-x64}"

# "$DEPLOY_DIR/…", not "$0": this script cd's into DEPLOY_DIR above, so a relative invocation
# (./deploy/deploy-dev.sh --help, which is how the header itself spells it) leaves $0 pointing at a
# path that no longer resolves, and --help fails with a sed error instead of printing the help.
SELF="$DEPLOY_DIR/$(basename "${BASH_SOURCE[0]}")"
case "${1:-}" in -h|--help) sed -n '2,52p' "$SELF"; exit 0 ;; esac
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
export HC_GATEWAY_PORT="${PORTS[gateway]}" HC_CATALOG_PORT="${PORTS[catalog]}" \
       HC_BOOKING_PORT="${PORTS[booking]}" HC_MESSAGING_PORT="${PORTS[messaging]}" \
       HC_PAYOUT_PORT="${PORTS[payout]}"

# The shared infrastructure plane — hc-infra, not this stack. Addressed by CONTAINER NAME, which is
# what the applications use over hcnet; the published port is only for the banner and for anything
# on the host that wants the UI. There is no HC_KAFKA_PORT and no HC_CONSUL_PORT here any more:
# this stack publishes neither, because it runs neither.
SHARED_NETWORK="${HC_SHARED_NETWORK:-hcnet}"
SHARED_CONSUL="${HC_SHARED_CONSUL:-hc-shared-quality-consul}"
SHARED_KAFKA="${HC_SHARED_KAFKA:-hc-shared-quality-kafka}"
SHARED_CONSUL_UI_PORT="${HC_SHARED_CONSUL_UI_PORT:-18510}"
# Topics are prefixed so this estate cannot consume quality's events, or be consumed by it
# (decisions.md D29). MUST match HEALTHCONNECT_TOPICS_PREFIX in docker/docker-compose.dev.yml —
# create one set and configure another and the apps sit on topics nobody publishes to, silently.
# `-` and not `:-`: an EXPLICIT empty must stay empty, because that is the documented
# escape hatch for reproducing the crossed-events state, and `:-` would silently
# substitute the default for it and make the warning below unreachable.
TOPIC_PREFIX="${HC_TOPIC_PREFIX-dev.}"
SHARED_INFRA_DIR="${HC_SHARED_INFRA_DIR:-$HOME/webroot/01-healthconnect/hc-infra}"
export HC_SHARED_NETWORK="$SHARED_NETWORK" HC_SHARED_CONSUL="$SHARED_CONSUL" \
       HC_SHARED_KAFKA="$SHARED_KAFKA" HC_TOPIC_PREFIX="$TOPIC_PREFIX"

# Compose service names carry a `dev-` prefix; the CLI names do not. See the header for why.
compose_name() { printf 'dev-%s' "$1"; }

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
    --force)     FORCE=1; shift ;;
    --timeout)   TIMEOUT="$2"; shift 2 ;;
    -h|--help)   sed -n '2,52p' "$SELF"; exit 0 ;;
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

  shared_plane
}

# The broker and Consul are hc-infra's, on a network this stack declares external. Compose's own
# error for a missing external network names the network and nothing else, and the error for a
# missing broker is no error at all — the apps start, serve, report healthy, and everything they
# publish goes nowhere. So all three are checked here, by name, with the fix printed.
running() { [[ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" == "true" ]]; }
shared_plane() {
  local fix="start it with:  (cd $SHARED_INFRA_DIR && ./startup.sh)"
  docker network inspect "$SHARED_NETWORK" >/dev/null 2>&1 \
    || die "the shared network '$SHARED_NETWORK' does not exist — $fix"
  running "$SHARED_CONSUL" || die "$SHARED_CONSUL is not running — $fix"
  running "$SHARED_KAFKA"  || die "$SHARED_KAFKA is not running — $fix"

  # A leader, not merely an answering agent: Consul serves /v1/status/leader and `consul members`
  # before it has elected one, and every KV read fails with "No cluster leader" until it does.
  docker exec "$SHARED_CONSUL" consul operator raft list-peers >/dev/null 2>&1 \
    || die "$SHARED_CONSUL has no leader yet — wait, or $fix"
  docker exec "$SHARED_KAFKA" /opt/kafka/bin/kafka-broker-api-versions.sh \
    --bootstrap-server localhost:9092 >/dev/null 2>&1 \
    || die "$SHARED_KAFKA is not answering — wait, or $fix"
  ok "shared plane: $SHARED_CONSUL (leader elected), $SHARED_KAFKA on $SHARED_NETWORK"

  # One bus, two estates — separated by the topic prefix since decisions.md D29, so events no
  # longer cross. This stays as a note rather than a warning because the separation depends on a
  # value that can be cleared: run with HC_TOPIC_PREFIX='' beside a live quality stack and both
  # receive everything either publishes, which is precisely the state D29 closed.
  if running hc-market-quality-booking; then
    if [[ -z "$TOPIC_PREFIX" ]]; then
      warn "the QUALITY stack is running and HC_TOPIC_PREFIX is EMPTY — the two estates will consume"
      warn "each other's events. Unset it to take the 'dev.' default, or stop quality."
    else
      log "quality is also running; separated by the '${TOPIC_PREFIX}' topic prefix"
    fi
  fi
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

# Databases only. The broker and Consul are already up — preflight refused to get this far
# otherwise — and neither is a service in this project any more.
infra_up() {
  step "Infrastructure"
  local dbs=()
  for s in "${SERVICES[@]}"; do dbs+=("${s}-db"); done
  compose up -d "${dbs[@]}"
  ok "databases started"

  # Topics on the SHARED broker, via docker exec rather than `compose exec`: it is not this
  # project's container. --if-not-exists throughout, so topics the quality stack or another product
  # already created are left exactly as they are — this adds, it never redefines.
  log "ensuring topics on $SHARED_KAFKA (prefix '${TOPIC_PREFIX}')"
  for t in booking.requested booking.accepted booking.declined booking.cancelled \
           booking.completed review.published payout.settled notification.raised \
           dispute.resolved; do
    docker exec "$SHARED_KAFKA" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
      --create --if-not-exists --topic "${TOPIC_PREFIX}healthconnect.$t" --partitions 3 --replication-factor 1 >/dev/null
  done
  ok "9 topics present"
}

apps_up() {
  step "Services"
  export SPRING_PROFILES_ACTIVE="$PROFILES"
  SEED_HOST_PATH="$(dirname "$SEED_FILE")"
  export SEED_HOST_PATH
  : "${JWT_BASE64_SECRET:?set JWT_BASE64_SECRET (one key across the estate — see the workspace guide)}"
  export JWT_BASE64_SECRET
  # The erasure pepper (decisions.md D35). Required, not generated: one value across booking,
  # catalog and messaging, and nothing re-keys aliases already written, so a value that changes
  # between runs leaves earlier erasures unreconcilable. Generate one once and keep it in deploy/.env,
  # which this script sources at startup — see the note at the top, and note that compose does NOT
  # pick that file up by itself, because its project directory is deploy/docker/:
  #     echo "HC_PRIVACY_PEPPER=$(head -c 32 /dev/urandom | base64 -w0)" >> deploy/.env
  : "${HC_PRIVACY_PEPPER:?set HC_PRIVACY_PEPPER (the erasure pepper — see decisions.md D35)}"
  export HC_PRIVACY_PEPPER
  local names=(); for s in "${SERVICES[@]}"; do names+=("$(compose_name "$s")"); done
  compose up -d "${names[@]}"
  local failed=0
  for s in "${SERVICES[@]}"; do
    wait_http "$s" "http://localhost:${PORTS[$s]}/management/health" "$TIMEOUT" || failed=1
  done
  (( failed )) && { warn "some services did not come up — showing the last 40 lines"; compose logs --tail=40; die "startup failed"; }
  ok "all services healthy"
}

# Compares what the API reports against what the seed file contains. A mismatch fails the run —
# the same integrity discipline the prototype applied to its charts, applied to the loader.
#
# --- SEED-EXACT AND SEED-PLUS-ACTIVITY ARE TWO DIFFERENT ASSERTIONS ------------------------------
#
# This function used to assert `reviews == the seed file's count` and then compare the API's rating
# against an average computed FROM THE SEED FILE. Both are true only of an estate nothing has
# exercised — and deploy/verify-cycle.sh exists to exercise this one. It books, accepts, completes
# and REVIEWS, and a review cannot be deleted (spec §7, review integrity is one-directional). So a
# successful cycle against a dev estate made the next `deploy-dev.sh up` — without --clean — die at
# `seed counts do not match`, or past that at `derived rating 4.3 disagrees with the seed's own
# reviews (4.7)`. One tool reporting the other tool's success as a fault, which is exactly the
# defect the quality run of 1eadc7a found in quality/startup.sh --verify, one script over. It was
# unreachable until verify-cycle.sh could address more than one estate; making it portable made it
# reachable here.
#
# So the counts are split by whether anything in this repository writes to them, as --verify's are:
#
#   SEED-EXACT      professionals. Nothing in this repository creates one, so drift is a real fault
#                   and the assertion stays exact.
#   SEED + ACTIVITY reviews. At least the seed's figure, with the surplus PRINTED rather than
#                   swallowed, so a number that has moved is still on the screen.
#
# And the rating check is asked of the API's OWN reviews rather than of the seed file, which is
# both stronger and independent of how much the estate has been exercised: `professional_rating` is
# a view over the review table, and the review endpoint reads that same table by another road. That
# is "derived, never stored" asserted directly. Its arithmetic is integer tenths on purpose — see
# the note where it is computed.
verify_seed() {
  step "Seed verification"
  local api="http://localhost:${PORTS[catalog]}"
  local expect_pro expect_rev
  expect_pro="$(jq '.professionals|length' "$SEED_FILE")"
  expect_rev="$(jq '.reviews|length'       "$SEED_FILE")"
  local got_pro got_rev
  got_pro="$(curl -fsS "$api/api/professionals/count" || echo 0)"
  got_rev="$(curl -fsS "$api/api/reviews/count"       || echo 0)"
  printf '  professionals %s/%s\n' "$got_pro" "$expect_pro"
  [[ "$got_pro" == "$expect_pro" ]] || die "professionals: got $got_pro, $SEED_FILE has $expect_pro"
  [[ "$got_rev" =~ ^[0-9]+$ ]] || die "reviews: the API answered '$got_rev', which is not a number"
  if (( got_rev > expect_rev )); then
    printf '  reviews       %s (seed %s + %s recorded)\n' "$got_rev" "$expect_rev" "$(( got_rev - expect_rev ))"
  elif (( got_rev == expect_rev )); then
    printf '  reviews       %s (seed-exact)\n' "$got_rev"
  else
    die "reviews: got $got_rev, fewer than the $expect_rev in $SEED_FILE"
  fi

  # The rule the whole design turns on: a rating must equal the average of its own reviews, as the
  # API serves them. The only check here that would catch a broken professional_rating view.
  local ref card rating review_count
  ref="$(jq -r '.professionals[0].ref' "$SEED_FILE")"
  card="$(curl -fsS "$api/api/professionals/$ref" || echo '{}')"
  rating="$(jq -r '.card.rating // empty' <<<"$card")"
  review_count="$(jq -r '.card.reviewCount // empty' <<<"$card")"
  [[ -n "$rating" && -n "$review_count" ]] || die "$ref has no rating or reviewCount — is the professional_rating view there?"

  # Paged, not one big page: /api/professionals/{ref}/reviews passes `size` straight through with no
  # cap while reviewCount comes from the uncapped view, so asking for one page of 200 and averaging
  # whatever came back reports a plausible wrong number the day the page truncates. A page that
  # cannot be completed is refused rather than averaged.
  # Every jq result is defaulted before it reaches an arithmetic context: a body that is not JSON at
  # all — a sibling's login page on a stolen port — makes jq print nothing, and `(( got + ))` is a
  # shell syntax error rather than a verification failure. The refusal below is the intended answer.
  local page=0 got=0 sum=0 total="" body chunk add
  while (( page < 50 )); do
    body="$(curl -fsS "$api/api/professionals/$ref/reviews?page=$page&size=200" || echo '{}')"
    chunk="$(jq -r '(.content // []) | length'                <<<"$body" 2>/dev/null)"
    total="$(jq -r '.totalElements // empty'                  <<<"$body" 2>/dev/null)"
    add="$(  jq -r '[(.content // [])[].stars] | add // 0'    <<<"$body" 2>/dev/null)"
    sum=$(( sum + ${add:-0} ))
    got=$(( got + ${chunk:-0} ))
    page=$(( page + 1 ))
    [[ "$total" =~ ^[0-9]+$ ]] || break
    (( ${chunk:-0} == 0 || got >= total )) && break
  done
  [[ "$total" =~ ^[0-9]+$ && "$got" == "$total" ]] \
    || die "$ref: served $got of ${total:-?} reviews — refusing to average a truncated page"
  [[ "$review_count" == "$total" ]] \
    || die "$ref: the view counts $review_count reviews, the review endpoint serves $total"

  # Integer tenths, half away from zero, which is what Postgres's round(numeric, 1) does inside the
  # view. Not `add/length*10|round`: jq's numbers are doubles, and an average that is exactly x.x5
  # in decimal is not exactly representable — 87/20 becomes 4.34999999999999964, rounds DOWN to 4.3,
  # and disagrees with the view's 4.4 against an estate that is entirely correct. Same class of
  # defect as the half-to-even round() the quality box's own check was caught with.
  #   round(10*sum/total) = floor((2*(10*sum) + total) / (2*total))   for positive integers
  local want_tenths got_tenths
  want_tenths=$(( (20 * sum + total) / (2 * total) ))
  got_tenths="$(jq -r '(.card.rating * 10 | round)' <<<"$card")"
  printf '  %s rating    %s over %s reviews (their average is %s.%s)\n' \
    "$ref" "$rating" "$review_count" "$(( want_tenths / 10 ))" "$(( want_tenths % 10 ))"
  [[ "$got_tenths" == "$want_tenths" ]] \
    || die "derived rating $rating disagrees with the $total reviews the API serves for $ref"
  ok "seed loaded, counts and derived rating consistent"
}

banner() {
  cat <<EOF

$c_b HealthConnect Marketplace — dev estate up$c_reset
  Gateway API      http://localhost:${PORTS[gateway]}
  API docs         http://localhost:${PORTS[gateway]}/swagger-ui/index.html
  Profiles         $PROFILES
  Seed             $SEED_FILE

$c_dim  Shared plane (hc-infra, not this stack):$c_reset
  Consul UI        http://localhost:${SHARED_CONSUL_UI_PORT}/ui  — services register as hc-market-dev-*
  Kafka            $SHARED_KAFKA:9092 from a container; localhost:19192 from this host

$c_dim  The prototype at docs/Abofonsa_BridgeCare_Marketplace.html can drive this estate (D29).
  Open it with ?api=http://localhost:${PORTS[gateway]} — reads go live, writes stay in memory.
  Without a query string it is still the closed demo, which is what the seed is extracted from.
  Check it agrees:  node deploy/verify-prototype-live.mjs http://localhost:${PORTS[gateway]}$c_reset

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
    # Ahead of preflight deliberately: this refusal needs no docker, no jq and no shared plane, and a
    # refusal that first spends thirty seconds proving the estate is healthy reads as a broken estate.
    #
    # A PARTIAL reseed dates one service to today and leaves the others on whatever day they were
    # seeded — decisions.md D48. Every seeded date in a service moves by ONE number, `today -
    # $meta.demoToday`, computed when that service seeds; the four are supposed to arrive at the same
    # one. D48 accepted the residual that they compute it independently, and it argued that from `up`,
    # where the four are started by a single `compose up` and are seconds apart. `--services` makes it
    # something else entirely: `reseed --services catalog` reseeds catalog ALONE, and the gap is then
    # however many days have passed since the others were seeded — three for an estate left up over a
    # weekend.
    #
    # Nothing fails when it happens, which is the whole reason for a refusal here rather than a note
    # somewhere. Booking never asks catalog about a slot and payout's aggregates read only payout's own
    # ledger, so a three-day gap means every seeded booking falls on a day the professional's calendar
    # shows no slot, and `verify_seed` — which counts professionals and reviews — stays green.
    #
    # --force is the escape hatch, because reseeding one service IS the fast loop when you are working
    # on that service's seeder. It is a refusal, not a prohibition.
    seeded=(); for s in "${SERVICES[@]}"; do [[ $s == gateway ]] || seeded+=("$s"); done
    all_seeded=(); for s in "${ALL_SERVICES[@]}"; do [[ $s == gateway ]] || all_seeded+=("$s"); done
    if (( ${#seeded[@]} < ${#all_seeded[@]} )) && (( ! FORCE )); then
      die "refusing to reseed a subset (${seeded[*]}) — the others keep the day they were seeded on, and
    a seeded estate that disagrees with itself about what day it is fails nothing and is caught by
    nothing (decisions.md D48). Reseed all of them (drop --services), or pass --force if you meant it."
    fi
    # An `if`, not a `&&` chain: this script runs under `set -e` with an ERR trap, so a chain whose
    # first test is false returns 1 and kills the run at the line that was supposed to say nothing.
    if (( FORCE )) && (( ${#seeded[@]} < ${#all_seeded[@]} )); then
      # Name the ones left BEHIND, not the whole list — the operator already knows what they asked
      # for, and what they need on the screen afterwards is which services are now stale.
      stale=(); for s in "${all_seeded[@]}"; do [[ " ${seeded[*]} " == *" $s "* ]] || stale+=("$s"); done
      warn "--force: reseeding ${seeded[*]} only — ${stale[*]} keep the day they were seeded on (D48)"
    fi

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
  restart) preflight
           names=(); for s in "${SERVICES[@]}"; do names+=("$(compose_name "$s")"); done
           compose restart "${names[@]}"; for s in "${SERVICES[@]}"; do
             wait_http "$s" "http://localhost:${PORTS[$s]}/management/health" "$TIMEOUT" || true; done ;;
  status)  compose ps ;;
  logs)    names=(); for s in "${SERVICES[@]}"; do names+=("$(compose_name "$s")"); done
           compose logs -f --tail=120 "${names[@]}" ;;
  # `down` cannot take the shared plane with it and does not try: hc-infra's Consul and Kafka are
  # not services in this project, and hcnet is external, which is exactly why it is declared that
  # way. --remove-orphans additionally sweeps the pre-2026-08-31 containers — this stack's own
  # broker and Consul, and the un-prefixed service containers — which is how you migrate a running
  # estate onto this file.
  down)
    if (( DO_CLEAN )); then warn "removing containers AND volumes"; compose down -v --remove-orphans;
    else compose down --remove-orphans; fi
    ok "stopped" ;;
  *) die "unknown command '$COMMAND' (up | reseed | restart | status | logs | down)" ;;
esac
