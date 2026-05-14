# Day 4 Checkpoint Results — Accounting ZA 90-Day Lifecycle (Keycloak)

**Date**: 2026-05-14
**Branch**: `bugfix_cycle_2026-05-14`
**QA Driver**: Playwright MCP against Keycloak dev stack
**Stack**: backend :8080, gateway :8443, frontend :3000, portal :3002, keycloak :8180, mailpit :8025
**Actors**: Carol Mokoena (Member), Thandi Thornton (Owner)
**Status**: **DAY 4 COMPLETE** -- 8 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED

## Summary

All Day 4 checkpoints passed. Two scenarios were executed:

1. **Carol logs 1.0 hours** on Sipho Dlamini's tax return engagement ("Document collection -- client portal") against the "Collect IRP5/IT3(a) certificates" task. Time entry recorded at Carol's billing rate of R 450,00/hr.

2. **Thandi creates Kgosi Holdings (Pty) Ltd** as a new company client with all accounting-za promoted fields inline (entity type, registration number, VAT number, financial year end, registered address, primary contact details). Screenshot captured as the "Company client wow moment" (Day 4 demo wow). Onboarding completed via FICA KYC checklist (8/8 required items completed with documents linked, 3 optional items skipped). System auto-transitioned Kgosi to ACTIVE after checklist completion (City and Country were already populated during creation).

---

## Checkpoint Results

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 4.1 | Carol logs 1.0 hours: "Document collection -- client portal" | **PASS** | Logged in as Carol Mokoena (carol@thornton-test.local / SecureP@ss3). Navigated to Sipho Dlamini engagement (583ee45e-40b5-4846-9082-92f69f0f5f17). Tasks tab: 7 tasks visible, Carol assigned to 4. Clicked "Log Time" on "Collect IRP5/IT3(a) certificates" task. Dialog: Duration=1h 0m, Date=2026-05-14, Description="Document collection -- client portal", Billable=checked, Rate=R 450,00/hr. Total: 1h x R 450,00 = R 450,00. Submitted. Time tab confirms: Total=1h, Billable=1h, Entries=1, Task="Collect IRP5/IT3(a) certificates". Screenshot: `qa_cycle/evidence/day-04/carol-time-entry-1h.png` |
| 4.2 | Navigate to Clients > New Client | **PASS** | Signed out Carol, logged in as Thandi Thornton (thandi@thornton-test.local / SecureP@ss1). Navigated to /org/thornton-associates/customers. Clients list shows 1 client (Sipho Dlamini, Active). Clicked "New Client" -- "Create Client" dialog opened (Step 1 of 2). |
| 4.3 | Fill standard: Name = Kgosi Holdings (Pty) Ltd, Email, Phone | **PASS** | Filled Name="Kgosi Holdings (Pty) Ltd", Type=Company, Email="finance@kgosi-holdings.co.za", Phone="+27-11-555-0301". |
| 4.4 | Fill promoted fields (all inline) | **PASS** | All promoted fields rendered inline in Step 1 dialog (NOT in a sidebar). Filled: Entity Type="Pty Ltd (Private Company)", Registration Number="2018/123456/07", Tax Number (VAT)="4123456789", Financial Year End="2026-02-28", Address="Suite 402, Kgosi Towers, 15 Biermann Ave, Rosebank, 2196", City="Johannesburg", Postal Code="2196", Country="South Africa (ZA)", Contact Name="Lerato Khumalo", Contact Email="lerato@kgosi-holdings.co.za", Contact Phone="+27-82-555-0302". Step 2: SA Accounting custom fields -- SARS Tax Reference="4123456789", FICA Verified="Not Started". |
| 4.5 | Screenshot: New Client dialog with all promoted fields | **PASS** | Screenshot captured before saving: `qa_cycle/evidence/day-04/new-client-kgosi-promoted-fields.png` (dialog screenshot), `qa_cycle/evidence/day-04/new-client-kgosi-fullpage.png` (full page). All accounting-za promoted fields visible inline in the dialog: Entity Type dropdown, Registration Number, Tax Number/VAT, Financial Year End, Address fields, Contact fields, Business Details. |
| 4.6 | Save > client appears | **PASS** | Clicked Next > Create Client. Redirected to Kgosi detail page at /customers/90d93d67-b462-4fe9-9732-656af5ab889e. Navigated to clients list: 2 clients shown -- Sipho Dlamini (Active) and Kgosi Holdings (Pty) Ltd (initially Prospect). |
| 4.7 | Open detail > verify all promoted fields render inline | **PASS** | Detail page renders all promoted fields inline (NOT in sidebar): (1) Address: Suite 402, Kgosi Towers, 15 Biermann Ave, Rosebank, 2196 / Johannesburg, 2196 / ZA. (2) Primary Contact: Lerato Khumalo, lerato@kgosi-holdings.co.za, +27-82-555-0302. (3) Business Details: Registration Number=2018/123456/07, Tax Number=4123456789, Entity Type=Pty Ltd (Private Company), Financial Year End=Feb 28, 2026. (4) SA Accounting -- Client Details: SARS Tax Reference=4123456789, FICA Verified=Not Started. Screenshot: `qa_cycle/evidence/day-04/kgosi-detail-promoted-fields.png` |
| 4.8 | Complete onboarding checklist > ACTIVE | **PASS** | Transitioned Kgosi to ONBOARDING via Change Status > Start Onboarding. FICA KYC -- SA Accounting checklist auto-created with 11 items (8 required). Uploaded 8 FICA documents to Documents tab (certified ID, proof of residence, company registration, tax clearance, bank confirmation, beneficial ownership, letters of authority, trust deed). Completed all 8 required checklist items by linking corresponding documents. Skipped 3 optional items (Proof of Business Address, Resolution/Mandate, Source of Funds Declaration). Final progress: 8/11 completed (8/8 required). System auto-transitioned Kgosi from ONBOARDING to ACTIVE (City and Country were already populated). Header badges: "Active / Active". Screenshots: `qa_cycle/evidence/day-04/kgosi-active-status.png`, `qa_cycle/evidence/day-04/clients-list-two-active.png` |

---

## Client Detail: Kgosi Holdings (Pty) Ltd

| Field | Value |
|-------|-------|
| Client ID | 90d93d67-b462-4fe9-9732-656af5ab889e |
| Name | Kgosi Holdings (Pty) Ltd |
| Type | Company |
| Email | finance@kgosi-holdings.co.za |
| Phone | +27-11-555-0301 |
| Lifecycle | Active |
| Status | Active |
| Entity Type | Pty Ltd (Private Company) |
| Registration Number | 2018/123456/07 |
| Tax Number (VAT) | 4123456789 |
| Financial Year End | Feb 28, 2026 |
| Address | Suite 402, Kgosi Towers, 15 Biermann Ave, Rosebank, 2196 |
| City | Johannesburg |
| Postal Code | 2196 |
| Country | ZA |
| Primary Contact | Lerato Khumalo |
| Contact Email | lerato@kgosi-holdings.co.za |
| Contact Phone | +27-82-555-0302 |
| SARS Tax Reference | 4123456789 |
| FICA Verified | Not Started (completed in checklist, field not auto-updated) |

## Time Entry: Carol on Sipho Engagement

| Field | Value |
|-------|-------|
| Engagement | Sipho Dlamini -- 2025/26 Tax Return (583ee45e-40b5-4846-9082-92f69f0f5f17) |
| Task | Collect IRP5/IT3(a) certificates |
| Duration | 1.0 hours |
| Description | Document collection -- client portal |
| Billable | Yes |
| Rate | R 450,00/hr (Carol's member default) |
| Amount | R 450,00 |
| Date | 2026-05-14 |
| Logged by | Carol Mokoena |

## FICA KYC Checklist: Kgosi Holdings

| # | Item | Status | Required | Document Linked |
|---|------|--------|----------|----------------|
| 1 | Certified ID Copy | Completed | Yes | certified-id-kgosi.txt |
| 2 | Proof of Residence | Completed | Yes | proof-of-residence-kgosi.txt |
| 3 | Company Registration (CM29/CoR14.3) | Completed | Yes | company-registration-kgosi.txt |
| 4 | Tax Clearance Certificate | Completed | Yes | tax-clearance-kgosi.txt |
| 5 | Bank Confirmation Letter | Completed | Yes | bank-confirmation-kgosi.txt |
| 6 | Proof of Business Address | Skipped | No | -- |
| 7 | Resolution / Mandate | Skipped | No | -- |
| 8 | Beneficial Ownership Declaration | Completed | Yes | beneficial-ownership-kgosi.txt |
| 9 | Source of Funds Declaration | Skipped | No | -- |
| 10 | Letters of Authority (Master's Office) | Completed | Yes | letters-of-authority-kgosi.txt |
| 11 | Trust Deed (Certified Copy) | Completed | Yes | trust-deed-kgosi.txt |

---

## Console Errors

| Category | Count | Severity | Details |
|----------|-------|----------|---------|
| 404 /api/assistant/invocations | ~7 | LOW | AI assistant API not implemented. Falls back gracefully. Pre-existing. |
| WebSocket HMR | ~1 | INFO | Dev-only hot module replacement. Not a product issue. |

**No new product-level console errors introduced by Day 4 operations.** All errors are pre-existing dev-mode issues noted during Day 0/1/2/3.

---

## Observations

1. **Time logging from task list**: The "Log Time" button is available directly from the task table row in the engagement Tasks tab. The dialog pre-fills the date, shows the member's billing rate (R 450,00/hr for Carol), and calculates the total (1h x R 450,00 = R 450,00). Billable is checked by default.

2. **Company client promoted fields**: All accounting-za promoted fields for a company client render inline in the Step 1 create dialog: Name, Type (Individual/Company/Trust), Email, Phone, Tax Number, Address (multi-field section), Contact (name/email/phone), Business Details (Registration Number, Entity Type dropdown, Financial Year End). Step 2 shows SA Accounting custom fields (SARS Tax Reference, FICA Verified). No fields were hidden in a sidebar or "Other Fields" section.

3. **Auto-activation after checklist**: When City and Country are already populated during creation, completing the FICA/KYC checklist auto-transitions the client from ONBOARDING to ACTIVE without requiring a manual "Activate" click. This is the same behavior observed in Day 2 with Sipho (except Sipho needed City/Country added post-creation because they were empty).

4. **Checklist document linking**: Each checklist item with a document requirement shows "Select a document..." dropdown when "Mark Complete" is clicked. Documents must be uploaded to the Documents tab first, then linked from the checklist. After selection, clicking "Confirm" marks the item as completed with the linked document.

5. **SA Accounting -- Trust Details auto-assigned**: The Trust Details field group was auto-assigned to Kgosi Holdings even though it's a Pty Ltd company (not a trust). This is because the accounting-za vertical profile assigns both client detail groups by default. Non-blocking observation -- the fields are collapsed and don't interfere with the company workflow.

---

## Evidence Files

- `qa_cycle/evidence/day-04/carol-time-entry-1h.png` -- Time tab showing Carol's 1h entry on Sipho engagement
- `qa_cycle/evidence/day-04/new-client-kgosi-promoted-fields.png` -- New Client dialog with all promoted fields filled (wow moment screenshot)
- `qa_cycle/evidence/day-04/new-client-kgosi-fullpage.png` -- Full page screenshot of New Client dialog
- `qa_cycle/evidence/day-04/kgosi-detail-promoted-fields.png` -- Kgosi detail page with all promoted fields inline
- `qa_cycle/evidence/day-04/kgosi-active-status.png` -- Kgosi detail page showing Active/Active status after onboarding
- `qa_cycle/evidence/day-04/clients-list-two-active.png` -- Clients list showing 2 active clients (Sipho + Kgosi)

---

**Day 4 Result: 8 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED**
**No new gaps filed.**
