# Fix Spec: GAP-L-43 — Portal read model stays PENDING after submit

## Problem

`PortalEventHandler` exposes `@TransactionalEventListener` methods for `RequestItemAcceptedEvent` and `RequestItemRejectedEvent`, but no listener for `RequestItemSubmittedEvent`. As a result, when the portal submits a response for a request item:

- Tenant schema is correctly mutated (`tenant_*.request_items.status` flips `PENDING → SUBMITTED`, `document_id` is populated, `submitted_at` set) — verified on Day 4 (three FICA items for REQ-0002 at 22:01:27/22:05:06/22:06:17 in `tenant_5039f2d497cf.request_items`).
- Portal read model (`portal.portal_request_items`) is never updated — rows remain `PENDING` with frozen `synced_at` from the parent request's create event.
- Aggregate counts on `portal.portal_requests` are stale (`submitted_items=0` while `total_items=3`).
- Portal UI list + detail therefore read "0/3 submitted • status SENT" even after a successful submission, and every downstream portal-POV day (8, 11, 30, 46, 61, 75) inherits the stale view.

## Root Cause (confirmed via code read)

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java`

- **Existing listeners** (Accepted + Rejected) are at lines **841–854** and **856–870** respectively. Both use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, wrap work in `handleInTenantScope(event.tenantId(), event.orgId(), …)`, call `readModelRepo.updatePortalRequestItemStatus(itemId, "<status>", rejectionReason?, null, null)`, then `readModelRepo.recalculatePortalRequestCounts(event.requestId())`.
- **Missing listener**: no `@TransactionalEventListener` method consuming `RequestItemSubmittedEvent`. `RequestItemSubmittedEvent` is imported nowhere in this file (it is imported only by `PortalInformationRequestService`, `InformationRequestNotificationHelper`, `InformationRequestNotificationEventListener`, and its own definition file).
- **Event publisher**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalInformationRequestService.java` line **280–298** (`publishItemSubmittedEvent`). The event is published from `submitItem` (line 196) and `submitTextResponse` (line 219), both of which are `@Transactional`. `handleInTenantScope` + `AFTER_COMMIT` phase is therefore the correct contract — mirrors the Accepted path exactly.
- **Event shape** (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/RequestItemSubmittedEvent.java`, lines 11–26): carries `itemId`, `requestId`, `tenantId`, `orgId`, `occurredAt`, `portalContactId`, `customerId`. It does **not** carry `documentId` or `textResponse` as typed fields. The listener must therefore re-read the item from the tenant schema (inside the `handleInTenantScope` block) via the already-injected `requestItemRepository` to obtain those values before writing them to the portal read model.
- **Read-model repo** (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/repository/PortalReadModelRepository.java` lines 868–878 + 880–893): `updatePortalRequestItemStatus(UUID itemId, String status, String rejectionReason, UUID documentId, String textResponse)` and `recalculatePortalRequestCounts(UUID requestId)` are the exact calls needed — no new repo methods required.

## Fix

Add a single `@TransactionalEventListener` method to `PortalEventHandler` mirroring `onRequestItemAccepted`/`onRequestItemRejected`, with the one difference that it re-reads the `RequestItem` inside the tenant scope to pull `documentId` + `textResponse` (since the event doesn't carry them).

### 1. Add import

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java`

Add (alphabetical order, between the existing `RequestItemAcceptedEvent` and `RequestItemRejectedEvent` imports at lines 32–33):

```java
import io.b2mash.b2b.b2bstrawman.event.RequestItemSubmittedEvent;
```

### 2. Add listener method

Insert the following **between** the existing `onInformationRequestCompleted` (line 822–838) and `onRequestItemAccepted` (line 840) handlers, so the Submitted listener naturally precedes Accepted/Rejected in chronological flow:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onRequestItemSubmitted(RequestItemSubmittedEvent event) {
  handleInTenantScope(
      event.tenantId(),
      event.orgId(),
      () -> {
        try {
          var item =
              requestItemRepository
                  .findById(event.itemId())
                  .orElseThrow();
          readModelRepo.updatePortalRequestItemStatus(
              event.itemId(),
              "SUBMITTED",
              null,
              item.getDocumentId(),
              item.getTextResponse());
          readModelRepo.recalculatePortalRequestCounts(event.requestId());
        } catch (Exception e) {
          log.warn("Failed to project RequestItemSubmittedEvent: itemId={}", event.itemId(), e);
        }
      });
}
```

Notes:

- `requestItemRepository` is already injected (constructor line 106, field line 90).
- `handleInTenantScope` already binds `RequestScopes.TENANT_ID` + `ORG_ID` so the repository read resolves against the correct `tenant_*` schema (same pattern used by `onInformationRequestSent` at lines 747–748 for `itemRepository.findById`/`findByRequestId`).
- `documentId` and `textResponse` are mutually exclusive per `PortalInformationRequestService.submitResponse` (line 158–167) — at most one is non-null; the existing `updatePortalRequestItemStatus` signature handles both being passed through.
- The `try/catch` + warn-log pattern matches the Accepted/Rejected siblings exactly.
- Recount is required because `portal.portal_requests.submitted_items` is a persisted aggregate (see `recalculatePortalRequestCounts` lines 880–893) — without it, the parent request row stays at `submitted_items=0` even if the per-item row flips.

### 3. (Optional) Integration test

If an integration test exists that covers `onRequestItemAccepted` into the portal read model, mirror one test case for the submit path: publish a `RequestItemSubmittedEvent` for a pre-seeded `PENDING` item that has a `document_id` set, assert the matching `portal.portal_request_items` row flips to `SUBMITTED` with the `document_id` populated and the parent `portal.portal_requests.submitted_items` increments by 1.

If no such test harness exists, skip — the manual scenario path (Day 4 checkpoints 4.12–4.13) is the primary verification.

## Scope

- **Backend only.**
- Files to modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java` (1 import + 1 listener method).
- Files to create: (optional) integration test mirroring any existing Accepted-listener test.
- No new repository methods, no new DB migrations, no event-shape changes, no frontend or portal changes.
- Backend restart required after merge (new listener bean method must be registered by Spring).

## Verification

1. **Unit / integration**: if the optional test is added, `./mvnw test -Dtest='*PortalEventHandler*'` should pass including the new submit case.
2. **Manual (rerun Day 4 checkpoints 4.12–4.14)**:
   - Sipho re-uploads a FICA item from portal `/requests/{REQ-0002}`.
   - Immediately after upload: `SELECT status, document_id FROM portal.portal_request_items WHERE id = <itemId>` → `SUBMITTED` + non-null `document_id`.
   - `SELECT submitted_items, total_items, status FROM portal.portal_requests WHERE id = <requestId>` → `submitted_items` increments by 1.
   - Portal UI `/requests` list card for REQ-0002 now reflects "N/3 submitted" matching the tenant schema.
   - Portal UI `/requests/{REQ-0002}` detail shows the uploaded item's status badge as `SUBMITTED`, not `PENDING`.
3. **Regression sweep**: re-run Accepted + Rejected paths (Day 5 checkpoints 5.2/5.3) to confirm the existing listeners still update status correctly and counts recalculate — no behavioural change expected since the new listener is additive.

## Estimated Effort

**S (<30 min).** Single-file change in `PortalEventHandler.java` (1 import + ~15-line method), backend restart, manual re-verification on tenant + portal schemas.
