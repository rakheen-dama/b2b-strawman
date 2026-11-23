# 657A — found (outside the slice; not fixed here)

## F-657A-1 — legal-lifecycle run 1: two non-trust failures that did not reproduce

- **Where:** `e2e/tests/legal-lifecycle/day-75-complex.spec.ts:224` ("Alice: Log 360min on Moroka estate matter" — matter Fees → Time tab showed only `Report estate to Master of High Court 1h`, expected `/Prepare Liquidation & Distribution account\s*6h/` within 15 s) and `day-90-review.spec.ts:98` ("Matters list shows the lifecycle's six matters" — link `Apex Annual Returns <run-id>` not found in `matters-table`). 13 dependent tests did not run.
- **Run:** fresh stack built from `05812c045`, 2026-11-23 (`e2e/lifecycle.log`). Re-run on a second fresh stack: 105/105 (`e2e/lifecycle-run2.log`).
- **Relation to 657A:** none found — the PR touches no time-recording, matter-list or non-trust frontend code, and its backend changes are confined to `verticals/legal/trustaccounting/**`, `takeon` arch test and the V33 migration. Root cause not established (snapshots of run 1 were overwritten by the depth-ii run that followed on the same checkout).
- **Next:** if either recurs on a later slice's fresh-stack run, capture the `error-context.md` immediately and reproduce on `main` before filing as a product defect.
