# ADR-054: Tag Storage — Join Table vs. Array Column

**Status**: Accepted

**Context**: Phase 11 introduces tags — freeform, org-scoped labels applied to projects, tasks, and customers. Tags are multi-valued (an entity can have many tags) and cross-entity (a single tag can be applied to projects, tasks, and customers). The storage strategy must support:

- Efficient filtering: "find all projects with tag X" and "find all projects with tags X AND Y."
- Usage counting: "how many entities use tag Z?" (for the tag management UI).
- Cascade deletion: deleting a tag removes all associations.
- Tag auto-complete: list all tags with their names and colors.
- Cross-entity queries: "find all entities (any type) with tag X" (for future global search).

Key constraints:
- Tags apply to three entity types (`PROJECT`, `TASK`, `CUSTOMER`) with a shared tag namespace per org.
- Tags are org-scoped (tenant-isolated via `tenant_id` + `@Filter`/RLS).
- Tag names are freeform (user-created), not a fixed enum.
- Expected volume: tens to hundreds of tags per org, thousands of entity-tag associations.

**Options Considered**:

1. **Polymorphic join table (`entity_tags`)** — A dedicated table with `(tag_id, entity_type, entity_id)` linking tags to entities. The `tag_id` column has a FK to the `tags` table. The `entity_id` column is a UUID with no FK constraint (polymorphic reference).
   - Pros:
     - **Standard SQL filtering**: Tag-based queries use `JOIN` or `EXISTS` subqueries. Well-optimized by PostgreSQL's query planner.
     - **Referential integrity on tag side**: `REFERENCES tags(id) ON DELETE CASCADE` ensures deleting a tag removes all associations atomically.
     - **Usage counting**: `SELECT tag_id, COUNT(*) FROM entity_tags GROUP BY tag_id` — trivial aggregate query with no JSONB parsing.
     - **Cross-entity queries**: `SELECT * FROM entity_tags WHERE tag_id = :id` returns all entities with that tag, regardless of type.
     - **Multi-tag AND filtering**: Multiple `EXISTS` subqueries compose naturally. PostgreSQL handles this efficiently with the right indexes.
     - **Consistent with existing patterns**: The codebase uses `entity_type + entity_id` polymorphic references in `Comment` and `Notification` entities.
   - Cons:
     - **No FK to entity tables**: `entity_id` can't reference `projects`, `tasks`, and `customers` simultaneously. Orphaned rows are possible if an entity is deleted without cleanup. Mitigated by application-level cleanup or periodic garbage collection.
     - **Write overhead**: Applying 5 tags to an entity requires 5 INSERT statements (after deleting existing associations). The full-replace pattern (DELETE all + INSERT new) is simple but creates more write operations than updating an array.
     - **Join cost on list queries**: Including tags in entity list responses requires a join or subquery per entity. For a 20-row page, that's 20 subqueries (or one batched join).

2. **PostgreSQL `text[]` array column on entity tables** — Add a `tags text[]` column to `projects`, `tasks`, and `customers`. Store tag slugs directly in the array.
   - Pros:
     - **Single-query reads**: Tags are part of the entity row — no join needed for list responses.
     - **Simple writes**: `UPDATE projects SET tags = ARRAY['urgent', 'vip'] WHERE id = :id`.
     - **GIN index for array queries**: `CREATE INDEX ON projects USING GIN (tags)` supports `@>` containment queries.
   - Cons:
     - **No referential integrity**: Tag values are denormalized strings. Renaming a tag requires updating every entity row that contains the old name across three tables.
     - **No cascade delete**: Deleting a tag requires scanning all three entity tables and removing the slug from every array. This is `O(n)` across all entities, not `O(1)`.
     - **No tag metadata**: The array stores slugs only — no color, no ID. Tag metadata (color, display name) must be joined from a separate `tags` table anyway, negating the "no join" advantage.
     - **Usage counting requires scanning**: Counting how many entities use a tag requires `SELECT COUNT(*) FROM projects WHERE 'urgent' = ANY(tags) UNION ALL SELECT COUNT(*) FROM tasks WHERE ...` — three full table scans per tag.
     - **Cross-entity queries are three queries**: Finding all entities with a tag requires querying three separate tables and merging results.
     - **Hibernate mapping friction**: JPA's `text[]` mapping requires custom type handlers. Not as clean as a standard entity relationship.

3. **JSONB array of tag objects on entity tables** — Add a `tags JSONB` column to each entity table, storing `[{ "id": "uuid", "slug": "urgent", "color": "#EF4444" }]`.
   - Pros:
     - **Single-query reads with metadata**: Tags including name and color are embedded — no join for display.
     - **Richer than `text[]`**: Stores tag ID, slug, name, and color together.
     - **GIN index for containment**: `tags @> '[{"slug": "urgent"}]'` works with GIN.
   - Cons:
     - **Massive denormalization**: Tag metadata (name, color) is duplicated across every entity that uses the tag. Renaming a tag or changing its color requires updating every JSONB array across three tables.
     - **Consistency risk**: If a tag rename update misses some rows (e.g., a concurrent write), tag metadata becomes inconsistent. There's no single source of truth at query time.
     - **No cascade delete**: Same problem as `text[]` — requires scanning all entities.
     - **No usage counting**: Same problem as `text[]` — three table scans.
     - **JSONB array manipulation**: Adding/removing a tag from a JSONB array is syntactically complex (`jsonb_set`, `jsonb_array_elements`, filtering). Much harder than INSERT/DELETE on a join table.
     - **Storage overhead**: Each tag association stores ~100 bytes of JSON (id + slug + name + color) vs. ~32 bytes in a join table row (two UUIDs).

**Decision**: Polymorphic join table (`entity_tags`) (Option 1).

**Rationale**: The join table is the correct relational pattern for this use case. The primary operations — filtering by tag, counting usage, cascading deletes, and cross-entity queries — are all natural SQL operations on a join table. The array-based alternatives (Options 2 and 3) optimize for reads at the cost of making every other operation harder.

The "no FK to entity tables" limitation of the polymorphic pattern is well-understood in this codebase. The `Comment` entity already uses `entity_type + entity_id` to reference tasks and documents without FK constraints. Application-level cleanup (deleting entity tags when an entity is deleted) is reliable and consistent with existing patterns.

The join overhead for list queries is mitigated by:
1. Batching: `SELECT et.entity_id, t.* FROM entity_tags et JOIN tags t ON t.id = et.tag_id WHERE et.entity_type = 'PROJECT' AND et.entity_id IN (:ids)` — one query for the entire page.
2. The tag join is small: each entity typically has 1-5 tags, so the join produces a manageable result set.

**Consequences**:
- `tags` table for tag definitions (id, name, slug, color, tenant_id).
- `entity_tags` table for associations (tag_id FK, entity_type, entity_id, tenant_id).
- `ON DELETE CASCADE` on `entity_tags.tag_id` — deleting a tag removes all associations automatically.
- Application-level cleanup: entity deletion services (ProjectService, TaskService, CustomerService) must delete related `EntityTag` rows before deleting the entity. Alternative: periodic garbage collection job that removes `EntityTag` rows where `entity_id` no longer exists in the referenced table.
- Tag filtering uses `EXISTS` subqueries: `WHERE EXISTS (SELECT 1 FROM entity_tags et JOIN tags t ON t.id = et.tag_id WHERE t.slug = 'urgent' AND et.entity_type = 'PROJECT' AND et.entity_id = p.id)`.
- Tag metadata (name, color) requires a join for list responses — mitigated by batch loading.
- Indexes on `entity_tags`: `(tenant_id, entity_type, entity_id)` for entity-to-tags lookups, `(tenant_id, tag_id, entity_type)` for tag-to-entities lookups.
