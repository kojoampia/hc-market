# Backlog — hc-market

Every open item in this repository, folded into work packages. Sources: `docs/decisions.md` D1–D39,
the two code reviews of 2026-09-01, and the verification runs against the quality box.

**This is a derived document.** `decisions.md` holds the reasoning and stays the record; this holds
only *what is left*, in a shape you can pick work from. Where the two disagree, `decisions.md` wins —
and where either disagrees with the code, the code wins.

**Status vocabulary.** `DONE` — built, tested, and verified against a running estate. `IN PROGRESS` —
being worked now. `READY` — specified, unblocked, nobody is holding it. `BLOCKED` — waiting on a named
person, not on engineering. `WON'T` — considered and deliberately not done, with the reason.

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
| **WP-12** | Payments: the zero-amount booking | READY | — |
| **WP-13** | Payments: provider choice and Act 987 | READY (large) | D37 — Paystack, Hubtel and MoMo direct, customer chooses. WP-11 is done, so no longer blocked |
| **WP-14** | Verification badge | DONE | — |
| **WP-15** | Badge: date-only on the wire | READY | — |
| **WP-16** | Search performance | WON'T (measured) | — |
| **WP-17** | Video and WhatsApp providers | READY (spec only) | D37 — cost both, build neither |
| **WP-18** | Production `infranet` alias check | CLOSED | D37 — the rename made it moot |
| **NEW-1** | A retry reported rows re-written, not rows that held data | DONE | D39 |
| **NEW-2** | Catalog's receipt omitted what it deleted | DONE | D39 |
| **NEW-3** | A privacy test that could not fail, over a leak that was real | DONE | D40 |

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

## WP-07 — Erasure: orchestration · DONE (unmerged)

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

## WP-08 — Erasure: the still-active account · DONE (unmerged)

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

**Still open, and none of it is engineering:**

- the **registration number itself** — answered "registered", but the number was not supplied, and the
  privacy notice and processing record cannot be published without it;
- the **privacy notice and processing record**, following from the lawful-basis answer;
- the **data-residency transfer basis** — a written document, and a decision about whether the estate
  needs one or six, since `webserver` hosts all six products.

**Watch two things.** The retention numbers were authored in the question and ratified rather than
independently proposed, so they are provisional in origin even though they are live in configuration.
And `care-summary-days` is load-bearing: counsel's position that the care summary is ordinary contract
data rests partly on it being held briefly, so lengthening it is a legal change and not a tuning one.

The two coded judgements D37 ratified — the **review body** is not erased (public speech about a
professional) and `Dispute.resolution` is kept (the brokerage's record of a financial decision,
underpinning a compensating ledger entry) — are untouched and stay as they are.

## WP-10 — Payments: the seam can complete a lifecycle · DONE (unmerged)

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

## WP-11 — Payments: asynchronous confirmation · DONE (unmerged)

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

## WP-12 — Payments: the zero-amount booking · READY

Two seeded services are genuinely free, and "from ₵0" is correct rather than a bug. `authorizePayment`
runs unconditionally, so a real provider gets asked to authorize 0 pesewas and rejects it — every free
booking in the estate becomes uncreatable the day a provider is configured, and no test would notice.
One condition and one test.

Adjacent, same package: provider exceptions currently become 500s rather than the `FAILED` path; and
`@ConditionalOnMissingBean` in a user `@Configuration` is order-sensitive, so the real provider should
be a component-scanned bean — worth a javadoc line before somebody discovers it as a startup failure
that differs between a laptop and CI.

## WP-13 — Payments: provider and Act 987 · READY, no longer blocked on WP-11

Provider choice and contract, and whether a split-settlement model clears Act 987. The seam is
deliberately agnostic between split-at-capture and reconcile-afterwards, and **nothing on
`PaymentProvider` pays the professional** — that omission is what makes it survivable, and it should
survive this decision rather than be pre-empted by it.

**What WP-11 leaves for it to do, beyond writing the three adapters** (D43):

- **the registry.** `PaymentConfiguration` still supplies one provider by `@ConditionalOnMissingBean`;
  three of them need a registry keyed by name, with the off-platform bean as the fallback rather than
  the only entry. The webhook already resolves the provider by the name in its path, so it becomes a
  registry lookup and the "callback addressed to a provider this service is not configured for"
  refusal starts doing real work;
- **the customer's choice on the intent**, validated against something the server knows rather than
  taken on faith (D22);
- **two lines per environment to expose the webhook, not one.** A route with
  `Path=/services/healthconnectbooking/webhooks/**` *and* a permit in the gateway's security for it —
  the generated gateway chain authenticates `/services/**` before routing, so the route on its own
  gives a webhook that silently never arrives. Nothing before that moment is reachable from outside,
  which is deliberate;
- **each provider's signing secret**, handled like the estate's other two: required, never committed,
  and absent means the callbacks are refused rather than trusted.

## WP-14 — Verification badge · DONE

D16, D31, D33. The profile states what the badge means and carries `verifiedOn`; the reviewer login and
evidence reference stay behind `ROLE_BROKERAGE`. D33 fixed a real disclosure defect: the date scanned
past a later suspension, so `SUSPENDED` and an old `verifiedOn` shipped together and any client
rendering "Verified on {date}" showed a badge for someone whose verification had been removed. The
regression tests were confirmed to fail on the old code before being kept.

## WP-15 — Badge: date-only on the wire · READY

The DTO comment says "the DATE ONLY" and the field serialises a full `Instant` — disclosing when desk
staff work, which is adjacent to the reviewer identity D16 keeps private, and contradicting the DTO's
own documentation. `LocalDate` in a stated zone. Small.

Same package: nothing pins the non-disclosure. One assertion that the public profile JSON contains
neither the reviewer key nor `evidenceRef` turns a future "just add the reviewer" into a red test
instead of a disclosure.

## WP-16 — Search performance · WON'T, for now

D19. Search is `contains()` in Java over every card. **Measured rather than deferred on feel**: p95
26 ms at 18 professionals against a 5 ms control, where D19's own trigger is ~200 professionals or a
latency measurement. Neither is met. The figures and the re-measure command are in D19; revisit when
the catalogue grows.

## WP-17 — Video and WhatsApp providers · BLOCKED

D17, D18. Budget for a video provider and a WhatsApp BSP, if either is wanted.

## WP-18 — Production `infranet` alias · BLOCKED

D28/D30. Whether `gateway` is already a DNS alias on production's shared `infranet`. Cannot be answered
from a workstation. Largely defused — the production compose services were renamed `hc-market-*` with
explicit container names, so a collision is impossible whatever else is on that network — but the
question itself is still unanswered on the host.

## NEW-3 — the receipt scrub is real, but its test cannot fail · DONE (unmerged)

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

## Not a package: standing constraints

- **Production is off limits.** The pipeline is not ready; `deploy/deploy-prod.sh --dry-run` is the
  only thing to run against it, and it contacts nothing.
- **Work goes on a branch with a PR.** Pushing `main` publishes five images to GHCR.
- **Quality is `jacserver`, which is this workstation** at 127.0.0.1 — `./quality/startup.sh --local`,
  no ssh. It is the last real gate, and it has now found three defects that every test suite passed.
