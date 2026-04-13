# Day 2 Checkpoint Results — FICA/KYC Onboarding (Sipho Dlamini)

**Actor**: Bob Ndlovu (Admin)
**Date**: 2026-04-12
**Cycle**: 2
**Client**: Sipho Dlamini (7d062c02-bcf1-4071-8cea-787ee313781a)

---

### CP-2.1: Transition Sipho to ONBOARDING
- **Result**: PASS
- **Evidence**: Clicked "Change Status" > "Start Onboarding" on Sipho's client detail page. Confirmation dialog appeared, clicked "Start Onboarding". Lifecycle badge updated from "Prospect" to "Onboarding". "Since Apr 12, 2026" timestamp appeared. "Onboarding" tab appeared in the tab bar. Client Readiness showed "Onboarding checklist (0/8)".
- **Gap**: —

### CP-2.2: Navigate to Onboarding/Compliance tab — verify FICA checklist auto-instantiated
- **Result**: PASS
- **Evidence**: Clicked "Onboarding" tab. "Legal Individual Client Onboarding" checklist auto-instantiated with 9 items (0/9 completed, 0/8 required). Checklist pack is `fica-kyc-za` equivalent — named "Legal Individual Client Onboarding". Status badge: "In Progress".
- **Gap**: —

### CP-2.3: Verify checklist contains "Certified ID Copy", "Proof of Address", "Source of Funds"
- **Result**: PASS
- **Evidence**: Checklist contains 9 items covering all 3 required items (mapped names): (1) Proof of Identity (= Certified ID Copy), (2) Proof of Address, (3) Source of Funds Declaration (= Source of Funds). Plus 6 additional items: Beneficial Ownership Declaration, Engagement Letter Signed, Conflict Check Performed, Power of Attorney Signed, FICA Risk Assessment, Sanctions Screening. Items 7-9 have dependency chains (Blocked by other items). Each item shows Required/Optional tag, description, and document requirement.
- **Gap**: —

### CP-2.4: Mark "Certified ID Copy" complete + add note
- **Result**: PASS
- **Evidence**: Clicked "Mark Complete" on "Proof of Identity". Completion form expanded with Notes field and document selector combobox. Document-requiring items have a disabled Confirm button until a document is linked. Filled notes: "Verified against home affairs ID". Selected "test-fica-id.pdf" from uploaded documents. Confirm button enabled, clicked. Item transitioned to "Completed" with note and date visible: "Apr 12, 2026 — Verified against home affairs ID". Dependency chain resolved: FICA Risk Assessment and Sanctions Screening unblocked.
- **Note**: Completion flow is more rigorous than scenario expected — items with "Requires document" annotation require uploading a document to the Documents tab first, then selecting it in the checklist completion form. Had to upload 5 test PDFs to the Documents tab before completing document-requiring items.
- **Gap**: —

### CP-2.5: Mark "Proof of Address" complete + upload test PDF
- **Result**: PASS
- **Evidence**: Uploaded test-fica-address.pdf via Documents tab upload dialog (drag-and-drop area). Completed "Proof of Address" with note "Utility bill verified, within 3 months" and linked test-fica-address.pdf. Checklist updated to 2/9 completed. Item shows "Completed" badge with date and note.
- **Gap**: —

### CP-2.6: Mark "Source of Funds" complete + add note
- **Result**: PASS
- **Evidence**: Completed "Source of Funds Declaration" with note "Employment income, verified via payslip" and linked test-fica-funds.pdf. Also completed remaining items in sequence: Beneficial Ownership Declaration, Engagement Letter Signed, Conflict Check Performed (no document required), Power of Attorney Signed, FICA Risk Assessment (no document required), Sanctions Screening (no document required). All 9/9 items completed (8/8 required).
- **Gap**: —

### CP-2.7: Verify checklist 100% complete — customer auto-transitions to ACTIVE
- **Result**: PARTIAL
- **Evidence**: Checklist showed "9/9 completed (8/8 required)" with "Completed" status badge. However, customer did NOT auto-transition to ACTIVE. Two issues: (1) "Tax Number is required for Customer Activation" was a blocking activation requirement not covered in the Day 1 creation flow or Day 2 scenario steps. After filling tax_number via Edit dialog, the blocking message cleared. (2) Even after clearing all blockers, auto-transition still did not fire — required manual "Change Status > Activate" to transition to ACTIVE.
- **Gap**: GAP-D2-01, GAP-D2-02

### CP-2.8: Verify ACTIVE badge on client detail page
- **Result**: PASS
- **Evidence**: After manual activation via "Change Status > Activate", client detail page shows two "Active" badges (record status + lifecycle status). Screenshot captured: `qa_cycle/screenshots/cycle-2/day02-cp2.8-active-badge.png`.
- **Gap**: —

### CP-2.9: Navigate to audit log for this client — verify FICA completion events recorded
- **Result**: FAIL
- **Evidence**: No audit log page exists in the frontend for viewing per-client audit events. Tried `/org/mathebula-partners/audit`, `/org/mathebula-partners/audit-log`, `/org/mathebula-partners/settings/audit` — all return 404. Settings sidebar has no "Audit" or "Audit Log" link. Customer detail page tabs do not include an "Audit" or "Activity" tab. Backend audit event infrastructure exists (Phase 6), but no frontend page surfaces audit events per customer context. Backend API `/api/audit-events` returns 401 without auth, confirming endpoint exists.
- **Gap**: GAP-D2-03

---

## Summary

| Metric | Count |
|--------|-------|
| Total checkpoints | 9 |
| PASS | 6 |
| PARTIAL | 1 |
| FAIL | 1 |
| SKIP | 0 |
| New gaps | 3 |

## Console Errors
- 0 console errors during Day 2 execution.
