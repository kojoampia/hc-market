# Backlog — hc-market

Every open item in this repository, folded into work packages. Sources: `docs/decisions.md` D1–D34,
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
| **WP-04** | Erasure: pepper the pseudonym | DONE (unmerged) | — |
| **WP-05** | Erasure: the notifications a repeat booking hides | READY | — |
| **WP-06** | Erasure: a durable record in booking and catalog | READY | — |
| **WP-07** | Erasure: orchestration across the three services | BLOCKED | architect — needs service-to-service auth or the hc-admin desk |
| **WP-08** | Erasure: what an erased person who keeps their account is | BLOCKED | product |
| **WP-09** | Erasure: retention periods and lawful basis | BLOCKED | counsel |
| **WP-10** | Payments: the seam can complete a lifecycle | READY | — |
| **WP-11** | Payments: asynchronous confirmation | READY | — |
| **WP-12** | Payments: the zero-amount booking | READY | — |
| **WP-13** | Payments: provider choice and Act 987 | BLOCKED | counsel + architect |
| **WP-14** | Verification badge | DONE | — |
| **WP-15** | Badge: date-only on the wire | READY | — |
| **WP-16** | Search performance | WON'T (measured) | — |
| **WP-17** | Video and WhatsApp providers | BLOCKED | architect — budget |
| **WP-18** | Production `infranet` alias check | BLOCKED | architect, on the host |

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

**Loose end for the operator:** `deploy-prod.sh` renders only non-secret values into the host `.env`,
exactly as it does for `JWT_BASE64_SECRET`, so `HC_PRIVACY_PEPPER` must exist in the production host's
environment before the next production deploy or the stack refuses to come up.

## WP-05 — Erasure: the notifications a repeat booking hides · READY

Confirmed by the code review and **not yet fixed**. Notifications *about* an erased customer that sit
in the professional's bell menu are found through the customer's own conversations' `bookingReference`.
But `openThreadIfNone` dedupes by professional, so a customer's **second** booking with the same
professional never appears as any conversation's `bookingReference` — and that booking's "Ama Mensah
asked for…" notification is therefore missed. Repeat bookings with one professional are not an exotic
case; they are the product working.

Fix: union the deep links from the customer's *own* notifications (collected before they are re-keyed)
with the conversation-derived set. Test: two bookings, one professional, one thread — assert both
professional-side notifications are redacted. It fails today.

## WP-06 — Erasure: a durable record in booking and catalog · READY

Messaging records `erased_subject` with an `erasedAt`; booking and catalog record the act only in a log
line and an HTTP response body that evaporates with the request. For an irreversible action with legal
significance, that is thin. The same register would also give those two services the protection
`erased_subject` gives messaging, if either ever consumes an event that writes a customer login.

## WP-07 — Erasure: orchestration · BLOCKED

A complete erasure is three separate desk calls, and calling one without the others leaves a partially
erased customer. There is no orchestrator because this estate has no service-to-service authentication,
so an endpoint that fanned out would need a mechanism that does not exist. Sequencing belongs in the
`hc-admin` desk. Related: there is no back-sweep for anyone erased before `erased_subject` existed — on
quality that was test data only, and it has been cleared.

## WP-08 — Erasure: the still-active account · BLOCKED

Erasure does not touch the gateway's user store, so an erased person can log in and book again.
Messaging would pseudonymise the new booking's thread while booking and catalog store the real login —
the estate disagreeing with itself about whether someone exists. Either erasure implies account
deactivation as a documented fourth desk step, or the register applies only to events older than
`erasedAt`. Both are product decisions, and neither is implemented.

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
