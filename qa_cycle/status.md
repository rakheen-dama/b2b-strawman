# QA Cycle Status — Deep Automation & Notification Verification (2026-03-18)

## Current State

- **QA Position**: T2/T3/T4 COMPLETE. T2 triggers verified (3/3 fire correctly). T3.2 SendEmail blocked by BUG-AUTO-01. T4 email content blocked.
- **Cycle**: 3 (continuation of automation verification with lifecycle seed data)
- **E2E Stack**: HEALTHY (rebuilt from main with PRs #745-#748, lifecycle seed 24/24 PASS)
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
| BUG-AUTO-01 | SendEmail automation action silently fails -- no email template for AUTOMATION_EMAIL | HIGH | OPEN | — | — | T3.2 | EmailNotificationChannel skips unmapped type. Executor reports success falsely. |
| BUG-UI-01 | Proposal dialog customer selector unresponsive | MEDIUM | OPEN | — | — | T2.5 | Radix Popover inside Dialog modal conflict. Clicks do not open popover. |

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
