# Batch D brief: portal correctness (G89-F23 MEDIUM, 633C D2 LOW, 633C D6 LOW)

**Branch:** `fix/g89-d-portal-correctness`, cut from a fresh `origin/main` after Batch A has merged.
**PR title:** `fix(portal): hide internal-draft documents from the activity feed; humanise deadline badges and trust email type (G89-F23, 633C D2/D6)`
**Authorisation:** founder D1/D2 (2026-11-06).
**Gates:**
- backend full verify (orchestrator)
- portal gate
- two bound reviews
- CodeRabbit
- browser-observed S2-64 side (portal "Firm actions") plus a Mailpit trust email on the Keycloak stack

## G89-F23 (MEDIUM): the portal feed announces internal-draft documents

**Observed in 636B.** The portal "Firm actions" list showed "Statement of Account generated" and "Document generated for you" for closure documents that were saved as internal drafts.

**Cause** (verified at `30317a851`):
- `portal/PortalActivityEventTypes.java:44-48` allow-lists `document.generated`, `docx_document.generated` and `statement.generated` by **event type only**.
- `PortalActivityService.listActivity` (around `:40-75`) queries `audit_events` at **read time**, through `AuditEventRepository.findActivityFirmForCustomer` / `findActivityForPortalContact` (around `:265-370`).
- For those three types, `entity_id` is the `generated_documents.id` (`GeneratedDocumentService:349`, `:793`; `StatementService:200`).
- `generated_documents.document_id` links to `documents.visibility`. `Document.Visibility.isPortalVisible` is at `Document.java:261`; SHARED and PORTAL are visible.

**Fix.**
- In the two activity queries, keep a row of these three types only when its generated document's linked `documents` row is portal-visible.
- Exclude rows where `document_id` is null, because the document was never saved to the matter.
- Other event types are unaffected.
- No re-projection is needed. The filter runs at read time, so setting a document to PORTAL makes its event appear on the next read.
- Labels at `PortalActivityEventResponse.java:107-108` are unchanged.

**Tests** (reproduce first, red on main):
- `PortalActivityInternalDraftIntegrationTest`
  - `internalDraftGeneratedDocument_notInPortalActivity`
  - `sharedGeneratedDocument_inPortalActivity`
  - `visibilityChangedToPortal_eventAppears`
  - `statementGenerated_internal_hidden`
  - `unsavedGeneratedDocument_hidden`
- Cover both the firm-for-customer query and the portal-contact query.

## 633C D2 (LOW): portal deadline badges print "UPCOMING"

**Where.**
- `portal/components/deadlines/deadline-list.tsx:92-93`, used at `:299`.
- `portal/components/deadlines/deadline-detail-panel.tsx:26-27`, used at `:152`.
- In both, `formatStatus` only swaps underscores for spaces.

**Fix.** Add one label map in `portal/lib/labels.ts` (it already holds the request labels): Upcoming / Due soon / Overdue / Completed / … Cover every status value the portal deadline type can hold; check the type. Both components use it. Add a vitest for the label map and one rendering assertion.

## 633C D6 (LOW): the trust email says "Type DEPOSIT"

**Where.** `portal/notification/PortalEmailNotificationChannel.java:282,314` passes `event.transactionType()` raw, and `templates/email/portal-trust-activity.html:20` prints it.

**Fix.** Map the type to a client-facing label in the channel: Deposit / Payment / Refund / Fee transfer / Interest credit / Transfer in / Transfer out. Make it a single static map with a fallback to a title-cased value.

**Test.** Extend `PortalEmailNotificationChannelIntegrationTest`, or add a unit test, to assert the rendered email contains "Deposit" and not "DEPOSIT".

## Process

Follow `batch-B-brief.md` "Process":
- `pwd` check before the first write
- commit each red test before its fix
- targeted tests: `portal/**`, `customerbackend/**`, `audit/**`, `document/**`, and every importer of a changed class
- run the portal gate from `portal/`: `pnpm lint && pnpm build && pnpm test && pnpm format:check`
- open the PR; do not run the full verify and do not merge
- report the PR number, head sha, test counts and judgment calls
