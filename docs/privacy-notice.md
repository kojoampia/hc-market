# Privacy notice — Abofonsa BridgeCare

> **STATUS: DRAFT FOR COUNSEL'S REVIEW. NOT PUBLISHED, NOT APPROVED.**
>
> Drafted 2026-09-11 against what the code actually does — `decisions.md` **D88**, backlog **WP-09**.
> It is committed here so it can be reviewed and corrected in the open; **nothing in this repository
> publishes it**, and it must not be served anywhere until counsel has signed it off.
>
> **Read the two boxes below before reading the notice.** Both describe places where the *system* and a
> reasonable reading of this document could come apart, and both are the point of sending it for review.

> **⚠ WHAT THIS DRAFT DELIBERATELY DOES NOT CLAIM**
>
> - **No health information is collected or stored.** See §4. A booking records *that* a care summary
>   was shared, as a yes/no; it holds no conditions, no allergies and no medications. D42 obtained a
>   lawful-basis position about such data and **the platform has never held any** — that finding is
>   D88's and counsel should know it, because it changes the risk they assessed.
> - **Retention periods are stated, not enforced.** See §7. Nothing deletes anything on a schedule.
> - **Erasure is operated by a person, not by the customer.** See §8. There is no self-service button.

> **⚠ SCOPE**
>
> This notice is written for **Abofonsa BridgeCare as a whole**, because the Data Protection Commission
> registration belongs to Jojo Addison Consultancy rather than to any one service, and a customer
> experiences one brand. **Only Annex A — the BridgeCare Marketplace — has been verified against
> source code.** The other services on the same platform are named in §3 and their processing is *not*
> described here: whoever owns them must add their own annex or confirm that this notice covers them.

---

## 1. Who we are

**Jojo Addison Consultancy** is the data controller for Abofonsa BridgeCare.

| | |
| --- | --- |
| **Controller** | Jojo Addison Consultancy |
| **Data Protection Commission registration** | **P0021484082** |
| **Website** | jojoaddison.net |
| **Contact for data protection enquiries** | *to be supplied — see §11* |

## 2. What this notice covers

Abofonsa BridgeCare is a set of services for home healthcare and related non-medical services in
Ghana. This notice explains what personal data we collect, why, how long we keep it, who else sees it,
and what you can ask us to do about it.

## 3. The services

| Service | What it is | Covered by an annex here? |
| --- | --- | --- |
| **BridgeCare Marketplace** (`market.abofonsa.com`) | Connects customers with non-medical health professionals — trainers, nutritionists, therapists, home carers | **Yes — Annex A** |
| Patient subsystem (`patient.abofonsa.com`) | Patient-facing services | No |
| Professional subsystem (`professional.abofonsa.com`) | Clinician-facing services, onboarding and credentialing | No |
| Admin console (`admin.abofonsa.com`) | Internal back office | No |
| Public site (`web.abofonsa.com`, `abofonsa.com`) | Marketing, waitlist | No |
| Crowdfunding (`fund.abofonsa.com`) | Pledging and vouchers | No |

**Only Annex A is verified.** The rest are listed so that a reader knows this notice is about a
platform rather than a single website, and so the gap is visible rather than implied.

## 4. What we collect, and what we do not

### We collect

- **Your account details** — the name you sign in with, your first and last name, and your email
  address. Your password is stored only as a one-way hash; we cannot read it.
- **What you book** — who you booked, which service, the price, the date and time, and the time zone
  it is expressed in.
- **What you tell us about a booking** — an optional note, an optional "someone I care for" name, and
  a visit address when you ask for a home visit.
- **Your messages** — the text of messages you exchange with a professional through the platform.
- **Your reviews** — a rating, a review body, and the display name you publish it under.
- **A record of payment**, when a payment provider is used: a reference that provider issued. See §6.

### We do **not** collect

- **We do not collect or store health information.** A booking can record that you agreed to share a
  care summary with a professional — as a single yes/no — but **the summary itself is not stored on
  this platform**. No condition, allergy or medication is held in any of our records.
- **We do not take payment card details.** If you pay through a provider, you enter your details on
  their page and we never see them.
- **Nobody on this platform may diagnose or prescribe.** The professionals listed in the Marketplace
  are non-medical, and the service is a brokerage rather than clinical care.

## 5. Why we use it, and on what basis

We process your personal data to **perform our contract with you** — to take your booking, let you and
the professional arrange it, take payment where it applies, and keep the records the law requires us to
keep.

Where we keep financial records longer than we otherwise would, we do so **to comply with a legal
obligation**.

We do **not** rely on consent for any of the processing described here, and we do **not** use your data
for advertising or profiling.

## 6. Who else sees it

| Recipient | What they receive | When |
| --- | --- | --- |
| **The professional you book** | Your name, what you booked, your note, and your visit address if you asked for a home visit | When you make the booking |
| **Paystack** (payment provider) | Your email address and the amount, so they can take the payment | Only if a payment provider is enabled and you choose it. **No provider is enabled today** |
| **Our hosting provider** | Everything, as the operator of the servers our systems run on | Continuously |

We do not sell your data, and we do not share it with anyone else except where the law requires it.

## 7. How long we keep it

| Category | Period |
| --- | --- |
| **Financial records** — bookings, payment records, disputes | **6 years** (2,190 days) |
| **Operational data** — messages, notifications | **1 year** (365 days) |

> **⚠ These periods are a stated policy, not an applied one.** Nothing in our systems deletes data on a
> schedule today. The periods above are what we have decided and configured; enforcing them is
> outstanding engineering work, and this notice says so rather than implying otherwise. Our internal
> policy endpoint reports the same caveat.

## 8. Your rights, and how to use them

You can ask us to:

- **tell you what we hold** about you;
- **correct** anything that is wrong;
- **erase** your personal data, where we are not required to keep it;
- **restrict** or **object to** how we use it;
- **complain** to the Data Protection Commission.

> **⚠ Erasure is carried out by a member of our team, not by you.** There is no button in the product.
> You ask us, a person at our brokerage desk performs the erasure, and the system produces a record of
> exactly what was removed. That is how it works today and this notice describes it rather than
> promising self-service.

**What erasure does and does not remove.** When we erase you, your name and sign-in details are
replaced everywhere they appear, your free-text notes and addresses are deleted, and your messages are
redacted. **Two things are kept on purpose:**

- **the body of a review you published**, because it is public speech about a professional's business
  and other people rely on it. Your name is removed from it;
- **how a dispute was resolved**, because it is a record of a decision affecting another person.

## 9. Where your data is

Our systems run on a server outside Ghana, and your personal data is therefore transferred outside
Ghana. The transfer basis and the safeguards that apply to it are described in a separate document.

*See `docs/data-transfer-basis.md` — also a draft for review.*

## 10. Security

Access to the systems that hold your data requires authentication, and the internal tools that can
read or erase customer records require a specific staff authorisation. Passwords are stored as one-way
hashes.

> **⚠ One claim here is about intent rather than observation.** Encryption in transit at the public
> edge is **configured and has never been exercised**, because the Marketplace has never been deployed
> — see Annex A and the transfer basis document, which says the same thing in the same words. It is
> written this way rather than as "traffic is encrypted" so that the notice and the internal record
> cannot drift apart, and so the first deploy has something to confirm rather than something to
> contradict.

## 11. Things this draft cannot yet state, and needs from you

These are the gaps that stop this becoming a published notice. **Each needs an answer from the
organisation or from counsel, not from engineering.**

1. **A data protection contact** — an email address or postal address a data subject writes to. §1 has
   a placeholder and a notice cannot be published with one.
2. **Whether this notice covers all six services or only the Marketplace.** §3 lists them and only
   Annex A is verified. If it is meant to cover all of them, each needs an annex written by whoever
   owns it.
3. **The hosting provider's identity**, for §6. Our production server is a virtual machine at
   `199.247.5.252`; the provider and the contractual terms with them have not been confirmed to
   engineering, and naming the wrong company in a published notice would be worse than the current
   gap.
4. **Whether the DPC registration covers all six services** or is specific, which affects §1 and §3.
5. **A complaints route**, beyond naming the Commission — whether the organisation wants an internal
   step first.
6. **Whether "6 years" for financial records is the intended statutory reading.** D42 records that the
   figure was authored inside the question put to counsel and ratified for use, rather than
   independently proposed. It is live in configuration and stated above; it is not a considered opinion
   on Ghanaian law.
7. **Whether counsel wishes to revisit the lawful-basis position** now that it is established that no
   care summary content is stored (see the first box). Their position was taken on the assumption that
   conditions, allergies and medications were held.

---

# Annex A — BridgeCare Marketplace (`market.abofonsa.com`)

**This annex is verified against source code as of 2026-09-11.** It is the only part of this notice
that is.

## A.1 What the Marketplace holds about a customer

| Where | Field | What it is |
| --- | --- | --- |
| Account | `login` | The name you sign in with |
| Account | `firstName`, `lastName` | Your name |
| Account | `email` | Your email address |
| Account | `password` | A bcrypt hash. Not readable |
| Booking | `customerLogin`, `customerName` | Who booked |
| Booking | `professionalRef`, `professionalLogin`, `serviceName` | What was booked |
| Booking | `priceMinor`, `currency` | What it cost |
| Booking | `scheduledDate`, `scheduledTime`, `zoneId` | When, and in whose clock |
| Booking | `customerNote` | Free text you wrote |
| Booking | `onBehalfOf` | "Someone I care for" |
| Booking | `visitAddress` | Where a home visit happens |
| Booking | `careSummaryShared` | **A yes/no. Not a summary.** |
| Booking | `cancellationReason` | Free text, if cancelled |
| Booking | `meetingLink` | A meeting link **the professional supplied**, for an online session |
| Messaging | `body`, `recipientLogin` | Your messages |
| Catalog | `authorName`, `note` | Your published review |
| Payment record | a provider's reference | Only if a provider is used. **No card details** |

## A.2 What the Marketplace does **not** hold

- **No care summary content.** `careSummaryShared` is a boolean. Searching the entire source tree for
  a condition, an allergy or a medication field returns nothing: the words appear only in code
  comments.
- **No card or bank details.** The payment record holds a reference a provider issued and a note we
  wrote ourselves; the provider's own messages are never stored.
- **No payment provider is enabled today**, so in practice no payment data leaves the platform at all.

## A.3 Erasure, precisely

Performed by a member of the brokerage team at `POST /api/desk/customers/{login}/erase`, behind a
staff authorisation. Your sign-in name is replaced everywhere by a one-way alias, so two of your
records can still be told apart from each other without either naming you.

**Removed:** your name, your visit address, your notes, your "someone I care for", your cancellation
reason, your message bodies, your notifications, your favourites, and the meeting link on any booking.

**Kept, deliberately:** the body of a published review (your name is removed), and how a dispute was
resolved. Both are stated in §8 and both are decisions recorded in our decision log.

The system produces a receipt counting exactly what changed, so we can tell you what was done.
