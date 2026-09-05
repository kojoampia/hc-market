# HealthConnect Marketplace — decisions log

Answers to the gaps and issues raised before implementation began. This resolves spec §13 open
question #1 and the seven seed↔JDL conflicts recorded in `../CLAUDE.md`.

**Date:** 24 August 2026 · **Decided by:** Kojo Ampia-Addison · **Spec:** `healthconnect-marketplace.md`

---

## Environment as found

Verified on this machine, not assumed:

| Tool | Version | Note |
|---|---|---|
| JHipster | **9.2.0** (global) | generates Spring Boot **4.0.7**; Boot 3 fallback is 3.5.15 |
| Node | 24.18.0 | npm 11.16.0 |
| Maven | 3.9.15 | `~/.mvn/bin/mvn` |
| Docker | 29.7.2, Compose v5.5.0 | daemon reachable |
| JDKs with a real `javac` | 17.0.19, 21.0.11, **25.0.2 (Oracle)**, 26 | `/usr/lib/jvm/java-25-openjdk-amd64` is a **JRE — no `javac`** |

Spec §13 #1 ("JHipster 9.x or an 8.x scaffold with a hand-managed BOM?") is therefore **moot**:
9.2.0 is installed and emits Boot 4.0.7 natively. No BOM surgery is needed.

---

## D1 — Gateway runs on MongoDB, so the whole estate is Spring Boot 4

**The problem, found by probe-generating rather than by reading docs.** JHipster 9.2.0 decides the
Boot generation with one rule:

```js
springBoot4: data => !(data.databaseTypeSql && data.reactive) && !data.databaseTypeCouchbase
```

A JHipster gateway is **always** `reactive: true` — it is Spring Cloud Gateway. So the spec's
`databaseType sql` gateway trips both halves of the conjunction and silently drops to Boot 3.
Confirmed by generating all three shapes:

| Probe | reactive | Spring Boot | `<java.version>` |
|---|---|---|---|
| gateway + `sql` | `true` (forced) | **3.5.15** | 21 |
| gateway + `mongodb` | `true` (forced) | **4.0.7** | 21 |
| microservice + `sql` | `false` | **4.0.7** | 21 |

"Spring Boot 4 everywhere" and "PostgreSQL everywhere" cannot both hold.

**Decision: the gateway owns users on MongoDB; the four domain services keep PostgreSQL.**

```
gateway     mongodb    reactive     Boot 4.0.7    users, JWT, routing, rate limiting
catalog     postgres   imperative   Boot 4.0.7    categories, professionals, services, reviews
booking     postgres   imperative   Boot 4.0.7    the booking aggregate
messaging   postgres   imperative   Boot 4.0.7    threads, messages, notifications
payout      postgres   imperative   Boot 4.0.7    brokerage config, ledger, payouts
```

Rationale: this is already the proven idiom in `hc-admin`, `hc-patient` and `hc-professional` —
every one of them runs a reactive MongoDB gateway that owns users and issues JWTs. The gateway
stores accounts and nothing else, so it has no need of SQL. Every place the design actually leans on
SQL — the `professional_rating` view, the ledger aggregates, the same-slice-of-days month-to-date
comparison — lives in a domain service, and those stay on PostgreSQL 17.

**Amends spec §4:** the `healthconnect-gateway` row's database becomes MongoDB, not `hc_gateway`.
Nothing else in the spec changes.

## D2 — Java 25

`javaVersion` is **not a valid JDL config key** in 9.2.0 (`MismatchedTokenException: Found an invalid
token 'javaVersion'`) and there is no `--java-version` CLI flag. It is set in `.yo-rc.json` and the
app regenerated, which is the route taken here.

Build with `JAVA_HOME=/usr/lib/jvm/jdk-25.0.2-oracle-x64`. **Never** `java-25-openjdk-amd64` — that
is a JRE, and its failure mode is an incremental build that *passes* because `target/classes` is
already populated. Verify any JDK claim with `clean verify`, never an incremental build.

## D3 — v1 is a vertical slice: registry + gateway + catalog

All five apps get scaffolded so the JDL is proven end to end, but only **catalog** is brought fully
to life in this pass: seeded, rating derived from reviews, and the browse/facets/profile/availability
endpoints answering. `booking`, `messaging` and `payout` are generated and compile, but are not
wired.

The slice ends on a number, not on a green build:

```
GET /api/professionals/count          -> 18
GET /api/professionals/p1             -> rating == AVG(stars) over its reviews, to 1dp
```

## D4 — The seed is regenerated from the prototype

Rather than relax the JDL to fit the existing fixture, `demo/seed-data.json` is **re-extracted from
the prototype's four `<script>` blocks** with the missing fields filled in.

The one thing regeneration cannot invent honestly: the prototype's `REVIEWS[]` carries no booking
id, so `Review.bookingReference` has to be minted during extraction. Every review gets a synthetic
completed booking reference, and the extractor records the rule it used so the invention is visible
rather than silent.

Conflicts being closed by regeneration (all seven from `../CLAUDE.md`):

| Conflict | Resolution in the regenerated seed |
|---|---|
| Reviews lack `bookingReference` | minted per review, traceable to the review ref |
| `deliveryMode` is prose (`"In person"`) | emitted as `IN_PERSON` / `ONLINE` / `HOME_VISIT` |
| `requests[].status` is `"PENDING"` | emitted as `REQUESTED` |
| `professionals[].verified` is boolean | emitted as `VerificationState`, e.g. `VERIFIED` |
| `bookings[].customerRef` = `"c1"` | resolved to `customerLogin` + `customerName` |
| `Professional.deliveryModes` has no column | added to the catalog JDL |
| `credentials` / `highlights` / `initials` | added to the catalog JDL |

**Invariant preserved:** ratings stay absent from the file. They are derived from `reviews` at read
time, so the seed still cannot ship an inconsistency. The regenerated file must reproduce the
prototype's figures exactly — 18 professionals, 52 services, 63 reviews, 256 sessions, ₵81,620 gross.

## D5 — Consul, matching the siblings

The spec §4 and both deploy scripts assume the **JHipster Registry (Eureka)** on 8761 —
`deploy-dev.sh` waits on a compose service named `registry` at
`http://localhost:8761/management/health`. All three sibling stacks use **Consul**, and the
workspace guide calls Consul *required in development* for the JHipster stacks.

**Decision: Consul.** One discovery mechanism across all four products, so operational knowledge
transfers.

This is the contradiction that costs the most to resolve, because the scripts are byte-identical to
the spec's appendices. **Three things must change together or they fork:**

| File | Change |
|---|---|
| `deploy/deploy-dev.sh` | `infra_up()` starts `consul` not `registry`; health wait moves to `:8500/v1/status/leader` |
| spec Appendix A | same bytes — must be regenerated from the script |
| spec §4 | drop the `healthconnect-registry` / 8761 row, add Consul on 8500 |

## D6 — Sibling layout; the deploy scripts get rewritten

Siblings name their containers by role — `api/`, `gateway/`, `web/`. The spec §10 uses
`healthconnect-<service>/`, and `deploy-dev.sh` bakes that in as a Maven reactor:

```bash
for s in "${SERVICES[@]}"; do mods+="${mods:+,}healthconnect-$s"; done
./mvnw -q -pl "$mods" -am clean verify -Pdev,test
```

**Decision: the siblings win on layout, and both scripts are rewritten to match.**

```
hc-market/
├── gateway/ catalog/ booking/ messaging/ payout/
├── jdl/*.jdl
├── deploy/
│   ├── deploy-dev.sh  deploy-prod.sh
│   ├── demo/seed-data.json
│   └── docker/docker-compose.{dev,prod}.yml
└── docs/
```

No aggregator `pom.xml` and no Maven reactor — each app is built standalone with
`(cd catalog && ./mvnw clean verify)`, exactly as every sibling repo is built. Directory names are
roles; **JHipster `baseName` still follows the spec** (`healthconnectCatalog`, `healthconnectGateway`),
because that is what names the artifact, the app class and the service in Consul.

Consequence: **spec Appendices A and B must be rewritten from the new scripts**, or they describe a
build that no longer exists. Same discipline as D5 — the script and its appendix are one artefact in
two places.

## D7 — The booking state machine follows the diagram, not the enum

§5.2 declares 8 `BookingStatus` values but its transition diagram reaches only 6; `ACCEPTED` and
`NO_SHOW` are both unreachable, while the Kafka topic for accepting is named `booking.accepted`.

**Decision: the diagram wins.** `ACCEPTED` is removed — accepting a request moves it straight to
`CONFIRMED`, as the prototype's `PRO_SCHEDULE` already assumes. `NO_SHOW` is kept and given the
transition it was missing.

```
REQUESTED ──accept───▶ CONFIRMED ──complete──▶ COMPLETED
    │                      │
    ├──decline──▶ DECLINED  ├──no-show──▶ NO_SHOW        ← new
    ├──propose──▶ RESCHEDULE_PROPOSED ──accept──▶ CONFIRMED
    └──cancel───▶ CANCELLED ◀──cancel── CONFIRMED
```

The topic name `healthconnect.booking.accepted` stays — it names the **act**, not the resulting
state, and its payload carries `status = CONFIRMED`.

## D8 — `Review` gains `customerLogin`

§9 requires `POST /api/reviews` to prove "a COMPLETED booking for that customer and professional",
but §5.1's `Review` carries only `authorName` and `authorInitials` — there is no customer identity
to match against.

**Decision: add `customerLogin` to `Review`**, and make `bookingReference` unique.
`authorName`/`authorInitials` remain the public display fields; `customerLogin` is never serialised
to the public DTO. The check becomes local to one row plus the booking lookup:

```
booking.customerLogin == token.login
  && booking.professionalRef == review.professionalRef
  && booking.status == COMPLETED
  && !booking.reviewed
```

`bookingReference unique` is what makes "one review per booking" a schema guarantee rather than a
service-layer hope.

## D9 — Image builds skip tests; `verify` is a separate, explicit step

Found by running `deploy-dev.sh up` for real: the gateway alone took **over ten minutes** before the
first image was built. The JDL asks for `testFrameworks [cucumber]`, and the generated Cucumber
tests stand up Testcontainers — so `clean verify` pays a container-startup tax per app, five times
over, every time anyone brings the estate up.

The sibling products already settled this. `hc-patient/deploy/docker/api.Dockerfile` builds with
`-DskipTests` and says why in a comment: Testcontainers needs a Docker daemon, which an image build
does not have, so `./mvnw verify` belongs "on a developer machine or in CI".

**Decision: `deploy-dev.sh` packages with `-DskipTests` and offers `--with-tests` for the full run.**

```bash
./deploy-dev.sh up                 # clean package -DskipTests, then jib  (fast)
./deploy-dev.sh up --with-tests    # clean verify before each image       (slow, Testcontainers)
(cd catalog && ./mvnw clean verify)   # the normal way to run one app's tests
```

Packaging an image is the wrong place to discover a broken test, and the wrong place to require a
Docker daemon inside a Docker build.

## D10 — Published ports are overridable

`deploy-dev.sh up` could not bind its defaults on this workstation: `abofonsa_api` holds 8080 and a
`dev_consul` holds 8500, alongside the three sibling `*-quality-*` stacks. Six products sharing one
machine will collide by construction.

Every published port in `docker-compose.dev.yml` now reads an environment variable with the spec's
port as its default, and **`deploy-dev.sh` reads the same variables for its health gates** — so the
two cannot disagree. Container-internal ports never move: every service still listens on 8080 inside
its own container, and `SPRING_CLOUD_CONSUL_PORT` is always 8500.

```bash
export HC_GATEWAY_PORT=18200 HC_CATALOG_PORT=18201 HC_BOOKING_PORT=18202 \
       HC_MESSAGING_PORT=18203 HC_PAYOUT_PORT=18204 \
       HC_CONSUL_PORT=18500  HC_KAFKA_PORT=19092
```

## D11 — Two infrastructure defects found by actually running the estate

Neither was visible from a compose-file validation; both needed a real run.

**`apache/kafka-native` ships no shell scripts.** The native image is a GraalVM binary containing
only `kafka.Kafka` — there is no `/opt/kafka/bin` at all. The healthcheck and the topic creation
both need `kafka-topics.sh`, so Kafka never reported healthy, all five apps blocked on
`depends_on: condition: service_healthy`, and the script sat on `waiting for Kafka` until timeout
with nothing in any log explaining why. **Use `apache/kafka:3.9.0`**, the JVM image. Fast startup is
not worth losing the tooling the estate depends on.

**Spring Boot 4 renamed the MongoDB properties.** JHipster 9.2.0 generates `spring.mongodb.uri`, not
`spring.data.mongodb.uri`. Setting `SPRING_DATA_MONGODB_URI` therefore sets a property nobody reads,
`application-dev.yml`'s `localhost:27017` wins, and the gateway dies inside Mongock with
`Connection refused ... localhost:27017` — a message that points at the database rather than at the
variable name. The correct variable is **`SPRING_MONGODB_URI`**, which is also what
`hc-patient/run-local.sh` exports.

**Jib images contain no `curl` and no `wget`.** A compose healthcheck written as
`['CMD','curl','-f',...]` fails with `executable file not found` on every attempt, so the container
never leaves `health: starting`. They *do* contain `bash`, so both compose files now speak HTTP over
bash's `/dev/tcp`, and `deploy-prod.sh`'s readiness gate — which used
`docker compose exec ... curl` — does the same.

**Each service listens on its own port inside its container.** The JDL assigns `serverPort` per
service (catalog 8081, booking 8082, …) so the five can run side by side on a developer machine.
Inside separate containers that only creates opportunities to map the wrong one, and it did: every
service was mapped to container port 8080, so only the gateway answered. Both compose files now set
**`SERVER_PORT: 8080`** for every service, so the internal port is uniform and only the host mapping
varies.

**`/services/**` was authenticated at the gateway, making "public reads" false in practice.** Spec §6
says public reads need no token. The catalog honoured that, but the generated gateway
`SecurityConfiguration` ends with `.pathMatchers("/services/**").authenticated()` and rejected
Discover and Browse with a 401 *before routing* — so public reads only worked if you bypassed the
gateway, which no client does. A new `MarketplacePublicRouteConfiguration` on the gateway permits GET
on the catalog's public paths and nothing else; `POST /api/reviews` still returns 401.

All are fixed with the reasoning recorded inline, so none is re-introduced by someone tidying up.

## D12 — `Ledger` carries the professional's login and the delivery mode

Two amendments, both forced by making the earnings screen actually work.

**`professionalLogin`.** Spec §9 requires `/api/pro/**` to resolve the professional from the token
and refuse anything that is not the caller's, but the specified `Ledger` carried only
`professionalRef` — nothing a JWT subject can be matched against without asking the catalog service
what `p1`'s login is. That would make every earnings read depend on catalog being up, and would
break the rule that each service reads only its own sections of the seed. Exactly the same reasoning
as D8 for `Review.customerLogin`.

The endpoint therefore takes **no professional parameter at all** — not even an admin override. The
login comes from the JWT subject and nowhere else, so "refuse any reference that is not the
caller's" is true by construction rather than by a check someone can forget. Verified: a second
professional's token returns `0`, and a token signed with the wrong key returns 401.

**`deliveryMode`, `serviceRef`, `serviceName`.** Denormalised onto the ledger row, exactly as
`grossMinor` already was. The earnings screen breaks sessions down by format and by service, and a
ledger row must keep saying what it said when it was written even if the booking is later corrected
— the same rule that stops a receipt changing when a price is edited.

**Why the money columns are stored at all**, given the derived-not-stored rule: commission depends on
the `BrokerageConfig` *in force when the booking completed*, so recomputing it later from today's
rate would rewrite history. What must never be stored is a total **across** rows — and none is.
Every figure on the earnings screen is a SQL aggregate: per-month rows, lifetime totals, average
session value, and the two breakdowns.

Commission is rounded **per row**, never on a total. With the seeded prices every result is an exact
integer so the two agree today, but they diverge the moment a price appears that does not divide
cleanly — and at that point a total-first calculation disagrees with the sum of the receipts the
customers were shown. The receipts are the truth.

## D13 — Publishing: `ghcr.io/kojoampia`, built by CI, tagged by commit

Four answers, taken 25 August 2026.

**Channel: `ghcr.io/kojoampia`,** and the repository is **public**, matching `hc-admin-gateway`,
`hc-patient-service` and `hc-professional-service`.

Public was chosen with the exposure stated rather than assumed. The workspace guide records that
these stacks "seed accounts whose passwords derive from their logins by a rule published in a public
repository, several holding privileged roles" — so this extends a known risk rather than creating a
new one. What was checked before pushing:

| | |
|---|---|
| `quality/.jwt-secret` (a real key generated this session) | gitignored, **never committed** |
| PATs, private keys, AWS keys | none in any tracked file or in history |
| Committed `base64-secret` values | present, but only in `application-secret-samples.yml`, `src/test/resources` and `central-server-config` |
| `application-dev.yml` / `application-prod.yml` | carry **no** key at all |
| All three compose files | `JWT_BASE64_SECRET:?` — **required**, the stack refuses to start without injection |

So no deployment can fall back to a published key. That is a better position than the siblings',
where the guide's warning originates, and it should stay that way: **never add a default
`base64-secret` to a profile that actually runs.**

**Image names: `hc-market-<service>`,** not the `healthconnect-<service>` that spec §12 and
`deploy-prod.sh` originally specified. `healthconnect-` is the platform's name and four products
share it; the sibling packages are all `hc-<product>-<service>`, and hc-market's should sort beside
them.

**Built by GitHub Actions on push to `main`,** using the built-in `GITHUB_TOKEN`, which carries
`packages: write` — no PAT to create, store or rotate. (The workstation's `gh` token has scopes
`repo, read:org, gist, admin:*` and could not have pushed packages anyway.) `deploy-prod.sh` then
runs with `--no-build` and deploys what CI built.

This is the decision that makes `--images=published` honest: an image built on a workstation cannot
prove it matches any commit, and proving exactly that is what a quality box is for.

**Tagged by commit SHA, with no semantic version.** Provenance is exact and there is no version to
bump or forget. The cost is real and worth naming: *"which version is on quality?"* now has no answer
a person can hold in their head — only a SHA to look up. Revisit when there is something worth
calling a release.

---

## D14 — A publish is proved against the registry, not against the builder

Taken 25 August 2026, forced by the first `release.yml` run.

That run was **green on all five services.** Jib logged, for every one of them:

```
[INFO] Built and pushed image as ghcr.io/kojoampia/hc-market-catalog, …:1327d5d…, …
```

and exited 0. `ghcr.io` held the SHA tag for **three**. `catalog` and `booking` had `latest` only;
`GET /v2/kojoampia/hc-market-catalog/manifests/1327d5d…` returned 404 immediately, again twenty
minutes later, and still 404s now — so it was a lost write, not propagation lag. Both packages were
public and anonymously listable throughout, so visibility was never the cause. Nothing in the run
was red, and GitHub has no error to show for it. **The root cause is not established.**

The `verify` job as first written could not have seen this: it checked `needs.publish.result`, which
is the matrix's own account of itself. **A gate that asks the thing being gated whether it worked is
not a gate.** It now performs the token-then-manifest handshake a `docker pull` performs, per
service, retrying ten times over ~5 minutes so eventual consistency is distinguished from absence.

**Why this outranks the inconvenience of a re-run.** `latest` was present for all five the entire
time. A quality stack that fell back to it would have pulled, started, gone healthy and served
correct-looking data — while running `catalog` and `booking` from a commit nobody chose. Every check
short of comparing digests would have passed. That is the exact failure a quality box exists to make
impossible, which is the retroactive justification for `quality/startup.sh` requiring `TAG` rather
than defaulting to `latest`: the guard that looked pedantic is the one that would have caught this.

**The remedy is to re-run the workflow, not to re-tag by hand.** The build is deterministic and
re-pushing is cheap; a hand-pushed tag reintroduces exactly the "an image nobody can trace to a
commit" problem D13 exists to remove. Re-running at `beba627` published all five, verified
independently from this workstation.

If it recurs on a *different* pair of services, that points at a registry-side race between
concurrent pushes sharing base layers, and serialising the matrix (`max-parallel: 1`) is the answer.
One occurrence is not enough to conclude that, so the matrix stays parallel and the gate stays
strict.

---

## D15–D25 — Spec §13 answered

Answered 25 August 2026. **These are recommendations, not yet ratified decisions.** D1–D14 record
answers taken by the architect; this section records a proposed answer to each of §13's eleven
remaining questions, with the reasoning and the cost, so that ratifying one is a yes/no rather than
a fresh investigation. Three of them — D15, D24 and anything with a contract attached — cannot be
settled by engineering judgement alone and say so.

Nothing here is implemented. Each entry ends with what it would cost to build.

### Three of §13's premises do not match the code

Checked before answering, because a question built on a wrong premise gets a wrong answer:

| §13 says | The code says |
|---|---|
| "Ghana's Data Protection Act applies to **the care summary**" | hc-market stores **no care summary**. `Booking.careSummaryShared` is a lone `Boolean`; no entity, column or endpoint anywhere in the five apps holds conditions, allergies or medications. Spec §9's description of that endpoint is **unbuilt**. |
| "**PostgreSQL full-text** is enough for 18 professionals" | There is no full-text search and no SQL filtering. `MarketplaceService.BrowseFilter.matches` is `haystack.toLowerCase().contains(needle)` in **Java**, over the whole card list, with facets tallied the same way. |
| Observability — "JHipster ships Micrometer, which backend?" | hc-market wires **nothing**: no OpenTelemetry agent, no OTLP endpoint, no `monitoring` network in any of the three compose files. The siblings all do. |

---

### D15 — Payments: provider-managed split, not escrow *(needs a commercial and legal decision)*

**Recommended: a single charge at booking, split by the provider**, with the professional's share
settling to their own subaccount and the platform only ever receiving its 12%. Paystack Subaccounts
or Flutterwave Split Payments; Hubtel is the Ghana-native alternative and strongest on direct bank
settlement. Confirm current capabilities against the provider's own docs — this area moves.

**Why not escrow, and why not authorise-and-capture.** Escrow is the intuitive reading of "holds
money and releases it after the session", and it is the expensive one: holding customer funds as a
non-bank engages Bank of Ghana licensing under the Payment Systems and Services Act, 2019 (Act 987).
That is a licence application, not a sprint.

Authorise-and-capture is the standard way to dodge that — except that it does not exist on mobile
money, which is how most of this market pays. MoMo collections are a direct debit with customer
approval; there is no two-phase hold to capture later. Card authorisations also expire in about a
week, which a booking three weeks out will outlive. So the two-phase model fails on the dominant
rail and degrades on the other.

A provider-managed split gets the commercial effect — customer committed, professional assured — with
the platform never holding the professional's money, so the licensing question largely goes away.

**It maps onto what already exists.** `Ledger` already stores `grossMinor`, `commissionMinor` and
`netMinor` per row, and `BrokerageConfig` is already effective-dated, so the historical commission
rate is already preserved. `Payout` stops being something the platform pays and becomes a
**reconciliation** record against the provider's settlement report; `Payout.bankReference` becomes
the settlement ID.

**Cost.** A `Charge` entity is genuinely missing — today **nothing in the estate collects money from
a customer at all**. It needs provider reference, state, and idempotency on `bookingReference`.
Refunds need modelling too; `lateCancellation` already implies a 50% retention. Webhook handling
must be idempotent, which the existing outbox/`eventId` discipline already has a pattern for.

**What is not an engineering call:** which provider (pricing, settlement times, contract), and
whether the split model clears Act 987 for this business. Both need the company's counsel.

### D16 — KYC: a manual admin queue, in `hc-admin`, and no register to automate against

**Recommended: evidence review by a human in v1.** The one automatable piece is identity — the
National Identification Authority's Ghana Card verification, already used by banks. Police clearance
comes from the Ghana Police CID as a certificate, and certificates of competence come from training
bodies.

**There is no register to check most of these people against, and that is inherent to the product.**
The scope note restricts the marketplace to *non-medical* professionals; trainers, nutritionists and
home carers are not licensed by a statutory body the way a doctor is by the Medical and Dental
Council. So "verify against a register" has no target for most listings. Verification here means
*documents seen by a person*, and the badge should not imply more than that.

**Cost, and a gap worth naming.** `Professional.verification` is a bare enum column with **no record
of who set it, when, or on what evidence** — for a trust signal shown publicly beside someone's name,
that is thin. It needs a reviewer, a timestamp, an evidence reference and an append-only history.
`Credential` rows exist but hold only a label; document storage is unbuilt. The queue itself belongs
in the back-office console, not here.

### D17 — Online sessions: relay a link, never host the room

**Recommended v1: the professional supplies their own meeting link** (Meet, Zoom, whatever they
already use); the platform stores it and reveals it an hour before, which is the promise the
prototype makes. Daily.co is the upgrade if a no-account, in-browser room with a waiting room is
wanted later; self-hosted Jitsi trades a licence cost for an operational one.

**Why not host.** Hosting a room where health matters are discussed drags in recording, retention and
consent — three problems the platform does not otherwise have. **Recommend no recording in v1**, which
keeps it that way.

**Cost.** `Booking` has nowhere to put a link. And "released one hour before" is a *scheduled reveal*
— there is no scheduler anywhere in this estate, so the honest cheap version is to compute
visibility at read time from `scheduledDate`/`scheduledTime` rather than to push anything.

### D18 — Notifications: in-app and email in v1; WhatsApp is the one worth the integration

**Recommended: in-app plus email now; SMS for confirmations and reminders only; no push.** Push is
moot — hc-market has no mobile app and no web front end at all (`api/` and `web/` are empty by
design).

**WhatsApp is the honest answer for this market** and deserves to be weighed on engagement rather
than on transport cost, via a Business API provider. It is a real integration, so it is a v1.5 call,
not a default.

**Cost.** `Notification` today is `recipientLogin, kind, body, raisedAt, readAt, deepLink` — a row in
a table. Sending anything *outside* the app needs a channel, a delivery state, a provider reference
and a dedupe key, plus an outbox so a provider outage does not lose the notification. Booking's
transactional outbox is the pattern to copy rather than reinvent.

### D19 — Search: move it into SQL at around 200 professionals; Elasticsearch, realistically never

Correcting the premise above: today every browse request builds **every** professional's card —
including the rating-view join — and filters them in Java with `contains()`. The matching is not the
problem; the **loading** is.

**Recommended: push filtering, sorting and faceting into SQL, with a `tsvector` GIN index for `q`
and `pg_trgm` for typo tolerance.** Trigger it on a p95 latency measurement or ~200 professionals,
whichever comes first — not on feel.

**Elasticsearch earns its operational cost in the tens of thousands of documents**, or when relevance
tuning and synonyms become a product feature in their own right. For a Ghanaian marketplace of
non-medical professionals, that is not a realistic horizon. Postgres covers it.

**MEASURED 2026-08-31, so the trigger is a number rather than a feeling.** Against the quality estate
at 18 professionals, through the gateway, 60 samples each:

| Endpoint | p50 | p95 | max |
|---|---|---|---|
| `GET /api/professionals?size=200` (browse) | 21 ms | **26 ms** | 41 ms |
| `GET /api/professionals?q=nutrition&minRating=4` | 20 ms | 23 ms | 26 ms |
| `GET /api/professionals/facets` | 20 ms | 26 ms | 45 ms |
| `GET /api/professionals/count` (control — one query) | 4 ms | 5 ms | 5 ms |

So the load-everything-and-filter-in-Java approach costs about **21 ms over a single-query control**,
and that gap is what scales with the catalogue: browse builds every card, rating-view join included,
before filtering any of them. At ~200 professionals the same shape lands somewhere around 200–250 ms,
which is where this stops being free.

**Conclusion: not now.** 26 ms is not a latency problem and 18 is not 200. Re-take the measurement
before deciding otherwise — the command is above and takes a minute — rather than reasoning from the
fact that `contains()` in a loop looks wrong. It is wrong in principle and irrelevant in practice at
this size, and D19 exists to keep those two apart.

**One constraint to respect:** rating comes from the `professional_rating` view and is `null` for the
unrated. Sorting and `minRating` in SQL must join that view and keep excluding unrated professionals
rather than coalescing them to zero — the whole point of D-the-rating-rule. A `LEFT JOIN` plus a
careless `COALESCE(rating, 0)` would silently reintroduce exactly the collapse the design forbids.

### D20 — Availability: recurrence rules **and** generated slots, not one or the other

**Recommended: professionals author rules; the system materialises slots forward.** Add
`AvailabilityRule` (weekday, start, end, interval, validFrom, validUntil) and
`AvailabilityException` (a date that is closed, or replaced). Keep `AvailabilitySlot` as the
bookable unit and generate it forward a fixed horizon.

**Why not compute availability on the fly.** `AvailabilitySlot.taken` needs a *row* to lock. Two
customers booking the same 07:00 must collide on a unique constraint over
`(professional, date, time)`; with slots computed at read time there is nothing to contend on and the
double-booking is silent. The generated row is what makes the race safe — the same reasoning as the
unique `(customer_login, professional_ref)` on `Favourite`.

**Cost, and a defect found while answering.** `slotTime` is `String maxlength(5)`. It sorts correctly
only by the accident of zero-padded 24-hour text, and it accepts `"7:00"` and `"25:99"` today.
`scheduledTime` on `Booking` has the same shape. Both should be `LocalTime`. That is a JDL change, so
it rewrites a Liquibase changelog and needs a real migration rather than a regeneration.

**As built (D26 step 4).** `AvailabilityRule` + `AvailabilityOverride` → `AvailabilityPlanner`, with
rule management on `/api/pro/**` because the generated `AvailabilityRuleResource` and
`AvailabilityOverrideResource` were **deleted**: their unscoped CRUD on `/api/availability-rules`
would have let any authenticated user edit anyone's working hours. That is the same disclosure the
hand-written `FavouritesResource` exists to prevent, and adding two more of them would have been a
regression dressed as a feature.

Three decisions worth having written down:

- A window is **exclusive of its end**. 07:00–11:00 at 60 minutes is four sessions, the last starting
  at 10:00 — not five, with one running past the hours the professional set.
- An override that opens different hours **borrows the slot length** from a rule on that weekday, so
  "same sessions, different time" does not silently change how long a session is.
- Generation is **explicit**, not scheduled. There is no scheduler in this estate and inventing one
  here would be a second thing to operate; a professional generating their own calendar also gets to
  see what changed, which is what the response reports.

**The guarantee that matters, and it is tested:** generation *never* removes a slot that is taken,
including when a day is closed. A booked appointment is a commitment to a customer, and deleting it
by editing working hours would cancel someone's session without telling them.
`ProWorkspaceResource.setAvailability` already refuses for that reason, and generation must not
become a way around that refusal — "close this day" is precisely the operation that would try.

**Known limitation, stated rather than hidden.** Generation is additive on ordinary rule days, so a
hand-edited day is authoritative only until the next run. Making a day genuinely different means
recording an override. Fixing that properly needs an origin marker on the slot so generated and
hand-added rows can be told apart; without one, any deletion rule is a guess about which of two
sources of truth wins, and guessing wrong deletes a professional's working day.

### D21 — Time zones: keep Africa/Accra, but store it instead of assuming it

**Recommended: the booking's wall clock stays as written, and gains an explicit `zoneId` defaulting
to `Africa/Accra`. The professional's local time is authoritative.**

Ghana is UTC+0 all year with no daylight saving, so the current implicit approach is genuinely safe
*today* — this is the rare case where the naive model is correct. What it is not is *legible*: nothing
in the schema says which zone the wall clock belongs to.

**Do not convert appointments to UTC instants.** That is the classic mistake in this exact domain. An
`Instant` is right for "when did this happen" — `raisedAt`, `completedAt`, and the ledger already use
it correctly. It is wrong for "when is the appointment", because if a zone's rules ever change, the
7 a.m. session must stay at 7 a.m. rather than silently becoming 6 a.m.

**The professional's zone wins** because that is where the service is physically delivered — in-person
and home visits are unambiguously there. The realistic driver for any of this is a diaspora customer
booking a home visit for a relative in Accra: a display-side problem, which an explicit `zoneId`
solves without touching the booking model.

### D22 — Multi-currency: keep the column, build nothing, but **enforce** it

**Recommended: keep `currency`, add no conversion, and start validating it.**

Keeping it costs one `varchar(3)` per money row and makes every stored amount self-describing;
dropping it would only be honest if GHS were certain forever, and diaspora payment is the obvious
second currency. But a currency column that can silently disagree across a join is worse than none,
and **nothing today checks that a `Booking`'s currency matches its `ServiceOffering`, or that a
`Ledger` row matches its `Booking`.** That check is cheap and belongs in `BookingWorkflow` and the
ledger consumer.

**Explicitly not recommended:** FX rates, per-currency price lists, conversion at read time. Real work,
real rounding hazards, no requirement.

**As built (D26 step 3), and it found something worse than a currency mismatch.** Enforcing the
currency meant finding where a booking's currency comes from, and the answer was: the request body.
So did `priceMinor` and `serviceName`.

```java
.priceMinor(request.priceMinor())
.currency(request.currency() == null ? "GHS" : request.currency())
```

The client is the customer's browser. `Ledger` derives gross, commission and net from the completed
booking. So **the price of a booking was whatever the caller said it was**, and a crafted request
could have credited a professional ₵0 for a ₵280 session, or ₵280,000 — with nothing anywhere
disagreeing, because every downstream figure is faithfully derived from the number that was stored.
The values are denormalised deliberately (a receipt must not change when a price is later edited),
but *denormalised* was being used as if it meant *unverified*.

Booking now asks catalog through a fail-closed `CatalogClient`, and a request whose figures disagree
is rejected with **409** rather than quietly booked at the catalogue's price — the realistic cause is
a price change mid-wizard, and charging the customer a number they were never shown is not a fix.
Catalog unreachable is **503**, not 500: nothing is broken, the price simply cannot be established.

This deliberately inverts D12's reasoning rather than contradicting it. D12 keeps `professionalLogin`
on the booking so the professional's *inbox* never has to ask catalog — a read path, hit constantly,
that must survive catalog being down. Creating a booking is a single write that must be correct.
**Availability beats correctness on the read; correctness beats availability on the write.**

On the payout side, `Ledger.currency` was `p.path("currency").asText("GHS")` — an event with no
currency minted a GHS row regardless, and nothing compared the row against the `BrokerageConfig`
whose `commissionRate` had just been applied to it. Both refuse now, and the consumer's existing
rethrow means the event is never marked processed, so it retries instead of acknowledging a row that
was never written.

**Two things this pass did not fix**, recorded rather than quietly left:

- `professionalLogin` is still taken from the request and **cannot** be checked against the public
  profile endpoint, which correctly does not expose logins. A caller who lies about it puts the
  booking in someone else's inbox. Fixing it needs an internal catalog endpoint — a different
  mechanism from this one, so a separate piece of work.
- `deliveryMode` still defaults to `ONLINE` when absent from an event, which mislabels a row on the
  earnings-by-format breakdown. Same class of silent default as the currency one was.

### D23 — Disputes: a separate aggregate, a `ROLE_BROKERAGE` desk, and reversing entries

**Recommended: do not add states to `BookingStatus`.** It is a clean seven-state machine with a
sealed-interface transition set, and a dispute is not a booking state — a booking can be disputed and
still be completed.

Add a `Dispute` aggregate keyed by a unique `bookingReference`: `raisedBy`, `reason`, `status`
(`OPEN → UNDER_REVIEW → RESOLVED | REJECTED`), `resolution`, `resolvedBy`, `dueBy`, and the same
append-only history `BookingStatusChange` already models. The desk is a role — `ROLE_BROKERAGE` — and
its screens belong in `hc-admin`, not in a customer-facing app.

**The hard part is the payout interaction.** A dispute upheld after `COMPLETED` must undo a `Ledger`
row that has already credited a professional. **Recommend compensating entries, never deletion or
mutation** — a reversal row with negative amounts. That keeps the ledger append-only, keeps every
earnings aggregate correct without recomputation, and matches the review policy's existing
one-directional discipline (there is no endpoint to delete a review; the only response is a reply).

**Note the promise has a clock.** "Resolved in five working days" needs escalation and a scheduler,
and there is no scheduler in this estate. Until there is, the figure is marketing rather than a
guarantee.

**As built (D26 step 5).** `DisputeWorkflow` (never `DisputeService` — the JDL generates that name),
`DisputeResources.Customer` and `DisputeResources.Desk`, and a `DisputeEventConsumer` in payout's
`service` package. `dueBy` is recorded and sorts the desk queue; nothing enforces it, and the code
says so.

Two things fell out of building it that the recommendation had not anticipated:

**The session count would have gone up when money went down.** Every earnings figure is a plain
aggregate over `Ledger`, which is exactly what makes a compensating entry work — the sums simply
include a negative row. But `count(l)` counted the reversal as a *session*. A professional would have
been reported as having done one more session on the day one of theirs was reversed, with gross
falling at the same time: two figures moving in opposite directions, neither of which anyone would
think to distrust. All five aggregate queries now count with
`sum(case when l.reversalOf is null then 1 else 0 end)`.

**A reversal must be priced at the original's commission rate, not today's.** `BrokerageConfig` is
effective-dated so a historical row keeps the terms it was written under; recomputing the commission
from the config in force at resolution time would refund a commission that was never charged. The
reversal takes its proportion from the row it reverses, and mirrors that row's professional,
currency, delivery mode and service — a reversal that mirrors its original cannot disagree with it,
and sending those fields in the event would have created a second source for them.

Smaller calls, each with a reason: a refund larger than the earning is **capped**, not rejected,
because reversing more than was credited would leave a professional owing money on a session they
were legitimately paid for. A dispute upheld on a booking that never earned anything is a **no-op**,
not a failure — throwing would retry forever against a row that will never exist. `RESOLVED` and
`REJECTED` are **terminal**, because reopening means either double-reversing or un-reversing. And
the reversal is dated **today**, not backdated to the original, so it cannot silently rewrite a month
that has already been reported.

Also fixed here, a drift introduced by the `LocalTime` change: the outbox payload was serialising
`scheduledTime` as a raw `LocalTime`, which Jackson renders `"07:00:00"` as soon as a seconds value
exists. Messaging writes that straight into a notification body, so the wire format is pinned with
`SlotTime.format` like everywhere else.

### D24 — Data protection: pseudonymise on erasure, never delete the ledger *(needs legal sign-off)*

**The care summary is the wrong thing to worry about here, because it does not exist.** What
hc-market actually holds that is personal: customer logins and names, booking history, **`visitAddress`
— a home address**, `customerNote`, `Message.body` free text, reviews and the ledger. Of those,
**`visitAddress` and `Message.body` are the sensitive ones**: people will type health details into a
message thread whatever the schema intends.

Ghana's Data Protection Act, 2012 (Act 843) and the Data Protection Commission set the frame —
controller registration, purpose limitation, and a data subject's right to erasure.

**Recommended erasure model: pseudonymisation, not deletion.** Redact `customerName`, `visitAddress`,
`customerNote` and message bodies; keep `Ledger` rows, amounts and `professionalRef` intact; keep the
`Booking` with a tombstoned customer identity. Financial records have their own retention obligation
that erasure does not override, and the unique `bookingReference` chain stays whole, so payouts,
reviews and earnings do not break.

**This is where "derived, never stored" pays off unexpectedly.** Because there is no
`professional.total_earnings` and no stored rating, redacting a customer requires **no recomputation
anywhere** — every aggregate is a view or a query over rows that are still there. Had those totals
been denormalised, every erasure would have been a consistency problem.

**Residency needs checking, not assuming:** production is a VPS at 199.247.5.252, and Act 843
constrains transfers abroad in some circumstances.

**I am describing engineering consequences, not giving legal advice.** Retention periods, the lawful
basis for each field, controller registration and the residency position all need the company's
counsel before anything here is relied on.

### D25 — Observability: attach the OTel agent and push to the host collector. No new backend.

**Recommended: do exactly what the siblings do.** The production host already runs one observability
stack — Grafana/Mimir/Loki/Tempo behind an `otel-collector` on the external `monitoring` network. Every
other product borrows it rather than shipping its own, and hc-market should too.

**Not Prometheus scraping.** The workspace's model is push (OTLP); the host's Alloy config carries no
application scrape targets and that is deliberate. Adding a scrape path here would be a second
pattern for no gain.

**Cost.** Bake the OpenTelemetry Java agent into the Jib image config, set
`OTEL_EXPORTER_OTLP_ENDPOINT` in the production compose, join the `monitoring` network, and mount
alert rules **per application** — never appended to a shared fleet file, so a YAML mistake costs one
app's alerting rather than everyone's.

**One sibling pattern does not carry over:** hc-market is API-only, so there is no browser posting to
a same-origin `/v1/traces` and no nginx proxy hop for it.

**As built (D26 step 2).** Two things differ from the sibling shape, both forced by hc-market having
no Dockerfiles:

- The agent comes from **Maven Central** (`io.opentelemetry.javaagent:opentelemetry-javaagent`,
  fetched by `maven-dependency-plugin` into `target/otel/`) rather than being `curl`ed in a
  Dockerfile or committed. This repository is public and the jar is ~24MB.
- The Jib `extraDirectories` override needs **`combine.self="override"`**, or Maven keeps
  `pluginManagement`'s simple `<paths>` form and the agent is silently absent from an image that
  otherwise builds and runs perfectly. Found by looking inside the image, which is now the only
  honest way to check.

`JAVA_OPTS` is assembled from **two** variables — `HC_JAVA_OPTS` and `HC_OTEL_JAVA_OPTS` — so an
operator raising the heap cannot detach the agent by accident. hc-patient folds both into one
string; here that would be a footgun, because the failure is silent and looks like the service
simply having nothing to say.

Instrumentation was verified rather than assumed: Tomcat `SERVER` spans carrying `http.route` and
JDBC spans carrying `db.operation`, on 2.30.0 under Java 25. A green up-tile proves only that the
agent read some MBeans.

Alert rules live in `deploy/observability/hc-market-rules.yaml`, five alerts in three groups, and
that file names what deliberately has **no** rule and why — Kafka consumer lag (no series exists),
seed completeness (a deploy-time check, not runtime), browser signals (no front end), and payout
settlement failures (no provider is wired, per D15). An absent rule that looks like an oversight
gets "fixed" later into an alert that cannot fire.

**Installing it is not this repository's job**, exactly as with nginx: the file is staged here and
the architect copies it to the monitoring stack and restarts Mimir. The instructions are in the file.

### And the SSE claim, which is not in §13

The spec header advertises **"Kafka, SSE"**. No SSE endpoint is defined in §6 or §7, none is built,
and the prototype's "live" messaging is a simulated local reply.

**Recommended: drop the claim, and poll in v1.** If real-time is genuinely wanted, the shape that fits
this architecture is **SSE at the gateway, fed by Kafka** — the gateway is reactive and holding
thousands of open streams is what it is good at. Streaming from `messaging` directly would pin a
servlet thread per subscriber, because that service is imperative Spring MVC.

---

## D26 — What of D15–D25 gets built, and in what order

Four answers, taken 25 August 2026. This ratifies part of the section above and defers the rest.

**Build now: D22 (currency enforcement), D25 (observability), D23 (disputes), D20 (availability
rules), and the two defects.** Everything else stays a recommendation.

**`slotTime` and `scheduledTime` become `LocalTime` now.** They are `String maxlength(5)` today and
accept `"7:00"` and `"25:99"`. Changing them rewrites a Liquibase changelog, so it is only cheap
while no environment holds data worth keeping — **which is true today and will not be true again.**
Dev and quality schemas get dropped and rebuilt; the generated changelog is a fresh install, not a
migration, and there is no production to migrate.

**Payments (D15) waits for the provider decision.** Nothing gets built, deliberately. The split
model makes `Payout` a *reconciliation* against a provider's settlement report; escrow makes it a
payment the platform executes. That is a structural difference, not a detail behind an interface, so
a `Charge` written now would be a guess at which — and unpicking the wrong one costs more than the
delay. The collection gap stays open and stays visible.

**Disputes stop at the hc-market API.** The `Dispute` aggregate, its state machine, the
`ROLE_BROKERAGE` endpoints and the compensating ledger entry all live here. The desk UI in
`hc-admin` is deliberately *not* in scope: it is a separate git repository — a second commit at
minimum — and hc-admin's Bootstrap/`abf-` conventions translate from nothing in this product, so
guidance written for one inverts for the other. The API can be consumed by a console designed on its
own schedule.

### Order, and why it is not the order the questions were asked in

**Corrected before any of it was built.** The first version of this list put observability first, on
the reasoning that it was "configuration only, no generator run, independent of everything else."
That was wrong. hc-market has **no Dockerfiles** — it builds images with Jib, configured in
`pom.xml`, and `pom.xml` is generated. It carries no `jhipster-needle-*` markers, so
`jhipster jdl --force` rewrites it wholesale rather than merging into it. Wiring the agent first
would have put the whole of D25 in front of a regeneration that discards it.

This is a structural difference from the siblings and not a mistake they could have warned about:
`hc-patient` bakes its agent in through `docker/gateway.Dockerfile`, a hand-written file in a
*deploy* repository that no generator touches. hc-market has no such file to edit.

So: regenerate first, then wire, and **add the Jib/OTel pom block to the regeneration-hazard
checklist**, because the next regeneration will discard it just as readily.

1. **The JDL changes, one regeneration per app** — `LocalTime` in catalog and booking,
   `AvailabilityRule`/`AvailabilityException` in catalog, `Dispute` in booking. Batched
   deliberately: `jhipster jdl --force` regenerates *every* entity in an app, so each extra run is
   another pass through the regeneration-hazard checklist and another chance to drop the
   `professional_rating` view include or leave an ambiguous mapping behind. Two runs, not four.
2. **D25 observability** — now that nothing will overwrite it. The agent comes from Maven Central
   via `maven-dependency-plugin` rather than a jar committed to a public repository.
3. **Currency enforcement** — service-layer, no generator.
4. **Availability generation** — the rule-to-slot materialiser.
5. **Dispute workflow and compensating entries** — the largest, and last because it depends on the
   `Dispute` entity landing in step 1.

---

## D27 — One broker, one Consul, borrowed from `hc-infra`. This repository declares neither.

**Decided 2026-08-31.** No deployment in hc-market runs its own Kafka or its own Consul. Both live
once, outside this repository, and every stack points at them by container name over a network it
declares `external`.

| Stack | Broker | Consul | Network |
|---|---|---|---|
| `quality/compose.yml` | `hc-shared-quality-kafka:9092` | `hc-shared-quality-consul:8500` | `hcnet`, external, created by `hc-infra/startup.sh` |
| `deploy/docker/docker-compose.dev.yml` | the same | the same | the same |
| `deploy/docker/docker-compose.prod.yml` | the host's `kafka:9092` | the host's `consul:8500` | `infranet`, external, host-wide |

Production already worked this way — it borrowed the host's infrastructure from the first deploy,
the way every sibling product does. The change is that **dev and quality now do too**, and that
"borrow, never bundle" is now the rule rather than an accident of what production happened to have.

### Why: four brokers is the same as no broker

Until 2026-08-31 `hc-admin`, `hc-professional` and hc-market's quality stack each ran a private
single-node broker and `hc-patient` ran none, so every cross-product event path in the estate was
configured, deployed and **never once exercised**. Nothing failed. Each stack came up green, every
producer's send succeeded, and the topic simply existed more than once. A broken bus gets fixed; a
bus that is four buses does not, because nothing reports it.

hc-market's *dev* estate was the last holdout, and leaving it there would have preserved the same
defect in miniature — a five-service estate whose event paths are only ever exercised against a
broker nothing else can see.

Consul is the same story from the other end. All four stacks had it switched off, so the estate had
no service inventory at all and Consul's catalogue was empty for four days.

### What this does *not* change: Consul registers, it does not route

The generated gateway ships `spring.cloud.gateway.server.webflux.discovery.locator.enabled: true`.
That was harmless while the dev estate had a private Consul holding only its own five services. On a
**shared** Consul the catalogue also holds hc-admin, hc-patient, hc-professional and hc-market's
quality estate — so the locator would mint routes into other people's running services, and the
symptom would be a gateway answering correctly most of the time.

So the locator is **`false` in every environment**, with four static routes beneath it, exactly as
production has always had. Turning it on would not make quality more production-like; it would make
it less. The four static paths in the dev file are byte-for-byte what the locator used to produce,
so no client URL changed.

Registration is safe because of two settings, both per service and both non-negotiable:
`DISCOVERY_PREFER_IP_ADDRESS: "false"` and `DISCOVERY_HOSTNAME` set to the **container** name. These
containers are on two networks and so have two addresses; Spring picks one and Consul health-checks
whatever it was handed. Pick the wrong one and the service registers, then flaps critical — and the
registration reads as the broken thing rather than the address.

KV-backed config stays off. The prefix is empty, and an application reading config from a store with
nothing in it starts identically to one that does not — right up until somebody adds a key.

### The consequence that cost the most thought: DNS on a shared network

Compose publishes a service's **name** as a DNS alias on **every network it joins**. The dev file's
services were called `gateway`, `catalog`, `booking`, `messaging`, `payout` — and `catalog` and
`booking` are already aliases on `hcnet`, claimed by the quality stack. Verified by resolving them
from an unrelated container: `getent hosts catalog` from `hc-admin-quality-service` answers with
hc-market **quality**'s catalog.

A second `catalog` on that network does not error. Docker answers with whichever it likes, so the
quality gateway would have started routing a share of its traffic into the dev catalog, and the dev
catalog would have verified bookings against the quality booking service. Nothing logs anything.

Three consequences, all in `docker-compose.dev.yml`:

- every service is named **`dev-<service>`**, unique across the host;
- every service carries an explicit `container_name: hc-market-dev-<service>`;
- every intra-stack address is a **container name** (`http://hc-market-dev-booking:8080`), never a
  short service name.

`deploy-dev.sh` keeps the un-prefixed names on its CLI and maps them, so `--services catalog` still
works.

The **databases deliberately stay off `hcnet`**. Another product has no business reaching this
stack's Postgres, and keeping them on the project's own network is also what lets them keep the
short, obvious names (`catalog-db`, `gateway-db`) without ambiguity.

### The consequence that is not solved: dev and quality share a topic set

Topic names are compiled in — `@KafkaListener(topics = "healthconnect.booking.completed")` — so
there is no per-estate prefix and the two estates cannot be separated on the broker.

Consumer groups **are** distinct (`hc-market-dev-*` against `hc-market-*`), which stops the two
estates stealing each other's partitions. Distinct groups mean the opposite thing for delivery:
**both** estates receive every event either one publishes. Completing a booking in dev writes a
ledger row in quality.

`deploy-dev.sh` warns when it finds the quality stack running, and does not refuse — it is a
developer's machine and there are legitimate reasons to have both up. Making it *safe* rather than
merely *visible* needs a configurable topic prefix in booking, payout, messaging and catalog. That
is application work; it is not done, and it is not in D26's scope.

### What was checked

- `docker network inspect hcnet` and `getent hosts` from three containers, before and after, to
  establish that the alias collision is real and that the `dev-` prefix removes it.
- `deploy-dev.sh up --no-build --services catalog` against the live shared plane: the catalog
  registered as `hc-market-dev-catalog` with **two passing** Consul checks, seeded 18/63 with the
  derived rating matching, logged **zero** `MessageDeliveryException` (it logs those on a timer when
  no broker is reachable), and — the point of the exercise — `catalog` still resolved to the quality
  container from the quality gateway, which continued to serve `/api/professionals/count` = 18
  throughout.
- Both stacks' preflights fail with the `hc-infra` command printed when the network or either
  container is absent.

### The five containers that could not be removed

The pre-migration dev containers had been crash-looping for twelve hours against a `consul` that no
longer existed. They are wedged in the daemon: `docker stop`, `kill`, `rm -f` and
`update --restart=no` all return *"tried to kill container, but did not receive an exit event"*.
Clearing them needs a Docker daemon restart, which would bounce the quality stack, the monitoring
stack, `hc-infra` and every sibling product on the box — so they were left in place. They are inert
and hold no ports. `deploy-dev.sh down --remove-orphans` will sweep them once the daemon is next
restarted.

---

## D28 — `professionalLogin` is verified against the catalogue, over a path the gateway cannot reach

**Decided 2026-08-31**, closing the first of the two holes D22 recorded and did not fix.

### The hole

`POST /api/bookings` took `professionalLogin` from the request body and stored it unverified. D12
put it there deliberately — so the professional's *inbox*, a constantly-hit read path, never has to
ask catalog who a `professionalRef` belongs to — and D22 then closed the price hole beside it while
explicitly leaving this one open, because the public profile endpoint correctly does not expose
logins and there was therefore nothing to check against.

The consequence is not a mispriced booking, it is a **misdelivered** one: a caller who sends a
truthful `professionalRef` with somebody else's login puts a real, valid booking into a professional
it does not belong to. Every downstream figure stays consistent, because everything derives
faithfully from the login that was stored. The victim sees a request for a service they do not
offer; the intended professional sees nothing at all.

### The answer: disclose to the cluster, never to the edge

Catalog gains **`GET /internal/professionals/{ref}/login`**, and it is the *only* thing under
`/internal/**`. Booking asks it on every create and uses the answer as the authority — a request
that omits `professionalLogin` gets the catalogue's, and one that disagrees is **409**, the same
treatment and for the same reason as the price: the realistic cause is a stale profile in the
wizard, and silently correcting a caller's data teaches it nothing.

Fails closed like the price call: catalog unreachable is **503**, never a guessed login. That
inverts D12's availability-over-correctness reasoning for exactly the reason D22 gave — availability
beats correctness on the read, correctness beats availability on the write.

### What actually protects it

**The path, plus the gateway's route predicates, and nothing else.** There is no service-to-service
authentication in this estate; every service only validates tokens, and booking holds none of its
own. So the four gateway routes narrow:

```
-  Path=/services/healthconnectcatalog/**
+  Path=/services/healthconnectcatalog/api/**
```

`/internal/**` then matches no route at all, and the gateway is the only ingress in every
environment — quality binds every published port to `127.0.0.1` behind one nginx vhost, and
production publishes the gateway alone. Narrowing costs nothing: every consumer in the repository
already goes through `/api/**`, checked before the change.

Catalog also gets an explicit filter chain for `/internal/**` in a **new** file
(`InternalApiSecurityConfiguration`, the same regeneration-proofing as
`MarketplacePublicSecurityConfiguration`) rather than relying on what the generated chain does with
a path none of its matchers mention. `GET` is permitted, everything else denied — and the comment
says plainly that the chain is not the protection, the routing is.

**The threat model, stated rather than implied.** Anything already inside the estate's docker
network can read any professional's login. That is the same trust level as being able to reach the
databases, which are on those networks too. What this closes is the *external* caller, who is the
one who could previously do it through a documented public endpoint with a valid customer token.

### Rejected

- **A service-to-service token.** Correct in principle and a whole mechanism this estate does not
  have: something must mint it, hold it, and rotate it alongside the one shared signing key. Worth
  building when a second internal call needs it; not worth inventing for one field.
- **Kafka-fed read model.** Catalog publishes `ref → login`, booking projects it locally. No
  synchronous dependency, and an entire consistency surface — staleness, replay, backfill — for one
  string on a write path that is already synchronous for the price.
- **A verify-don't-disclose endpoint** (`POST .../verify-login` returning a boolean). Strictly less
  disclosure, but it leaves the request body authoritative for a field the caller controls, so
  booking could still only reject and never *establish* the truth. Disclosure lets the field be
  dropped from the request entirely, which is where this should end up.

### Found while doing it: production does not route statically

Every document in this repository says production routes statically, and D27 leans on it to justify
`discovery.locator.enabled: false` in dev and quality. **`docker-compose.prod.yml` sets no routes at
all and does not disable the locator**, so production routes *dynamically* through a Consul on
`infranet` that it shares with `hc-admin`, `hc-patient` and `hc-professional`.

Two consequences, and the first is why this is fixed here rather than filed:

1. **The control above would not exist in production.** A locator-derived route is
   `/services/{serviceId}/**` — the unnarrowed form — so `/internal/**` would be reachable from the
   internet through the very gateway this decision relies on.
2. hc-market's production gateway would publish routes to whatever else is registered on that
   Consul, which is three other products.

So `docker-compose.prod.yml` now carries `DISCOVERY_LOCATOR_ENABLED: "false"` and the same four
narrowed static routes, making the documentation true rather than aspirational.

### What was checked

Against the running quality stack, with an HS512 `ROLE_CUSTOMER` token minted from the estate's
signing key — because **no test in this repository can assert this**. Both new ITs talk to their
service directly, so a green suite says nothing about what the gateway will route.

Before narrowing, that ordinary customer token reached catalog on
`/services/healthconnectcatalog/management/health` (**200**) and on
`/services/healthconnectcatalog/internal/professionals/p1/login` (**403** — refused by the service,
having been proxied to it; against the new catalog image it would have returned the login). After
narrowing, both are **404** at the gateway with no route, while `/api/professionals/count` and
`/api/professionals/p1` still answer 200 with the token, the three anonymous public reads still
answer 200, and booking and messaging still route.

That is the whole control, and it is worth restating why it had to be measured rather than reasoned
about: `/services/**` is `authenticated()` at the gateway, so an *anonymous* probe returns 401 for
both the safe and the unsafe case and proves nothing at all.

**Not resolved, and it needs the host to settle:** production's compose services are named
`gateway`, `catalog`, `booking`, `messaging`, `payout` on the shared external `infranet`, and
compose publishes a service name as a DNS alias on every network it joins — the exact collision D27
had to fix on `hcnet`, where `catalog` and `booking` were already claimed. Whether `gateway` is
already taken on `infranet` by a sibling product cannot be checked from a workstation. The static
routes above address the *routing* half; the naming half stays open and is listed below.

---

## D29 — The rest of the open list: what gets built, in what order, and why that order

**Decided 2026-08-31**, answering the engineering items D26 left and D27/D28 added. Five things are
in: the `deliveryMode` default, per-estate topic prefixes, the D16 audit trail, D21 time zones, and
SSE at the gateway. One more is a change of stance: the prototype gets opened up and pointed at the
live estate.

### The order, and why it is not the order they were asked in

**Regeneration first, everything else after.** D16 adds an entity and D21 adds fields, so both are
JDL changes — and `jhipster jdl --force` rewrites every generated file in an app, including
`pom.xml`, the Liquibase master changelog and `application*.yml`. This is the same trap D26 hit and
corrected: it put observability first because it looked like "configuration only", and would have
put the whole of D25 in front of a regeneration that discards it.

So:

1. **Topic prefix** and **`deliveryMode`** — hand-written service classes only, no generator, no
   schema change. Immediate, and independent of everything below.
2. **D16 + D21 in one regeneration per app.** Batched deliberately: each extra `jhipster jdl --force`
   run is another pass through the regeneration-hazard checklist in `CLAUDE.md` and another chance to
   drop the `professional_rating` view include or leave an ambiguous mapping behind.
3. **SSE at the gateway** — the gateway has no JDL entities, so nothing above touches it.
4. **Open the prototype** — last, because it consumes the API rather than changing it, and every
   screen it drives should be driving the finished contract.

### The topic prefix: a property, defaulting to empty

`healthconnect.topics.prefix` defaults to `""`, so **production and quality keep the exact topic
names they have today** and nothing on the shared broker moves. Only the dev compose sets one
(`dev.`). A mistake in this mechanism therefore cannot rename a production topic — the failure mode
is a dev estate that talks to itself, which is the intent anyway.

The alternative — an explicit prefix per environment, `prod.`/`quality.`/`dev.` — is more
symmetrical and self-describing, and it renames topics two running estates already use. Consumer
offsets are keyed by (group, topic, partition), so renaming resets every one of them and strands
whatever is in flight on the old names. Symmetry is not worth that.

**Two things make this cheap, and both are prior decisions paying off.** Consumers switch on the
*envelope's* `type` field, not on `record.topic()`, so a prefixed topic does not break a single
`switch` — the domain event type was never the transport address. And booking publishes through the
outbox, so the prefix is applied at **send** time in `OutboxPublisher` rather than at record time:
the stored row keeps the logical topic, which means an unsent row survives a prefix change and the
outbox stays a log of domain events rather than of Kafka addresses.

What this does *not* fix: the two estates still share one broker, and a dev estate configured with
an empty prefix by mistake is back to crossing. `deploy-dev.sh` keeps its warning.

### SSE: built, at the gateway, and only there — with three things found on the way

D25's closing note said drop the claim or build it at the reactive gateway fed by Kafka, never in
imperative `messaging`. It gets built. The gateway is the only reactive application in the estate and
the only one already holding a connection to every client, so it is the only place a long-lived
per-user stream costs nothing structurally.

The alternative considered and rejected was dropping the claim: there is no frontend today, so the
channel would have no reader. That reasoning inverts with the decision below — the prototype is being
opened up, so there *will* be a reader, and a marketplace whose bookings change state under the
customer is exactly the case polling serves worst.

**What JHipster generates is a sample, not this.** `broker.KafkaConsumer` and
`/api/healthconnect-gateway-kafka/consume` look exactly like the feature and are wrong in four ways
at once: the sink is `unicast()`, so the **second** connected client gets an error; there is no
`text/event-stream` content type, so it is not SSE; there is **no per-user filtering**, so every
subscriber would see every event and customers would read each other's bookings; and it binds to
`sse-topic`, which nothing in hc-market publishes to. Both are left in place — they are generated, and
a regeneration would put them back — with the real implementation in new files beside them.

**The consumer group is unique per instance, inverting the estate's own rule.** Everywhere else a
shared explicit group is right: work is divided, each event is handled once, and D27's compose files
warn at length against anonymous groups losing offsets. For a fan-out it is the opposite. Every
gateway instance must see *every* event, because the user it needs to reach may be connected to any of
them — a shared group would give two instances half the partitions each and half the connected users
would silently never be told anything. So the group carries `${random.uuid}` and its offsets are
deliberately disposable; events published while an instance was down are worthless to a live stream by
definition.

**`@KafkaListener` in the gateway had to be proven, not assumed.** The gateway carries
`spring-cloud-starter-stream-kafka` and no `spring-boot-starter-kafka`, and everything JHipster
generates for it goes through Spring Cloud Stream bindings instead — so a listener that was never
wired would have failed in the way this repository keeps getting caught by: context starts, bean
exists, endpoint answers, stream opens, nothing ever arrives. `MarketplaceEventFanoutIT` publishes to a
real broker and waits for it to come out of the sink. It works.

**What could not be tested, and why it is written down rather than quietly skipped.** The SSE framing
is not asserted over HTTP. `@IntegrationTest` binds `WebTestClient` to the application context rather
than to a port, and a mock-bound client buffers the whole response before returning from `exchange()`
— which never happens for a stream that by design never completes. Measured at every timeout it was
given, up to 40 seconds. Only the 401 can be asserted, because it short-circuits before a body exists.

So the filter moved: `streamFor(login)` lives on the fan-out rather than as a predicate in the
resource. Addressing was already that class's concern — it decides who an event is *for* — so it
should also decide who may see it, and the disclosure boundary is now one file that a real-broker test
can exercise with no HTTP client in the way. Covering the framing end to end needs a real port and a
real minted token; that test is not written and is listed as open.

**The browser check found a defect in seconds that nothing headless had caught.** The prototype is
now served same-origin from the quality vhost at `http://market.healthconnect.local/prototype`, which
also removes CORS from the picture entirely — `?api=` with no value reaches the gateway directly,
including `/api/stream`. The first thing on screen was **`FROM ₵NaN / session`**, on the profile and
on every browse card.

`p.rate` — the indicative "from" price — is a *derived* field that block 1 computes once at load,
`Math.min` over the professional's paid services. Live mode replaces `PROS` wholesale and recomputed
`rating` and `reviewCount` but not `rate`, so `money(undefined)` rendered `₵NaN`. Nothing threw. The
verifier did not catch it because it asserted `services[0].price`, and the figure on screen comes from
a different field.

That is the case for the rule `CLAUDE.md` already states — a change touching a screen ends in a real
browser — and it had been deferred three times before this. The verifier now checks every `rate` is
finite and that p1's equals its cheapest paid service.

**The vhost's CSP needed a scoped exception, and it is the one the file warned about.** The site
policy is `default-src 'none'`, truthful for an API and fatal to a single HTML file of inline script
and style. The `/prototype` location grants `'unsafe-inline'` for script and style plus
`connect-src 'self'`, and nothing else; the API keeps the strict policy, confirmed by reading the
header back. `add_header` REPLACES rather than merges, so that confirmation matters.

**The heartbeat's first tick had to move to zero, and a test found it.** `Flux.interval(HEARTBEAT,
HEARTBEAT)` meant the stream emitted nothing for twenty seconds — and WebFlux does not commit the
response until the first element is written. So a client received no status line and no headers for
those twenty seconds: an `EventSource` sitting in CONNECTING, and any proxy with a short header
timeout free to kill a connection that had not yet said anything. Firing the first comment
immediately commits the response, which is what an SSE endpoint should do. Found by a real-port test
that could not get past `exchange()` — the client was not slow, the server had genuinely sent nothing.

**Two nginx directives became load-bearing for a reason they were not written for.**
`quality/host-site.conf` already had `proxy_buffering off` and `proxy_read_timeout 1h`. With buffering
on, nginx holds each frame until its buffer fills, so events arrive in clumps or not until the
connection closes; with the default 60-second read timeout an idle stream is cut every minute. Neither
looks like a failure — the browser reconnects silently and the only symptom is a channel that misses
things. The gateway also sends a keep-alive comment every 20 seconds, which covers a default-configured
proxy that this file does not control.

### The prototype gets opened up

Until now the prototype was a **closed demo** — no `fetch`, no `API_BASE` hook, `TODAY` hardcoded —
and that was the right shape while it was the acceptance target and nothing else. It changes role
here: it becomes the first client, which makes it the fastest way to find out whether the API a
screen needs is the API it got.

This is not a decision to build the product's frontend. `api/` and `web/` stay empty, no framework is
chosen, and nothing here commits the eventual client to a stack. What it commits to is that every
endpoint in §6 gets exercised by something other than curl.

**As built.** The demo remains the default: opened with no query string the prototype behaves exactly
as it always has, because it is still the acceptance target *and* the seed's source. `?api=` opts in;
`&token=` additionally opens `/api/stream`.

**The seam is the script block, and it is load-bearing.** `extract-seed.mjs` slices the *first* script
block and evaluates it in a vm sandbox with no `fetch` and no `window`, so live mode is the *last*
block and mutates the data consts in place rather than reassigning them. CI regenerates the seed on
every push, which is what keeps the two halves apart. Confirmed byte-identical after the change.

**Reads are live, and so are two of the three writes.** Categories, professionals, services, reviews
and availability come from catalog, and ratings are recomputed from the reviews that arrived — the
same derivation the demo does, so a rating cannot disagree with its own reviews in either mode. With
a token the customer's own bookings load from booking, and **creating a booking and publishing a
review go to the estate**. `sendMsg()` is live too, and is the one override that is a **rewrite rather than a wrapper**: the
demo pushes a reply from the professional 1.6 seconds after you send, and against a real estate that
would put words into a real person's mouth in a real conversation. The thread is re-read from the
server instead, and the verifier asserts no reply was fabricated.

The writes deliberately omit `priceMinor`, `currency` and `professionalLogin`, so the server
establishes all three. Verified on the row the prototype created: `professional_login` was
`akosua.mensah` and `zone_id` `Africa/Accra` despite the client sending neither — the first time D22,
D28 and D21 have all been exercised together by a real client rather than by a test with a mocked
`CatalogClient`.

**Which is how a real defect surfaced.** `HEALTHCONNECT_CATALOG_BASE_URL` was set in **none** of the
three compose files, so booking's `CatalogClient` fell back to `http://healthconnectcatalog` — a name
that resolves nowhere. Since D22 introduced that client, `POST /api/bookings` had been failing closed
with **503 in every deployed environment**, including the release shipped to quality minutes earlier.
Nothing caught it: the ITs mock `CatalogClient`, no health check exercises a write, and
`verify-cycle.sh` — which would have caught it — needs minted tokens and had not been run. Fixed in
all three files.

**The live channel needed `fetch`, not `EventSource`.** `EventSource` cannot send an `Authorization`
header and `/api/stream` is authenticated, so the alternative would have been accepting the token in
the query string — putting a credential into every access log and `Referer`. The client parses SSE
frames itself instead, which is about twenty lines, and drops the comment-only keep-alives exactly as
`EventSource` would.

**What it cost, and what caught it.** Two field names were guessed wrong: reviews are `authorName`,
`publishedOn` and `professionalReply`, not `customerName`, `postedOn` and `reply`. Nothing threw. The
cards rendered with a blank byline and no date, which reads as sparse demo data rather than as a bug.
`deploy/verify-prototype-live.mjs` exists because of that — it extracts the **shipped** block and runs
it against a live estate rather than restating the mapping, since a restated copy would pass while the
page drifted. Against the quality stack it reproduces the demo's own figures exactly: 18 professionals,
63 reviews, 52 services, p1 at 4.7 from 7 reviews, prices in cedis.

**A literal closing script tag inside a comment ends the element.** The block's header comment
originally described the extractor's slice using the real tags, which terminated the script early and
rendered the rest of the file as text. The tags are spelled out in words there now. Found by
`node --check` on the extracted block, not by looking at the page.

**Not verified in a browser.** The mapping, the derivation and the banner are all exercised by the
verifier above, against a real estate, through the real code. What is *not* covered is rendering: the
Chrome extension available here would not hold a `localhost` page long enough to script it, and rather
than keep retrying that, the gap is recorded. Loading the page by hand is the outstanding step, and
CLAUDE.md's rule that a change touching a screen ends in a real browser still applies to it.

---

## D30 — Four answers, 2026-08-31

Taken after the D29 work, and recorded because three of them close items that had been sitting on the
"needs a person" list.

### D28's `infranet` question is closed by making it moot

The question was whether `gateway` is already a DNS alias on production's shared `infranet`. It cannot
be answered from a workstation, so it is not answered: **the production compose services are renamed
`hc-market-*` with explicit container names**, and the gateway's static route hosts point at those.
The aliases this stack publishes on that shared network are now unique to this product, whatever else
is on it.

Making a collision impossible beats investigating whether one exists — the same conclusion D27 reached
on `hcnet`, where `catalog` and `booking` turned out to be taken and docker was answering with
whichever container it felt like, silently.

`deploy-prod.sh` maps the short CLI names onto the compose ones, so `--services catalog,booking` is
unchanged. That mapping is not cosmetic: `docker compose up -d gateway` against a file with no such
service fails loudly, but `docker compose pull` with no arguments would quietly pull everything.

### The release agent no longer tells itself to skip consent

`~/.claude/agents/code-pipeline.md` Step 5 read *"production is launched and returns a 200 but it is
not operational so deploy without asking for express permission."* Two halves had come apart in it:
whether production is *operational* is a different question from whether deploying to it needs
consent, and a standing instruction to skip consent outlives the circumstance that motivated it.

Step 5 now halts before production and produces a `--dry-run` plan. An operator who wants a deploy
says so in the invoking prompt; an agent concluding on its own that permission was implied is the
failure the wording now prevents.

### `hc-infra`'s header no longer claims ports hc-market gave back

It said hc-market's dev estate holds 18500 and 19092. False since D27 removed that stack's private
broker and Consul. Corrected in place — `hc-infra` is not a git repository, so there is no branch to
put it on — and comments only, no configuration touched. The shifted ports stay where they are, which
the file already explains: a port that means one thing in the file and another on the host is how an
afternoon gets lost.

### The SSE-on-the-wire gap gets investigated rather than routed around — and it was a real bug

An unexplained difference between two test contexts, in a live channel, was worth understanding. It
took one bisect to find that **every SSE frame this estate has ever sent carried garbage**.

The bisect subscribed to the fan-out directly and over HTTP in the same context and the same run.
Result: `fanout=2 http=1` — the consumer was fine, so the fault was between the sink and the socket.
The single HTTP frame read:

```
{"array":false,"bigDecimal":false,"binary":false,"containerNode":true,"nodeType":"OBJECT", …}
```

The payload was a `JsonNode` in the `ServerSentEvent`'s data, and Jackson serialised it by its BEAN
PROPERTIES — the results of `isArray()`, `isBigDecimal()`, `getNodeType()` — instead of the JSON it
represents. Not a subset of the event, not a mangled event: none of it.

**Nothing failed.** The connection opened, frames arrived on time, the fan-out test passed, and
`verify-prototype-live.mjs` passed end to end against a live estate — because the only client that
exists reads the `event:` name to raise a toast and never looks at `data`. A stream that is live,
punctual and carrying nothing is the exact shape of defect this repository keeps producing, and the
only reason it surfaced is that somebody insisted on asserting the wire rather than the behaviour.

Fixed by converting the payload to plain maps at ingestion, so the record is usable by anything that
subscribes and there is one place to get it right. The wire test now asserts the real payload *and*
that `nodeType` and `bigDecimal` are absent, so a regression cannot pass as "some JSON arrived".

---

## D31 — The three items that were waiting on a person, answered 2026-08-31

D15, D16 and D24 had sat in the "needs a person" table since the spec. They were put to the architect
as four questions; these are the answers and what was built from them.

### D16 — keep the badge, and make the profile say what it means

**Answered: keep it, state its meaning on the profile.**

There is no register of non-medical health professionals in Ghana to check anyone against, so
"Verified" cannot mean what a reader assumes it means — a licence looked up somewhere. It means a
person at BridgeCare looked at documents. The other two options both lose something real: relabelling
it ("Documents checked") throws away a signal customers do use and that professionals worked for, and
leaving it alone lets the screen keep an implication the business cannot stand behind.

Built in three parts, and the split matters:

- `ProfessionalDetail` gained **`verifiedOn` only** — the date. The reviewer's login and the evidence
  reference stay on the `ROLE_BROKERAGE` desk endpoint where D29 put them. A public profile saying
  *who* checked and *on what document* would publish a staff name and a document reference to anyone
  who asks, which is a different decision that nobody made.
- `MarketplaceService.verifiedOn` filters to `VerificationState.VERIFIED`. A professional under review,
  or one whose verification was withdrawn, has no date — the field is absent rather than stale, so it
  cannot outlive the state that justified it.
- The prototype renders the qualifier next to the badge: verification is a check of documents, not a
  licence.

The seeded professionals have no verification review history, so the date is absent in demo mode and
the qualifier still renders. That is the correct behaviour for a fresh estate and was confirmed in a
browser rather than reasoned about.

### D24 — build the erasure mechanism; retention periods stay configuration

**Answered: build the mechanism, periods stay config.**

The split is clean and worth stating: *how* to erase somebody is engineering, *how long* to keep their
records is law. Waiting for counsel on both would have left the estate with no way to honour a request
it will eventually receive; inventing a retention period would have had a developer take a legal
position on Ghanaian data protection and on the retention obligations sitting on financial records.

**Pseudonymisation, not deletion.** `ErasureWorkflow` in booking, messaging and catalog replaces
`customerLogin` with `erased-<first 12 hex of SHA-256(login)>` and redacts the free text: the visit
address and customer note in booking, the message bodies in messaging, the author name and initials in
catalog. Deleting instead would break more than it protects — `Ledger` rows in payout are keyed by
`bookingReference`, financial records carry their own retention obligation that an erasure request does
not override, and a professional's earnings are aggregates over rows that must still exist to be
aggregated.

**The pseudonym is deterministic on purpose**, and identically derived in all three services, so one
person carries one alias estate-wide and their rows stay reconcilable against a payout without naming
them. A fresh random value per row would have made an erased customer's booking history impossible to
audit.

**This turned out to be cheap because of a rule chosen for another reason entirely.** Redacting a
customer requires *no recomputation anywhere* — there is no `professional.total_earnings` and no stored
rating, so every figure in the estate is a view or a query over rows the erasure leaves in place. Had
those been columns, erasure would have meant recomputing each one and getting every rounding decision
right a second time, under legal deadline. "Derived, never stored" paid off in a place nobody picked it
for. `ErasureResourceIT` asserts both halves, and the second is the one that matters: the booking
reference, the money fields, `professionalRef`, the status and the date all survive intact.

**Three deliberate boundaries.**

1. **The review body is not erased.** Author name and initials go; the text stays. A review is public
   speech about a professional, relied on by other customers and already answered in public by the
   professional — erasing the person is not the same as retracting what they said. This is the
   judgement here most likely to need revisiting once counsel has an opinion, and it is one method to
   change if the answer comes back the other way. Favourites are deleted outright instead: a saved
   list is purely personal, nothing aggregates over it, and a tombstoned row would be an orphan.
2. **`ROLE_BROKERAGE`, not self-service.** A customer cannot erase themselves. Not paternalism: an
   erasure request has to be identity-checked before it is acted on, and an endpoint that erased on
   the strength of the caller's own token would let anyone who borrowed a session destroy that
   person's history. The check happens off-system, by a person; this is what they call afterwards.
3. **It is three calls, not one.** There is no service-to-service authentication in this estate, so an
   orchestrating endpoint would need a mechanism that does not exist. Sequencing belongs in the
   `hc-admin` desk. Until it is there the gap is real: calling one and not the others leaves a
   partially erased customer. Recorded rather than hidden.

**Deploying it found something the tests did not.** Erasing a customer on the quality box returned
`messagesErased: 0` for somebody whose conversation had just been pseudonymised — a booking raises a
thread before anyone writes in it, so a customer can hold a row keyed to their login and no messages
at all. The redaction was correct; the *receipt* was not, and the receipt is the thing an operator
files against the request. Filed as "messaging held nothing for this person", it would have been
false. It now reports `conversationsPseudonymised` beside `messagesErased`, and
`ErasureResourceIT` covers the empty thread specifically.

The general lesson is the one this repository keeps relearning: the estate was green, the endpoint
worked, and the only thing wrong was a number nobody would have questioned. It took running it
against real data to see.

**`healthconnect.privacy.retention-days` has no default and nothing sweeps on it.** The absence is the
point — a plausible-looking `365` would be worse than nothing, because it would stop anyone asking.
`GET /api/desk/privacy` reports the configured value and reports `enforced: false` alongside it, so a
configured period is never mistaken for an applied one. Enforcement needs a scheduled sweep and there
is no scheduler anywhere in this estate, the same gap `Dispute.dueBy` records; when one exists it calls
`eraseCustomer` and nothing else has to be decided.

**Still with counsel, and unchanged by any of this:** retention periods, lawful basis, controller
registration, data residency, and whether review text must go too.

### D15 — build a provider-agnostic seam now

**Answered: build a provider-agnostic seam now.**

This goes against D15's own recorded position, which was to build nothing until a provider and an Act
987 opinion exist, and the reasoning for that position has not changed: a payment seam written before
anyone knows whether settlement is split at capture or reconciled afterwards is a guess at the shape,
and the wrong shape is more expensive to remove than no shape at all. The instruction is explicit, so
it is implemented — and the risk is recorded here rather than argued again later.

What that means in practice, and the constraint that keeps it honest: **the seam must not encode a
settlement model.** It carries an intent to be paid and a record of what happened, and it does not
assume the money moves in one hop, in two, or at capture rather than at completion. The moment it
assumes one of those it stops being provider-agnostic and becomes an unfinished integration with a
particular provider, which is exactly what D15 wanted to avoid.

**As built.** `PaymentProvider` in booking's `service.payment`, with `authorize`, `capture`, `refund`
and `status` — and the property that makes it survivable is what is *missing*: **nothing on it pays
the professional.** The two plausible arrangements in Ghana settle that leg completely differently. A
split-settlement provider moves the professional's share itself at capture and the platform only
records it; a reconcile-afterwards arrangement has the platform receive everything and disburse later
against the ledger. A `payProfessional` method would have picked one. Payout's `Ledger` already
records what each professional is owed, derived from completed bookings, and that record is correct
under either model. Likewise `authorize` and `capture` are separate calls because some providers
separate them, not because this estate has decided when either happens — a provider that only does
immediate charges implements `authorize` as a capture and returns `CAPTURED`.

**Naming today's arrangement was the unexpected value.** The seam needed a state for "the platform is
not in the money's path", and writing `OFF_PLATFORM` down made visible an assumption that had been
nowhere stated: bookings are created, completed, and a `Ledger` row credits a professional for money
this estate has never touched. That is a defensible business model. It was simply not written
anywhere, and an unstated assumption about money is the kind an accountant discovers.

**Nothing is persisted, and that is the same discipline that keeps the interface honest.** There is no
`payment_attempt` table, because the columns it needs are the provider's — reference format, status
vocabulary, webhook identifiers — and a schema that has run in production is a migration rather than
an edit. When a provider is chosen the table is obvious and the interface does not change.

The one call site is `POST /api/bookings`, before the row is written: authorizing afterwards would put
a third-party call inside the transaction that publishes `booking.requested`, so a provider timeout
would roll back a booking the customer's screen had every reason to believe was made. A decline is
**402 with nothing written** — a booking without its money blocks a professional's diary for a session
nobody paid for — and a provider that fell over is **502**, because the client's next move is retry
rather than find another instrument.

The only implementation is `UnconfiguredPaymentProvider`, which reports `OFF_PLATFORM` and **throws**
on `capture`, `refund` and `status`. The asymmetry is deliberate: there is no money to move and no
provider to ask, so any caller reaching those holds a false belief, and a polite `FAILED` would let it
survive as an apparent outage that gets retried. `PaymentSeamIT` substitutes a provider to exercise
the decline and failure branches, because a refusal path that has never run is a refusal path nobody
knows the shape of — and the day a provider is added is the wrong day to discover that a declined
payment produces a booking anyway.

### The pending non-code items

**Answered: merge PR #2 and retarget #3 at main; push the hc-admin branch.** Both are done. PR #2
(`live-messaging-and-sse-framing`) merged, #3 was closed and reopened as **#4** against `main`, and
`hc-admin/quality`'s `fix-stale-hc-market-port-note` is pushed and open as that repository's PR #17 —
a comment-only correction, since hc-market's dev estate stopped holding 18500 when it moved onto
`hc-infra`'s shared plane.

The instruction *"skip merging PR #2 in this run"* came later in the same session and was overtaken:
by then it had already been merged. Recorded because a later instruction that reads as a reversal is
worth being able to tell apart from one that was simply already satisfied.

---

## D32 — Erasure has to outlive the moment it runs in

Found by verifying D31's endpoints on the quality box, which is the only reason it was found at all:
every test passed, the endpoint did what it said, and the estate was green throughout.

### The race

Create a booking, erase the customer immediately, and messaging raises the conversation *afterwards* —
from the `booking.requested` event still in flight. Under the original login. The evidence:

```
t-b-a2216d8d | verify.subject
```

still sitting there, seconds after `verify.subject` had been erased and a clean receipt filed saying
so. The window is small and the row held only the login, no name or message body. It is still the one
class of request where "we erased them, then re-created them from a queue" is not an answer anybody
can give.

**Erasure was a one-shot sweep, and a sweep is only correct if nothing arrives afterwards.**

### `erased_subject`, holding pseudonyms and nothing else

A table in messaging — hand-written and included in `master.xml` like `processed_event` and
`outbox_event`, because it is infrastructure rather than anything the JDL should model. The consumer
consults it before writing anything keyed to a person, and writes the pseudonym instead of the login
when it finds a match.

**There is deliberately no column for the login.** A register of erased people that names them is
precisely the thing erasure was asked to remove, and — unlike every row being redacted — it would have
to be kept forever for the check to keep working. So the check runs the other way: the consumer
already holds a login, hashes it with the same rule everything else uses, and looks for the result.
Neither side ever stores the original. The cost is that the table cannot answer *who* has been erased,
only whether *this* person has, and that is the only question anything here asks.

**The row is still written.** Skipping it would leave a professional's thread list and bell menu with
holes where a real booking is, in order to protect an identifier that can simply be replaced.

### And a second gap the same investigation opened

Reading the consumer to fix the race showed where else a customer's identity ends up:

- **`Notification.recipientLogin`** was never touched by erasure at all. Notifications addressed to
  the customer — "Your home visit on 12 Sep is confirmed" — kept their login indefinitely.
- Worse, **`booking.requested` puts the customer's NAME in the professional's notification**: "Ama
  Mensah asked for a home visit on 12 Sep". That row is keyed to the *professional's* login, so no
  query by recipient returns it, which is exactly why it survived. It is found through `deepLink`,
  which is `/bookings/<ref>` for everything this service raises, matched against the erased customer's
  own conversations.

Notifications to the customer are re-keyed; notifications about them, held by somebody else, have
their body redacted and the row kept — it is a real event in the professional's history, and the name
can be removed without removing the event. The receipt reports the two counts separately, because a
single total would give an operator no way to tell that data held *about* this person *by another
user* was dealt with too.

### What this says about the testing

`BookingEventConsumer` had **no test of any kind** before this. It is the only code in the service
that stores a person's login without a person having just authenticated, and it was the one place
erasure did not reach. The gap and the missing coverage were the same gap, and the tests that existed
all passed while both were true.

D24's mechanism now has an integration test for the late event, for the name in somebody else's menu,
and for the register answering about a login it has never stored.

---

## D33 — A date must not outlive the badge it dates

Found by a code review of D31, and it corrects a claim made in D31 itself: that `verifiedOn` "filters
to `VERIFIED`, so a professional under review, or one whose verification was withdrawn, has no date —
the field is absent rather than stale". The first half was true and the second did not follow.

`MarketplaceService.verifiedOn` looked for the most recent `VERIFIED` review **anywhere in the
history**, which means it scanned straight past a later suspension. A professional with
`VERIFIED (Jan)` then `SUSPENDED (Mar)` was published as:

```json
{ "card": { "verification": "SUSPENDED" }, "verifiedOn": "2026-01-14T09:41:07Z" }
```

Both fields correct in isolation, and together a lie. Any client that renders "Verified on {date}"
from the date being present — the obvious implementation, and the one the prototype uses — shows a
verification badge for somebody whose verification was taken away. That is the exact harm D16 exists
to prevent, arriving through the field D16 added.

**The original reasoning was sound and guarded the wrong direction.** The comment said taking the
latest review of any kind "would date a badge from the review that removed it", which is true. It
stopped the badge being dated *from* the removal; it did not stop the date *surviving* the removal.

Now the latest review is taken first and its date returned only if that decision is `VERIFIED`. This
also makes the date agree with `Professional.verification` by construction — the column the desk
projects from the very same review — where before the two could contradict each other in one payload.

**The test is the point of this entry.** `suspensionClearsTheDate` posts `VERIFIED` then `SUSPENDED`
and asserts the date is gone; `reVerifyingRestoresTheDate` covers the way back. Both were confirmed to
**fail against the old implementation** before being kept, because a regression test that has never
been seen to fail is a test of nothing. One review of any kind could not have caught this — it needed
two, in order, which is why nothing existing did.

---

## D34 — Erasure reached one table out of five

A code review of D31/D32 went looking for anywhere a customer's identity could survive an erasure.
It found four tables in booking, a full-table scan in catalog, a race that D32's own fix does not
close, and a service with no erasure test at all. Every one was confirmed against the code before
being acted on.

### The four tables booking never touched

The receipt said `bookingsErased: 1` while the person was still named in:

| Table | Column | What it held |
|---|---|---|
| `outbox_event` | `payload`, `actor` | `customerLogin` **and** `customerName`, in one row per event ever published about them |
| `dispute` | `raisedByLogin`, `reason` | their login, and a thousand characters of what they said went wrong |
| `booking_status_change` | `actor` | their login on every row they caused — requesting and cancelling are both customer actions |
| `dispute_status_change` | `actor` | the same |
| `booking` | `cancellationReason` | 400 characters, usually explaining something personal about why |

**The outbox is the worst of them and deserves naming plainly.** There is no purge of sent rows
anywhere in booking — `sent_at` is only ever set — so an erasure that reported success left the login
and the display name sitting in a table nothing reads and nobody looks at, indefinitely. Now rewritten
in place, *including unsent rows*: an event still waiting to go out should carry the pseudonym to its
consumer rather than depend on the consumer recognising the login as erased. The payload is parsed and
re-serialised rather than string-replaced, because a blind substitution corrupts JSON the first time a
login appears inside another value.

`Dispute.resolution` is **deliberately kept**: it is the brokerage's own record of how a financial
dispute was settled, it underpins a compensating ledger entry, and it is retained on the same basis
the ledger is. It may name the customer. That goes to counsel beside the review-body question.

The `note` column on both status-history tables is a system string — `"raised"`, the transition's
action — not user text, so it is left alone.

### The race D32 narrowed but did not close

D32 recorded the pseudonym at the top of the erasure transaction and said that covered an event in
flight. Under `READ_COMMITTED` it does not. The register row is invisible to any other transaction
until the erasure **commits**, so:

1. the consumer begins, reads `isErased` → false;
2. the erasure runs its sweep — which cannot see the consumer's uncommitted conversation either;
3. both commit, and the conversation exists under the original login, against a filed receipt.

Byte for byte the failure D32 was written to close, in a smaller window. The comment in the code
claimed a guarantee the code did not have, which is the part worth being uncomfortable about: the
mechanism was right and the reasoning about it was one step short.

Closed with a Postgres **advisory transaction lock** keyed on the subject, taken by both the erasure
and the consumer. The consumer blocks until the erasure commits and then sees the register; the
erasure blocks until an in-flight consumer commits and then sees its row. The lock releases with the
transaction, including on the paths that throw. The key is derived in Java from the same SHA-256 the
alias comes from rather than through Postgres's undocumented `hashtext`, so neither side sends a login
to the database as a query parameter where it would land in `pg_stat_activity` and the slow-query log.

### Smaller, and all confirmed

- **Catalog loaded every review in the service** to find one customer's — `findAll().stream().filter(...)`
  — in the same feature where messaging's code carries a comment explaining why that is wrong.
- **Catalog had no erasure test at all**, while both siblings had one. That is how both the scan and
  the untested author redaction shipped.
- **`erasedAt` was overwritten on a re-run.** `save()` on an existing primary key replaced the original
  timestamp with the date of whoever ran the erasure a second time — and data subject requests get
  retried, because they arrive by email and get forwarded. That timestamp is the one fact an audit of
  an irreversible action asks for. Guarded.
- **Nine columns these sweeps filter by had no index.** `booking.customer_login` also serves "my
  bookings"; `conversation.customer_login` serves the thread list on every page load;
  `notification.deep_link` is used only by the erasure and was a sequential scan of the whole table.
  One hand-written changelog per service, and three more rows in the regeneration-hazard table.

### The test that could not have caught any of it

Booking's `ErasureResourceIT` seeded a booking and nothing else. A fixture that touches one table can
only ever prove one table is erased, and it passed throughout. It now populates every table the
workflow is supposed to reach, so a new column holding personal data fails a test rather than shipping.

Every erasure IT also gained a **second customer** whose rows must be untouched. Every test in all
three services seeded exactly one person, so a regression that widened a sweep — a dropped `where`, a
query on the wrong column — would have passed all of them while erasing the estate.

### Two things this does not fix, recorded rather than left to be found

**The pseudonym is not a secret, and the javadoc implied it was.** It said "not reversible without
already knowing the login". Logins here are short and guessable, so anyone with read access to any of
the three databases can hash candidates offline, re-identify every `erased-…` row, and confirm from
`erased_subject` whether a named person was erased. The fix is an HMAC with a per-estate pepper —
identical across the three services, injected like `JWT_BASE64_SECRET`, never committed. It is cheap
now and much less so once real erasures exist, because changing the derivation re-keys nothing that
has already been written. **Done — D35**, taken while it was still cheap, for exactly that reason.

**Nothing says what happens when an erased person keeps using their account.** Erasure does not touch
the gateway's user store, so they can log in and book again. Messaging would then pseudonymise the new
booking's thread while booking and catalog store the real login — the estate disagreeing with itself
about whether someone exists. Either erasure implies account deactivation as a documented fourth desk
step, or the register must only apply to events older than `erasedAt`. Both are product decisions.

---

## D35 — An alias anybody could reverse

D34 recorded this against itself rather than fixing it, and named the reason to fix it early: changing
the derivation re-keys nothing that has already been written. That is now the whole cost of this
entry, and it is paid once. It gets more expensive every day the estate runs, and it becomes
impossible the day somebody who cannot be asked twice has been erased.

### What was wrong

The alias was `erased-<first 12 hex of SHA-256(login)>`, computed by three copy-pasted static methods,
and its javadoc claimed it was "not reversible without already knowing the login". Every word of that
is true and the conclusion does not follow. The logins in this estate are `ama.mensah` — a first name,
a dot, a surname — so the candidate space is a phone book, not a key space. Anyone holding a dump of
booking, catalog or messaging can hash a list of names offline and match the results against the
stored aliases, and every redacted row is a name again.

Against messaging's `erased_subject` register the same computation answers a worse question. The
register exists precisely so that no login is stored anywhere (D32), and the whole of that reasoning
rests on the hash being one-way in practice rather than only in principle. Hash a name, look it up,
and the register tells you whether that named person exercised a right to erasure — which is a fact
about them that erasure was asked to remove, sitting in the table built to protect it.

Nothing about this was exotic. It is the reason password storage stopped being a bare digest decades
ago, arriving in a place nobody was thinking about passwords.

### What it is now

`erased-<first 16 hex of HMAC-SHA256(pepper, login)>`, in `SubjectPseudonym` — one new top-level file,
**copied verbatim into booking, catalog and messaging**, with CI asserting the three copies are
byte-identical. The pepper is a per-estate secret injected exactly as `JWT_BASE64_SECRET` is: absent
from `application-dev.yml` and `application-prod.yml`, required by all three compose files,
generated and persisted by `quality/startup.sh` beside the signing key, and committed only in
`src/test/resources`, where the value is a fixture with the words "not a real one" in it. This
repository is public; a pepper it contains is not a pepper.

The three copies matter more than they look. There is no shared library here — five standalone Maven
projects, no aggregator pom (D6) — so the derivation is duplicated, and duplicated code that must
agree, with nothing checking that it does, is what the three static methods already were. If they
drift, nothing fails: each service goes on redacting its own rows correctly and only a cross-service
lookup comes back empty, months later, with everything green at the time. So CI diffs the files, and
`SubjectPseudonymUnitTest` — also byte-identical in the three — pins the alias for a fixed login under
a fixed pepper against a value computed with `openssl`, so a change made carefully in all three at
once is still caught.

The alias widened from 12 hex characters to 16 in the same change. The column is already
`varchar(64)`, so it was free, and doing it separately would have meant paying the migration cost
below twice.

The advisory-lock key messaging derives for the erasure race (D34) comes off the same MAC, so there is
one derivation in one file rather than two that could disagree. It is not a secrecy question — the
number never leaves the transaction — but the alternative was a second copy of a hash.

### The decision that took the longest: what happens with no pepper

Two bad options. A service that refuses to start turns a missing privacy secret into an outage of
booking, catalog and messaging — the marketplace stops taking bookings, the professional's inbox goes
dark — over a value that one desk endpoint and one consumer branch read. A service that quietly falls
back to an unpeppered digest writes a re-identifiable alias into rows in place, against a filed
receipt saying the person was erased, and there is no way back from that: nothing re-keys an alias
once it is written.

**The service starts and the derivation refuses.** `SubjectPseudonym.of` throws, the erasure desk
answers `503` naming the variable, and an unpeppered alias is impossible rather than merely unlikely.
The failure lands on the one operation that must not proceed, at the moment somebody makes it, instead
of on everyone. The alternative had a predictable ending too: an operator facing a dead estate puts a
plausible value in to get it up, and a pepper chosen under that pressure is the committed-default
failure arriving by a different road.

Startup logs an `ERROR` rather than a warning, because nothing else about an unpeppered service looks
wrong — it serves, it is healthy, and it stays that way until a data subject request arrives, which
may be months.

**Messaging has one narrow exception**, and it is the interesting half. It is the only service holding
a register of who has been erased, and its booking-event consumer asks that register before it writes
a login. Run it unpeppered and `isErased` answers "no" for everybody — *including people it erased* —
so the next lagging `booking.requested` writes an erased customer's real login into a fresh
conversation and a fresh notification. That is byte for byte the failure D32 exists to close, arriving
through a configuration mistake instead of a race, and it is silent. So `ErasureRegisterGuard` refuses
to start messaging when the register has rows and no pepper is set. Empty register and no pepper: it
starts, because nothing can have been erased and there is nothing to get wrong. That is why
`isErased` and `lockSubject` answer `false` and do nothing rather than throwing — by the time either
runs unpeppered, the register is provably empty and `false` is the true answer rather than a guess.

### The migration consequence, which is the part to read twice

**Changing the derivation re-keys nothing.** Aliases already written stay in their rows exactly as
they were, computed under the old rule, and everything recomputed from now on produces a different
string. So an `erased_subject` lookup for a previously-erased subject misses; a redacted booking in
booking and a redacted conversation in messaging that used to share an alias no longer do; and
reconciling either against a payout ledger row for the same person returns nothing. Nothing errors.
The rows are all still there, still redacted, still correct as redactions — they have simply stopped
being recognisable as the same person, which is the one property the alias existed to provide.

On the quality box this costs nothing, and the way it was found is worth recording, because the
sentence that used to sit here said the register "has been cleared" and that was **false**.

What had been cleared was the *conversation* rows — the logins that erasures had pseudonymised. The
`erased_subject` register was not touched, and could not have been by that work: the register rows
were what those same erase calls had just written. Six 19-character pre-D35 aliases were still sitting
there when D35 first deployed to quality.

So the first quality deploy of the pepper had messaging refuse to start, exactly as designed, and
crash-loop 29 times against `restart: unless-stopped`. That turned into an unplanned field test of the
ordering fix above, and it passed: the refusal came through `Failed to start bean
'erasureRegisterGuard'` → *"cancelling refresh attempt"*, **zero** Kafka listener containers started
across the whole log, and **zero** rows were written to `conversation` or `notification` across all 29
restarts. Under the old `ApplicationRunner` each of those restarts would have handed the consumer
another slice of backlog to process unpeppered.

The remedy was the documented one — `./quality/startup.sh --local --clean`, which drops the volumes
and re-seeds — and quality now runs on the new derivation with an empty register and a witness
recorded. Dev estates are the same: `deploy-dev.sh down --clean`, not a new pepper.

The lesson is narrower than "check your claims". Clearing *data about* erased people is not the same
operation as clearing the *register of who was erased*, and the two live in different tables for good
reasons. A migration note that conflates them reads as done when it is not.

**If real erasures existed, this change could not be made this way.** There would be three options and
none of them is good. Re-keying is impossible by construction: it would need the original logins, and
the entire point of the design is that nobody kept them. Carrying both derivations — try the new
alias, fall back to the old — keeps the lookups working and keeps every old alias exactly as
reversible as it was, which is the defect. Accepting the break means the estate can no longer connect
one erased person's rows across services, which is not a data loss but is a permanent loss of the
ability to answer "show me everything you still hold about this data subject" — a question that only
gets asked about people who have already asked once.

That is the entire argument for doing it now, and it is worth stating as a rule rather than as a note
about this one change: **the derivation is effectively immutable from the first real erasure onwards**.
The same applies to the pepper itself. Rotating it is indistinguishable, from the rows' point of view,
from removing it — and `ErasureRegisterGuard` catches only the removal, because a wrong pepper looks
exactly like a right one until something fails to match. So the pepper belongs with the platform's
long-lived secrets, not in a per-deploy `.env` that `deploy-prod.sh` regenerates, and the compose file
says so in the comment beside it.

### The guard ran, and it ran too late

A code review of the above found that `ErasureRegisterGuard` was correct about everything except
*when*, and being late is the whole of it: in the one scenario the guard exists for, the damage it
was written to prevent had already been committed to the database by the time it threw.

It was an `ApplicationRunner`. Spring starts `SmartLifecycle` beans inside `finishRefresh()`, which
is part of the context refresh, and `KafkaListenerEndpointRegistry` is one of those beans — starting
it starts `BookingEventConsumer`'s listener container, whose `autoStartup` resolves true. Boot's
`ApplicationRunner`s are invoked from `callRunners()`, *after* `refreshContext()` has returned. So on
an unpeppered messaging service with rows in `erased_subject`, the real sequence was: the context
refreshes; the container starts and begins draining its backlog; `storable()` runs with no pepper, so
`lockSubject` is a no-op and `isErased` answers `false` about people this service had itself erased,
and their real logins go into fresh conversations and fresh notifications, each committing in its own
transaction; and only then does the guard get its turn and kill the process.

`restart: unless-stopped` in both `quality/compose.yml` and `docker-compose.prod.yml` makes that a
loop rather than a single incident. Every restart grants the consumer another slice of the backlog
before the guard objects again, so a deep backlog is processed unpeppered a chunk at a time, at the
speed of the restart. **And the operator sees exactly what the design intended them to see** — a
service crash-looping on a missing variable, naming the variable — with nothing anywhere to suggest
that rows were written on the way past. A guard whose failure mode is indistinguishable from its
success mode is worse than no guard, because it is trusted.

An `ApplicationRunner` was the wrong hook because it answers "is the application ready to be
declared started", and the question here is "may anything in this process touch a person's login".
The second is decided during the refresh, not after it, and there is no ordering relationship between
`callRunners()` and anything the refresh already did. The javadoc's reasoning — "a failure aborts
startup and closes the context rather than logging into a service that then serves" — was true and
answered the wrong question: the consumer is not serving, it is consuming, and it had started.

The guard is now a `SmartLifecycle` at `Integer.MIN_VALUE`, throwing from `start()`. Lifecycle beans
start in ascending phase order, so nothing else in the context can precede it — including the
listener registry at `ContainerProperties.DEFAULT_PHASE`, `Integer.MAX_VALUE - 100` — and an
exception out of `start()` propagates through `finishRefresh()` and aborts the refresh, so the
context is destroyed exactly as before. Phasing it below *everything* rather than merely below the
registry's default is deliberate: a container factory can be given a custom phase, and a guard that
is only conditionally first is not a guard.

The alternative the review put forward — the count in an early bean's `afterPropertiesSet` with
`@DependsOn("liquibase")` — would also have beaten the container, and was not taken for two reasons.
It buys the ordering at the price of a hard-coded bean name, and the phase gives the Liquibase
guarantee for nothing: every singleton, Liquibase's included, is instantiated in
`finishBeanFactoryInitialization()`, before any lifecycle bean starts. Liquibase is synchronous here
(`application.liquibase.async-start: false`), so `erased_subject` exists by then — the same ordering
`SeedDataLoader` depends on, and the same race if it were ever turned back on.

Semantics are unchanged, and that is the point: empty register and no pepper still starts, because
that is what lets `isErased` answer `false` and `lockSubject` do nothing instead of throwing and
stalling the consumer over a variable one desk endpoint reads. Only the moment of the decision moved.

`ErasureWorkflow.isErased`'s javadoc claimed that by the time that line runs unpeppered "the register
is provably empty". That was false for as long as the defect existed — the register was provably
empty only *after* the runner had run, and `isErased` could and did run first. It now says so, and
says what makes the claim true.

**The test is the part worth keeping.** `ErasureRegisterGuardUnitTest` passed throughout, because it
tests the decision and nothing can see the timing from there; a replacement that only re-asserted
"the guard throws when the register is non-empty" would have passed while the defect was live too.
`ErasureRegisterGuardOrderingTest` refreshes a plain `GenericApplicationContext` holding the guard and
a stand-in registered at a real `KafkaListenerEndpointRegistry`'s phase, and asserts the stand-in was
never started — taking the phase from an actual instance rather than from a copy of the constant, so
an upstream change cannot leave the test passing against a guard that no longer runs first. It was
confirmed to **fail against the old implementation** before being kept, and it failed in the
informative way: under an `ApplicationRunner` the refresh completes without throwing at all. Its
sibling assertions cover the two states that must still start, so it cannot pass by refusing
everything.

### Six more, from a code review of the guard fix

The same review that found the guard's ordering found six other things, and they have one shape in
common with it: every one is a mechanism that reads as though it works. Five are in the deployment
path, where nothing is exercised until somebody deploys, and the sixth is in the CI checks written to
protect the pepper — checks that would have passed on the broken state they exist to catch.

**A production deploy could never have satisfied its own compose file.** `docker-compose.prod.yml`
requires `JWT_BASE64_SECRET` and `HEALTHCONNECT_PRIVACY_PEPPER` with `:?`, and `render_env` in
`deploy-prod.sh` emitted neither, having never emitted the signing key either. The script then
overwrote `$REMOTE_PATH/.env` with that output and ran `docker compose up` over ssh with no
environment, so every production deploy would have died at `up` on "platform JWT secret is required"
— after the compose file had been uploaded and `.env` rotated, with the old stack already disturbed.
The pepper half was added by D35 on top of a defect that had simply never been exercised, because
production deploys are halted. Worse than the failure was the instruction: the generated file's
header said *do not edit on the host*, so an operator who added the value by hand to get the stack up
would have it silently deleted by the next deploy, while `--rollback` restored `.env.previous`
wholesale and therefore *kept* it. The two paths disagreed about what the file contained.

The compose file's own comment described the answer — the pepper "belongs with the platform's
long-lived secrets, not in a per-deploy `.env` that `deploy-prod.sh` regenerates" — and nothing
implemented it, which is how the sentence survived being read. There is now a `secrets.env` beside
the generated `.env`, created once by hand, rewritten by no deploy and no rollback, and passed to
every remote compose invocation as a second `--env-file`. Every invocation, not just `up`:
interpolation happens on `pull`, on `exec` and on the health gate too, and a `:?` fires the same way
in all of them. Preflight checks both keys are present before the stack is touched, which is the
whole point of moving the check there — the failure now lands while the running estate is still
untouched. The script never reads the values, so `--dry-run` cannot print what it never fetched;
that was verified on both channels with sentinel values exported into the deployer's environment.

**`quality/startup.sh` could mint a second pepper, and nothing would say so.** Its precedence was
environment, then file, then generate, and an environment-provided value was never written down. Run
it once with `HC_PRIVACY_PEPPER` exported and again without, and the second run finds no file and
generates a fresh random pepper for a stack whose `erased_subject` rows were written under the first.
That is the failure this very section warns about — "a wrong pepper looks exactly like a right one
until something fails to match" — arriving through the script that was written to prevent it. An
environment value is now persisted the first time it is seen, and a conflict between a non-empty
variable and a non-empty file is fatal: one of the two matches the aliases in the volumes and the
script cannot tell which, so choosing is not its decision to take. Deleting the file, with the
volumes, is how the operator says which — deliberately, which is what the comment above it already
asked for. A teardown is exempt, because dropping the volumes is the remedy and refusing it would
refuse the fix along with the mistake.

**Two states as unrecoverable as a missing pepper, and nothing detected either.** Both were recorded
above as risks and left there. A register holding *pre-D35 aliases* — `erased-` plus 12 hex where a
peppered one is 16 — can never match anything the service computes again, so `isErased` answers false
about people it erased and the next lagging event writes their login back. It is counted by length,
because the register deliberately holds nothing that says which rule produced a row, and startup now
refuses on it and points at the migration section rather than letting the service run. A *changed*
pepper is the other, and it needed something to compare against: the first startup that has a pepper
records what it produces for a fixed sentinel input, and every later startup recomputes and compares,
so a rotation is refused at the deploy that caused it instead of surfacing months later as a lookup
that quietly misses. The refusal stands even when the register is empty, because the pepper is one
value across three services and erasure is three separate desk calls — messaging having erased nobody
says nothing about booking and catalog, which hold aliases and have no register to notice with.

The sentinel lives in its own table, `privacy_pepper_witness`, and that is the interesting part of it.
The obvious home was a row in `erased_subject`, and it would have been a defect: the guard asks that
table exactly one question — has this service erased anybody — and its "no pepper, empty register,
start anyway" answer is what lets `isErased` return `false` and `lockSubject` do nothing instead of
throwing and stalling the consumer. A sentinel row there makes `count()` non-zero for ever, the
allowance disappears with nothing saying so, and the failure presents as a service refusing to start
over a person it never erased. `ErasureResourceIT.theWitnessIsNotAnErasedSubject` pins the
separation. The sentinel input contains a NUL so no login can collide with it; the row does give
anyone with database access a known input/output pair for the pepper, which cannot re-identify
anybody but does let a guessed pepper be confirmed — so the pepper must stay 32 random bytes, as
every script here generates, and never a memorable phrase.

**A startup check is a statement about the process that ran it.** Two messaging instances against one
database, one started without the pepper, is a state the guard cannot prevent: the unpeppered one
passed while the register was empty and then answers `isErased` = false for ever, including about
everybody its peppered sibling erases afterwards, with no restart to re-trigger anything. Deployments
here are single-instance, so this is structural rather than active. It is stated plainly — all
replicas share one pepper or none may run — in the guard's javadoc and beside the variable in the
production compose file, where the person scaling a service will actually read it. And it is narrowed
rather than left: the unpeppered path re-asks the register at most once every thirty seconds, so the
window is bounded by that interval instead of by the next restart. Only the unpeppered path, which is
the only configuration that can be wrong this way — a healthy estate runs no extra query at all, and
the hot path is untouched. It throws when it finds rows, which is not the case the "do not stall the
consumer" rule protects: that rule is about an empty register, where `false` is the true answer,
where here the alternatives are refusing the event or writing an erased person's real login, and only
one of those can be taken back.

**Both CI checks could pass on a broken state.** The compose check grepped per *file*, so it was
satisfied by the string appearing anywhere — and it passes today only because all five services share
one YAML anchor. Moving messaging onto its own environment block, which is exactly how the
per-service `DISCOVERY_HOSTNAME` overrides beside it already work, would leave it unpeppered while
the other four kept the check green. The committed-value check matched only the nested `pepper:`
spelling, and Spring reads the flat `healthconnect.privacy.pepper:` just as happily — a line
beginning with `h`, which a leading-whitespace anchor cannot see. Both are now a script rather than a
grep, asking compose itself for each service's merged environment (`config --no-interpolate` resolves
the anchors and leaves the `${...}` intact) and judging a committed pepper by its *value* rather than
by its indentation. The test beside it builds both broken states and asserts that the old greps
passed on them, because a check nobody has watched fail is a check of nothing.

**And `deploy-dev.sh` pointed at a file nothing read.** It told the operator to keep the pepper in
`deploy/.env`, never sourced it, and compose auto-loads `.env` from the *project* directory — which
for `-f deploy/docker/docker-compose.dev.yml` is `deploy/docker/`, not `deploy/`. So an operator who
did exactly as they were told still hit the `:?` and concluded the script was broken. The file is now
sourced, before the defaults, so a value in it can also override the published ports.

### What is not fixed

The second of D34's two open notes stands untouched: nothing says what happens when an erased person
keeps using their account. It is a product decision and this was not it.

Two smaller ones, recorded rather than left to be found. `messaging/config/liquibase/master.xml` now
carries a third hand-written include, `privacy_pepper_witness`, and losing it to a regeneration is
silent in a new way: the guard finds no witness, concludes this is a first peppered startup, and
records a new one under whatever pepper is currently set. It belongs in `CLAUDE.md`'s
regeneration-hazard table beside the `erased_subject` and `processed_event` rows. And the witness
proves only that the pepper has not changed *since this database first saw one* — a service brought
up wrong on its very first peppered start records the wrong value as correct, which no check inside
the service can distinguish from the right one.

---

## D37 — The six blocked items, answered 2026-09-02

Every item in `docs/backlog.md` that was waiting on a person rather than on engineering was put to the
architect. All six came back. Two of them corrected a premise in the question itself, which is
recorded here rather than quietly fixed.

### WP-07 — hc-market gets its own service-to-service key, and my question was wrong

**Answered: create a shared JWT key for the services here, because hc-market is unrelated to hc-admin
in any shape or form.**

The question offered "sequence it in the hc-admin desk" as the recommended option, on the stated
grounds that the admin console "already holds a staff token that all three services accept". That is
false. The platform-wide signing secret in `~/webroot/01-healthconnect/.env` is shared by **hc-admin,
hc-patient and hc-professional** — that is what makes cross-stack routing work between those three.
hc-market is not in that set: it carries its own `JWT_BASE64_SECRET`, generated per estate, persisted
at `quality/.jwt-secret` on the quality box and required independently by all three of its compose
files. An hc-admin token presented to hc-market's gateway fails signature validation.

So the recommendation was built on a misreading of the workspace's own key arrangement, and the answer
is the correct route: the five hc-market services already share one key with each other, and that is
the mechanism to use. A service mints a token signed with the estate key, carrying a service identity,
and the other services accept it exactly as they accept a user's.

**The consequence worth stating before it is built.** Any service holding the estate key can mint a
token for any subject with any authority, including `ROLE_BROKERAGE`. That is already true today — all
five validate against the same secret, so the key has always been an estate-wide capability rather
than a per-service one. Making it a *deliberate* mechanism does not widen the blast radius, but it
does mean the erasure fan-out must not become a general-purpose "any service may call anything"
credential: the minted token should carry a narrow, named authority used by nothing else, so a
compromised service cannot quietly widen its own reach.

### WP-08 — the register applies only to events older than the erasure

**Answered: scope the register by `erasedAt`.**

An erased person who logs in and books again is doing something new, and the erasure covered what
existed when it ran. So a booking made **after** the erasure is stored under the real login, and
everything that existed before it stays pseudonymised.

**Scope this by the BOOKING's age, not the event's** — the first wording of this decision said "the
consumer compares the event's own timestamp against `ErasedSubject.erasedAt`", and a review caught
that it says something nobody intended. Every later event on a booking that was already open when the
erasure ran — its acceptance, its completion, its cancellation — is timestamped *after* `erasedAt`.
Under an event-timestamp rule those would each be written under the real login and the real name,
putting an erased customer's identity back into the professional's bell menu one lifecycle step at a
time. It would also silently break D36's guarantee that its residual "does not grow", which rests on
exactly those later events being pseudonymised.

The intent is about a person choosing to come back, which means a *new booking*. Two ways to
implement it, and the choice belongs with whoever builds WP-08: carry the booking's own `raisedAt`
in the outbox payload and compare that to `erasedAt`, which is explicit and needs one field added in
booking; or treat a `bookingRef` messaging already holds rows for as predating the erasure, which
needs no new field but infers the answer. The first is preferable — it states the fact rather than
deducing it — but either satisfies the decision, and an event-timestamp comparison satisfies neither.
The estate stops disagreeing with itself — no more conversation keyed to an alias for a booking that
booking and catalog hold under a real name — and the historical rows stay erased.

This is the smaller of the two options and the more accurate one. The alternative, deactivating the
account as a fourth desk step, would have made "erased" a tidier state at the cost of locking out
somebody who has chosen to come back.

### WP-09 — both coded judgements stand, pending counsel

**Answered: keep both as built.**

The review **body** is not erased — it is public speech about a professional, relied on by other
customers and already answered in public. `Dispute.resolution` is kept — the brokerage's own record of
how a financial dispute was settled, underpinning a compensating ledger entry, retained on the basis
the ledger is.

Both remain flagged for counsel. Neither is expensive to reverse: each is one method, and the rating
stays correct either way because it is derived from the rows rather than stored.

The rest of WP-09 — retention periods, lawful basis, controller registration, data residency — is
untouched by this and stays with counsel. `healthconnect.privacy.retention-days` keeps its absent
default.

### WP-13 — all three payment providers, with the customer choosing

**Answered: implement Paystack, Hubtel and MoMo direct so customers can choose. Settlement is arranged
separately with each provider.**

This is a larger answer than the question anticipated and it changes the shape of work already built,
so the consequences are worth setting down plainly rather than discovering them in the implementation.

**The settlement seam survives, and that is the good news.** "Settlement arranged separately with each
provider" is exactly the property `PaymentProvider` was designed for: nothing on it pays the
professional, because split-at-capture and reconcile-afterwards settle that leg differently. Three
providers with three settlement arrangements is the case that omission exists to accommodate.

**Three things now have to change.**

First, **the single-bean wiring goes.** `PaymentConfiguration` supplies one `PaymentProvider` via
`@ConditionalOnMissingBean`; offering a choice needs a registry keyed by provider name, with the
unconfigured off-platform implementation as the fallback rather than the only entry.

Second, **the customer's choice has to reach the seam**, which means a provider identifier on the
booking request and on `PaymentIntent` — and by D22's rule, a client-supplied field that something
downstream trusts must be validated against something the server knows, not taken on faith.

Third, and most consequentially, **WP-11 stops being optional**. All three of these providers confirm
asynchronously — Paystack by redirect, Hubtel and MoMo by a prompt on the customer's phone and a
webhook — so none can truthfully return `AUTHORIZED` or `DECLINED` from the synchronous `authorize`
the seam has today. The pending state, the next-action field and the webhook contract are now a
prerequisite of WP-13 rather than an improvement to it. That also forces the question WP-11 flagged
and nobody has answered: **may a booking exist while its payment is pending?**

Act 987 remains a counsel question. It is no longer blocking construction of the seam, but it governs
whether the platform may hold customer funds at all, and that determines which settlement arrangement
each provider contract can take.

### WP-17 — both, costed before either is built

**Answered: both — get costs first.**

So the deliverable is a specification rather than an integration: for a video provider and a WhatsApp
BSP, what each would need, what it would touch, and what it would cost to run. Neither gets built
against a guess at the requirement.

### WP-18 — closed

**Answered: the rename made it moot.**

D30 chose to make a collision impossible rather than investigate whether one exists — the production
compose services are `hc-market-*` with explicit container names — which is the same conclusion D27
reached on `hcnet`. The original question no longer changes any decision, so it stops being an open
item.

---

## Still open after this section

Only what engineering cannot settle alone:

| # | Needs | From whom |
|---|---|---|
| D15 | Provider choice and contract; whether a split model clears Act 987 | Counsel |
| D24 | Retention periods, lawful basis, controller registration, data residency; whether review text must be erased too | Counsel |
| D17, D18 | Budget for a video provider and a WhatsApp BSP, if either is wanted | Architect |
| D28 | Whether `gateway` is already a DNS alias on production's `infranet` | Architect, on the host |

**D16 is closed** — see D31. Its *audit* half was built by D29 (`VerificationReview` and the
`ROLE_BROKERAGE` desk); its product half was answered on 2026-08-31 and the profile now states what
the badge means.

D15 and D24 stay in this table with **narrower** questions than they had. Both now have engineering
built against them — a provider-agnostic seam and the erasure mechanism — so what remains is the part
that was never engineering's to answer, not the whole item.

**Closed by D29, and listed here because this table said otherwise until 2026-08-31:** the `D22`
`deliveryMode` default now refuses rather than guessing; `D27`'s shared topic set is separated by
`healthconnect.topics.prefix`; and `D25`'s SSE endpoint exists at `GET /api/stream`. A table of open
items that keeps closed ones is worse than no table — it is read as current.

Engineering items genuinely open, none of them blocked on anyone:

| # | What | Why not now |
|---|---|---|
| D19 | Search is `contains()` in Java over every card | **Measured, not deferred on feel:** p95 26 ms at 18 professionals against a 5 ms control. D19's trigger is ~200 professionals or a latency measurement; neither is met. Figures and the re-measure command are in D19 |

**Six rows left this table on 2026-08-31, and every one of them had already been fixed while it went
on listing them.** Two were D29's.

`sendMsg()` is no longer the prototype write left in memory: live mode **rewrites** it rather than
wrapping it, because the demo's version invents a reply from the professional 1.6 s later and against
a live estate that would put words into a real person's mouth. `verify-prototype-live.mjs --writes`
asserts the message was sent, that the thread was re-read from the server, and — waiting a full two
seconds so a fabricated reply would have landed — that **no reply was fabricated**.

And an event published to Kafka *is* now asserted to arrive on the wire as SSE data:
`MarketplaceStreamFramingIT.anEventArrivesOnTheWire` connects to the real port with a real token and
asserts the payload, plus the absence of `nodeType` and `bigDecimal` so a regression cannot pass as
"some JSON arrived". The RANDOM_PORT-versus-MOCK difference that this row called "not yet understood"
was not a test artefact at all — it was the bean-property bug D30 found, and the row outlived its own
explanation.

Of the other four, one had been sitting there already annotated *(closed)* — which is its own small
lesson, since a row marked closed in a table headed "genuinely open" is still a row somebody has to
read and dismiss. The remaining three were deploy-path defects.

`deploy-prod.sh` no longer rebuilds by default — `DO_BUILD=0`, with `--build` to opt in, so a
production deploy uses the image CI built and verified rather than overwriting it at the same SHA.
`--dry-run` now prints `○ [dry-run] would …` for operations it skips instead of a `✓` implying a check
it never made. And the `healthconnect-<service>` image name was corrected in the script header, in
§12, and — a week later than the other two — in §11's acceptance checklist, which is the worst place
of the three for a name that cannot exist, because a checklist line is read as evidence rather than as
prose.

Which is the same failure this table exists to prevent, arriving from the other direction: an open-items
list that keeps closed items is read as current, and someone re-fixes what is already fixed. Check a
row against the code before acting on it.

---

## D36 — The notification a repeat booking hid

Found by a code review of D34/D35, and confirmed against the code before anything was written: a test
that seeds the shape the review described fails on the old implementation with
`notificationsRedacted expected:<2> but was:<1>`, on a receipt that reports 200 OK and looks complete.

D32 established that there are two kinds of notification an erasure has to reach, and that the second
is the awkward one. Notifications *to* the customer are re-keyed to the pseudonym. Notifications
*about* the customer sit in the **professional's** bell menu — "Ama Mensah asked for a home visit on
12 Sep" — keyed to somebody else's login, so no query by recipient will ever return them. They are
found through `deepLink`, which is `/bookings/<ref>` for everything this service raises, and their
bodies are redacted while the row stays, because the row is a real event in the professional's
history and the name can be removed without removing the event.

The mechanism was right. The set of deep links it was given was not.

### Why the dedupe made it invisible

The links came from the customer's own conversations, via `Conversation.bookingReference`. That
reads as complete, and it is complete only if every booking has a thread of its own. It does not:
`BookingEventConsumer.openThreadIfNone` deliberately dedupes threads **by professional**, so a
customer's second booking with the same person reuses the conversation the first one opened, and the
conversation keeps the *first* booking's reference. The second booking's reference is therefore not
on any conversation anywhere in the service, its `/bookings/<ref>` was never in the link set, and the
professional's "Ama Mensah asked for…" for that booking was never looked at.

The result is the worst shape a privacy defect can take: the erasure succeeds, the receipt is clean,
the counts are all non-zero and plausible, and the customer's name is still sitting in another user's
bell menu. Nothing is red anywhere, and the number an operator files against the data subject request
is simply too small by however many repeat bookings that person made.

Two things kept it hidden for as long as they did. The dedupe is correct — one thread per
professional is the right conversation model, and the whole point of a marketplace is that people book
the same trainer again — so nothing about it looks like a bug to read past. And every test seeded one
booking per customer, which is the same fixture failure D34 named in booking: a fixture that exercises
one of something can only ever prove the code handles one of them. Repeat bookings with one
professional are not an edge case; they are the product working as intended.

### What now guarantees the link set is complete

The deep links are the **union** of two sources: the references on the customer's conversations, as
before, and the `deepLink` of the customer's **own** notifications, collected in the loop that
re-keys them. The bridge is that the customer's copy and the professional's copy of one booking event
carry the same deep link — booking's outbox raises both from the same event, so "Your strength session
on 26 Sep is confirmed" in the customer's bell and "Ama Mensah asked for a strength session on 26 Sep"
in the professional's both point at `/bookings/b-repeat`. Finding either one finds the other.

Collecting them in the re-keying loop rather than in a second query is deliberate — those rows are
being visited and saved anyway, and `addressedTo` is one indexed lookup that already runs. Re-keying
sets `recipientLogin` and does not touch `deepLink`, so reading the link on either side of the setter
is the same string; it is read first only so the code says plainly that it does not depend on the
order. The set is a `LinkedHashSet`, so the two sources overlapping costs nothing and the single
`deepLink in (…)` query stays a single query against the `idx_notification_deep_link` D34 added. This
is emphatically not a `findAll().stream().filter(...)`, which is what catalog was doing in this same
feature and what the comment in this same method warns against.

### The one row this still cannot reach, stated rather than left to be found

A booking that is still **pending** at the moment the erasure runs has raised a notification to the
professional and none to the customer, and — if it is a repeat with that professional — shares its
thread with an earlier booking. Nothing keyed to the customer points at it, so no query the union can
make will return it. That row is the residual, and it is the only one: messaging holds no other record
of a booking's existence, and the alternative of redacting by professional would take other customers'
notifications with it, which trades a disclosure for a larger one.

It does not grow. Every subsequent event on that booking — accepted, declined, cancelled, completed —
is written by `BookingEventConsumer`, which consults the register D32 built and writes the pseudonym
and "A customer" instead of the login and the name. So the window is one row per booking that was
pending at the instant of erasure, and it closes for everything that happens afterwards. Closing it
properly needs messaging to know about bookings it has no thread for, which is a schema change and
belongs with WP-06's durable record rather than here.

**Corrected by D38, and not in the way this section predicted.** It did not need a schema change: the
fan-out hands messaging the customer's booking references, which booking has been authoritative for
all along, so `/bookings/<ref>` for the pending booking enters the link set without messaging learning
anything durable. And the residual did not disappear — it acquired a boundary. The references arrive
in a *payload*, and a direct desk call sends none, so `POST .../erase` still cannot reach that row
while `POST .../erase-everywhere` can. The paragraph above is therefore true of the single-service
endpoint and false of the fan-out, which is a much more useful thing to be able to say than "one row
somewhere". See D38.

### The receipt

`notificationsRedacted` means exactly what it meant before — notifications in somebody else's list
whose body named the customer — and now counts all of them rather than a subset. No operator reading
an old receipt should reinterpret it; they should assume it was too low.

**And, from D39, no longer too high on a second run.** The count widened here without anybody noticing
that widening it also made it repeat: the rows are matched on `deepLink`, which does not change when a
body is redacted, so every later erasure of the same customer found them again and reported the number
it had just re-written. The definition in this paragraph is unchanged; what changed is that it now
counts bodies this run replaced rather than bodies it matched.

A re-keyed notification is counted in `notificationsReKeyed` and never also in `notificationsRedacted`,
even though its body is now redacted along with the re-key for the reason four paragraphs below. One
row appearing in two counts would inflate a figure that gets filed against a data subject request, and
the two numbers answer different questions: how many rows stopped being addressed to this person, and
how many rows in *other* people's lists stopped naming them.

### A review of the fix, and the four things it found

A code review of the above went back through the mechanism and found nothing wrong with the union
itself, which is worth stating before the rest of this. What it found were four ways the same defect
gets back in — three of them through work already scheduled, and none of them visible as a failing
test. A fifth belonged to D37 rather than here and was corrected there: the first wording of WP-08
scoped the register by the *event's* timestamp instead of the *booking's* age, which would have put an
erased customer's name back into the professional's bell menu one lifecycle step at a time and
silently falsified the "it does not grow" claim this section rests on.

**The residual was prose and nothing else.** It is stated above, and restated in `ErasureWorkflow`'s
javadoc, and nothing executable asserted it — while WP-06, WP-07 and WP-08 all touch this mechanism.
This repository's own rule is that a regression test nobody has watched fail is a test of nothing, and
a documented gap nobody asserts is the same thing from the other side: there is no way to tell whether
the residual is still one row, has grown, or was closed by accident. `ErasureResourceIT
.theResidualIsOneRowForAPendingBooking` now seeds exactly that shape — a professional-side "Booking
requested" row for `b-pending`, no customer-keyed row pointing at it, and the thread keyed to `b-first`
— and asserts that the row is neither redacted nor counted. **It pins current behaviour deliberately
and was never seen to fail**, which its javadoc says in those words so that nobody reads it as a
regression test. The day WP-06/WP-07 gives messaging the booking references it holds no thread for,
that test goes red, and going red is the point: it forces this section to be corrected in the same
commit rather than being left describing an estate that has moved on. The null-`deepLink` branch of
the filter is pinned beside it, for rows that demonstrably exist.

**WP-07 arrived and that test stayed green, which is worth being uncomfortable about for a moment
before accepting it.** The prediction assumed the references would reach messaging by some route this
test would travel; they reach it in a request body, and this test sends none. So the mechanism it was
built to catch did change and it correctly did not fire, because the behaviour it pins is still the
behaviour of the endpoint it calls. It now has a partner —
`theResidualIsClosedWhenTheFanOutSuppliesTheReferences`, the same fixture with the references
supplied, confirmed red beforehand with the same `expected:<2> but was:<1>` this section opens with —
and the pair is what states the boundary. A test that pins a gap has to name the path it pins it on;
this one did not, and that is the general lesson rather than anything about erasure.

**The union's completeness rests on two invariants nothing enforced.** The first is that every
notification about a person carries `/bookings/<ref>`. The column is nullable and `MessagingSeeder`
already writes notifications with no deep link at all, so this was true only of the rows `raise()`
happens to produce, stated in a javadoc, and about to be tested by the next person to add a case. That
person is predictable: the `default` branch of `BookingEventConsumer`'s switch currently swallows the
`notification.raised` fan-in that `BookingWorkflow` already publishes for reschedule proposals and
no-shows — **and marks it processed, so a case added later can never replay what was swallowed**.
Whoever adds those cases and builds the row inline rather than through `raise()` creates rows that
erasure can never reach, with nothing red anywhere. `raise()` now refuses a blank booking reference the
way it already refuses a blank recipient, logs it and skips, and its javadoc states the invariant as an
erasure property rather than as a display convenience. One bell row is a much cheaper loss than a
permanent hole in what an erasure can find.

**The second invariant is that notification rows are append-only.** `MessagingResource` exposes no
delete today, only `readAt`, and a "clear notifications" button is an entirely ordinary feature to be
asked for. The day it deletes rows, the bridge this fix is built on dies: the customer's own copy of a
booking event is the only thing pointing at the professional's copy of it, so deleting the customer's
copies leaves the professional holding a row that names an erased person, against a clean receipt, with
nothing failing. That is the defect this section just fixed, arriving from a feature nobody would think
to connect to erasure. It is now said beside the endpoint that would grow such a feature and in
`ErasureWorkflow`'s javadoc: a clear-bell feature sets `readAt` and deletes nothing.

**A malformed link matched everybody's rows rather than nobody's.** `raise()` built
`"/bookings/" + bookingRef` without looking at the reference, so a blank one produced the literal
`/bookings/` — non-null and non-blank, therefore surviving the filter into the `IN` set, where it
matched every *other* row built the same way regardless of whose booking it was and overwrote their
bodies. The receipt would have reported a larger and entirely plausible count. The blank-reference
refusal above closes the source; the filter also rejects a link that is nothing but the prefix, because
rows written before it refused are still in the table and no migration goes looking for them.

**And the re-key skipped the body, which coupled correctness to the wording of the templates.** A
notification addressed to the customer was re-keyed to the alias and its body left alone, commented
"its body names nobody" — true of every template the estate has today, and false the first day one
greets by name. "Hi Ama, your strength session on 26 Sep is confirmed" re-keyed to an alias keeps the
name in the row for ever, and no existing test notices, because every customer-side fixture in them
happens not to name the customer. The body is now redacted along with the re-key. Nothing is lost:
the recipient of those rows no longer exists, so there is nothing on the other side of the scale, and
the correctness of an erasure should not depend on a copywriting decision made in another sprint. The
new test uses a fixture body that does name them.

### What must stay true

Three things, and each of them is one line of code away from being false. **Every notification is
raised through `BookingEventConsumer.raise` and carries `/bookings/<ref>`** — a row built inline, or
with no deep link, is invisible to erasure permanently and silently. **Notification rows are
append-only** — a clear-bell feature sets `readAt`; a delete removes the bridge between the two copies
of one booking event. **Nothing decides what to redact from the wording of a body** — the sweep keys on
recipient and deep link, and the day it starts depending on what a template says, it starts depending
on something no test in this repository guards.

### The WP-07 fan-out should carry the booking references

Stated here as a design note rather than built, because WP-07 is a package of its own. **Built as
written, on 2026-09-02 — see D38.** The note did its job: the payload carries the references from the
first version rather than being a login that somebody widens later.

Closing the pending residual properly is described above as a WP-06 schema change — messaging learning
about bookings it holds no thread for. Once WP-07's service-token fan-out exists, there is a cheaper
route that needs no schema change at all: booking holds the authoritative list of a customer's booking
references, `booking.customer_login` is indexed for exactly this kind of question since D34, and the
fan-out is already going to call messaging with a subject. If that call carries the customer's booking
references, messaging's link set becomes complete by construction — conversations, the customer's own
notifications, *and* every booking booking knows about, including the ones still pending — and the
residual disappears rather than being narrowed.

The reason to write this down before WP-07 is built rather than after: the payload's shape is decided
once, and a fan-out that carries only a login will be extended later by whoever discovers this section
a third time. This union has already been declared complete twice.

---

## D38 — Erasure fans out, on a key this estate already shares

Built 2026-09-02, from D37's answer to WP-07 and D36's design note about the payload.

### The defect, which is not that erasure was hard

A complete erasure has always been three calls — booking for the visit address and the notes,
messaging for the message bodies and the bell menus, catalog for the review authorship and the saved
list. D24 recorded plainly that there was no orchestrator and that calling one without the others
leaves a partially erased customer. Recording it is not the same as preventing it, and the reason
this particular gap is worse than an ordinary missing feature is that **each of the three receipts
looks exactly like a complete erasure**. They carry a pseudonym and plausible non-zero counts, they
return 200, and there is no artefact anywhere in the estate whose absence would tell anybody that the
third call was never made. An operator who erased two services out of three and filed the receipt
would have done everything the system asked of them and would be wrong.

That is the same shape as every erasure defect this repository has found: the estate is green, the
endpoint does what it says, and the only thing wrong is a number nobody would think to question.

### Why it could not be built until now, and the premise that was wrong

D28 states it in as many words: there is no service-to-service authentication in this estate, every
service only validates tokens, and booking holds none of its own. That is why catalog's
`/internal/professionals/{ref}/login` is protected by a gateway route predicate rather than by a
credential, and why D28's "rejected" list has a service-to-service token on it as correct in
principle and not worth inventing for one field.

The question put to the architect proposed sequencing the erasure from the `hc-admin` desk, on the
stated grounds that the admin console already holds a staff token all three services accept.
**That was false, and D37 says so.** The platform-wide signing secret in
`~/webroot/01-healthconnect/.env` is shared by hc-admin, hc-patient and hc-professional — that
sharing is the entire mechanism behind cross-stack routing between those three — and hc-market is not
in that set. It carries its own `JWT_BASE64_SECRET`, generated per estate, persisted at
`quality/.jwt-secret` on the quality box and required independently by all three of its compose
files. An hc-admin token presented here fails signature validation. The recommendation was built on a
misreading of the workspace's own key arrangement, which is worth leaving written down: the fix was
not to build the recommended thing more carefully, it was to notice that it could not have worked.

What hc-market does have is a key its own five services already share with each other. So one of them
mints a token and the others accept it exactly as they accept a user's.

### Booking orchestrates, and it is not an arbitrary choice

`POST /api/desk/customers/{login}/erase-everywhere` lives in booking because booking is the only
service that holds the fact the other two need. D36 established that messaging cannot find a
notification about a booking it has no thread for, and that booking holds the authoritative list of a
customer's bookings with `booking.customer_login` indexed for exactly that question since D34. Putting
the orchestrator anywhere else would have meant asking booking for the list and then passing it on,
which is the same call with an extra hop in it. Booking also already reaches catalog over
`HEALTHCONNECT_CATALOG_BASE_URL`, so two of the three legs were already addressable.

Booking erases itself first, in its own transaction, and then makes the two HTTP calls outside it.
First because that leg cannot fail for a network reason and because it holds the home address, so a
fan-out that dies half way has already removed the worst of it; outside the transaction because
holding a database transaction open across two remote services would trade this problem for a worse
one.

### `ROLE_CUSTOMER_ERASURE`, named for what it permits

D37 was explicit that the minted token must carry a narrow, named authority used by nothing else, and
the reasoning behind that instruction is worth restating because it is easy to misread as security
theatre. Any service holding the estate key can already mint a token for any subject with any
authority, `ROLE_BROKERAGE` included. That was true before this was built and it is true after; a
shared symmetric key has always been an estate-wide capability rather than a per-service one. What
changes when a fan-out is built is not the blast radius, it is whether that capability becomes an
*interface*. A fan-out that routinely presented `ROLE_BROKERAGE` would turn "a compromised service
could mint anything" into "every service is expected to mint anything", and the difference between
those two sentences is most of what a security review is about.

So the authority is `ROLE_CUSTOMER_ERASURE`, and it is named for the permission rather than for the
mechanism. "Fan-out" would have described how the call arrives; whoever next meets the constant on an
endpoint needs to know what it lets through. It appears on exactly one endpoint per service — the
erasure desk in messaging and in catalog — and on nothing else. In particular it does **not** appear
on booking's own erasure endpoints: the authority permits being a *leg*, booking is never one, and
granting it there would let a fan-out token trigger a fan-out.

Three narrowings, and each is enforced by the side that receives the token rather than promised by the
side that issues it, because a promise made by the minting service is worth exactly as much as the
minting service:

**One named customer.** The token carries an `erasure_subject` claim, and a caller holding only the
fan-out authority may erase that login and no other. Without it the authority would mean "erase
anybody", which is `ROLE_BROKERAGE` with a different name and no audit trail. With it, a copy of a
token taken off the wire buys an erasure of the person it was already being used to erase.

**Thirty seconds.** The receiving side compares `iat` against `exp` rather than trusting the issuer to
have been careful. This stops nothing an attacker would do — anyone holding the key mints their own —
and that is not what it is for. It stops the ordinary decay: a later caller, another service, or a
rewritten minter reusing a user token's twenty-four hours for a fan-out, which would be accepted
estate-wide for a day and would look exactly like the real thing.

**A subject that is not a person.** `sub` is `system:erasure-fanout` rather than the operator's login.
This one is easy to get wrong in the direction that feels more helpful: putting the operator's login
in the subject would improve the audit trail in the downstream logs, and would also make a leaked
fan-out token a bearer credential for a real person on every `/api/**` path in the estate that merely
asks to be authenticated — which is most of them. The audit trail lives in booking's log instead,
where the orchestration actually happened, and the token is worth nothing anywhere it is presented
except the one endpoint that reads its claims.

`ErasureFanoutToken` states that contract in a file copied **byte-identically into all three
services**, the same treatment `SubjectPseudonym` gets and for the same reason: there is no shared
library here, and a claim name that drifts by one character between the minting service and the
accepting one turns every fan-out into a 403 that reads as a permissions problem in the service doing
the refusing rather than as a typo in the service doing the asking. CI now diffs it alongside the
alias derivation. Booking's copy uses only the constants and its check method is unreachable there,
which is deliberate — the three files can then be compared as bytes rather than as behaviour.

### Partial failure: report, do not retry, do not refuse

Three options, and only one of them is honest.

**Refusing** — rolling back booking's own erasure when a remote leg fails, so that the whole thing is
all-or-nothing — sounds like the safe answer and is not. It delays a redaction the data subject has
already asked for on the strength of an outage somewhere else, and it cannot deliver what it promises
anyway: if catalog is the leg that fails, messaging has already erased and there is no un-erasing it.

**Retrying** in process would make the endpoint's latency unbounded and would hide a genuine outage
behind a slow success, which is the failure shape this repository keeps finding by other means.

**Reporting** leaves the decision with the person who already owns it. So every leg is attempted
whatever the earlier ones did — refusing to try catalog because messaging was unreachable would leave
*more* of the customer's data in place, not less — and the receipt names each service, its status and
its counts. A leg that failed reports no counts at all rather than zeroes, because a zero and an
unknown must not read the same on the sheet that gets filed against a data subject request.

**The status code is 200 only when every leg erased, and 502 with the same receipt otherwise.** 207
Multi-Status describes the situation more precisely and was rejected on a failure-mode argument rather
than a semantic one. The whole defect being fixed is that a partial erasure looks like a success, so
the cost of the two mistakes is not symmetric: a caller that reads only the status and mis-reads a 502
retries an idempotent operation, and a caller that mis-reads a 207 files a partial erasure as a
complete one. The operator's instruction on a 502 is to call it again, and to escalate if the same leg
fails twice.

The failure messages carry the root cause's type, and that detail came out of getting it wrong first.
The client originally distinguished "could not be reached" from "answered something unreadable" by
catching `ResourceAccessException` for the first — which is wrong, because a **read timeout**, the
single most likely real failure here, comes back from the message converter as a plain
`RestClientException`. The confident branch would have told an operator that messaging had answered
when it had not said a word. The two are now one branch with the root cause's class name in the
message, so `SocketTimeoutException`, `ConnectException` and a Jackson exception are distinguishable
without this service pretending to a classification it gets wrong.

### Idempotent, and the part of that which is not obvious

Erasure requests arrive by email and get forwarded, so they get retried; after a 502 the operator is
told to call again. The second run reports zeroes from every service and that is the honest answer.

> **Corrected by D39 — this was false when it was written.** Messaging reported
> `notificationsRedacted: 2` on every subsequent fan-out, indefinitely, because those rows are matched
> by `deep_link` and the count was of rows re-written rather than of rows that still held anything.
> The sentence above is true of booking and catalog, was true of three of messaging's four counts, and
> was wrong about the fourth — the one an operator retrying after a 502 would read as "data was still
> exposed at the moment of the retry". It is true as written now. See D39.

The part worth stating is where the booking references come from. They are read back **under the
alias, after the local erasure**, not collected from the rows on the way past. Collecting them during
the sweep reads better and breaks the retry: a second run finds nothing under the original login, so
the list would be empty, and the retry that exists precisely because messaging failed the first time
would call messaging with no references at all — quietly reopening D36's residual on the one path most
likely to hit it, with a receipt reporting a clean second pass. Reading by alias returns the same list
every time.

### D36's residual: closed on one path, and now bounded rather than vague

D36 ends with a design note asking that the fan-out carry the customer's booking references, on the
grounds that the payload's shape is decided once and a fan-out carrying only a login would be extended
later by whoever rediscovered the section a third time. It does carry them, and messaging folds
`/bookings/<ref>` for each into the same link set it already builds from the customer's conversations
and the customer's own notifications.

**But the residual did not disappear; it acquired a boundary, and that is a better outcome than the
one D36 predicted.** D36 expected `ErasureResourceIT.theResidualIsOneRowForAPendingBooking` to go red
the day messaging was handed the references. It did not, because the references arrive in a *payload*
and a direct desk call sends none — so `POST .../erase` still cannot reach a notification about a
booking it has no thread for, and `POST .../erase-everywhere` can. The residual is now exactly the gap
between those two endpoints, which is a far more useful thing to know than "one row somewhere", and
the two tests are only meaningful as a pair: the old one pins the desk path, and
`theResidualIsClosedWhenTheFanOutSuppliesTheReferences` pins the fan-out path with the same fixture.
The second was confirmed red before the change with `notificationsRedacted expected:<2> but was:<1>`,
which is byte for byte the signature D36 recorded when it found the defect in the first place.

The references are supplied by a caller, so it is worth being precise about what they can do. They
only ever cause a notification body to be replaced — never a row to be read back, never a row to be
created, never a login disclosed — so the worst a wrong reference achieves is blanking a message that
should have kept its text. That is why the authority carrying them is scoped to one named customer:
not because a disclosure is possible, but because a redaction of somebody else's row would be.

### A defect found by building this, which nothing else could have found

D35 requires `HEALTHCONNECT_PRIVACY_PEPPER` to be identical in booking, catalog and messaging, and
injects it three times from three compose entries. Nothing verified it. If the three ever diverge,
all three services keep working perfectly, every erasure succeeds, every receipt is plausible — and
one person acquires three aliases whose rows can never be reconciled again, with no way back, because
a pseudonym does not invert. It is the same class of silent divergence D35's own CI check was written
to prevent for the derivation, with the value left unguarded.

The fan-out is the first thing in this estate that ever sees two services' aliases for one person in
the same place, so it compares them. A leg that erased under a different alias is reported as
`ALIAS_MISMATCH` rather than folded into `FAILED`, because the two need different things from an
operator: a failure wants a retry, and a mismatch wants the deployment corrected and then every row
already written under the wrong alias reconciled by hand. The counts are kept on a mismatched leg,
since the rows really were redacted.

### Deployment, and the variable that has to be in three places

Booking now reaches messaging, so `HEALTHCONNECT_MESSAGING_BASE_URL` is set in
`docker-compose.dev.yml`, `docker-compose.prod.yml` and `quality/compose.yml`, and CI's
cross-service base URL check has been widened to demand it in all three. That check exists because
`HEALTHCONNECT_CATALOG_BASE_URL` was unset in every environment for a week while `POST /api/bookings`
503'd, in silence, with nothing red. This one would fail more loudly — an unset base URL makes every
fan-out report messaging as unreachable, in the receipt, which is the entire purpose of the receipt —
but the check costs nothing and the class of mistake is identical.

The quality entry uses the **container name** `hc-market-quality-messaging` rather than the short
service name its two neighbours use. `hcnet` is shared with three sibling products and hc-market's dev
stack, compose publishes a service name as a DNS alias on every network it joins, and a duplicate does
not error — Docker answers with whichever it likes. The two neighbouring lines predate that rule and
changing them is a separate change with its own risk; a new line has no reason to be written the risky
way.

### What this does not do, stated rather than left to be found

**No test in this repository proves the handshake across two services.** Booking's suite stands
messaging and catalog up as loopback stubs and reads the token off the wire, which proves what booking
sends; messaging's and catalog's suites mint the same token with their own encoder and prove what they
will accept. Those two halves meeting is a property of a shared secret and of five standalone Maven
projects that cannot be assembled in one test context. D28 recorded the same limitation and reached
the same conclusion, which is that the wire is checked against a running estate. **This has not yet
been run against the quality box**, and until it has, the claim that a real booking container can
reach a real messaging container with a token it minted is reasoned rather than measured.

**The receipt still evaporates.** WP-06 asks for a durable record of an erasure in booking and catalog,
and D36's design note suggested WP-07 might make it unnecessary. It does not — it makes the case
stronger. What used to evaporate was three HTTP responses; what evaporates now is one HTTP response
that is the only account of which legs ran, for an irreversible action with legal significance, in
precisely the case (a partial fan-out) where an operator most needs to be able to prove what happened.
The half of WP-06 that the fan-out did retire is its justification-by-residual; the durable record is
untouched and is now the whole of that package. **Built as D39.**

**Nothing here touches the gateway's user store.** An erased person can still log in and book again,
which is WP-08 and is unaffected by this in either direction.

---

## D39 — An erasure's record should be true, and should outlive the request

Built 2026-09-02, from WP-06 and two findings on the quality box. Three items in three places, and
they are one concern: **erasure is the feature where the receipt is the deliverable.** Everywhere else
in this estate a number on a response is a convenience; here it is the artefact an operator files
against a legal request, and it has now been wrong or ephemeral three separate times. Doing them as
one pass rather than a third patch is the point of this entry.

### The count that could not go down

D38 states, and `docs/backlog.md` restated, that a second `erase-everywhere` reports zeroes from every
service. **Messaging did not.** It answered `notificationsRedacted: 2` on the second call, and on the
third, and on every call after that, for ever.

Nothing was wrong with the data. Those bodies had held the placeholder since the first call. What was
wrong is the thing that gets filed: an operator retrying after a 502 — which is exactly what they are
told to do — reads a non-zero redaction count on the retry and reasonably concludes that personal data
was still exposed at the moment they retried. The receipt is the only account they have, and it was
telling them something that had not happened.

The cause is one line and it generalises into a rule worth keeping:

> **A counter keyed on the customer's login is self-clearing. A counter keyed on anything else has to
> compare before it counts.**

Conversations, messages and the customer's own notifications are found *by the login*, which the first
pass replaces, so a second pass matches nothing and reports zero without anyone having to be careful.
`notificationsRedacted` is different in kind: those rows sit in the **professional's** bell menu, are
keyed to somebody else, and are found through `deepLink` — which is not personal data and does not
change when a body is redacted. The same rows matched every time, were re-written with the placeholder
that was already in them, and were counted again. The workflow now skips a row whose body is already
the placeholder: no write, no count. It shows on the fan-out path rather than the desk path, because
the fan-out reads the booking references back under the alias and therefore still has a link set on a
retry (D38), where a desk call's link set is empty for the unrelated reason that nothing is keyed to
the original login any more.

Confirmed red first, as this repository requires: `JSON path "$.notificationsRedacted" expected:<0> but
was:<2>`, on a 200 OK whose body read
`{"pseudonym":"erased-…","conversationsPseudonymised":0,"messagesErased":0,"notificationsReKeyed":0,"notificationsRedacted":2}`
— every other count honestly zero beside the one that was not.

### And the same error in booking, which nobody had gone looking for

The instruction to check the other counters found a second instance, in booking's
`outboxPayloadsRedacted`. Rows there are matched by `aggregate_ref`, which is the **booking's**
reference and says nothing about whether the row ever named anybody — and the sweep then wrote
`customerName: "[erased]"` into every one of them unconditionally.

That is not hypothetical. `OutboxRecorder.record(String, Dispute, String)` keys a dispute event to the
booking, deliberately, so that a reversal cannot overtake the booking events it concerns; its payload
carries `disputeRef`, `bookingRef`, the money and the resolution, and **no customer fields at all**. So
a customer who raised one dispute got a receipt reading two where one row held anything of theirs, and
an event that was never defined to carry a customer name acquired one. Both halves are now conditional
on there being something to remove — `hasNonNull`, so an event without a name does not gain one, and
the actor re-key counts too. Red first: `JSON path "$.outboxPayloadsRedacted" expected:<1> but was:<2>`.

The audit of all nine counters across the three services, since the useful output of this is the table
rather than the two fixes:

| Service | Count | Matched on | Could over-count? |
|---|---|---|---|
| booking | `bookingsErased` | `customer_login` | No — self-clearing |
| booking | `outboxPayloadsRedacted` | `aggregate_ref` | **Yes, and did** — fixed |
| booking | `disputesRedacted` | `raised_by_login` | No — self-clearing |
| booking | `historyRowsReKeyed` | `actor` | No — self-clearing |
| catalog | `reviewsDeidentified` | `customer_login` | No — self-clearing |
| catalog | `favouritesDeleted` | `customer_login`, then deleted | No |
| messaging | `conversationsPseudonymised` | `customer_login` | No — self-clearing |
| messaging | `messagesErased` | the customer's conversations | No — reachable only under the login |
| messaging | `notificationsReKeyed` | `recipient_login` | No — self-clearing |
| messaging | `notificationsRedacted` | `deep_link` | **Yes, and did** — fixed |

Every one that can over-count is one that had to reach rows held *by somebody else about* the customer,
which is the same shape as the two hardest defects in D34 and D36. That is not a coincidence: the rows
this feature has most trouble finding are the rows it has most trouble counting, for the same reason —
nothing in them is keyed to the person.

### Catalog's receipt omitted the one thing it deletes

`catalog`'s workflow deletes the customer's favourites outright — deliberately, since a saved list is
purely personal and a tombstoned row would be an orphan (D24) — logs it, and reported only
`reviewsDeidentified`. On the quality box it deleted two favourites and said nothing about them. A
customer with no reviews and a saved list of twelve produced a receipt of zeroes, which an operator
files as "catalog held nothing for this person".

This is the defect D31 found in messaging, where a booking raises a thread before anybody writes in it
so an empty conversation was re-keyed and reported as nothing. It was fixed there, the lesson was
written down, and **nobody checked whether the other two services had the same shape**. They did.
Catalog's is the worse instance of it: the omitted count is of the only rows this feature *deletes*
anywhere in the estate. Red first: `No value at JSON path "$.favouritesDeleted"`, against a body
reading `{"pseudonym":"erased-…","reviewsDeidentified":1}`.

The general lesson is not about counting. It is that a defect found in one of three copy-pasted
services is a defect **reported against all three until each has been looked at**, and this repository
now has two instances of failing to do that — this one, and D38's discovery that nothing verified the
three services run the same pepper.

### The durable record — WP-06

Messaging has recorded erasures since D32 in `erased_subject`. Booking and catalog recorded one in a
log line and an HTTP response body that ends with the request. For an irreversible act with legal
significance that is thin, and WP-07 sharpened rather than retired the argument: what evaporates now is
a **single** `erase-everywhere` receipt that is the only account of which legs ran, and the case where
an operator most needs it is precisely the 502.

**Two tables, because there are two facts.** This is the whole of the design decision and it is worth
stating as such, because the obvious version — one row per person, updated on each attempt — is wrong
in a way this repository has already been bitten by.

- **`erased_subject`, one row per person, written once, in booking and in catalog.** "This service
  erased this person, at this time." A *local* fact: only the service that ran a sweep can attest to
  it, and a central register would say "booking believes catalog erased X" — which is a different
  claim, and is exactly the claim that turned out to be false the day catalog was never called.
  `erasedAt` never moves; D35 had to fix precisely that in messaging's copy, where `save()` on an
  existing key replaced the original timestamp with the date of the retry.
- **`erasure_run`, one row per fan-out attempt, in booking only.** "A fan-out was attempted, and here
  is what each leg said." This one cannot be distributed at all, and the reason is the argument for
  choosing booking rather than a per-service copy: **a leg that fails is a leg that cannot record its
  own failure.** The service that could not be reached writes nothing, by definition. The only place a
  partial outcome can exist is the orchestrator, and booking is already the orchestrator because it
  holds the booking references the other legs need (D38).

So the answer to "three per-service registers or one record of the fan-out" is **both, and they are not
alternatives** — they answer different questions with different lifecycles. Folding them together would
mean either a retry overwriting the first attempt (destroying the evidence of the partial run that is
the entire reason to keep any of this) or `erasedAt` moving. Hence a UUID key and no updates on
`erasure_run`, and a subject key with a write-once guard on `erased_subject`.

**Pseudonyms only, and it needed enforcing rather than merely intending.** The principle is copied from
`ErasedSubject`'s javadoc in messaging: a register of erased people *that names them* is precisely the
thing erasure was asked to remove, and unlike the rows being redacted it would have to be kept for
ever. Both new tables hold the alias and never a login.

That is easy to say and there was one route by which it would have been false. `erasure_run` stores the
receipt as it was rendered, and a failed leg's message carries the root cause — and the root cause of
an unreachable leg is an I/O error naming the URL it was thrown against,
`/api/desk/customers/<login>/erase`. Stored verbatim, the one row written specifically to be kept for
ever would have been the only row in the estate naming a person who asked to be forgotten, sitting in
the audit trail of their own erasure. The login is substituted for the alias before the row is written,
and a test asserts the stored receipt carries neither the login nor a fragment of it while still
carrying `SocketTimeoutException` — scrubbing the identity must not scrub the diagnosis. The HTTP
response is *not* scrubbed and does not need to be: the operator typed that login into the path, and
the response ends with the request.

**The receipt is stored as a blob rather than modelled into columns**, which is the same discipline
`ErasureFanoutClient` already applies: it copies each leg's counts through under the names that service
gave them, precisely so that a count added or removed downstream cannot be misreported by a record in
this service that still declares the old one. A column per count would undo that on the durable side,
and an audit asks for the answer that was given rather than for a reconstruction of it.

**A failure to record does not fail the erasure, and does not go quiet.** The redactions have already
happened and are not coming back, so throwing would replace a receipt naming three legs with a 500
naming none — reintroducing the invisibility this whole package exists to remove, from the other side.
The receipt carries `recorded` and `recordId` instead; `recorded: false` is a thing an operator can act
on before closing the tab, and the log line is an ERROR because nothing else about the response would
look wrong.

**Neither new register gates anything, and that is why neither needs a guard.** D35's
`ErasureRegisterGuard` exists in messaging because messaging *consults* its register: run it unpeppered
and `isErased` answers "no" about people it erased, so the next lagging `booking.requested` writes a
real login back. Booking and catalog only write, and an unpeppered service refuses to erase at all
(503), so no row in either can carry an alias its service cannot reproduce. The trap was worth naming
in the other direction too: **nothing was added to messaging's `erased_subject`.** Its emptiness is
load-bearing — it is what lets `isErased` answer `false` and `lockSubject` do nothing rather than
throwing and stalling the consumer — and D35 already had to move the pepper witness into its own table
for exactly that reason. A row for a fan-out attempt would have made `count()` non-zero for ever and
taken the allowance away with nothing saying so.

**This is also the substrate WP-08 will read**, which is why the shape matters more than a log line
would. WP-08 scopes the register by the **booking's** age rather than the event's, so that somebody who
books again after being erased is stored under their real login while everything that existed before
stays pseudonymised. That comparison needs `erasedAt` next to the service that holds the booking's
`raisedAt`, which is booking, which now has it. The day something *reads* one of these registers to
decide what to write, that service needs the guard messaging has — said in both javadocs, beside a
repository deliberately left with no query methods so that adding one is the moment somebody meets the
sentence.

### Two hazards added to the regeneration table

`booking/config/liquibase/master.xml` gains one include and `catalog/config/liquibase/master.xml`
gains one, and losing them fails in opposite ways. Booking's is **loud** — every `erase-everywhere`
500s on a missing `erasure_run` table. Catalog's is **silent**: erasure keeps working, the receipt still
reports what it redacted and deleted, and the estate simply stops keeping any record that an
irreversible act happened, in the one service that deletes rather than redacts. Both are in
`CLAUDE.md`'s table, which is the only reason any of the nine rows above it have survived three
regenerations.

### What this does not do

**None of it has been run against the quality box.** D38 ends on the same sentence about the fan-out
handshake and it is still true; this adds two tables and a scrub that no test in this repository can
prove behave the same against a real Postgres as against a Testcontainers one — the Liquibase
changelogs are exercised by every integration test, so the schema is proved, but a live erasure filing
a live receipt is not the same event.

**Nothing reads `erasure_run` yet.** There is no desk endpoint for "show me every attempt at erasing
this person"; the repository method exists and the index behind it exists, and an operator today gets
the row by asking the database. Adding the endpoint means deciding who may read an audit trail of
erasures, which is a `ROLE_BROKERAGE`-versus-something-narrower question of the same kind D38 answered
for the fan-out authority, and it was not answered here.

**The pre-D39 erasures on quality are not back-filled.** Same position D35 took: there is no way to
reconstruct a record of an act nobody kept a record of, and on quality it is test data.

---

## D40 — An erasure is not a permanent verdict on a person

**Built 2026-09-02. Backlog WP-08, and NEW-3 beside it.** D37 answered the question; this is what was
built against that answer and the one thing the answer had to be read carefully to get right.

### The estate disagreed with itself about whether somebody existed

Erasure never touched the gateway's user store — deliberately, D37: deactivating the account would
have made "erased" a tidier state at the cost of locking out somebody who has chosen to come back. So
an erased person can log in and book again, and until now messaging's `erased_subject` register was
**unconditional**. The consumer saw the login registered as erased and pseudonymised the new booking's
thread and the professional's bell row, while booking and catalog stored the real login on the same
booking. One booking, two names for the same customer, and nothing red anywhere.

**The register is now scoped: it applies to what existed when the erasure ran, and to nothing after
it.** A booking raised before `erasedAt` stays pseudonymised for ever; a booking raised after it is a
new relationship and is stored under the real login.

### The whole package is which clock decides that, and the obvious one is wrong

D37's first wording said to compare **the event's** timestamp against `erasedAt`. It says something
nobody intended. A booking that is still open at the moment of an erasure goes on emitting events
afterwards — accepted, completed, cancelled, reviewed — and every one of those is stamped after
`erasedAt`. Under an event-timestamp rule each would be written under the customer's real login and
real name, putting an erased person back into the professional's thread list and bell menu **one
lifecycle step at a time**, and silently breaking D36's guarantee that its residual does not grow.

So the comparison is against the **booking's** age. D37 offered two ways to get it and preferred the
explicit one; that is what was built.

**`bookingRaisedAt` on the outbox payload is `Booking.raisedAt`** — when the booking was *created*,
written once by `CustomerBookingResource` and never moved by any transition, which is exactly what
makes it a truthful proxy for "this booking existed before the erasure". It is a property of the
booking rather than of the message carrying it, so every event about one booking reports the same
instant however late in the lifecycle it is published. The envelope's `occurredAt` is the other fact
and keeps its own meaning; the two are now visibly different things in the same event rather than one
thing somebody could mistake for the other.

The alternative D37 allowed — "treat a `bookingRef` messaging already holds rows for as predating the
erasure" — needs no new field and infers the answer from the absence of a row, which is the same shape
as the reasoning that produced D36's hidden residual: messaging's rows are deduped by professional and
do not enumerate a customer's bookings. Stating the fact is cheaper than deducing it and does not
depend on a second invariant staying true.

### Absent means covered, and that is load-bearing

A null or unparseable `bookingRaisedAt` answers exactly as the unconditional check it replaced. Events
published before the field existed are still in outboxes and on the broker, and an event that cannot
say how old its booking is must not be the thing that decides an identifier is safe to store. The
failure directions are not symmetric: pseudonymising a booking that need not have been costs one
thread its customer's name, while failing to pseudonymise writes a real login into a row nothing will
ever revisit. `BookingEventConsumerIT.aLateEventCannotResurrectAnErasedCustomer` — D32's test, which
sends no such field — is now the pin for this as well.

### It made no new reader of a register, which is the question D39 attached to this package

D39 left booking's and catalog's `erased_subject` write-only and said the first thing WP-08 must do is
decide whether the service it turns into a reader needs messaging's `ErasureRegisterGuard`, because an
unpeppered service consulting a register answers "not erased" about people it erased. **The reader is
still messaging, and messaging has had that guard since D35.** Booking only publishes a column it
already stores; it reads nothing. So the two new registers stay write-only and stay unguarded, and the
condition D39 attached is discharged rather than deferred.

### NEW-3 — a privacy test that could not fail, and the leak it was hiding

`ErasureFanout.record` scrubs the login out of a receipt before storing it, because a failed leg's
message carries the URL it was thrown against and that URL contains the login. The reasoning was right
and the substitution was there. **`theRecordNeverNamesThePerson` passed with the scrub deleted
outright** — confirmed twice, once when the defect was reported and once here.

It drove the failure with a **read timeout**, whose message is `Error while extracting response for
type [java.util.Map<…>] and content type [application/octet-stream]` — no URL, therefore no login,
therefore nothing to scrub and nothing to catch. It was green because the leak is absent on that path,
not because the scrub closed it. A privacy test that cannot fail is worse than no test: it is filed as
evidence.

It now drives a **refused connection** — messaging's stub is stopped for that one test and rebound
afterwards — which arrives as a `ResourceAccessException` reading `I/O error on POST request for
"http://…/api/desk/customers/<login>/erase"`. With the scrub removed it fails, quoting the whole
receipt with `ama.tobeforgotten` sitting in it.

**And driving a real URL settled the hypothesis NEW-3 parked beside itself, which turned out to be a
real defect.** The scrub was `receipt.replace(login, alias)`, and the string it hunts is inside a URL:
the gateway's `LOGIN_REGEX` permits `? ^ ` { | }` and `@` in an email-shaped login, and `RestClient`
strictly encodes a URI variable, so `ama?forgot@example.com` reached the kept row as
`ama%3Fforgot%40example.com` while the substitution looked for the unencoded spelling and matched
nothing. Nobody could demonstrate it before, for the same reason the test above could not fail: with a
read timeout there is no URL in the message at all. One fixture fixed both.

The scrub now matches **every spelling of every character** — each character as itself or as its
percent-encoding, hex case-insensitively. Deliberately not "call the same encoder the client called":
`RestClient` and `UriUtils.encodePathSegment` disagree about `@` alone, and pinning a privacy control
to a library's encoding choice is a second thing to keep in step. What it does not handle is a login
needing JSON escaping — a quote or a backslash — which `LOGIN_REGEX` does not permit and which is
written down beside the code rather than left to be rediscovered.

### What this does not do

**Not run against the quality box.** Same sentence D38 and D39 end on. Two things here are worth a
live run in particular: an erased customer really booking again through the estate, and a receipt
filed from a leg that is genuinely down rather than one whose socket a test closed.

**Nothing back-fills.** A booking raised before an erasure that has already been consumed under the
old unconditional rule stays as it is, which is correct — the old rule and the new one agree about
every booking that predates the erasure, and that is all the rows there are.

**Catalog was not touched and did not need to be.** It runs no Kafka consumer, so it has no path by
which a stale event can write a login back; its `erased_subject` remains the write-only record D39
made it.
## D41 — The payment seam could not complete a lifecycle it had already started

Built 2026-09-03, from WP-10 and the payment review. D31 built the seam and left one defect in it that
was sharper than everything else in this repository: **`authorizePayment` read the outcome's state and
threw away `providerReference`.**

That handle is the first argument of `capture`, `refund` and `status` — every method on the port
except `authorize` — and there was no table holding it, no column, and no log line. So the day a real
provider first answered `AUTHORIZED`, the customer's money was committed and the platform held nothing
with which to capture it, give it back, or ask what had become of it. The seam was able to *start* a
payment lifecycle it had made itself unable to finish, and nothing anywhere would have gone red.

The same defect from the other side, which is why the two were fixed together: if `creator.create`
threw after a successful authorization, the customer was charged for a booking that does not exist,
and there was nothing to void the authorization with. One is the cause of the other.

### Where the handle is stored, and why it is not on `Booking`

Storing it at all needs stating against this repository's central rule. **Derived, never stored** is
about figures that have a source — a rating, an earning, a total — and it says: compute it, so that
the copy cannot drift from the thing it copies. A `providerReference` has no source here. It is issued
by somebody else and derivable from nothing the estate holds, so the choice is not "store or derive",
it is "store or lose". `Professional.verification` (D16) is the existing precedent for a stated
exception of this shape, and this is a second one.

**Where** was the real decision, and a column on `Booking` was the obvious candidate. It is wrong, and
the deciding argument is a sequence rather than a preference:

> **The authorization happens before the booking row is written.** D31 put it there deliberately, so
> that a third-party call is not inside the transaction that publishes `booking.requested` — a
> provider timing out must not roll back a booking the customer's screen had every reason to believe
> in. So at the instant the handle arrives **there is no booking row to put it on**, and the case
> where that matters most is exactly the case this package was opened for: the create fails, the money
> is committed, and a column on a row that was never written holds nothing.

Three more things a column could not hold, all of them already on the backlog:

| | A column on `Booking` | `payment_attempt` |
|---|---|---|
| Authorize now, capture part of it later | one handle, one state — the second movement overwrites the first | two rows, both readable |
| WP-13, customer chooses between three providers | a decline followed by a retry on another provider overwrites the abandoned handle — and an abandoned attempt that the first provider later confirms is precisely the one that must stay voidable | one row per attempt, none lost |
| WP-11, confirmation arrives by webhook naming only the provider's reference | no index on it; the estate cannot find out what the payment was for | `idx_payment_attempt_provider_reference` |

**What it costs.** A table, two indexes, and a join for anyone wanting a booking and its money in one
read. Nothing in the estate wants that today — no screen shows a payment — so the cost is paid later,
and the alternative would have had to be torn up by WP-11 in any case. The table also carries rows
whose booking does not exist, which is not a defect: it is the true account of money committed for a
booking that failed, and it is why there is no foreign key to `booking`.

**A row is written when there is a handle to write, and only then.** Today's estate is entirely
`OFF_PLATFORM` — the customer pays the professional directly — and recording every outcome would fill
the table with one contentless row per booking and make "is there money against this booking?" answer
yes for every booking in the estate. Every handle *is* kept whatever the state, including a decline's:
a provider that hands one back is telling us how to ask about that attempt later, and a declined
booking that turns out to have taken money is exactly when somebody needs to.

**And it holds no personal data**, deliberately. No login, no name, no contact detail a provider might
have needed for a mobile-money prompt; the only link to a person is `bookingReference`, which an
erasure keeps on purpose (D24/D31). So `ErasureWorkflow` does not sweep it — correct as long as the
columns stay as they are, which is why both the entity and the changelog say so where the next writer
will meet it. The one place a customer's details could have arrived unannounced is an error string, so
`attention_note` is composed by this platform from a provider name, a reference and an exception
class, and never copied from a provider's message. D39's stored receipt needed a scrub for exactly
that reason, by way of a URL rather than a message.

### `PaymentRecorder` is `REQUIRES_NEW`, and that is the load-bearing annotation

The exact mirror of `OutboxRecorder`, which is `MANDATORY` so an event can only be written in the same
transaction as the change it describes. A payment handle is the opposite kind of fact: **it exists to
survive the failure of the work that comes after it.** A row sharing the booking's transaction would
be rolled back precisely when the money was committed and the booking was not, which is the case the
table exists for.

### The rest of the package

**`voidAuthorization` on the port.** A distinct call at every real provider, and not a settlement-model
choice, so it belongs on the interface rather than being deferred with D15's open questions. Without
it the estate had no answer at all to "money committed, booking not created". `BookingPayments.release`
chooses by state — an authorization is voided, money already captured is refunded — because a void
against a settled payment is refused by every provider that distinguishes them. That choice is not a
settlement assumption: it is true of any provider with both calls, and a provider with only one
implements the other as an alias.

**A release that fails is flagged, not retried.** `needs_attention` is the column an operator queries.
A second automatic attempt against a provider that has just failed is how one stuck payment becomes
several, and reconciling against a provider's console is not an action this platform can take. The
state is left as the provider last reported it — overwriting it would lose the one fact the person
clearing it up needs — and the full cause goes to the log at ERROR, where a provider's message can say
whatever it likes.

**`refund` carries a currency now**, against the house rule that money is minor units *plus* an
explicit ISO code — and it was missing in the method where the omission is least recoverable, since a
refund in the wrong currency is a second wrong transaction rather than a rejected one. **`capture`
takes an amount and a currency** for the same reason and one more: a `capture(reference)` that could
only take the whole authorization made the estate's only route to a smaller charge a full capture
followed by a refund, which is two entries on a customer's statement and, at most providers, two fees.
The amount comes from the `payment_attempt` row rather than from the booking, because what may be
moved is bounded by what the provider agreed to.

**`PaymentState.holdsMoney()` is not `permitsBooking()`.** `OFF_PLATFORM` permits a booking and holds
nothing, and the release path has to ask the second question: the unconfigured provider throws on
`voidAuthorization` by design, so releasing an off-platform booking would have replaced whatever real
failure had just happened with an `IllegalStateException` about payments.

### The idempotency key: the comment was wrong, not the code

`PaymentIntent`'s javadoc described `bookingReference` as "the natural idempotency key … so a retried
authorization is recognisable as the same one". **The call site defeats that**, and knowingly:
`CustomerBookingResource.create` mints `"b-" + a fresh UUID` per request, so two submissions of one
wizard are two references, two intents and — once a provider exists — two charges.

Of the two ways to make code and comment agree, the comment was corrected. Making the promise true
needs a key chosen by whoever *intends* the booking — an `Idempotency-Key` header on
`POST /api/bookings`, threaded through — and that is a contract with the client rather than with the
provider. The payment seam is not the place to invent one unilaterally, and deriving a reference from
the request's contents instead would change what a booking reference *is* for the sake of one
downstream caller. What the key does buy is real and is written down where it was overstated: a
provider's own retry, a client library's retry and every later call about this payment all name the
same booking, so duplicates of *this* call can be collapsed and `payment_attempt` reconciles against
the provider's records without a second identifier to keep in step with the first. The gap is named in
the same javadoc rather than left for somebody to rediscover.

### What this deliberately does not do

**No `PENDING`/`REQUIRES_ACTION`, no next-action field, no webhook contract.** That is WP-11, and it is
the next package. This makes it easier rather than harder: a webhook needs a row to update and an index
to find it by, and both now exist — the second is unused today and was added for that reason.

**No zero-amount condition.** That is WP-12, and this package neither helps nor hinders it: a free
booking still asks the provider to authorize 0 pesewas, and the day a real provider is configured it
will still refuse.

**Nothing pays the professional**, and that is still the property that makes the seam survivable
(D15/D31). Nothing added here assumes when settlement happens or in how many hops.

**Not run against a live provider, because there is not one.** Every branch is exercised through a
substituted provider in `PaymentSeamIT`, which is the same limitation D31 recorded and the same reason:
a refusal path that has never run is a refusal path nobody knows the shape of, and the day a provider
is added is the wrong day to discover that a failed booking keeps the customer's money.
## D42 — WP-09 put to counsel, and the answers gated on the environment

`WP-09` had been blocked since it was written, on four questions no engineer here can answer:
retention periods, lawful basis, controller registration and data residency. All four were put to
counsel on 2026-09-03 and all four came back. This records the answers, how they are wired, and — for
two of them — what the answer rests on, because both carry a dependency that should be visible now
rather than discovered by a regulator.

**Where the figures came from, stated first because it changes how they should be read.** The three
retention periods were offered as a worked example inside the question, authored here rather than
proposed by counsel independently. Counsel selected the *model* and then ratified the numbers for use
today, which is what makes them attributable. They are not a considered legal opinion on how long a
booking must be kept, and the day counsel produces one, the environment is where it goes.

### Retention — three categories, read from the environment

**Answered: financial records on a statutory clock, operational data shorter, the care summary
shortest — and ratified for use today.**

```yaml
healthconnect:
  privacy:
    retention:
      financial-days: ${HC_RETENTION_FINANCIAL_DAYS:2190}    # bookings, ledger, disputes
      operational-days: ${HC_RETENTION_OPERATIONAL_DAYS:365}  # messages, notifications
      care-summary-days: ${HC_RETENTION_CARE_SUMMARY_DAYS:90} # conditions, allergies, medications
```

This replaces the single nullable `retentionDays` on `PrivacyProperties`. It is the only one of the
three models offered under which a sweep can run at all without destroying records the platform is
required to keep: a clock short enough for a message body is far too short for a ledger row, and one
long enough for the ledger holds health data for six years. The split makes financial rows survive an
operational sweep **by construction** rather than by a condition somebody has to remember to write.

**The old comment argued at length that a default would be "a legal position taken by whoever typed
it".** That argument was right and it has been *discharged*, not abandoned — a number that came from
counsel is not a developer inventing a claim about Ghanaian law. What survives from it is the shape:
the values are read from the environment so a deployment can be corrected without cutting a release,
with counsel's figures as the committed fallback so an unconfigured estate still runs the ratified
policy rather than none.

**One correction to the question as it was put.** It described `retentionDays` as "duplicated across
booking, catalog and messaging". It is not: `PrivacyProperties` and `PrivacyResource` exist **only in
booking**. The *pepper* is estate-wide and the retention policy is not, which is correct — booking
owns the bookings, the disputes and the ledger's counterpart, and a retention period is a statement
about records rather than about services.

**Nothing enforces any of it, and the risk of that being missed went UP with this change.** Three
populated categories look far more like a working retention regime than one unset integer ever did.
`GET /api/desk/privacy` therefore keeps reporting `enforced: false` beside the numbers, `PrivacyProperties`
logs the same caveat at every startup, and `PrivacyResourceIT` pins it with a test that should fail
and be changed deliberately the day a scheduler exists.

### Lawful basis — contract throughout, including the care summary

**Answered: contract performance, with the care summary treated as ordinary contract data rather than
special-category.**

The reasoning is that the customer volunteers conditions, allergies and medications to make a
*non-medical* booking work — a trainer needs to know about a knee, a nutritionist about an allergy —
and the scope note is a hard boundary: nobody on this platform may diagnose or prescribe.

**This is the answer that carries risk, and the record should say so plainly.** It was the option with
the lowest engineering cost, and it asserts something a regulator could disagree with: that
conditions, allergies and medications are not health data attracting extra protection merely because
the recipient is not a clinician. Counsel has taken that position and it is counsel's to take. What
follows from it:

- **No consent record is built**, and the schema has nowhere to put one. If the position is revised,
  the remedy is per-booking, revocable, explicitly-recorded consent — a schema change, an API change
  and a screen, not a configuration flag. It gets more expensive with every real care summary stored.
- **`care-summary-days` becomes load-bearing.** It is now the main thing limiting the exposure, so
  shortening it is safe and lengthening it changes what the position rests on. That is a question for
  counsel, not for whoever is editing the environment file, and it is written where the value lives.
- Nothing in the code changes today. This answer is a documentation deliverable — the privacy notice
  and the processing record — not an implementation one.

### Controller registration — registered, injected, never committed

**Answered: Jojo Addison Consultancy is registered with Ghana's Data Protection Commission.** So
registration does not gate launch.

**The number was not supplied with the answer**, and it is needed for the privacy notice and the
processing record. That gap is now a configuration one rather than a documentation one:
`HC_DPC_REGISTRATION` is read at startup, blank counts as absent, and there is **no fallback**. Unlike
the retention periods, a wrong value here is a false claim about a real organisation's relationship
with a regulator, and a plausible-looking placeholder is worse than absence because it stops anyone
asking. Absent, booking starts, logs a warning, and the desk reports `null` — reported as `null`
rather than as an empty string so "not configured" cannot be read as "registered with a number nobody
can see".

It lives in the same `secrets.env` as the two real secrets, for a different reason than they do: not
because publishing it would be dangerous, but because this repository is public and the number is not
ours to publish on the organisation's behalf. It stays unset on quality on purpose — quality's job is
to look like production, but it has no business asserting a real registration.

### Data residency — cross-border transfer permitted, safeguards to be written down

**Answered: the transfer conditions are met; document what is transferred, where, and under what
safeguards.**

Production stays on `webserver` (199.247.5.252). No infrastructure change, and specifically no
relocation and no split store — the third option offered, care summaries in Ghana and the rest abroad,
would have meant a second data store, a cross-border join and a new failure mode on every booking
read.

**Two things about scope.** The machine hosts **all six products**, not just hc-market: the same
customer data crosses the same border for `hc-patient` and `hc-professional`. A document written here
can only speak for hc-market, and somebody should decide whether the estate needs one transfer basis
or six. And this repository is public, so the document will be read — the same discipline already
applied to keeping every usable secret out of `application-dev.yml` and `application-prod.yml`.

### What WP-09 becomes

| Piece | State |
| --- | --- |
| Retention model, three categories | **Built here.** Environment-gated, counsel's figures as fallback |
| Retention *numbers* | Ratified for use today; authored here, not independently proposed |
| Lawful basis | **Answered.** No code. Privacy notice and processing record outstanding |
| Controller registration | **Answered and wired.** Awaiting the number itself |
| Data residency | **Answered.** No code. Transfer basis outstanding, scope across six products undecided |

The two coded judgements D37 ratified — the review body is not erased, `Dispute.resolution` is kept —
are untouched by this and stay as they are.

## D43 — A payment nobody can answer synchronously, and the booking that waits for it

Built 2026-09-03, from WP-11. D37 chose Paystack, Hubtel and MTN MoMo and made this package a
prerequisite of WP-13 rather than an improvement to it, because **none of the three can truthfully
answer `AUTHORIZED` or `DECLINED` from the synchronous `authorize` the seam had.** Paystack returns an
authorization URL the customer must visit; Hubtel and MoMo raise a prompt on the customer's phone and
confirm by webhook minutes later. A seam whose only answers were "yes" and "no" would therefore have
had to guess, and both guesses are bad in the same way: an optimistic one creates bookings for money
that never arrives, a pessimistic one refuses every booking in the estate.

The shape is common to all three, which is why it could be built without choosing one. Nothing here
names a provider.

### What was added to the seam

**`PaymentState.PENDING`**, and the two questions the enum answers are now written on each constant
rather than as `this == A || this == B` chains at the bottom of the file. That is not tidying. Those
chains gave a new value `false` for both questions **by omission**, so the answer to "may a booking
exist while its payment is pending?" would have been settled by whoever forgot rather than by whoever
decided. Adding a state is now a compile error until both are answered.

**`PaymentNextAction`** on the outcome — a `kind` and, for a redirect, a URL. Two shapes, because the
providers produce two: `VISIT_URL` for Paystack's checkout page, `AWAIT_DEVICE_PROMPT` for a prompt
that is already on the customer's phone and has nowhere to send them. A client switches on the kind
rather than on the URL being present, so "check your phone" is a case it renders rather than a link it
failed to find.

It is deliberately not a message from the provider. A client cannot act on prose, and a provider's own
words are where a customer's name or phone number arrives unannounced — the hazard D39 met by way of a
URL in a stored receipt and D41 met by way of an error string in `attention_note`. The URL *is*
validated, once, where it is built: it goes into a browser's address bar and it came from a third
party, so a `javascript:` or `data:` URL relayed from a compromised or spoofed response would be
script running in the customer's session one click after a screen that says "complete your payment".

**A pending outcome must carry a provider reference**, enforced by the constructor. This is D41's
defect arriving from the other end: the webhook finds a payment by the provider's handle and by
nothing else, so a pending payment with no handle is one whose confirmation can never be matched to a
booking — and the booking would sit waiting for ever while the customer's money went through
perfectly well. Nothing downstream could detect that.

**`PaymentProvider.readCallback`**, which is where a provider's signature scheme lives and is the only
thing that authenticates a callback. And **`BookingPayments.release` now releases a pending payment
too**: the test is `holdsMoney() || awaitingCustomer()`, because a booking abandoned while its payment
is pending leaves the customer looking at a prompt they can approve a minute later. That is money
taken for a booking that does not exist, confirmed by a webhook that will never find one — D41's
defect exactly, down the asynchronous path.

### May a booking exist while its payment is pending? **Yes, in a state nobody has been told about**

The question WP-11 flagged and D37 repeated. It was answered here rather than referred, because both
answers are defensible on the product and the decisive arguments turned out to be about *where the
data lives* rather than about what a customer should see.

**Both answers have to store the customer's intention somewhere durable.** That is the whole of it. A
pending payment can be confirmed after the customer has closed the browser, so "refuse the booking and
let the client re-submit once it clears" is not a third option — it loses a booking precisely when the
money succeeded, which is the worst outcome available. So the choice is between holding the *booking*
and holding the *request*.

Holding the request means a second table carrying `customerLogin`, `customerName`, `customerNote`,
`visitAddress`, `onBehalfOf` and `careSummaryShared` — a booking in everything but name, with none of
the machinery a booking has. It would need its own erasure sweep and its own counter on the receipt
(D34's defect was four tables that named a person and did not know it); its own expiry; and its own
reference minting. And it would reserve nothing: two customers could both be pending on the same slot,
both be charged, and one be refunded afterwards.

Holding the booking uses the one place this estate already stores an intention to book, with the
erasure sweep, the audit history, the state machine and the customer's own "my bookings" screen
already attached to it. So: **`BookingStatus.PENDING_PAYMENT`**, in front of the state machine rather
than in it, with two transitions out and none back in.

**The condition on that answer is that the professional is not told, and it is enforced twice.**

- **No `booking.requested` is published.** `BookingCreator.createAwaitingPayment` writes the row and
  its audit entry and publishes nothing; `BookingTransition.PaymentConfirmed` publishes it when the
  money is confirmed. This is the guard that matters, because the event is what reaches messaging, and
  no query of messaging's is under booking's control. The event is late rather than lost, and every
  consumer still sees exactly one of it.
- **Every professional-facing query already filters by status.** `/api/pro/requests` asks for
  `REQUESTED` and the schedule asks for `CONFIRMED`, so a pending booking reaches neither without a
  line being changed. `Accept.from()` does not include `PENDING_PAYMENT` either, so a professional who
  guessed a reference gets the ordinary 409 rather than a booking nobody paid for.

**As written that was wrong, and the review found it: on the day WP-11 shipped it was enforced
once.** JHipster's generated `BookingStatusChangeResource` was still mounted at
`/api/booking-status-changes` with no `@PreAuthorize`, and `/api/**` asks only for `.authenticated()`
— so any token in the estate read the whole status-change history, `PENDING_PAYMENT` entries and the
`actor` column included. The status filters above were a second mechanism only against a caller who
used the professional's own endpoints. **It is enforced twice now**, and the resource is deleted rather
than locked down; see the review section at the end of this decision for what it did and why deletion
was the answer.

**What is deliberately not built: expiry.** A pending booking that is never confirmed sits there. It
blocks nothing today — nothing in this estate reserves an availability slot when a booking is made,
which was checked rather than assumed — and there is no scheduler here to sweep it with. The day slots
are reserved, the reservation must ignore pending bookings older than the provider's own window,
computed at read time in the manner D17 describes, rather than acquiring a sweeper for one column.

**The abandoned path publishes nothing at all**, and is a transition of its own rather than a
`Cancel`. `Cancel` computes `lateCancellation`, so a booking abandoned inside the free window would
have acquired a 50% fee against a customer who paid nothing and whose booking no professional ever
saw. It is cancelled by `PLATFORM` because neither party chose it. And an event would be the first
thing anybody downstream ever heard about that booking — messaging would open a conversation to raise
a notification about something the professional was deliberately never told about.

### The webhook contract

**`POST /webhooks/payments/{provider}`.** The body is taken as text and passed on untouched: every
provider signs the bytes it sent, so a body parsed and re-serialised verifies against nothing, and the
symptom of getting that wrong is "every callback is rejected", which reads as a wrong secret.

**Authentication is the provider's signature and nothing else.** There is no token; a provider cannot
hold one. `PaymentProvider.readCallback` verifies by the provider's own scheme — Paystack signs with
HMAC-SHA512 under the secret key, the others have theirs — and returning an outcome is a statement
that this really is the provider speaking. **An unauthenticated caller gets 401 with no detail**: not
400 naming the missing header, not 403 confirming the provider exists, no message saying which part
was wrong. An endpoint that explains its refusals is an oracle for constructing one that is not
refused. Today every caller gets 401, because the only provider bean in the estate is the unconfigured
one and it refuses every callback by definition — which is correct for an estate that collects no
money, not a placeholder.

**What it does:** finds the `payment_attempt` by the provider's handle, records the verdict, and — if
the booking is still `PENDING_PAYMENT` — confirms it into `REQUESTED` or cancels it. Nothing else. It
does not create bookings, and it reads nothing the callback says about amounts, customers or prices:
the only things taken from it are the handle and the verdict. D22's rule is not weakened by a
callback, because a callback decides nothing about what a booking costs or whose it is.

**The same callback twice: 200, and nothing happens the second time.** All three providers retry until
they get a 2xx and send duplicates besides, so this is the ordinary case. **Idempotency is decided
from the booking's own state under a row lock**, not from a record of what has been seen. A
`processed_event` table — payout's mechanism for its Kafka consumer — is the wrong tool here: what must
not happen twice is the *transition*, not the callback. Two genuinely different callbacks about one
payment must both be applied; one callback re-sent after a partial failure must be applied once in
total. The booking's status answers both, and `findByReferenceForUpdate` is what stops two
simultaneous retries both reading `PENDING_PAYMENT` and both publishing `booking.requested`.

**409 was considered for the duplicate and rejected on cost of mis-reading**, the same way D38 rejected
207: a provider told to retry keeps retrying until it gives up and files the payment as undelivered.

**Two 404s, and they mean different things to us and the same thing to the provider.** A callback that
overtakes its own booking — authorizing happens before the booking row is written, deliberately
(D31/D41), so there is a window — is a genuine race, and the provider's retry resolves it. A handle
this service never issued gets the same answer so that a provider replaying against a rebuilt database
eventually stops. In both cases the attempt row still records what the provider said.

**Money confirmed after this platform released it is flagged for a person**, not retried. It is told
apart from the race with no new column: a row already carrying `VOIDED` or `REFUNDED` was released by
us, so a confirmation on top of it is money committed for a booking that does not exist.
`needs_attention` is the column an operator queries, exactly as in D41.

**`PaymentRecorder.confirmed` joins the caller's transaction**, which is the one place this package
inverts D41. Every other method there is `REQUIRES_NEW`, because a handle must survive the failure of
the work that follows it. A webhook is the opposite arrangement: the verdict and the transition it
justifies are one change, and a callback that fails half-way is retried by the provider, so there is
nothing to preserve independently.

### What keeps a public endpoint off the internet today

**The same thing that keeps catalog's `/internal/**` private — D28.** The gateway's four route
predicates match `/services/<service>/api/**`, and this path is not under `/api`, so no request from
outside is routed to it in any environment. That was checked against all three compose files rather
than assumed. It is deliberate for as long as there is no provider: an unauthenticated endpoint nobody
legitimate calls should not be reachable, whatever it does with what it arrives with.

**WP-13 makes it reachable, and it is two things rather than one.** A fifth route with
`Path=/services/healthconnectbooking/webhooks/**`, *and* a permit in the gateway's security for that
path — the generated gateway chain ends with `.pathMatchers("/services/**").authenticated()`, so the
route alone returns 401 before routing, exactly as it did for the public catalogue reads
`MarketplacePublicRouteConfiguration` exists to let through. A route without the permit is a webhook
that silently never arrives, which reads as a broken provider integration. The exact lines are in
`PaymentWebhookResource`'s javadoc, beside the code that would receive them.

`PaymentWebhookSecurityConfiguration` permits the POST inside booking and denies everything else under
the prefix, so the path cannot quietly acquire a readable endpoint. Like catalog's equivalent, it is a
new file: regeneration rewrites `SecurityConfiguration` and discards edits to it.

### No schema change, and that is D41 paying off

`payment_attempt` already holds the state as a name, already has `idx_payment_attempt_provider_reference`
for a lookup nothing performed until now, and already tolerates rows whose booking does not exist. D41
built the index for this package and said so. The only migration-shaped change is a new
`BookingStatus` value, which is a `varchar` with no check constraint — and it is in `jdl/booking.jdl`
as well as in the generated enum, or the next regeneration would delete it.

**The next-action URL is not stored.** It is short-lived, it is handed to the client in the response,
and it is a bearer capability to complete somebody's payment — putting one in the table that was
designed to hold no personal data would be a new hazard for the sake of a "resume payment" feature
nobody has asked for.

### Tested, and what the tests cannot reach

Twenty-one new tests — eight unit and thirteen integration. The enum's three answers for `PENDING`,
the unconfigured provider's refusal, the two constructor guards, the two next-action shapes and the
state machine's one-way door; then, against the endpoints, the pending
create that publishes nothing, the professional's inbox before and after confirmation, the duplicate,
the decline, both 404s, the money-after-release, and the three refusals. Every one was confirmed red
against a deliberate mutation of the implementation before being kept, and the mutations were reverted
and the revert proved with a full `clean verify` — the discipline D41 broke when
`recorder.record(...) // RED-FIRST` shipped as the implementation.

**Not run against a live provider, because there is not one**, and not run against the quality box.
Every branch here is exercised through a substituted provider, which is D31's limitation unchanged and
D41's for the same reason: the day a provider is wired is the wrong day to discover what a refusal path
looks like.

### The review of WP-11, and the eight things it changed

Reviewed 2026-09-03, nine findings, eight of them real. Each was reproduced before it was fixed — the
first four against the running estate or a red test, the rest against a deliberate mutation — because
this repository has already paid for a plausible-sounding hypothesis fixed without one.

**The audit trail was an unauthenticated CRUD endpoint, and that is the finding that mattered.**
`/api/booking-status-changes` was still the resource JHipster generates: no `@PreAuthorize`, `/api/**`
asking only for `.authenticated()`, and therefore 200 with **292 rows** to a plain `ROLE_USER` on the
quality estate. The read leak is narrower than it looks — the nested `booking` serialises with only
`id`, so `customerLogin`, `visitAddress`, `customerNote`, `priceMinor` and `status` all come back null
— but the history itself and the `actor` column do not, and `actor` holds real logins beside erasure
aliases. **The write half is the serious one**: `POST`, `PUT`, `PATCH` and `DELETE` were open to the
same token, which was confirmed by creating a forged row (201) and editing another (200) from a test
holding nothing but `ROLE_USER`. That contradicts D34 and D39, which file this history as the
append-only evidence of what happened to a booking, and `BookingTransition`'s own claim that
`BookingWorkflow.apply` is the only thing that writes one.

It is **deleted**, not locked down, which is what this repository already did to `BookingResource`,
`DisputeResource` and `DisputeStatusChangeResource`. Nothing read it — checked across the Java, the
prototype, the deploy scripts and the verification scripts before deleting — and the history is already
served, scoped to the caller's own booking, by `CustomerBookingResource.one`. A `ROLE_BROKERAGE`
read-only version was the alternative and buys nothing today: no screen asks for the estate's whole
status history, and an endpoint with no caller is an endpoint whose authorization nobody exercises.
The two files are in CLAUDE.md's regeneration delete table, and a new `AuditTrailIsNotAnApiIT` — a new
file, so regeneration leaves it alone — fails if either comes back.

**A failed release looks exactly like an untouched payment, by state.** `PaymentConfirmations`
distinguished "money arrived after we released it" from "the callback overtook its booking" by reading
`VOIDED` or `REFUNDED` off the attempt row. But D41's rule is that a release which *fails* is flagged
and the state is deliberately left as the provider last reported it — so the worst case in the estate,
money committed for a booking that does not exist *and* a cancellation the provider refused, carried
`PENDING` and was filed as a benign race at INFO. `needs_attention` is now part of the same question,
read from a value captured before anything writes. And the mirror defect beside it: a released row
whose callback holds no money was having its `VOIDED` overwritten with the `FAILED` that followed,
destroying this platform's own record of the release — the one fact whoever reconciles it needs. It is
left alone now.

**Taking the newest attempt for a reused handle was a guess, not a match.** The lookup returns a list
because `PaymentRecorder.record` says two attempts against one booking may legitimately carry one
provider reference. Picking `get(0)` off a newest-first ordering is right whenever the newer row is the
same payment and wrong whenever it is not: with the newer row belonging to a booking that was never
written, a confirmation for a customer who is actually waiting answers the provider 404 and leaves the
booking in `PENDING_PAYMENT` for ever, every retry landing on the same wrong row. The reviewer could
not demonstrate a provider that reuses handles and said so; the code-level defect was demonstrated by a
test and is fixed by **matching on the booking that is waiting** — at most one per handle can be, since
the transitions out of `PENDING_PAYMENT` are one-way — with recency kept as the fallback.

**The flat 401 was the adapter's manners rather than the endpoint's guarantee.** Only
`PaymentCallbackRefused` was caught, so a malformed body that made an adapter throw `NullPointerException`,
`JsonProcessingException` or `IllegalArgumentException` escaped as 500 — and two of those are reachable
from constructors written in this very package. A forged malformed body got 500 and a forged
well-formed one got 401, which is a two-valued oracle telling a prober which of their attempts is
structurally closer to one this service would accept. Every failure to establish the provider is one
answer now.

**A pending booking is a dead end for the customer, and that is a decision.** `Cancel.from()` does not
include `PENDING_PAYMENT`, so the customer's cancel is a 409, and it stays that way: at the moment they
press it the provider is holding a live authorization — a page open, or a prompt on their phone they
can approve a minute later — and a cancel that moved the booking without cancelling the payment is
D41's defect exactly. Releasing it first needs a provider that can be asked, which is WP-13. It is not
a *permanent* dead end: the provider's callback ends it either way, confirming into `REQUESTED` or
abandoning into `CANCELLED`. What was a plain defect is that **`cancellation-preview` had no status
guard at all**, and answered `lateCancellation: true` with the full `priceMinor` for a booking inside
the free window whose money had never moved — a fee quoted to a customer who has paid nothing, for an
action the endpoint next door refuses. The preview now asks the same transition `/cancel` asks and
answers the same 409.

**`PENDING` could be built with no next action.** The constructor enforced the handle — what the
platform needs to finish the payment — and said nothing about what the *customer* needs. An outcome
with `PaymentNextAction.none()` renders as "nothing to do" against a payment that completes only when
somebody visits a page or approves a prompt. Both halves are now enforced in the same place.

**And the test whose job was to break when a state was added did not break.**
`permitsBookingIsExhaustive` hand-listed seven constants; there are eight, and `PENDING`'s answer is
the substance of this whole decision. Confirmed by adding a ninth state and watching it pass. It is
driven from `values()` now, with the expectation table asserted to cover the enum exactly, so a new
state fails the first line naming itself — the same argument that moved `permitsBooking` and
`holdsMoney` on to the constants, one layer out.

**The ninth finding did not reproduce, and the code was left alone.** The webhook takes
`@RequestBody String` while `PaymentCallback` promises the bytes as received, and the concern was a
lossy decode that would read like a wrong secret. Checked rather than assumed: the
`StringHttpMessageConverter` in this application's context reports `defaultCharset=UTF-8`, and Spring
special-cases a JSON content type to UTF-8 whatever the default — so a charset-less body decodes
faithfully, verified with two-, three- and four-byte characters through the real endpoint. It stays a
`String`. A provider that signed something other than UTF-8 would break it, and none of the three D37
chose does.

**Two javadoc claims were also corrected**, both true of the gateway and neither true of a host.
`PaymentWebhookResource` said "no request from outside can be routed here in any environment" and
`PaymentWebhookSecurityConfiguration` said "nothing routes here from outside today": D28's property is
about the four route predicates, and `docker-compose.dev.yml` publishes booking's own port on every
interface. Quality publishes it on loopback and production not at all. This is the same property
catalog's `/internal/**` has always had, so it is not a regression — but the route predicates are a
control against the internet, not against the machine the container runs on.

## D44 — A booking that costs nothing was going to be refused by a provider asked to take nothing

Built 2026-09-03, from WP-12. Three things in the payment seam, and the first is the package's reason
to exist.

### The free booking, which no test would have noticed

`BookingPayments.take` asked the provider to authorize whatever the booking cost, unconditionally.
**Two of the eighteen seeded professionals offer a service at `priceMinor: 0`** — recorded in
`CLAUDE.md` as correct rather than a bug, because "from ₵0" is what the catalogue says and a free
introductory session is a real offering. All three providers D37 chose refuse an authorization for
zero: Paystack has a minimum charge, and neither Hubtel nor MTN MoMo raises a prompt for nothing.

**That last claim is from the providers' published behaviour and not from a live account, because
there is none.** It does not carry the decision on its own. Even a provider that politely accepted an
amount of zero would be one HTTP round trip to a third party, per free booking, about money nobody
owes — and a `payment_attempt` row asserting that somebody is holding a fact about it.

So **every free booking in the estate becomes uncreatable the day a provider is configured**, and
until that day nothing goes red. The unconfigured provider answers `OFF_PLATFORM` to any amount at
all, including zero, so the whole test suite passes and the defect is invisible until it lands on
customers. That is the same shape as D41's dropped handle — a path this estate cannot exercise
because it has no provider, failing on the day it acquires one.

**The condition is in `take`, not at the call site.** `CustomerBookingResource` could have skipped the
call, and that is where a guard like this usually ends up. It is the wrong place for the same reason
`PaymentRecorder.record` holds "no handle, no row" rather than trusting its callers: an invariant
about money should be held by the one method everything goes through. WP-13 adds a second call site
for the customer's provider choice, and a guard in the resource would have to be remembered there.

**Zero exactly, not `<= 0`.** A negative price is a defect in whatever priced it, and treating it as
free would be this service quietly deciding that the platform owes the customer money. So it does not
take the free path; it goes past this guard and fails further along, loudly, rather than becoming a
free session nobody questioned.

**Where it fails was overstated, and the review corrected it.** This used to say the negative amount
"goes to the provider and is refused — a 402 or a 502 somebody has to explain". That is what will
happen once a provider is configured; it is not what happens today. `UnconfiguredPaymentProvider`
answers `OFF_PLATFORM` to −500 exactly as it does to 15000, so the booking proceeds and dies at
`bookings.save` on `Booking.priceMinor`'s `@Min(0)` — a `ConstraintViolationException`, and a 500. That
is loud, which is the property the decision wanted, and it is unreachable anyway while catalog's own
`@Min(0)` on `ServiceOffering.priceMinor` holds. The decision stands unchanged; only its account of
the failure was written for an estate that does not exist yet, which is the same thing this whole
package keeps having to be careful about.

### What state the booking is created in, and it is not `PENDING_PAYMENT`

**`REQUESTED`, and `booking.requested` is published**, exactly as for a priced booking whose payment
is off-platform. D43 built `PENDING_PAYMENT` for a booking whose money is on its way, and the whole
mechanism is that a webhook ends the wait. **Nothing will ever confirm a payment that was never
started**, so a free booking in `PENDING_PAYMENT` would sit there for ever, unseen by the
professional, in a state D43 deliberately gave no expiry sweep. The customer would have made a
booking nobody was told about, and no timeout to discover it.

The professional is therefore told about a free booking immediately, which is also the product
answer: there is nothing to wait for.

**No `payment_attempt` row**, and that follows from D41 rather than being a new decision: a row is
written only when a handle comes back, and no provider was asked, so no handle exists. The table's
account stays true — a row means somebody else is holding a fact about this booking's money.

### `PaymentState.NOTHING_TO_PAY`, and why it is not `OFF_PLATFORM`

The cheap implementation reuses `OFF_PLATFORM`: it permits a booking, holds no money, and writes no
row, so every mechanical consequence is already right. It was rejected on what the estate would then
be unable to say.

**`OFF_PLATFORM` is a claim about who paid whom** — the customer paid the professional directly,
which has always been true here and which D31 exists to state rather than assume. Nobody pays anybody
for a free session, so filing one as off-platform is a false statement by the platform about its own
booking. And it costs something concrete later: `OFF_PLATFORM` should **stop being produced** the day
a provider is configured, and that is the signal that the estate has entered the money's path. Free
bookings wearing it would answer "is any money in this estate settled off the platform?" yes for
ever.

`NOTHING_TO_PAY` is **the one value in the enum no provider reports.** `PaymentState`'s own admission
test is that no value may be specific to one provider, and this passes it in a stronger form than its
neighbours: it is not that every provider has a notion of it, it is that the decision is identical
whichever provider is configured, because it is taken before any of them is reached. The javadoc says
so, since the constant otherwise looks like a value somebody could return from an adapter.

Adding it cost two red tests, and **both were the tests D43 built to go red for exactly this.**
`permitsBookingIsExhaustive` failed on its `containsExactlyInAnyOrder` line before comparing a single
answer, and `holdsMoneyIsNotPermitsBooking` failed on `values()).hasSize(8)`. That is one package's
distance between a guard being written and the guard firing, which is about as good a demonstration
as they were going to get. Both answers were then written down deliberately: it permits a booking, it
holds no money, and it is not awaiting the customer.

### A provider that throws is a provider that failed

`PaymentState.FAILED` and its 502 have existed since D15. **There was no route to them from an
exception**, so an adapter whose HTTP client timed out produced a 500 with a stack trace, while a
provider that politely answered `FAILED` produced a 502 and a client that retries. Two answers to one
situation, and the unhandled one is the shape every real adapter will actually take — a
`RestClientException`, a `JsonProcessingException`, a null dereference in somebody else's response
body. It is also the wrong answer twice over: a 500 says this platform is broken when a third party
is, and it is the one response a client is entitled to read as "do not try that again".

`take` now catches `RuntimeException` around `provider.authorize` and answers `FAILED`. Three notes on
the shape:

- **The reason is composed, never copied.** It names the provider and the exception's class and
  nothing else, because it is rendered into a response body and a payment provider's own words are
  where a phone number or a cardholder's name arrives unannounced — the hazard D41 met by way of
  `attention_note`, D43 by way of the next action and D39 by way of a URL in a stored receipt. The
  whole exception goes to the log at ERROR, where it can say whatever it likes.
- **`FAILED`, not `DECLINED`.** The customer's instrument said nothing; a decline is a business answer
  and sends the client to another card. The distinction was already drawn in the resource and this
  simply lands on the correct side of it.
- **Only the provider call is wrapped.** A `PaymentRecorder` that throws is this platform failing to
  keep the one fact it cannot reconstruct, with the money possibly committed — that stays a 500, and
  loudly. There is a test for it, because the tidier `try` around the whole method would have
  swallowed it.

**The first of those was half true, and the review found the other half.** "Composed, never copied"
was a property of the *thrown* path this section adds, and not of the *answered* path beside it, which
is the common one: `PaymentOutcome.declined(reason)` and `failed(reason)` take an adapter-authored
string, and `CustomerBookingResource.authorizePayment` relayed it verbatim into a
`ResponseStatusException` — from where `ExceptionTranslator` renders it as the ProblemDetail's
`detail`. Composing at the boundary too is in the review section below.

The catch is deliberately not narrowed to a payment-specific exception type. There is none on the
port and there should not be: an adapter is somebody else's code, and a seam that only behaves when a
third party wraps its failures correctly is a seam that does not behave.

### `@ConditionalOnMissingBean` in a user `@Configuration` is order-sensitive — a warning, not a fix

`PaymentConfiguration` supplies the fallback provider under `@ConditionalOnMissingBean`. **That
annotation is only reliable in an auto-configuration**, which this is not. What that costs, however,
was written down backwards, and the review caught it — the paragraph that follows is the corrected
version. What it replaced said the condition might or might not see a component-scanned
`PaymentProvider` depending on the order the scanner reached the two classes in, and told the reader
to use an explicit `@Bean` in a sibling `@Configuration` instead. **That is the wrong way round in
both halves**: the scanned shape is the reliable one and the recommended shape is the one that
collides. Advice that causes the failure it warns about is worse than no advice, and this one sat in a
javadoc, in this decision and in `CLAUDE.md`.

**A component-scanned `PaymentProvider` is always visible to the condition.** Read from the Spring
7.0.8 sources on the classpath, not from memory: `ConfigurationClassParser.doProcessConfigurationClass`
calls `componentScanParser.parse(...)`, whose `ClassPathBeanDefinitionScanner.doScan`
**registers every scanned definition** — `registerBeanDefinition` at the point it adds each holder —
before returning the set the parser then recurses over. A `@Bean` method's condition is evaluated
later still, at `REGISTER_BEAN` phase in
`ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForBeanMethod`, which
`ConfigurationClassPostProcessor` does not reach until `parser.parse(candidates)` has returned. The
scan is therefore complete before the first `@Bean` condition fires, and the fallback backs off every
time.

**The genuinely order-sensitive shape is the one the old text recommended.** An explicit
`@Bean PaymentProvider` in a sibling `@Configuration` is registered in configuration-class parse
order; for two component-scanned classes that is the order `getResources` returned them in, which
nothing sorts — `ClassPathScanningCandidateComponentProvider` has no comparator — so it is the
filesystem's business. `PaymentConfiguration` parsed first: the condition sees nothing, the fallback is
registered, the sibling then registers the real provider unconditionally, and every injection point
has two candidates and a `NoUniqueBeanDefinitionException`. Parsed second: it sees the real one and
steps aside. Identical code, two outcomes, and it can differ between a laptop and CI.

**What is built is the corrected warning**, on the class, saying that a real provider added before
WP-13 should be a `@Component` — or carry `@Primary`, which settles the ambiguity whichever way the
parse order fell and is therefore order-independent in any shape. The thing to avoid is a bare `@Bean`
beside `PaymentConfiguration`. WP-13 replaces the condition outright with a registry keyed by provider
name, which is the shape three providers need anyway, so building an order-independent mechanism now
would be building it twice.

**Both halves are now asserted rather than described.** `PaymentConfigurationUnitTest` builds two
`AnnotationConfigApplicationContext`s: one where a provider definition is already in the registry
before `PaymentConfiguration` is registered, which yields exactly one `PaymentProvider` bean; and the
sibling-`@Bean` pair in both orders, which yields two beans and a `NoUniqueBeanDefinitionException` one
way and one bean the other. Registration order stands in for parse order there, and that substitution
is sound: `ConfigurationClassPostProcessor` sorts candidates only by `@Order` and neither class
declares one, so a stable sort leaves them as registered.

**The cheap fix, recorded rather than taken:** move the class to `@AutoConfiguration` and list it in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Auto-configurations
arrive through a deferred import selector, processed after every user configuration class, which is
precisely the ordering the annotation assumes, and `@SpringBootApplication`'s
`AutoConfigurationExcludeFilter` keeps the class from also being component-scanned —
`HealthconnectBookingApp` carries the plain annotation, so that filter is in place, which was checked
rather than assumed. Two small changes, and **not built, so not measured**: WP-13 deletes the condition
it would be protecting, so one of the two would be throwaway work. If WP-13 slips and a bare `@Bean`
provider is wired before it, this is the fix to reach for.

### Tested, and what is not

Seven new tests — five unit in a new `BookingPaymentsUnitTest`, two integration in `PaymentSeamIT` —
plus the two D43 guard tests updated with the new state's answers.

Three were confirmed red against the current code before the fix:

- the free booking at the endpoint, with the provider stubbed to do what a real one does with zero.
  **`Status expected:<201> but was:<402>`** — the defect itself, a free booking refused because a
  provider was asked to take nothing. The kept assertion is `verify(payments, never()).authorize(any())`,
  because a guard that asked and then ignored the answer would still be a round trip to a third party
  per free booking and would still be refused;
- the same thing at the seam, red on `verifyNoInteractions(provider)`;
- the throwing adapter, red twice: `Status expected:<502> but was:<500>` at the endpoint and an escaped
  `IllegalStateException` at the seam.

`NOTHING_TO_PAY` being the state cannot be red before the constant exists, so it is asserted beside
the interaction check rather than instead of it — the arrangement D43 used for `pendingIsThreeAnswers`.

The two that assert decisions rather than fixes are the "a priced booking is still authorized" pair —
the guard is a condition, not a switch — and "a recorder that throws is not dressed up as a provider
failure", which pins the narrowness of the `catch`. booking finishes at **114 unit + 101 IT**, green on
a full `clean verify`.

**Not run against a live provider, because there is not one, and not run against the quality box.**
This is the third package in a row to say so and the sentence is doing more work each time: the whole
zero-amount defect exists because the estate's only provider answers the same thing to every question.

### The review of WP-12, and the four things it changed

Reviewed 2026-09-03, all four findings real and none of them in the zero-amount guard itself. Every one
was reproduced against a red test before it was fixed, and the theme they share is the one this section
is named for: **three of the four were documents asserting a property the code did not have.** WP-12
wrote its javadoc, this decision and `CLAUDE.md` in the same pass as the code, and the writing ran
slightly ahead of what was built.

**The provider's own words still reached the response body.** The one that matters. `authorizePayment`
relayed `taken.outcome().reason()` verbatim into a `ResponseStatusException`, and
`ExceptionTranslator.getProblemDetailWithCause` → `customizeProblem` → `getCustomizedErrorDetails` →
`err.getMessage()` renders that as the ProblemDetail's `detail`. So D44's "composed, never copied", the
matching javadoc on `BookingPayments.authorize` and the new `CLAUDE.md` bullet were **true of the
thrown path this package added and false of the answered path beside it** — which is the common one,
since `PaymentOutcome.declined(reason)` and `failed(reason)` both take an adapter-authored string. The
failure is one line of WP-13: a Paystack adapter writing `declined(response.path("message").asText())`,
Paystack answering `"Declined — card ending 4242, Ama Mensah, 0244123456"`, and that landing in a 402
body and in every client that logs a ProblemDetail. Nothing downstream saves it —
`getCustomizedErrorDetails` redacts only package names and `DataAccessException`, and only under
`prod`.

Confirmed red at the endpoint with exactly that string, and the assertion failure is the whole finding:
`"{"detail":"402 PAYMENT_REQUIRED \"Declined — card ending 4242, Ama Mensah, 0244123456\"", …}" not to
contain "Ama Mensah"`. The refusal message is now **composed at the boundary too**, from the state
alone — "the payment was declined" for a 402, "the payment provider could not be asked, or answered
with an error" for a 502 — and `outcome.reason()` goes to the log at WARN, which is where whoever has
to explain a refusal was always going to look. It says the same thing the status code says, which is
all a customer can act on anyway: the client's next move is another instrument or another attempt, and
no provider's prose changes which. The `switch` is exhaustive with no `default`, so a tenth
`PaymentState` is a compile error there rather than a state that silently inherits somebody else's
sentence.

**Nothing stopped an adapter answering `NOTHING_TO_PAY`.** `PaymentState`'s javadoc calls it "the one
value in this enum that is not a provider's answer" and `PaymentOutcome.nothingToPay()` is a public
static factory on the record every adapter constructs — so, again, the documentation was the whole of
the guarantee. `PENDING` got two compact-constructor invariants for this exact class of defect on the
reasoning that a state which lies is worse than no state; this got a sentence.

What it admits is the quietest failure in the seam: a ₵150.00 booking, an adapter mapping an
unrecognised Hubtel status onto `nothingToPay()`. `permitsBooking()` is true and the state is not
`PENDING`, so the booking is created in `REQUESTED`, `booking.requested` is published, the professional
is told, no `payment_attempt` row exists because no handle came back, **no money moved, and nothing
anywhere in the estate disagrees with anything.** Red at the endpoint as `Status expected:<502> but
was:<201>` — a booking made for money nobody took.

`take` already knows `intent.amountMinor() != 0` on that branch, so the refusal is structural there:
the outcome cannot hold the check because it does not know the amount, and `take` is the one method
that does — the same reason the zero-amount guard is there rather than at the call site. **The chosen
failure is `FAILED`, not a thrown exception and not `DECLINED`.** A provider answering something this
platform cannot use is precisely what `FAILED` means, and it lands on the 502 that this package's own
`catch` exists to produce; throwing would have reintroduced the 500 one branch along, in the same
commit that removed it. `DECLINED` would be a lie about whose fault it is and would send the customer
to another card for a problem no card has. The handle is carried across if one came with the outcome,
because D41's "every handle is stored, whatever the state" has no exceptions and
`PaymentOutcome.failed(String)` alone would have dropped the reference.

**`provider.name()` was called unwrapped, including twice inside the new catch block.** An adapter
whose `name()` throws — a lazily-read merchant id, a null region code — re-threw straight out of the
handler and landed back on the 500 that handler was written to remove. Red as an escaped
`IllegalStateException: no merchant id configured` from `BookingPayments.authorize`. It goes through a
wrapped `providerName()` now, at all six sites rather than the three the review named: the success
path is one, where the money is committed by then and a name nobody can produce would have cost the
handle D41 exists to keep; and `release` is three more, which is worse again, because `release` runs
from the resource's `catch` and a throw there would replace the exception that failed the booking with
one about a provider's name — on the single path where money is committed and the booking does not
exist. Null and blank are treated as a throw is, because `payment_attempt.provider` is not-null and the
row is worth more than the name.

**And the `@ConditionalOnMissingBean` warning had the mechanism backwards**, which is written up in
that section above rather than here, because the corrected text belongs where the wrong text was. The
short version: the scanned shape is reliable and the shape the warning recommended is the one that
collides, so the advice caused the failure it warned about. Verified against the Spring 7.0.8 sources
on the classpath and then pinned with two context tests.

Two smaller documentation corrections, both marked in place above: D44's claim that a **negative**
price "goes to the provider and is refused — a 402 or a 502" describes an estate with a provider in it
and not today's, where it reaches `bookings.save` and fails `@Min(0)` as a 500; and
`nextActionFor`'s javadoc promised that the state name travels beside the action so a client can tell
"waiting on you" from "nothing to pay", when only `PENDING` produces a `PaymentAction` and that field
is therefore always `"PENDING"`. Keeping `NOTHING_TO_PAY` off the wire is right — a free booking is a
`REQUESTED` booking like any other and the state is the platform's account of why no provider was
asked — so the comment was corrected to say so rather than the code changed to match it.

**Two flagged assertions were deliberately left alone.** The review noted `attempts…isEmpty()` and
`jsonPath("$.payment").doesNotExist()` in the WP-12 ITs as redundant rather than wrong —
`verifyNoInteractions(recorder)` and `verify(payments, never()).authorize(any())` in the unit tests do
the real work — and redundant belt-and-braces in a payments test is not worth a commit to remove.

Seven new tests, four unit and three integration, **all seven confirmed red against the current code
before anything was changed**, plus two more in `PaymentConfigurationUnitTest` that pin the corrected
ordering claim and were green on arrival — which is the point of them, since what they contradict was a
javadoc rather than the code. booking finishes at **120 unit + 104 IT**, green on a full `clean verify`.

Still not run against a live provider and still not run against the quality box, for the fourth package
running. The whole of this review is about the seam that only a real provider will ever exercise, so the
sentence is worth repeating rather than shortening.

## D45 — Three providers a customer chooses between, and three adapters that have never spoken to one

Built 2026-09-04, from WP-13. The largest of the payment packages and the one whose honest answer is
partly a refusal: everything around the providers is built and verified, and the providers themselves
are not, because this repository could not reach them.

### What the registry replaced, and why the condition had to go

`PaymentConfiguration` supplied one provider under `@ConditionalOnMissingBean`, so "adding a real
provider is one bean and no edit". D37 chose **three** — Paystack, Hubtel and MTN MoMo, with the
customer choosing between them — and one-bean-wins cannot express that at all: two providers under
that annotation is a `NoUniqueBeanDefinitionException` at best and a silently arbitrary winner at
worst. D44 documented the annotation's order-sensitivity rather than fixing it, explicitly because
this is the package that deletes it. It is deleted, and **the warning went with it** rather than being
left as advice about a mechanism that is gone.

`PaymentProviders` is the replacement: every `PaymentProvider` bean, resolved by the name it answers
to. Four properties of it are load-bearing.

**Nothing injects a `PaymentProvider` by type any more.** `BookingPayments` and
`PaymentWebhookResource` both take the registry. That is what makes two providers two entries rather
than a collision, and it is why D44's whole ordering hazard evaporates instead of being worked
around: `PaymentConfigurationUnitTest` now asserts a sibling `@Bean` provider coexisting with the
fallback **in both registration orders**, which is the exact pair the deleted test used to
demonstrate the opposite of.

**The fallback is injected by bean name, not found by its `name()`.** `@Qualifier` on
`PaymentProviders.FALLBACK_BEAN`, so this class holds no opinion about what an absent provider calls
itself and a real adapter cannot make itself the fallback by claiming to be `none`.

**The fallback is never a choice.** `choices()` excludes it by identity. Otherwise "pay through no
provider" is a payment method a client can ask for: booking created, `booking.requested` published,
the professional told, and no provider ever asked for the money. It stays reachable by
`named()`, so a callback addressed to it is refused *by its own adapter* — a 401 like every other
refusal, rather than a 404 arranged by hiding it from a lookup.

**Nothing asks `name()` eagerly.** An index built in the constructor would ask every adapter to name
itself before the context has finished starting, and D44 found what trusting `name()` costs. Four
providers is a list to walk; `nameOf` never throws and never returns null, and an adapter that cannot
name itself is excluded from `choices()` rather than offered — no callback could ever be routed back
to it, so a booking made through it would wait for a confirmation with nowhere to arrive.

### The customer's choice, under D22's rule

`CreateBooking.paymentProvider` is the one client-supplied field on that request that decides **who
ends up holding the customer's money**, which is exactly what D22 exists to be suspicious of. Unlike
the price and the professional's login, it genuinely is the client's to say — nothing on the server
knows which wallet a customer wants to pay from — so what the server keeps is the right to refuse.

Three cases, resolved in `PaymentProviders.chosen`:

- **nothing configured** — today's estate. `choices()` is empty, the fallback answers, and a request
  that names nobody behaves exactly as it did before this package. A request that *names* somebody is
  refused: the caller believes this estate collects money and it does not, and a 201 would create a
  booking whose customer thinks they have paid;
- **one provider** — it is the default. Requiring a name for a decision with one possible answer
  would refuse every booking made by a client written before the choice existed;
- **more than one, and no name** — refused. The platform must not pick who takes the customer's
  money; picking the first would make the answer depend on bean registration order, which is the
  property the registry was built to remove.

**409 for a name that is not offered, 400 for a choice that was needed and absent.** The client's
next move differs — re-read the offer and ask again, versus ask the customer — which is the same
distinction 402 and 502 draw one layer down, and the 409 matches what D22 already answers when a
client's figures disagree with the catalogue. A name that was never valid and one that has just
stopped being valid are indistinguishable from here, so the answer that is right in both cases wins.

**The refusal names the offer and never the request.** `offered()` is this service's own
configuration and is safe to say out loud; the requested name is a stranger's string on its way to a
response body, which is the route D44 closed for a provider's own prose.

**The resolution happens after the zero-amount guard, and that ordering is a decision.** A free
booking asks nobody, so it must not be refused for failing to name anybody: resolving first would
make every one of the estate's free bookings a 400 the day a second provider was configured — D44's
defect returning by a different road. Both halves are pinned, at the seam and at the endpoint.

**`Taken` carries the provider that answered.** Before there were three, `release` used the single
injected provider and was correct by accident. A `release` that resolved the provider again would ask
whichever adapter a fresh choice landed on to void an authorization it never issued — on the one path
where money is committed and the booking does not exist.

**No endpoint publishes the list, deliberately.** A `GET /api/payments/providers` was considered and
not built: no screen asks for it — the prototype has no payment step at all — and an endpoint with no
caller is an endpoint whose authorization nobody exercises, which is the argument that kept a
`ROLE_BROKERAGE` read of the status history out in D43's review. The gap it would fill is real and is
closed the cheap way instead: the 400 that demands a choice lists what there is to choose from, so a
client always has a route to the names. The day a provider is configured, the choice screen and its
list arrive together.

### The webhook is reachable now, and that is two lines per environment

D43 recorded that nothing routes to `POST /webhooks/payments/{provider}` in any environment — the
gateway's four predicates match `/services/<service>/api/**` and this path is not under `/api` — and
that WP-13 would need **two** changes rather than one. Both are made, in all three compose files and
in the gateway:

- a fifth route, `Path=/services/healthconnectbooking/webhooks/**`, with `StripPrefix=2`;
- `PaymentWebhookRouteConfiguration` in the gateway, permitting **POST** on that path and nothing
  else. The generated chain ends with `.pathMatchers("/services/**").authenticated()` and
  authenticates *before* routing, so the route alone is a webhook that returns 401 at the edge:
  nothing reaches booking, nothing appears in booking's log, and the provider retries until it files
  the payment as undelivered. That reads as a broken provider integration rather than as a missing
  line, which is why the pair is written down in four places and asserted in three — the review below
  added the third, and it is the only one of them that observes the running container.

**D28's property is narrowed by exactly one path and not weakened.** Catalog's `/internal/**` is
still unroutable; the dangerous near-miss is `/services/healthconnectbooking/**`, one segment shorter
and enough to publish every booking, dispute and erasure endpoint booking has to anonymous callers.
CI's route check used to demand that *every* predicate end in `/api/**` and that there be exactly
four; it now allows exactly one webhook route, matched in full, and a second check greps the
gateway's permit for the same string and for `HttpMethod.POST`. A route without its permit fails CI
rather than a provider.

A third check, added by the review, pins the webhook route's **target and prefix** as well as its
predicate: the greps above read predicates only, so a `ROUTES_4_URI` pointing at catalog and a
`StripPrefix=1` both passed. The comparison is relative — the webhook route must point wherever
booking's own `/api` route points — because the three compose files name booking three different ways.

What now stands in front of that endpoint is the provider's signature and nothing else, which is what
`readCallback` was always for. Today every caller still gets 401 — from two places rather than one: a
name nothing is configured for resolves to no adapter at all, and every adapter that does resolve
refuses.

### The three adapters, and the line this package would not cross

**WP-13 had no network access, no provider account and no credentials.** Paystack's, Hubtel's and MTN
MoMo's documentation could not be read and none of the three could be called. So the adapters are
**seams with their provider-specific halves missing**: a name, their settings, a documented list of
what each still needs, and a refusal in place of every call that would have to know how the provider
actually speaks.

The alternative was to write them from memory and plausibility, and it was rejected on the same
ground this repository has already paid for twice. **Code like that compiles and passes the mocks
written to match it**, because the only thing it is ever checked against is the assumption that
produced it — D9's suite that was skipped for a week while everything compiled, and D14's green
publish that put no image in the registry. And the place it would have been wrong is the worst one
available: an adapter runs on the path where a customer's money is already committed, so a wrong
signature check accepts a forgery or rejects every genuine callback, and a wrong status mapping
creates bookings for money that never arrived or cancels bookings whose money did.

**How they fail is not uniform, and the asymmetry is the same one `UnconfiguredPaymentProvider`
draws.** `authorize`, `capture`, `refund`, `voidAuthorization` and `status` throw
`UnsupportedOperationException`, which `BookingPayments` turns into `FAILED` — a 502 and no booking,
with the whole exception at ERROR. `readCallback` throws `PaymentCallbackRefused`, which is the flat
401: that method is reached by whoever posts to a public endpoint rather than by this platform's own
code, and a 500 with a stack trace per probe tells a stranger their request got further in than it
did.

**Each is registered only when its own `enabled` property is true, and none is true anywhere in this
repository.** Turning one on today makes every priced booking that names it answer 502, and
`PaymentProviderProperties` says exactly that at WARN, once per enabled provider, at startup. They are
beans at all — rather than classes nothing can produce — because the wiring between a name, a choice,
a route and a callback is precisely what this package could verify, and a bean no configuration can
create is wiring nobody has run.

`@ConditionalOnProperty` rather than `@ConditionalOnMissingBean`, and the difference is the whole of
D44's hazard: it reads the `Environment` and never the bean registry, so no parse order can change its
answer.

**What each adapter still needs is written where whoever has the credentials will be looking** — on
the class, with the shared questions in `net.jojoaddison.service.payment.provider`'s `package-info`.
The shape is the same for all three: the authorization call, its response and **which field is the
durable handle**, the status vocabulary and its mapping (with anything unrecognised mapping to
`FAILED`), the callback payload and where the handle appears in it, **the signature algorithm and
exactly what bytes it covers**, and which credentials the outbound call needs as against the single
`secret` modelled for callbacks. Two per-provider questions do not generalise and are recorded
separately: Hubtel's and MoMo's prompts need a mobile number that `PaymentIntent` deliberately does
not carry, and MoMo's authentication appears to be a multi-step arrangement whose token is state this
seam has nowhere to keep.

### The third estate-wide secret

`healthconnect.payments.<provider>.secret` is what a provider signs its callbacks with, and it is
handled exactly as `JWT_BASE64_SECRET` and `HC_PRIVACY_PEPPER` are: injected by all three compose
files, **never committed**, because this repository is public. One difference follows from what it is
for — the other two are required always, this one only where its provider is turned on, so it is
optional and blank counts as absent, as `PrivacyProperties.controllerRegistration` treats blank.

**Absent means callbacks are refused, never trusted.** That is D35's rule for the pepper applied to a
provider's key. An enabled provider with no secret is still registered and still offered, and refuses
every callback with the same flat 401 an unimplemented one gives — identical from outside, because an
endpoint that distinguishes its refusals is an oracle, and different in the log, because "configure
the secret" and "write the integration" are two jobs for two people. Dropping it from the registry
instead was rejected: it presents as "the provider we configured is not offered", with nothing
anywhere saying why.

No deploy script changed. `deploy-dev.sh` already sources `deploy/.env` with `set -a`, and
`deploy-prod.sh` already passes `secrets.env` with `--env-file`, so a value put in either reaches the
container. They are deliberately **not** added to `deploy-prod.sh`'s required-secret list: an estate
with no provider configured needs none, and making them required would fail every production deploy
for a value nothing reads.

### Act 987 is not answered, and nothing here pre-empts it

Whether a split-settlement model clears Ghana's Payment Systems and Services Act is a question for a
person with the standing to answer it. WP-13 did not answer it and could not.

What this package protects is the property that makes the answer cheap either way:
**`PaymentProvider` still has no method that pays the professional.** D15 recorded that omission as
the single most important thing about the interface — split-at-capture and reconcile-afterwards are
the same seam from here — and the temptation to add a settlement or transfer call while writing three
adapter classes is exactly how it would have been decided by accident, in code. The package
documentation says so where an implementer will read it.

### Tested, and what the tests still cannot reach

**Thirty-nine new tests, two deleted, thirty-seven net** — booking +27 unit and +7 integration, the
gateway +3. The headline said thirty-one when the enumeration below already added to thirty-nine and
the tree moved by thirty-seven; the review counted it and this is the corrected figure. Eleven in a
new `PaymentProvidersUnitTest`, ten in a new `ProviderAwaitingIntegrationUnitTest` (one case and
three parameterised over the three adapters), four in `BookingPaymentsUnitTest`, four in
`PaymentConfigurationUnitTest` **replacing** the two that pinned the deleted condition — which is
where the two-test gap between thirty-nine and thirty-seven is — six in a new
`PaymentProviderChoiceIT`, the first test in this repository with **two** providers configured, one
in `PaymentSeamIT`, and three in a new gateway `PaymentWebhookRouteConfigurationTest`.

Confirmed red against a deliberate mutation before being kept, and the mutations are the plausible
wrong implementations rather than arbitrary breakage:

- `release` resolving the provider again instead of carrying it: `Wanted but not invoked
  paymentProvider.voidAuthorization`, the money going back to a provider that never took it;
- the choice resolved before the zero-amount guard: `PaymentChoiceRefused: this booking has to name a
  payment provider` on the free booking, at the seam and as a 500 at the endpoint;
- the platform picking a provider and ignoring the client's name: three ITs red at once —
  `expected:<201> but was:<500>`, `expected:<409> but was:<500>`, `expected:<400> but was:<500>`;
- `named()` falling through to the first configured adapter on a miss: `anUnknownNameResolvesToNothing`
  red at the unit level, and `aCallbackForNobodyIsRefusedUnread` red as `expected:<401> but was:<500>`
  — a callback for a provider this estate does not run reaching a real adapter;
- `choices()` including the fallback: "pay through no provider" becoming a choice, three tests red;
- the gateway permit widened to `/services/healthconnectbooking/**`: the whole of booking's API
  claimed by an anonymous chain, red on the one assertion that names it.

**Still not run against a live provider, for the fifth package running, and still not run against the
quality box.** This time the sentence is not a limitation of the tests but the subject of the package:
what WP-13 delivers is everything that can be true without a provider, and the three classes that
cannot are marked as such rather than dressed up.

### The review of WP-13, and the five things it changed

Reviewed 2026-09-04. Both builds were green and the honesty constraint held — no invented wire format
anywhere in the code, and Paystack's prose survived because it is labelled unverified. Five findings,
all real, and the first of them turned out to be worse than the review thought.

**The gateway permit was defended by nothing that observes Spring.** CI grepped two literals out of
the source file and `PaymentWebhookRouteConfigurationTest` built the chain with
`new PaymentWebhookRouteConfiguration()`. Neither asks the container for the bean, so removing
`@Configuration`, `@Bean` or `@Order` left every check green while every provider callback was 401 at
the edge. Measured, all three: CI green, three unit tests green, and the callback refused.

`PaymentWebhookRoutePermitIT` is the half that asks the running context — the chain list, the declared
precedence, and an anonymous POST through `WebTestClient` with an anonymous POST at
`/services/healthconnectbooking/api/bookings` beside it as the control, because "not 401" would also
pass on a gateway that authenticates nothing.

**And `@Order` was already inert.** The review's model was that deleting it would reorder the chains
to `[PUBLIC, GENERATED, WEBHOOK]`. It does not: measured both ways, the order is unchanged. Two
unordered beans tie at `LOWEST_PRECEDENCE` and a stable sort leaves them in registration order, which
for these is component-scan order — `MarketplacePublic…`, `PaymentWebhook…`, `SecurityConfiguration`,
alphabetically. Probing further showed why the annotation could not have been doing the work:
`findAnnotationOnBean(name, Order.class)` answered `null` for **all three** of the gateway's chains.
Spring offers the comparator the factory *method* and the bean *type* as order sources, never the
declaring configuration class, so an `@Order` on a `@Configuration` class holding `@Bean` factories is
read by nobody.

So the webhook was open because P sorts before S. **Renaming the class would have closed it, silently**
— the generated `springSecurityFilterChain` has a *negated* `securityMatcher` claiming everything
except `/app`, `/i18n`, `/content` and `/swagger-ui`, so it claims the callback path too and would
have authenticated it first. `@Order` moved onto the `@Bean` method in both
`PaymentWebhookRouteConfiguration` and `MarketplacePublicRouteConfiguration` — the public chain had
the identical inert annotation and the identical accidental ordering — where the value is read and
`HIGHEST_PRECEDENCE + 11` is *strictly* ahead of an unordered chain rather than tied with it. The IT
asserts the strictness, so the annotation is now load-bearing and its removal is red.

**`named()` could shadow an adapter, inverting the fallback guarantee.** No adapter can *become* the
fallback — that is injected by bean name — but the hazard runs in the mirror direction, and the
javadoc claimed the guarantee without it. `choices()` excludes the fallback **by identity**, so an
adapter calling itself `none` is offered; `named()` returns the **first** match, and the fallback is
declared first. `chosen("none")` therefore passed the check and resolved to `UnconfiguredPaymentProvider`:
`OFF_PLATFORM`, a booking in `REQUESTED`, `booking.requested` published, the professional told, and
nobody ever asked for the money. The same shape hits any two adapters sharing a name — one silently
unreachable by a booking and unaddressable by its own callbacks.

Unreachable today, since the three names are fixed constants. `refuseAmbiguousNames` refuses both at
startup anyway, because a name collision is a programming error rather than a runtime condition and
refusing the booking would be refusing the wrong party. It is **not** the eager index D44 warned
about: it reads every name once through the safe path and caches none of them. One of its tests
asserts the `@PostConstruct` is still on it — a guard the container never runs is not a guard, which
is the lesson from the finding above, one method away.

**The CI route check ignored the route's target.** Widening the predicate failed it, removing the
route failed it, a trailing space failed it — but pointing `ROUTES_4_URI` at catalog passed, and
`StripPrefix=2` → `StripPrefix=1` passed. Both are one character in a block that already names three
other container hostnames, and both produce exactly the outcome the check's own comment describes: the
callback is delivered somewhere that maps nothing at that path, the provider gets a 404 from a URL it
was given, and the booking waits in `PENDING_PAYMENT` for ever with the professional never told it
exists. Now asserted **relatively** — whatever booking's own `/api` route points at, the webhook route
must point at too — because the three files name booking three different ways, and a literal would
have to be maintained in the check as well as in the compose files.

**Two documents were wrong about their own subject.** This section's headline said thirty-one tests
over an enumeration adding to thirty-nine against a tree that moved by thirty-seven; all three now
agree. And `CLAUDE.md`'s note on the gateway's hand-written files had grown a second file without
growing a second consequence — "Without **it**" had two antecedents and described one. Both files'
consequences are stated now, along with where the `@Order` has to sit and why.

---

## D46 — Four defects the quality box found, and the two of them that were decisions

Found 2026-09-04 by the quality run of `1eadc7a` — the first quality run since WP-09, five packages
back. That gap is the first finding and it is not one of the four: **the box had been serving a
five-package-old commit for twenty-six hours while every health check, every tile and every
`--verify` reported it healthy.** `quality/startup.sh` defaults `TAG` to the current commit and
refuses to fall back to `latest` precisely so a stack cannot run something nobody chose (D13, D14),
and it did its job — nobody had run it. WP-10 through WP-13 each closed with the sentence "no run
against the quality box"; four of those in a row is what twenty-six hours of a stale estate looks
like from the other end.

The four defects below are what the run then found once the box was on the right commit. **Two are
repairs and two are decisions**, and the difference matters: a repair restores a property the code
was supposed to have, a decision changes what the tool is for.

### 1 — Both end-to-end scripts could only address the dev estate. A repair.

`verify-cycle.sh` pinned `localhost:18201`, `localhost:18202` and `docker exec
healthconnect-dev-<svc>-db-1`; `verify-outbox-recovery.sh` pinned 18202/18203 and
`healthconnect-dev-<svc>-db-1`. Quality publishes 18100–18103 and names its containers
`hc-market-quality-*` explicitly, so **neither script could run against the box they exist for** —
and since the dev estate was in a restart loop, they would have failed against both.

Worth noticing what the pinned ports were: 18201/18202 are not `deploy-dev.sh`'s defaults, they are
the *override example* in `CLAUDE.md`. So the scripts were already pinned to one operator's shell
rather than to the estate, and had been since they were written.

Both are parameterised now on `deploy-dev.sh`'s own variable names with `deploy-dev.sh`'s own
defaults — `HC_CATALOG_PORT`, `HC_BOOKING_PORT`, `HC_MESSAGING_PORT`, `HC_PAYOUT_PORT` — plus one
variable per database container, because the dev compose names none of them (compose derives
`<project>-<service>-1`) while the quality compose names all five. `HC_DEV_BOOKING_CTR` becomes
`HC_BOOKING_CTR`, with the old spelling still honoured: the `DEV` in it was a lie the moment the
script could address a second estate.

**And a guard, because "override them together, never one side only" is a rule nothing enforced.**
An HTTP port from one estate beside a database container from another is silent: the API answers
from one place and every count is read from another's tables, and the run reads as a broken outbox
rather than as a mis-addressed script. Compose labels every container with its project name, so the
containers behind the ports and the containers holding the rows can be compared without either
script knowing any estate's naming scheme — which they must not, since the two compose files
disagree about naming deliberately (the dev one prefixes `dev-` to keep `hcnet`'s aliases apart).
Both scripts refuse before touching anything. In `verify-outbox-recovery.sh` that check is load
bearing rather than tidy: it **disconnects a named container from `hcnet`**, so naming the wrong
estate's container severs a service somebody else is using, and the reconnect trap only restores
the one it cut. It also asserts that the container it is about to sever is the one answering on the
booking port, or "the accept still succeeded" is asserted against a service that was never cut off.

Both were then run against the live quality estate, which is the only thing that proves any of it:
`CYCLE PASSED` and `OUTBOX RECOVERY PASSED`.

### 2 — A successful `verify-cycle.sh` made `startup.sh --verify` report a fault. A decision.

`--verify` asserted `reviews == 63`. `verify-cycle.sh` books, accepts, completes and **reviews**,
and a review cannot be deleted — deliberately, since review integrity is one-directional (spec §7).
So a successful cycle left the box at 64 and the next `--verify` printed `✗ reviews through the
gateway got 64 want 63` and exited failure. **Two tools each working correctly, arranged so that one
reports the other's success as a defect**, with the next person sent hunting something that is not
there.

The constraint that could not be weakened is this repository's oldest one: `--verify` exists partly
to catch a wrong-app or wrong-data collision, and `admin.healthconnect.local` once served the
patient app with a 200 and a plausible login page. An assertion deleted cannot see that.

**Taken: the counts are split by whether anything in this repository writes to them, and the
exactness that is given up is replaced by something a count could never have given.**

- **Seed-exact** — `professionals`, and the catalogue's own body. Nothing here creates a
  professional, so drift is a fault and the assertion stays exact.
- **Seed plus recorded activity** — `reviews`. At least the seed's figure, with the surplus
  *printed* rather than swallowed: `64 (seed 63 + 1 recorded)`. A number that has moved is still on
  the screen, it is just not an exit code.
- **And a new check that does not depend on the count at all**: p1's `rating` and `reviewCount`,
  which come from the `professional_rating` view, must equal the average and the number of the
  reviews the API serves from a different endpoint. That is "derived, never stored" asserted
  directly, and it holds whether the box is seed-exact or has been exercised. *(As first written it
  was asked on loopback only, where no sibling can answer, while the paragraph below claimed the
  collision property for it. Corrected by the review — see §5.)*

**What it costs, stated rather than buried:** `--verify` no longer fails when somebody has written
extra reviews into the box by hand. It reports them. That is a real loss and it is the smaller half
of the trade — the collision check is *stronger* than it was, because `at least 63` fails on a
non-number exactly as `== 63` did, and the derivation check is new.

The alternatives weighed. A mode flag (`--verify --exercised`) puts the judgement on whoever
remembers to pass it, which is the same class of guard as the `@Order` D45's review found nobody was
reading. Reseeding after every cycle makes the end-to-end check cost four minutes and a data wipe,
which is how it stops being run. Teaching `--verify` to recognise the cycle script's own review by
its body couples two tools that should not know about each other and fails the moment anyone books
by hand.

**`verify-cycle.sh` also says what it wrote now**, whether it passed or failed: the booking
reference, the ledger row, the review with the resulting count beside the seed's, the messaging
rows, and the fact that there is no surgical undo because reviews are not deletable — with the
reseed command spelled out. Half of this defect was a tool that changed an estate and did not say
so.

**Found in the fix, and it belongs here because it is the same class as the defect:** the first
version of the derivation check compared a Python `round()` against a rating the view rounds in
Postgres, reported `rating 4.3 over 8, reviews say 4.2 over 8`, and was **wrong about a correct
estate** — a 4.25 average, half-to-even against half-away-from-zero. A plausible wrong number
arriving in the checker rather than in the thing checked. `Decimal` with `ROUND_HALF_UP` now, with
the reason written where the next person will meet it.

### 3 — A refusal that offered the one name it exists to withhold. A repair.

`PaymentChoiceRefused.message` used the literal `"none"` as its empty-offer sentinel, and
`UnconfiguredPaymentProvider.name()` is **also** `"none"`. So an estate with nothing configured
answered `paymentProvider: "none"` with *"this estate does not offer that payment provider; it
offers: none"* — a sentence that contradicts itself, and whose only actionable reading points a
client integrator at the one name D45's `choices()` exists to keep off the list. Behaviour was
correct throughout; the refusal is right and the request is refused. Only the prose was wrong.

**The fallback is not renamed.** `none` is a URL segment on the webhook path, a property key and a
`payment_attempt` column value, and D45 chose it deliberately. The sentinel is what moves: an empty
offer now says *"this estate has no payment provider configured, so it cannot take payment for this
booking"*, which is the fact and is also the client's next move — leave the field off rather than
guess a better name. Still composed from `offered()` and never echoing the request, which is the
property D45 and D44 both paid for.

The unreachable case is answered too rather than left to compose nonsense: `CHOICE_REQUIRED` cannot
arrive with an empty offer through `chosen`, because it needs at least two, but the constructor is
public. Confirmed red first, quoting the whole defect: *Expecting throwable message "this estate
does not offer that payment provider; it offers: none" not to contain "it offers: none"*.

### 4 — A headline figure that was not live, under a banner that said LIVE. A decision.

Discover's fourth hero stat, "Sessions brokered", was `PRO_HISTORY.length + BOOKINGS.length`.
`PRO_HISTORY` is a const in the prototype's first script block that live mode **never** repopulates,
and `BOOKINGS` only repopulates behind a token — so the closed demo and the live estate displayed
the identical **18 / 16 / 63 / 269**. The first three coincide only because the seed was extracted
from the demo. The fourth is fabricated: the estate seeds **256** sessions and 286 bookings in
total, and neither is 269.

This is the `p.rate` / `₵NaN` defect exactly — a value block 1 derives once that live mode forgot to
recompute — with one difference that makes it worse to find. `₵NaN` announced itself the moment
somebody looked at the page. A plausible number does not, and nobody would ever look at it twice.

**Taken: do not show a figure that cannot be true, rather than make it live.** Making it live needs
a public estate-wide count of bookings, and there is none — booking publishes nothing an anonymous
reader can count. Adding one is a decision about disclosing volume, taken for the sake of a
prototype's hero tile, on a service whose every other endpoint is scoped to the caller. That is the
wrong reason to open an endpoint, and the same argument D45 used to *not* publish the provider list.
Three figures that are true beat four with one invented.

**Built inside the prototype's constraints, which are strict and easy to break.** The seed is
extracted from the **first** script block only, in a `vm` sandbox with no `fetch` and no `window`;
live mode is the **last** block and mutates the data consts in place. So the figure becomes a
`function sessionsBrokered()` declared in the **third** block beside `viewDiscover`, live mode
replaces it with one returning `null` exactly as it already replaces `confirmBooking`, `sendMsg` and
`submitReview`, and `viewDiscover` omits the tile on `null`. Block 1 is untouched, no network call
moves anywhere near it, and `node deploy/demo/extract-seed.mjs` regenerates `seed-data.json`
**byte-identically** — asserted, not assumed, and still 18/52/63/256/₵81,620.

**Pinned in `verify-prototype-live.mjs`, in both directions.** Live Discover must not contain
"Sessions brokered" *and the closed demo must still show it, still counting 269* — because deleting
the tile outright satisfies the first and quietly changes the acceptance target, which is the
prototype's other job. The demo half runs the same blocks in a second context with no `?api=`, which
is the only thing that turns live mode on. Both new checks confirmed red against the old file
(`live Discover shows no "Sessions brokered" true want false`) with the demo pins already green.

**Ended in a real browser**, which for this defect is not optional and for once was not sufficient
either — the number looked fine. Chrome, both modes, against the live quality estate: demo renders
`18 / 16 / 63 / 269`, live renders `18 / 16 / 64` with no fourth tile and no `NaN` anywhere in the
rendered DOM.

### 5 — Reviewed 2026-09-04: seven findings, all applied

The review was positive on all four fixes and then found seven things. **Three of them are
documents claiming a property the code does not have**, which is now the most repeated finding on
this project; two are the fixes reaching one of the two places that needed them; one is a new
checker that becomes a false alarm at a scale nobody has reached yet; and one is pre-existing and
wider than this branch, recorded rather than fixed.

**The substantive one: §2 was fixed in `quality/startup.sh` and not in `deploy/deploy-dev.sh`, and
§1 is what made the second reachable.** `verify_seed` asserted `reviews == the seed file's count`
and then compared the API's rating against an average computed *from the seed file*. It is called by
`up` as well as by `reseed`, so the exact pairing this decision describes still existed one script
over: run the newly-portable `verify-cycle.sh` against a dev estate, then `deploy-dev.sh up` without
`--clean`, and it dies at `seed counts do not match` — or, past that, at `derived rating 4.3
disagrees with the seed's own reviews (4.7)`. Making the cycle script portable is what put a second
estate within its reach. Both deaths were reproduced verbatim, and the fix is `--verify`'s
treatment: `professionals` stays seed-exact (nothing in this repository creates one), `reviews`
becomes seed-plus-activity with the surplus printed, and the rating is compared against the reviews
**the API itself serves** rather than against the seed file. It stays in `jq` — `deploy-dev.sh`
requires `docker`, `curl` and `jq` and nothing else, and a new dependency in Appendix A is not free.
Its arithmetic is integer tenths for the reason §2's own rounding defect gives: `add/length*10|round`
is doubles, and 87/20 is 4.34999999999999964 in binary, which rounds *down* to 4.3 and disagrees
with the view's 4.4 against an estate that is entirely correct. Re-embedded into the spec with
`sync-appendices.sh`.

**The collision argument was attached to a check that never traversed the vhost.** The derivation
check was asked at `http://127.0.0.1:$GATEWAY_PORT`, which reaches this compose project's own
gateway and nothing else — no sibling can answer there. So it proved the read model, and the prose
around it (here, in `startup.sh` and in `CLAUDE.md`) claimed it was what makes `--verify`
"unsatisfiable by a sibling app on a stolen hostname". The shared nginx is the only surface where a
wrong application can reply at all, and the single count at `http://$SITE` was the only check that
went through it. Both halves are done rather than either: the derivation check now runs **twice**,
once on loopback and once through `$SITE` when the name resolves, and the three documents are
corrected to claim only what is true. Nothing was lost by the original placement — the
`reviews == 63` it replaced was on the same loopback port — but a justification that does not
survive reading the code is the thing this project keeps having to fix.

**The new check becomes a false alarm at 200 reviews.** It averaged `?page=0&size=200` and compared
the length of that page against `reviewCount`, which comes from the uncapped view;
`MarketplaceResource` passes `size` straight through with no cap, and the response already carries
`totalElements`, which was ignored. Past 200 reviews on p1 it would have reported `rating 4.4 over
250, reviews say 4.5 over 200` against a correct estate — the `round()` defect again, one release
later, in the same checker. p1 carries 7 today and `verify-cycle.sh` adds one per run **to p1**, so
it is distant and not theoretical. It pages now, asserts `totalElements == reviewCount` as a named
failure of its own, and refuses to average a page it could not complete. Confirmed red at a scale
this workstation cannot be driven to by **constructing** it: a stub catalogue serving 250 reviews
whose first 200 are five stars and whose last 50 are four, so the true average (4.8) and the
first-page average (5.0) differ. The old block reported `rating 4.8 over 250, reviews say 5.0 over
200`; the new one agrees, and still fails on a skewed rating and on a `reviewCount` that disagrees
with `totalElements`.

**An empty compose label collapsed into the container name.** `project_of` returns empty for a
container docker started rather than compose, and `printf '%s\t%s' "" "$name"` is a line beginning
with a tab — which whitespace-splitting `awk` reads as the *name* being field 1. So N unlabelled
containers looked like N distinct estates, and the refusal printed container names in the project
column with blanks beside them. It fails safe and accuses the wrong thing, and a hand-started
database is exactly what `CLAUDE.md`'s own `docker run -d --name hc-catalog-db …` loop produces.
`awk -F'\t'` and a `(none)` placeholder, in both scripts. Reproduced first: projects `""`/`alpha`
and `""`/`beta` give `unique=2` under the old form and `unique=1` under the new. **What that leaves
is stated in both files** — two unlabelled containers now group together, so the guard cannot tell
two hand-started estates apart, because there is nothing to compare. The case that matters, an
unlabelled container mixed with a compose-managed one, is still refused and is now named correctly.

**A precondition on a service one script never addresses.** `verify-outbox-recovery.sh` required
`HC_MESSAGING_PORT` to be published by a container in the same project, and then never made an HTTP
request to messaging — every messaging assertion goes to `mq()` and reads the database. So the guard
refused runs it had no reason to refuse, which was reproduced: HEAD refuses the quality box's own
documented invocation with `nothing publishes messaging's port 8083`. Narrowed to what the script
uses; messaging is still held to the estate check through `HC_MESSAGING_DB_CTR`, which is the thing
the assertions read. **The general form of it is documented rather than narrowed away**: both
scripts find containers by asking docker which one *publishes* a port, so an estate whose services
run from a jar or an IDE against dockerised databases is refused even though the assertions would
work. In `verify-cycle.sh` that is the price of the consistency guard — the compose project label on
the container listening there is the only thing tying an HTTP port to the rows behind it without
hardcoding a naming scheme — and in `verify-outbox-recovery.sh` it is not a price at all but the
method, since the test *is* disconnecting a container from a docker network. Both headers now say
so plainly.

**A documented variable the script does not read.** `verify-cycle.sh`'s header example set
`HC_BOOKING_DB_CTR`; `q()` handles `catalog` and `payout` only and booking's database is never
queried. It implied booking's *database* was part of the estate check when only its *API container*
is, and a typo in it would have been silent. Removed, with the reason in its place.

**And one that is pre-existing, wider than this branch, and deliberately not fixed.** The
prototype's professional workspace — `proStats()`, "Recently completed", "N confirmed sessions ahead
· N completed to date", the earnings screen's upcoming total, "% of sessions reviewed" — is computed
from `PRO_HISTORY` and `PRO_SCHEDULE`, and live mode repopulates neither. **In live mode that entire
screen renders demo figures under the LIVE banner**: §4's defect one screen wide instead of one tile.
Fixing it needs a professional's token and endpoints this estate does not publish, and widening a
review fix into it is how a branch stops being reviewable. But §4's rule generalises — adding a hero
figure means saying which mode it is true in — so the live block's own "WHAT IS LIVE, AND WHAT IS
NOT" section now says it, and the backlog carries it as **NEW-8**, a known limitation rather than an
oversight. The seed still regenerates byte-identically after the edit, which is the only thing that
proves a comment in that file is only a comment.

**What could not be exercised, and what was reasoned instead.** The dev estate has been in a restart
loop for five days, so `deploy-dev.sh up` could not be run and the largest finding could not be
proved against a real dev estate. Its `verify_seed` was therefore run — the real function body,
sourced from the real file with the router stripped — against a **stub catalogue** standing in for
the estate a successful `verify-cycle.sh` leaves behind (18 professionals, 64 reviews, p1 with the
seed's seven stars plus a one-star review: 34/8 = 4.25 → 4.3), and against the **live quality box**
read-only, where it passes. Both of its old deaths fired against the stub and all five of the new
one's refusals fire: a vanished professional, a rating that disagrees with its own reviews, a
`reviewCount` that disagrees with `totalElements`, a page that truncates, and a sibling's HTML
answering on the port. Nothing was written to any estate.

---

## D47 — A badge is dated in the desk's calendar, and says nothing more than the day

WP-15. Two things, both about the same field: `ProfessionalDetail.verifiedOn`, the one piece of the
verification audit trail D16 lets out onto a public profile.

### The field said "the DATE ONLY" and shipped a timestamp

The comment on it read *"The DATE ONLY. The reviewer's login and the evidence reference stay on the
desk endpoint"*, and the type beside that sentence was `Instant`. So an unauthenticated `GET
/api/professionals/{ref}` answered:

```json
{ "verification": "VERIFIED" }, "verifiedOn": "2026-09-04T19:45:03.137625199Z"
```

— to the nanosecond. That is not the disclosure D16 was arguing about, and it is adjacent to it. The
date says a person at BridgeCare checked, which is the whole point; the *time* says when that person
was at their desk. Aggregated over a catalogue it is a picture of when the verification queue is
worked, which shift decided a contested case, and which reviews were signed off at 23:50 — a
correlate of the reviewer identity the field exists to withhold, arriving on the same field, in the
same response, one type away. D16 gave the profile a date and nobody checked what a date was on the
wire.

`LocalDate`, then. What is **stored** is untouched: `VerificationReview.reviewedAt` is still an
`Instant`, and the desk endpoint still returns it in full to `ROLE_BROKERAGE`. This is a rendering
decision at the public boundary, not a remodelling.

### The zone is the decision, and Africa/Accra is the answer

An `Instant` cannot become a `LocalDate` without a zone, and there is no such thing as picking one
silently — there is only picking one and not writing it down. Three candidates were weighed.

**`ZoneId.systemDefault()` — rejected, and it is the one that would have shipped.** Ghana is UTC+0
all year, so on a workstation in Accra, in CI, and in a container with no `TZ`, it produces exactly
the right answer. It produces the wrong one the day a container is started somewhere else or a
developer's laptop is not on GMT — which was measurable here immediately: this workstation runs
`Europe/Berlin`, and the badge-date unit test written against a `systemDefault()` implementation
reported `expected: 2026-01-14 but was: 2026-01-15` without any test fixture arranging it. The
implicit choice is not merely undocumented; it is already wrong on the machine this was built on.

**The professional's own `zoneId` — rejected, and it is the one D21 might seem to require.** D21
gives the professional's zone the wall clock of an **appointment**, because that is where the
service is physically delivered. A verification is not delivered anywhere. It is BridgeCare reading
documents at a desk, and D21 puts that squarely in its *other* category — the `Instant` that records
"when did this happen", beside `raisedAt` and `completedAt`. Rendering it per-professional would also
mean one afternoon at one desk became two different dates depending on whose profile it was written
on, and would need a fallback for a null `zoneId` that could only be `Africa/Accra` anyway.

**`Africa/Accra`, named in the code as `MarketplaceService.BADGE_ZONE`.** The brokerage's own
calendar, one zone for one desk, the same date to every reader.

**What that means near midnight, stated rather than discovered.** A review recorded at 23:40 in
Accra is dated the 14th on the badge and is already the 15th for a customer reading it in Nairobi;
one recorded at 00:20 is dated the 15th while it is still the 14th in Accra's west. That is the
intended trade. The alternative — rendering in the reader's zone — makes the same review two
different dates to two customers, which is worse for a field whose entire job is to be a stable
public claim about a person. The badge names the day BridgeCare did the work, in BridgeCare's
calendar, and does not move.

**Pinned by observation, not by reading the constant back.** `VerificationBadgeDateUnitTest` sets
the JVM default zone to `America/New_York` and asserts an instant that is a different day there, so
a return to `systemDefault()` is red; and it asserts `2026-01-14T23:40:00Z` is the 14th, which is
red for any zone east of UTC. Between them the day is bracketed from both ends. The pair cannot
distinguish `Africa/Accra` from `UTC` — nothing can, they have never differed and Ghana has no DST —
so the third assertion reads the constant, which is honest about being a spelling check.

### Nothing pinned the non-disclosure, and now something does

D16 kept the reviewer's login and the evidence reference behind `ROLE_BROKERAGE`. That was true for
one reason: nobody had added them to the public projection. There was no test, in any service, that
would have gone red if somebody had — and "just add the reviewer, customers like knowing" is a
plausible, well-meant, single-line change to a record.

`VerificationDeskResourceIT.thePublicProfileDisclosesNeitherReviewerNorEvidence` verifies a
professional with a real evidence reference at the desk, fetches the **public** profile, and asserts
the serialised body contains neither the key `reviewer`, nor the desk login, nor `evidenceRef`, nor
the reference itself.

**On the body, deliberately, and it belongs at the desk's IT deliberately.** The defect this
prevents is a field arriving on the wire, and a test that inspects a Java object cannot see a
serialiser being helpful — an added getter, an `@JsonUnwrapped`, a projection that starts returning
the entity all put the field on the response while the DTO looks unchanged. And the only way to give
the public profile something to leak is to make a decision at the desk first, which is why it sits
beside D33's tests rather than in a public-profile test that would have had to reach into this one
for a fixture.

**It was made to fail before it was kept**, since a test asserting an absence passes for free. Adding
`String reviewer` and `String evidenceRef` to `ProfessionalDetail` and populating them from the
latest review turned it red, quoting the whole leaked body:

```
"…,"verifiedOn":"2026-09-04","reviewer":"ama.brokerage","evidenceRef":"CID-2026-0041"}"
not to contain: "reviewer"
```

The two fields were then removed. The date half was red first the ordinary way, against the code as
it stood: `JSON path "$.verifiedOn" expected:<2026-09-04> but was:<2026-09-04T19:45:03.137625199Z>`,
which is the defect itself printed by the test that fixes it.

### What was checked and left alone

**D33's regression tests still mean what they claim, and one of the two is stronger than the other.**
`suspensionClearsTheDate` and `reVerifyingRestoresTheDate` assert `verifiedOn` is null and non-null
across a `VERIFIED → SUSPENDED → VERIFIED` history, and a type change from `Instant` to `LocalDate`
does not touch presence or absence — which is the only thing D33 is about. Both still pass.

**Only `suspensionClearsTheDate` fails against D33's defect, and the first version of this section
claimed both did.** That claim was wrong, and wrong in the way this project keeps finding: a
statement about test strength that reads as settled and is measurable. `reVerifyingRestoresTheDate`
asserts the date is **non-null** after `VERIFIED → SUSPENDED → VERIFIED`, and a `verifiedOn` that
scans past a suspension also answers non-null there — so it cannot go red against that defect and
never could. It is a complementary guard against the *over-correction*: a filter that suppressed the
date whenever any suspension appears anywhere in the history would be red here and green in its
partner. `suspensionClearsTheDate` is the one that fires against D33 itself, and it was watched
firing during the review of WP-15 (`expected: null but was: 2026-09-04`). The commit message of
`5f6756c` carries the same overclaim; this is its correction. The new `thePublicDateIsADate`
strengthens the pair slightly by accident: it is the first test in the file to read the field through
the API rather than through the service.

**The prototype needs no change and got none.** It renders

```js
(p.verifiedOn ? ' on ' + fmtD(p.verifiedOn.slice(0,10)) : '')
```

— guarded by a truthiness test, so the `null` every seeded professional carries renders nothing at
all rather than throwing on `.slice`. **Quote the guard with the call**: an earlier version of this
paragraph and of `5f6756c`'s commit message quoted only `fmtD(p.verifiedOn.slice(0,10))`, which
reconstructs an alarm that is not there — a later reader meets what looks like an unguarded `.slice()`
on a field that is null for all 18. Inside the guard, `"2026-09-04".slice(0,10)` is `"2026-09-04"`,
so the slice becomes a no-op rather than a truncation, and `parseD` splits on `-` and wants exactly
what a `LocalDate` serialises to. `verify-prototype-live.mjs` reads `p1.verified` and not the date;
`verify-cycle.sh` reads neither. So no client breaks, which was checked rather than assumed — a date
arriving where an instant was is precisely the change that passes every Java test and breaks a screen.

**And the null case was then checked against the running box rather than reasoned about.** The API
answers `"verifiedOn":null` for all 18 seeded professionals, the browse card never reads the field,
live mode normalises an absent value to `null` on ingestion, and walking all 18 profile routes in a
real browser produced no page error. This is the `p.rate` / `₵NaN` class of defect (D46, NEW-7), and
the only way to close it is the browser.

**Not fixed here, and worth someone's attention. There are five in catalog, not four** — the first
count of this list, in this section and in `CLAUDE.md`, missed the one that matters most, and it is
the one that **writes** rather than renders:

| Where | What it decides | Shape |
| --- | --- | --- |
| `CatalogSeeder:105` | how far **every seeded date** is shifted | writes — **fixed by D48** |
| `ReviewWriteResource:115` | `Review.publishedOn` on a new review | writes, stored |
| `MarketplaceResource:132` | the default start of a public availability window | renders |
| `ProWorkspaceResource:228` | the default start of the professional's own window | renders |
| `ProWorkspaceResource:337` | the same, on the second window endpoint | renders |

The first row is closed — **D48**, with the identical line in `BookingSeeder`, `MessagingSeeder` and
`PayoutSeeder` closed in the same commit, because closing catalog's alone would have been worse than
closing none. The other four stand exactly as described below.

**And the inventory was only ever catalog's, which the NEW-9 review corrected.** This section was
written as part of a catalog package and scoped its grep to catalog, so it reads as an estate-wide
inventory and is not one. Counting what is **still open** after D48: four in catalog, above, and
**six more outside it** — and the six are worse than the four, because three of them **write the very
column D48 identifies as the cross-service pivot**:

| Where | What it decides | Shape |
| --- | --- | --- |
| `payout` `BookingEventConsumer:143` | `ledger.earned_on` for a completed booking | **writes the pivot** |
| `payout` `BookingEventConsumer:246` | `ledger.earned_on` for a late-cancellation fee | **writes the pivot** |
| `payout` `DisputeEventConsumer:142` | `ledger.earned_on` for a reversal | **writes the pivot** |
| `payout` `ProEarningsResource:69` | "today", for the month-to-date earnings slice | renders |
| `payout` `ProEarningsResource:86` | the same, on the chart endpoint | renders |
| `booking` `ProBookingResource:111` | the default start of the professional's own window | renders |

That is **NEW-10** in the backlog, opened rather than fixed: after D48, `ledger.earned_on` is written
in Accra's calendar by the seeder and in the JVM's by the consumer, in the same table. The three
writes rank above the two reads, and `DisputeEventConsumer:142` is the sharpest of them — its comment
reasons carefully about *which day* a reversal belongs to and never names a zone, which is this whole
class of defect in one place: the decision was taken, and the calendar it was taken in was not.

The seeders' inventory is closed by construction rather than by a list — CI now refuses any clock or
implicit-zone read anywhere in the four `service.seed` packages, `SeedCalendar` excepted (D48).

`CatalogSeeder`'s is `anchorDates ? 0 : ChronoUnit.DAYS.between(seed.meta().demoToday(), LocalDate.now())`,
so one JVM-default call moves availability slots, review dates and — through the identical line in
`BookingSeeder`, `MessagingSeeder` and `PayoutSeeder` — bookings, conversations and ledger rows. On
`Europe/Berlin` in summer the JVM date runs ahead of Accra's from **22:00 UTC to midnight** (00:00 to
02:00 CEST), and a seed loaded in that window is shifted a day further than one loaded an hour
earlier, so an estate quietly stops being seed-exact against itself.

**Two things narrow it, and both were checked rather than assumed.** The quality box sets
`HEALTHCONNECT_SEED_ANCHOR_DATES: "true"` in `quality/compose.yml`, so the ternary short-circuits and
the call is never evaluated there at all; and the dev compose defaults it to `false` but sets no `TZ`
on any service, so a dev container's JVM default is UTC, which is Accra. What is left is a seeder run
on a workstation with `anchor-dates=false` — a hand-run rather than a scripted one, since `CLAUDE.md`'s
own single-service recipe anchors and the tests anchor. Latent, then, not live; and one `TZ:` line in
a compose file away from being live.

**Deliberately not fixed in WP-15, and opened as NEW-9 in the backlog instead**, because it is not a
one-line change. The identical line is in four seeders and the dates they write have to agree with
each other — catalog's `availability_slot.slot_date` against booking's `scheduled_date`, payout's
`ledger.earned_on` against booking's `completed_at` — so **fixing catalog alone is worse than fixing
none**: a uniform one-day offset in all four becomes a one-day disagreement between two services'
seeded data. There is no shared library here, so it is four edits plus whatever seam makes it
provable red, which is a package rather than a rider on a rendering package.

**Done as D48.** `SeedCalendar`, copied byte-identically into all four with CI diffing the copies; the
zone half closed, the "four evaluate independently" half deliberately left open with its triggers
named. Both narrowing facts above were re-measured there rather than inherited from here, and both
still hold.

The four rendering sites stay as they were: same class of latent defect, none of them WP-15's, and
`Review.publishedOn` is a stored date rather than a rendered one, so correcting that one is a data
question and not a serialisation one.

### Two test-strength notes, applied

Both came out of the WP-15 review as judgement calls, and both were taken.

`thePublicProfileDisclosesNeitherReviewerNorEvidence` asserted its four absences **case-sensitively**,
so a component spelled `Reviewer`, or a `@JsonProperty("REVIEWER")` on one spelled anything, published
a staff name straight past it. The field name belongs to whoever adds the field, which is exactly the
person this test exists to stop. `doesNotContainIgnoringCase` now, confirmed red first against a
key-only leak — a `String Reviewer` component carrying `"Ama B."`, a value the test does not hold, so
the old form was green on all four needles and the new one reports `but found: ["reviewer"]`.

`thePublicDateIsADate` computed `LocalDate.now(Africa/Accra)` **after** the desk stamped the review
with `Instant.now()`, so a stamp microseconds before Accra midnight against an expectation microseconds
after was a one-millisecond-per-day failure — at 02:00 on this workstation. A package about a midnight
boundary should not leave one in its own test. The Accra date is read either side of the call now and
the served date asserted `isBetween` them, and the "date, not instant" half moved to a pattern on the
raw body (`"verifiedOn":"\d{4}-\d{2}-\d{2}"`), which is a stronger statement of the shape than an
equality against a rendered string. Confirmed it did not weaken: reverting `verifiedOn` to `Instant`
turns it red quoting the original defect, `"verifiedOn":"2026-09-04T20:26:24.623041711Z"`.

## D48 — The four seeders keep the estate's calendar, and one of two failure modes is closed on purpose

NEW-9, opened by the WP-15 review (D47) and deliberately not folded into it. Four near-identical
lines, one per seeded service:

```java
long shift = anchorDates ? 0 : ChronoUnit.DAYS.between(seed.meta().demoToday(), LocalDate.now());
```

`LocalDate.now()` takes the JVM default zone. Under `anchor-dates=false` that single call decides how
far **every** seeded date in that service moves — availability slots and review dates in catalog,
every booking's schedule and its `raisedAt`/`completedAt` in booking, conversations and notifications
in messaging, `ledger.earned_on` in payout.

### The two failure modes, named separately, because only one of them is closed here

**1. The zone is implicit.** The shift is measured against whatever calendar the container happens to
be started in. This is the implementation D47 rejected for the badge, arriving on the one call in
catalog that D47's first inventory missed and the only one of the five that *writes*.

**2. The four evaluate independently.** Even with an identical zone, four services seeding either
side of midnight compute two different shifts, and there is no barrier between them — they start when
they start.

Mode 1 is closed. Mode 2 is not, and that is a decision rather than an omission; the argument is
below.

### Both narrowing facts still hold, re-checked rather than inherited

D47 recorded two and this package re-measured both against the tree at `18d86c8`:

- `quality/compose.yml` sets `HEALTHCONNECT_SEED_ANCHOR_DATES: "true"` on **all four** seeded
  services, so the ternary short-circuits and the call is never evaluated on the box;
- **no compose file in this repository sets `TZ` on any service** — `grep -rn 'TZ:' deploy/docker/*.yml
  quality/compose.yml` returns nothing — so a dev container's JVM default is UTC, which is Accra.

So the defect was latent, not live: what was exposed was a seeder run by hand with
`anchor-dates=false` on a workstation, and one `TZ:` line away from being neither.

One correction to D47 in passing, and it changes nothing: this workstation runs **`Europe/Vienna`**,
not `Europe/Berlin`. Same offset, same DST rules, same two-hour window every summer evening in which
its date runs ahead of Accra's — so every claim D47 made about the behaviour holds, and only the name
was wrong.

### Africa/Accra, in a file copied four times

`SeedCalendar`, in `net.jojoaddison.service.seed`, holding `SEED_ZONE = Africa/Accra` and the shift
itself. Same zone as D47's `BADGE_ZONE` and the same argument: Ghana is UTC+0 all year, so the value
has not changed — what changed is that it is written down and can no longer be moved by an
environment variable set by somebody who was not thinking about the seed.

**It is a file rather than a constant because a constant duplicated four times with nothing comparing
the copies is how the next divergence arrives in silence.** There is no shared library here, so this
is the `SubjectPseudonym` arrangement (D35) applied to the same class of problem, one service wider:
the file and its known-answer test are **copied byte-identically into catalog, booking, messaging and
payout**, and CI diffs the four copies. Comments included — a comment true in one service and stale in
another is its own defect, which D35 established and this inherits.

A second check goes with it: **no seeder may read the clock directly**, a grep over the four `load()`
methods. `SeedCalendar` is now the only place in any of them that reads the time, which is what makes
that a grep rather than an argument. `Instant.now()` is deliberately not in the banned list — an
`Instant` carries no calendar and cannot be read in the wrong one, and `CatalogSeeder` stamps
`Favourite.addedAt` with one, which is D21's "when did this happen" category and not a date to be
shifted at all. Both checks were watched firing before being kept: the grep against the four lines as
they stood at `18d86c8`, and the diff against a `payout` copy with `UTC` substituted for
`Africa/Accra`.

### Why NOT a single explicit "today", which was the other candidate

The obvious way to close mode 2 as well is one `HC_SEED_TODAY` computed once by `deploy-dev.sh` and
handed to all four containers, defaulting to unset. It was designed and rejected, and the reasons are
worth writing down because the trigger to revisit is specific.

**What it buys.** Mode 2 closed outright, and a reproducible seed — `HC_SEED_TODAY=2026-08-10` becomes
a second spelling of `anchor-dates=true`, and any day can be replayed.

**What it costs.**

- **A fifth place a value has to agree with a sixth.** This repository has been burnt by exactly that
  shape more than once: the dev topic prefix in the compose file and in `deploy-dev.sh` (D29), the
  quality vhost's upstream port in `host-site.conf` and in `compose.yml`, the webhook route and its
  gateway permit (D45). Each of those now needs a CI check to hold it together. This would be another,
  guarding a window measured in seconds.
- **`deploy-dev.sh` is Appendix A**, so touching it means re-embedding the spec.
- **Container churn.** A value that changes daily is a changed environment, so `deploy-dev.sh up` on a
  running estate would recreate all four app containers once a day for no reason the operator asked
  for. Persisting it to a gitignored file instead — the shape `quality/startup.sh` uses for the
  pepper — avoids that and buys a new question: how does one deliberately move the estate's calendar
  forward, and the answer "delete the file" is a secret's idiom applied to something that is not a
  secret.
- **An optional knob that is only correct when the deploy script sets it** is safe when unset (it
  falls back to exactly what this package builds) and therefore also silently absent when somebody
  runs `docker compose up` by hand — which is precisely the hand-run case that was the defect's only
  live exposure in the first place.

**And what the residual actually is, measured rather than feared.** The four apps are started by one
`compose up -d` in `apps_up`, each `depends_on` its own database and nothing else, so they boot in
parallel and each evaluates the shift when its own context is ready — seconds apart, not minutes
(**re-measured at review: 7.1 seconds** between the four seeder log lines on the quality box, so
straddling Accra midnight is roughly a 1-in-12,000 event per fresh `up`). The window in which two of
them can land on different Accra days is that spread, once a day, on the dev estate only, since
quality anchors and production never seeds. `deploy-dev.sh reseed` is the same shape: four sequential
`curl`s, seconds apart.

*(This paragraph was incomplete as first written, and §review 4 below is the correction:
`reseed --services <subset>` is one `curl`, not four, and its gap is days rather than seconds. It is
refused without `--force` now, which is what makes the sentence above true rather than nearly true.)*

Set against that, mode 1 was wrong for **two hours every day** on the machine this was written on, in
every environment, including hand-runs, and one `TZ:` line from being wrong on all of them.

**The trigger to revisit is named, so this is a decision and not a shrug.** Add `HC_SEED_TODAY` the
day any of the following becomes true: a `TZ:` line is added to a compose file (the whole reason mode
1 mattered, and it makes the spread wider than the boot window if it differs per service); the four
services stop being started together, by a staggered `deploy-dev.sh` or by a dependency ordering that
puts minutes between them; or a seeded date acquires a consumer that fails loudly rather than
silently, at which point reproducibility is worth a variable on its own.

**Diagnosability was taken instead, at no cost.** Each seeder's log line now names the day it shifted
to as well as the shift — `shifting every seed date by 26 days: 2026-08-10 -> 2026-09-05 in
Africa/Accra` — so if two services ever do disagree, the record of why is in both logs. The day is
**derived from the shift** (`demoToday.plusDays(shift)`) rather than read from the clock a second
time: two reads either side of Accra midnight would print a date the seed was not loaded against, in
the one line whose job is to explain a disagreement.

### The seam, and what was watched go red

The seeders took a boolean and no clock, so there was no way to stand at 23:59 in Accra while the JVM
thought it was tomorrow. `SeedCalendar.shiftDays(demoToday, anchorDates, Clock)` is that seam:
package-private, taking the **instant** from the clock and the **calendar** from `SEED_ZONE`, which is
why the production overload passes `Clock.systemUTC()` rather than `systemDefaultZone()` — a clock
carrying the JVM's zone would put the defect straight back. The test's fixed clocks deliberately carry
a *non*-Accra zone for the same reason: a clock already holding the right zone could not tell an
implementation that names one from an implementation that does not.

`SeedCalendarUnitTest` is six tests, and **three of them were confirmed red** against a stand-in whose
only difference from the line it replaces is that the instant is injectable — `LocalDate.ofInstant(clock.instant(), ZoneId.systemDefault())`,
which is `LocalDate.now()` exactly:

```
theSameDayShiftsTheSameWhateverHourTheSeedIsLoadedAt
  [a seed loaded at 23:40 in Accra is the same day as one loaded at noon]
  expected: 26L but was: 27L
lateEveningIsStillAccrasDay   expected: 2026-09-05 but was: 2026-09-06
earlyMorningIsStillAccrasDay
```

The first is NEW-9 itself printed by the test that fixes it: two instants twelve hours apart on one
Accra day, under a default zone east of UTC, producing shifts that differ by one — which is a
one-day disagreement between whichever two services seeded either side of 22:00 UTC.

The two zone tests bracket the day from both ends, as D47's badge pair does, and they are **two tests
rather than two assertions in one** because the first assertion to fail hides the second: written as
one test, the westward half would never have been watched firing. That is the class of finding the
WP-15 review spent its time on.

The remaining three assert what cannot be made red by the old code, and each says so: anchoring
ignores the clock entirely (a decision, not a fix); the no-clock overload really routes through the
seam, read either side and asserted `isBetween` so a test about midnight does not contain one; and
`SEED_ZONE` is spelled `Africa/Accra`, which is honestly a spelling check — nothing can distinguish
Accra from UTC by observation and nothing ever will.

### What did not change

The seeders' signature is still `load(SeedFile, boolean)`, the property is still
`healthconnect.seed.anchor-dates`, and no compose file, deploy script or spec appendix was touched —
which is why this package needed no re-embedding and no new variable. The seed regenerates
byte-identically; nothing about the seed *file* is involved, only the arithmetic applied to it.

*(That last sentence stopped being true one review later: the residual below turned out to have a
days-wide case in `deploy-dev.sh`, so Appendix A **was** re-embedded after all. See §review 4.)*

### Reviewed 2026-09-05 — nine findings, and the two that matter are both about the check

The review verified rather than accepted: it re-diffed the four copies, re-measured both narrowing
facts on the live box, reproduced the three red tests, and confirmed by mutation that the tests' clock
zones are load-bearing (against `LocalDate.now(clock)`, `at()` on `Europe/Berlin` puts two tests red;
change only `at()` to `Africa/Accra` and all six pass). The core stood. What did not was the CI check
built to stop the defect coming back.

**1. The grep missed the exact stand-in this document quotes.** The alternation banned the *type* that
reads the clock — `LocalDate.now(`, `Clock.system` — and not the *zone* that interprets one. So
`ChronoUnit.DAYS.between(demoToday, LocalDate.ofInstant(Instant.now(), ZoneId.systemDefault()))`
passed green, and that is, character for character, the stand-in quoted above as reproducing NEW-9.
**The check that exists to stop this coming back did not stop it coming back in the form its own
author used to demonstrate it.** `ZoneId.systemDefault`, `TimeZone.getDefault` and `systemDefaultZone`
are in the list now. The `Instant.now()` carve-out is unchanged and was upheld on its merits — the
seed's `favourites` are bare professional refs with no date, so `Favourite.addedAt` has nothing to
shift and an instant is the only honest value — but a bare `Instant` and an `Instant` handed an
implicit zone are now different things to this check, which is what the carve-out always meant.

**2. The `^[^/*]*` anchor failed open on any earlier slash or star.** A negated character class cannot
cross the characters it excludes, so the anchor stopped at the first `/` or `*` on the line and
everything after it was invisible: `long pct = amount / 100; LocalDate d = LocalDate.now();` passed,
as did a log string containing a slash and any multiplication — and `PayoutSeeder` does commission
arithmetic and logs it. The intent was to skip comment lines, and it is served properly by *dropping*
comment lines from a full-line match rather than anchoring past them. Comment lines are filtered out
of `grep -n`'s output, which keeps the real line numbers and, more to the point, **fails closed**: an
unusual line now over-reports instead of passing.

**3. The grep named four files rather than the package.** `SeedDataLoader` is in
`net.jojoaddison.service.seed` in all four services, is the `ApplicationRunner` that calls
`load(SeedFile, boolean)`, and was not covered. Not hypothetical: the `HC_SEED_TODAY` this section
names as future work changes the seam's signature, and the natural place to compute that `LocalDate`
is `SeedDataLoader` — so the check was aimed at the four files that stop being the site of the defect
exactly when the follow-up happens. It scans the whole package now and exempts `SeedCalendar`, which
is the one file that is supposed to read a clock.

All three were watched firing, against seven constructed reintroductions — the three implicit-zone
forms, the three slash-and-star forms, and a `LocalDate.now()` in `SeedDataLoader`. The check as
committed at `41053c0` missed **all seven**; the widened one fires on all seven. Two controls were run
with them and must stay green, because a check that catches everything is a check nobody can work
under: a bare `Instant.now()`, and a comment naming `LocalDate.now()`.

A fourth fail-open was introduced by the fix and closed before it was kept: scanning a *directory*
rather than a list of files means an unexpanded glob leaves `grep` erroring on a literal path while
every file reports `ok`, so a renamed or moved package would make the check blind and green. The
directory's existence and the **count of files actually scanned** are both asserted now, and that was
watched firing too — moving `payout`'s seed package aside turns the step red with
`payout/.../seed does not exist — this check would scan nothing and pass` instead of passing. A check
that fails open on the tree it is pointed at is the same defect as a check that fails open on the code
it is reading, and this one arrived while fixing the other.

**4. The residual was argued against the wrong worst case, and this one is days wide.** Everything
argued above about `up` is confirmed, and better than claimed: the four seeder log timestamps on the
quality box are **7.1 seconds** apart, so straddling Accra midnight on a fresh `up` is roughly a
1-in-12,000 event. But `deploy-dev.sh` takes `--services`, and `reseed` iterates it — so
`./deploy/deploy-dev.sh reseed --services catalog` reseeds catalog **alone**, dated today, against
three services still holding whatever day they were seeded on. That is not a seven-second window; it
is however many days have passed, three for a dev estate left up over a weekend. This section reasoned
only about `up`, and about `reseed` as "four sequential curls", and never noticed that a flag
documented at the top of the same script makes it one curl.

Concretely, and silently: catalog reseeded three days after booking puts `availability_slot.slot_date`
three days ahead of `scheduled_date`, so every seeded booking falls on a day the professional's
calendar shows no slot. Nothing fails. Booking never asks catalog about a slot, catalog never marks
one taken, and `verify_seed` counts professionals and reviews. Payout is the same shape against
`completed_at`, moving the monthly chart at a month boundary while lifetime gross stays ₵81,620.

**Decided: refuse it, and document what `--force` still buys you.** `reseed` is now all-or-nothing
unless `--force` is passed, and the refusal names the consequence and the flag. Three arguments for
the guard over the note-in-the-margin the review offered as the alternative. It **restores the
argument this section makes** rather than qualifying it: the residual really is seconds wide once the
one case that is not seconds wide cannot be reached by accident, and a residual argued at one size
while existing at another is the thing to fix, not the sentence describing it. It costs **none of what
`HC_SEED_TODAY` costs** — the whole case against that option is that it is a fifth value which must
agree with a sixth, and this is a refusal computed from arguments already parsed, agreeing with
nothing and interpolated nowhere. And it keeps the case the flag exists for: reseeding one service is
the fast loop when you are working on that service's seeder, so this is a refusal, not a prohibition,
and `--force` prints which services are now stale rather than going quiet. The guard sits **ahead of
`preflight`**, because a refusal that first spends thirty seconds proving the estate is healthy reads
as a broken estate.

`deploy-dev.sh` is Appendix A, so this one carried a re-embed: `./deploy/sync-appendices.sh`, then
`--check` clean.

**5. Opened as NEW-10 rather than fixed: payout disagrees with itself about the pivot column.** Not
NEW-9's to fix and out of its scope, but it undermines this section's own thesis and nothing recorded
it. Five implicit-zone `LocalDate.now()` calls in payout, **three of them writes to
`ledger.earned_on`** — the column named above as the cross-service pivot — plus the two "today" values
the month-to-date slice is computed against. So after D48 that column is written in Accra's calendar
by the seeder and in the JVM's by the consumer, in the same table: between 22:00 and 24:00 UTC in
summer on this workstation, a booking completed at 23:30 UTC gets a row dated tomorrow, and on a
month's last day it lands in the next month and vanishes from a month-to-date tile computed against a
"today" that is also a day ahead. `DisputeEventConsumer:142` is the sharpest: its comment reasons
carefully about which day a reversal belongs to and never names a zone. Tabulated in D47, whose
inventory was corrected at the same time — it was written in a catalog package, scoped its grep to
catalog, and reads as estate-wide when it never was.

**6. "which the lifetime-earnings aggregate sums over" was not accurate**, and it was in six places —
`SeedCalendar`'s javadoc in all four copies, the CI comment, and this section. `EarningsRepository.lifetime`
is `select … from Ledger l where l.professionalLogin = :login`; every aggregate in that file reads
payout's own `Ledger` and nothing else, and `booking.completed_at` is in a **different database
instance**, so it cannot enter any of them and there is no join. Nor does booking ever ask catalog
about a slot. The true claim is weaker and more interesting: **nothing joins these pairs and nothing
can**, so the agreement between the four is enforced by no query at all and a break in it surfaces
only on a *screen* — the professional's overview putting sessions from booking beside earnings from
payout, a profile putting catalog's slots beside booking's scheduled date. That is why the corruption
is undetectable here, which is the point the inaccurate version was reaching for by the wrong route.

**7. An anchored run logged nothing about its calendar.** All four seeders guarded the log line with
`if (shift != 0)`, so the quality box — which anchors, on all four services — printed no line at all,
and "no line" is ambiguous between "anchored" and "the shift happened to be zero". Since the whole
point of that line is to leave a record of *why* two services disagreed, the one estate anybody audits
was the one saying nothing. There is an `else` now, naming which of the two cases it is, `demoToday`,
and `SEED_ZONE` — one line, and the box becomes readable.

**8. The clock's own zone was bracketed from one side only.** `underDefaultZone` correctly brackets
the *JVM* default in both directions, and this section says so. But the fixed clocks were always
`Europe/Berlin`, so an implementation reading the *clock's* zone — `LocalDate.now(clock)`, the obvious
wrong turn — is caught eastward and not westward: at 02:30 UTC a Berlin clock says 04:30 on the same
day and agrees with Accra by accident, leaving `earlyMorningIsStillAccrasDay` green. `at()` takes a
zone now and the westward test uses a westward clock, so the pair brackets both dimensions rather than
one and a half.

**9. `CLAUDE.md`'s duplication bullet named only `SubjectPseudonym`.** That bullet is what someone
about to edit a duplicated file reads; `SeedCalendar` was mentioned sixty lines away, inside the
badge-zone bullet, where nobody looking for the rule would find it. There are two such families now
and the rule names both.

**Counts, unchanged in every service, which is the expected shape.** `clean verify` on all four:
catalog **94 unit + 127 IT**, booking **158 + 111**, messaging **62 + 90**, payout **85 + 165** — the
same figures as `41053c0`, because finding 8 strengthened an existing test rather than adding one and
findings 1–3, 6, 7 and 9 are a CI script, comments and a log line. The seed still regenerates
byte-identically, both appendices match, every shell script parses, `build.yml` parses, and the four
copies are two checksums across eight files.

**One thing the review reported that was re-measured here rather than taken on trust**, since it is
the number the whole residual argument rests on: the four seeder log lines on the running quality box
are `payout 19:00:52.988` → `catalog 19:01:00.121`, a spread of **7.13 s**. Confirmed.

---

## D49 — Production is API-only, its databases are bundled and private, and none of it has been run

**Decided 2026-09-05**, building `deploy/prod-server/` to the shape the three sibling stacks share.
This is configuration and documentation only: nothing here has contacted `webserver`, and the whole
of its evidence is `deploy-prod.sh --dry-run` (which contacts nothing), `docker compose config`,
`bash -n`, seven watched mutations against the new CI checks, and reading the siblings.

That is stated first because it is the most useful thing about this decision. The package's value is
not that production now works; it is that the *questions* production asks are written down, and that
five of them turned out to have answers the repository already believed it had.

### The five things that were not true

Each of these would have stopped or silently damaged a first deploy, and each was invisible for the
same reason: **no path in this repository had ever been executed against a host.**

**1. No production database was declared anywhere.** `docker-compose.prod.yml` requires nine
connection values with `:?` — a Mongo URI, four JDBC URLs, four passwords — and `render_env` emits
none of them, so they had to be in `secrets.env`. Nothing said so, and nothing said where the stores
those URLs address were supposed to come from. `deploy-prod.sh`'s own preflight comment asserted that
`infranet` carried "Kafka, Consul and the databases", which was true of the first two and had never
been true of the third. The answer to "where does production's catalog database live?" was written
down nowhere at all.

**2. The preflight checked two of the eleven required values.** That check exists precisely so a
missing value is refused *before* `.env` is overwritten and `.env.previous` rotated. With nine of
eleven unchecked, a host holding the two secrets passed preflight and then died at `up` on "catalog
datasource url is required" — a stack half-rolled over a variable nothing in the pipeline supplied,
which is the defect the check was built to prevent, nine keys wide.

**3. The smoke test could not pass, in two independent ways.** It asked for
`$base/api/professionals/count`, and the gateway routes `/services/<service>/api/**` and nothing else
— that narrowing **is** D28's security control, so `/api/**` at the edge matches no route and never
will. And its default base was `https://health.jojoaddison.net`, a hostname this product does not
serve. A deploy that reached its own verification step would have reported a failure it caused.

**4. The gateway published on every interface.** `'${HC_GATEWAY_PORT:-8080}:8080'` binds `0.0.0.0`,
so on the production host the gateway would have answered the internet directly on 8080, beside the
nginx that terminates TLS. **This is not a routing hole** — D28's predicates apply on both paths, so
`/internal/**` stays unreachable either way — and that is exactly why it is worth recording: every
functional check passes, the site works, and the only consequence is a second front door with no TLS,
no security headers, no CSP, no robots policy and no webhook rate limit. Every sibling binds
`127.0.0.1`; `quality/README.md` states the property this restores ("the vhost is the only way in")
and the production file did not have it.

**5. `deploy-prod.sh` told the operator to install the wrong signing key.** Its `secret_hint` and its
header both said to take `JWT_BASE64_SECRET` from `~/webroot/01-healthconnect/.env`. That file holds
the key `hc-admin`, `hc-patient` and `hc-professional` share, and **hc-market is deliberately not in
that set** — D37 says so at length, having corrected the same misreading once already when it was
offered as the reason to sequence erasure from the admin desk.

This is the one of the five with a security shape, and its shape is unusual: **nothing would have
failed.** HS512 does not care which random bytes it is, so the estate would have come up perfectly.
What would have changed is that any of hc-market's five services — each of which holds the key and
can therefore mint a token for any subject with any authority — would have been minting tokens the
other three products accept, and an `hc-admin` token would have carried authority here. A capability
boundary the repository documents in two places would have been dissolved by a hint, silently, on
first deploy. The hint is now the opposite instruction with the reason attached, in both places.

Findings 1, 2 and 3 also share a shape worth naming: **`--dry-run` cannot see any of them.** It is a
faithful printer of a plan, and a plan can be wrong. What found them was writing down what the plan
would meet on the far side.

### The decision the user took, and the one the package took

**Production is API-only. The prototype is not served on `market.abofonsa.com`, and there is no
`/prototype` location in either nginx file.**

Quality serves `docs/Abofonsa_BridgeCare_Marketplace.html` same-origin at
`http://market.healthconnect.local/prototype`, with a deliberately relaxed CSP, so `?api=` reaches
the estate without CORS. The page is populated with the seed: 18 invented professionals presented as
real people, with names, credentials, association registration numbers, prices and reviews. On a
private LAN box, plaintext, reachable from one office, that is a demo. On a public health-services
domain under the BridgeCare brand it is eighteen fabricated practitioner listings published to the
internet — findable, quotable, and indistinguishable from the real thing to whoever arrives.

The interesting part is not the decision but **how it is kept**. A comment would not have held it:
the plausible way it comes back is somebody noticing the two edges differ and restoring parity, which
is a reasonable-sounding motive and a wrong one — quality exists to rehearse production, not the
reverse. So the reason is written in the vhost's own header where an editor meets it, *and* CI
asserts the absence, refusing a `/prototype` location, an `alias` to that HTML, and the
`unsafe-inline` the page needs. All three were watched firing.

The consequence is a good one and is now shared: the only surface offered to a browser on that
hostname is a JSON API, so its CSP is `default-src 'none'` — the truthful policy for something that
only returns JSON, and character-for-character the policy quality carries at site level.
`host-site.conf` claimed to be "STRICTER THAN PRODUCTION, WHICH SENDS NO CSP AT ALL"; that stopped
being true the moment this file existed and is corrected in place. **The differences now run the
other way, and there is exactly one of them.**

**The package's own decision was the databases, and it is the opposite call from D27's.**

D27's rule is borrow, never bundle, and it is about what the host already runs once for everybody: one
broker, one Consul, one collector, on networks this stack declares `external` and never creates. That
is upheld unchanged — nothing here declares a broker or a registry.

A database is the other kind of thing. There is no host-wide PostgreSQL for four products to share,
hc-market owns its schema, and D27's own closing paragraph keeps the databases off `hcnet` so that
another product cannot reach them. So they are **bundled and private**: five stores in a second
compose project, `hc-market-data`, on this product's own `hcmarketnet`, which the application stack
joins as a third network. Not `infranet` — three sibling products share that one, and a database
another product can resolve is precisely what D27 refused.

Two compose projects rather than one file, and that is the second decision. The siblings each keep a
single `prod-server/compose.yml` because their `deploy.sh` ships that file and nothing else;
hc-market's `deploy-prod.sh` already ships `docker-compose.prod.yml`. **A second copy of the five
services is the "written twice" failure this repository has paid for three times** — the vhost port
against the compose port, the topic prefix against the script that creates the topics, the webhook
route against the gateway permit. So the split is by responsibility: the applications roll on every
deploy, the stores are installed once and do not roll, and **nothing appears in both**. There remains
exactly one place in this repository where a production gateway route is written.

The cost is that the two projects must agree on the network and on five container names, and the
container names are only visible to CI through `secrets.env.example` — the values themselves live on
the host. That pair is checked; a rename in one and not the other is a five-service outage from a
one-word edit, reported as `UnknownHostException`, which points at DNS rather than at either file.

### Six new CI checks, all watched firing

The repository's rule is that a check nobody has seen fail is a check nobody should trust, so each was
run against a constructed reintroduction:

| check | mutation it was watched refusing |
|---|---|
| the production vhost and its compose agree on a port | `proxy_pass` moved to 8087 |
| the gateway publishes on loopback only | the `127.0.0.1:` prefix deleted |
| the vhost's webhook location matches the routed path | `webhooks/` renamed to `webhook/` |
| the production vhost does not serve the prototype | a `/prototype` location added; and separately, only the relaxed CSP added |
| the data tier's container names match the connection template | `hc-market-catalog-db` renamed in the compose alone |
| the connection template carries no filled-in secret | a plausible base64 pepper pasted into it |

The port check needed one correction before it was kept, and it is the same class of fail-open the
NEW-9 review found twice: a loose `HC_GATEWAY_PORT:-[0-9]+` reads **two** values out of
`docker-compose.prod.yml`, because the comment above the publish line quotes the old `0.0.0.0` form
verbatim. It matches the publish line itself now. **A check a comment can break is a check somebody
eventually deletes.**

The webhook-location check is the subtlest of the six and the only one whose failure changes nothing
visible: `location /` proxies the callback regardless, so a drifted path leaves the site working, the
provider answered, and the rate limit, the 64k body cap and the POST restriction silently not
applying to the estate's one unauthenticated public write path.

The data tier is deliberately **not** added to the three gateway-route loops, which iterate a fixed
list of three compose files. It declares no route and would fail "expected 4 narrowed gateway routes,
found 0" — and the reason it would is the reason the split exists.

**There is a seventh now.** The review below found that the most serious of the five defects was the
only one with no mechanical guard at all, and it is the one nothing running could catch.

### A seventh finding, in the file that was being read

`--help` was `sed -n '2,66p'`, with a comment beside it saying that was "the whole header block, up
to but not including its closing rule" and recording that it had been extended from line 40 when the
host-secrets section was added, "at which point `--help` stopped printing the one thing a first-time
deployer has to do before deploying at all". The header ran to line 78 by then. So `--help` printed
everything except lines 67–78, and **lines 68–74 are the `cat > secrets.env` command** — the omission
the comment was written to record having fixed, arriving a second time by the same road. It is
computed now: skip the shebang and the opening rule, print until the closing one.

### What is deliberately not built

- **No script installs anything into `/etc/nginx`.** That belongs to the architect on both machines,
  and being on the box is not the same as being authorised to reconfigure its edge. The README prints
  the sudo lines and runs none of them, following `quality/startup.sh`. It also says to read the
  installed file first, because the installed copy drifts from the repository's and sometimes
  deliberately. `hc-admin`'s `update-nginx.sh` is not copied for the same reason, plus a narrower
  one: it exists to stop that product's own `deploy.sh --with-nginx` destroying certbot's TLS block,
  and hc-market's deploy script installs no nginx at all.
- **No credential-rotation script.** `hc-admin`'s covers one Mongo database; hc-market has five
  stores across two engines and the same initialise-only-on-an-empty-volume trap applies to all five.
  A script covering a fifth of the problem invites the belief that the rest is covered. The trap is
  documented per engine instead, and writing the script is named as the first thing to do when there
  is a rotation to perform.
- **No browser-RUM ingest and no `/v1/traces` location.** Those exist to accept spans from an Angular
  SPA. There is none here, so it would be a new public unauthenticated endpoint bought with nothing.
- **No `observability/` directory.** hc-market already has `deploy/observability/hc-market-rules.yaml`
  and CI parses it; moving it would break that path for no gain.
- **No expiry, no schema-rollback path, no restore rehearsal.** All three are named in the README's
  outstanding list rather than sketched. No dump has ever been restored, which makes the backups a
  belief rather than a backup — and that is written where an operator reads it before trusting a
  `backups/` directory. (The backup *script* had never been run either, until the review below ran
  it; that found a defect and proves nothing about restoring.)

### The review, and the eight things it found

**Reviewed 2026-09-05**, the same day. The review verified all five defects above independently
against `main` rather than taking them from a commit message, watched all six checks fire on
constructed reintroductions, and confirmed the security posture. It then found eight more, one of
them blocking. All eight are applied.

**The blocking one was that this decision's own closing paragraph described behaviour the code did
not have.** It said the smoke test would "warn" on an empty catalogue, and so did `README.md`. A
failing smoke test does not warn — `deploy-prod.sh` ends `if health_gate && smoke_test; then …
else … rollback`. So requiring `> 0` on an estate that never seeds meant: the **first** deploy ends
in `die "no previous deployment recorded"`, with the stack up, correct, and never written to
`deployments.log`, and the operator's first experience of production is a red failure; the **second**
finds `.env.previous` and therefore *succeeds* at rolling back a deployment that had just come up
healthy. The estate could not have shipped again until it had data.

The fix separates the two answers the check was conflating, and the argument for that shape over
putting `> 0` behind a flag is that **`0` is not a weaker answer than `18`**. Getting a number back
from `https://market.abofonsa.com/services/healthconnectcatalog/api/professionals/count` exercises
DNS, TLS, nginx, the gateway's route predicates and a round trip to catalog's PostgreSQL — every one
of them identically at `0`. What distinguishes a healthy empty estate from a broken one is not the
magnitude, it is whether a number arrives at all: a catalog that cannot reach its database answers
nothing. So no number is a failure, `0` is warned about loudly and passes, and the `> 0` requirement
survives as `HC_SMOKE_MIN_PROFESSIONALS`, opt-in, validated in preflight. Once there is real data an
estate answering `0` **is** a failure and should roll back — but only an operator knows when that day
is, and defaulting to it costs a rollback of a working stack. Exercised against a local server in
five states: `0`, `18`, `0` with the floor at 1, an HTML body, and nothing listening.

The other seven, in the order they matter:

| # | what was wrong | why it survived |
|---|---|---|
| 2 | every remote `docker compose` relied on implicit file discovery, and this branch put a **second** compose file in that directory | it was one file for as long as the script existed. Compose prefers `compose.yml` over `docker-compose.yml`, so an operator copying the data tier under its repository name captured every later `pull`, `up`, `exec` and `ps`. Fails loudly, which is the only reason it was not worse. `-f docker-compose.yml`, one word |
| 3 | `prod-server/start` printed **"all five stores healthy" when the stores had exited** | `docker compose ps` lists only *running* containers; `-a` is needed for the rest. Filtering that output for "not healthy" finds nothing, and an empty result reads as nothing wrong. Reproduced: a two-service project with one exited container listed one service without `-a` and two with it. It asserts five present and five healthy now |
| 4 | `backup.sh` put **both database passwords in world-readable host argv**, under a comment saying that was exactly what the `-e` form prevented | `docker exec -e PGPASSWORD=<value>` is the client's own argv. Measured: `ps -eo args` showed the value for the length of every nightly dump. The name-only form does not, and one residue is now stated rather than denied — `mongodump` has no password variable and no `--password-file`, so its value is in the *container's* argv |
| 5 | the `:?` message an operator meets at the moment of failure still said **"platform JWT secret is required"** | four other copies of defect 5 were corrected and this one was quoted approvingly in two comments. It is the only one of the five that a person reads while something is already broken |
| 6 | the most serious defect was the **only one with no CI check** | there is no `iss` and no `aud` anywhere in this estate, so a pasted platform key interoperates silently and for ever. Nothing running can catch it; the guard has to be on the words. Now `.github/checks/signing-key-severance.sh` |
| 7 | `/srv/healthconnect` was justified as "the code was followed" | there is a much stronger reason. The siblings' `start` scripts read `--env-file ../.env` — the platform key file one directory above `~/webroot/01-healthconnect/<product>/`. The conventional path would put hc-market directly below the file D37 spends a page keeping it away from, with the pattern that consumes it one directory over. Also recorded: both compose files pin `name:`, so a later move is copying files rather than migrating data |
| 8 | runbook details: a `deploy@` ssh user that is probably fiction, brace expansion in a remote `dash`, `/srv` write access, no check that the **open** endpoints are the intended ones, `add_header Content-Type` in the robots.txt location, and one `▸` printed without a `[dry-run]` marker | each small, and the first and fourth are the interesting ones. The siblings ssh to the alias `webserver` as root; and `/api/register` is `permitAll` on the gateway, so **open self-registration is public on `market.abofonsa.com`** from the first deploy — presumably intended, and unrecorded until now in a file that curls three things to prove the closed things are closed |

The robots.txt one is worth one more line because it was measured rather than reasoned. `add_header
Content-Type text/plain` does not set the content type; served under a real nginx the response
carried **two** `Content-Type` headers, `application/octet-stream` first from `default_type` and
`text/plain` appended beside it — and, because a location declaring any `add_header` inherits none of
the server's, no CSP, no `X-Frame-Options`, no `X-Content-Type-Options` and no `Referrer-Policy`.
That is the replacement semantics the file's own CSP comment warns about, firing in the file that
warns about it. `default_type` and no `add_header` at all fixes both halves.

### A seventh CI check — the guard the fifth defect never had

`.github/checks/signing-key-severance.sh`, guarding the one defect of the five that nothing running
could ever catch. Three arms, each watched refusing a real state:

| arm | mutation it was watched refusing |
|---|---|
| `secret_hint`'s advice must still say what to generate, and may name the platform key file only to forbid it | `main`'s original hint restored — refused four ways at once |
| every mention of `~/webroot/01-healthconnect/.env` under `deploy/` and `quality/` must carry a refusal within two lines | a runbook line saying to paste the value in |
| no compose file's `:?` message may describe the estate key as the platform's | `main`'s "platform JWT secret is required" restored |

The first arm is the sharp one and the second is deliberately coarse. `main`'s original hint said
"never generate a fresh one here", so it *contains* a negation and would have passed arm 2 — which is
why arm 1 asserts the presence of the generation command and the exact phrase `Do NOT copy it from`
rather than the absence of something. Its job is that the original wording fails, not that every
conceivable rewrite does.

### What could not be verified, and is therefore a guess

`8086` for the gateway's loopback port: chosen in the family the siblings occupy (hc-admin 8083,
hc-patient 8085, hc-professional 5503) and **not known to be free on `webserver`**. `/srv/healthconnect`
as the stack directory: `deploy-prod.sh`'s default, and now argued on its merits rather than on the
code's precedence (see finding 7 above) — still not known to exist or to be writable there, and still
one `--path` flag. The ssh target `webserver` and a root account there: taken from what every sibling
does rather than checked. Both are the first items in the README's outstanding list. So is backlog
**WP-18**, unchanged: whether `gateway`, `catalog` or `booking` is already a DNS alias on `infranet`
still cannot be answered from a workstation — largely defused, since every service is `hc-market-*`
with an explicit `container_name`, and still unanswered.

And one thing this package **cannot** do, which the review's first finding made visible: a rollback
restores `.env.previous` and therefore the previous *tag*, but `remote_deploy` overwrites
`docker-compose.yml` and keeps no previous copy of it. So a deploy that breaks the estate through a
compose change — a route predicate, a variable name, a port — rolls the images back underneath the
new file. Not fixed here, because it needs a decision about what a deploy should keep and for how
long; recorded so that "it rolls back by itself" is not read as more than it is.
## D49 — Paystack, from evidence rather than plausibility, and the one thing the estate cannot tell it

WP-13 shipped everything around the three payment adapters and none of the adapters, and said why in
a sentence worth repeating: nobody here had an account, credentials or documentation, so a signature
scheme or a field path written under those conditions "compiles, and passes the tests written to
match it", on the one path where a customer's money is already committed.

**That condition ended for one of the three, and not by acquiring an account.**
`hc-crowdfund-app` — a sibling product in this same workspace, by the same consultancy — has run a
live Paystack integration for months: an adapter, its tests, and the `app.paystack` block that
configures it. It is a different codebase with a different seam (`initialize`/`verifyWebhook`/
`parseWebhookEvent` over a `Pledge`), a different Jackson and a different domain, so **nothing was
copied**. What was taken is the wire format, which is a fact about Paystack rather than a property of
that codebase, and the reasoning in its comments, which is hard-won.

D45's per-adapter list had six items. Five of the six are answered:

| D45's question | The answer, from the working integration |
| --- | --- |
| the authorization call | `POST /transaction/initialize`, `Authorization: Bearer sk_…`, JSON `{email, amount, reference}` |
| the amount's unit | **minor units** — which is what this estate already stores, so pesewas go on the wire unchanged |
| the platform's own reference | `reference`, **client-generated and sent by us**. Paystack does not issue it |
| the response | `data.authorization_url` is the redirect, `data.reference` is the durable handle, `data.access_code` is for an inline checkout this estate does not use |
| the callback payload | `{event, data:{reference}}`; `charge.success` is the success and everything else is not |
| the signature | **HMAC-SHA512 of the raw body, hex, under the secret key**, in `x-paystack-signature` |

The sixth — the credentials — is answered too, and cheaply: **Paystack needs no second key.** The
same `sk_` value authenticates the outbound call and computes the callback HMAC, which is why
`PaymentProviderProperties` gained no key field.

### D43's guess was right, and it should stop being written as a guess

D43 described Paystack's callback from memory and hedged every word of it; the Paystack class's own
list said to "treat every word of that as a thing to confirm rather than a thing to implement,
including the header's spelling and the digest's encoding". All of it is confirmed: HMAC-SHA512, the
raw body, hex, `x-paystack-signature`, the secret key. The hedging is removed where the claim is now
known. It is left standing for Hubtel and MTN MoMo, about which nothing has changed.

### Two calls are implemented and four are not, and that is the same rule rather than an exception

`authorize` and `readCallback` — the booking path, which is exactly the pair
`service.payment.provider`'s documentation says to do first. `capture`, `refund`,
`voidAuthorization` and `status` still refuse.

**The working integration does `initialize` plus the webhook and nothing else.** So the evidence runs
out precisely where those four begin, and writing them would be D45's invention arriving inside a
class that otherwise works — which is worse than a class that refuses everything, because it looks
like an integration. An adapter is not finished or unfinished; it is six calls, each of which is
either sourced or guessed, and this one is sourced twice and closed four times.

**The cost is real and is stated rather than discovered later.** `refund` and `voidAuthorization` are
reached from `BookingPayments.release`, so a `PENDING_PAYMENT` booking whose `creator.create` throws
cannot have its live payment cancelled at Paystack: the release throws, the attempt row is flagged
`needs_attention`, and a person reconciles it against Paystack's console. D41 built that degradation
for exactly this and it is the honest behaviour — but it means **D43's "a `PENDING_PAYMENT` booking is
not the customer's to cancel" is not closed by this package either**, since closing it needs a
provider that can be *asked* to release an authorization and this one still cannot be.

### The email, which is the decision this package could not take alone

Paystack's initialize requires an `email`. `PaymentIntent` carries a login and no contact details,
deliberately — its own javadoc says "which identifier a provider needs is exactly the thing this
record must not guess", and the package documentation answers the general case with "a provider that
needs a phone number or an email fetches it at its own boundary".

**That answer turned out to name a boundary with nothing behind it.** Three sources were considered.

**1. The booking request.** Rejected, and it is the tempting one because it is four lines.

- The prototype is the UX contract and it is explicit: the account screen renders the email
  **read-only**, sourced from "your BridgeCare patient record", and the booking wizard never asks for
  one. So a booking-time email field is not the product, it is a new field invented to satisfy an
  adapter.
- It is a client-supplied field that something downstream trusts — Paystack keys a customer record on
  it and sends a receipt to it — which is **D22's rule verbatim**, and D22's own note says "adding
  another client-supplied field that something downstream trusts reopens this".
- It creates a *second*, unverified contact detail for a person the estate already holds one for,
  arriving through a different door, so the receipt for somebody's money goes to whichever address the
  client last sent. That is D40's "the estate disagreeing with itself about whether someone exists",
  in a different column.
- And it is personal data arriving at booking. It need not be stored — but the next person will store
  it for the receipt, and `payment_attempt` acquiring a customer field is precisely the way D41's "the
  table holds no personal data and the erasure sweep therefore does not visit it" stops being true.

**2. The login, when it happens to be an email.** Rejected. The gateway's `LOGIN_REGEX` permits both
spellings, so this works for whoever registered with an address and fails for whoever did not — all
eighteen seeded customers are `firstname.lastname` — and it fails **at the moment they try to pay**,
which is the worst available moment for a rule that holds for a subset of users. A rule with a
silent exception is worse than no rule.

**3. The account store, which is the gateway's.** The correct source, and **not built.** Standing up
an endpoint that returns a person's email address by login is a decision about the estate's personal
data — who may ask, under what authority, whether the answer is routable — of exactly the kind D38
took for the erasure fan-out and D45 declined to take for the provider list. The payment seam does
not get to take it on its own, and D28's `/internal/**` property means the shape is not obvious
either: booking would be calling the *gateway*, which it has never done.

**So the boundary is named and left empty.** `CustomerContacts` in `service.payment`, one method,
no implementation, and `CustomerContacts.unanswered()` as the default the Paystack bean is built
with. On today's estate `authorize` therefore refuses **before the round trip** — no request, no
reference, no money — and `BookingPayments` turns that into a 502 and no booking, which is exactly
what a priced booking naming Paystack did before this package, for a different reason. Whoever
answers the decision writes one `@Component` and edits nothing: the bean is resolved through an
`ObjectProvider`, so an implementation is preferred the moment one exists.

**Optional rather than required, and that is D35's shape.** A required `CustomerContacts` would fail
booking's context for want of a decision nobody has taken — a service down behind one missing bean,
which is the outage D35 refused for the privacy pepper on the grounds that it makes an operator paste
in a plausible value.

### Three things that are refused rather than guessed

**The currency.** The working integration sends no currency field at all, so the amount is denominated
by whatever the merchant account settles in, and there is no evidence a per-transaction currency is
even accepted. Every price in this estate is GHS. A booking denominated in anything else would be
charged as that many minor units of the account's currency — a **silent mis-charge rather than a
rejected call** — so the adapter answers `FAILED` for it, which is a 502 and no booking. Adding a
second currency means finding the documentation that says how to declare one.

**A key that is not a secret key.** Paystack issues `pk_`/`sk_` across `test_`/`live_` and lists them
side by side, so pasting the public key into the secret's slot is an easy slip that nothing downstream
catches: the service starts, offers Paystack, and 401s at initialize the first time a customer picks
it — and cannot verify a callback either, since the HMAC uses the secret key. This is the crowdfund
comment's own reasoning and it imports unchanged. It is refused at both doors and **announced at
startup**, which is where the mistake belongs. It does **not** refuse to boot, for D35's reason.

**An unrecognised event.** Anything that is not `charge.success` is `FAILED` and never a
booking-permitting state, which is the rule this package's documentation already sets for every
adapter. **The residual, stated:** an unrelated Paystack event quoting a reference this platform
issued would cancel a booking still waiting for payment. That is the recoverable direction — the
customer books again — where the other one is money taken for a booking nobody made, and it is
narrow besides, since `PaymentConfirmations` transitions nothing that has left `PENDING_PAYMENT`.

### The failure outcome keeps the handle, which is not what `PaymentOutcome.failed` does

`PaymentOutcome.failed(reason)` sets the reference to null, and `PaymentConfirmations` finds the
`payment_attempt` row **by** the reference. So a `charge.failed` mapped through that factory names no
payment, answers `UNKNOWN_PAYMENT`, and leaves the customer's booking in `PENDING_PAYMENT` for ever
while Paystack retries a callback that can never be matched to anything. **D41's dropped handle,
arriving on the failure path**, one package after D43 met the same shape from the pending side. The
canonical constructor is used instead, and there is a test that says so in its own name.

### Two things this package corrected because they had become false

**The startup log said the opposite of the truth.** `PaymentProviderProperties.announce()` warned that
every enabled adapter "is ENABLED and is NOT IMPLEMENTED — it refuses every authorization and every
callback". True of all three when D45 wrote it; false for Paystack the moment one of them was built,
and an estate taking real payments while saying in its own first ten lines that it cannot is the
shape three of D44's four review findings had. That class binds properties and cannot see a bean, so
it cannot tell the two apart. The claim moved to `ProviderAwaitingIntegration.integratedCalls()`,
which an implemented adapter overrides — so the account stays true by construction rather than by
somebody remembering, and it is WARN for a seam and INFO for an integration. What is left in
`PaymentProviderProperties` is the half that really is a property question: an enabled provider with
no signing secret refuses every callback whether or not anybody wrote its integration.

`integratedCalls()` is deliberately **not consulted at dispatch**. What happens when an unimplemented
call is reached is decided by the call throwing, in one place. A list that is wrong makes a log line
wrong; a list that routed would make a payment wrong.

**The three compose files said it too**, in a comment block asserting that none of the three is
implemented and that turning one on makes every priced booking answer 502. The second half is still
true of Paystack and now for a different reason, which is exactly the sort of accidental
half-correctness this repository keeps finding in its own documents, so all three now say which of
the two situations an operator is in.

### Two new settings, under `PaymentProviderProperties`' own rule

`base-url` and `timeout-ms` on `Provider` — the class says "whoever has the credentials adds the
fields their provider actually has, in the same commit as the adapter that reads them", and this is
the first adapter here that makes an outbound call. Both have working defaults
(`api.paystack.co`, 10 s) so neither needs setting; the timeout exists because the call happens
inside `POST /api/bookings` with the customer waiting on it, and an unbounded wait on a third party
is booking's request threads.

They are passed by **all three compose files** rather than left to relaxed binding, because a variable
this repository documents and no compose file carries is a variable that silently does nothing —
which is what D46 found in both end-to-end scripts. That is another two places to keep in step, which
D48 warns about; it is accepted here because the alternative is the failure mode this repository has
already paid for twice.

### What is verifiable here, and what is not

**24 unit tests, against a real socket.** The stub is a JDK `HttpServer` on loopback with an
ephemeral port, not a substituted request factory: the request that is asserted on is one that went
over a wire, headers, JSON encoding and content type included. D45's whole argument was that a wire
format checked only against the assumption that produced it is not checked at all, and mocking the
HTTP client away is a weaker version of the same problem. Nothing reaches the network.

**Fourteen of the 24 were watched failing against the seam**, which is what an adapter that refuses
everything can be red for. The other ten are *negative* tests — a forged callback is refused, a
tampered one is refused, a `javascript:` URL never becomes a booking — and they **pass against a class
that refuses everything**, which is NEW-3's "a test that cannot fail" exactly. So they were proved
load-bearing by mutation instead, five ways:

| Mutation | What went red |
| --- | --- |
| `MessageDigest.isEqual(…)` forced true | `aForgedCallbackIsRefused`, `aTamperedCallbackIsRefused` |
| drop the `toLowerCase` before comparing | `caseIsFoldedNotBranchedOn` |
| rethrow instead of refusing an unparseable body | `aVerifiedButUnparseableBodyIsRefused` |
| read `reference` instead of `data.reference` | three callback tests, including the success |
| remove `@PostConstruct` from the key-shape announcement | `theStartupAnnouncementsRunAtStartup` |

`clean verify` on booking: **180 unit + 111 IT**, from 158 + 111 at `8cec0e3` — 24 added, and two
parameterised cases retired with Paystack leaving `ProviderAwaitingIntegrationUnitTest`.

**Not done, and it is the same sentence for the sixth package running:** no live provider and no run
against the quality box. It is a weaker sentence than it was — the wire format is no longer this
repository's own invention — and a stronger one in one respect: this is the first adapter for which
"run it against a sandbox account" is a thing somebody could actually do, and until they have,
nothing here has seen Paystack answer.

**And the estate cannot take a Paystack payment today regardless**, because of the email. That is not
a defect to be fixed by a later commit; it is a decision waiting for somebody with standing, and the
502 in front of it is the correct behaviour until then.
