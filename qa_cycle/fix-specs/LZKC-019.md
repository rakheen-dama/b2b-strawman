# Fix Spec: LZKC-019 — Matter activity feed prints raw audit event keys for portal/document/statement events

## Problem
Day 61 / 61.9 (and Day 85 observation): the firm matter Activity tab renders "Sipho Dlamini performed portal.document.downloaded on document" and "statement.generated on generated_document" — raw event keys with no document names — while task events get friendly copy.

## Root Cause (verified)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java:44-135` — `formatMessage()` switch has no case arms for portal/document/statement/closure event types, so they hit the default at **line 134**: `default -> "%s performed %s on %s".formatted(actorName, eventType, entityType);`
- Frontend (`frontend/components/activity/activity-item.tsx:57`) only prints the server-formatted `message` — fix is 100% backend.
- Document names ARE already in the event payload — no schema change: `portal.document.downloaded` details carry `file_name` (`portal/PortalQueryService.java:198-207`); `statement.generated` carries `file_name` + `project_id` (`verticals/legal/statement/StatementService.java:190-201`); `resolveEntityName()` (ActivityMessageFormatter.java:301) already maps `document`/`generated_document` → `getFileName(details)`.
- Portal side already has the humanised label table to mirror: `portal/PortalActivityEventResponse.java:73-110` (`summaryFor()`).

Verified missing event types that reach the matter feed (cover the class, not just the two reported strings):
- Portal actions: `portal.document.downloaded`, `portal.document.upload_initiated`, `portal.request_item.submitted` (emitted at `customerbackend/service/PortalInformationRequestService.java:328` — the existing `information_request.item_submitted` case at line 110 is dead), `portal.invoice.paid` (`invoice/PaymentReconciliationService.java:164`), `portal.document.acknowledged`.
- Firm/document actions: `statement.generated`, `document.generated_with_clauses` (`template/GeneratedDocumentService.java:304`), `document.created` (`document/DocumentService.java:171/217/285` — switch only has `document.uploaded`), `document.accessed` (`DocumentService.java:538`), `document.visibility_changed` (`DocumentService.java:353`), `matter_closure.closed` / `matter_closure.reopened` / `matter.closure.override_used`.

## Fix
Add case arms to the `formatMessage` switch in `ActivityMessageFormatter.java` (before the line-134 default) for each event type above, third-person actor-prefixed phrasing with filename where available, e.g.:
- `case "portal.document.downloaded" -> "%s downloaded document \"%s\"".formatted(actorName, getFileName(details));`
- `case "statement.generated" -> "%s generated a statement of account \"%s\"".formatted(actorName, getFileName(details));`
- `case "portal.request_item.submitted" -> ...` (and delete or keep-as-alias the dead `information_request.item_submitted` case at line 110 — keep as alias, zero risk).

Also folded in (Day 85 observation): "View audit" control in Closure history is inert — treat as separate polish, NOT in this spec's scope unless orchestrator says otherwise.

## Scope
Backend only
Files to modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java` (+ its unit test)
Files to create: none
Migration needed: no

## Verification
Re-open firm matter Activity tab (Day 61.9 / Day 85 view): portal download rows read `Sipho Dlamini downloaded document "statement-of-account-…pdf"`; statement row reads friendly copy. Unit-test each new case in the existing `ActivityMessageFormatter` test class.

## Estimated Effort
M (30 min – 2 hr) — mechanical but ~12 case arms + tests

## Cluster members
Single gap, but the fix deliberately covers the whole missing-event-type class in one file (same mechanism: missing switch arms falling to the line-134 default).
