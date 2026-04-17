# ADR-246: Profile-Gated Dashboard Widgets

**Status**: Accepted

**Context**:

Phase 66 adds a single piece of UI: a team-utilization widget on the company dashboard. It reads existing Phase 38 `UtilizationService` data — a horizontal feature available to every tenant — and surfaces `teamAverages.avgBillableUtilizationPct` as a KPI card with a 4-week sparkline trend. The widget is intended to be visible only when the active vertical profile is `consulting-za`, because agencies value team billable utilization as a primary operating metric while legal and accounting firms tend to use matter- or deadline-centric KPIs instead.

The mechanical question is how to gate the widget. Kazi has two existing gating primitives:

- **`<ModuleGate>`** — checks `VerticalModuleRegistry` for an enabled module. Used throughout the frontend to hide backend-module-dependent surfaces (trust accounting screens for non-legal tenants, SARS calendar for non-accounting tenants).
- **Profile check (`OrgProfileProvider` + ad-hoc profile ID comparisons)** — reads the active profile ID from context. No first-class hook today; each call site duplicates the boilerplate.

Using `<ModuleGate>` for the utilization widget would require creating a `consulting` or `agency-utilization` module purely to drive the gate — an empty module whose only purpose is to return `true` under `consulting-za`. This is exactly the module sprawl ADR-244 decided against. On the other hand, leaving the widget completely ungated means every profile sees a KPI that doesn't match how non-consulting verticals think about their work, adding visual clutter and implying a priority the tenant doesn't share.

A third option is to ship the widget unconditionally and rely on the dashboard's per-tenant customization to hide it — but customization is a user-action, not a defaults story, and founders of legal/accounting tenants would see a consulting-shaped dashboard on first login. The goal is sensible defaults keyed to the profile.

**Options Considered**:

1. **Create a `consulting` module to gate the widget, same pattern as legal/accounting modules** — treat profile-specific UI surfacings as module territory.
   - Pros:
     - Reuses the existing `<ModuleGate>` primitive; one gating story across the app
     - Module registry is the single source of truth for "is this feature active"
     - Consistent with how legal/accounting modules gate their own dashboard widgets
   - Cons:
     - Requires an empty backend module — no entities, no services, no controllers, just a registration
     - Module sprawl: every piece of profile-specific UI becomes a module, even when the UI is re-framing existing horizontal data
     - Contradicts ADR-244 (`consulting-za` has `enabledModules: []` by design)
     - Raises confusion: is `consulting` a module? It has no backend. Documentation has to explain why

2. **Profile-gate the widget via a small `useProfile()` hook — no new module** — introduce a first-class `useProfile()` helper that reads the active profile ID from `OrgProfileProvider` and lets the widget render conditionally.
   - Pros:
     - Aligns with ADR-244: modules are for backend capabilities, profiles gate UI framing
     - One tiny hook; no backend changes
     - Makes profile-specific UI cheap for future phases — every vertical gets to add re-framed horizontal widgets without needing a backend module
     - Keeps `<ModuleGate>` semantically clean (it's about backend capabilities being on/off)
     - Distinguishes "the data exists and everyone has it" (horizontal feature) from "the data comes from a module that's enabled per tenant" (vertical capability)
   - Cons:
     - Two gating primitives to understand (`<ModuleGate>` for modules, `useProfile()` for profile-specific framing)
     - Engineers must pick the correct gate for the correct situation; wrong choice will work but will drift the architecture
     - Requires a small conceptual distinction to be documented

3. **Ship the widget unconditionally; users hide via dashboard settings** — render for all profiles and rely on user action to suppress.
   - Pros:
     - No gating logic at all
     - Users who value utilization see it regardless of profile
     - Simplest implementation
   - Cons:
     - Wrong defaults for legal and accounting tenants; they see an agency KPI on first login
     - Assumes tenants customise their dashboards, which many never do
     - Mixes vertical semantics on one dashboard; the "company dashboard" stops being vertical-shaped
     - Counter to the Phase 64 lesson that sensible defaults matter more than exposed toggles

**Decision**: Option 2 — profile-gate the widget via a small `useProfile()` hook; no new module.

**Rationale**:

**Modules are for backend capabilities; profiles are for UI framing.** ADR-244 nailed the module semantic down: a module exists when a vertical introduces a backend primitive the horizontal stack can't express. The utilization widget is the opposite — it surfaces existing horizontal data with vertical-specific framing. Gating it via a module would force us to create a backend-less module solely to drive a frontend gate, undoing ADR-244 the same day it ships.

**Small, reusable pattern.** A `useProfile()` hook is cheap. It reads `OrgProfileProvider` context and returns the active profile ID. Any future widget that wants to surface horizontal data with vertical-specific framing can use the same hook. This is a pattern we expect to reuse as more verticals ship:

- A matter-load widget for `legal-za` (re-framing existing project-count data)
- A filing-pipeline widget for `accounting-za` (re-framing existing deadline data)
- A headcount-velocity widget for a future recruitment vertical (re-framing existing member-activity data)

Each of these is a re-skinning of horizontal data, not a new capability. Profile-gating is the right seam.

**Clear semantic separation.** With Option 2, the rule is:

- *Backend capability visible / hidden per tenant?* → use `<ModuleGate>`
- *Horizontal feature re-framed for a specific profile's worldview?* → use `useProfile()`

This keeps `<ModuleGate>` honest. A `<ModuleGate>` check that returns `true` for `consulting-za` despite no actual module existing would be a lie in the type system.

**Discoverability for other engineers.** Widgets adopting profile-gating are visually distinct in the code: a leading `if (profile !== "consulting-za") return null;` (or equivalent `<ProfileGate profile="...">`) reads as a UI-level decision, not a capability decision.

**Multi-tenant cost.** Zero. The hook reads existing context; there's no backend change, no registry change, no per-tenant cost.

**Consequences**:

- A new frontend hook `useProfile()` lives at `frontend/lib/hooks/useProfile.ts` (if not already present); it reads from `OrgProfileProvider` and returns the profile ID.
- The team-utilization widget renders conditionally on `profile === "consulting-za"`; other profiles see nothing.
- Future profile-specific dashboard widgets follow the same pattern. A small `<ProfileGate>` wrapper component may emerge once 2–3 widgets adopt the hook; optional and out of scope for Phase 66.
- Documentation (frontend CLAUDE.md or a component README) spells out the rule: `<ModuleGate>` for modules, `useProfile()` for profile-specific framing.
- `<ModuleGate>` remains unchanged; this ADR does not modify its behaviour. It continues to gate backend-module-dependent surfaces unchanged.
- The `consulting-za` profile keeps `enabledModules: []` — no module is created for the widget.
- Related: [ADR-181](ADR-181-vertical-profile-structure.md), [ADR-192](ADR-192-enabled-modules-authority.md), [ADR-239](ADR-239-horizontal-vs-vertical-module-gating.md), [ADR-244](ADR-244-pack-only-vertical-profiles.md).
