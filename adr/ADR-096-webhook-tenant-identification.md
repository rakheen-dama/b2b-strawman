# ADR-096: Webhook Tenant Identification

**Status**: Accepted

**Context**:

SendGrid's Event Webhook sends delivery status events (delivered, bounced, dropped, deferred) to a single configured URL. In DocTeams' multi-tenant architecture (schema-per-tenant), the webhook handler must determine which tenant schema an email event belongs to in order to update the correct `EmailDeliveryLog` row. The challenge: webhook payloads arrive without tenant context, and the `EmailDeliveryLog` table exists in each tenant's schema — there is no cross-schema index.

The decision is: how does the webhook endpoint identify the tenant for each event in the payload?

**Options Considered**:

1. **Encode tenant schema in webhook URL path (per-tenant webhook URLs)** — Each org's SendGrid account would be configured with a unique webhook URL like `/api/webhooks/email/sendgrid/{tenantSchema}`.
   - Pros:
     - Tenant identification is immediate — no database lookup needed.
     - Simple implementation: extract from URL path parameter.
   - Cons:
     - URL proliferation: each BYOAK org needs a unique webhook URL displayed in their settings.
     - Security risk: the tenant schema name is exposed in a public URL. Even though webhook signatures prevent forgery, the schema name is sensitive metadata.
     - For platform SMTP (where SendGrid is the platform's own account), all tenants share one SendGrid account with one webhook URL — there's no per-tenant URL.
     - If a BYOAK org changes their webhook URL, in-flight events may be lost.

2. **Encode tenant schema in SendGrid `unique_args` metadata (chosen)** — When sending via `SendGridEmailProvider`, include the tenant schema as a custom argument in the SendGrid API request (`unique_args: { "tenantSchema": "tenant_abc" }`). SendGrid includes `unique_args` in webhook event payloads. For platform SMTP: include tenant schema in the email's `X-Tenant-Schema` header and store it in `EmailDeliveryLog.metadata` — but SMTP webhooks are not supported, so this only matters for SendGrid.
   - Pros:
     - Single webhook URL for all tenants (platform or BYOAK).
     - Tenant schema travels with the email event — no lookup needed.
     - Works for both platform SendGrid and BYOAK SendGrid.
     - No sensitive data in URLs.
     - `unique_args` is a standard SendGrid feature designed for this purpose.
   - Cons:
     - Only works with SendGrid (SMTP has no webhook mechanism). For SMTP, delivery status stays at `SENT` — no delivery/bounce confirmation.
     - `unique_args` values are included in webhook payloads in plaintext. The tenant schema name is not highly sensitive (it's an internal identifier like `tenant_abc`), but it is visible to anyone who intercepts the webhook payload. Mitigation: webhook signature verification ensures only SendGrid can send valid payloads.
     - Requires careful serialization: `unique_args` must be a flat JSON object with string values.

3. **Global (non-tenant-scoped) delivery log table** — Create `email_delivery_log` in the `public` schema instead of tenant schemas. The webhook handler queries a single global table without needing tenant context.
   - Pros:
     - Simplest webhook handler: no tenant context switching, just a global query.
     - `providerMessageId` lookup is a single-table index scan.
   - Cons:
     - Breaks the schema-per-tenant isolation model — the core architectural principle of the platform (ADR-064).
     - Global table requires a `tenant_schema` column and careful access control to prevent cross-tenant data leakage.
     - All email delivery queries (admin dashboard, stats) would need to filter by `tenant_schema` manually instead of relying on schema isolation.
     - Sets a bad precedent: if the delivery log is global, future features might also bypass schema isolation "for convenience."

4. **`providerMessageId` cross-schema lookup** — Keep the delivery log tenant-scoped. On webhook receipt, iterate over all tenant schemas to find the `EmailDeliveryLog` row matching the `providerMessageId`.
   - Pros:
     - No changes to the email sending flow (no metadata encoding).
     - Delivery log stays fully tenant-scoped.
   - Cons:
     - O(N) schema searches where N is the number of tenants. Unacceptable at scale.
     - Requires listing all tenant schemas and connecting to each one — expensive and slow.
     - Race conditions if the delivery log is being written concurrently.

**Decision**: Option 2 — Encode tenant schema in SendGrid `unique_args` metadata.

**Rationale**:

Option 2 provides tenant identification with zero additional database lookups and no cross-schema queries. SendGrid's `unique_args` feature is purpose-built for this use case — attaching application-specific metadata to email events. The metadata travels with the event through SendGrid's pipeline and is included in every webhook payload, making tenant identification immediate and reliable.

Option 1 was rejected because it exposes the tenant schema in public URLs and doesn't work for the platform SMTP case (single SendGrid account, multiple tenants). Option 3 was rejected because it violates the schema-per-tenant isolation model that is the core architectural invariant of the platform. Introducing a global table for email delivery would set a precedent for bypassing tenant isolation. Option 4 was rejected because cross-schema iteration is O(N) and does not scale.

For platform SMTP (JavaMailSender, not SendGrid): there are no webhooks, so delivery status stays at `SENT`. This is an acceptable limitation — platform SMTP is the free tier. Orgs that need delivery confirmation should use BYOAK SendGrid.

The webhook handler flow:
1. Verify webhook signature.
2. For each event in the payload, extract `tenantSchema` from `unique_args`.
3. Set the tenant context via `ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)`.
4. Query `EmailDeliveryLog` by `providerMessageId` within the tenant schema.
5. Update delivery status.

**Consequences**:

- Positive:
  - Single webhook URL: `/api/webhooks/email/sendgrid` — no per-tenant URLs.
  - Tenant identification is O(1) per event — no database lookups or schema iteration.
  - Works for both platform SendGrid and BYOAK SendGrid accounts.
  - Delivery log remains fully tenant-scoped, preserving schema isolation.

- Negative:
  - Platform SMTP has no delivery/bounce tracking (stays at SENT status). Acceptable for the free tier.
  - Tenant schema name is included in SendGrid `unique_args` (visible in webhook payloads). Low risk: payloads are signature-verified and transit over HTTPS.

- Neutral:
  - `SendGridEmailProvider` must include `unique_args` in every API request. Minor code addition.
  - If a future email provider also supports webhooks, a similar metadata-encoding approach will be needed (provider-specific implementation).

- Related: [ADR-064](ADR-064-dedicated-schema-for-all-tenants.md) (schema-per-tenant isolation — must not be violated), [ADR-095](ADR-095-two-tier-email-resolution.md) (two-tier provider resolution).
