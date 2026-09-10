#!/usr/bin/env bash
#
# The shared comment stripper must strip the case its predecessor could not, and must not strip code.
#
# WHY THIS EXISTS. Every step in build.yml that matches against source text is only as good as
# `strip-comments.awk`. **THE NUMBER OF THEM IS COUNTED BELOW, NEVER WRITTEN HERE** — D77's reviewer
# found this file still saying "four checks" in two places, including the operator-facing message, in
# the same commit whose whole subject was a comment that rots: the stripper's own header had read
# "four" for four decisions after it stopped being four. A count in a sentence is the first thing to
# go stale, so `callers` is derived from the workflow on every run and interpolated into the one
# message an operator reads at the worst moment.
#
# Three of the callers carried a private line-based `sed` until decisions.md D56's review, which
# removes only a block comment that opens and closes on ONE line — so a comment naming a deleted call
# satisfied the check guarding it, verified on two of them, and that was the eighth fail-open in this
# family. Consolidating fixed it and created a new risk: the stripper is now one file that every
# text-matching check trusts, and nothing was asserting it works.
#
# It is a TEST OF A CHECK'S MECHANISM, which is the pattern pepper-wiring-test.sh established: it
# builds the states the old version passed on and asserts the new one does not.
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 1

awkfile=.github/checks/strip-comments.awk
workflow=.github/workflows/build.yml
fail=0

pass() { echo "ok   $1"; }
bad() { echo "::error file=$awkfile::$1"; fail=1; }

# Derived, not stated — the same one-liner the stripper's own header publishes. `?` rather than a
# number if the workflow is unreadable, because a wrong count in a refusal is worse than no count.
callers='?'
if [ -f "$workflow" ]; then
  callers=$(awk '/^      - name: /{n=$0} /strip-comments\.awk/{print n}' "$workflow" | sort -u | grep -c . || true)
fi

if [ ! -f "$awkfile" ]; then
  echo "::error::$awkfile does not exist — $callers steps in build.yml call it and two of them PASS when it is absent. See decisions.md D56 and D77."
  exit 1
fi
pass "$callers steps in $workflow call the stripper — counted, not quoted"

probe=$(mktemp)
trap 'rm -f "$probe"' EXIT

# The house style: a multi-line block comment whose continuation lines do NOT begin with `*`. This
# is the exact shape the old sed could not see, and `booking/.../ErasureWorkflow.java` has one.
cat > "$probe" <<'JAVA'
package probe;

/* this used to send the moment:
   uri.queryParam("at", at)
   and now it does not */
public final class Probe {

    /**
     * Javadoc naming queryParam("on", on) in prose.
     */
    void real() {
        uri.queryParam("amountMinor", amountMinor); // trailing queryParam("nope", x)
    }
}
JAVA

stripped=$(awk -f "$awkfile" "$probe")

# 1. The multi-line block comment is gone.
if printf '%s\n' "$stripped" | grep -q 'queryParam("at"'; then
  bad "a multi-line block comment survived stripping — this is the fail-open D56's review found, restored"
else
  pass "a multi-line, non-javadoc block comment is stripped"
fi

# 2. Javadoc is gone. It always was, but by accident: the old sed relied on a `grep -v` dropping
#    `*`-prefixed lines, which is not the same thing as understanding a block.
if printf '%s\n' "$stripped" | grep -q 'queryParam("on"'; then
  bad "javadoc survived stripping"
else
  pass "javadoc is stripped"
fi

# 3. A trailing // comment is gone.
if printf '%s\n' "$stripped" | grep -q 'queryParam("nope"'; then
  bad "a trailing // comment survived stripping"
else
  pass "a trailing // comment is stripped"
fi

# 4. REAL CODE SURVIVES. Every assertion above is satisfied by a stripper that outputs nothing, which
#    is precisely how a missing one made two callers pass. This is the control.
if printf '%s\n' "$stripped" | grep -q 'queryParam("amountMinor"'; then
  pass "real code survives — the assertions above are not passing on an empty result"
else
  bad "real code was stripped too; every check calling this is now blind rather than merely wrong"
fi

# 5. LINE NUMBERING IS PRESERVED. The implicit-zone check quotes the original line back by number and
#    D54's compares annotation positions against the class declaration; a stripper that deleted lines
#    would make both of those cite the wrong line, silently.
in_lines=$(wc -l < "$probe")
out_lines=$(printf '%s\n' "$stripped" | wc -l)
if [ "$in_lines" -eq "$out_lines" ]; then
  pass "line numbering is preserved — $in_lines lines in, $out_lines out"
else
  bad "line numbering changed: $in_lines lines in, $out_lines out; two callers quote lines by number"
fi

# 6. And the predecessor really did fail on case 1, so this file documents what it defends against
#    rather than asserting it in prose. If this ever stops being true the comment above is wrong.
old=$(sed -E 's@/\*[^*]*\*+([^/*][^*]*\*+)*/@ @g; s@//.*@@' "$probe" | grep -vE '^[[:space:]]*\*')
if printf '%s\n' "$old" | grep -q 'queryParam("at"'; then
  pass "the sed it replaced does leave that comment intact — the defect is real and reproduced here"
else
  bad "the sed it replaced no longer reproduces the defect; this test's premise needs re-establishing"
fi

# ---------------------------------------------------------------------------------------------------
# STRING LITERALS — decisions.md D77, the ninth fail-open in this family.
#
# The header used to say `/*` inside a string "would confuse it, and nothing in the estate has one".
# Fourteen main-source files had one: every service's generated SecurityConfiguration and
# WebConfigurer, catalog's two hand-written filter chains, booking's webhook chain and payout's
# LedgerDTO. A `/**` inside a path pattern opened a block comment that never closed, so the file was
# truncated from that line to EOF — and for a check that must NOT find something, text that is not
# there cannot be matched, so the direction was fail-OPEN and not fail-closed as the header claimed.
#
# Every assertion below is paired with the shape it is defending, and case 11 is the CONTROL for the
# whole group: a stripper that simply stopped stripping would satisfy 7 through 10.
#
# THE BLOCK COMMENT IS LAST, AND THAT ORDERING IS LOAD-BEARING. It was above `marker` in the first
# version of this file and case 12 caught it: the string-blind stripper opens an unterminated block
# comment at `"/internal/**"` and then RECOVERS at the first `*/` it meets, so a closing comment
# delimiter anywhere above the marker hands it back everything below — case 7 passed under both
# versions and distinguished nothing. Keep every assertion's subject above the only `*/` in the probe.
strprobe=$(mktemp)
trap 'rm -f "$probe" "$strprobe"' EXIT
cat > "$strprobe" <<'JAVA'
package probe;

public final class StringProbe {

    static final String PATHS = "/internal/**";
    static final String GLOB = "/api/professionals/*";
    static final String URL = "http://healthconnectcatalog";
    static final char SLASH = '/';
    static final String ESCAPED = "a\"/*b";
    String block = """
        {"href":"http://x/**","note":"not a comment"}""";

    void keptAfterEveryStringAbove() {
        marker("survived");
    }

    /* a real block comment naming shouldNotSurvive("x") */
}
JAVA

s=$(awk -f "$awkfile" "$strprobe")

# 7. THE HEADLINE CASE. `/**` inside a path pattern must not open a comment, so everything after it
#    in the file is still there. This is the exact shape of all six filter-chain files.
if printf '%s\n' "$s" | grep -q 'marker("survived")'; then
  pass "a '/**' inside a string literal does not truncate the rest of the file"
else
  bad "a '/**' inside a string literal still swallows the rest of the file — D77's defect, restored. Fourteen main-source files have one, including every service's SecurityConfiguration"
fi

# 8. `//` inside a string is not a line comment. In the main sources, 22 files that did NOT truncate
#    lost 67 lines this way — the figure's tree and measure are named because D77's first draft mixed
#    a main+test line count with a main-only file count. Nearly all of them are @Value defaults like
#    ${healthconnect.catalog.base-url:http://healthconnectcatalog}.
if printf '%s\n' "$s" | grep -q 'http://healthconnectcatalog'; then
  pass "a '//' inside a string literal is not treated as a line comment"
else
  bad "a '//' inside a string literal is still cut as a line comment — every cross-service base-url default in booking loses its host"
fi

# 9. A text block is code. booking/.../OutboxPublisher.java and every service's App class have one,
#    and their content contains both quotes and URLs.
if printf '%s\n' "$s" | grep -q 'not a comment'; then
  pass "a text block's content survives, quotes and slashes included"
else
  bad "a text block's content was stripped or truncated; OutboxPublisher's envelope template is one"
fi

# 10. A comment is STILL a comment when it follows all of that — the state machine has to come back
#     out of every literal above, and an off-by-one in any of them leaves it inside a string for ever.
if printf '%s\n' "$s" | grep -q 'shouldNotSurvive'; then
  bad "a block comment after the string literals survived, so the stripper never left one of them — it is now keeping comments, which is the fail-open all five callers exist to close"
else
  pass "a block comment after every literal above is still stripped"
fi

# 11. THE CONTROL. An escaped quote and a char literal holding a slash are the two ways to leave the
#     state machine mid-string; if either is mishandled the rest of the file is either swallowed or
#     treated as a string, and 7 through 10 can pass for the wrong reason.
if printf '%s\n' "$s" | grep -q 'a\\"/\*b'; then
  pass "an escaped quote and a char literal do not desynchronise the scanner"
else
  bad "an escaped quote or a char literal desynchronised the scanner; cases 7 to 10 may be passing for the wrong reason"
fi

# 12. And the version this replaced really did truncate case 7, so the defect is reproduced here
#     rather than asserted in prose — the same discipline as case 6.
truncating=$(mktemp)
trap 'rm -f "$probe" "$strprobe" "$truncating"' EXIT
cat > "$truncating" <<'AWK'
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
AWK
if awk -f "$truncating" "$strprobe" | grep -q 'marker("survived")'; then
  bad "the string-blind version no longer truncates this probe; case 7's premise needs re-establishing"
else
  pass "the string-blind version it replaced does truncate this probe — the defect is real and reproduced here"
fi

# 13. LINE NUMBERING IS PRESERVED FOR THIS PROBE TOO. Case 5 asserts it only for the first probe,
#     which has no text block — and a text block is the one construct here that spans lines, so it is
#     exactly where a state machine could swallow or emit an extra one. Two callers quote the original
#     line back by number and D77's precedence check compares an annotation's line against the class
#     declaration's, so an off-by-one here mis-attributes an error to the wrong line rather than
#     failing. A review finding: cheap, and untested until asked for.
in_str=$(wc -l < "$strprobe")
out_str=$(printf '%s\n' "$s" | wc -l)
if [ "$in_str" -eq "$out_str" ]; then
  pass "line numbering survives the string and text-block probe — $in_str lines in, $out_str out"
else
  bad "line numbering changed on the string probe: $in_str lines in, $out_str out; the text block is the likely culprit and three callers cite lines by number"
fi

# 14. STATE DOES NOT LEAK BETWEEN FILES. `inblk` and `intxt` are global, so a file ending inside an
#     unterminated construct would truncate the NEXT file on the same command line from line 1 — the
#     same fail-open one level up, arriving with no Java being unusual. Every caller passes one file
#     today and nothing enforces that, so the awk guards it with `FNR == 1` and this pins the guard.
#
#     THE SECOND FILE MUST BE $strprobe, NOT $probe, and that is case 12's lesson applied to case 14
#     rather than restated. Written with $probe first, this assertion did NOT discriminate: leaked
#     state recovers at the first `*/` it meets, $probe closes a block comment on its fourth line, and
#     `queryParam("amountMinor")` sits well below that — so deleting the FNR == 1 reset from the awk
#     left this GREEN. Measured, with the mutant's absence asserted first. $strprobe's only `*/` is
#     below its marker, so leakage swallows `marker("survived")` and the assertion bites.
leaky=$(mktemp)
trap 'rm -f "$probe" "$strprobe" "$truncating" "$leaky"' EXIT
printf 'class Unterminated {\n    /* a block comment with no end\n' > "$leaky"
if awk -f "$awkfile" "$leaky" "$strprobe" | grep -q 'marker("survived")'; then
  pass "an unterminated comment in one file does not truncate the next file on the same command line"
else
  bad "block-comment state leaked from one file into the next, so a multi-file invocation truncates from line 1 with nothing unusual in the Java. The FNR == 1 reset in $awkfile is missing"
fi

exit "$fail"
