#!/usr/bin/env bash
# ==============================================================================
#  The e2e suite's credentials must come from the environment — NEW-80, decisions.md D108.
#
#  `web/cypress.config.ts` is a GENERATED file (D101), and what the generator writes is four bare
#  credential literals in its `expose` block:
#
#      adminUsername: 'admin',  adminPassword: 'admin',  username: 'admin',  password: 'admin',
#
#  That is not a leak and this check is not about exposure. It is about a public repository stating a
#  credential as a value, where a reader has to RECONSTRUCT three facts before concluding it is safe:
#  the `dev`/`test` rule this repository publishes anyway, D61's refusal to create an administrator
#  under `prod` without `HC_GATEWAY_ADMIN_PASSWORD`, and that nothing runs Cypress. The estate's
#  standing rule is the same one inverted — a committed default may exist only where it cannot be the
#  value a running estate uses — so each credential reads `process.env.HC_E2E_*` with the published
#  development value as its documented fallback.
#
#  WHY A CHECK AT ALL, FOR A FILE NOTHING EXECUTES. Because `jhipster --force` restores the literals
#  and NOTHING ELSE WOULD NOTICE: no test imports this file, `build.yml` sets
#  `CYPRESS_INSTALL_BINARY: '0'` so the binary is not even installed, and the regenerated file passes
#  every gate this repository has. ⚠ ESLint does not merely tolerate it, it IGNORES it —
#  `npx eslint cypress.config.ts` answers "File ignored because no matching configuration was supplied"
#  and exits 0, because a root-level `.ts` matches none of `eslint.config.ts`'s `.ts` globs — and no
#  `tsc` reads it either, so `prettier:check` is the only gate that parses this file at all. It is the
#  regeneration table's silent-loss shape with no suite behind it, so a text check is the only guard
#  available.
#
#  THE KEY SET IS DERIVED FROM THE SPECS' OWN CONTRACT AND NEVER LISTED HERE, which is this
#  repository's rule after NEW-15: `commands.ts` declares `interface Credentials { … }`, one field per
#  credential the suite consumes, and a generator that adds a fifth must answer for it in the pull
#  request that adds it. A list of four in this file could not see that, and every enumerated list in
#  this repository's CI has gone stale or failed open.
#
#  ⚠ A FLOOR IS NOT ENOUGH, AND THAT WAS THIS CHECK'S OWN FAIL-OPEN. The first version matched
#  `<name>:` without TypeScript's optional marker, so `adminUsername?: string;` derived THREE fields,
#  printed `derived 3 field(s)` and exited 0 with a committed literal in the config — an honest count
#  compared against nothing, past a `>= 2` floor. Under-derivation in the one mechanism this header
#  calls its answer to NEW-15, whose root cause was that no test could see an omission from a list.
#
#  So the set is derived TWICE, from two unrelated shapes in the same file, and the two must be EQUAL:
#  the `interface Credentials` field names, and the `Cypress.expose('…')` names inside the
#  `credentials` command's own body. A blindness in one regex cannot be in the other — the optional
#  marker is invisible to the first and irrelevant to the second — so the count is now compared against
#  something. TypeScript makes the equality a real invariant rather than a convention: the command
#  returns `Credentials`, so a field in the interface that the body never assigns does not compile.
#  Fewer than two derived fields, an unreadable config, or a missing stripper all refuse before any
#  assertion runs.
#
#  ⚠ IT MATCHES TEXT, SO COMMENTS ARE STRIPPED FIRST. The subject's own comment block names all four
#  variables; unstripped, this check would be satisfied by the paragraph explaining it, which is the
#  fail-open `strip-comments.awk` exists for. The Java stripper is correct for both files here: `//`
#  and `/* */` are TypeScript's comments too, and neither file contains a template literal — the one
#  TypeScript construct that stripper does not know.
#
#  WHAT IT DOES NOT REACH, stated rather than left as an empty column:
#    * It does not run Cypress and cannot. Nothing in this estate does.
#    * It does not judge the FALLBACK value. `?? 'admin'` and `?? 'hunter2'` are the same to it — what
#      it holds is that a real value can arrive from outside, and the fallback's provenance is prose.
#    * It reads the `expose` block as lines, not as a parsed object, so each credential must stay on
#      ONE line. A prettier-wrapped argument is refused: fail-closed, exactly as D60's `.zoneId(` is.
#    * The second derivation is bounded by the `credentials` command's own body, found from
#      `Cypress.Commands.add('credentials'` to the first line beginning `});`. A restructuring that
#      moves those `Cypress.expose` calls elsewhere is REFUSED rather than followed — fail-closed, and
#      the refusal names both sets so the repair is to teach this check, not to delete it.
#    * `allowCypressEnv` is asserted to be `false` because this file's own reasoning depends on it
#      being untouched, NOT because it blocks anything — see D108 §4.
#
#  Run it directly; it prints what it derived and what it checked, either way.
#      ./.github/checks/e2e-credentials-are-not-committed.sh
# ==============================================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

CONFIG="${HC_CYPRESS_CONFIG:-web/cypress.config.ts}"
COMMANDS="${HC_CYPRESS_COMMANDS:-web/src/test/javascript/cypress/support/commands.ts}"
STRIPPER="${HC_STRIPPER:-.github/checks/strip-comments.awk}"
PREFIX="${HC_E2E_VAR_PREFIX:-process.env.HC_E2E_}"

fail=0
note() { printf '  %s\n' "$*"; }
refuse() {
  printf '✗ %s\n' "$*" >&2
  fail=1
}

# Every text-matching check in this repository guards that the stripper EXISTS, and that guard is
# measured rather than decorative: absent it, two checks whose subject is a silent gap printed `ok`
# having read nothing (CLAUDE.md, "Each caller guards that the file EXISTS").
if [[ ! -f "$STRIPPER" ]]; then
  printf '✗ %s is missing — this check cannot strip comments and would be satisfied by its own prose\n' "$STRIPPER" >&2
  exit 1
fi
[[ -f "$CONFIG" ]] || {
  printf '✗ no Cypress config at %s — nothing to check, and NEW-80 cannot be said to hold\n' "$CONFIG" >&2
  exit 1
}
[[ -f "$COMMANDS" ]] || {
  printf '✗ no %s — the credential field set is DERIVED from its `interface Credentials`, so without it this check has no subject\n' "$COMMANDS" >&2
  exit 1
}

stripped_config=$(awk -f "$STRIPPER" "$CONFIG")
stripped_commands=$(awk -f "$STRIPPER" "$COMMANDS")

# ---------------------------------------------------------------------------------------------
# 1a. Derive the credential fields from `interface Credentials { … }` in the specs' support file.
#     Brace-bounded, so an interface declared after it cannot contribute fields.
#
#     `\??` is not decoration: without it `adminUsername?: string;` derives nothing for that field and
#     the check exits 0 over a committed literal. Measured as mutant M4.
# ---------------------------------------------------------------------------------------------
fields=$(
  awk '
    /interface[ \t]+Credentials[ \t]*\{/ { inside = 1; next }
    inside && /\}/                      { exit }
    inside && match($0, /[A-Za-z_][A-Za-z0-9_]*\??[ \t]*:/) {
      f = substr($0, RSTART, RLENGTH)
      sub(/\??[ \t]*:$/, "", f)
      print f
    }
  ' <<<"$stripped_commands" | sort -u
)

derived=0
if [[ -n "$fields" ]]; then derived=$(wc -l <<<"$fields"); fi

if ((derived < 2)); then
  refuse "derived $derived credential field(s) from \`interface Credentials\` in $COMMANDS, expected at least 2 — the derivation found nothing, which is how this check passes over a renamed interface"
  printf 'e2e-credentials-are-not-committed: REFUSED (derivation floor)\n' >&2
  exit 1
fi

# ---------------------------------------------------------------------------------------------
# 1b. THE SAME SET, DERIVED AGAIN FROM AN UNRELATED SHAPE, and the two must agree. A count compared
#     against nothing is what let the optional marker through; this is what it is compared against.
#     Bounded by the `credentials` command's own body, because `Cypress.expose('jwtStorageName')` and
#     `Cypress.expose('authenticationUrl')` appear elsewhere in this file and are not credentials.
# ---------------------------------------------------------------------------------------------
exposed=$(
  awk "
    /Cypress\\.Commands\\.add\\('credentials'/ { inside = 1 }
    inside && /^\\}\\);/                       { exit }
    inside {
      s = \$0
      while (match(s, /Cypress\\.expose\\('[A-Za-z_][A-Za-z0-9_]*'\\)/)) {
        seg = substr(s, RSTART, RLENGTH)
        sub(/^Cypress\\.expose\\('/, \"\", seg)
        sub(/'\\)\$/, \"\", seg)
        print seg
        s = substr(s, RSTART + RLENGTH)
      }
    }
  " <<<"$stripped_commands" | sort -u
)

if [[ "$fields" != "$exposed" ]]; then
  refuse "the two derivations of the credential set disagree, so neither can be trusted: \`interface Credentials\` gives [$(tr '\n' ' ' <<<"$fields")] and the \`credentials\` command's own \`Cypress.expose(…)\` calls give [$(tr '\n' ' ' <<<"$exposed")]. One of them is missing a field this suite uses — teach this check rather than removing it."
  printf 'e2e-credentials-are-not-committed: REFUSED (the two derivations disagree)\n' >&2
  exit 1
fi

# `has_in` rather than a pipeline into `grep -q`: a match makes `grep -q` exit at once, its producer
# takes SIGPIPE and dies 141, and under `pipefail` the PIPELINE's status is the producer's — so a
# match arrives as a failure and the branch never fires. Eleven checks here had that defect and three
# were fail-open (decisions.md D98, backlog NEW-71). A herestring is not a pipeline at all.
has_in() {
  local text=$1 pattern=$2 rc=0
  grep -qF -- "$pattern" <<<"$text" || rc=$?
  case $rc in
    0) return 0 ;;
    1) return 1 ;;
    *)
      printf '✗ grep answered %s — neither match nor no-match. Refusing rather than guessing.\n' "$rc" >&2
      exit 1
      ;;
  esac
}

# ---------------------------------------------------------------------------------------------
# 2a. THE `expose` BLOCK ITSELF, brace-bounded, because `Cypress.expose(…)` reads that object and
#     nothing else. The first version grepped the whole stripped file while its own comment claimed
#     otherwise, so four correct bindings in a neighbouring `env: { }` block satisfied it while every
#     credential in `expose` stayed a literal — exit 0, "4 checked". A comment is not a scope.
# ---------------------------------------------------------------------------------------------
expose_block=$(
  awk '
    !inside && /(^|[^A-Za-z0-9_])expose[ \t]*:[ \t]*\{/ { inside = 1; depth = 0 }
    inside {
      print
      n = length($0)
      for (i = 1; i <= n; i++) {
        c = substr($0, i, 1)
        if (c == "{") depth++
        if (c == "}") { depth--; if (depth == 0) exit }
      }
    }
  ' <<<"$stripped_config"
)

if [[ -z "$expose_block" ]]; then
  refuse "$CONFIG has no \`expose: { … }\` block — \`Cypress.expose(…)\` reads that object, so every credential the specs ask for would be \`undefined\`"
  printf 'e2e-credentials-are-not-committed: REFUSED (no expose block)\n' >&2
  exit 1
fi

# ---------------------------------------------------------------------------------------------
# 2b. Every derived field must be bound from the environment — on EVERY line that binds it.
#
#     ⚠ NOT the first. `grep -m1` reads the first binding and JavaScript obeys the LAST, so a
#     duplicate key — a merge resolution or a hand-edit, not a regeneration — put
#     `password: process.env.…` above `password: 'admin'` and exited 0, with the literal in force. And
#     ESLint is NO backstop here: it does not lint this file at all (D108 §6), so `no-dupe-keys`
#     never runs over it.
# ---------------------------------------------------------------------------------------------
checked=0
while IFS= read -r field; do
  [[ -n "$field" ]] || continue

  # Every binding line for this field. The status is read rather than discarded, so "no such key"
  # (1) and "grep could not answer" (anything else) stay distinguishable — and this is a plain
  # redirection, never a pipeline into something that exits early (D98, NEW-71).
  lines='' rc=0
  lines=$(grep -E "^[[:space:]]*${field}[[:space:]]*:" <<<"$expose_block") || rc=$?
  if ((rc == 1)) || [[ -z "$lines" ]]; then
    refuse "$CONFIG's \`expose\` block declares no \`$field:\` — the specs read \`Cypress.expose('$field')\` for it, so the suite would get \`undefined\`"
    checked=$((checked + 1))
    continue
  elif ((rc > 1)); then
    printf '✗ grep answered %s reading %s — refusing rather than guessing\n' "$rc" "$CONFIG" >&2
    exit 1
  fi

  bindings=$(wc -l <<<"$lines")
  bad=0
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    if ! has_in "$line" "$PREFIX"; then
      refuse "$CONFIG binds \`$field\` without reading $PREFIX…: ${line#"${line%%[![:space:]]*}"} — this is NEW-80. A credential in a public repository states where it comes from, not what it is; see decisions.md D108."
      bad=1
    fi
  done <<<"$lines"

  if ((bad == 0)); then
    if ((bindings > 1)); then
      note "ok   $field  <-  ${PREFIX}… on all $bindings binding(s) — the LAST one is the one in force"
    else
      note "ok   $field  <-  ${PREFIX}… with a fallback on the same line"
    fi
  fi
  checked=$((checked + 1))
done <<<"$fields"

# ---------------------------------------------------------------------------------------------
# 3. `allowCypressEnv: false` is unchanged. Not a control — the file's own reasoning cites it, and
#    flipping it re-enables a browser API Cypress has deprecated and intends to remove (D108 §4).
# ---------------------------------------------------------------------------------------------
if has_in "$stripped_config" 'allowCypressEnv: false'; then
  note "ok   allowCypressEnv: false, as generated"
else
  refuse "$CONFIG no longer says \`allowCypressEnv: false\` — that widens a deprecated API across every spec and is argued nowhere. Argue it in a decision first (D108 §4)."
fi

printf 'e2e-credentials-are-not-committed: derived %d field(s) from %s, checked %d in %s\n' \
  "$derived" "$COMMANDS" "$checked" "$CONFIG"

if ((fail)); then
  printf '✗ an e2e credential is committed as a value rather than read from the environment — see decisions.md D108\n' >&2
  exit 1
fi
printf '✓ every e2e credential comes from the environment, with a documented development fallback\n'
