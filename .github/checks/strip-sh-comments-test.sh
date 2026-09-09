#!/usr/bin/env bash
#
# The SHELL comment stripper must strip shell comments, must not strip shell code, and must keep the
# two limits its header claims — decisions.md D68 §12.
#
# WHY THIS EXISTS. `strip-sh-comments.awk` arrived with three inline controls in its one caller
# (outputs-nothing, strips-nothing, real-code-survives, all against the real subject) and that is
# genuine coverage of the *caller*. What it did not cover is the **survival** properties the awk's
# own header documents — `${#arr[@]}`, `$#`, `${x#pfx}` — and `verify-outbox-recovery.sh` depends on
# the first of those on a line no assertion in that caller greps. "Documented in three places and
# enforced nowhere" is this repository's named failure mode, and the sibling `strip-comments.awk`
# spent a whole family of fail-opens establishing it.
#
# It is a TEST OF A CHECK'S MECHANISM, the pattern strip-comments-test.sh established, and it is
# deliberately the same shape so the two can be read side by side.
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 1

awkfile=.github/checks/strip-sh-comments.awk
javaawk=.github/checks/strip-comments.awk
fail=0

pass() { echo "ok   $1"; }
bad() { echo "::error file=$awkfile::$1"; fail=1; }

if [ ! -f "$awkfile" ]; then
  echo "::error::$awkfile does not exist — outbox-alias-restore-test.sh calls it and guards for it, so that check exits 1 rather than passing blind. See decisions.md D68."
  exit 1
fi

probe=$(mktemp)
trap 'rm -f "$probe"' EXIT

# Every shape that matters, in one file: the two comment forms to remove, the three `#`-in-expansion
# forms to keep, and one line of ordinary code as the control. The tokens are distinct so a single
# grep can tell which survived.
cat > "$probe" <<'SH'
#!/usr/bin/env bash
# a full-line comment naming KILL_FULLLINE
set -uo pipefail
count=${#KEEP_ARRAY[@]}          # a trailing comment naming KILL_TRAILING
args=$#
trimmed=${KEEP_VAR#prefix}
docker network connect KEEP_CODE
SH

stripped=$(awk -f "$awkfile" "$probe")

# 1. A full-line comment goes.
if printf '%s\n' "$stripped" | grep -q 'KILL_FULLLINE'; then
  bad "a full-line # comment survived stripping"
else
  pass "a full-line # comment is stripped"
fi

# 2. A trailing comment goes. This is the one that matters for the caller: the guarded string
#    `docker network connect` appears in that script's header prose, and unstripped the count reads
#    3 rather than 1.
if printf '%s\n' "$stripped" | grep -q 'KILL_TRAILING'; then
  bad "a trailing # comment survived stripping"
else
  pass "a trailing # comment is stripped"
fi

# 3-5. THE SURVIVAL PROPERTIES the header documents, each asserted separately — a battery that fires
#      as one number cannot say which of the three broke, and the caller depends on the first.
for tok in KEEP_ARRAY:'${#arr[@]}' KEEP_VAR:'${x#pfx}'; do
  name=${tok%%:*}; form=${tok#*:}
  if printf '%s\n' "$stripped" | grep -q "$name"; then
    pass "$form survives — that # follows a brace, not a space"
  else
    bad "$form was stripped; the caller reads \${#BOOKING_ALIASES[@]} and would go blind"
  fi
done
if printf '%s\n' "$stripped" | grep -q 'args=\$#'; then
  pass '$# survives — that # follows a dollar'
else
  bad '$# was stripped'
fi

# 6. REAL CODE SURVIVES. Every assertion above except 3-5 is satisfied by a stripper that outputs
#    nothing, which is exactly how a missing sibling made two callers pass. This is the control.
if printf '%s\n' "$stripped" | grep -q 'KEEP_CODE'; then
  pass "real code survives — the assertions above are not passing on an empty result"
else
  bad "real code was stripped too; the check calling this is now blind rather than merely wrong"
fi

# 7. LINE NUMBERING IS PRESERVED. The caller asserts four line-position relations against each other
#    and takes the LAST match of the capture anchor; a stripper that deleted lines would move every
#    relation below the deletion, silently.
in_lines=$(wc -l < "$probe")
out_lines=$(printf '%s\n' "$stripped" | wc -l)
if [ "$in_lines" -eq "$out_lines" ]; then
  pass "line numbering is preserved — $in_lines lines in, $out_lines out"
else
  bad "line numbering changed: $in_lines lines in, $out_lines out; the caller compares line numbers"
fi

# 8. THE JAVA STRIPPER REALLY CANNOT DO THIS, which is the whole reason a second file exists rather
#    than a fifth caller of the first. Reproduced rather than asserted in prose: if this ever stops
#    being true, the second stripper's justification needs re-establishing and CLAUDE.md is wrong.
if [ -f "$javaawk" ]; then
  viajava=$(awk -f "$javaawk" "$probe")
  if printf '%s\n' "$viajava" | grep -q 'KILL_FULLLINE'; then
    pass "strip-comments.awk leaves shell comments intact — the fail-open is real and reproduced here"
  else
    bad "strip-comments.awk now strips shell comments too; this file's premise needs re-establishing"
  fi
  # …and it exits ZERO while doing nothing, which is what makes it dangerous rather than merely
  # useless. A non-zero would have sent somebody to look.
  if awk -f "$javaawk" "$probe" >/dev/null 2>&1; then
    pass "…and exits 0 while removing nothing, which is why the wrong stripper fails open"
  else
    bad "strip-comments.awk now errors on a shell file; the argument for two files has changed shape"
  fi
else
  bad "$javaawk is missing; case 8 cannot establish why a second stripper exists"
fi

# 9. THE TWO STATED LIMITS, asserted so they stay stated. Neither is a defect to fix — both are
#    written in the awk's header — and pinning them means a change to either is a deliberate one.
#
#    (a) A `#` inside a quoted string, preceded by a space, TRUNCATES the line. Fail-CLOSED: it
#        removes too much, so a check goes red on correct code and somebody looks.
printf 'echo "issue # KEEP_QUOTED"\n' > "$probe"
if awk -f "$awkfile" "$probe" | grep -q 'KEEP_QUOTED'; then
  bad "a quoted # no longer truncates; the header documents that it does, and fail-closed is the point"
else
  pass "a quoted # truncates the line — fail-closed, as documented"
fi

#    (b) `;#` with no space is NOT seen. Fail-OPEN, and the reason it is tolerated rather than fixed
#        is that no assertion in the caller can be satisfied by it: the guarded strings are matched
#        on lines of their own. Pinned here so the tolerance is a decision rather than an oversight.
printf 'true;# KEEP_SEMI docker network connect\n' > "$probe"
if awk -f "$awkfile" "$probe" | grep -q 'KEEP_SEMI'; then
  pass ";# is not treated as a comment — a stated limit, fail-open, tolerated (D68 §12)"
else
  pass ";# is now stripped too — strictly better than documented; update the awk's header"
fi

exit "$fail"
