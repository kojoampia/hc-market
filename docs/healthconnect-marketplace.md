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

**The broker and Consul are borrowed, never bundled — `decisions.md` D27.** No deployment in this
repository declares either one: dev, quality and production all point at a broker and a Consul that
some other thing owns, by container name, over a network they declare `external`. Locally and on
the quality box that is `hc-infra`'s shared plane on `hcnet`; in production it is the host's own
`infranet` infrastructure, which every sibling product borrows too. Bring the shared plane up before
either stack; both refuse to start without it, because the failure it prevents is silent — a service
whose broker is unreachable starts, serves and reports healthy while everything it publishes goes
nowhere.

Discovery is **Consul**, matching `hc-admin`, `hc-patient` and `hc-professional`. Nothing listens on
8761. Consul **registers; it does not route**: every gateway route in every environment is static,
so a shared catalogue holding four products' services can never mint a route into somebody else's
running estate.

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
  zoneId String required maxlength(64)      // ADDED, D21/D29 — an IANA name, e.g. Africa/Accra
}

// ADDED, decisions.md D16/D29. Who verified this professional, when, and on what evidence.
// APPEND-ONLY: there is no endpoint to edit or delete a row, for the same reason there is none to
// delete a Review. `Professional.verification` is strictly the projection of the latest row here,
// and the desk writes both in ONE transaction — a deliberate exception to "derived, never stored",
// because Browse filters on the column and the 18 seeded professionals have no history to derive
// from. `evidenceRef` names a document held elsewhere; there is no document store here.
entity VerificationReview {
  reference String required unique
  decision VerificationState required
  reviewer String required                  // the deciding login, from the JWT and never the body
  reviewedAt Instant required
  evidenceRef String maxlength(200)
  note String maxlength(1000)
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
  slotTime LocalTime required               // was String(5) — decisions.md D20, D26
  taken Boolean required
}

// ADDED, decisions.md D20/D26. The rule is what a professional edits; the slot is what a customer
// books. Slots stay materialised rows because `taken` needs a row to LOCK — two customers booking
// the same 07:00 must collide on a unique constraint, and with availability computed at read time
// there is nothing to contend on and the double booking is silent.
entity AvailabilityRule {
  weekday Weekday required                  // names match java.time.DayOfWeek exactly
  startTime LocalTime required
  endTime LocalTime required                // EXCLUSIVE: 07:00–11:00 at 60min is four sessions
  slotMinutes Integer required min(5) max(480)
  validFrom LocalDate required
  validUntil LocalDate                      // null = open-ended
  active Boolean required
}

// Named Override, NOT Exception: a JPA entity ending in `Exception` reads as a Throwable wherever
// it appears, and this repo already paid that tax renaming Thread to Conversation.
entity AvailabilityOverride {
  overrideDate LocalDate required
  closed Boolean required                   // true = no sessions; false + window = those hours
  startTime LocalTime
  endTime LocalTime
  note String maxlength(200)
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
  scheduledTime LocalTime required
  zoneId String required maxlength(64)      // ADDED, D21/D29. The zone the two fields above are in,
                                            // captured from the professional at creation. NOT an
                                            // Instant: a zone whose rules change must leave the
                                            // 7 a.m. session at 7 a.m.          // was String(5) — decisions.md D21, D26
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

// ADDED, decisions.md D23/D26. A SEPARATE lifecycle from BookingStatus, deliberately: a booking can
// be disputed and still be completed, so folding disputes into that enum would force one of the two
// facts to be discarded. Hand-written logic lives in DisputeWorkflow, never DisputeService — the
// JDL generates that name and regeneration would replace it silently.
enum DisputeStatus { OPEN, UNDER_REVIEW, RESOLVED, REJECTED }

entity Dispute {
  reference String required unique
  bookingReference String required unique   // one dispute per booking, as a schema guarantee
  raisedBy CancelledBy required
  raisedByLogin String required
  professionalRef String required
  reason String required maxlength(1000)
  status DisputeStatus required
  raisedAt Instant required
  dueBy Instant required                    // the five-working-day promise. RECORDED, NOT ENFORCED:
                                            // there is no scheduler in this estate, so nothing
                                            // escalates on expiry. It sorts the desk queue.
  resolution String maxlength(1000)
  resolvedBy String
  resolvedAt Instant
  refundMinor Long min(0)                   // never negative here; payout applies the sign
  currency String maxlength(3)
}

entity DisputeStatusChange {
  fromStatus DisputeStatus
  toStatus DisputeStatus required
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
  reversalOf String                   // ADDED, decisions.md D23/D26. Set on a compensating entry,
                                      // naming the booking whose earning it reverses.
                                      //
                                      // A reversal CANNOT reuse the original bookingReference —
                                      // that column is unique and its uniqueness is the guard
                                      // against a replayed booking.completed double-crediting a
                                      // professional. So a compensating entry carries the DISPUTE
                                      // reference in bookingReference (also unique, so a replayed
                                      // dispute.resolved cannot double-reverse) and records the
                                      // original here.
                                      //
                                      // Session counts must exclude these rows. A bare count(l)
                                      // reports one MORE session on the day one is reversed, while
                                      // gross falls — two figures moving in opposite directions.
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
| `POST` | `/api/bookings` | Wizard step 4 — creates `REQUESTED`. Price, currency, service name **and `professionalLogin`** come from the catalogue, never from the body — 409 on disagreement, 503 if catalog cannot be asked |
| `GET` | `/api/bookings/mine?status` | My bookings — the four tabs |
| `POST` | `/api/bookings/{ref}/reschedule` | Reschedule modal |
| `POST` | `/api/bookings/{ref}/cancel` | Cancel modal — returns the fee that will apply |
| `GET` | `/api/bookings/{ref}/receipt` | Receipt modal — gross, commission, total |
| `POST` | `/api/reviews` | Review modal — rejected unless a `COMPLETED` booking backs it |
| `GET` | `/api/threads` · `/api/threads/{ref}` | Messages |
| `POST` | `/api/threads/{ref}/messages` | Send |
| `GET` | `/api/notifications` · `POST /api/notifications/read` | Bell menu |
| `GET`/`POST`/`DELETE` | `/api/favourites` | Saved list — **built** |

**Built.** Every customer endpoint in this table now answers. Three notes worth carrying:

- `/api/favourites` is scoped to the caller, replacing the generated CRUD that would have exposed
  every customer's saved list. Saving twice returns 204 rather than an error, guarded by a unique
  constraint on `(customer_login, professional_ref)`.
- `/api/bookings/{ref}/receipt` gets its split from **payout**, at the date the session happened —
  a receipt reprinted after the brokerage changes terms must still say what the customer was told.
  It **fails closed** (503) rather than guessing 12%.
- The customer can now answer a proposed reschedule. `RESCHEDULE_PROPOSED` previously had no exit
  the customer controlled, so a proposed time could be offered with no way to reply.

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
| `GET`/`POST`/`PUT`/`DELETE` | `/api/pro/availability/rules[/{id}]` | Recurring working hours — **added D20/D26** |
| `GET`/`PUT`/`DELETE` | `/api/pro/availability/overrides[/{date}]` | A day that departs from the rules — **added D20/D26** |
| `POST` | `/api/pro/availability/generate?weeks&from` | Materialise rules into bookable slots — **added D20/D26** |

The three availability-rule rows live under `/api/pro/**` **because the generated
`AvailabilityRuleResource` and `AvailabilityOverrideResource` were deleted.** Their unscoped CRUD on
`/api/availability-rules` would have let any authenticated user edit anyone's working hours — the
same disclosure the hand-written `FavouritesResource` exists to prevent.

Generation **never removes a slot that is taken**, including when a day is closed. A booked
appointment is a commitment to a customer; removing one is a cancellation, and cancellations go
through the booking service where they raise an event.

**Built.** All eleven professional endpoints answer, split by which service owns the data:
`overview`, `earnings` and `payouts` in **payout**; `requests` and `schedule` in **booking**;
`services`, `profile`, `availability` and `reviews` in **catalog**. None takes a professional
parameter — the caller comes from the JWT, and in catalog the ownership is *in the query* rather
than a check afterwards, so a reference that is not yours simply is not found.

`overview` is the one endpoint that spans services: its "next up" card reads booking's schedule.
That call has a **2 s timeout** and reports `nextUpAvailable` separately from `nextUp`, so a booking
outage costs one card rather than the screen — and "nothing is booked" stays distinguishable from
"could not ask".

### Verification desk (`ROLE_BROKERAGE`, added D16/D29)

| Method | Path | Who |
|---|---|---|
| `GET` | `/api/desk/professionals/{ref}/verification` | the append-only history, newest first |
| `POST` | `/api/desk/professionals/{ref}/verification` | record a decision — **201**, because it appends |

`ROLE_BROKERAGE` rather than `ROLE_ADMIN`, for the reason the dispute desk has its own authority:
deciding whether someone carries the VERIFIED badge shown publicly beside their name is a narrower
and more consequential power than general administration.

The **reviewer is the JWT subject** and there is no such field on the request. An audit trail whose
actor the caller supplies records nothing. A body that sends one is ignored rather than refused —
JHipster does not fail on unknown properties — and the IT asserts what matters, which is that the
recorded reviewer is the authenticated subject regardless of what was sent.

The decision and `Professional.verification` are written in **one transaction**: the column is
strictly the projection of the latest row, and if the two could be written separately the badge
customers see could disagree with the only record of how it was decided.

**What the badge means: documents seen by a person.** The scope note restricts this marketplace to
non-medical professionals, who are not licensed by a statutory body, so for most listings there is no
register to check against (D16). `evidenceRef` names a document held elsewhere; there is no document
store in this service and D16 does not ask for one.

An empty history is a real answer, not a 404 — every professional seeded before this existed has a
state and nothing behind it.

### Estate-facing (`/internal/**`, added D28)

| Method | Path | Caller |
|---|---|---|
| `GET` | `/internal/professionals/{ref}/login` | **booking**, on every create — who owns this reference |

**Not under `/api/`, and that is the security control.** The gateway's four routes match
`/services/<service>/api/**` and nothing else, so no request from outside can be routed to
`/internal/**` in any environment. There is no service-to-service authentication in this estate —
every service validates JWTs and none holds one of its own — so an unroutable path is what stands in
for one. Widening those predicates back to `/services/<service>/**` publishes this endpoint.

The threat model, stated rather than implied: anything already on the estate's docker network can
read any professional's login, which is the same trust level as being able to reach the databases.
What D28 closes is the **external** caller, who could previously put a booking in someone else's
inbox through a documented public endpoint with an ordinary customer token.

### Disputes (added D23/D26)

| Method | Path | Who |
|---|---|---|
| `GET` | `/api/disputes` | the customer's own disputes |
| `POST` | `/api/disputes/bookings/{bookingRef}` | a customer raises one, against a completed or no-show booking |
| `GET` | `/api/disputes/{reference}` | the customer's own, 404 for anyone else's |
| `GET` | `/api/desk/disputes` | **`ROLE_BROKERAGE`** — the queue, oldest deadline first |
| `POST` | `/api/desk/disputes/{ref}/review` · `/uphold` · `/reject` | the desk's three decisions |

`ROLE_BROKERAGE`, deliberately not `ROLE_ADMIN`: upholding a dispute writes a compensating entry
against a professional's earnings, a narrower and more consequential power than general
administration.

Only **upholding** publishes an event, because only upholding moves money. Payout then writes a
compensating `Ledger` row — negative amounts, never a deletion or an edit of the original — so the
ledger stays append-only and every earnings figure remains a plain aggregate. Session counts exclude
reversals; see the note on `Ledger.reversalOf` in §5.4.

There is **no desk UI here**. D26 stops at the API — the console belongs in `hc-admin`, a separate
repository.

**Contract rule.** Chart endpoints return the *rows*, not a rendered series. `/api/pro/earnings` returns per-month `{month, sessions, grossMinor, commissionMinor, netMinor}` — the client draws either the chart or the table view from the same payload, which is exactly how the prototype's chart/table toggle stays honest.

---

## 7. Kafka

**Nine** topics (eight as specified, plus `dispute.resolved` from D23/D26), three partitions each, keyed by aggregate reference so per-booking ordering holds. `healthconnect.` prefix; Avro or JSON Schema in the registry; consumers are idempotent on `eventId`.

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
| `dispute.resolved` | booking | payout | **Added D23/D26.** Reverse the earning with a compensating ledger entry. Keyed by the BOOKING reference, not the dispute's, so it cannot overtake the booking events it concerns — a consumer that sees "reverse the earning for b-123" before "b-123 completed" has nothing to reverse. Only *upholding* publishes; a rejected dispute changes nothing downstream. |

**Transactional outbox.** Services write the domain change and an `outbox_event` row in one transaction; a Debezium-style poller publishes and marks sent. Without this, a booking can be accepted while the notification is silently lost — the failure mode a prototype never has to think about and a marketplace cannot afford.

**An estate prefix sits in front of every topic name — `decisions.md` D29.** `healthconnect.topics.prefix`
defaults to **empty**, so production and quality use exactly the names in the table above; only
`docker-compose.dev.yml` sets one (`dev.`), making the dev estate's topics `dev.healthconnect.*`.

It exists because D27 left one broker serving the whole estate and hc-market runs on it twice. Topic
names were compiled into the `@KafkaListener` annotations, so dev and quality shared a topic set —
and because their consumer groups differ, *both* received everything either published. Completing a
booking in dev wrote a ledger row in quality.

Two properties of the design above made this a configuration change rather than a rewrite. Consumers
`switch` on the envelope's **`type`**, never on the topic a record arrived by, so no consumer logic
changed — the event type is not the transport address and stays unprefixed. And booking publishes
through the outbox, so the prefix is applied at **send** time rather than when the row is written:
`outbox_event.topic` keeps the logical name, and a row written before a prefix changed still
publishes to the right place afterwards.

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
3. **Infrastructure** — MongoDB for the gateway and one PostgreSQL per domain service, and *only*
   those. The broker and Consul are `hc-infra`'s (D27), so preflight asserts them instead: the
   `hcnet` network exists, both containers are running, Consul has elected a **leader** (it answers
   `/v1/status/leader` before it has one, and every KV read fails until it does), and the broker
   answers `kafka-broker-api-versions.sh`. Then it creates the nine topics idempotently on the
   shared broker — `--if-not-exists` throughout, so topics another stack already made are left
   exactly as they are.
4. **Services** — exports the profiles, `HEALTHCONNECT_SEED_ENABLED=true` and the seed mount path,
   starts the five apps, gates on `/management/health` per service with a timeout and dumps the last
   40 log lines on failure.
5. **Seed verification** — compares the row counts the API reports against the counts in the JSON,
   **and checks the first professional's derived rating equals the average of its own reviews in the
   seed**. A mismatch fails the run. The rating check is the only one here that would catch a broken
   `professional_rating` view — the counts would all still pass.
6. **Banner** — gateway URL, Swagger UI, the shared plane's Consul UI and broker address (labelled
   as `hc-infra`'s, not this stack's), and a note that the prototype is a closed demo and cannot be
   pointed at this estate.

Two things it will not do. It never starts a broker or a Consul — that is D27, and the refusal is
the point. And it warns, without refusing, when the quality stack is running: one broker means one
topic set, topic names are compiled into the `@KafkaListener` annotations, and the two estates'
consumer groups differ — so completing a booking in dev writes a ledger row in quality. Separating
them needs a configurable topic prefix in the four services, which is application work and is not
done.

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

The four architectural gaps are settled in §2. **All twelve questions below have a proposed answer
in `decisions.md` D15–D25.** Each entry there carries the reasoning, what it would cost to build,
and whether it is an engineering call at all.

**Four are no longer proposals.** D26 ratified D20 (availability rules), D22 (currency enforcement),
D23 (disputes) and D25 (observability), and all four are built — the entities are in §5, the
endpoints in §6, and the checklist in §14 records what was verified. The remaining eight stay
recommendations: some wait on a decision that is not engineering's to take (D15, D24), the rest are
listed with the other open engineering items at the end of `decisions.md`.

Three of the questions below are **framed on premises the code contradicts**, and `decisions.md`
opens by correcting them: there is no care summary stored anywhere (Q10), there is no PostgreSQL
full-text search (Q6), and hc-market wires no observability at all (Q12).

1. ~~**Generator version.**~~ **ANSWERED** — JHipster 9.2.0, Spring Boot 4.0.7, Java 25, gateway on
   MongoDB so the estate is uniformly Boot 4. See §3 and `decisions.md` D1–D2.
2. **Payments.** The prototype holds money and releases it after the session. Which provider actually does that — Paystack, Flutterwave, Hubtel, a bank file? Escrow versus authorise-and-capture changes the `payout` model materially.
   → **D15:** provider-managed split, not escrow and not authorise-and-capture — mobile money has no two-phase hold. *Needs a commercial and legal decision.*
3. **Professional onboarding and KYC.** Who verifies credentials and police clearance, and against what register? Manual admin queue in v1, or an integration?
   → **D16:** manual review in `hc-admin`. There is no register for non-medical practitioners, which is inherent to the scope note.
4. **Online sessions.** "Online" is a delivery mode with a promised video link one hour before. Which provider, and does the platform host the room or just relay a link?
   → **D17:** relay a link, never host the room; no recording in v1.
5. **Notification transport.** In-app rows exist. Email, SMS and push all have a channel table in Ghana — which ones ship in v1, and through whom?
   → **D18:** in-app and email in v1, SMS for confirmations, no push (there is no app). WhatsApp is the integration worth costing.
6. **Search.** PostgreSQL full-text is enough for 18 professionals and probably for 500. At what number does Elasticsearch earn its operational cost?
   → **D19:** the premise is wrong — filtering is `contains()` in Java over every card. Move to SQL at ~200; Elasticsearch realistically never.
7. **Availability model.** The seed carries explicit slots. Real professionals think in recurring rules plus exceptions. Recurrence in v1, or slots generated from a rule engine?
   → **D20:** both — rules authored, slots materialised, because `taken` needs a row to lock against a double booking.
8. **Time zones.** Everything is currently Africa/Accra with no offset. Does the platform ever serve a client or professional outside GMT, and if so, whose local time is authoritative on a booking?
   → **D21:** the professional's. Keep the wall clock, add an explicit `zoneId`; do **not** convert appointments to UTC instants.
9. **Multi-currency.** `currency` is on every money field but only `GHS` is used. Real requirement, or should it be dropped to keep the model honest?
   → **D22:** keep the column, build no conversion — but start enforcing it, because nothing checks it agrees across a join today.
10. **Data protection.** Ghana's Data Protection Act applies to the care summary. Where does data live, how long is it retained, and what does a deletion request do to a booking history that a payout ledger depends on?
    → **D24:** no care summary is stored; `visitAddress` and message bodies are the sensitive fields. Erasure by pseudonymisation, never deleting the ledger. *Needs legal sign-off.*
11. **Disputes.** The prototype promises a brokerage desk resolving disputes in five working days. That is a workflow, a role and a set of states nobody has specified yet.
    → **D23:** a separate `Dispute` aggregate — not new `BookingStatus` values — with `ROLE_BROKERAGE`, and reversal by compensating ledger entries.
12. **Observability.** JHipster ships Micrometer. Which backend — Prometheus/Grafana, or something already running on `docker.jojoaddison.net`?
    → **D25:** the host's existing OTLP-push stack. No new backend, and not Prometheus scraping.

One further gap, not numbered above: the header advertises **"Kafka, SSE"** and no SSE endpoint is
defined or built. → **D25's closing note:** drop the claim and poll; if real-time is wanted, SSE
belongs at the reactive gateway fed by Kafka, not in imperative `messaging`.

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
- [x] **Losing the broker mid-accept leaves the booking accepted and the notification pending in the
      outbox, delivered on recovery.** Automated as `deploy/verify-outbox-recovery.sh`. It used to
      stop the broker; since D27 there is only one, shared by four products, so stopping it would
      take the estate down to test one service. It **disconnects the booking container from
      `hcnet`** instead — the same event from the outbox's point of view, and a strictly better test
      because nothing else on the host is affected. It then accepts a booking, asserts the accept
      succeeded and the event is UNSENT with no notification raised, reconnects, and asserts the row
      drains and the notification arrives **exactly once**. Reconnection is on an `EXIT` trap, so a
      failure part-way through cannot leave booking off the plane.
- [x] **A booking cannot be put into somebody else's inbox.** `professionalLogin` is established
      from catalog's `/internal/professionals/{ref}/login` on every create, not read from the
      request. Covered by `CustomerBookingCreateIT`: the catalogue's answer is stored when the
      request agrees and when it omits the field, a disagreeing login is 409 with nothing written,
      the refusal does not name the real owner, an unreachable catalogue is 503 and an unknown
      reference 404. `InternalProfessionalResourceIT` covers the endpoint itself, unauthenticated.
- [x] **`/internal/**` is unreachable through the gateway** — checked against the running quality
      stack with a minted `ROLE_CUSTOMER` token, because no test in the repository can assert it:
      both ITs above talk to their service directly, so a green suite is not evidence.

      Before narrowing, an ordinary customer token reached catalog on
      `/services/healthconnectcatalog/management/health` (**200**) and on
      `/services/healthconnectcatalog/internal/professionals/p1/login` (**403** — refused by the
      service, having been proxied to it). After: both **404** at the gateway, no route, while
      `/api/professionals/count` and `/api/professionals/p1` still answer 200 with the token,
      `/api/professionals/count`, `/api/categories` and `/api/reviews/count` still answer 200
      anonymously, and booking and messaging still route.
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
#  Brings up the whole microservice estate locally: MongoDB for the gateway, one PostgreSQL per
#  domain service, the gateway and the four domain services, then loads demo/seed-data.json through
#  the test,dev seed loader.
#
#  IT DOES NOT START A BROKER OR A CONSUL, and will not. Both live once, in hc-infra, and every
#  stack in this estate points at them by container name over the shared `hcnet` network. Start
#  that first — this script checks it and refuses to continue without it:
#
#      cd ~/webroot/01-healthconnect/hc-infra && ./startup.sh
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
#  Consul REGISTERS these services; it does not route them — the gateway's routes are static, in
#  docker/docker-compose.dev.yml, exactly as production's are.
#
#  Compose service names are `dev-<service>`; the names below are the ones you type. The prefix
#  exists because compose publishes a service name as a DNS alias on every network it joins, and
#  `catalog` and `booking` are already claimed on hcnet by the quality stack.
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

case "${1:-}" in -h|--help) sed -n '2,43p' "$0"; exit 0 ;; esac
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
export HC_GATEWAY_PORT="${PORTS[gateway]}" HC_CATALOG_PORT="${PORTS[catalog]}" \
       HC_BOOKING_PORT="${PORTS[booking]}" HC_MESSAGING_PORT="${PORTS[messaging]}" \
       HC_PAYOUT_PORT="${PORTS[payout]}"

# The shared infrastructure plane — hc-infra, not this stack. Addressed by CONTAINER NAME, which is
# what the applications use over hcnet; the published port is only for the banner and for anything
# on the host that wants the UI. There is no HC_KAFKA_PORT and no HC_CONSUL_PORT here any more:
# this stack publishes neither, because it runs neither.
SHARED_NETWORK="${HC_SHARED_NETWORK:-hcnet}"
SHARED_CONSUL="${HC_SHARED_CONSUL:-hc-shared-quality-consul}"
SHARED_KAFKA="${HC_SHARED_KAFKA:-hc-shared-quality-kafka}"
SHARED_CONSUL_UI_PORT="${HC_SHARED_CONSUL_UI_PORT:-18510}"
# Topics are prefixed so this estate cannot consume quality's events, or be consumed by it
# (decisions.md D29). MUST match HEALTHCONNECT_TOPICS_PREFIX in docker/docker-compose.dev.yml —
# create one set and configure another and the apps sit on topics nobody publishes to, silently.
# `-` and not `:-`: an EXPLICIT empty must stay empty, because that is the documented
# escape hatch for reproducing the crossed-events state, and `:-` would silently
# substitute the default for it and make the warning below unreachable.
TOPIC_PREFIX="${HC_TOPIC_PREFIX-dev.}"
SHARED_INFRA_DIR="${HC_SHARED_INFRA_DIR:-$HOME/webroot/01-healthconnect/hc-infra}"
export HC_SHARED_NETWORK="$SHARED_NETWORK" HC_SHARED_CONSUL="$SHARED_CONSUL" \
       HC_SHARED_KAFKA="$SHARED_KAFKA" HC_TOPIC_PREFIX="$TOPIC_PREFIX"

# Compose service names carry a `dev-` prefix; the CLI names do not. See the header for why.
compose_name() { printf 'dev-%s' "$1"; }

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
    -h|--help)   sed -n '2,43p' "$0"; exit 0 ;;
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

  shared_plane
}

# The broker and Consul are hc-infra's, on a network this stack declares external. Compose's own
# error for a missing external network names the network and nothing else, and the error for a
# missing broker is no error at all — the apps start, serve, report healthy, and everything they
# publish goes nowhere. So all three are checked here, by name, with the fix printed.
running() { [[ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" == "true" ]]; }
shared_plane() {
  local fix="start it with:  (cd $SHARED_INFRA_DIR && ./startup.sh)"
  docker network inspect "$SHARED_NETWORK" >/dev/null 2>&1 \
    || die "the shared network '$SHARED_NETWORK' does not exist — $fix"
  running "$SHARED_CONSUL" || die "$SHARED_CONSUL is not running — $fix"
  running "$SHARED_KAFKA"  || die "$SHARED_KAFKA is not running — $fix"

  # A leader, not merely an answering agent: Consul serves /v1/status/leader and `consul members`
  # before it has elected one, and every KV read fails with "No cluster leader" until it does.
  docker exec "$SHARED_CONSUL" consul operator raft list-peers >/dev/null 2>&1 \
    || die "$SHARED_CONSUL has no leader yet — wait, or $fix"
  docker exec "$SHARED_KAFKA" /opt/kafka/bin/kafka-broker-api-versions.sh \
    --bootstrap-server localhost:9092 >/dev/null 2>&1 \
    || die "$SHARED_KAFKA is not answering — wait, or $fix"
  ok "shared plane: $SHARED_CONSUL (leader elected), $SHARED_KAFKA on $SHARED_NETWORK"

  # One bus, two estates — separated by the topic prefix since decisions.md D29, so events no
  # longer cross. This stays as a note rather than a warning because the separation depends on a
  # value that can be cleared: run with HC_TOPIC_PREFIX='' beside a live quality stack and both
  # receive everything either publishes, which is precisely the state D29 closed.
  if running hc-market-quality-booking; then
    if [[ -z "$TOPIC_PREFIX" ]]; then
      warn "the QUALITY stack is running and HC_TOPIC_PREFIX is EMPTY — the two estates will consume"
      warn "each other's events. Unset it to take the 'dev.' default, or stop quality."
    else
      log "quality is also running; separated by the '${TOPIC_PREFIX}' topic prefix"
    fi
  fi
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

# Databases only. The broker and Consul are already up — preflight refused to get this far
# otherwise — and neither is a service in this project any more.
infra_up() {
  step "Infrastructure"
  local dbs=()
  for s in "${SERVICES[@]}"; do dbs+=("${s}-db"); done
  compose up -d "${dbs[@]}"
  ok "databases started"

  # Topics on the SHARED broker, via docker exec rather than `compose exec`: it is not this
  # project's container. --if-not-exists throughout, so topics the quality stack or another product
  # already created are left exactly as they are — this adds, it never redefines.
  log "ensuring topics on $SHARED_KAFKA (prefix '${TOPIC_PREFIX}')"
  for t in booking.requested booking.accepted booking.declined booking.cancelled \
           booking.completed review.published payout.settled notification.raised \
           dispute.resolved; do
    docker exec "$SHARED_KAFKA" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
      --create --if-not-exists --topic "${TOPIC_PREFIX}healthconnect.$t" --partitions 3 --replication-factor 1 >/dev/null
  done
  ok "9 topics present"
}

apps_up() {
  step "Services"
  export SPRING_PROFILES_ACTIVE="$PROFILES"
  SEED_HOST_PATH="$(dirname "$SEED_FILE")"
  export SEED_HOST_PATH
  : "${JWT_BASE64_SECRET:?set JWT_BASE64_SECRET (one key across the estate — see the workspace guide)}"
  export JWT_BASE64_SECRET
  local names=(); for s in "${SERVICES[@]}"; do names+=("$(compose_name "$s")"); done
  compose up -d "${names[@]}"
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
  Profiles         $PROFILES
  Seed             $SEED_FILE

$c_dim  Shared plane (hc-infra, not this stack):$c_reset
  Consul UI        http://localhost:${SHARED_CONSUL_UI_PORT}/ui  — services register as hc-market-dev-*
  Kafka            $SHARED_KAFKA:9092 from a container; localhost:19192 from this host

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
  restart) preflight
           names=(); for s in "${SERVICES[@]}"; do names+=("$(compose_name "$s")"); done
           compose restart "${names[@]}"; for s in "${SERVICES[@]}"; do
             wait_http "$s" "http://localhost:${PORTS[$s]}/management/health" "$TIMEOUT" || true; done ;;
  status)  compose ps ;;
  logs)    names=(); for s in "${SERVICES[@]}"; do names+=("$(compose_name "$s")"); done
           compose logs -f --tail=120 "${names[@]}" ;;
  # `down` cannot take the shared plane with it and does not try: hc-infra's Consul and Kafka are
  # not services in this project, and hcnet is external, which is exactly why it is declared that
  # way. --remove-orphans additionally sweeps the pre-2026-08-31 containers — this stack's own
  # broker and Consul, and the un-prefixed service containers — which is how you migrate a running
  # estate onto this file.
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
    # kojoampia, not jojoaddison: that is the account the sibling packages live under
    # (ghcr.io/kojoampia/hc-admin-gateway and friends). See decisions.md D13.
    GHCR_OWNER="${GHCR_OWNER:-kojoampia}"
    # hc-market-<service>, not healthconnect-<service>. `healthconnect` is the PLATFORM's name and
    # four products share it; the sibling packages are all hc-<product>-<service>, and hc-market's
    # should sort beside them rather than under a prefix that says nothing about which product they
    # belong to.
    IMAGE_PREFIX="ghcr.io/${GHCR_OWNER}/hc-market"
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

  # Both networks are declared `external: true`, so compose will not create them and `up` fails
  # outright if either is absent. They belong to the host, not to this stack: infranet carries
  # Kafka, Consul and the databases (~/webroot/00-infrastructure), monitoring carries the shared
  # otel-collector (~/webroot/02-monitoring).
  #
  # Checked here rather than discovered at `up`, because the tempting fix at that point is to
  # delete the network line -- and for `monitoring` that "fix" is silent: the stack comes up
  # healthy, serves correctly, and never reports another span.
  log "checking host networks"
  for net in "${HC_NETWORK:-infranet}" "${HC_MONITORING_NETWORK:-monitoring}"; do
    (( DRY_RUN )) && { printf '%s  [dry-run] docker network inspect %s%s\n' "$c_dim" "$net" "$c_reset"; continue; }
    ssh -o BatchMode=yes "$HOST" "docker network inspect $net >/dev/null 2>&1" \
      || die "the '$net' network does not exist on $HOST. It is host-wide and this stack does not create it — start the owning stack first. Do NOT drop it from docker-compose.prod.yml."
    ok "network $net present"
  done
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
