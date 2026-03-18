# QA Cycle Status — Data Integrity & Financial Accuracy (2026-03-18)

## Current State

- **QA Position**: T1-T4 COMPLETE (cycle 1) — all 4 tracks executed
- **Cycle**: 1
- **E2E Stack**: ALL HEALTHY (frontend:3001, backend:8081, mock-idp:8090, mailpit:8026, postgres:5433, localstack:4567) — NEEDS_REBUILD (GAP-DI-02 + GAP-DI-03 backend fixes merged)
- **Branch**: `bugfix_cycle_data_integrity_2026-03-18`
- **Scenario**: `qa/testplan/data-integrity-financial-accuracy.md`
- **Focus**: Data integrity verification, financial calculations accuracy

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| GAP-DI-01 | DRAFT invoices cannot be voided (only APPROVED/SENT) | Minor | BY_DESIGN | — | — | T1.2 | Intentional. INV-040 in QA plan confirms DRAFT->VOID rejected. DRAFT invoices can be deleted instead. `voidDraft()` exists for internal billing-run cancellation only. |
| GAP-DI-02 | Comments can be created on ARCHIVED projects | Minor | FIXED | Dev | #755 | T1.4 | Fixed. `projectLifecycleGuard.requireNotReadOnly()` added to `createComment()` and `updateComment()`. 2 integration tests added. |
| GAP-DI-03 | Audit events can be DELETED via direct SQL | Major | FIXED | Dev | #756 | T4.11 | Fixed. V74 migration adds `prevent_audit_delete()` function + `BEFORE DELETE` trigger. Integration test added. |

## Results Summary

| Track | ID | Test | Result | Evidence |
|-------|-----|------|--------|----------|
| T1.1 | T1.1.1 | PROSPECT -> ONBOARDING | PASS | HTTP 200 |
| T1.1 | T1.1.2 | ONBOARDING -> ACTIVE (checklist) | PASS | Auto-transition after 4 items |
| T1.1 | T1.1.3 | ACTIVE -> DORMANT | PASS | HTTP 200 |
| T1.1 | T1.1.4 | DORMANT -> ACTIVE | PASS | HTTP 200 |
| T1.1 | T1.1.5 | ACTIVE -> OFFBOARDING -> OFFBOARDED | PASS | HTTP 200 |
| T1.1 | T1.1.6 | PROSPECT -> ACTIVE (bypass) | PASS (REJECTED) | HTTP 400 |
| T1.1 | T1.1.7 | ONBOARDING -> ACTIVE (incomplete) | PASS (REJECTED) | HTTP 400 |
| T1.1 | T1.1.8 | OFFBOARDED -> ONBOARDING | PASS (REJECTED) | HTTP 400 |
| T1.1 | T1.1.9 | PROSPECT -> DORMANT | PASS (REJECTED) | HTTP 400 |
| T1.1 | T1.1.10 | ACTIVE -> PROSPECT (backward) | PASS (REJECTED) | HTTP 400 |
| T1.1 | T1.1.11 | PROSPECT: create project | PASS (REJECTED) | HTTP 400 lifecycle guard |
| T1.1 | T1.1.12 | PROSPECT: create invoice | PASS (REJECTED) | HTTP 400 lifecycle guard |
| T1.1 | T1.1.13 | PROSPECT: create time entry | PASS (TRANSITIVE) | Blocked via project guard |
| T1.1 | T1.1.14 | ACTIVE: create project | PASS | HTTP 201 |
| T1.1 | T1.1.15 | OFFBOARDED: create project | PASS (REJECTED) | HTTP 400 lifecycle guard |
| T1.2 | T1.2.1 | DRAFT -> APPROVED | PASS | HTTP 200 |
| T1.2 | T1.2.2 | APPROVED -> SENT | PASS | HTTP 200 |
| T1.2 | T1.2.3 | SENT -> PAID | PASS | HTTP 200 |
| T1.2 | T1.2.4 | DRAFT -> VOID | PASS (BY_DESIGN) | HTTP 409 — intentionally rejected. INV-040 confirms. |
| T1.2 | T1.2.5 | DRAFT -> SENT (skip APPROVED) | PASS (REJECTED) | HTTP 409 |
| T1.2 | T1.2.6 | DRAFT -> PAID (skip all) | PASS (REJECTED) | HTTP 409 |
| T1.2 | T1.2.7 | APPROVED -> PAID (skip SENT) | PASS (REJECTED) | HTTP 409 |
| T1.2 | T1.2.8 | PAID -> edit via PUT | PASS (REJECTED) | HTTP 409 |
| T1.2 | T1.2.9 | PAID -> VOID | PASS (REJECTED) | HTTP 409 |
| T1.2 | T1.2.10 | VOID -> APPROVED | PASS (REJECTED) | HTTP 409 |
| T1.2 | T1.2.11 | DRAFT: edit line items | PASS | HTTP 201 |
| T1.2 | T1.2.12 | APPROVED: edit line items | PASS (REJECTED) | HTTP 409 |
| T1.2 | T1.2.13 | SENT: edit line items | PASS (REJECTED) | HTTP 409 |
| T1.3 | T1.3.1 | DRAFT -> SENT | PASS | HTTP 200 |
| T1.3 | T1.3.4 | DRAFT -> ACCEPTED | PASS (REJECTED) | No accept endpoint on org controller |
| T1.3 | T1.3.5 | SENT -> re-SENT | PASS (REJECTED) | HTTP 409 |
| T1.3 | T1.3.2 | SENT -> ACCEPTED | NOT TESTED | Requires portal JWT |
| T1.3 | T1.3.3 | SENT -> DECLINED | NOT TESTED | Requires portal JWT |
| T1.3 | T1.3.6 | DECLINED -> ACCEPTED | NOT TESTED | Requires portal JWT |
| T1.3 | T1.3.7 | EXPIRED -> ACCEPTED | NOT TESTED | Requires portal JWT |
| T1.4 | T1.4.1 | OPEN -> IN_PROGRESS -> DONE | PASS | Via PUT /api/tasks/{id} |
| T1.4 | T1.4.2 | DONE -> IN_PROGRESS | PASS (REJECTED) | HTTP 400 |
| T1.4 | T1.4.3 | CANCELLED -> IN_PROGRESS | PASS (REJECTED) | HTTP 400 |
| T1.4 | T1.4.4 | DONE -> OPEN (reopen) | PASS | HTTP 200 |
| T1.4 | T1.4.5 | ARCHIVED: create task | PASS (REJECTED) | HTTP 400 |
| T1.4 | T1.4.6 | ARCHIVED: log time | PASS (TRANSITIVE) | Blocked via task guard |
| T1.4 | T1.4.7 | ARCHIVED: create comment | FAIL (GAP-DI-02) | HTTP 201 — comment created |
| T1.4 | T1.4.8 | ARCHIVED -> ACTIVE (unarchive) | NOT TESTED | No unarchive endpoint |
| T2.1 | T2.1.1 | Alice rate snapshot = R1,500 | PASS | billingRateSnapshot=1500.0 |
| T2.1 | T2.1.2 | Currency = ZAR | PASS | billingRateCurrency=ZAR |
| T2.1 | T2.1.3 | Cost rate snapshot = R600 | PASS | costRateSnapshot=600.0 |
| T2.5 | T2.5.3 | Change Alice rate to R1,800 | PASS | Rate updated |
| T2.5 | T2.5.4 | Existing entry still R1,500 | PASS | Snapshot immutable |
| T2.5 | T2.5.5 | New entry uses R1,800 | PASS | New rate applied |
| T3.1 | T3.1.1-5 | Basic line item (3xR450) | MATH_OK | subtotal=1350, tax=202.50, total=1552.50 |
| T3.2 | T3.2.1-4 | Multi-line (R750+R900+R850) | MATH_OK | subtotal=2500, tax=375, total=2875 |
| T3.3 | T3.3.1-2 | Rounding R1,333.33 | MATH_OK | tax=200.00, total=1533.33 |
| T3.3 | T3.3.3-4 | Rounding R99.99 | MATH_OK | tax=15.00, total=114.99 |
| T3.3 | T3.3.5-6 | Rounding 3xR333.33 | MATH_OK | tax=150.00, total=1149.99 |
| T3.4 | T3.4.3 | Per-line vs invoice tax | DOCUMENTED | Per-line tax (R50.01, not R50.00). hasPerLineTax=true |
| T4.1 | T4.1.1 | customer.created audit | AUDIT_OK | Event with name, email, actor |
| T4.1 | T4.1.2 | customer.lifecycle.transitioned | AUDIT_OK | oldStatus, newStatus, notes |
| T4.2 | T4.2.1 | invoice.created audit | AUDIT_OK | currency, customer_name, line_count |
| T4.2 | T4.2.2 | invoice.approved audit | AUDIT_OK | invoice_number, total, issue_date |
| T4.2 | T4.2.3 | invoice.sent audit | AUDIT_OK | invoice_number |
| T4.2 | T4.2.4 | invoice.paid audit | AUDIT_OK | total, paid_at, payment_reference |
| T4.2 | T4.2.5 | invoice.voided audit | AUDIT_OK | reverted counts included |
| T4.11 | T4.11.1 | Audit UPDATE blocked | AUDIT_OK | DB trigger prevent_audit_update() works |
| T4.11 | T4.11.2 | Audit DELETE blocked | AUDIT_FAIL (GAP-DI-03) | DELETE 1 — no trigger on DELETE |

## Scorecard

| Track | Tested | Pass | Fail | Not Tested |
|-------|--------|------|------|------------|
| T1 — State Machines | 41 | 37 | 1 | 5 |
| T2 — Rate Hierarchy | 8 | 8 | 0 | 0 |
| T3 — Invoice Arithmetic | 11 | 11 | 0 | 0 |
| T4 — Audit Trail | 11 | 10 | 1 | 0 |
| **Total** | **71** | **66** | **2** | **5** |

**Pass rate (excluding NOT TESTED): 66/66 (after BY_DESIGN reclassification) = 97.0% (2 real failures remain)**

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-18T10:00Z | Setup | Data integrity QA cycle initialized on branch bugfix_cycle_data_integrity_2026-03-18. Scenario: qa/testplan/data-integrity-financial-accuracy.md |
| 2026-03-18T10:20Z | Infra | Backend restarted (exited 137 — OOM/SIGKILL, no crash). All 6 services healthy. Seed data verified: 1 tenant, 5 customers, 7 projects, 4 members. |
| 2026-03-18T10:30Z | QA | Cycle 1 execution started. Authenticated as Alice (owner). Created test customers for lifecycle testing. |
| 2026-03-18T10:45Z | QA | T1.1 complete (15/15 PASS). Customer lifecycle guards fully enforced. |
| 2026-03-18T10:55Z | QA | T1.2 complete (12/13 PASS). GAP-DI-01: DRAFT->VOID not allowed. |
| 2026-03-18T11:00Z | QA | T1.3 partial (3/7 PASS, 4 not tested — portal auth required). |
| 2026-03-18T11:05Z | QA | T1.4 complete (6/8 PASS). GAP-DI-02: Comments on archived projects allowed. |
| 2026-03-18T11:10Z | QA | T2 complete (8/8 PASS). Rate snapshots immutable. Org default rates resolve correctly. |
| 2026-03-18T11:15Z | QA | T3 complete (11/11 MATH_OK). All arithmetic correct. Per-line tax calculation confirmed. |
| 2026-03-18T11:20Z | QA | T4 partial (10/11 PASS). GAP-DI-03: Audit DELETE not blocked. |
| 2026-03-18T11:25Z | QA | Cycle 1 complete. Results written to checkpoint-results/data-integrity-cycle1.md |
| 2026-03-18T11:40Z | Product | Triage started for GAP-DI-01, GAP-DI-02, GAP-DI-03. |
| 2026-03-18T11:45Z | Product | GAP-DI-01 -> BY_DESIGN. INV-040 in `docs/qa/plans/02-invoicing-billing.md` explicitly expects DRAFT->VOID rejected. `Invoice.voidDraft()` exists for internal billing-run cancellation only. T1.2.4 reclassified as PASS (BY_DESIGN). |
| 2026-03-18T11:50Z | Product | GAP-DI-02 -> SPEC_READY. `CommentService.createComment()` missing `projectLifecycleGuard.requireNotReadOnly()` call. All other child-entity services (task, document, time entry) have this guard. Fix spec written to `fix-specs/GAP-DI-02.md`. Effort: S. |
| 2026-03-18T11:55Z | Product | GAP-DI-03 -> SPEC_READY. V12 migration only creates UPDATE trigger. New V74 migration needed for DELETE trigger. No cascading impact — standalone fix. Fix spec written to `fix-specs/GAP-DI-03.md`. Effort: S. |
| 2026-03-18T11:55Z | Product | Cascading analysis: GAP-DI-02 and GAP-DI-03 are independent bugs with no downstream failures. Neither escalated to blocker. |
| 2026-03-18T12:20Z | Dev | GAP-DI-02 FIXED via PR #755 (squash-merged). Injected `ProjectLifecycleGuard` into `CommentService`. Added `requireNotReadOnly()` in `createComment()` and `updateComment()`. `deleteComment()` and `listComments()` intentionally left unguarded. 2 integration tests added (create + update on archived project return 400). All 9 comment tests pass. Backend change — NEEDS_REBUILD. |
| 2026-03-18T12:50Z | Dev | GAP-DI-03 FIXED via PR #756 (squash-merged). Added V74 Flyway migration with `prevent_audit_delete()` function + `BEFORE DELETE` trigger on `audit_events`, mirroring V12's UPDATE trigger pattern. Added `deleteOnAuditEventsRaisesException` integration test. All 6 audit isolation tests pass (3712 total, 0 errors). Backend migration change — NEEDS_REBUILD. |
