# ADR-172: Settings Layout Pattern

**Status**: Accepted
**Date**: 2026-03-10
**Phase**: Phase 44 — Navigation Zones, Command Palette & Settings Modernization

## Context

The DocTeams settings page currently renders 23 settings as a card grid. Each card links to a sub-page. When a user clicks a card, the entire content area navigates to that settings page — the card grid disappears, and there is no persistent settings navigation. Returning to the hub requires the browser back button or breadcrumbs.

This pattern has three problems: (1) users lose context of where they are within settings, (2) switching between settings requires an extra click back to the hub, and (3) the flat card grid provides no logical grouping for 23 items.

Modern B2B tools (Linear, Notion, Claude Desktop, Vercel) use a persistent sidebar + content pane layout for settings. The sidebar shows all settings sections, the content pane renders the active section, and switching sections is a single click without navigating away.

We need to decide the implementation pattern: a nested Next.js layout with URL-based routing, or client-side tab switching within a single page.

## Options Considered

### 1. **Nested App Router layout with URL preservation (chosen)** — A `layout.tsx` in the settings directory renders the sidebar; individual settings pages render as `{children}`

- Pros:
  - URLs are preserved and bookmarkable (`/settings/rates`, `/settings/billing`)
  - SSR-friendly — each settings page can be a server component with its own data fetching
  - No client-side state management for active tab
  - Leverages Next.js App Router's layout persistence — sidebar never re-renders on navigation
  - Back/forward browser navigation works naturally
  - Shareable links (send someone a link to a specific settings page)
- Cons:
  - Navigation between settings sections triggers a server render (mitigated by client-side navigation in App Router)
  - Individual settings pages must be valid standalone routes (they already are)

### 2. **Client-side tabs within a single page** — A single settings page renders all sections as tabs, switching content without URL changes

- Pros:
  - Instant switching — no navigation, no server render
  - Simpler routing — one page component manages all settings
- Cons:
  - URLs don't change — cannot bookmark or share a specific setting
  - All settings content loaded at once (heavy page weight)
  - Browser back/forward doesn't work between settings sections
  - Would require rewriting all 23 settings pages into a single mega-component or using lazy loading
  - Breaks the existing route structure (`/settings/rates` would need redirects or parallel support)
  - Server components in individual settings pages would need to become client components

### 3. **Client-side tabs with URL hash** — Tab switching updates the URL hash (`/settings#rates`) without page navigation

- Pros:
  - URL partially preserved (hash-based)
  - Instant switching like Option 2
- Cons:
  - Hash-based routing is a pre-App Router pattern — fights against Next.js conventions
  - Still loads all content or needs lazy loading
  - Hash changes don't trigger server-side data fetching
  - Existing `/settings/rates` URLs would break (or need parallel support)
  - Cannot use server components for individual settings sections
  - Accessibility concerns with hash-based navigation

## Decision

Use **Option 1** — nested App Router layout with URL preservation. A new `layout.tsx` at `app/(app)/org/[slug]/settings/layout.tsx` renders the settings sidebar and the active page as `{children}`. All existing settings page routes (`/settings/billing`, `/settings/rates`, etc.) continue to work unchanged. The settings hub page (`/settings`) redirects to the first available settings page (`/settings/billing`).

## Rationale

The nested layout pattern is the natural fit for Next.js App Router and the most maintainable solution. The App Router's layout persistence means the settings sidebar renders once and persists across settings page navigations — it does not re-render when the user switches sections. This gives the same "instant switching" feel as client-side tabs while preserving all the benefits of URL-based routing.

The existing 23 settings pages are already standalone route segments with their own `page.tsx` and often their own `actions.ts` for server actions. Converting them to client-side tabs would require abandoning this structure and either merging everything into one component (unmaintainable) or building a lazy-loading system (complex). The layout approach requires zero changes to individual settings pages — they render inside the layout's `{children}` slot without modification.

URL preservation is important for a B2B product. Team members share links to specific settings ("can you check the rates config?"). Admins bookmark settings they frequently adjust. Browser back/forward navigation works as expected. These are table-stakes UX behaviors that client-side tab switching would break.

The only trade-off is that navigating between settings sections triggers a client-side navigation (not a full page load — Next.js handles this as a soft navigation). In practice, this is imperceptible: the sidebar stays mounted, only the content pane updates.

## Consequences

- **Positive**: Zero changes to existing settings page files — they render inside the layout automatically
- **Positive**: Bookmarkable, shareable URLs for every settings section
- **Positive**: Layout persistence means the sidebar never flickers or re-renders
- **Positive**: Server components work in settings pages — data fetching stays on the server
- **Negative**: Initial load of the settings area fetches the layout + the active page (two components) — marginal overhead
- **Neutral**: The settings hub page becomes a redirect rather than a content page — users always land on a specific setting
- **Neutral**: Mobile adaptation requires a separate `SettingsMobileNav` component since the sidebar is hidden on small screens
