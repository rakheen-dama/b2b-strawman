# ADR-055: Saved View Filter Execution

**Status**: Accepted

**Context**: Phase 11 introduces saved views — named filter configurations that users can apply to list pages (projects, tasks, customers). Each saved view stores its filter criteria as JSONB: status filters, tag filters, custom field filters, date ranges, and text search. When a user selects a saved view, the system must apply these filters to the entity list and return paginated results.

The key design question is where filter execution happens: purely in the browser (client-side filtering of pre-loaded data), purely on the server (SQL query construction), or a hybrid approach. The answer affects performance, pagination consistency, and implementation complexity.

A secondary question is how the filter JSONB is translated to SQL — whether via a generic query builder, JPA Criteria API, or hand-written JPQL/native queries.

**Options Considered**:

1. **Server-side SQL execution with parameterized native queries** — The backend parses the filter JSONB, translates each filter clause to a SQL predicate, and constructs a native query with parameterized values. Standard filters (status, assignee, date range, search) map to simple WHERE clauses. Custom field filters use JSONB operators (`@>`, `->>`, cast). Tag filters use EXISTS subqueries.
   - Pros:
     - Consistent pagination: the database applies filters, sorts, and LIMIT/OFFSET in a single query. Page sizes are accurate.
     - Scales to large datasets: filtering 10,000 entities returns only the matching page, not all 10,000 rows.
     - JSONB operators and GIN indexes are purpose-built for this use case.
     - Filter logic is centralized in the backend — frontend never needs to implement filter matching.
     - URL-shareable: filter parameters are passed as query params (`?status=ACTIVE&tags=urgent&customField[court]=high_court`), so saved view state can be reconstructed from the URL.
   - Cons:
     - SQL construction logic is moderately complex — each filter type needs a specific SQL translation.
     - Native queries bypass Hibernate's `@Filter` — must rely on RLS (`set_config('app.current_tenant', ...)`) for tenant isolation in shared-schema mode. However, `TenantFilterTransactionManager` already sets `app.current_tenant` for all transactions, so RLS is active.
     - Adding new filter types requires backend changes (new SQL clause generation).

2. **Client-side filtering in the browser** — The backend returns all entities (or a large page), and the frontend filters, sorts, and paginates in memory using the saved view's filter criteria.
   - Pros:
     - Zero backend changes for filter logic — the frontend handles everything.
     - Instant filter switching — no server round-trip when changing filters.
     - Simple backend: just return paginated entities without filter support.
   - Cons:
     - Does not scale: a tenant with 5,000 projects cannot load all of them into the browser to filter client-side.
     - Pagination is broken: the backend paginates before filtering. Page 1 might have 20 rows but only 3 match the filter. The user sees 3 results and thinks there are no more.
     - Custom field filtering on JSONB would require the frontend to parse and match JSONB values against filter criteria — duplicating database logic in JavaScript.
     - Not URL-shareable without additional serialization.
     - Memory pressure in the browser for large datasets.

3. **JPA Criteria API with Specification pattern** — Use Spring Data JPA's `Specification<T>` to build type-safe filter predicates in Java. Standard filters use JPA criteria. Custom field filters use `CriteriaBuilder.function()` for JSONB operations.
   - Pros:
     - Type-safe query construction — no raw SQL strings.
     - Composable: specifications can be AND'd together (`spec1.and(spec2).and(spec3)`).
     - Works with Hibernate `@Filter` (criteria queries go through the Session, so tenant filter is applied).
     - Familiar Spring Data pattern.
   - Cons:
     - JPA Criteria API has poor support for PostgreSQL JSONB operators. `@>` containment, `->>` text extraction, and `::numeric` casts require `CriteriaBuilder.function()` calls that are verbose and fragile.
     - Tag filtering via EXISTS subquery is awkward in Criteria API — requires correlated subquery construction.
     - The generated SQL is often suboptimal compared to hand-written native queries.
     - Debugging criteria-generated SQL is harder than debugging explicit queries.
     - JSONB-specific operations are database-specific anyway — the Criteria API's portability benefit is lost.

**Decision**: Server-side SQL execution with parameterized native queries (Option 1).

**Rationale**: Server-side execution is the only viable option for consistent pagination and scalability. Client-side filtering (Option 2) breaks pagination semantics fundamentally — this is not a reasonable trade-off at any scale.

Between server-side approaches (Option 1 vs Option 3), native queries win because the dominant filter patterns are JSONB-specific. The `@>` containment operator, `->>` text extraction, `::numeric` and `::date` casts, and `EXISTS` subqueries for tag filtering are all PostgreSQL-native operations that the JPA Criteria API handles poorly. Writing these as parameterized native SQL is clearer, more maintainable, and produces better execution plans.

The `ViewFilterService` constructs queries dynamically:

```java
public class ViewFilterService {
    // Translates filter params to SQL WHERE clauses + parameter bindings
    FilterQuery buildFilterQuery(String entityType, Map<String, Object> filters);
}
```

Each filter type has a dedicated handler:
- `StatusFilterHandler` → `status IN (:statuses)`
- `TagFilterHandler` → `EXISTS (SELECT 1 FROM entity_tags ...)`
- `CustomFieldFilterHandler` → `custom_fields @> :json` or `custom_fields ->> 'slug' ...`
- `DateRangeFilterHandler` → `created_at >= :from AND created_at <= :to`
- `SearchFilterHandler` → `name ILIKE '%' || :search || '%'`

Native queries in this context rely on RLS for tenant isolation (not Hibernate `@Filter`). This is safe because `TenantFilterTransactionManager` sets `app.current_tenant` via `set_config()` at the beginning of every transaction, and all new tables have RLS policies.

Column configuration (which columns to display in the list) is stored in the saved view's `columns` JSONB but is purely a frontend concern. The backend always returns the full entity data including `customFields`. The frontend reads the `columns` array and renders only the specified columns. This avoids encoding UI concerns in the API.

**Consequences**:
- New `ViewFilterService` in `view/` package — translates filter JSONB to SQL WHERE clauses.
- Filter handlers for each filter type — easily extensible for new filter types.
- Native queries used for filtered list endpoints — bypasses Hibernate `@Filter` but relies on RLS.
- Filter parameters serialized to URL query params: `?status=ACTIVE&tags=slug1,slug2&customField[slug]=value`.
- `?view={viewId}` query param on list endpoints — loads saved view and applies its filters.
- Column configuration is frontend-only — the backend does not vary its response based on column config.
- Adding new filter types (e.g., assignee, priority) requires a new handler class and SQL clause — no changes to existing handlers.
- The `ViewFilterService` must sanitize filter values to prevent SQL injection — all values are parameterized, never interpolated.
