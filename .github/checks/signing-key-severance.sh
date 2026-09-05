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
  if printf '%s' "$arm" | grep -qE "$PLATFORM_ENV"; then
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
  if printf '%s' "$msg" | grep -qF 'platform' && ! printf '%s' "$msg" | grep -qF 'NOT the platform key'; then
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
exit "$fail"
