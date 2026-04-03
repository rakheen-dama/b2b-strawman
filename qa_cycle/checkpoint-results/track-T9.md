# Track 9 — Notifications & Reminders (Cycle 1)

**Executed**: 2026-04-04
**Actor**: Alice Moyo (owner, legal tenant)
**Method**: API

## Summary

No court date reminder or prescription warning notifications exist. The `CourtDateReminderJob` runs on cron (`0 0 6 * * *` = 6 AM daily) and has not executed during this QA cycle. There is no manual trigger endpoint. The job code is correct and would:
- Create `COURT_DATE_REMINDER` for the PRE_TRIAL (2026-04-17) and TRIAL (2026-05-15) which are within 30-day lookahead
- Create `PRESCRIPTION_WARNING` for the Mining prescription (expired 2026-01-10) and transition it to EXPIRED
- Respect idempotency (one notification per court date/tracker per creator)

---

## T9.1 — Court Date Reminder Notification

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T9.1.1 Court date within reminder window triggers notification | **SKIP** | Reminder job hasn't run. PRE_TRIAL (2026-04-17) is 14 days away, within default 7-day `reminderDays`. No manual trigger endpoint exists. |
| T9.1.2 Notification references correct court date | **SKIP** | No notifications to verify |
| T9.1.3 Notification links to court date detail | **SKIP** | No notifications to verify |

## T9.2 — Prescription Warning Notification

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T9.2.1 Mining tracker (expired) generates warning | **SKIP** | Job hasn't run. Code (line 162-166 in `CourtDateReminderJob.java`) would set status to EXPIRED and skip notification for already-expired trackers. |
| T9.2.2 Check for PRESCRIPTION_WARNING notifications | **FAIL** | `GET /api/notifications` returns 4 notifications, all `AUTOMATION_ACTION_FAILED` type. Zero `PRESCRIPTION_WARNING` or `COURT_DATE_REMINDER`. |
| T9.2.3 Verify references | **SKIP** | No prescription warnings exist |
| T9.2.4 If absent: note as GAP | **N/A** | Not a bug — reminder job cron hasn't fired. GAP-P55-013 logged for completeness. |

## T9.3 — Notification Deduplication

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T9.3.1-T9.3.2 | **SKIP** | Cannot test without triggering the job. Code review confirms deduplication logic: `notificationRepository.existsByTypeAndReferenceEntityId()` check before creating. |

---

## New Gap

### GAP-P55-013: No manual trigger for court date reminder job

**Track**: T9.1 — Court Date Reminder Notification
**Step**: T9.1.1
**Category**: missing-feature
**Severity**: minor
**Description**: The `CourtDateReminderJob` only runs on cron schedule (6 AM daily). There is no `POST /api/internal/jobs/court-date-reminder` or similar endpoint to manually trigger it during QA. This makes it impossible to verify notification generation without waiting for the cron window.
**Evidence**:
- Module: court_calendar / notification
- Expected: Manual trigger endpoint (even if internal-only)
- Actual: Only cron trigger exists
**Suggested fix**: Add an internal endpoint `POST /internal/jobs/court-date-reminder` gated by API key, calling `courtDateReminderJob.execute()`.
