# Phase 13 — Dedicated Schema for All Tenants

This phase eliminates the shared-schema (`tenant_shared`) tenant isolation path entirely. After this refactoring, every tenant — regardless of billing tier — receives a dedicated `tenant_<hash>` schema. The `Tier` enum remains for billing and feature gating, but no longer determines schema topology.

This is a **pure refactoring phase** — no new features are added. The result is a simpler codebase with one isolation model, fewer annotations per entity, no custom transaction manager, no RLS policies, and no `findOneById` workarounds. The dual-path architecture was a permanent maintenance tax: every new entity required 7 extra touchpoints (`TenantAware`, `@FilterDef`, `@Filter`, `@EntityListeners`, `tenant_id` field, getter/setter, `findOneById` in repo). Multiple production-grade bugs originated exclusively from the shared-schema path.

**Architecture doc**: `architecture/phase13-dedicated-schema-only.md`

**ADRs**: [ADR-064](../adr/ADR-064-dedicated-schema-only.md) (Dedicated Schema for All Tenants — supersedes ADR-012)

**Impact summary**: 5 files deleted, 4 infrastructure files simplified, 27 entities stripped of shared-schema annotations, 22 repositories lose `findOneById` (+ 3 lose `findAllByIds`), ~168 main-source call sites updated, ~80 test-source call sites updated, 2 migrations deleted + 15 stripped of tenant_id/RLS + renumbered, 7 test files deleted, ~14 test files updated.

**Pre-requisite**: `docker compose down -v && docker compose up -d` before final verification (clean-slate Flyway).

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 96 | Infrastructure Removal & Provisioning Simplification | Backend | -- | M | 96A | |
| 97 | Entity, Repository & Call-Site Cleanup | Backend | 96 | L | 97A, 97B, 97C | |
| 98 | Migration Rewrite & Renumber | Backend | -- | M | 98A | |
| 99 | Test Cleanup, Verification & Documentation | Backend | 96, 97, 98 | M | 99A, 99B | |

## Dependency Graph

```
[E96A Infrastructure + Provisioning]
         │
         ▼
[E97A Entity/Repo Batch 1] ──► [E97B Entity/Repo Batch 2] ──► [E97C Call-Site Migration + Services]
                                                                         │
[E98A Migration Rewrite] ─────────────────────────────────────────────── │
                                                                         │
                                                            ┌────────────┘
                                                            ▼
                                                   [E99A Test Cleanup]──►[E99B Docs & Verification]
```

**Parallel opportunities**:
- Epic 98 (migrations) can run in parallel with Epics 97A/97B/97C (entity/repo/call-site cleanup) — completely independent file sets
- Epic 99 depends on ALL of 96, 97, 98 completing (tests must compile against final code + final migrations)

## Implementation Order

### Stage 1: Foundation (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1 | Epic 96 | 96A | Delete 5 shared-schema infrastructure files, simplify 4 infrastructure files, simplify provisioning to single path, stub `TenantAware` as empty interface for compile compatibility. Must run first — all other slices depend on simplified infrastructure. |

### Stage 2: Core Cleanup (Parallel tracks)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 97 | 97A | Strip TenantAware from entities batch 1 (project, document, member, customer, task, timeEntry, invoice + related) and their repositories (remove `findOneById` declarations). 10 entities + 8 repos. Sequential dependency on 96A (TenantAware stubbed as empty). |
| 2b | Epic 97 | 97B | Strip TenantAware from entities batch 2 (remaining 18 entities) and their repositories (remove `findOneById`/`findAllByIds` declarations). Delete TenantAware.java stub. Sequential dependency on 97A (to avoid merge conflicts in shared imports). |
| 2c | Epic 97 | 97C | Migrate all `findOneById` -> `findById` and `findAllByIds` -> `findAllById` call sites across services, controllers, and event handlers (~168 main-source calls in ~58 files). Also update service-level native SQL (ViewFilterService, TagFilterHandler, InvoiceNumberService + InvoiceCounter). Sequential dependency on 97B (repos must have declarations removed first). |
| 2-parallel | Epic 98 | 98A | Delete V7/V8, strip tenant_id/RLS from 15 migrations, renumber V9-V30 to V7-V28, fix 7 unique constraints. Independent of Java changes — runs in parallel with 97A/97B/97C. |

### Stage 3: Validation (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 99 | 99A | Delete 7 shared-schema test files, update ~16 test files (findOneById -> findById, remove tenant_shared refs, update schema expectations). Must follow all code changes. |
| 3b | Epic 99 | 99B | Update documentation (ADR-064 already written, update ARCHITECTURE.md Section 8, update backend/CLAUDE.md, update MEMORY.md). Run full verification suite. `./mvnw clean verify` must pass with 0 failures. |

### Timeline

```
Stage 1:  [96A Infrastructure] ──────────────────────────────────────────────────────►
Stage 2:  .........[97A Entities Batch 1]──►[97B Entities Batch 2]──►[97C Call Sites + Services]
          .........[98A Migrations] ──────────────────────────────────────────────────►
Stage 3:  ............................................[99A Tests]──►[99B Docs & Verify]
```

**Critical path**: 96A -> 97A -> 97B -> 97C -> 99A -> 99B
**Parallel track**: 98A runs independently alongside 97A/97B/97C
**All slices in single PR**: Since this is a refactoring, all slices should be merged atomically in a single PR. The code will not compile at intermediate stages (e.g., after 97A but before 97C).

---

## Epic 96: Infrastructure Removal & Provisioning Simplification

**Goal**: Delete the 5 shared-schema infrastructure classes, simplify the 4 remaining infrastructure files to remove all shared-schema branching, simplify provisioning to a single dedicated-schema path, and stub `TenantAware` as an empty interface so entities continue to compile until Epic 97 strips them. After this epic, the infrastructure layer knows nothing about `tenant_shared`, `TenantInfo`, `TenantFilterTransactionManager`, or tier-based schema routing.

**References**: Architecture doc Sections 3.1 (files to delete), 3.2 (files to simplify), 3.8 (provisioning changes), 3.7.4 (OrgSchemaMappingRepository). [ADR-064](../adr/ADR-064-dedicated-schema-only.md).

**Dependencies**: None (first epic)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **96A** | 96.1-96.12 | Delete TenantAwareEntityListener, TenantFilterTransactionManager, TenantUpgradeService, TenantInfo. Stub TenantAware as empty interface. Simplify HibernateMultiTenancyConfig (stock JpaTransactionManager), SchemaMultiTenantConnectionProvider (remove RLS methods/SHARED_SCHEMA), TenantFilter (cache String not TenantInfo), TenantMigrationRunner (remove shared-schema bootstrap). Simplify TenantProvisioningService (single path). Remove findTenantInfoByClerkOrgId from OrgSchemaMappingRepository. Verify build compiles (tests may fail — expected). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 96.1 | Delete `TenantAwareEntityListener.java` | 96A | | DELETE `multitenancy/TenantAwareEntityListener.java` (~20 lines). No references remain after TenantAware stub removes `@EntityListeners` requirement from entities (entities still compile because `implements TenantAware` stays until Epic 97, but the listener class is unused). |
| 96.2 | Delete `TenantFilterTransactionManager.java` | 96A | | DELETE `multitenancy/TenantFilterTransactionManager.java` (~35 lines). Custom JpaTransactionManager enabling @Filter. Replaced by stock JpaTransactionManager in task 96.5. |
| 96.3 | Delete `TenantUpgradeService.java` | 96A | | DELETE `provisioning/TenantUpgradeService.java` (~244 lines). Starter-to-Pro data migration. No upgrade path needed when all tenants start dedicated. |
| 96.4 | Delete `TenantInfo.java` | 96A | | DELETE `multitenancy/TenantInfo.java` (~10 lines). Record `(schemaName, tier)` only existed for TenantFilter cache. Replaced by plain String cache. |
| 96.5 | Simplify `HibernateMultiTenancyConfig.java` | 96A | | MODIFY `multitenancy/HibernateMultiTenancyConfig.java`. Replace `new TenantFilterTransactionManager(emf)` with `new JpaTransactionManager(emf)`. Remove TenantFilterTransactionManager import. Per architecture Section 3.2.1. |
| 96.6 | Simplify `SchemaMultiTenantConnectionProvider.java` | 96A | | MODIFY `multitenancy/SchemaMultiTenantConnectionProvider.java`. Remove: `SHARED_SCHEMA` constant, `ORG_ID_PATTERN` constant, `setCurrentTenant()` method, `resetCurrentTenant()` method, `validateOrgId()` method. Remove calls to `setCurrentTenant` from `getConnection()` and `resetCurrentTenant` from `releaseConnection()`. Simplify `sanitizeSchema()` (drop `SHARED_SCHEMA` branch). Simplify `getConnection()` / `releaseConnection()` per architecture Section 3.2.2. |
| 96.7 | Simplify `TenantFilter.java` | 96A | | MODIFY `multitenancy/TenantFilter.java`. Change cache type from `Cache<String, TenantInfo>` to `Cache<String, String>`. Update `resolveTenant()` and `lookupTenant()` to work with plain String (use `findByClerkOrgId()` + `OrgSchemaMapping::getSchemaName`). Update `doFilterInternal()` to use `String schema` instead of `TenantInfo info`. Remove TenantInfo import. Remove `findTenantInfoByClerkOrgId` dependency. Per architecture Section 3.2.3. |
| 96.8 | Simplify `TenantMigrationRunner.java` | 96A | | MODIFY `provisioning/TenantMigrationRunner.java`. Remove `SHARED_SCHEMA` constant, `bootstrapSharedSchema()` method, the `if (SHARED_SCHEMA.equals(mapping.getSchemaName())) continue` skip. Simplify `run()` to iterate all mappings without skipping. Per architecture Section 3.2.4. |
| 96.9 | Simplify `TenantProvisioningService.java` | 96A | | MODIFY `provisioning/TenantProvisioningService.java`. Remove `SHARED_SCHEMA` constant, `provisionStarter()` method, the tier-branching `if (org.getTier() == Tier.STARTER)`. Inline `provisionPro()` into single `provisionTenant()` path. All tenants get `tenant_<hash>`. Per architecture Section 3.8. |
| 96.10 | Remove `findTenantInfoByClerkOrgId` from `OrgSchemaMappingRepository` | 96A | | MODIFY `multitenancy/OrgSchemaMappingRepository.java`. Delete the `@Query` method that returns `Optional<TenantInfo>`. TenantFilter now uses the existing `findByClerkOrgId()` returning `Optional<OrgSchemaMapping>`. Per architecture Section 3.7.4. |
| 96.11 | Stub `TenantAware` as empty interface | 96A | | MODIFY `multitenancy/TenantAware.java`. Replace current content (interface with `getTenantId`/`setTenantId` methods) with an empty marker interface: `public interface TenantAware {}`. This allows all 27 entities that `implement TenantAware` to continue compiling until Epic 97 strips them. The `@EntityListeners` annotation referencing the deleted listener will cause warnings but not compilation failures (the annotation target class is missing, so it is a no-op). |
| 96.12 | Verify compilation | 96A | | Run `./mvnw compile -q`. Expect: compilation succeeds. Tests may fail (TenantInfo references in test files, `findTenantInfoByClerkOrgId` in tests, `provisionStarter` in tests). That is expected — test cleanup is in Epic 99. |

### Key Files

**Slice 96A — Delete:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAwareEntityListener.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilterTransactionManager.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantUpgradeService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantInfo.java`

**Slice 96A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAware.java` — Stub as empty interface
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/HibernateMultiTenancyConfig.java` — Stock JpaTransactionManager
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/SchemaMultiTenantConnectionProvider.java` — Remove RLS methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java` — Cache String not TenantInfo
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantMigrationRunner.java` — Remove shared-schema bootstrap
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` — Single provisioning path
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMappingRepository.java` — Remove findTenantInfoByClerkOrgId

**Slice 96A — Read for context:**
- `architecture/phase13-dedicated-schema-only.md` Sections 3.1, 3.2, 3.7.4, 3.8
- `adr/ADR-064-dedicated-schema-only.md`

### Architecture Decisions

- **TenantAware stub strategy**: Rather than deleting `TenantAware.java` and immediately fixing all 27 entity compile errors, stub it as an empty interface. This allows infrastructure cleanup (Epic 96) to be committed and verified independently. Epic 97 then strips `implements TenantAware` from entities and deletes the stub. This avoids a 30+ file atomic commit.
- **Stock JpaTransactionManager**: `TenantFilterTransactionManager` existed solely to enable Hibernate `@Filter("tenantFilter")` for shared-schema sessions. With no `@Filter` annotations remaining (after Epic 97), stock `JpaTransactionManager` suffices.
- **Provisioning simplification**: `provisionStarter()` is deleted entirely. `provisionPro()` logic is inlined into `provisionTenant()`. All tiers get dedicated schema.

---

## Epic 97: Entity, Repository & Call-Site Cleanup

**Goal**: Strip all shared-schema annotations and fields from 27 entities, remove `findOneById`/`findAllByIds` declarations from 22 repositories, migrate ~168 main-source `findOneById` call sites to `findById`, migrate ~11 `findAllByIds` call sites to `findAllById`, update native SQL in ViewFilterService/TagFilterHandler/InvoiceNumberService to remove `tenant_id` references, and delete the TenantAware stub. After this epic, no entity carries `tenant_id`, no repository has custom `findOneById`, and no service references shared-schema artifacts.

**References**: Architecture doc Sections 3.3 (entity changes), 3.4 (repository changes), 3.6 (seeder changes), 3.7 (service changes).

**Dependencies**: Epic 96 (infrastructure must be simplified first; TenantAware must be stubbed as empty)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **97A** | 97.1-97.4 | Strip TenantAware from entities 1-10 (project, document, member, projectMember, customer, customerProject, task, timeEntry, invoice, invoiceLine). Remove `findOneById` from matching 8 repos. Remove `findAllByIds` from ProjectRepository and InvoiceRepository. ~10 entity files + ~8 repo files. Changes are mechanical: remove 7 items per entity (implements, 3 annotations, field, getter, setter) + delete 3-5 lines per repo. | |
| **97B** | 97.5-97.8 | Strip TenantAware from entities 11-27 (comment, notification, notificationPreference, auditEvent, portalContact, billingRate, costRate, projectBudget, orgSettings, tag, entityTag, savedView, documentTemplate, generatedDocument, fieldDefinition, fieldGroup, fieldGroupMember) + InvoiceCounter. Remove `findOneById` from matching 14 repos. Remove `findAllByIds` from TagRepository. Delete `TenantAware.java` stub. ~18 entity files + ~14 repo files + 1 delete. | |
| **97C** | 97.9-97.16 | Migrate ALL `findOneById(id)` -> `findById(id)` call sites across services and controllers (~168 occurrences in ~58 files). Migrate `findAllByIds(ids)` -> `findAllById(ids)` call sites (~11 occurrences in ~8 files). Update ViewFilterService (remove tenant_id filter). Update TagFilterHandler (remove tenant_id JOIN). Refactor InvoiceNumberService (remove tenant discriminator). Verify seeders have no `setTenantId()` calls. | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 97.1 | Strip TenantAware from core entities (Project, Document, Member, ProjectMember) | 97A | | MODIFY 4 entity files in `project/`, `document/`, `member/` packages. For each: remove `implements TenantAware`, remove `@FilterDef`, `@Filter`, `@EntityListeners(TenantAwareEntityListener.class)` annotations, remove `@Column(name = "tenant_id") private String tenantId` field, remove `getTenantId()`/`setTenantId()` methods, remove unused imports (Filter, FilterDef, ParamDef, TenantAware, TenantAwareEntityListener, EntityListeners). Pattern: see architecture Section 3.3 before/after for Project.java. |
| 97.2 | Strip TenantAware from customer/task entities (Customer, CustomerProject, Task) | 97A | | MODIFY 3 entity files in `customer/`, `task/` packages. Same mechanical removal as 97.1. |
| 97.3 | Strip TenantAware from time/invoice entities (TimeEntry, Invoice, InvoiceLine) | 97A | | MODIFY 3 entity files in `timeentry/`, `invoice/` packages. Same mechanical removal. |
| 97.4 | Remove `findOneById`/`findAllByIds` from repos batch 1 | 97A | | MODIFY 8 repo files: `ProjectRepository` (remove findOneById + findAllByIds), `DocumentRepository`, `MemberRepository`, `CustomerRepository`, `TaskRepository`, `TimeEntryRepository`, `InvoiceRepository` (remove findOneById + findAllByIds), `InvoiceLineRepository`. Delete the `@Query("SELECT ... WHERE ... id = :id")` + `findOneById` method and any `findAllByIds` method. Also remove `@Param` import if no longer used. |
| 97.5 | Strip TenantAware from communication entities (Comment, Notification, NotificationPreference, AuditEvent) | 97B | | MODIFY 4 entity files in `comment/`, `notification/`, `audit/` packages. Same mechanical removal as 97.1. |
| 97.6 | Strip TenantAware from portal/rate/budget/settings entities (PortalContact, BillingRate, CostRate, ProjectBudget, OrgSettings) | 97B | | MODIFY 5 entity files in `portal/`, `billingrate/`, `costrate/`, `budget/`, `settings/` packages. Same mechanical removal. |
| 97.7 | Strip TenantAware from tag/view/template/field entities (Tag, EntityTag, SavedView, DocumentTemplate, GeneratedDocument, FieldDefinition, FieldGroup, FieldGroupMember) + InvoiceCounter | 97B | | MODIFY 9 entity files in `tag/`, `view/`, `template/`, `fielddefinition/`, `invoice/` packages. Same mechanical removal. **InvoiceCounter** (`invoice/InvoiceCounter.java`): additionally remove `tenantId` field, constructor parameter, getter, and the `DEDICATED_SCHEMA_SENTINEL` constant. Per architecture Section 3.7.3. |
| 97.8 | Remove `findOneById`/`findAllByIds` from repos batch 2 + delete TenantAware stub | 97B | | MODIFY 14 repo files: `CommentRepository`, `NotificationRepository`, `AuditEventRepository`, `PortalContactRepository`, `BillingRateRepository`, `CostRateRepository`, `ProjectBudgetRepository`, `OrgSettingsRepository`, `TagRepository` (remove findOneById + findAllByIds), `SavedViewRepository`, `DocumentTemplateRepository`, `GeneratedDocumentRepository`, `FieldDefinitionRepository`, `FieldGroupRepository`. DELETE `multitenancy/TenantAware.java` (the empty stub). Build will not compile until 97C migrates call sites. |
| 97.9 | Migrate `findOneById` -> `findById` in high-frequency services | 97C | | MODIFY services with the most call sites: `InvoiceService.java` (25 occurrences), `TimeEntryService.java` (13), `TaskService.java` (9), `FieldGroupService.java` (8), `CustomerProjectService.java` (7). Find-and-replace `findOneById(` with `findById(` in each file. Return type is identical (`Optional<Entity>`), so no further changes needed. 5 files, ~62 occurrences. |
| 97.10 | Migrate `findOneById` -> `findById` in medium-frequency services | 97C | | MODIFY: `ProjectService.java` (6), `DocumentService.java` (6), `CustomerService.java` (6), `BillingRateService.java` (6), `CommentService.java` (5), `GeneratedDocumentService.java` (5), `DocumentTemplateService.java` (5), `NotificationService.java` (4). 8 files, ~43 occurrences. |
| 97.11 | Migrate `findOneById` -> `findById` in remaining services and controllers | 97C | | MODIFY: `PortalQueryService.java` (3), `PortalContactService.java` (3), `ReportService.java` (3), `FieldDefinitionService.java` (3), `InvoiceContextBuilder.java` (3), `CostRateService.java` (2), `ProjectMemberService.java` (2), `ProjectAccessService.java` (1), `SavedViewService.java` (2), `TagService.java` (2), `ProjectContextBuilder.java` (2), `PortalAuthController.java` (2), `DashboardService.java` (1), `PortalResyncService.java` (1), `PdfRenderingService.java` (1), `TemplateContextHelper.java` (1), `BudgetCheckService.java` (1), `TaskController.java` (1), `ProjectController.java` (1), `CustomerController.java` (1), `TimeEntryController.java` (1). 21 files, ~34 occurrences. |
| 97.12 | Migrate `findAllByIds` -> `findAllById` call sites | 97C | | MODIFY: `InvoiceService.java` (findAllByIds on ProjectRepository, InvoiceRepository), `CustomerContextBuilder.java` (findAllByIds on ProjectRepository), `TimeEntryController.java` (findAllByIds on InvoiceRepository), `EntityTagService.java` (3 findAllByIds on TagRepository), `TemplateContextHelper.java` (findAllByIds on TagRepository). ~11 occurrences in ~5 files (some files overlap with 97.9-97.11). |
| 97.13 | Update `ViewFilterService` — remove tenant_id filter | 97C | | MODIFY `view/ViewFilterService.java`. Remove the `e.tenant_id = :tenantOrgId` condition and `params.put("tenantOrgId", RequestScopes.ORG_ID.get())`. Remove the `ORG_ID.isBound()` check. Schema isolation handles tenant filtering for native queries. Per architecture Section 3.7.1. **Critical**: without this fix, all saved view queries will reference a non-existent `tenant_id` column. |
| 97.14 | Update `TagFilterHandler` — remove tenant_id JOIN | 97C | | MODIFY `view/TagFilterHandler.java`. Remove `et.tenant_id = e.tenant_id` from the EXISTS subquery. Per architecture Section 3.7.2. **Critical**: without this fix, tag filters in saved views will produce SQL errors. |
| 97.15 | Refactor `InvoiceNumberService` + callers | 97C | | MODIFY `invoice/InvoiceNumberService.java`. Remove `tenantId` parameter from `assignNumber()`. Remove `DEDICATED_SCHEMA_SENTINEL` constant. Rewrite native UPSERT SQL to remove `tenant_id` references — use singleton constraint instead. Update caller: `InvoiceService.java` (`assignNumber(tenantId)` -> `assignNumber()`). Per architecture Section 3.7.3. **Critical**: without this fix, invoice number generation will throw SQL error. |
| 97.16 | Verify no `setTenantId` calls remain + compilation check | 97C | | Run `grep -r "setTenantId\|getTenantId" backend/src/main/java/ --include="*.java"`. Expected: 0 results. Run `grep -r "findOneById\|findAllByIds" backend/src/main/java/ --include="*.java"`. Expected: 0 results. Run `./mvnw compile -q`. Expected: compilation succeeds. |

### Key Files

**Slice 97A — Modify (entities):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/Document.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/Member.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMember.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerProject.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/Task.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java`

**Slice 97A — Modify (repositories):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLineRepository.java`

**Slice 97B — Modify (entities):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/Comment.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/Notification.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationPreference.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContact.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/costrate/CostRate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/ProjectBudget.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/Tag.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/EntityTag.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/SavedView.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocument.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinition.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroup.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupMember.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceCounter.java`

**Slice 97B — Modify (repositories):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContactRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/costrate/CostRateRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/ProjectBudgetRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/TagRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/SavedViewRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupRepository.java`

**Slice 97B — Delete:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAware.java` — Delete the empty stub

**Slice 97C — Modify (services with findOneById, grouped by call count):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` — 25 findOneById + findAllByIds calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` — 13 calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` — 9 calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java` — 8 calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerProjectService.java` — 7 calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` — 6 calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentService.java` — 6 calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` — 6 calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateService.java` — 6 calls
- (+ 25 more service/controller/handler files with 1-5 calls each, see tasks 97.11-97.12)

**Slice 97C — Modify (service-level native SQL):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/ViewFilterService.java` — Remove tenant_id filter
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/TagFilterHandler.java` — Remove tenant_id JOIN
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceNumberService.java` — Remove tenant discriminator

### Architecture Decisions

- **Mechanical entity cleanup**: Each entity loses the same 7 items: `implements TenantAware`, `@FilterDef`, `@Filter`, `@EntityListeners`, `tenant_id` field, `getTenantId()`, `setTenantId()`. No judgment calls needed per entity.
- **findOneById -> findById is type-safe**: Both return `Optional<Entity>`. The replacement is a guaranteed-safe find-and-replace.
- **findAllByIds -> findAllById**: `JpaRepository.findAllById(Iterable<ID>)` accepts `List<UUID>` directly. Return type is `List<Entity>`. Call sites pass `List<UUID>` already.
- **InvoiceNumberService singleton constraint**: With no `tenant_id`, the counter is one-row-per-schema. Use a singleton constraint (architecture Section 3.7.3) or simplify to single-row semantics via `ON CONFLICT ON CONSTRAINT`.
- **ViewFilterService removal is safe**: Native queries already run within the correct schema (`SET search_path TO tenant_<hash>`). The `tenant_id` filter was defense-in-depth for shared schema only.

---

## Epic 98: Migration Rewrite & Renumber

**Goal**: Delete the two shared-schema foundation migrations (V7, V8), strip `tenant_id` columns, indexes, and RLS policies from 15 remaining migrations, fix 7 unique constraints that change shape, and renumber V9-V30 to V7-V28. After this epic, all tenant migrations are clean DDL with no shared-schema artifacts. This is a clean-slate operation — no production data exists.

**References**: Architecture doc Sections 3.5 (migration changes), Section 3.5.3 (special cases).

**Dependencies**: None (SQL files are independent of Java code). However, must be committed atomically with entity changes (Epic 97) to avoid Hibernate schema mismatch at runtime.

**Scope**: Backend (SQL migrations only)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **98A** | 98.1-98.8 | Delete V7 and V8. Edit 15 migrations in-place to remove tenant_id columns, indexes, RLS policies. Fix 7 unique constraints (customers email, org_settings singleton, invoice number dual partial indexes, invoice_counters, field_definitions/field_groups slug, document_templates slug). Renumber V9-V30 to V7-V28. Verify no tenant_id/RLS artifacts remain. | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 98.1 | Delete V7 and V8 migrations | 98A | | DELETE `V7__add_tenant_id_for_shared.sql` and `V8__shared_schema_member_unique.sql`. V7 added tenant_id + RLS to V1-V5 tables. V8 widened UNIQUE(clerk_user_id) to UNIQUE(clerk_user_id, tenant_id). The original V3 UNIQUE(clerk_user_id) is correct for dedicated schemas. |
| 98.2 | Strip tenant_id/RLS from V9-V13 (customers, customer_projects, tasks, documents_scope, time_entries) | 98A | | MODIFY 5 files: `V9__create_customers.sql`, `V10__create_customer_projects.sql`, `V11__create_tasks.sql`, `V12__extend_documents_scope.sql` (if tenant_id present), `V13__create_time_entries.sql`. For each: remove `tenant_id VARCHAR(255)` column, remove `CREATE INDEX idx_*_tenant_id`, remove `ALTER TABLE ... ENABLE ROW LEVEL SECURITY`, remove `CREATE POLICY ... USING (tenant_id = ...)`. **V9 special case**: change `UNIQUE(email, tenant_id)` to `UNIQUE(email)`. |
| 98.3 | Strip tenant_id/RLS from V14-V18 (audit_events, comments, notifications, notification_preferences, portal_contacts) | 98A | | MODIFY 5 files: `V14__create_audit_events.sql`, `V15__create_comments.sql`, `V16__create_notifications.sql`, `V17__create_notification_preferences.sql`, `V18__create_portal_contacts_and_magic_link_tokens.sql`. Same removal pattern. |
| 98.4 | Strip tenant_id/RLS from V19 (rate/budget tables) + fix org_settings singleton | 98A | | MODIFY `V19__create_rate_budget_tables.sql`. Remove tenant_id columns, indexes, RLS from billing_rates, cost_rates, project_budgets tables. **org_settings special case**: remove `CREATE UNIQUE INDEX idx_org_settings_tenant ON org_settings (tenant_id)` entirely — singleton per schema is implicit (one schema = one org). |
| 98.5 | Strip tenant_id/RLS from V23 (invoices) + fix dual partial indexes | 98A | | MODIFY `V23__create_invoices.sql`. Remove tenant_id columns, indexes, RLS from invoices, invoice_lines tables. **invoice_counters special case**: remove `tenant_id` column, remove `CREATE UNIQUE INDEX idx_invoice_counters_tenant ON invoice_counters (tenant_id)`, add `ALTER TABLE invoice_counters ADD CONSTRAINT invoice_counters_singleton UNIQUE (id)` (or simpler singleton mechanism). **invoices special case**: replace dual partial indexes (`WHERE tenant_id IS NULL` / `WHERE tenant_id IS NOT NULL`) with single `CREATE UNIQUE INDEX idx_invoices_number_unique ON invoices (invoice_number) WHERE invoice_number IS NOT NULL`. Per architecture Section 3.5.3. |
| 98.6 | Strip tenant_id/RLS from V24-V25 (field_definitions, field_groups, tags) + fix slug uniqueness | 98A | | MODIFY `V24__add_field_definitions_groups.sql` and `V25__add_tags.sql`. **V24 special cases**: change `UNIQUE(tenant_id, entity_type, slug)` to `UNIQUE(entity_type, slug)` for both field_definitions and field_groups. **V25**: remove tenant_id from tags and entity_tags tables, remove RLS. |
| 98.7 | Strip tenant_id/RLS from V28-V29 (saved_views, document_templates) + fix slug uniqueness | 98A | | MODIFY `V28__add_saved_views.sql` and `V29__create_document_templates.sql`. **V29 special case**: replace dual partial indexes on document_templates slug with single `UNIQUE(slug)`. Per architecture Section 3.5.3. |
| 98.8 | Renumber migrations V9-V30 to V7-V28 + verify | 98A | | RENAME all migration files: V9->V7, V10->V8, V11->V9, ..., V30->V28 (each decremented by 2). Run verification: `grep -r "tenant_id\|ROW LEVEL SECURITY\|CREATE POLICY" backend/src/main/resources/db/migration/tenant/ --include="*.sql"` — expect 0 results. Note: V20 (rate snapshots), V21 (budget version), V22 (dashboard indexes), V26 (field pack status), V27 (custom field columns), V30 (branding/template pack status) may not have tenant_id/RLS but still need renumbering. |

### Key Files

**Slice 98A — Delete:**
- `backend/src/main/resources/db/migration/tenant/V7__add_tenant_id_for_shared.sql`
- `backend/src/main/resources/db/migration/tenant/V8__shared_schema_member_unique.sql`

**Slice 98A — Modify + Rename (migrations with tenant_id/RLS, listed with original names):**
- `V9__create_customers.sql` → V7
- `V10__create_customer_projects.sql` → V8
- `V11__create_tasks.sql` → V9
- `V12__extend_documents_scope.sql` → V10
- `V13__create_time_entries.sql` → V11
- `V14__create_audit_events.sql` → V12
- `V15__create_comments.sql` → V13
- `V16__create_notifications.sql` → V14
- `V17__create_notification_preferences.sql` → V15
- `V18__create_portal_contacts_and_magic_link_tokens.sql` → V16
- `V19__create_rate_budget_tables.sql` → V17
- `V23__create_invoices.sql` → V21
- `V24__add_field_definitions_groups.sql` → V22
- `V25__add_tags.sql` → V23
- `V28__add_saved_views.sql` → V26
- `V29__create_document_templates.sql` → V27

**Slice 98A — Rename only (no content changes, just version number):**
- `V20__add_time_entry_rate_snapshots.sql` → V18
- `V21__add_project_budget_version.sql` → V19
- `V22__add_dashboard_indexes.sql` → V20
- `V26__add_field_pack_status.sql` → V24
- `V27__add_custom_field_columns.sql` → V25
- `V30__add_branding_and_template_pack_status.sql` → V28

### Architecture Decisions

- **Clean-slate approach**: No production data. `docker compose down -v` destroys all schemas and Flyway history. In-place editing is safe.
- **Renumbering strategy**: Decrement all versions by 2 (delete V7/V8, shift V9-V30 to V7-V28). Flyway reads version numbers from filenames, so a clean start sees no gaps.
- **Unique constraint changes**: 7 constraints change shape because they previously included `tenant_id` as a discriminator. In a dedicated schema, `tenant_id` is redundant — the schema boundary provides the isolation. `UNIQUE(email)` per schema means unique per tenant.
- **invoice_counters singleton**: Without `tenant_id`, one counter row per schema. The UPSERT ON CONFLICT targets the singleton constraint.

---

## Epic 99: Test Cleanup, Verification & Documentation

**Goal**: Delete the 7 shared-schema test files, update ~16 test files (replace `findOneById` with `findById`, remove `tenant_shared` references, update schema expectations), update project documentation (ADR-064 cross-references, ARCHITECTURE.md Section 8, backend/CLAUDE.md), and run the full verification suite to confirm zero shared-schema artifacts remain.

**References**: Architecture doc Sections 3.9 (test changes), 5 (verification strategy).

**Dependencies**: Epics 96, 97, 98 (all code and migration changes must be complete)

**Scope**: Backend + Documentation

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **99A** | 99.1-99.9 | Delete 6 shared-schema test files (StarterTenantIntegrationTest, MixedTenantIntegrationTest, TimeEntrySharedSchemaIntegrationTest, TaskStarterTenantIntegrationTest, BillingUpgradeIntegrationTest, TierUpgradeIntegrationTest). Update ~16 test files: replace findOneById->findById (~80 occurrences), remove tenant_shared references, update provisioning test expectations. Run `./mvnw clean verify -q` and fix any test failures. | |
| **99B** | 99.10-99.15 | Update ARCHITECTURE.md Section 8, update backend/CLAUDE.md (remove shared-schema references, anti-patterns), update MEMORY.md, add cross-references to ADR-064 from ADR-011/012/015/016, update TASKS.md Phase 13 entry. Run full 10-point verification suite from architecture doc Section 5. Final `./mvnw clean verify` with full pass. | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 99.1 | Delete shared-schema test files | 99A | | DELETE 6 test files: `multitenancy/StarterTenantIntegrationTest.java`, `multitenancy/MixedTenantIntegrationTest.java`, `timeentry/TimeEntrySharedSchemaIntegrationTest.java`, `task/TaskStarterTenantIntegrationTest.java`, `billing/BillingUpgradeIntegrationTest.java`, `provisioning/TierUpgradeIntegrationTest.java`. These exclusively test shared-schema isolation or tier-upgrade migration — both eliminated. |
| 99.2 | Update `findOneById` -> `findById` in all test files | 99A | | MODIFY ~16 test files with `findOneById` references (~80 occurrences). Mechanical find-and-replace. Files: `DocumentServiceTest` (16), `ProjectServiceTest` (9), `ProjectContextBuilderTest` (8), `InvoiceContextBuilderTest` (8), `CustomerContextBuilderTest` (6), `InvoiceIntegrationTest` (5), `FieldGroupIntegrationTest` (5), `SavedViewIntegrationTest` (4), `TagIntegrationTest` (4), `PortalEventHandlerTest` (4), `DocumentTemplateIntegrationTest` (3), `FieldDefinitionIntegrationTest` (3), `ViewFilterIntegrationTest` (2), `InvoiceLifecycleIntegrationTest` (1), `FieldGroupMemberIntegrationTest` (1), `AuditTenantIsolationTest` (1). |
| 99.3 | Update provisioning test files | 99A | | MODIFY `provisioning/ProvisioningIntegrationTest.java`: remove Starter-path test cases, all tests should expect dedicated schema (`tenant_<hash>`). MODIFY `provisioning/TenantProvisioningServiceTest.java`: remove Starter-specific mocking (`provisionStarter()`, `SHARED_SCHEMA`), update to test single provisioning path. |
| 99.4 | Update audit test files | 99A | | MODIFY `audit/AuditTenantIsolationTest.java` and `audit/InternalAuditControllerTest.java`: remove `tenant_shared` references in test setup, update schema references to `tenant_<hash>`. |
| 99.5 | Update `PlanEnforcementIntegrationTest` | 99A | | MODIFY `member/PlanEnforcementIntegrationTest.java`: remove Starter-schema-specific setup if present. Ensure PRO plan sync still occurs. |
| 99.6 | Update view/tag test files | 99A | | MODIFY `view/ViewFilterIntegrationTest.java`: remove `tenantOrgId` parameter assertions (ViewFilterService no longer adds tenant_id filter). MODIFY `view/SavedViewIntegrationTest.java`: remove shared-schema references. MODIFY `tag/TagIntegrationTest.java`: remove shared-schema references. |
| 99.7 | Verify no shared-schema references in test source | 99A | | Run `grep -r "tenant_shared\|SHARED_SCHEMA\|TenantInfo\|findTenantInfoByClerkOrgId\|provisionStarter\|TenantUpgradeService\|TenantFilterTransactionManager\|TenantAwareEntityListener" backend/src/test/java/ --include="*.java"`. Expected: 0 results. |
| 99.8 | Run full test suite | 99A | | Run `./mvnw clean verify -q 2>&1 | tail -30`. Expected: all tests pass. Expected test count: ~790+ (down from ~830+ due to deleted test files). If failures occur, fix them — common issues: test still references `tenant_shared` string, test creates `TenantInfo` object, test calls deleted method. |
| 99.9 | Verify main source is clean | 99A | | Run all 10 verification commands from architecture doc Section 5.1. All must return 0 results. |
| 99.10 | Update `architecture/ARCHITECTURE.md` Section 8 | 99B | | MODIFY Section 8 (Database Architecture): replace dual-path description with single-path dedicated-schema model. Remove shared-schema diagrams. Add reference to ADR-064. Keep `Tier` enum description (billing/feature gating, not schema topology). |
| 99.11 | Update `backend/CLAUDE.md` | 99B | | MODIFY: remove all shared-schema references, anti-patterns, `TenantAware` usage instructions, `findOneById` patterns. Update multitenancy section to describe single dedicated-schema path. Remove `TenantFilterTransactionManager` references. |
| 99.12 | Add cross-references to ADR-064 | 99B | | MODIFY `adr/ADR-011-tiered-tenancy.md`: add note "Partially superseded by ADR-064 — tier no longer determines schema topology". MODIFY `adr/ADR-012-row-level-isolation.md`: add note "Fully superseded by ADR-064". MODIFY `adr/ADR-015-provisioning-per-tier.md`: add note "Partially superseded by ADR-064 — single provisioning path". MODIFY `adr/ADR-016-tier-upgrade-migration.md`: add note "Fully superseded by ADR-064 — no upgrade migration needed". |
| 99.13 | Update TASKS.md Phase 13 entry | 99B | | MODIFY `TASKS.md`: update Phase 13 epic overview to reflect actual slices completed. Update epic names/descriptions to match this task file. |
| 99.14 | Update MEMORY.md | 99B | | Update project memory with Phase 13 completion notes: shared-schema path eliminated, new entity creation no longer requires TenantAware boilerplate, standard `findById` used everywhere, migration versioning shifted by -2. |
| 99.15 | Final verification and sign-off | 99B | | Run `docker compose -f compose/docker-compose.yml down -v && docker compose -f compose/docker-compose.yml up -d`. Run `./mvnw clean verify -q`. Start app: `./mvnw spring-boot:run`, provision a Starter tenant, verify schema is `tenant_<hash>` (not `tenant_shared`). Verify `SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'tenant_shared'` returns 0 rows. |

### Key Files

**Slice 99A — Delete:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/StarterTenantIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/MixedTenantIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntrySharedSchemaIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/task/TaskStarterTenantIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/BillingUpgradeIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/TierUpgradeIntegrationTest.java`

**Slice 99A — Modify (test files with findOneById, by occurrence count):**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/document/DocumentServiceTest.java` — 16 occurrences
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/project/ProjectServiceTest.java` — 9 occurrences
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/ProjectContextBuilderTest.java` — 8 occurrences
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/InvoiceContextBuilderTest.java` — 8 occurrences
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/CustomerContextBuilderTest.java` — 6 occurrences
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceIntegrationTest.java` — 5 occurrences
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupIntegrationTest.java` — 5 occurrences
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/view/SavedViewIntegrationTest.java` — 4 occurrences
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/tag/TagIntegrationTest.java` — 4 occurrences
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandlerTest.java` — 4 occurrences
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateIntegrationTest.java` — 3 occurrences
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinitionIntegrationTest.java` — 3 occurrences
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/view/ViewFilterIntegrationTest.java` — 2 occurrences
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLifecycleIntegrationTest.java` — 1 occurrence
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupMemberIntegrationTest.java` — 1 occurrence
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditTenantIsolationTest.java` — 1 occurrence

**Slice 99A — Modify (shared-schema references):**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/ProvisioningIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditTenantIsolationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/InternalAuditControllerTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/member/PlanEnforcementIntegrationTest.java`

**Slice 99B — Modify:**
- `architecture/ARCHITECTURE.md` — Section 8
- `backend/CLAUDE.md` — Remove shared-schema patterns
- `adr/ADR-011-tiered-tenancy.md` — Add superseded note
- `adr/ADR-012-row-level-isolation.md` — Add superseded note
- `adr/ADR-015-provisioning-per-tier.md` — Add superseded note
- `adr/ADR-016-tier-upgrade-migration.md` — Add superseded note
- `TASKS.md` — Update Phase 13 entry

### Architecture Decisions

- **Test count reduction**: Expect ~40 fewer tests after deleting 6 shared-schema test files. All remaining tests must pass.
- **ADR-064 already written**: The ADR file exists at `adr/ADR-064-dedicated-schema-only.md` (accepted status). This slice adds cross-references from superseded ADRs.
- **Clean database required for verification**: `docker compose down -v && up -d` must precede the final `./mvnw clean verify` run because Flyway version numbers changed.
- **No frontend changes**: This phase is entirely backend. No frontend tests need updating.
