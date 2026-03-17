# QA Cycle Status — Automation & Notification Verification (2026-03-18)

## Current State

- **QA Position**: T2.1 (start of Track 2 — Trigger Verification)
- **Cycle**: 1
- **E2E Stack**: HEALTHY
- **Branch**: `bugfix_cycle_automation_2026-03-18`
- **Scenario**: `qa/testplan/automation-notification-verification.md`
- **Execution Order**: T1 → T7 → T2 → T3 → T4 → T5 → T6

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| BUG-AUTO-01 | Actions not persisted when creating/editing automation rules via UI | Critical | OPEN | — | — | T1 | Frontend create/update API call does not include actions in payload. Seeded rules unaffected (actions pre-exist in DB). Blocks custom rule testing. |
| BUG-AUTO-02 | All 11 seeded automation rules are DISABLED by default | Medium | OPEN | — | — | T1 | Vertical pack rules should be ENABLED by default. Dashboard shows "Active Rules: 0". Must manually enable each rule before trigger testing. |
| BUG-AUTO-03 | AutomationEventListener missing trigger type mapping for TaskCompletedEvent | Critical | OPEN | — | — | T7 | Backend logs: "No trigger type mapping for event class TaskCompletedEvent, skipping". Task Completion Chain seeded rule never fires. Also affects DocumentGeneratedEvent. INVOICE_STATUS_CHANGED and PROPOSAL_SENT mappings confirmed working. |

## Status Values

- **OPEN** → **SPEC_READY** → **IN_PROGRESS** → **FIXED** → **VERIFIED** → done
- **REOPENED** (fix didn't work) → back to SPEC_READY
- **WONT_FIX** (documented, not blocking e2e flow)
- **BUG** (logged, prioritized, not blocking unless cascading)

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-18T00:00Z | Setup | Initialized QA cycle for automation-notification-verification test plan. E2E stack healthy (frontend 3001, backend 8081, Mailpit 8026). Branch created. Execution order: T1 → T7 → T2 → T3 → T4 → T5 → T6. |
| 2026-03-18T22:40Z | QA Agent | Completed Track 1 (Automation Rule CRUD). 11 seeded rules found (expected ~4). All disabled (BUG-AUTO-02). Created custom rule "Payment received — notify Alice" — metadata saved, but actions NOT persisted (BUG-AUTO-01). Edit name, toggle enable/disable, execution log page all work. Seeded rules DO have persisted actions (verified "Task Completion Chain" has Create Task action). Proceeding to T7. |
| 2026-03-18T22:50Z | QA Agent | Completed Track 7 (Vertical Automation Templates). Manually enabled all 11 seeded rules. Tested Task Completion Chain — completed task "Gather financial data" but automation did NOT fire. Backend log: "No trigger type mapping for event class TaskCompletedEvent, skipping" (BUG-AUTO-03). INVOICE_STATUS_CHANGED and PROPOSAL_SENT mappings confirmed present in logs. Proceeding to T2 to test triggers with confirmed mappings. |
| 2026-03-18T22:55Z | QA Agent | STOPPING after T1 + T7. Three critical blockers found. BUG-AUTO-01 blocks custom rule actions. BUG-AUTO-02 means seeded rules need manual enabling. BUG-AUTO-03 proves event-to-trigger mappings are incomplete (TASK_STATUS_CHANGED broken; INVOICE_STATUS_CHANGED and PROPOSAL_SENT confirmed working). Fix BUG-AUTO-01 and BUG-AUTO-03 before resuming at T2.1. BUG-AUTO-02 is a seed quality issue (workaround: manual toggle). |
