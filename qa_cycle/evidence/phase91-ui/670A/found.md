# 670A — found (exit gate, Phase 91)

None of these is fixed in 670A (docs/evidence-only exit gate). Each is routed to a follow-up in `tasks/phase91-ui-modernization-ii.md` § Closure report C.6.

| Id | Severity | Finding | Source | Routed |
|---|---|---|---|---|
| F-670A-1 | LOW | Portal **dev server**: Next's first compile of `/auth/exchange` remounts the page and replays the single-use magic-link token (401), so "Login Failed" shows although the session exists; `/home` then loads. Not reproduced on a production build; `portal/` unchanged in Phase 91 | `$EV/suites/README.md` | closure C.6(d) |
| F-670A-2 | LOW | 390 mobile nav Sheet: Escape must be pressed twice (first press closes a hidden tooltip) and focus returns to `<body>`, not the "Toggle menu" trigger. `sidebar.tsx` unchanged in Phase 91 | `$EV/exit/README.md` § Findings | closure C.6(d) |
| F-670A-3 | LOW | `/legal/adverse-parties` row-menu Delete item hard-coded `text-red-600`, 3.50:1 in dark (pre-existing; 669A moved the line unchanged) | `$EV/exit/darkmode.md` DD-8 | FU-10 |
| F-670A-4 | MEDIUM (dark) | Two hand-rolled combobox triggers in settings cards, border 1.15:1 / fill 1.11:1 in dark; the Rates one moved 1.29 → 1.15 via 665D card nesting. Ruled non-blocking (D-104) | `$EV/exit/darkmode.md` DD-7 | FU-8 |
| F-670A-5 | LOW (evidence note) | Dark-walk rows 3–101 share one browser context, so after row 2's Ctrl+B they loaded with the rail **collapsed** (cookie carried), not only row 74. No verdict changes (expanded-rail nav audited on row 1; rail state does not change body tokens). Raised by the bug/quality delta-2 review of #1883 | `$EV/exit/scripts/m2-darkwalk.cjs:58-65` | FU-39 (runner hardening: fresh context per rail state) |

## CodeRabbit later rounds (one-round rule)

| Round | Finding | Disposition |
|---|---|---|
| 2 (@92073b211) | Major: "Fix the phase-caused F-670A-4 regression before closing 670" (`tasks/phase91-ui-modernization-ii.md:1099`) | **Deferred**: not Critical / security / tenant / data / money; already D-104 → FU-8; PR reply posted on #1883 |
