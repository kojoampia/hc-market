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

# ⚠ ONE ROUTE OBJECT AT A TIME, BY BRACE DEPTH — never "the first import at or after the `path:`
# line", which is what this did until review and which is a reachable fail-open.
#
# Key order inside a JavaScript object literal is meaningless to Angular, so
# `{ loadComponent: …, path: 'activate' }` is a NO-OP edit and an entirely plausible one. Under the
# line-scanning version the search then started BELOW the `path:` line, ran past this object's
# closing brace, and returned the NEXT route's component — measured on the real tree, which reported
# `?key= -> paramValue(params, 'key') in new-password/new-password.ts` for BOTH links and exited 0.
# Both screens happen to read a parameter called `key`, so the wrong file was still green; combine
# it with one screen using a literal `params.get('key')` — the exact defect case 9 refuses — and the
# check printed its success line about a file it had not examined.
#
# This walks characters and accumulates each depth-1 object whole, so an object's own braces bound
# the search and key order cannot matter. Nested objects (`data: { authorities: [...] }` in
# `app.routes.ts`) are inside their parent's buffer rather than units of their own, which is what
# makes "depth-1 object" mean "top-level route" for these two files.
ROUTE_OBJECTS_AWK='
BEGIN { depth = 0; buf = "" }
{
  line = $0
  n = length(line)
  for (i = 1; i <= n; i++) {
    c = substr(line, i, 1)
    if (c == "{") { depth++; if (depth == 1) buf = "" }
    if (depth >= 1) buf = buf c
    if (c == "}") {
      depth--
      if (depth == 0) {
        path = ""; imp = ""
        if (match(buf, "path:[ \t]*" q "[A-Za-z0-9/_-]*" q)) {
          seg = substr(buf, RSTART, RLENGTH)
          sub("^path:[ \t]*" q, "", seg); sub(q "$", "", seg)
          path = seg
        }
        if (match(buf, "import\\(" q "\\./[A-Za-z0-9/_.-]+" q "\\)")) {
          seg = substr(buf, RSTART, RLENGTH)
          sub("^import\\(" q "\\./", "", seg); sub(q "\\)$", "", seg)
          imp = seg
        }
        if (path != "" || imp != "") print path "\t" imp
        buf = ""
      }
    }
  }
  if (depth >= 1) buf = buf "\n"
}'

route_objects() { awk -v q="'" "$ROUTE_OBJECTS_AWK" <<<"$1"; }

# --- 2a. WHICH top-level route mounts the file this check is about ----------------------------
# Asking for `path: 'account'` anywhere would be wider than the message it prints. Repointing
# `loadChildren: () => import('./account/account-lifecycle.routes')` at `./entities/entity.routes`
# while leaving `path: 'account'` in place left this at exit 0 while its own refusal read
# "whatever $ROUTES_FILE says, nothing mounts it" — a message implying a check nobody had written.
# So the prefix is read off the object whose import RESOLVES to $ROUTES_FILE, which checks mounting
# and derives the prefix in one read.
app_dir=$(dirname "$APP_ROUTES")
prefix=''
while IFS=$'\t' read -r path imp; do
  [[ -n "$imp" ]] || continue
  [[ "$app_dir/$imp.ts" == "$ROUTES_FILE" ]] || continue
  prefix=$path
  break
done < <(route_objects "$stripped_app")

if [[ -z "$prefix" ]]; then
  refuse "no top-level route in $APP_ROUTES imports $ROUTES_FILE — nothing mounts it, so neither mail address is served whatever that file says"
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

# A WHOLE-LINE match, which is what a declared route path needs. `has_in` is a substring test, so
# `activate` would be satisfied by a route declaring `activate-account` — the rename case 4 refuses,
# passing as though it were served. Same fail-closed stdio discipline.
has_line() {
  local text=$1 pattern=$2 rc=0
  grep -qxF -- "$pattern" <<<"$text" || rc=$?
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

  # Asked of the route OBJECTS rather than of the raw text, so "declares this path" means the same
  # thing here as it does to the pairing below — two reads that disagreed about whitespace or key
  # order would be two guards with one name.
  if has_line "$(route_objects "$stripped_routes" | cut -f1)" "$child"; then
    note "ok   /$path  ->  path: '$child' in $(basename "$ROUTES_FILE")"
  else
    refuse "the mail composes /$path but $ROUTES_FILE declares no \`path: '$child'\` — the link lands on the client's 404 page. This is NEW-60."
  fi

  # The parameter is read by the screen behind that route, not by the route table. Which file that
  # is comes from THAT ROUTE OBJECT'S OWN `loadComponent`, bounded by the object's braces — so a
  # renamed component is followed rather than guessed, and the parameter must be read through
  # `paramValue`, because a literal `paramMap.get('key')` is an offender to
  # `endpoint-construction.spec.ts`.
  #
  # ⚠ Neither by position in the file NOR by line order within it. Two fail-opens were measured here
  # and `route_objects`' header carries the second; both reported a parameter read in the WRONG FILE
  # and exited 0, because both screens read a parameter called `key`.
  component=$(
    while IFS=$'\t' read -r p i; do
      if [[ "$p" == "$child" ]]; then
        printf '%s\n' "$i"
        break
      fi
    done < <(route_objects "$stripped_routes")
  )
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
