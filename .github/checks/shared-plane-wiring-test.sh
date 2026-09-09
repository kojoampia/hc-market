#!/usr/bin/env bash
# ==============================================================================
#  shared-plane-wiring.sh's own test — decisions.md D66.
#
#  The check beside this file is green on a correct tree, which says nothing at all: six checks in
#  this repository have reported `ok` having read nothing, and two of them were consolidations of
#  earlier fail-opens. This constructs each broken state on a COPY of the real files, asserts the
#  mutation actually applied before believing its result, and requires the check to go red.
#
#  Every mutation is one this repository has either had or would plausibly write:
#
#    1  the network name hardcoded          — NEW-25's exact shape, and the state `main` was in
#    2  the broker address hardcoded        — the same, one variable along
#    3  ONLY the binder property hardcoded  — the half-move the compose file warns about at the line
#    4  the Consul host hardcoded
#    5  a default renamed in the script     — the two-defaults drift the pairing exists to catch
#    6  the default assignment commented out
#    7  the compose `name:` commented out   — D64's review's fail-open, asked of this check
#    8  env_for_compose stops exporting     — a `--down` addressing a different plane
#    9  the membership refusal deleted      — preflight green against a plane nothing can reach
#   10  the network key renamed             — the enumerated key, which must REFUSE rather than skip
#   11  one service given its own broker    — two planes in one estate, reported as a count
#   12  the compose file absent             — an unreadable subject, which must not read as clean
#
#      ./.github/checks/shared-plane-wiring-test.sh
# ==============================================================================
set -Eeuo pipefail

HERE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE_DIR/../.." && pwd)"
CHECK="$HERE_DIR/shared-plane-wiring.sh"
[[ -f "$CHECK" ]] || { echo "cannot find $CHECK" >&2; exit 1; }

pass=0; fail=0
note() { printf '  ok   %s\n' "$*"; pass=$((pass + 1)); }
bad()  { printf '  FAIL %s\n' "$*" >&2; fail=$((fail + 1)); }

WORK="$(mktemp -d "${TMPDIR:-/tmp}/hc-shared-plane-test-XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

fresh() { # fresh <case> -> prints the sandbox directory holding a pristine copy of both files
  local d="$WORK/$1"; mkdir -p "$d"
  cp "$ROOT/quality/compose.yml" "$d/compose.yml"
  cp "$ROOT/quality/startup.sh"  "$d/startup.sh"
  printf '%s' "$d"
}

run_check() { # run_check <sandbox> -> exit status of the check over that sandbox only
  ( cd "$ROOT" && HC_PAIRS="$1/compose.yml:$1/startup.sh" HC_STARTUP="$1/startup.sh" \
      bash -e "$CHECK" >"$1/out.txt" 2>&1 ) && return 0 || return $?
}

# expect_red <case> <what changed> <a string the mutant must now contain> — plus the control that
# the ORIGINAL text is gone, because a sed that matched nothing leaves a green check reading green
# for the wrong reason. That is not hypothetical: it is how five harnesses in this repository
# reported success while testing nothing.
#
# And an expected fragment of the ERROR, because "red" is not the assertion — a check that is red on
# everything is red here too, and a mutation that trips a neighbouring sub-check reports a defect
# nobody introduced. Each case must fail through the door it was aimed at.
expect_red() { # expect_red <sandbox> <name> <must-be-present> <must-be-absent> <file> <error-fragment>
  local d="$1" name="$2" present="$3" absent="$4" f="$5" want="$6"
  grep -qF -- "$present" "$f" || { bad "$name — the mutation did not apply ('$present' is not in the file)"; return; }
  if [[ -n "$absent" ]] && grep -qF -- "$absent" "$f"; then
    bad "$name — the mutation did not replace the original ('$absent' is still in the file)"; return
  fi
  if [[ "$f" == *.sh ]] && ! bash -n "$f"; then bad "$name — the mutant script does not parse, so the check aborted rather than failing"; return; fi
  if run_check "$d"; then
    bad "$name — the check PASSED on a broken tree"
    sed -n '1,200p' "$d/out.txt" >&2
    return
  fi
  if grep -qF -- "$want" "$d/out.txt"; then
    note "$name → red"
  else
    bad "$name — red, but not through the door it was aimed at (no '$want' in the output)"
    grep '::error::' "$d/out.txt" >&2 || true
  fi
}

printf '\nControl: the committed files\n'
d="$(fresh control)"
if run_check "$d"; then note "green on an unmutated copy"; else bad "the check is red on the committed files"; cat "$d/out.txt" >&2; fi

printf '\nCompose: each half hardcoded again\n'
d="$(fresh m1)"; sed -i 's|name: ${HC_SHARED_NETWORK:-hcnet}|name: hcnet|' "$d/compose.yml"
expect_red "$d" "1  the network name hardcoded" 'name: hcnet' 'name: ${HC_SHARED_NETWORK' "$d/compose.yml" "still joins 'hcnet'"

d="$(fresh m2)"; sed -i 's|SPRING_KAFKA_BOOTSTRAP_SERVERS: ${HC_SHARED_KAFKA:-hc-shared-quality-kafka}:9092|SPRING_KAFKA_BOOTSTRAP_SERVERS: hc-shared-quality-kafka:9092|' "$d/compose.yml"
expect_red "$d" "2  the broker address hardcoded" 'SPRING_KAFKA_BOOTSTRAP_SERVERS: hc-shared-quality-kafka:9092' '' "$d/compose.yml" "both must be 'hc-ci-probe-kafka:9092'"

d="$(fresh m3)"; sed -i 's|SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS: ${HC_SHARED_KAFKA:-hc-shared-quality-kafka}:9092|SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS: hc-shared-quality-kafka:9092|' "$d/compose.yml"
expect_red "$d" "3  only the BINDER property hardcoded" 'SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS: hc-shared-quality-kafka:9092' 'BINDER_BROKERS: ${HC_SHARED_KAFKA' "$d/compose.yml" "both must be 'hc-ci-probe-kafka:9092'"

d="$(fresh m4)"; sed -i 's|SPRING_CLOUD_CONSUL_HOST: ${HC_SHARED_CONSUL:-hc-shared-quality-consul}|SPRING_CLOUD_CONSUL_HOST: hc-shared-quality-consul|' "$d/compose.yml"
expect_red "$d" "4  the Consul host hardcoded" 'SPRING_CLOUD_CONSUL_HOST: hc-shared-quality-consul' 'SPRING_CLOUD_CONSUL_HOST: ${HC_SHARED_CONSUL' "$d/compose.yml" "renders SPRING_CLOUD_CONSUL_HOST='hc-shared-quality-consul'"

printf '\nDefaults: the pair that must stay one value\n'
d="$(fresh m5)"; sed -i 's|^SHARED_KAFKA="${HC_SHARED_KAFKA:-hc-shared-quality-kafka}"|SHARED_KAFKA="${HC_SHARED_KAFKA:-hc-shared-dev-kafka}"|' "$d/startup.sh"
expect_red "$d" "5  the script's default renamed" 'hc-shared-dev-kafka' '' "$d/startup.sh" "defaults HC_SHARED_KAFKA to 'hc-shared-dev-kafka'"

d="$(fresh m6)"; sed -i 's|^SHARED_NETWORK="${HC_SHARED_NETWORK:-hcnet}"|# SHARED_NETWORK="${HC_SHARED_NETWORK:-hcnet}"\nSHARED_NETWORK="hcnet"|' "$d/startup.sh"
expect_red "$d" "6  the default assignment commented out, the literal beside it" 'SHARED_NETWORK="hcnet"' '' "$d/startup.sh" "defaults HC_SHARED_NETWORK to '«nothing this check can read»'"

d="$(fresh m7)"; sed -i 's|^    name: ${HC_SHARED_NETWORK:-hcnet}$|    # name: ${HC_SHARED_NETWORK:-hcnet}|' "$d/compose.yml"
expect_red "$d" "7  compose's name: commented out (D64's review's fail-open)" '# name: ${HC_SHARED_NETWORK:-hcnet}' '' "$d/compose.yml" "still joins 'hcnet'"

printf '\nScript behaviour\n'
d="$(fresh m8)"; sed -i 's|^  export HC_SHARED_NETWORK="$SHARED_NETWORK" HC_SHARED_CONSUL="$SHARED_CONSUL" \\$|  : \\|' "$d/startup.sh"
expect_red "$d" "8  env_for_compose stops exporting the three" '  : \' 'export HC_SHARED_NETWORK=' "$d/startup.sh" "env_for_compose exports"

d="$(fresh m9)"; sed -i '/grep -Fxq "\$SHARED_NETWORK"/,+1d;/docker inspect -f .{{range \$k, \$v := \.NetworkSettings\.Networks}}/d' "$d/startup.sh"
expect_red "$d" "9  the membership refusal deleted" 'is not running' 'grep -Fxq' "$d/startup.sh" "accepted a Consul and a broker"

printf '\nSubjects that must be refused rather than skipped\n'
d="$(fresh m10)"; sed -i -e 's|^  hcnet:$|  sharednet:|' -e 's|networks: \[quality, qualitynet, hcnet\]|networks: [quality, qualitynet, sharednet]|' "$d/compose.yml"
expect_red "$d" "10 the network key renamed" '  sharednet:' '' "$d/compose.yml" "declares no network under the key"

d="$(fresh m11)"; sed -i 's|^      SPRING_CLOUD_STREAM_DEFAULT_GROUP: hc-market-payout$|      SPRING_CLOUD_STREAM_DEFAULT_GROUP: hc-market-payout\n      SPRING_CLOUD_CONSUL_HOST: some-other-consul|' "$d/compose.yml"
expect_red "$d" "11 one service given its own Consul" 'SPRING_CLOUD_CONSUL_HOST: some-other-consul' '' "$d/compose.yml" "more than one shared-plane address"

d="$(fresh m12)"; rm -f "$d/compose.yml"
if run_check "$d"; then bad "12 the compose file absent — the check PASSED"; else note "12 the compose file absent → red"; fi

printf '\n%d ok, %d failed\n' "$pass" "$fail"
exit $(( fail > 0 ))
