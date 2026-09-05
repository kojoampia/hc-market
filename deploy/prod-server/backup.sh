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
# NOT INSTALLED. Nothing in this repository writes a crontab, and this script has never been run on
# any host. The README says so where an operator will read it before trusting a backups/ directory.
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

mkdir -p backups
chmod 700 backups

# A dump that fails mid-stream still leaves a file, so every one of these is checked for size rather
# than for the exit status of a pipeline — `set -o pipefail` catches the producer, but an empty
# archive from a command that exited 0 is the case that matters.
nonempty() {
  [[ -s "$1" ]] || { echo "ERROR: $1 is empty" >&2; exit 1; }
}

# --- MongoDB first: the user store ---------------------------------------------------------------
MONGO_USER="$(secret HC_MONGO_ROOT_USERNAME)"
MONGO_PASS="$(secret HC_MONGO_ROOT_PASSWORD)"
out="backups/healthconnectGateway-${STAMP}.archive.gz"
echo "==> dumping healthconnectGateway (user store) -> $out"
# --archive to stdout, gzipped on the host: no temporary file inside the container, and the dump
# never touches the container's writable layer.
docker exec hc-market-gateway-db mongodump \
  --username "$MONGO_USER" --password "$MONGO_PASS" --authenticationDatabase admin \
  --db healthconnectGateway --archive --quiet | gzip > "$out"
nonempty "$out"

# --- The four PostgreSQL instances ---------------------------------------------------------------
#
# One instance per service, so one pg_dump per container. PGPASSWORD is passed with `docker exec -e`
# rather than on the command line so it does not appear in `ps` on the host.
#
# --clean --if-exists so a restore into a non-empty database replaces rather than collides, and
# --no-owner because the role names are the same in every environment but the OIDs are not.
for pair in \
    "hc-market-catalog-db:healthconnectCatalog:HC_CATALOG_DB_PASSWORD" \
    "hc-market-booking-db:healthconnectBooking:HC_BOOKING_DB_PASSWORD" \
    "hc-market-messaging-db:healthconnectMessaging:HC_MESSAGING_DB_PASSWORD" \
    "hc-market-payout-db:healthconnectPayout:HC_PAYOUT_DB_PASSWORD"; do
  ctr="${pair%%:*}"; rest="${pair#*:}"; db="${rest%%:*}"; key="${rest#*:}"
  out="backups/${db}-${STAMP}.sql.gz"
  echo "==> dumping $db -> $out"
  docker exec -e PGPASSWORD="$(secret "$key")" "$ctr" \
    pg_dump --username "$db" --dbname "$db" --clean --if-exists --no-owner | gzip > "$out"
  nonempty "$out"
done

echo "==> pruning dumps older than ${RETAIN_DAYS} days"
find backups \( -name '*.archive.gz' -o -name '*.sql.gz' \) -mtime "+${RETAIN_DAYS}" -print -delete

echo "done. $(find backups -type f | wc -l) archive(s) in $PWD/backups"
