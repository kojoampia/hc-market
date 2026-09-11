#!/usr/bin/env bash
#
# What names are already taken on a shared docker network?
#
# decisions.md D86, backlog WP-18. D28/D30 asked whether `gateway` is already a DNS alias on
# production's shared `infranet`, and the question has sat unanswered since because it cannot be
# answered from a workstation. This is that question as one command, for somebody who is on the host.
#
# IT IS READ-ONLY IN THE STRONG SENSE. It creates no container, joins no network, pulls no image and
# writes no file — `docker network inspect` alone answers the question, because compose publishes a
# container's service name and container name as DNS aliases ON the network object, so the aliases are
# already recorded there. A probe that started a throwaway container to run `getent hosts` would be the
# obvious implementation and it would be a worse one: it changes state on a production host to learn
# something the network object already knows.
#
# WHAT THE ANSWER IS WORTH, honestly. The collision it was raised about is already impossible: D49
# renamed this product's production services to `hc-market-*` with explicit container names, so nothing
# hc-market publishes can be shadowed by, or shadow, anything else on that network. So a taken `gateway`
# would not be a defect to fix — it would be a fact to know, and the reason to know it is the NEXT
# product that joins `infranet` with a short name.
#
#   ./deploy/probe-infranet-aliases.sh                    # infranet, on the host you are logged into
#   ./deploy/probe-infranet-aliases.sh --network hcnet     # any network — used to verify this script
#   ./deploy/probe-infranet-aliases.sh --names a,b,c       # ask about other names
#
# Run it ON the host: `ssh webserver` then this, or paste it. It takes no credential and no --host,
# deliberately — this repository's production path is halted, and a script that could reach out would be
# a script somebody could point at production by accident.
#
set -uo pipefail

NETWORK='infranet'
# The five short names hc-market once used and the siblings still do. `gateway` is the one D28/D30 asked
# about; the rest are here because the answer is only interesting as a set — knowing `gateway` is taken
# and `catalog` is free tells you more than either alone.
NAMES='gateway,catalog,booking,messaging,payout'

while [ $# -gt 0 ]; do
  case "$1" in
    --network) NETWORK="${2:-}"; shift 2 ;;
    --names)   NAMES="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,33p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) printf 'unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

c_r=$'\033[31m'; c_g=$'\033[32m'; c_y=$'\033[33m'; c_d=$'\033[2m'; c_0=$'\033[0m'

printf '\n=== 1. can docker be asked? ===\n'
# STATUS-CHECKED, NOT FOLDED. D71's rule: a discarded exit status becomes a claim of absence, and
# "docker exits 1 and prints [] on stdout" is true for BOTH an absent network and an unanswerable
# daemon (measured, 29.7.2) — so the two are told apart by docker's own words and never by its status.
if ! docker_version="$(docker version --format '{{.Server.Version}}' 2>&1)"; then
  printf '  %s✗%s    docker could not be asked on this host: %s\n' "$c_r" "$c_0" "$(printf '%s' "$docker_version" | grep -m1 . || printf 'docker said nothing at all')"
  printf '       Nothing below was read. This is not an answer about %s.\n' "$NETWORK"
  exit 1
fi
printf '  %sok%s   docker server %s answers here\n' "$c_g" "$c_0" "$docker_version"

printf '\n=== 2. does %s exist? ===\n' "$NETWORK"
if ! inspected="$(docker network inspect "$NETWORK" 2>&1)"; then
  case "$inspected" in
    *'o such network'*|*'not found'*)
      printf '  %s✗%s    there is no network called %s on this host.\n' "$c_r" "$c_0" "$NETWORK"
      printf '       That IS an answer to WP-18 — no shared network means no alias to collide with —\n'
      printf '       but check the name first: the production infrastructure compose creates it, so a\n'
      printf '       host where it is absent is a host where that stack has not been brought up.\n' ;;
    *)
      printf '  %s✗%s    %s could not be inspected, and this is NOT the same as it being absent:\n' "$c_r" "$c_0" "$NETWORK"
      # THE FIRST NON-EMPTY LINE, not the first line. Measured: `docker version` against a dead
      # DOCKER_HOST prints a BLANK line and then its reason, so `head -1` yields a refusal that names
      # no cause — the exact defect this repository has found nineteen times. Caught by running it.
      printf '       %s\n' "$(printf '%s' "$inspected" | grep -m1 . || printf 'docker said nothing at all')" ;;
  esac
  exit 1
fi
printf '  %sok%s   %s exists\n' "$c_g" "$c_0" "$NETWORK"

printf '\n=== 3. every name published on it ===\n'
# One line per container per alias. `Aliases` is what compose wrote when it created the container, and
# the container's own name is also resolvable whether or not it appears there — so both are listed, and
# the source of each is named, because an alias and a container name are not equally stable: an alias
# belongs to the compose service, and a bare `docker network connect` does not restore it (D68).
published="$(
  printf '%s' "$inspected" | python3 -c '
import json,sys
try:
    nets = json.load(sys.stdin)
except Exception as e:
    sys.stderr.write("could not parse docker network inspect: %s\n" % e); sys.exit(3)
for net in nets:
    for cid, c in (net.get("Containers") or {}).items():
        name = c.get("Name") or cid[:12]
        print("%s\tcontainer-name" % name)
        for a in (c.get("Aliases") or []):
            if a and a != name:
                print("%s\talias" % a)
' 2>/dev/null
)"

if [ -z "$published" ]; then
  printf '  %s•%s    no containers are attached to %s right now.\n' "$c_y" "$c_0" "$NETWORK"
  printf '       An empty network publishes no names, so nothing is taken — but this is a reading of a\n'
  printf '       MOMENT, not of the network. If the stack that uses it is down, bring it up and re-run.\n'
else
  printf '%s' "$published" | sort -u | while IFS=$'\t' read -r nm kind; do
    printf '      %-40s %s%s%s\n' "$nm" "$c_d" "$kind" "$c_0"
  done
  printf '      %s%s name(s) published in total%s\n' "$c_d" "$(printf '%s' "$published" | sort -u | grep -c .)" "$c_0"
fi

# AN EMPTY ALIAS SET ACROSS THE BOARD IS ITS OWN FINDING, and it changes how far this answer reaches.
# Compose publishes a service name as an alias when it CREATES a container; a bare `docker network
# connect` restores the connection and not the names (D68), so a network whose containers all show only
# their own container name has been reconnected by hand at some point. The short names are genuinely
# free right now in that state — and a compose recreate would republish every service name at once. So
# "free" is then a reading of a moment rather than of the network, and it is worth saying which.
alias_count="$(printf '%s' "$published" | grep -c 'alias$' || true)"
name_count="$(printf '%s' "$published" | grep -c 'container-name$' || true)"
if [ "${name_count:-0}" -gt 0 ] && [ "${alias_count:-0}" -eq 0 ]; then
  printf '\n  %s•%s    every one of the %s containers publishes only its own name — no compose aliases at all.\n' "$c_y" "$c_0" "$name_count"
  printf '       That is D68'"'"'s state: something reconnected these by hand, and a compose recreate would\n'
  printf '       republish every service name at once. Read section 4 as "free at this moment".\n'
fi

printf '\n=== 4. are the names WP-18 asked about taken? ===\n'
taken=''
free=''
IFS=',' read -ra wanted <<<"$NAMES"
for want in "${wanted[@]}"; do
  want="$(printf '%s' "$want" | tr -d ' ')"
  [ -n "$want" ] || continue
  # EXACT, whole-field match. A substring test would report `gateway` taken because
  # `hc-market-quality-gateway` is present, which is the opposite of the answer: a long name cannot
  # shadow a short one.
  if printf '%s' "$published" | cut -f1 | grep -qxF "$want"; then
    taken="$taken $want"
    printf '  %s•%s    %-12s TAKEN by: %s\n' "$c_y" "$c_0" "$want" \
      "$(printf '%s' "$inspected" | python3 -c '
import json,sys
w=sys.argv[1]
for net in json.load(sys.stdin):
    for cid,c in (net.get("Containers") or {}).items():
        nm=c.get("Name") or cid[:12]
        if nm==w or w in (c.get("Aliases") or []):
            print(nm, end=" ")
' "$want" 2>/dev/null)"
  else
    free="$free $want"
    printf '  %sok%s   %-12s free\n' "$c_g" "$c_0" "$want"
  fi
done

printf '\n=== the answer ===\n'
if [ -n "$taken" ]; then
  printf '  On %s, these short names are already published:%s\n' "$NETWORK" "$taken"
  printf '\n  %sWhat it means for hc-market: nothing to fix.%s D49 renamed this product'"'"'s production\n' "$c_d" "$c_0"
  printf '  services to hc-market-* with explicit container names, so none of the above can shadow\n'
  printf '  anything it publishes, and it publishes none of the above. WP-18 is answered.\n'
  printf '\n  What it means for the NEXT product joining %s: these names are unavailable, and a\n' "$NETWORK"
  printf '  second container publishing one of them does not error — docker answers with whichever it\n'
  printf '  likes, silently. That is the finding worth carrying out of this probe.\n'
else
  printf '  On %s, none of these is published: %s\n' "$NETWORK" "$NAMES"
  printf '\n  %sWP-18 is answered: there was no collision to have.%s The rename that made it impossible\n' "$c_d" "$c_0"
  printf '  was belt-and-braces rather than a fix, which is worth knowing and is not a reason to undo it.\n'
fi
printf '\n  Nothing was created, joined, pulled or written by this script.\n\n'
