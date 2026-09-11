#!/usr/bin/env bash
#
# Does the OpenTelemetry agent carry a MICROMETER meter to OTLP?
#
# decisions.md D85, backlog NEW-44. D84 shipped the gateway identity dashboard with a
# NOT-YET-TRANSPORTED marker because nothing in this repository collects `gateway_identity_*`, and it
# refused to guess which of three routes should. This is the measurement that decides between the first
# two, and it is a measurement rather than a reading of the agent's documentation for the reason D63
# exists: "the agent is present" was mistaken for "the agent instruments", and `verify-otel-agent.sh`
# reports loading and instrumenting SEPARATELY because of it.
#
# THE QUESTION, precisely. The agent's `micrometer-1.5` instrumentation works by adding an
# OpenTelemetry-backed registry to `io.micrometer.core.instrument.Metrics.globalRegistry`. So a meter is
# carried if and only if it reaches THAT registry. This script asks whether that bridge functions at all
# on this agent version and this JDK — one counter, one gauge, on the global registry, with the logging
# metrics exporter, and a grep for the names in what the agent emits.
#
# WHAT IT DOES NOT ANSWER, and the half it cannot: whether the GATEWAY's `MeterRegistry` bean is the
# global registry. That is Spring Boot's `management.metrics.use-global-registry` and is asserted in the
# gateway's own suite by `MicrometerReachesTheGlobalRegistryIT`, because it is a fact about the running
# application context and not about the agent. BOTH must hold for the dashboard's series to arrive; this
# file is one of the two and says so.
#
# It needs NO estate, NO database, NO network egress and NO container — a JDK, the agent jar, and
# micrometer-core on a classpath. Mirrors verify-otel-agent.sh deliberately, including `--describe`.
#
#   ./deploy/verify-micrometer-otlp-bridge.sh
#   ./deploy/verify-micrometer-otlp-bridge.sh --agent /path/to/otel-javaagent.jar
#   ./deploy/verify-micrometer-otlp-bridge.sh --describe     # print the contract; runs no JVM
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# THE LIVE CONSTANTS, printed by --describe so this file and the decision cannot drift apart. D63's
# script does the same and CI compares its output against CLAUDE.md.
readonly COUNTER_NAME='probe.bridge.counter'
readonly GAUGE_NAME='probe.bridge.gauge'
readonly EXPORT_INTERVAL_MS=1000
readonly SETTLE_SECONDS=6
readonly MICROMETER_VERSION='1.16.6'
# THE FLAG THAT DECIDES THE ANSWER. Measured: without it the agent attaches NO registry to
# Metrics.globalRegistry and no Micrometer meter is exported; with it, both probe meters cross. So the
# bridge is OPT-IN and off by default, and a run that tested only the default would report a capability
# as absent — which is this repository's own recurring defect, in the script written to avoid it.
readonly MICROMETER_FLAG='OTEL_INSTRUMENTATION_MICROMETER_ENABLED'

c_r=$'\033[31m'; c_g=$'\033[32m'; c_y=$'\033[33m'; c_d=$'\033[2m'; c_0=$'\033[0m'
ok()   { printf '  %sok%s   %s\n' "$c_g" "$c_0" "$1"; }
bad()  { printf '  %s✗%s    %s\n' "$c_r" "$c_0" "$1"; FAIL=1; }
note() { printf '  %s•%s    %s\n' "$c_y" "$c_0" "$1"; }
dim()  { printf '%s  %s%s\n' "$c_d" "$1" "$c_0"; }
FAIL=0

AGENT=''
DESCRIBE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --agent)    AGENT="${2:-}"; shift 2 ;;
    --describe) DESCRIBE=1; shift ;;
    -h|--help)  sed -n '2,32p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)          printf 'unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

if [ "$DESCRIBE" -eq 1 ]; then
  printf 'verify-micrometer-otlp-bridge contract\n'
  printf '  counter under test    %s\n' "$COUNTER_NAME"
  printf '  gauge under test      %s\n' "$GAUGE_NAME"
  printf '  export interval       %sms\n' "$EXPORT_INTERVAL_MS"
  printf '  settle window         %ss\n' "$SETTLE_SECONDS"
  printf '  micrometer version    %s\n' "$MICROMETER_VERSION"
  printf '  opt-in flag           %s (measured: the bridge is OFF without it)\n' "$MICROMETER_FLAG"
  printf '  registry under test   io.micrometer.core.instrument.Metrics.globalRegistry\n'
  printf '  answers               whether the agent carries a global-registry meter to OTLP\n'
  printf '  does NOT answer       whether the gateway MeterRegistry bean IS the global registry\n'
  exit 0
fi

printf '\n=== 1. the agent jar ===\n'
if [ -z "$AGENT" ]; then
  AGENT="$(find "$ROOT" -path '*/target/otel/otel-javaagent.jar' 2>/dev/null | sort | head -1)"
fi
if [ -z "$AGENT" ] || [ ! -f "$AGENT" ]; then
  bad "no agent jar. Build any service (\`./mvnw package -DskipTests\`) so */target/otel/ exists, docker cp it out of a running container at /app/otel-javaagent.jar, or pass --agent."
  exit 1
fi
ok "agent: ${AGENT#"$ROOT"/} ($(stat -c%s "$AGENT") bytes)"

printf '\n=== 2. a compiler, and micrometer on a classpath ===\n'
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
command -v "$JAVAC" >/dev/null 2>&1 || { bad "no javac at '$JAVAC'. /usr/lib/jvm/java-25-openjdk-amd64 is a JRE with NO compiler — export JAVA_HOME=/usr/lib/jvm/jdk-25.0.2-oracle-x64."; exit 1; }
ok "javac: $("$JAVAC" -version 2>&1)"

CP=''
for a in micrometer-core micrometer-commons; do
  j="$HOME/.m2/repository/io/micrometer/$a/$MICROMETER_VERSION/$a-$MICROMETER_VERSION.jar"
  if [ ! -f "$j" ]; then
    bad "$a $MICROMETER_VERSION is not in ~/.m2. It is the version the gateway resolves; build the gateway once so Maven fetches it."
    exit 1
  fi
  CP="${CP:+$CP:}$j"
done
ok "micrometer $MICROMETER_VERSION on the probe classpath"

printf '\n=== 3. the probe ===\n'
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cat > "$WORK/Probe.java" <<JAVA
import io.micrometer.core.instrument.Metrics;
import java.util.concurrent.atomic.AtomicLong;

// ONE COUNTER AND ONE GAUGE, both on the GLOBAL registry, because that is the only registry the agent's
// micrometer-1.5 instrumentation bridges. A meter on a standalone registry is deliberately NOT used
// here: it would be absent from the output for a reason that has nothing to do with the agent, and the
// script would report the bridge broken when the probe was wrong.
public class Probe {
    public static void main(String[] args) throws Exception {
        Metrics.counter("$COUNTER_NAME", "outcome", "success").increment(7);
        AtomicLong held = new AtomicLong(42);
        Metrics.gauge("$GAUGE_NAME", held, AtomicLong::doubleValue);
        System.out.println("PROBE_REGISTERED registries=" + Metrics.globalRegistry.getRegistries().size());
        // Sleep past several export intervals. A counter is only emitted once the periodic reader runs,
        // so a probe that exits immediately measures nothing and reports the bridge broken.
        Thread.sleep(${SETTLE_SECONDS}000L);
        System.out.println("PROBE_DONE");
    }
}
JAVA
"$JAVAC" -cp "$CP" -d "$WORK" "$WORK/Probe.java" 2>"$WORK/javac.err" || { bad "the probe did not compile:"; sed 's/^/      /' "$WORK/javac.err"; exit 1; }
ok "probe compiled"

# ONE RUN PER STATE. `$1` is the value of the opt-in flag; the probe and every other setting are
# identical, so the only thing that differs between the two readings is that variable.
run_probe() { # run_probe <flag value> <output file>
  OTEL_METRICS_EXPORTER=logging \
  OTEL_TRACES_EXPORTER=none \
  OTEL_LOGS_EXPORTER=none \
  OTEL_METRIC_EXPORT_INTERVAL="$EXPORT_INTERVAL_MS" \
  OTEL_SERVICE_NAME=micrometer-bridge-probe \
  env "$MICROMETER_FLAG=$1" \
    "$JAVA" -javaagent:"$AGENT" -cp "$CP:$WORK" Probe >"$2" 2>&1
  grep -q PROBE_DONE "$2"
}

read_probe() { # read_probe <output file> — prints "registries counter gauge jvm"
  printf '%s %s %s %s\n' \
    "$(grep -oE 'PROBE_REGISTERED registries=[0-9]+' "$1" | grep -oE '[0-9]+$' | head -1)" \
    "$(grep -cF "$COUNTER_NAME" "$1")" \
    "$(grep -cF "$GAUGE_NAME" "$1")" \
    "$(grep -cE 'jvm\.(memory|thread)' "$1")"
}

printf '\n=== 4. run it under the agent in BOTH states ===\n'
dim "OTEL_METRICS_EXPORTER=logging $MICROMETER_FLAG=<false|true> java -javaagent:<agent> Probe"
run_probe false "$WORK/off.txt" || { bad "the probe did not finish with the flag off:"; tail -20 "$WORK/off.txt" | sed 's/^/      /'; exit 1; }
run_probe true  "$WORK/on.txt"  || { bad "the probe did not finish with the flag on:";  tail -20 "$WORK/on.txt"  | sed 's/^/      /'; exit 1; }
read -r off_reg off_c off_g off_jvm <<<"$(read_probe "$WORK/off.txt")"
read -r on_reg  on_c  on_g  on_jvm  <<<"$(read_probe "$WORK/on.txt")"
ok "both runs completed"

printf '\n=== 5. the control: is the agent exporting at all? ===\n'
# WITHOUT THIS, "no micrometer metric" is indistinguishable from "no metric". The agent exports its own
# MBean-derived jvm.* series whether or not the micrometer bridge is attached, so those series are the
# proof that a zero in part 6 means something. D63 exists because a green tile derived from
# jvm_thread_count was read as evidence the agent instrumented anything.
printf '      %-34s off=%s  on=%s\n' 'jvm.* metrics exported' "$off_jvm" "$on_jvm"
if [ "${off_jvm:-0}" -eq 0 ] || [ "${on_jvm:-0}" -eq 0 ]; then
  bad "the agent exported no metrics at all in at least one run, so neither reading below says anything about the micrometer bridge. Fix the agent jar or the exporter settings before reading part 6."
  printf '\nmicrometer otlp bridge: FAILED\n'; exit 1
fi
ok "the agent is exporting, so a zero in part 6 is a fact about micrometer and not about the agent"

printf '\n=== 6. does a MICROMETER meter cross, and in which state? ===\n'
printf '      %-34s %-18s %s\n' '' "$MICROMETER_FLAG=false" "$MICROMETER_FLAG=true"
printf '      %-34s %-18s %s\n' 'registries on globalRegistry' "${off_reg:-0}" "${on_reg:-0}"
printf '      %-34s %-18s %s\n' "$COUNTER_NAME" "$off_c" "$on_c"
printf '      %-34s %-18s %s\n' "$GAUGE_NAME" "$off_g" "$on_g"

if [ "$on_c" -gt 0 ] && [ "$on_g" -gt 0 ] && [ "$off_c" -eq 0 ] && [ "$off_g" -eq 0 ]; then
  ok "THE BRIDGE WORKS AND IS OPT-IN: both instruments cross with $MICROMETER_FLAG=true and neither does without it"
  printf '\n  %sSo NEW-44 route one is OPEN and costs one variable. What remains is whether the gateway\n  MeterRegistry bean IS Metrics.globalRegistry — MicrometerReachesTheGlobalRegistryIT — and whether\n  an estate attaches the agent at all, which is HC_OTEL_JAVA_OPTS and D73 §3.%s\n' "$c_d" "$c_0"
elif [ "$on_c" -gt 0 ] && [ "$on_g" -gt 0 ]; then
  bad "the bridge carries both instruments with the flag on, AND carried something with it off (counter=$off_c gauge=$off_g). That contradicts the opt-in reading this file records — re-measure before trusting either."
elif [ "$on_c" -gt 0 ] || [ "$on_g" -gt 0 ]; then
  bad "PARTIAL with the flag on: counter=$on_c gauge=$on_g. One instrument kind crosses and the other does not, which is narrower than either yes or no — do not record it as 'the bridge works'."
else
  bad "THE BRIDGE CARRIES NO MICROMETER METER even with $MICROMETER_FLAG=true, on this agent and this JDK, while jvm.* exported fine. Route one of NEW-44 is closed; see decisions.md D85 for the remaining two."
fi

printf '\n'
if [ "$FAIL" -ne 0 ]; then printf 'micrometer otlp bridge: FAILED\n'; else printf 'micrometer otlp bridge: ok\n'; fi
exit "$FAIL"
