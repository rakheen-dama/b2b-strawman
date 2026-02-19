# ADR-073: Standard Billing Rate for Overage

**Status**: Accepted
**Date**: 2026-02-19

**Context**:

Hour bank retainers allocate a fixed number of hours per period at a base fee. When consumed hours exceed the allocation, the excess is "overage" and must be billed separately. The question is how to determine the per-hour rate for overage billing.

The platform already has a three-level billing rate hierarchy (Phase 8, [ADR-039](ADR-039-rate-resolution-hierarchy.md)): org default → project override → customer override. This hierarchy determines what rate applies when billing time for a given customer. Retainer overage is conceptually the same — unbilled time beyond the retainer allocation for a specific customer.

Some enterprise billing platforms offer configurable overage rates per retainer (e.g., 1.5x standard rate for overage, or a specific $/hr overage rate). This adds a dedicated rate field to the retainer agreement.

**Options Considered**:

1. **Use existing billing rate hierarchy (chosen)** — Overage hours are billed at the customer's effective billing rate from the existing rate card system. No new rate configuration on the retainer.
   - Pros: Single source of truth for rates — change the customer's rate in one place, overage reflects it; zero additional configuration on retainer creation; consistent with how the platform already bills time (Phase 10 invoice generation uses the same hierarchy); no rate synchronization issues between retainer overage and ad-hoc billing.
   - Cons: Cannot charge a premium for overage (e.g., 1.5x rate); cannot offer a discounted overage rate as a client incentive; overage rate changes when the base rate changes (may surprise clients if not communicated).

2. **Configurable overage rate per retainer** — Add `overage_rate` (DECIMAL) to RetainerAgreement. When set, overage uses this rate instead of the hierarchy.
   - Pros: Full control over overage pricing per retainer; can implement premium overage (1.5x, 2x) as a deterrent; rate is locked to the agreement terms, not affected by hierarchy changes.
   - Cons: Another rate to manage per retainer — increases configuration surface; must decide precedence when both overage rate and hierarchy rate exist; rate drift — overage rate may become stale if not updated when base rates change; confusing UX if some retainers use custom overage rates and others use hierarchy rates.

3. **Overage multiplier** — Add `overage_multiplier` (DECIMAL, default 1.0) to RetainerAgreement. Overage rate = hierarchy rate × multiplier.
   - Pros: Flexible — covers premium (1.5x), standard (1.0x), and discounted (0.8x) overage; still anchored to the hierarchy rate, so base rate changes flow through; single field, simple to understand.
   - Cons: Multiplier of 1.0 is the common case — adds a field that's almost always default; still requires explaining the multiplier concept in the UI; edge case: what if multiplier is 0? (free overage? needs validation); adds complexity to invoice line calculation.

**Decision**: Option 1 — use the existing billing rate hierarchy for overage.

**Rationale**:

The target users are small-to-medium professional services firms (accounting firms, consultancies). In this market segment, overage is almost universally billed at the standard hourly rate. The retainer's value proposition is predictable pricing (X hours for $Y), not discounted rates — the per-hour rate is the same whether inside or outside the retainer allocation.

Premium overage pricing (1.5x, 2x) is a pattern seen in large enterprise contracts and telecom-style agreements, not in professional services retainers. Adding this configuration creates a decision point during retainer creation that most users would leave at default, while adding code paths for rate resolution, invoice generation, and UI explanation.

The existing billing rate hierarchy already handles per-customer rate differentiation. If Customer A should be billed at $200/hr for overage and Customer B at $150/hr, those rates are already configured in the rate card system. The retainer doesn't need to duplicate this.

The rate is resolved at period close time (not at time entry creation), which means current rates always apply. This is intentional and matches professional services billing practice — firms bill at their current published rates, not historical rates from when the retainer was signed.

**Consequences**:

- Positive:
  - Zero additional rate configuration on retainers — simpler creation and management
  - Single source of truth for billing rates across the platform
  - Rate changes automatically apply to future overage calculations
  - Consistent behavior between retainer overage billing and ad-hoc time billing

- Negative:
  - Cannot charge a premium for overage hours (acceptable for target market; can be added as `overage_multiplier` in a future enhancement if customer demand emerges)
  - Overage rate is implicitly tied to the rate hierarchy — if an admin changes a customer's rate mid-period, the new rate applies to the entire period's overage at close time (this is by design but should be documented in the UI)
  - No rate lock for long-term retainer agreements — the effective overage rate may change over the retainer's lifetime (mitigated by the fact that professional services firms regularly review and update their rate cards)
