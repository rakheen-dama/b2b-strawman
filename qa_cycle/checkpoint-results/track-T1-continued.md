# Track 1 — Court Calendar Results (Continued: T1.2–T1.8)

**Executed**: 2026-04-04
**Actor**: QA Agent (Cycle 1, continued)
**Stack**: Keycloak dev (3000/8080/8443/8180)

---

## T1.2 — Court Date Calendar View

| Step | Result | Evidence |
|------|--------|----------|
| T1.2.1 | PASS | Calendar tab exists on Court Calendar page. Clicking "Calendar" tab switches to month grid view. |
| T1.2.2 | PASS | Calendar defaults to April 2026 (current month). Month header shows "April 2026" with left/right arrow navigation. |
| T1.2.3 | PASS | Markers visible on: April 10 (showing "1"), April 18 (showing "1"), April 25 (showing "1"). Days without events are disabled buttons. |
| T1.2.4 | PASS | Clicking right arrow navigates to May 2026. |
| T1.2.5 | PASS | May 15 has marker (showing "1") corresponding to the TRIAL court date. |
| T1.2.6 | PASS | Clicking April 10 shows detail panel: Date=2026-04-10 at 10:00:00, Type=Pre_trial, Court=Johannesburg High Court, Reference=2026/12345, Matter=Mabena v Road Accident Fund, Client=Sipho Mabena, Status=SCHEDULED, Description=Pre-trial conference. Actions: Postpone, Cancel, Record Outcome. Clicking May 15 shows Trial detail with Judge=Molemela J. |

---

## T1.3 — Create New Court Date

| Step | Result | Evidence |
|------|--------|----------|
| T1.3.1 | FAIL | Clicking "New Court Date" button crashes the page with error boundary: "Something went wrong". |
| T1.3.2–T1.3.5 | BLOCKED | Cannot fill form — dialog crash. |
| T1.3.6 | PASS (API) | Created via API: `POST /api/court-dates` with projectId=Nkosi Estate, dateType=TAXATION, scheduledDate=2026-05-20. Response correctly auto-resolved customerName="Nkosi Family Trust". Status=SCHEDULED. |

### GAP-P55-006: "New Court Date" dialog crashes with TypeError

**Track**: T1.3 — Create New Court Date
**Step**: T1.3.1
**Category**: crash
**Severity**: major (blocks all court date creation from UI)
**Description**: Clicking the "New Court Date" button on the Court Calendar page causes the error boundary to trigger with "Something went wrong". The console shows: `TypeError: Cannot read properties of undefined (reading 'map')` in a react-hook-form Controller render function (line 3233 of frontend chunk). The backend API works correctly — this is a frontend-only issue, likely a combobox/select field trying to `.map()` over an undefined options array before it loads.
**Evidence**:
- Console error: `TypeError: Cannot read properties of undefined (reading 'map')` at `Controller` component
- Error boundary catches and shows "Something went wrong"
- Backend API `POST /api/court-dates` works correctly with proper field names
- Page recovers after "Refresh page" click
**Impact**: Blocks T1.3, T1.8 (creating new court dates from UI). Postpone, Cancel, and Record Outcome dialogs from the Actions menu work correctly.
**Suggested fix**: Ensure the form field options (likely matter/project list or type dropdown) are initialized to an empty array `[]` default before the async data loads.

---

## T1.4 — Postpone Court Date

| Step | Result | Evidence |
|------|--------|----------|
| T1.4.1 | PASS | Opened PRE_TRIAL row (April 10, Mabena v RAF). |
| T1.4.2 | PASS | Clicked Actions > Postpone. Dialog appeared: "Postpone Court Date" with New Date and Reason fields. |
| T1.4.3 | PASS | Filled: New Date = 2026-04-17, Reason = "Counsel unavailable — briefing conflict". |
| T1.4.4 | PASS | Status changed to Postponed in list. |
| T1.4.5 | PASS | Date in list updated to 2026-04-17 (was 2026-04-10). |
| T1.4.6 | FAIL | Detail panel shows Date, Type, Court, Reference, Matter, Client, Status, Description — but NO "Postponement Reason" field. The reason entered is not displayed anywhere in the detail view. |

### GAP-P55-007: Postponement reason not visible in court date detail view

**Track**: T1.4 — Postpone Court Date
**Step**: T1.4.6
**Category**: missing-feature
**Severity**: minor
**Description**: After postponing a court date, the detail panel does not show the postponement reason. The data fields shown are: Date, Type, Court, Reference, Matter, Client, Status, Description. There is no "Reason" or "Postponement Reason" field. The cancellation reason IS shown (under "Outcome" label) — inconsistency.
**Suggested fix**: Add a "Reason" field to the detail view for POSTPONED court dates.

---

## T1.5 — Cancel Court Date

| Step | Result | Evidence |
|------|--------|----------|
| T1.5.1 | PASS | Opened MEDIATION row (April 18, Modise Divorce). |
| T1.5.2 | PASS | Clicked Actions > Cancel. Dialog: "Cancel Court Date" with Reason field. |
| T1.5.3 | PASS | Filled: Reason = "Parties reached settlement agreement". |
| T1.5.4 | PASS | Status changed to Cancelled in list. |
| T1.5.5 | PASS | Cancellation reason visible in detail view under "Outcome" field: "Parties reached settlement agreement". |
| T1.5.6 | PASS | Cancelled court date still visible in list (not deleted). |
| T1.5.7 | PASS | Actions cell is empty for cancelled row — no Edit, Postpone, or Outcome actions available. |

---

## T1.6 — Record Outcome

| Step | Result | Evidence |
|------|--------|----------|
| T1.6.1 | PASS | Opened HEARING row (April 25, Mining Rights). |
| T1.6.2 | PASS | Clicked Actions > Record Outcome. Dialog: "Record Outcome" with Outcome field. |
| T1.6.3 | PASS | Filled: Outcome = "Application granted subject to environmental impact assessment conditions". |
| T1.6.4 | PASS | Status changed to Heard in list. |
| T1.6.5 | PASS | Outcome text visible in detail view under "Outcome" field. |
| T1.6.6 | PASS | Actions cell is empty for Heard row — no Edit, Postpone, or Cancel actions. |

---

## T1.7 — State Machine Edge Cases

| Step | Result | Evidence |
|------|--------|----------|
| T1.7.1 | PASS | CANCELLED row has no Actions dropdown — cannot attempt Postpone. |
| T1.7.2 | PASS | CANCELLED row has no Actions dropdown — cannot attempt Record Outcome. |
| T1.7.3 | PASS | HEARD row has no Actions dropdown — cannot attempt Cancel. |
| T1.7.4 | PARTIAL | POSTPONED pre-trial shows Cancel and Record Outcome actions. "Edit" action is missing — test plan expects Edit, Cancel, Record Outcome. |
| T1.7.5 | BLOCKED | Cannot edit — no Edit action available for POSTPONED court date. |

### GAP-P55-008: No Edit action for court dates

**Track**: T1.7 — State Machine Edge Cases
**Step**: T1.7.4, T1.7.5
**Category**: missing-feature
**Severity**: minor
**Description**: Court dates in SCHEDULED or POSTPONED status have no "Edit" option in the Actions dropdown. The available actions are only Postpone/Cancel/Record Outcome (for SCHEDULED) or Cancel/Record Outcome (for POSTPONED). The test plan expects an Edit action to modify description, court, time, etc. without changing status.
**Suggested fix**: Add an "Edit" menu item to the Actions dropdown for SCHEDULED and POSTPONED court dates, opening the UpdateCourtDateRequest form.

---

## T1.8 — Multiple Court Dates Per Matter

| Step | Result | Evidence |
|------|--------|----------|
| T1.8.1 | PASS | Mabena v RAF has 2 court dates (PRE_TRIAL + TRIAL) visible in list. |
| T1.8.2 | BLOCKED | Cannot create via UI (GAP-P55-006). Created via API: TAXATION for Nkosi Estate (not additional for Mabena). |
| T1.8.3 | N/A | Not tested — would require creating a 3rd Mabena court date. |
| T1.8.4 | N/A | Not tested — matter filter missing (GAP-P55-004). |

---

## Additional Finding — Dashboard Court Dates Widget

The dashboard "Upcoming Court Dates" widget shows "Unable to load court dates." — this is a separate bug from the court calendar page itself.

### GAP-P55-009: Dashboard "Upcoming Court Dates" widget shows error

**Track**: Dashboard observation
**Category**: ui-error
**Severity**: minor
**Description**: The dashboard has an "Upcoming Court Dates" widget at the bottom that displays "Unable to load court dates." despite court dates existing and the Court Calendar page loading correctly.
**Suggested fix**: Investigate the dashboard API call for upcoming court dates — likely a different endpoint or missing error handling.
