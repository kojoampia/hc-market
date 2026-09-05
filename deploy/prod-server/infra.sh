#!/bin/bash
# One-time setup on webserver: create the one Docker network this product owns, and refuse to
# continue if either of the two it BORROWS is missing.
#
#   ssh webserver
#   cd /srv/healthconnect && ./infra.sh
#
# Mirrors the sibling infra.sh scripts under ~/webroot, which pre-create a named network rather than
# let compose generate a default one — a shared, predictable name is what survives a
# --force-recreate, and here it is also what lets TWO compose projects attach to the same object.
# That second reason is specific to hc-market: `hc-market-data` (the stores) and `healthconnect`
# (the five apps) are separate projects, so a compose-created network would give each of them its
# own and the applications would resolve none of the database hostnames.
#
# --- What this creates, and what it refuses to create (decisions.md D27) ---------------------------
#
# CREATES  hcmarketnet   hc-market's own. Carries nothing but this product's five databases and the
#                        five application containers that read them. Private on purpose: D27's
#                        closing paragraph keeps the databases off every shared network, so no other
#                        product on this host can resolve `hc-market-catalog-db` at all.
#
# REFUSES  infranet      host-wide, owned by ~/webroot/00-infrastructure/services. Carries the one
#                        Kafka broker and the one Consul that every product on this box borrows.
#          monitoring    host-wide, owned by ~/webroot/02-monitoring/services. Carries the shared
#                        otel-collector every product pushes OTLP to.
#
# Neither of those two is this stack's to create, and creating an EMPTY network with the right name
# is the worst possible outcome: `docker compose up` succeeds, every container starts, every health
# check passes, and the estate is silently talking to nothing. A missing broker in particular is
# invisible — the app starts, serves, and reports healthy while everything produced goes nowhere.
# So this checks, names the stack that owns each one, and exits.
set -euo pipefail

DATA_NETWORK="${HC_DATA_NETWORK:-hcmarketnet}"

if docker network inspect "$DATA_NETWORK" >/dev/null 2>&1; then
  echo "$DATA_NETWORK already exists"
else
  docker network create "$DATA_NETWORK"
  echo "created $DATA_NETWORK"
fi

fail=0
if ! docker network inspect "${HC_NETWORK:-infranet}" >/dev/null 2>&1; then
  echo "ERROR: the shared '${HC_NETWORK:-infranet}' network is missing. It carries the one Kafka" >&2
  echo "       broker and the one Consul this estate borrows rather than bundles (decisions.md" >&2
  echo "       D27); bring up ~/webroot/00-infrastructure/services first." >&2
  echo "       Do NOT create it empty — the stack would come up healthy and publish to nothing." >&2
  fail=1
fi

if ! docker network inspect "${HC_MONITORING_NETWORK:-monitoring}" >/dev/null 2>&1; then
  echo "ERROR: the shared '${HC_MONITORING_NETWORK:-monitoring}' network is missing. It carries the" >&2
  echo "       otel-collector these five services export to; bring up ~/webroot/02-monitoring/services" >&2
  echo "       first. Losing telemetry is silent — the service goes quiet on the dashboards and" >&2
  echo "       nothing else changes." >&2
  fail=1
fi

exit "$fail"
