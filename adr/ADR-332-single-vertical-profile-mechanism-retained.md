# ADR-332: Single-Vertical Stance — Keep the Profile Mechanism with One `legal-za` Profile, No Picker, Registry Trimmed to 8 Modules

**Status**: Accepted

**Context**:

Phase 49 introduced vertical profiles (`vertical-profiles/*.json` loaded by `VerticalProfileRegistry`; ADR-181 structure, ADR-189 storage), a hardcoded `VerticalModuleRegistry` with `VERTICAL` and `HORIZONTAL` module definitions, `VerticalModuleGuard`, and profile-driven `enabled_modules` defaults at provisioning (ADR-192). Phase 62 unified gating with category metadata so HORIZONTAL modules are admin-toggled in Settings → Features (ADR-239). Phase 65 made pack-only verticals first-class (ADR-244). Profiles are chosen at provisioning (`ProvisioningController` `verticalProfile`), by `platform-admin/demo`, and by the access-request industry map.

Scouting found **four** profiles, not the three the requirements listed (`consulting-generic.json` also exists; `architecture/phase86-subtraction-legal-only.md` §11.3.10(a)), and 12 registered modules — 8 VERTICAL / 4 HORIZONTAL — of which four are enabled by no legal profile: `regulatory_deadlines` (accounting SARS/CIPC), `resource_planning` (consulting), `retainer_agreements` (also listed for legal-za but deleted with `retainer` in slice 4), `automation_builder` (accounting/consulting; legal-za never enabled it, so legal firms cannot see or disable any rule today — §11.3.10(i)).

The founder decision (`requirements/claude-code-prompt-phase86.md`): **legal-only, single profile** — delete `accounting-za` and `consulting-za` and everything only they enable; whether the *mechanism* survives is an `/architecture` call, "the bias is keep the JSON mechanism, one profile, no profile picker in the UI, because removal is churn with no user-visible benefit." Phase 87 (legal-native shell) owns the terminology-map mechanism and any further collapse.

Two places where the doc and the requirements diverge, doc wins: the requirements' scouting note lists `disbursements` under HORIZONTAL; the doc places it VERTICAL (§11.3.9, 6 VERTICAL: `trust_accounting, court_calendar, conflict_check, lssa_tariff, disbursements, matter_closure`). And the doc's rewritten `VerticalProfileRegistryTest` expects nine `enabledModules` for `legal-za` including `deadlines` (the portal module id kept in `legal-za.json`), while the registry after slice 10 defines eight modules — `deadlines` is a profile/portal module id, not a `VerticalModuleRegistry` entry, and the slug-validation test's `KNOWN_UNSEEDED_DEFAULTS` becomes `Set.of()`. The builder must reconcile this at slice 10 (§11.3.9 "Registry tests").

**Options Considered**:

### A. The profile mechanism

1. **Keep the JSON mechanism with a single `legal-za` entry and no picker (CHOSEN)** — `VerticalProfileRegistry` loads one profile; `ProvisioningController` / `OrgProvisioningService` default `verticalProfile` to `legal-za` and reject any other id with 400 (not a silent fallback); `AccessRequestApprovalService.INDUSTRY_TO_PROFILE` maps every industry to `legal-za`; `demo-provision-form.tsx` collapses to a fixed value; `VerticalProfileReconciliationSeeder` and `PackReconciliationRunner` trimmed to surviving pack types.
   - Pros: no user-visible benefit from collapsing, so no churn in provisioning, demo, access-request and seeder code that works today; `legal-za.json` remains the single declarative statement of what a law-firm tenant gets (modules, packs, terminology namespace); provisioning stays idempotent (ADR-T007) with the same pipeline; Phase 87 can collapse it later on a small codebase with the legal-native shell in hand.
   - Cons: a one-entry registry is a mechanism with no second case — readers will wonder why it exists; the terminology map keeps a profile key until Phase 87; `useProfile.ts`, `terminology-text.tsx` and `promoted-field-slugs.ts` keep profile branches that always take the legal path.
2. **Collapse the mechanism now** — delete `VerticalProfileRegistry`, the profile JSON, the `verticalProfile` provisioning parameter and `VerticalModuleRegistry`; hard-code legal modules and packs.
   - Pros: least code; no dead abstraction.
   - Cons: touches provisioning, demo, access-request, packs reconciliation, module guard and every `@RequiresModule`-style gate in one phase that is already 12 slices; pre-empts Phase 87's shell decisions; the requirements explicitly bias against it ("churn with no user-visible benefit"); the Phase 65 install pipeline (ADR-240) keys pack filtering on the profile (ADR-184) and would need a rewrite for zero gain.
3. **Keep all four profiles, hide the non-legal ones** (gate the picker, keep the JSON and packs).
   - Pros: no deletion risk; resurrection is a UI change.
   - Cons: forbidden by "Delete, don't disable"; the non-legal packs, seeders and ~20 pack tests keep running in every full verify; `MultiVerticalCoexistenceTest` and the consulting Playwright config keep costing CI minutes for a product with one vertical.
4. **Keep the mechanism *and* the picker with one option.**
   - Pros: nothing to change in the demo form or access-request industry list.
   - Cons: a single-option picker is a UI lie; the access-request form would still list accounting/agency industries that resolve to a law-firm tenant.

### B. The module registry

1. **Remove the four modules nothing legal uses; 8 remain, only `bulk_billing` and `information_requests` toggleable (CHOSEN)** — VERTICAL `trust_accounting, court_calendar, conflict_check, lssa_tariff, disbursements, matter_closure` (all default-on for `legal-za`); HORIZONTAL `bulk_billing, information_requests` (`defaultEnabledFor → List.of("legal-za")`); `VerticalModuleGuard` unchanged; `VerticalModuleRegistryTest` 12 → 8, horizontal 2, vertical 6.
   - Pros: Settings → Features keeps a real purpose (two admin toggles a firm may genuinely want off); `VerticalModuleGuard` and the ADR-239 category model survive unchanged; removing a module is a registry-row deletion plus its nav item, not a mechanism change.
   - Cons: eight definitions for one profile is still a registry with one consumer; `regulatory_deadlines` leaves with the whole `deadline/` package (§11.3.10(c)), so the staff `/deadlines` route goes while the portal `deadlines` feed (custom-field dates via `FieldDateApproachingEvent`) stays — two things called "deadlines" with different fates.
2. **Make every surviving module always-on; delete the HORIZONTAL category and Settings → Features.**
   - Pros: fewer concepts; no toggles to test.
   - Cons: a firm that does not run bulk billing or information requests has no way to hide them; reverses ADR-239 for no requirement; Phase 87's settings consolidation is the right place to decide that.
3. **Keep all 12 definitions, including the four orphaned ones.**
   - Pros: zero registry edits.
   - Cons: `resource_planning`, `retainer_agreements` and `regulatory_deadlines` would gate routes that no longer exist; `automation_builder` would gate a page that ADR-334 makes always-on; the registry test would pin dead rows.

**Decision**: Keep the vertical-profile JSON mechanism with `legal-za` as the only profile and no picker anywhere (A1); trim `VerticalModuleRegistry` to 8 modules — 6 VERTICAL, 2 HORIZONTAL (`bulk_billing`, `information_requests`) — and keep `VerticalModuleGuard` and Settings → Features for those two toggles (B1). Any further collapse is Phase 87's call once the shell is legal-native.

**Rationale**:

1. **The founder's bias is the cheapest correct answer.** Removal of the mechanism is churn in provisioning, demo, access-request and packs code that already works, in a phase whose risk budget is spent on deletions and a migration squash. Deleting the *entries* delivers the user-visible result (no accounting/agency anywhere) without touching the mechanism.
2. **Zero production tenants makes the profile deletions clean.** No tenant was ever provisioned as `accounting-za` or `consulting-za` outside dev volumes, so `enabled_modules` rows, pack installs and `org_settings.*_pack_status` columns for them simply are dropped by the owning slice's throwaway migration and omitted from the squashed baseline (ADR-331) (ADR-331); nothing needs a data migration.
3. **The design-partner GTM sells one product.** Every pilot firm is a South African law firm; a profile picker, an industry list with accountants on it, or a demo form offering "consulting" is at best noise and at worst a credibility problem in a sales demo.
4. **Full-verify cost.** The non-legal profiles drag ~20 pack tests, `MultiVerticalCoexistenceTest`, `TrustCoexistenceTest`, the consulting Playwright config and two QA demo scenarios through every verify. Deleting the entries removes that; deleting the mechanism would not remove more.
5. **Phase 87 is the right owner of the collapse.** It renames the wire and deletes the terminology-map mechanism; whether `VerticalProfileRegistry` survives that is best decided with the legal-native shell in hand, not before.

**Consequences**:

- Positive: `vertical-profiles/legal-za.json` is the one place that says what a tenant gets; unknown profile ids are a 400 at provisioning (`VerticalProfileIntegrationTest` rewritten to assert it); `VerticalProfileRegistryTest` asserts `legal-za` with `terminologyNamespace "en-ZA-legal"` and drops the `rateCardDefaults` cases.
- Positive: `TrustCoexistenceTest` becomes trivially true (trust tables exist in every tenant) and is rewritten rather than deleted; module gating still governs the six legal modules and two toggles (§11.9).
- Negative: a one-entry registry and profile-keyed terminology map persist until Phase 87; three frontend files keep profile branches with one live path.
- Negative: `regulatory_deadlines`, `FilingStatus`, the 8 SARS/CIPC deadline types and the staff `/deadlines` route are gone; a law firm wanting statutory filing reminders has only custom-field dates (portal `deadlines`) and court dates until a legal-flavoured equivalent is specced.
- Negative: `RatePackSeeder` and `org_settings.rate_pack_status` go if `MemberRateSeedingIntegrationTest` shows no legal use (verify at build); `OrgSettingsSchemaSnapshotTest` takes a fifth authorised pin update if so.
- Resolved: `deadlines` is `PortalDeadlineService.MODULE_ID`, deliberately absent from the registry (`OrgSettingsService.java:475-476`, LZKC-026); `legal-za.json` keeping it in `enabledModules` beside an 8-entry registry is today's passing state, and `VerticalProfileModuleSlugValidationTest` checks registry → profile only. No reconciliation needed, not the test.
- Related: [ADR-181](ADR-181-vertical-profile-structure.md), [ADR-189](ADR-189-vertical-profile-storage.md), [ADR-192](ADR-192-enabled-modules-authority.md) (Phase 49 mechanism retained), [ADR-190](ADR-190-module-guard-granularity.md) (guard unchanged), [ADR-239](ADR-239-horizontal-vs-vertical-module-gating.md) (Phase 62 categories retained with two HORIZONTAL rows), [ADR-184](ADR-184-vertical-scoped-pack-filtering.md) / [ADR-240](ADR-240-unified-pack-catalog-install-pipeline.md) (Phase 65 packs catalogue — the install path for legal content), [ADR-197](ADR-197-calculated-vs-stored-deadlines.md) / [ADR-199](ADR-199-filing-status-lazy-creation.md) (Phase 51 regulatory deadlines, superseded via ADR-330), [ADR-330](ADR-330-archive-tag-not-fork.md).
