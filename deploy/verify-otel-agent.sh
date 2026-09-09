#!/usr/bin/env bash
#
# Prove the OpenTelemetry agent INSTRUMENTS, rather than merely loading.
#
# decisions.md D63, backlog NEW-23. CLAUDE.md has carried this recipe as prose since D25:
#
#     OTEL_TRACES_EXPORTER=logging OTEL_METRICS_EXPORTER=none java -javaagent:… -jar …
#     # then hit an endpoint and look for a SERVER span carrying http.route
#
# and it was run once, by hand, in August 2026. A recipe nobody can re-run is a claim, so this is
# that recipe as a command. It needs NO estate, NO database, NO network egress and NO container: a
# JDK, the agent jar, and one loopback request.
#
# WHY THE DISTINCTION MATTERS, and why "the tile is green" is not an answer. The agent reports
# `jvm_thread_count` off JMX MBeans whether or not it rewrites a single application class, and
# `service:up:current` on the fleet dashboards is derived from exactly that series. hc-patient
# records the failure this exists to catch: the 2.9.0 agent their older images pin loads, logs its
# banner, exports JVM metrics and instruments NOTHING on a modern JDK, in silence. Every up-tile
# would have been green throughout.
#
#   ./deploy/verify-otel-agent.sh                       # find the agent, run the probe, assert
#   ./deploy/verify-otel-agent.sh --agent /path/to.jar  # name it explicitly
#   ./deploy/verify-otel-agent.sh --describe            # print the contract; runs no JVM
#
# --describe exists for CI. The workflow cannot afford a 24MB agent download on every push, but it
# CAN assert that CLAUDE.md's prose and this script still name the same environment variables and
# the same span attribute — which is the half that rots. The values it prints are the live
# constants below, never a second copy of them, so the two cannot drift apart in this file.
#
# WHAT IT DOES NOT PROVE, said plainly because the temptation is to read it as more. The probe is a
# `com.sun.net.httpserver` server instrumented by the agent's `java-http-server` module. That is a
# real class rewrite of a real application class — which is the property in question — but it is
# NOT Tomcat, and it is not the reactive stack the gateway runs. A regression confined to
# `servlet`/`tomcat`/`reactor-netty` would pass here. Bringing up a service jar instead would need
# a PostgreSQL or a MongoDB per service and would make this unrunnable in the loop it exists for;
# the trade is deliberate. If you are chasing a suspected framework-specific gap, run a real
# service under the same two variables and read its log.
set -euo pipefail

# The contract. --describe prints these; the assertions below use these; there is one copy.
readonly TRACES_EXPORTER_VAR='OTEL_TRACES_EXPORTER'
readonly TRACES_EXPORTER_VALUE='logging'
readonly METRICS_EXPORTER_VAR='OTEL_METRICS_EXPORTER'
readonly METRICS_EXPORTER_VALUE='none'
readonly SPAN_KIND='SERVER'
readonly ROUTE_ATTRIBUTE='http.route'
readonly AGENT_JAR_IN_IMAGE='/app/otel-javaagent.jar'
readonly PROBE_ROUTE='/api/professionals/count'

AGENT=''
PORT="${HC_OTEL_PROBE_PORT:-19731}"
SECONDS_TO_RUN="${HC_OTEL_PROBE_SECONDS:-6}"
DESCRIBE=0

while [ $# -gt 0 ]; do
  case "$1" in
    --agent) AGENT="${2:?--agent needs a path}"; shift 2 ;;
    --port) PORT="${2:?--port needs a number}"; shift 2 ;;
    --describe) DESCRIBE=1; shift ;;
    -h|--help) sed -n '2,40p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [ "$DESCRIBE" = 1 ]; then
  # Machine-readable, one `key=value` per line. Consumed by build.yml's
  # "CLAUDE.md's agent recipe and verify-otel-agent.sh must agree".
  echo "traces-exporter-var=$TRACES_EXPORTER_VAR"
  echo "traces-exporter-value=$TRACES_EXPORTER_VALUE"
  echo "metrics-exporter-var=$METRICS_EXPORTER_VAR"
  echo "metrics-exporter-value=$METRICS_EXPORTER_VALUE"
  echo "span-kind=$SPAN_KIND"
  echo "route-attribute=$ROUTE_ATTRIBUTE"
  echo "agent-jar-in-image=$AGENT_JAR_IN_IMAGE"
  exit 0
fi

HERE="$(cd "$(dirname "$0")/.." && pwd)"

# --- Find a JDK ---------------------------------------------------------------------------------
#
# `java Probe.java` is a single-file source launch and needs the compiler module, so a JRE fails
# here — and /usr/lib/jvm/java-25-openjdk-amd64 on jacserver is exactly that. CLAUDE.md's JDK trap
# in one sentence: it is a JRE with no javac, and the failure it produces elsewhere is a build that
# silently passes. Refuse early and name the working one rather than let the launch fail obscurely.
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
JAVA_BIN="${JAVA_BIN:-$(command -v java || true)}"
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  echo "no java found — export JAVA_HOME=/usr/lib/jvm/jdk-25.0.2-oracle-x64" >&2
  exit 1
fi
if [ ! -x "${JAVA_BIN%/java}/javac" ]; then
  echo "$JAVA_BIN is a JRE (no javac beside it), and a single-file source launch needs a compiler." >&2
  echo "export JAVA_HOME=/usr/lib/jvm/jdk-25.0.2-oracle-x64" >&2
  exit 1
fi

# --- Find the agent -----------------------------------------------------------------------------
#
# Preference order, and every one of them is a real place it lives: named on the command line, in
# the environment, or fetched into target/otel by maven-dependency-plugin during any build. The
# fourth place is inside a published image, which is a docker cp rather than a path, so it is
# printed rather than searched — this script starts no containers.
if [ -z "$AGENT" ]; then
  AGENT="${HC_OTEL_AGENT_JAR:-}"
fi
if [ -z "$AGENT" ]; then
  AGENT="$(find "$HERE" -maxdepth 4 -path '*/target/otel/otel-javaagent.jar' -print -quit 2>/dev/null || true)"
fi
if [ -z "$AGENT" ] || [ ! -f "$AGENT" ]; then
  cat >&2 <<EOF
no OpenTelemetry agent jar found. Any of these produces one:

  (cd catalog && ./mvnw -q package -DskipTests)     # -> catalog/target/otel/otel-javaagent.jar
  docker cp <a running hc-market container>:$AGENT_JAR_IN_IMAGE /tmp/otel-javaagent.jar
  ./deploy/verify-otel-agent.sh --agent /tmp/otel-javaagent.jar
EOF
  exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# The probe. It serves ONE route on loopback and calls it, which is the whole experiment: an
# application class the agent had to rewrite, exercised, with the span printed to the console
# instead of being sent anywhere. No dependency on anything outside the JDK.
cat > "$WORK/Probe.java" <<EOF
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Probe {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("probe.port"));
        long seconds = Long.parseLong(System.getProperty("probe.seconds"));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("$PROBE_ROUTE", exchange -> {
            byte[] body = "18".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        HttpClient client = HttpClient.newHttpClient();
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "$PROBE_ROUTE")).build(),
                HttpResponse.BodyHandlers.ofString()
            );
            Thread.sleep(500);
        }
        server.stop(0);
    }
}
EOF

echo "agent : $AGENT ($(wc -c < "$AGENT") bytes)"
echo "java  : $JAVA_BIN"
echo "probe : GET $PROBE_ROUTE on 127.0.0.1:$PORT for ${SECONDS_TO_RUN}s"
echo

# OTEL_LOGS_EXPORTER is set beside CLAUDE.md's two. It is not part of the recipe's contract and is
# not in --describe: the agent defaults it to otlp, so leaving it alone means every log record is
# batched at an endpoint nothing is listening on, and the output this parses fills with export
# stack traces. Silencing it changes nothing about whether a SERVER span appears.
set +e
env \
  "$TRACES_EXPORTER_VAR=$TRACES_EXPORTER_VALUE" \
  "$METRICS_EXPORTER_VAR=$METRICS_EXPORTER_VALUE" \
  OTEL_LOGS_EXPORTER=none \
  OTEL_SERVICE_NAME=hc-market-otel-agent-probe \
  "$JAVA_BIN" -javaagent:"$AGENT" \
    -Dprobe.port="$PORT" -Dprobe.seconds="$SECONDS_TO_RUN" \
    "$WORK/Probe.java" > "$WORK/out.log" 2>&1
rc=$?
set -e

if [ "$rc" != 0 ]; then
  echo "the probe JVM exited $rc:" >&2
  tail -20 "$WORK/out.log" >&2
  echo >&2
  echo "if that is 'Address already in use', pass --port <free port>." >&2
  exit 1
fi

fail=0

# 1. LOADED. The banner alone, which is what a green up-tile is worth.
loaded=$(grep -m1 'opentelemetry-javaagent - version:' "$WORK/out.log" || true)
if [ -n "$loaded" ]; then
  echo "ok   agent LOADED  — ${loaded#*VersionLogger - }"
else
  echo "FAIL agent did not load: no version banner in the probe's output." >&2
  fail=1
fi

# 2. INSTRUMENTS. A SERVER span for the route, carrying http.route. Both conditions on ONE line and
# both named in the message, because a battery that reports as a single number cannot say which
# half went. Matched with grep -F on the literals so a regex metacharacter in a route name (there
# is none today) could never quietly widen this.
span=$(grep -F " $SPAN_KIND " "$WORK/out.log" | grep -F "$ROUTE_ATTRIBUTE=$PROBE_ROUTE" | head -1 || true)
if [ -n "$span" ]; then
  echo "ok   agent INSTRUMENTS — a $SPAN_KIND span carries $ROUTE_ATTRIBUTE=$PROBE_ROUTE"
  echo
  echo "$span"
else
  echo "FAIL agent LOADED but INSTRUMENTED NOTHING: no $SPAN_KIND span carrying" >&2
  echo "     $ROUTE_ATTRIBUTE=$PROBE_ROUTE appeared. This is hc-patient's 2.9.0 failure — the" >&2
  echo "     banner logs, JVM metrics flow, and no application class is rewritten." >&2
  echo >&2
  tail -20 "$WORK/out.log" >&2
  fail=1
fi

exit "$fail"
