/**
 * Chart theme — token references only (ADR-370; architecture §15.2.5).
 *
 * Every colour is a `var(--…)` defined in `@b2mash/ui/styles/globals.css` for both `:root`
 * and `.dark`, so charts follow the theme with no hex fallbacks and no `dark:` overrides.
 * Slots are assigned in fixed order (never cycled past 5 — fold the rest into "Other"), and a
 * series keeps its slot when a filter removes others. A chart uses categorical **or** status
 * colours, never both. Pinned by `__tests__/theme/chart-palette.test.ts`.
 */
export const CHART_THEME = {
  /** Categorical series slots — one hue each, stepped per theme. */
  categorical: [
    "var(--chart-1)", // slot 1 — teal (Kazi accent family)
    "var(--chart-2)", // slot 2 — orange
    "var(--chart-3)", // slot 3 — blue
    "var(--chart-4)", // slot 4 — amber (sits near the warning hue)
    "var(--chart-5)", // slot 5 — violet
  ],
  /** Magnitude — one hue (teal), low → high. */
  sequential: [
    "var(--seq-100)",
    "var(--seq-200)",
    "var(--seq-300)",
    "var(--seq-400)",
    "var(--seq-500)",
  ],
  /** Ordered aged-debtor buckets — one hue (orange), current → 90+ days. */
  aging: {
    current: "var(--aging-current)",
    d30: "var(--aging-d30)",
    d60: "var(--aging-d60)",
    d90plus: "var(--aging-d90plus)",
  },
  /** Above / below zero (profitability ranking); the midpoint is the border colour. */
  diverging: {
    positive: "var(--div-pos)",
    negative: "var(--div-neg)",
    midpoint: "var(--border)",
  },
  /** State, not series — always paired with an icon and a label. */
  status: {
    success: "var(--success)",
    warning: "var(--warning)",
    danger: "var(--danger)",
    info: "var(--info)",
  },

  /** Recessive grid lines. */
  grid: { stroke: "var(--border)", strokeDasharray: "3 3" },
  /** Axis ticks, labels and reference lines — a text token, never a series colour. */
  axis: "var(--muted-foreground)",
  /** Tooltip surface — the popover tokens (one step above cards in dark). */
  tooltip: {
    background: "var(--popover)",
    text: "var(--popover-foreground)",
    border: "1px solid var(--border)",
    borderRadius: 8,
    boxShadow: "var(--shadow-overlay)",
  },

  // Area-chart gradient fill opacity (top → bottom)
  gradientOpacity: { top: 0.3, bottom: 0.0 },

  // Bar chart defaults
  bar: {
    radius: [4, 4, 0, 0] as [number, number, number, number],
    hoverBrightness: 1.1,
  },

  // Donut chart defaults
  donut: {
    innerRadius: "60%",
    outerRadius: "80%",
    cornerRadius: 4,
    paddingAngle: 2,
  },

  // Area chart defaults
  area: {
    type: "monotone" as const,
    dot: false,
    activeDot: { r: 4, strokeWidth: 2 },
  },

  // Font
  fontFamily: "var(--font-mono)",
} as const;
