# 639D: own OBS of the Home "Debtors" widget aging meter on the kit StackedMeter

Captured on the :3001 mock stack. The frontend image was built from `faa96b0a8` (code head of PR #1766), and the data came from the
E2E-LEGAL legal-lifecycle and legal-depth-ii runs. Signed in as Alice (owner) at `/org/e2e-test-org/dashboard`. Each row's
`MicroStackedBar` is now `StackedMeter palette="ordinal-aging"` (`components/charts/stacked-meter.tsx`). Its colours come
from `CHART_THEME.aging` → `var(--aging-*)`.

**How.** `obs.cjs` is a Playwright-library script (`chromium.launch()`), not the Playwright MCP. The MCP Chrome profile is held
by the user's live interactive session, the same deviation as 637A–639B. Theme is set through `localStorage['kazi-theme']`, as in 639B.
`obs-report.json` holds the raw per-run results, including the resolved CSS custom properties and the computed fills.

| Check | 1024 light | 1440 light | 1024 dark | 1440 dark |
|---|---|---|---|---|
| Widget renders: headline "4 / clients owe fees", 4 debtor rows | ✓ | ✓ | ✓ | ✓ |
| `svg[role="img"]` meters in widget = 4 (one per row) | ✓ | ✓ | ✓ | ✓ |
| Each `aria-label` = "Aging of {client}'s outstanding fees" | ✓ | ✓ | ✓ | ✓ |
| `aria-describedby` resolves to a `.sr-only` sibling stating all 4 buckets + amounts (e.g. "Current R 8 625,00, 30 days R 0,00, 60 days R 0,00, 90+ days R 0,00") | ✓ | ✓ | ✓ | ✓ |
| Drawn segment fill computes to `--aging-current` (only bucket > 0 in seeded data) | ✓ | ✓ | ✓ | ✓ |
| Track fill computes to `--muted`, zero buckets draw nothing, no gap lines with 1 part | ✓ | ✓ | ✓ | ✓ |
| Aging ≠ status: `--aging-*` values differ from `--success/--warning/--danger` | ✓ | ✓ | ✓ | ✓ |
| Legend: 4 items with dots, dot colours = `--aging-current/d30/d60/d90plus` | ✓ | ✓ | ✓ | ✓ |
| Meter box (w×h px) | 286×6 | 494×6 | 286×6 | 494×6 |
| Widget horizontal overflow | none | none | none | none |
| Hydration warnings | 0 | 0 | 0 | 0 |
| Console errors | 1* | 0 | 1* | 0 |

\* `MockAuthContextProvider: failed to fetch userinfo` fires on the first page load of a fresh browser context. It comes from
mock auth, not the meter.

Resolved tokens, as `lab()` (from `getComputedStyle(document.documentElement)`):

| Token | light | dark |
|---|---|---|
| `--aging-current` | 71.5 29.1 39.0 | 38.7 34.2 35.5 |
| `--aging-d30` | 60.7 39.7 49.1 | 51.3 41.5 48.2 |
| `--aging-d60` | 49.9 48.6 55.9 | 64.3 36.2 45.3 |
| `--aging-d90plus` | 39.6 44.4 44.6 | 77.5 22.9 32.5 |
| `--success` | 55.5 −45.5 30.2 | 67.1 −45.2 29.7 |
| `--warning` | 70.8 20.2 68.5 | 76.9 14.2 62.0 |
| `--danger` | 49.2 66.1 46.2 | 61.1 56.6 33.2 |

The aging ramp is one hue, ordered by lightness. It runs light → dark in light mode and is inverted in dark mode (90+ is the most salient in both).
It shares no value with the status tokens.

**What the screenshots show.** `debtors-<w>-<theme>.png`: the Debtors card with headline 4 and four rows (Acme Corp R 8 625,00, Sipho
Ndlovu R 5 482,63, Apex Holdings R 4 025,00, Moroka Family Trust R 125,00), each "Not yet overdue". Each row has a full-width 6 px
meter with rounded ends, in light peach (light) or dark rust (dark). Below them is the four-dot legend (Current / 30 days / 60 days / 90+ days), which
shows the full ramp. At 1024 the long client names truncate with an ellipsis before the amount, and nothing overflows. `home-<w>-<theme>.png`: the
viewport with Debtors next to Trust position, and Today's diary / Unbilled WIP above. At 1024 the scroll-into-view puts the floating
header (638B) over the "Home" h1, which is expected sticky-header behaviour.

**Notes.**
- **Seeded data limits coverage.** Every seeded debtor is 100 % "Current" (not yet overdue), so each meter draws one segment.
  Multi-segment rendering live (d30/d60/d90+ fills on rects, the 2 px surface gap lines) was **not observable** on this
  dataset. That rendering is covered by the preset unit tests, and the legend dots show the d30/d60/d90+ tokens resolving live. No data was inserted
  (SQL shortcuts are forbidden in QA).
- **Transient duplicate in the DOM.** The widget briefly exists twice at first sight. The second copy is React's streaming-SSR staging
  segment (`<div hidden id="S:0">` under `<body>`), which is invisible and removed on reveal. This happens in both themes and is not a defect. The script scopes the
  locator to `#main-content` and records the counts (`widgetCountAtFirstSight` / `widgetCountAfterSettle`).
- **Token observation (not a 639D change).** The `--aging-current` swatch is about 2.2:1 against the card in light and about 2.65:1 in dark
  (computed from L\*). Both are under the 3:1 non-text guide. Every bucket amount is also given in text (sr summary + row total), so the meter is
  not the only carrier. The tokens date from 637A (#1759). This is noted for the palette owner.
- Matter Overview ring (`ProgressRadial`): not observable — `OverviewMetricsStrip` has no production importer (since PR #817); flagged for 641B.
- SparklineTrend OBS carried by 640A (OBS-CARRY).

Files: `debtors-<w>-<theme>.png` (widget element), `home-<w>-<theme>.png` (viewport), `obs.cjs`, `obs-report.json`,
`gates-summary.txt`, `e2e/*-summary.txt`.
