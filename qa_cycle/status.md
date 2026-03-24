# QA Cycle Status — Data Integrity & Financial Accuracy / Keycloak Dev Stack (2026-03-24)

## Current State

- **QA Position**: Day 0 — Prerequisites check, then T1 start
- **Cycle**: 0 (setup)
- **Dev Stack**: READY — all services healthy
- **Branch**: `bugfix_cycle_financial_accuracy_2026-03-24`
- **Scenario**: `qa/testplan/data-integrity-financial-accuracy.md`
- **Focus**: State machines, rate resolution, invoice math, audit completeness
- **Auth Mode**: Keycloak (not mock-auth). Login via Keycloak redirect flow.
- **Results**: (none yet)

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend | http://localhost:3000 | UP |
| Backend | http://localhost:8080 | UP |
| Gateway (BFF) | http://localhost:8443 | UP |
| Keycloak | http://localhost:8180 | UP |
| Mailpit | http://localhost:8025 | UP |

## Existing Data (from Phase 49 T0 cycle)

- **Org**: "Thornton & Associates" (alias=thornton-associates, schema=tenant_4a171ca30392)
- **Org**: "QA Verify Corp" (alias=qa-verify-corp, schema=tenant_62aa7c96ab38)
- **Users**: padmin@docteams.local (platform-admin), thandi@thornton-test.local (owner), bob@thornton-test.local (member), qatest@thornton-verify.local (owner of QA Verify Corp)
- **Customers**: Naledi Corp QA (ACTIVE), Kgosi Holdings QA Cycle2 (OFFBOARDED), Lifecycle Chain C4 (OFFBOARDED)
- **Invoices**: INV-0001 (Naledi Corp QA, APPROVED, R8,050)
- **Templates**: 13 templates active (7 PLATFORM accounting-za, 1 custom clone, 5 generic PLATFORM)
- All passwords: `password`

**Additional seed needed for this plan:**
- At least 1 customer in PROSPECT status (T1 invalid transition testing)
- At least 1 invoice in DRAFT status (T1 + T3 invoice lifecycle testing)
- At least 1 invoice in SENT status (T3 payment testing)
- Multiple billing rates at different levels (org, project) (T2 hierarchy testing)

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| (none yet) | | | | | | | |

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-24T12:00Z | Setup | Data Integrity & Financial Accuracy QA cycle initialized on branch bugfix_cycle_financial_accuracy_2026-03-24. Scenario: qa/testplan/data-integrity-financial-accuracy.md. Reusing seed data from Phase 49 T0 cycle. |
| 2026-03-24T12:05Z | Infra | Dev stack verified healthy. Docker infra (Postgres, Keycloak, LocalStack, Mailpit) all running. Local services started via svc.sh: backend (PID 66144, :8080), gateway (PID 66301, :8443), frontend (PID 66435, :3000), portal (PID 66487, :3002). Keycloak realm docteams responding. |
