# Phase 93 exit — gap report (660A, task 660.5) — DRAFT

Compiled 2026-11-25 on worktree `epic-660A/take-on-capstone` (product code = `main` `ef5579c29`;
branch rebased on `main` `8534e17a2`, docs only). Stack A (take-on) containers created
2026-11-25T02:01:13Z; run of record 02:02:02Z → 02:04:32Z, 16/16 passed (`README.md`).

**Draft.** This register holds every finding of the take-on run (stack A) and the cross-references
the brief asked for. The orchestrator adds the findings of stacks B (legal-lifecycle,
legal-depth-ii) and C (exit-month), then finalises it with the closure report (660.6).

**Shorthands.** `$X` = `qa_cycle/evidence/phase93-exit` · `$EV` = `qa_cycle/evidence/phase93` ·
dec = `tasks/phase93-decisions.md` · `$FT` = `frontend/components/take-on`.

## Rules applied

Severity rubric (verbatim from the Phase 92 exit register):

- HIGH: a wrong money figure or balance on an authoritative surface (ledger, cashbook,
  reconciliation, statement, monthly list, Section 35, portal), lost or corrupted data, a privacy
  leak to a client, a security or tenant hole, or a legal-compliance breach.
- MEDIUM: a wrong or misleading figure or label on a non-authoritative surface, a concurrency race
  or deadlock with no corruption, a missing refusal that allows a bad state that is detectable and
  correctable, a broken UI path for a supported task, or a lost report or audit row.
- LOW: cosmetic, a11y, wording, test quality, refactor or doc staleness.

`(src)` = the source's own severity; `(assigned)` = this rubric. **DEFERRED** = a step the product
gives no way to observe in the browser; it is never counted as PASS. Each new defect gets its own
reproduce-first PR (with BE-VERIFY when it touches the backend), never a fix inside 660A.

## Summary (stack A)

| Severity | New, open | DEFERRED (no trigger) | Carried (known, cross-referenced) |
|---|---|---|---|
| HIGH | 0 | 0 | 0 |
| MEDIUM | 1 (G93-1) | 0 | 0 |
| LOW | 2 (F-660A-1, F-660A-2) | 2 (G93-2, G93-3) | 9 |

No money figure differed from the expected figures written before the run
(`qa/fixtures/take-on/README.md`): every card, message, equation, report figure, reconciliation
figure and database total matched.

## A. Findings of the take-on run

| ID | Source | Observed (repro: stack, user, step, input) | Expected (file:line / text) | Severity | Owning slice | Disposition |
|---|---|---|---|---|---|---|
| G93-1 | blocker B-1; `$EV/657C/found.md` F-657C-8; founder FR-1; dec D-259 | A trust position can be taken on only into a NEW non-primary account: the seeded primary opens on the stack date (`compose/seed/lib/trust.sh` sends no `openedDate`; `TrustAccountService.createTrustAccount` defaults to today) and no UI changes an account's opened date or its primary flag (`UpdateTrustAccountRequest`), so a cutover two months back is refused `ACCOUNT_OPENED_AFTER_CUTOVER` on the primary. On the non-primary G, **no UI posts a client deposit or a client payment** in the first post-cutover month: Transactions → Record, client ledgers and the matter Trust tab bind to the primary; record-from-statement on a S86_2 account offers only Fund interest, charge refund, unidentified deposit (credits) and Fund sweep, bank charge (debits) (`frontend/lib/types/trust.ts:357-399`). Stack A, Alice, step 15: the first month's "deposit" and "payment" were therefore an unidentified deposit and the Fund sweep (option B, both recorded from their lines and reconciled). | 660.2 / 660.3: "the first statement … one deposit, one payment"; a client's money in the first month on the taken-on account. The R438 727,40 world with client movements is proven only by `FirstPostCutoverReconciliationIntegrationTest` (657D, D-224). | MEDIUM (assigned: a broken UI path for a supported task — a firm cannot post client trust movements on the account it took on unless that is its primary) | product — multi-account trust (FR-1: next phase) | follow-up (next phase, FR-1) |
| G93-2 | blocker B-2; `$EV/655A/obs/README.md` check g; dec D-135, D-260 | Stack A, Alice, step 14: Compliance → "Check for Dormant Clients" → "No dormant clients detected." (backend "Dormancy check completed: threshold=90 days, candidates=0"); all 18 imported clients ACTIVE. The **dormancy job itself** (`DormancyScheduledJob.executeDormancyCheck`, `@Scheduled(cron = "0 0 2 * * *")` → `executeDormancyTransitions()`) has no product trigger: `JobQueueAdminController` only lists, retries and deletes DEAD_LETTER jobs; inserting a job row by SQL is forbidden in QA. | 660.3: "the dormancy job triggered via `JobQueueAdminController` leaves imported clients ACTIVE". The transition is proven by `ImportedClientDormancyIntegrationTest` (BE-VERIFY), not in the browser. | LOW (assigned: an observability gap; the read-only check and the integration test cover the behaviour) | product (655A surface; needs a platform-admin "run job now" or equivalent) | **DEFERRED** — own PR |
| G93-3 | blocker B-3; `$EV/658A/obs/README.md` check 5b; dec D-261 | Stack A, Bob, step 12–13: Collections → "Debtor book" lists the six taken-on debtors, each amount in the bucket of its due date as of the run date; reminders off; `collection_activities` 0. The **CashDigest** has no trigger: `CashDigestScheduler.enqueueWeeklyDigest` runs only at `0 0 7 ? * MON`, no controller enqueues `cash_digest`. (The digest sends one email per owner/admin, so a trigger would grow Mailpit.) | 660.3: "the CashDigest (triggered) shows the debtors". | LOW (assigned: observability gap; the debtor book shows the same data) | product (658A surface) | **DEFERRED** — own PR |
| F-660A-1 | stack A run 3 (2026-11-25); dec D-264 | Stack A, Bob, step 4 (trust batch VALIDATED with 2 statutory blocks — C-003 −R500,00 → `CLIENT_IN_DEBIT` + `CONTROL_TOTAL_EQ2`): the Validation step still shows **"Continue to sign-off"** (`$FT/take-on-wizard.tsx:340` gates it on `batch.status === "VALIDATED"` only). Clicking it opens the Sign-off step, which tells the admin "A partner or director signs off this take-on from their Home page." — although the batch can never be signed or committed (`allowedActions` = no SIGN_OFF, no COMMIT; the gate holds). Screenshot `$X/screenshots/04-trust-debit-block-sign-off-step.png`. | FD-16 / D-124: a statutory block is never overridable and the batch goes nowhere until the corrected file is validated; the UI should not invite the preparer onward (e.g. hide or disable the button while `counts.blocks > 0`, or say the take-on is blocked). | LOW (assigned: misleading wording / flow on a non-authoritative step; no gate bypass) | 657C (trust sign-off step) | follow-up PR (frontend only) |
| F-660A-2 | stack A backend log (`$X/run-of-record/backend-log-warn-error.txt`) | One WARN in the run window: Micrometer "Reached the maximum number of 'uri' tags for 'http.server.requests'. …" (the per-URI metrics tag limit). Not tied to a take-on action; the take-on and trust routes carry ids in their paths. | No WARN beyond the known fallback-font lines. | LOW (assigned: observability; metrics for later URIs are dropped, no user-facing effect) | platform (not Phase 93) | follow-up — check URI templating of the take-on / trust routes before filing a PR |

## B. Carried known items (cross-referenced, not re-filed)

| ID | Where recorded | Seen again in stack A? |
|---|---|---|
| OBS-656A-1 — the matter header shows the raw work-type code (D-182) | `$EV/656A/obs/README.md` check 4 | Not asserted (matter headers not opened) |
| OBS-658A-1 — the wizard header shows the cutover date as ISO | `$EV/658A/obs/README.md` | Not asserted; unchanged in code (`$FT/take-on-wizard.tsx:192` renders `batch.cutoverDate` raw) |
| OBS-657C-1 — no backend log line for a trust commit | `$EV/657C/found.md` | Yes — committed lines for clients, matters, debtors only |
| OBS-657C-2 — Results "Other trust accounts" label for the s86(3) placement | `$EV/657C/found.md` | Not asserted |
| OBS-657B-1 — deficit-row difference copy (D-195) | `$EV/657B/obs/README.md` | Not reached (the debit run's deficit row was not asserted) |
| OBS-657D-2 — pdfbox fallback-font WARNs per report render | `$EV/657D/obs/README.md` | Yes — 16 WARNs for the one render |
| F-657C-3 | `$EV/657C/found.md` | Not exercised |
| F-658A-1 — the Xero enqueue listener may never enqueue (FR-11: after 660A) | `$EV/658A/found.md`; dec D-258 FR-11 | Not exercised (no Xero connection on the e2e stack) |
| F-658A-7 / F-652E-8 — vitest load-timeout flake in `trust-balance-trend-chart.test.tsx` | `$EV/658A/found.md` | See the 660A FE-GATE in the PR (did / did not recur) |

Also seen, not defects: 8 × Next.js "⨯ Error: The destination stream closed early." in the
frontend container during the run (page reloads after the trust-account dialogs; recorded by
657B/657C/657D); the spec selectors fixed between attempts (README, "Earlier attempts").

## C. Pending (orchestrator)

- Stack B: legal-lifecycle (floor 105 passed) and legal-depth-ii (floor 40 passed, 4 skipped).
- Stack C: exit-month (floor 16 passed, portal dev server on :3002).
- Final counts, then the closure report (660.6).
