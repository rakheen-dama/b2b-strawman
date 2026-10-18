# ADR-342: Audit `entityType`, EntityTag, SavedView and Field-Definition Entity-Type Strings Stay `"project"` / `"customer"` / `"PROJECT"` / `"CUSTOMER"` Under the Renamed Wire — Guard Test + Comment Convention

**Status**: Accepted

**Context**:

Two planes of persisted entity-type strings exist and neither is the `EntityType` enum. **The audit plane** uses lowercase, snake_case free strings ([ADR-026](ADR-026-audit-event-granularity.md): free-form event types; [ADR-035](ADR-035-activity-feed-direct-audit-query.md): the activity feed is a direct query on `audit_events` with an expression index on `details->>'project_id'`): `"invoice"` ×22, `"org_settings"` ×14, `"task"` ×11, `"document_template"` ×11, `"document"` ×11, `"trust_transaction"` ×10, **`"project"` ×10, `"customer"` ×10**, `"legal_disbursement"` ×9, `"acceptance_request"` ×9 (`.arch-context.md` §8). `AuditEventTypeRegistryTest:84,131,132` pins the catalogue at **`hasSize(29)`**; `audit/{ProjectServiceAuditTest, CustomerServiceAuditTest, AuditTenantIsolationTest, AuditEventControllerTest, AuditServiceIntegrationTest}`, `portal/PortalActivityServiceFirmFilterTest` and `AuditEventMetadataEndpointIntegrationTest` assert the `"project"`/`"customer"` literals; `ActivityMessageFormatter` reads `details.entity_type`; the frontend `components/audit/entity-cell.tsx` maps `customer: (id) => /customers/${id}`, `project: (id) => /projects/${id}`, `matter_closure: …` by that key. **The EntityTag / SavedView / field-definition plane** uses UPPERCASE: `"PROJECT"` ×10, `"CUSTOMER"` ×12, `"TASK"` ×16, `"INVOICE"` ×2 across `ProjectController`, `CustomerController`, `TaskController`, `tag/*` (`EntityTag`, `EntityTagService`, `TagFilterUtil` — [ADR-054](ADR-054-tag-storage-join-table-vs-array.md)), `view/*` (`saved_views.entity_type varchar(20)`, `ViewFilterHelper`'s entity→table map `PROJECT→projects`, `CustomFieldFilterUtil`'s promoted-slug map keyed `"CUSTOMER"`/`"PROJECT"` — [ADR-055](ADR-055-saved-view-filter-execution.md)), `InvoiceController` and `AccountingSyncService.entityType("INVOICE")`; the frontend `lib/types/field.ts` `EntityType = "PROJECT"|"TASK"|"CUSTOMER"|"INVOICE"` drives `getViews("PROJECT")` and `getFieldDefinitions("PROJECT")`.

The memory `backend_audit_entitytype_casing` records the failure mode: mixing the planes, or renaming one side, **silently breaks the activity tab** — rows are written with one string and queried with another, nothing throws, the tab is simply empty (PR #1503).

[ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md) renames `project→matter` and `customer→client` on the wire; the natural "consistency fix" a later contributor will reach for is to rename these strings too. The requirements say they stay and ask this ADR to document it "so nobody fixes it later". Phase 87 adds no migration.

**Options Considered**:

1. **Rename the strings to match the wire** — `"project"`→`"matter"`, `"customer"`→`"client"`, `"PROJECT"`→`"MATTER"`, `"CUSTOMER"`→`"CLIENT"`.
   - Pros:
     - One vocabulary from URL to audit row.
     - `entity-cell.tsx` keys match the route segment.
   - Cons:
     - Persisted data: `audit_events.entity_type`, `audit_events.details->>'entity_type'`, `entity_tags.entity_type`, `saved_views.entity_type` and `field_definitions.entity_type` rows all carry the old strings — a data migration (`V4`) plus a rewrite of the `details->>'project_id'` expression index, in a migration-free phase.
     - Zero user-visible gain: the strings are internal keys.
     - The UI label ("Matter", "Client") is written by the component that renders them ([ADR-338](ADR-338-terminology-layer-removal-single-vocabulary.md)).
     - Touches ≈ 20 audit sites, ≈ 22 UPPERCASE sites, the `AuditEventTypeRegistry` catalogue and seven asserting test classes.
     - The silent-breakage risk is maximal exactly while the rename is in flight.
2. **Keep every string as-is; add a guard test and a comment convention (CHOSEN)** — a new `EntityTypeLiteralStabilityTest` (a plain JUnit source scan with no Spring context, in `src/test/java/…/audit/` beside `AuditEventTypeRegistryTest` — not `architecture/`, which is ArchUnit-only; separate from `WireVocabularyContractTest`) asserts that the audit catalogue and the set of audit `entityType` literals used by `ProjectService`/`CustomerService` contain `"project"` and `"customer"` and **not** `"matter"`/`"client"`, and that the EntityTag/SavedView/field-definition plane contains `"PROJECT"`/`"CUSTOMER"` and not `"MATTER"`/`"CLIENT"`; a one-line comment `// ADR-342: persisted entity-type literal — stays "project" under the matter/client wire` at each literal site (or on the shared constant where one exists); `AuditEventTypeRegistryTest` stays pinned at 29.
   - Pros:
     - No migration, no data touch, no index change.
     - The rename PRs cannot accidentally sweep these literals because the test fails on `"matter"`.
     - The comment answers the "why is this still project?" question at the point a contributor would change it.
     - Frontend `entity-cell.tsx` changes only its href targets (`/matters/${id}`, `/clients/${id}`), not its keys — the one place the two vocabularies meet, and it is a two-line edit.
   - Cons:
     - A permanent mismatch between wire (`matter`) and audit key (`project`) that every audit-log reader must know.
     - The audit UI shows a label, so users never see it, but developers reading `audit_events` do.
     - The guard is string-level; a new entity type added with the wrong casing on the wrong plane is still possible — the test guards renames, not new entries.
3. **Dual-write old and new strings** — write `"matter"` alongside `"project"` (or an alias column) and accept both on read.
   - Pros:
     - Migrates vocabulary gradually without a bulk update.
   - Cons:
     - Doubles the write paths and every query predicate (`entityType IN ('project','matter')`), including the native SQL in `ReportRepository` and the activity index.
     - Ends in the same data migration later plus a cleanup.
     - The activity tab breaks if any read path forgets the alias — the exact silent failure the memory warns about.
4. **Translate at the read boundary** — keep storage, map `"project"`→`"matter"` in the audit/activity DTOs and the tag/view responses.
   - Pros:
     - API consumers see legal vocabulary in `entityType` fields.
   - Cons:
     - Puts a terminology map back into the backend one phase after deleting the frontend one.
     - The mapping must be applied symmetrically on filter inputs (`ActivityController`'s `entityType` param, `/api/views?entityType=`, `/api/audit-events` facets) or filters silently return nothing — again the memory's failure mode.
     - The MCP tools (`get_audit_events`, `get_matter_activity`) would then expose a third vocabulary.

**Decision**: Option 2 — audit `entityType` strings, EntityTag, SavedView and field-definition entity-type strings keep their current values (`"project"`, `"customer"`, `"PROJECT"`, `"CUSTOMER"`), `AuditEventTypeRegistryTest` stays pinned at 29, and a guard test plus a comment convention make the stability explicit and test-enforced.

**Rationale**:

1. **They are keys, not labels.** Users see "Matter" and "Client" because the rendering component writes those words ([ADR-338](ADR-338-terminology-layer-removal-single-vocabulary.md)); the strings exist to join rows, filter facets and pick a table (`ViewFilterHelper`). Renaming a key changes nothing visible and risks everything persisted.
2. **The failure is silent.** The activity tab, audit facets, tag filters and saved views all degrade to "empty", not to an error, when a string drifts (PR #1503). A test that fails loudly on `"matter"` converts a silent production defect into a red PR.
3. **No migration in Phase 87, and the baseline is immutable soon.** `saved_views`, `entity_tags`, `field_definitions` and `audit_events` are in `tenant/V1__baseline.sql`; rewriting their rows is a `V4` with no product value, and once Phase 84's take-on data lands the cost becomes permanent ([ADR-331](ADR-331-migration-squash-at-zero-tenants.md)).
4. **MCP already exposes these strings.** `get_audit_events` and `get_matter_activity` return audit rows to the `kazi-legal-za` plugin; changing `entityType` values would be a contract change [ADR-343](ADR-343-mcp-contract-pinning-test.md) exists to prevent.
5. **Code-defined default views depend on it.** Phase 87's system saved views (My matters, Open matters, Awaiting fee note, Dormant; Active clients, FICA outstanding, With trust balance) are code-defined and merged client-side ahead of user rows precisely because no migration may seed them; they are keyed `"PROJECT"`/`"CUSTOMER"` so they filter through the same `ViewFilterHelper` path as user views.

**Consequences**:

- Positive:
  - `EntityTypeLiteralStabilityTest` lands in the first rename batch (S1) beside `WireVocabularyContractTest`.
  - The two tests together define the boundary: wire says matter/client, persistence and keys say project/customer.
  - `AuditEventTypeRegistryTest` 29-pin untouched.
  - The seven asserting test classes untouched.
  - `ActivityMessageFormatter`, `TagFilterUtil`, `ViewFilterHelper`, `CustomFieldFilterUtil` untouched.
  - Frontend: `lib/types/field.ts` `EntityType` union unchanged.
  - `getViews("PROJECT")`/`getFieldDefinitions("PROJECT")` calls unchanged even in renamed files.
  - `entity-cell.tsx` keys unchanged, hrefs renamed.
  - `backend/CLAUDE.md` gains the rule next to the wire-vocabulary paragraph ([ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md)): "audit/EntityTag/SavedView/field-definition strings do not follow the wire".
- Negative:
  - Developers querying `audit_events` or `saved_views` directly must know `project` means matter.
  - The comment convention and `backend/CLAUDE.md` are the only cues.
  - A future legitimate rename (e.g. a Phase that introduces a real `Matter` entity) must write the data migration this ADR avoids and update the guard test deliberately — the test is designed to make that a conscious act.
  - The guard does not police new entity types.
  - Casing discipline for new audit/tag strings still relies on review and the memory note.
- Follow-ups:
  - None required by Phase 87. If a later phase renames entities ([ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md) option 1), it supersedes this ADR and ships the migration.
- Related:
  - [ADR-026](ADR-026-audit-event-granularity.md) / [ADR-035](ADR-035-activity-feed-direct-audit-query.md) (why audit strings are free text and indexed), [ADR-054](ADR-054-tag-storage-join-table-vs-array.md) (polymorphic `EntityTag`), [ADR-055](ADR-055-saved-view-filter-execution.md) (server-side view filters keyed by entity type), [ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md) (the wire this ADR declines to follow), [ADR-343](ADR-343-mcp-contract-pinning-test.md) (audit rows crossing the MCP boundary), [ADR-331](ADR-331-migration-squash-at-zero-tenants.md).
