# Day 9 Checkpoint Results — Accounting ZA 90-Day Lifecycle (Keycloak)

**Date**: 2026-05-15
**Branch**: `bugfix_cycle_2026-05-14`
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180)
**Agent**: QA Agent (Opus 4.6)
**Actor**: Carol Mokoena (Member) — carol@thornton-test.local

---

## Scenario

**Day 9 (checkpoint 9.1)**: Carol logs 2.0 hours on Kgosi Monthly Bookkeeping engagement with description "Debtors recon".

---

## Pre-conditions Verified

| Check | Result | Evidence |
|-------|--------|----------|
| Logged out previous user (Bob) | **PASS** | Gateway logout POST with CSRF token. Redirected to landing page. |
| Carol needs engagement membership | **NOTE** | Carol was not a member of the Kgosi Monthly Bookkeeping engagement. The engagement had 0 members (all 6 tasks unassigned). Carol could not access the engagement (404 from API, "Something went wrong" in UI). Bob (Admin) added Carol as a member before the time-logging step. See note below. |
| Logged in as Carol via Keycloak | **PASS** | KC login: carol@thornton-test.local / SecureP@ss3. Redirected to /org/thornton-associates/dashboard. Sidebar shows "Carol Mokoena" / "carol@thornton-test.local". |
| Kgosi Monthly Bookkeeping engagement accessible | **PASS** | After membership grant: Navigated to engagement ID a32c67d5-8e09-47b9-82ec-f0e82fa94ec4. Title: "Kgosi Holdings -- Monthly Bookkeeping (Mar 2026)", Role badge: Member, Status: Active, Ref: BK-2026-03-0001, Type: BOOKKEEPING, 6 tasks, 1 member (Carol), 3.0h already logged (Bob Day 8). |

### Prerequisite Note: Carol Added as Engagement Member

The scenario (Day 9 checkpoint 9.1) assumes Carol can log time on the Kgosi Monthly Bookkeeping engagement. However, Carol was never explicitly added as a member to this engagement in any prior day. The engagement was created by Thandi on Day 5 with all 6 tasks unassigned and 0 members.

The RBAC system correctly prevents Members from accessing engagements they are not assigned to:
- Engagements list showed only 1 engagement for Carol (Sipho Tax Return, where she was added on Day 3)
- Direct URL navigation to the bookkeeping engagement returned 404 from the API
- UI displayed "Something went wrong" error page

**Resolution**: Bob (Admin) was logged in and added Carol as a member to the bookkeeping engagement via the Members tab > Add Member dialog. This is a scenario gap (missing prerequisite step), not a product bug. The RBAC enforcement is working correctly.

**Recommendation**: The scenario should include an explicit step (e.g., at the end of Day 5 or start of Day 8) where an Admin/Owner adds Carol as a member to the Kgosi Monthly Bookkeeping engagement before she is expected to log time on it.

---

## Day 9 Checkpoints

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 9.1a | Navigate to Kgosi Monthly Bookkeeping engagement as Carol | **PASS** | Engagement page loaded at `/org/thornton-associates/projects/a32c67d5-8e09-47b9-82ec-f0e82fa94ec4`. Role badge: "Member". 6 tasks visible in Tasks tab: Bank reconciliation, Creditors reconciliation, Debtors reconciliation, VAT calculation & reconciliation, Management accounts preparation, Month-end close & review. All Open, all Unassigned. |
| 9.1b | Open Tasks tab and find Debtors reconciliation task | **PASS** | Tasks tab loaded with 6 tasks. "Debtors reconciliation" row visible with "Log Time" and "Claim" action buttons. |
| 9.1c | Open Log Time dialog on Debtors reconciliation task | **PASS** | Clicked "Log Time" on Debtors reconciliation row. Dialog opened: "Log Time -- Record time spent on this task." Fields: Duration (h/m), Date, Description, Billable checkbox. Billing rate displayed: R 450,00/hr (member default -- Carol's rate). |
| 9.1d | Fill time entry: 2h, "Debtors recon", billable | **PASS** | Filled: Duration = 2h 0m, Date = 2026-05-15, Description = "Debtors recon", Billable = checked. Calculated total displayed: 2h x R 450,00 = R 900,00. |
| 9.1e | Submit time entry | **PASS** | Clicked "Log Time" button. Dialog closed. No error toast. |
| 9.1f | Verify time entry on Time tab | **PASS** | Time tab shows: Total Time = 5h (was 3h, +2h), Billable = 5h, Non-billable = 0m, Contributors = 2 (Bob + Carol), Entries = 2. By Task: Bank reconciliation = 3h (1 entry), Debtors reconciliation = 2h (1 entry). |
| 9.1g | Verify engagement overview updated | **PASS** | Overview tab: Hours = 5.0h (was 3.0h). Team: Bob Ndlovu - 3.0h, Carol Mokoena - 2.0h. Recent Activity: "Carol Mokoena logged 2h on task 'Debtors reconciliation'" (1 minute ago). Time Breakdown chart: 5.0h total. |
| 9.1h | Verify dashboard hours updated | **PASS** | Dashboard: Hours This Month = 9.5h (was 7.5h, +2.0h from Carol's entry). Engagement Health: Kgosi Monthly Bookkeeping = 5.0h. Active Engagements = 3 (unchanged). Recent Activity: "Carol Mokoena created a time_entry" (2 minutes ago). |

---

## Summary

| Total | PASS | FAIL | PARTIAL | DEFERRED |
|-------|------|------|---------|----------|
| 8 | 8 | 0 | 0 | 0 |

**Day 9 Result: ALL PASS (8/8)**

No new product gaps identified. The only finding is a scenario gap (missing prerequisite for Carol's engagement membership) which was resolved by having Bob add Carol as a member before executing the time-logging checkpoint. The product's RBAC enforcement is correct.

## Evidence Files

- `qa_cycle/evidence/day-09-carol-time-entry-overview.png` -- Engagement overview with 5.0h logged, team breakdown showing Bob 3.0h + Carol 2.0h
- `qa_cycle/evidence/day-09-carol-time-entry-time-tab.png` -- Time tab showing By Task breakdown: Bank reconciliation 3h, Debtors reconciliation 2h

## Console Errors

- 1 transient console error on first engagement page load (non-blocking, page renders correctly after wait). Consistent with prior days' observations of SSR hydration timing.
- No persistent JavaScript/Next.js errors across page navigations.

## Cumulative State After Day 9

- **Sipho Dlamini engagement**: 2.5h total (1.0h Carol Day 4 + 1.5h Carol Day 7)
- **Kgosi Monthly Bookkeeping**: 5.0h total (3.0h Bob Day 8 + 2.0h Carol Day 9) -- R 3,450.00 unbilled (Bob 3h x R850 + Carol 2h x R450)
- **Kgosi Year-End Pack**: 2.0h total (2.0h Thandi Day 7)
- **Total hours this month**: 9.5h across all engagements

## New Gaps Filed

**None.** (Scenario prerequisite gap noted but not a product bug.)

## QA Position

**Day 9 -- COMPLETE.** 8/8 checkpoints PASS, 0 blockers, 0 new gaps. Ready to advance to Day 10 (Bob uploads bank statements to bookkeeping engagement documents).
