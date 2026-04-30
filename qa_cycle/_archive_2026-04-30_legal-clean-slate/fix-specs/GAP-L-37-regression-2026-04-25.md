# Fix Spec: GAP-L-37-regression-2026-04-25 — field-group over-attach at matter (PROJECT) scope

## Problem

QA Day 3 cycle-1 verify (2026-04-25 SAST) checkpoint **3.6 FAIL — REGRESSION**:
the RAF litigation matter `e788a51b-3a73-456c-b932-8d5bd27264c2`
("Dlamini v Road Accident Fund", `work_type=LITIGATION`) in tenant
`mathebula-partners` (`vertical_profile=legal-za`) renders **three** auto-applied
field groups instead of two:

1. **SA Conveyancing — Matter Details** — Conveyancing Type, Erf Number, Deeds
   Office, Lodgement Date, Transfer Duty, Bond Institution. **Wrong** — these are
   conveyancing fields and have no business on a litigation matter.
2. SA Legal — Matter Details — Case Number, Court, Opposing Party, Advocate. Correct.
3. Project Info — generic. Correct.

DB confirms `tenant_5039f2d497cf.projects.applied_field_groups` jsonb column
contains both `2b892529-…` (conveyancing_za_matter) **and** `2ce9428d-…`
(legal_za_matter).

Evidence: `qa_cycle/checkpoint-results/day-03.md` §"Day 3 Re-Verify — Cycle 1
(post-bugfix_cycle_2026-04-24 merge train)" row 3.6; screenshot
`day-03-cycle1-3.5-matter-overview-with-fica.png`; structural snapshot
`day-03-cycle1-matter-detail.yml` lines 139–270.

## Root Cause (verified)

PR #1122 ("Fix: frontend LOW sweep") **explicitly skipped L-37** — its own
description states:

> ### Skipped (1 / 9)
> - **GAP-L-37** — Over-broad field-group auto-attach. Requires new
>   `appliesToMatterTypes` metadata on the backend `FieldGroup` entity + a
>   filter in the list endpoint — no such metadata exists in the current
>   codebase. Pure client-side filtering would hard-code group-slug → matter-type
>   mappings that don't belong in the UI. **Deferred pending backend schema
>   change.**

So there is no "customer-scope fix" to mirror — the fix never landed for either
scope. The customer scope passed at QA Day 2 only by coincidence: there is no
`conveyancing-za-customer.json` field pack on the classpath, only the project
one (`backend/src/main/resources/field-packs/conveyancing-za-project.json`).

The actual mechanics of the bug:

1. `backend/src/main/resources/vertical-profiles/legal-za.json:9` declares
   `"field": ["legal-za-customer", "legal-za-project", "conveyancing-za-project"]`
   — conveyancing is bundled into the legal-za vertical profile by design.
2. `FieldPackSeeder` (extends `AbstractPackSeeder`) seeds every classpath pack
   whose `verticalProfile` matches the tenant. Both `legal-za-project.json`
   and `conveyancing-za-project.json` declare `verticalProfile: "legal-za"`,
   so both get installed into every legal-za tenant. Each pack's `group.autoApply`
   is `true`.
   - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/AbstractPackSeeder.java:130-141` — only filter is `packProfile.equals(tenantProfile)`.
   - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java:93` — `group.setAutoApply(pack.group().autoApplyOrDefault())` carries the JSON's `autoApply: true` straight onto the row.
3. `FieldGroupService.resolveAutoApplyGroupIds(EntityType entityType)` at
   `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java:348-368`
   returns **every** active field group with `auto_apply=true` for the entity
   type. There is **no** filter on tenant attribute, work_type, vertical
   sub-segment, or any other predicate. The `dependsOn` jsonb column is
   currently used only for "this field group depends on these other field group
   IDs being applied first" — not as a predicate.
4. Three call sites copy this list onto a new project at create time:
   - `ProjectFieldService.prepareForCreate` line 85 (manual create flow).
   - `ProjectTemplateService.instantiateTemplate` line 537 (create-from-template flow — what QA exercised).
   - `ProjectTemplateService.instantiateFromTemplate` line 691 (scheduler-driven recurring matter flow).

Net effect: every legal-za matter — regardless of work_type or template —
inherits both `legal_za_matter` and `conveyancing_za_matter` groups.

### Why option (b) is NOT the right fix

QA's recommended option (b) — "gate field-group seeding behind a per-tenant
pack-install whitelist so `conveyancing-za-project` is not present in legal-za
tenants" — sounded cheap but is actually larger than option (c), and has worse
semantics:

- The formal `PackInstallService` / `PackCatalogService` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/`) handles `DOCUMENT_TEMPLATE` and `AUTOMATION_TEMPLATE` only. Field packs are **not** integrated into that pipeline; they go through the older `AbstractPackSeeder` flow. So "mirror the existing pattern" is hypothetical — there is no pattern at the field-pack layer.
- A real legal-za tenant **does** want `conveyancing-za-project` available — a single legal firm can take both litigation and conveyancing matters. We must keep the field group **available** but not auto-attach to every matter. Removing the seed entirely (option b literal) would mean conveyancing matters in mixed-practice firms have no conveyancing fields — over-correction.
- The least disruptive interpretation of (b) — split `legal-za.json` into `legal-za-litigation.json` vs `legal-za-conveyancing.json` and force tenant choice — adds a profile-explosion problem and a UX migration for existing tenants.

### Why option (a) is NOT the right fix

`field_groups.depends_on` is currently typed as `List<UUID>` referring to **other
FieldGroup IDs that must be auto-applied alongside this one** (`FieldGroupService.validateDependsOn`
line 379 enforces same-entity-type, existence, no self-reference, no mutual
dependency). Repurposing it as a "tenant attribute predicate" overloads a
column that already has working semantics, and requires a non-trivial schema
change (predicate JSON → resolver evaluator). Higher risk than option (c).

### Why option (c) IS the right fix

Field group `conveyancing-za-project` is conceptually scoped to a specific
`Project.workType` ("CONVEYANCING"). `Project.workType` already exists
(`backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java:114-115`,
`work_type` column, length 50). Templates set `workType` at create time
(see `ProjectTemplateService` line 549-551). So the predicate "auto-apply this
group **iff** project.work_type is in {CONVEYANCING}" has a natural home
already on the entity. We just need:

1. A new optional `applicable_work_types` jsonb (or `text[]`) column on
   `field_groups` — null/empty means "applies to all work_types" (default,
   keeps existing legal-za-project + project-info behaviour).
2. A new optional field on `FieldPackGroup` JSON record (`applicableWorkTypes`)
   that the seeder writes onto the column.
3. A new method `FieldGroupService.resolveAutoApplyGroupIds(EntityType, String workType)`
   that filters out any group whose `applicableWorkTypes` is non-empty and
   does not contain the supplied work_type.
4. Three call-site updates (the two `ProjectTemplateService` paths and
   `ProjectFieldService.prepareForCreate`) to pass the project's work_type into
   the resolver. Customer/Task/Invoice paths are untouched — they don't have
   work_type.

Backwards-compatible: existing field groups have null `applicable_work_types`
→ same behaviour as today. Only `conveyancing-za-project` opts in.

## Fix

### 1. Schema migration

New tenant migration `V112__add_field_group_applicable_work_types.sql`:

```sql
-- GAP-L-37-regression-2026-04-25: scope auto-apply to project.work_type
-- when a field group declares applicable_work_types. NULL/empty = unscoped.
ALTER TABLE field_groups
  ADD COLUMN IF NOT EXISTS applicable_work_types jsonb;

-- Backfill conveyancing project field group with its work-type predicate so
-- existing legal-za tenants (provisioned before this migration) stop
-- auto-attaching conveyancing fields to non-conveyancing matters.
UPDATE field_groups
SET applicable_work_types = '["CONVEYANCING"]'::jsonb,
    updated_at = NOW()
WHERE pack_id = 'conveyancing-za-project'
  AND applicable_work_types IS NULL;
```

Note: this migration runs per-tenant via Flyway's tenant track
(`backend/src/main/resources/db/migration/tenant/`). The next free filename is
`V112__…` (V111 is the most recent — `V111__add_information_request_due_date.sql`).

### 2. JSON pack record + classpath JSON edit

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackGroup.java` — add field:

```java
public record FieldPackGroup(
    String slug,
    String name,
    String description,
    Boolean autoApply,
    java.util.List<String> applicableWorkTypes) {

  public boolean autoApplyOrDefault() {
    return autoApply != null && autoApply;
  }

  public java.util.List<String> applicableWorkTypesOrEmpty() {
    return applicableWorkTypes != null ? applicableWorkTypes : java.util.List.of();
  }
}
```

Jackson tolerates missing fields when deserializing the existing 9 JSON files
(they don't declare `applicableWorkTypes`) — they default to null → empty list →
unscoped behaviour. Confirm with the existing test suite.

`backend/src/main/resources/field-packs/conveyancing-za-project.json` — add to
the `group` block:

```json
  "group": {
    "slug": "conveyancing_za_matter",
    "name": "SA Conveyancing — Matter Details",
    "description": "South African conveyancing-specific fields for property transfer matters (projects with matter_type = CONVEYANCING)",
    "autoApply": true,
    "applicableWorkTypes": ["CONVEYANCING"]
  },
```

### 3. Entity column + setter

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroup.java` — add field, getter, setter
(mirror the `dependsOn` jsonb pattern at lines 50-52, 133-135, 156-159):

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "applicable_work_types", columnDefinition = "jsonb")
private java.util.List<String> applicableWorkTypes;

public java.util.List<String> getApplicableWorkTypes() {
  return applicableWorkTypes;
}

public void setApplicableWorkTypes(java.util.List<String> applicableWorkTypes) {
  this.applicableWorkTypes = applicableWorkTypes;
  this.updatedAt = java.time.Instant.now();
}
```

### 4. Seeder writes the predicate

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java`,
inside `applyPack` after line 93 (`group.setAutoApply(...)`), add:

```java
var workTypes = pack.group().applicableWorkTypesOrEmpty();
if (!workTypes.isEmpty()) {
  group.setApplicableWorkTypes(new java.util.ArrayList<>(workTypes));
}
```

### 5. Resolver overload

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java`,
add **alongside** the existing `resolveAutoApplyGroupIds(EntityType)` (do NOT
break the existing signature — it's still used for CUSTOMER/TASK/INVOICE):

```java
/**
 * Variant for PROJECT entity type — applies the same auto-apply lookup but
 * filters out groups whose applicable_work_types is non-empty and does not
 * contain the supplied work_type. Used at project create time so a legal-za
 * tenant that has both legal-za-project and conveyancing-za-project field
 * groups installed only attaches the conveyancing one to matters with
 * work_type = CONVEYANCING.
 *
 * <p>Pass {@code null} or empty work_type to suppress the filter (i.e., omit
 * any group that REQUIRES a work_type — they would never match).
 */
public List<UUID> resolveAutoApplyGroupIds(EntityType entityType, String workType) {
  var autoGroups = fieldGroupRepository.findByEntityTypeAndAutoApplyTrueAndActiveTrue(entityType);
  var filtered = new java.util.ArrayList<FieldGroup>();
  for (var group : autoGroups) {
    var required = group.getApplicableWorkTypes();
    if (required == null || required.isEmpty()) {
      filtered.add(group);                    // unscoped — always applies
      continue;
    }
    if (workType != null && required.contains(workType)) {
      filtered.add(group);                    // matches caller's work_type
    }
    // else: scoped to a different work_type → skip
  }
  var groupIds = new LinkedHashSet<>(filtered.stream().map(FieldGroup::getId).toList());
  // dependsOn resolution unchanged from the existing signature
  for (var group : filtered) {
    if (group.getDependsOn() != null) {
      for (UUID depId : group.getDependsOn()) {
        var depGroup = fieldGroupRepository.findById(depId);
        if (depGroup.isPresent() && depGroup.get().isActive()) {
          groupIds.add(depId);
        } else {
          log.warn(
              "Skipping invalid/inactive dependency group {} for auto-apply group {}",
              depId,
              group.getId());
        }
      }
    }
  }
  return new ArrayList<>(groupIds);
}
```

The original `resolveAutoApplyGroupIds(EntityType)` should now delegate to the
new variant with `workType=null`, so existing CUSTOMER/TASK/INVOICE callers
continue to skip groups that DO declare `applicableWorkTypes` (which is
correct — those are project-only predicates anyway, and CUSTOMER/TASK/INVOICE
groups should never declare work_type predicates).

### 6. Three call-site updates

Each of the three `setAppliedFieldGroups` callers in the project create path
must pass the project's work_type:

a. `ProjectFieldService.prepareForCreate` (line 60-97) — add `String workType`
   parameter, pass it through. Caller is `ProjectService.createProject` —
   already has `workType` in scope (it's a parameter to `createProject` at
   line 156). Update the call at line 165 to pass `workType`.

b. `ProjectTemplateService.instantiateTemplate` line 537 — change to:
   ```java
   var autoApplyFieldGroupIds =
       fieldGroupService.resolveAutoApplyGroupIds(EntityType.PROJECT, request.workType());
   ```
   Note: the `request.workType()` is available here (used at line 549).
   **Important**: the auto-apply resolution currently runs at line 537 BEFORE
   line 549 sets the work_type onto the project. Move the work_type set before
   the resolve call (or use `request.workType()` directly — same string).

c. `ProjectTemplateService.instantiateFromTemplate` line 691 (scheduler path) —
   the recurring scheduler instantiates from the template directly. The
   template itself doesn't carry work_type today (templates produce projects;
   work_type is set per-project on creation). Pass `null` for now — the
   scheduler-instantiated projects won't get a work_type and therefore won't
   get any work_type-scoped field groups (correct behaviour: no false attach).
   Add a TODO comment referencing this gap so a future feature can wire
   template.workType through.

### 7. Repository note

No new repository method needed — the existing `findByEntityTypeAndAutoApplyTrueAndActiveTrue`
still does the heavy lift. Filtering happens in-process which is fine given
the small cardinality of auto-apply groups per tenant (typically <10).

## Scope

- **Files to modify** (8):
  1. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroup.java` — new column, getter, setter.
  2. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackGroup.java` — new record field + helper.
  3. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java` — write predicate.
  4. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupService.java` — new overload + delegation.
  5. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectFieldService.java` — add workType param.
  6. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` — pass workType to ProjectFieldService.
  7. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplateService.java` — both call sites (lines 537, 691).
  8. `backend/src/main/resources/field-packs/conveyancing-za-project.json` — add `applicableWorkTypes: ["CONVEYANCING"]`.
- **Files to create** (1):
  1. `backend/src/main/resources/db/migration/tenant/V112__add_field_group_applicable_work_types.sql` — column + backfill update.
- **Migration needed**: yes — adds `field_groups.applicable_work_types jsonb` column and backfills the conveyancing pack's predicate so existing tenants (provisioned before this migration) get the fix without re-seeding.
- **Backend restart needed**: yes — Java code change + Flyway migration must run on startup.
- **Frontend HMR sufficient**: N/A — no frontend change.

## Verification

### A. Re-run scenario 3.6 end-to-end (Bob → Litigation matter for Sipho)

1. `bash compose/scripts/svc.sh restart backend` (loads the V112 migration; the running PID's `field_groups` table will gain the `applicable_work_types` column and the `conveyancing-za-project` row will be backfilled).
2. Verify migration ran: `docker exec -it $(docker ps -qf name=postgres) psql -U postgres -d app -c "SELECT pack_id, applicable_work_types FROM tenant_5039f2d497cf.field_groups WHERE pack_id IN ('conveyancing-za-project','legal-za-project');"` — conveyancing row shows `["CONVEYANCING"]`, legal row shows NULL.
3. **Without re-creating the matter**, the existing `e788a51b-…` row's `applied_field_groups` is stale (it was set at create time before the fix). Two acceptable verify paths:
   - **Path A (fresh matter)**: create a new RAF litigation matter for Sipho via the same flow (Bob → customer detail → "+ New Matter" → Litigation template). Expect the new matter's `applied_field_groups` to contain ONLY `legal_za_matter` and `Project Info` group IDs — no conveyancing. UI shows two field-group cards, not three.
   - **Path B (test conveyancing positive case)**: create a Conveyancing matter for Sipho (work_type=CONVEYANCING). Expect `applied_field_groups` to include both `conveyancing_za_matter` AND `legal_za_matter` (legal stays unscoped → applies to all). UI shows both cards.
4. UI assertion: matter detail page Field Groups panel renders only the expected groups; SA Conveyancing — Matter Details with its 6 fields is **absent** for the litigation matter and **present** for a conveyancing matter.

### B. DB invariants

```sql
-- For legal-za tenant after fix, a litigation matter has 2 group IDs.
SELECT id, name, work_type, jsonb_array_length(applied_field_groups)
FROM tenant_5039f2d497cf.projects
WHERE work_type = 'LITIGATION'
  AND status = 'ACTIVE'
ORDER BY created_at DESC LIMIT 5;
-- Expect jsonb_array_length = 2 (Project Info + legal_za_matter), not 3.

-- A conveyancing matter has 3 group IDs.
SELECT id, name, work_type, jsonb_array_length(applied_field_groups)
FROM tenant_5039f2d497cf.projects
WHERE work_type = 'CONVEYANCING'
ORDER BY created_at DESC LIMIT 5;
-- Expect jsonb_array_length = 3 (Project Info + legal_za_matter + conveyancing_za_matter).
```

### C. Targeted backend tests

Add a new integration test class (e.g., `FieldGroupAutoApplyWorkTypeIntegrationTest`)
under `backend/src/test/java/.../fielddefinition/` covering:

1. Project with `work_type=LITIGATION` in a legal-za tenant inherits `legal_za_matter` and `Project Info` but **not** `conveyancing_za_matter`.
2. Project with `work_type=CONVEYANCING` in the same tenant inherits all three.
3. Project with `work_type=null` inherits unscoped groups only (excludes conveyancing).
4. Customer/Task/Invoice paths still call the old `resolveAutoApplyGroupIds(EntityType)` overload and are untouched (regression guard).
5. Seeder backfill: re-seeding a legal-za tenant after the migration sets the conveyancing group's `applicable_work_types` correctly.

Reuse `TestEntityHelper`, `TestJwtFactory`, `TestcontainersConfiguration` per `backend/CLAUDE.md`. Do not add a `PostgreSQLContainer`.

### D. Existing test suite

Full backend `./mvnw test` must remain green (the new optional field on
`FieldPackGroup` is null-tolerant for the 9 existing pack JSON files;
`resolveAutoApplyGroupIds(EntityType)` still exists with original semantics).

## Estimated Effort

**M (30 min – 2 hr)** — 8 file edits + 1 migration + 1 new test class. Plumbing
through 3 call sites is the bulk of the work. No tricky concurrency, no
distributed coordination, no new external surface. The Jackson record default
behaviour and the existing dependsOn-jsonb pattern give us copy-paste templates.
