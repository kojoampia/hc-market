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
# ==============================================================================
set -uo pipefail
BK=http://localhost:18202; M=http://localhost:18203
PRO=$(cat /tmp/tok-pro.txt); CUST=$(cat /tmp/tok-cust.txt)
P=healthconnect-dev
NET="${HC_SHARED_NETWORK:-hcnet}"
BOOKING_CTR="${HC_DEV_BOOKING_CTR:-hc-market-dev-booking}"
bq() { docker exec ${P}-booking-db-1 psql -U healthconnectBooking -t -A -c "$1"; }
mq() { docker exec ${P}-messaging-db-1 psql -U healthconnectMessaging -t -A -c "$1"; }
on_net() { docker inspect -f "{{if index .NetworkSettings.Networks \"$NET\"}}true{{else}}false{{end}}" "$BOOKING_CTR" 2>/dev/null; }
trap 'if [ "$(on_net)" = false ]; then echo "reconnecting $BOOKING_CTR to $NET"; docker network connect "$NET" "$BOOKING_CTR" >/dev/null 2>&1; fi' EXIT
fail=0
chk() { if [ "$2" = "$3" ]; then printf '  ok   %-46s %s\n' "$1" "$2"; else printf '  FAIL %-46s got %s want %s\n' "$1" "$2" "$3"; fail=1; fi; }

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
