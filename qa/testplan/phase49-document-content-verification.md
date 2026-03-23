# Test Plan: Document & Field Content Verification
## Phase 49 — DocTeams Platform

**Version**: 1.0
**Date**: 2026-03-17
**Author**: Product + QA
**Vertical**: accounting-za (Thornton & Associates)
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / Keycloak 8180 / Mailpit 8025). See `qa/keycloak-e2e-guide.md` for setup.

---

## 1. Purpose

Phases 47-48 proved the platform's features *work* (buttons click, pages load, CRUD succeeds).
This test plan verifies that the **content produced by those features is correct** — that
variables resolve to real data, clauses assemble properly, custom fields flow into documents,
and the full document lifecycle (author → render → deliver → accept) produces accurate,
professional output.

**Core question**: If Thornton & Associates generated an engagement letter for Kgosi Construction
today, would the letter contain the right client name, fee amount, SARS reference, and legal
clauses — or would it have blanks, wrong values, or missing sections?

## 2. Scope

### In Scope

| Track | Description | Method |
|-------|-------------|--------|
| T0 | Data preparation — seed 90-day lifecycle + populate custom fields | Automated (Playwright UI + API) |
| T1 | Template variable fidelity (all 8 accounting-za templates) | Automated (Playwright + PDF read) |
| T2 | Clause assembly, ordering, and variable resolution | Automated |
| T3 | Engagement letter pipeline (proposal → document → content check) | Automated |
| T4 | Information request full loop (firm → portal → review) | Automated |
| T5 | Custom field → document flow (field packs → customer → template) | Automated |
| T6 | Document acceptance / e-signing (send → view → accept → certificate) | Automated |
| T7 | DOCX template pipeline (upload → discover → merge → PDF) | **Manual** |

### Out of Scope

- Basic CRUD, navigation, billing, profitability, role-based access (proven Phase 47-48)
- Template editor authoring (Tiptap WYSIWYG) — tested in Phase 31 unit/integration tests
- Email delivery content (Mailpit verification is checkpoint-only, not content-deep)
- Performance / load testing

## 3. Prerequisites

### 3.1 Keycloak Dev Stack

Start infrastructure and local services per `qa/keycloak-e2e-guide.md`, then run `keycloak-bootstrap.sh`.

Verify: frontend (3000), backend (8080), gateway (8443), keycloak (8180), postgres (5432), localstack (4566), mailpit (8025).

### 3.2 90-Day Seed Data

The QA agent (or a seed script) must bootstrap the Thornton & Associates 90-day lifecycle
before content verification begins. This creates the data that documents will render against.

**Required seed state** (from Phase 48 lifecycle script):

| Entity | Count | Key Records |
|--------|-------|-------------|
| Org settings | 1 | Currency=ZAR, brand colour=#1B5E20, billing/cost rates set |
| Customers | 4 | Kgosi Construction (Pty) Ltd, Naledi Hair Studio, Vukani Tech Solutions, Moroka Family Trust |
| Custom field values | ~16/customer | Company Registration, VAT Number, SARS Tax Reference, Entity Type, etc. |
| Projects | 5+ | Monthly Bookkeeping (Kgosi, Naledi, Vukani), Annual Administration (Moroka), BEE Review (Vukani) |
| Time entries | 10+ | Across 3 team members with rate snapshots (R1500/R850/R450) |
| Invoices | 4+ | Mix of DRAFT, APPROVED, SENT, PAID with line items and tax |
| Retainers | 2 | Kgosi (R5,500/month), Vukani (R4,500/month) |
| Proposals | 1+ | Kgosi engagement letter (SENT or ACCEPTED) |
| FICA checklists | 4 | All complete (customers are ACTIVE) |

**Critical for content verification**: Custom field values MUST be populated during seed.
Without them, template variables like `customer.customFields.sars_tax_reference` resolve to blank.

**Seed custom field values per customer**:

| Customer | company_registration_number | vat_number | sars_tax_reference | acct_entity_type | financial_year_end |
|----------|---------------------------|------------|-------------------|-----------------|-------------------|
| Kgosi Construction | 2019/123456/07 | 4520012345 | 9012345678 | PTY_LTD | 2026-02-28 |
| Naledi Hair Studio | — | — | 8801015001082 | SOLE_PROPRIETOR | 2026-02-28 |
| Vukani Tech Solutions | 2021/654321/07 | 4520098765 | 9087654321 | PTY_LTD | 2026-06-30 |
| Moroka Family Trust | — | — | 1234567890 | TRUST | 2026-02-28 |

**Trust-specific fields for Moroka**:

| Field | Value |
|-------|-------|
| trust_registration_number | IT789/2018 |
| trust_deed_date | 2018-06-15 |
| trust_type | INTER_VIVOS |
| trustee_names | Samuel Moroka, Grace Moroka, Adv. T. Nkosi |
| trustee_type | APPOINTED |
| letters_of_authority_date | 2018-07-20 |

### 3.3 Notation

- [ ] **PASS** — content is correct and complete
- [ ] **FAIL** — content is wrong, missing, or has unresolved variables
- [ ] **PARTIAL** — content is mostly correct but has minor issues (note specifics)
- [ ] **BLANK** — variable resolved to empty string where a value was expected
- [ ] **UNRESOLVED** — raw `{{variable}}` syntax visible in output

---

## 4. Test Tracks

---

### Track 0 — Data Preparation (Seed + Custom Field Population)

**Goal**: Bootstrap the Thornton & Associates 90-day lifecycle and — critically — populate
all custom field values that templates depend on. Without this track, every template test
in T1-T6 produces false negatives (blank variables).

**Method**: QA agent runs the Phase 48 lifecycle script (Days 0-90) via Playwright, then
does a second pass to fill in custom field values that weren't captured during initial
customer creation.

#### T0.1 — Run 90-Day Lifecycle Seed

Follow the Phase 48 lifecycle script (`tasks/phase48-lifecycle-script.md`) through all days.
This creates the baseline entities (customers, projects, invoices, time entries, etc.).

- [ ] **T0.1.1** Day 0 complete: Org settings configured (ZAR, brand colour, rates, tax)
- [ ] **T0.1.2** Day 1 complete: Kgosi Construction created, FICA complete, ACTIVE, project + retainer + tasks
- [ ] **T0.1.3** Day 2-3 complete: Naledi, Vukani, Moroka created and ACTIVE with projects/tasks
- [ ] **T0.1.4** Day 7 complete: Time entries logged by Carol, Bob, Alice across projects
- [ ] **T0.1.5** Day 14 complete: More time entries, notifications checked
- [ ] **T0.1.6** Day 30 complete: Invoices created (hourly + retainer), approved, sent, budgets set
- [ ] **T0.1.7** Day 45 complete: Payment recorded, expenses logged, ad-hoc engagement
- [ ] **T0.1.8** Day 60 complete: Second billing cycle, reports verified
- [ ] **T0.1.9** Day 75 complete: Year-end project, information request sent
- [ ] **T0.1.10** Day 90 complete: Portfolio review, profitability, document generation, compliance

**Checkpoint**: Verify entity counts match prerequisites table (Section 3.2).

#### T0.2 — Populate Custom Field Values (Kgosi Construction)

**Actor**: Bob (Admin)

Navigate to Kgosi Construction customer detail → custom fields section (or edit dialog).
If fields were partially filled during creation, update them now. If they were blank,
fill all values.

- [ ] **T0.2.1** Set `acct_company_registration_number` = **2019/123456/07**
- [ ] **T0.2.2** Set `vat_number` = **4520012345**
- [ ] **T0.2.3** Set `sars_tax_reference` = **9012345678**
- [ ] **T0.2.4** Set `acct_entity_type` = **PTY_LTD**
- [ ] **T0.2.5** Set `financial_year_end` = **2026-02-28**
- [ ] **T0.2.6** Set `trading_as` = **Kgosi Construction**
- [ ] **T0.2.7** Set `registered_address` = **14 Main Road, Sandton, Johannesburg, 2196**
- [ ] **T0.2.8** Set `primary_contact_name` = **Thabo Kgosi**
- [ ] **T0.2.9** Set `primary_contact_email` = **thabo@kgosiconstruction.co.za**
- [ ] **T0.2.10** Set `primary_contact_phone` = **+27-11-555-0100**
- [ ] **T0.2.11** Set `fica_verified` = **VERIFIED**
- [ ] **T0.2.12** Set `fica_verification_date` = **2026-01-15**
- [ ] **T0.2.13** Save → reload page → verify all values persisted

**Capture verification**: Take a screenshot or note the visible field values. These are the
"source of truth" for all template content checks in T1 and T5.

#### T0.3 — Populate Custom Field Values (Naledi Hair Studio)

- [ ] **T0.3.1** Set `sars_tax_reference` = **8801015001082**
- [ ] **T0.3.2** Set `acct_entity_type` = **SOLE_PROPRIETOR**
- [ ] **T0.3.3** Set `financial_year_end` = **2026-02-28**
- [ ] **T0.3.4** Set `primary_contact_name` = **Naledi Molefe**
- [ ] **T0.3.5** Set `primary_contact_email` = **naledi@naledihair.co.za**
- [ ] **T0.3.6** Set `fica_verified` = **VERIFIED**
- [ ] **T0.3.7** Set `fica_verification_date` = **2026-01-16**
- [ ] **T0.3.8** Save → verify persisted
- [ ] **T0.3.9** Leave `company_registration_number` and `vat_number` blank — Naledi is a sole proprietor without these. This sets up **T5.6** (missing field behaviour test)

#### T0.4 — Populate Custom Field Values (Vukani Tech Solutions)

- [ ] **T0.4.1** Set `acct_company_registration_number` = **2021/654321/07**
- [ ] **T0.4.2** Set `vat_number` = **4520098765**
- [ ] **T0.4.3** Set `sars_tax_reference` = **9087654321**
- [ ] **T0.4.4** Set `acct_entity_type` = **PTY_LTD**
- [ ] **T0.4.5** Set `financial_year_end` = **2026-06-30**
- [ ] **T0.4.6** Set `registered_address` = **88 Rivonia Blvd, Rivonia, Johannesburg, 2128**
- [ ] **T0.4.7** Set `primary_contact_name` = **Sipho Ndlovu**
- [ ] **T0.4.8** Set `primary_contact_email` = **finance@vukanitech.co.za**
- [ ] **T0.4.9** Set `fica_verified` = **VERIFIED**
- [ ] **T0.4.10** Set `fica_verification_date` = **2026-01-17**
- [ ] **T0.4.11** Save → verify persisted

#### T0.5 — Populate Custom Field Values (Moroka Family Trust)

Standard fields:

- [ ] **T0.5.1** Set `sars_tax_reference` = **1234567890**
- [ ] **T0.5.2** Set `acct_entity_type` = **TRUST**
- [ ] **T0.5.3** Set `financial_year_end` = **2026-02-28**
- [ ] **T0.5.4** Set `primary_contact_name` = **Samuel Moroka**
- [ ] **T0.5.5** Set `primary_contact_email` = **trustees@morokatrust.co.za**
- [ ] **T0.5.6** Set `fica_verified` = **VERIFIED**
- [ ] **T0.5.7** Set `fica_verification_date` = **2026-01-18**

Trust-specific fields (should be visible because entity type = TRUST):

- [ ] **T0.5.8** Verify trust field group is visible on the customer detail page
- [ ] **T0.5.9** Set `trust_registration_number` = **IT789/2018**
- [ ] **T0.5.10** Set `trust_deed_date` = **2018-06-15**
- [ ] **T0.5.11** Set `trust_type` = **INTER_VIVOS**
- [ ] **T0.5.12** Set `trustee_names` = **Samuel Moroka, Grace Moroka, Adv. T. Nkosi**
- [ ] **T0.5.13** Set `trustee_type` = **APPOINTED**
- [ ] **T0.5.14** Set `letters_of_authority_date` = **2018-07-20**
- [ ] **T0.5.15** Save → verify all trust fields persisted
- [ ] **T0.5.16** If trust fields NOT visible: log as GAP (conditional field group not showing for TRUST entity type)

#### T0.6 — Org Settings for Document Variables

These org-level values appear in template headers/footers. Set them if not already configured.

**Actor**: Alice (Owner)

- [ ] **T0.6.1** Navigate to Settings > General
- [ ] **T0.6.2** Set `documentFooterText` = **"Thornton & Associates | Reg. 2015/001234/21 | 14 Loop St, Cape Town 8001"** (or verify already set)
- [ ] **T0.6.3** Set `taxRegistrationNumber` = **4510067890** (if field exists — needed for invoice-za template)
- [ ] **T0.6.4** Verify org `name` displays correctly (this is what `org.name` resolves to)
- [ ] **T0.6.5** Verify `defaultCurrency` = **ZAR**
- [ ] **T0.6.6** Save → verify persisted

#### T0.7 — Project Custom Field Values

Some templates reference `project.customFields.*`. Set these on relevant projects.

- [ ] **T0.7.1** Open Kgosi "Monthly Bookkeeping" project → custom fields
- [ ] **T0.7.2** Set `engagement_type` = **Monthly Bookkeeping** (if field exists in accounting-za-project pack)
- [ ] **T0.7.3** Open Kgosi "Annual Tax Return 2026" project → custom fields
- [ ] **T0.7.4** Set `tax_year` = **2026** (if field exists)
- [ ] **T0.7.5** Set `sars_submission_deadline` = **2026-11-30** (if field exists)
- [ ] **T0.7.6** If project custom fields don't exist: note which are missing, log as observation
- [ ] **T0.7.7** Save all changes

#### T0.8 — Portal Contact Setup

Track 4 (information requests) and Track 6 (e-signing) require portal contacts to exist.

- [ ] **T0.8.1** Navigate to Kgosi customer → Portal / Contacts tab
- [ ] **T0.8.2** If no portal contact exists: create one (Name = Thabo Kgosi, Email = thabo@kgosiconstruction.co.za)
- [ ] **T0.8.3** Verify portal contact is active
- [ ] **T0.8.4** Repeat for Vukani (Name = Sipho Ndlovu, Email = finance@vukanitech.co.za) if needed for T3/T6

#### T0.9 — Data Readiness Checkpoint

Before proceeding to Track 1, spot-check the data:

- [ ] **T0.9.1** Open Kgosi customer detail → verify at least 10 custom field values are visible and non-blank
- [ ] **T0.9.2** Open Moroka customer detail → verify trust fields are visible and populated
- [ ] **T0.9.3** Navigate to Invoices → verify at least 2 invoices exist with line items
- [ ] **T0.9.4** Navigate to Projects → verify at least 4 projects exist
- [ ] **T0.9.5** Navigate to Settings > General → verify org name, currency, and footer text are set

**STOP GATE**: If T0.9.1 fails (fields blank or not visible), do NOT proceed to Track 1.
Investigate whether field packs were seeded, whether the vertical profile is applied, and
whether custom field values are saving correctly. Fix before continuing.

---

### Track 1 — Template Variable Fidelity

**Goal**: For each of the 8 accounting-za templates, generate a document and verify every
variable resolves to the expected value. No blanks, no unresolved `{{...}}` tokens, no
wrong-customer data.

**Method**: Generate via UI → open HTML preview → check DOM content → download PDF → read PDF → verify content checklist.

#### T1.1 — Engagement Letter: Monthly Bookkeeping (PROJECT scope)

**Template slug**: `engagement-letter-bookkeeping`
**Generate from**: Kgosi "Monthly Bookkeeping" project detail → Generate Document dropdown
**Associated clauses**: 7 (4 required, 3 optional from accounting-za-clauses)

**Content checklist — HTML preview**:

- [ ] **T1.1.1** `org.name` resolves (header, intro, signature, footer) — not blank, not `{{org.name}}`
- [ ] **T1.1.2** `customer.name` = "Kgosi Construction (Pty) Ltd" (salutation, client details, responsibilities, footer)
- [ ] **T1.1.3** `customer.customFields.company_registration_number` = "2019/123456/07" — appears in registration field
- [ ] **T1.1.4** `project.customFields.engagement_type` — resolves or shows blank (check if field is populated)
- [ ] **T1.1.5** `org.documentFooterText` — resolves in footer section (may be blank if not configured — note)
- [ ] **T1.1.6** No unresolved variables: search page content for `{{` — expect zero matches
- [ ] **T1.1.7** Currency references use ZAR / R symbol, not USD / $

**Content checklist — PDF download**:

- [ ] **T1.1.8** PDF downloads successfully (non-zero file size)
- [ ] **T1.1.9** PDF contains "Kgosi Construction (Pty) Ltd" (read PDF, search text)
- [ ] **T1.1.10** PDF contains org name in header
- [ ] **T1.1.11** PDF contains "2019/123456/07" (company registration)
- [ ] **T1.1.12** PDF does NOT contain literal `{{` or `}}` characters
- [ ] **T1.1.13** All 4 required clauses present: "Limitation of Liability", "Termination", "Confidentiality", "Document Retention"

---

#### T1.2 — Engagement Letter: Annual Tax Return (PROJECT scope)

**Template slug**: `engagement-letter-tax-return`
**Generate from**: Kgosi "Annual Tax Return 2026" project (create if not exists in seed)

**Content checklist**:

- [ ] **T1.2.1** `org.name` resolves in header and closing
- [ ] **T1.2.2** `customer.name` = "Kgosi Construction (Pty) Ltd"
- [ ] **T1.2.3** `customer.customFields.sars_tax_reference` = "9012345678" — appears in SARS reference field
- [ ] **T1.2.4** `project.customFields.tax_year` — resolves (check if field populated in seed, note if blank)
- [ ] **T1.2.5** `project.customFields.sars_submission_deadline` — resolves or noted as blank
- [ ] **T1.2.6** No unresolved `{{` in output
- [ ] **T1.2.7** Associated clauses present (5): Limitation of Liability, Termination, Confidentiality, Third-Party Reliance, Document Retention

---

#### T1.3 — Engagement Letter: Advisory (PROJECT scope)

**Template slug**: `engagement-letter-advisory`
**Generate from**: Vukani "BEE Certificate Review" project

**Content checklist**:

- [ ] **T1.3.1** `org.name` resolves
- [ ] **T1.3.2** `customer.name` = "Vukani Tech Solutions (Pty) Ltd"
- [ ] **T1.3.3** `project.name` = "BEE Certificate Review — Vukani" (advisory subject matter)
- [ ] **T1.3.4** `org.documentFooterText` resolves in footer
- [ ] **T1.3.5** No unresolved `{{`
- [ ] **T1.3.6** Associated clauses (4): Limitation of Liability, Confidentiality, Termination, Third-Party Reliance

---

#### T1.4 — Monthly Report Cover (PROJECT scope)

**Template slug**: `monthly-report-cover`
**Generate from**: Kgosi "Monthly Bookkeeping" project

**Content checklist**:

- [ ] **T1.4.1** `org.name` in header
- [ ] **T1.4.2** `customer.name` = "Kgosi Construction (Pty) Ltd" in "Prepared For" field
- [ ] **T1.4.3** `project.name` = "Monthly Bookkeeping — Kgosi" in project field
- [ ] **T1.4.4** `generatedAt` shows today's date (not blank, not epoch, not `{{generatedAt}}`)
- [ ] **T1.4.5** Date format is human-readable (not raw ISO timestamp)
- [ ] **T1.4.6** No unresolved `{{`

---

#### T1.5 — SA Tax Invoice (INVOICE scope)

**Template slug**: `invoice-za`
**Generate from**: An existing Kgosi invoice (January retainer invoice)

**Content checklist**:

- [ ] **T1.5.1** `org.name` in issuer section
- [ ] **T1.5.2** `org.taxRegistrationNumber` — resolves (note: may need to be set in org settings during seed)
- [ ] **T1.5.3** `customer.name` = "Kgosi Construction (Pty) Ltd"
- [ ] **T1.5.4** `customerVatNumber` = "4520012345" (from custom field)
- [ ] **T1.5.5** `invoice.invoiceNumber` — resolves to a sequential number (e.g. "INV-001")
- [ ] **T1.5.6** `invoice.issueDate` — human-readable date
- [ ] **T1.5.7** `invoice.dueDate` — human-readable date, after issue date
- [ ] **T1.5.8** `invoice.currency` = "ZAR"
- [ ] **T1.5.9** `invoice.subtotal` — numeric, matches expected amount (R5,500 for retainer)
- [ ] **T1.5.10** `invoice.taxAmount` — 15% of subtotal
- [ ] **T1.5.11** `invoice.total` — subtotal + tax
- [ ] **T1.5.12** **Line items table** renders: description, quantity, unit price, tax, line total — at least 1 row
- [ ] **T1.5.13** Line item amounts are numeric (not NaN, not blank, not `{{}}`)
- [ ] **T1.5.14** Math check: line totals sum to subtotal; subtotal + tax = total
- [ ] **T1.5.15** Currency symbol is R or ZAR throughout, no $ or USD

---

#### T1.6 — Statement of Account (CUSTOMER scope)

**Template slug**: `statement-of-account`
**Generate from**: Kgosi Construction customer detail → Generate Document

**Content checklist**:

- [ ] **T1.6.1** `org.name` in header and closing
- [ ] **T1.6.2** `customer.name` = "Kgosi Construction (Pty) Ltd"
- [ ] **T1.6.3** `generatedAt` — today's date, human-readable
- [ ] **T1.6.4** `totalOutstanding` — numeric amount (may be R0.00 if all paid — note value)
- [ ] **T1.6.5** **Invoice history table** renders with columns: invoice number, issue date, due date, total, currency, status, running balance
- [ ] **T1.6.6** At least 1 invoice row in the table (Kgosi has invoices from seed)
- [ ] **T1.6.7** Invoice numbers in table match actual invoice numbers in the system
- [ ] **T1.6.8** Status column shows correct values (PAID, SENT, etc.)
- [ ] **T1.6.9** Running balance column calculates correctly
- [ ] **T1.6.10** No unresolved `{{`

---

#### T1.7 — FICA Confirmation Letter (CUSTOMER scope)

**Template slug**: `fica-confirmation`
**Generate from**: Kgosi Construction customer detail → Generate Document

**Content checklist**:

- [ ] **T1.7.1** `org.name` in header, intro, signature, footer
- [ ] **T1.7.2** `customer.name` = "Kgosi Construction (Pty) Ltd" (appears 3x: salutation, verification details, closing)
- [ ] **T1.7.3** `customer.customFields.fica_verification_date` — resolves to a date (check if populated in seed)
- [ ] **T1.7.4** If fica_verification_date is blank: note as GAP (field should be set when FICA checklist completed)
- [ ] **T1.7.5** No unresolved `{{`

---

#### T1.8 — Cross-Customer Isolation Check

**Goal**: Generate the same template for two different customers and verify data doesn't leak.

- [ ] **T1.8.1** Generate `statement-of-account` for Kgosi → note customer name in output
- [ ] **T1.8.2** Generate `statement-of-account` for Naledi → note customer name in output
- [ ] **T1.8.3** Kgosi document does NOT contain "Naledi Hair Studio"
- [ ] **T1.8.4** Naledi document does NOT contain "Kgosi Construction"
- [ ] **T1.8.5** Invoice tables show only the respective customer's invoices

---

### Track 2 — Clause Assembly & Ordering

**Goal**: Verify that clauses associated with templates are correctly assembled into generated
documents — present, in order, with resolved variables, and responsive to user selection.

#### T2.1 — Default Clause Inclusion

**Template**: `engagement-letter-bookkeeping` (7 associated clauses: 4 required, 3 optional)
**Generate from**: Kgosi "Monthly Bookkeeping" project

- [ ] **T2.1.1** All 4 required clauses appear in the generated document:
  - "Limitation of Liability" (accounting-za variant)
  - "Termination" (accounting-za variant)
  - "Confidentiality" (accounting-za variant)
  - "Document Retention" (accounting-za variant)
- [ ] **T2.1.2** Optional clauses appear if selected by default:
  - "Fee Escalation"
  - "Third-Party Reliance"
  - "Electronic Communication Consent"
- [ ] **T2.1.3** Clauses appear in the order defined by the template-clause association (check `sortOrder`)

#### T2.2 — Clause Variable Resolution

- [ ] **T2.2.1** Within "Limitation of Liability" clause: `org.name` resolves (not blank)
- [ ] **T2.2.2** Within "Limitation of Liability" clause: `customer.name` = "Kgosi Construction (Pty) Ltd"
- [ ] **T2.2.3** Within "Termination" clause: both `org.name` and `customer.name` resolve (customer.name appears 2x)
- [ ] **T2.2.4** Within "Confidentiality" clause: both names resolve
- [ ] **T2.2.5** No clause body contains `{{org.name}}` or `{{customer.name}}` as literal text

#### T2.3 — Clause Selection / Deselection (if UI supports it)

**Precondition**: The generation dialog shows a clause picker with checkboxes for optional clauses.

- [ ] **T2.3.1** Generation dialog shows clause list with required (locked) and optional (toggleable) clauses
- [ ] **T2.3.2** Deselect "Fee Escalation" → generate → verify "Fee Escalation" section is absent from output
- [ ] **T2.3.3** Deselect all optional clauses → generate → verify only 4 required clauses appear
- [ ] **T2.3.4** Re-select all → generate → verify all 7 clauses appear

#### T2.4 — Clause Ordering

- [ ] **T2.4.1** If the generation dialog allows reordering clauses → reorder two clauses
- [ ] **T2.4.2** Verify the generated document reflects the new order
- [ ] **T2.4.3** If reordering is not supported in UI — note as observation (not a gap)

---

### Track 3 — Engagement Letter Pipeline

**Goal**: End-to-end test of the highest-value document flow: create a proposal → generate
the engagement letter → verify the letter's content matches the proposal data.

#### T3.1 — Create Proposal for Vukani Advisory

**Actor**: Alice (Owner)

- [ ] **T3.1.1** Navigate to Proposals → New Proposal
- [ ] **T3.1.2** Fill: Title = "BEE Advisory Services — Vukani Tech", Customer = Vukani Tech Solutions, Fee Model = FIXED_FEE, Amount = R7,500, Expiry = 30 days
- [ ] **T3.1.3** Add scope/description if field exists: "BEE scorecard analysis and compliance review"
- [ ] **T3.1.4** Save as DRAFT → verify proposal appears in list
- [ ] **T3.1.5** Open proposal detail → verify all entered data displays correctly

#### T3.2 — Generate Engagement Letter from Project

- [ ] **T3.2.1** Navigate to Vukani "BEE Certificate Review" project → Generate Document
- [ ] **T3.2.2** Select template: "Engagement Letter — Advisory" (`engagement-letter-advisory`)
- [ ] **T3.2.3** Review clause selection (default: 4 clauses — Limitation of Liability, Confidentiality, Termination, Third-Party Reliance)

#### T3.3 — Verify Engagement Letter Content

**HTML Preview**:

- [ ] **T3.3.1** Letter is addressed to "Vukani Tech Solutions (Pty) Ltd" — not Kgosi, not blank
- [ ] **T3.3.2** Advisory subject references "BEE Certificate Review — Vukani" (project name)
- [ ] **T3.3.3** Org name appears in header and closing
- [ ] **T3.3.4** All 4 clauses present with resolved variables
- [ ] **T3.3.5** No unresolved `{{`

**PDF Download + Read**:

- [ ] **T3.3.6** PDF downloads successfully
- [ ] **T3.3.7** PDF contains "Vukani Tech Solutions"
- [ ] **T3.3.8** PDF contains "BEE Certificate Review"
- [ ] **T3.3.9** PDF contains all 4 clause titles
- [ ] **T3.3.10** PDF does NOT contain `{{` or `}}`

#### T3.4 — Send Proposal and Verify Email

- [ ] **T3.4.1** Open proposal → click Send → status changes to SENT
- [ ] **T3.4.2** Check Mailpit → email received for Vukani contact
- [ ] **T3.4.3** Email subject references proposal title or firm name
- [ ] **T3.4.4** Email body contains a link (acceptance or view link)

---

### Track 4 — Information Request Full Loop

**Goal**: Test the complete firm → portal → firm cycle for document collection.

#### T4.1 — Create Information Request

**Actor**: Alice (Owner)

- [ ] **T4.1.1** Navigate to Kgosi customer detail → Requests tab
- [ ] **T4.1.2** Click New Information Request
- [ ] **T4.1.3** Select request template: "Year-End Info Request" (or create from scratch)
- [ ] **T4.1.4** Verify request items are pre-populated from template:
  - Trial Balance (required, FILE_UPLOAD)
  - Bank Statements Full Year (required, FILE_UPLOAD)
  - Loan Agreements (required, FILE_UPLOAD)
  - Fixed Asset Register (required, FILE_UPLOAD)
  - Debtors Age Analysis (optional, FILE_UPLOAD)
  - Creditors Age Analysis (optional, FILE_UPLOAD)
  - Insurance Schedule (optional, FILE_UPLOAD)
  - Payroll Summary (required, FILE_UPLOAD)
- [ ] **T4.1.5** Add custom item: "Directors' Resolutions" (required, FILE_UPLOAD)
- [ ] **T4.1.6** Save → verify request appears in requests list as DRAFT
- [ ] **T4.1.7** Subject line references Kgosi or year-end

#### T4.2 — Send Request

- [ ] **T4.2.1** Open request → click Send
- [ ] **T4.2.2** Verify status changes to SENT
- [ ] **T4.2.3** Check Mailpit → notification email sent to Kgosi contact
- [ ] **T4.2.4** Email contains a portal link for the client to respond

#### T4.3 — Portal: Client Responds

**Precondition**: Portal contact exists for Kgosi. If not, create one first (or verify auto-creation).

- [ ] **T4.3.1** Navigate to portal (via magic link from email, or direct portal URL with auth)
- [ ] **T4.3.2** Verify client sees the pending information request
- [ ] **T4.3.3** Request shows all items with required/optional indicators
- [ ] **T4.3.4** Upload a test file for "Trial Balance" item (use any small PDF/image)
- [ ] **T4.3.5** Upload a test file for "Bank Statements Full Year"
- [ ] **T4.3.6** Skip optional items → submit partial response
- [ ] **T4.3.7** Verify submission confirmation shown to client

#### T4.4 — Firm Reviews Submissions

**Actor**: Bob (Admin)

- [ ] **T4.4.1** Navigate to Kgosi customer → Requests tab → open the request
- [ ] **T4.4.2** Verify request status shows partial/submitted
- [ ] **T4.4.3** For "Trial Balance": click item → see uploaded file → click Accept
- [ ] **T4.4.4** For "Bank Statements": click item → click Reject with reason "Incomplete — missing December statement"
- [ ] **T4.4.5** Verify item statuses update (Accepted, Rejected)
- [ ] **T4.4.6** Check Mailpit → rejection notification sent to client (if configured)

#### T4.5 — Client Re-submits Rejected Item

- [ ] **T4.5.1** Client returns to portal → sees rejected item with reason
- [ ] **T4.5.2** Client uploads a corrected file for "Bank Statements"
- [ ] **T4.5.3** Client re-submits
- [ ] **T4.5.4** Firm reviews → accepts the re-submission
- [ ] **T4.5.5** Request status updates to reflect all required items accepted

---

### Track 5 — Custom Field → Document Flow

**Goal**: Verify the data chain from field pack definition → customer creation → field value
storage → template variable context → rendered document. The custom field should appear
correctly in the generated document.

#### T5.1 — Standard Custom Field Flow (Kgosi)

- [ ] **T5.1.1** Open Kgosi customer detail → verify custom fields section shows accounting-za fields
- [ ] **T5.1.2** Verify these field values are populated:
  - Company Registration Number = "2019/123456/07"
  - VAT Number = "4520012345"
  - SARS Tax Reference = "9012345678"
  - Entity Type = "PTY_LTD" (or display label "Pty Ltd")
  - Financial Year-End = "2026-02-28" (or formatted display)
- [ ] **T5.1.3** Generate `engagement-letter-bookkeeping` for Kgosi's Monthly Bookkeeping project
- [ ] **T5.1.4** In output: verify `customer.customFields.company_registration_number` = "2019/123456/07"
- [ ] **T5.1.5** If VAT number appears in any template variable → verify = "4520012345"
- [ ] **T5.1.6** If SARS reference appears → verify = "9012345678"

#### T5.2 — FICA Verification Date Flow

- [ ] **T5.2.1** Check Kgosi's `fica_verification_date` field — is it populated?
- [ ] **T5.2.2** If populated: generate `fica-confirmation` template → verify date appears in output
- [ ] **T5.2.3** If NOT populated: note as GAP — completing FICA checklist should auto-set this date
- [ ] **T5.2.4** Manually set `fica_verification_date` → regenerate → verify it now appears

#### T5.3 — Conditional Fields: Trust Entity Type (Moroka)

- [ ] **T5.3.1** Open Moroka Family Trust customer detail
- [ ] **T5.3.2** Verify Entity Type field = "TRUST"
- [ ] **T5.3.3** Verify trust-specific fields are visible:
  - Trust Registration Number = "IT789/2018"
  - Trust Deed Date = "2018-06-15"
  - Trust Type = "INTER_VIVOS"
  - Names of Trustees = "Samuel Moroka, Grace Moroka, Adv. T. Nkosi"
- [ ] **T5.3.4** Generate `fica-confirmation` for Moroka → verify customer name = "Moroka Family Trust"
- [ ] **T5.3.5** Check if trust-specific fields appear in the generated document (they may not — depends on template variables. Note which fields are/aren't in the template)

#### T5.4 — Non-Trust Customer: Conditional Fields Hidden

- [ ] **T5.4.1** Open Naledi Hair Studio customer detail
- [ ] **T5.4.2** Verify Entity Type = "SOLE_PROPRIETOR"
- [ ] **T5.4.3** Verify trust-specific fields are NOT shown (Trust Registration Number, Trust Deed Date, etc.)
- [ ] **T5.4.4** If trust fields are visible for a sole proprietor: log as GAP (conditional visibility broken)

#### T5.5 — Invoice Custom Field Flow

- [ ] **T5.5.1** Generate `invoice-za` for a Kgosi invoice
- [ ] **T5.5.2** Verify `customerVatNumber` = "4520012345" in the invoice output
- [ ] **T5.5.3** If `customerVatNumber` is blank but the custom field has a value: log as GAP (field not mapped to invoice context)
- [ ] **T5.5.4** Verify `org.taxRegistrationNumber` resolves (check if org settings has this field)

#### T5.6 — Missing Required Field Behaviour

**Goal**: When a template uses a variable but the entity doesn't have a value for it, what
happens? The system should either (a) warn the user before generation that required fields
are missing, or (b) handle the blank gracefully in the output. Silently producing
"Registration: " with trailing whitespace is a content quality failure.

**Setup**: Naledi Hair Studio has `company_registration_number` and `vat_number` intentionally
left blank (she's a sole proprietor — these don't apply to her).

- [ ] **T5.6.1** Navigate to Naledi's project → Generate Document → select `engagement-letter-bookkeeping`
- [ ] **T5.6.2** **Pre-generation check**: Does the system show a warning that `company_registration_number` is missing? Note the behaviour:
  - (a) Warning shown with option to proceed or fill in the field → **PASS** (ideal)
  - (b) No warning, generation proceeds silently → note, continue to T5.6.3
- [ ] **T5.6.3** Check the generated output for the registration number field:
  - (a) Field line is hidden/omitted entirely → **PASS** (conditional rendering)
  - (b) Shows "Registration Number: " with blank value after colon → **FAIL** (ugly output)
  - (c) Shows "Registration Number: N/A" or similar placeholder → **PASS** (graceful fallback)
  - (d) Shows `{{customer.customFields.company_registration_number}}` literally → **FAIL** (unresolved variable)
- [ ] **T5.6.4** Check the VAT number field in the same document — same criteria as T5.6.3
- [ ] **T5.6.5** Generate `invoice-za` for a Naledi invoice → check `customerVatNumber` field:
  - Naledi is not VAT-registered, so this should be blank. Does the invoice handle it gracefully?
  - SA tax invoices from non-VAT vendors shouldn't show a VAT registration field at all
- [ ] **T5.6.6** Compare the Naledi document side-by-side with the Kgosi document (same template).
  The Kgosi version should have populated fields; the Naledi version should handle blanks
  without looking broken.

**Expected behaviour** (any of these is acceptable):
1. Pre-generation validation warns about missing fields
2. Template uses conditional blocks to hide empty fields
3. Renderer substitutes a sensible fallback ("N/A", "—", or omits the line)

**Unacceptable behaviour** (log as GAP):
1. Blank value with dangling label ("Registration: ")
2. Unresolved `{{...}}` syntax in output
3. No indication to the user that data is missing

---

### Track 6 — Document Acceptance / E-Signing

**Goal**: Test the full acceptance workflow — firm sends a generated document for e-signing,
client views it in the portal, types their name to accept, system generates a Certificate
of Acceptance with SHA-256 hash.

#### T6.1 — Send Document for Acceptance

**Actor**: Alice (Owner)

- [ ] **T6.1.1** Generate an engagement letter (e.g., `engagement-letter-bookkeeping` for Kgosi)
- [ ] **T6.1.2** After generation, find the "Send for Acceptance" action (button on generated document or document detail)
- [ ] **T6.1.3** Select recipient (Kgosi portal contact)
- [ ] **T6.1.4** Configure expiry (e.g., 14 days) if option exists
- [ ] **T6.1.5** Send → verify acceptance request created with status PENDING
- [ ] **T6.1.6** Check Mailpit → email sent to Kgosi contact with acceptance link

#### T6.2 — Client Views and Accepts

- [ ] **T6.2.1** Open the acceptance link from the email (or navigate to portal acceptance page)
- [ ] **T6.2.2** Verify the PDF is displayed in-browser (or a preview is shown)
- [ ] **T6.2.3** Verify document content matches what was generated (client name, clauses, etc.)
- [ ] **T6.2.4** Type full name in the acceptance field (e.g., "Thabo Kgosi")
- [ ] **T6.2.5** Click "I Accept" (or equivalent acceptance button)
- [ ] **T6.2.6** Verify confirmation screen shown to client

#### T6.3 — Firm Verifies Acceptance

**Actor**: Alice

- [ ] **T6.3.1** Navigate to the generated document or acceptance tracking page
- [ ] **T6.3.2** Verify acceptance status = ACCEPTED
- [ ] **T6.3.3** Verify acceptance metadata displayed:
  - Accepted by: "Thabo Kgosi"
  - Timestamp: today's date/time
  - IP address: recorded (any value)
- [ ] **T6.3.4** Certificate of Acceptance PDF available for download
- [ ] **T6.3.5** Download certificate → read PDF → verify it contains:
  - Document title/reference
  - Acceptor name: "Thabo Kgosi"
  - Acceptance timestamp
  - SHA-256 hash of the original document
- [ ] **T6.3.6** Audit trail entry created for the acceptance event

#### T6.4 — Expired Acceptance (Edge Case)

- [ ] **T6.4.1** Note whether there's a way to test expiry without time manipulation
- [ ] **T6.4.2** If expiry processor runs on a schedule, check if expired requests are marked correctly
- [ ] **T6.4.3** If not testable in E2E: note as observation, verify via backend integration tests

---

### Track 7 — DOCX Template Pipeline (Manual)

**Goal**: Verify the Word document upload, field discovery, merge, and PDF conversion pipeline.

**Method**: Manual testing by founder. The DOCX pipeline depends on LibreOffice for PDF
conversion which may not be available in the E2E Docker stack.

#### T7.1 — Preparation

- [ ] **T7.1.1** Create a `.docx` file with these merge fields:
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
- [ ] **T7.1.2** Save as `test-engagement.docx`

#### T7.2 — Upload and Field Discovery

- [ ] **T7.2.1** Navigate to Settings > Templates (or template management page)
- [ ] **T7.2.2** Upload `test-engagement.docx` as a new DOCX template
- [ ] **T7.2.3** Verify the system auto-discovers these variables:
  - customer.name
  - customer.customFields.company_registration_number
  - customer.customFields.vat_number
  - project.name
  - org.name
  - generatedAt
- [ ] **T7.2.4** Set template scope to PROJECT
- [ ] **T7.2.5** Save template

#### T7.3 — Generate Filled Document

- [ ] **T7.3.1** Navigate to Kgosi "Monthly Bookkeeping" project → Generate Document
- [ ] **T7.3.2** Select the uploaded DOCX template
- [ ] **T7.3.3** Generate → download the filled `.docx`
- [ ] **T7.3.4** Open in Word/LibreOffice → verify:
  - "To: Kgosi Construction (Pty) Ltd" (not `{{customer.name}}`)
  - "Registration: 2019/123456/07"
  - "VAT: 4520012345"
  - "RE: Monthly Bookkeeping — Kgosi"
  - Org name resolved
  - Date resolved
- [ ] **T7.3.5** No `{{...}}` tokens remain in the document

#### T7.4 — PDF Conversion

- [ ] **T7.4.1** If "Download as PDF" option exists → click it
- [ ] **T7.4.2** Verify PDF downloads (non-zero size)
- [ ] **T7.4.3** Open PDF → verify content matches the filled DOCX
- [ ] **T7.4.4** If PDF conversion fails: note error and whether LibreOffice is available in the environment

---

## 5. Content Verification Prompts (for AI Agent)

When the QA agent downloads and reads a PDF, it should use these structured prompts
to analyse the content:

### 5.1 General Document Verification Prompt

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
   - ZAR or "R" prefix (not USD, $, EUR, or £)
   - Thousands separator (space or comma) for amounts > 999
   - Two decimal places for amounts with cents
   Report any non-ZAR currency references.

4. DATE FORMAT: Check all dates. They should be human-readable
   (not raw ISO 8601 like "2026-02-28T00:00:00Z" or epoch timestamps).
   Report any machine-formatted dates.

5. CUSTOMER DATA MATCH: The document should reference:
   - Customer: [EXPECTED_CUSTOMER_NAME]
   - Any custom field values provided in the checklist
   Report if the customer name is wrong or absent.

6. ORG DATA: The document should reference the firm/org name in header or footer.
   Report if absent.

Report findings as:
- PASS: [description of check]
- FAIL: [description] — found: "[actual text]", expected: "[expected text]"
- WARN: [description] — [observation]
```

### 5.2 Invoice-Specific Verification Prompt

```
Analyse this invoice PDF and perform these additional checks:

1. MATH VERIFICATION:
   - For each line item: quantity × unit price should equal line total
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

### 5.3 Certificate of Acceptance Verification Prompt

```
Analyse this Certificate of Acceptance PDF and verify:

1. ACCEPTOR NAME: Should contain the typed name of the person who accepted.
   Report if missing.

2. TIMESTAMP: Should contain a date/time of acceptance. Report if missing or
   if it shows a clearly wrong date (e.g., epoch, year 1970).

3. DOCUMENT REFERENCE: Should identify which document was accepted
   (title, ID, or filename). Report if missing.

4. HASH: Should contain a SHA-256 hash (64-character hexadecimal string).
   Report if missing or if the format doesn't match hex.

5. INTEGRITY STATEMENT: Should contain language about the document's integrity
   or that the hash can be used to verify the document hasn't been modified.
```

---

## 6. Gap Reporting Format

For each content issue found, log a gap entry:

```markdown
### GAP-P49-NNN: [Short title]

**Track**: T[N].[N] — [Test case name]
**Step**: [Step number]
**Category**: content-error | missing-variable | wrong-data | format-error | missing-feature
**Severity**: blocker | major | minor | cosmetic
**Description**: [What was expected vs what was found]
**Evidence**:
- Template: [slug]
- Variable: [variable path]
- Expected: [expected value]
- Actual: [actual value or "BLANK" or "UNRESOLVED"]
- Screenshot/PDF: [path]
**Suggested fix**: [If obvious — e.g., "Map customer.customFields.vat_number to customerVatNumber in InvoiceContextBuilder"]
```

**Severity guide for content issues**:

| Severity | Criteria |
|----------|----------|
| blocker | Document cannot be generated, or contains another customer's data (data leak) |
| major | Variable resolves to blank where a value is expected; math is wrong on invoice; clause missing entirely |
| minor | Date in wrong format; currency symbol inconsistent; extra whitespace from empty optional field |
| cosmetic | Slight formatting issue; optional field blank but acceptable |

---

## 7. Success Criteria

| Criterion | Target |
|-----------|--------|
| All 8 templates generate without error | 100% |
| No unresolved `{{...}}` tokens in any generated document | 0 occurrences |
| Custom field values flow correctly from customer to document | 100% of seeded fields |
| Invoice math is correct (line totals, subtotal, tax, total) | 100% |
| All required clauses present in engagement letters | 100% |
| Clause variables resolve within clause bodies | 100% |
| Information request full loop completes (send → upload → review) | End-to-end PASS |
| Document acceptance full loop completes (send → accept → certificate) | End-to-end PASS |
| Cross-customer data isolation (no data leaks between customers) | 100% |
| Zero blocker gaps | 0 |

---

## 8. Execution Notes

### Agent Execution Order

1. **Track 0 — Data Preparation**: Run 90-day lifecycle seed (T0.1), populate all custom
   field values (T0.2-T0.5), configure org settings (T0.6), set project custom fields (T0.7),
   create portal contacts (T0.8), pass readiness checkpoint (T0.9). **STOP if T0.9 fails.**
2. **Track 5 — Custom Field → Document Flow**: Verify the data chain works before testing
   all templates. If T5.1 shows fields don't flow into documents, investigate before
   running 8 templates that will all show the same failure.
3. **Track 1 — Template Variable Fidelity**: Core of this plan. Run all 8 templates.
4. **Track 2 — Clause Assembly**: Depends on T1 templates generating successfully.
5. **Track 3 — Engagement Letter Pipeline**: Builds on T1 + T2.
6. **Track 6 — Document Acceptance / E-Signing**: Builds on T3 or T1 generated documents.
7. **Track 4 — Information Request Full Loop**: Independent of document generation.
8. **Track 7 — DOCX Pipeline**: Manual testing by founder (last, independent).

### PDF Download Location

Agent should download PDFs to a known directory for reading:

```
qa/testplan/artifacts/
├── t1.1-engagement-letter-kgosi.pdf
├── t1.5-invoice-za-kgosi.pdf
├── t1.6-statement-of-account-kgosi.pdf
├── t1.6-statement-of-account-naledi.pdf
├── t3.3-engagement-letter-vukani-advisory.pdf
├── t6.3-certificate-of-acceptance-kgosi.pdf
└── ...
```

### When to Stop

- If Track 5 (custom fields) shows that field values are not populated → STOP.
  Seed data is broken. Fix seed before continuing.
- If Track 1 first template shows all variables unresolved → STOP.
  Rendering pipeline has a systemic issue. Investigate before testing more templates.
- Otherwise: complete all tracks and produce the gap report.
