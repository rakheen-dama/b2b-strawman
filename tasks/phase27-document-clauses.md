# Phase 27 -- Document Clauses

Phase 27 adds a **clause library** to the DocTeams document generation system. Clauses are reusable, variable-aware text blocks (HTML/Thymeleaf) that can be composed into documents at generation time. Template authors configure which clauses a template suggests; users pick and reorder clauses before generating. A Standard Clauses pack seeds ~12 professional-services clauses per tenant.

**Architecture doc**: `architecture/phase27-document-clauses.md`

**ADRs**:
- [ADR-104](../adr/ADR-104-clause-rendering-strategy.md) -- Clause Rendering Strategy (string concatenation before single Thymeleaf pass)
- [ADR-105](../adr/ADR-105-clause-snapshot-depth.md) -- Clause Snapshot Depth (metadata only, PDF is the content snapshot)
- [ADR-106](../adr/ADR-106-template-clause-placeholder-strategy.md) -- Template-Clause Placeholder Strategy (single `${clauses}` variable, append fallback)

**Migration**: V44 tenant -- `clauses` table + `template_clauses` table + `generated_documents.clause_snapshots` JSONB + `org_settings.clause_pack_status` JSONB.

**Dependencies on prior phases**: Phase 12 (Document Templates/PDF), Phase 13 (Dedicated Schema), Phase 23 (Template Validation), Phase 6 (Audit).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 187 | Clause Entity Foundation + Migration | Backend | -- | M | 187A, 187B | |
| 188 | Template-Clause Association API | Backend | 187 | M | 188A | |
| 189 | Generation Pipeline Extension | Backend | 187, 188 | L | 189A, 189B | |
| 190 | Clause Pack Seeder | Backend | 187, 188 | M | 190A | |
| 191 | Clause Library Frontend | Frontend | 187 | M | 191A, 191B | |
| 192 | Template Clauses Tab + Generation Dialog Frontend | Frontend | 188, 189, 191 | L | 192A, 192B | |

---

## Dependency Graph

```
[E187A Clause Entity + V44 Migration + Repository]
        |
[E187B ClauseService + Controller + Audit + Tests]
        |
        +----------------------------+----------------------------+
        |                            |                            |
[E188A TemplateClause Entity       [E191A Clause Actions          |
 + Service + Controller + Tests]    + Clause Library Page]        |
        |                            |                            |
        +----------------------------+----------------------------+
        |                                                         |
[E189A ClauseAssembler + Rendering                              [E191B Clause Form Dialog
 Context Injection + Preview Extension]                          + Preview + Clone/Deactivate]
        |
[E189B Generation Request Extension
 + Snapshot Recording + Backward Compat Tests]
        |
        +----------------------------+
        |                            |
[E190A ClausePackSeeder            [E192A Template Clauses Tab
 + Pack JSON + Provisioning Hook]   + Clause Picker Dialog]
                                     |
                                   [E192B Generation Dialog
                                    Clause Step + Action Extension]
```

**Parallel opportunities**:
- Epics 188 and 191 are fully independent after 187B -- can start in parallel immediately.
- After 188A completes: Epics 189 and 190 can start in parallel (190 requires 188A for TemplateClause associations).
- 191A and 191B are sequential (library page before form dialog).
- 192A and 192B are sequential (template tab before generation dialog).
- 192B is the final slice -- it requires all backend functionality (189B) and the frontend clause library (191B).

---

## Implementation Order

### Stage 0: Entity Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 187 | 187A | V44 migration (complete DDL: `clauses` table, `template_clauses` table, `generated_documents.clause_snapshots` column, `org_settings.clause_pack_status` column, indexes) + `Clause` entity + `ClauseSource` enum + `ClauseRepository` with custom queries + entity unit tests + repository integration tests. ~6 new files, ~1 migration file. Backend only. | **Done** (PR #387) |
| 0b | 187 | 187B | `ClauseService` (create, update, delete with reference guard, deactivate, clone, list, getById, preview) + DTOs (`CreateClauseRequest`, `UpdateClauseRequest`, `ClauseResponse`) + `ClauseController` at `/api/clauses` with RBAC + audit events + integration tests. ~7 new files, ~0-1 modified files. Backend only. | |

### Stage 1: Associations + Frontend Library (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 188 | 188A | `TemplateClause` entity + `TemplateClauseRepository` + `TemplateClauseService` (CRUD, replace-all, add, remove) + `TemplateClauseController` at `/api/templates/{templateId}/clauses` + `template_clause.configured` audit event + integration tests. ~7 new files, ~0-1 modified files. Backend only. | |
| 1b (parallel) | 191 | 191A | `clause-actions.ts` server actions + `settings/clauses/page.tsx` server component + `clauses-content.tsx` client component (grouped list, search, category filter) + sidebar navigation update (add "Clauses" link under Settings). ~4 new files, ~1 modified file. Frontend only. | |

### Stage 2: Rendering Pipeline + Frontend Form (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 189 | 189A | `ClauseAssembler` utility (clause body concatenation + div wrapping) + extend `PdfRenderingService` (inject `clauses`/`clauseCount` into context, add `renderFragment()` public method for clause preview, template fallback for missing `${clauses}` placeholder) + extend `GeneratedDocument` entity with `clauseSnapshots` JSONB field + integration tests for assembly + rendering. ~2 new files, ~2 modified files. Backend only. | |
| 2b (parallel) | 191 | 191B | `clause-form-dialog.tsx` (create/edit with title, category combobox, description, body textarea, preview button) + `clause-preview-panel.tsx` (rendered HTML display) + clone action + deactivate action + frontend tests for clause management. ~3 new files, ~1 modified file. Frontend only. | |

### Stage 3: Generation Extension + Pack Seeder (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a (parallel) | 189 | 189B | Extend `GenerateDocumentRequest` and `PreviewRequest` with optional `clauses` field + clause resolution logic (fallback to template defaults, required clause validation 422) + snapshot recording in `GeneratedDocumentService` + `document.generated_with_clauses` audit event + backward compatibility tests (templates without clauses) + integration tests. ~3 modified files, ~0-1 new test files. Backend only. | |
| 3b (parallel) | 190 | 190A | `ClausePackSeeder` + `ClausePackDefinition` record + `clause-packs/standard-clauses/pack.json` (~12 clause bodies) + default template-clause associations for engagement letter and SOW templates + `OrgSettings.clausePackStatus` field + `recordClausePackApplication()` method + provisioning hook (`@DependsOn` or call ordering after `TemplatePackSeeder`) + integration tests (pack loading, idempotency, SYSTEM source, association creation). ~4 new files, ~2 modified files. Backend only. | |

### Stage 4: Template Clauses Tab Frontend

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a | 192 | 192A | `template-clause-actions.ts` server actions + `template-clauses-tab.tsx` (ordered clause list with required toggle, reorder up/down, remove button, save) + `clause-picker-dialog.tsx` (browse library grouped by category, multi-select, confirm) + template editor integration (add "Clauses" tab alongside existing tabs) + frontend tests. ~4 new files, ~1 modified file. Frontend only. | |

### Stage 5: Generation Dialog Clause Step Frontend

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a | 192 | 192B | `generation-clause-step.tsx` (checklist with required/optional, browse library button, reorder, "Next: Preview" button) + extend `GenerateDocumentDialog.tsx` (multi-step: clause selection -> preview -> generate, skip clause step when template has no associations) + extend `generateDocumentAction` and `previewTemplateAction` with clause support + frontend tests (clause step shown/hidden, required enforcement, preview with clauses, backward compat). ~2 new files, ~3 modified files. Frontend only. | |

### Timeline

```
Stage 0: [187A] --> [187B]                                          (sequential)
Stage 1: [188A] // [191A]                                           (parallel, after 187B)
Stage 2: [189A] // [191B]                                           (parallel, after 188A / 191A respectively)
Stage 3: [189B] // [190A]                                           (parallel, after 189A / 188A respectively)
Stage 4: [192A]                                                     (after 188A + 191B)
Stage 5: [192B]                                                     (after 189B + 192A)
```

**Critical path**: 187A -> 187B -> 188A -> 189A -> 189B -> 192A -> 192B

---

## Epic 187: Clause Entity Foundation + Migration

**Goal**: Create the `Clause` entity, `ClauseSource` enum, repository, service, controller, V44 migration, audit events, and RBAC. This is the foundation for all subsequent clause work.

**References**: Architecture doc Sections 2.1 (Clause entity), 3.1 (CRUD flows), 4.1 (API surface), 8 (migration SQL), 9.1 (backend file list), 10 (permissions). ADR-104, ADR-105, ADR-106.

**Dependencies**: None -- this is the foundation epic.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **187A** | 187.1--187.7 | V44 migration (complete DDL: `clauses` table, `template_clauses` table, `clause_snapshots` on `generated_documents`, `clause_pack_status` on `org_settings`, all indexes and constraints) + `Clause` entity + `ClauseSource` enum + `ClauseRepository` with custom queries + entity unit tests + repository integration tests. ~6 new files, ~1 migration file. Backend only. | **Done** (PR #387) |
| **187B** | 187.8--187.16 | `ClauseService` (create, update, delete with reference guard, deactivate, clone, list, getById, preview via `PdfRenderingService`) + DTOs (`CreateClauseRequest`, `UpdateClauseRequest`, `ClauseResponse`) + `ClauseController` at `/api/clauses` with RBAC + audit events + service tests + controller integration tests. ~7 new files, ~0-1 modified files. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 187.1 | Create V44 tenant migration | 187A | | New file: `db/migration/tenant/V44__create_clause_tables.sql`. (1) CREATE `clauses` table per architecture doc Section 8 with slug UNIQUE, slug format CHECK, source CHECK constraints. (2) CREATE INDEX `idx_clauses_active_category` partial index on `(active, category, sort_order) WHERE active = true`. (3) CREATE INDEX `idx_clauses_pack_id` partial index on `(pack_id) WHERE pack_id IS NOT NULL`. (4) CREATE `template_clauses` table with FK to `document_templates(id) ON DELETE CASCADE` and FK to `clauses(id) ON DELETE RESTRICT`, UNIQUE on `(template_id, clause_id)`. (5) CREATE INDEX `idx_template_clauses_template_id` on `(template_id, sort_order)`. (6) CREATE INDEX `idx_template_clauses_clause_id` on `(clause_id)`. (7) ALTER `generated_documents` ADD `clause_snapshots JSONB`. (8) ALTER `org_settings` ADD `clause_pack_status JSONB`. Note: Architecture doc says V43 but V43 is taken by Phase 26. |
| 187.2 | Create `ClauseSource` enum | 187A | | New file: `clause/ClauseSource.java`. Enum: `SYSTEM`, `CLONED`, `CUSTOM`. Distinct from `TemplateSource` -- different semantics (SYSTEM = read-only pack-seeded, CLONED = customizable copy of SYSTEM, CUSTOM = org-created). |
| 187.3 | Create `Clause` entity | 187A | 187.1, 187.2 | New file: `clause/Clause.java`. `@Entity`, `@Table(name = "clauses")`. Fields per architecture doc Section 2.1: id (UUID PK), title (String 200), slug (String 200), description (String 500 nullable), body (TEXT), category (String 100), source (ClauseSource), sourceClauseId (UUID nullable), packId (String 100 nullable), active (boolean default true), sortOrder (int default 0), createdAt (Instant), updatedAt (Instant). Protected no-arg constructor. Public constructor `(String title, String slug, String body, String category)`. `update()`, `deactivate()`, `cloneFrom()` methods per architecture doc Section 9.3. Pattern: `tax/TaxRate.java` entity style (no Lombok, constructor initialization). |
| 187.4 | Create `ClauseRepository` | 187A | 187.3 | New file: `clause/ClauseRepository.java`. `JpaRepository<Clause, UUID>`. Custom queries: `findByActiveTrueOrderByCategoryAscSortOrderAsc()`, `findBySlug(String slug)`, `findByCategoryAndActiveTrueOrderBySortOrderAsc(String category)`, `findByPackIdAndSourceAndActiveTrue(String packId, ClauseSource source)`, `findAllByOrderByCategoryAscSortOrderAsc()` (for includeInactive). Also add `@Query("SELECT DISTINCT c.category FROM Clause c WHERE c.active = true ORDER BY c.category") List<String> findDistinctActiveCategories()` for the categories endpoint. |
| 187.5 | Write entity unit tests | 187A | 187.3 | New file: `clause/ClauseTest.java`. Tests: (1) constructor_sets_required_fields, (2) constructor_sets_timestamps_and_defaults, (3) update_modifies_fields_and_updatedAt, (4) deactivate_sets_active_false, (5) cloneFrom_creates_cloned_copy. ~5 tests. |
| 187.6 | Write repository integration tests | 187A | 187.4 | New file: `clause/ClauseRepositoryIntegrationTest.java`. Tests: (1) save_and_findById, (2) findByActiveTrueOrderByCategoryAscSortOrderAsc_returns_active_only, (3) findBySlug_returns_clause, (4) findByCategoryAndActiveTrueOrderBySortOrderAsc_filters_by_category, (5) findAllByOrderByCategoryAscSortOrderAsc_returns_all_including_inactive, (6) findDistinctActiveCategories_returns_unique_categories, (7) slug_unique_constraint_enforced. ~7 tests. Pattern: `tax/TaxRateRepositoryIntegrationTest.java`. |
| 187.7 | Verify migration runs on Testcontainers | 187A | 187.1 | Covered by repository integration tests: Flyway runs V44 automatically. Verify: `clauses` table created, `template_clauses` table created, `generated_documents.clause_snapshots` column added, `org_settings.clause_pack_status` column added. |
| 187.8 | Create `CreateClauseRequest` DTO | 187B | | New file: `clause/dto/CreateClauseRequest.java`. Java record with validation: `@NotBlank @Size(max=200) String title`, `@Size(max=500) String description`, `@NotBlank String body`, `@NotBlank @Size(max=100) String category`. |
| 187.9 | Create `UpdateClauseRequest` DTO | 187B | | New file: `clause/dto/UpdateClauseRequest.java`. Same fields and validation as `CreateClauseRequest`. |
| 187.10 | Create `ClauseResponse` DTO | 187B | | New file: `clause/dto/ClauseResponse.java`. Java record: `UUID id`, `String title`, `String slug`, `String description`, `String body`, `String category`, `ClauseSource source`, `UUID sourceClauseId`, `String packId`, `boolean active`, `int sortOrder`, `Instant createdAt`, `Instant updatedAt`. Static factory `from(Clause)`. |
| 187.11 | Create `ClauseService` | 187B | 187.4 | New file: `clause/ClauseService.java`. `@Service`. Constructor injection: `ClauseRepository`, `TemplateSecurityValidator`, `AuditService`. Methods: `createClause(CreateClauseRequest)` -- validate body via `TemplateSecurityValidator`, generate slug (extract `DocumentTemplate.generateSlug()` pattern or reimplement), check slug uniqueness with suffix, set source=CUSTOM. `updateClause(UUID id, UpdateClauseRequest)` -- block SYSTEM (400), regenerate slug if title changed. `deleteClause(UUID id)` -- hard delete, check TemplateClause references first (409 with count). `deactivateClause(UUID id)` -- set active=false. `cloneClause(UUID id)` -- use `Clause.cloneFrom()`, generate unique slug. `listClauses(boolean includeInactive, String category)`. `listCategories()`. `getById(UUID id)`. `previewClause(UUID clauseId, UUID entityId, TemplateEntityType entityType)` -- uses `TemplateContextBuilder` + `PdfRenderingService.renderFragment()`. Publish audit events for create/update/delete/clone/deactivate. Pattern: `tax/TaxRateService.java` for CRUD service, `template/DocumentTemplateService.java` for slug generation. Note: `TemplateClauseRepository` injection needed for deletion guard -- will be created in 188A. For 187B, inject as `Optional<TemplateClauseRepository>` or use a `@Lazy` injection to break circular dep. Alternatively, add a `long countByClauseId(UUID clauseId)` method to `ClauseRepository` as a native query joining `template_clauses`. Prefer the native query approach to avoid cross-epic dependency. |
| 187.12 | Create `ClauseController` | 187B | 187.11, 187.10 | New file: `clause/ClauseController.java`. `@RestController`, `@RequestMapping("/api/clauses")`. Endpoints: `GET /` (list, `@RequestParam(defaultValue = "false") boolean includeInactive`, `@RequestParam(required = false) String category`), `GET /categories` (distinct categories), `GET /{id}` (detail), `POST /` (`@PreAuthorize("hasAnyRole('ORG_ADMIN','ORG_OWNER')")`), `PUT /{id}` (same RBAC), `DELETE /{id}` (same RBAC, 409 if referenced), `POST /{id}/deactivate` (same RBAC), `POST /{id}/clone` (same RBAC), `POST /{id}/preview` (same RBAC, request body: `{entityId, entityType}`). Pattern: `tax/TaxRateController.java`. |
| 187.13 | Wire audit events | 187B | 187.11 | In `ClauseService`: use `AuditService.record()` with event types `clause.created`, `clause.updated`, `clause.deleted`, `clause.cloned`, `clause.deactivated`. Include relevant details (title, category, source, changed fields for update, source clause title for clone). Pattern: existing audit calls in `tax/TaxRateService.java`. |
| 187.14 | Write service unit tests | 187B | 187.11 | New file: `clause/ClauseServiceTest.java`. Tests: (1) createClause_saves_clause, (2) createClause_validates_body_security, (3) createClause_generates_unique_slug, (4) updateClause_changes_fields, (5) updateClause_blocks_system_clause_400, (6) deleteClause_hard_deletes, (7) deleteClause_referenced_returns_409, (8) deactivateClause_sets_inactive, (9) cloneClause_creates_cloned_copy, (10) listClauses_filters_inactive, (11) listClauses_filters_by_category, (12) listCategories_returns_distinct. ~12 tests. |
| 187.15 | Write controller integration tests | 187B | 187.12 | New file: `clause/ClauseControllerIntegrationTest.java`. MockMvc tests: (1) GET_returns_clause_list, (2) GET_categories_returns_distinct, (3) GET_by_id_returns_detail, (4) POST_creates_clause_as_admin, (5) POST_403_for_member, (6) PUT_updates_clause, (7) PUT_400_for_system_clause, (8) DELETE_hard_deletes_unreferenced, (9) DELETE_409_when_referenced, (10) POST_deactivate_sets_inactive, (11) POST_clone_creates_copy, (12) POST_preview_returns_html. ~12 tests. Pattern: `tax/TaxRateControllerIntegrationTest.java`. |
| 187.16 | Add deletion guard query to ClauseRepository | 187B | 187.4 | Add native query to `ClauseRepository`: `@Query(value = "SELECT COUNT(*) FROM template_clauses WHERE clause_id = :clauseId", nativeQuery = true) long countTemplateClauseReferences(@Param("clauseId") UUID clauseId)`. This avoids needing `TemplateClauseRepository` in 187B (which is created in 188A). The `template_clauses` table exists from V44 migration. |

### Key Files

**Slice 187A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V44__create_clause_tables.sql`
- `backend/src/main/java/.../clause/Clause.java`
- `backend/src/main/java/.../clause/ClauseSource.java`
- `backend/src/main/java/.../clause/ClauseRepository.java`
- `backend/src/test/java/.../clause/ClauseTest.java`
- `backend/src/test/java/.../clause/ClauseRepositoryIntegrationTest.java`

**Slice 187B -- Create:**
- `backend/src/main/java/.../clause/dto/CreateClauseRequest.java`
- `backend/src/main/java/.../clause/dto/UpdateClauseRequest.java`
- `backend/src/main/java/.../clause/dto/ClauseResponse.java`
- `backend/src/main/java/.../clause/ClauseService.java`
- `backend/src/main/java/.../clause/ClauseController.java`
- `backend/src/test/java/.../clause/ClauseServiceTest.java`
- `backend/src/test/java/.../clause/ClauseControllerIntegrationTest.java`

**Slice 187B -- Read for context:**
- `backend/src/main/java/.../template/TemplateSecurityValidator.java` -- body validation pattern
- `backend/src/main/java/.../template/DocumentTemplate.java` -- slug generation pattern
- `backend/src/main/java/.../template/PdfRenderingService.java` -- `renderFragment()` needed for preview (added in 189A, mock for 187B tests)
- `backend/src/main/java/.../audit/AuditService.java` -- audit event pattern
- `backend/src/main/java/.../tax/TaxRateController.java` -- CRUD controller pattern
- `backend/src/main/java/.../tax/TaxRateService.java` -- CRUD service pattern

### Architecture Decisions

- **Clause in `clause/` package (not `template/`)**: Clauses are conceptually related to templates but are a distinct domain. Separate package keeps feature cohesion and avoids bloating the template package.
- **Hard delete + deactivate (not soft-delete only)**: `deleteClause()` is a hard delete with a reference guard (409 if templates reference it). `deactivateClause()` is the soft-deactivation (sets `active = false`, always succeeds). Two distinct operations with distinct semantics -- avoids API confusion.
- **V44 migration (not V43)**: V43 is taken by Phase 26 (Invoice Tax Handling). Architecture doc references "V43" but this is stale.
- **Complete DDL in 187A**: All DDL (clauses, template_clauses, generated_documents extension, org_settings extension) in a single migration file. Flyway validates checksums -- splitting DDL across slices would cause checksum failures.
- **Native query for deletion guard**: `ClauseRepository` uses a native query joining `template_clauses` to count references, avoiding a dependency on `TemplateClauseRepository` (which is created in Epic 188).
- **Slug generation**: Reimplement slug generation in `ClauseService` (same algorithm as `DocumentTemplate.generateSlug()`). Extracting to a shared utility is ideal but not required for v1 -- the pattern is simple (lowercase, hyphenate, trim, uniqueness suffix).

---

## Epic 188: Template-Clause Association API

**Goal**: Create the `TemplateClause` join entity, repository, service, and controller that allow template authors to configure which clauses a template suggests.

**References**: Architecture doc Sections 2.2 (TemplateClause entity), 3.2 (association management flows), 4.2 (API surface), 9.1 (file list).

**Dependencies**: Epic 187 (Clause entity and V44 migration must exist).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **188A** | 188.1--188.8 | `TemplateClause` entity + `TemplateClauseRepository` + `TemplateClauseService` (getTemplateClauses, setTemplateClauses, addClauseToTemplate, removeClauseFromTemplate) + `TemplateClauseController` at `/api/templates/{templateId}/clauses` + `template_clause.configured` audit event + DTOs + integration tests. ~7 new files, ~0-1 modified files. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 188.1 | Create `TemplateClause` entity | 188A | | New file: `clause/TemplateClause.java`. `@Entity`, `@Table(name = "template_clauses")`. Fields: id (UUID PK), templateId (UUID, `@Column(name = "template_id")`), clauseId (UUID, `@Column(name = "clause_id")`), sortOrder (int default 0), required (boolean default false), createdAt (Instant). Protected no-arg constructor. Public constructor `(UUID templateId, UUID clauseId, int sortOrder, boolean required)`. No `updatedAt` -- records are replaced atomically. Pattern: Join entities in the codebase (e.g., `member/ProjectMember.java`). |
| 188.2 | Create `TemplateClauseRepository` | 188A | 188.1 | New file: `clause/TemplateClauseRepository.java`. `JpaRepository<TemplateClause, UUID>`. Custom queries: `findByTemplateIdOrderBySortOrderAsc(UUID templateId)`, `findByClauseId(UUID clauseId)`, `deleteByTemplateIdAndClauseId(UUID templateId, UUID clauseId)`, `deleteAllByTemplateId(UUID templateId)`, `existsByTemplateIdAndClauseId(UUID templateId, UUID clauseId)`, `Optional<TemplateClause> findMaxSortOrderByTemplateId(UUID templateId)` (or `@Query` for max sortOrder). |
| 188.3 | Create `TemplateClauseDetail` DTO | 188A | | New file: `clause/dto/TemplateClauseDetail.java`. Java record: `UUID id`, `UUID clauseId`, `String title`, `String slug`, `String category`, `String description`, `String bodyPreview` (first 200 chars of body), `boolean required`, `int sortOrder`, `boolean active`. Built from join of `TemplateClause` and `Clause`. |
| 188.4 | Create `TemplateClauseRequest` DTO | 188A | | New file: `clause/dto/TemplateClauseRequest.java`. Java record: `@NotNull UUID clauseId`, `int sortOrder`, `boolean required`. Used for the replace-all `PUT` endpoint. Also create `SetTemplateClausesRequest`: `@NotNull @Valid List<TemplateClauseRequest> clauses`. Also create `AddClauseToTemplateRequest`: `@NotNull UUID clauseId`, `boolean required`. |
| 188.5 | Create `TemplateClauseService` | 188A | 188.2, 188.3 | New file: `clause/TemplateClauseService.java`. `@Service`. Constructor injection: `TemplateClauseRepository`, `ClauseRepository`, `DocumentTemplateRepository`, `AuditService`. Methods: `getTemplateClauses(UUID templateId)` -- load associations, enrich with clause details (body preview = first 200 chars). `setTemplateClauses(UUID templateId, List<TemplateClauseRequest> clauses)` -- validate template exists (404), validate all clause IDs exist and active (400), delete existing, insert new in transaction. `addClauseToTemplate(UUID templateId, UUID clauseId, boolean required)` -- check existing (409), determine next sortOrder, create. `removeClauseFromTemplate(UUID templateId, UUID clauseId)` -- idempotent delete. Publish `template_clause.configured` audit event on set/add/remove. |
| 188.6 | Create `TemplateClauseController` | 188A | 188.5 | New file: `clause/TemplateClauseController.java`. `@RestController`, `@RequestMapping("/api/templates/{templateId}/clauses")`. Endpoints: `GET /` (MEMBER+, returns `List<TemplateClauseDetail>`), `PUT /` (ADMIN/OWNER, replaces full list), `POST /` (ADMIN/OWNER, adds single), `DELETE /{clauseId}` (ADMIN/OWNER, removes). Pattern: `billingrate/BillingRateController.java` for nested resource controller. |
| 188.7 | Write service unit tests | 188A | 188.5 | New file: `clause/TemplateClauseServiceTest.java`. Tests: (1) getTemplateClauses_returns_enriched_list, (2) setTemplateClauses_replaces_all, (3) setTemplateClauses_validates_template_exists_404, (4) setTemplateClauses_validates_clauses_exist_400, (5) addClauseToTemplate_adds_at_end, (6) addClauseToTemplate_duplicate_409, (7) removeClauseFromTemplate_deletes, (8) removeClauseFromTemplate_nonexistent_idempotent. ~8 tests. |
| 188.8 | Write controller integration tests | 188A | 188.6 | New file: `clause/TemplateClauseControllerIntegrationTest.java`. MockMvc tests: (1) GET_returns_template_clauses, (2) PUT_replaces_clause_list, (3) PUT_403_for_member, (4) POST_adds_clause_to_template, (5) POST_409_duplicate, (6) DELETE_removes_clause, (7) GET_empty_for_template_without_clauses. ~7 tests. Pattern: `tax/TaxRateControllerIntegrationTest.java`. |

### Key Files

**Slice 188A -- Create:**
- `backend/src/main/java/.../clause/TemplateClause.java`
- `backend/src/main/java/.../clause/TemplateClauseRepository.java`
- `backend/src/main/java/.../clause/TemplateClauseService.java`
- `backend/src/main/java/.../clause/TemplateClauseController.java`
- `backend/src/main/java/.../clause/dto/TemplateClauseDetail.java`
- `backend/src/main/java/.../clause/dto/TemplateClauseRequest.java`
- `backend/src/test/java/.../clause/TemplateClauseServiceTest.java`
- `backend/src/test/java/.../clause/TemplateClauseControllerIntegrationTest.java`

**Slice 188A -- Read for context:**
- `backend/src/main/java/.../clause/Clause.java` -- entity for enrichment
- `backend/src/main/java/.../clause/ClauseRepository.java` -- clause lookup
- `backend/src/main/java/.../template/DocumentTemplateRepository.java` -- template existence check
- `backend/src/main/java/.../audit/AuditService.java` -- audit event pattern
- `backend/src/main/java/.../member/ProjectMember.java` -- join entity pattern reference

### Architecture Decisions

- **Separate controller (not nested in `DocumentTemplateController`)**: The template-clause association is a distinct concern. Keeping it in `clause/TemplateClauseController.java` follows feature-package convention and avoids bloating the template controller.
- **Replace-all as primary write operation**: `setTemplateClauses` is atomic (delete + insert in transaction). This is simpler than incremental updates and matches the UX (template editor saves the full clause list).
- **No cascade from Clause deactivation**: Deactivating a clause does not remove its `TemplateClause` associations. The clause is hidden from the picker but preserved on existing templates. The template editor shows deactivated clauses with an indicator.

---

## Epic 189: Generation Pipeline Extension

**Goal**: Extend the document generation and preview pipeline to support clauses. This is the core rendering integration -- clause assembly, context injection, snapshot recording, and backward compatibility.

**References**: Architecture doc Sections 3.3 (generation flow), 4.3 (API extensions), 6 (rendering pipeline), ADR-104 (rendering strategy), ADR-105 (snapshot depth), ADR-106 (placeholder strategy).

**Dependencies**: Epics 187 + 188 (Clause and TemplateClause entities/services must exist).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **189A** | 189.1--189.7 | `ClauseAssembler` utility (clause body concatenation with div wrapping) + extend `PdfRenderingService` (inject `clauses`/`clauseCount` into context, add public `renderFragment()` method, template fallback for missing `${clauses}`) + extend `GeneratedDocument` entity with `clauseSnapshots` JSONB field + unit tests for assembler + integration tests for rendering with clauses. ~2 new files, ~2 modified files. Backend only. | |
| **189B** | 189.8--189.14 | Extend `GenerateDocumentRequest` and `PreviewRequest` with optional `clauses` field + clause resolution logic (fallback to template defaults, required clause validation 422) in `GeneratedDocumentService` + snapshot recording + `document.generated_with_clauses` audit event + backward compatibility tests + integration tests. ~0-1 new files, ~3 modified files. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 189.1 | Create `ClauseAssembler` utility | 189A | | New file: `clause/ClauseAssembler.java`. `@Component`. Method: `String assembleClauseBlock(List<Clause> clauses)` -- iterates clauses in order, wraps each body in `<div class="clause-block" data-clause-slug="{slug}">`, concatenates. Returns empty string for empty list. Escapes slug value in `data-clause-slug` attribute (HTML attribute encoding). Pattern: Utility class, stateless. Per architecture doc Section 6.1. |
| 189.2 | Add `renderFragment()` to `PdfRenderingService` | 189A | | Modify: `template/PdfRenderingService.java`. Add new public method: `String renderFragment(String templateContent, Map<String, Object> context)`. Wraps existing private `renderThymeleaf()` logic (or refactors it to be reusable). The fragment is wrapped in a minimal HTML shell for Thymeleaf processing: `<html><body>` + content + `</body></html>`, then strips the wrapper from the output. Used by `ClauseService.previewClause()` (Epic 187B) and clause assembly. |
| 189.3 | Add clause context injection to rendering pipeline | 189A | 189.1, 189.2 | Modify: `template/PdfRenderingService.java`. Extend `generatePdf()` and `previewHtml()` methods (or the shared rendering path) to accept an optional `List<Clause> resolvedClauses` parameter. Before Thymeleaf rendering: (1) Run `TemplateSecurityValidator.validate()` on each clause body (defense in depth). (2) Call `ClauseAssembler.assembleClauseBlock(resolvedClauses)` to get assembled HTML. (3) Inject `clauses` (String) and `clauseCount` (Integer) into the context map. |
| 189.4 | Add template fallback for missing `${clauses}` | 189A | 189.3 | In `PdfRenderingService` rendering path: If clause HTML is non-empty AND template content does not contain `${clauses}`, append a fallback section: `<div class="clauses-section"><hr/><h2>Terms and Conditions</h2><div th:utext="${clauses}"></div></div>`. Per architecture doc Section 6.3 and ADR-106. |
| 189.5 | Add `clauseSnapshots` field to `GeneratedDocument` | 189A | | Modify: `template/GeneratedDocument.java`. Add field: `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "clause_snapshots", columnDefinition = "jsonb") private List<Map<String, Object>> clauseSnapshots;`. Add getter/setter and a `setClauseSnapshots(List<Clause> clauses)` convenience method that builds the `[{clauseId, slug, title, sortOrder}]` structure. Column already exists from V44 migration. Per ADR-105. |
| 189.6 | Write `ClauseAssembler` unit tests | 189A | 189.1 | New file: `clause/ClauseAssemblerTest.java`. Tests: (1) assembleClauseBlock_empty_list_returns_empty, (2) assembleClauseBlock_single_clause_wraps_in_div, (3) assembleClauseBlock_multiple_clauses_preserves_order, (4) assembleClauseBlock_escapes_slug_in_attribute, (5) assembleClauseBlock_includes_clause_body_content. ~5 tests. |
| 189.7 | Write rendering integration tests | 189A | 189.3 | New file: `clause/ClauseRenderingIntegrationTest.java`. Tests: (1) render_with_clauses_includes_clause_content, (2) render_with_clauses_resolves_thymeleaf_variables_in_clause_body, (3) render_without_clauses_backward_compatible, (4) render_with_clauses_fallback_when_no_placeholder, (5) renderFragment_returns_rendered_html, (6) clause_security_validation_at_render_time. ~6 tests. Pattern: Existing rendering tests in `template/` package. |
| 189.8 | Extend `GenerateDocumentRequest` with clauses field | 189B | | Modify: `template/DocumentTemplateController.java`. Add to existing `GenerateDocumentRequest` record: `List<ClauseSelection> clauses` (nullable). Create nested record `ClauseSelection`: `UUID clauseId`, `int sortOrder`. Same extension for `PreviewRequest`. Per architecture doc Section 3.3 and 4.3. |
| 189.9 | Create clause resolution logic | 189B | 189.8 | Modify: `template/GeneratedDocumentService.java` (or create a new `clause/ClauseResolver.java` helper). Resolution logic: (1) If `clauses` param null: load template's default clause list from `TemplateClauseService.getTemplateClauses()`. (2) If `clauses` param provided: validate required clauses present (from `TemplateClauseRepository` where `required = true`), throw 422 if missing. (3) Validate all clause IDs exist via `ClauseRepository.findAllById()`, throw 400 for invalid. (4) Load full `Clause` entities in resolved order. (5) If template has no associations and no clauses param: empty list (backward compatible). |
| 189.10 | Wire clause resolution into `generateDocument()` | 189B | 189.9 | Modify: `template/GeneratedDocumentService.java`. In `generateDocument()`: (1) Resolve clauses via resolution logic. (2) Pass resolved clauses to `PdfRenderingService` (extended in 189A). (3) After generation: set `clauseSnapshots` on `GeneratedDocument` entity. (4) If clauses were included: publish supplementary `document.generated_with_clauses` audit event (template name, clause slugs, clause count). |
| 189.11 | Wire clause resolution into preview endpoint | 189B | 189.9 | Modify: `template/DocumentTemplateController.java` or `PdfRenderingService`. Extend preview flow to accept and resolve clauses using the same resolution logic. Preview renders with clauses included so users see the composed document. |
| 189.12 | Write resolution unit tests | 189B | 189.9 | New file: `clause/ClauseResolverTest.java` (or tests within `GeneratedDocumentServiceTest`). Tests: (1) resolve_null_clauses_uses_template_defaults, (2) resolve_empty_clauses_uses_template_defaults, (3) resolve_explicit_clauses_uses_provided, (4) resolve_missing_required_clause_throws_422, (5) resolve_invalid_clause_id_throws_400, (6) resolve_no_associations_no_param_empty_list. ~6 tests. |
| 189.13 | Write generation with clauses integration tests | 189B | 189.10 | New file: `clause/ClauseGenerationIntegrationTest.java` (or extend existing generation tests). Tests: (1) generate_with_explicit_clauses_includes_content, (2) generate_with_default_clauses_from_template, (3) generate_without_clauses_backward_compatible, (4) generate_missing_required_clause_422, (5) generate_stores_clause_snapshots, (6) preview_with_clauses_returns_composed_html. ~6 tests. |
| 189.14 | Wire audit event for generation with clauses | 189B | 189.10 | In `GeneratedDocumentService`: emit `document.generated_with_clauses` audit event when clauses are included. Details: template name, clause slugs (list), clause count. Augments the existing `document.generated` event (which still fires). When no clauses selected, only the existing event fires. |

### Key Files

**Slice 189A -- Create:**
- `backend/src/main/java/.../clause/ClauseAssembler.java`
- `backend/src/test/java/.../clause/ClauseAssemblerTest.java`
- `backend/src/test/java/.../clause/ClauseRenderingIntegrationTest.java`

**Slice 189A -- Modify:**
- `backend/src/main/java/.../template/PdfRenderingService.java` -- add `renderFragment()`, clause context injection, fallback
- `backend/src/main/java/.../template/GeneratedDocument.java` -- add `clauseSnapshots` JSONB field

**Slice 189B -- Create:**
- `backend/src/test/java/.../clause/ClauseResolverTest.java`
- `backend/src/test/java/.../clause/ClauseGenerationIntegrationTest.java`

**Slice 189B -- Modify:**
- `backend/src/main/java/.../template/DocumentTemplateController.java` -- extend request DTOs
- `backend/src/main/java/.../template/GeneratedDocumentService.java` -- wire resolution + snapshots + audit

**Slice 189A/B -- Read for context:**
- `backend/src/main/java/.../template/TemplateSecurityValidator.java` -- defense in depth validation
- `backend/src/main/java/.../template/TemplateContextBuilder.java` -- context builder interface
- `backend/src/main/java/.../template/ProjectContextBuilder.java` -- example context builder
- `backend/src/main/java/.../clause/TemplateClauseService.java` -- for default clause loading
- `backend/src/main/java/.../clause/TemplateClauseRepository.java` -- for required clause check

### Architecture Decisions

- **ClauseAssembler as separate utility**: Keeps the assembly logic testable in isolation. `PdfRenderingService` delegates to it rather than inlining the concatenation logic.
- **renderFragment() as public method**: Exposes the existing private `renderThymeleaf()` logic for clause preview. The fragment is wrapped in a minimal HTML shell. This enables `ClauseService.previewClause()` to render a single clause body with entity context.
- **Split into 189A (assembler + rendering) and 189B (request extension + resolution)**: 189A is self-contained infrastructure; 189B wires it into the generation flow. This keeps each slice under the 10-file limit and allows 189A to be tested independently of the full generation pipeline.
- **Resolution logic may live in a helper class**: If the resolution logic is complex enough, extract to `clause/ClauseResolver.java` rather than bloating `GeneratedDocumentService`. Decision left to the builder.

---

## Epic 190: Clause Pack Seeder

**Goal**: Create the `ClausePackSeeder`, standard clauses pack JSON with ~12 professional-services clause bodies, default template-clause associations, and integration with tenant provisioning.

**References**: Architecture doc Sections 3.5 (seeder flow), 7 (standard clauses pack), 9.1 (file list).

**Dependencies**: Epics 187 + 188 (Clause and TemplateClause entities must exist).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **190A** | 190.1--190.8 | `ClausePackSeeder` (follows `TemplatePackSeeder` pattern) + `ClausePackDefinition` record + `clause-packs/standard-clauses/pack.json` (~12 clause definitions with HTML bodies using Thymeleaf variables) + default template-clause associations for engagement letter and SOW templates + `OrgSettings.clausePackStatus` field + `recordClausePackApplication()` method + provisioning hook + integration tests (pack loading, idempotency, SYSTEM source, association creation, OrgSettings tracking). ~4 new files, ~2 modified files. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 190.1 | Create `ClausePackDefinition` record | 190A | | New file: `clause/ClausePackDefinition.java`. Java records for JSON deserialization: `ClausePackDefinition(String id, int version, String name, String description, List<ClauseDefinition> clauses, List<TemplateAssociation> templateAssociations)`. Nested records: `ClauseDefinition(String title, String slug, String category, String description, String body, int sortOrder)`, `TemplateAssociation(String templatePackId, String templateKey, List<String> clauseSlugs, List<String> requiredSlugs)`. Pattern: `template/TemplatePackDefinition.java`. |
| 190.2 | Create standard clauses pack JSON | 190A | | New file: `resources/clause-packs/standard-clauses/pack.json`. Contains ~12 clause definitions per architecture doc Section 7.2: Payment Terms, Fee Schedule, Engagement Acceptance, Scope of Work, Client Responsibilities, Limitation of Liability, Professional Indemnity, Confidentiality, Termination, Force Majeure, Dispute Resolution, Document Retention. Each clause body is professional HTML using Thymeleaf variables: `${org.name}`, `${customer.name}`. Template associations for engagement-letter and statement-of-work. ~300-400 lines of JSON. |
| 190.3 | Add `clausePackStatus` to `OrgSettings` | 190A | | Modify: `settings/OrgSettings.java`. Add field: `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "clause_pack_status", columnDefinition = "jsonb") private List<Map<String, Object>> clausePackStatus;`. Add methods: `getClausePackStatus()`, `setClausePackStatus(...)`, `recordClausePackApplication(String packId, int version)` (appends to list). Column already exists from V44 migration. Pattern: Existing `templatePackStatus` field and `recordTemplatePackApplication()` method on same entity. |
| 190.4 | Create `ClausePackSeeder` | 190A | 190.1, 190.3 | New file: `clause/ClausePackSeeder.java`. `@Component`. Constructor injection: `ClauseRepository`, `TemplateClauseRepository`, `DocumentTemplateRepository`, `OrgSettingsService`, `ObjectMapper`, `ResourcePatternResolver`. Method: `seedPacksForTenant(String tenantId)` -- bind ScopedValue for tenant, scan `classpath:clause-packs/*/pack.json`, parse JSON, check `clausePackStatus` for idempotency, create `Clause` entities with `source = SYSTEM`, `packId` set, handle slug collisions (append `-system` suffix). Pattern: `template/TemplatePackSeeder.java` -- follow the exact same resource scanning, JSON parsing, and idempotency pattern. |
| 190.5 | Create default template-clause associations in seeder | 190A | 190.4 | In `ClausePackSeeder`, after seeding clauses: iterate `templateAssociations` from pack definition. For each association: find template by `packId` and slug/key in `document_templates`. Find seeded clauses by `packId` and slug. Create `TemplateClause` records with `required` flag based on `requiredSlugs`, `sortOrder` based on position in `clauseSlugs`. Skip silently if template not found. Per architecture doc Section 7.4. |
| 190.6 | Wire seeder into provisioning flow | 190A | 190.4 | Modify: `provisioning/TenantProvisioningService.java` (or wherever `TemplatePackSeeder` is called). Add call to `ClausePackSeeder.seedPacksForTenant()` **after** `TemplatePackSeeder.seedPacksForTenant()` -- ordering matters because clause-template associations reference templates. Use `@DependsOn` or explicit call ordering. |
| 190.7 | Write seeder integration tests | 190A | 190.4 | New file: `clause/ClausePackSeederIntegrationTest.java`. Tests: (1) seedPacksForTenant_creates_system_clauses, (2) seedPacksForTenant_creates_template_associations, (3) seedPacksForTenant_is_idempotent, (4) seedPacksForTenant_records_pack_in_org_settings, (5) seedPacksForTenant_handles_slug_collision, (6) seedPacksForTenant_skips_missing_templates. ~6 tests. Pattern: `template/TemplatePackSeederIntegrationTest.java` (if exists) or general integration test pattern. |
| 190.8 | Verify clause bodies render with template context | 190A | 190.2 | Add test to integration test file: (7) seeded_clause_bodies_render_with_context -- load a seeded SYSTEM clause, render its body through `PdfRenderingService.renderFragment()` with a sample context containing `org.name` and `customer.name`, verify variables are resolved. ~1 additional test. |

### Key Files

**Slice 190A -- Create:**
- `backend/src/main/java/.../clause/ClausePackDefinition.java`
- `backend/src/main/java/.../clause/ClausePackSeeder.java`
- `backend/src/main/resources/clause-packs/standard-clauses/pack.json`
- `backend/src/test/java/.../clause/ClausePackSeederIntegrationTest.java`

**Slice 190A -- Modify:**
- `backend/src/main/java/.../settings/OrgSettings.java` -- add `clausePackStatus` field
- `backend/src/main/java/.../provisioning/TenantProvisioningService.java` -- add seeder call

**Slice 190A -- Read for context:**
- `backend/src/main/java/.../template/TemplatePackSeeder.java` -- exact pattern to follow
- `backend/src/main/java/.../template/TemplatePackDefinition.java` -- record structure reference
- `backend/src/main/java/.../settings/OrgSettings.java` -- `templatePackStatus` pattern
- `backend/src/main/resources/template-packs/common/` -- pack JSON structure reference

### Architecture Decisions

- **Pack runs after TemplatePackSeeder**: Clause-template associations reference templates that must already exist. Explicit ordering in provisioning flow prevents race conditions.
- **Slug from pack definition (not generated)**: System clauses use the slug from `pack.json` directly, ensuring consistent slugs across all tenants. If a collision occurs (org created a custom clause with the same slug), append `-system` suffix.
- **~12 clauses, professional but generic**: Clause bodies use Thymeleaf variables for org/customer names but avoid jurisdiction-specific legal language. Orgs clone and customize for their needs.

---

## Epic 191: Clause Library Frontend

**Goal**: Build the clause library management page under Settings. Users can view, create, edit, clone, deactivate, and preview clauses.

**References**: Architecture doc Section 9.2 (frontend file list), Requirements doc Sections 6 (clause library management).

**Dependencies**: Epic 187 (backend CRUD API must exist).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **191A** | 191.1--191.6 | `clause-actions.ts` server actions (getClauses, getClause, createClause, updateClause, deleteClause, deactivateClause, cloneClause, getClauseCategories) + `settings/clauses/page.tsx` server component + `clauses-content.tsx` client component (grouped list by category, search, category filter dropdown, source badges, action buttons) + sidebar navigation update. ~4 new files, ~1 modified file. Frontend only. | |
| **191B** | 191.7--191.12 | `clause-form-dialog.tsx` (create/edit dialog with title, category combobox, description textarea, body textarea with monospace font, preview button) + `clause-preview-panel.tsx` (rendered HTML display in sandboxed iframe) + `previewClause` server action + clone confirmation + deactivate confirmation + frontend tests. ~3 new files, ~2 modified files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 191.1 | Create `clause-actions.ts` server actions | 191A | | New file: `lib/actions/clause-actions.ts`. Server actions calling backend API: `getClauses(includeInactive?: boolean, category?: string)`, `getClause(id: string)`, `createClause(data: CreateClauseData)`, `updateClause(id: string, data: UpdateClauseData)`, `deleteClause(id: string)`, `deactivateClause(id: string)`, `cloneClause(id: string)`, `getClauseCategories()`. Use `apiClient` from `lib/api.ts`. Add `revalidatePath` for settings/clauses. TypeScript types: `Clause`, `CreateClauseData`, `UpdateClauseData`. Pattern: `lib/actions/tax-rate-actions.ts` or similar settings actions. |
| 191.2 | Create `settings/clauses/page.tsx` | 191A | 191.1 | New file: `app/(app)/org/[slug]/settings/clauses/page.tsx`. Server component: fetch clauses and categories via server actions, pass to client component. Page title "Clause Library", description text. Pattern: `app/(app)/org/[slug]/settings/templates/page.tsx`. |
| 191.3 | Create `clauses-content.tsx` | 191A | 191.1 | New file: `components/clauses/clauses-content.tsx`. Client component. Features: (1) Search input (filters by title, client-side). (2) Category filter dropdown (from `getClauseCategories()`). (3) Clause list grouped by category with collapsible category headers. (4) Each row: title, category badge, source badge (System/Cloned/Custom), description snippet, active/inactive indicator. (5) Action buttons per row: System clauses show "Clone" only; Custom/Cloned show "Edit", "Deactivate"/"Activate"; unreferenced Custom show "Delete". (6) "New Clause" button at top. Pattern: `components/templates/TemplateList.tsx` for grouped list structure. |
| 191.4 | Update sidebar navigation | 191A | | Modify: `lib/nav-items.ts` (or wherever sidebar navigation items are defined). Add "Clauses" link under Settings section, pointing to `/org/[slug]/settings/clauses`. Position after "Templates". Icon: `FileText` or `ScrollText` from Lucide. |
| 191.5 | Add TypeScript types | 191A | | In `clause-actions.ts` or a separate `lib/types/clause.ts`: define `Clause` type matching `ClauseResponse` DTO, `CreateClauseData`, `UpdateClauseData`. |
| 191.6 | Write list rendering tests | 191A | 191.3 | New test file: `__tests__/clauses/clauses-content.test.tsx`. Tests: (1) renders_clause_list_grouped_by_category, (2) search_filters_by_title, (3) category_filter_filters_list, (4) system_clause_shows_clone_only, (5) custom_clause_shows_edit_deactivate. ~5 tests. Pattern: `__tests__/templates/` test structure. |
| 191.7 | Create `clause-form-dialog.tsx` | 191B | 191.3 | New file: `components/clauses/clause-form-dialog.tsx`. Dialog component with: (1) Title input (text, required). (2) Category combobox -- allows typing new category or selecting existing from `getClauseCategories()`. Use Shadcn Combobox pattern. (3) Description textarea (optional, max 500 chars). (4) Body textarea -- monospace font, generous height (min 200px), HTML/Thymeleaf content. Placeholder text with syntax hints. (5) "Preview" button (calls `previewClause` action, opens preview panel). (6) Save/Cancel buttons. (7) In edit mode: pre-populate fields from existing clause. Pattern: `components/templates/CreateTemplateForm.tsx` for form dialog structure. |
| 191.8 | Create `clause-preview-panel.tsx` | 191B | | New file: `components/clauses/clause-preview-panel.tsx`. Component that displays rendered clause HTML. Uses a sandboxed iframe (`sandbox="allow-same-origin"`) to render the HTML safely. Takes `html: string` prop. Shows loading state while preview is fetching. Pattern: Template preview components in `components/templates/`. |
| 191.9 | Add `previewClause` server action | 191B | 191.1 | Modify: `lib/actions/clause-actions.ts`. Add `previewClause(clauseId: string, entityId: string, entityType: string)` -- calls `POST /api/clauses/{id}/preview`. Returns `{ html: string }`. Note: Preview requires an entity ID and type. The form dialog needs an entity picker or uses a default/sample entity. For v1, use the first available entity of each type as a preview target (or show a dropdown). |
| 191.10 | Add clone confirmation | 191B | 191.3 | In `clauses-content.tsx`: Clone action opens a confirmation toast/dialog ("Clone this clause to create an editable copy?"), then calls `cloneClause` action, refreshes list. Show success toast with new clause title. |
| 191.11 | Add deactivate confirmation | 191B | 191.3 | In `clauses-content.tsx`: Deactivate action opens confirmation dialog ("This clause will be hidden from the clause picker but preserved on existing templates."), calls `deactivateClause` action, refreshes list. Delete action: calls `deleteClause`, handles 409 error (show template count in error toast). |
| 191.12 | Write form and action tests | 191B | 191.7 | New test file: `__tests__/clauses/clause-form-dialog.test.tsx`. Tests: (1) renders_create_form_empty, (2) renders_edit_form_pre_populated, (3) validates_required_fields, (4) category_combobox_shows_existing_categories, (5) preview_button_calls_action, (6) clone_action_creates_copy, (7) deactivate_action_sets_inactive, (8) delete_409_shows_error. ~8 tests. |

### Key Files

**Slice 191A -- Create:**
- `frontend/lib/actions/clause-actions.ts`
- `frontend/app/(app)/org/[slug]/settings/clauses/page.tsx`
- `frontend/components/clauses/clauses-content.tsx`
- `frontend/__tests__/clauses/clauses-content.test.tsx`

**Slice 191A -- Modify:**
- `frontend/lib/nav-items.ts` -- add "Clauses" to settings navigation

**Slice 191B -- Create:**
- `frontend/components/clauses/clause-form-dialog.tsx`
- `frontend/components/clauses/clause-preview-panel.tsx`
- `frontend/__tests__/clauses/clause-form-dialog.test.tsx`

**Slice 191B -- Modify:**
- `frontend/lib/actions/clause-actions.ts` -- add `previewClause` action
- `frontend/components/clauses/clauses-content.tsx` -- wire clone/deactivate/delete confirmations

**Slices 191A/B -- Read for context:**
- `frontend/app/(app)/org/[slug]/settings/templates/page.tsx` -- settings page pattern
- `frontend/components/templates/TemplateList.tsx` -- grouped list pattern
- `frontend/components/templates/CreateTemplateForm.tsx` -- form dialog pattern
- `frontend/lib/actions/template-actions.ts` -- server action pattern
- `frontend/lib/api.ts` -- API client
- `frontend/lib/nav-items.ts` -- navigation structure

### Architecture Decisions

- **Split into 191A (list page) and 191B (form/preview/actions)**: The list page is the foundation; the form dialog and preview panel build on top. Splitting keeps each slice at ~5-6 files.
- **Combobox for category**: Allows both selecting existing categories and typing new ones. Uses Shadcn Combobox component with client-side filtering.
- **Plain textarea for body (not rich-text)**: Per requirements, v1 uses a plain textarea with monospace font. A rich-text/WYSIWYG editor is out of scope.
- **Sandboxed iframe for preview**: Prevents clause HTML from affecting the page. Same pattern used for template preview.

---

## Epic 192: Template Clauses Tab + Generation Dialog Frontend

**Goal**: Add the Clauses tab to the template editor (configure which clauses a template suggests) and extend the generation dialog with a clause selection step.

**References**: Architecture doc Section 9.2 (frontend file list), Requirements doc Sections 7 (template clause config) and 8 (generation dialog).

**Dependencies**: Epics 188 (backend association API), 189 (backend generation pipeline), 191 (frontend clause library).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **192A** | 192.1--192.7 | `template-clause-actions.ts` server actions + `template-clauses-tab.tsx` (ordered clause list with required toggle, reorder, remove, save) + `clause-picker-dialog.tsx` (browse library grouped by category, multi-select, confirm) + template editor integration (add "Clauses" tab) + frontend tests. ~4 new files, ~1 modified file. Frontend only. | |
| **192B** | 192.8--192.14 | `generation-clause-step.tsx` (checklist with required/optional, browse library, reorder, next button) + extend `GenerateDocumentDialog.tsx` (multi-step: clause selection -> preview -> generate, skip when no associations) + extend `generateDocumentAction` and `previewTemplateAction` with clause support + frontend tests. ~2 new files, ~3 modified files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 192.1 | Create `template-clause-actions.ts` server actions | 192A | | New file: `lib/actions/template-clause-actions.ts`. Server actions: `getTemplateClauses(templateId: string)`, `setTemplateClauses(templateId: string, clauses: TemplateClauseConfig[])`, `addClauseToTemplate(templateId: string, clauseId: string, required: boolean)`, `removeClauseFromTemplate(templateId: string, clauseId: string)`. TypeScript types: `TemplateClauseDetail`, `TemplateClauseConfig`. Pattern: `lib/actions/template-actions.ts`. |
| 192.2 | Create `template-clauses-tab.tsx` | 192A | 192.1 | New file: `components/templates/template-clauses-tab.tsx`. Client component. Features: (1) Load template's clause list via `getTemplateClauses()`. (2) Ordered list showing: clause title, category badge, required toggle (Switch component), reorder up/down buttons, remove (X) button. (3) "Add Clause" button (opens clause picker dialog). (4) "Save" button -- calls `setTemplateClauses()` with full list. (5) Unsaved changes indicator. (6) Empty state: "No clauses configured. Add clauses to suggest default terms when generating documents from this template." Pattern: Similar to template editor tab components. |
| 192.3 | Create `clause-picker-dialog.tsx` | 192A | 192.1 | New file: `components/templates/clause-picker-dialog.tsx`. Dialog component. Features: (1) Load all active clauses via `getClauses()` (from `clause-actions.ts`). (2) Group by category with collapsible headers. (3) Each clause: checkbox, title, category badge, description snippet. (4) Already-associated clauses shown as disabled/checked. (5) Search input for filtering. (6) "Add Selected" and "Cancel" buttons. (7) Returns selected clause IDs to parent. Pattern: Multi-select dialog similar to member picker patterns. |
| 192.4 | Integrate Clauses tab into template editor | 192A | 192.2 | Modify: `components/templates/TemplateEditor.tsx` (or `TemplateEditorForm.tsx`). Add "Clauses" tab alongside existing Content/CSS/Settings tabs. Tab renders `template-clauses-tab.tsx` with the template ID. |
| 192.5 | Add reorder logic | 192A | 192.2 | In `template-clauses-tab.tsx`: up/down buttons swap `sortOrder` values between adjacent items. State managed locally until Save is clicked. Can also implement drag-and-drop in future, but up/down buttons are sufficient for v1. |
| 192.6 | Write template clauses tab tests | 192A | 192.2 | New test file: `__tests__/templates/template-clauses-tab.test.tsx`. Tests: (1) renders_clause_list, (2) required_toggle_updates_state, (3) remove_button_removes_clause, (4) add_clause_opens_picker, (5) save_calls_set_action. ~5 tests. |
| 192.7 | Write clause picker dialog tests | 192A | 192.3 | New test file: `__tests__/templates/clause-picker-dialog.test.tsx`. Tests: (1) renders_clauses_grouped_by_category, (2) already_associated_shown_disabled, (3) search_filters_clauses, (4) add_selected_returns_ids. ~4 tests. |
| 192.8 | Create `generation-clause-step.tsx` | 192B | | New file: `components/templates/generation-clause-step.tsx`. Client component (Step 1 of generation dialog). Features: (1) Load template's clause list via `getTemplateClauses()`. (2) Checklist: required clauses checked + disabled, optional clauses checked by default + toggleable. Each row: checkbox, title, category badge, short description. (3) "Browse Library" button at bottom: opens secondary picker (reuse `clause-picker-dialog.tsx`) showing all active org clauses not in the list. (4) Reorder controls (up/down). (5) "Next: Preview" button -- passes selected clause IDs and order to parent. (6) State: `selectedClauses: {clauseId, sortOrder, required}[]`. |
| 192.9 | Extend `GenerateDocumentDialog.tsx` with multi-step flow | 192B | 192.8 | Modify: `components/templates/GenerateDocumentDialog.tsx`. Changes: (1) On dialog open: check if template has `TemplateClause` associations (via `getTemplateClauses()`). (2) If yes: show Step 1 (clause selection via `generation-clause-step.tsx`), then Step 2 (preview), then Step 3 (generate). (3) If no associations: skip Step 1, show existing preview/generate flow (backward compatible). (4) Step indicator (e.g., "Step 1 of 3" or breadcrumb-style). (5) Back button from preview returns to clause selection with selections preserved. (6) State management: `selectedClauses` persists across step navigation. |
| 192.10 | Extend `generateDocumentAction` with clauses | 192B | | Modify: `lib/actions/template-actions.ts`. Extend `generateDocumentAction(templateId, entityId, options)` to accept optional `clauses: {clauseId: string, sortOrder: number}[]`. Pass in the API request body. |
| 192.11 | Extend `previewTemplateAction` with clauses | 192B | | Modify: `lib/actions/template-actions.ts`. Extend `previewTemplateAction(templateId, entityId, options)` to accept optional `clauses` parameter. Preview request includes selected clauses so the composed document is shown. |
| 192.12 | Wire clause selection into preview step | 192B | 192.9, 192.11 | In `GenerateDocumentDialog.tsx`: When the user clicks "Next: Preview" from clause selection step, call `previewTemplateAction` with the selected clauses. The preview shows the full composed document with clauses rendered inline. |
| 192.13 | Wire clause selection into generate step | 192B | 192.9, 192.10 | In `GenerateDocumentDialog.tsx`: When the user clicks "Generate" or "Download PDF", call `generateDocumentAction` with the selected clauses. If generation succeeds, show success state as before. |
| 192.14 | Write generation dialog tests | 192B | 192.9 | New test file: `__tests__/templates/generation-clause-step.test.tsx`. Tests: (1) clause_step_shown_when_template_has_clauses, (2) clause_step_hidden_when_no_clauses, (3) required_clauses_cannot_be_unchecked, (4) optional_clauses_toggleable, (5) browse_library_adds_extra_clauses, (6) back_button_preserves_selections, (7) preview_includes_clauses, (8) generate_includes_clauses, (9) backward_compat_no_clause_step. ~9 tests. |

### Key Files

**Slice 192A -- Create:**
- `frontend/lib/actions/template-clause-actions.ts`
- `frontend/components/templates/template-clauses-tab.tsx`
- `frontend/components/templates/clause-picker-dialog.tsx`
- `frontend/__tests__/templates/template-clauses-tab.test.tsx`
- `frontend/__tests__/templates/clause-picker-dialog.test.tsx`

**Slice 192A -- Modify:**
- `frontend/components/templates/TemplateEditor.tsx` (or `TemplateEditorForm.tsx`) -- add Clauses tab

**Slice 192B -- Create:**
- `frontend/components/templates/generation-clause-step.tsx`
- `frontend/__tests__/templates/generation-clause-step.test.tsx`

**Slice 192B -- Modify:**
- `frontend/components/templates/GenerateDocumentDialog.tsx` -- multi-step flow
- `frontend/lib/actions/template-actions.ts` -- extend generate/preview actions with clauses

**Slices 192A/B -- Read for context:**
- `frontend/components/templates/TemplateEditor.tsx` -- existing tab structure
- `frontend/components/templates/TemplateEditorForm.tsx` -- editor form pattern
- `frontend/components/templates/GenerateDocumentDialog.tsx` -- current generation flow
- `frontend/components/templates/GenerateDocumentDropdown.tsx` -- dropdown trigger
- `frontend/lib/actions/template-actions.ts` -- existing generate/preview actions
- `frontend/components/clauses/clauses-content.tsx` -- clause list rendering (from 191A)
- `frontend/components/clauses/clause-picker-dialog.tsx` -- reuse pattern (if applicable)

### Architecture Decisions

- **Split into 192A (template tab) and 192B (generation dialog)**: The template clauses tab is a standalone feature; the generation dialog extension is the culmination that ties everything together. Splitting prevents the builder from having to understand both the template editor and the generation dialog simultaneously.
- **Reuse clause-picker-dialog in generation step**: The "Browse Library" button in the generation dialog opens the same picker as the template editor's "Add Clause" button. The picker component is parameterized to exclude already-selected clauses.
- **Multi-step dialog (not modal-within-modal)**: Per requirements, the clause selection is a clear step, not a nested dialog. Step indicator guides the user: Clause Selection -> Preview -> Generate.
- **Backward compatibility as first test**: The no-clause fast path (skip Step 1 when template has no associations) is critical. Tests explicitly verify this.

---

## Summary of Estimated Test Counts

| Epic | Slice | Backend Tests | Frontend Tests | Total |
|------|-------|---------------|----------------|-------|
| 187 | 187A | ~12 (5 unit + 7 repo) | -- | 12 |
| 187 | 187B | ~24 (12 unit + 12 integration) | -- | 24 |
| 188 | 188A | ~15 (8 unit + 7 integration) | -- | 15 |
| 189 | 189A | ~11 (5 assembler + 6 rendering) | -- | 11 |
| 189 | 189B | ~12 (6 resolver + 6 generation) | -- | 12 |
| 190 | 190A | ~7 | -- | 7 |
| 191 | 191A | -- | ~5 | 5 |
| 191 | 191B | -- | ~8 | 8 |
| 192 | 192A | -- | ~9 | 9 |
| 192 | 192B | -- | ~9 | 9 |
| **Total** | | **~81** | **~31** | **~112** |

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java` - Core rendering pipeline to extend with clause assembly and context injection (Epic 189)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java` - Exact pattern for ClausePackSeeder (Epic 190)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java` - Generation flow to extend with clause resolution and snapshot recording (Epic 189B)
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/templates/GenerateDocumentDialog.tsx` - Generation dialog to extend with clause selection step (Epic 192B)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/tax/TaxRateService.java` - CRUD service pattern to follow for ClauseService (Epic 187B)