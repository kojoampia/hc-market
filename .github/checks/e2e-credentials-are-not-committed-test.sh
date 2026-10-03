#!/usr/bin/env bash
# ==============================================================================
#  Drives `e2e-credentials-are-not-committed.sh` against broken states, in a SYNTHETIC TREE.
#
#  Every case builds a throwaway `cypress.config.ts` and `commands.ts`, points the check at them
#  through its `HC_*` variables, and asserts the verdict. Nothing here touches `web/` — a check whose
#  test mutates the tree it guards is one forgotten restore away from committing the defect it
#  planted, which is a real failure in this workspace's history.
#
#  WHY THIS TEST EXISTS AT ALL, for a check over a file nothing executes. Because the check is the
#  ONLY guard: no suite runs Cypress, so a fail-open here is a fail-open with nothing behind it. Three
#  of the check's own fail-opens were found by review rather than here — an optional field marker
#  (case 17), `grep -m1` where JavaScript obeys the last key (18), and a whole-file grep behind a
#  comment claiming the `expose` block (19, 22) — which is the argument for the eight mutants rather
#  than against them.
#
#  ⚠ WHICH CASE DISTINGUISHES WHICH MUTATION IS MEASURED AND HAS MOVED TWICE. Do not assume a case
#  covers what its heading suggests: cases 3, 4 and 5 were the stripper's subjects until the
#  every-binding-line fix refused them twice over, and the stripper's real subjects are now the
#  ABSENCE cases, 6 and 22, plus 12. Each case says what it distinguishes today.
#
#  Every case classifies itself as a `refusal` or a `control`, and the trailer refuses to print unless
#  the counters reconcile with what `report` saw (D98's correction to the account-lifecycle test,
#  which printed a constant and overstated in both directions).
#
#  Read the count off the last line of a run. Do not quote one from a document.
#      ./.github/checks/e2e-credentials-are-not-committed-test.sh
# ==============================================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# Overridable so a MUTANT of the check can be driven against the same cases without editing the tree
# the check guards — which is how case 3's discrimination was established rather than asserted.
CHECK="${HC_CHECK:-$ROOT/.github/checks/e2e-credentials-are-not-committed.sh}"
STRIPPER="$ROOT/.github/checks/strip-comments.awk"

for required in "$CHECK" "$STRIPPER"; do
  [[ -f "$required" ]] || {
    printf '✗ %s is missing — the harness cannot run, and no case below would be about its own subject\n' "$required" >&2
    exit 1
  }
done
[[ -x "$CHECK" ]] || {
  printf '✗ %s is not executable — every case would exit 126 and be reported as a broken fixture\n' "$CHECK" >&2
  exit 1
}

WORK="$(mktemp -d -t hc-e2e-creds-XXXXXX)"
trap 'rm -rf "$WORK"' EXIT

pass=0 fail=0 refusals=0 controls=0 seen=0
refusal() { refusals=$((refusals + 1)); }
control() { controls=$((controls + 1)); }

report() {
  local label=$1 want=$2 got=$3 out=$4
  seen=$((seen + 1))
  if [[ "$want" == "$got" ]]; then
    printf '  ok   %s (exit %s)\n' "$label" "$got"
    pass=$((pass + 1))
  else
    printf '  FAIL %s — wanted exit %s, got %s\n' "$label" "$want" "$got"
    printf '%s\n' "$out" | sed 's/^/       | /'
    fail=$((fail + 1))
  fi
}

# ---------------------------------------------------------------------------------------------
# A correct synthetic pair. Every case starts here and breaks exactly one thing, so a case's subject
# is the difference and not the fixture.
# ---------------------------------------------------------------------------------------------
build() {
  local dir=$1
  rm -rf "$dir"
  mkdir -p "$dir"

  # ⚠ THE COMMENTS HERE ARE THE FIXTURE'S POINT, NOT DECORATION, AND WHERE THEY SIT IS HALF OF IT.
  # They mirror the real subject: a NON-JAVADOC block comment (the house style, and the shape D77's
  # string-blind stripper family keeps being caught by) whose indented lines are byte-identical to the
  # real bindings — placed INSIDE the `expose` block, which is where the subject's own comment is. Cases
  # 3 to 6 restore the defect below it, so an unstripped check reads the comment's copy and reports
  # `ok`; measured, five cases go green with the stripping removed. Two placements have now been wrong
  # here: a javadoc-shaped comment (whose `*`-prefixed lines the per-field pattern skips anyway, so the
  # mutation changed nothing), and a correct-shaped one placed ABOVE `expose:`, which the block scoping
  # added at review puts out of range. A bait outside the scope is not a bait.
  #
  # `allowCypressEnv: false` is quoted in a comment too, outside the block — that assertion reads the
  # whole file, and case 12 is what holds the stripping honest for it.
  cat >"$dir/cypress.config.ts" <<'TS'
import { defineConfig } from 'cypress';

export default defineConfig({
  retries: 2,
  // `allowCypressEnv: false` stays exactly as generated.
  allowCypressEnv: false,
  expose: {
    /*
      The four credentials read the environment:
      adminUsername: process.env.HC_E2E_ADMIN_USERNAME ?? 'admin',
      adminPassword: process.env.HC_E2E_ADMIN_PASSWORD ?? 'admin',
      username: process.env.HC_E2E_USERNAME ?? 'admin',
      password: process.env.HC_E2E_PASSWORD ?? 'admin',
      (Six spaces, so a case below can rewrite the four-space REAL lines and leave this bait intact.)
    */
    adminUsername: process.env.HC_E2E_ADMIN_USERNAME ?? 'admin',
    adminPassword: process.env.HC_E2E_ADMIN_PASSWORD ?? 'admin',
    username: process.env.HC_E2E_USERNAME ?? 'admin',
    password: process.env.HC_E2E_PASSWORD ?? 'admin',
    authenticationUrl: '/api/authenticate',
    jwtStorageName: 'abm-authenticationToken',
  },
});
TS

  cat >"$dir/commands.ts" <<'TS'
export interface Credentials {
  adminUsername: string;
  adminPassword: string;
  username: string;
  password: string;
}

Cypress.Commands.add('credentials', () => {
  return cy.env(['E2E_USERNAME', 'E2E_PASSWORD']).then(({ E2E_USERNAME, E2E_PASSWORD }) => {
    return {
      adminUsername: E2E_USERNAME ?? Cypress.expose('adminUsername'),
      adminPassword: E2E_PASSWORD ?? Cypress.expose('adminPassword'),
      username: E2E_USERNAME ?? Cypress.expose('username'),
      password: E2E_PASSWORD ?? Cypress.expose('password'),
    };
  });
});
TS
}

run() {
  local dir=$1 out rc=0 stripper=${2:-$STRIPPER}
  out=$(
    HC_CYPRESS_CONFIG="$dir/cypress.config.ts" \
      HC_CYPRESS_COMMANDS="$dir/commands.ts" \
      HC_STRIPPER="$stripper" \
      "$CHECK" 2>&1
  ) || rc=$?
  LAST_OUT=$out
  return "$rc"
}

drive() {
  local label=$1 want=$2 dir=$3 rc=0 stripper=${4:-$STRIPPER}
  run "$dir" "$stripper" || rc=$?
  report "$label" "$want" "$rc" "$LAST_OUT"
}

printf '\n== e2e-credentials-are-not-committed.sh, driven against broken states ==\n\n'

# --- 1. The control that makes every refusal below mean something -----------------------------
control
D="$WORK/c1"
build "$D"
drive '1  a correct config passes' 0 "$D"

# --- 2. THE ITEM ITSELF: the generated literals, which `--force` restores ----------------------
refusal
D="$WORK/c2"
build "$D"
cat >"$D/cypress.config.ts" <<'TS'
import { defineConfig } from 'cypress';

export default defineConfig({
  retries: 2,
  allowCypressEnv: false,
  expose: {
    adminUsername: 'admin',
    adminPassword: 'admin',
    username: 'admin',
    password: 'admin',
    authenticationUrl: '/api/authenticate',
  },
});
TS
drive '2  NEW-80 itself: all four as bare literals, as the generator writes them' 1 "$D"

# --- 3. The literals back with the prose intact — a SURVIVOR of the stripper mutation, and it says so
#  The fixture's block comment carries all four bindings verbatim and here the four real lines go back
#  to literals. This case was written to distinguish a stripped check from an unstripped one and **no
#  longer does**: review's every-binding-line fix means the literal line fails whether or not the
#  comment's copy is also matched, so M1 leaves it red. It is kept because it is the shape `--force`
#  plus a surviving comment actually produces, and because two independent rules refusing it is the
#  point. **The stripper's own subjects are the ABSENCE cases — 6 and 22, where the only matching line
#  is prose — and case 12, whose assertion reads the whole file.** Measured, not reasoned.
refusal
D="$WORK/c3"
build "$D"
sed -i "s@^    adminUsername: process.env.*@    adminUsername: 'admin',@" "$D/cypress.config.ts"
sed -i "s@^    adminPassword: process.env.*@    adminPassword: 'admin',@" "$D/cypress.config.ts"
sed -i "s@^    username: process.env.*@    username: 'admin',@" "$D/cypress.config.ts"
sed -i "s@^    password: process.env.*@    password: 'admin',@" "$D/cypress.config.ts"
drive '3  the four bindings present ONLY in a block comment' 1 "$D"

# --- 4. ONE of the four regressed. The others still read the environment. ----------------------
#  The item named two lines and there are four; a check asserting "the file mentions HC_E2E_" would
#  pass this. It is per-field for that reason.
refusal
D="$WORK/c4"
build "$D"
sed -i "s@^    adminPassword: process.env.*@    adminPassword: 'admin',@" "$D/cypress.config.ts"
drive '4  one field of four falls back to a literal' 1 "$D"

# --- 5. The username half regressed, which is the half somebody might think harmless -----------
refusal
D="$WORK/c5"
build "$D"
sed -i "s@^    username: process.env.*@    username: 'admin',@" "$D/cypress.config.ts"
drive '5  a USERNAME field falls back to a literal' 1 "$D"

# --- 6. A field the specs consume that the config does not declare ------------------------------
#  `Cypress.expose('password')` then yields `undefined` and the suite logs in with nothing.
refusal
D="$WORK/c6"
build "$D"
sed -i "/^    password: process.env/d" "$D/cypress.config.ts"
drive '6  a derived field is absent from the config entirely' 1 "$D"

# --- 7. THE DERIVATION FLOOR. A renamed interface leaves nothing to check. ----------------------
#  Without the floor this prints `ok` having compared nothing, which is this family's whole failure
#  mode: a for-each over an empty list is vacuously satisfied.
refusal
D="$WORK/c7"
build "$D"
sed -i 's@export interface Credentials {@export interface LoginDetails {@' "$D/commands.ts"
drive '7  `interface Credentials` renamed: the derivation finds nothing' 1 "$D"

# --- 8. The derivation is bounded by the interface's own brace ----------------------------------
#  A one-field interface is below the floor even with other interfaces in the file, so the bound is
#  asserted rather than assumed: were it unbounded, the second interface's fields would be counted.
refusal
D="$WORK/c8"
build "$D"
cat >"$D/commands.ts" <<'TS'
export interface Credentials {
  adminUsername: string;
}

export interface Unrelated {
  adminPassword: string;
  username: string;
  password: string;
}
TS
drive '8  fields after the interface closes are not counted' 1 "$D"

# --- 9. The stripper is missing: refuse rather than read raw text -------------------------------
refusal
D="$WORK/c9"
build "$D"
drive '9  the stripper is absent' 1 "$D" "$WORK/no-such-stripper.awk"

# --- 10. The config is gone --------------------------------------------------------------------
refusal
D="$WORK/c10"
build "$D"
rm -f "$D/cypress.config.ts"
drive '10 no cypress.config.ts at all' 1 "$D"

# --- 11. The specs' support file is gone: no subject to derive from -----------------------------
refusal
D="$WORK/c11"
build "$D"
rm -f "$D/commands.ts"
drive '11 no commands.ts, so the field set cannot be derived' 1 "$D"

# --- 12. `allowCypressEnv` flipped — a widening argued nowhere ----------------------------------
refusal
D="$WORK/c12"
build "$D"
sed -i 's@allowCypressEnv: false,@allowCypressEnv: true,@' "$D/cypress.config.ts"
drive '12 allowCypressEnv flipped to true' 1 "$D"

# --- 13. CONTROL: a FIFTH credential field, bound from the environment --------------------------
#  The field set is derived, so a generator adding one is checked the moment it exists — and a
#  correctly bound fifth field must stay GREEN or the derivation would be refusing correct code.
#
#  ⚠ `add_field` adds it to BOTH shapes the check derives from, because a field declared in
#  `interface Credentials` that the `credentials` command never assigns DOES NOT COMPILE — the command
#  returns `Credentials`. The first version of this case added it to the interface alone and was red on
#  the cross-check; the fixture was illegal TypeScript, not the check wrong. D98 §4b's rule: a fixture
#  for a text guard must still be legal in the language the text is written in, or its coverage story
#  describes a state the compiler forbids.
add_field() {
  local dir=$1 field=$2
  sed -i "s@^  password: string;@  password: string;\n  $field: string;@" "$dir/commands.ts"
  sed -i "s@^      password: E2E_PASSWORD ?? Cypress.expose('password'),@      password: E2E_PASSWORD ?? Cypress.expose('password'),\n      $field: Cypress.expose('$field'),@" "$dir/commands.ts"
}

control
D="$WORK/c13"
build "$D"
add_field "$D" brokerageUsername
sed -i "s@^    authenticationUrl@    brokerageUsername: process.env.HC_E2E_BROKERAGE_USERNAME ?? 'brokerage',\n    authenticationUrl@" "$D/cypress.config.ts"
drive '13 control: a fifth derived field, correctly bound, passes' 0 "$D"

# --- 14. A fifth field the config does not bind from the environment ----------------------------
refusal
D="$WORK/c14"
build "$D"
add_field "$D" brokeragePassword
sed -i "s@^    authenticationUrl@    brokeragePassword: 'brokerage',\n    authenticationUrl@" "$D/cypress.config.ts"
drive '14 a fifth derived field committed as a literal' 1 "$D"

# --- 15. CONTROL: the non-credential keys may stay literals ------------------------------------
#  `authenticationUrl` and `jwtStorageName` are a path and a storage key, not credentials, and they
#  are NOT in `interface Credentials`. A check widened to "no quoted literal in the expose block"
#  would be red here, on a correct file.
control
D="$WORK/c15"
build "$D"
drive '15 control: authenticationUrl and jwtStorageName stay literals' 0 "$D"

# --- 16. CONTROL: the fallback's VALUE is not this check's business -----------------------------
#  Stated because it is a limit somebody will otherwise mistake for coverage: what is held is that a
#  real value can arrive from outside, not which string the fallback carries.
control
D="$WORK/c16"
build "$D"
sed -i "s@?? 'admin',@?? 'changed-default',@g" "$D/cypress.config.ts"
drive '16 control: a different fallback value is not a finding' 0 "$D"

# --- 17. THE OPTIONAL MARKER. The check's own fail-open, found at review. ----------------------
#  `adminUsername?: string;` is not matched by `<name>:`, so the first derivation returned THREE
#  fields, printed an honest count, sailed past the `>= 2` floor and exited 0 with a literal in the
#  config. Under-derivation in the mechanism this check calls its answer to NEW-15.
#
#  ⚠ TWO things keep it red now and a mutation of either alone is not enough: `\??` in the field
#  pattern, and the SECOND derivation it is compared against. Dropping `\??` alone leaves this case red
#  — via the cross-check — which is the designed redundancy and is measured as M4/M5.
refusal
D="$WORK/c17"
build "$D"
sed -i 's@^  adminUsername: string;@  adminUsername?: string;@' "$D/commands.ts"
sed -i "s@^    adminUsername: process.env.*@    adminUsername: 'admin',@" "$D/cypress.config.ts"
drive '17 an OPTIONAL field in the interface, with that field a literal' 1 "$D"

# --- 18. A DUPLICATE KEY: the environment read first, the literal second ------------------------
#  JavaScript obeys the LAST key and `grep -m1` read the first, so this exited 0 with the literal in
#  force. Not a regeneration shape — `--force` rewrites the whole block, which is case 2 — but a merge
#  resolution or a hand-edit. ESLint is no backstop: it does not lint this file at all (D108 §6), so
#  `no-dupe-keys` never runs over it.
refusal
D="$WORK/c18"
build "$D"
sed -i "s@^    password: process.env.HC_E2E_PASSWORD ?? 'admin',@    password: process.env.HC_E2E_PASSWORD ?? 'admin',\n    password: 'admin',@" "$D/cypress.config.ts"
drive '18 a duplicate binding whose LAST copy is a literal' 1 "$D"

# --- 19. CORRECT BINDINGS IN THE WRONG BLOCK ---------------------------------------------------
#  `Cypress.expose(…)` reads the `expose` object and nothing else. Four correct bindings in a
#  neighbouring `env: { }` satisfied a whole-file grep while every credential in `expose` stayed a
#  literal — exit 0, "4 checked". The check's own comment claimed it read the field's line "in the
#  `expose` block"; the code did not implement it. A comment is not a scope.
refusal
D="$WORK/c19"
build "$D"
cat >"$D/cypress.config.ts" <<'TS'
import { defineConfig } from 'cypress';

export default defineConfig({
  allowCypressEnv: false,
  env: {
    adminUsername: process.env.HC_E2E_ADMIN_USERNAME ?? 'admin',
    adminPassword: process.env.HC_E2E_ADMIN_PASSWORD ?? 'admin',
    username: process.env.HC_E2E_USERNAME ?? 'admin',
    password: process.env.HC_E2E_PASSWORD ?? 'admin',
  },
  expose: {
    adminUsername: 'admin',
    adminPassword: 'admin',
    username: 'admin',
    password: 'admin',
  },
});
TS
drive '19 the environment reads are in `env`, and `expose` is all literals' 1 "$D"

# --- 20. THE TWO DERIVATIONS DISAGREE ----------------------------------------------------------
#  The interface keeps the field and the `credentials` body stops exposing it. Neither derivation can
#  then be trusted, so the check refuses rather than taking the smaller one — which is the move that
#  made the optional marker invisible.
refusal
D="$WORK/c20"
build "$D"
sed -i "/adminPassword: E2E_PASSWORD ?? Cypress.expose('adminPassword')/d" "$D/commands.ts"
drive '20 a field in `interface Credentials` that the command never exposes' 1 "$D"

# --- 21. CONTROL: a duplicate binding where BOTH copies read the environment ---------------------
#  Duplicates are not what is banned — committed values are. A check refusing this would be red on a
#  file that is merely untidy.
control
D="$WORK/c21"
build "$D"
sed -i "s@^    username: process.env.HC_E2E_USERNAME ?? 'admin',@    username: process.env.HC_E2E_USERNAME ?? 'admin',\n    username: process.env.HC_E2E_USERNAME ?? 'admin',@" "$D/cypress.config.ts"
drive '21 control: a duplicate binding, both copies from the environment' 0 "$D"

# --- 22. THE SCOPE'S OWN CASE: bound correctly in `env`, ABSENT from `expose` --------------------
#  Case 19 is refused by a whole-file read as well, because every matching line must carry the prefix
#  and `expose`'s literal lines still fail — so 19 does not discriminate the scoping. This does: the
#  field is bound correctly in `env` and simply is not in `expose` at all, so a whole-file read finds
#  the `env` line and reports ok while `Cypress.expose('password')` yields `undefined`.
refusal
D="$WORK/c22"
build "$D"
sed -i "/^    password: process.env/d" "$D/cypress.config.ts"
sed -i "s@^  expose: {@  env: {\n    password: process.env.HC_E2E_PASSWORD ?? 'admin',\n  },\n  expose: {@" "$D/cypress.config.ts"
drive '22 the field is in `env` and absent from `expose`' 1 "$D"

# ---------------------------------------------------------------------------------------------
printf '\n'
if ((refusals + controls != seen)); then
  printf '✗ %d case(s) ran but %d classified themselves (%d refusal + %d control) — classify every case\n' \
    "$seen" "$((refusals + controls))" "$refusals" "$controls" >&2
  exit 1
fi
printf 'e2e-credentials-are-not-committed-test: %d case(s) — %d refusal(s), %d control(s); %d passed, %d failed\n' \
  "$seen" "$refusals" "$controls" "$pass" "$fail"
((fail == 0)) || exit 1
printf '✓ the check refuses every state it exists to refuse, and passes every correct one\n'
