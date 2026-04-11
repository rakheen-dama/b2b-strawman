# Track 1 — Court Calendar Results (Partial)

**Executed**: 2026-04-04
**Actor**: QA Agent (Cycle 1)
**Stack**: Keycloak dev (3000/8080/8443/8180)

---

## T1.1 — Court Date List & Filtering

| Step | Result | Evidence |
|------|--------|----------|
| T1.1.1 | PASS | Navigate to Court Calendar → List View loads |
| T1.1.2 | PASS | All 4 seeded court dates visible |
| T1.1.3 | PASS | Columns: Date, Time, Type, Court, Matter, Client, Status, Actions |
| T1.1.4 | PASS | All 4 show "Scheduled" status badge |
| T1.1.5 | N/A | Type filter not tested yet (dropdown available with options: Hearing, Trial, Motion, Conference, Mediation, Arbitration, Mention, Other) |
| T1.1.6 | N/A | Client filter not tested yet (no client filter dropdown visible — only Status and Type filters) |
| T1.1.7 | N/A | Date range filter not tested yet (no date range filter visible) |
| T1.1.8 | N/A | Clear filters not tested |

**Note**: The filter UI has Status and Type dropdowns but NO client filter or date range filter. This may be a gap.

### GAP-P55-004: Court Calendar missing date range and client/matter filters

**Track**: T1.1 — Court Date List & Filtering
**Step**: T1.1.6, T1.1.7
**Category**: missing-feature
**Severity**: minor
**Description**: The Court Calendar list view has filter dropdowns for Status and Type, but does not have filters for Client/Matter name or date range. The test plan expects these filters.
**Evidence**:
- Available filters: "All Statuses" (Scheduled/Postponed/Heard/Cancelled), "All Types" (Hearing/Trial/Motion/Conference/Mediation/Arbitration/Mention/Other)
- Missing: Client name filter, date range filter (from/to)
- Screenshot: `t1-court-calendar-list.png`
**Suggested fix**: Add client/matter search input and date range picker to the filter bar.

### GAP-P55-005: Court date type badge shows raw enum value with underscore

**Track**: T1.1 — Court Date List & Filtering
**Step**: T1.1.3
**Category**: ui-error
**Severity**: cosmetic
**Description**: The Type column displays "Pre_trial" with an underscore instead of a human-readable format like "Pre-Trial" or "Pre-trial Conference".
**Evidence**:
- Expected: "Pre-Trial" or "Pre-trial Conference"
- Actual: "Pre_trial"
- Screenshot: `t1-court-calendar-list.png`
**Suggested fix**: Add display name mapping for court date types in the frontend.

---

## T1.2–T1.8 — Not Yet Executed

Remaining Track 1 checkpoints (Calendar View, Create New Court Date, Postpone, Cancel, Record Outcome, State Machine, Multiple Dates Per Matter) have not been executed yet. QA position set to T1.2.

---

## Screenshot Artifacts

- `t1-court-calendar-list.png` — Court Calendar list view with 4 dates, all SCHEDULED
