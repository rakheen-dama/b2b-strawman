# QA Test Plan: 90-Day SA Law Firm Lifecycle

**Target**: DocTeams platform (post-Phase 61 + 64)
**Vertical**: General/mixed SA law firm ("Mathebula & Partners") — Johannesburg
**Stack**: E2E mock-auth on port 3001 / backend 8081 / Mailpit 8026
**Profile**: `legal-za`

**Prereqs**: `bash compose/scripts/e2e-up.sh` running. Login at `http://localhost:3001/mock-login`.
**Users**: Alice (Senior Partner / Owner), Bob (Associate / Admin), Carol (Candidate Attorney / Member)
**Org**: e2e-test-org

**How to use**: Walk through each step in order. Check the box when done. Note any failures with the step number.

**Screenshot convention**: Steps marked with 📸 require both a regression baseline (`toHaveScreenshot()`) and a curated capture saved to `documentation/screenshots/legal-vertical/`.

---

## Day 0 — Firm Setup

Actor: **Alice** (Owner)

- [ ] **0.1** Login as Alice at `/mock-login` → lands on `/org/e2e-test-org/dashboard`
- [ ] **0.2** Verify **legal-za profile is active** — sidebar shows "Matters" not "Projects", "Clients" not "Customers"
- [ ] **0.3** Verify sidebar navigation includes: Trust Accounting, Court Calendar, Conflict Check sections (module-gated)
- [ ] **0.4** Navigate to **Settings > General** (`/settings/general`)
- [ ] **0.5** Set default currency to **ZAR** → Save → verify it persists on reload
- [ ] **0.6** Set brand colour to **#1B3A4B** (dark teal) → Save → verify colour chip updates
- [ ] **0.7** Navigate to **Settings > Rates** (`/settings/rates`)
- [ ] **0.8** Create org-level billing rate for Alice: **R2,500/hr** (ZAR)
- [ ] **0.9** Create org-level billing rate for Bob: **R1,200/hr**
- [ ] **0.10** Create org-level billing rate for Carol: **R550/hr**
- [ ] **0.11** Create cost rate for Alice: **R1,000/hr**
- [ ] **0.12** Create cost rate for Bob: **R500/hr**
- [ ] **0.13** Create cost rate for Carol: **R200/hr**
- [ ] **0.14** Navigate to **Settings > Tax** (`/settings/tax`)
- [ ] **0.15** Create tax rate: Name = **VAT**, Rate = **15%**, Active = yes
- [ ] **0.16** Navigate to **Settings > Team** (`/settings/team`) → verify Alice (Owner), Bob (Admin), Carol (Member) are listed
- [ ] **0.17** Navigate to **Settings > Custom Fields** (`/settings/custom-fields`) → verify legal field packs loaded (matter_type, case_number, court_name, opposing_party, etc.)
- [ ] **0.18** Navigate to **Settings > Templates** (`/settings/templates`) → verify 4 matter templates listed: "Litigation (Personal Injury / General)", "Deceased Estate Administration", "Collections (Debt Recovery)", "Commercial (Corporate & Contract)"
- [ ] **0.19** Navigate to **Settings > Trust Accounts** (or equivalent trust config page)
- [ ] **0.20** Create trust account: Name = **"Mathebula & Partners Trust Account"**, Type = **GENERAL**, Bank = **Standard Bank**
- [ ] **0.21** Set LPFF rate: **6.5%** (test rate)
- [ ] **0.22** Navigate to **Settings > Modules** → verify court_calendar, conflict_check, lssa_tariff, trust_accounting all enabled
- [ ] **0.23** 📸 **Screenshot**: Dashboard with legal nav, trust account card, "Matters" label visible

**Day 0 Checkpoints**:
- [ ] Currency shows ZAR on settings page
- [ ] 3 billing rates + 3 cost rates visible on rates page
- [ ] VAT 15% visible on tax page
- [ ] Legal custom fields exist for CLIENT and MATTER entity types
- [ ] 4 matter templates present (Litigation, Deceased Estate Administration, Collections, Commercial)
- [ ] Trust account created (GENERAL, Standard Bank)
- [ ] All 4 legal modules enabled
- [ ] **Terminology**: sidebar, headings, breadcrumbs all show legal terms (Matter, Client, Fee Note, etc.)

---

## Day 1 — First Client Onboarding (Sipho Ndlovu — Litigation)

### Conflict check & client creation

Actor: **Bob** (Associate / Admin)

- [ ] **1.1** Login as Bob → Navigate to **Conflict Check** page
- [ ] **1.2** Search for **"Sipho Ndlovu"** → verify result is **CLEAR** (green — no existing matches)
- [ ] **1.3** 📸 **Screenshot**: Conflict check clear result
- [ ] **1.4** Navigate to **Clients** (`/customers` or legal-termed equivalent)
- [ ] **1.5** Click **New Client**
- [ ] **1.6** Fill: Name = **Sipho Ndlovu**, Email = **sipho.ndlovu@email.co.za**, Phone = **+27-82-555-0101**
- [ ] **1.7** Fill custom fields: client_type = **INDIVIDUAL**, id_passport_number = **8501015800083**, physical_address = "42 Commissioner St, Johannesburg, 2001"
- [ ] **1.8** Save → verify client appears in list with status **PROSPECT**
- [ ] **1.9** Click into client detail → verify lifecycle badge shows **PROSPECT**

### FICA/KYC onboarding

- [ ] **1.10** Transition Sipho to **ONBOARDING** → verify badge updates
- [ ] **1.11** Navigate to Onboarding/Compliance tab → verify FICA checklist auto-instantiated
- [ ] **1.12** Mark checklist items: "Certified ID Copy" ✓, "Proof of Address" ✓
- [ ] **1.13** If KYC verification button is available (Phase 61), click to verify ID — expect no-op in E2E stack but verify UI flow
- [ ] **1.14** Complete all remaining FICA checklist items
- [ ] **1.15** Verify client auto-transitions to **ACTIVE**

### Create matter from litigation template

- [ ] **1.16** Navigate to **Matters** → click **New Matter**
- [ ] **1.17** Select template: **Litigation**
- [ ] **1.18** Fill: Name = **"Personal Injury Claim — Sipho Ndlovu vs RAF"**, Client = Sipho Ndlovu
- [ ] **1.19** Set custom fields: matter_type = **LITIGATION**, case_number = "TBD", court_name = "Gauteng Division, Johannesburg", opposing_party = "Road Accident Fund"
- [ ] **1.20** Save → verify matter created with **9 pre-populated action items** from litigation template
- [ ] **1.21** Verify action items match template: Initial consultation, Letter of demand, Issue summons, etc.
- [ ] **1.22** 📸 **Screenshot**: Matter detail showing pre-populated action items from litigation template

### Engagement letter

Actor: **Alice** (Senior Partner)

- [ ] **1.23** Login as Alice → Navigate to **Engagement Letters** (Proposals)
- [ ] **1.24** Click **New Engagement Letter**
- [ ] **1.25** Fill: Title = "Personal Injury — Sipho Ndlovu", Client = Sipho, Fee Model = **Hourly**, Expiry = 30 days
- [ ] **1.26** Save → verify status = **DRAFT**
- [ ] **1.27** Click **Send** → verify status = **SENT**
- [ ] **1.28** Check Mailpit (`http://localhost:8026`) for engagement letter notification

**Day 1 Checkpoints**:
- [ ] Conflict check run (clear result)
- [ ] Client created, transitioned PROSPECT → ONBOARDING → ACTIVE
- [ ] FICA checklist completed
- [ ] Matter created from Litigation template with 9 action items
- [ ] Engagement letter sent, email in Mailpit
- [ ] **Terminology** throughout: "Client" not "Customer", "Matter" not "Project", "Action Items" not "Tasks", "Engagement Letter" not "Proposal"

---

## Day 2-3 — Additional Client Onboarding

### Apex Holdings (company, commercial) — Actor: Bob

- [ ] **2.1** **Conflict check** on "Apex Holdings" → CLEAR
- [ ] **2.2** Create client: **Apex Holdings (Pty) Ltd**, email = legal@apexholdings.co.za, registration_number = 2019/123456/07
- [ ] **2.3** Fill: physical_address = "100 Sandton Drive, Sandton, 2196", client_type = COMPANY
- [ ] **2.4** Transition to ACTIVE (complete FICA: company registration CM29, director IDs, proof of business address)
- [ ] **2.5** Create matter from **Commercial template**: "Shareholder Agreement — Apex Holdings"
- [ ] **2.6** Set: matter_type = COMMERCIAL, estimated_value = R2,500,000
- [ ] **2.7** Verify 9 action items from commercial template
- [ ] **2.8** 📸 **Screenshot**: Client detail with legal custom fields populated

### Moroka Family Trust (trust, estates) — Actor: Alice

- [ ] **2.9** **Conflict check** on "Moroka Family Trust" → CLEAR
- [ ] **2.10** Create client: **Moroka Family Trust**, email = trustees@morokatrust.co.za
- [ ] **2.11** Fill: client_type = TRUST, registration_number = IT/2015/000123, notes = "Trustees: James Moroka, Sarah Moroka. Deceased: Peter Moroka (d. 2026-02-15)"
- [ ] **2.12** Transition to ACTIVE (complete FICA: trust deed, trustee IDs, letters of authority)
- [ ] **2.13** Create matter from **Deceased Estate Administration** template: "Deceased Estate — Peter Moroka"
- [ ] **2.14** Set: matter_type = ESTATES
- [ ] **2.15** Verify 9 action items from Deceased Estate Administration template
- [ ] **2.16** 📸 **Screenshot**: Deceased Estate Administration matter with template action items

### QuickCollect Services (company, collections) — Actor: Bob

- [ ] **2.17** **Conflict check** on "QuickCollect Services" → CLEAR
- [ ] **2.18** Create client: **QuickCollect Services (Pty) Ltd**, email = operations@quickcollect.co.za, registration_number = 2020/654321/07
- [ ] **2.19** Transition to ACTIVE (complete FICA)
- [ ] **2.20** Create matter from **Collections template**: "Debt Recovery — vs Mokoena (R45,000)"
- [ ] **2.21** Set: matter_type = COLLECTIONS, estimated_value = R45,000
- [ ] **2.22** Verify 9 action items from collections template
- [ ] **2.23** Create 2nd matter: "Debt Recovery — vs Pillay (R28,000)", same template
- [ ] **2.24** Create 3rd matter: "Debt Recovery — vs Dlamini (R62,000)", same template

**Day 2-3 Checkpoints**:
- [ ] 4 total clients visible on client list, all ACTIVE
- [ ] 6 total matters: Sipho (1), Apex (1), Moroka (1), QuickCollect (3)
- [ ] Conflict checks run for all 4 clients (all clear)
- [ ] All templates applied correctly with 9 action items each
- [ ] FICA completed for all clients
- [ ] **Terminology**: Client list, matter list, and action item labels all use legal terms

---

## Day 7 — First Week of Work

### Carol (Candidate Attorney) — junior work

- [ ] **7.1** Login as Carol → navigate to **My Work** (`/my-work`)
- [ ] **7.2** Verify assigned action items are visible across matters
- [ ] **7.3** Open Sipho matter → Action Items tab → click "Initial consultation & case assessment"
- [ ] **7.4** Mark status → **In Progress**
- [ ] **7.5** Click **Log Time** → fill: **90 minutes**, Date = today, Description = "Taking instructions from client re: personal injury claim against RAF", Billable = yes
- [ ] **7.6** Save → verify time recording appears in the Time tab
- [ ] **7.7** Check that the rate snapshot is **R550** (Carol's billing rate)
- [ ] **7.8** Open QuickCollect "vs Mokoena" matter → log time on "Skip tracing": **60 min**, "Debtor address verification — TPN trace"
- [ ] **7.9** Open QuickCollect "vs Pillay" matter → log time on "Letter of demand": **45 min**, "Drafting Section 129 notice"

### Bob (Associate) — substantive legal work

- [ ] **7.10** Login as Bob → open Sipho matter
- [ ] **7.11** Log time on "Letter of demand": **120 min**, "Drafting demand letter to Road Accident Fund"
- [ ] **7.12** Verify rate snapshot = **R1,200** (Bob's rate)
- [ ] **7.13** Open Apex matter → log time on "Due diligence review": **180 min**, "Reviewing shareholder agreements and MOI"
- [ ] **7.14** Add comment on Sipho matter: **"RAF claim — need police report number and J88 medical report from client"**
- [ ] **7.15** Verify comment appears with Bob's name and timestamp

### Alice (Senior Partner) — advisory & court calendar

- [ ] **7.16** Login as Alice → open Moroka estates matter
- [ ] **7.17** Log time on "Report estate to Master": **60 min**, "Reviewing estate documents, preparing J294 reporting form"
- [ ] **7.18** Verify rate snapshot = **R2,500** (Alice's rate)
- [ ] **7.19** Navigate to **Court Calendar**
- [ ] **7.20** Add court date: Matter = Sipho litigation, Type = **Motion**, Date = 30 days from today, Court = "Gauteng Division, Johannesburg", Notes = "Application for interim payment"
- [ ] **7.21** Verify court date appears with status **SCHEDULED** (blue badge)
- [ ] **7.22** 📸 **Screenshot**: Court calendar with first entry

### Verification

- [ ] **7.23** Navigate to Sipho matter → **Activity** tab → verify time recordings and comment appear in feed
- [ ] **7.24** Navigate to **My Work** as Carol → verify action items show updated status
- [ ] **7.25** 📸 **Screenshot**: My Work page showing legal action items across matters

**Day 7 Checkpoints**:
- [ ] Time recordings created by 3 different users on 4 different matters
- [ ] Rate snapshots match each user's billing rate
- [ ] Action item transitioned to IN_PROGRESS
- [ ] Comment visible on matter
- [ ] Court date created with SCHEDULED status
- [ ] Activity feed shows chronological events
- [ ] My Work page reflects correct assignments
- [ ] **Terminology**: Time tab shows "Time Recordings" not "Time Entries", action item labels correct throughout

---

## Day 14 — Trust Deposits & Conflict Detection

### Trust accounting begins — Actor: Alice

- [ ] **14.1** Navigate to **Trust Accounting** → **Transactions**
- [ ] **14.2** Click **New Transaction** → Type = **DEPOSIT**
- [ ] **14.3** Fill: Client = **Moroka Family Trust**, Amount = **R250,000**, Reference = "FNB EFT — Estate Moroka proceeds from property sale"
- [ ] **14.4** Save → verify transaction shows as **PENDING APPROVAL**
- [ ] **14.5** **Approve** the transaction
- [ ] **14.6** Navigate to **Client Ledgers** → open Moroka Family Trust
- [ ] **14.7** Verify ledger card shows **R250,000** balance
- [ ] **14.8** 📸 **Screenshot**: Trust deposit form + client ledger with balance

### Second trust deposit

- [ ] **14.9** Create deposit: Client = **QuickCollect Services**, Amount = **R45,000**, Reference = "Settlement payment — Mokoena debtor, full and final"
- [ ] **14.10** Approve transaction
- [ ] **14.11** Verify QuickCollect client ledger shows R45,000

### Conflict detection — Actor: Bob

- [ ] **14.12** Navigate to **Conflict Check**
- [ ] **14.13** Search for **"Mokoena"** → expect **AMBER or RED** match (adverse party / debtor from QuickCollect collections matter)
- [ ] **14.14** Verify match details show the linked matter
- [ ] **14.15** 📸 **Screenshot**: Conflict check with adverse party match found

### Adverse party registry

- [ ] **14.16** Navigate to Sipho matter → **Adverse Parties** (or equivalent section)
- [ ] **14.17** Add adverse party: **"Road Accident Fund"**
- [ ] **14.18** Verify adverse party linked to matter

### More time logging

- [ ] **14.19** Carol: 120 min on Sipho "Discovery" action item, "Collating medical records and police report for discovery bundle"
- [ ] **14.20** Carol: 60 min on QuickCollect "vs Mokoena" — "Issue summons", "Preparing summons for service"
- [ ] **14.21** Bob: 90 min on Moroka "Inventory of assets & liabilities", "Compiling estate asset register from bank statements and property valuations"

### Trust dashboard check

- [ ] **14.22** Navigate to **Trust Accounting** dashboard
- [ ] **14.23** Verify summary cards show: total trust balance (R295,000), number of clients with trust funds (2), recent transactions
- [ ] **14.24** 📸 **Screenshot**: Trust dashboard overview

**Day 14 Checkpoints**:
- [ ] 2 trust deposits approved and posted
- [ ] Client ledger balances correct (Moroka R250,000, QuickCollect R45,000)
- [ ] Conflict check found Mokoena as adverse party (AMBER/RED)
- [ ] Adverse party "Road Accident Fund" linked to Sipho matter
- [ ] Trust dashboard shows accurate summary
- [ ] **Terminology**: Trust pages use "Client" not "Customer", ledger labels consistent with legal terms

---

## Day 30 — First Billing Cycle

Actor: **Alice** (billing)

### Fee note for Sipho (hourly + tariff + disbursement)

- [ ] **30.1** Navigate to **Fee Notes** (`/invoices` or legal-termed equivalent)
- [ ] **30.2** Click **New Fee Note**
- [ ] **30.3** Select client = **Sipho Ndlovu**, matter = "Personal Injury Claim"
- [ ] **30.4** **Add tariff line**: Navigate to tariff selector → find LSSA tariff item → select "Instructions to institute action" → verify amount auto-populated from LSSA schedule
- [ ] **30.5** Add time-based line: Description = "Senior Partner — consultation & strategy", Qty = 1 hr, Unit Price = R2,500, Tax = VAT (15%)
- [ ] **30.6** Add time-based line: Description = "Associate — demand letter & discovery", Qty = 2 hrs, Unit Price = R1,200, Tax = VAT
- [ ] **30.7** Add time-based line: Description = "Candidate Attorney — client liaison & research", Qty = 1.5 hrs, Unit Price = R550, Tax = VAT
- [ ] **30.8** Add disbursement line: Description = "Sheriff service fees", Amount = R350, Tax = VAT
- [ ] **30.9** Verify subtotal calculation (tariff + time lines + disbursement)
- [ ] **30.10** Verify VAT = 15% of subtotal
- [ ] **30.11** Save as DRAFT → verify fee note number auto-generated
- [ ] **30.12** 📸 **Screenshot**: Fee note with tariff line + time lines + disbursement + VAT calculation

### Fee note for Apex (fixed fee — first tranche)

- [ ] **30.13** Create fee note: Client = Apex Holdings, Description = "Shareholder Agreement — Phase 1 (Due Diligence)"
- [ ] **30.14** Add line: "Professional fees — due diligence review", Qty = 1, Unit Price = R35,000, Tax = VAT
- [ ] **30.15** Verify total = **R40,250** (R35,000 + 15% VAT)

### Trust fee transfer for Moroka (fees deducted from trust)

- [ ] **30.16** Navigate to **Trust Accounting → Transactions**
- [ ] **30.17** New Transaction → Type = **FEE_TRANSFER**
- [ ] **30.18** Client = Moroka Family Trust, Amount = **R8,500**, Description = "Professional fees — estate administration (reporting to Master, asset inventory)"
- [ ] **30.19** Approve → verify trust balance decremented (R250,000 → R241,500)
- [ ] **30.20** Verify Moroka client ledger updated
- [ ] **30.21** 📸 **Screenshot**: Trust fee transfer transaction

### Fee note for QuickCollect

- [ ] **30.22** Create fee note: Client = QuickCollect, Description = "Collections — Mokoena matter"
- [ ] **30.23** Add line: "Professional fees — skip tracing, demand, summons", Amount based on logged time
- [ ] **30.24** Add disbursement: "Court filing fees" R200, Tax = VAT

### Fee note lifecycle

- [ ] **30.25** Open Sipho fee note → click **Approve** → verify status = **APPROVED**
- [ ] **30.26** Click **Send** → verify status = **SENT**
- [ ] **30.27** Check Mailpit → verify fee note email received
- [ ] **30.28** Repeat approve + send for Apex fee note
- [ ] **30.29** Verify sequential fee note numbering across all 4 fee notes
- [ ] **30.30** 📸 **Screenshot**: Fee note list showing 4 fee notes in various statuses

### Fee estimate (budget)

- [ ] **30.31** Open Apex matter → **Fee Estimate** tab (Budget)
- [ ] **30.32** Set fee estimate: Total = **R150,000**, Currency = ZAR, Alert at 80%
- [ ] **30.33** Verify fee estimate status shows amount billed so far (R35,000 of R150,000)

**Day 30 Checkpoints**:
- [ ] 4 fee notes created (hourly+tariff, fixed, trust-sourced, collections)
- [ ] LSSA tariff line integrated into fee note
- [ ] Disbursement line items work correctly
- [ ] VAT calculations correct on all fee notes
- [ ] Fee note lifecycle: DRAFT → APPROVED → SENT
- [ ] Email sent for fee notes (check Mailpit)
- [ ] Fee note numbering is sequential
- [ ] Trust fee transfer reduced Moroka ledger balance
- [ ] Fee estimate set and tracking for Apex matter
- [ ] **Terminology**: "Fee Notes" not "Invoices" on list/detail pages, "Disbursement" not "Expense" on line items, "Fee Estimate" not "Budget" on tab heading

---

## Day 45 — Reconciliation & Prescription

### Bank reconciliation — Actor: Alice

- [ ] **45.1** Navigate to **Trust Accounting → Reconciliation**
- [ ] **45.2** Select trust account: "Mathebula & Partners Trust Account"
- [ ] **45.3** Upload bank statement CSV (Standard Bank format with matching transactions: R250,000 Moroka deposit, R45,000 QuickCollect deposit, R8,500 fee transfer)
- [ ] **45.4** Verify **auto-matching**: deposits matched by amount + reference
- [ ] **45.5** Verify any unmatched items can be manually matched or excluded
- [ ] **45.6** Verify **3-way reconciliation**: bank balance = cashbook balance = client ledger total
- [ ] **45.7** Mark reconciliation **complete**
- [ ] **45.8** 📸 **Screenshot**: Reconciliation split-pane with auto-matched transactions, 3-way balance totals

### Prescription tracking — Actor: Bob

- [ ] **45.9** Open Sipho matter → **Prescription** tab
- [ ] **45.10** Verify prescription type identified (personal injury = **3 years** per Prescription Act)
- [ ] **45.11** Verify prescription date calculated from date of accident/cause of action
- [ ] **45.12** Check days remaining display
- [ ] **45.13** 📸 **Screenshot**: Prescription tracker showing days remaining, prescription type

### Court date lifecycle

- [ ] **45.14** Navigate to **Court Calendar**
- [ ] **45.15** Find Sipho's motion date → click → update status to **POSTPONED**
- [ ] **45.16** Add new court date: same matter, Type = Motion, Date = 14 days after original, Notes = "Postponement by agreement — respondent's counsel unavailable"
- [ ] **45.17** Verify original date shows **POSTPONED** (amber badge), new date shows **SCHEDULED** (blue badge)

### Payment recording

- [ ] **45.18** Open Apex fee note (R40,250) → click **Record Payment**
- [ ] **45.19** Enter reference = "EFT-2026-APX-001" → submit
- [ ] **45.20** Verify fee note status changes to **PAID**

### Resource check

- [ ] **45.21** Navigate to **Resources** (`/resources`) → verify allocation data loads
- [ ] **45.22** Check utilization breakdown for Alice, Bob, Carol

**Day 45 Checkpoints**:
- [ ] Bank reconciliation balanced (3-way)
- [ ] Prescription tracking functional (3-year personal injury)
- [ ] Court date lifecycle: SCHEDULED → POSTPONED → new SCHEDULED
- [ ] Payment recorded, fee note status = PAID
- [ ] Resource utilization data present
- [ ] **Terminology**: Court Calendar labels, reconciliation page headings, and fee note status badges all use legal terms

---

## Day 60 — Interest Run & Second Billing

### Interest calculation — Actor: Alice

- [ ] **60.1** Navigate to **Trust Accounting → Interest**
- [ ] **60.2** Click **New Interest Run**
- [ ] **60.3** Select period: last 30 days, Account = "Mathebula & Partners Trust Account"
- [ ] **60.4** Click **Calculate** → verify daily-balance method applied
- [ ] **60.5** Verify interest calculated for Moroka (higher balance = more interest) and QuickCollect
- [ ] **60.6** Verify **LPFF split** calculated at configured rate (6.5%)
- [ ] **60.7** Click **Approve** → then **Post**
- [ ] **60.8** Verify **INTEREST_CREDIT** transactions posted to client ledgers
- [ ] **60.9** Verify **INTEREST_LPFF** transaction posted (LPFF portion)
- [ ] **60.10** 📸 **Screenshot**: Interest run wizard showing calculation breakdown, LPFF split percentages

### Trust investment — Actor: Alice

- [ ] **60.11** Navigate to **Trust Accounting → Investments**
- [ ] **60.12** Click **Place Investment**
- [ ] **60.13** Fill: Client = Moroka Family Trust, Principal = **R200,000**, Institution = "Standard Bank Fixed Deposit", Rate = 8.5%, Term = 90 days, Deposit Date = today
- [ ] **60.14** Select investment basis: **§86(3) Firm Discretion** (firm-initiated investment)
- [ ] **60.15** Save → verify investment appears in register as ACTIVE
- [ ] **60.16** Verify trust balance reduced (R200,000 moved to investment)
- [ ] **60.17** 📸 **Screenshot**: Investment placement dialog with §86(3) selection

### Test §86(4) path

- [ ] **60.18** Place second investment: Client = QuickCollect, Principal = **R30,000**, Institution = "Nedbank Call Account", Rate = 7.0%, Term = 60 days
- [ ] **60.19** Select investment basis: **§86(4) Client Instruction** (client-instructed)
- [ ] **60.20** Verify investment register distinguishes §86(3) and §86(4) investments

### Second billing cycle

- [ ] **60.21** Create Sipho fee note #2: ongoing litigation work (discovery prep, court attendance prep)
- [ ] **60.22** Create Moroka fee note: time-based estate administration work → then do **FEE_TRANSFER** from trust for the same amount
- [ ] **60.23** Create QuickCollect fee note: "vs Pillay" matter, include line "Default judgment obtained — Clerk of Court fee" as disbursement
- [ ] **60.24** Approve and send all fee notes

### Reports

- [ ] **60.25** Navigate to **Reports** (`/reports`)
- [ ] **60.26** Generate time tracking report → verify data renders across all matters
- [ ] **60.27** Generate matter profitability report → verify revenue vs cost data
- [ ] **60.28** Export CSV if available → verify file downloads
- [ ] **60.29** 📸 **Screenshot**: Profitability report showing margins across 4 clients

**Day 60 Checkpoints**:
- [ ] Interest run completed with LPFF split correctly calculated
- [ ] INTEREST_CREDIT + INTEREST_LPFF transactions posted to ledgers
- [ ] Investment placed under §86(3) (firm discretion) — LPFF rate from config
- [ ] Investment placed under §86(4) (client instruction) — statutory 5% rate
- [ ] Investment register shows both with correct basis column
- [ ] Second billing cycle completed (3 more fee notes)
- [ ] Trust fee transfer for Moroka reduces ledger balance
- [ ] Reports functional with meaningful data
- [ ] **Terminology**: Report headings use "Matter Profitability" not "Project Profitability", "Time Recordings" not "Time Entries" in report filters

---

## Day 75 — Complex Engagement & Adverse Parties

### Multi-matter per client — Actor: Alice

- [ ] **75.1** Create new matter for Sipho: **"Road Accident Fund Claim — Sipho Ndlovu (Second Incident)"** using Litigation template
- [ ] **75.2** Verify Sipho now has **2 matters** — both visible on client detail

### Adverse party registry

- [ ] **75.3** Open Sipho matter #1 → Adverse Parties → add **"Road Accident Fund"** (if not already added on Day 14)
- [ ] **75.4** Open Sipho matter #2 → Adverse Parties → add **"Road Accident Fund"**
- [ ] **75.5** Open QuickCollect "vs Mokoena" → add **"T. Mokoena"** as adverse party
- [ ] **75.6** Open QuickCollect "vs Pillay" → add **"R. Pillay"** as adverse party

### Conflict check stress test — Actor: Bob

- [ ] **75.7** Conflict check: search **"Ndlovu"** → verify Sipho shows as **existing client** (green — same side)
- [ ] **75.8** Conflict check: search **"Road Accident Fund"** → verify shows as **existing adverse party** (amber)
- [ ] **75.9** Conflict check: search **"Mokoena"** → verify shows as **adverse party** on QuickCollect matter (amber/red)
- [ ] **75.10** 📸 **Screenshot**: Conflict check showing existing adverse party match

### Estate matter progression — Actor: Carol + Alice

- [ ] **75.11** Carol: Open Moroka estates matter → mark "Advertise for creditors" → **DONE**
- [ ] **75.12** Carol: Mark "Inventory of assets & liabilities" → **DONE**
- [ ] **75.13** Carol: Log **360 min** on "Prepare Liquidation & Distribution account" — "Compiling L&D account from estate asset register and creditor claims"
- [ ] **75.14** Alice: Create information request for Moroka: Subject = "Outstanding Estate Documents", Items: "Bank account closure letter", "Outstanding utility bills", "Property valuation update"
- [ ] **75.15** Send information request → check Mailpit

### Year-end engagement (new matter for existing client)

- [ ] **75.16** Create new matter for Apex: **"Company Secretarial — Annual Returns 2026"** using Commercial template
- [ ] **75.17** Verify Apex now has 2 matters
- [ ] **75.18** Log time: Bob 120 min on "Client intake & scope of work"

### Resource utilization

- [ ] **75.19** Navigate to **Resources** → utilization tab
- [ ] **75.20** Verify billable hours breakdown for Alice, Bob, Carol
- [ ] **75.21** Check capacity data

**Day 75 Checkpoints**:
- [ ] Multi-matter per client works (Sipho: 2, Apex: 2, QuickCollect: 3)
- [ ] 9 total matters across 4 clients
- [ ] Adverse party registry populated (RAF, Mokoena, Pillay)
- [ ] Conflict checks detect existing adverse parties correctly
- [ ] Estate matter progression tracked (2 items completed, L&D in progress)
- [ ] Information request sent (Mailpit)
- [ ] Resource utilization shows meaningful data
- [ ] **Terminology**: Conflict check results, information request emails, and resource pages all use legal terms consistently

---

## Day 90 — Quarter Review & Section 35 Compliance

Actor: **Alice** (full review)

### Portfolio review

- [ ] **90.1** Navigate to **Clients** → verify all 4 clients listed with **ACTIVE** status
- [ ] **90.2** Check client count and lifecycle summary
- [ ] **90.3** Navigate to **Matters** → verify 9 matters visible across clients
- [ ] **90.4** Navigate to **Fee Notes** → verify 7+ fee notes visible (mix of DRAFT, SENT, PAID)
- [ ] **90.5** Filter by status **PAID** → verify Apex fee note shows
- [ ] **90.6** Filter by status **SENT** → verify other fee notes show
- [ ] **90.7** 📸 **Screenshot**: Client list, matter list, fee note list (all with legal terminology)

### Trust compliance — Section 35

- [ ] **90.8** Navigate to **Trust Accounting → Reports**
- [ ] **90.9** Select **Section 35 Data Pack** → Generate
- [ ] **90.10** Verify composite report includes:
  - [ ] Trust account summary (account name, bank, type)
  - [ ] All client ledger balances (Moroka, QuickCollect)
  - [ ] Reconciliation status (last reconciliation date, balanced = yes/no)
  - [ ] Interest allocations (with LPFF split details)
  - [ ] Investment register (with §86(3)/(4) basis column)
- [ ] **90.11** 📸 **Screenshot**: Section 35 report generation / preview

### Trust reports

- [ ] **90.12** Generate **Client Trust Balances** report → verify all clients with trust funds listed
- [ ] **90.13** Generate **Investment Register** → verify §86(3) and §86(4) investments shown separately with applicable rates
- [ ] **90.14** Generate **Trust Receipts & Payments** → verify complete transaction history
- [ ] **90.15** 📸 **Screenshot**: Investment register with §86 basis column

### Profitability

- [ ] **90.16** Navigate to **Profitability** page
- [ ] **90.17** Verify data across 4 clients: revenue, costs, margins
- [ ] **90.18** Check utilization: Alice, Bob, Carol — billable hours + billable %
- [ ] **90.19** Check matter profitability: compare revenue vs cost per matter
- [ ] **90.20** 📸 **Screenshot**: Profitability dashboard with per-matter breakdown

### Dashboard

- [ ] **90.21** Navigate to **Dashboard**
- [ ] **90.22** Verify KPI cards show meaningful non-zero data (matters, fee notes, trust balance, etc.)
- [ ] **90.23** Verify recent activity widget shows trust transactions, time recordings, fee notes
- [ ] **90.24** 📸 **Screenshot**: Dashboard overview (curated shot for blog/deck)

### Document generation

- [ ] **90.25** Open Sipho client → find **Generate Document** button/dropdown
- [ ] **90.26** Select a legal template → click **Preview** → verify HTML renders with Sipho's details
- [ ] **90.27** Generate PDF if available → verify download

### Compliance overview

- [ ] **90.28** Navigate to **Compliance** (`/compliance`)
- [ ] **90.29** Verify all 4 clients show as FICA complete
- [ ] **90.30** Verify trust reconciliation status shown (if integrated)

### Court calendar review

- [ ] **90.31** Navigate to **Court Calendar**
- [ ] **90.32** Verify entries: original motion (POSTPONED), new motion (SCHEDULED)
- [ ] **90.33** Update one court date to **HEARD** → verify status change to green badge

### Role-based access

- [ ] **90.34** Login as **Carol** → navigate to `/settings/rates` → verify access is **blocked** (permission error or redirect)
- [ ] **90.35** Carol → navigate to trust account config → verify **blocked** (trust admin requires Owner/Admin)
- [ ] **90.36** Carol → navigate to `/my-work` → verify personal action items and time recordings visible
- [ ] **90.37** Carol → try to approve a trust transaction → verify **blocked** (approval requires Owner/Admin)
- [ ] **90.38** Login as **Bob** → navigate to `/settings/general` → verify admin access works
- [ ] **90.39** Bob → approve a trust transaction → verify works (Admin role)
- [ ] **90.40** 📸 **Screenshot**: Carol's restricted view vs Alice's full view (side by side)

**Day 90 Final Checkpoints**:
- [ ] 4 clients, all ACTIVE, FICA complete
- [ ] 9 matters with mixed statuses and types (litigation, commercial, estates, collections)
- [ ] 7+ fee notes (DRAFT, SENT, PAID)
- [ ] Trust account with: deposits, fee transfers, reconciliation, interest run, 2 investments (§86(3) + §86(4))
- [ ] Court calendar with dates (SCHEDULED, POSTPONED, HEARD)
- [ ] Conflict checks logged (clear + adverse party match results)
- [ ] Prescription tracking functional (3-year personal injury)
- [ ] LSSA tariff lines on fee notes
- [ ] Section 35 report generated with all required data
- [ ] Investment register shows §86 basis distinction
- [ ] Role-based access enforced (Carol blocked from admin/trust config/trust approval)
- [ ] All UI shows legal terminology throughout: "Matter", "Client", "Fee Note", "Disbursement", "Action Item", "Engagement Letter", "Mandate", "Fee Estimate", "Time Recording", "Tariff Schedule"
- [ ] **Overall verdict: Could Mathebula & Partners actually run their practice on this platform?** [ YES / NO ]

### Fork-Readiness Assessment

Rate each area PASS / PARTIAL / FAIL. All must be PASS or PARTIAL (with documented workaround) for fork-readiness.

| Area | Criteria | Rating |
|------|----------|--------|
| **Terminology** | All UI labels use legal-za terms (Matter, Client, Fee Note, Disbursement, Action Item, Engagement Letter, Mandate, Fee Estimate, Time Recording, Tariff Schedule). No generic terms visible. | [ ] |
| **Matter Templates** | 4 templates seed correctly, each produces 9 action items, matterType custom field populated. | [ ] |
| **Trust Accounting** | Deposits, fee transfers, reconciliation, interest run (LPFF split), investments (§86(3)/(4)) all functional. | [ ] |
| **LSSA Tariff** | Tariff line items auto-populate amounts from schedule on fee notes. | [ ] |
| **Conflict Checks** | Name search returns clear/amber/red results, adverse party registry populated and cross-referenced. | [ ] |
| **Court Calendar** | Date lifecycle (SCHEDULED/POSTPONED/HEARD), linked to matters, date display correct. | [ ] |
| **Prescription Tracking** | Prescription type identified, date calculated, days remaining displayed. | [ ] |
| **Section 35 Compliance** | Data pack generates with trust summary, ledger balances, reconciliation status, interest allocations, investment register. | [ ] |
| **FICA/KYC** | Checklist auto-instantiated, completion triggers ACTIVE transition, compliance overview reflects status. | [ ] |
| **Fee Notes (Billing)** | Lifecycle (DRAFT/APPROVED/SENT/PAID), sequential numbering, VAT calculation, disbursement lines, email delivery. | [ ] |
| **Role-Based Access** | Carol (Member) blocked from rates/trust config/trust approval. Bob (Admin) has admin access. Alice (Owner) has full access. | [ ] |
| **Reports & Profitability** | Time tracking, matter profitability, and utilization reports render with correct data across all 4 clients. | [ ] |
| **Data Integrity** | 4 clients (all ACTIVE), 9 matters, 7+ fee notes, trust balances reconcile, investment register accurate. | [ ] |
| **Screenshot Baselines** | All 25 regression baselines captured, 16 curated hero shots saved to documentation directory. | [ ] |

**Fork-readiness verdict**: [ READY / NOT READY — must be NOT READY if any row is FAIL, or PARTIAL without documented workaround ]

Blocking issues (if any):
1. _TBD during execution (leave blank only if all required criteria pass)_

---

## Screenshot Summary

### Regression Baselines (25 captures)

| Step | File Name | Description |
|---|---|---|
| 0.23 | `day-00-dashboard-legal-nav-active.png` | Dashboard with legal sidebar navigation |
| 1.3 | `day-01-conflict-check-clear.png` | Conflict check clear result |
| 1.22 | `day-01-litigation-template-items-loaded.png` | Matter with pre-populated action items |
| 2.8 | `day-02-client-legal-fields-populated.png` | Client detail with legal custom fields |
| 2.16 | `day-02-estates-template-applied.png` | Deceased Estate Administration matter template |
| 7.22 | `day-07-court-calendar-entry-scheduled.png` | Court calendar with first entry |
| 7.25 | `day-07-my-work-legal-active.png` | My Work page with action items |
| 14.8 | `day-14-trust-deposit-ledger-posted.png` | Trust deposit + client ledger balance |
| 14.15 | `day-14-conflict-match-found.png` | Conflict check adverse party match |
| 14.24 | `day-14-trust-dashboard-loaded.png` | Trust dashboard overview |
| 30.12 | `day-30-fee-note-tariff-completed.png` | Fee note with tariff + disbursement |
| 30.21 | `day-30-trust-fee-transfer-approved.png` | Trust fee transfer transaction |
| 30.30 | `day-30-fee-note-list-mixed.png` | Fee note list mixed statuses |
| 45.8 | `day-45-reconciliation-balanced.png` | Bank reconciliation 3-way balance |
| 45.13 | `day-45-prescription-tracker-active.png` | Prescription days remaining |
| 60.10 | `day-60-interest-lpff-calculated.png` | Interest run with LPFF split |
| 60.17 | `day-60-investment-s86-placed.png` | Investment with §86 selection |
| 60.29 | `day-60-profitability-loaded.png` | Profitability report |
| 75.10 | `day-75-conflict-adverse-detected.png` | Conflict check adverse party stress test |
| 90.7 | `day-90-portfolio-review-loaded.png` | Client + matter + fee note lists |
| 90.11 | `day-90-section-35-generated.png` | Section 35 report |
| 90.15 | `day-90-investment-register-loaded.png` | Investment register with §86 basis |
| 90.20 | `day-90-profitability-dashboard-loaded.png` | Profitability per-matter |
| 90.24 | `day-90-dashboard-overview-active.png` | Dashboard KPIs (hero shot) |
| 90.40 | `day-90-rbac-comparison-loaded.png` | Role-based access comparison |

### Curated Hero Shots (for blog/deck — `documentation/screenshots/legal-vertical/`)

1. `trust-dashboard-overview.png` — Trust Accounting dashboard with balance cards
2. `conflict-check-clear-result.png` — Green "no conflicts found" result
3. `conflict-check-adverse-match.png` — Amber/red adverse party detected
4. `litigation-template-action-items.png` — Matter with 9 pre-populated action items
5. `court-calendar-scheduled.png` — Court calendar with entries
6. `prescription-tracker-days.png` — Prescription days remaining countdown
7. `trust-deposit-form.png` — Trust deposit transaction form
8. `client-ledger-balance.png` — Client ledger card with running balance
9. `fee-note-tariff-disbursement.png` — Complete fee note with LSSA tariff + time + disbursement
10. `trust-fee-transfer.png` — Fee transfer from trust to operating
11. `bank-reconciliation-matched.png` — 3-way reconciliation with auto-matched items
12. `interest-run-lpff-split.png` — Interest wizard showing LPFF calculation
13. `investment-s86-selection.png` — §86(3)/(4) basis selection dialog
14. `section-35-report.png` — Section 35 audit data pack
15. `profitability-per-matter.png` — Matter profitability comparison
16. `role-comparison-carol-alice.png` — Candidate attorney vs senior partner access

---

## Known Limitations (Not Testable in E2E Stack)

| Item | Reason |
|------|--------|
| KYC verification (VerifyNow) | BYOAK adapter is no-op in E2E — verify UI button/dialog exists but actual verification won't execute |
| Payment links (PayFast) | PSP is no-op in E2E — verify fee note has no payment link (expected) |
| Bank CSV auto-matching accuracy | Need to create a test CSV file matching Standard Bank format — auto-matching depends on reference string quality |
| Section 35 PDF generation | Depends on Phase 12 PDF pipeline + trust report data — verify if wired up end-to-end |
| LPFF actual rate | Using test rate (6.5%) — actual LPFF rate changes quarterly |
| Investment maturity alerts | 90-day test window may not trigger maturity notifications — verify alert configuration exists |
| Trust dual-approval mode | Only 1 owner in E2E seed (Alice) — dual approval requires 2 approvers configured |

---

## Failure Reporting

For each failure, note:
```
Step: [number]
Expected: [what should happen]
Actual: [what happened]
Screenshot: [filename or path]
URL: [page URL when failure occurred]
Console errors: [any JS errors in browser console]
Terminology issue: [yes/no — was a generic term visible instead of legal term?]
```
