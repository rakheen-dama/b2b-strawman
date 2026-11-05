# 634D — E2E gate (E2E-LEGAL + E2E-DEFAULT), PR #1736 head 422ddd3a8

Stack: E2E mock-auth (:3001 / :8081 / :5433), built from the 634D worktree. The frontend image was built from the worktree source at 13:52:33Z, which is byte-identical to `422ddd3a8`. Each stack below is **fresh**: `e2e-down.sh` → `docker builder prune -f` → `e2e-up.sh`. Runs were 2026-11-05, 15:57–16:37 SAST. No screenshot baselines are committed (only `.gitkeep`), so each suite's first run captures them.

| Stack | Suite | Result |
|-------|-------|--------|
| A (15:57) | `test:e2e:legal-depth-ii` | 16 passed, 4 skipped, 1 did not run, 22 failed. All 22 failures are `A snapshot doesn't exist … writing actual` (baseline capture). |
| A | `test:e2e:legal-lifecycle` | **Invalid run (orchestrator ordering error).** It ran on the same stack *after* depth-ii, so depth-ii's "Sipho Ndlovu" clients turned the lifecycle conflict checks into "Potential Conflict", and the serial suite cascaded (29 passed, 13 failed, 63 did not run). Nothing was fixed here. The run was replaced by C, D, F and H on fresh stacks. |
| A | `test:e2e` (default) | **164 passed, 14 skipped**. |
| B (16:04) | `test:e2e:legal-depth-ii` | 37 passed, 4 skipped, 1 did not run, 1 failed. The failure is `day-14-conveyancing.spec.ts:375`, where `page.waitForLoadState("networkidle")` timed out after 30 s. This is the same one-off timeout 634C recorded. It is on the OTP document preview, which does not use the disbursement table, and it did not recur on E, G or I. |
| C (16:08) | `test:e2e:legal-lifecycle` | 80 passed, 25 failed. 24 failures are baseline captures. The 25th is `day-00-dashboard-legal-nav-active.png`, which differs by 3% from the baseline captured on the polluted stack A. No functional failure. |
| D (16:13) | `test:e2e:legal-lifecycle` | 104 passed, 1 failed: the same polluted `day-00` baseline. |
| E (16:18) | `test:e2e:legal-depth-ii` | 38 passed, 4 skipped, 1 failed. The failure is a missing baseline, `day-14-otp-acceptance-request-sent.png`, never captured because B timed out before it. |
| F (16:21) | `test:e2e:legal-lifecycle` | 104 passed, 1 failed: re-capture of `day-00-dashboard-legal-nav-active.png`, after the polluted baseline was deleted. |
| G (16:26) | `test:e2e:legal-depth-ii` | **39 passed, 4 skipped**, exit 0. |
| H (16:30) | `test:e2e:legal-lifecycle` | **105 passed**, exit 0. |
| I (16:34) | `test:e2e:legal-depth-ii` | **39 passed, 4 skipped**, exit 0. This run also created the rows for the orchestrator's re-measure below. |

**Verdict.** The gates are green:
- legal-depth-ii: 39 passed with the 4 existing skips, twice on fresh stacks (G and I);
- legal-lifecycle: 105/105 on fresh stack H;
- default: 164 passed, 14 skipped.

The disbursement specs (`day-05-disbursements`, `day-30-45-soa-writeoff`, `day-75-85-closure`) passed on every run where they executed. Their only failures were first-run baseline captures. No E2E spec was edited.

## Orchestrator independent re-measure (stack I, head 422ddd3a8)

Measured with Playwright MCP on `[data-testid="disbursement-list"]`. "Past edge" counts the visible table elements whose right edge lies beyond the container rect. The matter is "Ndlovu — transition repro mw3ndrv6da03" (`15e68012-…`), `?tab=disbursements`.

| Mount | Viewport | client / scroll | Past edge | Billing visible per row | Actions visible per row | Supplier sort controls visible |
|---|---|---|---:|---|---|---:|
| Firm list | 1440×900 | 1120 / 1120 | 0 | 1,1,1,1 | 1,1,1,0 | 1 |
| Firm list | 1024×768 | 704 / 704 | 0 | 1,1,1,1 | 1,1,1,0 | 1 |
| Matter tab | 1440×900 | 1072 / 1072 | 0 | 1 | 1 | 1 |
| Matter tab | 1024×768 | 656 / 656 | 0 | 1 | 1 | 1 |

The fourth firm-list row has no Actions trigger because no action applies to it (`hasActions`, unchanged from main). The document never overflows (`documentElement.scrollWidth === innerWidth`). These numbers match the builder's After table in `OBSERVATION.md`.
