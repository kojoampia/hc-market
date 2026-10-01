#!/usr/bin/env bash
# ==============================================================================
#  "A review's public author may not be composed from an identifier" — its own test.
#
#  decisions.md D104 §9a, backlog NEW-81. The guard it drives is a step in build.yml, and D104 §9a
#  claimed it had been "driven against 19 states" with no script anybody else could run — so two
#  reviewers and a coordinator each wrote their own lifter to check it. D63's rule: A RECIPE NOBODY
#  CAN RE-RUN IS A CLAIM RATHER THAN A CHECK.
#
#  HOW IT DRIVES THE SHIPPED CODE. The step is lifted out of build.yml BY NAME, exactly as
#  refusal-logging-level-test.sh does it, so what runs is the shipped text and not a copy that can
#  drift. An empty lift is fatal rather than skipped, and the lifted block must contain
#  `ReviewAuthor.displayName(` — a renamed or reindented step would otherwise leave every assertion
#  below running against an empty script and passing.
#
#  IT NEVER TOUCHES THE REAL CHECKOUT. Every case runs in a synthetic tree under a temp directory,
#  rebuilt from the real files per case. An earlier private harness mutated the working tree in place
#  and restored it afterwards; that is one interrupted run away from leaving a planted defect in a
#  repository another agent is editing.
#
#  ⚠ EVERY MUTATION IS CHECKSUMMED BEFORE AND AFTER, AND A MUTATION THAT CHANGED NOTHING IS FATAL.
#  This is not defensive decoration — it is the defect this harness's predecessor actually had. Four
#  `sed` patterns carried a two-argument call signature after the signature became three-argument;
#  they matched nothing, left the tree CORRECT, the guard correctly exited 0, and the harness reported
#  four FAILURES. A guard reported as broken because the instrument had stopped reaching its subject.
#
#  THE COUNT IS DERIVED AND PRINTED ON THE LAST LINE. Do not quote a number for this from any
#  document — read the line this prints. Every case classifies itself as a `refusal` or a `control`,
#  and the trailer refuses to print unless the two counters reconcile with what ran.
#
#      ./.github/checks/review-author-guard-test.sh
# ==============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

WORKFLOW=.github/workflows/build.yml
STEPNAME="A review's public author may not be composed from an identifier"
M=catalog/src/main/java/net/jojoaddison

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
step="$work/step.sh"
tree="$work/tree"

pass=0
bad=0
refusals=0
controls=0

ok()  { printf '  ok   %s\n' "$1"; pass=$((pass + 1)); }
err() { printf '::error::%s\n' "$1"; bad=$((bad + 1)); }

# ---------------------------------------------------------------------------- lift the real step --
# Stops at the next step's `- name:` OR at the comment block that introduces it, so what is lifted
# is this step and nothing else.
awk -v want="      - name: $STEPNAME" '
  $0 == want { p = 1; next }
  p && (/^      - name: / || /^      #/) { exit }
  p { print }
' "$WORKFLOW" | sed -e '/^ *run: |$/d' -e 's/^          //' > "$step"

if [ ! -s "$step" ]; then
  err "could not lift the step named '$STEPNAME' out of $WORKFLOW — it has been renamed or reindented, and every assertion below would run against an empty script and pass."
  printf '\nreview author guard test: FAILED\n'; exit 1
fi
for needle in 'ReviewAuthor.displayName(' 'strip-comments.awk'; do
  if ! grep -qF "$needle" "$step"; then
    err "the lifted step does not contain '$needle', so either the lift took the wrong block or the check has stopped doing what this tests. See decisions.md D104 §9a."
    printf '\nreview author guard test: FAILED\n'; exit 1
  fi
done
ok "lifted $(wc -l < "$step") lines of the shipped step out of $WORKFLOW"

# ------------------------------------------------------------------------- the synthetic estate --
# Only the files that matter: the four legitimate writers, the composer, and the stripper. `scanned`
# is therefore small — the real run's figure is printed by CI and is not this test's subject.
SUBJECTS=(
  "$M/web/rest/ReviewWriteResource.java"
  "$M/service/ReviewAuthor.java"
  "$M/service/ErasureWorkflow.java"
  "$M/service/seed/CatalogSeeder.java"
  "$M/domain/Review.java"
)
for f in "${SUBJECTS[@]}" .github/checks/strip-comments.awk; do
  [ -f "$f" ] || { err "$f does not exist, so this test has no subject"; printf '\nreview author guard test: FAILED\n'; exit 1; }
done

build() {
  rm -rf "$tree"
  mkdir -p "$tree/.github/checks"
  cp .github/checks/strip-comments.awk "$tree/.github/checks/"
  for f in "${SUBJECTS[@]}"; do
    mkdir -p "$tree/$(dirname "$f")"
    cp "$f" "$tree/$f"
  done
}

RES="$tree/$M/web/rest/ReviewWriteResource.java"
ERA="$tree/$M/service/ErasureWorkflow.java"
SEED="$tree/$M/service/seed/CatalogSeeder.java"
REV="$tree/$M/domain/Review.java"
AUTHOR="$tree/$M/service/ReviewAuthor.java"

# A mutation that changed nothing is FATAL — see the header.
edit() { # file sed-expr…
  local f="$1"; shift
  local before after
  before="$(cksum < "$f")"
  sed -i "$@" "$f"
  after="$(cksum < "$f")"
  if [ "$before" = "$after" ]; then
    err "a mutation of ${f#"$tree"/} changed nothing — the pattern has drifted out of date and the case below would be driving an UNMUTATED tree, which reads as 'the guard did not fire'. Expression: $*"
    return 1
  fi
}

# Writes a second author-writing class into service/, at a path no allowance names.
plant_second_writer() { # body-of-the-write
  mkdir -p "$tree/$M/service"
  cat > "$tree/$M/service/AuthorBackfill.java" <<JAVA
package net.jojoaddison.service;

import net.jojoaddison.domain.Review;

class AuthorBackfill {

    private String authorName;

    void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    void backfill(Review r, String login) {
        $1
    }
}
JAVA
}

run() { ( cd "$tree" && bash "$step" >"$work/out" 2>&1 ); }

refusal() { # desc
  local rc
  run; rc=$?
  refusals=$((refusals + 1))
  if [ "$rc" -ne 0 ]; then ok "refuses: $1"
  else err "DID NOT REFUSE: $1 — the guard exited 0. It printed: $(tail -1 "$work/out")"; fi
}

control() { # desc
  local rc
  run; rc=$?
  controls=$((controls + 1))
  if [ "$rc" -eq 0 ]; then ok "allows:  $1"
  else err "REFUSED CORRECT CODE: $1 — a ban that rejects accurate code gets loosened by the next person who meets it. It printed: $(grep -m1 '::error' "$work/out")"; fi
}

# ------------------------------------------------------------------------------------ the cases --

build; control "the tree as committed"

# --- the call-site grep: it holds THAT THE CALL IS PRESENT, and nothing more ---
build; edit "$RES" 's/ReviewAuthor\.displayName(/passThrough(/'                 && refusal "the resource stops composing the NAME"
build; edit "$RES" 's/ReviewAuthor\.initials(/initialsOf(/'                     && refusal "the resource stops composing the INITIALS"
build; edit "$RES" 's|\.authorName(ReviewAuthor\.displayName(.*$|.authorName(summary.customerName())|' \
  && refusal "NEW-81's original defect, restored verbatim"
build; edit "$RES" 's|\.authorName(ReviewAuthor\.displayName(.*$|.authorName(login)|' \
  && refusal "the author is the login outright"
build; edit "$RES" 's|\.authorInitials(ReviewAuthor\.initials(.*$|.authorInitials(initialsOf(summary.customerName(), login))|' \
  && refusal "a hand-rolled initials helper beside a correct name"
# The prose fail-open this repository has found nine times: a COMMENT naming the calls.
build; edit "$RES" 's|\.authorName(ReviewAuthor\.displayName(.*$|.authorName(summary.customerName()) /* ReviewAuthor.displayName( and ReviewAuthor.initials( */|' \
  && refusal "a COMMENT naming both calls does not satisfy the grep"

# --- the six composition spellings, in the file whose token IS allowed ---
# `+` was the only one caught before D104 §9a's second round; the other four published a login with
# the guard asserting in its own success line that nothing concatenated.
build; edit "$RES" 's|, summary\.customerLogin()))|, summary.customerLogin()) + login)|' \
  && refusal "composition in the live write: + "
build; edit "$RES" 's|, summary\.customerLogin()))|, summary.customerLogin()).concat(login))|' \
  && refusal "composition in the live write: .concat( — THE BLOCKING FINDING"
build; edit "$ERA" 's|r\.setAuthorName(REDACTED_NAME);|r.setAuthorName(r.getCustomerLogin() + REDACTED_NAME);|' \
  && refusal "composition in the erasure: +"
build; edit "$ERA" 's|r\.setAuthorName(REDACTED_NAME);|r.setAuthorName(r.getCustomerLogin().concat(REDACTED_NAME));|' \
  && refusal "composition in the erasure: .concat("
build; edit "$ERA" 's|r\.setAuthorName(REDACTED_NAME);|r.setAuthorName(String.format("%s%s", r.getCustomerLogin(), REDACTED_NAME));|' \
  && refusal "composition in the erasure: String.format"
build; edit "$ERA" 's|r\.setAuthorName(REDACTED_NAME);|r.setAuthorName("%s".formatted(r.getCustomerLogin()).substring(0) + REDACTED_NAME.substring(0));|' \
  && refusal "composition in the erasure: .formatted("
build; edit "$ERA" 's|r\.setAuthorName(REDACTED_NAME);|r.setAuthorName(String.join("", r.getCustomerLogin(), REDACTED_NAME));|' \
  && refusal "composition in the erasure: String.join"
build; edit "$ERA" 's|r\.setAuthorName(REDACTED_NAME);|r.setAuthorName(new StringBuilder(r.getCustomerLogin()).append(REDACTED_NAME).toString());|' \
  && refusal "composition in the erasure: StringBuilder"
build; edit "$SEED" 's|\.authorName(r\.authorName())|.authorName(r.customerLogin() + r.authorName())|' \
  && refusal "composition in the seeder: a login prepended to the seed's own field"

# --- the sweep: every OTHER file ---
build; plant_second_writer 'r.setAuthorName(login);'
refusal "a bare second writer in a file no allowance names"
build; plant_second_writer 'this.setAuthorName(login);'
refusal "a second writer borrowing the ENTITY's this.setAuthorName allowance"
build; plant_second_writer 'r.setAuthorName(ReviewAuthor.ANONYMOUS_NAME);'
refusal "a second writer naming the COMPOSER's token in the wrong file"
build; plant_second_writer 'r.setAuthorName(Erasures.REDACTED_NAME);'
refusal "a second writer naming the ERASURE's token in the wrong file"
# Path anchoring: a copy of a legitimate file under another package must not inherit its allowance
# by tail-matching `*/domain/Review.java`.
build; mkdir -p "$tree/$M/service/legacy/domain"; cp "$REV" "$tree/$M/service/legacy/domain/Review.java"
edit "$tree/$M/service/legacy/domain/Review.java" 's|this.setAuthorName(authorName);|this.setAuthorName(login);|' \
  && refusal "a copy of the entity under another package does not inherit its allowance"

# --- the guard's own preconditions ---
build; rm -f "$AUTHOR";                                    refusal "ReviewAuthor is renamed away"
build; rm -f "$tree/.github/checks/strip-comments.awk";    refusal "the shared comment stripper is missing"
build; rm -rf "$tree/$M";                                  refusal "there is no tree to scan"
# The positive control for the sweep: with the accessors renamed it must not pass having matched
# nothing. This is the fail-open every check in this family has had at least once.
build
rename='s/\.authorName(/.byline(/g; s/\.authorInitials(/.monogram(/g; s/\.setAuthorName(/.setByline(/g; s/\.setAuthorInitials(/.setMonogram(/g'
renamed=0
for f in "$RES" "$SEED" "$REV" "$ERA"; do edit "$f" "$rename" && renamed=$((renamed + 1)); done
[ "$renamed" -eq 4 ] && refusal "the accessors are renamed estate-wide — it must not pass having matched nothing"

# --- green controls. A ban that refuses correct code is as much a defect as one that passes it. ---
build; edit "$SEED" 's|\.authorName(r\.authorName())|.authorName(row.authorName())|; s|\.authorInitials(r\.authorInitials())|.authorInitials(row.authorInitials())|' \
  && control "the seeder's loop variable renamed — the allowance is the field, not the variable"
build; edit "$ERA" 's|r\.setAuthorName(REDACTED_NAME)|r.setAuthorName(ErasureWorkflow.REDACTED_NAME)|' \
  && control "the erasure's stand-in written fully qualified"
build; edit "$RES" 's|, login, summary\.customerLogin()))|, summary.customerLogin(), login))|g' \
  && control "the composer's two identifiers swapped — same rule, different order"
# The stripper runs FIRST, so a `+` in a comment on an author-write line is not a composition.
build; edit "$RES" 's|\.authorName(ReviewAuthor\.displayName(summary\.customerName(), login, summary\.customerLogin()))|.authorName(ReviewAuthor.displayName(summary.customerName(), login, summary.customerLogin())) /* not a + composition */|' \
  && control "a + inside a COMMENT on an author-write line is not a composition"

# ------------------------------------------------------------------------------------- trailer --
total=$((refusals + controls))
printf '\n'
if [ "$((pass + bad))" -ne "$((total + 1))" ]; then
  printf '::error::case accounting does not reconcile — %s refusals + %s controls + 1 lift = %s, but %s assertions ran. A case that classified itself as neither would be absorbed silently.\n' \
    "$refusals" "$controls" "$((total + 1))" "$((pass + bad))"
  bad=$((bad + 1))
fi
printf 'review author guard test: %s refusals, %s controls, %s states driven — %s\n' \
  "$refusals" "$controls" "$total" "$([ "$bad" -eq 0 ] && echo ok || echo FAILED)"
[ "$bad" -eq 0 ]
