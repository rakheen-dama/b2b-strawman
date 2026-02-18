# Technical Breakdown: Dedicated Schema for All Tenants

**Goal**: Remove the shared-schema (`tenant_shared`) path so every tenant — regardless of plan tier — gets a dedicated `tenant_<hash>` schema. Clean slate assumed (no production data migration).

**Motivation**: The dual-path (shared vs dedicated) creates a permanent maintenance tax: every entity needs `TenantAware`, every query needs `@Filter` correctness, every test needs shared-schema variants. Multiple bugs have originated from this split (findById bypassing @Filter, OSIV pinning, RLS session var races). Dedicated-schema-only eliminates an entire category of isolation bugs.

---

## Step 0 — Pre-flight

> Before touching code: nuke the local database so Flyway starts clean.

```bash
docker compose -f compose/docker-compose.yml down -v
docker compose -f compose/docker-compose.yml up -d
```

All tenant schemas + `flyway_schema_history` tables are destroyed. The app will re-bootstrap from V1 on next startup.

---

## Step 1 — Delete Shared-Schema Infrastructure (5 files)

### 1a. Delete `TenantFilterTransactionManager`
**File**: `multitenancy/TenantFilterTransactionManager.java` — **DELETE**

This custom `JpaTransactionManager` only exists to enable `@Filter("tenantFilter")` on the Hibernate Session at transaction start for `tenant_shared`. With no shared schema, no Hibernate filter is needed.

### 1b. Delete `TenantAwareEntityListener`
**File**: `multitenancy/TenantAwareEntityListener.java` — **DELETE**

The `@PrePersist` listener that populated `tenant_id` on INSERT when operating in the shared schema. No longer needed.

### 1c. Delete `TenantAware` interface
**File**: `multitenancy/TenantAware.java` — **DELETE**

Marker interface for shared-schema row-level isolation. Every entity implementing this will be updated in Step 3.

### 1d. Delete `TenantUpgradeService`
**File**: `provisioning/TenantUpgradeService.java` — **DELETE**

The 244-line service that migrated data from `tenant_shared` to a dedicated schema on plan upgrade. No upgrade path needed when all tenants start dedicated.

### 1e. Delete `TenantInfo` record
**File**: `multitenancy/TenantInfo.java` — **DELETE**

The `TenantInfo(schemaName, tier)` record was created to carry tier awareness through `TenantFilter`'s cache. With no shared-schema branching, the cache can store plain `String` schema names.

---

## Step 2 — Simplify Multitenancy Infrastructure (4 files)

### 2a. `HibernateMultiTenancyConfig` — replace transaction manager
**File**: `multitenancy/HibernateMultiTenancyConfig.java`

Replace:
```java
@Bean @Primary
PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
    return new TenantFilterTransactionManager(emf);
}
```
With:
```java
@Bean @Primary
PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
    return new JpaTransactionManager(emf);
}
```

### 2b. `SchemaMultiTenantConnectionProvider` — remove RLS session variable logic
**File**: `multitenancy/SchemaMultiTenantConnectionProvider.java`

Remove these methods entirely (they only served shared-schema RLS):
- `setCurrentTenant()` (lines 109–121) — set `app.current_tenant` via `set_config()`
- `resetCurrentTenant()` (lines 123–129) — `RESET app.current_tenant`
- `validateOrgId()` (lines 140–144)

Remove the calls to them from `getConnection()` and `releaseConnection()`.

Remove the `SHARED_SCHEMA` constant and `ORG_ID_PATTERN`.

The `sanitizeSchema()` method drops its `SHARED_SCHEMA` branch:
```java
private String sanitizeSchema(String schema) {
    if ("public".equals(schema) || SCHEMA_PATTERN.matcher(schema).matches()) {
        return schema;
    }
    throw new IllegalArgumentException("Invalid schema name: " + schema);
}
```

### 2c. `TenantFilter` — simplify cache to `String`
**File**: `multitenancy/TenantFilter.java`

Change the Caffeine cache from `Cache<String, TenantInfo>` to `Cache<String, String>` (schema name only). Update `resolveTenant()` / `lookupTenant()` accordingly.

The `OrgSchemaMappingRepository.findTenantInfoByClerkOrgId()` query can be simplified to return just the schema name (`Optional<String>`), or the repository method can be replaced with the simpler `findByClerkOrgId()` already available.

### 2d. `TenantMigrationRunner` — remove shared-schema bootstrap
**File**: `provisioning/TenantMigrationRunner.java`

Remove:
- `SHARED_SCHEMA` constant
- `bootstrapSharedSchema()` method (creates + migrates `tenant_shared`)
- The `if (SHARED_SCHEMA.equals(...)) continue` skip in the migration loop

The `run()` method becomes simply: iterate all `org_schema_mapping` rows and run Flyway on each.

---

## Step 3 — Strip `TenantAware` from All Entities (33 entities)

For each entity listed below, remove:
1. `implements TenantAware`
2. `@EntityListeners(TenantAwareEntityListener.class)` annotation
3. `@FilterDef(name = "tenantFilter", ...)` annotation
4. `@Filter(name = "tenantFilter", ...)` annotation
5. The `tenant_id` field (`@Column(name = "tenant_id") private String tenantId`)
6. The `getTenantId()` / `setTenantId()` methods
7. Remove the Hibernate `@Filter` / `@FilterDef` imports

### Entity list (32 files):

| Package | Entity | File |
|---------|--------|------|
| `project` | `Project` | `project/Project.java` |
| `document` | `Document` | `document/Document.java` |
| `member` | `Member` | `member/Member.java` |
| `member` | `ProjectMember` | `member/ProjectMember.java` |
| `customer` | `Customer` | `customer/Customer.java` |
| `customer` | `CustomerProject` | `customer/CustomerProject.java` |
| `task` | `Task` | `task/Task.java` |
| `timeentry` | `TimeEntry` | `timeentry/TimeEntry.java` |
| `invoice` | `Invoice` | `invoice/Invoice.java` |
| `invoice` | `InvoiceLine` | `invoice/InvoiceLine.java` |
| `comment` | `Comment` | `comment/Comment.java` |
| `notification` | `Notification` | `notification/Notification.java` |
| `notification` | `NotificationPreference` | `notification/NotificationPreference.java` |
| `audit` | `AuditEvent` | `audit/AuditEvent.java` |
| `portal` | `PortalContact` | `portal/PortalContact.java` |
| `billingrate` | `BillingRate` | `billingrate/BillingRate.java` |
| `costrate` | `CostRate` | `costrate/CostRate.java` |
| `budget` | `ProjectBudget` | `budget/ProjectBudget.java` |
| `settings` | `OrgSettings` | `settings/OrgSettings.java` |
| `tag` | `Tag` | `tag/Tag.java` |
| `tag` | `EntityTag` | `tag/EntityTag.java` |
| `view` | `SavedView` | `view/SavedView.java` |
| `template` | `DocumentTemplate` | `template/DocumentTemplate.java` |
| `template` | `GeneratedDocument` | `template/GeneratedDocument.java` |
| `fielddefinition` | `FieldDefinition` | `fielddefinition/FieldDefinition.java` |
| `fielddefinition` | `FieldGroup` | `fielddefinition/FieldGroup.java` |
| `fielddefinition` | `FieldGroupMember` | `fielddefinition/FieldGroupMember.java` |
| `retention` | `RetentionPolicy` | `retention/RetentionPolicy.java` |
| `datarequest` | `DataSubjectRequest` | `datarequest/DataSubjectRequest.java` |
| `checklist` | `ChecklistTemplate` | `checklist/ChecklistTemplate.java` |
| `checklist` | `ChecklistTemplateItem` | `checklist/ChecklistTemplateItem.java` |
| `checklist` | `ChecklistInstance` | `checklist/ChecklistInstance.java` |
| `checklist` | `ChecklistInstanceItem` | `checklist/ChecklistInstanceItem.java` |

---

## Step 4 — Replace `findOneById` with `findById` in Repositories (28 repos)

Every repository currently has a JPQL `findOneById()` workaround because `JpaRepository.findById()` uses `EntityManager.find()` which bypasses Hibernate `@Filter`. With no `@Filter`, standard `findById()` works correctly.

**For each repository**: delete the `findOneById` JPQL method:
```java
// DELETE this
@Query("SELECT e FROM Entity e WHERE e.id = :id")
Optional<Entity> findOneById(@Param("id") UUID id);
```

**For each service/controller calling `findOneById()`**: replace with `findById()`.

### Repository list (28 files):

`RetentionPolicyRepository`, `ChecklistTemplateRepository`, `ChecklistInstanceItemRepository`, `ChecklistInstanceRepository`, `ChecklistTemplateItemRepository`, `CustomerRepository`, `OrgSettingsRepository`, `DataSubjectRequestRepository`, `GeneratedDocumentRepository`, `DocumentTemplateRepository`, `SavedViewRepository`, `TagRepository`, `FieldGroupRepository`, `FieldDefinitionRepository`, `TimeEntryRepository`, `InvoiceRepository`, `ProjectRepository`, `InvoiceLineRepository`, `TaskRepository`, `AuditEventRepository`, `MemberRepository`, `ProjectBudgetRepository`, `CostRateRepository`, `BillingRateRepository`, `PortalContactRepository`, `NotificationRepository`, `CommentRepository`, `DocumentRepository`

### Service call sites to update:

Search for all `findOneById` call sites (there are ~60+ across services) and replace with `findById`. The return type is the same (`Optional<Entity>`), so this is a mechanical find-and-replace.

---

## Step 5 — Simplify Provisioning (2 files)

### 5a. `TenantProvisioningService` — single path for all tiers
**File**: `provisioning/TenantProvisioningService.java`

Remove:
- `SHARED_SCHEMA` constant
- `provisionStarter()` method
- The `if (org.getTier() == Tier.STARTER)` branch in `provisionTenant()`

The `provisionTenant()` method becomes a single path that always does: `createSchema()` → `runTenantMigrations()` → seeders → `createMapping()` → subscription.

### 5b. `Tier` enum — keep but decouple from tenancy
**File**: `provisioning/Tier.java` — **KEEP**

`Tier.STARTER` / `Tier.PRO` stays for billing/feature gating. It just no longer determines schema topology.

### 5c. `PlanSyncService` / `PlanLimits` — no changes needed
These services gate features (member limits, etc.) based on tier. They don't reference shared schema.

---

## Step 6 — Rewrite Flyway Migrations (clean slate)

Since we're starting from scratch, rewrite the tenant migrations to remove all `tenant_id` columns, indexes, and RLS policies.

### 6a. Delete V7 and V8 entirely
- **DELETE** `V7__add_tenant_id_for_shared.sql` — added `tenant_id` + RLS to V1-V5 tables
- **DELETE** `V8__shared_schema_member_unique.sql` — widened unique constraint for shared schema

### 6b. Restore `members` unique constraint
In `V3__create_members.sql`, ensure `UNIQUE(clerk_user_id)` is the constraint (not the V8 widened version).

### 6c. Remove `tenant_id` from later migrations (V9+)

Every migration from V9 onward that creates a table includes `tenant_id VARCHAR(255)` and an RLS policy block. Remove:
1. The `tenant_id` column from each `CREATE TABLE`
2. The `CREATE INDEX ... ON table (tenant_id)` lines
3. The `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` lines
4. The `DO $$ ... CREATE POLICY tenant_isolation_* ... $$ END` blocks
5. Any `UNIQUE` constraints that include `tenant_id` (replace with the column set minus `tenant_id`)

### Affected migrations:

| Migration | Tables | Shared-schema artifacts to remove |
|-----------|--------|----------------------------------|
| V9 | `customers` | tenant_id col, index, RLS policy |
| V10 | `customer_projects` | tenant_id col, index, RLS policy |
| V11 | `tasks` | tenant_id col, index, RLS policy |
| V12 | `documents` (alter) | tenant_id additions if any |
| V13 | `time_entries` | tenant_id col, index, RLS policy |
| V14 | `audit_events` | tenant_id col, index, RLS policy |
| V15 | `comments` | tenant_id col, index, RLS policy |
| V16 | `notifications` | tenant_id col, index, RLS policy |
| V17 | `notification_preferences` | tenant_id col, index, RLS policy |
| V18 | `portal_contacts`, `magic_link_tokens` | tenant_id col, index, RLS policy |
| V19 | `billing_rates`, `cost_rates`, `project_budgets` | tenant_id col, index, RLS policy |
| V20 | `time_entries` (alter) | check for tenant_id refs |
| V21 | `project_budgets` (alter) | check for tenant_id refs |
| V23 | `invoices`, `invoice_lines`, `invoice_counters` | tenant_id col, index, RLS policy |
| V24 | `field_definitions`, `field_groups`, `field_group_members` | tenant_id col, unique constraints, RLS |
| V25 | `tags`, `entity_tags` | tenant_id col, index, RLS policy |
| V28 | `saved_views` | tenant_id col, index, RLS policy |
| V29 | `document_templates`, `generated_documents` | tenant_id col, index, RLS policy |
| V31 | compliance tables | tenant_id col, index, RLS policy |

### Re-number migrations

After deleting V7 and V8, re-number V9→V7, V10→V8, etc. to keep the sequence contiguous. This is safe because we're starting from a clean slate.

> **Alternative**: Keep V7/V8 as no-ops (`-- intentionally empty`) to avoid re-numbering. Simpler but leaves dead migration slots.

---

## Step 7 — Simplify Seeder Binding

### `FieldPackSeeder`, `TemplatePackSeeder`, `CompliancePackSeeder`

These seeders currently accept `(String schema, String orgId)` and bind both `RequestScopes.TENANT_ID` (schema) and `RequestScopes.ORG_ID` (orgId). The ORG_ID binding was needed for `TenantAwareEntityListener` to populate `tenant_id`.

After removing `TenantAwareEntityListener`, the ORG_ID binding in seeders is only needed if the seeder code itself reads ORG_ID (e.g., for setting `tenant_id` manually via `setTenantId(orgId)`). Check each seeder:
- If it calls `setTenantId()` on entities → remove those calls
- ORG_ID binding can stay (it's still useful for audit events, logging, etc.)

### `TenantProvisioningService.seedDefaultRetentionPolicies()`

Remove the `customerPolicy.setTenantId(orgId)` / `auditPolicy.setTenantId(orgId)` calls — the field no longer exists on entities.

---

## Step 8 — Update Services Using ORG_ID for shared-schema logic

### `ViewFilterService` — remove shared-schema filter branching
**File**: `view/ViewFilterService.java`

Currently checks `RequestScopes.ORG_ID.isBound()` and adds a `tenantOrgId` parameter for shared-schema filtering in native queries. Remove this branching — in dedicated schemas, `search_path` provides isolation, so no `WHERE tenant_id = :tenantOrgId` clause is needed.

### `NotificationEventHandler` / `PortalEventHandler` — keep ORG_ID binding
These bind ORG_ID when re-establishing scoped context for async processing. ORG_ID is still needed for audit events and domain events that carry the org identifier. **No change needed** beyond removing any `TenantAwareEntityListener`-specific comments.

### Services using `ORG_ID` for domain events
`ProjectService`, `TimeEntryService`, `InvoiceService`, `TaskService`, `DocumentService`, `CommentService`, `CustomerService`, `CustomerProjectService`, `ProjectMemberService` — these read `RequestScopes.ORG_ID` to pass to domain events (for notification fanout, audit, etc.). **No change needed** — ORG_ID stays as a business identity, not a tenancy discriminator.

---

## Step 9 — Update / Delete Tests (14+ test files)

### Delete entirely:
- `multitenancy/StarterTenantIntegrationTest.java` — tests shared-schema isolation
- `multitenancy/MixedTenantIntegrationTest.java` — tests cross-schema (shared + dedicated) isolation
- `timeentry/TimeEntrySharedSchemaIntegrationTest.java` — shared-schema time entry isolation
- `task/TaskStarterTenantIntegrationTest.java` — shared-schema task isolation
- `compliance/ComplianceSharedSchemaTest.java` — shared-schema compliance isolation
- `billing/BillingUpgradeIntegrationTest.java` — Starter→Pro schema migration
- `provisioning/TierUpgradeIntegrationTest.java` — tier upgrade flow

### Update:
- `provisioning/ProvisioningIntegrationTest.java` — remove Starter-path test cases; all provisioning tests should use the single (dedicated) path
- `provisioning/TenantProvisioningServiceTest.java` — remove Starter-specific mocking
- `audit/AuditTenantIsolationTest.java` — may reference shared schema in setup
- `audit/InternalAuditControllerTest.java` — may reference shared schema
- `member/PlanEnforcementIntegrationTest.java` — remove Starter-schema-specific setup
- `compliance/V31MigrationTest.java` — remove tenant_id assertions
- Any test that calls `provisionTenant()` with Starter tier expectations

### All remaining tests:
Search for `"tenant_shared"` in test files and update. Many tests that provision tenants as Starter will now get dedicated schemas — their assertions about schema names need updating.

---

## Step 10 — Update `RequestScopes.ORG_ID`

`RequestScopes.ORG_ID` **stays**. It's used by:
- Domain events (audit, notification, portal event handlers)
- Seeders (binding context for provisioning)
- Internal endpoints (`InternalAuditController`, `MemberSyncService`, `DevPortalController`)
- Services that carry org identity for business logic

Only 3 consumers used ORG_ID specifically for shared-schema isolation:
1. `TenantAwareEntityListener` → deleted (Step 1b)
2. `SchemaMultiTenantConnectionProvider.setCurrentTenant()` → removed (Step 2b)
3. `TenantFilterTransactionManager.doBegin()` → deleted (Step 1a)

---

## Step 11 — Update ADRs and Documentation

### Supersede ADR-012 (shared-schema decision)
Write a new ADR documenting the reversal:
- **Context**: Dual-path (shared + dedicated) created maintenance tax across 32 entities, 28 repositories, 4 infrastructure classes, and multiple bug categories
- **Decision**: All tenants get dedicated schemas; `Tier` is decoupled from tenancy topology
- **Consequences**: `tenant_id` column removed from all tenant tables; RLS policies removed; `TenantFilterTransactionManager` deleted

### Update `backend/CLAUDE.md`
- Remove references to shared-schema, `TenantAware`, `TenantFilterTransactionManager`, `findOneById` workaround
- Remove V7/V8 migration descriptions
- Simplify multitenancy section

### Update `architecture/ARCHITECTURE.md`
- Update Section 4 (multitenancy) to reflect single-path

---

## Execution Order

The steps above are designed to be executed in dependency order:

```
Step 0  (clean DB)
  │
Step 1  (delete shared-schema infrastructure)
  │
Step 2  (simplify remaining infra)
  ├──── Step 3  (strip TenantAware from entities)    ← parallelizable
  ├──── Step 4  (findOneById → findById)              ← parallelizable
  └──── Step 6  (rewrite migrations)                  ← parallelizable
  │
Step 5  (simplify provisioning) — depends on Step 1
  │
Step 7  (simplify seeders) — depends on Step 3
  │
Step 8  (service ORG_ID cleanup) — depends on Steps 2, 3
  │
Step 9  (tests) — depends on all above
  │
Step 10 (verify ORG_ID usage) — verification pass
  │
Step 11 (docs/ADRs) — last
```

---

## Verification

After all changes:

```bash
# 1. Clean database
docker compose -f compose/docker-compose.yml down -v && docker compose -f compose/docker-compose.yml up -d

# 2. Build + test (Testcontainers will start clean Postgres)
cd backend && ./mvnw clean verify

# 3. Verify no "tenant_shared" or "SHARED_SCHEMA" references remain
grep -r "tenant_shared\|SHARED_SCHEMA\|TenantAware\|TenantFilterTransactionManager\|findOneById" \
  backend/src/main/java/ --include="*.java" | grep -v "test"

# 4. Start app and provision a Starter tenant — should get dedicated schema
./mvnw spring-boot:run
# (trigger provisioning via webhook or internal API)
# Verify: SELECT * FROM public.org_schema_mapping; — should show tenant_<hash>, NOT tenant_shared
```

---

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Missing a `setTenantId()` call in a seeder/service | Medium | `grep -r "setTenantId" backend/src/main/java/` — all hits must be removed |
| Native SQL still references `tenant_id` | Medium | `grep -r "tenant_id" backend/src/main/java/ --include="*.java"` — verify each hit |
| Test still expects `tenant_shared` schema name | High | `grep -r "tenant_shared" backend/src/test/` — update or delete every match |
| Migration re-numbering breaks Flyway | Low | Clean slate — no existing `flyway_schema_history` to conflict |
| ORG_ID removal breaks domain events | None | ORG_ID stays (Step 10) |

---

## Summary

| Metric | Before | After |
|--------|--------|-------|
| Entity interface implementations | 32 `TenantAware` | 0 |
| Hibernate `@Filter` annotations | 32 entities | 0 |
| `@FilterDef` annotations | 32 entities | 0 |
| Custom `findOneById` methods | 28 repositories | 0 |
| Shared-schema infrastructure classes | 4 (TenantFilterTxMgr, TenantAwareEntityListener, TenantAware, TenantUpgradeService) | 0 |
| RLS policies in migrations | ~20 tables | 0 |
| `tenant_id` columns | ~35 tables | 0 |
| Isolation model | Dual (schema + row-level) | Single (schema) |
| Shared-schema-specific tests | 7 test files | 0 |
