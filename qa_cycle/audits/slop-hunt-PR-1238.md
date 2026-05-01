# Slop hunt — PR #1238: fix(OBS-2104): bulk billing — accept NULL currency + include legal disbursements

**Batch**: D — SQL/billing
**Reviewed**: 2026-05-01
**Verdict**: NEEDS-FOLLOW-UP

## PR description vs diff

PR description claims "Two cascading defects". The diff confirms both: (1) loosened `te.billing_rate_currency = :currency` to `(... = :currency OR ... IS NULL)` plus `COALESCE(billing_rate_snapshot, 0)` in the SUM; (2) added `LEFT JOIN legal_disbursements ld` with `billing_status='UNBILLED' AND approval_status='APPROVED'` lifecycle gates and updated count + HAVING + amount projection.

Honest about scope. But: this PR was rewritten in `#1240` (OBS-2104b) within ~75 minutes of merge because it introduced a Cartesian-product row-multiplication bug. The PR's own targeted test scope (`-Dtest='*BillingRun*'`) did not catch this — the existing suite did not cover multi-task + 1-disbursement aggregates. So the PR's "test plan" passed but the underlying behaviour was wrong. Honest description, but the verification evidence ("69/69 pass") was insufficient relative to the actual risk surface of a SQL aggregation rewrite.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | MEDIUM | Workaround masquerading as fix — accepting NULL where invariant should hold | `BillingRunSelectionService.java:498` (post-PR) | NULL `billing_rate_currency` accepted as "matches the run currency" and the entry is billed at R 0,00 via `COALESCE(billing_rate_snapshot, 0)`. This is a documented Path A workaround in `qa_cycle/fix-specs/OBS-2104.md` tied to the OBS-2101 WONT_FIX cascade (rate snapshot never happens when no rate card exists). The firm silently bills R 0 for an entry instead of erroring or skipping with a clear signal. | Track as a known-debt item. The right fix is upstream — either backfill currency at rate-card creation, prevent billable time entries without a rate card, OR surface a clear "0 lines could not be priced" warning at billing-run preview. The current path is a silent zero-revenue trap. |
| 2 | MEDIUM | Test-scope drift — full verify not run before merge per CLAUDE.md §1 | PR description test plan | `./mvnw test -Dtest='*BillingRun*'` (69) and `*Disbursement*,*FeeNote*` (57). No `./mvnw verify` evidence in the PR description. CLAUDE.md §5 requires full verify for any production-behaviour change. The targeted scope did not include `BillingRunPreviewCardinalityTest` (it didn't exist yet), so the Cartesian regression was undetectable by the chosen test set. | Already implicitly addressed: PR #1240 added the cardinality regression suite and (per cycle 23) full verify was confirmed clean. Going forward, treat any SQL-aggregation rewrite as needing a row-cardinality regression test in the same PR. |
| 3 | LOW | Comment over-narrating SQL | `BillingRunSelectionService.java:474-481` | 8-line comment block enumerates "Two cascading defects fixed here" with sub-points. The text duplicates what `OBS-2104.md` already documents and is now stale (PR #1240 superseded the SQL within hours). | Comment was rewritten in #1240. Resolved naturally. |
| 4 | LOW | HAVING clause growth | `BillingRunSelectionService.java:535-538` | Additional `OR COUNT(DISTINCT ld.id) > 0` line in HAVING — fine in isolation, but with the Cartesian product still present (pre-#1240) the HAVING was correctly counting via DISTINCT but the SUMs were broken. Symmetry of HAVING (DISTINCT) and SUM (no DISTINCT) is the smell that #1240 addressed. | Already addressed by #1240's CTE rewrite. |

## Test scope check

- Targeted: `./mvnw test -Dtest='*BillingRun*'` (69) + `'*Disbursement*,*FeeNote*'` (57). **Targeted, not full verify.**
- Did the test exercise the new behaviour? Partially — passing the existing 69 BillingRun tests proved the change didn't break the existing baseline. It did NOT prove the SQL produced correct totals across multi-source aggregates. The cardinality bug shipped, then was repaired by #1240. **This is the textbook test-scope-drift failure mode CLAUDE.md §5 was written to prevent.**
- The PR shipped a frontend-untouched backend SQL change. Frontend test plan was not relevant. No `pnpm test` in PR description, which is correct.

## Notes

- This PR superseded by PR #1240 within 75 minutes for the Cartesian bug. PR #1240's commit message explicitly says "Two cascading defects fixed here" was preserved as invariants but the SQL pattern changed. So #1238's behaviour-on-disk on `main` was never the long-lived state; it was a 75-minute window. Operationally this is fine, but documents a "ship-then-repair" pattern that the new lockdown rules are meant to prevent.
- The disbursement amount aggregation `ld.amount + COALESCE(ld.vat_amount, 0)` correctly handles legal_disbursement's modeling (no markup column, VAT bundled). This part is correct.
- The PR is single-file (1 changed). Scope discipline is met.
- One open question: `.snapshotBillingRate` is bypassed when no rate card matches, leaving `billing_rate_currency=NULL`. The fix accepts this. But the same time entry would also have `billing_rate_snapshot=NULL`. The COALESCE wraps that to 0 in the discovery query, but `createEntrySelections()` (lines 568-580) does NOT apply COALESCE — it just selects the IDs. The downstream invoice creation pipeline (out of this PR's scope) reads `billing_rate_snapshot` directly. **If invoice generation NPEs on a NULL snapshot, the wizard would create the selection but the generate step would fail.** Worth verifying at the next QA cycle that lines selected via the NULL-currency path actually produce a valid R 0 line on the fee note PDF, not a hard NPE.
