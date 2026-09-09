# Remove shell comments from a script, preserving line numbering.
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
#
# That second case is the deliberate direction. Removing too much makes a check go RED on correct
# code, which sends somebody to look; removing too little lets a comment satisfy the check it is
# supposed to be guarded against, which is the failure this whole family exists to close. The caller
# asserts both halves — that a comment naming a guarded string is removed, and that real code
# survives — because every other assertion in a stripper's test is satisfied by one that prints
# nothing.
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
