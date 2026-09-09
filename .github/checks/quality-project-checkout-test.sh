#!/usr/bin/env bash
# ==============================================================================
#  quality/startup.sh must refuse to start a project another checkout created — decisions.md D67
#
#  THE STATE THIS CATCHES, measured on this host on 2026-09-09 rather than imagined:
#
#      $ docker compose ls
#      hc-market-quality  …/hc-market/quality/compose.yml,
#                         …/hc-market/.claude/worktrees/agent-a3d048…/quality/compose.yml
#
#  One project, two config files. The five application containers were created from the main
#  checkout; the five DATABASES from a git worktree — which has since been pruned, so half the live
#  stack records its provenance as a directory that does not exist.
#
#  The label is only the evidence. env_for_compose sets SEED_DIR="$ROOT/deploy/demo" and compose.yml
#  binds it into catalog, booking, messaging and payout, so an `up` from a worktree repoints four
#  LIVE containers at a host directory that vanishes when that worktree is pruned. Two packages in a
#  row (D64's successor and D66) identified this and declined to restart the stack because of it.
#
#  And a warning would not have been enough, which is the measured fact this whole check rests on:
#  `compose up` recreates — and relabels — only the services whose configuration CHANGED. Measured
#  on a throwaway project: `up` from a second directory recreated the one service whose bind mount
#  moved and reported the other as `Running`, leaving it labelled with the first directory. So a
#  wrong-place `up` SPLITS the project, and no later `up` from the right place puts it back.
#
#  It tests the bytes in quality/startup.sh rather than a copy of them: check_project_checkout is
#  extracted from the file and evaluated here, exactly as quality-pepper-persistence-test.sh does
#  with resolve_secret. Sourcing the whole script is not an option — it ends by deploying a stack.
#  The docker probe it calls is stubbed for §1–§5, so those need no daemon and touch no container,
#  and then exercised for real against a throwaway project of its own in §7: a stub agreeing with a
#  stub establishes nothing about what `docker ps` answers.
#
#      ./.github/checks/quality-project-checkout-test.sh
# ==============================================================================
set -Eeuo pipefail

HERE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE_DIR/../.." && pwd)"
STARTUP="$REPO/quality/startup.sh"
[[ -f "$STARTUP" ]] || { echo "cannot find $STARTUP" >&2; exit 1; }

pass=0; fail=0
check() { # check <name> <actual> <expected>
  if [[ "$2" == "$3" ]]; then printf '  ok   %s\n' "$1"; pass=$((pass + 1));
  else printf '  FAIL %s\n       expected: %s\n       actual:   %s\n' "$1" "$3" "$2" >&2; fail=$((fail + 1)); fi
}

# The function under test, lifted verbatim, with the colour helpers it calls stubbed. HERE and
# PROJECT are the globals it reads: HERE is the checkout the operator is standing in, PROJECT the
# compose project whose container labels answer "and which one created this stack".
#
# project_container_config_files is stubbed AFTER the eval, so the later definition wins and the real
# one never runs here. ROWS is what the stub prints — one `<name><tab><config files>` line per
# container, empty for a host that has never run this stack. Nothing about that is a hook in the
# shipped script: check_project_checkout calls a function, and a test is entitled to answer for it.
# What it must not do is stand in for the probe everywhere, which is what §7 exists to stop.
harness() {
  local here="$1"; shift
  (
    set -Eeuo pipefail
    HERE="$here"
    ROOT="$(dirname "$here")"
    PROJECT="${PROJECT:-hc-market-quality}"
    ok()  { printf 'ok: %s\n' "$*"; }
    die() { printf 'die: %s\n' "$*" >&2; exit 3; }
    eval "$(sed -n '/^check_project_checkout() {/,/^}/p' "$STARTUP")"
    project_container_config_files() {
      [[ "${ROWS_UNREADABLE:-}" == "yes" ]] && return 2
      [[ -n "${ROWS:-}" ]] && printf '%s\n' "$ROWS"
      return 0
    }
    check_project_checkout
  )
}

echo "quality/startup.sh — the project belongs to one checkout"

# A real directory, so `$HERE/compose.yml` is a plausible path and the "does not exist" annotation
# in §4 is about the OTHER checkout rather than about this one by accident.
here="$(mktemp -d "${TMPDIR:-/tmp}/hc-checkout-test-XXXXXX")"
mkdir -p "$here/quality"; : > "$here/quality/compose.yml"
MINE="$here/quality/compose.yml"
gone="$here/pruned-worktree/quality/compose.yml"     # deliberately never created

# 1. A GENUINE FIRST RUN. No containers at all, which is the case a strict fix breaks and the one
#    nobody tests in anger: somebody standing up a new box, whose stack cannot start if this
#    refuses. It must proceed, and say why it is entitled to.
rc=0; out="$(harness "$here/quality")" || rc=$?
check "no containers at all is a first run and proceeds" "$rc" "0"
case "$out" in *"first run"*) r=says ;; *) r="$out" ;; esac
check "…and says so rather than passing silently" "$r" "says"

# 2. THE HAPPY PATH, kept so a fix cannot pass by refusing everything.
rc=0; out="$(ROWS="$(printf 'hc-market-quality-gateway\t%s\nhc-market-quality-gateway-db\t%s' "$MINE" "$MINE")" \
             harness "$here/quality")" || rc=$?
check "every container created from this checkout proceeds" "$rc" "0"

# 3. ONE container created elsewhere is enough. The exact set is required, not "this checkout is
#    among them" — see §5 for why the looser reading is worthless.
rc=0; err="$(ROWS="$(printf 'hc-market-quality-gateway\t%s\nhc-market-quality-gateway-db\t%s' "$MINE" "$gone")" \
             harness "$here/quality" 2>&1 >/dev/null)" || rc=$?
check "one container created elsewhere is fatal" "$rc" "3"
case "$err" in *"$MINE"*) r=names ;; *) r="$err" ;; esac
check "…and the refusal names this checkout" "$r" "names"
case "$err" in *"$gone"*) r=names ;; *) r="$err" ;; esac
check "…and the other one" "$r" "names"
case "$err" in *"hc-market-quality-gateway-db"*) r=names ;; *) r="$err" ;; esac
check "…and which container disagrees, not merely that one does" "$r" "names"
case "$err" in *"--down"*) r=says ;; *) r="$err" ;; esac
check "…and tells the operator what to do about it" "$r" "says"

# 4. A PATH THAT NO LONGER EXISTS is called out, because it changes the remedy: the first way out
#    the message offers is "run it from there", and there is no there. This is today's real state.
rc=0; err="$(ROWS="$(printf 'hc-market-quality-gateway-db\t%s' "$gone")" \
             harness "$here/quality" 2>&1 >/dev/null)" || rc=$?
check "a config file that has been pruned away is still fatal" "$rc" "3"
case "$err" in *"does not exist on this host"*) r=flags ;; *) r="$err" ;; esac
check "…and is flagged as gone rather than merely listed" "$r" "flags"

#    …and the annotation is about the OTHER checkout, not a blanket suffix on every row. A version
#    that annotated unconditionally would satisfy the assertion above while telling the operator
#    that the directory they are standing in does not exist.
rc=0; err="$(ROWS="$(printf 'hc-market-quality-gateway\t%s\nhc-market-quality-gateway-db\t%s' "$MINE" "$gone")" \
             harness "$here/quality" 2>&1 >/dev/null)" || rc=$?
check "…and only on the row whose file is missing" \
  "$(printf '%s\n' "$err" | grep -c 'does not exist on this host' || true)" "1"

# 5. TODAY'S STATE, and the reason the exact set is required. Half the containers name this checkout
#    and half name another: the looser reading — "this checkout is among the ones docker names" — is
#    satisfied by precisely the state this guard exists to end, and it is the state that survived
#    several `up`s from the right place because compose relabels only what it recreates.
rows="hc-market-quality-catalog	$MINE
hc-market-quality-catalog-db	$gone"
rc=0; err="$(ROWS="$rows" harness "$here/quality" 2>&1 >/dev/null)" || rc=$?
check "a split project is fatal even though this checkout is one of the two" "$rc" "3"

# 6. "Docker did not answer" and "there is nothing there" are two readings of an empty list, and only
#    one of them is safe to start a stack on. The probe returns non-zero for the first.
rc=0; err="$(ROWS_UNREADABLE=yes harness "$here/quality" 2>&1 >/dev/null)" || rc=$?
check "an unanswerable docker is fatal rather than assumed empty" "$rc" "3"
case "$err" in *"could not ask docker"*) r=says ;; *) r="$err" ;; esac
check "…and says which question went unanswered" "$r" "says"
rm -rf "$here"

# 6b. ONLY `up` REACHES THE GUARD, and that is structural rather than behavioural: down, clean and
#     verify exit at the router before `step "Preflight"` ever runs. `down` is the remedy this
#     refusal recommends — refusing it would refuse the remedy along with the mistake, D65 §6 one
#     guard along — so this asserts the ordering that makes it exempt, not a flag inside the guard.
#     Line numbers, because "the call is after preflight" is exactly the claim.
#     `|| x=""` on both, and that is not defensive noise: `grep` exits 1 having matched nothing,
#     this file sets `pipefail`, so the assignment fails and `set -e` aborts the whole run — with no
#     output, which reads as no failures. Watched happen, on the mutation that deletes the call.
pre_ln="$(grep -n '^step "Preflight"' "$STARTUP" | head -1 | cut -d: -f1)" || pre_ln=""
call_ln="$(grep -n '^check_project_checkout$' "$STARTUP" | head -1 | cut -d: -f1)" || call_ln=""
check "the guard is called at all" "$([[ -n "$call_ln" ]] && echo yes || echo no)" "yes"
check "…after 'step Preflight', so only an up reaches it" \
  "$([[ -n "$pre_ln" && -n "$call_ln" ]] && (( call_ln > pre_ln )) && echo yes || echo no)" "yes"
for a in down clean verify; do
  check "…and '$a' still exits at the router before preflight" \
    "$(sed -n "/^case \"\$ACTION\" in/,/^esac/p" "$STARTUP" | grep -c "^ *$a)\(  *\)\?.*exit 0" || true)" "1"
done

# 7. THE PROBE ITSELF, against a real daemon. Everything above answers for docker; this asks it.
#    Its own throwaway compose project, created and removed here — it never looks at
#    hc-market-quality's containers, so running this cannot depend on, or disturb, a live stack.
echo
if ! docker info >/dev/null 2>&1; then
  echo "  SKIP project_container_config_files against a real docker daemon — none reachable here."
  echo "       Every case above answered for docker with a stub, so nothing has established that"
  echo "       'docker ps --format {{.Label ...}}' returns the config file at all. Run this on a"
  echo "       machine with docker."
else
  # Guarded, because every case above stubs this function: if the extraction comes back empty the
  # probe is simply missing from startup.sh, and an unguarded run reports `command not found` and
  # aborts at 127 rather than saying which of the two things is wrong.
  if ! sed -n '/^project_container_config_files() {/,/^}/p' "$STARTUP" | grep -q 'docker ps'; then
    echo "  FAIL no project_container_config_files() calling 'docker ps' in $STARTUP" >&2
    echo "       Every case above answers for that function with a stub, so without it this file" >&2
    echo "       asserts a fix that is not there. See decisions.md D67." >&2
    fail=$((fail + 1))
    printf '\n%s passed, %s failed\n' "$pass" "$fail"; exit 1
  fi
  probe() { ( set -Eeuo pipefail; PROJECT="$1"
              eval "$(sed -n '/^project_container_config_files() {/,/^}/p' "$STARTUP")"
              project_container_config_files ) }

  # A real image is needed to create a container at all. If it cannot be had, SKIP loudly rather
  # than reporting a green section: every assertion below is about what docker answers, and a
  # section that creates nothing answers nothing.
  img="busybox:latest"
  if ! docker image inspect "$img" >/dev/null 2>&1 && ! docker pull -q "$img" >/dev/null 2>&1; then
    echo "  SKIP project_container_config_files against a real daemon — cannot obtain $img."
    echo "       §1–§6 all answered for docker with a stub, so nothing here has established that"
    echo "       compose writes com.docker.compose.project.config_files at all."
    printf '\n%s passed, %s failed\n' "$pass" "$fail"; exit $(( fail > 0 ))
  fi

  tp="hc-market-d67probe-$$"
  work="$(mktemp -d "${TMPDIR:-/tmp}/hc-checkout-probe-XXXXXX")"
  # On an EXIT trap, not at the end: an assertion aborting here would otherwise leave containers on
  # the host, and the next run would find them and report a stale pid as its subject. It removes
  # only the names it created, and never touches a real project's.
  cleanup_probe() {
    docker rm -f "${tp}-alpha" "${tp}-bravo" >/dev/null 2>&1 || true
    docker network rm "${tp}_default" >/dev/null 2>&1 || true
    rm -rf "$work"
  }
  trap cleanup_probe EXIT

  mkdir -p "$work/main" "$work/tree"
  # Two files, two services, ONE project — the state on this host reproduced from scratch, which is
  # the only way to know `docker compose ls` reporting two config files is a thing compose does
  # rather than a thing somebody once did by hand.
  printf 'services:\n  alpha:\n    image: %s\n    container_name: %s-alpha\n' "$img" "$tp" > "$work/main/compose.yml"
  printf 'services:\n  bravo:\n    image: %s\n    container_name: %s-bravo\n' "$img" "$tp" > "$work/tree/compose.yml"

  # `create`, not `up`: the labels this probe reads are written at CREATE time (measured), so
  # nothing here has to start, and a CI runner is left with two stopped containers for a second
  # rather than two running ones.
  docker compose -p "$tp" -f "$work/main/compose.yml" create >/dev/null 2>&1
  docker compose -p "$tp" -f "$work/tree/compose.yml" create >/dev/null 2>&1

  found="$(probe "$tp" | tr '\n' '|')"
  check "the probe reads the config file docker recorded for a container" \
    "$(case "$found" in *"$work/main/compose.yml"*) echo yes ;; *) echo "no ($found)" ;; esac)" "yes"
  check "…for every container of the project, not the first one" \
    "$(case "$found" in *"$work/tree/compose.yml"*) echo yes ;; *) echo "no ($found)" ;; esac)" "yes"
  check "…and names which container each belongs to" \
    "$(case "$found" in *"${tp}-alpha"*) echo yes ;; *) echo "no ($found)" ;; esac)" "yes"
  check "…and claims nothing from a project whose name merely starts the same way" \
    "$(probe "${tp}-decoy")" ""

  #  A project nothing has ever used reads as a first run — and reads as one by EXITING ZERO, which
  #  is the whole of it. An empty list is the answer for a brand new box and a non-zero return is how
  #  the probe says it could not ask, so a function that conflates them refuses the one estate it
  #  must let through. D65's first probe did exactly that (`grep -v` exits 1 having matched nothing,
  #  under `pipefail`), and it went unnoticed because every stubbed case answers for docker.
  check "a project with nothing of its own reads as a first run" "$(probe "hc-market-d67absent-$$")" ""
  rc=0; probe "hc-market-d67absent-$$" >/dev/null || rc=$?
  check "…and says so by succeeding, not by failing with an empty answer" "$rc" "0"

  rc=0; DOCKER_HOST=unix:///nonexistent-hc-d67.sock probe "$tp" >/dev/null 2>&1 || rc=$?
  check "an unreachable daemon is an error, not an empty answer" \
    "$([[ "$rc" != 0 ]] && echo nonzero || echo zero)" "nonzero"

  #  An EMPTY project name is refused rather than asked about. Asked, the daemon answers *nothing
  #  here* at rc 0 — measured against this filter, not carried over from D65's volume one — which is
  #  indistinguishable from a first run, so an empty PROJECT would start a stack over whatever is
  #  already running. Unreachable while startup.sh sets PROJECT unconditionally; every published
  #  port above that line is spelled `${X:-default}`, so the tidy-up that gives this one the same
  #  treatment is an ordinary edit and this costs a line.
  rc=0; out="$(probe "" 2>/dev/null)" || rc=$?
  check "an empty project name is refused, not answered" \
    "$([[ "$rc" != 0 ]] && echo nonzero || echo "zero, returned [$out]")" "nonzero"

  cleanup_probe; trap - EXIT
  echo "  (removed the ${tp} throwaway project)"
fi

printf '\n%s passed, %s failed\n' "$pass" "$fail"
exit $(( fail > 0 ))
