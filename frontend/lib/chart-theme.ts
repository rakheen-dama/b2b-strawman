export const CHART_THEME = {
  // Color palette — references CSS custom properties with hex fallbacks
  colors: {
    primary: "var(--color-chart-1, #2563eb)", // warm orange
    secondary: "var(--color-chart-2, #e11d48)", // teal
    tertiary: "var(--color-chart-3, #e77e23)", // dark blue
    quaternary: "var(--color-chart-4, #8b5cf6)", // yellow
    quinary: "var(--color-chart-5, #06b6d4)", // amber
  },

  // Slate-based supplementary colors for data-heavy charts
  slate: {
    grid: "var(--color-slate-200)", // light mode grid lines
    gridDark: "var(--color-slate-700)", // dark mode grid lines
    axis: "var(--color-slate-500)", // axis text
    muted: "var(--color-slate-400)", // secondary series
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
