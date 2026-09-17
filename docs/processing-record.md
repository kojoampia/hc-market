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
| **Retention** | **Split.** An *activated* account is not categorised — **see §6.1, this is a gap**. An account never activated is **deleted after 3 days**, automatically — §3.1 |
| **Recipients** | **An outbound mail relay, from 2026-09-17** (`decisions.md` D94). Activation and password-reset mail carries the sign-in name and is addressed to the email address, so whoever relays it processes both on our behalf. **No relay is configured on any system yet** — the provider has not been chosen (a budget decision, `decisions.md` D90 §7) — and a system with none configured refuses to start rather than accepting registrations it cannot answer. **Whoever is chosen is a processor and needs a contract and a §4 entry**; §6.7 carries that. Nothing else leaves the platform |

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

### 3.1 One erasure that IS automatic, and is not one of the periods above

| | |
| --- | --- |
| **What** | An account created but never activated |
| **Deleted after** | **3 days**, swept daily at 01:00 in the running system's own time zone — `Etc/UTC` in every container we deploy, which is also Accra time |
| **Where** | `gateway/…/service/UnactivatedAccountSweep.java` — a cron task registered through `SchedulingConfigurer`, deleting accounts that are `activated = false`, hold an activation key, and were **created** longer ago than the configured window (it filters on the creation date; an unactivated account with no key — one an administrator created for somebody — is left alone) |
| **Data destroyed** | Sign-in name, first and last name, email address, password hash |
| **Decided by** | **The organisation, since 2026-09-17** (`decisions.md` D94). Until then: nobody here — it was JHipster's generated behaviour, a literal in a generated file, and was never a decision of this project |
| **Configured by** | `healthconnect.accounts.unactivated-retention-days` / `…-sweep-cron`, from `HC_UNACTIVATED_ACCOUNT_RETENTION_DAYS` and `HC_UNACTIVATED_ACCOUNT_SWEEP_CRON`. Both blank on every system, so **3 days is what is applied**; a value that is set and unreadable, or zero, or negative, stops the service starting |
| **Runs today** | Yes. Verified live: the quality gateway started 2026-09-11T21:22Z, so it has swept four times |
| **Watched deleting** | Yes, since 2026-09-17 — at the boundary, against a real database, in `UnactivatedAccountSweepIT`: an account past the window is removed, one an hour inside it is not, an activated account is never touched however old it is, and an unactivated account with no activation key is left alone. Watched again on a live gateway, deleting one real account and leaving a recent one |

Recorded because it destroys a real person's personal data on a timer, and because **this document
asserted the opposite until 2026-09-15.** It was a *de facto* retention period for one class of account
and it has still never been put to counsel — what changed on 2026-09-17 is that it is a **stated** one:
the same three days, moved out of a generated file into configuration, reported at
`GET /management/info` as `accounts.unactivatedRetentionDays`, and stated to the data subject in
`docs/privacy-notice.md` §7.1. A check in CI refuses a build whose code and whose two documents
disagree about the number.

**The number was deliberately not changed.** `decisions.md` D94 §2 records why: the objection D91
raised was that an undecided framework default was destroying personal data unrecorded, not that three
days is wrong, and lengthening the window holds a name and an email address for longer under a position
nobody has taken. Extending it, and stopping the deletion altogether, are both argued there as rejected
alternatives — the second one because it would make §6.1's gap bigger rather than smaller.

Two things that followed from the old entry are **closed**: the activation mail could not be sent on any
system, so the three days could not be survived (**NEW-47**, fixed — mail is now required before a
system can be deployed and a system that cannot send it refuses to start); and §6.1's "accounts have no
retention category" remains not quite right, because unactivated ones have one — now by decision rather
than by default.

> **⚠ NOTHING ENFORCES ANY OF THIS — but not for the reason this document gave until 2026-09-15.**
> It said *"there is no scheduler in this estate"*, and that is false: `@EnableScheduling` is active in
> **all five** services and a `ThreadPoolTaskScheduler` thread runs in **all five** quality containers,
> measured at `/proc/1/task`. Two `@Scheduled` methods already run — booking's outbox poll every two
> seconds, which is the estate's entire event-delivery path, and the gateway's daily deletion of
> unactivated accounts (§3.1). **What is missing is a retention sweep, not the ability to schedule one.**
> `decisions.md` **D91**. The periods are read from
> the environment at start-up with the ratified figures as the committed default, the internal policy
> endpoint reports them beside `enforced: false`, and a test pins that honesty so the day a sweep
> exists it must be changed deliberately.

## 4. Transfers outside Ghana

Production is intended to run on a virtual server at `199.247.5.252`, outside Ghana. All of the
processing above would therefore involve a transfer. **See `docs/data-transfer-basis.md`** — a separate
draft. The host also runs the other five BridgeCare services, so the same transfer affects them.

**A second transfer arrives with the mail relay, and it is not chosen yet** (§2.1, `decisions.md` D94).
Every activation and password-reset message carries a sign-in name to an email address, through
whoever relays it; most candidate providers are outside Ghana. The provider is a budget decision that
has not been taken (`decisions.md` D90 §7), which means this is the rare case where the
data-protection position can be settled **before** the processing starts rather than after. §6.7.

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
| Retention enforcement | **Not implemented** — §3. The scheduling capability exists and is running, and one sweep now runs on it: unactivated accounts, §3.1, by decision since `decisions.md` D94. The three configured periods still have no sweep behind them — D91, NEW-52 |
| Rate limits on the public account paths | **Provided, not installed** (`decisions.md` D94). Registration, password-reset-request and sign-in are capped per source address at both edges — 10/min and 1/s — which is what stops open self-registration being an account-creation and mail-sending amplifier. The configuration is in this repository; `/etc/nginx` belongs to the architect, so it is installed by a person and **is not in force on any system until they do** |
| A registration cannot be answered if mail cannot be sent | **Implemented** (`decisions.md` D94). A system with no mail configuration refuses to start rather than answering 201, discarding the message and deleting the account three days later |
| Audit log of staff access to customer records | **Not implemented.** §6.3 |

## 6. Known gaps, each needing a decision or work

### 6.1 Two activities have no retention category
**2.1 (accounts)** and **2.4 (reviews)** are not covered by either configured period. An account is
neither a financial record nor operational data, and a review is published indefinitely by design.
**Needs counsel:** whether an account and a published review need stated periods, and what they are.

**One correction since 2026-09-15:** an *unactivated* account does have a period — three days (§3.1).
Since 2026-09-17 it is one the organisation has *taken* rather than inherited (`decisions.md` D94), and
the question to counsel is sharper than it was rather than answered: **the organisation now holds a
3-day rule for one class of account, has never examined it, and has no rule at all for the rest.**
D94 §5 also asks whether "never delete" should be expressible at all, and deliberately does not answer
it — it is the one option that would make this gap bigger.

### 6.2 Retention is not enforced
§3. Engineering work, and **not** a scheduler this estate lacks — that claim was false and is corrected
in §3.1's box (`decisions.md` D91). Two scheduled tasks already run, one of them a deletion of personal
data (§3.1), so what is missing is a sweep for the three configured periods rather than the ability to
run one. It is the largest gap in this record and it is stated in the notice as well.

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

### 6.7 The mail relay is a processor nobody has chosen
New on 2026-09-17, `decisions.md` D94. §2.1 now names an outbound relay as a recipient of a sign-in
name and an email address, and §4 as a probable transfer — but **no provider is configured on any
system**, because choosing one is a budget decision that has not been taken (`decisions.md` D90 §7).

That order is unusually favourable and worth using rather than regretting: the processing has not
started, so the contract, the transfer basis and the retention the provider applies to message logs can
all be settled before the first message is sent. **Needs a decision, then a processor entry here.**
Until one exists, no system can be deployed that would accept a registration — a system with no mail
configuration refuses to start.
