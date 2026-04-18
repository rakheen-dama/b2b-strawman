# ADR-252: Portal Nav — Slim Left Rail, Not a Mirror of the Firm App

**Status**: Accepted

**Context**:

The portal shipped in Phase 22 with a top-nav containing three entries — Projects, Proposals, Invoices — plus a profile icon. Horizontal top-nav becomes crowded around 5 entries on typical laptops and unusable on mobile. Phase 68 introduces six new top-level destinations — Home, Trust, Retainer, Deadlines, Information Requests, Pending Acceptance — which would push the top-nav to 8+ items before accounting for tenant-gated ones. We had to pick a new shape before building the new pages.

Firm-side (Phase 44) adopted a zoned sidebar with a `⌘K` command palette. That decision served heavy-use firm operators who navigate across dozens of entities per session and benefit from keyboard acceleration. Portal users are different: they visit infrequently, on mixed devices, and scan rather than navigate. They need a shape that makes "what am I supposed to do right now?" immediately obvious, not a shape that rewards memorisation and shortcut fluency. The distinct visual identity also matters — the portal is a client-facing product surface, and mirroring the firm's internal sidebar would make the portal look like the firm's own tool leaked into the client's browser.

**Options Considered**:

1. **Slim left rail with mobile drawer** — 240px desktop sidebar, icon + label per entry, flat (ungrouped) ordering, collapses to hamburger drawer below `md`. Top bar is 48px and carries only branding + user menu.
   - Pros:
     - Scales to 10–12 items without visual clutter.
     - Familiar pattern for client-facing SaaS (Stripe Dashboard, Linear customer portals, Notion public views).
     - Mobile drawer is a well-understood, accessible pattern.
     - Keeps content area simple (no horizontal scroll, no nav state flipping).
   - Cons:
     - Loses screen width compared to today's top-nav (240px taken).
     - No keyboard acceleration for power users (but portal users aren't power users).

2. **Mirror firm-side zoned sidebar + `⌘K` palette** — Adopt Phase 44 verbatim.
   - Pros:
     - Single design system maintained across both apps.
     - Accommodates future growth in entries.
   - Cons:
     - Over-engineered for the portal's use pattern. Zones imply grouping that has no natural fit on 10 flat destinations.
     - Command palette is invisible to clients and rarely useful for infrequent visits.
     - Makes the portal look like firm software — blurring the client-facing boundary.
     - More code to maintain, more test surface.

3. **Grouped top-nav with dropdowns** — Keep the top-nav shape; group "Finance" (Invoices, Trust, Retainer), "Projects" (Projects, Deadlines), "Actions" (Requests, Acceptance, Proposals) into dropdowns.
   - Pros:
     - Smallest delta from current shape.
     - No new component primitives.
   - Cons:
     - Dropdowns on mobile are painful.
     - Requires two clicks to reach most destinations.
     - Grouping taxonomy is arbitrary and changes as features grow.
     - Doesn't solve the underlying "there are now 10 things" problem — just hides them.

**Decision**: Option 1 — slim left rail on desktop, mobile drawer below `md`, flat ordering, no command palette.

**Rationale**:

**Client-first skim use.** Portal users visit to answer "what's pending?", "what's my balance?", "where are my invoices?". A flat, always-visible list of destinations answers those questions faster than any clever grouping. Icon + label is scannable. No two-click paths.

**Distinct visual identity.** The portal is a client-facing product; it should not look like the firm's internal tool. A different nav shape (rail vs. top-nav-with-zones) establishes that boundary at first glance.

**Scales past the current ceiling.** 240px vertical rail comfortably hosts 10 items; mobile drawer has no hard cap. Firm-side adopted a left sidebar for the same reason — we're taking the underlying architectural insight (vertical scales, horizontal doesn't) without the complexity overhead of zones + palette.

**Low risk to retrofit.** The existing three pages (Projects, Invoices, Proposals) drop into the new shell unchanged. No controller refactors.

**Consequences**:

- `portal/components/portal-header.tsx` is deleted; `portal-topbar.tsx` + `portal-sidebar.tsx` + `portal-mobile-drawer.tsx` replace it.
- Content area narrows from `max-w-6xl` to `max-w-4xl` on desktop.
- All nav item definitions consolidate into `portal/lib/nav-items.ts` as a declarative registry with profile/module predicates.
- New `<PortalModuleGate>` component is introduced because portal never had a client-side module gate (only the firm side did).
- Portal does not gain a command palette; if future phases need one, this ADR is the point of revisit.
- Mobile breakpoint behaviour is now first-class: drawer state reuses the existing `mobileMenuOpen` pattern.
- Screenshot baselines are invalidated — Phase 68 ships new ones at `sm`/`md`/`lg`.

**Related**:

- [ADR-168](ADR-168-navigation-zones-and-command-palette.md) — Firm-side navigation zones (deliberately diverged from).
- [ADR-253](ADR-253-portal-surfaces-as-read-model-extensions.md) — Portal vertical surfaces pattern (the rail hosts these new entries).
- [ADR-244](ADR-244-pack-only-vertical-profiles.md) — Profile-based nav gating (mechanism the rail uses).
