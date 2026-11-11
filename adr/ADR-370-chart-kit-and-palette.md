# ADR-370: Chart kit on shadcn `chart` + recharts 3, and the Kazi chart palette (categorical/sequential/status) with accessible alternatives

**Status**: Accepted (amends [ADR-046](ADR-046-dashboard-charting-approach.md) and [ADR-205](ADR-205-chart-theming-strategy.md))

**Context**:

[ADR-046](ADR-046-dashboard-charting-approach.md) chose a hybrid: custom SVG for small visuals, Recharts for complex charts. [ADR-205](ADR-205-chart-theming-strategy.md) centralised chart styling in `frontend/lib/chart-theme.ts`. At HEAD both have decayed:

- **Only three charts are live.**
  - The recharts `DonutChart` (`components/dashboard/donut-chart.tsx`, used only by `components/my-work/time-breakdown.tsx`).
  - The hand-rolled SVG `MiniProgressRing` (used by `components/projects/overview-metrics-strip.tsx`).
  - The hand-rolled SVG `MicroStackedBar` (used by `components/dashboard/debtors-widget.tsx`).
- **Five chart files are dead**, with no consumers: `horizontal-bar-chart`, `radial-gauge`, `kpi-card`, `sparkline-chart` and `sparkline`.
- **`lib/chart-theme.ts` doesn't match its own tokens.**
  - Its hex fallbacks (#2563eb, #e11d48, #e77e23, #8b5cf6, #06b6d4) match neither its comments ("warm orange", "teal", …) nor the CSS values.
  - The tooltip hard-codes `var(--color-slate-900)` and `#ffffff`, and the grid hard-codes `slate-200`, so neither works in dark mode.
- **`--chart-1..5`** in `packages/ui/styles/globals.css` are stock shadcn values whose **hue changes per index between `:root` and `.dark`** (orange → blue, teal → green, …), so a series changes colour when the theme flips.
- **There are no status tokens** (success / warning / danger / info).

Phase 90 adds the following charts:

- the KPI hours sparkline;
- unbilled WIP by fee earner;
- aged-debtor buckets;
- the trust balance trend and fees billed vs collected ([ADR-372](ADR-372-read-only-chart-series-endpoints.md));
- utilization by fee earner;
- the matter profitability ranking.

It also rebuilds the three live charts and turns dark mode on ([ADR-371](ADR-371-dark-mode-light-default.md)). The frontend already depends on `recharts` ^3.7.0. The template uses shadcn's `components/ui/chart.tsx` on recharts 3.8.

**Options Considered**:

1. **Keep the hybrid and fix `chart-theme.ts`; add new charts as one-off recharts components** — the smallest change.
   - Pros: No new component layer.
   - Cons: It perpetuates per-chart tooltip, legend and config code, which is exactly what left five dead charts. There is no common accessibility contract, and the token drift between `chart-theme.ts` and the CSS remains possible.
2. **shadcn `chart` (`ChartContainer`, `ChartConfig`, `ChartTooltip`, `ChartLegend`) on recharts 3, plus Kazi presets, a token-only theme and dependency-free SVG micro presets (CHOSEN).**
   - Pros: `ChartContainer` maps series to CSS variables, so the palette lives in tokens and dark mode is automatic. Tooltip and legend are shared. It matches the template's chart blocks ([ADR-368](ADR-368-admincn-template-adoption.md)) and needs no new dependency.
   - Cons: Recharts is a sizeable client bundle that must be loaded only where needed. The CLI-generated `chart.tsx` needs slate/teal review.
3. **Switch libraries (visx, nivo, ECharts, Tremor)** — a different rendering stack.
   - Pros: Some offer canvas rendering or richer accessibility out of the box.
   - Cons: It needs a new dependency and its own ADR, has no shadcn token integration, and the three live charts would still need porting. Nothing Phase 90 charts needs is missing from recharts 3.
4. **Hand-rolled SVG for everything** — extend `MiniProgressRing` / `MicroStackedBar` to all forms.
   - Pros: Zero chart dependency.
   - Cons: Axes, scales, tooltips, hover hit-testing and keyboard access would all be reimplemented for area, bar and grouped forms. That is high cost and high risk for no gain.

**Decision**: Option 2 — one chart kit in `frontend/components`, made of recharts presets on shadcn `chart` plus dependency-free SVG micro presets, that reads only the tokens of §15.2.5 of the architecture doc under one accessibility contract, with `lib/chart-theme.ts` reduced to a truthful map onto those tokens.

**Rationale**:

1. **Kit shape.**
   - `frontend/components/ui/chart.tsx` comes from the shadcn CLI and is reviewed for slate/teal.
   - The presets live in `frontend/components/charts/`, named in §15.2.4 of the [architecture doc](../architecture/phase90-ui-modernization.md):
     - **recharts presets** (`"use client"`, on `ChartContainer`): `TrendAreaChart` (one series, or two series in the same unit), `BarChartPreset` (single, grouped or stacked; vertical or horizontal, including the ranking), `DonutChart`;
     - **micro presets** (dependency-free inline SVG, no recharts, server-safe): `SparklineTrend`, `ProgressRadial`, `StackedMeter`.
   - Recharts presets take formatters: ZAR through `formatCurrency` from `@/lib/format`, which keeps the intl polyfill on the path, plus hours and percent. No shared hours or percent formatter exists at HEAD, so the kit adds one and retires the local `formatHours` copies when they are touched. Micro presets take caller-formatted text instead, as `StatCard` does, so they import neither recharts nor the formatter.
   - Each recharts preset has empty and loading states and uses recessive axis and grid defaults.
   - The micro presets keep the half of [ADR-046](ADR-046-dashboard-charting-approach.md) that worked. They read the same tokens and follow the accessibility contract below. As a result the Home dashboard's KPI row and the matter Overview strip load no recharts and need no `next/dynamic`.
   - Recharts presets are client components. They are imported with `next/dynamic` and a skeleton wherever the chart is conditional or below the fold.
2. **Palette as tokens, by job** (`dataviz` method: form first, colour last, validated by script):
   - **Categorical `--chart-1..5`.** Each index keeps the **same hue in both modes**, and only lightness and chroma are stepped per mode, so a series is recognisable across the theme flip. Slot 1 is anchored on the Kazi teal family. The order of the remaining hues is the one that maximises the minimum adjacent colour-vision-deficiency (CVD) ΔE. Hues are assigned in fixed order, never cycled. Each slot must meet:
     - lightness L 0.43–0.77 (light) and 0.48–0.67 (dark), and chroma C ≥ 0.10;
     - adjacent CVD ΔE ≥ 8 (6–8 only with secondary encoding), and adjacent normal-vision ΔE ≥ 15 (hard);
     - ≥ 3:1 against the `--card` surface in each mode, or else visible labels or the table view.

     A chart with more than 5 series folds the rest into "Other". Scatter-like forms use at most the first three slots (the all-pairs check).
   - **Sequential `--seq-100…500`** is a single-hue (teal) ramp for magnitude, light to dark, with its anchor flipped in dark mode. Its light end does not clear 2:1, so it is not used for ordinal buckets.
   - **Ordinal `--aging-current`, `--aging-d30`, `--aging-d60`, `--aging-d90plus`** is a separate single-hue (orange) ramp. Aged debtors (current / 1–30 / 31–60 / 61+) are **ordinal**, not status, so they use the `--aging-*` ramp. It is validated with `--ordinal` (monotone L, adjacent ΔL ≥ 0.06, light end ≥ 2:1).
   - **Diverging `--div-pos` / `--div-neg`** (blue ↔ red about a `--border` midpoint) serves the profitability ranking above and below zero margin.
   - **Status `--success`, `--warning`, `--danger`, `--info`** form a fixed scale on steps distinct from the categorical slots. Each has a `--{status}-soft` badge fill and a `--{status}-foreground` badge text. They are always paired with an icon and a label, and never used as "series 4". Badges and FICA and reconciliation states use them too.
   - The token slice runs the dataviz skill's `validate_palette.js` per mode against that mode's `--card` value and pastes the output into the PR. Token names and the validated OKLCH values are specified in §15.2.5 of the [architecture doc](../architecture/phase90-ui-modernization.md), and the slice must pass them.
3. **Chart rules for this product.**
   - **Never a dual axis.** Billed vs collected share one ZAR axis (grouped bars or two lines).
   - **Money charts state their currency** (rulings §6), and the trust trend labels the current month "as of today".
   - **A single-series chart has no legend box**; its title names the series. Two or more series always get a legend, and up to four are also direct-labelled.
   - **Text uses text tokens, never the series colour.**
   - **Utilization with fewer than 2 rows** (a non-admin sees only their own row) renders the table and a note instead of a chart (rulings §6).
4. **Accessibility contract.**
   - Every **recharts preset** has:
     - an accessible name (the visible title), using recharts 3's `accessibilityLayer` keyboard support (on by default in recharts 3; verify at build);
     - a visually hidden one-sentence summary of the headline figure;
     - a "View as table" toggle that renders the same rows as a plain semantic `<table>` (not DataTable, so the chart kit does not depend on [ADR-369](ADR-369-datatable-tanstack-client-mode.md)'s client component; §15.2.4 governs);
     - hover and focus tooltips through `ChartTooltip`.
   - Every **micro preset** has an accessible name and a visually hidden summary that states every value it draws. It has **no table toggle**: it draws only a handful of values, its summary already states them in full, and it sits beside the figure it decorates (a tile value or a debtor row).
   - In both kinds, colour is never the only signal.
5. **`lib/chart-theme.ts`** exports `var(--chart-N)`, `var(--seq-N00)`, `var(--aging-*)`, `var(--div-*)` and status references, with no hex fallbacks. The tooltip and grid use `--popover`, `--popover-foreground` and `--border`. Its comments describe roles, not colour names. A vitest pins every exported value to a `var(--…)` string.

**Consequences**:

- Positive:
  - One kit with tokens that are truthful in both themes. The dead chart files and their tests (`horizontal-bar-chart.test.tsx`, `kpi-card.test.tsx`, `sparkline-chart.test.tsx`) are deleted after a repo-wide grep. `chart-components.test.tsx` is edited, not deleted, because it also covers the live donut and stacked bar.
  - The palette is validated against WCAG contrast and colour-vision-deficiency (CVD) checks by script, not by eye, and is reusable by the portal later (tokens live in `@b2mash/ui`).
  - The Home dashboard's KPI row and the matter Overview strip stay recharts-free through the SVG micro presets.
  - Aged debtors read as ordered buckets on the one-hue `--aging-*` ramp, never as good/bad status colours.
- Negative:
  - Retuning `--chart-*` changes the portal's token file, as does adding sequential (`--seq-*`), ordinal (`--aging-*`), diverging (`--div-*`) and status (`--{status}`, `--{status}-soft`, `--{status}-foreground`) tokens in `packages/ui/styles/globals.css`. The token slice runs PORTAL-GATE, although the portal renders no charts.
  - Every chart carries a table toggle and a summary sentence, which adds a little per-chart work and some vitest cases (empty, loading, single-row, redacted).
  - Two rendering paths (recharts presets and SVG micro presets) remain. They share tokens and the accessibility contract, but not their code.

**Related**: [ADR-046](ADR-046-dashboard-charting-approach.md) (hybrid kept; amended), [ADR-205](ADR-205-chart-theming-strategy.md) (theme object kept; now token-only), [ADR-367](ADR-367-design-system-layering.md) (kit is app-coupled; tokens shared), [ADR-368](ADR-368-admincn-template-adoption.md), [ADR-369](ADR-369-datatable-tanstack-client-mode.md) (table alternative), [ADR-371](ADR-371-dark-mode-light-default.md) (per-mode steps, no `dark:` overrides in charts), [ADR-372](ADR-372-read-only-chart-series-endpoints.md) (series for the trust and billing trends); [architecture §15](../architecture/phase90-ui-modernization.md).
