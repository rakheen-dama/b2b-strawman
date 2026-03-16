# ADR-185: Terminology Switching Approach

**Status**: Accepted
**Date**: 2026-03-16
**Phase**: 48 (QA Gap Closure)
**Supersedes**: [ADR-182](ADR-182-terminology-override-mechanism.md) (Terminology Override Mechanism)

## Context

DocTeams is a multi-tenant platform serving different professional services verticals. The QA cycle with the `accounting-za` vertical revealed that platform terminology ("Projects," "Customers," "Proposals," "Rate Cards") does not match the language accounting practitioners use daily ("Engagements," "Clients," "Engagement Letters," "Fee Schedules"). This creates a cognitive translation layer that erodes the perception of the platform as purpose-built for accounting firms.

The scope is narrow: approximately 15 term pairs need to be overridden, and the overrides apply to navigation labels, page headings, breadcrumbs, and major button labels only. Deeply nested component text, form labels, tooltips, and error messages are out of scope for this phase.

The vertical profile identifier (`verticalProfile`) is already stored in `OrgSettings` and returned by the `GET /api/settings` endpoint. The frontend does not have `next-intl` installed — ADR-182 proposed per-vertical message directories with `next-intl` runtime merge, but that work was never implemented and `next-intl` was not added to the project. This ADR supersedes ADR-182 with a simpler approach that matches the current codebase state.

## Options Considered

### Option 1: Static map in frontend bundle

Store a `Record<string, Record<string, string>>` in a TypeScript file. The outer key is the vertical profile identifier (e.g., `"accounting-za"`), the inner key is the platform term. A `t(term)` function does a simple lookup: `TERMINOLOGY[profile]?.[term] ?? term`. The map is imported as a static module -- no network request, no async loading.

- **Pros:**
  - Zero latency -- the map is part of the JavaScript bundle, available synchronously
  - Zero backend changes -- `verticalProfile` is already available from the settings API
  - Trivial implementation: one file for the map, one React context for the `t()` function
  - Tree-shakeable: bundler can eliminate unused vertical maps in production
  - The `t(term)` function signature is compatible with `next-intl`'s `useTranslations()` pattern, making a future migration additive
  - Testable with simple unit tests: `expect(t('Projects')).toBe('Engagements')`
  - No coupling to any i18n library -- the platform remains free to choose later

- **Cons:**
  - Adding new verticals requires a code change and redeploy (not runtime-configurable)
  - No support for pluralization rules, gendered forms, or locale-specific formatting
  - The map grows linearly with each new vertical (though at ~15 terms per vertical, this is negligible)
  - Not suitable for full i18n (date formats, number formats, right-to-left support)

### Option 2: Runtime API lookup from OrgSettings

Store terminology overrides in a new JSONB column on `OrgSettings` (e.g., `terminology_overrides`). The frontend fetches overrides as part of the initial settings load. A `t(term)` function checks the overrides map.

- **Pros:**
  - Runtime-configurable: admins could customize terminology without a code change
  - Terminology stored alongside other org configuration -- single source of truth
  - Could support per-org customization beyond the vertical profile defaults

- **Cons:**
  - Adds a network dependency to term resolution (though it piggybacks on the existing settings fetch, which is already async)
  - Requires a database migration to add the JSONB column
  - Requires backend validation logic to prevent conflicting or circular overrides
  - Over-engineered for ~15 static terms that are vertical-specific, not org-specific
  - Admin UI needed for managing overrides -- additional frontend work
  - Risk of stale terminology if the settings fetch fails or is slow

### Option 3: Full `next-intl` integration

Install `next-intl`, configure locale detection from the vertical profile, create message bundles per locale/vertical, and use `useTranslations()` throughout the app.

- **Pros:**
  - Industry-standard i18n framework with full feature set (pluralization, interpolation, formatting)
  - Server-side rendering support in Next.js App Router
  - Future-proof: handles date/number formatting, right-to-left, and multi-locale support
  - Large community and ecosystem

- **Cons:**
  - Massive scope: requires wrapping every user-visible string in the app with `t()` (hundreds of files, not ~30-40)
  - `next-intl` configuration in App Router requires route segment config, middleware changes, and message file management
  - Significant bundle size increase for locale data
  - Vertical-specific terminology is not the same problem as multi-language i18n -- conflating them complicates both
  - Estimated 2-3x the implementation effort of Option 1, for the same Phase 48 outcome
  - Premature: DocTeams is English-only; multi-language is not on the roadmap

## Decision

**Option 1 -- Static map in frontend bundle.**

## Rationale

The terminology switching need is narrow and well-defined: ~15 term pairs, applied to ~30-40 surface-level locations, driven by a known set of vertical profiles. A static map solves this with zero latency, zero backend changes, and trivial implementation.

The `t(term)` function signature (`(term: string) => string`) is deliberately designed to be compatible with `next-intl`'s `useTranslations()` return type. If full i18n is needed in the future, the migration path is: install `next-intl`, move term definitions into message JSON files, replace the `TerminologyProvider` with `next-intl`'s `NextIntlClientProvider`, and the `t()` calls in components remain unchanged. The function signature acts as a seam.

Runtime API lookup (Option 2) solves a problem we don't have: per-org customization beyond the vertical profile. The accounting-za vertical needs "Projects" -> "Engagements" for all accounting firms, not just some. If per-org overrides become necessary, the static map can be extended to check org-level overrides from the settings response (already fetched) without changing the `t()` interface.

Full `next-intl` (Option 3) is the right answer for multi-language internationalization, but Phase 48 is not an i18n phase. Installing an i18n framework for 15 English-to-English term swaps is over-engineering. The framework can be introduced when actual multi-language support is needed, and the `t()` seam ensures backward compatibility.

## Consequences

- Terminology maps are static TypeScript -- adding a new vertical requires a code change and frontend redeploy.
- The `t()` function is synchronous and has no async or loading state. Components do not need Suspense boundaries for terminology.
- Only ~30-40 high-visibility locations are updated in Phase 48. Deeper application text remains in platform terms until a future i18n phase.
- The `TerminologyProvider` React context adds negligible overhead: one context value, one map lookup per `t()` call.
- New verticals can be added by appending to the `TERMINOLOGY` map -- no structural changes.
- Future `next-intl` migration is additive: the `t()` function signature is the compatibility contract.
