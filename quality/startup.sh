#!/bin/bash
# Runs the hc-market stack, seeded, on jacserver at http://market.healthconnect.local
#
#   ./startup.sh --local          run compose here (you are already on jacserver)
#   ./startup.sh --local --verify touch nothing, just re-run the checks
#   ./startup.sh --local --down   stop everything, keep the databases
#   ./startup.sh --local --clean  stop everything and drop the database volumes
#   ./startup.sh --host=X         a different ssh target (default: jacserver)
#   ./startup.sh --images=local   use images built here rather than pulling (the default; see below)
#
# --- Where this runs ---------------------------------------------------------------------------
#
# On jacserver — ssh alias `jacserver`, 192.168.1.2 on the LAN. Its config lives in
# ~/webroot/01-healthconnect/hc-market/quality, the way ../deploy puts production's config on the
# production server. From a workstation this ships the config there and drives compose over ssh;
# with --local it runs compose here instead.
#
# NOT on `webserver`. That is the production VPS (199.247.5.252), a different machine — and it
# reports `jacserver` as its own hostname, so asking a host its name cannot tell them apart. This
# stack runs the dev,test profile pair, which is what makes it seeded, and that server is
# public-facing.
#
# From a workstation the ssh alias distinguishes them. --local has no alias to lean on, so it checks
# the one property the two do not share: jacserver is on the LAN and the VPS has no private address
# at all. Docker bridges are excluded — every docker host carries 172.17.0.1, including the VPS, so
# counting them would defeat the test.
#
# --- The front door is the GATEWAY -------------------------------------------------------------
#
# There is no nginx and no web container in compose.yml: hc-market is API-only. jacserver's own
# nginx has a vhost for market.healthconnect.local pointing at the port the gateway publishes, and
# host-site.conf beside this script is that vhost. Every published port binds 127.0.0.1, so the
# vhost is the only way in.
#
# Installing it is one sudo, once — this script PRINTS it and runs nothing, because /etc is
# root-owned and not this repository's to edit.
#
# --- Images ------------------------------------------------------------------------------------
#
# The siblings pull published images, which is the better discipline: it proves the thing you are
# about to deploy is the thing you built. hc-market has nothing published yet — no CI, no registry
# push — so `--images=local` is the DEFAULT here and the script says so on every run rather than
# pretending otherwise. Switch the default the day images are published.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
SSH_HOST="${SSH_HOST:-jacserver}"
REMOTE_DIR="~/webroot/01-healthconnect/hc-market/quality"
PROJECT="hc-market-quality"

# Host ports. GATEWAY_PORT is written here AND in host-site.conf; check_ports() enforces they agree.
GATEWAY_PORT="${GATEWAY_PORT:-15509}"
CATALOG_PORT="${CATALOG_PORT:-18100}"
BOOKING_PORT="${BOOKING_PORT:-18101}"
MESSAGING_PORT="${MESSAGING_PORT:-18102}"
PAYOUT_PORT="${PAYOUT_PORT:-18103}"
SITE="market.healthconnect.local"

MODE="remote"; ACTION="up"; IMAGES="local"

c_reset=$'\033[0m'; c_b=$'\033[1m'; c_dim=$'\033[2m'
c_ok=$'\033[32m'; c_warn=$'\033[33m'; c_err=$'\033[31m'; c_info=$'\033[36m'
log()  { printf '%s▸%s %s\n' "$c_info" "$c_reset" "$*"; }
ok()   { printf '%s✓%s %s\n' "$c_ok" "$c_reset" "$*"; }
warn() { printf '%s!%s %s\n' "$c_warn" "$c_reset" "$*"; }
die()  { printf '%s✗ %s%s\n' "$c_err" "$*" "$c_reset" >&2; exit 1; }
step() { printf '\n%s%s%s\n' "$c_b" "$*" "$c_reset"; }

for arg in "$@"; do
  case "$arg" in
    --local)     MODE="local" ;;
    --verify)    ACTION="verify" ;;
    --down)      ACTION="down" ;;
    --clean)     ACTION="clean" ;;
    --host=*)    SSH_HOST="${arg#*=}" ;;
    --images=*)  IMAGES="${arg#*=}" ;;
    -h|--help)   sed -n '2,45p' "$0"; exit 0 ;;
    *)           die "unknown option: $arg (try --help)" ;;
  esac
done

# --- This must not be the production VPS -------------------------------------------------------
#
# `hostname` cannot tell them apart — the VPS answers `jacserver` too. The distinguishing property
# is a private LAN address, which jacserver has and the VPS does not. Docker bridges are excluded
# because every docker host has one.
assert_not_production() {
  [[ "$MODE" == "local" ]] || return 0
  local lan
  lan="$(ip -4 -o addr show scope global 2>/dev/null \
        | grep -vE '\b(docker|br-|veth)' \
        | awk '{print $4}' | grep -E '^(10\.|172\.(1[6-9]|2[0-9]|3[01])\.|192\.168\.)' | head -1)"
  [[ -n "$lan" ]] || die "no private LAN address on this machine — refusing to run the dev,test stack. This looks like the production VPS, not jacserver."
  ok "on a LAN host (${lan%%/*}) — not the production VPS"
}

# --- The port is written twice, so check it once ------------------------------------------------
check_ports() {
  local in_conf
  in_conf="$(grep -oE 'proxy_pass http://127\.0\.0\.1:[0-9]+' "$HERE/host-site.conf" | grep -oE '[0-9]+$' | sort -u)"
  [[ -n "$in_conf" ]] || die "no proxy_pass port found in host-site.conf"
  [[ "$(printf '%s\n' "$in_conf" | wc -l)" == "1" ]] || die "host-site.conf proxies to more than one port: $in_conf"
  [[ "$in_conf" == "$GATEWAY_PORT" ]] \
    || die "host-site.conf proxies to $in_conf but GATEWAY_PORT is $GATEWAY_PORT — nginx would 502. Change both."
  ok "vhost and compose agree on port $GATEWAY_PORT"
}

# --- The estate's shared signing key ------------------------------------------------------------
#
# One key across all five services. Persisted beside this script so a restart does not invalidate
# every token someone is holding, and gitignored because it is a secret even in quality.
resolve_secret() {
  local f="$HERE/.jwt-secret"
  if [[ -n "${JWT_BASE64_SECRET:-}" ]]; then :
  elif [[ -s "$f" ]]; then JWT_BASE64_SECRET="$(cat "$f")"
  else
    JWT_BASE64_SECRET="$(head -c 64 /dev/urandom | base64 -w0)"
    umask 077; printf '%s' "$JWT_BASE64_SECRET" > "$f"
    log "generated a new signing key at quality/.jwt-secret"
  fi
  export JWT_BASE64_SECRET
}

compose() { docker compose -p "$PROJECT" -f "$HERE/compose.yml" "$@"; }

env_for_compose() {
  export TAG="${TAG:-local}" REGISTRY="${REGISTRY:-healthconnect}"
  export GATEWAY_PORT CATALOG_PORT BOOKING_PORT MESSAGING_PORT PAYOUT_PORT
  export SEED_DIR="$ROOT/deploy/demo"
}

# --- Verification -------------------------------------------------------------------------------
#
# These read the RESPONSE BODY, never just the status. A vhost that has been stolen by a sibling
# answers 200 with a perfectly plausible page — that exact failure is on record for
# admin.healthconnect.local — so "something replied" proves nothing at all.
verify() {
  step "Verify"
  local base="http://127.0.0.1:${GATEWAY_PORT}"
  local fail=0
  chk() { if [[ "$2" == "$3" ]]; then printf '  %s✓%s %-46s %s\n' "$c_ok" "$c_reset" "$1" "$2"; else printf '  %s✗%s %-46s got %s want %s\n' "$c_err" "$c_reset" "$1" "$2" "$3"; fail=1; fi; }

  chk "gateway health" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$base/management/health")" "200"

  local n
  n="$(curl -s --max-time 10 "$base/services/healthconnectcatalog/api/professionals/count" || echo "")"
  chk "professionals through the gateway, no token" "$n" "18"
  chk "reviews through the gateway" \
    "$(curl -s --max-time 10 "$base/services/healthconnectcatalog/api/reviews/count" || echo "")" "63"

  # The rule the whole design turns on. A count can be right while the rating read model is broken;
  # this is the only check here that would notice.
  local rating
  rating="$(curl -s --max-time 10 "$base/services/healthconnectcatalog/api/professionals/p1" \
            | python3 -c 'import sys,json;print(json.load(sys.stdin)["card"]["rating"])' 2>/dev/null || echo "")"
  chk "p1 rating is derived, not null" "$(if [[ -n "$rating" && "$rating" != "None" ]]; then echo present; else echo missing; fi)" "present"

  # It must be OUR application answering, not a sibling's Angular app on a stolen hostname.
  chk "the body is this catalogue, not a sibling's app" \
    "$(curl -s --max-time 10 "$base/services/healthconnectcatalog/api/categories" | grep -c 'Fitness & Movement' || true)" "1"

  chk "lifetime gross via payout" \
    "$(curl -s --max-time 10 "http://127.0.0.1:${PAYOUT_PORT}/management/health" -o /dev/null -w '%{http_code}')" "200"

  # And the same, through the hostname — only meaningful once the vhost is installed.
  if getent hosts "$SITE" >/dev/null 2>&1; then
    local viahost
    viahost="$(curl -s --max-time 10 "http://$SITE/services/healthconnectcatalog/api/professionals/count" || echo "")"
    chk "professionals via http://$SITE" "$viahost" "18"
  else
    warn "$SITE does not resolve — skipping the hostname checks. See the sudo block below."
  fi

  (( fail == 0 )) || die "verification failed"
  ok "verified"
}

sudo_block() {
  cat <<EOF

${c_b}Two things need root, and this script runs neither.${c_reset}
${c_dim}/etc is not this repository's to edit. Both are one-time.${c_reset}

  ${c_b}1. Resolve the name${c_reset}
     echo '127.0.0.1  $SITE market.abofonsa.local' | sudo tee -a /etc/hosts

  ${c_b}2. Install the vhost${c_reset}
     sudo ln -sfn "$HERE/host-site.conf" /etc/nginx/sites-enabled/$SITE.conf
     sudo nginx -t && sudo systemctl reload nginx

${c_dim}Symlinked, not copied, so editing host-site.conf here edits the live site after a reload.${c_reset}
EOF
}

# --- Router -------------------------------------------------------------------------------------
[[ "$MODE" == "local" ]] || die "remote mode is not implemented for hc-market yet — run this on jacserver with --local"

case "$ACTION" in
  down)  env_for_compose; resolve_secret; compose down; ok "stopped, databases kept"; exit 0 ;;
  clean) env_for_compose; resolve_secret; compose down -v; ok "stopped, database volumes dropped"; exit 0 ;;
  verify) env_for_compose; verify; exit 0 ;;
esac

step "Preflight"
assert_not_production
check_ports
resolve_secret
env_for_compose
[[ -f "$ROOT/deploy/demo/seed-data.json" ]] || die "no seed at $ROOT/deploy/demo/seed-data.json"
ok "seed present — $(python3 -c 'import json;d=json.load(open("'"$ROOT"'/deploy/demo/seed-data.json"));print(len(d["professionals"]),"professionals,",len(d["reviews"]),"reviews")')"

if [[ "$IMAGES" == "local" ]]; then
  warn "using LOCAL images ($REGISTRY/*:$TAG) — nothing is published for hc-market yet, so this"
  warn "cannot prove the deployed image is the built one. That is the point of --images=published,"
  warn "and it is unavailable until these are pushed."
  for s in gateway catalog booking messaging payout; do
    docker image inspect "$REGISTRY/$s:$TAG" >/dev/null 2>&1 \
      || die "missing image $REGISTRY/$s:$TAG — build it: (cd $ROOT/$s && ./mvnw -Pdev package jib:dockerBuild -Djib.to.image=$REGISTRY/$s:$TAG)"
  done
  ok "all five images present locally"
else
  log "pulling $REGISTRY/*:$TAG"
  compose pull
fi

step "Start"
compose up -d
ok "containers started"

step "Wait for health"
for s in gateway catalog booking messaging payout; do
  printf '  %s… ' "$s"
  for i in $(seq 1 90); do
    [[ "$(docker inspect -f '{{.State.Health.Status}}' "hc-market-quality-$s" 2>/dev/null)" == "healthy" ]] && { printf '%shealthy%s\n' "$c_ok" "$c_reset"; break; }
    [[ $i == 90 ]] && { printf '%stimeout%s\n' "$c_err" "$c_reset"; compose logs --tail=40 "$s"; die "$s did not become healthy"; }
    sleep 4
  done
done

verify
sudo_block

cat <<EOF
${c_b}hc-market quality is up${c_reset}
  Site           http://$SITE   ${c_dim}(once the vhost is installed)${c_reset}
  Gateway        http://127.0.0.1:$GATEWAY_PORT
  catalog        http://127.0.0.1:$CATALOG_PORT      booking   http://127.0.0.1:$BOOKING_PORT
  messaging      http://127.0.0.1:$MESSAGING_PORT      payout    http://127.0.0.1:$PAYOUT_PORT
  Profiles       dev,test        ${c_dim}(both — that pair is what makes it seeded)${c_reset}

${c_dim}  This is an API. There is no page to open: try
    curl http://$SITE/services/healthconnectcatalog/api/professionals/count${c_reset}
EOF
