# ADR-245: Localized Profile as Derivative of Generic Profile

**Status**: Accepted

**Context**:

The vertical profile registry (ADR-181, ADR-189) currently holds three profiles: `accounting-za`, `legal-za`, and `consulting-generic`. Phase 66 adds a fourth, `consulting-za`, creating a situation where two profiles serve the same vertical at different localisation levels — a country-agnostic generic baseline (`consulting-generic`) and a South-African variant (`consulting-za`) that carries ZAR rates, VAT 15%, en-ZA terminology, and SA-specific cultural defaults.

The design question is how these profiles should relate to each other. The platform expects more country variants as the product expands: `consulting-uk`, `consulting-us`, `consulting-au`, and parallel forks for accounting and legal once the product leaves South Africa. Without a clear coexistence model, the registry will either (a) fill up with near-duplicate profiles that copy-paste 80% of the generic pack content and accrue drift, or (b) collapse into a single profile with tenant-level overrides that obscure which content is universal and which is country-specific.

A second concern is discoverability. Platform admins provisioning a new tenant pick a profile from a list. If the list shows five consulting profiles (`consulting-generic`, `consulting-za`, `consulting-uk`, etc.), the naming convention needs to make it obvious which variant a given tenant should pick, and what the generic baseline means if it's still offered.

A third concern is long-term divergence. South African VAT is 15%; UK VAT is 20%; US has no VAT. If every country variant is a copy of the generic, maintaining the generic becomes a chore — every pack-content update has to be manually mirrored across country variants. Conversely, if country variants layer on top of a shared generic baseline, the layering needs a well-understood semantic.

**Options Considered**:

1. **Single generic profile with per-tenant locale / currency overrides** — no country variants; every tenant picks `consulting-generic` and sets locale/currency/tax settings on the tenant record.
   - Pros:
     - One profile to maintain; zero drift
     - Simplest registry
     - Per-tenant flexibility for edge cases (a multinational agency with offices in SA + UK)
   - Cons:
     - Pack content must be locale-neutral — no ZAR rates, no VAT 15% default, no en-ZA terminology, no SA-specific compliance references
     - Tenants must manually configure dozens of settings on day one; defeats the "demo-ready in one click" promise
     - Cannot express country-specific compliance packs (FICA, POPIA) because the profile has no country affinity
     - Forces every country's cultural assumptions into tenant-level config rather than shared defaults

2. **Country variants coexist with a generic baseline, explicit layering via pack references** — `{vertical}-generic` is the universal baseline; `{vertical}-{country}` profiles are derivatives that reference the generic's pack content where it's universal and override with country-specific packs where it isn't.
   - Pros:
     - Universal pack content (e.g., common project templates, standard clauses) lives in generic and is maintained once
     - Country variants are small — they override only what's locale-specific (rates, VAT, compliance, terminology)
     - Generic profile remains usable as a fallback for international tenants or early-access pilots
     - Naming pattern `{vertical}-{country-code}` is self-documenting
     - Future verticals that start generic can graduate to country variants without breaking existing tenants
   - Cons:
     - Two profiles to document per vertical once country variants exist
     - Platform admins must understand the difference at provisioning time
     - Pack-layering semantics need to be documented — which packs are additive, which are replacements

3. **Replace generic with country-specific whenever a country variant exists** — `consulting-generic` is retired the moment `consulting-za` ships; future country variants stand alone with no shared baseline.
   - Pros:
     - No layering model to maintain
     - Each country profile is self-contained and easy to reason about
   - Cons:
     - Content duplication: every country profile copies the generic pack content and then overrides locale-specific bits
     - Drift: an update to one country's project template pack doesn't propagate to the others
     - No fallback profile for international or single-tenant pilots
     - Retiring `consulting-generic` invalidates any test tenants or demos built against it

**Decision**: Option 2 — country variants coexist with a generic baseline, with explicit layering via pack references.

**Rationale**:

**Preserves the generic baseline for international expansion.** When Kazi adds its first UK consulting tenant, `consulting-generic` remains a valid starting point. The UK variant can be added later without disturbing the SA variant. Keeping the generic alive also keeps a country-neutral reference profile useful for testing pack loaders, running demos for prospects who haven't disclosed their country, and bootstrapping future forks.

**Explicit locale layering.** Country variants override exactly the locale-sensitive pack types — rate pack (currency), document templates (VAT number, local terminology), compliance pack (FICA / SARS / CIPC for SA, HMRC for UK, IRS for US), terminology overrides (en-ZA vs. en-GB spellings), and tax defaults. Pack types that are locale-neutral (field definitions for `campaign_type`, `deliverable_type`, the creative brief questionnaire, the 8 standard agency clauses) can either be duplicated (cheap, frozen) or referenced from the generic (harder to evolve but zero-drift).

**Naming pattern.** `{vertical}-{country-code}` layers over `{vertical}-generic`. Country codes use ISO-3166-1 alpha-2 (`za`, `uk`, `us`, `au`, `nz`, `ke`, `ng`). Generic is always the suffix `-generic`. This gives a predictable provisioning UX: SA agency picks `consulting-za`; an international or untyped tenant picks `consulting-generic`; UK will pick `consulting-uk` when it exists.

**Phase 66 specifics.** `consulting-za` duplicates rather than references generic pack content for the slices it overrides — this is the pragmatic v1 choice because the pack loaders do not yet support pack-reference chains. Future platform work can introduce a shared-pack mechanism (an explicit `inherits: "consulting-generic"` field in the manifest) once a second country variant exists and the drift cost is real. Until then, each country variant is self-contained but explicitly names the generic as its ancestor in documentation and status files.

**Multi-tenant cost.** Because pack content is classpath-resident and organisation-agnostic, having `consulting-generic` and `consulting-za` coexist costs only disk space in the JAR. No per-tenant duplication.

**Consequences**:

- `consulting-generic` is preserved, unchanged; `consulting-za` ships alongside it and is the preferred profile for SA agencies.
- Country variants follow the naming pattern `{vertical}-{country-code}`.
- The profile-picker UI (platform-admin provisioning) shows both profiles when they exist. Picking guidance: SA tenant → `consulting-za`; international or unknown → `consulting-generic`.
- Layering is currently implicit — country variants duplicate rather than reference the generic's pack content — but the naming convention leaves room for explicit layering later without breaking existing profiles.
- When the second country variant (`consulting-uk` etc.) lands, a pack-reference mechanism should be considered in its own ADR; until then, duplication is the lower-risk choice.
- Country-specific compliance packs (FICA for SA, equivalents elsewhere) live on the country variant, never on the generic.
- Tax defaults, locale, currency, and terminology key always live on the country variant — the generic is locale-neutral.
- Related: [ADR-181](ADR-181-vertical-profile-structure.md), [ADR-189](ADR-189-vertical-profile-storage.md), [ADR-184](ADR-184-vertical-scoped-pack-filtering.md), [ADR-185](ADR-185-terminology-switching-approach.md), [ADR-244](ADR-244-pack-only-vertical-profiles.md).
