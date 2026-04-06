# QA Cycle Status — 90-Day SA Law Firm Lifecycle (2026-04-06)

## Current State

- **QA Position**: Day 1, Step 1.1 (BLOCKED — Conflict Check page crashes)
- **Cycle**: 1
- **E2E Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_2026-04-06`
- **Scenario**: `qa/testplan/qa-legal-lifecycle-test-plan.md`
- **Focus**: Full 90-day lifecycle for SA law firm (Mathebula & Partners). Trust accounting, LSSA tariff, conflict checks, court calendar, prescription tracking, fee notes, reconciliation, interest runs, investments, Section 35 compliance, FICA/KYC, role-based access.
- **Auth Mode**: Mock-auth (E2E stack)

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend (mock auth) | http://localhost:3001 | UP |
| Backend (e2e profile) | http://localhost:8081 | UP |
| Mock IDP | http://localhost:8090 | UP |
| Mailpit | http://localhost:8026 | UP |
| Postgres | localhost:5433 | UP |

## Test Plan Structure

| Day | Focus | Steps | Status |
|-----|-------|-------|--------|
| Day 0 | Firm Setup (rates, tax, trust account, modules) | 0.1–0.23 | DONE (7 gaps) |
| Day 1 | First Client Onboarding (conflict, FICA, matter, engagement letter) | 1.1–1.28 | BLOCKED at 1.1 |
| Day 2-3 | Additional Clients (Apex, Moroka, QuickCollect — 6 matters) | 2.1–2.24 | NOT_STARTED |
| Day 7 | First Week Work (time logging, court date, comments, My Work) | 7.1–7.25 | NOT_STARTED |
| Day 14 | Trust Deposits & Conflict Detection | 14.1–14.24 | NOT_STARTED |
| Day 30 | First Billing Cycle (fee notes, tariff, trust transfer, budget) | 30.1–30.33 | NOT_STARTED |
| Day 45 | Reconciliation & Prescription | 45.1–45.22 | NOT_STARTED |
| Day 60 | Interest Run & Second Billing | 60.1–60.29 | NOT_STARTED |
| Day 75 | Complex Engagement & Adverse Parties | 75.1–75.21 | NOT_STARTED |
| Day 90 | Quarter Review & Section 35 Compliance | 90.1–90.40 | NOT_STARTED |

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Notes |
|----|---------|----------|--------|-------|----|-------|
| GAP-D0-01 | No legal matter templates (Litigation, Deceased Estate Admin, Collections, Commercial) seeded by legal-za profile | HIGH | OPEN | Dev | — | Blocks Day 1 steps 1.16-1.20 and all subsequent matter creation from template |
| GAP-D0-02 | Trust Accounting is "Coming Soon" stub — cannot create trust accounts or set LPFF rate | HIGH | OPEN | Dev | — | Blocks Day 14, 30, 45, 60, 90 trust-related steps |
| GAP-D0-03 | No Settings > Modules page to verify/toggle legal modules | LOW | OPEN | Dev | — | Modules appear enabled via sidebar links |
| GAP-D0-04 | "Projects" group header not renamed to "Matters" in sidebar | LOW | OPEN | Dev | — | Cosmetic; child link "Matters" is correct |
| GAP-D0-05 | Dashboard cards say "Active Projects"/"Project Health" instead of legal terms | LOW | OPEN | Dev | — | Cosmetic |
| GAP-D0-06 | Team page Role column empty for all members | LOW | OPEN | Dev | — | Cosmetic |
| GAP-D0-07 | E2E seed does not pre-apply legal-za profile — requires manual profile switch | MEDIUM | OPEN | Dev | — | Workaround: manually apply via Settings > General |
| GAP-D1-01 | Conflict Check page crashes: TypeError: Cannot read properties of undefined (reading 'map') | CRITICAL | OPEN | Dev | — | BLOCKER — page crashes on load, blocks all conflict check steps across all days |

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-04-06T09:00Z | Setup | QA cycle initialized. Branch: bugfix_cycle_2026-04-06. E2E stack confirmed running (frontend:3001, backend:8081, mailpit:8026). |
| 2026-04-06T16:00Z | QA | Day 0 executed. Applied Legal (South Africa) profile (seed had accounting). Created 3 billing rates (Alice R2500, Bob R1200, Carol R550) and 3 cost rates (Alice R1000, Bob R500, Carol R200). Verified ZAR currency, 15% tax, team members, custom fields. Found 7 gaps: no matter templates (GAP-D0-01), trust accounting stub (GAP-D0-02), no modules page (GAP-D0-03), terminology gaps (GAP-D0-04/05/06), seed profile issue (GAP-D0-07). |
| 2026-04-06T16:05Z | QA | Day 1 started. Login as Bob. Conflict Check page crashes with TypeError (GAP-D1-01 CRITICAL BLOCKER). Clients page loads OK with "New Client" button. STOPPED at Step 1.1 per blocker rule. |
