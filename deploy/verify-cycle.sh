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
#
#  --- IT WRITES. THAT IS THE POINT, AND IT HAS TO BE SAID OUT LOUD -----------------------------
#
#  A booking, three transitions, a ledger row and a REVIEW, all of which stay in the estate after
#  this exits. On the quality box that leaves `reviews` one above the seed's 63 for ever, and
#  quality/startup.sh --verify used to report exactly that as a fault: two tools each working
#  correctly, and the second one reporting the first one's success as a defect. --verify now
#  separates the counts nothing here writes to from the ones this script moves; the summary printed
#  at the end of this run says what changed and how to put it back.
#
#  --- WHICH ESTATE ------------------------------------------------------------------------------
#
#  It used to be pinned to the dev estate — localhost:18201/18202 and healthconnect-dev-*-db-1 —
#  so it could not run against the quality box, which is the box it most needs to run against and
#  the one that has found every defect no test suite caught. Both halves are overridable now, and
#  they must be overridden TOGETHER: an HTTP port from one estate beside a database container from
#  another reads every assertion against rows the API never touched. check_estate() below refuses
#  that rather than letting it produce numbers.
#
#      # the quality box (quality/startup.sh's ports and its explicit container names)
#      HC_CATALOG_PORT=18100 HC_BOOKING_PORT=18101 HC_PAYOUT_PORT=18103 \
#      HC_CATALOG_DB_CTR=hc-market-quality-catalog-db \
#      HC_PAYOUT_DB_CTR=hc-market-quality-payout-db \
#        ./deploy/verify-cycle.sh
#
#  Only the two databases this script actually reads are named. There is deliberately no
#  HC_BOOKING_DB_CTR: booking is addressed over HTTP throughout and q() knows about catalog and
#  payout only, so a variable for it would imply booking's DATABASE is part of the estate check when
#  only its API container is — and a typo in a variable nothing reads is silent.
#
#  The port names are deploy-dev.sh's own, with deploy-dev.sh's own defaults, so exporting the
#  HC_*_PORT block CLAUDE.md documents configures the estate and this script together.
#
#  --- IT REQUIRES A DOCKERISED, PORT-PUBLISHING ESTATE, WHICH IS A REAL CONSTRAINT --------------
#
#  Stated rather than discovered. Two of the four containers it compares are found by asking docker
#  WHICH CONTAINER PUBLISHES a port, so an estate whose services run from a jar or an IDE against
#  dockerised databases is refused even though every assertion in the script would work against it.
#  That is the price of the consistency guard: the only thing tying an HTTP port to the rows behind
#  it, without either estate's naming scheme being hardcoded, is the compose project label on the
#  container listening there. Run the services in containers for this check, or read the numbers by
#  hand.
# ==============================================================================
set -uo pipefail
CAT=http://localhost:${HC_CATALOG_PORT:-8081}; BK=http://localhost:${HC_BOOKING_PORT:-8082}
PRO=$(cat /tmp/tok-pro.txt); CUST=$(cat /tmp/tok-cust.txt)

# The dev compose names no database container, so compose derives `<project>-<service>-1`; the
# quality compose names all five explicitly. Hence a variable per database rather than a prefix.
CATALOG_DB_CTR="${HC_CATALOG_DB_CTR:-healthconnect-dev-catalog-db-1}"
PAYOUT_DB_CTR="${HC_PAYOUT_DB_CTR:-healthconnect-dev-payout-db-1}"

q()  {
  case "$1" in
    catalog) c=$CATALOG_DB_CTR ;;
    payout)  c=$PAYOUT_DB_CTR ;;
    *)       echo "no database container configured for '$1'" >&2; return 1 ;;
  esac
  docker exec "$c" psql -U healthconnect$2 -t -A -c "$3"
}
api() { curl -s --max-time 15 "$@"; }
fail=0
chk() { if [ "$2" = "$3" ]; then printf '  ok   %-42s %s\n' "$1" "$2"; else printf '  FAIL %-42s got %s want %s\n' "$1" "$2" "$3"; fail=1; fi; }

# --- The ports and the database containers must name the SAME estate ----------------------------
#
# Overriding one side and not the other is the failure this check exists for, and it is silent: the
# API answers from one estate while every count is read from another's tables, so `ledger rows
# total` and `gross increased by the price` fail against numbers that were never going to agree and
# the run reads as a broken outbox.
#
# Compose labels every container it starts with its project name, and that is enough to compare the
# four containers this script touches without knowing any estate's naming scheme. It reads the label
# rather than the name because the two compose files disagree about naming on purpose — the dev one
# prefixes `dev-` to keep hcnet's aliases apart, the quality one names every container explicitly.
project_of() { docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$1" 2>/dev/null; }
publisher_of() { docker ps --filter "publish=$1" --format '{{.Names}}' | head -1; }
check_estate() {
  local bad=0 seen="" name proj
  local catalog_api booking_api
  catalog_api="$(publisher_of "${HC_CATALOG_PORT:-8081}")"
  booking_api="$(publisher_of "${HC_BOOKING_PORT:-8082}")"
  [ -n "$catalog_api" ] || { echo "  FAIL nothing publishes catalog's port ${HC_CATALOG_PORT:-8081} — is the estate up?"; bad=1; }
  [ -n "$booking_api" ] || { echo "  FAIL nothing publishes booking's port ${HC_BOOKING_PORT:-8082} — is the estate up?"; bad=1; }
  for name in "$CATALOG_DB_CTR" "$PAYOUT_DB_CTR"; do
    docker inspect "$name" >/dev/null 2>&1 || { echo "  FAIL no such database container: $name"; bad=1; }
  done
  [ $bad -eq 0 ] || { echo ""; echo "CYCLE FAILED — see the header for the quality box's values"; exit 1; }

  # A container docker started rather than compose carries no project label, and an EMPTY label must
  # not collapse into the container name: `printf '%s\t%s' "" "$name"` is a line beginning with a
  # tab, and whitespace-splitting awk then reads the NAME as field 1. Two unlabelled containers
  # would look like two distinct estates and be reported with names in the project column and blanks
  # beside them — it fails safe, but it accuses the wrong thing, and a hand-started database is
  # exactly what CLAUDE.md's `docker run -d --name hc-catalog-db …` loop produces. Hence a
  # placeholder, and -F'\t' so the fields are the fields. What that leaves, stated: two containers
  # with no label group together as one "(none)", so this cannot tell two hand-started estates
  # apart — there is nothing to compare. It still refuses the case that matters, an unlabelled
  # container mixed with a compose-managed one, and now names it correctly.
  for name in "$catalog_api" "$booking_api" "$CATALOG_DB_CTR" "$PAYOUT_DB_CTR"; do
    proj="$(project_of "$name")"
    printf -v seen '%s\n%s\t%s' "$seen" "${proj:-(none)}" "$name"
  done
  if [ "$(printf '%s' "$seen" | awk -F'\t' 'NF{print $1}' | sort -u | wc -l)" != "1" ]; then
    echo "  FAIL the ports and the databases belong to different estates:"
    printf '%s\n' "$seen" | awk -F'\t' 'NF{printf "         %-20s %s\n", $1, $2}'
    echo "       Override the ports and the database containers TOGETHER — see the header."
    echo ""; echo "CYCLE FAILED — the estate is not consistently addressed"; exit 1
  fi
  proj="$(project_of "$CATALOG_DB_CTR")"
  printf '  estate  %s   catalog %s   booking %s\n' "${proj:-(none)}" "$CAT" "$BK"
}
check_estate

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

# --- What this run left behind ------------------------------------------------------------------
#
# Printed whether it passed or failed, because a failed run has usually written most of this too.
# The estate is a booking, a ledger row and a review further from the seed than it was, and the next
# person to look at it should be told so by the script that did it rather than by a count that
# disagrees with a number they were told to expect.
echo ""
echo "── what this run wrote, and how to undo it ──"
printf '  booking     %s   (REQUESTED -> CONFIRMED -> COMPLETED, on p1)\n' "$REF"
printf '  ledger      1 row in payout, gross +28000, commission 3360\n'
printf '  review      1 one-star review on p1 — reviews are now %s, seed-exact is 63\n' \
  "$(api $CAT/api/reviews/count)"
printf '  messaging   a conversation and its notifications for kojo.ampia.addison\n'
cat <<'RESTORE'
  Nothing here deletes a review — that is deliberate (spec §7, review integrity), so there is no
  surgical undo. To put the box back to seed-exact, reseed it:
      ./quality/startup.sh --local --clean && TAG=<sha> ./quality/startup.sh --local
  Or leave it: quality/startup.sh --verify counts reviews as "seed plus recorded activity" and
  reports the surplus rather than failing on it.
RESTORE

echo ""
[ $fail -eq 0 ] && echo "CYCLE PASSED" || echo "CYCLE FAILED"
exit $fail
