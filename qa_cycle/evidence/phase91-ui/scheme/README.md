# Phase 91 · 661A — Light-mode colour scheme: founder pack (FD-1)

**Status: OPEN — awaiting FD-1.** This folder is evidence only. No product file changes on `main`. The four candidate schemes live on a prototype branch that is **never merged** (architecture §18.3.1 P1, X4). 661B starts only after § Decision below is filled in (P-3).

**What you are deciding:** which light colour scheme the firm app gets (A1, A2, B or C, or a variant), which colour the primary buttons are, and whether the pre-existing dark-mode accent contrast failure (K-21) gets fixed in 661B. The question is quoted word for word at the end of this file. The recommendation is **A1**.

Sources: [`tasks/phase91-ui-modernization-ii.md`](../../../../tasks/phase91-ui-modernization-ii.md) Epic 661 (661.1–661.6) · [architecture §18.2.1, §18.3.1, §18.6.1](../../../../architecture/phase91-ui-modernization-ii.md) · [ADR-392](../../../../adr/ADR-392-firm-app-light-mode-colour-scheme.md) (Proposed, pending FD-1).

---

## Decision

> _To be filled in by the orchestrator once the founder rules on FD-1. 661B refuses to start while this section is empty (P-3)._
>
> - Candidate:
> - Primary-button colour:
> - K-21 (dark accent) fixed in 661B?:
> - Variant requested? (if yes: addendum PR `phase91/61A-scheme-variant-<n>`):
> - Ruled on (date, by):

---

## 1. The prototype

| | |
|---|---|
| Branch | `phase91/p1-scheme-prototype` (pushed to origin; **no PR, never merged**; 661B deletes it after FD-1 unless the founder asks to keep it) |
| Head SHA | `5caf1b74a0b69f32f18aafbdae52ee013b42ef8b` |
| Base | `main` @ `7b7107d021d3fb201bb6794cce0996c066d8d7af` (Phase 93 complete; see § 2) |
| Diff vs base | 4 files, +118 / −1: `frontend/app/globals.css`, `frontend/components/shell/app-sidebar.tsx`, `packages/ui/styles/globals.css`, `packages/ui/src/button.tsx` |

What the branch changes (661.2):

- **`frontend/app/globals.css`**
  - Adds four raw `--kazi-teal-*` tokens in `:root`, at the §18.2.1 starting values: 50 `#e4f9f5`, 100 `#caf0e9`, 700 `#00776a`, 950 `#00312e`.
  - Adds one block per candidate, selected by `:root:not(.dark)[data-scheme-candidate="a1" | "a2" | "b" | "c"]`. Each block sets exactly the tokens in the §18.2.1 candidate table:
    - `--sidebar*`
    - `--background`
    - `--primary` / `-foreground`
    - `--accent` / `-foreground`
    - `--button-accent` / `-foreground`

    `--card`, `--border` and `--ring` are unchanged in every candidate.
  - One structural rule for A, the "Shell structure" row of §18.2.1: `#main-content` drops its own fill, so the inset card paints the work surface.
- **`packages/ui/styles/globals.css`**: adds `--button-accent: var(--teal-600)` and `--button-accent-foreground: oklch(100% 0 0)` to both `:root` and `.dark`. These are today's values, so nothing changes outside the candidate blocks.
- **`packages/ui/src/button.tsx`**: the `accent` recipe becomes `bg-(--button-accent) text-(--button-accent-foreground) hover:bg-(--button-accent)/90`.
- **`frontend/components/shell/app-sidebar.tsx`**: the org layout's client boundary gets a throwaway `?scheme=` handler. It sets `data-scheme-candidate` on `<html>`, and for A1/A2 the `Sidebar` switches to `variant="inset"`.

**The Kazi-tuned teal steps were not retuned.** Every §18.6.1 pair passes at the starting values (§ 4), so the prototype ships them unchanged.

### How to reproduce

1. Start a fresh stack from a checkout of the prototype branch. The E2E stack builds from relative paths:

   ```bash
   git worktree add ../wt-scheme origin/phase91/p1-scheme-prototype && cd ../wt-scheme
   bash compose/scripts/e2e-down.sh && docker builder prune -f && bash compose/scripts/e2e-up.sh
   ```

2. Populate data (§ 3).
3. Sign in at <http://localhost:3001/mock-login> → **Sign In** (Alice, owner).
4. Add `?scheme=` to any `/org/e2e-test-org/…` URL:

| URL parameter | Scheme |
|---|---|
| `?scheme=today` | today's slate-950 rail; also clears the choice |
| `?scheme=a1` | A1: light rail, inset canvas, slate-900 primary buttons |
| `?scheme=a2` | A2: as A1, with teal-700 primary buttons |
| `?scheme=b` | B: tinted slate-teal rail, white canvas |
| `?scheme=c` | C: deep ink-teal rail, teal-700 primary buttons |

The choice is kept in `localStorage` (`kazi-scheme-candidate`), so it survives client navigation. `?scheme=today` clears it. The candidates are light-mode only: in dark mode every token block is inert.

**The 661A stack was left running on this build**, so you can click through the schemes live at :3001.

---

## 2. Snapshot (provisional — not the phase baseline)

These are the §18.1 metric commands, run verbatim on `main` @ `7b7107d02`, 2026-11-25 18:11 UTC. They are for this pack only (P-2). **661B records the authoritative baseline** on its own HEAD before it edits anything.

Script: [`scripts/01-snapshot-metrics.sh`](scripts/01-snapshot-metrics.sh) → [`scripts/snapshot-metrics.txt`](scripts/snapshot-metrics.txt).

| Metric | §18.1 (`5aae8f4ab`) | Now (`7b7107d02`) |
|---|---|---|
| Pages under `org/[slug]` | 58 | 58 |
| `PageHeader` adoption | 7 | 7 |
| Files with `<h1` | 71 | 71 |
| Display h1 (`font-display text-3xl`) | 92 | 92 |
| Direct tables by area | settings 10, trust-accounting 6, invoices 4, legal 1, documents 1 | settings 10, trust-accounting 6, invoices 4, legal 1, documents 1 |
| Raw hex (PCRE, `*.tsx`) | 32 | **33** |
| `dark:` lines, whole frontend | 3 647 | **3 842** |
| EmptyState calls without `variant` | 53 (guard report) | not measured: the guard is created in 663A |

The raw-hex and `dark:` counts grew after the breakdown. Phase 93's take-on screens are the likely source.

**Phase 93 on `main`:**

- The take-on tab is in `frontend/lib/settings-nav.ts:181` (`id: "take-on"`).
- Its loader and actions are `settings/matters/_tabs/take-on-tab.tsx` and `settings/matters/take-on-actions.ts`.
- Phase 93 was closed by 660A, PR #1858 (`23e09c41b`). The post-phase fixes are #1859 (`deb79ed8a`), #1860 (`92e287539`) and #1861 (`d8aaba16a`).
- `main` HEAD is `7b7107d02`, the handover docs commit.

---

## 3. Data on the stack

1. **Fresh stack** built from the prototype branch: `e2e-down` → `docker builder prune -f` → `e2e-up`. It was built at `0997fa7cc`. The frontend container was then rebuilt with `e2e-rebuild.sh frontend` at the final head `5caf1b74a`, which differs only by a lint comment.
2. **`pnpm test:e2e:legal-lifecycle`** (mock auth, :3001): **105 passed, 0 failed, 0 skipped (3.0 min)**. Log: [`scripts/lifecycle-population.txt`](scripts/lifecycle-population.txt). The tree it dirtied in the worktree was restored afterwards.
3. **Top-up through the UI** (never SQL). The lifecycle suite logs all its time inside the current 30 days, and leaves unbilled time for one fee earner only. As a result, Home's StatCards drew no delta and the "Unbilled WIP" widget showed one row instead of the chart. Two scripted Log Time entries fixed the deltas:
   - [`04-topup-time-entries.cjs`](scripts/04-topup-time-entries.cjs): Alice logs 2 h billable dated 40 days ago, and 1 h non-billable today, on Sipho's litigation matter.

   After that, **Hours logged ↑ +637.5 %** and **Billable % ↓ −6.8 pp**. The dashboard's org cache has a 3-minute TTL, so the change showed up after a wait.

   Matrix run 1 ([`05-shoot-matrix.cjs`](scripts/05-shoot-matrix.cjs), log [`shoot-matrix-run1.txt`](scripts/shoot-matrix-run1.txt)) then showed that the WIP chart only renders for **two or more fee earners** (`unbilled-wip-widget.tsx`, `earners.length >= 2`). So a second top-up was added:
   - [`06-topup-second-earner.cjs`](scripts/06-topup-second-earner.cjs): Bob logs 3 h billable today.

   The final state is **Hours logged 17.8 h, ↑ +787.5 %**, **Billable % 94.4 %, ↓ −5.6 pp**, and the WIP chart drawn with two bars.
4. **The final matrix** is run 2: [`07-shoot-matrix-rerun.cjs`](scripts/07-shoot-matrix-rerun.cjs). It is byte-identical to 05 apart from a header and the usage lines. It overwrote every PNG from run 1. Its log is [`shoot-matrix.txt`](scripts/shoot-matrix.txt) and its per-shot probe is [`shoot-manifest.json`](scripts/shoot-manifest.json), which records the attribute, the computed `--sidebar`, `--background` and `--button-accent`, the rail paint, the sidebar variant and state, and the h1.

---

## 4. The screenshot matrix (661.3)

The matrix covers 5 schemes × 5 pages × 2 widths × 2 rail states, which is **100 viewport PNGs** at `<scheme>/<page>-<width>-<expanded|collapsed>.png`, plus **10 brand-colour crops** at `<scheme>/brand-<burgundy|gold>.png`.

- **Schemes** (folder names): `today`, `a1`, `a2`, `b`, `c`.
- **Pages:**
  - `home`: owner Home. It shows the StatCards with the ↑ hours delta and the ↓ billable delta, the Unbilled WIP chart, and the floating header.
  - `matter`: Sipho's litigation matter, "Ndlovu v RAF". It shows the hero, the status badges and the grouped tabs.
  - `settings-firm-general`: Settings › Firm › General, the cards and forms as they look today.
  - `fee-notes`: the fee-notes list, an unmigrated long-tail page.
  - `matters`: the matters list, which is a DataTable.
- **Widths:** `1440` (1440×900) and `1024` (768 high).
- **Rail states:** set by the shadcn `sidebar_state` cookie.
- **Brand-colour crops:** each is the sidebar header (logo, the `org-brand-accent` bar and the org name, the search pill) at 1440, rail expanded, 2× DPR.
  - The brand colour was set through **Settings › Firm › General → Save Settings**: first burgundy `#7b2432`, then gold `#e7b643`.
  - It was then **restored to the original `#1B3A4B`** through the same form. The save confirmation was seen and the value persisted after a reload (`shoot-manifest.json` → `brand`).
  - The 100 matrix shots carry that original `#1B3A4B` brand bar.

Every shot's probe (`scripts/shoot-manifest.json`) confirms that the right candidate attribute was on `<html>`. The check found 0 mismatches across 110 shots. The probe also confirms `variant="inset"` for A1/A2 only, and the rail state each shot asked for.

Quick index, at 1440 with the rail expanded:

| Page | Today | A1 | A2 | B | C |
|---|---|---|---|---|---|
| Home | [today](today/home-1440-expanded.png) | [a1](a1/home-1440-expanded.png) | [a2](a2/home-1440-expanded.png) | [b](b/home-1440-expanded.png) | [c](c/home-1440-expanded.png) |
| Matter detail | [today](today/matter-1440-expanded.png) | [a1](a1/matter-1440-expanded.png) | [a2](a2/matter-1440-expanded.png) | [b](b/matter-1440-expanded.png) | [c](c/matter-1440-expanded.png) |
| Settings › Firm › General | [today](today/settings-firm-general-1440-expanded.png) | [a1](a1/settings-firm-general-1440-expanded.png) | [a2](a2/settings-firm-general-1440-expanded.png) | [b](b/settings-firm-general-1440-expanded.png) | [c](c/settings-firm-general-1440-expanded.png) |
| Fee notes | [today](today/fee-notes-1440-expanded.png) | [a1](a1/fee-notes-1440-expanded.png) | [a2](a2/fee-notes-1440-expanded.png) | [b](b/fee-notes-1440-expanded.png) | [c](c/fee-notes-1440-expanded.png) |
| Matters list | [today](today/matters-1440-expanded.png) | [a1](a1/matters-1440-expanded.png) | [a2](a2/matters-1440-expanded.png) | [b](b/matters-1440-expanded.png) | [c](c/matters-1440-expanded.png) |
| Brand: burgundy | [today](today/brand-burgundy.png) | [a1](a1/brand-burgundy.png) | [a2](a2/brand-burgundy.png) | [b](b/brand-burgundy.png) | [c](c/brand-burgundy.png) |
| Brand: gold | [today](today/brand-gold.png) | [a1](a1/brand-gold.png) | [a2](a2/brand-gold.png) | [b](b/brand-gold.png) | [c](c/brand-gold.png) |

The `-1024-` and `-collapsed` variants sit next to each file in the same folder.

**Where to look:**

| Difference | Where to see it |
|---|---|
| Primary vs accent button | `matters` ("New Matter" is primary; "New from Template" is outline) and `fee-notes` ("New fee note" is accent) |
| The shell | `home` |
| Rail active pill and indicator | every page; the rail is the left column |

**Extra evidence for one finding:** `found/dark-home-1440-{today,a1}.png` (§ 9, F-661A-3).

---

## 5. AA contrast tables (661.4)

These numbers come from the repo's colour maths, [`frontend/__tests__/theme/color-math.ts`](../../../../frontend/__tests__/theme/color-math.ts) (sha256 `c14b6f10…`). It is compiled with the frontend's own `tsc` and run against the **prototype branch's CSS**: [`scripts/02-aa-tables.sh`](scripts/02-aa-tables.sh) + [`02-aa-tables.cjs`](scripts/02-aa-tables.cjs). Outputs: [`aa-tables.txt`](scripts/aa-tables.txt) (with hexes) and [`aa-tables.json`](scripts/aa-tables.json).

**How the cascade is modelled:**

| Scheme | Tokens |
|---|---|
| Today (light) | shared `:root` + app `:root` |
| Candidate | today + its `:root:not(.dark)[data-scheme-candidate=…]` blocks |
| Dark | today + shared `.dark` + app `.dark` |

The script also checks that every block that names `data-scheme-candidate` and declares a token is `:root:not(.dark)`-guarded. Result: `true`. So **the dark column is the same for every candidate**.

Translucent fills, such as the rail's white 6 % / 8 % active pill and the `/50`–`/80` text opacity modifiers, are composited over the rail in sRGB first, the way browsers paint them.

The **floor** is 4.5:1 for text and 3:1 for non-text. Rows tagged **[info]** are not in §18.6.1's list. They are measured because the light rails expose them (§ 9, F-661A-1).

| Pair | Floor | Today | A1 | A2 | B | C | Dark (every candidate) |
|---|---|---|---|---|---|---|---|
| body text on canvas (--foreground / --background) | 4.5 | 16.58 | 17.79 | 17.79 | 17.79 | 16.58 | 20.03 |
| body text on card (--card-foreground / --card) | 4.5 | 17.79 | 17.79 | 17.79 | 17.79 | 17.79 | 17.96 |
| muted text on canvas (--muted-foreground / --background) | 4.5 | 7.03 | 7.54 | 7.54 | 7.54 | 7.03 | 7.51 |
| muted text on card (--muted-foreground / --card) | 4.5 | 7.54 | 7.54 | 7.54 | 7.54 | 7.54 | 6.74 |
| rail text (--sidebar-foreground / --sidebar) | 4.5 | 20.03 | 9.41 | 9.41 | 15.12 | 14.33 | 19.32 |
| active item text on its fill (--sidebar-accent-foreground / --sidebar-accent over rail) | 4.5 | 17.92 | 11.6 | 11.6 | 17.96 | 11.28 | 17.02 |
| active indicator bar vs rail (--sidebar-primary / --sidebar, non-text) | 3 | 8.92 | 4.91 | 4.91 | 4.6 | 6.38 | 8.6 |
| primary-button label (--primary-foreground / --primary) | 4.5 | 20.03 | 17.96 | 5.46 | 17.96 | 5.46 | 20.03 |
| accent button label (--button-accent-foreground / --button-accent) | 4.5 | **3.16 FAIL** | 5.46 | 5.46 | 5.46 | 5.46 | **3.16 FAIL (K-21)** |
| focused menu/select item (--accent-foreground / --accent) | 4.5 | **3.16 FAIL** | 5.46 | 5.46 | 5.46 | 5.46 | **2.25 FAIL (K-21)** |
| focus ring on rail (--sidebar-ring / --sidebar, non-text) | 3 | 8.92 | 3.48 | 3.48 | 3.25 | 6.38 | 8.6 |
| focus ring (--ring) vs rail (non-text) | 3 | 5.18 | 3.48 | 3.48 | 3.25 | 3.71 | 8.6 |
| focus ring vs canvas (--ring / --background, non-text) | 3 | 3.57 | 3.83 | 3.83 | 3.83 | 3.57 | 8.92 |
| focus ring vs card (--ring / --card, non-text) | 3 | 3.83 | 3.83 | 3.83 | 3.83 | 3.83 | 8 |
| [info] rail group label (text-sidebar-foreground/50 on rail) | 4.5 | 5.34 | **2.55 FAIL** | **2.55 FAIL** | **3.26 FAIL** | 4.68 | 5.37 |
| [info] rail org name (text-sidebar-foreground/80 on rail) | 4.5 | 12.7 | 5.36 | 5.36 | 8.55 | 9.66 | 12.38 |
| [info] search pill text (text-sidebar-foreground/60 on --sidebar-accent over rail) | 4.5 | 7.03 | **3.11 FAIL** | **3.11 FAIL** | 4.69 | 5.16 | 6.84 |
| [info] user-footer email (text-sidebar-foreground/60 on rail) | 4.5 | 7.3 | **3.21 FAIL** | **3.21 FAIL** | **4.43 FAIL** | 6.04 | 7.29 |
| [info] rail vs canvas separation (--sidebar / --background, luminance only) | — | 18.49 | 1.1 | 1.1 | 1.18 | 13.23 | 1.04 |
| [info] canvas vs card (--background / --card) | — | 1.07 | 1 | 1 | 1 | 1.07 | 1.11 |

**What the table says:**

- **Every §18.6.1 pair passes for every candidate.** That includes the two that fail today: the light accent button and the focused menu item (K-04), white on teal-600 at 3.16. Every candidate moves both to `--kazi-teal-700` at 5.46.
- **The dark column is unchanged, and K-21 stands.**
  - The dark accent button is still white on teal-600, **3.16:1**.
  - The dark focused menu item is still white on teal-500, **2.25:1**.

  The candidates cannot fix these, because their blocks never match under `.dark`. Fixing them is the FD-1 sub-question.
- **The light rails expose translucent rail text** (the **[info]** rows). On A and B, three pieces of text fall below 4.5:1:
  - the uppercase group labels ("TODAY", "MATTERS"…), drawn at `/50` opacity: 2.55 on A, 3.26 on B;
  - the search-pill placeholder: 3.11 on A;
  - the user-footer email: 3.21 on A, 4.43 on B.

  On today's black rail and on C, the same opacities pass. These are component opacity classes, not tokens, so no candidate token block can fix them. Whichever light rail is picked, 661B needs a token for them, for example `--sidebar-muted-foreground` (§ 9, F-661A-1).
- **Rail vs canvas** measures 1.10 for A and 1.18 for B, against 1.09 / 1.17 in §18.6.1's scratch numbers. Either way it is luminance-only separation, so the zones are told apart by border and shadow (§ 7).

How the measured numbers differ from §18.6.1's scratch table. None of these changes a pass or a fail.

| Pair | §18.6.1 scratch | Measured | Note |
|---|---|---|---|
| A indicator bar | 5.0 | 4.91 | |
| C active item | 12.0 | 11.28 | composited white 8 % over the rail |
| A rail vs canvas | 1.09 | 1.10 | |
| B focus ring vs rail | 3.3 | 3.25 | |

---

## 6. Chart palette — dataviz `validate_palette.js` (661.4)

`chart-1..5` was checked against each candidate's **canvas** (`--background`) and **card** (`--card`) in light mode, and against the unchanged dark canvas and card. Script: [`scripts/03b-palette-validator.sh`](scripts/03b-palette-validator.sh). Output: [`palette-validator.txt`](scripts/palette-validator.txt).

- Validator: the dataviz skill's `validate_palette.js`, sha256 `25df43be…` (bundled skill 2.1.330).
- The first attempt, `03-palette-validator.sh`, failed before running any check: node 20 loaded the ES-module validator as CommonJS. Its output is kept in [`03-palette-validator.failed-esm.txt`](scripts/03-palette-validator.failed-esm.txt).
- The slots are light `#009787 #e06623 #096acb #c8800d #894ed6` and dark `#00aa99 #df6c32 #3986e4 #bd891c #9d6fe3`.

| Surface | Today | A1 | A2 | B | C | Dark |
|---|---|---|---|---|---|---|
| canvas | `#f4f6f8`: PASS, **WARN** (chart-4 amber `#c8800d` 2.96:1) | `#fdfeff`: PASS | `#fdfeff`: PASS | `#fdfeff`: PASS | `#f4f6f8`: PASS, **WARN** (amber 2.96:1) | `#07080b`: PASS |
| card | `#fdfeff`: PASS | PASS | PASS | PASS | PASS | `#14171c`: PASS |

- On every surface: lightness band PASS, chroma floor PASS, worst adjacent CVD ΔE 12.3 (light, protan) / 13.6 (dark, deutan), normal-vision floor 26.4 / 26.0, and `--pairs all` on the first three slots PASS.
- **The only flag is a WARN, and it predates Phase 91.** It applies wherever the canvas is today's 97.2 % (today and C): chart-4 amber reads at 2.96:1, just under 3:1. The palette was validated against the card only (ADR-370), and Kazi's charts are drawn inside cards, so this is informational. **A1, A2 and B clear it**, because their canvas is the card colour.

---

## 7. Brand colour (661.3, 661.4)

`--brand-color` is a decorative 3 px bar beside the org name, never text (G-A3), so no ratio is required. These are the ratios against each rail (`aa-tables.txt`):

| Rail | Burgundy `#7b2432` | Gold `#e7b643` | Crops |
|---|---|---|---|
| Today (slate-950 `#07080b`) | 2.04 | 10.65 | [burgundy](today/brand-burgundy.png) · [gold](today/brand-gold.png) |
| A1 / A2 (`#f1f3f7`) | 8.83 | **1.69** | [burgundy](a1/brand-burgundy.png) · [gold](a1/brand-gold.png) |
| B (`#e0eef2`) | 8.26 | **1.58** | [burgundy](b/brand-burgundy.png) · [gold](b/brand-gold.png) |
| C (`#003030`) | **1.46** | 7.62 | [burgundy](c/brand-burgundy.png) · [gold](c/brand-gold.png) |
| Dark (every candidate, `#0c0e12`) | 1.97 | 10.27 | — |

- **Light rails (A, B):** a dark brand colour reads clearly; a pale gold all but disappears (1.6–1.7).
- **Dark rails (C, and today):** the reverse. A dark burgundy disappears. This is the Phase 88 "dark brand on a dark rail" problem (628B), and C brings it back in light mode.
- **Whatever the pick, dark mode has the dark-rail behaviour**, because the dark rail is unchanged.
- **The org name next to the bar carries the identity**, so a faint bar is a cosmetic loss, not a legibility one.

---

## 8. One paragraph per candidate (661.5)

**Today: the slate-950 rail (the reference).**

- **Strengths:** the strongest mark, and every rail pair passes easily.
- **What reads heavy:** the black rail and the black primary buttons ("New Matter", the "All" filter chip).
- **AA failures:** the light accent button and the focused menu item fail AA (K-04, 3.16:1).
- **Chart:** chart-4 sits just under 3:1 on the 97.2 % canvas (a WARN).
- **Brand colour:** a dark burgundy brand barely shows on the rail (2.04).

**A1: light rail, inset canvas, slate-900 primary (recommended).**

- **The look:** the rail turns into a quiet grey-blue frame (`#f1f3f7`), and the work area becomes one raised white card with rounded corners and a soft shadow. This is the Linear / Vercel / admincn pattern. See `a1/home-1440-expanded.png` and `a1/matters-1440-collapsed.png`.
- **Separating rail from work area:** the two differ in luminance by only **1.10:1**. The inset card's edge, radius and shadow do the separating. In the screenshots it reads clearly at 1440 and at 1024, and in both rail states.
- **The active item:** a mint pill (`--kazi-teal-100`) with a teal-700 indicator bar (4.91:1).
- **Buttons:** slate-900 primary buttons keep today's hierarchy. "New Matter" is dark and "New fee note" (accent) is teal, now at an AA-passing 5.46:1.
- **Costs:**
  - **Translucent rail text fails on the light rail.** The `/50` group labels are 2.55, the search-pill text 3.11 and the user email 3.21. 661B must give them a token (F-661A-1).
  - **The search pill uses the same mint fill as the active item**, so collapsed it reads like a second selected item. See `a1/fee-notes-1024-collapsed.png` (F-661A-2).
  - **The inset card is a component prop**, so it also appears in **dark** mode. That breaks the "dark pixel-identical" promise for A (F-661A-3).
  - **The card's 8 px margin costs 16 px of content width.** The matters DataTable at 1440 expanded starts scrolling one column earlier (F-661A-4).
  - **A gold brand bar nearly vanishes** (1.69).

**A2: as A1, with teal-700 primary.**

- **Same shell and same AA results as A1**, except the primary-button label at 5.46.
- **More brand-forward:** "New Matter" and every default button become teal.
- **It flattens the hierarchy.** Default and accent buttons are now the same colour (`#00776a`). The `default` "New Matter" in `a2/matters-*` and the `accent` "New fee note" in `a2/fee-notes-*` are indistinguishable; in A1 one is slate-900 and the other teal. On the fee-note detail header, which this matrix does not shoot, the accent "Approve" / "Send fee note" would no longer stand apart from the default action beside them (`invoice-header-actions.tsx:68,77,87`, per §18.2.1).
- **The unmigrated long tail mixes colours.** The hard-coded slate-950 filter chips ("All", "Active") now sit next to teal primaries; see `a2/matters-1440-expanded.png`.

**B: tinted slate-teal rail, white canvas, white active pill.**

- **No structural change.** The rail is a pale blue-teal (`#e0eef2`) and the canvas turns white. The active item is a white pill with a teal-700 bar (4.60:1). Rail text is slate-900 (15.12:1).
- **A clear two-zone layout without a black slab.** Rail vs canvas is only **1.18:1**, so the rail border carries the separation.
- **The tint competes with colour.** It sits beside the teal badges ("Sent", "Active"), the teal chart bars and the hard-coded teal settings-rail highlight (`b/settings-firm-general-1440-expanded.png`). It looks a little dated next to coloured status badges.
- **Rail text:** the `/50` group labels (3.26) and the user email (4.43) fail on this rail too.
- **Brand colour:** gold nearly vanishes (1.58).

**C: deep ink-teal rail, white text, teal-700 primary.**

- **Today's look, warmed:** a dark teal slab (`#003030`) with white text (14.33:1) and today's teal-500 indicator (6.38:1). The canvas is unchanged.
- **Every rail pair passes**, including the translucent ones.
- **It is still a dark slab**, which is what the founder asked to move away from.
- **The primary buttons are teal**, so C has A2's hierarchy problem: default and accent are the same colour.
- **The chart-4 WARN stays**, because the canvas is unchanged.
- **Dark brand colours fail on this rail.** A dark burgundy reads at **1.46:1** (`c/brand-burgundy.png`), so the Phase 88 problem comes back in light mode.

---

## 9. Findings and deviations

### Findings

Details are in [`found.md`](found.md). None of these is fixed in 661A.

| ID | Severity | Finding | Owner |
|---|---|---|---|
| F-661A-1 | MEDIUM (if A or B is picked) | Translucent rail text fails AA on the light rails: the `/50` group labels, the `/60` search pill and the `/60` user email (A 2.55 / 3.11 / 3.21; B 3.26 / 4.69 / 4.43). These are component opacity classes, so no token in the candidate blocks fixes them. | 661B (scheme made it illegible) |
| F-661A-2 | LOW | Under A, the search pill uses `bg-sidebar-accent` (the mint active fill). Collapsed, it reads like a second active item. | 661B design note |
| F-661A-3 | MEDIUM (if A is picked) | `variant="inset"` is structural, so A's inset card also renders in **dark** mode (`found/dark-home-1440-a1.png` vs `-today.png`: 8 px margin, 12 px radius, wrapper painted `--sidebar`). That breaks ADR-392's and the task file's "dark pixel-identical" claim for A. | FD-1 / 661B (spec question) |
| F-661A-4 | LOW | A's inset margin costs 16 px of content width. On the matters DataTable at 1440 expanded, the "Next in diary" column clips into horizontal scroll. Today already hides the row-actions column at that width. | 664B (DataTable F1/K-11) |
| F-661A-5 | LOW, pre-existing | chart-4 amber at 2.96:1 on today's 97.2 % canvas (validator WARN). The palette was validated on the card only. | backlog (A/B clear it) |
| F-661A-6 | INFO, pre-existing | A console error on the first dashboard load after mock sign-in: "MockAuthContextProvider: failed to fetch userinfo" (2 of the 5 sign-ins in the final run). Known, and harmless to the shots. | none |

### Deviations from the spec

1. **Data top-up beyond the lifecycle suite.** Two UI-scripted Log Time entries for the StatCard deltas, and one more for the WIP chart (§ 3). Without them, Home could not show "StatCards with an up and a down delta" or "the WIP chart" as 661.3 requires. No SQL was used.
2. **The matrix was shot twice.** Run 1 (`05-shoot-matrix.cjs`) was superseded by the identical `07-shoot-matrix-rerun.cjs` once the WIP chart's two-earner rule surfaced. Run 1's PNGs were overwritten. Its log and manifest are kept as `shoot-matrix-run1.txt` / `shoot-manifest-run1.json`.
3. **The validator was run twice.** `03-palette-validator.sh` failed on ES-module loading before running any check, and `03b` re-ran it with `--experimental-default-type=module`.
4. **A's structural rule applies in both themes**, mirroring what 661B would ship (F-661A-3). The token blocks themselves are light-only, as specified.
5. **The `--kazi-teal-*` values were not retuned.** The §18.2.1 starting values pass every pair. "The prototype tunes them" therefore became "verified as-is".
6. **Brand crop location:** the spec does not name a path. The crops sit in each scheme's folder as `brand-<colour>.png`.

---

## 10. Recommendation

**A1:**

- the light rail with the inset canvas;
- **slate-900 primary buttons**;
- in the scoped light block only, `--accent` (focused menu and select items) and the new `--button-accent` (accent buttons) retuned to `--kazi-teal-700` (`#00776a`, 5.46:1).

**Why A1:**

- It removes the heavy black slab without inventing a new identity colour.
- It uses a structure the installed shadcn sidebar already supports.
- It keeps the default-vs-accent hierarchy that the fee-note header and every action bar rely on.
- It fixes both light-mode AA failures (K-04).
- It clears the chart-4 canvas WARN.

**What 661B must add if A1 is picked:**

- a token for the translucent rail text (F-661A-1);
- a decision on whether the inset card may appear in dark mode (F-661A-3). Either accept it, or make the variant light-only in a way that keeps one `<main>`.

K-21, the dark accent failure, stays recorded in the backlog unless you rule otherwise.

Judge on the screenshots in § 4, not on the tables.

---

## 11. FD-1 — the question (verbatim, `tasks/phase91-ui-modernization-ii.md` FD-1)

> Which light scheme — A (light rail, inset canvas), B (tinted rail), C (deep-teal rail) or a variant — and which primary-button colour (A1 slate-900 / A2 teal-700)? Sub-question: is the pre-existing **dark** accent failure K-21 (white on teal-600 3.16:1; focused menu item white on teal-500 2.25:1) fixed here?

Recommendation: **A1**, with `--accent` and `--button-accent` retuned to `--kazi-teal-700` in the scoped light block only. **K-21 stays recorded (backlog)** unless the founder says otherwise.

If you pick A, please also say how the inset card should behave in dark mode (F-661A-3).

---

## 12. Scripts (committed as executed; never edited after running)

| File | What it does | Output |
|---|---|---|
| `01-snapshot-metrics.sh` | §18.1 metric commands, verbatim, on `main` | `snapshot-metrics.txt` |
| `02-aa-tables.sh` + `02-aa-tables.cjs` | compiles `color-math.ts`; AA pairs per scheme + dark + brand | `aa-tables.txt`, `aa-tables.json`, `aa-tables-run.txt` |
| `03-palette-validator.sh` | validator wrapper (**failed**: ESM) | `03-palette-validator.failed-esm.txt` |
| `03b-palette-validator.sh` | re-run with `--experimental-default-type=module` | `palette-validator.txt` |
| `04-topup-time-entries.cjs` | UI top-up: StatCard deltas | `topup-time-entries.txt` |
| `05-shoot-matrix.cjs` | matrix run 1 (superseded) | `shoot-matrix-run1.txt`, `shoot-manifest-run1.json` |
| `06-topup-second-earner.cjs` | UI top-up: second fee earner (WIP chart) | `topup-second-earner.txt` |
| `07-shoot-matrix-rerun.cjs` | **final** matrix + brand crops + brand restore | 110 PNGs, `shoot-matrix.txt`, `shoot-manifest.json` |
| `08-dark-inset-check.cjs` | F-661A-3 evidence (dark, today vs a1) | `found/dark-home-1440-*.png`, `dark-inset-check.txt` |
| — | data population: `pnpm test:e2e:legal-lifecycle` | `lifecycle-population.txt` |

Run order on the 661A stack: 01 → 02 → 03 / 03b (static), then lifecycle → 04 → 05 → 06 → 07 → 08.

The scripts need:

- node 20 (nvm), with `@playwright/test` resolved from the prototype worktree's `frontend/`;
- the stack on :3001 / :8081 / :8090;
- the lifecycle run file at `$TMPDIR/kazi-legal-lifecycle-run.json`.
