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
#     HC_PROD_HOST       the ssh target — `webserver`, the alias every sibling stack uses
#     HC_REGISTRY_USER / HC_REGISTRY_TOKEN     for docker.jojoaddison.net
#     GHCR_OWNER / GHCR_TOKEN                  for the github channel
#
#  Optional:
#     HC_PUBLIC_URL                 what the smoke test asks (default https://market.abofonsa.com)
#     HC_SMOKE_MIN_PROFESSIONALS    minimum catalogue count the smoke test will accept (default 0)
#     HC_SSH_TIMEOUT                seconds ssh will spend connecting, per ssh (default 8)
#
#  That last one bounds the CONNECT, not the remote command, and it is the value the ssh check has
#  always carried. An unbounded connect means a refusal that never arrives, and a message nobody waits
#  for is the same as no message. See host_run and decisions.md D75.
#
#  THIS SAID "EVERY REMOTE PROBE" AND MEANT PREFLIGHT'S SIX. D75 put SSH_OPTS on the probes that go
#  through host_run and deliberately left the deploy phase alone, so every ssh after preflight — the
#  upload, the login, the pull, the roll, the HEALTH GATE'S OWN POLL, both /management/info probes, the
#  rollback and the deployments.log append — still hung on the TCP default. Measured against a
#  blackholed address: 136s per connect unbounded, 8s with the timeout, which is 24 iterations × 5
#  services × 136s — about four and a half HOURS — before the gate could say anything at all.
#
#  EVERY INVOCATION CARRIES IT NOW, and the measure has to be named or the number is meaningless:
#  in COMMAND POSITION, over the comment-stripped and continuation-joined file, there are 12 `ssh`
#  and 1 `scp` — one of the ssh being host_run's, so ELEVEN were the deploy-phase ones this closed.
#  Two other measures of the same thing give other numbers and both are right: lines carrying the
#  `"${SSH_OPTS[@]}"` expansion is 13 (equal to the first, which is how you know none was missed),
#  and invocations ANCHORED at the start of a line is 8, because five of them are written inside
#  `$( )`, after a pipe, or after `if`. D78 §7 enumerates all thirteen by line; re-derive rather
#  than quoting a number here. See decisions.md D78 §7, §13 and backlog NEW-36.
#
#  Optional ON THE HOST, in $REMOTE_PATH/secrets.env or .env — the founding brokerage terms
#  (decisions.md D57, backlog NEW-18). All five may be left unset and an estate that sets none is
#  priced correctly; the defaults are the prototype's 12% commission and 3-day payout lag:
#     HC_BROKERAGE_COMMISSION_RATE  HC_BROKERAGE_PAYOUT_LAG_DAYS  HC_BROKERAGE_CURRENCY
#     HC_BROKERAGE_FREE_CANCELLATION_HOURS  HC_BROKERAGE_LATE_CANCELLATION_PCT
#
#  They are deliberately NOT in the required-secret list below, for the same reason a payment
#  provider's secret is not: absence is a working configuration. A value that is set and unreadable
#  refuses to start, which the health gate reports.
#
#  That default is 0 and is not an oversight. Production never seeds, so the honest count on a fresh
#  estate is 0, and a failing smoke test does not warn here — it rolls the deployment back. The check
#  requires a NUMBER, not a positive one; see smoke_test. Raise the floor to 1 once there is real
#  data, at which point an estate answering 0 is a failure and should roll back.
#
#  Required ON THE HOST, in $REMOTE_PATH/secrets.env, and NOT here. TWELVE values, all of them `:?`
#  in docker-compose.prod.yml, all of them checked in preflight by name before the stack is touched:
#     JWT_BASE64_SECRET       this ESTATE's signing key   (decisions.md D37 — NOT the platform key)
#     HC_PRIVACY_PEPPER       the erasure pepper           (decisions.md D35)
#     HC_GATEWAY_ADMIN_PASSWORD  the FIRST administrator   (decisions.md D61)
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
#  Those twelve are the stack's long-lived values and this script never sees them. It generates .env
#  on every deploy and overwrites what was there, so anything kept in .env survives exactly until
#  the next deploy — which is why docker-compose.prod.yml's two `:?` variables lived in a file that
#  could not hold them, and why every production `up` would have died on
#  "required variable JWT_BASE64_SECRET is missing a value" the first time anyone ran one. The compose
#  file's own comment beside HEALTHCONNECT_PRIVACY_PEPPER said the pepper "belongs with the
#  platform's long-lived secrets, not in a per-deploy .env that deploy-prod.sh regenerates"; this
#  is the file that makes that sentence true.
#
#  secrets.env is created once, out of band, and never written by this script. Create it ON the
#  server rather than piping it there, so no value ever exists in a local shell history:
#
#      ssh $HC_PROD_HOST
#      mkdir -p /srv/healthconnect && cd /srv/healthconnect
#      umask 077 && cat > secrets.env      # paste all twelve, filled in, then Ctrl-D
#      chmod 600 secrets.env
#
#  JWT_BASE64_SECRET IS GENERATED FRESH — `head -c 64 /dev/urandom | base64 -w0` — AND IS NOT THE
#  PLATFORM KEY. This block did NOT say that until 2026-09-05; it named ~/webroot/01-healthconnect/.env
#  instead, and that file holds the key hc-admin, hc-patient and hc-professional share, which
#  hc-market deliberately does not (decisions.md D37). Nothing would have failed — HS512 does not
#  care which random bytes it is — while these five services acquired the ability to mint tokens the
#  other three products accept, and an hc-admin token acquired authority here.
#
#  There is no `iss` and no `aud` anywhere in this estate, so nothing would ever have refused such a
#  token. The guard is mechanical instead and lives in CI ("No deploy path may point the signing key
#  at the platform's shared key"), which is why the wording here matters rather than merely reads
#  well: it is checked.
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
SECRET_KEYS=(JWT_BASE64_SECRET HC_PRIVACY_PEPPER HC_GATEWAY_ADMIN_PASSWORD)
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
# The two files in $REMOTE_PATH, and the number of stores the second one declares.
#
# THE APPLICATION FILE IS NAMED EXPLICITLY BELOW rather than left to compose's file discovery. That
# was tolerable while this directory held one compose file; deploy/prod-server/ put a second one
# beside it, and the only thing keeping them apart is the README telling an operator to rename it to
# data-compose.yml on the way in. Compose prefers `compose.yml` over `docker-compose.yml`, so an
# operator who copies the data tier under its repository name silently redirects every later pull,
# up, exec and ps in this script at the DATA project. It fails loudly (`no such service
# hc-market-gateway`) rather than quietly, which is the only reason this was not worse — and naming
# the file costs one word. deploy/prod-server/start already names both of its own.
APP_COMPOSE_FILE="docker-compose.yml"
DATA_COMPOSE_FILE="data-compose.yml"
DATA_STORE_COUNT=5
# Every remote compose invocation goes through this. Two --env-file arguments, in this order: the
# later file wins, and the generated .env must never be able to override a secret. Naming any
# --env-file disables compose's automatic .env loading, so both have to be listed.
REMOTE_COMPOSE="docker compose --env-file .env --env-file $SECRETS_FILE -f $APP_COMPOSE_FILE"

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

# ---- ASKING THE HOST SOMETHING, AND ESTABLISHING WHICH HOP ANSWERED -------------------------------
#
# EVERY REMOTE PROBE IN PREFLIGHT GOES THROUGH host_run, and it exists because one message covered
# five outcomes (decisions.md D75, backlog NEW-33). The host-network check was
#
#     ssh -o BatchMode=yes "$HOST" "docker network inspect $net >/dev/null 2>&1" \
#       || die "the '$net' network does not exist on $HOST. $net_hint"
#
# and $net_hint told the reader to go and create the network or start the owning stack. That is right
# for exactly one of the outcomes: ssh not reaching the host, ssh being refused, no docker on the
# host, a daemon that could not be asked, and the network genuinely being absent all produced it —
# and across a network the first two are the likeliest. It asks about the same docker object D71's
# first arm does — whether a network exists — and it is the family's first member with TWO HOPS in
# it, which is why it needed a mechanism rather than D71's four lines. D68 fix 2, D69 §10 and D71
# closed the earlier members; count the list and not the number, and each document says which of
# instances, object kinds and occasions it is counting.
#
# THE SENTINEL IS THE WHOLE MECHANISM, and it is there because a status cannot do this job.
# MEASURED, OpenSSH 10.2p1, against throwaway targets on this workstation: ssh exits **255** when it
# cannot connect (unresolvable name, refused port, timed-out connect, host key mismatch, key refused
# under BatchMode) and otherwise exits with the REMOTE command's status — so `exit 255` on the far
# side is indistinguishable from ssh never getting there. Two round trips would not fix it either:
# a host that becomes unreachable between them is a state neither probe saw, and this runs three
# times in a loop. So the remote command announces its own status on its own line, and the presence
# of that line — not any exit code — is what establishes that a shell on the host ran anything at
# all. D74's rule, one protocol along: establish who answered from the answer naming the question.
#
# BOTH STREAMS ARE CAPTURED, as in D71's arms and for the same reason: docker exits 1 both for a
# network that is absent and for a daemon that cannot be asked, printing `[]` on stdout for both
# (re-derived here on docker 29.8.0, through a real ssh, rather than taken from D71 — which itself
# carries a §7 for a claim that was wrong). Only the message tells them apart. This also means
# ssh's own words are available for the hop it failed at, which is what ssh_hint reads.
HOST_SENTINEL="__hc_remote_status__"
HOST_STATUS=0
HOST_OUTPUT=""
# BatchMode is deliberate and is NOT a candidate for "just let it prompt": a deploy that stops
# halfway waiting for a passphrase is worse than one that does not start. The timeout is the value
# the ssh check has always carried, now applied to every probe — an unbounded connect means the
# refusal never arrives, and a message nobody waits for is the same as no message.
SSH_OPTS=(-o BatchMode=yes -o "ConnectTimeout=${HC_SSH_TIMEOUT:-8}")
# What to tell an operator whose ssh never reached a shell. The CAUSE is established by the missing
# sentinel; this only chooses the remedy, from ssh's own stderr — and it degrades to the generic arm
# if a future OpenSSH words one of them differently, which leaves the cause correct and the advice
# merely unspecific. That is the direction to fail in: the message quotes what ssh actually said
# either way. Every branch below was measured on this workstation against a throwaway target.
#
# NOTE THE ASYMMETRY WITH THE REMOTE HALF, because it is the honest part of this. The ssh client is
# LOCAL — its words are produced by the same binary a deploy would use — so keying on them is a
# measurement. What a production host's docker says is not measurable from here (decisions.md D49:
# nothing in that path has ever run against a host), which is why the remote arms key on a status
# the remote shell reports, plus D71's already-measured two-literal absence match, and never on
# prose nobody here has read.
#
# ONE STATE BREAKS THAT ASYMMETRY AND IS WORTH KNOWING: a connection dropped after the remote command
# began writing arrives here with the FAR SIDE's words in `$1` and no sentinel, so a remote `grep:
# …: Permission denied` would select the "check ssh-add -l" arm. It costs a remedy and never a cause
# — the cause is the missing sentinel — and the message quotes what actually arrived, so the reader
# sees the mismatch. Not repaired by parsing harder: telling the two apart needs a channel this has
# no access to.
ssh_hint() {
  case "$1" in
    *"Could not resolve hostname"*)
      printf 'That name does not resolve here. HC_PROD_HOST is an ssh TARGET — `webserver`, the alias every sibling stack uses, or an entry in ~/.ssh/config — not a URL and not the site hostname.' ;;
    *"Connection refused"*|*"onnection timed out"*|*"No route to host"*|*"Network is unreachable"*)
      printf 'The name resolved and nothing answered on the ssh port: the host is down, the port is closed, or you are not on a network that can reach it. None of that is about this stack.' ;;
    *"Host key verification failed"*|*"REMOTE HOST IDENTIFICATION HAS CHANGED"*|*"o matching host key"*)
      printf 'Something answered and ssh refused it — the key it presented is not the one ~/.ssh/known_hosts records for this target. Establish WHY before removing the entry: a rebuilt host and an interception look identical from here.' ;;
    *"Permission denied"*|*"Too many authentication failures"*)
      printf 'The host answered and refused the key. This script never prompts (BatchMode=yes), so an agent holding the key must be running — check `ssh-add -l` — or the identity must be named for this target in ~/.ssh/config.' ;;
    *) printf 'ssh printed its own reason above. Nothing on the host was asked, so nothing about the stack there is established either way.' ;;
  esac
}
# host_run <what is being asked, for the refusal> <sh to run on the host>
#
# Sets HOST_STATUS to the REMOTE command's own status and HOST_OUTPUT to everything the far side
# printed on either stream. Dies itself — with one message, at every call site — when ssh did not
# reach a shell, because that cause and its remedy are the same wherever it is asked from; the
# `asking` clause is the only part that differs, and it is the fact the sentence carries about its
# own site (decisions.md D69 §5's discriminator, applied to a helper). It deliberately carries no
# advice about the stack: a remedy printed against the wrong cause is the whole of NEW-33.
#
# The sentinel line is printed with a LEADING newline, so it is always a line of its own even when
# the remote command's last write had none — otherwise the status would be appended to a line of
# output and that line would then be stripped with it.
#
# THE PROBE IS RUN IN A SUBSHELL ON THE FAR SIDE, and that is not decoration: without the
# parentheses a remote command containing `exit` terminates the remote shell before the sentinel is
# printed, and host_run then reports an ssh that never arrived for a command that ran perfectly.
# None of the six probes below says `exit` — this was found by driving the shipped function with one
# that did — so what the wrap buys is that the seventh cannot reintroduce NEW-33 by accident.
host_run() {
  local asking="$1" wrapped raw="" line said
  HOST_STATUS=0; HOST_OUTPUT=""
  wrapped="($2"$'\n'')'$'\n''printf "\n%s %s\n" "'"$HOST_SENTINEL"'" "$?"'
  raw="$(ssh "${SSH_OPTS[@]}" "$HOST" "$wrapped" 2>&1)" || true
  # `|| true` on the grep, not tidiness: `set -Eeuo pipefail` is on, an unmatched grep exits 1, and
  # the substitution would then abort the script through the ERR trap — with the ssh diagnosis this
  # function exists to print never reaching anybody. Not matching IS the answer here.
  line="$(printf '%s\n' "$raw" | { grep -F "$HOST_SENTINEL " || true; } | tail -1)"
  if [[ -z "$line" ]]; then
    said="$(printf '%s' "$raw" | tr '\n' ' ')"
    # "NO ANSWER ARRIVED" AND NOT "SSH NEVER GOT THERE", because one state reaches this branch with
    # the remote command having run: a connection dropped after it started writing carries far-side
    # output and no sentinel. Rare, and the wording is what keeps the sentence true — the cause this
    # branch can establish is that no answer came back, never that nothing ran. Whatever arrived is
    # quoted verbatim either way. See decisions.md D75 §7.
    die "could not $asking — no answer from a shell on $HOST arrived, so NOTHING about the stack there was established, least of all that anything is missing. $(ssh_hint "$raw") ssh said: ${said:-«nothing at all»}"
  fi
  HOST_STATUS="${line##* }"
  # A sentinel whose status is not a number means the far side is not doing what this function
  # assumes — refuse rather than fall through, because every caller below branches on it and a
  # non-numeric value would take the `*)` arm and be reported as the remote command failing.
  [[ "$HOST_STATUS" =~ ^[0-9]+$ ]] \
    || die "could not $asking — $HOST answered with a status this script cannot read ('$line'). Nothing about the stack there is established."
  HOST_OUTPUT="$(printf '%s\n' "$raw" | { grep -vF "$HOST_SENTINEL " || true; })"
}
# 127 is the shell's status for a command it cannot find (POSIX, and measured here through a real
# ssh with PATH emptied: `bash: line 1: docker: command not found`). It is keyed on the STATUS and
# not on that sentence, because the wording belongs to whichever shell the host runs.
no_docker_on_host() {
  die "ssh reached $HOST and there is no \`docker\` command there — $HOST_OUTPUT. This script deploys applications onto a host that already runs docker and compose v2; it does not install either. Nothing about the stack on that host is established, and in particular this is NOT a missing network or a stopped service."
}
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
    HC_GATEWAY_ADMIN_PASSWORD)
      printf 'The first administrator on this estate (decisions.md D61). Generate one with `head -c 24 /dev/urandom | base64 -w0`. It is read ONLY by a gateway database that has no administrator yet: once the account exists, and however its password is later rotated, this value is never consulted again — so keep it here rather than removing it, and do not expect changing it to reset anybody. Absent, the gateway refuses to create an administrator rather than falling back to the value published in this repository, and the deploy is rolled back by the health gate.' ;;
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
  # Checked HERE and not in smoke_test, which runs after the stack has already been rolled: a typo in
  # this one refuses the deploy before anything is touched rather than sending a healthy estate into
  # rollback over an unparseable floor.
  [[ "${HC_SMOKE_MIN_PROFESSIONALS:-0}" =~ ^[0-9]+$ ]] \
    || die "HC_SMOKE_MIN_PROFESSIONALS must be a whole number (got '$HC_SMOKE_MIN_PROFESSIONALS'). It is the smoke test's minimum catalogue count; unset it to accept an empty catalogue."

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

  # THIS IS THE ARM AN OPERATOR REACHES FIRST, so it is fixed here rather than left for NEW-33's own
  # line further down — D71 §2's finding, one script along. Its message was an explicit two-way fold
  # ("cannot reach $HOST over ssh, OR docker compose v2 is missing there"), and it fires BEFORE the
  # network loop: repairing only the loop would have shipped a preflight whose first refusal names
  # two causes and whose fourth names one.
  #
  # `ok "host reachable"` claims less than it reads, and that is worth knowing before trusting it:
  # measured through a real ssh, `docker compose version` answers 0 with `DOCKER_HOST` pointed at a
  # socket that does not exist. It establishes ssh, a docker CLI and the compose v2 plugin — and
  # NOT that a daemon will answer. The network loop below is where the daemon is first asked, which
  # is why its "could not be asked" arm is a live path rather than a theoretical one.
  log "checking ssh to $HOST"
  if (( DRY_RUN )); then
    skipped "would check ssh to $HOST — NOT contacted"
  else
    host_run "reach $HOST over ssh" 'docker compose version >/dev/null'
    case "$HOST_STATUS" in
      0)   ok "host reachable — ssh works and docker compose v2 answers there" ;;
      127) no_docker_on_host ;;
      # HEDGED, and it is the only residual arm in this file that names a cause at all: the others
      # quote what the host said and stop. `docker compose version` fails for very few reasons — the
      # plugin missing is the one worth naming, and measured, it survives an unanswerable daemon — but
      # the exit status and the output are the evidence and the sentence says which is which.
      *)   die "ssh reached $HOST and \`docker compose version\` failed there (exit $HOST_STATUS): $HOST_OUTPUT. The host is reachable and the credential works, so this is about docker rather than about the connection; the likeliest cause is compose v2 missing beside the docker CLI, and the status and output above are what the host actually said. Every remote command below is a \`docker compose\` invocation." ;;
    esac
  fi

  # The two secrets docker-compose.prod.yml requires with `:?`. Checked HERE, before the stack is
  # touched, because the alternative is where this used to land: `docker compose up` on the host,
  # after .env has already been overwritten and .env.previous rotated, dying on
  # "required variable JWT_BASE64_SECRET is missing a value" — a stack half-rolled over a variable
  # nothing in the pipeline ever supplied. render_env has never emitted either of them and never
  # will; they live in secrets.env, which this script does not generate.
  #
  # Presence and non-emptiness only. The values stay on the host: nothing here reads them, so
  # nothing here can print them, and --dry-run cannot leak what it never fetched.
  log "checking $REMOTE_PATH/$SECRETS_FILE on $HOST"
  if (( DRY_RUN )); then
    skipped "would confirm $REMOTE_PATH/$SECRETS_FILE holds all ${#SECRET_KEYS[@]} secrets and ${#CONNECTION_KEYS[@]} connection values — NOT contacted"
    for v in "${SECRET_KEYS[@]}" "${CONNECTION_KEYS[@]}"; do skipped "  $v"; done
  else
    # BOTH OF THESE ASSERTED AN ABSENCE FROM A DISCARDED SSH STATUS, exactly as the network loop did:
    # an ssh that never arrived was reported as "secrets.env is missing" and then as "$v is not set",
    # for all twelve, which sends an operator to a file that is fine. `test -s` folding "missing" and
    # "empty" together is deliberate and stays — that is one remedy stated as a disjunction, not a
    # cause asserted (decisions.md D75, and D71 §5 on what the rule is actually about).
    host_run "read $REMOTE_PATH/$SECRETS_FILE on $HOST" "test -s '$REMOTE_PATH/$SECRETS_FILE'"
    case "$HOST_STATUS" in
      0) : ;;
      1) die "$HOST:$REMOTE_PATH/$SECRETS_FILE is missing or empty. It holds the estate's long-lived secrets (${SECRET_KEYS[*]}) and the five stores' connection values, it is created once by hand, and this script deliberately never writes it — see the header for the exact command and deploy/prod-server/secrets.env.example for the template. Without it every service refuses to start on the compose file's own :? checks." ;;
      *) die "whether $HOST:$REMOTE_PATH/$SECRETS_FILE is there could not be established — the check itself failed on the host (exit $HOST_STATUS): $HOST_OUTPUT. This is not a statement about the file." ;;
    esac
    for v in "${SECRET_KEYS[@]}" "${CONNECTION_KEYS[@]}"; do
      host_run "look for $v in $REMOTE_PATH/$SECRETS_FILE on $HOST" \
        "grep -qE '^[[:space:]]*$v=.' '$REMOTE_PATH/$SECRETS_FILE'"
      case "$HOST_STATUS" in
        0) ok "$v present" ;;
        1) die "$v is not set in $HOST:$REMOTE_PATH/$SECRETS_FILE. $(secret_hint "$v")" ;;
        # grep answers 2 for a file it cannot READ, which is a live state for a 0600 file owned by
        # somebody else — measured at 2 with `No such file or directory` through a real ssh. Reported
        # as "$v is not set" it reads as a value to add to a file the operator cannot open.
        *) die "$HOST:$REMOTE_PATH/$SECRETS_FILE could not be read while looking for $v (grep exit $HOST_STATUS): $HOST_OUTPUT. Nothing is established about $v, or about the ${#SECRET_KEYS[@]} secrets and ${#CONNECTION_KEYS[@]} connection values beside it — check the file's ownership and mode for the account this ssh authenticates as." ;;
      esac
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
  # NEW-33's OWN LINE. `$net_hint` is right for exactly ONE of the outcomes this used to fold, and
  # actively wrong for the others — it tells the reader to create a network or start a stack, which
  # is no remedy at all for an ssh that never arrived or a daemon that could not be asked. So it is
  # printed on the absence arm and nowhere else, which is the same call D71 §3 made about `$fix`.
  #
  # THE ABSENCE MATCH IS D71's, VERBATIM AND FOR ITS MEASURED REASON: `Error response from daemon`
  # AND `not found`, in that order. Re-derived here on docker 29.8.0 through a real ssh rather than
  # inherited — absent network and unanswerable daemon both exit 1 and both print `[]` on stdout,
  # differing only in the sentence, so a status check alone would produce a refusal that is honest
  # about *whether* it knows and silent about *what* it knows. Requiring the sentence only a daemon
  # that ANSWERED can produce is what stops the other direction: `not found` on its own is carried
  # by a CLI with no daemon behind it, and reported as an absent network that is NEW-33's cost
  # arriving through NEW-33's fix.
  #
  # It fails closed the other way — a future daemon wording an absence differently routes to "could
  # not be asked", which is the wrong cause and still stops the deploy.
  log "checking host networks"
  for net in "${HC_NETWORK:-infranet}" "${HC_DATA_NETWORK:-hcmarketnet}" "${HC_MONITORING_NETWORK:-monitoring}"; do
    (( DRY_RUN )) && { skipped "would ask $HOST whether the '$net' network exists — NOT contacted"; continue; }
    if [[ "$net" == "${HC_DATA_NETWORK:-hcmarketnet}" ]]; then
      net_hint="It is hc-market's own and carries the five databases. Create it once on the host with \`cd $REMOTE_PATH && ./infra.sh\` — see deploy/prod-server/README.md."
    else
      net_hint="It is host-wide and this stack does not create it — start the owning stack first (~/webroot/00-infrastructure for infranet, ~/webroot/02-monitoring for monitoring)."
    fi
    host_run "ask $HOST whether the '$net' network exists" "docker network inspect '$net'"
    if (( HOST_STATUS == 0 )); then
      ok "network $net present"
      continue
    fi
    (( HOST_STATUS == 127 )) && no_docker_on_host
    case "$HOST_OUTPUT" in
      *"Error response from daemon"*"not found"*)
        die "the '$net' network does not exist on $HOST. $net_hint Do NOT drop it from docker-compose.prod.yml." ;;
      *)
        # THIS SENTENCE DELIBERATELY DOES NOT QUOTE THE OTHER ARM'S REMEDY, not even to say it does
        # not apply. It read "…starting a plane or running ./infra.sh is not the remedy" first, and
        # the check guarding this arm — which refuses a message carrying the absence arm's advice —
        # went red on the negation. A refusal that names a command an operator should not run is one
        # they will run.
        die "docker could not be asked whether the '$net' network exists on $HOST, so whether this stack can join it is unestablished. This is NOT a statement that the network is missing, and the remedy on the absence arm above is not the remedy here: $HOST_OUTPUT" ;;
    esac
  done

  # AND THE FIVE STORES, WHICH ARE A DIFFERENT COMPOSE PROJECT AND THEREFORE INVISIBLE TO EVERYTHING
  # ELSE HERE. `depends_on` cannot express them — the applications are project `healthconnect` and
  # the stores are `hc-market-data`, so compose has no idea the other project exists — which is the
  # same reason the shared networks are checked by hand above rather than declared as a dependency.
  #
  # The network check passing says only that hcmarketnet EXISTS, not that anything is on it. A deploy
  # rolled while the data tier is down previously passed preflight in full, overwrote .env, rotated
  # .env.previous, pulled, rolled, failed Liquibase in all five services, failed the health gate and
  # then rolled back — a five-service outage caused by the deploy, over a condition that was true
  # before it started and is one ssh away.
  #
  # Counted rather than merely listed, and `ps -a` rather than `ps`: compose ps without -a shows only
  # RUNNING containers, so a store that has exited is not in the output at all and an empty result
  # reads as "nothing wrong". That is the same fail-open deploy/prod-server/start carried.
  log "checking the data tier on $HOST"
  if (( DRY_RUN )); then
    skipped "would confirm all $DATA_STORE_COUNT stores in $REMOTE_PATH/$DATA_COMPOSE_FILE are running — NOT contacted"
  else
    # THE COUNT IS DONE HERE, NOT ON THE HOST, and that is the whole of this hunk. The remote
    # pipeline was `ps -a … 2>/dev/null | grep -c ' running$' || true`, which cannot fail: compose's
    # error went to /dev/null, the status was grep's and then discarded, and `[[ =~ ]] || running=0`
    # turned every remaining failure into the number 0. So a wrong --path, a missing
    # data-compose.yml, an unanswerable daemon and an ssh that never arrived all read as
    # "0 of 5 stores running" — a message about a data tier, for four things that are not one.
    local running
    host_run "ask docker compose about the data tier on $HOST" \
      "cd '$REMOTE_PATH' && docker compose --env-file '$SECRETS_FILE' -f '$DATA_COMPOSE_FILE' ps -a --format '{{.Service}} {{.State}}'"
    if (( HOST_STATUS != 0 )); then
      (( HOST_STATUS == 127 )) && no_docker_on_host
      die "docker compose could not be asked about the data tier on $HOST (exit $HOST_STATUS): $HOST_OUTPUT. Nothing is established about the five stores — this is not a report that they are down. Check --path (currently $REMOTE_PATH), that $DATA_COMPOSE_FILE is there under that name, and that the daemon is answering; see deploy/prod-server/README.md."
    fi
    # `|| true` because `grep -c` exits 1 when it counts none, and 0 is the answer being asked for.
    running="$(printf '%s\n' "$HOST_OUTPUT" | { grep -c ' running$' || true; })"
    (( running == DATA_STORE_COUNT )) \
      || die "the data tier is not up on $HOST — $running of $DATA_STORE_COUNT stores running in $REMOTE_PATH/$DATA_COMPOSE_FILE. This stack deploys applications and never provisions a database; the stores are installed once and started with \`cd $REMOTE_PATH && ./start\`. Deploying now would roll five services onto databases that are not there, fail Liquibase in all of them, and roll back. See deploy/prod-server/README.md."
    ok "data tier up — $running stores running"
  fi
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
  run ssh "${SSH_OPTS[@]}" "$HOST" "mkdir -p '$REMOTE_PATH'"

  log "uploading compose stack and env"
  if (( DRY_RUN )); then
    printf '%s  [dry-run] scp %s %s:%s/%s%s\n' "$c_dim" "$COMPOSE_TEMPLATE" "$HOST" "$REMOTE_PATH" "$APP_COMPOSE_FILE" "$c_reset"
    render_env | sed 's/^/    /'
  else
    scp -q "${SSH_OPTS[@]}" "$COMPOSE_TEMPLATE" "$HOST:$REMOTE_PATH/$APP_COMPOSE_FILE"
    render_env | ssh "${SSH_OPTS[@]}" "$HOST" "cat > '$REMOTE_PATH/.env.next'"
    ssh "${SSH_OPTS[@]}" "$HOST" "cd '$REMOTE_PATH' && { [ -f .env ] && cp .env .env.previous || true; } && mv .env.next .env"
  fi

  if (( DO_PUSH )); then
    # The same rule the preflight login follows, and it was the one place still breaking it: a bare
    # `▸ authenticating the host to ghcr.io` printed under --dry-run while the command below it was
    # guarded, so the output read as though the host had been contacted and its credentials
    # exercised. Nothing here may claim a step it skipped.
    if (( DRY_RUN )); then
      skipped "would authenticate $HOST to $REGISTRY_HOST as $REGISTRY_USER — NOT contacted"
    else
      log "authenticating the host to $REGISTRY_HOST"
      printf '%s' "$REGISTRY_TOKEN" \
        | ssh "${SSH_OPTS[@]}" "$HOST" "docker login '$REGISTRY_HOST' -u '$REGISTRY_USER' --password-stdin >/dev/null"
    fi
    log "pulling $TAG"
    run ssh "${SSH_OPTS[@]}" "$HOST" "cd '$REMOTE_PATH' && $REMOTE_COMPOSE pull $(compose_names)"
  fi

  log "rolling services"
  run ssh "${SSH_OPTS[@]}" "$HOST" "cd '$REMOTE_PATH' && $REMOTE_COMPOSE up -d --remove-orphans $(compose_names)"
}

# ---- THE GATE'S POLLS FOLD; ITS EXHAUSTION MAY NOT — decisions.md D78, backlog NEW-36 ------------
#
# D71 §5's rule is `a die may not fold; a warn and a poll may`, and its stated edge is a poll whose
# EXHAUSTION is fatal. This is that edge, and until now it was the one place in either script with
# nothing standing beside it. The 24 probes below discard both streams and every status DELIBERATELY
# — during a wait an unanswerable daemon or a host that blinked must be a retry, and a status check
# inside the loop makes a one-second flake fatal on the estate's slowest gate — but what the loop
# ends in is `return 1`, and the router at the bottom of this file turns that into a `rollback`. So a
# host that went away mid-deploy named five HEALTHY services as unhealthy and rolled the stack back.
#
# A STATUS CHECK INSIDE THE LOOP WOULD NOT HAVE ATTRIBUTED ANYTHING ANYWAY, which is worth knowing
# before reaching for the cheap fix. MEASURED, docker 29.8.0, against throwaway containers on this
# workstation: `compose exec` exits **1** for a service whose port refuses the connection, **1** for
# a service that is not running, **1** for a service this compose file does not declare, and **1**
# for a daemon that cannot be asked — four states, one status, and ssh's own 255 on top of them
# (decisions.md D75 §1). The attribution has to come from a DIFFERENT QUESTION, asked once, after
# the loop. That is gate_exhausted.
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
      #
      # SSH_OPTS, because this probe was one of the eleven deploy-phase invocations with no
      # ConnectTimeout (NEW-36's own second half). MEASURED here: an ssh with no ConnectTimeout
      # spends **136s** on a blackholed address before answering, so 24 iterations × 5 services was
      # **4.5 hours** to a refusal, not the four minutes the item assumed. With the timeout it is 8s
      # per probe. It bounds the CONNECT and not the remote command, so a slow readiness probe is
      # unaffected — and CI asserts the array still carries both options, which it did not until
      # D78's review: the check lifted SSH_OPTS and read only HOST_SENTINEL's value, so emptying or
      # renaming it left every assertion green.
      ssh "${SSH_OPTS[@]}" "$HOST" "cd '$REMOTE_PATH' && $REMOTE_COMPOSE exec -T $(compose_name "$s") bash -c \
        'exec 3<>/dev/tcp/localhost/8080 && printf \"GET /management/health/readiness HTTP/1.0\\r\\n\\r\\n\" >&3 && grep -q UP <&3'" \
        >/dev/null 2>&1 || bad+=" $s"
    done
    [[ -z "$bad" ]] && { ok "all services report READY"; return 0; }
    (( waited += 10 )); sleep 10
    # EVERY ARM OF gate_exhausted EITHER DIES OR RETURNS 0, and the gate's own answer is 1 either
    # way. That is not tidiness: `health_gate` is only ever called from a condition — `if health_gate
    # && smoke_test` and `health_gate && ok … || die …` — where bash suppresses `set -e` for the
    # whole command and every function it calls, so a non-zero return from here is safe today and is
    # exactly the kind of fact that stops being true when a call site moves.
    (( waited >= HEALTH_TIMEOUT )) && { gate_exhausted "$bad"; return 1; }
    printf '  waiting%s (%ss)\n' "$bad" "$waited"
  done
}

# gate_exhausted <the services the loop gave up on>
#
# ONE ATTRIBUTING PROBE, AFTER THE LOOP AND NEVER INSIDE IT. `compose ps -a` asks a different
# question from the readiness probe above, and its status is honest where `exec`'s is not. MEASURED,
# 29.8.0: **0** with a line per container when the daemon answered, **1** carrying docker's own
# sentence when it could not be asked, and **0 with nothing at all** when the project has no
# containers. Routed through host_run, so an ssh that never reached a shell is refused by its own hop
# (decisions.md D75) instead of being counted as five services that failed.
#
# `{{.Health}}` IS A SECOND OPINION AND NOT A REPEAT OF THE QUESTION, which is the only reason the
# blip case below is decidable at all. Every app service in docker-compose.prod.yml carries the same
# /dev/tcp readiness healthcheck, run by the daemon INSIDE the host every 15s, so its verdict cannot
# be affected by the hop the 24 probes cross. If the daemon says the services the gate gave up on are
# healthy, then what failed is this end of the wire.
#
# WHICH WAY IT FAILS, AND WHY (decisions.md D78 §4.1). Rolling a healthy estate back because the host
# went away is the harm this exists to prevent; a stack that genuinely failed and is NOT rolled back
# is the opposite harm. So the rollback happens only on the arm where the host ANSWERED and its own
# answer agrees that something is not ready — everything else is a `die`, which stops the deploy and
# reverts nothing. Nothing is lost by refusing: `rollback` needs this same host for all four of its
# steps (a host_run for the previous tag, an ssh to restore .env and roll, and this gate again), so
# an estate that cannot be asked cannot be reverted either, and the refusal names the by-hand
# command for the moment the host comes back.
gate_exhausted() {
  local bad="$1" svc unproven="" logs_of=""
  host_run "ask $HOST what state the services are in, now that the gate has timed out" \
    "cd '$REMOTE_PATH' && $REMOTE_COMPOSE ps -a --format '{{.Service}} {{.State}} {{.Health}}'"
  (( HOST_STATUS == 127 )) && no_docker_on_host
  (( HOST_STATUS == 0 )) \
    || die "the health gate timed out after ${HEALTH_TIMEOUT}s and docker compose on $HOST could not then be asked what state the services are in (exit $HOST_STATUS): $HOST_OUTPUT. So it is NOT established that$bad failed to become ready — the probes above fold a daemon that cannot be asked into the same silence a service that is still starting produces, and they cross a network to do it. NOTHING HAS BEEN ROLLED BACK, deliberately: a rollback needs this same host, and reverting a healthy estate over a daemon nobody could ask is the failure this refusal exists to prevent. Establish what is actually there — \`ssh $HOST 'cd $REMOTE_PATH && $REMOTE_COMPOSE ps -a'\` — then either fix the gate failure and redeploy, or revert by hand with \`./deploy-prod.sh --rollback --host $HOST\`."
  # THE HOST ANSWERED AND KNOWS OF NOTHING. Measured: `ps -a` exits 0 with empty output for a project
  # that has no containers. That is not "they never became ready" and it is not this gate's subject —
  # `up -d` ran through `run`, so a failure there would have printed its own command through the ERR
  # trap. What is left is a --path or a compose project that is not the one just rolled, and an older
  # tag cannot fix either.
  [[ -n "$HOST_OUTPUT" ]] \
    || die "the health gate timed out after ${HEALTH_TIMEOUT}s and docker compose on $HOST reports NO CONTAINERS AT ALL for $REMOTE_PATH/$APP_COMPOSE_FILE — not stopped ones, none. So$bad was never established to be unready; there is nothing there to be unready. Nothing has been rolled back: an older tag is not the remedy for a stack that is not running under this project. Check --path (currently $REMOTE_PATH) and that $APP_COMPOSE_FILE on the host is the file this deploy uploaded."
  # THE BLIP, WHICH IS THE ONE CASE ONLY A SECOND OPINION CAN SEE. A host unreachable for the last
  # poll alone puts every service in $bad while four of them were ready a second earlier; docker's own
  # healthcheck ran inside the host throughout and is the evidence for that reading.
  for svc in $bad; do
    if printf '%s\n' "$HOST_OUTPUT" | grep -qE "^$(compose_name "$svc")[[:space:]]+running[[:space:]]+healthy[[:space:]]*$"; then
      continue
    fi
    unproven+=" $svc"
    logs_of+=" $(compose_name "$svc")"
  done
  if [[ -z "$unproven" ]]; then
    printf '%s\n' "$HOST_OUTPUT" | sed 's/^/    /'
    die "the health gate timed out after ${HEALTH_TIMEOUT}s, and docker on $HOST reports every service it gave up on —$bad — as RUNNING and HEALTHY, from the same readiness probe run inside the host (see the table above). So what failed is this end of the wire and not the estate: the deploy's probes cross an ssh, docker's healthcheck does not. NOTHING HAS BEEN ROLLED BACK, which is the whole point of this arm — the stack on $HOST is $TAG and is answering. This deploy is not recorded in deployments.log, so re-run it once the link is steady if you want that record; \`./deploy-prod.sh --rollback --host $HOST\` is there if you want $TAG off the host instead."
  fi
  # ESTABLISHED UNREADY: the host answered, and its own answer agrees. This is the one arm that
  # returns, and the router rolls the stack back on it.
  warn "the health gate timed out after ${HEALTH_TIMEOUT}s. The host ANSWERED, so this is the services and not the hop:"
  printf '%s\n' "$HOST_OUTPUT" | sed 's/^/    /'
  # THE EVIDENCE THE ROLLBACK IS ABOUT TO DESTROY, which is what `deploy-dev.sh` puts on the same line
  # as its own `die` (decisions.md D71 §9) — ported here as evidence rather than as the decision,
  # because the decision is already made above. `up -d` at the previous tag RECREATES these
  # containers, so the failed tag's log is readable in this window and in no other.
  #
  # Through host_run for the same reason as the probe above, and its remote-status arm is a `warn`
  # rather than a `die`: unreadiness is already established, so a daemon that goes quiet between the
  # two round trips must not turn a correct rollback into a refusal. An ssh that stops answering
  # between them still dies inside host_run — and rightly, because the rollback below needs it.
  host_run "read the last log lines of$unproven on $HOST" \
    "cd '$REMOTE_PATH' && $REMOTE_COMPOSE logs --no-color --tail=40$logs_of"
  if (( HOST_STATUS == 0 )); then
    warn "  last 40 log lines from each service that never became ready:"
    printf '%s\n' "$HOST_OUTPUT" | sed 's/^/    /'
  else
    warn "  their logs could not be read (exit $HOST_STATUS): $HOST_OUTPUT"
    warn "  the rollback below recreates these containers, so read them now or not at all."
  fi
  warn "still unhealthy:$unproven — rolling back."
  return 0
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
  # --- AND IT REQUIRED `> 0`, WHICH A HEALTHY FRESH PRODUCTION ESTATE CANNOT SATISFY -------------
  #
  # Production does not seed (render_env writes HEALTHCONNECT_SEED_ENABLED=false, and the profile
  # pair double-locks it), so the honest count on a first deploy is 0. A failing smoke test is not
  # advisory here — it falls through to `rollback` at the bottom of this file — so the requirement
  # made the first deploy end in `die "no previous deployment recorded"` with the stack up, correct
  # and unrecorded, and the second one end in a SUCCESSFUL rollback of a deployment that had just
  # come up healthy. The estate could not have shipped again until it had data.
  #
  # So the test now asks the question it was always trying to ask, and the two answers it used to
  # conflate are separated:
  #
  #   not a number   the edge, the route, the gateway or catalog's datasource is broken. This is the
  #                  real check, and it exercises DNS, TLS, nginx, D28's route predicates and a
  #                  round trip to PostgreSQL — every one of which a count of 0 exercises too.
  #   0              a healthy estate with nothing in it. WARNED LOUDLY, and passed.
  #
  # `> 0` remains available as HC_SMOKE_MIN_PROFESSIONALS, which is where it belongs: once there is
  # real data, an estate that suddenly answers 0 IS a failure, and only the operator knows when that
  # day arrives. Opt-in rather than default because the wrong answer costs a rollback of a working
  # stack, and this script's rule is that a refusal must be about the deployment rather than about
  # what the deployment happens to contain. Validated in preflight, so a typo is refused before the
  # host is touched rather than after.
  local n min="${HC_SMOKE_MIN_PROFESSIONALS:-0}"
  n="$(curl -fsS --max-time 10 "$base/services/healthconnectcatalog/api/professionals/count" || echo "")"
  if [[ ! "$n" =~ ^[0-9]+$ ]]; then
    warn "catalogue smoke test failed — no number came back (got '${n:-nothing}')"
    warn "  GET $base/services/healthconnectcatalog/api/professionals/count"
    return 1
  fi
  if (( n < min )); then
    warn "catalogue answered $n, below the HC_SMOKE_MIN_PROFESSIONALS floor of $min"
    return 1
  fi
  if (( n == 0 )); then
    warn "catalogue answering, and it is EMPTY — 0 published professionals."
    warn "  On a fresh estate that is the honest answer: production never seeds. It is NOT the same"
    warn "  as a catalogue that cannot reach its database, which answers nothing at all — that is"
    warn "  the distinction this check makes, and why 0 is not a failure."
    warn "  Set HC_SMOKE_MIN_PROFESSIONALS=1 once there is real data, and 0 becomes a failure again."
  else
    ok "catalogue answering — $n published professionals"
  fi

  # --- CAN PAYOUT PRICE ANYTHING? (decisions.md D57, backlog NEW-18) ------------------------------
  #
  # The catalogue check above cannot see this and never could. brokerage_config was empty on every
  # estate that does not seed — which is production — because nothing outside the seeder and the
  # generated CRUD D54 deleted could write a row. A deploy of that estate came up healthy, PASSED the
  # catalogue smoke test, and then: BookingEventConsumer threw on the first booking.completed and
  # retried it for ever (no ledger row, /api/pro/earnings stuck at zero, booking perfectly happy), and
  # BrokerageResource answered 503, so every receipt was a 503. Both after the customer's money moved.
  #
  # THE REMEDY EXISTS FIRST, AND THAT ORDER IS THE POINT. A gate for a condition with no fix fails
  # every deploy until the fix ships — and a failing gate here does not warn, it rolls the stack back
  # (D49). BrokerageBootstrap writes the founding row during payout's context refresh, on every
  # environment, so by the time this runs a correct estate cannot be in the state being checked for.
  #
  # WHAT IT ASKS, AND WHY IT IS /management/info RATHER THAN /management/health.
  #
  # This was written against the aggregate health endpoint first, and a real prod boot against an empty
  # throwaway database settled it: /management/health answered DOWN on an estate whose founding row was
  # present and correct, because `binders.kafka` was down with no broker on that machine. A smoke test
  # reading the aggregate would therefore have FAILED A HEALTHY DEPLOY over a broker blip — and this
  # gate does not warn, it rolls back. That is WP-19's defect rebuilt by the check meant to prevent a
  # different one. Note that health_gate above already knew: it probes /management/health/readiness,
  # which is `readinessState,db` and deliberately not the aggregate.
  #
  # A named health GROUP would have been the other narrowing, and it is rejected in
  # BrokerageTermsHealthIndicator: a group is configuration, application.yml is regenerated wholesale,
  # and losing it would make this request a 404 and fail a healthy deploy in the other direction.
  # BrokerageTermsInfoContributor is a @Component in a new file — no configuration, and a regeneration
  # leaves it alone. CI asserts the class exists, that payout still exposes `info`, and that this line
  # still asks for it, so a build that reaches here cannot be missing the endpoint; that is what makes
  # failing CLOSED below safe.
  #
  # It prints the rate on success on purpose. A plausible-but-wrong founding value — 0.15 where 0.12
  # was meant — is the one failure FoundingTerms cannot validate, and a deploy that states what this
  # estate charges is the last moment a person can catch it.
  #
  # Asked of the CONTAINER over bash's /dev/tcp, exactly as health_gate does: /management is 404 at the
  # public edge on purpose, and the Jib images ship no curl.
  #
  # THIS PROBE STILL FOLDS, AND THAT IS A DECISION RATHER THAN AN OMISSION — decisions.md D78 §4.2,
  # which is where NEW-36 stopped. It is the same shape the health gate had one step along: `2>/dev/null
  # || true`, five outcomes, and a `return 1` that reaches the same `rollback`. Two things make it a
  # different call from the gate's.
  #
  # The MESSAGE already names both readings — "holds NO brokerage terms in force, or could not be
  # asked" — with a remedy paragraph that covers both, so nobody is sent to fix the wrong thing, which
  # is what this whole family is about (D71 §5 permits a `warn` to fold; the cost of NEW-33 was always
  # the remedy, not the status).
  #
  # And the DIRECTION TO FAIL IS THE OPPOSITE ONE. In the gate, an unestablished answer must not roll a
  # healthy estate back. Here the condition being checked for is D57's, and it is silent: an estate that
  # cannot price a booking passes every other check, then retries the first completed booking for ever
  # and answers 503 to every receipt, both after the customer's money has moved. So an unestablished
  # answer must NOT be allowed to ship — it fails closed, which means rolling back, which means the
  # fold costs nothing that reversing it would buy. Routing it through host_run would also change the
  # arm the gate's repair deliberately keeps: host_run refuses on the ssh hop, so a link that dropped
  # between the gate and here would leave a possibly-unpriced estate up rather than reverted.
  #
  # The gateway version probe below folds too and decides nothing at all — it `warn`s and the deploy
  # proceeds either way.
  local brokerage
  brokerage="$(ssh "${SSH_OPTS[@]}" "$HOST" "cd '$REMOTE_PATH' && $REMOTE_COMPOSE exec -T $(compose_name payout) bash -c \
    'exec 3<>/dev/tcp/localhost/8080 && printf \"GET /management/info HTTP/1.0\\r\\n\\r\\n\" >&3 && cat <&3'" 2>/dev/null || true)"
  if printf '%s' "$brokerage" | grep -qE '"termsInForce"[[:space:]]*:[[:space:]]*true'; then
    ok "payout holds brokerage terms in force — $(printf '%s' "$brokerage" \
      | grep -oE '"commissionRate"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | cut -d'"' -f4) commission"
  else
    warn "payout holds NO brokerage terms in force, or could not be asked."
    warn "  This is silent everywhere else: booking stays green, the catalogue check above passes, and"
    warn "  the first completed booking then retries for ever — no ledger row, no earnings — while"
    warn "  every receipt answers 503. Both land after the customer's money has moved."
    warn "  BrokerageBootstrap should have written the founding row at startup (decisions.md D57)."
    warn "  Look for 'brokerage: founded this estate's terms' in payout's log, and check that"
    warn "  HC_BROKERAGE_COMMISSION_RATE and friends are readable — a malformed one refuses startup."
    return 1
  fi

  # The version comes from the CONTAINER, not from the edge, and that is deliberate.
  #
  # /management is 404 at the public edge on purpose (prod-server/hc-market-app.conf): actuator
  # carries health detail, metrics, env, loggers and the build's git SHA, and `info` in particular
  # hands a stranger the exact build a CVE would be matched against. So this asks the gateway itself,
  # over the same bash /dev/tcp channel the health gate already uses — the Jib images ship no curl.
  #
  # Strictly better than the old form as well as merely possible: it reports what the DEPLOYED
  # container believes it is, rather than what the edge happens to route.
  if ssh "${SSH_OPTS[@]}" "$HOST" "cd '$REMOTE_PATH' && $REMOTE_COMPOSE exec -T $(compose_name gateway) bash -c \
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
  # ROUTED THROUGH host_run FOR THE SAME REASON AS PREFLIGHT'S FIVE (decisions.md D75). `|| true` on
  # an ssh whose answer is then tested for emptiness makes an unreachable host indistinguishable from
  # a first deploy — and this function is where every FAILED deploy lands, so it is the worst place in
  # the file to be told to go and look for a .env.previous that is sitting there intact. The status is
  # `cut`'s and therefore 0 even when grep matched nothing, which is why the discriminator here is the
  # OUTPUT rather than the status: empty means no HC_TAG line, and host_run has already refused if no
  # shell ran at all.
  local prev
  host_run "read the previous tag from $REMOTE_PATH/.env.previous on $HOST" \
    "cd '$REMOTE_PATH' && grep -m1 '^HC_TAG=' .env.previous 2>/dev/null | cut -d= -f2"
  # A non-zero status here is `cd` refusing the directory, not an absent file — reported as "no
  # previous deployment recorded" that is a wrong --path wearing a fact about the estate's history.
  (( HOST_STATUS == 0 )) \
    || die "the previous tag could not be read on $HOST (exit $HOST_STATUS): $HOST_OUTPUT. Nothing has been rolled back and nothing about this estate's deployment history is established — check --path, which is currently $REMOTE_PATH."
  prev="$(printf '%s' "$HOST_OUTPUT" | tr -d '[:space:]')"
  # THE FIRST DEPLOY HAS NO PREVIOUS ONE, and this is the path it reaches when its gates fail. Say
  # what state the host is in rather than only what could not be done: the stack is still running
  # whatever was just rolled onto it, nothing has been reverted, and the operator's next move is to
  # look at why the gate failed — not to hunt for a .env.previous that was never going to exist.
  [[ -n "$prev" ]] || die "no previous deployment recorded on $HOST — nothing to roll back to. The stack is STILL RUNNING $TAG and was not reverted; this is the first deploy here. Read the gate failure above, then either fix it and redeploy, or take the stack down by hand."
  warn "rolling back to $prev"
  # .env.previous holds the previous deploy's non-secret values and nothing else, so restoring it
  # cannot take a secret back to an older value — secrets.env is not deploy state and is not rotated
  # here. Before the split, a secret hand-added to .env survived a rollback but not a deploy, which
  # meant the two paths disagreed about what the stack would come up with.
  run ssh "${SSH_OPTS[@]}" "$HOST" "cd '$REMOTE_PATH' && cp .env.previous .env && $REMOTE_COMPOSE pull $(compose_names) && $REMOTE_COMPOSE up -d $(compose_names)"
  TAG="$prev"
  health_gate && ok "rolled back to $prev" || die "rollback to $prev is also unhealthy — manual intervention required"
}

record_success() {
  (( DRY_RUN )) && return 0
  ssh "${SSH_OPTS[@]}" "$HOST" "cd '$REMOTE_PATH' && printf '%s\t%s\t%s\t%s\n' \
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
