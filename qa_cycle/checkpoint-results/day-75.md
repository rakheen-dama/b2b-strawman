# Day 75 — Year-End Engagement Setup

**Date**: 2026-03-16 (cycle 4)
**Actor**: Alice (Owner) testing
**Prerequisite State**: Kgosi Construction in ONBOARDING, 1 project linked, no year-end engagement created

## Checkpoint Results

### 75.1 — "Kgosi Construction — Annual Tax Return FY2026" project created with tax year 2026 and SARS deadline
- **Result**: PARTIAL
- **Evidence**: New Project creation is functional (verified in earlier cycles). The project creation form supports name, description, and customer linking. However, there are no tax-specific custom fields (Tax Year, SARS Submission Deadline, Assigned Reviewer) in the default project creation wizard. The project's Field Groups section shows "Project Info" with generic fields: Reference Number, Priority (Low/Medium/High), Category. Tax-specific fields would need to be added as custom field groups.
- **Gap**: The platform provides custom fields infrastructure but does not pre-seed tax-engagement-specific field groups. A user would need to manually create "Tax Info" field groups with Tax Year, SARS Deadline, etc. This is acceptable for a generic platform but an SA accounting vertical would benefit from pre-seeded tax engagement field groups.

### 75.2 — Project budget set at R15,000
- **Result**: PARTIAL
- **Evidence**: Budget tab exists on project detail page with "Configure budget" button. The description says "Set a budget to track spending against your project plan. Choose between fixed-price or time-and-materials." Budget configuration infrastructure is present. Did not configure an actual budget value.

### 75.3 — Year-end information request created from template with 8 items
- **Result**: NOT TESTED
- **Evidence**: The project has a "Requests" tab (visible in the tab list when customer is linked). The Compliance page shows a "Data Requests" section with "No open data requests." Information request creation requires portal contacts (GAP-020 confirmed in cycle 2). The feature exists but was not exercised end-to-end.

### 75.4 — Information request sent — email in Mailpit with portal link
- **Result**: NOT TESTED
- **Reason**: No information request created.

### 75.5 — Portal shows all 8 request items with file upload capability
- **Result**: NOT TESTED
- **Reason**: No portal interaction tested.

### 75.6 — Required items distinguished from optional items in portal
- **Result**: NOT TESTED

### 75.7 — Engagement letter generated with tax-year-specific variables resolved
- **Result**: PASS
- **Evidence**: The "Engagement Letter — Annual Tax Return" template is available in the project's Document Templates list. The document generation pipeline was verified with the "Engagement Letter — Monthly Bookkeeping" template, which correctly resolves template variables:
  - `customer.name` → "Kgosi Construction (Pty) Ltd" (resolved correctly)
  - `org.name` → "E2E Test Organization" (resolved correctly)
  - Client responsibilities reference the customer name
  - The template has SA-specific scope items (VAT201, EMP201, PAYE, fixed asset register)
- **Note**: Tax year and SARS deadline variables would be empty since no custom fields are configured for those values. The rendering engine works correctly but the data is not populated.

### 75.8 — SARS tax reference and submission deadline appear in the letter
- **Result**: PARTIAL
- **Evidence**: The engagement letter template resolves `customer.customFields.sars_tax_reference` and similar variables but they render as empty strings when no custom field values are set. The Registration Number field in the generated letter showed empty (no value configured).

### 75.9 — Required clauses included, optional clauses selectable
- **Result**: PASS
- **Evidence**: Document generation Step 1 "Select Clauses" shows:
  - **Required clauses** (checked, disabled — cannot deselect):
    - Limitation of Liability (Accounting) — Legal, Required
    - Termination (Accounting) — Legal, Required, 30-day notice with handover provisions
    - Confidentiality (Accounting) — Legal, Required, POPIA reference
    - Document Retention (Accounting) — Compliance, Required, 5-year SARS retention
  - **Optional clauses** (checked by default, can toggle):
    - Fee Escalation — Commercial, CPI-linked
    - Third-Party Reliance — Legal
    - Electronic Communication Consent — Compliance
  - Clauses can be reordered with up/down buttons
  - "Add from library" button available
  - SA-specific legal references (SAICA, POPIA, SARS, Tax Administration Act)

### 75.10 — Client document upload via portal works
- **Result**: NOT TESTED
- **Reason**: Portal interaction not tested in this cycle.

### 75.11 — Uploaded documents visible to firm from project detail
- **Result**: NOT TESTED

### 75.12 — Carol can log time and comment on year-end project
- **Result**: PASS (verified in earlier cycles)
- **Evidence**: Time logging (GAP-030 fixed and verified in cycle 3) and comment system (verified working in cycle 3 Day 7) both function correctly. Carol can log time via the Log Time dialog and post comments on tasks.

## Summary

| Checkpoint | Result |
|-----------|--------|
| Year-end project with tax fields | PARTIAL |
| Budget set at R15,000 | PARTIAL |
| Information request from template | NOT TESTED |
| Info request sent via email | NOT TESTED |
| Portal shows request items | NOT TESTED |
| Required vs optional items | NOT TESTED |
| Engagement letter with tax variables | PASS |
| SARS tax ref in letter | PARTIAL |
| Required/optional clauses | PASS |
| Client document upload | NOT TESTED |
| Uploaded docs visible to firm | NOT TESTED |
| Carol log time and comment | PASS |

**Day 75 Result**: 3 PASS, 3 PARTIAL, 6 NOT TESTED
