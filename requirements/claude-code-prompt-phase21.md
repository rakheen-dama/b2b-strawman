You are a senior SaaS architect working on an existing multi-tenant "DocTeams" style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with dedicated schema-per-tenant isolation (Phase 13 eliminated shared schema).
- Projects, Customers, Tasks, TimeEntries, and Documents (with org/project/customer scopes).
- Internal staff users authenticated via Clerk, with org-scoped RBAC (admin, owner, member).
- Neon Postgres + S3 + Spring Boot 4 backend + Next.js 16 frontend, running on ECS/Fargate.
- **Auth abstraction** (Phase 20, frontend): `AuthContext`/`AuthUser` types with provider dispatch (`clerk` or `mock` via env var). Clerk is the only real implementation. ~12 files still import Clerk directly (annotated, out of scope for this phase).
- **Payment provider** (Phase 10): `PaymentProvider` interface with `MockPaymentProvider` implementation. Domain types (`PaymentRequest`, `PaymentResult`) are vendor-free records.
- **Notification channels** (Phase 6.5): `NotificationChannel` interface with `InAppNotificationChannel` (real) and `EmailNotificationChannel` (log stub, local/dev profile only). `NotificationDispatcher` routes based on user preferences.
- **S3 storage** (no abstraction): `S3Client` from AWS SDK injected directly into 5+ domain services (`GeneratedDocumentService`, `OrgSettingsService`, `DataExportService`, `DataAnonymizationService`, `RetentionService`). `S3PresignedUrlService` is a partial facade for presigned URLs only. AWS SDK types leak into domain code.
- **Document rendering** (Phase 12): `PdfRenderingService` (Thymeleaf + OpenHTMLToPDF), `TemplateContextBuilder` interface with 3 implementations. Vendor types confined to rendering services.
- **Domain events**: `DomainEvent` sealed interface with 16 record implementations, published via Spring `ApplicationEventPublisher`.
- **OrgSettings** (Phase 8): per-tenant configuration including default currency, logo, brand_color, footer text. Lives in tenant schema.
- **Rate cards, budgets & profitability** (Phase 8): `BillingRate`, `CostRate`, `ProjectBudget`.
- **Invoicing** (Phase 10): `Invoice`/`InvoiceLine` entities, draft-to-paid lifecycle.
- **Retainers** (Phase 17): `RetainerAgreement`, `RetainerPeriod` with hour banks and rollover.
- **Reporting** (Phase 19): `ReportDefinition` entity, `ReportQuery` strategy interface, 3 standard reports, CSV/PDF export.
- **Tags, custom fields, views** (Phase 11): `FieldDefinition`, `Tag`, `SavedView`.
- **Customer compliance & lifecycle** (Phase 14): lifecycle state machine, checklist engine, data subject requests, retention policies.
- **Backend auth isolation**: `ClerkJwtAuthenticationConverter` and `ClerkJwtUtils` are confined to `security/` package. Domain code uses `RequestScopes` (ScopedValue) and Spring Security authorities — no Clerk types leak.

For **Phase 21**, I want to add **Integration Ports, BYOAK Infrastructure & Feature Flags** — a structural/architectural phase that defines clean integration boundaries (ports) for all external vendor dependencies, introduces a "Bring Your Own API Key" framework for tenant-scoped provider configuration, and gates integration domains behind feature flags.

***

## Objective of Phase 21

Design and specify:

1. **StorageService port** — Extract a `StorageService` interface from the existing direct S3 usage. All 5+ domain services that currently inject `S3Client` must be refactored to inject `StorageService` instead. The `S3StorageAdapter` implements this interface. No new storage functionality — pure refactoring to seal the vendor leak.

2. **AccountingProvider port** — Define an interface for syncing invoices to external accounting software, mapping chart of accounts, and reconciling payments. v1 implementation is `NoOpAccountingProvider` (returns success, logs the call). Future adapters: Xero, QuickBooks, Sage.

3. **AiProvider port** — Define an interface for text generation, document summarization, and smart categorization. v1 implementation is `NoOpAiProvider` (returns empty/no-op results). Future adapters: OpenAI, Anthropic, local models.

4. **DocumentSigningProvider port** — Define an interface for sending documents for e-signature, checking signature status, and downloading signed copies. v1 implementation is `NoOpSigningProvider` (returns auto-signed status). Future adapters: DocuSign, SigniFlow.

5. **SecretStore port** — Define an interface for storing, retrieving, and deleting sensitive credentials (API keys, tokens). v1 implementation is `EncryptedDatabaseSecretStore` using AES-256-GCM with a master key sourced from an environment variable (`INTEGRATION_ENCRYPTION_KEY`). Ciphertext, IV, and auth tag stored in an `org_secrets` table in the tenant schema. Future adapters: HashiCorp Vault, AWS Secrets Manager.

6. **OrgIntegration entity & BYOAK registry** — A new `OrgIntegration` entity that stores per-tenant integration configuration: `(id, domain, provider_slug, enabled, config_json, created_at, updated_at)`. API keys are NOT stored here — they go through `SecretStore`. An `IntegrationRegistry` Spring component discovers available provider adapters at startup and resolves the active adapter for a given tenant + domain at request time.

7. **Feature flags** — Extend `OrgSettings` with boolean flags for integration domains: `accounting_enabled`, `ai_enabled`, `document_signing_enabled`. These gate the entire domain — when disabled, the UI shows "Coming soon" / disabled state, and backend endpoints return 403 with a clear message. Payment and email/notification domains are always available (existing functionality, no feature flag needed).

8. **Integrations settings UI** — A new "Integrations" section in the org settings frontend. Lists available integration domains (Accounting, AI, Document Signing, Payments). Each domain shows: enabled/disabled toggle, provider selection dropdown (from available adapters), API key entry field (masked, one-way — enter to set, cannot retrieve), connection test button. Admin/owner role required.

9. **Audit integration** — Integration configuration changes (enable/disable, provider change, key rotation) logged as audit events.

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- **Ports live in the domain** — each port interface is defined in its own package within the domain layer (e.g., `integration/storage/StorageService.java`, `integration/accounting/AccountingProvider.java`). Adapter implementations live in sub-packages (e.g., `integration/storage/s3/S3StorageAdapter.java`).
- **No vendor types in port interfaces** — port methods accept and return only domain-owned types (records, enums, primitives). Vendor SDK types are confined to adapter implementations.
- **Adapter selection via Spring** — use `@ConditionalOnProperty` for system-wide defaults (like storage, which is infrastructure-level). Use the `IntegrationRegistry` for tenant-scoped selection (like accounting, which varies per org).
- **Existing interfaces preserved** — `PaymentProvider` and `NotificationChannel` already follow this pattern. Do NOT move or rename them. The BYOAK registry should be able to wrap/discover these existing interfaces alongside the new ports.
- **SecretStore encryption** — AES-256-GCM (authenticated encryption). Each secret gets a unique random IV (96-bit). The master key is 256-bit, sourced from `INTEGRATION_ENCRYPTION_KEY` environment variable. Key rotation strategy: re-encrypt all secrets with new key (admin endpoint, not in v1 scope — just design for it by storing a `key_version` column).
- **No OAuth flows in v1** — all vendor auth is API-key-based. Some vendors (Xero, QuickBooks) use OAuth2 — that's a future phase. The `OrgIntegration.config_json` field can store OAuth tokens when that phase arrives, but the UI and backend only support API key entry for now.
- **Feature flags are simple booleans on OrgSettings** — not a generic feature flag framework. These are specifically for integration domain gating. Plan-tier gating (Starter vs. Pro) is a separate concern and already exists.

2. **Tenancy**

- `OrgIntegration` and `org_secrets` tables live in the tenant schema (dedicated schema per tenant). No cross-tenant access.
- The `IntegrationRegistry` resolves the active provider for the current tenant by reading `OrgIntegration` within the tenant's schema. This happens per-request, cached in-memory with a short TTL (e.g., 60 seconds via Caffeine) to avoid repeated DB lookups.
- StorageService is an exception — it's infrastructure-level (system-wide S3 bucket), not tenant-configurable. It uses `@ConditionalOnProperty`, not the BYOAK registry.

3. **Security**

- API keys entered via the UI are transmitted over HTTPS, encrypted by `SecretStore` before persistence, and never returned to the frontend (write-only). The UI shows a masked placeholder ("••••••••abc123") showing only the last 6 characters, stored as a separate `key_suffix` column on `OrgIntegration`.
- The `INTEGRATION_ENCRYPTION_KEY` env var must be set in production. If missing, the `EncryptedDatabaseSecretStore` should fail fast at startup with a clear error message (not silently fall back to plaintext).
- Connection test endpoints (for verifying API keys work) should be rate-limited or require admin role to prevent abuse.

4. **Testing**

- All domain services that previously used `S3Client` directly must be testable with a `StorageService` mock/stub — no AWS SDK in unit tests.
- The `NoOp*` provider stubs serve as test-friendly defaults.
- Integration tests for `EncryptedDatabaseSecretStore` should verify: encrypt/decrypt round-trip, different tenants get different secrets, missing master key fails fast.

***

## Section 1 — StorageService Port & S3 Refactoring

### StorageService Interface

```java
public interface StorageService {
    /**
     * Upload a file and return the storage key.
     */
    String upload(String key, byte[] content, String contentType);

    /**
     * Upload a file from an InputStream (for large files).
     */
    String upload(String key, InputStream content, long contentLength, String contentType);

    /**
     * Download a file's content.
     */
    byte[] download(String key);

    /**
     * Delete a file.
     */
    void delete(String key);

    /**
     * Generate a presigned upload URL (time-limited).
     */
    PresignedUrl generateUploadUrl(String key, String contentType, Duration expiry);

    /**
     * Generate a presigned download URL (time-limited).
     */
    PresignedUrl generateDownloadUrl(String key, Duration expiry);
}
```

Domain record:
```java
public record PresignedUrl(String url, Instant expiresAt) {}
```

### S3StorageAdapter

- Implements `StorageService`, wraps `S3Client` and `S3Presigner`.
- Annotated `@Component` + `@ConditionalOnProperty(name = "storage.provider", havingValue = "s3", matchIfMissing = true)`.
- Bucket name from `storage.s3.bucket` config property.
- Absorbs logic currently in `S3PresignedUrlService` (which can be deleted or kept as an internal detail of the adapter).

### Refactoring targets

These domain services must be refactored to inject `StorageService` instead of `S3Client`:

1. `GeneratedDocumentService` — PDF upload after generation
2. `OrgSettingsService` — branding logo upload/delete
3. `DataExportService` — data export ZIP upload
4. `DataAnonymizationService` — anonymization artifact storage
5. `RetentionService` — retention-expired file deletion
6. `S3PresignedUrlService` — presigned URL generation (merge into S3StorageAdapter or keep as thin delegate)

All AWS SDK imports (`software.amazon.awssdk.*`) must be removed from these services. Only the `S3StorageAdapter` may import AWS SDK types.

***

## Section 2 — SecretStore Port & Encrypted Database Implementation

### SecretStore Interface

```java
public interface SecretStore {
    void store(String secretKey, String plaintext);
    String retrieve(String secretKey);
    void delete(String secretKey);
    boolean exists(String secretKey);
}
```

The `secretKey` is a logical name like `"accounting:xero:api_key"`. The tenant scope is implicit (queries run within the tenant's schema, like all other entities).

### EncryptedDatabaseSecretStore

- Uses AES-256-GCM for authenticated encryption.
- Each secret stored as: `(id, secret_key, encrypted_value, iv, key_version, created_at, updated_at)` in an `org_secrets` table.
- `encrypted_value` = ciphertext + GCM auth tag (Java's `AES/GCM/NoPadding` appends the tag to ciphertext).
- `iv` = 96-bit random nonce, unique per encrypt operation, stored as Base64.
- `key_version` = integer, defaults to 1. Supports future key rotation.
- Master key loaded from `INTEGRATION_ENCRYPTION_KEY` env var, decoded from Base64.
- `@PostConstruct` validation: fail fast if env var is missing or key length is not 256 bits.

### Migration

- New migration: `org_secrets` table with columns `(id UUID PK, secret_key VARCHAR UNIQUE, encrypted_value TEXT, iv VARCHAR, key_version INT DEFAULT 1, created_at TIMESTAMP, updated_at TIMESTAMP)`.

***

## Section 3 — Integration Ports (Stubs)

### 3A — AccountingProvider

```java
public interface AccountingProvider {
    String providerId();  // e.g., "xero", "quickbooks", "noop"

    AccountingSyncResult syncInvoice(InvoiceSyncRequest request);
    AccountingSyncResult syncCustomer(CustomerSyncRequest request);
    ConnectionTestResult testConnection();
}
```

Domain records:
- `InvoiceSyncRequest` — invoice number, customer name, line items (description, quantity, unit price, tax), currency, dates
- `CustomerSyncRequest` — customer name, email, address fields
- `AccountingSyncResult` — success boolean, external reference ID (nullable), error message (nullable)
- `ConnectionTestResult` — success boolean, provider name, error message (nullable)

`NoOpAccountingProvider`: returns success with `"NOOP-"` + UUID reference. Annotated `@Component`, registered as default.

### 3B — AiProvider

```java
public interface AiProvider {
    String providerId();

    AiTextResult generateText(AiTextRequest request);
    AiTextResult summarize(String content, int maxLength);
    List<String> suggestCategories(String content, List<String> existingCategories);
    ConnectionTestResult testConnection();
}
```

Domain records:
- `AiTextRequest` — prompt, max tokens, temperature (optional)
- `AiTextResult` — success boolean, content (nullable), error message (nullable), tokens used (nullable)

`NoOpAiProvider`: returns empty/no-op results (empty string for text, empty list for categories). Annotated `@Component`, registered as default.

### 3C — DocumentSigningProvider

```java
public interface DocumentSigningProvider {
    String providerId();

    SigningResult sendForSignature(SigningRequest request);
    SigningStatus checkStatus(String signingReference);
    byte[] downloadSigned(String signingReference);
    ConnectionTestResult testConnection();
}
```

Domain records:
- `SigningRequest` — document bytes, content type, signer name, signer email, callback URL (optional)
- `SigningResult` — success boolean, signing reference (nullable), error message (nullable)
- `SigningStatus` — enum (PENDING, SIGNED, DECLINED, EXPIRED), signing reference, updated at

`NoOpSigningProvider`: returns auto-signed status immediately (SIGNED with mock reference). Annotated `@Component`, registered as default.

***

## Section 4 — OrgIntegration Entity & BYOAK Registry

### OrgIntegration Entity

```java
@Entity
@Table(name = "org_integrations")
public class OrgIntegration {
    @Id @GeneratedValue UUID id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    IntegrationDomain domain;   // ACCOUNTING, AI, DOCUMENT_SIGNING, PAYMENT

    @Column(name = "provider_slug", nullable = false)
    String providerSlug;        // "noop", "xero", "quickbooks", "stripe", etc.

    boolean enabled;

    @Column(name = "config_json", columnDefinition = "jsonb")
    String configJson;          // Non-sensitive config (base URL overrides, region, etc.)

    @Column(name = "key_suffix")
    String keySuffix;           // Last 6 chars of API key for display ("••••abc123")

    Instant createdAt;
    Instant updatedAt;
}
```

Enum:
```java
public enum IntegrationDomain {
    ACCOUNTING, AI, DOCUMENT_SIGNING, PAYMENT
}
```

Migration: `org_integrations` table with unique constraint on `domain` (one provider per domain per tenant).

### IntegrationRegistry

```java
@Component
public class IntegrationRegistry {
    // Discovers all beans implementing integration port interfaces at startup.
    // At request time, resolves the active adapter for the current tenant + domain:
    //   1. Read OrgIntegration for domain from tenant schema (cached, 60s TTL)
    //   2. If no OrgIntegration row or not enabled → return NoOp adapter
    //   3. Match provider_slug to registered adapter beans
    //   4. If adapter needs credentials → inject via SecretStore

    <T> T resolve(IntegrationDomain domain, Class<T> portInterface);
}
```

Usage in domain services:
```java
// Instead of injecting a specific provider:
@Autowired AccountingProvider accountingProvider;

// Services that need tenant-scoped resolution:
var accounting = integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
accounting.syncInvoice(request);
```

Note: `StorageService` does NOT use the registry — it's system-wide infrastructure, not tenant-configurable.

### Provider Registration

Each adapter bean self-registers via a marker interface or annotation:

```java
@IntegrationAdapter(domain = IntegrationDomain.ACCOUNTING, slug = "xero")
@Component
public class XeroAccountingAdapter implements AccountingProvider { ... }
```

The `@IntegrationAdapter` annotation carries the domain and slug. The registry scans for beans with this annotation at startup.

***

## Section 5 — Feature Flags

### OrgSettings Extension

Add boolean columns to `OrgSettings` (existing entity, Phase 8):

```sql
ALTER TABLE org_settings ADD COLUMN accounting_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE org_settings ADD COLUMN ai_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE org_settings ADD COLUMN document_signing_enabled BOOLEAN NOT NULL DEFAULT FALSE;
```

All default to `FALSE` — integration domains are opt-in.

### Backend Gating

A reusable guard annotation or check:

```java
@IntegrationGuard(IntegrationDomain.ACCOUNTING)
@PostMapping("/api/accounting/sync-invoice")
public ResponseEntity<?> syncInvoice(...) { ... }
```

Or a simpler approach via an `IntegrationGuardService`:

```java
integrationGuard.requireEnabled(IntegrationDomain.ACCOUNTING);
// throws 403 ProblemDetail if not enabled
```

The guard reads from `OrgSettings` (cached via the existing org settings cache).

### Frontend Gating

The integrations settings page shows all domains. Disabled domains show a toggle to enable. Integration-specific UI elsewhere (e.g., "Sync to Xero" button on an invoice) checks the feature flag and renders as disabled/hidden when the domain is off.

The `/api/org-settings` response already includes org settings — add the three boolean flags to the response DTO.

***

## Section 6 — Integrations Settings UI

### New Page: Settings → Integrations

Path: `/org/[slug]/settings/integrations`

Layout: Card grid (one card per integration domain).

Each card shows:
- **Domain name** and icon (Accounting, AI, Document Signing, Payments)
- **Status badge**: "Active" (green), "Configured" (yellow, has key but domain disabled), "Not configured" (gray)
- **Provider**: dropdown of available providers for this domain (populated from `/api/integrations/providers` endpoint)
- **API Key**: masked input field. Shows "••••••abc123" if a key exists (using `keySuffix`). "Set API Key" button opens a dialog with a password input. Key is write-only — setting a new key replaces the old one.
- **Enable/Disable toggle**: only available if a provider is selected and key is set
- **Test Connection button**: calls the provider's `testConnection()`, shows success/error toast
- **Last updated**: timestamp from `OrgIntegration`

### API Endpoints

```
GET    /api/integrations                  — list org integrations (domain, provider, enabled, keySuffix, updatedAt)
GET    /api/integrations/providers         — list available providers per domain (from registry)
PUT    /api/integrations/{domain}          — upsert integration config (providerSlug, configJson)
POST   /api/integrations/{domain}/set-key  — set API key (encrypted via SecretStore)
POST   /api/integrations/{domain}/test     — test connection
DELETE /api/integrations/{domain}/key      — remove API key
PATCH  /api/integrations/{domain}/toggle   — enable/disable
```

All endpoints require `ROLE_ORG_ADMIN` or `ROLE_ORG_OWNER`.

***

## Section 7 — Audit Integration

Log these events to the audit trail (using existing `AuditService`):

- `INTEGRATION_CONFIGURED` — when a provider is selected or changed for a domain
- `INTEGRATION_KEY_SET` — when an API key is set (DO NOT log the key value)
- `INTEGRATION_KEY_REMOVED` — when an API key is deleted
- `INTEGRATION_ENABLED` — when a domain is enabled
- `INTEGRATION_DISABLED` — when a domain is disabled
- `INTEGRATION_CONNECTION_TESTED` — when a connection test is run (log result)

***

## Out of scope

- **Real vendor adapters** — no Xero, QuickBooks, Stripe, SendGrid, OpenAI, DocuSign implementations. Only NoOp stubs. Each real adapter is a future single-epic effort.
- **OAuth2 vendor authentication** — some vendors (Xero, QuickBooks) use OAuth. This phase only supports API key auth. OAuth flow (redirect, token exchange, refresh) is a future phase.
- **Webhook ingestion from vendors** — receiving callbacks from Xero, Stripe, etc. is a future concern.
- **Key rotation endpoint** — the `key_version` column supports future rotation, but the actual rotation mechanism (admin endpoint, re-encrypt all secrets) is deferred.
- **Per-member integration permissions** — all members can use configured integrations. Fine-grained "who can sync to Xero" is deferred.
- **Moving remaining Clerk-specific frontend files behind the auth abstraction** — Phase 20 scope, not this phase.
- **Generic feature flag framework** — the three boolean flags on OrgSettings are purpose-built for integration gating. A generic feature flag system (LaunchDarkly-style) is a separate concern.
- **Plan-tier gating for integrations** — e.g., "AI only on Pro plan." This can be layered on later via the existing plan enforcement system.
- **Vendor-specific configuration UIs** — e.g., Xero-specific "map chart of accounts" wizard. The settings UI only handles provider selection, API key, and enable/disable.

***

## ADR Topics

The architecture phase should produce ADRs for:

1. **Integration port package structure** — where do port interfaces vs. adapters live? Options: (a) per-domain packages under `integration/` (e.g., `integration/accounting/`, `integration/storage/`), (b) ports in domain packages, adapters in `infrastructure/`, (c) all in a single `integration/` flat package. Decision should consider discoverability, import discipline, and the Spring component scan boundary.

2. **Tenant-scoped adapter resolution** — how does the `IntegrationRegistry` resolve the correct adapter at request time? Options: (a) per-request DB lookup with Caffeine cache, (b) tenant context listener that pre-loads integration config, (c) lazy resolution on first use per request. Trade-offs: cache staleness, cold-start latency, memory per tenant.

3. **Secret storage strategy** — why AES-256-GCM in the database (vs. Vault, vs. KMS envelope encryption, vs. external secrets manager). Document the threat model: what we're protecting against (DB dump), what we're not (compromised application server with env access). Include the migration path to Vault.

4. **Feature flag scope** — why simple boolean columns on OrgSettings (vs. a generic feature flag table, vs. third-party service). Document that this is intentionally narrow — only for integration domain gating, not a general feature management system.

***

## Style and boundaries

- Follow existing code patterns: controllers return `ResponseEntity<?>`, services are `@Service` + `@Transactional`, entities use JPA annotations.
- New packages under `src/main/java/io/b2mash/b2b/b2bstrawman/integration/` for the port framework, adapters, and registry.
- The S3 refactoring is a **pure refactoring** — no behavior changes, no new features. Existing tests must pass without modification (beyond updating imports). If a test was testing S3-specific behavior, it should now test through the `StorageService` interface.
- The frontend integrations settings page uses existing Shadcn components (Card, Switch, Select, Input, Button, Badge). No new component library additions needed.
- Keep the NoOp stubs genuinely useful for development — they should log what they would do (with parameter details) so developers can verify integration points are being called correctly.
