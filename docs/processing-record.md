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
| **Retention** | **Financial — 2,190 days (6 years).** Configured; a sweep exists since `decisions.md` D96 and is **switched off on every system**, so not applied. §3, §6.2 |
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
| **Retention** | **Operational — 365 days.** Configured, not enforced, and **no sweep exists** — D96 built one for the financial period only, in a different service. §6.2, backlog NEW-68 |
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
| **Retention** | **Financial — 2,190 days.** Configured, not enforced, and **no sweep reaches this data** — D96's sweep runs in booking against a *customer's* rows, and a customer's erasure deliberately leaves every ledger row intact. §6.2 |
| **Recipients** | None outside the platform. **Nothing in this platform pays a professional** — the interface has no settlement call, deliberately, pending the Act 987 question (WP-13) |

### 2.7 Erasure, and the record of it

| | |
| --- | --- |
| **Purpose** | Give effect to an erasure request, and keep evidence of what was done |
| **Lawful basis** | Legal obligation |
| **Categories of data** | A one-way alias derived from the subject's sign-in name; counts of what changed; whether every service completed; the booking references touched; timestamps. **No acting staff member and no reason** — see the correction below |
| **Where** | Booking (`erasure_run`, `erased_subject`), catalog and messaging registers |
| **Retention** | Kept — it is the evidence that an irreversible act was performed |
| **Recipients** | Internal only |

> The alias is an HMAC keyed by an estate-wide secret, so the same person resolves to the same alias in
> every service and two of their records can be told apart without either naming them. **It cannot be
> reversed** and **the key must never be rotated after an erasure**, or existing aliases are orphaned
> (D35).

> **⚠ CORRECTED 2026-09-18 — this row claimed a stored actor that does not exist.** It read *"the
> acting staff member's sign-in name"* among the categories of data, and **no such field is stored
> anywhere on the erasure path.** Measured field by field rather than inferred:
>
> | Table | Every column it has |
> | --- | --- |
> | `erased_subject` | `pseudonym`, `erased_at` |
> | `erasure_run` | `id`, `pseudonym`, `ran_at`, `complete`, `booking_references`, `receipt` |
> | inside the serialised `receipt` | `pseudonym`, `complete`, `recorded`, `recordId`, `bookingReferences`, and a leg per service — **no actor** |
>
> **This over-stated what is kept, which is the safer direction and still wrong** — a processing record
> is read as an account of what the organisation holds, and a regulator asking *"who performed this
> erasure"* would be pointed at a column that is not there. It was wrong before `decisions.md` D96 and
> is corrected by it, because **D96 is the package that established the falsifying fact**: it verified
> the absence of an actor column while deciding what a *sweep* would put in one, and that absence is
> the whole premise of backlog **NEW-67**.
>
> **The gap this leaves is real and is NEW-67's**, not a documentation slip. Since D96 the register is
> written by **two callers that mean different things** — a subject's request, identity-checked by a
> person at the desk (D40), and the financial retention period expiring on a timer — and the rows are
> **identical**. So the estate cannot answer either *"who did this"* or *"why"*. §6.3 records the
> related absence of any audit of staff *reads*; this is the same shape on the estate's one
> irreversible *act*. NEW-67 recommends a nullable `reason` and deliberately leaves the actor question
> to §6.3, because a staff member's sign-in name in a table kept for ever is itself a disclosure
> decision (D47's reasoning).

## 3. Retention, as configured

| Category | Days | Covers |
| --- | --- | --- |
| Financial | **2,190** | Bookings, payment records, disputes, ledger |
| Operational | **365** | Message bodies, notifications |

**There is no third category.** A `care-summary-days` period of 90 days existed until D88 and is
removed: it governed no data.

> **A SWEEP EXISTS SINCE 2026-09-17 AND IS SWITCHED OFF — `decisions.md` D96, backlog NEW-52. The
> heading above is still true of every system, and it is true for a different reason now, so read
> this before quoting either.** What changed and what did not:
>
> | | |
> | --- | --- |
> | The **financial** period (2,190 days) | **Has a sweep.** `RetentionSweep` in booking erases every customer with no booking activity for that long, by calling the same erasure the desk calls, recorded on the same register (§3, D39) |
> | Is it running anywhere? | **No.** It is **off by default** and, when switched on, is in **dry run** by default — two independent decisions between a system and an irreversible erasure. All three compose files pass the switches with no value |
> | The **operational** period (365 days) | **Still has no sweep at all**, genuinely unchanged. It governs message bodies, notifications and conversations, which live in a different service; applying booking's six-year clock to them would be six times too long and would file a receipt saying otherwise. §6.2 |
> | `enforced: false` on the policy endpoint | **Now derived** from the sweep's own two switches rather than returned as a literal. So this document's claim is checkable against a running system instead of taken on trust, and it cannot silently go stale the day somebody enables the sweep |
>
> **The honest summary is therefore a third thing, neither "not implemented" nor "implemented":** the
> organisation now has the means to apply the financial period and has not chosen to. That is a
> materially better position than having no means — the gap is a decision rather than engineering — and
> it is **not** the same as applying it. Nothing has been erased by a timer on any system.

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
>
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
| Retention enforcement | **Partly available, and switched off** — §3, §6.2, and this row read "**Not implemented**" until 2026-09-17. The scheduling capability exists and is running, and two sweeps run on it by decision: unactivated accounts (§3.1, `decisions.md` D94) and, since D96, the financial period **as it applies to a customer's data in the booking service only** — off by default and in dry run when enabled, so applied on no system. **Not** reached by it: a professional's earnings and payout batches (§2.6, the same period), a professional's identity on an old booking, and the whole **operational** period (§2.2, §2.3). NEW-52 is closed; what remains is a decision to enable what exists, NEW-68 for the operational period, and an unasked question for §2.6 |
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

### 6.2 Retention is not enforced — and since 2026-09-17 that is a decision, not missing work
§3. **The shape of this gap changed with `decisions.md` D96** and it is worth stating precisely,
because it has now been wrong in two different directions in this document.

It was never a scheduler this estate lacked — that claim was false and is corrected in §3's box
(D91). It is no longer missing engineering either **for one part of the financial period, and the scope
matters more than the capability**: `RetentionSweep` applies that period to **a customer's personal
data in the booking service** — it calls `eraseCustomer`, the single-service erasure, not the desk's
`eraseEverywhere` fan-out. **It is off on every system**, by a default this repository chose
deliberately, and enabling it takes two separate switches. So for that data the question is now *"will
the organisation apply its own stated policy, and from when"* — a decision for a person, and one that
can be taken with a dry run's count in hand rather than blind.

**What the financial period covers and this sweep does NOT reach**, so that "the financial period has a
sweep" is never read as category-wide:

- **§2.6 — a professional's earnings and payout batches**, in the **payout** service, also under the
  2,190-day period. Nothing sweeps them and this sweep cannot: it runs in booking, against booking's
  tables, and erasing a customer leaves every ledger row deliberately intact (that is the whole reason
  erasure here is pseudonymisation rather than deletion — ledger rows are keyed by booking reference
  and carry their own retention obligation);
- **a professional's identity on an old booking.** The sweep redacts the *customer*.
  `professionalLogin` and `professionalRef` are untouched by design, so a seven-year-old booking still
  names the practitioner who delivered it;
- **§2.2 and §2.3 — message bodies, notifications and conversations**, which are the *operational*
  period and are the paragraph below.

None of those three is an oversight in the sweep; each is a different subject with a different
argument, and the first two have never been asked. **The capability is one activity wide, not one
category wide.**

**What is still missing work** is the **operational** period (365 days): message bodies, notifications
and conversations, in messaging, with nothing sweeping them. That is **NEW-68**, and it is not a copy of
what D96 built — a shorter period over a different service's tables, where the data is the substance of
what people wrote to each other rather than a financial record. Six years of message bodies under a
policy that says one year is the larger exposure of the two, and it is the one that remains.

Still stated in the notice as well, and still the largest gap in this record.

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
