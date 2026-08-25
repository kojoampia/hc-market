# HealthConnect Marketplace — implementation spec

*Refactored from `marketplace-demo.md` (the clickable prototype build notes) into a buildable specification.*

**Stack:** JHipster 9.2.0 · Java 25 · Spring Boot 4.0.7 · PostgreSQL 17 (MongoDB on the gateway) · Kafka · Consul
**Status:** partly built. The prototype is the UX contract, this document is the backend contract.
**Date:** 10 August 2026 · **amended** 24 August 2026 — see `decisions.md`

> **Read `decisions.md` first.** Twelve questions this document left open or contradicted itself on
> have been answered, and several of the answers change what is written below. Where this document
> and `decisions.md` disagree, `decisions.md` is newer and wins. Where either disagrees with the
> code, the code wins.
>
> Build status: `gateway`, `catalog`, `booking`, `messaging` and `payout` are scaffolded and compile
> on Java 25. **`catalog` is the only service wired up** — seeded, serving spec §6's public reads,
> with the derived rating verified against SQL for all 18 professionals.

---

## 1. What changed in the refactor

The prototype (`Abofonsa_BridgeCare_Marketplace.html`) is a single self-contained file where every entity lives in a JavaScript array and every figure is computed at render time. That property is the thing worth preserving: **ratings, earnings and payout totals were derived, never stored, so they could not drift.** This specification carries that rule into the persistence layer rather than dropping it the moment there is a database.

| Prototype | Becomes |
|---|---|
| `PROS`, `CATS`, in-file services and availability | `catalog` service — PostgreSQL, published through the gateway |
| `REVIEWS` array, `p.rating` computed on load | `catalog` service — reviews stored, **rating derived** in a read model |
| `BOOKINGS`, `PRO_REQUESTS`, `PRO_SCHEDULE`, `PRO_HISTORY` | `booking` service — one `booking` aggregate with a status lifecycle |
| `THREADS`, `NOTIFICATIONS` | `messaging` service |
| `COMMISSION`, `PAYOUT_LAG`, receipt and payout tables | `payout` service — derived from completed bookings |
| Seeded LCG that generated 256 sessions | `demo/seed-data.json`, loaded only under `spring.profiles.active=test,dev` |
| `setRole()` topbar switch | Real JWT roles: `ROLE_CUSTOMER`, `ROLE_PROFESSIONAL`, `ROLE_ADMIN`, `ROLE_OPERATOR` |
| `toast()` after an action | Kafka domain event → `messaging` → notification row |

---

## 2. Decisions taken

| Question | Answer |
|---|---|
| Application shape | **JHipster microservices + gateway.** A gateway plus four domain services, each owning its schema. Kafka is the inter-service backbone. |
| Authentication | **JHipster JWT.** `ROLE_CUSTOMER`, `ROLE_PROFESSIONAL`, `ROLE_ADMIN`, `ROLE_OPERATOR`. No external IdP in v1. |
| Frontend | **API-only.** JHipster generates no SPA. The single-file prototype remains the design reference and the acceptance target for the API. |
| Production runtime | **Docker Compose over SSH**, with a registry channel switch and automatic rollback. |
| Seed data | Extracted to `demo/seed-data.json`, loaded only under the `test,dev` profile pair. |
| Money | Stored as **minor units** (`long`, pesewas) with an explicit ISO currency code. Never `double`. |

---

## 3. Version note — read before scaffolding

**Resolved.** JHipster **9.2.0** is installed and generates Spring Boot **4.0.7** natively; no BOM
surgery is needed. But it decides the Boot generation with one rule —
`springBoot4 = !(databaseTypeSql && reactive)` — and a JHipster gateway is *always* reactive. A SQL
gateway therefore drops silently to Boot 3.5.15 while the SQL microservices get 4.0.7. The gateway
runs on **MongoDB** so the whole estate lands on Boot 4; see `decisions.md` D1.

`javaVersion` is **not a valid JDL config key** in 9.2.0 and there is no CLI flag for it — it is set
in each app's `.yo-rc.json` and the app regenerated.

Language level: Java 25, `--release 25`, preview features off. Records for DTOs and events, sealed interfaces for the booking state machine, virtual threads enabled (`spring.threads.virtual.enabled=true`) since every service is I/O-bound.

---

## 4. Service topology

| Service | Port | Database | Owns |
|---|---|---|---|
| `consul` | 8500 | — | Service discovery and config (**not** the JHipster Registry — `decisions.md` D5) |
| `healthconnect-gateway` | 8080 | **MongoDB** | Routing, JWT issue/verify, rate limiting, user accounts |
| `healthconnect-catalog` | 8081 | `hc_catalog` | Categories, professionals, services, availability, reviews |
| `healthconnect-booking` | 8082 | `hc_booking` | Requests, bookings, appointments, completed sessions |
| `healthconnect-messaging` | 8083 | `hc_messaging` | Threads, messages, notifications |
| `healthconnect-payout` | 8084 | `hc_payout` | Commission, receipts, monthly payouts |

Kafka (KRaft, no ZooKeeper) on 9092. One PostgreSQL 17 instance per *domain* service — separate
instances rather than separate schemas, so a service can be moved to its own host without a
migration. The gateway is the exception: it stores only user accounts, and runs MongoDB so that the
estate can be uniformly Spring Boot 4 (§3).

Discovery is **Consul**, matching `hc-admin`, `hc-patient` and `hc-professional`. Nothing listens on
8761.

```
                    ┌─────────────┐
   prototype ──────▶│   gateway   │────── JWT, routing, rate limit
   (or any client)  └──────┬──────┘
                           │ REST
        ┌──────────────┬───┴────────┬───────────────┐
        ▼              ▼            ▼               ▼
   ┌─────────┐   ┌──────────┐  ┌───────────┐  ┌──────────┐
   │ catalog │   │ booking  │  │ messaging │  │  payout  │
   └────┬────┘   └────┬─────┘  └─────┬─────┘  └────┬─────┘
        │             │              │             │
        └─────────────┴──────┬───────┴─────────────┘
                             ▼
                    Kafka  healthconnect.*
```

---

## 5. Domain model (JDL)

Written as JDL files so each can be regenerated independently. **The authoritative versions are in
`jdl/` — `gateway.jdl`, `catalog.jdl`, `booking.jdl`, `messaging.jdl`, `payout.jdl`.** The blocks
below are the design rationale; where they differ from `jdl/`, the files win, and the differences
are called out inline.

### 5.1 `catalog.jdl`

```jdl
application {
  config { baseName healthconnectCatalog, applicationType microservice,
           serverPort 8081, databaseType sql, prodDatabaseType postgresql,
           devDatabaseType postgresql, authenticationType jwt,
           messageBroker kafka, serviceDiscoveryType eureka, buildTool maven }
  entities Category, Professional, ServiceOffering, AvailabilitySlot, Review
}

enum DeliveryMode { IN_PERSON, ONLINE, HOME_VISIT }
enum VerificationState { UNVERIFIED, PENDING, VERIFIED, SUSPENDED }

entity Category {
  code String required unique maxlength(32)
  name String required
  blurb String maxlength(400)
  icon String
  sortOrder Integer required
}

entity Professional {
  reference String required unique          // p1 … p18 in the seed
  userLogin String required unique          // links to the gateway account
  displayName String required
  headline String required maxlength(160)
  speciality String required
  city String required
  countryCode String required maxlength(2)
  yearsPractising Integer min(0)
  verification VerificationState required
  insured Boolean required
  policeClearance Boolean required
  responseMinutes Integer
  rebookRatePct Integer min(0) max(100)
  bio TextBlob
  languages String                          // comma-separated, small and stable
  deliveryModes String                      // AMENDED: comma-separated DeliveryMode names.
                                            // Browse filters on `mode` but the original model gave
                                            // it nowhere to live.
  initials String maxlength(4)              // AMENDED: the prototype's duotone avatar
  avatarGradientFrom String maxlength(7)
  avatarGradientTo String maxlength(7)
  publishedAt Instant
}

// AMENDED: credentials and highlights are child rows, not comma-separated strings, because the data
// defeats that idiom -- "BSc Nutrition & Dietetics, University of Ghana" contains the separator.
entity Credential { label String required maxlength(160) sortOrder Integer required }
entity Highlight  { label String required maxlength(160) sortOrder Integer required }

entity ServiceOffering {
  reference String required unique
  name String required
  durationMinutes Integer min(0)
  priceMinor Long required min(0)           // pesewas — never a float
  currency String required maxlength(3)
  description String maxlength(500)
  active Boolean required
  sortOrder Integer
}

entity AvailabilitySlot {
  slotDate LocalDate required
  slotTime String required maxlength(5)     // "07:00"
  taken Boolean required
}

entity Review {
  reference String required unique
  customerLogin String required             // AMENDED: decisions.md D8
  authorName String required
  authorInitials String maxlength(4)
  stars Integer required min(1) max(5)
  publishedOn LocalDate required
  body TextBlob required
  professionalReply TextBlob
  bookingReference String required unique   // proves the review is earned; unique = one per booking
}

relationship OneToMany {
  Category{professionals} to Professional{category required}
  Professional{services} to ServiceOffering{professional required}
  Professional{availability} to AvailabilitySlot{professional required}
  Professional{reviews} to Review{professional required}
}
```

**Derived, not stored.** `Professional` has no `rating` or `reviewCount` column. Both come from a read model:

```sql
CREATE VIEW professional_rating AS
SELECT professional_id,
       ROUND(AVG(stars)::numeric, 1) AS rating,
       COUNT(*)                      AS review_count
FROM review GROUP BY professional_id;
```

Backed by a `@Immutable` entity and served on the professional DTO. If review volume ever makes the view too slow, it becomes a materialised view refreshed on `review.published` — the column still never appears on `professional`. This is the single most important rule inherited from the prototype: a rating that disagrees with its reviews is a bug the schema should make impossible.

### 5.2 `booking.jdl`

```jdl
// AMENDED (decisions.md D7): ACCEPTED removed -- it was unreachable, and the diagram below has
// always sent accept straight to CONFIRMED. NO_SHOW kept, and given the transition it lacked.
enum BookingStatus { REQUESTED, DECLINED, RESCHEDULE_PROPOSED,
                     CONFIRMED, COMPLETED, CANCELLED, NO_SHOW }
enum CancelledBy { CUSTOMER, PROFESSIONAL, PLATFORM }

entity Booking {
  reference String required unique
  customerLogin String required
  customerName String required
  professionalRef String required
  serviceRef String required
  serviceName String required               // denormalised: a receipt must not
  priceMinor Long required min(0)           // change when a price is edited
  currency String required maxlength(3)
  scheduledDate LocalDate required
  scheduledTime String required maxlength(5)
  deliveryMode DeliveryMode required
  status BookingStatus required
  customerNote String maxlength(1000)
  onBehalfOf String maxlength(120)          // "someone I care for"
  visitAddress String maxlength(400)
  careSummaryShared Boolean required
  raisedAt Instant required
  respondedAt Instant
  completedAt Instant
  cancelledAt Instant
  cancelledBy CancelledBy
  cancellationReason String maxlength(400)
  lateCancellation Boolean
  reviewed Boolean required
}

entity BookingStatusChange {
  fromStatus BookingStatus
  toStatus BookingStatus required
  actor String required
  occurredAt Instant required
  note String maxlength(400)
}

relationship OneToMany { Booking{history} to BookingStatusChange{booking required} }
```

One aggregate covers what the prototype kept in four arrays. `PRO_REQUESTS` is `status = REQUESTED`, `PRO_SCHEDULE` is `CONFIRMED`, `PRO_HISTORY` is `COMPLETED`, and the customer's four tabs are queries over the same table. The state machine is a sealed interface with explicit transitions; `BookingStatusChange` is the append-only audit that the prototype had no room for.

**Transition rules**

```
REQUESTED ──accept──▶ CONFIRMED ──complete──▶ COMPLETED
     │                    │
     ├──decline──▶ DECLINED├──no-show──▶ NO_SHOW
     ├──propose──▶ RESCHEDULE_PROPOSED ──accept──▶ CONFIRMED
     └──cancel───▶ CANCELLED ◀──cancel── CONFIRMED
```

The topic for accepting is still `healthconnect.booking.accepted`: it names the **act**, not the
resulting state, and its payload carries `status = CONFIRMED`.

Cancellation inside 24 hours sets `lateCancellation = true`, which the payout service reads to raise a 50% fee to the professional. Everything else is free.

### 5.3 `messaging.jdl`

**AMENDED — the aggregate is `Conversation`, not `Thread`.** A JHipster entity named `Thread`
generates `net.jojoaddison.domain.Thread`, which shadows `java.lang.Thread` for every unqualified
use inside that package. The REST surface is unaffected: the endpoints are still `/api/threads`.

```jdl
enum Direction { CUSTOMER_TO_PROFESSIONAL, PROFESSIONAL_TO_CUSTOMER, SYSTEM }

entity Conversation {
  reference String required unique
  customerLogin String required
  professionalRef String required
  bookingReference String
  lastMessageAt Instant
}
entity Message {
  direction Direction required
  body TextBlob required
  sentAt Instant required
  readAt Instant
}
entity Notification {
  recipientLogin String required
  kind String required
  body String required maxlength(400)
  raisedAt Instant required
  readAt Instant
  deepLink String
}
relationship OneToMany { Thread{messages} to Message{thread required} }
```

### 5.4 `payout.jdl`

```jdl
enum PayoutStatus { OPEN, IN_PROGRESS, PAID, FAILED }

entity BrokerageConfig {
  commissionRate BigDecimal required min(0) max(1)   // 0.12
  payoutLagDays Integer required                     // 3
  freeCancellationHours Integer required             // 24
  lateCancellationPct BigDecimal required            // 0.50
  effectiveFrom Instant required
}
entity Ledger {                       // one row per completed or late-cancelled booking
  bookingReference String required unique
  professionalRef String required
  professionalLogin String required   // AMENDED: the ownership check needs something local to
                                      // match the JWT subject against — same reason Review gained
                                      // customerLogin. See decisions.md D12.
  deliveryMode DeliveryMode required  // AMENDED: denormalised for the by-format breakdown, exactly
  serviceRef String                   // as grossMinor already is
  serviceName String
  grossMinor Long required
  commissionMinor Long required
  netMinor Long required
  currency String required maxlength(3)
  earnedOn LocalDate required
  payout Payout
}
entity Payout {
  reference String required unique    // PAY-202607-AM
  professionalRef String required
  periodStart LocalDate required
  periodEnd LocalDate required
  grossMinor Long required
  commissionMinor Long required
  netMinor Long required
  currency String required maxlength(3)
  status PayoutStatus required
  settledOn LocalDate
  bankReference String
}
relationship ManyToOne { Ledger{payout} to Payout{entries} }
```

`BrokerageConfig` is versioned by `effectiveFrom`, so changing the commission rate never rewrites history — a booking is priced against the config in force when it completed. The prototype's hard-coded `COMMISSION = 0.12` could not express that.

**Derived, not stored.** Monthly earnings, gross-vs-net, sessions by service, sessions by format, lifetime totals, average session value and repeat rate are all SQL aggregates over `Ledger` joined to `Booking`. No `professional.total_earnings` column exists. Month-to-date comparisons use the **same slice of days** in the previous month, exactly as the prototype's dashboard does, so a partial month never reads as a collapse.

---

## 6. API surface

Every endpoint below exists because a prototype screen needs it. Public reads need no token; everything else is role-scoped at the gateway.

### Public / customer

| Method | Path | Screen |
|---|---|---|
| `GET` | `/api/categories` | Discover — category tiles with counts |
| `GET` | `/api/professionals` | Browse — faceted: `category`, `speciality`, `mode`, `city`, `maxPriceMinor`, `minRating`, `verifiedOnly`, `q`, `sort`, `page`, `size` |
| `GET` | `/api/professionals/facets` | Browse — the live counts beside each filter |
| `GET` | `/api/professionals/count` | Smoke test and seed verification |
| `GET` | `/api/professionals/{ref}` | Profile — with services, rating, verification |
| `GET` | `/api/professionals/{ref}/availability?from&to` | Profile and booking wizard step 2 |
| `GET` | `/api/professionals/{ref}/reviews?page&size` | Profile — paginated reviews |
| `POST` | `/api/bookings` | Wizard step 4 — creates `REQUESTED` |
| `GET` | `/api/bookings/mine?status` | My bookings — the four tabs |
| `POST` | `/api/bookings/{ref}/reschedule` | Reschedule modal |
| `POST` | `/api/bookings/{ref}/cancel` | Cancel modal — returns the fee that will apply |
| `GET` | `/api/bookings/{ref}/receipt` | Receipt modal — gross, commission, total |
| `POST` | `/api/reviews` | Review modal — rejected unless a `COMPLETED` booking backs it |
| `GET` | `/api/threads` · `/api/threads/{ref}` | Messages |
| `POST` | `/api/threads/{ref}/messages` | Send |
| `GET` | `/api/notifications` · `POST /api/notifications/read` | Bell menu |
| `GET`/`POST`/`DELETE` | `/api/favourites` | Saved list |

### Professional workspace (`ROLE_PROFESSIONAL`, scoped to the caller)

| Method | Path | Screen |
|---|---|---|
| `GET` | `/api/pro/overview` | Overview — the four stat tiles, both charts, next up |
| `GET` | `/api/pro/requests` | Requests inbox |
| `POST` | `/api/pro/requests/{ref}/accept` · `/decline` · `/propose` | The three actions |
| `GET` | `/api/pro/schedule?from&to&mode&q` | Schedule — grouped by day |
| `GET`/`POST`/`PUT` | `/api/pro/services` | Services editor |
| `POST` | `/api/pro/services/{ref}/publish` · `/hide` | Live / hidden toggle |
| `GET` | `/api/pro/earnings?months=7` | Earnings — MTD, gross-vs-net, by format |
| `GET` | `/api/pro/payouts` | Payout table |
| `GET` | `/api/pro/reviews` · `POST /api/pro/reviews/{ref}/reply` | Reviews and public replies |
| `GET`/`PUT` | `/api/pro/profile` | Listing editor |
| `GET`/`PUT` | `/api/pro/availability` | Working hours |

**Built.** All eleven professional endpoints answer, split by which service owns the data:
`overview`, `earnings` and `payouts` in **payout**; `requests` and `schedule` in **booking**;
`services`, `profile`, `availability` and `reviews` in **catalog**. None takes a professional
parameter — the caller comes from the JWT, and in catalog the ownership is *in the query* rather
than a check afterwards, so a reference that is not yours simply is not found.

`overview` is the one endpoint that spans services: its "next up" card reads booking's schedule.
That call has a **2 s timeout** and reports `nextUpAvailable` separately from `nextUp`, so a booking
outage costs one card rather than the screen — and "nothing is booked" stays distinguishable from
"could not ask".

**Contract rule.** Chart endpoints return the *rows*, not a rendered series. `/api/pro/earnings` returns per-month `{month, sessions, grossMinor, commissionMinor, netMinor}` — the client draws either the chart or the table view from the same payload, which is exactly how the prototype's chart/table toggle stays honest.

---

## 7. Kafka

Eight topics, three partitions each, keyed by aggregate reference so per-booking ordering holds. `healthconnect.` prefix; Avro or JSON Schema in the registry; consumers are idempotent on `eventId`.

| Topic | Producer | Consumers | Carries |
|---|---|---|---|
| `booking.requested` | booking | messaging | Notify the professional, open a thread if none exists |
| `booking.accepted` | booking | messaging, catalog | Notify customer; mark the availability slot taken |
| `booking.declined` | booking | messaging, catalog | Notify customer; release the slot |
| `booking.cancelled` | booking | messaging, catalog, payout | Notify; release slot; raise a late fee if inside 24h |
| `booking.completed` | booking | payout, messaging | Write the ledger entry; prompt for a review |
| `review.published` | catalog | messaging | Notify the professional; refresh the rating read model |
| `payout.settled` | payout | messaging | Notify the professional that money moved |
| `notification.raised` | any | messaging | Generic fan-in for anything without its own topic |

**Transactional outbox.** Services write the domain change and an `outbox_event` row in one transaction; a Debezium-style poller publishes and marks sent. Without this, a booking can be accepted while the notification is silently lost — the failure mode a prototype never has to think about and a marketplace cannot afford.

Event envelope:

```json
{
  "eventId": "018f...uuid",
  "type": "healthconnect.booking.accepted",
  "occurredAt": "2026-08-10T09:14:22Z",
  "aggregateRef": "b14",
  "actor": "akosua.mensah",
  "payload": { "bookingRef":"b14", "professionalRef":"p1", "customerLogin":"kojo.ampia.addison",
               "scheduledDate":"2026-08-12", "scheduledTime":"10:00", "priceMinor":15000, "currency":"GHS" }
}
```

---

## 8. Seed data

`deploy/demo/seed-data.json` — one file, **277 KB**, extracted from the prototype's in-memory arrays
with no hand-editing. It is **regenerated, never edited**, by `deploy/demo/extract-seed.mjs`, which
asserts the prototype's own figures and refuses to write on a mismatch.

| Section | Rows |
|---|---|
| `categories` | 4 |
| `professionals` (with nested `services`, `availability`) | 18 professionals · 52 services · 21 days of slots each |
| `reviews` | 63 |
| `customers` | **61** — see below |
| `bookings` | 13 |
| `requests` | 5 |
| `appointments` | 12 |
| `sessions` | 256 completed, ₵81,620 gross |
| `threads` | 4 conversations |
| `notifications` | 4 |
| `brokerage` | commission 0.12, payout lag 3 days, free cancellation 24h, late fee 50% |

Ratings are deliberately **absent** from the file. They are computed from `reviews` at read time by
the `professional_rating` view, so the seed cannot ship an inconsistency.

**Customers is 61, not 20.** The prototype describes exactly one customer in full (`ME`) plus flat
lists of names. The earlier extraction built customer rows from `CLIENTS` alone, which left the 3
clients naming requests and the 40 distinct review authors with no row at all — harmless until
`Review.customerLogin` became required (D8), at which point they are 43 dangling references. The
extractor now takes the union of everyone who acts as a customer anywhere, and asserts that no
reference dangles.

**What the extractor invents, and by what rule.** The prototype's reviews carry no booking id and
never did, so `Review.bookingReference` is minted as `"b-" + review id`. Those references match no
row in `bookings`; they exist so the column can be non-null and unique. `POST /api/reviews` enforces
the real rule at write time against live bookings. Every rule is recorded in `$meta.derivationRules`
in the output file, so the invention is visible rather than buried in a script.

### Loader

```java
@Component
@Profile({"test & dev"})                       // both, never one alone
@ConditionalOnProperty(name = "healthconnect.seed.enabled", havingValue = "true")
class SeedDataLoader implements ApplicationRunner {

    private final Path seedFile;               // healthconnect.seed.file
    private final CatalogSeeder catalogSeeder; // each service implements its own slice

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (catalogSeeder.alreadySeeded()) {   // idempotent: safe on every restart
            log.info("seed already present, skipping");
            return;
        }
        var seed = mapper.readValue(seedFile.toFile(), SeedFile.class);
        require(seed.meta().name().equals("healthconnect-demo-seed"), "not a HealthConnect seed file");
        catalogSeeder.load(seed);              // booking/messaging/payout read their own sections
        log.info("seeded {} professionals, {} reviews", seed.professionals().size(), seed.reviews().size());
    }
}
```

- Guarded by **both** the profile pair and an explicit property. Two independent locks, because "demo data appeared in production" is the kind of incident that ends a brokerage.
- `prod` sets `healthconnect.seed.enabled=false` and the file is not baked into the production image at all.
- Each service reads only its own top-level sections, so the same file loads five times without collision.
- `POST /management/healthconnect/reseed` truncates and reloads. Registered **only** under `test & dev` — it does not exist as an endpoint in a production build.
- Dates in the seed are anchored to `2026-08-10`. The loader shifts every date by `today − 2026-08-10` unless `healthconnect.seed.anchor-dates=true`, so a demo run three months from now still shows "tomorrow" and a live month-to-date figure.

---

## 9. Security

- JHipster JWT. `ROLE_CUSTOMER` on registration; `ROLE_PROFESSIONAL` added by an admin once verification passes; `ROLE_ADMIN` for the brokerage desk.
- Ownership is enforced in the service layer, not by hiding buttons: `/api/pro/**` resolves the professional from the token and refuses any reference that is not the caller's.
- The care summary a customer shares with a professional is scoped per booking (`careSummaryShared`) and returns conditions, allergies and medications only. Clinical notes and reports are never exposed through this API — the prototype's scope note is a hard boundary, not copy.
- Review integrity: `POST /api/reviews` requires a `COMPLETED` booking for that customer and professional, unreviewed. There is no admin endpoint to delete a review; the only response is a public reply.
- Rate limits at the gateway: 60 rpm anonymous, 600 rpm authenticated, 10 rpm on booking creation.
- Secrets never reach the command line — see the Jib credential handling in Appendix B.

---

## 10. Repository layout

**AMENDED (`decisions.md` D6).** Directories are named by role, as in every sibling product, and
there is **no aggregator `pom.xml` and no Maven reactor** — each app is a standalone Maven project
built with its own `./mvnw`, exactly as `hc-admin`, `hc-patient` and `hc-professional` are built.

```
hc-market/
├── gateway/          # healthconnectGateway — MongoDB, reactive
├── catalog/          # healthconnectCatalog — the only wired service so far
├── booking/          # healthconnectBooking     scaffolded, compiles, unwired
├── messaging/        # healthconnectMessaging   scaffolded, compiles, unwired
├── payout/           # healthconnectPayout      scaffolded, compiles, unwired
├── jdl/
│   └── gateway.jdl  catalog.jdl  booking.jdl  messaging.jdl  payout.jdl
├── deploy/
│   ├── deploy-dev.sh  deploy-prod.sh
│   ├── demo/
│   │   ├── seed-data.json        # regenerated, never edited
│   │   └── extract-seed.mjs      # regenerates it from the prototype
│   └── docker/
│       ├── docker-compose.dev.yml    # consul, kafka, mongo, 4x postgres, 5 services
│       └── docker-compose.prod.yml   # image refs read from .env
└── docs/
    ├── healthconnect-marketplace.md            # this file
    ├── decisions.md                            # the answers that amend it
    ├── marketplace-demo.md
    └── Abofonsa_BridgeCare_Marketplace.html    # UX contract
```

---

## 11. Dev / test deployment — `deploy-dev.sh`

```bash
./deploy-dev.sh up                       # build, start the estate, seed, verify
./deploy-dev.sh up --no-build            # start from existing images
./deploy-dev.sh up --with-tests          # run `clean verify` before each image (slow)
./deploy-dev.sh up --services catalog,booking
./deploy-dev.sh reseed                   # wipe + reload seed-data.json
./deploy-dev.sh status | logs | restart
./deploy-dev.sh down --clean             # also drop volumes
```

**Published ports are overridable** (`decisions.md` D10), because six products on one workstation
collide by construction — this one already has something on 8080 and 8500. The script reads the
same variables it passes to compose, so the health gates always follow the mapping:

```bash
export HC_GATEWAY_PORT=18200 HC_CATALOG_PORT=18201 HC_BOOKING_PORT=18202 \
       HC_MESSAGING_PORT=18203 HC_PAYOUT_PORT=18204 \
       HC_CONSUL_PORT=18500  HC_KAFKA_PORT=19092
```

`JWT_BASE64_SECRET` is required and has no default — one signing key across the estate.

What it does, in order:

1. **Preflight** — docker, compose v2, `jq`, and a JDK 25 with a real `javac` (it checks for the
   binary, because the JRE at `java-25-openjdk-amd64` fails silently on incremental builds);
   validates `demo/seed-data.json` is a HealthConnect seed file and prints its counts;
   **refuses to run if `prod` appears in the profile list**.
2. **Build** — each app packaged standalone with `-DskipTests`, then `jib:dockerBuild`. Tests are
   skipped here on purpose (`decisions.md` D9): the Cucumber tests stand up Testcontainers, so
   `clean verify` costs minutes per app and wants a Docker daemon that an image build does not have.
   `--with-tests` runs the full verify.
3. **Infrastructure** — Consul, Kafka, MongoDB for the gateway, one PostgreSQL per domain service;
   waits on `/v1/status/leader`, waits for Kafka to answer `--list`, then creates all eight topics
   idempotently.
4. **Services** — exports the profiles, `HEALTHCONNECT_SEED_ENABLED=true` and the seed mount path,
   starts the five apps, gates on `/management/health` per service with a timeout and dumps the last
   40 log lines on failure.
5. **Seed verification** — compares the row counts the API reports against the counts in the JSON,
   **and checks the first professional's derived rating equals the average of its own reviews in the
   seed**. A mismatch fails the run. The rating check is the only one here that would catch a broken
   `professional_rating` view — the counts would all still pass.
6. **Banner** — gateway URL, Swagger UI, Consul UI, and a note that the prototype is a closed demo
   and cannot be pointed at this estate.

Full script: **Appendix A**. Keep the two in step with `./deploy/sync-appendices.sh`.

---

## 12. Production deployment — `deploy-prod.sh`

Multi-channel by design: the same build lands in either registry, and the channel is recorded in the image label, the host `.env` and the deployment log.

| Channel | Flag | Image | Credentials |
|---|---|---|---|
| **Default** | *(none)* | `docker.jojoaddison.net/healthconnect/<service>:<tag>` | `HC_REGISTRY_USER` / `HC_REGISTRY_TOKEN` |
| **GitHub** | `--channel github` | `ghcr.io/<owner>/healthconnect-<service>:<tag>` | `GHCR_OWNER` / `GHCR_TOKEN` |

GHCR has no nested-path namespaces the way a private Harbor does, so the channel switches the separator (`/` vs `-`) as well as the host. Both channels tag three ways: the version, the short git SHA, and `latest`.

```bash
./deploy-prod.sh --tag 1.4.0                                  # default channel
./deploy-prod.sh --channel github --tag 1.4.0                 # ghcr.io
./deploy-prod.sh --tag 1.4.0 --services catalog,booking       # partial roll
./deploy-prod.sh --tag 1.4.0 --dry-run                        # print everything, change nothing
./deploy-prod.sh --rollback                                   # back to the previous tag
```

Sequence:

1. **Preflight** — resolves the tag from `pom.xml` if not given; warns on a dirty working tree; logs in to the channel registry; proves SSH and remote compose v2 before building anything.
2. **Build** — `mvn -Pprod`, then `jib:build` per service straight to the registry. Credentials go through `JIB_TO_USERNAME` / `JIB_TO_PASSWORD` in the environment, never as `-D` flags, so they cannot appear in `ps`, in CI logs, or in `--dry-run` output.
3. **Deploy** — uploads `docker-compose.prod.yml` and a generated `.env`, **keeping the outgoing `.env` as `.env.previous`** (this is what makes rollback possible), authenticates the host, pulls, then `up -d`.
4. **Health gate** — polls `/management/health/readiness` inside each container for up to 240s.
5. **Smoke test** — hits the public gateway for a live professional count and checks `/management/info` reports the tag just deployed.
6. **On success** — appends `timestamp, tag, sha, channel` to `deployments.log` on the host.
7. **On failure of either gate** — automatically rolls back to `.env.previous` and re-gates; a failing rollback exits non-zero and says so plainly rather than pretending.

`prod` sets `HEALTHCONNECT_SEED_ENABLED=false` and `SPRING_PROFILES_ACTIVE=prod`. The seed file is not in the production image.

Full script: **Appendix B**.

---

## 13. Open questions

The four architectural gaps are settled in §2. These are still open, roughly in the order they will block work:

1. ~~**Generator version.**~~ **ANSWERED** — JHipster 9.2.0, Spring Boot 4.0.7, Java 25, gateway on
   MongoDB so the estate is uniformly Boot 4. See §3 and `decisions.md` D1–D2.
2. **Payments.** The prototype holds money and releases it after the session. Which provider actually does that — Paystack, Flutterwave, Hubtel, a bank file? Escrow versus authorise-and-capture changes the `payout` model materially.
3. **Professional onboarding and KYC.** Who verifies credentials and police clearance, and against what register? Manual admin queue in v1, or an integration?
4. **Online sessions.** "Online" is a delivery mode with a promised video link one hour before. Which provider, and does the platform host the room or just relay a link?
5. **Notification transport.** In-app rows exist. Email, SMS and push all have a channel table in Ghana — which ones ship in v1, and through whom?
6. **Search.** PostgreSQL full-text is enough for 18 professionals and probably for 500. At what number does Elasticsearch earn its operational cost?
7. **Availability model.** The seed carries explicit slots. Real professionals think in recurring rules plus exceptions. Recurrence in v1, or slots generated from a rule engine?
8. **Time zones.** Everything is currently Africa/Accra with no offset. Does the platform ever serve a client or professional outside GMT, and if so, whose local time is authoritative on a booking?
9. **Multi-currency.** `currency` is on every money field but only `GHS` is used. Real requirement, or should it be dropped to keep the model honest?
10. **Data protection.** Ghana's Data Protection Act applies to the care summary. Where does data live, how long is it retained, and what does a deletion request do to a booking history that a payout ledger depends on?
11. **Disputes.** The prototype promises a brokerage desk resolving disputes in five working days. That is a workflow, a role and a set of states nobody has specified yet.
12. **Observability.** JHipster ships Micrometer. Which backend — Prometheus/Grafana, or something already running on `docker.jojoaddison.net`?

---

## 14. Verification checklist for the first build

Carried over from the prototype's verification discipline, adapted to a backend. Status as of
24 August 2026, after the catalog slice.

**Verified, by running it:**

- [x] Seed extraction reproduces the prototype exactly: 18 professionals, 52 services, 63 reviews,
      256 sessions, ₵81,620 gross — asserted by `extract-seed.mjs`, which refuses to write otherwise.
- [x] Seed loads into PostgreSQL 17 and the API reports the seed's own counts back: 18 and 63.
- [x] A professional's rating equals `AVG(stars)` over its reviews to one decimal — checked for
      **all 18**, against SQL, not just `p1`.
- [x] `customerLogin` does not leak into the public review DTO.
- [x] Unknown professional reference returns 404; public reads need no token.
- [x] `deploy-prod.sh --dry-run` on both channels prints the correct image coordinates
      (`docker.jojoaddison.net/healthconnect/catalog` vs `ghcr.io/<owner>/healthconnect-catalog`)
      and leaks no token.
- [x] `prod` profile refuses to seed even with `HEALTHCONNECT_SEED_ENABLED=true` — confirmed against
      a separate database that stayed empty.

- [x] **`./deploy-dev.sh up` reaches "all services healthy" with no manual step**, then passes seed
      verification. Run end to end: Consul up, Kafka up, 8 topics created, all five apps healthy,
      `professionals 18/18`, `reviews 63/63`, `p1 rating 4.7 (seed average 4.7)`. Four infrastructure
      defects were found and fixed in the process — see `decisions.md` D11.
- [x] All five services register in Consul and the gateway routes to them.
- [x] Public reads work **through the gateway** with no token, while `POST /api/reviews` still
      returns 401.

- [x] **Lifetime gross from `/api/pro/earnings` equals ₵81,620** — `8162000` pesewas over 256
      ledger rows, the prototype's figure unchanged by the migration. The per-month rows sum to the
      lifetime totals, and both breakdowns (by format, by service) sum to 256 sessions.
- [x] `gross − commission = net` holds on every one of the 256 ledger rows.
- [x] `/api/pro/earnings` takes no professional parameter: the login comes from the JWT subject, so
      a second professional's token returns `0`, and a token signed with the wrong key returns 401.

**Not yet verified — the services these depend on are scaffolded but unwired:**
- [x] **Booking a slot, accepting it, completing it, then reviewing it moves the rating and creates
      exactly one ledger row.** Automated as `deploy/verify-cycle.sh`; crosses catalog, booking,
      Kafka and payout. Last run: rating 4.7 (7) → 4.3 (8) on a one-star review, ledger 257 → 258,
      gross +28000, commission 3360, second review refused with 409, booking flagged reviewed.
- [x] **Killing Kafka mid-accept leaves the booking accepted and the notification pending in the
      outbox, delivered on recovery.** Automated as `deploy/verify-outbox-recovery.sh`, which stops
      the broker, accepts a booking, asserts the accept succeeded and the event is UNSENT with no
      notification raised, then restarts the broker and asserts the row drains and the notification
      arrives **exactly once**.
- [ ] A deliberately failing deploy rolls back and the previous tag serves traffic. *(Needs a host.)*

---

## Appendix A — `deploy/deploy-dev.sh`

*Embedded verbatim from `deploy/deploy-dev.sh`. These are the same bytes in two places: if you edit
one, re-embed the other, or this document starts describing a script that does not exist. Verify
with the `diff` one-liner in `../CLAUDE.md`.*

```bash
#!/usr/bin/env bash
# ==============================================================================
#  HealthConnect Marketplace — dev / test deployment
#
#  Brings up the whole microservice estate locally: Consul, Kafka, MongoDB for the gateway,
#  one PostgreSQL per domain service, the gateway and the four domain services, then loads
#  demo/seed-data.json through the test,dev seed loader.
#
#  Usage:
#     ./deploy-dev.sh up                      # build, start everything, seed
#     ./deploy-dev.sh up --no-build           # start from existing images
#     ./deploy-dev.sh up --services catalog,booking
#     ./deploy-dev.sh reseed                  # wipe + reload seed-data.json only
#     ./deploy-dev.sh status | logs | restart | down
#     ./deploy-dev.sh down --clean            # also drop volumes (data loss)
#
#  Options:
#     --profiles <list>   Spring profiles           (default: test,dev)
#     --services <list>   Comma-separated subset    (default: all)
#     --seed-file <path>  Seed JSON                 (default: demo/seed-data.json)
#     --no-build          Skip the Maven/Jib build
#     --with-tests        Run `clean verify` before each image (slow: Testcontainers per app)
#     --clean             Remove volumes on down / rebuild from scratch on up
#     --timeout <secs>    Per-service health gate   (default: 180)
#
#  Layout note (decisions.md D6): the five apps are SIBLING DIRECTORIES of this script's parent,
#  each a standalone Maven project with its own ./mvnw — there is no aggregator pom and no Maven
#  reactor, exactly as in hc-admin, hc-patient and hc-professional.
#
#  Discovery is CONSUL (decisions.md D5), not the JHipster Registry. There is no service on 8761.
# ==============================================================================
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"       # the workspace holding gateway/ catalog/ …
cd "$DEPLOY_DIR"

# ------------------------------------------------------------------ defaults --
PROFILES="test,dev"
SEED_FILE="$DEPLOY_DIR/demo/seed-data.json"
COMPOSE_FILE="$DEPLOY_DIR/docker/docker-compose.dev.yml"
PROJECT="healthconnect-dev"
ALL_SERVICES=(gateway catalog booking messaging payout)
SERVICES=("${ALL_SERVICES[@]}")
DO_BUILD=1
DO_CLEAN=0
RUN_TESTS=0
TIMEOUT=180
# Java 25 needs a JDK with a compiler. /usr/lib/jvm/java-25-openjdk-amd64 is a JRE and its failure
# mode is an incremental build that silently passes — see the workspace guide.
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/jdk-25.0.2-oracle-x64}"

case "${1:-}" in -h|--help) sed -n '2,32p' "$0"; exit 0 ;; esac
# the first bare word is the command; anything starting with "-" is an option
if [[ $# -gt 0 && "$1" != -* ]]; then COMMAND="$1"; shift; else COMMAND="up"; fi

# Host ports, overridable so several products can run on one workstation without colliding.
# These MUST match the defaults in docker/docker-compose.dev.yml, which reads the same variables.
declare -A PORTS=(
  [gateway]="${HC_GATEWAY_PORT:-8080}"
  [catalog]="${HC_CATALOG_PORT:-8081}"
  [booking]="${HC_BOOKING_PORT:-8082}"
  [messaging]="${HC_MESSAGING_PORT:-8083}"
  [payout]="${HC_PAYOUT_PORT:-8084}"
)
CONSUL_PORT="${HC_CONSUL_PORT:-8500}"
export HC_GATEWAY_PORT="${PORTS[gateway]}" HC_CATALOG_PORT="${PORTS[catalog]}" \
       HC_BOOKING_PORT="${PORTS[booking]}" HC_MESSAGING_PORT="${PORTS[messaging]}" \
       HC_PAYOUT_PORT="${PORTS[payout]}" HC_CONSUL_PORT="$CONSUL_PORT" 

# --------------------------------------------------------------------- output --
c_reset=$'\033[0m'; c_dim=$'\033[2m'; c_b=$'\033[1m'
c_ok=$'\033[32m'; c_warn=$'\033[33m'; c_err=$'\033[31m'; c_info=$'\033[36m'
log()  { printf '%s▸%s %s\n' "$c_info" "$c_reset" "$*"; }
ok()   { printf '%s✓%s %s\n' "$c_ok" "$c_reset" "$*"; }
warn() { printf '%s!%s %s\n' "$c_warn" "$c_reset" "$*"; }
die()  { printf '%s✗ %s%s\n' "$c_err" "$*" "$c_reset" >&2; exit 1; }
step() { printf '\n%s%s%s\n' "$c_b" "$*" "$c_reset"; }
trap 'die "failed at line $LINENO: ${BASH_COMMAND}"' ERR

# ---------------------------------------------------------------- arg parsing --
while [[ $# -gt 0 ]]; do
  case "$1" in
    --profiles)  PROFILES="$2"; shift 2 ;;
    --services)  IFS=',' read -r -a SERVICES <<< "$2"; shift 2 ;;
    --seed-file) SEED_FILE="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"; shift 2 ;;
    --no-build)   DO_BUILD=0; shift ;;
    --with-tests) RUN_TESTS=1; shift ;;
    --clean)     DO_CLEAN=1; shift ;;
    --timeout)   TIMEOUT="$2"; shift 2 ;;
    -h|--help)   sed -n '2,32p' "$0"; exit 0 ;;
    *)           die "unknown option: $1 (try --help)" ;;
  esac
done

for s in "${SERVICES[@]}"; do
  [[ " ${ALL_SERVICES[*]} " == *" $s "* ]] || die "unknown service '$s' (known: ${ALL_SERVICES[*]})"
done

# ------------------------------------------------------------- prerequisites --
require() { command -v "$1" >/dev/null 2>&1 || die "$1 is required but not on PATH"; }
java_major() {                       # robust: ignores "Picked up JAVA_TOOL_OPTIONS" noise
  local out major
  out="$("$JAVA_HOME/bin/java" -version 2>&1 || true)"
  major="$(printf '%s\n' "$out" | grep -E 'version "' | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  [[ "$major" =~ ^[0-9]+$ ]] || major=0
  printf '%s' "$major"
}
preflight() {
  step "Preflight"
  require docker; require curl; require jq
  docker compose version >/dev/null 2>&1 || die "docker compose v2 plugin is required"
  docker info >/dev/null 2>&1 || die "docker daemon is not reachable"

  if [[ $DO_BUILD -eq 1 ]]; then
    [[ -x "$JAVA_HOME/bin/javac" ]] || die "no javac at $JAVA_HOME — that is a JRE, not a JDK. Set JAVA_HOME to a real JDK 25."
    local jv; jv="$(java_major)"
    (( jv >= 25 )) || die "Java 25+ required for Spring Boot 4 (found ${jv/0/unknown} at $JAVA_HOME)"
    for s in "${SERVICES[@]}"; do
      [[ -x "$ROOT_DIR/$s/mvnw" ]] || die "$ROOT_DIR/$s/mvnw not found — has the app been generated?"
    done
    ok "Java $jv at $JAVA_HOME"
  fi

  [[ -f "$COMPOSE_FILE" ]] || die "compose file not found: $COMPOSE_FILE"
  [[ -f "$SEED_FILE"    ]] || die "seed file not found: $SEED_FILE"
  jq -e '.["$meta"].name == "healthconnect-demo-seed"' "$SEED_FILE" >/dev/null \
    || die "$SEED_FILE does not look like a HealthConnect seed file"
  ok "seed file OK — $(jq '.professionals|length' "$SEED_FILE") professionals, $(jq '.reviews|length' "$SEED_FILE") reviews, $(jq '.sessions|length' "$SEED_FILE") historic sessions"

  case ",$PROFILES," in
    *,prod,*) die "refusing to run the dev script with the 'prod' profile — use deploy-prod.sh" ;;
  esac
  ok "profiles: $PROFILES"
}

compose() { docker compose -p "$PROJECT" -f "$COMPOSE_FILE" "$@"; }

wait_http() {                        # wait_http <name> <url> <timeout>
  local name="$1" url="$2" limit="$3" waited=0
  printf '  %s… ' "$name"
  until curl -fsS --max-time 3 "$url" >/dev/null 2>&1; do
    (( waited += 3 )); sleep 3
    if (( waited >= limit )); then printf '%stimeout%s\n' "$c_err" "$c_reset"; return 1; fi
    printf '.'
  done
  printf '%sup%s (%ss)\n' "$c_ok" "$c_reset" "$waited"
}

# ------------------------------------------------------------------- actions --
# Each app is a standalone Maven project. No reactor, no -pl: we cd into each in turn, exactly as
# every sibling product in this workspace is built.
#
# TESTS ARE SKIPPED WHEN BUILDING IMAGES, deliberately, matching hc-patient/deploy/docker/*.Dockerfile
# ("`./mvnw verify` on a developer machine or in CI"). These apps are generated with Cucumber and
# their tests stand up Testcontainers, so `clean verify` costs minutes per app and needs a Docker
# daemon -- packaging an image is the wrong place to discover that. Run tests explicitly:
#
#     ./deploy-dev.sh up --with-tests      # clean verify before each image
#     (cd ../catalog && ./mvnw clean verify)
build() {
  step "Build"
  export JAVA_HOME
  for s in "${SERVICES[@]}"; do
    if (( RUN_TESTS )); then
      log "verifying $s (Testcontainers -- slow)"
      ( cd "$ROOT_DIR/$s" && ./mvnw -q -ntp clean verify -Pdev ) || die "tests failed for $s"
    else
      log "packaging $s"
      ( cd "$ROOT_DIR/$s" && ./mvnw -q -ntp clean package -DskipTests -Pdev ) || die "build failed for $s"
    fi
    log "jib:dockerBuild healthconnect/$s:local"
    ( cd "$ROOT_DIR/$s" && ./mvnw -q -ntp jib:dockerBuild -DskipTests -Pdev \
        -Djib.to.image="healthconnect/$s:local" ) || die "image build failed for $s"
  done
  ok "images built$( (( RUN_TESTS )) && printf ' (tests passed)' || printf ' (tests skipped)')"
}

infra_up() {
  step "Infrastructure"
  local dbs=()
  for s in "${SERVICES[@]}"; do dbs+=("${s}-db"); done
  compose up -d consul kafka "${dbs[@]}"
  wait_http "consul" "http://localhost:${CONSUL_PORT}/v1/status/leader" 120 || die "Consul did not start"
  log "waiting for Kafka"
  local waited=0
  until docker compose -p "$PROJECT" -f "$COMPOSE_FILE" exec -T kafka \
        /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list >/dev/null 2>&1; do
    (( waited += 3 )); sleep 3
    (( waited >= 120 )) && die "Kafka did not become ready"
  done
  ok "Kafka ready"
  log "ensuring topics"
  for t in booking.requested booking.accepted booking.declined booking.cancelled \
           booking.completed review.published payout.settled notification.raised; do
    docker compose -p "$PROJECT" -f "$COMPOSE_FILE" exec -T kafka \
      /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
      --create --if-not-exists --topic "healthconnect.$t" --partitions 3 --replication-factor 1 >/dev/null
  done
  ok "8 topics present"
}

apps_up() {
  step "Services"
  export SPRING_PROFILES_ACTIVE="$PROFILES"
  SEED_HOST_PATH="$(dirname "$SEED_FILE")"
  export SEED_HOST_PATH
  : "${JWT_BASE64_SECRET:?set JWT_BASE64_SECRET (one key across the estate — see the workspace guide)}"
  export JWT_BASE64_SECRET
  compose up -d "${SERVICES[@]}"
  local failed=0
  for s in "${SERVICES[@]}"; do
    wait_http "$s" "http://localhost:${PORTS[$s]}/management/health" "$TIMEOUT" || failed=1
  done
  (( failed )) && { warn "some services did not come up — showing the last 40 lines"; compose logs --tail=40; die "startup failed"; }
  ok "all services healthy"
}

# Compares what the API reports against what the seed file contains. A mismatch fails the run —
# the same integrity discipline the prototype applied to its charts, applied to the loader.
verify_seed() {
  step "Seed verification"
  local expect_pro expect_rev
  expect_pro="$(jq '.professionals|length' "$SEED_FILE")"
  expect_rev="$(jq '.reviews|length'       "$SEED_FILE")"
  local got_pro got_rev
  got_pro="$(curl -fsS "http://localhost:${PORTS[catalog]}/api/professionals/count" || echo 0)"
  got_rev="$(curl -fsS "http://localhost:${PORTS[catalog]}/api/reviews/count"       || echo 0)"
  printf '  professionals %s/%s\n  reviews       %s/%s\n' "$got_pro" "$expect_pro" "$got_rev" "$expect_rev"
  [[ "$got_pro" == "$expect_pro" && "$got_rev" == "$expect_rev" ]] \
    || die "seed counts do not match $SEED_FILE"

  # The rule the whole design turns on: a rating must equal the average of its own reviews.
  # Cheap to check, and the only check here that would catch a broken professional_rating view.
  local ref rating avg
  ref="$(jq -r '.professionals[0].ref' "$SEED_FILE")"
  rating="$(curl -fsS "http://localhost:${PORTS[catalog]}/api/professionals/$ref" | jq -r '.card.rating')"
  avg="$(jq -r --arg r "$ref" '[.reviews[]|select(.professionalRef==$r)|.stars] | (add/length*10|round)/10' "$SEED_FILE")"
  printf '  %s rating    %s (seed average %s)\n' "$ref" "$rating" "$avg"
  [[ "$rating" == "$avg" ]] || die "derived rating $rating disagrees with the seed's own reviews ($avg)"
  ok "seed loaded, counts and derived rating consistent"
}

banner() {
  cat <<EOF

$c_b HealthConnect Marketplace — dev estate up$c_reset
  Gateway API      http://localhost:${PORTS[gateway]}
  API docs         http://localhost:${PORTS[gateway]}/swagger-ui/index.html
  Consul UI        http://localhost:${CONSUL_PORT}
  Kafka bootstrap  localhost:9092
  Profiles         $PROFILES
  Seed             $SEED_FILE

$c_dim  The prototype at docs/Abofonsa_BridgeCare_Marketplace.html is a CLOSED demo: it has no
  fetch calls and no API_BASE hook, so it cannot be pointed at this estate. Driving it from the
  live API is unbuilt work, not a configuration step.$c_reset

EOF
}

# --------------------------------------------------------------------- router --
case "$COMMAND" in
  up)
    preflight
    (( DO_CLEAN )) && { warn "--clean: removing volumes"; compose down -v --remove-orphans || true; }
    (( DO_BUILD )) && build
    infra_up
    apps_up
    verify_seed
    banner
    ;;
  reseed)
    preflight
    step "Reseed"
    for s in "${SERVICES[@]}"; do
      [[ $s == gateway ]] && continue
      log "truncating + reloading $s"
      curl -fsS -X POST "http://localhost:${PORTS[$s]}/management/healthconnect/reseed" \
        -H 'Content-Type: application/json' >/dev/null \
        || die "reseed endpoint refused on $s (is it running with test,dev?)"
    done
    verify_seed
    ;;
  restart) preflight; compose restart "${SERVICES[@]}"; for s in "${SERVICES[@]}"; do
             wait_http "$s" "http://localhost:${PORTS[$s]}/management/health" "$TIMEOUT" || true; done ;;
  status)  compose ps ;;
  logs)    compose logs -f --tail=120 "${SERVICES[@]}" ;;
  down)
    if (( DO_CLEAN )); then warn "removing containers AND volumes"; compose down -v --remove-orphans;
    else compose down --remove-orphans; fi
    ok "stopped" ;;
  *) die "unknown command '$COMMAND' (up | reseed | restart | status | logs | down)" ;;
esac
```

---

## Appendix B — `deploy/deploy-prod.sh`

*Embedded verbatim from `deploy/deploy-prod.sh`. Same warning as Appendix A.*

```bash
#!/usr/bin/env bash
# ==============================================================================
#  HealthConnect Marketplace — production deployment
#
#  Builds immutable images, pushes them to a registry CHANNEL, then rolls the
#  Docker Compose stack on the production host over SSH with a health gate and
#  automatic rollback to the previously deployed tag.
#
#  Channels:
#     (default)          docker.jojoaddison.net/healthconnect/<service>:<tag>
#     --channel github   ghcr.io/<owner>/healthconnect-<service>:<tag>
#
#  Usage:
#     ./deploy-prod.sh --tag 1.4.0
#     ./deploy-prod.sh --channel github --tag 1.4.0
#     ./deploy-prod.sh --tag 1.4.0 --services catalog,booking
#     ./deploy-prod.sh --rollback                  # back to the previous tag
#     ./deploy-prod.sh --tag 1.4.0 --dry-run       # print, change nothing
#
#  Options:
#     --channel <name>   default | github            (default: default)
#     --tag <version>    Image tag                   (default: from pom.xml)
#     --host <target>    SSH target                  (default: $HC_PROD_HOST)
#     --path <dir>       Remote stack directory      (default: /srv/healthconnect)
#     --services <list>  Comma-separated subset      (default: all)
#     --no-build         Reuse images already built locally
#     --no-push          Build and deploy without pushing (host must reach them)
#     --rollback         Redeploy the previous tag recorded on the host
#     --dry-run          Print every command instead of running it
#     --yes              Skip the confirmation prompt (for CI)
#
#  Required environment:
#     HC_PROD_HOST       e.g. deploy@app-01.jojoaddison.net
#     HC_REGISTRY_USER / HC_REGISTRY_TOKEN     for docker.jojoaddison.net
#     GHCR_OWNER / GHCR_TOKEN                  for the github channel
# ==============================================================================
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"       # holds gateway/ catalog/ booking/ messaging/ payout/
cd "$DEPLOY_DIR"
# Java 25 needs a JDK with a compiler. java-25-openjdk-amd64 is a JRE and fails silently on an
# incremental build -- see the workspace guide.
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/jdk-25.0.2-oracle-x64}"

# ------------------------------------------------------------------ defaults --
CHANNEL="default"
TAG=""
HOST="${HC_PROD_HOST:-}"
REMOTE_PATH="/srv/healthconnect"
ALL_SERVICES=(gateway catalog booking messaging payout)
SERVICES=("${ALL_SERVICES[@]}")
DO_BUILD=1
DO_PUSH=1
DO_ROLLBACK=0
DRY_RUN=0
ASSUME_YES=0
COMPOSE_TEMPLATE="$DEPLOY_DIR/docker/docker-compose.prod.yml"
HEALTH_TIMEOUT=240

c_reset=$'\033[0m'; c_b=$'\033[1m'; c_dim=$'\033[2m'
c_ok=$'\033[32m'; c_warn=$'\033[33m'; c_err=$'\033[31m'; c_info=$'\033[36m'
log()  { printf '%s▸%s %s\n' "$c_info" "$c_reset" "$*"; }
ok()   { printf '%s✓%s %s\n' "$c_ok" "$c_reset" "$*"; }
warn() { printf '%s!%s %s\n' "$c_warn" "$c_reset" "$*"; }
die()  { printf '%s✗ %s%s\n' "$c_err" "$*" "$c_reset" >&2; exit 1; }
step() { printf '\n%s%s%s\n' "$c_b" "$*" "$c_reset"; }
run()  { if (( DRY_RUN )); then printf '%s  [dry-run] %s%s\n' "$c_dim" "$*" "$c_reset"; else "$@"; fi; }
trap 'die "failed at line $LINENO: ${BASH_COMMAND}"' ERR

# ---------------------------------------------------------------- arg parsing --
while [[ $# -gt 0 ]]; do
  case "$1" in
    --channel)   CHANNEL="$2"; shift 2 ;;
    --tag)       TAG="$2"; shift 2 ;;
    --host)      HOST="$2"; shift 2 ;;
    --path)      REMOTE_PATH="$2"; shift 2 ;;
    --services)  IFS=',' read -r -a SERVICES <<< "$2"; shift 2 ;;
    --no-build)  DO_BUILD=0; shift ;;
    --no-push)   DO_PUSH=0; shift ;;
    --rollback)  DO_ROLLBACK=1; shift ;;
    --dry-run)   DRY_RUN=1; shift ;;
    --yes|-y)    ASSUME_YES=1; shift ;;
    -h|--help)   sed -n '2,40p' "$0"; exit 0 ;;
    *)           die "unknown option: $1 (try --help)" ;;
  esac
done

# ---------------------------------------------------------- channel resolution --
case "$CHANNEL" in
  default)
    REGISTRY_HOST="docker.jojoaddison.net"
    IMAGE_PREFIX="docker.jojoaddison.net/healthconnect"
    IMAGE_SEP="/"
    REGISTRY_USER="${HC_REGISTRY_USER:-}"
    REGISTRY_TOKEN="${HC_REGISTRY_TOKEN:-}"
    CRED_HINT="HC_REGISTRY_USER / HC_REGISTRY_TOKEN"
    ;;
  github|ghcr)
    CHANNEL="github"
    REGISTRY_HOST="ghcr.io"
    GHCR_OWNER="${GHCR_OWNER:-jojoaddison}"
    IMAGE_PREFIX="ghcr.io/${GHCR_OWNER}/healthconnect"
    IMAGE_SEP="-"
    REGISTRY_USER="${GHCR_USER:-$GHCR_OWNER}"
    REGISTRY_TOKEN="${GHCR_TOKEN:-}"
    CRED_HINT="GHCR_OWNER / GHCR_TOKEN"
    ;;
  *) die "unknown channel '$CHANNEL' (default | github)" ;;
esac
image_for() { printf '%s%s%s:%s' "$IMAGE_PREFIX" "$IMAGE_SEP" "$1" "$2"; }

for s in "${SERVICES[@]}"; do
  [[ " ${ALL_SERVICES[*]} " == *" $s "* ]] || die "unknown service '$s' (known: ${ALL_SERVICES[*]})"
done

# ------------------------------------------------------------------ preflight --
require() { command -v "$1" >/dev/null 2>&1 || die "$1 is required but not on PATH"; }
java_major() {                       # robust: ignores "Picked up JAVA_TOOL_OPTIONS" noise
  local out major
  out="$("$JAVA_HOME/bin/java" -version 2>&1 || true)"
  major="$(printf '%s\n' "$out" | grep -E 'version "' | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  [[ "$major" =~ ^[0-9]+$ ]] || major=0
  printf '%s' "$major"
}
# There is no aggregator pom (decisions.md D6), so the version comes from the gateway's own pom.
# Every app is released together and shares a tag; if they ever diverge, pass --tag explicitly.
resolve_tag() {
  [[ -n "$TAG" ]] && return
  [[ -x "$ROOT_DIR/gateway/mvnw" ]] || die "no $ROOT_DIR/gateway/mvnw — pass --tag explicitly"
  TAG="$(cd "$ROOT_DIR/gateway" && ./mvnw -q -ntp -Dexec.executable=echo -Dexec.args='${project.version}' \
        --non-recursive exec:exec 2>/dev/null | tail -1 | tr -d '[:space:]')"
  [[ -n "$TAG" ]] || die "could not resolve the version — pass --tag explicitly"
  TAG="${TAG%-SNAPSHOT}"
}
preflight() {
  step "Preflight — channel '$CHANNEL' → $REGISTRY_HOST"
  require docker; require ssh; require git; require curl
  docker info >/dev/null 2>&1 || die "docker daemon is not reachable"
  [[ -n "$HOST" ]] || die "no target host — pass --host or export HC_PROD_HOST"
  [[ -f "$COMPOSE_TEMPLATE" ]] || die "missing $COMPOSE_TEMPLATE"

  # This workspace level is NOT a git repository -- each app is its own repo, and hc-market may
  # not be under version control at all. Probe the gateway repo rather than the cwd, and treat
  # "no repo" as a warning, not a failure.
  if git -C "$ROOT_DIR/gateway" rev-parse --git-dir >/dev/null 2>&1; then
    if [[ -n "$(git -C "$ROOT_DIR/gateway" status --porcelain 2>/dev/null)" ]]; then
      warn "gateway working tree is dirty -- the deployed image will not match any commit"
      (( ASSUME_YES )) || { read -r -p "  continue anyway? [y/N] " a; [[ "$a" == [yY] ]] || exit 1; }
    fi
    GIT_SHA="$(git -C "$ROOT_DIR/gateway" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  else
    warn "no git repository under $ROOT_DIR/gateway -- image provenance will read 'unknown'"
    GIT_SHA="unknown"
  fi

  if (( DO_PUSH )); then
    [[ -n "$REGISTRY_TOKEN" ]] || die "registry credentials missing — set $CRED_HINT"
    log "docker login $REGISTRY_HOST as $REGISTRY_USER"
    (( DRY_RUN )) || printf '%s' "$REGISTRY_TOKEN" \
      | docker login "$REGISTRY_HOST" -u "$REGISTRY_USER" --password-stdin >/dev/null \
      || die "registry login failed for $REGISTRY_HOST"
    ok "authenticated to $REGISTRY_HOST"
  fi

  log "checking ssh to $HOST"
  (( DRY_RUN )) || ssh -o BatchMode=yes -o ConnectTimeout=8 "$HOST" 'docker compose version >/dev/null' \
    || die "cannot reach $HOST over ssh, or docker compose v2 is missing there"
  ok "host reachable"
}

confirm() {
  (( ASSUME_YES )) && return 0
  (( DRY_RUN ))    && return 0
  printf '\n%sDeploying%s  tag %s%s%s  ·  channel %s%s%s  ·  host %s%s%s\n' \
    "$c_b" "$c_reset" "$c_b" "$TAG" "$c_reset" "$c_b" "$CHANNEL" "$c_reset" "$c_b" "$HOST" "$c_reset"
  printf '  services: %s\n' "${SERVICES[*]}"
  read -r -p "  proceed? [y/N] " a; [[ "$a" == [yY] ]] || { warn "aborted"; exit 1; }
}

# --------------------------------------------------------------------- build --
# Each app is a standalone Maven project -- no reactor, no -pl. We cd into each in turn, exactly
# as every sibling product in this workspace is built.
build_and_push() {
  step "Build"
  export JAVA_HOME
  [[ -x "$JAVA_HOME/bin/javac" ]] || die "no javac at $JAVA_HOME -- that is a JRE, not a JDK"
  local jv; jv="$(java_major)"
  (( jv >= 25 )) || die "Java 25+ required (found ${jv/0/unknown} at $JAVA_HOME)"
  # credentials go to Jib through the environment, never on the command line,
  # so they cannot leak into `ps`, CI logs or --dry-run output
  export JIB_TO_USERNAME="$REGISTRY_USER" JIB_TO_PASSWORD="$REGISTRY_TOKEN"
  for s in "${SERVICES[@]}"; do
    local img; img="$(image_for "$s" "$TAG")"
    log "verifying $s"
    run bash -c "cd '$ROOT_DIR/$s' && ./mvnw -q -ntp clean verify -Pprod"
    log "packaging $s -> $img"
    run bash -c "cd '$ROOT_DIR/$s' && ./mvnw -q -ntp jib:build -Pprod \
      -Djib.to.image='$img' \
      -Djib.to.tags='$TAG,$GIT_SHA,latest' \
      -Djib.container.labels='org.opencontainers.image.revision=$GIT_SHA,org.opencontainers.image.version=$TAG,net.jojoaddison.channel=$CHANNEL'"
  done
  unset JIB_TO_USERNAME JIB_TO_PASSWORD
  ok "images published to $REGISTRY_HOST"
}
build_local_only() {
  step "Build (local, no push)"
  export JAVA_HOME
  for s in "${SERVICES[@]}"; do
    run bash -c "cd '$ROOT_DIR/$s' && ./mvnw -q -ntp clean verify -Pprod"
    run bash -c "cd '$ROOT_DIR/$s' && ./mvnw -q -ntp jib:dockerBuild -Pprod -Djib.to.image='$(image_for "$s" "$TAG")'"
  done
  ok "images built locally"
}

# -------------------------------------------------------------------- deploy --
render_env() {
  cat <<EOF
# generated by deploy-prod.sh — do not edit on the host
HC_TAG=$TAG
HC_GIT_SHA=$GIT_SHA
HC_CHANNEL=$CHANNEL
HC_IMAGE_PREFIX=$IMAGE_PREFIX
HC_IMAGE_SEP=$IMAGE_SEP
HC_REGISTRY_HOST=$REGISTRY_HOST
SPRING_PROFILES_ACTIVE=prod
HEALTHCONNECT_SEED_ENABLED=false
EOF
}

remote_deploy() {
  step "Deploy → $HOST:$REMOTE_PATH"
  run ssh "$HOST" "mkdir -p '$REMOTE_PATH'"

  log "uploading compose stack and env"
  if (( DRY_RUN )); then
    printf '%s  [dry-run] scp %s %s:%s/docker-compose.yml%s\n' "$c_dim" "$COMPOSE_TEMPLATE" "$HOST" "$REMOTE_PATH" "$c_reset"
    render_env | sed 's/^/    /'
  else
    scp -q "$COMPOSE_TEMPLATE" "$HOST:$REMOTE_PATH/docker-compose.yml"
    render_env | ssh "$HOST" "cat > '$REMOTE_PATH/.env.next'"
    ssh "$HOST" "cd '$REMOTE_PATH' && { [ -f .env ] && cp .env .env.previous || true; } && mv .env.next .env"
  fi

  if (( DO_PUSH )); then
    log "authenticating the host to $REGISTRY_HOST"
    (( DRY_RUN )) || printf '%s' "$REGISTRY_TOKEN" \
      | ssh "$HOST" "docker login '$REGISTRY_HOST' -u '$REGISTRY_USER' --password-stdin >/dev/null"
    log "pulling $TAG"
    run ssh "$HOST" "cd '$REMOTE_PATH' && docker compose pull ${SERVICES[*]}"
  fi

  log "rolling services"
  run ssh "$HOST" "cd '$REMOTE_PATH' && docker compose up -d --remove-orphans ${SERVICES[*]}"
}

health_gate() {
  step "Health gate (${HEALTH_TIMEOUT}s)"
  if (( DRY_RUN )); then printf '%s  [dry-run] skipped%s\n' "$c_dim" "$c_reset"; return 0; fi
  local waited=0 bad
  while :; do
    bad=""
    for s in "${SERVICES[@]}"; do
      # `docker compose exec ... curl` cannot work: the Jib images ship no curl and no wget.
      # bash IS present, so readiness is probed over bash's /dev/tcp instead.
      ssh "$HOST" "cd '$REMOTE_PATH' && docker compose exec -T $s bash -c \
        'exec 3<>/dev/tcp/localhost/8080 && printf \"GET /management/health/readiness HTTP/1.0\\r\\n\\r\\n\" >&3 && grep -q UP <&3'" \
        >/dev/null 2>&1 || bad+=" $s"
    done
    [[ -z "$bad" ]] && { ok "all services report READY"; return 0; }
    (( waited += 10 )); sleep 10
    (( waited >= HEALTH_TIMEOUT )) && { warn "still unhealthy:$bad"; return 1; }
    printf '  waiting%s (%ss)\n' "$bad" "$waited"
  done
}

smoke_test() {
  step "Smoke test"
  if (( DRY_RUN )); then printf '%s  [dry-run] skipped%s\n' "$c_dim" "$c_reset"; return 0; fi
  local base="${HC_PUBLIC_URL:-https://health.jojoaddison.net}"
  local n
  n="$(curl -fsS --max-time 10 "$base/api/professionals/count" || echo "")"
  [[ "$n" =~ ^[0-9]+$ && "$n" -gt 0 ]] || { warn "catalogue smoke test failed (got '${n:-nothing}')"; return 1; }
  ok "catalogue answering — $n published professionals"
  curl -fsS --max-time 10 "$base/management/info" | grep -q "$TAG" \
    && ok "gateway reports version $TAG" || warn "gateway did not report $TAG in /management/info"
}

rollback() {
  step "Rollback"
  local prev
  prev="$(ssh "$HOST" "cd '$REMOTE_PATH' && grep -m1 '^HC_TAG=' .env.previous 2>/dev/null | cut -d= -f2" || true)"
  [[ -n "$prev" ]] || die "no previous deployment recorded on $HOST — nothing to roll back to"
  warn "rolling back to $prev"
  run ssh "$HOST" "cd '$REMOTE_PATH' && cp .env.previous .env && docker compose pull ${SERVICES[*]} && docker compose up -d ${SERVICES[*]}"
  TAG="$prev"
  health_gate && ok "rolled back to $prev" || die "rollback to $prev is also unhealthy — manual intervention required"
}

record_success() {
  (( DRY_RUN )) && return 0
  ssh "$HOST" "cd '$REMOTE_PATH' && printf '%s\t%s\t%s\t%s\n' \
    \"\$(date -u +%FT%TZ)\" '$TAG' '$GIT_SHA' '$CHANNEL' >> deployments.log"
}

# --------------------------------------------------------------------- router --
if (( DO_ROLLBACK )); then
  HOST="${HOST:-${HC_PROD_HOST:-}}"; [[ -n "$HOST" ]] || die "no target host"
  rollback; exit 0
fi

resolve_tag
preflight
confirm
if   (( DO_BUILD && DO_PUSH )); then build_and_push
elif (( DO_BUILD ));            then build_local_only
else                                 warn "--no-build: deploying whatever $TAG already exists"
fi
remote_deploy

if health_gate && smoke_test; then
  record_success
  step "Done"
  ok "HealthConnect $TAG live on $HOST via the '$CHANNEL' channel ($IMAGE_PREFIX)"
  printf '  rollback with: %s./deploy-prod.sh --rollback --host %s%s\n' "$c_dim" "$HOST" "$c_reset"
else
  warn "deployment did not pass its gates"
  rollback
  exit 1
fi
```

---

## Appendix C — `demo/seed-data.json` shape

```jsonc
{
  "$meta":      { "name": "healthconnect-demo-seed", "version": "1.0.0",
                  "demoToday": "2026-08-10", "loadedByProfiles": ["test","dev"],
                  "note": "Ratings are NOT stored. They are derived from reviews at read time." },
  "brokerage":  { "commissionRate": 0.12, "payoutLagDays": 3, "currency": "GHS",
                  "freeCancellationHours": 24, "lateCancellationPct": 0.5 },
  "deliveryModes": ["In person","Online","Home visit"],
  "cities":     ["Accra","Tema","Kumasi","Takoradi","Cape Coast"],
  "slotTimes":  ["07:00","08:30","10:00","11:30","13:00","14:30","16:00","17:30","19:00"],

  "categories": [ { "code":"FITNESS", "name":"Fitness & Movement", "blurb":"…",
                    "specialities":["Personal Trainer","Yoga Instructor", … ] } ],

  "professionals": [ {
      "ref":"p1", "userLogin":"akosua.mensah", "displayName":"Akosua Mensah",
      "headline":"Registered Nutritionist · Metabolic health",
      "categoryCode":"NUTRITION", "speciality":"Nutritionist", "city":"Accra", "countryCode":"GH",
      "deliveryModes":["In person","Online","Home visit"], "yearsPractising":9,
      "verified":true, "insured":true, "policeClearance":true,
      "languages":["English","Twi","Ga"], "responseMinutes":22, "rebookRatePct":68,
      "credentials":[ … ], "bio":"…", "highlights":[ … ],
      "services":[ { "ref":"s1a", "name":"Nutrition assessment (first visit)",
                     "durationMinutes":60, "priceMinor":28000, "currency":"GHS",
                     "description":"…", "active":true, "sortOrder":1 } ],
      "availability":[ { "date":"2026-08-10", "slots":["08:30","13:00","16:00"] } ]
  } ],

  "reviews":       [ { "ref":"r1", "professionalRef":"p1", "authorName":"Yaa Boakye",
                       "stars":5, "publishedOn":"2026-07-28", "body":"…",
                       "professionalReply":"…" } ],
  "customers":     [ { "ref":"c1", "userLogin":"kojo.ampia.addison", "displayName":"Kojo Ampia-Addison",
                       "email":"…", "city":"Accra", "careSummary":"…" } ],
  "bookings":      [ { "ref":"b1", "customerRef":"c1", "professionalRef":"p1", "serviceRef":"s1b",
                       "scheduledDate":"2026-08-12", "scheduledTime":"10:00",
                       "deliveryMode":"Online", "status":"CONFIRMED", "priceMinor":15000 } ],
  "requests":      [ … 5 … ],
  "appointments":  [ … 12 … ],
  "sessions":      [ … 256 completed, ₵81,620 gross … ],
  "threads":       [ { "ref":"t1", "customerRef":"c1", "professionalRef":"p1",
                       "messages":[ { "seq":1, "direction":"PROFESSIONAL_TO_CUSTOMER",
                                      "sentAt":"2026-08-08T09:12:00Z", "body":"…" } ] } ],
  "notifications": [ … 4 … ]
}
```

Money is minor units throughout (`28000` = ₵280.00). `priceMinor` on a service is the price the
customer pays; the 12% brokerage fee is inside it, not added to it — the same convention the
prototype's receipt and payout table use.
