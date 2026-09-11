# Remove shell comments from a script, preserving line numbering.
#
# DO NOT WRITE THE NUMBER OF CALLERS INTO A SENTENCE. Derive it — decisions.md D82, backlog NEW-38.
# The files that invoke this one are:
#
#   grep -rl 'strip-sh-comments\.awk' .github/ | grep -v 'checks/strip-sh-comments\.awk$' \
#     | xargs grep -lE "awk[[:space:]]+-f[^|;]*strip-sh-comments|STRIP[A-Z_]*=[^=]*strip-sh-comments|awkfile=[^=]*strip-sh-comments"
#
# and the `build.yml` steps whose subject reaches it are:
#
#   awk '/^      - name: /{n=$0} /strip-sh-comments\.awk/{print n}' .github/workflows/build.yml | sort -u
#
# Those are TWO MEASURES and neither is "the number of callers": the first counts files that run this
# awk, the second counts workflow steps that mention it — and today `build.yml` mentions it only in a
# comment, so it invokes nothing directly while one of its steps depends on it through
# `outbox-alias-restore-test.sh`. Name the measure whenever quoting either.
#
# This header carries no total on purpose. Two test files stated one and BOTH were wrong in the same
# direction: `shared-plane-wiring-test.sh` said "four checks" and `host-probe-attribution-test.sh` said
# "five", while five files invoke it — and NEW-38, the item raised to correct the first, itself named
# five callers by a composition that missed one. Three statements of one number, three different wrong
# answers, in an estate whose own rule is "count the list, not the number".
#
# THE SHARED STRIPPER NEXT TO THIS ONE IS A JAVA STRIPPER — `//` and `/* */` — and running it over a
# shell script removes nothing at all while reporting success, which is the fail-open every one of
# those eight findings was. So this is a second file rather than a widening of the first: the two
# languages agree on nothing about what a comment looks like, and a stripper that guessed from the
# extension would be one `case` away from silently doing neither.
#
# This is NOT a shell parser. The rule is: blank from the first `#` that begins a word — at column 1,
# or preceded by a space or a tab — to end of line.
#
#   `${#arr[@]}`, `$#`, `${x#pfx}`      SURVIVE, because that `#` follows `{` or `$`.
#   `echo "issue #4"`                   is TRUNCATED, because that one follows a space, and nothing
#                                       here knows it is inside a quote.
#   `true;# a comment`                  is NOT SEEN, because that `#` follows `;`. Fail-OPEN, and
#                                       tolerated rather than fixed: see below.
#
# The truncation is the deliberate direction. Removing too much makes a check go RED on correct
# code, which sends somebody to look; removing too little lets a comment satisfy the check it is
# supposed to be guarded against, which is the failure this whole family exists to close. The caller
# asserts both halves — that a comment naming a guarded string is removed, and that real code
# survives — because every other assertion in a stripper's test is satisfied by one that prints
# nothing.
#
# `;#` IS THE ONE FAIL-OPEN HERE AND IT IS NAMED RATHER THAN CLOSED, because widening the rule to
# "any `#` not preceded by `{` or `$`" would truncate `${x#pfx}` written as `${x#pfx}` after a
# semicolon and every other punctuation-adjacent expansion nobody has written yet — trading a limit
# nothing can exploit for one that fires on correct code. Nothing can exploit it today because the
# caller's guarded strings are matched on lines of their own (`^mapfile …`, `^reconnect$`,
# `^docker network disconnect`) or counted across the file where a `;#` comment would have to
# contain the string to matter. `strip-sh-comments-test.sh` §9 pins both limits so that a change to
# either is a decision, and it passes either way on this one while telling you which you have.
#
# LINE NUMBERING IS PRESERVED: exactly one output line per input line, blanked rather than deleted.
# The caller compares line numbers of a capture against a disconnect, so a dropped line would move
# every relation below it.
{
  line = $0
  n = length(line)
  for (i = 1; i <= n; i++) {
    c = substr(line, i, 1)
    if (c != "#") continue
    if (i == 1) { line = ""; break }
    p = substr(line, i - 1, 1)
    if (p == " " || p == "\t") { line = substr(line, 1, i - 1); break }
  }
  print line
}
