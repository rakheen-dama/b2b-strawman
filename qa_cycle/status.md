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
| GAP-DI-01 | DRAFT invoices cannot be voided (only APPROVED/SENT) | Minor | WONT_FIX | — | — | T1.2 | BY_DESIGN: DRAFT invoices can be deleted (no financial impact). Voiding is for APPROVED/SENT invoices to preserve audit trail. Confirmed by architecture doc and QA plan INV-040. Existing fix-spec already documents this. |
| GAP-DI-02 | Comments on ARCHIVED projects | Minor | FIXED | — | — | T1.4 | Archive guard now blocks comments |
| GAP-DI-03 | Audit DELETE vulnerability | Major | FIXED | — | — | T4.11 | prevent_audit_delete() trigger added |
| GAP-DI-04 | Auto-transition actorType is USER not SYSTEM | Minor | WONT_FIX | — | — | T4.12 | BY_DESIGN: Auto-transitions (checklist completion -> ACTIVE) are triggered by the last user action within their HTTP request. AuditEventBuilder auto-detects actorType=USER because MEMBER_ID is bound. Recording the triggering user provides better traceability than a generic SYSTEM actor. The actorType=SYSTEM path is correctly used for non-HTTP contexts (scheduled jobs like DormancyScheduledJob). |
| GAP-DI-05 | No project unarchive endpoint | Minor | WONT_FIX | — | — | T1.4 | FALSE_POSITIVE: Project unarchive IS supported via `PATCH /api/projects/{id}/reopen`. ProjectStatus enum allows ARCHIVED->ACTIVE transition. Project.reopen() handles both COMPLETED and ARCHIVED states. QA agent missed the existing endpoint. |
| GAP-DI-06 | ipAddress not in audit API response | Minor | FIXED | Dev Agent | [#832](https://github.com/rakheen-dama/b2b-strawman/pull/832) | T4.10 | Added ipAddress and userAgent to AuditEventResponse DTO and from() factory method. Test updated to verify presence. |

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
| 2026-03-24T21:30Z | Product | Triaged 4 OPEN gaps from cycle 1. GAP-DI-01: WONT_FIX (by design — DRAFT deletion exists, voiding is for approved/sent). GAP-DI-04: WONT_FIX (by design — user-triggered auto-transitions correctly record the triggering user, SYSTEM reserved for scheduled jobs). GAP-DI-05: WONT_FIX (false positive — PATCH /reopen endpoint already handles ARCHIVED->ACTIVE). GAP-DI-06: SPEC_READY — AuditEventResponse DTO missing ipAddress/userAgent fields (fix spec written). |
| 2026-03-24T21:45Z | Dev Agent | GAP-DI-06 FIXED. Added ipAddress and userAgent fields to AuditEventResponse record and from() factory method in AuditEventController. Updated test to verify fields are present (ipAddress="127.0.0.1", userAgent key present even if null). All 18 audit controller tests pass. PR #832 merged (squash) into bugfix_cycle_doc_verify_2026-03-24. |
