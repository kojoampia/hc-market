# Cross-border transfer basis — BridgeCare Marketplace

> **STATUS: DRAFT FOR COUNSEL'S REVIEW. NOT APPROVED.**
>
> Drafted 2026-09-11 — `decisions.md` **D88**, backlog **WP-09**. D42 recorded counsel's answer that
> *"the transfer conditions are met; document what is transferred, where, and under what safeguards"*.
> This is the attempt at that document, and it **stops** at the two facts engineering cannot supply
> (§5).
>
> **hc-market only**, by decision. The same host runs five other BridgeCare services and the same
> border is crossed for each; this document does not speak for them, and §6 says what that costs.

---

## 0. Controller

| | |
| --- | --- |
| **Controller** | Jojo Addison Consultancy |
| **DPC registration** | **P0021484082** |
| **Service this document covers** | BridgeCare Marketplace, `market.abofonsa.com` |
| **Services on the same host it does NOT cover** | hc-patient, hc-professional, hc-admin, the public site, hc-crowdfund — see §6 |

## 1. What is transferred

Everything the Marketplace processes. There is no split store and no data kept in Ghana — D42
considered and rejected that option, because care summaries in one country and the rest abroad would
have meant a second data store, a cross-border join and a new failure mode on every booking read.

The categories are enumerated in `docs/processing-record.md` §2. In summary: account details,
bookings and their free text, visit addresses, message bodies, published reviews, payment references,
and professionals' earnings.

**Not transferred, because it does not exist:** any health information. See the processing record's
§2.2.

## 2. Where it goes

| | |
| --- | --- |
| **Destination** | A virtual server at **199.247.5.252**, reached as `webserver` |
| **Country** | **Not confirmed to engineering.** §5.1 |
| **Provider** | **Not confirmed to engineering.** §5.1 |
| **What else runs there** | The other five BridgeCare services, one Kafka broker, one observability stack, and the production databases for this product |

> **⚠ THE TRANSFER HAS NOT HAPPENED YET.** hc-market has never been deployed. `market.abofonsa.com`
> resolves to nothing this workspace has run, and the production compose file is unexecuted
> configuration. **This document is written before the transfer rather than after it**, which is the
> right order, and it means every safeguard below is a statement about intent that a first deploy will
> either confirm or contradict.

## 3. Why the transfer happens

Operational necessity rather than choice of convenience: the organisation runs one production host for
all six services. Keeping this product's data in Ghana would mean a second host, a second deployment
pipeline and cross-border joins for a platform whose services already call one another.

## 4. Safeguards in place

These are **verified against source and configuration**. Each says how.

| Safeguard | State | Evidence |
| --- | --- | --- |
| **Data in transit to the host is encrypted** | Implemented | Deployment and administration happen over SSH only; `deploy-prod.sh` makes every remote call through `ssh` with `BatchMode` and a bounded connect timeout |
| **Public traffic is TLS-terminated at the edge** | Configured, unexercised | `deploy/prod-server/hc-market-app.conf`. Never run against the host |
| **The application is not directly reachable** | Configured | The gateway binds to loopback in the production compose; nginx is the only front door. It published on `0.0.0.0` until D49 found it |
| **Each service has its own database instance** | Implemented | Five separate PostgreSQL instances plus MongoDB, on this product's own network |
| **Databases are unreachable from other products** | Implemented | They join neither the shared `infranet` nor any other product's network — deliberate, so another product cannot resolve `hc-market-catalog-db` |
| **Every non-public endpoint requires authentication** | Implemented | Public reads are an explicit allow-list; everything else is authenticated, and internal endpoints are additionally kept off the routable surface |
| **Customer records and erasure require staff authorisation** | Implemented | `ROLE_BROKERAGE`, asserted by tests against a running container rather than against source |
| **Passwords are one-way hashes** | Implemented | bcrypt |
| **Secrets are not in the repository** | Implemented | The signing key, the privacy pepper, the administrator password and any provider secret are all required from the environment; CI refuses a committed usable key. This repository is public |
| **An erased person's identifiers are replaced with a one-way alias** | Implemented | HMAC keyed by an estate-wide secret, consistent across services, irreversible |
| **A record of every erasure is kept** | Implemented | Append-only registers in three services, with counts of exactly what changed |

## 5. What this document cannot state, and needs

**Both are facts about the organisation's arrangements, not about the code.** Neither can be drafted
from a repository, and the document is incomplete without them.

### 5.1 Who the host is, in which country, on what terms

§2 names an IP address. A transfer basis needs the **processor's identity**, the **country**, and the
**contractual terms** — specifically whether a data processing agreement exists and what it says about
onward transfer, breach notification, deletion on termination and sub-processors.

Engineering can confirm the address and that the machine is administered over SSH by this
organisation. It cannot confirm who owns it or what was signed. **Naming the wrong company in a
document a regulator may read would be worse than this gap**, which is why it is a gap.

### 5.2 Which condition under Ghana's Act 843 is being relied on

D42 records counsel's answer that the conditions are met, and **not which condition**. A transfer basis
that does not name its own ground is not a basis. This needs one sentence from counsel identifying it,
and whatever it rests on — an adequacy determination, contractual safeguards, or the data subject's
position.

## 6. Scope, and what a per-product document costs

**This document covers hc-market alone**, by decision, on the ground that engineering can only verify
this product's data flows.

The cost should be visible: **the same customer's data crosses the same border for hc-patient and
hc-professional**, on the same machine, under the same arrangements. A regulator reading six
near-identical transfer bases for one server may reasonably ask why there are six. If the organisation
prefers one estate-wide basis, this document is the template for it and §4's evidence column is the
part that would have to be re-verified per product — the safeguards are implemented in *this*
product's code and cannot be asserted for another without reading it.

## 7. Review

| | |
| --- | --- |
| **Drafted** | 2026-09-11, engineering, against source |
| **Reviewed by counsel** | **Not yet** |
| **Approved** | **No** |
| **Blocking** | §5.1 and §5.2 |
