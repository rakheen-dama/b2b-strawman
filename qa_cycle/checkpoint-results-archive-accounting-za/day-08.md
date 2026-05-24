# Day 8 Checkpoint Results — Accounting ZA 90-Day Lifecycle (Keycloak)

**Date**: 2026-05-15
**Branch**: `bugfix_cycle_2026-05-14`
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180)
**Agent**: QA Agent (Opus 4.6)
**Actor**: Bob Ndlovu (Admin) — bob@thornton-test.local

---

## Scenario

**Day 8 (checkpoint 8.1)**: Bob logs 3.0 hours on Kgosi Monthly Bookkeeping engagement with description "Mar bank recon + creditors".

---

## Pre-conditions Verified

| Check | Result | Evidence |
|-------|--------|----------|
| Logged out previous user (Thandi) | **PASS** | Clicked User menu > Sign out. Redirected to landing page. |
| Logged in as Bob via Keycloak | **PASS** | KC login: bob@thornton-test.local / [REDACTED]. Redirected to /org/thornton-associates/dashboard. Sidebar shows "Bob Ndlovu" / "bob@thornton-test.local". |
| Kgosi Monthly Bookkeeping engagement accessible | **PASS** | Navigated to engagement ID a32c67d5-8e09-47b9-82ec-f0e82fa94ec4. Title: "Kgosi Holdings -- Monthly Bookkeeping (Mar 2026)", Status: Active, Ref: BK-2026-03-0001, Type: BOOKKEEPING, 6 tasks all Open/Unassigned, 0h logged prior. |

---

## Day 8 Checkpoints

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 8.1a | Navigate to Kgosi Monthly Bookkeeping engagement | **PASS** | Engagement page loaded at `/org/thornton-associates/projects/a32c67d5-8e09-47b9-82ec-f0e82fa94ec4`. 6 tasks visible: Bank reconciliation, Creditors reconciliation, Debtors reconciliation, VAT calculation & reconciliation, Management accounts preparation, Month-end close & review. All Open, all Unassigned. |
| 8.1b | Open Log Time dialog on Bank reconciliation task | **PASS** | Clicked "Log Time" button on Bank reconciliation row in Tasks tab. Dialog opened: "Log Time -- Record time spent on this task." Fields: Duration (h/m), Date, Description, Billable checkbox. |
| 8.1c | Fill time entry: 3h, "Mar bank recon + creditors", billable | **PASS** | Filled: Duration = 3h 0m, Date = 2026-05-15, Description = "Mar bank recon + creditors", Billable = checked. Billing rate displayed: R 850.00/hr (Bob's admin rate). Calculated total: 3h x R 850.00 = R 2,550.00. |
| 8.1d | Submit time entry | **PASS** | Clicked "Log Time" button. Dialog closed. No error toast. |
| 8.1e | Verify time entry on Time tab | **PASS** | Time tab shows: Total Time = 3h, Billable = 3h, Non-billable = 0m, Contributors = 1, Entries = 1. By Task: Bank reconciliation = 3h (1 entry). By Member: Bob Ndlovu = 3h billable, 0m non-billable. |
| 8.1f | Verify engagement overview updated | **PASS** | Overview tab: Hours = 3.0h (was 0h). Revenue margin = 58.8%. Recent Activity: "Bob Ndlovu logged 3h on task 'Bank reconciliation'" (1 minute ago). Time Breakdown chart: Bob Ndlovu = 3.0h. Team: Bob Ndlovu - 3.0h. Unbilled Time: R 2,550.00 across 3.0 hours. |
| 8.1g | Verify dashboard hours updated | **PASS** | Dashboard: Hours This Month = 7.5h (was 4.5h, +3.0h from Bob's entry). Bookkeeping engagement row: 3.0h (was 0h). Active Engagements = 3 (unchanged). |

---

## Summary

| Total | PASS | FAIL | PARTIAL | DEFERRED |
|-------|------|------|---------|----------|
| 7 | 7 | 0 | 0 | 0 |

**Day 8 Result: ALL PASS (7/7)**

No new gaps identified. Time entry workflow works correctly for all checkpoints:
- Log Time dialog opens from task row action button
- Duration, description, date, and billable fields all functional
- Billing rate correctly reflects Bob's admin rate (R 850.00/hr)
- Revenue calculation correct (3h x R 850.00 = R 2,550.00)
- Time tab, Overview tab, and Dashboard all reflect the new entry
- Unbilled time widget shows accurate R 2,550.00 total

## Evidence Files

- `qa_cycle/evidence/day-08-bob-time-entry-overview.png` — Engagement overview with 3.0h logged and activity feed
- `qa_cycle/evidence/day-08-bob-time-entry-time-tab.png` — Time tab showing By Task and By Member breakdowns

## Console Errors

- 1 transient console error on engagement page load (non-blocking, page renders correctly). Consistent with prior days' observations of SSR hydration timing.

## Cumulative State After Day 8

- **Sipho Dlamini engagement**: 2.5h total (1.0h Carol Day 4 + 1.5h Carol Day 7)
- **Kgosi Monthly Bookkeeping**: 3.0h total (3.0h Bob Day 8) — R 2,550.00 unbilled
- **Kgosi Year-End Pack**: 2.0h total (2.0h Thandi Day 7)
- **Total hours this month**: 7.5h across all engagements

## New Gaps Filed

**None.**

## QA Position

**Day 8 — COMPLETE.** 7/7 checkpoints PASS, 0 blockers, 0 new gaps. Ready to advance to Day 9 (Carol logs 2.0h on bookkeeping "Debtors recon").
