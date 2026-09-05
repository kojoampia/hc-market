#!/bin/bash
# Dumps all five production databases to ./backups/ and prunes anything older than RETAIN_DAYS.
#
#   ssh webserver
#   cd /srv/healthconnect && ./backup.sh
#
# Intended for the server's root crontab, in the staggered nightly ladder the sibling stacks already
# occupy (hc-professional at 01:00, and see each stack's README for the rest). Absolute path and
# /var/log, matching their entries:
#
#   0 3 * * * /srv/healthconnect/backup.sh >> /var/log/hc-market-backup.log 2>&1
#
# NOT INSTALLED. Nothing in this repository writes a crontab, and this script has never run on a
# production host. The README says so where an operator will read it before trusting a backups/
# directory.
#
# It has now run ONCE, on 2026-09-05, on the workstation against five throwaway containers carrying
# the production names — the first execution in its life, and the review that prompted it found the
# credential handling below wrong. All five dumps came back non-empty and the mongo archive was
# readable. That is evidence about this script, and none at all about a production host or about
# whether any dump can be RESTORED, which is still the highest-value item in the README's
# outstanding list.
#
# `docker exec` rather than a published port: the databases deliberately publish none (see
# compose.yml), and the credentials in secrets.env are what authenticates here.
#
# --- FIVE DATABASES, TWO ENGINES, AND ONLY ONE OF THEM IS IRREPLACEABLE ---------------------------
#
# The gateway's MongoDB holds the USER STORE, and erasure does not touch it (decisions.md D40) — so
# it is the one database here whose contents cannot be reconstructed from anything else in the
# estate. It is dumped first for that reason: a run that fails part-way should fail having already
# secured the accounts.
#
# The four PostgreSQL instances hold the domain. They are not derivable from each other either —
# each service owns its own INSTANCE, so nothing joins them and nothing can (decisions.md D48) — but
# they are the half of the estate a replay could in principle rebuild, and the ledger in particular
# is written from booking's events.
#
# --- WHAT A DUMP OF THIS ESTATE CONTAINS ----------------------------------------------------------
#
# Customer logins, display names, booking references, prices, care summaries, conversation bodies,
# dispute reasons, and the pseudonyms of everyone who has been erased. The erasure pepper
# (decisions.md D35) is what stands between a dump and re-identifying every `erased-…` row from a
# list of guessable logins — and the pepper is in secrets.env, on the same host, in the same
# directory. A dump plus that file is a complete re-identification of every erased person.
#
# So: `umask 077` before anything is written, backups/ is created 0700, and the README says plainly
# that copying these off the host is a data-transfer decision with a legal shape (D42's residency
# question is still open), not a convenience.
set -euo pipefail
umask 077
cd "$(dirname "${BASH_SOURCE[0]}")"

RETAIN_DAYS="${RETAIN_DAYS:-14}"
SECRETS_FILE="${SECRETS_FILE:-secrets.env}"
STAMP="$(date +%Y%m%d-%H%M%S)"

[[ -s "$SECRETS_FILE" ]] || {
  echo "ERROR: $PWD/$SECRETS_FILE is missing or empty. It holds the database credentials this" >&2
  echo "       script authenticates with; see secrets.env.example in the repository." >&2
  exit 1
}

# Read one key WITHOUT sourcing the file. Sourcing would execute whatever is in it and would also
# leave every secret in this shell's environment, where a later `docker exec` could inherit it.
secret() {
  local v
  v="$(grep -m1 -E "^[[:space:]]*$1=" "$SECRETS_FILE" | cut -d= -f2- || true)"
  [[ -n "$v" ]] || { echo "ERROR: $1 is not set in $SECRETS_FILE" >&2; exit 1; }
  printf '%s' "$v"
}
# `secret` exits the script when a key is missing — but only when it is called DIRECTLY. In the
# command-prefix form used below (`PGPASSWORD="$(secret …)" docker exec …`) it runs in a substitution
# whose failure `set -e` does not propagate, so a missing key would hand the database an empty
# password and surface as an authentication error from pg_dump rather than as the name of the line
# somebody forgot. Called once here for the message, and again below for the value.
require_secret() { secret "$1" >/dev/null; }

mkdir -p backups
chmod 700 backups

# A dump that fails mid-stream still leaves a file, so every one of these is checked for size rather
# than for the exit status of a pipeline — `set -o pipefail` catches the producer, but an empty
# archive from a command that exited 0 is the case that matters.
nonempty() {
  [[ -s "$1" ]] || { echo "ERROR: $1 is empty" >&2; exit 1; }
}

# --- WHERE THE PASSWORDS ARE VISIBLE, WHICH IS NOT NOWHERE ---------------------------------------
#
# `docker exec -e NAME=value` puts the value in the argv of the HOST's docker client, and
# /proc/<pid>/cmdline is world-readable — so every dump below used to be a window in which any local
# user could read the credential out of `ps`. The comment beside it asserted the opposite ("so it
# does not appear in ps on the host"), which is worse than no comment: it is a protection somebody
# would rely on. The window is the nightly cron run, every night.
#
# Both forms below pass the NAME only and let the value travel in an environment:
#
#   PGPASSWORD=… docker exec -e PGPASSWORD …   value is in the docker client's ENVIRONMENT, and
#                                              /proc/<pid>/environ is readable by the owner and root
#                                              only — not by `ps`, and not by another local user.
#   docker exec -e MONGO_PASSWORD … bash -c …  same on the host. mongodump has no password
#                                              environment variable and no --password-file, and its
#                                              interactive prompt needs a TTY that would corrupt the
#                                              archive on stdout — so the value is expanded by a
#                                              shell INSIDE the container and does appear in that
#                                              container's own argv for the duration. Stated rather
#                                              than claimed away: reading it needs root on the host
#                                              or a process already inside the database container,
#                                              and anything with either already has the database.
#
# The values are attached as a COMMAND PREFIX rather than exported, so each one is in the environment
# of exactly one `docker` process and not of this script, its gzip, or its find. That is the same
# reason `secret` reads the file instead of sourcing it.
#
# --- MongoDB first: the user store ---------------------------------------------------------------
require_secret HC_MONGO_ROOT_USERNAME
require_secret HC_MONGO_ROOT_PASSWORD
out="backups/healthconnectGateway-${STAMP}.archive.gz"
echo "==> dumping healthconnectGateway (user store) -> $out"
# --archive to stdout, gzipped on the host: no temporary file inside the container, and the dump
# never touches the container's writable layer.
MONGO_USERNAME="$(secret HC_MONGO_ROOT_USERNAME)" MONGO_PASSWORD="$(secret HC_MONGO_ROOT_PASSWORD)" \
  docker exec -e MONGO_USERNAME -e MONGO_PASSWORD hc-market-gateway-db \
    bash -c 'exec mongodump --username "$MONGO_USERNAME" --password "$MONGO_PASSWORD" \
               --authenticationDatabase admin --db healthconnectGateway --archive --quiet' \
  | gzip > "$out"
nonempty "$out"

# --- The four PostgreSQL instances ---------------------------------------------------------------
#
# One instance per service, so one pg_dump per container. PGPASSWORD is passed BY NAME — see the note
# above on where each password is visible; `docker exec -e PGPASSWORD=<value>` would put it in the
# host's world-readable argv, which is what the comment here used to deny.
#
# --clean --if-exists so a restore into a non-empty database replaces rather than collides, and
# --no-owner because the role names are the same in every environment but the OIDs are not.
for pair in \
    "hc-market-catalog-db:healthconnectCatalog:HC_CATALOG_DB_PASSWORD" \
    "hc-market-booking-db:healthconnectBooking:HC_BOOKING_DB_PASSWORD" \
    "hc-market-messaging-db:healthconnectMessaging:HC_MESSAGING_DB_PASSWORD" \
    "hc-market-payout-db:healthconnectPayout:HC_PAYOUT_DB_PASSWORD"; do
  ctr="${pair%%:*}"; rest="${pair#*:}"; db="${rest%%:*}"; key="${rest#*:}"
  require_secret "$key"
  out="backups/${db}-${STAMP}.sql.gz"
  echo "==> dumping $db -> $out"
  PGPASSWORD="$(secret "$key")" docker exec -e PGPASSWORD "$ctr" \
    pg_dump --username "$db" --dbname "$db" --clean --if-exists --no-owner | gzip > "$out"
  nonempty "$out"
done

echo "==> pruning dumps older than ${RETAIN_DAYS} days"
find backups \( -name '*.archive.gz' -o -name '*.sql.gz' \) -mtime "+${RETAIN_DAYS}" -print -delete

echo "done. $(find backups -type f | wc -l) archive(s) in $PWD/backups"
