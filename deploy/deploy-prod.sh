#!/usr/bin/env bash
# ==============================================================================
#  HealthConnect Marketplace — production deployment
#
#  Builds immutable images, pushes them to a registry CHANNEL, then rolls the
#  Docker Compose stack on the production host over SSH with a health gate and
#  automatic rollback to the previously deployed tag.
#
#  Channels:
#     (default)          docker.jojoaddison.net/healthconnect/<service>:<tag>
#     --channel github   ghcr.io/<owner>/hc-market-<service>:<tag>
#
#  hc-market-, NOT healthconnect-. This header said healthconnect- until 2026-08-31 while the code
#  produced hc-market- (see image_for and decisions.md D13), and sync-appendices.sh could not catch
#  it: the spec appendix faithfully reproduces this header, so both copies were wrong together. A
#  name that cannot exist sends whoever reads it hunting for a registry fault instead of a tag.
#
#  Usage:
#     ./deploy-prod.sh --tag 1.4.0
#     ./deploy-prod.sh --channel github --tag 1.4.0
#     ./deploy-prod.sh --tag 1.4.0 --services catalog,booking
#     ./deploy-prod.sh --rollback                  # back to the previous tag
#     ./deploy-prod.sh --tag 1.4.0 --dry-run       # print, change nothing
#
#  Options:
#     --channel <name>   default | github            (default: default)
#     --tag <version>    Image tag                   (default: from pom.xml)
#     --host <target>    SSH target                  (default: $HC_PROD_HOST)
#     --path <dir>       Remote stack directory      (default: /srv/healthconnect)
#     --services <list>  Comma-separated subset      (default: all)
#     --build            Rebuild and re-push at this tag, OVERWRITING what CI published.
#                        Not the default — see DO_BUILD below.
#     --no-build         Accepted and now the default; kept so existing invocations still work
#     --no-push          Build and deploy without pushing (host must reach them)
#     --rollback         Redeploy the previous tag recorded on the host
#     --dry-run          Print every command instead of running it
#     --yes              Skip the confirmation prompt (for CI)
#
#  Required environment (on the machine you run this from):
#     HC_PROD_HOST       e.g. deploy@app-01.jojoaddison.net
#     HC_REGISTRY_USER / HC_REGISTRY_TOKEN     for docker.jojoaddison.net
#     GHCR_OWNER / GHCR_TOKEN                  for the github channel
#
#  Required ON THE HOST, in $REMOTE_PATH/secrets.env, and NOT here. ELEVEN values, all of them `:?`
#  in docker-compose.prod.yml, all of them checked in preflight by name before the stack is touched:
#     JWT_BASE64_SECRET       this ESTATE's signing key   (decisions.md D37 — NOT the platform key)
#     HC_PRIVACY_PEPPER       the erasure pepper           (decisions.md D35)
#     HC_GATEWAY_MONGODB_URI  the gateway's user store
#     HC_{CATALOG,BOOKING,MESSAGING,PAYOUT}_DB_URL       the four PostgreSQL instances
#     HC_{CATALOG,BOOKING,MESSAGING,PAYOUT}_DB_PASSWORD  and their credentials
#
#  The template, with a generation command beside every line and a real value on none of them, is
#  deploy/prod-server/secrets.env.example. The five stores those last nine address are declared in
#  deploy/prod-server/compose.yml and installed on the host once — this script deploys applications
#  and never provisions a database.
#
#  Optional in the same file, and NOT a secret:
#     HC_DPC_REGISTRATION  the Data Protection Commission registration number (decisions.md D42)
#
#  It sits beside the two secrets for a different reason than they do: not because publishing it
#  would be dangerous, but because it is a real identifier belonging to a real organisation and this
#  repository is public, so it is not ours to commit on their behalf. Absent, the stack starts
#  normally, booking logs a warning and GET /api/desk/privacy reports null — which is the honest
#  answer and is why this is not a `:?` variable. The retention periods need nothing here at all:
#  counsel's ratified figures are the committed fallback (HC_RETENTION_FINANCIAL_DAYS and its two
#  siblings override them only if a deployment has to be corrected without cutting a release).
#
#  Those eleven are the stack's long-lived values and this script never sees them. It generates .env
#  on every deploy and overwrites what was there, so anything kept in .env survives exactly until
#  the next deploy — which is why docker-compose.prod.yml's two `:?` variables lived in a file that
#  could not hold them, and why every production `up` would have died on
#  "JWT_BASE64_SECRET: platform JWT secret is required" the first time anyone ran one. The compose
#  file's own comment beside HEALTHCONNECT_PRIVACY_PEPPER said the pepper "belongs with the
#  platform's long-lived secrets, not in a per-deploy .env that deploy-prod.sh regenerates"; this
#  is the file that makes that sentence true.
#
#  secrets.env is created once, out of band, and never written by this script. Create it ON the
#  server rather than piping it there, so no value ever exists in a local shell history:
#
#      ssh $HC_PROD_HOST
#      mkdir -p /srv/healthconnect && cd /srv/healthconnect
#      umask 077 && cat > secrets.env      # paste all eleven, filled in, then Ctrl-D
#      chmod 600 secrets.env
#
#  JWT_BASE64_SECRET IS GENERATED FRESH — `head -c 64 /dev/urandom | base64 -w0` — AND IS NOT THE
#  PLATFORM KEY. This block said "<the platform key, from ~/webroot/01-healthconnect/.env>" until
#  2026-09-05, and that file holds the key hc-admin, hc-patient and hc-professional share; hc-market
#  is deliberately not in that set (decisions.md D37). Nothing would have failed — HS512 does not
#  care which random bytes it is — while these five services acquired the ability to mint tokens the
#  other three products accept, and an hc-admin token acquired authority here.
#
#  Full template, with the other ten and a generation command for each:
#      deploy/prod-server/secrets.env.example
#
#  Both files are passed to compose explicitly (--env-file .env --env-file secrets.env), because
#  naming any --env-file stops compose auto-loading .env, and because the `:?` checks are evaluated
#  at INTERPOLATION time — so pull, up, exec and rollback all need both or none of them work.
# ==============================================================================
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Absolute, because this script cd's into DEPLOY_DIR below: a relative $0 stops resolving after that,
# and --help then fails with a sed error rather than printing the header an operator needs before a
# first deploy — which now includes how to create secrets.env.
SELF="$DEPLOY_DIR/$(basename "${BASH_SOURCE[0]}")"
ROOT_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"       # holds gateway/ catalog/ booking/ messaging/ payout/
cd "$DEPLOY_DIR"
# Java 25 needs a JDK with a compiler. java-25-openjdk-amd64 is a JRE and fails silently on an
# incremental build -- see the workspace guide.
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/jdk-25.0.2-oracle-x64}"

# ------------------------------------------------------------------ defaults --
CHANNEL="default"
TAG=""
HOST="${HC_PROD_HOST:-}"
REMOTE_PATH="/srv/healthconnect"
ALL_SERVICES=(gateway catalog booking messaging payout)
SERVICES=("${ALL_SERVICES[@]}")
# DEPLOY WHAT CI PUBLISHED. This defaulted to 1 until 2026-08-31, which meant an ordinary production
# deploy rebuilt all five services on the operator's workstation and pushed them over the images CI
# had already built and — per D14's verify job — proved existed at that SHA. The tag stayed the same
# while the bytes behind it changed, which is exactly what D13 set out to prevent: images are built
# by CI, tagged by commit, and a deploy chooses one.
#
# `--build` opts back in for the case the flag exists for: an unreleased tag CI has never seen.
DO_BUILD=0
DO_PUSH=1
DO_ROLLBACK=0
DRY_RUN=0
ASSUME_YES=0
COMPOSE_TEMPLATE="$DEPLOY_DIR/docker/docker-compose.prod.yml"
HEALTH_TIMEOUT=240
# The host's long-lived secrets, beside the generated .env and deliberately not part of it. See the
# header. Never read, written or printed by this script — its whole contribution is to insist the
# file is there and to hand its name to compose.
SECRETS_FILE="secrets.env"
SECRET_KEYS=(JWT_BASE64_SECRET HC_PRIVACY_PEPPER)
# ...and the nine connection values that are ALSO `:?` in docker-compose.prod.yml and were also
# emitted by nothing.
#
# THE PREFLIGHT CHECKED TWO OF THE ELEVEN UNTIL 2026-09-05, which is the same defect it was built to
# fix, nine keys wide. A host whose secrets.env held the two secrets passed preflight, had .env
# overwritten and .env.previous rotated, and then died at `up` on
# "catalog datasource url is required" — a stack half-rolled over a variable nothing in the pipeline
# ever supplied. It was invisible because no production database was declared anywhere in this
# repository until deploy/prod-server/ existed, so there was no obvious place for these to come from
# and nothing noticed they came from nowhere.
#
# Kept as a second array rather than folded into SECRET_KEYS because they are not secrets and the
# messages differ: the URLs are topology and can be reconstructed from prod-server/compose.yml, while
# a lost pepper cannot be reconstructed from anything. Presence and non-emptiness only, for both —
# the values stay on the host, nothing here reads them, so nothing here can print them.
CONNECTION_KEYS=(
  HC_GATEWAY_MONGODB_URI
  HC_CATALOG_DB_URL   HC_CATALOG_DB_PASSWORD
  HC_BOOKING_DB_URL   HC_BOOKING_DB_PASSWORD
  HC_MESSAGING_DB_URL HC_MESSAGING_DB_PASSWORD
  HC_PAYOUT_DB_URL    HC_PAYOUT_DB_PASSWORD
)
# Every remote compose invocation goes through this. Two --env-file arguments, in this order: the
# later file wins, and the generated .env must never be able to override a secret. Naming any
# --env-file disables compose's automatic .env loading, so both have to be listed.
REMOTE_COMPOSE="docker compose --env-file .env --env-file $SECRETS_FILE"

c_reset=$'\033[0m'; c_b=$'\033[1m'; c_dim=$'\033[2m'
c_ok=$'\033[32m'; c_warn=$'\033[33m'; c_err=$'\033[31m'; c_info=$'\033[36m'
log()  { printf '%s▸%s %s\n' "$c_info" "$c_reset" "$*"; }
ok()   { printf '%s✓%s %s\n' "$c_ok" "$c_reset" "$*"; }
warn() { printf '%s!%s %s\n' "$c_warn" "$c_reset" "$*"; }
die()  { printf '%s✗ %s%s\n' "$c_err" "$*" "$c_reset" >&2; exit 1; }
# Deliberately NOT a tick. Under --dry-run the checks below are not performed, and the output must
# not be readable as though they were.
skipped() { printf '%s  ○ [dry-run] %s%s\n' "$c_dim" "$*" "$c_reset"; }
step() { printf '\n%s%s%s\n' "$c_b" "$*" "$c_reset"; }
run()  { if (( DRY_RUN )); then printf '%s  [dry-run] %s%s\n' "$c_dim" "$*" "$c_reset"; else "$@"; fi; }
trap 'die "failed at line $LINENO: ${BASH_COMMAND}"' ERR

# ---------------------------------------------------------------- arg parsing --
while [[ $# -gt 0 ]]; do
  case "$1" in
    --channel)   CHANNEL="$2"; shift 2 ;;
    --tag)       TAG="$2"; shift 2 ;;
    --host)      HOST="$2"; shift 2 ;;
    --path)      REMOTE_PATH="$2"; shift 2 ;;
    --services)  IFS=',' read -r -a SERVICES <<< "$2"; shift 2 ;;
    --build)     DO_BUILD=1; shift ;;
    # Kept as an accepted no-op: it is documented, it is in muscle memory, and silently rejecting it
    # would fail a deploy for asking for what is now the default.
    --no-build)  DO_BUILD=0; shift ;;
    --no-push)   DO_PUSH=0; shift ;;
    --rollback)  DO_ROLLBACK=1; shift ;;
    --dry-run)   DRY_RUN=1; shift ;;
    --yes|-y)    ASSUME_YES=1; shift ;;
    # The whole header block, COMPUTED rather than numbered: skip the shebang and the opening rule,
    # print until the closing one.
    #
    # It was `sed -n '2,66p'` and the comment beside it said that was "the whole header block, up to
    # but not including its closing rule" — which had stopped being true. The header ran to line 78
    # by then, so --help printed everything EXCEPT lines 67-78, and lines 68-74 are the `cat >
    # secrets.env` command a first-time deployer has to run before deploying at all. That is exactly
    # the omission the comment was written to record having fixed, arriving a second time by the same
    # road: a hardcoded line number in a file that grows.
    -h|--help)   awk 'NR<=2 {next} /^# ={20,}$/ {exit} {print}' "$SELF"; exit 0 ;;
    *)           die "unknown option: $1 (try --help)" ;;
  esac
done

# ---------------------------------------------------------- channel resolution --
case "$CHANNEL" in
  default)
    REGISTRY_HOST="docker.jojoaddison.net"
    IMAGE_PREFIX="docker.jojoaddison.net/healthconnect"
    IMAGE_SEP="/"
    REGISTRY_USER="${HC_REGISTRY_USER:-}"
    REGISTRY_TOKEN="${HC_REGISTRY_TOKEN:-}"
    CRED_HINT="HC_REGISTRY_USER / HC_REGISTRY_TOKEN"
    ;;
  github|ghcr)
    CHANNEL="github"
    REGISTRY_HOST="ghcr.io"
    # kojoampia, not jojoaddison: that is the account the sibling packages live under
    # (ghcr.io/kojoampia/hc-admin-gateway and friends). See decisions.md D13.
    GHCR_OWNER="${GHCR_OWNER:-kojoampia}"
    # hc-market-<service>, not healthconnect-<service>. `healthconnect` is the PLATFORM's name and
    # four products share it; the sibling packages are all hc-<product>-<service>, and hc-market's
    # should sort beside them rather than under a prefix that says nothing about which product they
    # belong to.
    IMAGE_PREFIX="ghcr.io/${GHCR_OWNER}/hc-market"
    IMAGE_SEP="-"
    REGISTRY_USER="${GHCR_USER:-$GHCR_OWNER}"
    REGISTRY_TOKEN="${GHCR_TOKEN:-}"
    CRED_HINT="GHCR_OWNER / GHCR_TOKEN"
    ;;
  *) die "unknown channel '$CHANNEL' (default | github)" ;;
esac
image_for() { printf '%s%s%s:%s' "$IMAGE_PREFIX" "$IMAGE_SEP" "$1" "$2"; }

for s in "${SERVICES[@]}"; do
  [[ " ${ALL_SERVICES[*]} " == *" $s "* ]] || die "unknown service '$s' (known: ${ALL_SERVICES[*]})"
done

# The COMPOSE service names, which are not the names you type.
#
# docker-compose.prod.yml calls its services hc-market-<name> (decisions.md D28): compose publishes
# a service name as a DNS alias on every network it joins, and infranet is shared with three sibling
# products, so plain `gateway` and `catalog` there would be claiming aliases that may already belong
# to somebody else. The CLI keeps the short names — `--services catalog,booking` is unchanged — and
# everything handed to `docker compose` is mapped through here.
#
# Get this wrong and the symptom is not an error: `docker compose up -d gateway` on a file with no
# service called `gateway` fails loudly, but `docker compose pull` with no arguments would quietly
# pull everything. Mapped explicitly for that reason.
compose_name() { printf 'hc-market-%s' "$1"; }
compose_names() { local out=() n; for n in "${SERVICES[@]}"; do out+=("$(compose_name "$n")"); done; printf '%s' "${out[*]}"; }

# ------------------------------------------------------------------ preflight --
require() { command -v "$1" >/dev/null 2>&1 || die "$1 is required but not on PATH"; }
# What to tell an operator who is missing one of them. The pepper's advice is not the signing key's:
# a wrong signing key signs everybody out and can be corrected, while a wrong pepper is written into
# rows in place and nothing re-keys them (decisions.md D35).
secret_hint() {
  case "$1" in
    JWT_BASE64_SECRET)
      # THIS ADVICE WAS WRONG UNTIL 2026-09-05, and it was wrong in the direction that widens a blast
      # radius rather than the one that breaks a deploy. It said to take the key from
      # ~/webroot/01-healthconnect/.env, which is the PLATFORM key shared by hc-admin, hc-patient and
      # hc-professional — and hc-market is not in that set (decisions.md D37). Any service holding a
      # key can mint a token for any subject with any authority, so following the old hint would
      # silently have given hc-market's five services the ability to mint tokens the other three
      # products accept, and given an hc-admin token authority here. Nothing would have failed: HS512
      # does not care which random bytes it is, so the estate would have come up perfectly.
      printf 'ONE KEY FOR THIS ESTATE, GENERATED FRESH: `head -c 64 /dev/urandom | base64 -w0`. Do NOT copy it from ~/webroot/01-healthconnect/.env — that is the key hc-admin, hc-patient and hc-professional share, and hc-market is deliberately not in that set (decisions.md D37). Sharing it would let these five services mint tokens the other three products accept.' ;;
    HC_PRIVACY_PEPPER)
      printf 'The erasure pepper (decisions.md D35). If this host has never been deployed, generate one once with `head -c 32 /dev/urandom | base64 -w0` and keep it forever; if it HAS, the old value is the only correct one — a new pepper leaves every erased subject unrecognisable and nothing reports it.' ;;
    HC_GATEWAY_MONGODB_URI)
      printf 'mongodb://<user>:<pass>@hc-market-gateway-db:27017/healthconnectGateway?authSource=admin — the store declared in deploy/prod-server/compose.yml. Use a HEX password: base64 (+ / =) is not legal unescaped in a URI and the driver rejects the rest as an invalid host:port.' ;;
    HC_*_DB_URL)
      printf 'jdbc:postgresql://hc-market-<service>-db:5432/healthconnect<Service> — the stores declared in deploy/prod-server/compose.yml, reachable over hcmarketnet. See deploy/prod-server/secrets.env.example.' ;;
    HC_*_DB_PASSWORD)
      printf 'The SAME value the store reads in deploy/prod-server/compose.yml — written once in secrets.env and read by both compose projects, which is why they cannot drift. See deploy/prod-server/secrets.env.example.' ;;
    *) printf 'See the header, and deploy/prod-server/secrets.env.example.' ;;
  esac
}
java_major() {                       # robust: ignores "Picked up JAVA_TOOL_OPTIONS" noise
  local out major
  out="$("$JAVA_HOME/bin/java" -version 2>&1 || true)"
  major="$(printf '%s\n' "$out" | grep -E 'version "' | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  [[ "$major" =~ ^[0-9]+$ ]] || major=0
  printf '%s' "$major"
}
# There is no aggregator pom (decisions.md D6), so the version comes from the gateway's own pom.
# Every app is released together and shares a tag; if they ever diverge, pass --tag explicitly.
resolve_tag() {
  [[ -n "$TAG" ]] && return
  [[ -x "$ROOT_DIR/gateway/mvnw" ]] || die "no $ROOT_DIR/gateway/mvnw — pass --tag explicitly"
  TAG="$(cd "$ROOT_DIR/gateway" && ./mvnw -q -ntp -Dexec.executable=echo -Dexec.args='${project.version}' \
        --non-recursive exec:exec 2>/dev/null | tail -1 | tr -d '[:space:]')"
  [[ -n "$TAG" ]] || die "could not resolve the version — pass --tag explicitly"
  TAG="${TAG%-SNAPSHOT}"
}
preflight() {
  step "Preflight — channel '$CHANNEL' → $REGISTRY_HOST"
  require docker; require ssh; require git; require curl
  docker info >/dev/null 2>&1 || die "docker daemon is not reachable"
  [[ -n "$HOST" ]] || die "no target host — pass --host or export HC_PROD_HOST"
  [[ -f "$COMPOSE_TEMPLATE" ]] || die "missing $COMPOSE_TEMPLATE"

  # This workspace level is NOT a git repository -- each app is its own repo, and hc-market may
  # not be under version control at all. Probe the gateway repo rather than the cwd, and treat
  # "no repo" as a warning, not a failure.
  if git -C "$ROOT_DIR/gateway" rev-parse --git-dir >/dev/null 2>&1; then
    if [[ -n "$(git -C "$ROOT_DIR/gateway" status --porcelain 2>/dev/null)" ]]; then
      warn "gateway working tree is dirty -- the deployed image will not match any commit"
      (( ASSUME_YES )) || { read -r -p "  continue anyway? [y/N] " a; [[ "$a" == [yY] ]] || exit 1; }
    fi
    GIT_SHA="$(git -C "$ROOT_DIR/gateway" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  else
    warn "no git repository under $ROOT_DIR/gateway -- image provenance will read 'unknown'"
    GIT_SHA="unknown"
  fi

  if (( DO_PUSH )); then
    [[ -n "$REGISTRY_TOKEN" ]] || die "registry credentials missing — set $CRED_HINT"
    log "docker login $REGISTRY_HOST as $REGISTRY_USER"
    # A tick here used to print under --dry-run too, while the login it claims was skipped. That is
    # false confidence in the one command somebody runs BEFORE touching production: the output read
    # as though the credentials and the host had been checked when neither had been contacted. Both
    # of these now say plainly that they were skipped.
    if (( DRY_RUN )); then
      skipped "would authenticate to $REGISTRY_HOST as $REGISTRY_USER"
    else
      printf '%s' "$REGISTRY_TOKEN" \
        | docker login "$REGISTRY_HOST" -u "$REGISTRY_USER" --password-stdin >/dev/null \
        || die "registry login failed for $REGISTRY_HOST"
      ok "authenticated to $REGISTRY_HOST"
    fi
  fi

  log "checking ssh to $HOST"
  if (( DRY_RUN )); then
    skipped "would check ssh to $HOST — NOT contacted"
  else
    ssh -o BatchMode=yes -o ConnectTimeout=8 "$HOST" 'docker compose version >/dev/null' \
      || die "cannot reach $HOST over ssh, or docker compose v2 is missing there"
    ok "host reachable"
  fi

  # The two secrets docker-compose.prod.yml requires with `:?`. Checked HERE, before the stack is
  # touched, because the alternative is where this used to land: `docker compose up` on the host,
  # after .env has already been overwritten and .env.previous rotated, dying on
  # "platform JWT secret is required" — a stack half-rolled over a variable nothing in the pipeline
  # ever supplied. render_env has never emitted either of them and never will; they live in
  # secrets.env, which this script does not generate.
  #
  # Presence and non-emptiness only. The values stay on the host: nothing here reads them, so
  # nothing here can print them, and --dry-run cannot leak what it never fetched.
  log "checking $REMOTE_PATH/$SECRETS_FILE on $HOST"
  if (( DRY_RUN )); then
    skipped "would confirm $REMOTE_PATH/$SECRETS_FILE holds all ${#SECRET_KEYS[@]} secrets and ${#CONNECTION_KEYS[@]} connection values — NOT contacted"
    for v in "${SECRET_KEYS[@]}" "${CONNECTION_KEYS[@]}"; do skipped "  $v"; done
  else
    ssh -o BatchMode=yes "$HOST" "test -s '$REMOTE_PATH/$SECRETS_FILE'" \
      || die "$HOST:$REMOTE_PATH/$SECRETS_FILE is missing or empty. It holds the estate's long-lived secrets (${SECRET_KEYS[*]}) and the five stores' connection values, it is created once by hand, and this script deliberately never writes it — see the header for the exact command and deploy/prod-server/secrets.env.example for the template. Without it every service refuses to start on the compose file's own :? checks."
    for v in "${SECRET_KEYS[@]}" "${CONNECTION_KEYS[@]}"; do
      ssh -o BatchMode=yes "$HOST" "grep -qE '^[[:space:]]*$v=.' '$REMOTE_PATH/$SECRETS_FILE'" \
        || die "$v is not set in $HOST:$REMOTE_PATH/$SECRETS_FILE. $(secret_hint "$v")"
      ok "$v present"
    done
  fi

  # All three networks are declared `external: true`, so compose will not create them and `up` fails
  # outright if any is absent.
  #
  # TWO belong to the host, not to this stack: infranet carries Kafka and Consul
  # (~/webroot/00-infrastructure), monitoring carries the shared otel-collector
  # (~/webroot/02-monitoring). This comment used to say infranet carried "the databases" too, and it
  # never did — no compose file here declared a production database at all until
  # deploy/prod-server/ did.
  #
  # THE THIRD IS THIS PRODUCT'S OWN. hcmarketnet carries the five stores, is created by
  # deploy/prod-server/infra.sh, and is deliberately NOT infranet: three sibling products share that
  # one, and a database another product can resolve is what decisions.md D27 keeps off a shared
  # network. It is checked here rather than created here for the same reason as the other two — this
  # script deploys applications and does not provision a host.
  #
  # Checked here rather than discovered at `up`, because the tempting fix at that point is to
  # delete the network line -- and for `monitoring` that "fix" is silent: the stack comes up
  # healthy, serves correctly, and never reports another span. For hcmarketnet it is not silent at
  # all, which is the easier failure: five services that cannot resolve a datasource host.
  log "checking host networks"
  for net in "${HC_NETWORK:-infranet}" "${HC_DATA_NETWORK:-hcmarketnet}" "${HC_MONITORING_NETWORK:-monitoring}"; do
    (( DRY_RUN )) && { printf '%s  [dry-run] docker network inspect %s%s\n' "$c_dim" "$net" "$c_reset"; continue; }
    if [[ "$net" == "${HC_DATA_NETWORK:-hcmarketnet}" ]]; then
      net_hint="It is hc-market's own and carries the five databases. Create it once on the host with \`cd $REMOTE_PATH && ./infra.sh\` — see deploy/prod-server/README.md."
    else
      net_hint="It is host-wide and this stack does not create it — start the owning stack first (~/webroot/00-infrastructure for infranet, ~/webroot/02-monitoring for monitoring)."
    fi
    ssh -o BatchMode=yes "$HOST" "docker network inspect $net >/dev/null 2>&1" \
      || die "the '$net' network does not exist on $HOST. $net_hint Do NOT drop it from docker-compose.prod.yml."
    ok "network $net present"
  done
}

confirm() {
  (( ASSUME_YES )) && return 0
  (( DRY_RUN ))    && return 0
  printf '\n%sDeploying%s  tag %s%s%s  ·  channel %s%s%s  ·  host %s%s%s\n' \
    "$c_b" "$c_reset" "$c_b" "$TAG" "$c_reset" "$c_b" "$CHANNEL" "$c_reset" "$c_b" "$HOST" "$c_reset"
  printf '  services: %s\n' "${SERVICES[*]}"
  read -r -p "  proceed? [y/N] " a; [[ "$a" == [yY] ]] || { warn "aborted"; exit 1; }
}

# --------------------------------------------------------------------- build --
# Each app is a standalone Maven project -- no reactor, no -pl. We cd into each in turn, exactly
# as every sibling product in this workspace is built.
build_and_push() {
  step "Build"
  export JAVA_HOME
  [[ -x "$JAVA_HOME/bin/javac" ]] || die "no javac at $JAVA_HOME -- that is a JRE, not a JDK"
  local jv; jv="$(java_major)"
  (( jv >= 25 )) || die "Java 25+ required (found ${jv/0/unknown} at $JAVA_HOME)"
  # credentials go to Jib through the environment, never on the command line,
  # so they cannot leak into `ps`, CI logs or --dry-run output
  export JIB_TO_USERNAME="$REGISTRY_USER" JIB_TO_PASSWORD="$REGISTRY_TOKEN"
  for s in "${SERVICES[@]}"; do
    local img; img="$(image_for "$s" "$TAG")"
    log "verifying $s"
    run bash -c "cd '$ROOT_DIR/$s' && ./mvnw -q -ntp clean verify -Pprod"
    log "packaging $s -> $img"
    run bash -c "cd '$ROOT_DIR/$s' && ./mvnw -q -ntp jib:build -Pprod \
      -Djib.to.image='$img' \
      -Djib.to.tags='$TAG,$GIT_SHA,latest' \
      -Djib.container.labels='org.opencontainers.image.revision=$GIT_SHA,org.opencontainers.image.version=$TAG,net.jojoaddison.channel=$CHANNEL'"
  done
  unset JIB_TO_USERNAME JIB_TO_PASSWORD
  ok "images published to $REGISTRY_HOST"
}
build_local_only() {
  step "Build (local, no push)"
  export JAVA_HOME
  for s in "${SERVICES[@]}"; do
    run bash -c "cd '$ROOT_DIR/$s' && ./mvnw -q -ntp clean verify -Pprod"
    run bash -c "cd '$ROOT_DIR/$s' && ./mvnw -q -ntp jib:dockerBuild -Pprod -Djib.to.image='$(image_for "$s" "$TAG")'"
  done
  ok "images built locally"
}

# Deploying a tag without building it is only safe if the tag is actually THERE. D14 records a
# release that exited 0 having pushed three of five images, so "the tag exists for one service" says
# nothing about the others — and the failure surfaces on the host, mid-deploy, as a pull error.
#
# NOT FATAL when credentials are absent. The workstation does not need registry access for this
# deploy to work — the HOST pulls — so refusing here would block a legitimate deploy over a check
# that is a convenience. It says so instead of implying it checked.
verify_published() {
  step "Verify images"
  if (( DRY_RUN )); then
    for s in "${SERVICES[@]}"; do skipped "would confirm $(image_for "$s" "$TAG") exists"; done
    return 0
  fi
  if [[ -z "$REGISTRY_TOKEN" ]]; then
    warn "no registry credentials on this machine, so image existence was NOT confirmed."
    warn "The host pulls these itself; if $TAG was never published the failure appears mid-deploy."
    return 0
  fi
  printf '%s' "$REGISTRY_TOKEN" | docker login "$REGISTRY_HOST" -u "$REGISTRY_USER" --password-stdin >/dev/null \
    || die "registry login failed for $REGISTRY_HOST"
  local missing=0
  for s in "${SERVICES[@]}"; do
    local img; img="$(image_for "$s" "$TAG")"
    if docker manifest inspect "$img" >/dev/null 2>&1; then
      ok "$img"
    else
      printf '%s✗ %s is not in the registry%s\n' "$c_err" "$img" "$c_reset" >&2
      missing=1
    fi
  done
  (( missing )) && die "refusing to deploy a tag the registry does not hold. Re-run the release workflow, or use --build."
  ok "all $TAG images present"
}

# -------------------------------------------------------------------- deploy --
#
# NON-SECRET VALUES ONLY, and the header says where the rest are rather than merely forbidding an
# edit. The old header — "do not edit on the host" — was an instruction an operator could not follow
# and a defect at the same time: the compose file demands JWT_BASE64_SECRET and HC_PRIVACY_PEPPER,
# neither was ever emitted here, and a value added by hand to make the stack start was silently
# deleted by the next deploy while the file told them not to touch it. Rollback restored
# .env.previous wholesale, so a hand-added secret survived a rollback and not a deploy, and the two
# paths disagreed about what this file even contained.
render_env() {
  cat <<EOF
# generated by deploy-prod.sh — do not edit on the host.
# Secrets are NOT here and never will be: JWT_BASE64_SECRET and HC_PRIVACY_PEPPER live in
# $SECRETS_FILE beside this file, which no deploy rewrites. Compose reads both.
HC_TAG=$TAG
HC_GIT_SHA=$GIT_SHA
HC_CHANNEL=$CHANNEL
HC_IMAGE_PREFIX=$IMAGE_PREFIX
HC_IMAGE_SEP=$IMAGE_SEP
HC_REGISTRY_HOST=$REGISTRY_HOST
SPRING_PROFILES_ACTIVE=prod
HEALTHCONNECT_SEED_ENABLED=false
EOF
}

remote_deploy() {
  step "Deploy → $HOST:$REMOTE_PATH"
  run ssh "$HOST" "mkdir -p '$REMOTE_PATH'"

  log "uploading compose stack and env"
  if (( DRY_RUN )); then
    printf '%s  [dry-run] scp %s %s:%s/docker-compose.yml%s\n' "$c_dim" "$COMPOSE_TEMPLATE" "$HOST" "$REMOTE_PATH" "$c_reset"
    render_env | sed 's/^/    /'
  else
    scp -q "$COMPOSE_TEMPLATE" "$HOST:$REMOTE_PATH/docker-compose.yml"
    render_env | ssh "$HOST" "cat > '$REMOTE_PATH/.env.next'"
    ssh "$HOST" "cd '$REMOTE_PATH' && { [ -f .env ] && cp .env .env.previous || true; } && mv .env.next .env"
  fi

  if (( DO_PUSH )); then
    log "authenticating the host to $REGISTRY_HOST"
    (( DRY_RUN )) || printf '%s' "$REGISTRY_TOKEN" \
      | ssh "$HOST" "docker login '$REGISTRY_HOST' -u '$REGISTRY_USER' --password-stdin >/dev/null"
    log "pulling $TAG"
    run ssh "$HOST" "cd '$REMOTE_PATH' && $REMOTE_COMPOSE pull $(compose_names)"
  fi

  log "rolling services"
  run ssh "$HOST" "cd '$REMOTE_PATH' && $REMOTE_COMPOSE up -d --remove-orphans $(compose_names)"
}

health_gate() {
  step "Health gate (${HEALTH_TIMEOUT}s)"
  if (( DRY_RUN )); then printf '%s  [dry-run] skipped%s\n' "$c_dim" "$c_reset"; return 0; fi
  local waited=0 bad
  while :; do
    bad=""
    for s in "${SERVICES[@]}"; do
      # `docker compose exec ... curl` cannot work: the Jib images ship no curl and no wget.
      # bash IS present, so readiness is probed over bash's /dev/tcp instead.
      # $REMOTE_COMPOSE, not a bare `docker compose`: interpolation happens on every subcommand,
      # so an `exec` without the secrets file dies on the same `:?` an `up` would.
      ssh "$HOST" "cd '$REMOTE_PATH' && $REMOTE_COMPOSE exec -T $(compose_name "$s") bash -c \
        'exec 3<>/dev/tcp/localhost/8080 && printf \"GET /management/health/readiness HTTP/1.0\\r\\n\\r\\n\" >&3 && grep -q UP <&3'" \
        >/dev/null 2>&1 || bad+=" $s"
    done
    [[ -z "$bad" ]] && { ok "all services report READY"; return 0; }
    (( waited += 10 )); sleep 10
    (( waited >= HEALTH_TIMEOUT )) && { warn "still unhealthy:$bad"; return 1; }
    printf '  waiting%s (%ss)\n' "$bad" "$waited"
  done
}

smoke_test() {
  step "Smoke test"
  if (( DRY_RUN )); then printf '%s  [dry-run] skipped%s\n' "$c_dim" "$c_reset"; return 0; fi
  local base="${HC_PUBLIC_URL:-https://market.abofonsa.com}"

  # --- BOTH HALVES OF THIS TEST WERE UNPASSABLE UNTIL 2026-09-05 ---------------------------------
  #
  # THE PATH. It asked for `$base/api/professionals/count`, and the gateway routes
  # `/services/<service>/api/**` and nothing else (decisions.md D28) — that narrowing IS the security
  # control, so /api/** at the edge matches no route and never will. The URL below is the one
  # quality/README.md documents and the one quality/startup.sh checks; it is the estate's actual
  # public read.
  #
  # THE HOST. The default was https://health.jojoaddison.net, a name this product does not serve.
  # market.abofonsa.com is the hostname deploy/prod-server/market.abofonsa.com.conf bootstraps.
  #
  # Neither had been noticed because a deploy that reaches its smoke test has never happened. This is
  # the class of defect the whole prod-server package exists to flush out: paths that only run on a
  # host nothing has ever run against.
  local n
  n="$(curl -fsS --max-time 10 "$base/services/healthconnectcatalog/api/professionals/count" || echo "")"
  [[ "$n" =~ ^[0-9]+$ && "$n" -gt 0 ]] || { warn "catalogue smoke test failed (got '${n:-nothing}') — GET $base/services/healthconnectcatalog/api/professionals/count"; return 1; }
  ok "catalogue answering — $n published professionals"

  # The version comes from the CONTAINER, not from the edge, and that is deliberate.
  #
  # /management is 404 at the public edge on purpose (prod-server/hc-market-app.conf): actuator
  # carries health detail, metrics, env, loggers and the build's git SHA, and `info` in particular
  # hands a stranger the exact build a CVE would be matched against. So this asks the gateway itself,
  # over the same bash /dev/tcp channel the health gate already uses — the Jib images ship no curl.
  #
  # Strictly better than the old form as well as merely possible: it reports what the DEPLOYED
  # container believes it is, rather than what the edge happens to route.
  if ssh "$HOST" "cd '$REMOTE_PATH' && $REMOTE_COMPOSE exec -T $(compose_name gateway) bash -c \
       'exec 3<>/dev/tcp/localhost/8080 && printf \"GET /management/info HTTP/1.0\\r\\n\\r\\n\" >&3 && cat <&3'" \
       2>/dev/null | grep -q "$TAG"; then
    ok "gateway container reports version $TAG"
  else
    warn "the gateway container did not report $TAG in /management/info"
  fi
}

rollback() {
  step "Rollback"
  # THE UNGUARDED SSH THAT USED TO BE HERE. Reading the previous tag ran even under --dry-run, so
  # `--rollback --dry-run` contacted the production host to answer a question it then printed a plan
  # about — while the flag's own help says "print, change nothing". A read is not a write, but a dry
  # run that touches the host is not a dry run, and this is the one command somebody reaches for
  # when a deploy has just gone wrong and they want to know what rolling back would do BEFORE doing
  # it. Found by actually running `--rollback --dry-run`, which nothing had.
  if (( DRY_RUN )); then
    skipped "would read HC_TAG from $HOST:$REMOTE_PATH/.env.previous — NOT contacted"
    skipped "would roll the stack back to that tag and re-run the health gate"
    return 0
  fi
  local prev
  prev="$(ssh "$HOST" "cd '$REMOTE_PATH' && grep -m1 '^HC_TAG=' .env.previous 2>/dev/null | cut -d= -f2" || true)"
  [[ -n "$prev" ]] || die "no previous deployment recorded on $HOST — nothing to roll back to"
  warn "rolling back to $prev"
  # .env.previous holds the previous deploy's non-secret values and nothing else, so restoring it
  # cannot take a secret back to an older value — secrets.env is not deploy state and is not rotated
  # here. Before the split, a secret hand-added to .env survived a rollback but not a deploy, which
  # meant the two paths disagreed about what the stack would come up with.
  run ssh "$HOST" "cd '$REMOTE_PATH' && cp .env.previous .env && $REMOTE_COMPOSE pull $(compose_names) && $REMOTE_COMPOSE up -d $(compose_names)"
  TAG="$prev"
  health_gate && ok "rolled back to $prev" || die "rollback to $prev is also unhealthy — manual intervention required"
}

record_success() {
  (( DRY_RUN )) && return 0
  ssh "$HOST" "cd '$REMOTE_PATH' && printf '%s\t%s\t%s\t%s\n' \
    \"\$(date -u +%FT%TZ)\" '$TAG' '$GIT_SHA' '$CHANNEL' >> deployments.log"
}

# --------------------------------------------------------------------- router --
if (( DO_ROLLBACK )); then
  HOST="${HOST:-${HC_PROD_HOST:-}}"; [[ -n "$HOST" ]] || die "no target host"
  rollback; exit 0
fi

resolve_tag
preflight
confirm
if   (( DO_BUILD && DO_PUSH )); then build_and_push
elif (( DO_BUILD ));            then build_local_only
else                                 verify_published
fi
remote_deploy

if health_gate && smoke_test; then
  record_success
  step "Done"
  ok "HealthConnect $TAG live on $HOST via the '$CHANNEL' channel ($IMAGE_PREFIX)"
  printf '  rollback with: %s./deploy-prod.sh --rollback --host %s%s\n' "$c_dim" "$HOST" "$c_reset"
else
  warn "deployment did not pass its gates"
  rollback
  exit 1
fi
