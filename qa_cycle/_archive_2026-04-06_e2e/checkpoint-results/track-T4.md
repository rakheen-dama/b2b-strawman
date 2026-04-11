# Track 4 — Conflict Check (Cycle 1)

**Executed**: 2026-04-04
**Actor**: Alice Moyo (owner, legal tenant)
**Method**: API (backend) — UI page crashes (GAP-P55-011)

## Summary

The **Conflict Check page crashes on load** with `TypeError: Cannot read properties of undefined (reading 'map')` in a react-hook-form Controller component. This is the same class of bug as GAP-P55-006 (court date dialog crash). The entire page is non-functional.

All backend API conflict check operations work correctly. Testing was completed via direct API calls.

---

## T4.1 — Exact ID Number Match

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T4.1.1 Navigate to Conflict Check | **FAIL** | Page crashes with "Something went wrong" (GAP-P55-011) |
| T4.1.2 Click "New Conflict Check" | **FAIL** | Page crash — form never renders |
| T4.1.3 Fill: Thandi Modise / 9205085800185 / NEW_MATTER | **PASS (API)** | `POST /api/conflict-checks` with `checkedName`, `checkedIdNumber`, `checkType` |
| T4.1.4 Result = CONFLICT_FOUND | **PASS (API)** | `"result": "CONFLICT_FOUND"` |
| T4.1.5 Conflict details: matched Thandi Modise, ID_NUMBER_EXACT, Modise Divorce, OPPOSING_PARTY | **PASS (API)** | `matchType: "ID_NUMBER_EXACT"`, `similarityScore: 1.0`, `adversePartyName: "Thandi Modise"`, `projectName: "Modise Divorce Proceedings"`, `relationship: "OPPOSING_PARTY"` |
| T4.1.6 Red/danger styling | **FAIL** | Cannot verify — UI crashed |

**Check ID**: `ff42c327-ef97-49aa-b03d-5933c938e490`

## T4.2 — Exact Registration Number Match

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T4.2.1 Check: BHP SA Mining / 2015/987654/07 / NEW_CLIENT | **PASS (API)** | 201 Created |
| T4.2.2 Result = CONFLICT_FOUND | **PASS (API)** | `"result": "CONFLICT_FOUND"` |
| T4.2.3 Matched "BHP Minerals SA (Pty) Ltd" via registration number | **PASS (API)** | `matchType: "REGISTRATION_NUMBER_EXACT"`, `similarityScore: 1.0` |
| T4.2.4 Linked matter: Mining Rights Application | **PASS (API)** | `projectName: "Mining Rights Application — Kagiso Mining"` |

**Check ID**: `9a3f296b-c882-475f-9632-c9df99675285`

## T4.3 — Fuzzy Name Match

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T4.3.1 Check: "Road Accident Fund SA" / NEW_MATTER | **PASS (API)** | 201 Created |
| T4.3.2 Result = CONFLICT_FOUND (fuzzy match) | **PASS (API)** | `"result": "CONFLICT_FOUND"` |
| T4.3.3 Details reference "Road Accident Fund" | **PASS (API)** | `adversePartyName: "Road Accident Fund"` |
| T4.3.4 Match type = name similarity | **PASS (API)** | `matchType: "NAME_SIMILARITY"`, `similarityScore: 0.84` |

**Check ID**: `67439364-eb8c-4a34-8a75-3a88b2c8e245`

## T4.4 — Alias Match

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T4.4.1 Check: "BHP Billiton" / NEW_MATTER | **PASS (API)** | 201 Created |
| T4.4.2 Result finds match via alias | **PASS (API)** | `"result": "POTENTIAL_CONFLICT"`, `matchType: "ALIAS_MATCH"`, `similarityScore: 0.65` |
| T4.4.3 Details reference "BHP Minerals SA (Pty) Ltd" | **PASS (API)** | `adversePartyName: "BHP Minerals SA (Pty) Ltd"` |

**Check ID**: `2730677c-6f34-42b3-8efe-098b24eede9e`

## T4.5 — No Conflict

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T4.5.1 Check: "Shoprite Holdings Ltd" / 1990/004118/06 / NEW_CLIENT | **PASS (API)** | 201 Created |
| T4.5.2 Result = NO_CONFLICT | **PASS (API)** | `"result": "NO_CONFLICT"` |
| T4.5.3 Green/success styling | **FAIL** | Cannot verify — UI crashed |
| T4.5.4 No conflict details section | **PASS (API)** | `"conflictsFound": []` |

**Check ID**: `8f5c214a-d397-485b-9070-2ab3d00e7823`

## T4.6 — Customer Table Cross-Check

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T4.6.1 Check: "Sipho Mabena" / 8501015800089 / PERIODIC_REVIEW | **PASS (API)** | 201 Created |
| T4.6.2 System searches adverse party + customer table | **PASS (API)** | Match found from customer table |
| T4.6.3 "Sipho Mabena" match from customer table | **PASS (API)** | `relationship: "EXISTING_CLIENT"`, `customerName: "Sipho Mabena"`, `matchType: "NAME_SIMILARITY"`, `similarityScore: 1.0` |

**Check ID**: `90634d8c-a424-4abf-a2a4-9c160c36a72b`

## T4.7 — Resolve Conflict (PROCEED)

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T4.7.1 Open T4.1 result | **FAIL** | UI crashed — used API |
| T4.7.2 Click "Resolve" | **FAIL** | UI crashed — used API |
| T4.7.3 Fill: PROCEED + notes | **PASS (API)** | `POST /api/conflict-checks/{id}/resolve` |
| T4.7.4 Resolution saved | **PASS (API)** | `resolution: "PROCEED"`, `resolutionNotes: "Separate matter..."` |
| T4.7.5 Resolved by = Alice, date = today | **PASS (API)** | `resolvedBy: "55fc3f72-..."`, `resolvedAt: "2026-04-03T23:06:49..."` |

## T4.8 — Resolve with Waiver

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T4.8.1 Open T4.2 result | **FAIL** | UI crashed — used API |
| T4.8.2 Resolve: WAIVER_OBTAINED + notes | **PASS (API)** | `POST /api/conflict-checks/{id}/resolve` |
| T4.8.3 Resolution saved with WAIVER_OBTAINED | **PASS (API)** | `resolution: "WAIVER_OBTAINED"` |

## T4.9 — Conflict Check History

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T4.9.1 Navigate to history | **FAIL** | UI crashed |
| T4.9.2 All 6 checks in chronological order | **PASS (API)** | `GET /api/conflict-checks` returns 6 checks, ordered by `checkedAt` desc |
| T4.9.3 Filter by CONFLICT_FOUND = 4 results | **PARTIAL** | `?result=CONFLICT_FOUND` returns 4 (includes Sipho cross-check). Expected 3 per test plan, but 4 is correct since T4.6 also matched |
| T4.9.4 Filter by NEW_CLIENT = 2 results | **PASS (API)** | `?checkType=NEW_CLIENT` returns BHP SA Mining + Shoprite |
| T4.9.5 "Checked By" = Alice for all | **PASS (API)** | All 6 have `checkedBy: "55fc3f72-..."` (Alice's member ID) |

## T4.10 — Input Validation

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T4.10.1 Empty name → validation error | **PASS (API)** | HTTP 400 |
| T4.10.2 Whitespace only → validation error | **PASS (API)** | HTTP 400 |
| T4.10.3 Special characters accepted | **PASS (API)** | "O'Brien", "van der Merwe", "Muller" all return 200 with NO_CONFLICT |

---

## New Gap

### GAP-P55-011: Conflict Check page crashes on load

**Track**: T4.1 — Conflict Check: Exact ID Number Match
**Step**: T4.1.1
**Category**: ui-error
**Severity**: major
**Description**: The Conflict Check page (`/org/{slug}/conflict-check`) crashes immediately on load with a TypeError in a react-hook-form Controller component. The error is: `TypeError: Cannot read properties of undefined (reading 'map')`. This is the same class of bug as GAP-P55-006 (court date dialog). The entire page is non-functional — no form, no history, nothing renders. Backend API works correctly for all conflict check operations.
**Evidence**:
- Module: conflict_check
- Endpoint: `/org/{slug}/conflict-check` (frontend page)
- Expected: Conflict check form with check type selector, name/ID fields, submit button
- Actual: "Something went wrong" error boundary, TypeError in Controller.render()
**Suggested fix**: Likely a select/combobox field in `conflict-check-form.tsx` where options array is undefined at render time. Same pattern as GAP-P55-006 — add defensive `?? []` on the options array passed to the Controller render function.
