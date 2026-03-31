# ADR-211: Tariff Rate Integration Approach -- Separate TariffItem Entity

**Status**: Proposed
**Date**: 2026-03-31
**Phase**: 55 (Legal Foundations: Court Calendar, Conflict Check & LSSA Tariff)

## Context

Phase 55 introduces LSSA tariff rates for legal tenants. The platform already has a rate card system (Phase 8): `BillingRate` with a 3-level hierarchy (org -> project -> customer), `CostRate` for internal cost tracking, and rate snapshots on time entries. The existing system is built around hourly billing -- an amount per hour worked, resolved at time entry creation and snapshotted for immutability.

LSSA tariff rates are fundamentally different: they are fixed amounts per activity (e.g., "Drawing of summons -- R1,250"), not per hour worked. A single invoice line can reference a tariff item with a quantity (e.g., "3 pages of affidavit at R195/folio"), and the amount is `quantity x tariff_amount`, not `hours x hourly_rate`.

The question is how tariff rates should integrate with the existing billing pipeline, specifically with the `InvoiceLine` entity that currently computes amounts from `BillingRate` snapshots.

## Options Considered

### Option A: Extend BillingRate with Tariff Fields

Add tariff-related columns to the existing `BillingRate` entity: `tariff_item_number`, `tariff_unit` (PER_ITEM, PER_FOLIO, etc.), `rate_type` discriminator (HOURLY, TARIFF). Modify rate resolution logic to handle both types.

- **Pros:** Single rate entity. Existing rate hierarchy (org -> project -> customer) applies to tariff rates. Rate management UI is reused.
- **Cons:** `BillingRate` becomes a dual-purpose entity with many nullable columns (tariff fields null for hourly rates, hourly fields irrelevant for tariff rates). Rate resolution logic becomes conditional on rate type. Rate snapshots on time entries would need to handle tariff semantics. Accounting tenants would see tariff-related columns on their BillingRate rows. The 3-level hierarchy (org -> project -> customer) does not apply to tariff rates -- tariffs are published schedules, not negotiated per-client rates.

### Option B: Separate TariffItem Entity with Optional FK on InvoiceLine (Selected)

Create a new `TariffSchedule` and `TariffItem` entity pair, completely separate from `BillingRate`. Add an optional `tariff_item_id` FK column to `InvoiceLine`. When creating a tariff-based invoice line, the amount comes from the tariff item, not from a BillingRate. The existing hourly billing pipeline is completely untouched.

- **Pros:** Clean separation of concerns. Hourly billing and tariff billing are orthogonal -- they do not share code, entities, or UI. Accounting tenants never encounter tariff entities. The tariff system can evolve independently (new unit types, schedule versioning, custom overrides) without affecting hourly billing. The `InvoiceLine` extension is minimal: one nullable FK column and one enum value.
- **Cons:** Two separate "rate" systems in the codebase. Invoice creation logic has a conditional branch for tariff lines. If a future requirement needs to blend tariff and hourly rates in a single calculation (unlikely), the separation would make this harder.

### Option C: Line Item Type Discriminator

Add a `line_source_type` discriminator to `InvoiceLine` that controls how the amount is computed: TIME (hours x rate), EXPENSE (direct amount), TARIFF (tariff_item x quantity). No separate tariff entity -- tariff data is embedded directly in the line item.

- **Pros:** No new entity. All billing logic is in InvoiceLine.
- **Cons:** Tariff item details (item number, section, schedule name) would need to be denormalized into InvoiceLine columns. No centralized tariff schedule management. No way to browse or search tariff items. Tariff updates (new LSSA gazette) would require finding and updating individual invoice lines. Loses the audit trail of "this line was generated from tariff item 2(a) in the LSSA 2024/2025 schedule."

## Decision

**Option B -- Separate TariffItem entity with optional FK on InvoiceLine.**

## Rationale

1. **Loose coupling preserves multi-vertical isolation:** The hourly billing pipeline (`BillingRate` -> rate resolution -> time entry snapshot -> InvoiceLine) is a core platform capability used by all verticals. Modifying it to accommodate tariff semantics creates coupling between verticals. A legal-specific change to tariff handling could break accounting billing. The separate entity approach means tariff changes only touch `verticals/legal/tariff/` and the InvoiceLine FK -- the blast radius is minimal.

2. **Different data models:** Tariff rates belong to a schedule (grouped by court level and category), have item numbers, sections, and unit types (PER_FOLIO, PER_QUARTER_HOUR). BillingRates belong to a hierarchy (org -> project -> customer), have effective dates, and are always per-hour. These are structurally different -- forcing them into one entity produces a confusing model.

3. **Read-only vs. editable:** LSSA tariff schedules are seeded as read-only (`is_system = true`). Firms can clone them to create editable copies. This clone-and-edit pattern does not exist in the BillingRate system and would add complexity if forced into it.

4. **Minimal InvoiceLine change:** Adding a nullable `tariff_item_id` FK and a `TARIFF` line type is a small, additive change. Existing invoice lines are unaffected (the FK is null for all non-tariff lines). The invoice total calculation (`SUM(line_amount)`) does not change -- tariff lines contribute their amount to the total the same way manual or time-based lines do.

5. **Traceability:** The FK from `InvoiceLine` to `TariffItem` provides an audit trail: "this invoice line was generated from tariff item 2(a) in the LSSA 2024/2025 High Court schedule." This is important for legal billing disputes and taxation of costs.

## Consequences

- **Positive:** Hourly billing pipeline is completely untouched -- zero regression risk for accounting tenants
- **Positive:** Tariff schedule management (browse, search, clone) is a self-contained feature
- **Positive:** InvoiceLine extension is minimal and additive
- **Positive:** Audit trail from invoice line to tariff item is preserved
- **Negative:** Two separate "rate" concepts in the codebase (BillingRate for hourly, TariffItem for per-activity)
- **Negative:** Invoice creation has a conditional branch for tariff lines (module-gated, so only active for legal tenants)
- **Mitigations:** Module gating ensures accounting tenants never encounter tariff-related code paths. The tariff package is in `verticals/legal/tariff/`, physically separated from the billing rate package.
