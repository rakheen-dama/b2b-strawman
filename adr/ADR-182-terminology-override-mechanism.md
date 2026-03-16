# ADR-182: Terminology Override Mechanism

**Status**: Superseded by [ADR-185](ADR-185-terminology-switching-approach.md)
**Date**: 2026-03-15

## Context

The DocTeams platform uses generic professional services terminology: "Projects", "Customers", "Tasks", "Rate Cards", "Proposals". For an accounting firm, the natural terms are different: "Engagements" instead of "Projects", "Clients" instead of "Customers", "Work Items" instead of "Tasks", "Fee Schedule" instead of "Rate Cards", "Engagement Letters" instead of "Proposals".

Phase 43 built the i18n infrastructure using `next-intl` with message files in `frontend/src/messages/en/`. The system supports locale switching (e.g., `en`, `en-ZA`) but does not support per-vertical terminology overrides within a locale. A South African accounting firm wants English (`en`) as their language but with accounting-specific terminology layered on top.

The question is how to define and apply vertical-specific terminology overrides without rebuilding the i18n system.

## Options Considered

### Option 1: Per-vertical message directory with runtime merge (chosen)

Create a directory `frontend/src/messages/en-ZA-accounting/` containing only the message keys that differ from the base `en` locale. At runtime, the message provider loads the base messages first, then merges the vertical override on top. The vertical identifier is stored as a configuration value (environment variable or org setting) and determines which override directory to load.

- **Pros**:
  - Minimal i18n infrastructure change — just a merge step in the message provider
  - Override files are small (only changed keys) — easy to review and maintain
  - Follows the same pattern as locale overrides (`en-ZA` overrides `en`)
  - Content is created now; runtime merge can be implemented when needed
- **Cons**:
  - Requires a code change in the message provider to support the merge (not available today)
  - The vertical identifier needs to be passed to the frontend somehow (env var, API response, cookie)
  - Build-time merge is simpler but prevents per-tenant vertical switching

### Option 2: Runtime term mapping table

Store terminology mappings in the database (`TerminologyOverride` entity: `originalTerm`, `overrideTerm`, `context`). The frontend fetches the overrides on app load and applies them via a wrapper around the `t()` function. Admins can edit overrides via Settings UI.

- **Pros**:
  - Per-tenant customisation — different tenants can have different terminology
  - Admin-editable without code deployment
  - No file system changes — purely database-driven
- **Cons**:
  - New entity, new API endpoint, new Settings UI page — significant production code
  - Extra API call on every page load (or cached with invalidation complexity)
  - Risk of inconsistency: some terms come from message files, others from database
  - Over-engineering for a QA phase — the goal is to test with accounting terminology, not build a term mapper

### Option 3: Build-time vertical bundles

Create separate Next.js builds per vertical. Each build has its own `messages/` directory with the correct terminology baked in. The deployment target determines which build is served.

- **Pros**:
  - No runtime overhead — terminology is resolved at build time
  - Complete isolation between verticals
  - Simpler frontend code — no merge logic needed
- **Cons**:
  - Multiple builds to maintain and deploy
  - Cannot serve multiple verticals from the same deployment
  - Contradicts the multi-tenant architecture — tenants on the same deployment should be configurable, not rebuilt
  - Does not scale beyond 2-3 verticals

## Decision

**Option 1: Per-vertical message directory with runtime merge.** Override files are created at `frontend/src/messages/en-ZA-accounting/` containing only the keys that differ. The runtime merge is documented as a future implementation step — the content is created now so that it is ready when the merge mechanism is built.

## Rationale

This phase creates content, not infrastructure. The terminology override files represent the *specification* of what an accounting firm's UI should say. Whether that specification is applied via runtime merge, build-time substitution, or a future term mapping service is an implementation detail that belongs in a follow-up phase.

Option 1 was chosen over Option 2 because a database-backed term mapper is production code that this QA phase explicitly avoids. Option 1 was chosen over Option 3 because build-time bundles contradict the multi-tenant model — a single deployment should serve both accounting and non-accounting tenants.

The key insight is that the override file set is small. The accounting vertical changes roughly 7 terms (Projects, Tasks, Customers, Proposals, Time Entries, Rate Cards, and their plurals/variants). This is perhaps 30-40 message keys out of hundreds. A directory with a single `common.json` file containing these overrides is trivial to create and maintain.

If the runtime merge turns out to be complex (e.g., `next-intl` does not support layered message sources natively), the gap analysis will document this, and the fix phase can evaluate whether Option 2 or a simpler approach (CSS-based label overrides, React context wrapper) is more pragmatic.

## Consequences

### Positive
- Terminology content is created and reviewed during this phase — ready for implementation
- Override file convention is established for future verticals (law, consulting, etc.)
- No production code risk — files are created but not loaded until the merge mechanism is built
- The gap report will document exactly what is needed to make the override work at runtime

### Negative
- The accounting terminology is not visible in the UI during this phase's QA execution — the agent and founder see generic terms
- The gap report will contain a `missing-feature` entry for "terminology override not applied"
- There is a risk that the override mechanism is never built if other priorities take precedence

### Neutral
- The override directory name (`en-ZA-accounting`) establishes a naming convention: `{locale}-{vertical}`
- The terminology mapping table (Section 1.2 of the requirements) serves as the specification for what keys to override
- If `next-intl` natively supports message fallback chains (base → locale → vertical), the implementation may be as simple as configuring the chain — no custom merge code needed
