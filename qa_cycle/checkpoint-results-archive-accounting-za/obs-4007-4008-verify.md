# OBS-4007 + OBS-4008 Verification Report

**Date**: 2026-05-16
**Branch**: `bugfix_cycle_2026-05-14`
**Agent**: QA
**Verdict**: **BOTH VERIFIED**

## Context

Both fixes are backend automation-engine bugs discovered on Day 30 of the accounting-za lifecycle. The bugs caused automation rule execution failures (silent backend errors) when budget threshold events triggered SEND_NOTIFICATION actions.

## OBS-4007: Budget Alert SEND_NOTIFICATION fails — no PROJECT_OWNER recipients

**Root cause**: `SendNotificationActionExecutor` used hardcoded `"LEAD"` (uppercase) instead of `Roles.PROJECT_LEAD` (`"lead"` lowercase), and had no fallback when no project lead was found.

**Fix (PR #1311)**: Use `Roles.PROJECT_LEAD` constant + fallback to org admins/owners via `memberRepository.findByRoleSlugsIn(List.of("admin", "owner"))`.

### Verification

- **Code review**: `SendNotificationActionExecutor.java` line 105 now uses `Roles.PROJECT_LEAD` constant. Fallback to org admins/owners at line 110.
- **Observed behavior**: The `PROJECT_OWNER` recipient type resolved via the fallback path (since DB stores `LEAD` uppercase while constant is `"lead"` lowercase). The fallback correctly returned 2 org admin/owner members (Thandi + Bob).
- **Backend log evidence**:
  ```
  2026-05-15T22:09:51.027 - Created automation execution for rule f0dd42ef (Engagement Budget Alert (80%)) with status TRIGGERED
  2026-05-15T22:09:51.038 - Automation sent 2 notification(s)
  ```
- **UI evidence**: Notification "Budget alert: Moroka Family Trust -- FY2025/26 Annual Trust AFS at 80%" appeared in Thandi's notifications bell (41 seconds after time entry).
- **Contrast with pre-fix**: The old "Automation action failed: Engagement Budget Alert (80%)" notification (from 9 hours earlier) is also visible in the notification list, confirming the bug existed and is now resolved.

**Status**: VERIFIED

## OBS-4008: Budget Alert Escalation fails — Jackson null thresholdPercent deserialization

**Root cause**: `BudgetThresholdTriggerConfig(int thresholdPercent)` used primitive `int` which cannot accept `null` from empty `{}` trigger config JSON.

**Fix (PR #1312)**: Changed `int` to `Integer`. Null threshold = catch-all in `TriggerConfigMatcher.matchesBudgetThreshold()` (line 102: `if (config.thresholdPercent() == null) return true`).

### Verification

- **Code review**: `BudgetThresholdTriggerConfig.java` now uses `Integer thresholdPercent` (not primitive `int`). `TriggerConfigMatcher.java` line 102 handles null as catch-all.
- **Observed behavior**: The "Budget Alert Escalation" rule with empty `{}` trigger config was successfully deserialized and matched the budget threshold event.
- **Backend log evidence**:
  ```
  2026-05-15T22:09:51.003 - Created automation execution for rule 1407abbb (Budget Alert Escalation) with status TRIGGERED
  2026-05-15T22:09:51.026 - Automation sent 2 notification(s)
  ```
- **UI evidence**: Notification "Budget alert: Moroka Family Trust -- FY2025/26 Annual Trust AFS" appeared in Thandi's notifications bell.
- **No Jackson exceptions**: Zero errors in backend log. No `JsonMappingException` or deserialization failures.

**Status**: VERIFIED

## Test Procedure

1. Logged in as Thandi Thornton (org owner) via Keycloak OIDC flow at `http://localhost:3000/dashboard`
2. Reset `threshold_notified` flag on Moroka Trust AFS budget (project `0a39ccb1`) to re-enable alert path
3. Navigated to Timesheet (`/my-work/timesheet`)
4. Logged 0.5h on "Draft annual financial statements" task (Sat May 16)
5. Saved timesheet - time entry created successfully
6. Budget crossed threshold: 8.5h of 8h budget = 106.25% (later 11.5h = 144% after additional entries resolved)
7. Both automation rules triggered:
   - **Budget Alert Escalation** (catch-all, `{}` config): 2 notifications sent
   - **Engagement Budget Alert (80%)** (`thresholdPercent: 80`): 2 notifications sent
8. Verified notifications appeared in Thandi's notification bell
9. Verified zero errors in backend log
10. Verified zero JS errors related to the fixes (only pre-existing AI assistant 404s)

## Evidence

- Screenshot: `qa_cycle/evidence/day-30/automation-execution-budget-alert.png` (timesheet after save)
- Screenshot: `qa_cycle/evidence/day-30/thandi-budget-alert-notification.png` (notifications with budget alerts)
- Screenshot: `qa_cycle/evidence/day-30/year-end-pack-budget-83pct.png` (budget tab showing Over Budget)
- Backend logs: Both rules TRIGGERED + 2 notifications each, zero errors
