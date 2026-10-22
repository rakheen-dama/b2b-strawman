# ADR-345: Canonical Project-Role Values (`lead` / `member`) — Normalise Every Writer, Add a CHECK, Guard at the Entity, Migrate Existing Rows

**Status**: Accepted

**Context**:

`project_members.project_role` is a `varchar(50)` with no enum and no CHECK (`tenant/V1__baseline.sql:1458-1470`); the only invariant is the partial unique index `idx_project_members_unique_lead … WHERE project_role = 'lead'` (`:2171`). The canonical constants are `Roles.PROJECT_LEAD = "lead"` / `Roles.PROJECT_MEMBER = "member"` (`security/Roles.java:17-19`), but two producers write other values: `ProjectTemplateService.java:569,739` writes the literal `"LEAD"` on both instantiation paths, and `LegalDemoDataSeeder.java:151` writes `"CONTRIBUTOR"`. The R1 census (`tasks/phase88-research/R1-matter-team-lead-casing.md` §2) found **13 lowercase readers** (`ProjectAccessService:47`, `ProjectMemberService:116,148`, `ProjectMemberRepository:40`, `MatterListRowRepository:46`, `TimeEntryService:424-431`, the partial index, the frontend `matterRole === "lead"` …) and **4 uppercase readers** (`NotificationService:359,512,615`, `AssignMemberActionExecutor:25,94`, `CreateTaskActionExecutor:105`), plus one tolerant reader (`ProjectContextBuilder:154`, `equalsIgnoreCase`).

The split is symmetric and silent: a template-created matter's lead is invisible to the Matters list "Fee earner" column (G-D11 a), gets 403 on the per-member time breakdown (G-D13), cannot transfer lead or save-as-template; a `POST /api/matters`-created matter never notifies its lead and automation `PROJECT_OWNER` recipients resolve to nobody, falling back to every admin/owner (N-1, the double-recipient half of G-B13). `InstantiateTemplateIntegrationTest:277` pins `"LEAD"` — the one test that actively pins the defect. Next free tenant migration is V4 (`.arch-context.md` §3).

**Options Considered**:

1. **Normalise every writer to the constants, lower-case at the entity, migrate existing rows and add `CHECK (project_role IN ('lead','member'))` (CHOSEN).**
   - Pros:
     - One literal source (`Roles`).
     - The 4 uppercase readers become identical to the 13.
     - The CHECK makes the invariant self-enforcing in every tenant schema, so a future writer (Phase 84 take-on) cannot reintroduce the split.
     - The partial unique index finally covers every lead row.
     - Fixes the walked tenant and demo tenants in place — no re-seed.
   - Cons:
     - A data migration with ordering subtleties (duplicate leads, `CONTRIBUTOR`) and a V4 targeted test.
     - `InstantiateTemplateIntegrationTest:277` flips.
     - `AutomationEndToEndTest:102` seed changes for hygiene.
2. **Case-insensitive compare at every reader.**
   - Pros:
     - No migration.
     - Existing rows untouched.
   - Cons:
     - Touches ~12 read sites including native SQL (`lower(pm.project_role)`), JPQL and Spring Data derived queries that must become custom queries.
     - The partial index must be rebuilt on `lower(project_role)`.
     - Leaves the data dirty and `"CONTRIBUTOR"` rows still unrecognised as anything.
     - Spreads tolerance instead of removing the cause — the pattern that produced the 13/4 split in the first place.
3. **Replace the column with an enum (`@Enumerated(STRING)` `ProjectRole`).**
   - Pros:
     - Type-safe in Java.
     - The compiler finds every reader.
   - Cons:
     - Java enum constants are upper-case.
     - Storing `lead`/`member` needs an `AttributeConverter`, and the wire contract (`matterRole: "lead"`, pinned by `ProjectMemberIntegrationTest:80,261`, `MatterListRowIntegrationTest:303,328`, audit JSON `{"role":"lead"}` in `ProjectMemberAuditTest:227`) must still be lowercase — the converter is the same guard as option 1 with more ceremony.
     - A larger blast radius (MCP DTO, three JPQL projections, `MatterWithRole`) for no additional invariant beyond what the CHECK gives.
4. **Fix only the writer (no migration).**
   - Pros:
     - Smallest diff.
   - Cons:
     - Every existing template-created matter (the walked tenant has 4 `LEAD` rows) stays broken.
     - The demo tenant would have to be re-seeded. Rejected by the founder's "fix at the root" decision.

**Decision**: Option 1 — all six write sites use `Roles.PROJECT_LEAD` / `Roles.PROJECT_MEMBER` (template instantiation both paths; demo seeder `CONTRIBUTOR → member`), the four uppercase readers use the constants, `ProjectMember` normalises through a single `Roles.normaliseProjectRole(String)` (lower-cases; anything outside `Roles.PROJECT_ROLES` maps to `member`) in its constructor/setter and `@PrePersist`/`@PreUpdate`, and tenant V4 normalises existing rows then adds `chk_project_members_role`.

**Rationale**:

1. **Schema-per-tenant makes the DB the right place for the invariant.** Every tenant schema gets the CHECK through the Flyway tenant runner (as V2/V3 did), so the rule holds for tenants provisioned before and after this phase, including Phase 84 take-on imports that will write this table.
2. **The demo-partner timeline needs the walked tenant fixed, not re-seeded.** The V4 data migration repairs the capstone tenant and every demo tenant in place; only option 1 and a V4 do that.
3. **Pinned contracts stay lowercase.** The wire (`matterRole`), audit JSON and frontend `MatterRole = "lead" | "member"` are already lowercase and pinned; normalising the store to match is the change that touches the fewest pins.
4. **Migration ordering is deliberate.** Because the partial unique index is lowercase-only, a project could hold a `'lead'` (creator path) and a `'LEAD'` (template path) at once. V4 therefore (a) maps any value outside `lead`/`member` case-insensitively to `member`, (b) demotes duplicate leads *case-insensitively* keeping the earliest `(created_at, id)`, (c) only then lower-cases, (d) adds the CHECK with the catalog guard scoped to `current_schema()` per `backend/db/BASELINE.md`.

**Consequences**:

- Positive:
  - G-D11 a, G-D13, N-1 and the double-recipient half of G-B13 close from one cause.
  - The Diary fee-earner fallback (613A) and `ProjectSetupStatusService` "team assigned" step start reading real rows.
  - `AssignMemberActionExecutor` / `CreateTaskActionExecutor` `PROJECT_OWNER` gains its first test against a real lead row (none exists today — R1 §3).
  - A new integration test instantiates a template with a chosen lead and asserts `MatterListRow.feeEarnerId`, `ProjectAccessService.canEdit`, lead notification recipients and `PROJECT_OWNER` resolution through the real service path (the capstone lesson: `MatterListRowIntegrationTest` was green because it never instantiated a template).
- Negative:
  - `InstantiateTemplateIntegrationTest:277` flips to `Roles.PROJECT_LEAD`.
  - `AutomationEndToEndTest:102` and `NotificationServiceIntegrationTest:405,408` (`"MEMBER"`) seeds change.
  - V4 is shared with whichever units merge before the first persistent apply — [ADR-347](ADR-347-fee-note-due-date-and-balance-model.md), [ADR-353](ADR-353-firm-letterhead-settings.md), [ADR-354](ADR-354-persisted-retention-end-date.md) and the U15/U23/U24 columns are the candidates; once any persistent stack (Keycloak dev DB, re-walk tenant, VPS) has applied V4, later units take V5, V6, … (next free number).
  - The first unit to merge creates it and the migration needs its own targeted test (mixed-case rows, a `CONTRIBUTOR` row, a duplicate-lead project) since `BaselineSchemaParityTest` is pinned to V1 and must not be re-recorded.
  - Demo-seeded tenants gain a lead where none existed (first seeded member).
  - `DemoDataSeederIntegrationTest` is re-run.
  - `McpMatterDto` javadoc ("LEAD/MEMBER/…") is corrected.
  - The MCP contract-pinning test ([ADR-343](ADR-343-mcp-contract-pinning-test.md)) is re-run, not re-pinned.
- Related:
  - [ADR-346](ADR-346-matter-visibility-and-team-management.md) (the membership path that makes these rows exist), [ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md) (audit `{"role":"lead"}` literals unchanged), `backend/db/BASELINE.md` (migration rules), memory `feedback_migration_numbers_next_free`.
