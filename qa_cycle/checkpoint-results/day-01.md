# Day 1 — Custom Field to Document Flow (Cycle 1)

## Phase 49 QA Cycle — 2026-03-17

**Track**: T5 — Custom Field to Document Flow (STOP GATE day)
**Actor**: Alice (Owner)

---

## Section 1.1 — Standard Custom Field Flow (Kgosi)

### 1.1 — Open Kgosi customer detail, verify custom fields section
- **Result**: PASS
- **Evidence**: Navigated to `/org/e2e-test-org/customers/b18bd72f-6e2b-4117-8a37-a331022e5bf6`. "SA Accounting -- Client Details" field group visible with all 16 fields rendered. All values populated from Day 0 seed.

### 1.2 — Verify specific field values match Day 0 seed
- **Result**: PASS
- **Evidence**:
  - Company Registration Number = "2019/123456/07" -- matches
  - VAT Number = "4520012345" -- matches
  - SARS Tax Reference = "9012345678" -- matches
  - Entity Type = "Pty Ltd" (selected in combobox) -- matches
  - Financial Year-End = "2026-02-28" -- matches

### 1.3 — Generate `engagement-letter-bookkeeping` for Kgosi's Monthly Bookkeeping project
- **Result**: PASS
- **Evidence**: Navigated to project `/org/e2e-test-org/projects/dc12a3f6-da31-4c58-9453-7c7e3179a57b`. Clicked "Generate" link for "Engagement Letter -- Monthly Bookkeeping". Step 1 of 2 (clause selection) showed 7 clauses: 4 required (checked, disabled), 3 optional (checked). Clicked "Next: Preview". Step 2 of 2 loaded with HTML preview in iframe.

### 1.4 — Verify `company_registration_number` resolves to "2019/123456/07"
- **Result**: PASS
- **Evidence**: In iframe preview, `Registration Number: 2019/123456/07` rendered correctly after the strong "Registration Number:" label. **GAP-P49-002 VERIFIED** -- the template variable key fix (PR #728, `company_registration_number` -> `acct_company_registration_number`) works correctly.

### 1.5 — Verify `customer.name` resolves to "Kgosi Construction (Pty) Ltd"
- **Result**: PASS
- **Evidence**: Customer name appears in:
  - Salutation: "Dear Kgosi Construction (Pty) Ltd,"
  - Client Details: "Client: Kgosi Construction (Pty) Ltd"
  - Client Responsibilities: "Kgosi Construction (Pty) Ltd undertakes to provide all source documents..."

### 1.6 — Verify `org.name` resolves (not blank, not `{{org.name}}`)
- **Result**: PARTIAL
- **Evidence**: Org name resolves -- not blank, not `{{org.name}}`. However, it resolves to "E2E Test Organization" (the system org name from mock-auth setup), NOT "Thornton & Associates" (the trading name set in org settings). The header shows "E2E Test Organization", the intro says "E2E Test Organization will render services", and the closing says "E2E Test Organization". The footer correctly shows "Thornton & Associates | Reg. 2015/001234/21 | 14 Loop St, Cape Town 8001" (from `documentFooterText`). The `org.name` context variable pulls from the org entity name, not the display/branding name. Not a blocking issue for the data chain test, but worth noting for document quality.
- **Observation**: Engagement Type shows "MONTHLY_BOOKKEEPING" (enum value) instead of display label "Monthly Bookkeeping".

---

**Verdict for 1.4 + 1.5 + 1.6**: Data chain works. 1.4 PASS, 1.5 PASS, 1.6 PARTIAL (resolves but to system name). **STOP GATE PASSES**. Proceed to Day 2.

---

## Section 1.2 — FICA Verification Date Flow

### 1.7 — Check Kgosi's `fica_verification_date` on customer detail
- **Result**: PASS
- **Evidence**: FICA Verification Date field shows "2026-01-15" on customer detail page.

### 1.8 — Generate `fica-confirmation` for Kgosi (customer-scoped)
- **Result**: PASS
- **Evidence**: From customer detail, clicked "Generate" for "FICA Confirmation Letter". Dialog opened directly to preview (no clause selection step -- customer-scoped templates skip clause step). HTML preview rendered in iframe.

### 1.9 — Verify `fica_verification_date` resolves in output
- **Result**: PASS
- **Evidence**: "Verification Date: 15 January 2026" rendered after the strong label. The date is human-formatted (not raw ISO "2026-01-15"). The `VariableFormatter.formatDate()` works correctly.

### 1.10 — Compare value (if blank, log GAP)
- **Result**: PASS
- **Evidence**: Value matches -- "15 January 2026" corresponds to the stored "2026-01-15". No GAP needed.

---

## Section 1.3 — Conditional Fields: Trust Entity Type (Moroka)

### 1.11 — Open Moroka Family Trust customer detail
- **Result**: PASS
- **Evidence**: Navigated to `/org/e2e-test-org/customers/ee5bac6e-1323-4399-9efd-9a7109a6515b`. Heading shows "Moroka Family Trust", status Active, completeness 100%.

### 1.12 — Verify Entity Type = "TRUST" and trust-specific fields
- **Result**: PASS
- **Evidence**:
  - Entity Type = "Trust" (selected in combobox) -- matches
  - Trust Registration Number = "IT789/2018" -- matches
  - Trust Deed Date = "2018-06-15" -- matches
  - Trust Type = "Inter Vivos (Living Trust)" (selected) -- matches
  - Names of Trustees = "Samuel Moroka, Grace Moroka, Adv. T. Nkosi" -- matches
  - Also populated: Trustee Appointment Type = "Appointed", Letters of Authority Date = "2018-07-20"

### 1.13 — Generate `fica-confirmation` for Moroka, verify customer name
- **Result**: PASS
- **Evidence**: Generated FICA Confirmation Letter from Moroka customer detail. Preview shows:
  - "Dear Moroka Family Trust,"
  - "...client identification and verification process...for Moroka Family Trust."
  - "Client: Moroka Family Trust"
  - "Verification Date: 18 January 2026" (matches FICA date 2026-01-18)
  - No cross-contamination with other customers.

### 1.14 — Check if trust-specific fields appear in generated FICA document
- **Result**: PASS (informational)
- **Evidence**: The FICA Confirmation Letter template does NOT include trust-specific fields (no trust registration number, trust deed date, trust type, or trustees names). This is expected -- the FICA template is a generic client verification letter, not trust-specific. Trust fields are available in the context but the template does not reference them.

---

## Section 1.4 — Non-Trust Customer: Conditional Fields Hidden

### 1.15 — Open Naledi Hair Studio customer detail
- **Result**: PASS
- **Evidence**: Navigated to `/org/e2e-test-org/customers/af2a8b9a-6baa-47c9-b785-b1b386b5332c`. Heading shows "Naledi Hair Studio", subtitle "Sole proprietor, hourly billing", completeness 71%.

### 1.16 — Verify Entity Type = "SOLE_PROPRIETOR"
- **Result**: PASS
- **Evidence**: Entity Type combobox shows "Sole Proprietor" selected.

### 1.17 — Verify trust-specific fields are NOT shown
- **Result**: PARTIAL
- **Evidence**: The "SA Accounting -- Trust Details" field group header IS visible on the page (as a collapsed section), but contains no populated trust fields. Trust Registration Number, Trust Deed Date, Trust Type, Names of Trustees are all empty/hidden within the collapsed group. The field group is present because the field pack auto-applies it to all customers (including non-trust). The fields within are not pre-filled. Ideally the trust group header should be hidden for non-trust entity types, but this is a cosmetic issue -- the fields themselves are not visible/populated.

---

## Section 1.5 — Invoice Custom Field Flow

### 1.18 — Generate `invoice-za` (SA Tax Invoice) for a Kgosi invoice
- **Result**: PASS
- **Evidence**: Navigated to Invoices list, clicked Kgosi Draft invoice (R 6,497.50). Clicked "Generate Document" dropdown -> "SA Tax Invoice". HTML preview loaded in dialog.

### 1.19 — Verify `customerVatNumber` = "4520012345"
- **Result**: PASS
- **Evidence**: "Client VAT Number: 4520012345" rendered after strong label. Custom field `vat_number` correctly mapped to `customerVatNumber` via InvoiceContextBuilder alias (GAP-P49-011 confirmed correct).

### 1.20 — Verify `org.taxRegistrationNumber` resolves
- **Result**: PASS
- **Evidence**: "VAT Registration No: 4510067890" rendered in the "From" section. Matches the value set in org settings during Day 0.

**Additional Invoice Observations**:
- "From: E2E Test Organization" (system org name, consistent with engagement letter)
- "To: Kgosi Construction (Pty) Ltd" -- correct
- Currency: ZAR -- correct
- Amounts: R5 650,00 / R847,50 / R6 497,50 -- correct SA format with comma decimal
- Invoice Number: blank (draft invoice, no number assigned) -- expected
- Line item table amounts show raw decimal (5500.00) not ZAR-formatted -- minor formatting gap
- Footer: "Thornton & Associates | Reg. 2015/001234/21 | 14 Loop St, Cape Town 8001" -- correct
- Legal notice: "This is a tax invoice as defined in section 20 of the Value-Added Tax Act 89 of 1991" -- correct

---

## Section 1.6 — Missing Required Field Behaviour

### 1.21 — Generate `engagement-letter-bookkeeping` for Naledi's project
- **Result**: PASS
- **Evidence**: Navigated to project `/org/e2e-test-org/projects/8f959e4a-e702-4c86-85ed-d67949614604` ("Monthly Bookkeeping -- Naledi"). Clicked "Generate" for engagement letter. Clause selection dialog appeared (Step 1 of 2). All 7 clauses shown. Clicked "Next: Preview". HTML preview loaded.

### 1.22 — Pre-generation check: warning about missing `company_registration_number`?
- **Result**: PARTIAL
- **Evidence**: Behavior (b) -- No warning shown. Generation proceeds silently through clause selection to preview. No indication that required template variables are missing. The `TemplateValidationService` pre-generation check did not trigger a visible warning in the UI.

### 1.23 — Check registration number field in Naledi engagement letter output
- **Result**: FAIL
- **Evidence**: Behavior (b) -- "Registration Number:" label is shown with BLANK value after colon. Additionally, ALL customer fields are blank:
  - Salutation: "Dear ," (comma with no name)
  - "Client:" (blank after colon)
  - "Registration Number:" (blank after colon)
  - "Engagement Type:" (blank -- project custom field also unset)
  - Client Responsibilities: "To enable us to fulfil our obligations, undertakes to provide..." (missing customer name mid-sentence)
  - **Root cause**: The Naledi project shows "Internal Project" with no customer linked on the project entity. The ProjectContextBuilder found no customer for this project, so ALL customer-related template variables resolve to empty strings. The customer IS linked from the customer side (Naledi customer detail shows the project in its Projects tab), but the project-to-customer link appears missing or one-directional.

### 1.24 — Check VAT number in Naledi engagement letter
- **Result**: N/A
- **Evidence**: The `engagement-letter-bookkeeping` template does not include a VAT number field. Only Registration Number is in the client details section of this template. VAT number is tested via the invoice template (1.19/1.25).

### 1.25 — Generate `invoice-za` for a Naledi invoice
- **Result**: PARTIAL
- **Evidence**: Navigated to Naledi invoice INV-0001 (Sent, R 1,638.75). Generated "SA Tax Invoice". Preview shows:
  - "To: Naledi Hair Studio" -- customer name resolves correctly (invoice has customer linked directly)
  - "Client VAT Number:" -- blank after label (Naledi has no VAT number). Behavior (b).
  - "Invoice Number: INV-0001" -- resolves correctly
  - "Issue Date: 17 March 2026" -- resolves correctly
  - "Due Date:" -- blank (no due date set on this invoice)
  - The blank VAT field is acceptable for a non-VAT registered sole proprietor, but ideally the label line should be omitted entirely.

### 1.26 — Compare Naledi vs Kgosi documents (same template)
- **Result**: FAIL
- **Evidence**: Engagement letter comparison:
  - **Kgosi**: "Dear Kgosi Construction (Pty) Ltd,", "Client: Kgosi Construction (Pty) Ltd", "Registration Number: 2019/123456/07", "Engagement Type: MONTHLY_BOOKKEEPING" -- all populated
  - **Naledi**: "Dear ,", "Client:", "Registration Number:", "Engagement Type:" -- all blank
  - The Naledi version has dangling labels with no values, an unprofessional salutation ("Dear ,"), and a broken mid-sentence gap in Client Responsibilities. This is not graceful blank handling.
  - Invoice comparison is better: Kgosi invoice has "Client VAT Number: 4520012345", Naledi invoice has "Client VAT Number:" (blank label). Both have correct customer names. The invoice context builder handles the customer link correctly because invoices are directly linked to customers, whereas the Naledi project was not linked (showing "Internal Project").

---

## New GAPs Identified

### GAP-P49-013: Naledi project not linked to customer -- document generation produces broken output
- **Severity**: major
- **Track**: T5.6
- **Description**: The "Monthly Bookkeeping -- Naledi" project shows as "Internal Project" with no customer linked on the project entity. When generating an engagement letter from this project, ALL customer template variables resolve to empty strings, producing: "Dear ,", "Client:", "Registration Number:", and a broken sentence ("...obligations, undertakes to provide..."). The customer IS linked from the customer side (Naledi customer detail shows the project in its Projects tab), but the project-to-customer link is missing. This may be a seed data issue (the project was created without a customer association) or a bidirectional link gap.

### GAP-P49-014: Blank template fields render as label-with-empty-value instead of hiding
- **Severity**: minor
- **Track**: T5.6
- **Description**: When template variables resolve to empty strings, the label line (e.g., "Registration Number:", "Client VAT Number:") is still rendered with a blank value after the colon. This produces unprofessional output (dangling labels). Consistent with GAP-P49-003 (placeholder behavior) but distinct: GAP-P49-003 uses `________` placeholder for missing *custom field* values, while GAP-P49-014 is about missing *entity-level* values (customer name, registration number). Ideal behavior would be to conditionally hide the entire line when the value is empty.

### GAP-P49-015: org.name resolves to system org name, not trading/display name
- **Severity**: cosmetic
- **Track**: T5.1
- **Description**: `org.name` in template context resolves to "E2E Test Organization" (the system org entity name from mock-auth/Keycloak) rather than "Thornton & Associates" (the trading name set in org branding settings). The footer uses `org.documentFooterText` which correctly shows "Thornton & Associates". This means the document header and body show a different firm name than the footer. In production with real Keycloak, the org name would match the firm name. In E2E testing, this creates a cosmetic inconsistency. May not be a real bug in production but should be verified.

---

## Day 1 Summary

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| 1.1 | PASS | Custom fields section visible |
| 1.2 | PASS | All 5 field values match seed data |
| 1.3 | PASS | HTML preview loads in dialog |
| 1.4 | PASS | Registration number "2019/123456/07" resolves. **GAP-P49-002 VERIFIED** |
| 1.5 | PASS | Customer name in 3+ locations |
| 1.6 | PARTIAL | Resolves but to system org name, not trading name |
| 1.7 | PASS | FICA date "2026-01-15" populated |
| 1.8 | PASS | FICA confirmation preview loads |
| 1.9 | PASS | Date formatted as "15 January 2026" |
| 1.10 | PASS | Value matches |
| 1.11 | PASS | Moroka detail loads |
| 1.12 | PASS | All 4 trust field values match |
| 1.13 | PASS | Correct customer name in FICA letter |
| 1.14 | PASS | Trust fields not in FICA template (expected) |
| 1.15 | PASS | Naledi detail loads |
| 1.16 | PASS | Sole Proprietor selected |
| 1.17 | PARTIAL | Trust group header visible but fields empty |
| 1.18 | PASS | Invoice template generates |
| 1.19 | PASS | customerVatNumber = "4520012345" |
| 1.20 | PASS | VAT Reg No = "4510067890" |
| 1.21 | PASS | Generation proceeds |
| 1.22 | PARTIAL | No pre-generation warning |
| 1.23 | FAIL | All customer fields blank (project not linked) |
| 1.24 | N/A | VAT not in engagement letter template |
| 1.25 | PARTIAL | Client VAT Number label shown blank |
| 1.26 | FAIL | Dangling labels, broken salutation |

**Totals**: 26 checkpoints: 18 PASS, 4 PARTIAL, 2 FAIL, 1 N/A, 1 informational PASS
**STOP GATE**: PASSES (1.4 + 1.5 + 1.6 all resolve -- data chain works)
**New GAPs**: 3 (GAP-P49-013 major, GAP-P49-014 minor, GAP-P49-015 cosmetic)
**GAP-P49-002**: VERIFIED (template variable key fix works)
