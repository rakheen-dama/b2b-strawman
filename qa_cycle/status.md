# QA Cycle Status — Data Integrity & Financial Accuracy / Keycloak Dev Stack (2026-03-24)

## Current State

- **QA Position**: Cycle 1 complete (T1-T4 core tests). Cycle 2 needed for deferred tracks (T1.3, T1.5, T2.3-T2.6, T3.6-T3.7, T4.3-T4.9).
- **Cycle**: 1 (complete)
- **Dev Stack**: READY — all services healthy
- **Branch**: `bugfix_cycle_financial_accuracy_2026-03-24`
- **Scenario**: `qa/testplan/data-integrity-financial-accuracy.md`
- **Focus**: State machines, rate resolution, invoice math, audit completeness
- **Auth Mode**: Keycloak (not mock-auth). JWT via direct grant with organization scope.
- **Results**: `qa_cycle/checkpoint-results/financial-accuracy-cycle1.md`

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend | http://localhost:3000 | UP |
| Backend | http://localhost:8080 | UP |
| Gateway (BFF) | http://localhost:8443 | UP |
| Keycloak | http://localhost:8180 | UP |
| Mailpit | http://localhost:8025 | UP |

## Existing Data (from Phase 49 T0 cycle + this cycle's prerequisites)

- **Org**: "Thornton & Associates" (alias=thornton-associates, schema=tenant_4a171ca30392)
- **Org**: "QA Verify Corp" (alias=qa-verify-corp, schema=tenant_62aa7c96ab38)
- **Users**: padmin@docteams.local (platform-admin), thandi@thornton-test.local (owner), bob@thornton-test.local (admin), qatest@thornton-verify.local (owner of QA Verify Corp)
- **Customers**: Naledi Corp QA (ACTIVE), Kgosi Holdings QA Cycle2 (OFFBOARDED), Lifecycle Chain C4 (OFFBOARDED), Test Integrity Customer (OFFBOARDED), Invalid Transition Test Customer (PROSPECT)
- **Invoices**: INV-0001 (APPROVED, R8,050), INV-0002 (PAID, R3,680), INV-0003 (PAID, R1,035), INV-0004 (VOID, R115), plus multiple DRAFT test invoices
- **Billing Rates**: Thandi MEMBER_DEFAULT R600, Thandi PROJECT_OVERRIDE R700 (Rate Test Project), Bob MEMBER_DEFAULT R850, 3 ORG_DEFAULT rates
- **Projects**: 6 active (incl. Rate Hierarchy Test Project, T1 Test Project [ARCHIVED]), plus existing
- **Templates**: 13 templates active
- All passwords: `password`

## Cycle 1 Summary

| Track | Tested | Passed | Failed |
|-------|--------|--------|--------|
| T1 — State Machines | 35 | 34 | 1 (known GAP-DI-01) |
| T2 — Rate Hierarchy | 14 | 14 | 0 |
| T3 — Invoice Math | 17 | 17 | 0 |
| T4 — Audit Trail | 18 | 18 | 0 |
| **Total** | **84** | **83** | **1 known** |

**Previous gaps resolved**: GAP-DI-02 (comments on archived projects) FIXED, GAP-DI-03 (audit DELETE vulnerability) FIXED.

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| GAP-DI-01 | DRAFT invoices cannot be voided (only APPROVED/SENT) | Minor | OPEN | — | — | T1.2 | Design decision — not a bypass |
| GAP-DI-02 | Comments on ARCHIVED projects | Minor | FIXED | — | — | T1.4 | Archive guard now blocks comments |
| GAP-DI-03 | Audit DELETE vulnerability | Major | FIXED | — | — | T4.11 | prevent_audit_delete() trigger added |
| GAP-DI-04 | Auto-transition actorType is USER not SYSTEM | Minor | OPEN | — | — | T4.12 | Auto-transitions record triggering user |
| GAP-DI-05 | No project unarchive endpoint | Minor | OPEN | — | — | T1.4 | Projects cannot be unarchived |
| GAP-DI-06 | ipAddress not in audit API response | Minor | OPEN | — | — | T4.10 | Field may exist in DB but not in DTO |

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-24T12:00Z | Setup | Data Integrity & Financial Accuracy QA cycle initialized on branch bugfix_cycle_financial_accuracy_2026-03-24. Scenario: qa/testplan/data-integrity-financial-accuracy.md. Reusing seed data from Phase 49 T0 cycle. |
| 2026-03-24T12:05Z | Infra | Dev stack verified healthy. Docker infra (Postgres, Keycloak, LocalStack, Mailpit) all running. Local services started via svc.sh: backend (PID 66144, :8080), gateway (PID 66301, :8443), frontend (PID 66435, :3000), portal (PID 66487, :3002). Keycloak realm docteams responding. |
| 2026-03-24T20:42Z | QA | Authenticated as Thandi via Keycloak. Enabled direct access grants on gateway-bff client for API-level testing with JWT + organization scope. |
| 2026-03-24T20:45Z | QA | Prerequisites created: PROSPECT customer, 2 invoices (DRAFT, SENT), Bob billing rate R850, Thandi project override R700. |
| 2026-03-24T20:50Z | QA | T1 complete: 35 checkpoints, 34 PASS, 1 known (GAP-DI-01). All invalid transitions correctly rejected. GAP-DI-02 (comment on archived project) FIXED. |
| 2026-03-24T20:55Z | QA | T2 complete: 14 checkpoints, all PASS. Rate hierarchy (member default, project override) resolves correctly. Snapshots immutable after capture. Multi-user rates confirmed. |
| 2026-03-24T20:58Z | QA | T3 complete: 17 checkpoints, all MATH_OK. Rounding uses HALF_UP. Per-line tax calculation confirmed. Edge cases (fractional qty, tiny amounts) correct. |
| 2026-03-24T21:02Z | QA | T4 complete: 18 checkpoints, all AUDIT_OK. Customer + invoice lifecycle events present with correct details. Audit immutability verified — both UPDATE and DELETE blocked by triggers. GAP-DI-03 (DELETE vulnerability) FIXED. |
| 2026-03-24T21:05Z | QA | Cycle 1 complete. 84 checkpoints tested, 83 PASS, 1 known design decision. 2 previous gaps fixed. 3 new minor gaps documented. No blockers. |
