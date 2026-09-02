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
| **WP-05** | Erasure: the notifications a repeat booking hides | DONE (unmerged) | D36 |
| **WP-06** | Erasure: a durable record in booking and catalog | DONE (unmerged) | D39 |
| **WP-07** | Erasure: orchestration across the three services | DONE (unmerged) | D38 |
| **WP-08** | Erasure: what an erased person who keeps their account is | READY | unblocked by D37; substrate built by D39 — scope by the **booking's** age against `erasedAt` |
| **WP-09** | Erasure: retention periods and lawful basis | BLOCKED (narrowed) | counsel — the two coded judgements are ratified by D37 |
| **WP-10** | Payments: the seam can complete a lifecycle | READY | — |
| **WP-11** | Payments: asynchronous confirmation | READY | — |
| **WP-12** | Payments: the zero-amount booking | READY | — |
| **WP-13** | Payments: provider choice and Act 987 | READY (large) | D37 — Paystack, Hubtel and MoMo direct, customer chooses. **Depends on WP-11** |
| **WP-14** | Verification badge | DONE | — |
| **WP-15** | Badge: date-only on the wire | READY | — |
| **WP-16** | Search performance | WON'T (measured) | — |
| **WP-17** | Video and WhatsApp providers | READY (spec only) | D37 — cost both, build neither |
| **WP-18** | Production `infranet` alias check | CLOSED | D37 — the rename made it moot |
| **NEW-1** | A retry reported rows re-written, not rows that held data | DONE (unmerged) | D39 |
| **NEW-2** | Catalog's receipt omitted what it deleted | DONE (unmerged) | D39 |

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

## WP-08 — Erasure: the still-active account · READY (answered by D37)

Erasure does not touch the gateway's user store, so an erased person can log in and book again.
Messaging would pseudonymise the new booking's thread while booking and catalog store the real login —
the estate disagreeing with itself about whether someone exists.

**Answered: a booking made after the erasure is stored under the real login; everything that existed
before it stays pseudonymised.** Someone who books again has chosen a new relationship, and the
erasure covered what existed when it ran. Account deactivation was the alternative and was not taken:
it would have made "erased" a tidier state at the cost of locking out somebody who came back.

**The substrate exists as of D39.** Booking and catalog now hold an `erased_subject` register with an
`erasedAt`, matching messaging's, so the comparison this package needs — the booking's own `raisedAt`
against the time of the erasure — can be made in the service that holds both. D39 also states the
condition attached to it: those two registers are written and never read today, which is why neither
has messaging's `ErasureRegisterGuard`. **The first thing WP-08 does is decide whether the service it
makes into a reader needs that guard**, because an unpeppered service that consults a register answers
"not erased" about people it erased, which is the failure D35 exists to prevent.

**Scope it by the BOOKING's age, not the event's** — D37's first wording said to compare the event's
timestamp against `erasedAt`, and a review caught that this means something nobody intended. Every
later event on a booking already open when the erasure ran is timestamped after `erasedAt`, so an
event-timestamp rule puts the customer's real login and name back one lifecycle step at a time, and
breaks D36's guarantee that its residual does not grow. Either carry the booking's `raisedAt` in the
outbox payload and compare that, or treat a `bookingRef` messaging already holds rows for as
predating the erasure. The first states the fact rather than deducing it.

## WP-09 — Erasure: retention and lawful basis · BLOCKED

Retention periods, lawful basis, controller registration, data residency. Plus two judgement calls the
code has taken and flagged: the **review body** is not erased (public speech about a professional), and
`Dispute.resolution` is kept (the brokerage's record of a financial decision, underpinning a
compensating ledger entry). Both may name the customer.

## WP-10 — Payments: the seam can complete a lifecycle · READY

D15, and the sharpest finding of the payment review. `authorizePayment` uses the outcome's state and
reason and **discards `providerReference`** — the handle `capture`, `refund` and `status` all require.
With no payment table by design, and no log line either, the day a real provider returns `AUTHORIZED`
the money is committed and the platform holds nothing to capture or refund it with.

Also in this package: no compensating action if `creator.create` throws after an authorization (money
taken, no booking, no reference to void it); no `void`/`cancel` operation on the port, which is a
distinct call at real providers and is not a settlement-model choice; `refund` carries an amount with
no currency, against the house rule that money is minor units *plus* an ISO code; `capture` cannot
express a partial capture; and `PaymentIntent`'s javadoc promises an idempotency key that the call site
defeats by minting the reference per request.

## WP-11 — Payments: asynchronous confirmation · READY

Paystack returns an authorization URL the customer must visit; Hubtel and MTN MoMo raise a prompt on
the customer's phone and confirm by webhook. None can truthfully return `AUTHORIZED` or `DECLINED` from
a synchronous `authorize`. The shape is common to all three, so it can be built without choosing one: a
`PENDING`/`REQUIRES_ACTION` state with `permitsBooking` decided explicitly, a next-action field on the
outcome, and a stated webhook contract. Deciding whether a booking may exist while payment is pending
is much cheaper now than after a provider is wired.

## WP-12 — Payments: the zero-amount booking · READY

Two seeded services are genuinely free, and "from ₵0" is correct rather than a bug. `authorizePayment`
runs unconditionally, so a real provider gets asked to authorize 0 pesewas and rejects it — every free
booking in the estate becomes uncreatable the day a provider is configured, and no test would notice.
One condition and one test.

Adjacent, same package: provider exceptions currently become 500s rather than the `FAILED` path; and
`@ConditionalOnMissingBean` in a user `@Configuration` is order-sensitive, so the real provider should
be a component-scanned bean — worth a javadoc line before somebody discovers it as a startup failure
that differs between a laptop and CI.

## WP-13 — Payments: provider and Act 987 · BLOCKED

Provider choice and contract, and whether a split-settlement model clears Act 987. The seam is
deliberately agnostic between split-at-capture and reconcile-afterwards, and **nothing on
`PaymentProvider` pays the professional** — that omission is what makes it survivable, and it should
survive this decision rather than be pre-empted by it.

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

---

## Not a package: standing constraints

- **Production is off limits.** The pipeline is not ready; `deploy/deploy-prod.sh --dry-run` is the
  only thing to run against it, and it contacts nothing.
- **Work goes on a branch with a PR.** Pushing `main` publishes five images to GHCR.
- **Quality is `jacserver`, which is this workstation** at 127.0.0.1 — `./quality/startup.sh --local`,
  no ssh. It is the last real gate, and it has now found three defects that every test suite passed.
