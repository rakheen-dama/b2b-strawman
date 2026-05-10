# ADR-275: OAuth2 Augmentation of OrgIntegration + SecretStore

**Status**: Accepted

**Context**:

Phase 21 established the `OrgIntegration` entity and `SecretStore` interface for managing tenant integration credentials. The existing model handles API-key-based integrations well: the tenant enters an API key, it is encrypted via `SecretStore.store()`, the last 6 characters are saved as `keySuffix` on `OrgIntegration` for display, and the key is retrieved at runtime via `SecretStore.retrieve()`. This model works for Stripe (payment), SendGrid (email), and other providers that use static API keys.

Xero uses OAuth2 with a refresh-token flow, which introduces credential lifecycle concerns that API keys do not have:

- **Access tokens expire.** Xero access tokens have a 30-minute TTL. Every API call must check whether the token is expired and, if so, refresh it before proceeding.
- **Refresh tokens rotate.** Each token refresh returns a new refresh token; the old one is invalidated. If a refresh token is lost or not persisted after rotation, the connection is permanently broken and the tenant must re-authenticate.
- **Connection metadata is richer.** An OAuth2 connection carries a Xero tenant ID (Xero's UUID for the connected Xero org, distinct from the Kazi tenant), granted scopes, the connected user, and a status that can degrade (CONNECTED -> REFRESH_FAILED -> REVOKED).
- **Re-authentication is a UX event.** When refresh fails (revoked consent, Xero-side disconnection), the integration must surface a "Reconnect required" state and pause sync until the tenant re-authenticates. API-key integrations never need this.

The question is how to store and manage OAuth2 credentials within the existing Phase 21 infrastructure without breaking API-key-based integrations.

**Options Considered**:

1. **Add OAuth2 fields directly to `OrgIntegration`** — Extend the `org_integrations` table with columns for `access_token_expires_at`, `xero_tenant_id`, `oauth_status`, `last_token_refresh_at`, `connected_by_member_id`, `granted_scopes`, and `disconnected_at`. Tokens (access and refresh) remain in `SecretStore`.
   - Pros:
     - No new entity or table. All integration state is on one entity. Queries for "is this integration connected and healthy?" are a single row lookup.
     - The `OrgIntegration` entity already has `configJson` (JSONB) which could hold some of this metadata without new columns.
   - Cons:
     - Pollutes the `OrgIntegration` entity with Xero-specific fields. API-key integrations (Stripe, SendGrid) would have null `xero_tenant_id`, null `oauth_status`, null `access_token_expires_at`. The entity becomes a union type — some fields apply to OAuth2 providers, others to API-key providers, and the developer must know which fields to check for which provider type.
     - `OrgIntegration` is a shared entity across all integration domains (PAYMENT, EMAIL, ACCOUNTING, AI, DOCUMENT_SIGNING, KYC_VERIFICATION). Adding 7+ OAuth2-specific columns to a table used by all domains violates the single-responsibility principle.
     - The `xero_tenant_id` is Xero-specific, not OAuth2-generic. If a future provider (Sage Pastel) also uses OAuth2 but has different connection metadata (e.g., a `company_id` instead of a `tenant_id`), the column naming becomes confusing or a second set of provider-specific columns is needed.
     - The `configJson` JSONB fallback is tempting but creates a dual-schema problem: some metadata in typed columns, some in JSONB, and the developer must know which fields live where.

2. **New `accounting_xero_connection` table linked to `OrgIntegration` (CHOSEN)** — A dedicated tenant-scoped table that stores Xero-specific OAuth2 connection metadata. The `OrgIntegration` entity remains unchanged (it tracks the integration domain, provider slug, enabled/disabled, and config). The connection table holds `xero_tenant_id`, `xero_org_name`, `connected_by_member_id`, `connected_at`, `last_token_refresh_at`, `access_token_expires_at`, `scope`, `status` (CONNECTED, REFRESH_FAILED, REVOKED), and `disconnected_at`. Tokens (access and refresh) live in `SecretStore` keyed by `{orgIntegrationId}:xero:access` and `{orgIntegrationId}:xero:refresh`.
   - Pros:
     - Clean separation of concerns. `OrgIntegration` remains a generic, domain-agnostic entity. `AccountingXeroConnection` holds Xero-specific state. API-key integrations are unaffected — they have no connection table row.
     - The connection entity models the OAuth2 lifecycle explicitly: CONNECTED (healthy, tokens valid or refreshable), REFRESH_FAILED (refresh attempts exhausted, sync paused, reconnect needed), REVOKED (user disconnected or Xero-side revocation). This state machine is visible in the entity, not hidden in JSONB or inferred from null/non-null fields.
     - Provider-specific without being hacky. The table is named `accounting_xero_connection`, not `oauth2_connection`. If Sage Pastel needs a connection table in Phase 72+, it gets `accounting_sage_connection` with its own provider-specific metadata. No shared schema to negotiate.
     - Refresh tokens in `SecretStore` follow the established pattern ([ADR-201](ADR-201-secret-store-reuse-for-ai-keys.md)): one encryption implementation, one key-rotation path, tenant-scoped by schema isolation. The connection table stores non-sensitive metadata (Xero org name, scopes, timestamps); the sensitive material (tokens) is in the secret store.
     - The FK to `OrgIntegration.id` (unique constraint) ensures a 1:1 relationship. The integration registry resolves the adapter via `OrgIntegration`; the adapter looks up the connection table for Xero-specific metadata. Clean join path.
   - Cons:
     - One more entity, one more table, one more migration. The `accounting_xero_connection` table is tenant-scoped (per [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md)), created in V121 alongside `accounting_sync_entry` and `accounting_tax_code_mapping`.
     - Retrieving full integration state requires a join: `OrgIntegration` + `AccountingXeroConnection` + `SecretStore.retrieve()`. This is three lookups for "is the Xero connection healthy and what are the credentials?" In practice, the adapter caches the connection state and only calls `SecretStore.retrieve()` when making an API call, so the join cost is negligible.
     - If multiple providers adopt OAuth2, each gets its own connection table. This is intentional (provider-specific metadata is genuinely different) but means N tables for N OAuth2 providers rather than one generic OAuth2 table. Acceptable for the expected provider count (2–3 over the product lifetime).

3. **Generic `oauth2_connection` table for all OAuth2 providers** — A single table that stores OAuth2 connection metadata for any provider: `provider_slug`, `external_tenant_id`, `external_org_name`, `status`, `scopes`, timestamps. Provider-specific metadata goes in a JSONB `metadata` column.
   - Pros:
     - One table for all OAuth2 providers. Adding Sage Pastel OAuth2 in Phase 72+ does not require a new migration — just a new row with `provider_slug=sage-pastel`.
     - Schema is normalised: all OAuth2 connections in one place, queryable across providers ("show all OAuth2 connections for this tenant").
   - Cons:
     - Premature abstraction. Phase 71 ships one OAuth2 provider (Xero). Designing a generic table for multiple providers adds design complexity (what goes in typed columns vs JSONB? What scope format works for all providers? What status values are universal?) without a second provider to validate the abstraction.
     - Provider-specific metadata varies. Xero has a `xero_tenant_id` (a UUID that identifies the connected Xero org, required as a header on every API call). Sage Pastel might have a `company_id` (an integer). A generic `external_tenant_id` column must be typed as `VARCHAR` to accommodate both, losing type information. Or the differences go in JSONB, which creates the dual-schema problem.
     - The generic table name `oauth2_connections` does not communicate domain intent. A developer looking at the schema sees a generic infrastructure table, not a business table. `accounting_xero_connection` immediately communicates what it is.
     - If the generic table needs provider-specific indexes (e.g., `(xero_tenant_id)` for Xero header lookup, `(company_id)` for Sage Pastel), the indexes become conditional on `provider_slug` — awkward and potentially slow.
     - The YAGNI principle applies. Build the abstraction when you have two concrete implementations to abstract over, not before. One Xero connection table now; if Sage Pastel arrives in Phase 72+ and the tables are similar enough, extract a shared base at that time.

**Decision**: Option 2 — New `accounting_xero_connection` table linked to `OrgIntegration` via FK, with refresh and access tokens stored in `SecretStore`. The `OrgIntegration` entity is not modified.

**Rationale**:

The decision follows the principle of provider-specific connection tables over premature generalisation. Phase 21's `OrgIntegration` + `SecretStore` was designed for API-key integrations: a single encrypted credential per integration. OAuth2 introduces a richer lifecycle (token expiry, refresh rotation, consent revocation, connection status degradation) that does not fit into the API-key model without awkward null-heavy columns or JSONB-in-lieu-of-schema compromises.

A dedicated `accounting_xero_connection` table models the OAuth2 lifecycle as first-class columns: `status` tracks the connection health (CONNECTED / REFRESH_FAILED / REVOKED), `access_token_expires_at` drives the refresh-on-401 strategy in `XeroApiClient`, `last_token_refresh_at` provides operational visibility, and `xero_tenant_id` is available for the mandatory `Xero-tenant-id` header without a `SecretStore` lookup. The connection entity is a clean, typed, queryable model of the Xero integration state — not a JSONB bag.

The token storage strategy is simple and consistent with [ADR-201](ADR-201-secret-store-reuse-for-ai-keys.md): access and refresh tokens are stored in `SecretStore` with keys `{orgIntegrationId}:xero:access` and `{orgIntegrationId}:xero:refresh`. The `SecretStore` handles encryption (AES-256-GCM), tenant isolation (schema-per-tenant), and key rotation. The connection table stores non-sensitive metadata only — a compromise that keeps the entity readable in logs and debug output without exposing credentials.

The refresh-on-401 strategy in `XeroApiClient` works as follows: every Xero API call checks `access_token_expires_at` from the cached connection state. If expired (or within a 60-second grace window), refresh proactively before the call. If the call returns 401 despite a valid-looking token (clock skew, server-side revocation), refresh once and retry. If refresh fails three consecutive times across calls, mark the connection REFRESH_FAILED, pause outbound sync (entries queue but do not push), and surface a "Reconnect required" banner on the integration card. This strategy is co-located with the Xero adapter, not in the generic integration infrastructure.

**Consequences**:

- Positive:
  - `OrgIntegration` remains unchanged. API-key integrations (Stripe, SendGrid, etc.) are unaffected by Phase 71. No migration needed on the shared entity.
  - The connection entity provides a clear, typed model of Xero OAuth2 state. The `status` column directly drives UI state (connected / reconnect required / disconnected) without inference.
  - Tokens in `SecretStore` follow the established encryption and isolation patterns. No new encryption code, no new secret storage mechanism.
  - The connection's `xero_tenant_id` is a typed UUID column, not a JSONB field. It can be indexed, queried, and validated at the schema level.
  - Refresh-on-401 is encapsulated in `XeroApiClient`. Other adapters (future Sage Pastel) can implement their own token-refresh strategy without affecting the Xero adapter.

- Negative:
  - One additional table and entity to maintain. The `accounting_xero_connection` table adds a Flyway migration (V121), a JPA entity, and a repository. This is a small but real maintenance cost.
  - Full integration state requires looking at three sources: `OrgIntegration` (domain, slug, enabled), `AccountingXeroConnection` (status, xero_tenant_id, timestamps), and `SecretStore` (tokens). In practice, the `XeroAccountingProvider` encapsulates this lookup and exposes a simple `isConnected()` / `getCredentials()` surface to the sync service.
  - If multiple OAuth2 providers are added in future phases, each gets its own connection table. For the expected provider count (2–3), this is manageable. If 10+ providers were expected, a generic table (Option 3) would be more appropriate — but that is not the trajectory for this product.

- Neutral:
  - Per [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md), `accounting_xero_connection` is a tenant-scoped table in each `tenant_<hash>` schema. No global table, no `tenant_id` column, no RLS policy.
  - The `connected_by_member_id` column records which team member initiated the OAuth2 consent flow. This is audit data — it answers "who connected our Xero?" without requiring a lookup against the audit-event log.
  - The `scope` column persists the granted OAuth2 scopes as a comma-separated string. If Xero ever adds new scopes that Kazi needs, the reconnect flow can request them and the stored scopes reflect the upgrade.

- Related: [ADR-088](ADR-088-integration-port-package-structure.md) (integration port structure — `OrgIntegration` entity), [ADR-201](ADR-201-secret-store-reuse-for-ai-keys.md) (SecretStore reuse for credentials), [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md) (Xero-only adapter), [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (dedicated sync service — consumes connection state), [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md) (schema-per-tenant isolation).
