# 639B — own OBS: My Work "Time Breakdown" donut on the kit DonutChart

Captured on the fresh :3001 mock stack, frontend image built from `008e77020` (final code head of PR #1764), after
E2E-LEGAL seeded time entries. Signed in as Alice (owner) at `/org/e2e-test-org/my-work`. The recharts presets
(TrendAreaChart, BarChartPreset) have no live consumer in this slice: **recharts preset OBS carried by 640B**.

**How.** `obs.cjs` is a Playwright-library script (`chromium.launch()`), not the Playwright MCP. The MCP Chrome profile is held
by the user's live interactive session, the same deviation as 637A–639A. `obs-report.json` holds the raw per-run results.

| Check | 1024 light | 1440 light | 1024 dark | 1440 dark | 1440 light, reduced motion |
|---|---|---|---|---|---|
| `role="figure"`, name "Time Breakdown" | ✓ | ✓ | ✓ | ✓ | ✓ |
| Sector fills `var(--chart-1..4)` (slots pinned by rank) | ✓ | ✓ | ✓ | ✓ | ✓ |
| Centre "10.5h / total" | ✓ | ✓ | ✓ | ✓ | ✓ |
| Hover on the largest sector → tooltip "Estate Late Peter Moroka … 7,0h" | ✓ | ✓ | ✓ | ✓ | ✓ |
| Keyboard: Tab to the `accessibilityLayer` surface, ArrowRight ×2 → tooltip on the 3rd sector ("Ndlovu v RAF … 1,0h") | ✓ | ✓ | ✓ | ✓ | ✓ |
| Tables in panel by default: 0 (the page's own 1 table is unchanged) | ✓ | ✓ | ✓ | ✓ | ✓ |
| "View as table" mounts exactly 1 table (header + 4 rows); "Hide table" unmounts it | ✓ | ✓ | ✓ | ✓ | ✓ |
| Hydration warnings | 0 | 0 | 0 | 0 | 0 |
| Console errors | 0 | 0 | 1* | 0 | 0 |

\* `MockAuthContextProvider: failed to fetch userinfo` fires on the first page load of a fresh browser context. It comes from
mock auth, not the chart, and shows up in whichever run is first after sign-in.

**Defect found and fixed during OBS.** The first capture, at `7eb951b36`, showed the kit tooltip's label and value running
together ("…mwd7963k4f56**7,0h**"). It was fixed in `5e557a862` (`gap-4` on the tooltip row in `components/ui/chart.tsx`) and
re-observed here.

**Notes for 640C (My Work), not defects of this slice:**
- At 1024 the side card is narrow, so matter names wrap onto two lines.
- The itemised list keeps the legacy `7.0h (67%)` format, pinned by `my-work-dashboard.test.tsx` and outside this slice. The
  kit tooltip, table and sr summary use en-ZA `7,0h` / `66,7%`, so one card shows two decimal separators.

Files: `my-work-donut-<w>-<theme>[-hover|-keyboard|-table].png` (panel), `my-work-page-<w>-<theme>.png` (full viewport), the
`-reduced-motion` variants, `e2e/*-summary.txt`, `gates-summary.txt`.
