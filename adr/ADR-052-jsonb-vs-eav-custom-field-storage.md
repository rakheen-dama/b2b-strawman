# ADR-052: JSONB vs. EAV for Custom Field Storage

**Status**: Accepted

**Context**: Phase 11 introduces custom fields — org-defined, typed fields on projects, tasks, and customers. Each entity can have zero to dozens of custom field values, with the set of fields varying per organization and per entity type. The storage strategy must support:

- Efficient reads: loading an entity and all its custom field values in a single query.
- Filtering: querying entities by custom field values (e.g., "all projects where court = high_court_gauteng").
- Schema flexibility: adding/removing field definitions without DDL migrations.
- Tenant isolation: custom field values must respect the existing `@Filter`/RLS isolation model.

The expected data volume is moderate — hundreds of field definitions per tenant, thousands to low tens of thousands of entities with custom field values. This is not a "millions of key-value pairs" scenario.

Key constraints:
- PostgreSQL is the only data store (no NoSQL databases).
- Custom fields apply to three existing entities (Project, Task, Customer) which already have established table schemas.
- Hibernate `@Filter` and PostgreSQL RLS are the primary isolation mechanisms — any storage approach must be compatible with both.
- Field definitions are relational (normal tables) — only field *values* need flexible storage.

**Options Considered**:

1. **JSONB column on entity table** — Add a `custom_fields JSONB` column to `projects`, `tasks`, and `customers`. Values are stored as a flat JSON object keyed by field definition slug. Queries use JSONB operators (`@>`, `->>`) backed by GIN indexes.
   - Pros:
     - **Single-query reads**: Entity + custom fields load in one row fetch. No joins.
     - **Simple writes**: Merge the JSONB object on update. No insert/delete/update of separate rows.
     - **Natural with Hibernate**: Maps directly to `Map<String, Object>` via `@JdbcTypeCode(SqlTypes.JSON)`. Existing `AuditEvent.details` uses this pattern.
     - **Tenant isolation is free**: The JSONB column is just another column on the entity table — existing `@Filter`/RLS policies apply without changes.
     - **GIN index handles containment queries**: `custom_fields @> '{"court": "high_court"}'` is covered by a single GIN index. No per-field index needed.
     - **Schema flexibility**: Adding a field definition doesn't change the table schema. The JSONB column accepts any key-value pairs.
   - Cons:
     - **No FK enforcement on values**: JSONB values can't reference other tables. Dropdown option validation is application-level, not database-level.
     - **Type coercion at query time**: Filtering by numeric range requires `(custom_fields ->> 'amount')::numeric > 100`, which bypasses GIN and requires a sequential scan (or expression index).
     - **Row size growth**: Large JSONB objects increase row size. TOAST compression mitigates this, but very large custom field sets (50+ fields with long text values) could impact scan performance.
     - **No individual field history**: Updating one custom field value replaces the entire JSONB object. Fine-grained change tracking requires application-level diffing.

2. **Entity-Attribute-Value (EAV) table** — A separate `custom_field_values` table with columns `(entity_type, entity_id, field_definition_id, value_text, value_number, value_date, value_boolean, value_json)`. One row per field value per entity.
   - Pros:
     - **Relational purity**: Each value is its own row with FK to `field_definitions`. Standard SQL for queries.
     - **Per-field indexing**: Can create indexes on specific value columns for frequently filtered fields.
     - **Granular history**: Each value change is a separate row operation — integrates with change-data-capture tools.
     - **Type-specific columns**: `value_number`, `value_date`, etc. enable proper type comparison without casting.
   - Cons:
     - **Join explosion**: Loading an entity with N custom fields requires a join that produces N rows, then pivoting. With 10 fields per entity and 20 entities per page, that's 200 extra rows per list query.
     - **Complex queries**: Filtering by two custom field values requires two self-joins on the EAV table. Each additional filter adds another join. Performance degrades with filter count.
     - **Tenant isolation overhead**: The EAV table needs its own `tenant_id`, `@Filter`, and RLS policy. Every custom field read goes through the filter — multiplied by the number of values per entity.
     - **Multiple type columns**: Most rows use only one of `value_text/number/date/boolean/json`. The rest are null. Wastes space and complicates the schema.
     - **Write complexity**: Creating an entity with 10 custom fields requires 10 INSERT statements into the EAV table, plus managing upserts on update.

3. **Hybrid: JSONB for storage, materialized EAV for indexing** — Store custom field values as JSONB on the entity table (Option 1), but maintain a materialized EAV table that extracts and indexes specific high-value fields for fast filtering. A trigger or service keeps the EAV table in sync.
   - Pros:
     - **Best of both**: Fast reads via JSONB, fast filtered queries via indexed EAV columns.
     - **Selective indexing**: Only frequently-filtered fields are materialized — not all fields.
   - Cons:
     - **Dual-write consistency**: The JSONB and EAV table can drift. Triggers add complexity; application-level sync has timing gaps.
     - **Schema management**: The materialized EAV table needs schema changes when field definitions change — defeating the purpose of dynamic fields.
     - **Over-engineering**: At the expected data volumes (thousands of entities, not millions), GIN indexes on JSONB handle the query load without materialization.
     - **Operational burden**: Two storage locations for the same data. Debugging discrepancies is painful.

**Decision**: JSONB column on entity table (Option 1).

**Rationale**: JSONB is the right trade-off at this project's scale. The expected data volume (hundreds of field definitions per tenant, thousands to tens of thousands of entities) is well within PostgreSQL's JSONB performance envelope. GIN indexes handle containment queries (`@>`) efficiently, and the few cases requiring range queries on numeric/date fields can use expression indexes if needed (e.g., `CREATE INDEX ON projects ((custom_fields ->> 'case_value')::numeric)` for a specific high-volume field).

The critical advantage is simplicity: a single-query read, no join explosion, and free tenant isolation. The EAV approach would add 100+ rows per list page load, with N self-joins for N filter criteria — complexity that's not justified by the data volumes. The hybrid approach is pure over-engineering at this stage.

The JSONB approach has a clear migration path if query patterns change:
1. For specific high-volume filter fields, add expression indexes (`CREATE INDEX ON projects ((custom_fields ->> 'slug'))`).
2. If full-text search on custom field values is needed, extract values into a `tsvector` column.
3. If EAV-style queries become necessary (millions of entities), the JSONB column can be migrated to an EAV table without API changes — the backend abstracts the storage behind the service layer.

This approach is consistent with the existing `AuditEvent.details` JSONB pattern in the codebase and with PostgreSQL best practices for semi-structured data at moderate volumes.

**Consequences**:
- `custom_fields JSONB` column added to `projects`, `tasks`, and `customers` tables in V24 migration.
- GIN indexes on `custom_fields` for each table.
- Hibernate mapping: `@JdbcTypeCode(SqlTypes.JSON)` on `Map<String, Object> customFields` field.
- `CustomFieldValidator` service validates JSONB values against `FieldDefinition` at the application layer — no database-level type enforcement.
- Filtering via JSONB containment: `custom_fields @> '{"slug": "value"}'::jsonb` for exact match, `custom_fields ->> 'slug'` with casting for range queries.
- Future: if a specific custom field becomes a high-volume filter target, add an expression index without schema changes. If data volumes exceed tens of thousands of entities per tenant, consider the hybrid approach (Option 3).
- No per-field update granularity at the database level — the entire JSONB object is replaced on each update. Application-level diffing provides change tracking for audit events.
