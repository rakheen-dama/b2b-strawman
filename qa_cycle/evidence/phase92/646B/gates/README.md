# 646B observed gates — PR #1790 @ 65d0f7f72 (2026-11-17, SAST)

All commands ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-646B` (HEAD 65d0f7f721fe75e504d54fc1a6199b877e5e6a38, tree clean before and after). No source edits, no commits, no Maven. 646B adds only a read surface, so OBS changed no state at all; psql was used read-only as evidence.

## Stack builds
The old 649B stack was torn down first (`stack0-down.log`). Exactly one e2e stack ran at a time.

| Stack | Built | V18 | Evidence |
|---|---|---|---|
| #1 (lifecycle + navigation + OBS) | "Building from source at: …/worktree-epic-646B" | flyway v18 "phase92 trust creditor accounts" success=t at 23:03:25 UTC | `stack1-build.log`, `stack1-v18-proof.txt`, `stack1-backend.log` (0 ERROR lines) |
| #2 (depth-ii) | same source path, after an `e2e-down.sh` wipe (0 trust rows before the suite) | v18 success=t at 23:10:23 UTC | `stack2-build.log`, `stack2-v18-proof.txt`, `stack2-backend.log` (0 ERROR lines) |

- The `/app/app.jar` V18 file (`db/migration/tenant/V18__phase92_trust_creditor_accounts.sql`) has sha1 626ee618…. That is **identical** to the worktree file on both stacks. The jar also holds `creditor/TrustCreditorLedgerController.class`, which is new in 646B.
- The frontend container holds the route `.next/server/app/(app)/org/[slug]/trust-accounting/other-creditors`.
- `pg_constraint` on both stacks:
  - `chk_trust_txn_ledger_target` is present, with four branches: CLIENT, CLIENT_INVESTMENT, NON_CLIENT and INTER_ACCOUNT.
  - `chk_trust_txn_customer_required` is absent.
  - `chk_trust_txn_type` lists 25 names.
- Both stacks had the single tenant schema `tenant_7d218705360b`. Health was UP and `:3001` returned HTTP 200.
- **Both freshly seeded stacks already had 1 trust account with 2 creditor accounts and 0 trust transactions** at stack-up. The e2e seed creates the trust account through the app at 23:03:26, after V18 ran at 23:03:25 (`stack1-pre-lifecycle-db.out`).

## Gate 1a — E2E-LEGAL lifecycle (stack #1): PASS (observed)
**105 passed / 0 failed / 0 skipped in 3.0 min** (184 s wall), exit 0. This matches the baseline of 105. `.last-run.json` reads `passed`.

- Log: `lifecycle.log`, summary in `lifecycle-summary.txt`.
- Trust-touching lines: `lifecycle-trust-tests.txt`.
- Documentation screenshots: `lifecycle-artifacts/`.

## Gate 1b — the slice's own spec, `e2e/tests/navigation/sidebar-navigation.spec.ts` (stack #1, after lifecycle): PASS (observed)
**Setup.** The spec belongs to the default config (`e2e/playwright.config.ts`), not to a legal config. It ran through `pnpm test:e2e:navigation --reporter=list` with `PLAYWRIGHT_BASE_URL=http://localhost:3001`. That is the 628F precedent: the navigation suite on the fresh-seeded stack, after E2E-LEGAL.

**Result.** **17 passed / 0 failed in 8.1 s** (9 s wall), exit 0. Log: `navigation.log`.
- 14 of the 17 are the sidebar spec:
  - NAV-01 has 13 "page loads" tests, including **"Other trust creditors page loads"**.
  - NAV-02 is **"six zones in order with 29 items in order"**.
- The other 3 are `command-palette.spec.ts`.

## Gate 1c — E2E-LEGAL depth-ii (stack #2, fresh): PASS (observed)
**40 passed / 4 skipped / 0 failed in 2.2 min** (133 s wall), exit 0. This matches the baseline of 40/4. `.last-run.json` reads `passed`.

The 4 skips are the baseline ones:
- `day-30-45-soa-writeoff.spec.ts:361` (fixme)
- `null-prefetch-probe.spec.ts:74` ×3

Log: `depth-ii.log`, summary in `depth-ii-summary.txt`, screenshots in `depth-ii-artifacts/`. Read-only sums after the run (`depth-ii-derived-sums.out`):
- Sipho has DEPOSIT 5 000 and PAYMENT −1 250, giving 3 750.00.
- The cashbook is 3 750.00 and EQ-2 is 0.00.
- There are still 2 creditor accounts, and 0 transactions have a `creditor_account_id`.

## Gate 2 — OBS in a real browser (stack #1, after the lifecycle and navigation runs): PASS (observed)
**Script:** `obs.cjs` (Playwright library, headless Chromium, TZ Africa/Johannesburg). Logins went through `/mock-login` with the user select: Alice (owner), Bob (admin), Carol (member).

**Output:**
- `obs/obs-result.json` and `obs/obs-output.log`
- `obs/NN-*.png` and `.txt`
- SQL in `obs-sums.sql` / `obs-sums.out`

**Console errors: 0. Page errors: 0.**

### a. Sidebar
| User | Trust zone links (in order) | Directly after "Client ledgers" | href |
|---|---|---|---|
| Alice (`01`) | Overview, Transactions, Client ledgers, **Other trust creditors**, Reconciliation, Interest, Investments, Trust reports | yes (index 13 → 14) | `/org/e2e-test-org/trust-accounting/other-creditors` |
| Bob (`03`) | same | yes | same |
| Carol (`05`) | same | yes (index 9 → 10) | same |

When the page is open, the item shows the active highlight (`02`).

### b. Other trust creditors page (trust account "Mathebula & Partners Trust Account", 359eee4e…)
| Check | UI (Alice `02`; identical for Bob `04`, Carol `06`) | SQL / API |
|---|---|---|
| Title (h1) | "Other trust creditors" | — |
| Breadcrumb | "E2E Test Organization › Trust accounting › Other trust creditors" | — |
| Fund ledger card | "Legal Practitioners' Fidelity Fund — interest and charges · **R 0,00** · Interest received for the Fund, less bank charges" | FUND_INTEREST_AND_CHARGES. The derived balance is 0, and the API returns `balance: 0, inDebit: false, asAt: 2026-11-17`. |
| Unidentified card | "Unidentified deposits · **R 0,00** · Deposits whose owner is not yet known" | UNIDENTIFIED_DEPOSITS. The derived balance is 0, and the API returns `balance: 0, inDebit: false`. |
| Number of ledger cards | 2 | 2 rows for the trust account |
| In-debit notice (`fund-ledger-in-debit`) | absent | inDebit false |
| Raw enum codes in the page text | none (FUND_INTEREST_AND_CHARGES and UNIDENTIFIED_DEPOSITS were not found in the body text) | — |
| Empty or error state | neither shown; no "Something went wrong" | — |

`cardCount` is 3 in `obs-result.json`. The `[data-testid^="creditor-"]` selector also matches the grid container `creditor-account-cards`; the two per-type card test-ids are both present.

### c. Gating
- **Carol (member) sees the nav item and the page, with the same two R 0,00 cards.**
- This matches the seeded capability matrix (`obs-sums.out` "c: role capability matrix"):
  - member = {VIEW_TRUST};
  - admin = owner = {APPROVE_TRUST_PAYMENT, MANAGE_TRUST, VIEW_TRUST}.
- It also matches the page's gate (`VIEW_TRUST` or admin/owner), the nav item's `requiredCapability: "VIEW_TRUST"`, and the endpoint's `@RequiresCapability("VIEW_TRUST")`.
- No seeded user lacks VIEW_TRUST, so the negative case (no VIEW_TRUST, so notFound) was **not observed in the browser**. It is covered only by the PR's unit and integration tests.

Direct API calls were made with the browser context's request client, from Alice's and Carol's sessions:

| Call | Result |
|---|---|
| `GET http://localhost:8081/api/trust-accounts/{id}/creditor-accounts` with the context's `mock-auth-token` cookie as Bearer | **200** for both, with the two accounts above |
| The same call without a token | **401**. The backend logged this as WARN `security.auth_failed`. These are the only 2 auth-failure WARNs, and both are these probes. |
| `/api/trust-accounts/…` on the frontend origin `:3001` | **404**. The frontend has no `/api` proxy; the page calls the backend server-side through `lib/api`. |

### d. DB evidence (read-only, `obs-sums.out`)
| Check | Result |
|---|---|
| `trust_creditor_accounts` per trust account | 1 trust account, **2 rows**, 2 distinct types: FUND_INTEREST_AND_CHARGES "Legal Practitioners' Fidelity Fund — interest and charges" (374cedb5…) and UNIDENTIFIED_DEPOSITS "Unidentified deposits" (f6a66e02…) |
| `chk_trust_txn_ledger_target` | present (1) |
| `chk_trust_txn_customer_required` | absent (0) |
| `chk_trust_txn_type` | **25 names**: DEPOSIT … FIRM_INV_RETURN (full list in d5) |
| `trust_transactions` after lifecycle | 6 rows, all `ledger = CLIENT`. `creditor_account_id IS NULL` on **6/6** and `investment_id IS NULL` on 6/6. |
| EQ-2 | cashbook 286 500.00 − client ledgers 286 500.00 = **0.00**. This is the same cashbook as the 649B baseline. |
| Derived creditor balances | FUND_INTEREST_AND_CHARGES 0, UNIDENTIFIED_DEPOSITS 0 |
| `audit_events` with `entity_type = 'trust_creditor_account'` | **2 rows**, both `trust_creditor_account.created` at 23:03:26.897 by "Alice Owner"; the details carry `trust_account_id`, `account_type` and `name`. They come right after `trust_account.created` (23:03:26.889). |

There are audit events because both accounts were opened by the app's `TrustAccountService.createTrustAccount`, when the seed created the trust account after V18 ran. That is the expected "created through the app" case. The migration's own backfill INSERT (for trust accounts that existed before V18) was not exercised on this stack, because no trust account existed when V18 ran. So the "migration-created rows have no audit event" case was **not observed** here.

### e. Logs
| Log | Result |
|---|---|
| Backend stack #1 (`stack1-backend.log`) | 505 lines: 328 INFO, 153 WARN, **0 ERROR** |
| Backend stack #2 (`stack2-backend.log`) | 267 INFO, 199 WARN, **0 ERROR** |

The WARNs, in both stacks:
- PDF fallback-font notices;
- the expected 403s on matter time-summary/by-member;
- tax-number-missing draft warnings;
- the two deliberate 401 probes above.

None mentions creditor accounts.

The frontend log (`stack1-frontend.log`) has 27 × `⨯ Error: The destination stream closed early.` with no stack and no 646B reference. Next.js logs this when the client (Playwright) navigates away mid-RSC stream. It was not compared against a 649B frontend log, which was not captured then, so it is recorded here as unattributed noise rather than as 0.

## Anomalies
None against 646B. Two points are not observed and stay open, for the record:
1. The member-without-VIEW_TRUST negative path is not available among the seeded users.
2. The audit-event behaviour of the migration backfill INSERT was not exercised, because the seed creates the trust account after V18.

Machine note: other containers (`maestro-perf-*`, not started by this run) appeared while stack #2 was coming up. They were left alone and did not affect the counts.

## Cleanup
- After each legal run I copied the suite screenshots to `lifecycle-artifacts/` and `depth-ii-artifacts/`, then ran `git clean -fd -- documentation/screenshots/legal-vertical`.
- Afterwards `git status --short` was **empty**: `.epic-brief.md` and `frontend/test-results/` are ignored. HEAD is **65d0f7f72**.
- **Stack #2 (depth-ii) is still running.** Nothing else started by this run is running.
