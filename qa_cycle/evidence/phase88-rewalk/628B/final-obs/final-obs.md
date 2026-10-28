# 628B FINAL re-observation — e2e mock-auth stack :3001 built fresh from 5cd76cc68, 2026-10-29 ~00:35–00:42 SAST (22:35–22:42 UTC)

Browser: `mcp__playwright`, Alice (owner), org e2e-test-org. Measurements via page.evaluate (getBoundingClientRect, scrollWidth/innerWidth, gridTemplateColumns).

| # | Check | Route | Viewport | Value | Verdict | Screenshot |
|---|---|---|---|---|---|---|
| F1a | G-D20 diary | /diary?view=week (court date Fri 30 Oct 09:30) | 768×900 | grid 480 px, 1 column (gridTemplateColumns 1), cell 480, text col 395, title 2 lines (natural 2), time above, tooltip = full title, sw 768 | PASS | 01 |
| F1b | G-D20 diary | same | 1024×900 | grid 704 px (< 896), 1 column, cell 704, text col 619, title 1 line (full), time above, tooltip full, sw 1024 | PASS (was FAIL "6 / 2" at bd9d01fcd) | 02 |
| F1c | G-D20 diary | same | 1280×900 | grid 960 px, 7 columns (lefts 280/418/557/695/833/971/1110), cell 130, text col 45, visible lines "628B " / "Gaut…" (2-line clamp), time 09:30 above + not clipped, tooltip full, sw 1280 | PASS (tight: ~5 chars/line) | 03, 03b |
| F1d | G-D20 diary | same | 1440×900 | grid 1120, 7 columns, cell 153, text col 68, "628B / Gauteng…" 2 lines (h 40), time above, tooltip full, sw 1440 | PASS | 04, 04b |
| F1e | boundary (info) | same | 1216×900 | grid 896 (=@4xl), 7 columns, cell 121, text col 36, "628B / Gaut…", sw 1216 | INFO (worst 7-col case, still ≤2 lines, not "6/2") | — |
| F2a | crumbs | /matters/55b077f8… ("Khumalo Property Holdings — Erf 1234 Sandton Transfer mvsoxhi2a97e") | 1440 | org › Matters › name; last crumb 448 px ellipsis, title=full; nav 264–947 vs switcher 1217; header 56; sw 1440 | PASS | 05 |
| F2b | crumbs | same | 1024 | full trail; last crumb 324 px ellipsis, title=full; nav right 785 < switcher 801; header 56; sw 1024. Nit: org crumb wraps to 2 lines (header still 56) | PASS (nit) | 06 |
| F2c | crumbs | same | 768 | full trail; last crumb 90 px ellipsis; nav right 529 < switcher 545; header 56, header sw 528 = cw. Page sw 857 from matter action row (Generate Document / Statement / More, right 857) — not header, file not in PR | PASS (header); page overflow pre-existing/out of scope | 07 |
| F2d | crumbs | same | 640 | full trail (sm); last crumb 166 px ellipsis; nav 68–409 vs Toggle 16–52, switcher 425; header 56; sw 640 | PASS | 08 |
| F2e | crumbs | same | 390 | only last crumb visible (68–159, ellipsis, title=full); 0 visible chevrons, no leading chevron; switcher 175; no overlap; header 56, header sw 390. Page sw 617 from matter action row (same as bd9d01fcd obs) | PASS (header); page overflow pre-existing | 09 |
| F2f | crumbs | /dashboard | 390 / 640 / 768 / 1024 / 1440 | 390: "Home" only, no chevron; ≥640: "E2E Test Organization › Home"; 0 overlaps; header 56; sw = iw at all 5 | PASS | 10, 11, 12 |
| F3a | width | /settings/documents?tab=templates, /legal/disbursements, /matters, /clients, /settings/fees, /settings/connections, /invoices/*, /diary, /dashboard, matter detail | 1440 | sw 1440 on all | PASS | 13–21 |
| F3b | labels | /settings/fees, /settings/connections | 1440 | tabs "Rates", Tax, …; "Integrations", MCP, AI, …; connections h1 "Integrations" | PASS | 17, 18 |
| F3c | h1 | /invoices/billing-runs, /invoices/collections | 1440 | "Fee runs" (crumb Fee notes › Fee runs); "Debtors" (crumb Fee notes › Debtors) | PASS | 19, 20 |
| F3d | fee-note crumb | /invoices/0864a3bb… | 1440 | "E2E Test Organization › Fee notes › INV-0001" | PASS | 21 |
| F3e | sidebar org | aside | 1440 | `truncate text-xs font-medium text-white/80`, colour white/0.8; bar `h-4 w-[3px] rounded-full` 3×16, bg rgb(27,58,75) = `var(--brand-color)` = #1B3A4B set by legal-lifecycle day-00 spec (bd9d01fcd obs saw default teal on an unbranded org) | PASS (low contrast on dark sidebar with this brand colour — info) | 12 |
| F3f | console | 13 routes above + diary/matter/dashboard | — | 0 errors except /settings/fees (bare + ?tab=tariffs): React #418 — the known pre-existing member-rates-table.tsx UTC-vs-SAST date issue (00:00–02:00 SAST window) | PASS (#418 pre-existing, not this PR) | console.log |

## Notes
- Diary marks Wed 28 Oct as today at 00:3x SAST 29 Oct (server UTC date) — pre-existing timezone behaviour, not in this PR's scope.
- Matter detail at 390/768: page overflow + "Close Matter" buttons overlapping the title at 390 come from the matter header/action row (files not in PR #1704 diff).

## Data created (via UI)
- Court date: matter "Khumalo Property Holdings — Erf 1234 Sandton Transfer mvsoxhi2a97e" (55b077f8-aeb5-4ca4-ac3f-9be7c0624c84), Hearing, 2026-10-30 09:30, court "628B Gauteng Division of the High Court, Johannesburg — Motion Court Room 4B", reminder 7 days.
