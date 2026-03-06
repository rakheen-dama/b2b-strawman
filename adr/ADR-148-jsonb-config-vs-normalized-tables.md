# ADR-148: JSONB Config vs Normalized Tables for Automation Configuration

**Status**: Accepted
**Date**: 2026-03-06
**Phase**: 37 (Workflow Automations v1)

## Context

Automation rules have three configuration surfaces that vary by type: trigger configuration (what specifically about the event to match), conditions (field-based predicates to filter), and action configuration (what to do and how). Each trigger type has different config fields — status change triggers need `fromStatus` and `toStatus`, budget threshold triggers need `thresholdPercent`, simple triggers need no config. Similarly, each action type has unique config — `CREATE_TASK` needs `taskName`, `assignTo`, etc., while `SEND_EMAIL` needs `recipientType`, `subject`, `body`.

The question is how to store this heterogeneous, type-dependent configuration in the database. The codebase already uses JSONB extensively — 18 entities have JSONB columns for flexible data (audit event details, custom field values, saved view filters, report parameters, etc.).

## Options Considered

1. **JSONB columns with application-layer validation** — Store `trigger_config`, `conditions`, and `action_config` as JSONB columns. Validate structure at the application layer using Java sealed classes that are deserialized from the JSONB on read and serialized to JSONB on write.
   - Pros:
     - Schema flexibility — adding a new trigger type or action type requires no migration, only a new sealed class permit
     - Simple entity model — `AutomationRule` has 3 JSONB columns, `AutomationAction` has 1
     - Consistent with established codebase patterns (18 entities already use JSONB)
     - Sealed classes provide compile-time type safety — the Java type system enforces that each trigger/action type has the correct fields
     - Jackson polymorphic deserialization handles the mapping cleanly
     - JSONB is queryable in PostgreSQL (`trigger_config->>'toStatus' = 'COMPLETED'`) if needed
     - No joins required to load a rule with its full configuration
   - Cons:
     - No database-level referential integrity on config fields (e.g., `specificMemberId` in action config is not a real FK)
     - Schema changes to config structure require application-level migration logic (not Flyway)
     - PostgreSQL JSONB indexing is less efficient than B-tree indexes on typed columns
     - Validation errors surface at the application layer, not the database layer

2. **Fully normalized relational tables** — Separate tables for each config type: `trigger_status_change_configs`, `trigger_budget_threshold_configs`, `action_create_task_configs`, `action_send_notification_configs`, etc.
   - Pros:
     - Full database-level type safety and referential integrity
     - Standard indexing on typed columns
     - Clear schema documentation — the table structure IS the config documentation
     - Database-level constraints (NOT NULL, CHECK, FK) enforce validity
   - Cons:
     - Explosion of tables — 3 trigger config tables + 6 action config tables = 9 additional tables minimum
     - Adding a new trigger or action type requires a new migration + new table + new entity + new repository
     - Loading a rule requires joins across multiple config tables (polymorphic query pattern)
     - Condition storage is particularly awkward — variable-length list of heterogeneous predicates maps poorly to normalized tables
     - Maintenance burden grows linearly with the number of supported types
     - Inconsistent with existing codebase patterns (no precedent for this level of normalization for config data)

3. **Entity-Attribute-Value (EAV) pattern** — A single `automation_config_values` table with columns `(rule_id, config_scope, key, value_type, value_text, value_number, value_uuid)`.
   - Pros:
     - Single table for all config types
     - Extensible without schema changes
   - Cons:
     - Lose all type safety — values are stringly-typed
     - Complex queries to reconstruct a config object (pivot/aggregate)
     - No compile-time validation possible
     - Performance degrades with config complexity
     - Widely considered an anti-pattern for application configuration
     - No codebase precedent

## Decision

Option 1 — JSONB columns with sealed class validation at the application layer.

## Rationale

JSONB is the natural fit for type-discriminated configuration data. The automation system has 8 trigger types with 3 config shapes and 6 action types with 6 config shapes. Normalizing these into relational tables would add 9+ tables, 9+ entities, 9+ repositories, and polymorphic join logic — all for data that is always loaded and saved as a unit with its parent entity. The additional complexity is not justified by the marginal benefit of database-level constraints on config fields.

The sealed class hierarchy provides the type safety that JSONB alone would lack:

```java
sealed interface TriggerConfig permits
    StatusChangeTriggerConfig, BudgetThresholdTriggerConfig, EmptyTriggerConfig {}
```

When a `TriggerConfig` is deserialized from JSONB, the sealed class enforces that the fields are correct for the trigger type. If a `StatusChangeTriggerConfig` is missing `toStatus`, deserialization fails with a clear error. This is equivalent to a NOT NULL constraint in a normalized table, enforced at the application layer instead of the database layer.

The codebase has strong precedent for this pattern. `AuditEvent.details`, `FieldDefinition.options`, `SavedView.filters`, `ReportDefinition.parameterSchema`, `Proposal.contentJson`, and many others store structured data as JSONB. The automation config follows the same pattern with the added benefit of sealed class validation — a stricter approach than the `Map<String, Object>` used by most existing JSONB columns.

The JSONB approach also keeps the migration simple: one `V56` migration creates 4 tables. The normalized approach would require 13+ tables with complex FK relationships.

## Consequences

- `AutomationRule` has `trigger_config JSONB` and `conditions JSONB` columns
- `AutomationAction` has `action_config JSONB` column
- Sealed class hierarchies (`TriggerConfig`, `ActionConfig`) provide compile-time type safety
- Jackson polymorphic deserialization uses `triggerType`/`actionType` as discriminator
- Adding a new trigger or action type requires: new enum value, new sealed class permit, new executor — no migration
- Config field references (e.g., `specificMemberId`) are not FK-validated at the database level
- Consistent with existing JSONB usage patterns across 18+ entities
