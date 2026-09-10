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
#  TWENTY-TWO MUTATIONS. Trust the list and not the number — this repository has written such a count
#  wrong four times, twice in the commit that was correcting the same defect elsewhere.
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
  if [[ -n "$absent" ]] && grep -qF -- "$absent" "$f"; then
    bad "$name — the mutation did not replace the original ('$absent' is still in the file)"; return
  fi
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

# THE ONE CASE THAT MUTATES THE CHECK'S OWN INSTRUMENT rather than the subject. Absent the shell
# stripper, part 3 reads empty text: every `grep -F` finds nothing, so every site is reported
# missing and — before the guard — every count came back 0, which reads as "no bare ssh anywhere".
f="$(fresh m14)"
if run_check "$f" HC_STRIP_SH=/nonexistent/strip.awk; then
  bad "14  the shell stripper absent — the check PASSED with no stripper, so part 3 read nothing and called it clean"
  sed -n '1,120p' "$f.out" >&2
elif grep -qF 'is missing, so part 5 could not strip comments' "$f.out"; then
  note "14  the shell stripper absent → red"
else
  bad "14  the shell stripper absent — red, but not through the door it was aimed at"
  grep '::error::' "$f.out" >&2 || true
fi

printf '\n%s ok, %s failed\n' "$pass" "$fail"
(( fail == 0 ))
