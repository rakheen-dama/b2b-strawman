# Phase 7 -- Customer Portal Backend Prototype

Phase 7 takes the existing customer portal MVP (built in Phase 4, Epic 43) and evolves it into a **production-grade backend prototype** with persistent magic-link authentication, a dedicated read-model schema driven by domain events, new portal API endpoints (comments, summaries, profile), and a Thymeleaf developer test harness. All changes are backend-only -- no frontend epics in this phase. See `architecture/phase7-customer-portal-backend.md` (Section 13 of architecture/ARCHITECTURE.md) and [ADR-030](../adr/ADR-030-magic-link-auth-for-customers.md)--[ADR-033](../adr/ADR-033-local-only-thymeleaf-test-harness.md) for design details.

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 54 | PortalContact & Persistent Magic Links | Backend | -- | M | 54A, 54B | **Done** (PR #123, #124) |
| 55 | Portal Read-Model Schema & DataSource | Backend | 54 | M | 55A, 55B | **Done** (PR #125, #126) |
| 56 | Domain Events & Event Handlers | Backend | 55 | L | 56A, 56B, 56C | **Done** (PR #127, #128, #129) |
| 57 | Portal Comments, Summary & Profile APIs | Backend | 56 | M | 57A, 57B | **Done** (PR #130, #131) |
| 58 | Thymeleaf Dev Harness | Backend | 54, 57 | S | 58A | |

## Dependency Graph

```
[E54 PortalContact & Magic Links] ──────────────┬──► [E55 Read-Model Schema & DataSource]
                                                  │         │
                                                  │         └──► [E56 Domain Events & Handlers]
                                                  │                       │
                                                  │                       └──► [E57 Portal APIs]
                                                  │                                  │
                                                  └──────────────────────────────────┤
                                                                                     └──► [E58 Thymeleaf Harness]
```

**Sequential chain**: Epics 54 through 57 form a strict dependency chain. Epic 58 (Thymeleaf) depends on both 54 (for MagicLinkService) and 57 (for portal APIs to exercise). There are no parallel tracks in Phase 7 because each epic builds on the infrastructure from the previous one.

## Implementation Order

### Stage 1: Authentication Foundation

| Order | Epic | Rationale |
|-------|------|-----------|
| 1 | Epic 54: PortalContact & Persistent Magic Links | New tenant-schema entities + refactored auth flow. Prerequisite for all other Phase 7 work. |

### Stage 2: Read-Model Infrastructure

| Order | Epic | Rationale |
|-------|------|-----------|
| 2 | Epic 55: Portal Read-Model Schema & DataSource | Creates the `portal` schema, tables, second DataSource, and JDBC repository. Required before event handlers can write projections. |

### Stage 3: Event Infrastructure

| Order | Epic | Rationale |
|-------|------|-----------|
| 3 | Epic 56: Domain Events & Event Handlers | Defines event hierarchy, instruments core services to publish events, and implements event handlers that write to the read model. Must follow Stage 2 (read-model tables must exist). |

### Stage 4: Portal APIs + Harness (Sequential)

| Order | Epic | Rationale |
|-------|------|-----------|
| 4 | Epic 57: Portal Comments, Summary & Profile APIs | New portal endpoints reading from the read-model schema. Requires event handlers to be populating the read model. |
| 5 | Epic 58: Thymeleaf Dev Harness | Dev-only test UI exercising the full portal flow. Last because it depends on all portal APIs. |

### Timeline

```
Stage 1:  [E54]                              <- auth foundation (must complete first)
Stage 2:  [E55]                              <- read-model schema (after E54)
Stage 3:  [E56]                              <- events + handlers (after E55)
Stage 4:  [E57]                              <- portal APIs (after E56)
Stage 5:  [E58]                              <- dev harness (after E54 + E57)
```

---

## Epic 54: PortalContact & Persistent Magic Links

**Goal**: Create the `portal_contacts` and `magic_link_tokens` tables in the tenant schema, implement `PortalContact` and `MagicLinkToken` entities, refactor `MagicLinkService` to use DB-backed tokens with SHA-256 hashing, update `PortalAuthController` to resolve via `PortalContact` instead of direct `Customer` lookup, add `PORTAL_CONTACT_ID` to `RequestScopes`, and add `customer_visible` column to tasks. Includes rate limiting (3 tokens per contact per 5 minutes) and audit fields (`created_ip`, `used_at`).

**References**: [ADR-030](../adr/ADR-030-magic-link-auth-for-customers.md), `architecture/phase7-customer-portal-backend.md` Section 13.2.1, 13.3.1, 13.8.1

**Dependencies**: None (builds on existing portal package from Phase 4)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **54A** | 54.1--54.6 | V14 tenant migration, PortalContact entity + repo + service, PortalContact integration tests | |
| **54B** | 54.7--54.13 | MagicLinkToken entity + repo, refactored MagicLinkService (DB-backed), updated PortalAuthController, updated RequestScopes + CustomerAuthFilter, auth flow integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 54.1 | Create V14 tenant migration for portal_contacts, magic_link_tokens, and tasks.customer_visible | 54A | | `db/migration/tenant/V14__create_portal_contacts_and_magic_link_tokens.sql`. Three parts: (1) `portal_contacts` table: `id` (UUID PK DEFAULT gen_random_uuid()), `org_id` (VARCHAR(255) NOT NULL), `customer_id` (UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE), `email` (VARCHAR(255) NOT NULL), `display_name` (VARCHAR(255)), `role` (VARCHAR(20) NOT NULL DEFAULT 'GENERAL'), `status` (VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'), `tenant_id` (VARCHAR(255)), `created_at` (TIMESTAMPTZ NOT NULL DEFAULT now()), `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT now()). UNIQUE(email, customer_id). Indexes: `idx_portal_contacts_email_org`, `idx_portal_contacts_customer`, `idx_portal_contacts_tenant`. RLS policy. (2) `magic_link_tokens` table: `id` (UUID PK DEFAULT gen_random_uuid()), `portal_contact_id` (UUID NOT NULL REFERENCES portal_contacts(id) ON DELETE CASCADE), `token_hash` (VARCHAR(64) NOT NULL), `expires_at` (TIMESTAMP NOT NULL), `used_at` (TIMESTAMP), `created_at` (TIMESTAMPTZ NOT NULL DEFAULT now()), `created_ip` (VARCHAR(45)). UNIQUE(token_hash). Indexes: `idx_magic_link_tokens_contact_created`, `idx_magic_link_tokens_expires`. No `tenant_id` column on tokens -- cross-tenant lookup by hash. (3) `ALTER TABLE tasks ADD COLUMN customer_visible BOOLEAN NOT NULL DEFAULT false`. Pattern: follow `V9__create_customers.sql` for table + index + RLS structure. |
| 54.2 | Create PortalContact entity | 54A | | `portal/PortalContact.java` -- JPA entity mapped to `portal_contacts`. Fields: UUID id, orgId (String, NOT NULL), customerId (UUID, NOT NULL), email (String, NOT NULL), displayName (String, nullable), role (enum ContactRole: PRIMARY/BILLING/GENERAL), status (enum ContactStatus: ACTIVE/SUSPENDED/ARCHIVED), tenantId (String), createdAt (Instant), updatedAt (Instant). Annotations: `@FilterDef`/`@Filter` for `tenantFilter`, `@EntityListeners(TenantAwareEntityListener.class)`, implements `TenantAware`. Inner enums: `ContactRole`, `ContactStatus`. Pattern: follow `customer/Customer.java` entity structure exactly. |
| 54.3 | Create PortalContactRepository | 54A | | `portal/PortalContactRepository.java` -- extends `JpaRepository<PortalContact, UUID>`. Methods: `Optional<PortalContact> findOneById(UUID id)` (JPQL `@Query` for `@Filter` compatibility -- CRITICAL, do NOT use `findById()`), `Optional<PortalContact> findByEmailAndOrgId(String email, String orgId)` (JPQL), `List<PortalContact> findByCustomerId(UUID customerId)` (JPQL), `boolean existsByEmailAndCustomerId(String email, UUID customerId)`. Pattern: follow `customer/CustomerRepository.java` with JPQL `findOneById`. |
| 54.4 | Create PortalContactService | 54A | | `portal/PortalContactService.java`. Constructor injection of `PortalContactRepository`, `CustomerRepository`. Methods: `createContact(String orgId, UUID customerId, String email, String displayName, PortalContact.ContactRole role)` -- validates customer exists via `customerRepository.findOneById()`, checks unique email+customer via `existsByEmailAndCustomerId()`, creates and saves PortalContact. `listContactsForCustomer(UUID customerId)` -- returns `findByCustomerId()`. `findByEmailAndOrg(String email, String orgId)` -- delegates to repo. `suspendContact(UUID contactId)` -- sets status=SUSPENDED. `archiveContact(UUID contactId)` -- sets status=ARCHIVED. All `@Transactional`. Pattern: follow `customer/CustomerService.java` style. |
| 54.5 | Add PortalContact integration tests | 54A | | `portal/PortalContactIntegrationTest.java`. ~10 tests: create contact (verify persisted correctly), create with duplicate email+customer (409), create with email that exists on a different customer (success -- email can appear across customers), find by email and org, list contacts for customer, suspend contact (status changes), archive contact (status changes), tenant isolation (contact in tenant A invisible in tenant B), contact created_by customer not found (404), verify org_id is populated correctly. Seed: provision tenant, sync member, create customer in `@BeforeAll`. Pattern: follow `portal/PortalAuthIntegrationTest.java` setup. |
| 54.6 | Add PORTAL_CONTACT_ID to RequestScopes | 54A | | Modify `multitenancy/RequestScopes.java` -- add `public static final ScopedValue<UUID> PORTAL_CONTACT_ID = ScopedValue.newInstance()` and `requirePortalContactId()` convenience method (throws `IllegalStateException` if not bound). Pattern: follow existing `CUSTOMER_ID` pattern. |
| 54.7 | Create MagicLinkToken entity | 54B | | `portal/MagicLinkToken.java` -- JPA entity mapped to `magic_link_tokens`. Fields: UUID id, portalContactId (UUID, NOT NULL), tokenHash (String, NOT NULL), expiresAt (Instant, NOT NULL), usedAt (Instant, nullable), createdAt (Instant), createdIp (String, nullable). No `@FilterDef`/`@Filter` -- no `tenant_id` column, tokens are looked up by hash across all tenants. No `TenantAware`. Constructor: `MagicLinkToken(UUID portalContactId, String tokenHash, Instant expiresAt, String createdIp)`. Method: `markUsed()` -- sets `usedAt = Instant.now()`. `isExpired()` -- `Instant.now().isAfter(expiresAt)`. `isUsed()` -- `usedAt != null`. Pattern: follow `Customer.java` but without tenant filter annotations. |
| 54.8 | Create MagicLinkTokenRepository | 54B | | `portal/MagicLinkTokenRepository.java` -- extends `JpaRepository<MagicLinkToken, UUID>`. Methods: `Optional<MagicLinkToken> findByTokenHash(String tokenHash)`, `long countByPortalContactIdAndCreatedAtAfter(UUID portalContactId, Instant after)` (for rate limiting -- count tokens created in last 5 min), `List<MagicLinkToken> findByPortalContactIdOrderByCreatedAtDesc(UUID portalContactId)`. No JPQL needed since there is no `@Filter` on this entity. |
| 54.9 | Refactor MagicLinkService to use DB-backed tokens | 54B | | Modify `portal/MagicLinkService.java`. Replace JWT+Caffeine approach entirely. New methods: `generateToken(UUID portalContactId, String createdIp)` -- (1) rate-limit check via `tokenRepository.countByPortalContactIdAndCreatedAtAfter(contactId, Instant.now().minusMinutes(5))` -- if > 3, throw `PortalAuthException("Too many login attempts")`, (2) generate 32-byte cryptographically random token via `SecureRandom`, (3) SHA-256 hash via `MessageDigest`, (4) store `MagicLinkToken(contactId, hexHash, Instant.now().plus(15, ChronoUnit.MINUTES), createdIp)`, (5) return raw token string (Base64 URL-safe). `verifyAndConsumeToken(String rawToken)` -- hash the raw token, look up by hash, validate not expired/used, mark as used, return `portalContactId`. Remove: `consumedTokens` Caffeine cache, JWT generation/verification. Remove `CustomerIdentity` record -- replace with `UUID` return (portalContactId). Remove constructor `@Value` for `portal.magic-link.secret` -- no longer needs a signing secret. Inject `MagicLinkTokenRepository` and `PortalContactRepository`. |
| 54.10 | Update PortalAuthController for PortalContact-based flow | 54B | | Modify `portal/PortalAuthController.java`. `requestMagicLink()`: resolve org -> schema, within tenant scope find `PortalContact` by email+orgId (not `Customer` by email), check contact status (SUSPENDED/ARCHIVED -> 403), call `magicLinkService.generateToken(contact.getId(), request.getRemoteAddr())`. Response: always return generic message; in dev profile (`@Value("${spring.profiles.active:}")`) also return magicLink URL. `exchangeToken()`: call `magicLinkService.verifyAndConsumeToken(token)`, load `PortalContact`, resolve org schema, verify customer still ACTIVE, issue portal JWT with `customerId` from contact. Update DTOs: `MagicLinkResponse` gains optional `magicLink` field, `PortalTokenResponse` gains `customerName` field. Inject `PortalContactService` or `PortalContactRepository`. Remove `CustomerRepository` direct dependency. |
| 54.11 | Update CustomerAuthFilter to bind PORTAL_CONTACT_ID | 54B | | Modify `portal/CustomerAuthFilter.java`. After verifying portal JWT and resolving tenant, optionally load `PortalContact` by customer_id + org_id to bind `PORTAL_CONTACT_ID`. Add `RequestScopes.PORTAL_CONTACT_ID` to the `ScopedValue.where()` chain. This is optional for Phase 7 (not all portal endpoints need contact ID), but binding it now enables future use without re-touching the filter. If contact lookup fails (customer exists but has no PortalContact), continue without binding PORTAL_CONTACT_ID -- backward compatible with Phase 4 portal sessions. |
| 54.12 | Add MagicLinkToken + refactored auth flow integration tests | 54B | | `portal/MagicLinkTokenIntegrationTest.java`. ~12 tests: generate token and verify (success), generate token and verify returns portal_contact_id, token single-use enforcement (second verify fails), expired token rejected, rate limiting (4th token in 5 min fails), tampered token hash rejected, request-link endpoint returns 200 with message for existing contact, request-link endpoint returns 200 for non-existent email (no enumeration), exchange endpoint with valid token returns portal JWT + customerId + customerName, exchange endpoint with invalid token returns 401, exchange for suspended contact returns 403, full round-trip: request-link -> extract token -> exchange -> use JWT on /portal/projects. Seed: provision tenant, sync member, create customer, create PortalContact in `@BeforeAll`. Pattern: follow `PortalAuthIntegrationTest.java`. |
| 54.13 | Add cross-tenant portal isolation tests | 54B | | ~3 tests: PortalContact in org A cannot be looked up via org B's auth flow, token generated for org A cannot be exchanged against org B, portal JWT from org A cannot access org B's data. Pattern: provision two tenants, create contacts in each. Follow `PortalAuthIntegrationTest.java` for security assertions. |

### Key Files

**Slice 54A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V14__create_portal_contacts_and_magic_link_tokens.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContact.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContactRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContactService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/PortalContactIntegrationTest.java`

**Slice 54A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- Add `PORTAL_CONTACT_ID` ScopedValue

**Slice 54A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java` -- Entity pattern reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java` -- JPQL findOneById pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAware.java` -- Interface to implement
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAwareEntityListener.java` -- Entity listener
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/PortalAuthIntegrationTest.java` -- Test setup pattern
- `backend/src/main/resources/db/migration/tenant/V9__create_customers.sql` -- Migration pattern

**Slice 54B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkToken.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkTokenRepository.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkTokenIntegrationTest.java`

**Slice 54B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkService.java` -- Full refactor: JWT+Caffeine -> DB-backed tokens
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalAuthController.java` -- PortalContact-based lookup
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/CustomerAuthFilter.java` -- Bind PORTAL_CONTACT_ID

**Slice 54B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalJwtService.java` -- Unchanged, but issueToken() signature may need customerId from PortalContact
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMappingRepository.java` -- Org -> schema resolution
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ScopedFilterChain.java` -- ScopedValue binding pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/PortalAuthIntegrationTest.java` -- Existing auth tests to verify backward compatibility

### Architecture Decisions

- **`portal/` package reuse**: PortalContact and MagicLinkToken live alongside existing portal classes (MagicLinkService, PortalAuthController, etc.) since they are part of the same bounded context.
- **No `tenant_id` on MagicLinkToken**: Tokens are looked up by `token_hash` during the exchange flow before tenant context is known. The `portal_contact_id` FK provides the tenant association.
- **SHA-256 hash storage**: Raw tokens are never persisted. Only the SHA-256 hex digest is stored. The raw token is transmitted once in the magic link URL.
- **Rate limiting via SQL count**: `COUNT(*) WHERE portal_contact_id = ? AND created_at > ?` is simple, correct, and uses the `idx_magic_link_tokens_contact_created` index.
- **PortalContact in tenant schema**: It is authoritative data managed by staff, not a derived read model. Lives alongside customers, not in the portal read-model schema.
- **Two-slice decomposition**: 54A (migration + PortalContact entity + repo + service + tests) is the foundation. 54B (MagicLinkToken + refactored MagicLinkService + updated controllers + auth flow tests) layers the new auth mechanism on top. Each slice touches ~6-8 files.

---

## Epic 55: Portal Read-Model Schema & DataSource

**Goal**: Create the dedicated `portal` schema in the global database, define the four read-model tables (`portal_projects`, `portal_documents`, `portal_comments`, `portal_project_summaries`), configure a second DataSource and `JdbcClient` for the portal schema, and implement the `PortalReadModelRepository` with JDBC-based CRUD operations. This epic provides the write/read infrastructure that event handlers (Epic 56) will use.

**References**: [ADR-031](../adr/ADR-031-separate-portal-read-model-schema.md), `architecture/phase7-customer-portal-backend.md` Section 13.2.2, 13.8.2, 13.9.4

**Dependencies**: Epic 54 (PortalContact entity needed for customer_id references in read-model design)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **55A** | 55.1--55.4 | V7 global migration, PortalDataSourceConfig, portal JdbcClient bean, basic connectivity test | |
| **55B** | 55.5--55.9 | PortalReadModelRepository (upsert/delete/query methods), read-model view records, integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 55.1 | Create V7 global migration for portal schema | 55A | | `db/migration/global/V7__create_portal_schema.sql`. Creates `portal` schema and four tables: `portal.portal_projects` (composite PK `(id, customer_id)`, org_id, name, status, description, document_count, comment_count, created_at, updated_at), `portal.portal_documents` (UUID PK, org_id, customer_id, portal_project_id, title, content_type, size, scope, s3_key, uploaded_at, synced_at), `portal.portal_comments` (UUID PK, org_id, portal_project_id, author_name, content, created_at, synced_at), `portal.portal_project_summaries` (composite PK `(id, customer_id)`, org_id, total_hours DECIMAL(10,2), billable_hours DECIMAL(10,2), last_activity_at, synced_at). Indexes per Section 13.8.2. No RLS policies -- access control is application-layer. Pattern: follow `V6__add_subscriptions.sql` for global migration structure. |
| 55.2 | Create PortalDataSourceConfig | 55A | | `config/PortalDataSourceConfig.java`. `@Configuration` class. Creates a `@Bean @Qualifier("portalDataSource") DataSource` pointing to the same Postgres instance but with `search_path` set to `portal`. Implementation: use `HikariDataSource` with connection-init-sql `SET search_path TO portal, public`. Re-use the same JDBC URL, username, password from the primary DataSource (inject from `spring.datasource.*` properties). Create `@Bean @Qualifier("portalJdbcClient") JdbcClient` using the portal DataSource. Pattern: follow existing `HikariDataSource` bean configuration in the codebase. |
| 55.3 | Verify Flyway runs the global V7 migration | 55A | | Ensure the existing `FlywayConfig` runs global migrations on startup. The global migration path `db/migration/global/` should already be configured. Verify that the `portal` schema and its tables exist after application startup in a test. If Flyway global migration is not triggered automatically, configure it explicitly. |
| 55.4 | Add portal DataSource connectivity integration test | 55A | | `config/PortalDataSourceConfigIntegrationTest.java`. ~4 tests: portal JdbcClient is injectable, portal JdbcClient can query `portal.portal_projects` (empty result), portal JdbcClient can insert and read back a row in `portal.portal_projects`, portal DataSource is distinct from the primary DataSource (different search_path). Pattern: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` + inject `@Qualifier("portalJdbcClient") JdbcClient`. |
| 55.5 | Create read-model view records | 55B | | `customerbackend/model/PortalProjectView.java` -- Java record: `UUID id, String orgId, UUID customerId, String name, String status, String description, int documentCount, int commentCount, Instant createdAt, Instant updatedAt`. `customerbackend/model/PortalDocumentView.java` -- record: `UUID id, String orgId, UUID customerId, UUID portalProjectId, String title, String contentType, Long size, String scope, String s3Key, Instant uploadedAt, Instant syncedAt`. `customerbackend/model/PortalCommentView.java` -- record: `UUID id, String orgId, UUID portalProjectId, String authorName, String content, Instant createdAt, Instant syncedAt`. `customerbackend/model/PortalProjectSummaryView.java` -- record: `UUID id, String orgId, UUID customerId, java.math.BigDecimal totalHours, BigDecimal billableHours, Instant lastActivityAt, Instant syncedAt`. These are plain Java records for mapping JdbcClient query results -- NOT JPA entities. |
| 55.6 | Create PortalReadModelRepository -- upsert methods | 55B | | `customerbackend/repository/PortalReadModelRepository.java`. `@Repository`, constructor injection of `@Qualifier("portalJdbcClient") JdbcClient`. Upsert methods using `INSERT ... ON CONFLICT ... DO UPDATE`: `upsertPortalProject(UUID projectId, UUID customerId, String orgId, String name, String status, String description, Instant createdAt)`, `upsertPortalDocument(UUID documentId, String orgId, UUID customerId, UUID portalProjectId, String title, String contentType, Long size, String scope, String s3Key, Instant uploadedAt)`, `upsertPortalComment(UUID commentId, String orgId, UUID portalProjectId, String authorName, String content, Instant createdAt)`, `upsertPortalProjectSummary(UUID projectId, UUID customerId, String orgId, BigDecimal totalHours, BigDecimal billableHours, Instant lastActivityAt)`. All use `ON CONFLICT ... DO UPDATE SET ... updated_at/synced_at = now()`. Pattern: follow Section 13.9.4 code example. |
| 55.7 | Create PortalReadModelRepository -- delete and count-update methods | 55B | | Add to `PortalReadModelRepository`: `deletePortalProject(UUID projectId, UUID customerId)`, `deletePortalDocument(UUID documentId)`, `deletePortalComment(UUID commentId)`, `deletePortalProjectsByOrg(String orgId)` (for resync), `incrementDocumentCount(UUID projectId, UUID customerId)`, `decrementDocumentCount(UUID projectId, UUID customerId)`, `incrementCommentCount(UUID projectId, UUID customerId)`, `decrementCommentCount(UUID projectId, UUID customerId)`, `findCustomerIdsByProjectId(UUID projectId, String orgId)` -- returns `List<UUID>` by querying `portal.portal_projects WHERE id = ? AND org_id = ?`. |
| 55.8 | Create PortalReadModelRepository -- query methods | 55B | | Add to `PortalReadModelRepository`: `findProjectsByCustomer(String orgId, UUID customerId)` -- returns `List<PortalProjectView>`, `findProjectDetail(UUID projectId, UUID customerId, String orgId)` -- returns `Optional<PortalProjectView>`, `findDocumentsByProject(UUID portalProjectId, String orgId)` -- returns `List<PortalDocumentView>`, `findDocumentsByCustomer(String orgId, UUID customerId)` -- returns `List<PortalDocumentView>`, `findCommentsByProject(UUID portalProjectId, String orgId)` -- returns `List<PortalCommentView>`, `findProjectSummary(UUID projectId, UUID customerId, String orgId)` -- returns `Optional<PortalProjectSummaryView>`. All use `JdbcClient.sql(...).params(...).query(RecordType.class).list()` or `.optional()`. |
| 55.9 | Add PortalReadModelRepository integration tests | 55B | | `customerbackend/repository/PortalReadModelRepositoryIntegrationTest.java`. ~10 tests: upsert project (insert then update, verify updated_at changes), upsert document, upsert comment, upsert project summary, find projects by customer (returns only matching org+customer), find documents by project, find comments by project, increment/decrement document count, delete project, find customer IDs by project ID. Seed: insert test data via the repository upsert methods. Pattern: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`. |

### Key Files

**Slice 55A -- Create:**
- `backend/src/main/resources/db/migration/global/V7__create_portal_schema.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/PortalDataSourceConfig.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/config/PortalDataSourceConfigIntegrationTest.java`

**Slice 55A -- Read for context:**
- `backend/src/main/resources/db/migration/global/V6__add_subscriptions.sql` -- Global migration pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/` -- Existing configuration beans
- `backend/src/main/resources/application.yml` -- DataSource properties to reuse

**Slice 55B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/model/PortalProjectView.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/model/PortalDocumentView.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/model/PortalCommentView.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/model/PortalProjectSummaryView.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/repository/PortalReadModelRepository.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/repository/PortalReadModelRepositoryIntegrationTest.java`

**Slice 55B -- Read for context:**
- `architecture/phase7-customer-portal-backend.md` Section 13.9.4 -- Repository code pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalQueryService.java` -- Existing query patterns

### Architecture Decisions

- **`customerbackend/` package**: New top-level feature package for the portal read-model layer. Separate from `portal/` (which holds authoritative auth/contact entities). The `customerbackend/` name comes from the architecture doc and represents the "customer backend slice" -- the CQRS read side.
- **JDBC, not JPA**: Read-model entities use `JdbcClient` to avoid multi-`EntityManagerFactory` complexity. The portal schema has no JPA entities, no Hibernate session management.
- **Same Postgres instance, different schema**: The portal DataSource connects to the same database but sets `search_path = portal, public`. This allows future extraction to a separate database by changing only the DataSource URL.
- **Composite PKs on portal_projects and portal_project_summaries**: The same core project can be projected for multiple customers. `(id, customer_id)` makes each projection row unique.
- **Two-slice decomposition**: 55A (migration + DataSource config + connectivity test) is infrastructure. 55B (repository methods + view records + tests) is the application layer. Each slice touches ~4-6 files created.

---

## Epic 56: Domain Events & Event Handlers

**Goal**: Define the `PortalDomainEvent` sealed hierarchy, instrument core services (`DocumentService`, `ProjectService`, `CustomerService`, `CustomerProjectService`) to publish Spring `ApplicationEvent` instances after mutations, and implement `PortalEventHandler` with `@TransactionalEventListener(phase = AFTER_COMMIT)` handlers that update the portal read-model schema. Includes a manual resync endpoint for disaster recovery.

**References**: [ADR-032](../adr/ADR-032-spring-application-events-for-portal.md), `architecture/phase7-customer-portal-backend.md` Section 13.6, 13.3.2

**Dependencies**: Epic 55 (PortalReadModelRepository must exist for handlers to write to)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **56A** | 56.1--56.4 | PortalDomainEvent sealed hierarchy (all event record types), event publication in core services | |
| **56B** | 56.5--56.9 | PortalEventHandler with @TransactionalEventListener for all events, event handler unit tests | |
| **56C** | 56.10--56.13 | Resync endpoint + service, end-to-end event projection integration tests, cross-tenant isolation tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 56.1 | Create PortalDomainEvent sealed base class | 56A | | `customerbackend/event/PortalDomainEvent.java` -- `public abstract sealed class PortalDomainEvent`. Fields: `String orgId`, `String tenantId`, `Instant occurredAt`. Constructor sets `occurredAt = Instant.now()`. Permits: `CustomerCreatedEvent`, `CustomerUpdatedEvent`, `ProjectCreatedEvent`, `ProjectUpdatedEvent`, `CustomerProjectLinkedEvent`, `CustomerProjectUnlinkedEvent`, `DocumentCreatedEvent`, `DocumentVisibilityChangedEvent`, `DocumentDeletedEvent`, `TimeEntryAggregatedEvent`. Pattern: follow Section 13.6.1 design. |
| 56.2 | Create event record types | 56A | | Create final classes in `customerbackend/event/` package, each extending `PortalDomainEvent`: `CustomerCreatedEvent(UUID customerId, String name, String email, String orgId, String tenantId)`, `CustomerUpdatedEvent(UUID customerId, String name, String email, String status, String orgId, String tenantId)`, `ProjectCreatedEvent(UUID projectId, String name, String description, String status, String orgId, String tenantId)`, `ProjectUpdatedEvent(UUID projectId, String name, String description, String status, String orgId, String tenantId)`, `CustomerProjectLinkedEvent(UUID customerId, UUID projectId, String orgId, String tenantId)`, `CustomerProjectUnlinkedEvent(UUID customerId, UUID projectId, String orgId, String tenantId)`, `DocumentCreatedEvent(UUID documentId, UUID projectId, UUID customerId, String fileName, String scope, String visibility, String s3Key, Long size, String contentType, String orgId, String tenantId)`, `DocumentVisibilityChangedEvent(UUID documentId, String visibility, String previousVisibility, String orgId, String tenantId)`, `DocumentDeletedEvent(UUID documentId, String orgId, String tenantId)`, `TimeEntryAggregatedEvent(UUID projectId, BigDecimal totalHours, BigDecimal billableHours, Instant lastActivityAt, String orgId, String tenantId)`. All fields are serializable primitives (String, UUID, Instant, BigDecimal) -- no entity references. Note: sealed classes cannot be records in Java, so these are final classes extending the sealed base with constructor + getters. |
| 56.3 | Instrument core services to publish events | 56A | | Modify 4 services to inject `ApplicationEventPublisher` and call `publishEvent()` after successful mutations: (1) `customer/CustomerService.java` -- publish `CustomerCreatedEvent` in `createCustomer()`, `CustomerUpdatedEvent` in `updateCustomer()` and `archiveCustomer()`. (2) `project/ProjectService.java` -- publish `ProjectCreatedEvent` in `createProject()`, `ProjectUpdatedEvent` in `updateProject()`. (3) `customer/CustomerProjectService.java` -- publish `CustomerProjectLinkedEvent` in `linkCustomerToProject()`, `CustomerProjectUnlinkedEvent` in `unlinkCustomerFromProject()`. (4) `document/DocumentService.java` -- publish `DocumentCreatedEvent` in `confirmUpload()` (not `initiateUpload` -- document is not finalized until confirmed), `DocumentVisibilityChangedEvent` in `toggleVisibility()`, `DocumentDeletedEvent` in `cancelUpload()`. Use `RequestScopes.requireOrgId()` and `RequestScopes.TENANT_ID.get()` for event fields. |
| 56.4 | Add event publication verification tests | 56A | | `customerbackend/event/EventPublicationTest.java`. ~6 tests: verify `ApplicationEventPublisher.publishEvent()` is called with correct event type when creating a customer, updating a project, linking customer to project, confirming a document upload, toggling document visibility, deleting a document. Use `@RecordApplicationEvents` annotation (Spring Framework 6+) in integration tests. |
| 56.5 | Create PortalEventHandler -- project and customer events | 56B | | `customerbackend/handler/PortalEventHandler.java`. `@Component` class. Constructor injection of `PortalReadModelRepository`, `CustomerProjectRepository` (for resolving customer-project links). Methods with `@TransactionalEventListener(phase = AFTER_COMMIT)`: `onCustomerProjectLinked(CustomerProjectLinkedEvent)` -- load project details from core, upsert `portal_project` for this customer. `onCustomerProjectUnlinked(CustomerProjectUnlinkedEvent)` -- delete `portal_project` for this customer, delete associated portal_documents and portal_comments. `onProjectUpdated(ProjectUpdatedEvent)` -- find all customer IDs linked to this project, upsert portal_project for each. `onCustomerUpdated(CustomerUpdatedEvent)` -- if status changed to ARCHIVED, clean up portal projections. Each handler wrapped in try-catch logging errors via SLF4J -- failed projections must not affect the caller. |
| 56.6 | Create PortalEventHandler -- document events | 56B | | Add to `PortalEventHandler`: `onDocumentCreated(DocumentCreatedEvent)` -- if visibility != SHARED, skip. Find all customer IDs linked to this project via `readModelRepo.findCustomerIdsByProjectId()`. For each customer: upsert portal_document, increment document_count. `onDocumentVisibilityChanged(DocumentVisibilityChangedEvent)` -- if changed TO SHARED: project as new document (same as created). If changed FROM SHARED: delete portal_document, decrement document_count. `onDocumentDeleted(DocumentDeletedEvent)` -- delete portal_document, decrement document_count if was SHARED. Each handler idempotent: upsert handles re-projection gracefully. |
| 56.7 | Create PortalEventHandler -- time entry event | 56B | | Add to `PortalEventHandler`: `onTimeEntryAggregated(TimeEntryAggregatedEvent)` -- find all customer IDs linked to this project, upsert portal_project_summary for each with totalHours, billableHours, lastActivityAt. This is a stub for Phase 7 -- the `TimeEntryAggregatedEvent` will be published manually during resync or by a future scheduled aggregation. |
| 56.8 | Handle cross-schema queries in event handlers | 56B | | Event handlers run AFTER_COMMIT -- the original transaction's tenant context (`RequestScopes.TENANT_ID`) may still be bound or may not be (depends on thread model). To safely query tenant-schema data (e.g., loading project details for projection), use `ScopedValue.where(RequestScopes.TENANT_ID, event.tenantId()).call(() -> { ... })` within each handler method. The portal write operations use `PortalReadModelRepository` which has its own `JdbcClient` on the portal DataSource -- no tenant scoping needed for those. Document this pattern in the handler class javadoc. |
| 56.9 | Add PortalEventHandler unit/integration tests | 56B | | `customerbackend/handler/PortalEventHandlerTest.java`. ~10 tests: CustomerProjectLinked -> portal_project created, CustomerProjectUnlinked -> portal_project deleted, ProjectUpdated -> all linked customer projections updated, DocumentCreated (SHARED) -> portal_document + count incremented, DocumentCreated (INTERNAL) -> no projection, DocumentVisibilityChanged INTERNAL->SHARED -> document projected, DocumentVisibilityChanged SHARED->INTERNAL -> document removed + count decremented, DocumentDeleted -> portal_document removed, handler exception does not propagate (verify via mock that throws), TimeEntryAggregated -> summary upserted. For unit tests: mock `PortalReadModelRepository`, verify method calls. For integration tests: verify full round-trip (publish event -> check portal schema tables). |
| 56.10 | Create PortalResyncService | 56C | | `customerbackend/service/PortalResyncService.java`. `@Service`. Constructor injection of `PortalReadModelRepository`, `CustomerProjectRepository`, `ProjectRepository`, `DocumentRepository`, `TimeEntryRepository`, `OrgSchemaMappingRepository`. Method: `resyncOrg(String orgId)` -- (1) resolve org -> schema, (2) delete all portal data for orgId, (3) within tenant scope: load all CustomerProject links, for each: upsert portal_project with project details, load SHARED documents for each project, upsert portal_documents, compute document_count/comment_count. `@Transactional` on the portal DataSource side (or use `TransactionTemplate` with a portal `PlatformTransactionManager`). |
| 56.11 | Create resync internal endpoint | 56C | | `customerbackend/controller/PortalResyncController.java`. `@RestController`, `@RequestMapping("/internal/portal")`. `POST /internal/portal/resync/{orgId}` -- calls `portalResyncService.resyncOrg(orgId)`. Secured by existing `ApiKeyAuthFilter` (matches `/internal/**`). Returns 200 with `{ "message": "Resync completed for org {orgId}", "projectsProjected": N, "documentsProjected": N }`. Pattern: follow existing `/internal/members/sync` endpoint style. |
| 56.12 | Add resync integration tests | 56C | | `customerbackend/service/PortalResyncIntegrationTest.java`. ~5 tests: resync empty org (no error, 0 projections), resync org with projects and documents (verify portal tables populated), resync is idempotent (run twice, same result), resync after deleting a portal_project (re-creates it), resync with non-existent org (404 or empty result). Seed: provision tenant, create customers, projects, documents, customer-project links. Verify portal schema rows after resync. |
| 56.13 | Add end-to-end event projection integration tests | 56C | | `customerbackend/EventProjectionIntegrationTest.java`. ~6 tests: create a customer and link to project via staff API -> verify portal_project appears in portal schema. Upload and confirm a SHARED document -> verify portal_document appears and document_count incremented. Toggle visibility SHARED -> INTERNAL -> verify portal_document removed and count decremented. Unlink customer from project -> verify portal_project removed. Update project name -> verify portal_project name updated for all linked customers. Full round-trip: staff creates data -> portal API returns projected data. These are the definitive integration tests that prove the event pipeline works end-to-end. |

### Key Files

**Slice 56A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/PortalDomainEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/CustomerCreatedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/CustomerUpdatedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/ProjectCreatedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/ProjectUpdatedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/CustomerProjectLinkedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/CustomerProjectUnlinkedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/DocumentCreatedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/DocumentVisibilityChangedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/DocumentDeletedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/TimeEntryAggregatedEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/EventPublicationTest.java`

**Slice 56A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` -- Add `ApplicationEventPublisher` + publishEvent() calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` -- Add `ApplicationEventPublisher` + publishEvent() calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerProjectService.java` -- Add `ApplicationEventPublisher` + publishEvent() calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentService.java` -- Add `ApplicationEventPublisher` + publishEvent() calls

**Slice 56A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- `requireOrgId()`, `TENANT_ID.get()`
- `architecture/phase7-customer-portal-backend.md` Section 13.6 -- Event types and publication points

**Slice 56B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandlerTest.java`

**Slice 56B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/repository/PortalReadModelRepository.java` -- Upsert/delete methods to call
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerProjectRepository.java` -- findByProjectId for customer resolution
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectRepository.java` -- findOneById for project details in handlers
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentRepository.java` -- findOneById for document details

**Slice 56C -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalResyncService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalResyncController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalResyncIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/EventProjectionIntegrationTest.java`

**Slice 56C -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMappingRepository.java` -- Org -> schema resolution for resync
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/PortalAuthIntegrationTest.java` -- Test setup pattern with tenant provisioning

### Architecture Decisions

- **Three-slice decomposition**: 56A (event hierarchy + service instrumentation) is the publish side. 56B (event handler + unit tests) is the subscribe side. 56C (resync + end-to-end tests) is the verification and recovery layer. Each slice touches 6-12 files but most in 56A are small event record classes.
- **Sealed class hierarchy**: `PortalDomainEvent` is sealed to enable exhaustive pattern matching in handlers. Note: Java sealed classes with records requires the records to extend a non-record sealed class -- the base must be an abstract class, not a record.
- **Events published in service methods, not controllers**: Ensures events fire only when the business operation succeeds within the transaction.
- **`@TransactionalEventListener(phase = AFTER_COMMIT)`**: Handlers fire after the core domain transaction commits. This means:
  - Core operation failure -> no event published
  - Handler failure -> core operation still committed (eventual consistency)
  - Slight timing gap: event fires after commit, before the response is sent
- **ScopedValue re-binding in handlers**: Since handlers may run after the original filter chain's ScopedValue scope exits, each handler must re-bind `TENANT_ID` from the event payload to query tenant-schema data.
- **Error isolation**: Every handler method wraps logic in try-catch. Failed projections are logged, not re-thrown. The `synced_at` timestamp enables staleness detection.

---

## Epic 57: Portal Comments, Summary & Profile APIs

**Goal**: Implement the new portal API endpoints that read from the portal read-model schema: project detail, comments listing, time/billing summary stub, and current contact profile. Create `PortalReadModelService` as the query facade. Update the existing `PortalProjectController` with a detail endpoint.

**References**: `architecture/phase7-customer-portal-backend.md` Section 13.4.2, 13.3.3

**Dependencies**: Epic 56 (event handlers must be populating the read model for queries to return data)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **57A** | 57.1--57.5 | PortalReadModelService, project detail endpoint, comments endpoint, profile endpoint, tests | |
| **57B** | 57.6--57.9 | Summary endpoint, cross-tenant isolation tests, updated CustomerAuthFilter shouldNotFilter for /portal/dev/**, backward compatibility tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 57.1 | Create PortalReadModelService | 57A | | `customerbackend/service/PortalReadModelService.java`. `@Service`. Constructor injection of `PortalReadModelRepository`, `PortalQueryService` (for customer validation). Methods: `getProjectDetail(UUID projectId, UUID customerId, String orgId)` -- calls `readModelRepo.findProjectDetail()`, returns `PortalProjectView` or throws `ResourceNotFoundException`. `listProjectComments(UUID projectId, UUID customerId, String orgId)` -- verifies customer linked to project via `readModelRepo.findProjectDetail()` (if no portal_project for this customer, they are not linked), returns `readModelRepo.findCommentsByProject()`. `getProjectSummary(UUID projectId, UUID customerId, String orgId)` -- verifies customer linked, returns `readModelRepo.findProjectSummary()` or empty stub. `getContactProfile(UUID customerId, String orgId)` -- within tenant scope: load `PortalContact` by customerId+orgId, return contact details + customer name. All `@Transactional(readOnly = true)`. |
| 57.2 | Add project detail endpoint to PortalProjectController | 57A | | Modify `portal/PortalProjectController.java`. Add `GET /portal/projects/{id}`. Implementation: `UUID customerId = RequestScopes.requireCustomerId(); String orgId = RequestScopes.requireOrgId(); var project = portalReadModelService.getProjectDetail(id, customerId, orgId);` Return `PortalProjectDetailResponse(UUID id, String name, String status, String description, int documentCount, int commentCount, Instant createdAt)`. Inject `PortalReadModelService` alongside existing `PortalQueryService`. |
| 57.3 | Create PortalCommentController | 57A | | `customerbackend/controller/PortalCommentController.java`. `@RestController`, `@RequestMapping("/portal/projects/{projectId}/comments")`. `GET /portal/projects/{projectId}/comments` -- reads `RequestScopes.requireCustomerId()` and `requireOrgId()`, calls `portalReadModelService.listProjectComments()`, maps to `PortalCommentResponse(UUID id, String authorName, String content, Instant createdAt)`. Pattern: follow `portal/PortalProjectController.java`. |
| 57.4 | Create PortalProfileController | 57A | | `customerbackend/controller/PortalProfileController.java`. `@RestController`, `@RequestMapping("/portal/me")`. `GET /portal/me` -- reads `RequestScopes.requireCustomerId()` and `requireOrgId()`, calls `portalReadModelService.getContactProfile()`, maps to `PortalProfileResponse(UUID contactId, UUID customerId, String customerName, String email, String displayName, String role)`. Pattern: follow `portal/PortalProjectController.java`. |
| 57.5 | Add portal comment, profile, and project detail integration tests | 57A | | `customerbackend/controller/PortalCommentControllerTest.java` (~5 tests): list comments for project with data, list comments for project with no comments (empty list), list comments for project not linked to customer (404), comments sorted by createdAt. `customerbackend/controller/PortalProfileControllerTest.java` (~3 tests): get profile returns contact details, profile includes customer name, unauthenticated request returns 401. `portal/PortalProjectControllerTest.java` extension (~3 tests): get project detail returns correct fields, get project detail for unlinked project returns 404, project detail includes document and comment counts. Total: ~11 tests. Seed: provision tenant, create customer, PortalContact, link to project, create SHARED documents, add comments via resync or manual insert into portal schema. |
| 57.6 | Create PortalSummaryController | 57B | | `customerbackend/controller/PortalSummaryController.java`. `@RestController`, `@RequestMapping("/portal/projects/{projectId}/summary")`. `GET /portal/projects/{projectId}/summary` -- reads `RequestScopes.requireCustomerId()` and `requireOrgId()`, calls `portalReadModelService.getProjectSummary()`, maps to `PortalSummaryResponse(UUID projectId, BigDecimal totalHours, BigDecimal billableHours, Instant lastActivityAt)`. Returns zeroed stub if no summary exists. Pattern: follow `PortalCommentController.java`. |
| 57.7 | Add summary controller integration tests | 57B | | `customerbackend/controller/PortalSummaryControllerTest.java`. ~4 tests: get summary returns stub data (zeroes), get summary after resync returns aggregated data, get summary for unlinked project returns 404, unauthenticated request returns 401. |
| 57.8 | Add cross-tenant portal API isolation tests | 57B | | `customerbackend/CrossTenantPortalIsolationTest.java`. ~4 tests: customer from org A cannot list org B's projects via portal API, customer from org A cannot view org B's project detail, customer from org A cannot list org B's comments, customer from org A cannot view org B's profile. Seed: provision two tenants, create customers + contacts + projects + links in each. Issue portal JWTs for each. |
| 57.9 | Update CustomerAuthFilter shouldNotFilter for dev paths | 57B | | Modify `portal/CustomerAuthFilter.java` -- update `shouldNotFilter()` to also exclude `/portal/dev/**` paths (these will be handled by the Thymeleaf harness in Epic 58 with its own auth). Add: `if (path.startsWith("/portal/dev/")) return true;`. Verify existing portal endpoints still work. Also update `SecurityConfig.java` portalFilterChain to add `.requestMatchers("/portal/dev/**").permitAll()` conditionally (or handle in Epic 58). ~2 tests: verify `/portal/dev/generate-link` is not blocked by CustomerAuthFilter, verify `/portal/projects` still requires portal JWT. |

### Key Files

**Slice 57A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalReadModelService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalCommentController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalProfileController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalCommentControllerTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalProfileControllerTest.java`

**Slice 57A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalProjectController.java` -- Add `GET /portal/projects/{id}` detail endpoint

**Slice 57A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/repository/PortalReadModelRepository.java` -- Query methods to call
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalQueryService.java` -- Existing query patterns
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- requireCustomerId(), requireOrgId()

**Slice 57B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalSummaryController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalSummaryControllerTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/CrossTenantPortalIsolationTest.java`

**Slice 57B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/CustomerAuthFilter.java` -- Exclude `/portal/dev/**` from filtering
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` -- Permit `/portal/dev/**` in portal filter chain

### Architecture Decisions

- **`PortalReadModelService` as query facade**: Centralizes access control checks (customer linked to project?) and portal schema queries. Controllers are thin delegates.
- **Read-model endpoints vs existing PortalQueryService**: The existing `PortalQueryService` (Phase 4) queries tenant-schema tables directly. New endpoints query the portal read-model schema instead. Both coexist -- existing endpoints continue to work unchanged.
- **Zero-value summary stub**: `GET /portal/projects/{id}/summary` returns zeroes if no `PortalProjectSummary` row exists. This avoids 404s for projects that have not yet had time entries aggregated.
- **Two-slice decomposition**: 57A (core API endpoints + tests) and 57B (summary + isolation + security updates). Each slice touches 5-7 files.

---

## Epic 58: Thymeleaf Dev Harness

**Goal**: Add a dev/local-only Thymeleaf test harness that serves server-rendered views for testing the full portal flow: magic link generation, dashboard with projects/documents/comments, and project detail. The harness exercises the same backend services as the real portal and is gated by `@Profile({"local", "dev"})`.

**References**: [ADR-033](../adr/ADR-033-local-only-thymeleaf-test-harness.md), `architecture/phase7-customer-portal-backend.md` Section 13.3.4, 13.4.4

**Dependencies**: Epic 54 (MagicLinkService), Epic 57 (portal APIs to exercise)

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **58A** | 58.1--58.8 | Thymeleaf dependency, DevPortalConfig, DevPortalController, templates, security config update, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 58.1 | Add spring-boot-starter-thymeleaf dependency | 58A | | Add to `pom.xml`: `<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-thymeleaf</artifactId></dependency>`. This is always available on the classpath; profile-guarding is done via `@Profile` on the config and controller beans. |
| 58.2 | Create DevPortalConfig | 58A | | `config/DevPortalConfig.java`. `@Configuration`, `@Profile({"local", "dev"})`. Configures Thymeleaf template resolution if not auto-configured. Sets template prefix to `classpath:/templates/portal/`, suffix `.html`, mode HTML. Add `@Bean` for `SpringTemplateEngine` if needed, or rely on Spring Boot auto-configuration (which should pick up templates automatically once the starter is on classpath). |
| 58.3 | Create DevPortalController -- magic link generator | 58A | | `dev/DevPortalController.java`. `@Controller`, `@Profile({"local", "dev"})`, `@RequestMapping("/portal/dev")`. `GET /portal/dev/generate-link` -- returns Thymeleaf view `generate-link` with model attributes for available orgs (query `OrgSchemaMappingRepository.findAll()`). `POST /portal/dev/generate-link` -- accepts `email` and `orgId` form params. Within tenant scope: find or create `PortalContact` (if not exists, create with GENERAL role), generate magic link via `MagicLinkService`. Add the generated magic link URL to the model and re-render the form page with the link displayed. Handle errors (org not found, customer not found) with user-friendly messages in the template. |
| 58.4 | Create DevPortalController -- dashboard | 58A | | Add to `DevPortalController`: `GET /portal/dev/dashboard?token={portalJwt}` -- accepts the portal JWT as a query param (after magic link exchange). Verify JWT via `PortalJwtService`, resolve customer and org. Within tenant scope: load projects via `PortalReadModelService` (or `PortalQueryService` for backward compatibility), load documents. Add to model: `projects`, `documents`, `customerName`, `orgId`. Return Thymeleaf view `dashboard`. `GET /portal/dev/project/{id}?token={portalJwt}` -- project detail view with documents, comments, and summary. Return view `project-detail`. |
| 58.5 | Create Thymeleaf templates | 58A | | Three templates in `src/main/resources/templates/portal/`: (1) `generate-link.html` -- HTML form with email input, org ID dropdown (populated from model), submit button. After generation: displays clickable magic link URL. Minimal CSS (inline styles or basic `<style>` block). (2) `dashboard.html` -- header with customer name, project list as HTML table (name, status, document count), document list as HTML table (title, type, size). Each project name links to project detail. (3) `project-detail.html` -- project name + description, documents table, comments table (author, content, date), summary section (total hours, billable hours). Back link to dashboard. All templates extend a `layout.html` fragment or are standalone. |
| 58.6 | Update security config for dev harness paths | 58A | | If not already done in Epic 57B (task 57.9), update `SecurityConfig.java` portalFilterChain to permit `/portal/dev/**` without authentication. The dev harness manages its own auth (JWT passed as query param, not as Bearer header). Ensure `CustomerAuthFilter.shouldNotFilter()` returns `true` for `/portal/dev/**`. |
| 58.7 | Add DevPortalController integration tests | 58A | | `dev/DevPortalControllerTest.java`. ~6 tests (run with `@ActiveProfiles("test")` -- note: "test" is not "local" or "dev", so verify the tests use the right profile or add "dev" to test profiles for this test class): generate-link page renders (GET returns 200 with HTML content-type), generate-link POST creates contact and returns page with magic link, dashboard renders with projects, project detail renders with comments and summary, non-dev profile does not expose dev endpoints (conditionally test), invalid JWT on dashboard returns error page. For profile testing: use `@TestPropertySource(properties = "spring.profiles.active=dev")` or create a separate test config. Pattern: use `MockMvc` with `.accept(MediaType.TEXT_HTML)`. |
| 58.8 | Add dev harness usage documentation | 58A | | Add a section to `backend/CLAUDE.md` documenting: how to start the harness (`./mvnw spring-boot:run` with local profile), URLs (`/portal/dev/generate-link`), the flow (enter email -> get link -> click link -> see dashboard), and limitations (dev/local only, minimal styling, not a production UI). |

### Key Files

**Slice 58A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/DevPortalConfig.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dev/DevPortalController.java`
- `backend/src/main/resources/templates/portal/generate-link.html`
- `backend/src/main/resources/templates/portal/dashboard.html`
- `backend/src/main/resources/templates/portal/project-detail.html`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/dev/DevPortalControllerTest.java`

**Slice 58A -- Modify:**
- `backend/pom.xml` -- Add `spring-boot-starter-thymeleaf` dependency
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` -- Permit `/portal/dev/**` (if not done in Epic 57)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/CustomerAuthFilter.java` -- Exclude `/portal/dev/**` (if not done in Epic 57)

**Slice 58A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkService.java` -- Token generation to invoke
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalJwtService.java` -- JWT verification for dashboard
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalReadModelService.java` -- Query facade for dashboard data
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContactService.java` -- Create contact if not exists
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMappingRepository.java` -- List orgs for dropdown

### Architecture Decisions

- **Single-slice epic**: 8 tasks but they are tightly coupled -- the config, controller, templates, and tests form a single coherent unit. Splitting would create artificial boundaries.
- **`dev/` package**: Separate from `portal/` and `customerbackend/` to make the dev-only nature explicit.
- **JWT as query param, not header**: The Thymeleaf views are server-rendered, so there is no JavaScript to set headers. The portal JWT is passed as a `?token=` query parameter. This is acceptable for a dev-only tool; a production UI would use cookies or header-based auth.
- **`@Profile({"local", "dev"})` guard**: The config and controller beans are not created in production profiles. Even if someone navigates to `/portal/dev/`, Spring returns 404 because no controller handles the path.
- **Templates alongside controller**: Thymeleaf templates in `src/main/resources/templates/portal/` coexist with the application but are only rendered when the dev controller is active.
