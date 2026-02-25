# Phase 24 — Outbound Email Delivery

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with a notification system (Phase 6.5) that includes a channel abstraction (`NotificationChannel` interface), an `EmailNotificationChannel` stub that logs to console instead of sending, an `EmailTemplate` enum mapping notification types to simple string-formatted subjects/bodies, and a `NotificationDispatcher` that routes to channels based on user `NotificationPreference`. The email toggle in the preferences UI is disabled with "Coming soon" tooltip.

**Existing infrastructure this phase builds on:**

- **Notification channel abstraction** (`notification/channel/`): `NotificationChannel` interface with `channelId()`, `deliver()`, `isEnabled()`. `EmailNotificationChannel` exists as a `@Profile({"local", "dev"})` stub that logs emails. `InAppNotificationChannel` persists notifications. `NotificationDispatcher` resolves channels and checks per-user preferences before dispatching.
- **Email templates** (`EmailTemplate` enum): Maps notification types to subjects/bodies via `String.format()`. Types: TASK_ASSIGNED, TASK_CLAIMED, TASK_UPDATED, COMMENT_ADDED, DOCUMENT_SHARED, MEMBER_INVITED, DEFAULT. No HTML rendering — plain text only.
- **Template renderer placeholder** (`TemplateRenderer.java`): Empty `@Component` with a comment noting Thymeleaf integration is future work.
- **Notification preferences** (`NotificationPreference` entity): Per-member, per-type toggles for `inAppEnabled` (default true) and `emailEnabled` (default false). UI shows email toggle as disabled.
- **20 notification types** in `NotificationService`: TASK_ASSIGNED, TASK_CLAIMED, TASK_UPDATED, COMMENT_ADDED, DOCUMENT_SHARED, MEMBER_INVITED, BUDGET_ALERT, INVOICE_APPROVED, INVOICE_SENT, INVOICE_PAID, INVOICE_VOIDED, DOCUMENT_GENERATED, RECURRING_PROJECT_CREATED, SCHEDULE_SKIPPED, SCHEDULE_COMPLETED, RETAINER_PERIOD_READY_TO_CLOSE, RETAINER_PERIOD_CLOSED, RETAINER_APPROACHING_CAPACITY, RETAINER_FULLY_CONSUMED, RETAINER_TERMINATED.
- **Integration ports** (Phase 21, `integration/`): `IntegrationDomain` enum (ACCOUNTING, AI, DOCUMENT_SIGNING, PAYMENT — no EMAIL yet), `IntegrationRegistry` with tenant-scoped resolution, `@IntegrationAdapter` annotation, `OrgIntegration` entity with JSONB config, `SecretStore` for encrypted API keys, NoOp stub pattern.
- **Thymeleaf rendering** (Phase 12, `template/`): `PdfRenderingService` with a `StringTemplateResolver`-based Thymeleaf engine, `TemplateContextBuilder` strategy pattern, `TemplateSecurityValidator` for SSTI prevention. Reusable for HTML email rendering.
- **Portal magic links** (Phase 7): `MagicLinkService` generates 32-byte tokens with 15-min TTL and rate limiting. Currently returns token to the API caller — no email delivery.
- **Invoice send flow** (Phase 10): `InvoiceService` transitions invoice to SENT status, publishes `InvoiceSentEvent`, creates in-app notifications for admins. No email to customer.
- **Org branding** (Phase 12): `OrgSettings` has `logoUrl`, `brandColor`, `footerText` — available for email template branding.

## Objective

Wire up real email delivery so that every user-facing flow that currently stubs or skips email — notifications, invoice delivery, portal magic links, document sharing — actually reaches inboxes. After this phase:

- Every org gets working email out of the box via platform SMTP (JavaMail) — zero configuration required
- Orgs that want higher volume or custom deliverability can bring their own SendGrid API key (BYOAK override)
- All 20 notification types can send branded HTML emails based on user preferences
- Invoice "Send" delivers a PDF attachment to the customer's email
- Portal contacts receive magic link emails instead of manual link copying
- Bounce and failure status is tracked per delivery for debugging and audit
- Per-tenant rate limiting prevents abuse

## Constraints & Assumptions

- **Two-tier email architecture**: Platform SMTP (JavaMail via Spring's `JavaMailSender`) is the default — every org gets email with zero config. BYOAK SendGrid is the optional upgrade path, using the `IntegrationPort` / `IntegrationRegistry` pattern from Phase 21. A new `EMAIL` domain is added to `IntegrationDomain`.
- The `EmailProvider` port interface must be provider-agnostic — no SendGrid-specific or JavaMail-specific types in the interface. Both `SmtpEmailProvider` and `SendGridEmailProvider` implement it.
- Sender address is `noreply@{platform-domain}` for v1. No custom sender domains or DNS verification.
- Email templates use Thymeleaf (reuse the `StringTemplateResolver` engine from Phase 12). Templates are classpath resources, not database-stored.
- The existing `EmailNotificationChannel` stub is replaced (or re-profiled) — production uses the real channel, local/dev continues to log.
- Email delivery is fire-and-forget from the caller's perspective — `NotificationDispatcher.dispatch()` should not block on email delivery. Failures are logged and tracked but don't fail the triggering operation.
- Bounce tracking requires a publicly accessible webhook endpoint. The architect should decide whether this is a separate controller path or integrated into existing webhook infrastructure.
- All new entities and columns are tenant-scoped (schema-per-tenant).
- This phase does NOT add inbound email parsing, open/click tracking, custom sender domains, or additional provider adapters beyond SendGrid.

## Detailed Requirements

### 1. EmailProvider Port Interface & Two-Tier Adapters

**Problem:** The integration port system has domains for ACCOUNTING, AI, DOCUMENT_SIGNING, and PAYMENT but no EMAIL domain. There's no provider-agnostic interface for sending email. Requiring every org to configure a third-party API key before email works creates a poor onboarding experience.

**Requirements:**
- Add `EMAIL` to the `IntegrationDomain` enum.
- Create an `EmailProvider` port interface in the `integration/` package with methods:
  - `SendResult sendEmail(EmailMessage message)` — sends a single email
  - `SendResult sendEmailWithAttachment(EmailMessage message, EmailAttachment attachment)` — sends email with attachment (for invoice PDFs)
- `EmailMessage` value object: `to` (email address), `subject`, `htmlBody`, `plainTextBody` (fallback), `replyTo` (optional), `metadata` (Map for tracking IDs).
- `EmailAttachment` value object: `filename`, `contentType`, `content` (byte array).
- `SendResult` value object: `success` (boolean), `providerMessageId` (String, nullable), `errorMessage` (String, nullable).

#### Tier 1 — Platform SMTP (default, zero config)
- Create `SmtpEmailProvider` adapter. Annotate with `@IntegrationAdapter(domain = EMAIL, slug = "smtp")`.
- Uses Spring's `JavaMailSender` (auto-configured via `spring.mail.*` properties).
- This is the **default adapter** — the one `IntegrationRegistry` resolves when an org has no EMAIL integration configured.
- Platform-managed SMTP credentials configured in application properties (e.g., a platform SendGrid account, Amazon SES, or any SMTP relay). The org never sees or manages these credentials.
- Sender address: `noreply@{platform-domain}` (from `docteams.email.sender-address` property).
- Sender name: org display name from `OrgSettings` (falls back to platform name).
- `providerMessageId` on `SendResult`: extracted from the SMTP `Message-ID` header for delivery tracking.
- Tighter rate limits apply when using platform SMTP (see Section 6).

#### Tier 2 — BYOAK SendGrid (optional upgrade)
- Create `SendGridEmailProvider` adapter. Annotate with `@IntegrationAdapter(domain = EMAIL, slug = "sendgrid")`.
- Uses SendGrid Java SDK (`com.sendgrid:sendgrid-java`).
- Activated when an org configures an EMAIL integration with `providerSlug = "sendgrid"` via the Integration Settings UI.
- Retrieves API key from `SecretStore` using the org's `keySuffix`.
- Sender address: configurable per-org in `OrgIntegration.configJson` (field: `senderEmail`), defaults to `noreply@{platform-domain}`.
- Sender name: configurable per-org (field: `senderName`), defaults to org display name from `OrgSettings`.
- Registers a SendGrid webhook URL in `configJson` for bounce tracking (field: `webhookSecret`).
- Higher rate limits apply when using BYOAK (see Section 6).

#### Resolution Logic
- When `IntegrationRegistry.resolve(EMAIL, EmailProvider.class)` is called:
  1. If the org has an enabled EMAIL integration → return the configured adapter (e.g., `SendGridEmailProvider`).
  2. If no org-level integration → return `SmtpEmailProvider` (platform default). **Not** `NoOpEmailProvider`.
- `NoOpEmailProvider` is retained only for local/dev profiles where no SMTP server is available (logs email details at INFO level, returns success). It replaces `SmtpEmailProvider` via `@Profile({"local", "dev"})`.
- **Architect: decide how to implement the "fallback to platform SMTP" behavior** — options: make `SmtpEmailProvider` the default slug in `IntegrationRegistry` instead of "noop", or add a fallback chain to the resolution logic.

### 2. Email Template Rendering Service

**Problem:** `EmailTemplate` enum uses `String.format()` for plain-text subjects/bodies. Emails need branded HTML with org logo, colors, and rich content.

**Requirements:**
- Implement the existing `TemplateRenderer` component (currently an empty stub) as an `EmailTemplateRenderer` that uses Thymeleaf's `StringTemplateResolver`.
- Reuse the security infrastructure from `PdfRenderingService` (`TemplateSecurityValidator`, `LenientStandardDialect`) but keep it as a separate service — email rendering doesn't need PDF conversion.
- Email templates are Thymeleaf HTML files stored on the classpath at `templates/email/`.
- Create a **base layout template** (`templates/email/base.html`) with:
  - Responsive HTML email structure (table-based layout for email client compatibility)
  - Header with org logo (from `OrgSettings.logoUrl`) and org name
  - Content area (yielded to child templates)
  - Footer with org name, `footerText` from `OrgSettings`, and unsubscribe link
  - Brand color accent from `OrgSettings.brandColor`
- Create **per-type email templates** that extend the base layout. Group notification types by template similarity:
  - `notification-task.html` — TASK_ASSIGNED, TASK_CLAIMED, TASK_UPDATED (actor, task name, project name, action, link to task)
  - `notification-comment.html` — COMMENT_ADDED (actor, comment preview, entity name, link)
  - `notification-document.html` — DOCUMENT_SHARED, DOCUMENT_GENERATED (actor, document name, project name, link)
  - `notification-member.html` — MEMBER_INVITED (inviter name, org name, link)
  - `notification-budget.html` — BUDGET_ALERT (project name, budget percentage, threshold, link)
  - `notification-invoice.html` — INVOICE_APPROVED, INVOICE_SENT, INVOICE_PAID, INVOICE_VOIDED (invoice number, customer name, amount, status, link)
  - `notification-schedule.html` — RECURRING_PROJECT_CREATED, SCHEDULE_SKIPPED, SCHEDULE_COMPLETED (schedule name, project name, link)
  - `notification-retainer.html` — all 5 retainer types (retainer name, customer name, period, utilization, link)
  - `portal-magic-link.html` — magic link email for portal contacts (org name, link, expiry notice)
  - `invoice-delivery.html` — customer-facing invoice email (org name, invoice number, amount, due date, "View Invoice" button linking to portal)
- Each template receives a context map with: `orgName`, `orgLogoUrl`, `brandColor`, `footerText`, `recipientName`, `unsubscribeUrl`, `appUrl`, plus type-specific variables.
- `EmailTemplateRenderer.render(String templateName, Map<String, Object> context)` returns `RenderedEmail` with `subject` and `htmlBody`.
- Generate a plain-text fallback by stripping HTML tags from the rendered body (simple utility — doesn't need to be perfect).

### 3. EmailNotificationChannel Enhancement

**Problem:** `EmailNotificationChannel` is a dev/local stub that logs instead of sending. It needs to become a production-ready channel that resolves the `EmailProvider` via `IntegrationRegistry` and sends real emails.

**Requirements:**
- Remove the `@Profile({"local", "dev"})` restriction from `EmailNotificationChannel`. It should always be active.
- Inject `IntegrationRegistry` and `EmailTemplateRenderer`.
- In `deliver(Notification notification, String recipientEmail)`:
  1. Resolve `EmailProvider` via `IntegrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class)`.
  2. Build the template context from the `Notification` entity (type, title, body, reference entity, actor info).
  3. Render the email via `EmailTemplateRenderer.render(templateName, context)`.
  4. Call `emailProvider.sendEmail(message)`.
  5. Track the delivery result (see Section 5).
- If the resolved provider is `NoOpEmailProvider` (no integration configured and no platform default), log and skip silently — don't fail.
- Email delivery must not throw exceptions that propagate to the caller. Catch all exceptions, log them, and record a FAILED delivery status.

### 4. Non-Notification Email Flows

**Problem:** Three email flows don't go through the notification system: portal magic links, invoice delivery to customers, and document share links. These need direct email sending.

**Requirements:**

#### 4a. Portal Magic Link Email
- Create a `PortalEmailService` in the `portal/` package.
- Method: `sendMagicLinkEmail(PortalContact contact, String magicLinkUrl)`.
- Renders `portal-magic-link.html` template with org branding and the magic link URL.
- Sends via resolved `EmailProvider`.
- Integrate into `MagicLinkService`: after token generation, call `PortalEmailService.sendMagicLinkEmail()`.
- The existing API endpoint that generates magic links should trigger email automatically (currently it returns the token to the caller — it should continue to return the token AND send the email).
- Track delivery status on the `MagicLinkToken` entity or in a separate delivery log.

#### 4b. Invoice Delivery Email
- Create an `InvoiceEmailService` in the `invoice/` package.
- Method: `sendInvoiceEmail(Invoice invoice, byte[] pdfBytes)`.
- Renders `invoice-delivery.html` template with invoice details, customer info, org branding.
- Attaches the PDF using `EmailProvider.sendEmailWithAttachment()`.
- Recipient: the customer's primary contact email (from `Customer` entity or a configurable field).
- Integrate into the invoice SENT transition in `InvoiceService`: after marking as SENT, generate PDF (if not already generated) and call `InvoiceEmailService.sendInvoiceEmail()`.
- If email delivery fails, the invoice remains SENT (delivery failure is tracked but doesn't block the status transition).

#### 4c. Document Share Email
- Extend the existing document upload/share notification to support sending an email with a link to the document.
- This goes through the normal `NotificationDispatcher` path — the `DOCUMENT_SHARED` notification type already exists.
- The email template should include a direct link to the document in the app (not the portal — internal team members only for v1).

### 5. Delivery Tracking & Bounce Handling

**Problem:** Without delivery tracking, there's no way to diagnose "I never got the email" complaints. Bounces need to be captured for invoice and magic link emails where non-delivery has business consequences.

**Requirements:**
- Create an `EmailDeliveryLog` entity (tenant-scoped):
  - `UUID id`
  - `String recipientEmail`
  - `String templateName` — which email template was used
  - `String referenceType` — NOTIFICATION, INVOICE, MAGIC_LINK
  - `UUID referenceId` — ID of the notification, invoice, or magic link token
  - `EmailDeliveryStatus status` — enum: SENT, DELIVERED, BOUNCED, FAILED
  - `String providerMessageId` — SendGrid message ID for correlation
  - `String errorMessage` — failure/bounce reason (nullable)
  - `Instant createdAt`
  - `Instant updatedAt`
- Every email send (notification, invoice, magic link) creates a delivery log entry.
- Create a `POST /api/webhooks/email/{provider}` endpoint (not tenant-scoped — provider webhooks are global):
  - Validates webhook signature (SendGrid signs webhooks with a verification key).
  - Parses bounce/failure events from the SendGrid Event Webhook payload.
  - Looks up the `EmailDeliveryLog` entry by `providerMessageId`.
  - Updates status to BOUNCED or FAILED with the error reason.
  - For bounced invoice emails: create an in-app notification for org admins — "Invoice {number} email to {customer} bounced: {reason}".
  - For bounced magic link emails: log a warning (portal contact may have a bad email address).
- Publish an audit event for delivery failures (type: `email.delivery.failed`).
- **Architect: decide on webhook endpoint security** — SendGrid uses a verification key per account. Since this is a multi-tenant system, the webhook URL needs to identify which tenant the event belongs to (options: encode tenant ID in the URL path, use the `providerMessageId` to look up the tenant, or use a global table).

### 6. Rate Limiting & Abuse Prevention

**Problem:** Without rate limiting, a misconfigured automation or a malicious tenant could exhaust the platform SMTP quota (which the platform pays for) or the org's own SendGrid quota.

**Requirements:**
- Implement per-tenant email rate limiting with **tier-aware limits**:
  - **Platform SMTP (Tier 1)**: 50 emails per hour per tenant. Tighter limit because the platform bears the cost.
  - **BYOAK SendGrid (Tier 2)**: 200 emails per hour per tenant. Higher limit because the org pays for their own quota.
  - Limits configurable via application properties (`docteams.email.rate-limit.smtp`, `docteams.email.rate-limit.byoak`).
  - Pro/Enterprise plan tiers may have multipliers (check `OrgSettings` or plan tier).
  - When limit is exceeded: log a warning, skip email delivery, record RATE_LIMITED status in delivery log.
  - Do NOT fail the triggering operation (notification still shows in-app, invoice still marks as SENT).
- Platform-level aggregate rate limiting (for Tier 1 only):
  - Aggregate limit across all tenants using platform SMTP (e.g., 2000 emails per hour total).
  - Individual tenant limit still applies within the aggregate.
  - If aggregate limit is hit, all platform-SMTP tenants are throttled (BYOAK tenants are unaffected).
  - **Architect: recommend implementation** — in-memory counter (Caffeine cache) vs. database counter vs. Redis (if available). Consider that this is a single-instance deployment for now.

### 7. Unsubscribe Handling

**Problem:** Email regulations (CAN-SPAM, POPIA) require an unsubscribe mechanism. The `NotificationPreference` system already supports per-type email opt-out, but there's no way to trigger it from an email link.

**Requirements:**
- Generate a signed unsubscribe URL per email: `/api/email/unsubscribe?token={signed-token}`.
- The token encodes: `memberId`, `notificationType`, `tenantSchema` (signed with HMAC to prevent tampering).
- `GET /api/email/unsubscribe` — verifies the token, sets `emailEnabled = false` for that notification type on the member's preference, returns a simple HTML confirmation page ("You've been unsubscribed from {type} emails").
- Include the unsubscribe URL in the base email template footer.
- The unsubscribe link is for internal team member notifications only — portal/invoice emails to customers don't use `NotificationPreference` (they're transactional, not marketing).
- **List-Unsubscribe header**: Include `List-Unsubscribe` and `List-Unsubscribe-Post` headers in notification emails for one-click unsubscribe support in email clients (Gmail, Apple Mail).

### 8. Frontend Changes

**Problem:** The email toggle in notification preferences is disabled. Orgs have no UI to configure email provider settings.

**Requirements:**

#### 8a. Notification Preferences — Enable Email Toggle
- In `notification-preferences-form.tsx`: remove the "Coming soon" disabled state from the email toggle.
- Enable the toggle for all notification types.
- When toggled on, the preference is saved via the existing `updateNotificationPreferences` action.
- No setup-required warning needed — email works out of the box via platform SMTP.
- Add the missing notification types to the preferences UI: BUDGET_ALERT, INVOICE_APPROVED, INVOICE_SENT, INVOICE_PAID, INVOICE_VOIDED, DOCUMENT_GENERATED, RECURRING_PROJECT_CREATED, SCHEDULE_SKIPPED, SCHEDULE_COMPLETED.

#### 8b. Email Provider Settings
- Add an "Email" card to the Integration Settings page (Phase 21 UI, `integrations-settings` page).
- The card should clearly communicate the two-tier model:
  - **Default state** (no integration configured): Show a status badge "Platform Email — Active" with a note: "Your organization is using DocTeams' built-in email delivery. For higher volume or custom sender identity, configure your own SendGrid account below."
  - **BYOAK state**: Show the configured provider with a status badge.
- The card follows the existing integration card pattern (provider selector, API key input, test connection button).
- Fields (shown when expanding "Use your own email provider"):
  - Provider: dropdown (SendGrid — only option for v1, but UI should support future additions).
  - API Key: password input, stored via `SecretStore`.
  - Sender Email: text input, defaults to `noreply@{platform-domain}`.
  - Sender Name: text input, defaults to org name.
  - Webhook URL: read-only display of the webhook URL to configure in SendGrid dashboard (auto-generated based on tenant).
  - "Test Connection" button: sends a test email to the current user's email address.
  - "Send Test Email" button: sends a rendered test email using the `DEFAULT` template.
- Show delivery statistics summary: emails sent (last 24h), bounces (last 7d), failures (last 7d) — queried from `EmailDeliveryLog`.
- Show current rate limit tier: "Platform: 50/hour" or "Custom: 200/hour".

#### 8c. Delivery Log Viewer (Admin Only)
- Add a "Delivery Log" tab to the email settings section (or as a sub-page).
- Table with columns: Date, Recipient, Template, Status (with color-coded badge), Provider Message ID, Error.
- Filterable by status (SENT, DELIVERED, BOUNCED, FAILED, RATE_LIMITED) and date range.
- Accessible to ADMIN and OWNER roles only.

### 9. Audit Events

**Requirements:**
- Record audit events for:
  - `email.integration.configured` — org configures email provider
  - `email.integration.updated` — org changes provider settings
  - `email.integration.removed` — org removes email provider
  - `email.delivery.failed` — email delivery failure (includes recipient, template, error)
  - `email.delivery.bounced` — email bounced (includes recipient, bounce reason)
  - `email.test.sent` — test email sent from settings
- These build on the existing `AuditEvent` entity and `AuditService` from Phase 6.

## Out of Scope

- Custom sender domains / DNS verification (DKIM, SPF, DMARC setup)
- Open/click tracking
- Inbound email capture or parsing
- Additional provider adapters (Mailgun, SES, Postmark — port is ready, adapters come later)
- Visual email template editor (templates are classpath resources)
- Email scheduling / send-later functionality
- Email threading (In-Reply-To / References headers for conversation grouping)
- Bulk email / marketing campaigns
- Customer-facing email preferences (portal contacts can't manage their email settings in v1)

## ADR Topics

The architect should produce ADRs for:

1. **Two-tier email resolution** — how `IntegrationRegistry` resolves `SmtpEmailProvider` (platform default) vs `SendGridEmailProvider` (BYOAK) vs `NoOpEmailProvider` (dev). Options: change default slug from "noop" to "smtp", add fallback chain, or auto-provision an `OrgIntegration` row for every tenant. Consider: should the registry's resolution logic be generic (any domain can have a platform default) or EMAIL-specific?
2. **Webhook tenant identification** — how the bounce webhook endpoint determines which tenant an email event belongs to. Options: encode tenant in webhook URL path, encode in email metadata/custom args, or use a global (non-tenant-scoped) delivery log table for webhook correlation. Consider multi-tenant security implications.
3. **Rate limiting implementation** — in-memory (Caffeine) vs. database counter vs. scheduled reset. Consider: accuracy requirements, restart behavior, future multi-instance deployment. Recommend the simplest approach that works for single-instance now with a clear upgrade path.

## Style & Boundaries

- Follow the existing `IntegrationPort` / `@IntegrationAdapter` pattern for the email provider (mirror `AccountingProvider` / `NoOpAccountingProvider` structure).
- Follow the existing `NotificationChannel` pattern for the enhanced `EmailNotificationChannel`.
- Email templates use Thymeleaf but are classpath resources (not database-stored like document templates) — they're part of the application, not user-customizable.
- The `EmailDeliveryLog` entity follows the standard tenant-scoped entity pattern (no `@TenantAware`, just lives in tenant schema).
- Frontend components follow existing patterns: integration settings card pattern, notification preferences form pattern, data table pattern for delivery log.
- Spring Boot's `spring-boot-starter-mail` provides `JavaMailSender` for the platform SMTP adapter — this is a standard dependency.
- The SendGrid Java SDK (`com.sendgrid:sendgrid-java`) should be added as an optional Maven dependency — the application should start without it if no org uses SendGrid.
- All new Flyway migrations go in the tenant schema (next available version after Phase 23's V38).
