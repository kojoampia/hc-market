#!/usr/bin/env bash
#
# The shared comment stripper must strip the case its predecessor could not, and must not strip code.
#
# WHY THIS EXISTS. Four checks in build.yml match against source text and every one of them is only
# as good as `strip-comments.awk`. Three of the four carried a private line-based `sed` until
# decisions.md D56's review, which removes only a block comment that opens and closes on ONE line —
# so a comment naming a deleted call satisfied the check guarding it, verified on two of them, and
# that was the eighth fail-open in this family. Consolidating fixed it and created a new risk: the
# stripper is now one file that four checks trust, and nothing was asserting it works.
#
# It is a TEST OF A CHECK'S MECHANISM, which is the pattern pepper-wiring-test.sh established: it
# builds the states the old version passed on and asserts the new one does not.
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 1

awkfile=.github/checks/strip-comments.awk
fail=0

pass() { echo "ok   $1"; }
bad() { echo "::error file=$awkfile::$1"; fail=1; }

if [ ! -f "$awkfile" ]; then
  echo "::error::$awkfile does not exist — four checks in build.yml call it and two of them PASS when it is absent. See decisions.md D56."
  exit 1
fi

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

exit "$fail"
