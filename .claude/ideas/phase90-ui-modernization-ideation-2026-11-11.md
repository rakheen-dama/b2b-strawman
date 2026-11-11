# Phase 90 Ideation — UI Modernization (2026-11-11)

Output: `requirements/claude-code-prompt-phase90.md`. Builds on the GTM design-partner plan (2026-07-11) and the HeyKazi branding notes (2026-03-22, Signal Deck).

## Lighthouse / context
- Legal-only Kazi, SA small and medium firms, going to design partners. A dated or inconsistent UI reads as a "risky small vendor" before the trust engine is seen, so UI polish counts as GTM work, not vanity.
- The founder bought the shadcn admincn template (shadcnstudio, 6 layouts; gitignored at repo root) as inspiration and confirmed its licence allows code reuse.

## Decision rationale
- **Scope: foundation + hero surfaces** over foundation-only (too little visible change) and a whole-app sweep (58 pages, collision-prone, 12+ epics).
- **Look: evolve Signal Deck** (slate/teal, Sora/IBM Plex/JetBrains Mono) and borrow only the template's *structure*. Rejected: the template's neutral look with Geist (loses continuity with the landing page) and a fresh brand pass.
- **Detail pages: hero header + KPI strip + tabs.** The founder rejected the template's left summary rail, which means less layout churn and less e2e risk.
- **Backend:** small additive read-only aggregate endpoints are OK where a chart needs a series. No migrations.
- **Dark mode on, light default, opt-in.** Not "follow system".
- **Extras chosen:** dark mode and report/trust charts. **Rejected:** portal restyle, theme customizer.

## Key facts found (saves re-scouting)
- Kazi is shadcn new-york/Radix. The template is base-vega/Base UI (`render=` vs `asChild`), so components must be translated, not copied.
- `/api/dashboard/kpis` returns a trend and previous-period comparison, but **no frontend calls it**. That gives a free KPI row with deltas and sparklines.
- recharts 3.7 is installed but used once. Five chart components are dead. There's no TanStack, no DataTable, no PageHeader and no shadcn sidebar or chart component.
- Dark tokens and ≈3.8k `dark:` classes exist, but no ThemeProvider was ever wired.
- No committed screenshot baselines exist. Tests select by role/text, so a reskin is low-risk if labels stay.

## Sequencing
- Start only after G89 Batch E (#1752) and Batch F merge, because of file collisions.
- Phase 84 (take-on) is independent; the founder decides the order.
- **Follow-ups parked:**
  - portal adoption of the primitives
  - long-tail page migration (adopt when touched)
  - the `components/projects`→`matters` and `customers`→`clients` folder rename (a mechanical PR)
  - a theme customizer (if ever)

## Design preferences (for future UI phases)
- Brand continuity over trendiness.
- Change the visuals, never labels or IA (Phase 87's IA stands).
- Behaviour parity on any migrated list.
- Observe every UI change in both themes at 1024/1440.
