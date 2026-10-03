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
#  `CYPRESS_INSTALL_BINARY: '0'` so the binary is not even installed, and the regenerated file lints,
#  formats and builds exactly as well as this one. It is the regeneration table's silent-loss shape
#  with no suite behind it, so a text check is the only guard available.
#
#  THE KEY SET IS DERIVED FROM THE SPECS' OWN CONTRACT AND NEVER LISTED HERE, which is this
#  repository's rule after NEW-15: `commands.ts` declares `interface Credentials { … }`, one field per
#  credential the suite consumes, and a generator that adds a fifth must answer for it in the pull
#  request that adds it. A list of four in this file could not see that, and every enumerated list in
#  this repository's CI has gone stale or failed open.
#
#  THE DERIVATION FLOOR IS THE PART THAT MATTERS. A for-each over nothing prints `ok` having compared
#  nothing — the shape that has produced fail-opens here repeatedly — so fewer than two derived fields,
#  an unreadable config, or a missing stripper all refuse before any assertion runs.
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
# 1. Derive the credential fields from `interface Credentials { … }` in the specs' support file.
#    Brace-bounded, so an interface declared after it cannot contribute fields.
# ---------------------------------------------------------------------------------------------
fields=$(
  awk '
    /interface[ \t]+Credentials[ \t]*\{/ { inside = 1; next }
    inside && /\}/                      { exit }
    inside && match($0, /[A-Za-z_][A-Za-z0-9_]*[ \t]*:/) {
      f = substr($0, RSTART, RLENGTH)
      sub(/[ \t]*:$/, "", f)
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
# 2. Every derived field must be bound from the environment, on its own line, in the config.
# ---------------------------------------------------------------------------------------------
checked=0
while IFS= read -r field; do
  [[ -n "$field" ]] || continue

  # The field's own line in the `expose` block. `grep -n` is not piped into anything that exits
  # early; its status is read, so "no such key" and "grep could not answer" stay distinguishable.
  line='' rc=0
  line=$(grep -m1 -E "^[[:space:]]*${field}:" <<<"$stripped_config") || rc=$?
  if ((rc == 1)) || [[ -z "$line" ]]; then
    refuse "$CONFIG declares no \`$field:\` — the specs read \`Cypress.expose('$field')\` for it, so the suite would get \`undefined\`"
    checked=$((checked + 1))
    continue
  elif ((rc > 1)); then
    printf '✗ grep answered %s reading %s — refusing rather than guessing\n' "$rc" "$CONFIG" >&2
    exit 1
  fi

  if has_in "$line" "$PREFIX"; then
    note "ok   $field  <-  ${PREFIX}… with a fallback on the same line"
  else
    refuse "$CONFIG binds \`$field\` without reading $PREFIX…: ${line#"${line%%[![:space:]]*}"} — this is NEW-80. A credential in a public repository states where it comes from, not what it is; see decisions.md D108."
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
