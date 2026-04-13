# Day 5 — Matter Detail Wow Moment

**Date**: 2026-04-12
**Actor**: Bob Ndlovu (Admin)
**Matter**: Sipho Dlamini v. Standard Bank (civil)
**Page**: `/org/mathebula-partners/projects/9f9e6ea1-1bd6-40a6-98f6-c762b0af44bb`

---

## Checkpoint Results

### CP-5.1: Navigate to Sipho's litigation matter detail page
**Result**: PASS
- Already on the page from Day 4 session. URL confirmed: `/org/mathebula-partners/projects/9f9e6ea1-...`
- Matter heading: "Sipho Dlamini v. Standard Bank (civil)" with Member/Active badges
- Breadcrumb: Mathebula & Partners > Matters > Matter

### CP-5.2: Verify all promoted fields visible inline at top (matter_type, case_number, court_name)
**Result**: PARTIAL
- `case_number`: PASS — "Case Number" field with value "JHB/CIV/2026/001" rendered inline under "SA Legal — Matter Details" field group
- `court_name`: PASS — "Court" field with value "Gauteng High Court, Johannesburg" rendered inline
- `matter_type`: FAIL — Not a dedicated inline field. Appears only in the description text as "Matter type: LITIGATION". Already tracked as **GAP-D3-02** (MED).
- Additional fields visible: Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value (all from SA Legal pack)

### CP-5.3: Verify tabs all load without errors
**Result**: PASS
All five scenario tabs load without errors. Zero console errors throughout navigation.

| Tab | Status | Content |
|-----|--------|---------|
| Action Items (Tasks) | PASS | 9 tasks visible, table with Priority/Title/Status/Assignee/Due Date columns, Bob on "Initial consultation", Carol on "Letter of demand" |
| Time | PASS | Total 1h 30m, Billable 1h 30m, Non-billable 0m, 1 contributor, 1 entry. By-task and by-member breakdowns render. |
| Documents | PASS | 1 document: engagement-letter-litigation-sipho-dlamini-v-standard-bank-civil-2026-04-12.pdf (4.6 KB, Apr 12 2026) |
| Client Comments | PASS | Empty state: "No customer comments yet" with Post Reply input. Tab renders cleanly. |
| Activity | PASS | 5 events: time entry logged, 2 task assignments, 2 member additions. Filter buttons (All/Tasks/Documents/Comments/Members/Time) present. |

Additional tabs verified present in the tab strip (18 total): Overview, Documents, Members, Clients, Action Items, Time, Disbursements, Fee Estimate, Financials, Staffing, Rates, Generated Docs, Requests, Client Comments, Court Dates, Adverse Parties, Trust, Activity.

### CP-5.4: Screenshot — Matter detail page with promoted fields + terminology + tabs visible
**Result**: PASS
- Screenshot: `qa_cycle/screenshots/cycle-2/day05-cp5.4-matter-detail-wow-moment.png`
- Full-page screenshot captures: matter heading, SA Legal custom fields, Overview tab with health/hours/tasks/activity/time breakdown/budget/team, sidebar with legal terminology, breadcrumb, all 18 tabs visible

---

## Pre-existing Gaps Observed (not new)

| GAP_ID | Observation on Day 5 |
|--------|---------------------|
| GAP-D3-02 | `matter_type` not a promoted inline field, only in description text |
| GAP-D3-03 | "Back to Projects", "Complete Project", "Project Info", "added a member to the project" — generic terminology leaks |
| GAP-D4-02 | Revenue "--" on Overview because rate snapshotting broken |

## New Gaps

None. All issues observed are pre-existing from Days 3-4.

---

## Summary

- **Checkpoints**: 4 total — 3 PASS, 1 PARTIAL
- **New gaps**: 0
- **Console errors**: 0
- **Day 5 COMPLETE**
