# ADR-275: OAuth2 Augmentation of `OrgIntegration` via Sibling Table + Named `SecretStore` Keys

**Status**: Accepted

**Date**: 2026-05-03

**Context**: Phase 21's integration plumbing models *single-API-key* providers — `IntegrationKeys.apiKey(domain, slug)` returns a single string key (`"accounting:xero:api_key"`) and `OrgIntegration` carries a single `key_suffix` for display. Xero is OAuth2: per-tenant connections require `client_id`, `client_secret` (platform-level), `access_token` (rotates ~30min), `refresh_token` (long-lived), `xero_tenant_id` (Xero's UUID for the connected Xero org — distinct from Kazi tenant), `scope`, `access_token_expires_at`. The single-key model does not fit.

A second concern: not every future provider will be OAuth2. Email-SMTP, KYC vendors, and PayFast remain API-key. We don't want to bloat `OrgIntegration` with OAuth-specific columns if 80% of providers don't use them.

A third concern: refresh-token rotation. `SecretStore.store(key, plaintext)` overwrites — perfect for token rotation. Rotation must be atomic with the connection-row update so that the live token always matches the recorded `access_token_expires_at`.

**Options Considered**:

1. **Sibling `accounting_xero_connection` table + tokens in `SecretStore` under named keys.** A new tenant-scoped table holds non-secret connection metadata (`xero_tenant_id`, `xero_org_name`, `status`, `last_token_refresh_at`, `access_token_expires_at`, `scope`, `refresh_failure_count`, `last_poll_at`, `customer_import_completed_at`); tokens go in `SecretStore` under named keys (`accounting:xero:access_token`, `…:refresh_token`, `…:client_id`, `…:client_secret`). `OrgIntegration` is unchanged.
   - Pros: `OrgIntegration` stays lean — non-OAuth providers don't pay for OAuth columns.
   - Pros: `xero_tenant_id` and other Xero-specific fields are typed and queryable, not buried in JSONB.
   - Pros: `SecretStore.store()` semantics make refresh-token rotation atomic at the secret level.
   - Pros: Future OAuth providers (Google Calendar, Slack, etc) get their own sibling table — clean precedent.
   - Cons: One more table per OAuth provider.

2. **Extend `OrgIntegration` with OAuth-specific columns.** Add `external_tenant_id`, `access_token_expires_at`, `scope`, `refresh_failure_count`, etc. to `OrgIntegration`.
   - Pros: One table.
   - Cons: 80% of `OrgIntegration` rows (non-OAuth providers) carry null OAuth columns.
   - Cons: Provider-specific columns leak into a generic table. `xero_tenant_id` is meaningless to Stripe.
   - Cons: Schema migration required for every future OAuth provider with a new field.

3. **Generic `OAuthConnection` table for any OAuth provider.** A single table with provider-id discriminator and JSONB payload for provider-specific fields.
   - Pros: One table for all OAuth providers, future-proof for Google / Slack / etc.
   - Cons: Provider-specific fields (`xero_tenant_id`, future Google `calendar_id`) buried in JSONB — not typed, not queryable without `->`/`->>`.
   - Cons: Premature generalisation. We have one OAuth provider in v1.
   - Cons: Refresh logic and connection-status transitions still need to be provider-specific in code, so the "shared table" provides no real abstraction.

**Decision**: Option 1 — sibling `accounting_xero_connection` table for non-secret connection metadata; tokens in `SecretStore` under named keys (`accounting:xero:client_id`, `…:client_secret`, `…:access_token`, `…:refresh_token`); `OrgIntegration` unchanged.

**Rationale**: The Phase 21 model is good; we extend it instead of rewriting it. `OrgIntegration` continues to model "is this provider configured and enabled?" — the sibling table models "OAuth state for the configured Xero provider." This separation also makes the model amenable to future OAuth providers (each gets its own typed sibling table — no JSONB-search debt) and keeps non-OAuth providers free of dead columns.

`SecretStore` already supports multiple secrets per provider via key naming (the existing `IntegrationKeys.apiKey(domain, slug)` is one of N possible keys; nothing prevents `accounting:xero:refresh_token`). The `EncryptedDatabaseSecretStore` AES/GCM encryption applies uniformly. `store()`'s overwrite semantics make refresh-token rotation a single atomic call.

The `client_id` and `client_secret` are platform-level (one Xero app servicing all Kazi tenants in v1), so they could plausibly live in environment variables. We choose to store them in `SecretStore` per-tenant anyway, on the basis that (a) it costs nothing extra; (b) it preserves the option of moving to per-tenant Xero apps later without a migration.

The three-strike refresh-failure rule is encoded in the connection-row state machine (`refresh_failure_count` increments on each refresh failure; at 3 → `status='REFRESH_FAILED'`, sync paused, banner shown).

**Consequences**:

- Positive: `OrgIntegration` stays lean and provider-agnostic.
- Positive: Xero-specific connection metadata is typed and queryable.
- Positive: Refresh-token rotation is atomic via `SecretStore.store()`.
- Positive: Future OAuth providers (Google Calendar etc.) get their own sibling table — clear precedent.
- Negative: One more table per OAuth provider. Acceptable cost for the typing benefit.
- Negative: Two writes (one to `accounting_xero_connection`, one or more to `SecretStore`) on connect — must be inside a single `@Transactional` block. Documented and tested.
- Neutral: Disconnect leaves the connection row in `status='REVOKED'` rather than deleting; sync entries stay as historical record. Audit trail intact.
- Neutral: One-shot `customer_import_completed_at` field lives on the connection row, so reconnect (= new row) gives a fresh import opportunity.

**Related**: [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md) (provider choice), [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (sync engine), [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md) (per-tenant schema), [ADR-201](ADR-201-integration-guard-service.md) (Phase 21 module-level gate), [ADR-148](ADR-148-jsonb-sealed-class-config.md) (sealed-record `OrgIntegration.config_json`).
