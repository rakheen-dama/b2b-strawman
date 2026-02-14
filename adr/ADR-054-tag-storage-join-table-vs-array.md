# ADR-054: Tag Storage — Join Table vs Array Column

**Status**: Accepted

**Context**: Phase 11 adds freeform tags to projects, tasks, and customers. Tags are org-scoped labels with optional color. The storage strategy must support: (a) applying multiple tags to an entity, (b) filtering list endpoints by tags (AND logic: "show entities that have ALL of these tags"), (c) cross-entity-type tag identity (the same "urgent" tag can be applied to projects, tasks, and customers), (d) tag deletion with cascade (deleting a tag removes all associations), and (e) usage counting ("how many entities use this tag?").

Tags are polymorphic — the same `Tag` record can be associated with projects, tasks, or customers. The entity type is part of the association, not the tag itself.

**Options Considered**:

1. **Polymorphic join table (`EntityTag`)** — A separate table with `(tag_id, entity_type, entity_id)` rows. The `Tag` table holds the tag metadata (name, slug, color). `EntityTag` links tags to entities.
   - Pros:
     - Standard relational pattern for many-to-many relationships.
     - Tag metadata (name, color) is stored once in the `Tag` table — updates propagate to all associations immediately.
     - Referential integrity: `tag_id` FK with `ON DELETE CASCADE` ensures tag deletion cleans up all associations automatically.
     - Efficient filtering: `EXISTS (SELECT 1 FROM entity_tags WHERE entity_id = p.id AND tag_id = :tagId)` is a simple indexed subquery.
     - Usage counting is a simple `COUNT(*)` GROUP BY on `entity_tags`.
     - Cross-entity-type queries ("find all entities with tag X") are straightforward.
     - Supports future per-association metadata (e.g., "who applied this tag and when") via additional columns on `EntityTag`.
   - Cons:
     - Additional table with its own tenant isolation (Hibernate `@Filter`, RLS policy).
     - Tag application is multi-row: applying 5 tags requires 5 INSERT operations (or DELETE + INSERT for full-replace).
     - Loading tags for an entity list requires a JOIN or subquery — not a simple column read.
     - Polymorphic `entity_id` has no database FK to the actual entity table — application-level integrity only.

2. **JSONB array column on each entity** — Add a `tags JSONB` column to projects, tasks, and customers. Store tags as an array of objects: `[{ "id": "uuid", "name": "Urgent", "slug": "urgent", "color": "#EF4444" }]`.
   - Pros:
     - Single column read — no JOIN needed to load tags with the entity.
     - Simple writes — update the JSONB array in place.
     - GIN index supports `@>` containment queries for filtering.
   - Cons:
     - Tag metadata is denormalized: renaming a tag requires updating the JSONB array on every entity that uses it. For a tag used on 1,000 entities, that's 1,000 UPDATE operations.
     - No referential integrity: deleting a tag requires scanning and updating JSONB arrays across three tables.
     - Usage counting requires scanning JSONB arrays across all entity tables: `SELECT COUNT(*) FROM projects WHERE tags @> '[{"id": "..."}]'` + same for tasks + same for customers.
     - Cross-entity-type queries are expensive (UNION across three tables with JSONB containment).
     - JSONB array ordering is application-managed — PostgreSQL does not guarantee array element order.
     - Tag identity is fragile: the `id` in the JSONB array must match the tag's actual ID, but there's no FK enforcement.

3. **PostgreSQL `text[]` array column** — Add a `tag_slugs text[]` column to each entity. Tags are stored as an array of slug strings. Tag metadata (name, color) lives in the `Tag` table and is joined at read time.
   - Pros:
     - Simpler than JSONB arrays — just slug strings.
     - GIN index on `text[]` supports `@>` (array containment) for efficient filtering: `WHERE tag_slugs @> ARRAY['urgent', 'vip']`.
     - Reads are fast — no JOIN needed for filtering (just the array column), JOIN only needed for display metadata (name, color).
   - Cons:
     - Renaming a tag's slug requires updating all entity arrays (same cascade problem as JSONB, slightly simpler).
     - No referential integrity on the array elements.
     - Deleting a tag requires scanning and updating arrays across three tables.
     - Usage counting still requires scanning arrays across three tables.
     - `text[]` is a PostgreSQL-specific type — less portable than JSONB (though portability is not a concern for this project).
     - Hibernate mapping for `text[]` requires `@JdbcTypeCode` or custom type handlers — less straightforward than a simple join table.

**Decision**: Polymorphic join table (`EntityTag`) (Option 1).

**Rationale**: The join table is the correct trade-off for tags because the dominant operations are *read-time filtering* and *tag lifecycle management* (rename, delete, count usage), not *bulk tag loading*. The JOIN cost for loading tags on a list of entities is minimal with proper indexing (`entity_tags(tenant_id, entity_type, entity_id)`), and the benefits are substantial:

1. **Tag rename/delete is O(1)**: Renaming a tag updates one row in the `Tag` table. Deleting a tag cascades via `ON DELETE CASCADE` on the FK. With array columns (Options 2 and 3), both operations are O(N) where N is the number of entities using the tag.

2. **Filtering is efficient**: The `EXISTS` subquery pattern for AND-logic tag filtering is well-optimized by PostgreSQL's query planner, especially with an index on `(tenant_id, tag_id, entity_type)`.

3. **Multi-tenant isolation is straightforward**: `EntityTag` has its own `tenant_id` column and `@Filter`, following the same pattern as every other tenant-scoped entity. Array columns would embed tag references inside the entity row, which works for `@Filter` on the entity itself but makes cross-entity tag queries (usage counting) harder.

The polymorphic `entity_type + entity_id` pattern does sacrifice database-level referential integrity on the entity side (no FK to `projects`, `tasks`, or `customers`). This is an accepted trade-off — the same pattern is used by `AuditEvent.entityType + entityId` in Phase 6, and application-level integrity (validating entity existence before creating `EntityTag` rows) has proven reliable.

**Consequences**:
- New `tags` table for tag metadata (id, tenant_id, name, slug, color).
- New `entity_tags` table for polymorphic associations (tag_id FK, entity_type, entity_id).
- `ON DELETE CASCADE` on `entity_tags.tag_id` — deleting a tag removes all associations.
- No FK from `entity_id` to entity tables — application-level integrity.
- Indexes: `(tenant_id, entity_type, entity_id)` for "get tags for entity", `(tenant_id, tag_id, entity_type)` for "find entities with tag".
- Tag filtering on list endpoints uses `EXISTS` subqueries — one per tag for AND logic.
- Entity response DTOs include a `tags` array populated via a separate query (not a JPA `@ManyToMany` — using explicit repository queries to avoid N+1).
- Tag rename/delete are O(1) operations with no cascade writes to entity tables.
