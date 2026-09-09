#!/usr/bin/env bash
#
# The alert rules and the compose files must agree about whether anything reports.
#
# decisions.md D63, backlog NEW-23. deploy/observability/hc-market-rules.yaml declares five alerts
# over two metric families that ONLY the OpenTelemetry Java agent emits, and until D63 no JVM in
# any environment had ever been started with `-javaagent`. Two of the five are `absent()` queries,
# so installing that file against a tenant hc-market has never reported to would have fired them
# continuously and for ever. Nothing could see it: the existing rules check asserts the YAML parses
# and that group names are unique, which a file describing a signal nobody emits satisfies
# perfectly.
#
# So this asserts the RELATIONSHIP rather than either side. The rules file carries a marker saying
# it has no data source; that marker is REQUIRED while dev and quality attach no agent, and
# REFUSED once either does. The day somebody turns telemetry on, this check goes red and the
# paragraph gets rewritten — which is the only mechanism that stops a true statement quietly
# becoming a false one in the other direction. Every previous version of this failure in this
# repository was a document that stayed still while the code moved.
#
# IT READS RENDERED COMPOSE VALUES, NEVER THE FILE'S TEXT, and that is the point rather than
# convenience. `docker compose config` resolves the anchors, the merge keys and the `${VAR:-}`
# defaults and hands back what a container would actually receive — so the long comment above
# quality/compose.yml's JAVA_OPTS, which names `-javaagent:/app/otel-javaagent.jar` four times in
# prose, cannot satisfy this. A grep would have been satisfied by it. Comment-stripping fail-opens
# are this repository's most recurrent defect class (nine across D48-D56, one of them inside the
# fix for the previous eight); reading the rendered value is how a check avoids needing a stripper
# at all.
#
# The four things it asserts, each named separately in its own message because a battery reporting
# as one exit status cannot say which half went:
#
#   1. production renders the agent flag for all five services, with HC_OTEL_JAVA_OPTS unset;
#   2. dev and quality render NO agent flag with HC_OTEL_JAVA_OPTS unset;
#   3. dev and quality DO render it when HC_OTEL_JAVA_OPTS is set — the switch works, rather than
#      merely being written down. A default of `${HC_OTEL_JAVA_OPTS:--javaagent:...}` reversed by
#      hand into a hardcoded string would pass (2) and fail here;
#   4. the rules file's marker agrees with (2).
set -euo pipefail

cd "$(dirname "$0")/../.."

readonly AGENT_FLAG='-javaagent:/app/otel-javaagent.jar'
readonly RULES='deploy/observability/hc-market-rules.yaml'
readonly MARKER='# NOT-YET-ATTACHED: no environment in this repository starts a JVM with -javaagent'
readonly QUALITY='quality/compose.yml'
readonly DEV='deploy/docker/docker-compose.dev.yml'
readonly PROD='deploy/docker/docker-compose.prod.yml'

for f in "$RULES" "$QUALITY" "$DEV" "$PROD"; do
  if [ ! -f "$f" ]; then
    echo "::error::$f is missing — this check cannot establish anything without it. See decisions.md D63."
    exit 1
  fi
done

fail=0

# Render one compose file and print `service<TAB>JAVA_OPTS` for every service that has one.
# jq rather than python+yaml: `docker compose config --format json` needs no third-party module,
# and jq is already a hard requirement of deploy-dev.sh.
render() {
  local file="$1"; shift
  env "$@" docker compose -f "$file" config --format json \
    | jq -r '.services | to_entries[]
             | select(.value.environment.JAVA_OPTS != null)
             | "\(.key)\t\(.value.environment.JAVA_OPTS)"'
}

readonly -a QUALITY_ENV=(JWT_BASE64_SECRET=ci-placeholder HC_PRIVACY_PEPPER=ci-placeholder TAG=ci)
readonly -a DEV_ENV=(JWT_BASE64_SECRET=ci-placeholder HC_PRIVACY_PEPPER=ci-placeholder)
readonly -a PROD_ENV=(
  HC_IMAGE_PREFIX=example HC_IMAGE_SEP=/ HC_TAG=ci
  JWT_BASE64_SECRET=ci-placeholder HC_PRIVACY_PEPPER=ci-placeholder
  HC_GATEWAY_ADMIN_PASSWORD=ci-placeholder HC_GATEWAY_MONGODB_URI=mongodb://x/y
  HC_CATALOG_DB_URL=jdbc:x HC_CATALOG_DB_PASSWORD=x
  HC_BOOKING_DB_URL=jdbc:x HC_BOOKING_DB_PASSWORD=x
  HC_MESSAGING_DB_URL=jdbc:x HC_MESSAGING_DB_PASSWORD=x
  HC_PAYOUT_DB_URL=jdbc:x HC_PAYOUT_DB_PASSWORD=x
)

# Count services rendering the flag, and refuse a count of zero SERVICES — the fail-open every
# derived check in this repository has had to close. A compose file that stopped declaring
# JAVA_OPTS at all would otherwise satisfy "no service attaches the agent" perfectly.
count_attached() {
  local rendered="$1"
  printf '%s\n' "$rendered" | grep -cF -- "$AGENT_FLAG" || true
}
count_services() {
  local rendered="$1"
  printf '%s\n' "$rendered" | grep -c . || true
}

echo "--- 1. production attaches the agent by default ---"
prod_rendered="$(render "$PROD" "${PROD_ENV[@]}")"
prod_services="$(count_services "$prod_rendered")"
prod_attached="$(count_attached "$prod_rendered")"
if [ "$prod_services" -eq 0 ]; then
  echo "::error file=$PROD::no service renders a JAVA_OPTS at all, so this check established nothing about production. See decisions.md D63."
  fail=1
elif [ "$prod_attached" != "$prod_services" ]; then
  echo "::error file=$PROD::$prod_attached of $prod_services services render '$AGENT_FLAG'. Production is the one environment that attaches it; a service missing it goes quiet on the dashboards while starting, serving and reporting healthy. See decisions.md D25 and D63."
  printf '%s\n' "$prod_rendered"
  fail=1
else
  echo "ok   $PROD — all $prod_services services render the agent flag"
fi

echo "--- 2. dev and quality attach nothing by default ---"
runnable_attached=0
for pair in "$QUALITY:QUALITY_ENV" "$DEV:DEV_ENV"; do
  file="${pair%%:*}"; envname="${pair##*:}"
  declare -n envref="$envname"
  rendered="$(render "$file" "${envref[@]}")"
  services="$(count_services "$rendered")"
  attached="$(count_attached "$rendered")"
  if [ "$services" -eq 0 ]; then
    echo "::error file=$file::no service renders a JAVA_OPTS at all, so this check established nothing about it. See decisions.md D63."
    fail=1
  elif [ "$attached" != 0 ]; then
    echo "note $file — $attached of $services services attach the agent by default"
    runnable_attached=$((runnable_attached + attached))
  else
    echo "ok   $file — none of $services services attaches the agent by default (D63)"
  fi
done

echo "--- 3. the opt-in actually reverses it ---"
for pair in "$QUALITY:QUALITY_ENV" "$DEV:DEV_ENV"; do
  file="${pair%%:*}"; envname="${pair##*:}"
  declare -n envref2="$envname"
  rendered="$(render "$file" "${envref2[@]}" "HC_OTEL_JAVA_OPTS=$AGENT_FLAG")"
  services="$(count_services "$rendered")"
  attached="$(count_attached "$rendered")"
  if [ "$services" -eq 0 ] || [ "$attached" != "$services" ]; then
    echo "::error file=$file::with HC_OTEL_JAVA_OPTS set, $attached of $services services render '$AGENT_FLAG'. The absence of the agent here is supposed to be one variable away from its presence; if it is not, it is an accident again rather than a decision. See decisions.md D63."
    printf '%s\n' "$rendered"
    fail=1
  else
    echo "ok   $file — HC_OTEL_JAVA_OPTS attaches the agent to all $services services"
  fi
done

echo "--- 4. the rules file agrees ---"
if grep -qF -- "$MARKER" "$RULES"; then
  marker=present
else
  marker=absent
fi
if [ "$runnable_attached" -gt 0 ] && [ "$marker" = present ]; then
  echo "::error file=$RULES::an environment that runs now attaches the agent, but this file still carries the NOT-YET-ATTACHED marker. Two of its five alerts are absent() queries and the marker is what stops them being installed against a tenant nothing reports to; leaving it in place once something does report makes the file wrong in the other direction. Remove the marker and rewrite the paragraph above it. See decisions.md D63."
  fail=1
elif [ "$runnable_attached" -eq 0 ] && [ "$marker" = absent ]; then
  echo "::error file=$RULES::no environment that has ever run attaches the OpenTelemetry agent, so nothing answers any query in this file — but the NOT-YET-ATTACHED marker is gone. Installing it would fire HcMarketGatewayDown and HcMarketServiceDown continuously and for ever. Restore the marker, or attach the agent somewhere that runs. See decisions.md D63 and backlog NEW-23."
  fail=1
else
  echo "ok   $RULES — marker $marker, agent attached in $runnable_attached runnable services"
fi

exit "$fail"
