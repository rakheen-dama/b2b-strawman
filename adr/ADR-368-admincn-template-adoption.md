# ADR-368: Adopting the admincn template: translate Base UI to Radix, credit and licence

**Status**: Accepted

**Context**:

Phase 90 borrows the *structure* of the paid admincn admin template while keeping Kazi's Signal Deck identity: slate scale at hue 260, teal accent, and Sora / IBM Plex Sans / JetBrains Mono (founder decision 2, 2026-11-11). The template is checked out at the repo root as `shadcn-nextjs-admincn-admin-template-1.0.0/` and is **gitignored** (`.gitignore:164`).

- It contains six layout variants (`default-layout`, `full-navbar-layout`, `horizontal-layout`, `icon-menu-layout`, `paper-layout`, `split-layout`). Its README credits shadcn/studio by ThemeSelection.
- `default-layout/package.json` names it `shadcn-nextjs-admincn-admin-template` 1.0.0. It builds on `@base-ui/react` 1.7.0, `@tanstack/react-table` 8.21.3 and `recharts` 3.8.0. Its `components.json` style is `base-vega`.
- The blocks Phase 90 wants:
  - the shell (`components/layout/Sidebar.tsx`, `Header.tsx`, `CommandMenu.tsx`);
  - KPI cards (`views/dashboards/statistics/statistics-card-03.tsx` and the sparkline cards);
  - charts (`views/dashboards/charts/`);
  - tables (`views/datatables/datatable-invoice.tsx`, `views/apps/users/list/`, `views/datatables/export-button`);
  - `ui/timeline.tsx` and `views/pages/empty-state/empty-state-v2`.

Kazi is shadcn **new-york on Radix**:

- `frontend/components.json`: style `new-york`, the unified `radix-ui` 1.4.3 package, `baseColor: neutral`;
- next 16.3.8 (pnpm patch), react 19.2.3, `recharts` ^3.7.0;
- `@b2mash/ui` built on `radix-ui`, and `frontend/CLAUDE.md` forbids `@radix-ui/react-*` imports in favour of `radix-ui`.

Base UI composes with `render={<X/>}` props, while Radix uses `asChild` plus a child. The Base UI primitives behave differently from Radix in focus management, portal placement and data attributes.

The e2e suites depend on Radix semantics. For example, `GroupedTabBar` opens a Radix `DropdownMenu` whose items carry `tab-item-<id>`, and that menu is called 78 times through `e2e/helpers/grouped-tabs.ts`.

On 2026-11-11 the founder confirmed that the paid licence permits reusing template code inside Kazi (founder decision 3). The licence text itself is not in the repo. The README's MIT badge refers to the open-source shadcn/studio repository, not to this paid template. The founder's confirmation is the authority for this ADR.

**Options Considered**:

1. **Adopt the template's stack: move Kazi to Base UI / `base-vega`** — rebase the design system on the template so blocks drop in unchanged.
   - Pros: Template blocks paste in unchanged. Future template updates apply directly.
   - Cons: Every primitive would be rewritten: the 20 files in `frontend/components/ui`, `@b2mash/ui`, and the portal through `@b2mash/ui`. Every `asChild` call site and every role-based e2e selector would be at risk. This is explicitly out of scope ("Radix stays", Out of Scope: "Migrating to Base UI or shadcn `base-vega`").
2. **Copy template files verbatim beside the Radix primitives** — add `@base-ui/react` and keep both libraries.
   - Pros: The fastest way to get the template look.
   - Cons: Two dialog, dropdown, tooltip and popover systems with different focus and keyboard behaviour, a second runtime dependency, and two ways to write every component. Template colours are neutral/zinc/indigo, which `frontend/CLAUDE.md` bans. The result would look assembled, which is the problem this phase exists to fix.
3. **Use the template as a visual reference only; rebuild every block from the shadcn CLI and first principles** — no template code enters the repo.
   - Pros: No licence or credit obligations. Everything is native to Kazi.
   - Cons: It re-derives patterns the founder has paid for and cleared for reuse: the KPI card with a delta chevron, and the datatable toolbar with column toggle, export and selection. That makes it slower and more likely to drift from the reference.
4. **Translate: shadcn CLI components first, then template blocks translated into Kazi's conventions, credited per file (CHOSEN).**
   - Pros: One primitive library (Radix). Template structure is reused where it saves time. The output is indistinguishable from native Kazi code to lint, typecheck, format and e2e.
   - Cons: Translation is manual work for each block. Template updates don't merge automatically; each one is re-translated.

**Decision**: Option 4 — template code enters Kazi only by translation into shadcn new-york/Radix and Kazi tokens.

Each translated file carries a credit header, and the template directory stays gitignored and is never imported, built or shipped (see Rationale).

**Rationale**:

1. **Translation rules**, applied by every slice that adapts a block:
   - Add shadcn components through the CLI first (`sidebar`, `chart`, `toggle-group`, `scroll-area`, and `breadcrumb` if needed) so they match `components.json`. Then review the output against `frontend/CLAUDE.md`: neutral/zinc/gray becomes slate, indigo becomes teal, and `radix-ui` replaces `@radix-ui/react-*`.
   - `render={<X/>}` becomes `asChild` plus a child.
   - Each Base UI primitive becomes its new-york equivalent: `Menu` → `DropdownMenu`, `Popover` → `Popover`, `Tooltip` → `Tooltip`, `Dialog` → `Dialog`/`Sheet`, `Combobox` → `Command` inside a `Popover`.
   - Base primitives are imported from `@b2mash/ui` (Button, Badge, Card, Input, Label, Separator, Skeleton).
   - Template theme presets, `theme-presets.ts`, decorative components (`bg-silk`, `border-beam`, `morphing-text`, `number-ticker`, `background-ripple`), images and logos are **not** copied. "Corporate" and "Modern Minimal" are used only as density references for the token slice.
   - Versions follow Kazi (recharts ^3.7, react 19.2.3, next 16.3.8). A template version is never a reason to bump a Kazi dependency. The new dependencies (`@tanstack/react-table` 8.x, `next-themes`) are justified in [ADR-369](ADR-369-datatable-tanstack-client-mode.md) and [ADR-371](ADR-371-dark-mode-light-default.md).
2. **Credit.** Every file containing adapted template code starts with a comment giving:
   - the source (`shadcn/studio admincn admin template v1.0.0` and the template-relative source path);
   - that it is used under Kazi's paid licence (founder confirmation 2026-11-11);
   - that it was translated from Base UI to Radix.

   The PR body lists the adapted files. The architecture doc §15 keeps the adoption table (block → Kazi file). HEAD has no `THIRD_PARTY*` or `NOTICE*` file, and this ADR does not create one. If a licence audit later requires a repo-level notice, it is generated from the credit headers.
3. **Containment.**
   - No source file, `tsconfig` path or build script references `shadcn-nextjs-admincn-admin-template-1.0.0/`, and the directory is never committed.
   - HEAD's `.dockerignore` does not list it. The first adoption slice checks whether any Docker build context includes the repo root and adds the directory to `.dockerignore` if one does (verify at build).
   - Kanban, calendar, mail and chat apps from the template are out of scope.
4. **Why translation beats the alternatives here.** Kazi's acceptance bar is the legal Playwright suites, which select by role and test id. Radix semantics are what those selectors were written against. A second primitive library would split keyboard and focus behaviour inside a single page. Rebuilding from scratch would spend design-partner time re-deriving cleared work.

**Consequences**:

- Positive:
  - One primitive library, one style (`new-york`), one token source. Adapted code passes `pnpm lint && pnpm typecheck && pnpm format:check` like native code.
  - Provenance is visible in each file and in the PR history, so a future licence question can be answered by grepping for the credit header.
  - No template asset, theme or dependency ships in the frontend or portal images.
- Negative:
  - Translating takes longer than copying. Each adapted block needs a vitest of its own states because Radix behaviour differs from Base UI (focus return, `data-state` attributes).
  - Template updates (v1.x) don't flow in. A later improvement is re-translated by hand or ignored.
  - The licence is held outside the repo. If ownership of the licence changes, the credit headers are the only in-repo record.

**Related**: [ADR-367](ADR-367-design-system-layering.md) (which layer a translated block lands in), [ADR-369](ADR-369-datatable-tanstack-client-mode.md) (datatable blocks), [ADR-370](ADR-370-chart-kit-and-palette.md) (chart blocks and the shadcn `chart` component), [ADR-371](ADR-371-dark-mode-light-default.md), [ADR-287](ADR-287-grouped-tabs-dense-navigation.md) / [ADR-298](ADR-298-shared-grouped-tab-bar.md) (Radix `DropdownMenu` inside `GroupedTabBar`); [architecture §15](../architecture/phase90-ui-modernization.md).
