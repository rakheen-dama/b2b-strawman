# 636A — legal-depth-ii (E2E-LEGAL), Phase 89 exit gate

**Status:** PASS

| Field | Value |
|---|---|
| Command | `cd frontend && NODE_OPTIONS= E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 PLAYWRIGHT_JSON_OUTPUT_FILE=/tmp/636A/G1-depth-ii.json pnpm test:e2e:legal-depth-ii --reporter=list,json` |
| Stack | G1: the same fresh stack as `lifecycle.md` (`1034b6af8`, tenant `tenant_7d218705360b`, Flyway 11/3, seed Complete!) |
| Baselines | Captured on stack C1 (`--update-snapshots=all`, after lifecycle, same order as G1): 23 frames, 0 missing vs the spec `captureScreenshot` names, `day-14-otp-acceptance-request-sent.png` present. Not committed |
| Order on stack | lifecycle → **depth-ii** → default |
| Start / end (SAST) | 20:10:51 / 20:12:49 (2.0 min). Outside 00:00–02:00 ✓ |
| Result (JSON stats) | expected 39 / unexpected 0 / skipped 4 / flaky 0 (duration 117 s) |
| Result (list summary) | `4 skipped` / `39 passed (2.0m)` |
| Exit code | 0 |
| Snapshot writes | 0 "writing actual" lines |

## Baseline comparison

| | passed | failed | skipped | did not run |
|---|---|---|---|---|
| Plan floor | ≥ 39 | 0 | 4 | — |
| Current main (634E run 7 / 634D G+I) | 39 | 0 | 4 | 0 |
| **This run (G1)** | **39** | **0** | **4** | **0** |

No test's outcome differs from current main: the totals are equal, there are no failures, and the skip count is the same.

## Named specs

The plan names no depth-ii specs.

## Skips (4, all declared fixme / opt-in)

From `raw/G1-depth-ii.tsv` (status `skipped`, last-result `skipped`, so none of them is a serial "did not run"):

| Test | Reason |
|---|---|
| `day-30-45-soa-writeoff.spec.ts:292` "Alice: Statements-tab Download fetches the PDF" | `test.fixme` at `:292` |
| `null-prefetch-probe.spec.ts:74` "Matters list -> matter (projects/[id]): 120 fresh-session clicks render the destination" | opt-in: `test.skip(!process.env.NULL_PREFETCH_PROBE, …)` at `:71` |
| `null-prefetch-probe.spec.ts:74` "Reports -> report detail (reports/[reportSlug]): 120 fresh-session clicks render the destination" | same |
| `null-prefetch-probe.spec.ts:74` "Sidebar /projects -> /dashboard: 120 fresh-session clicks render the destination" | same |

**Correction to the brief / 634C note.** That note attributed the 4 skips to "day-05, day-14, day-30-45 and null-prefetch-probe", and two of those are wrong:

- `day-05-disbursements.spec.ts:147` is a conditional `test.skip(await notFound.isVisible(), …)`. It did not fire, because the module is enabled.
- The `day-14-conveyancing` fixme was removed in 628E (comment at `:140`).

The real set is 1 fixme plus 3 instances of one parametrised opt-in probe. The count of 4 is unchanged (README 636A-c3).

## Failures

None. The known candidate `day-14-conveyancing.spec.ts:375` `waitForLoadState("networkidle")` 30 s timeout on the OTP preview did not occur in either C1 or G1.

## Capture run (C1, unscored)

- Result: `4 skipped` / `39 passed (1.9m)` (JSON: expected 39 / unexpected 0 / skipped 4), rc 0, 20:03:16–20:05:11 SAST.
- 23 "writing actual" lines and 23 PNGs. These match the 23 non-curated `captureScreenshot` names exactly.
- No non-snapshot failure occurred.
- Visual sanity check: `day-14-otp-acceptance-request-sent.png` shows an authenticated matter page (Khumalo Property Holdings conveyancing matter, Generated documents tab, Offer to Purchase "Awaiting Acceptance").
- The frame embeds the run-random matter suffix and a bell count of 14. G1 still matched within the configured threshold.

Summary: `raw/C1-depth-ii.summary.txt`.

## Raw output

`raw/G1-depth-ii.txt`, `raw/G1-depth-ii.tsv`, `raw/G1-depth-ii.json`

## Tree hygiene

See `README.md` § Tree hygiene.
