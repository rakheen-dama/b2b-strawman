# QA Cycle 2026-04-25 — Gap Closure Plan

**Source cycle**: `bugfix_cycle_2026-04-24` verify cycle (commit `5367c463`).
**Cycle outcome**: 9 gaps verified (PRs #1125, #1127, #1130, #1133, #1134, #1135, #1136, #1137, #1138, #1139). 17 items deferred — this doc closes all of them. **No workarounds. Root-cause fixes only.**
**Authority**: User directive 2026-04-25 — "no workarounds, fix all bugs." Supersedes the prior cycle's defer-to-Sprint-2 decisions where they used "workaround viable" as the justification.

---

## How to Read This Doc

- Each bug has a **plain-English** description any reader can understand, then root cause + fix approach + scope.
- Bugs are grouped into **Epics** by the surface they touch (trust accounting, document pipeline, portal terminology, etc.) so they can be assigned to a single owner without context switching.
- The **Sequenced Build Order** at the bottom shows which Epics gate which others.
- **Product-decision flags** (KYC provider, real PayFast credentials) are called out — those bugs cannot start coding until the decision lands.

---

## Epic Overview

| Epic | Name | Surface | Bugs | Effort | Blocks |
|------|------|---------|------|--------|--------|
| **E1** | Trust accounting reversal cascade + nudge email | Backend (services + listener) | L-70, MINOR-Trust-Nudge-Email-Missing | M | — |
| **E2** | Document pipeline + visibility model | Backend + frontend | L-45-regression, L-67, L-74-followup, L-74-followup-2, OBS-Day61-NoProjectDocVisibilityToggle, OBS-Day61-PortalDocumentsProjectionPartial | L | — |
| **E3** | Portal terminology + UX seam | Portal frontend + backend (email service) | L-65, L-66, L-68, C5 (MINOR-Copy-Projects), L-73-followup | M | — |
| **E4** | Audit + activity surfaces (Phase 69 minimum) | Backend + portal + firm frontend | L-75a (stub), L-75b, OBS-NoPortalActivityTrail | M | E1 (if cascade events get audited) |
| **E5** | Closure-pack notifications + digest manual trigger | Backend (listener + admin endpoint) | L-72, OBS-Day75-NoManualDigestTrigger | M | E2 (Visibility.PORTAL enum) |
| **E6** | SoA template + retention card UX | Backend (template) + frontend | OBS-Day60-SoA-Fees/Trust-Empty, OBS-Day60-RetentionShape | S | — |
| **E7** | Real PayFast sandbox integration | Backend + infra (tunnel) | L-64-followup | M | **Product/infra decision required** |
| **E8** | KYC adapter wiring | Backend (integration registry) | L-30 | M | **Product/provider decision required** |
| **E9** | Vertical content packs (RAF + bulk billing) | Pack JSON + migration | L-36, L-61-followup, L-58 | S–M | — |
| **E10** | Multi-role portal-contact CRUD | Backend + portal frontend | L-40 | M | E3 (terminology overlap) |
| **E11** | Proposal fee-estimate side-table | Backend (entity + variable resolver) + frontend (component) | L-49 | M | — |
| **E12** | Beneficial-owners structured field group | Pack JSON + field service | L-54 | M | — |
| **E13** | Test debt — re-enable disabled S3 integration tests | Backend test config | H1 (26 tests) | S | — |
| **E14** | Doc-drift redirect | Frontend route | MINOR-Doc-Drift-26 | S | — |

---

# Epic E1 — Trust accounting reversal cascade + nudge email

## E1.1 — GAP-L-70: FEE_TRANSFER reversal must roll back the linked invoice

**Plain English**: When a firm books a fee transfer from a client's trust ledger to the firm's office account, the system marks the linked invoice PAID. If the firm later realises the transfer was wrong and reverses it, the trust balance correctly snaps back — but the invoice stays stuck in PAID state, hiding the reversal from the matter ledger and from the client. The system should automatically undo the payment record on the invoice when the underlying trust transaction is reversed, so the books always tie out.

**Root cause**: `TrustTransactionService.reverseTransaction` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java:959-1092`) handles the trust-side ledger reversal for debit types (lines 1056-1071) but never calls back into the invoice domain. The original approval path at `performApprovalCompletion` (lines 840-853) writes a `payment_event` row and flips the invoice via `InvoiceTransitionService.recordPayment`, but there is no symmetric reverse method.

**Fix approach**:
1. **New backend method**: `InvoiceTransitionService.reversePayment(UUID invoiceId, UUID paymentEventId)`. Behaviour: load invoice; require status = `PAID`; load the named `payment_event` row; **delete** that one row only; recompute remaining `payment_events.completed` count for the invoice. If zero remain → flip invoice `PAID → SENT`, clear `paid_at` and `payment_reference`, publish `InvoicePaymentReversedEvent`. If ≥1 remain (multi-payment case) → keep invoice PAID, publish `InvoicePaymentPartiallyReversedEvent` only.
2. **Wire from trust side**: in `TrustTransactionService.reverseTransaction`, after marking the original `FEE_TRANSFER` row REVERSED (line ~1057), check `original.getInvoiceId() != null` and call `invoiceTransitionService.reversePayment(invoiceId, original.getPaymentEventId())`. Both writes share the same `@Transactional` boundary.
3. **Repository helper**: `PaymentEventRepository.findByInvoiceIdAndStatus(UUID, Status.COMPLETED)` to support the count check.
4. **Tests**: integration tests covering single-payment reversal (invoice flips back to SENT), multi-payment partial reversal (invoice stays PAID), and reversal of a transaction with no invoice link (no-op cascade).

**Scope**: M — 2 service methods (1 new on `InvoiceTransitionService`, 1 modified on `TrustTransactionService`), 1 new event class, 1 repository query, 4-6 integration tests. No DB migration. Backend restart required.

---

## E1.2 — MINOR-Trust-Nudge-Email-Missing: Trust deposit must email the portal contact

**Plain English**: When the firm records that a client deposited money into the firm's trust account, the client should get an email saying "we received your funds." Today, no email is sent — the client only sees the deposit by logging into the portal. The plumbing exists (the system already publishes a `TrustTransactionRecordedEvent` for read-model sync) but no email handler is subscribed.

**Root cause**: `TrustTransactionService.recordDeposit` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java:265-274`) publishes `TrustTransactionRecordedEvent.recorded(...)`. `TrustNotificationHandler` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustNotificationHandler.java:50-71`) only listens for `TrustTransactionApprovalEvent`, not `TrustTransactionRecordedEvent`.

**Fix approach**:
1. Add a second `@TransactionalEventListener(phase = AFTER_COMMIT)` method `onTrustTransactionRecorded(TrustTransactionRecordedEvent event)` in `TrustNotificationHandler`. Filter to `event.type() == DEPOSIT` (skip WITHDRAWAL/FEE_TRANSFER/REFUND — those have their own notifications via E5).
2. Resolve portal contacts for the matter's customer; for each, dispatch via `PortalEmailService.send("trust_deposit_received", contactEmail, model)` where `model` carries amount, currency, matter name, ledger balance after.
3. Create the email template at `backend/src/main/resources/templates/email/trust-deposit-received.html` (mirror existing trust templates).
4. Test: extend `TrustNotificationHandlerTest` with a deposit-recorded scenario asserting email sent + correct subject + recipient.

**Scope**: S — 1 listener method, 1 template, 1 test. Backend restart required.

**Cross-cutting**: Should land before/with E5 so the notification surface is uniform.

---

# Epic E2 — Document pipeline + visibility model

## E2.1 — GAP-L-45-regression: Per-item Download button on firm info-request detail

**Plain English**: When a client uploads a document for an information request, the firm's request-detail page is supposed to show a Download button next to that item. The button is wired in the UI but never appears — because the backend response doesn't include the file's display name, and the UI hides the button if that field is missing.

**Root cause**: `RequestItemResponse.from()` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/dto/InformationRequestDtos.java:149-169`) maps 18 fields from the entity but skips `documentFileName`. The frontend gate at `frontend/components/legal/information-requests/request-detail-client.tsx:342-356` requires both `item.documentId` and `item.documentFileName`.

**Fix approach**:
1. Add `String documentFileName` to the `RequestItemResponse` record (after `documentId`, around line 141).
2. Populate via the `Document` lookup pattern already used by `AcceptanceService.java:822` — inject `DocumentRepository` into the DTO factory or use a service helper. Code: `documentRepository.findById(item.getDocumentId()).map(Document::getFileName).orElse(null)`.
3. Test: extend `InformationRequestServiceTest` to assert `documentFileName` populated for ACCEPTED items with linked documents.

**Scope**: S — 1 DTO field, 1 factory line, 1 repository injection, 1 test. Backend restart required.

**Cross-cutting**: Land before E2.2 (ad-hoc info-request UI), so any newly created items also surface the file name.

---

## E2.2 — GAP-L-67: Firm UI for ad-hoc information requests

**Plain English**: Firms can create an information request from a pre-built template (FICA pack, Liquidation pack, etc.) but cannot create a free-form ad-hoc request — say "send me the medical report" — through the UI. The backend supports it; the firm UI just doesn't expose an "Add Item" button on a draft request and won't let the firm send a draft that has zero items.

**Root cause**: Backend `POST /api/information-requests/{id}/items` exists at `InformationRequestController.java:73` but has zero callers in `frontend/components/legal/information-requests/`. The detail page renders items from `request.items` only and provides no "Add Item" affordance. The Send button is gated on item count > 0 by a UI check, not backend logic.

**Fix approach**:
1. **New component** `AddItemDialog.tsx` next to `request-detail-client.tsx`. Form fields: name (TEXT), description (TEXTAREA), responseType (Select: TEXT_RESPONSE | FILE_UPLOAD), fileTypeHints (optional). Form submits via new server action `addItemAction(slug, requestId, item)` calling `POST /api/information-requests/{id}/items`.
2. **Wire to detail page**: in `request-detail-client.tsx`, render an "Add Item" button conditionally on `status === "DRAFT"` (above the items list). Click opens `AddItemDialog`. On success, revalidate page so the new item appears.
3. **Send button gate**: change the Send button visibility from `status === "DRAFT" && items.length > 0` to `status === "DRAFT"` only — let the backend decide whether a 0-item send is valid (today the backend allows it; if product wants to forbid, that's a separate backend rule).
4. **Capability gate**: same as the existing "Send" button (`MANAGE_INFORMATION_REQUESTS` or equivalent).
5. **Tests**: Vitest for the dialog form validation, server action handler, and detail-page integration.

**Scope**: M — 1 new dialog component, 1 server action, 1 detail-page edit, 4-6 Vitest cases. No backend changes. Frontend HMR — no restart.

**Cross-cutting**: Depends on E2.1 (DTO must surface `documentFileName` so ad-hoc items show downloads after submission).

---

## E2.3 — L-74-followup: Add `Visibility.PORTAL` enum value

**Plain English**: Today, every shared-with-portal document has visibility = SHARED, whether the firm clicked a "share with client" toggle or the system auto-shared a closure letter. For audit + analytics, we need to tell those two apart. Add a third value, `PORTAL` (or `CLIENT_VISIBLE`), reserved for system-auto-shared closure-pack artefacts.

**Root cause**: `Document.Visibility` is a constants holder with `INTERNAL` + `SHARED` only. `PortalQueryService.listProjectDocuments` filters on `visibility = SHARED`. `MatterClosureService.generateClosureLetterSafely` and `StatementService.generate` both call `documentService.toggleVisibility(linkedDocId, SHARED)` (after PR #1138), conflating system-share with manual-share.

**Fix approach**:
1. Add `PORTAL` constant to `Document.Visibility`.
2. Broaden `PortalQueryService.listProjectDocuments` filter from `visibility = 'SHARED'` to `visibility IN ('SHARED', 'PORTAL')`.
3. In `MatterClosureService.generateClosureLetterSafely` + `StatementService.generate`, change the auto-flip target from `SHARED` to `PORTAL`.
4. Flyway tenant migration `V113__split_visibility_portal.sql`: `UPDATE documents SET visibility='PORTAL' WHERE generated_from_template IN ('matter-closure-letter', 'statement-of-account')` (or `WHERE id IN (SELECT document_id FROM generated_documents WHERE template_slug IN (...))`).
5. Tests: extend `PortalQueryServiceTest` to assert both PORTAL and SHARED visible to portal; assert audit events distinguish the two.

**Scope**: S — 1 enum constant, 1 filter line, 2 service-call updates, 1 tenant migration, 2-3 test additions. Backend restart + tenant Flyway migration auto-applied.

**Cross-cutting**: Land before E2.4 (manual visibility toggle UI), so the toggle UI shows three states (INTERNAL / SHARED / PORTAL) coherently.

---

## E2.4 — OBS-Day61-NoProjectDocVisibilityToggle: Firm UI to flip document visibility

**Plain English**: A firm user cannot toggle a single document between INTERNAL (firm only) and SHARED (visible to portal contact) from the matter Documents tab. Today the workaround is to delete + re-generate the document. Firms need a per-row toggle.

**Root cause**: Backend `DocumentService.toggleVisibility(documentId)` already exists. Frontend matter Documents tab (under `frontend/app/(app)/org/[slug]/matters/[id]/documents/` or equivalent) lists documents but doesn't render a per-row toggle.

**Fix approach**:
1. **Backend endpoint** (if missing): `PATCH /api/documents/{id}/visibility` body `{visibility: "INTERNAL" | "SHARED"}` returning updated `DocumentResponse`. Gate with `@RequiresCapability("MANAGE_DOCUMENTS")`.
2. **Frontend column**: add Visibility column to the documents table with an icon button (eye / eye-off / portal-icon). Clicking opens a small inline confirm or directly fires server action `toggleDocumentVisibility(slug, documentId, target)`.
3. **Refresh**: revalidate the documents list query so the change is visible immediately.
4. **Disable on PORTAL-class docs**: documents with `visibility=PORTAL` (system-auto-shared) should show the icon but disable the manual toggle (with a tooltip "system-managed"). Forces users to think about closure-pack docs separately.
5. **Tests**: Vitest for the button gating + API call.

**Scope**: M — 1 backend endpoint (if missing), 1 frontend table column, 1 server action, 3-4 tests.

**Cross-cutting**: Lands after E2.3 (Visibility.PORTAL must exist first).

---

## E2.5 — OBS-Day61-PortalDocumentsProjectionPartial: SoA must populate portal_documents projection

**Plain English**: There's a denormalised "portal documents" projection table that powers the portal's document list. When a Statement of Account is generated, the SoA gets persisted in the firm's documents table but never flows into the portal projection. Today portal still works (because `PortalQueryService` reads from the firm table directly as a fallback), but the projection drifts and will bite a future query that relies on it.

**Root cause**: `StatementService.generate` (after PR #1137) creates a `Document` row but does not publish a `DocumentGeneratedEvent`. The projector that maintains `portal.portal_documents` listens for that event and would normally insert/upsert the projection row.

**Fix approach**:
1. After successful document persistence in `StatementService.generate`, publish `DocumentGeneratedEvent.of(document, scope=PROJECT, visibility=PORTAL)` (visibility per E2.3 outcome).
2. Verify the projector listener (`PortalEventHandler.onDocumentGenerated` or `PortalDocumentProjector.handle`) writes to `portal_documents` correctly for project-scoped documents.
3. Backfill: a one-time data-correction script `tasks/backfill-portal-documents-from-soa.sql` for any existing SoA rows in dev/QA tenants.
4. Tests: integration test that generates a SoA → asserts a row in `portal.portal_documents` matching the document_id.

**Scope**: S — 1 event publish line + 1 backfill script + 1 integration test. Backend restart required.

**Cross-cutting**: Becomes redundant if E2.6 (refactor SoA to reuse `GeneratedDocumentService`) lands, since that path already publishes the event. Build E2.5 first (small, safe); revisit during E2.6 to delete duplicated event publication.

---

## E2.6 — L-74-followup-2: Refactor `StatementService.generate` to reuse `GeneratedDocumentService`

**Plain English**: Today the SoA generator hand-rolls document persistence and event publishing because the shared `GeneratedDocumentService` was designed for entity-bound contexts (one customer, one matter), not period-bound contexts (date range + matter). The duplication is small but repeated bug surface. Generalise the abstraction so SoA can use the shared service.

**Root cause**: `GeneratedDocumentService.generateDocument` accepts `(entityId, memberId)` and resolves a `TemplateContextBuilder`. SoA needs `(entityId, periodStart, periodEnd, memberId)` so its context builder isn't a `TemplateContextBuilder`. `StatementService.generate` therefore duplicates ~100 lines of persistence + event logic.

**Fix approach**:
1. Define `PeriodContextBuilder extends TemplateContextBuilder` with `Map<String,Object> buildPeriodContext(UUID entityId, LocalDate start, LocalDate end, UUID memberId)`.
2. Have `StatementOfAccountContextBuilder` implement `PeriodContextBuilder` (additive — its existing `buildContext` can delegate to `buildPeriodContext` with current month as default).
3. Update `GeneratedDocumentService.generateDocument` overload that accepts `(entityId, slug, periodStart, periodEnd, memberId)` and dispatches via `instanceof PeriodContextBuilder`.
4. Refactor `StatementService.generate` to call the new overload and delete the inline persistence/event code.
5. E2.5's event publish is removed (now covered by `GeneratedDocumentService`'s normal path).
6. Tests: keep all existing `StatementServicePortalDocumentTest` cases green; add one round-trip test via the new overload.

**Scope**: M — 1 new interface, 1 builder change, 1 service overload, 1 refactor (deletes ~100 LOC), test adjustments.

**Cross-cutting**: Lands after E2.3, E2.4, E2.5 are stable. Reduces future maintenance burden; not urgent for cycle exit.

---

# Epic E3 — Portal terminology + UX seam

## E3.1 — GAP-L-65: Portal + email terminology overrides for legal-za "Fee Note"

**Plain English**: A legal firm sees "Fee Note" everywhere on the firm side — page headings, buttons, breadcrumbs. The same firm's client sees "Invoice" on the portal, and the email subject reads "Invoice INV-0001" instead of "Fee Note INV-0001". The terminology override system works on firm-side but was never extended to portal or email templates. Users see two different brand vocabularies for the same record.

**Root cause**:
- **Firm**: `frontend/app/(app)/org/[slug]/layout.tsx:16,131` wraps the org chrome in `<TerminologyProvider>` fed by `getOrgSettings()` at line 51, with the `terminologyNamespace` field at line 78. Firm components consume `useTerminology()`.
- **Portal**: `portal/app/(authenticated)/layout.tsx` is a client component, has no equivalent fetch, and never wraps children in any terminology provider. Pages like `portal/app/(authenticated)/invoices/page.tsx:60` hardcode `"Invoices"`.
- **Email**: `backend/src/main/resources/templates/email/invoice-delivery.html:2` hardcodes `Invoice from {{org.name}}` in the subject and body. `InvoiceEmailService` builds the model with no terminology key.

**Fix approach**:
1. **Portal layout RSC conversion**: convert `portal/app/(authenticated)/layout.tsx` to a server component (drop `"use client"` if currently set), fetch the portal contact's org's settings (or a slim "public terminology" endpoint exposing only the namespace), pass `terminologyNamespace` into a new `<PortalTerminologyProvider>`.
2. **Reusable provider**: extract `TerminologyProvider` + `useTerminology` from firm into a shared package (or copy into portal — verify whether `frontend/lib/terminology/` is reachable from portal). Mirror exactly. Fall back to "Invoice"/"invoices" if namespace fetch fails.
3. **Portal page sweep**: replace hardcoded "Invoice"/"Invoices"/"Back to invoices" in `portal/app/(authenticated)/invoices/page.tsx`, `[id]/page.tsx`, navigation, and the "Contact firm to arrange payment" fallback string. Estimated 8-12 occurrences.
4. **New backend endpoint**: `GET /api/portal/terminology` returning `{namespace: "legal-za", terminology: {invoice: "Fee Note", ...}}` for the current portal contact's org. Cached server-side (terminology rarely changes).
5. **Email templates**: add Thymeleaf model variable `${terminology.invoice}` (default "Invoice"). Update `InvoiceEmailService` (and any acceptance/closure email service) to inject org settings + terminology map into the template model. Update subject-line builders to use the variable.
6. **Tests**: portal Vitest for provider hydration; backend test for `/api/portal/terminology` per-tenant resolution; email-rendering test assertion that subject reads "Fee Note INV-0001 ..." for legal-za tenant.

**Scope**: M — 1 portal layout change, 1 new portal provider, ~10 portal page edits, 1 new backend endpoint, 2-3 email-service edits, 3-4 email templates. Backend restart for new endpoint; portal HMR.

**Cross-cutting**: Email work overlaps with E5 (closure-pack notifications) — design email-template terminology variables together so we don't double-touch the same templates.

---

## E3.2 — GAP-L-66: Mock payment gateway URL must use backend host

**Plain English**: When the portal "Pay Now" link is generated for a fake payment in dev/test, it points to `localhost:3000/portal/dev/mock-payment` (the Next.js frontend host) but the actual mock-checkout page is served by the backend at `localhost:8080/portal/dev/mock-payment`. The frontend returns 404 on direct click; the QA tester has to manually rewrite the URL with port 8080.

**Root cause**: `MockPaymentGateway.java:55` reads `@Value("${docteams.payment.mock.checkout-base-url:http://localhost:8080}")`. The default is correct, but `application*.yml` likely overrides it (or relies on `${docteams.app.base-url}` which points to frontend). The Spring `MockPaymentController` is mapped at `/portal/dev/mock-payment` on backend port 8080.

**Fix approach** (chosen: explicit config — option (a)):
1. Set `docteams.payment.mock.checkout-base-url: http://localhost:8080` in `backend/src/main/resources/application-local.yml` and `application-dev.yml` and `application-keycloak.yml`. Override anywhere else this key is set incorrectly.
2. Verify in `application.yml` that no incorrect default is shipped to prod (mock controller is `@Profile({local,dev,keycloak})` so prod is safe regardless).
3. Test: the existing `MockPaymentGatewayTest` should already cover the URL build — assert the host equals `:8080` for the dev profile.

**Scope**: S — 3 yml additions, 0 code, 0 migrations.

**Alternative fix considered**: Next.js rewrite from `/portal/dev/mock-payment` → backend. Rejected — adds a frontend-side knowledge of dev-only routes, leaks dev surface into prod build paths, and the simple yml override does the job cleanly.

---

## E3.3 — GAP-L-68: Portal trust-movements endpoint

**Plain English**: The portal home page has a "Last trust movement" tile that calls a backend endpoint that doesn't exist. The tile silently degrades to "No recent activity" so the user never knows it failed. The endpoint needs to be built — return the most recent N trust transactions across all of the contact's matters.

**Root cause**: `portal/app/(authenticated)/home/page.tsx:239` calls `GET /portal/trust/movements?limit=1`. `PortalTrustController` exposes only `/summary`, `/matters/{id}/transactions`, and `/matters/{id}/statement-documents`.

**Fix approach**:
1. New controller method on `PortalTrustController`:
   ```java
   @GetMapping("/movements")
   public ResponseEntity<List<PortalTrustMovementResponse>> listRecentMovements(
       @RequestParam(defaultValue = "10") int limit) {
     UUID customerId = RequestScopes.requireCustomerId();
     return ResponseEntity.ok(portalTrustLedgerService.listRecentMovements(customerId, limit));
   }
   ```
2. New service method `PortalTrustLedgerService.listRecentMovements(UUID customerId, int limit)` — query all `trust_transactions` for matters belonging to the customer, ORDER BY `recorded_at DESC` LIMIT N, project to `PortalTrustMovementResponse(id, type, amount, currency, recorded_at, matter_name, description)`.
3. Tenant guard: `RequestScopes.requireCustomerId()` ensures the contact only sees their own trust movements.
4. Tests: `PortalTrustControllerTest` adds a movements case; `PortalTrustLedgerServiceTest` covers the limit + ordering.

**Scope**: S — 1 controller method, 1 service method, 1 DTO, 2 tests. Backend restart required.

---

## E3.4 — L-73-followup: Portal CLOSED badge variant + Past tab

**Plain English**: After the L-73 backend fix, the portal now correctly knows when a matter is closed — but the "CLOSED" status renders as plain text with no styling, and the portal's matter list has no way to separate Active matters from past ones. Clients see a flat list with no visual distinction.

**Root cause**:
- `portal/components/status-badge.tsx:4-11` defines `PROJECT_STATUS_COLORS` with ACTIVE / COMPLETED / ON_HOLD / CANCELLED only — no CLOSED. Line 43 falls back to an empty class for unknown statuses.
- `portal/app/(authenticated)/projects/page.tsx:90-96` renders projects as a flat `.map()` with no grouping.

**Fix approach**:
1. Add `CLOSED: "bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-400"` to `PROJECT_STATUS_COLORS`.
2. In `projects/page.tsx`: wrap the project list in a Shadcn `<Tabs>` with three tabs — All (default), Active (`status === 'ACTIVE'`), Past (`status IN ('CLOSED', 'COMPLETED', 'CANCELLED')`). Filter the list by selected tab.
3. Tests: portal Vitest snapshot covering the badge variant and Past tab grouping with 1 closed + 2 active matters.

**Scope**: S — 1 badge color, 1 page tab/filter, 2 tests. Portal HMR.

---

## E3.5 — MINOR-Copy-Projects: Dashboard recent-activity copy uses terminology override

**Plain English**: The firm dashboard's empty-state for the Recent Activity widget says "Activity will appear as your team works on **projects**." For a legal firm, this should say "matters." A single hardcoded English string that needs to flow through the terminology system.

**Root cause**: `frontend/components/dashboard/recent-activity-widget.tsx:75` hardcodes the literal `"Activity will appear as your team works on projects."`. The component is firm-side, so `useTerminology()` is in scope.

**Fix approach**:
1. Add `"use client"` if missing, import `useTerminology()`.
2. Replace the hardcoded string with `` `Activity will appear as your team works on ${t("projects")}.` `` (terminology-map already maps "projects" → "matters" for legal-za).
3. Test: snapshot the widget under both legal-za and a generic terminology namespace.

**Scope**: S — 1 component change, 1 test.

---

# Epic E4 — Audit + activity surfaces (Phase 69 minimum)

## E4.1 — GAP-L-75a: Stub firm-side audit-log page

**Plain English**: The product roadmap has a full audit-log page planned in Phase 69 (filters, exports, presets). For now, a working **stub** unblocks the verify cycle and gives platform admins a way to inspect events that already exist in the database. The stub renders a paginated table calling the existing backend endpoint with no filtering UI yet.

**Root cause**: No frontend route at `frontend/app/(app)/org/[slug]/settings/audit-log/`. Backend `AuditEventController` already exposes `GET /api/audit-events` (gated by `@RequiresCapability("TEAM_OVERSIGHT")`).

**Fix approach** (stub only — full Phase 69 scope deferred):
1. New page `frontend/app/(app)/org/[slug]/settings/audit-log/page.tsx`. Fetch `GET /api/audit-events?page=0&size=50` server-side. Render a table with columns: Occurred At | Actor Type | Actor (name resolved) | Event Type | Entity Type | Entity ID | Details (collapsed JSON).
2. Pagination via search params (`?page=N`).
3. Capability gate via existing org-permissions middleware — render "Not authorised" if user lacks `TEAM_OVERSIGHT`.
4. Add navigation entry under Settings.
5. Test: page renders 50 rows, pagination works, gate blocks unauthorised user.

**Scope**: S — 1 page component, 1 nav entry, 2-3 tests. Frontend HMR.

**Cross-cutting**: Phase 69 will replace this with a richer page (filters, export, facets). This stub stays as the foundation; do not over-engineer.

---

## E4.2 — GAP-L-75b: Matter Activity tab actor filter (lean, client-side)

**Plain English**: The matter Activity tab has filters for event type (Tasks / Documents / Comments / etc.) but not for actor (who did the thing). Phase 69 will eventually ship a backend facet endpoint that enumerates actors. Until then, build a lean client-side filter that extracts distinct actors from the events already loaded on the page.

**Root cause**: Activity tab component (likely `frontend/app/(app)/org/[slug]/matters/[id]/activity/` or `frontend/components/activity/`) renders filter checkboxes for type only. No actor selector.

**Fix approach** (no Phase 69 dependency):
1. Add a Shadcn `<Select>` "Filter by actor" above the events list.
2. Compute options on the client: `[...new Set(events.map(e => e.actorId))].map(id => ({id, name: resolveActorName(id, events)}))` where `resolveActorName` looks up name from any event's `actorName` field.
3. Apply filter: when selected, hide events not matching the chosen actor.
4. Re-extract on every page reload (so changing date range shows different actors).
5. Annotate: when Phase 69 ships the facet endpoint, swap client-side extraction for `GET /api/audit-events/facets/actors?from=&to=`.
6. Tests: Vitest covering the dropdown population, filter logic, and re-extraction on page change.

**Scope**: S — 1 component change, 1-2 tests.

---

## E4.3 — OBS-NoPortalActivityTrail: Portal `/activity` page

**Plain English**: The portal has no activity / timeline page. Clients can't see a list of "things I did" or "things the firm did on my matter". After the L-75c PORTAL_CONTACT audit emission landed (PR #1139), the data is in the backend but never surfaced to the client. Build a portal `/activity` page that shows the contact's own actions plus firm actions on the contact's matters.

**Root cause**: No `portal/app/(authenticated)/activity/` route. No portal-side audit-events query endpoint.

**Fix approach**:
1. **Backend**: new portal endpoint `GET /portal/activity?page=N&size=50` on a new `PortalActivityController` (or extend existing portal controller). Query `audit_events` where `(actorType='PORTAL_CONTACT' AND actorId=current_contact_id) OR (details->>'project_id' IN (SELECT id FROM projects WHERE customer_id=current_customer_id))`. Order by `occurred_at DESC`. Project to `PortalActivityEventResponse` with safe fields (no internal IDs unless needed).
2. **Service**: `PortalActivityService.listActivityForContact(customerId, pageable)`. Tenant + contact guard via `RequestScopes`.
3. **Frontend page** `portal/app/(authenticated)/activity/page.tsx`. Two tabs: "Your actions" (filter `actorType=PORTAL_CONTACT`), "Firm actions on your matter" (filter `actorType=USER`). Card/list rendering.
4. **Navigation**: add "Activity" link in portal sidebar.
5. **Tests**: backend service + controller test for tenant-isolation; portal Vitest for tabs + rendering.

**Scope**: M — 1 backend controller + service, 1 portal page, 1 nav entry, 4-5 tests. Backend restart + portal HMR.

**Cross-cutting**: Builds on L-75c (PORTAL_CONTACT audit emission). E1.1 (invoice payment reversal) should publish an audit event so the activity feed reflects the reversal — coordinate event-type names.

---

# Epic E5 — Closure-pack notifications + digest manual trigger

## E5.1 — GAP-L-72: Per-event portal-contact email on closure + SoA generation

**Plain English**: When the firm closes a matter or generates a Statement of Account for a client, the client should get an email immediately ("your matter is closed; the closure pack is in your portal" / "your statement of account is ready"). Today the client only learns about it via the weekly digest or by logging in. Build a single notification handler that reacts to any new portal-visible document and emails the contact, with throttle + dedup so a closure-pack with two simultaneous documents only sends one email.

**Root cause**: `MatterClosureService.confirmClose` and `StatementService.generate` (after PRs #1138/#1137) create documents with `visibility=SHARED` (or `PORTAL` after E2.3) but no listener emails the client. `PortalDigestScheduler` is the only path, and it's weekly cron-driven.

**Fix approach** (Option B — generic listener with dedup):
1. New handler `PortalDocumentNotificationHandler` listening for `DocumentGeneratedEvent` (already published by `GeneratedDocumentService`, and to be published by `StatementService` per E2.5).
2. Filter: only handle events where `visibility IN (SHARED, PORTAL)` AND `scope=PROJECT` AND template type is in a per-doc-type allowlist (`matter-closure-letter`, `statement-of-account`, future: any closure-pack template). Allowlist is configurable via `org_settings.portal_notification_doc_types` (default the two above).
3. Dedup: short-window (5 min) per-customer-per-matter throttle in Redis or a simple in-memory `Caffeine` cache. If a closure batch produces N documents, the first triggers the email; the next N-1 are coalesced into a "with attachments" line.
4. Email template `portal-document-ready.html` with terminology variables (per E3.1).
5. Tests: integration tests covering single-document trigger, multi-document coalesce, doc-type allowlist enforcement, terminology rendering.

**Scope**: M — 1 new handler class, 1 new email template, 1 `org_settings` column + Flyway migration (`V114__add_portal_notification_doc_types.sql`), 4-6 integration tests. Backend restart.

**Cross-cutting**: Lands after E2.3 (Visibility.PORTAL) + E2.5 (event publication on SoA) + E3.1 (terminology). Templates touched here overlap with E3.1.

---

## E5.2 — OBS-Day75-NoManualDigestTrigger: Admin endpoint to fire weekly digest on demand

**Plain English**: The portal weekly digest runs Monday 08:00 by cron. There's no way for a platform admin or QA to trigger it on demand for testing or recovery. Add an internal admin endpoint that fires the existing scheduler immediately.

**Root cause**: `PortalDigestScheduler.runWeeklyDigest()` is a `@Scheduled` method on a Spring bean, callable in code but not exposed via REST.

**Fix approach**:
1. New controller method on `AdminTasksController` (or create `PortalAdminController`):
   ```java
   @PostMapping("/internal/portal/digest/run-weekly")
   @ApiKeyRequired   // matches other /internal/* endpoints
   public ResponseEntity<Map<String,String>> runDigestManually() {
     portalDigestScheduler.runWeeklyDigest();
     return ResponseEntity.accepted().body(Map.of("status", "queued"));
   }
   ```
2. Optional: a dev-portal page at `/portal/dev/run-digest` (`@Profile({local,dev})`) with a button + last-run audit log for QA convenience.
3. Tests: controller test for the API key gate + queuing semantics.

**Scope**: S — 1 controller method (and optional dev page), 1-2 tests. Backend restart.

---

# Epic E6 — SoA template + retention card UX

## E6.1 — OBS-Day60-SoA-Fees/Trust-Empty: SoA template loops render empty

**Plain English**: After the L-71 fix, the SoA generates without crashing — but the fee-line and trust-ledger sections render empty even when there's real data. The DTO→Map conversion landed cleanly; the gap is between the converted map keys and what the SoA template's loop syntax expects to iterate over.

**Root cause** (hypothesis pending template audit): `StatementOfAccountContextBuilder.build` populates `context.put("fees", Map.of("entries", toMapList(agg.feeLines()), ...))` and `context.put("trust", Map.of("deposits", toMapList(agg.trust().deposits), ...))`. The template likely uses Tiptap variable syntax that doesn't match the nested key path (e.g., template loops over `{{disbursements}}` not `{{fees.entries}}`, or expects a top-level `feeLines` key, or the field names inside each map don't match the template's row references).

**Fix approach**:
1. **Audit the template**: locate the SoA Tiptap template JSON (search for `statement-of-account.json` or grep for `disbursements` literal in `backend/src/main/resources/templates/`). Read the loop syntax verbatim.
2. **Compare keys**: cross-check the template's loop variables against what `StatementOfAccountContextBuilder` puts in the context map. Identify the mismatch.
3. **Fix at the model level** (preferred): adjust `StatementOfAccountContextBuilder` keys to match what the template expects. Less risk than editing the template (which may be live in tenant data).
4. **Add a render-test**: extend `StatementOfAccountContextBuilderTest` (post-L-71) with a roundtrip rendering case that asserts non-empty rows in each section for a populated matter.
5. **Visual verify**: regenerate SoA on the existing closed Dlamini matter from cycle 1 — fee + trust sections should populate.

**Scope**: S — diagnosis + 1 builder change OR 1 template change. 1-2 test additions. Backend restart.

---

## E6.2 — OBS-Day60-RetentionShape: Per-matter retention card UI

**Plain English**: When a matter closes, the system stamps a "retention clock started" timestamp and the matter will be auto-deleted after the org's retention period (e.g., 5 years). Today, the UI doesn't show the per-matter retention end date — the user has to look up the global retention policy and do mental math. Add a retention card on the matter Overview tab (only visible for closed matters).

**Root cause**: `Project` has `retentionClockStartedAt` (populated on close per ADR-249); `org_settings` has `legalMatterRetentionYears`. No frontend component renders the calculated end date.

**Fix approach**:
1. **Backend**: extend the matter detail response to include `retentionEndsOn` (computed: `retentionClockStartedAt + org_settings.legalMatterRetentionYears`).
2. **Frontend `RetentionCard.tsx`**: render only when `status=CLOSED && retentionClockStartedAt != null`. Card content:
   - "This closed matter will be permanently deleted on **[date]** ([X days remaining])."
   - Link to `/settings/data-protection` for org-wide policy details.
3. Add to matter Overview tab below the existing closure-letter card.
4. Tests: snapshot covering the 5-year-out date for a Day 60 closure.

**Scope**: S — 1 backend response field, 1 frontend component, 1-2 tests.

---

# Epic E7 — Real PayFast sandbox integration

## E7.1 — L-64-followup: Real PayFast sandbox

**Plain English**: We have a working in-memory mock for PayFast that exercises every code path except real cryptographic signature verification against PayFast's servers. Before going live, we need to wire real sandbox credentials, expose the webhook endpoint to the public internet via a tunnel, and run an integration test that round-trips a payment through PayFast's sandbox.

**Root cause**: `PayFastPaymentGateway` is implementation-complete (signatures, IP validation, ITN POST-back) but is never invoked because no `org_integrations` row points at slug=`payfast` in dev/test. No sandbox merchant credentials configured. No tunnel set up for ITN callbacks.

**Fix approach**:
1. **Product/infra decision** (PRODUCT-DECISION-FLAG):
   - Acquire PayFast sandbox merchant credentials (`merchant_id`, `merchant_key`, `passphrase`).
   - Choose tunnel: ngrok / Cloudflare Tunnel / a permanent staging URL.
   - Decide prod posture: real PayFast in prod from day 1, or stage rollout.
2. **Code**:
   - Wire credentials into `SecretStore` for dev/staging profiles: `payment.payfast.merchant_id`, etc.
   - Add `application-staging.yml`: `docteams.payfast.sandbox: true`, `docteams.payfast.notify_url: https://<tunnel>/api/webhooks/payment/payfast`.
   - Auto-seed `org_integrations(domain=PAYMENT, slug=payfast, enabled=true)` for the demo tenant on staging profile (mirror `MockPaymentIntegrationSeeder`).
3. **Tests**:
   - New `PayFastSandboxIntegrationTest` (network-marked — only runs when `PAYFAST_SANDBOX=true` env present): generate session, POST to `https://sandbox.payfast.co.za/eng/process`, simulate Buyer Approval, await ITN, assert webhook reconciliation flips invoice to PAID.
   - Manual smoke checklist for first deploy.
4. **Mock stays**: `MockPaymentGateway` remains the dev-default. Real PayFast only when explicitly enabled per tenant.

**Scope**: M — credentials + tunnel infra (external), 1-2 yml additions, 1 seeder per profile, 1 network-marked integration test, 1 smoke checklist.

**Product-decision flag**: **YES**. Provider account, tunnel ownership, prod cutover plan.

---

# Epic E8 — KYC adapter wiring

## E8.1 — L-30: KYC adapter integration registry

**Plain English**: The system has scaffolding for KYC providers (verify a person's identity for FICA compliance) but no provider is wired up — the cycle's scenarios 2.8–2.10 are gated on this. Build the same `IntegrationRegistry`-style wiring used for payment gateways: a `IDENTITY_VERIFICATION` integration domain, a NoOp default, a Mock for dev/test, and adapters ready for real providers (Smile ID / OnFido / Truecaller / VerifyNow / CheckID — choose one).

**Root cause**: `KycVerificationPort` interface exists with `NoOpKycAdapter`, `CheckIdKycAdapter`, `VerifyNowKycAdapter`. None are wired to `IntegrationRegistry` (which only knows about `IntegrationDomain.PAYMENT`). No provider resolution at runtime; service falls through to no-op or hard-coded provider.

**Fix approach**:
1. **Product decision** (PRODUCT-DECISION-FLAG): which real provider to integrate first, and whether dev/staging uses Mock or NoOp by default.
2. **Code**:
   - Add `IntegrationDomain.IDENTITY_VERIFICATION` enum value.
   - Annotate adapters: `@IntegrationAdapter(domain=IDENTITY_VERIFICATION, slug="...")` for NoOp, mock, and the chosen real adapter.
   - Build `MockKycGateway` mirroring `MockPaymentGateway` pattern (slug=`mock`, `@Profile({local,dev,keycloak,test})`, in-memory session store, dev `/dev/mock-kyc` page that simulates Verified / Failed outcomes).
   - Update `KycVerificationService` to resolve provider via `IntegrationRegistry.resolve(IDENTITY_VERIFICATION, KycVerificationPort.class)` (mirror payment).
   - Auto-seed `org_integrations(domain=IDENTITY_VERIFICATION, slug=mock, enabled=true)` for legal-za tenants in dev/keycloak profiles via `MockKycIntegrationSeeder` hooked into `PackReconciliationRunner`.
3. **Tests**: adapter resolution test, mock round-trip test, NoOp fallback test.

**Scope**: M — 1 enum value, 4-5 adapter annotations, 1 new mock gateway, 1 service refactor, 1 seeder, 1 dev page, 6-8 tests.

**Product-decision flag**: **YES**. Real provider choice + sandbox credentials.

---

# Epic E9 — Vertical content packs (RAF + bulk billing + court-dates union)

## E9.1 — L-36: RAF-specific matter template

**Plain English**: Personal-injury law firms in SA spend a huge chunk of time on Road Accident Fund claims. RAF claims have unique procedures (RAF claim submission, medical-tariff escalation, prescription rules). Today firms use a generic LITIGATION template. Build an RAF-specific template with the right tasks, deadlines, and field groups so a personal-injury firm doesn't have to retrofit every matter.

**Root cause**: `legal-za.json` matter template pack ships only `Litigation (Personal Injury / General)`. No RAF specialisation.

**Fix approach**:
1. **Pack JSON**: add new template object `legal-za-matter-template-raf` with `matterType="LITIGATION"` and `workType="RAF"` (use the existing work-type predicate from the L-37 fix in PR #1132 to scope the template). Tasks: initial RAF claim submission, insurer correspondence, medical reports, RAF tariff schedule, settlement negotiation, prescription monitoring (3-year SA rule).
2. Auto-attach the RAF-specific field group already authored by E12 (beneficial-owners) if the matter binds to a TRUST/COMPANY claimant.
3. Pack reconciliation runner picks it up on next install for legal-za tenants.
4. Tests: instantiate RAF template, assert task count + field-group attachment per work-type predicate.

**Scope**: S — 1 pack JSON object, 0 code, 1-2 tests.

**No product flag** — RAF specialty is well-defined in SA legal practice.

---

## E9.2 — L-61-followup: Bulk Billing Runs default ON for legal-za

**Plain English**: Most legal-za firms invoice clients in monthly batches rather than one-off. Bulk billing runs is built but not enabled by default for the legal-za vertical — firms have to flip a feature flag manually. Ship it ON by default.

**Root cause**: `legal-za.json` `enabledModules` array does not include `"billing_runs"` (or equivalent). Existing tenants have the feature unflipped.

**Fix approach**:
1. Add `"billing_runs"` to `enabledModules` in `legal-za.json` vertical profile (and `accounting-za.json` if applicable).
2. Tenant Flyway migration `V115__enable_bulk_billing_for_legal_za.sql`: `UPDATE org_settings SET enabled_modules = ... WHERE vertical_profile = 'legal-za' AND NOT (enabled_modules @> '["billing_runs"]'::jsonb);`
3. Verify `PackReconciliationRunner` picks up the change on next reconciliation.
4. Tests: assert `enabled_modules` contains `billing_runs` after pack install for a fresh legal-za tenant.

**Scope**: S — 1 JSON addition, 1 migration, 1-2 tests.

---

## E9.3 — L-58: Court dates union into matter Overview "Upcoming Deadlines" tile

**Plain English**: A legal matter has two sources of deadlines: court dates (set by the judge) and regulatory deadlines (set by the firm — like filing deadlines or compliance dates). The matter Overview's "Upcoming Deadlines" tile should show both, sorted by date. Today court dates are persisted but not surfaced; the tile only ever shows regulatory deadlines (which legal-za doesn't enable).

**Root cause**: `UpcomingDeadlinesTile` (or equivalent) on the matter Overview tab queries `regulatory_deadlines` only. `court_dates` data exists but is not joined in.

**Fix approach** (chosen: backend aggregator, single REST call):
1. **Backend**: new endpoint `GET /api/projects/{id}/upcoming-deadlines` returning a union of `court_dates` (mapped to `{type: "COURT", date, description, status}`) + `regulatory_deadlines` (mapped to `{type: "REGULATORY", ...}`). Order by date ASC. Filter to future-or-today.
2. **Frontend**: replace the existing tile's two fetches (or the single regulatory fetch) with one call to the new endpoint. Render a flat sorted list with a small badge to distinguish court vs regulatory.
3. Tests: backend service test for union ordering; frontend snapshot for mixed-type rendering.

**Scope**: S — 1 backend endpoint + service, 1 frontend tile change, 3 tests. Backend restart.

---

# Epic E10 — Multi-role portal-contact CRUD

## E10.1 — L-40: Multi-role portal-contact CRUD

**Plain English**: Today each customer has effectively one portal contact. Real clients are organisations with multiple people who need different access levels — the CFO who approves invoices, the bookkeeper who uploads documents, the legal counsel who reviews everything. Add CRUD for multiple portal contacts per customer with three roles: PRIMARY (signs/approves), ADMIN (uploads/reviews), GENERAL (view-only).

**Root cause**: `PortalContact` entity has a `ContactRole` enum (PRIMARY, BILLING, GENERAL) but `PortalContactAutoProvisioner` always provisions GENERAL. CRUD endpoints exist but DTOs don't expose role; portal-side capability checks don't differentiate by role.

**Fix approach**:
1. **Role semantics**: rename/repurpose existing roles to PRIMARY (approve, sign), ADMIN (upload, view all), GENERAL (read-only). Sunset BILLING (or fold into PRIMARY). Document in inline JavaDoc.
2. **CRUD**:
   - Backend: extend `PortalContactRequest` DTO with `role` field. Update `PortalContactService.create/update` to honour it. Add capability check `hasCapability("MANAGE_PORTAL_CONTACTS")`.
   - Frontend (firm side): under customer detail, add a "Portal Contacts" section listing existing contacts with role pill + edit/delete actions + "Add Portal Contact" button.
3. **Authorization gates**:
   - In portal-side controllers, check `PortalContactContext.role()`:
     - PRIMARY: full access (accept proposals, pay invoices, sign documents, upload).
     - ADMIN: upload + view + comment, no signing/approval.
     - GENERAL: read-only (view documents, view invoices, no uploads).
   - Wire via a `@RequiresPortalRole` annotation or a `PortalAuthorisation` filter.
4. **Auto-provisioner**: keep the singleton-GENERAL behaviour for first contact; allow firm to upgrade or add more via the new UI.
5. **Tests**: backend authorisation tests per role × per endpoint matrix; frontend Vitest for role-pill display and gated buttons.

**Scope**: M — 1 entity update if role enum changes, ~6 controller/service edits with role checks, 1 firm-side UI page, 1 portal-side authorisation filter, 8-12 tests. Backend restart.

**Cross-cutting**: Lands after E3.1 (terminology) so the new UI labels respect "Portal Contact" / "Client Contact" override per vertical.

---

# Epic E11 — Proposal fee-estimate side-table

## E11.1 — L-49: Proposal fee-estimate side-table + line-item rendering

**Plain English**: When a firm sends a proposal to a client, the client sees a written quote like "fixed fee R 50,000". They don't see how that number broke down. Add a structured fee-estimate side-table on proposals (e.g., "Initial consult: 2 hrs × R1,500 = R3,000", "Pleadings: 8 hrs × R2,000 = R16,000", etc.) so the client can see the underlying scope.

**Root cause**: `Proposal` entity stores aggregated fee fields only (`fixedFeeAmount`, `hourlyRateNote`, etc.). No structured line-item breakdown. Templates render via free-text variables. No frontend side-table component.

**Fix approach**:
1. **Backend data model**:
   - New value object `FeeLineItem(description, quantity, unitRate, amount, taxRate, currency)`.
   - Add `feeLineItems JSONB` column to `proposals` table (Flyway: `V116__proposals_add_fee_line_items.sql`).
   - Update `ProposalService.createDraft` / `updateDraft` to accept + persist line items.
2. **Population**: `ProposalOrchestrationService.buildFromMatterTasks(projectId)` — auto-seed line items from the matter's task list × the assignee's rate-card snapshot. Firm can edit before send.
3. **Variable resolver**: `ProposalVariableResolver` exposes `{{fee_line_items}}` array for template iteration.
4. **Frontend**: `FeeLineItemsTable` component (read-only on portal; editable in firm-side proposal editor). Columns: Description | Qty/Hours | Unit Rate | Subtotal | Tax | Total + grand total row.
5. **Templates**: update legal-za + accounting-za proposal templates to include `{{#each fee_line_items}}` loop section.
6. **Tests**: orchestration auto-seed test, edit/persist round-trip, template render assertion, portal display snapshot.

**Scope**: M — 1 entity change + migration, 1 orchestration method, 1 variable resolver update, 1 frontend component (firm + portal), 2 template updates, 6-8 tests. Backend restart + tenant migration.

---

# Epic E12 — Beneficial-owners structured field group

## E12.1 — L-54: Beneficial-owners structured field group for TRUST/COMPANY

**Plain English**: FICA requires non-natural-person clients (trusts, companies, close corporations) to declare every beneficial owner with their name, ID, and ownership percentage. Today this lives in a free-text field; firms re-key the same data across multiple matters and the system can't validate or query it. Build a structured repeating field group with a row per beneficial owner.

**Root cause**: Existing field-group system supports single-level field packs (one parent record). Repeating sub-records don't exist. FICA pack mentions beneficial owners as a document upload (item 8) — no structured capture.

**Fix approach**:
1. **Field-group schema extension**:
   - Add support for `repeatable: true` field groups with child records (data shape: `JSONB array of objects`).
   - Each child record has its own field schema (name TEXT, id_number TEXT, percentage DECIMAL, relationship Select{owner, trustee, director, member}).
2. **Pack JSON**:
   - Add `beneficial_owners` repeatable field group to `legal-za-customer.json` and `accounting-za-customer.json`.
   - Apply via `applicableEntityTypes: ["TRUST", "COMPANY", "PTY_LTD", "CC"]` predicate (extend the existing predicate model from L-37 fix in PR #1132).
3. **Backend**:
   - `CustomerFieldService` serialises/deserialises repeatable groups.
   - Tenant Flyway migration `V117__customer_repeatable_field_groups.sql` if any schema change needed.
4. **Frontend**:
   - Customer detail "Beneficial Owners" section with table + Add Row button.
   - Validate sum of percentages ≤ 100.
   - Save via existing `PUT /api/customers/{id}` (custom fields are part of the body).
5. **Tests**: field-group serialisation, predicate filtering by entity type, percentage validation.

**Scope**: M — schema layer extension (touches field-pack runtime), 2 pack JSON additions, possibly 1 tenant migration, 1 frontend component, 6-8 tests. Backend restart + frontend HMR + tenant migration.

**No product flag** — FICA requirements drive the schema.

---

# Epic E13 — Test debt — re-enable disabled S3 integration tests

## E13.1 — H1: Re-enable `AcceptanceControllerIntegrationTest` + `PortalAcceptanceControllerIntegrationTest`

**Plain English**: 26 integration tests are disabled because LocalStack (the local AWS S3 mock that runs in Docker) is flaky in CI/agent runs. The codebase already has an `InMemoryStorageService` that works fine for tests. The disabled tests likely just need to use the in-memory mock instead of LocalStack — verify and re-enable.

**Root cause**: Tests at `backend/src/test/java/io/b2mash/b2b/b2bstrawman/acceptance/PortalAcceptanceControllerIntegrationTest.java:43` + `AcceptanceControllerIntegrationTest.java:47` are `@Disabled` with reason "Transient S3/LocalStack connection refusal". Both `@Import(TestcontainersConfiguration.class)` which already binds `InMemoryStorageService` as `@Primary`, so LocalStack should not be reached at runtime.

**Fix approach** (chosen: Option A — likely just a re-enable):
1. **Verify**: read both test files. Check that `@Import(TestcontainersConfiguration.class)` is present. If yes, the LocalStack reach-through must be from a static init or non-`@Primary` overlap — investigate.
2. **Re-enable**: remove `@Disabled` annotations.
3. **Run**: `./mvnw test -Dtest='*AcceptanceControllerIntegrationTest,*PortalAcceptanceControllerIntegrationTest'`. Inspect failures.
4. **If still flaky**: identify which call hits real S3. Bind `InMemoryStorageService` as `@Primary` for these specific tests. Add an archunit rule to forbid future `LocalStackContainer` usage in `backend/src/test/`.
5. **Document**: brief note in `backend/CLAUDE.md` testing section about the InMemoryStorageService convention.

**Scope**: S — diagnose + enable (likely 0 code changes other than removing 2 `@Disabled`). Backend test run.

---

# Epic E14 — Doc-drift redirect

## E14.1 — MINOR-Doc-Drift-26: `/settings/team` → `/team` redirect

**Plain English**: The QA scenario doc references the URL `/settings/team` to find the team management page, but the actual route is `/org/{slug}/team`. Bookmarks and demo scripts that hit the old URL get a 404. Add a permanent redirect.

**Root cause**: Next.js route at `frontend/app/(app)/org/[slug]/team/` is the canonical path. No alias for `/settings/team` exists.

**Fix approach**:
1. Add a redirect in `frontend/middleware.ts` (or `next.config.ts`):
   ```typescript
   { source: "/org/:slug/settings/team", destination: "/org/:slug/team", permanent: true }
   ```
2. Update QA scenario doc to reference the canonical path going forward.
3. Tests: integration test that hitting the legacy path 301s to the new one.

**Scope**: S — 1 line in middleware/config, 1 doc update, 1 test.

---

# Sequenced Build Order

Read top-to-bottom. Items in the same group can run in parallel. Items below an arrow depend on items above.

```
GROUP A — independent foundations (parallel):
   E1.1 (invoice payment reversal cascade)
   E1.2 (trust-deposit nudge email)
   E2.1 (info-request DTO documentFileName)
   E3.2 (mock-payment URL config)
   E3.3 (portal trust-movements endpoint)
   E3.4 (portal CLOSED badge + Past tab)
   E3.5 (dashboard copy terminology)
   E4.1 (audit-log stub page)
   E4.2 (matter Activity actor filter)
   E6.1 (SoA template loop fix)
   E6.2 (per-matter retention card)
   E9.1 (RAF matter template)
   E9.2 (bulk billing default ON)
   E9.3 (court-dates union into Overview)
   E13.1 (re-enable disabled tests)
   E14.1 (settings/team redirect)
        ↓
GROUP B — depend on Group A (parallel within):
   E2.2 (info-request ad-hoc UI)             ← depends E2.1
   E2.3 (Visibility.PORTAL enum)              ← independent backend
   E3.1 (portal terminology + email)          ← independent
   E4.3 (portal /activity page)               ← depends E1.1 (audit events on reversal)
        ↓
GROUP C — depend on Group B:
   E2.4 (firm doc-visibility toggle UI)       ← depends E2.3
   E2.5 (SoA event publication for projection) ← depends E2.3
   E5.1 (closure-pack notifications)          ← depends E2.3 + E2.5 + E3.1
   E10.1 (multi-role portal contact CRUD)     ← depends E3.1 (terminology)
   E11.1 (proposal fee-estimate side-table)   ← independent (could go in Group A but heavy)
   E12.1 (beneficial-owners field group)      ← independent (could go in Group A but heavy)
        ↓
GROUP D — depend on Group C:
   E2.6 (refactor SoA into GeneratedDocumentService)  ← depends E2.3, E2.4, E2.5
   E5.2 (admin digest trigger)                        ← independent (could go anywhere)
        ↓
GROUP E — gated on product/infra decisions (start as soon as decision lands):
   E7.1 (real PayFast sandbox)   ← needs sandbox creds + tunnel
   E8.1 (KYC adapter wiring)     ← needs real provider choice
```

---

# Effort Estimate (rough)

| Epic | Effort | Notes |
|------|--------|-------|
| E1 | M (~6 hr) | Two backend services + listener + tests |
| E2 | L (~16 hr) | Largest — touches DTO, frontend dialog, enum, migration, refactor |
| E3 | M (~10 hr) | Portal layout work + email templates |
| E4 | M (~8 hr) | New portal endpoint + page + audit-log stub + actor filter |
| E5 | M (~6 hr) | Listener + admin endpoint + email template |
| E6 | S (~3 hr) | Template diagnosis + retention card |
| E7 | M (~6 hr) | Plus external infra time (tunnel + sandbox account setup) |
| E8 | M (~8 hr) | Plus product time to choose provider |
| E9 | S (~4 hr) | Pack JSON + 1 endpoint + 1 migration |
| E10 | M (~10 hr) | Authorisation matrix + UI + tests |
| E11 | M (~10 hr) | Entity + migration + auto-seed + frontend component + templates |
| E12 | M (~8 hr) | Field-group runtime extension + pack JSON + UI + validation |
| E13 | S (~2 hr) | Likely just diagnose + enable |
| E14 | S (~30 min) | One-line redirect |
| **Total** | **~110 hr** | Excluding external setup time for E7/E8 |

Two engineers running in parallel through Groups A → B → C → D should complete the in-codebase work in ~3 weeks. E7 and E8 run on their own clock once decisions land.

---

# Open Product Decisions

These items cannot start coding until product confirms:

1. **E7 (real PayFast)**:
   - Which environment gets real PayFast first (staging only? prod immediately?)
   - Tunnel ownership (engineering ngrok account vs Cloudflare Tunnel managed by infra)
   - Tenant opt-in or universal?
2. **E8 (KYC provider)**:
   - Which provider (Smile ID has the strongest SA market presence; OnFido is enterprise-grade global; VerifyNow is local)
   - Real-provider sandbox credentials
   - When to enable for which tenants
3. **E10 (portal-contact roles)**:
   - Confirm role model: `PRIMARY / ADMIN / GENERAL` vs the existing `PRIMARY / BILLING / GENERAL`. Sunset BILLING or fold into PRIMARY?
4. **E11 (fee line items)**:
   - Auto-seed from tasks vs purely manual entry?
   - Editable by client (counter-proposal) or read-only?

Surface these in a single product-review meeting before kicking off Group E and Group C respectively.

---

# Excluded From Scope

- **L-21** — WONT_FIX per product call (already documented in cycle status).
- **OBS-L-27** — Portal PDF iframe cross-origin: infra-team decision (not an engineering bug).

That leaves zero open items in the cycle backlog after this plan executes.
