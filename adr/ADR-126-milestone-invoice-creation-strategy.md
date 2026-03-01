# ADR-126: Milestone Invoice Creation Strategy

**Status**: Accepted

**Context**:

Fixed-fee proposals can define 1-N payment milestones, each with a percentage of the total fee and a relative due date (days after acceptance). When a portal contact accepts a fixed-fee proposal with milestones, the system needs to create invoices for those milestones. The question is when those invoices are created: all at once on acceptance, one-at-a-time as milestones are reached, or on a scheduled basis before their due dates.

The existing invoice system supports DRAFT invoices (editable, not yet sent to client). Firms regularly create DRAFT invoices ahead of time and approve/send them when ready. The invoice lifecycle is DRAFT → APPROVED → SENT → PAID / VOID. A DRAFT invoice has no financial commitment — it is purely an internal record until approved.

A typical fixed-fee milestone schedule might look like: 30% on acceptance (0 days), 40% at mid-project delivery (+30 days), 30% on completion (+60 days). The firm needs visibility into the full billing schedule from the moment the proposal is accepted so they can plan cash flow and track billing progress.

**Options Considered**:

1. **Create all invoices on acceptance** -- When a proposal with milestones is accepted, create one DRAFT invoice per milestone immediately. Each invoice has the calculated amount (`fixedFeeAmount * percentage / 100`), a due date (`acceptedAt + relativeDueDays`), and a description matching the milestone description. The firm reviews and approves/sends each invoice as the milestone is reached.
   - Pros:
     - Full billing schedule visible immediately in the invoices list — firm can plan cash flow from day one
     - No additional scheduling infrastructure needed — the invoice system already handles DRAFT invoices with future due dates
     - Each invoice is independently editable (the firm can adjust amounts or descriptions before approving)
     - Auditable: the complete billing plan is captured at acceptance time, linked back to the proposal
     - Consistent with how firms already work: create invoices ahead, approve when ready
   - Cons:
     - Creates potentially many DRAFT invoices at once (typical: 2-5, extreme: 10+)
     - If the engagement scope changes mid-project, the firm must manually update or void pre-created invoices
     - Invoices with far-future due dates (e.g., +180 days) sit in DRAFT for months

2. **Create invoices on-demand as milestones are reached** -- Only the first milestone invoice (if `relativeDueDays = 0`) is created on acceptance. Subsequent invoices are created when the firm manually triggers "Create invoice for milestone X" from the proposal detail page.
   - Pros:
     - Avoids creating invoices for milestones that may never be reached (engagement terminated early)
     - Each invoice reflects current reality at the time of creation
     - Simpler acceptance orchestration (fewer entities created in the transaction)
   - Cons:
     - No upfront billing schedule visibility — the firm must remember to create invoices for each milestone
     - Requires a new "Create milestone invoice" action and UI on the proposal detail page
     - The proposal must maintain state about which milestones have been invoiced — additional complexity
     - Breaks the "acceptance sets up everything" promise that makes proposals valuable
     - If the firm forgets to create a milestone invoice, revenue is lost

3. **Scheduled invoice creation before due dates** -- A background job creates DRAFT invoices N days before each milestone's due date. For example, create the invoice 7 days before `acceptedAt + relativeDueDays`.
   - Pros:
     - Invoices appear just-in-time, avoiding long-lived DRAFTs
     - Automatic — no manual action required from the firm
   - Cons:
     - Requires a new scheduled processor (complexity, testing, failure handling)
     - The firm cannot see the full billing schedule until each invoice materializes
     - If the scheduler fails or the system is down near a due date, the invoice is missed
     - The "lead time" (N days before due) is an arbitrary policy that may not suit all firms
     - Milestone reference tracking is needed to know which milestones have been scheduled vs. created

**Decision**: Option 1 -- Create all invoices on acceptance.

**Rationale**:

The primary value of proposal milestones is billing schedule visibility. When a firm defines a three-milestone payment plan — 30% on signing, 40% at mid-delivery, 30% on completion — they expect to see three invoices appear the moment the client accepts. This matches how professional services billing works in practice: the engagement letter (proposal) defines the payment schedule, and invoices are prepared upfront for the full engagement.

Creating all invoices as DRAFTs is harmless: DRAFT invoices have no financial commitment, are not visible to the client (only SENT+ invoices appear in the portal), and are fully editable. The firm can adjust amounts, change descriptions, or void invoices before approving them. This gives the firm complete control while providing immediate billing schedule visibility.

The concern about pre-creating invoices for milestones that may never be reached (engagement terminated early) is addressed by the existing invoice lifecycle: the firm simply voids unneeded invoices. This is a known, well-understood workflow — every professional services firm knows how to void a DRAFT invoice.

Option 2 (on-demand) was rejected because it shifts cognitive burden to the firm: they must remember to create invoices at each milestone, which is the exact manual work that proposals are meant to eliminate. Option 3 (scheduled) was rejected because it introduces unnecessary complexity (a new scheduled processor, lead-time configuration, failure handling) for a problem that does not require scheduling — the billing schedule is fully known at acceptance time.

The typical milestone count is 2-5 per proposal. Creating 5 DRAFT invoices in a single transaction is trivially fast and well within database performance expectations.

**Consequences**:

- Acceptance orchestration creates one DRAFT invoice per `ProposalMilestone` in the same transaction
- Each invoice: amount = `fixedFeeAmount * milestone.percentage / 100`, due date = `acceptedAt + milestone.relativeDueDays`, line type = `FIXED_FEE`, description = milestone description
- `ProposalMilestone.invoiceId` is set to the created invoice's ID for cross-reference
- If no milestones are defined for a FIXED proposal, a single DRAFT invoice for the full `fixedFeeAmount` is created (implicit single milestone)
- DRAFT invoices are not visible in the portal — the firm must approve and send each invoice separately
- If the engagement scope changes, the firm manually edits or voids affected DRAFT invoices
- The invoices list page will show these milestone invoices alongside other invoices, filterable by project/customer
- Related: [ADR-125](ADR-125-acceptance-orchestration-transaction-boundary.md)
