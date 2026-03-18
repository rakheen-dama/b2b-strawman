# QA Cycle Status — Automation & Notification Verification (2026-03-18)

## Current State

- **QA Position**: T2.1 (start of Track 2 — Trigger Verification)
- **Cycle**: 1
- **E2E Stack**: HEALTHY — NEEDS_REBUILD (backend + frontend code changed by BUG-AUTO-01 and BUG-AUTO-03 fixes)
- **Branch**: `bugfix_cycle_automation_2026-03-18`
- **Scenario**: `qa/testplan/automation-notification-verification.md`
- **Execution Order**: T1 → T7 → T2 → T3 → T4 → T5 → T6

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| BUG-AUTO-01 | Actions not persisted when creating/editing automation rules via UI | Critical | FIXED | Dev | #746 | T1 | **Root cause**: Backend `CreateRuleRequest`/`UpdateRuleRequest` DTOs lack `actions` field — Jackson silently drops the data. Fixed: added `actions` field to both DTOs, `createRule()` persists actions atomically, `updateRule()` syncs actions via delete+recreate. Frontend `config` property renamed to `actionConfig` to match backend. NEEDS_REBUILD before QA verification. |
| BUG-AUTO-02 | All 11 seeded automation rules are DISABLED by default | Medium | FIXED | Dev | #747 | T1 | **Root cause**: `AutomationTemplateSeeder.applyPack()` explicitly calls `rule.toggle()` after creation. Fixed: removed toggle call so seeded rules default to ENABLED. Updated 4 test files to account for enabled seeded rules. NEEDS_REBUILD before QA verification. |
| BUG-AUTO-03 | AutomationEventListener missing trigger type mapping for TaskCompletedEvent | Critical | FIXED | Dev | #745 | T7 | Added `TaskCompletedEvent` mapping to `TriggerTypeMapping`, instanceof dispatch in `AutomationContext`, and status derivation in `TriggerConfigMatcher`. All automation tests + full verify green. Backend code change — NEEDS_REBUILD before QA verification. |

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
| 2026-03-18T23:10Z | Product Agent | Triaged all 3 OPEN items. Root causes confirmed via codebase grep. **BUG-AUTO-01** (Critical, cascading blocker): Backend DTOs missing `actions` field — fix is DTO + service change, both create and update paths. **BUG-AUTO-02** (Medium, non-blocking): Seeder explicitly toggles rules to disabled — remove 2 lines. **BUG-AUTO-03** (Critical, cascading blocker): `TaskCompletedEvent` not mapped in `TriggerTypeMapping`, plus `AutomationContext` and `TriggerConfigMatcher` need `TaskCompletedEvent` handling to avoid ClassCastException and status matching failures. All 3 items SPEC_READY. Fix specs written to `qa_cycle/fix-specs/BUG-AUTO-{01,02,03}.md`. Priority order: BUG-AUTO-03 first (unblocks T7.2 + T2.3), then BUG-AUTO-01 (unblocks custom rule testing), then BUG-AUTO-02 (QoL). All estimated S (< 30 min each). |
| 2026-03-18T23:20Z | Dev | BUG-AUTO-03 FIXED via PR #745 (squash-merged to bugfix_cycle_automation_2026-03-18). Three-part fix: (1) `TriggerTypeMapping`: added `TaskCompletedEvent.class -> TASK_STATUS_CHANGED` mapping. (2) `AutomationContext`: added `instanceof TaskCompletedEvent` dispatch + `buildTaskCompleted()` method (status="COMPLETED", previousStatus=null). (3) `TriggerConfigMatcher`: added `TaskCompletedEvent` cases in `deriveNewStatus()` ("COMPLETED") and `deriveOldStatus()` (null). All automation tests pass. Full `mvn clean verify` green. Backend code change — NEEDS_REBUILD before QA verification. |
| 2026-03-18T23:40Z | Dev | BUG-AUTO-01 FIXED via PR #746 (squash-merged to bugfix_cycle_automation_2026-03-18). Four-part fix: (1) Added `List<CreateActionRequest> actions` field to `CreateRuleRequest` and `UpdateRuleRequest` DTOs. (2) `AutomationRuleService.createRule()` now persists actions atomically with the rule. (3) `AutomationRuleService.updateRule()` syncs actions via delete+recreate when provided, falls back to existing actions when null. (4) Frontend `ActionRequest.config` renamed to `actionConfig` in `automations.ts`, `create-rule-client.tsx`, and `rule-detail-client.tsx`. All automation tests pass. Full `mvn clean verify` green (exit 0). Frontend build green. Backend + frontend code change — NEEDS_REBUILD before QA verification. |
| 2026-03-19T00:00Z | Dev | BUG-AUTO-02 FIXED via PR #747 (squash-merged to bugfix_cycle_automation_2026-03-18). Removed `rule.toggle()` call in `AutomationTemplateSeeder` so seeded rules default to ENABLED. Updated `AutomationTemplateControllerTest` assertions (seeded rules now expected enabled). Added `disableSeededRules()` setup in `AutomationEventListenerTest`, `AutomationSchedulerTest`, and `ProjectLifecycleIntegrationTest` to prevent enabled seeded rules from interfering with test-specific rule logic. Full `mvn clean verify` green (0 failures). Backend seed code change — NEEDS_REBUILD before QA verification. |
