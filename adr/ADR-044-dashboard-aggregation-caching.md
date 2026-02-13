# ADR-044: Dashboard Data Strategy — Query-Time Aggregation with In-Memory Caching

**Status**: Accepted

**Context**: Phase 9 introduces operational dashboards that aggregate data from multiple existing entities (Projects, Tasks, TimeEntries, Members, ProjectBudgets) into at-a-glance KPIs, project health scores, team workload distributions, and personal utilization metrics. These dashboards are read-only views — they never mutate data, only query and aggregate it.

The aggregation queries are non-trivial: they span multiple tables, use GROUP BY with conditional aggregation, and compute derived metrics like billable percentage and overdue ratios. Running these queries on every page load without any caching would place unnecessary load on the database, especially for the company dashboard which aggregates across all projects and all team members in the org.

The expected data scale is modest: tens of thousands of time entries and thousands of tasks per tenant, not millions. The tenancy model (schema-per-tenant for Pro, shared schema with RLS for Starter) means each query operates within a single tenant's data partition.

The question is how to balance dashboard freshness (users expect recent changes to appear) with database load (aggregation queries should not dominate the query budget).

**Options Considered**:

1. **Materialized views refreshed on schedule** -- PostgreSQL materialized views that pre-compute dashboard aggregations, refreshed every N minutes by a background job (pg_cron or application scheduler).
   - Pros:
     - Database handles the aggregation and storage — application code is simpler
     - Fast reads (materialized view is a regular table scan)
     - Well-understood PostgreSQL feature with REFRESH MATERIALIZED VIEW CONCURRENTLY support
   - Cons:
     - Requires a background scheduler (pg_cron, Spring @Scheduled, or external cron) — the platform currently has no scheduled jobs and introducing one adds operational complexity
     - Materialized views are per-schema — with schema-per-tenant isolation, each tenant's schema needs its own materialized views, multiplying the number of database objects to manage
     - Refresh is all-or-nothing per view — cannot refresh a single project's metrics, must refresh the entire view
     - Neon Postgres (the production database) has limitations on pg_cron and background workers
     - Adds DDL to every tenant migration for view creation and refresh scheduling
     - Staleness is fixed by the refresh interval, not adaptive to query patterns

2. **Pre-computed summary tables updated on write** -- Maintain denormalized summary tables (e.g., `project_summary` with task counts, hours, etc.) that are updated via triggers or application-level event handlers whenever source data changes.
   - Pros:
     - Dashboard reads are trivially fast — just SELECT from the summary table
     - Data is always fresh (updated on every write)
     - No caching layer needed — the summary table IS the cache, with database-level durability
   - Cons:
     - Adds write-path complexity to every mutation: creating a task, logging time, completing a task all need to update the summary table
     - Trigger-based updates are fragile and hard to debug; application-level updates couple every service to the summary table
     - Schema changes in source tables cascade to summary table maintenance code
     - Risk of summary table drift from actual data if an update path is missed
     - New migration per tenant schema for the summary table
     - Every existing service (TaskService, TimeEntryService, etc.) gains a dependency on the summary update mechanism — violates the single responsibility principle

3. **Query-time aggregation with Caffeine in-process cache** -- Run aggregation queries on demand, cache results in Caffeine (already a project dependency) with short TTLs. No pre-computation, no background jobs, no new database objects.
   - Pros:
     - Zero write-path impact — dashboards are purely read-only, no coupling to mutation services
     - No new database objects (no materialized views, no summary tables, no triggers)
     - No background jobs or schedulers — eliminates an entire class of operational concerns
     - Caffeine is already used in the codebase (TenantFilter, MemberFilter) with established patterns (getIfPresent + put)
     - Per-tenant cache isolation via key prefix — cache keys include tenantId as the first segment
     - Short TTL (1-3 minutes) provides acceptable freshness for a dashboard that is loaded on page visit
     - Cache misses are self-healing — a slow query only runs once per TTL period per tenant
     - Stateless horizontally — each application instance maintains its own cache, no distributed cache coordination needed
     - Graceful degradation: if cache is cold, user waits a few hundred milliseconds for the query; subsequent requests are fast
   - Cons:
     - Dashboard data can be up to 3 minutes stale — a task created by one user may not appear in another user's dashboard immediately
     - Each application instance maintains its own cache — in a multi-instance deployment (ECS), different instances may serve different cached data until TTLs align
     - Aggregation queries hit the database on cache miss — for very large tenants, these could be slow (mitigated by indexes and the modest data scale)
     - Cache memory is bounded but consumes JVM heap — 1,000 org-level entries and 5,000 project-level entries with average 2KB per entry = ~12MB, well within ECS task memory

4. **Read replicas with dedicated analytics queries** -- Route dashboard queries to a PostgreSQL read replica to isolate analytics load from the primary transactional database.
   - Pros:
     - Complete isolation of dashboard query load from write-path performance
     - Read replica can have different indexes optimized for aggregation
     - No caching needed — queries can always run fresh against the replica
   - Cons:
     - Neon Postgres read replicas add cost and operational complexity
     - Replication lag introduces staleness anyway (typically seconds, but can spike)
     - Requires a second datasource configuration, connection pool, and routing logic
     - Over-engineers the problem at the current data scale — the expected query load does not warrant a dedicated replica
     - Does not exist in the current infrastructure; introducing it for dashboards alone is disproportionate

**Decision**: Query-time aggregation with Caffeine in-process cache (Option 3).

**Rationale**:

1. **Zero write-path impact**: The dashboard layer is purely additive — it reads existing data without touching mutation paths. No existing service (TaskService, TimeEntryService, etc.) gains any new dependency. This is critical for a feature that is developed in parallel with Phase 8 (rates, budgets) — the two phases cannot create merge conflicts in shared services.

2. **Operational simplicity**: No background jobs, no materialized views, no summary tables, no triggers. The entire caching infrastructure is a Caffeine cache instance declared as a field in DashboardService. It starts when the application starts and evicts on TTL — no monitoring, no coordination, no failure modes beyond JVM restart (which clears the cache harmlessly).

3. **Acceptable staleness**: A 1-3 minute TTL means that in the worst case, a dashboard shows data that is 3 minutes old. For an operational dashboard loaded on page visit (not a real-time monitoring screen), this is well within user expectations. The user who creates a task sees it immediately on the task list — they do not expect the org-wide KPI to update in the same second.

4. **Proven pattern**: Caffeine is already used in the codebase with the `getIfPresent() + put()` pattern. The dashboard cache follows the exact same approach — no new patterns to learn, no new infrastructure to manage.

5. **Right-sized for the data scale**: With tens of thousands of time entries per tenant, aggregation queries complete in tens of milliseconds on indexed tables. Caching is an optimization, not a necessity. If the platform grows to millions of entries per tenant, the architecture can evolve to materialized views (Option 1) or read replicas (Option 4) without changing the API contract.

**Consequences**:
- DashboardService maintains two Caffeine caches: `orgCache` (3-min TTL, 1,000 max entries) and `projectCache` (1-min TTL, 5,000 max entries)
- Cache keys follow the format `"{tenantId}:{namespace}[:{id}]:{params}"` for per-tenant isolation (e.g., `"tenant_abc:kpis:2025-01-01_2025-01-31"`, `"tenant_abc:project:{projectId}:health"`, `"tenant_abc:personal:{memberId}:2025-01-01_2025-01-31"`). See Section 9.5 of the architecture doc for the full key specification per endpoint.
- No write-through eviction — short TTLs handle staleness; mutation services do not need to be aware of the dashboard cache
- In a multi-instance deployment, each instance has its own cache — this means a user whose requests are load-balanced across instances may see slightly different data within a TTL window. This is acceptable for dashboard use.
- Memory overhead: bounded by max entries. At ~2KB average per cached response, the org cache uses ~2MB and the project cache uses ~10MB. Both are well within the JVM heap budget.
- When to revisit: if P95 dashboard load time exceeds 2 seconds on cache miss (indicating that aggregation queries are too slow for query-time computation), consider introducing materialized views for the slowest queries. Monitor via Spring Boot Actuator metrics on the dashboard endpoints.
- No distributed cache (Redis) is needed — the data is derived from a shared database, so eventual consistency across instances is acceptable for dashboards
