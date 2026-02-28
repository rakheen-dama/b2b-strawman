# ADR-118: Invoice Line Type Extension

**Status**: Accepted

**Context**:

The existing `InvoiceLine` entity (Phase 10, ADR-049) uses implicit typing: the "type" of a line is determined by which foreign key is populated. If `timeEntryId` is set, it is a time line; if `retainerPeriodId` is set, it is a retainer line; if both are null, it is a manual line. Phase 30 adds expense billing, which introduces a fourth type via `expenseId`. With four mutually exclusive FK columns and no explicit discriminator, the implicit typing pattern becomes fragile.

Querying by line type currently requires checking multiple columns: `WHERE time_entry_id IS NOT NULL` for time lines, `WHERE retainer_period_id IS NOT NULL` for retainer lines, and `WHERE time_entry_id IS NULL AND retainer_period_id IS NULL` for manual lines. Adding `expense_id` makes the manual line condition even more brittle: `WHERE time_entry_id IS NULL AND retainer_period_id IS NULL AND expense_id IS NULL`. This affects reporting queries, PDF rendering logic (which formats lines differently by type), and the invoice detail UI.

**Options Considered**:

1. **Continue implicit FK-based typing (add `expense_id`, derive type from which FK is set)** -- Add `expense_id` as a fourth nullable FK column. Line type is inferred at read time by inspecting which FK is non-null.
   - Pros:
     - No schema change beyond adding the new FK column -- minimal migration
     - Consistent with the existing (albeit imperfect) pattern
     - No data backfill needed for existing rows
   - Cons:
     - Four-way null checking is fragile and error-prone in queries and application logic
     - Manual line detection becomes a four-column IS NULL check that must be updated every time a new line type is added
     - Reporting queries (e.g., "total billed expenses") require `WHERE expense_id IS NOT NULL` rather than a clean `WHERE line_type = 'EXPENSE'`
     - PDF rendering logic must inspect multiple FKs to determine formatting -- a switch on an explicit type is cleaner
     - No database-level constraint prevents multiple FKs from being set simultaneously (e.g., both `timeEntryId` and `expenseId` non-null)

2. **Add explicit `line_type` VARCHAR column as discriminator** -- Add a `line_type` column (`VARCHAR(20)`, NOT NULL, default `'TIME'`) alongside the existing FK columns. Values: `TIME`, `EXPENSE`, `RETAINER`, `MANUAL`. Backfill existing rows based on current FK values.
   - Pros:
     - Clean, explicit typing: `WHERE line_type = 'EXPENSE'` is readable and index-friendly
     - Simplifies reporting, PDF rendering, and UI grouping logic -- switch/case on a single column
     - Self-documenting: the column makes the line type visible in raw data inspection
     - Default of `'TIME'` ensures backward compatibility with existing rows (most lines are time-based)
     - Can add a CHECK constraint to enforce valid values at the database level
     - Future line types (e.g., `SUBSCRIPTION`, `FIXED_FEE`) require only a new enum value, not new FK inspection logic
   - Cons:
     - Redundant with the FK columns -- the type can technically be derived from them, creating a potential consistency risk
     - Requires a data migration to backfill existing rows (though the logic is deterministic and simple)
     - Two sources of truth: `line_type` and the FK columns must agree. Application logic must set both correctly.
     - Adds one column to every invoice line row (negligible storage impact)

3. **JPA @Inheritance with separate tables per line type** -- Use JPA's table-per-class or joined-table inheritance to create `TimeInvoiceLine`, `ExpenseInvoiceLine`, `RetainerInvoiceLine`, and `ManualInvoiceLine` as separate entities/tables.
   - Pros:
     - Strongest type safety: each line type is a distinct Java class with only its relevant fields
     - No nullable FK columns -- each subclass has only the FK it needs
     - JPA handles the discriminator automatically
   - Cons:
     - Massive refactoring: the existing `InvoiceLine` entity, repository, service, and all consumers must be restructured
     - JPA inheritance strategies have well-known performance pitfalls: JOINED requires multi-table joins for every query; TABLE_PER_CLASS prevents polymorphic queries without UNION ALL
     - Breaks the existing migration: the `invoice_lines` table would need to be split into multiple tables or gain a JPA-managed discriminator column (which is essentially Option 2 with more ceremony)
     - The existing codebase uses no JPA inheritance anywhere -- introducing it for a single entity creates an inconsistency
     - Invoice total calculation must aggregate across subclass tables, adding query complexity

**Decision**: Option 2 -- Add explicit `line_type` VARCHAR column as discriminator.

**Rationale**:

With four line types (TIME, EXPENSE, RETAINER, MANUAL), implicit FK-based typing crosses the threshold from "pragmatic shortcut" to "maintenance burden." Every query that needs to filter or group by line type must inspect multiple nullable columns, and the condition for identifying manual lines grows with each new FK column added. An explicit discriminator column makes the type queryable, indexable, and self-documenting.

The `line_type` column follows the established pattern in this codebase: Task status, Task priority, and ExpenseCategory are all stored as VARCHAR columns with CHECK constraints and validated against Java enums. This is consistent with the project's preference for string-stored enums over Postgres enum types or JPA inheritance.

The backfill migration is deterministic and safe: rows with `time_entry_id IS NOT NULL` get `'TIME'`, rows with `retainer_period_id IS NOT NULL` get `'RETAINER'`, and all remaining rows get `'MANUAL'`. No `expense_id` rows exist yet (expenses are new in Phase 30), so there is no ambiguity. The default of `'TIME'` ensures that any rows not explicitly backfilled (edge case) are categorized as the most common type.

The redundancy between `line_type` and the FK columns is an acceptable trade-off. The application layer sets both atomically in the service method that creates invoice lines. A database-level CHECK constraint can optionally enforce consistency (e.g., `CHECK (line_type != 'EXPENSE' OR expense_id IS NOT NULL)`), though this is not strictly necessary given that all line creation goes through the service layer.

JPA inheritance (Option 3) was rejected because the existing codebase uses no inheritance mapping, the refactoring cost is disproportionate to the benefit, and JPA inheritance strategies introduce performance and query complexity that a simple VARCHAR column avoids.

**Consequences**:

- `InvoiceLine` entity gains `lineType` field (`VARCHAR(20)`, NOT NULL, default `'TIME'`)
- Flyway migration adds the column with a DEFAULT clause, then backfills: `UPDATE invoice_lines SET line_type = 'RETAINER' WHERE retainer_period_id IS NOT NULL; UPDATE invoice_lines SET line_type = 'MANUAL' WHERE time_entry_id IS NULL AND retainer_period_id IS NULL`
- New expense invoice lines are created with `line_type = 'EXPENSE'` and `expense_id` set
- All existing invoice line creation code is updated to set `lineType` explicitly (TIME, RETAINER, MANUAL)
- Reporting queries simplified: `SELECT SUM(amount) FROM invoice_lines WHERE line_type = 'EXPENSE'` replaces multi-column null checks
- PDF rendering logic switches on `lineType` instead of inspecting FK columns
- `InvoiceLineType` Java enum: `TIME`, `EXPENSE`, `RETAINER`, `MANUAL` -- used for validation and API serialization
- Minor consistency risk: `line_type` and the FK columns could theoretically disagree if set incorrectly. Mitigated by centralizing line creation in `InvoiceService` and optional CHECK constraints.
- Related: [ADR-049](ADR-049-line-item-granularity.md) (original invoice line design), [ADR-115](ADR-115-expense-markup-model.md) (expense markup affects billable amount on expense lines), [ADR-114](ADR-114-expense-billing-status-derivation.md) (expense billing status derivation)
