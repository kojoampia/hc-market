# Remove Java comments from a source file, preserving line numbering.
#
# Four checks in build.yml match against source text, and every one of them is only as good as this:
# "A CHECK WHOSE REACH DEPENDS ON PROSE IS NOT A CHECK" (D53's review) has now been the finding in
# eight successive fail-opens, and each time the fix was to strip comments before matching. This is
# that stripper, in one place, because three of the four had each grown their own and two of them
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
# Two callers depend on it: the implicit-zone check quotes the ORIGINAL line back at the author by
# number, and D54's CRUD check compares annotation positions against the class declaration.
#
# It is deliberately not a Java parser. `/*` inside a string literal would confuse it, and nothing in
# the estate has one; the failure would be fail-CLOSED (too much removed, a check goes red on correct
# code), which is the right direction for a tool whose whole job is to stop things passing.
{
  line = $0
  while (1) {
    if (inblk) {
      p = index(line, "*/")
      if (p == 0) { line = ""; break }
      line = substr(line, p + 2); inblk = 0
    } else {
      p = index(line, "/*")
      if (p == 0) break
      pre = substr(line, 1, p - 1); rest = substr(line, p + 2)
      q = index(rest, "*/")
      if (q == 0) { line = pre; inblk = 1; break }
      line = pre substr(rest, q + 2)
    }
  }
  sub(/\/\/.*/, "", line)
  print line
}
