# UX Fix: Replace Raw User IDs with Member Names

> **Priority**: Medium | **Effort**: ~1 day | **Risk**: Low (additive — no breaking changes)

## Problem

Several UI surfaces display raw UUIDs (e.g. `user_39p2TMx...`) instead of human-readable member names. The `Task` entity already solves this correctly with a `createdByName` companion field — the same pattern needs to be applied everywhere.

## Reference Pattern (Task — already correct)

**How it works**: Controller batch-loads member names via `MemberRepository`, passes a `Map<UUID, String>` into the response DTO factory method.

| Layer | File | Detail |
|-------|------|--------|
| Backend controller | `task/TaskController.java:278-292` | `resolveNames()` batch-loads from `MemberRepository` |
| Backend DTO | `task/TaskController.java:360-402` | `TaskResponse` has both `UUID createdBy` and `String createdByName` |
| Frontend type | `lib/types.ts:183` | `createdByName: string \| null` |
| Frontend component | `tasks/task-detail-sheet.tsx:403-408` | `{task.createdByName ?? "—"}` |

---

## Fixes Required

### 1. GeneratedDocument — `generatedBy` (ACTIVE BUG)

**Status**: Raw UUID visible in the UI right now.

The backend service (`GeneratedDocumentService:307-317`) already resolves `generatedByName` in `GeneratedDocumentListResponse`. The frontend just isn't using it.

| What | Where |
|------|-------|
| Backend DTO | `template/GeneratedDocumentService.java:307-317` — `generatedByName` already exists |
| Frontend type | `lib/types.ts:889-896` — add `generatedByName: string \| null` |
| Frontend component | `components/templates/GeneratedDocumentsList.tsx:172` — change `{doc.generatedBy}` to `{doc.generatedByName ?? "—"}` |

**Backend work**: None.
**Frontend work**: Update type + one line in component.

---

### 2. Project — `createdBy`

**Status**: Not currently displayed, but the field is exposed in the API and the type. Safe to add now for future use.

| What | Where |
|------|-------|
| Backend entity | `project/Project.java:31-32` — `UUID createdBy` |
| Backend DTO | `project/ProjectController.java:299-309` — `ProjectResponse` record, add `String createdByName` |
| Backend factory | `project/ProjectController.java:311-349` — `ProjectResponse.from()` needs `Map<UUID, String> memberNames` param |
| Backend controller | `project/ProjectController.java` — add `resolveNames()` helper (same pattern as TaskController) |
| Frontend type | `lib/types.ts:3-14` — add `createdByName: string \| null` to `Project` interface |

**Backend work**: Add name field to DTO, batch-load in controller list/detail endpoints.
**Frontend work**: Update type. Display wherever `createdBy` would be shown.

---

### 3. Customer — `createdBy` + `lifecycleStatusChangedBy`

**Status**: Not currently displayed.

| What | Where |
|------|-------|
| Backend DTO | `customer/CustomerController.java:383-439` — `CustomerResponse` record |
| Backend fields needed | `String createdByName`, and in `TransitionResponse` (:468): `String lifecycleStatusChangedByName` |
| Frontend type | `lib/types.ts:94-111` — `Customer` interface, add `createdByName` |
| Frontend type | `lib/types.ts:123-129` — `TransitionResponse` interface, add `lifecycleStatusChangedByName` |

**Backend work**: Add name fields to both DTOs, batch-load in controller.
**Frontend work**: Update types. Display wherever shown.

---

### 4. Invoice — `createdBy` + `approvedBy`

**Status**: Not currently displayed but important for audit trail.

| What | Where |
|------|-------|
| Backend DTO | `invoice/dto/InvoiceResponse.java:11-62` — lines 30-31 |
| Backend fields needed | `String createdByName`, `String approvedByName` |
| Frontend type | `lib/types.ts:636-660` — lines 655-656 |

**Backend work**: Add name fields to DTO, batch-load in InvoiceController.
**Frontend work**: Update types. Display in invoice detail view.

---

### 5. Document — `uploadedBy`

**Status**: Not currently displayed.

| What | Where |
|------|-------|
| Backend entity | `document/Document.java:41-42` — `UUID uploadedBy` |
| Frontend type | `lib/types.ts:44` — `uploadedBy: string \| null` |

**Backend work**: Add `uploadedByName` to document response DTO.
**Frontend work**: Update type. Display in document list/detail if desired.

---

### 6. Checklist — `completedBy` (instance + item)

**Status**: Not currently displayed.

| What | Where |
|------|-------|
| Backend entity (item) | `checklist/ChecklistInstanceItem.java:51-52` |
| Backend entity (instance) | `checklist/ChecklistInstance.java:35-36` |
| Frontend type (item) | `lib/types.ts:947` |
| Frontend type (instance) | `lib/types.ts:962` |

**Backend work**: Add `completedByName` to both response DTOs.
**Frontend work**: Update types. Display in checklist UI.

---

### 7. PeriodSummary — `closedBy`

**Status**: Not currently displayed.

| What | Where |
|------|-------|
| Backend DTO | `retainer/dto/PeriodSummary.java:24` |

**Backend work**: Add `closedByName` to DTO, resolve in service.
**Frontend work**: Update type. Display in retainer period views.

---

### 8. DataSubjectRequest — `requestedBy` + `completedBy`

**Status**: Not currently displayed.

| What | Where |
|------|-------|
| Backend entity | `datarequest/DataSubjectRequest.java:43-44, 49-50` |
| Frontend type | `lib/types.ts:983, 985` |

**Backend work**: Add `requestedByName` and `completedByName` to response DTO.
**Frontend work**: Update types.

---

### 9. RetainerAgreement — `createdBy`

**Status**: Not currently displayed.

| What | Where |
|------|-------|
| Backend entity | `retainer/RetainerAgreement.java:67-68` |

**Backend work**: Add `createdByName` to response DTO.
**Frontend work**: Update type.

---

## Implementation Order

| Priority | Item | Reason |
|----------|------|--------|
| **P0** | 1. GeneratedDocument | Active bug — raw ID visible to users now |
| **P1** | 2. Project | High-traffic entity, `createdBy` likely to surface in upcoming UI |
| **P1** | 4. Invoice | Audit trail — users need to see who created/approved |
| **P2** | 3. Customer | Useful for lifecycle tracking |
| **P2** | 5. Document | `uploadedBy` useful in document lists |
| **P3** | 6-9. Checklist, PeriodSummary, DataRequest, Retainer | Lower traffic, not yet displayed |

## Implementation Approach

For each entity, follow the Task pattern:

1. **Backend DTO**: Add `String {field}Name` companion field alongside the existing `UUID {field}`
2. **Backend controller/service**: Collect all UUIDs from the result set, batch-load names via `memberRepository.findAllById(uuids)`, build a `Map<UUID, String>` lookup, pass into DTO factory
3. **Frontend type**: Add `{field}Name: string | null` to the interface in `lib/types.ts`
4. **Frontend component**: Replace `{entity.field}` with `{entity.fieldName ?? "—"}`
5. **Tests**: Add assertions for name fields in integration tests

**Key rule**: Never remove the UUID field — keep both `createdBy` (for programmatic use) and `createdByName` (for display). This ensures zero breaking changes.

---

## Status: Companion Name Fields — DONE (PR #276)

All 9 entities above have been fixed. The remaining issues below were discovered during code review.

---

## Follow-up: Pre-existing N+1 Query Issues

> Found during the PR #276 code review. These are NOT regressions — they existed before the member name work.

### 10. RecurringScheduleService — N+1 per schedule in `list()`

**Status**: Pre-existing bug. `resolveMemberName()` calls `memberRepository.findById()` per schedule instead of batch-loading.

| What | Where |
|------|-------|
| N+1 source | `schedule/RecurringScheduleService.java` — `resolveMemberName()` (~line 647-651) |
| Affected endpoint | `list()` (~line 246) — iterates schedules, calls `buildResponse()` per item |
| Also N+1 | `resolveTemplateName()` and `resolveCustomerName()` in same file — same per-entity `findById()` pattern |

**Fix**: Extract all `projectLeadMemberId` + `createdBy` UUIDs from the schedule list, batch-load via `findAllById()`, pass `Map<UUID, String>` into `buildResponse()`. Same for template and customer names.

**Bonus**: `ScheduleResponse` has `UUID createdBy` but no `String createdByName` — add it while fixing the N+1.

---

### 11. DataRequestController — N+1 for customer names in `listRequests()`

**Status**: Pre-existing. `resolveCustomerName()` calls `customerRepository.findById()` per request.

| What | Where |
|------|-------|
| N+1 source | `datarequest/DataRequestController.java` — `resolveCustomerName()` (~line 170) |
| Affected endpoint | `listRequests()` (~line 62) — iterates requests, resolves customer name per item |

**Fix**: Collect all `customerId` UUIDs, batch-load via `customerRepository.findAllById()`, pass `Map<UUID, String>` into `DataRequestResponse.from()`.

Note: Member names in this controller were fixed correctly in PR #276 (batch `resolveMemberNames()`). Only the customer name path is N+1.

---

### 12. CostRateResponse — missing null guard

**Status**: Pre-existing. `memberNames.get(rate.getMemberId())` has no null guard — will NPE if `memberId` is null.

| What | Where |
|------|-------|
| Risk | `costrate/CostRateController.java` — `CostRateResponse.from()` (~line 163) |

**Fix**: Add `rate.getMemberId() != null ? memberNames.get(rate.getMemberId()) : null` (same pattern as all other entities).
