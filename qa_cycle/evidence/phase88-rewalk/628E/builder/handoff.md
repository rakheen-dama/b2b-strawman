# 628E B1 → B2 handoff (Days 0–14 strict, rule, helpers)

Branch `phase88/11E-strict-playwright`, worktree `/Users/rakheendama/Projects/2026/worktree-epic-628E`.
Evidence: fresh stack → `/tmp/628E/b1/lifecycle-fresh-1.log` (75 passed / 4 failed / 3 skipped / 18 did-not-run;
all 4 fails are Days 30–90 old code), then `/tmp/628E/b1/depth-ii-fresh-1.log` (39 / 0 / 4).

## State after Day 14 (fresh stack)
- Trust account "Mathebula & Partners Trust Account" (primary GENERAL, Standard Bank, 051001 / 0123456789).
- LPFF rate 6.50% (share 50%), **Effective From = today − 365 days** → any Day 60 period start in the last year works.
- Billing rates Alice 2 500 / Bob 1 200 / Carol 550; cost rates 1 000 / 500 / 200 — **effective from today − 365**
  (so back-dated time entries are rated).
- VAT: seeded "VAT — Standard" 15.00% default (nothing created).
- Clients (all with address 12 Rivonia Road / Sandton / 2196 / ZA):
  - Sipho Ndlovu <run> — Individual, ID 8501015800087 (tax no. auto-filled), **ACTIVE** (FICA 9/9 done Day 1).
  - Apex Holdings <run> — Company, tax 9012345678, **ONBOARDING**.
  - Moroka Family Trust <run> — Trust, tax 9123456789, **ONBOARDING**.
  - QuickCollect Services <run> — Company, tax 9234567890, **ONBOARDING**.
- Template matters, client-linked (Alice is lead on all; Carol is on Sipho's team):
  - `siphoMatter()` "Ndlovu v RAF <run>" — Litigation (Personal Injury / General). Header fields case 2026/12345,
    court "Gauteng Division, Pretoria", opposing "Road Accident Fund".
  - `apexMatter()` "Apex Shareholder Agreement <run>" — Commercial (Corporate & Contract). **No time logged** —
    A3's unbilled-item precondition is yours (time on "Due diligence review" or an approved disbursement).
  - `morokaMatter()` "Estate Late Peter Moroka <run>" — Deceased Estate Administration.
  - `qcMatter()` "QC Debt Recovery #1 <run>" — Collections (Debt Recovery). **No time logged** (QC fee note precondition is yours).
- Unbilled billable time (all dated today):
  - Sipho: Carol 1h30 "Initial consultation & case assessment" (R550), Bob 2h "Letter of demand" (R1 200),
    Carol 0h45 "Letter of demand" (R550).
  - Moroka: Alice 1h "Report estate to Master of High Court" (R2 500).
- Court date: Sipho, MOTION, today+30, court "Gauteng Division, Pretoria", description "Motion — Ndlovu v RAF", Scheduled.
- Trust deposits RECORDED: Moroka R250 000 (matter Moroka, ref `DEP/MOROKA/<run>`), QuickCollect R45 000
  (matter QC, ref `DEP/QC/<run>`). Moroka ledger balance 250 000,00.
- Adverse party "Road Accident Fund" (Government), **not linked to any matter**.
- A task comment (internal) on Sipho "Letter of demand".

## Run-state API — `frontend/e2e/helpers/legal-lifecycle.ts`
File `$TMPDIR/kazi-legal-lifecycle-run.json`, started by Day 0 test 1 (`startLifecycleRun()`).
- `currentLifecycleRun()`; name builders `siphoClient/apexClient/morokaClient/qcClient()`,
  `siphoMatter/apexMatter/morokaMatter/qcMatter()`, `lifecycleClientName(key)`.
- `clientId("sipho"|"apex"|"moroka"|"qc")` — UUIDs recorded at creation (use for the raw-UUID "Client ID" fields
  in Record Fee Transfer / Place Investment). `matterId("sipho"|"apex"|"moroka"|"qc")`.
- `recordMatterId(key, id)` (key is open-ended, e.g. "sipho2"), `recordValue(key, value)` / `recordedValue(key)`
  for fee-note UUIDs etc. Recorded values: `morokaDepositRef`, `qcDepositRef`.
- Helpers: `idFromUrl(page, "invoices")`, `randPattern("250 000,00")` (NBSP-tolerant regex),
  `createTemplateMatter(page, {template, name, client})` → id, `logTimeOnTask(page, {task, hours, minutes, date,
  description, rate?})` (expects the matter page open; `rate` asserts the preview), `runConflictCheck(page, name)` →
  result locator (verdict = `result.getByRole("heading", { level: 3 })`), `ensureTrustAccount`, `TRUST_ACCOUNT`,
  `openTrustTransactions`, `trustTransactionRow(page, ref)`, `recordTrustDeposit(page, {...})`.
- `openGroupedTab` now lives in `helpers/grouped-tabs.ts` (depth-ii re-exports it).

## Gotchas hit
- **Single-tab groups** (matter "Diary" = `schedule`) have no sub-tab menu: `openGroupedTab(page,"schedule","court-dates")`
  throws. Click `getByTestId("tab-group-schedule")` and assert `aria-selected="true"` (see Day 7 court-date test).
- **PRODUCT BUG (628E-BLOCKER)**: a conflict check that matches an adverse party with no matter link crashes the page
  (`components/legal/conflict-check-result.tsx:141` `match.relationship.replace` on `null`; backend sends
  `relationship: null` from `ConflictCheckService.buildAdversePartyConflict(ap, null, …)`). Day 14 conflict test is
  `test.fixme` with this tag. **Day 75 conflict searches that hit "Road Accident Fund"/Mokoena/Pillay (unlinked) will crash
  the same way** — likely also Day 75 `:164` ("adverse" search). Customer/matter-name matches were not observed crashing
  (not tested).
- `getByTestId("home-page")` matches 2 nodes (one hidden outside `<main>`): scope to `#main-content`.
- Settings custom-field rows: the name cell also contains the description → `getByRole("cell", { name: /^Case Number/ })`.
- Node `toLocaleString("en-ZA")` gives "2,500" (not the browser's NBSP form) — build amounts by hand / use `randPattern`.
- Carol (Member) gets 403 on `/time-summary/by-member` (Fees → Time) — use Bob/Alice for that tab.
- The conflict checks in Days 1–3 expect "No Conflict" → **fresh stack only**. Days 0 and 7–14 tolerate a reused stack
  within limits (a re-run of Day 7–14 in the same run duplicates the motion row/deposit refs → their count-1 asserts fail).
- After the lifecycle has a trust account, Day 60 `:41` (old soft code) now **fails** instead of runtime-skipping
  (`day-60-interest.spec.ts:89` soft `toBeTruthy`). Baseline had it skipped.

## Accessible names confirmed (by passing runs)
- Rates: row `billing-rate-{owner|admin|member}` / `cost-rate-…`; buttons `Add rate for <Name>` / `Edit billing rate for <Name>`;
  dialog "Add Rate", labels "Hourly Rate"/"Hourly Cost"/"Effective From", button "Create Rate"; tab "Cost Rates".
- Brand: label "Brand Color", button "Save Settings", text "Settings saved successfully.".
- Interest: h1 "Interest", `lpff-rates-section` → button "Add Rate" → `lpff-rate-dialog` labels "Effective From", "Rate %",
  "LPFF Share %", "Notes (optional)", submit "Add Rate"; table `lpff-rates-table` row "6.50%".
- Nav: `navigation "Main navigation"`; zone buttons "Fees", "Trust"; links "Tariffs", "Overview", "Transactions",
  "Client ledgers", "Interest".
- Template dialog: "New from Template" → option `/^Litigation \(Personal Injury \/ General\)/` etc. → "Next" →
  label /Matter name/, native select /^Client/ → "Create Matter".
- Tasks: row button `Open task detail for <title>`, row button "Log Time"; dialog "Log Time" `#time-*`, `rate-preview`
  "Billing rate: R 550,00/hr"; task sheet dialog "Task Detail" → tab "Comments" → label "Add a comment", "Visibility"
  (value INTERNAL) → "Post Comment".
- Diary: button "New court date" → dialog "Schedule Court Date": labels Matter (native), Type (native, value MOTION), Date,
  Court Name, /^Description/ → "Schedule Court Date". Matter Diary tab list `court-date-list` row contains "Motion", "Scheduled".
- Deposit: `record-transaction-button` → menuitem "Record Deposit" → dialog "Record Deposit", `trust-deposit-customer-trigger`,
  `trust-deposit-matter-trigger`, labels Amount / Reference / /Description/ → "Record Deposit"; row text "RECORDED".
- Ledgers: `client-ledgers-page`, `client-search` + button "Search", row `ledger-row-<clientId>` with client link,
  detail `client-summary` "Trust Balance".
- Adverse party: `adverse-party-registry`, `create-adverse-party-trigger`, `adverse-party-dialog` heading "Add Adverse Party",
  labels /^Name/, /^Party Type/ (native, GOVERNMENT) → "Create Party"; `adverse-party-table`.
- My Work: h1 "My Work"; today's entries render "<matter> — <description>".

## Rule scope for B2
`frontend/eslint.config.mjs` scopes `kazi-frontend-local/no-isvisible-catch` to day-00, day-01-03, day-07-14, matter-team,
`legal-depth-ii/**`, `helpers/**`, `fixtures/**`. Widen to `e2e/tests/legal-lifecycle/**/*.ts` and flip the
`day-30-billing.spec.ts` case in `__tests__/eslint-rules/no-isvisible-catch.test.ts` (it.each "is not (yet) enforced").
