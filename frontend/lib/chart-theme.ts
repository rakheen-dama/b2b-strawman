export const CHART_THEME = {
  // Color palette — references CSS custom properties resolved at runtime
  colors: {
    primary: "var(--chart-1)",   // warm orange
    secondary: "var(--chart-2)", // teal
    tertiary: "var(--chart-3)",  // dark blue
    quaternary: "var(--chart-4)", // yellow
    quinary: "var(--chart-5)",   // amber
  },

  // Slate-based supplementary colors for data-heavy charts
  slate: {
    grid: "var(--color-slate-200)",       // light mode grid lines
    gridDark: "var(--color-slate-700)",   // dark mode grid lines
    axis: "var(--color-slate-500)",       // axis text
    muted: "var(--color-slate-400)",      // secondary series
  },

  // Gradient fill factory — returns [startColor, endColor] for area charts
  gradientOpacity: { top: 0.3, bottom: 0.0 },

  // Tooltip styling
  tooltip: {
    background: "var(--color-slate-900)",
    text: "#ffffff",
    border: "none",
    borderRadius: 8,
    boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
  },

  // Grid line styling
  grid: {
    strokeDasharray: "3 3",
    stroke: "var(--color-slate-200)",
  },

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
