# Verification Cycle 2 — Phase 55 Fix Verification

**Executed**: 2026-04-04
**Actor**: QA Agent (Cycle 2)
**Stack**: Keycloak dev (3000/8080/8443/8180)
**Branch**: `bugfix_cycle_2026-04-04`

---

## Summary

| Result | Count |
|--------|-------|
| VERIFIED | 12 |
| REOPENED | 0 |

All 12 FIXED gaps from Cycle 1 have been verified. No regressions found.

---

## Frontend Fixes (HMR -- already live)

### 1. GAP-P55-009 — Dashboard court dates widget URL (PR #909)

**Result**: VERIFIED
**Evidence**: Dashboard loads with "Upcoming Court Dates" widget showing actual data: "2026-04-17 | Pre-Trial | Mabena v Road Accident Fund | Johannesburg High Court" with "View All" link. Previously showed "Unable to load court dates."

---

### 2. GAP-P55-006 — "New Court Date" dialog crash (PR #910)

**Result**: VERIFIED
**Evidence**: Clicked "New Court Date" button on Court Calendar page. "Schedule Court Date" dialog opened successfully with all fields: Matter (combobox with options), Type (combobox: Hearing/Trial/Motion/Conference/Mediation/Arbitration/Mention/Other), Date, Time, Court Name, Court Reference, Judge/Magistrate, Description, Reminder (days before), Cancel/Schedule buttons. Previously crashed with `TypeError: Cannot read properties of undefined (reading 'map')`.

---

### 3. GAP-P55-011 — Conflict Check page crash (PR #910)

**Result**: VERIFIED
**Evidence**: Navigated to `/org/moyo-dlamini-attorneys/conflict-check`. Page loaded successfully with "Run Check" tab (selected) and "History (9)" tab. Form shows: Name to Check, ID Number, Registration Number, Check Type (combobox: New Client/New Matter/Periodic Review), Customer (optional combobox), Matter (optional combobox), and "Run Conflict Check" button. Previously crashed with TypeError on load.

---

### 4. GAP-P55-012 — Tariff Schedules page crash (PR #911)

**Result**: VERIFIED
**Evidence**: Navigated to `/org/moyo-dlamini-attorneys/legal/tariffs`. Page loaded showing "3 schedules":
- LSSA 2024/2025 High Court Party-and-Party (19 items)
- Internal Advisory Rates (3 items)
- Moyo & Dlamini -- Custom Rates 2026 (19 items)

Each schedule has a "Clone" button. Previously crashed due to data shape mismatch (backend returns array, frontend expected paginated `{ content, page }`).

---

### 5. GAP-P55-005 — Type badge display (PR #913)

**Result**: VERIFIED
**Evidence**: Court Calendar list view shows properly formatted type badges:
- "Pre-Trial" (was "Pre_trial")
- "Mediation" (single word, unchanged)
- "Hearing" (single word, unchanged)
- "Trial" (single word, unchanged)
- "Taxation" (single word, unchanged)

Dashboard widget also shows "Pre-Trial" (not "Pre_trial"). Fix applied in 3 locations (list view, widget, detail panel).

---

### 6. GAP-P55-014 — Terminology "Client:" vs "Customer:" (PR #913)

**Result**: VERIFIED
**Evidence**: Matter detail page (`/projects/{id}`) header section shows `"Client: Sipho Mabena"` (link to customer). Previously showed "Customer:". Uses `<TerminologyText template="{Customer}:" />` which resolves to "Client:" for legal tenants.

**Note**: The Overview tab card (ref=e294) still shows `"Customer: Sipho Mabena"` -- this is a different component location not covered by the original gap. The primary header label fix is confirmed working.

---

### 7. GAP-P55-003 — Module gating shows descriptive message (PR #916)

**Result**: VERIFIED (code-verified)
**Evidence**: Verified via code inspection and API:
1. `court-calendar/page.tsx` checks `enabledModules.includes("court_calendar")` and renders "Module Not Available" / "The Court Calendar module is not enabled for your organization." when false.
2. `conflict-check/page.tsx` checks `enabledModules.includes("conflict_check")` with same pattern.
3. `legal/tariffs/page.tsx` checks `enabledModules.includes("lssa_tariff")` with same pattern.
4. Thornton tenant API confirms `enabledModules: []` (empty) and `verticalProfile: "accounting-za"`.
5. Previously all three pages called `notFound()` which showed a generic "Something went wrong" error.

Browser-based verification was not possible (could not switch Keycloak sessions within the same Playwright context without losing the gateway BFF session state). Code path is deterministic -- if `enabledModules` does not include the required module, the descriptive UI renders.

---

### 8. GAP-P55-004 — Calendar filters: date range and client search (PR #918)

**Result**: VERIFIED
**Evidence**: Court Calendar filter bar now includes:
- Status dropdown ("All Statuses": Scheduled/Postponed/Heard/Cancelled)
- Type dropdown ("All Types": Hearing/Trial/Motion/Conference/Mediation/Arbitration/Mention/Other)
- **From date** text input (NEW)
- **To date** text input (NEW)
- **Search client/matter** text input with placeholder "Search client/matter..." (NEW)

Functional test: Typed "Mabena" in the search field. Table filtered from 5 rows down to 2 (both Mabena v RAF dates: Pre-Trial and Trial). Previously only Status and Type filters existed.

---

### 9. GAP-P55-008 — Edit court date action (PR #919)

**Result**: VERIFIED
**Evidence**:
- **SCHEDULED** court date (Trial, May 15): Actions dropdown shows "Edit", "Postpone", "Cancel", "Record Outcome". Edit is first item.
- **POSTPONED** court date (Pre-Trial, Apr 17): Actions dropdown shows "Edit", "Cancel", "Record Outcome". Edit is first item.
- **CANCELLED** court date (Mediation): No Actions column (empty cell). Correct -- terminal state.
- **HEARD** court date (Hearing): No Actions column (empty cell). Correct -- terminal state.
- Detail panel for POSTPONED date also shows "Edit" button alongside "Cancel" and "Record Outcome".

Previously no "Edit" action existed for any status.

---

## Backend Fixes (restarted after merge)

### 10. GAP-P55-002 — Prescription expired status (PR #912)

**Result**: VERIFIED
**Evidence**:
- **API**: `GET /api/prescription-trackers` returns Mining GENERAL_3Y (prescriptionDate=2026-01-10) with `status: "EXPIRED"`. Was `"RUNNING"` before the fix.
- **API**: Mabena DELICT_3Y (prescriptionDate=2027-06-15) correctly shows `status: "RUNNING"` (future date).
- **UI**: Prescriptions tab shows Mining tracker first with "Expired" status badge and "--" in Days Left column. Mabena shows "Running" with 437 days left.

Dynamic status computation (`computeEffectiveStatus()`) correctly evaluates prescription date against today at query time.

---

### 11. GAP-P55-007 — Postponement reason visible in detail (PR #914)

**Result**: VERIFIED
**Evidence**: Postponed the Trial court date via API with reason "Counsel unavailable due to scheduling conflict". API response confirms `outcome: "Postponed: Counsel unavailable due to scheduling conflict"`. UI detail panel for the newly postponed Trial shows:
```
Outcome: "Postponed: Counsel unavailable due to scheduling conflict"
```
The "Outcome" label is the same used for cancellations and recorded outcomes, providing a consistent display pattern.

**Note**: The Pre-Trial court date that was postponed during Cycle 1 (before the fix) has `outcome: null` because the reason was not stored at postponement time. This is expected -- the fix only affects new postponements.

---

### 12. GAP-P55-010 — Adverse party search (PR #917)

**Result**: VERIFIED
**Evidence**:
- `GET /api/adverse-parties?search=BHP` returns 1 result: "BHP Minerals SA (Pty) Ltd". Was 0 results before.
- `GET /api/adverse-parties?search=Road` returns 1 result: "Road Accident Fund". Was 0 results before.

ILIKE substring match (`findByNameContaining`) now works as primary search path. Short tokens that previously failed with `pg_trgm similarity()` threshold now match correctly.

---

## Data Changes During Verification

- **Trial court date** (ae76b4a2) was postponed from 2026-05-15 to 2026-05-22 to test GAP-P55-007 fix. Status changed from SCHEDULED to POSTPONED. Outcome field populated with postponement reason.

---

## Conclusion

All 12 FIXED gaps have been verified. The Phase 55 Legal Foundations bugfix cycle is complete. No regressions were observed during verification. The remaining 2 gaps (GAP-P55-001 WONT_FIX, GAP-P55-013 WONT_FIX) were correctly triaged and do not require further action.
