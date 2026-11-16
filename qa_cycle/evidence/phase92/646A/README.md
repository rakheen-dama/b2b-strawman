# 646A observed gates — PR #1788 @ 3285515df (2026-11-16, SAST)

All commands ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-646A` (HEAD 3285515df, tree clean before and after). No source edits, no commits.

## Stack builds
| Stack | Built | V16 | Evidence |
|---|---|---|---|
| #1 (lifecycle + OBS + EXPLAIN) | 20:07–20:09, "Building from source at: …/worktree-epic-646A"; backend ran `./mvnw clean package` | flyway v16 "phase92 trust posting core" success=t at 18:09:16 UTC | `stack1-build.log`, `stack1-v16-proof.txt` |
| #2 (depth-ii) | 20:14–20:15, same source path | v16 success=t at 18:15:35 UTC | `stack2-build.log`, `stack2-v16-proof.txt` |

- The `/app/app.jar` in the backend container holds `db/migration/tenant/V16__phase92_trust_posting_core.sql` with sha1 49e4ebb5…, which is identical to the worktree file. The jar also holds `TrustLedgerQueryRepository.class` and `TrustPostingService.class`.
- `client_ledger_cards` columns are now `id, trust_account_id, customer_id, created_at, updated_at`. It has no `balance` and no totals columns.
- `trust_transactions` has the new columns `ledger`, `cashbook_effect`, `ledger_effect` and `posted_at`.
- The `chk_trust_txn_posting` constraint and the `idx_trust_txn_ledger_sum` index (partial, `WHERE posted_at IS NOT NULL`) are both present.
- Both stacks had the single tenant schema `tenant_7d218705360b`. Health was `{"status":"UP"}` and `:3001` returned HTTP 200.

## Gate 1 — E2E-LEGAL lifecycle: PASS (observed)
**105 passed / 0 failed / 0 skipped in 3.0 min** (183 s wall), exit 0. This matches the main baseline of 105/105. Log: `lifecycle.log`.

These trust-touching tests all passed:
- Moroka deposit R250 000 and the ledger balance check.
- QuickCollect deposit R45 000.
- The trust dashboard overview.
- FEE_TRANSFER R8 500, with Bob's approval leaving Moroka at R241 500.
- The reconciliation start.
- The Day-60 interest run, calculate only: no Approve or Post action offered, and the API refuses both.
- Placing the s86(3) and s86(4) investments.

The full list is in `lifecycle-trust-tests.txt`. There were no failures, so none needed classifying. The 16 documentation screenshots the suite wrote are copied to `lifecycle-artifacts/documentation-screenshots/`.

## Gate 3 — E2E-LEGAL depth-ii, on its own fresh stack: PASS (observed)
**40 passed / 4 skipped / 0 failed in 2.0 min** (119 s wall), exit 0. This matches the baseline of 40 passed / 4 skipped. Log: `depth-ii.log`.

The 4 skips are the same as baseline:
- `day-30-45-soa-writeoff.spec.ts:361`: a `test.fixme` for the Statements-tab PDF download. It is a pre-existing LocalStack presigned-URL problem inside the container, unrelated to 646A.
- `null-prefetch-probe.spec.ts:74` ×3: opt-in probes.

These passed:
- Trust-linked disbursement: fund the ledger, then approve a deeds-office trust payment.
- Statement of Account generate and save.
- Write-off.
- Matter closure: the all-green gate report (which includes the trust-balance gate), the failing-gates route, and the override.

Details are in `depth-ii-trust-tests.txt`; screenshots are in `depth-ii-artifacts/documentation-screenshots/`.

Extra read-only SQL on the depth-ii stack (`depth-ii-derived-sums.out`):
- Sipho has DEPOSIT 5 000 and PAYMENT −1 250. His derived balance is 3 750.00, and the HEAD-style sum is also 3 750.00.
- The cashbook is 3 750.00 and EQ-2 difference is 0.00.
- All invariant counts are 0, and there are 0 INTEREST_% rows.

## Gate 2 — OBS in a real browser (stack #1, after the lifecycle run): PASS (observed)
Script: `obs.cjs` (Playwright library, headless Chromium, Alice via `/mock-login`). Output: `obs/obs-result.json`, `obs/obs-output.log`, plus a `.png` and `.txt` for each page.

| Figure | UI | Derived SQL (`posted_at IS NOT NULL`) | HEAD-style sign-CASE (RECORDED/APPROVED) |
|---|---|---|---|
| Overview: trust balance (cashbook) | R 286 500,00 | SUM(cashbook_effect) = 286500.00 | 286500.00 |
| Overview: "Client ledgers … (agrees with the cashbook)" | R 286 500,00 | SUM(ledger_effect, CLIENT) = 286500.00 | — |
| List: Moroka Family Trust | R 241 500,00 (deposits 250 000, fee transfers 8 500) | 241500.00 | 241500.00 |
| List: QuickCollect Services | R 45 000,00 (deposits 45 000) | 45000.00 | 45000.00 |
| Moroka ledger page (3 txns, the most) | Trust Balance R 241 500,00; totals 250 000 − 0 − 8 500 − 0 = 241 500 | 241500.00 | 241500.00 |
| QuickCollect ledger page (2 txns) | R 45 000,00 | 45000.00 | 45000.00 |

- **EQ-2**: cashbook 286500.00 − Σ client ledgers 286500.00 = **0.00**.
- **Invariant counts** were all 0:
  - posted status (RECORDED/APPROVED/REVERSED) with `posted_at` NULL;
  - AWAITING_APPROVAL/REJECTED rows with `posted_at` set or a non-zero effect;
  - posted rows that break the `|ledger_effect| ∈ {0, amount}` / `cashbook ∈ {ledger, 0}` shape.
- **INTEREST_% rows**: 0.
- The 3 AWAITING_APPROVAL rows (R200 000, R30 000, R500) carry `posted_at` NULL and zero effects. They are correctly excluded.
- **Interest UI**:
  - The client-ledgers list headers are Client Name, Trust Balance, Total Deposits, Total Payments, Total Fee Transfers, Total Refunds and Last Transaction. There is **no Total Interest column**.
  - The ledger totals breakdown on both client pages has **no "Interest credited" row**.
  - No page visited contains "total interest" or "interest credited".
- **Console errors: 0. Page errors: 0.**
- Screenshots:
  - `obs/01-trust-overview.png`
  - `obs/02-client-ledgers.png`
  - `obs/03-client-ledger-01-48eef20a-….png` (Moroka)
  - `obs/03-client-ledger-02-1379330a-….png`
- SQL: `obs-derived-sums.sql` / `obs-derived-sums.out`.
- **Observation, not a gate failure:** the client-ledger detail page's **Type filter pills still offer "Interest Credit" and "Interest LPFF"** (visible in `obs/03-client-ledger-01-…png`). V16 removed both types from `chk_trust_txn_type`, so these filters can never match a row. A related leftover: `chk_trust_txn_customer_required` still mentions `INTEREST_LPFF` (shown in `stack1-v16-proof.txt`). This is harmless but dead.

**Demo-tenant OBS (seeded books R30 000,00 / −R3 000,00 / cashbook R27 000,00): DEFERRED.** Demo provisioning needs the Keycloak Admin API, which the mock-auth e2e stack does not have; 645A recorded the same reason. No SQL workaround was attempted.

## Gate 2 — EXPLAIN, risk R-8 (stack #1)
Files: `explain-r8.sql` / `explain-r8.out`, plus the probes `explain-r8-supplementary*.sql/.out`.

Each run happened in one psql session:
1. `BEGIN`.
2. Insert 200 synthetic customers and 50 000 posted RECORDED DEPOSIT rows on the primary account, spread round-robin over those 200 customers across about 700 dates. All CHECKs and FKs were satisfied.
3. `ANALYZE`.
4. `EXPLAIN (ANALYZE, BUFFERS)` with literal binds.
5. `ROLLBACK`.

| Query (repository SQL, literal binds) | Plan | Exec ms |
|---|---|---|
| Q1 `cashbook`, asAt NULL | Seq Scan → Aggregate | 8.85 |
| Q1b `cashbook`, asAt 2026-06-30 | Seq Scan → Aggregate | 9.02 |
| Q2 `clientLedgerTotal`, asAt NULL | Seq Scan → Aggregate | 10.81 |
| Q3 `clientBalances` (report batch, 202 groups) | Seq Scan → HashAggregate → Sort | 15.55 |
| Q4 `clientBalancePage` p0/20 | Seq Scan → HashAggregate → top-N heapsort → Limit | 14.66 |
| Q4c its countQuery | Seq Scan → Sort → Aggregate | 20.30 |
| Q5 `generalBalancesByClient` (20 ids via subselect) | Seq Scan + Hash Semi Join → HashAggregate | 13.06 |
| Q5 with 3 literal ids | Bitmap Index Scan on `idx_trust_txn_customer` | 0.42 |

**`idx_trust_txn_ledger_sum` was not used by any plan.** It is valid (`indisvalid=t`, predicate `posted_at IS NOT NULL`).

- When I disabled seqscan, the planner chose `idx_trust_txn_account_date` (bitmap; 13.1 ms for Q1, 19.2 ms for Q4).
- When I disabled both seqscan and bitmapscan, it chose a plain Index Scan on `idx_trust_txn_account_date` (21.1 ms for Q4).

Why, as far as I can tell:
- All 50 006 rows sit on one account, so the account predicate selects about 100% of the table and a seq scan is the cheapest plan.
- Uncommitted rows are not all-visible, so an index-only scan on the INCLUDE index cannot pay off inside a rolled-back transaction.

**Caveat:** this test shape cannot show whether `idx_trust_txn_ledger_sum` helps in a multi-account tenant with vacuumed data. What I did observe: at 50k rows every derived-balance query finishes in **≤ 21 ms**.

**Rollback proof:**
- Before: 6 txns / 5 customers.
- After each of the 3 runs: 6 txns / 5 customers, with 0 rows matching `R8/SYN/%` and 0 customers matching `r8-synthetic-%`.
- The rolled-back inserts leave dead tuples until autovacuum runs. Stack #1 has since been torn down anyway.

## Cleanup
- I copied the suite-written `documentation/screenshots/legal-vertical/*.png` from each run into the evidence folder, then ran `git clean -fd -- frontend/test-results frontend/e2e/screenshots documentation`.
- `frontend/test-results` held only `.last-run.json` (`"status": "passed", "failedTests": []`). It is copied to `lifecycle-artifacts/test-results/`.
- Afterwards `git status --short` was **empty** and **HEAD was 3285515df**.
- The depth-ii stack (#2) is still running.

## Attribution
I saw no failures in either suite, so no failure is attributable to 646A. The only 646A-related findings are the leftover Interest Credit / Interest LPFF filter pills and the stale `INTEREST_LPFF` reference in `chk_trust_txn_customer_required`, both described above.
