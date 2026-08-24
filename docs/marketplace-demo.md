# Wellbeing Marketplace Demo — build notes

**Artefact:** `Abofonsa_BridgeCare_Marketplace.html` (single self-contained file, ~186 KB, no network, no storage APIs, all state in memory).

> **Refactored into a buildable spec:** see `claude/healthconnect-marketplace.md` — JHipster microservices, Java 25, Spring Boot 4, PostgreSQL, Kafka, with this prototype's data extracted to `demo/seed-data.json` and kept as the UX contract.

Built 10 August 2026. Brief: a clickable marketplace connecting **non-medical** health professionals with customers, using the Health Connect Platform's brokerage idea and the Abofonsa BridgeCare design language established in `claude/patient-context-demo.md`.

## Decisions taken

| Question | Answer |
|---|---|
| Perspective | **Both sides**, with a role switcher in the topbar — customer marketplace (you are Kojo Ampia-Addison) and professional workspace (you are Akosua Mensah, listing `p1`) |
| Categories | All four: Fitness & Movement, Nutrition & Lifestyle, Wellness & Therapy-adjacent, Home & Elder Care |
| Styling | BridgeCare tokens carried over verbatim; **marketplace layout**, not the patient shell |
| Fidelity | Fully clickable — hash router, 4-step booking wizard, filters, pagination, modals, charts, toasts, live messaging |

## Design language carried over

- Tokens verbatim: `--navy #0D3058`, `--gold #C59437`, `--cream #F7F4EE`, `--bg #F2F0EA`, the status greens/ambers/reds, radius and shadow scales.
- Type: Inter/Lato stack, 15px base, 800-weight tight-tracked headings, uppercase micro-labels.
- Reused components: `.card` / `.stat` / `.pill` / `.lrow` / `.opt` / `.tbl` / `.pager` / `.tabs` / `.srch` / `.chip`, modal, toast, mobile 5-slot tab bar, "interactive demo" badge, navy sidebar with gold active pill (kept **only** for the professional dashboard).
- New components in the same idiom: `.hero` search bar, `.cat` tiles, `.procard`, `.avatar` (initials, duotone, verification tick), `.stars`, `.steps` wizard, `.days`/`.slots` pickers, `.svc` rows, `.msgs` two-pane thread view, `.trust` strip, `.split` responsive two-column grid.

## The brokerage model as encoded

- 12% platform brokerage fee, included in the displayed price; the customer receipt and the professional's payout table both show the split.
- Payment is **held, not charged** at request time; released after the session, `PAYOUT_LAG` = 3 days after month end.
- Free cancellation to 24 hours; inside 24 hours a 50% late fee is shown and goes to the professional.
- Trust chain on every listing: identity, credentials, insurance, and — for home visitors — police clearance.
- A standing scope note: everyone listed is non-medical, may not diagnose or prescribe, and clinical requests route back to the BridgeCare care team rather than to a listing.

## Screens

**Customer** — Discover (hero search, four category tiles, highly rated, available soonest, how-it-works, scope note) · Browse (faceted filters with live counts, six sort orders, removable chips, pagination, empty state) · Professional profile (about, credentials, services, next 10 days, review distribution + paginated reviews with replies, your history) · Booking wizard (service → date/time → details → review & confirm → confirmation) · Bookings (four tabs, reschedule / cancel / review / receipt modals) · Messages (threads, send, simulated reply) · Saved · Account.

**Professional** — Overview (four computed stat tiles, two charts, next up, requests waiting, practice at a glance) · Requests (accept / decline with reason / propose another time / accept all) · Schedule (grouped by day, search + format filter, appointment modal, recently completed table) · Services (edit, add, publish/hide, income split, category pricing benchmark) · Earnings (month-to-date like-for-like, gross-vs-net line chart, format donut, payout table) · Reviews (distribution, public replies) · Profile (listing editor, verification checklist, working hours, scope of practice).

## Data integrity

18 professionals across 4 categories, 52 services between them; 63 hand-written reviews. **Every rating is computed from the reviews array at render time**, so a rating can never drift from its reviews — and leaving a review in the demo visibly moves the professional's average.

The professional's six-month history (256 completed sessions, ₵81,620 gross, Feb–Aug 2026) is generated once from a fixed-seed LCG, so it is identical on every load. Earnings by month, sessions by service, sessions by format, the payout table, lifetime gross/net, average session value and repeat rate are all derived from that one array — the charts and the tables cannot disagree. Availability is likewise seeded per professional, so the slots offered in the wizard are the same slots shown on the profile.

Month-to-date is compared against the **same slice of days** in the previous month, not the whole month, so the current partial month does not read as a collapse.

## Chart palette

`#256ABF` blue · `#EDA100` gold · `#1BAF7A` aqua · `#7A5AA8` violet — the palette validated for the patient demo, extended by one slot. Every chart ships direct labels **and** a table-view toggle; converging line-chart end labels are pushed apart so they stay legible.

## Verified

Headless Chromium at 1440×1000 and 390×844: no console or page errors. Exercised every route on both sides, the full booking wizard through to a created booking, accept/decline/propose, service add-edit-hide, review publish and reply, reschedule, cancel, receipt, message send-and-reply, all chart table toggles, filter facets, pagination and empty states.