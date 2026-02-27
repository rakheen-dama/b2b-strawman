# Phase 28 — Document Acceptance (Lightweight E-Signing)

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with a mature document generation pipeline that produces engagement letters, proposals, and service agreements as PDFs stored in S3. The platform has a customer portal with magic-link authentication for external clients, outbound email delivery, and a comprehensive audit trail.

**What exists and what this phase changes:**

Generated documents today are a dead end — the firm generates a PDF, and the workflow stops. To get a client's acceptance on an engagement letter, firms must download the PDF, email it manually, and ask the client to reply with "I accept." There is no tracking, no proof of acceptance, and no audit trail.

This phase adds a **Document Acceptance** workflow: firms send a generated document to a portal contact for acceptance via the existing portal magic-link system. The client views the PDF, types their full name, and clicks "I Accept." The system records the acceptance with a full audit trail (timestamp, IP, user agent) and auto-generates a **Certificate of Acceptance** PDF as tamper-proof evidence — the kind of document firms file alongside the engagement letter for compliance.

**Existing infrastructure this phase builds on:**

- **GeneratedDocument entity** (`template/GeneratedDocument.java`): Tracks generated PDFs — `templateId`, `primaryEntityType`, `primaryEntityId`, `documentId`, `fileName`, `s3Key`, `fileSize`, `generatedBy`, timestamps. The acceptance request links to this entity.
- **PortalContact entity** (`portal/PortalContact.java`): External client contacts with magic-link authentication. Each portal contact belongs to a customer and has `name`, `email`, `role`. The acceptance flow uses existing portal auth — no new auth mechanism needed.
- **Magic link system** (`portal/MagicLinkService.java`, `portal/MagicLinkToken.java`): Generates secure, time-limited tokens for portal access. Tokens are associated with a portal contact and verified on access. The acceptance email includes a magic link that authenticates the contact and routes them to the acceptance page.
- **Portal read-model** (`portal/readmodel/`): A separate read-model schema per tenant that stores denormalized data for portal access. Has `PortalDocument`, `PortalProject`, `PortalInvoice` entities synced via domain events. Acceptance requests will need a corresponding read-model entity.
- **Portal frontend** (`portal/` Next.js app): Customer-facing app at `/portal/` with project list, project detail, invoice list/detail, profile. Uses magic-link auth. The acceptance page will be a new route in this app.
- **Email delivery** (`notification/channel/EmailNotificationChannel.java`, `email/`): Thymeleaf-based email templates, SMTP + SendGrid adapters, delivery logging. The "Document sent for acceptance" email uses this pipeline.
- **PdfRenderingService** (`template/PdfRenderingService.java`): OpenHTMLToPDF rendering pipeline. Reused for generating the Certificate of Acceptance PDF.
- **OrgSettings** (`settings/OrgSettings.java`): Has branding fields (logo, brand_color, footer) used in template/PDF rendering. The certificate uses org branding.
- **Audit infrastructure** (`audit/`): `AuditEvent` entity with JSONB details, `AuditService.record()` API. All acceptance state transitions are audited.
- **S3 / StorageService** (`storage/StorageService.java`): Port-based file storage abstraction (S3 in prod, LocalStack locally). Certificates are stored in S3 alongside the original document.
- **Flyway migrations**: Current latest is V44 (Phase 27). Next available is V45.

## Objective

Add a document acceptance workflow that allows firms to send generated documents to portal contacts for formal acknowledgment. After this phase:

- Firms can send any generated document to a portal contact for acceptance via a "Send for Acceptance" action
- The client receives an email with a magic link that routes directly to the acceptance page
- The acceptance page shows the PDF and an acceptance form: "I, [typed full name], accept this document"
- On acceptance, the system records the typed name, timestamp, IP address, and user agent
- A Certificate of Acceptance PDF is auto-generated with all acceptance metadata and a hash of the original document — stored in S3 as a companion file
- The firm sees acceptance status on generated documents (Pending, Viewed, Accepted, Expired, Revoked) with full audit detail
- Firms can manually re-send the acceptance email (remind) or revoke a pending request
- Acceptance requests have a configurable expiry period (org-level default, per-request override)
- All state transitions are recorded as audit events

## Constraints & Assumptions

- **Portal contacts only.** Acceptance recipients must be existing `PortalContact` entities for the customer associated with the generated document. This leverages the existing magic-link auth system. Sending to arbitrary email addresses is out of scope.
- **Single signer per request.** Each acceptance request targets one portal contact. Multi-signer (e.g., both partners of a client accepting) is out of scope — firms can send separate requests to multiple contacts if needed.
- **No counter-signature.** The firm does not sign after the client accepts. The generation act itself (with `generatedBy` member ID) serves as the firm's implicit approval of the document content.
- **One active request per document-contact pair.** If the firm sends a new acceptance request for the same document to the same contact, the previous request is automatically revoked.
- **Certificate is immutable.** Once generated, the Certificate of Acceptance PDF is never modified. If a request is revoked after acceptance, the certificate remains (the revocation is a separate audit event, not a certificate deletion).
- **Expiry is soft.** An expired request can still be "reminded" (re-sent with a new expiry). The original request entity is updated, not replaced.
- **Magic link routing.** The acceptance email magic link should route the portal contact directly to the acceptance page for that specific document, not to the portal dashboard. The portal app needs a dedicated `/portal/accept/{requestToken}` route.
- **Backward compatible.** Existing generated documents without acceptance requests continue to work as before. The "Send for Acceptance" action is additive.

## Detailed Requirements

### 1. AcceptanceRequest Entity & Repository

**Problem:** There is no way to track that a document has been sent for acceptance or what the outcome was.

**Requirements:**
- Create an `AcceptanceRequest` entity in a new `acceptance/` package:
  - `UUID id`
  - `UUID generatedDocumentId` — references `GeneratedDocument`. Required.
  - `UUID portalContactId` — references `PortalContact` (the recipient). Required.
  - `UUID customerId` — denormalized from the generated document's entity linkage. Required. Enables efficient queries ("all pending acceptances for Customer X").
  - `AcceptanceStatus status` — enum: `PENDING`, `SENT`, `VIEWED`, `ACCEPTED`, `EXPIRED`, `REVOKED`. Default: `PENDING`.
  - `String requestToken` — unique, cryptographically random token (same generation pattern as magic links) embedded in the acceptance URL. Max 255 characters.
  - `Instant sentAt` — when the email was sent (null until sent).
  - `Instant viewedAt` — when the recipient first opened the acceptance page (null until viewed).
  - `Instant acceptedAt` — when the recipient clicked accept (null until accepted).
  - `Instant expiresAt` — when the request expires. Calculated from org-level default + per-request override.
  - `Instant revokedAt` — when the firm revoked the request (null unless revoked).
  - `String acceptorName` — the full name typed by the recipient at acceptance. Max 255 characters. Null until accepted.
  - `String acceptorIpAddress` — IP address captured at acceptance. Max 45 characters (IPv6). Null until accepted.
  - `String acceptorUserAgent` — user agent string captured at acceptance. Max 500 characters. Null until accepted.
  - `String certificateS3Key` — S3 key for the generated Certificate of Acceptance PDF. Null until certificate is generated.
  - `String certificateFileName` — display filename for the certificate (e.g., "Certificate-of-Acceptance-{slug}-{date}.pdf"). Max 255 characters.
  - `UUID sentByMemberId` — the member who initiated the send. Required.
  - `String revokedByMemberId` — the member who revoked, if applicable. Nullable.
  - `int reminderCount` — number of reminder emails sent (default 0).
  - `Instant lastRemindedAt` — when the last reminder was sent. Nullable.
  - `Instant createdAt`, `Instant updatedAt`
- Unique constraint on `(generatedDocumentId, portalContactId, status)` where status is `PENDING` or `SENT` or `VIEWED` — enforces "one active request per document-contact pair." (Accepted/expired/revoked requests don't block new ones.)
- `AcceptanceRequestRepository` — standard JPA repository with:
  - `findByRequestToken(String token)` — for token-based lookup from portal
  - `findByGeneratedDocumentIdOrderByCreatedAtDesc(UUID documentId)` — all requests for a document
  - `findByCustomerIdAndStatusInOrderByCreatedAtDesc(UUID customerId, List<AcceptanceStatus> statuses)` — for customer-level views
  - `findByStatusAndExpiresAtBefore(AcceptanceStatus status, Instant cutoff)` — for expiry processing
  - `findByGeneratedDocumentIdAndPortalContactIdAndStatusIn(UUID documentId, UUID contactId, List<AcceptanceStatus> activeStatuses)` — for duplicate detection

### 2. AcceptanceService & Workflow

**Problem:** No business logic for creating, sending, tracking, or completing acceptance requests.

**Requirements:**
- `AcceptanceService` with:
  - `createAndSend(UUID generatedDocumentId, UUID portalContactId, Integer expiryDays)`:
    - Validates the generated document exists and belongs to a customer
    - Validates the portal contact exists and belongs to that customer
    - If an active request already exists for this document-contact pair, revokes it automatically
    - Creates `AcceptanceRequest` with status `PENDING`, generates `requestToken`
    - Calculates `expiresAt`: uses `expiryDays` if provided, otherwise falls back to org-level default (see section 7)
    - Sends the acceptance email (see section 4) — on successful send, transitions status to `SENT` and sets `sentAt`
    - Publishes audit event
    - Returns the created request
  - `markViewed(String requestToken, String ipAddress)`:
    - Looks up request by token
    - If status is `SENT`, transitions to `VIEWED` and sets `viewedAt`
    - If already `VIEWED` or `ACCEPTED`, no-op (idempotent)
    - If `EXPIRED` or `REVOKED`, returns appropriate error
    - Checks expiry: if `expiresAt` is past, transitions to `EXPIRED` and returns error
  - `accept(String requestToken, AcceptanceSubmission submission)`:
    - `AcceptanceSubmission`: `{ name: String }` — the typed full name
    - Validates request token, checks status is `SENT` or `VIEWED`
    - Checks expiry: if past, transitions to `EXPIRED` and returns error
    - Records: `acceptorName`, `acceptorIpAddress` (from request), `acceptorUserAgent` (from request), `acceptedAt = now()`
    - Transitions status to `ACCEPTED`
    - Triggers certificate generation asynchronously (section 5)
    - Publishes audit event and notification (section 8)
    - Returns the updated request
  - `revoke(UUID requestId)`:
    - Validates request exists and status is `PENDING`, `SENT`, or `VIEWED`
    - Transitions to `REVOKED`, sets `revokedAt` and `revokedByMemberId`
    - Publishes audit event
  - `remind(UUID requestId)`:
    - Validates request exists and status is `SENT` or `VIEWED`
    - Checks expiry: if past, transitions to `EXPIRED` and returns error
    - Re-sends the acceptance email with the same token
    - Increments `reminderCount`, sets `lastRemindedAt`
    - Publishes audit event
  - `getByToken(String token)` — returns request with document metadata (title, file name) and customer info. Used by the portal acceptance page.
  - `getByDocument(UUID generatedDocumentId)` — returns all requests for a document, ordered by creation date desc.
  - `getByCustomer(UUID customerId)` — returns all requests for a customer, optionally filtered by status.
  - `processExpired()` — batch job (called by scheduler or startup): finds all PENDING/SENT/VIEWED requests past `expiresAt` and transitions them to `EXPIRED`. Publishes audit events.

### 3. Acceptance REST API

**Problem:** No endpoints for managing acceptance requests.

**Requirements:**
- `AcceptanceController` at `/api/acceptance-requests`:
  - `POST /api/acceptance-requests` — create and send acceptance request. Body: `{ generatedDocumentId, portalContactId, expiryDays? }`. Returns created request. ADMIN/OWNER only.
  - `GET /api/acceptance-requests?documentId={id}` — list requests for a document. MEMBER+.
  - `GET /api/acceptance-requests?customerId={id}` — list requests for a customer. MEMBER+.
  - `GET /api/acceptance-requests/{id}` — get request detail (includes acceptance metadata if accepted). MEMBER+.
  - `POST /api/acceptance-requests/{id}/remind` — re-send acceptance email. ADMIN/OWNER only.
  - `POST /api/acceptance-requests/{id}/revoke` — revoke a pending request. ADMIN/OWNER only.
  - `GET /api/acceptance-requests/{id}/certificate` — download the Certificate of Acceptance PDF (redirects to S3 presigned URL or streams the file). MEMBER+. Returns 404 if not yet accepted.
- `PortalAcceptanceController` at `/api/portal/acceptance` (portal-facing, authenticated via magic link / portal auth):
  - `GET /api/portal/acceptance/{token}` — get acceptance page data: document title, file name, PDF download URL, request status, org branding. Public (token-authenticated). Also triggers `markViewed`.
  - `GET /api/portal/acceptance/{token}/pdf` — stream/redirect to the document PDF for viewing. Token-authenticated.
  - `POST /api/portal/acceptance/{token}/accept` — submit acceptance. Body: `{ name: String }`. Token-authenticated. Captures IP and user agent from the request.

### 4. Email Delivery

**Problem:** No email template for sending documents for acceptance.

**Requirements:**
- Create email templates (Thymeleaf HTML, same pattern as existing notification emails):
  - **Acceptance request email** (`acceptance-request.html`):
    - Subject: "{org.name} — Document for your acceptance: {document.fileName}"
    - Body: Professional email with org branding, document title, brief explanation ("Please review and accept the attached document"), and a prominent "Review & Accept" CTA button linking to the portal acceptance page via magic link
    - The magic link URL pattern: `{portalBaseUrl}/accept/{requestToken}` — this both authenticates the contact and routes to the acceptance page
  - **Acceptance reminder email** (`acceptance-reminder.html`):
    - Subject: "Reminder: {org.name} — Document awaiting your acceptance"
    - Body: Similar to the request email but with "This is a reminder" framing and the original sent date
  - **Acceptance confirmation email** (`acceptance-confirmation.html`):
    - Sent to the portal contact after they accept
    - Subject: "Confirmed: You have accepted {document.fileName}"
    - Body: Confirmation with acceptance timestamp and a note that they can view the document in the portal
  - **Acceptance notification to firm** (internal notification, not email):
    - When a document is accepted, notify the member who sent the request (in-app notification via existing notification system)
- Use the existing `EmailNotificationChannel` / `EmailService` pipeline. Do not create a parallel email delivery mechanism.

### 5. Certificate of Acceptance Generation

**Problem:** Firms need a downloadable proof document that demonstrates acceptance occurred.

**Requirements:**
- When a document is accepted, generate a **Certificate of Acceptance** PDF:
  - One-page document with org branding (logo, brand color)
  - Title: "Certificate of Acceptance"
  - Content:
    - Document title and file name
    - Organization name (the firm)
    - Recipient name (from portal contact) and email
    - Acceptance statement: "I, {acceptorName}, accept this document."
    - Acceptance timestamp (UTC and local timezone if determinable)
    - IP address
    - User agent
    - SHA-256 hash of the original document PDF (computed from S3 content at acceptance time)
    - Acceptance request ID (for cross-referencing)
  - Footer: "This certificate was automatically generated by {org.name} via DocTeams. It serves as a record of electronic acceptance."
- **Implementation**: Create a Thymeleaf template for the certificate (`certificate-of-acceptance.html` in `classpath:templates/certificates/`). Render via `PdfRenderingService` (same pipeline as document generation). Upload to S3 with a key pattern like `{tenantSchema}/certificates/{requestId}/certificate.pdf`.
- **Timing**: Generate the certificate synchronously during the `accept()` call. The certificate is small (one page) and fast to render — async adds complexity without meaningful benefit.
- Store `certificateS3Key` and `certificateFileName` on the `AcceptanceRequest` entity.

### 6. Portal Read-Model Extension

**Problem:** The portal needs to show pending acceptance requests and the acceptance page data.

**Requirements:**
- Add a `PortalAcceptanceRequest` entity to the portal read-model schema:
  - `UUID id` (same as the main AcceptanceRequest ID)
  - `UUID portalContactId`
  - `UUID generatedDocumentId`
  - `String documentTitle` — denormalized from GeneratedDocument
  - `String documentFileName` — denormalized
  - `String documentS3Key` — for PDF viewing
  - `String status`
  - `String requestToken`
  - `Instant sentAt`
  - `Instant expiresAt`
  - `String orgName` — for display
  - `String orgLogo` — for branding
  - `Instant createdAt`
- Sync via domain events (same pattern as existing portal read-model sync):
  - `AcceptanceRequestSent` → creates `PortalAcceptanceRequest`
  - `AcceptanceRequestAccepted` → updates status
  - `AcceptanceRequestRevoked` → updates status
  - `AcceptanceRequestExpired` → updates status
- The portal acceptance page reads from this read-model, not from the main schema.

### 7. OrgSettings Extension

**Problem:** No org-level configuration for acceptance request defaults.

**Requirements:**
- Add to `OrgSettings`:
  - `Integer acceptanceExpiryDays` — default expiry period for acceptance requests (default: 30 days). Nullable (null = 30 days).
- Add a "Document Acceptance" section to the existing org settings UI (or a sub-section under an existing settings page):
  - Expiry days input (number field, min 1, max 365, default 30)
- This is a small addition — not a new settings page, just a new section on an existing one.

### 8. Audit Events

**Requirements:**
- Record audit events for all acceptance state transitions:
  - `acceptance.created` — request created (details: document title, recipient name/email, expiry date)
  - `acceptance.sent` — email sent (details: document title, recipient email)
  - `acceptance.viewed` — recipient viewed the acceptance page (details: IP address, timestamp)
  - `acceptance.accepted` — recipient accepted (details: acceptor name, IP, user agent, timestamp)
  - `acceptance.reminded` — reminder sent (details: reminder count, recipient email)
  - `acceptance.revoked` — request revoked (details: revoked by member name, reason if provided)
  - `acceptance.expired` — request expired (details: original expiry date, document title)
  - `acceptance.certificate_generated` — certificate PDF generated (details: S3 key, document hash)

### 9. Notifications

**Requirements:**
- When a document is accepted:
  - In-app notification to the member who sent the request: "{contact.name} has accepted {document.fileName}"
  - Use existing notification system (`NotificationService.notify()`)
- When a request is about to expire (optional, nice-to-have):
  - In-app notification to the sender 3 days before expiry: "Acceptance request for {document.fileName} expires in 3 days"
  - This requires a scheduler check — if too complex, defer to a future phase

### 10. Frontend — Send for Acceptance Action

**Problem:** No way for firm members to initiate an acceptance request from the UI.

**Requirements:**
- Add a "Send for Acceptance" action on generated documents. This appears in two places:
  - **Generated Documents list** (on project/customer detail pages): Action menu item per document row
  - **After document generation** (in the GenerateDocumentDialog success state): "Send for Acceptance" button alongside existing "Download" and "Save to Documents" actions
- **Send for Acceptance dialog**:
  - Opens when the action is triggered
  - Shows: document title, file name, generation date
  - Recipient picker: dropdown of portal contacts for the document's customer. Shows contact name and email. If no portal contacts exist, show a message: "No portal contacts found for this customer. Add a portal contact first." with a link to the customer's contacts tab.
  - Expiry override: optional number input ("Expires in N days", placeholder shows org default). If left blank, uses org default.
  - "Send" button: calls `POST /api/acceptance-requests`, shows success toast with "Acceptance request sent to {name}"
  - Error handling: show specific errors for common cases (no portal contacts, contact not found, document not found)

### 11. Frontend — Acceptance Status Tracking

**Problem:** No visibility into whether a document has been sent for acceptance or what the status is.

**Requirements:**
- **Status badge on generated documents**: Show acceptance status next to each generated document in lists:
  - No request: no badge (current behavior)
  - SENT: "Awaiting Acceptance" (yellow/amber badge)
  - VIEWED: "Viewed" (blue badge)
  - ACCEPTED: "Accepted" (green badge with check icon)
  - EXPIRED: "Expired" (gray badge)
  - REVOKED: "Revoked" (gray badge)
- **Acceptance detail panel**: Expandable section or side panel on the generated document showing:
  - Recipient name and email
  - Status with timestamp (sent at, viewed at, accepted at)
  - If accepted: acceptor name, acceptance date, "Download Certificate" button
  - Actions: "Remind" button (if SENT/VIEWED), "Revoke" button (if SENT/VIEWED)
  - Reminder history: "Reminded N times, last on {date}"

### 12. Portal — Acceptance Page

**Problem:** No page in the portal for viewing and accepting documents.

**Requirements:**
- New route: `/portal/accept/{requestToken}`
  - The magic link in the email routes here
  - If the token is valid and the contact is authenticated (via magic link): show the acceptance page
  - If the token is invalid or expired: show an appropriate error page ("This link has expired" / "This request has been revoked")
- **Acceptance page layout**:
  - Org branding (logo, name) at the top
  - Document title and file name
  - PDF viewer: embedded PDF display (use `<iframe>` with the PDF URL, or a PDF.js viewer for better UX). The PDF should be viewable in the browser without downloading.
  - Below the PDF viewer:
    - Acceptance form:
      - Text: "By typing your name below and clicking Accept, you confirm that you have reviewed and accept this document."
      - Input field: "Your full name" (required, must match minimum 2 characters)
      - "I Accept" button: prominent, primary action. Disabled until name is entered.
    - Legal fine print: "Your acceptance will be recorded along with your IP address and browser information as proof of acceptance."
  - **Post-acceptance state**: After clicking accept, the page updates to show:
    - Green confirmation: "You have accepted this document on {date}"
    - The PDF remains viewable
    - "Download PDF" button for the original document
    - The acceptance form is replaced with the confirmation message
- **Expired/Revoked states**: If the request has expired or been revoked, show a clear message instead of the acceptance form. The PDF should still be viewable (the contact was sent the document — hiding it after expiry is poor UX).
- Add a "Pending Acceptances" section to the portal dashboard (or documents tab) showing documents awaiting the contact's acceptance, with a direct link to the acceptance page.

## Out of Scope

- Multi-signer workflows (multiple people accepting one document)
- Counter-signatures (firm signing after client acceptance)
- Third-party e-signature provider integration (DocuSign, SignRequest, HelloSign)
- Canvas-drawn or image-based signatures
- Automatic reminders / scheduled reminder emails
- Bulk send (same document to multiple recipients in one action)
- Non-portal-contact recipients (sending to arbitrary email addresses)
- Acceptance templates (customizable acceptance text per org)
- Acceptance conditions / partial acceptance ("I accept with modifications")
- Document negotiation / redlining
- SMS-based acceptance delivery
- Mobile-specific acceptance app
- Acceptance analytics / reporting dashboards
- Webhook-out for acceptance events (third-party integrations)

## ADR Topics

The architect should produce ADRs for:

1. **Acceptance token strategy** — how the acceptance URL authenticates the recipient. Options: (a) reuse existing magic-link tokens (acceptance request piggybacks on portal magic-link auth, token in URL is a magic-link token with a redirect to the acceptance page), (b) separate acceptance-specific token (a new single-use or limited-use token type, independent of magic-link auth), (c) composite token (acceptance token that also grants portal session on first use). Consider: security (token reuse risk, brute-force protection), UX (does the user land on a login page or go straight to the document?), token expiry alignment (acceptance expiry vs. magic-link expiry), and existing magic-link infrastructure reuse.

2. **Certificate storage and integrity** — how the Certificate of Acceptance is stored and its relationship to the original document. Options: (a) store as a separate S3 object linked via `AcceptanceRequest.certificateS3Key` (simple, but two objects to manage), (b) store as a `GeneratedDocument` entry with a special type/category (reuses existing document tracking infrastructure), (c) append certificate as a page to the original PDF (single file, but mutates the original). Consider: immutability guarantees, ease of retrieval, document hash integrity (if the cert references the original's hash, the original must not be modified), and S3 lifecycle/cleanup.

3. **Portal read-model sync granularity** — what acceptance data belongs in the portal read-model vs. queried live from the main schema. Options: (a) full sync (mirror all acceptance request fields to portal read-model, portal reads only from read-model), (b) minimal sync (only token + status + document reference in read-model, portal controller fetches full data from main schema on acceptance page load), (c) hybrid (read-model for list views, live query for the acceptance page itself). Consider: consistency requirements (acceptance is a critical action — stale data is risky), read-model sync latency, and the fact that the portal acceptance page is a transactional flow (not just a read-only view).

## Style & Boundaries

- The `AcceptanceRequest` entity and associated logic go in a new `acceptance/` package — conceptually related to documents but a distinct domain with its own lifecycle.
- `AcceptanceController` follows the thin-controller pattern: pure delegation to `AcceptanceService`, no business logic.
- `PortalAcceptanceController` follows the same pattern as existing portal controllers — token-authenticated, reads from portal read-model where possible.
- The certificate Thymeleaf template goes in `classpath:templates/certificates/` — separate from document templates (certificates are system-rendered, not user-editable).
- Email templates go in the existing email template directory alongside other notification emails.
- Portal acceptance page follows existing portal frontend patterns (layout, branding, component style).
- The "Send for Acceptance" dialog in the main app follows existing dialog patterns (Shadcn dialog, form validation, loading states, error toasts).
- Status badges on generated documents follow existing badge patterns (same color scheme and component as invoice status badges).
- Flyway migration: V45 (next available after Phase 27's V44). Creates `acceptance_requests` table, adds `acceptance_expiry_days` column to `org_settings`, adds portal read-model table.
- Domain events for portal sync follow the existing `DomainEvent` pattern — published via `ApplicationEventPublisher`, handled by read-model event handlers.
