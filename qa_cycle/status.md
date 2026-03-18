# QA Cycle Status — Deep Automation & Notification Verification (2026-03-18)

## Current State

- **QA Position**: T2/T3/T4 COMPLETE. T2 triggers verified (3/3 fire correctly). T3.2 SendEmail blocked by BUG-AUTO-01. T4 email content blocked.
- **Cycle**: 3 (continuation of automation verification with lifecycle seed data)
- **E2E Stack**: READY (rebuilt 2026-03-18T08:30Z with PRs #750 + #752)
- **Branch**: `qa_deep_automation_2026-03-18`
- **Scenario**: `qa/testplan/automation-notification-verification.md`
- **Focus**: T2 remaining triggers (invoice, proposal, budget), T4 email content, T3 email actions

## Seed Data Available

| Entity | Count | Details |
|--------|-------|---------|
| Customers | 5 | Kgosi Construction, Naledi Hair Studio, Vukani Tech Solutions, Moroka Family Trust, Acme Corp (all ACTIVE) |
| Projects | 7 | Monthly Bookkeeping (Kgosi/Naledi/Vukani), Annual Admin (Moroka), BEE Review (Vukani), Tax Return (Kgosi), Website Redesign |
| Invoices | 4 | INV-0001 (now PAID), INV-0002 (PAID), 2 DRAFT |
| Proposals | 2 | PROP-0001 Monthly Bookkeeping — Kgosi (SENT), PROP-0002 QA Test — Tax Advisory (SENT) |
| Rate cards | 3 | Alice R1500/hr, Bob R850/hr, Carol R450/hr |
| Time entries | Multiple | Across days 7, 14, 45, 75 + QA test entries |
| Budgets | 2 | Kgosi bookkeeping (85% consumed), BEE review |

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| BUG-AUTO-01 | SendEmail automation action silently fails -- no email template for AUTOMATION_EMAIL | HIGH | FIXED | Dev | #750 | T3.2 | Created `notification-automation.html` template, added `AUTOMATION_EMAIL` mapping in `resolveTemplateName()`, changed `deliver()` to return boolean, `SendEmailActionExecutor` now checks return value and reports `ActionFailure` on delivery failure. Full `mvn clean verify` green. NEEDS_REBUILD before QA verification. |
| BUG-UI-01 | Proposal dialog customer selector unresponsive | MEDIUM | FIXED | Dev | #752 | T2.5 | Added `modal={false}` to Popover in CreateProposalDialog, CreateRetainerDialog, and FieldGroupDialog (2 popovers). Frontend build green. NEEDS_REBUILD before verification. |

## Results Summary

| Track | ID | Test | Result | Evidence |
|-------|-----|------|--------|----------|
| T2 | T2.2 | INVOICE_STATUS_CHANGED | PASS | Execution log + screenshot |
| T2 | T2.5 | PROPOSAL_SENT | PASS | Execution log + screenshot |
| T2 | T2.6 | BUDGET_THRESHOLD_REACHED | PARTIAL | Trigger fires, action fails (no project owner in seed) |
| T3 | T3.2 | SendEmail action | FAIL | BUG-AUTO-01: no template for AUTOMATION_EMAIL |
| T4 | T4.1-T4.8 | Email content verification | BLOCKED | Blocked by T3.2 |

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-18T01:30Z | Setup | Deep QA cycle initialized. E2E stack rebuilt from main (with automation fixes). Lifecycle seed 24/24 PASS. 5 customers, 7 projects, 4 invoices, 1 proposal provisioned. Mailpit cleared. Focus: T2 remaining triggers + T4 email content + T3 email actions. |
| 2026-03-18T07:10Z | QA Agent | T2.2 PASS: Created rule for INVOICE_STATUS_CHANGED->PAID. Recorded payment on INV-0001 (SENT->PAID). InvoicePaidEvent fired, execution completed in 8ms. |
| 2026-03-18T07:10Z | QA Agent | T2.5 PASS: Created PROP-0002 via API (Vukani, R5000 retainer). Sent proposal. ProposalSentEvent fired, Proposal Follow-up rule executed in 4ms. UI bug found: customer selector Popover unresponsive inside Dialog. |
| 2026-03-18T07:10Z | QA Agent | T2.6 PARTIAL: Logged 1h time on Monthly Bookkeeping Kgosi (75%->85%). BudgetThresholdEvent fired. Action failed: no PROJECT_OWNER recipient. Trigger verified correct. |
| 2026-03-18T07:10Z | QA Agent | T3.2 FAIL: Created SendEmail rule for TASK_STATUS_CHANGED->COMPLETED. Completed task. Execution reports "Completed" with emailSentTo=alice@e2e-test.local, but Mailpit shows 0 emails. Root cause: EmailNotificationChannel has no template for AUTOMATION_EMAIL type, silently skips. BUG-AUTO-01 logged. |
| 2026-03-18T07:10Z | QA Agent | T4.1-T4.8 BLOCKED: No emails reach Mailpit. Blocked by BUG-AUTO-01. |
| 2026-03-18T07:30Z | Product Agent | BUG-AUTO-01 triaged: Two-part root cause confirmed. (1) `EmailNotificationChannel.resolveTemplateName()` has no case for `AUTOMATION_EMAIL` — returns null, delivery silently skipped. (2) `SendEmailActionExecutor` calls `emailChannel.deliver()` (void) and always returns `ActionSuccess` regardless of delivery outcome. Fix spec written to `fix-specs/BUG-AUTO-01-deep.md`: create `notification-automation.html` template, add switch case, change `deliver()` to return boolean, check result in executor. Status: OPEN -> SPEC_READY. |
| 2026-03-18T07:30Z | Product Agent | BUG-UI-01 triaged: Root cause confirmed as Radix Popover-inside-modal-Dialog focus trap conflict. `PopoverContent` renders via Portal (outside Dialog DOM tree), so Dialog's modal overlay blocks pointer events. `type="button"` already present — not the issue. Fix: add `modal={false}` to Popover. Same pattern found in `create-retainer-dialog.tsx` and `FieldGroupDialog.tsx`. Fix spec written to `fix-specs/BUG-UI-01.md`. Status: OPEN -> SPEC_READY. |
| 2026-03-18T08:00Z | Dev | BUG-AUTO-01 FIXED via PR #750 (squash-merged to qa_deep_automation_2026-03-18). Three-part fix: (1) Created `notification-automation.html` Thymeleaf template for AUTOMATION_EMAIL type with `th:utext` body for variable-resolved content. (2) Added `"AUTOMATION_EMAIL" -> "notification-automation"` case in `EmailNotificationChannel.resolveTemplateName()`. (3) Changed `NotificationChannel.deliver()` from `void` to `boolean`; `EmailNotificationChannel` returns true/false based on delivery outcome; `SendEmailActionExecutor` now checks return value and reports `ActionFailure` when delivery fails. All tests pass. Full `mvn clean verify` green. Backend code change — NEEDS_REBUILD before QA verification. |
| 2026-03-18T08:10Z | Dev | BUG-UI-01 FIXED via PR #752 (squash-merged to qa_deep_automation_2026-03-18). Added `modal={false}` to 4 Popover components nested inside modal Dialogs: (1) CreateProposalDialog customer selector, (2) CreateRetainerDialog customer selector, (3) FieldGroupDialog dependencies selector, (4) FieldGroupDialog fields selector. Prevents Radix Dialog focus trap from blocking Popover portal pointer events. Frontend build green. All 1563 tests pass (1 pre-existing portal-login failure unrelated). Frontend code change — NEEDS_REBUILD before QA verification. |
| 2026-03-18T08:30Z | Infra Agent | E2E stack rebuilt with PRs #750 + #752. All services healthy (frontend 3001, backend 8081, mock-idp 8090, mailpit 8026). Lifecycle seed 24/24 PASS. Mailpit cleared. Stack READY for QA verification of BUG-AUTO-01 and BUG-UI-01 fixes. |
