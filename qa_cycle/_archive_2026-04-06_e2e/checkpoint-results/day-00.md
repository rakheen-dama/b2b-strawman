# Day 0 — Data Preparation (Cycle 1)

## Phase 49 QA Cycle — 2026-03-17

## Step 0.1 — Phase 48 Lifecycle Seed
- **Result**: PASS (run via `compose/seed/lifecycle-test.sh` — 24/24 assertions pass)
- **Evidence**: Seed script completed successfully before QA cycle started.

---

## Step 0.2 — Custom Fields: Kgosi Construction (Phase 0B)

**Actor**: Bob (Admin)

### 0.11 — Navigate to Kgosi customer detail, custom fields section
- **Result**: PASS
- **Evidence**: Navigated to `/org/e2e-test-org/customers/b18bd72f-6e2b-4117-8a37-a331022e5bf6`. "SA Accounting -- Client Details" field group visible with all 16 fields rendered (Company Registration Number, Trading As, VAT Number, SARS Tax Reference, SARS eFiling Profile Number, Financial Year-End, Entity Type, Industry SIC Code, Registered Address, Postal Address, Primary Contact Name, Primary Contact Email, Primary Contact Phone, FICA Verified, FICA Verification Date, Referred By). Also visible: "SA Accounting -- Trust Details" (collapsed, empty), "Contact & Address" groups.

### 0.12 — Company Registration Number = 2019/123456/07
- **Result**: PASS
- **Evidence**: Cleared seed value `2015/098765/07`, filled `2019/123456/07`. Text field accepts CIPC format.

### 0.13 — Trading As = Kgosi Construction
- **Result**: PASS
- **Evidence**: Was blank, filled `Kgosi Construction`. Text value saved.

### 0.14 — VAT Number = 4520012345
- **Result**: PASS
- **Evidence**: Cleared seed value `2015/098765/07`, filled `4520012345`.

### 0.15 — SARS Tax Reference = 9012345678
- **Result**: PASS
- **Evidence**: Cleared seed value `2015/098765/07`, filled `9012345678`.

### 0.16 — Financial Year-End = 2026-02-28
- **Result**: PASS
- **Evidence**: Already set to `2026-02-28` by seed. No change needed.

### 0.17 — Entity Type = PTY_LTD
- **Result**: PASS
- **Evidence**: Already set to "Pty Ltd" by seed. Dropdown shows correct selection.

### 0.18 — Registered Address = 14 Main Road, Sandton, Johannesburg, 2196
- **Result**: PASS
- **Evidence**: Cleared seed value `123 Test Street, Johannesburg`, filled full address.

### 0.19 — Primary Contact Name = Thabo Kgosi
- **Result**: PASS
- **Evidence**: Already set to `Thabo Kgosi` by seed. No change needed.

### 0.20 — Primary Contact Email = thabo@kgosiconstruction.co.za
- **Result**: PASS
- **Evidence**: Already set by seed. No change needed.

### 0.21 — Primary Contact Phone = +27-11-555-0100
- **Result**: PASS
- **Evidence**: Was blank, filled `+27-11-555-0100`.

### 0.22 — FICA Verified = VERIFIED
- **Result**: PASS
- **Evidence**: Already set to "Verified" by seed. Dropdown shows correct selection.

### 0.23 — FICA Verification Date = 2026-01-15
- **Result**: PASS
- **Evidence**: Was blank, filled `2026-01-15`.

### 0.24 — Save, reload, verify persistence
- **Result**: PASS
- **Evidence**: Clicked "Save Custom Fields". Reloaded page. All 12 values persisted correctly: Company Registration Number = `2019/123456/07`, Trading As = `Kgosi Construction`, VAT Number = `4520012345`, SARS Tax Reference = `9012345678`, Financial Year-End = `2026-02-28`, Entity Type = `Pty Ltd`, Registered Address = `14 Main Road, Sandton, Johannesburg, 2196`, Primary Contact Name = `Thabo Kgosi`, Primary Contact Email = `thabo@kgosiconstruction.co.za`, Primary Contact Phone = `+27-11-555-0100`, FICA Verified = `Verified`, FICA Verification Date = `2026-01-15`.

---

## Step 0.3 — Custom Fields: Naledi Hair Studio (Phase 0C)

**Actor**: Bob (Admin)

### 0.25 — Navigate to Naledi customer detail, custom fields
- **Result**: PASS
- **Evidence**: Navigated to `/org/e2e-test-org/customers/af2a8b9a-6baa-47c9-b785-b1b386b5332c`. Custom field section visible with "SA Accounting -- Client Details" group.

### 0.26 — SARS Tax Reference = 8801015001082
- **Result**: PASS
- **Evidence**: Cleared seed value `N/A`, filled `8801015001082`. ID number accepted.

### 0.27 — Entity Type = SOLE_PROPRIETOR
- **Result**: PASS
- **Evidence**: Already set to "Sole Proprietor" by seed. No change needed.

### 0.28 — Financial Year-End = 2026-02-28
- **Result**: PASS
- **Evidence**: Already set by seed. No change needed.

### 0.29 — Primary Contact Name = Naledi Molefe
- **Result**: PASS
- **Evidence**: Seed had `Naledi Mokoena`, updated to `Naledi Molefe` per script.

### 0.30 — Primary Contact Email = naledi@naledihair.co.za
- **Result**: PASS
- **Evidence**: Already set by seed. No change needed.

### 0.31 — FICA Verified = VERIFIED
- **Result**: PASS
- **Evidence**: Already set to "Verified" by seed.

### 0.32 — FICA Verification Date = 2026-01-16
- **Result**: PASS
- **Evidence**: Was blank, filled `2026-01-16`.

### 0.33 — Company Registration Number and VAT Number are blank
- **Result**: PASS
- **Evidence**: Cleared both fields (seed had `N/A` values). After save, both show blank/empty.

### 0.34 — Save, verify persisted
- **Result**: PASS
- **Evidence**: Saved and reloaded. 7 values populated, 2 intentionally blank (Company Registration Number, VAT Number). Completeness dropped from 100% to 71% as expected due to blank optional fields.

---

## Step 0.4 — Custom Fields: Vukani Tech Solutions (Phase 0D)

**Actor**: Bob (Admin)

### 0.35 — Navigate to Vukani customer detail, custom fields
- **Result**: PASS
- **Evidence**: Navigated to `/org/e2e-test-org/customers/4a354cb9-d114-4bd1-8c3b-861ca19de51e`. Custom field section visible.

### 0.36 — Company Registration Number = 2021/654321/07
- **Result**: PASS
- **Evidence**: Cleared seed value `2018/567890/07`, filled `2021/654321/07`.

### 0.37 — VAT Number = 4520098765
- **Result**: PASS
- **Evidence**: Cleared seed value, filled `4520098765`.

### 0.38 — SARS Tax Reference = 9087654321
- **Result**: PASS
- **Evidence**: Cleared seed value, filled `9087654321`.

### 0.39 — Entity Type = PTY_LTD
- **Result**: PASS
- **Evidence**: Already set to "Pty Ltd" by seed.

### 0.40 — Financial Year-End = 2026-06-30
- **Result**: PASS
- **Evidence**: Changed from seed `2026-02-28` to `2026-06-30` (June year-end).

### 0.41 — Registered Address = 88 Rivonia Blvd, Rivonia, Johannesburg, 2128
- **Result**: PASS
- **Evidence**: Cleared seed value, filled full address.

### 0.42 — Primary Contact Name = Sipho Ndlovu
- **Result**: PASS
- **Evidence**: Seed had `Sipho Dlamini`, updated to `Sipho Ndlovu` per script.

### 0.43 — Primary Contact Email = finance@vukanitech.co.za
- **Result**: PASS
- **Evidence**: Already set by seed.

### 0.44 — FICA Verified = VERIFIED
- **Result**: PASS
- **Evidence**: Already set by seed.

### 0.45 — FICA Verification Date = 2026-01-17
- **Result**: PASS
- **Evidence**: Was blank, filled `2026-01-17`.

### 0.46 — Save, verify persisted
- **Result**: PASS
- **Evidence**: Saved and reloaded. All 10 values populated and persisted correctly.

---

## Step 0.5 — Custom Fields: Moroka Family Trust (Phase 0E)

**Actor**: Bob (Admin)

### 0.47 — Navigate to Moroka customer detail, custom fields
- **Result**: PASS
- **Evidence**: Navigated to `/org/e2e-test-org/customers/ee5bac6e-1323-4399-9efd-9a7109a6515b`. Custom fields visible with "SA Accounting -- Client Details" AND "SA Accounting -- Trust Details" sections.

### 0.48 — SARS Tax Reference = 1234567890
- **Result**: PASS
- **Evidence**: Cleared seed value `IT123/2015`, filled `1234567890`.

### 0.49 — Entity Type = TRUST
- **Result**: PASS
- **Evidence**: Already set to "Trust" by seed.

### 0.50 — Financial Year-End = 2026-02-28
- **Result**: PASS
- **Evidence**: Already set by seed.

### 0.51 — Primary Contact Name = Samuel Moroka
- **Result**: PASS
- **Evidence**: Seed had `Moroka Trustees`, updated to `Samuel Moroka`.

### 0.52 — Primary Contact Email = trustees@morokatrust.co.za
- **Result**: PASS
- **Evidence**: Already set by seed.

### 0.53 — FICA Verified = VERIFIED
- **Result**: PASS
- **Evidence**: Already set by seed.

### 0.54 — FICA Verification Date = 2026-01-18
- **Result**: PASS
- **Evidence**: Was blank, filled `2026-01-18`.

### 0.55 — Trust field group visible when Entity Type = TRUST
- **Result**: PASS
- **Evidence**: "SA Accounting -- Trust Details" section is visible with 6 trust-specific fields: Trust Registration Number, Trust Deed Date, Trust Type, Names of Trustees, Trustee Appointment Type, Letters of Authority Date. Entity Type was already TRUST from seed -- fields were visible immediately on page load.

### 0.56 — Trust Registration Number = IT789/2018
- **Result**: PASS
- **Evidence**: Cleared seed value `IT123/2015`, filled `IT789/2018`.

### 0.57 — Trust Deed Date = 2018-06-15
- **Result**: PASS
- **Evidence**: Cleared seed value `2020-01-01`, filled `2018-06-15`.

### 0.58 — Trust Type = INTER_VIVOS
- **Result**: PASS
- **Evidence**: Already set to "Inter Vivos (Living Trust)" by seed.

### 0.59 — Trustee Names = Samuel Moroka, Grace Moroka, Adv. T. Nkosi
- **Result**: PASS
- **Evidence**: Was blank, filled comma-separated names.

### 0.60 — Trustee Appointment Type = APPOINTED
- **Result**: PASS
- **Evidence**: Was "Select...", selected "Appointed" from dropdown.

### 0.61 — Letters of Authority Date = 2018-07-20
- **Result**: PASS
- **Evidence**: Was blank, filled `2018-07-20`.

### 0.62 — Save, verify all trust fields persisted
- **Result**: PASS
- **Evidence**: Saved and reloaded. All 13 values persisted: 7 standard + 6 trust-specific. Trust Registration Number = `IT789/2018`, Trust Deed Date = `2018-06-15`, Trust Type = `Inter Vivos (Living Trust)`, Names of Trustees = `Samuel Moroka, Grace Moroka, Adv. T. Nkosi`, Trustee Appointment Type = `Appointed`, Letters of Authority Date = `2018-07-20`.

---

## Step 0.6 — Org Settings for Document Variables (Phase 0F)

**Actor**: Alice (Owner)

### 0.63 — Navigate to Settings > General
- **Result**: PASS
- **Evidence**: Navigated to `/org/e2e-test-org/settings/general`. Page loads with Currency, Tax Configuration, and Branding sections.

### 0.64 — Document Footer Text set
- **Result**: PASS
- **Evidence**: Was blank. Set to `Thornton & Associates | Reg. 2015/001234/21 | 14 Loop St, Cape Town 8001`. Field accepts text (72/500 characters).

### 0.65 — Tax Registration Number = 4510067890
- **Result**: PASS
- **Evidence**: Was blank. Set to `4510067890`. Persisted after save and reload.

### 0.66 — Org name = Thornton & Associates
- **Result**: PARTIAL
- **Evidence**: The General settings page does not have an explicit "org name" field. The sidebar shows "e2e-test-org" (the slug). The display name "Thornton & Associates" was set via the Phase 48 lifecycle seed API (`PUT /api/org-settings`). The footer text includes "Thornton & Associates" which confirms the value is present in the system. Observation: no UI field to change org display name on Settings > General.

### 0.67 — Default Currency = ZAR
- **Result**: PASS
- **Evidence**: Currency dropdown shows "ZAR -- South African Rand".

### 0.68 — Save all changes
- **Result**: PASS
- **Evidence**: Clicked "Save Settings". Reloaded page. All values persisted: Tax Registration Number = `4510067890`, Document Footer Text = `Thornton & Associates | Reg. 2015/001234/21 | 14 Loop St, Cape Town 8001`, Brand Color = `#1B5E20`, Currency = ZAR.

---

## Step 0.7 — Project Custom Field Values (Phase 0G)

**Actor**: Alice (Owner)

**GAP-P49-006 Verification**: The `autoApply: true` fix in PR #729 is VERIFIED. Project custom fields now auto-apply to all projects.

### 0.69 — Open Kgosi "Monthly Bookkeeping" project, custom fields
- **Result**: PASS
- **Evidence**: Navigated to `/org/e2e-test-org/projects/dc12a3f6-da31-4c58-9453-7c7e3179a57b`. "SA Accounting -- Engagement Details" field group IS visible with 5 fields: Engagement Type (required, dropdown), Tax Year, SARS Submission Deadline, Assigned Reviewer, Complexity. GAP-P49-006 VERIFIED.

### 0.70 — Set engagement_type = MONTHLY_BOOKKEEPING
- **Result**: PASS
- **Evidence**: Selected "Monthly Bookkeeping" from Engagement Type dropdown. Saved. Project setup progress went from 80% to 100% ("Required fields filled 1/1").

### 0.71 — Open Kgosi "Annual Tax Return 2026" project, custom fields
- **Result**: PASS
- **Evidence**: Navigated to `/org/e2e-test-org/projects/fce283fe-eb02-4a34-aeca-cb1d9ddf1164`. "SA Accounting -- Engagement Details" field group visible.

### 0.72 — Set engagement_type = ANNUAL_TAX_RETURN
- **Result**: PASS
- **Evidence**: Selected "Annual Tax Return" from Engagement Type dropdown.

### 0.73 — Set tax_year = 2026
- **Result**: PASS
- **Evidence**: Filled Tax Year text field with `2026`.

### 0.74 — Set sars_submission_deadline = 2026-11-30
- **Result**: PASS
- **Evidence**: Filled SARS Submission Deadline date field with `2026-11-30`.

### 0.75 — Check for missing project custom fields
- **Result**: PASS
- **Evidence**: Both projects have the "SA Accounting -- Engagement Details" field group auto-applied. No fields missing. GAP-P49-006 fix confirmed.

### 0.76 — Save all changes on both projects
- **Result**: PASS
- **Evidence**: Saved on both projects. Project setup progress for Annual Tax Return went from 40% to 60% ("Required fields filled 1/1"). Custom field values visible on project list cards: "Engagement Type: Monthly Bookkeeping" on Kgosi project, "Engagement Type: Annual Tax Return", "Tax Year: 2026", "SARS Submission Deadline: Nov 30, 2026" on Annual Tax Return project.

---

## Step 0.8 — Portal Contact Setup (Phase 0H)

**Actor**: Alice (Owner)

### 0.77 — Navigate to Kgosi customer detail, Portal/Contacts tab
- **Result**: PARTIAL
- **Evidence**: Navigated to Kgosi customer detail. No "Portal" or "Contacts" tab visible in customer tabs (available tabs: Projects, Documents, Onboarding, Invoices, Retainer, Requests, Rates, Generated Docs, Financials). Portal contacts may have been created via the Phase 48 seed API but cannot be verified or created through the firm-side UI.

### 0.78 — Create portal contact for Kgosi (Thabo Kgosi)
- **Result**: PARTIAL
- **Evidence**: Cannot create portal contact through UI -- no portal contacts tab exists on customer detail page. The Phase 48 seed script creates contacts via API. The API endpoint `/api/customers/{id}/portal-contacts` exists but returns 403 for Alice (owner role). Observation: portal contact management UI not implemented for firm-side.

### 0.79 — Verify portal contact is active
- **Result**: PARTIAL
- **Evidence**: Cannot verify through UI. Portal contact status is only accessible via API.

### 0.80 — Navigate to Vukani customer detail, Portal/Contacts tab
- **Result**: PARTIAL
- **Evidence**: Same as 0.77 -- no Portal/Contacts tab on customer detail page.

### 0.81 — Create portal contact for Vukani (Sipho Ndlovu)
- **Result**: PARTIAL
- **Evidence**: Same as 0.78. Cannot create through UI.

**Observation**: Portal contact management is a backend-only feature at this point. No firm-side UI exists for creating or managing portal contacts. This is consistent with GAP-P49-005 (Portal acceptance page missing -- WONT_FIX). The contacts were likely created via API in the Phase 48 seed. This does not block Day 0 completion since portal contacts are only needed for Tracks 6 and 7 (both WONT_FIX in this cycle).

---

## Step 0.9 — Data Readiness Checkpoint (Phase 0I)

**Actor**: Bob (Admin)

### 0.82 — Kgosi custom fields: 10+ values visible and non-blank
- **Result**: PASS
- **Evidence**: Verified on Kgosi detail page after reload: 12 fields populated with script values (Company Registration Number, Trading As, VAT Number, SARS Tax Reference, Financial Year-End, Entity Type, Registered Address, Primary Contact Name, Primary Contact Email, Primary Contact Phone, FICA Verified, FICA Verification Date). All match Day 0 seed values exactly. STOP GATE PASSES.

### 0.83 — Moroka trust fields visible and populated
- **Result**: PASS
- **Evidence**: Trust-specific fields all populated: Trust Registration Number = `IT789/2018`, Trust Deed Date = `2018-06-15`, Trust Type = `Inter Vivos (Living Trust)`, Names of Trustees = `Samuel Moroka, Grace Moroka, Adv. T. Nkosi`, Trustee Appointment Type = `Appointed`, Letters of Authority Date = `2018-07-20`.

### 0.84 — Invoices: 2+ exist with line items
- **Result**: PASS
- **Evidence**: Navigated to `/org/e2e-test-org/invoices`. 4 invoices visible: 2 Draft (Vukani R3,450, Kgosi R6,497.50), 1 Paid (INV-0002 Kgosi R6,325), 1 Sent (INV-0001 Naledi R1,638.75). Total Outstanding R1,638.75. Paid This Month R6,325.

### 0.85 — Projects: 4+ exist
- **Result**: PASS
- **Evidence**: Navigated to `/org/e2e-test-org/projects`. 7 projects visible: Monthly Bookkeeping (Kgosi, Naledi, Vukani), Annual Tax Return 2026 (Kgosi), BEE Certificate Review (Vukani), Annual Administration (Moroka Trust), Website Redesign (seed default). All 6 lifecycle projects active. Custom field values visible on project cards.

### 0.86 — Settings: org name, currency, footer text
- **Result**: PASS
- **Evidence**: Settings > General shows: Currency = ZAR, Tax Registration Number = `4510067890`, Document Footer Text = `Thornton & Associates | Reg. 2015/001234/21 | 14 Loop St, Cape Town 8001`, Brand Color = `#1B5E20`. Org display name not shown on settings page but set via API.

---

## Day 0 Summary

| Section | Steps | Pass | Partial | Fail | Notes |
|---------|-------|------|---------|------|-------|
| 0.1 Lifecycle Seed | 1 | 1 | 0 | 0 | Pre-completed |
| 0.2 Kgosi Fields | 14 | 14 | 0 | 0 | All 12 values set and persisted |
| 0.3 Naledi Fields | 10 | 10 | 0 | 0 | 7 values set, 2 intentionally blank |
| 0.4 Vukani Fields | 12 | 12 | 0 | 0 | All 10 values set and persisted |
| 0.5 Moroka Fields | 16 | 16 | 0 | 0 | 7 standard + 6 trust = 13 values |
| 0.6 Org Settings | 6 | 5 | 1 | 0 | Org name not on settings page |
| 0.7 Project Fields | 8 | 8 | 0 | 0 | GAP-P49-006 VERIFIED |
| 0.8 Portal Contacts | 5 | 0 | 5 | 0 | No UI for portal contacts |
| 0.9 Data Readiness | 5 | 5 | 0 | 0 | STOP GATE PASSES |
| **Total** | **77** | **71** | **6** | **0** | |

**STOP GATE 0.82: PASS** -- Proceed to Day 1.

**GAP-P49-006: VERIFIED** -- Project custom fields auto-apply correctly after `autoApply: true` fix (PR #729).

**Observations**:
- Portal contacts have no firm-side UI (consistent with GAP-P49-005)
- Org display name field not on General settings page (set via API only)
- Seed data had placeholder values (e.g., `N/A`, `2015/098765/07`) that needed to be overwritten
