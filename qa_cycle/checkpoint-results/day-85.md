# Day 85 — Firm final closure paperwork

**Cycle**: 2 (2026-05-14, branch `bugfix_cycle_2026-05-13`)
**Actor**: Thandi Mathebula (firm `:3000`, Keycloak auth)
**Result**: **PASS**

## Checkpoints

| # | Checkpoint | Result | Evidence |
|---|---|---|---|
| 85.1 | Closure letter attached Day 60 | **PASS** | `matter-closure-letter-dlamini-v-road-accident-fund-2026-05-14.pdf` (1.6 KB) visible in Documents tab, uploaded May 14, 2026. `statement-of-account-dlamini-v-road-accident-fund-2026-05-14.pdf` (5.0 KB) also present. Total 7 documents on matter. |
| 85.2 | Final closing letter / thank-you correspondence (if required) | **PASS** (N/A) | Closure letter already attached from Day 60. No thank-you template in the legal-za doc-template pack. Available templates verified: Engagement Letter (3 variants), Project Summary Report, Notice of Motion, Founding Affidavit, Matter Closure Letter, Statement of Account, Offer to Purchase, Deed of Transfer, Power of Attorney to Pass Transfer, Bond Cancellation Instruction. Tenant workflow does not require additional final correspondence. |
| 85.3 | Matter retention policy persists correctly | **PARTIAL** | Retention banner on matter Overview reads: "Retention clock started on **14 May 2026**. Your firm's matter-retention period isn't configured yet, so the scheduled deletion date can't be computed. Configure retention period -->" with link to `/org/mathebula-partners/settings/data-protection`. The `closedAt` date is correctly captured. The absolute `end_date` is uncomputable because the firm-level retention period is unconfigured. Same as prior cycle — scenario-amendable (5-year config is a Settings concern, not a closure-flow invariant). |
| 85.4 | Audit log filters by actor work for BOTH firm users AND portal contacts | **PASS** | **Matter Activity tab**: 90-day lookback selected. Actor filter combobox offers 3 actors: Bob Ndlovu (firm), Sipho Dlamini (portal), Thandi Mathebula (firm). Filtering by Sipho Dlamini shows 13 portal events: portal.document.downloaded x2 (Day 61), portal.request_item.submitted x4 (Day 4 + Day 46), portal.document.upload_initiated x4 (Day 4 + Day 46), portal.invoice.paid x1 (Day 30). All portal actions correctly recorded. **Org-level Audit Log** (`/settings/audit-log`): 88 total events across 2 pages. Portal Contact events include portal.document.downloaded, portal.request_item.submitted, portal.document.upload_initiated, portal.invoice.paid — all attributed to "Portal Contact" actor label. Firm-side events attributed to named users (Thandi Mathebula, Bob Ndlovu). Event types span full lifecycle: matter creation, info requests, trust deposits/payments, time entries, disbursements, billing runs, invoices, payments, court dates, customer lifecycle transitions, matter closure, document generation. |

## Summary

- Matter RAF-2026-001 confirmed **CLOSED** (status badge "Closed", Closure history shows "May 14, 2026 / Concluded")
- Closure documents (closure letter 1.6 KB + SoA 5.0 KB) both attached and downloadable from Documents tab
- Retention clock started correctly on closure date; end_date requires firm-level retention period config (unconfigured)
- Activity tab supports per-actor filtering — portal contact (Sipho Dlamini) is a first-class filterable actor alongside firm users
- Org-level audit log has 88 events covering the full 85-day lifecycle with both firm and portal actors
- Console: minor errors present (non-blocking, related to prior navigation)
- No new gaps filed — all checkpoints pass or match known state from prior days
