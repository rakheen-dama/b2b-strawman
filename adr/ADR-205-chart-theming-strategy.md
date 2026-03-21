# ADR-205: Chart Theming Strategy

**Status**: Accepted
**Date**: 2026-03-21
**Phase**: 53 (Dashboard Polish, Navigation Cleanup & Print-Accurate Previews)

## Context

The platform has 17+ dashboard components across three dashboards (Org Dashboard, My Work, Project Detail Overview) plus a profitability page, all rendering charts via Recharts (v3.7.0). Currently, chart styling — colors, fills, tooltips, grid lines, axes, border radii — is applied inline per-component with no shared configuration. Each chart component (`SparklineChart`, `HorizontalBarChart`, `MiniProgressRing`, etc. in `components/dashboard/`) independently chooses its colors, tooltip formatting, and structural props.

Phase 53 introduces new chart patterns that compound the consistency problem:

- **Sparklines** with gradient area fills for the metrics strip
- **Radial gauges** for utilization/percentage indicators
- **Micro stacked bars** for inline billable/non-billable breakdowns
- **Donut charts** replacing basic pie charts (inner radius ~60%, rounded corners, center content)
- **Area charts** with gradient fills from color to transparent, smooth curves, hover dots
- **Heatmap-style** horizontal bars for team utilization

The design system (Signal Deck) defines chart colors as CSS custom properties in `globals.css` (`--chart-1` through `--chart-5`, with separate light and dark mode values in OKLCH color space). However, there is no TypeScript-level theme object that Recharts components can consume. Recharts is SVG-based and its API is fundamentally prop-driven — colors, gradients, tooltips, grid styles, and axis formatting are all passed as React props or child components, not styled via CSS.

The goal is visual consistency across all chart instances: when a designer says "make all tooltips use slate-900 background with 8px border radius" or "switch the primary series color from teal-500 to teal-600," the change should propagate to all 20+ chart instances from a single location.

## Options Considered

### Option 1: Centralized Theme Config Object (Selected)

A single `chart-theme.ts` file in `lib/` exporting a typed theme configuration consumed by all chart components via standard imports. The config provides:

- Color palettes (primary series, secondary series, status colors)
- Tooltip component props (background, text color, border radius, shadow)
- Grid line styles (stroke, dash array, opacity)
- Axis styles (tick font, color, line visibility)
- Gradient definitions (fill directions, opacity stops)
- Structural defaults (bar radius, donut inner/outer radius, animation duration)

Chart components import the theme and apply values to Recharts props:

```tsx
import { chartTheme } from "@/lib/chart-theme";

<Tooltip
  contentStyle={chartTheme.tooltip.style}
  labelStyle={chartTheme.tooltip.labelStyle}
/>
<CartesianGrid {...chartTheme.grid} />
```

- **Pros:**
  - **Single source of truth for all chart styling.** A tooltip background change is one line in `chart-theme.ts`, not 17 files. This aligns with the phase goal: "does this look great in a 16:9 landing page screenshot?" requires pixel-perfect consistency across charts
  - **TypeScript type-safe.** The theme object is typed — IDE autocomplete for `chartTheme.tooltip.style.backgroundColor`, compile-time errors if a property is removed. New chart components get editor guidance for available theme properties
  - **Aligned with Recharts' API surface.** Recharts accepts colors, styles, and structural props as JavaScript values. A JS theme object maps directly to these props without an impedance mismatch. Gradient `<defs>` in SVG, tooltip component styling, animation configs — all native JS
  - **Works with existing component structure.** The `components/dashboard/` directory already has reusable chart components. Adding a theme import is the natural evolution — same pattern as importing `cn()` from `lib/utils`
  - **Color values reference CSS vars.** The theme config can use the OKLCH values that match `--chart-1` through `--chart-5` from `globals.css`, maintaining the design system as the canonical color source. For components that support CSS var syntax in SVG (e.g., `fill="var(--chart-1)"`), the theme can expose the var reference directly

- **Cons:**
  - **Requires updating all 17+ existing chart components** to import and apply the theme. This is a one-time migration cost during Phase 53 but touches many files
  - **Some Recharts customizations still need inline props.** Complex tooltip content (e.g., showing billable vs. non-billable breakdown) requires custom `<Tooltip content={...} />` components. The theme provides defaults, not a complete abstraction — chart-specific formatting still lives in the chart component
  - **Two sources for color values.** Colors are defined in `globals.css` (CSS vars) and referenced in `chart-theme.ts` (JS values). If someone changes `--chart-2` in CSS without updating the theme config, the values drift. Mitigated by documenting the relationship and keeping the theme config's color section clearly annotated

### Option 2: CSS-Only Theming via Custom Properties

Extend the existing CSS custom properties in `globals.css` to cover all chart styling needs. Add vars for tooltip background, grid stroke, axis color, gradient stops, etc. Chart components read these via `var()` in inline styles or Tailwind classes.

```css
:root {
  --chart-tooltip-bg: oklch(20.5% 0.011 260);
  --chart-grid-stroke: oklch(91% 0.008 260);
  --chart-grid-dash: 3 3;
  --chart-axis-color: oklch(55.5% 0.025 260);
}
```

- **Pros:**
  - **Leverages the existing CSS variable system.** `globals.css` already defines `--chart-1` through `--chart-5`. Extending this pattern is consistent with how the Signal Deck design system works
  - **Dark mode works automatically.** CSS vars switch between `:root` and `.dark` — no JS logic needed for mode detection. Charts styled via `var()` inherit the correct palette without runtime checks
  - **Zero JS bundle impact.** No additional JavaScript shipped for theming. The theme lives entirely in CSS, parsed by the browser

- **Cons:**
  - **Recharts is SVG-based and does not natively consume CSS vars for all properties.** SVG `<linearGradient>` definitions require `stopColor` as a prop — `var()` works in some browsers for SVG attributes but has inconsistent support. Tooltip components are React elements, not CSS-styled DOM — their styling is JS objects passed as props. Animation durations, curve types (`monotone`, `natural`), and structural values (inner radius, bar corner radius) have no CSS representation
  - **No type safety.** A typo in `var(--chart-tooltiip-bg)` (double-i) silently fails with no color rendered. No compile-time or IDE support for available chart variables
  - **Gradient definitions cannot be expressed in CSS vars alone.** Recharts area gradients require SVG `<defs><linearGradient>` elements with `<stop>` children. These are React/SVG elements, not CSS properties. The gradient direction, opacity stops, and color transitions must be defined in JSX regardless of where the colors come from
  - **Structural chart properties have no CSS equivalent.** Donut inner radius (60%), bar corner radius (`[4, 4, 0, 0]`), axis tick formatting, tooltip value formatters — these are Recharts props, not CSS properties. A CSS-only approach covers colors but leaves the majority of the theme unaddressed

### Option 3: Recharts Custom Wrapper Components

Build wrapper components (`ThemedAreaChart`, `ThemedDonutChart`, `ThemedBarChart`, etc.) that encapsulate all styling decisions internally. No shared theme config — each wrapper bakes in its own colors, tooltip styles, grid configuration, and gradient definitions.

```tsx
// components/charts/themed-area-chart.tsx
export function ThemedAreaChart({ data, dataKey, ...props }) {
  return (
    <ResponsiveContainer>
      <AreaChart data={data}>
        <defs>{/* gradient defined inline */}</defs>
        <CartesianGrid strokeDasharray="3 3" stroke="oklch(91% 0.008 260)" />
        <Tooltip contentStyle={{ background: "oklch(20.5% 0.011 260)" }} />
        <Area dataKey={dataKey} fill="url(#gradient)" />
      </AreaChart>
    </ResponsiveContainer>
  );
}
```

- **Pros:**
  - **Each chart is fully self-contained.** A developer reading `ThemedDonutChart` sees every styling decision in one file. No need to trace imports to a theme config to understand why a tooltip is dark
  - **Easy to diverge intentionally.** If the profitability page donut needs different styling from the dashboard donut, the wrappers are independent — no theme override mechanism needed
  - **No shared dependency.** Changing one wrapper cannot accidentally break another chart. Isolation by default

- **Cons:**
  - **Duplication of color/style values across wrappers.** If there are 8 wrapper components, the tooltip background color `oklch(20.5% 0.011 260)` appears in 8 files. When the design team says "make tooltips slightly lighter," 8 files must be updated
  - **Inconsistency risk.** Over time, wrappers drift — one developer hardcodes `slate-800` for a tooltip while another uses `slate-900`. Without a shared reference, visual inconsistency creeps in. This is the exact problem Phase 53 is trying to solve
  - **Harder to maintain at scale.** Phase 53 alone introduces 6+ new chart patterns (sparkline, radial gauge, micro bar, donut, gradient area, utilization bar). Future phases will add more. Each new chart type means a new wrapper with duplicated style values
  - **No design-system integration.** The Signal Deck design system's chart colors are defined centrally in `globals.css`. Scattering the same values across wrapper components breaks the "single source of truth" principle the design system was built on

### Option 4: Hybrid — Theme Config Reading CSS Variables at Runtime

A TypeScript theme config that reads chart colors from CSS custom properties at runtime via `getComputedStyle()`, while providing TypeScript-level defaults for structural properties. Colors come from CSS (dark mode toggle works automatically), structure comes from JS (type-safe).

```tsx
// lib/chart-theme.ts
export function getChartColors() {
  const style = getComputedStyle(document.documentElement);
  return {
    primary: style.getPropertyValue("--chart-1").trim(),
    secondary: style.getPropertyValue("--chart-2").trim(),
    // ...
  };
}

export const chartTheme = {
  tooltip: { style: { borderRadius: 8, fontSize: 13 } },
  grid: { strokeDasharray: "3 3" },
  donut: { innerRadius: "60%", outerRadius: "80%" },
  // colors resolved at render time via getChartColors()
};
```

- **Pros:**
  - **Colors are truly single-sourced in CSS.** `globals.css` is the canonical color reference. The theme config never hardcodes OKLCH values — it reads them at runtime. No drift risk between CSS and JS
  - **Dark mode works automatically.** When `.dark` class toggles, `getComputedStyle()` returns the dark palette. No mode-aware logic in the theme config
  - **Structural properties are type-safe.** Border radius, inner radius, dash arrays — all typed in the theme object. IDE autocomplete works for non-color properties

- **Cons:**
  - **`getComputedStyle()` requires `document` — not available during SSR.** Next.js 16 renders server components first. Chart components using this approach must be client components (they already are, since Recharts requires `"use client"`), and the color resolution must be deferred to mount time or wrapped in `useEffect`. This adds complexity
  - **Runtime cost per render.** `getComputedStyle()` on every chart render is wasteful. Needs caching (e.g., memoize on mount, invalidate on theme toggle) — more code for the same result as hardcoding the matching OKLCH values
  - **Two places to look.** When debugging "why is this chart teal instead of slate," the developer checks the theme config, finds `getChartColors()`, then has to look at `globals.css` for the actual value. The indirection adds cognitive load
  - **OKLCH values from `getComputedStyle()` may not match Recharts' expected format.** Recharts accepts hex, rgb, hsl, or named colors reliably. OKLCH is a newer CSS color function — while modern browsers compute it correctly, the string format returned by `getComputedStyle()` may differ from what was authored (browsers may normalize to `color()` or `rgb()`). This creates subtle rendering differences

## Decision

**Option 1 — Centralized theme config object.**

## Rationale

1. **Recharts' API is the hard constraint.** Recharts is an SVG-based charting library whose entire customization surface is React props and child components. Tooltip styling, gradient definitions, grid configuration, axis formatting, animation timing, curve interpolation, radius values — all are JavaScript values passed as props or JSX children. A CSS-only approach (Option 2) covers perhaps 30% of the theming surface (fill colors, stroke colors) while leaving the remaining 70% (gradients, tooltips, structural props, formatters) unstyled or inconsistently styled. A JavaScript theme object maps 1:1 to Recharts' actual API.

2. **The existing CSS chart vars remain the color authority.** This decision does not duplicate the color system. The five chart color vars (`--chart-1` through `--chart-5`) in `globals.css` remain the design system's canonical color definitions, with separate light and dark mode values. The theme config in `chart-theme.ts` uses `var(--chart-1)` references for fill/stroke colors — these resolve at render time so dark mode works automatically without a separate palette. For SVG gradient `<stop>` elements where `var()` support is inconsistent, the theme config uses hardcoded OKLCH values that match the CSS vars (this is a small subset — only gradient stop colors). This hybrid within Option 1 is intentional: `var()` for direct fill/stroke (works in modern browsers for HTML-embedded SVG), hardcoded OKLCH for gradient stops only. The alternative (Option 4's runtime `getComputedStyle()`) avoids any hardcoded values but introduces SSR complications, runtime cost, and OKLCH parsing edge cases that outweigh the benefit for a five-color palette that changes rarely.

3. **Self-contained wrappers (Option 3) are the status quo that Phase 53 aims to fix.** The current state — 17 chart components each choosing their own tooltip style, grid color, and axis formatting — is Option 3 in practice. The visual inconsistencies this produces are the reason Phase 53 Section 4 ("Chart Component Library Upgrade") exists. Formalizing the current approach into named wrappers does not solve the problem; it just names it.

4. **The component structure supports this pattern naturally.** The `components/dashboard/` directory already has reusable chart components (`SparklineChart`, `HorizontalBarChart`, `MiniProgressRing`, `KpiCard`). These components accept data props and render charts. Adding `import { chartTheme } from "@/lib/chart-theme"` follows the same import pattern used for `cn()` from `lib/utils` or `CAPABILITIES` from `lib/capabilities`. No new architectural concept — just a new config file.

5. **Migration cost is bounded and one-time.** Phase 53 will touch all dashboard components anyway (layout restructuring for Org Dashboard, My Work, and Project Detail Overview). Updating each component to apply `chartTheme.tooltip.style` instead of inline `{ background: "#1e293b" }` is an incremental change during a file that's already being modified. The migration is not a separate effort — it's folded into the dashboard redesign work.

## Consequences

- **Positive:**
  - All chart components across the three dashboards and profitability page share a single visual language — consistent tooltips, grids, axes, gradients, and color palettes
  - Design iteration speed increases — "make all tooltips more rounded" is a one-line change in `chart-theme.ts`, verified across all charts by refreshing any dashboard page
  - New chart components introduced in Phase 53 (radial gauge, micro stacked bar, enhanced sparkline, donut) are built theme-aware from day one, establishing the pattern for future phases
  - The theme config is TypeScript-typed — IDE autocomplete guides developers to available theme properties, preventing ad-hoc inline styling
  - The existing `SparklineChart` and `HorizontalBarChart` components become more reusable once they consume the theme, as they no longer carry hardcoded style opinions

- **Negative:**
  - Dual update requirement for gradient stop colors only — if `--chart-2` changes in `globals.css`, the matching hardcoded OKLCH value in `chart-theme.ts` gradient definitions must also change. Direct fill/stroke uses `var()` references so those stay in sync automatically. This dual-update risk is limited to gradient stops and is documented in the theme config file with comments linking each value to its CSS var origin. The risk is low: the five-color palette has not changed since the Signal Deck design system was established
  - The theme config becomes a shared dependency for all dashboard components. A breaking change to the theme config's interface (renaming properties, restructuring the object) requires updating all consumers. Mitigated by keeping the interface stable and using TypeScript to catch breakages at compile time
  - Chart-specific customizations still exist alongside the theme. A complex tooltip that shows billable/non-billable breakdown has formatting logic that doesn't belong in the theme. The theme provides defaults; chart components can override selectively. This split is intentional but requires discipline to decide what belongs in the theme vs. the component

- **Neutral:**
  - The theme config file (`lib/chart-theme.ts`) is a client-side module consumed only by client components (all Recharts components require `"use client"`). It has no SSR implications and no impact on server component rendering
  - Recharts remains the sole chart dependency — no new libraries introduced. The theme config is a plain TypeScript object, not a framework or abstraction layer
  - Dark mode support for charts is handled by the existing `--chart-1` through `--chart-5` CSS vars. Chart components that use `var()` references for fill/stroke colors automatically get the correct palette in both light and dark mode. Gradient stop colors use hardcoded OKLCH values and currently reflect the light mode palette. If dark mode gradient support is needed in the future, the gradient section can be extended with a `darkGradients` subset or can adopt runtime CSS var reading for gradient stops only — the structural theme properties and direct fill/stroke references remain static either way
