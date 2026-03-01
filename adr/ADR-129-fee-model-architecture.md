# ADR-129: Fee Model Architecture — Single vs. Mixed

**Status**: Accepted

**Context**:

Proposals include a fee configuration that determines how the engagement will be billed. The platform supports three billing models through existing infrastructure: fixed fees (invoices with line items), hourly billing (time entries billed through rate cards), and retainer agreements (periodic consumption with auto-invoicing). The question is whether a single proposal can combine multiple fee models (e.g., fixed fee for initial setup + hourly for ongoing work) or must use exactly one fee model.

Professional services engagements sometimes involve mixed billing: a fixed fee for a defined deliverable (audit report, tax return) combined with hourly billing for ad-hoc advisory work, or a retainer for ongoing support with a separate fixed fee for a specific project. Some competitors (Practice/Ignition) support multiple "services" per proposal, each with its own fee model. However, this flexibility comes with significant UX and orchestration complexity.

The orchestration on acceptance is the key constraint. Each fee model triggers different actions: FIXED creates invoices, HOURLY configures rate card references, and RETAINER creates a `RetainerAgreement`. A mixed-fee proposal would need to execute multiple billing setup paths and present a more complex fee summary to the client. The proposal form UI would also be significantly more complex: instead of choosing one fee model, the user would compose a list of fee items with potentially different models.

**Options Considered**:

1. **Single fee model per proposal** -- Each proposal has exactly one `feeModel` enum value (FIXED, HOURLY, or RETAINER). The fee configuration fields on the proposal entity are model-specific: `fixedFeeAmount`/`fixedFeeCurrency` for FIXED, `hourlyRateNote` for HOURLY, `retainerAmount`/`retainerCurrency`/`retainerHoursIncluded` for RETAINER. Only the fields relevant to the selected model are populated.
   - Pros:
     - Simple mental model: one proposal = one fee model = one billing setup on acceptance
     - Straightforward orchestration: a `switch` on `feeModel` determines the billing setup path
     - Clean UI: fee configuration section shows only the fields relevant to the selected model (card selector, not a dynamic list)
     - Portal display is simple: one fee summary card with the amount and model description
     - Mixed billing can be handled by creating two proposals (e.g., one FIXED for audit, one HOURLY for advisory) or by manually configuring billing post-acceptance
     - Matches the entity model: `Proposal` has typed columns for each model, not a polymorphic fee item table
   - Cons:
     - Cannot represent "fixed fee for setup + hourly for ongoing" in a single proposal — requires two proposals or manual post-acceptance configuration
     - Firms with complex billing arrangements must decompose them into separate proposals or configure billing manually after acceptance

2. **Mixed billing with fee items** -- Add a `ProposalFeeItem` entity: each proposal can have 1-N fee items, each with its own fee model, amount, and description. The orchestration creates billing entities for each fee item.
   - Pros:
     - Flexible: a single proposal can describe a complex billing arrangement
     - More competitive with Practice/Ignition-style "services" per proposal
     - The client sees the full engagement cost structure in one proposal
   - Cons:
     - Significant entity model complexity: new `ProposalFeeItem` entity, potentially with its own milestone sub-items for FIXED fee items
     - Orchestration complexity: must iterate over fee items and execute different billing setup paths for each, handling cross-item dependencies (e.g., two FIXED items creating separate invoice sets)
     - UX complexity: the fee configuration section becomes a dynamic list builder with per-item fee model selection, amount inputs, and milestone editors — significantly harder to design and implement
     - Portal display complexity: the fee summary must present multiple fee items, potentially with different models, in a way that is clear to the client
     - Edge cases: what if one fee item is RETAINER and another is also RETAINER? Two retainer agreements for the same project? What about a RETAINER + FIXED where the fixed milestones overlap with retainer periods?
     - Testing matrix explodes: 3 fee models × N items × with/without milestones × with/without template × ...

3. **Fee model as a separate entity (FeeConfiguration)** -- Extract fee configuration into a standalone `FeeConfiguration` entity that can be shared across proposals, used as a template, or versioned independently. The proposal references a fee configuration rather than embedding fee fields.
   - Pros:
     - Reusable fee configurations: "Our standard hourly engagement terms" can be referenced by multiple proposals
     - Clean separation of proposal content (scope of work) from fee terms
     - Fee configuration could be versioned independently
   - Cons:
     - Over-abstraction: fee configuration is rarely reused across proposals — each engagement has unique amounts, milestones, and terms
     - Adds an entity and a join table for a concept that is naturally embedded in the proposal
     - Complicates the proposal creation UX: users must create or select a fee configuration before or during proposal creation
     - No existing parallel in the codebase — invoices embed their line items, retainers embed their terms, budgets embed their amounts
     - Premature generalization without evidence of reuse demand

**Decision**: Option 1 -- Single fee model per proposal.

**Rationale**:

The single-fee-model constraint dramatically simplifies every layer of the system: entity model, orchestration logic, validation rules, UI design, portal display, and testing. The proposal entity has typed columns for each fee model's configuration, and the orchestration service switches on a single enum value to determine the billing setup path. This produces clean, auditable, testable code.

The practical impact of the constraint is minimal. Professional services engagements are typically billed under one primary model: an audit is fixed-fee, advisory work is hourly, ongoing support is a retainer. The cases where a single engagement genuinely requires mixed billing are uncommon and can be handled through two mechanisms:

1. **Two proposals for the same customer**: Create a FIXED proposal for the audit deliverable and a separate HOURLY proposal for advisory hours. Each has its own acceptance flow, its own project (or both can reference the same project via manual configuration post-acceptance). This is explicit, auditable, and matches how many firms already separate engagement letters for different scopes.

2. **Manual post-acceptance configuration**: Accept the proposal with the primary fee model, then manually configure additional billing on the created project (add rate cards for hourly billing, create additional invoices, set up a retainer). The proposal handles 80% of the setup; the remaining 20% is manual.

Option 2 (mixed billing with fee items) was rejected because it introduces combinatorial complexity in orchestration, validation, and UI that is disproportionate to the frequency of mixed-billing engagements. The testing matrix alone — every combination of fee models, milestone schedules, and template configurations — would triple the test count. For v1, simplicity and reliability are more valuable than covering edge-case billing arrangements.

Option 3 (separate fee entity) was rejected as premature abstraction. Fee configurations are not reused across proposals in practice — each proposal has unique amounts, terms, and milestones specific to that client engagement. Extracting fee configuration into a separate entity adds indirection without reducing complexity.

Mixed billing can be revisited in a future phase if user demand materializes. The migration path is clear: add a `ProposalFeeItem` entity alongside the existing fields, deprecate the inline fee columns, and update the orchestration service to iterate over fee items. The single-model architecture does not create technical debt that blocks this evolution.

**Consequences**:

- `Proposal.feeModel` is a single `FeeModel` enum value (FIXED, HOURLY, RETAINER) — not a collection
- Fee configuration fields are flat columns on the `Proposal` entity, not a separate table
- Only fields relevant to the selected fee model are populated; others are NULL
- Validation enforces model-specific rules: FIXED requires `fixedFeeAmount > 0`, RETAINER requires `retainerAmount > 0`, HOURLY has no amount requirement
- Acceptance orchestration uses a `switch(proposal.getFeeModel())` to determine the billing setup path
- Mixed billing is handled via separate proposals or manual post-acceptance configuration
- Future mixed-billing support can be added via a `ProposalFeeItem` entity without breaking existing proposals
- Related: [ADR-124](ADR-124-proposal-storage-model.md), [ADR-125](ADR-125-acceptance-orchestration-transaction-boundary.md), [ADR-126](ADR-126-milestone-invoice-creation-strategy.md)
