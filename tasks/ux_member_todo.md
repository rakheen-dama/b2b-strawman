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

### 10. RecurringScheduleService — 3N+1 queries per list call

**Status**: ✅ DONE — batch-loaded in fix/n-plus-one-batch-loading branch. `buildResponse()` made 3 individual `findById()` calls per schedule.

| What | Where | Detail |
|------|-------|--------|
| N+1 source #1 | `RecurringScheduleService.java:639-641` | `resolveTemplateName()` → `ProjectTemplateRepository.findById()` per schedule |
| N+1 source #2 | `RecurringScheduleService.java:643-645` | `resolveCustomerName()` → `CustomerRepository.findById()` per schedule |
| N+1 source #3 | `RecurringScheduleService.java:647-652` | `resolveMemberName()` → `MemberRepository.findById()` per schedule |
| Affected endpoint | `list()` (line 246) | Streams all schedules through `buildResponse()` |
| Secondary N+1 | `listExecutions()` (line 362) | `buildExecutionResponse()` → `projectRepository.findById()` per execution (up to 50) |
| Missing field | `ScheduleResponse` | Has `UUID createdBy` but no `String createdByName` — missed in PR #276 |

**Impact**: 10 schedules = 30 queries. 50 schedules = 150 queries. Plus up to 50 more for executions.

**Fix plan**:
1. Add three batch-load helpers: `resolveTemplateNames()`, `resolveCustomerNames()`, `resolveMemberNames()` — collect IDs from schedule list, `findAllById()`, return `Map<UUID, String>`
2. Modify `list()` to call batch-loaders once, pass maps into `buildResponse(schedule, templateNames, customerNames, memberNames)`
3. Add batch `resolveProjectNames()` for `listExecutions()`
4. Add `createdByName` to `ScheduleResponse` (include `createdBy` in member batch-load)
5. Keep single-item methods (`get()`, `create()`, etc.) with individual resolution — 3 queries for 1 item is acceptable

---

### 11. DataRequestController — N+1 for customer names in `listRequests()`

**Status**: ✅ DONE — batch-loaded in fix/n-plus-one-batch-loading branch. `resolveCustomerName()` (line 170-172) called `customerRepository.findById()` per request.

| What | Where | Detail |
|------|-------|--------|
| N+1 source | `DataRequestController.java:170-172` | `resolveCustomerName()` — per-request `findById()` |
| Affected endpoint | `listRequests()` (line 62-76) | Iterates requests, resolves customer name per item |
| Already fixed | `resolveMemberNames()` (lines 174-186) | Member names correctly batch-loaded in PR #276 |
| Repository | `CustomerRepository` | `findAllById()` inherited from `JpaRepository` — ready to use |

**Fix plan**:
1. Add `resolveCustomerNames(List<DataSubjectRequest>)` — mirror existing `resolveMemberNames()` pattern
2. Update `listRequests()` to batch-load customer names: `var customerNames = resolveCustomerNames(requests)`
3. Change `DataRequestResponse.from()` call to use `customerNames.getOrDefault(req.getCustomerId(), "Unknown")`
4. Delete per-item `resolveCustomerName()` method

---

### 12. CostRateResponse + BillingRateResponse — missing null guards on member name lookup

**Status**: ✅ DONE — null guards added in fix/n-plus-one-batch-loading branch. `memberNames.get(rate.getMemberId())` had no fallback if member was deleted after cost/billing rate was created.

| What | Where | Detail |
|------|-------|--------|
| CostRate risk | `CostRateController.java:165` | `CostRateResponse.from()` — `memberNames.get(rate.getMemberId())` with no fallback |
| BillingRate risk | `BillingRateController.java:228` | `BillingRateResponse.from()` — same pattern (newly discovered) |
| DB constraint | `CostRate.java:22-23` | `@Column(nullable = false)` — DB prevents null `memberId`, but doesn't prevent missing lookups |
| Note | BillingRateController lines 230/232 | `projectId`/`customerId` correctly guarded with ternary — only `memberId` is unguarded |

**Fix plan**:
1. `CostRateResponse.from()`: change to `memberNames.getOrDefault(rate.getMemberId(), "")`
2. `BillingRateResponse.from()`: change to `memberNames.getOrDefault(rate.getMemberId(), "")`
3. Consistent with collector pattern `m.getName() != null ? m.getName() : ""` used in `resolveMemberNames()`

---

## 13. Member Webhook Sync — Founding Member Placeholder Data (ROOT CAUSE)

> **Priority**: P0 | **Status**: DONE (PR #277) | **Risk**: Medium (touches auth/member pipeline)

### Problem

The org founding member has `user_39p2TMx0TN7d9ypFbq4FhY35dQX` as their name and `user_39p2TMx0TN7d9ypFbq4FhY35dQX@placeholder.internal` as their email in the `members` table. The Clerk user has a real first/last name set. This means all the companion `{field}Name` work from PR #276 correctly resolves... to a Clerk user ID string.

### Root Cause Analysis

Two code paths can create a `Member` record, and they race:

1. **`MemberFilter.lazyCreateMember()`** (`member/MemberFilter.java:113-139`) — fires on the **first authenticated API request**. Only has the JWT `sub` claim (Clerk user ID). Creates a member with placeholder data:
   - `name` = `clerkUserId` (the raw `user_39p2TMx...` string)
   - `email` = `clerkUserId + "@placeholder.internal"`
   - `avatarUrl` = `null`

2. **`MemberSyncService.syncMember()`** (`member/MemberSyncService.java:48-98`) — fires when the Clerk `organizationMembership.created` webhook arrives. Has real name/email from Clerk API. If the member already exists, calls `member.updateFrom(email, name, avatarUrl, orgRole)`.

**The race — CONFIRMED via Clerk webhook logs**:

Clerk fires `organization.created` and `organizationMembership.created` within **8ms** of each other (`created_at: 1771378703848` vs `1771378703856`). The webhook payload contains the correct data (`first_name: "Rakheen"`, `last_name: "Dama"`, `email: "rakheend.subscriptions@gmail.com"`), so the webhook **was delivered**.

The failure happens in `MemberSyncService.syncMember()` at line 55:
```java
String schemaName = resolveSchema(clerkOrgId);
```
which calls (line 160-166):
```java
private String resolveSchema(String clerkOrgId) {
    return mappingRepository.findByClerkOrgId(clerkOrgId)
        .orElseThrow(() -> new IllegalArgumentException("No tenant provisioned for org: " + clerkOrgId))
        .getSchemaName();
}
```

If the `organizationMembership.created` webhook is processed before the `organization.created` provisioning completes (which creates the `org_schema_mapping` row), `resolveSchema` throws `IllegalArgumentException`. The frontend webhook handler catches and logs this silently (`frontend/lib/webhook-handlers.ts:153-159`), and the member is never synced. The user's subsequent API requests hit `MemberFilter.lazyCreateMember()` which stores the Clerk user ID as the name.

**This affects EVERY new org's founding member** — it's not a one-off.

### Contributing Factors

| Factor | File | Issue |
|--------|------|-------|
| Webhook race: membership sync before provisioning completes | `member/MemberSyncService.java:160-166` | `resolveSchema()` throws if schema not yet provisioned |
| Webhook error swallowed silently | `frontend/lib/webhook-handlers.ts:153-159` | `catch` logs but doesn't retry — member permanently stuck |
| Placeholder uses raw Clerk ID as name | `member/MemberFilter.java:119` | `clerkUserId` used as name — should be `null` or `"Unknown"` |
| `updateFrom()` blindly overwrites with null | `member/Member.java:85-91` | If webhook sends `name: null`, it blanks existing data instead of preserving it |
| Webhook swallows errors | `frontend/lib/webhook-handlers.ts:153-159` | `catch` logs but doesn't retry or flag the member as stale |
| No self-healing mechanism | `member/MemberFilter.java:106-111` | `resolveOrCreateMember` doesn't detect or log stale placeholder members |
| No re-sync endpoint for members | — | No way to trigger a bulk member re-sync from Clerk |
| Cache hides staleness | `member/MemberFilter.java:32-33` | Caffeine cache (1h TTL) means even if the DB is fixed, the stale ID is cached |

### Confirmed Root Cause (from investigation)

The race condition is **architectural** — Clerk fires `organization.created` and `organizationMembership.created` as independent HTTP requests (8ms apart). Even though the frontend `handleOrganizationCreated()` awaits provisioning before returning, it only blocks *its own handler*. A separate `organizationMembership.created` request can be processed concurrently on another server process.

**Timeline of failure:**
```
T+0ms     Clerk fires organization.created → Frontend handler A starts
T+8ms     Clerk fires organizationMembership.created → Frontend handler B starts
T+8ms     Handler B calls POST /internal/members/sync
T+8ms     MemberSyncService.resolveSchema() → findByClerkOrgId() → EMPTY
T+8ms     ❌ IllegalArgumentException("No tenant provisioned for org: ...")
T+8ms     Handler B catches error, logs, returns 200 to Clerk (no retry)
T+1500ms  Handler A finishes provisioning (schema + 28 migrations + seeders + mapping)
T+5000ms  User's first API request → MemberFilter.lazyCreateMember()
T+5000ms  Member saved with name="user_39p2TMx..." email="user_39p2TMx...@placeholder.internal"
T+5000ms  Member cached in Caffeine for 1 hour → stale data served everywhere
```

**This affects EVERY new org's founding member** — the 8ms gap is faster than the 1-3s provisioning time.

---

### Solution Design

#### Approach: Retry with backoff in `syncMember()` (backend-side)

**Why backend, not frontend?** The frontend webhook handler is fire-and-forget by design (returns 200 to Clerk immediately). Adding retry logic in the backend's `MemberSyncService` is simpler, keeps the fix in one layer, and handles any future callers of the sync endpoint.

**Why not a queue?** Overkill for a 1-3s delay. A simple retry loop with `Thread.sleep()` (or virtual thread equivalent) is sufficient — this is an internal endpoint called once per member creation.

---

### Implementation Plan

#### Layer 1: Fix the race (P0)

| # | What | Where | Details |
|---|------|-------|---------|
| A | **Retry `resolveSchema()` with backoff** | `MemberSyncService.java:55` | If `findByClerkOrgId()` returns empty, retry up to 5 times with 500ms intervals (total wait: up to 2.5s). Only for `IllegalArgumentException` from `resolveSchema()`. Log each retry at WARN level. |
| B | **Change placeholder name to `null`** | `MemberFilter.java:119` | `new Member(clerkUserId, clerkUserId + "@placeholder.internal", null, null, orgRole)` — name becomes `null` instead of the raw Clerk user ID. Companion `*Name` fields already handle null → `"—"` in the frontend. |
| C | **Make `updateFrom()` null-safe** | `Member.java:85-91` | Only overwrite a field if the new value is non-null: `if (email != null) this.email = email;` etc. Prevents Clerk sending partial data from blanking existing fields. |

**Layer 1 is a single PR — ~20 lines of production code.**

#### Layer 2: Self-healing for existing data (P1)

| # | What | Where | Details |
|---|------|-------|---------|
| D | **Add `GET /internal/members/stale`** | New endpoint in `MemberSyncController` | `memberRepository.findByEmailEndingWith("@placeholder.internal")` — returns list of `{clerkUserId, name, email}`. Requires a new repo method. |
| E | **Add `POST /internal/members/sync-all`** | New endpoint in `MemberSyncController` | Accepts `List<SyncMemberRequest>`, loops through `syncMember()` for each. Idempotent — safe to call repeatedly. |
| F | **Frontend stale-member repair on org load** | `frontend/lib/webhook-handlers.ts` or a new API route | On `organization.created` (after provisioning succeeds), re-fetch all org members from Clerk API and POST to `/internal/members/sync-all`. This catches the founder who was missed during the race. |

**Alternative for F**: Instead of a webhook-time repair, add a **Server Component check** on the Settings page that calls `/internal/members/stale` and shows a "Re-sync members" button for admins.

#### Layer 3: Observability (P2)

| # | What | Where | Details |
|---|------|-------|---------|
| G | **Log stale member detection on login** | `MemberFilter.resolveOrCreateMember()` | After finding an existing member, check if `email` ends with `@placeholder.internal`. If so, log WARN: `"Stale placeholder member detected: {clerkUserId} in tenant {tenantId}"`. |
| H | **Evict cache on sync** | `MemberSyncService.syncMember()` | After updating a member, call `memberFilter.evictFromCache(tenantId, clerkUserId)` — already done in `deleteMember()` but missing in `syncMember()`. This ensures the next request picks up the real name. |

---

### Recommended Implementation Order

```
1. ✅ Layer 1A (retry in syncMember)     — eliminates the race for all new orgs
2. ✅ Layer 1B (null placeholder name)   — stops showing Clerk user IDs in UI
3. ✅ Layer 1C (null-safe updateFrom)    — defensive, prevents future data loss
4. ✅ Layer 2H (cache eviction on sync)  — ensures fixed data propagates immediately
5. ✅ Layer 2D (stale endpoint)          — enables data repair for existing orgs
6. Layer 2E+F (bulk sync + auto-repair) — self-healing, no manual intervention
7. Layer 3G (login detection)           — observability for any remaining edge cases
```

Items 1–5 shipped in PR #277 (18 files, +323/-63). Also fixed null-safe `Collectors.toMap()` across 12 controllers (cascade from 1B). Items 6–7 can follow.

---

### Code Sketches

#### 1A: Retry in `MemberSyncService.syncMember()`

```java
// Replace: String schemaName = resolveSchema(clerkOrgId);
// With:
String schemaName = resolveSchemaWithRetry(clerkOrgId);

private String resolveSchemaWithRetry(String clerkOrgId) {
    for (int attempt = 1; attempt <= 5; attempt++) {
        var mapping = mappingRepository.findByClerkOrgId(clerkOrgId);
        if (mapping.isPresent()) {
            return mapping.get().getSchemaName();
        }
        if (attempt < 5) {
            log.warn("Schema not yet provisioned for org {} (attempt {}/5), retrying in 500ms...",
                clerkOrgId, attempt);
            try { Thread.sleep(500); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalArgumentException("Interrupted while waiting for schema: " + clerkOrgId);
            }
        }
    }
    throw new IllegalArgumentException("No tenant provisioned for org after 5 attempts: " + clerkOrgId);
}
```

#### 1B: Null placeholder name in `MemberFilter.lazyCreateMember()`

```java
// Change line 119 from:
var member = new Member(clerkUserId, clerkUserId + "@placeholder.internal", clerkUserId, null, orgRole);
// To:
var member = new Member(clerkUserId, clerkUserId + "@placeholder.internal", null, null, orgRole);
```

#### 1C: Null-safe `Member.updateFrom()`

```java
public void updateFrom(String email, String name, String avatarUrl, String orgRole) {
    if (email != null) this.email = email;
    if (name != null) this.name = name;
    if (avatarUrl != null) this.avatarUrl = avatarUrl;
    if (orgRole != null) this.orgRole = orgRole;
    this.updatedAt = Instant.now();
}
```

#### 2H: Cache eviction in `MemberSyncService.syncMember()`

```java
// After member save (both create and update paths), add:
memberFilter.evictFromCache(schemaName, clerkUserId);
```

---

### Data Repair (existing stale members)

For orgs already affected, run after deploying Layer 2D+E:

```bash
# 1. Find all tenant schemas
psql -c "SELECT schema_name, clerk_org_id FROM public.org_schema_mapping;"

# 2. For each schema, find stale members
psql -c "SELECT clerk_user_id, name, email FROM tenant_abc123.members WHERE email LIKE '%@placeholder.internal';"

# 3. Use the /internal/members/sync-all endpoint with data from Clerk API
# Or manually update:
psql -c "UPDATE tenant_abc123.members SET name='Rakheen Dama', email='rakheend.subscriptions@gmail.com' WHERE clerk_user_id='user_39p2TMx0TN7d9ypFbq4FhY35dQX';"
```

---

### Tests Required

| Test | Where | What |
|------|-------|------|
| Retry succeeds after delay | `MemberSyncServiceIntegrationTest` | Create member sync request without provisioning first, provision in a separate thread after 1s, assert member is synced with real data |
| Retry exhausted | `MemberSyncServiceIntegrationTest` | Call sync without ever provisioning, assert `IllegalArgumentException` after 5 attempts |
| Null-safe updateFrom | `MemberTest` (unit) | Call `updateFrom(null, null, null, null)` on a member with real data, assert fields unchanged |
| Placeholder name is null | `MemberFilterIntegrationTest` | Assert lazy-created member has `name == null` (not the Clerk user ID) |
| Cache eviction on sync | `MemberSyncServiceIntegrationTest` | Lazy-create member (placeholder), sync with real data, assert subsequent `MemberFilter` resolution returns real name |
| Stale endpoint | `MemberSyncControllerIntegrationTest` | Create placeholder member, call `GET /internal/members/stale`, assert it appears |

---

### Decisions NOT Taken (and why)

| Option | Rejected Because |
|--------|-----------------|
| **Fetch from Clerk API in `MemberFilter`** (Layer 3I in original) | Adds 100-300ms latency to every first request per member. The retry approach fixes the root cause without impacting request latency. |
| **Frontend-side retry in webhook handler** | The webhook handler returns 200 to Clerk regardless. Adding frontend retry means managing timeouts, state, and error handling across two layers. Backend retry is self-contained. |
| **Event queue between provisioning and member sync** | Over-engineered for a 1-3s delay. `Thread.sleep()` is fine for an internal endpoint called once per member creation. |
| **Make `MemberFilter` never create placeholders** | Would break the first-request flow — member ID is needed for `RequestScopes.MEMBER_ID` binding, which all downstream services depend on. |
