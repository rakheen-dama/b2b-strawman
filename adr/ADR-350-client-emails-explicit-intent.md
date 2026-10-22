# ADR-350: Client-Facing Emails Are Sent Only on Explicit Intent; Generated Documents Are INTERNAL Until Shared; the `portalNotificationDocTypes` Allowlist Is Removed

**Status**: Accepted

**Context**:

GAP-L-72 made "generated ⇒ portal-visible ⇒ emailed" an invariant for two artefacts. `StatementService.generate:155-168` always saves the Statement of Account as `Visibility.PORTAL` and publishes `DocumentGeneratedEvent` with `details.visibility = "PORTAL"`; `GenerateStatementRequest` has only `periodStart, periodEnd, templateId`; the dialog's single button is "Preview & Save" (`statement-of-account-dialog.tsx:331`) with no email copy. `MatterClosureService.generateClosureLetterSafely:391-401` births the closure letter PORTAL and `generateSoaSafely` reuses the same generate; the closure dialog's checkbox copy says only "will be attached to this matter" (`matter-closure-dialog.tsx:317-321`). `PortalDocumentNotificationHandler.onDocumentGenerated:117-244` then emails "Document ready" when the template slug is in `OrgSettings.portal.portalNotificationDocTypes` (default exactly those two slugs, `PortalSettings.java:56-73`) — a setting with **no UI anywhere** — and builds the legacy `/projects/{id}` link (`:300`). Result: the walk's "Preview & Save" emailed the client a document the firm had not reviewed (G-C5, G-D14 emails), and the portal matter card shows no status (G-D19, frontend-only: `PortalMatterResponse` already carries `status`). Founder decision D7 chose opt-in, default off, SoA INTERNAL until shared, allowlist removed.

**Options Considered**:

1. **Keep the allowlist and build a settings UI for it; add honest dialog copy ("the client will be emailed immediately").**
   - Pros:
     - No request/entity change.
     - Firms can switch the two slugs off.
   - Cons:
     - Still couples visibility, generation and email in one event.
     - A "preview" SoA remains irrevocably portal-visible and a quiet closure is impossible.
     - A tenant-wide switch cannot express per-action intent ("this SoA yes, that one no"). Explicitly out of scope per the register §6.
2. **Per-action intent: `notifyClient` (default false) and `shareWithClient` on the SoA and closure requests; documents saved INTERNAL unless shared; the handler fires only on explicit intent; allowlist removed (CHOSEN).**
   - Pros:
     - The firm decides at the moment of the act.
     - The dialog states exactly what will be sent.
     - INTERNAL-until-shared gives review-before-share by construction (the handler naturally skips INTERNAL).
     - Removes a silent setting with no UI and one mechanism (allowlist) in favour of the request.
     - A new `POST /api/generated-documents/{id}/share` endpoint wrapping the Document visibility change (no share endpoint exists today) becomes the later send path.
   - Cons:
     - Two request DTOs, two dialogs, handler gate change, and the Phase 68 "client waits a week for the digest" concern returns unless the firm ticks the box — accepted: the box is on the dialog the firm is already using.
3. **Always notify, but copy the firm (`cc` the sender) and show a banner.**
   - Pros:
     - No one is surprised after the fact.
   - Cons:
     - Still sends an unreviewed document to a client.
     - A cc is not consent.
     - POPIA-sensitive artefacts (closure letters with balances) should not leave the firm on a default.

**Decision**: Option 2 — `GenerateStatementRequest` and `ClosureRequest` gain `notifyClient` (null → false) and `shareWithClient` (null → false); the SoA and closure letter are saved `Visibility.INTERNAL` unless `shareWithClient`; `DocumentGeneratedEvent.details` carries `notify_client`; `PortalDocumentNotificationHandler` fires only when the event says `notify_client = true` and the document is portal-visible, keeping the dedup gate; `portalNotificationDocTypes` is removed from the `PortalSettings` API and the handler; the column is left in place and unused; dropping it is deferred to a later cleanup migration; the handler's link becomes `/matters/{id}` and carries org context per [ADR-351](ADR-351-portal-contact-directory.md); the portal matter card renders `status` (required on `PortalMatter`).

**Rationale**:

1. **Intent belongs to the act, not to a tenant setting.** A firm emails a client a statement because an attorney decided to, on that statement; the request is the only object that can carry that decision. The allowlist was an approximation that worked when "Preview & Save" was assumed to be the final act.
2. **INTERNAL-until-shared is the existing visibility model** (`Document.Visibility` INTERNAL/SHARED/PORTAL; the new share endpoint only wraps that visibility change); using it means no new state, and the G-C4 lesson (the firm emailed a document it could not read in the preview) cannot recur.
3. **Removing the setting is cheaper than exposing it.** A settings UI for a list of template slugs is a feature with its own tests and copy, for a decision that is better taken per action; the register §6 keeps it out of scope.
4. **Demo-partner safety:** a design partner will close matters and generate statements during an evaluation; nothing may leave the firm unless someone ticked "Email the client".

**Consequences**:

- Positive:
  - "Preview & Save" sends no email.
  - Ticking "Email the client" sends one with a `/matters/` link that lands after exchange.
  - A Closed matter reads "Closed" on the portal list (G-C5, G-D14 emails, G-D19).
  - The closure dialog can generate a letter and SoA for the file without notifying a deceased estate's family on the same afternoon.
- Negative:
  - `StatementServicePortalDocumentTest` (pins PORTAL visibility) and `PortalDocumentNotificationHandlerIntegrationTest` (pins allowlist behaviour) are rewritten around intent.
  - `MatterClosureServiceIntegrationTest` closure-letter visibility assertions flip to INTERNAL by default.
  - `statement-of-account-dialog.test.tsx` and `matter-closure-dialog.test.tsx` gain the checkbox cases.
  - `matter-card.test.tsx` gains the badge.
  - `OrgSettingsSchemaSnapshotTest` and `BaselineSchemaParityTest` are unaffected: the `portal_notification_doc_types` column stays in place (dropping it is a later cleanup migration).
  - Portal digest timing: a shared-but-not-notified document reaches the client through the weekly digest, as Phase 68 designed.
  - Scenario step 61.2 ("system-auto-shared") is a §5-authorised wording amendment in the docs PR.
- Related:
  - [ADR-351](ADR-351-portal-contact-directory.md) (org-aware links), [ADR-336](ADR-336-engagement-letter-document-plus-acceptance.md) (acceptance emails are already explicit-intent — the model this ADR generalises), [ADR-344](ADR-344-qa-scenario-amendment-authorisation-phase87.md), `tasks/phase88-research/R5-portal.md` §Q6, `tasks/phase88-gap-register.md` D7.
