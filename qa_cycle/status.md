# QA Cycle Status — Data Integrity & Financial Accuracy / Keycloak Dev Stack (2026-03-24)

## Current State

- **QA Position**: ALL_TRACKS_COMPLETE. Cycles 1+2 finished. 138 checkpoints, 136 passed, 2 known (GAP-DI-01 WONT_FIX, GAP-DI-07 OPEN). One new major gap: expired proposals can be accepted (GAP-DI-07).
- **Cycle**: 2 (complete)
- **Dev Stack**: READY — all services healthy
- **Branch**: `bugfix_cycle_financial_accuracy_2026-03-24`
- **Scenario**: `qa/testplan/data-integrity-financial-accuracy.md`
- **Focus**: State machines, rate resolution, invoice math, audit completeness
- **Auth Mode**: Keycloak (not mock-auth). JWT via direct grant with organization scope. Portal JWT via dev harness magic link.
- **Results**: `qa_cycle/checkpoint-results/financial-accuracy-cycle1.md` (Cycle 1), `qa_cycle/checkpoint-results/financial-accuracy-cycle2.md` (Cycle 2)

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
- **Invoices**: INV-0001 (APPROVED, R8,050), INV-0002 (PAID, R3,680), INV-0003 (PAID, R1,035), INV-0004 (VOID, R115), INV-0005 (SENT), INV-0006 (VOID, R6,238.75), INV-0007 (VOID, R3,795), INV-0008 (APPROVED, R3,795), plus multiple DRAFT test invoices
- **Billing Rates**: Thandi MEMBER_DEFAULT R600, Thandi PROJECT_OVERRIDE R700 (Rate Test Project), Thandi PROJECT_OVERRIDE R750, Thandi CUSTOMER_OVERRIDE R475 (Naledi Corp), Bob MEMBER_DEFAULT R850, 3 ORG_DEFAULT rates
- **Projects**: 6 active (incl. Rate Hierarchy Test Project, T1 Test Project [ARCHIVED]), plus existing
- **Proposals**: PROP-0002 (ACCEPTED), 3 other proposals (DECLINED, ACCEPTED/expired, DRAFT)
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

## Cycle 2 Summary

| Track | Tested | Passed | Failed | Skipped |
|-------|--------|--------|--------|---------|
| T1.3 — Proposal Guards | 7 | 6 | 1 (GAP-DI-07) | 0 |
| T1.4.8 — Project Unarchive | 1 | 1 | 0 | 0 |
| T1.5 — Void Side Effects | 7 | 7 | 0 | 1 |
| T2.3 — Customer Override | 4 | 4 | 0 | 0 |
| T2.4 — No Rate Found | 0 | 0 | 0 | 4 |
| T2.6 — Rate on Date Change | 3 | 3 | 0 | 0 |
| T3.6 — Retainer Math | 2 | 2 | 0 | 0 |
| T3.7 — Void/Re-Invoice | 8 | 8 | 0 | 0 |
| T4.3-T4.9 — Audit Events | 22 | 22 | 0 | 5 |
| GAP Verifications | 4 | 4 | 0 | 0 |
| **Total** | **54** | **53** | **1** | **10** |

**New gap**: GAP-DI-07 (expired proposals can be accepted). **Verified**: GAP-DI-05 (false positive confirmed), GAP-DI-06 (ipAddress/userAgent now in API).

## Combined Totals (Cycle 1 + 2)

| Track | Total Tested | Total Passed | Total Failed |
|-------|-------------|--------------|--------------|
| T1 — State Machines | 50 | 48 | 2 (DI-01 known, DI-07 new) |
| T2 — Rate Hierarchy | 21 | 21 | 0 |
| T3 — Invoice Math | 27 | 27 | 0 |
| T4 — Audit Trail | 40 | 40 | 0 |
| **Total** | **138** | **136** | **2** |

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| GAP-DI-01 | DRAFT invoices cannot be voided (only APPROVED/SENT) | Minor | WONT_FIX | — | — | T1.2 | BY_DESIGN: DRAFT invoices can be deleted (no financial impact). Voiding is for APPROVED/SENT invoices to preserve audit trail. Confirmed by architecture doc and QA plan INV-040. Existing fix-spec already documents this. |
| GAP-DI-02 | Comments on ARCHIVED projects | Minor | FIXED | — | — | T1.4 | Archive guard now blocks comments |
| GAP-DI-03 | Audit DELETE vulnerability | Major | FIXED | — | — | T4.11 | prevent_audit_delete() trigger added |
| GAP-DI-04 | Auto-transition actorType is USER not SYSTEM | Minor | WONT_FIX | — | — | T4.12 | BY_DESIGN: Auto-transitions (checklist completion -> ACTIVE) are triggered by the last user action within their HTTP request. AuditEventBuilder auto-detects actorType=USER because MEMBER_ID is bound. Recording the triggering user provides better traceability than a generic SYSTEM actor. The actorType=SYSTEM path is correctly used for non-HTTP contexts (scheduled jobs like DormancyScheduledJob). |
| GAP-DI-05 | No project unarchive endpoint | Minor | WONT_FIX | — | — | T1.4 | FALSE_POSITIVE: Project unarchive IS supported via `PATCH /api/projects/{id}/reopen`. ProjectStatus enum allows ARCHIVED->ACTIVE transition. Project.reopen() handles both COMPLETED and ARCHIVED states. QA agent missed the existing endpoint. |
| GAP-DI-06 | ipAddress not in audit API response | Minor | VERIFIED | Dev Agent | [#832](https://github.com/rakheen-dama/b2b-strawman/pull/832) | T4.10 | Added ipAddress and userAgent to AuditEventResponse DTO and from() factory method. Verified in Cycle 2: all 5 sampled events have ipAddress=0:0:0:0:0:0:0:1 and userAgent=curl/8.9.1. |
| GAP-DI-07 | Expired proposals can be accepted via portal | Major | SPEC_READY | — | — | T1.3 | `PortalProposalService.acceptProposal()` checks status==SENT but does NOT check expiresAt. A proposal with expiresAt=2026-01-01 was accepted on 2026-03-24. Fix spec: `qa_cycle/fix-specs/GAP-DI-07.md`. Add `isExpired()` to Proposal entity, add expiry guard in `acceptProposal()` and `declineProposal()`, defense-in-depth in `markAccepted()`. |

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
| 2026-03-25T00:00Z | QA | Cycle 2 started. Authenticated via Keycloak direct grant (Thandi + Bob). Portal JWT obtained via dev harness magic link exchange for portal proposal tests. |
| 2026-03-25T00:10Z | QA | T1.3 complete: 7 checkpoints, 6 PASS, 1 FAIL. Proposal lifecycle guards enforce DRAFT->SENT->ACCEPTED/DECLINED correctly. Invalid transitions (DRAFT->ACCEPTED, ACCEPTED->SENT, DECLINED->ACCEPTED) all rejected. **NEW BUG GAP-DI-07**: Expired proposals (expiresAt in past) can still be accepted via portal — PortalProposalService does not check expiresAt. |
| 2026-03-25T00:15Z | QA | GAP-DI-05 re-verified: PATCH /api/projects/{id}/reopen successfully transitions ARCHIVED->ACTIVE. Confirmed FALSE_POSITIVE. |
| 2026-03-25T00:25Z | QA | T1.5 complete: 7 PASS. Void invoice side effects work correctly — time entries revert to UNBILLED (invoiceId cleared), voided invoice preserved with line items, reverted entries can be re-invoiced. |
| 2026-03-25T00:30Z | QA | T2.3 complete: 4 PASS. Customer override R475 correctly wins over member default R600 (on projects without project override). Project override R700 still wins over customer override R475. Full 3-level hierarchy confirmed: project > customer > member. |
| 2026-03-25T00:35Z | QA | T2.6 complete: Rate snapshots RE-SNAPSHOT on date change (not immutable across date edits). Changing date to pre-effective period yields null snapshot. Changing back restores correct rate. |
| 2026-03-25T00:40Z | QA | T3.6 complete: Retainer invoice math correct. R5,500 + 4h overage @ R450 = R7,300 subtotal, R1,095 tax, R8,395 total. |
| 2026-03-25T00:45Z | QA | T3.7 complete: Void/re-invoice cycle correct. Invoice A (R3,795) voided, Invoice B (R3,795) created with identical amounts and different number (INV-0007 vs INV-0008). |
| 2026-03-25T00:55Z | QA | T4.3-T4.9 complete: 22 audit checkpoints tested. Time entry (created/updated/deleted), proposal (created/sent/accepted), document (created/uploaded/generated), billing rate (created/updated), project (created/archived/reopened), task (created), comment (created/updated/deleted) — all AUDIT_OK with ipAddress populated. Role/member audit events not available (Keycloak-managed). |
| 2026-03-25T01:00Z | QA | GAP-DI-06 VERIFIED: All 5 sampled audit events have ipAddress and userAgent fields populated in API response. |
| 2026-03-25T01:05Z | QA | Cycle 2 complete. 54 checkpoints tested, 53 PASS, 1 FAIL (GAP-DI-07). Combined Cycle 1+2: 138 tested, 136 passed, 2 known (DI-01 WONT_FIX, DI-07 OPEN). QA Position set to ALL_TRACKS_COMPLETE. |
| 2026-03-25T01:20Z | Product | GAP-DI-07 SPEC_READY. Root cause confirmed: `PortalProposalService.acceptProposal()` and `declineProposal()` check status==SENT but not expiresAt. Race window between expiry and hourly `ProposalExpiryProcessor` batch run. Fix: add `isExpired()` to Proposal entity, expiry guards in PortalProposalService (accept+decline), defense-in-depth in `markAccepted()`. Also needs `declineProposal()` guard for consistency. Fix spec: `qa_cycle/fix-specs/GAP-DI-07.md`. |
