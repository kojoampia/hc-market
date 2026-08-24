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
#  WARNING: stops and starts the Kafka container. Do not run against anything shared.
# ==============================================================================
set -uo pipefail
BK=http://localhost:18202; M=http://localhost:18203
PRO=$(cat /tmp/tok-pro.txt); CUST=$(cat /tmp/tok-cust.txt)
P=healthconnect-dev
bq() { docker exec ${P}-booking-db-1 psql -U healthconnectBooking -t -A -c "$1"; }
mq() { docker exec ${P}-messaging-db-1 psql -U healthconnectMessaging -t -A -c "$1"; }
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

echo "── kill Kafka ──"
docker stop ${P}-kafka-1 >/dev/null 2>&1
chk "kafka running" "$(docker inspect -f '{{.State.Running}}' ${P}-kafka-1)" "false"

echo "── accept with the broker down ──"
STATUS=$(curl -s --max-time 20 -X POST -H "Authorization: Bearer $PRO" $BK/api/pro/requests/$REF/accept \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['status'])" 2>/dev/null)
chk "the accept still succeeded" "$STATUS" "CONFIRMED"
chk "the booking is CONFIRMED in the database" "$(bq "select status from booking where reference='$REF';")" "CONFIRMED"
sleep 8
chk "the event is sitting UNSENT in the outbox" \
  "$(bq "select count(*) from outbox_event where aggregate_ref='$REF' and type like '%accepted' and sent_at is null;")" "1"
chk "no confirmation notification yet" \
  "$(mq "select count(*) from notification where recipient_login='kojo.ampia.addison' and deep_link='/bookings/$REF' and kind='Booking confirmed';")" "0"

echo "── bring Kafka back ──"
docker start ${P}-kafka-1 >/dev/null 2>&1
until docker exec ${P}-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list >/dev/null 2>&1; do sleep 3; done
echo "  broker is back"

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
