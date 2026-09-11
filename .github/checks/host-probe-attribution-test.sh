#!/usr/bin/env bash
# ==============================================================================
#  host-probe-attribution.sh's own test — decisions.md D75, backlog NEW-33.
#
#  The check beside this file is green on a correct tree, which says nothing at all: nine harnesses
#  in this repository have reported success while testing nothing, and three of those were written
#  by whoever was fixing the last one. So each broken state is constructed on a COPY of
#  deploy-prod.sh, the mutation is asserted to have APPLIED — original gone, mutant present, `bash
#  -n` parsing — before its result is believed, and the check must go red THROUGH THE DOOR IT WAS
#  AIMED AT. "It went red" is not the assertion here: every one of these outcomes stops the deploy
#  either way, and what is being pinned is which cause the refusal names.
#
#  ONE MUTATION PER ROW OF THE LIST BELOW, and this header states no total — decisions.md D78 §14.
#  It said THIRTY while thirty-three cases ran, in the commit that had just corrected the same count
#  in three other files, which is this repository's oldest failure mode and the fifth instance of it
#  in this package alone. **Two measures are in play** and a single number cannot be either: the
#  numbered cases (each a named broken state) and the red states asserted (cases 14 and 22 are
#  hand-rolled rather than `expect_red` calls, and the control is a green assertion). Both are DERIVED
#  and printed by the run's own summary, so nothing here can go stale; read that line, not a comment.
#
#    1  the sentinel test deleted            — host_run back to reading a status, which cannot tell
#                                              ssh's 255 from the remote command's
#    2  the sentinel never appended          — nothing establishes that a shell ran; every probe
#                                              reads as an ssh failure, including the good one
#    3  the far-side subshell removed        — a remote command containing `exit` never reaches the
#                                              printf, so a command that RAN is reported as ssh not
#                                              arriving. This is why part 1's probes say `exit`
#    4  the unanswerable arm folded into the
#       absence arm                          — NEW-33 itself, restored: one message for two causes
#    5  the absence match loosened to the
#       bare `not found` substring           — D71's tightening, undone. The state it lets through
#                                              is a docker relaying somebody else's error over
#                                              DOCKER_HOST=ssh://, which exits 1 and no status
#                                              branch can see
#    6  $net_hint printed on the unanswerable
#       arm                                  — the right cause with the wrong remedy, which is the
#                                              cost NEW-33 is actually about
#    7  $net_hint dropped from the absence
#       arm                                  — the one arm it belongs on, left without it
#    8  the 127 branch deleted               — a host with no docker reported as a daemon that could
#                                              not be asked
#    9  the ssh gate spells out its own ssh
#       again                                — a probe that stops routing through host_run, which
#                                              parts 1 and 2 cannot see at all
#   10  --dry-run prints a tick              — false confidence in the one command somebody runs
#                                              BEFORE touching production
#   11  the network loop's anchor renamed    — an unliftable subject, which must be an ERROR and not
#                                              a green run over nothing
#   12  HOST_SENTINEL emptied                — the mechanism itself gone
#   13  a probe renamed out from under
#       part 3's enumeration                 — the list is enumerated, so it must refuse rather than
#                                              silently compare against a probe that is not there
#   14  the shell stripper absent            — one file five checks trust; absent it, part 5 reads
#                                              empty text and reports every call site as routed
#
#  Eight more from the review of `25566a2`, and the first of them is the reason the others exist: part
#  2 drives ONE of the six call sites, and part 5's textual assertion says nothing about the
#  remote-STATUS arms of the other five. Cases 15 and 17 are the two the reviewer reproduced.
#
#   15  the secrets loop's remote-status
#       arm emptied                          — THE FAIL-OPEN, and the only mutation in this file
#                                              whose result is a PASS. grep's exit 2 matches an empty
#                                              branch, the loop walks all twelve values, and
#                                              preflight approves a secrets.env it could not read
#   16  the not-set refusal stops naming
#       which value                          — twelve values are asked one at a time so that the
#                                              refusal can name one
#   17  rollback stops checking the remote
#       status                               — a wrong --path was back to a fact about this estate's
#                                              deployment history. WORSE than the review predicted:
#                                              `cd`'s error is on stderr, host_run captures both, so
#                                              `prev` is non-empty and the mutant rolls back to a tag
#                                              made of an error message
#   18  the previous tag is no longer
#       trimmed                              — `cut` hands back the CR of a CRLF file, and `1.4.0\r`
#                                              is a tag no registry carries
#   19  part 3's lift anchor renamed         — an unliftable subject must be an ERROR, not a green
#                                              run over nothing
#   20  part 4's START anchor renamed        — the same, and `prev` is a PREFIX of `previous`, so the
#                                              obvious rename passed the original-is-gone control
#                                              while having applied
#   21  part 4's END anchor renamed          — a DIFFERENT failure: an awk range whose terminator
#                                              stops matching prints to the END OF THE FILE, so the
#                                              lift is non-empty and part 4 drives the router. Found
#                                              by writing this case; closed with a terminator check
#   22  the subject does not parse           — the awk lifts do not cross a syntax break, so the
#                                              check went green over a file nobody could run
#
#  Eight more for the HEALTH GATE — decisions.md D78, backlog NEW-36. Its 24 polls fold on purpose
#  and its exhaustion is fatal by way of `rollback`, so these pull in both directions: 27 is the
#  cheap "fix" that makes a one-second flake a rolled-back deploy, and the rest are the exhaustion
#  claiming a cause it cannot establish. Cases 23-28 are covered by part 6 and by nothing else,
#  measured: with part 6 cut out of a copy of the check this file reports 25 ok, 6 failed.
#
#   23  the exhaustion arm back to
#       warn-and-roll-back                   — NEW-36 itself: five HEALTHY services named as
#                                              unhealthy and the stack rolled back, whoever the
#                                              silence belonged to
#   24  the state probe's remote-status
#       arm emptied                          — a daemon that could not be asked, reported as services
#                                              that failed, and rolled back on
#   25  the no-containers arm removed        — `ps -a` exits 0 with NO output for a project with none
#                                              (measured), so no status check can see this state
#   26  the healthy-contradiction arm
#       removed                              — the blip: docker's own healthcheck says every service
#                                              is healthy and the deploy reverts it anyway
#   27  a status check moved INSIDE the poll  — the opposite direction, and the one D71 §5 argues
#                                              against: a service that fails one poll and answers the
#                                              next must still pass
#   28  the log read turned into a refusal    — the evidence is not the decision; unreadiness is
#                                              already established, and a daemon that goes quiet one
#                                              round trip later must not stop a correct rollback
#   29  gate_exhausted renamed                — an unliftable subject is an ERROR, not a green run
#                                              over nothing (cases 11, 19, 20 one function along)
#   30  the health column dropped from the
#       state probe                          — the second opinion itself. Part 5's enumeration is
#                                              what stands behind this one, and that is stated at
#                                              the case: the docker stub does not render a format
#                                              string, so part 6 cannot see the column go
#
#  Six more for the EXECUTED call sites — decisions.md D80, backlog NEW-42. Cases 31-42 are the three
#  earlier rounds and each of those was a fail-open in the guard that closed the round before; these
#  are the states no matcher could reach at all. 43 and 44 are the per-site half of SSH_OPTS, aimed at
#  the health gate's own poll because the preamble's line count concedes it cannot see that one; 45
#  and 46 are the two halves of the sourced-vs-executed guard D80 added, and 45 in particular is the
#  defect that change INTRODUCES; 47 is the unliftable subject one function along; 48 was one of the
#  two greps D80 deleted from part 5.
#
#   43  the health gate's poll loses
#       SSH_OPTS                             — the floor counts LINES, so removing the expansion from
#                                              any eleven of the twelve leaves it green
#   44  an option added to SSH_OPTS that one
#       site does not receive                — every text assertion still passes: one assignment, both
#                                              options, the exact byte string. Order swapped in the
#                                              hand-written site so part 5's inline ban does NOT fire
#   45  nothing calls main                   — the script parses, defines everything and exits 0
#                                              having deployed nothing. THE DEFECT D80's OWN CHANGE
#                                              INTRODUCES, and invisible to text and to sourcing
#   46  the sourced-vs-executed guard gone   — sourcing performs a deployment, which is what
#                                              `. ./deploy-prod.sh` did before D80
#   47  the router's function renamed        — an unliftable subject is an ERROR, not a green run over
#                                              nothing (cases 11, 19, 20, 29 at four other names)
#   48  the deploy router passes no phase    — the gate refuses before a probe is sent, so every
#                                              deployment dies at it; seen as a program that never
#                                              reached its polls
#   49  a remote site stops being asked      — part 7's site list must refuse in BOTH directions, so
#                                              a probe that goes away is named rather than passed over
#   50  a seventh remote probe appears       — the direction parts 1-4 structurally cannot see: they
#                                              drive named functions, so an ssh growing beside the six
#                                              is invisible to them
#
#      ./.github/checks/host-probe-attribution-test.sh
# ==============================================================================
set -Eeuo pipefail

HERE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE_DIR/../.." && pwd)"
# Overridable for ONE purpose, and it is this file's own control: point it at a copy of the check
# with an assertion removed and every case that rested on that assertion must report "the check
# PASSED on a broken tree". A harness nobody has watched fail is a harness of nothing, and that is
# as true of this one as of the check it drives.
CHECK="${HC_CHECK:-$HERE_DIR/host-probe-attribution.sh}"
SUBJECT="$ROOT/deploy/deploy-prod.sh"
[[ -f "$CHECK" ]]   || { echo "cannot find $CHECK" >&2; exit 1; }
[[ -f "$SUBJECT" ]] || { echo "cannot find $SUBJECT" >&2; exit 1; }

pass=0; fail=0
note() { printf '  ok   %s\n' "$*"; pass=$((pass + 1)); }
bad()  { printf '  FAIL %s\n' "$*" >&2; fail=$((fail + 1)); }

WORK="$(mktemp -d "${TMPDIR:-/tmp}/hc-host-probe-test-XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

fresh() { # fresh <case> -> prints the path to a pristine copy of the subject
  local d="$WORK/$1"; mkdir -p "$d"
  cp "$SUBJECT" "$d/deploy-prod.sh"
  printf '%s' "$d/deploy-prod.sh"
}
run_check() { # run_check <mutant path> [extra env assignment]
  local f="$1"; shift
  ( cd "$ROOT" && env HC_PROD_SCRIPT="$f" "$@" bash -e "$CHECK" >"$f.out" 2>&1 ) && return 0 || return $?
}

# expect_red <mutant> <name> <must-be-present> <must-be-absent> <error fragment>
#
# The two controls before the run are the point of this file. A `sed` that matched nothing leaves a
# green check reading green for the wrong reason, and a mutant that does not parse makes the check
# ABORT rather than fail — which also looks like red.
expect_red() {
  local f="$1" name="$2" present="$3" absent="$4" want="$5"
  if [[ -n "$present" ]] && ! grep -qF -- "$present" "$f"; then
    bad "$name — the mutation did not apply ('$present' is not in the file)"; return
  fi
  # `LINE:<text>` ASKS FOR AN EXACT LINE, and it exists because two of D80's cases replace a line —
  # `  main` — whose text also occurs inside neighbouring comments, so the substring control would
  # fail on a mutation that had applied perfectly. Spelling it as a multi-line `grep -F` pattern is
  # WORSE than useless and was tried: `-F` splits the pattern on the newline, the empty half then
  # matches every file, and the control passes on a mutation that did nothing. Measured.
  case "$absent" in
    LINE:*)
      # `-F` AS WELL AS `-x`: without it the pattern is a BRE, so a future pattern carrying a `.`, a
      # `[` or a `*` would silently match something else — and the failure mode is a "check PASSED on
      # a broken tree" message about a mutation that had applied, which is a red run with a misleading
      # cause. Both patterns in use today are literal-safe; this closes it for one character. D80's
      # review, note 1.
      if grep -Fqx -- "${absent#LINE:}" "$f"; then
        bad "$name — the mutation did not replace the original (a line reading exactly '${absent#LINE:}' is still in the file)"; return
      fi ;;
    ?*)
      if grep -qF -- "$absent" "$f"; then
        bad "$name — the mutation did not replace the original ('$absent' is still in the file)"; return
      fi ;;
  esac
  if ! bash -n "$f"; then bad "$name — the mutant does not parse, so the check aborted rather than failing"; return; fi
  if run_check "$f"; then
    bad "$name — the check PASSED on a broken tree"
    sed -n '1,120p' "$f.out" >&2
    return
  fi
  if grep -qF -- "$want" "$f.out"; then
    note "$name → red"
  else
    bad "$name — red, but not through the door it was aimed at (no '$want' in the output)"
    grep '::error::' "$f.out" >&2 || true
  fi
}

printf '\nControl: the committed script\n'
f="$(fresh control)"
if run_check "$f"; then note "green on an unmutated copy"; else bad "the check is red on the committed script"; cat "$f.out" >&2; fi

printf '\nhost_run: the sentinel, which is the whole mechanism\n'
f="$(fresh m1)"; sed -i 's|  if \[\[ -z "$line" \]\]; then|  if false; then|' "$f"
expect_red "$f" "1  the sentinel test deleted" 'if false; then' 'if [[ -z "$line" ]]; then' \
  "did not say that no answer came back from a shell"

f="$(fresh m2)"
sed -i 's|^  wrapped="($2".*$|  wrapped="$2"|' "$f"
expect_red "$f" "2  the sentinel never appended" '  wrapped="$2"' 'printf "\\n%s %s\\n"' \
  "did not come back cleanly from a remote command that SUCCEEDED"

# A QUOTED HERE-DOC, NOT A `sed`. That line is four kinds of quote deep — `wrapped="($2"$'\n'')'…`
# — and every sed spelling of it either matched nothing (green for the wrong reason, caught by the
# control above) or needed escaping nobody could read. The replacement is written out verbatim
# instead and swapped in by line, which is the same technique with none of the guessing.
f="$(fresh m3)"
cat > "$WORK/m3.repl" <<'REPL'
  wrapped="$2"$'\n''printf "\n%s %s\n" "'"$HOST_SENTINEL"'" "$?"'
REPL
awk -v file="$WORK/m3.repl" '
  /^  wrapped="\(\$2"/ { while ((getline l < file) > 0) print l; close(file); next }
  { print }' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
expect_red "$f" "3  the far-side subshell removed" '  wrapped="$2"$' 'wrapped="($2"' \
  "reported an ssh failure for a remote command that RAN and exited 255"

printf '\nThe network arm: which cause each refusal names\n'
f="$(fresh m4)"; sed -i 's|      \*"Error response from daemon"\*"not found"\*)|      *)|' "$f"
expect_red "$f" "4  the unanswerable arm folded into the absence arm (NEW-33 itself)" '      *)' \
  '*"Error response from daemon"*"not found"*)' "named the wrong cause"

f="$(fresh m5)"; sed -i 's|      \*"Error response from daemon"\*"not found"\*)|      *"not found"*)|' "$f"
expect_red "$f" "5  the absence match loosened to a bare substring" '      *"not found"*)' \
  '*"Error response from daemon"*"not found"*)' "read a relayed 'command not found' as an absent network"

f="$(fresh m6)"
sed -i 's|This is NOT a statement that the network is missing, and the remedy on the absence arm above is not the remedy here|$net_hint And|' "$f"
expect_red "$f" "6  \$net_hint printed on the unanswerable arm" 'network exists on $HOST, so whether this stack can join it is unestablished. $net_hint And' \
  'the remedy on the absence arm above is not the remedy here' "prints the remedy for the wrong one"

f="$(fresh m7)"
sed -i 's|        die "the .\$net. network does not exist on \$HOST. \$net_hint Do NOT drop it|        die "the '"'"'$net'"'"' network does not exist on $HOST. Do NOT drop it|' "$f"
expect_red "$f" "7  \$net_hint dropped from the absence arm" \
  "network does not exist on \$HOST. Do NOT drop it" '$net_hint Do NOT drop it' \
  "no longer says how to fix it"

# BOTH occurrences, deliberately: the network loop has one and the data tier has another, and an
# address restricted to the first left the second in place — so the "original is gone" control
# failed and reported a mutation that had in fact applied. Deleting both is also the likelier real
# edit ("127 is just another failure").
f="$(fresh m8)"; sed -i 's|^\( *\)(( HOST_STATUS == 127 )) && no_docker_on_host$|\1true|' "$f"
expect_red "$f" "8  the 127 branch deleted, in both loops" '    true' '(( HOST_STATUS == 127 )) && no_docker_on_host' \
  "refused a host with no docker command, but did not say so"

printf '\nThe call sites, and the instrument part 3 rests on\n'
f="$(fresh m9)"
sed -i "s|    host_run \"reach \$HOST over ssh\" 'docker compose version >/dev/null'|    ssh -o BatchMode=yes \"\$HOST\" 'docker compose version >/dev/null' \|\| die \"cannot reach \$HOST\"|" "$f"
expect_red "$f" "9  the ssh gate spells out its own ssh again" 'ssh -o BatchMode=yes "$HOST" ' \
  'host_run "reach $HOST over ssh"' "spelling out their own '-o BatchMode=yes'"

f="$(fresh m10)"
sed -i 's|{ skipped "would ask \$HOST whether the .\$net. network exists — NOT contacted"; continue; }|{ ok "network $net present"; continue; }|' "$f"
expect_red "$f" "10  --dry-run prints a tick" '{ ok "network $net present"; continue; }' \
  'skipped "would ask $HOST whether' "prints a tick under --dry-run"

f="$(fresh m11)"; sed -i 's|^  log "checking host networks"$|  log "checking the host networks"|' "$f"
expect_red "$f" "11  the network loop's anchor renamed" '  log "checking the host networks"' \
  '  log "checking host networks"' "has no host-network loop this check can read"

f="$(fresh m12)"; sed -i 's|^HOST_SENTINEL=.*$|HOST_SENTINEL=""|' "$f"
expect_red "$f" "12  HOST_SENTINEL emptied" 'HOST_SENTINEL=""' 'HOST_SENTINEL="__hc' \
  "declares no non-empty HOST_SENTINEL at the top level"

f="$(fresh m13)"
sed -i "s|\"test -s '\$REMOTE_PATH/\$SECRETS_FILE'\"|\"[ -s '\$REMOTE_PATH/\$SECRETS_FILE' ]\"|" "$f"
expect_red "$f" "13  a probe renamed out from under part 3's enumeration" "[ -s '\$REMOTE_PATH/\$SECRETS_FILE' ]" \
  "\"test -s '\$REMOTE_PATH/\$SECRETS_FILE'\"" "no longer asks the host about secrets.env being there at all"

printf '\nsecrets.env and the previous tag — the five other call sites\n'
# M-c, THE ONLY MUTATION IN THIS FILE WHOSE RESULT IS A PASS RATHER THAN A WRONG MESSAGE. One line,
# it parses, and before part 3 existed the check was 22 ok / exit 0 on it: `grep`'s exit 2 — the
# unreadable-0600 state the code's own comment names as live — matches an empty branch, the loop
# proceeds past all twelve values, and preflight APPROVES a secrets file it could not read. Worse
# than the defect this package fixed, which at least refused.
f="$(fresh m15)"
sed -i 's|^        \*) die "\$HOST:\$REMOTE_PATH/\$SECRETS_FILE could not be read.*|        *) : ;;|' "$f"
expect_red "$f" "15  the secrets loop's remote-status arm emptied (the FAIL-OPEN)" '        *) : ;;' \
  'could not be read while looking for' "walked all twelve values past it"

f="$(fresh m16)"
sed -i 's|1) die "\$v is not set in|1) die "a value is not set in|' "$f"
expect_red "$f" "16  the not-set refusal stops naming which value" 'die "a value is not set in' \
  'die "$v is not set in' "without saying which value is missing"

# M-b. THE REVIEW PREDICTED "no previous deployment recorded" AND THE MUTANT IS WORSE THAN THAT,
# which is worth recording rather than smoothing over: `cd`'s error goes to STDERR, host_run captures
# both streams, so with the status check gone `prev` is non-empty — it is the error text — and the
# emptiness refusal never fires either. The mutant rolls the stack back to a tag made of
# `bash: line 1: cd: /…: No such file or directory`. So the door is the ACCEPTED arm, not the
# wrong-cause one, and the reviewer's prediction assumed an empty answer.
f="$(fresh m17)"
sed -i 's|^  (( HOST_STATUS == 0 )) \\$|  (( 1 == 1 )) \\|' "$f"
expect_red "$f" "17  rollback stops checking the remote status" '  (( 1 == 1 )) \' \
  '  (( HOST_STATUS == 0 )) \' "ACCEPTED a previous tag it could not read"

# A quoted here-doc and an awk line swap again, for case 3's reason: that line is three quoting
# levels deep and every `sed` spelling of it matched nothing, which the mutation-applied control
# caught rather than letting it read as green.
f="$(fresh m18)"
cat > "$WORK/m18.repl" <<'REPL'
  prev="$(printf '%s' "$HOST_OUTPUT")"
REPL
awk -v file="$WORK/m18.repl" '
  /^  prev="\$\(printf/ { while ((getline l < file) > 0) print l; close(file); next }
  { print }' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
# The original-is-gone string names the HOST_OUTPUT pipeline and not the bare `tr`: resolve_tag has
# its own `tr -d '[:space:]'` on the Maven version, so the loose spelling failed the control on a
# mutation that had applied perfectly. Second time in this file that a substring control was wider
# than its subject — see case 20.
expect_red "$f" "18  the previous tag is no longer trimmed" 'prev="$(printf '"'"'%s'"'"' "$HOST_OUTPUT")"' \
  '"$HOST_OUTPUT" | tr -d' "from a .env.previous holding HC_TAG=1.4.0"

f="$(fresh m19)"
sed -i 's|^  log "checking \$REMOTE_PATH/\$SECRETS_FILE on \$HOST"$|  log "checking the file $REMOTE_PATH/$SECRETS_FILE on $HOST"|' "$f"
expect_red "$f" "19  part 3's lift anchor renamed" '  log "checking the file $REMOTE_PATH' \
  '  log "checking $REMOTE_PATH/$SECRETS_FILE on $HOST"' "has no secrets.env block this check can read"

# `tag_before` and not `prev_tag`: the original-is-gone control is a substring grep, and `prev` is a
# prefix of `previous`, so the obvious rename passes it while having applied. Watched doing that.
f="$(fresh m20)"
sed -i 's|^  local prev$|  local tag_before|' "$f"
expect_red "$f" "20  part 4's START anchor renamed" '  local tag_before' \
  '  local prev' "has no previous-tag read this check can read"

# THE END ANCHOR, and it is a different failure from case 20 rather than its mirror. An awk range
# whose terminator stops matching prints to the END OF THE FILE, so the lift is enormous and
# non-empty: the "did it lift anything" guard passes and part 4 drives the rest of the script,
# router included. Found by writing this case, and closed with a terminator check.
f="$(fresh m21)"
sed -i 's|^  (( HOST_STATUS == 0 )) \\$|  (( HOST_STATUS == 0 )) \\|; s|^  \[\[ -n "\$prev" \]\] |  [ -n "$prev" ] |' "$f"
expect_red "$f" "21  part 4's END anchor renamed" '  [ -n "$prev" ] ' \
  '  [[ -n "$prev" ]] ' "does not end at a line carrying"

# A SUBJECT THAT DOES NOT PARSE, which `expect_red` cannot express — it refuses an unparseable
# mutant precisely so a syntax error is never mistaken for a red run. The awk lifts do not cross a
# syntax break, so before the parse guard this check went green over a file nobody could run.
f="$(fresh m22)"
printf '\nthis ) is ( not shell\n' >> "$f"
if bash -n "$f" 2>/dev/null; then
  bad "22  the subject does not parse — the mutation did not apply (the mutant still parses)"
elif run_check "$f"; then
  bad "22  the subject does not parse — the check PASSED over a script nobody could run"
  sed -n '1,40p' "$f.out" >&2
elif grep -qF 'does not parse, so nothing below is a statement' "$f.out"; then
  note "22  the subject does not parse → red"
else
  bad "22  the subject does not parse — red, but not through the door it was aimed at"
  grep '::error::' "$f.out" >&2 || true
fi

printf '\nThe health gate: the polls must keep folding, the exhaustion must not\n'
# CASE 23 IS THE DEFECT ITSELF, PUT BACK. One line, it parses, and it is exactly what the file said
# until D78: an exhausted gate warns about five services and falls through to `rollback`, whoever
# the silence belonged to.
f="$(fresh m23)"
sed -i 's|{ gate_exhausted "\$bad" "\$phase"; return 1; }|{ warn "still unhealthy:$bad"; return 1; }|' "$f"
expect_red "$f" "23  the exhaustion arm back to warn-and-roll-back (NEW-36 itself)" \
  '{ warn "still unhealthy:$bad"; return 1; }' '{ gate_exhausted "$bad" "$phase"; return 1; }' \
  "reported an unreachable host as unhealthy services and fell through to a rollback"

# THE STATE PROBE'S STATUS ARM. Addressed to gate_exhausted's own line range, because `(( HOST_STATUS
# == 0 ))` appears in `rollback` as well — case 17's subject — and a sed matching both would leave
# the original-is-gone control failing on a mutation that had applied. Third time in this file that a
# control has been wider than its subject (cases 8 and 20 are the others).
f="$(fresh m24)"
awk '
  /^gate_exhausted\(\) \{/ { in_fn = 1 }
  in_fn && /could not then be asked what state the services are in/ { print "    || true # MUTATED-24"; next }
  /^\}/ && in_fn { in_fn = 0 }
  { print }' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
expect_red "$f" "24  the state probe's remote-status arm emptied" '|| true # MUTATED-24' \
  'could not then be asked what state the services are in' \
  "ROLLED THE STACK BACK over a daemon it could not ask"

# The host answered and knows of nothing. Measured: `ps -a` exits 0 with empty output there, so the
# status arm above cannot see this state and it is a separate refusal rather than a nicety.
f="$(fresh m25)"
# `%` as the delimiter, not `|`: the line being matched IS an `||`, and `sed` reads the second bar
# as the end of the pattern and then refuses the expression. Caught by running it.
sed -i 's%^    || die "the health gate timed out after ${HEALTH_TIMEOUT}s and docker compose on $HOST reports NO CONTAINERS.*%    || true ## MUTATED-25%' "$f"
expect_red "$f" "25  the no-containers arm removed" '|| true ## MUTATED-25' 'NO CONTAINERS AT ALL' \
  "reported a project with NO CONTAINERS as services that failed to become ready"

# THE BLIP ARM, which is the one that actually stops a healthy estate being rolled back: the polls
# got nothing and docker's own healthcheck — the same readiness probe, run inside the host — says
# every service the gate gave up on is healthy.
f="$(fresh m26)"
sed -i 's|^  if \[\[ -z "\$unproven" \]\]; then$|  if false; then # MUTATED-26|' "$f"
expect_red "$f" "26  the healthy-contradiction arm removed" 'if false; then # MUTATED-26' \
  'if [[ -z "$unproven" ]]; then' \
  "ROLLED BACK an estate whose every service docker itself reports as running and healthy"

# THE OPPOSITE MUTATION, and the reason part 6 has a transient case at all: the cheap reading of
# NEW-36 is "check the status" and doing it INSIDE the loop makes one failed poll a rolled-back
# deploy. D71 §5's rule is that a poll may fold; this is what enforces the half of it that stayed.
f="$(fresh m27)"
sed -i 's%        >/dev/null 2>&1 || bad+=" $s"%        >/dev/null 2>\&1 || die "$s did not answer" ## MUTATED-27%' "$f"
expect_red "$f" "27  a status check moved INSIDE the poll" 'die "$s did not answer" ## MUTATED-27' \
  '|| bad+=" $s"' "DIED on a service that failed its first poll and answered the next"

# And the same rule the other way: the log read is EVIDENCE, so its failure may not become a
# refusal. Unreadiness is established by the probe above it, and a daemon that goes quiet one round
# trip later must not turn a correct rollback into a stopped deploy.
f="$(fresh m28)"
sed -i 's|^    warn "  their logs could not be read (exit \$HOST_STATUS): \$HOST_OUTPUT"$|    die "their logs could not be read" # MUTATED-28|' "$f"
expect_red "$f" "28  the log read turned into a refusal" 'die "their logs could not be read" # MUTATED-28' \
  'their logs could not be read (exit $HOST_STATUS)' \
  "DIED because it could not read the failed services' logs"

# AN UNLIFTABLE SUBJECT MUST BE AN ERROR, not a green run over nothing — cases 11, 19 and 20 one
# function along. Both halves renamed, so the mutant is a script that works perfectly and whose
# subject this check cannot find.
f="$(fresh m29)"
sed -i 's|gate_exhausted|gate_diagnose|g' "$f"
expect_red "$f" "29  gate_exhausted renamed" 'gate_diagnose() {' 'gate_exhausted' \
  "declares no 'gate_exhausted'"

# THE SECOND OPINION ITSELF, dropped as a tidy-up. What stands behind this one is part 5's
# enumeration rather than part 6: the docker stub does not render the format string, so a `ps` that
# no longer asks for {{.Health}} still ANSWERS with it in the harness. Stated rather than papered
# over — the textual site is the guard here, which is what it is for.
f="$(fresh m30)"
sed -i "s|{{.Service}} {{.State}} {{.Health}}|{{.Service}} {{.State}}|" "$f"
# The original-is-gone control names the WHOLE format string and not the bare column: the code's own
# comment argues for {{.Health}} by name, comments are not stripped from the file itself, and the
# looser spelling therefore failed the control on a mutation that had applied. Fourth time in this
# file — see cases 8, 20 and 24.
expect_red "$f" "30  the health column dropped from the state probe" \
  "ps -a --format '{{.Service}} {{.State}}'" "{{.Service}} {{.State}} {{.Health}}" \
  "no longer asks the host about what state the services are in once the health gate has timed out"

printf '\nSSH_OPTS: lifted by the check, and asserted by nothing until review\n'
# CASES 31-33 ARE THE REVIEW FINDING ON D78 — every one of them exited 0 against the shipped check.
# `SSH_OPTS` was lifted into LIFT_GLOBALS with a `|| true` beside it, the function-existence loop did
# not name it, and only HOST_SENTINEL's value was ever read — so the array could lose the timeout,
# lose every option, or be renamed out from under the grep, and part 6 stayed green because the stub
# does not care how many options an `ssh` is handed. That is the half of NEW-36 taken beyond the item
# (136s per connect against 8s, measured) with nothing behind it, and part 5's own success line
# already claimed to cover it.
#
# THE TIMEOUT, which is the one the measurement is about.
f="$(fresh m31)"
sed -i 's|^SSH_OPTS=(-o BatchMode=yes -o "ConnectTimeout=\${HC_SSH_TIMEOUT:-8}")$|SSH_OPTS=(-o BatchMode=yes)|' "$f"
expect_red "$f" "31  SSH_OPTS loses its ConnectTimeout" 'SSH_OPTS=(-o BatchMode=yes)' \
  'ConnectTimeout=${HC_SSH_TIMEOUT:-8}' "carries no ConnectTimeout"

# THE WHOLE ARRAY EMPTIED, which also takes BatchMode with it — so the refusal must be about the
# option part 5's inline ban assumes is here, and the assertions are ordered to say both.
f="$(fresh m32)"
sed -i 's|^SSH_OPTS=(-o BatchMode=yes -o "ConnectTimeout=\${HC_SSH_TIMEOUT:-8}")$|SSH_OPTS=()|' "$f"
expect_red "$f" "32  SSH_OPTS emptied" 'SSH_OPTS=()' 'BatchMode=yes -o "ConnectTimeout' \
  "carries no ConnectTimeout"

# THE ONE THE EXISTING GUARD WAS SHAPED TO CATCH AND DID NOT. Renaming the array leaves the grep
# lifting only the HOST_SENTINEL line, so the sentinel guard passes, `LIFT_GLOBALS` is short by one
# line, and every ssh in the shipped script expands an unset name. Under the stub that is invisible;
# on a host it is the TCP default on all twelve of them.
f="$(fresh m33)"
sed -i 's|SSH_OPTS|SSH_CONNECT_OPTS|g' "$f"
expect_red "$f" "33  SSH_OPTS renamed" 'SSH_CONNECT_OPTS=(-o BatchMode=yes' 'SSH_OPTS' \
  "declares no SSH_OPTS at the top level"

# CASE 34 IS THE REVIEW FINDING ON §13's OWN REPAIR, and the house question that found it: *would a
# comment satisfy this?* It did. The guard read the RAW line and matched by substring, so an array
# carrying neither option printed "carries both BatchMode and a ConnectTimeout" because the substring
# landed in the trailing comment — measured at exit 0. Both lines are lifted from the shell stripper's
# output now, and this case is the only thing standing over that.
f="$(fresh m34)"
sed -i 's%^SSH_OPTS=(-o BatchMode=yes -o "ConnectTimeout=${HC_SSH_TIMEOUT:-8}")$%SSH_OPTS=(-o BatchMode=yes) ## keep -o "ConnectTimeout=..." off while debugging slow links%' "$f"
expect_red "$f" "34  the ConnectTimeout moved into a trailing comment" \
  'off while debugging slow links' 'SSH_OPTS=(-o BatchMode=yes -o "ConnectTimeout' \
  "carries no ConnectTimeout"

printf '\nThe two callers, and the claim each refusal makes about the stack\n'
# CASE 35: the rollback phase's sentences replaced by the deploy phase's, which is what every refusal
# said before D78 §14 — so a gate reached FROM `rollback` tells the operator nothing has been rolled
# back, and offers `--rollback` as the remedy, after a rollback has just run.
f="$(fresh m35)"
awk '
  /^    rollback\)$/ { in_rb = 1; print; next }
  in_rb && /^      left=/ {
    print "      left=\"NOTHING HAS BEEN ROLLED BACK, deliberately. Revert by hand with ./deploy-prod.sh --rollback --host $HOST once the host answers.\" ## MUTATED-35"
    next }
  # The `;;` MUST SURVIVE: without it the arm runs on into the `*)` below and the mutant does not
  # parse, which expect_red refuses rather than counting as red. Caught by running it.
  in_rb && /^      rolling=/ { print "      rolling=\"rolling back.\" ;;"; in_rb = 0; next }
  { print }' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
expect_red "$f" "35  the rollback phase claims nothing was rolled back" '## MUTATED-35' \
  'THE ROLLBACK TO $TAG HAS ALREADY BEEN APPLIED' \
  "claims NOTHING HAS BEEN ROLLED BACK when it was called from"

# CASE 36: the premise the blip arm rests on, and it lives in ANOTHER FILE. Drop the healthcheck that
# makes the same readiness request and docker's health column is blank for every service, so every
# blip becomes an established-unready rollback — NEW-36's own harm — while the stub answers with a
# health column regardless, which is exactly why part 6 alone cannot see it.
f="$(fresh m36)"
cp "$ROOT/deploy/docker/docker-compose.prod.yml" "$WORK/m36-compose.yml"
sed -i 's|/management/health/readiness|/management/health|' "$WORK/m36-compose.yml"
if grep -q '/management/health/readiness' "$WORK/m36-compose.yml"; then
  bad "36  the prod compose stops healthchecking readiness — the mutation did not apply"
elif run_check "$f" HC_PROD_COMPOSE="$WORK/m36-compose.yml"; then
  bad "36  the prod compose stops healthchecking readiness — the check PASSED, so the blip arm's premise is unguarded"
  sed -n '1,40p' "$f.out" >&2
elif grep -qF 'declares no healthcheck making the /management/health/readiness request' "$f.out"; then
  note "36  the prod compose stops healthchecking readiness → red"
else
  bad "36  the prod compose stops healthchecking readiness — red, but not through the door it was aimed at"
  grep '::error::' "$f.out" >&2 || true
fi

printf '\nThe gate'"'"'s two call sites — the binding nothing was looking at\n'
# CASES 37 AND 38 ARE THE THIRD REVIEW'S BLOCKING FINDING. Both swaps parse, and against the commit
# before D78 §15 BOTH the check and this test exited 0 — the no-default guard sees only an absent
# argument, case 35 mutates gate_exhausted's own arm, and part 6 drives the function with call strings
# THIS FILE writes. So the phase mechanism was bound to the program by nothing at all, and case 37
# restores finding 2's defect exactly: a gate exhausted from a revert saying "NOTHING HAS BEEN ROLLED
# BACK" and offering `--rollback` after the rollback has just run.
#
# THEIR DOOR MOVED IN D80, which is the point of that decision rather than a side effect. §15 repaired
# them with two stripped-text greps in part 5 and said at the site that it was a compromise; part 7
# sources the shipped file, calls `main` and `rollback`, and reads the phase off the sentences the
# phase composes — split at the rollback, so a sentence is attributed to the CALLER that produced it.
# Both greps are deleted, so these two `want` fragments are now part 7's messages: if either case ever
# goes "red, but not through the door it was aimed at", the execution is what broke and not a matcher.
f="$(fresh m37)"
sed -i 's|^  health_gate rollback && ok "rolled back to \$prev"|  health_gate deploy \&\& ok "rolled back to $prev"|' "$f"
expect_red "$f" "37  rollback() runs the gate in the deploy phase" \
  'health_gate deploy && ok "rolled back to $prev"' 'health_gate rollback && ok' \
  "rollback() runs the health gate in the DEPLOY phase"

f="$(fresh m38)"
sed -i 's|^  if health_gate deploy && smoke_test; then$|  if health_gate rollback \&\& smoke_test; then|' "$f"
expect_red "$f" "38  the deploy router runs the gate in the rollback phase" \
  '  if health_gate rollback && smoke_test; then' '  if health_gate deploy && smoke_test; then' \
  "DEPLOY router runs the health gate in the ROLLBACK phase"

printf '\nThe value behind the option name, and the assignment bash actually obeys\n'
# CASE 39: the option's NAME with a zero default behind it — measured green before this round, and
# printing "bounded" for an array that hands ssh ConnectTimeout=0 whenever the environment is unset.
# The script's own declaration-time guard cannot see it: that guard validates ${HC_SSH_TIMEOUT:-8},
# its OWN default, so the two defaults diverge with both green.
f="$(fresh m39)"
sed -i 's|ConnectTimeout=${HC_SSH_TIMEOUT:-8}|ConnectTimeout=${HC_SSH_TIMEOUT:-0}|' "$f"
expect_red "$f" "39  the ConnectTimeout default changed to 0" \
  'ConnectTimeout=${HC_SSH_TIMEOUT:-0}' 'ConnectTimeout=${HC_SSH_TIMEOUT:-8}' \
  "names a ConnectTimeout whose value is not"

# CASE 40: a SECOND top-level assignment. This check reads the first with `head -1`; bash obeys the
# last. Appended immediately after the original, which is where a "temporary" edit goes.
f="$(fresh m40)"
awk '
  /^SSH_OPTS=\(-o BatchMode=yes -o "ConnectTimeout=\$\{HC_SSH_TIMEOUT:-8\}"\)$/ {
    print; print "SSH_OPTS=(-o BatchMode=yes) ## MUTATED-40"; next }
  { print }' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
# THE ORIGINAL-IS-GONE CONTROL DOES NOT APPLY HERE and is passed empty deliberately: this mutation
# ADDS a line rather than replacing one, so the original must still be present — that is the whole
# point of it. The mutation-applied control is the marker grep.
expect_red "$f" "40  a second SSH_OPTS assignment, which bash obeys and head -1 never sees" \
  'SSH_OPTS=(-o BatchMode=yes) ## MUTATED-40' '' \
  "and this check reads the first while bash obeys the last"

printf '\nThe compose premise, and the comment that satisfied its guard\n'
# CASE 41: the third comment-satisfies-a-guard on this branch, and the most realistic of them —
# whoever weakens a healthcheck writes down which path they removed. Measured green before this round.
f="$(fresh m41)"
cp "$ROOT/deploy/docker/docker-compose.prod.yml" "$WORK/m41-compose.yml"
sed -i 's|/management/health/readiness|/management/health|' "$WORK/m41-compose.yml"
awk '
  /^ *healthcheck:/ { print; print "    # readiness (/management/health/readiness) dropped: it flapped on slow disks"; next }
  { print }' "$WORK/m41-compose.yml" > "$WORK/m41-compose.tmp" && mv "$WORK/m41-compose.tmp" "$WORK/m41-compose.yml"
if ! grep -q '/management/health/readiness' "$WORK/m41-compose.yml"; then
  bad "41  the readiness path survives only in a comment — the mutation did not apply (the path is gone entirely)"
elif grep -qE "^ +- 'exec 3<>/dev/tcp.*readiness" "$WORK/m41-compose.yml"; then
  bad "41  the readiness path survives only in a comment — the mutation did not apply (a real test still requests it)"
elif run_check "$f" HC_PROD_COMPOSE="$WORK/m41-compose.yml"; then
  bad "41  the readiness path survives only in a comment — the check PASSED, so a YAML comment satisfies the premise guard"
  sed -n '1,40p' "$f.out" >&2
elif grep -qF 'declares no healthcheck making the /management/health/readiness request' "$f.out"; then
  note "41  the readiness path survives only in a comment → red"
else
  bad "41  the readiness path survives only in a comment — red, but not through the door it was aimed at"
  grep '::error::' "$f.out" >&2 || true
fi

# CASE 42: the healthcheck disabled per service with the anchor intact — a blank {{.Health}} for that
# service, which gate_exhausted reads as unproven, so a blink takes it down the rollback arm.
f="$(fresh m42)"
cp "$ROOT/deploy/docker/docker-compose.prod.yml" "$WORK/m42-compose.yml"
awk '
  /^  hc-market-payout:$/ { print; print "    healthcheck:"; print "      disable: true"; next }
  { print }' "$WORK/m42-compose.yml" > "$WORK/m42-compose.tmp" && mv "$WORK/m42-compose.tmp" "$WORK/m42-compose.yml"
if ! grep -qE '^ *disable: *true' "$WORK/m42-compose.yml"; then
  bad "42  one service's healthcheck disabled — the mutation did not apply"
elif run_check "$f" HC_PROD_COMPOSE="$WORK/m42-compose.yml"; then
  bad "42  one service's healthcheck disabled — the check PASSED, so the premise is 'a healthcheck somewhere' rather than 'every service'"
  sed -n '1,40p' "$f.out" >&2
elif grep -qF 'disables a healthcheck somewhere' "$f.out"; then
  note "42  one service's healthcheck disabled → red"
else
  bad "42  one service's healthcheck disabled — red, but not through the door it was aimed at"
  grep '::error::' "$f.out" >&2 || true
fi

printf '\nPer site, and per program — the four states only an executed call site has\n'
# CASES 43-48 ARE D80's, and each is a state that was green through D78's third round. The first two
# are the per-site half: the preamble's floor counts LINES carrying the expansion, so removing it from
# any eleven of the twelve leaves it at two and green, and an option ADDED to the array and not
# reaching a site was invisible to everything. Both mutations are aimed at the health gate's own poll,
# which is NEW-36's second half and the site the floor's own message concedes it cannot see.
#
# AN awk LINE SWAP, not a `sed`, for case 3's reason: that line is four quoting levels deep and ends
# in a continuation, and every sed spelling of it either matched nothing or needed escaping nobody
# could read. The replacement is written out verbatim and swapped in by line.
f="$(fresh m43)"
cat > "$WORK/m43.repl" <<'REPL'
      ssh "$HOST" "cd '$REMOTE_PATH' && $REMOTE_COMPOSE exec -T $(compose_name "$s") bash -c \
REPL
awk -v file="$WORK/m43.repl" '
  /^      ssh "\$\{SSH_OPTS\[@\]\}" "\$HOST" "cd .\$REMOTE_PATH. && \$REMOTE_COMPOSE exec -T/ {
    while ((getline l < file) > 0) print l; close(file); next }
  { print }' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
expect_red "$f" "43  the health gate's own poll loses SSH_OPTS" \
  '      ssh "$HOST" "cd '"'"'$REMOTE_PATH'"'"' && $REMOTE_COMPOSE exec -T' \
  '      ssh "${SSH_OPTS[@]}" "$HOST" "cd '"'"'$REMOTE_PATH'"'"' && $REMOTE_COMPOSE exec -T' \
  "in order, the health gate's own poll"

# CASE 44: THE ARRAY GAINS AN OPTION AND ONE SITE HAND-WRITES THE OLD TWO. Every text assertion in the
# check still passes — one assignment, BatchMode present, the exact ConnectTimeout byte string — and
# the floor counts one line fewer while staying well over two. The order is swapped so that part 5's
# ban on a literal `ssh -o BatchMode=yes` does NOT fire: this case must go red through part 7 or not
# at all, and a red through the neighbour's door would prove nothing about per-site coverage.
f="$(fresh m44)"
sed -i 's|^SSH_OPTS=(-o BatchMode=yes -o "ConnectTimeout=\${HC_SSH_TIMEOUT:-8}")$|SSH_OPTS=(-o BatchMode=yes -o "ConnectTimeout=${HC_SSH_TIMEOUT:-8}" -o ServerAliveInterval=15)|' "$f"
cat > "$WORK/m44.repl" <<'REPL'
      ssh -o "ConnectTimeout=${HC_SSH_TIMEOUT:-8}" -o BatchMode=yes "$HOST" "cd '$REMOTE_PATH' && $REMOTE_COMPOSE exec -T $(compose_name "$s") bash -c \
REPL
awk -v file="$WORK/m44.repl" '
  /^      ssh "\$\{SSH_OPTS\[@\]\}" "\$HOST" "cd .\$REMOTE_PATH. && \$REMOTE_COMPOSE exec -T/ {
    while ((getline l < file) > 0) print l; close(file); next }
  { print }' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
# THE AIM CONTROL READS STRIPPED TEXT, because part 5's ban does. Over the RAW file it fired on a
# mutation that had applied perfectly: this script's own comments quote the old folded
# `ssh -o BatchMode=yes …` line verbatim, six times, so an unstripped control is asserting the length
# of a paragraph — which is the reason the shell stripper exists at all (D68).
if awk -f "$ROOT/.github/checks/strip-sh-comments.awk" "$f" | grep -qF 'ssh -o BatchMode=yes'; then
  bad "44  an option in SSH_OPTS that does not reach one site — the mutation is aimed at the wrong door (part 5 bans that spelling)"
else
  expect_red "$f" "44  an option in SSH_OPTS that does not reach one site" \
    '-o ServerAliveInterval=15' \
    '      ssh "${SSH_OPTS[@]}" "$HOST" "cd '"'"'$REMOTE_PATH'"'"' && $REMOTE_COMPOSE exec -T' \
    "in order, the health gate's own poll"
fi

# CASE 45 IS THE DEFECT D80's OWN CHANGE INTRODUCES, and the reason part 7 executes the file as a
# subprocess as well as sourcing it. With the router in `main`, deleting the one line that calls it
# leaves a script that parses, defines every function and exits 0 having deployed nothing: no output,
# no host contacted, and nothing textual or sourced able to tell it from a working program.
f="$(fresh m45)"
sed -i 's|^  main$|  : ## MUTATED-45|' "$f"
expect_red "$f" "45  nothing calls main, so the script deploys nothing and exits 0" \
  '  : ## MUTATED-45' 'LINE:  main' "asked the host NOTHING"

# CASE 46: THE GUARD REMOVED, so `main` runs on LOAD again — which is the state before D80 and is a
# defect in its own right: `. ./deploy-prod.sh`, typed to read a function, IS a deployment. Here it
# also takes part 7's own mechanism away, so the case is aimed at the inertness assertion.
f="$(fresh m46)"
sed -i 's|^if \[\[ "\${BASH_SOURCE\[0\]}" == "\$0" \]\]; then$|if true; then ## MUTATED-46|' "$f"
expect_red "$f" "46  the sourced-vs-executed guard removed, so sourcing deploys" \
  'if true; then ## MUTATED-46' 'if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then' \
  "A sourced deploy script must not deploy"

# CASE 47: THE ROUTER MOVED OUT FROM UNDER PART 7 — `main` renamed on both lines, so the program works
# perfectly and the harness has nothing to call. An unliftable subject must be an ERROR and not a green
# run over nothing: cases 11, 19, 20 and 29 are the same rule at four other names.
f="$(fresh m47)"
sed -i 's|^main() {$|run_all() {|; s|^  main$|  run_all|' "$f"
expect_red "$f" "47  the router's function renamed" 'run_all() {' 'LINE:main() {' \
  'declares no `main` for part 7 to call'

# CASE 48: THE ROUTER STOPS PASSING A PHASE AT ALL. `health_gate` refuses before a single probe is
# sent (part 6's own case, at the function), so every deployment dies at the gate — which part 7 sees
# as an executed program that never reached its polls. This was one of part 5's deleted greps.
f="$(fresh m48)"
sed -i 's|^  if health_gate deploy && smoke_test; then$|  if health_gate \&\& smoke_test; then|' "$f"
expect_red "$f" "48  the deploy router passes no phase" \
  '  if health_gate && smoke_test; then' '  if health_gate deploy && smoke_test; then' \
  "never got as far as the health gate's polls"

# CASES 49 AND 50 ARE PART 7.4's DERIVED HALF, in both directions. They were measured once by hand
# and that is exactly what this repository keeps finding wrong with itself — a measurement nobody can
# re-run is a claim — so both are cases. 49 removes a site: `record_success` stops appending, and the
# check must say WHICH site stopped being asked rather than passing over nineteen it still sees. 50
# adds a SEVENTH probe, which is the thing parts 1-4 structurally cannot see: they drive named
# functions, so an ssh that grows beside the six is invisible to them.
f="$(fresh m49)"
sed -i 's|>> deployments.log"|> /dev/null"|' "$f"
expect_red "$f" "49  a remote site stops being asked" '> /dev/null"' '>> deployments.log"' \
  "no longer asks the host"

f="$(fresh m50)"
awk '/^  log "rolling services"$/ { print "  ssh \"${SSH_OPTS[@]}\" \"$HOST\" \"uptime\"" } { print }' \
  "$f" > "$f.tmp" && mv "$f.tmp" "$f"
# THE ORIGINAL-IS-GONE CONTROL DOES NOT APPLY and is passed empty, as in case 40: this mutation ADDS
# a probe rather than replacing one, so every original line must still be there.
expect_red "$f" "50  a seventh remote probe grows beside the six" \
  'ssh "${SSH_OPTS[@]}" "$HOST" "uptime"' '' \
  "made a remote invocation part 7 does not recognise"

# THE ONE CASE THAT MUTATES THE CHECK'S OWN INSTRUMENT rather than the subject. Absent the shell
# stripper, part 5 reads empty text: every `grep -F` finds nothing, so every site is reported
# missing and — before the guard — every count came back 0, which reads as "no bare ssh anywhere".
# Since D78 §14 the guard is HOISTED and fatal, because the SSH_OPTS value check reads stripped text
# too and a trailing comment satisfied it otherwise — so the door this case goes through is the early
# refusal rather than part 5's own branch, which no longer exists.
f="$(fresh m14)"
if run_check "$f" HC_STRIP_SH=/nonexistent/strip.awk; then
  bad "14  the shell stripper absent — the check PASSED with no stripper, so part 3 read nothing and called it clean"
  sed -n '1,120p' "$f.out" >&2
elif grep -qF 'is missing, so nothing below could strip comments' "$f.out"; then
  note "14  the shell stripper absent → red"
else
  bad "14  the shell stripper absent — red, but not through the door it was aimed at"
  grep '::error::' "$f.out" >&2 || true
fi

# THE COUNTS ARE DERIVED, so a case added or removed cannot leave a number behind (D78 §14). `cases`
# counts the numbered states this file names; `pass` is what actually reported, control included.
# DISTINCT case numbers, from the naming convention every case follows: a quote, the number, two
# spaces, then a non-digit. Narrower spellings under-counted (a `[a-z$]` class missed the cases whose
# names begin `--dry-run` or an escaped `$net_hint`, answering 27 and then 30) and a wider one caught
# the prose `"127 is just another failure"` in a comment. Derived and printed rather than asserted,
# because the number in this file's own header was wrong twice.
cases="$(grep -oE '"[0-9]+  [^"0-9]' "${BASH_SOURCE[0]}" | grep -oE '[0-9]+' | sort -un | wc -l)"
printf '\n%s ok, %s failed  (%s numbered mutations in this file, plus the green control)\n' \
  "$pass" "$fail" "$cases"
(( fail == 0 ))
