#!/usr/bin/env bash
# ==============================================================================
#  Drives `mail-links-are-served.sh` against broken states, in a SYNTHETIC TREE.
#
#  Every case builds a throwaway directory holding a mail template, a routes file, an `app.routes.ts`
#  and a screen, points the check at them through its `HC_*` variables, and asserts the check's
#  verdict. Nothing here touches the repository's own files — a check whose test mutates the tree it
#  guards is one restore away from committing the defect it planted (CLAUDE.md's own standing rule).
#
#  Every case classifies itself as a `refusal` or a `control`, and the trailer REFUSES to print
#  unless the two counters reconcile with what `report` saw. That is D98's correction to
#  `account-lifecycle-guards-test.sh`, which printed `$((pass - 2))` and overstated in both
#  directions because two readers derived two different answers from a file mixing both kinds.
#
#  Read the count off the last line of a run. Do not quote one from a document.
#      ./.github/checks/mail-links-are-served-test.sh
# ==============================================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# The subject, overridable so a MUTANT of the check can be driven against the same cases
# without editing the tree the check guards. That is how cases 18 and 19 were established as
# discriminating rather than asserted to be: `HC_CHECK=/tmp/positional.sh ./…-test.sh`. A test that
# mutates the repository to measure itself is one forgotten restore away from committing the defect
# it planted, which is a real failure in this workspace's history.
CHECK="${HC_CHECK:-$ROOT/.github/checks/mail-links-are-served.sh}"
STRIPPER_DEFAULT="$ROOT/.github/checks/strip-comments.awk"

# ⚠ THE HARNESS REFUSES TO RUN IF ITS SUBJECT IS NOT THERE, and this was added after watching it
# blame a case for its own breakage. Driven from a directory where `$ROOT` resolved elsewhere, every
# invocation exited 127 and the transcript read `FAIL 21 app.routes.ts does not exist — wanted exit
# 1, got 127` — a case reported as broken when what was broken was the instrument. Refusal cases
# happen to fail loudly on 127 (they want exactly 1), so nothing was silently green; what was wrong
# is the DIAGNOSIS, and a diagnosis nobody can act on is how a red run gets re-run instead of read.
# "Suspect the instrument before the code" is this workspace's standing rule.
for required in "$CHECK" "$STRIPPER_DEFAULT"; do
  [[ -f "$required" ]] || {
    printf '✗ %s is missing — the harness cannot run, and no case below would be about its own subject\n' "$required" >&2
    exit 1
  }
done
[[ -x "$CHECK" ]] || {
  printf '✗ %s is not executable — every case would exit 126 and be reported as a broken fixture\n' "$CHECK" >&2
  exit 1
}
STRIPPER="$STRIPPER_DEFAULT"

WORK="$(mktemp -d -t hc-mail-links-XXXXXX)"
trap 'rm -rf "$WORK"' EXIT

pass=0 fail=0 refusals=0 controls=0 seen=0
refusal() { refusals=$((refusals + 1)); }
control() { controls=$((controls + 1)); }

report() {
  local label=$1 want=$2 got=$3 out=$4
  seen=$((seen + 1))
  if [[ "$want" == "$got" ]]; then
    printf '  ok   %s (exit %s)\n' "$label" "$got"
    pass=$((pass + 1))
  else
    printf '  FAIL %s — wanted exit %s, got %s\n' "$label" "$want" "$got"
    printf '%s\n' "$out" | sed 's/^/       | /'
    fail=$((fail + 1))
  fi
}

# ---------------------------------------------------------------------------------------------
# A correct synthetic tree. Every case starts from this and breaks exactly one thing, so a case's
# subject is the difference and not the fixture.
# ---------------------------------------------------------------------------------------------
build() {
  local dir=$1
  rm -rf "$dir"
  mkdir -p "$dir/mail" "$dir/app/account/activation" "$dir/app/account/new-password"

  cat >"$dir/mail/activationEmail.html" <<'HTML'
<html><body><a th:href="${baseUrl}/account/activate?key=${user.activationKey}">Activate</a></body></html>
HTML
  cat >"$dir/mail/passwordResetEmail.html" <<'HTML'
<html><body><a th:href="${baseUrl}/account/reset/finish?key=${user.resetKey}">Reset</a></body></html>
HTML

  cat >"$dir/app/app.routes.ts" <<'TS'
const routes = [
  { path: 'login', loadComponent: () => import('./login/login') },
  {
    path: 'account',
    loadChildren: () => import('./account/account-lifecycle.routes'),
  },
];
export default routes;
TS

  cat >"$dir/app/account/account-lifecycle.routes.ts" <<'TS'
const routes = [
  {
    path: 'activate',
    loadComponent: () => import('./activation/activation'),
  },
  {
    path: 'reset/finish',
    loadComponent: () => import('./new-password/new-password'),
  },
];
export default routes;
TS

  cat >"$dir/app/account/activation/activation.ts" <<'TS'
export default class Activation {
  ngOnInit() {
    this.route.queryParamMap.subscribe(params => this.key.set(paramValue(params, 'key')));
  }
}
TS
  cat >"$dir/app/account/new-password/new-password.ts" <<'TS'
export default class NewPassword {
  ngOnInit() {
    this.route.queryParamMap.subscribe(params => this.key.set(paramValue(params, 'key')));
  }
}
TS
}

run() {
  local dir=$1 out rc=0
  out=$(
    HC_MAIL_TEMPLATES="$dir/mail" \
      HC_ROUTES_FILE="$dir/app/account/account-lifecycle.routes.ts" \
      HC_APP_ROUTES="$dir/app/app.routes.ts" \
      HC_STRIPPER="$STRIPPER" \
      "$CHECK" 2>&1
  ) || rc=$?
  LAST_OUT=$out
  return "$rc"
}

drive() {
  local label=$1 want=$2 dir=$3 rc=0
  run "$dir" || rc=$?
  report "$label" "$want" "$rc" "$LAST_OUT"
}

printf '\n== mail-links-are-served.sh, driven against broken states ==\n\n'

# --- 1. The control that makes every refusal below mean something -----------------------------
control
D="$WORK/c1"
build "$D"
drive '1  a correct tree passes' 0 "$D"

# --- 2. The item itself: the client serves neither path ---------------------------------------
refusal
D="$WORK/c2"
build "$D"
cat >"$D/app/account/account-lifecycle.routes.ts" <<'TS'
const routes = [];
export default routes;
TS
drive '2  NEW-60 itself: no account route at all' 1 "$D"

# --- 3. One path renamed on the CLIENT side — the two-segment one, which is the trap ----------
refusal
D="$WORK/c3"
build "$D"
sed -i "s@path: 'reset/finish'@path: 'reset-finish'@" "$D/app/account/account-lifecycle.routes.ts"
drive '3  the client renames reset/finish to reset-finish' 1 "$D"

# --- 4. One path renamed on the TEMPLATE side -------------------------------------------------
refusal
D="$WORK/c4"
build "$D"
sed -i 's@/account/activate?key=@/account/activate-account?key=@' "$D/mail/activationEmail.html"
drive '4  the template moves to /account/activate-account' 1 "$D"

# --- 5. The prefix is gone from app.routes.ts: the child table mounts nowhere ------------------
refusal
D="$WORK/c5"
build "$D"
sed -i "s@path: 'account',@path: 'acct',@" "$D/app/app.routes.ts"
drive '5  app.routes.ts stops declaring the `account` prefix' 1 "$D"

# --- 6. THE COMMENT FAIL-OPEN. The paths live only in prose. -----------------------------------
#  This is the case the whole stripper family exists for: `account-lifecycle.routes.ts` quotes both
#  paths in its own javadoc, so an unstripped check is satisfied by the paragraph explaining it.
refusal
D="$WORK/c6"
build "$D"
cat >"$D/app/account/account-lifecycle.routes.ts" <<'TS'
/**
 * The two addresses the mail composes:
 *   path: 'activate'
 *   path: 'reset/finish'
 * …and this file declares neither of them.
 */
const routes = [];
export default routes;
TS
drive '6  both paths present ONLY in a comment' 1 "$D"

# --- 7. …and the same thing in a line comment, which the old sed-based stripper also missed ----
refusal
D="$WORK/c7"
build "$D"
cat >"$D/app/account/account-lifecycle.routes.ts" <<'TS'
const routes = [
  // path: 'activate'
  // path: 'reset/finish'
];
export default routes;
TS
drive "7  both paths present only in // comments" 1 "$D"

# --- 8. The screen stops reading the parameter the mail sends ----------------------------------
refusal
D="$WORK/c8"
build "$D"
cat >"$D/app/account/activation/activation.ts" <<'TS'
export default class Activation {
  ngOnInit() {
    this.activate();
  }
}
TS
drive '8  the screen never reads ?key=' 1 "$D"

# --- 9. …and reading it with a literal `paramMap.get` is ALSO refused -------------------------
#  Not a style preference: `endpoint-construction.spec.ts` keys on the method name, so that spelling
#  is an offender to it, and `route-params.ts` exists for exactly this.
refusal
D="$WORK/c9"
build "$D"
cat >"$D/app/account/new-password/new-password.ts" <<'TS'
export default class NewPassword {
  ngOnInit() {
    this.route.queryParamMap.subscribe(params => this.key.set(params.get('key')));
  }
}
TS
drive '9  the screen reads the key with a literal paramMap.get' 1 "$D"

# --- 10. …and the parameter in a COMMENT does not count either --------------------------------
refusal
D="$WORK/c10"
build "$D"
cat >"$D/app/account/activation/activation.ts" <<'TS'
export default class Activation {
  ngOnInit() {
    /* reads paramValue(params, 'key') — except it does not */
    this.activate();
  }
}
TS
drive "10 the parameter read present only in a comment" 1 "$D"

# --- 11. THE DERIVATION FLOOR. No templates match, so the for-each loop is vacuous. ------------
#  Without this floor the check prints `ok` having compared nothing — the fail-open that has been
#  the finding in every enumerated list in this repository's CI.
refusal
D="$WORK/c11"
build "$D"
rm "$D/mail/passwordResetEmail.html"
drive '11 only ONE link derivable — below the floor' 1 "$D"

refusal
D="$WORK/c12"
build "$D"
rm "$D/mail/activationEmail.html" "$D/mail/passwordResetEmail.html"
drive '12 NO link derivable at all' 1 "$D"

# --- 13. The stripper itself is missing: fail closed, naming the cause -------------------------
#  Measured rather than assumed, because absent it two sibling checks printed `ok` having read
#  nothing at all (CLAUDE.md, "Each caller guards that the file EXISTS").
refusal
D="$WORK/c13"
build "$D"
rc=0
out=$(
  HC_MAIL_TEMPLATES="$D/mail" \
    HC_ROUTES_FILE="$D/app/account/account-lifecycle.routes.ts" \
    HC_APP_ROUTES="$D/app/app.routes.ts" \
    HC_STRIPPER="$D/no-such-stripper.awk" \
    "$CHECK" 2>&1
) || rc=$?
report '13 the comment stripper is absent' 1 "$rc" "$out"

# --- 14. The routes file is gone entirely ------------------------------------------------------
refusal
D="$WORK/c14"
build "$D"
rm "$D/app/account/account-lifecycle.routes.ts"
drive '14 the routes file does not exist' 1 "$D"

# --- 15. The component behind a route cannot be found -----------------------------------------
refusal
D="$WORK/c15"
build "$D"
rm "$D/app/account/new-password/new-password.ts"
drive '15 the component a route names is missing' 1 "$D"

# --- 16. A THIRD template appears, composing a path nothing serves ------------------------------
#  The reason the expected set is DERIVED and not a list of two. A check holding the client against
#  a hard-coded pair cannot see this at all.
refusal
D="$WORK/c16"
build "$D"
cat >"$D/mail/emailChangeEmail.html" <<'HTML'
<html><body><a th:href="${baseUrl}/account/email/confirm?token=${user.emailKey}">Confirm</a></body></html>
HTML
drive '16 a THIRD template composes an unserved path' 1 "$D"

# --- 17. The CONTROL for 16: a third template whose path IS served passes ----------------------
#  ⚠ AND IT IS ALSO THE CASE THAT DISCRIMINATES THE COMPONENT PAIRING — measured, not assumed.
#  The check's first version took the Nth `import('./…')` for the Nth derived link, which is right
#  only while `sort -u`'s order equals the declaration order. Driven against that mutant through
#  `HC_CHECK=`, this is the ONE case of nineteen that moves: it goes RED on a perfectly correct tree
#  and names the wrong two files — because `sort -u` orders the three links
#  activate / email/confirm / reset/finish while the routes declare activate / reset/finish /
#  email/confirm, so `?token=` is looked for in `new-password.ts` and `?key=` in `confirm-email.ts`.
#  Red on correct code is how a guard gets deleted by the next person who meets it.
control
D="$WORK/c17"
build "$D"
cat >"$D/mail/emailChangeEmail.html" <<'HTML'
<html><body><a th:href="${baseUrl}/account/email/confirm?token=${user.emailKey}">Confirm</a></body></html>
HTML
mkdir -p "$D/app/account/confirm-email"
cat >"$D/app/account/confirm-email/confirm-email.ts" <<'TS'
export default class ConfirmEmail {
  ngOnInit() {
    this.route.queryParamMap.subscribe(params => this.token.set(paramValue(params, 'token')));
  }
}
TS
cat >"$D/app/account/account-lifecycle.routes.ts" <<'TS'
const routes = [
  { path: 'activate', loadComponent: () => import('./activation/activation') },
  { path: 'reset/finish', loadComponent: () => import('./new-password/new-password') },
  { path: 'email/confirm', loadComponent: () => import('./confirm-email/confirm-email') },
];
export default routes;
TS
drive '17 control: a third template whose route EXISTS passes' 0 "$D"

# --- 18 and 19. Declaration order reversed. THESE TWO DO NOT DISCRIMINATE, and saying so is the
#  point. The obvious way to pin "pairing is by route, not by position" is to reverse two
#  declarations — and with only TWO routes that cannot work, because both screens read a parameter
#  called `key`: the positional mutant pairs each link with the other's screen, finds `key` in it,
#  and is green (18) or red for the wrong reason (19). Measured against the mutant: 18 and 19 both
#  agree with the shipped version, and only case 17 moves.
#
#  They are kept anyway, as the fixture they are: 18 pins that a reorder is not itself a defect, and
#  19 that a broken screen is still caught after one. A case that distinguishes nothing is worth
#  keeping only if it says so, which is this file's own `templates.length > 4` lesson (D103 §14).
control
D="$WORK/c18"
build "$D"
cat >"$D/app/account/account-lifecycle.routes.ts" <<'TS'
const routes = [
  { path: 'reset/finish', loadComponent: () => import('./new-password/new-password') },
  { path: 'activate', loadComponent: () => import('./activation/activation') },
];
export default routes;
TS
drive '18 control: declaration order reversed, still correct' 0 "$D"

refusal
D="$WORK/c19"
build "$D"
# Same reversal, and now the first-declared screen is the broken one. See the note above 18: the
# positional mutant also refuses this, for the wrong pair of files.
cat >"$D/app/account/account-lifecycle.routes.ts" <<'TS'
const routes = [
  { path: 'reset/finish', loadComponent: () => import('./new-password/new-password') },
  { path: 'activate', loadComponent: () => import('./activation/activation') },
];
export default routes;
TS
cat >"$D/app/account/activation/activation.ts" <<'TS'
export default class Activation {
  ngOnInit() {
    this.activate();
  }
}
TS
drive '19 order reversed AND the second screen drops the key' 1 "$D"

# --- 20. The templates DIRECTORY is gone — distinct from an empty one -------------------------
#  Case 12 is an EMPTY directory (the derivation floor); this is no directory at all, which is the
#  shape a moved or renamed `templates/mail` takes. The two are one `[[ -d ]]` apart and fail for
#  different reasons, so both are driven: a check that answered "0 links, fine" to a path it could
#  not even open would be reporting on a tree it never read.
refusal
D="$WORK/c20"
build "$D"
rm -rf "$D/mail"
drive '20 the templates directory does not exist' 1 "$D"

# --- 21. `app.routes.ts` is gone entirely ------------------------------------------------------
refusal
D="$WORK/c21"
build "$D"
rm "$D/app/app.routes.ts"
drive '21 app.routes.ts does not exist' 1 "$D"

# ---------------------------------------------------------------------------------------------
printf '\n'
if ((refusals + controls != seen)); then
  printf '✗ %d case(s) ran but %d classified themselves (%d refusal + %d control) — classify every case\n' \
    "$seen" "$((refusals + controls))" "$refusals" "$controls" >&2
  exit 1
fi
printf 'mail-links-are-served-test: %d case(s) — %d refusal(s), %d control(s); %d passed, %d failed\n' \
  "$seen" "$refusals" "$controls" "$pass" "$fail"
((fail == 0)) || exit 1
printf '✓ the check refuses every state it exists to refuse, and passes every correct one\n'
