# hc-market on `webserver` — the production runbook

Everything that lives **on the production host** and is not shipped by a deploy: the data tier, the
nginx edge, and the three scripts that provision, back up and restart the estate.

```
compose.yml                       the five stores — 4x PostgreSQL 17, 1x MongoDB 8
secrets.env.example               the eleven required values, every one of them empty
market.abofonsa.com.conf          the pre-Certbot vhost. Installed ONCE, then owned by certbot
hc-market-app.conf                the snippet it includes. This is the file that ships on a change
nginx-conf.d/hc-market-webhook.conf   the payment webhook's rate-limit zone (http scope)
infra.sh                          creates hcmarketnet; refuses if infranet or monitoring is absent
backup.sh                         dumps all five databases; prunes by RETAIN_DAYS
start                             brings the stores up, then the applications. NOT a deploy
```

---

## Read this first: none of it has ever been run

**Nothing in this directory has touched a production host. Not one command, not one file.** It was
written on `jacserver` from the three sibling stacks and from `deploy-prod.sh`, validated with
`--dry-run` (which contacts nothing), `docker compose config`, `bash -n` and the repository's CI —
and that is the whole of its evidence.

It was then **reviewed**, on 2026-09-05, and eight things were wrong. Four of them were in this file.
The review's evidence is a little better than the first pass's, because three of the eight were found
by *running* something for the first time rather than by reading it: `backup.sh` executed against
five throwaway containers, the nginx files served by a real nginx, and `docker compose ps` watched
reporting five healthy stores while two of them had exited. The state of the far side is unchanged —
still nothing on a host — and everything below about what could not be verified still holds.

So this is a plan, not a record. Read it as one:

- **Every port, path and hostname on the far side is unverified.** `8086` is not known to be free on
  `webserver`; `/srv/healthconnect` is `deploy-prod.sh`'s default and not known to exist; the
  `infranet` and `monitoring` networks are documented by the workspace guide and were not inspected.
- **`webserver` reports `jacserver` as its own hostname**, so a hostname check cannot tell the two
  machines apart. Know which one you are on before you type anything.
- The list of what a person still has to do by hand is at the bottom, under
  [Still outstanding](#still-outstanding-the-things-only-a-person-can-do). It is the most useful
  section in this file.

The standing constraint in `docs/backlog.md` has not moved: **the production pipeline is not ready,
and `./deploy/deploy-prod.sh --dry-run` is the only thing to run against it.**

---

## The shape of it, and why it is not the siblings' shape

`hc-admin`, `hc-patient` and `hc-professional` each keep one `prod-server/compose.yml` holding
everything, because their `deploy.sh` ships that file and nothing else. hc-market's
`deploy-prod.sh` already ships `deploy/docker/docker-compose.prod.yml`, which declares the five
services, their routes, their secrets and their telemetry. Restating any of that here would be the
"written twice" failure this repository has paid for three times — the vhost port against the
compose port, the topic prefix against the script that creates the topics, the webhook route against
the gateway permit.

So the split is by **responsibility**:

| | file | who writes it on the host | when |
|---|---|---|---|
| the five applications | `deploy/docker/docker-compose.prod.yml` → `/srv/healthconnect/docker-compose.yml` | `deploy-prod.sh` | every deploy |
| the five stores | `deploy/prod-server/compose.yml` → `/srv/healthconnect/data-compose.yml` | a person | once |
| deploy state | `/srv/healthconnect/.env` | `deploy-prod.sh` | every deploy, overwritten |
| everything long-lived | `/srv/healthconnect/secrets.env` | a person | once, and never again |

Nothing appears in both compose files. **There is exactly one place in this repository where a
production gateway route is written**, and it is not in this directory.

Two compose projects, then: `healthconnect` (the apps) and `hc-market-data` (the stores). They meet
on `hcmarketnet`, which is why that network is created by `infra.sh` rather than by either of them —
a compose-created network is named after its project, and there are two projects.

### `/srv/healthconnect`, and why it is not `~/webroot/01-healthconnect/hc-market`

The siblings all live under `~/webroot/01-healthconnect/<product>/` and this stack does not, which
looked at first like the code's default winning over a host convention by accident. It is worth more
than that, and this is the argument:

**The siblings' `start` scripts acquire the platform signing key by being one directory below it.**
`hc-professional/deploy/prod-server/start` is `ENV_FILES=(--env-file ../.env --env-file .env)`, and
`../.env` from `~/webroot/01-healthconnect/hc-professional/` is `~/webroot/01-healthconnect/.env` —
the file holding the key those three products share and hc-market must **not** use (`decisions.md`
D37). hc-patient and hc-admin read it the same way. So placing
hc-market at the conventional path would put it **directly below the one file `decisions.md` D37 and
D49 spend a page keeping it away from**, with the sibling pattern that consumes it sitting one
directory over as the obvious thing to copy. `/srv/healthconnect` makes that mistake require intent
rather than a habit; this stack's `start` names `secrets.env` and nothing above itself.

**And moving later is cheap, because neither project's identity comes from the directory.**
`docker-compose.prod.yml` pins `name: healthconnect` and `compose.yml` pins `name: hc-market-data`,
so the project names and the five named volumes (`hc-market-data_gateway-data` and its four
siblings) are the same wherever the files sit. A move is copying four files and re-running `./start`
— not a data migration, and not a `docker compose down -v` under any circumstances.

It is still true that nothing has confirmed `/srv/healthconnect` exists or is writable on the host,
and it is still one `--path` flag if the convention should win. What has changed is that following
the convention now has a cost attached to it, and the cost is a signing key.

### Three networks, and only one of them is ours

`decisions.md` D27 is the rule: **borrow, never bundle**. It is about the things the host already
runs once for everybody.

| network | carries | owned by | this stack |
|---|---|---|---|
| `infranet` | the one Kafka broker, the one Consul | `~/webroot/00-infrastructure/services` | **borrows** |
| `monitoring` | the shared `otel-collector` | `~/webroot/02-monitoring/services` | **borrows** |
| `hcmarketnet` | this product's five databases | `./infra.sh` | **owns** |

Nothing in this repository declares a broker or a Consul, and nothing ever should. The databases are
the opposite call and it is not an exception — D27's own closing paragraph keeps them off every
shared network, because another product has no business resolving `hc-market-catalog-db`. They are
bundled *and* private.

`infra.sh` creates the third and **refuses** the first two rather than creating them. Creating an
empty network with the right name is the worst outcome available: `up` succeeds, every container
starts, every health check passes, and the estate is talking to nothing. A missing broker in
particular is invisible — the app starts, serves, and reports healthy while everything produced goes
nowhere.

### `market.abofonsa.com` is API-only

**The prototype is not served here, and that is a decision rather than an omission.**

Quality serves `docs/Abofonsa_BridgeCare_Marketplace.html` at
`http://market.healthconnect.local/prototype`, same-origin, with a deliberately relaxed CSP. That
page is populated with the seed: 18 invented professionals presented as real people, with names,
credentials, association registration numbers, prices and reviews. On a private LAN box, reachable
from one office, that is a demo. On a public health-services domain under the BridgeCare brand it is
eighteen fabricated practitioner listings published to the internet — findable, quotable, and
indistinguishable from the real thing to whoever arrives at the URL.

The vhost says so in its own comments and CI asserts the absence, because the plausible way this
comes back is somebody noticing the two edges differ and "restoring parity with quality". Parity is
the wrong target: quality exists to rehearse production, not the other way round.

The consequence is a good one. The only surface offered to a browser here is a JSON API, so the CSP
can be `default-src 'none'` with no exception at all — the truthful policy for something that only
ever returns JSON, and identical to the one quality carries at site level.

---

## What was taken from the siblings, and what was not

Copied in shape, adapted in content:

| sibling file | here | what changed |
|---|---|---|
| `prod-server/compose.yml` | `compose.yml` | five stores instead of one; two engines; PostgreSQL with real passwords rather than the `trust` auth quality uses; no application services at all |
| `professional.abofonsa.com.conf` | `market.abofonsa.com.conf` | one hostname, pre-Certbot, includes the snippet. The certbot-owns-this-file rule is copied verbatim because it is the one that silently deletes HTTPS |
| `hc-professional-app.conf` | `hc-market-app.conf` | no SPA, no websocket, no RUM ingest, no refresh-token limiter; a webhook limiter instead, plus the three `proxy_hide_header` lines that only an API-behind-a-gateway needs |
| `nginx-conf.d/hc-professional-{auth,rum}.conf` | `nginx-conf.d/hc-market-webhook.conf` | one zone rather than three, for the estate's one unauthenticated public POST |
| `infra.sh` | `infra.sh` | creates one network and checks two, rather than creating one and checking one |
| `backup.sh` | `backup.sh` | five databases across two engines; credentials read from `secrets.env` without sourcing it |
| `start` | `start` | two compose projects in order, with a health wait between them, and a refusal to invent a tag |
| `.env.example` | `secrets.env.example` | renamed, because the file it templates is `secrets.env` and `.env` here is generated by the deploy |

**Deliberately not copied:**

- **`hc-admin/prod-server/update-nginx.sh`** — it re-installs the edge config while preserving
  certbot's TLS block, and exists because `hc-admin`'s `deploy.sh --with-nginx` would otherwise
  destroy HTTPS. hc-market's `deploy-prod.sh` has no `--with-nginx` and installs no nginx at all, so
  there is nothing to guard against. **`/etc/nginx` is the architect's on both machines**; this
  repository stages configuration and prints the sudo line. Writing a script that edits the live edge
  would be claiming an ownership hc-market does not have.
- **`hc-admin/prod-server/sync-platform-services.sh`** — fills the admin console's platform-health
  grid from Mimir. hc-market has no console and no such collection.
- **`hc-admin/prod-server/rotate-mongo-password.sh`** — a real hazard that applies here (see
  [Rotating a credential](#rotating-a-credential)), and deliberately documented rather than scripted.
  hc-market has five stores across two engines and the Mongo case is one of them; a script covering
  one fifth of the problem invites the belief that the other four are covered too. When there is a
  rotation to do, this is the first thing to write.
- **`hc-patient/prod-server/observability/`** — hc-market already has the equivalent at
  `deploy/observability/hc-market-rules.yaml`, and CI parses it. Moving it here would break that
  path for no gain.
- **`hc-patient/prod-server/hc-patient-rum.conf`** and the browser-telemetry `location = /v1/traces`
  — those exist to ingest spans from an Angular SPA. There is no SPA here (spec §2), no browser
  posting OTLP, and adding a public unauthenticated telemetry endpoint that nothing uses would be a
  new attack surface bought with nothing.
- **The `web` container and the second nginx hop.** hc-market is API-only, so the edge proxies
  straight to the gateway. `quality/README.md` states the cost and it holds here: the class of defect
  a two-hop chain exists to catch — a header or a scheme mangled between two nginxes — cannot occur,
  and cannot be rehearsed either.
- **A `deploy.sh` in this directory.** The siblings each have one because each is one repo per
  component; hc-market is a monorepo with one `deploy/deploy-prod.sh` covering all five services.
  `./start` is the on-host restart path and deliberately cannot choose a tag.

---

## First deploy, from nothing

Nine steps. Steps 1–6 are on the server, 7–9 from a workstation. **Steps 4 and 5 need someone with
sudo, and this repository runs neither.**

### 0. Confirm you are on the right machine, and that the port is free

`webserver` reports `jacserver` as its hostname, so ask the address:

```bash
ip -4 addr | grep -w inet          # 199.247.5.252 is webserver; 192.168.1.2 is the workstation
ss -ltnp | grep -w 8086            # must be EMPTY
```

**8086 is a guess.** It sits in the family the siblings use for their loopback ports (hc-admin 8083,
hc-patient 8085, hc-professional 5503) and nothing here has ever checked it against this host. If it
is taken, change it in **two** places in the same commit — `HC_GATEWAY_PORT`'s default in
`deploy/docker/docker-compose.prod.yml` and both `proxy_pass` lines in `hc-market-app.conf` — because
nginx cannot read the environment and CI checks the two agree.

### 1. Put the files on the host

```bash
ssh webserver 'mkdir -p /srv/healthconnect'
scp deploy/prod-server/compose.yml webserver:/srv/healthconnect/data-compose.yml
scp deploy/prod-server/infra.sh deploy/prod-server/backup.sh deploy/prod-server/start \
    webserver:/srv/healthconnect/
ssh webserver 'chmod +x /srv/healthconnect/infra.sh /srv/healthconnect/backup.sh /srv/healthconnect/start'
```

**`/srv` is root-owned**, so `mkdir -p /srv/healthconnect` needs root — which the ssh target here is
(see [step 7](#7-deploy) on why `webserver` and not a `deploy` user). If it is ever not, this is the
line that fails first, and the answer is to create the directory with sudo once and `chown` it.

The three paths are written out rather than braced: `{infra.sh,backup.sh,start}` is a bashism, the
remote login shell is not guaranteed to be bash, and under `dash` it expands to nothing and `chmod`
fails on a literal `/srv/healthconnect/{infra.sh,backup.sh,start}`.

`data-compose.yml`, not `compose.yml`: `deploy-prod.sh` writes `docker-compose.yml` into the same
directory on every deploy, and two files whose names differ by a hyphen in one directory is a mistake
waiting for a tired evening. **The rename is load-bearing and no longer only by convention** —
compose prefers `compose.yml` over `docker-compose.yml` when it discovers a file itself, so a data
tier copied under its repository name would have captured every later `pull`, `up`, `exec` and `ps`.
`deploy-prod.sh` now names `-f docker-compose.yml` on every remote invocation, so the wrong file
cannot be picked up silently; keep the rename anyway, because two compose files in one directory with
one of them named by discovery is a trap waiting for the next script.

### 2. Create `secrets.env`

**Eleven values, all required, none of them committed anywhere.** The template with a generation
command beside each is [`secrets.env.example`](secrets.env.example). Create the real file **on the
server**, so no value ever exists in a local shell history, a synced dotfile or a chat log:

```bash
ssh webserver
mkdir -p /srv/healthconnect && cd /srv/healthconnect
umask 077 && cat > secrets.env      # paste, filled in, then Ctrl-D
chmod 600 secrets.env
```

Three of the eleven need saying out loud.

**`JWT_BASE64_SECRET` is generated fresh and is NOT the platform key.**

```bash
head -c 64 /dev/urandom | base64 -w0
```

`~/webroot/01-healthconnect/.env` holds the key `hc-admin`, `hc-patient` and `hc-professional`
share, and hc-market is deliberately not in that set (`decisions.md` D37). Copying it here would
change nothing visible — HS512 does not care which random bytes it is — while silently giving these
five services the ability to mint tokens the other three products accept, and giving an `hc-admin`
token authority here. `deploy-prod.sh`'s own hint said to copy it until 2026-09-05.

**`HC_PRIVACY_PEPPER` is generated once and kept for ever.**

```bash
head -c 32 /dev/urandom | base64 -w0
```

It keys the HMAC behind an erased customer's alias (`decisions.md` D35). Aliases are written into
rows in place and **nothing re-keys them**, so a rotation is indistinguishable from a deletion as far
as those rows are concerned: messaging stops recognising its own erased subjects, and nothing reports
it. If this host has ever run an erasure, the existing value is the only correct one and it cannot be
recovered from the data — so it belongs in whatever the organisation uses for key escrow, and this is
the only line in the file for which that is true.

**The Mongo password must be hex, not base64.** It is interpolated into a `mongodb://` URI where
base64's `+`, `/` and `=` are not legal unescaped: the `/` ends the userinfo section and the driver
rejects the rest as an invalid `host:port`, so the gateway fails to start with a message pointing at
the database rather than at the password. `openssl rand -hex 24` is 192 bits, no weaker.

### 3. Create the network and bring the stores up

```bash
ssh webserver
cd /srv/healthconnect
./infra.sh          # creates hcmarketnet; fails if infranet or monitoring is missing
./start             # stores up, waited for; then says no deploy has landed yet
```

`./start` exits cleanly at that point and tells you to deploy. That is correct: there is no
`docker-compose.yml` and no `.env` yet.

**The health wait asserts five, and looks at stopped containers too.** `docker compose ps` lists only
*running* containers — `-a` is needed for the rest — so a store that has **exited** appeared in no
listing, a filter for "not healthy" found nothing, and the script printed `all five stores healthy`
and started the applications against dead databases. If all five had exited the output was empty and
the gate was unanimous about it. The likeliest way to reach that state is the one this file documents
under [Rotating a credential](#rotating-a-credential): a Mongo datadir whose credentials no longer
match. It now requires five services present and all five healthy, so an exited store is reported and
a *removed* one — which appears in no listing at all, with or without `-a` — is caught by the count.

`./start` also **pulls nothing**. Its header always said so; a `pull` of the application stack had
crept in beneath it, which matters in exactly the case [step 7](#7-deploy) creates —
`deploy-prod.sh --build` overwrites what CI published at a tag, after which a post-reboot `./start`
would silently fetch different bytes for the same tag. Bringing back exactly what was running is the
contract. (`up -d` still fetches an image that is not on the host at all; that is a pruned image,
not a re-pushed tag.)

### 4. DNS — someone else's console, not this repository's

Point `market.abofonsa.com` at **199.247.5.252** (A record; AAAA too if the host has one). Wait for
it to resolve from off-host before step 5 — certbot's HTTP-01 challenge fetches
`http://market.abofonsa.com/.well-known/acme-challenge/…` from the public internet, and a name that
has not propagated fails in a way that reads as a broken nginx.

```bash
dig +short market.abofonsa.com @1.1.1.1
```

### 5. nginx and TLS — needs sudo, and this repository runs none of it

**`/etc/nginx` belongs to the architect on both machines.** The repository provides the files and
stages them; installing, symlinking, `nginx -t` and reloading are done by a person. Following
`quality/startup.sh`'s example, these lines are printed and never executed.

**Read the installed file before you overwrite anything.** The installed copy drifts from the
repository's, and sometimes deliberately.

The zone file goes **first**. `limit_req_zone` is `http`-scope, so enabling the snippet without it
fails `nginx -t` with "unknown limit_req zone" — and a failed test refuses the reload for **every
site on this host**, not only this one.

```bash
scp deploy/prod-server/nginx-conf.d/hc-market-webhook.conf webserver:/tmp/
scp deploy/prod-server/hc-market-app.conf                  webserver:/tmp/
scp deploy/prod-server/market.abofonsa.com.conf            webserver:/tmp/

ssh webserver
sudo mv /tmp/hc-market-webhook.conf   /etc/nginx/conf.d/
sudo mv /tmp/hc-market-app.conf       /etc/nginx/snippets/
sudo mv /tmp/market.abofonsa.com.conf /etc/nginx/sites-available/hc-market.conf
sudo ln -sfn /etc/nginx/sites-available/hc-market.conf /etc/nginx/sites-enabled/hc-market.conf
sudo nginx -t && sudo systemctl reload nginx
```

Then TLS, which **rewrites the vhost in place**:

```bash
sudo certbot --nginx -d market.abofonsa.com
```

**After this, `market.abofonsa.com.conf` in this repository is no longer the file on the server**, and
must never be copied over it again. Certbot's version has two server blocks, the certificate paths
and the HTTP→HTTPS redirect; the repository's has one server block and none of that. Copying over it
removes HTTPS, `nginx -t` **passes** because the result is valid nginx, the reload succeeds, and the
site drops to plain HTTP with every check still green. Both sibling stacks record this; neither
learned it gently.

Do not hand-write the `listen 443 ssl` block instead. A block referencing `/etc/letsencrypt` paths
that do not exist yet stops nginx starting at all, so the site cannot be enabled to serve the
challenge that would create them — and it breaks automatic renewal.

Everything that changes with a release lives in the **snippet**, which is safe to re-ship:

```bash
scp deploy/prod-server/hc-market-app.conf webserver:/tmp/
ssh webserver 'sudo mv /tmp/hc-market-app.conf /etc/nginx/snippets/ \
  && sudo nginx -t && sudo systemctl reload nginx'
```

### 6. Confirm CI published the images you are about to deploy

```bash
git log --format='%H %s' -1 origin/main
gh api "/users/kojoampia/packages/container/hc-market-catalog/versions" --jq '.[0].metadata.container.tags'
```

`.github/workflows/release.yml` publishes all five to `ghcr.io/kojoampia/hc-market-<service>` on push
to `main`, tagged by commit SHA. **A green workflow is not proof the images exist** — `decisions.md`
D14 records a run that exited 0 having pushed three of five, with `catalog` and `booking` holding
`latest` only and their SHA tags 404 twenty minutes later. The workflow's `verify` job now performs
the token-then-manifest handshake for each, and re-running the workflow is the fix.

### 7. Deploy

From a **workstation**, not from the server:

**`webserver`, as root.** Every sibling deploys to the ssh alias `webserver`
(`hc-professional/deploy/deploy.sh`: `SSH_HOST="${SSH_HOST:-webserver}"`), and `hc-professional`'s
own `backup.sh` gives its installed cron path as `/root/webroot/…`, so `~` there is `/root` and the
account is root. The alias also picks up whatever `~/.ssh/config` says about port, key and user,
which an IP literal does not. This line named a `deploy@` user until 2026-09-05 — a copy-pasteable
assumption in a file that is otherwise scrupulous about listing what it could not check. **The ssh
user is still unverified from here**; what changed is that it is now the same guess every other
stack on that host makes rather than a new one.

```bash
export HC_PROD_HOST=webserver                   # the ~/.ssh/config alias, and root there
export GHCR_OWNER=kojoampia GHCR_TOKEN=…        # a PAT with read:packages

./deploy/deploy-prod.sh --channel github \
  --tag 1eadc7a43db09eb8e9928909c2c3494854890cf6 --dry-run     # ALWAYS this first
./deploy/deploy-prod.sh --channel github \
  --tag 1eadc7a43db09eb8e9928909c2c3494854890cf6
```

**The full 40-character SHA, always, on the github channel.** GHCR has no nested-path namespaces, so
the channel switches the image *separator* as well as the host —
`ghcr.io/kojoampia/hc-market-catalog:<sha>` against
`docker.jojoaddison.net/healthconnect/catalog:<tag>` — and a **short SHA names an image that only
ever existed in the private registry**. Rolling to one on the github channel is a pull that 404s
mid-deploy; rolling to one that *is* on the mirror needs `--channel local` with it, and then you are
running an artefact no quality box ever verified.

`--dry-run` contacts nothing — not ssh, not the registry, not the host. It says so on each check it
skips (`○ [dry-run] … NOT contacted`) rather than printing a tick beside a check it did not perform.
`--rollback --dry-run` is safe too; it used to read the previous tag over ssh, which is a dry run
touching the host, and no longer does.

What the deploy does, in order: preflight (registry login, ssh, **all eleven `secrets.env` keys by
name**, all three networks, **and the five stores running**) → verify the images are in the registry
→ upload the compose file and generate `.env`, keeping the old one as `.env.previous` → pull →
`up -d` → health gate → smoke test. If the gates fail it **rolls back by itself** and exits non-zero.

The data-tier check is the newest of the preflight's and it closes a gap the network check could not
see: `hcmarketnet` existing says nothing about anything being **on** it. A deploy rolled while the
stores were down passed preflight in full, rotated `.env`, pulled, rolled, failed Liquibase in all
five services and then rolled back — a five-service outage caused by the deploy, over a condition
that was true before it started. It counts `docker compose ... ps -a` rather than listing what is
unhealthy, for the reason in [step 3](#3-create-the-network-and-bring-the-stores-up).

**On the first deploy there is nothing to roll back to**, and the script now says so: if the gates
fail it reports that the stack is still running the tag just deployed and was not reverted, rather
than only that `.env.previous` is missing.

It does **not** build. `DO_BUILD` defaults to 0: images are built by CI, tagged by commit, and a
deploy chooses one. `--build` exists for an unreleased tag CI has never seen and overwrites what CI
published at that tag.

### 8. Verify

```bash
curl -fsS https://market.abofonsa.com/services/healthconnectcatalog/api/professionals/count
```

Production does not seed (double-locked: the `test & dev` profile pair *and*
`healthconnect.seed.enabled`, and `deploy-prod.sh` writes `HEALTHCONNECT_SEED_ENABLED=false` into
every `.env`), so on a fresh estate **the honest answer is `0`** — which is also what a catalog
talking to an empty database says, and what a catalog that cannot reach its database does not say at
all.

**That last distinction is the whole of the smoke test, and until 2026-09-05 it did not make it.**
The check required `> 0`, and a failing smoke test does not warn — it falls through to `rollback`. So
the first deploy would have ended in `no previous deployment recorded`, with the stack up, correct
and never written to `deployments.log`; and the second, still empty, would have **successfully rolled
back a deployment that had just come up healthy**. The estate could not have shipped again until it
had data. This file said "expect it to warn", and that was not what the code did.

It now separates the two answers it was conflating: **no number at all** is a failure (the edge, the
route, the gateway or catalog's datasource), and **`0`** is warned about loudly and passed. A count of
`0` exercises DNS, TLS, nginx, D28's route predicates and a round trip to PostgreSQL exactly as a
count of 18 would.

The `> 0` requirement is still available, as the floor it always was:

```bash
HC_SMOKE_MIN_PROFESSIONALS=1 ./deploy/deploy-prod.sh --channel github --tag <sha>
```

Set it once there is real data. From that day an estate answering `0` **is** a failure and should
roll back; only an operator knows when that day is, which is why it is opt-in rather than default.

Check the headers as well as the status, because a wrong-app collision answers 200:

```bash
curl -sI https://market.abofonsa.com/services/healthconnectcatalog/api/professionals/count \
  | grep -iE 'content-security-policy|x-robots-tag|referrer-policy'
```

Exactly one of each, `default-src 'none'`, `noindex, nofollow`, `same-origin`. Two `Referrer-Policy`
values means the `proxy_hide_header` lines are missing and the gateway's copy is getting through —
a duplicated header with conflicting values resolves differently in different browsers.

And confirm the closed things are closed:

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://market.abofonsa.com/management/info        # 404
curl -s -o /dev/null -w '%{http_code}\n' https://market.abofonsa.com/api/professionals/count # 404, no route
curl -s -o /dev/null -w '%{http_code}\n' \
  https://market.abofonsa.com/services/healthconnectcatalog/internal/professionals/p1/login  # 404, no route
```

**The last one is the important one.** catalog's `/internal/**` authenticates nobody, and the only
thing keeping it off the internet is that no gateway route matches it (`decisions.md` D28). If it
answers anything but 404, stop and read the route predicates.

And confirm the **open** things are the ones meant to be open, which is the same check pointed the
other way. The gateway's `SecurityConfiguration` permits four paths anonymously — `/api/register`,
`/api/activate` and both halves of the password reset — so **self-registration is public on
`market.abofonsa.com` from the first deploy**:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://market.abofonsa.com/api/register  # NOT 401
```

That is JHipster's generated posture and is presumably what a two-sided marketplace wants: a customer
who cannot create an account cannot book. It is written down here because it is the one surface on
this hostname that a stranger can *write* to without a signature, it is open by inheritance rather
than by a decision anybody recorded, and three lines above this one we curl three things to prove the
closed things are closed. If open registration is **not** wanted, that is a change to the gateway's
`SecurityConfiguration` and a decision in `decisions.md`, not an nginx rule — a `location` blocking it
would put a security control in two files that have to agree, which is exactly what
`hc-market-app.conf` refuses to do for the routed paths.

The two end-to-end scripts — `deploy/verify-cycle.sh` and `deploy/verify-outbox-recovery.sh` — are
**not** for this estate. They create data and one of them severs a container from its network; both
are parameterised for dev and quality, and neither has ever been pointed at a production host.

### 9. Record what you deployed

`deploy-prod.sh` appends to `/srv/healthconnect/deployments.log` on success. Read it before the next
deploy, and before any rollback.

---

## Rolling back

```bash
./deploy/deploy-prod.sh --rollback --host "$HC_PROD_HOST" --dry-run
./deploy/deploy-prod.sh --rollback --host "$HC_PROD_HOST"
```

This reads `HC_TAG` from `.env.previous` on the host, restores that file, pulls and rolls, and
re-runs the health gate — dying loudly if the rollback is *also* unhealthy, because at that point a
person is needed rather than another automatic step.

To roll to something that is not the immediately previous deploy, name it:

```bash
./deploy/deploy-prod.sh --channel github --tag <full-40-char-sha>
```

**The full 40 characters.** A short SHA on the github channel names an image that only ever existed
in `docker.jojoaddison.net`, so the pull 404s on the host, mid-deploy, after `.env` has already been
rotated. The sibling products have exactly this rule for the same reason.

**A rollback does not touch `secrets.env`.** It restores `.env.previous`, which holds the previous
deploy's non-secret values and nothing else — so it cannot take a secret back to an older value.
Before that split, a secret hand-added to `.env` survived a rollback and not a deploy, and the two
paths disagreed about what the stack would even come up with.

**A rollback does not roll the schema back.** Liquibase changelogs are applied forward and the
generated ones are a fresh install rather than a migration, so rolling the *images* back onto a
database an old version has never seen is not a supported move and has never been tried here. If a
release contained a schema change, a rollback is a restore-from-dump question, not a `--tag`
question.

---

## When it goes wrong

**Start by asking which layer.** From the host, inside-out:

```bash
ssh webserver
cd /srv/healthconnect

# 1. are the stores up and healthy?
docker compose --env-file secrets.env -f data-compose.yml ps

# 2. are the applications up? (both --env-file arguments, or interpolation dies on a :? check)
docker compose --env-file .env --env-file secrets.env -f docker-compose.yml ps
docker compose --env-file .env --env-file secrets.env -f docker-compose.yml logs --tail=100 hc-market-catalog

# 3. does the gateway answer on loopback, behind nginx?
curl -s localhost:8086/services/healthconnectcatalog/api/professionals/count

# 4. does nginx answer?
curl -sI https://market.abofonsa.com/services/healthconnectcatalog/api/professionals/count
```

| symptom | likely cause |
|---|---|
| `502` from nginx, gateway healthy | the port is written twice and they disagree — `hc-market-app.conf` against `HC_GATEWAY_PORT` |
| every service dies at `up` naming a variable | a key missing from `secrets.env`. All eleven are checked in preflight *before* the stack is touched, so this means someone ran `docker compose` by hand |
| `UnknownHostException: hc-market-catalog-db` | the app container is not on `hcmarketnet`, or `infra.sh` was never run |
| services healthy, nothing ever happens | **the broker.** A missing one is silent: everything produced goes nowhere. `MessageDeliveryException` on a timer is the *only* signal — its absence after a change to how the broker is addressed is worth checking for |
| the erasure desk answers `503` | `HC_PRIVACY_PEPPER` is absent. The services start deliberately rather than refusing to boot, so that an outage behind one value does not have somebody paste in a plausible one |
| messaging refuses to start | the pepper **changed**, and messaging's register already holds rows. It compares a sentinel alias in its own table. Unpeppered it would write real logins back for people a sibling has erased |
| the estate is up but nothing is on the dashboards | the `monitoring` network, or the OTel agent missing from an image. `service:up:current` is derived from `jvm_thread_count`, which the agent reports whether or not it instruments anything — **a green tile is not evidence** |
| a provider's callback never arrives | the route **and** the gateway permit are two changes; the route alone is 401 at the edge, with nothing in booking's log |
| every priced booking naming a provider answers `502` | that provider was enabled. All three adapters are unimplemented seams (`decisions.md` D45) and fail closed by design |

**`docker compose down` on the wrong file takes the databases with it.** The stores are project
`hc-market-data`; the apps are project `healthconnect`. `down -v` on the first destroys five volumes
and there is no undo. Take a dump first, every time.

---

## Backups

`backup.sh` dumps all five databases into `/srv/healthconnect/backups/` and prunes past
`RETAIN_DAYS` (14). It reaches the containers with `docker exec` because they publish no ports, and
reads credentials out of `secrets.env` **without sourcing it** — sourcing would execute the file's
contents and leave every secret in the shell's environment.

**Where the passwords are visible while a dump runs**, because the script used to claim they were
nowhere. `docker exec -e PGPASSWORD=<value>` puts the value in the argv of the *host's* docker
client, and `/proc/<pid>/cmdline` is world-readable — so for the length of each nightly dump any
local user could read the credential out of `ps`, under a comment saying that was exactly what the
`-e` form prevented. Both engines now pass the **name** and let the value travel in an environment
(`/proc/<pid>/environ`, owner and root only), attached as a command prefix so it is in one `docker`
process and not in the script's own environment. One residue is stated rather than claimed away:
`mongodump` has no password environment variable, no `--password-file`, and an interactive prompt
that needs a TTY which would corrupt the archive on stdout — so its value is expanded by a shell
*inside* the gateway's database container and is in **that container's** argv for the duration.
Reading it needs root on the host or a process already inside that container, and either of those
already has the database.

**It is not installed.** Nothing here writes a crontab. The sibling stacks sit in a staggered nightly
ladder (hc-professional at 01:00); 03:00 is free of them:

```bash
sudo crontab -e
0 3 * * * /srv/healthconnect/backup.sh >> /var/log/hc-market-backup.log 2>&1
```

**A dump of this estate is the most sensitive artefact the product produces.** It contains customer
logins, display names, care summaries, conversation bodies, dispute reasons, prices, and the
pseudonyms of everyone who has been erased. The pepper is what stands between those pseudonyms and
re-identifying every erased person from a list of guessable logins — and it is in `secrets.env`, on
the same host, in the same directory. **A dump plus that file is a complete reversal of every
erasure this estate has performed.** Copying either off the host is a data-transfer decision with a
legal shape, and `decisions.md` D42 records that the residency transfer basis is still an open
question with no document behind it.

The Mongo dump is taken first because it is the one database whose contents cannot be reconstructed
from anything else: erasure does not touch the gateway's user store (`decisions.md` D40), so that is
where the accounts live.

**The script now runs; the backups are still unproven.** It was executed for the first time on
2026-09-05, on the workstation, against five throwaway containers carrying the production names —
five non-empty archives, the mongo one readable, `backups/` at 0700 and each file at 0600 — and that
run is what found the credential handling above. What it says nothing about is a production host, and
nothing at all about **restoring**, which has never been tried anywhere. That is still the
highest-value item in the outstanding list below: an unrestored backup is a belief, not a backup.

---

## Rotating a credential

**Read this before changing any value in `secrets.env`.** Three of the eleven behave differently and
two of them are traps.

**`HC_PRIVACY_PEPPER` — do not.** Nothing re-keys an alias already written. If anything has ever been
erased on this host, changing it orphans every one of those rows, messaging refuses to start, and
there is no path back except the old value.

**The Mongo credentials — order matters and is not obvious.**
`MONGO_INITDB_ROOT_USERNAME`/`PASSWORD` are read by the image **only when it initialises an empty
data directory**. Once the volume has data, editing `secrets.env` does not change the account — it
only makes `HC_GATEWAY_MONGODB_URI` stop authenticating, and the gateway comes back unhealthy with no
clue as to why. The account has to be changed *inside* Mongo first, then in both values here.
`hc-admin/deploy/prod-server/rotate-mongo-password.sh` does exactly this for one database and is the
right thing to adapt; it is deliberately not copied (see above).

**The four PostgreSQL passwords — same shape, same trap.** `POSTGRES_PASSWORD` initialises an empty
data directory and does nothing afterwards. Change the role inside the database (`ALTER ROLE … WITH
PASSWORD …`), then the one line in `secrets.env` that both compose projects read, then recreate.

**`JWT_BASE64_SECRET`** can be rotated, and it signs everybody out. It affects hc-market alone —
the sibling three share a different key and hc-market is not in that set (`decisions.md` D37), so
this is not the three-stack cutover the workspace guide describes.

---

## Still outstanding: the things only a person can do

None of this has been done, and none of it can be done from a workstation.

**Before a first deploy could succeed at all**

1. Confirm `/srv/healthconnect` exists on `webserver` and is writable, and that `webserver` resolves
   to a root account in `~/.ssh/config`. The two path conventions disagree and **the disagreement is
   now argued rather than merely noted** — see
   [`/srv/healthconnect`, and why it is not `~/webroot/01-healthconnect/hc-market`](#srvhealthconnect-and-why-it-is-not-webroot01-healthconnecthc-market).
   The short version: the conventional path sits directly below the platform signing key this product
   must not use, with a sibling `start` pattern that reads `../.env` one directory over. Still one
   `--path` flag if the convention should win, and still a cheap move either way, because both
   compose files pin `name:`.
2. Confirm **8086** is free on the host, and change it in **two** files if not.
3. Confirm `infranet` and `monitoring` exist and that the broker and Consul are actually on them.
   `deploy-prod.sh` checks the networks; nothing checks that anything is listening.
4. Answer **backlog WP-18**: whether `gateway`, `catalog` or `booking` is already a DNS alias on
   `infranet`. Largely defused — every service is `hc-market-*` with an explicit `container_name`, so
   a collision is impossible whatever else is there — but the question itself is still unanswered on
   the host.
5. Create `secrets.env`, with a **freshly generated** signing key and a pepper that goes into escrow.
6. Point DNS at the host and wait for it to resolve publicly.
7. Install the three nginx files and run `certbot --nginx`. **Needs sudo. Read the installed files
   first.**
8. Run `./infra.sh` and `./start`.

**Before it could be trusted**

9. Install `backup.sh` in cron, then **restore one dump into a scratch database and confirm it comes
   back**. Untested backups are the most expensive kind of belief in this file.
10. Decide whether alert rules from `deploy/observability/hc-market-rules.yaml` should be mounted per
    application into the host's Mimir. They are mounted per application everywhere else in the estate,
    never appended to a shared fleet file, so a YAML mistake costs one app's alerting.
11. Verify the OTel agent is **instrumenting**, not merely loading. A green `service:up` tile is
    derived from `jvm_thread_count`, which the agent reports from MBeans whether or not it rewrites a
    single application class. The check that counts is a `SERVER` span carrying `http.route`.
12. Run the estate's own end-to-end shape against production **once**, by hand and with intent, and
    decide first what data it is acceptable to create. The two scripts in `deploy/` are not it.

**Not engineering at all, and blocking more than it looks**

13. The **DPC registration number** (`decisions.md` D42, backlog WP-09). Counsel answered
    "registered"; the number was not supplied. `HC_DPC_REGISTRATION` has no fallback anywhere, blank
    counts as absent, and `GET /api/desk/privacy` reports `null` — which is the honest answer and is
    why a placeholder must not be invented. **The privacy notice and the processing record cannot be
    published without it**, and this hostname is where a data subject would go looking for them.
14. The **data-residency transfer basis** — a written document, and a decision about whether the
    estate needs one or six, since `webserver` hosts all six products. Directly relevant to where a
    dump may be copied.
15. **Act 987** and the payment providers. All three adapters are seams that fail closed; enabling
    one makes every priced booking naming it answer 502. Whether a split-settlement model clears the
    Act is a question for a person with standing, and `PaymentProvider` still has no method that pays
    the professional — which is what keeps the answer cheap either way.
