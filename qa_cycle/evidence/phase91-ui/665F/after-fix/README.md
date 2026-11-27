# 665F OBS after-fix — PR #1875 @ `cec81dad5`

The fix commit range `f3c298e43..cec81dad5` was re-observed in a real browser. It made two visual changes:
1. `connections/_tabs/xero.tsx`: the "Xero Integration" h2 went from `font-display text-2xl` to `text-foreground text-lg leading-none font-semibold`.
2. `connections/_tabs/mcp-connector-card.tsx`: the card title now uses `IntegrationSectionTitle icon={Bot}`.

The observer only rendered pages and sent GET requests. No product code was changed, nothing was written to the database, and nothing was reseeded or rebuilt.

## Provenance

| | |
|---|---|
| `git rev-parse HEAD` (worktree) | `cec81dad5c86e69567d193f6878d031e261c6558` |
| `docker inspect e2e-frontend` | created `2026-11-26T23:51:00.915630841Z`, image `docteams-e2e-frontend` `sha256:b25eb4cb947cc1cea47b71eeee8654a00e5c5f3f93ef63928bb9a92df6868e89` |
| Stack | e2e mock-auth: frontend :3001, backend :8081. The legal e2e suites had just seeded it. |
| Browser | Playwright headless Chromium on node 20, DPR 1, reduced motion, TZ Africa/Johannesburg, `sidebar_state=true`, theme from localStorage `kazi-theme` |
| Persona | Alice (owner), signed in through `/mock-login` → Sign In |

## Files

- `scripts/lib.cjs` is a verbatim copy of `../after/scripts/lib.cjs`. The original was not edited.
- `scripts/01-verify.cjs` is the capture and measurement script, committed exactly as executed. Usage: `PHASE=after-fix node 01-verify.cjs`.
- `verify.json` holds every measurement and verdict on a single line.
- `sheet-xero-alice.png` and `sheet-mcp-alice.png` are the contact sheets: 1024×768 light/dark and 1440×900 light/dark, full page. Following the lib convention, the raw per-viewport PNGs stay in `/tmp/665F-after-fix-raw/` and are not committed.

## Results

| Check | Verdict | Numbers (identical in all 4 viewport × theme variants unless noted) |
|---|---|---|
| Integrations: h1 + "Xero Integration" h2 | **PASS** | There is 1 h1 in `#main-content`: "Integrations", 24px/600, Sora. The h2 `#xero-heading` computes to 18px / 600 / line-height 18px, `ui-sans-serif` stack, colour `#14171c` in light and `#ffffff` in dark. The h1 (24px) is larger than the h2 (18px). `[id="xero-heading"]` count = 1. `section[aria-labelledby="xero-heading"]` count = 1 and resolves to the h2. `getByRole('heading', {name:'Xero Integration', exact})` = 1. |
| Xero h2 vs a real SettingsSection h2 (extra check, not in the brief) | **FAIL (LOW)** | Font size (18px), weight (600), colour and family are all equal. **Line-height differs: Xero is 18px, SettingsSection ("Accounting" h2) is 28px.** |
| MCP: h1 + card title | **PASS** | There is 1 h1: "MCP", 24px/600. Exactly 1 heading has the accessible name "Claude / MCP Connector"; it is an h2, aria snapshot `- heading "Claude / MCP Connector" [level=2]`. Icon tile: `aria-hidden="true"`, 40×40, radius 8px, 20×20 svg, 12px gap to the label. |
| MCP tile vs sibling tiles on `?tab=xero` | **PASS** | All 8 tiles on Integrations have the same box: 40×40, gap 12, radius 8px, aria-hidden. The 8 are Accounting, AI Assistant, Document Signing, Email Delivery, Payment Gateway, KYC Verification, Claude / MCP Connector and Xero Connection. Xero Connection's tile shows the letter "X" instead of an svg; its box is the same. The MCP tab's tile has the same box. |
| 1024 overflow | **PASS** | On xero and mcp, in light and dark, `scrollWidth`/`clientWidth` = 1024/1024. Elements past the tab panel's right edge = 0. The panel's right edge is at 992 in light and 1000 in dark. |
| Console | **PASS** | 8 pages: 0 messages, 0 errors, 0 hydration warnings. |

## Defect (LOW): Xero h2 line-height 18px vs SettingsSection h2 28px

`SettingsSection` builds its heading class with `cn("leading-none font-semibold", "text-lg")`. tailwind-merge drops `leading-none` there because it is followed by `text-lg`, which sets a line-height: `twMerge("leading-none font-semibold","text-lg")` → `"font-semibold text-lg"`. The rendered SettingsSection h2 therefore has class `font-semibold text-lg` and computes line-height 28px.

The Xero h2 uses a plain `className` string with no `cn`, so `leading-none` survives and it computes line-height 18px. The font, size, weight and colour match. The Xero h2's line box is 10px shorter than every other section heading on the page. This only affects vertical rhythm; there is no clipping or overlap.
