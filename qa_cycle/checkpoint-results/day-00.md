# Day 0 — Firm Setup
## Executed: 2026-03-15T21:09Z
## Actor: Alice (Owner)

### Checkpoint 0.1 — Authenticate and verify dashboard
- **Result**: PASS
- **Evidence**: Mock-login page loaded at /mock-login with Alice Owner pre-selected. Clicked Sign In, redirected to /org/e2e-test-org/dashboard. Dashboard shows "Getting Started" checklist with 6 items, 3 of 6 complete (50%): Create project, Add customer, Invite team member are done; Log time, Set up rate card, Generate invoice are pending. Alice Owner identity visible in sidebar.
- **Gap**: —

### Checkpoint 0.2 — Verify vertical profile is applied
- **Result**: PASS
- **Evidence**: Navigated to Settings > Custom Fields. Projects tab shows "SA Accounting — Engagement Details" field group (Pack, Active) with 8 fields including Engagement Type, Tax Year, SARS Submission Deadline, Assigned Reviewer, Complexity. Customers tab shows "SA Accounting — Client Details" field group (Pack, Active) with fields including SARS Tax Reference, Financial Year-End, Entity Type, FICA Verified, etc. Also present: "Company FICA Details", "Contact & Address", "FICA Compliance" groups. No explicit "accounting-za" label visible on the Organization settings page (which is "Coming soon"), but the accounting field packs confirm the vertical profile was applied during provisioning.
- **Gap**: —

### Checkpoint 0.3 — Configure org branding
- **Result**: FAIL
- **Evidence**: Settings > Organization is marked "Coming soon" — no way to change org name from "E2E Test Organization" to "Thornton & Associates". Cannot upload logo or set brand colour from this page. A Branding section exists at the bottom of Settings > Templates page with Brand Color and Logo upload, but org name change is not possible anywhere. Brand colour defaulted to #000000.
- **Gap**: GAP-008A (existing — org settings page "Coming Soon")

### Checkpoint 0.4 — Configure billing rates
- **Result**: PASS
- **Evidence**: Navigated to Settings > Rates & Currency. Changed default currency from USD to ZAR ("Default currency updated." confirmation). Set billing rates via Add Rate dialog for each member: Alice Owner ZAR 1,500.00, Bob Admin ZAR 850.00, Carol Member ZAR 450.00. All rates show currency ZAR, effective from Mar 15, 2026, status Ongoing. Switched to Cost Rates tab and set: Alice ZAR 650.00, Bob ZAR 350.00, Carol ZAR 180.00. All cost rates saved successfully.
- **Gap**: —
- **Note**: Default currency in the rate dialog did NOT auto-pick ZAR for the first three billing rates (had to manually select ZAR each time), but DID correctly default to ZAR for the cost rates after the org default was changed. Minor UX friction.

### Checkpoint 0.5 — Configure SA VAT
- **Result**: PARTIAL
- **Evidence**: Settings > Tax shows a pre-configured "Standard" tax rate at 15.00%, marked as Default and Active. Also has "Zero-rated" (0.00%) and "Exempt" (0.00%) rates. The 15% rate is functionally correct for SA VAT, but the tax label says "Tax" rather than "VAT". The rate name is "Standard" rather than "VAT". Tax Registration Number field is empty. This is functionally correct but the naming could be more SA-specific.
- **Gap**: — (cosmetic difference, not a functional gap)

### Checkpoint 0.6 — Verify team members
- **Result**: PARTIAL
- **Evidence**: Team page header shows "3 members" and invite form shows "3 of 10 members", confirming all three team members exist. However, the member list below shows "No members found" with console errors: "Failed to load resource: net::ERR_CONNECTION_REFUSED @ http://localhost:8080/api/members" — the E2E backend runs on port 8081, not 8080. The member list API endpoint URL is misconfigured in the frontend for the E2E stack.
- **Gap**: GAP-025 (NEW) — Team member list API calls port 8080 instead of 8081 in E2E stack; member list shows "No members found" despite 3 members existing.

### Checkpoint 0.7 — Verify accounting field pack
- **Result**: PASS
- **Evidence**: Settings > Custom Fields > Customers tab shows "SA Accounting — Client Details" field group with fields including: Company Registration Number, Trading As, VAT Number, SARS Tax Reference, SARS eFiling Profile Number, Financial Year-End, Entity Type, Industry (SIC Code), Registered Address, Postal Address, Primary Contact Name, Primary Contact Email, Primary Contact Phone, FICA Verified, FICA Verification Date, Referred By. All fields present and Active. Projects tab shows "SA Accounting — Engagement Details" with Engagement Type, Tax Year, SARS Submission Deadline, Assigned Reviewer, Complexity plus Reference Number, Priority, Category. Field counts exceed the expected 16+5 because both common and accounting-specific packs are merged.
- **Gap**: —

### Checkpoint 0.8 — Verify accounting template pack (GAP-008 verification)
- **Result**: PASS
- **Evidence**: Settings > Templates shows 10 templates total. 7 accounting-specific templates confirmed: (1) Monthly Report Cover, (2) Engagement Letter — Monthly Bookkeeping, (3) Engagement Letter — Annual Tax Return, (4) Engagement Letter — Advisory, (5) SA Tax Invoice, (6) Statement of Account, (7) FICA Confirmation Letter. Plus 3 common platform templates: Invoice Cover Letter, Standard Engagement Letter, Project Summary Report. All are Tiptap format, Platform source, Active status.
- **Gap**: — (GAP-008 is VERIFIED)

### Checkpoint 0.9 — Verify FICA/KYC checklist template
- **Result**: FAIL
- **Evidence**: Settings > Checklists shows only 1 template: "Generic Client Onboarding" (4 items, ANY customer type, auto-instantiate: Yes, Platform source). No "FICA/KYC — SA Accounting" checklist template exists. The accounting pack seeder does not include a FICA checklist template with the expected 9 items (Certified ID Copy, Proof of Residence, CM29/CoR14.3, Tax Clearance Certificate, Bank Confirmation Letter, Proof of Business Address, Resolution/Mandate, Beneficial Ownership Declaration, Source of Funds Declaration).
- **Gap**: GAP-026 (NEW) — FICA/KYC checklist template not seeded by accounting-za pack. Only generic onboarding checklist exists.

### Checkpoint 0.10 — Verify clause library
- **Result**: PASS
- **Evidence**: Settings > Clauses shows all 7 accounting-specific clauses: (1) Fee Escalation (Commercial), (2) Document Retention (Accounting) (Compliance), (3) Electronic Communication Consent (Compliance), (4) Limitation of Liability (Accounting) (Legal), (5) Termination (Accounting) (Legal), (6) Confidentiality (Accounting) (Legal), (7) Third-Party Reliance (Legal). All are System source, Active. Additionally, common clauses are present: Payment Terms, Fee Schedule, Engagement Acceptance, Scope of Work, Client Responsibilities, Limitation of Liability, Professional Indemnity, Confidentiality, Termination, Force Majeure, Dispute Resolution, Document Retention.
- **Gap**: —

### Checkpoint 0.11 — Verify and apply automation rule templates
- **Result**: PARTIAL
- **Evidence**: Settings > Automations shows 9 automation rules including the 3 accounting-specific ones: (1) FICA Reminder (7 days) — Customer Status trigger, (2) Engagement Budget Alert (80%) — Budget Threshold trigger, (3) Invoice Overdue (30 days) — Invoice Status trigger. All three are present but their toggle switches appear to be in the OFF state (not checked). They exist but are not enabled by default. 6 additional common automations also present (Request Complete Follow-up, Document Review Notification, New Project Welcome, Budget Alert Escalation, Overdue Invoice Reminder, Task Completion Chain).
- **Gap**: — (rules exist; enabling them is a manual step as expected)

### Checkpoint 0.12 — Verify request template pack
- **Result**: PASS
- **Evidence**: Settings > Request Templates shows "Year-End Information Request (SA)" with 8 items, Platform source, Active status. Description: "Information request template for year-end tax and financial statement preparation — requests trial balance, bank statements, loan agreements, and fixed asset register from the client via portal". Additionally, 4 more accounting templates present: Tax Return Supporting Docs (5 items), Monthly Bookkeeping (4 items), Company Registration (4 items), Annual Audit Document Pack (5 items).
- **Gap**: —

### Checkpoint 0.13 — Check terminology overrides
- **Result**: PASS (known gap confirmed)
- **Evidence**: Sidebar navigation shows generic platform terminology: "Projects" (not "Engagements"), "Customers" under "Clients" group (not standalone "Clients"), "Team" (not specific). This confirms GAP-005 — terminology overrides are not loaded at runtime. The platform functions correctly but does not feel accounting-specific in its labeling.
- **Gap**: GAP-005 (existing, confirmed — WONT_FIX)

---

## Summary

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| 0.1 — Dashboard & auth | PASS | Getting Started checklist visible, 3/6 complete |
| 0.2 — Vertical profile | PASS | Both SA Accounting field groups present and active |
| 0.3 — Org branding | FAIL | GAP-008A: Org settings "Coming soon", cannot rename org |
| 0.4 — Billing rates | PASS | All 6 rates set (3 billing + 3 cost) in ZAR |
| 0.5 — SA VAT | PARTIAL | 15% default rate exists but labeled "Standard"/"Tax" not "VAT" |
| 0.6 — Team members | PARTIAL | 3 members confirmed but list API hits wrong port (8080 vs 8081) |
| 0.7 — Field pack | PASS | All accounting fields present |
| 0.8 — Template pack | PASS | 7 accounting templates + 3 common = 10 total. GAP-008 VERIFIED |
| 0.9 — FICA checklist | FAIL | GAP-026: No FICA/KYC checklist template seeded |
| 0.10 — Clause library | PASS | All 7 accounting clauses present |
| 0.11 — Automations | PARTIAL | 3 rules present but toggles are OFF |
| 0.12 — Request templates | PASS | Year-End Info Request (SA) with 8 items present |
| 0.13 — Terminology | PASS | Confirms GAP-005 (generic labels, known WONT_FIX) |

**New gaps found**: GAP-025 (team list API port), GAP-026 (FICA checklist not seeded)
**Verified fixes**: GAP-008 (accounting templates now visible)
**Blockers for Day 1**: None — Day 0 has no cascading blockers preventing Day 1 execution.
