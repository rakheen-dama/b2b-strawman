# ADR-394: Settings shell: `SettingsSection` cards, one h1 per tab, and a mobile family bar

**Status**: Accepted (refines the presentation of [ADR-341](ADR-341-settings-taxonomy-five-families-controls-stay-pages-go.md); ADR-341's taxonomy, `?tab=` mechanics, loaders and route count are unchanged)

**Context**:

Phase 87 consolidated settings into five families (Firm, Matters, Documents, Fees & Trust, Connections) with `?tab=` tabs; Phase 93 adds Matters › Take-on, making 32 tabs, plus 11 sub-pages (ADR-341; `lib/settings-nav.ts`). The founder asked (RD-3) for a settings shell with section cards and right-aligned actions, same routes, same labels. At HEAD:

- **Shell.** `settings/layout.tsx` renders `SettingsSidebar` in `<aside class="hidden w-60 shrink-0 md:block">` (`:13-14`), so below `md` there is no way to switch family except the app Sheet's single "Settings" item or the command palette; the family's tab strip scrolls at every width (`settings-family-tabs.tsx:84-85`, "no mobile pill row — D11"). The rail sticks at `top-14` (`settings-sidebar.tsx:42`), which predates the floating header, and hard-codes its active state (`:65,84`). The rail is `nav aria-label="Settings navigation"`.
- **Titles.** No family page has a header. Each of the 31 tab loaders renders its own `font-display text-3xl` `<h1>` (only the active loader runs, `settings-family-page.tsx:96`), and e2e asserts many of those texts as level-1 headings ("Tax Settings", "Roles & Permissions", "Data Protection", "Rates & Currency", "Notification Preferences", "Custom Fields", "Tags", "General", "Packs", "Templates"); `sidebar-navigation.spec.ts:38-39` requires a level-1 heading in `#main-content` on `/settings`.
- **Save models.** Tabs mix per-card saves, one save for several cards (Firm › General's "Save Settings", `general-settings-form.tsx:341`), instant-save controls (portal cadence Select, Features switches, MCP/integration switches) and per-row dialogs, sometimes on one tab. Forms own their state (`useState` `saving`, `disabled={saving}`, e.g. `TaxSettingsForm.tsx:29-50,125`) and call server actions directly.
- **Danger zones.** Disconnect Xero, revoke MCP access, uninstall pack, logo delete, processing-register delete — each with its own `AlertDialog` or `window.confirm`.
- **Layering.** ADR-367 puts presentational, dependency-free primitives in `@b2mash/ui` (PORTAL-GATE on every change) and app-coupled ones in `frontend/components`. The portal has no settings surface.

**Options Considered**:

*Section primitive*:

1. **No primitive; restyle each card inline with `Card`/`CardHeader`/`CardFooter`.**
   - Pros: No new API.
   - Cons: 32 tabs re-invent the same header/footer/danger layout; the "one look" the phase exists for drifts again; nothing to test once.
2. **`SettingsSection` in `@b2mash/ui`.**
   - Pros: Shareable if the portal ever has settings; it is presentational and dependency-free, so ADR-367 allows it.
   - Cons: No portal consumer exists or is planned; every iteration across four settings-content slices would run PORTAL-GATE; the portal bundle gains an unused export.
3. **`SettingsSection` + `SettingsActionBar` in `frontend/components/settings/settings-section.tsx`, layout only — title (today's heading text and level), description, header actions, optional footer for a per-card save with a status slot, a destructive variant; the action bar for a page-level save (chosen).**
   - Pros: One composition for every tab; carries all four save models side by side without owning any of them; FE-GATE only; promotable to `@b2mash/ui` later, mechanically, if the portal needs it (as EmptyState's promotion is deferred in ADR-367).
   - Cons: A primitive that is "just layout" can be bypassed; reviewers and the exit metrics enforce adoption.
4. **A form framework primitive that owns dirty/saving state and submits (e.g. `SettingsForm` with `onSave`).**
   - Pros: Uniform dirty tracking and pending UI.
   - Cons: Changes save semantics in a visual phase (the requirements forbid it): forms would move their state, error handling and success messages into a new owner.

*Page title*:

5. **A family-level `PageHeader` (e.g. "Firm settings") with tab titles demoted to `h2`.**
   - Pros: One title above the tab strip, conventional.
   - Cons: Breaks every e2e level-1 assertion on tab titles; "Firm settings" as a visible heading is new copy.
6. **A family-level h1 plus each tab's h1.**
   - Pros: Keeps tab assertions.
   - Cons: Two h1s per page, breaking the one-h1 rule PageHeader exists to enforce and strict-mode `getByRole("heading",{level:1})` lookups.
7. **No family h1; each tab keeps exactly one h1 with its existing text, rendered through `PageHeader` inside the tab, including permission branches (chosen).**
   - Pros: Every assertion holds; no new copy; one h1 per page; the loader keeps owning its title, so permission branches stay correct.
   - Cons: The title sits below the tab strip, as today, rather than above it.

*Mobile settings navigation*:

8. **Nothing new; rely on the tab strip and the app Sheet.**
   - Pros: No work.
   - Cons: Switching family on a phone takes the app Sheet plus a second navigation — the kink the requirements name.
9. **A family `Select` above the content.**
   - Pros: Compact; one control.
   - Cons: Needs `onValueChange` → `router.push` (new client navigation code), loses link semantics (prefetch, middle-click, the real URL), hides the current set of families behind a tap.
10. **A Sheet or drawer containing the full rail.**
    - Pros: The whole tree, as on desktop.
    - Cons: A second overlay on a page that already has the app Sheet; two taps to switch; more focus management.
11. **A horizontal, scrollable family link bar (`md:hidden`) with the same hrefs, named "Settings navigation", active family `aria-current="page"` and the shared underline (chosen).**
    - Pros: Plain links; one tap; the current family visible at a glance; five labels fit at 390px with at most one scroll; below `md` the rail is `display:none`, so only one landmark with that name is in the accessibility tree; no new copy.
    - Cons: A second horizontal scroller above the tab strip (keyboard-focus OBS at 390 required); it lists families only — tabs come from the strip.

**Decision**: Options 3, 7 and 11.

- `SettingsSection` (`title?`, `as: "h2" | "h3"`, `description?`, `actions?`, `children`, `footer?`, `status?`, `variant: "default" | "destructive"`, testid passthrough) and `SettingsActionBar` (`children`, `status?`, `sticky?`) live in `frontend/components/settings/settings-section.tsx`, server-safe, composing `@b2mash/ui` `Card`. They own no state, submit nothing and add no confirmation: pending and disabled come from the form's existing state, and destructive actions keep their existing dialogs. A section with no heading today gets no title. The destructive variant is visual only (token border and header wash), with no "Danger zone" heading.
- Each tab renders one `PageHeader` with its existing h1 children and description; no family-level header.
- `components/shared/tab-underline.ts` exports two underline variants: one keyed on `aria-selected` (the family tab strip and GroupedTabBar) and one keyed on `aria-current="page"` (the mobile family bar, whose items are links).
- `settings/layout.tsx` renders the restyled rail (tokens, the main sidebar's active-bar idiom, sticky at `top-[calc(var(--app-header-height)+0.5rem)]`) at `md` and up, and `settings-family-bar.tsx` below `md`.
- `SettingsFamilyTabs` keeps its mechanics (manual activation, `router.replace` + `useOptimistic`, focus into the new panel, `forceMount` pending panel, tablist named "{Family} settings") and adopts the 642C underline keyed on `aria-selected` in a horizontal `ScrollArea`, through a style module shared with GroupedTabBar. The strip is not sticky.

**Rationale**:

The requirements fix behaviour and ask only for layout, so the primitive is layout and nothing else: that is what lets it wrap a form that saves three cards at once, a card that saves itself, a switch that saves on change and a destructive block on the same tab without touching any of them. The title rule follows from the e2e contract and the one-h1 rule: the tab's existing h1 is the page's title. The mobile bar is links because the URL is the source of truth in settings (ADR-341) and links keep it so without new navigation code. `frontend/components` is the cheaper home for a primitive with one consumer family and four slices of iteration ahead of it.

**Consequences**:

- Positive:
  - Every settings tab and sub-page shares one section look, right-aligned actions and a distinct danger treatment.
  - Family switching works on phones; the rail sits correctly under the floating header.
  - No e2e title assertion, save path or loader changes.
- Negative:
  - The tab title remains below the tab strip; if the founder later wants it above, the loader contract changes (a separate decision).
  - `SettingsSection` relies on adoption discipline; the exit metrics count tabs still hand-rolling cards.
  - Two horizontal scrollers on mobile settings pages; both need keyboard-focus observation.
  - The tab strip does not stick; long tabs scroll it away (revisitable from the kinks register).

**Related**: [ADR-341](ADR-341-settings-taxonomy-five-families-controls-stay-pages-go.md) (taxonomy and `?tab=`), [ADR-367](ADR-367-design-system-layering.md) (layering), [ADR-369](ADR-369-datatable-tanstack-client-mode.md) (tables inside sections, `frame={false}`), [ADR-385](ADR-385-take-on-architecture.md) (the Take-on tab this shell frames); [architecture §18.2.2, §18.2.4, §18.3.4, §18.3.5, §18.5.2](../architecture/phase91-ui-modernization-ii.md); slices E4A, E5A–E5F.
