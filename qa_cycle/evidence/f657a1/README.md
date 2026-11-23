# F-657A-1: matter Time tab stays stale after logging time (root cause, reproduction, fix)

## What was reported

657A's first legal-lifecycle run had 2 failures, logged as F-657A-1 in `qa_cycle/evidence/phase93/657A/found.md`.

### Failure 1: `day-75-complex.spec.ts:224`

- **Test:** "Alice: Log 360min on Moroka estate matter".
- **What happened:** after the 6 h entry was logged, Fees → Time showed only the Day 7 entry (`Report estate to Master of High Court 1h`) for the full 15 s wait.

### Failure 2: `day-90-review.spec.ts:98`

- **What happened:** the `Apex Annual Returns …` link was not found.
- **Cause:** this is a knock-on failure, not a second defect.
  - The day-75 describe is serial, so after test 79 failed, test 81 ("Create 2nd Commercial matter for Apex (annual returns)") never ran.
  - So the matter Day 90 looks for was never created (`lifecycle.log`: tests 80–81 "did not run").

## Root cause: a product defect, not the environment

`frontend/components/projects/time-summary-panel.tsx` copied its server props into state once, at mount:
`useState(initialSummary)`, `useState(initialByTask)`, `useState(initialByMember)`.
State initialisers ignore later prop changes.

On the matter page:

1. **Tabs mount on demand.** Radix `TabsPrimitive.Content` (`components/matters/matter-tabs.tsx`) unmounts inactive tabs, so the Time panel mounts when its tab is opened.
2. **A tab switch has no server round-trip.** It is `window.history.replaceState` (`matter-tabs.tsx:207-209`).
3. **The dialog closes before the fresh data commits.** `LogTimeDialog` awaits `createTimeEntry` and then closes. The action's `revalidatePath` refresh commits in a router transition that can land after the dialog has closed.

If the user (or the spec) opens Fees → Time inside that window:

- the panel mounts with the pre-entry props;
- when the refreshed props arrive, its state ignores them;
- it stays stale until a reload. Real users can hit this the same way.

## Reproduction, before the fix (E2E stack, frontend built from `main` at the 657A merge)

- **Script:** `repro.cjs` drives a real Chromium through the UI, the way the spec does:
  1. Load the matter.
  2. Tasks → Log Time 5 min.
  3. Open Fees → Time as soon as the dialog closes.
  4. Read the Entries card.
  5. Poll 35 s for it to catch up, then reload.
- **Setup:** matter "Estate Late Peter Moroka …" from 657A's lifecycle data. Run from node v20.11.1 with `PW_TEST=<worktree>/frontend/node_modules/@playwright/test`.

| Run | Throttle | Stale when Time opened | Never caught up in 35 s (reload fixed it) |
|---|---|---|---|
| `warm-x1.log` | none | 1/4 | **1/4** |
| `before-x4.log` | CPU ×4 | 1/8 | **1/8** |

This is the F-657A-1 signature. The Entries card was one short at open (e.g. 7 instead of 8), never updated without a reload, and was correct after it.

**Screenshots.** In the before and after runs, the script took them *after* its reload (an ordering mistake in the first version of the script). So `*-after-reload.png` show the corrected page, not the stale one; they are named for what they show. The script now captures `*-stale-before-reload.png` before reloading.

## Fix

`time-summary-panel.tsx`:

- **No date filter:** the panel renders the server props directly, so a refresh shows at once whenever it lands.
- **Date filter set:** the panel keeps local state only for the filtered data. One effect fetches it whenever the filter changes, and again whenever the server props change underneath it, so a filtered view does not lag either.
- **Out-of-order responses:** a response that arrives after a newer request started is dropped.
- **Clearing the filter:** shows the server props again without a fetch (the old code fetched the unfiltered data again).

**Unit tests** (`__tests__/components/projects/time-summary-panel.test.tsx`, +4):

- The regression test "shows fresh server data when its initial props change after mount" **failed before the fix** (red run recorded in the PR) and passes after.
- Three date-filter tests cover:
  - fetch on set;
  - re-fetch when the server data changes under an active filter;
  - no fetch on clear.

## After the fix (same stack, frontend rebuilt from this branch, measured on a cold restart)

| Run | Throttle | Stale when Time opened | Never caught up |
|---|---|---|---|
| `after-x4-cold.log` | CPU ×4, first run after the frontend restart | 1/12 | **0/12** (caught up in 1.8 s) |

- **The race still happens.** The tab can still open before the refresh commits.
- **It no longer causes a failure.** The panel updates as soon as the refresh lands, well inside the spec's 15 s.

## Gates (on this branch)

- **FE-GATE** (nvm node v20.11.1): passed.
  - `pnpm lint`: 0 errors (77 warnings, all of them on main before this change).
  - `format:check`: clean.
  - `build`: OK.
  - `pnpm test`: **491 files, 4717 passed, 2 skipped**.
- **Unit red run:** `unit-red-before-fix.txt` runs the new tests against the `main` component, and 3 fail.
  - The regression test fails.
  - The re-fetch-under-a-filter test fails.
  - The clear-without-fetch test fails, because the old code fetched again on clear.
- **E2E-LEGAL:** fresh stack (`e2e-down.sh && e2e-up.sh`) built from this branch.
  - `test:e2e:legal-lifecycle` **105 passed** (`e2e/lifecycle.log`).
  - `test:e2e:legal-depth-ii` **40 passed, 4 skipped** (`e2e/depth-ii.log`).
  - Both equal the baseline.
- **Backend:** unchanged, so no backend verify is required.
