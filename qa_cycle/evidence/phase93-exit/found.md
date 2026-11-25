# Phase 93 exit (660A) — deferred review items

Review findings on PR #1858 that were not fixed in 660A. Both need edits to shared e2e helpers that
the legal-lifecycle, legal-depth-ii and exit-month suites import. Those suites' runs of record were
being collected from the same worktree while the review was addressed, so editing the helpers then
would have invalidated them. Each item is a test-infrastructure refactor (LOW, test quality); fix
each in its own PR after the 660A runs of record are in.

| ID | Where | Finding | Suggested fix | Why deferred |
|---|---|---|---|---|
| R-1 (review M1) | `frontend/e2e/helpers/take-on.ts` `evidenceDir()` (:61-84); `frontend/e2e/helpers/trust-balanced-month.ts:42-58` (`evidenceDir`), `:199` (`csvFile`), `:250` (`evidenceShot`) | The take-on spec routes the shared helpers' evidence directory by setting `process.env.EXIT_MONTH_EVIDENCE_DIR` before `trust-balanced-month` fixes its directory on first use. This is hidden coupling through a global: the shared module fixes the directory once per worker, so any other spec in the same worker would then write to the take-on dir. | Give the shared helpers an explicit directory: e.g. a `setEvidenceDir(dir)` export, or an optional `dir` parameter on `evidenceShot` / `csvFile` / `writeEvidence`, and drop the env-var routing in `take-on.ts`. | Edits `trust-balanced-month.ts` (imported by the exit-month suite). Mitigated in 660A: `take-on.ts` fails fast if `EXIT_MONTH_EVIDENCE_DIR` is already set to another directory, or if the shared module has already fixed one; the take-on project runs alone with `workers: 1`. |
| R-2 (review M2) | `frontend/e2e/helpers/take-on.ts:50` (`MAILPIT_URL`), `:226-239` (`mailpitTotal`); `frontend/e2e/helpers/mailpit.ts:1` | A second Mailpit client: `take-on.ts` reads `/api/v1/messages?limit=1` with its own `MAILPIT_URL` default (`:8026`, the e2e stack), while `mailpit.ts` defaults to `:8025` (the dev stack). | Add a `mailpitSummary()` (total and latest message) to `mailpit.ts` and use it in `take-on.ts`; settle the default port in one place (the e2e suites already pass `MAILPIT_URL`). | Edits `mailpit.ts` (imported by the legal-lifecycle and legal-depth-ii suites); changing its default port would change which Mailpit those suites read. |

## Post-merge additions (LOW review notes on PR #1858, delta review @73edaa9f5 — not fixed in-PR per the HIGH/MEDIUM-only rule)

- **R-3 — fixture expiry 2027-08-15.** The D-265 refusal fires inside step 10 (after steps 0–9 have committed into the fresh stack); from that date steps 11–17 are skipped too (`test.describe.serial`). Suggested fix: call `ficaQueueOn(D.today)` in `beforeAll` so the run refuses up front. **Renew the CDD dates in `qa/fixtures/take-on/` before 2027-08-15.**
- **R-4 — D-265 alternatives.** The row could also name "make the CDD dates relative to C" (templating the reproducible XLSX at run time) as the alternative considered.
- **R-5 — references.** R-1/R-2 line refs are 1–2 lines off (`evidenceDir` `take-on.ts:63-86`, `MAILPIT_URL` `:51`, `mailpitTotal` `:227-240`); `gap-report.md` lists R-1/R-2 under "Carried (known)" though they are new in PR #1858; the `suites/README.md` carry-over diff command omits `frontend/package.json` (unchanged in the delta).
