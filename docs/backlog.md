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
| **WP-12** | Payments: the zero-amount booking | DONE | D44 — reviewed 2026-09-03, four findings, all fixed |
| **WP-13** | Payments: provider choice and Act 987 | PARTLY DONE | D45 — registry, choice, route, permit and secrets built; the three adapters are seams awaiting real documentation, and Act 987 is a question for a person. Reviewed 2026-09-04, five findings, all fixed |
| **WP-14** | Verification badge | DONE | — |
| **WP-15** | Badge: date-only on the wire | READY | — |
| **WP-16** | Search performance | WON'T (measured) | — |
| **WP-17** | Video and WhatsApp providers | READY (spec only) | D37 — cost both, build neither |
| **WP-18** | Production `infranet` alias check | CLOSED | D37 — the rename made it moot |
| **NEW-1** | A retry reported rows re-written, not rows that held data | DONE | D39 |
| **NEW-2** | Catalog's receipt omitted what it deleted | DONE | D39 |
| **NEW-3** | A privacy test that could not fail, over a leak that was real | DONE | D40 |
| **NEW-4** | Both end-to-end scripts could only address the dev estate | DONE | D46 |
| **NEW-5** | A passing `verify-cycle.sh` made `--verify` report a fault | DONE | D46 |
| **NEW-6** | A refusal that offered the one name it withholds | DONE | D46 |
| **NEW-7** | "Sessions brokered" was not live, under a LIVE banner | DONE | D46 |

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

## WP-12 — Payments: the zero-amount booking · DONE (unmerged)

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

**Deliberately not built: the three adapters' wire formats.** WP-13 had no network access, no provider
account and no credentials, so Paystack's, Hubtel's and MTN MoMo's documentation could not be read and
none could be called. The classes exist, extend `ProviderAwaitingIntegration`, fail closed on every
call, and carry a documented list of exactly what each still needs — the authorization call and its
response, which field is the durable handle, the status vocabulary and its mapping, the callback
payload, and the signature algorithm with what bytes it covers. A signature check or a status mapping
written from plausibility would have compiled, passed the mocks written to match it, and been fiction
on the one path where a customer's money is already committed.

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

**Still open:**

- an implementer with credentials, per adapter, working from the lists on the three classes;
- Act 987 itself, and with it whether settlement is split-at-capture or reconciled afterwards;
- no run against the quality box, and no live provider — the fifth package in a row to say so;
- no endpoint publishes the provider list. Deliberate (D45): no screen asks for one, and the 400 that
  demands a choice names what there is to choose from. Revisit when a payment screen exists;
- `PENDING_PAYMENT` is still a dead end for the customer's cancel (D43), because releasing a live
  authorization needs a provider that can be asked and none of the three can be.

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
never stored" asserted directly, true whether or not the box has been exercised, and unsatisfiable
by a sibling app on a stolen hostname. `verify-cycle.sh` also states what it wrote and how to
reseed, because half of this defect was a tool that changed an estate and did not say so.

**The cost, stated:** `--verify` reports rather than fails when somebody has hand-written reviews
into the box. The collision check is stronger than before, not weaker — `at least 63` fails on a
non-number exactly as `== 63` did.

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

---

## Not a package: standing constraints

- **Production is off limits.** The pipeline is not ready; `deploy/deploy-prod.sh --dry-run` is the
  only thing to run against it, and it contacts nothing.
- **Work goes on a branch with a PR.** Pushing `main` publishes five images to GHCR.
- **Quality is `jacserver`, which is this workstation** at 127.0.0.1 — `./quality/startup.sh --local`,
  no ssh. It is the last real gate, and it has now found **seven** defects that every test suite
  passed — three at WP-07/WP-09, four more in the run of `1eadc7a` (D46). Two of that four were in
  the gate's own tooling, which is the argument for running it every package rather than every fifth:
  the box cannot find anything while the scripts that exercise it cannot address it.
