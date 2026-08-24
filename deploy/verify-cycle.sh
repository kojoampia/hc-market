#!/usr/bin/env bash
# ==============================================================================
#  Spec §14: "Booking a slot, accepting it as the professional, completing it, then reviewing it
#  moves the rating and creates exactly one ledger row."
#
#  Exercises catalog, booking and payout together against a RUNNING estate, and crosses Kafka: the
#  ledger row only appears if booking's outbox published booking.completed and payout consumed it.
#
#  Usage:  ./deploy/verify-cycle.sh
#  Needs:  the estate up (./deploy/deploy-dev.sh up) and two tokens in /tmp:
#            /tmp/tok-pro.txt   a ROLE_PROFESSIONAL token for akosua.mensah
#            /tmp/tok-cust.txt  a ROLE_CUSTOMER token for kojo.ampia.addison
#          Mint them with the estate's JWT_BASE64_SECRET; see CLAUDE.md.
#
#  Deliberately reviews with ONE star: a 5-star review on a professional already averaging 4.7
#  barely moves the number, so a broken rating would still look plausible. One star moves it
#  visibly, which is what makes "the rating moved" a real assertion rather than a rounding artefact.
# ==============================================================================
set -uo pipefail
CAT=http://localhost:18201; BK=http://localhost:18202
PRO=$(cat /tmp/tok-pro.txt); CUST=$(cat /tmp/tok-cust.txt)
q()  { docker exec healthconnect-dev-$1-db-1 psql -U healthconnect$2 -t -A -c "$3"; }
api() { curl -s --max-time 15 "$@"; }
fail=0
chk() { if [ "$2" = "$3" ]; then printf '  ok   %-42s %s\n' "$1" "$2"; else printf '  FAIL %-42s got %s want %s\n' "$1" "$2" "$3"; fail=1; fi; }

echo "── before ──"
R0=$(api $CAT/api/professionals/p1 | python3 -c "import sys,json;c=json.load(sys.stdin)['card'];print(f\"{c['rating']} {c['reviewCount']}\")")
L0=$(q payout Payout "select count(*) from ledger;")
G0=$(q payout Payout "select sum(gross_minor) from ledger;")
printf '  p1 rating %s | ledger rows %s | gross %s\n' "$R0" "$L0" "$G0"

echo "── 1. book ──"
D=$(date -u -d "+5 days" +%Y-%m-%d)
REF=$(api -X POST -H "Authorization: Bearer $CUST" -H 'Content-Type: application/json' \
  -d "{\"professionalRef\":\"p1\",\"professionalLogin\":\"akosua.mensah\",\"serviceRef\":\"s1a\",\"serviceName\":\"Nutrition assessment (first visit)\",\"priceMinor\":28000,\"currency\":\"GHS\",\"scheduledDate\":\"$D\",\"scheduledTime\":\"10:00\",\"deliveryMode\":\"ONLINE\",\"customerNote\":\"cycle test\"}" \
  $BK/api/bookings | python3 -c "import sys,json;print(json.load(sys.stdin)['reference'])")
chk "created, status" "$(api -H "Authorization: Bearer $CUST" $BK/api/bookings/$REF | python3 -c "import sys,json;print(json.load(sys.stdin)['booking']['status'])")" "REQUESTED"

echo "── 2. accept ──"
chk "accepted, status" "$(api -X POST -H "Authorization: Bearer $PRO" $BK/api/pro/requests/$REF/accept | python3 -c "import sys,json;print(json.load(sys.stdin)['status'])")" "CONFIRMED"

echo "── 3. complete ──"
chk "completed, status" "$(api -X POST -H "Authorization: Bearer $PRO" $BK/api/pro/bookings/$REF/complete | python3 -c "import sys,json;print(json.load(sys.stdin)['status'])")" "COMPLETED"

echo "── 4. the ledger row arrives via Kafka ──"
for i in $(seq 1 30); do [ "$(q payout Payout "select count(*) from ledger where booking_reference='$REF';")" = "1" ] && break; sleep 2; done
chk "ledger rows for this booking" "$(q payout Payout "select count(*) from ledger where booking_reference='$REF';")" "1"
chk "ledger rows total"            "$(q payout Payout "select count(*) from ledger;")" "$((L0+1))"
chk "gross increased by the price" "$(q payout Payout "select sum(gross_minor) from ledger;")" "$((G0+28000))"
chk "commission is 12% of 28000"   "$(q payout Payout "select commission_minor from ledger where booking_reference='$REF';")" "3360"

echo "── 5. review it ──"
STARS=1
CODE=$(curl -s -o /tmp/rv.json -w '%{http_code}' --max-time 15 -X POST -H "Authorization: Bearer $CUST" -H 'Content-Type: application/json' \
  -d "{\"bookingReference\":\"$REF\",\"stars\":$STARS,\"body\":\"Cycle test review.\"}" $CAT/api/reviews)
chk "review accepted" "$CODE" "201"
chk "a second review is refused" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 -X POST -H "Authorization: Bearer $CUST" -H 'Content-Type: application/json' -d "{\"bookingReference\":\"$REF\",\"stars\":5,\"body\":\"again\"}" $CAT/api/reviews)" "409"

echo "── 6. the rating moved, and still equals AVG(stars) ──"
R1=$(api $CAT/api/professionals/p1 | python3 -c "import sys,json;c=json.load(sys.stdin)['card'];print(f\"{c['rating']} {c['reviewCount']}\")")
SQL=$(q catalog Catalog "select round(avg(stars)::numeric,1)||' '||count(*) from review r join professional p on p.id=r.professional_id where p.reference='p1';")
printf '  rating %s -> %s\n' "$R0" "$R1"
chk "API rating equals SQL" "$R1" "$SQL"
[ "$R0" != "$R1" ] && printf '  ok   %-42s it moved\n' "rating changed" || { printf '  FAIL rating did not move\n'; fail=1; }
chk "booking is now flagged reviewed" "$(api -H "Authorization: Bearer $CUST" $BK/api/bookings/$REF | python3 -c "import sys,json;print(json.load(sys.stdin)['booking']['reviewed'])")" "True"

echo ""
[ $fail -eq 0 ] && echo "CYCLE PASSED" || echo "CYCLE FAILED"
exit $fail
