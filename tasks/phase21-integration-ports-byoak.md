# Phase 21 — Integration Ports, BYOAK Infrastructure & Feature Flags

Phase 21 is a **structural/architectural phase** that creates clean integration boundaries for all external vendor dependencies. It introduces no new business features — instead it defines the **ports** (interfaces), **adapters** (implementations), and **infrastructure** (BYOAK registry, secret storage, feature flags) that future vendor integrations will plug into.

After this phase, adding any vendor adapter (Xero, OpenAI, DocuSign, Stripe) is a self-contained single-epic effort requiring zero changes to domain services, controllers, or the frontend. Every port ships with a NoOp stub that logs calls and returns success.

**Architecture doc**: `architecture/phase21-integration-ports-byoak.md`

**ADRs**:
- [ADR-088](../adr/ADR-088-integration-port-package-structure.md) — Integration Port Package Structure (per-domain sub-packages under `integration/`)
- [ADR-089](../adr/ADR-089-tenant-scoped-adapter-resolution.md) — Tenant-Scoped Adapter Resolution (per-request DB lookup + Caffeine 60s TTL)
- [ADR-090](../adr/ADR-090-secret-storage-strategy.md) — Secret Storage Strategy (AES-256-GCM in-database, app-level encryption)
- [ADR-091](../adr/ADR-091-feature-flag-scope.md) — Feature Flag Scope (boolean columns on OrgSettings, not a generic flag table)

**Migration**: V36 — `org_integrations` table, `org_secrets` table, 3 boolean columns on `org_settings`

**Dependencies on prior phases**: Phase 8 (OrgSettings entity), Phase 10 (PaymentProvider / MockPaymentProvider), Phase 12 (GeneratedDocumentService), Phase 13 (dedicated schema isolation). No breaking changes to existing APIs.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 145 | StorageService Port + S3 Refactoring | Backend | — | M | 145A, 145B | **Done** (PRs #302, #303) |
| 146 | SecretStore Port + Encrypted Database Implementation | Backend | — | S | 146A | **Done** (PR #304) |
| 147 | Integration Port Interfaces + NoOp Stubs | Backend | — | M | 147A, 147B | **Done** (PRs #305, #306) |
| 148 | OrgIntegration Entity + IntegrationRegistry + BYOAK Infrastructure | Backend | 147 | M | 148A, 148B | **Done** (PRs #307, #308) |
| 149 | Feature Flags + IntegrationGuardService | Backend | — | S | 149A | **Done** (PR #309) |
| 150 | Integration Management API (Controller + Service) | Backend | 146, 148 | M | 150A, 150B | |
| 151 | Audit Integration for Config Events | Backend | 150 | S | 151A | |
| 152 | Integrations Settings UI | Frontend | 149, 150 | M | 152A, 152B | |

---

## Dependency Graph

```
[E145A StorageService interface + PresignedUrl record]
       |
[E145B S3StorageAdapter + refactor 5 domain services]
       |
  (complete — parallel with E146, E147, E149)

[E146A OrgSecret entity + EncryptedDatabaseSecretStore]
       |
       +---> [E150A IntegrationController + IntegrationService]
       |
[E147A Accounting + AI ports + NoOp stubs]
       |
[E147B Signing port + ConnectionTestResult + IntegrationDomain enum]
       |
[E148A OrgIntegration entity + @IntegrationAdapter annotation]
       |
[E148B IntegrationRegistry (startup discovery + Caffeine cache)]
       |
       +---> [E150A IntegrationController + IntegrationService]
                    |
             [E150B Integration request/response DTOs + security]
                    |
             [E151A Audit event hooks (6 event types)]
                    |
             [E152A Integration settings page + API client]
                    |
             [E152B IntegrationCard + SetApiKeyDialog + ConnectionTestButton]

[E149A OrgSettings boolean flags + IntegrationGuardService]
       |
       +---> [E152A] (flags in OrgSettings DTO for frontend)
```

**Parallel opportunities**:
- Epics 145, 146, 147, and 149 are fully independent — all four can run in parallel in Stage 1.
- Epic 148 depends only on 147 completing.
- Epic 150 depends on 146 and 148 completing.
- Epic 151 depends only on 150.
- Epic 152 depends on 149 and 150 completing.

---

## Implementation Order

### Stage 1: Independent foundations (fully parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a (parallel) | Epic 145 | 145A | `StorageService` interface + `PresignedUrl` record. Foundation types for the port — no service code yet. | **Done** (PR #302) |
| 1b (parallel) | Epic 146 | 146A | V36 migration (`org_secrets` table) + `OrgSecret` entity + `OrgSecretRepository` + `EncryptedDatabaseSecretStore` with AES-256-GCM. Fully independent. | **Done** (PR #304) |
| 1c (parallel) | Epic 147 | 147A | `AccountingProvider` + `AiProvider` interfaces + all their domain records + `NoOpAccountingProvider` + `NoOpAiProvider`. Independent of entity layer. | **Done** (PR #305) |
| 1d (parallel) | Epic 149 | 149A | 3 boolean columns on `OrgSettings` (V36 migration portion) + `IntegrationGuardService` + `IntegrationDisabledException`. Reads existing `OrgSettingsService`. | **Done** (PR #309) |

### Stage 2: Build on foundations (partially parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a (parallel) | Epic 145 | 145B | `S3StorageAdapter` + refactor 5 domain services to inject `StorageService`. Depends on 145A interface. | **Done** (PR #303) |
| 2b (parallel) | Epic 147 | 147B | `DocumentSigningProvider` interface + domain records + `NoOpSigningProvider` + `ConnectionTestResult` shared record + `IntegrationDomain` enum. Depends on 147A (package established). | **Done** (PR #306) |

### Stage 3: Registry layer

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 148 | 148A | V36 migration (`org_integrations` table) + `OrgIntegration` entity + `OrgIntegrationRepository` + `@IntegrationAdapter` annotation. Depends on 147B (IntegrationDomain enum must exist). | **Done** (PR #307) |
| 3b | Epic 148 | 148B | `IntegrationRegistry` component (startup bean discovery, Caffeine cache, `resolve()`, `evict()`). Also annotates `MockPaymentProvider` with `@IntegrationAdapter`. Depends on 148A. | **Done** (PR #308) |

### Stage 4: API layer

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 150 | 150A | `IntegrationService` (upsert, set-key, test, toggle, key-delete). Depends on 146A (SecretStore) and 148B (IntegrationRegistry). |
| 4b | Epic 150 | 150B | `IntegrationController` + request/response DTOs + `@PreAuthorize` security. Depends on 150A. |

### Stage 5: Parallel finishers

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 5a (parallel) | Epic 151 | 151A | Wire 6 audit event types into `IntegrationService`. Depends on 150B. |
| 5b (parallel) | Epic 152 | 152A | `lib/api/integrations.ts` API client + settings sidebar nav update + feature flag types. Depends on 149A (OrgSettings DTO has flags) and 150B (API endpoints exist). |

### Stage 6: Frontend UI

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 6 | Epic 152 | 152B | Integrations settings page + `IntegrationCard` + `SetApiKeyDialog` + `ConnectionTestButton`. Depends on 152A (API client). |

### Timeline

```
Stage 1: [145A] // [146A] // [147A] // [149A]         (parallel)
Stage 2: [145B] // [147B]                              (partial parallel)
Stage 3: [148A] --> [148B]                             (sequential)
Stage 4: [150A] --> [150B]                             (sequential)
Stage 5: [151A] // [152A]                              (parallel)
Stage 6: [152B]                                        (after 152A)
```

**Critical path**: 147A → 147B → 148A → 148B → 150A → 150B → 152A → 152B

---

## Epic 145: StorageService Port + S3 Refactoring

**Goal**: Create the `StorageService` port interface and `PresignedUrl` domain record. Build `S3StorageAdapter` that wraps `S3Client` and absorbs `S3PresignedUrlService` presign logic. Refactor all 5 domain services that inject `S3Client` directly to inject `StorageService` instead. Remove all AWS SDK imports from domain code. This is a **pure refactoring** — zero behavior changes, all existing tests must pass.

**References**: Architecture doc Sections 21.3 (StorageService Port), 21.8 (S3 Refactoring Plan).

**Dependencies**: None (foundation epic — can start immediately).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **145A** | 145.1–145.4 | `StorageService` interface + `PresignedUrl` record + `integration/storage/` package scaffolding + unit test. ~3 new files. | **Done** (PR #302) |
| **145B** | 145.5–145.11 | `S3StorageAdapter` implementation + refactor `GeneratedDocumentService`, `OrgSettingsService`, `DataExportService`, `DataAnonymizationService`, `RetentionService` + delete/subsume `S3PresignedUrlService` presign methods. ~8 files modified, 1 new file. | **Done** (PR #303) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 145.1 | Create `integration/storage/` package with `StorageService.java` | 145A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/storage/StorageService.java`. Define the interface with 6 methods: `upload(String key, byte[] content, String contentType)`, `upload(String key, InputStream content, long contentLength, String contentType)`, `download(String key)`, `delete(String key)`, `generateUploadUrl(String key, String contentType, Duration expiry)`, `generateDownloadUrl(String key, Duration expiry)`. All methods are Java interface methods (no default implementations). Package: `io.b2mash.b2b.b2bstrawman.integration.storage`. Per architecture doc Section 21.3 exact signatures. |
| 145.2 | Create `PresignedUrl.java` domain record | 145A | 145.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/storage/PresignedUrl.java`. Java record: `public record PresignedUrl(String url, Instant expiresAt) {}`. No external SDK imports. Located in `integration/storage/` alongside the interface. |
| 145.3 | Create `integration/storage/s3/` package structure placeholder | 145A | 145.1 | Create the `s3/` sub-package by adding a `package-info.java` file: `/** S3 implementation of StorageService. AWS SDK types confined to this package. */ package io.b2mash.b2b.b2bstrawman.integration.storage.s3;`. This ensures the package exists before 145B adds `S3StorageAdapter`. |
| 145.4 | Write `StorageService` contract unit test (compile-time shape check) | 145A | 145.1, 145.2 | New file: `backend/src/test/java/.../integration/storage/StorageServiceContractTest.java`. Not a behavior test — a compile-time contract test that verifies the interface has all required methods. Create an anonymous inner class that implements `StorageService` and assert all methods exist via reflection. ~4 assertions. Pattern: similar to Phase 12 `TemplateContextBuilder` interface tests. Ensures no method signature drift during refactoring. |
| 145.5 | Create `S3StorageAdapter.java` | 145B | 145A | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/storage/s3/S3StorageAdapter.java`. Annotate `@Component` + `@ConditionalOnProperty(name = "storage.provider", havingValue = "s3", matchIfMissing = true)`. Constructor injects `S3Client s3Client`, `S3Presigner s3Presigner`, `@Value("${aws.s3.bucket-name}") String bucketName` (check existing `S3Config` for exact property key). Implement all 6 `StorageService` methods: `upload(key, bytes, contentType)` uses `PutObjectRequest` + `RequestBody.fromBytes()`; `upload(key, stream, length, contentType)` uses `RequestBody.fromInputStream()`; `download(key)` uses `GetObjectRequest` + `.asByteArray()`; `delete(key)` uses `DeleteObjectRequest` (best-effort, log warning on failure); `generateUploadUrl()` / `generateDownloadUrl()` absorb logic from existing `S3PresignedUrlService`. AWS SDK imports confined to this class only. Read `backend/src/main/java/.../s3/S3PresignedUrlService.java` for existing presign logic to absorb. |
| 145.6 | Refactor `GeneratedDocumentService` — replace `S3Client` with `StorageService` | 145B | 145.5 | Modify: `backend/src/main/java/.../template/GeneratedDocumentService.java`. Replace constructor parameter `S3Client s3Client` with `StorageService storageService`. Replace `s3Client.putObject(PutObjectRequest, RequestBody.fromBytes(pdfBytes))` with `storageService.upload(key, pdfBytes, "application/pdf")`. Remove all `import software.amazon.awssdk.*` statements from this file. The storage key convention (`org/{tenantId}/generated/{docId}.pdf`) stays unchanged — keep the key-building logic in the service. Existing integration tests must continue to pass (update `@MockitoBean S3Client` to `@MockitoBean StorageService`). |
| 145.7 | Refactor `OrgSettingsService` — replace `S3Client` with `StorageService` | 145B | 145.5 | Modify: `backend/src/main/java/.../settings/OrgSettingsService.java`. Replace `S3Client s3Client`, `S3PresignedUrlService s3PresignedUrlService`, and `S3Properties s3Properties` constructor params with single `StorageService storageService`. Replace logo upload (`s3Client.putObject(...)`) with `storageService.upload(key, logoBytes, contentType)`. Replace logo delete (`s3Client.deleteObject(...)`) with `storageService.delete(key)`. Replace `s3PresignedUrlService.generateDownloadUrl(key, Duration.ofHours(1))` with `storageService.generateDownloadUrl(key, Duration.ofHours(1))`. Remove all AWS SDK imports. Update tests to use `@MockitoBean StorageService`. |
| 145.8 | Refactor `DataExportService` — replace `S3Client` with `StorageService` | 145B | 145.5 | Modify: `backend/src/main/java/.../datarequest/DataExportService.java`. Replace `S3Client s3Client` + `S3Properties` with `StorageService storageService`. Replace `s3Client.putObject(...)` ZIP upload with `storageService.upload(key, zipBytes, "application/zip")`. Remove all AWS SDK imports. Update tests. |
| 145.9 | Refactor `DataAnonymizationService` — replace `S3Client` with `StorageService` | 145B | 145.5 | Modify: `backend/src/main/java/.../datarequest/DataAnonymizationService.java`. Replace `S3Client s3Client` + `S3Properties` with `StorageService storageService`. Replace `s3Client.deleteObject(...)` (best-effort) with `storageService.delete(key)` (contract: logs warning on failure — consistent with existing behavior). Remove all AWS SDK imports. Update tests. |
| 145.10 | Refactor `RetentionService` — replace `S3Client` with `StorageService` | 145B | 145.5 | Modify: `backend/src/main/java/.../retention/RetentionService.java`. Replace `S3Client s3Client` + `S3Properties` with `StorageService storageService`. Replace `s3Client.deleteObject(...)` (best-effort deletion of expired docs) with `storageService.delete(key)`. Remove all AWS SDK imports. Update tests. |
| 145.11 | Handle `S3PresignedUrlService` disposition + verify zero AWS SDK leakage | 145B | 145.5–145.10 | `S3PresignedUrlService` currently has two roles: (1) presigned URL generation (absorbed by `S3StorageAdapter` in 145.5) and (2) key path builders (`buildKey()`, `buildOrgKey()`, `buildCustomerKey()`). Decision: keep `S3PresignedUrlService` as a thin key-builder utility (rename class comment to reflect this), remove its `S3Client`/`S3Presigner` dependencies, and delegate any remaining presign callers (e.g., `DocumentController`) to use `StorageService` directly. Run: `grep -r "software.amazon.awssdk" backend/src/main/java --include="*.java" -l` — must return only `S3Config.java` and `S3StorageAdapter.java`. Add `@MockitoBean StorageService` to any integration test that previously mocked `S3Client`. |

### Key Files

**Slice 145A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/storage/StorageService.java` — Port interface (6 methods)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/storage/PresignedUrl.java` — Domain record
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/storage/s3/package-info.java` — Package placeholder
- `backend/src/test/java/.../integration/storage/StorageServiceContractTest.java` — Compile-time contract assertions

**Slice 145B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/storage/s3/S3StorageAdapter.java` — S3 implementation

**Slice 145B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java` — `S3Client` → `StorageService`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` — `S3Client` + `S3PresignedUrlService` → `StorageService`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportService.java` — `S3Client` → `StorageService`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataAnonymizationService.java` — `S3Client` → `StorageService`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retention/RetentionService.java` — `S3Client` → `StorageService`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/s3/S3PresignedUrlService.java` — Remove presign methods, keep key builders

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/S3Config.java` — `S3Properties` record, bucket property key
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/s3/S3PresignedUrlService.java` — Presign logic to absorb
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java` — Representative service to refactor
- Architecture doc Section 21.8 (S3 Refactoring Plan table)

### Architecture Decisions

- **`@ConditionalOnProperty` not the BYOAK registry**: Storage is infrastructure-level (system-wide S3 bucket). All tenants share the same storage endpoint. Only the tenant-scoped key prefix (`org/{tenantId}/...`) provides isolation. The registry pattern is for tenant-configurable adapters (accounting, AI, signing). This matches ADR-051 (PSP adapter design) which also uses conditional property for deployment-time selection.
- **S3StorageAdapter absorbs `S3PresignedUrlService` presign logic**: The presign methods in `S3PresignedUrlService` are S3-specific implementation details. They belong in the adapter, not as a separate service. Key path builders stay as `S3PresignedUrlService` utility methods since they encode domain path conventions, not S3 specifics.
- **Best-effort delete semantics**: `StorageService.delete()` logs a warning but does not throw on failure (consistent with existing `DataAnonymizationService` and `RetentionService` behavior). The adapter wraps the S3 delete in try-catch.

---

## Epic 146: SecretStore Port + Encrypted Database Implementation

**Goal**: Create the `SecretStore` port interface and `EncryptedDatabaseSecretStore` implementation using AES-256-GCM encryption. Create `OrgSecret` entity and repository. Add the `org_secrets` table as part of the V36 migration. Fail-fast startup validation ensures the `INTEGRATION_ENCRYPTION_KEY` environment variable is set and correct length before the application starts.

**References**: Architecture doc Sections 21.5 (SecretStore & Encryption), 21.11 (Database Migrations). ADR-090.

**Dependencies**: None (fully independent).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **146A** | 146.1–146.8 | V36 migration (`org_secrets` table) + `OrgSecret` entity + `OrgSecretRepository` + `SecretStore` interface + `EncryptedDatabaseSecretStore` (AES-256-GCM) + integration tests for encrypt/decrypt round-trip, missing key fail-fast, cross-tenant isolation. ~6 new files. | **Done** (PR #304) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 146.1 | Create V36 migration — `org_secrets` table portion | 146A | | New file: `backend/src/main/resources/db/migration/tenant/V36__create_integration_tables.sql`. For this slice, add only the `org_secrets` table DDL. SQL: `CREATE TABLE org_secrets (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), secret_key VARCHAR(200) NOT NULL, encrypted_value TEXT NOT NULL, iv VARCHAR(24) NOT NULL, key_version INT NOT NULL DEFAULT 1, created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), CONSTRAINT uq_org_secrets_key UNIQUE (secret_key));`. This is a tenant-schema migration (not global). Note: V36 file started here; other epics will add to the same file via separate ALTER statements. |
| 146.2 | Create `OrgSecret.java` entity | 146A | 146.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/OrgSecret.java`. Annotate `@Entity @Table(name = "org_secrets")`. Fields: `@Id @GeneratedValue(strategy = GenerationType.UUID) UUID id`, `@Column(name = "secret_key", nullable = false, length = 200) String secretKey`, `@Column(name = "encrypted_value", nullable = false, columnDefinition = "TEXT") String encryptedValue`, `@Column(name = "iv", nullable = false, length = 24) String iv`, `@Column(name = "key_version", nullable = false) int keyVersion`, `@Column(name = "created_at", nullable = false, updatable = false) Instant createdAt`, `@Column(name = "updated_at", nullable = false) Instant updatedAt`. Protected no-arg constructor. Public constructor: `OrgSecret(String secretKey, String encryptedValue, String iv, int keyVersion)` sets all fields + `createdAt = Instant.now()` + `updatedAt = Instant.now()`. Method `updateEncryptedValue(String encryptedValue, String iv, int keyVersion)` updates ciphertext + `updatedAt`. Getters for all fields. No `@Filter`, no `tenant_id` column — schema boundary handles isolation per ADR-064. Pattern: `DocumentTemplate.java` (Phase 12). |
| 146.3 | Create `OrgSecretRepository.java` | 146A | 146.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/OrgSecretRepository.java`. Extends `JpaRepository<OrgSecret, UUID>`. Custom methods: `Optional<OrgSecret> findBySecretKey(String secretKey)`, `void deleteBySecretKey(String secretKey)`, `boolean existsBySecretKey(String secretKey)`. |
| 146.4 | Create `SecretStore.java` port interface | 146A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/SecretStore.java`. Interface with 4 methods: `void store(String secretKey, String plaintext)` (upsert semantics), `String retrieve(String secretKey)` (throws if not found), `void delete(String secretKey)` (no-op if not found), `boolean exists(String secretKey)`. |
| 146.5 | Create `EncryptedDatabaseSecretStore.java` | 146A | 146.2–146.4 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/EncryptedDatabaseSecretStore.java`. Annotate `@Component`. Static constants: `ALGORITHM = "AES/GCM/NoPadding"`, `GCM_TAG_LENGTH = 128`, `IV_LENGTH = 12`. Constructor injects `OrgSecretRepository repository` + `@Value("${integration.encryption-key:}") String encodedKey`. `@PostConstruct validateKey()`: if null/blank throw `IllegalStateException("INTEGRATION_ENCRYPTION_KEY environment variable is not set...")`. If length != 32 bytes throw `IllegalStateException("...must be a Base64-encoded 256-bit (32-byte) key...")`. Implement: `@Transactional store()` — generate 12-byte IV, AES-256-GCM encrypt, Base64-encode ciphertext + IV, upsert via `repository.findBySecretKey()`. `@Transactional(readOnly=true) retrieve()` — find by key (throw `ResourceNotFoundException` if absent), decode IV + ciphertext, decrypt with `GCMParameterSpec`, return string. `@Transactional delete()` — `repository.deleteBySecretKey(key)`. `@Transactional(readOnly=true) exists()` — `repository.existsBySecretKey(key)`. |
| 146.6 | Add `integration.encryption-key` to `application-local.yml` and `application-test.yml` | 146A | 146.5 | Modify: `backend/src/main/resources/application-local.yml` and `backend/src/test/resources/application-test.yml`. Add: `integration.encryption-key: <base64-encoded-256-bit-test-key>`. For test/local, use a fixed known key. Production value injected via ECS environment variable. |
| 146.7 | Write integration tests for `EncryptedDatabaseSecretStore` | 146A | 146.5, 146.6 | New file: `backend/src/test/java/.../integration/secret/EncryptedDatabaseSecretStoreIntegrationTest.java`. Tests (use `ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test").run()`): (1) `store_and_retrieve_roundtrip` — store "my-key" → "plaintext", retrieve → assert equals "plaintext". (2) `ciphertext_differs_for_same_plaintext` — store same value twice, read `encryptedValue` columns via repository, assert the two ciphertext strings differ (unique IV per encrypt). (3) `retrieve_throws_when_key_missing` — assert `ResourceNotFoundException`. (4) `delete_removes_secret` — store → delete → `exists()` returns false. (5) `exists_returns_false_when_absent` — assert `exists("no-such-key") == false`. (6) `upsert_overwrites_existing` — store twice with different values, retrieve once, assert latest value returned. ~6 tests. |
| 146.8 | Fail-fast test for missing encryption key | 146A | 146.5 | New test method in `EncryptedDatabaseSecretStoreIntegrationTest`: `startup_fails_when_encryption_key_missing` — create `EncryptedDatabaseSecretStore` with blank key string, call `validateKey()` directly, assert `IllegalStateException` is thrown. |

### Key Files

**Slice 146A — Create:**
- `backend/src/main/resources/db/migration/tenant/V36__create_integration_tables.sql` — `org_secrets` table DDL
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/OrgSecret.java` — Entity
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/OrgSecretRepository.java` — JPA repository
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/SecretStore.java` — Port interface
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/EncryptedDatabaseSecretStore.java` — AES-256-GCM implementation
- `backend/src/test/java/.../integration/secret/EncryptedDatabaseSecretStoreIntegrationTest.java` — 7 integration tests

**Slice 146A — Modify:**
- `backend/src/main/resources/application-local.yml` — Add `integration.encryption-key`
- `backend/src/test/resources/application-test.yml` — Add `integration.encryption-key` (fixed test key)

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java` — Entity pattern (no @Filter, no tenant_id)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/ResourceNotFoundException.java` — Exception to throw on missing key
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` — ScopedValue for test setup
- Architecture doc Section 21.5 (encryption parameters table)

### Architecture Decisions

- **AES-256-GCM chosen over Vault/KMS**: For v1 scale (a few API keys per tenant), app-level encryption in the existing database is the right trade-off. The `SecretStore` interface provides a clean seam for future migration to `VaultSecretStore` without changing domain code. See ADR-090.
- **Upsert semantics for `store()`**: If a secret already exists for a key, overwrite it (rotate). This matches the "set API key" UX — setting a new key replaces the old one transparently.
- **Fail-fast `@PostConstruct` validation**: Running without encryption is worse than not starting.
- **`key_version` column design**: Supports future key rotation (re-encrypt with new key, bump version). The actual rotation endpoint is out of Phase 21 scope but the column exists to avoid a future migration.

---

## Epic 147: Integration Port Interfaces + NoOp Stubs

**Goal**: Create the three tenant-scoped integration port interfaces (`AccountingProvider`, `AiProvider`, `DocumentSigningProvider`) with all their domain records, plus their NoOp stub implementations. Create the `ConnectionTestResult` shared record and `IntegrationDomain` enum. No entity layer, no registry — pure interface + stub definitions that the registry (Epic 148) will discover.

**References**: Architecture doc Sections 21.3 (Port Interfaces), 21.2 (New Enums and Records). ADR-088.

**Dependencies**: None (fully independent).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **147A** | 147.1–147.8 | `AccountingProvider` interface + 4 domain records + `NoOpAccountingProvider` + `AiProvider` interface + 2 domain records + `NoOpAiProvider`. ~10 new files. | **Done** (PR #305) |
| **147B** | 147.9–147.15 | `DocumentSigningProvider` interface + 3 domain records + `SigningState` enum + `NoOpSigningProvider` + `ConnectionTestResult` shared record + `IntegrationDomain` enum. ~8 new files. | **Done** (PR #306) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 147.1 | Create `integration/accounting/` package with `AccountingProvider.java` | 147A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingProvider.java`. Interface with 4 methods: `String providerId()`, `AccountingSyncResult syncInvoice(InvoiceSyncRequest request)`, `AccountingSyncResult syncCustomer(CustomerSyncRequest request)`, `ConnectionTestResult testConnection()`. |
| 147.2 | Create `InvoiceSyncRequest.java` domain record | 147A | 147.1 | New file in `integration/accounting/`. Record: `public record InvoiceSyncRequest(String invoiceNumber, String customerName, List<LineItem> lineItems, String currency, LocalDate issueDate, LocalDate dueDate) {}`. |
| 147.3 | Create `LineItem.java` domain record | 147A | 147.1 | New file in `integration/accounting/`. Record: `public record LineItem(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxAmount) {}`. |
| 147.4 | Create `CustomerSyncRequest.java` domain record | 147A | 147.1 | New file in `integration/accounting/`. Record: `public record CustomerSyncRequest(String customerName, String email, String addressLine1, String addressLine2, String city, String postalCode, String country) {}`. |
| 147.5 | Create `AccountingSyncResult.java` domain record | 147A | 147.1 | New file in `integration/accounting/`. Record: `public record AccountingSyncResult(boolean success, String externalReferenceId, String errorMessage) {}`. |
| 147.6 | Create `NoOpAccountingProvider.java` | 147A | 147.1–147.5 | New file in `integration/accounting/`. Annotate `@Component`. `providerId()` returns `"noop"`. `syncInvoice()` logs via SLF4J Logger, returns `new AccountingSyncResult(true, "NOOP-" + UUID.randomUUID().toString().substring(0, 8), null)`. `syncCustomer()` similar. `testConnection()` returns `new ConnectionTestResult(true, "noop", null)` (forward reference to 147B). |
| 147.7 | Create `integration/ai/` package with `AiProvider.java` and domain records | 147A | | New files in `integration/ai/`. `AiProvider.java` interface: `String providerId()`, `AiTextResult generateText(AiTextRequest request)`, `AiTextResult summarize(String content, int maxLength)`, `List<String> suggestCategories(String content, List<String> existingCategories)`, `ConnectionTestResult testConnection()`. `AiTextRequest.java` record: `(String prompt, int maxTokens, Double temperature)`. `AiTextResult.java` record: `(boolean success, String content, String errorMessage, Integer tokensUsed)`. 3 files total. |
| 147.8 | Create `NoOpAiProvider.java` | 147A | 147.7 | New file in `integration/ai/`. Annotate `@Component`. `providerId()` returns `"noop"`. `generateText()` returns `new AiTextResult(true, "", null, 0)`. `summarize()` returns `new AiTextResult(true, "", null, 0)`. `suggestCategories()` returns `List.of()`. `testConnection()` returns `new ConnectionTestResult(true, "noop", null)`. Unit tests: `NoOpAiProviderTest` — ~4 unit tests. |
| 147.9 | Create `IntegrationDomain.java` enum | 147B | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationDomain.java`. Enum: `ACCOUNTING, AI, DOCUMENT_SIGNING, PAYMENT`. Located at root of `integration/` package. |
| 147.10 | Create `ConnectionTestResult.java` shared record | 147B | 147.9 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ConnectionTestResult.java`. Record: `public record ConnectionTestResult(boolean success, String providerName, String errorMessage) {}`. Located at `integration/` root. |
| 147.11 | Create `integration/signing/` package with `SigningState.java` enum | 147B | 147.9 | New file in `integration/signing/`. Enum: `public enum SigningState { PENDING, SIGNED, DECLINED, EXPIRED }`. |
| 147.12 | Create `DocumentSigningProvider.java` interface + domain records | 147B | 147.10, 147.11 | New files in `integration/signing/`. `DocumentSigningProvider.java` interface: `String providerId()`, `SigningResult sendForSignature(SigningRequest request)`, `SigningStatus checkStatus(String signingReference)`, `byte[] downloadSigned(String signingReference)`, `ConnectionTestResult testConnection()`. `SigningRequest.java` record: `(byte[] documentBytes, String contentType, String signerName, String signerEmail, String callbackUrl)`. `SigningResult.java` record: `(boolean success, String signingReference, String errorMessage)`. `SigningStatus.java` record: `(SigningState state, String signingReference, Instant updatedAt)`. 4 new files. |
| 147.13 | Create `NoOpSigningProvider.java` | 147B | 147.12 | New file in `integration/signing/`. Annotate `@Component`. `providerId()` returns `"noop"`. `sendForSignature()` returns success with "NOOP-SIGN-" prefix. `checkStatus()` returns `new SigningStatus(SigningState.SIGNED, signingReference, Instant.now())`. `downloadSigned()` returns `new byte[0]`. `testConnection()` returns success. |
| 147.14 | Write unit tests for `NoOpAccountingProvider` and `NoOpSigningProvider` | 147B | 147.6, 147.13 | New file: `backend/src/test/java/.../integration/NoOpStubsTest.java`. Tests: (1) `accounting_syncInvoice_returns_success_with_noop_ref`. (2) `accounting_testConnection_success`. (3) `signing_sendForSignature_returns_success_ref`. (4) `signing_checkStatus_returns_SIGNED`. (5) `signing_downloadSigned_returns_empty_bytes`. (6) `ai_suggestCategories_empty_list`. ~6 unit tests total. |
| 147.15 | Update `NoOpAccountingProvider` and `NoOpAiProvider` imports for `ConnectionTestResult` | 147B | 147.10, 147.6, 147.8 | Modify `NoOpAccountingProvider.java` and `NoOpAiProvider.java`: add real import `import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;`. Remove any forward reference comment. Run `./mvnw -q clean compile` to confirm zero errors. |

### Key Files

**Slice 147A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingProvider.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/InvoiceSyncRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/LineItem.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/CustomerSyncRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingSyncResult.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/NoOpAccountingProvider.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiProvider.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiTextRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiTextResult.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/NoOpAiProvider.java`

**Slice 147B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationDomain.java` — Enum
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ConnectionTestResult.java` — Shared record
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/signing/SigningState.java` — Enum
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/signing/DocumentSigningProvider.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/signing/SigningRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/signing/SigningResult.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/signing/SigningStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/signing/NoOpSigningProvider.java`
- `backend/src/test/java/.../integration/NoOpStubsTest.java` — Unit tests (6 tests)

**Slice 147B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/NoOpAccountingProvider.java` — Add real `ConnectionTestResult` import
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/NoOpAiProvider.java` — Add real `ConnectionTestResult` import

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/PaymentProvider.java` — Existing port pattern to match
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/MockPaymentProvider.java` — Existing NoOp stub pattern
- Architecture doc Section 21.3 (interface signatures)

### Architecture Decisions

- **`IntegrationDomain` enum at `integration/` root**: The enum is shared infrastructure — all ports reference it.
- **`PAYMENT` in `IntegrationDomain`**: Allows `MockPaymentProvider` to be registered with the `IntegrationRegistry` via `@IntegrationAdapter`. Payment does NOT get a feature flag (always enabled).
- **NoOp stubs use `UUID.randomUUID().toString().substring(0, 8)` for refs**: Short enough to be readable in logs, unique enough for development tracing.
- **147A split from 147B**: 147A creates the two most likely "used first" ports (Accounting + AI). 147B completes the set with the `ConnectionTestResult` and `IntegrationDomain` shared types.

---

## Epic 148: OrgIntegration Entity + IntegrationRegistry + BYOAK Infrastructure

**Goal**: Create the `OrgIntegration` entity and repository, the `@IntegrationAdapter` custom annotation, and the `IntegrationRegistry` Spring component that discovers adapter beans at startup and resolves the active adapter per tenant at request time using a Caffeine cache. Also annotate `MockPaymentProvider` and the three NoOp stubs with `@IntegrationAdapter`. This is the BYOAK registry core.

**References**: Architecture doc Sections 21.4 (BYOAK Registry), 21.11 (Migration). ADR-089.

**Dependencies**: Epic 147 (all port interfaces and `IntegrationDomain` enum must exist for registry to be complete).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **148A** | 148.1–148.5 | V36 migration (`org_integrations` table) + `OrgIntegration` entity + `OrgIntegrationRepository` + `@IntegrationAdapter` annotation + annotate the 4 NoOp/stub adapters. ~5 new files, 4 modified. | **Done** (PR #307) |
| **148B** | 148.6–148.11 | `IntegrationRegistry` component (startup bean discovery, Caffeine cache, `resolve()`, `evict()`, `availableProviders()`) + unit tests for registry behavior. ~2 new files. | **Done** (PR #308) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 148.1 | Add `org_integrations` DDL to V36 migration | 148A | 146.1 (V36 file exists) | Modify: `backend/src/main/resources/db/migration/tenant/V36__create_integration_tables.sql`. Append: `CREATE TABLE org_integrations (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), domain VARCHAR(30) NOT NULL, provider_slug VARCHAR(50) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT FALSE, config_json JSONB, key_suffix VARCHAR(6), created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), CONSTRAINT uq_org_integrations_domain UNIQUE (domain));`. |
| 148.2 | Create `OrgIntegration.java` entity | 148A | 148.1, 147.9 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegration.java`. `@Entity @Table(name = "org_integrations")`. Fields: `UUID id`, `IntegrationDomain domain`, `String providerSlug`, `boolean enabled`, `String configJson`, `String keySuffix`, `Instant createdAt`, `Instant updatedAt`. Methods: `updateProvider()`, `setKeySuffix()`, `clearKeySuffix()`, `enable()`, `disable()`. Pattern: architecture doc Section 21.12 entity code. |
| 148.3 | Create `OrgIntegrationRepository.java` | 148A | 148.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegrationRepository.java`. Extends `JpaRepository<OrgIntegration, UUID>`. Method: `Optional<OrgIntegration> findByDomain(IntegrationDomain domain)`. |
| 148.4 | Create `@IntegrationAdapter` annotation | 148A | 147.9 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationAdapter.java`. `@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME) public @interface IntegrationAdapter { IntegrationDomain domain(); String slug(); }`. |
| 148.5 | Annotate existing adapter stubs with `@IntegrationAdapter` | 148A | 148.4, 147A, 147B | Modify 4 files: `NoOpAccountingProvider` → `@IntegrationAdapter(domain = ACCOUNTING, slug = "noop")`, `NoOpAiProvider` → `@IntegrationAdapter(domain = AI, slug = "noop")`, `NoOpSigningProvider` → `@IntegrationAdapter(domain = DOCUMENT_SIGNING, slug = "noop")`, `MockPaymentProvider` → `@IntegrationAdapter(domain = PAYMENT, slug = "mock")`. |
| 148.6 | Create `IntegrationRegistry.java` component | 148B | 148.4, 148.5 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationRegistry.java`. Annotate `@Component`. Startup: scan `applicationContext.getBeansWithAnnotation(IntegrationAdapter.class)`, populate `adapterMap`. Method `resolve(IntegrationDomain domain, Class<T> portInterface)`: cache key = `tenantSchema + ":" + domain.name()`, Caffeine `configCache.get(key, loader)` — loader returns `OrgIntegrationCacheEntry.EMPTY` (never null) when no DB row exists. If EMPTY or `!enabled`, return NoOp adapter. Else look up slug; if no match, fall back to NoOp. Method `availableProviders(IntegrationDomain domain)`: returns list of slugs. Method `evict(tenantSchema, domain)`: invalidates cache entry. Private `record OrgIntegrationCacheEntry(String providerSlug, boolean enabled, String configJson)` with `EMPTY` sentinel. |
| 148.7 | Write unit tests for `IntegrationRegistry` — adapter discovery | 148B | 148.6 | New file: `backend/src/test/java/.../integration/IntegrationRegistryTest.java`. Tests: (1) `discovers_annotated_beans_at_startup`. (2) `duplicate_slug_throws_at_startup`. (3) `resolve_returns_noop_when_no_config`. (4) `resolve_returns_noop_when_disabled`. ~4 unit tests. |
| 148.8 | Write integration test for `IntegrationRegistry` — cache behavior | 148B | 148.6 | Integration tests: (1) `cache_miss_hits_db_then_returns_noop`. (2) `evict_clears_cache_entry`. ~2 integration tests. |
| 148.9 | Verify `NoOpAccountingProvider` is returned by default | 148B | 148.6, 148.7 | Smoke test: for a fresh tenant with no `OrgIntegration` row, `integrationRegistry.resolve(ACCOUNTING, AccountingProvider.class)` returns `NoOpAccountingProvider`. Call `syncInvoice()` on the result and verify `success=true`. |
| 148.10 | Write Caffeine null-return guard assertion | 148B | 148.6 | Test that `resolve()` never triggers NPE. Call `resolve(ACCOUNTING, AccountingProvider.class)` for a domain with no DB row. If NPE is thrown, test fails. Covers the documented lesson: "Caffeine `Cache.get(key, loader)` throws NPE if loader returns null." |
| 148.11 | Add `Caffeine` dependency confirmation to `pom.xml` | 148B | | Read `backend/pom.xml`. Caffeine is already a dependency (used by `OrgSettingsService`). Confirm `com.github.ben-manes.caffeine:caffeine` is present. If not, add it. |

### Key Files

**Slice 148A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegration.java` — Entity
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegrationRepository.java` — JPA repository
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationAdapter.java` — Custom annotation

**Slice 148A — Modify:**
- `backend/src/main/resources/db/migration/tenant/V36__create_integration_tables.sql` — Add `org_integrations` table DDL
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/NoOpAccountingProvider.java` — Add `@IntegrationAdapter`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/NoOpAiProvider.java` — Add `@IntegrationAdapter`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/signing/NoOpSigningProvider.java` — Add `@IntegrationAdapter`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/MockPaymentProvider.java` — Add `@IntegrationAdapter(domain=PAYMENT, slug="mock")`

**Slice 148B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationRegistry.java` — Registry component
- `backend/src/test/java/.../integration/IntegrationRegistryTest.java` — Unit + integration tests (8 tests)

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` — Existing Caffeine cache pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/MockPaymentProvider.java` — Provider bean to annotate
- Architecture doc Section 21.4 (IntegrationRegistry code shown verbatim)

### Architecture Decisions

- **Startup fail-fast on duplicate slug**: If two adapter beans register with the same `domain+slug` combination, `IllegalStateException` at startup.
- **`OrgIntegrationCacheEntry.EMPTY` sentinel instead of null**: Caffeine's `Cache.get(key, loader)` throws `NullPointerException` if the loader returns null. Documented lesson in `MEMORY.md`.
- **Registry does NOT inject `SecretStore`**: Adapters inject `SecretStore` themselves at method invocation time.
- **`MockPaymentProvider` stays in `invoice/` package**: Per ADR-088, existing interfaces are not moved.

---

## Epic 149: Feature Flags + IntegrationGuardService

**Goal**: Extend `OrgSettings` with three boolean fields (`accountingEnabled`, `aiEnabled`, `documentSigningEnabled`). Add `ALTER TABLE org_settings` DDL to the V36 migration. Create `IntegrationGuardService` and `IntegrationDisabledException`. Update the `OrgSettings` DTO and `PUT /api/org-settings` to accept and return the three new flags.

**References**: Architecture doc Section 21.6 (Feature Flags). ADR-091.

**Dependencies**: None (OrgSettings entity exists from Phase 8, no new dependencies).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **149A** | 149.1–149.8 | V36 migration (ALTER TABLE org_settings) + OrgSettings entity extension + IntegrationGuardService + IntegrationDisabledException + DTO + API updates + tests. ~4 modified files, 2 new files. | **Done** (PR #309) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 149.1 | Add `ALTER TABLE org_settings` to V36 migration | 149A | | Modify: `backend/src/main/resources/db/migration/tenant/V36__create_integration_tables.sql`. Append: `ALTER TABLE org_settings ADD COLUMN accounting_enabled BOOLEAN NOT NULL DEFAULT FALSE; ALTER TABLE org_settings ADD COLUMN ai_enabled BOOLEAN NOT NULL DEFAULT FALSE; ALTER TABLE org_settings ADD COLUMN document_signing_enabled BOOLEAN NOT NULL DEFAULT FALSE;`. |
| 149.2 | Extend `OrgSettings.java` entity with 3 boolean fields | 149A | 149.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`. Add fields: `accountingEnabled`, `aiEnabled`, `documentSigningEnabled`. Add getters + mutation method `updateIntegrationFlags(boolean, boolean, boolean)`. |
| 149.3 | Create `IntegrationDisabledException.java` | 149A | 147.9 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationDisabledException.java`. Extends `ErrorResponseException`. Returns 403 ProblemDetail. Pattern: `PlanLimitExceededException.java`. |
| 149.4 | Create `IntegrationGuardService.java` | 149A | 149.2, 149.3 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationGuardService.java`. `@Service`. Method `requireEnabled(IntegrationDomain domain)`: switch on domain, check corresponding flag, throw `IntegrationDisabledException` if disabled. `PAYMENT` always returns true. |
| 149.5 | Update `OrgSettingsDto` / response record to include 3 boolean flags | 149A | 149.2 | Modify the OrgSettings response DTO. Add fields `accountingEnabled`, `aiEnabled`, `documentSigningEnabled`. |
| 149.6 | Update `PUT /api/org-settings` request to accept integration flags | 149A | 149.2 | Modify `OrgSettingsController.java` request DTO. Call `orgSettings.updateIntegrationFlags()` in the service. |
| 149.7 | Write unit tests for `IntegrationGuardService` | 149A | 149.4 | New test class: `IntegrationGuardServiceTest.java`. Tests: (1) `accounting_enabled_no_exception`. (2) `accounting_disabled_throws_403`. (3) `payment_always_allowed`. (4) `ai_disabled_throws`. ~4 unit tests. |
| 149.8 | Extend existing `OrgSettings` integration tests for new flags | 149A | 149.2, 149.5, 149.6 | Modify existing `OrgSettingsIntegrationTest.java`. Tests: (1) `get_org_settings_includes_integration_flags` — assert defaults are `false`. (2) `update_org_settings_sets_flags` — PUT + GET roundtrip. ~2 additional tests. |

### Key Files

**Slice 149A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationDisabledException.java` — 403 exception
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationGuardService.java` — Guard service
- `backend/src/test/java/.../integration/IntegrationGuardServiceTest.java` — 4 unit tests

**Slice 149A — Modify:**
- `backend/src/main/resources/db/migration/tenant/V36__create_integration_tables.sql` — ALTER TABLE org_settings
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` — 3 boolean fields + mutation method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java` — Response DTO + request DTO
- Existing `OrgSettingsIntegrationTest.java` — Add 2 test assertions

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` — Existing entity structure
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java` — Controller + DTO pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/PlanLimitExceededException.java` — `ErrorResponseException` extension pattern
- Architecture doc Section 21.6 (`IntegrationGuardService` code)

### Architecture Decisions

- **Boolean columns on `OrgSettings` not a generic feature flag table**: Three purpose-built columns are simpler, type-safe, and leverage existing caching. See ADR-091.
- **`PAYMENT` always returns `true`**: Payment is existing core functionality — no admin should be able to disable it.
- **`IntegrationGuardService` as a separate service, not an AOP aspect**: Simpler explicit call pattern.

---

## Epic 150: Integration Management API (Controller + Service)

**Goal**: Create `IntegrationService` that orchestrates all integration configuration operations (upsert provider, set API key, remove API key, test connection, toggle enable/disable). Create `IntegrationController` that exposes all 7 endpoints under `/api/integrations`. Add request/response DTOs and `@PreAuthorize` security.

**References**: Architecture doc Section 21.7 (API Surface). Sequence diagrams in Section 21.9.

**Dependencies**: Epic 146 (SecretStore for set-key operation), Epic 148 (IntegrationRegistry for test + provider listing).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **150A** | 150.1–150.6 | `IntegrationService` with all domain operations: upsert, set-key, delete-key, test, toggle + cache eviction. ~2 new files. | |
| **150B** | 150.7–150.12 | `IntegrationController` + request/response DTOs + `@PreAuthorize` + MockMvc integration tests for all 7 endpoints. ~2 new files (1 test file). | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 150.1 | Create `OrgIntegrationDto.java` response record | 150A | 148.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegrationDto.java`. Record: `public record OrgIntegrationDto(String domain, String providerSlug, boolean enabled, String keySuffix, String configJson, Instant updatedAt) {}`. Static factory `from(OrgIntegration)`. |
| 150.2 | Create `IntegrationService.java` — upsert and toggle operations | 150A | 148.2, 148.3, 148.6 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationService.java`. `@Service`. Methods: `upsertIntegration(domain, providerSlug, configJson)`, `toggleIntegration(domain, enabled)`. Both evict registry cache after save. |
| 150.3 | Add set-key and delete-key to `IntegrationService` | 150A | 150.2, 146A | Methods: `setApiKey(domain, apiKey)` — secret key convention: `"{domain}:{providerSlug}:api_key"`. Stores via `secretStore.store()`. Updates `OrgIntegration.keySuffix` (last 6 chars). `deleteApiKey(domain)` — deletes secret + clears suffix. |
| 150.4 | Add test-connection to `IntegrationService` | 150A | 150.2, 148.6 | Method: `testConnection(domain)` — resolves adapter via registry, calls `testConnection()`, returns `ConnectionTestResult`. |
| 150.5 | Add `listAllIntegrations()` to `IntegrationService` | 150A | 150.1 | Iterates all `IntegrationDomain` values, returns synthesized DTOs for unconfigured domains. |
| 150.6 | Add `availableProviders()` to `IntegrationService` | 150A | 148.6 | Returns `Map<String, List<String>>` of domain → available slugs from registry. |
| 150.7 | Create `IntegrationController.java` | 150B | 150A | New file. `@RestController @RequestMapping("/api/integrations") @PreAuthorize("hasAnyRole('ROLE_ORG_ADMIN', 'ROLE_ORG_OWNER')")`. 7 endpoints: `GET /`, `GET /providers`, `PUT /{domain}`, `POST /{domain}/set-key`, `POST /{domain}/test`, `DELETE /{domain}/key`, `PATCH /{domain}/toggle`. |
| 150.8 | Create request/response DTO inner records in controller | 150B | 150.7 | Inner records: `UpsertIntegrationRequest(providerSlug, configJson)`, `SetApiKeyRequest(@NotBlank apiKey)`, `ToggleRequest(enabled)`. |
| 150.9 | Write MockMvc integration tests for GET endpoints | 150B | 150.7, 150.8 | New file: `IntegrationControllerTest.java`. Tests: (1) `list_integrations_returns_all_four_domains`. (2) `list_providers_returns_noop_for_new_domains`. (3) `member_cannot_access_integrations_api` — 403. ~3 tests. |
| 150.10 | Write MockMvc tests for PUT, POST, PATCH, DELETE endpoints | 150B | 150.7 | Tests: (4) `put_upserts_integration_config`. (5) `post_set_key_returns_204`. (6) `post_test_connection_returns_success`. (7) `delete_key_removes_key_suffix`. (8) `patch_toggle_enables_integration`. (9) `set_key_blank_returns_400`. ~6 tests. |
| 150.11 | Verify API key is never returned in any response | 150B | 150.10 | Assertions in existing tests: after `set-key`, GET response does NOT contain the API key value. Verify `keySuffix` contains only last chars. |
| 150.12 | Validate `@PreAuthorize` coverage at controller level | 150B | 150.7 | Test: `admin_can_access_all_endpoints` — verify both ADMIN and OWNER roles are accepted. |

### Key Files

**Slice 150A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegrationDto.java` — Response record
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationService.java` — Service

**Slice 150B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationController.java` — REST controller
- `backend/src/test/java/.../integration/IntegrationControllerTest.java` — 12 MockMvc integration tests

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java` — Controller pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java` — Audit service for Epic 151
- Architecture doc Section 21.7 (endpoint contracts)

### Architecture Decisions

- **Controller-level `@PreAuthorize`**: The annotation at class level applies to all handler methods.
- **Secret key naming convention**: `"{domain}:{providerSlug}:api_key"` — deterministic, human-readable.
- **Synthesized DTOs for unconfigured domains**: `GET /api/integrations` always returns all 4 domains.
- **Cache eviction on upsert and toggle**: Provides immediate consistency for the admin flow.

---

## Epic 151: Audit Integration for Config Events

**Goal**: Wire 6 audit event types into `IntegrationService`. All integration configuration changes are logged to the audit trail. API keys must never appear in audit details.

**References**: Architecture doc Section 21.10 (Audit Integration). Existing `AuditEventBuilder` pattern from Phase 6.

**Dependencies**: Epic 150 (IntegrationService must exist).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **151A** | 151.1–151.6 | Inject `AuditService` into `IntegrationService`, add 6 audit event calls, write integration test assertions. ~2 files modified, 1 test file extended. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 151.1 | Inject `AuditService` into `IntegrationService` | 151A | 150A | Add `AuditService auditService` to constructor parameters. |
| 151.2 | Add `integration.configured` audit event | 151A | 151.1 | In `upsertIntegration()` (after save + evict): log `eventType("integration.configured")`, details: domain, providerSlug, previousProvider. |
| 151.3 | Add `integration.key_set` and `integration.key_removed` audit events | 151A | 151.1 | In `setApiKey()`: log `eventType("integration.key_set")`, details: domain only (NEVER log key value). In `deleteApiKey()`: log `eventType("integration.key_removed")`. |
| 151.4 | Add `integration.enabled` and `integration.disabled` audit events | 151A | 151.1 | In `toggleIntegration()`: log `eventType("integration.enabled")` or `eventType("integration.disabled")`, details: domain, providerSlug. |
| 151.5 | Add `integration.connection_tested` audit event | 151A | 151.1 | In `testConnection()`: log `eventType("integration.connection_tested")`, details: domain, providerSlug, success, errorMessage. |
| 151.6 | Write audit event assertions in integration tests | 151A | 151.2–151.5 | Modify `IntegrationControllerTest.java`. After key operations, query audit events and assert: (1) After PUT — `integration.configured` exists. (2) After POST set-key — `integration.key_set` exists, details do NOT contain the API key string. (3) After DELETE key — `integration.key_removed` exists. (4) After PATCH toggle — `integration.enabled`/`integration.disabled` exists. ~4 additional assertions. |

### Key Files

**Slice 151A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationService.java` — Add `AuditService` injection + 6 event calls
- `backend/src/test/java/.../integration/IntegrationControllerTest.java` — Add 4 audit assertions

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java` — Service to inject
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` — Builder pattern
- An existing service that uses audit (e.g., `InvoiceService.java`) — for the audit call pattern

### Architecture Decisions

- **Audit in separate epic**: Follows the project's established pattern from Phase 6.
- **API key value NEVER logged**: Only domain name in audit details for key operations.
- **`entityType = "org_integration"`**: Consistent entity type for all integration audit events.

---

## Epic 152: Integrations Settings UI

**Goal**: Create the frontend integrations settings page at `/org/[slug]/settings/integrations`. Build `IntegrationCard`, `SetApiKeyDialog`, and `ConnectionTestButton` components. Create the `lib/api/integrations.ts` API client. Update settings sidebar navigation.

**References**: Architecture doc Sections 21.7 (API Surface), 21.6 (Frontend Gating), 21.12 (Frontend Changes).

**Dependencies**: Epic 149 (OrgSettings flags in API response), Epic 150 (Integration endpoints exist).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **152A** | 152.1–152.6 | `lib/api/integrations.ts` API client + OrgSettings TypeScript type extension + settings sidebar nav update + types. ~4 modified/new files. | |
| **152B** | 152.7–152.13 | Integrations settings page + `IntegrationCard` + `SetApiKeyDialog` + `ConnectionTestButton` components + tests. ~6 new files. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 152.1 | Create `lib/api/integrations.ts` API client | 152A | | New file. Functions: `listIntegrations()`, `listProviders()`, `upsertIntegration()`, `setApiKey()`, `testConnection()`, `deleteApiKey()`, `toggleIntegration()`. All call base API client with Bearer JWT. Pattern: follow existing `lib/api/` style. |
| 152.2 | Define TypeScript types for integration domain | 152A | | New file: `frontend/lib/types/integrations.ts`. Types: `IntegrationDomain`, `OrgIntegration`, `ConnectionTestResult`. |
| 152.3 | Extend OrgSettings TypeScript type with integration flags | 152A | | Modify OrgSettings interface: add `accountingEnabled`, `aiEnabled`, `documentSigningEnabled`. |
| 152.4 | Update settings sidebar navigation to include Integrations | 152A | | Add nav item: `{ label: "Integrations", href: "/org/[slug]/settings/integrations", icon: Plug }`. |
| 152.5 | Create route directory structure | 152A | | Create `frontend/app/(app)/org/[slug]/settings/integrations/` directory. Page created in 152B. |
| 152.6 | Write TypeScript type tests | 152A | 152.2, 152.3 | Compile-time type assertions using `satisfies`. ~3 type-level tests. |
| 152.7 | Create integrations settings page `page.tsx` | 152B | 152.1, 152A | Server Component. Fetch `listIntegrations()` and `listProviders()` server-side. Render 4 `IntegrationCard` components in 2-column grid. |
| 152.8 | Create `IntegrationCard.tsx` component | 152B | 152.7 | `"use client"`. Renders: domain icon, status badge, provider selector, API key section (masked), enable/disable switch, connection test button. |
| 152.9 | Create `SetApiKeyDialog.tsx` component | 152B | 152.8 | `"use client"`. Shadcn Dialog. Input `type="password"`. Controlled open state (revalidates, doesn't redirect). |
| 152.10 | Create `ConnectionTestButton.tsx` component | 152B | 152.8 | `"use client"`. Button with loading state. Shows success/error toast. |
| 152.11 | Create integration card actions in `actions.ts` | 152B | 152.1 | Server actions: `upsertIntegrationAction()`, `setApiKeyAction()`, `deleteApiKeyAction()`, `toggleIntegrationAction()`. Each calls API client then `revalidatePath()`. |
| 152.12 | Write component tests for `IntegrationCard` | 152B | 152.8 | Tests: (1) `renders_not_configured_badge`. (2) `renders_active_badge`. (3) `shows_masked_key_suffix`. (4) `set_api_key_button_opens_dialog`. `afterEach(() => cleanup())` for Radix leak prevention. ~4 tests. |
| 152.13 | Write page-level smoke test | 152B | 152.7 | Mock API, render page, assert 4 cards rendered. ~3 assertions. |

### Key Files

**Slice 152A — Create:**
- `frontend/lib/api/integrations.ts` — API client (7 functions)
- `frontend/lib/types/integrations.ts` — TypeScript types
- `frontend/__tests__/lib/types/integrations.test.ts` — Type shape assertions

**Slice 152A — Modify:**
- `frontend/lib/types/org-settings.ts` — Add 3 boolean flag fields
- Settings sidebar nav file — Add Integrations link

**Slice 152B — Create:**
- `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` — Server Component page
- `frontend/app/(app)/org/[slug]/settings/integrations/actions.ts` — Server actions
- `frontend/components/integrations/IntegrationCard.tsx` — Card component
- `frontend/components/integrations/SetApiKeyDialog.tsx` — API key entry dialog
- `frontend/components/integrations/ConnectionTestButton.tsx` — Test button + toast
- `frontend/__tests__/components/integrations/IntegrationCard.test.tsx` — 4 component tests
- `frontend/__tests__/app/settings/integrations.test.tsx` — 3 smoke tests

**Read for context:**
- Settings sidebar nav structure (e.g., `frontend/lib/nav-items.ts`)
- Any existing `frontend/app/(app)/org/[slug]/settings/**` page — Server Component pattern
- `frontend/lib/types/org-settings.ts` — OrgSettings interface to extend
- `frontend/lib/api.ts` — Base API client
- Architecture doc Section 21.12 (Frontend Changes table)

### Architecture Decisions

- **`"use client"` on `IntegrationCard` not the page**: Page is Server Component for data fetching. Cards need interactivity.
- **`type="password"` on API key input**: Never render API keys in cleartext.
- **Server actions for mutations**: Following existing `actions.ts` pattern.
- **Radix Dialog cleanup**: `afterEach(() => cleanup())` required per documented lesson.
- **`SetApiKeyDialog` controlled open state**: Revalidates, doesn't redirect — needs controlled state.

---

## Summary

| Epic | New Backend Files | Modified Backend Files | New Frontend Files | Modified Frontend Files |
|------|-------------------|----------------------|--------------------|------------------------|
| 145 | 4 | 6 | 0 | 0 |
| 146 | 6 | 2 | 0 | 0 |
| 147 | 18 | 2 | 0 | 0 |
| 148 | 5 | 5 | 0 | 0 |
| 149 | 3 | 4 | 0 | 0 |
| 150 | 4 | 0 | 0 | 0 |
| 151 | 0 | 2 | 0 | 0 |
| 152 | 0 | 0 | 9 | 2 |
| **Total** | **40** | **21** | **9** | **2** |

### Test Count Estimate

| Epic | New Tests |
|------|-----------|
| 145 | 4 (contract) |
| 146 | 8 (integration) |
| 147 | 10 (unit) |
| 148 | 8 (unit + integration) |
| 149 | 8 (unit + integration) |
| 150 | 14 (MockMvc) |
| 151 | 4 (audit assertions) |
| 152 | 10 (component + type) |
| **Total** | **~66 new tests** |
