# Phase 41 -- Organisation Roles & Capability-Based Permissions

Phase 41 replaces the coarse three-role authorization model (Owner/Admin/Member) with a **capability-based permission system**. Firm administrators can define custom roles with 7 curated capability toggles, assign them to team members, and optionally override individual capabilities per user. Capabilities are resolved entirely in the application layer from the tenant database -- no IDP or gateway changes. Existing `@PreAuthorize` annotations on ~70 admin-gated endpoints are migrated to `@RequiresCapability`. The frontend gains a `CapabilityProvider` context, sidebar gating, and a Settings > Roles & Permissions management page.

**Architecture doc**: `architecture/phase41-org-roles-capabilities.md`

**Dependencies on prior phases**:
- Phase 36 (Auth): Keycloak JWT roles, `MemberFilter`, `RequestScopes`, `MemberSyncFilter`
- Phase 13 (Dedicated Schema): Schema-per-tenant, no `@Filter`/`@FilterDef` needed
- Phase 6 (Audit): `AuditService`, `AuditEventBuilder`
- Phase 6.5 (Notifications): `NotificationService`
- All feature phases with `@PreAuthorize` annotations: 4, 5, 8, 10, 12, 14, 37, 38, 40

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 312 | OrgRole Entity Foundation & Migration | Backend | -- | M | 312A, 312B | **Done** (PRs #636, #637) |
| 313 | Capability Resolution & Authorization Infrastructure | Backend | 312 | M | 313A, 313B | **Done** (PRs #638, #639) |
| 314 | @PreAuthorize Migration (Batch 1: Financial, Invoicing, Project) | Backend | 313 | M | 314A, 314B | **Done** (PRs #640, #641) |
| 315 | @PreAuthorize Migration (Batch 2: Customer, Automation, Resource, Team) | Backend | 313 | M | 315A, 315B | **Done** (PRs #642, #643) |
| 316 | OrgRole CRUD API & Member Role Assignment | Backend | 312 | M | 316A, 316B | **Done** (PRs #644, #645) |
| 317 | Audit Events & Notifications | Backend | 316 | S | 317A | **Done** (PR #646) |
| 318 | Frontend Capability Context & Sidebar Gating | Frontend | 313 | M | 318A, 318B | **Done** (PRs #647, #648) |
| 319 | Settings: Roles & Permissions Page | Frontend | 316, 318 | M | 319A, 319B | **Done** (PRs #649, #650) |
| 320 | Team Page: Member Role Management & Invite Extension | Frontend | 316, 318 | M | 320A, 320B | |

---

## Dependency Graph

```
BACKEND TRACK
─────────────────────────────────────────────────────

[E312A OrgRole entity,
 Capability enum,
 join tables,
 V64 migration,
 Member extension]
        |
[E312B OrgRole repository,
 OrgRole service (CRUD shell),
 capability resolution
 algorithm, DTO records,
 system role seed SQL]
        |
        +─────────────────────────────────────+
        |                                     |
[E313A RequestScopes.CAPABILITIES,          [E316A OrgRole CRUD
 CapabilityAuthorizationService,              endpoints (list, get,
 @RequiresCapability annotation,              create, update, delete),
 CapabilityAuthorizationManager,              OrgRoleController,
 MemberFilter extension                       validation rules,
 + integration tests]                         + integration tests]
        |                                     |
[E313B /api/me/capabilities                [E316B Member role
 endpoint, member capabilities               assignment endpoint
 endpoint, capability                        PUT /api/members/{id}/role,
 resolution edge cases                       GET /api/members/{id}/capabilities,
 + integration tests]                        invite flow extension,
        |                                    + integration tests]
        +───────────────+                     |
        |               |                  [E317A Audit events
[E314A Migration       [E315A Migration      (ROLE_CREATED, UPDATED,
 batch 1: invoice/,     batch 2: customer/,  DELETED, MEMBER_ROLE_CHANGED),
 billingrun/,           compliance/,         notifications on
 costrate/, report/,    retention/,          capability changes
 expense/ (approve)     datarequest/,        + integration tests]
 ~25 annotations        checklist/,
 + test updates]        informationrequest/,
        |               acceptance/
[E314B Migration         ~20 annotations
 batch 1b: project/,    + test updates]
 projecttemplate/,            |
 schedule/,             [E315B Migration
 proposal/                batch 2b: automation/,
 ~15 annotations          capacity/ (writes),
 + test updates]          adminTimeEntry/
                          ~15 annotations
                          + test updates]

FRONTEND TRACK (after E313 backend APIs ready)
───────────────────────────────────────────────

[E318A lib/api/capabilities.ts,
 CapabilityProvider context,
 RequiresCapability wrapper,
 /api/me/capabilities fetch
 + tests]
        |
[E318B Sidebar nav gating,
 page-level protection
 (notFound for missing caps),
 component-level gating
 (action buttons hidden)
 + tests]
        |
        +─────────────────────────────────────+
        |                                     |
[E319A Settings > Roles                    [E320A Team page: role
 & Permissions page,                         column, member detail
 system roles section,                       panel with role dropdown,
 custom role cards,                          capability overrides UI
 capability reference                        + tests]
 + tests]                                     |
        |                                  [E320B Invite form:
[E319B Create/Edit role                      role dropdown with
 dialog, delete role                         custom roles, capability
 confirmation, capability                    summary preview
 toggles                                     + tests]
 + tests]
```

**Parallel opportunities**:
- E312A/B are sequential (entity before service).
- E313 and E316 can run in parallel after E312B (independent backend domains).
- E314 and E315 can run in parallel after E313 (independent annotation batches).
- E317A depends only on E316B.
- E318A/B are sequential (context before gating).
- E319 and E320 can run in parallel after E318B (independent frontend pages).

---

## Implementation Order

### Stage 0: Entity Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 312 | 312A | `OrgRole` entity, `Capability` enum, `Member` extension (org_role_id + overrides), V67 migration (3 tables + 1 ALTER + system role seed). ~4 new/modified files. Backend only. | **Done** (PR #636) |
| 0b | 312 | 312B | `OrgRoleRepository`, `OrgRoleService` (resolution algorithm + CRUD shell), DTO records, slug generation utility. ~6 new files (~8 tests). Backend only. | **Done** (PR #637) |

### Stage 1: Authorization Infrastructure & OrgRole CRUD (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 313 | 313A | `RequestScopes.CAPABILITIES` ScopedValue, `CapabilityAuthorizationService`, `@RequiresCapability` annotation, `CapabilityAuthorizationManager`, `MemberFilter` extension to resolve + bind capabilities. ~7 new/modified files (~10 tests). Backend only. | **Done** (PR #638) |
| 1b | 313 | 313B | `GET /api/me/capabilities` endpoint, `GET /api/members/{id}/capabilities` endpoint, edge cases (unassigned role, empty overrides, admin bypass). ~3 new/modified files (~8 tests). Backend only. | **Done** (PR #639) |
| 1c (parallel with 1a) | 316 | 316A | `OrgRoleController` with 5 CRUD endpoints (list, get, create, update, delete), validation rules (name uniqueness, system role guard, delete-with-members guard). ~3 new/modified files (~12 tests). Backend only. | **Done** (PR #644) |
| 1d | 316 | 316B | `PUT /api/members/{id}/role` endpoint, `GET /api/members/{id}/capabilities`, override validation, invite flow extension (`MemberFilter.lazyCreateMember` with orgRoleId). ~4 modified files (~10 tests). Backend only. | **Done** (PR #645) |

### Stage 2: @PreAuthorize Migration (parallel batches)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 314 | 314A | Migrate `@PreAuthorize` to `@RequiresCapability` on: `InvoiceController` (19), `BillingRunController` (15), `CostRateController` (4), `ReportController` profitability endpoints (2), `ExpenseController` approve (2). ~5 modified controller files + test updates (~10 test adjustments). Backend only. | **Done** (PR #640) |
| 2b (parallel) | 314 | 314B | Migrate `@PreAuthorize` to `@RequiresCapability` on: `ProjectController` creates/edits (5), `ProjectTemplateController` writes (4), `RecurringScheduleController` writes (4), `ProposalController` writes (3). ~4 modified controller files + test updates (~8 test adjustments). Backend only. | **Done** (PR #641) |
| 2c (parallel) | 315 | 315A | Migrate `@PreAuthorize` to `@RequiresCapability` on: `CustomerController` (8), `CompliancePackController` (1), `RetentionController` (6), `DataRequestController` (8), `ChecklistTemplateController` writes (3), `ChecklistInstanceController` writes (3), `InformationRequestController` admin writes (5), `RequestTemplateController` writes (3), `AcceptanceController` admin (3). ~9 modified controller files + test updates (~10 test adjustments). Backend only. | **Done** (PR #642) |
| 2d (parallel) | 315 | 315B | Migrate `@PreAuthorize` to `@RequiresCapability` on: `AutomationRuleController` (8), `AutomationActionController` (4), `AutomationExecutionController` (3), `AutomationTemplateController` (2), `MemberCapacityController` (4), `ResourceAllocationController` writes (3), `AdminTimeEntryController` (1). ~7 modified controller files + test updates (~8 test adjustments). Backend only. | **Done** (PR #643) |

### Stage 3: Audit & Notifications

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 317 | 317A | 4 audit event types (`ROLE_CREATED`, `ROLE_UPDATED`, `ROLE_DELETED`, `MEMBER_ROLE_CHANGED`), `AuditEventBuilder` additions, capability-change notification via `NotificationService`. ~3 modified files (~6 tests). Backend only. | **Done** (PR #646) |

### Stage 4: Frontend Capability Context

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a | 318 | 318A | `lib/api/capabilities.ts` API client, `lib/capabilities.tsx` `CapabilityProvider` context, `RequiresCapability` wrapper component, integration into org layout. ~5 new/modified files (~6 tests). Frontend only. | **Done** (PR #647) |
| 4b | 318 | 318B | Sidebar nav gating in `nav-items.ts` + `desktop-sidebar.tsx`, page-level protection on 6 gated routes (invoices, customers, profitability, resources, compliance, settings/automations), action button gating (Create Project, Generate Invoice, Approve time). ~8 modified files (~6 tests). Frontend only. | **Done** (PR #648) |

### Stage 5: Settings & Team Pages (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 319 | 319A | Settings > Roles & Permissions page, `lib/api/org-roles.ts` API client, system roles section (read-only), custom role cards with capability pills + member count, capability reference section, settings hub card. ~6 new/modified files (~5 tests). Frontend only. | **Done** (PR #649) |
| 5b | 319 | 319B | Create/Edit role dialog with name, description, and 7 capability checkboxes. Delete role confirmation dialog. Server actions for CRUD. ~4 new files (~5 tests). Frontend only. | **Done** (PR #650) |
| 5c (parallel with 5a) | 320 | 320A | Team page: role name badge column, override indicator, member detail panel with role dropdown and capability toggle overrides. Server actions for role assignment. ~5 modified/new files (~5 tests). Frontend only. | **Done** (PR #651) |
| 5d | 320 | 320B | Invite form: role dropdown showing system + custom roles, capability summary preview, "Customize for this user" overrides section. ~3 modified files (~4 tests). Frontend only. | |

### Timeline

```
Stage 0: [312A] -> [312B]                                              (sequential)
Stage 1: [313A] // [316A] -> [313B] -> [316B]                          (313 + 316 parallel start, then sequential)
Stage 2: [314A] // [314B] // [315A] // [315B]                          (all 4 parallel after 313A)
Stage 3: [317A]                                                        (after 316B)
Stage 4: [318A] -> [318B]                                              (sequential, after 313B APIs exist)
Stage 5: [319A] -> [319B] // [320A] -> [320B]                         (two parallel tracks after 318B)
```

**Critical path**: 312A -> 312B -> 313A -> 313B -> 318A -> 318B -> 319A -> 319B (8 slices sequential).

**Fastest path with parallelism**: 16 slices total, 8 on critical path. Stage 2 (4 slices) runs fully parallel.

---

## Epic 312: OrgRole Entity Foundation & Migration

**Goal**: Create the `OrgRole` entity, `Capability` enum, extend the `Member` entity with `org_role_id` and capability overrides, and write the V64 Flyway migration that creates the new tables and seeds system roles.

**References**: Architecture doc Sections 41.2, 41.6, 41.7.

**Dependencies**: None -- greenfield entity in tenant schema.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **312A** | 312.1--312.6 | `OrgRole` entity, `Capability` enum, `Member` entity extension (org_role_id FK + capability overrides `@ElementCollection`), V67 tenant migration (3 new tables: `org_roles`, `org_role_capabilities`, `member_capability_overrides` + ALTER members + system role seed data). ~4 new/modified files. Backend only. | **Done** (PR #636) |
| **312B** | 312.7--312.14 | `OrgRoleRepository`, `OrgRoleService` (capability resolution algorithm + CRUD shell methods), DTO records (`OrgRoleResponse`, `CreateOrgRoleRequest`, `UpdateOrgRoleRequest`, `AssignRoleRequest`, `MemberCapabilitiesResponse`), slug generation utility, existing member backfill data migration in V64. ~6 new files + 1 test file (~8 tests). Backend only. | **Done** (PR #637) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 312.1 | Create `Capability` enum | 312A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java`. Values: `FINANCIAL_VISIBILITY`, `INVOICING`, `PROJECT_MANAGEMENT`, `TEAM_OVERSIGHT`, `CUSTOMER_MANAGEMENT`, `AUTOMATIONS`, `RESOURCE_PLANNING`. Include a static `Set<String> ALL_NAMES` field and a `fromString(String)` validation method. Pattern: `backend/.../billingrun/BillingRunStatus.java` for enum conventions. |
| 312.2 | Create `OrgRole` entity | 312A | 312.1 | New file: `backend/.../orgrole/OrgRole.java`. JPA entity mapped to `org_roles` table. Fields: `id` (UUID PK, `GenerationType.UUID`), `name` (String, max 100, not null), `slug` (String, max 100, not null, unique per tenant), `description` (String, max 500, nullable), `isSystem` (boolean, not null, default false), `createdAt` (Instant), `updatedAt` (Instant). Capabilities via `@ElementCollection @CollectionTable(name = "org_role_capabilities", joinColumns = @JoinColumn(name = "org_role_id")) @Column(name = "capability") private Set<String> capabilities = new HashSet<>()`. `@PrePersist`/`@PreUpdate` for timestamps. Pattern: `backend/.../invoice/Invoice.java` for entity conventions. |
| 312.3 | Extend `Member` entity with `org_role_id` | 312A | 312.2 | Modify: `backend/.../member/Member.java`. Add `@Column(name = "org_role_id") private UUID orgRoleId;` (nullable initially -- migration will backfill). Add getter/setter. No JPA relationship (UUID FK field only, matching project conventions). |
| 312.4 | Extend `Member` entity with capability overrides | 312A | 312.3 | Modify: `backend/.../member/Member.java`. Add `@ElementCollection @CollectionTable(name = "member_capability_overrides", joinColumns = @JoinColumn(name = "member_id")) @Column(name = "override_value") private Set<String> capabilityOverrides = new HashSet<>()`. Add getter/setter. Override values are `+CAPABILITY` or `-CAPABILITY` prefixed strings per ADR-163. |
| 312.5 | Create V64 tenant migration | 312A | | New file: `backend/src/main/resources/db/migration/tenant/V64__create_org_role_tables.sql`. DDL: (1) CREATE TABLE `org_roles` (id UUID PK, name VARCHAR(100) NOT NULL, slug VARCHAR(100) NOT NULL, description VARCHAR(500), is_system BOOLEAN NOT NULL DEFAULT false, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, UNIQUE(slug)). (2) CREATE TABLE `org_role_capabilities` (org_role_id UUID NOT NULL REFERENCES org_roles(id) ON DELETE CASCADE, capability VARCHAR(50) NOT NULL, PRIMARY KEY(org_role_id, capability)). (3) CREATE TABLE `member_capability_overrides` (member_id UUID NOT NULL REFERENCES members(id) ON DELETE CASCADE, override_value VARCHAR(60) NOT NULL, PRIMARY KEY(member_id, override_value)). (4) ALTER TABLE members ADD COLUMN org_role_id UUID REFERENCES org_roles(id). (5) INSERT system roles (OWNER with all 7 caps, ADMIN with all 7 caps, MEMBER with empty caps). (6) UPDATE members SET org_role_id = (SELECT id FROM org_roles WHERE slug = CASE WHEN org_role = 'owner' THEN 'owner' WHEN org_role = 'admin' THEN 'admin' ELSE 'member' END). (7) ALTER TABLE members ALTER COLUMN org_role_id SET NOT NULL. Pattern: `backend/.../db/migration/tenant/V63__create_billing_run_tables.sql` for version numbering. |
| 312.6 | Add indexes to V64 migration | 312A | 312.5 | Part of V64 file: CREATE INDEX `idx_members_org_role_id` ON members(org_role_id). CREATE INDEX `idx_org_roles_slug` ON org_roles(slug). CREATE INDEX `idx_org_roles_is_system` ON org_roles(is_system). |
| 312.7 | Create `OrgRoleRepository` | 312B | 312A | New file: `backend/.../orgrole/OrgRoleRepository.java`. Extends `JpaRepository<OrgRole, UUID>`. Methods: `findBySlug(String slug)`, `findByIsSystem(boolean isSystem)`, `existsByNameIgnoreCase(String name)`, `existsBySlugAndIdNot(String slug, UUID id)`. Pattern: `backend/.../invoice/InvoiceRepository.java`. |
| 312.8 | Create slug generation utility | 312B | | Add to `OrgRoleService` (or as a static utility method). Converts role name to kebab-case slug: lowercase, replace spaces/special chars with `-`, strip consecutive `-`, strip leading/trailing `-`. E.g., "Project Manager" -> "project-manager", "Senior Associate (Level 2)" -> "senior-associate-level-2". |
| 312.9 | Create `OrgRoleService` with capability resolution | 312B | 312.7 | New file: `backend/.../orgrole/OrgRoleService.java`. `@Service`. Constructor injects `OrgRoleRepository`, `MemberRepository`. Core method: `resolveCapabilities(UUID memberId)` -- fetches member's OrgRole (eagerly loads capabilities), fetches member's overrides, applies resolution algorithm (base caps + additions - removals). For system roles with slug `"owner"` or `"admin"`, returns `Set.of("ALL")`. For `"member"` system role, returns empty set + overrides. Pattern: `backend/.../invoice/InvoiceService.java` for service structure. |
| 312.10 | Add CRUD shell methods to `OrgRoleService` | 312B | 312.9 | Add to `backend/.../orgrole/OrgRoleService.java`: `listRoles()`, `getRole(UUID id)`, `createRole(CreateOrgRoleRequest)`, `updateRole(UUID id, UpdateOrgRoleRequest)`, `deleteRole(UUID id)`. Validate: name uniqueness, capabilities subset of enum, system role guards. Slug auto-generated from name. |
| 312.11 | Create DTO records | 312B | | New file: `backend/.../orgrole/dto/OrgRoleDtos.java`. Records: `CreateOrgRoleRequest(String name, String description, Set<String> capabilities)`, `UpdateOrgRoleRequest(String name, String description, Set<String> capabilities)`, `OrgRoleResponse(UUID id, String name, String slug, String description, Set<String> capabilities, boolean isSystem, long memberCount, Instant createdAt, Instant updatedAt)`, `AssignRoleRequest(UUID orgRoleId, Set<String> capabilityOverrides)`, `MemberCapabilitiesResponse(UUID memberId, String roleName, Set<String> roleCapabilities, Set<String> overrides, Set<String> effectiveCapabilities)`, `MyCapabilitiesResponse(Set<String> capabilities, String role, boolean isAdmin, boolean isOwner)`. Pattern: `backend/.../billingrun/dto/BillingRunDtos.java`. |
| 312.12 | Add `countByOrgRoleId` to `MemberRepository` | 312B | | Modify: `backend/.../member/MemberRepository.java`. Add `long countByOrgRoleId(UUID orgRoleId)`. Used by `OrgRoleService` to include `memberCount` in responses and to guard deletion. |
| 312.13 | Add `findByOrgRoleId` to `MemberRepository` | 312B | | Modify: `backend/.../member/MemberRepository.java`. Add `List<Member> findByOrgRoleId(UUID orgRoleId)`. Used for cascading role updates to members (notification on capability change). |
| 312.14 | Write integration tests for OrgRoleService | 312B | 312.10 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleServiceTest.java`. Tests (~8): (1) `resolveCapabilities_adminRole_returnsAll`; (2) `resolveCapabilities_memberRole_returnsEmpty`; (3) `resolveCapabilities_customRole_returnsRoleCaps`; (4) `resolveCapabilities_withAddOverride_addsCapability`; (5) `resolveCapabilities_withRemoveOverride_removesCapability`; (6) `createRole_validRequest_setsSlug`; (7) `createRole_duplicateName_throws`; (8) `deleteRole_withMembers_throws`. Use `@SpringBootTest @Import(TestcontainersConfiguration.class)`. |

### Key Files

**Slice 312A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRole.java`
- `backend/src/main/resources/db/migration/tenant/V64__create_org_role_tables.sql`

**Slice 312A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/Member.java` -- add `orgRoleId` + `capabilityOverrides`

**Slice 312A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` -- entity pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunStatus.java` -- enum pattern
- `backend/src/main/resources/db/migration/tenant/V63__create_billing_run_tables.sql` -- latest migration

**Slice 312B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/dto/OrgRoleDtos.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleServiceTest.java`

**Slice 312B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberRepository.java` -- add `countByOrgRoleId`, `findByOrgRoleId`

**Slice 312B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceRepository.java` -- repository pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- service pattern

### Architecture Decisions

- **Tenant-scoped entity**: `OrgRole` lives in the tenant schema. No `@FilterDef`/`@Filter` needed (dedicated schema handles isolation per Phase 13).
- **UUID FK field (not JPA relationship)**: `Member.orgRoleId` is a raw UUID, not `@ManyToOne`. Consistent with all other FK fields in the codebase.
- **`@ElementCollection` for capabilities and overrides**: Maps directly to join tables without requiring separate entity classes. Hibernate manages lifecycle automatically.
- **Single migration file V64**: Contains table creation, system role seed, member backfill, and NOT NULL enforcement. Atomic deployment -- partial migration would leave schema inconsistent.
- **System role seed in migration SQL**: The 3 system roles (OWNER, ADMIN, MEMBER) are seeded via INSERT in the Flyway migration, not by application code. This ensures every tenant schema has them before the application reads.

---

## Epic 313: Capability Resolution & Authorization Infrastructure

**Goal**: Wire capability resolution into the request lifecycle (MemberFilter), create the `@RequiresCapability` annotation with its `AuthorizationManager`, add `RequestScopes.CAPABILITIES`, and expose the `/api/me/capabilities` endpoint. This epic provides the infrastructure all subsequent authorization changes depend on.

**References**: Architecture doc Sections 41.3, 41.5.3.

**Dependencies**: Epic 312 (OrgRole entity, service, resolution algorithm).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **313A** | 313.1--313.8 | Add `CAPABILITIES` ScopedValue to `RequestScopes`, `CapabilityAuthorizationService`, `@RequiresCapability` annotation, `CapabilityAuthorizationManager` (Spring Security `AuthorizationManager<MethodInvocation>`), extend `MemberFilter.doFilterInternal` to resolve + bind capabilities, register manager in `SecurityConfig`. ~7 new/modified files (~10 tests). Backend only. | **Done** (PR #638) |
| **313B** | 313.9--313.14 | `GET /api/me/capabilities` endpoint on new `CapabilityController`, `GET /api/members/{id}/capabilities` on `OrgMemberController`, edge case tests (unassigned role, empty overrides, admin bypass, member self-access). ~3 new/modified files (~8 tests). Backend only. | **Done** (PR #639) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 313.1 | Add `CAPABILITIES` ScopedValue to `RequestScopes` | 313A | 312B | Modify: `backend/.../multitenancy/RequestScopes.java`. Add `public static final ScopedValue<Set<String>> CAPABILITIES = ScopedValue.newInstance()`. Add `getCapabilities()` (returns bound set or empty), `hasCapability(String)` (checks set contains capability or "ALL"). |
| 313.2 | Create `@RequiresCapability` annotation | 313A | | New file: `backend/.../orgrole/RequiresCapability.java`. `@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)`. Single `String value()` attribute. |
| 313.3 | Create `CapabilityAuthorizationManager` | 313A | 313.2 | New file: `backend/.../orgrole/CapabilityAuthorizationManager.java`. Implements `AuthorizationManager<MethodInvocation>`. `check()` method: read `@RequiresCapability` annotation from method, if absent return `null` (abstain), verify authentication is present, delegate to `RequestScopes.hasCapability(annotation.value())`. Pattern: See architecture doc Section 41.3.5. |
| 313.4 | Register `CapabilityAuthorizationManager` in security config | 313A | 313.3 | Modify: `backend/.../config/SecurityConfig.java`. Ensure `@EnableMethodSecurity` is present. Register `CapabilityAuthorizationManager` as a custom pre-authorization manager. The manager coexists with existing `@PreAuthorize` -- both work simultaneously. |
| 313.5 | Create `CapabilityAuthorizationService` | 313A | 313.1 | New file: `backend/.../orgrole/CapabilityAuthorizationService.java`. `@Service`. `hasCapability(String)` delegates to `RequestScopes.hasCapability()`. `requireCapability(String)` throws `AccessDeniedException` if missing. Alternative to annotation for inline checks. |
| 313.6 | Extend `MemberFilter` to resolve and bind capabilities | 313A | 313.1 | Modify: `backend/.../member/MemberFilter.java`. After resolving `MemberInfo`, call `orgRoleService.resolveCapabilities(memberId)`. Add capabilities to the ScopedValue carrier: `carrier = carrier.where(RequestScopes.CAPABILITIES, capabilities)`. Constructor inject `OrgRoleService`. The member cache remains member-ID-only; capability resolution is a separate uncached DB fetch per request. |
| 313.7 | Handle missing org_role_id gracefully in MemberFilter | 313A | 313.6 | In `MemberFilter.resolveCapabilities()`: if member has no `orgRoleId` (should not happen after migration), bind empty set. Log a warning. This is a safety net for edge cases during migration. |
| 313.8 | Write integration tests for authorization infrastructure | 313A | 313.6 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/CapabilityAuthorizationTest.java`. Tests (~10): (1) `requiresCapability_withCapability_allows`; (2) `requiresCapability_withoutCapability_returns403`; (3) `requiresCapability_adminRole_alwaysAllows`; (4) `requiresCapability_ownerRole_alwaysAllows`; (5) `requiresCapability_memberRole_noCaps_returns403`; (6) `requiresCapability_customRole_correctCap_allows`; (7) `requiresCapability_customRole_wrongCap_returns403`; (8) `requiresCapability_unauthenticated_returns401`; (9) `preAuthorize_stillWorks_afterRegistration`; (10) `capabilities_boundInRequestScope_afterMemberFilter`. Create a test controller with `@RequiresCapability("INVOICING")` endpoint for testing. Use MockMvc with JWT mocks. |
| 313.9 | Create `CapabilityController` with `/api/me/capabilities` | 313B | 313A | New file: `backend/.../orgrole/CapabilityController.java`. `@RestController`. `GET /api/me/capabilities` -- reads `RequestScopes.CAPABILITIES`, `RequestScopes.ORG_ROLE`, builds `MyCapabilitiesResponse`. `@PreAuthorize("isAuthenticated()")`. Pure delegation to `OrgRoleService.getMyCapabilities()`. |
| 313.10 | Add `getMyCapabilities()` to `OrgRoleService` | 313B | 313.9 | Modify: `backend/.../orgrole/OrgRoleService.java`. Add `getMyCapabilities(UUID memberId)` -- fetches member, fetches role name, returns `MyCapabilitiesResponse` with capabilities from `RequestScopes`, role name, isAdmin/isOwner flags. |
| 313.11 | Add `GET /api/members/{id}/capabilities` endpoint | 313B | 313.10 | Modify: `backend/.../member/OrgMemberController.java`. Add `@GetMapping("/{id}/capabilities")`. Admin/Owner or self (check `RequestScopes.MEMBER_ID` matches `{id}` or caller is admin/owner). Delegates to `orgRoleService.getMemberCapabilities(UUID memberId)`. |
| 313.12 | Add `getMemberCapabilities()` to `OrgRoleService` | 313B | 313.11 | Modify: `backend/.../orgrole/OrgRoleService.java`. `getMemberCapabilities(UUID memberId)` -- fetches member, OrgRole, overrides, resolves effective capabilities. Returns `MemberCapabilitiesResponse`. |
| 313.13 | Write integration tests for capability endpoints | 313B | 313.11 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/CapabilityEndpointTest.java`. Tests (~8): (1) `getMyCapabilities_admin_returnsAll`; (2) `getMyCapabilities_member_returnsEmpty`; (3) `getMyCapabilities_customRole_returnsRoleCaps`; (4) `getMyCapabilities_withOverrides_returnsEffective`; (5) `getMemberCapabilities_self_allowed`; (6) `getMemberCapabilities_admin_allowed`; (7) `getMemberCapabilities_otherMember_returns403`; (8) `getMyCapabilities_returnsRoleName`. |
| 313.14 | Ensure `@PreAuthorize` and `@RequiresCapability` coexist | 313B | 313.8 | Verify in test: an endpoint with `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")` still works correctly after the `CapabilityAuthorizationManager` is registered. Both systems must coexist during the incremental migration. Add one dedicated coexistence test. |

### Key Files

**Slice 313A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/RequiresCapability.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/CapabilityAuthorizationManager.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/CapabilityAuthorizationService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/CapabilityAuthorizationTest.java`

**Slice 313A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- add CAPABILITIES
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java` -- resolve + bind capabilities
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/SecurityConfig.java` -- register authorization manager

**Slice 313A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java` -- resolution algorithm
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtUtils.java` -- JWT extraction patterns

**Slice 313B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/CapabilityController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/CapabilityEndpointTest.java`

**Slice 313B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/OrgMemberController.java` -- add capabilities endpoint
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java` -- add getMyCapabilities, getMemberCapabilities

### Architecture Decisions

- **Per-request DB resolution (no caching)**: Capability resolution fetches OrgRole + overrides on every request. This ensures role updates are immediately effective. The tables are tiny (~20 rows for roles, ~2 rows per member for overrides). Caching can be added later if profiling shows a hot path.
- **`AuthorizationManager<MethodInvocation>` pattern**: Coexists with existing `@PreAuthorize` -- Spring's method security chain evaluates all registered managers. Returning `null` (abstain) for non-annotated methods lets `@PreAuthorize` handle those.
- **Admin/Owner bypass via `"ALL"` sentinel**: Instead of special-casing admin/owner in every check, the resolution step returns `Set.of("ALL")` for system admin/owner roles. The `hasCapability()` check is a single set lookup.

---

## Epic 314: @PreAuthorize Migration (Batch 1: Financial, Invoicing, Project)

**Goal**: Replace `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")` with `@RequiresCapability` on controllers in the financial, invoicing, and project management domains. This is the highest-impact batch -- InvoiceController alone has 19 annotations.

**References**: Architecture doc Section 41.4 (migration map).

**Dependencies**: Epic 313 (authorization infrastructure must be in place).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **314A** | 314.1--314.5 | Migrate `InvoiceController` (19 annotations -> `INVOICING`), `BillingRunController` (15 annotations -> `INVOICING`), `CostRateController` (4 annotations -> `FINANCIAL_VISIBILITY`), `ReportController` profitability endpoints (2 -> `FINANCIAL_VISIBILITY`), `ExpenseController` approve endpoints (2 -> `FINANCIAL_VISIBILITY`). Update corresponding integration tests to use custom-role JWT fixtures. ~5 modified controllers + ~5 modified test files. Backend only. | **Done** (PR #640) |
| **314B** | 314.6--314.9 | Migrate `ProjectController` admin endpoints (5 annotations -> `PROJECT_MANAGEMENT`, delete stays `ORG_OWNER`), `ProjectTemplateController` writes (4 -> `PROJECT_MANAGEMENT`), `RecurringScheduleController` writes (4 -> `PROJECT_MANAGEMENT`), `ProposalController` admin writes (3 -> `INVOICING`, delete stays `ORG_OWNER`). Update tests. ~4 modified controllers + ~4 modified test files. Backend only. | **Done** (PR #641) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 314.1 | Migrate `InvoiceController` (19 annotations) | 314A | 313A | Modify: `backend/.../invoice/InvoiceController.java`. Replace all `@PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN', 'ROLE_ORG_OWNER')")` with `@RequiresCapability("INVOICING")`. 19 endpoints. Leave `isAuthenticated()` endpoints unchanged. |
| 314.2 | Migrate `BillingRunController` (15 annotations) | 314A | 313A | Modify: `backend/.../billingrun/BillingRunController.java`. Replace all 15 `@PreAuthorize` admin guards with `@RequiresCapability("INVOICING")`. |
| 314.3 | Migrate `CostRateController` (4 annotations) | 314A | 313A | Modify: `backend/.../costrate/CostRateController.java`. Replace 4 `@PreAuthorize` admin guards with `@RequiresCapability("FINANCIAL_VISIBILITY")`. |
| 314.4 | Migrate `ReportController` + `ExpenseController` | 314A | 313A | Modify: `backend/.../report/ReportController.java` -- replace admin-gated profitability endpoints (2) with `@RequiresCapability("FINANCIAL_VISIBILITY")`. Modify: `backend/.../expense/ExpenseController.java` -- replace admin-gated approve/categorize endpoints (2) with `@RequiresCapability("FINANCIAL_VISIBILITY")`. Leave all-member endpoints unchanged. |
| 314.5 | Update integration tests for batch 1 controllers | 314A | 314.4 | Modify: test files for `InvoiceController`, `BillingRunController`, `CostRateController`, `ReportController`, `ExpenseController`. Add test fixtures for custom-role JWTs: create a test helper that creates a member with a custom role having specific capabilities. Existing admin-jwt tests should still pass (admin has ALL). Add 2 new tests per controller: (1) custom role with correct capability -> 200; (2) custom role with wrong capability -> 403. ~10 new test methods across ~5 files. |
| 314.6 | Migrate `ProjectController` (5 admin endpoints) | 314B | 313A | Modify: `backend/.../project/ProjectController.java`. Replace create/edit/archive `@PreAuthorize` admin guards (5 endpoints) with `@RequiresCapability("PROJECT_MANAGEMENT")`. `DELETE` endpoint stays `hasRole('ORG_OWNER')`. All-member reads unchanged. |
| 314.7 | Migrate `ProjectTemplateController` writes (4 annotations) | 314B | 313A | Modify: `backend/.../projecttemplate/ProjectTemplateController.java`. Replace admin-gated writes with `@RequiresCapability("PROJECT_MANAGEMENT")`. Leave all-member reads unchanged. |
| 314.8 | Migrate `RecurringScheduleController` + `ProposalController` | 314B | 313A | Modify: `backend/.../schedule/RecurringScheduleController.java` -- replace admin-gated writes (4) with `@RequiresCapability("PROJECT_MANAGEMENT")`. Modify: `backend/.../proposal/ProposalController.java` -- replace admin-gated writes (3) with `@RequiresCapability("INVOICING")`. Delete stays `ORG_OWNER`. Leave all-member reads unchanged. |
| 314.9 | Update integration tests for batch 1b controllers | 314B | 314.8 | Modify: test files for `ProjectController`, `ProjectTemplateController`, `RecurringScheduleController`, `ProposalController`. Add custom-role JWT test fixtures. ~8 new test methods across ~4 files. |

### Key Files

**Slice 314A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/costrate/CostRateController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/ExpenseController.java`

**Slice 314B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplateController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalController.java`

**Both slices -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/RequiresCapability.java` -- annotation to use
- Architecture doc Section 41.4 -- migration map for exact mapping per endpoint

### Architecture Decisions

- **Incremental migration**: `@PreAuthorize` and `@RequiresCapability` coexist. Both are evaluated by Spring Security's method security chain. This allows gradual migration without a flag day.
- **Owner-only delete preserved**: `DELETE` on projects and proposals stays `hasRole('ORG_OWNER')` -- destructive operations are not delegatable via capabilities.
- **Test strategy**: Existing admin JWT tests continue to pass (admin bypass). New tests verify custom-role behavior. This ensures no regression.

---

## Epic 315: @PreAuthorize Migration (Batch 2: Customer, Automation, Resource, Team)

**Goal**: Replace `@PreAuthorize` with `@RequiresCapability` on the remaining admin-gated controllers: customer management, automations, resource planning, and team oversight domains.

**References**: Architecture doc Section 41.4 (migration map).

**Dependencies**: Epic 313 (authorization infrastructure).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **315A** | 315.1--315.4 | Migrate `CustomerController` (8), `CompliancePackController` (1), `RetentionController` (6), `DataRequestController` (8), `ChecklistTemplateController` writes (3), `ChecklistInstanceController` writes (3), `InformationRequestController` admin (5), `RequestTemplateController` writes (3), `AcceptanceController` admin (3) all to `CUSTOMER_MANAGEMENT`. ~9 modified controllers + test updates (~10 test adjustments). Backend only. | **Done** (PR #642) |
| **315B** | 315.5--315.8 | Migrate `AutomationRuleController` (8), `AutomationActionController` (4), `AutomationExecutionController` (3), `AutomationTemplateController` (2) to `AUTOMATIONS`. Migrate `MemberCapacityController` (4), `ResourceAllocationController` writes (3) to `RESOURCE_PLANNING`. Migrate `AdminTimeEntryController` (1) to `TEAM_OVERSIGHT`. ~7 modified controllers + test updates (~8 test adjustments). Backend only. | **Done** (PR #643) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 315.1 | Migrate `CustomerController` (8 annotations) | 315A | 313A | Modify: `backend/.../customer/CustomerController.java`. Replace admin-gated CRUD endpoints with `@RequiresCapability("CUSTOMER_MANAGEMENT")`. Leave all-member read endpoints unchanged. |
| 315.2 | Migrate customer-related controllers | 315A | 313A | Modify: `backend/.../compliance/CompliancePackController.java` (class-level -> `CUSTOMER_MANAGEMENT`), `backend/.../retention/RetentionController.java` (6 -> `CUSTOMER_MANAGEMENT`), `backend/.../datarequest/DataRequestController.java` (8 -> `CUSTOMER_MANAGEMENT`), `backend/.../acceptance/AcceptanceController.java` (3 admin endpoints -> `CUSTOMER_MANAGEMENT`). |
| 315.3 | Migrate checklist and information request controllers | 315A | 313A | Modify: `backend/.../checklist/ChecklistTemplateController.java` (writes -> `CUSTOMER_MANAGEMENT`), `backend/.../checklist/ChecklistInstanceController.java` (writes -> `CUSTOMER_MANAGEMENT`), `backend/.../informationrequest/InformationRequestController.java` (admin endpoints -> `CUSTOMER_MANAGEMENT`), `backend/.../informationrequest/RequestTemplateController.java` (writes -> `CUSTOMER_MANAGEMENT`). Leave `isAuthenticated` reads unchanged. |
| 315.4 | Update integration tests for batch 2a controllers | 315A | 315.3 | Modify: test files for all 9 controllers. Add custom-role JWT fixtures with `CUSTOMER_MANAGEMENT` capability. ~10 new test methods. Verify existing admin tests pass. |
| 315.5 | Migrate automation controllers | 315B | 313A | Modify: `backend/.../automation/AutomationRuleController.java` (8 -> `AUTOMATIONS`), `backend/.../automation/AutomationActionController.java` (4 -> `AUTOMATIONS`), `backend/.../automation/AutomationExecutionController.java` (3 -> `AUTOMATIONS`), `backend/.../automation/template/AutomationTemplateController.java` (2 -> `AUTOMATIONS`). |
| 315.6 | Migrate resource planning controllers | 315B | 313A | Modify: `backend/.../capacity/MemberCapacityController.java` (4 -> `RESOURCE_PLANNING`), `backend/.../capacity/ResourceAllocationController.java` (write endpoints only, 3 -> `RESOURCE_PLANNING`). Leave all-member read endpoints unchanged. |
| 315.7 | Migrate `AdminTimeEntryController` | 315B | 313A | Modify: `backend/.../timeentry/AdminTimeEntryController.java` (1 -> `TEAM_OVERSIGHT`). |
| 315.8 | Update integration tests for batch 2b controllers | 315B | 315.7 | Modify: test files for automation, capacity, time entry controllers. Add custom-role JWT fixtures with respective capabilities. ~8 new test methods. |

### Key Files

**Slice 315A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CompliancePackController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retention/RetentionController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataRequestController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestTemplateController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceController.java`

**Slice 315B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRuleController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationActionController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationExecutionController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/template/AutomationTemplateController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/MemberCapacityController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/ResourceAllocationController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/AdminTimeEntryController.java`

### Architecture Decisions

- **Batch 2 is independent of Batch 1**: Slices 315A/B can run in parallel with 314A/B since they modify different controllers. No file conflicts.
- **Class-level vs method-level annotation**: `CompliancePackController` currently uses class-level `@PreAuthorize`. Replace with `@RequiresCapability("CUSTOMER_MANAGEMENT")` at the class level (the annotation supports this).

---

## Epic 316: OrgRole CRUD API & Member Role Assignment

**Goal**: Expose the OrgRole CRUD REST API and the member role assignment endpoint. These endpoints power the Settings > Roles & Permissions page and the Team page member management.

**References**: Architecture doc Sections 41.5.1, 41.5.2, 41.5.4.

**Dependencies**: Epic 312 (OrgRole entity, service).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **316A** | 316.1--316.6 | `OrgRoleController` with 5 CRUD endpoints (list, get, create, update, delete), validation (name uniqueness, system role guard, capabilities subset, delete-with-members guard). ~2 new files + 1 test file (~12 tests). Backend only. | **Done** (PR #644) |
| **316B** | 316.7--316.13 | `PUT /api/members/{id}/role` endpoint, role assignment service logic with override validation, `MemberFilter.lazyCreateMember` extension for invite flow (assign orgRoleId on first login). ~4 modified files + 1 test file (~10 tests). Backend only. | **Done** (PR #645) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 316.1 | Create `OrgRoleController` | 316A | 312B | New file: `backend/.../orgrole/OrgRoleController.java`. `@RestController @RequestMapping("/api/org-roles")`. Endpoints: `GET /` (list all, any member), `GET /{id}` (get detail, any member), `POST /` (create, `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")`), `PUT /{id}` (update, admin/owner), `DELETE /{id}` (delete, admin/owner). Pure delegation to `OrgRoleService`. Pattern: follow backend CLAUDE.md controller discipline. |
| 316.2 | Implement `listRoles()` with member count | 316A | 316.1 | Enhance `OrgRoleService.listRoles()` to include `memberCount` per role. Query `MemberRepository.countByOrgRoleId(roleId)` for each role. Return `List<OrgRoleResponse>`. |
| 316.3 | Implement `createRole()` with validation | 316A | 316.1 | Enhance `OrgRoleService.createRole()`: validate name not blank, name unique (case-insensitive), capabilities subset of `Capability.ALL_NAMES`, generate slug from name. Return `OrgRoleResponse`. Throw `ResourceConflictException` for duplicate name. |
| 316.4 | Implement `updateRole()` with system role guard | 316A | 316.1 | Enhance `OrgRoleService.updateRole()`: fetch role, reject if `isSystem` (throw `InvalidStateException("Cannot modify system roles")`), validate name uniqueness (excluding self), validate capabilities subset, update fields + slug. |
| 316.5 | Implement `deleteRole()` with member check | 316A | 316.1 | Enhance `OrgRoleService.deleteRole()`: fetch role, reject if `isSystem`, check `memberCount > 0` (throw `ResourceConflictException("Reassign N members before deleting")`), delete. Return void. |
| 316.6 | Write integration tests for OrgRole CRUD | 316A | 316.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleControllerTest.java`. Tests (~12): (1) `listRoles_returnsSystemAndCustom`; (2) `listRoles_includesMemberCount`; (3) `getRole_exists_returns200`; (4) `getRole_notFound_returns404`; (5) `createRole_valid_returns201`; (6) `createRole_duplicateName_returns409`; (7) `createRole_invalidCapability_returns400`; (8) `createRole_memberRole_returns403`; (9) `updateRole_valid_returns200`; (10) `updateRole_systemRole_returns422`; (11) `deleteRole_noMembers_returns204`; (12) `deleteRole_withMembers_returns409`. |
| 316.7 | Add `assignRole()` to `OrgRoleService` | 316B | 316A | Modify: `backend/.../orgrole/OrgRoleService.java`. `assignRole(UUID memberId, AssignRoleRequest request)`: fetch member, validate orgRoleId exists, validate overrides (valid capability names, valid +/- prefix), apply. Guard: cannot change owner's role, cannot assign owner system role. Return `MemberCapabilitiesResponse`. |
| 316.8 | Add role assignment endpoint to `OrgMemberController` | 316B | 316.7 | Modify: `backend/.../member/OrgMemberController.java`. Add `@PutMapping("/{id}/role") @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")`. Delegates to `orgRoleService.assignRole()`. |
| 316.9 | Validate override format | 316B | 316.7 | In `OrgRoleService.assignRole()`: validate each override starts with `+` or `-`, the capability name after prefix is in `Capability.ALL_NAMES`. Throw `InvalidStateException` for invalid format. |
| 316.10 | Implement Owner protection rules | 316B | 316.7 | In `OrgRoleService.assignRole()`: (1) cannot change role of the org owner (member whose current system role slug is "owner"), (2) cannot assign the OWNER system role to another member, (3) ADMIN system role can only be assigned by owners (check `RequestScopes.ORG_ROLE`). Throw `ForbiddenException` for violations. |
| 316.11 | Extend `MemberFilter.lazyCreateMember` for invite flow | 316B | 316.7 | Modify: `backend/.../member/MemberFilter.java`. In `lazyCreateMember()`: after creating the member, assign the default system role based on JWT `orgRole` (owner->OWNER, admin->ADMIN, member->MEMBER). Look up system OrgRole by slug and set `member.orgRoleId`. This ensures JIT-provisioned members always have an orgRoleId. Future: invite flow will pass a custom role ID via member sync. |
| 316.12 | Add invite flow orgRoleId propagation | 316B | 316.11 | Modify: `backend/.../member/MemberSyncService.java` (or `MemberSyncController`). When a member sync event includes an `orgRoleId` field (from invite), set it on the member. This extends the existing webhook-driven member sync to carry the custom role. |
| 316.13 | Write integration tests for role assignment | 316B | 316.12 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/RoleAssignmentTest.java`. Tests (~10): (1) `assignRole_validRequest_updatesRole`; (2) `assignRole_withOverrides_setsOverrides`; (3) `assignRole_cannotChangeOwner_returns403`; (4) `assignRole_cannotAssignOwnerRole_returns403`; (5) `assignRole_invalidCapabilityOverride_returns400`; (6) `assignRole_nonExistentRole_returns404`; (7) `assignRole_memberRole_returns403`; (8) `assignRole_adminCanAssignCustomRole`; (9) `lazyCreateMember_setsDefaultSystemRole`; (10) `assignRole_clearsOverridesWhenOmitted`. |

### Key Files

**Slice 316A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleControllerTest.java`

**Slice 316A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java` -- enhance CRUD methods

**Slice 316B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java` -- add assignRole
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/OrgMemberController.java` -- add PUT /{id}/role
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java` -- extend lazyCreateMember
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java` -- orgRoleId propagation

**Slice 316B -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/RoleAssignmentTest.java`

### Architecture Decisions

- **OrgRole CRUD is admin-only**: Custom role management uses `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")` -- not `@RequiresCapability`. Role management is a platform administration function, not a delegatable feature-domain capability.
- **Delete guard**: A role with assigned members cannot be deleted. The frontend must provide a reassignment workflow before deletion.
- **Override validation**: Override strings must match the pattern `[+-]VALID_CAPABILITY`. Invalid overrides are rejected at API level, not stored and ignored.

---

## Epic 317: Audit Events & Notifications

**Goal**: Add audit events for role lifecycle changes and in-app notifications when a member's effective capabilities change.

**References**: Architecture doc Section 41.8 (Audit & Notifications).

**Dependencies**: Epic 316 (role CRUD and assignment must be implemented).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **317A** | 317.1--317.6 | 4 audit event types (ROLE_CREATED, ROLE_UPDATED, ROLE_DELETED, MEMBER_ROLE_CHANGED) via `AuditEventBuilder`, in-app notification on capability change via `NotificationService`. ~3 modified files + 1 test file (~6 tests). Backend only. | **Done** (PR #646) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 317.1 | Add `roleCreated()` to `AuditEventBuilder` | 317A | 316A | Modify: `backend/.../audit/AuditEventBuilder.java`. Add builder method `roleCreated(OrgRole role)` returning `AuditEvent` with type `ROLE_CREATED`, JSONB details `{name, slug, capabilities}`. |
| 317.2 | Add `roleUpdated()` to `AuditEventBuilder` | 317A | 316A | Modify: `backend/.../audit/AuditEventBuilder.java`. Add `roleUpdated(OrgRole role, Set<String> addedCaps, Set<String> removedCaps, long affectedMemberCount)`. JSONB details: `{name, addedCapabilities, removedCapabilities, affectedMemberCount}`. |
| 317.3 | Add `roleDeleted()` and `memberRoleChanged()` | 317A | 316B | Modify: `backend/.../audit/AuditEventBuilder.java`. `roleDeleted(String roleName)` -- JSONB `{name}`. `memberRoleChanged(Member member, String previousRole, String newRole, Set<String> overrides)` -- JSONB `{memberId, memberName, previousRole, newRole, overrides}`. |
| 317.4 | Wire audit events into `OrgRoleService` | 317A | 317.3 | Modify: `backend/.../orgrole/OrgRoleService.java`. Inject `AuditService`. Call `auditService.log()` in `createRole()`, `updateRole()`, `deleteRole()`, `assignRole()`. On `updateRole()` with capability changes, compute added/removed caps and affected member count. |
| 317.5 | Add capability-change notification | 317A | 317.4 | Modify: `backend/.../orgrole/OrgRoleService.java`. After `assignRole()` or `updateRole()` (when capabilities change), publish in-app notification to affected members: "Your permissions have been updated. You now have access to: [list]." Use existing `NotificationService.notify()`. On role update, notify all members on that role. On individual assignment, notify just that member. |
| 317.6 | Write integration tests for audit and notifications | 317A | 317.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/RoleAuditNotificationTest.java`. Tests (~6): (1) `createRole_logsAuditEvent`; (2) `updateRole_logsAuditEvent_withChangedCaps`; (3) `deleteRole_logsAuditEvent`; (4) `assignRole_logsAuditEvent`; (5) `assignRole_notifiesMember`; (6) `updateRole_withCapChange_notifiesAffectedMembers`. |

### Key Files

**Slice 317A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- add 4 builder methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java` -- wire audit + notifications

**Slice 317A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/RoleAuditNotificationTest.java`

**Slice 317A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- existing builder methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java` -- notify() pattern

### Architecture Decisions

- **In-app notification only**: No email for capability changes (low urgency). Consistent with architecture doc Section 41.8.
- **Role update cascading notification**: When a role's capabilities change, all members on that role are notified. The member count is bounded (10-50 per org), so this is a small batch.

---

## Epic 318: Frontend Capability Context & Sidebar Gating

**Goal**: Create the client-side `CapabilityProvider` React context that fetches capabilities on app load, the `RequiresCapability` wrapper component, and integrate capability gating into the sidebar navigation and page-level access control.

**References**: Architecture doc Sections 41.7.1, 41.7.2, 41.7.3.

**Dependencies**: Epic 313 (backend `/api/me/capabilities` endpoint must exist).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **318A** | 318.1--318.6 | `lib/api/capabilities.ts` API client, `lib/capabilities.tsx` `CapabilityProvider` context with `useCapabilities()` hook, `RequiresCapability` wrapper component, integration into org layout. ~5 new/modified files (~6 tests). Frontend only. | **Done** (PR #647) |
| **318B** | 318.7--318.12 | Sidebar nav gating (conditional nav items based on capabilities), page-level `notFound()` protection on 6+ gated routes, component-level action button gating (Create Project, Generate Invoice, Approve time). ~8 modified files (~6 tests). Frontend only. | **Done** (PR #648) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 318.1 | Create `lib/api/capabilities.ts` API client | 318A | 313B | New file: `frontend/lib/api/capabilities.ts`. Functions: `fetchMyCapabilities()` -- calls `GET /api/me/capabilities` via `lib/api.ts`, returns typed response. Pattern: `frontend/lib/api/billing-runs.ts` (if exists) or `frontend/lib/api.ts` fetch wrapper. |
| 318.2 | Create `lib/capabilities.tsx` CapabilityProvider | 318A | 318.1 | New file: `frontend/lib/capabilities.tsx`. `"use client"` component. `CapabilityContext` interface: `{ capabilities: Set<string>, hasCapability: (cap: string) => boolean, isAdmin: boolean, isOwner: boolean, isLoading: boolean }`. `CapabilityProvider` wraps children, fetches capabilities server-side via the org layout and passes down. `useCapabilities()` hook reads context. For admin/owner: `hasCapability` always returns `true`. |
| 318.3 | Integrate `CapabilityProvider` into org layout | 318A | 318.2 | Modify: `frontend/app/(app)/org/[slug]/layout.tsx`. Fetch capabilities server-side in the layout (server component calls `fetchMyCapabilities()`), pass data to `CapabilityProvider` wrapper around children. The provider is a client component receiving serialized data. |
| 318.4 | Create `RequiresCapability` wrapper component | 318A | 318.2 | Add to `frontend/lib/capabilities.tsx` or new file `frontend/components/requires-capability.tsx`. `<RequiresCapability cap="INVOICING">{children}</RequiresCapability>`. Renders children only if `hasCapability(cap)` returns `true`. Otherwise renders nothing (hidden, not greyed out). |
| 318.5 | Create capability constants | 318A | | Add to `frontend/lib/capabilities.tsx`. Export `CAPABILITIES` object: `{ FINANCIAL_VISIBILITY: "FINANCIAL_VISIBILITY", INVOICING: "INVOICING", PROJECT_MANAGEMENT: "PROJECT_MANAGEMENT", TEAM_OVERSIGHT: "TEAM_OVERSIGHT", CUSTOMER_MANAGEMENT: "CUSTOMER_MANAGEMENT", AUTOMATIONS: "AUTOMATIONS", RESOURCE_PLANNING: "RESOURCE_PLANNING" }`. |
| 318.6 | Write tests for CapabilityProvider | 318A | 318.4 | New file: `frontend/__tests__/capabilities.test.tsx`. Tests (~6): (1) `CapabilityProvider_setsCapabilities`; (2) `hasCapability_returnsTrue_whenPresent`; (3) `hasCapability_returnsFalse_whenMissing`; (4) `isAdmin_true_forAdmin`; (5) `RequiresCapability_rendersChildren_whenGranted`; (6) `RequiresCapability_rendersNothing_whenDenied`. Use mock data, no API calls in tests. |
| 318.7 | Add capability requirement to `NavItem` interface | 318B | 318A | Modify: `frontend/lib/nav-items.ts`. Add optional `requiredCapability?: string` field to `NavItem` interface. Set it on gated items: Invoices -> `INVOICING`, Customers -> `CUSTOMER_MANAGEMENT`, Profitability -> `FINANCIAL_VISIBILITY`, Resources -> `RESOURCE_PLANNING`, Compliance -> `CUSTOMER_MANAGEMENT`, Reports -> `FINANCIAL_VISIBILITY`, Retainers -> `INVOICING`, Recurring Schedules -> `PROJECT_MANAGEMENT`. Leave Dashboard, My Work, Calendar, Projects, Documents, Team, Notifications, Settings without a capability requirement. |
| 318.8 | Filter sidebar nav items by capability | 318B | 318.7 | Modify: `frontend/components/desktop-sidebar.tsx`. Import `useCapabilities()`. Filter `NAV_ITEMS` to only include items where `!item.requiredCapability || hasCapability(item.requiredCapability)`. If the sidebar is a server component, pass capabilities data down. Modify `frontend/components/mobile-sidebar.tsx` similarly. |
| 318.9 | Add page-level protection to gated routes | 318B | 318.3 | Modify gated page server components to check capabilities and return `notFound()` if missing. Pages to gate: `frontend/app/(app)/org/[slug]/invoices/page.tsx` (INVOICING), `frontend/app/(app)/org/[slug]/customers/page.tsx` (CUSTOMER_MANAGEMENT), `frontend/app/(app)/org/[slug]/profitability/page.tsx` (FINANCIAL_VISIBILITY), `frontend/app/(app)/org/[slug]/resources/page.tsx` (RESOURCE_PLANNING), `frontend/app/(app)/org/[slug]/compliance/page.tsx` (CUSTOMER_MANAGEMENT), `frontend/app/(app)/org/[slug]/reports/page.tsx` (FINANCIAL_VISIBILITY). Pattern: fetch capabilities in server component, check, call `notFound()` from `next/navigation`. |
| 318.10 | Gate action buttons with RequiresCapability | 318B | 318.4 | Modify key pages to wrap admin action buttons: (1) Projects page: wrap "Create Project" button with `<RequiresCapability cap="PROJECT_MANAGEMENT">`. (2) Customer page: wrap "Create Customer" with `<RequiresCapability cap="CUSTOMER_MANAGEMENT">`. (3) Time entries: wrap "Approve" button with `<RequiresCapability cap="TEAM_OVERSIGHT">`. ~3 modified component files. |
| 318.11 | Gate Settings sub-pages by capability | 318B | 318.7 | Modify: `frontend/app/(app)/org/[slug]/settings/automations/page.tsx` -- gate with `AUTOMATIONS` capability. `frontend/app/(app)/org/[slug]/settings/capacity/page.tsx` -- gate with `RESOURCE_PLANNING`. `frontend/app/(app)/org/[slug]/settings/rates/page.tsx` -- gate with `FINANCIAL_VISIBILITY`. Settings hub page remains admin-only (no change). |
| 318.12 | Write tests for sidebar and page gating | 318B | 318.10 | New file: `frontend/__tests__/capability-gating.test.tsx`. Tests (~6): (1) `sidebar_hidesInvoices_whenNoInvoicingCap`; (2) `sidebar_showsInvoices_whenInvoicingCap`; (3) `sidebar_showsAllForAdmin`; (4) `createProjectButton_hidden_whenNoProjectManagement`; (5) `createProjectButton_visible_whenProjectManagement`; (6) `approveButton_hidden_whenNoTeamOversight`. Mock `useCapabilities()` hook. |

### Key Files

**Slice 318A -- Create:**
- `frontend/lib/api/capabilities.ts`
- `frontend/lib/capabilities.tsx`
- `frontend/__tests__/capabilities.test.tsx`

**Slice 318A -- Modify:**
- `frontend/app/(app)/org/[slug]/layout.tsx` -- add CapabilityProvider

**Slice 318B -- Modify:**
- `frontend/lib/nav-items.ts` -- add `requiredCapability` field
- `frontend/components/desktop-sidebar.tsx` -- filter nav items
- `frontend/components/mobile-sidebar.tsx` -- filter nav items
- `frontend/app/(app)/org/[slug]/invoices/page.tsx` -- page-level gate
- `frontend/app/(app)/org/[slug]/customers/page.tsx` -- page-level gate
- `frontend/app/(app)/org/[slug]/profitability/page.tsx` -- page-level gate

**Slice 318B -- Create:**
- `frontend/__tests__/capability-gating.test.tsx`

### Architecture Decisions

- **Server-side fetch, client-side context**: Capabilities are fetched once in the org layout (server component), serialized, and passed to the `CapabilityProvider` (client component). No per-navigation API calls.
- **Hidden, not greyed out**: Features the user lacks capability for are removed from the DOM entirely. This is a deliberate UX decision from the architecture doc -- reduces confusion for users who don't know what they're missing.
- **`notFound()` for page protection**: Users who navigate directly to a gated URL see a 404, not a 403. This is consistent with the architecture doc's guidance and prevents information leakage.

---

## Epic 319: Settings: Roles & Permissions Page

**Goal**: Build the Settings > Roles & Permissions management page where admins create, edit, and delete custom roles with capability toggles.

**References**: Architecture doc Sections 41.7.4, 41.7.5.

**Dependencies**: Epic 316 (backend CRUD API), Epic 318 (frontend capability context).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **319A** | 319.1--319.6 | `lib/api/org-roles.ts` API client, Settings > Roles & Permissions page with system roles section (read-only cards), custom role cards (name, capability pills, member count, edit/delete actions), capability reference collapsible section, settings hub card link. ~6 new/modified files (~5 tests). Frontend only. | **Done** (PR #649) |
| **319B** | 319.7--319.12 | Create/Edit role dialog with name, description, and 7 capability checkboxes with descriptions. Delete role confirmation dialog with member reassignment warning. Server actions for create, update, delete. ~4 new files (~5 tests). Frontend only. | **Done** (PR #650) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 319.1 | Create `lib/api/org-roles.ts` API client | 319A | 316A | New file: `frontend/lib/api/org-roles.ts`. Functions: `fetchOrgRoles()`, `fetchOrgRole(id)`, `createOrgRole(data)`, `updateOrgRole(id, data)`, `deleteOrgRole(id)`. TypeScript interfaces: `OrgRole`, `CreateOrgRoleRequest`, `UpdateOrgRoleRequest`. Pattern: `frontend/lib/api/capabilities.ts` or similar API client files. |
| 319.2 | Add settings hub card for Roles & Permissions | 319A | | Modify: `frontend/app/(app)/org/[slug]/settings/page.tsx`. Add a "Roles & Permissions" card linking to `/org/[slug]/settings/roles`. Include icon (ShieldCheck or similar) and description "Define custom roles and manage team permissions." Admin/Owner only. |
| 319.3 | Create Roles & Permissions page (server component) | 319A | 319.1 | New file: `frontend/app/(app)/org/[slug]/settings/roles/page.tsx`. Server component that fetches `org-roles` via API. Renders system roles section and custom roles section. Admin/Owner only (`notFound()` for members). |
| 319.4 | Create system roles section | 319A | 319.3 | Part of page or new component. Render 3 system role cards (Owner, Admin, Member) with capability pills (all 7, all 7, none) in a read-only section. Show "System" badge. Show member count per role. No edit/delete actions. |
| 319.5 | Create custom role cards | 319A | 319.3 | Part of page or new client component `frontend/components/roles/role-card.tsx`. Each card shows: role name, description, capability pills (colored badges for each capability), member count, Edit and Delete icon buttons. "New Role" button at bottom. Pattern: existing settings cards (e.g., tags, checklists). |
| 319.6 | Write tests for Roles page | 319A | 319.5 | New file: `frontend/__tests__/roles-page.test.tsx`. Tests (~5): (1) `renders_systemRoles_readOnly`; (2) `renders_customRoles_withCapabilityPills`; (3) `renders_memberCount_perRole`; (4) `renders_newRoleButton`; (5) `renders_editDeleteActions_onCustomRoles`. Mock API responses. |
| 319.7 | Create role dialog (Create/Edit) | 319B | 319A | New file: `frontend/components/roles/role-dialog.tsx`. `"use client"`. Dialog with: name input, description textarea, 7 capability checkboxes (each with label + description text). Checkboxes for: Financial Visibility, Invoicing, Project Management, Team Oversight, Customer Management, Automations, Resource Planning. Use Shadcn Dialog, Checkbox, Input, Textarea. Support both create and edit modes. |
| 319.8 | Create server actions for role CRUD | 319B | 319.7 | New file: `frontend/app/(app)/org/[slug]/settings/roles/actions.ts`. Server actions: `createRole(formData)`, `updateRole(id, formData)`, `deleteRole(id)`. Call API client. Revalidate page path. Pattern: `frontend/app/(app)/org/[slug]/settings/tags/actions.ts`. |
| 319.9 | Create delete role confirmation dialog | 319B | 319.8 | New file: `frontend/components/roles/delete-role-dialog.tsx`. AlertDialog showing role name and member count. If members > 0, show "Reassign N members to another role before deleting." with disabled delete button. If members = 0, show confirm/cancel. Pattern: existing delete dialogs in settings. |
| 319.10 | Create capability reference section | 319B | 319.3 | Part of page or collapsible section. Table with 7 rows: capability name, description of what it grants. Collapsible by default. Helps admins understand what each capability controls. |
| 319.11 | Wire dialogs to role cards | 319B | 319.9 | Modify: `frontend/components/roles/role-card.tsx` (or page). Edit button opens role dialog in edit mode (pre-populated). Delete button opens delete confirmation. New Role button opens dialog in create mode. |
| 319.12 | Write tests for role dialogs | 319B | 319.11 | New file: `frontend/__tests__/role-dialog.test.tsx`. Tests (~5): (1) `createDialog_submitsValidData`; (2) `editDialog_preFillsExistingData`; (3) `editDialog_updatesCapabilities`; (4) `deleteDialog_showsMemberCount`; (5) `deleteDialog_disablesDelete_whenMembersAssigned`. Mock server actions. Use `@testing-library/react`. |

### Key Files

**Slice 319A -- Create:**
- `frontend/lib/api/org-roles.ts`
- `frontend/app/(app)/org/[slug]/settings/roles/page.tsx`
- `frontend/components/roles/role-card.tsx`
- `frontend/__tests__/roles-page.test.tsx`

**Slice 319A -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/page.tsx` -- add hub card

**Slice 319B -- Create:**
- `frontend/components/roles/role-dialog.tsx`
- `frontend/components/roles/delete-role-dialog.tsx`
- `frontend/app/(app)/org/[slug]/settings/roles/actions.ts`
- `frontend/__tests__/role-dialog.test.tsx`

**Both slices -- Read for context:**
- `frontend/app/(app)/org/[slug]/settings/tags/page.tsx` -- settings page pattern
- `frontend/app/(app)/org/[slug]/settings/tags/actions.ts` -- server actions pattern
- `frontend/components/ui/dialog.tsx` -- Dialog component
- `frontend/components/ui/checkbox.tsx` -- Checkbox component

### Architecture Decisions

- **Settings sub-route**: `/settings/roles` follows existing convention of `/settings/{feature}`.
- **Read-only system roles**: System roles are displayed but not editable. This is enforced both in the API (422 for system role edits) and in the UI (no edit/delete actions).
- **Capability pills**: Each capability is shown as a colored badge. This provides quick visual scanning of what a role can do.

---

## Epic 320: Team Page: Member Role Management & Invite Extension

**Goal**: Enhance the Team page with role badges, a member detail panel for role assignment with capability override toggles, and extend the invite form to support custom role selection.

**References**: Architecture doc Sections 41.7.6, 41.7.7, 41.5.4.

**Dependencies**: Epic 316 (backend role assignment API), Epic 318 (frontend capability context).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **320A** | 320.1--320.6 | Team page: role name badge column in member list, override indicator (+N or "custom"), member detail side panel with role dropdown + capability toggle overrides, server action for role assignment. ~5 modified/new files (~5 tests). Frontend only. | **Done** (PR #651) |
| **320B** | 320.7--320.11 | Invite form: role dropdown showing system + custom roles (grouped), capability summary preview below dropdown, "Customize for this user" expandable override section, server action extension for orgRoleId. ~3 modified files (~4 tests). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 320.1 | Add role column to member list table | 320A | 318A | Modify: `frontend/components/team/member-list.tsx`. Add "Role" column showing role name badge. For system roles: use existing badge variants (owner, admin, member). For custom roles: use a neutral badge with role name. Show override indicator "+N" if member has overrides. |
| 320.2 | Create member detail panel component | 320A | 320.1 | New file: `frontend/components/team/member-detail-panel.tsx`. `"use client"`. Sheet or side panel showing: member name, email, role dropdown (selectable list of all roles), capability checkboxes reflecting role defaults. Checkboxes show which are from the role preset vs overrides (e.g., italic label or "overridden" tag). Save and Cancel buttons. Admin/Owner only. |
| 320.3 | Fetch roles for dropdown | 320A | 320.2 | In member detail panel: call `fetchOrgRoles()` to populate the role dropdown. Group into "System" (Owner, Admin, Member) and "Custom" sections. When role changes, update capability checkboxes to reflect new role's preset capabilities. |
| 320.4 | Implement capability override toggles | 320A | 320.3 | In member detail panel: when user toggles a capability that differs from the role preset, track it as an override. Show visual distinction: capabilities from role preset are "default" (normal style), capabilities toggled beyond preset are "added" (+), capabilities removed from preset are "removed" (-). Build the `capabilityOverrides` array from toggle state. |
| 320.5 | Create server action for role assignment | 320A | 320.4 | Modify or create: `frontend/app/(app)/org/[slug]/team/actions.ts`. Add `assignMemberRole(memberId, orgRoleId, capabilityOverrides)` server action. Calls `PUT /api/members/{id}/role`. Revalidates team page. |
| 320.6 | Write tests for member role management | 320A | 320.5 | New file: `frontend/__tests__/member-role-management.test.tsx`. Tests (~5): (1) `memberList_showsRoleBadge`; (2) `memberDetail_showsRoleDropdown`; (3) `memberDetail_showsCapabilityToggles`; (4) `memberDetail_tracksOverrides`; (5) `memberDetail_callsAssignOnSave`. Mock API responses and server actions. |
| 320.7 | Extend invite form with role dropdown | 320B | 318A | Modify: `frontend/components/team/invite-member-form.tsx`. Replace the existing simple role select (Member/Admin) with a grouped dropdown: "System" (Member, Admin) and "Custom" (list of custom roles). When a custom role is selected, set `role: "member"` for Keycloak and add `orgRoleId` to form data. When Admin is selected, hide capability customization (admin has everything). |
| 320.8 | Add capability summary preview to invite form | 320B | 320.7 | In invite form: when a custom role is selected, show a read-only summary of that role's capabilities below the dropdown (capability pills). Helps the admin understand what the invited user will be able to do. |
| 320.9 | Add "Customize for this user" override section | 320B | 320.8 | In invite form: collapsible section below the capability summary. When expanded, shows 7 capability checkboxes reflecting the selected role's defaults. Toggling creates overrides. Build `capabilityOverrides` from toggle state. |
| 320.10 | Update invite server action for orgRoleId | 320B | 320.9 | Modify: `frontend/app/(app)/org/[slug]/team/actions.ts`. Extend the invite action to include `orgRoleId` and `capabilityOverrides` in the API request body. The backend invite endpoint accepts these optional fields. |
| 320.11 | Write tests for invite form extension | 320B | 320.10 | New file: `frontend/__tests__/invite-role-selection.test.tsx`. Tests (~4): (1) `inviteForm_showsCustomRolesInDropdown`; (2) `inviteForm_showsCapabilitySummary_onCustomRoleSelection`; (3) `inviteForm_hidesCustomization_onAdminSelection`; (4) `inviteForm_sendsOrgRoleId_withCustomRole`. Mock API responses. |

### Key Files

**Slice 320A -- Modify:**
- `frontend/components/team/member-list.tsx` -- add role column + override indicator

**Slice 320A -- Create:**
- `frontend/components/team/member-detail-panel.tsx`
- `frontend/__tests__/member-role-management.test.tsx`

**Slice 320A -- Modify:**
- `frontend/app/(app)/org/[slug]/team/actions.ts` -- add assignMemberRole action

**Slice 320B -- Modify:**
- `frontend/components/team/invite-member-form.tsx` -- role dropdown, capability preview, overrides
- `frontend/app/(app)/org/[slug]/team/actions.ts` -- extend invite action

**Slice 320B -- Create:**
- `frontend/__tests__/invite-role-selection.test.tsx`

**Both slices -- Read for context:**
- `frontend/components/team/member-list.tsx` -- current team table structure
- `frontend/components/team/invite-member-form.tsx` -- current invite form
- `frontend/app/(app)/org/[slug]/team/page.tsx` -- team page server component
- `frontend/lib/api/org-roles.ts` -- API client from Epic 319

### Architecture Decisions

- **Sheet panel for member detail**: A slide-out panel (Sheet component) for member role editing, rather than a full page navigation. This matches the quick-edit UX pattern used elsewhere in the app.
- **Override visual distinction**: Capability toggles that differ from the role preset are visually marked. This prevents admins from accidentally creating overrides when they meant to change the role.
- **Invite backward compatibility**: The `orgRoleId` field is optional in the invite API. Omitting it defaults to the MEMBER system role, preserving existing invite behavior.

---

