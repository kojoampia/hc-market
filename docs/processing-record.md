# Record of processing activities — BridgeCare Marketplace

> **STATUS: DRAFT FOR COUNSEL'S REVIEW. NOT APPROVED.**
>
> Drafted 2026-09-11 — `decisions.md` **D88**, backlog **WP-09**. **hc-market only.** The other five
> BridgeCare services run on the same host and are not described here; each needs its own record, or a
> decision that one record covers the platform.
>
> Every row was read off source code, the JDL model of record, or a configuration file, and each says
> where. **Where a row could not be verified it says so instead of guessing** — §6 lists those.

---

## 1. Controller

| | |
| --- | --- |
| **Controller** | Jojo Addison Consultancy |
| **DPC registration** | **P0021484082** |
| **Service** | BridgeCare Marketplace, `market.abofonsa.com` |
| **Status of the service** | **Never deployed.** No production estate exists as of 2026-09-11; the only running instance is an internal quality box on a private LAN holding invented demo data |

> **⚠ THE MOST IMPORTANT LINE IN THIS DOCUMENT.** This service has never been deployed and holds no
> real customer's data anywhere. Everything below describes what it **would** process on the day it
> runs, which is the right time to have this record — but it is not a description of processing
> happening now.

## 2. Processing activities

### 2.1 Account and authentication

| | |
| --- | --- |
| **Purpose** | Let a customer or professional sign in and be identified across the platform |
| **Lawful basis** | Contract performance |
| **Categories of data** | Sign-in name, first name, last name, email address, password (bcrypt hash), assigned roles |
| **Categories of subject** | Customers, non-medical health professionals, internal staff |
| **Source** | Provided by the subject at registration |
| **Where** | Gateway service, MongoDB. `gateway/…/domain/User.java` |
| **Retention** | Not categorised. **See §6.1 — this is a gap** |
| **Recipients** | None outside the platform |

### 2.2 Taking and managing a booking

| | |
| --- | --- |
| **Purpose** | Record what a customer booked from which professional, and carry it through to completion, cancellation or dispute |
| **Lawful basis** | Contract performance; legal obligation for the financial record |
| **Categories of data** | Customer sign-in name and display name; professional reference and sign-in name; service reference and name; price and currency; scheduled date, time and IANA time zone; delivery mode; status; free-text customer note; "on behalf of" name; visit address; **a boolean recording that a care summary was shared**; cancellation reason; a meeting link the professional supplied; timestamps |
| **Categories of subject** | Customers, professionals |
| **Source** | The customer, at booking. Price, service name and the professional's sign-in name are taken from the catalogue rather than from the request (D22, D28) |
| **Where** | Booking service, PostgreSQL. `jdl/booking.jdl`, `booking/…/domain/Booking.java` |
| **Retention** | **Financial — 2,190 days (6 years).** Configured, not enforced |
| **Recipients** | The professional booked |

> **No health data.** `careSummaryShared` is a `Boolean`. No field, column or entity in this estate
> holds a condition, an allergy or a medication — verified by searching every service's main source;
> the words occur only in comments. D42 obtained a lawful-basis position treating such content as
> ordinary contract data, and **the platform has never held any**. That is D88's finding and §6.4.

### 2.3 Messaging between a customer and a professional

| | |
| --- | --- |
| **Purpose** | Let the two parties to a booking arrange it |
| **Lawful basis** | Contract performance |
| **Categories of data** | Message body, sender and recipient sign-in names, conversation reference, notification body and deep link, read/unread state |
| **Categories of subject** | Customers, professionals |
| **Source** | Written by the parties |
| **Where** | Messaging service, PostgreSQL. `jdl/messaging.jdl` |
| **Retention** | **Operational — 365 days.** Configured, not enforced |
| **Recipients** | The other party to the conversation |

### 2.4 Reviews

| | |
| --- | --- |
| **Purpose** | Let customers publish an assessment of a professional, and let others read it |
| **Lawful basis** | Contract performance |
| **Categories of data** | Rating, review body, the display name the author publishes under, booking reference, publication date, the professional's public reply |
| **Categories of subject** | Customers (as authors), professionals (as subjects of the review) |
| **Source** | The customer |
| **Where** | Catalog service, PostgreSQL. `jdl/catalog.jdl` |
| **Retention** | Not categorised. **See §6.1** |
| **Recipients** | **Published — readable by anyone**, which is the point of a review |

> **A review body survives erasure.** Deliberate and recorded (D37): it is public speech about a
> professional's business that other people rely on. The author's name is removed; the text stays.

### 2.5 Payment

| | |
| --- | --- |
| **Purpose** | Take payment for a booking |
| **Lawful basis** | Contract performance |
| **Categories of data** | **Stored by us:** the provider's payment reference, the provider's name, the amount, a note we composed ourselves. **Sent to the provider:** the customer's email address and the amount |
| **Categories of subject** | Customers |
| **Where** | Booking service, `payment_attempt` table |
| **Retention** | Financial — 2,190 days |
| **Recipients** | **Paystack**, `api.paystack.co` — only when enabled. **No provider is enabled on any estate today** |

> Three properties worth recording. **No card details ever reach this platform** — the customer enters
> them on the provider's page. **No provider's prose is stored or shown**: every message a customer
> sees is composed from our own vocabulary (D44), because a provider's message is a route by which
> somebody else's words carry a customer's details into our records. And **the payment table holds no
> personal data**, which is why the erasure sweep does not visit it — a fact that stops being true the
> moment a customer field is added there.

### 2.6 Earnings and payouts

| | |
| --- | --- |
| **Purpose** | Record what a professional has earned and what has been paid to them |
| **Lawful basis** | Contract performance; legal obligation |
| **Categories of data** | Professional sign-in name and reference, service name, gross, commission and net amounts, the day earned, payout batches |
| **Categories of subject** | Professionals |
| **Where** | Payout service, PostgreSQL. `jdl/payout.jdl` |
| **Retention** | Financial — 2,190 days |
| **Recipients** | None outside the platform. **Nothing in this platform pays a professional** — the interface has no settlement call, deliberately, pending the Act 987 question (WP-13) |

### 2.7 Erasure, and the record of it

| | |
| --- | --- |
| **Purpose** | Give effect to an erasure request, and keep evidence of what was done |
| **Lawful basis** | Legal obligation |
| **Categories of data** | A one-way alias derived from the subject's sign-in name; counts of what changed; the acting staff member's sign-in name; timestamps |
| **Where** | Booking (`erasure_run`, `erased_subject`), catalog and messaging registers |
| **Retention** | Kept — it is the evidence that an irreversible act was performed |
| **Recipients** | Internal only |

> The alias is an HMAC keyed by an estate-wide secret, so the same person resolves to the same alias in
> every service and two of their records can be told apart without either naming them. **It cannot be
> reversed** and **the key must never be rotated after an erasure**, or existing aliases are orphaned
> (D35).

## 3. Retention, as configured

| Category | Days | Covers |
| --- | --- | --- |
| Financial | **2,190** | Bookings, payment records, disputes, ledger |
| Operational | **365** | Message bodies, notifications |

**There is no third category.** A `care-summary-days` period of 90 days existed until D88 and is
removed: it governed no data.

> **⚠ NOTHING ENFORCES ANY OF THIS.** There is no scheduler in this estate. The periods are read from
> the environment at start-up with the ratified figures as the committed default, the internal policy
> endpoint reports them beside `enforced: false`, and a test pins that honesty so the day a sweep
> exists it must be changed deliberately.

## 4. Transfers outside Ghana

Production is intended to run on a virtual server at `199.247.5.252`, outside Ghana. All of the
processing above would therefore involve a transfer. **See `docs/data-transfer-basis.md`** — a separate
draft. The host also runs the other five BridgeCare services, so the same transfer affects them.

## 5. Security measures

| Measure | State |
| --- | --- |
| Authentication on every non-public endpoint | **Implemented.** Public reads are an explicit allow-list |
| Staff authorisation for customer records and erasure | **Implemented** — `ROLE_BROKERAGE`, asserted by tests against the running container |
| Passwords stored as bcrypt hashes | **Implemented** |
| TLS in transit | **Implemented** at the edge for the services that are deployed. **hc-market is not deployed** |
| Each service owns its own database instance | **Implemented** |
| Databases unreachable from other products | **Implemented** — they join no shared network |
| Payment provider callbacks authenticated by signature over the raw body | **Implemented** (HMAC-SHA512, constant-time comparison) |
| Retention enforcement | **Not implemented** — §3 |
| Audit log of staff access to customer records | **Not implemented.** §6.3 |

## 6. Known gaps, each needing a decision or work

### 6.1 Two activities have no retention category
**2.1 (accounts)** and **2.4 (reviews)** are not covered by either configured period. An account is
neither a financial record nor operational data, and a review is published indefinitely by design.
**Needs counsel:** whether an account and a published review need stated periods, and what they are.

### 6.2 Retention is not enforced
§3. Engineering work — a scheduler this estate does not have. It is the largest gap in this record and
it is stated in the notice as well.

### 6.3 No audit log of staff access
A member of the brokerage desk can read any customer's records and perform an erasure. The erasure is
recorded; **a read is not.** Nothing in this estate logs who looked at whom. **Needs a decision** on
whether that is acceptable before launch.

### 6.4 Counsel's lawful-basis position was taken about data the platform does not hold
D42 treated conditions, allergies and medications as ordinary contract data. D88 established that none
is stored. **Counsel should be told**, because it changes the risk they assessed, and because a
position on special-category data is worth having before a care summary is ever added rather than
after.

### 6.5 The hosting provider is unconfirmed
§4 names an address, not a company. A processor cannot be recorded properly without knowing who it is
and on what terms.

### 6.6 This record covers one of six services
The other five process customer data on the same host. **Needs a decision:** one record for the
platform, or six.
