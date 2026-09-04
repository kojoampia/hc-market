#!/usr/bin/env bash
# ==============================================================================
#  Spec §14: "Killing Kafka mid-accept leaves the booking accepted and the notification pending in
#  the outbox, delivered on recovery."
#
#  This is the check that proves the outbox is real rather than decorative. Anything can look
#  correct while the broker is up; the question is what survives when it is not.
#
#  Usage:  ./deploy/verify-outbox-recovery.sh
#  Needs:  the estate up, and /tmp/tok-pro.txt + /tmp/tok-cust.txt (see verify-cycle.sh).
#
#  --- IT NO LONGER STOPS THE BROKER, AND MUST NOT ---------------------------------------------
#
#  This script used to `docker stop` the dev estate's own Kafka. Since 2026-08-31 there is no such
#  thing: hc-market, hc-admin, hc-patient and hc-professional all share hc-infra's single broker, so
#  stopping it would take four products down to test one — and the old warning ("do not run against
#  anything shared") would now be a warning against running it at all.
#
#  It severs the BOOKING SERVICE from the shared plane instead, by disconnecting its container from
#  hcnet. From the outbox's point of view that is the same event and a strictly better test: the
#  producer cannot reach a broker, and nothing else on the host is affected. Booking keeps its
#  database, which is on the project's own network, so the accept still commits.
#
#  Reconnecting is not optional — a failure part-way through leaves booking off the plane. Hence
#  the trap below, which reconnects on any exit.
#
#  --- WHICH ESTATE ------------------------------------------------------------------------------
#
#  The booking CONTAINER was already overridable; the ports and the two database containers were
#  not, so the script could not be pointed at the quality box — which is the box it most needs to
#  run against, since a container severed from hcnet is a far better approximation of a real broker
#  outage there than on a dev estate that is usually half up. All of them are overridable now, and
#  they must be overridden TOGETHER: an HTTP port from one estate beside a database container from
#  another reads every assertion against rows the API never touched, and check_estate() below
#  refuses that rather than letting it produce numbers.
#
#      # the quality box (quality/startup.sh's ports and its explicit container names)
#      HC_BOOKING_PORT=18101 \
#      HC_BOOKING_CTR=hc-market-quality-booking \
#      HC_BOOKING_DB_CTR=hc-market-quality-booking-db \
#      HC_MESSAGING_DB_CTR=hc-market-quality-messaging-db \
#        ./deploy/verify-outbox-recovery.sh
#
#  MESSAGING IS READ FROM ITS DATABASE AND NEVER OVER HTTP, so there is no HC_MESSAGING_PORT here.
#  It was required for one commit, as a precondition on a service this script makes no request to:
#  the guard refused runs it had no reason to refuse, and a port variable nothing reads is a typo
#  waiting to be silent. Messaging is still held to the estate check through HC_MESSAGING_DB_CTR,
#  which is the thing the assertions actually read.
#
#  The port names are deploy-dev.sh's own, with deploy-dev.sh's own defaults, so exporting the
#  HC_*_PORT block CLAUDE.md documents configures the estate and this script together.
#
#  --- IT REQUIRES A DOCKERISED, PORT-PUBLISHING BOOKING SERVICE ---------------------------------
#
#  Stated rather than discovered, and here it is not merely the consistency guard's price: the whole
#  method is to DISCONNECT booking's container from a docker network, so a booking service running
#  from a jar or an IDE cannot be cut off from the broker by this script at all. The precondition is
#  the test. An estate whose databases are dockerised but whose services are not is refused, and
#  correctly.
# ==============================================================================
set -uo pipefail
BK=http://localhost:${HC_BOOKING_PORT:-8082}
PRO=$(cat /tmp/tok-pro.txt); CUST=$(cat /tmp/tok-cust.txt)
NET="${HC_SHARED_NETWORK:-hcnet}"
# HC_DEV_BOOKING_CTR is the name this variable had when the dev estate was the only estate it could
# address. Still honoured so nobody's shell history breaks; the DEV in it is now a lie, hence the
# rename.
BOOKING_CTR="${HC_BOOKING_CTR:-${HC_DEV_BOOKING_CTR:-hc-market-dev-booking}}"
# The dev compose names no database container, so compose derives `<project>-<service>-1`; the
# quality compose names all five explicitly. Hence a variable per database rather than a prefix.
BOOKING_DB_CTR="${HC_BOOKING_DB_CTR:-healthconnect-dev-booking-db-1}"
MESSAGING_DB_CTR="${HC_MESSAGING_DB_CTR:-healthconnect-dev-messaging-db-1}"
bq() { docker exec "$BOOKING_DB_CTR" psql -U healthconnectBooking -t -A -c "$1"; }
mq() { docker exec "$MESSAGING_DB_CTR" psql -U healthconnectMessaging -t -A -c "$1"; }
on_net() { docker inspect -f "{{if index .NetworkSettings.Networks \"$NET\"}}true{{else}}false{{end}}" "$BOOKING_CTR" 2>/dev/null; }
trap 'if [ "$(on_net)" = false ]; then echo "reconnecting $BOOKING_CTR to $NET"; docker network connect "$NET" "$BOOKING_CTR" >/dev/null 2>&1; fi' EXIT
fail=0
chk() { if [ "$2" = "$3" ]; then printf '  ok   %-46s %s\n' "$1" "$2"; else printf '  FAIL %-46s got %s want %s\n' "$1" "$2" "$3"; fail=1; fi; }

# --- The ports, the containers and the databases must name the SAME estate ----------------------
#
# This script disconnects a named container from hcnet. Naming the wrong estate's container is not
# merely a wrong measurement — it severs a booking service somebody else is using, and the trap only
# restores the one it cut. So the check runs before anything is touched.
#
# Compose labels every container it starts with its project name, which is enough to compare the
# four containers this script touches without knowing either estate's naming scheme. It reads the
# label rather than the name because the two compose files disagree about naming on purpose — the
# dev one prefixes `dev-` to keep hcnet's aliases apart, the quality one names every container.
#
# Only booking's PORT is a precondition, because booking's port is the only one this script uses:
# every messaging assertion goes to mq() and reads the database directly. Requiring a publisher for
# messaging's port too refused runs for a service the script never addresses.
project_of() { docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$1" 2>/dev/null; }
publisher_of() { docker ps --filter "publish=$1" --format '{{.Names}}' | head -1; }
check_estate() {
  local bad=0 seen="" name proj booking_api
  booking_api="$(publisher_of "${HC_BOOKING_PORT:-8082}")"
  [ -n "$booking_api" ] || { echo "  FAIL nothing publishes booking's port ${HC_BOOKING_PORT:-8082} — is the estate up?"; bad=1; }
  for name in "$BOOKING_CTR" "$BOOKING_DB_CTR" "$MESSAGING_DB_CTR"; do
    docker inspect "$name" >/dev/null 2>&1 || { echo "  FAIL no such container: $name"; bad=1; }
  done
  [ $bad -eq 0 ] || { echo ""; echo "OUTBOX RECOVERY FAILED — see the header for the quality box's values"; exit 1; }

  # An empty project label must not collapse into the container name: `printf '%s\t%s' "" "$name"`
  # is a line beginning with a tab, and whitespace-splitting awk reads the NAME as field 1, so N
  # containers docker started rather than compose look like N distinct estates and are reported with
  # names in the project column and blanks beside them. It fails safe and accuses the wrong thing.
  # What the placeholder leaves, stated: two unlabelled containers group together as one "(none)",
  # so two hand-started estates cannot be told apart — there is nothing to compare. The case that
  # matters, an unlabelled container mixed with a compose-managed one, is still refused and is now
  # named correctly.
  for name in "$booking_api" "$BOOKING_CTR" "$BOOKING_DB_CTR" "$MESSAGING_DB_CTR"; do
    proj="$(project_of "$name")"
    printf -v seen '%s\n%s\t%s' "$seen" "${proj:-(none)}" "$name"
  done
  if [ "$(printf '%s' "$seen" | awk -F'\t' 'NF{print $1}' | sort -u | wc -l)" != "1" ]; then
    echo "  FAIL the port, the booking container and the databases belong to different estates:"
    printf '%s\n' "$seen" | awk -F'\t' 'NF{printf "         %-20s %s\n", $1, $2}'
    echo "       Nothing has been disconnected. Override them TOGETHER — see the header."
    echo ""; echo "OUTBOX RECOVERY FAILED — the estate is not consistently addressed"; exit 1
  fi
  # The booking container this is about to sever must be the one answering on the booking port, or
  # the "accept still succeeded" assertion is made against a service that was never cut off.
  if [ "$booking_api" != "$BOOKING_CTR" ]; then
    echo "  FAIL port ${HC_BOOKING_PORT:-8082} is served by $booking_api but this would disconnect $BOOKING_CTR."
    echo "       Nothing has been disconnected. Set HC_BOOKING_CTR to match the port."
    echo ""; echo "OUTBOX RECOVERY FAILED — the estate is not consistently addressed"; exit 1
  fi
  proj="$(project_of "$BOOKING_CTR")"
  printf '  estate  %s   booking %s   messaging db %s   severing %s\n' \
    "${proj:-(none)}" "$BK" "$MESSAGING_DB_CTR" "$BOOKING_CTR"
}
check_estate

echo "── set up a booking to accept ──"
D=$(date -u -d "+9 days" +%Y-%m-%d)
REF=$(curl -s --max-time 15 -X POST -H "Authorization: Bearer $CUST" -H 'Content-Type: application/json' \
  -d "{\"professionalRef\":\"p1\",\"professionalLogin\":\"akosua.mensah\",\"customerName\":\"Kojo Ampia-Addison\",\"serviceRef\":\"s1b\",\"serviceName\":\"Follow-up consultation\",\"priceMinor\":15000,\"currency\":\"GHS\",\"scheduledDate\":\"$D\",\"scheduledTime\":\"16:00\",\"deliveryMode\":\"ONLINE\"}" \
  $BK/api/bookings | python3 -c "import sys,json;print(json.load(sys.stdin)['reference'])")
# let the booking.requested event drain while the broker is still up
sleep 6
N0=$(mq "select count(*) from notification where recipient_login='kojo.ampia.addison';")
echo "  booking $REF created; customer notifications now $N0"

echo "── cut booking off from the broker ──"
docker network disconnect "$NET" "$BOOKING_CTR" >/dev/null 2>&1
chk "booking is off the shared plane" "$(on_net)" "false"

echo "── accept with the broker unreachable ──"
STATUS=$(curl -s --max-time 20 -X POST -H "Authorization: Bearer $PRO" $BK/api/pro/requests/$REF/accept \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['status'])" 2>/dev/null)
chk "the accept still succeeded" "$STATUS" "CONFIRMED"
chk "the booking is CONFIRMED in the database" "$(bq "select status from booking where reference='$REF';")" "CONFIRMED"
sleep 8
chk "the event is sitting UNSENT in the outbox" \
  "$(bq "select count(*) from outbox_event where aggregate_ref='$REF' and type like '%accepted' and sent_at is null;")" "1"
chk "no confirmation notification yet" \
  "$(mq "select count(*) from notification where recipient_login='kojo.ampia.addison' and deep_link='/bookings/$REF' and kind='Booking confirmed';")" "0"

echo "── put booking back on the plane ──"
docker network connect "$NET" "$BOOKING_CTR" >/dev/null 2>&1
chk "booking is back on the shared plane" "$(on_net)" "true"
# Docker's DNS for the reattached container settles within a second or two; the outbox publisher
# runs on a timer regardless, so the drain below is what actually proves the connection came back.
sleep 3

echo "── it should deliver itself ──"
for i in $(seq 1 40); do
  [ "$(bq "select count(*) from outbox_event where aggregate_ref='$REF' and type like '%accepted' and sent_at is null;")" = "0" ] && break
  sleep 3
done
chk "the outbox row is now sent" \
  "$(bq "select count(*) from outbox_event where aggregate_ref='$REF' and type like '%accepted' and sent_at is null;")" "0"
for i in $(seq 1 40); do
  [ "$(mq "select count(*) from notification where deep_link='/bookings/$REF' and kind='Booking confirmed';")" = "1" ] && break
  sleep 3
done
chk "the notification arrived, exactly once" \
  "$(mq "select count(*) from notification where deep_link='/bookings/$REF' and kind='Booking confirmed';")" "1"

echo ""
[ $fail -eq 0 ] && echo "OUTBOX RECOVERY PASSED" || echo "OUTBOX RECOVERY FAILED"
exit $fail
