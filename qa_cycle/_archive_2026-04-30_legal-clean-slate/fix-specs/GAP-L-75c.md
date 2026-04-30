# Fix Spec: GAP-L-75c — Portal-contact write events not persisted to `audit_events`

## Problem (verbatim from QA evidence)

Day 85 cycle-1 verify (2026-04-25 22:55-23:05 SAST,
`qa_cycle/checkpoint-results/day-85.md` §"Day 85 Re-Verify — Cycle 1",
step 85.4b "Filter by actor = Sipho (portal actor) → portal actions
recorded"). DB inspection (read-only `psql` exec on `b2b-postgres`,
DB `docteams`):

```
SELECT event_type, actor_type, COUNT(*)
FROM tenant_5039f2d497cf.audit_events
WHERE actor_type='PORTAL_CONTACT'
GROUP BY event_type, actor_type;
```

returns ONLY 2 rows tenant-wide:

| event_type | actor_type | count |
|---|---|---|
| `acceptance.viewed` | PORTAL_CONTACT | 1 |
| `acceptance.accepted` | PORTAL_CONTACT | 1 |

(both from Day 8 proposal acceptance via `AcceptanceService.markViewed`
+ `AcceptanceService.accept`, lines 310 + 377-390 of
`backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceService.java`.)

The following Sipho portal write actions executed during the 90-day
walk and **left zero rows** in `audit_events`:

| Day | Action | Code path | Audit emitted? |
|---|---|---|---|
| 4 | FICA item file upload (3×) | `PortalInformationRequestController.initiateUpload` → `PortalInformationRequestService.initiateUpload` | NO |
| 4 / 46 | Info-request item submit (file or text) | `PortalInformationRequestController.submitItem` → `PortalInformationRequestService.submitResponse` → `submitItem` / `submitTextResponse` | NO |
| 30 | Fee-note pay (mock checkout return) | `MockPaymentController.completeMockPayment` → `PaymentWebhookService.processWebhook` | NO |
| 61 | SoA / closure-letter download | `PortalDocumentController.presignDownload` → `PortalQueryService.getPresignedDownloadUrl` | NO |
| 11 / 46 / 61 / 75 | Project / trust ledger / invoice list view | All `PortalQueryService.list*` reads | NO (read events, lower priority — see Scope below) |

**Net effect**: scenario step 85.4b cannot be satisfied — even if
the matter Activity tab gains an actor filter (= L-75b) and even if
the firm-side audit-log surface ships (= L-75a, Phase 69), the
underlying data plane has nothing to filter. Exit checkpoint **E.14**
("Audit trail completeness — Day 85 audit log filters return both
firm-user events and portal-contact events over the 90 days") fails.
Day 88 wow-moment (firm + portal activity-feed side-by-side) is also
hollowed out — Sipho's half of the story has no events to render.

Reconfirms + widens the pre-existing Sprint-2 followup
**OBS-Day61-NoPortalDocAuditEvent** (which was scoped to doc downloads
only) to all portal write surfaces.

## Root Cause

The audit-emission pattern is well-established in the codebase
(`AuditEventBuilder` fluent builder + `AuditService.log(record)` —
see `AcceptanceService.java:310` and `:377` for the canonical
`actorType("PORTAL_CONTACT")` + `source("PORTAL")` shape). Two
portal-side services already wire `AuditService` in
(`PortalCommentService.java:43, :66`, but it incorrectly uses
`actorType("PORTAL_USER")` instead of `PORTAL_CONTACT` — separate
nit, see Cleanup below). The remaining portal write services were
never extended to emit:

- `PortalInformationRequestService` (`backend/.../customerbackend/service/PortalInformationRequestService.java`) — methods `initiateUpload` (line ~108), `submitItem` (line 171), `submitTextResponse` (line 201). All have `portalContactId` already in scope. None call `auditService.log(...)`.
- `PortalQueryService` (the read-side) — `getPresignedDownloadUrl(documentId, customerId)`. The portal contact identity is bound on `RequestScopes.PORTAL_CONTACT_ID` via `CustomerAuthFilter`. No audit emit.
- `PaymentWebhookService.processWebhook("mock", ...)` — flips invoice SENT→PAID and writes `payment_events` but not `audit_events`. The mock callback runs with a tenant context but no portal-contact identity bound (the callback is technically anonymous server-to-server). Emit needs to derive `portalContactId` from the invoice's customer → portal_contact link, OR (simpler, and more honest) emit with `actorType="SYSTEM"` + a `portal_contact_id` field in `details` (the user-initiated provenance is recorded via the `payment_events` row already, but the audit log needs a row to filter on).

No `AuditActorType` Java enum exists — `actorType` is a free-form
String on `AuditEventBuilder`. Existing values in use:
`USER`, `PORTAL_CONTACT`, `PORTAL_USER` (inconsistent — see Cleanup),
`SYSTEM`. Continue using `"PORTAL_CONTACT"` per the established
`AcceptanceService` pattern.

## Fix

Add `auditService.log(...)` calls at the **four** specific portal
write surfaces below, each emitted in the same `@Transactional`
boundary as the underlying mutation (per the `AcceptanceService`
pattern — no `REQUIRES_NEW`, so a rollback rolls back the audit row
too). Use the canonical builder shape:

```java
auditService.log(
    AuditEventBuilder.builder()
        .eventType("<event.type>")
        .entityType("<entity_type>")
        .entityId(<UUID>)
        .actorId(portalContactId)            // bind the portal contact UUID
        .actorType("PORTAL_CONTACT")
        .source("PORTAL")
        .details(Map.of(
            "project_id", projectId.toString(),  // for matter-scoped filter via findByProjectId
            // ...action-specific fields
        ))
        .build());
```

The `details.project_id` field is **load-bearing** — `AuditEventRepository.findByProjectId`
(line 51 of that file) and `findMostRecentByProject` (line 88) both
filter on `details->>'project_id'`, which is how the matter Activity
tab finds events. Without this field the new portal events would not
appear on the matter Activity feed even after L-75b lands.

### Site 1 — `PortalInformationRequestService.initiateUpload`

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalInformationRequestService.java`
Method: `initiateUpload(requestId, itemId, fileName, contentType, size, portalContactId)` (~line 108).
After `documentRepository.save(document)` and before returning
`UploadInitResult`, emit:

```java
auditService.log(
    AuditEventBuilder.builder()
        .eventType("portal.document.upload_initiated")
        .entityType("document")
        .entityId(document.getId())
        .actorId(portalContactId)
        .actorType("PORTAL_CONTACT")
        .source("PORTAL")
        .details(Map.of(
            "project_id", String.valueOf(request.getProjectId()),
            "request_id", requestId.toString(),
            "item_id", itemId.toString(),
            "file_name", fileName,
            "content_type", contentType,
            "size_bytes", size))
        .build());
```

### Site 2 — `PortalInformationRequestService.submitItem` + `submitTextResponse`

Same file. Both methods already share the post-save flow
(`itemRepository.save(item); autoTransitionToInProgress(request);
publishItemSubmittedEvent(request, item, portalContactId);`). Add a
single helper `emitItemSubmittedAudit(request, item, portalContactId,
documentId, hadText)` and call it once from each branch (or inline
twice, builder's call):

```java
auditService.log(
    AuditEventBuilder.builder()
        .eventType("portal.request_item.submitted")
        .entityType("request_item")
        .entityId(item.getId())
        .actorId(portalContactId)
        .actorType("PORTAL_CONTACT")
        .source("PORTAL")
        .details(Map.of(
            "project_id", String.valueOf(request.getProjectId()),
            "request_id", request.getId().toString(),
            "request_number", String.valueOf(request.getRequestNumber()),
            "item_name", String.valueOf(item.getName()),
            "response_type", documentId != null ? "FILE" : "TEXT",
            "document_id", documentId != null ? documentId.toString() : ""))
        .build());
```

### Site 3 — `PortalQueryService.getPresignedDownloadUrl`

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalQueryService.java`
(referenced by `PortalDocumentController.presignDownload`). After the
authorisation check passes and the presign URL is generated, emit:

```java
auditService.log(
    AuditEventBuilder.builder()
        .eventType("portal.document.downloaded")
        .entityType("document")
        .entityId(documentId)
        .actorId(customerId)        // see Note below — portalContactId preferred if bound
        .actorType("PORTAL_CONTACT")
        .source("PORTAL")
        .details(Map.of(
            "project_id", document.getProjectId() != null
                ? document.getProjectId().toString() : "",
            "file_name", document.getFileName(),
            "scope", document.getScope()))
        .build());
```

**Note on actorId**: `PortalDocumentController` currently calls
`RequestScopes.requireCustomerId()` (the `Customer` UUID), but
`CustomerAuthFilter` also binds `PORTAL_CONTACT_ID`. Use
`RequestScopes.requirePortalContactId()` instead (or fall back to
customerId if the contact id is not bound) so the actor row matches
the AcceptanceService rows already in the table. Update the
controller signature too if needed (one extra `requirePortalContactId()`
call — trivial).

### Site 4 — `PaymentWebhookService.processWebhook` (mock + future PSP)

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/payment/PaymentWebhookService.java`
After the invoice flips SENT→PAID and the `payment_events` row is
written, emit:

```java
auditService.log(
    AuditEventBuilder.builder()
        .eventType("portal.invoice.paid")
        .entityType("invoice")
        .entityId(invoice.getId())
        .actorType("PORTAL_CONTACT")  // payer is a portal contact in our flows
        .source("PORTAL")
        .details(Map.of(
            "project_id", invoice.getProjectId() != null
                ? invoice.getProjectId().toString() : "",
            "invoice_number", String.valueOf(invoice.getInvoiceNumber()),
            "amount_minor_units", String.valueOf(invoice.getAmountMinorUnits()),
            "currency", String.valueOf(invoice.getCurrency()),
            "payment_reference", reference,
            "provider", "mock"))
        .build());
```

Resolve `actorId` from the invoice's customer → portal_contact link
where possible (lookup is one repo call); leave `actorId=null` if no
portal contact can be derived (the `PORTAL_CONTACT` actorType + the
`portal_contact_id` in details still satisfies E.14 filtering by
matter; actor-name resolution falls through gracefully).

### Cleanup (drive-by, optional but cheap)

`PortalCommentService.java:72` uses `actorType("PORTAL_USER")` — the
only writer in the codebase that does. Change to `"PORTAL_CONTACT"`
for consistency with `AcceptanceService` and the new emissions; back-fill
shouldn't be needed (no consumer keys off this string today besides
the future Phase 69 audit-log filter, which won't ship in-cycle).
Out of scope if it inflates the PR — log as a separate L-75c-followup.

## Scope

**Backend, files**:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalInformationRequestService.java` — wire `AuditService` constructor injection; add 3 emit sites (initiateUpload + submitItem + submitTextResponse, or 1 helper called from 3 places).
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalQueryService.java` — wire `AuditService`; add 1 emit in `getPresignedDownloadUrl`.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalDocumentController.java` — pass `portalContactId` through (one-line change) so the service can emit with the correct actor.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/payment/PaymentWebhookService.java` — wire `AuditService`; add 1 emit after the invoice status flip.
- (Optional) `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalCommentService.java:72` — `PORTAL_USER` → `PORTAL_CONTACT` string fix.

**Tests**: 1 integration test per service, using the canonical
`@SpringBootTest + MockMvc + @Import(TestcontainersConfiguration.class)`
shape per `backend/CLAUDE.md`. Each test exercises the portal endpoint
and asserts a row landed in `audit_events` with
`actor_type='PORTAL_CONTACT'` and the correct `event_type` and
`details.project_id`. Reuse `TestEntityHelper`, `TestJwtFactory`, and
the existing `PortalContact` provisioning helpers — no new helpers
needed. ~4 new tests (one per service touched), each ~50 lines.

**No frontend changes** in this spec. (L-75b actor-filter UI is
deferred to Sprint 2 — see status.md row for L-75b.)

**No DB migration**. `audit_events` is already created by Phase 6
global migration; `details` is JSONB so new keys are additive.

**No new entities, no new endpoints**. Pure additive emit calls.

## Verification

1. **Backend rebuild + restart** (NEEDS_REBUILD=true — Java change):
   `bash compose/scripts/svc.sh restart backend && bash compose/scripts/svc.sh status`.
2. **DB pre-state snapshot**:
   `docker exec b2b-postgres psql -U postgres -d docteams -c "SELECT COUNT(*) FROM tenant_5039f2d497cf.audit_events WHERE actor_type='PORTAL_CONTACT';"` — record the baseline (expect 2).
3. **Re-walk one portal write of each kind on the existing Dlamini matter** (`e788a51b-…`) with a fresh Sipho magic-link via `http://localhost:3002/login`:
   - Upload one new file against the open REQ-0001 / REQ-0007 item slot (or open a fresh ad-hoc info request firm-side first if all items are SUBMITTED).
   - Submit the item (file or text).
   - Generate a new mock-payment fee-note (Day 30 path) and complete via mock checkout.
   - Trigger a SoA download (Day 61 path) — Documents tab → SoA → Download.
4. **DB post-state assertion**:
   ```sql
   SELECT event_type, actor_type, COUNT(*)
   FROM tenant_5039f2d497cf.audit_events
   WHERE actor_type='PORTAL_CONTACT'
   GROUP BY event_type, actor_type
   ORDER BY 1;
   ```
   Expect: `acceptance.viewed`, `acceptance.accepted`, `portal.document.upload_initiated`, `portal.request_item.submitted`, `portal.invoice.paid`, `portal.document.downloaded` — at least one row per new event_type, total ≥ 6 rows.
5. **Matter-scoped query** (proves `details.project_id` is set correctly so the future Activity tab filter and the Phase 69 page can find them):
   ```sql
   SELECT event_type, occurred_at
   FROM tenant_5039f2d497cf.audit_events
   WHERE actor_type='PORTAL_CONTACT'
     AND (details->>'project_id') = 'e788a51b-...';
   ```
   Expect ≥ 4 rows for the new event types (download, upload_initiated, item.submitted, invoice.paid).
6. **E.14 sufficiency**: with the new rows in place, even with the
   matter Activity tab still showing only firm-side events (L-75b
   deferred), the cross-actor exit checkpoint can be satisfied via DB
   evidence in the QA cycle write-up. Day 88 demo screenshot can also
   show the portal half via `psql` output as a fallback if no UI
   surface exists yet.
7. **Test gate** (E.15): `cd backend && ./mvnw -B verify` → BUILD SUCCESS,
   zero failures. The 4 new integration tests must pass. No frontend
   gates affected.

## Estimated Effort

**S–M (~3 hr total)**:
- 30 min — wire `AuditService` injection into 3 services (constructor edits) + 1-line pass-through in `PortalDocumentController`.
- 60 min — write the 4 emit calls (or 3 + 1 helper) and verify against the existing `AcceptanceService` builder pattern.
- 60 min — write the 4 integration tests (one per service touched).
- 30 min — local browser-driven smoke walk (REQ submit + mock-payment + download) and DB assertion run.

NEEDS_REBUILD: **true** (Java source change → backend restart).
NEEDS_DB_MIGRATION: **false** (`details` JSONB is additive).
RISK: low — pure additive emit calls inside existing transactions,
follows the canonical builder pattern already exercised by
`AcceptanceService`. No behavior change in any existing flow.
