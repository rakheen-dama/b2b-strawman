# ADR-341: Settings Taxonomy — Five Families, "Controls Stay, Pages Go", ≤ 25 Routes from 44, One Nav Source, Permanent Redirects for One Release

**Status**: Accepted

**Context**:

Post-86 the settings tree has **44 `page.tsx`** under `frontend/app/(app)/org/[slug]/settings/` — 43 real pages plus the `settings/page.tsx` index redirect to `general` (`.arch-context-frontend.md` §1): `general, features, roles, audit-log` · `data-protection(+requests)` · `compliance` · `checklists(+new,+[id],+[id]/edit)` · `custom-fields, tags` · `templates(+new,+[id]/edit)` · `clauses, acceptance` · `request-templates(+new,+[id])` · `request-settings` · `project-naming, project-templates(+[id])` · `rates, tax, time-tracking, trust-accounting, collections, batch-billing, billing` · `email, notifications` · `integrations(+xero,+xero/sync-log,+mcp)` · `ai(+history)` · `automations(+executions)` · `packs`. Twenty-seven leaf families; eight of the routes are CRUD editors (`*/new`, `*/[id]`, `*/[id]/edit`). The layout is Phase 44's nested App Router layout with URL preservation ([ADR-172](ADR-172-settings-layout-pattern.md)): `settings/layout.tsx` → `fetchMyCapabilities()` → `<SettingsSidebar>`.

Navigation to those pages is maintained **twice by hand**: `components/settings/settings-nav-groups.ts` `SETTINGS_NAV_GROUPS` (7 groups / 28 items — `general, work, documents, finance, clients, features, access`) feeds the sidebar, and `lib/nav-items.ts` `SETTINGS_ITEMS` (28 items, with a `comingSoon` "Security" entry at `href "#"` and an "Organization" alias for `general`) feeds the command palette ([ADR-171](ADR-171-command-palette-scope.md)). The two lists already disagree: Packs, Data Protection, AI history, the Xero/MCP sub-pages and `automations/executions` are reachable from the sidebar but not the palette. `settings-sidebar.tsx` rewrites `Project|Customer|Client|Proposal|Invoice` label prefixes through `t()` (gone with [ADR-338](ADR-338-terminology-layer-removal-single-vocabulary.md)). `components/ui/tabs.tsx` (Shadcn `Tabs`) exists; detail pages already resolve a tab from the URL (`lib/constants/tab-group-types.ts#resolveTabFromUrl`). `frontend/next.config.ts` has one permanent redirect precedent (`/org/:slug/settings/team` → `/org/:slug/team`, GAP-Doc-Drift-26).

The requirements target five families — **Firm** (general, team & roles, audit trail, data protection, features) · **Matters** (matter templates, naming, custom fields, tags, checklists, request templates + request settings) · **Documents** (templates, clauses, acceptance, email) · **Fees & Trust** (tariffs/rates, tax, time recording, fee runs/batch billing, billing, collections, trust accounting) · **Connections** (Xero + sync log, MCP, AI + history, notifications, packs, automations + executions) — "a tree a bookkeeper can learn in an afternoon", ≤ 25 routes, "keep every control; lose pages". The explicit before→after route table lives in the architecture doc (§12.7.2); this ADR fixes the rules it must obey. Two placements the architecture doc makes within those rules: `settings/billing` is the firm's Kazi subscription status (ADR-335), so it is the **Firm → Subscription** tab rather than a Fees & Trust tab; `settings/compliance` (retention policies, compliance packs) sits under **Firm** beside Data protection.

**Options Considered**:

1. **Keep the flat 44 pages**, regroup the sidebar only.
   - Pros:
     - No redirects, no tab plumbing, no e2e `/settings/` literal changes (16 in Playwright, 2 per scenario doc).
   - Cons:
     - Forty-three destinations for a firm of five.
     - `data-protection/requests`, `integrations/xero/sync-log`, `ai/history`, `automations/executions`, `request-settings` each exist as a page because a sub-feature once needed a route, not because a user needs a URL.
     - Leaves the two parallel nav lists and their drift in place.
2. **Five families as path segments (`settings/firm`, `settings/matters`, `settings/documents`, `settings/fees`, `settings/connections`); single-purpose pages fold into `?tab=` tabs of their family page; ≤ 25 routes; permanent redirects for all 43 old settings URLs for one release (CHOSEN)** — each family is one page whose former pages are Shadcn `Tabs` values addressed by `?tab=` (e.g. `settings/firm?tab=data-requests`, `settings/fees?tab=tax`, `settings/connections?tab=mcp`); CRUD editors and log views keep their own routes **under the family** (`settings/matters/checklists/[id]/edit`, `settings/documents/templates/new`, `settings/connections/xero/sync-log`, `settings/connections/ai/history`, `settings/connections/automations/executions`) because an editor owns form state and a `revalidatePath` target; `SETTINGS_ITEMS` and `SETTINGS_NAV_GROUPS` collapse into one exported source (`lib/settings-nav.ts` `SETTINGS_FAMILIES`) consumed by the sidebar, each family page and the palette, with tab deep links as palette entries.
   - Pros:
     - Meets the ≤ 25 target without hiding a control.
     - A deep link to a tab survives (`?tab=` is read on load and written on change), so the closure-report route count is honest.
     - Family-as-path makes the address say where a control lives (`settings/fees?tab=tax` *is* the tree a bookkeeper learns); the redirect table is mechanical (43 rows, committed) and the breadcrumb segment list shrinks from twenty settings sub-segments to five families.
     - One nav source ends the drift and gives the palette the pages it is missing today.
     - `settings-sidebar.test.tsx` and `command-palette-dialog.test.tsx` test one list.
   - Cons:
     - Tab pages are heavier (one page renders several panels' data).
     - Each folded page's server reads move to the family page and must be lazy per tab or accepted as a combined fetch.
     - "Fits a tab" is a judgement; the slice must justify each fold against "controls stay" in the PR.
     - Every removed URL is a scenario/Playwright amendment ([ADR-344](ADR-344-qa-scenario-amendment-authorisation-phase87.md)).
     - Bookmarks depend on the redirect table; every old settings URL changes (43 rows) and every Playwright `/settings/` literal (16) and both scenario docs are amended ([ADR-344](ADR-344-qa-scenario-amendment-authorisation-phase87.md)).
3. **One settings page with sections** (anchors, accordion).
   - Pros:
     - One route; nothing to group; ⌘F finds everything.
   - Cons:
     - Forty-three panels of server-rendered forms on one page.
     - Admin-only and module-gated sections interleave with open ones.
     - The CRUD editors still need routes, so it is "one page plus eight".
     - ADR-172 chose per-page routes for exactly this reason.
4. **Five families, every page keeps its route.**
   - Pros:
     - Grouping clarity with zero redirects.
   - Cons:
     - Route count stays 43; `sync-log`, `history`, `executions`, `requests` and `request-settings` remain pages whose only content is a table under a parent they cannot be reached from without the sidebar; fails the ≤ 25 target the founder set.

**Decision**: Option 2 — regroup into five families that are **path segments** (`settings/{firm,matters,documents,fees,connections}`), fold single-purpose pages into URL-addressable `?tab=` tabs of their family page so the tree is **17** routes (5 family pages + 11 kept editor/log routes + index; architecture §12.7.2), keep every CRUD editor and log route under its family, replace `SETTINGS_ITEMS` + `SETTINGS_NAV_GROUPS` with one source (`lib/settings-nav.ts` `SETTINGS_FAMILIES`), and give **all 43 old settings URLs** a permanent `next.config.ts` redirect (committed `lib/legacy-redirects.ts` table: 32 tab-kind + 11 route-kind rows) to their family page and tab, or kept route, for one release. Phase 84's take-on page lands as `settings/matters?tab=take-on`.

**Rationale**:

1. **Controls stay, pages go.** The requirement is a smaller tree, not fewer settings. The fold rule is mechanical — a page with no CRUD sub-routes whose controls fit a tab panel becomes a tab; a page with an editor keeps its routes — so the reviewer can check each fold against the rule rather than taste.
2. **Families are path segments because the address should say where a control lives.** Settings URLs appear in 16 Playwright literals and both scenario docs, but no production tenant has a bookmark yet (zero tenants, [ADR-331](ADR-331-migration-squash-at-zero-tenants.md)), so the cost of changing every settings URL is one committed 43-row redirect table and one scenario-amendment pass — both already required by the fold itself. In exchange `settings/fees?tab=tax` reads as the tree, `breadcrumbs.tsx` carries five family labels instead of twenty settings sub-segments, the sidebar rail and the family page's tab strip are two views of one `SETTINGS_FAMILIES` entry, and Phase 84's take-on page has an obvious home (`settings/matters?tab=take-on`).
3. **One source, because two already drifted.** `SETTINGS_ITEMS` lags the sidebar by at least six destinations today. A single `SETTINGS_FAMILIES` export in `lib/settings-nav.ts` (replacing `settings-nav-groups.ts` and `SETTINGS_ITEMS`) consumed by `settings-sidebar.tsx`, each family `page.tsx` and `command-palette-dialog.tsx` — with `adminOnly`, `requiredModule` and `requiredCapability` per tab — makes the palette, the rail and the tab strips provably the same set (one test over one list; `settings-nav.test.ts` also checks every redirect target resolves to a listed family + tab). The `comingSoon` "Security" placeholder is dropped.
4. **Tab deep-link rule.** A tab is addressable as `?tab=<id>`; the page reads it with `useSearchParams` on load, writes it with `router.replace` on change, falls back to the first tab for a missing or unknown value, and the redirect table always targets a page **plus tab** so no removed URL lands on a default tab silently. `resolveTabFromUrl` from `tab-group-types.ts` is the precedent (reuse or mirror; confirm at build time).
5. **Redirects for one release, committed as a table.** `permanent: true` (308) matches the `/settings/team` precedent; browsers cache it, so removing the rules after one release leaves cached bookmarks working. The table is a committed artefact (in `next.config.ts` with a comment per row naming the old route, or a `settings-redirects.ts` it imports) that the QA amendments cite by row, and its removal is a named follow-up.

**Consequences**:

- Positive:
  - Settings routes drop from 44 to **17** `page.tsx` (5 family pages + 11 kept editor/log routes + index; before→after table in architecture §12.7.2, confirmed in the closure report).
  - Every control remains reachable by URL.
  - `SETTINGS_FAMILIES` is the single settings nav source.
  - `SETTINGS_ITEMS` and `settings-nav-groups.ts` are deleted.
  - Palette and sidebar tests collapse to one fixture.
  - `translateNavLabel()` and `useTerminology` leave `settings-sidebar.tsx`.
  - `next.config.ts` `redirects()` gains **43** settings rows (32 tab-kind + 11 route-kind — the full table is architecture §12.7.2) through the committed `lib/legacy-redirects.ts`, plus the `/projects`, `/customers`, `/calendar`, `/court-calendar` rows from [ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md) and [ADR-339](ADR-339-attorney-week-navigation-and-one-diary.md).
  - `breadcrumbs.tsx` `SEGMENT_LABELS` (20 settings sub-segments today) shrinks to the five family segments plus the kept editor segments.
  - `NON_ROUTABLE_PREFIXES` is re-derived (`settings/documents/templates/[id]` replaces `settings/templates/[id]`; `breadcrumbs-routes.test.ts` is the proof).
  - `breadcrumbs-routes.test.ts` re-pins.
- Negative:
  - Family pages with several tabs fetch more per load unless tab panels are lazy.
  - The slice decides per page and documents it.
  - Removed-URL redirects live for one release only.
  - After removal an old bookmark 404s — acceptable because no production tenant has bookmarks yet.
  - `help.json` and `getting-started.json` link to settings routes.
  - They change in the same PR as the fold.
  - Phase 84's "Settings → Take-On" lands as `settings/matters?tab=take-on` (architecture §12.14 #3): a tab, so the route count stays 17 unless its mapping editor needs a route of its own.
- Follow-ups:
  - Remove the settings redirect rows one release after Phase 87 ships (tracked item in `TASKS.md`).
  - `settings/compliance` vs `compliance` (nav page) naming overlap is resolved by the family table, not here.
- Related:
  - [ADR-172](ADR-172-settings-layout-pattern.md) (layout mechanics and URL preservation kept), [ADR-171](ADR-171-command-palette-scope.md) (palette now reads the same source as the sidebar), [ADR-339](ADR-339-attorney-week-navigation-and-one-diary.md) (Firm → Settings entry), [ADR-338](ADR-338-terminology-layer-removal-single-vocabulary.md) (settings labels literal), [ADR-332](ADR-332-single-vertical-profile-mechanism-retained.md) (Features page keeps its two toggles; picker gone), [ADR-333](ADR-333-mcp-sole-conversational-ai-surface.md) (AI + MCP settings retained under Connections), [ADR-334](ADR-334-automation-engine-without-builder.md) (Automations list + executions as one page), [ADR-344](ADR-344-qa-scenario-amendment-authorisation-phase87.md).
