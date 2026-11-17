# 646E observed gates — PR #1791 @ d3964c700 (2026-11-17, SAST)

All commands ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-646E`, at HEAD d3964c700a81a8ec5dce7d1764a5afdfc1935165. The tree was clean before and after.

- No source edits, no commits, no Maven.
- OBS changed state only through the UI: Alice and Bob, signed in via `/mock-login`.
- psql was used read-only, as evidence.
- API reads used the browser context's own `mock-auth-token` against `:8081`. The frontend fetches server-side and `:3001` has no `/api` proxy, so a page network capture cannot see these calls.

## Stack builds
The previous slice's stack was torn down first (`stack0-down.log`). Exactly one e2e stack ran at a time.

| Stack | Built | Flyway | Evidence |
|---|---|---|---|
| #1 (lifecycle + OBS) | "Building from source at: …/worktree-epic-646E" | V16–V18 success=t at 00:49:41 UTC; 0 trust txns before the suite | `stack1-build.log`, `stack1-proof.txt`, `stack1-pre-lifecycle-db.out`, `stack1-backend.log` |
| #2 (depth-ii) | same source path, after an `e2e-down.sh` wipe (`stack1-down.log`) | V16–V18 success=t at 00:57:50 UTC; 0 trust txns before the suite | `stack2-build.log`, `stack2-proof.txt`, `stack2-backend.log` |

Proof that both stacks run this worktree's code (`vproof.sh`):
- `/app/app.jar` holds `transaction/TrustTransactionTypeController.class` and `transaction/TrustTransactionDirection.class`. Both are new in 646E.
- The extracted controller class contains the literal `api/trust-transaction-types`.
- The frontend container's `.next/server/chunks/ssr` contains `trust-transaction-types` (3 chunks).
- Unauthenticated `GET :8081/api/trust-transaction-types` returns **401**, so the endpoint is mapped behind auth. With Alice's session token it returns **200** (OBS a).
- Health is UP and `:3001` returns HTTP 200. Both stacks have the single tenant schema `tenant_7d218705360b`.

## Gate 1a — E2E-LEGAL lifecycle (stack #1): PASS (observed)
**105 passed / 0 failed / 0 skipped in 3.4 min** (206 s wall), rc 0. This matches the baseline of 105. `.last-run.json` reads `passed` (`lifecycle-last-run.json`).

- Log: `lifecycle.log`, summary in `lifecycle-summary.txt`.
- Trust lines: `lifecycle-trust-tests.txt` (37).
- Suite screenshots: `lifecycle-artifacts/`.

## Gate 1b — E2E-LEGAL depth-ii (stack #2, fresh): PASS (observed)
**40 passed / 4 skipped / 0 failed in 2.1 min** (127 s wall), rc 0. This matches the baseline of 40/4. The 4 skips are the baseline ones:
- `day-30-45-soa-writeoff.spec.ts:361` (fixme)
- `null-prefetch-probe.spec.ts:74` ×3 (opt-in)

Log: `depth-ii.log`, summary in `depth-ii-summary.txt`, screenshots in `depth-ii-artifacts/`. The read-only sums after the run (`depth-ii-derived-sums.out`) match 646B:
- Sipho has DEPOSIT 5 000 and PAYMENT −1 250.
- Cashbook 3 750.00 = client ledgers 3 750.00, so EQ-2 is 0.00.

## Gate 2 — OBS (stack #1, after lifecycle): PASS (observed)
**Script:** `obs.cjs`, adapted from the 646B/649B templates.

**Output:**
- `obs/obs-result.json` and `obs/obs-output.log`
- `obs/NN-*.png` and `.txt`
- SQL in `obs-sums.sql` / `obs-sums.out`
- Summary in `obs-summary.txt`

**Console errors: 0. Page errors: 0.**

Dates: today 2026-11-17. Apex deposit 2026-11-14, payment 2026-11-15, reversal 2026-11-17. Run tag `ZDF70`.

### a. Type registry: PASS
`GET /api/trust-transaction-types` from Alice's session returned 200 with **25 items** (`obs/a-trust-transaction-types.json`).

- **Keys:** `code, label, direction, ledger, origin, requiresApproval, reversal, clientVisible`.
- **DEPOSIT:** label "Deposit", direction **IN**, ledger **CLIENT**, origin FIRM, reversal NEEDS_APPROVAL.
- **REVERSAL:** label "Reversal", direction **NEUTRAL**, ledger **null**, origin null, reversal NONE.
- **Without a token:** 401.

### b. Transactions page: PASS (`01`, `02`)

**Filter pills**
- There are **25 type pills**, plus "All".
- Their labels equal the registry labels in registry order, and their `?type=` hrefs equal the registry codes.
- The labels are in sentence case: "Fee transfer", "Transfer out", "Transfer in", "Placed in section 86(4) investment", and so on.
- There are **no "Interest Credit" or "Interest LPFF" pills**, and none of the old Title-Case pills.

**Type badges**
- The badges are human labels.
- **No raw type code** (any of the 25, nor INTEREST_CREDIT, INTEREST_LPFF or DISBURSEMENT_PAYMENT) appears anywhere in the page text.

**Clicking "Deposit"**
- The URL becomes `?type=DEPOSIT`.
- 2 rows remain, both "Deposit". SQL also counts 2 deposits at that point.
- The pill is shown active.

### c. F8 — REVERSAL direction: PASS (`03`–`06`)
What I did, all through the UI:
1. Alice recorded the Apex deposit R 20 000 (RECORDED).
2. Alice recorded the Apex payment R 4 000 (AWAITING APPROVAL).
3. **Bob approved** the payment, which became APPROVED. Its row shows Payment, arrow-up-right, not green (`05`).
4. Alice opened Reverse on the APPROVED payment. The dialog shows the plain note, with no approval note.
5. She submitted a reason.

Results:

| | UI (`06`) | API (`GET …/transactions/{id}`) | SQL |
|---|---|---|---|
| Contra `REV-OBS646E-PAY-ZDF70` | "Reversal" R 4 000,00 RECORDED, **green + `lucide-arrow-down-left`, so it renders as an inflow** | type REVERSAL, typeLabel "Reversal", **direction IN**, ledger CLIENT, postedAt set, reversalOf = the payment | ledger_effect **+4 000**, cashbook +4 000, posted |
| Payment | "Payment" OUT, **REVERSED**, no Reverse offered | direction OUT, status REVERSED, reversedById = the contra | REVERSED, ledger_effect −4 000, reversed_by_id = the contra |

The Transactions page has no `data-direction` attribute. Its inflow/outflow markers are the green class and the lucide arrow icon, so those were asserted. The overview table carries `data-direction` (see f).

### d. R1 — pending contra: PASS (`07`)
Before: the RECORDED QuickCollect deposit `DEP/QC/…` (R 45 000) offered Reverse. Its API read showed `reversalPending: false` and `reversedById: null`.

Alice reversed it. The dialog carried the note "A deposit reversal must be approved by another member before it takes effect." After that:

**UI**
- The **deposit row no longer offers Reverse**: there are 0 `reversal-action-{deposit}` elements. The row still reads Deposit, IN, RECORDED.
- The pending contra `REV-DEP/QC/…` reads "Reversal" R 45 000,00, AWAITING APPROVAL, with Approve/Reject. It shows **arrow-up-right and is not green (OUT)**.

**API**
- The deposit has **`reversalPending: true`**, `reversedById: null`, status RECORDED.
- The contra has **direction OUT**, status AWAITING_APPROVAL and `postedAt: null`.

**SQL**
- The contra is REVERSAL with status **AWAITING_APPROVAL**.
- Its **`reversal_of` = the deposit (1a220bef…)**.
- It is not posted, with effects 0/0.
- The deposit's `reversed_by_id` is NULL.

### e. Client ledger page vs Transactions page: PASS (`08`, `09`)

| Client | Rows compared by id | Same label | Same arrow | Pills |
|---|---|---|---|---|
| Apex | Reversal (IN), Payment (OUT), Deposit (IN) | 3/3 | 3/3 | 13, which equals the registry filter `ledger ∈ {CLIENT, CLIENT_INVESTMENT}` + Reversal, in order |
| QuickCollect | Reversal (pending, OUT), Payment (OUT), Deposit (IN) | 3/3 | 3/3 | same 13 |

**Interest pills (646A found item 1).** There are no "Interest Credit" or "Interest LPFF" pills. The only pill with "interest" in it is the registry's own `INV_INTEREST` "Section 86(4) investment interest", which is a CLIENT_INVESTMENT type and expected.

No raw type codes appear on either page.

### f. Trust overview Recent Transactions: PASS (`10`)
- All 10 rows carry `data-direction`: **in ×4** (2 lifecycle deposits, the Apex deposit, the payment contra) and **out ×6** (fee transfers, payments, the pending deposit contra).
- The labels are human (Deposit, Fee transfer, Payment, Reversal), and there are no raw codes.
- `neutral` was **not observed**: no real row resolves to NEUTRAL, because every UI-created REVERSAL resolves its original. It is covered only by the unit tests.

### g. Firm reports (F7): PASS (`11`, `12`)
**Client Ledger Statement (Apex, 2026-11-12 … 2026-11-17).** The Type column reads **Deposit / Payment / Reversal**. No `DEPOSIT`/`PAYMENT`/`REVERSAL` code appears in the result. Debit/credit and running balance run 20 000 → 16 000 → 20 000.
- Note (W1, out of scope): the Reversal row's Description prints the firm's reversal reason.

**Receipts & Payments (same period).** The Type column reads **Deposit / Fee transfer**, with no codes.
- Note (known F1 → 646C): the reversed Apex payment and its contra are not listed, because of the status/type filter in `findForReceiptsPayments`.

### Logs
| Log | Result |
|---|---|
| Backend stack #1 | 334 INFO, 153 WARN, **0 ERROR** |
| Backend stack #2 | 273 INFO, 200 WARN, **0 ERROR** |

The stack #1 WARNs are:
- PDF fallback fonts;
- the expected 403s on time-summary/by-member;
- tax-number-missing drafts;
- notification no-recipients;
- startup bean notices;
- **2 × `security.auth_failed path=/api/trust-transaction-types`**. These are the two deliberate no-token probes (vproof 00:50:57 and OBS 00:55:18).

The frontend has 33 × `⨯ Error: The destination stream closed early.`, the same unattributed navigation-abort noise recorded in 646B (27 there).

Final EQ-2 on stack #1: cashbook 306 500.00 = client ledgers 306 500.00.

## Anomalies
No new defect (`found-obs.md`). Three notes, all pre-existing and tracked:
1. W1: the reversal reason appears in the statement Description.
2. F1: Receipts & Payments omits the reversed pair.
3. NEUTRAL direction is not observable through the UI.

## Cleanup
- After each legal run I copied the suite screenshots to `lifecycle-artifacts/` and `depth-ii-artifacts/`, then ran `git clean -fd -- documentation/screenshots/legal-vertical`.
- Afterwards `git status --short` was **empty**: `frontend/test-results/` and `.epic-brief.md` are ignored. HEAD is d3964c700.
- **Stack #2 (depth-ii) is left UP.** Nothing else started by this run is running.
