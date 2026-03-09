# ADR-168: Message Catalog Strategy

**Status**: Accepted
**Date**: 2026-03-09
**Phase**: Phase 43 — UX Quality Pass

## Context

Phase 43 introduces empty states, contextual help tooltips, error messages, and onboarding copy across ~15 pages and ~50 distinct user-facing strings. Without a centralised string management strategy, these strings would be hardcoded inline in JSX — the current pattern across all existing components.

Hardcoded strings create several problems: copy cannot be reviewed or edited without touching component code, duplicate strings drift over time, and adding a second locale (Afrikaans, Zulu — relevant for the South African market) would require a file-by-file migration. The founder explicitly requested that strings be centralised with i18n-ready codes, not scattered across components.

The question is what infrastructure to use: a full i18n framework (next-intl, react-i18next), a lightweight custom solution (JSON files + custom hook), or a CMS/database-backed approach.

## Options Considered

1. **Plain JSON files + custom `useMessage` hook (chosen)** — Namespace-split JSON files in `frontend/src/messages/en/` (e.g., `empty-states.json`, `help.json`, `errors.json`). A thin `useMessage(namespace)` hook returns a `t(code, interpolations?)` function. No framework dependency. `{{variable}}` interpolation matches the platform's template variable syntax.
   - Pros: Zero dependencies, full control over behaviour, trivial to understand (JSON lookup + string replace), compatible with any i18n library added later (same file structure), fast (static imports, no runtime fetching), dev-mode console warnings for missing keys aid development, interpolation syntax matches existing `{{entity.field}}` template convention
   - Cons: No pluralisation support (not needed yet), no ICU message format, no built-in locale negotiation, must manually add features if needs grow, no editor tooling (VS Code i18n extensions won't recognise custom format)

2. **next-intl** — The de facto i18n library for Next.js App Router. Provides `useTranslations()`, namespace support, pluralisation, ICU message format, middleware-based locale detection, and server component support.
   - Pros: Full-featured, well-maintained, excellent Next.js integration, built-in pluralisation and formatting, server component support, VS Code extension for key autocompletion
   - Cons: Adds a dependency for a feature set mostly unused (only English, no pluralisation needed), requires middleware configuration (locale routing), opinionated file structure may conflict with project conventions, heavier than needed — framework lock-in for what amounts to a JSON lookup, would need to configure even though locale switching is explicitly out of scope

3. **Database-backed CMS** — Store strings in a `messages` table (or use a headless CMS like Contentful). Admin-editable via a settings UI. Frontend fetches messages at runtime or build time.
   - Pros: Admin-editable without code deploys, supports per-tenant customisation (whitelabeling), runtime updates
   - Cons: Massive over-engineering for the current need, adds API latency or build complexity, requires admin UI, database storage for static content is wasteful, no current requirement for admin-editable copy, tenant customisation of error messages and help text is not a real user need

## Decision

Use **plain JSON files + custom `useMessage` hook** (Option 1). Message files live in `frontend/src/messages/en/` with one file per namespace. The hook accepts a namespace string, statically imports the corresponding JSON, and returns a `t()` function that performs key lookup with `{{variable}}` interpolation.

## Rationale

The platform needs a string catalog, not an internationalisation framework. The current requirement is: centralise strings so they're not inline in JSX, use codes so copy is grep-able, and structure files so adding a locale later is a matter of adding a parallel directory (e.g., `messages/af/`).

next-intl (Option 2) is the obvious "proper" choice for a Next.js app, but it introduces configuration overhead (middleware, providers, locale routing) for features that won't be used. The custom hook is ~30 lines of code, does exactly what's needed, and can be replaced by next-intl later by changing the hook implementation without touching any call sites — the `t(code)` interface is identical.

A CMS (Option 3) solves a problem that doesn't exist. Help text and error messages are product-defined, not user-customisable. If tenant-specific copy is ever needed, it would be a much more targeted solution (e.g., branding fields on OrgSettings) rather than a general-purpose CMS.

## Consequences

- **Positive**: All Phase 43 strings are centralised and grep-able by code
- **Positive**: Adding a locale is a mechanical task: copy `en/` to `af/`, translate values, add a locale parameter to the hook
- **Positive**: Zero new dependencies — no bundle size impact
- **Positive**: Dev-mode missing-key warnings catch typos during development
- **Negative**: No pluralisation — if needed later, must either add it to the custom hook or migrate to next-intl
- **Negative**: No editor tooling for key autocompletion — developers must reference the JSON files manually
- **Neutral**: Existing hardcoded strings are NOT migrated — only new Phase 43 strings use the catalog. Migration is incremental across future phases
