# Day 1 — First Client Onboarding: Kgosi Construction

## Cycle 1 — Executed: 2026-03-15T21:20Z
## Actor: Bob (Admin), then attempted Alice (Owner)

### Checkpoint 1.1 — Create Kgosi Construction as a new customer
- **Result**: PASS
- **Evidence**: Customer "Kgosi Construction (Pty) Ltd" created successfully via New Customer dialog (Step 1: Name, Type=Company, Email, Phone; Step 2: skipped field intake — only "Contact & Address" shown, no FICA fields per GAP-008B). Customer appears in list with status PROSPECT. ID: `5756c017-0998-4d88-877f-da8314431a3c`.
- **Gap**: GAP-008B (confirmed — FICA field groups not auto-attached during customer creation wizard Step 2)

### Checkpoint 1.1a — Populate all 16 accounting custom fields
- **Result**: PASS
- **Evidence**: Navigated to customer detail page. Added "SA Accounting — Client Details" field group via "Add Group" button (searchable dropdown found the group immediately). All 16 fields populated: Company Registration Number (2019/347821/07), Trading As (Kgosi Construction), VAT Number (4830291746), SARS Tax Reference (9301234567), SARS eFiling Profile (blank), Financial Year-End (2025-02-28), Entity Type (Pty Ltd), Industry SIC Code (41000), Registered Address, Postal Address, Primary Contact Name (Thabo Kgosi), Primary Contact Email, Primary Contact Phone, FICA Verified (Not Started), FICA Verification Date (blank), Referred By (Existing client referral). Saved successfully. Customer Readiness updated to "Required fields filled (7/7)".
- **Gap**: —

### Checkpoint 1.2 — Initiate FICA checklist for Kgosi Construction
- **Result**: PARTIAL (cycle 1) -> PASS (cycle 2)
- **Evidence (cycle 1)**: No "Checklists" tab available on PROSPECT customers. Transitioned customer to ONBOARDING via Change Status > Start Onboarding. An "Onboarding" tab appeared with a "Generic Client Onboarding" checklist (4 items), NOT the expected "FICA/KYC — SA Accounting" checklist with 9 items. The "Manually Add Checklist" dialog only shows the generic template — no FICA template available.
- **Evidence (cycle 2)**: After GAP-026 fix, "Manually Add Checklist" dialog now shows all 4 templates: FICA KYC — SA Accounting (9 items), Generic Client Onboarding (4 items), Company Client Onboarding FICA (6 items), Individual Client Onboarding FICA (5 items). FICA checklist instantiated successfully with 9 items: Certified ID Copy (required), Proof of Residence (required), Company Registration CM29/CoR14.3 (required), Tax Clearance Certificate (required), Bank Confirmation Letter (required), Proof of Business Address (optional), Resolution/Mandate (optional), Beneficial Ownership Declaration (required), Source of Funds Declaration (optional). Items show "Pending" status with "Mark Complete" buttons. Required items have no Skip option; optional items have both Mark Complete and Skip.
- **Gap**: — (GAP-026 VERIFIED)

### Checkpoint 1.3 — Verify customer lifecycle state
- **Result**: PASS
- **Evidence**: Customer transitioned from PROSPECT to ONBOARDING successfully. Customer detail page shows "Onboarding" badge. The lifecycle progression path is visible via the Change Status dropdown and the "Customer Readiness" section. After GAP-027 fix (cycle 2), the page renders correctly after transition — no crash.
- **Gap**: — (GAP-027 VERIFIED)

### Checkpoint 1.4 — Send information request via portal
- **Result**: FAIL (BLOCKER, cycle 1) -> PARTIAL (cycle 2)
- **Evidence (cycle 1)**: Cannot access the Requests tab on the customer detail page. After the lifecycle transition to ONBOARDING, the customer detail page crashed with `TypeError: Cannot read properties of null` during server-side rendering.
- **Evidence (cycle 2)**: After GAP-027 fix, Requests tab accessible on customer detail page. Shows "No information requests yet" with "New Request" button. Clicked "New Request" — dialog opens with template selector (Ad-hoc), reminder interval config (5 days default), and customer name. However, dialog shows "No portal contacts found for this customer. Please add a portal contact first." Both "Save as Draft" and "Send Now" are disabled. Cannot send information request without a portal contact configured for this customer.
- **Gap**: GAP-020 (confirmed — portal contacts not set up for new customers; information requests require portal contact configuration first)

### Checkpoint 1.5 — Alice creates engagement letter
- **Result**: FAIL (BLOCKED, cycle 1) -> PARTIAL (cycle 2)
- **Evidence (cycle 2)**: Customer detail page renders correctly (GAP-027 fixed). "Generate Document" button on customer page shows dropdown with 2 customer-scoped templates: "Statement of Account" and "FICA Confirmation Letter". No engagement letter templates available from customer context — engagement letters are project-scoped templates. To generate an engagement letter, a project must first be created and linked. This is by design (engagement letter templates use project context variables), but the Day 1 script expected engagement letter generation from the customer page.
- **Gap**: GAP-013 (confirmed — no engagement letter lifecycle tracking from customer page; engagement letters require project context)

### Checkpoint 1.6 — Send engagement letter for acceptance
- **Result**: FAIL (cycle 2)
- **Evidence**: Cannot generate engagement letter from customer context (project-scoped template). No "Send for Acceptance" flow available without first creating a project. Requires checkpoint 1.8 (create project) first.
- **Gap**: GAP-013

### Checkpoint 1.7 — Accept engagement letter via portal
- **Result**: FAIL (cycle 2)
- **Evidence**: Blocked by 1.6 — no engagement letter generated to accept.
- **Gap**: GAP-013

### Checkpoint 1.8 — Create engagement (project) from accepted engagement letter
- **Result**: PARTIAL (cycle 2)
- **Evidence**: No engagement letter acceptance flow available, but project creation is functional. Projects page loads correctly (GAP-008C verified). "New Project" button available. Project can be created independently and linked to customer. However, the "from accepted engagement letter" flow does not exist — projects are created standalone and linked to customers manually. This is a feature gap, not a bug.
- **Gap**: GAP-013 (engagement letter -> project creation flow does not exist)

### Checkpoint 1.9 — Set up retainer for Kgosi Construction
- **Result**: NOT TESTED (cycle 2)
- **Evidence**: Retainers page accessible via Clients > Retainers sidebar navigation. Not tested in detail as project was not linked to customer in this cycle. Retainer creation requires a project linked to a customer.
- **Gap**: —

---

## Cycle 2 — Fix Verifications (2026-03-16T00:00Z)
### Actor: Alice (Owner)

### Verification: GAP-027 (blocker -> FIXED -> VERIFIED)
- **Action**: Created new customer "Kgosi Construction (Pty) Ltd" (Company type). Navigated to detail page. Clicked Change Status > Start Onboarding > confirmed. Page did NOT crash.
- **Post-transition state**: Customer detail page renders correctly with "Onboarding" status badge. "Onboarding" tab appeared with auto-instantiated "Generic Client Onboarding" checklist (4 items). Customer Readiness shows "Onboarding checklist (0/4)". All other tabs (Projects, Documents, Invoices, Retainer, Requests, Rates, Generated Docs, Financials) accessible.
- **Customer list page**: Loads correctly after transition, showing Kgosi Construction with "Onboarding" lifecycle status.
- **Reload test**: Full page reload of customer detail renders correctly after hydration (SSR skeleton -> hydrated content in ~2s).
- **Console errors**: Only pre-existing React #418 hydration mismatch (cosmetic, non-blocking). No TypeError crashes.
- **Verdict**: VERIFIED

### Verification: GAP-026 (major -> FIXED -> VERIFIED)
- **Action**: Navigated to Settings > Checklists. Page shows 4 checklist templates.
- **Evidence**: "FICA KYC — SA Accounting" template visible with 9 items, customer type ALL, source Platform, auto-instantiate No. Also present: Generic Client Onboarding (4 items, auto-instantiate Yes), Company Client Onboarding FICA (6 items), Individual Client Onboarding FICA (5 items).
- **Instantiation test**: From Kgosi Construction Onboarding tab, clicked "Manually Add Checklist". Dialog shows all 4 templates. Selected "FICA KYC — SA Accounting" > Create Checklist. FICA checklist instantiated with all 9 items visible on the Onboarding tab. Items show correct required/optional status and document requirements.
- **Verdict**: VERIFIED

### Verification: GAP-025 (bug -> FIXED -> VERIFIED)
- **Action**: Navigated to Team page (/org/e2e-test-org/team).
- **Evidence**: Page shows "3 members" count. Table lists all 3 members: Alice Owner (alice@e2e-test.local), Bob Admin (bob@e2e-test.local), Carol Member (carol@e2e-test.local). Invite form shows "3 of 10 members". Pending Invitations tab available.
- **Console errors**: Only pre-existing React #418 hydration mismatch.
- **Verdict**: VERIFIED

### Verification: GAP-008C (bug -> FIXED -> VERIFIED)
- **Action**: Navigated to Projects page (/org/e2e-test-org/projects).
- **Evidence**: Page renders correctly showing "1 project" with "Website Redesign" listed. Project card shows status "Lead", description "E2E seed project for testing", date "Mar 15, 2026". New Project button available. Status filter tabs (Active, Completed, Archived, All) functional.
- **Console errors**: React #418 hydration mismatch (pre-existing, cosmetic). No blocking TypeError on project.status or createdAt.
- **Verdict**: VERIFIED

---

## Summary (Cycle 2)

| Checkpoint | Cycle 1 | Cycle 2 | Gap |
|-----------|---------|---------|-----|
| 1.1 — Create customer | PASS | (not re-tested) | GAP-008B |
| 1.1a — Populate custom fields | PASS | (not re-tested) | — |
| 1.2 — Initiate FICA checklist | PARTIAL | PASS | GAP-026 VERIFIED |
| 1.3 — Verify lifecycle state | PASS | PASS | GAP-027 VERIFIED |
| 1.4 — Information request | FAIL | PARTIAL | GAP-020 (portal contacts) |
| 1.5 — Engagement letter | FAIL | PARTIAL | GAP-013 (project-scoped) |
| 1.6 — Send engagement letter | FAIL | FAIL | GAP-013 |
| 1.7 — Accept engagement letter | FAIL | FAIL | GAP-013 |
| 1.8 — Create project | FAIL | PARTIAL | GAP-013 |
| 1.9 — Set up retainer | FAIL | NOT TESTED | — |

**Fix Verifications**: 4/4 VERIFIED (GAP-027, GAP-026, GAP-025, GAP-008C)

**Cycle 2 Totals**: 2 PASS, 3 PARTIAL, 3 FAIL, 1 NOT TESTED

**Assessment**: All 4 cycle 1 fixes verified. The blocker (GAP-027) is resolved. Day 1 remaining failures are due to missing portal contact setup (GAP-020, known minor) and engagement letter lifecycle features (GAP-013, known WONT_FIX). QA can proceed to Day 2.
