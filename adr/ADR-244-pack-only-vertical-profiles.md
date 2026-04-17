# ADR-244: Pack-Only Vertical Profiles

**Status**: Accepted

**Context**:

Kazi supports vertical profiles through a combination of backend modules and content packs (Phase 49, ADRs 181/189/192/239). Two existing verticals — `legal-za` and `accounting-za` — activate backend modules: `legal-za` enables a trust-accounting ledger, court calendar, conflict-check, and LSSA tariff; `accounting-za` enables a SARS-filing calendar and FICA compliance engine. Each vertical also ships field packs, document templates, automation rules, clauses, requests, and terminology overrides on top of its module stack.

Phase 66 introduces a third demo rail, `consulting-za`, for South-African digital agencies and creative studios. The design question is whether consulting should follow the legal/accounting precedent and ship with its own backend module (say, a `consulting` module gating a utilization dashboard, retainer-specific screens, and agency reports), or whether it can ship as pure pack content with optional profile-gated UI.

The decision matters beyond this phase. The platform expects to support more verticals over time — recruitment, property management, healthcare practice management, engineering consulting — and needs a consistent rule for when a vertical warrants a backend module and when it does not. Adding modules speculatively produces module sprawl, where every vertical has a token backend module that does nothing but register "this profile is active". Omitting modules when a vertical genuinely needs new primitives produces leaky JSONB shapes and missing transactional guarantees.

The consulting domain helps clarify the rule. Its operational model — project, retainer, time entry, proposal, invoice, utilization, profitability report — is already fully expressible in the horizontal stack. There is no ledger (like trust accounting) with unique invariants, no regulated deadline calendar (like SARS), no compliance regime with bespoke entities. Every agency concept fits inside existing entities plus custom-field JSONB (`campaign_type`, `retainer_tier`, etc.).

**Options Considered**:

1. **Always add a vertical-specific backend module for every vertical** — consistent rule, no judgement calls required.
   - Pros:
     - Uniform profile shape; every `{vertical}-{country}` has a matching `{vertical}` module
     - Easier to explain to new engineers
     - Module gate primitive works for every vertical uniformly
   - Cons:
     - Module sprawl: each empty module still costs a package, a `@Configuration`, tests, and cognitive overhead
     - Tempts developers to push pack content (field definitions, template references) into module code instead of pack JSON — undermining the pack-only seam
     - Breaks the pack-only demo path; you can no longer turn on `consulting-za` purely by dropping pack files into the classpath

2. **Pack-only vertical when operational model fits existing entities** — decision rule: horizontal stack expressive enough → pack-only; vertical primitive required → module-bearing.
   - Pros:
     - Matches reality: legal and accounting genuinely have primitives (ledger, regulated calendar) that need entity-level modelling; consulting does not
     - Keeps pack-only as a real first-class path — future verticals can ship as content-only with no Java code
     - Prevents empty modules and the "module exists to gate a widget" anti-pattern
     - Cheaper to add new verticals when they fit
   - Cons:
     - Requires a judgement call per vertical
     - Two profile shapes to document: module-bearing and pack-only
     - Risk of belatedly discovering a vertical needs a module after it has been shipped pack-only (mitigated by the ability to add a module later — pack content is already authored)

3. **Start with a module stub "just in case" and delete if unused** — add a module for every new vertical, mark it as provisional, and delete it if it stays empty after a couple of phases.
   - Pros:
     - Preserves optionality
     - If unique primitives are discovered mid-build, the module is already there
   - Cons:
     - Provisional modules rarely get deleted in practice — they accrete tests and imports
     - Creates technical debt on day one
     - Works against YAGNI

**Decision**: Option 2 — pack-only verticals are a first-class shape. A vertical gets a backend module only when it introduces primitives the horizontal stack cannot express (new entities, unique invariants, regulated transactional boundaries). Otherwise, the vertical ships pack-only: JSON pack content plus terminology overrides plus optional profile-gated UI widgets.

**Rationale**:

**Legal needed a module.** A trust-accounting ledger is not JSONB — it has its own transactional semantics (deposit, withdrawal, interest accrual, statutory reporting), referential integrity against matters, and compliance audit obligations. Attempting to model it as custom fields on a Payment entity would leak the trust invariants throughout the codebase and make the compliance audit trail unreliable.

**Accounting needed a module.** SARS filing deadlines are a regulated calendar with per-entity-type, per-financial-year-end shapes. The deadline engine performs date math specific to South African tax law (provisional tax due dates, CIPC annual-return windows). Expressing this as custom-field metadata on generic calendar entries would lose the domain guarantees and prevent deadline-engine evolution independent of calendar generic code.

**Consulting does not.** Campaigns are projects. Briefs are information requests. Retainers use the existing `RetainerAgreement` primitive. Proposals use the existing `Proposal` primitive. Utilization is a horizontal feature Phase 38 already computes. Every "agency concept" maps to a horizontal entity plus a custom field — and adding a field costs one JSON slug, not a Java module. Adding a module just to host a team-utilization widget would be the tail wagging the dog.

**Multi-tenant cost.** Backend modules carry cross-tenant cost: every module loads in every tenant's classpath, every module's migrations run on every schema. A module only exists to serve tenants that activate it — but it must still compile, test, and migrate for everyone. Pack content has no equivalent cost; unused pack files are inert classpath resources.

**Country variants.** A future `consulting-uk` can fork the packs without touching any Java module. If consulting had shipped with a module, the UK fork would either share the module (awkward if UK has country-specific behaviours) or need a parallel module. Pack-only makes country variants trivial — the exact concern ADR-245 addresses.

**Consequences**:

- `consulting-za`'s profile manifest sets `"enabledModules": []`. No module is created for consulting.
- Pack-only is documented as a first-class vertical shape, not a shortcut — future vertical additions must justify *adding* a module, not justify omitting one.
- The rule "does the vertical introduce a primitive the horizontal stack cannot express?" becomes the gate for module creation. Answered with entities (does this need its own entity?), invariants (does this need transactional guarantees the horizontal stack doesn't provide?), and compliance (is there a regulatory calendar or ledger?).
- Profile-gated widgets (widgets that re-frame existing horizontal data with vertical-specific framing) use the `useProfile()` hook, not `<ModuleGate>`. This is captured in ADR-246.
- If a pack-only vertical is later found to need a module, the module can be added without disturbing existing pack content — adding a module is additive.
- Related: [ADR-181](ADR-181-vertical-profile-structure.md), [ADR-189](ADR-189-vertical-profile-storage.md), [ADR-192](ADR-192-enabled-modules-authority.md), [ADR-239](ADR-239-horizontal-vs-vertical-module-gating.md), [ADR-245](ADR-245-localized-profile-derivatives.md), [ADR-246](ADR-246-profile-gated-dashboard-widgets.md).
