# ADR-052: JSONB vs EAV for Custom Field Storage

**Status**: Accepted

**Context**: Phase 11 introduces custom fields on projects, tasks, and customers. Each organization defines its own field definitions (name, type, validation rules), and field values are stored per entity. The storage strategy must support: (a) efficient reads — loading an entity with its custom fields in a single query, (b) filtering — list endpoints must support filtering by custom field values with reasonable performance, (c) schema flexibility — tenants add/remove fields without DDL changes, and (d) compatibility with the existing multi-tenant isolation model (Hibernate `@Filter` for Starter, dedicated schema for Pro).

The two canonical approaches for semi-structured data alongside relational data are:

1. **JSONB column on the entity table** — store custom field values as a JSON object on the entity row itself.
2. **Entity-Attribute-Value (EAV) table** — a separate table with `(entity_id, field_key, field_value)` rows, one per field value per entity.

Both approaches are well-understood in the PostgreSQL ecosystem. The choice depends on query patterns, data volume, and operational complexity.

**Options Considered**:

1. **JSONB column on the entity table** — Add a `custom_fields JSONB` column to `projects`, `tasks`, and `customers`. Values are keyed by field slug. GIN index for containment queries.
   - Pros:
     - Single query to load entity with all custom field values (no JOINs).
     - GIN index supports `@>` containment queries for filtering, which covers the most common filter patterns (equality, membership).
     - No additional table — simpler migration, no join explosion.
     - Custom fields are "just another column" for Hibernate `@Filter` — tenant isolation applies to the entity row as usual.
     - Schema-on-read flexibility: the JSONB column accepts any valid JSON without DDL changes.
     - Familiar pattern in the codebase — `AuditEvent.details` already uses `@JdbcTypeCode(SqlTypes.JSON)` with `Map<String, Object>`.
   - Cons:
     - No referential integrity between field values and field definitions — application-level validation only.
     - Cannot index individual field values efficiently (GIN handles containment, but range queries like `amount > 10000` require expression indexes or sequential scan of the JSONB).
     - Large JSONB values (many fields with long text) increase row size and can affect table scan performance.
     - Cannot enforce uniqueness constraints on individual field values within JSONB.

2. **Entity-Attribute-Value (EAV) table** — A separate `custom_field_values` table with `(entity_type, entity_id, field_definition_id, value JSONB)` rows. One row per field value per entity.
   - Pros:
     - Each value is a separate row — can be individually indexed (e.g., B-tree on `field_definition_id + value`).
     - Referential integrity: `field_definition_id` FK enforces that values reference valid field definitions.
     - Range queries on individual fields are straightforward with expression indexes.
     - Smaller row sizes — each value row is compact.
   - Cons:
     - Loading an entity with N custom fields requires a JOIN that produces N rows (or a subquery with `json_object_agg`). For list endpoints returning 20 entities with 10 custom fields each, this is 200 additional rows.
     - Filtering by multiple custom fields simultaneously requires multiple JOINs (one per field), leading to join explosion: `WHERE cfv1.field_id = :f1 AND cfv1.value = :v1 AND cfv2.field_id = :f2 AND cfv2.value > :v2`.
     - Tenant isolation must apply to the EAV table separately — requires its own `tenant_id`, `@Filter`, and RLS policy. Cross-tenant data leaks are a risk if the EAV filter is misconfigured.
     - Hibernate mapping is awkward — the EAV table doesn't map naturally to a `@OneToMany` on the entity because the "many" side is polymorphic (entity_type + entity_id).
     - Higher write amplification: setting 10 custom fields means 10 INSERT/UPDATE/DELETE operations on the EAV table, with potential for partial failures.

3. **Hybrid: JSONB column + materialized EAV for frequently filtered fields** — Store all values in JSONB on the entity table (source of truth), and maintain a denormalized EAV table for fields marked as "filterable" (using a trigger or application-level sync).
   - Pros:
     - Best of both: fast reads from JSONB, efficient filtered queries from EAV indexes.
     - Only fields that are actually filtered get the EAV overhead.
   - Cons:
     - Dual-write complexity: every custom field update must be applied to both JSONB and EAV.
     - Consistency risk: if the sync fails, the EAV index and JSONB column disagree.
     - Doubles the storage footprint for filtered fields.
     - Significant implementation complexity for v1 — solving a problem that may not exist at this scale.
     - "Filterable" flag adds complexity to field definition management.

**Decision**: JSONB column on the entity table (Option 1).

**Rationale**: At the expected scale (hundreds of field definitions per tenant, up to 10,000 entities per tenant), JSONB with GIN indexing provides more than adequate read and filter performance. The containment operator (`@>`) covers the most common filter patterns: equality, boolean, and dropdown membership. Range queries on numeric and date custom fields can use `custom_fields ->> 'slug'` with a cast, which is less efficient than a dedicated B-tree index but acceptable for the expected data volumes.

The EAV approach (Option 2) solves a performance problem that does not exist at this scale. The join explosion and Hibernate mapping complexity would be immediate pain for zero benefit. The hybrid approach (Option 3) adds dual-write complexity that is unjustified for v1.

JSONB is also the simplest option for multi-tenant isolation. The `custom_fields` column is part of the entity row, so existing `@Filter` and RLS policies apply automatically. An EAV table would need its own isolation layer — an additional surface for tenant data leaks.

If query patterns change (e.g., a tenant has 100,000+ entities and needs frequent range queries on a specific custom field), the migration path is clear: add expression indexes on hot fields (`CREATE INDEX ON projects ((custom_fields ->> 'amount')::numeric)`) or migrate to the hybrid approach. Neither requires changing the storage model.

**Consequences**:
- `custom_fields JSONB` column added to `projects`, `tasks`, and `customers` tables.
- GIN index on `custom_fields` for each table (covers `@>` containment queries).
- Field value validation is application-level only — no database-level enforcement of field types or required fields.
- Unknown keys in JSONB are silently stripped during validation (not stored), preventing schema pollution.
- Range queries on numeric/date custom fields use `(custom_fields ->> 'slug')::numeric` or `::date` casts, which are not covered by the GIN index. For high-cardinality range queries, expression indexes can be added per-tenant as needed.
- `@JdbcTypeCode(SqlTypes.JSON)` with `Map<String, Object>` for the Hibernate mapping — same pattern as `AuditEvent.details`.
