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
FILES=("$RULES" "$QUALITY" "$DEV" "$PROD")

BACKUP="$(mktemp -d)"
for f in "${FILES[@]}"; do
  mkdir -p "$BACKUP/$(dirname "$f")"
  cp "$f" "$BACKUP/$f"
done
restore() {
  for f in "${FILES[@]}"; do cp "$BACKUP/$f" "$f"; done
  rm -rf "$BACKUP"
}
trap restore EXIT

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
expect green "telemetry on, marker removed"

echo
echo "=== the rules file is missing entirely ==="
rm -f "$RULES"
expect red "the rules file does not exist"

echo
if [ "$failures" -ne 0 ]; then
  echo "$failures of the assertions above did not behave as required."
  exit 1
fi
echo "all assertions behaved as required."
