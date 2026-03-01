# ADR-130: Prerequisite Enforcement Strategy — Soft-Blocking vs. Hard-Blocking

**Status**: Accepted

**Context**:

DocTeams accumulates rich metadata about customers through field packs (FICA, tax, billing), lifecycle management, and custom field definitions — but none of this data is enforced at the points where it matters. When a user clicks "Generate Invoice" for a customer missing a billing address, the generation either fails with an opaque error or produces an incomplete document. When a customer transitions from ONBOARDING to ACTIVE, the system does not verify that required compliance fields (e.g., FICA pack fields in South African accounting firms) have been captured. The platform needs a mechanism to enforce data completeness at action points, but the enforcement UX determines whether users perceive the system as helpful or hostile.

The challenge is that prerequisite failures happen at moments of high intent — the user is trying to accomplish something (generate an invoice, activate a customer, create a project). The enforcement mechanism must surface the gap without losing the user's context or forcing them into a multi-step navigation detour. DocTeams operates in professional services firms where administrative staff may be handling dozens of customers; any friction that requires navigating away from the current action creates cognitive overhead and risks the user forgetting to return to complete the original task.

**Options Considered**:

1. **Soft-blocking with inline remediation modal** -- When prerequisites are not met, the system presents a modal overlay on the current page showing the missing fields with inline editors. The user fills the gaps in the modal, re-validates, and the original action proceeds automatically.
   - Pros:
     - Zero context-switching — user stays on the current page
     - Original action intent is preserved and auto-executed after remediation
     - Single interaction to fix and proceed
     - Consistent UX pattern across all enforcement points (one modal component)
   - Cons:
     - Modal must support rendering different field types (text, date, select, etc.)
     - Some structural violations (e.g., "add a portal contact") cannot be resolved inline — requires a link/redirect
     - More complex frontend component than a simple error message

2. **Hard-blocking with navigation redirect** -- When prerequisites are not met, the system redirects the user to the relevant entity page (e.g., customer detail) with a banner highlighting the missing fields. After filling the fields, the user must navigate back to the original action.
   - Pros:
     - Simpler frontend implementation — reuses existing entity edit pages
     - Full context of the entity is visible while editing
     - No new modal component needed
   - Cons:
     - Loses the user's action context (they must remember what they were doing and navigate back)
     - Two-step process: fix fields → return to action → retry
     - Multiple navigation events create friction, especially for batch workflows
     - Difficult to implement "return to original action" reliably across all entry points

3. **Warning-only (non-blocking)** -- The system shows a warning banner or toast notification listing missing fields but allows the action to proceed. The user can choose to fix the fields later.
   - Pros:
     - Zero friction — user is never blocked
     - Simplest implementation
     - No UX changes to existing action flows
   - Cons:
     - Defeats the purpose of enforcement — users will ignore warnings under time pressure
     - Incomplete data propagates to outputs (invoices without billing addresses, documents with missing fields)
     - External integrations (accounting sync) receive incomplete records
     - Compliance requirements (FICA) cannot be "warned about" — they must be enforced

**Decision**: Option 1 -- Soft-blocking with inline remediation modal.

**Rationale**:

The soft-blocking approach is the only option that balances enforcement rigor with workflow efficiency. Professional services firms process high volumes of customer interactions; any enforcement mechanism that breaks the user's flow will face resistance and workarounds (such as filling fields with placeholder data). The inline remediation modal preserves the action intent — the user clicked "Generate Invoice," and after filling two missing fields in the modal, the invoice generates automatically. This feels like assistance rather than obstruction.

The warning-only approach was rejected because it undermines the core objective. DocTeams is transitioning from "data capture" to "data quality assurance" — a warning that users can dismiss does not achieve quality assurance. In compliance-sensitive contexts (FICA in South Africa, KYC in financial services), missing data is not acceptable regardless of user preference.

The hard-blocking approach was rejected for UX reasons. In testing and user research with accounting firm workflows, navigation-based remediation consistently loses user context. A bookkeeper generating invoices for 15 customers will not tolerate being redirected to a customer detail page, filling a field, navigating back to the invoice list, and re-initiating the generation — for each incomplete customer. The modal approach handles this in a single interaction per customer.

For structural violations that cannot be resolved inline (e.g., "customer needs a portal contact"), the modal shows a descriptive resolution message with a link to the relevant page. This is an acceptable fallback because structural violations are less common than missing custom fields and typically require a more deliberate setup action.

**Consequences**:

- A shared `PrerequisiteModal` frontend component is built once and reused across all enforcement points (lifecycle transitions, invoice generation, proposal sending, document generation, project creation)
- The modal must support rendering all `FieldType` variants (TEXT, NUMBER, DATE, SELECT, BOOLEAN, PHONE, DROPDOWN) via an `InlineFieldEditor` component
- Backend returns structured 422 responses with `PrerequisiteCheck` payload; frontend interprets these into modal content
- Structural violations (non-custom-field issues) show resolution text + link rather than inline editors
- The modal implements a "Check & Continue" re-validation flow: fill fields → save → re-check → proceed if passed
- Existing action-triggering UI elements (buttons, dropdowns) must be wrapped with a prerequisite check call before executing the action
- Related: [ADR-131](ADR-131-prerequisite-context-granularity.md) (context granularity determines which fields the modal shows), [ADR-133](ADR-133-auto-transition-incomplete-fields.md) (auto-transitions cannot show a modal, so they block and notify instead)
