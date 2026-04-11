# Cycle 5 — Fix Verification + Exploratory Testing

**Date**: 2026-04-07
**Agent**: Infra + QA
**E2E Stack**: Rebuilt with `VERTICAL_PROFILE=legal-za` (all 6 containers healthy)
**Auth**: Alice (Owner) via mock-login
**Console Errors**: 0 across entire session

---

## Phase 1: E2E Stack Rebuild

- Tore down previous stack, rebuilt from branch `bugfix_cycle_2026-04-06`
- First build failed due to transient Maven Central download error (`jsoup:1.18.3` premature EOF)
- Retry succeeded; all 6 containers healthy: backend (8081), frontend (3001), mock-idp (8090), mailpit (8026), postgres (5433), localstack
- Seed completed with `VERTICAL_PROFILE=legal-za`

---

## Phase 2: Fix Verification Results

### VERIFIED FIXED

### Finding: GAP-D0-04 — Sidebar "Projects" renamed to "Matters"
- Page: http://localhost:3001/org/e2e-test-org/dashboard
- Severity: LOW
- Evidence: Sidebar group header shows "Matters" (not "Projects"). Sub-links show "Matters" and "Recurring Schedules".
- Status: **VERIFIED FIXED** (PR #976)

### Finding: GAP-D0-05 — Dashboard cards use legal terms
- Page: http://localhost:3001/org/e2e-test-org/dashboard
- Severity: LOW
- Evidence: KPI cards show "Active Matters" (not "Active Projects") and widget shows "Matter Health" (not "Project Health").
- Status: **VERIFIED FIXED** (PR #976)

### Finding: GAP-D0-06 — Team page Role column populated
- Page: http://localhost:3001/org/e2e-test-org/team
- Severity: LOW
- Evidence: Role column shows "Owner" for Alice. Table has proper "Role" column header.
- Status: **VERIFIED FIXED** (PR #981)

### Finding: GAP-D1-04 — Create Client dialog title uses "Client"
- Page: http://localhost:3001/org/e2e-test-org/customers (New Client dialog)
- Severity: LOW
- Evidence: Dialog title says "Create Client", button says "Create Client" (not "Create Customer").
- Status: **VERIFIED FIXED** (PR #976)

### Finding: GAP-D1-07 — Matter name from template uses user-entered name
- Page: http://localhost:3001/org/e2e-test-org/projects/{id}
- Severity: MEDIUM
- Evidence: Created matter from Litigation template, entered custom name "Ndlovu v RAF". Matter heading shows "Ndlovu v RAF" (not the template placeholder). Template auto-populates "Sipho Ndlovu - Litigation" when customer selected, but user override is preserved.
- Status: **VERIFIED FIXED** (PR #979)

### Finding: GAP-D7-05 — Adverse Parties tab has "Link Adverse Party" button
- Page: http://localhost:3001/org/e2e-test-org/projects/{id}?tab=adverse-parties
- Severity: MEDIUM
- Evidence: "Link Adverse Party" button is present in both empty state and populated states.
- Status: **VERIFIED FIXED** (PR #979)

### Finding: GAP-D30-03 — Invoice terminology uses "Fee Notes"
- Page: http://localhost:3001/org/e2e-test-org/invoices
- Severity: LOW
- Evidence: Sidebar link says "Fee Notes", page heading says "Fee Notes", breadcrumb says "fee notes".
- Status: **VERIFIED FIXED** (PR #976)

### STILL BROKEN

### Finding: GAP-D7-01 — Court Calendar "Schedule Court Date" matter dropdown still empty
- Page: http://localhost:3001/org/e2e-test-org/court-calendar
- Severity: HIGH
- Evidence: Opened "Schedule Court Date" dialog. Matter dropdown shows only "-- Select matter --" with no other options. The "Ndlovu v RAF" matter exists but is not populated in the dropdown.
- Expected: Dropdown should list all active matters.
- Status: **STILL OPEN** (SPEC_READY, fix never implemented)

### Finding: GAP-D30-02 — "Add Line" to invoice still returns parse error
- Page: http://localhost:3001/org/e2e-test-org/invoices/{id}
- Severity: HIGH
- Evidence: Filled in "Add Line Item" form (description: "Legal consultation - 2 hours", qty: 1, unit price: 250000, tax: Standard 15%). Clicked "Add". Error banner appeared: "The request body could not be read or parsed". Line item was NOT added.
- Expected: Line item should be added to the invoice successfully.
- Status: **STILL BROKEN** (fix marked FIXED but not working in this build)

### NOT VERIFIED (cannot test without invoice lines)

- GAP-D30-01 (LSSA Tariff NaN) — Could not verify because creating an invoice with lines failed. The "Add Tariff Items" button is visible on the invoice page, suggesting the dialog exists, but testing requires a working invoice.
- GAP-D7-04 (Activity task names) — No time entries with activity to verify
- GAP-D14-01 (Conflict check matter names) — No adverse parties seeded to test cross-match
- GAP-D45-01 (Court postponement) — No court dates to test
- GAP-D60-01 (Profitability terminology) — No data to generate profitability report
- GAP-D60-02 (Customer column in report) — No data to generate report

---

## Phase 3: Exploratory QA Findings

### Finding: Empty state text uses "customers"/"projects" instead of legal terms
- Page: http://localhost:3001/org/e2e-test-org/customers, http://localhost:3001/org/e2e-test-org/projects
- Severity: LOW
- Evidence: Clients page empty state says "No customers yet" and "Customers represent the organisations you work with." Matters page empty state says "No projects yet" and "Projects organise your work, documents, and time tracking."
- Expected: Should use "clients" and "matters" terminology when legal-za profile is active.

### Finding: Fee Notes page summary shows "$0.00" instead of ZAR
- Page: http://localhost:3001/org/e2e-test-org/invoices
- Severity: LOW
- Evidence: Total Outstanding, Total Overdue, and Paid This Month all show "$0.00" instead of "R 0,00" format. Invoice detail page correctly shows "R 0,00" for totals.
- Expected: Summary amounts should use ZAR/Rand formatting consistent with the rest of the app.

### Finding: "New Invoice" button text on Fee Notes page not using "Fee Note" terminology
- Page: http://localhost:3001/org/e2e-test-org/invoices
- Severity: LOW
- Evidence: Button says "New Invoice" instead of "New Fee Note". Also, "Back to Invoices" link on invoice detail page uses "Invoices" not "Fee Notes".
- Expected: All invoice references should use "Fee Note" terminology when legal-za profile active.

### Finding: "Back to Projects" link on matter detail page uses "Projects" terminology
- Page: http://localhost:3001/org/e2e-test-org/projects/{id}
- Severity: LOW
- Evidence: Back link says "Back to Projects" instead of "Back to Matters".
- Expected: Should use "Back to Matters".

### Finding: Customer field save from UI appears not to persist
- Page: http://localhost:3001/org/e2e-test-org/customers/{id}
- Severity: MEDIUM
- Evidence: Filled in Address Line 1, City, Country, Tax Number fields and clicked "Save Custom Fields". After page reload, fields appeared empty. Custom fields had to be saved via API (`PUT /api/customers/{id}` with `customFields` map) to persist. Possible frontend issue with how the save action sends data.
- Expected: "Save Custom Fields" button should persist values and they should survive page reload.

### Finding: Country dropdown sends label not value
- Page: http://localhost:3001/org/e2e-test-org/customers/{id}
- Severity: MEDIUM
- Evidence: Frontend Country dropdown shows labels like "South Africa" but the backend requires the option value "ZA". When saving via UI, it likely sends "South Africa" which the backend rejects as invalid. This may be the root cause of custom field save not working from UI.
- Expected: Dropdown should send option values (ZA, US, GB, etc.) not display labels.

---

## Summary

| Category | Count |
|----------|-------|
| Fixes VERIFIED | 7 (GAP-D0-04, D0-05, D0-06, D1-04, D1-07, D7-05, D30-03) |
| Fixes STILL BROKEN | 2 (GAP-D7-01 was never fixed, GAP-D30-02 fix not working) |
| Fixes NOT VERIFIED | 6 (insufficient data to test) |
| New exploratory findings | 5 (terminology gaps, currency format, custom field save) |
| Console errors | 0 |
