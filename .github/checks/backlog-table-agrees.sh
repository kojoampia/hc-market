#!/usr/bin/env bash
# ==============================================================================
#  The packages table may not disagree with its own sections.
#
#  decisions.md D83, backlog NEW-41. `docs/backlog.md` holds each item's status in its `## ` heading,
#  and the packages table near the top holds a SECOND copy of it for the nineteen work packages. That
#  duplication is what produced every status disagreement this file has had: NEW-21 `READY` against
#  `DONE (D62)`, WP-17 `READY (spec only)` against `BLOCKED`, WP-18 `CLOSED` against `BLOCKED`.
#
#  D83 removed the duplication for the `NEW-*` half — the table indexes work packages only now, which
#  is what its header always said — and KEPT it for the WP half, because the table is the one place a
#  reader can see the nineteen packages at once. So the pairing that remains is guarded here rather
#  than trusted.
#
#  WHAT IT DOES NOT DO. It does not check that every section has a row or every row a section in the
#  other direction beyond the WP set, and it does not read the `NEW-*` sections at all: after D83 they
#  have no second copy to disagree with, and inventing one here would reintroduce exactly what the
#  decision removed.
#
#  WHAT MUST AGREE IS THE VOCABULARY WORD, NOT THE WHOLE CELL — and getting that wrong was this
#  check's own first version. Written to compare the cells exactly, it refused WP-04 (`DONE` against
#  `DONE, on \`pepper-the-pseudonym\``) and WP-11 (`DONE` against `DONE, merged as PR #19`), where the
#  heading carries extra information and not a different status. NEW-41 settles this: a differing
#  QUALIFIER is "the same answer in different words" and is explicitly not a defect, which is why it
#  lists NEW-11 so the next sweep does not re-find it.
#
#  So the comparison normalises each side to the text before the first `,` or ` (` — `DONE, merged as
#  PR #19` and `DONE (D81)` both become `DONE`, `CLOSED by decision (D62)` becomes `CLOSED by
#  decision` — and refuses only when those differ. A qualifier that differs is printed as a NOTE, so
#  the pairs stay visible without a correct change going red. The four real disagreements this check
#  exists for all differ in the vocabulary word: `READY` against `DONE (D62)`, `READY (spec only)`
#  against `BLOCKED`, `CLOSED` against `BLOCKED`.
#
#      ./.github/checks/backlog-table-agrees.sh
# ==============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKLOG="$ROOT/docs/backlog.md"
fail=0
ok()  { printf '  ok   %s\n' "$1"; }
err() { printf '::error::%s\n' "$1"; fail=1; }

[[ -f "$BACKLOG" ]] || { err "$BACKLOG does not exist, so this check read nothing"; exit 1; }

# THE TABLE'S OWN EXTENT, found from its header rather than by line number: the file grows at the top
# as often as anywhere else. Rows are read only between that header and the first blank line after it,
# so the illustrative tables inside item sections — NEW-41's own has the same row shape, and it is what
# a naive `grep '^| \*\*WP-'` over the whole file picks up — cannot be mistaken for the index.
table_rows="$(awk '
  /^\| WP \| Package \| Status \| Blocked on \|/ { intab = 1; next }
  intab && /^[[:space:]]*$/                      { exit }
  intab && /^\| \*\*WP-/                         { print }
' "$BACKLOG")"

row_n="$(printf '%s\n' "$table_rows" | grep -c . || true)"
if (( row_n < 2 )); then
  err "found $row_n work-package rows between the table header and the blank line after it, so this check has no subject. Either the header moved or the table is gone; both make the agreement below vacuous."
  printf '\nbacklog table agrees: FAILED\n'; exit 1
fi

# EVERY WP SECTION HEADING, and its status is everything after the last ' · '. Anchored at the start of
# a line so a heading quoted inside a paragraph is not read as one.
sec_ids="$(grep -oE '^## WP-[0-9]+ ' "$BACKLOG" | tr -d '# ' | sort -u)"
sec_n="$(printf '%s\n' "$sec_ids" | grep -c . || true)"
if (( sec_n < 2 )); then
  err "found $sec_n '## WP-<n>' section headings, so there is nothing to compare the table against"
  printf '\nbacklog table agrees: FAILED\n'; exit 1
fi

# THE VOCABULARY WORD ALONE: everything before the first `,` or ` (`, trimmed. See the header for why
# this is not an exact cell comparison.
vocab() { printf '%s' "$1" | sed -E 's/ \(.*//; s/,.*//' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'; }

bad=0 paired=0 quals=0
while IFS= read -r row; do
  [[ -n "$row" ]] || continue
  id="$(printf '%s' "$row" | sed -nE 's/^\| \*\*(WP-[0-9]+)\*\*.*/\1/p')"
  [[ -n "$id" ]] || { err "a work-package row does not begin '| **WP-<n>** |': '$row'"; bad=1; continue; }
  # column 3, trimmed. `cut -d'|' -f4` because the leading '|' makes field 1 empty.
  rowst="$(printf '%s' "$row" | cut -d'|' -f4 | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  head="$(grep -m1 -E "^## $id " "$BACKLOG" || true)"
  if [[ -z "$head" ]]; then
    err "$id has a row in the packages table and no '## $id — … · <status>' section. The heading is where an item's status lives (backlog line 10), so a row without one is a status nothing owns."
    bad=1; continue
  fi
  secst="$(printf '%s' "$head" | sed -E 's/.* · //')"
  paired=$(( paired + 1 ))
  if [[ "$(vocab "$rowst")" != "$(vocab "$secst")" ]]; then
    err "$id: the packages table says '$rowst' and its own section says '$secst' — different words from the status vocabulary, not a differing qualifier. The heading wins: correct the row, or correct both if neither fits, as WP-18 did (decisions.md D83)."
    bad=1
  elif [[ "$rowst" != "$secst" ]]; then
    printf '  note %s: the table says %s and its section says %s — the same status with a different qualifier, which NEW-41 settles as not a defect\n' \
      "$id" "'$rowst'" "'$secst'"
    quals=$(( quals + 1 ))
  fi
done <<< "$table_rows"

# THE OTHER DIRECTION, over the WP set only: a section with no row is an item missing from the index
# that claims to list them all. This is the half that let NEW-23..NEW-39 go unindexed for seventeen
# items, and it is checked here for the WP set because that set is what the table now claims to be.
for id in $sec_ids; do
  printf '%s\n' "$table_rows" | grep -qE "^\| \*\*$id\*\*" \
    || { err "$id has a section and no row in the packages table, which claims to index the work packages"; bad=1; }
done

if (( bad == 0 )); then
  ok "the packages table agrees with all $paired work-package sections on the status word, and indexes every one of them${quals:+ ($quals differ only in their qualifier)}"
fi

printf '\n'
if (( fail )); then printf 'backlog table agrees: FAILED\n'; else printf 'backlog table agrees: ok\n'; fi
exit $fail
