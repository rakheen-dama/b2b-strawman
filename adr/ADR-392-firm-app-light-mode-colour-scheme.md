# ADR-392: Firm-app light-mode colour scheme: decided design-first, scoped to the firm shell

**Status**: Proposed — pending FD-1 (architecture §18.10). Decided here: the process, the scoping mechanism and the recommendation. The scheme-implement slice (E1A) records the founder's pick in this ADR and marks it Accepted (orchestrator-authorised). On acceptance it supersedes the light-sidebar ruling of [architecture §15.2.5](../architecture/phase90-ui-modernization.md) (`phase90-ui-modernization.md:234`, "Light keeps the slate-950 sidebar deliberately…") and closes §15.12 open question #3. ADR-367–ADR-374 are not edited.

**Context**:

Phase 90 kept a near-black (`--slate-950`) sidebar in light mode next to a near-white canvas, with black primary buttons (`--primary: var(--slate-950)`), as "Signal Deck's strongest identity mark" (§15.2.5). Its open question #3 said that if the founder preferred a light rail it would be "a token-only change in `frontend/app/globals.css`". On 2026-11-12 the founder asked for an alternative (Phase 91 requirements, RD-2), to be decided on real pages before anything is built. Dark mode is out of scope and keeps its dark rail.

Facts that shape the decision (architecture §18.0 rows 19–20, 23, 29):

- **Where tokens live.** Shared tokens are in `packages/ui/styles/globals.css`, imported verbatim by the portal (`portal/app/globals.css:12`) and by the frontend. The sidebar tokens and `--brand-color` are in `frontend/app/globals.css` (`:27-58`), the only app-scoped token layer.
- **`frontend/app/globals.css` is not firm-app-only.** The root layout imports it for every frontend route: marketing `/` (whose canvas is `body { @apply bg-background }`, `packages/ui/styles/globals.css:283-285`), auth, mock login, `accept/[token]`, and the pre-org `(app)` routes. A plain `:root` override of `--background` or `--ring` there reaches marketing.
- **Teal steps.** Only `--teal-500` and `--teal-600` are Kazi-tuned (`packages/ui/styles/globals.css:103-104,144-145`); `teal-50`, `teal-700`, `teal-950` are Tailwind's stock palette. White on teal-600 measures about 3.2:1 (scratch conversion; the requirements quote 3.7 — it fails 4.5:1 either way). That pair is used twice: `Button variant="accent"` (`packages/ui/src/button.tsx:14`, `bg-teal-600 text-white`, 45 call sites) and every focused menu or select item (`focus:bg-accent focus:text-accent-foreground`, `ui/dropdown-menu.tsx:64`, `ui/select.tsx:103`, with `--accent: var(--teal-600)`). `--accent` is themed: `.dark` sets `--accent: var(--teal-500)` with white text (`packages/ui/styles/globals.css:232-233`), while the button recipe is hard-coded teal-600 in both themes — so in dark mode the accent button is also 3.16:1 and focused menu items are 2.25:1. A button recipe pointed at `--accent` would therefore lighten every dark-mode accent button.
- **Shell structure.** The installed shadcn sidebar supports `variant="inset"` (`ui/sidebar.tsx:158,181,333`); AppSidebar uses the default variant (`app-sidebar.tsx:200-205`); `<main id="main-content">` paints its own `bg-background` (`$FA/layout.tsx:142`); `SidebarInset` renders a `<div>` so `<main>` stays the only main landmark (`ui/sidebar.tsx:325`).
- **Tests.** `__tests__/theme/token-contrast.test.ts` parses the real CSS, but `color-math.ts` `readBlock` reads only `:root` and `.dark` (`:39`). The chart palette was validated against the card surface, not the canvas.
- **Radix portals.** Dialogs, sheets, popovers, menus and toasts mount under `<body>`, outside any app wrapper element.

**Options Considered**:

*Which scheme* (prototyped as P1 on an unmerged branch, judged on screenshots; architecture §18.2.1 has the token table):

1. **Keep the slate-950 rail (status quo).**
   - Pros: No work; strongest contrast mark; matches the marketing site's dark slabs.
   - Cons: The founder asked for an alternative. Black primary buttons and the black rail are what makes the app read heavy. The accent and menu-focus AA failure stays.
2. **A — light rail (≈96.5% L) with the content in a shadcn inset card; active item a teal-tinted pill with a teal-700 indicator; primary slate-900 (A1) or teal-700 (A2).**
   - Pros: The calmest option; the Linear/Vercel/admincn pattern; the work area becomes the raised surface; the CLI sidebar already supports it; rail text and pill pass AA comfortably (9.4:1, 11.6:1).
   - Cons: The largest structural change (inset variant, `<main>` background, header inside a rounded card, sticky offsets to re-check); rail and canvas differ by luminance only 1.09:1, so separation relies on the inset border and shadow; teal-500/600 fail as the indicator on a light rail (2.0, 2.85:1), so a darker tuned teal is needed. A2 makes default and accent buttons the same colour.
3. **B — tinted slate-teal rail (≈94% L) beside a white canvas, white active pill, teal-700 indicator.**
   - Pros: A visible two-zone layout without a black slab; no structural change.
   - Cons: A tinted rail ages quickly next to coloured status badges and charts; rail vs canvas 1.17:1 needs a border; a light gold brand colour all but disappears (1.6:1).
4. **C — deep ink-teal rail (≈28% L), white text, teal-700 primary buttons.**
   - Pros: Keeps the strongest brand mark and warms it; the dark rail keeps light brand colours legible.
   - Cons: Still a dark slab, which is what the founder asked to move away from; a dark burgundy brand colour reads at 1.5:1 on it — the Phase 88 "dark brand on a dark rail" problem returns; brand-forward primaries flatten the default/accent hierarchy.

*Where the chosen tokens are written*:

5. **Change the shared tokens in `packages/ui/styles/globals.css`.**
   - Pros: One place; the portal would match.
   - Cons: Reaches the portal and marketing — exactly what FD-2 must decide, not assume; every iteration runs PORTAL-GATE; marketing's dark slabs are hard-coded and would clash with a new canvas.
6. **Override `:root` in `frontend/app/globals.css`, unscoped.**
   - Pros: What §15.12 OQ #3 expected; one small block.
   - Cons: Reaches marketing, auth, mock login and `accept/[token]` through the shared root layout — "firm app only" would not be true.
7. **Set the tokens on the firm shell's wrapper element.**
   - Pros: Scoped by the DOM; no `:has()`.
   - Cons: Radix portals mount under `<body>` and would not inherit them: a dialog's primary button would keep the old colour while the page behind it changes.
8. **A scoped root block in `frontend/app/globals.css`: `:root:not(.dark):has([data-app-shell="firm"])`, with the org layout setting `data-app-shell="firm"` (chosen).**
   - Pros: Tokens sit on `:root`, so portals get them; only the firm shell renders the marker, so marketing, auth and pre-org routes are untouched and the portal never loads the file; `:not(.dark)` keeps the dark blocks winning in dark mode (specificity 0,3,0 would otherwise beat `.dark`'s 0,1,0); client navigation from `/` into `/org/…` switches without a reload; the head script (ADR-374) is untouched.
   - Cons: A `:has()` selector (supported by every browser Kazi targets; one attribute on one element); the token-contrast loader must learn to read a scoped block; two places hold light values (shared `:root` and the scoped block), so the file's header comment must say which wins.

**Decision**: The scheme is decided design-first — P1 prototypes A1, A2, B and C as token blocks on an unmerged branch and shoots them on real pages (Home, a matter detail, Settings › Firm › General, the fee-notes list, the matters list) at 1440 and 1024 with the rail expanded and collapsed, with a non-teal firm brand colour, AA tables and the dataviz palette check per candidate; the founder picks (FD-1); E1A implements the pick as option 8, merging before any other Phase 91 surface slice. **Recommendation: A1** — light rail with the inset canvas, slate-900 primary buttons, `--accent` retuned. Whatever the pick:

- New teal steps are Kazi-tuned OKLCH raw tokens in `frontend/app/globals.css` with a `kazi-` prefix (`--kazi-teal-50/100/700/950`, starting values in architecture §18.2.1), never Tailwind's stock `teal-*` utilities, which stay stock.
- `--card` is not changed, so the chart palette's validation surface holds; the palette is still re-checked against the new canvas.
- If the pick retunes the accent (all three candidates do): two **new** shared tokens, `--button-accent` and `--button-accent-foreground`, are added to `packages/ui/styles/globals.css` in both `:root` and `.dark` with today's values (teal-600, white), and `Button variant="accent"` reads `bg-(--button-accent) text-(--button-accent-foreground) hover:bg-(--button-accent)/90` (the `(--x)` form, which tailwind-merge handles — 638B lesson). Only the scoped `:root:not(.dark)` firm block overrides them, and `--accent`, to the Kazi-tuned teal-700. Marketing, `accept/[token]`, the portal and **dark mode** therefore render the same pixels, proven by before/after screenshots (including a dark accent button and a dark focused menu item) and PORTAL-GATE. Otherwise the light AA failure is kink K-04.
- The pre-existing **dark** accent failure (kink K-21: button 3.16:1, focused menu item 2.25:1) is recorded, not fixed here, unless FD-1 decides otherwise.
- `--brand-color` stays a decorative 3px accent bar, never text.
- The `.dark` blocks and every existing shared `packages/ui` token value are unchanged; the only shared-file change is the two additive `--button-accent` tokens at today's values. The portal and marketing do not follow: FD-2 was ruled on 2026-11-22 — firm app only, as recommended.

**Rationale**:

A palette is a judgement made by looking, and Phase 90's own lesson 13 is that a token change reaches untouched pages; so the candidates are shown on real pages with real data, the founder decides on screenshots, and the implement slice walks every route in light before merging. A1 is recommended because it removes the heavy black slab without inventing a new identity colour, uses a structure the installed component already supports, keeps a clear hierarchy between the default (slate-900) and accent (teal) actions that pages like the fee-note header rely on (`invoice-header-actions.tsx:68,77,87`), and fixes the two light-mode AA failures that share the teal-600 fill, without touching dark mode, where `--accent` already means something else. The scoped root block is the only mechanism that is simultaneously firm-only, portal-safe and correct for content rendered in Radix portals.

**Consequences**:

- Positive:
  - The founder's request is met with evidence on file (`qa_cycle/evidence/phase91-ui/scheme/`), and the reversal of a deliberate Phase 90 ruling is recorded rather than drifted into.
  - Accent buttons and focused menu items pass AA in the firm app.
  - Portal and marketing are untouched (FD-2, ruled 2026-11-22).
- Negative:
  - Two light token sources in the frontend (shared `:root`, scoped firm block); the header comment of `frontend/app/globals.css` and the contrast test must keep them honest.
  - Two more shared tokens (`--button-accent`, `--button-accent-foreground`) exist only to decouple the button from the themed `--accent`; PORTAL-GATE runs for E1A.
  - Dark-mode accent contrast stays below AA (K-21) until a later decision.
  - Candidate A changes shell structure, so every sticky offset and the floating header must be re-observed; later slices' screenshots differ from Phase 90's evidence set (E1A re-shoots a companion set).
  - `teal-700` the utility and `--kazi-teal-700` the token are different colours; the prefix exists to make that obvious, but reviewers must still catch stock-teal utilities in new code.
  - Docs and marketing screenshots that show the app shell go stale (re-shot or listed at the exit gate).

**Related**: [ADR-367](ADR-367-design-system-layering.md) (L0 tokens; the app-only layer), [ADR-370](ADR-370-chart-kit-and-palette.md) (palette validated against the card), [ADR-371](ADR-371-dark-mode-light-default.md) (dark mode, `/org/` only, portal never dark — unchanged), [ADR-374](ADR-374-theme-first-paint-head-script.md) (head script — untouched), [ADR-076](ADR-076-separate-portal-app.md) (portal is a separate app); [architecture §18.2.1, §18.3.1, §18.6.1, §18.10 FD-1/FD-2](../architecture/phase91-ui-modernization-ii.md), [§15.2.5](../architecture/phase90-ui-modernization.md); slices P1, E1A.
