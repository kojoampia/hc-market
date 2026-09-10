#!/usr/bin/env bash
#
# The D77 precedence check must fail on each of the six states it exists to refuse.
#
# WHY THIS EXISTS. `A filter chain's precedence may not be declared where Spring cannot read it` is
# the only thing in this repository that can see a class-level `@Order` on a filter chain in a service
# with no integration test of its own — messaging and payout have never had a hand-written chain, so
# nothing there is written to be red about one. A check nobody drives is a claim, and this family's
# history is nine fail-opens deep: the check's FIRST version reported `ok` for the two catalog chains
# and the booking chain it had just been written to guard, because the shared stripper truncated all
# three at their own path patterns and only the generated chain above the truncation point was ever
# read. It passed, having never seen its subject.
#
# HOW IT DRIVES THE SHIPPED CODE. The step is lifted out of build.yml by name, exactly as
# host-probe-attribution.sh lifts functions out of deploy-prod.sh, so what runs here is the shipped
# text and not a restatement of it. An empty lift is fatal rather than skipped.
#
# It runs against a SYNTHETIC tree rather than the repository, because five of the six states are
# breakages — a missing stripper, a deleted config package — and a test that mutates the checkout it
# is running in leaves the checkout broken when it fails part-way through.
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 1

workflow=.github/workflows/build.yml
stepname="A filter chain's precedence may not be declared where Spring cannot read it"
fail=0

pass() { echo "ok   $1"; }
bad() {
  echo "::error file=$workflow::$1"
  fail=1
}

for f in "$workflow" .github/checks/strip-comments.awk; do
  if [ ! -f "$f" ]; then
    echo "::error::$f does not exist, so this test cannot drive the check it exists to drive. See decisions.md D77."
    exit 1
  fi
done

work=$(mktemp -d)
step="$work/step.sh"
trap 'rm -rf "$work"' EXIT

# Stops at the next step's `- name:` OR at the comment block that introduces it, so what is lifted is
# this step and nothing else. Without the second condition the following step's header comments come
# along; they are harmless as bash comments, which is exactly why nobody would notice the lift drifting.
awk -v want="      - name: $stepname" '
  $0 == want { p = 1; next }
  p && (/^      - name: / || /^      #/) { exit }
  p { print }
' "$workflow" | sed -e '/^ *run: |$/d' -e 's/^          //' > "$step"

if [ ! -s "$step" ]; then
  echo "::error file=$workflow::could not lift the step named '$stepname' out of the workflow — it has been renamed or reindented, and every assertion below would run against an empty script and pass. See decisions.md D77."
  exit 1
fi
if ! grep -q 'strip-comments.awk' "$step"; then
  echo "::error file=$workflow::the lifted step does not call the shared stripper, so either the lift took the wrong block or the check has grown a private stripper. See decisions.md D77 and D56."
  exit 1
fi
pass "lifted $(wc -l < "$step") lines of the shipped step"

# ---------------------------------------------------------------------------------------------------
# The synthetic estate. Five services, each with the generated chain; one hand-written chain beside
# it in `catalog`. Every fixture carries a path pattern in a string literal AND a javadoc quoting
# `{@code @Order}` above the class, because those are the two things that broke the first version.
tree=$work/estate
build_estate() {
  rm -rf "$tree"
  mkdir -p "$tree/.github/checks" "$tree/jdl"
  cp .github/checks/strip-comments.awk "$tree/.github/checks/"
  for svc in gateway catalog booking messaging payout; do
    mkdir -p "$tree/$svc/src/main/java/net/jojoaddison/config"
    printf 'application {\n  config { baseName healthconnect%s }\n}\n' "$svc" > "$tree/jdl/$svc.jdl"
    cat > "$tree/$svc/src/main/java/net/jojoaddison/config/SecurityConfiguration.java" <<'JAVA'
package net.jojoaddison.config;

/**
 * The generated chain. It carries no {@code @Order} and must not: it matches any request and has to
 * be published last.
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http.authorizeHttpRequests(authz -> authz.requestMatchers("/api/admin/**").hasAuthority("X"));
        return http.build();
    }
}
JAVA
  done
  cat > "$tree/catalog/src/main/java/net/jojoaddison/config/InternalApiSecurityConfiguration.java" <<'JAVA'
package net.jojoaddison.config;

/**
 * A hand-written chain. Its {@code @Order} is on the {@code @Bean} method, and this paragraph quotes
 * {@code @Order} above the class declaration on purpose — unstripped, that alone fails the check.
 */
@Configuration
public class InternalApiSecurityConfiguration {

    static final String INTERNAL_PATHS = "/internal/**";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 5)
    public SecurityFilterChain internalApiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(INTERNAL_PATHS);
        return http.build();
    }
}
JAVA
}

run_estate() { (cd "$tree" && bash -e "$step" 2>&1); }

expect_pass() { # $1 label
  local out rc
  out=$(run_estate); rc=$?
  if [ "$rc" = 0 ]; then
    pass "$1"
  else
    bad "$1 — expected the check to pass and it exited $rc: $(printf '%s' "$out" | grep '^::error' | head -1)"
  fi
}

expect_fail() { # $1 label, $2 substring the message must contain
  local out rc
  out=$(run_estate); rc=$?
  if [ "$rc" = 0 ]; then
    bad "$1 — THE CHECK PASSED on a state it exists to refuse"
  elif printf '%s' "$out" | grep -qF "$2"; then
    pass "$1 — refused, naming $2"
  else
    bad "$1 — refused, but the message names neither the file nor the cause: $(printf '%s' "$out" | grep '^::error' | head -1)"
  fi
}

# 0. THE CONTROL, and it is the assertion the first version of this check would have failed. A
#    correct estate must pass, AND the count must prove the hand-written chain was actually read —
#    "scanned 5" means the derivation saw only the generated chains and the subject was invisible.
build_estate
out=$(run_estate)
if [ "$?" != 0 ]; then
  bad "a correct synthetic estate is refused: $(printf '%s' "$out" | grep '^::error' | head -1)"
elif ! printf '%s' "$out" | grep -q 'scanned 6 chain-declaring files across 5 services'; then
  bad "a correct estate passes but the count is wrong: '$(printf '%s' "$out" | tail -1)'. Six chains are planted in five services; a smaller number means files are being skipped and the check is passing on files it never read — which is exactly how its first version reported ok for all three of its subjects"
else
  pass "a correct estate passes, and all six planted chains were read"
fi

# 1. The defect itself: @Order on the @Configuration class of a hand-written chain.
build_estate
f=$tree/catalog/src/main/java/net/jojoaddison/config/InternalApiSecurityConfiguration.java
perl -0pi -e 's/\@Configuration\npublic class/\@Configuration\n\@Order(Ordered.HIGHEST_PRECEDENCE + 5)\npublic class/' "$f"
perl -0pi -e 's/    \@Bean\n    \@Order\(Ordered\.HIGHEST_PRECEDENCE \+ 5\)\n/    \@Bean\n/' "$f"
if grep -qE '^@Order\(' "$f" && ! grep -qE '^    @Order\(' "$f"; then
  expect_fail "a hand-written chain with @Order on the class" "InternalApiSecurityConfiguration.java"
else
  bad "mutation 1 did not apply, so its result says nothing about the check"
fi

# 2. The state no integration test in this estate can see: a chain in a service that has never had
#    one. messaging and payout have no hand-written chain and nothing written to be red about one.
build_estate
cat > "$tree/messaging/src/main/java/net/jojoaddison/config/NotificationStreamSecurityConfiguration.java" <<'JAVA'
package net.jojoaddison.config;

/** A chain in a service that has never had one. Note the {@code @Order} quoted here, above the class. */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 7)
public class NotificationStreamSecurityConfiguration {

    static final String STREAM_PATHS = "/stream/**";

    @Bean
    public SecurityFilterChain notificationStreamFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(STREAM_PATHS);
        return http.build();
    }
}
JAVA
expect_fail "a new chain in a service with no test of its own" "NotificationStreamSecurityConfiguration.java"

# 3. The stripper is gone. Absent it, every fixture strips to nothing, no class declaration is found,
#    and the check must say so rather than report an error it cannot attribute — or pass.
build_estate
rm -f "$tree/.github/checks/strip-comments.awk"
expect_fail "the shared stripper is absent" "strip-comments.awk is missing"

# 4. The derivation matches nothing in one service. Every service in this estate has at least the
#    generated chain, so an empty scan is blindness rather than cleanliness.
build_estate
rm -rf "$tree/payout/src/main/java/net/jojoaddison/config"
expect_fail "a service whose config package is gone" "payout"

# 5. No JDL at all — the service list is derived from it, so this must not scan nothing and pass.
build_estate
rm -f "$tree"/jdl/*.jdl
expect_fail "jdl/*.jdl matches nothing" "matched nothing"

# 6. A chain file with no class declaration the check can find. It cannot tell a class-level @Order
#    from a method-level one in that file, and must refuse rather than choose.
build_estate
cat > "$tree/payout/src/main/java/net/jojoaddison/config/OddlyDeclaredConfiguration.java" <<'JAVA'
package net.jojoaddison.config;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public
class
OddlyDeclaredConfiguration {

    @Bean
    public SecurityFilterChain oddFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/odd/**").build();
    }
}
JAVA
expect_fail "a chain whose class declaration the check cannot find" "OddlyDeclaredConfiguration.java"

# 7. AND THE LAST STATE IS THE ONE THAT WOULD MAKE ALL OF THE ABOVE MEANINGLESS. A stripper that
#    output nothing would satisfy every `expect_fail` here — five of the six refusals would fire for
#    the wrong reason. So the control at case 0 is re-run last, against the real stripper, after all
#    the breaking: it must still pass and still report six chains.
build_estate
expect_pass "the estate still passes after every mutation is reverted"

exit "$fail"
