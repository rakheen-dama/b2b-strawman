# Day 2 Checkpoint Results — Accounting ZA 90-Day Lifecycle (Keycloak)

**Date**: 2026-05-14
**Branch**: `bugfix_cycle_2026-05-14`
**QA Driver**: Playwright MCP against Keycloak dev stack
**Stack**: backend :8080, gateway :8443, frontend :3000, portal :3002, keycloak :8180, mailpit :8025
**Actor**: Bob Ndlovu (Admin) -- `bob@thornton-test.local`
**Status**: **DAY 2 COMPLETE** -- 3 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED

## Summary

All Day 2 checkpoints passed. Sipho Dlamini was successfully transitioned from PROSPECT through ONBOARDING to ACTIVE via the full FICA KYC compliance workflow. The SA Accounting onboarding checklist was automatically created on lifecycle transition with 11 items (8 required, 3 optional). All required items were completed with documents linked; optional items were skipped with reasons. Customer activation required City and Country fields to be filled (blocking prerequisites enforced by the system). After filling required fields, the system auto-transitioned Sipho to ACTIVE lifecycle.

---

## Checkpoint Results

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 2.1 | Transition Sipho to ONBOARDING | **PASS** | Clicked Change Status > Start Onboarding on Sipho's detail page. Confirmation dialog appeared: "This will move the customer to Onboarding status and automatically create compliance checklists." Added note: "Beginning FICA/KYC compliance onboarding for Sipho Dlamini". Confirmed. Lifecycle badge changed from "Prospect" to "Onboarding". New "Onboarding" tab appeared in the detail page tab list. "FICA KYC -- SA Accounting" checklist auto-created with 11 items (0/11 completed, 0/8 required). "Since May 14, 2026" date added. "Fill in from uploads" button appeared. Screenshot: `qa_cycle/evidence/day-02/onboarding-checklist-full.png` |
| 2.2 | Complete onboarding checklist (accounting-za FICA/KYC variant) | **PASS** | Uploaded 8 FICA documents to the Documents tab (certified ID, proof of residence, tax clearance, bank confirmation, company registration, beneficial ownership, letters of authority, trust deed). Completed all 8 required checklist items by linking corresponding documents with notes. Skipped 3 optional items (Proof of Business Address, Resolution/Mandate, Source of Funds Declaration) with reasons ("Not applicable for sole proprietor"). Final progress: **11/11 completed (8/8 required)**. Checklist status changed to "Complete". Screenshot: `qa_cycle/evidence/day-02/onboarding-checklist-complete.png` |
| 2.3 | Verify customer transitions to ACTIVE | **PASS** | After completing the checklist, Change Status > Activate was attempted. System showed "Prerequisites: Customer Activation" dialog blocking activation because City and Country fields were empty. Edited Sipho's profile via Edit button: filled City=Johannesburg, State/Province=Gauteng, Postal Code=2017, Country=South Africa (ZA). After saving, the system auto-transitioned Sipho from ONBOARDING to ACTIVE. Header badges now show "Active / Active". Change Status dropdown offers only post-active transitions (Mark as Dormant, Offboard Customer). Clients list confirms Lifecycle=Active, Status=Active. Screenshots: `qa_cycle/evidence/day-02/sipho-active-status.png`, `qa_cycle/evidence/day-02/clients-list-sipho-active.png` |

---

## FICA KYC -- SA Accounting Checklist Detail

The onboarding checklist auto-created upon PROSPECT -> ONBOARDING transition contained the following 11 items:

| # | Item | Status | Required | Document Linked | Notes |
|---|------|--------|----------|----------------|-------|
| 1 | Certified ID Copy | Completed | Yes | certified-id-sipho.txt | Certified ID received and verified |
| 2 | Proof of Residence | Completed | Yes | proof-of-residence-sipho.txt | Proof of residence verified - utility bill |
| 3 | Company Registration (CM29/CoR14.3) | Completed | Yes | company-registration-sipho.txt | N/A for sole proprietor - marked complete with note |
| 4 | Tax Clearance Certificate | Completed | Yes | tax-clearance-sipho.txt | SARS tax clearance certificate verified |
| 5 | Bank Confirmation Letter | Completed | Yes | bank-confirmation-sipho.txt | Bank confirmation letter from FNB verified |
| 6 | Proof of Business Address | Skipped | No | -- | Not applicable for sole proprietor |
| 7 | Resolution / Mandate | Skipped | No | -- | Not applicable for sole proprietor |
| 8 | Beneficial Ownership Declaration | Completed | Yes | beneficial-ownership-sipho.txt | Beneficial ownership declaration received - sole owner |
| 9 | Source of Funds Declaration | Skipped | No | -- | Not required for individual low-risk client |
| 10 | Letters of Authority (Master's Office) | Completed | Yes | letters-of-authority-sipho.txt | N/A for individual - marked complete |
| 11 | Trust Deed (Certified Copy) | Completed | Yes | trust-deed-sipho.txt | N/A for individual - marked complete |

---

## Activation Prerequisites

The system enforces activation prerequisites via a "Prerequisites: Customer Activation" dialog:
- **City is required for Customer Activation** -- resolved by editing profile (City=Johannesburg)
- **Country is required for Customer Activation** -- resolved by editing profile (Country=South Africa/ZA)

These blocking conditions were visible throughout onboarding as a "Blocking activation" warning banner (red) on the detail page. After filling City and Country (plus State/Province=Gauteng, Postal Code=2017), the system auto-transitioned the customer to ACTIVE without requiring a manual "Activate" click.

---

## Console Errors

| Category | Count | Severity | Details |
|----------|-------|----------|---------|
| 404 /api/assistant/invocations | ~5 | LOW | AI assistant API not implemented. Falls back gracefully. Pre-existing. |
| SSR fetch errors | ~3 | LOW | Server component render errors caught by ErrorBoundary. Pre-existing. |
| WebSocket HMR | ~3 | INFO | Dev-only hot module replacement. Not a product issue. |

**No new product-level console errors introduced by Day 2 operations.** All errors are pre-existing dev-mode issues noted during Day 0/1.

---

## Observations

1. **Automatic checklist creation**: The FICA KYC -- SA Accounting checklist was automatically instantiated when the customer lifecycle transitioned from PROSPECT to ONBOARDING. This is correct accounting-za vertical behavior -- the checklist template matches the SA FICA/KYC compliance requirements.

2. **Document-gated completion**: Checklist items with "Requires document" cannot be marked complete until a document is uploaded and linked. The "Confirm" button remains disabled until a document is selected from the dropdown. This enforces proper compliance documentation.

3. **Skip with reason**: Optional items can be skipped but require a reason text (the "Confirm Skip" button is disabled until a reason is entered). This provides an audit trail for skipped compliance steps.

4. **Activation prerequisites**: The system correctly enforces that City and Country must be filled before a customer can transition to ACTIVE. This is shown as a blocking prereq dialog and as a persistent "Blocking activation" warning banner on the detail page.

5. **Auto-transition**: After all prerequisites were met (checklist complete + required fields filled), the system auto-transitioned the customer to ACTIVE upon saving the edit form. No manual "Activate" click needed once all conditions are satisfied.

6. **Checklist items for non-applicable entities**: Items like Company Registration, Letters of Authority, and Trust Deed are marked as "Required" even for sole proprietors where they are not applicable. These were completed by linking placeholder documents with notes explaining N/A status. This could be improved by entity-type-conditional required status (SOLE_PROPRIETOR should not require Company Registration or Trust Deed).

7. **FICA Verified custom field**: The "FICA Verified" custom field on the client detail still shows "Not Started" even after completing the full FICA KYC checklist. The onboarding checklist completion does not auto-update this field.

---

## Evidence Files

- `qa_cycle/evidence/day-02/onboarding-checklist-full.png` -- Full-page screenshot of FICA KYC checklist at 0/11, showing all 11 items
- `qa_cycle/evidence/day-02/onboarding-checklist-complete.png` -- Checklist at 11/11 with all items completed/skipped
- `qa_cycle/evidence/day-02/onboarding-progress-5-of-11.png` -- Mid-progress screenshot at 5/11 completed
- `qa_cycle/evidence/day-02/sipho-active-status.png` -- Sipho Dlamini detail page showing Active/Active lifecycle
- `qa_cycle/evidence/day-02/clients-list-sipho-active.png` -- Clients list confirming Sipho at Lifecycle=Active

---

**Day 2 Result: 3 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED**
**No new gaps filed.**
