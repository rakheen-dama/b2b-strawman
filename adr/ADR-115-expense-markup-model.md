# ADR-115: Expense Markup Model

**Status**: Accepted

**Context**:

Professional services firms routinely mark up expenses when billing clients -- typically 15-20% to cover administrative overhead associated with procuring and managing disbursements. Phase 30 introduces expense tracking with full billing integration, and the markup model determines how the billable amount (what the client pays) relates to the cost amount (what the firm actually spent).

The markup model must balance simplicity for firms that apply a uniform markup with flexibility for edge cases (e.g., a specific high-value expense negotiated at a different rate). It also interacts with the profitability calculation: the difference between billable amount and cost amount contributes to margin.

DocTeams already has a rate hierarchy pattern from Phase 8 (ADR-039): org-level default billing rates can be overridden at the project level, which can be overridden at the customer level. The question is whether expense markup needs the same level of granularity.

**Options Considered**:

1. **Per-expense markup only (no default)** -- Each expense has a `markupPercent` field that must be set explicitly. No org-level default.
   - Pros:
     - Simple data model -- one field, no inheritance logic
     - Every expense's markup is explicit and visible
   - Cons:
     - Tedious for firms that apply the same markup to all expenses -- every expense entry requires setting the markup
     - High error rate: users will forget to set markup, leading to expenses billed at cost
     - No way to change the "standard" markup without editing every future expense individually

2. **Org-level default markup only (no per-expense override)** -- A single `defaultExpenseMarkupPercent` on OrgSettings applies to all expenses. No per-expense override.
   - Pros:
     - Simplest configuration: set once, applies everywhere
     - Consistent markup across all expenses -- no surprises
     - Easy to audit: one number in org settings determines all markup
   - Cons:
     - No flexibility for edge cases: a negotiated zero-markup expense or a high-markup specialised procurement cannot be accommodated
     - Firms that bill some expenses at cost (e.g., exact filing fees) and mark up others (e.g., travel) cannot differentiate

3. **Org-level default + per-expense override** -- OrgSettings has a `defaultExpenseMarkupPercent` (nullable, null = no markup). Each expense has a nullable `markupPercent` field. If the per-expense field is set, it takes precedence; if null, the org default applies. Billable amount is computed at read time, not stored.
   - Pros:
     - Balances simplicity with flexibility: most expenses use the org default; edge cases override per-expense
     - Changing the org default retroactively affects all unbilled expenses that did not override -- correct business behavior
     - Computed billable amount (not stored) means org default changes are immediately reflected without data migration
     - Consistent with the rate card hierarchy concept from Phase 8, albeit simpler (two levels instead of three)
   - Cons:
     - Slightly more complex computation logic: must check per-expense first, then fall back to org default, then fall back to zero
     - Users must understand the precedence: a per-expense markup of `0.00` means "explicitly no markup", while null means "use the org default"
     - Read-time computation adds minor overhead to API responses (negligible for typical expense volumes)

4. **Tiered markup: org -> project -> expense** -- Three-level hierarchy mirroring the Phase 8 rate card pattern (ADR-039). OrgSettings has a default, each project can override it, and each expense can override the project-level value.
   - Pros:
     - Maximum flexibility: different projects can have different markup agreements
     - Fully consistent with the rate card hierarchy pattern from Phase 8
     - Supports complex client agreements (e.g., "Project A expenses at 20%, Project B expenses at cost")
   - Cons:
     - Over-engineering for v1: expense markup is simpler than billing rates -- most firms apply a uniform markup or none at all
     - Adds a column to the Project entity for a feature that may never be used
     - Three-level fallback logic is harder to explain to users and harder to debug
     - Rate cards justified three levels because billing rates vary significantly by project scope and skill level; expense markup does not have the same variability

**Decision**: Option 3 -- Org-level default + per-expense override.

**Rationale**:

The two-level model (org default + per-expense override) covers the vast majority of real-world scenarios. Most firms apply a uniform markup to all expenses, configured once in org settings. The occasional exception (a filing fee billed at exact cost, or a negotiated higher markup on subcontractor fees) is handled by setting the per-expense `markupPercent` field.

The billable amount is computed at read time rather than stored as a column. This is critical: if the org changes its default markup from 15% to 20%, all unbilled expenses should immediately reflect the new rate. Storing the billable amount would require a batch update on every default change, introducing a data consistency risk. The computation is trivial (`amount * (1 + effectiveMarkup / 100)`) and adds negligible overhead.

The tiered model (Option 4) was rejected because expense markup does not exhibit the same variability as billing rates. Phase 8's three-level rate hierarchy (ADR-039) was justified by the wide variation in billing rates across projects and skill levels. Expense markup is typically uniform across a firm -- the added complexity of a project-level override is not warranted for v1. If project-level markup becomes necessary, adding a `defaultExpenseMarkupPercent` column to the Project entity is a backward-compatible extension.

**Consequences**:

- `OrgSettings` gains a `defaultExpenseMarkupPercent` column (`NUMERIC(5,2)`, nullable, default null = no markup)
- `Expense` entity has a nullable `markupPercent` field -- null means "use org default", `0.00` means "explicitly no markup"
- Effective markup resolution: `expense.markupPercent != null ? expense.markupPercent : orgSettings.defaultExpenseMarkupPercent ?? BigDecimal.ZERO`
- `billableAmount` is computed at read time in `ExpenseService` and included in API response DTOs, but NOT stored as a column
- Changing the org default immediately affects all unbilled expenses that have no per-expense override -- no migration or batch update needed
- Profitability reports use the computed billable amount: margin = billableAmount - amount (expense markup contributes to profit)
- UI must clearly distinguish "no markup set (using org default)" from "markup explicitly set to 0%"
- Related: [ADR-039](ADR-039-rate-hierarchy.md) (rate card hierarchy -- similar pattern at a simpler scale), [ADR-114](ADR-114-expense-billing-status-derivation.md) (expense billing status derivation)
