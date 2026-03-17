# Document Content Verification Lifecycle Script
## Phase 49 — Thornton & Associates
### Platform: DocTeams | Vertical Profile: accounting-za | E2E Stack: http://localhost:3001

This script verifies that the **content produced by document templates is correct** — that
template variables resolve to real data, clauses assemble properly, custom fields flow into
documents, and the full document lifecycle (author, render, deliver, accept) produces accurate,
professional output.

**Prereqs**: Phase 48 lifecycle (90-day seed) must complete first. This script runs it as Day 0,
then layers content verification on top of the seeded data.

**Stack**: `bash compose/scripts/e2e-up.sh` running. Login at `http://localhost:3001/mock-login`.
Backend on 8081. Mailpit on 8026.

**Roles:**
- Alice (Owner) — Senior Accountant/Partner
- Bob (Admin) — Bookkeeper/Practice Administrator
- Carol (Member) — Junior Accountant

**Org**: e2e-test-org (renamed to "Thornton & Associates" during Day 0 seed)

**Clients (created during Day 0 seed):**
- Kgosi Construction (Pty) Ltd — retainer, R5,500/month
- Naledi Hair Studio — hourly, sole proprietor
- Vukani Tech Solutions (Pty) Ltd — retainer + advisory
- Moroka Family Trust — fixed fee, trust entity

**Template pack** (7 templates from `accounting-za`):

| # | Template Name | Slug | Scope | Clauses |
|---|---------------|------|-------|---------|
| 1 | Engagement Letter -- Monthly Bookkeeping | `engagement-letter-bookkeeping` | PROJECT | 7 (4 required, 3 optional) |
| 2 | Engagement Letter -- Annual Tax Return | `engagement-letter-tax-return` | PROJECT | 5 (3 required, 2 optional) |
| 3 | Engagement Letter -- Advisory | `engagement-letter-advisory` | PROJECT | 4 (2 required, 2 optional) |
| 4 | Monthly Report Cover | `monthly-report-cover` | PROJECT | 0 |
| 5 | SA Tax Invoice | `invoice-za` | INVOICE | 0 |
| 6 | Statement of Account | `statement-of-account` | CUSTOMER | 0 |
| 7 | FICA Confirmation Letter | `fica-confirmation` | CUSTOMER | 0 |

**Checkpoint notation:**
- [ ] PASS -- content is correct and complete
- [ ] FAIL -- content is wrong, missing, or has unresolved variables
- [ ] PARTIAL -- content is mostly correct but has minor issues (note specifics)
- [ ] BLANK -- variable resolved to empty string where a value was expected
- [ ] UNRESOLVED -- raw `{{variable}}` syntax visible in output

**PDF artifacts directory:** `qa/testplan/artifacts/`

---

## Day 0 -- Data Preparation (Track 0)

**Context**: Seed the full 90-day firm lifecycle from Phase 48, then do a second pass to
populate custom field values that templates depend on. Without populated custom fields,
every template test in Days 1-8 produces false negatives (blank variables).

---

### Phase 0A: Run 90-Day Lifecycle Seed

Follow the Phase 48 lifecycle script (`tasks/phase48-lifecycle-script.md`) through all days.
This creates the baseline entities (customers, projects, invoices, time entries, retainers,
proposals, FICA checklists, etc.).

**Actor**: Alice/Bob/Carol (as directed by Phase 48 script)

- [ ] **0.1** Day 0 complete: Org settings configured (ZAR, brand colour, rates R1500/R850/R450, cost rates R650/R350/R180, VAT 15%)
    - Actor: Alice | Action: Run Phase 48 Day 0 | Expected: Settings page shows ZAR currency, brand colour, all rates saved
- [ ] **0.2** Day 1 complete: Kgosi Construction created, FICA checklist started, ACTIVE, project + retainer + tasks
    - Actor: Bob/Alice | Action: Run Phase 48 Day 1 | Expected: Customer ACTIVE, project linked, retainer R5,500/month
- [ ] **0.3** Day 2-3 complete: Naledi, Vukani, Moroka created and ACTIVE with projects/tasks
    - Actor: Bob/Alice | Action: Run Phase 48 Days 2-3 | Expected: 4 total customers all ACTIVE, 4+ projects
- [ ] **0.4** Day 7 complete: Time entries logged by Carol, Bob, Alice across projects
    - Actor: Carol/Bob/Alice | Action: Run Phase 48 Day 7 | Expected: Time entries with correct rate snapshots
- [ ] **0.5** Day 14 complete: More time entries, notifications checked
    - Actor: Carol/Bob | Action: Run Phase 48 Day 14 | Expected: 10+ total time entries
- [ ] **0.6** Day 30 complete: Invoices created (hourly + retainer), approved, sent, budgets set
    - Actor: Alice | Action: Run Phase 48 Day 30 | Expected: 2+ invoices (Naledi hourly, Kgosi retainer), VAT 15% applied correctly
- [ ] **0.7** Day 45 complete: Payment recorded, expenses logged, ad-hoc BEE engagement
    - Actor: Alice/Bob | Action: Run Phase 48 Day 45 | Expected: Kgosi Jan invoice PAID, BEE project for Vukani created
- [ ] **0.8** Day 60 complete: Second billing cycle, reports verified
    - Actor: Alice | Action: Run Phase 48 Day 60 | Expected: Feb invoices created, BEE advisory invoice, reports page loads
- [ ] **0.9** Day 75 complete: Year-end project for Kgosi, information request sent
    - Actor: Alice/Carol | Action: Run Phase 48 Day 75 | Expected: "Annual Tax Return 2026 -- Kgosi" project created, info request sent
- [ ] **0.10** Day 90 complete: Portfolio review, profitability, document generation, compliance
    - Actor: Alice | Action: Run Phase 48 Day 90 | Expected: Dashboard shows non-zero KPIs, 4 customers ACTIVE

**Checkpoint**: Verify entity counts before proceeding.

| Entity | Expected | Actual |
|--------|----------|--------|
| Customers | 4 (all ACTIVE) | _____ |
| Projects | 5+ | _____ |
| Invoices | 4+ | _____ |
| Retainers | 2 (Kgosi, Vukani) | _____ |
| Time entries | 10+ | _____ |
| Proposals | 1+ | _____ |

---

### Phase 0B: Populate Custom Field Values -- Kgosi Construction

**Actor**: Bob (Admin)

Navigate to `/org/e2e-test-org/customers` and open Kgosi Construction detail page.
Locate the "SA Accounting -- Client Details" custom field section. If fields were partially
filled during Phase 48 creation, update them to match these exact values. If they are blank,
fill all values now.

**Custom field values for Kgosi** (these are the "source of truth" for all content checks):

| Field slug | Display name | Value |
|------------|-------------|-------|
| `acct_company_registration_number` | Company Registration Number | `2019/123456/07` |
| `trading_as` | Trading As | `Kgosi Construction` |
| `vat_number` | VAT Number | `4520012345` |
| `sars_tax_reference` | SARS Tax Reference | `9012345678` |
| `financial_year_end` | Financial Year-End | `2026-02-28` |
| `acct_entity_type` | Entity Type | `PTY_LTD` (select "Pty Ltd") |
| `registered_address` | Registered Address | `14 Main Road, Sandton, Johannesburg, 2196` |
| `primary_contact_name` | Primary Contact Name | `Thabo Kgosi` |
| `primary_contact_email` | Primary Contact Email | `thabo@kgosiconstruction.co.za` |
| `primary_contact_phone` | Primary Contact Phone | `+27-11-555-0100` |
| `fica_verified` | FICA Verified | `VERIFIED` (select "Verified") |
| `fica_verification_date` | FICA Verification Date | `2026-01-15` |

- [ ] **0.11** Navigate to Kgosi customer detail -> custom fields section
    - Actor: Bob | Action: Open customer detail at `/org/e2e-test-org/customers/{kgosi-id}` | Expected: Custom field section visible with "SA Accounting -- Client Details" group
- [ ] **0.12** Set `acct_company_registration_number` = **2019/123456/07**
    - Actor: Bob | Action: Fill field | Expected: Text field accepts CIPC format
- [ ] **0.13** Set `trading_as` = **Kgosi Construction**
    - Actor: Bob | Action: Fill field | Expected: Text value saved
- [ ] **0.14** Set `vat_number` = **4520012345**
    - Actor: Bob | Action: Fill field | Expected: Text field accepts VAT number
- [ ] **0.15** Set `sars_tax_reference` = **9012345678**
    - Actor: Bob | Action: Fill field | Expected: Text field accepts SARS reference
- [ ] **0.16** Set `financial_year_end` = **2026-02-28**
    - Actor: Bob | Action: Fill date picker | Expected: Date saved and displayed
- [ ] **0.17** Set `acct_entity_type` = **PTY_LTD** (select "Pty Ltd" from dropdown)
    - Actor: Bob | Action: Select dropdown option | Expected: Dropdown shows "Pty Ltd"
- [ ] **0.18** Set `registered_address` = **14 Main Road, Sandton, Johannesburg, 2196**
    - Actor: Bob | Action: Fill field | Expected: Full address saved
- [ ] **0.19** Set `primary_contact_name` = **Thabo Kgosi**
    - Actor: Bob | Action: Fill field | Expected: Contact name saved
- [ ] **0.20** Set `primary_contact_email` = **thabo@kgosiconstruction.co.za**
    - Actor: Bob | Action: Fill field | Expected: Email saved
- [ ] **0.21** Set `primary_contact_phone` = **+27-11-555-0100**
    - Actor: Bob | Action: Fill field | Expected: Phone number saved
- [ ] **0.22** Set `fica_verified` = **VERIFIED** (select "Verified" from dropdown)
    - Actor: Bob | Action: Select dropdown option | Expected: Shows "Verified"
- [ ] **0.23** Set `fica_verification_date` = **2026-01-15**
    - Actor: Bob | Action: Fill date picker | Expected: Date saved
- [ ] **0.24** Save -> reload page -> verify all 12 values persisted
    - Actor: Bob | Action: Save, then refresh page | Expected: All field values still displayed after reload

---

### Phase 0C: Populate Custom Field Values -- Naledi Hair Studio

**Actor**: Bob (Admin)

Naledi is a sole proprietor. Leave `company_registration_number` and `vat_number` **intentionally blank** --
she does not have these. This sets up the "missing field behaviour" test in Day 1.

| Field slug | Value |
|------------|-------|
| `sars_tax_reference` | `8801015001082` |
| `acct_entity_type` | `SOLE_PROPRIETOR` (select "Sole Proprietor") |
| `financial_year_end` | `2026-02-28` |
| `primary_contact_name` | `Naledi Molefe` |
| `primary_contact_email` | `naledi@naledihair.co.za` |
| `fica_verified` | `VERIFIED` |
| `fica_verification_date` | `2026-01-16` |
| `acct_company_registration_number` | _(leave blank)_ |
| `vat_number` | _(leave blank)_ |

- [ ] **0.25** Navigate to Naledi customer detail -> custom fields
    - Actor: Bob | Action: Open customer detail | Expected: Custom field section visible
- [ ] **0.26** Set `sars_tax_reference` = **8801015001082**
    - Actor: Bob | Action: Fill field | Expected: ID number accepted as SARS reference
- [ ] **0.27** Set `acct_entity_type` = **SOLE_PROPRIETOR**
    - Actor: Bob | Action: Select dropdown | Expected: "Sole Proprietor" selected
- [ ] **0.28** Set `financial_year_end` = **2026-02-28**
    - Actor: Bob | Action: Fill date picker | Expected: Date saved
- [ ] **0.29** Set `primary_contact_name` = **Naledi Molefe**
    - Actor: Bob | Action: Fill field | Expected: Text saved
- [ ] **0.30** Set `primary_contact_email` = **naledi@naledihair.co.za**
    - Actor: Bob | Action: Fill field | Expected: Email saved
- [ ] **0.31** Set `fica_verified` = **VERIFIED**
    - Actor: Bob | Action: Select dropdown | Expected: "Verified" selected
- [ ] **0.32** Set `fica_verification_date` = **2026-01-16**
    - Actor: Bob | Action: Fill date picker | Expected: Date saved
- [ ] **0.33** Confirm `acct_company_registration_number` and `vat_number` are blank
    - Actor: Bob | Action: Verify fields are empty | Expected: Both fields show empty/no value
- [ ] **0.34** Save -> verify persisted
    - Actor: Bob | Action: Save and reload | Expected: 7 values populated, 2 intentionally blank

---

### Phase 0D: Populate Custom Field Values -- Vukani Tech Solutions

**Actor**: Bob (Admin)

| Field slug | Value |
|------------|-------|
| `acct_company_registration_number` | `2021/654321/07` |
| `vat_number` | `4520098765` |
| `sars_tax_reference` | `9087654321` |
| `acct_entity_type` | `PTY_LTD` |
| `financial_year_end` | `2026-06-30` |
| `registered_address` | `88 Rivonia Blvd, Rivonia, Johannesburg, 2128` |
| `primary_contact_name` | `Sipho Ndlovu` |
| `primary_contact_email` | `finance@vukanitech.co.za` |
| `fica_verified` | `VERIFIED` |
| `fica_verification_date` | `2026-01-17` |

- [ ] **0.35** Navigate to Vukani customer detail -> custom fields
    - Actor: Bob | Action: Open customer detail | Expected: Custom field section visible
- [ ] **0.36** Set `acct_company_registration_number` = **2021/654321/07**
    - Actor: Bob | Action: Fill field | Expected: Text saved
- [ ] **0.37** Set `vat_number` = **4520098765**
    - Actor: Bob | Action: Fill field | Expected: Text saved
- [ ] **0.38** Set `sars_tax_reference` = **9087654321**
    - Actor: Bob | Action: Fill field | Expected: Text saved
- [ ] **0.39** Set `acct_entity_type` = **PTY_LTD**
    - Actor: Bob | Action: Select dropdown | Expected: "Pty Ltd" selected
- [ ] **0.40** Set `financial_year_end` = **2026-06-30**
    - Actor: Bob | Action: Fill date picker | Expected: Date saved (note: June year-end, different from other clients)
- [ ] **0.41** Set `registered_address` = **88 Rivonia Blvd, Rivonia, Johannesburg, 2128**
    - Actor: Bob | Action: Fill field | Expected: Text saved
- [ ] **0.42** Set `primary_contact_name` = **Sipho Ndlovu**
    - Actor: Bob | Action: Fill field | Expected: Text saved
- [ ] **0.43** Set `primary_contact_email` = **finance@vukanitech.co.za**
    - Actor: Bob | Action: Fill field | Expected: Text saved
- [ ] **0.44** Set `fica_verified` = **VERIFIED**
    - Actor: Bob | Action: Select dropdown | Expected: "Verified" selected
- [ ] **0.45** Set `fica_verification_date` = **2026-01-17**
    - Actor: Bob | Action: Fill date picker | Expected: Date saved
- [ ] **0.46** Save -> verify persisted
    - Actor: Bob | Action: Save and reload | Expected: All 10 values populated

---

### Phase 0E: Populate Custom Field Values -- Moroka Family Trust

**Actor**: Bob (Admin)

Moroka is a Trust entity. Standard fields plus trust-specific conditional fields.

**Standard fields:**

| Field slug | Value |
|------------|-------|
| `sars_tax_reference` | `1234567890` |
| `acct_entity_type` | `TRUST` (select "Trust") |
| `financial_year_end` | `2026-02-28` |
| `primary_contact_name` | `Samuel Moroka` |
| `primary_contact_email` | `trustees@morokatrust.co.za` |
| `fica_verified` | `VERIFIED` |
| `fica_verification_date` | `2026-01-18` |

**Trust-specific fields** (from `accounting-za-customer-trust` pack -- conditionally visible when Entity Type = TRUST):

| Field slug | Display name | Value |
|------------|-------------|-------|
| `trust_registration_number` | Trust Registration Number | `IT789/2018` |
| `trust_deed_date` | Trust Deed Date | `2018-06-15` |
| `trust_type` | Trust Type | `INTER_VIVOS` (select "Inter Vivos (Living Trust)") |
| `trustee_names` | Names of Trustees | `Samuel Moroka, Grace Moroka, Adv. T. Nkosi` |
| `trustee_type` | Trustee Appointment Type | `APPOINTED` (select "Appointed") |
| `letters_of_authority_date` | Letters of Authority Date | `2018-07-20` |

- [ ] **0.47** Navigate to Moroka customer detail -> custom fields
    - Actor: Bob | Action: Open customer detail | Expected: Custom field section visible
- [ ] **0.48** Set `sars_tax_reference` = **1234567890**
    - Actor: Bob | Action: Fill field | Expected: Text saved
- [ ] **0.49** Set `acct_entity_type` = **TRUST** (select "Trust")
    - Actor: Bob | Action: Select dropdown | Expected: "Trust" selected
- [ ] **0.50** Set `financial_year_end` = **2026-02-28**
    - Actor: Bob | Action: Fill date picker | Expected: Date saved
- [ ] **0.51** Set `primary_contact_name` = **Samuel Moroka**
    - Actor: Bob | Action: Fill field | Expected: Text saved
- [ ] **0.52** Set `primary_contact_email` = **trustees@morokatrust.co.za**
    - Actor: Bob | Action: Fill field | Expected: Text saved
- [ ] **0.53** Set `fica_verified` = **VERIFIED**
    - Actor: Bob | Action: Select dropdown | Expected: "Verified" selected
- [ ] **0.54** Set `fica_verification_date` = **2026-01-18**
    - Actor: Bob | Action: Fill date picker | Expected: Date saved
- [ ] **0.55** Verify trust field group becomes visible after setting Entity Type = TRUST
    - Actor: Bob | Action: Check page for "SA Accounting -- Trust Details" section | Expected: 6 trust-specific fields appear. If NOT visible: log as GAP (conditional field group not showing)
- [ ] **0.56** Set `trust_registration_number` = **IT789/2018**
    - Actor: Bob | Action: Fill field | Expected: Master's Office reference saved
- [ ] **0.57** Set `trust_deed_date` = **2018-06-15**
    - Actor: Bob | Action: Fill date picker | Expected: Date saved
- [ ] **0.58** Set `trust_type` = **INTER_VIVOS** (select "Inter Vivos (Living Trust)")
    - Actor: Bob | Action: Select dropdown | Expected: Trust type selected
- [ ] **0.59** Set `trustee_names` = **Samuel Moroka, Grace Moroka, Adv. T. Nkosi**
    - Actor: Bob | Action: Fill field | Expected: Comma-separated names saved
- [ ] **0.60** Set `trustee_type` = **APPOINTED** (select "Appointed")
    - Actor: Bob | Action: Select dropdown | Expected: "Appointed" selected
- [ ] **0.61** Set `letters_of_authority_date` = **2018-07-20**
    - Actor: Bob | Action: Fill date picker | Expected: Date saved
- [ ] **0.62** Save -> verify all trust fields persisted
    - Actor: Bob | Action: Save and reload | Expected: 7 standard + 6 trust-specific = 13 values populated

---

### Phase 0F: Org Settings for Document Variables

**Actor**: Alice (Owner)

These org-level values appear in template headers/footers via `org.*` variables.

- [ ] **0.63** Navigate to Settings > General (`/org/e2e-test-org/settings/general`)
    - Actor: Alice | Action: Navigate | Expected: General settings page loads
- [ ] **0.64** Set or verify `documentFooterText` = **"Thornton & Associates | Reg. 2015/001234/21 | 14 Loop St, Cape Town 8001"**
    - Actor: Alice | Action: Find footer text field and set value | Expected: Footer text field exists and saves. If field does not exist, log as observation.
- [ ] **0.65** Set or verify `taxRegistrationNumber` = **4510067890** (org's own VAT number for `invoice-za` template)
    - Actor: Alice | Action: Find tax registration field and set value | Expected: Field exists and saves. If not present, log as observation -- the `invoice-za` template will show a blank for `org.taxRegistrationNumber`.
- [ ] **0.66** Verify org `name` = **Thornton & Associates** (should be set from Phase 48 Day 0)
    - Actor: Alice | Action: Check org name field | Expected: "Thornton & Associates" displayed
- [ ] **0.67** Verify `defaultCurrency` = **ZAR**
    - Actor: Alice | Action: Check currency field | Expected: ZAR selected
- [ ] **0.68** Save all changes
    - Actor: Alice | Action: Click save | Expected: Settings persisted

---

### Phase 0G: Project Custom Field Values

**Actor**: Alice (Owner)

Some templates reference `project.customFields.*`. Set these on relevant projects.

- [ ] **0.69** Open Kgosi "Monthly Bookkeeping" project -> custom fields section
    - Actor: Alice | Action: Navigate to project detail | Expected: "SA Accounting -- Engagement Details" field group visible. If not visible, log as observation (project field packs may not auto-apply).
- [ ] **0.70** Set `engagement_type` = **MONTHLY_BOOKKEEPING** (select "Monthly Bookkeeping")
    - Actor: Alice | Action: Select dropdown | Expected: Engagement type saved. This resolves `project.customFields.engagement_type` in the bookkeeping engagement letter.
- [ ] **0.71** Open Kgosi "Annual Tax Return 2026" project -> custom fields
    - Actor: Alice | Action: Navigate to project detail | Expected: Custom fields section visible
- [ ] **0.72** Set `engagement_type` = **ANNUAL_TAX_RETURN** (select "Annual Tax Return")
    - Actor: Alice | Action: Select dropdown | Expected: Engagement type saved
- [ ] **0.73** Set `tax_year` = **2026**
    - Actor: Alice | Action: Fill text field | Expected: Tax year saved. Resolves `project.customFields.tax_year`.
- [ ] **0.74** Set `sars_submission_deadline` = **2026-11-30**
    - Actor: Alice | Action: Fill date picker | Expected: Deadline date saved. Resolves `project.customFields.sars_submission_deadline`.
- [ ] **0.75** If project custom fields don't exist on either project: log which fields are missing as observation
    - Actor: Alice | Action: Note gaps | Expected: Fields present. If absent, the `accounting-za-project` pack has `autoApply: false` so they may need manual application.
- [ ] **0.76** Save all changes on both projects
    - Actor: Alice | Action: Save | Expected: All project custom field values persisted

---

### Phase 0H: Portal Contact Setup

**Actor**: Alice (Owner)

Tracks 6 (e-signing) and 7 (information requests) require portal contacts.

- [ ] **0.77** Navigate to Kgosi customer detail -> Portal / Contacts tab
    - Actor: Alice | Action: Look for portal contacts section | Expected: Tab or section exists
- [ ] **0.78** If no portal contact exists: create one (Name = **Thabo Kgosi**, Email = **thabo@kgosiconstruction.co.za**)
    - Actor: Alice | Action: Create portal contact | Expected: Contact created and active
- [ ] **0.79** Verify portal contact is active and can receive emails
    - Actor: Alice | Action: Check status | Expected: Status shows active
- [ ] **0.80** Navigate to Vukani customer detail -> Portal / Contacts tab
    - Actor: Alice | Action: Open Vukani contacts | Expected: Tab/section exists
- [ ] **0.81** If no portal contact exists: create one (Name = **Sipho Ndlovu**, Email = **finance@vukanitech.co.za**)
    - Actor: Alice | Action: Create portal contact | Expected: Contact created and active

---

### Phase 0I: Data Readiness Checkpoint

> **STOP GATE**: If 0.82 fails (fields blank or not visible), do NOT proceed to Day 1.
> Investigate whether field packs were seeded, whether the vertical profile is applied,
> and whether custom field values are saving correctly. Fix before continuing.

- [ ] **0.82** Open Kgosi customer detail -> verify at least 10 custom field values are visible and non-blank
    - Actor: Bob | Action: Count populated fields | Expected: 12+ fields populated with the values from Phase 0B
- [ ] **0.83** Open Moroka customer detail -> verify trust fields are visible and populated
    - Actor: Bob | Action: Check trust-specific fields | Expected: Trust Registration Number, Trust Deed Date, Trust Type, Trustee Names all show non-blank values
- [ ] **0.84** Navigate to Invoices (`/org/e2e-test-org/invoices`) -> verify at least 2 invoices exist with line items
    - Actor: Bob | Action: Check invoice list | Expected: 2+ invoices visible (Naledi hourly, Kgosi retainer, possibly more)
- [ ] **0.85** Navigate to Projects (`/org/e2e-test-org/projects`) -> verify at least 4 projects exist
    - Actor: Bob | Action: Check project list | Expected: 4+ projects (Monthly Bookkeeping x3, Annual Administration, possibly BEE Review)
- [ ] **0.86** Navigate to Settings > General -> verify org name = "Thornton & Associates", currency = ZAR, footer text set
    - Actor: Bob | Action: Check settings | Expected: All three values present and correct

**STOP GATE**: If 0.82 fails, STOP. Fix seed data before proceeding.

---

## Day 1 -- Custom Field to Document Flow (Track 5)

**Context**: Before running all 8 templates, verify that the data chain works end-to-end:
field pack definition -> customer creation -> field value storage -> template variable context -> rendered document.
If this fails, every template test will show the same failure -- so catch it here first.

> **STOP GATE**: If field values do not appear in generated documents at all (1.1-1.6 all FAIL),
> STOP before running the 8 template tests in Days 2-3. Investigate the rendering pipeline.

**Actor**: Alice (Owner)

---

### 1.1 -- Standard Custom Field Flow (Kgosi)

- [ ] **1.1** Open Kgosi customer detail -> verify custom fields section shows accounting-za fields
    - Actor: Alice | Action: Navigate to Kgosi detail | Expected: "SA Accounting -- Client Details" group visible with populated values
- [ ] **1.2** Verify these specific field values are populated and match Day 0 seed:
    - Company Registration Number = "2019/123456/07"
    - VAT Number = "4520012345"
    - SARS Tax Reference = "9012345678"
    - Entity Type = "PTY_LTD" (or display label "Pty Ltd")
    - Financial Year-End = "2026-02-28" (or formatted display)
    - Actor: Alice | Action: Read field values | Expected: All 5 values match exactly
- [ ] **1.3** Generate `engagement-letter-bookkeeping` for Kgosi's Monthly Bookkeeping project:
    - Navigate to project detail -> click "Generate Document" dropdown -> select "Engagement Letter -- Monthly Bookkeeping"
    - In the clause selection step, accept defaults (all clauses)
    - Click to generate/preview
    - Actor: Alice | Action: Generate document via GenerateDocumentDropdown | Expected: HTML preview loads in dialog
- [ ] **1.4** In the HTML preview output: verify `customer.customFields.company_registration_number` resolves to "2019/123456/07"
    - Actor: Alice | Action: Search preview for "Registration Number" and its value | Expected: Shows "2019/123456/07" after the "Registration Number:" label
- [ ] **1.5** In the HTML preview: verify `customer.name` resolves to "Kgosi Construction (Pty) Ltd"
    - Actor: Alice | Action: Search preview for client name | Expected: "Kgosi Construction (Pty) Ltd" appears in salutation and client details section
- [ ] **1.6** In the HTML preview: verify `org.name` resolves (not blank, not `{{org.name}}`)
    - Actor: Alice | Action: Search preview for firm name | Expected: "Thornton & Associates" appears in header and intro

**Verdict**: If 1.4, 1.5, and 1.6 all PASS, the data chain works. Proceed to Day 2.

---

### 1.2 -- FICA Verification Date Flow

- [ ] **1.7** Check Kgosi's `fica_verification_date` field on customer detail -- is it populated?
    - Actor: Alice | Action: Check field value | Expected: Shows "2026-01-15"
- [ ] **1.8** Generate `fica-confirmation` for Kgosi (customer-scoped): customer detail -> Generate Document -> select "FICA Confirmation Letter"
    - Actor: Alice | Action: Generate document | Expected: HTML preview loads
- [ ] **1.9** In output: verify `customer.customFields.fica_verification_date` resolves to a date, not blank
    - Actor: Alice | Action: Search for "Verification Date" in preview | Expected: Shows "2026-01-15" or formatted equivalent (e.g., "15 January 2026")
- [ ] **1.10** If fica_verification_date is blank in output but populated on customer: log as GAP (field not mapped to FICA template context)
    - Actor: Alice | Action: Compare | Expected: Value matches

---

### 1.3 -- Conditional Fields: Trust Entity Type (Moroka)

- [ ] **1.11** Open Moroka Family Trust customer detail
    - Actor: Alice | Action: Navigate | Expected: Customer detail loads
- [ ] **1.12** Verify Entity Type = "TRUST" and trust-specific fields are visible:
    - Trust Registration Number = "IT789/2018"
    - Trust Deed Date = "2018-06-15"
    - Trust Type = "INTER_VIVOS" (or "Inter Vivos (Living Trust)")
    - Names of Trustees = "Samuel Moroka, Grace Moroka, Adv. T. Nkosi"
    - Actor: Alice | Action: Read values | Expected: All 4 values match
- [ ] **1.13** Generate `fica-confirmation` for Moroka -> verify customer name = "Moroka Family Trust"
    - Actor: Alice | Action: Generate document | Expected: "Moroka Family Trust" in salutation and client details, not "Kgosi" or any other customer
- [ ] **1.14** Check if trust-specific fields appear in the generated document (they may not -- depends on whether the template references trust variables)
    - Actor: Alice | Action: Search output | Expected: Note which trust fields appear vs don't. This is informational -- the FICA confirmation template may not include trust-specific fields.

---

### 1.4 -- Non-Trust Customer: Conditional Fields Hidden

- [ ] **1.15** Open Naledi Hair Studio customer detail
    - Actor: Alice | Action: Navigate | Expected: Customer detail loads
- [ ] **1.16** Verify Entity Type = "SOLE_PROPRIETOR" (or "Sole Proprietor")
    - Actor: Alice | Action: Check dropdown value | Expected: "Sole Proprietor" shown
- [ ] **1.17** Verify trust-specific fields are NOT shown (Trust Registration Number, Trust Deed Date, etc.)
    - Actor: Alice | Action: Search page for trust fields | Expected: Trust fields hidden. If visible for a sole proprietor: log as GAP (conditional visibility broken).

---

### 1.5 -- Invoice Custom Field Flow

- [ ] **1.18** Navigate to Invoices -> open an existing Kgosi invoice -> Generate Document -> select "SA Tax Invoice" (`invoice-za`)
    - Actor: Alice | Action: Navigate to invoice detail, click Generate Document | Expected: Invoice template available in dropdown for invoice entity type
- [ ] **1.19** In the HTML preview: verify `customerVatNumber` = "4520012345"
    - Actor: Alice | Action: Search for "Client VAT Number" in preview | Expected: Shows "4520012345". If blank but custom field has value: log as GAP (field not mapped to invoice context builder).
- [ ] **1.20** Verify `org.taxRegistrationNumber` resolves in the "From" section
    - Actor: Alice | Action: Search for "VAT Registration No" in preview | Expected: Shows "4510067890" if set in Phase 0F. If blank, log observation.

---

### 1.6 -- Missing Required Field Behaviour

**Goal**: When a template uses a variable but the entity doesn't have a value for it, what happens?

**Setup**: Naledi Hair Studio has `company_registration_number` and `vat_number` intentionally blank.

- [ ] **1.21** Navigate to Naledi's project -> Generate Document -> select `engagement-letter-bookkeeping`
    - Actor: Alice | Action: Generate document | Expected: Generation starts (or shows pre-generation warning)
- [ ] **1.22** **Pre-generation check**: Does the system show a warning that `company_registration_number` is missing?
    - Actor: Alice | Action: Observe dialog behaviour | Expected: Note which behaviour occurs:
      - (a) Warning shown with option to proceed or fill in field -> **PASS** (ideal)
      - (b) No warning, generation proceeds silently -> note, continue to 1.23
- [ ] **1.23** Check the generated output for the registration number field:
    - Actor: Alice | Action: Search for "Registration Number:" in preview | Expected: Note which behaviour occurs:
      - (a) Field line is hidden/omitted entirely -> **PASS** (conditional rendering)
      - (b) Shows "Registration Number: " with blank value after colon -> **FAIL** (ugly output)
      - (c) Shows "Registration Number: N/A" or similar placeholder -> **PASS** (graceful fallback)
      - (d) Shows literal `{{customer.customFields.company_registration_number}}` -> **FAIL** (unresolved variable)
- [ ] **1.24** Check the VAT number field in the same document -- same criteria as 1.23
    - Actor: Alice | Action: Search for "VAT" label in Naledi document | Expected: Same assessment as above
- [ ] **1.25** Generate `invoice-za` for a Naledi invoice -> check `customerVatNumber` field:
    - Actor: Alice | Action: Generate invoice template | Expected: Naledi is not VAT-registered, so this should be blank. SA tax invoices from non-VAT vendors shouldn't show a VAT registration field at all. Note behaviour.
- [ ] **1.26** Compare the Naledi document side-by-side with the Kgosi document (same template). The Kgosi version should have populated fields; the Naledi version should handle blanks without looking broken.
    - Actor: Alice | Action: Visual comparison | Expected: Kgosi has all fields filled; Naledi handles missing fields gracefully (no dangling labels, no unresolved tokens)

---

## Day 2-3 -- Template Variable Fidelity (Track 1)

**Context**: Core of this verification plan. Generate each of the 7 accounting-za templates
and check every variable resolves correctly. Each template gets its own sub-section with
content checklists for both HTML preview and PDF download.

**Actor**: Alice (Owner)

**PDF download directory**: Save all PDFs to `qa/testplan/artifacts/` for later analysis.

---

### Template 1: Engagement Letter -- Monthly Bookkeeping

**Slug**: `engagement-letter-bookkeeping`
**Generate from**: Kgosi "Monthly Bookkeeping" project detail -> Generate Document dropdown
**Associated clauses**: 7 total (4 required: Limitation of Liability, Termination, Confidentiality, Document Retention; 3 optional: Fee Escalation, Third-Party Reliance, Electronic Communication Consent)

**HTML Preview checks:**

- [ ] **2.1** `org.name` resolves in header, intro paragraph, and signature block -- not blank, not `{{org.name}}`
    - Actor: Alice | Action: Search preview for org name | Expected: "Thornton & Associates" appears in multiple locations
- [ ] **2.2** `customer.name` = "Kgosi Construction (Pty) Ltd" in salutation ("Dear ..."), client details section, and responsibilities
    - Actor: Alice | Action: Search preview for customer name | Expected: Exact match in 3+ locations
- [ ] **2.3** `customer.customFields.company_registration_number` = "2019/123456/07" -- appears after "Registration Number:" label
    - Actor: Alice | Action: Search for registration number | Expected: "2019/123456/07"
- [ ] **2.4** `project.customFields.engagement_type` resolves (should show "Monthly Bookkeeping" or similar)
    - Actor: Alice | Action: Search for engagement type value | Expected: Non-blank value. If blank, note whether project custom field was set in Phase 0G.
- [ ] **2.5** `org.documentFooterText` resolves in footer section
    - Actor: Alice | Action: Check bottom of preview for footer | Expected: "Thornton & Associates | Reg. 2015/001234/21 | 14 Loop St, Cape Town 8001" or similar. May be blank if field doesn't exist -- log observation.
- [ ] **2.6** No unresolved variables: search page content for `{{` -- expect zero matches
    - Actor: Alice | Action: Search HTML for `{{` | Expected: Zero occurrences
- [ ] **2.7** Currency references use ZAR / R symbol, not USD / $
    - Actor: Alice | Action: Search for currency symbols | Expected: R or ZAR only, no $ or USD

**PDF Download checks:**

- [ ] **2.8** Click "Download PDF" in the generation dialog -> save to `qa/testplan/artifacts/t1.1-engagement-letter-kgosi.pdf`
    - Actor: Alice | Action: Download PDF | Expected: Non-zero file downloads
- [ ] **2.9** PDF contains "Kgosi Construction (Pty) Ltd"
    - Actor: Alice | Action: Read PDF content | Expected: Customer name present
- [ ] **2.10** PDF contains org name "Thornton & Associates" in header
    - Actor: Alice | Action: Read PDF content | Expected: Org name present
- [ ] **2.11** PDF contains "2019/123456/07" (company registration number)
    - Actor: Alice | Action: Read PDF content | Expected: Registration number present
- [ ] **2.12** PDF does NOT contain literal `{{` or `}}` characters
    - Actor: Alice | Action: Search PDF text | Expected: Zero occurrences of `{{` or `}}`
- [ ] **2.13** All 4 required clauses present in PDF: "Limitation of Liability", "Termination", "Confidentiality", "Document Retention"
    - Actor: Alice | Action: Search PDF for clause titles | Expected: All 4 clause title strings found

**PDF Verification Prompt** (for AI agent reading the downloaded PDF):
```
Analyse this PDF document and check for the following:

1. UNRESOLVED VARIABLES: Search for any occurrence of "{{" or "}}". Report each one
   with the surrounding text (10 words before and after). Expected: zero occurrences.

2. BLANK FIELDS: Look for patterns that suggest empty variable resolution:
   - Two consecutive colons ("::") or colon followed by whitespace and nothing
   - Empty table cells where data is expected
   - Sentences that read unnaturally due to missing words
   Report each with location description.

3. CURRENCY FORMAT: Check all monetary amounts. They should use:
   - ZAR or "R" prefix (not USD, $, EUR, or GBP)
   - Thousands separator (space or comma) for amounts > 999
   - Two decimal places for amounts with cents
   Report any non-ZAR currency references.

4. DATE FORMAT: Check all dates. They should be human-readable
   (not raw ISO 8601 like "2026-02-28T00:00:00Z" or epoch timestamps).
   Report any machine-formatted dates.

5. CUSTOMER DATA MATCH: The document should reference:
   - Customer: "Kgosi Construction (Pty) Ltd"
   - Registration Number: "2019/123456/07"
   Report if the customer name is wrong or absent.

6. ORG DATA: The document should reference "Thornton & Associates" in header or footer.
   Report if absent.

Report findings as:
- PASS: [description of check]
- FAIL: [description] -- found: "[actual text]", expected: "[expected text]"
- WARN: [description] -- [observation]
```

---

### Template 2: Engagement Letter -- Annual Tax Return

**Slug**: `engagement-letter-tax-return`
**Generate from**: Kgosi "Annual Tax Return 2026" project detail -> Generate Document
**Associated clauses**: 5 (3 required: Limitation of Liability, Termination, Confidentiality; 2 optional: Document Retention, Third-Party Reliance)

- [ ] **2.14** Generate document, accept default clause selection
    - Actor: Alice | Action: Generate from project detail | Expected: HTML preview loads
- [ ] **2.15** `org.name` resolves in header and closing
    - Actor: Alice | Action: Search preview | Expected: "Thornton & Associates"
- [ ] **2.16** `customer.name` = "Kgosi Construction (Pty) Ltd"
    - Actor: Alice | Action: Search preview | Expected: Exact match
- [ ] **2.17** `customer.customFields.sars_tax_reference` = "9012345678" -- appears in SARS reference field
    - Actor: Alice | Action: Search for SARS reference | Expected: "9012345678"
- [ ] **2.18** `project.customFields.tax_year` resolves (should show "2026" if set in Phase 0G)
    - Actor: Alice | Action: Search for tax year | Expected: "2026" or blank (note if blank)
- [ ] **2.19** `project.customFields.sars_submission_deadline` resolves (should show "2026-11-30" or formatted)
    - Actor: Alice | Action: Search for deadline | Expected: Date value or blank (note)
- [ ] **2.20** No unresolved `{{` in output
    - Actor: Alice | Action: Search for `{{` | Expected: Zero occurrences
- [ ] **2.21** All 5 associated clauses present: Limitation of Liability, Termination, Confidentiality, Third-Party Reliance, Document Retention
    - Actor: Alice | Action: Search for clause titles | Expected: All 5 found
- [ ] **2.22** Download PDF to `qa/testplan/artifacts/t1.2-engagement-letter-tax-return-kgosi.pdf`
    - Actor: Alice | Action: Download | Expected: Non-zero file

---

### Template 3: Engagement Letter -- Advisory

**Slug**: `engagement-letter-advisory`
**Generate from**: Vukani "BEE Certificate Review" project detail -> Generate Document
**Associated clauses**: 4 (2 required: Limitation of Liability, Confidentiality; 2 optional: Termination, Third-Party Reliance)

- [ ] **2.23** Generate document
    - Actor: Alice | Action: Generate from Vukani BEE project | Expected: HTML preview loads
- [ ] **2.24** `org.name` resolves -- "Thornton & Associates"
    - Actor: Alice | Action: Search preview | Expected: Org name present
- [ ] **2.25** `customer.name` = "Vukani Tech Solutions (Pty) Ltd"
    - Actor: Alice | Action: Search preview | Expected: Exact match (not Kgosi, not blank)
- [ ] **2.26** `project.name` = "BEE Certificate Review -- Vukani" (or however project was named during Phase 48)
    - Actor: Alice | Action: Search preview | Expected: Project name matches advisory subject matter
- [ ] **2.27** `org.documentFooterText` resolves in footer
    - Actor: Alice | Action: Check footer area | Expected: Footer text present (or noted as observation if field doesn't exist)
- [ ] **2.28** No unresolved `{{`
    - Actor: Alice | Action: Search for `{{` | Expected: Zero occurrences
- [ ] **2.29** All 4 associated clauses present: Limitation of Liability, Confidentiality, Termination, Third-Party Reliance
    - Actor: Alice | Action: Search for clause titles | Expected: All 4 found
- [ ] **2.30** Download PDF to `qa/testplan/artifacts/t1.3-engagement-letter-advisory-vukani.pdf`
    - Actor: Alice | Action: Download | Expected: Non-zero file

---

### Template 4: Monthly Report Cover

**Slug**: `monthly-report-cover`
**Generate from**: Kgosi "Monthly Bookkeeping" project detail -> Generate Document
**No clauses** (cover page only)

- [ ] **2.31** Generate document
    - Actor: Alice | Action: Generate from project | Expected: HTML preview loads
- [ ] **2.32** `org.name` appears in header
    - Actor: Alice | Action: Search preview | Expected: "Thornton & Associates"
- [ ] **2.33** `customer.name` = "Kgosi Construction (Pty) Ltd" in "Prepared For" field
    - Actor: Alice | Action: Search for customer name | Expected: Exact match
- [ ] **2.34** `project.name` = "Monthly Bookkeeping -- Kgosi" (or actual project name) in project field
    - Actor: Alice | Action: Search for project name | Expected: Project name present
- [ ] **2.35** `generatedAt` shows today's date (not blank, not epoch, not `{{generatedAt}}`)
    - Actor: Alice | Action: Search for date | Expected: Human-readable date (e.g., "17 March 2026")
- [ ] **2.36** Date format is human-readable (not raw ISO timestamp like "2026-03-17T00:00:00Z")
    - Actor: Alice | Action: Check date format | Expected: Formatted date
- [ ] **2.37** No unresolved `{{`
    - Actor: Alice | Action: Search for `{{` | Expected: Zero occurrences
- [ ] **2.38** Download PDF to `qa/testplan/artifacts/t1.4-monthly-report-cover-kgosi.pdf`
    - Actor: Alice | Action: Download | Expected: Non-zero file

---

### Template 5: SA Tax Invoice

**Slug**: `invoice-za`
**Generate from**: An existing Kgosi invoice (January retainer invoice) -> Generate Document
**Variables**: `org.name`, `org.taxRegistrationNumber`, `customer.name`, `customerVatNumber`, `invoice.invoiceNumber`, `invoice.issueDate`, `invoice.dueDate`, `invoice.currency`, `invoice.subtotal`, `invoice.taxAmount`, `invoice.total`, line items table

- [ ] **2.39** Navigate to Invoices -> open Kgosi January retainer invoice -> Generate Document -> select "SA Tax Invoice"
    - Actor: Alice | Action: Navigate to invoice, generate | Expected: HTML preview loads
- [ ] **2.40** `org.name` in issuer/"From" section
    - Actor: Alice | Action: Search preview | Expected: "Thornton & Associates"
- [ ] **2.41** `org.taxRegistrationNumber` resolves after "VAT Registration No:" label
    - Actor: Alice | Action: Search for VAT reg | Expected: "4510067890" or blank (log observation if blank)
- [ ] **2.42** `customer.name` = "Kgosi Construction (Pty) Ltd" in "To" section
    - Actor: Alice | Action: Search preview | Expected: Exact match
- [ ] **2.43** `customerVatNumber` = "4520012345" after "Client VAT Number:" label
    - Actor: Alice | Action: Search preview | Expected: "4520012345"
- [ ] **2.44** `invoice.invoiceNumber` resolves to a sequential number (e.g., "INV-001" or similar pattern)
    - Actor: Alice | Action: Check invoice number | Expected: Non-blank, formatted number
- [ ] **2.45** `invoice.issueDate` shows a human-readable date
    - Actor: Alice | Action: Check issue date | Expected: Formatted date, not ISO
- [ ] **2.46** `invoice.dueDate` shows a human-readable date after the issue date
    - Actor: Alice | Action: Check due date | Expected: Due date > issue date
- [ ] **2.47** `invoice.currency` = "ZAR"
    - Actor: Alice | Action: Check currency | Expected: "ZAR"
- [ ] **2.48** `invoice.subtotal` is a numeric amount matching expected (R5,500 for retainer)
    - Actor: Alice | Action: Check subtotal | Expected: Matches retainer amount
- [ ] **2.49** `invoice.taxAmount` = 15% of subtotal
    - Actor: Alice | Action: Math check | Expected: Subtotal x 0.15
- [ ] **2.50** `invoice.total` = subtotal + tax
    - Actor: Alice | Action: Math check | Expected: Subtotal + tax amount
- [ ] **2.51** Line items table renders with columns: description, quantity, unit price, tax, line total
    - Actor: Alice | Action: Check table structure | Expected: At least 1 row with all columns populated
- [ ] **2.52** Line item amounts are numeric (not NaN, not blank, not `{{}}`)
    - Actor: Alice | Action: Check values | Expected: All numeric, non-empty
- [ ] **2.53** Math check: line totals sum to subtotal; subtotal + tax = total
    - Actor: Alice | Action: Arithmetic verification | Expected: All math correct
- [ ] **2.54** Currency symbol is R or ZAR throughout, no $ or USD
    - Actor: Alice | Action: Scan for currency symbols | Expected: Only R/ZAR
- [ ] **2.55** Download PDF to `qa/testplan/artifacts/t1.5-invoice-za-kgosi.pdf`
    - Actor: Alice | Action: Download | Expected: Non-zero file

**Invoice-Specific PDF Verification Prompt** (for AI agent):
```
Analyse this invoice PDF and perform these additional checks:

1. MATH VERIFICATION:
   - For each line item: quantity x unit price should equal line total
   - Sum of all line totals should equal the subtotal
   - Tax amount should be 15% of subtotal (SA VAT rate)
   - Total should be subtotal + tax amount
   Report any arithmetic errors with expected vs actual.

2. INVOICE NUMBER: Should be present and match pattern (e.g., INV-001 or similar).
   Report if missing or malformed.

3. DATES: Issue date and due date should both be present.
   Due date should be after issue date.

4. LINE ITEM TABLE: Should have at least one row with:
   - Description (non-empty text)
   - Quantity (positive number)
   - Unit price (positive number in ZAR)
   - Line total (positive number)
   Report empty or malformed rows.
```

---

### Template 6: Statement of Account

**Slug**: `statement-of-account`
**Generate from**: Kgosi Construction customer detail -> Generate Document
**Scope**: CUSTOMER (not project or invoice)

- [ ] **2.56** Navigate to Kgosi customer detail -> Generate Document -> select "Statement of Account"
    - Actor: Alice | Action: Generate from customer detail | Expected: HTML preview loads
- [ ] **2.57** `org.name` in header and closing
    - Actor: Alice | Action: Search preview | Expected: "Thornton & Associates"
- [ ] **2.58** `customer.name` = "Kgosi Construction (Pty) Ltd"
    - Actor: Alice | Action: Search preview | Expected: Exact match
- [ ] **2.59** `generatedAt` shows today's date, human-readable
    - Actor: Alice | Action: Check date | Expected: Formatted date
- [ ] **2.60** `totalOutstanding` is a numeric amount (may be R0.00 if all paid -- note actual value)
    - Actor: Alice | Action: Check balance | Expected: Numeric value in ZAR
- [ ] **2.61** Invoice history table renders with columns: invoice number, issue date, due date, total, currency, status
    - Actor: Alice | Action: Check table | Expected: Table with headers and at least 1 row
- [ ] **2.62** At least 1 invoice row in the table (Kgosi has invoices from Phase 48 seed)
    - Actor: Alice | Action: Count rows | Expected: 1+ rows
- [ ] **2.63** Invoice numbers in table match actual invoice numbers in the system
    - Actor: Alice | Action: Cross-reference | Expected: Numbers match
- [ ] **2.64** Status column shows correct values (PAID, SENT, etc.)
    - Actor: Alice | Action: Check statuses | Expected: Statuses match invoice list page
- [ ] **2.65** No unresolved `{{`
    - Actor: Alice | Action: Search for `{{` | Expected: Zero occurrences
- [ ] **2.66** Download PDF to `qa/testplan/artifacts/t1.6-statement-of-account-kgosi.pdf`
    - Actor: Alice | Action: Download | Expected: Non-zero file

---

### Template 7: FICA Confirmation Letter

**Slug**: `fica-confirmation`
**Generate from**: Kgosi Construction customer detail -> Generate Document
**Scope**: CUSTOMER

- [ ] **2.67** Navigate to Kgosi customer detail -> Generate Document -> select "FICA Confirmation Letter"
    - Actor: Alice | Action: Generate from customer detail | Expected: HTML preview loads
- [ ] **2.68** `org.name` appears in header, intro paragraph, and signature/footer (3+ occurrences)
    - Actor: Alice | Action: Count org name occurrences | Expected: 3+ occurrences of "Thornton & Associates"
- [ ] **2.69** `customer.name` = "Kgosi Construction (Pty) Ltd" appears in salutation, verification details, and closing (3x)
    - Actor: Alice | Action: Count customer name occurrences | Expected: 3 occurrences
- [ ] **2.70** `customer.customFields.fica_verification_date` resolves to a date (should be "2026-01-15" or formatted equivalent)
    - Actor: Alice | Action: Search for verification date | Expected: Non-blank date value
- [ ] **2.71** If fica_verification_date is blank: log as GAP (field should be populated from Phase 0B)
    - Actor: Alice | Action: Assess | Expected: Date present
- [ ] **2.72** No unresolved `{{`
    - Actor: Alice | Action: Search for `{{` | Expected: Zero occurrences
- [ ] **2.73** Download PDF to `qa/testplan/artifacts/t1.7-fica-confirmation-kgosi.pdf`
    - Actor: Alice | Action: Download | Expected: Non-zero file

---

### Cross-Customer Isolation Check

**Goal**: Generate the same template for two different customers and verify data does not leak.

- [ ] **2.74** Generate `statement-of-account` for Kgosi -> note customer name in output
    - Actor: Alice | Action: Generate | Expected: "Kgosi Construction (Pty) Ltd"
- [ ] **2.75** Generate `statement-of-account` for Naledi -> note customer name in output -> save as `qa/testplan/artifacts/t1.6-statement-of-account-naledi.pdf`
    - Actor: Alice | Action: Generate for Naledi | Expected: "Naledi Hair Studio"
- [ ] **2.76** Kgosi document does NOT contain "Naledi Hair Studio"
    - Actor: Alice | Action: Search Kgosi document | Expected: Zero occurrences of "Naledi"
- [ ] **2.77** Naledi document does NOT contain "Kgosi Construction"
    - Actor: Alice | Action: Search Naledi document | Expected: Zero occurrences of "Kgosi"
- [ ] **2.78** Invoice tables show only the respective customer's invoices
    - Actor: Alice | Action: Compare invoice rows | Expected: No cross-customer data

---

## Day 4 -- Clause Assembly and Ordering (Track 2)

**Context**: Verify that clauses associated with engagement letter templates are correctly
assembled into generated documents -- present, in order, with resolved variables, and
responsive to user selection/deselection.

**Actor**: Alice (Owner)

---

### 4.1 -- Default Clause Inclusion

**Template**: `engagement-letter-bookkeeping` (7 associated clauses: 4 required, 3 optional)
**Generate from**: Kgosi "Monthly Bookkeeping" project

Clause association from `accounting-za-clauses` pack:
- **Required (locked)**: `accounting-za-limitation-of-liability` (sortOrder 1), `accounting-za-termination` (sortOrder 3), `accounting-za-confidentiality` (sortOrder 4), `accounting-za-document-retention` (sortOrder 5)
- **Optional (toggleable)**: `accounting-za-fee-escalation` (sortOrder 2), `accounting-za-third-party-reliance` (sortOrder 6), `accounting-za-electronic-consent` (sortOrder 7)

- [ ] **4.1** Generate document with all clauses selected (default)
    - Actor: Alice | Action: Open Generate Document dialog, accept clause defaults, generate | Expected: HTML preview loads with all 7 clauses
- [ ] **4.2** All 4 required clauses appear in the generated document:
    - "Limitation of Liability" (accounting-za variant -- mentions "delict" and "R1,000,000")
    - "Termination" (accounting-za variant -- mentions "thirty (30) days" and "SARS obligations")
    - "Confidentiality" (accounting-za variant -- mentions "POPIA" and "SAICA")
    - "Document Retention" (accounting-za variant -- mentions "Tax Administration Act" and "five (5) years")
    - Actor: Alice | Action: Search for clause titles and key phrases | Expected: All 4 clause bodies present
- [ ] **4.3** Optional clauses appear (since all selected by default):
    - "Fee Escalation" (mentions "CPI + 2%")
    - "Third-Party Reliance" (mentions "solely for the use and benefit of")
    - "Electronic Communication Consent" (mentions "Electronic Communications and Transactions Act")
    - Actor: Alice | Action: Search for optional clause key phrases | Expected: All 3 optional clauses present
- [ ] **4.4** Clauses appear in the order defined by `sortOrder` in the clause pack association
    - Actor: Alice | Action: Note order of clauses in output | Expected: Order follows sortOrder (1, 2, 3, 4, 5, 6, 7) or whatever the template-clause association defines

---

### 4.2 -- Clause Variable Resolution

- [ ] **4.5** Within "Limitation of Liability" clause: `org.name` resolves (clause body says "The liability of [org.name] to [customer.name]...")
    - Actor: Alice | Action: Read clause body | Expected: "The liability of Thornton & Associates to Kgosi Construction (Pty) Ltd..."
- [ ] **4.6** Within "Limitation of Liability" clause: `customer.name` = "Kgosi Construction (Pty) Ltd"
    - Actor: Alice | Action: Check customer name in clause | Expected: Exact match
- [ ] **4.7** Within "Termination" clause: both `org.name` and `customer.name` resolve (customer.name appears 2x in this clause)
    - Actor: Alice | Action: Check both names in termination clause | Expected: Both resolved, customer name appears in termination provisions and fee liability sentence
- [ ] **4.8** Within "Confidentiality" clause: both names resolve (mentions POPIA and SAICA)
    - Actor: Alice | Action: Check both names | Expected: "Thornton & Associates undertakes to treat all information received from or on behalf of Kgosi Construction (Pty) Ltd..."
- [ ] **4.9** No clause body contains `{{org.name}}` or `{{customer.name}}` as literal text
    - Actor: Alice | Action: Search all clause bodies for `{{` | Expected: Zero occurrences

---

### 4.3 -- Clause Selection / Deselection

**Precondition**: The GenerateDocumentDialog shows a clause picker step (generation-clause-step.tsx) with checkboxes for optional clauses and locked indicators for required clauses.

- [ ] **4.10** In the generation dialog, verify clause list shows required (locked) and optional (toggleable) clauses
    - Actor: Alice | Action: Open Generate Document dialog, observe clause step | Expected: 4 clauses locked (cannot uncheck), 3 clauses with checkboxes
- [ ] **4.11** Deselect "Fee Escalation" -> generate -> verify "Fee Escalation" section is absent from output
    - Actor: Alice | Action: Uncheck Fee Escalation, generate | Expected: Output has 6 clauses, no "CPI + 2%" text
- [ ] **4.12** Deselect all optional clauses -> generate -> verify only 4 required clauses appear
    - Actor: Alice | Action: Uncheck all optional, generate | Expected: Only Limitation of Liability, Termination, Confidentiality, Document Retention present
- [ ] **4.13** Re-select all optional clauses -> generate -> verify all 7 clauses appear
    - Actor: Alice | Action: Check all, generate | Expected: All 7 clauses present

---

### 4.4 -- Clause Ordering

- [ ] **4.14** If the generation dialog allows drag-to-reorder clauses -> reorder two clauses
    - Actor: Alice | Action: Attempt reorder | Expected: Note whether reordering UI exists
- [ ] **4.15** If reordering supported: verify the generated document reflects the new order
    - Actor: Alice | Action: Check output order | Expected: Matches reordered sequence
- [ ] **4.16** If reordering is not supported in UI: note as observation (not a gap -- future enhancement)
    - Actor: Alice | Action: Record observation | Expected: Documented

---

## Day 5 -- Engagement Letter Pipeline (Track 3)

**Context**: End-to-end test of the highest-value document flow: create a proposal for
Vukani advisory, generate the engagement letter, verify the letter's content matches the
proposal data, then send and verify email delivery.

**Actor**: Alice (Owner)

---

### 5.1 -- Create Proposal for Vukani Advisory

- [ ] **5.1** Navigate to Proposals (`/org/e2e-test-org/proposals`) -> click New Proposal
    - Actor: Alice | Action: Navigate and click | Expected: Proposal creation form loads
- [ ] **5.2** Fill proposal: Title = "BEE Advisory Services -- Vukani Tech", Customer = Vukani Tech Solutions, Fee Model = FIXED_FEE, Amount = R7,500, Expiry = 30 days
    - Actor: Alice | Action: Fill form fields | Expected: All fields accept values
- [ ] **5.3** Add scope/description if field exists: "BEE scorecard analysis and compliance review"
    - Actor: Alice | Action: Fill description | Expected: Text saved (or field not present -- note)
- [ ] **5.4** Save as DRAFT -> verify proposal appears in list
    - Actor: Alice | Action: Save | Expected: Proposal visible in list with DRAFT status
- [ ] **5.5** Open proposal detail -> verify all entered data displays correctly
    - Actor: Alice | Action: Open detail | Expected: Title, customer, fee, amount, description all match

---

### 5.2 -- Generate Engagement Letter from Project

- [ ] **5.6** Navigate to Vukani "BEE Certificate Review" project -> Generate Document dropdown
    - Actor: Alice | Action: Navigate to project detail | Expected: Generate Document button visible
- [ ] **5.7** Select template: "Engagement Letter -- Advisory" (`engagement-letter-advisory`)
    - Actor: Alice | Action: Select from dropdown | Expected: Template selected, dialog opens
- [ ] **5.8** Review clause selection in the clause step (default: 4 clauses -- Limitation of Liability, Confidentiality, Termination, Third-Party Reliance)
    - Actor: Alice | Action: Review clause picker | Expected: 4 clauses listed, 2 required locked, 2 optional checked
- [ ] **5.9** Accept defaults and generate
    - Actor: Alice | Action: Click generate/preview | Expected: HTML preview loads

---

### 5.3 -- Verify Engagement Letter Content

**HTML Preview:**

- [ ] **5.10** Letter is addressed to "Vukani Tech Solutions (Pty) Ltd" -- not Kgosi, not blank
    - Actor: Alice | Action: Check salutation | Expected: "Dear Vukani Tech Solutions (Pty) Ltd,"
- [ ] **5.11** Advisory subject references "BEE Certificate Review -- Vukani" (project name)
    - Actor: Alice | Action: Search for project name | Expected: Project name present in subject/scope section
- [ ] **5.12** Org name "Thornton & Associates" appears in header and closing
    - Actor: Alice | Action: Search for org name | Expected: Present in 2+ locations
- [ ] **5.13** All 4 clauses present with resolved variables (no `{{org.name}}` or `{{customer.name}}`)
    - Actor: Alice | Action: Check all clause bodies | Expected: Variables resolved, all 4 present
- [ ] **5.14** No unresolved `{{` anywhere
    - Actor: Alice | Action: Search for `{{` | Expected: Zero occurrences

**PDF Download:**

- [ ] **5.15** Download PDF to `qa/testplan/artifacts/t3.3-engagement-letter-vukani-advisory.pdf`
    - Actor: Alice | Action: Download | Expected: Non-zero file
- [ ] **5.16** PDF contains "Vukani Tech Solutions"
    - Actor: Alice | Action: Read PDF | Expected: Customer name present
- [ ] **5.17** PDF contains "BEE Certificate Review"
    - Actor: Alice | Action: Read PDF | Expected: Project name present
- [ ] **5.18** PDF contains all 4 clause titles: "Limitation of Liability", "Confidentiality", "Termination", "Third-Party Reliance"
    - Actor: Alice | Action: Search PDF | Expected: All 4 found
- [ ] **5.19** PDF does NOT contain `{{` or `}}`
    - Actor: Alice | Action: Search PDF | Expected: Zero occurrences

---

### 5.4 -- Send Proposal and Verify Email

- [ ] **5.20** Open the Vukani advisory proposal -> click Send -> status changes to SENT
    - Actor: Alice | Action: Send proposal | Expected: Status updates to SENT
- [ ] **5.21** Check Mailpit (`http://localhost:8026`) -> email received for Vukani contact
    - Actor: Alice | Action: Check Mailpit inbox | Expected: Email to finance@vukanitech.co.za
- [ ] **5.22** Email subject references proposal title or firm name
    - Actor: Alice | Action: Check email subject | Expected: Contains "BEE Advisory" or "Thornton"
- [ ] **5.23** Email body contains a link (acceptance or view link)
    - Actor: Alice | Action: Check email body | Expected: Portal or acceptance URL present

---

## Day 6 -- Document Acceptance / E-Signing (Track 6)

**Context**: Test the full acceptance workflow -- firm sends a generated document for e-signing,
client views it in the portal, types their name to accept, system generates a Certificate
of Acceptance with SHA-256 hash.

**Actor**: Alice (Owner), then portal user (Thabo Kgosi)

---

### 6.1 -- Send Document for Acceptance

- [ ] **6.1** Generate an engagement letter: `engagement-letter-bookkeeping` for Kgosi "Monthly Bookkeeping" project
    - Actor: Alice | Action: Generate document | Expected: HTML preview loads with all content correct
- [ ] **6.2** After generation, find the "Send for Acceptance" action (button in GenerateDocumentDialog or on the generated document detail)
    - Actor: Alice | Action: Look for send/acceptance button | Expected: "Send for Acceptance" button visible. The GenerateDocumentDialog imports SendForAcceptanceDialog.
- [ ] **6.3** Select recipient: Kgosi portal contact (Thabo Kgosi, thabo@kgosiconstruction.co.za)
    - Actor: Alice | Action: Select recipient | Expected: Portal contact selectable
- [ ] **6.4** Configure expiry (e.g., 14 days) if option exists
    - Actor: Alice | Action: Set expiry | Expected: Expiry field exists (or note if absent)
- [ ] **6.5** Send -> verify acceptance request created with status PENDING
    - Actor: Alice | Action: Send | Expected: Acceptance tracking shows PENDING status
- [ ] **6.6** Check Mailpit -> email sent to Kgosi contact with acceptance link
    - Actor: Alice | Action: Check Mailpit | Expected: Email to thabo@kgosiconstruction.co.za with clickable link

---

### 6.2 -- Client Views and Accepts

- [ ] **6.7** Open the acceptance link from the email (or navigate to portal acceptance page via dev harness at `http://localhost:8081/portal/dev/generate-link`)
    - Actor: Portal user (Thabo) | Action: Click link | Expected: Portal page loads showing the document
- [ ] **6.8** Verify the PDF or document preview is displayed in-browser
    - Actor: Portal user | Action: View document | Expected: Document content visible
- [ ] **6.9** Verify document content matches what was generated (client name = "Kgosi Construction (Pty) Ltd", clauses present)
    - Actor: Portal user | Action: Read content | Expected: Content matches Day 2 generation
- [ ] **6.10** Type full name in the acceptance field: "Thabo Kgosi"
    - Actor: Portal user | Action: Type name | Expected: Text field accepts input
- [ ] **6.11** Click "I Accept" (or equivalent acceptance button)
    - Actor: Portal user | Action: Click accept | Expected: Acceptance submitted
- [ ] **6.12** Verify confirmation screen shown to client
    - Actor: Portal user | Action: Observe | Expected: Success message or confirmation page displayed

---

### 6.3 -- Firm Verifies Acceptance

- [ ] **6.13** Navigate to the generated document or acceptance tracking page (Settings > Acceptance at `/org/e2e-test-org/settings/acceptance` or on the document detail)
    - Actor: Alice | Action: Navigate | Expected: Acceptance record visible
- [ ] **6.14** Verify acceptance status = ACCEPTED
    - Actor: Alice | Action: Check status | Expected: Shows ACCEPTED
- [ ] **6.15** Verify acceptance metadata displayed:
    - Accepted by: "Thabo Kgosi"
    - Timestamp: today's date/time
    - IP address: recorded (any value)
    - Actor: Alice | Action: Check metadata fields | Expected: All 3 metadata fields present
- [ ] **6.16** Certificate of Acceptance PDF available for download
    - Actor: Alice | Action: Look for download button | Expected: "Download Certificate" or similar action available
- [ ] **6.17** Download certificate to `qa/testplan/artifacts/t6.3-certificate-of-acceptance-kgosi.pdf`
    - Actor: Alice | Action: Download | Expected: Non-zero file

**Certificate PDF Verification Prompt** (for AI agent):
```
Analyse this Certificate of Acceptance PDF and verify:

1. ACCEPTOR NAME: Should contain "Thabo Kgosi". Report if missing.

2. TIMESTAMP: Should contain a date/time of acceptance. Report if missing or
   if it shows a clearly wrong date (e.g., epoch, year 1970).

3. DOCUMENT REFERENCE: Should identify which document was accepted
   (title, ID, or filename). Report if missing.

4. HASH: Should contain a SHA-256 hash (64-character hexadecimal string).
   Report if missing or if the format doesn't match hex.

5. INTEGRITY STATEMENT: Should contain language about the document's integrity
   or that the hash can be used to verify the document hasn't been modified.
```

- [ ] **6.18** Certificate contains acceptor name "Thabo Kgosi"
    - Actor: Alice | Action: Read PDF | Expected: Name present
- [ ] **6.19** Certificate contains acceptance timestamp (today)
    - Actor: Alice | Action: Read PDF | Expected: Today's date/time
- [ ] **6.20** Certificate contains SHA-256 hash (64-character hex string)
    - Actor: Alice | Action: Search for hash | Expected: 64-character hex string present
- [ ] **6.21** Certificate contains document reference (title or ID)
    - Actor: Alice | Action: Read PDF | Expected: Document identified
- [ ] **6.22** Audit trail entry created for the acceptance event
    - Actor: Alice | Action: Check audit log or activity feed | Expected: Acceptance event logged

---

### 6.4 -- Expired Acceptance (Edge Case)

- [ ] **6.23** Note whether there's a way to test expiry without time manipulation
    - Actor: Alice | Action: Assess | Expected: Note whether expiry can be tested in E2E
- [ ] **6.24** If expiry processor runs on a schedule, check if any expired requests are visible
    - Actor: Alice | Action: Check | Expected: Documented observation
- [ ] **6.25** If not testable in E2E: note as observation, verify via backend integration tests
    - Actor: Alice | Action: Record | Expected: Documented

---

## Day 7 -- Information Request Full Loop (Track 4)

**Context**: Test the complete firm -> portal -> firm cycle for document collection. The firm
creates a year-end information request using the seeded template, sends it to the client,
the client responds via portal, and the firm reviews/accepts/rejects items.

**Actor**: Alice (Owner) creates and sends, Bob (Admin) reviews

---

### 7.1 -- Create Information Request

- [ ] **7.1** Navigate to Kgosi customer detail -> Requests tab (or `/org/e2e-test-org/information-requests`)
    - Actor: Alice | Action: Navigate | Expected: Requests section/page loads
- [ ] **7.2** Click New Information Request
    - Actor: Alice | Action: Click create button | Expected: Creation form/dialog opens
- [ ] **7.3** Select request template: "Year-End Information Request (SA)" (slug: `year-end-info-request-za`) if template picker exists, or create from scratch
    - Actor: Alice | Action: Select template | Expected: Template selected, items pre-populated
- [ ] **7.4** Verify request items are pre-populated from template (8 items from `year-end-info-request-za.json`):
    - Trial Balance (required, FILE_UPLOAD)
    - Bank Statements (Full Year) (required, FILE_UPLOAD)
    - Loan Agreements (required, FILE_UPLOAD)
    - Fixed Asset Register (required, FILE_UPLOAD)
    - Debtors Age Analysis (optional, FILE_UPLOAD)
    - Creditors Age Analysis (optional, FILE_UPLOAD)
    - Insurance Schedule (optional, FILE_UPLOAD)
    - Payroll Summary (required, FILE_UPLOAD)
    - Actor: Alice | Action: Count and verify items | Expected: 8 items listed with correct required/optional indicators
- [ ] **7.5** Add custom item: "Directors' Resolutions" (required, FILE_UPLOAD)
    - Actor: Alice | Action: Add item | Expected: 9th item added
- [ ] **7.6** Save -> verify request appears in requests list as DRAFT
    - Actor: Alice | Action: Save | Expected: Request visible with DRAFT status
- [ ] **7.7** Subject line references Kgosi or year-end
    - Actor: Alice | Action: Check subject | Expected: Contains "Kgosi" or "Year-End" or "Tax Return"

---

### 7.2 -- Send Request

- [ ] **7.8** Open request -> click Send
    - Actor: Alice | Action: Send request | Expected: Status changes to SENT
- [ ] **7.9** Verify status changes to SENT
    - Actor: Alice | Action: Check status | Expected: SENT
- [ ] **7.10** Check Mailpit -> notification email sent to Kgosi contact (thabo@kgosiconstruction.co.za)
    - Actor: Alice | Action: Check Mailpit inbox | Expected: Email present
- [ ] **7.11** Email contains a portal link for the client to respond
    - Actor: Alice | Action: Check email body | Expected: Clickable portal URL

---

### 7.3 -- Portal: Client Responds

**Precondition**: Portal contact exists for Kgosi (created in Phase 0H).

- [ ] **7.12** Navigate to portal (via magic link from email, or via dev harness at `http://localhost:8081/portal/dev/generate-link`)
    - Actor: Portal user (Thabo) | Action: Open portal | Expected: Portal loads
- [ ] **7.13** Verify client sees the pending information request
    - Actor: Portal user | Action: Look for request | Expected: Request visible with item list
- [ ] **7.14** Request shows all items with required/optional indicators
    - Actor: Portal user | Action: Check items | Expected: 9 items listed, required items marked
- [ ] **7.15** Upload a test file for "Trial Balance" item (use any small PDF or image file)
    - Actor: Portal user | Action: Upload file | Expected: File accepted
- [ ] **7.16** Upload a test file for "Bank Statements (Full Year)"
    - Actor: Portal user | Action: Upload file | Expected: File accepted
- [ ] **7.17** Skip optional items -> submit partial response
    - Actor: Portal user | Action: Submit | Expected: Submission accepted despite incomplete optional items
- [ ] **7.18** Verify submission confirmation shown to client
    - Actor: Portal user | Action: Observe | Expected: Success message or confirmation

---

### 7.4 -- Firm Reviews Submissions

- [ ] **7.19** Login as Bob -> Navigate to Kgosi customer -> Requests tab -> open the request
    - Actor: Bob | Action: Navigate | Expected: Request detail loads with submitted items
- [ ] **7.20** Verify request status shows partial/submitted
    - Actor: Bob | Action: Check status | Expected: Status reflects partial submission
- [ ] **7.21** For "Trial Balance": click item -> see uploaded file -> click Accept
    - Actor: Bob | Action: Accept item | Expected: Item status changes to ACCEPTED
- [ ] **7.22** For "Bank Statements": click item -> click Reject with reason "Incomplete -- missing December statement"
    - Actor: Bob | Action: Reject with reason | Expected: Item status changes to REJECTED, reason saved
- [ ] **7.23** Verify item statuses update (Trial Balance = Accepted, Bank Statements = Rejected)
    - Actor: Bob | Action: Check statuses | Expected: Correct statuses displayed
- [ ] **7.24** Check Mailpit -> rejection notification sent to client (if configured)
    - Actor: Bob | Action: Check Mailpit | Expected: Rejection email sent (or note if not configured)

---

### 7.5 -- Client Re-submits Rejected Item

- [ ] **7.25** Client returns to portal -> sees rejected item with reason "Incomplete -- missing December statement"
    - Actor: Portal user | Action: Check portal | Expected: Rejection reason visible
- [ ] **7.26** Client uploads a corrected file for "Bank Statements (Full Year)"
    - Actor: Portal user | Action: Upload replacement file | Expected: File accepted
- [ ] **7.27** Client re-submits
    - Actor: Portal user | Action: Submit | Expected: Re-submission accepted
- [ ] **7.28** Firm (Bob) reviews -> accepts the re-submission
    - Actor: Bob | Action: Accept re-submitted item | Expected: Item status changes to ACCEPTED
- [ ] **7.29** Request status updates to reflect all required items accepted (or note progress)
    - Actor: Bob | Action: Check overall status | Expected: Progress updated, completed items tracked

---

## Day 8 -- DOCX Template Pipeline (Track 7)

> **MANUAL TESTING ONLY** -- This track requires manual testing by the founder.
> The DOCX pipeline depends on LibreOffice for PDF conversion which may not be
> available in the E2E Docker stack. Skip this in automated Playwright runs.

### Instructions for Manual Testing

**7.1 -- Preparation**

Create a `.docx` file with these merge fields:
```
ENGAGEMENT LETTER

To: {{customer.name}}
Registration: {{customer.customFields.company_registration_number}}
VAT: {{customer.customFields.vat_number}}

RE: {{project.name}}

Dear {{customer.name}},

We are pleased to confirm our engagement for {{project.name}}.
Our firm, {{org.name}}, will provide the services outlined below.

Date: {{generatedAt}}
```
Save as `test-engagement.docx`.

**7.2 -- Upload and Field Discovery**

1. Navigate to Settings > Templates (`/org/e2e-test-org/settings/templates`)
2. Upload `test-engagement.docx` as a new DOCX template (via GenerateDocxDialog component)
3. Verify the system auto-discovers variables: `customer.name`, `customer.customFields.company_registration_number`, `customer.customFields.vat_number`, `project.name`, `org.name`, `generatedAt`
4. Set template scope to PROJECT
5. Save template

**7.3 -- Generate Filled Document**

1. Navigate to Kgosi "Monthly Bookkeeping" project -> Generate Document
2. Select the uploaded DOCX template
3. Generate -> download the filled `.docx`
4. Open in Word/LibreOffice and verify:
   - "To: Kgosi Construction (Pty) Ltd"
   - "Registration: 2019/123456/07"
   - "VAT: 4520012345"
   - "RE: Monthly Bookkeeping -- Kgosi" (or actual project name)
   - Org name = "Thornton & Associates"
   - Date = today
5. No `{{...}}` tokens remain in the document

**7.4 -- PDF Conversion (if available)**

1. If "Download as PDF" option exists -> click it
2. Verify PDF downloads (non-zero size)
3. Open PDF and verify content matches the filled DOCX
4. If PDF conversion fails: note error and whether LibreOffice is available

---

## Gap Reporting Format

For each content issue found during this lifecycle script, log a gap entry:

```markdown
### GAP-P49-NNN: [Short title]

**Track**: T[N].[N] -- [Test case name]
**Step**: [Step number]
**Category**: content-error | missing-variable | wrong-data | format-error | missing-feature
**Severity**: blocker | major | minor | cosmetic
**Description**: [What was expected vs what was found]
**Evidence**:
- Template: [slug]
- Variable: [variable path]
- Expected: [expected value]
- Actual: [actual value or "BLANK" or "UNRESOLVED"]
- Screenshot/PDF: [path in qa/testplan/artifacts/]
**Suggested fix**: [If obvious]
```

**Severity guide:**

| Severity | Criteria |
|----------|----------|
| blocker | Document cannot be generated, or contains another customer's data (data leak) |
| major | Variable resolves to blank where a value is expected; math is wrong on invoice; clause missing entirely |
| minor | Date in wrong format; currency symbol inconsistent; extra whitespace from empty optional field |
| cosmetic | Slight formatting issue; optional field blank but acceptable |

---

## Success Criteria

| Criterion | Target |
|-----------|--------|
| All 7 templates generate without error | 100% |
| No unresolved `{{...}}` tokens in any generated document | 0 occurrences |
| Custom field values flow correctly from customer to document | 100% of seeded fields |
| Invoice math is correct (line totals, subtotal, tax, total) | 100% |
| All required clauses present in engagement letters | 100% |
| Clause variables resolve within clause bodies | 100% |
| Information request full loop completes (send -> upload -> review) | End-to-end PASS |
| Document acceptance full loop completes (send -> accept -> certificate) | End-to-end PASS |
| Cross-customer data isolation (no data leaks between customers) | 100% |
| Zero blocker gaps | 0 |
