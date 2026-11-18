# 648B final-head re-gate: PR #1801 @ 736bc12e6 (2026-11-18, UTC)

**Why this exists.** The full observed gates ran at `c952121f6` (`../gates-c952121f6/README.md`):
- lifecycle 109/0 on two fresh stacks;
- depth-ii 40 passed / 4 skipped / 0 failed;
- OBS a–f all PASS.

`736bc12e6` (CodeRabbit pass 4) is a **frontend-only** change to `RecordInvestmentInterestDialog.tsx` and its tests (`git diff --stat c952121f6..736bc12e6`: the dialog, the test file and the red-run evidence). This re-gate binds E2E-LEGAL lifecycle and an OBS of the changed dialog behaviour to the final head.

| Gate | Result |
|---|---|
| Stack | fresh: `e2e-down` then `e2e-up` from the worktree at `736bc12e6`. Build log: "Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-648B", frontend "Compiled successfully". Up 10:25:01–10:26:27Z. Health UP |
| E2E-LEGAL lifecycle | **PASS**: 109 passed / 0 failed in 3.1 min, rc 0, first run (`lc1.log`). The 648B tests ran and passed: `day-60-interest.spec.ts:523` (NET bank advice, gross R100,00) and `:580` (s86(3) offers no Record interest) |
| E2E-LEGAL depth-ii | Carried from `c952121f6` (40 passed / 4 skipped / 0 failed). The delta touches only the interest dialog, which depth-ii does not exercise |
| OBS (pass-4 dialog changes, read-only, no submit) | **PASS** (`obs-a11y.cjs`, `obs/obs-a11y-result.json`, `obs/01-*.png`, `obs/02-*.png`). Checks: radiogroup accessible name "How did the bank credit it?" (via aria-labelledby); `aria-required="true"` on date and gross in bank-advice mode, none on the Fund share; default date 2026-11-18 = today in SAST; gross typed then Cancel → reopened dialog has gross "" and date 2026-11-18 (reset on open); 0 console errors, 0 page errors |
| Teardown | `e2e-down` rc 0; 0 `e2e-*` containers. Lifecycle PNGs copied to `lc1-artifacts/` and cleaned from `documentation/screenshots/legal-vertical`; no tracked file changed; HEAD unchanged |
