# 661A — found.md (out of scope for 661A; reported, not fixed)

Everything below was observed on the prototype stack, branch `phase91/p1-scheme-prototype` @ `5caf1b74a`, :3001, on 2026-11-25. Each item goes to 661B, to the kinks register (662A), or to the backlog, as stated. **No product code was changed in 661A.**

## F-661A-1: translucent rail text fails AA on the light rails (A, B)

- **Severity:** MEDIUM if A or B is picked.
- **Owner:** 661B. Its remit covers "the scheme made a page illegible".

Three rail texts use opacity modifiers on `--sidebar-foreground`. On the dark rails (today, C) they pass. On the light rails they fall below 4.5:1. Numbers are from `scripts/aa-tables.txt`, the repo colour maths, composited in sRGB:

| Text | Where | Today | A1/A2 | B | C |
|---|---|---|---|---|---|
| Group labels "TODAY", "MATTERS"… (`text-sidebar-foreground/50`, 11 px uppercase) | `components/shell/app-sidebar.tsx:100` | 5.34 | **2.55** | **3.26** | 4.68 |
| Search-pill placeholder (`text-sidebar-foreground/60` on `bg-sidebar-accent`) | `app-sidebar.tsx:275` | 7.03 | **3.11** | 4.69 | 5.16 |
| User-footer email (`text-sidebar-foreground/60`) | `components/sidebar-user-footer.tsx:60` | 7.30 | **3.21** | **4.43** | 6.04 |
| Org name (`/80`) | `app-sidebar.tsx:262` | 12.7 | 5.36 | 8.55 | 9.66 |

Two related uses were not measured: `ui/sidebar.tsx:212` (`[&>button]:text-sidebar-foreground/60`, the mobile sheet close button) and `ui/sidebar.tsx:427` (`text-sidebar-foreground/70`, SidebarGroupLabel's base class; AppSidebar overrides it with `/50` via `cn`).

**Why the prototype doesn't fix it:** these are component classes, not tokens, so no candidate token block can reach them.

**Suggested shape for 661B:** an app-scoped `--sidebar-muted-foreground` token, at today's effective value under `:root` and `.dark` and AA-tuned in the scoped block, used by those three sites. Its exact value should go into the token-contrast test.

## F-661A-2: under A, the search pill looks like an active item

- **Severity:** LOW (design note for 661B).
- **Where:** the search pill (`app-sidebar.tsx:~275`) paints `bg-sidebar-accent`.
- **What happens:** under A that is the mint `--kazi-teal-100`, the same fill as the active nav item. Collapsed, the rail shows two mint squares, the search button and the active page. See `a1/fee-notes-1024-collapsed.png` and `a1/matters-1440-collapsed.png`.
- **Why A only:** on B the pill is white (the same as B's active pill). On C and today it is a 6–8 % white wash, which reads as a neutral field.

## F-661A-3: A's inset card also appears in dark mode

- **Severity:** MEDIUM if A is picked. This is a spec question for FD-1 / 661B.
- **What the spec says:**
  - ADR-392 and the task file (cross-slice facts) say the scheme block "never matches in dark", so "every dark observation must be pixel-identical to the 661B before set".
  - Candidate A's shell change is `Sidebar variant="inset"`, a **component prop** (§18.2.1 "Shell structure", 661.11), and `<main>` drops `bg-background`. Neither is a token.
- **What the prototype shows:** it mirrors that change, and in dark mode the firm shell then renders the inset card.

`scripts/08-dark-inset-check.cjs` → `found/dark-home-1440-{today,a1}.png`, `scripts/dark-inset-check.txt`:

| Probe | today, dark | a1, dark |
|---|---|---|
| `data-variant` | `sidebar` | `inset` |
| `sidebar-inset` rect | x 256, y 0, w 1184 | x 256, **y 8, w 1176** |
| inset radius | 0 px | **12 px** |
| inset shadow | none | `0 1px 3px rgb(0 0 0 / .1), 0 1px 2px -1px rgb(0 0 0 / .1)` |
| wrapper background | transparent | **`--sidebar` (dark rail colour)** |

Dark tokens are untouched, so K-21 and every dark contrast pair are unchanged. **Dark layout pixels are not.** If A is picked, 661B needs a ruling on one of three options:

- (a) accept the inset in dark, and change the "dark pixel-identical" acceptance criterion to "dark tokens identical";
- (b) make the variant light-only, for example by reading the resolved theme in AppSidebar. This has a first-paint cost, because ADR-374's head script sets the class before hydration;
- (c) express the inset purely in CSS under the scoped `:root:not(.dark):has([data-app-shell="firm"])` selector, instead of through the `variant` prop.

**Recommended default: (c)**, ≈ 2 h in 661B; keeps dark mode pixel-identical as ADR-392 promises. The README § 11 puts the three options in plain words for FD-1. (Added after review of PR #1862.)

## F-661A-8: A's inset margin also costs 16 px of rail height (collapsed rail cuts the active item at 1024×768)

- **Severity:** MEDIUM if A is picked. Added after review of PR #1862 (the reviewer found it in the existing screenshots).
- **What happens:** the inset frame's 8 px top and bottom margins take 16 px of height from the rail. The collapsed (icon) rail does not scroll, so items past the bottom are cut. In `a1/fee-notes-1024-collapsed.png` and `a2/fee-notes-1024-collapsed.png` the **active** Fee notes item's mint highlight is cut through the middle (about y = 604), and the icons below it sit about 7 px higher than in today / B / C.
- **Already true today:** at 768 px high the collapsed rail already hides the last FEES items (`today/fee-notes-1024-collapsed.png` shows the active item whole). A moves the cut up into the active item; it does not create the clipping.
- **Owner:** 661B if A is picked (e.g. let the collapsed rail scroll); the pre-existing clipping goes to the 662A kinks register.
- **F-661A-2 note:** F-661A-2 cites the same screenshot for the search pill and did not call this out.

## F-661A-4: A's inset margin narrows the content column by 16 px

- **Severity:** LOW.
- **Owner:** 664B (DataTable F1 / K-11).
- **What happens:** at 1440 with the rail expanded, the matters DataTable's last column ("Next in diary") clips into horizontal scroll under A1/A2 (`a2/matters-1440-expanded.png`).
- **Already true today:** the row-actions column is already off-screen at that width (`today/matters-1440-expanded.png`; it is visible only when collapsed). A makes it 16 px worse; it does not create the problem.

## F-661A-5: chart-4 amber is under 3:1 on today's canvas

- **Severity:** LOW, pre-existing.
- **Owner:** backlog.
- **What the validator says:** `validate_palette.js` WARNs that chart-4 `#c8800d` reads at 2.96:1 against today's `--background` `#f4f6f8` (today and C).
- **Why it predates Phase 91:** the palette was validated against the card only (ADR-370), and charts render inside cards.
- **Which candidates clear it:** A1, A2 and B, because their canvas is the card colour.

## F-661A-6: console error on the first dashboard load after mock sign-in

- **Severity:** INFO, pre-existing, mock-auth only.
- **The error:** "MockAuthContextProvider: failed to fetch userinfo TypeError: Failed to fetch". It appeared on 2 of the 5 sign-ins in the final run (`scripts/shoot-manifest.json` → `errors`).
- **Context:** this is the known spurious settle-after-login error. It did not affect any shot.

## F-661A-7: Prettier disagrees with `packages/ui/src/button.tsx` formatting

- **Severity:** LOW. A note for 661B, which edits this file.
- **What happens:** running the frontend's Prettier on `packages/ui/src/button.tsx` reports the **unchanged parts** of the file as unformatted. `packages/ui` has no Prettier config of its own, so Prettier falls back to its defaults (80 columns, trailing commas) and reflows the long recipe lines.
- **What 661B should do:** keep its diff to the recipe line, and check which `format:check` (if any) covers `packages/ui` before running `--write`.
- **What the prototype did:** it kept the one-line recipe change unformatted.

## Data notes (not defects)

- The legal-lifecycle suite logs all its time inside the current 30 days, so Home's StatCards draw no delta on a fresh stack.
- The Unbilled WIP chart renders only for two or more fee earners (`unbilled-wip-widget.tsx`, `earners.length >= 2`). The suite leaves unbilled time for Alice only.
- 661A topped both up through the UI (README § 3). 661B and 662A may want the same top-ups for their own Home observations.
