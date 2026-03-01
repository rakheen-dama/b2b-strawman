# ADR-128: Proposal Numbering Strategy

**Status**: Accepted

**Context**:

Proposals need human-readable, sequential identifiers for reference in conversations, emails, and audit trails. When a firm member says "check proposal 42 for Acme Corp," the numbering scheme must produce unambiguous, sortable identifiers. The system already has a numbering pattern for invoices: `InvoiceCounter` with a format of `INV-{NNNN}`. The question is whether proposals should follow the same pattern, use date-based numbering, or support configurable prefixes.

The numbering must be unique within a tenant (not globally) because each tenant has its own schema with its own counter. Gaps in the sequence are acceptable (e.g., if a DRAFT proposal is deleted, its number is not reused). The format must be URL-safe and display cleanly in tables, emails, and printed documents.

**Options Considered**:

1. **Sequential counter with fixed prefix (PROP-NNNN)** -- A `ProposalCounter` entity (one row per tenant) tracks the next sequential number. On proposal creation, the counter is atomically incremented (`UPDATE proposal_counters SET next_number = next_number + 1 RETURNING next_number - 1`), and the number is formatted as `PROP-{NNNN}` (zero-padded to 4 digits, grows beyond 4 if needed). Follows the `InvoiceCounter` pattern exactly.
   - Pros:
     - Proven pattern: `InvoiceCounter` uses exactly this approach and has been in production since Phase 10
     - Simple and predictable: sequential numbers are easy to sort, reference, and communicate verbally
     - Atomic increment prevents duplicates even under concurrent proposal creation
     - Compact format: `PROP-0001` is shorter than date-based alternatives
     - Gap tolerance: deleted DRAFTs leave gaps, which is expected and non-confusing for users
   - Cons:
     - Fixed prefix (`PROP-`) cannot be customized per org — firms that prefer "Q-" for "quote" or "P-" for "proposal" must accept the default
     - Sequential numbers can reveal business volume (PROP-0500 implies ~500 proposals created) — minor information leak to portal contacts
     - No date information embedded in the number

2. **Date-based numbering (PROP-2026-0001)** -- Combine a year (or year-month) prefix with a sequential counter per period. Each year/month resets the counter. Format: `PROP-{YYYY}-{NNNN}` or `PROP-{YYYYMM}-{NNN}`.
   - Pros:
     - Embeds temporal context: `PROP-2026-0042` tells you the proposal was created in 2026
     - Counter resets periodically, keeping numbers small within each period
     - Familiar to firms that use date-based document numbering
   - Cons:
     - Counter management is more complex: need to track the current period and reset on period boundaries
     - Period boundary race conditions: proposals created near midnight on December 31 could get inconsistent year prefixes
     - Longer format: `PROP-2026-0001` vs. `PROP-0001`
     - If a firm creates few proposals per month, the counter resets are unnecessary overhead
     - Unlike invoices (which have regulatory requirements for sequential numbering in some jurisdictions), proposals have no legal numbering requirements

3. **Configurable prefix with sequential counter** -- Same sequential counter as Option 1, but the prefix is configurable in `OrgSettings` (default: "PROP"). The firm can change it to "Q", "PROP", "QUOTE", etc. Format: `{prefix}-{NNNN}`.
   - Pros:
     - Firms can customize to match their terminology and branding
     - Otherwise identical to Option 1 in implementation
   - Cons:
     - Adds a settings column and UI for a cosmetic preference — complexity for minimal value
     - Prefix changes mid-stream create inconsistency: PROP-0001 through PROP-0050, then suddenly Q-0051
     - Prefix validation needed: must be alphanumeric, reasonable length, URL-safe
     - Not worth the implementation cost in v1 — can be added later as an `OrgSettings` enhancement without migration

**Decision**: Option 1 -- Sequential counter with fixed prefix (PROP-NNNN).

**Rationale**:

The `InvoiceCounter` pattern is proven, simple, and well-tested. Reusing the same approach for proposals provides consistency across the platform and avoids inventing a new numbering scheme. Sequential numbering with a fixed prefix is the most common approach in professional services software — proposals are typically identified by sequential numbers, not dates, because firms reference proposals by number in conversations and correspondence.

The `PROP-` prefix is clear and unambiguous. While some firms might prefer "Q" for "quote," the terminology in DocTeams consistently uses "proposal" throughout the UI, API, and documentation. A configurable prefix (Option 3) adds implementation complexity — a new settings column, a settings UI section, prefix validation, and migration considerations — for a purely cosmetic feature that benefits only firms with strong branding preferences. This can be added in a future phase as an `OrgSettings` enhancement without any database migration (the `proposal_number` column is `VARCHAR(20)`, which accommodates any reasonable prefix).

Date-based numbering (Option 2) was rejected because proposals do not have the regulatory requirements that make date-based numbering valuable for invoices in some jurisdictions. The temporal context embedded in a date prefix is available from the `createdAt` timestamp and adds visual noise to the identifier.

The zero-padding to 4 digits (`PROP-0001` through `PROP-9999`) is a display convention, not a database constraint. When a firm exceeds 9999 proposals, the format naturally grows to `PROP-10000`. The `VARCHAR(20)` column accommodates this.

**Consequences**:

- `ProposalCounter` entity with a single row per tenant schema, `nextNumber` starting at 1
- `ProposalNumberService` atomically increments the counter and formats the number as `PROP-{NNNN}`
- The counter is incremented at proposal creation time (not at send time), so DRAFT proposals consume numbers
- Deleted DRAFT proposals leave gaps in the sequence — this is expected and documented
- Proposal numbers are unique within a tenant, enforced by a UNIQUE constraint on `proposals.proposal_number`
- Future enhancement: configurable prefix via `OrgSettings.proposalPrefix` can be added without migration (the numbering format is centralized in `ProposalNumberService`)
- Consistent with `InvoiceCounter` pattern (same implementation approach, same zero-padding convention)
- Related: [ADR-124](ADR-124-proposal-storage-model.md)
