# Take-on fixture set — a fictional firm's exports (Phase 93, task 660.1)

The input files of the take-on capstone `frontend/e2e/tests/legal-lifecycle/take-on.spec.ts`
(`pnpm test:e2e:take-on`, alone on a fresh e2e stack). They are what a firm moving to Kazi would
export from its previous practice-management system at the cutover date: its clients, its open
matters, its trust position at cutover and its debtors. Every name, email, ID number and account
number is fictional (`*.example.test`; the only real-looking address is the one row designed to
duplicate the seeded client Acme Corp, `contact@acme.example.com`).

Headers are the take-on catalogue labels (`TakeOnFieldCatalogue`), so the mapping step
auto-suggests every column. Dates are ISO (`yyyy-mm-dd`, chosen on the mapping step).

## Dates

The trust and debtor files carry dates **relative to the cutover date C** as tokens: `{{C}}`,
`{{C-472}}` (C minus 472 days), `{{C+10}}`. The spec renders them with
`renderFixture()` (`frontend/e2e/helpers/take-on.ts`) and keeps the rendered copy in the run's
evidence dir (`csv/`).

- **C** = the last day of the month **two** months back, by the SA date (D-224 / 660.2). Run on
  25 November 2026 → C = 30 September 2026.
- **M**, the first post-cutover month, is the month after C (October 2026 in that run): wholly in
  the past, so no statement line is in the future (FU-6a).
- The spec refuses to start within 30 minutes of SA midnight at a month end.

Clients and matters carry fixed dates (CDD, review and due dates), all in the past or well in the
future, so their outcomes do not depend on the run date.

## Files

| File | Dataset | Rows | Notes |
|---|---|---|---|
| `clients.xlsx` | Clients | 23 | **Two sheets**: sheet 1 "Old notes" is hidden (one cell); sheet 2 "Clients" has the header in row 1. Built from `xlsx-src/clients-sheet.csv` by `build-clients-xlsx.sh` (byte-for-byte reproducible; `--check` verifies the committed file). |
| `matters.csv` | Matters | 16 | One orphan; an extra column "File room" (parked as a note). |
| `trust-client-ledger-debit.csv` | Trust — client and matter balances | 3 | C-003 / TR-C1 **−500.00** (a client in debit). |
| `trust-client-ledger.csv` | Trust — client and matter balances | 3 | The corrected file: C-003 / TR-C1 49 500.00. |
| `trust-unidentified-deposits.csv` | Trust — unidentified deposits | 2 | Held, with their original dates; one over a year old. |
| `trust-investments.csv` | Trust — investments | 2 | One s86(3), one s86(4) (with instruction and endorsement); "Balance per confirmation" = the confirmation. |
| `trust-outstanding-items.csv` | Trust — items outstanding at cutover | 2 | One deposit in transit, one unpresented cheque. |
| `debtors.csv` | Debtor opening balances | 11 | Six valid, five errors; some overdue. |

The firm's trust accounts are created in the UI by the spec (not files): **G** "Take-on Trust
Banking Account" (General, **not primary**, Standard Bank 051001, no. 6570001), **I3** "Take-on
Investment 6573001" (s86(3)), **I4** "Take-on Investment 6574001" (s86(4)), each opened on
1 January two years before C (before the oldest unidentified deposit). G is not the seeded primary
because the primary opens on the stack date, after any cutover two months back (blocker B-1,
decision D-259).

## Clients (`clients.xlsx`, sheet "Clients") — expected outcome

File rows are numbered as the wizard numbers them (the header is row 1).

| Row | Previous system ID | Client | Designed as | Expected |
|---|---|---|---|---|
| 2–4 | C-001…C-003 | Amahle Zulu, Bongani Nkosi, Chantal Pillay | the trust clients | Valid |
| 5–10 | C-004…C-009 | Thandiwe Mokoena, Johan Pretorius, Karoo Freight (Pty) Ltd, Ayesha Patel, Lerato Mahlangu, Bheki Zondo | the debtors (C-009 has no address) | Valid (C-009 with an address warning) |
| 11 | C-010 | Naledi Khoza | risk `UNRATED` | Valid; FICA "Not risk-rated", no next review date |
| 12 | C-011 | Pieter Steyn | HIGH, last review 2022-03-01 (+12 months = 2023-03-01) | Valid; "Review date passed" |
| 13 | C-012 | Refilwe Dube | HIGH, `NOT_SCREENED`, CDD 2026-02-10 | Valid; "Not screened" (next review 2027-02-10) |
| 14 | C-013 | Kgomotso Moloi | MEDIUM, source of funds `OUTSTANDING`, CDD 2025-08-15 | Valid; "Source of funds outstanding" (next 2027-08-15) |
| 15 | C-014 | Highveld Logistics (Pty) Ltd | LOW, beneficial ownership `OUTSTANDING`, CDD 2025-04-01 | Valid; "Beneficial ownership outstanding" (next 2028-04-01) |
| 16–19 | C-015…C-018 | Karabo Sithole, Mandla Shabalala, Zinhle Mabuza, Mooi River Family Trust | clean | Valid |
| 20 | C-019 | T. Mokoena, `THANDIWE.MOKOENA@example.test` | repeats row 5's email (other case) | **Error** "Duplicate of row 5 in this file" |
| 21 | C-020 | Acme Corporation, `Contact@acme.example.com` | the seeded client Acme Corp | Valid, badge "Duplicate — will be skipped", "Matches a client already in Kazi"; after the commit "Duplicate — skipped" |
| 22 | C-021 | Lindiwe Mabaso | risk `VERY_HIGH` | **Error** "VERY_HIGH is not one of: Low, Medium, High, Unrated"; risk cell "—" |
| 23 | C-022 | (no name) | | **Error** "Client name is required." |
| 24 | C-023 | Sibusiso Radebe, no email | | **Error** "An email address is required to create a client in Kazi" |

- **Cards:** Rows 23 · Valid 19 · Errors 4 · Duplicates 1 · Blocks 0. Error filter: rows 20, 22,
  23, 24. Duplicate filter: row 21.
- **Attestation** (FICA-ATTEST-v1, "performed by E2E Test Organization"): "of the 18 clients to be
  created" — natural persons 15 · companies 2 · trust 1; Low 12 · Medium 3 · High 2 · Not
  risk-rated 1.
- **Commit:** "18 records will be created, and 1 duplicate skipped. This cannot be undone." →
  Created 18 · Duplicate — skipped 1 · Failed 0. "Create portal access" → "18 portal contacts
  created". No email.
- **FICA review queue** (`/clients?view=system:fica-review-due`), Unrated first, then the next
  review date ascending: Naledi Khoza → Pieter Steyn → Refilwe Dube → Kgomotso Moloi → Highveld
  Logistics (Pty) Ltd, each "Review due (attested)" with its one reason.
- **Dormancy:** "Check for Dormant Clients" → "No dormant clients detected."; all 18 ACTIVE.

## Matters (`matters.csv`) — expected outcome

| Row | Matter | Client reference | Expected |
|---|---|---|---|
| 2, 3, 4 | Transfer of Erf 101 Brooklyn (TR-A1), Estate late S Nkosi (TR-B1), Supply contract claim (TR-C1) | C-001, C-002, C-003 (previous-system ID) | Valid — the trust matters; row 4's attorney is Bob (`bob@e2e-test.local`) |
| 5 | Transfer of Erf 77 Waterkloof (TO-D-2001) | C-004 | Valid — the debtor matter |
| 7 | Labour dispute CCMA | `johan.pretorius@example.test` (email) | Valid |
| 8 | Sale of Erf 88 Umhlanga | `8803035800085` (ID number of Ayesha Patel) | Valid |
| 11 | Liquor licence renewal | C-012 | Valid, badge "Not a member" + "No member of the firm has this email address. The member who commits the take-on will be the matter's lead." |
| 15 | Bond registration Erf 101 Brooklyn | C-001 | Valid (Amahle Zulu's second matter) |
| 16 | Debt collection orphan | **C-099** | **Error** "No client matches C-099. Take the client on first, or correct the reference." |
| others | | C-006, C-010, C-011, C-018, C-013, C-015, C-002 | Valid |

- **Cards:** Rows 16 · Valid 15 · Errors 1 · Duplicates 0 · Blocks 0; the Error filter lists only
  row 16.
- **Commit** (from Validation): "15 records will be created. This cannot be undone." → Created 15 ·
  Failed 0. Row 16 stays an error, never committed (B-5, D-262). Client → Matters tab: Amahle Zulu
  (2), Bongani Nkosi (2), Johan Pretorius (1), Ayesha Patel (1).

## Trust opening position at C on G — expected outcome

Position form: bank 418 500.00 · outstanding deposits 5 000.00 · outstanding payments 20 000.00 ·
cashbook 403 500.00 · Fund ledger 1 250.00 · invested 300 000.00. C is a month end, so the
mid-month tick stays off.

| Source | Rows | Amounts |
|---|---|---|
| Client and matter balances | 3 | C-001/TR-A1 300 000,00 · C-002/TR-B1 150 000,00 · C-003/TR-C1 49 500,00 (= 499 500,00) |
| Unidentified deposits | 2 | 2 000,00 received C−472 (over a year: flag) · 750,00 received C−27 (= 2 750,00) |
| Investments | 2 | s86(3) 100 000,00 on I3 (the firm's surplus — placed in s86(3) −100 000,00) · s86(4) 200 000,00 on I4 for C-001/TR-A1, INSTR-A-01, endorsed |
| Items outstanding | 2 | Deposit EFT B-0001 5 000,00 (C−1, C-002/TR-B1) · Cheque PAY-778 20 000,00 (C−5, C-001/TR-A1) |

The equation:

- EQ-1: 418 500 + 5 000 − 20 000 = **403 500** = cashbook.
- EQ-2: clients 499 500 + Fund 1 250 + unidentified 2 750 − placed in s86(3) 100 000 = **403 500**
  = cashbook.
- Investments: 100 000 + 200 000 = 300 000 = invested.
- Deficit check: cashbook 403 500 + invested 300 000 = 703 500 = creditors incl. the s86(4) client
  money 703 500.

The three runs:

1. **Debit file** (`trust-client-ledger-debit.csv`, C-003 −500.00): Rows 9 · Valid 8 · Errors 1 ·
   Blocks **2**. The row: "This client's matter is in debit (R-500,00) at the cutover date. No
   trust creditor may be in debit (Rule 54.14.9). The firm must immediately report a debit balance
   to the Council in writing, with the reason and proof that it was rectified (Rule 54.14.11).
   Correct it in the previous system, then upload the corrected file." Banner "Statutory block —
   cannot be overridden": "The trust creditors (R353 500,00) do not equal the cashbook balance at
   cutover (R403 500,00): difference R50 000,00. Every creditor group must be included and must
   agree to the cent." and "1 row holds a statutory block (marked Block below). Correct it in the
   previous system, then upload the corrected file." No control in the banner; no commit; no
   sign-off.
2. **Corrected file, cashbook 403 510.00:** Rows 9 · Valid 9 · Errors 0 · Blocks **2**: "Bank
   balance R418 500,00 + outstanding deposits R5 000,00 − outstanding payments R20 000,00 is
   R403 500,00, not the cashbook balance R403 510,00." and "The trust creditors (R403 500,00) do not
   equal the cashbook balance at cutover (R403 510,00): difference R10,00. …"; EQ-1 and EQ-2
   "Difference R 10,00".
3. **Cashbook back to 403 500.00:** Validated, Rows 9 · Valid 9 · Errors 0 · Blocks 0; all five
   indicator rows "Difference R 0,00"; one flag, "Unidentified deposit older than one year".

Then Alice (owner) signs off from her Home page (TAKEON-SIGNOFF-v1, "… of E2E Test Organization …
this take-on of Take-on Trust Banking Account at {C long} …"), and Bob commits: "The opening
position of Take-on Trust Banking Account at {C long} will be posted and the account will not
accept entries dated on or before that date." The report: every position difference R 0,00, trust
creditors R 403 500,00, "Signed off by Alice Owner (Owner) … Prepared by Bob Admin." (not "Prepared
and signed off by the same person"). PDF starts `%PDF`; CSV first line `# Trust take-on report —
Take-on Trust Banking Account at {C long}`; the Reports-card downloads are byte-identical.

## Debtors (`debtors.csv`, cutover C) — expected outcome

| Row | Client reference | Fee note | Issue / due | Amount | Expected |
|---|---|---|---|---|---|
| 2 | C-004 + matter TO-D-2001 | PS-1001 | C−77 / C−47 | 4 600,00 | Valid, overdue |
| 3 | `johan.pretorius@example.test` | PS-1002 | C−29 / C+1 | 12 075,50 | Valid, overdue |
| 4 | C-006 | PS-1003 | C−10 / **blank** | 2 300,00 | Valid; due date defaulted to C+20: "No due date was given. It is set to {C+20 long}, the issue date plus the firm's payment terms of 30 days." + "Default" |
| 5 | `8803035800085` (ID number) | PS-1004 | C−29 / C+120 | 8 050,00 | Valid, not yet due |
| 6 | C-008 | **INV-0007** | C / C+30 | 1 150,00 | Valid; "Kazi's own fee-note numbers will continue after INV-0007." |
| 7 | C-009 (no address) | PS-1006 | C−30 / C | 3 450,00 | Valid; "Address or email missing" + "This client has no billing address or email in Kazi. …" |
| 8 | **C-098** | PS-1007 | | 999,00 | **Error** "No client matches C-098. Take the client on first, or correct the reference." |
| 9 | C-004 | PS-1008 | | **0,00** | **Error** "The amount must be more than R0,00." |
| 10 | C-005 | PS-1009 | **C+10** | 1 725,00 | **Error** "The date ({C+10 long}) is after the cutover date ({C long}). Take on only what the previous system held at the cutover date." |
| 11 | C-006 + matter **TO-D-2001** | PS-1010 | | 575,00 | **Error** "Matter TO-D-2001 is not a matter of client C-006." |
| 12 | C-007 | **INV-0001** | | 690,00 | **Error** "Fee-note number INV-0001 already exists in Kazi" (the seed's) |

- **Cards:** Rows 11 · Valid 6 · Errors 5 · Duplicates 0 · Blocks 0.
- **Commit** (from Validation): "Commit debtor opening balances take-on?" / "6 records will be
  created. This cannot be undone." → Created 6. Six SENT fee notes with their source numbers and
  the badge "Brought forward — take-on", total **31 625,50**. No email.
- **Debtor book** (Collections): one row per debtor client with the amount above, in the bucket of
  its due date as of the run date (Current ≤ 0 days overdue · 30d 1–30 · 60d 31–60 · 90d+ 61+). The
  spec computes the bucket from today's SA date. Collections reminders stay off.

## The first post-cutover month on G (generated by the spec, not a file)

`firstMonthStatementCsv()` writes `first-month-statement.csv` (Standard Bank CSV), M day 1 to the
last day:

| Date | Line | Amount | Balance | Resolved by |
|---|---|---|---|---|
| M-01 | Opening Balance (not a line) | 0.00 | 418 500.00 | — the opening = the take-on bank balance |
| M-02 | EFT B-0001 | +5 000.00 | 423 500.00 | auto-match → the deposit in transit |
| M-05 | PAY-778 | −20 000.00 | 403 500.00 | auto-match → the unpresented cheque |
| M-10 | CASH DEPOSIT BRANCH 0045 | +1 000.00 | 404 500.00 | record: Unidentified deposit |
| M-20 | LPFF INTEREST TRANSFER | −1 250.00 | 403 250.00 | record: Interest paid to the Fund (Fund sweep) |
| M-last | INTEREST | +312.40 | 403 562.40 | record: Fund interest |
| M-last | SERVICE FEE | −85.00 | 403 477.40 | record: Bank charge, recoverable, VAT 0 |

Expected: "Lines imported: 6", no import warning (no line ≤ C); "Auto-matched 2 lines", 4
remaining. After recording the four lines:

- EQ-1: bank **403 477,40** + 0 − 0 = adjusted 403 477,40 = cashbook 403 477,40 (403 500 + 1 000 −
  1 250 + 312,40 − 85,00). Difference R 0,00.
- EQ-2: client ledgers 499 500,00 + Fund 227,40 (1 250 − 1 250 + 312,40 − 85,00) + unidentified
  3 750,00 (2 750 + 1 000) − placed in s86(3) 100 000,00 = **403 477,40** = cashbook. Difference
  R 0,00.
- 0 unresolved lines; the only flag is the warning "Unidentified deposit held over a year"; no
  "Statement balance at the take-on date differs from the take-on".
- Complete → "{M} list locked — R 403 477,40 agrees with the cashbook and the adjusted bank
  balance." No manual match, no exclusion, no adjustment. No email.
- Receipts & Payments on G from the first of C's month to M's last day: one brought-forward line
  "Balance brought forward — take-on" R 403 500,00 and the footnote "The opening position taken on
  at {C long} is shown as one brought-forward line, not as receipts."

A client deposit or a client payment in M cannot be posted to G in the UI (every client posting
screen binds to the primary account, F-657C-8 / FR-1), so M's "deposit" and "payment" are the
unidentified deposit and the Fund sweep (D-259; gap report).
