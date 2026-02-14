# ADR-055: Saved View Filter Execution

**Status**: Accepted

**Context**: Phase 11 introduces saved views — named filter+column configurations that users apply to list pages (projects, tasks, customers). A saved view's `filters` JSONB contains structured filter criteria:

```json
{
    "status": ["ACTIVE", "ON_HOLD"],
    "tags": ["vip-client", "urgent"],
    "assignee": "member-uuid",
    "customFields": {
        "court": { "op": "eq", "value": "high_court_gauteng" },
        "filing_date": { "op": "gte", "value": "2025-01-01" }
    },
    "dateRange": { "field": "created_at", "from": "2025-01-01", "to": "2025-12-31" },
    "search": "keyword"
}
```

The design question is *where* and *how* these filters are executed — who translates the filter JSONB into actual query predicates, and who computes the resulting data set?

Key constraints:
- The platform uses server-side pagination (Spring Data `Pageable`). Filters must be applied at the database level for correct page counts.
- Custom field filters target JSONB columns with GIN indexes. Client-side filtering would require loading all entities.
- Tag filters require joins against the `entity_tags` table — not possible in client-side filtering.
- The saved view's `columns` configuration determines which columns the frontend renders. This is a display concern.

**Options Considered**:

1. **Server-side filter execution with dynamic query building** — The backend receives filter parameters (from a saved view or direct query params), builds a dynamic SQL/JPQL query with the appropriate WHERE clauses, and returns paginated results. The frontend receives the filtered data and applies column configuration for rendering.
   - Pros:
     - **Correct pagination**: Total count and page slicing reflect the filtered result set. The client gets exactly 20 filtered results per page.
     - **Index utilization**: JSONB containment queries use GIN indexes. Tag filters use the `entity_tags` indexes. Status/assignee filters use standard B-tree indexes.
     - **Composable**: Filters compose naturally as AND clauses in a WHERE predicate. Adding a new filter type is a new clause, not a new query.
     - **Security boundary**: Tenant isolation (`@Filter`/RLS) and permission checks are applied at the query level. No risk of leaking data that should be filtered out.
     - **Efficient for large datasets**: Only matching rows are fetched and transferred. A tenant with 10,000 projects but a filter matching 50 sends only 50 rows.
   - Cons:
     - **Dynamic query complexity**: Building SQL predicates from a JSONB filter configuration requires careful escaping, type handling, and null-safety. Susceptible to SQL injection if not parameterized.
     - **Testing surface**: Each filter type (status, tags, custom fields, date range, search) needs individual and combined testing. The combinatorial space is large.
     - **Backend knows filter semantics**: The backend must understand what `{ "op": "gte", "value": "2025-01-01" }` means for a DATE field. This couples the filter DSL to the backend.

2. **Client-side filtering** — The backend returns unfiltered, paginated data. The frontend applies filters in JavaScript after receiving the response. Saved views are purely frontend concerns.
   - Pros:
     - **Simple backend**: No dynamic query building. Standard paginated list endpoints.
     - **Instant filter switching**: Changing filters doesn't require a server round-trip (if all data is cached).
     - **Frontend controls all display logic**: Filters and columns are both handled in the same layer.
   - Cons:
     - **Broken pagination**: If the backend returns page 1 (20 items) and the client filters to 5 matching items, the page appears mostly empty. The total count is wrong. The client would need to load ALL data to paginate correctly.
     - **Performance at scale**: A tenant with 5,000 projects would need to transfer all 5,000 to the client for accurate filtering. This is unacceptable for mobile or slow connections.
     - **No JSONB query capability**: The client would need all custom field values for all entities to filter by custom fields. This transfers massive JSONB payloads unnecessarily.
     - **No tag filtering**: The client would need all entity-tag associations preloaded. Cross-entity tag queries are impractical.
     - **Security risk**: Sending unfiltered data to the client means the client sees all entities before filtering — potentially exposing data the user shouldn't see (though this is less relevant since all org members can see all entities).

3. **Hybrid: server-side for standard filters, client-side for custom field filters** — The backend handles pagination, status, assignee, and tag filters. Custom field filtering is applied client-side after the server returns results.
   - Pros:
     - **Simpler backend**: No JSONB query building. Standard filters only.
     - **Client handles the flexible part**: Custom field semantics live entirely in the frontend.
   - Cons:
     - **Split responsibility**: Debugging filter behavior requires checking both server and client. "Why isn't my filtered count right?" becomes a two-system problem.
     - **Pagination is still broken for custom field filters**: If the server returns 20 results and the client filters 10 out, the page has 10 results and the total count is wrong.
     - **Inconsistent behavior**: Status filter + tag filter gives correct pagination. Status filter + custom field filter gives incorrect pagination. Users can't understand this distinction.
     - **Saved views can't be shared reliably**: A shared view with custom field filters would produce different results for different clients if they have different data cached.

**Decision**: Server-side filter execution with dynamic query building (Option 1).

**Rationale**: Server-side execution is the only option that provides correct pagination, consistent behavior, and efficient data transfer. The "client-side" and "hybrid" approaches both fail at the fundamental requirement of accurate paginated results with custom field filters.

The dynamic query building concern is real but manageable. The implementation uses Spring Data's `Specification` (JPA Criteria API) pattern for composable predicates:

```java
// Conceptual — each filter type is a Specification<Project>
Specification<Project> spec = Specification.where(null);

if (filters.getStatus() != null) {
    spec = spec.and((root, query, cb) ->
        root.get("status").in(filters.getStatus()));
}

if (filters.getCustomFields() != null) {
    for (var entry : filters.getCustomFields().entrySet()) {
        String slug = entry.getKey();
        FilterOp op = entry.getValue();
        spec = spec.and(customFieldPredicate(slug, op));
    }
}

if (filters.getTags() != null) {
    for (String tagSlug : filters.getTags()) {
        spec = spec.and(tagExistsPredicate(tagSlug));
    }
}
```

Custom field predicates use native SQL fragments via `CriteriaBuilder.function()` for JSONB operators. Tag predicates use `EXISTS` subqueries. All predicates are parameterized — no string interpolation, no SQL injection risk.

The column configuration (`columns` in the saved view) is intentionally kept as a frontend concern. The backend returns all entity data (including custom fields and tags) regardless of which columns are configured. The frontend uses the column list to determine which table columns to render. This separation is correct because:
1. Column visibility is purely a display concern — it doesn't affect data access or performance.
2. The data payload difference between "all columns" and "selected columns" is negligible (custom fields are already a single JSONB object).
3. Implementing server-side column projection for JSONB sub-fields would add complexity without meaningful performance benefit.

**Consequences**:
- Each entity list endpoint (projects, tasks, customers) accepts filter query params: `status`, `tags`, `assignee`, `customField[slug]=value`, `customField[slug][op]=gt&customField[slug][value]=100`, `dateRange[field]=created_at&dateRange[from]=2025-01-01`, `search`.
- `SavedViewFilterService` translates a `SavedView.filters` JSONB into query params for the entity list service. Alternatively, the frontend reads the saved view's filters and passes them as URL search params directly.
- Entity list services use Spring Data `Specification<T>` for composable filter predicates.
- Custom field filter predicates use JSONB operators: `@>` for containment (exact match), `->>` with casting for range queries.
- Tag filter predicates use `EXISTS (SELECT 1 FROM entity_tags et JOIN tags t ON t.id = et.tag_id WHERE t.slug = :slug AND et.entity_type = :type AND et.entity_id = root.id)`.
- `columns` configuration is stored in the saved view but interpreted entirely by the frontend. The backend does not project columns.
- Pagination is always correct: `total`, `page`, `size` reflect the filtered result set.
- URL search params serialize the active filter state for shareable links: `?view={viewId}` or `?status=ACTIVE&tags=urgent&customField[court]=high_court_gauteng`.
- Future: if filter complexity grows (nested AND/OR logic), the filter DSL in the JSONB can be extended with a `logic` field. The current implementation assumes all filters are ANDed.
