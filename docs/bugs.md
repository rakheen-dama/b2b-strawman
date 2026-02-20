# Bug Tracker

Bugs found during product walkthrough testing. Each entry has enough context for an agent to investigate and fix independently.

**Format**: Each bug gets an ID (BUG-NNN), severity, affected area, description, root cause (if known), and fix guidance.

**Severities**: `critical` (blocks core flow), `high` (feature broken), `medium` (works but wrong), `low` (cosmetic/minor)

---

## BUG-001: New customers default to ACTIVE, bypassing onboarding lifecycle

**Severity**: high
**Area**: Backend — Customer creation
**Found in**: Walkthrough Chapter 2 (Onboarding Your First Client)

**Description**: When creating a customer via the UI, the customer is immediately set to `ACTIVE` lifecycle status. The PROSPECT and ONBOARDING stages are never reachable through normal creation flow, making the entire onboarding lifecycle (checklists, FICA compliance, transition prompts) unreachable for new customers.

**Root Cause**: `Customer.java` line 94 — the constructor hardcodes `this.lifecycleStatus = LifecycleStatus.ACTIVE`. The `CustomerService.createCustomer()` (line 118) uses this constructor without passing a lifecycle status. A second constructor accepting `LifecycleStatus` exists (line 97) but is never called from the service.

**Affected Files**:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java` (line 94)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` (line 118)
- Possibly `frontend/components/customers/create-customer-dialog.tsx` (no lifecycle field in create form)

**Fix Guidance**:
1. Change default in `Customer` constructor from `LifecycleStatus.ACTIVE` to `LifecycleStatus.PROSPECT`
2. Optionally add a lifecycle status dropdown to the create customer dialog (Prospect as default, Active as shortcut for existing clients being imported)
3. Check and update any tests that assert `ACTIVE` as the default lifecycle status after creation
4. Verify the `LifecycleTransitionDropdown` on the customer detail page renders correctly for PROSPECT status (it currently gates on `customer.status === "ACTIVE" && customer.lifecycleStatus` — the `customer.status` here is the archive status, not lifecycle, so this should still work)

**Impact**: The entire Phase 14 (Customer Compliance & Lifecycle) onboarding flow is effectively dead code for newly created customers. Existing customers in the database are also all ACTIVE.

---

<!-- TEMPLATE — copy this for new bugs:

## BUG-NNN: [Short description]

**Severity**: [critical/high/medium/low]
**Area**: [Backend/Frontend/Both] — [specific domain]
**Found in**: [Walkthrough chapter or scenario card]

**Description**: [What you observed vs. what should happen]

**Root Cause**: [If known — file, line, reason. Otherwise "Unknown — needs investigation"]

**Affected Files**:
- [file path and what needs changing]

**Fix Guidance**:
1. [Step-by-step fix instructions for the agent]

**Impact**: [What's broken because of this bug]

-->
