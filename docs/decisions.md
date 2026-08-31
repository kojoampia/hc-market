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

### The SSE-on-the-wire gap gets investigated rather than routed around

An unexplained difference between two test contexts, in a live channel, is worth understanding. See
the open list for where that got to.

---

## Still open after this section

Only what engineering cannot settle alone:

| # | Needs | From whom |
|---|---|---|
| D15 | Provider choice and contract; whether a split model clears Act 987 | Architect + counsel |
| D16 | Whether the verification badge's meaning is acceptable given no register exists | Product |
| D24 | Retention periods, lawful basis, controller registration, data residency | Counsel |
| D17, D18 | Budget for a video provider and a WhatsApp BSP, if either is wanted | Architect |
| D28 | Whether `gateway` is already a DNS alias on production's `infranet` | Architect, on the host |

D16's *audit* half — recording who verified a professional, when and on what evidence — **is built**
as of D29: `VerificationReview` and the `ROLE_BROKERAGE` desk. What remains of D16 is the product
question above, not an engineering gap.

**Closed by D29, and listed here because this table said otherwise until 2026-08-31:** the `D22`
`deliveryMode` default now refuses rather than guessing; `D27`'s shared topic set is separated by
`healthconnect.topics.prefix`; and `D25`'s SSE endpoint exists at `GET /api/stream`. A table of open
items that keeps closed ones is worse than no table — it is read as current.

Engineering items genuinely open, none of them blocked on anyone:

| # | What | Why not now |
|---|---|---|
| D29 | `sendMsg()` is the one prototype write still in memory — it mutates `THREADS` and invents a reply on a timer | Messaging's write surface is larger than booking's or reviewing's, and phase 5 stopped at the two that exercise D22/D28/D21 |
| D29 | An event published to Kafka is not asserted to arrive **on the wire** as SSE data | `MarketplaceStreamFramingIT` now covers the real-port connection with a real token; the data frame still does not arrive in a RANDOM_PORT context while it does reliably under MOCK, and that difference is not yet understood. Verified outside the suite by `verify-prototype-live.mjs --writes` |
| — | *(closed)* The prototype was finally loaded in a browser, served same-origin from the quality vhost at `/prototype` | It found `₵NaN` on every profile and browse card within seconds — see below |
| D13 | `deploy-prod.sh` rebuilds and re-pushes at the same SHA, overwriting the images CI built and verified | `--no-build` exists; which one a production deploy should use is a decision, not an oversight |
| D14 | `deploy-prod.sh --dry-run` prints `✓ authenticated to ghcr.io` and `✓ host reachable` unconditionally, while both operations are skipped | A dry run that implies a check it never made is the same class of false confidence D14 exists to prevent |
| D13 | `deploy-prod.sh`'s header and spec §12 say the github channel produces `ghcr.io/<owner>/healthconnect-<service>`; the code produces `hc-market-<service>` | `sync-appendices.sh` cannot catch it — the appendix faithfully reproduces the script's own stale header |
| D19 | Search is `contains()` in Java over every card | By D19's own terms the threshold is ~200 professionals. There are 18 |
