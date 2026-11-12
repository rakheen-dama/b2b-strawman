# ADR-371: Dark mode: `next-themes`, light default, opt-in toggle, semantic tokens over `dark:` pairs, long-tail debt list

**Status**: Accepted

**Context**:

Kazi has carried dark-mode styling for many phases without ever rendering it:

- `packages/ui/styles/globals.css` defines a `.dark` token block. `frontend/app/globals.css` declares `@custom-variant dark (&:is(.dark *));`; the portal declares its own copy.
- The frontend's `*.ts(x)` contains **3,851 lines / 4,610 occurrences of `dark:`**, and the portal has 33.
- **Nothing ever adds `.dark` to the document.** There is no `next-themes` (0 hits in workspace `package.json`s and `pnpm-lock.yaml`), no `useTheme`, no `prefers-color-scheme` handling, and the sonner `Toaster` has no `theme` prop.
- So every `dark:` class is unobserved. Turning dark mode on will expose white-on-white and black-on-black defects on pages nobody has looked at in dark.

Facts that constrain the design:

- In `frontend/app/layout.tsx`, **`<AuthProvider>` wraps `<html lang="en">`**, and `<html>` has no `suppressHydrationWarning` and no `class`.
- The sidebar tokens in `frontend/app/globals.css` are slate-950 in **both** modes, and `components/desktop-sidebar.tsx` hard-codes `bg-slate-950` / `text-white/*` instead of reading them.
- The "user menu" is two hand-rolled popovers:
  - mock auth: `MockUserButton` in `components/auth-header-controls.tsx`;
  - Keycloak: `components/auth/user-menu-bff.tsx`.
- The marketing site (`app/page.tsx`, `components/marketing/`) is not restyled but must not visibly break.
- The portal is out of scope and must not get dark mode.
- Founder decision 6 (2026-11-11): dark mode is switched on, **light by default**, opt-in through a Light / Dark / System toggle in the user menu, persisted per browser.

**Options Considered**:

1. **Don't ship dark: delete the `.dark` block and the `dark:` classes** — make the stylesheet honest by removing what never renders.
   - Pros: It removes ≈4,600 dead class occurrences and the risk of half-working dark mode.
   - Cons: It contradicts founder decision 6. It is a whole-app sweep larger than the phase, it touches every long-tail page, and it would have to be undone for any future dark work.
2. **Hand-rolled provider: a cookie plus an inline pre-paint script, with `.dark` set on `<html>` server-side from the cookie** — no new dependency.
   - Pros: No dependency. The RSC could render the right class from the first byte.
   - Cons: It reimplements what `next-themes` already does: the flash-of-unstyled-content (FOUC) script, the `system` listener, cross-tab sync, and the hydration-safe "mounted" pattern. Reading a cookie in the root layout opts every route into dynamic rendering. It is the less-documented path for shadcn.
3. **`next-themes` with `attribute="class"`, `defaultTheme="light"`, `enableSystem`, and `disableTransitionOnChange`, persisted in browser storage; semantic tokens replace `dark:` pairs on in-scope surfaces; long-tail pages are audited and listed (CHOSEN).**
   - Pros: It is the shadcn-documented provider (one small dependency) and handles FOUC, System and storage. Light by default means nobody sees an unobserved page in dark unless they opt in. The debt is recorded, not hidden.
   - Cons: It adds a second new runtime dependency after `@tanstack/react-table`. The preference is per browser, not per user, and a user who opts in can still reach long-tail pages that render poorly in dark.
4. **Same provider, but dark or System as the default** — follow the OS from day one.
   - Pros: It matches many users' OS settings.
   - Cons: A design-partner demo on a laptop set to dark would open on unverified long-tail pages. That is the "risky small vendor" impression this phase exists to remove.

**Decision**: Option 3 — a light-by-default `next-themes` provider with a Light / Dark / System control in both user menus, themed only on `/org/` routes, with semantic tokens replacing `dark:` pairs on in-scope surfaces and a dark-mode debt list for the long tail.

**Rationale**:

1. **Placement.** The `ThemeProvider` (class attribute, light default, System available) sits inside `<body>` in `frontend/app/layout.tsx`, with `suppressHydrationWarning` on `<html>`. `<AuthProvider>` wraps `<html>`, so the provider can't wrap the document root. It goes inside `<body>`, which `next-themes` supports because it writes the class to `document.documentElement` from its own script. `suppressHydrationWarning` on `<html>` covers the class it adds before hydration, so the console shows no hydration warnings (a requirements constraint). The toggle renders its selected state only after mount (`resolvedTheme` is undefined on the server). The `Toaster` receives the resolved theme through a small client wrapper (verify at build).
2. **The toggle.** It is added to `MockUserButton` and `UserMenuBff` so mock-auth e2e and Keycloak behave the same.
   - If the shell slice converts both menus to a Radix `DropdownMenu`, which rulings §3.11 allow, the toggle is a `DropdownMenuRadioGroup` with three items.
   - Otherwise it is a three-option `toggle-group` inside each popover.
   - The labels are "Light", "Dark" and "System". The control does not change existing menu items ("Change password", "Sign out") or their roles.
3. **Semantic tokens over pairs.** On every surface this phase touches, a `dark:` pair that a token already expresses is replaced. Examples: `bg-white dark:bg-slate-900` becomes `bg-card`, `text-slate-500 dark:text-slate-400` becomes `text-muted-foreground`, and `border-slate-200 dark:border-slate-800` becomes `border-border`. New code uses tokens. `dark:` remains legal only where no token applies (for example, an image or brand mark). The sidebar reads the `--sidebar-*` tokens instead of hard-coded `bg-slate-950`. The token slice sets each mode's sidebar values and records them in [architecture §15](../architecture/phase90-ui-modernization.md). Charts use per-mode palette steps from tokens ([ADR-370](ADR-370-chart-kit-and-palette.md)), never `dark:` overrides.
4. **The long tail.** The ≈45 untouched pages under `org/[slug]` get tokens and shell only. The exit gate opens each route in dark at 1024 on the Keycloak stack and checks legibility (no white-on-white, black-on-black or invisible borders on primary content).
   - A failure that a one-line token swap fixes inside an in-scope slice is fixed there.
   - Every other failure goes on the closure report's **dark-mode debt list**: route, element, symptom and suggested token.

   The pages are not silently shipped broken, and dark mode is not withheld from the rest of the app because of them.
5. **Routes outside `/org/` and the portal.** The provider passes `forcedTheme="light"` on every route whose path does not start with `/org/`. That covers the pre-org `(app)` routes (`/dashboard`, `/create-org`, `/platform-admin`), the marketing site and the auth routes (architecture §15.3.2). These routes are outside the surface this phase restyles and audits in dark. Forcing light means they can't visibly break, and it keeps the dark-mode debt list bounded to the audited `org/[slug]` surface. The portal gets no provider and never receives `.dark`. The portal's `@b2mash/ui` import includes the `.dark` token block, but no portal code applies the class, so the portal stays light. Its 33 `dark:` occurrences remain inert.

**Consequences**:

- Positive:
  - Dark mode works and is observed: every UI slice records 1024 and 1440 screenshots in light and dark ([architecture §15](../architecture/phase90-ui-modernization.md)). In-scope surfaces lose their `dark:` pairs, and the closure report gives the before/after count on those surfaces.
  - Light is the default, so e2e and design-partner demos keep today's look unless a user opts in. Playwright's default `colorScheme` is light, so System resolves to light in e2e.
  - The debt list makes the remaining dark-mode work visible and assignable.
- Negative:
  - `next-themes` is a new runtime dependency, alongside `@tanstack/react-table` ([ADR-369](ADR-369-datatable-tanstack-client-mode.md)).
  - The preference is stored per browser and doesn't follow the user across devices. A server-side preference would need a write endpoint, which this phase forbids.
  - Users who choose Dark or System can reach long-tail pages on the debt list before they are fixed.
  - Nearly all of the ≈4,600 `dark:` occurrences stay on long-tail pages until those pages are next touched.
  - Shell and sidebar vitests that assert colour classes (`nav-zone.test.tsx`, `desktop-sidebar.test.tsx`, `mobile-sidebar.test.tsx`) are rewritten when the sidebar moves to tokens.

**Related**: [ADR-367](ADR-367-design-system-layering.md) (tokens live in `@b2mash/ui`; the "no `dark:` pair where a token exists" rule), [ADR-370](ADR-370-chart-kit-and-palette.md) (chart and status tokens per mode), [ADR-368](ADR-368-admincn-template-adoption.md) (template theme presets are not adopted), [ADR-076](ADR-076-separate-portal-app.md) (portal is a separate app; it stays light); [architecture §15](../architecture/phase90-ui-modernization.md).
