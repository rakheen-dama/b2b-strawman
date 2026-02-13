# ADR-046: Dashboard Charting — Hybrid SVG + Recharts

**Status**: Accepted

**Context**: Phase 9 dashboards introduce several data visualization components: sparkline charts (small trend lines in KPI cards), circular progress rings (task completion), and stacked horizontal bar charts (team workload, time breakdown). The platform's frontend is built with Next.js 16, React 19, TypeScript, Tailwind CSS v4, and Shadcn UI. There is currently no charting library in the project.

The charting needs span a wide range of complexity: from a 24-pixel-tall sparkline (a single SVG polyline with no interactivity) to a stacked horizontal bar chart with tooltips, legends, responsive sizing, and accessible keyboard navigation. The question is whether to use a single approach for all visualizations or select the right tool for each complexity level.

Bundle size is a consideration for a B2B SaaS application: the dashboard is a frequently visited page, and every kilobyte of JavaScript adds to initial load time. However, code-splitting (dynamic imports) mitigates this — charting code is only loaded when the dashboard page is visited.

**Options Considered**:

1. **Recharts for everything** -- Use Recharts (a React-native charting library built on D3 primitives) for all visualizations: sparklines, progress rings, bar charts, and any future charts.
   - Pros:
     - Single library for all charting needs — consistent API, one learning curve for contributors
     - Built-in responsiveness (ResponsiveContainer), tooltips, legends, and accessibility
     - Tree-shakeable: import only the chart types you use
     - Active maintenance, large community, extensive documentation
     - Handles edge cases (empty data, single data point, responsive resize) that custom SVG would need to handle manually
   - Cons:
     - **Over-engineered for simple visualizations**: A sparkline is a single SVG `<polyline>` element — roughly 15 lines of code. Using Recharts for a sparkline requires `LineChart`, `Line`, `ResponsiveContainer`, configuration props, and yields a component that is harder to style with Tailwind than raw SVG.
     - **Bundle size overhead for simple cases**: Even with tree-shaking, importing Recharts for a sparkline brings in its core rendering engine. The sparkline SVG is ~500 bytes; the Recharts dependency for it would be ~15KB gzipped.
     - A `MiniProgressRing` (circular arc) is not a standard Recharts chart type — implementing it with Recharts requires either `RadialBarChart` (which is designed for different use cases and has heavyweight defaults) or dropping to custom SVG anyway.
     - Styling integration with Tailwind and Shadcn is indirect — Recharts uses its own styling props, not CSS classes.

2. **Custom SVG for everything** -- Build all visualizations as custom React components using raw SVG elements. No external charting library.
   - Pros:
     - **Zero bundle size overhead** — no external dependencies
     - Full control over rendering, styling, and behavior
     - Can be styled with Tailwind classes directly on SVG elements
     - Perfect Shadcn UI integration — uses the same color tokens, border radii, shadows
     - No version upgrade risk from a third-party library
   - Cons:
     - **Significant implementation effort for complex charts**: A stacked horizontal bar chart with tooltips, legends, responsive sizing, and accessible keyboard navigation is hundreds of lines of SVG math. Recharts provides all of this out of the box.
     - **Tooltip positioning** is surprisingly difficult to implement correctly — accounting for viewport edges, scroll position, and touch targets. Recharts handles this automatically.
     - **Accessibility**: Custom SVG charts need manual ARIA attributes, keyboard focus management, and screen reader descriptions. Recharts provides baseline accessibility.
     - **Maintenance burden**: Every layout edge case (zero-width bars, overlapping labels, responsive breakpoints) must be handled manually. These are solved problems in mature charting libraries.
     - Risk of subtle bugs in chart math (bar segment widths, axis scaling) that are already tested in libraries.

3. **Hybrid: Custom SVG for simple visualizations + Recharts for complex charts** -- Use custom SVG for sparklines and progress rings (trivial geometry, no interactivity). Use Recharts for stacked bar charts and any future complex interactive charts.
   - Pros:
     - **Right tool for each job**: Sparklines and progress rings are simple enough that a library adds overhead without value. Bar charts are complex enough that a library saves significant implementation and maintenance effort.
     - Minimal bundle impact: Recharts is only imported on pages that render bar charts (the dashboard and personal dashboard). Sparklines and progress rings use zero-dependency SVG.
     - Custom SVG components (sparkline, progress ring) are perfectly styled with Tailwind — they look native to the Shadcn design system.
     - Recharts handles the complex parts: tooltip positioning, stacking math, responsive resize, accessible labels.
     - Clear decision boundary: if a visualization is a single SVG primitive (line, arc, circle), it is custom SVG. If it requires layout, interactivity, or multiple data series, it uses Recharts.
   - Cons:
     - Two implementation patterns — contributors need to know when to use custom SVG vs Recharts. However, the boundary is clear and documented.
     - Recharts is still an external dependency (though only one, and only for complex charts).
     - Custom SVG sparklines do not get Recharts' built-in responsiveness — but a sparkline in a KPI card has a fixed width, so responsiveness is handled by the card's CSS grid, not the chart.

4. **Chart.js with react-chartjs-2** -- Use Chart.js (canvas-based rendering) via the react-chartjs-2 wrapper for all visualizations.
   - Pros:
     - Mature library with extensive chart type support
     - Good performance for large datasets (canvas rendering)
     - Plugin ecosystem for annotations, data labels, etc.
   - Cons:
     - **Canvas-based**: Renders to `<canvas>`, not SVG. This means charts cannot be styled with CSS/Tailwind, do not integrate with the DOM for accessibility, and cannot be inspected or tested with standard DOM testing utilities.
     - **React integration is a wrapper**: react-chartjs-2 wraps an imperative API, leading to awkward lifecycle management (destroy/recreate on prop change).
     - Larger bundle than Recharts: Chart.js core is ~60KB gzipped vs Recharts' ~40KB gzipped (tree-shaken).
     - Canvas rendering produces blurry charts on high-DPI displays unless explicitly handled.
     - Not idiomatic React — Chart.js manages its own rendering loop outside of React's virtual DOM.

**Decision**: Hybrid — custom SVG for simple visualizations (sparklines, progress rings) + Recharts for complex charts (stacked bar charts) (Option 3).

**Rationale**:

1. **Proportional complexity**: A sparkline is 15 lines of SVG code. A stacked horizontal bar chart with tooltips and legends is hundreds of lines. Using the same tool for both either over-engineers the simple case (Recharts for a polyline) or under-engineers the complex case (manual SVG for a stacked bar chart). The hybrid approach applies the right level of tooling to each problem.

2. **Bundle impact is minimal**: Recharts is tree-shakeable. Importing only `BarChart`, `Bar`, `XAxis`, `YAxis`, `Tooltip`, `Legend`, and `ResponsiveContainer` yields approximately 35-40KB gzipped. This is loaded only on the dashboard page via Next.js code splitting — it does not affect other page loads. The custom SVG components add zero to the bundle.

3. **Sparklines are too simple for a library**: A `SparklineChart` component is:
   ```tsx
   <svg width={width} height={height}>
     <polyline points={computePoints(data, width, height)} fill="none" stroke={color} />
   </svg>
   ```
   Wrapping this in `<ResponsiveContainer><LineChart><Line /></LineChart></ResponsiveContainer>` adds API surface, configuration props, and runtime overhead without adding any value. The sparkline has no axes, no tooltips, no legends, no interactivity — just a trend shape.

4. **Progress rings are not chart library territory**: A `MiniProgressRing` is an SVG circle with a `stroke-dasharray` offset. No charting library provides this as a first-class component without heavyweight defaults (Recharts' `RadialBarChart` includes axis labels, value labels, and animations that must all be disabled for a 32-pixel progress indicator).

5. **Bar charts justify a library**: A stacked horizontal bar chart needs: segment width calculation, stacking order, tooltip positioning that accounts for viewport edges, responsive resize, hover/focus state management, accessible labels, and a legend. Implementing all of this in custom SVG would take 300+ lines and require testing every edge case. Recharts provides all of it in a declarative React API.

6. **Clear boundary for contributors**: The rule is simple: if the visualization is a single SVG primitive (line, arc, circle, rectangle) with no interactivity, use custom SVG. If it requires layout calculation, multiple data series, or interactive features, use Recharts. This boundary is documented in the component directory and does not require judgement calls.

**Consequences**:
- `SparklineChart` and `MiniProgressRing` are pure SVG components with zero external dependencies. They are styled with Tailwind classes and Shadcn color tokens.
- `HorizontalBarChart` uses Recharts (`BarChart` with `layout="vertical"`, `Bar` components for stacked segments, `Tooltip` for hover details, `Legend` for color key).
- `recharts` is added as a production dependency via `pnpm add recharts`. It is code-split and only loaded on pages that render the `HorizontalBarChart` component.
- Future charting needs (e.g., line charts for financial trends in Phase 10, pie charts for resource allocation) should evaluate the same boundary: simple = custom SVG, complex = Recharts. This prevents both over-engineering and under-engineering.
- No D3 direct dependency: Recharts uses D3 internals but does not expose D3's imperative API. If a future visualization needs low-level D3 access (e.g., geographic maps, force-directed graphs), D3 can be added alongside Recharts without conflict.
