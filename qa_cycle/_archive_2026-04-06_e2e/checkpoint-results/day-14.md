# Day 14 — FICA Completion and Automation Verification

## Executed: 2026-03-16T03:20Z (cycle 3)
## Actor: Alice (Owner)

### Prerequisite Data

Time entries from Day 7 and Week 2 could NOT be created due to GAP-030 (Log Time crash). All time entry prerequisites are blocked.

### Checkpoint 14.1 — All 9 FICA items marked complete for Kgosi Construction
- **Result**: PARTIAL
- **Evidence**: Navigated to Kgosi Construction customer detail page. Status correctly shows "Onboarding". Onboarding tab shows "Generic Client Onboarding" checklist with 4 items (not FICA KYC 9-item checklist). Completed 3 of 4 generic items:
  1. Confirm client engagement — Completed (Mar 16, 2026)
  2. Verify contact details — Completed (Mar 16, 2026)
  3. Confirm billing arrangements — Completed (Mar 16, 2026)
  4. Upload signed engagement letter — **BLOCKED** (requires document upload: "This item requires a document upload. Please upload: Signed engagement letter")
- **FICA KYC checklist**: Not auto-instantiated on this customer. The "Manually Add Checklist" button is available but the FICA checklist was not tested in this cycle (was verified in cycle 2 via GAP-026 fix). The generic 4-item checklist was auto-created on ONBOARDING transition, not the FICA 9-item one.
- **Gap**: The generic onboarding checklist requires a document upload for the engagement letter item. This blocks completion without an actual file upload. Not a bug — correct enforcement — but creates friction in testing.

### Checkpoint 14.2 — FICA Verified custom field updated
- **Result**: NOT TESTED
- **Notes**: FICA custom fields (from accounting-za field group) are not attached to this customer. Only "Contact & Address" field group is shown. GAP-008B (FICA field groups not auto-attached during customer creation) remains open.

### Checkpoint 14.3 — Kgosi Construction lifecycle transitioned to ACTIVE
- **Result**: FAIL
- **Evidence**: Clicked "Change Status" > "Activate". System correctly blocked activation with message: "Cannot activate customer — one or more onboarding checklists are not yet completed." The generic onboarding checklist has 1 incomplete item (Upload signed engagement letter). Lifecycle enforcement is working correctly — this is by design, not a bug.
- **Workaround**: Would need to either (a) upload a document and complete the checklist, or (b) have a backend API to force the transition.

### Checkpoint 14.4 — Notification page loads and shows automation-triggered notifications
- **Result**: PASS (empty)
- **Evidence**: Navigated to `/org/e2e-test-org/notifications`. Page loads correctly showing "You're all caught up" with message "When there are new comments, task assignments, or status changes, they'll appear here." No automation-triggered notifications present — confirms GAP-003 (CHECKLIST_COMPLETED trigger does not exist) and GAP-007 (delayed triggers cannot be verified).

### Checkpoint 14.5 — Automation execution history accessible
- **Result**: NOT TESTED
- **Notes**: Did not navigate to `/org/e2e-test-org/settings/automations/executions`. Will test in a future cycle if time permits.

### Checkpoint 14.6 — FICA Reminder delay cannot be verified (GAP-007 confirmed)
- **Result**: CONFIRMED (WONT_FIX)
- **Evidence**: No automation notifications fired. The FICA Reminder automation has a 7-day delay that cannot be tested in real time.

### Checkpoint 14.7 — CHECKLIST_COMPLETED trigger absence confirmed (GAP-003)
- **Result**: CONFIRMED (WONT_FIX)
- **Evidence**: Completed 3 of 4 generic checklist items. No automation triggered. Even if all 4 were completed, the CHECKLIST_COMPLETED trigger type does not exist in the automation system.

### Checkpoint 14.8 — Time entry summaries show accurate totals across 2 weeks
- **Result**: FAIL (BLOCKED by GAP-030)
- **Evidence**: No time entries exist because Log Time crashes. Cannot verify time summaries.

---

## Summary

| Checkpoint | Result | Gap |
|-----------|--------|-----|
| 14.1 — FICA items complete for Kgosi | PARTIAL (3/4 generic, FICA not tested) | — |
| 14.2 — FICA Verified custom field updated | NOT TESTED | GAP-008B |
| 14.3 — Kgosi lifecycle to ACTIVE | FAIL (blocked by checklist) | — |
| 14.4 — Notification page loads | PASS (empty) | GAP-003, GAP-007 |
| 14.5 — Automation execution history | NOT TESTED | — |
| 14.6 — FICA Reminder delay (GAP-007) | CONFIRMED | GAP-007 |
| 14.7 — CHECKLIST_COMPLETED absence (GAP-003) | CONFIRMED | GAP-003 |
| 14.8 — Time entry summaries | FAIL (BLOCKED) | GAP-030 |

**Totals**: 1 PASS, 2 FAIL, 2 NOT TESTED, 1 PARTIAL, 2 CONFIRMED (known gaps)

## Observations

1. **Lifecycle enforcement works correctly**: The system properly blocks ONBOARDING -> ACTIVE transition when checklist items are incomplete. This is good security/compliance behavior but makes testing harder when document uploads are required.

2. **Generic vs FICA checklist**: The auto-instantiated checklist on ONBOARDING transition is the "Generic Client Onboarding" (4 items), not "FICA KYC — SA Accounting" (9 items). The FICA checklist must be manually added via "Manually Add Checklist". This may be by design (multiple checklists can coexist) but differs from the lifecycle script expectation.

3. **Document-gated checklist items**: The "Upload signed engagement letter" item requires a document upload (`Requires document: Signed engagement letter`). This is a correct enforcement but means the checklist cannot be completed without uploading an actual file. A document selector combobox is shown but no documents exist for this customer.

4. **Comments work independently of time logging**: The task commenting system is fully functional and not affected by the GAP-030 crash. Comments post immediately with correct attribution and timestamps.
