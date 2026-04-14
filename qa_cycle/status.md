# QA Cycle Status — Accounting-ZA 90-Day Demo (Fresh Tenant) — 2026-04-14-v2

## Current State

- **QA Position**: Day 14 (Days 3-14 completed via API; 2 clients ACTIVE, 3 engagements with tasks/time/budget/comments)
- **Cycle**: 1
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_2026-04-14-v2`
- **Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`
- **Focus**: Fresh tenant run — monitoring console errors and UI issues throughout.
- **Auth Mode**: Keycloak (real OIDC)
- **Method**: API-driven (Days 3-14 executed via REST API due to browser extension unavailable)

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

## Existing Data

- Org: **Thornton & Associates** (slug: `thornton-associates`, accounting-za profile)
- Users: Thandi (owner), Bob (admin), Carol (member) — passwords: SecureP@ss1/2/3
- Billing rates: 6 configured
- Clients: 2 (Sipho Dlamini INDIVIDUAL ACTIVE, Kgosi Holdings COMPANY ACTIVE)
- Templates: 5 pre-seeded (Year-End Pack, Monthly Bookkeeping, Tax Return Ind, Tax Return Co, VAT Return)
- Engagements: 3 (Sipho Tax Return, Kgosi Bookkeeping, Kgosi Year-End Pack)
- Tasks: 20 total (7+6+7 from templates)
- Time entries: 7 total (630 min / 10.5 hrs across 3 contributors)
- Comments: 2 (on Year-End Pack)
- Budget: 1 (Year-End Pack: 40 hrs / R60,000 ZAR, 7.5% consumed)
- Invoices: 0

## Gap Tracker

| GAP_ID | Day / Checkpoint | Severity | Status | Summary | Owner | Retries |
|--------|------------------|----------|--------|---------|-------|---------|
| GAP-V2-01 | Day 0 / setup | LOW | OPEN | Bob assigned Member role instead of Admin during JIT creation — invitation role not picked up. Fixed via DB UPDATE for this run. Likely due to invite link -> KC registration -> login timing issue. | Dev | 0 |
| GAP-V2-02 | Day 4 / checklist | LOW | OPEN | All FICA checklist items require document uploads. Cannot complete in API-only QA mode. Used `skip` workaround. Retest in browser mode. | QA | 0 |
| GAP-V2-03 | Day 4 / custom fields | LOW | OPEN | Test plan references `acct_entity_type`/`COMPANY_PTY_LTD` — correct slug is `entity_type`, correct value is `PTY_LTD`. Plan needs update. | Product | 0 |
| GAP-V2-04 | Day 4 / activation | LOW | OPEN | Customer activation requires `city` field. Prerequisite not obvious until transition attempt fails with 422. | Product | 0 |
| GAP-V2-05 | Day 12 / comments | LOW | OPEN | PROJECT-level comments require SHARED visibility, not INTERNAL. Test plan assumed INTERNAL. | Product | 0 |

## Legend

- **Status**: OPEN → SPEC_READY → FIXED → VERIFIED | REOPENED | STUCK | WONT_FIX
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-14 — Cycle v2 initialized. Fresh tenant run on main after PR #1036 merge.
- 2026-04-14 — Infra Agent: Dev stack started, all data cleaned, services UP.
- 2026-04-14 — QA Agents (2 turns): Completed Day 0 onboarding via browser (access request, OTP, approval, 3 KC registrations, settings, billing rates). Ran out of context before committing — state preserved in DB. Sipho Dlamini created + onboarded.
- 2026-04-14 — Manual fixes: Bob's role corrected to Admin (was Member due to invitation timing). Sipho activated to ACTIVE via DB. GAP-V2-01 logged.
- 2026-04-14 — QA Agent: Completed Days 3-14 via API. Created Kgosi Holdings (COMPANY, ACTIVE). Created 3 engagements from templates (7+6+7 tasks). Logged 10.5 hrs across 3 users. Set budget on Year-End Pack. Posted 2 comments. GAP-V2-02 through V2-05 logged.
