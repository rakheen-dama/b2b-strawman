# ADR-356: Pack Content Reconciliation for Existing Tenants — Version Bump plus an Idempotent `from → to` Re-Apply in the Seeder; User-Edited Rows Are Never Overwritten

**Status**: Accepted

**Context**:

Several Phase 88 units edit pack JSON: [ADR-353](ADR-353-firm-letterhead-settings.md) (engagement letters gain the logo and letterhead block, the fee note gains address and banking blocks), U7 (`document-review-notification` rule renamed "Document uploaded" and seeded disabled, D4), U10 (`conveyancing-za-clauses/pack.json:288` declares the seven OTP `templateAssociations`), U9 (SoA category label column), and U21 (24 pack-JSON strings and the report-definition names in `StandardReportPackSeeder`, R8 §1.3–1.4). All of these seed **once per tenant**: `AbstractPackSeeder:143-153` skips an applied pack and calls `reconcileExistingPack`, whose default is a no-op; `ClausePackSeeder.isPackAlreadyApplied:74-77` matches on packId only, so a `version` bump alone does nothing; `StandardReportPackSeeder:50` skips when applied; `backend/db/BASELINE.md` notes that pack-seeded data is not backfilled by seeders. Tenant V2 (`add_project_template_work_type`) is the worked example of an idempotent `UPDATE` in a migration. The requirements make reconciliation mandatory: "fresh tenants only" is not acceptable for demo surfaces because the walked Keycloak tenant and the demo tenants must change too. The open question is the rule for rows a firm has edited since seeding — document templates carry `source = SEEDER` and firms may clone or edit; automation rules can be renamed, disabled or re-enabled; field-group names can be edited.

**Options Considered**:

1. **Fresh-tenant-only (edit the JSON, accept that existing tenants keep old content).**
   - Pros:
     - Zero reconcile code.
   - Cons:
     - The capstone tenant, the demo tenants and the VPS dev tenant would still print "Banking details available on request" and run the "Document accepted" rule.
     - The end-of-phase re-walk would fail on the very surfaces this phase exists for. Rejected by the requirements.
2. **SQL data migrations (tenant V-next with `UPDATE … SET name = 'Matter info' WHERE …`).**
   - Pros:
     - Runs everywhere Flyway runs.
     - The V2 precedent exists.
   - Cons:
     - Pack content would live in two places (JSON for new tenants, SQL for old).
     - Each future pack edit needs a matching migration.
     - Tiptap document bodies (the engagement-letter block) are JSON documents that SQL cannot sensibly patch.
     - No way to express "only if unedited" without duplicating the seeder's knowledge of previous values.
3. **Versioned reconcile in the seeder: bump the pack `version`; when the installed version is lower, each seeder's `reconcileExistingPack` re-applies names, descriptions, associations, bodies and enabled flags idempotently using `(key, fromValue, toValue)` triples; a row is updated only when its current value equals the previous pack version's value (CHOSEN).**
   - Pros:
     - One source of content (the pack JSON) and one mechanism (`AbstractPackSeeder`), already invoked by `TenantProvisioningService` and `PackReconciliationRunner:129-130` in the right order (templates before clauses).
     - "Unedited since seeding" is decidable without a flag: `current == from` means the firm never touched it.
     - Anything else is left alone and logged.
     - Idempotent by construction (`current == to` is a no-op), so a re-run or a crash mid-way is safe.
   - Cons:
     - Each pack edit must carry the previous value (the pack JSON gains a `reconcile` block).
     - A user-edited row that happens to equal the old seeded value is overwritten — acceptable, since it is byte-identical to what the pack shipped.

**Decision**: Option 3 — every pack JSON (field, template, clause, automation, report, compliance, request) carries a `version` and gains a `reconcile` block:

```json
"reconcile": [
  { "entity": "automation_rule|field_group|document_template|clause_template_association|report_definition",
    "key": "<pack key/slug>", "field": "<column>",
    "from": "<previous shipped value, or sha256:<hash> for Tiptap bodies>", "to": "<new value>" }
]
```

It is applied by the existing hook `AbstractPackSeeder.reconcileExistingPack(D pack, String tenantId)`, which gains a **version gate**: it runs only when the pack's recorded version, parsed as an integer from `pack_install.pack_version` (`varchar(32)`), is lower than `pack.version`, and writes the new version afterwards (the gate is new; today the hook runs on every start with no gate and its default is a no-op). Today only the document-template and automation installers write `pack_install`; E8 extends those writes to all five pack types and backfills the clause, field and report rows at the version each tenant has applied (from the OrgSettings pack-status lists that `recordPackApplication` maintains), so the gate has a row to read for every seeder. **One rule:** a row field is updated only where its current value equals `from` (string equality, or hash equality for bodies); rows whose current value differs are left alone and logged at INFO with tenant, pack, key; clones are never touched; `clause_template_association` entries are upserted, skip-existing. Boolean flags follow the same rule — the U7 case is `enabled` `from: true → to: false`. Limitation, accepted: a firm that deliberately re-enabled the rule after an earlier change is indistinguishable from one that never touched it and is switched off again; the flag is one click to restore. The reconcile runs at backend startup for every tenant through the existing `PackReconciliationRunner` and once more on provisioning. **Ownership:** the version gate and the `reconcile`-block parser are built in E8 (U7, the first consumer); E9 (U8/U10 pack text + OTP associations) and E11 (U21 pack/report text) depend on E8.

**Rationale**:

1. **Packs are the product's content model under schema-per-tenant**; a tenant's seeded rows are a materialised copy of the JSON. Reconciling from the same JSON keeps one truth and makes the reconcile a property of every future pack edit rather than a one-off.
2. **The `from → to` rule is the smallest honest definition of "user-edited."** No `edited_by_user` flag exists on most pack-seeded tables and adding one is a V4 column per table; comparing against the previous shipped value needs no schema and never clobbers a firm's wording. For D4's rule flag it reads naturally: `enabled: true → false` applies only to rules whose flag is currently `true` — which cannot distinguish "never touched" from "deliberately re-enabled"; accepted because the flag is one click to restore.
3. **Idempotency is required by `BASELINE.md`** for migrations and is equally required here because the runner executes on every startup and every tenant; `current == to` short-circuits.
4. **Demo-partner timeline:** the walked tenant is upgraded by restarting the backend after merge (the `clean compile` rule), with no re-seed and no manual SQL — the only path compatible with the "no SQL shortcuts" and "no workarounds" rules.

**Consequences**:

- Positive:
  - The letterhead blocks, the OTP clause associations, the "Document uploaded" rule (disabled), the SoA category column and the 24 + report-definition legal strings reach the walked and demo tenants on the next restart.
  - Future pack edits are reconciled by default.
  - "fresh tenants only" ceases to be a possible outcome.
- Negative:
  - Each affected seeder implements `reconcileExistingPack` (template, clause, automation, report, field) against its pack's `reconcile` block; the version gate and the block parser land once, in E8 (U7), and E9 / E11 build on them.
  - `PackInstallBackfillTest`, `TemplatePackSeederTest`, `ClausePackSeederIntegrationTest` (gains a conveyancing association case), `AutomationTemplateSeederTest`, `ReportingControllerTest` gain "installed at version n−1, reconcile to n" cases including a user-edited row that must survive.
  - `ConveyancingTemplatePackTest`'s `EXPECTED_LEGAL_ZA_TEMPLATE_COUNT` and the `VisualRegressionTest` goldens change with the content.
  - A firm whose edit coincidentally equals the old shipped value is upgraded.
  - Documented as intended.
  - Clones (`DocumentTemplateService.resetToDefault:488` resets clones only) are untouched by reconcile — a clone is by definition user content.
- Follow-ups:
  - Record the reconcile contract (version bump + `from → to` triples + skip-on-edit rule) in `backend/CLAUDE.md` so every later pack edit ships its reconcile by default.
- Related:
  - [ADR-353](ADR-353-firm-letterhead-settings.md), [ADR-357](ADR-357-terminology-guard-extension.md) (pack text is in the census), [ADR-331](ADR-331-migration-squash-at-zero-tenants.md) and `backend/db/BASELINE.md` (why packs are not backfilled by migrations), `tasks/phase88-research/R4-documents-rendering-letterhead.md` §2, §6, `tasks/phase88-research/R8-terminology-census-shell-layout.md` §1.3, `tasks/phase88-gap-register.md` D4.
