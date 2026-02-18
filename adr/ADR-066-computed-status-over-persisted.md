# ADR-066: Computed Status Over Persisted Status

**Status**: Accepted

**Context**:

Phase 15's setup status and customer readiness features aggregate data from multiple existing entities (rates, budgets, members, custom fields, checklists) into a single status response. This status must reflect the current state of the underlying data — if an admin configures a rate card, the project's setup status should immediately show "Rate card configured: ✓" without any explicit "refresh" action.

The question is whether to compute this status on every request by querying existing tables, or to persist the status in a dedicated table (updated via event handlers whenever underlying data changes).

**Options Considered**:

1. **Computed on-the-fly (chosen)** — Each API request triggers queries against existing repositories. The service aggregates results into a DTO and returns it. No status is stored.
   - Pros: Always consistent with reality — impossible to have stale status; zero new database tables or migrations; no event handlers to maintain; no sync bugs (a category that has historically caused issues — see OSIV schema pinning, filter bypass bugs); simple to reason about; easy to test (mock repos, call service, assert result).
   - Cons: Every detail page load triggers 4-6 additional queries; cannot query "all projects that are fully set up" without computing status for every project; no historical tracking of when a project became fully set up.

2. **Persisted status table** — New `setup_status` table storing the last-computed status per entity. Updated via Spring `ApplicationEvent` listeners whenever a rate, budget, member, or field value changes.
   - Pros: Single-row lookup on detail page load; enables "show all incomplete projects" queries; enables historical tracking; reduces per-request query count.
   - Cons: Requires new database table + migration (violates Phase 15's no-new-tables constraint); requires event listeners for every mutation that affects status (rate CRUD, budget CRUD, member CRUD, field value changes, checklist progress, customer lifecycle transitions — at least 8 event sources); event handlers can miss edge cases (bulk operations, direct SQL, import flows); stale data risk if an event handler fails silently; significant implementation and testing overhead.

3. **Cached computation with TTL** — Compute on-the-fly but cache the result in application memory (Caffeine) with a short TTL (e.g., 30 seconds).
   - Pros: Reduces database queries for rapid page refreshes; no new tables; status eventually consistent within TTL window.
   - Cons: Cache invalidation complexity (must invalidate on any underlying data change or accept staleness); multi-instance deployments see different cached values; adds Caffeine dependency management; 30-second staleness window where a user configures a rate but still sees "No rate card" — confusing UX; the queries being cached are already fast (indexed lookups returning boolean/count results).

**Decision**: Option 1 — computed on-the-fly.

**Rationale**:

The queries involved are lightweight: `EXISTS` checks against indexed foreign key columns (`project_id`, `customer_id`), `COUNT` queries against small result sets (a project typically has 1-5 members, 0-3 rate overrides), and JSONB key existence checks on the entity's `custom_fields` column. In a dedicated-schema environment ([ADR-064](ADR-064-dedicated-schema-only.md)), these queries operate on small, tenant-scoped tables — a typical tenant has tens of projects, not thousands. The total query time for a full setup status computation is estimated at 2-5ms on indexed PostgreSQL.

The persisted approach would require event handlers for at least 8 different mutation paths (rate CRUD, budget CRUD, member add/remove, field value update, checklist item toggle, customer lifecycle transition, project-customer linking, and any future mutations affecting setup). Each handler is a potential consistency bug — and this project's history shows that event-driven state synchronization introduces subtle issues (see the OSIV EntityManager pinning and filter bypass bugs documented in project memory). The simplicity of "query the source of truth every time" eliminates this entire bug category.

The "all incomplete projects" query (Option 2's advantage) is not a Phase 15 requirement — the existing dashboards (Phase 9) serve the cross-project overview role. If this query becomes needed in the future, it can be implemented as a batch computation rather than a real-time persisted status.

**Consequences**:

- Positive:
  - Status is always consistent with underlying data — zero sync lag, zero stale data risk
  - No event handlers to write, test, or maintain across 8+ mutation paths
  - No new database tables or migrations
  - Testable in isolation: mock repositories, call service, assert DTO fields
  - Performance is adequate: 2-5ms for indexed queries on tenant-scoped tables
- Negative:
  - Cannot efficiently query "all projects where setup is incomplete" across the tenant (would require computing status for every project)
  - No historical record of when a project became fully set up (acceptable — this is a display feature, not an audit trail)
  - If the query set grows significantly in future phases, may need to revisit caching (but current 4-6 queries per page load is well within acceptable latency)
