# Track 5 — LSSA Tariff Management (Cycle 1)

**Executed**: 2026-04-04
**Actor**: Alice Moyo (owner, legal tenant)
**Method**: API (backend) — UI page crashes (GAP-P55-012)

## Summary

The **Tariffs page crashes on load** with two errors:
1. Server-side: `TypeError: Cannot read properties of undefined (reading 'totalElements')` — page expects paginated response but API returns array
2. Client-side: `TypeError: Cannot read properties of undefined (reading 'length')` in `TariffBrowserClient`

All backend tariff schedule and item operations work correctly via direct API calls.

---

## T5.1 — Browse Seeded Tariff Schedules

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T5.1.1 Navigate to Tariff Schedules | **FAIL** | Page crashes with "Something went wrong" (GAP-P55-012) |
| T5.1.2 At least 1 system schedule exists | **PASS (API)** | `GET /api/tariff-schedules` returns 1 system schedule: "LSSA 2024/2025 High Court Party-and-Party" |
| T5.1.3 System schedule shows "System" badge | **FAIL** | Cannot verify — UI crashed. API: `isSystem: true` |
| T5.1.4 Click schedule → items load | **FAIL** | Cannot verify UI. API: `GET /api/tariff-items?scheduleId={id}` returns 19 items |
| T5.1.5 Items grouped by section | **PASS (API)** | Items have `section` field: "Instructions and consultations", "Pleadings and documents", etc. |
| T5.1.6 Each item shows: Item Number, Description, Amount, Unit | **PASS (API)** | All fields present: `itemNumber`, `description`, `amount`, `unit` |

**Schedule ID**: `88d40d0d-f0fc-4cf1-b999-8a9e95b55e21`

## T5.2 — Search Tariff Items

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T5.2.1-T5.2.3 | **SKIP** | UI crashed. No search parameter found on `/api/tariff-items` endpoint (only `scheduleId`). Search may be client-side only. |

## T5.3 — System Schedule Immutability

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T5.3.1 Edit button absent/disabled | **FAIL** | Cannot verify — UI crashed |
| T5.3.2 Delete button absent/disabled | **FAIL** | Cannot verify — UI crashed |
| T5.3.3 Items cannot be edited | **FAIL** | Cannot verify — UI crashed |
| T5.3.4 Items cannot be deleted | **FAIL** | Cannot verify — UI crashed |
| T5.3.5 API PUT returns 400 | **PASS (API)** | `PUT /api/tariff-schedules/{id}` → HTTP 400 |
| T5.3.5+ API DELETE returns 400 | **PASS (API)** | `DELETE /api/tariff-schedules/{id}` → HTTP 400 |

## T5.4 — Clone Schedule

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T5.4.1 Click "Clone" | **FAIL** | UI crashed. Used API: `POST /api/tariff-schedules/{id}/clone` |
| T5.4.2 Clone dialog name pre-filled with "(Copy)" | **PARTIAL (API)** | Clone endpoint auto-names to "(Copy)" suffix. No dialog to verify. |
| T5.4.3 Change name | **N/A** | Clone endpoint ignores request body name — always uses "(Copy)". Renamed via separate PUT. |
| T5.4.4 New schedule appears | **PASS (API)** | Clone returned new schedule with different ID |
| T5.4.5 Items deep-copied | **PASS (API)** | Cloned schedule has 19 items (all distinct IDs from original) |
| T5.4.6 Item count matches original | **PASS (API)** | 19 = 19 |
| T5.4.7 No "System" badge | **PASS (API)** | `isSystem: false` |

**Clone ID**: `b371c856-5b26-48a0-8495-0b5ff0dd23d2`

## T5.5 — Edit Custom Schedule

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T5.5.1 Open cloned schedule | **FAIL** | UI crashed |
| T5.5.2 Edit item amount (+10%) | **PASS (API)** | `PUT /api/tariff-items/{id}` → amount 780 → 858. HTTP 200. |
| T5.5.3 Amount saved correctly | **PASS (API)** | Response shows `amount: 858.00` |
| T5.5.4 Add new item: 99.1 / Custom Services / Mining rights advisory / 3500.00 | **PASS (API)** | `POST /api/tariff-schedules/{id}/items` → 201 Created |
| T5.5.5 New item appears | **PASS (API)** | Item in response with correct fields |
| T5.5.6 Delete custom item | **PASS (API)** | `DELETE /api/tariff-items/{id}` → HTTP 204 |
| T5.5.7 Original system schedule unchanged | **PASS (API)** | Original item 1(a) still `amount: 780.00` |

## T5.6 — Create Custom Schedule from Scratch

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T5.6.1 Click "New Schedule" | **FAIL** | UI crashed |
| T5.6.2 Fill: Internal Advisory Rates / CIVIL / HIGH_COURT / 2026-01-01 | **PASS (API)** | `POST /api/tariff-schedules` → 201 Created |
| T5.6.3 Schedule created (empty) | **PASS (API)** | `itemCount: 0`, `isSystem: false` |
| T5.6.4 Add 3 items | **PASS (API)** | 3 items created via `POST /api/tariff-schedules/{id}/items` |
| T5.6.5 Items sort by sortOrder | **PASS (API)** | Items returned in sortOrder: 1, 2, 3 |

**Schedule ID**: `db14d737-321f-470c-a71c-0305a4c3141a`

---

## New Gap

### GAP-P55-012: Tariff Schedules page crashes on load

**Track**: T5.1 — Browse Seeded Tariff Schedules
**Step**: T5.1.1
**Category**: ui-error
**Severity**: major
**Description**: The Tariffs page (`/org/{slug}/legal/tariffs`) crashes with two errors:
1. Server-side: `TypeError: Cannot read properties of undefined (reading 'totalElements')` — the `TariffsPage` server component expects a paginated response shape (`{ content, page: { totalElements } }`) but `GET /api/tariff-schedules` returns a plain array.
2. Client-side: `TypeError: Cannot read properties of undefined (reading 'length')` in `TariffBrowserClient` — receives undefined instead of the expected data.

Root cause is a data shape mismatch: the backend returns `List<ScheduleResponse>` (array) while the frontend assumes Spring Data paginated format.
**Evidence**:
- Module: lssa_tariff
- Endpoint: `/org/{slug}/legal/tariffs` (frontend page)
- Expected: Tariff schedule list with system badge, clone button, item browser
- Actual: "Something went wrong" error boundary
**Suggested fix**: Either (a) change the backend `GET /api/tariff-schedules` to return paginated response (Spring Data `Page<>`) or (b) fix the frontend to handle array responses.
