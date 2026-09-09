#!/usr/bin/env bash
# ==============================================================================
#  quality/startup.sh must never mint a second erasure pepper — decisions.md D35
#
#  THE REGRESSION THIS CATCHES, which is a data defect and not a script defect:
#
#      HC_PRIVACY_PEPPER=… ./startup.sh --local     # env wins, nothing is written down
#      ./startup.sh --local                         # the file is still absent, so it GENERATES one
#
#  The second run hands a brand-new random pepper to a stack whose erased_subject rows were written
#  under the first one. Nothing turns red. Messaging starts, because ErasureRegisterGuard detects an
#  ABSENT pepper and this one is present; D35 records that a changed pepper "looks exactly like a
#  right one until something fails to match". Every alias in the register is orphaned from then on,
#  and nothing re-keys an alias once written.
#
#  THE SECOND REGRESSION, added by decisions.md D65 / backlog NEW-27:
#
#      cd $(git worktree add …)/quality && ./startup.sh --local
#
#  Both secret files are gitignored, so a worktree, a clone or a `cp -r` has neither of them while
#  the volumes they belong to are the same volumes. That reaches the identical `else` from the
#  identical three facts — no file, no variable, so generate — and the identical alias orphaning
#  follows. resolve_secret asks docker whether this compose project's volumes exist and refuses.
#
#  It tests the bytes in quality/startup.sh rather than a copy of them: the resolve_secret function
#  is extracted from the file and evaluated here. Sourcing the whole script is not an option — it
#  ends by deploying a stack — and restating the logic would test this file against itself. The
#  docker probe it calls is stubbed for those cases, so they need no daemon and touch no volume,
#  and then exercised for real against a throwaway project of its own at the end — a stub agreeing
#  with a stub proves nothing about what docker actually answers.
#
#      ./.github/checks/quality-pepper-persistence-test.sh
# ==============================================================================
set -Eeuo pipefail

HERE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STARTUP="$(cd "$HERE_DIR/../.." && pwd)/quality/startup.sh"
[[ -f "$STARTUP" ]] || { echo "cannot find $STARTUP" >&2; exit 1; }

pass=0; fail=0
check() { # check <name> <actual> <expected>
  if [[ "$2" == "$3" ]]; then printf '  ok   %s\n' "$1"; pass=$((pass + 1));
  else printf '  FAIL %s\n       expected: %s\n       actual:   %s\n' "$1" "$3" "$2" >&2; fail=$((fail + 1)); fi
}

# The function under test, lifted verbatim, with the colour and logging helpers it calls stubbed.
# ACTION and PROJECT are the other globals it reads: a teardown is allowed to proceed where an `up`
# is refused, and PROJECT is the compose project whose volumes answer "is this a first run".
#
# project_volumes is stubbed AFTER the eval, so the later definition wins and the real one never
# runs here. VOLUMES is what the stub prints — empty for a box that has never run this stack, one
# name per line for one that has. Nothing about that is a hook in the script: resolve_secret calls
# a function, and a test is entitled to answer for it. What it must not do is stand in for the
# probe everywhere, which is what §6 exists to stop.
harness() {
  local sandbox="$1"; shift
  (
    set -Eeuo pipefail
    HERE="$sandbox"
    ACTION="${ACTION:-up}"
    PROJECT="${PROJECT:-hc-market-quality}"
    log() { printf 'log: %s\n' "$*"; }
    ok() { :; }
    warn() { printf 'warn: %s\n' "$*"; }
    die() { printf 'die: %s\n' "$*" >&2; exit 3; }
    eval "$(sed -n '/^resolve_secret() {/,/^}/p' "$STARTUP")"
    project_volumes() {
      [[ "${VOLUMES_UNREADABLE:-}" == "yes" ]] && return 2
      [[ -n "${VOLUMES:-}" ]] && printf '%s\n' "$VOLUMES"
      return 0
    }
    resolve_secret
    # The pepper is printed because assertions compare it; the signing key deliberately is not.
    # Every one of these is a throwaway generated into a mktemp directory, but this repository is
    # public and so are its CI logs, and a key-shaped string in one invites the wrong question.
    printf 'PEPPER=%s\n' "$HC_PRIVACY_PEPPER"
  )
}

sandbox() { mktemp -d "${TMPDIR:-/tmp}/hc-pepper-test-XXXXXX"; }

echo "quality/startup.sh — erasure pepper persistence"

# 1. THE DEFECT ITSELF. An environment value must be written down the first time it is seen, so the
#    next run without it reads the same pepper instead of inventing one.
s="$(sandbox)"
out1="$(HC_PRIVACY_PEPPER=first-run-pepper harness "$s")"
check "an env-provided pepper is persisted" "$(cat "$s/.privacy-pepper" 2>/dev/null || echo '<no file written>')" "first-run-pepper"
out2="$(harness "$s")"
check "the next run without the variable reads the same pepper" \
  "$(printf '%s\n' "$out2" | sed -n 's/^PEPPER=//p')" "first-run-pepper"
rm -rf "$s"

# 2. Two different non-empty values is not a precedence question. One of them matches the aliases in
#    the volumes and this script cannot tell which, so it must refuse rather than choose.
s="$(sandbox)"; printf '%s' "stored-pepper" > "$s/.privacy-pepper"
rc=0; err="$(HC_PRIVACY_PEPPER=different-pepper harness "$s" 2>&1 >/dev/null)" || rc=$?
check "a conflicting env value is fatal" "$rc" "3"
case "$err" in *"differs from"*) r=names ;; *) r="$err" ;; esac
check "…and the refusal explains which two values disagree" "$r" "names"
rm -rf "$s"

# 3. A teardown must not be refused: dropping the volumes is how the operator resolves case 2, and
#    `down`/`clean` write no alias.
s="$(sandbox)"; printf '%s' "stored-pepper" > "$s/.privacy-pepper"
rc=0; out="$(ACTION=clean HC_PRIVACY_PEPPER=different-pepper harness "$s")" || rc=$?
check "a teardown with a conflicting value still runs" "$rc" "0"
check "…using the stored pepper" "$(printf '%s\n' "$out" | sed -n 's/^PEPPER=//p')" "stored-pepper"
rm -rf "$s"

# 4. The two paths that were already correct, kept so a fix cannot pass by refusing everything.
s="$(sandbox)"; printf '%s' "stored-pepper" > "$s/.privacy-pepper"
check "the file is used when the variable is unset" \
  "$(harness "$s" | sed -n 's/^PEPPER=//p')" "stored-pepper"
rm -rf "$s"

#    A GENUINE FIRST RUN — no file, no variable, and NO VOLUMES. This is the case a strict fix
#    breaks and the one nobody tests in anger: somebody standing up a new box, whose stack cannot
#    start at all if this refuses. It must still generate, persist and reuse, exactly as before D65.
s="$(sandbox)"
first="$(harness "$s" | sed -n 's/^PEPPER=//p')"
second="$(harness "$s" | sed -n 's/^PEPPER=//p')"
check "a genuine first run generates a pepper, persists it and reuses it" "$first" "$second"
check "…and it is not empty" "$([[ -n "$first" ]] && echo yes || echo no)" "yes"
check "…and it wrote the file rather than only holding a value" \
  "$([[ -s "$s/.privacy-pepper" ]] && echo yes || echo no)" "yes"
rm -rf "$s"

# 5. NEW-27 / D65. The same three facts as the first run above, plus one more: this compose
#    project's volumes are already there. The directory says first run and docker says otherwise,
#    and docker is the one that knows.
s="$(sandbox)"
rc=0; err="$(VOLUMES="hc-market-quality_booking-data
hc-market-quality_catalog-data" harness "$s" 2>&1 >/dev/null)" || rc=$?
check "no pepper here but the volumes exist is fatal" "$rc" "3"
check "…and nothing was written before refusing" \
  "$(ls -A "$s")" ""
case "$err" in *"volumes already exist"*) r=names ;; *) r="$err" ;; esac
check "…and the refusal names the situation" "$r" "names"
case "$err" in *"--clean"*) r=says ;; *) r="$err" ;; esac
check "…and tells the operator what to do about it" "$r" "says"
rm -rf "$s"

#    ONE volume is the same answer as five, and the case exists because the assertion above could
#    not tell. Fed two volumes and only two, a COUNTING regression passes: `[[ -n "$volumes" ]]`
#    rewritten as `(( $(… | grep -c .) >= 2 ))` keeps every other assertion in this file green.
#    What that costs is an `up` in a worktree against a PARTIALLY torn-down estate — one surviving
#    volume — falling through to the generate arm, where the volumes check suppresses the *write*
#    and not the *use*: the stack starts on a throwaway pepper against live erased_subject rows,
#    and the pepper is not even on disk afterwards to recover from. Every other single-volume case
#    in this file is a teardown or has a settled pepper, so none of them sees it.
s="$(sandbox)"
rc=0; err="$(VOLUMES="hc-market-quality_booking-data" harness "$s" 2>&1 >/dev/null)" || rc=$?
check "ONE volume is as fatal as five" "$rc" "3"
case "$err" in *"volumes already exist"*) r=names ;; *) r="$err" ;; esac
check "…and refuses for the same stated reason" "$r" "names"
check "…having written nothing" "$(ls -A "$s")" ""
rm -rf "$s"

#    A teardown is still allowed through — dropping the volumes is the remedy, and `down`/`clean`
#    write no alias. What it must not do is write the pepper it invented down: a file here would be
#    adopted by the next `up` in this same directory without ever asking docker again.
for a in down clean; do
  s="$(sandbox)"
  rc=0; out="$(ACTION="$a" VOLUMES="hc-market-quality_booking-data" harness "$s")" || rc=$?
  check "a '$a' against existing volumes still runs" "$rc" "0"
  check "…with a pepper for compose to interpolate" \
    "$([[ -n "$(printf '%s\n' "$out" | sed -n 's/^PEPPER=//p')" ]] && echo yes || echo no)" "yes"
  check "…and writes no pepper file" "$(ls -A "$s" | grep -c privacy-pepper || true)" "0"
  rm -rf "$s"
done

#    The generate arm re-checks the teardown itself, and THAT assertion is structural because the
#    line is unreachable while the guard above works — which is the whole reason it is there.
#    Reaching it behaviourally would mean defeating the guard first, and a check that defeats the
#    thing it is checking asserts nothing.
#
#    It is not a count. resolve_secret legitimately tests for a teardown three times — to let one
#    past the refusal, to tolerate a conflicting environment value during one, and this — so a
#    number here would be opaque, and would go red for two of the three reasons wrongly. It matches
#    the guard's OWN refusal instead, on the line after the test, with comments stripped by the same
#    `grep -vE '^[[:space:]]*#'` build.yml already uses on this file. Deleting the guard deletes the
#    message; rewording the message is a false red, which is the safe direction.
guarded="$(sed -n '/^resolve_secret() {/,/^}/p' "$STARTUP" | grep -vE '^[[:space:]]*#' \
           | grep -A1 '"\$ACTION" == "down"' | grep -c 'internal: about to use a throwaway' || true)"
check "the throwaway-pepper arm re-checks the teardown rather than trusting the guard above" \
  "$guarded" "1"

#    An operator who has said which pepper is correct is not guessing, so the volumes are beside
#    the point and the environment value is persisted exactly as in case 1.
s="$(sandbox)"
rc=0; HC_PRIVACY_PEPPER=named-by-the-operator VOLUMES="hc-market-quality_booking-data" harness "$s" >/dev/null || rc=$?
check "an env-provided pepper is not refused because volumes exist" "$rc" "0"
check "…and is still persisted" "$(cat "$s/.privacy-pepper" 2>/dev/null || echo '<no file written>')" "named-by-the-operator"
rm -rf "$s"

#    And a pepper already on disk here settles it whatever docker says.
s="$(sandbox)"; printf '%s' "stored-pepper" > "$s/.privacy-pepper"
check "a stored pepper is used even with volumes present" \
  "$(VOLUMES="hc-market-quality_booking-data" harness "$s" | sed -n 's/^PEPPER=//p')" "stored-pepper"
rm -rf "$s"

#    "Docker did not answer" and "there is nothing there" are two readings of an empty list, and
#    only one of them is safe to generate a pepper on. The probe returns non-zero for the first.
s="$(sandbox)"
rc=0; err="$(VOLUMES_UNREADABLE=yes harness "$s" 2>&1 >/dev/null)" || rc=$?
check "an unanswerable docker is fatal rather than assumed empty" "$rc" "3"
check "…and nothing was written" "$(ls -A "$s")" ""
rm -rf "$s"

#    The signing key keeps the opposite treatment deliberately: a new one costs everybody a fresh
#    sign-in and orphans nothing, so it generates and says so rather than refusing.
s="$(sandbox)"; printf '%s' "stored-pepper" > "$s/.privacy-pepper"
rc=0; out="$(VOLUMES="hc-market-quality_gateway-data" harness "$s" 2>&1)" || rc=$?
check "a missing signing key against existing volumes is not fatal" "$rc" "0"
check "…the key is written" "$([[ -s "$s/.jwt-secret" ]] && echo yes || echo no)" "yes"
case "$out" in *"warn: generated a NEW signing key"*) r=warns ;; *) r="$out" ;; esac
check "…and it warns that every token in circulation stops working" "$r" "warns"
rm -rf "$s"

# 6. THE PROBE ITSELF, against a real daemon. Everything above answers for docker; this asks it.
#    Its own throwaway compose project, its own volumes, created and removed here — it never looks
#    at hc-market-quality's, so running this cannot depend on, or disturb, a live stack.
echo
if ! docker info >/dev/null 2>&1; then
  echo "  SKIP project_volumes against a real docker daemon — no daemon reachable here."
  echo "       Every case above answered for docker with a stub, so nothing has established what"
  echo "       'docker volume ls' actually returns. Run this on a machine with docker."
  fail=$((fail + 0))
else
  # Guarded, because every case above stubs this function: if the extraction comes back empty the
  # probe is simply missing from startup.sh, and an unguarded run reports `command not found` and
  # aborts at 127 rather than saying which of the two things is wrong.
  if ! sed -n '/^project_volumes() {/,/^}/p' "$STARTUP" | grep -q 'docker volume ls'; then
    echo "  FAIL no project_volumes() calling 'docker volume ls' in $STARTUP" >&2
    echo "       Every case above answers for that function with a stub, so without it this file" >&2
    echo "       asserts a fix that is not there. See decisions.md D65." >&2
    fail=$((fail + 1))
    printf '\n%s passed, %s failed\n' "$pass" "$fail"; exit 1
  fi
  probe() { ( set -Eeuo pipefail; PROJECT="$1"
              eval "$(sed -n '/^project_volumes() {/,/^}/p' "$STARTUP")"; project_volumes ) }
  tp="hc-pepper-probe-$$"
  # On an EXIT trap, not at the end: an assertion aborting here would otherwise leave five volumes
  # on the host, and the next run of this file would find them and report a stale $$ as its subject.
  # Watched happen. It removes only the names it created, and never touches a real project's.
  made=()
  cleanup_probe_volumes() { [[ ${#made[@]} -gt 0 ]] && docker volume rm "${made[@]}" >/dev/null 2>&1; return 0; }
  trap cleanup_probe_volumes EXIT
  mkvol() { docker volume create "$@" >/dev/null; made+=("${!#}"); }

  mkvol --label "com.docker.compose.project=$tp" "${tp}_labelled"
  mkvol "${tp}_handmade"                        # no label: compose would still adopt it by name
  mkvol --label "com.docker.compose.project=$tp" "renamed-by-compose-$$"
  mkvol "${tp}-decoy2_data"                     # NOT this project: the prefix without the underscore
  mkvol --label "com.docker.compose.project=${tp}-decoy" "zz-decoy-$$"

  found="$(probe "$tp" | tr '\n' ' ')"
  check "the probe finds a labelled volume" \
    "$(case "$found" in *"${tp}_labelled"*) echo yes ;; *) echo "no ($found)" ;; esac)" "yes"
  check "…and one carrying the project's prefix with no label at all" \
    "$(case "$found" in *"${tp}_handmade"*) echo yes ;; *) echo "no ($found)" ;; esac)" "yes"
  check "…and one the label claims and the name does not" \
    "$(case "$found" in *"renamed-by-compose-$$"*) echo yes ;; *) echo "no ($found)" ;; esac)" "yes"
  check "…and does not claim a project whose name merely starts the same way" \
    "$(case "$found" in *decoy*) echo "no ($found)" ;; *) echo yes ;; esac)" "yes"
  check "a project with nothing of its own reads as a first run" \
    "$(probe "hc-pepper-absent-$$")" ""
  #    …and reads as one by EXITING ZERO, which is the whole of it. An empty list is the answer for
  #    a brand new box and a non-zero return is how the probe says it could not ask, so a function
  #    that conflates them refuses the one estate it must let through. The first version of it did:
  #    `grep -v '^$'` exits 1 having matched nothing, startup.sh sets pipefail, and a genuine first
  #    run died with "could not ask docker". Six mutations of the fix went red in §1–§5 and that one
  #    did not, because everything up there answers for docker with a stub. Assert the exit code.
  rc=0; probe "hc-pepper-absent-$$" >/dev/null || rc=$?
  check "…and says so by succeeding, not by failing with an empty answer" "$rc" "0"
  rc=0; DOCKER_HOST=unix:///nonexistent-hc-pepper.sock probe "$tp" >/dev/null 2>&1 || rc=$?
  check "and an unreachable daemon is an error, not an empty answer" \
    "$([[ "$rc" != 0 ]] && echo nonzero || echo "zero")" "nonzero"

  #    An EMPTY project name is refused rather than asked about. Asked, the daemon answers *nothing
  #    here* to both filters at rc 0 — measured, and the reason this is a real case rather than
  #    defensive noise: it is indistinguishable from a first run, so an empty PROJECT would generate
  #    a pepper against whatever is running. Unreachable while line 70 sets PROJECT unconditionally.
  rc=0; out="$(probe "" 2>/dev/null)" || rc=$?
  check "an empty project name is refused, not answered" \
    "$([[ "$rc" != 0 ]] && echo nonzero || echo "zero, returned [$out]")" "nonzero"

  cleanup_probe_volumes; trap - EXIT
  echo "  (removed the ${tp} throwaway volumes)"
fi

printf '\n%s passed, %s failed\n' "$pass" "$fail"
exit $(( fail > 0 ))
