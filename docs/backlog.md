# Backlog — hc-market

Every open item in this repository, folded into work packages. Sources: `docs/decisions.md` D1–D78,
the two code reviews of 2026-09-01, and the verification runs against the quality box.

**This is a derived document.** `decisions.md` holds the reasoning and stays the record; this holds
only *what is left*, in a shape you can pick work from. Where the two disagree, `decisions.md` wins —
and where either disagrees with the code, the code wins.

**Status vocabulary.** `DONE` — built, tested, and verified against a running estate. `IN PROGRESS` —
being worked now. `READY` — specified, unblocked, nobody is holding it. `BLOCKED` — waiting on a named
person, not on engineering. `WON'T` — considered and deliberately not done, with the reason.
`PARTLY DONE` — some of it is built and verified while the rest is blocked on a named person, so it is
neither `IN PROGRESS` (nobody is holding it) nor `BLOCKED` (part of it shipped); the row says which half
is which. `CLOSED by decision (D<n>)` — the item asked a question rather than named work, and the answer
is ratified; no code shipped, so it is not `DONE`.

**Two of these were in use before they were declared here**, which is the same defect this repository
keeps finding in its own checks and comments: a statement of what the vocabulary *is* that the file
itself contradicts. Added rather than relabelled — `PARTLY DONE` and `CLOSED by decision` each carry a
distinction the declared five cannot express, so the declaration was the wrong half.

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
| **NEW-8** | The prototype's professional workspace is demo-only in live mode | WON'T (documented) | D46 §5 |
| **NEW-9** | Four seeders shift every date by the JVM's idea of today | DONE | D48 — zone closed in all four; the shared-`today` half deliberately not built, with its triggers named. Reviewed 2026-09-05, nine findings, all applied |
| **NEW-10** | Payout writes `ledger.earned_on` in the JVM's calendar and the seeder's in Accra's | DONE | D51 — all six closed, three writes and three renders, across payout and booking. The data question is **answered**: no stored row anywhere was written outside Accra's calendar, so no migration. Catalog's four are NEW-12 |
| **WP-19** | Production deployment configuration, to sibling parity | PARTLY DONE | D49 — `deploy/prod-server/` built, five defects in the deploy path fixed, then reviewed 2026-09-05 and eight more applied, one of them blocking (a failing smoke test triggered an automatic rollback). **Nothing has ever been run against a host**; the fifteen things a person must still do are in that directory's README |
| **NEW-11** | A second payment attempt for one booking would reuse Paystack's reference | WON'T, until there is a second attempt | D50 — no such path exists; the day one is added the suffix goes in with it. Opened by the D50 review |
| **NEW-12** | Catalog's four implicit-zone reads, one of which stores a date | DONE | D52 — all four closed, `MarketCalendar`'s fourth copy, and the CI check now scans **every** service with no per-file exemption anywhere. The estate has no implicit-zone read left. The data question is **re-established, not cited, and its answer differs from D51's**: the quality box holds **two** rows written by the defective line, both dated correctly because the container's zone is `Etc/UTC` — "written by the defect and right", not "nothing was written". No migration |
| **NEW-13** | The brokerage rate is struck when the event is consumed, not when the booking completed | DONE | D53 — the act's instant is on the wire (`bookingCompletedAt`, `bookingCancelledAt`) and nothing on the pricing path reads a clock. An event without one falls back to the envelope's `occurredAt` **at WARN**, never to now; with neither it is refused. Ten tests, all watched red first; one CI check, watched firing three ways |
| **NEW-14** | A professional's own calendar opens on Accra's day, not theirs | CLOSED by decision | D55 — spec §13 #8 ratified 2026-09-07. A window *start* is a question about the page being read, so `MARKET_ZONE` is now **chosen** there rather than merely unchallenged. **No code change**: the two `ProWorkspaceResource` defaults were already right |
| **NEW-15** | Any authenticated user can change the brokerage's commission rate — **and eight other things** | DONE | D54 — the scope was **nine, not one**: `BrokerageConfig`, `Ledger`, `Payout`, `Credential`, `AvailabilitySlot`, `ServiceOffering`, `Highlight`, `Message` and `Conversation`, each with four write mappings and zero authorization. Measured in-process at 200/200/201/200 apiece before deletion — 36 assertions, 36 red. All nine deleted with their generated ITs, argued individually; three `GeneratedCrudIsNotAnApiIT` guards, each door mutated **separately**. **The root cause was the control**: one CI check now derives the expected set from `jdl/*.jdl` and demands, per entity, a delete-table row or real authorization. Eight mutations watched. Opened a tenth family as NEW-17 |
| **NEW-16** | The receipt strikes its split to the day and the ledger to the instant | DONE | D56 — `at` (an `Instant`) beside `on`, **both sent**, `at` preferred, and neither present is a **400** rather than `Instant.now()`. `on` stays because a new booking calling an old payout would otherwise fall through to that service's own clock — NEW-13 rebuilt one service over. The two selectors are merged into `BrokerageTerms` with an explicit tie-break (newest `id`), closing a non-determinism neither copy could see. First tests for an endpoint that had none: 10 ITs through the real binder, **7 red first**; one CI check, all five assertions watched firing — and running it found the check itself was banning a correct `Instant.now()` |
| **NEW-17** | The five generated Kafka sample resources are unauthenticated write endpoints | DONE | D59 — all five **deleted**, gateway included, with the five ITs that asserted the hole worked. **What `/publish` did is established, and both of D54's readings of it were wrong**: `application.yml` puts `kafka` in `spring.profiles.group.dev` *and* `.prod`, so the profile is active in every environment — read off the running quality container — and the sibling bindings from the same file have auto-created their topics on the shared broker. The write was real. The gateway's `/consume` was the decision rather than the deletion, and it is answered by a measurement: `sse-topic`'s end offset is **0**, so it never carried a byte. `broker.KafkaConsumer` and `KafkaProducer` **stay** as orphaned generated classes with no HTTP door. Second CI check, derived from `baseName` + `messageBroker` rather than from entities, with a `find` sweep closing its own rename fail-open; **14 mutations watched**, and the first harness run reverted the very rows the check depends on. Five guards, each service mutated separately. Opened **NEW-21** |
| **NEW-18** | Nothing can create a `BrokerageConfig`, and payout cannot price a booking without one | DONE | D57 — **shape (2), the seeded-once row**, argued against a Liquibase changeset (a rate is not schema, and a changeset cannot read the environment, so it *forces* a code constant) and against the append-only `ROLE_BROKERAGE` resource (**it bootstraps nothing** — a fresh estate still waits on a person, and the deploy check would then fail a healthy stack). `BrokerageBootstrap` writes one row into an **empty** table on every environment including `prod`, at `SmartLifecycle` phase `MIN_VALUE` so the consumer's container cannot start first. The founding values are code constants the environment **may** override — so nothing can be got wrong by omission, and a malformed one refuses startup. `effectiveFrom` is `Instant.EPOCH` and is deliberately *not* an input: it is the one field where a well-formed wrong value brings the defect back. The seed no longer writes or deletes the row at all. Preflight shipped with the remedy, never before it — and its first version was **wrong**, found by an actual `prod` boot: reading the aggregate `/management/health` would have rolled back a healthy stack whenever the Kafka binder was down. It reads `brokerage.termsInForce` from `/management/info` instead. 24 unit tests plus 4 IT cases, every guarded thing mutated separately; one CI check, watched firing six ways including on a comment |
| **NEW-19** | Two sites convert an appointment with `ZoneOffset.UTC` and ignore `Booking.zoneId` | DONE | D58 — both read `booking.getZoneId()` now, and the **two sites are one derivation**: `cancellationPreview` already called `isLate` while computing the same instant a second time, so the resource asks `BookingWorkflow.scheduledAt` for it. The decision the item reserved is **answered by construction, re-established rather than inherited**: `zone_id` is `NOT NULL` with **no column default** (the item and D55 both say otherwise), written at exactly one live site and never recomputed, so no in-flight booking's quoted boundary moves and nothing needed migrating. A zone tzdb cannot read falls back to `MARKET_ZONE` with a WARN rather than making a booking impossible to cancel. 9 tests east and west of UTC, both sites **mutated separately** — the resource alone is red at the endpoint while every unit test stays green. One CI check, watched firing three ways, for the third site no test can cover. Reviewed 2026-09-09, three non-blocking findings, all applied — the sharpest being a **pre-existing** test whose fixture set no zone and so began routing ten assertions through the new fallback. Opened **NEW-20** for the write side |
| **NEW-20** | A booking stores whatever zone the catalogue hands it, and nothing parses it | DONE | D60 — the decision is a **split**, not either shape the item offered: *absent is a state, unreadable is an error*. Null or blank still defaults (a catalogue one release behind, D56's deployment); a non-blank value tzdb cannot read is **502 and no booking**, because no release of catalog produces one and this estate refuses at a boundary (D45/D50/D57, and D22 on this very endpoint). `CapturedZone` follows `SlotTime`; it stores `ZoneId.of(x).getId()`, so the column round-trips — **measured**, all 604 region ids are their own id while the offset spellings normalise. `DEFAULT_ZONE_ID` deleted, default is `MARKET_ZONE`, three zone constants stay three. **The read-side fallback is NOT dead code** and the item was wrong to expect it: 302 rows were written before this, and a parse at one door is not a check constraint — said on `zoneOf` itself. 25 unit + 5 IT, **four mutations run separately** (the derivation can be right while the door is wrong, and it was). Catalog gets a test on its sole writer's constant plus a CI check scanning **all five** services, watched firing seven ways, so the day catalog grows onboarding its `setZoneId` is red. **Reviewed 2026-09-09, sound, two findings applied**: the sweep was line-based so a **wrapped** `.zoneId(\n raw)` evaded it — and prettier formats Java here, so that shape arrives without intent — and the ERROR log did not delimit the value, hiding the trailing-space fixture in the one place the refusal sends an operator. The check's service list is **enumerated, not derived**, now said so in both places |
| **NEW-21** | The generated Kafka sample writes to the shared broker with no caller at all, and has written 5.6 million times | READY | D59 — found while establishing NEW-17, and **larger than the door NEW-17 closed**. `broker.KafkaProducer` is a generated `Supplier<String>` bound to `kafkaProducer-out-0` and polled on Spring Cloud Stream's default one-second schedule, in **all five** services. Measured on the shared broker: end offset **5,603,896**, rising at **6/s** over a timed 60 s window. Nothing consumes it and nothing asked for it. The gateway's `broker.KafkaConsumer` sink is the other half — `unicast().onBackpressureBuffer()` with no subscriber since NEW-17. Not fixed there because the remedy is an edit to the generated `application-kafka.yml` (a `spring.cloud.function.definition` naming a bean that no longer exists refuses to bind), which is a regeneration-hazard row and a decision of its own |
| **NEW-22** | The gateway seeds `admin` with a published default password, in every profile including `prod` | DONE | D61 — the generated Mongock changeunit had **no `@Profile`**, and `mongock.migration-scan-package` is in the **base** `application.yml` with no `prod` override, so it ran on any fresh Mongo — which is exactly what a first production deploy creates. The hash was **measured, not assumed**: a red test proved it is bcrypt of the string `admin`, on an account carrying `ROLE_ADMIN` on the gateway whose tokens all five services accept. **Nothing was ever red and nothing would have been.** Takes **hc-professional's shape** — admin in every profile because an empty prod database has no other way in, demo accounts skipped under `prod`, idempotent `saveUserIfMissing` — which means it stops being a changeunit and becomes an `ApplicationRunner` (a changeunit is not a Spring bean, so the `Environment` and the `@Value` have nowhere to live). Mongock stays, with an empty scan package; **it tolerates that**, established by booting an IT rather than by reading. **Departs from that sibling on the open question**: `prod` with no password **refuses** rather than falling back to a derived value with a warning, following D35/D45 — hc-professional's fallback is what production gets by default there, since nothing in that repository sets the variable. The refusal is inside the missing-account supplier, so an estate whose admin already exists keeps deploying. hc-admin's `@Profile({dev,test})` rejected, and the stated reason **corrected**: it does have a prod path (`AdminBootstrapInitializer`), and its real cost is two admin-creating classes with two property names, one silently inert. 8 unit tests, a CI check whose first part sweeps for a **committed bcrypt hash** rather than for the logic — because the file is generated and a regeneration takes the logic with it — watched firing **14 ways**, each mutated separately. A neighbouring check was widened after **measuring** that `HC_[A-Z]+_DB_PASSWORD` could not see `HC_GATEWAY_ADMIN_PASSWORD`. **`hc-patient` has the identical defect and was not touched** — different repository, raise it there |

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

## WP-17 — Video and WhatsApp providers · BLOCKED

D17, D18. Budget for a video provider and a WhatsApp BSP, if either is wanted.

## WP-18 — Production `infranet` alias · BLOCKED

D28/D30. Whether `gateway` is already a DNS alias on production's shared `infranet`. Cannot be answered
from a workstation. Largely defused — the production compose services were renamed `hc-market-*` with
explicit container names, so a collision is impossible whatever else is on that network — but the
question itself is still unanswered on the host.

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

Status is `BLOCKED` rather than `READY` because what remains is a person with root, not engineering.
Nothing was destroyed by the attempt — five containers and five volumes are exactly as they were.

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
cannot run at all while NEW-31 stands. Three folded reads survive in the two files deliberately — a
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

## NEW-38 — the SHELL stripper's caller count is stale, in a file that is not its own · READY

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

---

## NEW-39 — `HEALTH_TIMEOUT` is a budget of 24 attempts and the header calls it seconds · READY

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

---

## NEW-41 — the packages table disagrees with three of its own sections, and stopped indexing after NEW-22 · READY

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
