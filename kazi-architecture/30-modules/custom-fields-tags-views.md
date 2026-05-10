# Custom Fields, Tags & Views

**Bounded context:** see [`10-bounded-contexts.md` § custom-fields-tags-views](../10-bounded-contexts.md#custom-fields-tags-views).

## Purpose

Cross-cutting **extensibility surface**: tenants add custom fields to any domain entity, group fields with auto-apply rules, attach freeform tags, and persist filtered list configurations as saved views — **all without code changes**. Every domain entity that owns a `custom_fields JSONB` column or surfaces tags consumes this module. Pack-seeded definitions arrive at provisioning via the [`packs`](packs.md) module; user-created definitions sit alongside them and are distinguished by `pack_id` / `pack_field_key`.

The module deliberately stays storage-thin (JSONB on the entity itself, per ADR-052) so that tenant isolation, audit, and Hibernate `@Filter` semantics inherit from the owning entity row rather than from a parallel EAV table.

## Entities owned

| Entity | Table | Anchor | Notes |
|---|---|---|---|
| `FieldDefinition` | `field_definitions` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinition.java:22` | Per-tenant copy of one field schema. Carries `entityType`, `slug`, `fieldType`, `required`, `options jsonb`, `validation jsonb`, `visibilityCondition jsonb`, `requiredForContexts jsonb`, `packId`, `packFieldKey`, `portalVisibleDeadline`, `active`. Slug is **immutable** after creation (ADR-093 invariant). |
| `FieldGroup` | `field_groups` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroup.java:19` | Bundle of field defs. Carries `autoApply boolean`, `dependsOn jsonb` (array of group UUIDs), `applicableWorkTypes jsonb` (PROJECT-only, see L37 regression note in source), `packId`. |
| `FieldGroupMember` | `field_group_members` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupMember.java` | Join row binding `FieldDefinition` → `FieldGroup`. |
| `Tag` | `tags` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/Tag.java:15` | Org-scoped label. `name`, `slug` (immutable per source comment L70-72), optional `color`. |
| `EntityTag` | `entity_tags` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/EntityTag.java:14` | Polymorphic join: `(tag_id FK, entity_type, entity_id)`. `ON DELETE CASCADE` from `Tag`. No FK on the entity side — application-level integrity (ADR-054 § Consequences). |
| `SavedView` | `saved_views` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/SavedView.java:19` | `entityType` (string discriminator), `filters jsonb`, `columns jsonb` (UI-only, server ignores per ADR-055 L74), `shared bool`, `createdBy UUID`, `sortOrder`. |

**Pack-seed scaffolding (read-only DTOs, not persisted):** `FieldPackDefinition`, `FieldPackGroup`, `FieldPackField` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackDefinition.java`. Consumed by `FieldPackSeeder` during pack install (delegated from the broader `packs` pipeline — see [`packs.md`](packs.md)).

**Custom-field values themselves are not entities here.** They live as `custom_fields JSONB` columns on `Project`, `Task`, `Customer`, `Invoice`, etc. (ADR-052 § Consequences). This module owns the *schema*, not the *values*.

## REST surface

| Controller | Path | Methods | Anchor |
|---|---|---|---|
| `FieldDefinitionController` | `/api/field-definitions` + `/api/field-groups` | Full CRUD on both, group membership management | `→ A1-backend-map.md:407` |
| `TagController` | `/api/tags` | CRUD on tags; usage counts | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/TagController.java` |
| `SavedViewController` | `/api/views` | `GET list`, `POST create`, `PUT /{id}`, `DELETE /{id}` (4 endpoints) | `→ A1-backend-map.md:406` |

**Custom-field write paths live on the owning entity, not on this module:** every entity controller that supports custom fields exposes `PUT /{id}/custom-fields` and `PUT /{id}/field-groups` (apply / detach groups), and `PUT /{id}/tags` (replace tag set). Examples — `ProjectController` `→ A1-backend-map.md:388`, `CustomerController` `→ A1-backend-map.md:390`, `InvoiceController` `→ A1-backend-map.md:391`, `TaskController` `→ A1-backend-map.md:389`. This is intentional: custom fields are part of the entity's lifecycle (validation, audit, prerequisites) and routing via the entity controller keeps capability checks consistent with normal updates.

**List-endpoint integration:** any list endpoint that accepts a `?view={viewId}` query param resolves it through `ViewFilterHelper.applyViewFilter(...)` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/ViewFilterHelper.java:34`), which loads the `SavedView`, validates `entityType` matches the calling endpoint, and dispatches to `ViewFilterService.executeFilterQuery(...)`. Filters can also be passed inline as query params (`?status=ACTIVE&tags=urgent&customField[slug]=value` per ADR-055 L19, L80) without a saved view.

## Frontend pages / components

| Path / file | Purpose |
|---|---|
| `frontend/app/(app)/org/[slug]/settings/custom-fields/page.tsx` | Field definitions + field groups admin UI (`→ A2-frontend-map.md:213`). Server component that streams definitions; `custom-fields-content.tsx` is the interactive client surface; `actions.ts` holds server actions for CRUD. |
| `frontend/app/(app)/org/[slug]/settings/tags/page.tsx` | Tag library — list, create, recolor, delete, usage counts (`tags-content.tsx` for client interactivity). |
| `frontend/components/views/SavedViewSelector.tsx` | Dropdown component embedded in every list page (projects, tasks, customers, invoices) that surfaces shared + personal views. |
| `frontend/components/views/ViewSelectorClient.tsx` | Client wrapper that wires `?view={id}` to the URL. |
| `frontend/components/views/CreateViewDialog.tsx` / `EditViewDialog.tsx` | Modal capture of filter state → POST/PUT `/api/views`. |
| `lib/types/field.ts` | `FieldDefinitionResponse`, `FieldGroupResponse`, `EntityType` types (`→ A2-frontend-map.md:363`). |
| `components/customers/CustomFieldBadges` | Inline rendering of custom-field values on the customer card (`→ A2-frontend-map.md:432`). |

Custom fields surface inside every entity edit form via a shared `CustomFieldSection` React component that evaluates `visibilityCondition` client-side (ADR-094 Decision A, Option 2 — frontend + backend both evaluate; backend authority lives in `CustomFieldValidator` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/CustomFieldValidator.java`).

## Domain events

**This module publishes one domain event** — `FieldDateApproachingEvent` — but it is published from `automation/fielddate/FieldDateScannerScheduler` (`→ A1-backend-map.md:491`), not from the `fielddefinition` package itself. The scheduler reads `FieldDefinition.portalVisibleDeadline` (see source L82-87) and `fieldType == DATE` to decide whether the event projects onto the customer-portal deadline view (`portal.portal_deadline_view`, ADR-257).

CRUD on `FieldDefinition`, `FieldGroup`, `Tag`, `EntityTag`, and `SavedView` does **not** publish domain events — these are configuration changes, not business state transitions. Audit coverage for these mutations comes from the generic audit pipeline (`audit` module) via the standard service-layer audit hooks, not via the `event` bus. (Confirmed by absence of any `*Event` records anchored to `fielddefinition/`, `tag/`, or `view/` packages in the discovery map.)

## Cross-cutting touchpoints

- **JSONB storage on entities (ADR-052).** Every entity that supports custom fields owns a `custom_fields JSONB` column with a GIN index for `@>` containment. Unknown keys are silently stripped during validation by `CustomFieldValidator` (ADR-052 § Consequences). Range queries cast through `(custom_fields ->> 'slug')::numeric` — *not* GIN-indexed, so per-tenant expression indexes may be added on hot fields (ADR-052 § Consequences L67-69).
- **Tenant isolation inherits from the entity row.** Because custom-field values live in the entity's own JSONB column, the schema-per-tenant boundary applies automatically — no parallel EAV table to isolate (ADR-052 § Rationale L60). `EntityTag`, `FieldDefinition`, `FieldGroup`, and `SavedView` are tenant-scoped tables sitting in the same per-tenant schema (`→ A1-backend-map.md:281, 297`).
- **Auto-apply (`FieldGroup.autoApply`).** `FieldGroupResolver` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroupResolver.java:17`) evaluates auto-apply at entity create time. For `PROJECT` entities the resolver additionally honours `applicableWorkTypes` (introduced for L37 regression — see source L54-62): a group whose list is null/empty applies to all work types; a group with a non-empty list applies only when the project's `workType` matches.
- **Conditional visibility (ADR-094).** `FieldDefinition.visibilityCondition` carries `{ dependsOnSlug, operator: eq|neq|in, value }`. Both frontend and backend evaluate independently — the backend's `CustomFieldValidator` skips required-field validation for hidden fields (ADR-094 Decision A, Rationale L77-79). Hidden values are **preserved** in JSONB ("ghost data") rather than cleared (ADR-094 Decision B § Rationale L82).
- **Saved-view filter execution (ADR-055).** `ViewFilterService` builds parameterised native SQL via per-filter handlers: `StatusFilterHandler`, `TagFilterHandler`, `CustomFieldFilterHandler`, `DateRangeFilterHandler`, `SearchFilterHandler` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/`). Native queries bypass Hibernate `@Filter`; tenant isolation falls back to the schema boundary itself (the `tenant_*` schema is selected per-request via `RequestScopes.TENANT_ID` → `search_path`, as documented in `backend/CLAUDE.md` § Multitenancy).
- **Pack-seeded vs user-created.** `FieldDefinition.packId` + `pack_field_key` and `FieldGroup.packId` track origin. `OrgSettings.fieldPackStatus jsonb` records which pack versions a tenant has applied (ADR-053 § Consequences L58). User edits to a pack-origin field are preserved across pack updates by `pack_field_key` set-difference (ADR-053 § Rationale L51).
- **Tag rename / delete is O(1).** Updates one row in `tags`; `ON DELETE CASCADE` on `entity_tags.tag_id` cleans associations (ADR-054 § Consequences). Cross-entity-type queries ("which entities use tag X?") go through `entity_tags` directly.
- **Required-for-contexts.** `FieldDefinition.requiredForContexts jsonb` (source L73-75) lists transition or document-template contexts where this field becomes required even if `required = false` globally. Drives prerequisite gates on lifecycle transitions (consumed by `prerequisite/` and `customer-lifecycle`).

## Vertical specifics

Field packs and field groups are the primary mechanism by which a vertical fork (legal-za, accounting-za, etc.) ships its domain shape:

- **Pack files live as classpath resources.** A vertical fork drops JSON pack files into `src/main/resources/field-packs/` (and `compliance-packs/`, `template-packs/`); `FieldPackSeeder` discovers them at provisioning (ADR-053 § Consequences L57-59). No code change in this module per vertical.
- **Auto-apply is the vertical hook.** A legal-za pack ships a "Litigation Fields" group with `autoApply = true` and `applicableWorkTypes = ["litigation"]`; an accounting-za pack ships "VAT Fields" with `entityType = INVOICE` and `autoApply = true`. The same module code services both — only the seed data differs.
- **`portalVisibleDeadline` is universal but pack-defined.** Whether a vertical exposes a custom date field on the portal deadline view is a property of the pack's field definition, not of this module.
- **Compliance packs are a sibling pattern (ADR-063 — see Open Questions).** They install checklist templates + retention policies + field definitions. Field-definition seeding inside a compliance pack delegates to `FieldPackSeeder.seedFieldsFromPack()` (ADR-063 § Consequences L82-86) — i.e. compliance packs reuse this module's seeder rather than duplicating it.

Cross-link: [`packs.md`](packs.md) for the unified pack catalog mechanism.

## Active ADRs

| ADR | Title | Status | Notes |
|---|---|---|---|
| ADR-052 | jsonb-vs-eav-custom-field-storage | Active | JSONB column on entity, GIN index, app-level validation. Reinforced by ADR-237 (structural-vs-custom field boundary) per `→ A4-adr-triage.md:61`. |
| ADR-053 | field-pack-seeding-strategy | Active | Per-tenant copies seeded at provisioning; `pack_id` + `pack_field_key` track origin. Reinforced by ADR-240 (unified pack catalog) per `→ A4-adr-triage.md:62`. |
| ADR-054 | tag-storage-join-table-vs-array | Active | Polymorphic join table `EntityTag`; cascade delete; `EXISTS` subqueries for filter. |
| ADR-055 | saved-view-filter-execution | Active | Server-side parameterised native SQL; `ViewFilterService` + per-filter handlers; columns are frontend-only. |
| ADR-093 | template-required-fields | Active | Soft slug references in `requiredContextFields jsonb` on `DocumentTemplate`; relies on this module's slug-immutability invariant. |
| ADR-094 | conditional-field-visibility | Active | Frontend + backend evaluation; preserve hidden values silently. Reinforced by ADR-167 (conditional-block-predicate) per `→ A4-adr-triage.md:103`. |
| ADR-063 | compliance-packs-bundled-seed-data | **Superseded-by-spirit** by ADR-240 | A4 (`→ A4-adr-triage.md:72, 415, 591`) flags ADR-063 as rolled into the unified pack catalog. The seeding *mechanism* (classpath JSON, per-tenant seeded copies, `pack_*` origin columns) is unchanged; only the *catalog* is unified. See Open Questions. |

See [`90-adr-index.md`](../90-adr-index.md) for full ADR index.

## Key flows

- Pack install → field definitions + field groups + auto-apply: see [`50-flows/pack-install-and-vertical-onboarding.md`](../50-flows/pack-install-and-vertical-onboarding.md).
- Custom-field date → portal deadline projection: anchored in `automation/fielddate/FieldDateScannerScheduler` (`→ A1-backend-map.md:491`); flow doc not yet authored — covered tangentially in [`50-flows/automation-trigger-to-action.md`](../50-flows/automation-trigger-to-action.md).
- Saved-view application on a list page: `?view={id}` → `ViewFilterHelper.applyViewFilter` → `ViewFilterService.executeFilterQuery` → native SQL with parameterised handlers. No dedicated flow doc; trace is short enough to live in ADR-055.

## Open questions / known fragility

1. **ADR-063 supersession status.** A4 marks it "Superseded-by-spirit" by ADR-240 (`→ A4-adr-triage.md:415, 591`) but the ADR file itself still says **Status: Accepted** (verified by reading the file). Either the ADR header needs updating to `Superseded-by: ADR-240` or A4's triage is wrong. **Decision needed** before this module page is treated as canonical.
2. **`ViewFilterHelper` / `ViewFilterService` SQL injection surface.** ADR-055 § Consequences L84 mandates: *"all values are parameterized, never interpolated."* The handler classes exist (`StatusFilterHandler`, `TagFilterHandler`, etc.) but `tableName` and `entityType` are passed as **string** arguments to `executeFilterQuery(...)` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/ViewFilterHelper.java:46`). If `tableName` ever flows from user input — even indirectly via `entityType` lookup — the parameterisation guarantee is bypassed for the table identifier. **Verify**: trace every call site of `executeFilterQuery` and confirm `tableName` is hardcoded per controller (not derived from request payload). Audit task.
3. **`FieldGroup.dependsOn` cycle handling.** The column is a `jsonb` array of group UUIDs (source L50-52) but no anchor in `FieldGroupResolver` shows cycle detection. If group A `dependsOn` B and B `dependsOn` A, auto-apply traversal could loop. **Verify**: read `FieldGroupResolver` end-to-end and confirm either (a) cycle detection exists, or (b) the data model invariant guarantees a DAG (e.g. ordering constraint). Currently undocumented in any ADR.
4. **Slug immutability is enforced in code, not in the schema.** `FieldDefinition.generateSlug` validates on create (source L115-131) and `Tag` source comment (L70-72) declares slug "intentionally immutable", but the database has no `UNIQUE` or check constraint preventing direct UPDATE. ADR-093's `requiredContextFields` model is **brittle** to a slug-rename bug — if anyone adds a `setSlug` setter or a Flyway migration that renames slugs, every soft slug reference in document templates silently goes stale.
5. **"Ghost data" in custom-field JSONB (ADR-094 Decision B).** Hidden field values persist. Exports, audits, and downstream integrations see values for fields that are not currently visible. ADR-094 calls this "not a security concern" (L98-99) but no documentation surfaces this to integration consumers — risks a surprise for the first integration that reads raw JSONB.
6. **`SavedView.entityType` is a `String`, not the `EntityType` enum** (source L26 vs `FieldDefinition.entityType` which uses `@Enumerated`). The values must align (e.g. `"PROJECT"`, `"CUSTOMER"`) but no FK / check constraint enforces it. A typo in a saved view's `entityType` would cause `ViewFilterHelper.resolveAndValidate` to throw at request time but is silently accepted at write time. Minor — flagged for normalisation if/when this module is touched.
