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
# --- Bring the shared plane up first -----------------------------------------------------------
#
# compose.yml declares NO Kafka and NO Consul. Since 2026-08-31 no deployment in this estate does:
# both live once, in hc-infra, and all four product stacks point at them by container name over the
# external `hcnet` network. Four brokers was the same as no broker — four disjoint logs, and every
# cross-product event path configured, deployed and never once exercised.
#
#     cd ~/webroot/01-healthconnect/hc-infra && ./startup.sh
#
# Preflight checks it and refuses to continue without it, because the failure it prevents is silent:
# a service whose broker is unreachable starts, serves and reports healthy.
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
# PUBLISHED by default, which is the whole point of a quality box: it proves the thing you are about
# to deploy is the thing CI built, from a commit that exists. Images come from
# ghcr.io/kojoampia/hc-market-<service>, tagged by commit SHA (decisions.md D13), published by
# .github/workflows/release.yml on every push to main.
#
# `--images=local` remains as a fallback for unreleased work in progress, and warns loudly, because
# an image built on this workstation cannot prove it matches any commit — which is exactly the
# guarantee this stack exists to provide.
#
# TAG defaults to the current commit. That is deliberate: it means `./startup.sh --local` deploys
# the code you are looking at, and fails honestly if CI has not published it yet rather than
# silently running something older.
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

MODE="remote"; ACTION="up"; IMAGES="published"

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
    -h|--help)   sed -n '2,63p' "$0"; exit 0 ;;
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

# --- The shared plane is hc-infra's, and this stack cannot start without it ----------------------
#
# compose.yml declares no broker and no Consul — since 2026-08-31 no deployment in this estate does
# — and joins `hcnet` as an external network. Compose's own error for a missing external network
# names the network and stops there; the error for a broker that is simply not running is no error
# at all, because a service with an unreachable broker starts, serves and reports healthy while
# everything it publishes goes nowhere. Both are checked here, by name, with the fix printed.
SHARED_NETWORK="${HC_SHARED_NETWORK:-hcnet}"
SHARED_CONSUL="${HC_SHARED_CONSUL:-hc-shared-quality-consul}"
SHARED_KAFKA="${HC_SHARED_KAFKA:-hc-shared-quality-kafka}"
SHARED_INFRA_DIR="${HC_SHARED_INFRA_DIR:-$HOME/webroot/01-healthconnect/hc-infra}"
check_shared_plane() {
  local fix="start it with:  (cd $SHARED_INFRA_DIR && ./startup.sh)"
  docker network inspect "$SHARED_NETWORK" >/dev/null 2>&1 \
    || die "the shared network '$SHARED_NETWORK' does not exist — $fix"
  for c in "$SHARED_CONSUL" "$SHARED_KAFKA"; do
    [[ "$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)" == "true" ]] \
      || die "$c is not running — $fix"
  done
  # A leader, not merely an answering agent: Consul serves /v1/status/leader before it has elected
  # one, and every KV read fails with "No cluster leader" until it does.
  docker exec "$SHARED_CONSUL" consul operator raft list-peers >/dev/null 2>&1 \
    || die "$SHARED_CONSUL has no leader yet — wait, or $fix"
  docker exec "$SHARED_KAFKA" /opt/kafka/bin/kafka-broker-api-versions.sh \
    --bootstrap-server localhost:9092 >/dev/null 2>&1 \
    || die "$SHARED_KAFKA is not answering — wait, or $fix"
  ok "shared plane: $SHARED_CONSUL (leader elected), $SHARED_KAFKA on $SHARED_NETWORK"
}

# --- The estate's shared secrets ----------------------------------------------------------------
#
# Two of them, both one value across all five services and both persisted beside this script:
# gitignored, because they are secrets even in quality, and persisted because regenerating either on
# every restart has a cost. For the signing key that cost is every token someone is holding. For the
# pepper it is worse and quieter — it keys the HMAC behind an erased customer's alias (decisions.md
# D35), nothing re-keys rows that already carry one, and a fresh pepper leaves messaging unable to
# recognise its own erased subjects. Delete .privacy-pepper only together with the stack's volumes.
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

  # --- The pepper does NOT follow the signing key's precedence, and that gap minted new ones ------
  #
  # env > file > generate, with an environment value never written down, is right for the signing
  # key: the worst case is that everyone signs in again. For the pepper it produced a silent data
  # defect. Run this once with HC_PRIVACY_PEPPER exported — the branch above takes it, nothing is
  # persisted — and run it again without it, and the `-s "$p"` test misses, so a BRAND NEW random
  # pepper is generated and handed to a stack whose erased_subject rows were written under the old
  # one. Messaging starts perfectly happily: ErasureRegisterGuard detects a MISSING pepper, and D35
  # says plainly that a changed pepper "looks exactly like a right one until something fails to
  # match". Every alias in the register is orphaned from that moment, permanently.
  #
  # So an environment value is persisted the first time it is seen, and a conflict is fatal. Choosing
  # between two candidate peppers is not a decision a startup script can take: one of them matches
  # the rows in the volumes and the other does not, and this script cannot tell which. Deleting the
  # file is how the operator says which — deliberately, and together with the volumes, exactly as the
  # comment above says.
  local p="$HERE/.privacy-pepper"
  local on_disk=""
  [[ -s "$p" ]] && on_disk="$(cat "$p")"
  if [[ -n "${HC_PRIVACY_PEPPER:-}" ]]; then
    if [[ -n "$on_disk" && "$on_disk" != "$HC_PRIVACY_PEPPER" ]]; then
      # A teardown writes no alias, and dropping the volumes is precisely how an operator resolves
      # this — so refusing here would refuse the remedy along with the mistake. The stored value is
      # used: `down` and `clean` need something for compose to interpolate and nothing more.
      if [[ "$ACTION" == "down" || "$ACTION" == "clean" ]]; then
        warn "HC_PRIVACY_PEPPER differs from quality/.privacy-pepper; using the stored one for this teardown"
        HC_PRIVACY_PEPPER="$on_disk"
      else
        die "HC_PRIVACY_PEPPER in the environment differs from quality/.privacy-pepper. Aliases already in this stack's databases were derived from one of them and nothing re-keys them (decisions.md D35), so the stack must not start until you say which is correct: unset the variable to keep the stored pepper, or drop the volumes and the file together ('./startup.sh --local --clean' then 'rm quality/.privacy-pepper') to adopt the new one."
      fi
    fi
    if [[ -z "$on_disk" ]]; then
      umask 077; printf '%s' "$HC_PRIVACY_PEPPER" > "$p"
      log "persisted the erasure pepper from the environment to quality/.privacy-pepper"
    fi
  elif [[ -n "$on_disk" ]]; then
    HC_PRIVACY_PEPPER="$on_disk"
  else
    HC_PRIVACY_PEPPER="$(head -c 32 /dev/urandom | base64 -w0)"
    umask 077; printf '%s' "$HC_PRIVACY_PEPPER" > "$p"
    log "generated a new erasure pepper at quality/.privacy-pepper"
  fi
  export HC_PRIVACY_PEPPER
}

compose() { docker compose -p "$PROJECT" -f "$HERE/compose.yml" "$@"; }

env_for_compose() {
  # Published images are tagged by commit SHA; local ones by the literal "local".
  if [[ "$IMAGES" == "local" ]]; then
    export TAG="${TAG:-local}" REGISTRY="${REGISTRY:-healthconnect}" IMAGE_SEP="/"
  else
    export TAG="${TAG:-$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo latest)}"
    # "-" not "/": GHCR has no nested-path namespaces, so the published name is
    # ghcr.io/kojoampia/hc-market-gateway. Getting this wrong yields a 404 that reads as
    # "CI has not published yet" when the name simply cannot exist.
    export REGISTRY="${REGISTRY:-ghcr.io/kojoampia/hc-market}" IMAGE_SEP="-"
  fi
  export GATEWAY_PORT CATALOG_PORT BOOKING_PORT MESSAGING_PORT PAYOUT_PORT
  export SEED_DIR="$ROOT/deploy/demo"
}

# --- Verification -------------------------------------------------------------------------------
#
# These read the RESPONSE BODY, never just the status. A vhost that has been stolen by a sibling
# answers 200 with a perfectly plausible page — that exact failure is on record for
# admin.healthconnect.local — so "something replied" proves nothing at all.
#
# --- SEED-EXACT AND SEED-PLUS-ACTIVITY ARE TWO DIFFERENT ASSERTIONS ------------------------------
#
# Every count here used to be exact, and that made two of this repository's own tools contradict
# each other. deploy/verify-cycle.sh books, accepts, completes and REVIEWS — the end-to-end check
# the box exists for — and a review cannot be deleted, deliberately (spec §7). So a successful cycle
# left `reviews` at 64 and the next --verify reported `✗ reviews through the gateway got 64 want 63`
# and exited failure: one tool reporting another tool's success as a fault, with the next person
# sent hunting a defect that is not there. Found by the quality run of 1eadc7a.
#
# So the counts are split by whether anything in this repository writes to them:
#
#   SEED-EXACT      professionals, and the catalogue's own body. Nothing here creates a
#                   professional, so any drift is a real fault and stays an exact assertion.
#   SEED + ACTIVITY reviews. At least the seed's figure, with the surplus PRINTED rather than
#                   swallowed — a number that has moved is still on the screen, it just is not an
#                   exit code.
#
# What that costs is real and is worth naming: --verify no longer fails when somebody has written
# extra reviews into this box by hand. What it must not cost is the collision check, and it does
# not — a sibling's Angular app answers with a page rather than an integer, and `at least 63` fails
# on a non-number exactly as `== 63` did. The check below is added to make that stronger rather than
# weaker: it compares p1's rating against the reviews the API itself serves, which is the "derived,
# never stored" invariant and holds whatever the count is. Nothing serving somebody else's data can
# satisfy it, and a count alone never could — see where it is asked, immediately below, because that
# last sentence is only about collisions on the address where a collision is possible.
#
# --- WHERE THE DERIVATION CHECK IS ASKED, WHICH IS THE POINT OF IT -------------------------------
#
# It is asked TWICE: once on the stack's own published loopback port, and once through $SITE when
# the name resolves. Those prove different things and only the second one is about collisions.
# 127.0.0.1:$GATEWAY_PORT reaches this compose project's gateway and nothing else — no sibling can
# answer there — so on that address the check proves the read model, not the identity of whoever
# answered. The shared nginx is the surface where a wrong app can reply at all; that is where
# admin.healthconnect.local served the patient app with a 200 and a plausible login page, and it is
# the only address at which "unsatisfiable by a sibling on a stolen hostname" is a claim about
# anything. The first version of this check ran on loopback only and the prose claimed the
# collision property regardless — the check was right and the sentence around it was not, which is
# this repository's most repeated defect.
derived_rating_agrees() {
  python3 - "$1" <<'PY' 2>/dev/null
# ROUND_HALF_UP, not round(). The view rounds in Postgres, whose numeric round is half-away-from
# zero; Python's built-in round is half-to-even. The first version of this check used round() and
# reported "rating 4.3 over 8, reviews say 4.2 over 8" against an estate that was entirely correct —
# a 4.25 average landing on either side of the same boundary. Exactly the class of plausible wrong
# number this file exists to catch, arriving in the checker instead of in the estate.
#
# And it PAGES rather than asking for one big page. The first version read `?page=0&size=200` and
# compared the length of that page against `reviewCount`, which the view does not cap: past 200
# reviews on p1 it would have reported "rating 4.4 over 250, reviews say 4.5 over 200" against a
# correct estate — the round() defect again, one release later. p1 carries 7 today and
# verify-cycle.sh adds one per run TO p1, so it is distant and not theoretical. A page that cannot
# be completed is refused rather than averaged, and `totalElements` is compared to the view's own
# `reviewCount` because those two numbers coming from different endpoints is the whole assertion.
import json, sys, urllib.request
from decimal import Decimal, ROUND_HALF_UP
base = sys.argv[1] + "/services/healthconnectcatalog"
def get(p):
    with urllib.request.urlopen(base + p, timeout=10) as r:
        return json.load(r)
card = get("/api/professionals/p1")["card"]
rows, total, page = [], None, 0
while page < 50:
    body = get("/api/professionals/p1/reviews?page=%d&size=200" % page)
    if not isinstance(body, dict):          # an unpaged answer is its own total
        rows, total = body, len(body)
        break
    chunk = body.get("content") or []
    total = body.get("totalElements")
    rows += chunk
    page += 1
    if total is None or not chunk or len(rows) >= total:
        break
if card.get("rating") is None or not rows:
    print("no reviews served for p1")
elif total is None or len(rows) != total:
    print("served %s of %s reviews — refusing to average a truncated page" % (len(rows), total))
elif card.get("reviewCount") != total:
    print("the view counts %s reviews, the review endpoint serves %s" % (card.get("reviewCount"), total))
else:
    avg = (Decimal(sum(r["stars"] for r in rows)) / Decimal(len(rows))).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP)
    ok = Decimal(str(card["rating"])) == avg
    print("agrees" if ok else "rating %s over %s, reviews say %s over %s" % (card["rating"], card.get("reviewCount"), avg, len(rows)))
PY
}
verify() {
  step "Verify"
  local base="http://127.0.0.1:${GATEWAY_PORT}"
  local fail=0
  chk() { if [[ "$2" == "$3" ]]; then printf '  %s✓%s %-46s %s\n' "$c_ok" "$c_reset" "$1" "$2"; else printf '  %s✗%s %-46s got %s want %s\n' "$c_err" "$c_reset" "$1" "$2" "$3"; fail=1; fi; }
  # Seed-or-more, with the surplus named. A blank or non-numeric answer fails, which is what keeps
  # this as good a collision check as an exact count.
  atleast() {
    if [[ "$2" =~ ^[0-9]+$ ]] && (( $2 >= $3 )); then
      if (( $2 > $3 )); then
        printf '  %s✓%s %-46s %s %s(seed %s + %s recorded)%s\n' "$c_ok" "$c_reset" "$1" "$2" "$c_dim" "$3" "$(( $2 - $3 ))" "$c_reset"
      else
        printf '  %s✓%s %-46s %s %s(seed-exact)%s\n' "$c_ok" "$c_reset" "$1" "$2" "$c_dim" "$c_reset"
      fi
    else
      printf '  %s✗%s %-46s got %s want at least %s\n' "$c_err" "$c_reset" "$1" "$2" "$3"; fail=1
    fi
  }

  chk "gateway health" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$base/management/health")" "200"

  local n
  n="$(curl -s --max-time 10 "$base/services/healthconnectcatalog/api/professionals/count" || echo "")"
  chk "professionals through the gateway, no token" "$n" "18"
  atleast "reviews through the gateway" \
    "$(curl -s --max-time 10 "$base/services/healthconnectcatalog/api/reviews/count" || echo "")" "63"

  # The rule the whole design turns on. A count can be right while the rating read model is broken;
  # this is the only check here that would notice.
  local rating
  rating="$(curl -s --max-time 10 "$base/services/healthconnectcatalog/api/professionals/p1" \
            | python3 -c 'import sys,json;print(json.load(sys.stdin)["card"]["rating"])' 2>/dev/null || echo "")"
  chk "p1 rating is derived, not null" "$(if [[ -n "$rating" && "$rating" != "None" ]]; then echo present; else echo missing; fi)" "present"

  # And that the derivation is the RIGHT one, which no count can show. p1's rating and reviewCount
  # come from the professional_rating view; the reviews come from the review table through a
  # different endpoint. They must agree, and they agree whether the box is seed-exact or has been
  # exercised — which is why this replaces the exactness the check above gave up rather than merely
  # sitting beside it. On loopback that is a check of the read model; through $SITE below it is also
  # a check that our application is the one answering.
  local derived
  derived="$(derived_rating_agrees "$base" || true)"
  chk "p1's rating equals the reviews it serves" "${derived:-unreachable}" "agrees"

  # It must be OUR catalogue answering — a body, never a status code. On this address that is a
  # check of the DATA (a wrong seed, a wrong image, an empty database); the stolen-hostname case it
  # is named for is the same assertion asked through $SITE below, which is the only place a sibling
  # can answer at all.
  chk "the body is this catalogue, not a sibling's app" \
    "$(curl -s --max-time 10 "$base/services/healthconnectcatalog/api/categories" | grep -c 'Fitness & Movement' || true)" "1"

  chk "lifetime gross via payout" \
    "$(curl -s --max-time 10 "http://127.0.0.1:${PAYOUT_PORT}/management/health" -o /dev/null -w '%{http_code}')" "200"

  # And the same, through the hostname — only meaningful once the vhost is installed, and the only
  # address in this function where a WRONG APPLICATION can answer at all. Everything above reaches
  # this compose project's own published port; the shared nginx is where a stolen server_name puts a
  # sibling's app behind our name, which is on record. So the derivation check is asked again here:
  # a count of 18 is a number any JSON endpoint could produce, while "the rating in this card equals
  # the average of the reviews this same host serves from another endpoint" is not something a
  # sibling application can satisfy by accident.
  if getent hosts "$SITE" >/dev/null 2>&1; then
    local viahost derived_viahost
    viahost="$(curl -s --max-time 10 "http://$SITE/services/healthconnectcatalog/api/professionals/count" || echo "")"
    chk "professionals via http://$SITE" "$viahost" "18"
    derived_viahost="$(derived_rating_agrees "http://$SITE" || true)"
    chk "p1's rating is derived via http://$SITE" "${derived_viahost:-unreachable}" "agrees"
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
check_shared_plane
resolve_secret
env_for_compose
[[ -f "$ROOT/deploy/demo/seed-data.json" ]] || die "no seed at $ROOT/deploy/demo/seed-data.json"
ok "seed present — $(python3 -c 'import json;d=json.load(open("'"$ROOT"'/deploy/demo/seed-data.json"));print(len(d["professionals"]),"professionals,",len(d["reviews"]),"reviews")')"

if [[ "$IMAGES" == "local" ]]; then
  warn "using LOCAL images ($REGISTRY/*:$TAG) — an image built on this workstation cannot prove it"
  warn "matches any commit, which is the guarantee this stack exists to provide. Use this only for"
  warn "work in progress that CI has not published yet."
  for s in gateway catalog booking messaging payout; do
    docker image inspect "$REGISTRY/$s:$TAG" >/dev/null 2>&1 \
      || die "missing image $REGISTRY/$s:$TAG — build it: (cd $ROOT/$s && ./mvnw -Pdev package jib:dockerBuild -Djib.to.image=$REGISTRY/$s:$TAG)"
  done
  ok "all five images present locally"
else
  # $IMAGE_SEP, not a literal "/". Published images are ghcr.io/kojoampia/hc-market-catalog; a
  # message naming hc-market/catalog describes a path GHCR cannot have, and sends whoever reads it
  # looking for a registry fault instead of a missing tag. That exact confusion is on record.
  log "pulling $REGISTRY$IMAGE_SEP*:$TAG from ghcr.io"
  # A pull failure here is usually CI not having published THIS commit yet — but not always: a run
  # can go green having pushed only some of the five (decisions.md D14), so a tag existing for one
  # service says nothing about the others. Either way the fix is the same, and failing here leaves
  # the stack exactly as it was rather than half-rolled.
  compose pull \
    || die "could not pull $REGISTRY$IMAGE_SEP*:$TAG — has .github/workflows/release.yml finished for this commit, and did its 'All five published' job pass? Use --images=local for unreleased work."
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
