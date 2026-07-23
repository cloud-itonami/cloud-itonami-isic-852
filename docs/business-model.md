# Business Model: Secondary Education (Administrative Coordination)

## Classification
- Repository: `cloud-itonami-isic-852`
- ISIC Rev.4: `852` — secondary education, narrowed to back-office
  administrative COORDINATION (never grading, curriculum, discipline, or
  custody)
- Social impact: student-safety administrative-coordination family-engagement
  equitable-access

## Customer

- secondary/middle/high school office administrators and coordinators
  (public or private) looking to replace paper/spreadsheet/group-chat
  coordination with a governed, auditable workflow
- small-school operators who cannot justify a full district-scale SIS/
  communication suite

## Problem

Same problem as `cloud-itonami-isic-851` (pre-primary/primary): attendance/
scheduling/supply/staffing coordination runs on paper or ad hoc chat with no
immutable record and no structural guarantee a safety concern reaches a
human. Commercial K-12 communication/SIS suites (ParentSquare, Brightwheel,
Procare) bundle this into a much larger, sales-gated product with no public
pricing (see Revenue below).

## Offer

- `log-attendance-note`, `schedule-parent-guardian-meeting`,
  `coordinate-supply-request`, `schedule-staff-shift-proposal`,
  `flag-safety-concern` (always escalates, never auto-commits at any phase)
- an independent Governor with three permanent HARD checks (student
  registered/verified; every effect must be `:propose`; grading/curriculum/
  discipline/custody/safety-authority content permanently blocked)
- an append-only audit ledger

## Funnel (demo → fork → certified operator)

1. **Demo** — the nightly build-time-regenerated operator console.
2. **Fork / self-host** — AGPL-3.0-or-later; run the actor for one school.
3. **itonami.cloud certification** (optional) — same trust ladder as every
   cloud-itonami venture.

## Revenue

| Package | Customer | Price shape (example) |
|---|---|---|
| Self-host starter | school IT/office lead | setup ¥100k–250k + optional support |
| Managed School Ops (Starter) | one school, unlimited staff seats | ¥25,000/月 flat |

**Market-anchored**: this is the SAME K-12 back-office coordination market
`cloud-itonami-isic-851` benchmarked against on 2026-07-22 (SchoolCues,
Bloomz, ParentSquare, Brightwheel, Procare —
`90-docs/pricing-intelligence/pricing-intelligence-ledger.edn`, run-id
`pricing-intel-20260722-01`) — secondary schools buy the same category of
communication/coordination software as primary schools, just a different
grade band. No separate competitor survey was run for 852 specifically since
851's research already covers this market; see `cloud-itonami-isic-851`'s
own `docs/business-model.md` for the full comparator table and reasoning.
Same conclusion applies here: **¥25,000/月 sits inside the real
$75–~$375/mo range** those 2 published comparators imply for a several-
hundred-student school, deliberately NOT the ¥50,000–150,000/月
portfolio-uniform range used by the HR/CRM/recruiting flagships.

**Subscribe (2026-07-23)**: a live Stripe Payment Link for the Managed
School Ops (Starter) tier (¥25,000/月 flat) is available now —
[**subscribe to Managed School Ops — Starter**](STRIPE_PAYMENT_LINK_PLACEHOLDER).
This is a no-code Stripe-hosted checkout; nothing in this repo's actor code
changed. After subscribing, contact gftdcojp via an [operator-interest
issue](https://github.com/cloud-itonami/cloud-itonami-isic-852/issues/new?template=operator-interest.yml)
to arrange managed-tenant setup (manual fulfillment today, no automated
onboarding yet). **No school has claimed or subscribed to this tier yet —
this is a live, working checkout with zero paid tenants, not a claim of
existing revenue.**

## Unit Economics (worked example, illustrative)

Same shape as `cloud-itonami-isic-851`: infrastructure ≈ ¥3k–8k/月, LLM cost
bounded to proposal-time only, human approval labor is the real cost driver
(~2–4 h/月 once the rollout phase stabilizes), support ~2–3 h/月. Business
scales with number of schools per operator, not proposal volume.

## Open Participation

Anyone may fork, run the demo, self-host, submit patches, and build a local
operator business. itonami.cloud certification is required before an
operator is listed, receives leads, or runs managed tenants under the
platform brand.

## Operator Trust Levels

| Level | Capability |
|---|---|
| Contributor | patches, docs, examples |
| Self-host operator | runs their own school's instance, no platform endorsement |
| Certified operator | listed on itonami.cloud after review |
| Managed operator | may receive leads and operate customer (school) tenants |
| Core maintainer | can approve changes to governor, security and governance |

## Trust Controls

- a proposal for an unregistered/unverified student is never committed or
  even escalated
- any proposal whose effect is not `:propose`, or whose content touches
  grading/curriculum/discipline/custody/safety-authority territory, is
  permanently blocked
- a safety-concern flag always reaches a human; it can never auto-commit
- every commit, hold, escalation and approval path is auditable
- sensitive student/family data stays outside Git

## Non-Negotiables

- Do not commit real student/family PII to this repository.
- Do not bypass the Governor for production commits.
- Do not expand the proposal-op allowlist to grading, academic assessment,
  curriculum, discipline, special-education/IEP, or custody decisions
  without a new ADR.
- Do not market an uncertified deployment as an itonami.cloud certified
  operator.
