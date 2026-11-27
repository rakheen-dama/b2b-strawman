# 667A OBS-before — trust Transactions, Client Ledgers, a client's history (task 667.1)

## Run and environment

**When**
- Seed (`00-seed`): 2026-11-27 ~03:30Z.
- Capture (`01-capture`): ~03:45Z, re-run once. The second run added the K-10 text-line probe and the "spaced only" split; nothing else changed.
- Assemble: after the capture.

**Stack:** the running e2e mock-auth stack, org `e2e-test-org`. It was not rebuilt, restarted or torn down.
- Ports: frontend :3001, backend :8081, mock IDP :8090.
- `e2e-frontend` container: created 2026-11-27T03:09:10Z.
- Frontend image: `docteams-e2e-frontend:latest`, `sha256:db413546154b`, built **2026-11-27T02:52:49Z** from `worktree-epic-666B/compose`.
- 666B's final head was `b8a2e2032`. `git diff --stat b8a2e2032 fe99599e8 -- frontend backend compose packages` is **empty**, so the frontend matches main **`fe99599e8`**, this worktree's HEAD.
- Backend image: built 2026-11-25T16:31:27Z.

**Browser:** the Playwright library (Chromium 1.59.1, from the main checkout's `frontend/node_modules`), not the MCP.
- Node 20.11.1 (nvm). DPR 1, reduced motion, timezone Africa/Johannesburg.
- Rail expanded (`sidebar_state=true`). Theme set through localStorage `kazi-theme`.
- All three personas log in through `/mock-login`: pick the user in `#user-select`, then click "Sign In".

**Writes:** no SQL. Everything is listed under "Seed" below.
- Raw tiles: `/tmp/667A-before-raw/`, not committed.
- Committed: 12 contact sheets + 2 K-10 crops.

**Scripts:** see `scripts/README`. For OBS-after (no re-seed), from `before/scripts/`:
`PHASE=after node 01-capture.cjs && PHASE=after STACK="<image + SHA>" node 02-assemble.cjs`

## Stack data before the seed

The stack had a fresh exit-month-style seed:
- **24** transactions on the primary account "Mathebula & Partners Trust Account" `f57abb48-3ee6-4496-a8f1-de5b3ddb9513` (locked through 2026-10-31).
- **2** client ledgers: Ndlovu Family Trust R 69 380 and Khumalo Holdings R 31 000, with invested R 20 095.
- 3 clients. Single approval (`requireDualApproval false`).

## Seed (`scripts/00-seed.cjs`, run once, idempotent; ids in `seed.json`)

1. **Dual approval.** `PUT /api/trust-accounts/f57abb48-…` `{requireDualApproval:true}` as Alice. The threshold stays null, so every approval is dual. There is no UI path to do this (F-667A-6). It is **left on**.
2. **Deposits** (REST as Alice, the RecordDepositDialog call, all RECORDED):
   - **22 × `667A-H-01..22`** on Khumalo Holdings `850dc8be-cac2-437f-807b-815b3cc5d95d`, matter `da6603da-…`, R 101–122, dated 2026-11-02..23.
   - **K-10 spaced** reference: 150 chars, `667A-K10 spaced EFT proceeds sale of Erf 4471 …`, tx **`cbe8fe09-f8fe-402f-a76d-4c46f463b72a`**, R 250, 25 Nov.
   - **K-10 token** reference: `667A-K10-TOKEN-` + 135 × `X`, tx **`28c4c1f5-dde9-4601-9323-40114c555fc4`**, R 260, 25 Nov.
   - `667A-ACME-DEP` on Acme Corp `2e97f9ad-4869-4993-919e-e32f249e6a7a`, R 2 000.
3. **Payments** (REST as Alice, each lands AWAITING_APPROVAL):
   - `667A-ACME-PAY` `3df04cfe-…`: Alice approved, then Bob → APPROVED. Acme's ledger is now **R 0,00**.
   - `667A-REV-PAY` `05eecafd-9ba7-48a3-9feb-56c7fa282b92`: Alice approved, then Bob → APPROVED.
   - **`667A-AWAIT-0` `6604935d-eb37-4755-906a-20689c712d2e`** (Ndlovu `d1f6f2d4-…`, R 1 500): no approval.
   - **`667A-AWAIT-1` `96d6bafb-6326-48ed-b65e-128117d816d8`** (R 2 500): see step 4.
4. **UI, Alice 1440 light:**
   - Clicked **Approve** on AWAIT-1 (`?status=AWAITING_APPROVAL`). The toast read "Approval recorded — 1 of 2 approvals. A second approver is required." The row now shows "1 of 2 approvals" and the button "**Approved by you**" (disabled, title "A different member must give the second approval"), with no reload.
   - **Reversed `667A-REV-PAY`** through Reverse, then the dialog (reason filled, date blank), then "Reverse Transaction". This created reversal **`7d084dcf-b9e8-401c-a067-eb86efd7595e`** `REV-667A-REV-PAY`, R 750, RECORDED, dated 27 Nov. The original became REVERSED with no Reverse button. "53 → 54 transactions found" updated without a reload, and there was **no toast** (F-667A-5).

**After the seed:**
- **54** transactions, **3 pages** of 20.
- Khumalo history: **34** rows, **2 pages**.
- 3 ledgers: Ndlovu 69 380 · Khumalo 33 963 (+20 095 invested) · Acme 0.

## Page sizes and paging (from the page source and observed)

- All three pages pass `size: 20` and `sort=transactionDate,desc`. `page` is zero-based.
- Transactions page 1 shows "Page 1 of 3", with Previous as a disabled `<button>` and Next as `<a href=…?page=1>`.
- **Page-2 deep link** `/transactions?page=1` gives "Page 2 of 3" and 20 rows. Prev → `?page=0`, Next → `?page=2`.
- `?page=2` gives "Page 3 of 3" and 14 rows, with Next disabled.
- `?page=9` gives no pager and 0 rows, with "54 transactions found" and "No transactions match the current filters" (F-667A-3).

## 1. Transactions — filters, status links, page drop (`measurements.json` › `interactions`, `pages.tx*`)

- **Status links:** All · Recorded · Awaiting Approval · Approved · Rejected · Reversed. The hrefs are built by `filterUrl`, which always drops `page`. On `?type=DEPOSIT&page=1`:
  - "Recorded" → `?type=DEPOSIT&status=RECORDED`.
  - "All" → `?type=DEPOSIT`.
- **Type links:** All + **25** offered types (they wrap to 5 rows at 1440). On `?type=DEPOSIT&page=1`, type "All" → `/transactions` (status kept if set).
- **TransactionFilters** (`router.push`, drops `page`). From `?type=DEPOSIT&page=1`:
  - From = 2026-11-01 → `?type=DEPOSIT&dateFrom=2026-11-01`
  - To → `?type=DEPOSIT&dateTo=2026-11-30`
  - Client ID → `?type=DEPOSIT&customerId=850dc8be-…` (500 ms debounce)
  - Matter ID, from `?page=1` → `?projectId=da6603da-…`
- Inputs are `#filter-dateFrom`/`#filter-dateTo` (160 px) and `#filter-customerId`/`#filter-projectId` (208 px, raw UUID text).
- **Pager links keep every param.**
  - `?type=DEPOSIT&page=1`: Prev → `?type=DEPOSIT&page=0`.
  - `?dateFrom=2026-11-01&dateTo=2026-11-30&customerId=…&projectId=…&page=1`: "Page 2 of 2", 26 found, Prev keeps all 4 params plus `page=0`.
  - Next from `?type=DEPOSIT` → `&page=1`, then Previous → `&page=0` (explicit).
- **Record Transaction** (`#record-transaction-button`, Alice/Bob only) opens a menu: Record Deposit · Record Payment · Record Transfer · Record Fee Transfer · Record Refund.

**Approval actions** (`?status=AWAITING_APPROVAL`, `sheet-approval-rows.png`, `sheet-transactions-bob.png`)

| Row | Alice (owner; the first approver) | Bob (admin; the second approver) | Carol (member) |
|---|---|---|---|
| AWAIT-0 (0 approvals) | Approve · Reject | Approve · Reject | no buttons, badge only |
| AWAIT-1 (Alice approved, status cell "1 of 2 approvals") | **"Approved by you" [disabled]** · Reject | **Approve (enabled)** · Reject | no buttons; "1 of 2 approvals" shown |

- Testids: `approval-actions-<id>` > `approve-button`/`reject-button`; `approval-progress-<id>` only on AWAIT-1.
- Bob was **not** made to click: the row stays 1 of 2 for the after-capture.

**Reversal**
- Alice and Bob see `reverse-button` (inside `reversal-action-<id>`) on **16 of the 20** rows on page 1. It is absent on the REVERSAL row, the REVERSED payment and the 2 awaiting rows. Carol sees 0.
- Reverse dialog on a deposit (`667A-H-22`, opened and cancelled): "A deposit reversal must be approved by another member before it takes effect." Date `min` = the row's date.
- On a payment the text is "Please provide a reason."

**K-10** (F-667A-1, `crop-k10-*.png`)

| | 1024 light | 1024 dark | 1440 light | 1440 dark |
|---|---|---|---|---|
| table / scroller clientWidth | **1 976 / 662 (+1 314)** | 1 976 / 670 | **1 976 / 1 078 (+898)** | 1 976 / 1 086 |
| Reference column | 1 311 | 1 311 | 1 311 | 1 311 |
| token text | 2 lines, widest 1 295 | | same | |
| Date column | 50 px, 3 lines | | 50 px, 3 lines | |
| spaced-only (token row hidden) | Ref 98 px, **15 lines**, row 325 px, table 764 / 662 | 764 / 670 | Ref 396 px, 3 lines, 1 078 / 1 078 | 1 086 / 1 086 |

- Column x-offsets at 1024 with the token: Date 0, Ref 50, Type 1 361, Amount 1 449, Status 1 565, Actions 1 730 (Carol's Actions column is 50 px wide, empty).
- Document h-overflow is **0** everywhere; the overflow is contained in the `overflow-x-auto` div inside the card (card 712 / 720 at 1024).

## 2. Client ledgers (`pages.cl*`, `sheet-client-ledgers-*.png`)

- Default: "3 clients found", columns Client Name · Trust Balance · Total Deposits · Total Payments · Total Fee Transfers · Total Refunds · Last Transaction.
  - Table 686 / 662 at 1024 (Last Transaction 24 px out of view); 1 078 / 1 078 at 1440.
  - Rows are `ledger-row-<clientId>` with a link to `/client-ledgers/<id>`.
- `?nonZeroOnly=true`: 2 rows (Acme R 0,00 dropped), and the pill is active.
  - The pill is a link that toggles the param and keeps `search`, dropping `page`: from `?search=a&page=1` → `?search=a&nonZeroOnly=true` → `?search=a`.
- `?search=Khumalo`: 1 row.
- The page's own **"Search"** button (`button` "Search", exact count 1) submits a GET form:
  - `/client-ledgers` + "Khumalo" → `?search=Khumalo`.
  - From `?nonZeroOnly=true&page=1` + "Ndlovu" → `?nonZeroOnly=true&search=Ndlovu` (the hidden input keeps nonZeroOnly; `page` is dropped).
- **Empty state:** `?search=zzz-667A-no-match` gives `data-testid="empty-state"` (1), "0 clients found", "No client ledgers match the current filters". It is the same for Carol.
- `?page=1` (out of range) shows the same empty state and "0 clients found" (F-667A-4). There is no pager with 3 ledgers; a page 2 of client ledgers would need ≥ 21 clients and was not seeded.

## 3. A client's history (`/client-ledgers/850dc8be-…` Khumalo; `pages.hist*`)

**Page structure, top to bottom:**
- `back-link` "Back to Client Ledgers".
- h1 "Khumalo Holdings" (Sora 30 px).
- `client-summary` grid: Trust Balance card (365 px) + Ledger totals card (747 px) at 1440.
- "Generate Statement" card (`card-title` div, not a heading): `print-statement` with From/To and a "Print Statement" button. Empty dates give "Please select both start and end dates". No PDF was generated.
- Status links, 13 type links, From/To (`ClientHistoryFilters`), then the "Transaction History" card.

**Totals breakdown** (`ledger-totals-breakdown`, all `ledger-total-*` inside `client-summary`):

| Row | Amount |
|---|---|
| Deposits | R 50 463,00 |
| +Transfers in | R 10 000,00 |
| −Payments | R 1 500,00 |
| −Fee transfers | R 0,00 |
| −Refunds | R 5 000,00 |
| −Placed in s86(4) | R 20 000,00 |
| **=General balance `ledger-total-balance` dd** | **R 33 963,00** |
| **+Invested `ledger-total-invested`** | **R 20 095,00** |
| **=Total held `ledger-total-held`** | **R 54 058,00** |

- Ndlovu (no investment) shows "=Balance R 69 380,00", with invested/held absent (0).
- Acme shows "=Balance R 0,00".

**Running Balance column**
- Page 1 rows, top down: REV-667A-REV-PAY **R 33 963,00** · K10-TOKEN R 33 213,00 · K10 spaced R 32 953,00 · 667A-REV-PAY (REVERSED) R 32 703,00 · H-22 R 33 453,00 …
- Page 2 (`?page=1`, "Page 2 of 2", 14 rows): H-06 R 31 621,00 … DEP-B-001 R 40 000,00.
- Ndlovu's awaiting rows show "—".
- **X2:** the card agrees with the newest posted running balance for all 3 clients. No register row is needed.

**Filters and pager**
- `?type=DEPOSIT&page=1`: "Page 2 of 2", 26 found.
- `?status=REVERSED`: 2 rows.
- `?dateFrom=2026-11-10&dateTo=2026-11-20`: 11 rows.
- From `?page=1`, both the From date and the "Recorded" link drop `page`.
- Next/Previous → `?page=1` / `?page=0`.

**Widths**
- History table 1 745 px with the K-10 rows at both widths: 662 / 1 078 scroller, so Running Balance is out of view.
- Page 2: 698 / 662 at 1024; fits at 1440.

## 4. Personas (`GET /api/me/capabilities`)

**Alice** (Owner, 29 capabilities, including MANAGE_TRUST and APPROVE_TRUST_PAYMENT) and **Bob** (Admin, isAdmin):
- Record Transaction, Approve/Reject and Reverse.
- Print Statement on history.

**Carol** (Member: VIEW_TRUST, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, AI_EXECUTE):
- **HTTP 200 on all three pages, read-only.** She gets the same tables, pagers, filters, ledger totals and empty state.
- **No** Record Transaction button, **no** approval or Reverse buttons. Her Actions column is an empty 50 px column.
- She still sees "Print Statement".
- On the landing page she sees a "Record Transaction" link (F-667A-7).

## 5. Shell measurements (every capture)

- `main#main-content` = 1, `<main>` = 1, h1 inside main = 1, h1 page-wide = 1. The h1s are "Transactions", "Client Ledgers" and the client's name.
- `font-display text-3xl` 30 px.
- Document h-overflow 0.
- `#main-content` inner width: 760 / 1 176 light, 768 / 1 184 dark. Cards: 712 / 1 128 light, 720 / 1 136 dark.
- Title is generic (F-667A-8).
- Every testid per page is in `discover.json`.

## 6. Console (`console.json`)

- 55 capture page loads plus the seed UI run: **0 errors, 0 warnings, 0 page errors, 0 hydration.**

## 7. SEL-COMPAT before (`sel-compat.json`; Alice / Bob / Carol)

| Selector | tx | cl | hist | landing |
|---|---|---|---|---|
| h1 "Transactions" | 1 (all) | 0 | 0 | 0 |
| `transactions-table` / `transaction-row-<id>` | 1 / 20 | 0 | 0 | **1** / 0 (the landing's Recent Transactions table carries the same testid) |
| button "Record Transaction" | Alice 1, Bob 1, Carol 0 | 0 | 0 | 0 |
| link "Record Transaction" | 0 | 0 | 0 | 1 (Alice and Carol) |
| `main tbody tr` | 20 | 3 | 20 | 10 |
| `client-ledgers-page` / `client-ledgers-table` / `ledger-row-<id>` | 0 | 1 / 1 / 3 | 0 | 0 |
| `empty-state` | 0 | 0 (1 on `?search=zzz…`, both personas) | 0 | 0 |
| `client-search` / button "Search" | 0 | 1 / 1 | 0 | 0 |
| link "Client Ledgers" (substring) | 0 | 0 | 1 ("Back to Client Ledgers" back-link) | 1 |
| `client-detail-page` / `client-summary` / `history-table` / `history-row-<id>` | 0 | 0 | 1 / 1 / 1 / 20 (td count 6, td[3] = Amount) | 0 |
| `ledger-totals-breakdown` / `ledger-total-balance` › dd | 0 | 0 | 1 / "R 33 963,00" | 0 |
| `ledger-total-held` / `ledger-total-invested` | 0 | 0 | "R 54 058,00" / "R 20 095,00" (Ndlovu: 0 / 0) | 0 |
| `print-statement-btn` / "Generate Statement" | 0 | 0 | 1 / 1 | 0 |
| nav link "Interest" (exact) / th "LPFF Rate" | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |

- The nav count is taken with the Trust group collapsed. "LPFF Rate" belongs to the reports page (667B).
- Approval testids (tx-await, 1440 light): `approval-actions-<id>` = 2 for Alice and Bob, 0 for Carol. `approve-button` / `reject-button` = 2 / 2 for Alice and Bob, 0 / 0 for Carol. `approval-progress-<id>` = 1 for all three.

## Defects

All pre-existing; details in `../found.md`:
- **F-667A-1** K-10 reproduced (MEDIUM)
- **F-667A-2** K-90 at 1024 (MEDIUM)
- F-667A-3 out-of-range page copy
- F-667A-4 client-ledgers count / out-of-range
- F-667A-5 no toast on reversal
- F-667A-6 no UI for dual approval
- F-667A-7 Carol's landing link
- F-667A-8 generic title
- F-667A-9 toast over the row actions
