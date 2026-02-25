# Phase 25 — Online Payment Collection

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with a complete invoicing system (Phase 10) that supports DRAFT → APPROVED → SENT → PAID lifecycle, unbilled time generation, line items, and HTML preview. Payment recording is currently manual — a "Record Payment" button marks an invoice as PAID with an optional reference string. Behind the scenes, a `PaymentProvider` interface exists with a `MockPaymentProvider` that always returns success.

**Existing infrastructure this phase builds on:**

- **Payment provider interface** (`invoice/`): `PaymentProvider` interface with `recordPayment(PaymentRequest): PaymentResult`. `MockPaymentProvider` is a `@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)` component that returns `success=true` with `"MOCK-PAY-{uuid}"` references. `PaymentRequest` record: `invoiceId`, `amount`, `currency`, `description`. `PaymentResult` record: `success`, `paymentReference`, `errorMessage`. ADR-051 documents the design.
- **Invoice entity** (`invoice/Invoice.java`): Has `paymentReference` (VARCHAR 255), `paidAt` (TIMESTAMPTZ), `status` enum (DRAFT, APPROVED, SENT, PAID, VOID). The `InvoiceService.recordPayment()` method transitions SENT → PAID, stores the reference, publishes `InvoicePaidEvent`. The controller endpoint is `POST /api/invoices/{id}/payment` with optional `{ paymentReference }`.
- **Integration ports** (Phase 21, `integration/`): `IntegrationDomain` enum (ACCOUNTING, AI, DOCUMENT_SIGNING, PAYMENT, EMAIL), `IntegrationRegistry` with tenant-scoped resolution via `OrgIntegration` entity and Caffeine cache, `@IntegrationAdapter` annotation for self-registration, `SecretStore` for encrypted API key storage, `IntegrationGuardService` for feature flag checks, NoOp stub pattern. The `PAYMENT` domain is declared but only the mock provider is registered.
- **Customer Portal** (Phase 22, `portal/`): Separate Next.js app at `portal/`. Portal contacts authenticate via magic links (Phase 7). Invoice list and detail pages exist (`/portal/invoices`, `/portal/invoices/[id]`). Portal backend exposes read-model endpoints for invoices (`/api/portal/invoices`). No payment capability on portal pages currently.
- **Portal read-model** (Phase 7 + 22): `portal_read` schema with synced invoice data. `PortalInvoiceProjection` includes invoice number, amount, status, due date. `InvoiceEventHandler` syncs invoice state changes to the read-model.
- **Org branding** (`OrgSettings`): `logoUrl`, `brandColor`, `footerText` — used in invoice templates and customer-facing pages.
- **Email delivery** (Phase 24, in progress): `EmailProvider` port with `SmtpEmailProvider` (platform default) and `SendGridEmailProvider` (BYOAK). Email template rendering via Thymeleaf. `EmailDeliveryLog` for tracking. Invoice SENT transition triggers email delivery with PDF attachment.
- **Integration Settings UI** (Phase 21): Card-grid layout at Settings → Integrations. Each domain has a card with provider selector, API key input, test connection. The PAYMENT card exists but has no real provider options.
- **Audit and notifications** (Phases 6 + 6.5): `AuditService` for domain events, `NotificationService` for in-app and email notifications. `InvoicePaidEvent` already triggers both.

## Objective

Add real online payment collection so that clients can pay invoices directly via Stripe or PayFast. After this phase:

- Tenants connect their Stripe or PayFast account via the Integration Settings UI (BYOAK model)
- When an invoice is sent, a unique payment link is generated and included in the invoice email and portal page
- Clients click "Pay Now" on the portal invoice page (or in the email) and are redirected to Stripe Checkout or PayFast's hosted payment page
- After successful payment, a webhook callback automatically marks the invoice as PAID — no manual intervention required
- Payment status, reference, and provider details are tracked on the invoice and in a payment event log
- The mock provider continues to work as the default for orgs that haven't configured a PSP (manual "Record Payment" button remains available)
- A `paymentDestination` field (default: `OPERATING`) is included on payment records to support future trust accounting in legal vertical forks

## Constraints & Assumptions

- **BYOAK only** — there is no platform-managed Stripe/PayFast account. Tenants must connect their own PSP account to enable online payments. Without a configured PSP, the existing manual "Record Payment" flow remains the only option. This is different from email (Phase 24) where platform SMTP provides a zero-config default.
- **Stripe Checkout (hosted)** — not embedded Stripe Elements. Client is redirected to a Stripe-hosted payment page. Zero PCI burden on the platform. Stripe handles Apple Pay, Google Pay, bank transfers, and local payment methods automatically.
- **PayFast standard integration** — client is redirected to PayFast's hosted payment page. PayFast handles all payment methods (credit card, EFT, SnapScan, Mobicred). Uses PayFast's ITN (Instant Transaction Notification) for webhook callbacks.
- **Full payment only** — no partial payments, no deposits, no split payments in v1. Client pays the full invoice amount. The invoice transitions directly from SENT → PAID.
- **No recurring/auto-charge** — no card-on-file, no automatic charging on due dates. Each invoice requires an explicit "Pay Now" action from the client.
- **Payment destination seam** — a `paymentDestination` enum field (values: `OPERATING`) on payment records. Default is always `OPERATING` in v1. The legal fork adds `TRUST` as a second value and builds ledger/routing logic on top. No trust accounting logic in this phase.
- **Existing `PaymentProvider` interface is replaced** — the current interface (`recordPayment(PaymentRequest): PaymentResult`) was designed for synchronous manual recording. Online payment collection is async (create session → redirect → webhook callback). The architect should design a new `PaymentGateway` port interface (or extend the existing one) that supports session creation, webhook processing, and status queries alongside manual recording.
- **The `PAYMENT` domain in `IntegrationDomain` already exists** — this phase wires it up with real adapters. The `IntegrationRegistry` resolution, `OrgIntegration` storage, and `SecretStore` encryption are all in place.
- **Portal is the primary payment surface** — clients pay from the portal invoice detail page. The payment link in the invoice email also goes to the portal. There is no standalone payment page outside the portal.
- **All new entities are tenant-scoped** (schema-per-tenant). All new migrations use the next available tenant schema version after Phase 24.
- **Currency** — invoice currency is already stored on the invoice. The payment session is created in the same currency. No currency conversion.

## Detailed Requirements

### 1. PaymentGateway Port Interface & Adapter Registration

**Problem:** The current `PaymentProvider` interface supports only synchronous manual payment recording. Online payment collection requires async flows: create a checkout session, redirect the client, receive a webhook callback, and reconcile.

**Requirements:**
- Design a `PaymentGateway` port interface in the `integration/` package (or extend the existing `PaymentProvider` — architect's decision). The interface needs to support:
  - `CreateSessionResult createCheckoutSession(CheckoutRequest request)` — creates a payment session with the PSP and returns a redirect URL
  - `WebhookResult handleWebhook(String payload, Map<String, String> headers)` — validates and processes incoming webhook payloads
  - `PaymentStatus queryPaymentStatus(String sessionId)` — queries the PSP for current payment status (fallback for missed webhooks)
- `CheckoutRequest` value object: `invoiceId` (UUID), `invoiceNumber` (String), `amount` (BigDecimal), `currency` (String, ISO 4217), `customerEmail` (String), `customerName` (String), `successUrl` (String — portal URL to redirect after success), `cancelUrl` (String — portal URL for cancelled payment), `metadata` (Map — for tenant identification in webhooks).
- `CreateSessionResult` value object: `success` (boolean), `sessionId` (String — PSP's session identifier), `redirectUrl` (String — where to send the client), `errorMessage` (String, nullable).
- `WebhookResult` value object: `verified` (boolean — signature valid), `eventType` (String — e.g., "payment.completed", "payment.failed"), `sessionId` (String), `paymentReference` (String — PSP transaction ID), `metadata` (Map).
- `PaymentStatus` enum: `PENDING`, `COMPLETED`, `FAILED`, `EXPIRED`, `CANCELLED`.
- The existing `MockPaymentProvider` stays in place for manual payment recording. The new `PaymentGateway` interface is separate — manual recording and online collection coexist.
- **Architect: decide on relationship between `PaymentProvider` (sync, manual) and `PaymentGateway` (async, online)** — options: (a) keep them as separate interfaces, (b) merge into one interface with both methods, (c) deprecate `PaymentProvider` and add a `recordManualPayment()` method to `PaymentGateway`. Consider backward compatibility and the BYOAK resolution model.

### 2. Stripe Adapter

**Problem:** Stripe is the global standard for online payments and the expected PSP for international tenants.

**Requirements:**
- Create `StripePaymentGateway` implementing `PaymentGateway`. Annotate with `@IntegrationAdapter(domain = PAYMENT, slug = "stripe")`.
- Uses the Stripe Java SDK (`com.stripe:stripe-java`).
- **Session creation** (`createCheckoutSession`):
  - Creates a Stripe Checkout Session in `payment` mode (one-time payment, not subscription).
  - Line item: single item with invoice number as description, invoice amount as unit price, quantity 1.
  - Sets `client_reference_id` to the invoice ID (UUID string).
  - Sets `metadata` with `tenantSchema` (for webhook tenant identification) and `invoiceId`.
  - Sets `customer_email` to pre-fill the Checkout form.
  - Sets `success_url` and `cancel_url` from the request (portal URLs with session ID placeholder: `?session_id={CHECKOUT_SESSION_ID}`).
  - Returns the Checkout Session URL as `redirectUrl`.
- **Webhook handling** (`handleWebhook`):
  - Validates the webhook signature using `Webhook.constructEvent()` with the endpoint signing secret.
  - Handles `checkout.session.completed` event: extracts `client_reference_id` (invoice ID), `payment_intent` (payment reference), and `metadata`.
  - Handles `checkout.session.expired` event: marks the payment attempt as expired.
  - Returns `WebhookResult` with parsed event data.
- **Status query** (`queryPaymentStatus`):
  - Retrieves the Checkout Session by ID via `Session.retrieve()`.
  - Maps Stripe session status to `PaymentStatus` enum.
- **Stripe API key**: Retrieved from `SecretStore` using the org's `OrgIntegration` `keySuffix`. The key is set per-request (not globally) to support multi-tenant usage — use `RequestOptions.builder().setApiKey(key).build()`.
- **Webhook signing secret**: Stored in `OrgIntegration.configJson` as `webhookSigningSecret`, encrypted via `SecretStore`.
- Add `com.stripe:stripe-java` as a Maven dependency. It should be a standard (not optional) dependency since Stripe is a primary adapter.

### 3. PayFast Adapter

**Problem:** PayFast is the dominant payment gateway for South African businesses. SA law firms and accounting practices expect PayFast support.

**Requirements:**
- Create `PayFastPaymentGateway` implementing `PaymentGateway`. Annotate with `@IntegrationAdapter(domain = PAYMENT, slug = "payfast")`.
- PayFast does not have a Java SDK — use direct HTTP integration with Spring's `RestClient`.
- **Session creation** (`createCheckoutSession`):
  - PayFast uses a form POST model: generate a signed payment URL that the client is redirected to.
  - Build the PayFast payment data: `merchant_id`, `merchant_key`, `return_url` (success), `cancel_url`, `notify_url` (ITN webhook URL), `amount`, `item_name` (invoice number), `item_description`, `email_address` (customer email), `custom_str1` (tenant schema for ITN identification), `custom_str2` (invoice ID).
  - Generate the PayFast signature (MD5 hash of URL-encoded parameter string + passphrase).
  - Return the PayFast payment URL (`https://www.payfast.co.za/eng/process` for production, `https://sandbox.payfast.co.za/eng/process` for sandbox) with encoded parameters as `redirectUrl`.
  - **Note**: PayFast doesn't create a "session" — the redirect URL IS the session. Generate a local session ID (UUID) for tracking and store it as the session reference.
- **Webhook handling** (`handleWebhook` — PayFast calls this "ITN"):
  - Validate the ITN: (1) verify source IP is PayFast's IP range, (2) verify the signature, (3) verify payment data matches what was sent, (4) confirm with PayFast's validation endpoint (`https://www.payfast.co.za/eng/query/validate`).
  - Extract `pf_payment_id` as payment reference, `custom_str1` as tenant schema, `custom_str2` as invoice ID.
  - Map PayFast `payment_status` to `PaymentStatus` enum: `COMPLETE` → `COMPLETED`, `FAILED` → `FAILED`, `PENDING` → `PENDING`.
  - Return `WebhookResult` with parsed data.
- **Status query** (`queryPaymentStatus`):
  - PayFast doesn't have a session status API. Return `PENDING` for sessions not yet confirmed via ITN, or the last known status from the payment event log.
- **PayFast credentials**: `merchantId` and `merchantKey` stored in `OrgIntegration.configJson`. `passphrase` stored encrypted via `SecretStore`.
- **Sandbox support**: Configurable via application property `docteams.payfast.sandbox=true` (default true for local/dev). Switches between sandbox and production URLs and IP ranges.
- No external SDK dependency — pure HTTP/REST integration.

### 4. Payment Link Generation & Invoice Extension

**Problem:** Invoices need a payment link that clients can click to pay. The link must be generated when a PSP is configured and the invoice is in SENT status.

**Requirements:**
- Add fields to the `Invoice` entity:
  - `paymentSessionId` (VARCHAR 255, nullable) — the PSP session identifier
  - `paymentUrl` (VARCHAR 1024, nullable) — the hosted payment page URL
  - `paymentDestination` (VARCHAR 50, default `'OPERATING'`) — enum string for future trust accounting fork
- Create a `PaymentLinkService` in the `invoice/` package:
  - Method: `generatePaymentLink(Invoice invoice)` — resolves the `PaymentGateway` via `IntegrationRegistry`, creates a checkout session, stores the session ID and URL on the invoice.
  - Method: `refreshPaymentLink(Invoice invoice)` — regenerates an expired payment link (Stripe sessions expire after 24h by default, PayFast URLs don't expire but may need regeneration for amount changes).
  - If no PSP is configured (registry returns NoOp or mock): do nothing. The payment link fields remain null. The manual "Record Payment" button is the only option.
- **Trigger**: After an invoice transitions to SENT in `InvoiceService`:
  1. If a PSP is configured → call `PaymentLinkService.generatePaymentLink()`.
  2. If email delivery is configured (Phase 24) → the invoice email includes the payment URL.
  3. The portal invoice detail page shows the "Pay Now" button if `paymentUrl` is non-null.
- **Payment link in email**: If Phase 24 is complete, extend the `invoice-delivery.html` Thymeleaf template to include a "Pay Now" button linking to `paymentUrl`. If `paymentUrl` is null, the button is omitted.
- The payment URL must be synced to the portal read-model so the portal can display it (extend `PortalInvoiceProjection` with `paymentUrl`).

### 5. Webhook Reconciliation & Payment Recording

**Problem:** After a client pays via Stripe or PayFast, a webhook callback must automatically mark the invoice as PAID without manual intervention.

**Requirements:**
- Create a `POST /api/webhooks/payment/{provider}` endpoint (NOT tenant-scoped — PSP webhooks are global, similar to Clerk webhooks):
  - Path variable `{provider}` maps to `"stripe"` or `"payfast"`.
  - The endpoint must identify the tenant from the webhook payload metadata (`tenantSchema` in Stripe metadata, `custom_str1` in PayFast ITN).
  - Set the tenant context (`RequestScopes.TENANT_ID`) before processing — use `ScopedValue.where().call()` pattern established in the ScopedValue migration.
  - Resolve the correct `PaymentGateway` adapter for the provider slug.
  - Call `adapter.handleWebhook(payload, headers)` to validate and parse the event.
  - If the event is a successful payment:
    1. Look up the invoice by ID from the webhook metadata.
    2. Call `InvoiceService.recordPayment()` with the PSP payment reference — this triggers the existing SENT → PAID transition, `InvoicePaidEvent`, audit event, and notification.
    3. Update the invoice `paymentSessionId` status (see payment event log below).
  - If the event is a failed/expired payment: log the event, update payment event log, but do NOT change invoice status.
  - Return HTTP 200 to the PSP immediately (before processing if possible — consider async processing for reliability).
- **Idempotency**: Webhooks may be delivered multiple times. The endpoint must be idempotent — if the invoice is already PAID, skip processing and return 200.
- **Webhook security**: Stripe uses HMAC signature verification. PayFast uses IP allowlist + signature + validation callback. Both must be enforced.
- **Payment event log** — Create a `PaymentEvent` entity (tenant-scoped):
  - `UUID id`
  - `UUID invoiceId` (FK to Invoice)
  - `String providerSlug` — "stripe", "payfast", "manual"
  - `String sessionId` — PSP session identifier (nullable for manual)
  - `String paymentReference` — PSP transaction ID
  - `PaymentEventStatus status` — enum: `CREATED`, `PENDING`, `COMPLETED`, `FAILED`, `EXPIRED`, `CANCELLED`
  - `BigDecimal amount`
  - `String currency`
  - `String paymentDestination` — "OPERATING" (v1 only)
  - `String providerPayload` — raw webhook payload (JSONB, for debugging/audit)
  - `Instant createdAt`
  - `Instant updatedAt`
- Every checkout session creation writes a `CREATED` event. Every webhook callback updates the event status. Manual payments also write a `COMPLETED` event with `providerSlug = "manual"`.

### 6. Portal Payment Flow

**Problem:** The portal invoice detail page shows invoice data but has no way for clients to pay.

**Requirements:**
- **Portal invoice detail page** (`portal/app/invoices/[id]/page.tsx`):
  - If the invoice has a `paymentUrl` and status is SENT: show a prominent "Pay Now" button.
  - Button redirects the client to the Stripe Checkout or PayFast payment page (external redirect, not iframe).
  - If status is PAID: show a "Paid" badge with payment date. No "Pay Now" button.
  - If status is DRAFT or APPROVED: no payment-related UI (invoice not yet sent).
  - If `paymentUrl` is null and status is SENT: show "Contact {org name} to arrange payment" (no PSP configured).
- **Success return page** (`portal/app/invoices/[id]/payment-success/page.tsx`):
  - After successful payment, Stripe/PayFast redirects to this page.
  - Shows a confirmation message: "Payment received — thank you!"
  - Displays invoice number, amount paid, and payment reference.
  - For Stripe: extract session ID from URL query param, call a portal backend endpoint to fetch payment status.
  - The invoice status may not yet be PAID (webhook is async) — poll or show "Payment is being processed" with a note that the invoice will update shortly.
- **Cancel return page** (`portal/app/invoices/[id]/payment-cancelled/page.tsx`):
  - If client cancels payment: show "Payment was cancelled. You can try again."
  - Show the "Pay Now" button again.
- **Portal backend extension**:
  - Extend `PortalInvoiceProjection` with `paymentUrl` (String, nullable).
  - Add `GET /api/portal/invoices/{id}/payment-status` — returns current payment status for the invoice (for polling after redirect).
  - The portal read-model event handler must sync `paymentUrl` and `paymentSessionId` when they're set on the invoice.

### 7. Integration Settings UI — Payment Provider Cards

**Problem:** The Integration Settings page has a PAYMENT card with no real provider options.

**Requirements:**
- Update the PAYMENT integration card on the Integrations Settings page:
  - **Default state** (no integration configured): Show status "Manual Payments Only" with a note: "Connect a payment provider to enable online invoice payments for your clients."
  - **Provider selector**: Dropdown with "Stripe" and "PayFast" options.
  - **Stripe configuration fields**:
    - API Secret Key: password input, stored via `SecretStore`. Label: "Secret Key (sk_live_... or sk_test_...)"
    - Webhook Signing Secret: password input, stored via `SecretStore`. Label: "Webhook Signing Secret (whsec_...)"
    - Webhook URL: read-only display of `{app-url}/api/webhooks/payment/stripe` — the URL the tenant configures in their Stripe dashboard.
    - "Test Connection" button: makes a test API call (e.g., `Balance.retrieve()`) to verify the key is valid.
  - **PayFast configuration fields**:
    - Merchant ID: text input, stored in `configJson`.
    - Merchant Key: text input, stored in `configJson`.
    - Passphrase: password input, stored via `SecretStore`.
    - Sandbox Mode: toggle (default on for safety). Label: "Use PayFast Sandbox for testing."
    - ITN Callback URL: read-only display of `{app-url}/api/webhooks/payment/payfast`.
    - "Test Connection" button: PayFast doesn't have a direct test API — show a note: "Send a test payment after saving to verify your configuration."
  - Follow the existing integration card pattern from Phase 21.
- **Payment status indicator on Invoice list page**: Add a small icon/badge next to invoices that have online payment enabled (i.e., `paymentUrl` is non-null). This helps tenants distinguish between invoices clients can pay online vs. invoices requiring manual payment.

### 8. Manual Payment Coexistence

**Problem:** Some clients pay by bank transfer, cheque, or cash. The manual "Record Payment" button must continue to work alongside online payment collection.

**Requirements:**
- The existing `POST /api/invoices/{id}/payment` endpoint with optional `{ paymentReference }` remains unchanged.
- If an invoice has an active checkout session (payment link generated) and the tenant records a manual payment:
  1. Mark the invoice as PAID via the existing flow.
  2. Mark the `PaymentEvent` for the checkout session as `CANCELLED` (the session is no longer needed).
  3. The Stripe/PayFast session expires naturally or is explicitly cancelled if the PSP supports it (Stripe: `Session.expire()`).
- The invoice detail page in the main app should show both options when a PSP is configured:
  - "Pay Now Link" — shows the payment URL (copyable) that was sent to the client.
  - "Record Manual Payment" — the existing button for recording offline payments.
- Write a `COMPLETED` payment event with `providerSlug = "manual"` when manual payment is recorded.

### 9. Audit Events

**Requirements:**
- Record audit events for:
  - `payment.session.created` — checkout session created for an invoice (includes provider, amount, currency)
  - `payment.completed` — payment received via webhook (includes provider, payment reference, amount)
  - `payment.failed` — payment failed via webhook (includes provider, error reason)
  - `payment.manual` — manual payment recorded (includes payment reference if provided)
  - `payment.session.expired` — checkout session expired without payment
  - `payment.integration.configured` — org configures a payment provider
  - `payment.integration.updated` — org changes provider settings
  - `payment.integration.removed` — org removes payment provider
- These build on the existing `AuditEvent` entity and `AuditService` from Phase 6.
- `InvoicePaidEvent` already triggers audit — ensure no duplication between the existing event and the new `payment.completed` event. The architect should decide whether to keep one or both.

### 10. Notifications

**Requirements:**
- Extend existing notifications for payment events:
  - On `payment.completed` (webhook): notify invoice creator and org admins — "Invoice {number} for {customer} was paid online via {provider}."
  - On `payment.failed`: notify org admins — "Online payment for Invoice {number} failed: {reason}."
  - On `payment.session.expired`: notify invoice creator — "Payment link for Invoice {number} has expired. You can regenerate it."
- The existing `INVOICE_PAID` notification type already fires on the SENT → PAID transition. Ensure the payment method (online vs. manual) is included in the notification detail.

## Out of Scope

- Partial payments, deposits, split payments
- Recurring payments / auto-charge on due date / card-on-file
- Payment plans or installment schedules
- Trust accounting ledger or trust-to-operating transfers (only the `paymentDestination` seam is included)
- Refunds or credit notes
- Multi-currency conversion (invoice currency = payment currency)
- Additional PSP adapters (Peach Payments, Ozow, etc. — the port interface is ready)
- Platform-managed PSP account (no "free tier" for payments — BYOAK only)
- Standalone payment pages outside the portal
- PCI compliance beyond SAQ-A (Stripe Checkout and PayFast hosted pages handle all card data)
- Payment receipts or confirmation emails from the platform (PSP sends their own receipts)

## ADR Topics

The architect should produce ADRs for:

1. **PaymentProvider vs PaymentGateway interface design** — how the new async payment flow coexists with the existing sync manual recording. Options: (a) two separate interfaces, (b) merge into one with both method sets, (c) deprecate `PaymentProvider` and consolidate. Consider backward compatibility, mock provider for testing, and the BYOAK resolution model where different orgs may have different providers.
2. **Webhook tenant identification for payments** — how the global webhook endpoint determines which tenant a payment event belongs to. The same problem was solved for email webhooks in Phase 24 (ADR-096). Options: reuse the same pattern (encode tenant schema in PSP metadata/custom fields), or use a global payment session lookup table. Consider that Stripe metadata has size limits and PayFast has only 5 custom string fields.
3. **Payment link lifecycle** — when payment links are generated, when they expire, and whether they're regenerated automatically. Stripe Checkout Sessions expire after 24h by default (configurable). PayFast URLs don't expire. Consider: should expired links auto-regenerate? Should there be a manual "Regenerate Link" button? What happens if the invoice amount changes after the link is generated?

## Style & Boundaries

- Follow the existing `IntegrationPort` / `@IntegrationAdapter` / `IntegrationRegistry` pattern for payment adapters (mirror the structure from Phase 21).
- The webhook endpoint follows the same pattern as Clerk webhooks and email webhooks (Phase 24) — global endpoint, tenant identification from payload metadata, `ScopedValue.where().call()` for tenant context.
- The portal payment flow must work with the existing magic link authentication — no additional auth required to pay.
- Frontend components follow existing patterns: integration settings card (Phase 21), invoice detail page (Phase 10/84), portal pages (Phase 22).
- The `PaymentEvent` entity follows the standard tenant-scoped entity pattern.
- Stripe Java SDK (`com.stripe:stripe-java`) is a new Maven dependency.
- PayFast integration uses Spring's `RestClient` — no additional HTTP client dependency.
- All new Flyway migrations go in the tenant schema (next available version after Phase 24).
- The `paymentDestination` field is a simple VARCHAR with default `'OPERATING'` — not a full enum entity. The legal fork adds values and routing logic.
