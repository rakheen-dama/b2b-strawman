# 627K — Epic 627 gate (task 627.22)

PR #1702, branch `phase88/10K-gate`, final head `04fbfdd2b` (main `aa0442b91` + one `legal-lifecycle` spec file). Worktree `worktree-epic-627K`. 2026-10-28.

| Gate | Head | Result |
|---|---|---|
| **BE-VERIFY** (`spotless:apply` no diff; `bash scripts/verify.sh -q`) | 3087d47aa (`backend/` = main aa0442b91 = 627H verified head) | EXIT 0 — **6238 testcases / 0 failures / 0 errors / 26 skipped**, 738 reports, 20:26 wall (load 17–25) |
| **FE-GATE** (`rm -rf .next`; lint, format:check, test, build) | 04fbfdd2b | lint 0 errors / 91 warnings; format clean; build OK; vitest **431 files / 3421 passed / 2 skipped** (re-run; see flake note) |
| **PORTAL-GATE** (lint, format:check, test, build) | 3087d47aa (`portal/` identical at 04fbfdd2b) | lint 0 errors / 4 warnings; format clean; vitest **44 files / 238 passed**; build OK |
| **E2E-LEGAL** fresh stack (`e2e-down.sh` + `e2e-up.sh` from the worktree, 19:48; `--update-snapshots=changed`) | 04fbfdd2b | **legal-lifecycle 75 passed / 4 failed / 5 skipped / 14 did not run** (2.2 min). The failures are the carried four: A1 `day-00:282` (at `:306`), A2 `day-30:32` (`:141/:151`), A4 `day-45:41` (`:86/:97/:115`), A6 `day-90:179` (`:204/:212/:220`, same lines as the 625G log). New **Day 5** test `day-01-03-onboarding.spec.ts:365` ✓ (2.2 s). **legal-depth-ii 38 passed / 0 failed / 5 skipped** (1.9 min): `day-75-85-closure.spec.ts:305` (was `:297`, retention card, un-fixme'd in 627C) ✓, `day-14:335` ✓. The carried set did not grow. |
| **MARKERS** ×3 | 04fbfdd2b | `verify-backend.json`, `verify-frontend.json`, `verify-portal.json` |

## Day 5 header assertion
Appended to `test.describe.serial("Day 1 — Sipho Ndlovu Onboarding")`, where the RUN_ID matter "Ndlovu v RAF" exists. Steps:
1. The strip is absent before the fill (`toHaveCount(0)`).
2. Fill Case Number / Court / Opposing Party through Details › Custom fields, then Save.
3. The strip `dd`s equal `["2026/12345", "Gauteng Division, Pretoria", "Road Accident Fund"]` in place within 15 s, and each is visible. The `dt` reads "Case no.".
4. After a reload, the same values are visible.

The test observed both states (absent → three values), so it can't pass vacuously. No separate mutation run was done: a re-run on the used stack would fail upstream at the Day 1 conflict check (the earlier Sipho now matches), so a mutation run needs another fresh stack.

## Flake (pre-existing, not this PR)
The first FE-GATE run on 04fbfdd2b was at load ~18, with `e2e-up` Docker builds running in parallel. It failed 1 test: `__tests__/components/invoices/invoice-generation-dialog.test.tsx` › "calls createInvoiceDraft on Create Draft click". The cause is `waitFor` at `:254-256` with the default 1 s timeout ("Unable to find role=button name Create Draft").
- The same test passed on 3087d47aa.
- The only change between the two heads is an e2e spec, and vitest excludes `e2e/**` (`vitest.config.ts:8`).
- The test file has been unchanged since Epic 611B.

A full re-run at lower load was green. Follow-up: give that `waitFor` an explicit timeout, like the PR #1550 `invoice-generation-with-expenses` fix.
