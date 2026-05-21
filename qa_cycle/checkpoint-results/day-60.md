# Day 60 (AMENDED) -- Firm matter closure + generate Statement of Account [FIRM]

**Date**: 2026-05-21
**Actors**: Bob Ndlovu (Admin, Phase A gate resolution), Thandi Mathebula (Owner, Phase B/C closure)
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443)
**Matter**: Dlamini v Road Accident Fund (RAF-2026-001, ID: 85b09bb3-5cdd-42b9-8364-1bea1e83153d)

---

## Phase A: Resolve closure gate prerequisites (Bob Ndlovu)

### A1: Review REQ-0003 submitted items (clear Info Requests gate)

| Checkpoint | Description | Result |
|-----------|-------------|--------|
| 60.1 | Navigate to RAF-2026-001 > Client > Requests > REQ-0003 | PASS -- REQ-0003 visible, In Progress, 0/2 accepted |
| 60.2 | REQ-0003 shows 2 items submitted by Sipho (Hospital discharge summary + Orthopaedic report) | PASS -- both items in SUBMITTED status with Accept/Reject buttons |
| 60.3 | Accept item 1 (Hospital discharge summary) | PASS -- status transitions to ACCEPTED, counter advances 1/2 |
| 60.4 | Accept item 2 (Orthopaedic report), envelope auto-transitions to COMPLETED | PASS -- 2/2 accepted, envelope status COMPLETED, "Completed on May 21, 2026" stamp |
| 60.5 | Verify REQ-0003 status = COMPLETED. Both info requests now COMPLETED. | PASS -- REQ-0003 status badge = "Completed", 2/2 accepted, both REQ-0001 and REQ-0003 COMPLETED |

### A2: Complete/cancel open tasks (clear Open Tasks gate)

| Checkpoint | Description | Result |
|-----------|-------------|--------|
| 60.6 | Navigate to RAF-2026-001 > Work > Tasks > view 9 open tasks | PASS -- Tasks tab shows 9 open tasks from RAF template, all Medium priority |
| 60.7 | Mark "Initial RAF claim assessment & instructions" (2h30m time logged) as DONE via Open > In Progress > Mark Done | PASS -- status transitions to Done, "Completed by Bob Ndlovu on May 21, 2026", time entry 2h30m preserved |
| 60.7 | Mark "File RAF1 claim form + supporting documents" (1h30m time logged) as DONE | PASS -- same Open > In Progress > Mark Done lifecycle |
| 60.7 | Cancel 7 remaining open tasks (no time logged): Prescription monitoring, Settlement/judgment payout, Trial/hearing, Court action, Settlement negotiation, Insurer correspondence, Statutory medical reports | PASS -- all 7 cancelled via Open > Cancelled |
| 60.7 | Cancel 2 auto-generated follow-up tasks (In Progress) | PASS -- both "Follow-up: Follow-up:..." tasks cancelled via In Progress > Cancelled |
| 60.8 | Verify 0 open/in-progress tasks remaining | PASS -- Tasks tab with Open+In Progress filter shows "No tasks match this filter" |

**Note**: Task completion auto-generates follow-up tasks (In Progress) when a task has no recurrence set. These cascading follow-ups (4 total: 2 from first Done, 2 from their follow-ups being Done'd) were all cancelled. Final state: 4 Done + 9 Cancelled = 13 total tasks, 0 open.

### A3: Resolve court date (clear Court Dates gate)

| Checkpoint | Description | Result |
|-----------|-------------|--------|
| 60.9 | Navigate to RAF-2026-001 > Schedule > Court Dates | PASS -- Pre-Trial court date 2026-06-04 at Gauteng Division Pretoria, STATUS: Scheduled |
| 60.10 | Actions menu > Record Outcome > "Pre-trial conference completed. Matter settled" | PASS -- dialog accepted outcome text, submitted successfully |
| 60.11 | Verify court date status no longer blocking closure | PASS -- status changed from "Scheduled" to "Heard" |

### A4: Approve trust payment (clear Trust Balance gate -- Section 86 dual-approval)

| Checkpoint | Description | Result |
|-----------|-------------|--------|
| 60.12 | Navigate to Trust Accounting > Transactions > locate PAY/2026/001 R 70,000 AWAITING_APPROVAL | PASS -- transaction visible on transactions page with Approve/Reject buttons |
| 60.13 | Bob clicks Approve | **FAIL** -- Error: "Insufficient permissions for this operation" |

**Root cause investigation**: Bob (Admin role) does not have the APPROVE_TRUST_PAYMENT capability. This capability appears to be Owner-role-only.

**Thandi self-approval attempt**: Switched to Thandi (Owner) and attempted approval.
- Error: "The transaction recorder cannot be the sole approver. A different member with APPROVE_TRUST_PAYMENT capability must approve this transaction."

**Deadlock**: The system requires a different member from the recorder to have APPROVE_TRUST_PAYMENT capability. Thandi (Owner) recorded the payment and cannot self-approve. Bob (Admin) and Carol (Member) lack the capability. Only the Owner role has it, but the Owner is the recorder.

| 60.14 | Trust balance drops to R 0.00 | NOT REACHED -- blocked by approval deadlock |
| 60.15 | Client ledger shows payment-out R 70,000 | NOT REACHED |

---

## Phase B: Verify all gates green (pre-closure check) -- Thandi

**Result**: NOT REACHED -- blocked by trust payment approval deadlock (Phase A4)

---

## Phase C: Run matter closure workflow -- Thandi

**Result**: NOT REACHED -- blocked by trust payment approval deadlock (Phase A4)

---

## BLOCKER: OBS-6002 -- Admin role lacks APPROVE_TRUST_PAYMENT capability, creating dual-approval deadlock

**Severity**: BLOCKER (cascading -- blocks Day 60, 61, and all subsequent days)

**Root cause**: The APPROVE_TRUST_PAYMENT capability is only assigned to the Owner role. In a dual-approval flow where the Owner (Thandi) recorded the payment, no other team member can approve it because Admin and Member roles lack the capability.

**Evidence**:
- Bob (Admin) approval attempt: "Insufficient permissions for this operation"
- Thandi (Owner) approval attempt: "The transaction recorder cannot be the sole approver. A different member with APPROVE_TRUST_PAYMENT capability must approve this transaction."
- Trust Accounting Settings > Approval Settings: "Single approval" mode

**Impact**: Cannot zero the trust balance, which blocks matter closure (trust balance gate), Statement of Account generation, portal download (Day 61), and all subsequent days.

**Fix options**:
1. **(Preferred) Grant Admin role the APPROVE_TRUST_PAYMENT capability** -- this is the correct behavior for a law firm where the Owner/Senior Partner records transactions and an Admin/Associate provides the dual-approval sign-off
2. Change approval mode to "None" (removes dual-approval requirement entirely -- not recommended for Section 86 compliance)
3. Add a second Owner user (workaround, not scalable)

**This is a code/configuration bug, not a scenario gap.** The dual-approval feature is correctly designed but the role-capability matrix has a gap: Admin users need APPROVE_TRUST_PAYMENT for the dual-approval pattern to work in practice.

---

## Gates Resolved (3 of 4)

| Gate | Status | Resolution |
|------|--------|------------|
| Info Requests | RESOLVED | REQ-0003 items accepted, envelope COMPLETED |
| Open Tasks | RESOLVED | 2 Done (with time logged) + 7 Cancelled + 4 follow-ups cancelled = 0 open |
| Court Dates | RESOLVED | Pre-Trial court date marked "Heard" via Record Outcome |
| Trust Balance | **BLOCKED** | OBS-6002 -- Admin lacks APPROVE_TRUST_PAYMENT capability |

---

## Console Errors

- `/api/assistant/invocations` 404 (recurring, AI assistant endpoint -- pre-existing)
- No JavaScript errors from application code

## New Gaps

| Gap ID | Summary | Severity | Status |
|--------|---------|----------|--------|
| OBS-6002 | Admin role lacks APPROVE_TRUST_PAYMENT capability, creating dual-approval deadlock when Owner records a trust payment | BLOCKER | OPEN |
