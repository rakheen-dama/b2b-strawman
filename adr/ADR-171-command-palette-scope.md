# ADR-171: Command Palette Scope

**Status**: Accepted
**Date**: 2026-03-10
**Phase**: Phase 44 — Navigation Zones, Command Palette & Settings Modernization

## Context

Phase 44 adds a command palette (Cmd+K) for keyboard-first quick navigation. The palette needs a defined scope: what is searchable and how deep the search goes. The fundamental tension is between a lightweight page navigator (fast to build, no backend dependency) and a full entity search that can find specific projects, customers, and invoices by name (requires a backend search endpoint or client-side cache of all entities).

The platform has ~16 navigable pages, ~23 settings pages, and potentially hundreds of projects, customers, and invoices per tenant. A page-only search covers the first two categories. Entity search would require either pre-fetching entity lists on palette open or adding a backend search-as-you-type endpoint.

This is a frontend-only phase with no backend changes. Any solution that requires new API endpoints is out of scope for the initial delivery.

## Options Considered

### 1. **Page-only search with optional recent-items cache (chosen)** — Index nav pages and settings; optionally show last 5 visited entities from an in-memory cache

- Pros:
  - Zero backend dependency — purely frontend
  - Fast implementation using cmdk's built-in fuzzy filter
  - Covers the primary use case: "I know which page I want, let me jump there"
  - Recent items add entity-level navigation without API calls
  - Clean upgrade path to full search later
- Cons:
  - Cannot find a specific project or customer by name (only recently visited ones)
  - Users familiar with Cmd+K in tools like Linear may expect entity search

### 2. **Full entity search with backend endpoint** — Palette queries a search API as the user types, returning matching projects, customers, invoices

- Pros:
  - Most powerful user experience — find anything from anywhere
  - Matches expectations set by Linear, Notion, GitHub
- Cons:
  - Requires a new backend search endpoint (violates frontend-only constraint)
  - Search-as-you-type needs debouncing, loading states, error handling
  - Backend must index entity names across the tenant schema — potential performance concern
  - Significantly larger scope than a navigation palette

### 3. **Client-side entity cache** — Pre-fetch all project and customer names on palette open, search client-side

- Pros:
  - No search endpoint needed — uses existing list APIs
  - Entity search works without backend changes
- Cons:
  - Fetching all entities on every palette open is expensive for large tenants (hundreds of projects)
  - Stale data if entities were created/renamed in another tab
  - Memory pressure from caching entity lists
  - Loading delay before palette is usable (bad UX for a "quick" jump tool)
  - List APIs are paginated — would need to fetch all pages or add an unpaginated endpoint

## Decision

Use **Option 1** — page-only search with an optional recent-items cache. The palette indexes all navigable pages (from `NAV_GROUPS` + `UTILITY_ITEMS`) and all settings pages (from `SETTINGS_ITEMS`), filtered by the user's capabilities and admin status. A "Recent" group showing the last 5 visited project/customer pages is a stretch goal for v1, implemented as a lightweight in-memory cache that resets on page refresh.

## Rationale

The command palette's primary value proposition is instant navigation, not entity search. Users who know they want "Invoices" or "Time Tracking settings" save 2-3 clicks by pressing Cmd+K and typing. This covers the most frequent use case and can be built entirely in the frontend with zero API calls.

Full entity search is a valuable feature but belongs in a dedicated phase. It requires a backend search endpoint with proper indexing, debounced queries, and result ranking — none of which are trivial. Bolting entity search onto a navigation palette creates scope creep and violates the frontend-only constraint.

The recent-items cache is a lightweight middle ground: it gives users entity-level navigation for the projects and customers they're actively working with, without any API calls. The cache is populated by observing `pathname` changes in the org layout. If a user visits `/org/acme/projects/abc-123`, the palette remembers "Project: Acme Audit 2026" (fetched from the page's existing data). This is in-memory only — it resets on refresh, which is acceptable for a "recent" list.

When full entity search is needed, the palette architecture supports it cleanly: add a "Search results" group below "Recent" that queries a backend endpoint with debounced input. The cmdk library supports async loading natively.

## Consequences

- **Positive**: Palette ships without backend dependency; implementation is contained to ~1 component
- **Positive**: Fast open time — no API calls, no loading state, instant fuzzy search
- **Positive**: Clean extension point for full entity search in a future phase
- **Negative**: Users cannot find specific projects/customers by name (except recently visited)
- **Negative**: Power users from Linear/Notion may expect more from Cmd+K initially
- **Neutral**: Recent-items cache is optional for v1 — the palette is useful without it
