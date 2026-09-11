#!/usr/bin/env bash
#
# observability-claims.sh's own test.
#
# decisions.md D63. A check that passes on a clean tree proves nothing at all — that is the finding
# behind nine fail-opens in this repository (D48-D56), one of which arrived inside the fix for the
# previous eight. So every guarded thing is mutated SEPARATELY here and the check is required to go
# red for it: an aggregate exit status cannot distinguish "all four guarded" from "one guarded and
# three decorative", and the only way to tell is to break them one at a time.
#
# It also asserts the two states that must PASS, which is the half people leave out. A check that
# refuses everything is red for the wrong reason and would be indistinguishable here from one that
# works.
#
# Every mutation is made on a copy in a scratch directory and the originals are restored by an EXIT
# trap, so a failure part-way through cannot leave a mutated compose file in the tree.
set -uo pipefail

cd "$(dirname "$0")/../.."

CHECK=.github/checks/observability-claims.sh
RULES=deploy/observability/hc-market-rules.yaml
QUALITY=quality/compose.yml
DEV=deploy/docker/docker-compose.dev.yml
PROD=deploy/docker/docker-compose.prod.yml
# PART 5's SUBJECTS — decisions.md D84, backlog NEW-43. The identity dashboard carries the same kind of
# marker the rules file does, held against the same measured fact, and part 5 additionally DERIVES the
# series it may query from the meters class. Both are backed up and restored with the rest, so a failure
# part-way through cannot leave a mutated dashboard in the tree.
DASH=deploy/observability/hc-market-gateway-identity.json
METERS=gateway/src/main/java/net/jojoaddison/management/GatewayIdentityMeters.java
FILES=("$RULES" "$QUALITY" "$DEV" "$PROD" "$DASH" "$METERS")

BACKUP="$(mktemp -d)"
for f in "${FILES[@]}"; do
  mkdir -p "$BACKUP/$(dirname "$f")"
  cp "$f" "$BACKUP/$f"
done
# RESTORE IS CALLED MORE THAN ONCE and must survive it — decisions.md D84. It used to end with
# `rm -rf "$BACKUP"`, which made it SINGLE-USE: the first call emptied the backup and every later one
# copied from nothing, silently, leaving the tree mutated and the EXIT trap printing `cp: cannot stat`
# after the summary had already said everything behaved. Invisible while no case called it twice, and
# part 5's four cases do. Found by running them — it deleted two files out of the working tree.
restore() {
  for f in "${FILES[@]}"; do cp "$BACKUP/$f" "$f"; done
}
trap 'restore; rm -rf "$BACKUP"' EXIT

failures=0

# Run the check the way GitHub Actions runs it — `bash -e`, not plain bash. A plain-bash harness
# has reported confident successes for steps that abort on their first iteration under Actions.
expect() {
  local want="$1" what="$2"
  # `|| rc=$?` rather than a bare call followed by `$?`. Half of this file's assertions expect a
  # NON-zero exit, and a bare call under `bash -e` aborts on the first of them — which presents as
  # a suite that stops after one green assertion and reports success for it.
  local rc=0
  bash -e "$CHECK" > "$BACKUP/out.txt" 2>&1 || rc=$?
  if [ "$want" = red ] && [ "$rc" = 0 ]; then
    echo "FAIL  $what — the check PASSED and should not have"
    sed 's/^/      /' "$BACKUP/out.txt"
    failures=$((failures + 1))
  elif [ "$want" = green ] && [ "$rc" != 0 ]; then
    echo "FAIL  $what — the check FAILED and should not have (exit $rc)"
    sed 's/^/      /' "$BACKUP/out.txt"
    failures=$((failures + 1))
  else
    echo "ok    $what — $want, exit $rc"
    # `|| true` is load-bearing, not defensive noise. A green run has no ::error line, so this
    # grep exits 1 — and under `bash -e` with pipefail, which is how GitHub Actions runs a `run:`
    # block, that aborts the whole script after the first assertion while reporting exit 0 for it.
    # Measured: the first version of this file did exactly that.
    { grep -E '^(::error|note )' "$BACKUP/out.txt" || true; } | sed 's/^/      /' | head -3
  fi
  restore_files
}
restore_files() { for f in "${FILES[@]}"; do cp "$BACKUP/$f" "$f"; done; }

echo "=== the tree as committed must PASS ==="
expect green "clean tree"

echo
echo "=== production stops attaching the agent ==="
# The silent half of D25's trap, one level up from the pom check: the image still carries the jar,
# the build is green, the service starts and serves, and it says nothing to the dashboards.
sed -i 's|\${HC_OTEL_JAVA_OPTS:--javaagent:/app/otel-javaagent.jar}|${HC_OTEL_JAVA_OPTS:-}|' "$PROD"
expect red "prod's JAVA_OPTS loses the agent default"

echo
echo "=== production stops declaring JAVA_OPTS at all ==="
# The fail-open the count guard exists for: nothing to inspect reads as nothing wrong.
sed -i '/^    JAVA_OPTS: \${HC_JAVA_OPTS/d' "$PROD"
expect red "prod declares no JAVA_OPTS"

echo
echo "=== quality reverts to the hardcoded string it carried before D63 ==="
# This is exactly `main` at a33d3cd. The switch stops existing while the file still looks
# deliberate, and (2) alone would call that a pass because no agent is attached either way.
sed -i 's|^    JAVA_OPTS: \${HC_JAVA_OPTS:--Xmx512m -Xms256m} \${HC_OTEL_JAVA_OPTS:-}$|    JAVA_OPTS: -Xmx512m -Xms256m|' "$QUALITY"
expect red "quality's opt-in is replaced by a hardcoded JAVA_OPTS"

echo
echo "=== dev stops declaring JAVA_OPTS at all ==="
sed -i '/^    JAVA_OPTS: \${HC_JAVA_OPTS:-} \${HC_OTEL_JAVA_OPTS:-}$/d' "$DEV"
expect red "dev declares no JAVA_OPTS"

echo
echo "=== the rules file loses its NOT-YET-ATTACHED marker ==="
# The subject of the whole check: five alerts over a signal nothing emits, with nothing left saying
# so, in a file whose install instructions read as ready to run.
sed -i '/^# NOT-YET-ATTACHED:/d' "$RULES"
expect red "the marker is deleted while nothing reports"

echo
echo "=== quality attaches the agent while the marker still claims nothing does ==="
sed -i 's|^    JAVA_OPTS: \${HC_JAVA_OPTS:--Xmx512m -Xms256m} \${HC_OTEL_JAVA_OPTS:-}$|    JAVA_OPTS: -Xmx512m -Xms256m -javaagent:/app/otel-javaagent.jar|' "$QUALITY"
expect red "an environment reports but the file still says none does"

echo
echo "=== the future state: quality attaches it AND the marker is gone ==="
# The control. Without this the whole test is satisfied by a check that refuses everything, and
# the day telemetry is genuinely turned on this is the shape that has to pass.
sed -i 's|^    JAVA_OPTS: \${HC_JAVA_OPTS:--Xmx512m -Xms256m} \${HC_OTEL_JAVA_OPTS:-}$|    JAVA_OPTS: -Xmx512m -Xms256m ${HC_OTEL_JAVA_OPTS:--javaagent:/app/otel-javaagent.jar}|' "$QUALITY"
sed -i '/^# NOT-YET-ATTACHED:/d' "$RULES"
# BOTH markers, because part 5 holds the dashboard's against the same fact part 4 holds the rules
# file's. In the future state where telemetry is genuinely on, neither file may still claim that
# nothing reports — and this case is the control for both parts at once, so leaving the dashboard's
# marker in place here would make it red for a reason the case is not about. Found by CI: this test
# was not re-run locally when part 5 was added, and it is the only thing that noticed.
sed -i 's/NOT-YET-TRANSPORTED/now-transported/' "$DASH"
expect green "telemetry on, both markers removed"

echo
echo "=== the rules file is missing entirely ==="
rm -f "$RULES"
expect red "the rules file does not exist"

# ---- PART 5: the identity dashboard — decisions.md D84, backlog NEW-43 -----------------------------
#
# Each of part 5's guarded things, mutated SEPARATELY. These four were run by hand when part 5 was
# written and that is not where a control belongs: a check is only as good as the states somebody can
# re-run. The restore below puts the rules file back first, since the cases above deleted it and part 4
# would otherwise be red for its own reason and mask every verdict here.
restore

echo
echo "=== the dashboard queries a series no meter publishes ==="
# A panel querying a name nothing emits shows "No data", which is indistinguishable from the transport
# gap the dashboard is already marked for — so the names must correspond, and the list of permitted
# names is DERIVED from the meters class rather than written in the check.
sed -i 's/gateway_identity_accounts/gateway_identity_signups/' "$DASH"
expect red "the dashboard queries a series nothing emits"
restore

echo
echo "=== the dashboard's marker is deleted while nothing transports ==="
sed -i 's/NOT-YET-TRANSPORTED/all-wired-up/' "$DASH"
expect red "the dashboard stops saying nothing carries its series"
restore

echo
echo "=== the dashboard is missing entirely ==="
rm -f "$DASH"
expect red "the dashboard does not exist"
restore

# ---- PART 6: the micrometer bridge flag — decisions.md D85, backlog NEW-44 ------------------------
#
# The agent's micrometer bridge is off by default, so HC_OTEL_JAVA_OPTS alone attaches the agent and
# carries no gateway_identity_* at all. Both failure shapes are cases, because "set to false" and
# "absent" are one line apart and produce the identical silence.
echo
echo "=== the bridge flag is removed from a compose file ==="
sed -i "/OTEL_INSTRUMENTATION_MICROMETER_ENABLED:/d" "$QUALITY"
expect red "quality declares no micrometer bridge flag"
restore

echo
echo "=== the bridge flag is present but false ==="
# A value other than true leaves the bridge off, which is indistinguishable from the variable being
# absent — so it must not be accepted merely because the name is there.
sed -i "s/OTEL_INSTRUMENTATION_MICROMETER_ENABLED: 'true'/OTEL_INSTRUMENTATION_MICROMETER_ENABLED: 'false'/" "$QUALITY"
expect red "quality sets the bridge flag to false"
restore

echo
echo "=== the meters class is missing, so the series list cannot be derived ==="
# THE VACUITY CASE, and the one that matters most: without it part 5 would compare the dashboard's
# queries against an EMPTY set of permitted names and report agreement. That is the shape nine
# fail-opens in this repository took, one of them inside the fix for the previous eight.
rm -f "$METERS"
expect red "the meters class does not exist, so nothing could be derived"
restore

echo
if [ "$failures" -ne 0 ]; then
  echo "$failures of the assertions above did not behave as required."
  exit 1
fi
echo "all assertions behaved as required."
