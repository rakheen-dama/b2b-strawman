# ADR-232: Configurable Dual Authorization for Trust Payments

**Status**: Accepted

**Context**:

Trust payments (PAYMENT, FEE_TRANSFER, REFUND) require authorization before they affect client balances. South African law requires "proper authorization" for trust account transactions but does not mandate a specific number of approvers for small firms. Larger firms — and those with professional indemnity insurance requirements or internal compliance policies — want dual authorization, where two different people must approve a payment before it is executed. The system needs to support both single and dual approval patterns without forcing all firms into the more restrictive model.

The approval model directly affects the TrustAccount entity design, the transaction lifecycle (draft -> pending -> approved -> executed), the authorization service, and the frontend approval workflow. It also intersects with the separation-of-duties principle: the person who records a transaction should not be the sole person who approves it.

**Options Considered**:

1. **Single approval only** — One person with the APPROVE_TRUST_PAYMENT capability approves the transaction. The recorder and approver must be different people (separation of duties), but only one approval is needed regardless of the payment amount or firm size.
   - Pros:
     - Simplest implementation: one `approved_by` and `approved_at` column on the transaction entity. No threshold logic, no second-approval workflow, no conditional UI states.
     - Adequate for small firms (2-3 people) where only one person — typically the principal — has approval authority. Requiring two approvers when only one person can approve is operationally impossible.
     - Meets the minimum legal requirement: SA law requires proper authorization but does not prescribe dual sign-off for all payment sizes.
     - Faster payment processing: one approval step means payments can be executed more quickly, which matters for time-sensitive payments (sheriff costs, urgent counsel fees).
   - Cons:
     - Insufficient for firms wanting tighter controls. Medium and large firms often have internal policies (or insurer requirements) mandating dual sign-off on payments above a certain amount.
     - No protection against a single compromised or negligent approver. If the one authorized person approves a fraudulent payment, there is no second check.
     - Cannot satisfy audit requirements for firms whose professional indemnity insurers require evidence of dual authorization on high-value payments.
     - Firms that grow from 3 to 10+ people cannot upgrade their approval controls without a system change.

2. **Dual approval always** — Every trust payment requires two different approvers, regardless of amount. The recorder cannot be either approver (strict three-person separation), or the recorder can be one of the two approvers but not both.
   - Pros:
     - Maximum safety: every payment passes through two independent checks. Fraud or error by one person is caught by the second.
     - Simple rule: no threshold logic, no conditional behavior. Every payment follows the same workflow.
     - Satisfies the strictest compliance interpretations and insurer requirements without configuration.
     - Clear audit trail: every transaction has two approval signatures, making audit reviews straightforward.
   - Cons:
     - Impractical for 2-3 person firms. If only the principal has APPROVE_TRUST_PAYMENT capability, dual approval is impossible — there is no second approver. The firm would be unable to process any trust payments.
     - Even in firms with two authorized approvers, both must be available for every payment. Holiday, illness, or travel creates a bottleneck where routine payments (R500 court filing fees) are blocked waiting for a second signature.
     - Operationally burdensome for high-volume, low-value payments. A firm processing 50 sheriff-cost payments per month does not benefit from dual approval on each R800 payment.
     - No flexibility: firms cannot opt into a lighter-weight process even when their risk profile and size justify it.

3. **Configurable per trust account with optional threshold** — Each TrustAccount entity carries two configuration fields: `require_dual_approval` (boolean, default false) and `payment_approval_threshold` (decimal, nullable). When `require_dual_approval` is false, single approval applies (recorder != approver). When true and threshold is null, all payments require dual approval. When true and threshold is set, payments at or above the threshold require dual approval while payments below it require only single approval. Self-approval prevention: the recorder cannot be the sole approver in single mode, and the two approvers must be different people in dual mode.
   - Pros:
     - Covers all practical firm configurations: solo practitioners and small firms use single approval; medium firms enable dual for all payments; cost-conscious firms set a threshold (e.g., dual only above R50,000) to balance safety with operational efficiency.
     - Configuration lives on TrustAccount, not OrgSettings, so a firm with both a general trust account and an investment trust account can apply different policies to each.
     - The threshold provides a useful middle ground: routine small payments (court filing fees, sheriff costs) get fast single approval while large payments (settlement payouts, counsel fees) get dual scrutiny.
     - Self-approval prevention is built into both modes, maintaining separation of duties regardless of configuration.
     - Incremental adoption: a firm can start with single approval and enable dual later as they grow, without data migration or workflow changes.
     - Audit trail captures the approval mode that was in effect at the time of approval, so auditors can verify compliance with the firm's own policy.
   - Cons:
     - More complex approval service: the service must check the trust account's configuration, compare the payment amount against the threshold (if set), and enforce the correct approval mode. This is conditional logic that single-mode or dual-mode-only implementations avoid.
     - Frontend must handle three states: pending single approval, pending first of two approvals, and pending second of two approvals. The approval UI is more complex than either pure-single or pure-dual.
     - Threshold edge cases: a payment of exactly the threshold amount requires dual approval (at-or-above semantics). Firms must understand this boundary. A payment of R49,999.99 gets single approval; R50,000.00 gets dual. This is a configuration concern, not a bug, but it requires clear documentation.
     - Testing surface is larger: tests must cover single mode, dual mode, threshold mode, self-approval prevention in each mode, and transitions between modes (e.g., changing the threshold while payments are pending).

4. **Role-based approval chains** — Define configurable approval workflows with ordered steps: e.g., bookkeeper records -> junior partner approves -> senior partner approves. Supports arbitrary chain lengths, delegation (if primary approver is unavailable, delegate to another), escalation (if not approved within N hours, escalate to next level), and timeout handling.
   - Pros:
     - Maximum flexibility: any approval topology can be modeled. Three-tier approval, parallel approval (any two of four partners), conditional routing based on payment type or amount.
     - Delegation and escalation handle availability problems: if an approver is on leave, the system automatically routes to a delegate or escalates after a timeout.
     - Future-proof: if regulatory requirements change to mandate specific approval hierarchies, the system already supports them.
   - Cons:
     - Massively over-engineered for the actual use case. SA law firms do not have complex approval hierarchies for trust payments. The typical pattern is "bookkeeper records, partner approves" or "any two of three partners." These patterns are fully covered by Option 3's boolean + threshold without a workflow engine.
     - Requires a workflow engine: step definitions, transition rules, delegation configuration, escalation timers, timeout handling, and a state machine for each transaction. This is a multi-phase feature (4-6 slices) that delivers no incremental value over Option 3 for the target market.
     - Configuration complexity: firm administrators must define approval chains, assign delegates, set escalation timeouts, and maintain the configuration as staff changes. This is a significant administrative burden for firms that just want "two people must approve large payments."
     - Debugging and support burden: when a payment is stuck in an approval chain, diagnosing whether the issue is a misconfigured chain, a missing delegate, an expired timeout, or a role assignment error requires deep understanding of the workflow engine.
     - The SA legal market does not demand this level of sophistication. Building it speculatively violates YAGNI.

**Decision**: Option 3 — Configurable per trust account with optional threshold.

**Rationale**:

The decision is driven by the diversity of firm sizes and risk profiles in the SA legal market.

Option 1 (single approval only) does not meet the needs of medium and large firms. Some firms are required by their professional indemnity insurers to have dual sign-off on payments above a certain amount. A system that only supports single approval forces these firms to implement out-of-band approval processes (email chains, physical sign-off sheets), which defeats the purpose of a digital trust accounting system.

Option 2 (dual approval always) is impractical for small firms. A 2-person firm where only the principal has APPROVE_TRUST_PAYMENT capability cannot process trust payments under a mandatory dual-approval model — there is no second approver. Even a 3-person firm with two authorized approvers faces operational bottlenecks when one is unavailable. Mandating dual approval for a R500 sheriff-cost payment is disproportionate to the risk.

Option 3 covers all practical scenarios without over-engineering. Small firms (2-3 people) use the default single-approval mode: one person records, a different person approves. Medium firms enable dual approval for all payments. Cost-conscious firms set a threshold: dual approval only above R50,000 (or whatever amount their compliance policy dictates). The configuration lives on the TrustAccount entity rather than OrgSettings because a firm might have different policies for its general trust account versus its investment trust account — this is a real-world pattern where investment accounts carry stricter controls.

Self-approval prevention is a non-negotiable requirement in all modes. In single-approval mode, the recorder cannot be the approver. In dual-approval mode, the recorder can be one of the two approvers (this is a common pattern: the partner who instructs a payment also serves as first approver, but a second independent approver is still required), and the two approvers must be different people. This maintains separation of duties without being so restrictive that small firms cannot operate.

Option 4 (role-based approval chains) is eliminated on complexity grounds. SA law firms do not have multi-tier approval hierarchies for trust payments. The typical patterns — "one person approves" and "two people approve" — are fully covered by a boolean and a threshold. Building a workflow engine with delegation, escalation, and timeout handling would cost 4-6 development slices and deliver zero incremental value over Option 3 for the foreseeable market. If a future vertical (e.g., large corporate law firms with 50+ partners) demands complex approval chains, this can be revisited — but Option 3's data model does not preclude a future upgrade.

The threshold semantics are straightforward: payments at or above the threshold require dual approval; payments below it require single approval. When no threshold is set but dual approval is enabled, all payments require dual approval regardless of amount. This covers the three practical configurations without introducing ambiguity.

**Consequences**:

- TrustAccount entity carries two new fields: `require_dual_approval` (boolean, default false) and `payment_approval_threshold` (decimal, nullable). These are trust-account-level settings, not org-level, because different trust accounts within the same firm may have different approval policies.
- Transaction entity needs six approval/rejection fields: `recorded_by` (the person who created the transaction), `approved_by` and `approved_at` (first approver), `second_approved_by` and `second_approved_at` (second approver, null in single-approval mode), `rejected_by`, `rejected_at`, and `rejection_reason` (either approver can reject at any stage).
- The approval service implements the following logic: (a) determine the required approval mode by checking the trust account's `require_dual_approval` flag and comparing the transaction amount against `payment_approval_threshold` (if set); (b) in single-approval mode, verify the approver is not the recorder; (c) in dual-approval mode, verify the first approver differs from the recorder (or allow the recorder as first approver with a mandatory different second approver), and verify the second approver differs from the first approver; (d) transition the transaction to APPROVED status only when the required number of approvals is met.
- Transaction lifecycle uses `AWAITING_APPROVAL` throughout both approval steps. In dual mode, the first approval sets `approved_by` while the status remains `AWAITING_APPROVAL`; the second approval sets `second_approved_by` and transitions to `APPROVED`. The service distinguishes partial approval by checking `approved_by IS NOT NULL AND second_approved_by IS NULL`. No additional status value is needed — this avoids schema changes to the status CHECK constraint and keeps the state machine simple (RECORDED → AWAITING_APPROVAL → APPROVED/REJECTED/REVERSED).
- Frontend approval UI must display: (a) whether a transaction requires single or dual approval (derived from the trust account's configuration and the transaction amount); (b) who has already approved (in dual mode, show first approver name and indicate second approval is pending); (c) rejection controls at any approval stage with a mandatory rejection reason field.
- Pending approval lists must clearly distinguish between "awaiting first approval" and "awaiting second approval" (derived from `approved_by IS NULL` vs `approved_by IS NOT NULL AND second_approved_by IS NULL`) so approvers can prioritize their queue.
- Audit events capture the approval mode, threshold in effect, and all approver identities. This allows auditors to verify that the firm's configured policy was followed for each transaction.
- Changing a trust account's approval configuration (e.g., enabling dual approval or adjusting the threshold) does not retroactively affect transactions already in the approval pipeline. Transactions in progress continue under the approval mode that was in effect when they entered the approval workflow. New transactions pick up the updated configuration.
- Migration adds `require_dual_approval` and `payment_approval_threshold` columns to the trust_accounts table, and `second_approved_by`, `second_approved_at`, `rejected_by`, `rejected_at`, and `rejection_reason` columns to the trust_transactions table (assuming `recorded_by`, `approved_by`, and `approved_at` already exist from the base transaction model).
