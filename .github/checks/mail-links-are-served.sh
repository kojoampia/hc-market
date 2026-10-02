#!/usr/bin/env bash
# ==============================================================================
#  Every link the mail templates compose must be a route the client serves — NEW-60, decisions.md D105.
#
#  THE DRIFT THIS CLOSES IS BETWEEN TWO PROJECTS THAT SHARE NO BUILD. The gateway's Thymeleaf
#  templates compose `${baseUrl}/account/activate?key=…` and `${baseUrl}/account/reset/finish?key=…`;
#  `web/` serves those paths from `app/account/account-lifecycle.routes.ts`. Nothing links the two:
#  the gateway has no idea a client exists, the client never reads a template, and a path renamed on
#  either side leaves BOTH suites green while the link in somebody's inbox lands on a 404 page.
#
#  That is not hypothetical — it is the item. For the estate's whole life the templates composed two
#  paths the client did not serve, and the symptom was a 401 nobody met because no estate can send
#  mail yet. The first person to receive one of these should not be the one who finds it again.
#
#  THE EXPECTED SET IS DERIVED FROM THE TEMPLATES AND NEVER LISTED HERE, which is this repository's
#  rule after NEW-15: a check holding a client against a hard-coded list of two cannot see a THIRD
#  template appearing, and every enumerated list in this repository's CI has gone stale or failed
#  open. The templates are the authority — they are what is actually sent — so the direction is
#  "for each path a template composes, the client must serve it" and not the reverse. A client route
#  with no template behind it is a screen somebody can reach by typing, which is nobody's defect.
#
#  THE POSITIVE CONTROL IS THE PART THAT MATTERS. A derivation that matches nothing satisfies a
#  for-each loop vacuously, which is exactly how this family fails: rename the templates' directory
#  and, without the floor below, this check prints `ok` having compared nothing. So it refuses a
#  derivation of fewer than two paths, and it refuses a routes file it could not read.
#
#  ⚠ IT MATCHES TEXT, SO COMMENTS ARE STRIPPED FIRST, and the routes file is full of prose naming
#  both paths — `account-lifecycle.routes.ts` quotes `${baseUrl}/account/activate?key=…` in its own
#  javadoc. Unstripped, this check would be satisfied by the paragraph explaining it, which is the
#  fail-open `strip-comments.awk` exists for and which has been the finding in nine of them. The
#  Java stripper is correct for this file: it understands `//` and `/* */`, and there is no template
#  literal in it (the one TypeScript construct that stripper does not know).
#
#  WHAT IT DOES NOT REACH, stated rather than left as an empty column:
#    * It does not render anything. That a route RESOLVES to a component is `app.routes.spec.ts`'s,
#      asked of the router; this is about the two strings agreeing across a project boundary.
#    * It reads the `account` prefix out of `app.routes.ts` rather than assuming it, but it cannot
#      see a nested `loadChildren` three levels down. Two levels is what this client has.
#    * A path composed at RUN time in the template (Thymeleaf concatenation) is invisible to it.
#      Neither template does that today, and the derivation floor is what would notice if the
#      literal form disappeared.
#
#  Run it directly; it prints what it checked and what it derived, either way.
#      ./.github/checks/mail-links-are-served.sh
# ==============================================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

MAIL_TEMPLATES="${HC_MAIL_TEMPLATES:-gateway/src/main/resources/templates/mail}"
ROUTES_FILE="${HC_ROUTES_FILE:-web/src/main/webapp/app/account/account-lifecycle.routes.ts}"
APP_ROUTES="${HC_APP_ROUTES:-web/src/main/webapp/app/app.routes.ts}"
STRIPPER="${HC_STRIPPER:-.github/checks/strip-comments.awk}"

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

[[ -d "$MAIL_TEMPLATES" ]] || {
  printf '✗ no mail templates at %s — nothing to derive the expected routes from\n' "$MAIL_TEMPLATES" >&2
  exit 1
}
[[ -f "$ROUTES_FILE" ]] || {
  printf '✗ no account routes at %s — NEW-60 is reopened: the mail links are served by nothing\n' "$ROUTES_FILE" >&2
  exit 1
}
[[ -f "$APP_ROUTES" ]] || {
  printf '✗ no %s\n' "$APP_ROUTES" >&2
  exit 1
}

# ---------------------------------------------------------------------------------------------
# 1. Derive the links. `${baseUrl}/<path>?<param>=` is the shape both templates use; the path is
#    what the client must serve and the param is what the screen must read.
# ---------------------------------------------------------------------------------------------
links=$(grep -rhoE '\$\{baseUrl\}/[A-Za-z0-9/_-]+\?[A-Za-z0-9_]+=' "$MAIL_TEMPLATES" | sort -u || true)

derived=0
if [[ -n "$links" ]]; then derived=$(printf '%s\n' "$links" | wc -l); fi

if ((derived < 2)); then
  refuse "derived $derived link(s) from $MAIL_TEMPLATES, expected at least 2 — the derivation found nothing to check, which is how this check passes over a renamed template"
  printf 'mail-links-are-served: REFUSED (derivation floor)\n' >&2
  exit 1
fi

# ---------------------------------------------------------------------------------------------
# 2. The client's routes, read off STRIPPED text so the javadoc quoting both paths cannot satisfy it.
# ---------------------------------------------------------------------------------------------
stripped_routes=$(awk -f "$STRIPPER" "$ROUTES_FILE")
stripped_app=$(awk -f "$STRIPPER" "$APP_ROUTES")

# The prefix the lazy child table hangs under, read rather than assumed.
prefix=$(printf '%s\n' "$stripped_app" |
  grep -oE "path: '[A-Za-z0-9_-]+',?[[:space:]]*$|path: '[A-Za-z0-9_-]+'" |
  grep -oE "'[A-Za-z0-9_-]+'" | tr -d "'" | grep -x 'account' || true)
if [[ "$prefix" != 'account' ]]; then
  refuse "$APP_ROUTES declares no top-level \`path: 'account'\` — whatever $ROUTES_FILE says, nothing mounts it"
fi

# `has_in` rather than a pipeline into `grep -q`: a match makes `grep -q` exit at once, its producer
# takes SIGPIPE and dies 141, and under `pipefail` the PIPELINE's status is the producer's — so a
# match arrives as a failure and the branch never fires. Eleven checks here had that defect and
# three were fail-open (decisions.md D98, backlog NEW-71). This is the herestring spelling, which is
# not a pipeline at all.
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
# 3. For each derived link: the client serves the path, and the screen reads the parameter.
# ---------------------------------------------------------------------------------------------
checked=0
while IFS= read -r link; do
  [[ -n "$link" ]] || continue
  # `${baseUrl}/account/activate?key=`  ->  path `account/activate`, param `key`
  path=${link#'${baseUrl}/'}
  param=${path#*\?}
  param=${param%=}
  path=${path%%\?*}

  if [[ "$path" != "$prefix"/* ]]; then
    refuse "the mail composes /$path, which is outside the \`$prefix\` prefix this check knows how to mount — add a route table for it and teach this check where it lives"
    continue
  fi
  child=${path#"$prefix"/}

  if has_in "$stripped_routes" "path: '$child'"; then
    note "ok   /$path  ->  path: '$child' in $(basename "$ROUTES_FILE")"
  else
    refuse "the mail composes /$path but $ROUTES_FILE declares no \`path: '$child'\` — the link lands on the client's 404 page. This is NEW-60."
  fi

  # The parameter is read by the screen behind that route, not by the route table. Which file that
  # is comes from THAT ROUTE'S OWN `loadComponent` — the first `import('./…')` at or after its
  # `path:` line — so a renamed component is followed rather than guessed, and the parameter must be
  # read through `paramValue`, because a literal `paramMap.get('key')` is an offender to
  # `endpoint-construction.spec.ts`.
  #
  # ⚠ NOT by position in the file. The first version took the Nth import for the Nth derived link,
  # which is correct only while `sort -u`'s order happens to equal the declaration order — a
  # coincidence, and this repository's own rule is not to rest on one. **Measured** by driving that
  # version through `mail-links-are-served-test.sh`'s `HC_CHECK=`: with THREE templates it is RED on
  # a correct tree, because `sort -u` gives activate / email/confirm / reset/finish while the routes
  # declare activate / reset/finish / email/confirm — so it looks for `?token=` in `new-password.ts`.
  # With only two routes it is green either way, which is why the two-route reorder case proves
  # nothing and the three-route control is the discriminator.
  component=$(awk -v want="path: '$child'" '
    index($0, want) { found = 1 }
    found && match($0, /import\(\x27\.\/[A-Za-z0-9\/_-]+\x27\)/) {
      seg = substr($0, RSTART, RLENGTH)
      sub(/^import\(\x27\.\//, "", seg)
      sub(/\x27\)$/, "", seg)
      print seg
      exit
    }
  ' <<<"$stripped_routes")
  screen="$(dirname "$ROUTES_FILE")/${component}.ts"
  if [[ -f "$screen" ]]; then
    stripped_screen=$(awk -f "$STRIPPER" "$screen")
    if has_in "$stripped_screen" "paramValue(params, '$param')"; then
      note "ok   ?$param=  ->  paramValue(params, '$param') in $component.ts"
    else
      refuse "$screen never reads \`paramValue(params, '$param')\` — the mail sends ?$param= and the screen would make a request without it"
    fi
  else
    refuse "cannot find the component behind /$path (looked for $screen) — this check cannot tell whether ?$param= is read"
  fi

  checked=$((checked + 1))
done <<<"$links"

printf 'mail-links-are-served: derived %d link(s) from %s, checked %d against %s\n' \
  "$derived" "$MAIL_TEMPLATES" "$checked" "$ROUTES_FILE"

if ((fail)); then
  printf '✗ a link the estate sends is not served by the client — see decisions.md D105\n' >&2
  exit 1
fi
printf '✓ every link the mail composes is a route the client serves\n'
