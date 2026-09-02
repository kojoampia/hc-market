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

On the quality box this costs nothing and has already been handled: everything erased there was test
data created by the erasure ITs and by hand, and it has been cleared. Dev estates are the same —
`deploy-dev.sh down --clean` is the answer, not a new pepper.

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

### What is not fixed

The second of D34's two open notes stands untouched: nothing says what happens when an erased person
keeps using their account. It is a product decision and this was not it.

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
