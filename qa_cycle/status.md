# QA Cycle Status — Accounting-ZA 90-Day Demo (Fresh Tenant) — 2026-04-14-v2

## Current State

- **QA Position**: Day 90 — ALL_DAYS_COMPLETE
- **Cycle**: 1
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_2026-04-14-v2`
- **Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`
- **Focus**: Fresh tenant run — monitoring console errors and UI issues throughout.
- **Auth Mode**: Keycloak (real OIDC)
- **Method**: API-driven (Days 3-90 executed via REST API due to browser extension unavailable)
- **ALL_DAYS_COMPLETE**: true

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend (kc mode) | http://localhost:3000 | UP |
| Backend (local+keycloak profile) | http://localhost:8080 | UP |
| Gateway (BFF) | http://localhost:8443 | UP |
| Portal | http://localhost:3002 | UP |
| Keycloak | http://localhost:8180 | UP |
| Mailpit UI | http://localhost:8025 | UP |
| Postgres (docteams) | localhost:5432 | UP |

## Final Data Totals

| Metric | Value |
|--------|-------|
| Customers (Clients) | 4 (all ACTIVE) |
| Engagements (Projects) | 5 (1 COMPLETED, 4 ACTIVE) |
| Tasks | 32 template tasks + follow-ups created/cancelled |
| Time Entries | 22 (2,730 min / 45.5 hrs) |
| Invoices | 4 (all PAID, total R19,665.00) |
| Audit Events | 193 |
| Notifications | 13 (Thandi) |
| Budget | 1 (Year-End Pack: 10/40 hrs, 25%, ON_TRACK) |

## Gap Tracker

| GAP_ID | Day / Checkpoint | Severity | Status | Summary | Owner | Retries |
|--------|------------------|----------|--------|---------|-------|---------|
| GAP-V2-01 | Day 0 / setup | LOW | OPEN | Bob assigned Member role instead of Admin during JIT creation | Dev | 0 |
| GAP-V2-02 | Day 4 / checklist | LOW | OPEN | All FICA checklist items require document uploads. Cannot complete in API-only QA mode | QA | 0 |
| GAP-V2-03 | Day 4 / custom fields | LOW | OPEN | Test plan references wrong custom field slugs | Product | 0 |
| GAP-V2-04 | Day 4 / activation | LOW | OPEN | Customer activation requires `city` field | Product | 0 |
| GAP-V2-05 | Day 12 / comments | LOW | OPEN | PROJECT-level comments require SHARED visibility, not INTERNAL | Product | 0 |
| GAP-V2-06 | Day 16 / template | LOW | OPEN | No "Trust Financial Statements" template in accounting-za pack | Product | 0 |
| GAP-V2-07 | Day 34 / profitability | MED | OPEN | Profitability incomplete: rate snapshots NULL for March entries, costValue NULL for all | Dev | 0 |
| GAP-V2-08 | Day 30 / budget | LOW | RESOLVED | Budget hoursUsed was null, now shows 10.0 hrs correctly | Dev | 0 |
| GAP-V2-09 | Day 36 / invoice lines | LOW | OPEN | Invoice lines from time entries have unitPrice=0 when rate snapshots null | Dev | 0 |
| GAP-V2-10 | Day 45 / invoice prereq | LOW | OPEN | Invoice creation requires `city` on customer profile | Product | 0 |
| GAP-V2-11 | Day 72 / task automation | LOW | OPEN | Cascading "Follow-up" tasks from automation rule on task completion | Dev | 0 |
| GAP-V2-12 | Day 72 / task claim | LOW | OPEN | Claim rejects already-assigned tasks (assigned member cannot claim their own task) | Dev | 0 |
| GAP-V2-13 | Day 80 / dashboard | LOW | OPEN | No /api/dashboard endpoint (404) | Dev | 0 |
| GAP-V2-14 | Day 80 / profitability | LOW | OPEN | No customer/org-level profitability aggregation endpoints | Dev | 0 |
| GAP-V2-15 | Day 58 / invoice send | LOW | OPEN | Invoice send validation requires 5 customer fields, only 1 set | Product | 0 |

## Skipped Steps (require browser)

- Day 65: Portal magic link generation and terminology check
- Day 87: Budget alert automation trigger (Year-End Pack only at 25%, needs ~22 more hrs to trigger 80%)
- Day 90.4: Tier removal sweep (Settings > Billing page)
- Day 90.5: Console errors
- Day 90.6: Mailpit sweep

## Legend

- **Status**: OPEN -> SPEC_READY -> FIXED -> VERIFIED | REOPENED | STUCK | WONT_FIX | RESOLVED
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-14 — Cycle v2 initialized. Fresh tenant run on main after PR #1036 merge.
- 2026-04-14 — Infra Agent: Dev stack started, all data cleaned, services UP.
- 2026-04-14 — QA Agents (2 turns): Completed Day 0 onboarding via browser. Sipho Dlamini created + onboarded.
- 2026-04-14 — Manual fixes: Bob's role corrected to Admin. Sipho activated via DB. GAP-V2-01 logged.
- 2026-04-14 — QA Agent: Completed Days 3-14 via API. 2 customers, 3 engagements, 10.5 hrs. GAP-V2-02 through V2-05 logged.
- 2026-04-14 — QA Agent: Completed Days 15-45 via API. 4 customers, 5 engagements, 27 hrs, 2 invoices PAID. GAP-V2-06 through V2-10 logged.
- 2026-04-14 — QA Agent: Completed Days 50-90 via API. 2 more invoices (INV-0003, INV-0004) created and paid. Ndaba VAT tasks completed. Sipho engagement COMPLETED. Utilization and My Work verified. Audit log sweep: 193 events. Terminology clean. Final totals: 22 time entries (45.5 hrs), 4 invoices (R19,665), 193 audit events. GAP-V2-11 through V2-15 logged. GAP-V2-08 RESOLVED (budget hoursUsed now works).
