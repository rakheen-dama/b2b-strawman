# Phase 92 — decisions log

One line per decision taken during execution. **Slice agents never wait for the founder**: they apply the
ruling below, or — where none exists — the documented default (else the most conservative safe option),
append a row here, and carry on. The founder reviews this file at stage checkpoints / phase end, not mid-run.
A row is a hard stop (`STOP`) only for: irreversible loss of real-tenant data, a security hole, or a
legal-compliance question with no safe default — and even then the agent records it and moves on to work
it is not blocking.

Columns: **id** · **source** (slice / found.md) · **decision taken** · **alternative** · **reversible?** · **status**
(`RULED` = founder ruling; `DEFAULT` = agent applied a default, founder to review; `STOP` = needs founder).

## Founder rulings — 2026-11-18 ("accept all recommendations")

| id | source | decision taken | alternative | reversible? | status |
|---|---|---|---|---|---|
| F-6a | 648A found.md | Re-check s86(3) placement cover at **approval** (under the account lock), as well as at request — two pending placements can no longer jointly overdraw the cashbook. Bug fix. | check at request only (current) | yes | RULED — follow-up FU-1 |
| F-6b | 648A F-6 / 649C N-1 | Cashbook-in-debit after a s86(3) placement — **combined rule** per the Fable legal opinion (`qa_cycle/evidence/phase92/legal/F-6-opinion.md`): (1) block approval of firm-initiated outflows from a trust banking account (payment, refund, fee transfer, s86(4)/s86(3) placement) unless the derived cashbook covers it, under an account lock — "withdraw from the s86(3) investment first"; (2) never block recording bank-confirmed entries (R14 / ADR-380 unchanged for records); (3) integrity flag `CASHBOOK_IN_DEBIT` prompting a Rule 54.14.8 check / 54.14.10 report. Open point: trust auditor / LPC to confirm the Rule 54.14.10 reading before the flag wording relies on it. | warning only; full block incl. recording | yes | RULED (founder sign-off 2026-11-18) — follow-up FU-9 (with FU-1) |
| R-2 | 648F found.md | Reports showing trust figures require the trust-view capability, not only financial visibility. Pre-existing gap. | leave as is | yes | RULED — follow-up FU-2 |
| R-1 | 648F found.md | A statement reads the general and invested balances in **one read-only, consistent transaction** (no double count of a concurrently approved placement). | accept the race | yes | RULED — follow-up FU-3 |
| W1 | 649B found.md | The firm's client-ledger-statement report applies the "reversal reasons stay off client surfaces" rule. | reword the page | yes | RULED — follow-up FU-4 |
| S2 | 649B found.md | A back-dated deposit reversal checks the client's (and matter cell's) balance **as at the reversal date as well as today**. | today only (current) | yes | RULED — follow-up FU-5 |
| F-646C-1 | 646C found.md | A second statement for the same account with the same end date is **refused unless it replaces the first** (re-import). | allow both | yes | RULED — owner 652C if it builds there, else FU-6 |
| F-647C | 647C found.md | Recording from / importing a statement line **dated after today is refused**, as the fee-transfer and reversal paths already are. | allow + flag | yes | RULED — follow-up FU-6 |
| O-1 | 648F found.md | Statement of Account line relabelled **"Trust balance held in the firm's trust account"** (pack bump + ADR-356 reconcile entry). | keep | yes | RULED — follow-up FU-7 |
| F-12 | 648B found.md | Dropping `trust_investments.interest_earned` without carrying totals over is **accepted** — no real tenants; demo/dev data only. | backfill | no (data dropped) | RULED |
| F-17 | 648B found.md | Backend CI Surefire heap raised in a separate scoped PR (not a founder call). Until then, an OOM CI run on green code is retried once and noted in the PR. | — | yes | RULED — follow-up FU-8 |
| 645A F-1 | 645A found.md | Interest-run wizard overflow: **dropped** — the wizard is removed in 651C. | fix | n/a | RULED |
| OQ #3–#14 | task file, Open questions | All proposed defaults **accepted**. | — | yes | RULED (DEFAULT-ACCEPTED) |

## Follow-up PRs (after the phase; one fix per PR, each with reproduce-first + full gates)

| id | covers | scope note |
|---|---|---|
| FU-1 | F-6a | `TrustInvestmentService` approval path for `FIRM_INV_PLACE`: lock funding account, re-check `cashbook(G, max(today, date)) ≥ amount`; red test with two pending placements first. |
| FU-2 | R-2 | Report definitions / report controller: trust-figure reports gated by the trust-view capability; capability-matrix test. |
| FU-3 | R-1 | Statement builders: read general + invested balances in a `REQUIRES_NEW`, read-only, REPEATABLE_READ sub-transaction (648F found.md option a). |
| FU-4 | W1 | Firm client-ledger-statement report: shared client-description rule + test. |
| FU-5 | S2 | Deposit-reversal approval: cover check as at `reversalDate` and as at today. |
| FU-6 | F-646C-1, F-647C | Statement import / record-from-line: refuse duplicate same-end-date statements (unless replacing) and future-dated lines. |
| FU-7 | O-1 | `legal-za` template pack: SoA label; version bump + reconcile entry + seeder reconcile test. |
| FU-8 | F-17 | `backend/pom.xml` Surefire `-Xmx` raise (durable fix: cull context-cache keys); full verify + CI ×2 green. |
| FU-9 | F-6a + F-6b | One PR with FU-1: approval-time cashbook cover check for all firm-initiated outflows under an account lock (lock order: transaction row → account → client anchor), `CASHBOOK_IN_DEBIT` integrity flag; recording paths untouched. Red tests first (two pending placements; payment after a s86(3) placement). |

## Decisions taken by slice agents (append below — newest last)

| id | source | decision taken | alternative | reversible? | status |
|---|---|---|---|---|---|
