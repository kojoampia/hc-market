#!/usr/bin/env bash
# ==============================================================================
#  The erasure pepper must be REQUIRED by every service that derives an alias, and COMMITTED nowhere
#  a real deployment reads — decisions.md D35.
#
#  Two checks, both of which replace a grep that could pass on a broken state:
#
#  1. PER SERVICE, NOT PER FILE. The old check was
#         grep -qE 'HEALTHCONNECT_PRIVACY_PEPPER: \$\{HC_PRIVACY_PEPPER:\?' "$f"
#     which asks whether the string appears ANYWHERE in the file. It passes today only because all
#     five services share one YAML anchor. Move messaging off that anchor — which is exactly how the
#     per-service DISCOVERY_HOSTNAME overrides beside it already work — and the grep still finds the
#     string on the other four while messaging starts unpeppered. That service is the one holding the
#     register of who has been erased, so it is the worst one to lose. This asks compose itself for
#     each service's MERGED environment (`config --no-interpolate` resolves the anchors and leaves
#     the ${...} expressions intact) and checks the three services that derive aliases, by name.
#
#  2. EVERY SPELLING, NOT ONE. The old check was `grep -qE '^\s+pepper:\s*\S'`, which matches only the
#     nested form. Spring reads the flat `healthconnect.privacy.pepper: value` just as happily, and
#     that line begins with `h` rather than whitespace, so it escaped the check entirely. This
#     matches any dotted or underscored prefix ending in `pepper`, and decides on the VALUE rather
#     than on its presence, so an empty one is not reported as a committed secret.
#
#  Inputs are overridable so the test beside this file can construct the broken states and watch it
#  fail — a check nobody has seen fail is a check of nothing.
#
#      ./.github/checks/pepper-wiring.sh
#      HC_COMPOSE_FILES="/tmp/x.yml" HC_SERVICE_DIRS="/tmp/svc" ./.github/checks/pepper-wiring.sh
# ==============================================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

COMPOSE_FILES="${HC_COMPOSE_FILES:-quality/compose.yml deploy/docker/docker-compose.dev.yml deploy/docker/docker-compose.prod.yml}"
# The three services carrying SubjectPseudonym. The gateway and payout derive no alias; they inherit
# the variable from the shared anchor and it is not a defect if they ever stop.
#
# Two lists rather than one, because they are two different things that happen to share three names:
# compose SERVICE names (matched by suffix, since each file prefixes them differently) and source
# DIRECTORIES holding the committed profiles. The test beside this file overrides them separately.
COMPOSE_SERVICES="${HC_COMPOSE_SERVICES:-booking catalog messaging}"
SERVICE_DIRS="${HC_SERVICE_DIRS:-booking catalog messaging}"

fail=0
err() { printf '::error%s::%s\n' "${2:+ file=$2}" "$1"; fail=1; }

# --- 1. required, per service, in every compose file --------------------------------------------
for f in $COMPOSE_FILES; do
  [ -f "$f" ] || { err "$f does not exist"; continue; }
  # --no-interpolate: `:?` is evaluated at interpolation time, so without this the command would die
  # on the very variables it is here to check for.
  json="$(docker compose -f "$f" config --no-interpolate --format json)" || { err "$f does not parse" "$f"; continue; }
  report="$(printf '%s' "$json" | HC_SERVICES="$COMPOSE_SERVICES" python3 -c '
import json, os, sys

wanted = os.environ["HC_SERVICES"].split()
doc = json.load(sys.stdin)
problems = []
for service in wanted:
    # Service keys differ per file: `messaging` in quality, `dev-messaging` in dev,
    # `hc-market-messaging` in production. Matched by suffix rather than by a per-file table.
    matches = {n: s for n, s in doc.get("services", {}).items() if n == service or n.endswith("-" + service)}
    if not matches:
        problems.append("no compose service for %s" % service)
        continue
    for name, spec in matches.items():
        env = spec.get("environment") or {}
        if isinstance(env, list):                       # the `- KEY=value` spelling
            env = dict(e.split("=", 1) for e in env if "=" in e)
        value = env.get("HEALTHCONNECT_PRIVACY_PEPPER")
        if value is None:
            problems.append("%s sets no HEALTHCONNECT_PRIVACY_PEPPER" % name)
        elif not value.startswith("${HC_PRIVACY_PEPPER:?"):
            problems.append("%s reads HEALTHCONNECT_PRIVACY_PEPPER as %s — it must be ${HC_PRIVACY_PEPPER:?...}, required and without a default" % (name, value))
        else:
            print("ok   %s" % name)
print("\n".join("bad  " + p for p in problems))
')"
  printf '%s\n' "$report" | sed '/^$/d' | sed "s#^#  $f: #"
  if printf '%s' "$report" | grep -q '^bad  '; then
    err "a service that derives erased-subject aliases would start unpeppered. isErased then answers false for people it has erased and the consumer writes their real login back — silently, on a green stack. See decisions.md D35." "$f"
  fi
done

# --- 2. and committed in no profile a real deployment loads --------------------------------------
for s in $SERVICE_DIRS; do
  for y in application.yml application-dev.yml application-prod.yml; do
    p="$s/src/main/resources/config/$y"
    [ -f "$p" ] || continue
    hit="$(HC_FILE="$p" python3 -c '
import os, re, sys

# Any of: `pepper: v`, `privacy.pepper: v`, `healthconnect.privacy.pepper: v`, and the
# uppercase-underscore spelling. The value is what is judged, so `pepper:` alone and `pepper: ""`
# are not reported — an empty pepper is the documented default, not a leaked secret.
key = re.compile(r"^\s*(?:[A-Za-z0-9_-]+[._])*pepper\s*:\s*(.*)$", re.I)
for n, line in enumerate(open(os.environ["HC_FILE"], encoding="utf-8"), 1):
    m = key.match(line)
    if not m:
        continue
    value = re.sub(r"\s+#.*$", "", m.group(1)).strip().strip("\"\x27").strip()
    if value:
        print("%d: %s" % (n, line.rstrip()))
')"
    if [ -n "$hit" ]; then
      printf '%s\n' "$hit" | sed "s#^#  $p:#"
      err "a pepper value is committed in a profile a real deployment loads. This repository is public, and a published pepper is not a pepper. src/test/resources carries a fixture on purpose; this is not that." "$p"
    fi
  done
done

[ "$fail" = 0 ] && echo "ok   the pepper is required by every aliasing service and committed in no runtime profile"
exit "$fail"
