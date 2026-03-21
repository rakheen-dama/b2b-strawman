# ADR-201: SecretStore Reuse for AI API Keys

**Status**: Accepted
**Date**: 2026-03-21
**Phase**: 52 (In-App AI Assistant — BYOAK)

## Context

Phase 52's BYOAK (Bring Your Own API Key) model requires each tenant to store their own Anthropic API key securely. The key is entered via the existing AI integration card on the Settings page, used at runtime by `AssistantService` to authenticate requests to the Anthropic Messages API, and must never be logged or exposed in API responses.

The platform already has secret storage infrastructure from Phase 21: `SecretStore` interface with `EncryptedDatabaseSecretStore` implementation, backed by the `OrgSecret` entity (`org_secrets` table, created in V36). Secrets are encrypted with AES-256-GCM, include an IV and key version for rotation support, and are tenant-scoped by schema isolation (each tenant's `org_secrets` table lives in their dedicated schema). The `IntegrationService.setApiKey(domain, apiKey)` method already handles the flow: encrypt via `SecretStore.store()`, save the last 6 characters as `keySuffix` on `OrgIntegration` for display, and emit an audit event.

The question is whether the AI API key should use this existing infrastructure or a dedicated storage mechanism.

## Options Considered

### Option 1: Reuse Existing SecretStore/OrgSecret (Selected)

Store the AI API key via `SecretStore.store("ai:anthropic:api_key", plaintext)`, following the existing secret key naming convention (`"{domain}:{provider}:api_key"`). Retrieve at chat time via `SecretStore.retrieve("ai:anthropic:api_key")`. No new entity, no new migration, no new encryption code.

- **Pros:**
  - **Zero new infrastructure:** The `org_secrets` table, `EncryptedDatabaseSecretStore`, `SecretStore` interface, and `IntegrationService.setApiKey()` all exist and are tested. The AI key is just another row in `org_secrets`
  - **Single encryption implementation:** AES-256-GCM encryption is implemented once in `EncryptedDatabaseSecretStore`. No risk of a second, potentially weaker encryption implementation
  - **Key rotation support:** `OrgSecret.updateEncryptedValue(encryptedValue, iv, keyVersion)` supports key rotation. If the platform master key is rotated, all secrets (including AI keys) are re-encrypted in one pass
  - **Tenant isolation by schema:** Each tenant's `org_secrets` table is in their dedicated schema. No `tenant_id` column needed, no filter to forget. A query in schema `tenant_abc` can only see `tenant_abc`'s secrets
  - **Audit trail exists:** `IntegrationService.setApiKey()` already emits `integration.key_set` audit events. The AI key gets the same audit treatment as payment gateway keys and email API keys
  - **Display flow exists:** `OrgIntegration.keySuffix` stores the last 6 characters for safe display ("...abc123"). The frontend `IntegrationCard` already shows this suffix. No new UI work for key display

- **Cons:**
  - **Shared table:** All integration secrets (payment, email, accounting, AI, signing) share the `org_secrets` table. A bug in `EncryptedDatabaseSecretStore` that corrupts data affects all domains, not just AI
  - **No domain-specific metadata:** `OrgSecret` stores only `secretKey`, `encryptedValue`, `iv`, and `keyVersion`. There is no field for metadata like "last validated at" or "daily token budget." If AI-specific metadata is needed, it must be stored elsewhere (e.g., `OrgIntegration.configJson`)
  - **Retrieval on every chat request:** `SecretStore.retrieve()` decrypts on every call. For frequent chat usage, this means repeated AES decryption. Not a performance concern in practice (AES-256-GCM decryption is sub-millisecond) but worth noting

### Option 2: Dedicated AiKeyEncryptionService with Separate Table

Create a new `AiKeyEncryptionService` with its own `ai_api_keys` table, separate encryption logic, and AI-specific metadata fields (provider, model, last validated timestamp, daily token usage).

- **Pros:**
  - AI key storage is self-contained — changes to AI key management don't risk affecting other integration secrets
  - Can include AI-specific metadata (provider, model preference, last validated, token budget) directly on the key record
  - Can optimize for chat access patterns (e.g., cache the decrypted key in memory for the session duration)

- **Cons:**
  - **Duplicate encryption code:** Must implement AES-256-GCM encryption a second time (or extract a shared utility from `EncryptedDatabaseSecretStore`). Two encryption implementations means two places to audit for security vulnerabilities
  - **New migration:** Requires a new tenant migration (V83) for the `ai_api_keys` table. The table would contain exactly one row per tenant (one AI key). A single-row table is a code smell — it's configuration, not entity data
  - **Divergent key rotation:** If the platform master key is rotated, the rotation script must know about both `org_secrets` and `ai_api_keys`. Easy to forget the second table
  - **Bypasses existing integration flow:** `IntegrationService.setApiKey()` handles audit events, key suffix storage, and cache eviction. A separate service must replicate all of this
  - **More code for the same outcome:** The end result (encrypted key in database, decrypted at runtime) is identical to Option 1 but with more classes, more tests, and more maintenance surface

### Option 3: Environment Variables per Tenant

Store AI API keys as environment variables or in a `.env` file, mapped by tenant ID (e.g., `AI_KEY_TENANT_ABC=sk-ant-...`).

- **Pros:**
  - Keys never touch the database — no risk of SQL injection exposing encrypted key material
  - Simple retrieval — `System.getenv("AI_KEY_" + tenantId)`
  - Follows the 12-factor app principle of storing config in the environment

- **Cons:**
  - **Does not scale with multi-tenancy:** Each new tenant requires a server restart (or hot-reload mechanism) to inject the new environment variable. With hundreds of tenants, the environment variable namespace becomes unmanageable
  - **Breaks self-service:** The BYOAK model requires tenants to set their own keys via the UI. Environment variables require server access — only platform operators can set them
  - **No encryption at rest:** Environment variables are stored in plaintext in the process environment. Any process dump, debug log, or container inspection exposes all tenant keys
  - **No audit trail:** No record of when a key was set, changed, or removed
  - **Incompatible with schema-per-tenant isolation:** The platform's security model relies on schema isolation. Environment variables are process-global, not schema-scoped

### Option 4: External Secret Manager (HashiCorp Vault / AWS Secrets Manager)

Store AI API keys in an external secret management service. Retrieve at runtime via API call to the vault.

- **Pros:**
  - Industry-standard secret management with access policies, audit logs, and automatic rotation
  - Secrets never stored in the application database
  - Built-in lease/TTL management
  - Can centralize all platform secrets (database credentials, API keys, signing keys) in one place

- **Cons:**
  - **New infrastructure dependency:** The platform currently has no Vault or AWS Secrets Manager integration. Adding one for AI keys alone is disproportionate
  - **Latency:** Every chat request requires a network call to the secret manager to retrieve the key. Can be mitigated with caching, but that introduces cache invalidation complexity
  - **Cost:** AWS Secrets Manager charges $0.40/secret/month + $0.05 per 10,000 API calls. For 200 tenants with frequent chat usage, costs are modest but non-zero
  - **Operational complexity:** Vault requires its own HA deployment, unsealing, audit log management. AWS Secrets Manager is simpler but adds AWS coupling
  - **Migration:** All existing secrets (`org_secrets` table) would need to be migrated to the external vault for consistency, or the platform would have two secret storage mechanisms — worse than the status quo
  - **Local development:** Requires a local Vault instance or Secrets Manager mock (LocalStack supports it, but adds another container to the dev stack)

## Decision

**Option 1 — Reuse existing SecretStore/OrgSecret.**

## Rationale

The AI API key is fundamentally the same kind of artifact as a Stripe API key, a SendGrid API key, or an accounting integration key — a tenant-scoped, encrypted secret used to authenticate with an external service. The platform already stores all of these in `org_secrets` via the `SecretStore` interface. Treating the AI key differently would create an architectural inconsistency that adds complexity without adding security.

1. **The existing infrastructure is proven.** `EncryptedDatabaseSecretStore` has been in production since Phase 21. It handles encryption (AES-256-GCM), key versioning, and per-tenant isolation via schema boundaries. The `IntegrationService.setApiKey()` flow handles audit events, key suffix storage, and cache invalidation. All of this works today for the `PAYMENT`, `EMAIL`, `ACCOUNTING`, and `DOCUMENT_SIGNING` domains. The `AI` domain simply gets the same treatment.

2. **No new migration is needed.** The `org_secrets` table (V36) already exists in every tenant schema. Storing a new secret with key `"ai:anthropic:api_key"` is an INSERT, not a schema change. This reduces deployment risk and keeps the migration sequence clean.

3. **The "shared table" risk is theoretical.** All integration secrets sharing `org_secrets` means a bug in `EncryptedDatabaseSecretStore` affects all domains. But the alternative (separate tables per domain) means maintaining multiple encryption implementations — a far greater security risk. One well-tested encryption path is safer than five domain-specific ones.

4. **AI-specific metadata belongs on `OrgIntegration`, not on the secret.** The selected model, last validation timestamp, and any future token budget are configuration, not secrets. They belong in `OrgIntegration.configJson` (JSONB), which already stores per-domain configuration. The `SecretStore` stores exactly one thing: the encrypted API key. This separation of concerns is clean.

## Consequences

- **Positive:**
  - Zero new code for encryption — `SecretStore.store()` and `SecretStore.retrieve()` handle everything
  - Zero new migrations — `org_secrets` table already exists
  - Consistent audit trail — `integration.key_set` audit event fires for AI keys just like all other domains
  - Key rotation covers all secrets including AI keys — single rotation script
  - Frontend `IntegrationCard` already displays key suffix from `OrgIntegration.keySuffix` — no UI changes for key display

- **Negative:**
  - AI key shares storage with all other integration secrets — no domain-level isolation within the database (mitigated by schema-per-tenant isolation across tenants)
  - `SecretStore.retrieve()` decrypts on every call — no in-memory caching of the decrypted key. If a tenant sends 50 chat messages in a session, `EncryptedDatabaseSecretStore` performs 50 AES-256-GCM decryptions. This is sub-millisecond per operation and not a practical concern, but a caching layer could be added if profiling shows otherwise

- **Neutral:**
  - The secret key naming convention `"ai:anthropic:api_key"` follows the existing pattern (`"{domain}:{provider}:api_key"`). If the tenant switches from Anthropic to a future OpenAI adapter, the old key is deleted and a new one is stored with key `"ai:openai:api_key"` via the existing `IntegrationService.deleteApiKey()` + `setApiKey()` flow
  - The `configJson` field on `OrgIntegration` stores the selected model (e.g., `{"model": "claude-sonnet-4-6"}`). This is read by `AssistantService` when building the `ChatRequest`. No new column is needed
