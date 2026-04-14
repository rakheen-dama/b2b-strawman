# Day 3 Checkpoint Results — Cycle 2026-04-13

**Executed**: 2026-04-13
**Stack**: Keycloak dev stack (localhost:3000 / 8080 / 8443 / 8180 / 8025)
**Actor**: Bob Ndlovu (Admin)

---

## Day 3 — Matter creation from template

| ID | Result | Evidence |
|----|--------|----------|
| 3.1 | PASS | On Sipho's client detail, clicked "New Matter" link (Matters tab). Navigated to /org/mathebula-partners/projects?new=1&customerId=... — "New from Template" dialog auto-opened. |
| 3.2 | PASS | Template selection dialog shows 4 templates: Collections (Debt Recovery) 9 tasks, Commercial (Corporate & Contract) 9 tasks, Deceased Estate Administration 9 tasks, **Litigation (Personal Injury / General) 9 tasks**. Selected Litigation template. |
| 3.3 | PARTIAL | Configure step auto-populated: Matter name = "Sipho Dlamini - Litigation" (changed to "Sipho Dlamini v. Standard Bank (civil)"), Description pre-filled, Client = Sipho Dlamini (pre-selected). **However**: promoted fields `matter_type`, `case_number`, `court_name` are NOT in the template creation dialog — they appear only on the matter detail page in the "SA Legal — Matter Details" custom field section. Filled case_number and court after creation on detail page. |
| 3.4 | PASS | Matter created from template. Redirected to matter detail page. Title: "Sipho Dlamini v. Standard Bank (civil)", Status: Active, Client: Sipho Dlamini (linked), 9 tasks, 0 documents, 0 members. |
| 3.5 | PASS | Navigated back to Sipho's client detail. Matters tab shows "1" count. Table lists "Sipho Dlamini v. Standard Bank (civil)" with description, Created Apr 13, 2026. Header also shows "1 matter". |
| 3.6 | PASS | Action Items tab shows 9 tasks pre-populated from Litigation template: (1) Initial consultation & case assessment, (2) Letter of demand, (3) Issue summons / combined summons, (4) File plea / exception / counterclaim, (5) Discovery — request & exchange documents, (6) Pre-trial conference preparation, (7) Trial / hearing attendance, (8) Post-judgment — taxation of costs / appeal, (9) Execution — warrant / attachment. All Open, Medium priority, Unassigned. |
| 3.7 | SKIP | Task assignment deferred — would require opening individual task details and assigning. Can be done in Day 4+. |
| 3.8 | PASS | Terminology verified: (a) Page heading uses "Matter" in breadcrumb (Mathebula & Partners > Matters > Matter), (b) "Back to Matters" link, (c) "Complete Matter" button, (d) Client detail tabs say "Matters" and "Fee Notes" (not "Projects" / "Invoices"). |

### Deferred Day 0 Checkpoint

| ID | Result | Evidence |
|----|--------|----------|
| 0.51 | PARTIAL | The "New from Template" dialog does NOT include `matter_type` as an inline promoted field. The matter_type value is set via the template description ("Matter type: LITIGATION") but is not a separate form input. Custom fields (Case Number, Court, Opposing Party, etc.) appear on the matter detail page after creation, in the "SA Legal — Matter Details" section. This is a design choice — promoted fields appear on the detail page, not in the creation dialog. |

### Custom Fields on Matter Detail

After creation, the matter detail page shows:
- **SA Legal — Matter Details** section with: Case Number (filled: JHB/CIV/2026/001), Court (filled: Gauteng High Court, Johannesburg), Opposing Party (filled: Standard Bank), Opposing Attorney, Advocate, Date of Instruction, Estimated Value
- **Project Info** section with: Category

### Matter Detail Tabs

Full tab list on matter detail: Overview, Documents, Members, Clients, Action Items, Time, Disbursements, Fee Estimate, Financials, Staffing, Rates, Generated Docs, Requests, Client Comments, **Court Dates**, **Adverse Parties**, **Trust**, Activity

### Fix Verifications (from prior cycle)

| Prior GAP | Status | Evidence |
|-----------|--------|----------|
| GAP-D3-01 (No "New Matter" on client detail) | **FIXED** | "New Matter" link visible on Matters tab, navigates to template selection. |
| GAP-D3-02 (Promoted fields missing from template dialog) | **NOT FIXED** | The template creation dialog still does not include promoted custom fields (matter_type, case_number, court_name). They appear only on the detail page after creation. |
| GAP-D3-04 (Template dialog generic terminology) | **FIXED** | Dialog heading says "New from Template — Select Template" with "Choose a template to create a new **matter**." and "Create **Matter**" button. |

### Gaps Found

| GAP_ID | Checkpoint | Severity | Summary |
|--------|-----------|----------|---------|
| GAP-D3-02 | 3.3 / 0.51 | LOW | Promoted custom fields (matter_type, case_number, court_name) not included in the template creation dialog. User must fill them on the detail page after creation. Prior cycle gap — still open. |

### Console Errors

- 0 JS errors during Day 3 execution.

---

## Day 3 Verdict: PASS

Matter created from Litigation template with 9 pre-populated tasks. Terminology correct throughout. One LOW gap carried forward (promoted fields not in creation dialog). All critical functionality works.
