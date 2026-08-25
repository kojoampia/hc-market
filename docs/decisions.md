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

## Still open — deferred, not blocking the slice

Spec §13 items 2–12, unchanged and none of them blocking v1:

| # | Question | Working assumption for now |
|---|---|---|
| 2 | Payments provider, escrow vs authorise-and-capture | none wired; `payout` is a derived ledger only |
| 3 | Professional onboarding / KYC | manual admin queue implied; nothing built |
| 4 | Online sessions — video provider | delivery mode only, no room or link |
| 5 | Notification transport | in-app rows only; no email/SMS/push |
| 6 | Search backend | PostgreSQL full-text; ample for 18 |
| 7 | Availability — slots vs recurrence rules | explicit slots, exactly as seeded |
| 8 | Time zones | Africa/Accra throughout, no offset |
| 9 | Multi-currency | `GHS` only; the `currency` column stays |
| 10 | Ghana DPA — residency, retention, deletion | not addressed |
| 11 | Disputes workflow | not specified, not built |
| 12 | Observability backend | workspace already runs OTLP-push Grafana/Mimir/Loki/Tempo |

One further gap, not in §13: the spec header now reads **"Kafka, SSE"**, but neither §6 nor §7
defines an SSE endpoint, and the prototype's live messaging is a simulated local reply. Real-time
transport is undefined and unbuilt.
