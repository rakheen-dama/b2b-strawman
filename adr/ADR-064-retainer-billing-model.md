# ADR-064: Retainer Billing Model -- Single Entity with Type Discriminator

**Status**: Accepted

**Context**: Phase 14 introduces retainer agreements that come in two flavours: fixed-fee retainers (a flat amount per billing period) and hour bank retainers (a base fee covering a set number of hours, with overage billing). The system needs to model both types while supporting shared lifecycle logic (DRAFT -> ACTIVE -> PAUSED -> EXPIRED / CANCELLED), shared billing job logic (period close, invoice generation), and a unified UI for management.

The key modelling question is whether to represent these as a single entity with a type discriminator or as separate entities (possibly using JPA inheritance).

**Options Considered**:

1. **Single entity with type discriminator** -- One `Retainer` entity with a `type` column (`FIXED_FEE` / `HOUR_BANK`). Hour-bank-specific fields (`allocated_hours`, `overage_rate`, `rollover_policy`) are nullable and validated at the application layer based on type.
   - Pros: Single table, single repository, single service. Queries are simple (`SELECT * FROM retainers WHERE ...`). UI code is unified. The billing job iterates one table. No JPA inheritance complexity.
   - Cons: Nullable fields that only apply to one type. Application-layer validation required to enforce type-specific constraints. Schema allows invalid state (e.g., FIXED_FEE with allocated_hours set) unless CHECK constraints are added.

2. **JPA inheritance (SINGLE_TABLE or JOINED)** -- Abstract `Retainer` superclass with `FixedFeeRetainer` and `HourBankRetainer` subclasses. JPA `@Inheritance` manages the discriminator.
   - Pros: Type safety at the Java level. Non-nullable fields per subclass. Compiler catches misuse.
   - Cons: JPA inheritance adds complexity (discriminator columns, query polymorphism issues with `@Filter`, Hibernate 7 inheritance + multitenancy edge cases). Two repository types needed or polymorphic repository with casting. UI and billing job must handle polymorphism. Established codebase pattern uses flat entities with no JPA inheritance anywhere.

3. **Separate entities, no inheritance** -- Two independent entities: `FixedFeeRetainer` and `HourBankRetainer`, each with its own table.
   - Pros: Clean separation. No nullable fields.
   - Cons: Duplicated lifecycle logic, duplicated billing job logic, duplicated UI components. Queries across "all retainers" require UNION. Dashboard MRR computation spans two tables. Significantly more code for minimal type-safety benefit.

**Decision**: Option 1 -- Single entity with type discriminator.

**Rationale**: The two retainer types share ~90% of their fields and 100% of their lifecycle logic. The only type-specific fields are three nullable columns (`allocated_hours`, `overage_rate` -- both DECIMAL, and `rollover_policy` -- VARCHAR with a default). This is a small price for the dramatic simplification in queries, service logic, UI code, and billing job implementation.

The established codebase pattern uses flat entities with string-typed enums (e.g., `Invoice.status`, `Customer.lifecycle_status`). No entity in the codebase uses JPA inheritance. Introducing inheritance for this use case would be an architectural outlier.

Application-layer validation (in `RetainerService`) enforces type-specific constraints: `allocated_hours` and `overage_rate` are required when type is HOUR_BANK, rejected when type is FIXED_FEE. This is consistent with how the codebase handles other conditional validation (e.g., invoice fields that differ by status).

**Consequences**:
- `Retainer` entity has three nullable fields (`allocated_hours`, `overage_rate`, `rollover_policy`) that only apply to HOUR_BANK type.
- `RetainerService.create()` and `RetainerService.update()` must validate type-specific field presence.
- The `retainers` table allows technically-invalid state (FIXED_FEE with allocated_hours) at the DB level. Adding a CHECK constraint like `CHECK (type != 'FIXED_FEE' OR allocated_hours IS NULL)` is an option but adds migration complexity for minimal benefit.
- All queries, the billing job, the UI, and the notification/audit infrastructure operate on a single entity type -- no polymorphic dispatching needed.
- Future retainer types (e.g., "project-based retainer") can be added by extending the `type` enum and adding nullable fields, following the same pattern.
