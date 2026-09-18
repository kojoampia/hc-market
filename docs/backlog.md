# Backlog — hc-market

Every open item in this repository, folded into work packages. Sources: `docs/decisions.md` D1–D90,
the two code reviews of 2026-09-01, and the verification runs against the quality box.

**This is a derived document.** `decisions.md` holds the reasoning and stays the record; this holds
only *what is left*, in a shape you can pick work from. Where the two disagree, `decisions.md` wins —
and where either disagrees with the code, the code wins.

**Status vocabulary.** `DONE` — built, tested, and verified against a running estate. `IN PROGRESS` —
being worked now. `READY` — specified, unblocked, nobody is holding it. `BLOCKED` — waiting on a named
person, not on engineering. `WON'T` — considered and deliberately not done, with the reason.
`PARTLY DONE` — some of it is built and verified while the rest is not, so it is neither `IN PROGRESS`
(nobody is holding it) nor `BLOCKED` (part of it shipped); the entry says which half is which. **The
blocking half need not be a person**: it was declared as "blocked on a named person" until D84, whose
NEW-43 is the first instance where the unshipped half waits on a *measurement* nobody has taken
(NEW-44) — widened rather than relabelled, for the same reason the two below were added. `CLOSED by decision (D<n>)` — the item asked a question rather than named work, and the answer
is ratified; no code shipped, so it is not `DONE`.

**Two of these were in use before they were declared here**, which is the same defect this repository
keeps finding in its own checks and comments: a statement of what the vocabulary *is* that the file
itself contradicts. Added rather than relabelled — `PARTLY DONE` and `CLOSED by decision` each carry a
distinction the declared five cannot express, so the declaration was the wrong half.

**AN ITEM'S STATUS LIVES IN ITS OWN `## ` HEADING, and that is the only place it lives** (decisions.md
D83, backlog NEW-41). The packages table below indexes the **work packages** only — which is what its
header says it is — and carries a second copy of *their* status, checked against these headings by CI.
For every `NEW-*` item the heading is the single record: the table used to carry rows for NEW-1..NEW-22
and none for NEW-23 onward, so it indexed 41 of 58 items while continuing to look complete, and every
status disagreement this file has produced came from one fact living in two places. Where a heading and
anything else disagree, **the heading wins** — the general rule at the top of this file settles
`decisions.md` against the code and says nothing about a file disagreeing with itself.

---

## The packages

| WP | Package | Status | Blocked on |
|---|---|---|---|
| **WP-01** | Erasure: the mechanism | DONE | — |
| **WP-02** | Erasure: reach every table | DONE | — |
| **WP-03** | Erasure: survive in-flight events | DONE | — |
| **WP-04** | Erasure: pepper the pseudonym | DONE | merged `b4d0138`, released, quality rebuilt clean |
| **WP-05** | Erasure: the notifications a repeat booking hides | DONE | D36 |
| **WP-06** | Erasure: a durable record in booking and catalog | DONE | D39 |
| **WP-07** | Erasure: orchestration across the three services | DONE | D38 |
| **WP-08** | Erasure: what an erased person who keeps their account is | DONE | D37, built as D40 |
| **WP-09** | Erasure: retention periods and lawful basis | PARTLY DONE | D42 — counsel answered all four. Retention built and environment-gated; two documents and one registration number outstanding |
| **WP-10** | Payments: the seam can complete a lifecycle | DONE | D41 |
| **WP-11** | Payments: asynchronous confirmation | DONE | D43 |
| **WP-12** | Payments: the zero-amount booking | DONE | D44 — reviewed 2026-09-03, four findings, all fixed |
| **WP-13** | Payments: provider choice and Act 987 | PARTLY DONE | D45 — registry, choice, route, permit and secrets built; reviewed 2026-09-04, five findings, all fixed. **D50 closed the Paystack adapter**: two of six calls implemented from a sibling product's working integration; reviewed 2026-09-05, five findings, all applied. Hubtel and MoMo are still seams and Act 987 is still a question for a person. **D74 closed the email**, on D72 §3's authorisation: `GatewayCustomerContacts` asks the gateway's `GET /internal/customers/{login}/email` with a **second** short-lived estate token (`ContactLookupToken` — reusing the erasure one would have made its scope a method name), so Paystack's `authorize` can name who is paying. Four sub-decisions argued, ten mutations run with eight red, and one of D72 §3's own stated terms **overturned by measurement**: D28's route-predicate argument does not keep a gateway endpoint private, because there is no route in front of it. Still no payment end to end, still no credentials, still `PARTLY DONE` |
| **WP-14** | Verification badge | DONE | — |
| **WP-15** | Badge: date-only on the wire | DONE | D47 — reviewed 2026-09-04, four findings, all applied |
| **WP-16** | Search performance | WON'T, for now | — |
| **WP-17** | Video and WhatsApp providers | PARTLY DONE | budget for the hosted upgrade; v1 is NEW-45 |
| **WP-18** | Production `infranet` alias check | PARTLY DONE | rename shipped; the question needs production host access |
| **WP-19** | Production deployment configuration, to sibling parity | PARTLY DONE | D49 — `deploy/prod-server/` built, five defects in the deploy path fixed, then reviewed 2026-09-05 and eight more applied, one of them blocking (a failing smoke test triggered an automatic rollback). **Nothing has ever been run against a host**; the fifteen things a person must still do are in that directory's README |

**This table indexes the WORK PACKAGES, which is what its header says it is — the `NEW-*` items are
read in their own sections (decisions.md D83, backlog NEW-41).** It carried rows for NEW-1..NEW-22 and
none for NEW-23..NEW-39, so it indexed 41 of 58 items while continuing to look complete; and every one
of the status disagreements this file has produced came from the same fact living in two places. The
duplication is removed rather than guarded for the `NEW-*` half. It REMAINS for the nineteen work
packages above, where two rows had already drifted (WP-17, WP-18), so those pairs are checked by CI:
*"the packages table may not disagree with its own sections"*.

---

## From prototype to production — the five phases

**`decisions.md` D90, 2026-09-15.** Everything above this line is the estate's *first* life: nineteen
work packages and forty-six items that made a backend real. This is the second one. The question it
answers is *"how do we move from prototype to a real application"*, and the short answer is that
**the backend is real and there is no application in front of it** — `web/`, `api/` and `mobile/` are
three empty directories, the only UI is one 212KB HTML file, and CI forbids serving that file in
production because it presents 18 invented practitioners with association registration numbers.

**The ordering rule**: unblocks the most other work, is reversible if wrong, is verifiable before
production. Phases are sequential where stated and their items are independent within a phase.

| phase | what | items | depends on |
|---|---|---|---|
| **1** | a person can hold an account | ~~NEW-47~~ **DONE** (D94) | — |
| **2** | the application | NEW-48 | phase 1 for anything behind a token; D90 §3 for its shape |
| **3** | the promises the API already makes | NEW-49, NEW-50, ~~NEW-51~~ **DONE** (D95), ~~NEW-52~~ **DONE** (D96), NEW-53 | nothing; cheaper with phase 2's screens |
| **4** | money for real | WP-13, NEW-54 | Act 987, provider credentials, a callback route |
| **5** | deploy | WP-18, WP-19, NEW-55 | the four gates below |

**Phase 1 is DONE as of 2026-09-17 — NEW-47, `decisions.md` D94.** It was first and it was not a
judgement call: the gateway's account lifecycle was generated, unconfigured and had never been
exercised by anybody, so a customer registered, was told to check their email, received nothing, could
never log in, and was deleted three days later — `201 Created` and one `WARN` line. Mail is now
required before any environment can be deployed, the three days are a stated and configurable policy
rather than a generated literal, the three public account paths have ceilings, and **the whole path was
walked for real** against a live gateway and a real SMTP catcher. One thing it could not close and one
it opened: **no message has reached a real provider**, because none is chosen (D90 §7, a budget item),
and the link in the mail points at a frontend route nothing serves yet — **NEW-60**, which is phase 2's
to close.

**Phase 2 is therefore unblocked for everything behind a token.**

**Phase 3 is not first, deliberately.** None of its five items blocks the frontend, and each is
cheaper to build against a screen that exercises it. They are all *claims the estate currently makes
and cannot meet*, which is why they are before the deploy and not after it.

**Why not deploy first.** An API-only production estate is deployable today and would be honest — but
it would have no way to acquire a user, no interface, eighteen fabricated professionals as its entire
supply, and no means of paying anybody. If the operational learning is wanted early, the reversible
version is to run phases 1 and 5 and **hold the DNS record** until phase 2 lands: production reachable
by IP, `market.abofonsa.com` pointed at nothing. D90 §2.

**The four gates a first production deploy may not go without** (D90 §4) — everything else here may
follow a deploy:

1. a working account lifecycle — **NEW-47, closed 2026-09-17** (D94). What remains of this gate is not engineering: **an SMTP provider has to be chosen and paid for**, because an environment with no mail configuration now refuses to start rather than swallowing registrations;
2. WP-19's blocking six, in `deploy/prod-server/README.md` — ssh target, port 8086, `infranet` and
   `monitoring`, `secrets.env`, DNS, nginx and certbot;
3. **a restored backup** — no dump this repository produces has ever been restored, which makes every
   one of them a belief;
4. the **privacy notice served somewhere a data subject can reach it** — WP-09. Three drafts exist and
   nothing in this estate serves any of them. It is the one item whose exposure begins on the day of
   the deploy rather than on the day of the first customer.

**Act 987 is deliberately not a gate.** It blocks taking money (phase 4), not deploying, and
`PaymentProvider` has **no method that pays a professional** on purpose — so the answer can arrive
late without a rewrite.

**The ten questions that are nobody's here to settle** are tabulated in D90 §7 with a default for each,
and each is framed in its own item below. **None of them blocks the phase above it from starting.**

---

## WP-01 — Erasure: the mechanism · DONE

D24, D31. Pseudonymisation rather than deletion, across booking, messaging and catalog: the login
becomes a deterministic alias, the free text goes, and everything the estate depends on —
`bookingReference`, the money fields, `professionalRef`, the status history — survives. `ROLE_BROKERAGE`,
not self-service, because an erasure request must be identity-checked by a person first.
`healthconnect.privacy.retention-days` exists with **no default**, and `GET /api/desk/privacy` reports
`enforced: false` beside it so a configured period is never mistaken for an applied one.

Verified against the quality box: the person gone, the money and reference intact, one alias in all
three services.

## WP-02 — Erasure: reach every table · DONE

D34. The receipt said "1 booking erased" while four other tables still named the person: `outbox_event`
(login **and** display name in every event ever published about them, with no purge of sent rows
anywhere), `dispute.raisedByLogin` and its `reason`, `actor` on both status-history tables, and
`booking.cancellationReason`. Catalog's full-table scan replaced, catalog's missing erasure test
written, `erasedAt` no longer overwritten on a re-run, nine unindexed columns indexed.

The fixtures were the root cause: a test that seeds one table can only ever prove one table is erased.
Every erasure test now populates every table the workflow should reach and seeds a **second customer**
whose rows must not move.

## WP-03 — Erasure: survive in-flight events · DONE

D32, D34. A lagging `booking.requested` re-created a conversation under a login erased seconds earlier.
`erased_subject` — pseudonyms only, no logins — makes erasure a standing fact the consumer consults.
D32's first attempt narrowed the window rather than closing it, because under `READ_COMMITTED` the
register row is invisible until the erasure commits; closed with a Postgres advisory transaction lock
keyed on the subject, taken by both sides.

## WP-04 — Erasure: pepper the pseudonym · DONE, on `pepper-the-pseudonym`

D34. The alias is an unkeyed truncated SHA-256 of a short, guessable login, so anyone with database
read access can hash candidates offline, re-identify every `erased-…` row, and confirm from
`erased_subject` whether a named person was erased. The javadoc claimed it was "not reversible without
already knowing the login", which understated it — guessing is enough.

HMAC with a per-estate pepper, identical across the three services, injected like `JWT_BASE64_SECRET`
and never committed; alias widened to 16 hex while the derivation is changing. Wants a known-answer
test per service and a CI check that the three implementations cannot drift.

**Cheap now, expensive later**: changing the derivation re-keys nothing already written, so every day
with real erasures in the data raises the cost.

Built as D35. `SubjectPseudonym` is one file copied verbatim into all three services, with a CI check
that diffs it and its known-answer test across them, so the three cannot drift. Absent the pepper, the
services **start and refuse to derive** — the desk answers 503 naming the variable — rather than
refusing to boot: an outage of three services behind a value that one endpoint reads would have an
operator paste in a plausible value, which is the committed-default failure arriving by another road.
Messaging is the exception and guards startup when its register already holds rows, because an
unpeppered messaging cannot recognise its own erased subjects and the next lagging event would write a
real login back in.

A review of that guard then found it fired at the wrong moment — an `ApplicationRunner` runs after the
context refresh, and the Kafka listener container starts *during* it, so an unpeppered messaging
service with rows in the register consumed a slice of its backlog and wrote erased customers' real
logins back before the guard threw, once per restart. It is now a `SmartLifecycle` phased below every
other lifecycle bean, with a test that fails if the ordering regresses. Same D35 section.

A second review, of that fix, found six more — all of them in the paths nothing exercises until
somebody deploys. `deploy-prod.sh` supplied *neither* secret to the compose file that requires both,
so every production deploy would have died at `up` while telling the operator not to edit the file
that would have fixed it; the two now live in a `secrets.env` on the host that no deploy rewrites,
checked in preflight before the running stack is touched. `quality/startup.sh` never persisted an
environment-provided pepper, so the next run without the variable minted a fresh one against
databases keyed to the old. Messaging refuses to start on a pre-D35 register or a changed pepper,
the second by comparing a sentinel alias recorded in its own table — not in `erased_subject`, whose
emptiness is load-bearing. The two CI greps became a script that fails on the states they passed on.
And `deploy-dev.sh` now sources the `deploy/.env` it had always told operators to write. Same D35
section.

**Loose end for the operator:** the production host needs `/srv/healthconnect/secrets.env` holding
`JWT_BASE64_SECRET` and `HC_PRIVACY_PEPPER` before the next production deploy. `deploy-prod.sh --help`
prints the exact command; preflight refuses early and by name if it is missing.

**~~Loose end for this repository:~~ closed.** `CLAUDE.md`'s regeneration-hazard table was said here to
have no row for messaging's `privacy_pepper_witness` include. It has one, and had one when this was
written — checked while D39 added two more rows to the same table.

## WP-05 — Erasure: the notifications a repeat booking hides · DONE

D36. Notifications *about* an erased customer that sit in the professional's bell menu are found
through `deepLink`, and the link set was derived from the customer's conversations alone. But
`openThreadIfNone` dedupes by professional, so a customer's **second** booking with the same
professional never appears as any conversation's `bookingReference` — and that booking's "Ama Mensah
asked for…" notification was therefore missed, against a receipt reporting a clean erasure with
plausible non-zero counts. Repeat bookings with one professional are not an exotic case; they are the
product working.

The link set is now the **union** of the conversation references and the `deepLink` of the customer's
*own* notifications, collected in the loop that re-keys them: the customer's copy and the
professional's copy of one booking event share a deep link, so finding either finds the other. Still
one indexed `deep_link in (…)` query, no full-table scan. Confirmed red before the fix —
`notificationsRedacted expected:<2> but was:<1>` — with a test seeding two bookings, one professional,
one thread, and a second customer whose rows must not move.

`notificationsRedacted` keeps its meaning and now counts all of them rather than a subset. The
residual D36 records: a booking still *pending* at the instant of erasure leaves one professional-side
row nothing keyed to the customer points at. It does not grow — every later event on that booking goes
through the consumer, which already writes the pseudonym and "A customer".

**Closed by WP-07 on the fan-out path, and it needed no schema change.** Booking holds the reference
list and hands it over, so the residual is now the stated difference between `POST .../erase` and
`POST .../erase-everywhere` rather than an unbounded gap. The single-service endpoint still cannot
reach that row, deliberately, because nothing has told it the booking exists. See D38.

**A review of that fix found four more, all now closed in the same D36 section.** The residual was
prose and nothing else, so it is now pinned by a test that says in its own javadoc that it asserts
current behaviour deliberately and will go red when WP-06/WP-07 closes the hole. (It did not: WP-07's
references arrive in a payload that a desk call does not send, so the test acquired a partner covering
the fan-out path instead, and the pair states the boundary. D38.) The union's
completeness rested on two invariants nothing enforced — every notification about a person carries
`/bookings/<ref>`, and notification rows are append-only — so `raise()` now refuses a blank booking
reference, and both invariants are stated where the next writer will meet them: the `default` branch of
the consumer's switch already swallows the `notification.raised` fan-in booking publishes, and marks it
processed. A blank reference produced the literal `/bookings/`, which matched every other malformed row
rather than one booking. And re-keying a customer's own notification left its body alone, which made
the erasure correct only for as long as no template greets anybody by name; the body is redacted with
the re-key now.

## WP-06 — Erasure: a durable record in booking and catalog · DONE

D39. Messaging recorded `erased_subject` with an `erasedAt`; booking and catalog recorded the act only
in a log line and an HTTP response body that evaporated with the request. For an irreversible action
with legal significance, that was thin.

**Re-assessed after WP-07, and it survived with a stronger case rather than a weaker one.** D36's
design note suggested the fan-out might make this package unnecessary, and it retired exactly one of
its two justifications: closing the pending-booking residual needed no schema change at all, because
booking already holds the reference list and can simply hand it over. What used to evaporate was three
HTTP responses that each described one service; what evaporated after WP-07 was **one** response that
is the only account of which legs ran — and the case where an operator most needs to prove what
happened is precisely a 502 where two services erased and the third did not.

**Built as two tables, because there are two facts.** `erased_subject` in booking and in catalog is one
row per person, written once, `erasedAt` never moved — a *local* standing fact, since only the service
that ran a sweep can attest to it and a central register would say "booking believes catalog erased X",
which is the claim that was false the day catalog was never called. `erasure_run` in booking is one row
per fan-out **attempt**, append-only, holding the receipt as it was rendered. That one cannot be
distributed at all: **a leg that fails is a leg that cannot record its own failure**, so the partial
outcome exists only at the orchestrator. Keying either by the subject alone would have had a retry
overwrite the first attempt — D35's `save()`-moves-`erasedAt` defect arriving in a new table.

Pseudonyms only, in both. The route by which that would have been false is worth knowing: a failed
leg's message carries the root cause, and an unreachable leg's root cause names the URL it was thrown
against, which contains the login. The stored receipt is scrubbed; the response is not, and does not
need to be.

Nothing was added to messaging's `erased_subject`, whose emptiness is load-bearing (D35). Neither new
register gates anything, so neither needs `ErasureRegisterGuard`; both javadocs say what changes the
day one is read rather than written, and **WP-08 is that day** — booking's `erasedAt` beside the
booking's own `raisedAt` is exactly the comparison WP-08 needs.

**Not done here:** no desk endpoint reads `erasure_run` back. Who may read an audit trail of erasures
is a question of the same kind D38 answered for the fan-out authority, and it was not answered.

## WP-07 — Erasure: orchestration · DONE

D38. `POST /api/desk/customers/{login}/erase-everywhere` on booking erases here, then calls messaging's
and catalog's existing desk endpoints, and returns one receipt naming each leg, its status and its
counts. It replaces three calls whose individual receipts were indistinguishable from a complete
erasure — which is the defect, rather than the inconvenience of making three calls.

**D37 answered this and corrected the question.** Sequencing does *not* belong in the `hc-admin` desk:
that product shares a signing key with hc-patient and hc-professional, and hc-market is not in that
set — it carries its own `JWT_BASE64_SECRET`, so an hc-admin token fails signature validation here.
The mechanism used is the key hc-market's own five services already share.

**The authority is `ROLE_CUSTOMER_ERASURE`**, named for what it permits rather than for the mechanism
that carries it, and it appears on one endpoint per service and nowhere else — not on booking's own
erasure endpoints, since it permits being a *leg* and booking is never one. Three narrowings, each
enforced by the receiving side rather than promised by the minting one: the token names the single
customer it authorises, it lives thirty seconds, and its subject is `system:erasure-fanout` rather than
the operator, so a leaked copy is not a bearer credential for a real person on every `/api/**` path in
the estate. The shared key already let any service mint anything; what this avoids is turning that
capability into an interface.

**Partial failure is reported, never retried and never refused.** Every leg is attempted whatever the
earlier ones did — refusing to try catalog because messaging was down leaves *more* data in place, not
less — and the response is **200 only when all three erased, 502 with the same receipt otherwise**. 207
Multi-Status is more precise and was rejected on cost of mis-reading: a mis-read 502 costs a retry of
an idempotent call, a mis-read 207 costs a partial erasure filed as a complete one. A failed leg
reports no counts rather than zeroes. The operator's next move is to call it again and to escalate if
the same leg fails twice.

**A retry now really does report zeroes from every service.** This entry claimed it before it was
true — messaging reported `notificationsRedacted: 2` on every subsequent call, for ever. See NEW-1
below and D39; the claim stands as written only from that fix onwards.

**The payload carries the booking references, as D36 asked.** They are read back under the alias
*after* the local erasure, so a retry — which by definition finds nothing under the original login —
still hands over the full list instead of quietly reopening the residual on the path most likely to
hit it.

**D36's residual is closed on the fan-out path and deliberately not on the desk path**, so it is now a
stated boundary between two endpoints rather than "one row somewhere". Both sides are pinned by tests;
the new one was confirmed red beforehand with `notificationsRedacted expected:<2> but was:<1>`.

Found while building it: **nothing verified that the three services run the same privacy pepper**,
which D35 requires and injects three times. A divergence is completely silent — every service keeps
working and one person acquires three irreconcilable aliases. The fan-out compares the aliases it gets
back and reports `ALIAS_MISMATCH`, which needs a deployment fixed rather than a retry.

`HEALTHCONNECT_MESSAGING_BASE_URL` is new and set in all three compose files, with CI's cross-service
base URL check widened to demand it. `ErasureFanoutToken` is copied byte-identically into all three
services and CI diffs it beside `SubjectPseudonym`.

**Not yet done:** run it against the quality box. No test in this repository can prove one service's
token is accepted by another — the two halves are proved separately, which is the limitation D28
recorded — so the handshake is reasoned rather than measured until the estate is up.

Related and unchanged: there is no back-sweep for anyone erased before `erased_subject` existed. On
quality that was test data only, and it has been cleared.

## NEW-1 — A retry reported rows re-written, not rows that held data · DONE

D39. Found on the quality box. D38 and this document both stated that a second `erase-everywhere`
reports zeroes from every service; messaging reported `notificationsRedacted: 2` on every subsequent
call, indefinitely, because those rows are matched by `deep_link` — which is not personal data and does
not change when a body is redacted — and the workflow counted rows it had **re-written** rather than
rows that still held anything. Harmless to the data and not to the receipt: an operator retrying after
a 502 reads a non-zero count and reasonably concludes data was still exposed at the moment of the
retry. Confirmed red first with `notificationsRedacted expected:<0> but was:<2>`.

The rule that came out of it, and the reason the other counters were audited: **a counter keyed on the
customer's login is self-clearing; a counter keyed on anything else has to compare before it counts.**
That audit found one more, in booking — `outboxPayloadsRedacted` matched on the booking's
`aggregate_ref` and counted every event under it, including the dispute events whose payload carries no
customer fields at all, while writing `customerName: "[erased]"` into them. Fixed in the same pass, red
first with `expected:<1> but was:<2>`. The full nine-counter table is in D39; the two that could
over-count are the two that had to reach rows held *by somebody else about* the customer, which is the
same shape as D34's and D36's hardest defects.

## NEW-2 — Catalog's receipt omitted what it deleted · DONE

D39. Catalog deletes the customer's favourites outright — deliberately (D24) — logs it, and reported
only `reviewsDeidentified`. On quality it deleted two favourites and said nothing about them; a
customer with no reviews and a saved list of twelve produced a receipt of zeroes, which an operator
files as "catalog held nothing for this person". The receipt now carries `favouritesDeleted`, which is
the only count in the estate of rows an erasure **deletes**. Confirmed red first with
`No value at JSON path "$.favouritesDeleted"`.

This is the defect D31 fixed in messaging's empty conversation, in a service nobody then checked. The
lesson recorded against it is not about counting: a defect found in one of three copy-pasted services
is a defect reported against all three until each has been looked at.

## WP-08 — Erasure: the still-active account · DONE

D37, built as D40. Erasure does not touch the gateway's user store, so an erased person can log in and
book again. Messaging pseudonymised the new booking's thread while booking and catalog stored the real
login — the estate disagreeing with itself about whether someone exists.

**Built: a booking made after the erasure is stored under the real login; everything that existed
before it stays pseudonymised.** Someone who books again has chosen a new relationship, and the
erasure covered what existed when it ran. Account deactivation was the alternative and was not taken:
it would have made "erased" a tidier state at the cost of locking out somebody who came back.

**Scoped by the BOOKING's age, not the event's** — D37's first wording said to compare the event's
timestamp against `erasedAt`, and a review caught that this means something nobody intended. Every
later event on a booking already open when the erasure ran is timestamped after `erasedAt`, so an
event-timestamp rule puts the customer's real login and name back one lifecycle step at a time, and
breaks D36's guarantee that its residual does not grow. Of D37's two permitted implementations the
explicit one was taken: booking's `OutboxRecorder` now puts `bookingRaisedAt` — `Booking.raisedAt`,
written once at creation and never moved by a transition — on every booking event's payload, and
messaging's `ErasureWorkflow.covers(login, bookingRaisedAt)` compares it to the register's `erasedAt`.
An absent or unreadable value counts as covered, so every event published before the field existed
behaves exactly as it did.

**The condition D39 attached is discharged rather than deferred.** D39 said the first thing this
package does is decide whether the service it turns into a register *reader* needs messaging's
`ErasureRegisterGuard`. It turns none: the reader is still messaging, which has had that guard since
D35, and booking merely publishes a column it already stores. Booking's and catalog's `erased_subject`
stay write-only and stay unguarded.

Confirmed red first, four ways: the returning customer's thread came back as `erased-…` instead of the
login against the old code, and the pre-existing-booking case came back as the login instead of
`erased-…` when the comparison was switched to the event's own timestamp.

**Not done:** no run against the quality box — an erased customer really booking again through a live
estate is the thing no test here can stand in for.

## WP-09 — Erasure: retention and lawful basis · PARTLY DONE

Counsel answered all four questions on 2026-09-03 — **D42**. Retention, lawful basis, controller
registration and data residency each have a position, and the engineering half is built.

**Built.** The single nullable `retentionDays` becomes three categories — financial 2190, operational
365, care summary 90 — read from `HC_RETENTION_*` at startup with counsel's ratified figures as the
committed fallback, so a deployment can be corrected without a release and an unconfigured estate still
runs the ratified policy rather than none. `HC_DPC_REGISTRATION` is read the same way but with **no**
fallback: blank counts as absent, the desk reports `null`, and a placeholder would be a false claim
about a real organisation. `GET /api/desk/privacy` reports all of it beside `enforced: false`.

**D88 SUPPLIED THE NUMBER AND DRAFTED THE THREE DOCUMENTS.** `P0021484082`, committed as the fallback
in `application.yml` — D42 decided the opposite and D88 says why: both of D42's grounds hold for a
*placeholder* and neither holds for the real number, and with no fallback an unconfigured estate
reported `null`, which stopped meaning "not configured" and started asserting **"not registered"**.
Quality still reports null, because the compose files define the variable as *empty* and Spring applies
a `:default` only to an *undefined* property.

**And the care-summary retention category is removed, because it governed nothing.**
`Booking.careSummaryShared` is a `Boolean`; zero field-shaped matches for a condition, an allergy or a
medication across every service's main source, the JDL and the changelogs. The words were only ever in
comments. D42 ratified a 90-day period and a lawful-basis position for data the platform does not hold.
**The cost, chosen with it stated:** counsel's figure is gone, so a future care-summary field needs the
period asked again.

**Three drafts, all marked DRAFT and NOT APPROVED, none served by anything here:**
`docs/privacy-notice.md` (the organisation, with a verified hc-market annex),
`docs/processing-record.md` and `docs/data-transfer-basis.md` (hc-market alone). Every factual claim was
read off source and each says where.

**Still open, and none of it is engineering:**

- the **registration number itself** — ANSWERED. `P0021484082`, D88;
- the **privacy notice and processing record**, following from the lawful-basis answer;
- the **data-residency transfer basis** — a written document, and a decision about whether the estate
  needs one or six, since `webserver` hosts all six products.

**Watch two things.** The retention numbers were authored in the question and ratified rather than
independently proposed, so they are provisional in origin even though they are live in configuration.
And `care-summary-days` **is gone — D88.** It was described here as load-bearing, on counsel's position
that the care summary is ordinary contract data resting partly on it being held briefly. It governed no
data at all: `careSummaryShared` is a boolean and nothing in this estate has ever stored a condition, an
allergy or a medication.

The two coded judgements D37 ratified — the **review body** is not erased (public speech about a
professional) and `Dispute.resolution` is kept (the brokerage's record of a financial decision,
underpinning a compensating ledger entry) — are untouched and stay as they are.

## WP-10 — Payments: the seam can complete a lifecycle · DONE

D15, and the sharpest finding of the payment review. `authorizePayment` used the outcome's state and
reason and **discarded `providerReference`** — the handle `capture`, `refund` and `status` all require.
With no payment table by design, and no log line either, the day a real provider returned `AUTHORIZED`
the money was committed and the platform held nothing to capture or refund it with.

Built as D41. **The handle is stored in a `payment_attempt` table in booking, not in a column on
`Booking`**, and the deciding argument is a sequence rather than a preference: the authorization
happens *before* the booking row is written (D31, so a provider timeout cannot roll back a booking the
customer's screen believed in), so at the instant the handle arrives there is no booking row to put it
on — which is also precisely the case the table exists for. WP-11's webhook needs to find a payment
*by* the provider's reference and WP-13's second provider needs a second attempt against one booking
to not overwrite the first; a column could do neither. Storing it at all is a stated exception to
"derived, never stored", of the same shape as `Professional.verification` (D16) but for a different
reason: a handle issued by somebody else has no source to be derived from, so the choice is store or
lose.

A row is written **only when a handle comes back**, so today's off-platform estate writes none; and the
table holds **no personal data**, so the erasure sweep does not visit it. Both are pinned by tests and
stated on the entity, because the second stops being true the moment somebody adds a customer column.

The rest of the package: `voidAuthorization` on the port and a **compensating release** when
`creator.create` throws — void for an authorization, refund for money already captured, and a release
that itself fails sets `needs_attention` for a person rather than retrying into a provider that has
just failed; `refund` and `capture` both carry an explicit currency now, and `capture` an amount, so a
partial capture is expressible; and `PaymentIntent`'s idempotency promise was **corrected rather than
implemented** — the call site really does mint a reference per request, so two submissions of one
wizard are two charges, and closing that needs an `Idempotency-Key` contract with the client which the
payment seam should not invent unilaterally. The gap is named in the javadoc that used to deny it.

Confirmed red first, three ways: the handle test failed with `Expected size: 1 but was: 0` against the
seam that dropped it; the three compensation tests failed with Mockito's `Wanted but not invoked`
against a resource with no release wired; and the "off-platform writes nothing" test — which asserts a
decision rather than a fix, and so cannot be red against the old code — was made red against a variant
that records every outcome.

**Not done:** no run against the quality box, and no live provider to run against. Every branch is
exercised through a substituted provider, which is D31's limitation unchanged.

## WP-11 — Payments: asynchronous confirmation · DONE, merged as PR #19

D43. Paystack returns an authorization URL the customer must visit; Hubtel and MTN MoMo raise a prompt
on the customer's phone and confirm by webhook. None can truthfully return `AUTHORIZED` or `DECLINED`
from a synchronous `authorize`, so the seam had to guess — and both guesses are bad the same way: an
optimistic one creates bookings for money that never arrives, a pessimistic one refuses every booking
in the estate.

**Built: `PaymentState.PENDING`, a next action on the outcome, and a webhook.** The two questions the
enum answers are now on each constant rather than in a `this == A || this == B` chain, because a chain
gives a new value `false` for both **by omission** — which would have made D43's central decision one
that whoever forgot took. The next action is a kind plus, for a redirect, a URL: two shapes because
the providers produce two, and a client switches on the kind so "a prompt is on your phone" is a case
it renders rather than a link it failed to find. The URL is scheme-checked where it is built, since it
comes from a third party and ends up in a browser's address bar. A pending outcome must carry a
provider reference — the constructor refuses without one, because a pending payment nothing can name is
one no webhook can ever match to a booking, which is D41's dropped handle from the other end.

**The unanswered question was answered: yes, a booking may exist while its payment is pending** — as
`PENDING_PAYMENT`, in front of the state machine rather than in it. The decisive argument is that
*both* answers must store the customer's intention durably, because a payment can confirm after the
browser is closed; so the choice was between holding the booking and holding the request, and holding
the request means a second table carrying six personal-data columns, its own erasure sweep, its own
counter on the receipt, its own expiry and its own reference minting — a booking in everything but
name, reserving nothing. The condition attached is enforced twice: **no `booking.requested` is
published** until the money is confirmed (the guard that matters, since the event is what reaches
messaging), and every professional-facing query already filters by status.

**The webhook contract:** `POST /webhooks/payments/{provider}`, authenticated by the provider's own
signature over the raw body and by nothing else; an unverified caller gets **401 with no detail**, and
today that is every caller, because the unconfigured provider refuses every callback by definition. A
duplicate is **200 and nothing happens** — idempotency is decided from the booking's state under a row
lock rather than from a seen-set, because what must not happen twice is the transition, not the
callback. A callback that overtakes its own booking gets 404 so the provider retries; money confirmed
after this platform released it is flagged for a person rather than retried.

**What keeps it off the internet is D28's property, not the security chain**: the gateway routes match
`/services/<service>/api/**` and this path is not under `/api`. WP-13 makes it reachable and must add
*two* things — the route and a gateway permit — or the callback silently never arrives.

**Not done:** no run against the quality box, no live provider, and no expiry for a pending booking
that is never confirmed. It blocks nothing today: nothing in this estate reserves an availability slot
when a booking is made, which was checked rather than assumed, and the day that changes the
reservation should ignore stale pending bookings at read time rather than acquire a sweeper.

**Reviewed 2026-09-03: nine findings, eight real, all fixed on the same branch.** The largest was not
WP-11's at all — JHipster's generated `BookingStatusChangeResource` was still mounted, so any
`ROLE_USER` token read the estate's whole status-change history (292 rows on quality) and could forge
or delete an audit row. Deleted, as `BookingResource` and the two dispute resources already were, with
a new `AuditTrailIsNotAnApiIT` that fails if regeneration puts it back. In the payment code: a release
that *failed* was indistinguishable from an untouched payment, so the worst case in the estate was
logged as a benign race; a released row's `VOIDED` was overwritten by the `FAILED` that followed it; a
reused provider handle was matched by recency rather than to the booking that is waiting; a malformed
body escaped as 500 while a forged one got 401, which is an oracle; `cancellation-preview` quoted a
late-cancellation fee at full price against a pending booking whose money had never moved; a `PENDING`
outcome could be built with no next action; and `permitsBookingIsExhaustive` hand-listed seven of eight
states, so the test whose job was to break when a state was added did not break when `PENDING` was.
The ninth — a lossy charset on the raw webhook body — **did not reproduce** and the code was left
alone: the converter in this application's context is UTF-8 and JSON is special-cased regardless,
checked rather than argued. D43 carries the detail, including the correction to its own "enforced
twice" claim, which was true of the design and not of the estate while that CRUD endpoint was up.

## WP-12 — Payments: the zero-amount booking · DONE

D44. Two seeded services are genuinely free, and "from ₵0" is correct rather than a bug.
`authorizePayment` ran unconditionally, so a real provider was going to be asked to authorize 0
pesewas and refuse it — every free booking in the estate uncreatable the day a provider is configured,
with nothing going red until then, because the unconfigured provider answers `OFF_PLATFORM` to any
amount at all.

**Built: no provider is asked.** The condition is in `BookingPayments.take` rather than at the
call site, for the reason `PaymentRecorder` holds "no handle, no row" rather than trusting its callers
— and because WP-13 adds a second call site. Zero exactly: a negative price is a defect in whatever
priced it, and treating it as free would be this service deciding the platform owes the customer
money.

**The behaviour, not only the guard.** The booking is created in **`REQUESTED`** and
`booking.requested` is published, so the professional hears about a free booking exactly as about a
priced one. Not `PENDING_PAYMENT`: **nothing will ever confirm a payment that was never started**, and
D43 deliberately gave that state no expiry sweep, so a free booking would have waited there for ever
unseen. No `payment_attempt` row, which follows from D41 rather than being a second decision — a row
is written only when a handle comes back and nobody was asked for one.

**`PaymentState.NOTHING_TO_PAY` rather than reusing `OFF_PLATFORM`.** Every mechanical consequence of
reusing it would have been right; what it costs is the ability to say anything true. `OFF_PLATFORM` is
a claim about who paid whom, nobody pays anybody for a free session, and the value should **stop being
produced** the day a provider is configured — free bookings wearing it would answer "is any money in
this estate settled off the platform?" yes for ever. It is the one value in the enum no provider
reports. Adding it turned **both** of D43's guard tests red before a single answer was compared, which
is those tests working one package after they were written.

**Also built: a provider that throws now answers `FAILED`** and gets the 502 a provider that answered
`FAILED` always got, instead of a 500 and a stack trace — which is the shape every real adapter's
failure will take. The reason is composed from a provider name and an exception class, never copied
from the provider's message: it is rendered into a response body, and that is the route by which a
customer's phone number arrives (D39, D41, D43 each met the same hazard by a different road). Only the
provider call is wrapped; a recorder that throws stays a loud 500, with a test saying so.

**And the ordering trap is documented rather than fixed.** `@ConditionalOnMissingBean` is only reliable
in an auto-configuration, so `PaymentConfiguration` carries a warning about what that costs. The first
version of that warning had the mechanism backwards and the review rewrote it — see below. The cheap
order-independent fix — `@AutoConfiguration` plus an `.imports` file, which is the ordering the
annotation assumes, with `@SpringBootApplication`'s exclude filter keeping the class from being scanned
twice — is recorded in D44 and deliberately **not built, so not measured**: WP-13 deletes the condition
it would protect.

Seven new tests, five unit and two integration, plus the two D43 guard tests updated. Three were
confirmed red first: the free booking at the endpoint with the provider stubbed to refuse a zero
amount (`Status expected:<201> but was:<402>` — the defect itself), the same thing at the seam on
`verifyNoInteractions(provider)`, and the throwing adapter twice
(`Status expected:<502> but was:<500>`, and an escaped `IllegalStateException`). booking: 114 unit +
101 IT green on a full `clean verify`.

**Reviewed 2026-09-03: four findings, all real, all fixed on the same branch — and three of the four
were documents claiming a property the code did not have.** The largest: `authorizePayment` still
relayed `outcome.reason()` verbatim into the response body, so "the reason is composed, never copied"
was true of the *thrown* path this package added and false of the *answered* path beside it, which is
the common one. Red at the endpoint with a stubbed `declined("Declined — card ending 4242, Ama Mensah,
0244123456")` coming straight back as the ProblemDetail's `detail`; the refusal message is composed
from the state now and the provider's words go to the log. Second: nothing stopped an adapter answering
`NOTHING_TO_PAY` for a priced booking, which is the quietest failure in the seam — a ₵150.00 booking
created in `REQUESTED`, the professional told, no money moved and nothing anywhere disagreeing. `take`
knows the amount, so it refuses it there as a `FAILED` and a 502. Third: `provider.name()` was called
unwrapped, twice inside the very catch block this package added, so an adapter whose `name()` throws
landed back on the 500 that catch removed; wrapped now at all six sites, `release` included. Fourth:
the `@ConditionalOnMissingBean` warning had the mechanism **backwards** — a component-scanned provider
is always visible to the condition, and the explicit `@Bean` the warning recommended is the shape that
collides — so the advice caused the failure it warned about. Verified against the Spring 7.0.8 sources
and pinned with two context tests. Two smaller wording corrections went with them: D44's account of a
negative price (a 500 from `@Min(0)` today, not the 402/502 it claimed) and `nextActionFor`'s javadoc
(the state it carries is always `PENDING`). Nine more tests, seven of them confirmed red first. booking:
**120 unit + 104 IT**. D44 carries the detail.

**Not done:** no live provider and no run against the quality box. The zero-amount defect existed
precisely because the estate's only provider answers the same thing to every question, so the
substituted-provider limitation D31, D41 and D43 all recorded is doing more work each package.

## WP-13 — Payments: provider choice and Act 987 · PARTLY DONE

**D86 wrote Paystack's other four calls, over a stated objection, and two of them turned out not to
exist.** The architect chose this after being told it was guesswork; that is their call and D86 records
it as one. Measured first: **`hc-crowdfund-app` calls only `/transaction/initialize`**, so there was
nothing in this workspace to source the rest from, and nothing here has spoken to Paystack.

**`capture` and `voidAuthorization` have no Paystack equivalent in this flow.** Initialize is a charge
and not a two-step hold, so no authorization is held to capture and none exists to void — before the
customer pays there is nothing, afterwards the operation is a refund. Both keep their refusal and
override it only to say *why*, because "not integrated" and "this provider has no such operation" are
different facts. **That is also the answer to D43's dead end**: a `PENDING_PAYMENT` booking's payment
cannot be voided because Paystack has no void.

**`status` is written and read-only** — `GET /transaction/verify/{reference}`, the call a sweep would use
for bookings D43 leaves waiting. Every non-success keeps the reference; an unrecognised status is
`FAILED` and never `PENDING`; an answer naming a different reference is refused rather than believed.

**`refund` is written and OFF by default**, behind `healthconnect.payments.paystack.refunds-enabled`,
separate from `enabled`. Accepted is not done — `REFUNDED` only for a status saying the money is back —
and a non-positive amount is refused before the wire, because Paystack treats an absent amount as a
**full** refund.

**36 unit tests (was 25)**, including what the refund actually sends: path, bearer, `transaction`, and
`amount` unchanged at 15000. **Written is not verified**: no call here has reached Paystack, and that is
the sentence to re-read before enabling refunds.

**Act 987 is still unanswered** and `PaymentProvider` still has no method that pays the professional,
which is what lets the answer go either way without a rewrite.

D45. Everything around the providers is built and verified; the providers themselves are not, and that
is the package's honest boundary rather than an unfinished afternoon.

**Built, and all four of the things WP-11 left behind (D43):**

- **the registry.** `PaymentProviders`, keyed by the name each adapter answers to.
  `@ConditionalOnMissingBean` is **deleted** — three providers is a shape one-bean-wins cannot express
  — and D44's ordering hazard evaporates with it, because nothing injects a `PaymentProvider` by type
  any more: two provider beans are two entries whichever order they are parsed in, asserted both ways.
  The fallback is injected **by bean name**, excluded from the customer's choices by identity, and
  still reachable by a callback so that it refuses one itself. The webhook's "addressed to a provider
  this service is not configured for" refusal is a registry lookup now and does real work;
- **the customer's choice**, under D22's rule. `CreateBooking.paymentProvider` decides who ends up
  holding the money, so an unoffered name is **409**, more-than-one-and-none-named is **400**, and
  nothing is ever defaulted on the customer's behalf. Nothing configured behaves exactly as before;
  one configured provider is the default. Resolved **after** the zero-amount guard, or every free
  booking becomes a 400 the day a second provider arrives, and carried on `Taken` so a release goes
  back to whoever took the money;
- **both lines per environment.** The fifth gateway route in all three compose files *and*
  `PaymentWebhookRouteConfiguration` permitting POST on it. CI checks the pair — the route check now
  allows exactly one webhook predicate matched in full, a second check greps the gateway's permit
  for the same string, and a third (added by the review) pins the route's target and prefix, because
  the others read predicates only. `PaymentWebhookRoutePermitIT` asks the running container, which is
  the only check of the four that notices when `@Configuration`, `@Bean` or `@Order` goes;
- **each provider's signing secret**, handled like the estate's other two and never committed.
  Optional rather than required, because a provider nobody enabled needs none; **absent means callbacks
  are refused, not trusted**, and an enabled-but-secretless provider refuses with the same flat 401 an
  unimplemented one gives.

**Deliberately not built by WP-13: the three adapters' wire formats.** WP-13 had no network access, no
provider account and no credentials, so Paystack's, Hubtel's and MTN MoMo's documentation could not be
read and none could be called. The classes exist, extend `ProviderAwaitingIntegration`, fail closed on
every call, and carry a documented list of exactly what each still needs — the authorization call and
its response, which field is the durable handle, the status vocabulary and its mapping, the callback
payload, and the signature algorithm with what bytes it covers. A signature check or a status mapping
written from plausibility would have compiled, passed the mocks written to match it, and been fiction
on the one path where a customer's money is already committed.

**One of the three is now built — D50, on `payments-the-provider-we-can-finally-name`.** Not by
acquiring an account: `hc-crowdfund-app`, a sibling product in this workspace, has run a live Paystack
integration for months, and its adapter, tests and configuration answer five of D45's six questions
outright while the sixth turns out not to apply. Nothing was copied — different seam, different
domain, different Jackson — but the wire format is a fact about Paystack rather than a property of
that codebase. D43's guessed callback scheme is **confirmed** word for word, header spelling and
digest encoding included, and the hedging is removed where the claim is now known.

**Two of the six calls, and the other four still refuse.** `authorize` and `readCallback` are the
booking path. The working integration does `initialize` plus the webhook and nothing else, so the
evidence runs out exactly where `capture`, `refund`, `voidAuthorization` and `status` begin — and an
invented call inside a class that otherwise works is worse than a class that refuses everything,
because it looks like an integration. Stated cost, not discovered later: a `PENDING_PAYMENT` booking
whose creation then fails cannot have its live payment cancelled, so the attempt row is flagged for a
person. **D43's dead-end cancel is therefore still a dead end.**

**And Paystack still cannot take a payment on this estate, for a reason that is a decision rather
than an omission.** Its initialize requires the customer's **email address**; `PaymentIntent` carries
a login and no contact details, deliberately; and the only defensible source is the gateway's account
store, which has no endpoint to ask. Two cheaper sources were considered and rejected — a field on the
booking request (D22's rule verbatim, and the prototype renders the email read-only from the BridgeCare
record rather than asking for one) and the login when it happens to be email-shaped (works for a subset
of users and fails at the moment they pay). So `CustomerContacts` names the boundary, has **no
implementation**, and `authorize` refuses before the round trip: 502 and no booking, which is what a
priced booking naming Paystack did before this package anyway. **Whoever may decide who reads the
account store closes it with one `@Component`.**

Three further things are refused rather than guessed: a currency other than GHS (the evidence sends no
currency field, so the amount is denominated by the merchant account — a silent mis-charge rather than
a rejected call), a secret that does not start with `sk_` (Paystack lists `pk_` beside it and pasting
the wrong one 401s only when a customer first pays), and any callback event that is not
`charge.success`.

Corrected in passing, because it had become false: the startup log and all three compose files
announced that **every** enabled adapter is unimplemented. That claim now comes from
`integratedCalls()` on the adapter itself, so an implemented one stops making it by construction.

**Act 987 is unanswered and was not answerable here.** Whether a split-settlement model clears it is a
question for a person with standing. What the package protects is the property that keeps the answer
cheap either way: `PaymentProvider` still has **no method that pays the professional**, and the package
documentation says so where an implementer will meet the temptation.

**Reviewed 2026-09-04 — five findings, all fixed.** Recorded in D45's review section. The largest
was not the one the review named: the webhook permit's `@Order` sat on the `@Configuration` class,
where Spring never reads it, so the chain was running ahead of the generated one on component-scan
order alone and renaming the class would have made every provider callback a 401 with nothing failing.
Both the webhook and the public chain now carry the annotation on the `@Bean` method. Also: a name
collision between two providers is refused at startup rather than silently making one of them
unreachable, the CI route check pins the webhook route's target and prefix, and two documents were
corrected about their own subject.

**One of those was closed on 2026-09-10 — the email, D72 §3 and D74, on
`wp13-the-customers-email`.** The item was a *decision* rather than an implementation and the architect
took it: the source is the gateway's own account store, asked over `GET /internal/customers/{login}/email`
with the short-lived estate-signed token D38's mechanism mints. `CustomerContacts` has one implementation
now — `GatewayCustomerContacts` — and Paystack's `authorize` can obtain an email for a login that has one.

**Precisely which half closed.** The *source* question, and nothing else. A payment is still not taken end
to end (no account, no credentials — D50), `capture`, `refund`, `voidAuthorization` and `status` still
refuse, Hubtel and MoMo are still seams needing a phone number through the same door, and **Act 987 is
still a stated blocker with no code behind it** (D72 §4). So WP-13 stays **PARTLY DONE**.

Four things D72 §3 left to the package, each argued in D74:

- **a second credential, not the erasure one.** `ContactLookupToken` — subject `system:contact-lookup`,
  authority `ROLE_CUSTOMER_CONTACT_READ`, claim `contact_subject`, thirty seconds — and
  `FanoutTokenMinter.forContactLookupOf` beside `forErasureOf`. Reusing the erasure token would have
  presented the gateway with a credential claiming to authorise an erasure while reading an email, and the
  scope everybody believed in would have been the name of a method. **The FIFTH verbatim-copy family**:
  byte-identical in booking and the gateway, CI diffs it, the gateway's copy is the reference, and the
  family is derived with `find` rather than listed;
- **a reactive chain, `@Order` on the `@Bean` method.** catalog's servlet config does not copy, and its
  own `@Order` was on the class where Spring cannot read it — **NEW-34**, since closed by **D77**, which
  re-measured the claim in the servlet stack rather than porting this one and found the same answer in
  three files rather than the one this item named;
- **what keeps it off the internet, and D72 §3's terms were wrong about it.** D28's route-predicate
  argument does not transfer to an endpoint on the gateway itself, because there is no route in front of
  it: both nginx vhosts proxy `location /` here and dev and quality publish the port on every interface.
  What refuses a stranger is the *credential*, plus `mayRead`'s narrowings — the authority alone is
  grantable to a real account by an administrator. **Measured, and it overturned the premise the chain was
  written on**: reactive Spring Security *denies* an unmatched exchange, so the chain opens a door rather
  than closing one, and deleting it breaks payments rather than leaking an address;
- **four failure cases, and a third answer.** `Optional.empty()` for a fact about the account (no email,
  no such login); a new `CustomerContacts.ContactsUnavailable` for "could not ask" — unreachable, 5xx,
  unreadable, or the gateway refusing the estate's own token. All end in 502 and no booking; what differs
  is the log, and folding them would print "this estate holds no email" for an estate that holds one.

**Fourteen mutations were run and eleven are red.** The three that are green are named in D74 §3 and §6 —
two guarded by a CI grep, one unreachable — rather than claimed as covered.

**Reviewed 2026-09-10 — no blocking findings, one should-fix and four optionals, all applied.** Recorded
in D74 §6. The should-fix was a 404 from a **misdeployed base URL** logged as a fact about somebody's
account, since every Spring service in the estate 404s on a path it does not map; the endpoint's 404 now
names the login and booking refuses one that does not. **Two of the five inverted their own premise when
run**: the review's suggested ProblemDetail check could not work (our own 404 had no body), and the
negative-span term it asked for turned out to be **unreachable** — `Jwt.Builder` refuses `exp` before
`iat`, so the test written for it went red at the fixture. The term is kept and the *framework's* refusal
is pinned instead, with both the javadoc and the test saying plainly that nothing covers the term itself.
The review also established two claims more strongly than this package had: the authority is granted by
no code path in either service, and `system:contact-lookup` is unconstructible as a login because
`LOGIN_REGEX` admits no colon — so "matches no user in any store" is enforced by validation rather than
by convention.

**Still open:**

- an implementer with credentials for **Hubtel and MTN MoMo**, working from the lists on those two
  classes. `hc-crowdfund-app` has a Hubtel adapter too, which is where its evidence will come from;
- **Paystack's other four calls**, which need documentation the sibling product does not exercise.
  `refund` and `voidAuthorization` are the ones with a caller;
- Act 987 itself, and with it whether settlement is split-at-capture or reconciled afterwards. **D72 §4
  refused to build a settlement seam behind a flag** and D74 did not reopen it: a retention period behind
  a flag is a number waiting for counsel, a settlement seam behind a flag is a capability this platform
  may not be licensed to have, built on the assumption that it may;
- **Hubtel's and MoMo's phone number**, which is the same door one field wider and is deliberately not
  D74's. What is not obvious, and is nobody's to decide inside the payment seam, is whether a phone number
  is the same disclosure as an email;
- no run against the quality box, and no live provider — the sixth package in a row to say so, though
  Paystack is the first adapter for which a sandbox run is something somebody could actually do;
- no endpoint publishes the provider list. Deliberate (D45): no screen asks for one, and the 400 that
  demands a choice names what there is to choose from. Revisit when a payment screen exists;
- `PENDING_PAYMENT` is still a dead end for the customer's cancel (D43), because releasing a live
  authorization needs a provider that can be asked and none of the three can be — including Paystack,
  whose `voidAuthorization` D50 deliberately left refusing.

**Reviewed 2026-09-05 — five findings, all applied.** Recorded in D50's review section. Two of them are
the *rule* behind fixes D50 had already made to two *instances*, and one of those was a trap laid for
whoever writes Hubtel: `PaymentOutcome.failed(reason)` nulls the handle, it is public, and it is the
obvious line to write in `readCallback` — every failed payment written that way stranded its booking in
`PENDING_PAYMENT` for ever under a WARN blaming the provider. It is refused at the endpoint now, with
the same flat 401 and an ERROR naming the adapter. Also: the startup log said the adapter was enabled
and implemented while every priced booking naming it 502s for want of an email, so the missing
`CustomerContacts` is a WARN at boot; two loose `RuntimeException` assertions were narrowed and two
signature shapes added; and `PaymentCallback`'s "bytes as received" now names the Boot charset default
it depends on. The reference divergence from the source integration is **kept and documented** rather
than closed — NEW-11.

## WP-14 — Verification badge · DONE

D16, D31, D33. The profile states what the badge means and carries `verifiedOn`; the reviewer login and
evidence reference stay behind `ROLE_BROKERAGE`. D33 fixed a real disclosure defect: the date scanned
past a later suspension, so `SUSPENDED` and an old `verifiedOn` shipped together and any client
rendering "Verified on {date}" showed a badge for someone whose verification had been removed. The
regression tests were confirmed to fail on the old code before being kept.

## WP-15 — Badge: date-only on the wire · DONE

D47. The DTO comment said "the DATE ONLY" and the field serialised a full `Instant`, to the
nanosecond — disclosing when desk staff work, which is adjacent to the reviewer identity D16 keeps
private, and contradicting the DTO's own documentation. It is now a `LocalDate`, and **only the
rendering changed**: `VerificationReview.reviewedAt` is still an `Instant` and the desk endpoint
still returns it in full.

**The zone was the decision, and it is `Africa/Accra`, named as `MarketplaceService.BADGE_ZONE`.**
Not the JVM default, which is right on every machine in this estate and was already wrong on the
workstation this was built on — `Europe/Berlin`, so the unit test written against a
`systemDefault()` implementation reported `expected: 2026-01-14 but was: 2026-01-15` with no fixture
arranging it. And not the professional's own `zoneId`, because D21 gives that the wall clock of an
**appointment**, and a verification is not delivered anywhere — it is BridgeCare reading documents at
a desk, which D21 puts in its other category, the `Instant` recording "when did this happen". Stated
consequence: a review recorded at 23:40 in Accra is dated the 14th on the badge and is already the
15th for a reader in Nairobi. The badge names the day BridgeCare did the work, in BridgeCare's
calendar, and reads the same to everybody.

**Same package: the non-disclosure is pinned now.** D16 kept the reviewer's login and the evidence
reference behind `ROLE_BROKERAGE`, and until this that was true only because nobody had added them to
the public projection — no test anywhere would have gone red. `thePublicProfileDisclosesNeitherReviewerNorEvidence`
verifies a professional with a real evidence reference at the desk and asserts the **serialised**
public body carries neither key nor value. On the body rather than the DTO, because a serialiser
being helpful is invisible to a test that inspects a Java object.

Both confirmed red first, and the second the only way it could be — by temporarily adding `reviewer`
and `evidenceRef` to `ProfessionalDetail` and watching it quote the leaked body back
(`"reviewer":"ama.brokerage","evidenceRef":"CID-2026-0041"` … `not to contain: "reviewer"`), then
removing them.

**D33's two regression tests still mean what they claim, and one of them is stronger than the other
— which this entry and D47 first got wrong.** They assert presence and absence across a
`VERIFIED → SUSPENDED → VERIFIED` history, which a type change does not touch, so both still pass.
But only `suspensionClearsTheDate` goes **red** against D33's own defect: `reVerifyingRestoresTheDate`
asserts non-null after a re-verification, and a date that scans past a suspension is also non-null
there, so it never could. It is a guard against the over-correction, not against D33. Corrected in
D47 after the WP-15 review watched the other one fire (`expected: null but was: 2026-09-04`).

No client breaks, checked rather than assumed: the prototype's
`p.verifiedOn ? … fmtD(p.verifiedOn.slice(0,10)) : ''` — the truthiness guard is the point, so the
null every seeded professional carries renders nothing — makes the slice a no-op instead of a
truncation, and `parseD` wants exactly what a `LocalDate` serialises to;
`verify-prototype-live.mjs` reads `p1.verified`, not the date; `verify-cycle.sh` reads neither.
The null case was then confirmed against the running box and in a real browser: `"verifiedOn":null`
for all 18, no page error on any of the 18 profile routes.

**Reviewed 2026-09-04 — four findings, three prose and one a real omission, all applied.** The
omission: the implicit-zone inventory was one short and the missing one **writes**. Two test-strength
notes were taken as well — the non-disclosure assertion is `doesNotContainIgnoringCase` now, red first
against a key-only `Reviewer` leak the old form was green on; and `thePublicDateIsADate` reads the
Accra date either side of the stamp instead of after it, closing a one-millisecond-per-day flake at
Accra midnight in a test about midnight. catalog: **88 unit + 127 IT** green on a full `clean verify`.

**Named, not fixed: five implicit-zone `LocalDate.now()` calls in catalog, not four.** Four render —
`ReviewWriteResource`, `MarketplaceResource`, twice in `ProWorkspaceResource` — and `Review.publishedOn`
is a stored date, so correcting that one is a data question. The fifth, `CatalogSeeder:105`, is
**NEW-9** below and is the consequential one. D47 tabulates all five.

**Not done:** no run against the quality box for the *desk* half. The 18 seeded professionals have no
verification review history, so `verifiedOn` is null on every one of them and the box cannot exercise
a non-null date without a desk call being made against it first. The null half was exercised.

## WP-16 — Search performance · WON'T, for now

D19. Search is `contains()` in Java over every card. **Measured rather than deferred on feel**: p95
26 ms at 18 professionals against a 5 ms control, where D19's own trigger is ~200 professionals or a
latency measurement. Neither is met. The figures and the re-measure command are in D19; revisit when
the catalogue grows.

## WP-17 — Video and WhatsApp providers · PARTLY DONE

D17, D18. Budget for a video provider and a WhatsApp BSP, if either is wanted.

**D86 built the seam the architect asked for — and found that this item's block never applied to v1.**

**D17's recommended v1 needs no provider, no account and no budget:** *"the professional supplies their
own meeting link (Meet, Zoom, whatever they already use); the platform stores it and reveals it an hour
before, which is the promise the prototype makes."* Daily.co is named as *"the upgrade if a no-account,
in-browser room with a waiting room is wanted later"*. So the budget question is about the **upgrade
alone**, and this row has been reading as though it blocked the feature.

**Built**: `MeetingRoomProvider`, `MeetingRoom`, `UnconfiguredMeetingRoomProvider`,
`ProviderAwaitingSelection`, `DailyCoMeetingRoomProvider`, `SessionConfiguration`, behind
`HC_DAILYCO_ENABLED` and off by default. The default is **not a refusal** — it reports
`professionalSupplied()`, which is D17's v1 as far as the seam is concerned; a selected-but-unwritten
provider refuses and never falls back; the refusal names what it needs; and there is **no recording, not
even as a seam**, with a test asserting the interface has exactly `name` and `create`. One bean that
chooses rather than two and a condition, because that construct is D44's ordering hazard.

**Still blocked, and now only on what it always was**: budget and a choice between Daily.co and
self-hosted Jitsi for the hosted upgrade; and a BSP account for WhatsApp, which D18 puts at v1.5 and
whose v1 — in-app — is the `Notification` table and is built. A WhatsApp seam would need a channel, a
delivery state, a provider reference, a dedupe key and an outbox (D18's own costing): a package, not a
flag.

**v1 is unblocked and is NEW-45.** Nothing calls the seam, deliberately: `Booking` has nowhere to store
a room, and adding a field is a JDL change with a Liquibase changelog behind it.

## WP-18 — Production `infranet` alias · PARTLY DONE

D28/D30. Whether `gateway` is already a DNS alias on production's shared `infranet`. Cannot be answered
from a workstation. Largely defused — the production compose services were renamed `hc-market-*` with
explicit container names, so a collision is impossible whatever else is on that network — but the
question itself is still unanswered on the host.

**D86 wrote the question as one command: `deploy/probe-infranet-aliases.sh`.** Read-only in the strong
sense — no container, no network join, no pull, no file — because `docker network inspect` alone answers
it: compose records a container's aliases on the network object. It takes **no `--host` and no
credential**, deliberately, since production is halted here and a script that could reach out is one
somebody could point at production by accident. Whoever has access runs it there.

Verified against `hcnet` (15 containers) and in all three states, each naming its own cause: network
present, network absent, daemon unanswerable — D71's rule, since docker exits 1 and prints `[]` for both
of the last two.

**Its output already found something.** On `hcnet` every name is a container name and **not one** is a
compose alias, independently confirming D68's recorded state; the probe says so when it sees it, because
then "free" describes a moment and a compose recreate would republish every service name at once.

**`PARTLY DONE`, and it was `CLOSED` in the table against `BLOCKED` here** (decisions.md D83, backlog
NEW-41). Neither fitted: the rename shipped and is verified, so `BLOCKED` understates it, and the
question is still unanswered on a host nobody here can reach, so `CLOSED` overstates it. `PARTLY DONE`
is the vocabulary's own word for exactly that split — *"some of it is built and verified while the rest
is blocked on a named person"* — and the half that is blocked needs **production host access**, which is
WP-19's dependency too.

## NEW-3 — the receipt scrub is real, but its test cannot fail · DONE

`ErasureFanout.record` replaces the login with the alias before storing the receipt, because a failed
leg's message can name the URL it was thrown from — `/api/desk/customers/<login>/erase` — and that row
is kept for ever. Good reasoning, and the substitution is correct.

**`theRecordNeverNamesThePerson` does not prove it.** Verified by deleting the scrub outright and
re-running: the test still passes. The failure mode it drives is a read timeout, and that message is

```
messaging gave no usable answer (SocketTimeoutException): Error while extracting response
for type [java.util.Map<java.lang.String, java.lang.Object>] and content type [application/octet-stream]
```

— no URL, so no login, so nothing for the scrub to remove and nothing for the assertion to catch. The
test passes because the leak is absent on that path, not because the scrub closed it.

**Fixed as D40.** The test now drives a **refused** connection — messaging's stub is stopped for that
one test and rebound afterwards — which arrives as a `ResourceAccessException` reading `I/O error on
POST request for "http://…/customers/<login>/erase"`. Confirmed both ways before being kept: with the
scrub deleted the new fixture fails, quoting the whole stored receipt with `ama.tobeforgotten` in it,
and with the scrub deleted the *old* read-timeout fixture still passes — which is the defect, restated
as a measurement.

**And the related question was settled by that same test, and it was a real defect rather than a
hypothesis.** The gateway's `LOGIN_REGEX` permits `? ^ ` { | }` and `@`, and `RestClient` strictly
encodes a URI variable, so `ama?forgot@example.com` reached the kept row as
`ama%3Fforgot%40example.com` while `receipt.replace(login, alias)` looked for the unencoded spelling
and matched nothing. Red first with `not to contain: "ama%3Fforgot"`. The scrub now matches every
character as itself or as its percent-encoding, hex case-insensitively, rather than calling whichever
encoder the client happened to use — `RestClient` and `UriUtils.encodePathSegment` already disagree
about `@`. A login needing JSON escaping is still not handled; `LOGIN_REGEX` permits none, and the
limit is written beside the code.

---

## The quality run of `1eadc7a`, 2026-09-04

The first quality run since WP-09, five packages back — WP-10, WP-11, WP-12 and WP-13 each closed
with the sentence "no run against the quality box", and this is what four of those in a row cost.
**The box had been serving a five-package-old commit for twenty-six hours while reporting healthy**,
which is not a defect in anything: `quality/startup.sh` defaults `TAG` to the current commit and
refuses `latest` exactly so a stack cannot run something nobody chose (D13, D14). Nobody had run it.
*(Recorded as the run reported it; the twenty-six hours were not independently measured here.)*

Brought to `1eadc7a43db09eb8e9928909c2c3494854890cf6` and seed-exact, it then found four defects,
none of which any test suite could see — the fourth time the standing constraint at the bottom of
this file has had its count raised. NEW-4 to NEW-7 below; the reasoning is D46.

Two of the four are worth reading together. **NEW-4 and NEW-5 are the reason the other two were
never going to be found by a test**: one made the end-to-end scripts unable to address the box at
all, and the other made the box's own verifier report a fault after they had run. The tooling that
exists to exercise a live estate had quietly stopped being usable against the only live estate there
is.

## NEW-4 — Both end-to-end scripts could only address the dev estate · DONE

D46. `verify-cycle.sh` pinned `localhost:18201`/`18202` and `healthconnect-dev-<svc>-db-1`;
`verify-outbox-recovery.sh` pinned 18202/18203 and the same container shape. Quality is 18100–18103
with `hc-market-quality-*`, so **neither script could run against the box they most need to run
against**, and with the dev stack in a restart loop they would today have failed against both.
The pinned ports were not even `deploy-dev.sh`'s defaults — they were `CLAUDE.md`'s *override
example*, so the scripts had been pinned to one operator's shell since they were written.

Both are parameterised on `deploy-dev.sh`'s own `HC_*_PORT` names and defaults, plus one variable
per database container; `HC_DEV_BOOKING_CTR` becomes `HC_BOOKING_CTR` with the old spelling still
honoured. And both now **refuse to run against an inconsistently addressed estate** — a port from
one and a database from another is silent and produces a page of plausible failures — by comparing
the compose project label of every container they touch. In `verify-outbox-recovery.sh` that guard
is load-bearing: the script disconnects a named container from `hcnet`, so the wrong name severs
somebody else's service and the reconnect trap only restores the one it cut.

Proved the only way it can be: both run against the live quality estate. `CYCLE PASSED`,
`OUTBOX RECOVERY PASSED`.

## NEW-5 — A passing `verify-cycle.sh` made `--verify` report a fault · DONE

D46. `--verify` asserted `reviews == 63`; the cycle script publishes a review and reviews cannot be
deleted (spec §7). So a successful cycle left the box at 64 and the next `--verify` printed
`✗ reviews through the gateway got 64 want 63` and exited failure — two tools each working
correctly, arranged so one reports the other's success as a defect.

Counts are split by whether anything here writes to them: `professionals` stays **seed-exact**,
`reviews` becomes **seed plus recorded activity** with the surplus printed (`64 (seed 63 + 1
recorded)`) rather than swallowed. The exactness given up is replaced by something stronger against
the failure that mattered — p1's rating and reviewCount, from the `professional_rating` view, must
equal the average and the count of the reviews the API serves from a different endpoint. "Derived,
never stored" asserted directly, and true whether or not the box has been exercised.
`verify-cycle.sh` also states what it wrote and how to reseed, because half of this defect was a
tool that changed an estate and did not say so.

**The cost, stated:** `--verify` reports rather than fails when somebody has hand-written reviews
into the box. The collision check is stronger than before, not weaker — `at least 63` fails on a
non-number exactly as `== 63` did.

**Reviewed 2026-09-04 — seven findings, all applied; D46 §5 carries the detail.** Three of them
were documents claiming a property the code did not have. The largest was that this fix landed in
`quality/startup.sh` and not in `deploy/deploy-dev.sh`, whose `verify_seed` had the same
seed-exact-plus-seed-file-average pairing and is called by `up` as well as `reseed` — so a
successful cycle against a dev estate made the next `up` without `--clean` die, which is this defect
verbatim, one script over. NEW-4 is what made it reachable. Also: the derivation check was justified
as a collision check while being asked only on loopback, where no sibling can answer (it runs
through `$SITE` as well now, and three documents are corrected); it averaged one page of 200 against
an uncapped `reviewCount` and would have reported a plausible wrong number past 200 reviews on p1
(it pages now, and refuses a page it could not complete); an empty compose label collapsed into the
container name in both end-to-end scripts' estate guard; `verify-outbox-recovery.sh` required a
published messaging port for a service it only ever reads from the database; and
`verify-cycle.sh`'s header documented an `HC_BOOKING_DB_CTR` that `q()` does not read.

## NEW-6 — A refusal that offered the one name it withholds · DONE

D46. `PaymentChoiceRefused`'s empty-offer sentinel was the literal `"none"`, and
`UnconfiguredPaymentProvider.name()` is also `"none"`, so `paymentProvider: "none"` answered *"this
estate does not offer that payment provider; it offers: none"* — pointing a client integrator at the
one name D45's `choices()` exists to keep off the list. Behaviour correct, prose wrong. The fallback
keeps its name (a URL segment, a property key, a column value — D45 chose it deliberately); the
sentinel moved, and the empty case now states the fact instead. Red first, quoting the whole
sentence.

## NEW-7 — "Sessions brokered" was not live, under a LIVE banner · DONE

D46. Discover's fourth hero stat was `PRO_HISTORY.length + BOOKINGS.length`; `PRO_HISTORY` is never
repopulated in live mode and `BOOKINGS` only is behind a token, so the closed demo and the live
estate both displayed **18 / 16 / 63 / 269** while the estate seeds 256 sessions. The `p.rate` /
`₵NaN` class exactly, except that it renders a plausible number, so nothing looks broken and nobody
looks twice.

Not made live: that needs a public estate-wide count of bookings, which does not exist and would be
a disclosure decision taken for a prototype's hero tile — the argument D45 used to not publish the
provider list. The tile is omitted in live mode instead, through a `sessionsBrokered()` in the third
script block that live mode replaces exactly as it replaces `confirmBooking`. Block 1 untouched, and
the seed regenerates byte-identically. Pinned in `verify-prototype-live.mjs` in **both** directions —
live must not show it, the demo must still show it and still count 269 — because deleting the tile
outright satisfies the first and quietly changes the acceptance target. Confirmed red before the fix
and ended in a real browser, both modes.

## NEW-8 — The prototype's professional workspace is demo-only in live mode · WON'T, documented

Found by the review of NEW-4 to NEW-7, and **pre-existing rather than introduced by them**. Block 4
of the prototype computes `proStats()`, "Recently completed", "N confirmed sessions ahead · N
completed to date", the earnings screen's upcoming total and "% of sessions reviewed" from
`PRO_HISTORY` and `PRO_SCHEDULE` — two consts in the first script block that live mode never
repopulates. So in live mode **that entire screen renders demo figures under the LIVE banner**,
which is NEW-7 one screen wide instead of one tile.

**Not fixed, and deliberately not fixed in the same branch as NEW-7.** Making it live needs a
professional's token in the prototype and endpoints this estate does not publish; widening a review
fix into it is how a branch stops being reviewable, and the tile's own argument — do not show a
figure that cannot be true — resolves differently here, because the workspace is the professional
half of the acceptance target and deleting it is not an option.

**What was done is the one sentence the rule now requires.** NEW-7 generalised to "adding a hero
figure means saying which mode it is true in", so the live block's "WHAT IS LIVE, AND WHAT IS NOT"
section says the workspace is demo-only and points here. It is a decision with a reason attached
rather than an oversight nobody has met yet. The seed still regenerates byte-identically after the
edit.

**When it comes back:** the day a professional-side screen is driven from the estate, this is the
work, and the endpoints it needs are the same ones `/api/pro/**` already has behind a token.

## NEW-9 — Four seeders shift every date by the JVM's idea of today · DONE

D47, found by the WP-15 review; built and reasoned as **D48**. Each of the four seeders opened with
the same line:

```java
long shiftDays = anchorDates ? 0 : ChronoUnit.DAYS.between(seed.meta().demoToday(), LocalDate.now());
```

`LocalDate.now()` takes the JVM default zone, which is the implementation D47 rejected for the badge.
It is the **fifth** implicit-zone call in catalog, the one the first inventory missed, and the only
one of the five that **writes**: it moves availability slots and review dates in catalog, every
booking's schedule and its `raisedAt`/`completedAt` in booking, conversations and notifications in
messaging, and `ledger.earned_on` in payout. On `Europe/Berlin` in summer the JVM date runs ahead of
Accra's from **22:00 UTC to midnight** (00:00–02:00 CEST), so a seed loaded in that window is shifted
a day further than one loaded an hour earlier and an estate quietly stops being seed-exact against
itself — with `--verify`'s count checks all still green, because the counts do not move.

**Latent rather than live, checked rather than assumed.** Quality sets
`HEALTHCONNECT_SEED_ANCHOR_DATES: "true"`, so the ternary short-circuits and the call is never
evaluated on the box. Dev defaults it to `false` but sets no `TZ` on any service, so a dev container's
JVM default is UTC, which is Accra. `CLAUDE.md`'s single-service recipe anchors; the tests anchor. What
is exposed today is a seeder run by hand with `anchor-dates=false` on a workstation — and one `TZ:`
line added to a compose file for any other reason.

**Why it is a package and not a one-line fix.** The four seeders' dates have to agree with each other:
catalog's `availability_slot.slot_date` against booking's `scheduled_date`, payout's `ledger.earned_on`
against booking's `completed_at`. **Nothing joins either pair and nothing can** — each service owns its
own database instance, so payout's aggregates read payout's own `Ledger` and booking never asks catalog
about a slot — which means the agreement is enforced by no query and a break in it shows up only on a
screen. So **fixing catalog alone is worse than fixing none** — a uniform one-day offset in all four
becomes a one-day disagreement between two services' seeded data, which is the shape of defect nothing
here detects. There is no shared library, so it is four identical edits (the `SubjectPseudonym` situation,
minus the CI diff), and proving it red needs a seam the seeders do not have: they take no clock and
no zone, only a boolean.

**The work:** decide the seam — a named `SEED_ZONE` constant per service, matching `BADGE_ZONE`, is the
cheap answer and is enough, since `demoToday` is a demo calendar and Accra is the estate's; a `Clock`
is the thorough one and buys a red-first test. Then four edits in one commit, and a line in CI's
consistency job if the four are to be kept identical.

**Built as D48, and it took both halves of that choice rather than either.** `SeedCalendar` holds
`SEED_ZONE = Africa/Accra` *and* a package-private `Clock` overload, because the constant is what
fixes the defect and the clock is the only way to watch it fixed — the seeders take a boolean, so
without a seam a date fix at a date boundary can only be tested by being run at the right hour. It is
one file **copied byte-identically into catalog, booking, messaging and payout** with its
known-answer test beside it, and CI diffs the four copies: a duplicated constant with nothing
comparing the copies is how the next divergence arrives in silence, which is the `SubjectPseudonym`
argument (D35) one service wider. A second CI check greps the four `load()` methods for a direct clock
read, and both checks were watched firing — the grep against the four lines as they stood at
`18d86c8`, the diff against a `payout` copy with `UTC` substituted in.

**Three of six tests confirmed red first**, against a stand-in differing from the old line only in
that the instant is injectable. The one that is the defect itself:
`[a seed loaded at 23:40 in Accra is the same day as one loaded at noon] expected: 26L but was: 27L`
— two instants twelve hours apart on one Accra day, under a default zone east of UTC, giving shifts
that differ by one. The two zone tests bracket the day from both ends and are two tests rather than
two assertions in one, because the first assertion to fail hides the second.

**Half the package was deliberately not built, and it is the half the entry above asked about.**
Pinning the zone makes the four agree on *which* calendar; it does not make them read it at the same
moment. A shared `HC_SEED_TODAY` computed once by `deploy-dev.sh` would close that, and was rejected:
it is a fifth value that has to agree with a sixth (the shape that has already cost this repository
the topic prefix, the vhost port and the webhook permit), it means re-embedding Appendix A, a
daily-changing environment recreates all four containers on every `up`, and being optional it is
absent in exactly the hand-run case that was the defect's only live exposure. Against that, the
residual is the seconds of spread between four services started in parallel by one `compose up`,
once a day, on the dev estate only — quality anchors and production never seeds. D48 names three
triggers that would flip the answer. What was taken instead, at no cost: the shift's log line now
names the day it shifted **to**, derived from the shift rather than read from the clock again, so a
disagreement leaves a record of why in both services' logs.

**Both narrowing facts re-measured rather than inherited, and both still hold**: `quality/compose.yml`
sets `HEALTHCONNECT_SEED_ANCHOR_DATES: "true"` on all four seeded services, and no compose file in
this repository sets `TZ` on any service.

**Reviewed 2026-09-05 — nine findings, all applied, and the serious ones are all about the CI check
rather than the fix.** The review verified rather than accepted — it re-diffed the four copies,
re-measured both narrowing facts on the box, reproduced the three red tests, and confirmed by mutation
that the tests' clock zones are load-bearing — and the code stood. The check built to stop NEW-9
returning did not. It banned the *type* that reads a clock and not the *zone* that interprets one, so
`LocalDate.ofInstant(Instant.now(), ZoneId.systemDefault())` passed green — **the exact stand-in D48
quotes as reproducing the defect**. Its `^[^/*]*` comment anchor could not cross a `/` or a `*`, so a
division or a log string with a slash hid the rest of the line, in the service that does commission
arithmetic. And it named four files rather than the package, missing `SeedDataLoader` — which is
precisely where D48's own named future work would put a `LocalDate`. All three widened and **watched
firing against seven constructed reintroductions the old check missed every one of**, with two
controls (a bare `Instant.now()`, a comment naming `LocalDate.now()`) that must stay green.

Also: the residual was argued against `up` and never against `reseed --services <subset>`, which
reseeds part of the estate against the rest's older day — days wide, not seconds — so that is
**refused now without `--force`** (Appendix A re-embedded); the "lifetime-earnings aggregate sums
over" claim was false in six places and is corrected to the true and weaker one, that nothing joins
those pairs at all and the disagreement is only ever visible on a screen; an anchored run logged
nothing, so the quality box said nothing about its own calendar, and there is an `else` now; the
fixed clocks were all eastward, so an implementation reading the *clock's* zone was bracketed one way
only — the westward test has a westward clock now, taking the mutation from 2 red to 3; and
`CLAUDE.md`'s duplication bullet names both copied families rather than one. D48 carries the detail.

**Not done:** no run against the quality box, which anchors and therefore cannot exercise this at all —
the only estate that evaluates the shift is dev. And the four *rendering* `LocalDate.now()` calls
D47 inventoried in catalog are untouched; this package was scoped to the one that writes. Payout's
five are **NEW-10**, opened by the review.

## NEW-10 — Payout writes the cross-service pivot in the JVM's calendar · DONE

**D51.** Opened by the NEW-9 review, and deliberately not fixed there: it was outside that package's
scope, but it undermined D48's thesis and nothing recorded it. `ledger.earned_on` is the column D48
names as the pivot between payout's seeded data and booking's `completed_at` — and after D48 the
**seeder** wrote it in `Africa/Accra` while the **consumer** wrote it in the JVM's zone, in the same
table:

| Where | What it decides | Shape |
| --- | --- | --- |
| `BookingEventConsumer:143` | `earned_on` for a completed booking | **writes the pivot** |
| `BookingEventConsumer:246` | `earned_on` for a late-cancellation fee | **writes the pivot** |
| `DisputeEventConsumer:142` | `earned_on` for a dispute reversal | **writes the pivot** |
| `ProEarningsResource:69` | "today", for the month-to-date slice | renders |
| `ProEarningsResource:86` | the same, on the chart endpoint | renders |

Between 22:00 and 24:00 UTC in summer on this workstation, a booking completed at 23:30 UTC gets a
ledger row dated **tomorrow**; on a month's last day it lands in the next month and disappears from a
month-to-date tile computed against a "today" that is also a day ahead. Lifetime gross is unmoved,
which is why nothing goes red.

The three writes rank above the two reads — a rendered default is wrong for one request, a stored date
is wrong for ever. `DisputeEventConsumer:142` is the sharpest of the five and worth reading: its
comment reasons carefully about *which day* a reversal belongs to ("dated today, not backdated … would
silently rewrite a month that has already been reported") and never names a zone. The decision was
taken; the calendar it was taken in was not.

**Done as D51**, with `booking` `ProBookingResource:111` folded in — the same implicit-zone default for
a rendered window, one line, and leaving it would have meant a second package for one call site.
`MarketCalendar` is the named constant plus a `today()` on an injectable clock, copied byte-identically
into payout and booking with CI diffing the copies. It is a **third** named zone rather than an
estate-wide one, beside `SeedCalendar.SEED_ZONE` and catalog's `MarketplaceService.BADGE_ZONE`: three
questions, three arguments, one answer, and merging them would leave two of the three arguments written
down nowhere. D51 argues it.

**The data question is answered, and the answer is that there was nothing to correct.** Established
rather than assumed: production has never been deployed, no compose file in the repository sets `TZ`
on any service, and every image's own default is `Etc/UTC` (measured, not inferred). Both estates that
hold rows were **read**: the quality box has 256 seeded ledger rows plus exactly one written by the
consumer, dated correctly; the dev estate — five `healthconnect-dev_*` volumes, containers wedged in
`Restarting` since 2026-08-30 — has 256 rows all carrying `h1`–`h9` seed references and an **empty
`processed_event`**, so no consumer has ever written there at all. So every `earned_on` ever stored was
written in UTC, which is Accra. **No migration, and it has an expiry**: the answer holds because nothing
is deployed and nothing sets `TZ`, and `ledger` carries no instant beside the date, so a row written in
the wrong calendar could never be told from a right one afterwards.

*(This paragraph claimed "no dev volume exists at all" as first written, and D51's review found it
false. The conclusion never moved; the argument is now the same one already made for quality. **Grep
docker for the compose PROJECT name** — `healthconnect-dev` — not the `container_name:` the compose
file declares and `CLAUDE.md` documents: volumes never take a container name, and these containers
predate that directive, so `| grep market` answers with the quality stack alone and reads as "there is
no dev estate". That misled three separate readers.)*

**Also closed:** a CI grep, D48's widened from a seed package to the whole `src/main` tree of **four**
services — payout, booking, messaging and gateway — watched firing against fifteen constructed
reintroductions in total, including the exact stand-in D48's own first check missed. It also bans the
**database-side** clock (`current_date`, `now()::date`), which is the identical defect in SQL and
invisible to a Java grep; there are none today, which is when it is free. Two fail-opens of its own
were found and closed: a `while read` over an empty `find` is one empty line rather than none, and a
line **starting** with a block comment hid the code behind it.

**Not closed:** catalog's four, which are **NEW-12**, and which is why the new check does not scan
catalog. `BookingEventConsumer.configInForce` prices at consumption rather than completion, which is
**NEW-13**, opened by the review and not fixed there — **closed since, by D53**. And nothing was run
against a live estate: both boxes were read, not rebuilt.

## WP-19 — Production deployment configuration · PARTLY DONE

D49. `deploy/prod-server/` built to the shape `hc-admin`, `hc-patient` and `hc-professional` share:
the data tier, the nginx vhost and its snippet, the http-scope rate-limit zone, `infra.sh`,
`backup.sh`, `start`, `secrets.env.example`, and a runbook. **Configuration and documentation only.
Nothing in it has contacted `webserver`**, and the whole of its evidence is `--dry-run` (which
contacts nothing), `docker compose config`, `bash -n`, seven watched mutations against six new CI
checks, and reading the siblings.

**The package's value is the five things that turned out not to be true**, each of which would have
stopped or silently damaged a first deploy and each invisible for the same reason — no path in this
repository had ever been executed against a host:

- **no production database was declared anywhere.** Nine `:?` connection values pointing at hostnames
  nothing on the host provided, and `deploy-prod.sh`'s own comment asserting `infranet` carried "the
  databases", which had never been true;
- **the preflight checked two of the eleven required values**, which is the defect that check exists
  to prevent, nine keys wide: a host holding the two secrets passed, had `.env` rotated, and then
  died at `up`;
- **the smoke test could not pass, two independent ways** — it asked for `/api/**` at the edge, which
  D28's narrowing makes unroutable by design, against a hostname this product does not serve;
- **the gateway published on `0.0.0.0`.** Not a routing hole (D28's predicates apply either way), and
  that is why it is worth recording: a second front door with no TLS, no headers, no CSP and no rate
  limit, with every functional check green;
- **`deploy-prod.sh` told the operator to install the wrong signing key** — the platform key
  hc-admin, hc-patient and hc-professional share, which D37 says at length hc-market is not part of.
  The only one with a security shape, and its shape is that **nothing would have failed**: these five
  services would have acquired the ability to mint tokens the other three products accept, silently,
  on first deploy.

**Two decisions taken.** Production is API-only — no `/prototype`, kept by CI rather than by a
comment, because the plausible way it returns is somebody "restoring parity with quality". And the
databases are **bundled and private**, in a second compose project on hc-market's own `hcmarketnet`:
the opposite call from D27's broker, for the reason D27 itself gives, and a second copy of the five
services deliberately not written.

**Still open, and none of it is engineering.** Fifteen items, listed in
`deploy/prod-server/README.md`. The ones that block a first deploy: confirm `/srv/healthconnect` and
the ssh target, confirm port **8086** is free (a guess, in the siblings' family, unverified), confirm
`infranet` and `monitoring` exist with something listening on them, create `secrets.env` with a
freshly generated key and an escrowed pepper, point DNS, and install nginx and run certbot — which
needs sudo and which nothing here does. Then the two that make it trustworthy rather than merely
running: **restore a dump and confirm it comes back** (no backup here has ever been restored, which
makes them beliefs), and verify the OTel agent is instrumenting rather than merely loading.

**WP-18 is unchanged and is one of the fifteen**: whether `gateway`, `catalog` or `booking` is
already a DNS alias on `infranet` still cannot be answered from a workstation.

**Reviewed 2026-09-05; eight findings, all applied.** One blocking: **a failing smoke test triggers an
automatic rollback**, and the smoke test required a catalogue count `> 0` on an estate that never
seeds. So the first production deploy would have ended in `no previous deployment recorded` with the
stack up and correct, and the second would have *successfully* rolled back a deployment that had just
come up healthy. Both this file and D49 described that as a warning. It now distinguishes **no number
at all** (a failure — the edge, the route or the datasource) from **`0`** (warned loudly, passed),
with `> 0` surviving as opt-in `HC_SMOKE_MIN_PROFESSIONALS`.

The other seven: the remote compose invocations named no `-f` while this branch added a second
compose file; `prod-server/start` reported "all five stores healthy" when stores had **exited**,
because `docker compose ps` hides stopped containers without `-a`; `backup.sh` put both database
passwords in world-readable host argv under a comment claiming the opposite; the `:?` message an
operator meets at the moment of failure still said "platform JWT secret is required"; the signing-key
severance — **the most serious of the original five** — had no CI check, and cannot have a runtime one
because this estate validates no `iss` and no `aud`; `/srv/healthconnect` deserved a better argument
than "the code was followed", and has one (the siblings' `start` reads `--env-file ../.env`, the
platform key, one directory above the conventional path); and six runbook details, of which the two
worth naming are that the ssh target is the alias `webserver` as root rather than an invented
`deploy@` user, and that **open self-registration is public on `market.abofonsa.com`** — `/api/register`
is `permitAll` on the gateway, presumably intended and previously unrecorded.

Three of the eight were found by *running* something for the first time: `backup.sh` (five throwaway
containers, first execution in its life), the nginx files under a real nginx, and `docker compose ps`
watched hiding an exited container. **A seventh CI check** — `.github/checks/signing-key-severance.sh`
— was added and watched firing three ways.
## NEW-11 — A second payment attempt for one booking would reuse Paystack's reference · WON'T, until there is one

Opened by the D50 review, and deliberately **not** built: it is a wall in front of a path nobody has
laid, and building the road to it early costs something today.

`PaystackPaymentProvider.authorize` sends `intent.bookingReference()` as Paystack's `reference`. The
integration D50 read the wire format from mints `"HC-" + id + "-" + random8` — it *had* the domain
identifier and appended fresh randomness anyway, **per attempt**, because a provider will not take one
reference twice. Assume Paystack rejects a reused one; nobody here has credentials to ask, and the
review correctly did not call them to find out.

**Harmless today, and not by luck that could quietly run out.** `CustomerBookingResource.create` mints a
fresh `b-<8 hex>` on every request and the authorization happens once, before the booking row exists.
There is no "retry the payment for booking X" endpoint, no path that authorizes twice, and nothing that
re-authorizes on failure. Uniqueness per attempt therefore holds structurally.

**And the divergence pays for itself while it holds.** Sending our own reference makes Paystack a second
check on a `b-` collision — 2^32 of them, so a birthday event rather than an impossibility — and it
checks *before* the money moves: a 502 and no booking. A per-attempt suffix makes every reference unique
at Paystack, so the same collision would surface only at the unique constraint when the booking is
written, which is D41's expensive path: money committed, booking gone, and `voidAuthorization` still
refusing.

**What to do when it stops holding.** The trigger is any path that authorizes twice for one booking —
the obvious companion to D43's dead-end cancel, "pay again". The failure is a wall rather than a
degradation: Paystack refuses the reference, the customer gets a 502 that retrying cannot escape, and
the only mention of a reference is inside a `RestClientResponseException` message that happens to quote
the response body. The fix goes in **the same commit as the path**: `bookingReference` plus a
per-attempt suffix, prefix kept so the booking is recoverable from the handle. `payment_attempt` is
already built for it — `PaymentRecorder.record`'s javadoc says two attempts against one booking may
legitimately carry the same reference, which today describes a world no adapter here can produce.

Written on `PaystackPaymentProvider.authorize`, where whoever adds that path will be standing.

## NEW-12 — Catalog's four implicit-zone reads, one of which stores a date · DONE (D52)

Opened by D51 rather than by a review, and separate from NEW-10 for two reasons: it is a different
service, and one of the four is a **stored** date whose correction is a data question of its own rather
than a rename. D47 inventoried them and they stand exactly as it recorded them.

| Where | What it decides | Shape |
| --- | --- | --- |
| `ReviewWriteResource:115` | `Review.publishedOn` on a new review | **writes, stored** |
| `MarketplaceResource:132` | the default start of a public availability window | renders |
| `ProWorkspaceResource:228` | the default start of the professional's own window | renders |
| `ProWorkspaceResource:337` | the same, on the second window endpoint | renders |

All four are `LocalDate.now()`. **The shape of the fix is settled and cheap** — a fourth copy of
`MarketCalendar` (D51), byte-identical, added to the CI diff beside payout's and booking's, plus the
one line each. Catalog already carries `MarketplaceService.BADGE_ZONE`, which stays separate for the
reason D47 and D51 both give: it is the verification desk's calendar and answers a different question.

**Two things make it more than a rename.** `Review.publishedOn` is on a *public* review, and catalog's
quality database holds seeded reviews plus whatever `verify-cycle.sh` has left there, so D51's "no
stored row needed correcting" **must be re-established rather than cited** — the four checks it used
(nothing deployed, no `TZ` in any compose file, the image's own default zone, and reading **every**
database that holds real rows) are the method, not the answer.

One of the two is already done, by D51's review and as evidence to be **re-verified rather than
cited**: `healthconnect-dev_catalog-data` holds **63** reviews with a newest `published_on` of
2026-08-24 — exactly the seed count, so no `ReviewWriteResource`-written row exists on the dev estate.
The quality box is the one still to read, and it is the one `verify-cycle.sh` writes to. Note the trap
that made D51's review necessary: **grep docker for the compose project name** (`healthconnect-dev`),
never the `container_name:` the compose file declares, or the dev estate looks like it does not exist.

And the CI check D51 added deliberately **does not scan catalog** while these are open, because a check
with these three files exempted would claim to cover the service while being blind in exactly the files
most likely to acquire the next one. Widening it to catalog is part of this package, not a follow-up.

**Closed by D52, 2026-09-06.** All four go through a fourth byte-identical copy of `MarketCalendar`;
the CI scan now covers **every** service in the repository with no per-file exemption anywhere, and was
watched firing against eight constructed reintroductions in catalog, one per fail-open in its history.
Catalog's fourteen new unit tests were each proved red first, under four mutations.

**The data question was re-established and its answer is not D51's.** Both premises held — nothing
deployed, no `TZ` in any of the four compose files or any running container, and both catalog images
measured through the JVM at `ZoneId.systemDefault() = Etc/UTC` — but the row count did not. The dev
estate has **63** reviews and none written by the resource, as recorded above. The **quality** estate
has **65**: 63 seeded and **two written by the defective line**, `r-3dba2020` (2026-09-05) and
`r-1948adca` (2026-09-06), one per `verify-cycle.sh` run, each matching its booking's completion
instant read in Accra. So the honest finding is **"written by the defect and still right, because the
container's zone happens to be the estate's calendar"** — not "nothing was written by the defect", and
the difference is the whole margin the defect had. No migration; `review` carries no instant beside the
date, so a wrong row could never have been identified anyway.

## NEW-13 — The brokerage rate is struck when the event is consumed, not when the booking completed · DONE

Opened by D51's review, four lines from a comment that package rewrote, and deliberately **not** fixed
there — it is a decision about money with a wire-format change behind it, not a rider on a calendar
package. **Built as D53.**

`BookingEventConsumer.configInForce` picks the latest `BrokerageConfig` whose `effectiveFrom` is not
after **`Instant.now()`**. The class javadoc four lines above says a booking "prices against the rate
in force when it **completed**". Those are the same thing while delivery is prompt and different after
any outage, replayed partition or paused consumer that straddles a rate change — the booking is then
priced at terms the customer was never shown, and the receipt (which strikes its split at
`completedAt`, `CustomerBookingResource.receipt`) would disagree with the ledger row by however much
the rate moved. Nothing detects that: both numbers are internally consistent.

**Same species as NEW-10, one axis over.** A decision was taken — "the rate in force" — and the
*moment* it was taken at was never written down, exactly as NEW-10's calendar was not. `Instant.now()`
itself is correct and stays; an instant carries no calendar, so this is not a zone question and
`MarketCalendar` has nothing to say about it.

**It is not a one-line fix, which is why it is an item.** `completedAt` **is not on the wire**:
`OutboxRecorder` publishes `bookingRaisedAt` and no completion instant, so pricing at completion means
adding a field to the `booking.completed` payload first, and then deciding what a consumer that
receives an event without one should do — the events already in the outbox on the day of the change
have no such field. That is a compatibility decision, not an edit.

**Cheap and worth doing while nothing has been deployed**, on the same argument D51 made for itself: a
ledger row records no rate, only the amounts computed from one, so a row priced at the wrong rate can
never be identified afterwards.

**Established rather than cited, which is what the argument above needed — and the sentence above is
overstated.** "Can never be identified afterwards" is not true and D53's review disproved it: the rate
is recoverable from `commission_minor / gross_minor` (one distinct value, `0.120000`, over all 258
quality rows, with the `HALF_UP` ambiguity bounded by ~0.000033 at `min(gross)` 15000),
`booking.completed_at` is stored, and `brokerage_config` keeps its effective-dated history, so a
mispriced row is identifiable by joining the two — and `DisputeEventConsumer.proportionalCommission`
already depends on that recoverability. **The true claim is that nothing *does* detect it, and payout
alone cannot.** That is still sufficient motivation, and it leaves the cheap-now argument intact, which
rests on there having only ever been one rate rather than on irrecoverability.

The live schema was read
off `hc-market-quality-payout-db`: `ledger` has fourteen columns, no rate, no `brokerage_config`
reference and **no instant** — `earned_on` is a `date`. Both estates hold exactly **one**
`BrokerageConfig`, the seeded 0.12/GHS row effective `2020-01-01`, and it has never moved: quality has
258 ledger rows and 2 processed events, dev has 256 and an **empty** `processed_event`. So no row
anywhere has ever been priced across a rate change, which is why this was the cheapest moment it will
ever have — the day a second config row exists, every row written across the change is ambiguous for
ever.

**What was decided, since the item said the compatibility question was the real work.** The act's
instant goes on the payload — `bookingCompletedAt` and `bookingCancelledAt`, two fields because two
events carry a money decision taken at different moments. An event without one falls back to the
**envelope's `occurredAt`**, which booking stamps in the same transaction as the transition and which
travels with the event, so no amount of delivery lag can move it; the fallback is a **WARN** naming the
booking, never a silent `Instant.now()`. With neither present the event is **refused** and retried,
which costs nothing because `occurred_at` is a not-null column. `bookingRaisedAt`, consumption-time and
refuse-everything were each considered and rejected, with reasons, in D53.

**Two adjacent things it did not close**, both now items of their own: **NEW-16**, the receipt's
day-granularity, narrowed from unbounded to sub-day and not removed; and **NEW-15**, found while
reading the config rows.

## NEW-14 — A professional's own calendar opens on Accra's day, not theirs · CLOSED by decision (D55)

Opened by the D52 review, and deliberately **not** decided there. `ProWorkspaceResource.availability`
and `.generate` default their window start to `MarketCalendar.today()` — the marketplace's day — for a
professional looking at their **own** calendar and generating their **own** slots.

The reason it is a question at all is that catalog, unlike payout, **has a zone to read**:
`Professional.zoneId` exists (D21) and both methods already hold the owner. D52's shared javadoc argued
Accra partly from payout having no such zone, which is true there and false here — corrected in place,
and the argument that remains is that a window *start* is a question about the page being read, while
the times inside it are already the professional's wall clock.

**Consequence today is nil.** Every `professional.zone_id` in every estate is `Africa/Accra` and the
column defaults to it, so the two spellings cannot differ. The day a professional carries another zone,
they open their calendar — and generate their slots — on Accra's day rather than their own, which near
midnight is the wrong day by one.

**Closed by D55, 2026-09-07, with no code change.** Spec §13 #8 was ratified: an *appointment's*
wall clock is the professional's (D21), a *window or "today" default* the platform renders is the
marketplace's. These two defaults are the second kind, so `MARKET_ZONE` was already the right answer —
what changes is that it is now **chosen** rather than unchallenged. The times *inside* the window are
already the professional's wall clock, carried on the slots; answering the start per-professional would
make one afternoon at one desk render as two dates depending on whose profile it landed on, which is
D47's `BADGE_ZONE` argument.

**An item closed by ratifying the behaviour it questioned is closed**, and that is worth naming: it was
never a defect, it was an unanswered question about a line that happened to be right. The half of §13 #8
that *did* find a defect is **NEW-19**.

## NEW-15 — Any authenticated user can change the brokerage's commission rate · DONE (D54)

**Closed by D54, and the scope turned out to be nine resources rather than one.** The item's own
closing paragraph asked whoever took it to put the delete table's question to every other generated
`*Resource` in the five services. Doing that first found eight more, every one with four write
mappings and no authorization annotation of any kind: `LedgerResource` and `PayoutResource` in payout,
`MessageResource` and `ConversationResource` in messaging, and `ServiceOfferingResource`,
`AvailabilitySlotResource`, `CredentialResource` and `HighlightResource` in catalog.

Ranked by what a **write** does rather than what a read discloses, the resource this item was opened on
comes fifth. Messaging's pair are the worst — they return the content of private conversations, the one
category of personal data here that is neither pseudonymised nor derivable — and payout's ledger and
payout tables are the money record, with no third copy of either.

All nine are deleted, with their generated ITs, each argued on its own callers and its own replacement
rather than in bulk; three `GeneratedCrudIsNotAnApiIT` guards go red the moment any of them answers
anybody again, with each door mutated separately to prove the guard is per-door and not per-file. **The
control itself was the real deliverable**: `build.yml` now derives the expected set from `jdl/*.jdl`
and demands, for every entity in the model of record, either a delete-table row or real authorization —
so the tenth cannot ship the way these nine did. D54 has the per-resource arguments, the measurements
and the eight mutations.

Everything below is the item as it stood, kept because it is the record of what was known before.

---

Opened by D53 while establishing that `effectiveFrom` had never moved, and **not** fixed there: NEW-13
is about *when* a rate is struck and this is about *who may set one*, which is an authorisation
decision with its own answer to choose. Deliberately not ridden in on that package, for the same
reason NEW-16 was not.

The generated `BrokerageConfigResource` is alive on `/api/brokerage-configs`, carrying
`@PostMapping`, `@PutMapping`, `@PatchMapping` and `@DeleteMapping`. payout's `SecurityConfiguration`
says `.requestMatchers("/api/**").authenticated()` and nothing narrows it, and the gateway routes
`Path=/services/healthconnectpayout/api/**`, which covers it. So the whole of JHipster's CRUD is
reachable by any token the estate will accept.

**Verified, read-only, against the quality box**: an HS512 `ROLE_USER` token minted with the estate's
key returns `200` and the config body through the gateway on `127.0.0.1:15509`. The writes sit on the
same rule and were deliberately **not** exercised, because repricing a live estate to prove a point is
not a trade worth making — the GET is sufficient evidence of the authorization rule.

The read is arguably public — the prototype prints "12% platform fee" on every listing, and
`/api/internal/brokerage/split` already discloses the rate to any authenticated caller by design. The
writes are not: a customer can create a backdated `BrokerageConfig` and reprice every booking completed
after it, and nothing in the ledger would afterwards say which rate was used (D53).

**How urgently a person should read this: there is no live external exposure today.** Production has
never been deployed (WP-19, D49), so the only estates where this answers are the quality box on a
private LAN and a wedged dev stack. It becomes a real exposure on the first production deploy, and
`/api/register` is `permitAll` on the gateway (D49's review), so on that day "any token the estate will
accept" includes one anybody can mint themselves by registering.

**The root cause is the control, not the code.** `CLAUDE.md`'s delete table is what stops a generated
CRUD resource shipping — it names eight of them, each with the disclosure or forgery it would allow —
and **`BrokerageConfigResource` has never appeared in it**, on `main` or on any branch. It is not that
the table was ignored; the resource was never put on it. Whoever takes this should ask the table's own
question of every *other* generated `*Resource` still alive in the five services, because the same
omission cannot be detected by any test that exists.

**Two shapes for the fix, and somebody should choose deliberately.** Delete the generated resource and
its IT and add it to the table, as is already done for eight others — there is no screen for it in the
prototype, no caller in this repository, and `PayoutSeeder` writes the one row directly through the
repository. Or keep it and gate it behind `ROLE_BROKERAGE` beside the dispute desk, which is the answer
if the brokerage ever wants a terms-change screen. Either way `AuditTrailIsNotAnApiIT` is the pattern
for the test — it is the only row in the delete table with a guard that fails if you miss it, and it
goes red the moment `/api/booking-status-changes` answers anybody again. The equivalent here goes red
the moment `/api/brokerage-configs` does.

## NEW-16 — The receipt strikes its split to the day and the ledger to the instant · DONE (D56)

**Closed by D56.** `at` — an `Instant` — is sent beside `on`, payout prefers it, and a request naming
neither is **400** rather than the `Instant.now()` that had been sitting in that branch since the
endpoint was written. `on` is kept and is not deprecated: booking and payout roll independently, so a
new booking calling an **old** payout is a real deployment, and with `on` beside `at` that window
prices to the day (this defect, not a new one) while without it that payout falls through to its own
clock — NEW-13 rebuilt in the other service, on a customer's financial statement. It is also the honest
request from a caller that has no moment, which is why it does not warn. The four cases and the reason
each was decided that way are tabulated in D56.

The two selectors are merged into **`BrokerageTerms`**, with a stated tie-break — newest `id` among
rows sharing an `effectiveFrom` — closing a non-determinism neither copy could detect: `Stream.max`
returns an arbitrary element among equals, so a receipt and a ledger row could resolve different rows
in one JVM. `BrokerageConfigService` is deliberately **not** absorbed and **not** deleted; D56 argues
both halves.

The endpoint had **no test at all**. It has ten now, through MockMvc with real query strings so the
`Instant` parameter *binding* is pinned and not only the selection rule; seven were red against the
unchanged code. Booking gets two more that cannot see each other's failure, and one CI check whose five
assertions were each watched firing on their own mutation — a run that found the check banning a
correct `Instant.now()` in `BookingEventConsumer` and got it narrowed.

**The review found four more, the first of them in the new check itself.** It had copied D53's
line-based comment stripper, blind to any block comment spanning more than one line, so deleting
`queryParam("at", at)` and leaving a comment naming it exited **0** — NEW-16 restored on a green build,
the eighth fail-open in this family. D54's stateful awk is now `.github/checks/strip-comments.awk` and
**all four** text-matching checks call it, each watched firing separately; the implicit-zone one turns
out to have had the same blind spot pointing the *other* way, as a false positive on correct code. Also
corrected: this decision's own reason for the clock-ban narrowing (a **test** covers the consumer, not
the check above it), a javadoc claiming a test that does not exist, and an undocumented binding where a
numeric `at` is read as epoch **milliseconds**.

**Watching those four run produced a fifth finding the consolidation itself created**: with the shared
file absent, two of the four — the implicit-zone check and D54's CRUD check, the two whose whole
subject is a silent gap — **exited 0 having read nothing**. Existence guards on all four, plus
`strip-comments-test.sh` as the mechanism's own test. The fix for eight fail-opens arrived with a
ninth, and running it is what found it.

**Nothing was ever priced across the defect**, as far as could be established: quality holds one
`brokerage_config` row at midnight 2020-01-01 against 260 ledger rows. The dev estate could not be
read — its containers have been `Restarting` for a week and it has no databases running — so that is a
claim about quality, not about both.

Everything below is the item as it stood.

---

Opened by D53, which **narrowed it from unbounded to sub-day and deliberately did not close it**.

`CustomerBookingResource.receipt` sends `LocalDate.ofInstant(completedAt, MARKET_ZONE)` and
`BrokerageResource.split` reads it back as `atStartOfDay(MARKET_ZONE)`. The ledger now prices at the
completion **instant**. So a rate taking effect at noon on the day a booking completed at 14:00 prices
the ledger under the new terms and the receipt under the old — the same two-numbers-that-disagree shape
NEW-13 was about, one granularity down. It bites only when `effectiveFrom` is not midnight in Accra,
which the one config in either estate is.

**Why it is separate.** The fix is a cross-service internal API change with a compatibility question of
its own: add `at` (an `Instant`) beside `on`, prefer it, and **send both** — a new booking calling an
old payout that ignores an unknown parameter would otherwise fall through to `Instant.now()`, which is
NEW-13 rebuilt in the other service. There is also no test of `BrokerageResource` at all today, so the
package includes writing the first one, and it must pin the parameter *binding* (an `Instant`
`@RequestParam`) and not only the selection rule.

**And merge the two selectors while you are there — with a better reason than D53 gave.** D53 declined
to merge `BookingEventConsumer.configInForce` with `BrokerageResource.inForce` on the grounds that
"they already agree on the rule", which is the weakest argument available: the value of merging is that
they keep agreeing. There is a concrete defect behind it. **Both do `Stream.max(comparing(effectiveFrom))`
over an unordered `findAll()`**, so two configs sharing an `effectiveFrom` resolve **non-deterministically**,
and the two copies can pick different rows *in the same JVM* — a receipt and a ledger row disagreeing
with no rate change between them. It needs a data error to reach (nothing stops one: see NEW-15, where
anybody can POST a config), and `max` returning an arbitrary element among equals is a documented
property rather than a bug to report. Unlike `SubjectPseudonym` and `MarketCalendar` there is nothing
stopping the merge: both are in payout's **same Maven module**, and `TechnicalStructureTest` permits
`web → service`, so one selector in `service` is reachable from both. Decide the tie-break explicitly
while merging — newest `id` wins is the obvious answer and any answer beats an arbitrary one.

## NEW-17 — The generated Kafka sample resources publish to the shared broker, unauthenticated · DONE (D59)

**Closed by D59.** All five are deleted, gateway included, with the five ITs that asserted the hole
worked; `KafkaSampleIsNotAnApiIT` is in all five services and a second, differently-derived CI check
demands a delete-table row or real authorization for the class each `messageBroker kafka` application
generates.

**The thing this item asked to be established is established, and the answer overturns the paragraph
below.** It says the `kafka` profile is active nowhere, so `/publish` was a `StreamBridge` dynamic
destination against an unconfigured binding. **That is wrong.** `application.yml` lists `kafka` in
`spring.profiles.group.dev` **and** `spring.profiles.group.prod` in all five services, and a profile
group member never appears in `SPRING_PROFILES_ACTIVE` — the running quality container reports
`activeProfiles: ["secret-samples","kafka","api-docs","dev","test"]` for a stack asked for two. The
binder is pointed at the shared broker by all three compose files, `auto-create-topics` is true, and
the sibling bindings in the same file have **auto-created `sse-topic` and `kafkaProducer-out-0` on
that broker**, with the consumer groups `healthconnect-{gateway,catalog,booking,messaging,payout}`
registered against them. So the original claim was right and the correction was the wrong half. None
of it was established by POSTing anywhere: the topic list, the offsets and the consumer groups are
reads.

**The gateway's asymmetry is answered by a measurement rather than an argument.** `GET /consume`
drains `sse-topic`, whose end offset on the shared broker is **0** — the endpoint has never carried a
byte in the estate's life, and the only thing it could carry is somebody else's event, unfiltered.
`broker.KafkaConsumer` and `broker.KafkaProducer` are **kept**: named by
`spring.cloud.function.definition` in a generated file, mapping no URL once the resource is gone,
which is D54's rule for an orphaned generated class.

**One thing this item did not anticipate, opened as NEW-21**: the supplier beside the consumer has
written **5.6 million** messages to the shared broker with no caller at all, and is still doing it at
6/s. That is bigger than the door this item closed, and it is a different defect.

Everything below is the item as it stood, kept because it is the record of what was known before —
including the paragraph the establishment overturned.

---

Opened by D54, which found them while re-deriving NEW-15's inventory and deliberately did not fix them.

`HealthconnectBookingKafkaResource`, `HealthconnectCatalogKafkaResource`,
`HealthconnectMessagingKafkaResource`, `HealthconnectPayoutKafkaResource` and — this is why it is not a
rider on D54 — `HealthconnectGatewayKafkaResource` each carry `@PostMapping("/publish")` taking a
`message` request parameter, with no `@PreAuthorize`, under `/api/**` and therefore behind the same
blanket `.authenticated()` the nine deleted resources sat behind. Each is gateway-routed. All three
compose files set `SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS` at the shared broker four products borrow
(D27) and the binding has `auto-create-topics: true`, so any token the estate accepts can publish an
arbitrary string onto that broker on a topic it creates on demand.

**What it does on a running estate is UNESTABLISHED, and D54 first claimed otherwise.** The original
write here said the binder was pointed at the shared broker with `auto-create-topics: true`, so any
token could publish an arbitrary string onto infrastructure four products share. **That cites a file no
environment loads.** Both the `spring.cloud.stream` bindings and `auto-create-topics` live in
`application-kafka.yml`, and the `kafka` profile is active **nowhere** — dev runs `test,dev`, quality
runs `dev,test` (read off the running container), production runs `prod`. Outside that profile there is
no `spring.cloud.stream` block at all, so `streamBridge.send("binding-out-0", …)` is a dynamic
destination against an unconfigured binding and what it does was never determined. Establish that
before writing the consequence down again; the endpoint being an unauthenticated write is reason
enough on its own.

**Why it is lower priority than the nine.** Nothing in this repository consumes `binding-out-0`, so the
worst case is junk on shared infrastructure rather than disclosure or forgery. The
`@GetMapping("/register")` half is the sample SSE consumer CLAUDE.md already describes as *not*
`/api/stream`, bound to a `sse-topic` nothing publishes to.

**CI currently asserts the hole works.** One `Healthconnect<Svc>KafkaResourceIT.producesMessages` per
service POSTs to `/publish` under `@WithMockUser` and expects **200**. Five ITs go with the five
resources, and their existence is part of why nobody looked: the endpoint has a passing test, so it
reads as intended behaviour.

**Why it needs its own package rather than a deletion.** It touches the **gateway**, which NEW-15 did
not; and removing the resource means deciding what becomes of the generated `broker.KafkaConsumer` it
injects and of `/api/healthconnect-gateway-kafka/consume`, which CLAUDE.md discusses under D25/D29 as
the thing `MarketplaceStreamResource` is not. That is a decision, not a deletion.

**NEW-15's CI check cannot see this family, and no widening of it will.** The blind spot is not "these
five have no JDL entity" — it is that the check's whole *shape* is entity-derived: it iterates entities
and asks a question about each. There is no entity to iterate here. So the answer is a **second,
differently-derived check** over `web/rest` rather than a wider version of the first, and its hard part
is distinguishing the estate's fourteen hand-written resources from the generated ones without
enumerating either. One derivation that might work: a resource whose name matches
`Healthconnect.*KafkaResource` is generated by construction, since the prefix is the JHipster
application name from the JDL — which makes even that family derivable from `jdl/*.jdl`, one level up
from the entities.

---

## NEW-18 — Nothing can create a `BrokerageConfig`, and payout cannot price a booking without one · DONE (D57)

Opened by D54's review, which found it by asking what the `BrokerageConfigResource` deletion actually
cost. D54 said "deleting it costs nothing and does not decide the question". **The first half was
wrong**, and the deletion is still right — it uncovered a gap that was always there.

Four facts, each verified:

- `BookingEventConsumer.configInForce` ends `.orElseThrow(() -> new IllegalStateException("no
  BrokerageConfig in force at " + at + " — cannot price a booking"))`.
- The only writers of a `BrokerageConfig` outside tests were `PayoutSeeder` and the resource D54
  deleted. `grep -rn "new BrokerageConfig()" payout/src/main` now finds exactly one hit, in the seeder.
- `payout/src/main/resources/config/application-prod.yml` sets `seed.enabled: false`, and seeding is
  double-locked on the `test & dev` profile pair besides.
- The generated `loadData` in `20231204031835_added_entity_BrokerageConfig.xml` is
  `context="faker"`, which no environment but dev enables — and dev's faker context is disabled here
  anyway for the reason in CLAUDE.md.

**So on a production estate `brokerage_config` is empty, the first `booking.completed` throws, and the
consumer retries it for ever.** No ledger row is written, `/api/pro/earnings` stays at zero, and the
booking service is perfectly happy because the failure is entirely inside payout's consumer. It lands
on the one path where the customer's money has already moved.

**Three shapes for the answer, and somebody should choose deliberately:**

1. A **Liquibase changeset outside `faker`** inserting the founding rate. Simplest, runs once, and puts
   the estate's commercial terms in a migration where a schema change can be reviewed — but a rate is
   not schema, and changing it later means a second changeset.
2. A **seeded-once row**, guarded so it writes only into an empty table, on a property that is safe to
   leave on in production. Closest to how the estate already behaves, and it makes "the founding rate"
   a deployment input rather than a code constant.
3. The **append-only `ROLE_BROKERAGE` resource** D54 said a terms-change screen would need. Solves
   bootstrap and terms-changes together, and is the most work.

**A preflight is worth considering with whichever is chosen, and is not a substitute for one.**
`deploy-prod.sh`'s existing smoke test counts professionals in the catalogue and cannot see this. An
assertion that payout holds at least one `BrokerageConfig` would turn a silent forever-retry into a
failed deploy — but note that a smoke test for a condition with **no remedy** would simply fail every
production deploy until one of the three above is built, and a failing smoke test triggers an automatic
rollback (D49). So build the answer first, and the check with it.

### Closed by D57 — shape (2), and the two rejections are the decision

**Shape (2), the seeded-once row.** `BrokerageBootstrap` writes one row from `FoundingTerms` into an
**empty** `brokerage_config`, on every environment including `prod`, at `SmartLifecycle` phase
`Integer.MIN_VALUE` — so `BookingEventConsumer`'s listener container cannot start against a table this
has not seen. `PayoutSeeder` no longer writes the row **or deletes it**; `clear()` deleting it meant
`deploy-dev.sh reseed` recreated this defect on a running estate, with nothing to put the row back
until the next restart.

**(3) lost because it bootstraps nothing.** An append-only `ROLE_BROKERAGE` resource is what a
terms-change screen needs and should still be built when there is one — but a fresh estate would sit
with an empty table until a person remembered to POST, and the preflight below would then fail a
perfectly healthy new stack, which rolls it back (D49). **(1) lost on four counts**, the sharpest being
that a Liquibase changeset cannot read the environment, so it does not merely prefer a code constant —
it forces one; and that every `master.xml` include here is a regeneration-hazard row, all of which have
fired at least once.

**The trade the decision actually turns on** — a wrong deployment input prices everything wrong versus
a code constant needing a release — is answered by taking neither horn: the founding values are code
constants (the prototype's `COMMISSION = 0.12`, `PAYOUT_LAG = 3`) **that the environment may
override**. Nothing can be got wrong by omission; a malformed or out-of-range value refuses startup by
name, on every estate, which is a thing a deployment input can do and a code constant cannot. A
*plausible* wrong number is caught by nothing, and that residual is accepted and bounded: written once,
into an empty table, logged at INFO, and never over an estate that already has terms.

**`effectiveFrom` is `Instant.EPOCH` and is deliberately not an input.** It is the one field where a
well-formed wrong value silently reintroduces "no config in force" — this defect, through its own fix.

**The preflight shipped with the remedy — and its first version was wrong, found by a real `prod`
boot.** It read the aggregate `/management/health`, which answered `DOWN` on an estate whose founding
row was present and correct, because the Kafka **binder** indicator is in that aggregate and the boot
had no broker. A smoke test reading it would have rolled back a healthy deploy on a broker blip: WP-19's
defect, rebuilt by the check meant to prevent a different one. `smoke_test` now reads
`brokerage.termsInForce` from payout's `/management/info`, which is about one thing, and prints the rate
on success — the one failure `FoundingTerms` cannot validate is a plausible wrong number. It **cannot
fire on a bootstrapped estate** for a structural reason: the founding row is dated to the epoch. The
probe fails closed, so CI guards the endpoint's existence with it.

**What quality could and could not show.** Nothing: the quality stack **seeds**, so
`brokerage_config` was never empty there — one row, `id 1001`, `0.12 / 3 / 24 / 0.50 / GHS`, read
rather than assumed. That is a large part of why this survived. The reproduction is in
`AFreshEstateCanPriceABookingIT` against a real PostgreSQL with the bootstrap disabled: the consumer's
`IllegalStateException` and the receipt's 503, both, in one run. The nearest thing to the production
case that *could* be run was: payout's jar under `--spring.profiles.active=prod` against a throwaway
empty database with `healthconnect.seed.enabled=true` forced. It founded the terms, wrote **no** seed,
and a second boot wrote nothing at all.

**Still open after this, and named in D57:** the terms-change path itself. There is no way to record a
rate change other than inserting a row by hand, and shape (3) is what a screen would need.

## NEW-19 — An appointment is converted in UTC and its own zone is ignored · DONE (D58)

**Closed by D58.** Both sites read `Booking.zoneId` now — and there is **one** site afterwards, which
is the finding: `cancellationPreview` already called `isLate`, so the instant it quoted hours from was
a second copy of the one the fee is decided on, and that is why a single defect needed two lines
named. `BookingWorkflow.scheduledAt` is public and the resource asks for it.

**The decision this item reserved did not need making, and D58 establishes that rather than assuming
it.** `booking.zone_id` is `NOT NULL` with **no column default** — this item's own closing paragraph
below says the column defaults to Accra and so does D55; both are wrong, the default is application
code in two places — and it is written at exactly one live site (`CustomerBookingResource.create`,
from the professional's offering) and never recomputed, the generated `BookingService` having no
caller anywhere. So the column *is* the record of the term the customer was quoted, reading it
honours D53 rather than testing it, no in-flight boundary moves, and no migration was needed. All
298 rows on the quality box read `Africa/Accra`.

A third decision the item did not anticipate: `ZoneId.of` throws on a name tzdb does not know, and
`isLate` is on the `/cancel` path as well as the preview's, so an unreadable zone would have made a
booking impossible to cancel. It falls back to `MARKET_ZONE` — the write side's own default for a
blank offering zone — with a WARN naming the row.

Nine tests, east and west of UTC because a fixture in Accra can distinguish nothing and one zone
agrees with UTC by accident at some hours; both sites mutated separately, and the mutation that
matters leaves every unit test green while the endpoint is red.

Everything below is the item as it stood, kept because it is the record of what was known before.

---

Opened by **D55**, which is what turned it from a question into a defect. Two sites do
`.toInstant(ZoneOffset.UTC)` on an appointment's wall clock and never read `Booking.zoneId`:

| Where | What it decides |
| --- | --- |
| `BookingWorkflow.scheduledAt:238` | the instant a booking's appointment is treated as happening at |
| `CustomerBookingResource.cancellationPreview:235` | the same, and the late-cancellation boundary quoted to a customer |

Both carry a comment saying they are deliberately not on `MARKET_ZONE` because §13 #8 was open and
D21's answer might not be Accra. **§13 #8 is answered and D21's answer is the professional's zone**, so
those comments are now the record of a question that has been settled and need rewriting to say what the
code does.

**Not a `MARKET_ZONE` site.** That is the trap this whole family has been avoiding: the fix is
`Booking.zoneId`, the booking's own captured zone, not the marketplace's constant. Sweeping them onto
`MARKET_ZONE` would produce identical behaviour today and be wrong for exactly the case the ratification
exists to handle.

**The cancellation half is customer-visible and needs its own care.** `cancellationPreview` prices a
late fee off `scheduled`, so moving the zone moves the hour at which a cancellation becomes late — a
term the customer was quoted. D53's rule applies: a term the customer was shown must not move under
them. Whether an in-flight booking keeps the boundary it was quoted, or is re-evaluated, is the decision
this package has to make rather than assume.

**Consequence today is nil, and that is why it is cheap.** Every `Booking.zoneId` in every estate is
`Africa/Accra`, the column defaults to it, and Ghana is UTC+0 all year, so the two spellings cannot
produce a different instant. It becomes real with the first professional onboarded outside GMT, and at
that point it is a wrong late-cancellation boundary on a live booking rather than a rendering slip. Same
argument D51 made for `ledger.earned_on`: the cheapest moment to fix a zone defect is while every zone
is the same.

---

## NEW-20 — A booking stores whatever zone the catalogue hands it, and nothing parses it · DONE

Opened by **D58**'s review, and deliberately not fixed there. `CustomerBookingResource.zoneOf:546`
returns `offering.zoneId()` unchanged whenever it is neither null nor blank, so whatever string
catalog puts in a professional's `zoneId` is written into `booking.zone_id` — a `varchar(64)` with no
parse, no check constraint and no enum behind it.

**What that costs, precisely.** D58 made `BookingWorkflow.scheduledAt` read that column, with a
fallback to `MARKET_ZONE` for a value tzdb cannot read. The fallback is the right read-side answer —
the alternative is a 500 on `/cancellation-preview` *and* on `POST /cancel`, so a booking nobody can
cancel — but it means an unreadable zone is **stored once and mis-read for ever afterwards**, at WARN,
on the boundary that decides a 50% late-cancellation fee. Correcting the value at capture is loud,
costs nobody a committed booking, and would make the read-side fallback genuinely dead code rather
than a live path nothing can reach on purpose.

**Why it is its own package.** It changes what `POST /api/bookings` accepts, which is D22's
territory: the choice is parse-or-default (quiet, and the zone silently becomes Accra for a
professional who is not in Accra) or refuse (loud, and a catalogue row nobody has validated can then
block a booking). That is a decision with a customer-facing failure mode either way, and D58
deliberately did not take it while fixing a read.

**Not reachable today, and that is why it is cheap.** Catalog has no write path for
`Professional.zoneId` — the deleted `ProfessionalResource` was the only one, and `CatalogSeeder` is
the sole writer — so every zone in every estate is the seeder's `Africa/Accra`, all 298 booking rows
on quality included. It becomes reachable the day catalog grows professional onboarding, which is
also the day a professional outside GMT becomes possible, so this and D58's own trigger are the same
day.

Whoever takes it should put the same question to `catalog` in the same pass: a zone written there is
what a booking copies, so validating only the copy leaves the source wrong and the profile screen
rendering it.

**Built as D60, and the decision is a SPLIT rather than either of the two the item offered.** *Absent
is a state; unreadable is an error.* A null or blank zone still defaults to the marketplace's calendar
— that is a catalogue one release behind, the deployment D56 keeps payout's `on` for, and refusing it
would fail every booking in the estate over an empty field. A **non-blank value tzdb cannot read** is a
**502 and no booking**: no release of catalog produces one, so it can only be a row somebody wrote
wrong, and this estate refuses at a boundary rather than storing something wrong (D45, D50, D57 — and
D22, which is this endpoint's own rule). `CapturedZone` follows `SlotTime`, the other string on the
same builder chain that must become a time before it is stored. What it stores is
`ZoneId.of(x).getId()`, so the column round-trips by construction — measured, not assumed: all **604**
tzdb region ids are their own `getId()` so no real calendar is rewritten, while the offset spellings do
normalise. `CustomerBookingResource.DEFAULT_ZONE_ID` is gone and the default is `MARKET_ZONE`, so the
three zone constants stay three and D58's read-side argument becomes structural.

**The read-side fallback stays and the item's expectation of it was wrong.** It does not become dead
code: it is a live path for the **302** rows already written by a capture that did not parse, and for
anything that writes a `varchar(64)` without going through capture. It is unreachable from
`POST /api/bookings` and from nothing else, and that is now said on `BookingWorkflow.zoneOf` itself.

**Catalog got the proportionate half.** No write path exists to guard, so a boundary there would be a
guard on a door that does not exist; a unit test asserts its sole writer's constant is a readable zone,
and — the part that matters — the CI check *"A zone may not be stored without being parsed"* scans all
five services, so the day catalog grows onboarding the `setZoneId` that comes with it is red. Watched
firing seven ways. Two shapes rejected and argued in D60: catalog refusing to *serve* a bad zone, and
parse-or-default with a loud signal (a signal after an irreversible write is a receipt, not a control).

**Reviewed 2026-09-09; sound, two non-blocking findings and an accuracy note, all applied.** The
sweep was **line-based**, so a wrapped `.zoneId(\n  raw)` evaded it entirely — reproduced, the check
printed *ok, 5 zone writes* without counting the planted write — and that is not a curiosity because
**prettier formats Java here** and produces exactly that shape from a long argument. Closed with a
`\.zoneId\([[:space:]]*$` alternation, which also makes a legitimately wrapped write red: fail-closed,
verified by wrapping the shipped site. The **ERROR log did not delimit the value**, so `Africa/Accra `
rendered identically to the readable zone in the one place the refusal sends an operator — now
`zoneId '{}'`, pinned by a test on the *formatted* message rather than the pattern. And the check's
service list is **enumerated, not derived**; that is stated plainly in D60 and in the check's own
comment, with why it is acceptable (every failure mode that exists today exits 1 loudly).

---

## NEW-21 — The generated Kafka sample writes to the shared broker with no caller at all · DONE (D62)

**Closed by D62.** `kafkaProducer` is out of `spring.cloud.function.definition` in all five services;
the beans stay, per D54's rule for an orphan. The architect chose that shape over deletion **with a
falsification condition attached** — *if unnaming it does not stop the publishing, delete instead* —
and the condition was tested rather than assumed. It holds, so no departure was taken.

**The proof is a throwaway broker on no docker network, never the shared one.** As generated, catalog
alone auto-creates `kafkaProducer-out-0` at startup and writes **61 messages in 62 s**; the gateway,
on the reactive stack, is **identical**; with nothing running the offset is frozen. With the name
removed, on a *fresh* broker, that topic is **never created at all** — a stronger result than a
stalled offset — while `sse-topic` still is, which is the control proving the service is still bound
and still doing its Kafka work.

**Three things this item asked to be established, established:**

| | |
| --- | --- |
| the rate, re-measured | 5,779,754 → **367 in a timed 62 s, 5.92/s**; 176,000 higher than D59's reading two weeks earlier |
| the poll interval | **1s**, a framework default from `PollerConfigEnvironmentPostProcessor`, read out of the bytecode; nothing here configures any of the three properties that override it |
| the gateway | **no different.** Measured separately rather than reasoned from the stack |
| anything consuming it | **nothing.** Every consumer group on the broker was described; not one names `kafkaProducer-out-0` |

**And the sixth publisher, which this item flagged as the reason it might be a workspace-wide
finding.** It is: 5.92/s ÷ 1/s is six publishers and five are ours, so hc-market's share is ~5/s or
about **432,000 a day**. `hc-admin-service` is registered as a consumer group on **`sse-topic`** — the
other half of this same generated sample — which places the same generated file in a sibling without
opening a sibling's repository. **The parent `CLAUDE.md` is where that belongs and this package did
not write it there**, because that file is outside this repository.

**What guards it**: `KafkaSampleSupplierIsNotPolledIT` in all five services, byte-identical — the
inverse of the `producesPooledMessages` D59 refused to carry forward, asking the test binder the
opposite question, with the consumer half as its positive control. Watched red first in catalog with
the config mutated back. Plus a derived CI check, *"No service may bind the generated Kafka sample
supplier"*, watched firing **fifteen ways** including the five services mutated one at a time.

**The first commit documented the byte-identity in three places and enforced it nowhere**, which
review caught — the house failure mode inside a package about a defect nothing could see. The second
commit makes the guard the **fourth verbatim-copy family** beside `SubjectPseudonym`, `SeedCalendar`
and `MarketCalendar`: CI diffs the copies, deriving both the service list and the reference from
`messageBroker kafka` in `jdl/*.jdl` rather than enumerating either, refusing a family of one and
announcing a reduced comparison instead of making one silently. Five more mutations, including one
character changed in each service's copy in turn and a single extra newline.

**Review also ran the experiment this package argued for rather than performed**: a catalog jar built
with `broker/KafkaConsumer.java` deleted and the `definition:` line removed — the exact end state of
the "finish the cleanup" tidy-up — auto-created `kafkaProducer-out-0` and held **104 messages within
two minutes**. The reason `kafkaConsumer` must stay named is therefore measured, not reasoned.

**The consumer half is settled, not omitted.** `kafkaConsumer` stays named, and it has to: Spring
Cloud Function auto-discovers a *lone* function bean when there is no explicit `definition`, which the
build itself says out loud — `Multiple functional beans were found [kafkaProducer, kafkaConsumer],
thus can't determine default function definition` — so unnaming the consumer would leave the supplier
the only function bean and bind it again. Its unbounded sink stays with it, unchanged and still at
`sse-topic` offset 0.

Everything below is the item as it stood.

---

Opened by **D59**, which found it while establishing what NEW-17's `/publish` actually did. It is the
same generated sample and the same shared broker, and it is **larger than the door NEW-17 closed** —
but it is not a door, so it was not ridden in on that package.

`broker.KafkaProducer` is a generated `@Component implements Supplier<String>` returning the constant
`"kafka_producer"`. `application-kafka.yml` names it in `spring.cloud.function.definition` and binds
it to `kafkaProducer-out-0`, and Spring Cloud Stream polls a supplier on a **one-second** default
schedule. All five services carry it, and the `kafka` profile is active in every environment through
`spring.profiles.group` (see D59), so all five have been doing this since they first started.

**Measured on the shared broker, by reading it:**

| | |
| --- | --- |
| `kafkaProducer-out-0` end offset | **5,603,896** |
| rate over a timed 60 s window | **366 messages, 6/s** |
| consumers | none — no group is registered against it |
| `sse-topic` end offset | **0** (the consumer half has never received anything) |

Six per second rather than five because hc-market's five are not the only JHipster applications on
`hc-shared-quality-kafka`; the broker is `hc-infra`'s and four products borrow it (D27). Attribution
of the *whole* 5.6 M to hc-market is therefore **not** established — what is established is that five
of the six services publishing are ours, and that nothing anywhere consumes the topic.

**The other half is the consumer.** Since NEW-17 the gateway's `broker.KafkaConsumer` sink —
`Sinks.many().unicast().onBackpressureBuffer()` — has no subscriber at all, so anything arriving on
`sse-topic` buffers without bound. That is not a regression from D59: `/consume` had no caller either,
and the topic has never carried a message. It is worth closing in the same pass.

**Why it needs a package.** The remedy is not deleting two classes. `spring.cloud.function.definition:
kafkaConsumer;kafkaProducer` lives in generated `application-kafka.yml`, so a definition naming a bean
that no longer exists refuses to bind and the service will not start — which means editing a generated
file, which means a new **regeneration-hazard row** in CLAUDE.md's Liquibase-and-config table for each
of five services. Three shapes are worth weighing: delete the supplier and its binding; keep the beans
and empty the `definition`; or leave both and set the poller's interval to something that is not one
second. The first is cleanest and costs the most rows. `producesPooledMessages` — deleted with the
sample ITs by D59, precisely because carrying it forward would be CI asserting this defect works — is
the test that would have to come back in some form to pin whichever is chosen.

**One thing to establish before choosing.** hc-admin, hc-patient and hc-professional are JHipster
estates on the same broker and the sixth publisher is probably one of them; if so, this is a
workspace-wide finding rather than an hc-market one, and the parent `CLAUDE.md` is where it belongs.
That was not checked — D59 was not authorised to touch another product's repositories.

---

## NEW-22 — The gateway seeded `admin` with a published password, in every profile · DONE (D61)

Opened and closed by **D61**. The gateway's `InitialSetupMigration` was the untouched generated
Mongock changeunit — `@ChangeUnit(id = "users-initialization", order = "001")`, **no `@Profile`** —
creating `admin` and `user` with two committed bcrypt hashes and `setActivated(true)`.

**What made it a production defect rather than a dev convenience**, each part established rather than
assumed:

| | |
| --- | --- |
| Does it run under `prod`? | **Yes.** `mongock.migration-scan-package` is in the **base** `application.yml`, no `prod` override anywhere |
| Is the hash the login? | **Yes — measured.** A test asserting `matches("admin", hash)` was false went red against the generated file |
| Is the account privileged? | **Yes.** `ROLE_ADMIN`, and `/api/admin/**` is `hasAuthority(ADMIN)` |
| Does the token travel? | **Yes.** One signing key, five services |
| Did anything say so? | **No.** Zero hits for `InitialSetupMigration` across `docs/` and `CLAUDE.md` |

A first production deploy creates exactly the empty database this seeds, so the estate would have come
up on `market.abofonsa.com` with `admin`/`admin` working. **Nothing in any suite was red, and nothing
would ever have been.**

**The shape is hc-professional's** — administrator in every profile because an empty production
database has no other way in, demo accounts skipped under `prod`, idempotent `saveUserIfMissing`. That
forces it from a `@ChangeUnit` to an `@Component implements ApplicationRunner`: a changeunit is not a
Spring bean, so an `Environment`, a `@Value` and a `PasswordEncoder` have nowhere to live in one, and
Mongock never re-runs a recorded changeunit, which makes "seed whatever is missing" inexpressible.
Mongock stays with an **empty scan package** — and that it tolerates one was established by booting an
IT, not by reading, because it was the one genuine unknown in this shape.

**The open question — `prod` with no password configured — is answered `refuse`, and that departs
from the sibling.** hc-professional falls back to a value derived from the login and warns; nothing in
that repository sets `GATEWAY_ADMIN_PASSWORD`, so on that estate the fallback *is* production. This
estate follows D35/D45, its own rule for a required secret with no safe default. The refusal sits
inside the supplier that only runs when the account is missing, so an estate whose administrator
already exists — and has since rotated their password — keeps deploying without the variable.

**hc-admin's `@Profile({dev,test})` was rejected, and the reason given for rejecting it needed
correcting**: hc-admin *does* have a production path, `AdminBootstrapInitializer`, so it is not true
that a fresh hc-admin estate has no operator account. Its actual cost is two classes that both create
an administrator, in two packages, under two property names, one of which silently does nothing when
unset.

**What guards it.** Eight unit tests, plus `.github/checks/admin-seed-wiring.sh`. The first of its
four parts sweeps for a **committed bcrypt hash** rather than for the new logic, because
`InitialSetupMigration` is a generated file and a regeneration would put the changeunit back and take
the logic with it — a hash is the one thing the generated version cannot return without. Watched
firing **14 ways** in `admin-seed-wiring-test.sh`, each guarded thing mutated separately, including
the three fail-open cases this repository keeps rediscovering: a missing subject file must fail rather
than pass having read nothing, and prose describing the gate must not satisfy the check.

**Left for someone else, in another repository:** `hc-patient/gateway` has the **identical** defect —
same untouched changeunit, same base-config scan package, no profile gate. Not touched; it is a
different product. `hc-professional` is a milder variant of the same family.

---

## NEW-23 — The OpenTelemetry agent is in every image and attached in no environment · DONE (D63)

Opened by **D62**, which found it while reading the running quality containers for an unrelated
reason. It is deliberately **not fixed there**: it changes what the quality box runs, and the box was
under a no-writes instruction for that package.

**Observability has never run in any environment, and no document records that.** CLAUDE.md has a
whole trap section on the agent — the `combine.self="override"` that stops Jib silently dropping it,
the check that it *instruments* rather than merely loads, the 2.30.0-vs-2.9.0 finding — and `build.yml`
carries *"Every pom must still carry the OpenTelemetry wiring"*. All of that guards the **build** half
of a runtime path that has executed nowhere.

**Established by reading the running quality containers, all five** (`85fba79`, read-only):

| | |
| --- | --- |
| `/app/otel-javaagent.jar` in the image | **present in all five**, 24,665,598 bytes — the Jib wiring works |
| `-javaagent` on the JVM's `/proc/1/cmdline` | **absent in all five** |
| `quality/compose.yml:97` | `JAVA_OPTS: -Xmx512m -Xms256m` — hardcoded, no agent, no `OTEL_*` anything |
| `deploy/docker/docker-compose.dev.yml` | **no `JAVA_OPTS` at all**, and no `OTEL_*` variable anywhere in the file |
| `deploy/docker/docker-compose.prod.yml:104` | `JAVA_OPTS: ${HC_JAVA_OPTS:-…} ${HC_OTEL_JAVA_OPTS:--javaagent:/app/otel-javaagent.jar}` — the only place it is attached |
| production | **has never been deployed.** `deploy/prod-server/README.md` says so before it says anything else (D49) |

So the one compose file that attaches the agent belongs to the one environment that has never run,
and the two that have run do not attach it. The image is built correctly, shipped correctly, and the
flag is never passed.

**What follows from it, and is the reason this is a package rather than a one-line edit.**
`deploy/observability/hc-market-rules.yaml` declares five alerts and **every one of them is a query
over metrics only the agent emits** — `absent(jvm_thread_count{service_name="hc-market-gateway"})`,
`rate(http_server_request_duration_seconds_count{…})`. They have never had a data source. Two of them
alert on *absence*, so on a tenant where hc-market has never reported they would be **firing
continuously** if the rules were ever loaded, and the `HcMarketGatewayDown` annotation already tells
the reader to check `JAVA_OPTS` for the agent flag before concluding the service is down — advice
written for the case that turns out to be the permanent state. CI checks that file parses and that its
group names are unique; nothing checks that anything answers its queries.

**Three things to decide, none of them obvious:**

1. **Does quality attach the agent?** It is the argument for the quality box existing — it is where a
   deploy-shaped defect is meant to surface, and this is one. Against: `jacserver` has no
   `otel-collector` on it, so the exporter would fail on every export and the estate would grow a new
   class of noise. Whether an unreachable OTLP endpoint is silent or loud on 2.30.0 is **unestablished**
   and is the first thing to measure.
2. **Does the agent get verified as instrumenting, not merely loading?** CLAUDE.md already names the
   trap — `jvm_thread_count` comes from MBeans and is reported whether or not a single application
   class is rewritten, so `service:up` being green is not evidence. The check that is: an
   `OTEL_TRACES_EXPORTER=logging` run producing a `SERVER` span with an `http.route`.
3. **Does the rules file stay as it is?** Five alerts over a signal nothing emits is not a monitoring
   gap, it is a monitoring claim that is false. Either the signal arrives or the file says what it is
   for.

**Not in scope for whoever takes this**: the Jib `extraDirectories` wiring, which is correct and
proven by the jar being in all five images.

**Closed by D63, and the body above is left as written including the part of it that is wrong.**
*"`jacserver` has no `otel-collector` on it"* is false: this host runs the `monitoring-quality`
compose project — otel-collector, grafana, mimir, loki, tempo, alloy — from a different repository,
and every container in it has been exited for days. So the first thing to measure was not "is there
a collector" but "what happens when the agent is attached and the collector is down", and the answer
is 35 ERROR lines with stack traces every 150 seconds at the agent's own default intervals, against
an estate whose five services currently carry zero. All three questions were answered: quality and
dev attach nothing and gain a one-variable opt-in; the instrumentation check is
`deploy/verify-otel-agent.sh` rather than a paragraph; the rules file says in its own header that
nothing answers its queries, held there by CI in both directions. It opened **NEW-24**.

---

## NEW-24 — hc-market's quality stack is the only one not on `qualitynet`, so it cannot reach a collector · DONE (D64)

Opened by **D63**, which found it while establishing what turning the agent on in quality would
actually do. Deliberately not fixed there: it is a change to the network topology of the last gate
before production, and the only way to exercise it is to restart that stack — which D63 was
instructed not to do, and which would have proved nothing while the collector is down anyway.

**The monitoring stack's own compose says how an application joins it**, and hc-market never did:

> `qualitynet` is what makes the whole stack work, in both directions: Alloy needs to reach
> hc-*-quality containers to scrape /actuator/prometheus, and the apps need to resolve
> `otel-collector` to export OTLP. […] Each application compose file therefore also joins
> qualitynet.

| | |
| --- | --- |
| `hc-admin/quality/compose.yml`, `hc-patient`, `hc-professional` | `networks: [quality, qualitynet, hcnet]` — **all three** |
| containers on `qualitynet` right now | **six**, two from each sibling, **none of ours** |
| `hc-market/quality/compose.yml` | `networks: [quality, hcnet]` |
| the collector's published ports | `127.0.0.1:4327` / `4328` — the **host's** loopback, unreachable from inside a container |

So D63's `HC_OTEL_JAVA_OPTS` switch turns the agent on and, on this box, it then exports into
nothing. That is stated in the compose comment rather than glossed — **one variable plus one
network** — but it is the one thing standing between hc-market and the quality box being able to
demonstrate its own observability story.

**The shape to copy is the siblings'.** Each declares `qualitynet` as external and its `startup.sh`
creates it if absent, which is what stops the stack becoming unstartable on a host where the
monitoring project has been fully `down`ed. Doing it here is three lines in `quality/compose.yml`
and one in `quality/startup.sh` — and then a real `--local` restart, because an unexercised change
to that stack's networking is exactly the class of change this repository measures rather than
reasons about.

**Two things to establish before or during**, neither of which D63 could:

1. Whether joining `qualitynet` changes anything about name resolution for the five services. They
   are already on `hcnet` with three sibling products, and CLAUDE.md's "compose publishes a service
   NAME as a DNS alias on every network it joins" trap is exactly about a third network arriving.
   The container names here are all `hc-market-quality-*`, so the risk looks low and *looks low* is
   not the standard this repository uses.
2. Whether the agent, once it can reach a running collector, is quiet. D63 measured it loud against
   a **dead** endpoint; nobody has measured it against a live one here, and the whole argument for
   leaving it off rests on that first number.

**Not in scope for whoever takes this**: starting, fixing or configuring the `monitoring-quality`
stack. It is another repository's, and the sibling stacks tolerate its being down while joined to
its network, which is the evidence that joining is safe independently of its state.

**A finding for the parent workspace, not for this repository.** That same Alloy config declares
*"NO APPLICATION SCRAPE TARGETS, and that is correct for this host […] the quality applications do
not expose Micrometer to be scraped — they PUSH OpenTelemetry to the collector"* — and no sibling
quality JVM carries `-javaagent` either, on eight readings of `/proc/1/cmdline`. The quality box
therefore collects container logs and docker stats for the whole estate and **no application metrics
from any product**. Four products wide, and hc-market is the only one that has written it down.

**Closed by D64.** `quality/compose.yml` declares `networks: [quality, qualitynet, hcnet]` on the
`x-service` anchor — the five app services and not the five databases — with `qualitynet` external
and named from `${HC_OTEL_NETWORK:-qualitynet}`, and `quality/startup.sh` creates it when it is
missing, which is the siblings' shape. Exercised by a real `./quality/startup.sh --local`: all five
containers on 172.24.0.8–.12, all five healthy, `--javaagent` absent and `ERROR` zero in all five,
the same argv as before, and `--verify` green including through the hostname. The six sibling
containers were not touched.

Both of the item's questions were taken up, and only one of them could be answered. **Name
resolution did not change at all** — measured with `getent hosts` from four containers before and
after, and identical: `gateway` was already a four-way claim on `hcnet` including ours, every
application container on `qualitynet` is also on `hcnet`, and Docker answers from the nearer network
first, so no answer in either direction carries a `172.24.x` address. **Whether the agent is quiet
against a live collector is still unmeasured**, and that is stated rather than reasoned around:
`monitoring-quality` has been exited since 2026-09-05, starting it is another repository's, and D63's
35-ERROR figure remains a measurement of a *dead* endpoint only. Joining the network removed the
second obstacle, not the first. It opened **NEW-25**, **NEW-26** and **NEW-27**.

---

## NEW-25 — `HC_SHARED_NETWORK`, `_CONSUL` and `_KAFKA` do nothing on the quality box · DONE (D66)

Found by **D64** while deciding how `HC_OTEL_NETWORK` should be wired, and it is the same defect one
variable family along.

`quality/startup.sh` read all three and used them in `check_shared_plane`, so overriding one changed
which network and which containers the **preflight checked**. `quality/compose.yml` then hardcoded
every one of them — `name: hcnet`, `hc-shared-quality-consul:8500`, `hc-shared-quality-kafka:9092` —
so the stack joined and addressed the default regardless. `CLAUDE.md` said *"Override which shared
plane a stack uses with `HC_SHARED_CONSUL`, `HC_SHARED_KAFKA` and `HC_SHARED_NETWORK` — both
`deploy-dev.sh` and `quality/startup.sh` read them"*, and for the quality half that was true of the
reading and false of the effect.

**`deploy-dev.sh` is the correct shape and was the model**: it exports all three and
`docker-compose.dev.yml` interpolates them (`name: ${HC_SHARED_NETWORK:-hcnet}`). D64 wired
`HC_OTEL_NETWORK` that way deliberately rather than copying the siblings, whose `OTEL_NETWORK` has
exactly this defect.

**Closed by D66.** `quality/compose.yml` interpolates all three, `env_for_compose` exports all three
on every action including teardown, and `.github/checks/shared-plane-wiring.sh` — with its own test
beside it — renders the file a second time with each variable set and asserts the value **moved**,
rather than grepping for the spelling that would move it. The unset render is **byte-identical** to
the committed baseline, and `docker compose up -d --dry-run` through the real `env_for_compose`
reported `Running` for all ten containers, so nothing on the quality box changes for anybody who sets
none of them.

**It found a second defect on the way, and that one was not costless.** `check_shared_plane` asked
whether the broker and Consul were *running* and never whether they were **on the network the stack
joins** — measured, the whole function passed against a throwaway empty network and its success line
printed `…on hc-market-d66-probe`, asserting a membership it had not looked for. Harmless while
compose hardcoded `hcnet`; live the moment the override started moving the join, and what it would
produce is D27's silence exactly: five healthy services publishing into nowhere. Refused now, fatally
and fail-closed. See **D66 §4**.

---

## NEW-26 — `verify-outbox-recovery.sh` reconnects booking without its alias · DONE (D68)

Found by **D64**, by measuring name resolution before and after a restart for an unrelated reason.

The script severs `hc-market-quality-booking` from `hcnet` and reconnects it — on an `EXIT` trap and
again at the end — with a bare `docker network connect "$NET" "$BOOKING_CTR"`. That restores the
*connection* and not the **alias**: compose publishes the service name as a DNS alias when it creates
the container, and a manual reconnect publishes only the container name. Measured on the running
quality box before this package's restart: `hc-market-quality-booking` carried `aliases=[]` on
`hcnet`, and `getent hosts booking` from inside two sibling containers answered nothing. A restart
fixes it, silently, so the state is invisible unless somebody looks between one. **← that last
sentence is measured false; see the correction four paragraphs down. A plain `compose up` does not
fix it, which is the restart anybody reaching for a fix would run.**

**Closed by D68**, which reads the alias set back off `docker inspect` immediately before the
disconnect and hands it to the one `docker network connect` left in the file — never `--alias
booking`, because the set belongs to compose and `HC_BOOKING_CTR` exists so this script can be
pointed at an estate whose names it does not know. The run now *asserts* the set came back, ordered,
instead of merely performing the reconnect.

**Two things this item said turned out to be wrong, and the second is the more useful.** It said
nothing addresses booking by its short name over `hcnet`, quoting
`http://hc-market-quality-messaging:8080` — true of that line, false of the file: `quality/compose.yml`
carries **nine** short-name addresses — five gateway routes (`catalog`, `booking`, `messaging`,
`payout`, and `booking` again on the webhook route) and four cross-service base URLs
(catalog→booking, booking→catalog, booking→payout, payout→booking), so **`booking` alone is four of
them** — and `CLAUDE.md` generalised the same way. D68 §3 lists all nine by line and says why the
list rather than the number is the artefact: this count was wrong three times running, in the section
whose whole subject is a count asserted rather than measured.
And what actually makes the loss free is not that: `booking` is satisfied on
the **project** network for the stack's own five and, since D64, on **`qualitynet`** for anything
outside — measured, `hc-admin-quality-service` answers `172.24.0.9` for `booking` and `172.21.0.7` for
`catalog`. This item's own "answered nothing" measurement predates D64's join and would not reproduce.
The name never went missing; it moved planes, one of them added by the package that found the defect.
See D68 §3.

**An empty captured set restores nothing, deliberately** (D68 §4): the quality box is in that state
today, and the script prints a `note` naming the container, the network and the remedy rather than
inventing an alias set from the compose service label or refusing a run over a cosmetic residue.

**"A restart fixes it, silently" — said above, and MEASURED FALSE for the restart people run** (D68
§9). On a throwaway compose project with an external network: a plain `compose up -d` with nothing
changed reports `Running` and leaves the alias set empty, because compose recreates only what changed
— D67's rule, one docker object along — and `quality/startup.sh` runs exactly that, with no
`--force-recreate`. What *does* repair it: a `down` then `up`, a `--force-recreate`, or any roll to a
new `TAG`, which recreates as a side effect. The live endpoint's `IPAMConfig` is the CLI's empty
struct rather than compose's `<nil>`, which is how that history was read off the box rather than
guessed.

**The live container was NOT repaired here** — that is a recreate, and therefore a roll-time act. The
roll needs `--clean` then `up`, or a new `TAG`; a same-tag re-run will not do it.

Guarded by `.github/checks/outbox-alias-restore-test.sh`, which extracts the shipped functions and
asks a real daemon on its own throwaway network — with a bare reconnect beside it as the positive
control, because an assertion that aliases came back is satisfied for the wrong reason by a probe that
never lost them. It needed a **second** comment stripper: the shared one is a Java stripper and
removes nothing from a shell script while reporting success. That stripper has its own test now
(`strip-sh-comments-test.sh`), because the survival properties its header documents — `${#arr[@]}`,
`$#`, `${x#pfx}` — were enforced nowhere while the subject script depended on the first of them.

**At review, four should-fixes, all taken** (D68 §11–§12). The check had a fail-open that was its own
argument: `count == 1` was applied to the `docker network connect` and not to the capture, so a
**second** capture after the disconnect passed everything while the defect was fully back — the array
overwritten with the now-empty set, and the script's own assertion comparing `""` against `""`.
Guarded twice now, and `ln_of` takes the **last** match rather than the first. The capture also could
not tell an empty answer from an unaskable docker, which fired the note with the wrong diagnosis and
then severed anyway; D65 and D67's rule applies — a non-zero probe means the question went unanswered
— so it is status-checked and refuses before cutting. The short-name count was wrong in four places
and is enumerated by line now. And `peer` was created one line before its cleanup knew its name.

---

## NEW-27 — `startup.sh` cannot tell a first run from a git worktree, and mints a new pepper · DONE (D65)

Found by **D64**, which ran the quality stack from a worktree and had to notice this before running
anything.

`quality/.jwt-secret` and `quality/.privacy-pepper` are gitignored, deliberately (D35): they are
secrets even in quality. A **git worktree** therefore has neither, while the databases those secrets
belong to are the same containers and the same volumes. `resolve_secret`'s `else` branch could not
tell the two situations apart — no file, no environment variable, so generate — and a fresh pepper
against databases whose `erased_subject` rows were written under the old one is D35's orphaning,
arrived at without anybody deciding anything. The signing key half is merely annoying; the pepper
half is the one that cannot be undone.

D64 side-stepped it by copying both files across before its first run. **Closed by D65**, which
reproduced it first — in this repository's own worktree, against the live quality volumes, without
starting anything — and then had `resolve_secret` ask docker.

- **The refusal.** No pepper on disk here, none in the environment, and this compose project's
  volumes already exist: fatal, naming the volumes it found and the three ways out. Nothing is
  written before it refuses, so a rejected run leaves the directory as it was.
- **"The volumes exist" is two `docker volume ls` calls, unioned**, because docker ANDs filters of
  different kinds — the compose **label** and the anchored **name** each miss a case the other sees,
  measured on throwaway decoys. **Any** of the project's volumes counts, which is safe because this
  script writes the pepper file before anything creates a volume.
- **A genuine first run is untouched** and is asserted by name, including that the probe returns an
  empty list by **succeeding**. That distinction was this package's own defect and is written up in
  D65 §7: the first version returned non-zero on an empty list under `pipefail`, so it would have
  refused the one estate it must let through.
- **The signing key keeps the opposite treatment** (D65 §5) — a new one is recoverable, so it warns
  loudly against existing volumes rather than refusing.
- **A teardown is not refused, and no longer writes down the pepper it invented** (D65 §6) — the
  second decision, which this item did not anticipate: without it `--down` was a one-flag bypass of
  the refusal.
- **The throwaway arm re-checks the teardown rather than inheriting it** (D65 §8, added at review).
  Its comment asserted the invariant and nothing enforced it, so a *counting* regression in the guard
  twenty lines up — measured, and green across all 35 assertions at the time — fell through to
  starting the stack on a pepper that is not even written down. The refusal is one volume now, not
  two, and the arm refuses anything that is not a teardown.

`.github/checks/quality-pepper-persistence-test.sh` grew from 10 assertions to 40 and now exercises
the probe against a real daemon on a throwaway project of its own.

---

## NEW-28 — the quality project's containers were built from two different checkouts · DONE (D67)

**Both halves are now done — the engineering by D67, the live repair by the roll of `e834137` on
2026-09-09.** Measured after it, and again on 2026-09-10: `docker compose ls` names **one** config file
for the project and **all ten** containers carry it; the five volumes survived with unchanged creation
dates; and every row count came back — `catalog.review` 68, `catalog.professional` 18,
`booking.booking` 296, `payout.ledger` 261, `messaging.notification` 29, plus D57's founding
`brokerage_config` row at `1970-01-01` and D65's single `privacy_pepper_witness` row, which are the two
that would have been quietly unrecoverable. The guard refused the roll first, exactly as designed,
naming both paths and annotating the missing one on precisely the five `-db` rows; `--down` then `up`
was the documented way through.

*This line read `PARTLY DONE … the live repair is not done` for a day after the roll had done it, which
is the `(unmerged)` defect in a new place: a status that was true when written and stopped being true
without anything changing it.*

The original engineering note follows.

**Closed as engineering by D67; the live repair was a roll-time step.** The
recurrence is prevented — `quality/startup.sh` asks docker which checkout the project's containers
were created from and refuses an `up` from anywhere else, with
`.github/checks/quality-project-checkout-test.sh` green at 28 assertions and red ten ways. What
remains is the half this item names below and no worktree may do: **recreating the five database
containers from the main checkout**, which is `./quality/startup.sh --local --down` (keeps every
volume) then a fresh `up`, from `main`, after merge. D67 §10 is the procedure, with what to check
before and after. **From `main`, today, an `up` will refuse until that is done** — the guard reads
the databases' stale label, which is the mechanism that forces the repair rather than a fault.

The item's closing question is answered with evidence in D67 §6: **nothing else points somewhere
temporary.** Every mount of all ten containers was enumerated from `docker inspect` — the only host
path any of them binds is `SEED_DIR`, on the four seeded services, and all four name the main
checkout today. The five databases have **no bind mounts at all**; their data is in project-prefixed
named volumes, which is why the stale label on exactly those five has cost nothing.

Two things the item assumed turned out otherwise, both measured on a throwaway project rather than
argued. A bare `docker compose -p hc-market-quality ps` **and a bare `down`** both work at rc 0 even
with one config path deleted — they read the labels, not the files — so the identity harm is smaller
than written; only `config` fails. And `compose up` relabels **only what it recreates**, so an `up`
from the wrong checkout *splits* the project rather than moving it and no later `up` from the right
one puts it back. That second fact is why the answer is a refusal and not a warning.

**The item as originally written follows unchanged**, because D67 contradicts two of its statements
and the record of what was believed is worth keeping beside the correction.

Found by **D65** while establishing what docker knows about the `hc-market-quality` project.
Measured, not reasoned: `docker compose ls` names **two** config files for that one project, and the
per-container labels say which is which.

```
hc-market-quality-{gateway,catalog,booking,messaging,payout}     …/hc-market/quality/compose.yml
hc-market-quality-{gateway,catalog,booking,messaging,payout}-db  …/.claude/worktrees/agent-a3d048…/quality/compose.yml
```

The five application containers were last recreated from the main checkout; the five **databases**
still carry the path of D64's worktree, which is where they were created. So half the live quality
stack records its provenance as a directory that stops existing the moment that worktree is pruned.

**It costs nothing today**, which is why it is an item rather than a fix. `quality/startup.sh` always
passes `-f "$HERE/compose.yml"`, so every supported operation names the file explicitly and none of
them reads the label. What breaks is the unsupported spelling — a bare `docker compose -p
hc-market-quality ps` or `down`, which resolves the project from those labels — and `docker compose
ls`'s output, which is what somebody looking for the stack reads first.

Closing it means recreating the five database containers from the main checkout, which is a restart
of the data tier of a box carrying several cycles of data, D57's founding row and the erasure state
NEW-27 is about. That is not a thing to do as a side effect of another package: it wants its own
window, and it wants `verify-cycle.sh` run after it. It is also the general shape of NEW-27 one level
up — **a stack whose identity is a directory, driven from directories that come and go** — so
whoever takes it should ask whether anything else in the project's labels points somewhere temporary.

---

## NEW-29 — `deploy-dev.sh`'s preflight cannot tell "running" from "reachable" either · DONE (D69)

**Closed by D69.** `shared_plane` now carries D66's whole loop: three outcomes where docker's answer
has three, and the membership refusal — measured against the live daemon in four states, the empty
throwaway network among them, where at `d3291a5` the function **passed** and printed
`…on hc-market-d69-probe`. The success line was rewritten to claim only what it asks. **Fatal**, on an
argument established for this script rather than carried over: dev's router is a `case` at the foot of
the file calling `preflight` per branch, and `down`, `status` and `logs` do not call it — so a broken
plane cannot wedge the teardown or the diagnostic beside it, and part 5 of the check now asserts that
premise rather than leaving it to a comment. `.github/checks/shared-plane-wiring.sh` was extended
rather than duplicated: part 4 walks `script:function` pairs (an unliftable function is an error, not
a skip), part 3 stays quality-only for a reason D69 §4 re-established rather than inherited, and part
5 asserts the **exact set** of router branches that call `preflight` — `{up, reseed, restart}` — after
review reproduced both holes in the deny-list it replaced: `up` losing its own `preflight` and a new
`doctor)` branch gaining one were each green (D69 §10). Test green at 26 with ten new mutations.
Appendix A re-embedded twice.

**What is NOT done, and it is the honest limit**: the guard has never run *inside* `deploy-dev.sh`.
Every measurement is the shipped function lifted out of the file, because the dev estate is still
wedged — now **NEW-31**, with the five containers' state read off docker and the remedy named. Nothing
below is stale; it is kept for the reasoning.

Found by **D66** while closing NEW-25 on the quality box, and it is the same blind spot in the dev
script's own **`shared_plane`** — `deploy/deploy-dev.sh:216`, and note the name: the quality copy is
`check_shared_plane` and this one is not, so a grep carried across from D66 finds nothing here and
reads as "already fixed".

That function asks four things — the network exists, `$SHARED_CONSUL` and `$SHARED_KAFKA` are
running, Consul has a leader, the broker answers — and **none of them is whether those two containers
are on the network the dev stack joins**. Measured on the quality copy, which is the same four checks:
against a throwaway empty network the whole function passed and printed `…on hc-market-d66-probe`,
asserting a membership it had never looked for. The repair is the three lines D66 added to
`quality/startup.sh` — ask the container which networks it is on, `grep -Fxq "$SHARED_NETWORK"`,
refuse — plus `./deploy/sync-appendices.sh`, because `deploy-dev.sh` is Appendix A.

**It is milder here than it was there, which is why it is an item rather than part of D66.**
`docker-compose.dev.yml` has always interpolated all three, so the dev override is *honest* — set
`HC_SHARED_NETWORK` and the stack really does join what preflight inspected — and the failure needs a
network that exists, carries neither shared container, and was named deliberately. What is left is
the case D27 exists for: a plane whose broker cannot be resolved, five healthy services, and
`MessageDeliveryException` on a timer as the only signal.

It was not fixed in D66 because the dev estate is **wedged** — the five un-killable containers D27
left behind — so the change could not be exercised against a running dev stack in that package, and a
guard nobody has watched refuse is a guard of nothing. Whoever takes it should run
`deploy-dev.sh up --no-build --services catalog` after, which is what D27 used.

**Extend `.github/checks/shared-plane-wiring.sh` in the same commit, or the dev copy ships
unguarded.** Its parts 1 and 2 walk both pairs, but parts 3 and 4 — the export and the membership
refusal — read `HC_STARTUP`, which defaults to `quality/startup.sh` and nothing else. That was
deliberate and is stated in the check's own header (deploy-dev.sh has no `env_for_compose` to run),
so the day this item adds the membership line to `shared_plane` there is **nothing in CI that would
notice it being removed again**. Part 4 is already parameterised by `HC_STARTUP` and stubs docker, so
the work is a second invocation and a function name, not a second check — but the function names
differ, so the `awk` range that lifts it out has to be parameterised too.

---

## NEW-30 — after a `down`, nothing records which checkout the stack came from · WON'T (D70)

Opened by **D67 §9**, which named it rather than widening its own fix into it.

D67's guard reads `com.docker.compose.project.config_files` off the project's **containers**. A
`down` removes the containers and keeps the volumes, so from that moment there is no record on this
host of which checkout the stack belongs to, and an `up` from a worktree looks exactly like a first
run. It is not unguarded — **D65's pepper refusal fires on precisely that state**: volumes present,
no `quality/.privacy-pepper` in this directory, fatal. But the documented way past that refusal is
to copy the pepper across, which is what D64 did and what the message itself suggests, and an
operator who does that passes both guards. What they get is a running stack whose seed bind mount
belongs to a directory that will be deleted — D67's harm, reached through the sequence
`--down` in a worktree, copy the pepper, `up`.

**It is narrow and it is real.** Narrow, because it needs a teardown *and* a copied pepper *and* a
directory that later goes away; real, because that is three ordinary steps and two of them are
printed by this repository's own error messages.

Why D67 did not fix it: every candidate record that survives a teardown is **a file in a directory**,
and a file in a directory is the proxy D65 rejected — a worktree does not carry it, so it reproduces
the original defect one layer along. A docker **label on the volumes** is the only object with the
right lifetime, and compose does not write one that names the checkout; setting one by hand means
`docker volume create` before the first `up`, which a first run has no reason to do. Whoever takes it
should start by asking whether the answer is a record at all, or whether it is D67 §4's fourth shape
— stop binding a host path for the seed — which would make the question moot rather than answered.

**Closed as `WON'T` on 2026-09-10, ratified — `decisions.md` D70.** The question above was asked and
answered: not a record, and not eliminated either. Three shapes were costed and rejected, and the one
that came closest was rejected on a ground D67 did not have:

- **Resolve `SEED_DIR` to the canonical checkout** (`git rev-parse --git-common-dir`) is the smallest
  change and needs no record at all, and D67's objection to resolve-to-canonical does not transfer —
  that was about silently running a *different checkout's compose file*, whereas CI already asserts the
  seed regenerates byte-identically from the prototype, so a different checkout's seed is provably the
  same bytes. Rejected as the wrong trade for the harm: it adds a git dependency to path resolution and
  a fallback branch to decide, to prevent a stale read-only bind mount.
- **Bake the seed into the five images** via the Jib `extraDirectories` block the poms already use for
  the OpenTelemetry agent. Measured, and **much cheaper than D67 assumed** — 296,031 bytes, mounted
  read-only, read once at startup; D67 called this "a release process, not eight lines" and that
  characterisation was too pessimistic. Rejected on a cost D67 did not name: production runs these same
  images with `HEALTHCONNECT_SEED_ENABLED=false`, so every production artefact would carry **18
  invented professionals with credentials and association registration numbers**. Not served — CI
  asserts the prototype's absence from `market.abofonsa.com` — but present, and adjacent to the reason
  that assertion exists.
- **Label the volumes at first run.** The only docker object with the right lifetime, as above. Rejected
  on three counts: it changes the first-run path on a fresh box; docker cannot relabel an existing
  volume, so the five live volumes stay unlabelled until a `--clean` that nothing else justifies; and it
  introduces a record whose only reader is a guard.

**What is kept instead is the trap, written down beside D65 and D67 in `CLAUDE.md`.** The residual is
honest and stated: the path stays walkable, and two of its three steps are suggested by this
repository's own error messages. The harm it leads to is a stale read-only bind mount, not data loss —
the volumes are untouched and the seed is not read again after startup.

---

## NEW-31 — nothing can exercise a dev-estate change, and the five containers say why · DONE (D72)

Opened by **D69 §7**, which named it rather than touching it. It is the reason NEW-29 waited a package
and the reason D69 could verify its own fix only by lifting the function out of the file: **two
successive packages have now been unable to run `deploy-dev.sh` at all.**

Read off docker rather than inferred, on 2026-09-10: five containers, `healthconnect-dev-{gateway,
catalog,booking,messaging,payout}-1`, state **`restarting`** with `RestartPolicy=no`, since
2026-08-30, on `healthconnect-dev_default` **alone** — not on `hcnet`. Each dies at context startup in
Hazelcast's discovery, on `I/O error on GET request for "http://consul:8500/v1/health/service/
healthconnectcatalog"`. That address is this stack's **own bundled Consul**, which D27 removed: they
are a pre-D27 estate looping against infrastructure this repository stopped declaring, and their
compose-derived names (rather than the `hc-market-dev-*` `container_name`s) date them the same way.

**The remedy is already written down and has never been run.** `deploy-dev.sh`'s `down` branch says
`--remove-orphans` "additionally sweeps the pre-2026-08-31 containers — this stack's own broker and
Consul, and the un-prefixed service containers — which is how you migrate a running estate onto this
file". So the item is small; what it is not is *free*, and that is why it is an item. A `down` is a
write to the daemon four products share a plane on, `CLAUDE.md`'s "un-killable" claim about these five
is **untested** (D69 read them and touched nothing), and the volumes underneath hold the only seeded
dev data anybody has — `healthconnect-dev_{gateway,catalog,booking,messaging,payout}-data`, which a
`--clean` would take with it.

Whoever takes it should decide the order deliberately: sweep first and then `up`, or `up` and let
`--remove-orphans` do it, are not the same act on a stack whose containers are in a state docker
itself is not moving out of. **And it is the package that can finally run what D66 and D69 could
not** — `./deploy-dev.sh up --no-build --services catalog`, then the shared-plane preflight against a
real estate, including the refusal, which no test in CI can reach.

**Clearing it was authorised on 2026-09-10 (D72 §1, full clean including volumes) and is blocked below
compose.** `down --clean` fails on all five with `tried to kill container, but did not receive an exit
event`; `docker rm -f` fails identically; `docker update --restart=no` succeeds and does not help.

**The cause recorded here was wrong for one hour on 2026-09-10 and is corrected in `decisions.md`
D72 §7.** It claimed one orphaned `containerd-shim` per container; the probe behind that claim
subtracted one for its own `grep` and reported the remainder, which returns 1 for a container with no
shim at all. `pgrep -fc "containerd-shim.*<id>"` returns **1 for an id that does not exist**, which is
how it was caught — the verification one-liner was tested against a fabricated id before being handed
over, and the fabricated id reported a shim. CLAUDE.md names that trap.

**What is actually true**, three ways with a healthy container as the positive control: `State.Pid=0`,
**zero** processes carrying the full 64-hex id, **no** `/sys/fs/cgroup/.../docker-<id>.scope`, and
absent from the 38 shim-backed ids parsed out of every shim's own `-id` argument. The control shows
pid 2433102, one process, and a cgroup — so the method can find what exists.

The containers are gone at the OS level. **Only docker's in-memory record still says `restarting`**,
which is why `stop`, `rm -f` and `down -v` all fail with *"tried to kill container, but did not receive
an exit event"*: the daemon is waiting for an exit from something that no longer exists.

**There is no narrow remedy.** The shim-kill this item recommended for an hour would have killed
nothing and read as a fix, which is worse than a visible failure. What remains:

- **`sudo systemctl restart docker`** — reloads the daemon's container state and clears the stale
  records. The only known cure. Bounces **47 running containers**, including three other products'
  only quality environments and the monitoring stack.
- **Leave them.** No process, no cgroup, no CPU — a daemon record and five volumes.

**CLEARED 2026-09-10.** The architect restarted the daemon; the five records went from `restarting`
to `Exited (255)`, which is removable. `./deploy/deploy-dev.sh down --clean` then took two runs — the
first hit a 300-second budget having removed one container, the second removed the rest, the five
volumes and the network. The four stragglers passed through `Dead` with
`removal of container … is already in progress` and cleared themselves over about six minutes; that
is docker finishing the job, not a second wedge.

**Final state, with the controls that make a zero non-vacuous**: dev containers **0**, dev volumes
**0**, dev networks **0** — while `hc-market-quality` still had **10** containers and **5** volumes
and the siblings **19**, before and after. Everything the daemon restart bounced came back: quality
10, monitoring 8, hc-vendor 5, admin/patient/professional 4 each, the shared plane 2.

So `deploy-dev.sh` is exercisable again, and the next `up` there is a **genuine first run** — the
state D65's "no volumes is a first run and still generates" and D67's "no containers at all is a
first run and proceeds" branches were written for and which nothing had ever exercised. That was the
reason this shape beat the containers-only recommendation, and it is now available to be used.

**That closing sentence read "Status is `BLOCKED` rather than `READY` because what remains is a person
with root, not engineering. Nothing was destroyed by the attempt — five containers and five volumes
are exactly as they were." until 2026-09-17, and it contradicted this item's own heading.** It was
written before the `CLEARED` paragraph above it and never revised, so an item whose `## ` heading said
`DONE (D72)` closed by declaring itself blocked on a person who had already done the thing. The heading
wins by the rule at the top of this file — and the general lesson is the one this file keeps recording
about itself: **a status in prose beside a status in a heading is one fact in two places**, and the
prose copy is the one nobody updates. Nothing else in this item is changed; the record of what was
wedged, why, and what cleared it is what it is for.

**RE-VERIFIED 2026-09-17, independently, and it has stayed cleared**: `docker ps -a` names **no**
`healthconnect-dev` or `hc-market-dev` container, `docker volume ls` holds **0** `healthconnect-dev_*`
volumes, and `docker compose ls -a` does not list the `healthconnect-dev` **project** at all — the
third of those being the probe this repository's own guidance says to use, because a filter built from
the name you expected cannot find what you did not expect. The control in the same output:
`hc-market-quality` reads `running(11)`, so the daemon was answering and a zero means something.

**It was resolved by an act outside these work packages** and this item claims no credit for it: D72
records the architect restarting the daemon on 2026-09-10 and a `down --clean` taking two runs. What
2026-09-17 adds is only that the state did not come back in the week since — which matters because
*five containers the daemon would not release* is exactly the kind of state that gets cleared once and
quietly returns.

---

## NEW-32 — `check_shared_plane`'s membership probe cannot tell "not on it" from "could not ask" · DONE (D71)

Opened by **D69 §10**, which fixed it in `deploy-dev.sh` and left the twin alone rather than editing a
ratified decision's file from outside its scope. It is **D68's fix 2 for a third docker object**, and
the third time this exact reading has been found in this repository.

`quality/startup.sh`'s `check_shared_plane` ends each loop iteration with

```
docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{println $k}}{{end}}' "$c" 2>/dev/null \
  | grep -Fxq "$SHARED_NETWORK" || die "$c is running but is not on '$SHARED_NETWORK' …"
```

so a daemon that cannot answer is reported as a broker **on the wrong network** — in the very function
that grew three separate messages two lines earlier (D66 §7) precisely to stop refusals misdiagnosing
themselves. **Nothing is unsafe**: both readings are fatal and both stop the `up`, which is why this is
an item rather than part of D69. What it costs is an operator sent to `hc-infra` to fix a plane that is
fine, which is the exact cost D66's own optional (3) was taken to remove.

The repair is four lines and is already written in `deploy/deploy-dev.sh`'s copy — capture into a
variable with `2>&1`, `(( rc == 0 )) || die "docker could not be asked which networks …"`, then
`printf | grep -Fxq`. Take it verbatim, with the comment, and note that the two copies are **not** a
verbatim-copy family (D69 §7 argues why they cannot be), so the diff must be read by eye.

Two things whoever takes it should keep. The fail-**closed** direction: an empty template still
refuses, because the grep finds nothing. And part 4 of `.github/checks/shared-plane-wiring.sh` stubs
docker with a function that always succeeds, so **no CI mutation can see this either way** — measure it
with a stub that answers `running` and then fails, which is how D69 measured both readings of the dev
copy.

**Closed by D71**, and it was **four instances** rather than one — instances, not object kinds, which
is the third way this family has been counted in three documents (D71 §5 counts kinds: three; the
opening paragraph above counts occasions: three). Each statement now says which. The membership repair is
`deploy-dev.sh`'s four lines verbatim, with the refusal text identical in both copies because that
sentence names a container, a question and docker's own answer and nothing about which stack is asking
(D71 §3) — the `ok` line's deliberate divergence, D69 §5, stands for the opposite reason.

**The item's own suggested measurement is what found the rest.** `DOCKER_HOST=unix:///nonexistent`
cannot reach the membership probe: the **first** line of the function is
`docker network inspect "$SHARED_NETWORK" >/dev/null 2>&1 || die "the shared network … does not
exist"`, the identical fold one docker object earlier, byte-identical in **both** scripts — so an
unreachable daemon is told the network does not exist, about a network that has existed for the
estate's whole life, with `hc-infra` printed as the remedy. Both arms are fixed in both scripts, and
the network arm is matched on docker's **message**, because docker exits 1 and prints `[]` on stdout
for an absent network and for an unanswerable daemon alike (measured, 29.7.2). Editing `deploy-dev.sh`
means **Appendix A was re-embedded**.

**"No CI mutation can see this" was true and is not any more.** Measured at `466e706` rather than
accepted: with D69's fix folded back out of `deploy-dev.sh`, part 4 exits **0**. The stub now takes a
fourth argument naming *which* docker read fails, and part 4 asks **six** states of each copy — on the
network, off it, the membership `inspect` unanswerable, the `network inspect` unanswerable, the network
genuinely absent, and the docker **CLI** answering `not found` about itself. The fifth is the positive
control for the fourth: those two differ only in docker's words, so a repair collapsing them into
"could not be asked" would pass everything else here while destroying NEW-25's own sentence. The sixth
came at **review**, with the match it guards: `not found` alone is a substring an unanswerable daemon
carries (`DOCKER_HOST=ssh://…` to a host with no docker), so the absence branch requires
`Error response from daemon` beside it — NEW-32's own cost, one arm along, and measured to be invisible
to the other five states. The check is **26** assertions where it was 18, and its test **34 ok** where
it was 26 — eight new cases, four per copy, **none** of them about a refusal going missing and all of
them about which cause it names. Case 26 is D69's fix folded back out, so the copy that already had the
repair is guarded too.

**What did not run is the script.** Everything was measured on the shipped function lifted out by
`awk`, as in D66, D67 and D69: D65 and D67 both correctly refuse this worktree, and `deploy-dev.sh`
could not run at all while NEW-31 stood — **which it no longer does, cleared 2026-09-10 and
re-verified 2026-09-17.** So the reason this package measured a lifted function is a fact about the
week it was written in and not a standing constraint: the dev estate is now a genuine first run, and
the refusal paths D66 and D69 could reach only by `awk` are available to be exercised for real. Three folded reads survive in the two files deliberately — a
`warn`, a poll and `running()` — because the rule is *a `die` may not fold; a `warn` and a poll may*
(D71 §5), and "always check the status" would have made a transient flake fatal in the health-wait
loop. **That poll has an edge, stated at review**: its exhaustion *is* a `die`, so a daemon dying
mid-`up` costs six minutes and then an absence claim, and what saves the diagnosis is the
`compose logs --tail=40` beside it — measured at `rc=1` with docker's own connection error. A poll may
fold; a poll whose exhaustion is fatal needs something beside it that cannot.

---

## NEW-33 — `deploy-prod.sh` folds four outcomes into "the network does not exist on the host" · DONE (D75)

Opened by **D71 §6** as an explicit loser, and it is the **sixth** instance of the reading D68 fix 2,
D69 §10 and D71 have each closed one copy of. `deploy/deploy-prod.sh`'s host-network preflight is

```
ssh -o BatchMode=yes "$HOST" "docker network inspect $net >/dev/null 2>&1" \
  || die "the '$net' network does not exist on $HOST. $net_hint …"
```

and `$net_hint` tells the reader to go and create it, or to start the owning stack. Four outcomes reach
that one message: ssh unreachable, ssh refused (`BatchMode` and a key that is not there), docker
unanswerable **on the host**, and the network genuinely absent. Only the last is what the message says,
and it fires for all three of the others across a network — where the first two are the likeliest.

**Not fixed with D71 for three reasons, each of which is why this is an item rather than a line.**
Production is off limits by instruction. **Nothing in that path has ever run against a host**
(`deploy/prod-server/README.md`), so a repair there is unmeasurable in the way D71's was — the
distinguishing messages would be invented rather than read off a daemon. And the taxonomy is genuinely
different: `ssh` and `docker` each have their own exit statuses and their own words, `ssh`'s status is
the *remote* command's when it connects at all, and separating those is a decision about what an
operator is told rather than a port of D71's four lines. It runs three times in a loop, so whatever is
decided applies to `infranet`, `hcmarketnet` and `monitoring` at once.

`--dry-run` prints this check and contacts nothing, which is the only exercise available and does not
reach the refusal.

**Closed by D75, and it was FIVE outcomes rather than four.** The fifth is a host with no `docker`
command on it — exit **127**, the shell's own status for a command it cannot find — which nothing had
counted because the item reasoned about ssh and the daemon and not about the CLI between them.

**The item's second reason was wrong, and it is the interesting correction.** "Unmeasurable in the way
D71's was" held only for the *messages a production host's docker prints*; the two **hops** are
buildable here. A user-owned `sshd` on port 22222, three authorized keys carrying different
`environment=` options, and the shipped functions lifted out by `awk` gave a real ssh client, a real
remote shell and a real docker daemon — so the defect was **reproduced** rather than accepted (four
states, one message, verbatim) and each of the five repairs was watched answering through a real ssh.
That is more than D66, D69 or D71 could say and less than a deploy; §7 of D75 says which is which.

**The taxonomy turned on a measurement, not a preference.** ssh exits **255** when it cannot connect
and otherwise exits with the **remote command's** status, so a remote `exit 255` is indistinguishable
from ssh never arriving — the one case that matters, and no status check separates it. The remote
command therefore announces its own status on its own line, and the **presence of that line**
establishes which hop answered: D74's rule, one protocol along. `$net_hint` is printed on the absence
arm alone.

**Three things came with it that the item did not name.** The arm an operator reaches **first** is 130
lines above this one — `cannot reach $HOST over ssh, **or** docker compose v2 is missing there`, an
explicit two-way fold — so fixing only the named line would have shipped a preflight whose first
refusal names two causes and whose fourth names five; that is D71 §2's finding, one script along, and
it is why **all six** attributing remote probes now go through one helper. Two of the other five were
folding facts of their own: `grep` answers **2** for a `secrets.env` it cannot read, reported as *"$v is
not set"*, and the data tier's remote `2>/dev/null | grep -c … || true` could not fail at all, so a
wrong `--path`, a missing compose file, a dead daemon and an unreachable host all read as *"0 of 5
stores running"*. And `rollback`'s `|| true` made an unreachable host indistinguishable from a first
deploy, in the function every **failed** deploy lands in.

**A remote `exit` used to swallow the sentinel** — found by driving the shipped function with one, not
by reading it — so the probe is wrapped in a subshell on the far side. No shipped probe says `exit`;
what the wrap buys is that the seventh cannot reintroduce this silently.

CI sees all of it: `host-probe-attribution.sh`, **32** assertions over five parts, driven by a stub
that **runs** the wrapped script it is handed so the sentinel is the shipped code's; its test at
**23 ok, 0 failed** over twenty-two mutations; and the harness's own control at **18 ok, 5 failed** with
part 2's cause assertions removed. `deploy-prod.sh` is Appendix B, so **Appendix B was re-embedded**.
Opens **NEW-36**.

**Review added parts 3 and 4, and the finding is worth carrying forward as a rule.** The first version
drove **one** of the six call sites behaviourally and covered the other five with a textual "routed
through `host_run`" assertion — which establishes only that they cannot report an *ssh* failure as a
fact about the host, and says nothing about their **remote-status** arms. Reproduced: with the secrets
loop's `*)` arm emptied — one line, it parses — `grep`'s exit 2 matches an empty branch, the loop walks
all twelve values, and **preflight passes on a secrets.env it could not read**. That is the one mutant
in this whole family whose result is a pass rather than a wrong message, so *routing a probe through
the right helper is not the same as guarding what it does with the answer*. Both blocks are lifted and
driven against real fixtures now, including a **directory** as the unreadable file (`test -s` 0, `grep`
2) so the state does not depend on `chmod`, which root ignores.

---

## NEW-34 — catalog's `/internal/**` chain declares a precedence Spring cannot read · DONE (D77)

Found while building D74's reactive equivalent, and it was WP-13's own review finding in a second
service — **and in a third, which this item did not know about.**

**Both of the item's questions are answered by measurement, and both answers are yes.** The annotation
is **not read** in a servlet chain: inverting catalog's class-level `@Order` to `LOWEST_PRECEDENCE`
moved nothing at all on the running container, while the same annotation on a `@Bean` method both reads
back through `findAnnotationOnBean` and reorders `FilterChainProxy`. And the ordering **matters
totally**, because the generated `SecurityConfiguration` declares no `securityMatcher` at all — its
`/api/**`, `/v3/api-docs/**` and `/management/**` entries are `authorizeHttpRequests` rules, which
narrow authorization and not the chain — so it matches `any request` and claims both hand-written
prefixes as well. The two hand-written chains are disjoint from each other and neither is disjoint from
it. What was holding the order up was the alphabet, exactly as this item said.

**It was three files, not one.** This item and CLAUDE.md both called catalog's the "one place" the
gateway's correction had not reached. A derived sweep found catalog's *two* chains and booking's
`PaymentWebhookSecurityConfiguration`, all three with the annotation on the class and two of them
carrying the same false paragraph about what the generated chain matches. A list trusted rather than
derived — NEW-15's root cause, recurring inside the description of itself.

**One thing the item did not anticipate: a mis-ordering is loud, not silent.** Servlet Spring Security
refuses to build the proxy — `WebSecurityFilterChainValidator` throws
`UnreachableFilterChainException` and the context does not start, measured at 109 failed contexts. That
does not make the annotation pointless, which is D77 §4's argument: a loud failure beats a silent one
and no failure beats both, and booking's obvious rename (`WebhookSecurityConfiguration`) sorts *after*
`SecurityConfiguration`.

**And the finding that mattered more than the line: the shared comment stripper could not see its own
subject.** D77's new CI check passed on the estate it was written for, reporting `ok` for eight files
and never mentioning the three it existed to guard, because `strip-comments.awk` read `/**` inside a
path pattern as a block-comment opener and truncated **14 of 535 main-source files** from that string
to end of file — every service's `SecurityConfiguration` and `WebConfigurer` among them — while **22
more lost 67 lines cut mid-line** by an unconditional `//` strip. Its own header said "nothing in the
estate has one" and that the failure would be "fail-CLOSED"; both were false, and for the three
estate-wide bans the direction was fail-**open**. The ninth fail-open in that family and the first
inside the mechanism the other eight were fixed with.

**Every figure here names its tree and its measure**, because D77's first draft did not: it reported a
main+test line count (82/50) against a main-only file count, which is this repository's own recurring
defect inside the entry describing it. Main sources: 36 files differ over 444 lines. See D77 §7.

Shipped: `@Order` on the `@Bean` method in all three classes; the two false paragraphs corrected in
place; `FilterChainPrecedenceIT` in catalog and booking, asking the container rather than the source;
a string-, char- and text-block-aware stripper with six new cases in its own test; and a CI check
deriving its service list from `jdl/*.jdl` so that a fifth chain in messaging or payout — which have
never had one and have no test written to be red about one — is caught the day it exists.

---

## NEW-35 — `MarketplaceEventFanoutIT` asserts an exact list over a shared sink, and the neighbour's events land in it · DONE (D76)

Found during D74's review pass: one gateway `clean verify` out of three went red on
`MarketplaceEventFanoutIT.recipientsComeFromThePayload`, and the same suite passed either side of it.
**Nothing in D74 touches the fan-out** — a reactive sink fed by a `@KafkaListener`, against an HTTP
resource and a token contract — and the failure names its own cause:

```
Expecting actual:
  ["kojo.customer", "akosua.mensah", "ama.other", "kwame.trainer"]
to contain exactly in any order:
  ["ama.other", "kwame.trainer"]
but the following elements were unexpected:
  ["kojo.customer", "akosua.mensah"]
```

**The paragraph that stood here was wrong about the cause, and this entry keeps it visible rather than
overwriting it**, because being wrong in this particular way is what the item turned out to be about. It
said `kojo.customer` and `akosua.mensah` were *"the previous test method's payloads, published by
`theStreamIsFilteredToTheSubject`"*, and that *"the two surplus values could only have come from a
neighbour"* — stronger evidence, it argued, than a re-run.

They could not have come from that method. **`recipientsComeFromThePayload` runs FIRST in the class** —
measured off the failsafe XML in four separate runs — so nothing later in the file precedes it. The pair
came from **`MarketplaceStreamFramingIT`**, another class, which runs earlier in the same JVM against the
same static broker and publishes `b-onwire-1` with exactly those two logins. The fan-out class is a
different Spring context, so its `@KafkaListener` joins a fresh `${random.uuid}` group, and
`SseKafkaTestContainer` sets `auto-offset-reset: earliest` — so it **replays the whole topic at startup**
into whichever method happens to be subscribed. Measured end to end with a probe that gave the running
listener a new group id: an already-consumed record's two recipients, back on the sink, with nothing
published. The reason the misattribution was available at all is that `kojo.customer` beside
`akosua.mensah` was published by **three** publications in **two** classes, so the pair named no
publisher.

The shape was still the estate's own recurring one, in a test rather than in production code: each method
subscribed to `fanout.stream()` — **one sink, shared by every subscriber in the JVM** — waited on
`until(() -> received.size() >= 2)`, and asserted `containsExactlyInAnyOrder` over the whole list. The
await is a floor that cannot say *whose* two arrived, and the assertion is exact over a broadcast that was
never this test's.

**Fixed by scoping, not by isolating** (D76). Each method mints an `aggregateRef` nothing else in the JVM
uses and asserts only over the events carrying it; the wait is for a **barrier** event published after the
one under test on the same single-partition topic, so the exactness is sound rather than racing the
emission it exists to catch; and no login or reference is shared between publications any more, so the
next leak names its own publisher. `@DirtiesContext` was refused on measurement — a new context is a new
group, so per-method isolation converts a 25% chance of one replay into a certainty of three.

**It also found a green that proved nothing, which was the worse half.**
`aFilteredStreamCarriesOnlyItsOwnersEvents` never failed from a leak — its assertions were `containsOnly`,
`hasSize(1)` and `contains`, the relaxed forms — and **it passes green with its own two publishes
deleted**, satisfied entirely by the two leaks the suite really produces. Measured. That is the edit the
package was warned not to make, already made, in the one method whose subject is the disclosure boundary.
It now asserts per reference against its own two events and is red under the same probe.

Reproduced 1 red in 4 baseline full runs, byte-identical to the output above; **9 full `clean verify`
runs green** after the fix, with the load-bearing evidence being a forced adversary (the same foreign
emissions redden the old file and leave the new one green) and five mutations of `MarketplaceEventFanout`
that are each red. Opens **NEW-37**.

**Reviewed 2026-09-10: approved, no blocking findings**, with every load-bearing claim reproduced
rather than accepted — including the adversary re-run with the foreign events on the *same topic*,
published between the event under test and its barrier so they are guaranteed present at assertion
time, still **3 green**. One should-fix, docs-only: D76 §7 attributed NEW-37's risk to listener
concurrency breaking the barrier's ordering, and it does not — only a partition-count change does.
Corrected in D76 §7, argued in D76 §9, and NEW-37 below is rewritten around the real mechanism. Both
optionals taken.

---

## NEW-36 — the health gate's exhaustion is fatal and rests on 24 folded reads · DONE (D78)

Opened by **D75 §6** as an explicit loser, and it is D71 §5's stated poll edge in the one script where
nothing stands beside it.

`deploy-prod.sh`'s `health_gate` probes each service over `docker compose exec … bash /dev/tcp` with
`>/dev/null 2>&1 || bad+=" $s"`, 24 times at ten-second intervals. **That folding is correct for each
poll** — during a wait an unanswerable daemon or an unreachable host should be a retry, and a status
check inside the loop would make a one-second flake fatal on the estate's slowest gate. What is not
correct is where it ends: `warn "still unhealthy:$bad"; return 1`, which falls through to `rollback`.
So a host that goes away mid-deploy costs four minutes and then names **five healthy services as
unhealthy**, and rolls the stack back — or tries to, and then dies in `rollback` for the real reason.

D71 §5's rule in full is *a poll may fold, and a poll whose exhaustion is fatal needs something beside
it that cannot*. In `deploy-dev.sh` that something already exists: a `compose logs --tail=40` on the
same line as the `die`, which fails loudly against a dead daemon (measured, D71 §9). **There is no
equivalent here**, and the two `/management/info` probes in `smoke_test` are the same shape one step
along — they `warn` rather than `die`, which D71 §5 permits, but the brokerage one `return 1`s into the
same rollback. Its message already names both readings ("holds NO brokerage terms in force, **or**
could not be asked") with a remedy paragraph covering both, which is why it is not a defect today.

The fix is a decision rather than a line, which is why this is an item. Three shapes, none costed:
one attributing probe **after** the loop exhausts — `host_run` is now in the file and would name the
hop — so the refusal distinguishes "they never became ready" from "we stopped being able to ask"; a
`compose logs --tail=40` beside the timeout, copying dev's answer, which diagnoses without deciding;
or a bounded consecutive-unreachable count that gives up early on the hop rather than late on the
services. The first is the one that makes the *message* right, which is what this family is about.

**Take the eleven deploy-phase `ssh` invocations with it.** None carries `BatchMode` or
`ConnectTimeout` — D75 put `SSH_OPTS` on the six *preflight* probes and deliberately did not touch the
rest, whose failures are printed by `run` or by the ERR trap. But a host that vanishes between
preflight and `up` hangs them on the TCP defaults, which is the same "the refusal never arrives"
problem one phase along, and `health_gate`'s own probe is among them. Pre-existing, outside D75's
claim, and cheapest to settle in whichever shape this item takes.

Nothing is unsafe: a rollback of a healthy stack is the cost, and on a first deploy it ends in
*"no previous deployment recorded"* — which, since D75, is at least no longer what an unreachable host
is told.

**Closed by D78, and the cheap repair the item allowed for would not have worked.** Measured on
throwaway containers (docker 29.8.0): `compose exec` exits **1** for a port that refuses, **1** for a
service that is not running, **1** for a service the compose file does not declare and **1** for a
daemon that cannot be asked — four states, one status, with ssh's own 255 on top. So the status the
loop discards could not have attributed anything even if it were read, and the attribution had to come
from a **different question**. `compose ps -a` is that question and its status is honest: 0 with a line
per container, 1 carrying docker's own sentence, and **0 with nothing at all** for a project that has
no containers — a fourth state the item did not anticipate and no status check can see.

**The shape taken is the first of the three, with the second demoted to evidence.** One attributing
probe after the loop, through `host_run`, and six outcomes each named by what came back: ssh never
reached a shell, no docker CLI (127), the daemon could not be asked, no containers at all, docker
itself calls every unready service healthy, or the host answered and agrees. **Only the last rolls
back.** `deploy-dev.sh`'s `compose logs --tail=40` is ported onto that one arm — not as the diagnosis
but because `up -d` at the previous tag **recreates the containers**, so the failed tag's log is
readable in that window and no other — and its own failure is a `warn`, since unreadiness is already
established by then.

**The blip is the case the item was really about, and `{{.Health}}` is what decides it.** A host away
for the *last poll only* puts every service in the bad list while four were ready throughout. Docker's
healthcheck is the same `/dev/tcp` readiness request, run by the daemon **inside** the host every 15s,
so it answers without crossing the hop the polls cross: if every service the gate gave up on reports
`running healthy`, what failed is this end of the wire, and the refusal says so and reverts nothing.
Every service must be contradicted for that arm to fire; a blank health column is not a contradiction.

**Where the cause cannot be established it refuses rather than reverts**, and the reason is
mechanical rather than a preference: `rollback` needs the same host at four points — a `host_run` for
the previous tag, an `ssh` to restore `.env` and roll, and `health_gate` again — so an estate that
cannot be asked cannot be reverted either. The cost is a deploy that goes un-recorded in
`deployments.log` until somebody re-runs it, against a production estate reverted over a link that
blinked.

**The eleven bare `ssh` invocations came with it**, as the item said they should, and one of the item's
own figures was wrong by two orders of magnitude in the process: a host that goes away costs "four
minutes" only while ssh fails fast. Measured against a blackholed address, an unbounded ssh connect
takes **136s** and `ConnectTimeout=8` takes **8s** — so 24 iterations × 5 services was **~4.5 hours**
to a refusal naming the wrong cause. Every invocation carries `SSH_OPTS` now — **12 `ssh` and 1 `scp`
in command position** over the stripped, continuation-joined file, one of the ssh being `host_run`'s, so
eleven were this package's. **Name that measure whenever quoting it**: anchored at the start of a line
the same file answers 7 and 1, because five are written inside `$( )`, after a pipe or after `if`, and
review read the first number as wrong against the second. Both are right; D78 §7 enumerates all thirteen
by line with a per-line control, and D78 §13 is the correction.

**`smoke_test`'s two probes are deliberately left folded** (D78 §8), which the item permitted and asked
to have said rather than omitted. Its message already names both readings with a remedy covering both,
and the direction to fail is the **opposite** one: the condition it guards is D57's and it is silent, so
an unestablished answer must not ship — which is what a rollback does. Both arguments are written at
the site.

CI sees it as **part 6** of `host-probe-attribution.sh` (**45** assertions, was 32), which drives the
shipped gate against the stub across ten states — including a **transient that must still PASS**,
because the cheap reading of this item is a status check inside the loop and that makes a one-second
flake a rolled-back deploy. Its test carries **thirty-three** mutations (`34 ok, 0 failed`), and the two
harness controls report **28 ok, 6 failed** with part 6 removed and **31 ok, 3 failed** with the
`SSH_OPTS` guard removed, each through its own door. `deploy-prod.sh` is Appendix B, so **Appendix B was
re-embedded**. Opens **NEW-39**, and **NEW-41** at review.

**Review found one blocking gap and it was the half taken beyond the item** (D78 §13): `SSH_OPTS` was
lifted by the check and asserted by nothing, so emptying the array, dropping its `ConnectTimeout` or
renaming it all exited **0** — measured, three ways, with the stub indifferent to how many options an
`ssh` is handed. The timeout is what the 136s/8s measurement is *about*, and part 5's success line
already claimed to cover it: *"SSH_OPTS is the one place BatchMode and the timeout are set"*, printed by
an assertion that only bans the inline spelling. **A matcher whose reach falls short of its own name**,
inside the package closing an instance of that. Fixed on the `HOST_SENTINEL` guard's own precedent —
the value, not the declaration — with both options required separately, and the reviewer's three states
carried as cases 31-33.

**A second review found four should-fix and no blocking state** (D78 §14), and the first of them is
this family arriving *inside* §13's repair: the new guard read the **raw** line, so
`SSH_OPTS=(-o BatchMode=yes) # keep -o "ConnectTimeout=..." off` printed *"carries both BatchMode and a
ConnectTimeout"* for an array carrying neither. Measured. Both lines are lifted from
`strip-sh-comments.awk`'s output now, and the asymmetry is written at the guard: `HOST_SENTINEL`
survives a raw grep only because a blank sentinel breaks behaviour parts 1-2 *drive*, while
`SSH_OPTS`' absence is invisible to the stub, so its text matcher is the sole defence.

The second is the same shape in the shipped script: `health_gate` has **two** callers, and from
`rollback` a revert has already been applied — so *"NOTHING HAS BEEN ROLLED BACK, deliberately"*, with
`--rollback` as the remedy, was false in the one function every failed deploy reaches. **The phase is a
parameter now, with no default**, composed once per caller and interpolated into every refusal; a
caller that omits it is refused before a probe is sent. Third: three stale "thirty"s, including a CI
**step name**, replaced by a derived count the run prints. Fourth: §7's thirteen **line numbers** were
stale at the commit that shipped them, so the record is what each invocation *is* and the number is
printed by the check.

Five notes folded in: `HC_SSH_TIMEOUT=0` reinstated the unbounded connect (measured at 25s against
8.1s) and is refused at declaration time; the blip arm's premise — the readiness healthcheck in
`docker-compose.prod.yml` — is asserted by part 6 with `HC_PROD_COMPOSE` making it drivable, because
dropping it makes every blip an established-unready rollback with all assertions green; the docker stub
renders `--format` **in order**, so a reordered format is red rather than silently dead; the warn arm no
longer claims more than a blank health column supports; and §8's counterfactual is corrected to the
direction-to-fail argument that was always carrying it. That round ended at **49** assertions,
**37 ok / 0 failed** over 36 numbered mutations, and **three** harness controls (29/8, 33/4, 36/1).

**A THIRD review followed, and it fired the cycle rule: three rounds of findings in one area means the
decision was wrong rather than the code** (D78 §15). Every round's fail-open was in the guard added to
close the previous one, and the pattern is worth more than the fixes — **each guard was exact about the
text it had just been burned by and silent about the binding one step away**. Blocking instance:
swapping `health_gate rollback` for `health_gate deploy` in `rollback()` parses and left the check AND
its test at exit **0**, restoring the false *"NOTHING HAS BEEN ROLLED BACK"* verbatim, because the
no-default guard sees only an absent argument and part 6 drives the function with call strings the
harness itself writes. Repaired with two call-site assertions (cases 37, 38) plus three more: the
`SSH_OPTS` guard pins the **expression** `ConnectTimeout=${HC_SSH_TIMEOUT:-8}` and refuses a second
assignment (39, 40) — a `:-0` default had printed "bounded" for an array that hands ssh an unbounded
connect — the compose premise strips YAML comments in its own window and refuses `disable: true`
(41, 42), and `$left` no longer makes **arm** claims, which removes rather than tests four undriven
phase × arm pairs. **NEW-42 is the structural close**, and this was the last textual round: **51**
assertions, **43 ok / 0 failed** over 42 mutations, **four** controls (33/10, 37/6, 42/1, 41/2), each
verifying as a set.

---

## NEW-37 — the fan-out test's barrier rests on a framework default nothing in this repository sets · DONE (D79)

Opened by **D76 §7** as a stated premise rather than a defect. `MarketplaceEventFanoutIT`'s three methods
wait for a **barrier** event published after the one under test on the same topic, and take that arrival
as proof that every emission the earlier record was going to make has already been recorded. That holds
on three premises, and only two of them are written down anywhere in this repository:

- **one partition per topic** — `SseKafkaTestContainer` creates all six as `new NewTopic(t, 1, 1)`, so it
  is in the file;
- **synchronous emission** — `Sinks.Many.tryEmitNext` delivers on the emitting thread, which is the
  `directBestEffort` contract and the reason `MarketplaceEventFanout` can never block a listener thread;
- **listener concurrency 1** — which is **measured** (no `#0-1-C-*` consumer thread appears anywhere in a
  full run) and **configured nowhere**. `spring.kafka.listener.concurrency` is absent from every yml in
  the gateway, and the `@KafkaListener` sets no `concurrency` attribute, so what holds is Spring Kafka's
  default.
  **That parenthesis is withdrawn, and it is the whole of what D79 found** (D79 §2). The thread-name grep
  cannot see a second child container at all: `gateway/src/test/resources/logback.xml` puts
  `org.springframework` at WARN, so `KafkaMessageListenerContainer` logs nothing at INFO —
  *"partitions assigned"* appears **0** times in a full run — and a consumer thread that exists produces
  no line to match. Measured: with `spring.kafka.listener.concurrency=2` set and **two** children
  demonstrably running, the same grep still answers only `#0-0-C-1`, twelve times, **every one of them
  from a different JVM** (an earlier test class's `NetworkClient` warnings). So the premise was
  *unmeasured*, not merely unconfigured, and the instrument that made it look benign could not have said
  otherwise. The instrument that works is the `KafkaListenerEndpointRegistry`, which needs no log:
  `concurrency=1 children=1`.

**Only the FIRST of those three is what carries the ordering, and D76 §7 attributed the risk to the
wrong one** — corrected here at review, because getting a mechanism plausibly wrong is this item's
neighbour's whole story. It said a higher concurrency default *"breaks the produced-order guarantee the
barrier is built on"*, bundled with a second partition. **It does not.** Spring Kafka's concurrency
distributes **partitions** across child containers, and a partition is owned by exactly one consumer at
a time, so per-partition consumption order survives any concurrency setting. The barrier and its event
are on the same topic, therefore the same single partition, therefore the same thread. **Only a change
to the partition count breaks the ordering** — and that count is in `SseKafkaTestContainer`, which is
to say the ordering premise is already this repository's.

What concurrency > 1 actually introduces is a different failure and worth knowing on its own terms:
two container threads consuming **different** topics would call `tryEmitNext` concurrently on
`Sinks.many().multicast().directBestEffort()` (`MarketplaceEventFanout:88`), which is the *serialized*
spec, so it answers **`FAIL_NON_SERIALIZED`** and `onEstateEvent`'s failure arm (`:148`) logs it at
**DEBUG** and drops the emission. In the test that is a missing element or a barrier timeout. **In
production it is a silently dropped live event** — and not the loss the class javadoc accounts for,
which is "drops for a subscriber too slow to keep up". A concurrency raise would add a second loss mode
that nothing in this estate names.

**The direction is still safe, and now for a reason that survives the correction.** A dropped emission
can only make an assertion miss something, never make a negative assertion pass wrongly: the drop
happens at the sink, *before* `streamFor`'s filter, so it reaches the control assertion — which demands
both recipients of both of this method's own events, by reference — before it could ever flatter the
`isEmpty()` beneath it. And the negative assertion is protected by same-partition ordering, which is
exactly what concurrency cannot disturb.

Two shapes, neither costed, and they no longer aim at the same premise:

- set **`spring.kafka.listener.concurrency: 1`** in the gateway's **test** config, with a comment saying
  the sink's serialisation depends on it. One line, and it makes the *emission* premise this
  repository's rather than the framework's. This is the one to reach for.
- assert the **partition count** in the test, or pin it as a named constant in
  `SseKafkaTestContainer` — that is the premise the *ordering* rests on, and the only one whose change
  would break the barrier's logic rather than merely drop a message.

**Do not** assert concurrency from the `KafkaListenerEndpointRegistry`'s container, which is what the
first draft of this item proposed: it would pin a value that is not load-bearing for ordering, and read
as though it were.

Nothing is wrong today. One premise is the framework's; the other two are already ours.

**Closed by D79, and BOTH shapes were taken — the first one somewhere else.** The emission premise is
pinned as **`concurrency = "1"` on the `@KafkaListener` itself**, not in the test config this item named:
the harm is a dropped live event in *production*, where a test-config line reaches nothing and would have
left the suite green while the estate lost events, and the annotation is the one spelling that **defeats
the knob** — measured, with `spring.kafka.listener.concurrency=2` set and the annotation present, the
container still runs `concurrency=1 children=1`, because an endpoint's concurrency overrides the
factory's. It is a hand-written file, so nothing joins the regeneration table. The ordering premise is
asserted of the **broker** by `MarketplaceEventFanoutIT.theBarrierRestsOnOnePartitionPerTopic`, against a
literal 1 rather than against the new `PARTITIONS_PER_TOPIC` constant, and watched red twice: the
constant raised to 2, and the topics created with 2 while the constant said 1. Both mutations left the
other three methods green, which is what a silent premise looks like.

The forbidden shape stayed forbidden. What was added beyond the item is a **CI grep** — the opposite of
D76 §6's answer for the same class, because no test can see the pin's deletion (the default is also 1),
which is D74's situation exactly. Its subject is derived from `jdl/*.jdl` rather than named, so a second
fan-out in another service is caught the day it exists; seven states driven, including an unstripped
positive control that passes the deletion.

**Opens nothing.** The item's third premise — synchronous emission — was confirmed in reactor-core
3.8.6's source and measured (561,466 `FAIL_NON_SERIALIZED` in 800,000 concurrent emits, none at one
thread, none at all through `Sinks.unsafe()`), and the `Sinks.unsafe()` hazard it exposed is guarded by
the same check rather than left as an item.

**Reviewed 2026-09-11: approved, no blocking findings** (D79 §10), with every mechanism claim
re-established from the framework sources and the two ITs re-run three times in the polluting order,
4/0 each. One should-fix and two promotions, all taken. The should-fix was this package's own doing:
naming the harness's topic list turned an anonymous inline list into a **claim about main source**
("the six topics the fan-out subscribes to") that nothing checked — two independent lists whose
defaults coincide — so the new method now reads the resolved topic set off the
`KafkaListenerEndpointRegistry`, requires the constant to equal it, and asks the broker about that
set; watched red with one topic removed. The promotions were both measured by the reviewer: the
`Sinks.unsafe(` ban was **evaded by a static import**, and the step exited 0 printing `ok` about a
file that had just left the safe spec — so there is a **positive** `Sinks.many(` assertion now, which
no new spelling of the negative can evade, and the ban is widened to the bare word; and the
empty-subject message named the pre-widening cause, which is the shape D79 §5 criticises in its own
draft. Chasing the first of those produced one more of this family: the added `emitNext`
discriminator **matched neither call**, because `tryEmitNext` has a capital E. `[eE]mitNext` now, with
the case error written into the check.

---

## NEW-38 — the SHELL stripper's caller count is stale, in a file that is not its own · DONE (D82)

Found by D77's review-fix sweep, and deliberately **not** folded into it: different mechanism,
different file, and a package that had just been corrected for widening its own scope should not widen
it again on the way out.

`.github/checks/shared-plane-wiring-test.sh:39` describes state 19 as *"the shell stripper absent — one
file **four** checks trust"*. Measured: `strip-sh-comments.awk` is called by **one** workflow step
(*"verify-outbox-recovery.sh must restore the aliases it severs"*) and **four** check scripts —
`strip-sh-comments-test.sh`, `outbox-alias-restore-test.sh`, `host-probe-attribution.sh` and
`shared-plane-wiring.sh` — so **five** callers, and the sentence is one short whichever of the two
things it means by "checks".

It is a comment and nothing depends on it, so the cost is a reader's confidence rather than a check's
reach. What makes it worth an item is that it is **the same defect D77 §7 and its review are about, on
the other stripper**: a count written into a sentence, gone stale, in a test-of-a-check whose subject is
that mechanism's reliability. D77 fixed its own by *deriving* the number at run time from `build.yml`
and printing it as an assertion, and `strip-comments.awk`'s header carries the one-line `awk` that
produces it. The same treatment fits here, and the shell stripper's callers are **not all in
`build.yml`** — four of the five are check scripts — so the derivation is a `grep -rl` rather than a
copy of D77's expression.

Do it as one line of shell, not as a corrected constant: a number in a comment is what this is.

**Closed by D82, and this item's own count was stale when it was written.** It names five callers as
one workflow step plus four check scripts. Measured on `main` at `4b92d8c`: **five files invoke the
stripper** and they are not that composition — `shared-plane-wiring.sh:152`,
`host-probe-attribution.sh:100`, `outbox-alias-restore-test.sh:40`, **`host-probe-attribution-test.sh:705`**
(the one missed) and its own `strip-sh-comments-test.sh:19`. And `build.yml` **invokes it nowhere**: its
single mention is a comment, in the step whose subject reaches the stripper through
`outbox-alias-restore-test.sh`. So "one workflow step" describes a dependency rather than a call.

A third statement existed at the same time — `host-probe-attribution-test.sh` said *"one file five checks
trust"*. **Three sentences about one number, three different wrong answers.**

The fix is a derivation that **prints** the list enumerated and asserts only a **floor of two**, never an
expected count: a new caller is not a defect, and a check asserting `== 5` would go red on a correct
change and then be "fixed" by editing the number — this defect through its own guard. Both stale
sentences now say "one file every shell matcher in the estate trusts" and point at the case that derives
it; `strip-sh-comments.awk`'s header carries both expressions and no total.

---

## NEW-39 — `HEALTH_TIMEOUT` is a budget of 24 attempts and the header calls it seconds · DONE (D81)

Opened by **D78 §10** as an explicit loser, and it is the residual of the lateness that decision
measured rather than a new reading.

`deploy-prod.sh` declares `HEALTH_TIMEOUT=240`, prints `Health gate (240s)`, and then counts
`waited += 10` **per iteration** regardless of how long the iteration took. The probes themselves are
unbounded in that arithmetic, so the gate's real duration is `24 × (probe time + 10s)` and the number
in the banner is a lower bound rather than a limit.

**Measured in D78 §1**, and this is what makes it worth an item: an ssh connect to a blackholed address
takes **136s** unbounded and **8s** with `ConnectTimeout=8`. Before D78 put `SSH_OPTS` on the poll, a
host that dropped packets mid-deploy therefore took `24 × 5 × 136s` — about **4.5 hours** — to reach a
refusal that then named the wrong cause. With the timeout it is about **20 minutes** to a refusal that
is now correct. Thirteen times better and still not 240 seconds.

Two shapes, neither costed:

One more fact for whoever takes it, from D78 §14's review and re-checked at §15: **`sleep 10` runs on
the final iteration too**, before the exhaustion check, so the real budget is `24 × (probes + 10s)`
with one sleep spent on nothing at all — ten seconds added to every failing gate for no probe.

- **bound the loop by a wall clock** (`SECONDS` at entry) so the banner and the behaviour agree. It is
  one line and it is **not free**: on a healthy estate an iteration is roughly `5 × probe + 10s`, so a
  240-second wall clock is fewer attempts than 24 and a slow-starting estate could fail a gate it
  previously passed. Note `start_period: 120s` and `retries: 20` in the compose healthcheck — docker's
  own patience for the same question is 300s, which is the number to argue against;
- **keep the attempt budget and rename it** — `HEALTH_ATTEMPTS=24`, with the banner saying attempts.
  Cheapest, changes no behaviour, and makes the header true.

Nothing is unsafe either way: since D78 the late refusal names the right cause and reverts nothing it
cannot establish. What is left is an operator waiting twenty minutes for a message about a link that
went down four minutes in.

**Closed by D81 with BOTH shapes, and the second one is the half that answers the sentence above.**
`HEALTH_ATTEMPTS=24` is the count the loop always enforced, renamed so the header is true — that is the
documentation defect. `HEALTH_DEADLINE=600` is the behaviour change, and it exists for the operator:
the gate stops at whichever bound comes first and **the refusal names which fired**, because attempts
spent is a statement about readiness while a ceiling hit with attempts unspent is a statement about the
link.

**600 is above docker's own patience, and this item's own number was wrong.** The compose healthcheck is
`start_period: 120s` with `retries: 20` at `interval: 15s`, so the daemon waits up to **420s** — not the
300s written above, which is the retry window without the start period. A ceiling below that would let
this gate overrule a verdict docker had not reached. Above it, the deadline can only fire when the
*probes* are slow, so the feared regression — a slow-starting estate failing a gate it used to pass — is
avoided by the value: a healthy estate spends ~15s a round and exhausts its attempts at ~360s.

Two smaller things went with it. The final `sleep 10` is gone, because both bounds are now tested
**before** the sleep rather than after it. And the bound is a **parameter with no default**, like
`phase` beside it: `$spent` is interpolated into all four refusals, so a default would make the reason
go *missing* rather than come out wrong. Cases **51** and **52** drive the ceiling arm and the
bound-less call; both were red through the wrong door first and are recorded in D81 §7.

---

## NEW-43 — a dashboard of gateway registrations and logins, on an estate that transports no application metric · PARTLY DONE

**Asked for by the architect on 2026-09-11**: a dashboard monitoring the gateway for **registrations
aggregated by (activated | not-activated)** and **logins aggregated by (success | failed)**.
**Priority: take it after NEW-39.**

The screen is a day's work. What makes this an item rather than a ticket is that **three of its four
layers do not exist, and the missing transport is missing by decision** — so building the panels first
would produce a dashboard that renders zeros for ever and looks like a broken query.

### What exists, measured

| Layer | State |
| --- | --- |
| `micrometer-registry-prometheus` on the gateway | **present** (`gateway/pom.xml:276`) |
| `prometheus` in `management.endpoints.web.exposure.include` | **present** (`config/application.yml:52`) |
| `SecurityMetersService` counters | **present**, and they are **not logins** — four counters over `security.authentication.invalid-tokens` tagged `cause=invalid-signature|expired|unsupported|malformed`, incremented from `SecurityJwtConfiguration`'s decoder. That is *token validation on a later request*, so a wrong password increments nothing |
| a registration counter | **absent** |
| a login success/failure counter | **absent** |
| an OTLP **metrics** registry (`micrometer-registry-otlp`) | **absent** — Micrometer meters therefore have no push path of their own |
| anything scraping `/management/prometheus` | **absent, and deliberately so — see below** |
| a Grafana dashboard of ours | **absent.** The five `<service>/src/main/docker/grafana/provisioning/dashboards/JVM.json` files are JHipster's, and `src/main/docker` is generated |

### The transport is closed on purpose, in both environments

This is the part to settle before any panel is drawn:

- **quality** — `quality/host-site.conf:116` is `location = /management/prometheus { return 404; }`. The
  endpoint is blocked at the edge.
- **production** — `deploy/docker/docker-compose.prod.yml:86` records that the collector *"deliberately
  has NO application scrape targets"*, which is the estate-wide rule in the parent `CLAUDE.md`:
  telemetry is **pushed** (OTLP), never scraped.
- **the push path is off by default** — D63/D64/D73. The OpenTelemetry agent is baked into all five
  images and attached in **no** environment by default; `HC_OTEL_JAVA_OPTS` turns it on, and D73 §3
  decided the default stays empty because `monitoring-quality` belongs to another repository.

So there are exactly two honest routes and picking one is a decision:

1. **Push, consistent with the estate.** Establish whether the OTel Java agent's Micrometer
   instrumentation actually bridges these meters to OTLP **on this agent version and this JDK** — do not
   assume it; D63 exists because "the agent is present" was mistaken for "the agent instruments", and
   `./deploy/verify-otel-agent.sh` is the shape of the answer. If it does not bridge, the question becomes
   whether to add `micrometer-registry-otlp` (a new dependency in five poms' worth of precedent) or to
   emit through the OTel API directly.
2. **Reverse a deliberate 404.** Cheaper and it contradicts a written decision in two files plus the
   parent guide. If this is chosen it needs its own argument, and the argument has to cover why hc-market
   scrapes when nothing else in the estate does.

### Four things the requested aggregation itself has to settle

**1. One of the two is a gauge and the other is a counter, and treating them alike gives two panels that
are wrong in different ways.** "Logins by success/failed" is an **event stream** — counters, monotonic,
nothing to look up later. "Registrations by activated/not-activated" is a **state of the collection**:
activation happens *after* registration (`UserService:121` writes `setActivated(false)` on register,
`UserService:52` flips it on `activateAccount`), so a counter incremented at registration can never move
when the user later activates, and you would be mutating a past bucket. It must be **derived at
observation time** — a gauge over the user collection — which is also this repository's central rule,
*derived, never stored*.

**2. "Failed" hides the one thing that links the two panels.** `DomainUserDetailsService:52` throws
`UserNotActivatedException`, distinct from bad credentials. A registered-but-never-activated user trying
to log in is *the* failure the registration panel exists to explain, and a single `failed` bucket erases
it. **At least three buckets**: bad credentials, not activated, and everything else. The parent rule
applies — a battery that fires as one number cannot tell you which door opened.

**3. No login, email or alias may appear in a metric label.** Two reasons and both are load-bearing here:
Micrometer tags with per-user values are unbounded cardinality, and a login in a label is a **disclosure
surface that survives erasure** (D31/D35/D38/D39 — nothing re-keys a metric already scraped or pushed,
and the erasure sweep does not visit a metrics backend). Aggregate counts only.

**4. Say which mode each figure is true in.** Quality runs `dev,test` and **seeds `admin` and `user`
with passwords derived from their logins by a rule published in this public repository**, so a
registration/login dashboard there counts seeded accounts and `verify-cycle.sh` traffic, not real use.
This is the prototype's *"Sessions brokered"* defect one surface along (D46): a plausible number that
looks like it works. **Adding a figure means saying which mode it is true in.**

### Where the file can live, and where it cannot

Not `<service>/src/main/docker/grafana/provisioning/dashboards/` — that is generated, so
`jhipster jdl --force` discards it and the regeneration table would need a row. `deploy/observability/`
is the precedent: it holds `hc-market-rules.yaml` today.

**And that directory has a live tripwire.** `hc-market-rules.yaml` carries a `NOT-YET-ATTACHED` marker
(line 51) which `.github/checks/observability-claims.sh` holds against what the compose files render, **in
both directions**. A dashboard asserting that these metrics arrive is a claim of the same kind the marker
exists to police, so this item must either satisfy that check or extend it — and two of the five existing
alerts are `absent()` queries (`HcMarketGatewayDown` and `HcMarketServiceDown`, enumerated rather than
counted: a raw grep for `absent(` answers **7**, being five occurrences inside those two alerts plus two
in prose). They fire continuously against a tenant that has never reported. Do not install a panel set
whose premise is untrue, for the same reason those rules were never installed.

### Done means

A dashboard whose panels are non-zero on an estate that is actually generating registrations and logins,
with the transport decision argued, the gauge/counter split implemented as such, at least three login
outcome buckets, no per-user label anywhere, and each figure labelled with the mode it is true in. If the
transport decision lands on "push", the agent's Micrometer bridge must be **verified instrumenting**, not
merely loading.

**`PARTLY DONE` by D84, and the split is deliberate rather than a shortfall.** Five of the six things
"done means" asks for are built and verified; the sixth cannot be satisfied by this repository at all.

Built and measured:

- **the gauge/counter split**, implemented as such — `gateway.identity.accounts` is a pair of gauges over
  the collection, `gateway.identity.logins` a set of counters;
- **four login outcome buckets**, not three — and the one that mattered is verified against the *running
  container*, because a mock could not answer it: `UserNotActivatedException` **does** survive Spring's
  authentication manager to be counted separately, where an unknown login does not;
- **no per-user label anywhere**, asserted over the registry's own meters;
- **each figure labelled with the mode it is true in**, as a text panel on the dashboard rather than a
  footnote;
- **the transport decision argued** — and argued to a *measurement nobody has taken*, which is NEW-44.

Not satisfiable here: *"panels non-zero on an estate that is actually generating registrations and
logins"*. Production has never been deployed and quality seeds its accounts, so **no estate in this
repository can make these panels non-zero with real data**, whatever the transport does. The dashboard
carries a `NOT-YET-TRANSPORTED` marker held against that fact by `observability-claims.sh` part 5.

The item's framing was right about the shape of the work and wrong about one number: it says
`build.yml` has one step that reaches the shell stripper — unrelated — but more relevantly it assumed
the four decisions were all downstream of transport. Three of them are not, which is why this shipped
rather than waiting.

---

## NEW-44 — nothing carries `gateway_identity_*` anywhere, and which route it should take is unmeasured · DONE (D85)

Opened by **D84 §7**, which decided everything about the gateway identity dashboard except where its
series go. The metrics are emitted and verified in-process; **no environment in this repository collects
them.**

**The estate's posture is push, and the push is off.** `/management/prometheus` is deliberately 404'd at
quality's edge (`quality/host-site.conf:116`) and unscraped in production
(`deploy/docker/docker-compose.prod.yml:86` — the collector *"deliberately has NO application scrape
targets"*), and the OpenTelemetry agent that would push is attached in no environment by default
(D73 §3, because `monitoring-quality` belongs to another repository).

**The measurement nobody has taken, and it is the whole item:** does the OpenTelemetry Java agent's
Micrometer instrumentation actually bridge these meters to OTLP, **on this agent version and this JDK**?
D63 exists precisely because "the agent is present" was mistaken for "the agent instruments", and
`./deploy/verify-otel-agent.sh` is the shape of an honest answer — it reports **loading and instrumenting
separately** for that reason. Do not assume the bridge works because the module exists.

Three routes, and the first two are only distinguishable after that measurement:

- **the agent bridges Micrometer** — then the transport already exists and the work is one variable
  (`HC_OTEL_JAVA_OPTS`) plus removing the dashboard's `NOT-YET-TRANSPORTED` marker, which
  `observability-claims.sh` part 5 will *require* once an environment attaches the agent;
- **it does not** — then either `micrometer-registry-otlp` goes into the gateway's pom, which is a
  **generated file** and therefore a regeneration-table row (the OpenTelemetry block in it is already
  hand-written for exactly this reason), or the two meters are emitted through the OTel API directly;
- **reverse the 404 and scrape** — cheapest, and it contradicts a written decision in two files plus the
  parent guide. It needs its own argument for why hc-market scrapes when nothing else in the estate does,
  and note it would be a control on production and no control at all on the two estates that publish the
  gateway's port.

**What this item cannot deliver whichever route wins**: panels with real data. Production has never been
deployed and quality seeds its accounts, so the figures stay demo figures until there is an estate with
users — which is why D84 shipped the dashboard marked rather than waiting for this.

**Closed by D85. Route one is open, and the measurement found that we were not on it.**

`deploy/verify-micrometer-otlp-bridge.sh` runs one probe twice, differing only in
`OTEL_INSTRUMENTATION_MICROMETER_ENABLED`. **Measured**: off → 0 registries attached to
`Metrics.globalRegistry` and 0 Micrometer meters exported; on → 1 registry, counter and gauge each
exported 7 times; `jvm.*` control 30 in both, which is what makes a zero mean anything. **So the bridge
works and is opt-in.** The script's own first version tested only the default and reported "the bridge
does not carry Micrometer" — a default recorded as a capability.

**And the application half was wrong.** The bridge adds its registry as a *child* of the global
composite, and a composite forwards only what is registered **through** it — it does not index what its
children register on themselves. D84 constructed the meters with the injected `MeterRegistry`, which is
the `PrometheusMeterRegistry`, a child. So **the series would never have arrived however the transport
was wired**, and every panel would have read "No data" with no config file showing why. Fixed by binding
the meters to `Metrics.globalRegistry`, with `MicrometerReachesTheGlobalRegistryIT` asserting both
directions — through the composite *and* still on the application's registry, since a fix that reached
the agent and lost `/management/prometheus` is the same defect reversed.

**`HC_OTEL_JAVA_OPTS` alone would still have carried nothing**, so the flag is set in all three compose
files beside the other inert `OTEL_*` variables, and `observability-claims.sh` part 6 refuses its absence
and any value other than true — "false" and "missing" are one line apart and identically silent.

**The dashboard's `NOT-YET-TRANSPORTED` marker stays**, and that is not a loose end: nothing attaches the
agent by default, deliberately (D73 §3). What changed is that turning it on is one variable with a known
outcome rather than an unmeasured hope, and part 5 will *demand* the marker come off the day an estate
attaches the agent.

The third option in the list above — reversing the 404 and scraping — is refused rather than deferred;
D85 §6 argues it.

---

## NEW-45 — the online-session v1 D17 recommended, which was never blocked · DONE (D87)

Opened by **D86 part 2**. WP-17 has read as BLOCKED on budget since D17, and D17's **v1 needs no
provider at all**: *"the professional supplies their own meeting link (Meet, Zoom, whatever they already
use); the platform stores it and reveals it an hour before, which is the promise the prototype makes."*
Daily.co is the *upgrade*. So the budget block applies to the upgrade and this half is unblocked.

**What it needs, and why it is an item rather than an afternoon:**

- **a field on `Booking` to store the link.** D17 said so at the time — *"Booking has nowhere to put a
  link"* — and it is still true. That is a JDL change, which means a regenerated entity, a Liquibase
  changelog, and a row in CLAUDE.md's regeneration table. CLAUDE.md's own warning applies: changing a
  JDL entity invalidates its checksum, so a database that ran the old changelog fails with
  `ValidationFailedException` and in dev the answer is to drop the schema;
- **a read-time visibility rule, not a scheduler.** D17 is explicit that there is no scheduler anywhere
  in this estate and that the honest version computes visibility from the booking's own
  `scheduledDate`/`scheduledTime`. **In the booking's own zone** — `Booking.zoneId`, not `MARKET_ZONE`
  — because D58 settled exactly this for cancellation and an appointment is read in its own calendar;
- **a decision about who may see it and when.** "An hour before" is the prototype's promise; whether the
  professional sees it earlier, and whether it survives a cancellation, are not settled anywhere;
- **the erasure question.** A meeting link is a URL, and D39's rule is that every row the sweep touches
  gets a number on the receipt. A link that names a room per booking is probably not personal data, but
  the sweep's coverage is decided per column and this would be a new one.

The seam D86 built is for the hosted upgrade and is **not** what this needs: nothing here hosts a room.
`MeetingRoom.professionalSupplied()` is already the right answer from the provider's side; what is
missing is somewhere to keep the link and a rule about revealing it.

**Closed by D87, and the prototype settled two of the three open questions in its own words**: *"A
private video link is sent to you and to [the professional] one hour before the session"* — both parties,
one hour.

**Built**: `Booking.meetingLink` (`varchar(500)`, nullable) in the JDL, the entity and the DTO, with an
**additive** changelog — regenerating the entity's own changelog would invalidate the checksum every
existing database recorded, which is the `ValidationFailedException` CLAUDE.md warns about. Verified:
130 ITs against a real PostgreSQL with no checksum failure.

**The reveal rule is `BookingWorkflow.meetingLinkFor`, four conditions and each a decision**: there is a
link (absent is normal, not a failure); the booking is `ONLINE` (a link on a home visit is somebody's
mistake and must not leak); the booking is still live — an **allow-list** over `BookingStatus` so a new
status decides for itself; and the session is within the hour **in the booking's own zone**, not
`MARKET_ZONE`, driven with a `America/Sao_Paulo` booking so the two answers actually differ. It stays
revealed after the session starts, deliberately: a customer who joins late needs it more than one who is
early.

**On `BookingDetail` and not `BookingView`** — the view is what `/mine` returns for every booking, so a
list carrying live links would disclose all of them on every page load. **The erasure sweep clears it**,
and it is the only one of those five fields that is not data *about* the customer: a live room URL
outliving the person it was for is a door rather than a datum.

**Not done, and deliberately not opened as an item**: nothing writes the column yet. A professional's
"supply your link" endpoint is one write on a resource that already exists rather than a package —
whoever adds it adds it. D87 §8.

The third open question — whether the reveal survives a cancellation — the prototype does not answer, and
D87 decided it conservatively: it does not, because a link to a room nobody will be in is worse than a
customer having to ask.

---

## NEW-46 — the roll gate gives up before docker does, so a successful roll exits 1 · DONE (D89)

Found by rolling quality to `4b18ac6` on 2026-09-11. **The roll succeeded and `startup.sh` exited 1**,
dying at its own health gate with `✗ gateway did not become healthy` — and therefore never reaching its
own `verify`. The stack was healthy; the gate was short.

**Measured**, container start to Spring's own "Started" line, with the OpenTelemetry agent attached:

| | gateway | catalog | messaging | booking | payout |
| --- | --- | --- | --- | --- | --- |
| wall clock | 528s | 492s | 561s | 592s | **641s** |
| Spring's own figure | 321s | 404s | 400s | 406s | 410s |

The gap — 170 to 230 seconds — is the JVM plus the agent rewriting classes **before Spring's clock
starts**, which is exactly the phase nothing had budgeted for.

**Three ceilings, and two of them were below reality:**

| bound | was | measured need |
| --- | --- | --- |
| `startup.sh`'s gate — 90 polls × 4s | **360s** | 641s |
| docker's own patience — `start_period` + `retries` × `interval` | **460s** | 641s |
| the roll | — | all five breached both |

**It is D81's finding one script along.** A gate below the daemon's own ceiling overrules a verdict the
daemon has not reached — D81 argued that for `deploy-prod.sh` and chose 600s against docker's 420s
there. Here the script was *below* docker and docker was below reality.

**Fixed by raising both, anchored rather than guessed.** `start_period` 60s → **300s**, because failures
inside it do not count at all, so widening it buys time for a slow start without making a genuinely
broken service look healthy for longer: docker's ceiling becomes 300 + 400 = **700s**. And the gate
becomes `HEALTH_POLLS=200` × 4s = **800s**, above docker so the daemon decides first, and above the
measured worst case with headroom. The refusal names its own budget now.

**One defect introduced and caught while fixing it**: `[[ $i == HEALTH_POLLS ]]` compares against the
literal string, so the timeout arm would have been dead and the loop would have ended silently after
200 polls. Proven both ways before and after — `i=200` fires with `"$HEALTH_POLLS"` and never fires
without it.

**Unexercised**: the new gate has not been driven by a real roll. Its arithmetic is measured and its
comparison is proven, but the stack is healthy and re-rolling it to test a timeout that should not fire
would cost a ten-minute restart to observe nothing. The `start_period` change takes effect on the next
container recreate.

**CLAUDE.md's "startup takes ~2 minutes" was true before the agent was attached** and is corrected.

---

## NEW-41 — the packages table disagrees with three of its own sections, and stopped indexing after NEW-22 · DONE (D83)

Opened at **D78's review**, which found one row and asked for the whole table to be checked rather than
that row fixed. Doing so turned up two more disagreements and a bigger finding underneath them, which is
why this is an item rather than a correction.

**NEW-40 is deliberately skipped**: it is reserved by a package running in parallel (NEW-37, decision
D79). The gap is not a lost item.

**Three rows disagree with the section that owns them.** Measured by extracting every
`| **ID** | … | STATUS |` row and comparing it against that ID's `## ID — … · STATUS` heading, then
reading each hit by hand — the extractor's own truncation produced three false positives (`PARTLY` for
`PARTLY DONE` on WP-09, WP-13 and WP-19) which are **not** defects:

| item | the table says | its section says | which is right |
| --- | --- | --- | --- |
| **NEW-21** | `READY` | `DONE (D62)` | the section — D62 closed it, and the row is the state before that |
| **WP-17** | `READY (spec only)` | `BLOCKED` | unresolved; the section's body says a person has to want a video provider, which is `BLOCKED`'s definition here, while the row's "spec only" is what D37 delivered |
| **WP-18** | `CLOSED` | `BLOCKED` | **neither cleanly** — the section's own body says the rename "largely defused" it *"but the question itself is still unanswered on the host"*, so `CLOSED` overstates and `BLOCKED` understates |

`NEW-11`'s two statuses read differently (`WON'T, until there is a second attempt` against
`WON'T, until there is one`) and are the same answer in different words; it is listed here so the next
sweep does not re-find it as a fourth.

**The bigger finding is that the table is not stale — it is partial.** **Seventeen** sections have no
table row at all: **NEW-23 through NEW-39**, unbroken. So the convention silently changed after NEW-22,
and the table stopped being an index of the backlog while continuing to look like one. That changes the
fix, which is the reason this is not a three-line edit:

- **correcting the three rows** leaves a table that indexes 41 of 58 items and still reads as complete;
- **completing the table** means seventeen new rows, each duplicating a status that already exists two
  screens down — the duplication that produced all three disagreements in the first place;
- **deleting the NEW-* rows from the table** and letting the sections be the record is the third shape,
  and the cheapest to keep true: the table then indexes the **work packages** only, which is what its
  header (`| WP | Package | Status | Blocked on |`) says it is, and the NEW items are read where they
  are argued.

The third is the one to reach for, and it wants the file's own vocabulary paragraph (line 10) updated to
say where an item's status lives. **It is NEW-15's root cause in a document**: one fact in two places
with nothing holding them together — and this file already carries the general rule for it, *"where the
two disagree, `decisions.md` wins — and where either disagrees with the code, the code wins"*, which
says nothing about a file disagreeing with itself.

A derived check is possible and probably not worth it: a `grep` pairing every table row with its section
heading would have caught all three, and is the same shape as `build.yml`'s CRUD gate one document over.
Cost it against the third option, which removes the pairing rather than guarding it.

**Closed by D83, taking the third shape — and the check was worth it for the half the third shape does
not cover.** The 22 `NEW-*` rows are deleted and the table indexes the nineteen work packages, which is
what its header always said. The vocabulary paragraph now says where a status lives: in the item's own
`## ` heading, and for a `NEW-*` item that is the only place.

**The costing above is right about the `NEW-*` rows and wrong about the WP rows.** Two of the three
disagreements were WP rows, and that pairing survives the third shape — so it is guarded by
`.github/checks/backlog-table-agrees.sh`, wired into `build.yml`. It compares the **status word** and not
the whole cell: a differing qualifier is "the same answer in different words" by this item's own reading,
so `DONE` against `DONE, merged as PR #19` is a note. **The check's first version refused exactly that**,
on WP-04 and WP-11, and was wrong.

WP-18 is `PARTLY DONE` in both places, since neither of its two statuses fitted, and its "Blocked on"
cell now names production host access rather than saying the rename made it moot. WP-17 takes its
section's `BLOCKED`. **A fourth pair turned up and is not a defect**: WP-16's row said `WON'T (measured)`
against `WON'T, for now` — NEW-11's class again; normalised anyway, since identical cells cost nothing.

One measurement warning from doing it, recorded because it produced a wrong reading first: a
`grep '^| \*\*WP-'` over the whole file picks up the illustrative table **in this section**, which has the
same row shape, and reported WP-17 and WP-18 as already agreeing when they did not. The check matches the
table's header and stops at the first blank line for that reason.

---

## NEW-42 — part 6 asserts TEXT about `deploy-prod.sh`'s call sites instead of executing them · DONE (D80)

Opened by **D78 §15**, and it is the item the cycle discipline produced rather than a defect anybody
found in the code: NEW-36's area returned findings three rounds running, each one a fail-open **in the
guard added to close the previous round**, so the wrong decision was the guarding strategy and not any
of the matchers.

| round | the fail-open | where it was |
| --- | --- | --- |
| D78 §13 | `SSH_OPTS` lifted and asserted about nowhere | in the check just extended for the gate |
| D78 §14 | that guard satisfied by a **trailing comment** | in the fix for §13 |
| D78 §15 | the guard asserts the option's **name** on the **first** declaration; the **phase** is bound to its call sites by nothing | in the fix for §14 |

**Each guard was exact about the text it had just been burned by and silent about the binding one step
away** — the value behind the option name, the code behind the comment, the caller behind the phase.
The blocking instance: `health_gate rollback` swapped for `health_gate deploy` in `rollback()` parses,
and left the check *and* its test at exit **0** while restoring §14's defect verbatim — an operator
told *"NOTHING HAS BEEN ROLLED BACK"* and offered `--rollback` after the rollback had just run.

**The structural cause is that part 6 never runs the program.** It lifts functions with `awk` and
drives them with call strings the harness itself writes (`GATE='health_gate deploy; …'`), so it
verifies a *function* and cannot see what the *script* passes. §15's repair for the blocking finding
is two stripped-text greps — which is a compromise stated as one at the site.

**The shape.** Source the shipped `deploy-prod.sh` in a subshell with `ssh`, `scp`, `docker` and `run`
replaced by stubs, then invoke the real `rollback()` and the real router branch, and assert on what
the stubs were handed. That closes findings 1 and 4 of §15 structurally, and with them the whole
"one step away" class: the phase a caller passes, which options an `ssh` actually receives per site,
and whether a refusal's sentence matches the arm *and* the caller that reached it. It also subsumes
the near-vacuous `>= 2` floor with real per-site coverage.

**Costed honestly, and it is not small:**

- **Nothing in this repository sources `deploy-prod.sh` today.** Every existing reading — D66, D69,
  D71, D75 and all of D78 — lifts functions by `awk` precisely to avoid it.
- **The file resists sourcing in three specific ways.** It runs `cd "$DEPLOY_DIR"` at the top; it
  installs an `ERR` trap that `die`s on any non-zero command; and its **router runs on load** (the
  `if (( DO_ROLLBACK ))` block and the `resolve_tag; preflight; confirm; …` sequence at the foot), so
  a naive `source` performs a deploy. Sourcing therefore needs either a guard in the script — a
  `[[ "${BASH_SOURCE[0]}" == "$0" ]]` around the router, which is a real change to the one script
  nothing can integration-test — or an `awk` that strips the router before sourcing, which
  reintroduces a lift and with it a terminator to get wrong (D75 case 21).
- **`resolve_tag` shells out to Maven and `preflight` requires `docker`, `git` and a `--host`**, so
  the router branch has to be reachable with those stubbed too.
- **The `run` wrapper is the cheap half**: every mutating command already goes through it, so stubbing
  `run` captures most call sites without touching `ssh` itself.

**D49 still holds and bounds the whole thing**: `deploy-prod.sh` has never been executed against a
host. That is *why* a harness matters — no environment will ever catch these — and it is also the
ceiling: sourcing the file with stubs establishes what the script *passes*, never what a production
host *answers*.

**Do not close it with a fourth textual guard.** D78 §15 is explicitly the last textual round on that
branch; if a fifth guard of this shape looks necessary, that is this item.

**CLOSED by D80, and no fourth textual guard was written — the two that existed are deleted.** Part 7
of `host-probe-attribution.sh` sources the shipped file and calls `main` and `rollback` for real, and
separately **executes** it as a subprocess, against stubbed `ssh`, `scp`, `docker`, `curl` and `git`;
it then asserts on the argument vectors the stubs were handed. **Twenty** remote invocation sites over
four scenarios, classified from the arguments, with unmatched in **either** direction an error — a site
that stops being asked and a seventh probe growing beside the six are both refusals.

Three things are executed rather than read: the **phase each shipped caller passes** (read off the
sentences the phase composes, split at `step "Rollback"` so a sentence is attributed to the caller and
not merely to the arm), **which options each individual `ssh` receives** (against the array the running
script holds, so an option *added* and not reaching a site is red too), and **two of the four phase ×
arm pairs D78 §15 removed rather than tested**. §15's two greps are gone; cases 37 and 38 are the same
two mutations through the executed door.

**The router moved into `main()`, called under `[[ "${BASH_SOURCE[0]}" == "$0" ]]`** — the `awk`-strip
alternative was refused as a fourth lift (D75 case 21's terminator trap) and because a stripped router
cannot be executed at all. It is a safety property in its own right: before it, `. ./deploy-prod.sh`
**was** a production deployment. That change introduces exactly one defect — nothing calling `main`
leaves a script that parses and exits 0 having deployed nothing — which is why part 7 executes the file
as well as sourcing it, and which is case **45**.

Measured: the check at **65** assertions, its test at **51 ok / 0 failed** over **50** mutations, and
**four** controls each verifying as a set — part 6 removed {23,24,25,26,27,28,35,36,41,42} (D78's set,
unchanged, so part 7 took no case's door from it), part 7 removed
{37,38,43,44,45,46,47,48,49,50}, the `SSH_OPTS` value guard removed {31,32,33,34,39,40}, the two-caller
assertions removed {35}. D78's fourth control was the two greps this deleted, so it is replaced rather
than added to. **D49 still bounds all of it**: this establishes what the script *passes*, never what a
host answers.

**Review took one should-fix as code** (D80 §6b). The sourced driver was `( source …; eval … ) || true`,
and **a compound whose status is tested disregards errexit and the ERR trap for everything inside it** —
including the trap the sourced subject installs. So the driver let the shipped script walk past a failed
command the executed program dies on, and printed `✓ rolled back to 1.3.9` for a rollback whose roll the
host refused. It is a **child process** now (`bash -c 'source …; main'`), and the `refused` scenario is
a permanent assertion that the trap fires there — with a fifth throwaway control confirming that
assertion is its sole carrier.

---

## NEW-47 — a customer who registers can never log in, and is deleted three days later · DONE (D94)

**Phase 1, and the first thing to pick up.** Opened by D90 §2. Nothing in this repository has ever
recorded it, which is the point: every part of it is generated code doing exactly what it was generated
to do, and the composite is a front door that swallows people.

**The sequence, each step measured:**

1. `POST /api/register` is `permitAll` — `gateway/.../config/SecurityConfiguration.java:73`, one of five
   such paths. WP-19's review noted the open self-registration and nothing followed from it;
2. `AccountResource.registerAccount` → `userService.registerUser(...)`, which sets
   **`newUser.setActivated(false)`** (`UserService.java:121`), then
   `.doOnSuccess(mailService::sendActivationEmail)`. The response is **`201 CREATED`** and does not wait
   for the mail;
3. `MailService` catches `MailException | MessagingException` and logs **`LOG.warn`** (`MailService.java:79-80`);
4. `application-prod.yml` carries `spring.mail.host: localhost`, `port: 25` and
   **`jhipster.mail.base-url: http://my-server-url-to-change`** — the literal generator placeholder,
   the only occurrence of that string in any `.yml` in the repository. **No compose file in any of the
   three environments sets `SPRING_MAIL_*` or `JHIPSTER_MAIL_BASE_URL`** — grepped across
   `docker-compose.dev.yml`, `quality/compose.yml` and `deploy/docker/docker-compose.prod.yml`;
5. `UserService`'s `@Scheduled(cron = "0 0 1 * * ?")` (`:291`) deletes not-activated users after three
   days — its own comment at `:287` says so.

**So: 201, no email, `activated=false`, cannot authenticate, gone in three days, one WARN line.** The
same shape as CLAUDE.md's *"a missing broker is silent"*, in the account lifecycle, on the one path
every customer takes first.

**`POST /api/account/reset-password/init` is the same defect with a worse ending** — it is also
`permitAll`, and a password reset that silently never arrives on a live marketplace generates support
load rather than a deletion.

**And there is no rate limit on it.** The only `limit_req` in either production nginx file is inside
`location /services/healthconnectbooking/webhooks/` (`deploy/prod-server/hc-market-app.conf:197-199`,
the sole non-comment occurrence in both files). Open self-registration
with no throttle, on a public health-services domain, is an account-creation amplifier whether or not
the mail works.

**Done means:**

- `SPRING_MAIL_HOST`, `_PORT`, `_USERNAME`, `_PASSWORD`, `_PROPERTIES_*` and `JHIPSTER_MAIL_BASE_URL`
  passed by all three compose files, with the same `:?`-or-default discipline the estate already
  applies to its three secrets. **The base-url is the one that must be required rather than
  defaulted**: a default is how `my-server-url-to-change` survived, and a link to the wrong host is
  worse than a mail that fails loudly;
- a **decision recorded** about whether an unactivated registration is deleted at all. Three days is a
  generated default nobody chose, and against a marketplace whose supply is verified by hand it is
  probably wrong;
- `limit_req` on `/api/register`, `/api/account/reset-password/init` and `/api/authenticate` in both
  nginx files — the zone must be declared in the `conf.d` snippet, not in the vhost, for the reason
  `hc-market-app.conf:12-16` already explains in place;
- **exercised on quality, end to end, against a real mailbox.** This is the half that cannot be skipped:
  every unit test in the estate passes today. Quality runs `dev,test`, where `admin` and `user` are
  seeded activated, so nothing there has ever walked the registration path either.

**Blocked on one outside fact** — an SMTP provider and credentials (D90 §7). Everything else is
engineering and can be built against a local catcher while that is obtained.

---

### CLOSED 2026-09-17 — `decisions.md` D94

**What was built**, against the five "done means" bullets above:

- **the three mail values, in all three compose files.** `HC_MAIL_HOST`, `HC_MAIL_PORT` and
  `HC_MAIL_BASE_URL` are `:?` in `docker-compose.prod.yml` and defaultless placeholders in
  `application-prod.yml`; `deploy-prod.sh` checks all three by name on the host before it touches the
  stack (`CONNECTION_KEYS`, now fifteen values); dev and quality default them at a **mailpit** catcher
  whose SMTP port is deliberately unpublished. The username, password and the two STARTTLS properties
  are **optional** — a relay may need none of them, and D94 §3 argues why that is not laxness.
  `application-prod.yml`'s `http://my-server-url-to-change` is gone and CI refuses its return;
- **the deletion is a decision now.** `healthconnect.accounts.unactivated-retention-days`, **default
  3**, so a default estate behaves byte-identically to every estate that has ever run. The architect
  ratified keeping the number and making it configurable; D94 §2 argues the two rejected shapes
  (extend it, stop deleting) rather than listing them. `@Scheduled` is out of the generated
  `UserService` and the schedule is in a new `UnactivatedAccountSweep` via `SchedulingConfigurer` —
  `0` and a negative window refuse startup, and there is deliberately no "never delete";
- **`limit_req` on the three paths, at both edges** — `hc_market_login` (1/s, burst 5) and
  `hc_market_account` (10/min, burst 3), keyed through a `map` so the single `location /` is not
  duplicated, which is hc-patient's pattern and its reasoning. Production's zones are a new
  `deploy/prod-server/nginx-conf.d/hc-market-account.conf`; quality's are in `host-site.conf` itself,
  because that file is a whole site at http scope. **Provided, printed, not installed** — `/etc/nginx`
  is the architect's;
- **exercised end to end, for real.** Registration → the message in the catcher → activation → sign-in,
  against a live gateway from this branch, a real MongoDB and a real SMTP catcher on this workstation:
  **`201`**, a message whose link carries the activation key, **`200` from `GET /api/activate`**, and a
  token from `/api/authenticate` — where the same credentials had answered **500** before activation,
  which is NEW-61. The sweep was then watched deleting a real unactivated account and leaving a recent
  one alone;
- **both privacy documents amended in the same commit** — `privacy-notice.md` §7.1 (the deletion is a
  policy we hold; the confirmation mail is no longer a known defect; contact us if it does not arrive)
  and `processing-record.md` §3.1, §2.1's recipients, §4's second transfer, §5's three new rows and a
  new §6.7. CI refuses a build whose code and whose two documents disagree about the number.

**Seven guards, 22 driven states**: `AccountRetentionUnitTest`, `UnactivatedAccountSweepUnitTest`,
`UnactivatedAccountSweepIT`, `MailDeliveryGuardUnitTest`, `MailDeliveryInfoContributorUnitTest`,
`ThereIsOneAccountSweepTest` (ArchUnit — no `@Scheduled` anywhere in the gateway, which is what a
regeneration brings back) and `.github/checks/account-lifecycle-guards.sh` with its own test.
Gateway: **97 unit / 140 IT / 0 Checkstyle**, from 67/135.

**What it did NOT close, and it is the same outside fact as before:** no message has reached a real
provider, because none is chosen. That is D90 §7's budget item. The difference is that an environment
with no mail configuration now **refuses to start** rather than answering 201 and discarding the
registration, so the gap can no longer be deployed through.

**Three things surfaced rather than taken, and D94 §5 is titled for all three**: whether
`/api/activate` and `/api/account/reset-password/finish` — also `permitAll` — should be rate-limited
too (recommended; hc-patient limits all four of its account paths, and CI pins the exclusion as an
exact set so the answer has to be written down); whether the window should be able to mean "never
delete", which is a sentinel rather than a number and would make `processing-record.md` §6.1's gap
bigger; and whether a registration whose mail fails should still answer 201 (it does, unchanged).
**A fourth was found by the walk and is NEW-61**, which is not a surfaced question but a measured
defect.

---

## NEW-48 — there is no application: the front end the prototype has been specifying all along · READY

> **RATIFIED 2026-09-16 — `decisions.md` D92 §2.** **Client-only JHipster 9.2.0 Angular into `web/`**,
> `skipServer: true`, to `hc-admin/app`'s shape — so the gateway is never regenerated and D61's
> `admin`/`admin` credential and the `pom.xml` OTel block are never at risk. **`enableTranslation` is
> ON**, against the recommendation: every string behind a `jhiTranslate` in `i18n/en/*.json` **from the
> first screen**, because retrofitting i18n means touching every template written without it.
> **`jhiPrefix` is `abm`.** Close the three day-one traps: `assets` as a glob-with-`ignore`,
> `eslint.config.ts` ignoring `.claude/`, and never white text on gold.

**Phase 2, and the largest package remaining in this project.** D90 §2, §3.

**What exists to build against is unusually complete**, which is why this is a build and not a design:
the prototype is the UX contract, the seed is its data, and every endpoint exists because a screen in it
needs one. **15 screens**, enumerated from the prototype's own view functions:

| | screen | route | endpoints it needs | token |
|---|---|---|---|---|
| 1 | Discover | `#/discover` | `/api/categories`, `/api/professionals`, `/api/reviews/count` | no |
| 2 | Browse | `#/browse` | `/api/professionals` with filter, sort, page | no |
| 3 | Public profile | `#/pro/{ref}` | `/api/professionals/{ref}`, `/availability`, `/reviews` | no |
| 4 | Booking wizard | `#/book/{ref}` | `POST /api/bookings` | yes |
| 5 | My bookings | `#/bookings` | `/api/bookings/mine`, `/{ref}`, `/cancellation-preview`, `/cancel` | yes |
| 6 | Messages | `#/messages` | `/api/threads`, `/{ref}`, `POST /{ref}/messages`, `/api/notifications` | yes |
| 7 | Saved | `#/saved` | `/api/favourites` | yes |
| 8 | Account | `#/account` | `/api/account` | yes |
| 9–15 | the professional workspace | `#/pro/{overview,requests,schedule,services,earnings,reviews,profile}` | `/api/pro/**` — **31 mappings across three services** | yes |

**Five stages. They are the package split if D90 §3 lands as recommended**, and they are not pre-split
into WP rows here because the shape decision is the architect's and a package list written before it
would name files that may not exist.

- **Stage A — the scaffold.** Generate, wire `ApplicationConfigService.getEndpointFor(api, microservice)`
  — the house pattern, and the rule is that a frontend never writes a service path literally — the JWT
  interceptor and login against `POST /api/authenticate`, the brand tokens, and CI (`npm test`, `lint`,
  `webapp:prod`). **Close D90 §3's three traps in the generated output before the first commit**: the
  `angular.json` `assets` glob-with-`ignore` (or this app publishes its own Sass, as `hc-admin/app`
  measurably does on a live hostname), `.claude/` in `eslint.config.ts`, and never white on gold
  (`#C59437` on white is 2.74:1, fails AA).
- **Stage B — the four public screens** (1–3). **No token, so it does not wait on NEW-47** and it is the
  first thing anybody outside this project can look at. `verify-prototype-live.mjs` already proves these
  endpoints answer the prototype's own field names, so this stage is layout and state, not discovery.
- **Stage C — the customer, authenticated** (4–8). Depends on NEW-47. Two rules carry over from the
  prototype's live mode and are not negotiable: the booking POST **omits** `priceMinor`, `currency` and
  `professionalLogin` so the server establishes them (D22, D28), and **no reply is ever fabricated** in a
  thread — the demo invents one 1.6s after sending and that is a lie against a live estate.
- **Stage D — the professional workspace** (9–15). The richest stage and the one with the least
  precedent. `/api/pro` is **31 mappings spread over three services** — counted, not estimated:
  catalog's `ProWorkspaceResource` **20**, booking's `ProBookingResource` **8**, payout's
  `ProEarningsResource` **3**, all three declaring `@RequestMapping("/api/pro")`. So one screen family
  fans out to three upstreams behind one prefix, which is precisely what
  `getEndpointFor(api, microservice)` exists for and what a hardcoded path would get wrong. It takes
  **no professional parameter** anywhere, resolving the owner from the JWT subject, so the whole family
  is built without an id in it. This stage
  **supersedes NEW-8** rather than fixing it — that item is the prototype's workspace rendering demo
  figures under a LIVE banner, and it stops mattering the day a real screen exists. Two things to fold
  in here rather than open as items: `/api/pro/payouts` returned `[]` for ever until **NEW-51**, which
  is **DONE** (D95) — so the payout table on that screen now has rows to draw whenever the desk has run
  a batch, and the screen no longer has to explain an empty one — and the
  meeting-link write D87 §8 deliberately declined to open — *"one write on a resource that already
  exists rather than a package"* — belongs on the services or schedule screen.
- **Stage E — the brokerage desk.** **NEW-53**; the prototype specifies no desk at all.

**Three decisions inside the build**, all recommended in D90 §3: the shape (client-only Angular at
`web/`, which avoids the gateway regeneration hazard that hands back a working `admin`/`admin`
credential — D61), the `jhiPrefix`, and translation on or off.

**One design fact the prototype hands over rather than teaches.** Its router tells `#/pro/p1` from
`#/pro/profile` with a reserved-word list duplicated at two sites plus a `state.role` test. Do not
inherit it: split the namespaces — `/professionals/:ref` public, `/pro/*` workspace — and the list
disappears along with the latent defect that a professional whose `reference` is `profile` is
unreachable.

**`GET /api/stream` is available and is on the gateway** (D25/D29), filtered to the JWT subject with no
`?login=`. It is lossy on purpose; the durable copy is messaging's notification table. A first version
can poll and adopt it later without changing a screen.

**What this does not include**, deliberately: `api/` and `mobile/` stay empty. `mobile/` appeared on
2026-09-11 and nothing in this repository explains it; D18 puts push at "no app", so a mobile client is
a product decision nobody has taken.

---

## NEW-49 — self-service professional signup, so this marketplace stops having eighteen practitioners for ever · READY

> **RATIFIED 2026-09-16 — `decisions.md` D92 §3, and the subject of this item CHANGED.** It was framed
> as desk enrolment behind `ROLE_BROKERAGE`; the answer is **self-service signup**, with an unverified
> professional **visible and badged** rather than withheld. Both went against the recommendation.
>
> **It is cheaper than this item costed it, because the read side already behaves this way** — measured:
> `GET /api/professionals` returns 18, of which **2 are `UNVERIFIED`** (`p9`, `p18`), and
> `verifiedOnly=true` returns 16. `verifiedOnly` is **opt-in**, so the default listing has served
> unverified professionals for the estate's whole life. What is missing is a badge on a client that does
> not exist yet.
>
> **The sharpest edge is already closed:** `verification`, `insured` and `policeClearance` are absent
> from `SaveProfile` — *"a professional who can set their own verified flag is a trust chain with a hole
> in it."* **What is not closed is `NEW-58`**: `Credential.label` and `yearsPractising` are self-declared
> free text served publicly, and `SUSPENDED` professionals are served in the default listing.

**Phase 3.** The supply side of a two-sided marketplace has no way in.

**Measured**: `new Professional()` appears **once** in all of `catalog/src/main` —
`service/seed/CatalogSeeder.java:142`. Every one of the 18 professionals on every estate is a seeded
row extracted from the prototype.

**What exists is more than it looks like, which is what makes this small.** `ProWorkspaceResource`
(20 of the 31 mappings on `/api/pro`) already lets a professional who *has* a row edit essentially everything:
services (`POST`, `PUT`, publish, hide), the profile, and **credentials and highlights** —
`ProWorkspaceResource.java:199` and `:207` write them, so CLAUDE.md's delete-table row saying
`CredentialResource`/`HighlightResource` were replaced by *"nothing, deliberately"* is true of the
generated CRUD and **not** true of the capability. There is a qualification write path; a reader of that
row alone would conclude otherwise.

**What is missing is exactly one thing: the row.** `meOrThrow()` (`:534-538`) resolves
`findByUserLogin(me())` and **404s with `"no professional listing for this account"`** when there is
none. So a person can register an account (NEW-47 notwithstanding), log in, and every one of those 28
endpoints answers 404 for ever.

**Where it belongs is the open question, and D16 answers it somewhere else.** Spec §13 Q3 is
*"professional onboarding and KYC"* and D16's answer is **"manual review in `hc-admin`"** — another
product, in another repository, with its own gateway and its own database. Meanwhile hc-market has built
`VerificationDeskResource` and the append-only `VerificationReview` audit *here*, behind `ROLE_BROKERAGE`.
**So the review desk landed in hc-market and the enrolment was assigned to hc-admin, and nothing
reconciles the two.** That is the decision (D90 §7).

**Recommended: build enrolment in hc-market.** The verification desk, the `verification` projection
(D16), `verifiedOn` (D33/D47) and the badge rules are all here; hc-admin would have to reach across a
service boundary into catalog's schema or call an endpoint that does not exist, and D16 predates all of
that being built. **What would change my mind**: if hc-admin is where a human operator already works
every day, a second console is a worse answer than a cross-product call.

**Done means** an `/api/pro/enrol`-shaped write that creates the `Professional` from the JWT subject in
`verification: UNVERIFIED` with no listing visible, and a `ROLE_BROKERAGE` path to admit it — plus a
decision about who may self-enrol at all. **The scope note is a hard boundary here**: everyone on this
platform is non-medical and may not diagnose or prescribe, and an open enrolment on a health domain is
where that stops being copy.

---

## NEW-50 — the API's "from" price includes a free service and the prototype's excludes it · READY

> **RATIFIED 2026-09-16 — `decisions.md` D92 §4. Neither reading wins: say both.** The headline is the
> **paid** minimum and the free service gets its **own marker**, not a price of ₵0. It is the only one of
> the three options that is not a one-line change and the only one that is true — a doula whose packages
> run to ₵3,200 is not a "from ₵0" listing, and a free intro call is a conversion tool.
>
> **Do not implement it by changing `fromPriceMinor`'s meaning.** `0` is the honest minimum; the badge is
> a second field **derived** from the same `services` collection, never stored.

**Phase 3, and the decision is a product one.** Found in a browser against live mode on 2026-09-15 and
confirmed against the running quality estate.

**The two definitions:**

- **the prototype** computes `p.rate = Math.min(... p.services.filter(s => s.price > 0) ...)` — an
  explicit `> 0`, at **three** sites (`:740`, `:2425`, and `:2824`, which is live mode's own recompute).
  The cheapest **paid** service;
- **catalog** is `select min(s.priceMinor) from ServiceOffering s where s.professional.reference = :ref
  and s.active = true` (`MarketplaceQueryRepository.java:40`). The literal minimum, **including zero**.

**Measured through the gateway**, exactly two of 18 differ — which matches CLAUDE.md's *"two seeded
services are genuinely free"*:

| | API `fromPriceMinor` | active prices | prototype shows |
|---|---|---|---|
| **p13** Hannah Tetteh | `0` | 0, 42000, 320000 | **₵420** |
| **p12** Abena Owusu | `0` | 0, 28000, 156000 | **₵280** |

**The sharp form of this is not the divergence, it is the comment.** The javadoc two lines above that
query (`:37-38`) reads *"the cheapest ACTIVE service, **exactly as the prototype computed it**"*. It is
the house failure mode: a claim of parity, asserted rather than measured, in the file that would settle
it. **That half is engineering and is wrong either way the product question goes** — the comment must
stop claiming agreement it does not have.

**Which one moves is not engineering's.** CLAUDE.md's *"a 'from ₵0' listing is correct, not a bug"* is
about the **seed** being legitimate and says nothing about which definition a listing should show.
Framed:

- **show the cheapest paid service** (change the JPQL): "from ₵420" for a doula whose packages run to
  ₵3,200 is the honest headline, and a free 20-minute consultation is a lead-in rather than a price. Cost:
  `maxPriceMinor` filtering and `price-asc` sorting silently change meaning for those two rows, and a
  professional offering **only** free services gets `null` and must not sort as free;
- **show zero** (change the prototype and the comment): "from ₵0" is literally true and a free
  consultation is a real thing a customer wants to find. Cost: the prototype is the acceptance target
  and CI asserts the seed regenerates from it byte-identically, so editing it is not free;
- **publish both** — `fromPriceMinor` and `fromPaidPriceMinor`. Honest, and two numbers on a card is a
  design problem handed to NEW-48.

**Recommended: follow the prototype.** It is the acceptance target, the rule in this repository is that
the prototype wins on UX, and "from ₵0" on a browse card reads as an error to a customer rather than as
an offer. **What would change my mind**: if free consultations are a deliberate acquisition mechanic, the
tile should say *"free consultation available"* and carry the paid price — which is the third option and
is strictly better than either of the first two.

**Whichever way it goes, `MarketplaceService`'s `maxPriceMinor` filter and the two `price-asc`/`-desc`
comparators are on the same quantity** and must be settled in the same commit. And note the second half
of the browser report was wrong: **`fromPriceMinor` is present on the detail endpoint** — at
`.card.fromPriceMinor`, because `ProfessionalDetail` embeds the whole card and both paths call the same
`toCard`. Measured: `0` for p13. Nothing to fix there (D90 §5).

---

## NEW-51 — the ledger earns and nothing ever settles: no writer for `Payout` · DONE (D95)

> **CLOSED 2026-09-17 — `decisions.md` D95**, on branch `new-51-a-payout-run-that-settles` off `7f6b21b`.
> `PayoutRun` (never `PayoutService` — the JDL generates that) computes a batch from
> `payout is null` rows, attaches them, and records a settlement against a `bankReference` a human
> supplies; `PayoutDeskResource` is `/api/desk/payouts` behind `ROLE_BROKERAGE`. Six new files in payout
> and two methods on an existing hand-written repository. **No JDL change, no changelog, no compose
> change** — every column it uses was already generated and already in the schema.
>
> **44 new tests, and all twenty-five guards were mutated one at a time and watched going red**, restored
> byte-identical after each. `GET /api/pro/payouts` now has something to show, asserted end to end.
>
> **`payout is null` ALONE IS FALSE AS A GUARD, found in this package's own first draft.** It stops a
> *later* run claiming a settled row and does nothing about a *simultaneous* one: two runs both see the
> rows unclaimed, both write a batch, and because the attachment is last-writer-wins one batch ends up
> holding **no rows and still reporting the money**. Settle both and the professional is paid twice, with
> nothing anywhere disagreeing. The batch query is `@Lock(PESSIMISTIC_WRITE)` now — and that property is
> **reasoned, not measured**: no test drives two concurrent runs, `theBatchQueryTakesAWriteLock` is
> structural and says so on itself.
>
> **REVIEW FOUND THE SAME RACE ONE METHOD ALONG, and that is the finding worth carrying** (D95 §12).
> This package wrote three paragraphs about check-then-act in `open` and left **`settle`** doing exactly
> it: read, refuse `PAID`, refuse `FAILED`, write. Two settlements of one `OPEN` batch both answer 200
> and **the first settlement's `bankReference` is silently overwritten** — the loss `settle`'s own
> javadoc said was unreachable. Fixed with `findByReferenceForUpdate`, which is
> `BookingQueryRepository`'s shape from D43, keeping the unlocked finder for the desk's read. The red-first
> evidence was better than a new test: switching the read broke **three existing tests immediately**,
> because they stubbed the unlocked finder.
>
> **And review found a negative-net batch could be marked `PAID`** (D95 §13). `settle` refused `PAID`
> and `FAILED` and nothing else, so the batch D95 §6 calls *"not something a desk can transfer"* was
> settleable. The harm compounds: an operator clears the `OPEN` list by settling a −15,000 batch with the
> next period's reference, **the debt then reads as paid *to* the professional**, the next period settles
> in full, and 15,000 is overpaid with every record internally consistent. `settle` refuses
> `netMinor <= 0` now, naming this item — an interim door, because refusing is recoverable and a `PAID`
> row stating money moved is not.
>
> **The reviewer's own mutation is in the harness now and is the sharpest of the 25**: `Math.abs()` around
> the three sums keeps `gross - commission == net` **true**, so the invariant guard cannot see it, while a
> negated reversal is added rather than subtracted. A guard against wrong signs is not a guard against a
> *discarded* sign, and the two are one function call apart.
>
> **One grounded fact below is off by one, and the correction widens the gap.** `new Payout()` occurs
> **zero** times in `payout/src/main` — *including* the seeder, which never constructed one, and
> `seed-data.json` has no `payouts` key. So **even a seeded estate has never held a payout row**, and the
> quality box could not have demonstrated the endpoint either.
>
> **THREE QUESTIONS SURFACED AND NOT TAKEN → NEW-64.** A negative-net batch is created and logged at WARN
> rather than refused (argued in D95 §6, and it is the one most deserving a second opinion); `FAILED` has
> no transition at all, so what happens to a failed batch's rows stays undecided and `settle` refuses one
> saying so; and the desk still has no screen (NEW-53).
>
> **`Payout.reference` is `PAY-<yyyyMM>-<professionalRef>-<nn>`**, which departs from the JDL's
> illustrated `PAY-202607-AM` on both halves — payout holds no name to take initials from, and initials
> are not unique on a unique column. D95 §4.

> **GROUNDED 2026-09-16, read-only, and it is smaller than this item costed it.** The model **already has
> the double-payment guard**: `jdl/payout.jdl` declares
> `relationship ManyToOne { Ledger{payout} to Payout{entries} }`, so **`payout IS NULL` is "not yet
> settled"** and a ledger row already in a batch cannot be batched again. With `PayoutStatus`
> `{OPEN, IN_PROGRESS, PAID, FAILED}` and `settledOn` + `bankReference`, the whole
> settled-by-hand flow is already expressible. Measured: **`new Payout()` occurs zero times outside the
> seeder** — *corrected above: zero times in `src/main` at all.*
>
> **THE SUBTLE PART, which was written down nowhere until now: `Ledger.reversalOf`.** D23's compensating
> entries for resolved disputes must be **inside** the batch arithmetic, or a professional is paid for a
> booking that was refunded. Note how a reversal is keyed — it carries the **dispute** reference in
> `bookingReference`, because that column is unique and its uniqueness is the guard against a replayed
> `booking.completed` double-crediting — so a batch query filtering on `bookingReference` shape rather than
> on `payout IS NULL` will get this wrong.
>
> Act 987 blocks **automated settlement**, not recording one a human made. That is why this is unblocked.

**Phase 3.** `grep -rn "new Payout()" payout/src/main` finds **nothing** — CLAUDE.md's delete-table row
says so, and the consequence is stated there too: `/api/pro/payouts` returns `[]` on an unseeded estate
and always will. What that row does not say is that **this is the only thing standing between a working
ledger and a professional being paid.**

**Everything the model needs is already there**, which is why this is an item and not a package. From
`jdl/payout.jdl`: `Payout` carries `periodStart`, `periodEnd`, `grossMinor`, `commissionMinor`,
`netMinor`, `status` (`OPEN`, `IN_PROGRESS`, `PAID`, `FAILED`), `settledOn` and — the load-bearing field
— **`bankReference`**. `Ledger{payout} to Payout{entries}` attaches the earnings.

**`bankReference` is the model's own answer to Act 987 and the reason this is NOT blocked on it.** Act
987 gates *the platform moving money through a provider API*; `PaymentProvider` has no method that pays
a professional, on purpose (WP-13, D45). Recording a batch that a **human settled by bank transfer**,
against the reference the bank gave them, needs no provider, no licence and no new decision — and it is
what `bankReference` and `settledOn` were put in the JDL for. So the unblocked version of this item is
the whole of the internal record, and only *automated* settlement waits on counsel.

**Done means:**

- a `ROLE_BROKERAGE` **payout run**: select `Ledger` rows for a professional with no `payout`, earned on
  or before today minus the payout lag, sum them, write one `Payout` in `OPEN`, attach the rows. The lag
  is not a new number — `FoundingTerms` already carries `HC_BROKERAGE_PAYOUT_LAG_DAYS`, default 3 (D57);
- a `ROLE_BROKERAGE` **mark-settled** taking a `bankReference` and a `settledOn`, moving `OPEN` →
  `PAID`, and **append-only** in the sense that matters: a `PAID` batch is not re-openable and a ledger
  row already attached is not re-attachable. Uniqueness on the ledger side is what stops a double
  payment, exactly as `bookingReference`'s uniqueness stops a double credit (`payout.jdl:110-115`);
- the day it is `LocalDate`-dated, **`MarketCalendar.MARKET_ZONE`** and nothing else — payout already
  holds the copy and `SeedAndMarketCalendarsAgreeUnitTest` guards it. A `LocalDate.now()` here is
  NEW-10 re-opened in a new file, and the CI check that bans an implicit zone scans all five services;
- **`Payout.reference`'s format is a decision**: the JDL's comment says `PAY-202607-AM`, which encodes a
  month and initials. A payout run that is not monthly, or two batches in one month, breaks it.

**Not in scope**: paying anybody. That is WP-13 and Act 987.

---

## NEW-52 — two configured promises with no SWEEP behind either · DONE (D96)

> **CLOSED 2026-09-17 — `decisions.md` D96. Two sweeps, built as two things, and one of them is
> deliberately switched off.**
>
> | | |
> | --- | --- |
> | **retention** | `RetentionSweep` + `RetentionSweepRepository` in booking. Erases every customer with **no booking activity** for the financial period, through the same `ErasureWorkflow.eraseCustomer` the desk calls — so it is recorded on the `erased_subject` register like any other erasure (D39), for free rather than by a second mechanism. **Off by default**, and **dry run by default when enabled**: two independent decisions between an estate and an irreversible deletion. `GET /api/desk/privacy`'s `enforced` is **derived** from those two switches instead of the literal `false` it returned before, so the desk cannot disagree with what the estate will do |
> | **`Dispute.dueBy`** | `DisputeSlaSweep` + `DisputeSlaRepository` + `DisputeSlaProperties`. Reports unresolved disputes past their recorded deadline, **on by default**, at **WARN**, mutating nothing — no status change and no `DisputeStatusChange`, because a deadline passing is not an act anybody took |
>
> **The asymmetry is the decision, not an oversight.** One destroys records on a timer with nobody
> watching; the other reads two columns and writes a log line. They share a trigger and nothing else:
> separate classes, separate properties holders, separate repositories, separate defaults — and a test
> asserts the separation, because a later refactor folding them together would leave every behavioural
> assertion in both files still true.
>
> **Gates**: `cd booking && ./mvnw clean verify` — **279** unit and **146** integration tests, 0
> failures, modernizer clean. **Nine mutations run, each separately, all red**: the two defaults
> inverted, `parseStrictBoolean` replaced by `Boolean.parseBoolean` (which reads a mistyped
> `sweep-dry-run` as *dry run off* — the direction that deletes), `isEnforcing()` losing its dry-run
> conjunct, the desk reverted to a literal, the dry-run branch removed, the register exclusion dropped
> from the eligibility query, the query rewritten row-level, and `UNDER_REVIEW` dropped from the
> overdue predicate. All five mutated files restored byte-identical.
>
> **Three things it settled that the item did not anticipate**, each opened rather than taken:
> **NEW-67** (the register cannot say *why* somebody was erased — two callers now write identical
> rows meaning opposite things), **NEW-68** (the **operational** period still has no sweep, in
> messaging, and fanning this one out would apply a six-year clock to one-year data), **NEW-69** (who
> is told about an overdue dispute — the brokerage cannot be named from booking).
>
> **The brief's own anticipated decision does not arise**, and that is worth recording: it expected
> `eraseCustomer` to record an acting staff member with nothing for a sweep to put there. Verified —
> `ErasedSubject` holds a pseudonym and an instant, `ErasureRun` holds no actor, and nothing on that
> path reads `SecurityUtils`. Which is *why* NEW-67 exists: the absence of that column is the gap.
>
> **Both counsel-facing drafts are corrected.** `docs/processing-record.md` §3/§5/§6.2 and
> `docs/privacy-notice.md` §7 said "configured, not enforced"; the honest statement is now a third
> thing — the means exist for one period and are switched off, and the other period has nothing. A
> regulator-facing document understating a capability is as wrong as one overstating it.
>
> **REVIEWED 2026-09-18, nothing blocked, three findings and four nits — all taken, two of which
> changed behaviour** (D96 §12). The one worth reading: **the continue-on-failure property was
> asserted by nothing**, and `RetentionSweepIT` is structurally incapable of asserting it — it is
> `@Transactional` and `eraseCustomer` is `REQUIRED`, so the erasure joins the test's transaction and
> the per-customer boundary does not exist to be observed. **Measured**: with `sweep()` mutated to
> abandon the run at the first failure, that IT passed all 8. `OneFailedErasureDoesNotAbandonTheSweepTest`
> (no context, Mockito) covers it now and pins the absent `@Transactional` besides.
> **A kill switch was surfaced by a nit about configuration rebinding**: `configureTasks` runs once, so
> the registered task survives a rebind — an operator disabling the sweep on a running estate was
> silently ignored and the nightly erasure continued. `sweep()` re-checks now. Enabling by rebind still
> needs a restart, which is the safe asymmetry. **And that test was vacuous on its first draft** — an
> unstubbed mock made it pass against the very mutation it exists to catch, caught by running the
> mutation rather than trusting the green, and it now carries an explicit control.
> Two regulator-facing over-statements corrected with it: §6.2's unscoped *"no longer missing
> engineering"* (the sweep is **one activity wide** — a customer's data in booking; §2.6's payout rows,
> a professional's identity, and the operational period are all outside it) and §2.7's claim to store
> *"the acting staff member's sign-in name"*, which **no table holds** — the fact this package itself
> established, and NEW-67's premise.
>
> **Also verified rather than assumed**, from §5 of the brief: `@EnableScheduling` is live in booking
> under `@Profile("!testdev & !testprod")` (so active in dev, test and prod, and — note —
> **inactive under the test suite**, which is why no scheduled erasure can fire inside a build — though
> the *pattern* is exercised: D94's sweep fired on its own cron on the quality gateway at
> 2026-09-18T01:00:00.437Z through the identical `SchedulingConfigurer` path, measured at review, so
> what is untested is these two beans and not the mechanism); the
> three period property names and their committed fallbacks; that `PrivacyResourceIT` was the test
> pinning `enforced: false`; and that `eraseCustomer` needs no HTTP request in scope.

**The item as it was written follows.**

**Phase 3.** `@Scheduled` appears in exactly **two** places in all five services' main sources, measured:
`booking/.../OutboxPublisher.java:69` (the outbox poll) and `gateway/.../UserService.java:291` (the
generated not-activated-user cleanup — see NEW-47, where it is part of the defect rather than a
feature). **Neither is a retention sweep.**

> **The scheduler infrastructure DOES exist, and this item said it did not** — corrected 2026-09-15,
> `decisions.md` **D91**, item **NEW-56**. `@EnableScheduling` is active in **all five** services
> (`config/AsyncConfiguration.java`, `@Profile("!testdev & !testprod")`, so active on every estate that
> runs) and a `ThreadPoolTaskScheduler` thread is live in **all five** quality containers, measured at
> `/proc/1/task`. The two `@Scheduled` methods this item correctly enumerated are the proof rather than
> the exception: one of them is the estate's entire event-delivery path.
>
> **It changes the cost, not the work.** "Done means one scheduler and two sweeps" below should read
> *two sweeps* — there is no scheduler to stand up, no `@EnableScheduling` to add and no starter to
> introduce. A new `@Component` with a `@Scheduled` method on it is picked up on every estate as it
> stands.

**Two promises rest on that absence** and both are honest about it in the source, which is why this is an
item rather than a finding:

- **retention** — WP-09. Three categories configured from `HC_RETENTION_*` with counsel's ratified
  figures (financial 2190, operational 365), reported by `GET /api/desk/privacy` beside
  **`enforced: false`**, with `PrivacyProperties`' javadoc naming the remedy precisely: *"when one exists
  it calls `ErasureWorkflow.eraseCustomer` on everything past the window; the erasure semantics are
  already decided and tested, so what is missing is the trigger and nothing else."* That is the whole
  costing. It also warns, in place, that **the risk went up with D88 rather than down**: a populated
  three-category policy reads far more like a working regime than one unset integer did;
- **`Dispute.dueBy`** — the prototype's promise of five working days, recorded and not enforced.
  `DisputeWorkflow`'s javadoc at `:42` says so and points at the same gap.

**Done means two sweeps, and they are not the same kind of thing** (the scheduler is already there —
see the box above). The
retention sweep performs an **irreversible** act on real people's data and must therefore be: off by
default, dry-runnable with a count before it deletes anything, and **recorded on the erasure register
like any other erasure** (D31/D39 — a receipt whose count is too small reads as "we held nothing about
this person"). The dispute sweep only notifies. Do not build them as one thing because they share a
trigger.

**Two things to settle with it** (and neither is a reason to wait): **accounts and published reviews
still have no retention category** — WP-09 names it — and a review body is deliberately *not* erased
(D37: public speech about a professional), so "retain for ever" may be the right answer and needs saying
rather than defaulting.

---

## NEW-53 — a desk read leaves no trace, and the desk has no screen in the acceptance target · READY

> **RATIFIED 2026-09-16 — `decisions.md` D92 §5. Screens, after Phase 2**, as recommended: erasure is a
> legal deliverable under DPC registration `P0021484082`, and a receipt read out of `psql` is a process
> that will be done wrong under pressure on the one path that is irreversible.
>
> **The queue is now load-bearing rather than occasional** — D92 §3 made every listing arrive unverified
> and publish immediately, so between now and Phase 2 it is worked by hand. **The audit half is not
> closed by this and is not meant to be:** nothing records who looked at whom, whichever interface the
> looking happens through.

**Phase 3, and two findings that share a subject.**

**One: nothing records that staff looked.** `ROLE_BROKERAGE` guards four surfaces —
`booking/.../DisputeResources` (`/api/desk/disputes`), `ErasureResource`, `PrivacyResource` and catalog's
`VerificationDeskResource`. The estate's two append-only audit tables, `BookingStatusChange` and
`DisputeStatusChange`, both audit **acts**; `VerificationReview` audits a verification act; an erasure
writes `erasure_run` and `erased_subject`. **A read writes nothing.** So a `ROLE_BROKERAGE` holder can
`GET /api/desk/disputes` and `GET /api/desk/disputes/{reference}` across every customer in the estate and
the estate retains no record that it happened — while the same person's *erasure* of one customer is
recorded in three services. That asymmetry is the finding: the irreversible act is evidenced and the
disclosure is not.

It is also a live gap against WP-09's own documents. `docs/processing-record.md` is a draft, and a
processing record that cannot answer *"who accessed this person's data"* is answering a question counsel
will ask.

**Two: the prototype specifies no desk at all.** Measured — `CUST_NAV` and `PRO_NAV` are the only two
navigations, and the word "Dispute" occurs once in the whole file, as copy. So four endpoint families
behind `ROLE_BROKERAGE` have **no screen in the acceptance target**, and today the only way to operate
any of them is `curl` with a hand-minted HS512 token. For the dispute desk that is inconvenient. For the
**erasure** desk it is worse: D31/D39 make the receipt *the deliverable an operator files against a legal
request*, and the operator is a person who does not mint JWTs.

**The decision** (D90 §7): does the desk get screens, or does it stay operator-driven? **Recommended:
screens, as NEW-48 stage E, after the customer and professional halves.** A legal-request path whose
interface is a shell command is one that will be run wrong under time pressure. **What would change my
mind**: if the brokerage is one or two people who are comfortable in a terminal, a documented runbook
plus a token-minting helper is a tenth of the cost and defensible — but then the runbook is the
deliverable and somebody has to write it.

**Done means** the read audit (append-only, its own table, actor and subject and endpoint and instant,
and it must **not** itself become a disclosure surface — the same reasoning that keeps the reviewer's
login off the public profile, D47), plus either the four screens or the runbook. **Erasure is
desk-operated by decision, not self-service** (WP-08/D40) — a data subject asks a person, so whatever is
built is what that person uses.

---

## NEW-54 — no environment in this estate can receive a provider callback · READY, and a decision with it

> **RATIFIED 2026-09-16 — `decisions.md` D92 §6. Wait for production behind the DNS-hold**, against the
> recommendation of a time-boxed tunnel to quality. **So the payment path's first real execution is in
> production**, which was the stated objection and is the architect's call; what the decision avoids is a
> temporary public door onto a box whose seeded privileged accounts have passwords derivable from a rule
> published in this public repository, and a tunnel could never have exercised nginx, TLS or the route
> anyway.
>
> **The mitigations are therefore requirements, not advice:** the DNS-hold holds until the callback is
> observed end to end; smallest chargeable amount on a real account; watch the booking's status
> transition rather than inferring it from a 200; **`refunds-enabled` stays off** (D86); **`status` —
> `GET /transaction/verify/{reference}` — is the reconciliation path** for a booking stuck in
> `PENDING_PAYMENT`, and it has never reached Paystack either; **one booking, then stop and read the
> row.**

**Phase 4.** WP-13 records that no payment has been taken end to end, that Hubtel and MoMo are seams,
and that Paystack is four of six calls never spoken to a live account. It does not record **why the last
mile cannot be closed on the evidence available here**, and that is a planning fact rather than a
payments one.

**A Paystack payment completes on a webhook.** `POST /webhooks/payments/{provider}`, authenticated by an
HMAC-SHA512 signature over the raw body (D50), routed through a fifth gateway route and permitted by
`PaymentWebhookRouteConfiguration`. Until that callback arrives the booking sits in `PENDING_PAYMENT`,
`booking.requested` is withheld, and the professional is never told (D43). **The webhook is the payment**,
as far as this estate is concerned.

**And there is nowhere for it to land.** Quality is `jacserver`, which is this workstation: the gateway
publishes on `127.0.0.1:15509` and the vhost answers `market.healthconnect.local`, a private LAN name.
Paystack cannot reach either. Production is the only host in the estate with a public name — and it has
never been deployed.

**So the payment path's first real execution is necessarily in production**, unless something is added
for it. That inverts this repository's strongest working rule: quality is the last real gate, and it has
found seven defects every test suite passed. On the one path where a customer's money is already
committed, it is being asked to find none.

**Three ways out, costed:**

- **a tunnel to quality for one supervised session** (`cloudflared`, `ngrok`) — cheapest by far, needs no
  new host, and is a temporary public door onto a box running `dev,test` with **seeded accounts whose
  passwords derive from their logins by a rule published in this public repository**. Acceptable only as
  supervised, time-boxed, and only with the tunnel pointed at the webhook path rather than at the
  gateway;
- **a real staging host** — answers this and NEW-55 and WP-19's "nothing has run against a host", and is
  a machine, a certificate, a DNS name and a recurring cost;
- **accept it and make the first production payment a test** — a real card or wallet, the smallest
  chargeable amount, with a rollback plan and somebody watching booking's log. This is what most small
  teams actually do, and it is defensible *provided* it happens before any real customer can reach the
  site, which the DNS-hold in D90 §2 makes possible.

**Recommended: the tunnel, for one session, and then the production test.** The tunnel proves the
signature scheme, the field paths and the status mapping — the three things D45 refused to guess — and
the production test proves the route, the nginx `limit_req` and the certificate. Neither substitutes for
the other. **What would change my mind**: if a staging host is being bought anyway for the other three
products, this stops being a payments question.

**Still blocked, unchanged**: Act 987 (counsel), and Paystack live credentials with a test account.

---

## NEW-55 — the `prod` profile has never served an account, and there is nothing between quality and the public · READY

> **RATIFIED 2026-09-16 — `decisions.md` D92 §7. No staging host**, as recommended and entailed by
> NEW-54's answer. Production behind the DNS-hold *is* the pre-production environment: real nginx, real
> certificate, real route, no public name. **What would reverse it** is the other three products wanting
> the same box — not hc-market's decision to take alone.

**Phase 5.** A companion to WP-19 rather than a duplicate of it: WP-19 is *"fifteen things a person must
do on the host"*, and this is the one thing that is true **after** all fifteen are done.

**`jacserver` is the only pre-production environment and it runs `dev,test` deliberately.** That is
right for what it was built for — it is the only place short of production where CSP failures,
SPA-fallback swallowing, wrong-image deploys and wrong-app collisions exist at all, and it has found
seven defects nothing else could. But its profile choice means a specific and growing list of things
**no environment has ever exercised**:

- the **account lifecycle under `prod`** — quality seeds `admin` and `user` activated, so registration,
  activation and password reset have never been walked anywhere (NEW-47);
- the gateway's **refusal to create an administrator without `HC_GATEWAY_ADMIN_PASSWORD`** — `prod`-only
  by design (D61), and a deploy that trips it is rolled back by the health gate, which is the good
  failure and is still a failure nobody has seen;
- **`BrokerageBootstrap` writing the founding row into an empty table** — quality seeds, so its table was
  never empty (D57 says so in as many words);
- the payment webhook (**NEW-54**);
- the OTel agent **instrumenting** rather than merely loading, in production (WP-19; D63's script exists
  and `deploy/verify-otel-agent.sh` needs no estate, so this one is cheap and should just be run);
- and once NEW-48 lands, the **frontend against the production CSP** — `default-src 'none'`, which quality
  deliberately relaxes for `/prototype` alone and which production must never inherit (D49). An Angular
  app under `default-src 'none'` is a real piece of work, not a header.

**The decision** (D90 §7): is a staging host bought, or is the first production deploy the staging?
**Recommended: no staging host for hc-market alone.** The estate is five services and one frontend, the
quality box already proves the published image is the deployed image, and D90 §2's DNS-hold gives a
production estate that is real, reachable by IP and not yet public — which is most of what staging buys
for a fraction of the cost. **What would change my mind**: NEW-54 wanting a public callback endpoint
anyway, or the other three products needing the same machine — at which point one staging host serves
four products and the arithmetic reverses.

**Done means** a decision recorded either way, and — if the answer is the DNS-hold — a written sequence
for it, because *"deploy but do not point DNS"* is an operational procedure and `deploy-prod.sh` has
never been run at all, let alone in a mode nobody has described.

---

## NEW-56 — "there is no scheduler in this estate" is false, and nine decisions rest on it · READY

**Phase 3, and it is a documentation defect with a compliance edge rather than a code defect.** Found
2026-09-15 while verifying NEW-47's premise; `decisions.md` **D91**.

**What was claimed, in ten places.** *"There is no scheduler in this estate"* — `docs/decisions.md` at
nine sites (D17's meeting-link reveal, D86, the retention answer, `Dispute.dueBy`, the slot sweep, the
brokerage figure, and three more), `docs/healthconnect-marketplace.md:325`, and — worst, because it is
outward-facing — `docs/processing-record.md` §3 and `docs/privacy-notice.md` §7, **both drafts destined
for counsel**. NEW-52 repeated it on the day it was written.

**What is true, measured twice — source and runtime:**

| | |
| --- | --- |
| `@EnableScheduling` | Active in **all five** services, `config/AsyncConfiguration.java`, `@Profile("!testdev & !testprod")` — so on for `dev`, `test` and `prod` alike |
| `ThreadPoolTaskScheduler` thread | Live in **all five** quality containers, read at `/proc/1/task/*/comm` |
| `@Scheduled` methods in main source | **Two.** `booking/…/OutboxPublisher.java:69`, `fixedDelayString` 2 s — **the estate's entire Kafka delivery path**. And `gateway/…/UserService.java:291`, `cron = "0 0 1 * * ?"` — deletes unactivated accounts after 3 days |
| Has the deletion run? | **Yes.** Quality's gateway started 2026-09-11T21:22Z; four 01:00 UTC boundaries have passed |

**The estate has run a scheduler as its core event path for its entire life** while ten documents said
it had none. That is this repository's own signature failure — a confident claim nobody measured,
repeated until it became load-bearing — and CLAUDE.md is largely a catalogue of the same shape.

**What is already done, and is not this item.** The two compliance drafts are corrected (with a new
`processing-record.md` §3.1 and `privacy-notice.md` §7.1 recording the 3-day deletion as processing in
its own right, because it destroys a real person's data on a timer and had never been written down).
NEW-52's title and costing are corrected. **D91 is the amendment of record** — `decisions.md` amends
everything else by house rule, so the nine historical citations are not edited in place.

**What is left, and why it is not just a find-and-replace.** Each of the nine cited the absence as the
*reason* for a choice. Re-read them and record, per decision, whether the conclusion survives a
falsified premise:

- **D17 / D86 / NEW-45 — the meeting-link reveal.** The likeliest to be genuinely re-openable: *"there
  is no scheduler anywhere in this estate, so the honest cheap version is to compute visibility at read
  time."* Read-time is still probably right (it needs no delivery guarantee and cannot double-send), but
  it should be right **on its own argument** rather than on a false one.
- **D53 / D51's `earned_on`, the slot sweep, `Dispute.dueBy`, the brokerage figure** — each said "no
  scheduler" as shorthand for "no sweep". Confirm that is all each meant.

**Done means** the nine are triaged with a one-line verdict each, none silently, and no document in the
repository asserts the absence any more. **The likely outcome is that every conclusion stands** — which
is the good case and still has to be established rather than assumed, because the one that does not is
the one worth finding.

**Not blocked.** No decision, no outside fact.

---

## NEW-57 — the identity gauges can be overwritten by a STALER reading, and the javadoc says they cannot · DONE (D93)

> **CLOSED 2026-09-16 — PR #70, merged as `32216fd`** (`e644cce` + `47b5514` + `d43a299`).
> Both counts now live in one immutable `AccountSplit` record behind a single `AtomicReference`, and each
> observation takes a **sequence before it queries**, so `setAccounts` is a CAS that refuses anything not
> newer. **The shape chosen was the versioned write, not the queue** — this item costed both, and the queue
> needs a sink plus a completion signal per enqueued observation and would make an explicit refresh wait on
> a Mongo round trip it does not need.
>
> **Mutation-tested, not asserted:** the pre-fix publication behaviour was reproduced behind the post-fix
> signature and **4 of the 5 new tests went red**, the tear test included — so a concurrent scrape genuinely
> did see a mixed pair. Restored byte-identical after every probe.
>
> **Two review rounds found two more claims of mine outrunning their code**, and the second found a *third*
> hollow test. All of it is in D93; the short version is that the fix's own new guarantee was false on the
> error path, the narrowed tear paragraph asserted one direction when the error is bidirectional, and the
> test written to pin the empty-count guard passed identically with and without it.
>
> **The observation tear is a stated residual, not fixed → NEW-59.**

**Found 2026-09-15 by CI on PR #69**, a branch whose entire diff is four `docs/*.md` files — so it is
**pre-existing on `main` at `d9b7365`** and was introduced by D84/D85. `GatewayIdentityMetricsIT` is
byte-identical between the two branches (0-line diff), and no Java test reads a markdown file. First
observed failure; D85's own PR was 6/6 green, which is what a timing-dependent flake looks like.

```
GatewayIdentityMetricsIT.gaugesPartitionTheCollection:182
expected: 6L
 but was: 2L
```

`expected` is `userRepository.count()`; `but was` is `activatedBefore + dormantBefore`. **The gauges
summed to 2 while the collection held 6** — they were holding a reading taken when the collection had
two documents in it.

### The mechanism, and it is not the test's fault

`IdentityMetricsRefresher.start()` runs `Flux.interval(Duration.ZERO, interval).concatMap(tick ->
refresh())`. **`concatMap` serialises the timer against itself and against nothing else.** An explicit
`refresh()` — which `GatewayIdentityMeters`' own contract invites, and which the test uses instead of
sleeping — runs *concurrently* with whatever the timer already has in flight, and `setAccounts` is
last-writer-wins:

1. the first tick fires at **`Duration.ZERO`**, at context startup, against a cold Testcontainers Mongo
   holding only `admin` and `user`. Its `Mono.zip` of two counts is slow;
2. earlier methods in the class add four accounts;
3. the test calls `refresher.refresh().block()`, which reads 6 and writes 6;
4. **the first tick's zip finally completes and writes 2 back over it;**
5. the test reads the gauges and gets 2.

**The `Duration.ZERO` first tick is deliberate and should stay** — D84's argument is that a gauge
reading `-1` for the first minute of every deploy is a dashboard that looks broken on every deploy, the
same call the SSE heartbeat makes. It is the *unserialised* explicit refresh that is wrong, not the
eager first tick.

### Two javadoc claims stronger than the code, which is the actual defect

This is the house failure mode in a feature I wrote three days ago, twice in one class pair:

- **`refresh()`** — *"a caller … can drive exactly one observation and know when it has landed"*. True
  of **landing**, false of **standing**: nothing stops a slower concurrent observation landing after it
  and replacing it. The `Mono` times your own write and says nothing about which write survives.
- **`setAccounts`** — *"both numbers are set from one observation so a dashboard cannot add them and get
  a total that existed at no single moment"*. The pair is genuinely zipped, so the *intent* holds, but
  the two `AtomicLong.set` calls are sequential — a reader landing between them adds one number from
  this observation to one from the last. The exposition is scraped concurrently, so that reader is real.

### Production impact is small, and that is a reason to be careful rather than relaxed

The interval is **60s**, so overlap in production needs a Mongo count slower than a minute: essentially
never, and when it happens the cost is one stale dashboard reading. **Do not use that to justify fixing
only the test.** The claims above are wrong regardless of how often they bite, and a gauge that can
silently regress is exactly the sort of thing that is discovered during an incident.

### Done means

Serialise every observation, and make the two javadocs true or delete them. Two candidate shapes, both
small — **pick one and record why**:

- **one queue.** Explicit refreshes go through the same `concatMap` chain as the timer (a `Sinks.many`
  trigger the loop consumes), so `refresh()` enqueues and nothing ever overlaps. Keeps the returned
  `Mono` meaningful and makes the javadoc true as written. Note the sink rules from D79 — `Sinks.many(`,
  never `unsafe`;
- **versioned write.** Stamp each observation and write only if newer. Cheaper, but leaves two
  concurrent Mongo queries running for no benefit.

For the torn pair, hold both numbers in **one** immutable object behind a single reference, so there is
no window between them.

**And fix the test properly rather than around it.** Asserting a partition was the right instinct — it
is why this failed loudly instead of passing on a coincidence — but it still races a live background
loop. Once refreshes are serialised, `refresh().block()` genuinely means "the standing reading is mine".

**Not blocked.** No decision outside the repository, no outside fact.

---

## NEW-58 — what "visible while unverified" requires, and the one state it leaves wrong · READY

**Opened by `decisions.md` D92 §3**, ratified 2026-09-16. This was not work before that decision: desk
enrolment made every one of these unnecessary, because nothing published until a person had looked at it.
Self-service signup with immediate visibility makes them the conditions on which that choice is safe.

**Phase 2, alongside NEW-49 — not after it.** Enrolment shipping without these is the window where the
harm is available.

### What is already safe, so it is not in scope

`ProWorkspaceResource.saveProfile` omits `verification`, `insured` and `policeClearance` from
`SaveProfile`, with the reason in place: *"a professional who can set their own verified flag is a trust
chain with a hole in it."* A self-enrolled professional cannot claim to be verified, insured or
police-cleared. **That is what makes this decision tenable and it needs no work** — and it must not be
"tidied" into the profile body by anyone adding fields there later.

### 1. Self-declared fields must say that they are self-declared

Two public fields are free text the professional sets, measured at `ProWorkspaceResource:187` and `:194`:

| Field | What a self-enrolled person can put in it |
| --- | --- |
| `Credential.label` | *"DONA International Certified Birth Doula"*, an association registration number, a licence number — anything |
| `yearsPractising` | any integer |

Under desk enrolment nobody could; under self-service anybody can, and this platform serves it on a
public health-services domain. **A badge on the profile does not qualify the credential** — the reader
sees a named person with a named qualification. Until `VERIFIED`, credentials and years must render as
**self-declared**, at the field rather than at the page.

This is the same category `VerificationReviewResource` was deleted for — *"a forged verification is a
public claim about a real person"* — arriving through a door that is now deliberately open, which is why
the mitigation is presentational rather than a refusal.

### 2. The badge goes on every surface, not the profile alone

Browse card, Discover, search result, **and the booking confirmation**. A card in Browse with no
qualifier asserts a bookable professional; the customer who books from it never opens the profile.

### 3. A takedown route for impersonation

Nothing provides one — no screen, no endpoint — because all eighteen listings were seeded and no real
person could be misrepresented by one. Self-service signup means a person can now have claims published
about them by somebody else. **Needs a route before enrolment opens**, and it need not be a product
feature: a documented contact that reaches the desk, with `VerificationState.SUSPENDED` as the lever, is
enough for v1. What is not enough is nothing.

### 4. Signup abuse limits

Registration is `permitAll` (NEW-47), and one account creating many professional listings is now a
supply-side spam vector rather than a theoretical one. Rate-limit both, and cap listings per account.

### 5. `SUSPENDED` in the default listing — a DEFECT, not a consequence

**Separated deliberately, because "visible while unverified" was decided and "visible while suspended"
was not.**

`VerificationState` is `UNVERIFIED, PENDING, VERIFIED, SUSPENDED`, and `jdl/catalog.jdl:66` says the
fourth exists *"because suspension has to be distinguishable from never-verified"*. But `verifiedOnly` is
**opt-in** — measured: the default listing returns all 18, `verifiedOnly=true` returns 16 — so **a
professional whose verification was taken away is served in the default listing**. That is worse than an
unverified one: it is a trust signal this platform granted and then withdrew, and the withdrawal is
invisible to a reader.

D33 fixed the adjacent half — a `SUSPENDED` professional was publishing a `verifiedOn` date, so the badge
outlived the verification. **The listing half is untouched.**

**Nothing has ever exercised it: there is no `SUSPENDED` row in either estate**, so every statement here
is read from code rather than observed. Write the row first, watch what is served, then fix it — in that
order, or the fix is verified against a reading of the same code that produced the defect.

### Done means

The four protections shipped with NEW-49 rather than after it, and the `SUSPENDED` listing decided
explicitly — suppressed, or served with an unmissable withdrawal notice — with a seeded or hand-written
`SUSPENDED` row proving which, since that state has never existed anywhere.

**Not blocked.** The decision is taken; this is its condition.

---

## NEW-59 — the account gauges are PUBLISHED as one observation but COUNTED as two · READY

**Opened by NEW-57's review**, 2026-09-16, `decisions.md` **D93**. NEW-57 fixed the *publication* tear; this
is the *observation* tear, which survives deliberately and is stated in the source rather than hidden.

`IdentityMetricsRefresher.refresh()` issues **two independent counts** joined by `Mono.zip`. `Mono.zip`
subscribes to both up front and they are separate commands on the driver, so neither is guaranteed to run
first — and an account that activates while they are in flight is counted wrongly **in either direction**:

```
is(true) snapshots BEFORE the flip, ne(true) AFTER  → counted by neither → sum = N − 1
ne(true) snapshots BEFORE the flip, is(true) AFTER  → counted by both    → sum = N + 1
```

**The first version of that paragraph claimed only `N − 1`**, which mattered because the paragraph exists to
warn the next test author: `sum <= collectionSize`, written on its strength, is flaky in exactly the
direction it called impossible. Corrected at review — and it is the reason this item says "in either
direction" twice.

**Bounded and self-correcting.** Never a regression, gone at the next tick, and it needs a registration
inside a two-query window. That is why it is an item rather than a fix in NEW-57.

**The fix is one aggregation grouping on `activated`** instead of two counts — and it carries two traps
worth naming before anyone starts:

- **null and absent must fold into the not-activated bucket.** The current query is `ne(true)` and *not*
  `is(false)` precisely so the two counts partition the collection even for a document with no `activated`
  field. A `$group` keyed on that field puts such documents under a *third* key, so a naive grouping
  reintroduces the exact gap `ne(true)` exists to close;
- **an aggregation can complete empty** on an empty collection, where `count` emits exactly one element or
  errors. NEW-57 added `.single()` at each count for that reason — measured: `Mono.zip` completes
  **normally** on an empty source, skipping `doOnNext` *and* `doOnError`, so nothing is published and
  nothing is logged. `anEmptyCountIsTreatedAsAFailure` is red without the guard; keep it red-able.

**It would also make `GatewayIdentityMetricsIT`'s sum assertion unconditionally sound.** Today that holds
only because the integration tests run sequentially, and the class javadoc says so.

**Not blocked.** No decision, no outside fact.

---

## NEW-60 — the activation link points at a page nothing serves · READY

**Opened by NEW-47's own walk, 2026-09-17**, `decisions.md` D94 §5. Small, and it is the last step
between "the mail arrives" and "a person can use it".

`templates/mail/activationEmail.html` composes the link as `${baseUrl}/account/activate?key=…` and
`passwordResetEmail.html` as `${baseUrl}/account/reset/finish?key=…` — **frontend routes**, which is
the right convention and is what `JHIPSTER_MAIL_BASE_URL` names. Note that the second one is not the
API's path with the origin changed: the API is `POST /api/account/reset-password/finish`, so the two
differ by more than a prefix and a screen has to know both. This estate has no frontend (**NEW-48**), so the origin an
operator can honestly put there is the API's own edge, where `/account/activate` matches no route and
answers **401**.

**401 and not 404, and the first draft of this item said 404 twice** — reasoned from "no such path"
rather than measured, and corrected by NEW-47's review. Reactive Spring Security *denies* an exchange
that no `authorizeExchange` rule matched (D74 measured the same default from the other direction), so
what a person following the link meets is a credential challenge for the page that exists to let them
authenticate. That is worse than a 404 for a customer and identical for a prober.

**Measured during NEW-47's walk**, against a live gateway with a real catcher:

| | |
| --- | --- |
| the link in the message | `http://127.0.0.1:18907/account/activate?key=l7WERmeOHkbVw8FG6znc` |
| following it, as a person would | **401** — not 404. Reactive Spring Security denies an exchange no `authorizeExchange` rule matched (D74 measured the same default), so the estate answers a *credential challenge* for a link it told somebody to click |
| `GET /api/activate?key=<the same key>` | **200**, and the account is activated |
| `POST /api/authenticate` after that | **200 with a token** |

**The 401 rather than a 404 is the part to keep**, and it was written here as a 404 from reasoning
before it was measured: a person following the link is asked to authenticate in order to reach the
page that exists to let them authenticate. There is no wording of that a customer can act on.

So the lifecycle works and the mail is one hop short of being usable by a person who is not holding a
terminal. Nobody is harmed today — no estate can send a message at all until a provider is chosen
(D90 §7) — which is exactly why this is an item rather than a patch: the first person to receive one
of these should not be the one who finds this.

**Why the generated template was NOT edited in NEW-47.** Pointing it at `/api/activate` would make
the link work today and be wrong the day NEW-48 lands: a person clicking it would get a bare `200`
with an empty body and no page, and JHipster's convention — which the frontend will implement — is the
SPA route. The template is also a generated file, so the edit would be discarded by a regeneration
while looking permanent.

**The fix, therefore, is a screen and belongs with phase 2**: NEW-48 stage B or C serves
`/account/activate` and `/account/reset/finish` — the two routes the shipped templates actually
compose, measured out of two real messages rather than read off the templates — calls
`GET /api/activate` and `POST /api/account/reset-password/finish` behind them, and
`JHIPSTER_MAIL_BASE_URL` then names the frontend's origin, which is what it was always for.

**Until then, two things are true and both are written down where they will be read.**
`deploy/prod-server/secrets.env.example`, `deploy-prod.sh`'s hint for `HC_MAIL_BASE_URL`,
`quality/compose.yml`, `application-prod.yml` and `deploy/prod-server/README.md` all say that the link
answers 401 and that the key in it activates the account through `GET /api/activate`. An operator
activating an account for somebody does it with the key out of the link.

**Five of those said 404 for one commit**, because the correction reached this item's table and
nothing else — the house failure mode, inside the commit that made the measurement. `deploy-prod.sh`'s
copy is the one that mattered: it is **printed to a production operator** during preflight, and it is
byte-embedded in the spec's Appendix B, where `sync-appendices.sh --check` was **green over the wrong
sentence in both places**. That check verifies byte-identity, not truth, and this is the first time
that limit has cost anything.

**Not blocked.** It needs NEW-48 to exist, and nothing else.

---

## NEW-61 — an unactivated login is identifiable by anyone, with no password, and answers 500 to its owner · READY, and a decision with it

**Opened by NEW-47's walk, 2026-09-17; corrected the same day by NEW-47's review, and the correction
is the item.** The first version of this entry called the disclosure *"a narrow oracle (it needs the
password)"*. **It does not need the password**, which makes it an unauthenticated enumeration oracle
rather than a curiosity — and the first version therefore invited the architect to weigh option 2's
cost against a status quo it described as narrower than option 2, when the status quo is **wider**.

**The mechanism, read in code rather than inferred from the statuses.**
`DomainUserDetailsService.createSpringSecurityUser` throws `UserNotActivatedException` **inside the
lookup's `.map()`**, and `UserDetailsService.findByUsername` is called by the authentication manager
*before* `BCryptPasswordEncoder` sees the request. So the outcome is decided by the lookup alone and
the supplied password cannot affect it. `ExceptionTranslator:111-118` then maps every
`AuthenticationException` to the title *"Unauthorized"* and the detail *"Invalid credentials"* — under
a comment reading *"Ensure no information about existing users is revealed via failed authentication
attempts"* — while taking the **status** from `toStatus(ex)`, and `UserNotActivatedException` is a
custom `AuthenticationException` with no `@ResponseStatus`, so it falls through to **500**.

**Measured live, twice by two people, on fresh accounts** (`POST /api/authenticate` unless noted):

| what was sent | status | detail |
| --- | --- | --- |
| unactivated login, **wrong** password | **500** | `Invalid credentials` |
| unactivated login, **right** password | **500** | `Invalid credentials` |
| unactivated account probed **by its email address**, any password | **500** | `Invalid credentials` |
| activated login, wrong password | 401 | `Invalid credentials` |
| a login that does not exist | 401 | `Invalid credentials` |
| an email address that does not exist | 401 | `Invalid credentials` |
| activated login, right password | 200 | a token |
| `POST /api/account/reset-password/init`, **known** address | 200 | — |
| `POST /api/account/reset-password/init`, **unknown** address | 200 | — |

**Three facts follow, and only the first was in the original entry.**

1. **A customer meets a 500 that says their password is wrong.** Somebody who registers, does not
   click the link, and tries to sign in is told the platform is broken *and* that their credentials
   are bad. Both are false, and this is the very next thing that happens after NEW-47's path.
2. **Anyone can enumerate unactivated accounts, by login or by email, with no credential at all** —
   `500` means "registered here and never activated", `401` means "not registered". Registration is
   open (`permitAll`), so the two together are a way to ask "is this person on this platform"
   about any address, at the rate limit's ceiling. The comment above the mapping claims this cannot
   happen; for one of the four buckets it is measured false.
3. **The estate already closed the same surface one endpoint along, deliberately.**
   `/api/account/reset-password/init` answers **200 whether or not the address exists** — JHipster's
   own choice, and the precedent for what this platform's answer to the tradeoff has been so far.

**The decision, which is not engineering's.** One line either way; what to *say* is the question, and
the review's correction moves the recommendation:

- **401 with `Invalid credentials`, identical to the other three.** Closes the enumeration oracle,
  matches the reset path's existing convention, and costs the customer nothing they did not already
  have — they were being told "invalid credentials" anyway, just with a 500 beside it.
  **Recommended.** The original entry recommended this only as "the floor"; with the oracle measured
  as unauthenticated, the disclosure argument now points the same way as the simplicity argument
  rather than against it.
- **A distinct answer — 403, or 401 with `account not activated`.** The customer can act on it and
  support load drops. It no longer "publishes the oracle", because the oracle is already open: what
  it does is make it *legible*, which is a smaller step than the first version of this item implied.
  Still a deliberate disclosure, and it should be taken as one rather than inherited from a status
  code nobody chose.
- **Either, plus "send me the link again".** The genuinely useful version. It needs a screen and its
  own rate limit, so it is NEW-48's; and if it lands, option 2's disclosure arrives with it anyway,
  because a resend form that only works for registered addresses is the same oracle wearing a
  button.

**A test has to come with it, and the shape matters.** Assert the status **and** — if option 1 is
chosen — that all four failure modes are indistinguishable from outside, including the by-email
probe, which is the row this item was missing. `GatewayIdentityMetricsIT` already measures that
`UserNotActivatedException` survives the authentication manager (D84), so the platform can keep
counting this outcome whichever answer is chosen — but that means **not** "fixing" this by converting
the exception earlier in `DomainUserDetailsService`, which would take the metric with it.

**Not blocked.** It needs the architect to pick one of the three.

---


## NEW-62 — `npm run prettier:format` rewrites seventeen files it was not asked to, every time · READY

**Opened 2026-09-17 at NEW-47's review**, which is the point: the tax had been paid by hand in three
successive rounds and the only record of it was in reports nobody will read again.

`CLAUDE.md` tells every package to run `npm run prettier:format` after editing Java, because prettier
formats Java here. Run it in `gateway/` today and it reformats **17 files this package never
touched** — hand-written classes and tests from D74, D79, D84 and D85:

```
config/{InternalApiSecurityConfiguration,MarketplacePublicRouteConfiguration,
        PaymentWebhookRouteConfiguration,dbmigrations/InitialSetupMigration}.java
management/GatewayIdentityMeters.java   service/IdentityMetricsRefresher.java
web/rest/InternalCustomerContactResource.java      + 10 test classes
```

The changes are prettier-java's method-chain rules, not anybody's mistake:
`Counter\n.builder(...)` becomes `Counter.builder(...)`, and a wrapped `new AtomicReference<>(...)`
is joined onto one line.

**The versions are pinned, which is what makes this worth an item rather than a shrug.**
`package.json` names `prettier` **3.9.5** and `prettier-plugin-java` **2.10.2** exactly, and
`./node_modules/.bin/prettier --version` reports 3.9.5 — so this is not a floating dependency that
will settle. **There is no committed lockfile** (`gateway/package-lock.json` is untracked and
`node_modules/` is gitignored), so what actually resolves is whatever npm picks for the plugin's own
transitive `java-parser` on the day, and that is the most likely source of the drift. **CI does not
run `prettier:check`**, so nothing has ever been red about it.

**What it costs, measured over three rounds of one package**: every `prettier:format` produces a diff
across four earlier packages' files, which has to be restored by hand before committing — and the
failure mode if somebody does not notice is a commit whose scope silently spans five packages,
which is exactly what the review process looks for and what `git add -A` would have shipped.

**Three answers, and the cheapest is not obviously right.**

1. **Commit a lockfile and reformat once.** `npm install` already writes one; committing it pins the
   transitive parser, and one commit then brings the tree to what the pinned prettier produces. Cost:
   one 17-file formatting commit touching four packages' files, which is ugly in `git blame` for
   exactly the files whose comments are load-bearing here. Benefit: the instruction in `CLAUDE.md`
   becomes true, and `prettier:check` could then join CI.
2. **Add `prettier:check` to CI without reformatting.** Immediately red, so it is only option 1 with
   the order reversed.
3. **`WON'T`, and say so in `CLAUDE.md`.** Tell the next package to run prettier and restore
   everything it did not edit — which is what three rounds have done already. Cost: the tax
   continues, and it is a tax on attention rather than time: the restoring is mechanical, noticing is
   not.

**Recommended: 1, in a commit of its own that touches nothing else**, so the formatting churn is
separable from any package's diff and reviewable as "only whitespace and line joins". It is not this
package's to take — it changes four earlier packages' files and adds a committed lockfile, which is a
dependency-management decision for the repository rather than for a backlog item.

**Not blocked.** It needs the architect to pick one.

---

## NEW-63 — nothing cross-checks preflight's required keys against the compose file's `:?` variables · READY

**Opened 2026-09-17 by NEW-47's fourth review round**, which found it as a residual and explicitly
declined to have it built in that package. Small, derived, and it guards a defect this estate has
already had once.

`deploy-prod.sh`'s preflight checks a list — `SECRET_KEYS` + `CONNECTION_KEYS` — by name on the host
before it touches the stack. `docker-compose.prod.yml` refuses to interpolate without its `:?`
variables. **Those are two hand-maintained lists of the same requirement, and nothing compares them.**

**Measured today, and they agree** — which is why this is a guard rather than a fix:

| | |
| --- | --- |
| preflight requires | **15** — `SECRET_KEYS` 3 + `CONNECTION_KEYS` 12 |
| the prod compose requires | **15** distinct names — 14 `HC_*` plus `JWT_BASE64_SECRET` |
| the two sets | **identical**, name for name |

(The nineteen the review counted is `:?` *occurrences*: the signing key and the pepper appear in
several services. Occurrences are not the subject; names are.)

**Why it matters in each direction**, because they are not symmetrical:

- **A `:?` variable with no preflight entry is the 2026-09-05 defect** — the one `deploy-prod.sh`'s own
  header records as *"THE PREFLIGHT CHECKED TWO OF THE ELEVEN"*. The deploy passes preflight,
  **overwrites `.env` and rotates `.env.previous`**, and then dies at `up` on the compose file's own
  message, leaving the host half-rolled over a variable nothing in the pipeline ever supplied.
- **A preflight entry with no `:?`** is milder and still wrong: a deploy refused over a value nothing
  reads, which is how a required variable becomes a placeholder somebody invents.

**And NEW-47's round three is the proof that the lists drift:** three keys were added to both places
in one commit, and a *third* copy of the same list — `host-probe-attribution.sh`'s fixture — was
missed, turning CI red on a correct branch. That fixture is derived now; these two are not.

**The shape, and it is about fifteen lines.** In `build.yml`'s `consistency` job: lift the two arrays
from `deploy-prod.sh` exactly as `host-probe-attribution.sh` already does, extract
`\$\{([A-Z_]+):\?` from `docker-compose.prod.yml`, and require the two **sets** to be equal, printing
both counts. Refuse an empty extraction on either side — a check that reads nothing must not pass.

**Two things to decide while writing it, not before:** whether `deploy/prod-server/compose.yml`'s own
`:?` variables belong in the comparison (it is a second compose project on the host, installed once,
and its variables are read from the same `secrets.env` — so probably yes, as a third set), and
whether `secrets.env.example` should be held to the same list (it is the operator's copy of it, and
it has been stale before).

**Not blocked.**

---

## NEW-64 — a payout batch can be owed *back*, and nothing says what a `FAILED` one means · READY, and two decisions with it

**Opened 2026-09-17 by NEW-51 / D95**, which implemented a defensible reading of each of these and
argued it at the site rather than stopping. Neither is a defect today; both are answers somebody
other than the implementer should confirm, and the second one is a door deliberately left shut.

**One: a batch whose rows sum to zero or below is still written — and CANNOT be settled.** D95 §6 and
**§13**. A period holding a reversal of an earning that was already paid in an *earlier* period sums
negative, and that is not something a desk can transfer.

| | |
| --- | --- |
| **as built** | the batch is created in `OPEN`, logged at **WARN** naming it, and **`settle` refuses it** (`netMinor <= 0`, message naming this item) |
| **the alternative for the open half** | refuse to create it — and then that reversal stays `payout is null` **for ever**, silently discounting some later, unrelated period |
| **the third option, which is the real one** | a status or a field that records what is owed *back*, so the carry-forward is a row rather than an operator's memory |

**The argument for what was built is visibility, not correctness**: a batch nobody can pay is on the
screen; a reversal that quietly shortens next quarter is not. **The cost of the third option** is a
JDL change with a Liquibase changelog behind it, which is why it was not taken inside an item. **What
would settle it**: whether the brokerage actually ever expects to reclaim money from a professional,
or whether in practice a reversal is always absorbed against a later positive period.

> **REVIEW CLOSED THE SETTLE HALF as an interim door, 2026-09-17 — D95 §13.** As first built, such a
> batch was **settleable against a bank reference**, and the harm compounds rather than staying local:
> an operator clearing the `OPEN` list settles a −15,000 batch with the next period's reference, **the
> debt then reads as paid *to* the professional**, the following period settles in full, and 15,000 is
> overpaid with every record internally consistent — the machine's whole account of that debt having
> been one WARN line and an `OPEN` row nobody must tidy.
>
> So this item is **no longer a live overpayment route**; what is left of it is the disposal question.
> The refusal is `<= 0` rather than `< 0` because a zero-net batch had no transfer either, and it
> **never needs to be `PAID`**: it holds its rows, so nothing is carried forward, and `OPEN` is the
> honest state. **Refusing is recoverable and settling is not** — this decision can open the door, and
> a `PAID` row stating money moved cannot be un-said, since there is no endpoint to delete one.
> Whatever is ratified here should say what such a batch finally *becomes*.

**Two: `PayoutStatus.FAILED` has no transition into it, and `settle` refuses one saying so.** D95
§7.2. The undecided question is **what happens to a failed batch's ledger rows** — return to
`payout is null`, or stay attached:

- **return them** and a batch that failed for a reason unrelated to the rows (a wrong account number)
  can be re-run cleanly — and if the transfer had in fact gone out, the rows are payable **twice**;
- **keep them attached** and no row is ever paid twice — and a batch marked `FAILED` by mistake strands
  those earnings permanently, because nothing else can ever claim them.

Both readings are reachable by one line, and **a transition written on a guess decides it silently, in
the direction that is either a double payment or a professional who is never paid.** So nothing in the
estate puts a batch there; `IN_PROGRESS` is equally unreachable, and both were before D95 too.

**Done means** an answer to each recorded in `decisions.md`, and then: for the first, either nothing
(ratifying what is built, plus whatever a zero-or-below batch finally *becomes*) or the third option
with its changelog; for the second, the transition plus whichever row treatment was chosen, with a test
that goes red if the other one is implemented.

**AND THE TWO-THREAD TEST GOES IN WITH THE `FAILED` TRANSITION — this is where that sentence lives**
(D95 §16, and the reviewer and the architect agree it was right to defer). Both of D95's locks —
`SettlementLedgerRepository.unsettledBetween` and `PayoutQueryRepository.findByReferenceForUpdate` —
rest on documented PostgreSQL semantics plus a structural pin, with **no test anywhere driving two
concurrent transactions**. That was proportionate while `PayoutRun.open` is the estate's **only writer
of `payout_id`**: one contender, and the harm is two batches over one set of rows.

**A `FAILED` transition that returns rows to unsettled writes `payout_id = null` from a second place**,
and at that point the interleavings multiply and no amount of reasoning about a single writer carries
over. So the item that adds the second writer is the item that owes the test: a class that is **not**
`@Transactional`, with its own transaction management, committing into the database every other
integration test in that JVM shares — a different kind of test from anything in this repository today,
which is the other half of why it was not written speculatively.

**Not blocked**, and not urgent while no estate has settled anything — production has never been
deployed and there is **no dev estate at all** (NEW-31, cleared 2026-09-10, re-verified 2026-09-17:
zero containers, zero volumes, no compose project), so no batch exists anywhere to be owed back or to
fail. **The quality box is the exception to that sentence as of 2026-09-17** and it is worth naming
here rather than leaving the reader to infer it: it holds `PAY-202602-p1-01`, `PAID`, 31 ledger rows
attached and 235 unsettled — the first payout batch this platform has ever recorded. So "no batch
exists anywhere" was true when this was written and is now false of one estate.

---

## NEW-65 — a deliberate business refusal logs at ERROR with its arguments, in all five services · DONE (D97)

> **CLOSED 2026-09-18 — `decisions.md` D97. A deliberate refusal is a WARN, it names the method and
> the reason this estate composed, and it echoes nothing it was handed.**
>
> The answer is this item's **middle** candidate, and it needed no decision from a person: **D44
> already settled the structurally identical question** one service along — compose the message at the
> boundary, send the outside-authored string to the log at WARN — and `CLAUDE.md` records that as house
> style. The two rejected positions are argued in D97 §2 rather than dismissed, and one of this item's
> three reasons for *"remove the advice"* is **false, measured**: `ExceptionTranslator` has exactly one
> log call in all five services and it is `LOG.debug`, so it reports to the **caller** and not to the
> log. With the advice deleted a desk refusal would leave no record anywhere in the estate.
>
> **TWO OF THIS ITEM'S OWN CLAIMS WERE WRONG, and the first changes where the fix had to go.**
>
> | this item said | measured |
> | --- | --- |
> | the defect is `:113` | `:113` is **one of two** ERROR lines. `logAfterThrowing` is a separate advice on the *same* pointcut, logs **every** `Throwable` at ERROR, and is the only one an `IllegalStateException` reaches. Driven against a real Spring AOP context: an `IllegalArgumentException` refusal produced **2 ERROR** lines (the second carrying `[PAY-202602-p1-01, GCB-TRF-99881726]`), an `IllegalStateException` refusal **1**. Since most of D95's desk throws `IllegalStateException` — `NotSettleable`, `NothingToSettle`, `NotPayableYet`, `MixedCurrencies`, `LedgerDoesNotAddUp`, `NoTermsInForce` — **a `:113`-only fix would not have kept the ERROR count at zero.** Both advices are WARN now |
> | *"there is no profile gate on the advice, so this fires on `prod` exactly as on `dev`"* | There **is** one, a level up: `LoggingAspectConfiguration` declares `@Profile(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)` on the `@Bean` in all five services, and `spring.profiles.group.prod` is `[kafka]`, so **`prod` never creates the aspect at all**. The disclosure was never a production disclosure. It does **not** shrink the item: the zero-ERROR premise is a *quality* measurement and quality runs `dev,test` (read off the container: `activeProfiles: ["secret-samples","kafka","api-docs","dev","test"]`), so the aspect is live on exactly the estate the argument rests on |
>
> **The premise was re-measured on the estate rolled to `1ac77c7` at 09:54 and was stronger than when
> it was written**: `ERROR = 0` on all five with the OpenTelemetry agent **attached**
> (`-javaagent:/app/otel-javaagent.jar -Dotel.exporter.otlp.endpoint=http://otel-collector:4317` on
> every container's `JAVA_OPTS`), `WARN = 196` on payout as the positive control. Nothing alerts on it —
> `hc-market-rules.yaml` keys on 5xx rates, confirmed by reading both rule groups — so the loss was
> silent, which is why it needed a check.
> **AND BY 13:10 THE SAME DAY THE ZERO WAS GONE ON ALL FIVE** — 49/38/35/27/24, none of it an
> application fault and none of it this change: OTLP exporter failures against a collector that could
> not accept, plus Kafka listener errors while the box sat at load 200+. Nothing announced it. That is
> **NEW-70**, demonstrated rather than predicted, and it is why the ten javadoc copies this package
> ships assert the *argument* for the level rather than a live count.
>
> **What shipped.** Two `.warn` in `logAfterThrowing` and one in `logAround`'s catch, no `getArgs()`
> outside the two guarded `log.debug` lines; `LoggingAspectRefusalUnitTest` — a **new** file, so it
> survives the regeneration that undoes the aspect — byte-identical in all five; a row on `CLAUDE.md`'s
> regeneration table; and the **sixth verbatim-copy family**, the first whose main-source member is a
> generated file. That last point is why the CI step asserts a **property** and not only an identity:
> `--force` restores the same wrong thing in all five at once, and five identical copies of the defect
> satisfy any diff.
>
> **Gates — TWO SERVICES FULLY MEASURED, THREE BLOCKED BY THE BOX, and the difference is stated rather
> than averaged.** `clean verify` is green on **booking (291 unit / 148 IT)** and **gateway (103 / 140)**,
> both `BUILD SUCCESS`, modernizer clean, each **+6** on its baseline — the new guard test. **catalog,
> messaging and payout did not complete**, three times each, and the cause is the same every time and is
> not this change: `CucumberTest` fails to load its `ApplicationContext` because the Testcontainers
> `postgres:18.4` container never becomes ready. **Measured rather than assumed** — a throwaway
> `postgres:18.4` on this box took **120 seconds** to log *"database system is ready to accept
> connections"*, against Testcontainers' **60-second** default wait, with up to eight other agents'
> Maven builds running (load average 19-210 across the attempts). What *is* measured in those three:
> surefire ran to completion at **115 / 68 / 172** tests with **0 failures** and the only errors being
> `CucumberTest`'s two, **`LoggingAspectRefusalUnitTest` passed 6/6 in every one of them**, and
> modernizer reported 0 violations. What is **not** measured is their integration tests — surefire fails
> first, so failsafe never ran at all (`maven-failsafe-plugin` appears **0** times in each log). Treat
> those three as **unverified**, not as passing.
>
> The check was driven against
> **12 mutations locally and 19 assertions in CI** — `refusal-logging-level-test.sh`, which lifts the
> shipped step out of `build.yml` and runs it against a synthetic estate whose fixtures are the real
> files, and which **prints its own count on every run**: read that line, because this one was written
> as 17 and was 19 one review round later. The unit test was run **red first**: 4 of 6 failed against
> the unmodified aspect, the content case printing the defect in full — `Illegal argument:
> [PAY-202602-p1-01, GCB-TRF-99881726] in settle()`.
>
> **The check's positive control was wrong on its first draft and its own test caught it** — it greped
> for the bare name `logAfterThrowing`, which `logAfterThrowingRenamed` satisfies as a substring, so the
> ban ran against a file whose subject it could no longer recognise and printed `ok`. It matches the
> declaration now. Visible only because the mutation was applied to all five copies at once; with one
> copy mutated the copy diff fired and the run was red for the wrong reason.
>
> **AND A SECOND REVIEW ROUND FOUND THE OPPOSITE DEFECT IN THE SAME BAN.** slf4j 2.x's fluent
> `log.atError().setMessage(…).log()` contains no `.error(`, so the ban was widened to
> `\.error\(|atError` — free, because nothing in the estate uses the fluent API (measured: zero
> `atError`/`atWarn`/`atDebug` in any service's main sources). **Widening one side and not the other
> made the check refuse correct code**: the WARN assertion still matched the literal `log.warn(`, so an
> arm rewritten as `log.atWarn()…` was rejected with a message insisting it *"does not log at WARN"*
> when it plainly does. Caught by a **green-expected control** written in the same round, and it is not
> a lesser defect than a hole — a guard that refuses accurate code gets loosened by the next person who
> meets it, and what gets loosened is the ban. Both halves take both spellings, and the red `atError`
> case and the green `atWarn` case sit together because either alone is satisfied by a check that is
> wrong in the other direction.
>
> **Four expected fragments went stale in that same edit**, and the test went **red** rather than
> quiet: the refusals were reworded while four `expect_fail` cases still asserted the old text, so each
> refused correctly and the harness reported *"refused, but the message names neither the file nor the
> cause"*. The direction is the safe one, and the lesson is to **assert the cause and not merely the
> refusal** — the price of rewording a message is then a red test rather than a silent one.
>
> **ONE QUESTION SURFACED AND NOT TAKEN → NEW-70.** Nothing observes the zero-ERROR count — no CI step,
> no alert rule, no dashboard panel, no `quality/startup.sh --verify` assertion. The signal this package
> protects still only fires if somebody runs `docker logs | grep -c ERROR` by hand, which is how it was
> measured for D64, D73 and this item alike.
>
> The two questions the brief anticipated are **answered in D97 rather than deferred**:
> `IllegalStateException` travels `logAfterThrowing`, which is fixed, and the type-split that would
> have handled it separately is refused for misclassifying `BookingEventConsumer`'s deliberate wrap of
> every failure as `IllegalStateException`; and an `isWarnEnabled` guard is declined as cargo cult —
> the `isDebugEnabled` guards exist because DEBUG is off everywhere and those calls render
> `Arrays.toString(getArgs())` on every advised call, while WARN is on everywhere and the arguments are
> no longer rendered.

**Found 2026-09-17** while opening NEW-52, and it is **pre-existing and generated** rather than
anything a recent package introduced — which is the first thing to establish about it, because it
lands on a signal NEW-51 has just started moving.

**Verified at `payout/src/main/java/net/jojoaddison/aop/logging/LoggingAspect.java:113`**, and the same
line at the same number in **all five** services (`grep -rn "Illegal argument" --include=LoggingAspect.java`
answers five files, every one at `:113`):

```java
} catch (IllegalArgumentException e) {
    log.error("Illegal argument: {} in {}()", Arrays.toString(joinPoint.getArgs()), joinPoint.getSignature().getName());
    throw e;
}
```

**Two things are absent and both matter.** There is **no `isDebugEnabled` guard** — unlike the two
`log.debug` calls eleven and six lines above it in the same method, which are guarded — and there is
**no profile gate** on the advice, so this fires on `prod` exactly as it fires on `dev`. The pointcut
is `applicationPackagePointcut() && springBeanPointcut()`, so its subject is our own beans.

**So a refusal this estate makes ON PURPOSE is logged at ERROR, with the arguments that caused it.**
`IllegalArgumentException` is the ordinary spelling of "the caller asked for something that is not
allowed" throughout these services, and `Arrays.toString(joinPoint.getArgs())` renders whatever was
passed. A settle refusal put a **bank reference** in the log by this route. That is the disclosure
half, and it is the smaller half.

**The larger half is a signal this repository has written down as load-bearing.**
`quality/compose.yml:150` calls payout's zero-ERROR count *"the estate's one free signal"* and *"the
load-bearing half"* — the argument being D64/D73's: all five quality services have carried **zero**
ERROR lines across their entire life, so an ERROR line means something, and that is the only way an
unattached OTel agent or a dead collector is visible at all. **NEW-51 ships a desk that refuses by
design** (D95: a period past the payout lag, a batch netting zero or below, a `PAID` batch re-stamped,
a `FAILED` one settled) — every one of those an `IllegalStateException` or an `IllegalArgumentException`
depending on the site. The moment the desk is used, ERROR stops being zero **in normal operation**, and
the signal is spent.

**What bounds the harm.** No alert fires: `deploy/observability/hc-market-rules.yaml` keys on 5xx rates
and these refusals are 4xx, so nothing pages anybody. And no estate has exercised the desk in anger
yet. So the cost today is a *lost* signal and arguments in a log, not an incident.

**Done means** deciding what an ERROR is for in this estate and making the aspect agree with it. The
obvious repair — drop it to WARN, or guard it, or exclude `web.rest` — is **not** obviously right and
should not be applied without answering the question underneath: *is a deliberate refusal an error?*
Three candidate positions, and the middle one is probably right:

- **WARN with the arguments** — keeps the diagnostic, frees the signal, still prints the bank
  reference;
- **WARN without the arguments, naming the method and the exception message only** — the message is
  ours and is composed at the boundary (D44's rule, already the house style for provider prose), so
  it says what was wrong without echoing what was sent. This is the one that fits the rules already
  written down;
- **remove the advice** — it is generated, it duplicates what the exception translator already
  reports, and nothing in this repository has ever read one of its lines.

**It is a GENERATED file in all five services**, so whatever is chosen belongs in the regeneration
table in `CLAUDE.md` with the symptom spelled out, or `--force` puts it back in silence. Note the
asymmetry that makes this survivable to find late: the two `debug` calls beside it are guarded, so the
*enter/exit* tracing everybody worries about is already off at INFO — it is only the failure arm that
was left unguarded, which is why nobody reading the class casually would notice.

**Not blocked.** No decision from a person, no outside fact — but it does want the question answered
rather than the line edited.

---

## NEW-66 — `--dry-run` cannot run without production credentials, and ends by claiming the tag is live · READY

**Found 2026-09-17**, two defects in one command, both verified twice at the source. `deploy-prod.sh
--dry-run` is documented in `CLAUDE.md` as *"safe; prints everything, changes nothing"* and is the one
thing anybody runs **before** touching production. Neither defect changes anything on a host; both
attack the one command whose whole value is that it can be trusted.

**One — it dies without a registry token.** `deploy/deploy-prod.sh:556`:

```
  if (( DO_PUSH )); then
    [[ -n "$REGISTRY_TOKEN" ]] || die "registry credentials missing — set $CRED_HINT"
```

`DO_PUSH` defaults to `1` (`:179`) and `--dry-run` does not clear it (`:282` sets `DRY_RUN=1` and
nothing else), so the `die` is reached on a plain `--dry-run`. The `if (( DRY_RUN ))` branch that
prints *"would authenticate to $REGISTRY_HOST"* is at **`:562`** — six lines **below** the gate that
already refused. So reading a plan requires exporting a real production registry token, which is
pressure to put a live credential in a shell for a command that contacts nothing. **`--no-push` is the
way round** and is not documented as being necessary.

**The irony is the record worth keeping.** The comment at **`:558`**, immediately under that `die`,
documents fixing this exact pattern *at this exact login*: *"A tick here used to print under
--dry-run too, while the login it claims was skipped. That is false confidence in the one command
somebody runs BEFORE touching production."* The fix was applied to the **tick** and the **gate above
it** was left where it was — so the same command is still unusable for the same reason the comment
gives for caring.

**Two — a dry run ends by asserting the deployment is live.** `deploy/deploy-prod.sh:1376`:

```
  if health_gate deploy && smoke_test; then
    record_success
    step "Done"
    ok "HealthConnect $TAG live on $HOST via the '$CHANNEL' channel ($IMAGE_PREFIX)"
```

Under `--dry-run`, `health_gate` returns 0 at `:914` (`[dry-run] skipped`) and `smoke_test` returns 0
at `:1073` (same), so the branch is **always** taken; `record_success` is correctly guarded at `:1326`
(`(( DRY_RUN )) && return 0`) and contacts nothing. **So the defect is the claim, not the action** —
nothing is deployed, nothing is recorded, and the last line an operator reads is a green `✓` saying a
tag is live on the production host. This is D78/D80's subject one line further on: every refusal in
that file was taught to say which hop answered, and the success line was never asked the same question.

**Done means** `--dry-run` runs with no credential of any kind, and every terminal claim it prints is
true of a run that contacted nothing. Two small changes and one judgement:

- move the `REGISTRY_TOKEN` gate **inside** the non-dry-run arm, or skip it when `DRY_RUN` — and keep
  it fatal for a real push, which is the direction it exists for;
- guard the `Done` line, printing what a dry run actually established (*the plan resolved, the tag
  resolved, nothing was contacted*) rather than the deployment's sentence;
- decide whether `--dry-run` should imply `--no-push`. It probably should not: *"would push to X"* is
  part of the plan an operator wants to read, and the answer is to stop needing the token rather than
  to stop printing the intent.

**And whatever is done needs a test that runs the program**, not a grep. D80 established that parts
1-6 of `host-probe-attribution.sh` drive lifted functions and part 7 is the only one that **executes**
the file — and that three successive guards on this script were each one step short of the binding
because of it. A dry run that contacts nothing is precisely a property only execution can assert:
part 7 already stubs `ssh`, `scp`, `docker`, `curl` and `git` and already refuses a run that asked the
host nothing, so the scenario belongs there and the harness for it exists.

**Not blocked**, and it must be fixed **without ever supplying a credential to pass the gate** — the
gate being unpassable is the finding.

---

## NEW-67 — the erasure register cannot say WHY somebody was erased · READY, and a decision with it

**Surfaced by D96 / NEW-52 rather than found**, and it is a decision before it is work. The brief that
opened NEW-52 predicted a different version of this — *"`eraseCustomer` records an acting staff member,
and a sweep has no staff member, so what goes in that column is a decision"* — and **there is no such
column**: verified, `ErasedSubject` holds `pseudonym` and `erasedAt` and nothing else, `ErasureRun`
holds a fan-out attempt's outcome and no actor, and nothing on the erasure path reads `SecurityUtils`.
So that decision does not arise. The one underneath it does.

**The register is now written by two callers that mean different things.** Until D96 every row in
`erased_subject` was a data subject request that a person at the desk had identity-checked and acted on
(D40: erasure is desk-operated by decision, not self-service). Since D96 a row may also be the financial
retention period expiring on a timer. **The rows are identical** — an alias and an instant — so an
operator asked *"why was this customer erased?"* has nothing to read, and the two answers have opposite
implications: one is a right exercised, the other is a policy applied.

**Why it matters more than it looks.** `docs/processing-record.md` §6.3 already records that nothing
audits staff access; this is the same shape one step along, on the estate's one irreversible act. And an
erasure receipt is *the artefact filed against a legal request* (D31/D39) — if a subject asks whether
their request was honoured, a register that cannot distinguish their request from a clock cannot answer.

**The decision, and it is not just "add a column":**

- **what the values may be.** `SUBJECT_REQUEST` and `RETENTION` are the two that exist today. A third
  arrives with every new caller, and an enum in an append-only legal record is a schema commitment;
- **whether an actor goes on beside it.** For a desk erasure the acting login is knowable and would make
  §6.3's audit gap smaller in exactly the place it matters most. It is also a *staff member's* identity
  in a table that must be kept for ever, which is a disclosure decision of D47's kind — the reviewer's
  login is deliberately kept off the public profile for the same reason;
- **what happens to the rows already there.** Everything written before D96 is a subject request, and
  that is knowable only because the sweep did not exist. A nullable column reading `null` for those is
  honest; backfilling them is a claim, and this repository's rule is that a retrospective fact nobody
  measured does not get written down.

**Cost.** A JDL change to `ErasedSubject` in three services (booking, catalog, messaging — it is one of
the copied families' neighbours and all three hold the table), and therefore an **additive** Liquibase
changelog rather than a regenerated entity one: regenerating the entity changelog invalidates the
checksum every existing database recorded, which is D87's `meeting_link` lesson and the
`ValidationFailedException` CLAUDE.md warns about. Not large. The decision is the expensive half.

**Not blocked on a person** in the sense that the recommendation is clear — **`reason`, nullable, the
two values, no actor until §6.3 is answered as a whole** — but it should be *taken* rather than
implemented by whoever picks it up.

---

## NEW-68 — the operational retention period has no sweep, and it is the shorter one · READY

**The other half of NEW-52, left undone deliberately and named rather than folded in** — D96 §scope.

D96 built `RetentionSweep` in **booking**, which holds the financial rows (bookings, disputes) and
applies the **financial** period, 2,190 days. The **operational** period — 365 days, six times shorter
— governs *message bodies, notifications and conversations*, which live in **messaging**. Nothing
sweeps them and nothing ever has.

**Why it was not done by fanning the existing sweep out.** `ErasureFanout` exists (D38) and booking can
already mint a token messaging accepts, so one call would have reached them. It would also have applied
**booking's six-year clock to data whose stated period is one year** — and then filed a receipt saying
the customer had been erased, which is D39's "a count that is too large reads as data was still
exposed" in its worst form: the receipt would be right about what it did and wrong about what the
policy required. Two periods need two cutoffs, and a sweep with one cutoff cannot have two.

**It is the larger exposure of the two, which is the argument for doing it.** Six years of message
bodies is the substance of what people wrote to each other, under a policy that says one year. The
financial rows the D96 sweep covers are the ones the platform is *obliged* to keep.

**Done means** a sweep in messaging on the operational period, and the shape is mostly settled by D96 —
off by default, dry-runnable with a count, recorded on messaging's own `erased_subject` register, its
own properties holder, its own class. Three things are genuinely different and want thinking about
rather than copying:

- **there is no `eraseCustomer` in messaging that the operational period alone should call.** Its
  `ErasureWorkflow` erases a *subject*, and a subject's conversations are not the same selection as
  "message bodies older than a year" — a live thread with one old message in it is the case to get
  right, and deleting a body out of a thread the other party can still read is a different act from
  pseudonymising a person;
- **the other party.** A conversation has two people and a professional's retention interest is not the
  customer's. Booking's sweep had no equivalent question because a booking has one customer;
- **messaging's register is CONSULTED, not merely written** (`ErasureRegisterGuard`, D32) — the one
  place in the estate where an erasure record changes later behaviour, so adding rows to it by a timer
  needs its guard re-read rather than assumed.

**Not blocked.** No decision from a person, and worth pairing with NEW-67 since both touch the register.

---

## NEW-69 — nobody is told when a dispute misses its five working days · READY, and a decision with it

**Surfaced by D96 / NEW-52, which closed half of it.** `Dispute.dueBy` was recorded and read back by
nothing; `DisputeSlaSweep` now reads it and reports. What it cannot do is **notify a person**, and that
is a decision this estate cannot take inside a payment or a sweep.

**The promise is the prototype's**: a customer who raises a dispute is offered a resolution in five
working days. It binds *the brokerage*.

**Why the sweep logs instead.** Each alternative needs somebody else's answer, and the cheapest is not
obviously right:

- **the brokerage cannot be named.** The desk is `ROLE_BROKERAGE`, an authority granted in the
  **gateway's** account store; booking holds no list of its holders and has no business acquiring one.
  Messaging's notification rows are keyed by *login*, so there is no recipient to write. Enumerating
  them would be a third internal cross-service lookup of D74's kind — a disclosure decision, not a
  plumbing one;
- **an outbox event reaches messaging and is dropped.** Its consumer's `default ->` arm logs
  `no notification defined for {}` at DEBUG, so publishing `dispute.overdue` today is a published event
  nothing consumes — the silent nothing this repository keeps finding (D59, D62). Adding a consumer
  needs a recipient, which is the first question again;
- **telling the customer** is a product decision with a commercial edge: it is the platform announcing
  it has missed its own commitment, and possibly inviting a remedy nobody has priced.

**So the interim recipient is the estate's log** — one WARN line naming the count and the references,
greppable and alertable by whoever runs the box, deliberately **not** ERROR (NEW-65's subject, and
`quality/compose.yml` rests an argument on the zero-ERROR count). That is a real reader and it is not
the brokerage.

**Done means** deciding who is told and how. **Recommended: the desk screens, as part of NEW-53** —
which is already ratified for after phase 2 (D92 §5) and is where a person who works disputes will
actually be looking. An overdue count on that screen needs no recipient, no event and no new
disclosure; the sweep's log covers the gap until then. **What would change my mind**: if the brokerage
turns out to be one or two people with an email address, a notification is cheaper than a screen — but
then the address is configuration, and configuration naming a person is its own small decision.

**Also worth settling with it**: whether an overdue dispute should *escalate* rather than merely be
reported. D96 deliberately wrote no transition into an "overdue" state — there is none in
`DisputeStatus`, and inventing one would put a value in an append-only audit trail that no desk
decision produced. If the answer is that it should, that is a state-machine change with a migration
behind it, not a sweep change.

**Not blocked on engineering.** The report exists; the recipient is the open question.

---

## NEW-70 — the estate's one free signal is watched by nothing · READY, and a decision with it

**Surfaced by D97 / NEW-65**, 2026-09-18, and it is the question that item's whole argument rests on
without answering.

> **THE SIGNAL FIRED THE DAY THIS ITEM WAS WRITTEN, AND NOTHING TOLD ANYBODY.** This item's premise was
> measured at 09:54 and re-measured at 13:10 on the same estate, the same roll, five hours apart — and
> it had moved. That is not a reason to weaken the item; **it is the item, demonstrated**, and it is
> worth reading before the argument below because it turns a prediction into an observation:
>
> | | 09:54 | 13:10 |
> | --- | --- | --- |
> | gateway / payout / booking / messaging / catalog | **0 / 0 / 0 / 0 / 0** | **49 / 38 / 35 / 27 / 24** |
>
> **Neither cause is an application fault and neither is hc-market's.** Most are the OTel agent's own
> exporter — `GrpcExporter - Failed to export {metrics,logs,spans}` against `otel-collector:4317`,
> 08:27Z to 09:27Z, which is **D63's predicted behaviour reproduced by accident**: `loki-quality` had
> been restarted and the collector could not accept, though the collector container itself never went
> down (`Up 29 hours`). The rest are Kafka listener-container and consumer-rebalance errors between
> 08:57Z and 09:02Z — **4 in catalog, 3 in booking** — while this workstation sat at load average 200+
> under eight other products' Maven builds, so the shared broker was not answering in time.
>
> **It was found only because a package that happened to be writing about the count re-measured before
> finishing.** No alert fired, no panel moved, no check went red, and a reader of any document in this
> repository would have gone on believing the zero. The ten javadoc copies D97 shipped originally
> asserted the live count and were rewritten to assert the *argument* instead — a javadoc quoting a
> number nothing watches is a javadoc that rots.

**The premise, as measured and as it decayed.** On the quality estate rolled to `1ac77c7` at 09:54,
`docker logs hc-market-quality-<svc> | grep -c ERROR` was **0** for all five with the OpenTelemetry
agent **attached** — every container's `JAVA_OPTS` carries
`-javaagent:/app/otel-javaagent.jar -Dotel.exporter.otlp.endpoint=http://otel-collector:4317` — and
`WARN` at **196** on payout as the positive control that stops the zero meaning "nothing is logging".
**By 13:10 it was not zero anywhere**, per the box above. Both readings are real; what makes them
useful together is that nothing in the estate can tell you which one is current.
That zero is the estate's only way of knowing a collector has died or an agent failed to attach: D63
measured **35 ERROR lines with stack traces every 150 seconds** against a dead OTLP endpoint, D73
measured **zero** against a live one, and `quality/compose.yml` calls it *"the estate's one free
signal"* and *"the load-bearing half"* in its own comments. **The 13:10 reading above is that mechanism
working exactly as D63 described it** — a collector that cannot accept, the agent saying so, in the one
place anybody would see it if anybody were looking. The signal is not theoretical and does not need
proving; what it needs is a reader.

**Nothing watches it.** Established by looking rather than assumed:

- `deploy/observability/hc-market-rules.yaml` — both rule groups key on
  `http_server_request_duration_seconds_count{http_response_status_code=~"5.."}` ratios. **No rule
  reads a log level**, and there is no Loki rule file in this repository at all;
- `deploy/observability/hc-market-gateway-identity.json` — six panels, all `gateway_identity_*`, and
  it carries a `NOT-YET-TRANSPORTED` marker of its own (D84 §7);
- `quality/startup.sh --verify` — counts professionals and reviews and derives the rating from the
  view (D46). It never reads a log;
- `deploy/deploy-prod.sh`'s smoke test — `/management/info` for `brokerage.termsInForce` and a
  catalogue count (D57). Never a log;
- `.github/workflows/build.yml` — nothing; CI has no estate to read logs from.

So three decisions in a row (D64, D73, D97) have turned on a number that has only ever been produced
by a person typing `grep -c` at the moment they needed it. **A premise nobody can re-run is a claim**,
which is this repository's own rule about `verify-otel-agent.sh` (D63) applied to the thing that script
exists to make visible.

### Two things that make it harder than it looks, and both need deciding

**1. "Zero" is ambiguous between two different questions.** `docker logs` returns a container's whole
life; a restart resets nothing and a roll to a new tag resets everything. *"Has this service ever
logged an ERROR"* and *"has it logged one since the last roll"* are different assertions with different
uses — the first is the one D63/D73/D97 have been quoting, and it is the one that becomes permanently
false the first time anything legitimate goes wrong. A check asserting the lifetime figure is a check
that goes red for ever after one incident and then gets disabled. **Recommended: assert the count since
the container started and print the lifetime figure beside it**, so a reader can see both and neither
number is doing a job the other is better at.

**2. What to do when it is not zero is an operations question, not an engineering one.** The plausible
answers are a `--verify` assertion (red on this box, nothing paged), a Loki rule (pages somebody, and
Loki belongs to `monitoring-quality` — another repository's stack, exited for five days in September and
able to go down again, which is D73 §3's whole argument for not depending on it), or a line in
`--verify`'s output that reports and does not fail. **Recommended: report-and-do-not-fail first**, for
the reason D46 records about `verify-cycle.sh` — a tool that calls a legitimate state a fault is a tool
people stop running — with an assertion added once somebody has watched the number for a while.

### What must NOT happen to it

**It may not become a reason to suppress an ERROR.** The direction of D97 was to stop a *generic
interceptor* authoring the signal; the moment a check exists, the cheap way to keep it green is to drop
a real ERROR to WARN at a site that knows it is an error, and that is the defect inverted. D97 §5 states
the rule and enumerates every hand-written `log.error` in the estate against it; that enumeration is the
thing to check a proposed suppression against.

**And it may not read `docker logs` on the quality box from CI.** CI has no daemon and no estate; a step
written that way would be skipped or green-by-absence, which is the fail-open this repository has found
nine times.

**Not blocked.** No decision from a person and no outside fact — but it wants the two questions above
answered rather than a `grep -c` wired into the first script that would take it.

---

## NEW-71 — `pipefail` turns `grep -q` inside out, and one CI guard cannot fire · DONE

**Closed 2026-09-18 by `decisions.md` D98**, on `new-71-a-match-reported-as-a-failure`. All three
questions answered — the pipeline shape, the pattern, and a third the item did not anticipate — and
**two of this item's own statements below are corrected by the work**, which is why they are left
standing rather than edited away:

- **§"Measured both ways" attributes the two outcomes to machine LOAD, and that is the wrong
  variable** (D98 §2). The discriminator is how much the producer still has to write after the
  match: under about 4.5KB it exits before `grep -q` closes the pipe and the status is honest, past
  it the status is 141. Measured, 5 runs per size, no variance. The stated direction is also
  inverted — a *suppressed* match is what prints `ok`, so load can only make `ok` more likely — and
  the real subject measured **141 five of five on a loaded box**, printing `ok`.
- **§3 says part 4's is the one fail-open site. THERE ARE THREE** (D98 §4): part 1's `@Scheduled`
  sweep, part 4's placeholder ban, and part 6's http-scope ban. A fourth, the surplus advisory, is
  the fail-open *shape* with no assertion behind it. Eight were fail-closed, as the item says.
  **Part 4's is the worst and the item was right to lead with it**: its one real occurrence in this
  repository was measurably unreportable. Part 1's blind spot is real but at a **position no
  regeneration produces** — `@Scheduled` is `@Target({METHOD, ANNOTATION_TYPE})`, so the class-level
  fixture that measured 141 does not compile, and the position `--force` actually uses was caught
  even by the broken shape. D98 §4b is that correction; this package's own first draft ranked part 1
  first and had to be corrected at review.

**What landed**: one shape — *no pipeline* — in two spellings chosen by where the text already is
(strip once to a file and grep the file; or a herestring for text already in a variable), both
adopted from shapes this repository already argues for rather than invented. `has_in` stops the
check on any grep answer that is neither match nor no-match, and on a stripper that runs and fails.
The ban is narrowed a second time — the message of a `${VAR:?…}` expansion is blanked, bounded to
that expansion's own `}`, with `:-` deliberately still caught — and D98 §5 names *narrowing a ban
twice* as a pattern rather than repeating the fix. `.claude` is pruned from part 4's walk.

**The test went from 35 assertions to 43.** Cases 1, 10 and 16 all passed the broken tree because
each one's mutation lands near the end of its file; 21, 22 and 23 are the same mutations moved to the
top, and the pair is kept deliberately. The mutation battery is tabulated in D98 §7, including the
two rows that are findings: part 1's mutation reddens **only** the new case, and part 6's reddens
**nothing** — that site is fixed on argument and no test can see it, which is stated rather than
dressed up.

**The sweep is NOT done and is NEW-73**: 61 `| grep -q` pipelines across 37 shell scripts, 21 of the
check scripts setting `pipefail`. Eleven are closed here, in the one file where the fail-opens were
measured.

---

<details>
<summary>The item as filed, kept for the record — two of its claims are corrected above</summary>

**Found 2026-09-18 while running D97's gates**, and it is **pre-existing and not D97's** — reported
rather than fixed, because the repair makes CI red on `main` for a second, separate reason (§3).

### The mechanism, measured with a control

`grep -q` exits at its **first match**. Its producer then takes `SIGPIPE` and dies **141** — and under
`set -o pipefail` the *pipeline's* status is the producer's, so **a match is reported as failure**.
Measured on this repository, same file, same pattern, one option apart:

```
$ bash -c 'awk -f .github/checks/strip-sh-comments.awk deploy/docker/docker-compose.prod.yml \
             | grep -q my-server-url-to-change; echo rc=$?'
rc=0                       # the match

$ bash -c 'set -o pipefail; awk -f … | grep -q my-server-url-to-change; echo rc=$?'
rc=141                     # the same match, reported as a failure
```

It is a **race, not a rule**: if the producer finishes writing before `grep -q` closes the pipe there
is no `SIGPIPE` and the status is 0. So the same command answers differently depending on load, which
is why this went unnoticed — and it is the second time this file has been caught by it. Its **own part 5
carries a comment about the first**: *"the variable form disagreed with itself between two runs of this
check over an unchanged `deploy-prod.sh` … whatever the cause, an instrument that answers differently
twice is not one to reason from."* The cause is this.

### What it costs, today, in one place

`.github/checks/account-lifecycle-guards.sh` is `set -Eeuo pipefail` and part 4 reads

```bash
conf_src "$f" | grep -q 'my-server-url-to-change' && placeholders="$placeholders $f"
```

so **the estate-wide ban on JHipster's placeholder base-url cannot report a file**. That is D94's
guard — the regeneration table calls the thing it protects *"the front door swallows people again"*:
restore `base-url: http://my-server-url-to-change` and `POST /api/register` answers **201**, the mail
is **delivered** with a dead link, one WARN is logged, and the sweep deletes the account in three days.
`MailServiceIT` mocks `JavaMailSender`, so nothing else in the estate can see it either.

**Measured both ways.** Under no load the check prints `ok   no .yml in the repository carries
my-server-url-to-change` and exits **0** with the string demonstrably present in a file it walked (135
`.yml` files, `placeholders` empty). Racing a Maven build on the same box it printed
`::error file=./deploy/docker/docker-compose.prod.yml::… contains JHipster's placeholder base-url` —
twice — and then stopped doing so. **Eight consecutive quiet runs: eight `ok`.**

There are **11** `| grep -q` pipelines in that file. Part 4's is the one whose direction is
fail-**open**; the others are `if ! … ; then err` shapes, where a swallowed match makes the check
refuse a correct tree — annoying, fail-closed, and the reason this has read as flakiness rather than as
a hole. **Which of the eleven are which has not been enumerated** and is part of the work.

### §3 Why the one-line repair is not enough, and why this is not folded into D97

`grep -q` is the wrong tool in a `pipefail` script; `grep -c … || true` compared against 0, or a
herestring (`grep -q PAT <<< "$text"` — not a pipeline, so the question cannot arise), or `grep -l`
against a file both answer honestly. **But fixing part 4 makes CI red on `main` immediately**, for a
false positive: the one surviving occurrence is on `docker-compose.prod.yml:219`, inside the *error
message* of a `${HC_MAIL_BASE_URL:? … It was \`http://my-server-url-to-change\` in
application-prod.yml until decisions.md D94}` expansion. It is prose in a shell parameter expansion, not
a base-url value, and the shell stripper cannot tell the two apart because both are YAML content. So
the item is **two decisions**, which is why it is an item:

1. **the pipeline** — replace the shape, and enumerate which of the eleven were fail-open and which
   fail-closed, because *"the check went red"* and *"the check could not see"* want different write-ups;
2. **the pattern** — the ban is a bare string in a `.yml`, and D94 already narrowed it once from raw
   text to stripped text after reporting three files, two of which were its own explanatory comments.
   Narrowing it again means banning the placeholder **as a value** — the right-hand side of a mapping,
   or a `:-` default, but not a `:?` message — which is a small YAML-shaped assertion rather than a
   grep. **Recommended**: match `my-server-url-to-change` only where it is not inside a `:?`, and say
   in place that the exclusion exists because the only legitimate mention of the string is a refusal
   explaining it.

**D97 hardened its own step rather than leaving the hazard to spread**: the new refusal-logging check
and its test use herestrings and files throughout and say why at the site. That step runs under
Actions' default `bash -e`, which carries **no** pipefail, so it was correct either way — and one
`defaults: run: shell: bash -eo pipefail` away from not being, which is the whole reason to prefer a
shape that cannot be wrong.

### The generalisation, which is this repository's own rule arriving from a new direction

D71 established *"a die may not fold; a warn and a poll may"* for `2>/dev/null` swallowing docker's
status. This is the same defect with no redirection in sight: **`pipefail` does not make a pipeline
honest, it makes it report the wrong end**, and an early-exiting consumer inverts the answer. Worth a
sweep of every `| grep -q` in a `pipefail` script — **21 of the check scripts set `pipefail`** — but a
sweep is only worth doing once somebody has decided (1) above, or it produces twenty edits in twenty
shapes.

**Not blocked.** No decision from a person and no outside fact.

</details>

---

## NEW-72 — a document true of the RENDER and false of the ESTATE, and nothing can tell a reader which · READY

**Found 2026-09-18 at D97's review.** It is given its own row rather than folded into NEW-70 because the
two have different subjects and different done-conditions: NEW-70 is *nothing watches the ERROR count*
and is closed by something that observes; this is *a header states as fact about the estate something
that is only a fact about the default*, and is closed by rewording plus deciding whether anything should
check the estate at all. Related in topic, unrelated in remedy — the NEW-52 → NEW-68 split is the
precedent.

### The instance

`deploy/observability/hc-market-rules.yaml` says, in capitals at line 14:

> `NOTHING ANSWERS THESE QUERIES TODAY, AND NOTHING EVER HAS. DO NOT INSTALL THIS FILE YET.`

and at line 19, `NO ENVIRONMENT ATTACHES IT`.

**Measured 2026-09-18: the agent is attached on all five quality services** — `1` for `javaagent` in
each container's `/proc/1/environ` — and `otel-collector-quality` has been up 30 hours with
`loki-quality` up 2. So the second sentence is false of what is running, and *"nothing ever has"* is
false of history.

### The distinction, which is the whole item and is why "the header is stale" undersells it

The file is **true of the rendered compose** and **false of the running estate**, and both at once:

- `quality/compose.yml` renders `JAVA_OPTS: ${HC_JAVA_OPTS:-…} ${HC_OTEL_JAVA_OPTS:-}`, and
  `HC_OTEL_JAVA_OPTS` **defaults empty**. That is D73 §3's deliberate decision — `monitoring-quality`
  belongs to another repository and can go down, and one variable set by an operator is the right
  interface for a dependency this repository does not control.
- Every roll this session passed that variable **explicitly**, so the estate has been running attached
  while the default it is checked against says otherwise.

**CI is not wrong and is not red.** `observability-claims.sh`'s marker check holds the file against the
*render*, which is the correct subject for a check about a default: a check that read the estate would
go red whenever an operator exercised a documented switch. Nothing needs fixing in the check.

**What is wrong is that a reader cannot tell which subject a sentence has** — and this exact hazard bit
this repository twice in one day. `CLAUDE.md`'s D63/D64 paragraph asserted a dead collector and a zero
ERROR count in the present tense, both false by 2026-09-18, and `quality/compose.yml` carried a
*"currently carry ZERO ERROR lines"* thirty-six lines from a new block saying the zero was gone. Both are
corrected by D97; this one is not, because the correction needs a decision.

### The decisions it wants

1. **What the header should claim.** Recommended: say it of the **default** explicitly — *"no environment
   attaches the agent BY DEFAULT; set `HC_OTEL_JAVA_OPTS` and it does, which is what CI checks and what
   this file is written against"* — and move the install refusal onto the condition rather than the
   estate. That keeps the file honest under both states without needing to know which is current.
2. **Whether anything should check the ESTATE, and where.** Not in `observability-claims.sh`, per above.
   If anywhere, it belongs beside NEW-70's observation of the ERROR count: both questions are *"what is
   this box actually doing right now"*, and `quality/startup.sh --verify` is the one tool that already
   runs against a live estate. **Recommended: pair it with NEW-70** and report rather than fail, for the
   reason D46 records about `verify-cycle.sh` — a tool that calls a legitimate state a fault stops being
   run.
3. **Whether the alert rules are now installable.** The header's *"DO NOT INSTALL"* rests on nothing
   answering the queries. Something does, on quality, when the variable is set. That is a separate call
   from (1) and should not ride along with a wording fix — installing rules that fire against a stack
   another repository owns is a conversation with whoever owns it.

### The generalisation worth keeping

**A marker held against a render is a statement about configuration; a reader takes it as a statement
about the world.** This repository has a rule for the neighbouring case — *a probe answers the question
it was pointed at, not the question the sentence written from it claims* (the `/proc/1/cmdline` finding,
and `JAVA_TOOL_OPTIONS` one layer down) — and this is its documentary twin. **Where a document states a
fact that a variable can change, say which of the two it is stating.**

**Not blocked.** No outside fact and no decision from a person for (1); (3) is somebody else's estate.

---

## Not a package: standing constraints

- **Production is off limits.** The pipeline is not ready; `deploy/deploy-prod.sh --dry-run` is the
  only thing to run against it, and it contacts nothing. It is now *configured* — `deploy/prod-server/`,
  WP-19, D49 — and configured is not deployed: **not one command in that directory has been run on a
  host**, and its README says so before it says anything else. `--dry-run` is a faithful printer of a
  plan, and three of WP-19's five findings were things `--dry-run` prints correctly and reality
  refuses.
- **Work goes on a branch with a PR.** Pushing `main` publishes five images to GHCR.
- **Quality is `jacserver`, which is this workstation** at 127.0.0.1 — `./quality/startup.sh --local`,
  no ssh. It is the last real gate, and it has now found **seven** defects that every test suite
  passed — three at WP-07/WP-09, four more in the run of `1eadc7a` (D46). Two of that four were in
  the gate's own tooling, which is the argument for running it every package rather than every fifth:
  the box cannot find anything while the scripts that exercise it cannot address it.

---

## NEW-73 — fifty more pipelines of the same shape, and a threshold to triage them by · DONE

**Closed 2026-09-18 by `decisions.md` D99**, on `new-73-fifty-pipelines-and-a-threshold`. **The answer
is triage by DIRECTION, not by size** — 18 pipelines converted, 33 left with their measurement — and
the threshold this item was named after turned out not to be a usable triage instrument. **Four of
this item's own statements below are corrected by the work** and are left standing rather than edited
away:

- **The population is 51 PIPELINES on 50 LINES.** `signing-key-severance.sh:89` carries two
  `| grep -q` on one line and `grep -c` counts lines, so the table's 50 undercounts — in the
  fail-open direction, because the first of that line's two is one of the fail-opens. 18 in the guard
  scripts, not 17.
- **§"Two sites deserve naming" calls `admin-seed-wiring.sh`'s two a ban and names them first to
  measure. Neither is the ban.** They are `printf … | grep -qE … || err` — *requirements*, so
  fail-**closed** — and D61's bcrypt-hash sweep is a separate loop twenty lines above with **no
  `| grep -q` in it at all**, never in this population. The two flagged as the priority hold the
  **largest** producer in the guard population (6,393 B) with a 5× margin and the harmless direction;
  the two genuinely fail-open sat one file away at 1,175 and 260 bytes. D99 §3b.
- **§"The three test scripts are the largest block and the lowest stakes" is false for 9 of 33.**
  The **worst site in the whole population is in a test script**:
  `host-probe-attribution-test.sh:712`, case 44's aim control, whose finding *is* the match —
  **141 ten runs of ten**, 36,589-byte producer, 12,175 bytes past the match, and the suite reported
  `53 ok, 0 failed` on a tree carrying the thing it exists to catch. D99 §4.
- **The ~4.5KB threshold is a property of ONE producer on ONE machine.** Reproduced exactly for
  `strip-sh-comments.awk` and then measured across six producer kinds: the Java stripper inverts
  **below** it, `awk '{print}'` needs 8–32KB, `printf` 16–32KB, and `cat` never inverts here while
  always inverting at 32KB in CI — an *implementation* gap (uutils vs GNU `cat`, gawk vs **mawk**),
  not a speed one. The boundary is also **flaky, not sharp**. D99 §2.

**What landed**: 13 fail-open sites, 3 that were driving `deploy-prod.sh` toward **reverting a healthy
production deployment** (a third failure direction neither D98 nor this item accounted for — D99 §5),
plus two argued exceptions. Appendix B re-embedded in the same commit. The 33 left are 32 fail-closed
and one pure `warn`, each with its producer size recorded, because *"under the threshold" without a
number is not a measurement* — this item's own rule.

**Not swept into a CI ban**, deliberately: a grep cannot read the direction distinction off a line, so
such a check would have to refuse 33 correct sites or encode something it cannot see. D99 §6(c).

<details>
<summary>The item as filed, kept for the record — four of its claims are corrected above</summary>

**Opened 2026-09-18 by `decisions.md` D98 §9**, which closed NEW-71's eleven and deliberately did not
sweep the rest. The shape is decided now, which is the precondition NEW-71 named: *"a sweep is only
worth doing once somebody has decided (1), or it produces twenty edits in twenty shapes."*

### The subject, measured — and read the measure, not just the number

**Counted from STRIPPED text, at the tip of `new-71-a-match-reported-as-a-failure`, 2026-09-18.**
Both qualifications matter and the first version of this table had neither:

| file | status pipelines | under `pipefail`? |
| --- | --- | --- |
| `strip-comments-test.sh` | 12 | yes |
| `account-lifecycle-guards-test.sh` | 8 | yes — four of its twelve were **closed by D98**; see its own header |
| `strip-sh-comments-test.sh` | 8 | yes |
| `signing-key-severance.sh` | 6 | yes — **bans**, so fail-open; measure these first |
| `deploy-prod.sh` | 4 | yes — **and it is Appendix B** |
| `admin-seed-wiring.sh` | 2 | yes — a **ban** (D61's bcrypt-hash sweep) |
| `filter-chain-precedence-test.sh` | 2 | yes |
| `backlog-table-agrees.sh`, `host-probe-attribution.sh`, `host-probe-attribution-test.sh`, `observability-claims.sh`, `pepper-wiring.sh`, `quality-pepper-persistence-test.sh`, `quality-project-checkout-test.sh`, `probe-infranet-aliases.sh` | 1 each | yes |
| `account-lifecycle-guards.sh` | **0** | D98's subject — nothing left |

**50 in total, across 15 files, and every one of them is under `pipefail`** — so the "only some
scripts are exposed" triage the first draft leaned on does not apply to this population at all.

⚠ **THE RAW COUNT IS NOT THIS NUMBER AND MUST NOT BE QUOTED.** `git grep -c '| *grep -q'` answers
**57** here and **61** at `55781f2`, because it counts the paragraphs *explaining* the defect:
`account-lifecycle-guards.sh` reads 5 raw and **0** stripped, and its test reads 10 raw and 8
stripped. That is this repository's own stripper rule applied to its own inventory — a check counting
these raw would be asserting the length of an argument (D98 §8).

⚠ **AND D98's COMMIT MOVED THE FIGURE, WHICH IS WHY IT IS DATED.** The first draft of this table was
measured at `55781f2` and was stale the moment it was written: D98 removed 11 from the check and its
new test cases added 4 more (since converted to herestrings at review). **Re-run the inventory rather
than trusting the table** — the command is in D98 §3, and the honest form strips first:

```bash
buf=$(mktemp); for f in $(git ls-files '*.sh'); do
  awk -f .github/checks/strip-sh-comments.awk "$f" > "$buf"
  n=$(grep -c '| *grep -q' "$buf" || true); [ "$n" = 0 ] || printf '%3s %s\n' "$n" "$f"
done; rm -f "$buf"
```

**That loop is written with a temp file rather than a pipe deliberately**, and not for tidiness: the
first version of this very inventory used `awk … | grep -q pipefail && pf=yes` and reported
`pipefail=no` for `deploy-prod.sh` and for `account-lifecycle-guards-test.sh`, **both of which
plainly set it** — the match sits near the top of a file well past the threshold, awk took `SIGPIPE`,
and the `&&` never fired. **The defect reproduced itself inside the instrument built to inventory
it**, in the safe-looking direction. Suspect the instrument.

### What makes this cheap, and what makes it not a blanket substitution

**The threshold is measurable per site** (D98 §2): a producer whose stripped output is under about
4.5KB finishes before `grep -q` closes the pipe and cannot invert today. So each site is two
questions — *how big is what it reads*, and *which way does it fail* — and D98 §4's enumeration is
the format to copy, because **"the check went red" and "the check could not see" want different
write-ups** and three of NEW-71's eleven were fail-open against eight that were not.

⚠ **A THIRD QUESTION, WHICH D98 §4b PAID FOR**: does the position you measure at *exist*? That
package ranked part 1 worst on a fixture planting `@Scheduled` at class level — which does not
compile, so no generator emits it — while the position a regeneration really uses was caught even by
the broken shape. The 141 was real and the scenario was not. **A fixture for a text guard must still
be legal in the language the text is written in**, or the coverage story describes a state the
compiler forbids.

Two sites deserve naming before anybody starts:

- **`deploy-prod.sh`'s four.** It is Appendix B of the spec, so any edit needs
  `./deploy/sync-appendices.sh` in the same commit. It is also the file D75/D78/D80 spent three
  packages making honest about *which hop answered*, and a folded pipeline status there is the same
  family of defect those decisions closed — worth reading D71 §5's *"a die may not fold; a warn and a
  poll may"* before touching it.
- **`signing-key-severance.sh`'s six and `admin-seed-wiring.sh`'s two.** Both are **bans** —
  `admin-seed-wiring.sh` deliberately sweeps for the committed bcrypt hash rather than for logic
  (D61) — and a ban's finding *is* the match, which is the fail-open direction. These are the ones to
  measure first.

The three test scripts (28 pipelines between them) are the largest block and the lowest stakes: a
swallowed match there makes a test's own assertion fail closed. Worth doing last, and worth doing,
because a test that reddens for the wrong reason is how the subject gets "fixed".

### Not blocked

No decision from a person, no outside fact, and the shape is already chosen and already has a worked
example beside its own test. What it wants is patience per site rather than a `sed`.

</details>

### The 33 that were left, with their numbers

Every one is fail-closed — a swallowed match makes the check **refuse a correct tree**, which is the
direction D71 §5 permits — except `deploy-prod.sh`'s **version probe** in `smoke_test`, which warns
either way and decides nothing. (Named by its function, not its line: this package's own comment
blocks moved every `deploy-prod.sh` line number, so the base-relative ones are labelled in D99 §5.)

**Figures are BYTES**, measured as `printf '%s' "$var" | wc -c`. The first version of this table
reported `${#var}` — *characters* — which reads 4 to 24 low wherever the payload carries an em-dash;
SIGPIPE depends on bytes written to the pipe, so bytes is the only correct unit here (D99 §2).
Producer sizes measured at `83857ee`; re-derive with the loop above rather than trusting these.

| file | sites | producer | bytes |
| --- | --- | --- | --- |
| `account-lifecycle-guards-test.sh` | 8 | the check's own output | **3,366** — the largest left, ~93 B/assertion |
| `strip-comments-test.sh` | 7 | inline Java probes | 341 |
| `signing-key-severance.sh` | 4 | `$arm`, `$prodmsg`, a 3-line `sed` window | 1,175 / 260 / 900 |
| `strip-sh-comments-test.sh` | 4 | inline shell probes | 248 |
| `admin-seed-wiring.sh` | 2 | stripped `InitialSetupMigration.java` | 6,393 (raw 12,080) |
| `filter-chain-precedence-test.sh` | 2 | the check's own output | 74 |
| `backlog-table-agrees.sh` | 1 | `$table_rows` | 2,974 (19 rows) |
| `host-probe-attribution.sh` | 1 | two-stage `awk` over the prod compose | 256 |
| `observability-claims.sh` | 1 | `$declared`, from the meter class | 49 |
| `quality-pepper-persistence-test.sh` | 1 | `sed` function range | 329 |
| `quality-project-checkout-test.sh` | 1 | `sed` function range | 271 |
| `deploy-prod.sh` | 1 | `$gateway_info` — **unmeasurable**, never run against a host (D49) | — |

⚠ **Do not read a small number here as "safe for ever".** The number is a fact about one afternoon on
one machine; the reason each of these is left alone is its **direction**, which does not move. The
three whose producer is not effectively a fixed string are tabulated in D99 §9.

---

## NEW-74 — an ERROR count has three different measures, and the naive one cannot see an application line · READY

**Opened 2026-09-18 by `decisions.md` D99**, found while measuring NEW-73 and **reported rather than
fixed** — it is outside that item's fence (which is about how a pipeline carries a status, not about
what anything asserts).

### The mechanism, and why the obvious instrument measures the wrong population

`docker logs` output is **ANSI-colourised**, so the character immediately before `ERROR` on an
application line is `m` — the tail of a colour escape — and **not a space**. Therefore:

> **`grep -c ' ERROR '` cannot match an application log line at all.**

What it *does* match is the OpenTelemetry agent's own differently-formatted lines
(`[otel.javaagent …] ERROR io.opentelemetry…`), which are not colourised. So the naive instrument
silently counts **exporter noise only** — the opposite population from the one every decision that
quotes it is about.

### Measured, on the quality payout container, before the roll

| instrument | answer | what it actually counts |
| --- | --- | --- |
| `grep -c ' ERROR '`, unstripped | **29** | the OTel exporter's own lines, and nothing of the application's |
| ANSI-stripped, `ERROR` anywhere | **43** | exporter **+** application |
| ANSI-stripped, anchored `^<ts> ERROR` | **14** | application only — of which **9** were Kafka rebalance and **5** were the probe's own |

Three measures, three answers, and **nothing in any of the documents that quote a figure says which
one was used.**

### Why it matters

**D63, D64, D73, D97 and NEW-70 all rest on an application-ERROR count.** D97's whole argument for
moving `LoggingAspect` to WARN is that an ERROR line *means* something, and the evidence for "it means
something" is that the count was zero. A count produced by the naive grep is a statement about the
exporter.

⚠ **This does NOT weaken D97 / NEW-65.** That package's evidence is a **delta measured on one
consistent instrument** — the same command before and after, on the same containers — and a delta
survives a constant offset in what the instrument counts. What is unsound is quoting any of these
figures as *the* ERROR count, or comparing a figure from one document against a figure from another.

**NEW-70 already records that most of its 13:10 figures were exporter lines.** What it does not record
is the **mechanical reason** — the colour escape — nor that the naive grep is *blind* to application
lines rather than merely diluted by exporter ones. Those two are the additions here.

### What this wants

Not a fix: a **decision about which measure is the estate's**, and then one place that derives it, in
the shape D63's `verify-otel-agent.sh` established (*a premise nobody can re-run is a claim*). The
anchored, ANSI-stripped form is the obvious candidate because it is the only one that answers *"did
this application log an error"*. Whoever takes it should also decide whether the answer is
lifetime-of-container or since-last-start — NEW-70's §1 poses that and it is the same question.

⚠ **It may not become a reason to suppress an ERROR** (D97's rule), and **it may not read `docker logs`
from CI** — CI has no daemon and no estate, so such a step is green-by-absence.

### Not blocked

No decision from a person and no outside fact.

---

## NEW-75 — the rate limits are in force on quality, and CLAUDE.md says no ceiling is in force anywhere · READY

**Opened 2026-09-18 by `decisions.md` D99**, reported and **not fixed**: the cycle's rule is that a
defect outside the item becomes an item with a reason, and there is a decision inside this one.

### What changed underneath the document

The architect reloaded nginx on this workstation on 2026-09-18, which **installed D94's rate-limit
zones for the first time**. They are live on the quality estate. Two claims in `CLAUDE.md` became
false at that moment:

- **line 658** — *"The rate limits are PROVIDED AND NOT INSTALLED, and there are two zone files now."*
- **line 666** — *"…the sudo lines are printed and nothing is installed, so **no ceiling is in force
  anywhere yet**."*

### Measured through nginx, not through the published port

The published port bypasses the edge, so it is the wrong door to ask at:

```
POST /api/authenticate ×12, nonexistent login   ->  401 ×7 then 429 ×5
control: a path outside both maps, ×12          ->  200 ×12
after 3s idle                                   ->  not 429 (the bucket refills)
```

`limit_req_status 429` is set, so a refusal is a 429 rather than nginx's default 503. **Seven passed
rather than the nominal `1 + burst=5`** because the loop spanned just over a second and the `1r/s`
bucket refilled by one — consistent, not an anomaly. **The control is what makes the result mean
anything**: zero 429s on an unlimited path is what rules out "the estate is simply sick".

### The correction is narrower than "it is wrong"

**Only quality is installed.** Production's zones live in
`deploy/prod-server/nginx-conf.d/hc-market-account.conf`, which has never been applied to any host
because production has never been deployed. So the honest replacement is *quality's are in force and
measured; production's remain unexecuted configuration* — **not** *the rate limits are installed*.

**The two deliberately-unlimited doors are unchanged**, and should be re-derived rather than trusted
from here: the `map` defaults are the empty string and nginx does not account a request whose
`limit_req` key is empty, so `/api/activate` and `/api/account/reset-password/finish` remain unlimited
**by construction**. CI already derives that set from `SecurityConfiguration`'s own matchers and
demands the difference be exactly those two.

### Why this is an item and not a two-line edit

`CLAUDE.md` is checked into a **public** repository and describes an estate whose edge configuration
is owned by a person who changes it **out of band**, so a sentence about what is installed has
**nothing keeping it true**. That is **NEW-72's genre one file along** — a document true of one subject
and read as a statement about another — and the decision to take is whether the fix is a corrected
sentence, a **dated** one, or a check that reads the live edge.

⚠ **`CLAUDE.md` lines 658 and 666 were deliberately left untouched** by the NEW-73 branch, so this
item still has its subject.

### Not blocked

No decision from a person; the measurement is done and the estate is available to re-measure.
