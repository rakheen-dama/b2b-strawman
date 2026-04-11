# Cycle 2 -- Day 1 Checkpoint Results

**Date**: 2026-04-06
**Actors**: Alice (Owner), Bob (Admin) attempted
**Stack**: E2E mock-auth (localhost:3001 / localhost:8081)
**Profile**: legal-za (pre-provisioned)

## Critical Finding: Bob (Admin) has broken navigation and page crashes

When logged in as Bob (Admin), the sidebar is severely degraded:
- Missing: Clients section, Finance section, Court Calendar, Conflict Check, Adverse Parties, Resources, Recurring Schedules
- Shows: only Dashboard, My Work, Calendar, Projects (not "Matters"), Team
- Conflict Check page crashes with "Something went wrong" error
- 404 resource error in console

This forced all Day 1 testing to be performed as Alice (Owner) instead of Bob (Admin) as the test plan specifies.

## Results

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 1.1 | Login as Bob, navigate to Conflict Check | FAIL | Bob's sidebar is degraded (no Clients/Finance/legal modules). Conflict Check page crashes: "Something went wrong". Console: 404 error. **NEW GAP-D1-02.** Tested as Alice instead. |
| 1.2 | Search "Sipho Ndlovu" conflict check | PASS | Result: "No Conflict" (green). Checked at 06/04/2026 20:08:49. History tab shows (1). Tested as Alice. |
| 1.3 | Screenshot conflict check | SKIP | Non-blocking screenshot step. |
| 1.4 | Navigate to Clients page | PASS | `/org/e2e-test-org/customers` loads. Shows 0 clients, "New Client" button visible. |
| 1.5 | Click "New Client" | PASS | "Create Customer" dialog opens (terminology gap: says "Customer" not "Client" in dialog title). |
| 1.6 | Fill client details | PASS | Name: Sipho Ndlovu, Email: sipho.ndlovu@email.co.za, Phone: +27-82-555-0101. |
| 1.7 | Fill custom fields | PARTIAL | Client Type = INDIVIDUAL set in Step 2 of creation wizard. ID/passport and address not filled during creation (would need to be done on detail page). |
| 1.8 | Save, verify in list with PROSPECT status | PASS | Client appears in list: Name=Sipho Ndlovu, Client Type=Individual, Lifecycle=Prospect, Email/Phone correct. |
| 1.9 | Click into detail, verify PROSPECT badge | PASS | Detail page shows "Prospect" lifecycle badge and "Active" status badge. |
| 1.10 | Transition to ONBOARDING | PASS | Change Status > Start Onboarding. Confirmation dialog shown. Badge updated to "Onboarding". Notification generated (1 unread). |
| 1.11 | Verify onboarding checklist auto-instantiated | PARTIAL | "Generic Client Onboarding" checklist created with 4 items. NOTE: Test plan expects FICA-specific items ("Certified ID Copy", "Proof of Address") but system creates generic checklist. Items: (1) Confirm client engagement, (2) Verify contact details, (3) Confirm billing arrangements, (4) Upload signed engagement letter. |
| 1.12 | Mark checklist items complete | PARTIAL | Completed 3/4 items. Item 4 "Upload signed engagement letter" requires a document attachment (combobox "Select a document...") and cannot be completed without one. |
| 1.13 | KYC verification button | SKIP | No KYC verification button visible (expected in E2E). |
| 1.14 | Complete all remaining checklist items | FAIL | **BLOCKER**: Item 4 "Upload signed engagement letter" has `requiresDocument` constraint. No documents uploaded to client yet, so combobox shows "Select a document..." with no options. Confirm without document is silently rejected. **NEW GAP-D1-03.** |
| 1.15 | Verify auto-transition to ACTIVE | FAIL | Client stuck at ONBOARDING (3/4 checklist). Manual "Activate" also blocked: "All onboarding checklists must be completed before activation." Cannot proceed to ACTIVE. |
| 1.16-1.21 | Create matter from Litigation template | NOT_TESTED | Blocked: CustomerLifecycleGuard blocks CREATE_PROJECT for non-ACTIVE customers. Client is stuck at ONBOARDING. |
| 1.22 | Screenshot matter detail | NOT_TESTED | Blocked by above. |
| 1.23-1.28 | Engagement letter flow | NOT_TESTED | Blocked by client not being ACTIVE. |

## Day 1 Checkpoint Summary

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| Conflict check run (clear result) | PASS | Works for Alice. Crashes for Bob (GAP-D1-02). |
| Client created, PROSPECT -> ONBOARDING -> ACTIVE | PARTIAL | PROSPECT -> ONBOARDING works. ONBOARDING -> ACTIVE blocked by document-required checklist item (GAP-D1-03). |
| FICA checklist completed | FAIL | Generic checklist, not FICA-specific. 3/4 items completed; document-required item blocks completion. |
| Matter created from Litigation template with 9 action items | NOT_TESTED | Blocked by client lifecycle. |
| Engagement letter sent, email in Mailpit | NOT_TESTED | Blocked by client lifecycle. |
| Terminology | PARTIAL | "Clients" in breadcrumb/sidebar, "Matters" link correct. BUT: create dialog says "Create Customer", "Activate Customer". |

## New Gaps Found

| ID | Summary | Severity | Category | Notes |
|----|---------|----------|----------|-------|
| GAP-D1-02 | Bob (Admin) has degraded sidebar and pages crash. Missing: Clients, Finance, Court Calendar, Conflict Check, Adverse Parties, Resources, Recurring Schedules. Conflict Check page shows "Something went wrong" with 404 error. | HIGH | Role/Permission | Only Owner role has full legal-za navigation. Admin/Member roles appear to lose legal module nav items. May be related to mock-auth role mapping or frontend permission gating. |
| GAP-D1-03 | Onboarding checklist item "Upload signed engagement letter" has `requiresDocument` constraint that cannot be satisfied -- no documents exist on newly created client, and Confirm without document selection is silently rejected. This blocks completing the checklist and prevents ONBOARDING -> ACTIVE transition. | HIGH | Lifecycle Blocker | Blocks entire Day 1 lifecycle flow from step 1.14 onward. Manual "Activate" also fails with same constraint. Workaround: either (a) remove document requirement from this checklist item, (b) allow completing without document, or (c) upload a document first. |
| GAP-D1-04 | Create Customer dialog title says "Create Customer" not "Create Client" when legal-za profile is active. "Activate Customer" dialog also uses generic terminology. | LOW | Terminology | Dialog titles not using legal terminology overrides. |
| GAP-D1-05 | Onboarding checklist is "Generic Client Onboarding" instead of FICA-specific checklist. Items are generic (confirm engagement, verify contact, billing, engagement letter) rather than FICA/KYC items (Certified ID, Proof of Address, etc.). | MEDIUM | Legal Vertical | legal-za profile should ideally seed a FICA-compliant onboarding checklist template. |

## Conclusion

Day 1 is **BLOCKED** at step 1.14. The onboarding checklist document requirement prevents completing the ONBOARDING -> ACTIVE transition, which cascades to block matter creation (steps 1.16-1.21) and engagement letter flow (steps 1.23-1.28).

Two high-severity gaps found:
1. **GAP-D1-02** (Bob broken navigation) -- non-cascading but affects multi-user testing
2. **GAP-D1-03** (document-required checklist blocker) -- cascading, blocks entire Day 1+ lifecycle

**Recommendation**: Fix GAP-D1-03 before next QA cycle. Either make the document optional on checklist completion, or seed a default document with the onboarding checklist.
