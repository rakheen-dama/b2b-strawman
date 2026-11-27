# 667B OBS-before — trust Reconciliation, the wizard frame, Investments, Trust Reports, the monthly list, Other creditors (task 667.5)

## Run and environment

**When (2026-11-27):** seed run 1 ~04:56Z, seed run 2 (backdated deposit) 05:08Z, seed run 3 (no-op check) ~05:10Z; final capture + assemble ~05:25Z. The capture was run four times while the probe was refined (page-root/header detection, empty-state text, step-indicator overflow, report header text); only the last run's output is committed. The data did not change between capture runs except for the run-2 deposit, which every committed number includes.

**Stack:** the running e2e mock-auth stack, org `e2e-test-org`. It was not rebuilt, restarted, reseeded or torn down.
- Ports: frontend :3001, backend :8081, mock IDP :8090.
- All `e2e-*` containers created 2026-11-27T04:41:53Z (a fresh exit-month seed ran at ~04:42–04:44Z).
- Frontend image `docteams-e2e-frontend`, `sha256:702f1dde0f12`, built **2026-11-27T04:25:09Z** from `worktree-epic-667A/compose` (compose labels). That was 667A's after-fix rebuild: its head `c8cbb4d53` was committed 04:22:23Z. `git diff --stat c8cbb4d53 427d6826c -- frontend backend compose packages` is **empty**, so the frontend matches main **`427d6826c`**, this worktree's HEAD.
- Backend image `sha256:e7fd89e47741`, built 2026-11-25T16:31:26Z. The only later backend commit on main (`d8aaba16a`, 17:41Z, take-on file reader) touches no trust code.

**Browser:** the Playwright library (Chromium, from the main checkout's `frontend/node_modules`), not the MCP.
- Node 20.11.1 (nvm). DPR 1, reduced motion, timezone Africa/Johannesburg.
- Rail expanded (`sidebar_state=true`). Theme via localStorage `kazi-theme`.
- Both personas log in through `/mock-login` (`#user-select`, then "Sign In").

**Writes:** no SQL. All writes are the REST calls the UI makes, listed under "Seed".
- Raw tiles: `/tmp/667B-before-raw/`, not committed.
- Committed: 13 contact sheets + 5 crops (18 PNGs).

**Scripts:** see `scripts/README`. OBS-after (no re-seed), from `before/scripts/`:
`PHASE=after node 01-capture.cjs && PHASE=after STACK="<image + SHA>" node 02-assemble.cjs`

## Stack data before the seed (`seed.json` › `dataBefore`)

- Primary account "Mathebula & Partners Trust Account" `789f111c-c0f5-4597-a105-4b5f0099e81d`. Locked through 2026-10-31, single approval. Two s86(4) investment accounts: `28691bf6-…` (Ndlovu) and `803d9a68-…` (Khumalo).
- **1** reconciliation: 31 Oct 2026, COMPLETED, balanced, `bdf7e8b2-1625-4580-810f-cc7544800286`, with monthly list **`a47359b9-53f9-4734-b87c-35ccc181daf3`**.
- **1** bank statement (`month-end-trust-account.csv`, RECONCILED). There were **no open statements**.
- **2** investments, both CLIENT_INSTRUCTION (s86(4)):
  - Khumalo `1ce07004-3844-4bf6-8ade-9330905a4c54`: ACTIVE, R 20 095.
  - Ndlovu `fc9d399c-7ae3-4ad1-b5a4-59952ef717cb`: WITHDRAWN, R 0.
  - There are **no** FIRM_DISCRETION (s86(3)) investments.
- 24 transactions. Ledgers: Ndlovu R 69 380, Khumalo R 31 000 (+ R 20 095 invested).
- The monthly list's frozen `integrityFlags` is `[]`, and `adjustmentsSinceLock.entries` was `[]`.

## Seed (`scripts/00-seed.cjs`; ids in `seed.json`)

1. **Open statement** (the wizard's step-2 upload: `POST /api/trust-accounts/{primary}/bank-statements`, multipart):
   - File `667B-open-statement-2026-11.csv`, two lines (2 Nov +R 250 "ACB CREDIT 667B OBS", 3 Nov −R 115 fee). Nothing in the books matches them.
   - Result **`eef9ac0e-4f2e-4b0c-b54a-9538eec9dfa7`**, status IMPORTED.
2. **22 DRAFT reconciliations** (the wizard's "Review Matches": `POST …/reconciliations {periodEnd, bankStatementId}`, then `POST /api/trust-reconciliations/{id}/calculate`):
   - Period ends 5 → 26 Nov 2026, all against `eef9ac0e`. The period end is the firm's choice (TrustReconciliationService createReconciliation, §16.3.4). The statement fits every one of them: its last line is 3 Nov, and no other statement has a line after that.
   - Each draft: bank R 103 265, cashbook R 101 630, ledgers R 100 380, isBalanced false.
   - Ids are in `seed.json` › `ids.draftIds`.
   - This was the only way to reach 2 pages without completing (and so locking) more months.
3. **Backdated deposit** (the RecordDepositDialog call):
   - "667B-AFTER-LOCK", R 500, Khumalo, dated 28 Oct 2026 → **`c1112e06-e11c-4bfd-8488-3ffaefec5a4f`**, RECORDED.
   - Entries backdated into a locked month are "recorded and shown against the list, never refused" (`TrustIntegrityService` BACKDATED_AFTER_LOCK). This one puts the only row into the monthly list's "Entered after this list was locked" table.
   - Side effect: trust balance R 102 130 on the landing (was R 101 630), and Khumalo is R 31 500.

**After the seed:**
- 23 reconciliations (22 DRAFT + 1 COMPLETED), giving 2 pages.
- 1 open statement.
- The monthly list has 1 after-lock entry.
- Nothing was discarded, completed or auto-matched.

## Page sizes and paging (file:line at `427d6826c`)

**Reconciliation list:**
- `size: 20` (`reconciliation/page.tsx:80`). `?page=` is zero-based and must match `^\d+$` (`:77`).
- `fetchReconciliations` (`reconciliation/actions.ts:45-65`) slices a **plain array**, because the endpoint has no Spring `Page` (G89-F17).
- The order is the endpoint's: period end descending. Page 1 = 26 Nov … 7 Nov (20 drafts). Page 2 = 6 Nov, 5 Nov, **31 Oct COMPLETED** (the "List locked" link is on page 2).
- `pageUrl` (`:112-117`) writes only `page`; no other params exist today.
- The pager renders only when `totalPages > 1` (`:280`). Page 1 shows "Page 1 of 2", with Previous as a disabled `<button>` and Next as `<a href=…?page=1>`.
- **Page-2 deep link** `/trust-accounting/reconciliation?page=1` gives "Page 2 of 2" and 3 rows. Previous → `?page=0` (explicit), Next is a disabled button.
- `?page=9` gives "23 reconciliations found" with **no table and no pager**, and the copy "No reconciliations yet. Start a new reconciliation to begin." (F-667B-7).

**Open statements:**
- Unpaged. Every non-RECONCILED statement of every trust account is listed (`:91-106`).

**Investments:**
- **No pager.** `fetchInvestments` reads page 0, `size=100`, `sort=depositDate,desc` (`investments/actions.ts:28,39`).
- `?investmentBasis=FIRM_DISCRETION|CLIENT_INSTRUCTION` is the only query param (`investments/page.tsx:84-88`). The `InvestmentBasisFilter` select does `router.replace` and keeps any other params (`InvestmentBasisFilter.tsx:21-29`).
- Observed transitions:
  - All → Firm Discretion: `?investmentBasis=FIRM_DISCRETION`.
  - → Client Instruction: `?investmentBasis=CLIENT_INSTRUCTION`.
  - → All: no query.

**Trust Reports:** unpaged; 9 cards.

**Monthly list:** unpaged; client tables.

## 1. Reconciliation list (`sheet-reconciliation-*.png`, `crop-recon-p2-1024-light.png`, `pages.recon*`)

**Layout:**
- h1 "Reconciliation" (raw `font-display text-3xl`, 30 px) and the subtitle "Trust account bank reconciliation history".
- Alice also gets the `new-reconciliation-btn` link "New Reconciliation", which goes to `/reconciliation/new` (the h1 there is "New Reconciliation").
- Page root `space-y-8`, with three blocks at y 96 / 188 / 1375 (1440 light).

**Reconciliation History card:**
- The card description reads "23 reconciliations found".
- Columns: Period · Bank Balance · Cashbook Balance · Client Ledger Total · Status · Completed · Monthly list.
- Rows `reconciliation-row-<id>` (20 / 3).

**Status chips** (`Badge`, data-slot=badge):

| Chip | Variant | Light | Dark |
|---|---|---|---|
| Unbalanced | warning | #bb4d00 on #fef3c6, **4.52** | 6.26 |
| Balanced | success | #008236 on #dbfce7, **4.50** | 6.46 |
| List locked | neutral | 6.76 | 5.84 |

- "Unbalanced" is shown on every DRAFT, and the Completed column shows "---" (F-667B-8).
- The completed row's "List locked" chip sits inside a `<Link data-testid="list-locked-<reconId>">` pointing to `/reconciliation/lists/a47359b9-…`. Clicking it lands on h1 "Trust creditors list — 31 Oct 2026".

**Widths:**

| | 1024 light | 1024 dark | 1440 light | 1440 dark |
|---|---|---|---|---|
| page 1 table / scroller | **678 / 662 (+16)** | 678 / 670 | 1 078 / 1 078 | 1 086 / 1 086 |
| page 2 table / scroller | **729 / 662 (+67)** | 729 / 670 | 1 078 / 1 078 | 1 086 / 1 086 |
| Period column | 59 px (3 lines, rows 85 px) | | 140 px (rows 47 px) | |

- At 1024 on page 2, the Monthly list cell spans x 624–728 in a 662 px scroller, so the "List locked" link is out of view (F-667B-1).
- Document h-overflow is 0 everywhere.

**Statements Not Yet Reconciled** (`open-statements` card):
- Table `open-statements-table`, row `open-statement-row-<id>` (today's name; 667.6 renames it `statement-row-<id>`).
- Columns: Trust Account · File · Period · Lines · Matched · (sr-only Actions).
- Row: "Mathebula & Partners Trust Account · 667B-open-statement-2026-11.csv · 02 Nov 2026 – 03 Nov 2026 · 2 · 0".
- Alice sees a ghost "Discard" button (`discard-statement-<id>`). Carol gets an empty 0 px Actions column.
- 662 / 662 at 1024, so it fits.

**Discard dialog** (`sheet-reconciliation-variants-alice.png`, Alice 1440 light + 1024 dark): opened, read, **Cancelled**.
- Text: "Discard statement? 667B-open-statement-2026-11.csv is retired: it is no longer used for reconciliation and its lines stop counting as unresolved. Matched lines are released. The statement and its lines are kept for the audit trail."
- Reason field `discard-reason-input`; buttons Cancel · "Discard statement" (`discard-statement-confirm`) · Close.
- Dialog box 512 × 310. The row is still listed after Cancel.
- Note: the backend would refuse this discard because 22 drafts use the statement (`TrustReconciliationService.discardBankStatement` doc). It was not confirmed.

## 2. `reconciliation/new` — wizard step 1 only (`sheet-recon-new-*.png`, `crop-k76-*.png`)

**Frame, top to bottom** (`new/page.tsx:664-720`):
- A `button` "← Back to Reconciliation" (y 96, 20 px; a `router.push` button, not a link).
- The header div with h1 "New Reconciliation" (30 px) and subtitle "Reconcile your trust account with a bank statement", at **y 148**. The other pages' h1 sits at y 96.
- `nav[aria-label="Wizard steps"] > ol[data-testid=step-indicator]` at y 240.
- `step-select` card at y 304.
- Root `space-y-8`.

**Step 1 controls:**
- `account-select` (native) with 3 options: the primary account + both s86(4) investment accounts.
- `period-end-input` with max = 2026-11-27.
- `step-next-btn` "Next", disabled.
- `reconciliation-split-pane` is 0 (step 4 only).

**Step badges** (K-76):
- Active "1": #ffffff on #00a38f = **3.16:1**, light and dark.
- Pending (light): #6b7482 on #eff1f5 = **4.18**. Pending (dark): 5.84.
- At 1024 the indicator is **726 px in a 712 px row**: it overflows the content edge by **14 px** (light) and 6 px (dark), and "Complete" is clipped. Badges are squeezed to **21–24 × 32 px** ovals, labels wrap to 2 lines and the row is 40 px tall (F-667B-6).
- At 1440 everything fits: 32 × 32 badges, with 213 px to spare.

**Carol** sees the same full wizard: account select with 3 options, the period-end input and the Next button (K-95a). Every write needs MANAGE_TRUST, which she doesn't have.

## 3. Investments (`sheet-investments-*.png`, `crop-k91-investments-1024-light.png`)

**Layout:**
- h1 "Investments" (30 px) and the subtitle "Trust investment register and maturity tracking".
- Alice also gets the "Place Investment" button in the header (MANAGE_TRUST).

**"Investment Register" card:**
- "2 investments found", with the `investment-basis-filter` select (All · Firm Discretion · Client Instruction) in the card header.
- Columns (exact): Client · Investment account · Rate · Term · Principal · Balance · Status · **Actions** (Alice only; Carol has 7 columns).
- Rows `investment-row-<id>`; `maturity-alert` count 0.

**Row contents:**
- **Khumalo:** "Section 86(4)" lead chip (#00786f on #cbfbf1, 4.75), "7.50%", "LPFF share **5% (statutory)**" (`text-teal-600` #00a38f, **3.13**), Call deposit, Principal R 20 000 with mono sub-lines (Gross interest / Fund 5% / Net interest), Balance R 20 095, "Active" chip. Alice's Actions: **Record interest · Interest entries · Withdraw**.
- **Ndlovu:** Withdrawn chip, empty Actions cell.

**K-91 widths:**

| | 1024 light | 1024 dark | 1440 |
|---|---|---|---|
| Alice table / scroller | **761 / 662 (+99)** | 761 / 670 (+91) | 1 078 / 1 078 |
| Alice Actions column | x 634, 127 px, **99 px out of view** | 91 px out | fits |
| Carol table / scroller | 662 / 662 (fits) | 670 / 670 | fits |

- Rows are 157 px at 1024. The mono Principal sub-lines wrap ("Gross interest" / "R 100,00" on two lines).

**Empty register:**
- `?investmentBasis=FIRM_DISCRETION` → "0 investments found", with `empty-investments` (a `div.py-8.text-center`, 1 078 × 84 at 1440, no icon, no action) reading "No investments yet. Place one to get started." Same for Carol (F-667B-9).
- This is the only real empty register on this stack. There is no s86(3) investment, and placing one would remove the empty state, so the **K-91 86(3) fund-note part is not observable** (`investment-interest-fund-note` count 0).

## 4. Trust Reports (`sheet-reports-*.png`, `crop-k36-report-card-1440-light.png`)

**Layout:** h1 "Trust Reports" (30 px) and the subtitle "Generate and download trust accounting reports". There is no take-on reports card (no take-on on this stack).

**Alice — 9 `report-card-<slug>` cards**, in this order: trust-receipts-payments, client-trust-balances, trust-position, client-ledger-statement, trust-reconciliation, investment-register, trust-interest-charges-schedule, unidentified-deposits-age-analysis, section-35-data-pack.
- Grid: 3 columns. Cards are 365 × 182/218 at 1440 and 227 × 222–278 at 1024.
- Each card is a `<Link>` to `/org/e2e-test-org/reports/<slug>`.
- **"Run Report →"** is `text-teal-600`: #00a38f on #fdfeff = **3.13:1** on all 9 cards (light). Dark is 9.63.

**Click-through:**
- section-35 card → `/reports/section-35-data-pack`, h1 "Section 35 Data Pack".
- investment-register card → h1 "Investment Register". Running it for the primary account gives columnheaders **Client · Institution · Account # · Investment Account · Basis · Principal · Balance · Gross Interest · Fund 5% · Net Interest · Status** (2 rows), and **"LPFF Rate" = 0**.

**Carol** (no FINANCIAL_VISIBILITY): no cards (0 `report-card-*`). Instead she gets `trust-reports-financial-visibility-required`: "Trust reports need financial visibility. Ask an owner or admin to grant it."

## 5. Monthly list `reconciliation/lists/a47359b9-…` (`sheet-monthly-list-*.png`)

**Already on `PageHeader`:**
- h1 "Trust creditors list — 31 Oct 2026" (24 px).
- Description "Mathebula & Partners Trust Account · Locked 27 Nov 2026, 06:43 by Alice Owner · Rule 54.15".
- Action: outline link "← Reconciliation" → `/trust-accounting/reconciliation`.

**Stat cards (4):** Adjusted bank R 101 630 · Cashbook R 101 630 · Trust creditors R 101 630 · Difference R 0. They show 2×2 at 1024 and 4 across at 1440.

**`monthly-list-creditors`** (raw table, `:104`):
- Columns Trust creditor · Kind · Balance.
- 6 body rows:
  - Khumalo Holdings / Client R 31 000, and its matter row.
  - Ndlovu / Client R 69 380, and its matter row (`monthly-list-matter` ×2, `pl-6` indent).
  - LPFF / Fund R 0.
  - "ACB CREDIT UNKNOWN · received 22 Oct 2026" / Unidentified deposit R 1 250.
- tfoot "Total trust creditors R 101 630,00".
- Fits at both widths (662 / 662, 1 078 / 1 078).

**Flags at completion** (`monthly-list-flags`, `:141-167`, a `<ul>` with severity chips, **not** a table): **not rendered** on this data, because the list was locked with `integrityFlags: []`. The flags are frozen at completion, so producing one would need another completed reconciliation; not attempted.

**"Entered after this list was locked"** (`monthly-list-after-lock`; the raw table at **`:185`**, which is what 667.7's "(:185)" points at):
- Columns Date · Entered · Type · Client · Reference · Cashbook.
- 1 row: "28 Oct 2026 · 27 Nov 2026, 07:08 · Deposit · Khumalo Holdings · 667B-AFTER-LOCK · R 500,00".
- Then the sentence "Trust creditors as at 31 Oct 2026 are now R 102 130,00, a change of R 500,00 from the locked list."
- The table has no testid; its rows are `after-lock-entry-<txId>`.

Carol's view is identical.

## 6. Other trust creditors (as migrated in 664B; `sheet-other-creditors.png`)

**Layout:** PageHeader h1 "Other trust creditors" (24 px). Sections `fund-ledger-section` (`fund-ledger-table`, 5 rows), `interest-memos-section` (`interest-memos-empty` "No memos for 2026…") and `unidentified-deposits-section` (2 rows).

**Widths:** `fund-ledger-table` is **777 / 662 (+115) at 1024**, with Running Balance out of view (F-667B-10). It fits at 1440. Carol's view is the same.

## 7. Shell (every capture)

- `main#main-content` = 1, `<main>` = 1, h1 in main = 1, h1 page-wide = 1, document h-overflow 0.
- `#main-content` inner width: 760 / 1 176 light, 768 / 1 184 dark. Cards: 712 / 1 128 light, 720 / 1 136 dark.
- h1 is 30 px on the four raw-header pages and 24 px on the PageHeader pages (monthly list, other creditors, landing).
- Title is the generic "Kazi — Practice management, built for Africa" everywhere (F-667A-8 class).
- No "Interest" link anywhere in the document (0, including the collapsed Trust group; `nav-items` has none). "LPFF Rate" header count is 0 on every captured page and in the Investment Register run.

## 8. Console (`console.json`)

60 capture page loads plus the interactions: **0 errors, 0 warnings, 0 page errors, 0 hydration.**

## 9. SEL-COMPAT before (`sel-compat.json`, 1440 light unless noted)

| Selector | Alice | Carol |
|---|---|---|
| h1 "Reconciliation" / "New Reconciliation" / "Investments" / "Trust Reports" | 1 / 1 / 1 / 1 | 1 / 1 / 1 / 1 |
| `new-reconciliation-btn` | 1 | 0 |
| `reconciliations-table` / `reconciliation-row-<id>` | 1 / 20 (page 2: 3) | same |
| `list-locked-<id>` | page 2: 1 | page 2: 1 |
| `open-statements` / `open-statements-table` / `open-statement-row-*` | 1 / 1 / 1 | 1 / 1 / 1 |
| **`statement-row-*`** | **0** (the name 667.6 introduces) | 0 |
| `discard-statement-<id>` | 1 | 0 |
| `step-indicator` / `step-select` / `account-select` / `period-end-input` / `step-next-btn` | 1/1/1/1/1 | 1/1/1/1/1 |
| `reconciliation-split-pane` | 0 (step 4 only) | 0 |
| `investments-page` / `investments-table` / `investment-row-*` | 1 / 1 / 2 | 1 / 1 / 2 |
| `empty-investments` (FIRM_DISCRETION) | 1 | 1 |
| `investment-basis-filter` / button "Place Investment" | 1 / 1 | 1 / 0 |
| investments columnheaders | Client · Investment account · Rate · Term · Principal · Balance · Status · Actions | same without Actions |
| `trust-reports-page` / `report-card-*` | 1 / **9** | 1 / 0 (+ `trust-reports-financial-visibility-required` 1) |
| text "Section 35" / "Investment Register" (leaf elements) | 2 / 1 on reports (also 1 "Investment Register" card title on investments) | 0 / 0 on reports |
| `monthly-list-page` / `monthly-list-creditors` | 1 / 1 | 1 / 1 |
| `monthly-list-matter` / `-flags` / `-after-lock` / `-brought-forward` | 2 / 0 / 1 / 0 | same |
| `other-creditors-page` / `fund-ledger-section` / `fund-ledger-table` | 1 / 1 / 1 | same |
| `a` "Interest" (exact, whole document) / "LPFF Rate" | 0 / 0 | 0 / 0 |

Every testid per page is in `discover.json`.

## What OBS-after must replay

**Data:**
- Run `01-capture.cjs` + `02-assemble.cjs` with `PHASE=after`, unedited, on this same data. **Do not re-seed** (that would only append a no-op run).
- The ids come from `seed.json`.

**Must stay identical:**
- The 60 page loads (same URLs, including `?page=1`, `?page=9`, `?investmentBasis=…`).
- The interactions:
  - pager Next → `?page=1`, Previous → `?page=0`.
  - "List locked" → monthly list.
  - New Reconciliation → `/new`.
  - wizard step-1 controls.
  - monthly-list back link.
  - basis-filter transitions.
  - Discard dialog opened and cancelled.
  - Section 35 click-through.
  - Investment Register run headers.
- The SEL-COMPAT counts: renames are allowed only where the plan says so (`open-statement-row-` → `statement-row-`).

**Should change:**
- K-36 (3.13 → ≥ 4.5).
- K-76 active badge (3.16 → ≥ 4.5).
- K-91 Actions in view at 1024.
- The h1 frame (PageHeader) on the four raw pages.
- F-667B-1 at 1024.
