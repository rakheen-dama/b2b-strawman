# 90-Day Accelerated Lifecycle Script
## Thornton & Associates — SA Accounting Firm
### Platform: DocTeams | Vertical Profile: accounting-za | E2E Stack: http://localhost:3001

This script simulates the first 90 days of "Thornton & Associates", a 3-person Johannesburg
accounting firm, on the DocTeams platform. The script is executed against the E2E mock-auth
stack with Playwright MCP. Each "day" represents a key moment in the firm's lifecycle.

**Roles:**
- Alice (Owner) — Senior Accountant/Partner
- Bob (Admin) — Bookkeeper/Practice Administrator
- Carol (Member) — Junior Accountant

**Clients (to be created):**
- Kgosi Construction (Pty) Ltd — retainer client, R5,500/month
- Naledi Hair Studio — hourly client, R850/hr (sole proprietor)
- Vukani Tech Solutions (Pty) Ltd — retainer + advisory, R8,000/month + overflow
- Moroka Family Trust — fixed fee per engagement

**Checkpoint notation:**
- [ ] PASS — action succeeded and produced expected outcome
- [ ] FAIL — action failed or produced wrong outcome (log gap immediately)
- [ ] PARTIAL — action succeeded but with friction or partial result

---

## Day 0 — Firm Setup

**Context**: Thornton & Associates has just signed up for DocTeams. Alice, the senior partner, logs in for the first time and configures the platform for their South African accounting practice. Everything hinges on this first session — if the vertical profile is correctly applied, the firm gets accounting-specific fields, templates, checklists, and automation rules out of the box.

**Actor**: Alice (Owner)

**Prerequisites**: E2E stack running on port 3001 with a freshly seeded org. The default seed creates org "E2E Test Organization" with Alice (owner), Bob (admin), Carol (member), one sample customer "Acme Corp", and one project "Website Redesign". The vertical profile may or may not be applied — Day 0 begins by verifying this.

### Actions

#### 0.1 — Authenticate and verify dashboard

1. Navigate to: `http://localhost:3001/mock-login`
2. Verify Alice Owner is the default selection
3. Click "Sign In"
4. Wait for redirect to dashboard at `/org/e2e-test-org/dashboard`
5. Look for a "Getting Started" checklist or onboarding wizard on the dashboard
6. Note which items appear in the checklist (if any)

- Expected: Dashboard loads with Alice's name visible. A getting started checklist prompts the new firm to complete setup steps (configure branding, add clients, set rates, etc.)

#### 0.2 — Verify vertical profile is applied

1. Navigate to: `/org/e2e-test-org/settings`
2. Look for any indication of the vertical profile ("accounting-za", "Accounting", or "Industry" field)
3. Navigate to: `/org/e2e-test-org/settings/custom-fields`
4. Look for "SA Accounting — Client Details" custom field group
5. Look for "SA Accounting — Engagement Details" custom field group

- Expected: The accounting vertical profile is applied and both custom field groups are visible and enabled. If NOT present, this is a gap — the E2E seed does not set `industry: "Accounting"` during org provisioning, so the `accounting-za` packs may not have been seeded.

#### 0.3 — Configure org branding

1. Navigate to: `/org/e2e-test-org/settings`
2. Change org name from "E2E Test Organization" to "Thornton & Associates"
3. Upload a firm logo (or note if logo upload is available)
4. Set brand colour to `#1B365D` (navy blue — professional accounting firm aesthetic)
5. Set default currency to ZAR (South African Rand)
6. Save settings

- Expected: Org name updates to "Thornton & Associates". Brand colour is saved. Currency is set to ZAR. These values will appear in generated documents via template variables `org.name`, `org.brandColor`.

#### 0.4 — Configure billing rates

1. Navigate to: `/org/e2e-test-org/settings/rates`
2. Set billing rates per team member:
   - Alice (Owner): R1,500/hr
   - Bob (Admin): R850/hr
   - Carol (Member): R450/hr
3. Set cost rates per team member:
   - Alice: R650/hr
   - Bob: R350/hr
   - Carol: R180/hr
4. Save rate configuration

- Expected: Billing and cost rates are saved for all three members. These rates will be used for time entry rate snapshots and profitability calculations. Note: the accounting vertical profile manifest specifies these defaults, but they are NOT automatically seeded (GAP-006) — manual configuration is required.

#### 0.5 — Configure SA VAT

1. Navigate to: `/org/e2e-test-org/settings/tax`
2. Add or verify a tax rate: "VAT" at 15%
3. Set it as the default tax rate
4. Save

- Expected: VAT 15% is configured as the default tax rate. This will be applied to invoices.

#### 0.6 — Verify team members

1. Navigate to: `/org/e2e-test-org/team`
2. Verify three members are listed:
   - Alice Owner — role: owner
   - Bob Admin — role: admin
   - Carol Member — role: member

- Expected: All three team members are listed with correct roles. No additional members need to be invited (E2E seed handles this).

#### 0.7 — Verify accounting field pack

1. Navigate to: `/org/e2e-test-org/settings/custom-fields`
2. Verify "SA Accounting — Client Details" group exists with 16 fields:
   - company_registration_number, trading_as, vat_number, sars_tax_reference, sars_efiling_profile, financial_year_end, entity_type, industry_sic_code, registered_address, postal_address, primary_contact_name, primary_contact_email, primary_contact_phone, fica_verified, fica_verification_date, referred_by
3. Verify "SA Accounting — Engagement Details" group exists with 5 fields:
   - engagement_type, tax_year, sars_submission_deadline, assigned_reviewer, complexity
4. Verify both groups are enabled (applied to customer and project entity types respectively)

- Expected: Both custom field groups are present and enabled. All 21 fields are listed with correct types and options.

#### 0.8 — Verify accounting template pack

1. Navigate to: `/org/e2e-test-org/settings/templates`
2. Verify 7 templates are listed:
   - Engagement Letter — Monthly Bookkeeping
   - Engagement Letter — Annual Tax Return
   - Engagement Letter — Advisory
   - Monthly Report Cover
   - SA Tax Invoice
   - Statement of Account
   - FICA Confirmation Letter
3. Click into "Engagement Letter — Monthly Bookkeeping" to verify it has template content with variables (`customer.name`, `org.name`, etc.)

- Expected: All 7 accounting templates are available. Templates contain Thymeleaf variables that will resolve against customer/project/org context.

#### 0.9 — Verify FICA/KYC checklist template

1. Navigate to: `/org/e2e-test-org/settings/checklists`
2. Look for "FICA/KYC — SA Accounting" checklist template (slug: `fica-kyc-za-accounting`)
3. Click into it and verify 9 items:
   - Certified ID Copy (required)
   - Proof of Residence (required)
   - Company Registration CM29/CoR14.3 (required)
   - Tax Clearance Certificate (required)
   - Bank Confirmation Letter (required)
   - Proof of Business Address (optional)
   - Resolution / Mandate (optional)
   - Beneficial Ownership Declaration (required)
   - Source of Funds Declaration (optional, risk-based)

- Expected: FICA checklist template exists with all 9 items. All items have `requiresDocument: true`. The checklist is NOT auto-instantiated — it must be manually applied to each new client.

#### 0.10 — Verify clause library

1. Navigate to: `/org/e2e-test-org/settings/clauses`
2. Verify 7 accounting clauses are listed:
   - Limitation of Liability (Accounting)
   - Fee Escalation
   - Termination (Accounting)
   - Confidentiality (Accounting)
   - Document Retention (Accounting)
   - Third-Party Reliance
   - Electronic Communication Consent
3. Click into one clause to verify it has content with variables

- Expected: All 7 clauses are present with Tiptap JSON bodies containing `org.name` and `customer.name` variables.

#### 0.11 — Verify and apply automation rule templates

1. Navigate to: `/org/e2e-test-org/settings/automations`
2. Look for available automation templates or pre-created rules:
   - FICA Reminder (7 days) — trigger: CUSTOMER_STATUS_CHANGED, action: SEND_NOTIFICATION after 7-day delay
   - Engagement Budget Alert (80%) — trigger: BUDGET_THRESHOLD_REACHED, action: SEND_NOTIFICATION immediately
   - Invoice Overdue (30 days) — trigger: INVOICE_STATUS_CHANGED to OVERDUE, action: SEND_NOTIFICATION + SEND_EMAIL
3. If templates need to be "activated" or "applied", do so for all three
4. Note: Three additional rules were desired but excluded due to missing trigger types (GAP-001, GAP-002, GAP-003)

- Expected: Three automation rules are available and active. The rules use existing trigger types. The three excluded rules (PROPOSAL_SENT, FIELD_DATE_APPROACHING, CHECKLIST_COMPLETED) are absent — this is a known gap.

#### 0.12 — Verify request template pack

1. Navigate to: `/org/e2e-test-org/settings/request-templates`
2. Look for "Year-End Information Request (SA)" template (slug: `year-end-info-request-za`)
3. Click into it and verify 8 items:
   - Trial Balance (required, FILE_UPLOAD)
   - Bank Statements — Full Year (required, FILE_UPLOAD)
   - Loan Agreements (required, FILE_UPLOAD)
   - Fixed Asset Register (required, FILE_UPLOAD)
   - Debtors Age Analysis (optional, FILE_UPLOAD)
   - Creditors Age Analysis (optional, FILE_UPLOAD)
   - Insurance Schedule (optional, FILE_UPLOAD)
   - Payroll Summary (required, FILE_UPLOAD)

- Expected: Request template exists with all 8 items. This template will be used on Day 75 for year-end information requests.

#### 0.13 — Check terminology overrides

1. While navigating the platform, observe sidebar navigation labels
2. Note whether the UI shows:
   - "Projects" or "Engagements"
   - "Customers" or "Clients"
   - "Time Entries" or "Time Records"
   - "Rate Cards" or "Fee Schedule"

- Expected: The UI shows generic platform terminology ("Projects", "Customers", etc.) because terminology overrides are not loaded at runtime (GAP-005). This is a cosmetic issue — the firm can still use the platform, but it does not feel like an accounting-specific tool.

### Checkpoints

- [ ] Dashboard loads with getting started checklist visible
- [ ] Vertical profile "accounting-za" is applied to the org (or gap logged if absent)
- [ ] Org name set to "Thornton & Associates"
- [ ] Brand colour set to #1B365D
- [ ] Default currency set to ZAR
- [ ] Billing rates configured: Alice R1,500/hr, Bob R850/hr, Carol R450/hr
- [ ] Cost rates configured: Alice R650/hr, Bob R350/hr, Carol R180/hr
- [ ] VAT 15% configured as default tax rate
- [ ] All 3 team members visible with correct roles
- [ ] SA Accounting — Client Details field group present with 16 fields
- [ ] SA Accounting — Engagement Details field group present with 5 fields
- [ ] 7 accounting document templates visible in template settings
- [ ] FICA/KYC checklist template present with 9 items
- [ ] 7 accounting clauses present in clause library
- [ ] 3 automation rules available and active
- [ ] Year-End Information Request template present with 8 items
- [ ] Terminology shows generic labels (known gap GAP-005 confirmed)

---

## Day 1 — First Client Onboarding: Kgosi Construction

**Context**: The firm's first real client walks through the door. Thabo Kgosi runs a mid-sized construction company in Johannesburg. He needs monthly bookkeeping services. Bob, the practice administrator, captures the client details while Alice prepares the engagement letter. This is the most critical test of the platform — can a real client be onboarded with all the accounting-specific information a SA firm needs?

**Actor**: Bob (Admin) creates the client; Alice (Owner) creates the engagement letter

**Prerequisites**: Day 0 complete — org configured with branding, rates, tax, and all accounting packs verified.

### Actions

#### 1.1 — Bob creates Kgosi Construction as a new client

1. Sign in as Bob: Navigate to `http://localhost:3001/mock-login`, select "Bob Admin", click "Sign In"
2. Navigate to: `/org/e2e-test-org/customers`
3. Click "New Customer" (or equivalent button)
4. Fill core fields:
   - Name: "Kgosi Construction (Pty) Ltd"
   - Email: "accounts@kgosiconstruction.co.za"
   - Phone: "+27 11 483 2910"
5. Save the customer record
6. Navigate to the newly created customer's detail page
7. Locate the "SA Accounting — Client Details" custom field section
8. Fill ALL accounting custom fields:
   - Company Registration Number: `2019/347821/07`
   - Trading As: `Kgosi Construction`
   - VAT Number: `4830291746`
   - SARS Tax Reference: `9301234567`
   - SARS eFiling Profile Number: (leave blank)
   - Financial Year-End: `2025-02-28`
   - Entity Type: `Pty Ltd`
   - Industry SIC Code: `41000` (Building construction)
   - Registered Address: `15 Industria Road, Industria West, Johannesburg, 2093`
   - Postal Address: `PO Box 4521, Industria West, 2093`
   - Primary Contact Name: `Thabo Kgosi`
   - Primary Contact Email: `thabo@kgosiconstruction.co.za`
   - Primary Contact Phone: `+27 82 555 1234`
   - FICA Verified: `Not Started`
   - FICA Verification Date: (leave blank)
   - Referred By: `Existing client referral — Mkhize Electrical`
9. Save custom field values

- Expected: Customer "Kgosi Construction (Pty) Ltd" is created with status PROSPECT. All 16 custom fields are populated and saved. The customer detail page shows all field values in the accounting field group section.

#### 1.2 — Initiate FICA checklist for Kgosi Construction

1. On the Kgosi Construction customer detail page, look for a "Checklists" tab or section
2. Click to add/instantiate a checklist
3. Select "FICA/KYC — SA Accounting" template
4. Verify the checklist instantiates with 9 items
5. Mark the first 2 items as complete:
   - "Certified ID Copy" — mark done (Thabo brought his certified ID copy to the meeting)
   - "Proof of Residence" — mark done (utility bill provided)
6. Leave the remaining 7 items incomplete

- Expected: FICA checklist is instantiated for Kgosi Construction with 9 items. 2 items marked complete, 7 remaining. The checklist is visible on the customer detail page.

#### 1.3 — Verify customer lifecycle state

1. On the Kgosi Construction detail page, look for the customer lifecycle status
2. Verify the customer is in PROSPECT status
3. Check if there is a visible path to advance through ONBOARDING to ACTIVE

- Expected: Customer shows PROSPECT status. The lifecycle progression PROSPECT -> ONBOARDING -> ACTIVE is visible or documented. FICA completion is part of the onboarding gate.

#### 1.4 — Send information request via portal

1. On the Kgosi Construction detail page, look for an "Information Requests" section or button
2. Create a new information request (this may be a free-form request, not the year-end template — that is for Day 75)
3. Add a request item: "Please provide your latest 3 months of bank statements for all business accounts"
4. Send the request to the client contact email (thabo@kgosiconstruction.co.za)
5. Check Mailpit at `http://localhost:8026` for the outbound email
6. Look for a portal link in the email body
7. Navigate to the portal link to verify the client can see the request

- Expected: Information request is created and sent. Email appears in Mailpit with a portal link. The portal page shows the request item. Client can respond/upload documents via portal.

#### 1.5 — Alice creates engagement letter for monthly bookkeeping

1. Sign in as Alice: Navigate to `http://localhost:3001/mock-login`, select "Alice Owner", click "Sign In"
2. Navigate to Kgosi Construction's customer page or engagement/project creation flow
3. Generate a document using the "Engagement Letter — Monthly Bookkeeping" template
4. Verify template variables resolve:
   - `customer.name` → "Kgosi Construction (Pty) Ltd"
   - `customer.customFields.company_registration_number` → "2019/347821/07"
   - `org.name` → "Thornton & Associates"
   - `org.brandColor` → "#1B365D"
5. Verify the engagement letter includes the required clauses:
   - Limitation of Liability (Accounting)
   - Termination (Accounting)
   - Confidentiality (Accounting)
   - Document Retention (Accounting)
6. Add the optional clauses:
   - Fee Escalation
   - Electronic Communication Consent
7. Set the fee: R5,500 per month
8. Preview/generate the document as PDF

- Expected: Engagement letter is generated with all variables resolved and all selected clauses included. The PDF looks professional with the firm's branding. Fee of R5,500/month is stated in the letter.

#### 1.6 — Send engagement letter for acceptance

1. From the generated engagement letter, look for a "Send for Acceptance" or "Send to Client" action
2. Send the engagement letter to Thabo Kgosi (thabo@kgosiconstruction.co.za)
3. Check Mailpit for the outbound email
4. Look for a portal link where the client can review and accept the letter
5. Navigate to the portal link

- Expected: Engagement letter is sent via email. Mailpit shows the email. Portal link allows the client to review the engagement letter and accept it.

#### 1.7 — Accept engagement letter (simulate client action)

1. Via the portal link from step 1.6, review the engagement letter
2. Click "Accept" or equivalent
3. Verify the acceptance is recorded

- Expected: Engagement letter status changes to "Accepted". The acceptance is logged with timestamp.

#### 1.8 — Create engagement (project) from accepted engagement letter

1. Navigate back to Alice's org view
2. Create a new project/engagement for Kgosi Construction:
   - Project Name: "Kgosi Construction — Monthly Bookkeeping 2026"
   - Link to Customer: Kgosi Construction (Pty) Ltd
   - Fill engagement custom fields:
     - Engagement Type: `Monthly Bookkeeping`
     - Tax Year: (leave blank — not applicable for bookkeeping)
     - Assigned Reviewer: `Alice Owner`
     - Complexity: `Moderate`
3. Save the project

- Expected: Project is created and linked to Kgosi Construction. Engagement custom fields are populated. The project appears in both the project list and the customer's project tab.

#### 1.9 — Set up retainer for Kgosi Construction

1. Navigate to: `/org/e2e-test-org/retainers`
2. Create a new retainer agreement:
   - Client: Kgosi Construction (Pty) Ltd
   - Project: Kgosi Construction — Monthly Bookkeeping 2026
   - Amount: R5,500/month
   - Period: Monthly
   - Start Date: 2026-01-01
3. Save the retainer

- Expected: Retainer agreement is created and linked to the project. The retainer dashboard shows Kgosi Construction with R5,500/month.

### Checkpoints

- [ ] Kgosi Construction (Pty) Ltd created as customer with status PROSPECT
- [ ] All 16 accounting custom fields populated and saved
- [ ] FICA checklist instantiated with 9 items, 2 marked complete
- [ ] Customer lifecycle shows PROSPECT status with visible progression path
- [ ] Information request created and sent — email appears in Mailpit
- [ ] Portal link in email leads to accessible information request page
- [ ] Engagement letter generated with correct variable resolution
- [ ] All 4 required clauses included in engagement letter
- [ ] Engagement letter sent for acceptance — email appears in Mailpit
- [ ] Portal shows engagement letter for client acceptance
- [ ] Engagement letter accepted via portal
- [ ] Project "Kgosi Construction — Monthly Bookkeeping 2026" created with engagement custom fields
- [ ] Retainer agreement created: R5,500/month linked to Kgosi Construction project

---

## Day 2 — Client Onboarding: Naledi Hair Studio and Vukani Tech Solutions

**Context**: Word of mouth is working. Two new clients approach the firm in the same week. Naledi Mokoena runs a small hair studio as a sole proprietor — she needs basic tax advisory on an hourly basis. Vukani Tech Solutions is a growing tech startup that needs full monthly bookkeeping plus ad-hoc advisory work. These two clients test different billing models: hourly and retainer-plus-overflow.

**Actor**: Bob (Admin)

**Prerequisites**: Day 1 complete — Kgosi Construction created with all fields, FICA started, engagement letter sent, project created, retainer set up.

### Actions

#### 2.1 — Create Naledi Hair Studio

1. Sign in as Bob
2. Navigate to: `/org/e2e-test-org/customers`
3. Click "New Customer"
4. Fill core fields:
   - Name: "Naledi Hair Studio"
   - Email: "naledi@naledihair.co.za"
   - Phone: "+27 72 881 4420"
5. Save the customer
6. Navigate to the customer detail page
7. Fill accounting custom fields:
   - Company Registration Number: (leave blank — sole proprietor, not registered)
   - Trading As: `Naledi Hair Studio`
   - VAT Number: (leave blank — below VAT threshold)
   - SARS Tax Reference: `8807156234089`
   - Financial Year-End: `2025-02-28`
   - Entity Type: `Sole Proprietor`
   - Registered Address: `Shop 12, Eastgate Mall, Bedfordview, 2007`
   - Primary Contact Name: `Naledi Mokoena`
   - Primary Contact Email: `naledi@naledihair.co.za`
   - Primary Contact Phone: `+27 72 881 4420`
   - FICA Verified: `Not Started`
8. Save custom fields

- Expected: Customer created with sole proprietor entity type. VAT and company registration fields are blank (correct for a sole proprietor below the VAT threshold). Entity type "Sole Proprietor" is selectable from the dropdown.

#### 2.2 — Initiate FICA checklist for Naledi

1. On Naledi's customer detail page, instantiate the FICA/KYC checklist
2. Note: For a sole proprietor, items like "Company Registration (CM29/CoR14.3)" and "Resolution / Mandate" may not apply — check if there is entity-type-specific filtering

- Expected: FICA checklist instantiates with 9 items. There is likely no entity-type filtering (all 9 items appear regardless of entity type). This is a minor gap if sole proprietors see company-specific items.

#### 2.3 — Create project for Naledi (hourly billing)

1. Navigate to: `/org/e2e-test-org/projects`
2. Create new project:
   - Name: "Naledi Hair Studio — Tax Advisory 2026"
   - Customer: Naledi Hair Studio
   - Engagement Type: `Advisory`
   - Complexity: `Simple`
3. Save the project
4. Note: No retainer for Naledi — she is billed hourly at Alice's rate (R1,500/hr for advisory) or Bob's rate (R850/hr for admin)

- Expected: Project created and linked to Naledi. No retainer is set up — billing will be based on time entries at the configured hourly rates.

#### 2.4 — Create Vukani Tech Solutions

1. Navigate to: `/org/e2e-test-org/customers`
2. Click "New Customer"
3. Fill core fields:
   - Name: "Vukani Tech Solutions (Pty) Ltd"
   - Email: "finance@vukanitech.co.za"
   - Phone: "+27 11 326 7800"
4. Save the customer
5. Fill accounting custom fields:
   - Company Registration Number: `2021/892410/07`
   - Trading As: `Vukani Tech`
   - VAT Number: `4921087365`
   - SARS Tax Reference: `0412567890`
   - Financial Year-End: `2025-02-28`
   - Entity Type: `Pty Ltd`
   - Industry SIC Code: `62020` (IT consultancy)
   - Registered Address: `Unit 4, Bryanston Gate Business Park, Bryanston, 2021`
   - Postal Address: `PO Box 78104, Sandton, 2146`
   - Primary Contact Name: `Sipho Ndaba`
   - Primary Contact Email: `sipho@vukanitech.co.za`
   - Primary Contact Phone: `+27 83 412 8890`
   - FICA Verified: `Not Started`
   - Referred By: `Google search`
6. Save custom fields

- Expected: Customer created with all Pty Ltd-specific fields populated including VAT number and company registration.

#### 2.5 — Initiate FICA for Vukani and create engagement

1. Instantiate FICA/KYC checklist for Vukani Tech Solutions
2. Create project:
   - Name: "Vukani Tech — Monthly Bookkeeping 2026"
   - Customer: Vukani Tech Solutions (Pty) Ltd
   - Engagement Type: `Monthly Bookkeeping`
   - Complexity: `Moderate`
3. Set up retainer:
   - Amount: R8,000/month
   - Period: Monthly
   - Start Date: 2026-01-01

- Expected: FICA checklist instantiated, project created, retainer set at R8,000/month. Vukani Tech uses a retainer model with hourly overflow for advisory work beyond the monthly scope.

### Checkpoints

- [ ] Naledi Hair Studio created with entity type "Sole Proprietor"
- [ ] Naledi's custom fields saved (VAT blank, no company registration)
- [ ] FICA checklist instantiated for Naledi (note entity-type filtering behaviour)
- [ ] Naledi project created: "Naledi Hair Studio — Tax Advisory 2026"
- [ ] No retainer set for Naledi (hourly billing model)
- [ ] Vukani Tech Solutions created with entity type "Pty Ltd"
- [ ] All Vukani custom fields saved including VAT and company registration
- [ ] FICA checklist instantiated for Vukani Tech
- [ ] Vukani project created: "Vukani Tech — Monthly Bookkeeping 2026"
- [ ] Vukani retainer created: R8,000/month

---

## Day 3 — Client Onboarding: Moroka Family Trust

**Context**: The firm's fourth client is a family trust — the most complex entity type from a regulatory perspective. Moroka Family Trust handles the estate of the late Mr. Moroka. Alice handles this one personally due to the complexity. This tests whether the platform can accommodate trust-specific requirements.

**Actor**: Alice (Owner)

**Prerequisites**: Days 1-2 complete — three clients onboarded with different entity types and billing models.

### Actions

#### 3.1 — Create Moroka Family Trust

1. Sign in as Alice
2. Navigate to: `/org/e2e-test-org/customers`
3. Click "New Customer"
4. Fill core fields:
   - Name: "Moroka Family Trust"
   - Email: "moroka.trust@mweb.co.za"
   - Phone: "+27 12 342 1150"
5. Save the customer
6. Fill accounting custom fields:
   - Company Registration Number: (leave blank — trusts use IT numbers, not CIPC)
   - Trading As: (leave blank)
   - VAT Number: (leave blank — trusts typically not VAT-registered)
   - SARS Tax Reference: `0287654321`
   - Financial Year-End: `2025-02-28`
   - Entity Type: `Trust`
   - Registered Address: `22 Church Street, Pretoria Central, 0002`
   - Primary Contact Name: `Lerato Moroka`
   - Primary Contact Email: `lerato.moroka@gmail.com`
   - Primary Contact Phone: `+27 79 234 5678`
   - FICA Verified: `Not Started`
7. Save custom fields

- Expected: Customer created with entity type "Trust". Trust-specific fields (company registration, VAT) are left blank. Note: the platform does not have trust-specific fields like trust registration number, Master's reference, or trust deed date — this is a vertical-specific gap.

#### 3.2 — Initiate FICA for Moroka Trust

1. Instantiate FICA/KYC checklist
2. Note the applicability of each item to a trust entity:
   - "Certified ID Copy" — applies (of trustees)
   - "Company Registration (CM29/CoR14.3)" — does NOT apply to trusts (should be Letters of Authority from the Master)
   - "Resolution / Mandate" — applies (trustee resolution)
   - "Beneficial Ownership Declaration" — applies (critical for trusts)
3. Mark nothing complete yet — documents to be collected

- Expected: FICA checklist instantiates with all 9 items. There is no entity-type-specific filtering, so trust-inappropriate items like "Company Registration" appear. This is a known limitation of the generic checklist approach.

#### 3.3 — Create engagements for Moroka Trust (fixed fee)

1. Create first project:
   - Name: "Moroka Trust — Annual Compliance 2026"
   - Customer: Moroka Family Trust
   - Engagement Type: `Trust Administration`
   - Complexity: `Complex`
2. Create second project:
   - Name: "Moroka Trust — IT12TR Tax Return 2026"
   - Customer: Moroka Family Trust
   - Engagement Type: `Annual Tax Return`
   - Tax Year: `2026`
   - SARS Submission Deadline: `2026-10-31`
   - Complexity: `Moderate`
3. Note: Moroka Trust uses fixed-fee billing per engagement (no retainer, no hourly). Set a project budget:
   - Annual Compliance: R12,000 fixed
   - Tax Return: R8,500 fixed
4. Navigate to project detail and look for a budget configuration option

- Expected: Both projects created with engagement-type-specific custom fields. The budget can be set per project. Fixed-fee billing means the firm invoices a flat amount upon completion, not based on time entries.

#### 3.4 — Review the client portfolio

1. Navigate to: `/org/e2e-test-org/customers`
2. Verify all 4 new clients are listed (plus the seeded "Acme Corp"):
   - Kgosi Construction (Pty) Ltd — PROSPECT/ONBOARDING
   - Naledi Hair Studio — PROSPECT
   - Vukani Tech Solutions (Pty) Ltd — PROSPECT
   - Moroka Family Trust — PROSPECT
3. Navigate to: `/org/e2e-test-org/projects`
4. Verify all engagements are listed:
   - Kgosi Construction — Monthly Bookkeeping 2026
   - Naledi Hair Studio — Tax Advisory 2026
   - Vukani Tech — Monthly Bookkeeping 2026
   - Moroka Trust — Annual Compliance 2026
   - Moroka Trust — IT12TR Tax Return 2026

- Expected: 5 clients in the client list (4 new + 1 seeded). 6 projects in the project list (5 new + 1 seeded). Client list may show custom field columns (entity type, FICA status) if list views support custom field display.

### Checkpoints

- [ ] Moroka Family Trust created with entity type "Trust"
- [ ] Trust-specific fields (company reg, VAT) correctly left blank
- [ ] FICA checklist instantiated for Trust (note entity-type filtering gap)
- [ ] "Moroka Trust — Annual Compliance 2026" project created with engagement type Trust Administration
- [ ] "Moroka Trust — IT12TR Tax Return 2026" project created with tax year and SARS deadline
- [ ] Project budgets set: R12,000 for compliance, R8,500 for tax return
- [ ] Client portfolio shows all 4 new clients with correct statuses
- [ ] Project list shows all 5 new engagements
- [ ] Entity type variety works: Pty Ltd, Sole Proprietor, Trust all accommodated

---

## Day 7 — First Real Work

**Context**: The firm has been running for a week. Carol, the junior accountant, arrives on Monday morning and checks her workload. She has bank statements to capture for Kgosi Construction and Sage reconciliation work for Vukani Tech. Bob follows up on missing documentation for Kgosi. Alice squeezes in a quick tax planning call with Naledi. This is the first test of daily work tracking.

**Actor**: Carol (Member) -> Bob (Admin) -> Alice (Owner)

**Prerequisites**: Days 0-3 complete — all 4 clients onboarded with projects and retainers configured.

### Actions

#### 7.1 — Carol checks her workload

1. Sign in as Carol: Navigate to `http://localhost:3001/mock-login`, select "Carol Member", click "Sign In"
2. Navigate to: `/org/e2e-test-org/my-work`
3. Review the My Work page — look for assigned tasks or projects
4. Note: Tasks may need to be created and assigned to Carol first. If My Work is empty, this is expected — the firm hasn't assigned tasks yet.

- Expected: My Work page loads. It may be empty if no tasks have been assigned. The page should show cross-project task visibility.

#### 7.2 — Carol logs time on Kgosi Construction

1. Navigate to: `/org/e2e-test-org/projects` and click into "Kgosi Construction — Monthly Bookkeeping 2026"
2. Look for a "Time" tab or "Log Time" button
3. Log a time entry:
   - Hours: 3.0
   - Description: "Captured bank statements and receipts for January 2026"
   - Date: 2026-01-07
4. Save the time entry
5. Verify the rate snapshot: Carol's rate should be R450/hr, so the billable amount should show R1,350.00

- Expected: Time entry created with 3.0 hours. Rate snapshot captures Carol's billing rate of R450/hr. Billable amount = R1,350.00. The time entry appears in the project's time log.

#### 7.3 — Carol logs time on Vukani Tech

1. Navigate to "Vukani Tech — Monthly Bookkeeping 2026" project
2. Log a time entry:
   - Hours: 2.0
   - Description: "Reconciled Sage accounts — January bank feeds"
   - Date: 2026-01-07
3. Save

- Expected: Time entry created with 2.0 hours at R450/hr = R900.00 billable.

#### 7.4 — Bob follows up on Kgosi documentation

1. Sign in as Bob
2. Navigate to "Kgosi Construction — Monthly Bookkeeping 2026" project
3. Look for a "Comments" section or tab
4. Add a comment: "Missing February bank statements — sent follow-up email to Thabo"
5. Log a time entry:
   - Hours: 1.0
   - Description: "Client liaison — outstanding documentation follow-up"
   - Date: 2026-01-07
6. Verify rate snapshot: Bob's rate should be R850/hr, billable amount R850.00
7. Look for document upload capability — upload a dummy PDF as "Client correspondence — January"

- Expected: Comment added to project. Time entry logged at R850/hr. Document uploaded and visible in the project's document section. Activity feed shows the comment and time entry.

#### 7.5 — Alice logs advisory time on Naledi

1. Sign in as Alice
2. Navigate to "Naledi Hair Studio — Tax Advisory 2026" project
3. Log a time entry:
   - Hours: 0.5
   - Description: "Tax planning discussion — provisional tax implications"
   - Date: 2026-01-07
4. Verify rate snapshot: Alice's rate should be R1,500/hr, billable amount R750.00

- Expected: Time entry created at R1,500/hr. Billable amount = R750.00. This is an hourly-billed client, so this 0.5hr will appear in the unbilled time summary for future invoicing.

#### 7.6 — Review time entries across the firm

1. As Alice, navigate to a reporting or time overview page
2. Look for a summary of today's time entries across all projects
3. Verify totals: Carol 5.0hr, Bob 1.0hr, Alice 0.5hr = 6.5hr total

- Expected: A reporting view shows time logged across the firm. Total hours and billable amounts are visible.

### Checkpoints

- [ ] Carol's My Work page loads (empty or with tasks)
- [ ] Carol logged 3.0hr on Kgosi Construction at R450/hr = R1,350.00
- [ ] Carol logged 2.0hr on Vukani Tech at R450/hr = R900.00
- [ ] Bob added comment on Kgosi project: "Missing February bank statements"
- [ ] Bob logged 1.0hr on Kgosi at R850/hr = R850.00
- [ ] Document uploaded to Kgosi project
- [ ] Alice logged 0.5hr on Naledi at R1,500/hr = R750.00
- [ ] Rate snapshots correct for all 3 members
- [ ] Cross-project time summary accessible

---

## Day 14 — FICA Completion and Automation Verification

**Context**: Two weeks in, Carol has been diligently collecting FICA documentation from clients. Kgosi Construction's FICA is now complete — all documents received and verified. Bob checks whether the automation rules have fired: the FICA reminder should have triggered for clients still in PROSPECT status after 7 days. This day tests the compliance workflow and automation system.

**Actor**: Carol (Member) -> Bob (Admin)

**Prerequisites**: Day 7 complete — time entries logged across projects. Kgosi Construction has 2 of 9 FICA items complete from Day 1. Additional time entries logged by Carol over the past week (~15 hours total across clients).

### Prerequisite Data for Day 14

Before executing Day 14 actions, create the following time entries to simulate a full working week:

**Time Entries (Week 2 — via project time log UI)**:

For Kgosi Construction — Monthly Bookkeeping 2026:
- Carol, 4.0hr, "Captured supplier invoices and creditor allocations — January", 2026-01-08
- Carol, 3.5hr, "Processed bank statement entries — Nedbank and FNB accounts", 2026-01-09
- Carol, 2.5hr, "Reconciled payroll entries against Sage report", 2026-01-10
- Bob, 0.5hr, "Reviewed Carol's bookkeeping entries — minor reclassification queries", 2026-01-10

For Vukani Tech — Monthly Bookkeeping 2026:
- Carol, 3.0hr, "Cloud accounting sync review — Xero bank feeds January", 2026-01-08
- Carol, 2.0hr, "Processed month-end journal entries", 2026-01-09

### Actions

#### 14.1 — Carol completes FICA for Kgosi Construction

1. Sign in as Carol
2. Navigate to Kgosi Construction customer detail page
3. Open the FICA/KYC checklist
4. Mark remaining items as complete (items 3-9):
   - Company Registration (CM29/CoR14.3) — complete
   - Tax Clearance Certificate — complete
   - Bank Confirmation Letter — complete
   - Proof of Business Address — complete
   - Resolution / Mandate — complete (director's resolution received)
   - Beneficial Ownership Declaration — complete
   - Source of Funds Declaration — complete (construction contracts provided)
5. Verify all 9 items are now marked complete
6. Check if the customer lifecycle status transitions automatically after FICA completion

- Expected: All 9 FICA items marked complete. The system should ideally trigger a lifecycle transition or notification. However, the `CHECKLIST_COMPLETED` automation trigger does not exist (GAP-003), so no automatic notification fires. Manual lifecycle transition may be required.

#### 14.2 — Update FICA status custom field

1. On Kgosi Construction detail page, edit the accounting custom fields
2. Change "FICA Verified" from "Not Started" to "Verified"
3. Set "FICA Verification Date" to 2026-01-14
4. Save

- Expected: FICA custom fields updated. These values will appear in generated documents (e.g., FICA Confirmation Letter template uses `customer.customFields.fica_verification_date`).

#### 14.3 — Transition Kgosi Construction through lifecycle

1. Look for a lifecycle transition action on the customer detail page
2. Advance Kgosi Construction from PROSPECT to ONBOARDING (if not already)
3. Complete any onboarding requirements
4. Advance to ACTIVE

- Expected: Customer lifecycle transitions: PROSPECT -> ONBOARDING -> ACTIVE. The exact mechanism depends on the platform's lifecycle implementation. Once ACTIVE, the customer can have projects, invoices, and full engagement operations.

#### 14.4 — Bob checks automation notifications

1. Sign in as Bob
2. Navigate to: `/org/e2e-test-org/notifications`
3. Look for automation-generated notifications:
   - FICA Reminder notifications for clients still in PROSPECT after 7 days (Naledi, Vukani, Moroka Trust)
   - Note: The FICA Reminder automation has a 7-day delay, and we cannot verify real-time delay execution (GAP-007)
4. Navigate to: `/org/e2e-test-org/settings/automations/executions`
5. Check the automation execution history

- Expected: Automation execution history may or may not show fired rules. The 7-day delay cannot be tested in real time. Check that the automation rule configuration is correct and the execution log page loads.

#### 14.5 — Bob reviews time entries

1. Navigate to profitability or reporting views
2. Look for a time entry summary across all projects for the past 2 weeks
3. Verify Carol's total hours (should be ~20+ hours across clients)
4. Verify Bob's total hours (should be ~2.5+ hours)

- Expected: Reporting views show accurate time summaries per team member and per project.

### Checkpoints

- [ ] All 9 FICA items marked complete for Kgosi Construction
- [ ] FICA Verified custom field updated to "Verified" with date 2026-01-14
- [ ] Kgosi Construction lifecycle transitioned to ACTIVE
- [ ] Notification page loads and shows any automation-triggered notifications
- [ ] Automation execution history accessible
- [ ] FICA Reminder delay cannot be verified in real time (GAP-007 confirmed)
- [ ] CHECKLIST_COMPLETED trigger absence confirmed (GAP-003)
- [ ] Time entry summaries show accurate totals across 2 weeks

---

## Day 30 — First Billing Cycle

**Context**: January is over. The firm has been working steadily across all four clients. It is time to close the month, send invoices, and get paid. Bob runs the billing cycle: close retainer periods for Kgosi and Vukani, create an hourly invoice for Naledi, and review all invoices before sending. Alice reviews the firm's profitability for the first month. This is the acid test — can the platform handle an SA accounting firm's billing workflow end to end?

**Actor**: Bob (Admin) -> Alice (Owner)

**Prerequisites**: 4 weeks of time entries exist across all clients. Retainer periods opened for January.

### Prerequisite Data for Day 30

Before executing Day 30 actions, create the following data to simulate 30 days of activity:

**Time Entries (via UI — log on individual project pages)**:

For Kgosi Construction — Monthly Bookkeeping 2026:
- Week 1: Carol, 4.0hr, "Captured client's bank statements and receipts — January 2026", 2026-01-06
- Week 1: Carol, 2.5hr, "Reconciled Sage payroll accounts", 2026-01-07
- Week 2: Carol, 3.5hr, "Processed supplier invoices and allocations", 2026-01-13
- Week 2: Bob, 1.0hr, "Client liaison — outstanding bank statements", 2026-01-14
- Week 3: Carol, 4.0hr, "Prepared month-end journals and accruals", 2026-01-20
- Week 3: Bob, 0.5hr, "Reviewed Carol's work — minor queries sent", 2026-01-21
- Week 4: Carol, 3.0hr, "Management accounts preparation", 2026-01-27
- Week 4: Alice, 0.5hr, "Reviewed January management accounts before delivery", 2026-01-28

(Note: Some of these overlap with Day 7/14 prerequisite entries — only create entries not already logged.)

For Vukani Tech Solutions — Monthly Bookkeeping 2026:
- Week 1: Carol, 3.0hr, "Initial bank statement capture and coding", 2026-01-06
- Week 2: Carol, 2.5hr, "Reconciled Xero cloud accounting transactions", 2026-01-13
- Week 3: Carol, 2.0hr, "Processed month-end payroll entries", 2026-01-20
- Week 4: Bob, 1.0hr, "Final review and management pack preparation", 2026-01-27

For Naledi Hair Studio — Tax Advisory 2026:
- Week 2: Alice, 0.5hr, "Tax planning discussion — provisional tax implications", 2026-01-14
(This should already exist from Day 7.)

**Retainer Periods**:
- Open January 2026 period for Kgosi Construction retainer (R5,500/month)
- Open January 2026 period for Vukani Tech retainer (R8,000/month)

### Actions

#### 30.1 — Bob closes January retainer for Kgosi Construction

1. Sign in as Bob
2. Navigate to: `/org/e2e-test-org/retainers`
3. Click into the Kgosi Construction retainer
4. Open/view the January 2026 period
5. Review the consumption summary:
   - Time logged against this retainer period: Carol ~17hr + Bob ~1.5hr + Alice ~0.5hr = ~19hr
   - Total cost at blended rates: (17 × R450) + (1.5 × R850) + (0.5 × R1,500) = R7,650 + R1,275 + R750 = R9,675 billable
   - Retainer fee: R5,500/month (fixed — the firm absorbs the excess this month)
6. Close the January period
7. Verify a retainer invoice is generated for R5,500 (not R9,675 — retainer caps the billing)

- Expected: January retainer period closes. An invoice is generated for R5,500 (the retainer amount). The overage of R4,175 is absorbed by the firm. This is a critical profitability insight.

#### 30.2 — Bob closes January retainer for Vukani Tech

1. Navigate to the Vukani Tech retainer
2. Review January consumption:
   - Carol ~10.5hr + Bob ~1hr = ~11.5hr
   - Billable: (10.5 × R450) + (1 × R850) = R4,725 + R850 = R5,575
   - Retainer fee: R8,000/month (under-consumed — firm profits on this client)
3. Close January period
4. Verify invoice generated for R8,000

- Expected: January retainer closes. Invoice generated for R8,000. The firm has R2,425 margin on Vukani's retainer this month.

#### 30.3 — Bob creates hourly invoice for Naledi Hair Studio

1. Navigate to: `/org/e2e-test-org/invoices`
2. Click "New Invoice" or look for "Generate from unbilled time"
3. Select Naledi Hair Studio as the client
4. Select the unbilled time entries:
   - Alice, 0.5hr, "Tax planning discussion" at R1,500/hr = R750.00
5. Verify line item calculation:
   - Subtotal: R750.00
   - VAT 15%: R112.50
   - Total: R862.50
6. Note: Naledi is below the VAT threshold — check if VAT should actually be applied. The firm is VAT-registered, so VAT is charged on all invoices. However, Naledi has no VAT number — the invoice should still include VAT (it is the firm's obligation, not the client's).
7. Review invoice details:
   - Client name and details
   - Invoice number (check numbering sequence)
   - Bank details for payment
   - VAT registration number
8. Save the invoice

- Expected: Hourly invoice created for R862.50 (R750 + 15% VAT). Invoice includes all required SA details: client name, VAT amount, invoice number. Bank details from org settings appear on the invoice.

#### 30.4 — Bob reviews all January invoices

1. Navigate to: `/org/e2e-test-org/invoices`
2. Verify 3 invoices are listed for January:
   - Kgosi Construction: R5,500.00 (retainer)
   - Vukani Tech: R8,000.00 (retainer)
   - Naledi Hair Studio: R862.50 (hourly + VAT)
3. Check invoice numbering sequence (e.g., INV-001, INV-002, INV-003)
4. Click into each invoice to verify:
   - Client VAT number displayed (for Kgosi and Vukani)
   - Bank details present
   - Correct line items and amounts

- Expected: Three invoices in the list with correct amounts. Invoice numbering is sequential. Client details (including VAT numbers) appear on Pty Ltd invoices.

#### 30.5 — Bob sends invoices

1. For each invoice, look for a "Send" or "Email Invoice" action
2. Send all 3 invoices to their respective client contacts:
   - Kgosi: thabo@kgosiconstruction.co.za
   - Vukani: sipho@vukanitech.co.za
   - Naledi: naledi@naledihair.co.za
3. Check Mailpit at `http://localhost:8026` for 3 outbound emails
4. Look for portal links in the emails (so clients can view/download invoices)
5. Verify invoice statuses change from DRAFT to SENT

- Expected: Three emails sent with invoice attachments or portal links. Mailpit shows 3 emails. Invoice statuses update to SENT. Portal links allow clients to view invoices.

#### 30.6 — Alice reviews profitability dashboard

1. Sign in as Alice
2. Navigate to: `/org/e2e-test-org/profitability`
3. Review the profitability dashboard:
   - **Revenue**: R5,500 (Kgosi) + R8,000 (Vukani) + R862.50 (Naledi) = R14,362.50
   - **Cost**: Based on cost rates applied to total hours per team member
     - Carol: ~27.5hr × R180/hr = R4,950
     - Bob: ~3.5hr × R350/hr = R1,225
     - Alice: ~1hr × R650/hr = R650
     - Total cost: ~R6,825
   - **Margin**: R14,362.50 - R6,825 = R7,537.50 (~52.5% margin)
4. Look for per-client profitability breakdown:
   - Kgosi: Revenue R5,500, Cost ~R9,000+ → **negative margin** (retainer under-priced or over-serviced)
   - Vukani: Revenue R8,000, Cost ~R5,575 → healthy margin
   - Naledi: Revenue R862.50, Cost ~R325 → healthy margin (advisory rates)
5. Look for team utilisation rates:
   - Carol: ~27.5hr / (4 weeks × 40hr) = ~17.2% utilisation (low — but she has only 4 clients)
   - Bob: ~3.5hr / 160hr = ~2.2%
   - Alice: ~1hr / 160hr = ~0.6%

- Expected: Profitability dashboard shows revenue, cost, and margin per client. The critical insight is that Kgosi Construction's retainer at R5,500/month does not cover the actual work performed (~R9,675 in billable time). The firm is losing money on this client. This is exactly the kind of insight an accounting firm needs.

#### 30.7 — Alice reviews budget tracking

1. Navigate to project budget views for Moroka Trust projects
2. Check budget status:
   - Annual Compliance: R12,000 budget — no time logged yet (on track)
   - Tax Return: R8,500 budget — no time logged yet (on track)
3. Check if budget alert automation would fire at 80% consumption

- Expected: Budget tracking shows 0% consumed for Moroka Trust projects. The budget alert automation is configured to fire at 80% (R9,600 for compliance, R6,800 for tax return).

### Checkpoints

- [ ] Kgosi Construction January retainer closed — invoice generated for R5,500
- [ ] Vukani Tech January retainer closed — invoice generated for R8,000
- [ ] Naledi hourly invoice created: R750 + R112.50 VAT = R862.50
- [ ] Invoice numbering is sequential
- [ ] All 3 invoices show correct client details including VAT numbers
- [ ] All 3 invoices sent via email — Mailpit shows 3 emails
- [ ] Invoice statuses changed from DRAFT to SENT
- [ ] Portal links in emails work (clients can view invoices)
- [ ] Profitability dashboard shows per-client revenue/cost/margin
- [ ] Kgosi Construction flagged as unprofitable (retainer vs. actual cost)
- [ ] Team utilisation rates visible
- [ ] Moroka Trust budget tracking shows 0% consumed

---

## Day 45 — Bulk Billing, Expenses, and New Engagement

**Context**: February has been busy. The firm is settling into a rhythm. Bob handles the bulk billing run for all retainer clients, records a payment for Kgosi's January invoice, and logs an expense for CIPC filing. Alice opens a new ad-hoc engagement for Vukani Tech — they need help with a BEE certificate review. The firm is growing and needs to manage capacity.

**Actor**: Bob (Admin) -> Alice (Owner)

**Prerequisites**: Day 30 complete — 3 invoices sent for January. February time entries created across all clients.

### Prerequisite Data for Day 45

Before executing Day 45 actions, create the following data to simulate February activity:

**Time Entries (February — via project time log)**:

For Kgosi Construction — Monthly Bookkeeping 2026:
- Carol, 4.0hr, "February bank statement capture — Nedbank", 2026-02-03
- Carol, 3.5hr, "Supplier invoice processing and creditor reconciliation", 2026-02-10
- Carol, 4.0hr, "Payroll reconciliation and PAYE returns preparation", 2026-02-17
- Carol, 3.0hr, "Month-end journals — accruals and prepayments", 2026-02-24
- Bob, 1.0hr, "Client meeting — discuss management accounts format", 2026-02-12
- Bob, 0.5hr, "Review Carol's February bookkeeping entries", 2026-02-25
- Alice, 0.5hr, "February management accounts final review", 2026-02-26

For Vukani Tech Solutions — Monthly Bookkeeping 2026:
- Carol, 3.0hr, "February Xero reconciliation — bank and credit card feeds", 2026-02-04
- Carol, 2.5hr, "Inter-company transaction reconciliation", 2026-02-11
- Carol, 2.0hr, "Month-end payroll and leave accrual entries", 2026-02-18
- Bob, 1.0hr, "Final review — February management pack", 2026-02-25

**Retainer Periods**:
- Open February 2026 period for Kgosi Construction retainer
- Open February 2026 period for Vukani Tech retainer

**Payment Recording**:
- Record payment received on Kgosi Construction January invoice (R5,500) — paid 2026-02-10 via EFT

### Actions

#### 45.1 — Bob processes bulk billing for February retainers

1. Sign in as Bob
2. Navigate to: `/org/e2e-test-org/invoices/billing-runs` or look for a bulk billing feature
3. Create a new billing run:
   - Period: February 2026
   - Select all retainer clients: Kgosi Construction, Vukani Tech
4. Review the billing run summary:
   - Kgosi Construction: R5,500 (retainer)
   - Vukani Tech: R8,000 (retainer)
   - Total: R13,500
5. Execute the billing run
6. Verify 2 invoices are generated with correct amounts

- Expected: Bulk billing run creates 2 invoices simultaneously. This is more efficient than closing retainers one by one. Invoice numbering continues from January (INV-004, INV-005).

#### 45.2 — Bob records payment on Kgosi January invoice

1. Navigate to: `/org/e2e-test-org/invoices`
2. Find the Kgosi Construction January invoice (INV-001 or similar)
3. Look for "Record Payment" action
4. Record payment:
   - Amount: R5,500.00
   - Date: 2026-02-10
   - Method: EFT / Bank Transfer
   - Reference: "Kgosi Construction — Jan 2026"
5. Verify invoice status changes from SENT to PAID

- Expected: Payment recorded. Invoice status updates to PAID. The payment shows in the client's payment history.

#### 45.3 — Bob logs expense for Kgosi Construction

1. Navigate to the Kgosi Construction project or look for an expense logging feature
2. Navigate to: `/org/e2e-test-org/projects/{kgosi-project-id}/expenses` (or similar)
3. Log an expense:
   - Description: "CIPC annual return filing fee"
   - Amount: R150.00
   - Date: 2026-02-15
   - Category: Disbursement / Filing Fee
   - Billable: Yes
4. Save the expense

- Expected: Expense logged on the project. The R150 should appear as an unbilled item that can be included in the next invoice. Expense tracking is essential for accounting firms that disburse filing fees on behalf of clients.

#### 45.4 — Bob verifies expense in unbilled summary

1. Check the Kgosi Construction project for unbilled items
2. Verify the R150 CIPC expense appears as unbilled/billable
3. Check if the expense can be included in the next retainer invoice or must be billed separately

- Expected: The expense appears in the unbilled items list. The firm should be able to add it to the March retainer invoice or bill it as a separate line item.

#### 45.5 — Alice creates ad-hoc engagement for Vukani Tech

1. Sign in as Alice
2. Navigate to: `/org/e2e-test-org/projects`
3. Create new project:
   - Name: "Vukani Tech — BEE Certificate Review"
   - Customer: Vukani Tech Solutions (Pty) Ltd
   - Engagement Type: `Advisory`
   - Complexity: `Moderate`
4. Set project budget: R7,500 (5 hours at R1,500/hr)
5. Generate an engagement letter using "Engagement Letter — Advisory" template
6. Send for acceptance

- Expected: New advisory engagement created alongside the existing monthly bookkeeping project. Budget set at R7,500. Engagement letter generated and sent. This demonstrates multiple concurrent engagements per client.

#### 45.6 — Alice reviews resource planning

1. Navigate to: `/org/e2e-test-org/resources` or `/org/e2e-test-org/resources/utilization`
2. Look for team capacity overview:
   - Carol: primary bookkeeper, ~30hr/month across clients
   - Bob: admin + review, ~5hr/month
   - Alice: advisory + final review, ~2hr/month
3. Check if the platform shows available capacity per team member

- Expected: Resource planning page shows team allocation and available capacity. If the resource planning feature is not fully implemented, note as a gap.

### Checkpoints

- [ ] Bulk billing run created for February with 2 retainer invoices
- [ ] Invoice numbering continues sequentially from January
- [ ] Payment recorded on Kgosi January invoice — status changed to PAID
- [ ] Expense logged: CIPC R150 on Kgosi Construction project
- [ ] Expense appears in unbilled items
- [ ] "Vukani Tech — BEE Certificate Review" project created with R7,500 budget
- [ ] Advisory engagement letter generated and sent
- [ ] Resource planning / utilisation view accessible
- [ ] Multiple concurrent engagements per client supported

---

## Day 60 — Second Billing Cycle and Quarterly Review

**Context**: Three months of operations are winding down. March billing needs processing, the BEE advisory for Vukani is nearly complete, and Alice wants a proper quarterly profitability review. This is the moment where the platform must demonstrate it can sustain ongoing operations — not just initial setup, but the repeatable monthly rhythm of a real accounting firm.

**Actor**: Bob (Admin) -> Alice (Owner)

**Prerequisites**: Day 45 complete — February invoices sent, payment recorded on Kgosi January, BEE advisory engagement created.

### Prerequisite Data for Day 60

Before executing Day 60 actions, create the following data:

**Time Entries (March — via project time log)**:

For Kgosi Construction — Monthly Bookkeeping 2026:
- Carol, 4.0hr, "March bank statements — Nedbank and FNB", 2026-03-03
- Carol, 3.5hr, "Supplier invoices and creditor reconciliation — March", 2026-03-10
- Carol, 3.5hr, "Payroll entries — March, including annual bonus accrual", 2026-03-17
- Carol, 3.0hr, "Month-end close — journals, accruals, prepayments", 2026-03-24
- Bob, 1.0hr, "Client meeting — quarterly review discussion", 2026-03-14
- Bob, 0.5hr, "Final review — March management accounts", 2026-03-25
- Alice, 0.5hr, "Quarterly review — management accounts sign-off", 2026-03-26

For Vukani Tech Solutions — Monthly Bookkeeping 2026:
- Carol, 3.0hr, "March Xero reconciliation — bank, credit card, petty cash", 2026-03-04
- Carol, 2.5hr, "Intercompany loan reconciliation and directors' accounts", 2026-03-11
- Carol, 2.0hr, "Month-end payroll and leave", 2026-03-18
- Bob, 1.0hr, "Final review — March management pack", 2026-03-25

For Vukani Tech — BEE Certificate Review (advisory):
- Alice, 2.0hr, "BEE scorecard analysis — ownership and management control elements", 2026-03-05
- Alice, 1.5hr, "Skills development and enterprise development spend review", 2026-03-12
- Alice, 1.0hr, "Draft BEE compliance report and recommendations", 2026-03-19
- Bob, 0.5hr, "Admin — collated BEE supporting documentation", 2026-03-06

**Retainer Periods**:
- Open March 2026 period for Kgosi Construction retainer
- Open March 2026 period for Vukani Tech retainer

**Payment Recordings**:
- Kgosi Construction February invoice paid: R5,500 on 2026-03-08
- Vukani Tech January invoice paid: R8,000 on 2026-03-05

### Actions

#### 60.1 — Bob processes March billing

1. Sign in as Bob
2. Close March retainer periods for Kgosi Construction (R5,500) and Vukani Tech (R8,000)
3. Create hourly invoice for Vukani Tech BEE advisory work:
   - Alice: 4.5hr × R1,500/hr = R6,750
   - Bob: 0.5hr × R850/hr = R425
   - Subtotal: R7,175
   - VAT 15%: R1,076.25
   - Total: R8,251.25
4. Include the R150 CIPC expense on the Kgosi Construction March retainer invoice (or as a separate disbursement invoice)
5. Send all invoices
6. Check Mailpit for outbound emails

- Expected: March retainer invoices generated (R5,500 and R8,000). BEE advisory invoice generated for R8,251.25 (exceeds R7,500 budget by R751.25 — note if budget alert fired at 80%). CIPC expense included. All invoices sent.

#### 60.2 — Alice conducts quarterly profitability review

1. Sign in as Alice
2. Navigate to: `/org/e2e-test-org/profitability`
3. Review Q1 (Jan-Mar) profitability:

   **Per-client P&L**:

   | Client | Revenue (Q1) | Cost (Q1) | Margin |
   |--------|-------------|-----------|--------|
   | Kgosi Construction | R16,650 (3×R5,500 + R150 expense) | ~R26,000 | **-R9,350** (LOSS) |
   | Vukani Tech | R32,251.25 (3×R8,000 + R8,251.25) | ~R15,000 | +R17,251.25 |
   | Naledi Hair Studio | R862.50 | ~R325 | +R537.50 |
   | Moroka Trust | R0 (no billing yet) | R0 | R0 |
   | **Total** | **R49,763.75** | **~R41,325** | **~R8,438.75 (17%)** |

4. Look for utilisation report:
   - Carol: ~80hr in Q1 across clients (out of 480 available) = ~16.7%
   - Bob: ~12hr / 480hr = 2.5%
   - Alice: ~8hr / 480hr = 1.7%

5. Navigate to: `/org/e2e-test-org/reports` or look for a timesheet report
6. Generate Q1 timesheet report
7. Look for export to CSV functionality

- Expected: Profitability dashboard shows Q1 totals. Kgosi Construction is clearly unprofitable — the firm needs to either increase the retainer or reduce service scope. Vukani Tech is the star client. Overall margin is thin. Utilisation is low across the board — the firm needs more clients. The platform should surface these insights clearly.

#### 60.3 — Alice reviews rate cards vs. profitability

1. Navigate to: `/org/e2e-test-org/settings/rates`
2. Compare current rates to profitability data:
   - Carol at R450/hr with R180/hr cost = 60% margin on hourly work
   - But Kgosi retainer at R5,500/month for ~19hr/month of Carol's time = effective rate of R289/hr (below cost!)
3. Consider: Should Carol's rate increase? Should the Kgosi retainer increase?
4. Note these insights for the firm's review

- Expected: The rate card settings page shows current rates. The platform does not directly show "effective hourly rate per retainer client" — this calculation is manual. Note as a potential UX gap if not available.

### Checkpoints

- [ ] March retainer invoices generated for Kgosi (R5,500) and Vukani (R8,000)
- [ ] BEE advisory invoice generated: R7,175 + R1,076.25 VAT = R8,251.25
- [ ] CIPC expense R150 included in billing
- [ ] Budget alert should have fired for BEE project (exceeded R7,500 budget)
- [ ] All March invoices sent — Mailpit shows outbound emails
- [ ] Q1 profitability dashboard shows per-client breakdown
- [ ] Kgosi Construction identified as unprofitable client
- [ ] Team utilisation report accessible
- [ ] Timesheet report generated for Q1
- [ ] CSV export available for timesheet data
- [ ] Rate card review completed (effective retainer rates calculated)

---

## Day 75 — Year-End Engagement Setup

**Context**: Tax season is approaching. Kgosi Construction's financial year ended on 28 February 2026. Alice needs to set up the year-end engagement, send an information request for all the year-end documentation, and generate the annual tax return engagement letter. This is the peak of accounting-specific workflow — information requests, document collection via portal, and engagement letter generation with SA-specific variables (tax year, SARS deadline).

**Actor**: Alice (Owner) -> Carol (Member)

**Prerequisites**: Day 60 complete — Q1 billing done, profitability reviewed. Kgosi Construction has completed FICA, is an ACTIVE client, has 2 paid invoices and 1 outstanding.

### Prerequisite Data for Day 75

Before executing Day 75 actions, create the following data:

**Time Entries (April — some activity continues)**:
- Carol, 4.0hr, "Kgosi April bank statements", 2026-04-01
- Carol, 3.0hr, "Vukani April reconciliation", 2026-04-02
- Bob, 1.0hr, "Kgosi April review", 2026-04-08

**Payment Recordings**:
- Kgosi Construction March invoice: paid R5,500 on 2026-04-05
- Vukani Tech February invoice: paid R8,000 on 2026-04-03

### Actions

#### 75.1 — Alice creates year-end engagement for Kgosi Construction

1. Sign in as Alice
2. Navigate to: `/org/e2e-test-org/projects`
3. Create new project:
   - Name: "Kgosi Construction — Annual Tax Return FY2026"
   - Customer: Kgosi Construction (Pty) Ltd
   - Engagement Type: `Annual Tax Return`
   - Tax Year: `2026`
   - SARS Submission Deadline: `2026-10-31`
   - Assigned Reviewer: `Alice Owner`
   - Complexity: `Complex`
4. Set project budget: R15,000 (based on complexity)

- Expected: Year-end project created with tax-specific custom fields populated. The tax year and SARS deadline are visible on the project detail page. Budget set at R15,000.

#### 75.2 — Send year-end information request via portal

1. From the Kgosi Construction year-end project detail page, look for an information request creation option
2. Create an information request using the "Year-End Information Request (SA)" template:
   - Trial Balance (required)
   - Bank Statements — Full Year (required)
   - Loan Agreements (required)
   - Fixed Asset Register (required)
   - Debtors Age Analysis (optional)
   - Creditors Age Analysis (optional)
   - Insurance Schedule (optional)
   - Payroll Summary (required)
3. Send the information request to Thabo Kgosi (thabo@kgosiconstruction.co.za)
4. Check Mailpit for the outbound email with portal link
5. Navigate to the portal link to verify:
   - All 8 request items are listed
   - File upload is available for each item
   - Required items are marked as required
   - Optional items are clearly optional

- Expected: Information request created from the year-end template with all 8 items. Email sent with portal link. Portal shows the request with file upload capability for each item.

#### 75.3 — Generate year-end engagement letter

1. Generate a document using the "Engagement Letter — Annual Tax Return" template
2. Verify template variables resolve:
   - `customer.name` → "Kgosi Construction (Pty) Ltd"
   - `customer.customFields.sars_tax_reference` → "9301234567"
   - `project.customFields.tax_year` → "2026"
   - `project.customFields.sars_submission_deadline` → "2026-10-31"
   - `org.name` → "Thornton & Associates"
3. Verify required clauses are attached:
   - Limitation of Liability (Accounting)
   - Termination (Accounting)
   - Confidentiality (Accounting)
4. Add optional clauses:
   - Document Retention (Accounting)
   - Third-Party Reliance
5. Preview/generate the PDF
6. Send for acceptance to Thabo

- Expected: Engagement letter generated with all SA-specific variables resolved including tax year and SARS deadline. Clauses included. PDF generated. Sent for client acceptance.

#### 75.4 — Simulate client document upload (portal interaction)

1. Navigate to the portal information request page (via the link from step 75.2)
2. For "Trial Balance" item, look for an upload button
3. Attempt to upload a document (use a test PDF if available)
4. Check if the upload succeeds and appears in the request item

- Expected: Portal allows file upload for information request items. The uploaded document is stored (in S3 via LocalStack) and visible to both the client and the firm.

#### 75.5 — Carol begins year-end preparation

1. Sign in as Carol
2. Navigate to the "Kgosi Construction — Annual Tax Return FY2026" project
3. Log time:
   - Hours: 3.0
   - Description: "Year-end data capture — reconciling trial balance to bank statements"
   - Date: 2026-04-15
4. Add a comment: "Trial balance received via portal — reconciliation in progress. Loan agreements still outstanding."
5. Check for documents uploaded via the portal (from step 75.4)

- Expected: Carol can log time and add comments on the year-end project. Portal-uploaded documents are visible in the project's document section.

### Checkpoints

- [ ] "Kgosi Construction — Annual Tax Return FY2026" project created with tax year 2026 and SARS deadline
- [ ] Project budget set at R15,000
- [ ] Year-end information request created from template with 8 items
- [ ] Information request sent — email in Mailpit with portal link
- [ ] Portal shows all 8 request items with file upload capability
- [ ] Required items distinguished from optional items in portal
- [ ] Engagement letter generated with tax-year-specific variables resolved
- [ ] SARS tax reference and submission deadline appear in the letter
- [ ] Required clauses included, optional clauses selectable
- [ ] Client document upload via portal works
- [ ] Uploaded documents visible to firm from project detail
- [ ] Carol can log time and comment on year-end project

---

## Day 90 — Final Review, Portfolio Assessment, and Fork Readiness

**Context**: Ninety days in. Thornton & Associates has completed a full quarter of operations on the DocTeams platform. Alice conducts a comprehensive review: profitability across all clients, team utilisation over 3 months, portfolio health, and outstanding items. Bob generates a report for Kgosi Construction and tests whether saved views work. This is the capstone test — does the platform genuinely support a real SA accounting practice?

**Actor**: Alice (Owner) -> Bob (Admin)

**Prerequisites**: Day 75 complete — year-end engagement set up for Kgosi Construction, information request sent, portal interaction tested.

### Prerequisite Data for Day 90

Before executing Day 90 actions, ensure the following state exists:

**Additional time entries**:
- Carol, 5.0hr, "Kgosi year-end — data capture continued", 2026-04-20
- Carol, 4.0hr, "Kgosi year-end — tax computation workings", 2026-04-25
- Alice, 1.5hr, "Reviewed Kgosi tax computation draft", 2026-04-28

**Information request status**:
- At least one item on the Kgosi year-end information request should have a document uploaded (from Day 75)

**Payment recordings**:
- Kgosi April retainer: paid R5,500 on 2026-05-03
- Vukani March retainer: paid R8,000 on 2026-04-28
- Naledi January invoice: OVERDUE (not paid — test overdue notification)

### Actions

#### 90.1 — Alice conducts comprehensive profitability review

1. Sign in as Alice
2. Navigate to: `/org/e2e-test-org/profitability`
3. Review 90-day profitability:

   **Firm-wide P&L (Jan-Apr)**:
   - Total revenue: ~R65,000+ (estimate based on 3 months retainers + hourly + advisory)
   - Total cost: ~R50,000 (based on cost rates × hours)
   - Margin: ~R15,000 (~23%)

4. Review per-client profitability:
   - **Kgosi Construction**: Revenue R22,150+ vs. Cost ~R35,000+ → LOSS. The retainer is severely under-priced.
   - **Vukani Tech**: Revenue R32,251+ vs. Cost ~R15,000 → Strong margin. Star client.
   - **Naledi Hair Studio**: Revenue R862.50 vs. Cost ~R325 → Profitable but tiny.
   - **Moroka Trust**: Revenue R0, Cost R0 → No work started. Fee will be invoiced on completion.

5. Review per-engagement-type breakdown (if available):
   - Monthly Bookkeeping: Revenue ~R40,500, Cost ~R30,000 → 26% margin
   - Advisory: Revenue ~R9,114, Cost ~R3,500 → 62% margin
   - Annual Tax Return: Revenue R0 (in progress), Cost ~R5,000

6. Check team utilisation (90-day period):
   - Carol: ~120hr / 720 available hr = ~16.7% utilised
   - Bob: ~18hr / 720hr = 2.5%
   - Alice: ~12hr / 720hr = 1.7%

- Expected: Profitability dashboard shows comprehensive 90-day data. Per-client and per-engagement breakdowns are available. Utilisation rates are visible. The data tells a clear story: the firm needs to reprice Kgosi's retainer, grow the client base, and leverage Carol's available capacity.

#### 90.2 — Alice reviews client portfolio health

1. Navigate to: `/org/e2e-test-org/customers`
2. Review all clients with their status:
   - Kgosi Construction (Pty) Ltd — ACTIVE, 2 paid invoices, 1 outstanding, FICA verified
   - Naledi Hair Studio — PROSPECT/ONBOARDING (FICA incomplete), 1 invoice OVERDUE
   - Vukani Tech Solutions (Pty) Ltd — PROSPECT/ONBOARDING (FICA incomplete), invoices mixed paid/outstanding
   - Moroka Family Trust — PROSPECT (no work started, FICA not started)
3. Check for overdue invoice warnings or dashboard alerts
4. Navigate to: `/org/e2e-test-org/compliance`
5. Look for FICA/compliance overview:
   - Kgosi: Verified
   - Naledi: Not Started (risk — should have been done)
   - Vukani: Not Started (risk)
   - Moroka: Not Started (risk)

- Expected: Client list shows lifecycle statuses and financial summaries. Compliance page highlights FICA gaps. Overdue invoices are flagged. The portfolio view gives Alice a clear picture of practice health.

#### 90.3 — Alice checks dashboard and getting started checklist

1. Navigate to: `/org/e2e-test-org/dashboard`
2. Look for the getting started checklist from Day 0
3. Check if it has been completed/dismissed after 90 days of activity
4. Review any dashboard widgets (recent activity, upcoming deadlines, overdue items)

- Expected: Dashboard shows useful summary data. Getting started checklist may still be visible (if not manually dismissed) or auto-completed based on actions taken.

#### 90.4 — Bob generates monthly report for Kgosi Construction

1. Sign in as Bob
2. Navigate to the "Kgosi Construction — Monthly Bookkeeping 2026" project
3. Generate a document using the "Monthly Report Cover" template
4. Verify template variable resolution:
   - `customer.name` → "Kgosi Construction (Pty) Ltd"
   - `org.name` → "Thornton & Associates"
   - Project details resolve correctly
5. Preview/download the generated document

- Expected: Monthly report cover letter is generated with correct variable resolution. The template produces a professional cover page for the management accounts.

#### 90.5 — Bob tests statement of account generation

1. Navigate to Kgosi Construction customer detail page
2. Generate a document using the "Statement of Account" template
3. Check if the template resolves invoice history:
   - January invoice: R5,500 — PAID
   - February invoice: R5,500 — PAID
   - March invoice: R5,500 — PAID
   - Outstanding balance: R5,500 (April, if issued) + R150 CIPC disbursement

- Expected: The statement of account may NOT resolve correctly — this is a known gap (GAP-004). The `CustomerContextBuilder` does not assemble invoice history into the rendering context. The template is a stub.

#### 90.6 — Bob tests FICA confirmation letter generation

1. Navigate to Kgosi Construction customer detail page
2. Generate a document using the "FICA Confirmation Letter" template
3. Verify template variables:
   - `customer.name` → "Kgosi Construction (Pty) Ltd"
   - `customer.customFields.fica_verification_date` → "2026-01-14"
   - `org.name` → "Thornton & Associates"

- Expected: FICA Confirmation Letter generated with correct verification date. This letter confirms to the client that their FICA verification is on file. Useful for regulatory evidence.

#### 90.7 — Bob attempts to create saved views

1. Navigate to: `/org/e2e-test-org/invoices`
2. Look for filtering and view-saving capability
3. Attempt to create a saved view: "My overdue invoices" (filter: status = OVERDUE)
4. Navigate to: `/org/e2e-test-org/customers`
5. Attempt to create a saved view: "Active retainer clients" (filter: status = ACTIVE, has retainer)
6. Verify views persist and load on page refresh

- Expected: Saved views may or may not be available depending on the platform's implementation. If not available, note as a gap.

#### 90.8 — Final portfolio summary

1. As Alice, take stock of the firm's 90-day position:

   **Clients**: 4 onboarded (1 fully active, 3 in various states)
   **Engagements**: 7 (5 monthly/ongoing, 1 advisory completed, 1 year-end in progress)
   **Invoices sent**: ~10 invoices across 3 months
   **Payments received**: ~5 payments
   **Revenue (90 days)**: ~R65,000
   **Net margin**: ~23%
   **FICA compliance**: 1/4 clients verified (25% — needs attention)
   **Year-end work**: 1 engagement in progress (Kgosi annual tax return)

2. The critical question: **"Overall: Could a real 3-person SA accounting firm run their practice on this platform?"**

- Expected: The platform covers the core workflow: client onboarding, time tracking, billing (retainer + hourly), document generation, and profitability reporting. Significant gaps exist in automation, terminology, and SA-specific workflows (trust accounting, entity-type FICA filtering, statement of accounts). The platform is approximately 70-80% ready for an accounting vertical — good enough for early adopters, not yet for the mainstream market.

### Checkpoints

- [ ] 90-day profitability dashboard shows comprehensive data
- [ ] Per-client P&L breakdown available
- [ ] Kgosi Construction confirmed as unprofitable (retainer under-priced)
- [ ] Team utilisation report shows 3-month trends
- [ ] Client portfolio shows lifecycle statuses and financial summaries
- [ ] Compliance overview shows FICA status across all clients
- [ ] Naledi invoice flagged as OVERDUE
- [ ] Dashboard provides useful summary widgets
- [ ] Getting started checklist completed/dismissed
- [ ] Monthly Report Cover template generates correctly
- [ ] Statement of Account template fails or produces stub (GAP-004 confirmed)
- [ ] FICA Confirmation Letter template generates with correct date
- [ ] Saved views: creation attempted (note availability)
- [ ] Fork readiness question answered in gap report

---

## Script Summary

| Day | Focus | Clients Touched | Key Outputs |
|-----|-------|----------------|-------------|
| 0 | Firm setup | — | Org configured, packs verified |
| 1 | First client | Kgosi Construction | Client, FICA, engagement letter, project, retainer |
| 2 | Two more clients | Naledi, Vukani Tech | Sole proprietor + retainer clients |
| 3 | Complex client | Moroka Trust | Trust entity, fixed-fee engagements |
| 7 | First work | All 4 | Time entries, comments, documents |
| 14 | FICA completion | Kgosi focus | FICA complete, lifecycle transition, automation check |
| 30 | First billing | All 4 | Retainer close, hourly invoice, profitability review |
| 45 | Bulk billing | All 4 | Bulk run, payments, expenses, new engagement |
| 60 | Q1 review | All 4 | Quarterly profitability, rate review, timesheet export |
| 75 | Year-end | Kgosi focus | Tax engagement, info request, portal, engagement letter |
| 90 | Final review | All 4 | Portfolio review, reports, fork readiness |

**Total checkpoints**: 127
**Clients onboarded**: 4 (Pty Ltd, Sole Proprietor, Pty Ltd, Trust)
**Billing models tested**: Retainer, hourly, retainer + overflow, fixed fee
**Templates exercised**: 6 of 7 (bookkeeping letter, tax return letter, advisory letter, monthly report, statement of account, FICA confirmation)
**Portal flows tested**: Information requests, document upload, engagement letter acceptance, invoice viewing

---

*End of lifecycle script. Proceed to gap report for findings.*
