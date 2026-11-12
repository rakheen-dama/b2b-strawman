# 638B evidence — floating AppHeader + command palette restyle (E2B)

Final code head: `67a073f7d`. Commits after it add evidence under this folder only.

| Folder / file | What |
|---|---|
| `fe-gate-summary.txt` | FE-GATE (`rm -rf .next` → lint, typecheck, format:check, full vitest, build), builder run at `d1893e6f4` and fix-cycle run at `67a073f7d`. Every step exit 0; 459 files, 3904 passed / 2 skipped |
| `before/` | 638.8 observe-first on a fresh stack built from `main` @ `9d7d2b62e`: `day-00-firm-setup.spec.ts` 16 passed, plus 11 header, palette and mobile screenshots (`before.cjs`, `before-report.json`, `README.md`) |
| `e2e/` | Fresh stack built from the worktree at `67a073f7d` (`e2e-down` → `docker builder prune -f` → `e2e-up`): navigation 16 passed, notification-bell 5 passed, legal-lifecycle 105 passed, legal-depth-ii 40 passed / 4 skipped. All equal the 637.1/638A baseline |
| `after-67a073f7d/` | 638.12 OBS on the same stack: O1–O17 PASS (`after.cjs`, `after-report.json`, `focus-probe.*`, `README.md`) |

OBS was run with a Playwright **library** script, not Playwright MCP. The MCP Chrome profile is held by the user's live session; 637A, 637B and 638A made the same deviation.

**Backlog (LOW, pre-existing, not changed in 638B):** when the command palette closes, focus goes to `<body>` instead of the control that opened it (header Search, sidebar pill, ⌘K). The palette is opened programmatically and has no Radix `DialogTrigger`, so Radix has no element to return focus to. Candidate fix for 644B: store `document.activeElement` on open and restore it in `onCloseAutoFocus`.
