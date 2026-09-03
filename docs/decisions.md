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
