# Remove Java comments from a source file, preserving line numbering.
#
# ELEVEN steps in build.yml match against source text and call this — counted with
# `awk '/^      - name: /{n=$0} /strip-comments\.awk/{print n}' build.yml | sort -u`, because this
# header said "four" for four decisions after it had stopped being four, and the number in a sentence
# like this one is the first thing to rot. Every one of them is only as good as this file:
# "A CHECK WHOSE REACH DEPENDS ON PROSE IS NOT A CHECK" (D53's review) has now been the finding in
# NINE successive fail-opens, and each time the fix was to strip comments before matching. This is
# that stripper, in one place, because three of the callers had each grown their own and two of them
# were wrong.
#
# WHAT WAS WRONG WITH THE OTHER ONE — decisions.md D56's review, and worth stating in full because it
# looked correct and had been copied twice:
#
#   sed -E 's@/\*[^*]*\*+([^/*][^*]*\*+)*/@ @g; s@//.*@@' "$f" | grep -vE '^[[:space:]]*\*'
#
# sed is LINE-BASED, so that block-comment expression only matches a comment that opens and closes on
# one line. Javadoc survived it only by accident: the `grep -v` drops continuation lines beginning
# with `*`. A NON-JAVADOC block comment spanning lines whose body does not start with `*` passes
# through whole — and that is the house style in this repository
# (`booking/.../ErasureWorkflow.java` has a five-line one). So a check guarding a string literal could
# be satisfied by a comment mentioning it, which is the exact fail-open all of them exist to close.
# Measured both ways on the same probe: the sed leaves `queryParam` intact, this removes it.
#
# LINE NUMBERING IS PRESERVED — exactly one output line per input line, blanked rather than deleted.
# Three callers depend on it: the implicit-zone check quotes the ORIGINAL line back at the author by
# number, D54's CRUD check compares annotation positions against the class declaration, and D77's
# precedence check compares an annotation's position against it too.
#
# IT UNDERSTANDS STRING LITERALS, AND THAT WAS THE NINTH FAIL-OPEN IN THIS FAMILY — decisions.md D77.
# This header used to say the opposite, in a sentence with two claims in it and both of them false:
#
#   "It is deliberately not a Java parser. `/*` inside a string literal would confuse it, and nothing
#    in the estate has one; the failure would be fail-CLOSED (too much removed, a check goes red on
#    correct code), which is the right direction for a tool whose whole job is to stop things passing."
#
# MEASURED, on the tree that sentence was written against. THREE DIFFERENT MEASURES ARE IN PLAY here
# and the first version of this comment mixed two of them — it reported a main+test line count against
# a main-only file count, which is the defect this whole entry is about, one document along. Each
# figure below therefore names its TREE and its MEASURE. Method: run both strippers over each file and
# compare OUTPUT LINE BY LINE — both emit one line per input line, so the outputs align by number.
# Do not use `diff` for this; it realigns and over-counts (499 against the true 444).
#
#   MAIN SOURCES, 535 files
#     36 files differ at all, over 444 output lines
#     14 of those are TRUNCATED FROM A STRING LITERAL TO END OF FILE, losing 377 lines between them —
#        every service's generated SecurityConfiguration ("/api/admin/**"), every service's
#        WebConfigurer ("/api/**"), catalog's two hand-written chains ("/internal/**",
#        "/api/professionals/*"), booking's webhook chain ("/webhooks/**") and payout's LedgerDTO
#        (invisible from line 14 to 210). `/**` inside a string opened a block comment that never closed.
#     22 differ WITHOUT truncating, losing 67 lines cut mid-line, because the old `//` strip was
#        applied to the whole line unconditionally — so every
#        `@Value("${…:http://healthconnectcatalog}")` in booking's four service clients lost
#        everything from `http:` onwards.
#
#   MAIN AND TEST, 886 files
#     56 files differ, over 528 lines; 15 truncated to EOF.
#
# The 14/535 half is the one to re-derive first if any of this is ever in doubt: it reproduces file
# for file, and it is the half that made a check pass on its own subject.
#
# And the direction was backwards. Removing too much is fail-CLOSED only for a check that must FIND
# something; for a check that must NOT find something — the three estate-wide bans, and D77's own —
# text that is not there cannot be matched, so a banned line hidden behind a path pattern PASSES. D77
# found that by running its new check and watching it report `ok` for the two catalog chains and the
# booking chain it had just been written to guard: they were invisible, and only the generated chain
# above the truncation point was seen. A check that cannot see its own subject reports success.
#
# So it now tracks four states rather than one. `"…"`, `'…'` and `"""…"""` are code and are copied
# through verbatim; a backslash escapes the next character in all three. Only `inblk` and `intxt`
# carry across lines, because a normal string or char literal may not contain a newline in Java —
# `instr` and `inchr` are reset at end of line deliberately, so that a construct this does not
# understand costs one line rather than the rest of the file, which is what the old version did.
#
# ONE FILE PER INVOCATION, and the `FNR == 1` reset is what makes that safe rather than conventional.
# `inblk` and `intxt` are global, so a file ending inside an unterminated construct would otherwise
# leak that state into the NEXT file on the same command line and truncate it from line 1 — the same
# fail-open as above, arriving without any Java being unusual. Every caller in build.yml runs this per
# file today, and nothing enforces that, so the guard is here instead of in a comment asking for it.
# It costs nothing when there is one file, and it is the right per-file semantics in any case.
#
# It is still not a Java parser and does not need to be. What it does not handle is stated rather
# than assumed, because the sentence it replaced was an assumption: a `"""` opening a text block must
# be the last token on its line in Java, so it is never confused with an empty string beside a quote;
# and a unicode escape spelling a comment opener (u002a after a backslash-u) is not decoded, which
# javac would accept and no file here contains. Both were probed adversarially at D77's review, along
# with char literals holding a delimiter, an escaped quote followed by a real comment on one line, a
# `"*/"` in a string beside a stray `*/` in code, CRLF, tabs and a missing trailing newline. Verify a
# claim about this file by running strip-comments-test.sh, never by reading this comment.
FNR == 1 { inblk = 0; intxt = 0 }
{
  line = $0
  out = ""
  i = 1
  n = length(line)
  while (i <= n) {
    c = substr(line, i, 1)
    if (inblk) {
      if (substr(line, i, 2) == "*/") { inblk = 0; i += 2 } else { i++ }
      continue
    }
    if (intxt) {
      if (c == "\\") { out = out substr(line, i, 2); i += 2; continue }
      if (substr(line, i, 3) == "\"\"\"") { out = out "\"\"\""; intxt = 0; i += 3; continue }
      out = out c; i++
      continue
    }
    if (instr) {
      if (c == "\\") { out = out substr(line, i, 2); i += 2; continue }
      out = out c
      if (c == "\"") instr = 0
      i++
      continue
    }
    if (inchr) {
      if (c == "\\") { out = out substr(line, i, 2); i += 2; continue }
      out = out c
      if (c == "'") inchr = 0
      i++
      continue
    }
    if (substr(line, i, 2) == "/*") { inblk = 1; i += 2; continue }
    if (substr(line, i, 2) == "//") break
    if (substr(line, i, 3) == "\"\"\"") { out = out "\"\"\""; intxt = 1; i += 3; continue }
    if (c == "\"") { out = out c; instr = 1; i++; continue }
    if (c == "'") { out = out c; inchr = 1; i++; continue }
    out = out c
    i++
  }
  instr = 0
  inchr = 0
  print out
}
