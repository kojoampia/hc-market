#!/usr/bin/env bash
# ==============================================================================
#  No deploy path may point JWT_BASE64_SECRET at the platform's shared key — decisions.md D37, D49.
#
#  THIS IS THE ONE OF WP-19's FIVE DEFECTS THAT HAD NO MECHANICAL GUARD, and it is the one with a
#  security shape. `deploy-prod.sh`'s own hint told the operator to take the production signing key
#  from ~/webroot/01-healthconnect/.env, which is the key hc-admin, hc-patient and hc-professional
#  share and which hc-market is deliberately not part of.
#
#  Nothing in the running estate can catch that. There is no `iss` claim and no `aud` claim anywhere
#  in these five services, so a token minted with the platform key is accepted here and a token
#  minted here is accepted there — silently, correctly, for ever. The estate would have come up
#  perfectly. The only place the boundary can be defended is in the words a deployer reads, so those
#  words are checked rather than merely written well.
#
#  Three arms, and they guard three different copies of the same instruction:
#
#  1. secret_hint's JWT_BASE64_SECRET arm — what an operator sees when preflight refuses. It must
#     still say what to generate, and if it names the platform file at all it must be to forbid it,
#     in those words.
#  2. Every mention of the platform key FILE, anywhere under deploy/ or quality/, must carry a
#     refusal within two lines of itself. Coarse on purpose: its job is that the ORIGINAL wording
#     fails, not that every possible rewrite does. `01-healthconnect/hc-infra` is a different thing —
#     the shared broker and Consul — and is not matched.
#  3. The compose files' `:?` messages — the string an operator meets at the moment of failure, and
#     the copy that was still wrong after the other four were corrected. It said "platform JWT secret
#     is required", which sends whoever hits it looking for the platform's key.
#
#  Run it directly; it prints what it checked either way.
#      ./.github/checks/signing-key-severance.sh
# ==============================================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

DEPLOY_SCRIPT="${HC_DEPLOY_SCRIPT:-deploy/deploy-prod.sh}"
# Where an instruction to a deployer can live. Docs under docs/ are records rather than instructions
# and are deliberately out of scope — decisions.md quotes the old wording as history.
SEARCH_PATHS="${HC_SEARCH_PATHS:-deploy quality}"
COMPOSE_FILES="${HC_COMPOSE_FILES:-deploy/docker/docker-compose.prod.yml deploy/docker/docker-compose.dev.yml quality/compose.yml}"
# The platform-wide settings file, which holds the shared key. NOT the hc-infra directory beside it.
PLATFORM_ENV='01-healthconnect/\.env'

fail=0
err() { printf '::error file=%s::%s\n' "$1" "$2"; fail=1; }

# --- 1. the hint an operator is given when the key is missing -------------------------------------
arm="$(awk '/^    JWT_BASE64_SECRET\)$/,/;;$/' "$DEPLOY_SCRIPT")"
if [[ -z "$arm" ]]; then
  err "$DEPLOY_SCRIPT" "secret_hint has no JWT_BASE64_SECRET arm — the operator is told nothing about which key to install. See decisions.md D37."
else
  for want in 'GENERATED FRESH' 'head -c 64 /dev/urandom' 'D37'; do
    if printf '%s' "$arm" | grep -qF -- "$want"; then
      echo "ok   secret_hint still says '$want'"
    else
      err "$DEPLOY_SCRIPT" "secret_hint's JWT_BASE64_SECRET advice no longer contains '$want'. It is the only instruction an operator gets about which signing key to install, and installing the platform's would dissolve a capability boundary silently. See decisions.md D37 and D49."
    fi
  done
  # If it names the platform file at all, it may only be to refuse it — in these words, because the
  # defect was a sentence that named the same file approvingly.
  #
  # A HERESTRING, BECAUSE A SWALLOWED MATCH HERE SKIPS THE REFUSAL CHECK ENTIRELY (decisions.md D99,
  # backlog NEW-73). This `if` is a gate, not an assertion: the whole "it may only be to forbid it"
  # test lives inside it, so `printf … | grep -q` dying 141 on a match means an `arm` that names the
  # platform key file approvingly is never examined at all — the one defect this file was written for.
  # $arm is 1,171 bytes today so it cannot invert; the direction is what is being repaired, not the
  # size. Sites in this file whose swallowed match produces a FALSE ALARM rather than a lost finding
  # are deliberately left as pipelines — see the note at the foot of this file.
  if grep -qE "$PLATFORM_ENV" <<<"$arm"; then
    if printf '%s' "$arm" | grep -qF 'Do NOT copy it from'; then
      echo "ok   secret_hint names the platform key file only to forbid it"
    else
      err "$DEPLOY_SCRIPT" "secret_hint names ~/webroot/01-healthconnect/.env without forbidding it ('Do NOT copy it from'). That file is the key hc-admin, hc-patient and hc-professional share; hc-market is not in that set. See decisions.md D37."
    fi
  fi
fi

# --- 2. no deploy-facing file may name it without a refusal beside it ------------------------------
while IFS=: read -r f n _; do
  [[ -n "${f:-}" ]] || continue
  from=$(( n > 2 ? n - 2 : 1 ))
  if sed -n "${from},$((n + 2))p" "$f" | grep -qE '\b(NOT|not|never|Never)\b'; then
    echo "ok   $f:$n names the platform key file, with a refusal beside it"
  else
    err "$f" "line $n names ~/webroot/01-healthconnect/.env with nothing within two lines refusing it. That is the platform's shared signing key and hc-market does not use it — every mention here must be a refusal. See decisions.md D37 and D49."
  fi
done < <(grep -rnE "$PLATFORM_ENV" $SEARCH_PATHS || true)

# --- 3. the `:?` message, which is what a failed `up` actually prints ------------------------------
for f in $COMPOSE_FILES; do
  msg="$(grep -o 'JWT_BASE64_SECRET:?[^}]*' "$f" | head -1 || true)"
  if [[ -z "$msg" ]]; then
    err "$f" "no \${JWT_BASE64_SECRET:?...} — the signing key is no longer required by this compose file, so a stack can start with whatever happens to be in the environment. See decisions.md D49."
    continue
  fi
  # HERESTRINGS, AND THIS LINE CARRIED TWO PIPELINES (decisions.md D99, backlog NEW-73 — which is why
  # the population is 51 pipelines on 50 lines and `grep -c` undercounts it). The FIRST one is the
  # fail-open: a swallowed match on 'platform' short-circuits the `&&` to false and prints `ok` for
  # exactly the message this arm exists to refuse — "platform JWT secret is required", the copy that
  # was still wrong after the other four were corrected. The second is negated and merely loud. Both
  # are converted because they are one condition; $msg is 256 bytes and neither can invert today.
  if grep -qF 'platform' <<<"$msg" && ! grep -qF 'NOT the platform key' <<<"$msg"; then
    err "$f" "the required-variable message describes this estate's signing key as the platform's ('$msg'). It is the one string an operator meets at the moment of failure, and it sends them to the key hc-admin, hc-patient and hc-professional share. See decisions.md D37 and D49."
  else
    echo "ok   $f says: $msg"
  fi
done

# The production file is the only one an operator meets on a host they cannot ask anybody about, so
# it carries the citation as well as the refusal.
prod="deploy/docker/docker-compose.prod.yml"
if [[ -f "$prod" ]]; then
  prodmsg="$(grep -o 'JWT_BASE64_SECRET:?[^}]*' "$prod" | head -1 || true)"
  for want in 'NOT the platform key' 'D37'; do
    printf '%s' "$prodmsg" | grep -qF -- "$want" \
      || err "$prod" "the production required-variable message does not say '$want'. See decisions.md D37 and D49."
  done
fi

[ "$fail" = 0 ] && echo "ok   the signing key is severed from the platform's in every deploy path"

# WHY THIS FILE MIXES TWO SHAPES, so that neither is "tidied" into the other (decisions.md D99).
#
# FOUR `| grep -q` PIPELINES remain here — that is a count of pipelines, not of evaluations, and two
# of the four sit inside `for` loops so they run six times between them. Derive it rather than
# trusting this sentence; this is the file whose line 89 carried two pipelines on one line and made
# NEW-73's own population 51 rather than 50:
#     awk -f .github/checks/strip-sh-comments.awk "$0" | grep -c '| *grep -q'
#
# They stay as pipelines by decision rather than by oversight: in every one a swallowed match makes
# this check REFUSE a correct tree — arm 1's `want` loop, the 'Do NOT copy it from' test, the `sed`
# window in arm 2, and arm 3's prod `want` loop. That is the direction D71 §5 permits things to fold
# in; somebody reads the refusal and looks. The two converted above are the opposite: a swallowed
# match there LOSES the finding and prints `ok`. Direction is a structural property and does not move
# when a file grows; the ~4.5KB threshold is a property of one producer on one machine and survives
# neither a bigger file nor a different runner image. So the rule applied here is by direction, never
# by size — and in this file the two dangerous sites hold the SMALLEST producers (1,171 and 256
# bytes) while the largest in the whole guard population is harmless, which is why size lost.
exit "$fail"
