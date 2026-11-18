# 648B observed gates: PR #1801 @ c952121f6 (2026-11-18, UTC)

Every command ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-648B` at HEAD
`c952121f61b7fed1e4211b401b65dc95c66c6ef4` (Phase 92 slice 648B: section 86(4) trust-investment interest entries).
The format follows `648E/gates-6b4615074/README.md`.

**Tree state.**
- Before the run, the tree held only the untracked `.epic-rulings.md` (`tree-before.txt`). It was recorded just after `mkdir`, while this directory was still empty, so git did not list it yet.
- After the run it held `.epic-rulings.md` plus this untracked directory (`tree-after.txt`).
- `git diff --stat` is empty. HEAD is unchanged (`head-before.txt`). Nothing was committed or pushed.

| Gate | Result |
|---|---|
| E2E-LEGAL lifecycle | **PASS**: **109 passed / 0 failed / 0 skipped**, rc 0, on two fresh stacks. Run 1 on stack #1: 3.1 min. Run 2 on stack #1b: 3.0 min, needed for a clean OBS state (see Gate 3). Both runs had the same 109-test list. Baseline was 107; 648B adds 2 tests, and both ran and passed in both runs: `day-60-interest.spec.ts:523` "Alice: Record section 86(4) interest — net of the Fund's 5% (bank advice, gross R100,00)" (1.3 s / 1.3 s) and `day-60-interest.spec.ts:580` "Alice: the s86(3) row offers no Record interest action — its interest is the Fund's" (386 ms / 365 ms) |
| E2E-LEGAL depth-ii | **PASS**: **40 passed / 4 skipped** (exactly the baseline skips) / 0 failed in 2.0 min, rc 0, fresh stack #2 |
| OBS a — GROSS 400,00 from a statement line | **PASS** (UI = API = SQL). Alice imported a CSV through the wizard on "Nedbank Section 86(4) Investments" and recorded it with Record from statement → "Section 86(4) investment interest" (pre-selected from the import suggestion) → QuickCollect's investment → Gross. The dialog showed gross 400 (read-only), "Fidelity Fund's 5% R 20,00" and "Client's share R 380,00". SQL after: `INV_INTEREST` 380.00 (CLIENT_INVESTMENT) and `FUND_INV_SHARE` 20.00 (NON_CLIENT), both posted and both carrying the line. The entry reads gross 400 / fund 20 / net 380 / GROSS / POSTED / line set. The line is `RECORDED` / `INVESTMENT_INTEREST` → the 380 row, with the badge "Recorded: Section 86(4) investment interest". The register sub-lines read Gross R 500,00 / Fund 5% R 25,00 / Net R 475,00 (lifecycle 100/5/95 + 400/20/380), with balance R 30 475,00 |
| OBS b — NET 95,00 (gross 100,00), plus a fresh NET bank advice | **PASS** (UI = API = SQL). The lifecycle entry showed on the register as Gross R 100,00 / Fund 5% R 5,00 / Net interest R 95,00, balance **R 30 095,00**. `GET …/interest` returned one entry: 100.00 / 5.00 / 95.00 / NET / POSTED, no line, no fund-share row. SQL had exactly one `INV_INTEREST` 95.00 row and **no `FUND_INV_SHARE`**. The client-ledger card had `investedBalance` **30095** and general balance 15000. Fresh case: Alice placed a Moroka s86(4) investment (R7 500, OBS4B-YICNA) and Bob approved it. Alice then recorded a bank advice with gross 33,33; the dialog computed Fund R 1,67 / client R 31,66 before submit. After: UI = API = SQL at 33.33 / 1.67 / 31.66 NET, one `INV_INTEREST` 31.66, no `FUND_INV_SHARE`, balance R 7 531,66, Moroka `investedBalance` 7531.66 |
| OBS c — section 86(3) offers no interest action | **PASS**. The register's FD-0987654321 row has **Withdraw only, no Record interest**. Its note reads "Interest is the Fund's — record it from the investment account's statement", and it has no interest figures. In the wizard on "Standard Bank Section 86(3) Investments", a 150,00 credit offers exactly "Interest received for the Legal Practitioners' Fidelity Fund" and "Bank charge refunded from the business account". There is no "Section 86(4) investment interest" and no investment picker. The s86(4) credit offers "Bank charge refunded…" and "Section 86(4) investment interest" |
| OBS d — no raw enum | **PASS**. There were 11 `textContent` scans (register ×3 states, wizard review ×3, Record from statement dialog, Record interest dialog ×3, s86(3) dialog). They used case-sensitive word-boundary regexes `/\bGROSS\b/ /\bNET\b/ /\bINVESTMENT_INTEREST\b/ /\bINV_INTEREST\b/ /\bFUND_INV_SHARE\b/ /\bCLIENT_INSTRUCTION\b/ /\bFIRM_DISCRETION\b/ /\bPOSTED\b/ /\bREVERSED\b/`. Result: 0 product hits. The one raw hit was **self-inflicted**: `GROSS` in the operator-typed Notes text "OBS YICNA 648B GROSS from the statement line" (the textarea's content). With that typed string removed, the re-test gives 0 hits (`obs/rescan-…-without-typed-note.txt`) |
| OBS e — clip probe 1280 / 1440 (1920 reference) | **PASS for figures and overflow**, with **F-1 (LOW) recorded**. The register table, the Record interest dialog and the Record from statement dialog each had 0 offenders and 0 truncated text, with no page or wrapper horizontal scroll. Every money figure was on one line and inside the viewport. **F-1:** the Record from statement dialog's closed investment `<select>` cuts its label, "…— Nedbank CALL-11223344" instead of "…CALL-1122334455". This happens at every width, because the dialog's width is fixed |
| OBS f — console and log | **PASS**: 0 console errors and 0 page errors. Backend error counts were 0 everywhere. The OBS window (10:06:30–10:07:30) had 4 INFO / 0 WARN / **0 ERROR**. The lifecycle windows had **140 INFO / 105 WARN / 0 ERROR** in both runs; 648E had 142 / 105 / 0, so the WARN mix is the same. Whole stacks: #1 339/151/0, #1b 336/151/0, #2 275/199/0 |

## Ground rules kept

- **No changes or Maven.** There were no source edits, commits, pushes or Maven runs. `pgrep -f surefire` was empty before every stack and every e2e run (`surefire-empty-check … 0` in `timings.txt`).
- **One e2e stack at a time.**
  - `e2e-down.sh` ran before every `e2e-up.sh` (`stack.sh`). There were three fresh stacks in sequence: #1 (lifecycle run 1 + OBS attempt 1), #1b (lifecycle run 2 + OBS) and #2 (depth-ii).
  - At the end, **0 `e2e-*` containers** are left.
- **State changed through the UI only.** Every change was made in a headless Chromium library script (`obs.cjs`, from `frontend/node_modules/@playwright/test`):
  - Alice imported the two statements in the reconciliation wizard (file input → auto-match → review).
  - Alice recorded the 400,00 line in Record from statement.
  - Alice placed the Moroka investment in Place Investment.
  - Bob approved it on Trust › Transactions.
  - Alice recorded the NET advice in the Record interest dialog.
  - There were no API writes; all `obs/api-*.json` are GETs with the context's own `mock-auth-token` against :8081.
- **Read-only evidence.** psql was used read-only (`obs-sums.sql` / `.out`).
- **Tooling.**
  - Node was nvm 20.11.1, with pnpm called by absolute path. The environment was `NODE_OPTIONS=""` and `PLAYWRIGHT_BASE_URL=http://localhost:3001`. Flags were passed without `--` (`run-suite.sh`).
  - The Playwright MCP tools and profile were not touched.
- **Long runs.** These ran as `nohup` + rc marker + bounded poll.
- **Tree restore.**
  - Each legal run left untracked PNGs in `documentation/screenshots/legal-vertical/`: 16 per lifecycle run and 10 for depth-ii.
  - They were copied to `lc1-artifacts/curated/`, `lc2-artifacts/curated/` and `depth-ii-artifacts/`, then removed with `git clean -fdq -- documentation/screenshots/legal-vertical`.
  - No tracked file changed.
  - The 648B lifecycle test's `captureScreenshot("day-60-investment-s864-interest-recorded")` is non-curated, so it writes only under `E2E_VISUAL=1` and no file exists for it.

## Stack builds and proof (`vproof.sh`)

| Stack | Window (UTC) | Flyway | Proof |
|---|---|---|---|
| #1 lifecycle run 1 + OBS attempt 1 | up 09:52:45–09:54:22, down 10:02:06 | V24 "phase92 investment interest" success = t | `stack1-proof.txt` |
| #1b lifecycle run 2 + OBS | up 10:02:06–10:02:44, down 10:08:47 | same | `stack1b-proof.txt` |
| #2 depth-ii | up 10:08:47–10:09:23, down 10:12:15 | same | `stack2-proof.txt` |

All three proofs show the same results:
- **Build log:** "Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-648B".
- **Jar classes:** the jar contains `investment/TrustInvestmentInterest.class`, `InterestPresentation.class` and `TrustInvestmentInterestRepository.class`.
- **Jar strings:**
  - `StatementLineClassification.class` and `TrustReconciliationService.class` contain `INVESTMENT_INTEREST`.
  - `TrustInvestmentService.class` contains `FUND_INV_SHARE`, `INV_INTEREST` and `interest_recorded`.
- **Migrations:** the latest tenant migration in the jar is `V24__phase92_investment_interest.sql`. Tenant `tenant_7d218705360b` flyway history: 24 "phase92 investment interest" t.
- **Schema:** `trust_investments.interest_earned` is absent (0 columns), and `trust_investment_interest` exists.
- **Frontend `.next`:** these strings are present in the investments page server chunk and static `0_mutqvo2izky.js`: "Bank advice — no statement line", "as the bank shows it", `interest-line-select`, `interest-bank-advice-note` and "Gross interest". "How did the bank credit it?" is also in the reconciliation `new` page chunk. "Section 86(4) investment interest" and "Net of the Fund" are in `lib/types/trust` chunks. `investment-interest-fund-note` is in the server chunk `_105qp0b._.js`.
- **Runtime:** health is UP and `:3001` returns 200.

## Gate 1 — E2E-LEGAL lifecycle: PASS (observed)

| Run | Stack | Window | Result | Files |
|---|---|---|---|---|
| 1 | #1 (fresh) | 09:56:16–09:59:21 | **109 passed (3.1 min)**, rc 0, `{"status":"passed","failedTests":[]}` | `lc1.log`, `lc1-summary.txt`, `lc1-trust-tests.txt` (53 lines), `lc1-last-run.json`, `lc1-artifacts/curated/` (16) |
| 2 | #1b (fresh) | 10:03:16–10:06:19 | **109 passed (3.0 min)**, rc 0, `passed` | `lc2.*`, `lc2-artifacts/curated/` (16) |

**Why run 2 exists.** It was not a flake re-run; run 1 was green. OBS attempt 1 on stack #1 left a half-done state: an imported s86(4) statement and a DRAFT reconciliation. The wizard cannot resume a draft, and re-importing would duplicate the lines (see Gate 3). Rather than build OBS on that leftover state, stack #1 was rebuilt fresh and the lifecycle re-run before OBS.

**The 648B tests** (lines 73–74 of both summaries):
- ✓ `day-60-interest.spec.ts:523:7` › "Alice: Record section 86(4) interest — net of the Fund's 5% (bank advice, gross R100,00)"
- ✓ `day-60-interest.spec.ts:580:7` › "Alice: the s86(3) row offers no Record interest action — its interest is the Fund's"

**Backend, lifecycle windows:** 140 INFO / 105 WARN / **0 ERROR** in both runs (`stack1-log-breakdown.txt`, `stack1b-log-breakdown.txt`). The WARNs are the PDFBox fallback-font lines plus the known noise: by-member 403 ×5, a draft invoice without a tax number ×2, no recipients for DOCUMENT_GENERATED, and the uri-tag meter. This is the same profile as 648E (142 / 105 / 0).

## Gate 2 — E2E-LEGAL depth-ii: PASS (observed)

**Result.** Stack #2 (fresh), 10:09:56–10:11:55. **40 passed / 4 skipped / 0 failed** (2.0 min), rc 0. `depth-ii-last-run.json` reads `passed`.

**Skips:** exactly the baseline ones: `day-30-45-soa-writeoff.spec.ts:361` and `null-prefetch-probe.spec.ts:74` ×3.

**Outputs:** `depth-ii.log`, `-summary.txt`, `-trust-tests.txt` and `depth-ii-artifacts/` (10 PNGs).

**Backend:** depth-ii window 88 INFO / 153 WARN / **0 ERROR**. Whole stack #2: 275 / 199 / **0 ERROR** (`stack2-log-breakdown.txt`).

## Gate 3 — OBS (stack #1b, after lifecycle run 2; run tag YICNA, today 2026-11-18 SAST)

**Run:** `obs.cjs`, 10:06:37–10:07:26, rc 0.
- `obs/obs-result.json` holds every figure and check; `obs-run.txt` is the summary.
- SQL is in `obs-sums.sql` / `.out`.
- Screenshots are `obs/NN-*` (full page at 1440 and 1280, plus crops) and `obs/e-*` (clip crops at 1280 / 1440 / 1920). Scanned text is in `obs/scan-*.txt`, and the CSVs as uploaded are `obs/obs648b-YICNA-*.csv`.

**Attempt 1** (`obs-attempt1-failed/`, stack #1, 10:00:16–10:00:58, rc 1) was a **script bug, not a product defect**:
- In `ReconciliationSplitPane`, the Record from statement button renders only for the selected unmatched line (`isSelected`). The script clicked `record-btn-…` without first clicking the line.
- Before failing, attempt 1 passed all 13 b0 checks, imported the statement (2 lines, both suggested `INVESTMENT_INTEREST`) and reached the review step with 0 console errors.
- The fix: click `bank-line-{id}` first (`openRecordFromStatement`).

**CSV format.** Uploads use the generic parser (`GenericCsvParser`: header skipped; Date, Description, Amount, Balance; `yyyy-MM-dd`).
- s86(4): `2026-11-18,Credit interest CALL-1122334455 OBS YICNA,400.00,30495.00` and `2026-11-18,Credit interest call account OBS YICNA second,60.00,30555.00`.
- s86(3): `2026-11-18,Credit interest FD-0987654321 OBS YICNA,150.00,200150.00`.
- The lifecycle placed both investments on day 60 with today's deposit date, so every line is dated today, which is ≥ the deposit date and ≤ today.

### b (lifecycle part, b0). QuickCollect NET 95,00 (gross 100,00): PASS (13/13 checks)

| Figure | UI (register row CALL-1122334455) | API | SQL |
|---|---|---|---|
| Entry | Gross interest R 100,00 / Fund 5% R 5,00 / Net interest R 95,00 (`01-register-b0-*`) | `GET /api/trust-investments/{id}/interest`: 1 entry, 100.00 / 5.00 / 95.00, `NET`, `POSTED`, `bankStatementLineId` null, `fundShareTransactionId` null | `trust_investment_interest`: 1 row 100.00 / 5.00 / 95.00 NET POSTED, no line, no fund txn |
| Rows | — | — | exactly one `INV_INTEREST` 95.00 (CLIENT_INVESTMENT, posted, ref `INV-INT-0E9C017B-1`), **0 `FUND_INV_SHARE`** |
| Investment balance | R 30 095,00 | register `balance` 30095, `grossInterest` 100 / `fundShare` 5 / `netInterest` 95, no `interestEarned` key | CLIENT_INVESTMENT Σ 30 095.00 |
| QuickCollect card | — | `investedBalance` **30095**, `balance` 15000 | — |

### a. GROSS 400,00 from a statement line → net 380,00 / Fund 20,00: PASS (18/18 product checks)

**Import and review.**
- In the wizard, Alice chose the Nedbank s86(4) account with period end 2026-11-18 and uploaded the CSV.
- The upload result read "Statement imported successfully Lines imported: 2 … Format: CSV". Auto-match matched 0 lines and left 2 for review.
- Both lines were stored with `suggested_classification = INVESTMENT_INTEREST`, which is the 648B import-time FUND_INTEREST → INVESTMENT_INTEREST mapping for S86_4.

**The dialog** (`05-a-record-from-statement-gross-*`, crop `05-a-…-crop.png`):
- Choices offered for this credit: "Bank charge refunded from the business account" and "Section 86(4) investment interest". The latter was pre-selected, with "Suggested from the line's description — check it before recording."
- Investment picker: "QuickCollect Services mwlye2u03095 — Nedbank CALL-1122334455" (see F-1).
- Presentation choices: "Gross — the Fund's 5% leaves in a separate debit" and "Net of the Fund's 5%".
- After choosing Gross, Gross interest = **400** (read-only), **Fidelity Fund's 5% R 20,00** and **Client's share R 380,00**.

**After Record:**
- The line badge reads "Recorded: Section 86(4) investment interest" (`06-a-wizard-after-record-1440.png`).
- **SQL:**
  - `trust_investment_interest` 55c09221…: entry_date 2026-11-18, gross **400.00**, fund **20.00**, net **380.00**, **GROSS**, **POSTED**, `bank_statement_line_id` = the 400,00 line, `fund_share_transaction_id` set.
  - Rows: `INV_INTEREST` **380.00** (CLIENT_INVESTMENT, ledger +380 / cashbook +380, posted) and `FUND_INV_SHARE` **20.00** (NON_CLIENT, +20 / +20, posted). Both are on the Nedbank s86(4) account, both carry the line, and both have reference `STMT-1-20261118`.
  - The line is `RECORDED` / `INVESTMENT_INTEREST`, with `trust_transaction_id` = the 380 row. The statement has line_count 2 and matched_count 1, because the 60,00 line stays unmatched.
- **API:** `GET …/interest` returns 2 entries, the GROSS one at 400 / 20 / 380 / POSTED with the line id and a fund-share txn id. The register API shows `grossInterest` 500 / `fundShare` 25 / `netInterest` 475 / `balance` 30475.
- **UI register** (`07-register-a-after-gross-*`): "R 30 000,00 Gross interest R 500,00 Fund 5% R 25,00 Net interest R 475,00", balance "R 30 475,00".
- **Card:** QuickCollect `investedBalance` went 30095 → **30475** (+380). General `balance` stayed at 15000. SQL CLIENT_INVESTMENT Σ is 30 475.00.

The 19th check, `noRawEnum`, was false only because of the self-inflicted typed-note hit described under d.

### b (fresh part, b1). NET bank advice 33,33 through the Record interest dialog: PASS (17/17)

**Why a fresh investment.** After the lifecycle, QuickCollect's CALL-1122334455 is the only active s86(4) investment, so a second one was created through the UI:
- Alice placed Moroka R7 500 at 7 % into "Nedbank Section 86(4) Investments" (OBS4B-YICNA, `08-b1-place-dialog-*`).
- Bob approved it on Transactions: "AWAITING APPROVAL" → "APPROVED" (`09-b3-*`).
- Before any interest, the register row showed Gross/Fund/Net R 0,00 and a Record interest action.

**Dialog on open** (`09-b1-record-interest-dialog-open-crop.png`):
- Statement line select = "Bank advice — no statement line". The other option was only "Line #2, 18 Nov 2026 — R 60,00": the consumed 400,00 line is not offered.
- Gross radio disabled, Net checked, and the bank-advice note shown.
- Date 2026-11-18, with min = max = 2026-11-18 (the deposit date is today). Shares "—".

**Line picker exercised, not submitted:**
- Picking the 60,00 line made the date read-only at 2026-11-18 and enabled Gross.
- Choosing Gross then showed gross 60 (read-only), Fund R 3,00 and client R 57,00 (`09-b1-…-line-gross-crop.png`).
- Switching back to "Bank advice" cleared the date and the gross, disabled Gross, re-checked Net and showed the note again (CR3 behaviour).

**Filled:** date 2026-11-18 and gross 33.33. The dialog showed **Fidelity Fund's 5% R 1,67 / Client's share R 31,66** (half-up 5 % of 3 333 cents = 167) before Record (`10-b1-record-interest-dialog-filled-*`).

**After:**

| | UI register (`11-register-b1-after-net-*`) | API | SQL |
|---|---|---|---|
| Entry | Gross interest R 33,33 / Fund 5% R 1,67 / Net interest R 31,66 | 1 entry 33.33 / 1.67 / 31.66 NET POSTED | 1 row 33.33 / 1.67 / 31.66 NET POSTED, entry_date 2026-11-18, no line, no fund txn |
| Rows | — | — | one `INV_INTEREST` 31.66 (CLIENT_INVESTMENT, posted, ref `INV-INT-99D9E32D-1`), **0 `FUND_INV_SHARE`** |
| Balance | R 7 531,66 | `balance` 7531.66; Moroka card `investedBalance` 7531.66, `balance` 234000 | CLIENT_INVESTMENT Σ 7 531.66 |

The 60,00 line is still `UNMATCHED` after the advice.

### c. Section 86(3) offers no interest action: PASS (9/9)

**Register** (`01-register-b0-*`, `e-register-table-*-crop.png`). The FD-0987654321 row reads "R 200 000,00 Interest is the Fund's — record it from the investment account's statement". Its Actions cell has only "Withdraw", and it has no Gross/Fund/Net sub-lines.

**Wizard.**
- Alice chose "Standard Bank Section 86(3) Investments" and uploaded the 150,00 credit. It was stored with suggestion `FUND_INTEREST`, so S86_3 is not remapped.
- Record from statement offered exactly "Interest received for the Legal Practitioners' Fidelity Fund" and "Bank charge refunded from the business account", with no investment picker (`15-c-s863-record-from-statement-*`).
- Alice pressed Cancel, and the line stayed UNMATCHED with no classification.

### d. No raw enum: PASS (0 product hits)

There were 11 scans in total; the regex list is in the gate table.

| Scan | Count |
|---|---|
| `register-b0-after-lifecycle`, `register-a-after-gross`, `register-b1-after-net` | 3 |
| `a-wizard-review`, `a-wizard-after-record`, `c-wizard-review` | 3 |
| `a-record-from-statement-dialog` | 1 |
| `b1-record-interest-dialog-open`, `-line-gross`, `-filled` | 3 |
| `c-s863-record-from-statement-dialog` | 1 |

**10 scans are clean.** `a-record-from-statement-dialog` matched `/\bGROSS\b/` only because of the Notes textarea content that **I typed** ("OBS YICNA 648B GROSS from the statement line"). Every rendered label reads "Gross — the Fund's 5% leaves in a separate debit" / "Gross interest". With the typed string removed, the scan has 0 hits (`obs/rescan-a-record-from-statement-dialog-without-typed-note.txt`).

### e. Clip probe: PASS for figures and overflow, with F-1 recorded

This used `measure.cjs` `measureIn` (from 648E/648A), plus:
- page `scrollWidth`;
- the register's `overflow-x-auto` wrapper `scrollWidth` vs `clientWidth`;
- a per-money-figure check: inside the viewport, not clipped, and the figure on one line (a Range over the money substring).

| Root | 1280 | 1440 | 1920 |
|---|---|---|---|
| `investments-table` (3 rows incl. the new sub-lines; 9 figure spans) | ok (wrapper 926/926) | ok (1086/1086) | ok (1182/1182) |
| `record-investment-interest-dialog` (filled) | ok | ok | ok |
| `record-from-statement-dialog` (GROSS filled) | ok | ok | ok |

In every case there were 0 offenders and 0 truncated text nodes, with no page horizontal scroll.

At 1280, QuickCollect's sub-line "Gross interest R 500,00" wraps to two lines: the label on one, the figure on the next. The figure itself stays intact (`e-register-table-1280-crop.png`).

**`measureIn` cannot see option text inside a closed `<select>`, so F-1 was found by eye from the crops.**

### f. Console and logs: PASS

**Browser:** `obs.cjs` had **0 console errors and 0 page errors**, across 38 POSTs (server actions).

**Backend, OBS window (10:06:30–10:07:30):** 4 INFO / 0 WARN / **0 ERROR**. The OBS attempt 1 window had 1 INFO / 0 / 0.

**Whole stacks:**

| Stack | INFO | WARN | ERROR |
|---|---|---|---|
| #1 | 339 | 151 | **0** |
| #1b | 336 | 151 | **0** |
| #2 | 275 | 199 | **0** |

**Frontend `⨯`:** stack #1 had 23 and stack #1b had 21, all "The destination stream closed early." (known, same as 648E).

## Found

**F-1 (LOW, in-slice, trust UI): the investment picker in Record from statement truncates the investment label.**

- **Repro:**
  1. Open the reconciliation wizard on a section 86(4) investment account.
  2. Import a credit line, select it and choose Record from statement → "Section 86(4) investment interest".
  3. Pick the investment.
- **Observed:** the closed native `<select>` (`interest-investment-select`, in the fixed `sm:max-w-lg` dialog) shows "QuickCollect Services mwlye2u03095 — Nedbank CALL-11223344". The option text is "QuickCollect Services mwlye2u03095 — Nedbank CALL-1122334455" (`obs-result.json` `steps.a.dialog.investmentOptions`), so the end of the deposit number — the part that tells two investments of one client apart — is cut. Width does not matter (1280 / 1440 / 1920 identical: `obs/e-a-record-from-statement-dialog-{1280,1440,1920}-crop.png`, `obs/05-a-record-from-statement-gross-crop.png`).
- **Context:** the e2e client name carries a 13-character run suffix, but a real long client name, such as a family trust, cuts the same way.
- **Code:** the label is built in `RecordFromStatementDialog.tsx` as `${clientName} — ${institution} ${accountNumber}`.
- **Status:** not fixed (gate run).

**No other defect** was observed. OBS attempt 1's failure was a script error (described under Gate 3), not a product defect.

## Timings (`timings.txt`)

| Step | UTC |
|---|---|
| stack #1 up | 09:52:45–09:54:22 |
| lifecycle run 1 | 09:56:16–09:59:21 |
| OBS attempt 1 (script bug) | 10:00:16–10:00:58 |
| stack #1 down / #1b up | 10:02:04–10:02:44 |
| lifecycle run 2 | 10:03:16–10:06:19 |
| OBS | 10:06:37–10:07:26 |
| stack #1b down / #2 up | 10:08:44–10:09:23 |
| depth-ii | 10:09:56–10:11:55 |
| stack #2 down | 10:12:13–10:12:15 |

## Cleanup

- All stacks were torn down (`stack1b-down-pre.log`, `stack2-down-pre.log`, `stack2-down.log`, rc 0). `docker ps -a` shows **0 `e2e-*` containers**.
- `git status --short` shows only `?? .epic-rulings.md` and `?? qa_cycle/evidence/phase92/648B/gates-c952121f6/`.
- `git diff --stat` is empty, and HEAD is c952121f6.
