# 657A OBS: the trust take-on registry observed in a real browser

- **Stack:** E2E mock-auth stack built from worktree-epic-657A @ `05812c045` (PR #1847). Frontend :3001, backend :8081. The backend container was created at 15:48:29 UTC. The Phase 92 exit-month spec had just run on it.
- **Tenant schema:** `tenant_7d218705360b` (`public.org_schema_mapping`, org `e2e-test-org`).
- **Browser:** headless Chromium launched by `obs.cjs` from `frontend/node_modules/@playwright/test`, run on node v20.11.1. The script is included here byte for byte as it was last run: `node obs.cjs`, signed in as Alice (owner) through `/mock-login`.
- **Database checks:** read-only psql `select` statements only. There were no SQL writes.
- **REST use:** only the GETs that capture the types JSON and the ledger-card JSON, and the three deliberate crafted POSTs of check 3. All of them went through the browser context (`page.request`), carrying the session's own credentials. Every UI checkpoint was observed in the browser.
- **Run time:** 2026-11-23. The OBS window was 15:55:05–15:58:00 UTC. The evidence files come from the final run, 15:57:38–15:58:00 UTC.
  - There were five runs; each run overwrote the files of the one before. Runs 1–4 failed or were superseded for script reasons, not product reasons:
    1. Run 1 clicked a Radix menu item during its open animation.
    2. Run 2 used `isVisible()`, which does not wait, on the client page's Trust card.
    3. Run 4 had no control request for check 3.
  - The crafted 3a/3b requests therefore went out four times in total (runs 2–5). See `psql-05`.

## Verdict: **PASS** (5 of 5 checks). One check-3 variant was not sent (reason below), and the wizard pane is DEFERRED to 657C.

| Check | Result | Observed |
|---|---|---|
| 1 Types endpoint | **PASS** | `GET /api/trust-transaction-types` → 200. **36** types. **11** have `takeOnOnly: true`, all `TAKEON_*`. The other **25** have `takeOnOnly: false` and none is `TAKEON_*`. Every entry carries both booleans. `clearedAtCutover: true` on **9** take-on types; `TAKEON_PAY_UNPRES` and `TAKEON_DEP_TRANSIT` are `false` by design. No pre-existing type is cleared. |
| 2 No take-on type offered | **PASS** | Transactions page: **25** type pills, equal in order to the registry's non-take-on labels. Ndlovu and Khumalo ledgers: **13** pills each; it would be 19 without the 657A filter. There were 0 hits for any take-on label or for "take-on / brought forward / b/f / cutover" in the pills, the Record Transaction menu, or any of the **10** dialogs opened. Reverse is still offered on 9 of the 20 rows on page 1. |
| 3 Crafted POST refused | **PASS** | No trust write takes a free type code (grep). `classification: "TAKEON_CLIENT"` on record-from-line → **400** "Malformed request body". Control `FUND_INTEREST` on the same line → 400 "already matched…", which shows the route and auth were fine. POST to the types registry → **405**. psql: `TAKEON_%` rows = **0**. The line is unchanged and there are still 31 trust rows. |
| 4 Ledgers and reconciliation unchanged | **PASS** | Ndlovu: R 69 380,00, nothing invested. Khumalo: general R 31 000,00, invested R 20 095,00, total R 51 095,00. Both equal the spec's FIG. "Brought forward at take-on" is hidden; that is the designed behaviour for a zero amount. API `broughtForward` = 0. Reconciliation 31 Oct 2026 and both monthly lists equal FIG (details below). Dashboard: "Books balance", No flags. DB: 0 rows cleared or batched, every cutover date null, V33 applied, the constraint lists 36 names. |
| 5 Console / backend errors | **PASS** | **0** browser console or page errors; the known mock-auth userinfo error did not occur. The backend logged 0 ERROR lines and 4 WARN lines, all of them `PageNotFound "Request method 'POST' is not supported"` from the crafted 3b requests. The frontend container logged nothing at warn or error. |

## Check 1: types endpoint (PASS)

- **Where it is fetched.** The endpoint is fetched **server-side**: `fetchTrustTransactionTypes()` in `trust-accounting/actions.ts:88` runs inside the RSC.
  - While the Transactions page loaded, the browser itself made **no** request to `/api/trust-transaction-types`. This was recorded by a `page.on('response')` listener; the listener's result is the first `[c1]` line in `obs-log.txt`.
  - In mock mode the frontend server authenticates by sending the `mock-auth-token` cookie's JWT as a Bearer token (`lib/api/client.ts` `getAuthFetchOptions`, `lib/auth/providers/mock/server.ts`).
  - So the response was captured by the same GET from the logged-in browser context, using that cookie's token. The result is in `types-endpoint.json`.
- **Counts.** Total 36. `takeOnOnly: true` = 11, `false` = 25. There are no missing flags.
- **The 11 take-on types:**

  | code | label | ledger | clearedAtCutover | reversal |
  |---|---|---|---|---|
  | TAKEON_CLIENT | Balance brought forward — take-on | CLIENT | true | NONE |
  | TAKEON_FUND | Fund ledger brought forward — take-on | NON_CLIENT | true | NONE |
  | TAKEON_FUND_DEBIT | Fund ledger brought forward in debit — take-on | NON_CLIENT | true | NONE |
  | TAKEON_UNIDENTIFIED | Unidentified deposit brought forward — take-on | NON_CLIENT | true | NONE |
  | TAKEON_INVESTMENT | Section 86(4) investment brought forward — take-on | CLIENT_INVESTMENT | true | NONE |
  | TAKEON_FIRM_INV_OUT | Placed in section 86(3) investment — brought forward | INTER_ACCOUNT | true | NONE |
  | TAKEON_FIRM_INV_IN | Section 86(3) investment brought forward — take-on | INTER_ACCOUNT | true | NONE |
  | TAKEON_PAY_UNPRES | Payment not yet presented at take-on | CLIENT | **false** | IMMEDIATE |
  | TAKEON_PAY_OFFSET | Take-on offset — payment not yet presented | CLIENT | true | NONE |
  | TAKEON_DEP_TRANSIT | Deposit not yet cleared at take-on | CLIENT | **false** | NEEDS_APPROVAL |
  | TAKEON_DEP_OFFSET | Take-on offset — deposit not yet cleared | CLIENT | true | NONE |

  - All 11 have origin `TAKE_ON` and `requiresApproval: false`.
  - The two `false` rows are intended. `TrustTransactionType.java:350-355` says that an item outstanding at cutover "is an ordinary matchable row on the client's matter, paired with a cleared offset".
- **The 25 pre-existing types,** all with `takeOnOnly: false` and `clearedAtCutover: false`:
  - DEPOSIT, PAYMENT, FEE_TRANSFER, REFUND, TRANSFER_OUT, TRANSFER_IN, REVERSAL
  - FUND_INTEREST, FUND_SWEEP, BANK_CHARGE, CHARGE_REFUND
  - UNIDENTIFIED_DEPOSIT, UNIDENT_ALLOC_OUT, UNIDENT_ALLOC_IN, UNIDENT_FUND_PAY
  - INV_PLACE, INV_RECEIVE, INV_INTEREST, FUND_INV_SHARE, INV_WITHDRAW, INV_RETURN
  - FIRM_INV_PLACE, FIRM_INV_RECEIVE, FIRM_INV_WITHDRAW, FIRM_INV_RETURN

## Check 2: no take-on type offered (PASS)

- **How each screen was checked.** The script read every screen's `innerText` and searched it for:
  - each of the 11 take-on labels from the endpoint; and
  - `/take-on|takeon|take on|brought forward|b\/f|cutover/i`.

  It found **0 hits** everywhere (`results.json` → `c2`).
- **Transactions page** (`?dateFrom=2026-10-01&dateTo=2026-10-31`, `s2-01`):
  - "All" plus 25 pills, equal in order to `offeredTransactionTypes(types)`, which is the registry minus the take-on types.
  - The pills run from Deposit … Section 86(3) investment returned to trust.
- **Record Transaction menu** (`s2-02`): Record Deposit, Record Payment, Record Transfer, Record Fee Transfer, Record Refund.
- **Dialogs opened and then cancelled.** None of them offers a transaction-type choice; the type is fixed by the dialog.

  | Dialog | Where | Shot | Pickers |
  |---|---|---|---|
  | Record Deposit | Transactions → Record Transaction | `s2-03` | Client and Matter combobox only |
  | Record Payment | Transactions → Record Transaction | `s2-04` | Client and Matter combobox only |
  | Record Transfer | Transactions → Record Transaction | `s2-05` | Free-text client and matter fields, no picker |
  | Record Fee Transfer | Transactions → Record Transaction | `s2-06` | Free-text Client ID and Fee note ID, no picker |
  | Record Refund | Transactions → Record Transaction | `s2-07` | Client and Matter combobox only |
  | Reverse Transaction | Transactions row → Reverse | `s2-08` | Reason and date only |
  | Record Deposit | Client page (Ndlovu) → Trust tab card | `s2-12` | Client is pre-filled; Matter combobox |
  | Record Payment | Client page (Ndlovu) → Trust tab card | `s2-13` | Client is pre-filled; Matter combobox |
  | Fee Transfer | Client page (Ndlovu) → Trust tab card | `s2-14` | Same as Record Fee Transfer |
  | Place Investment | Trust → Investments | `s2-15` | Only radio is the investment *basis* (FIRM_DISCRETION / CLIENT_INSTRUCTION), not a type |

- **Client ledger pages** (`s2-09` Ndlovu, `s2-10` Khumalo): "All" plus 13 pills, equal in order to `offeredTransactionTypes(types).filter(isClientLedgerType)`.
  - Without the 657A filter the list would have 19 pills. The six it removes are the CLIENT and CLIENT_INVESTMENT take-on types: TAKEON_CLIENT, TAKEON_INVESTMENT, TAKEON_PAY_UNPRES, TAKEON_PAY_OFFSET, TAKEON_DEP_TRANSIT and TAKEON_DEP_OFFSET.
  - The client ledger page itself has no posting dialog, only Print Statement.
- **Reversal is still offered.** 9 of the 20 rows on page 1 render a Reverse button. The first such row is `31 Oct 2026 STMT-19-20261031 Bank charge refunded from the business account R 57,50 RECORDED`, and its dialog opens (`s2-08`). `canOfferReversal` still reads the full registry.
- **Dialogs not opened.** Each was checked by reading the code instead; none has a transaction-type picker.
  - `RecordFromStatementDialog`: its radio list is `STATEMENT_LINE_CLASSIFICATIONS`, the six statement-line classifications (FUND_INTEREST … INVESTMENT_INTEREST). It is reachable only inside a reconciliation, after a statement upload, which would mutate the books. This is the same set check 3 shows the backend enforces as an enum.
  - `RecordInvestmentInterestDialog`: presentation radio GROSS/NET only.
  - `WithdrawInvestmentDialog`, `InvestmentInterestEntriesDialog`, `AllocateUnidentifiedDialog`, `PayToFundDialog`, `InterestMemoDialog`: no type field at all.
  - Grep: no `components/trust/*.tsx` dialog imports `TrustTransactionTypeInfo` or `fetchTrustTransactionTypes`.

## Check 3: crafted POST refused (PASS)

- **Write endpoints, from a grep of the trust `@PostMapping` / `@PutMapping` handlers:**
  - Most have no field that names what is posted: deposit, payment, transfer, fee-transfer, refund, approve, reject, reverse, investments, interest, withdraw, allocate, pay-to-fund, interest memo, reconciliation and statement.
  - Their type is fixed in code. For example, `TrustTransactionService` deposit builds `"DEPOSIT"` (lines 435 and 476).
  - The **only** body field that selects what is posted is `RecordFromLineRequest.classification` on `POST /api/bank-statement-lines/{lineId}/record`. It is a `StatementLineClassification` enum of 6 values and has no take-on member.
  - Backstop: `TrustPostingService.record()` throws for any `recordedOnlyAtTakeOn()` type, and `recordOpening` is `Propagation.MANDATORY`.
- **Requests,** sent from Alice's logged-in browser context with the session token and an `Idempotency-Key`. Full detail is in `crafted-post.txt`.

  | # | Request | Result |
  |---|---|---|
  | 3a | `POST /api/bank-statement-lines/e01a8f2c-…/record` `{"classification":"TAKEON_CLIENT"}` | **HTTP 400** `{"title":"Malformed request body","detail":"The request body could not be read or parsed","status":400}` |
  | 3b | `POST /api/trust-transaction-types` `{"code":"TAKEON_CLIENT"}` | **HTTP 405** `{"title":"Method Not Allowed","detail":"Method 'POST' is not supported."}` |
  | 3c (control) | Same line, `{"classification":"FUND_INTEREST"}` | **HTTP 400** `{"title":"Cannot record statement line","detail":"This statement line is already matched, recorded or excluded"}` |

  - 3a and 3c are both 400, but for different reasons. 3c shows that a known value reaches the service and is refused by the line-state guard, so 3a's 400 is the deserialiser rejecting `TAKEON_CLIENT`.
  - For 3a the backend replies with a generic "Malformed request body" ProblemDetail and logs nothing at INFO or above. The four 3b requests appear as WARN `PageNotFound` lines (`backend-log-warn-error.txt`, userId `user_e2e_alice`, tenant `tenant_7d218705360b`).
- **Not sent: a deposit carrying an extra `transactionType` field.**
  - Spring's Jackson ignores unknown properties, and the deposit endpoint fixes its type as `DEPOSIT`. So a well-formed body would have posted a real R-amount DEPOSIT to the exit-month books and broken check 4. A malformed one would prove nothing.
  - The code path is read above.
- **psql after the requests** (`psql-01`, `psql-05`):
  - `select count(*) … where transaction_type like 'TAKEON_%'` = **0**. Total trust rows are 31, the same before and after.
  - Line `e01a8f2c` is still `AUTO_MATCHED` to `274dbaa9-…`, with classification null.
  - `trust_idempotency_keys` stored the four 3a refusals (one request hash) and the 3c control, each as `COMPLETED / 400`. That is the designed replay of a 4xx refusal (648B F-11), not a posting.

## Check 4: existing ledgers and reconciliation unchanged (PASS)

- **Ledger card design.** `ledger-totals-breakdown.tsx` declares "Brought forward at take-on" with `hideWhenZero: true`. With no take-on, the designed result is **not shown**.
- **Ledger cards against the spec's FIG** (`trust-balanced-month.spec.ts`, step 15):

  | Client | General | Invested | Total held | "Brought forward" row | API `broughtForward` | Shot |
  |---|---|---|---|---|---|---|
  | Ndlovu Family Trust | R 69 380,00 (FIG 69 380,00) | not shown (FIG null) | not shown | absent; no text on the page | 0 | `s4-01` |
  | Khumalo Holdings | R 31 000,00 (FIG 31 000,00) | R 20 095,00 (FIG 20 095,00) | R 51 095,00 (FIG 51 095,00) | absent; no text on the page | 0 | `s4-02` |

  - Khumalo's breakdown starts at "Deposits R 47 500,00" with no leading "+". That is the designed behaviour when brought-forward is 0.
- **Reconciliation history** (`s4-03`): 31 Oct 2026, Bank R 103 130,00, Cashbook R 101 630,00, Client Ledger Total R 100 380,00, Balanced, completed 23 Nov 2026, List locked. This equals `FIG.g` (bank 103 130,00, cashbook 101 630,00, clientLedgers 100 380,00).
- **Monthly lists:**
  - **G's locked list** (`s4-04`): Adjusted bank, Cashbook and Trust creditors are each R 101 630,00, and the Difference is R 0,00.
    - Creditor rows: Khumalo R 31 000,00, Ndlovu R 69 380,00, Fund R 0,00, and Unidentified (ACB CREDIT UNKNOWN) R 1 250,00. These equal `FIG.list`.
    - There is no `monthly-list-brought-forward` row and no "B/f at take-on" text.
  - **I-B list** (`s4-05`): R 20 095,00 ×3, Difference R 0,00. Khumalo Invested (section 86(4)) R 20 095,00. There is no B/f row.
- **Trust dashboard** (`s4-06`):
  - The integrity card reads "As at 23 Nov 2026, all trust accounts | Books balance | No flags", with 0 flags.
  - Summary: Trust Balance R 101 630,00, Trust creditors R 101 630,00, Reconciliation Balanced.
- **psql, read-only:**
  - `psql-01`: 31 rows; `cleared_at_cutover = true` on **0**; `take_on_batch_id is not null` on **0**.
  - `psql-02`: `take_on_cutover_date` and `take_on_batch_id` are null on all three accounts.
  - `psql-03`: flyway `33 | phase93 trust take on | SQL | t`. V32 `phase93 take on core` is also applied.
  - `psql-04`: `chk_trust_txn_type` lists **36** names, 11 of them `TAKEON_*`, the same codes as the endpoint.

## Check 5: console and backend errors (PASS)

- **Browser.** 0 `console.error` and 0 `pageerror` in the final run, and in every earlier run (`obs-log.txt`, `results.json → consoleErrors`). The known spurious mock-auth userinfo error did not appear this time.
- **Backend, 15:55:00 onward** (`backend-log-obs-window.txt`, 13 lines):
  - 9 INFO job-queue fan-out lines, which are routine.
  - 4 WARN `org.springframework.web.servlet.PageNotFound "Request method 'POST' is not supported"`. These are the deliberate 3b requests, one per run that reached check 3; expected.
  - 0 ERROR.
- **Frontend container.** No warn or error lines in the window (`frontend-log-warn-error.txt` is empty).

## DEFERRED (not observable in this slice)

- **The reconciliation wizard's unmatched pane.** 657A changed `reconciliation/new/page.tsx`: it now pages every status and drops `clearedAtCutover` rows. That pane only renders during a new reconciliation, after a statement upload, and no cleared rows exist until a take-on commits. It belongs with 657C.
- **Any posting of a take-on row,** including the ledger card's non-zero "Brought forward at take-on" line and the monthly list's "B/f at take-on" row. Also 657C.

## Files

- `obs.cjs`: the script.
- `obs-log.txt`, `results.json`: the run log and the structured results.
- `types-endpoint.json`: the full registry response.
- `crafted-post.txt`: check 3 requests and responses.
- `psql-01` … `psql-05`: the read-only queries, echoed with `-e`.
- `backend-log-obs-window.txt`, `backend-log-warn-error.txt`, `frontend-log-warn-error.txt`: the logs.
- `s2-*` and `s4-*`: screenshots.
