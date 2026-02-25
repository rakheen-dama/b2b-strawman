# ADR-099: Webhook Tenant Identification for Payments

**Status**: Accepted

**Context**:

Phase 25 adds Stripe and PayFast payment collection. Both PSPs deliver payment results via webhooks to a global endpoint (`POST /api/webhooks/payment/{provider}`). DocTeams uses schema-per-tenant isolation (ADR-064), so the webhook handler must determine which tenant schema an incoming payment event belongs to before it can look up the invoice and update payment status.

This is the same class of problem solved in Phase 24 for email delivery tracking (ADR-096), where SendGrid webhook events needed tenant identification. That ADR chose to encode the tenant schema in SendGrid's `unique_args` metadata, avoiding database lookups and preserving schema isolation.

For payments, the situation has two provider-specific considerations: (1) Stripe supports arbitrary `metadata` on Checkout Sessions (up to 50 keys, 500-char values), and metadata is included in webhook event payloads; (2) PayFast has 5 `custom_str` fields (custom_str1 through custom_str5) that are echoed back in ITN (Instant Transaction Notification) callbacks. Both mechanisms allow encoding tenant identifiers in the outbound request and receiving them back in the webhook.

**Options Considered**:

1. **PSP metadata/custom fields (chosen)** -- Encode `tenantSchema` in Stripe `metadata` and PayFast `custom_str1` when creating the checkout session. The webhook handler extracts the tenant identifier from the payload without any database lookup. Each PSP adapter is responsible for writing and reading its own metadata format.
   - Pros:
     - Zero additional database queries on the webhook path. Tenant identification is O(1).
     - Consistent with ADR-096 (same pattern as email webhook tenant identification). Proven approach.
     - Works with any number of tenants -- no scaling concerns.
     - No cross-schema tables or queries. Schema isolation is preserved.
     - Stripe metadata and PayFast custom_str fields are designed for this purpose.
   - Cons:
     - Tenant schema name is visible in PSP dashboards and webhook payloads. Low risk: payloads are signature-verified, and the schema name is an internal identifier (e.g., `tenant_abc`), not a secret.
     - Each adapter must remember to encode metadata when creating sessions. If an adapter omits it, the webhook handler has no fallback. Mitigation: `CheckoutRequest` includes a `metadata` map that the calling service always populates with `tenantSchema`.
     - Stripe metadata has a 50-key limit and 500-char value limit per key. Tenant schema names are short (~30 chars), so this is not a practical concern.

2. **Global payment session lookup table** -- Create a `payment_session_lookup` table in the global/public schema with columns `(session_id, tenant_schema, invoice_id, provider_slug, created_at)`. When a checkout session is created, write a row. When a webhook arrives, look up the tenant by `session_id`.
   - Pros:
     - Provider-agnostic: works identically for Stripe, PayFast, and any future PSP.
     - No sensitive data encoded in PSP metadata.
     - Lookup is a simple indexed query on `session_id`.
   - Cons:
     - Introduces a global (non-tenant-scoped) table, violating the schema-per-tenant principle (ADR-064). This is the same concern that rejected Option 3 in ADR-096.
     - Requires write-on-session-create and read-on-webhook -- two additional database operations per payment flow.
     - Table grows indefinitely (one row per checkout session). Requires cleanup/retention policy.
     - If the write fails (or is missed due to a race/crash), the webhook has no fallback.
     - Sets precedent for global tables that erode tenant isolation.

3. **URL-encoded tenant identifier** -- Include the tenant schema in the webhook URL path: `/api/webhooks/payment/stripe/{tenantSchema}`. Each org configures their PSP webhook URL with their tenant schema embedded.
   - Pros:
     - Tenant identification is immediate from the URL path. No payload parsing needed.
     - Simple implementation: Spring `@PathVariable`.
   - Cons:
     - Each BYOAK org must configure a unique webhook URL in their Stripe/PayFast dashboard. The webhook URL displayed in Integration Settings must include the org's schema name.
     - Tenant schema name is exposed in a public URL. While webhook signature verification prevents forgery, the schema name is visible in PSP dashboards, server access logs, and any monitoring tools.
     - Rejected in ADR-096 (Option 1) for email webhooks for the same reasons.
     - If an org reconfigures their integration (rare), the old webhook URL may still receive events with the wrong tenant context.

**Decision**: Option 1 -- Encode tenant schema in PSP metadata/custom fields.

**Rationale**:

This decision follows the precedent set by ADR-096 for email webhooks. The pattern is proven, preserves schema isolation, and requires zero database lookups on the webhook path. Stripe's `metadata` and PayFast's `custom_str` fields are purpose-built for attaching application context to payment flows. The tenant schema name is an internal identifier (not a user-facing value or secret), and webhook payloads are protected by signature verification (Stripe HMAC, PayFast IP allowlist + signature + validation callback).

Option 2 (global lookup table) was rejected because it violates the schema-per-tenant isolation model. ADR-064 established this as the core architectural invariant, and ADR-096 already rejected global tables for the same use case. Adding a global table for payments would erode the principle further.

Option 3 (URL encoding) was rejected for the same reasons as ADR-096 Option 1: it exposes internal identifiers in public URLs and requires per-org webhook URL configuration, adding friction to the integration setup.

**Implementation sketch**:

```java
// Stripe: encode in Checkout Session metadata
SessionCreateParams params = SessionCreateParams.builder()
    .putMetadata("tenantSchema", request.metadata().get("tenantSchema"))
    .putMetadata("invoiceId", request.invoiceId().toString())
    // ...
    .build();

// Stripe webhook: extract from event metadata
Session session = (Session) event.getDataObjectDeserializer().getObject().orElseThrow();
String tenantSchema = session.getMetadata().get("tenantSchema");

// PayFast: encode in custom_str fields
formData.put("custom_str1", request.metadata().get("tenantSchema"));
formData.put("custom_str2", request.invoiceId().toString());

// PayFast ITN: extract from callback params
String tenantSchema = itnParams.get("custom_str1");
String invoiceId = itnParams.get("custom_str2");

// Webhook controller: set tenant context
ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema).call(() -> {
    processPaymentEvent(provider, webhookResult);
    return null;
});
```

**Consequences**:

- Positive:
  - Consistent with ADR-096 (email webhook tenant identification). One pattern for all webhook-based tenant identification.
  - O(1) tenant identification per webhook event. No database lookups.
  - Schema isolation fully preserved. No global tables.
  - Works for both Stripe and PayFast with provider-specific metadata encoding.

- Negative:
  - Tenant schema name is embedded in PSP metadata. Visible in Stripe dashboard events and PayFast ITN payloads. Low risk: internal identifier, payloads are signature-verified.
  - If an adapter omits the metadata (bug), the webhook handler cannot identify the tenant. Mitigation: `CheckoutRequest.metadata` is populated by `PaymentLinkService` before calling the adapter, and the adapter is responsible for encoding it in the PSP-specific format.

- Neutral:
  - PayFast has 5 custom_str fields; this design uses 2 (tenantSchema + invoiceId). Three fields remain available for future use.
  - Stripe metadata allows 50 keys; this design uses 2. Ample room for future expansion.

- Related: [ADR-096](ADR-096-webhook-tenant-identification.md) (email webhook tenant identification -- same pattern), [ADR-064](ADR-064-dedicated-schema-for-all-tenants.md) (schema-per-tenant isolation invariant).
