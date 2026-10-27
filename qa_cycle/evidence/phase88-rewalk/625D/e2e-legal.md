# 625D — E2E-LEGAL on a fresh stack (PR #1682 head 146728b9e)

Stack: `e2e-down.sh` + `e2e-up.sh` from the worktree, 2026-10-27T15:09Z → 15:14Z. Snapshots `--update-snapshots=all` (baselines untracked).

| Suite | Result | Baseline (624I) |
|---|---|---|
| legal-lifecycle | 74 passed / 4 failed / 5 skipped / 14 did not run | 74 / 4 / 5 / 14 |
| legal-depth-ii | 35 passed / 0 failed / 8 skipped | 35 / 0 / 8 |

Lifecycle failures (all carried-six, `tasks/phase88-research/R10-playwright-carried-failures.md`):
- A1 `day-00-firm-setup.spec.ts:282` Trust account settings page loads
- A2 `day-30-billing.spec.ts:32` Create fee note for Sipho Ndlovu
- A4 `day-45-recon-prescription.spec.ts:41` Start new bank reconciliation
- A6 `day-90-review.spec.ts:179` Profitability dashboard (strict-mode `getByText` soft asserts :204/:212)

Set vs 624I {A1, A2, A4, A5}: A5 (`day-90-review.spec.ts:63` fee notes list) now passes on this stack, so the serial run reaches A6, which was previously never run. The carried six did not grow. 625D touches no profitability, fee-note or trust code.

Amended specs (625D, combobox Event-type filter) — both green:
- `legal-depth-ii/day-30-45-soa-writeoff.spec.ts:336` Verify write-off audit event present in audit log ✓
- `legal-depth-ii/day-75-85-closure.spec.ts:382` Verify override audit event captures override_used=true + justification ✓

Frontend gate (orchestrator re-run on 146728b9e): lint 0 errors; format:check clean; vitest 418 files / 3190 passed / 2 skipped; `rm -rf .next && pnpm build` ✓. CI Frontend job pass on 146728b9e.
