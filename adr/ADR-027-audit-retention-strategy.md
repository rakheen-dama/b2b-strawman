# ADR-027: Audit Retention Strategy

**Status**: Accepted

**Context**: Audit events accumulate indefinitely without a retention policy. The platform needs a strategy for how long audit data is kept, how it's purged, and whether different event categories have different lifetimes. The approach must balance compliance requirements (some industries require multi-year audit trails), storage costs (Neon Postgres is metered), and operational simplicity.

The platform serves generic B2B verticals (legal, consulting, accounting). We cannot assume a single regulatory framework, so defaults should be reasonable for common cases with per-deployment configuration.

**Options Considered**:

1. **Indefinite retention (never delete)** — Keep all audit events forever. No purge mechanism.
   - Pros: Simplest implementation — no scheduled jobs, no configuration. Maximum data availability. No risk of prematurely deleting compliance-critical records.
   - Cons: Unbounded storage growth. For active orgs, the `audit_events` table will eventually dwarf all other tables. Neon Postgres storage is metered — this directly impacts cost. Index performance degrades on very large tables. GDPR "right to erasure" may require deletion capability.

2. **Fixed retention with configurable periods** — Default retention periods (e.g., 3 years for domain events, 1 year for security events) configurable via Spring application properties. A scheduled job purges expired events.
   - Pros: Bounded storage growth. Configurable per deployment (a healthcare deployment can set 7 years; a startup can set 1 year). Different lifetimes for different event categories (security events have shorter operational value than domain events). Industry-standard approach.
   - Cons: Requires a scheduled job. Risk of misconfiguration (too short = compliance violation). Need to handle tenant iteration for purging.

3. **Tiered retention: hot/warm/archive** — Recent events (30 days) in the main table; older events moved to a compressed archive table or exported to S3 as Parquet/JSON files. Queries span tiers.
   - Pros: Optimal performance for recent queries. Cold storage is cheaper. Can retain data indefinitely at low cost.
   - Cons: Significant implementation complexity — archive job, cross-tier query federation, S3 export infrastructure. Over-engineering for Phase 6. Archive queries are slow and require separate infrastructure.

4. **Per-tenant configurable retention** — Each org can set its own retention period via settings (e.g., a Pro org selects "7 years" in settings). The purge job reads per-org configuration.
   - Pros: Maximum flexibility. Enterprise customers can meet their specific compliance requirements.
   - Cons: Requires a settings UI (out of scope for Phase 6). Requires per-tenant configuration storage. The purge job becomes more complex (different TTLs per tenant). Org admins may not understand the implications of their choice.

**Decision**: Fixed retention with configurable periods (Option 2), with the scheduled purge job deferred to a future maintenance slice.

**Rationale**: A fixed, configurable retention policy is the pragmatic choice for Phase 6. It provides bounded storage growth with sensible defaults while allowing deployment-specific overrides.

The defaults are:
- **Domain events** (e.g., `task.created`, `document.accessed`): **3 years (1,095 days)**. This covers the most common compliance requirements for professional services (legal document retention, financial audit trails) and provides a comfortable buffer beyond the typical 2-year minimum.
- **Security events** (e.g., `security.access_denied`, `security.auth_failed`): **1 year (365 days)**. Security events are primarily useful for incident investigation, which typically occurs within weeks or months. One year provides ample time for post-incident review without the storage overhead of multi-year retention.

These defaults are configured via Spring application properties:
```yaml
audit:
  retention:
    domain-events-days: 1095
    security-events-days: 365
    purge-enabled: false  # Phase 6 default — enable when purge job is implemented
```

The scheduled purge job is **not implemented in Phase 6** because:
1. A newly-deployed system has no data to purge — retention is only relevant after 1+ years of operation.
2. Implementing the purge job correctly requires careful testing (multi-tenant iteration, partial failure handling, progress logging). This is a distinct slice of work.
3. The configuration properties and `AuditRetentionService` interface are defined now so the design is in place.

Per-tenant retention (Option 4) is deferred to a future phase when the settings UI is built. The per-deployment configuration (Option 2) is sufficient for single-tenant deployments and for platform operators managing multi-tenant SaaS.

**Consequences**:
- Audit events accumulate without purging until the scheduled job is implemented (acceptable for initial deployment).
- Retention periods are deployment-wide, not per-tenant. This is the correct default for a managed SaaS platform where the operator sets compliance policy.
- The purge job (when implemented) will use `DELETE FROM audit_events WHERE occurred_at < NOW() - INTERVAL '? days'` — hard deletes, not soft deletes.
- The tiered retention approach (Option 3) remains available as a future optimization if storage costs become a concern at scale.
- GDPR erasure of individual member data (nullifying `actor_id`) is handled separately from retention — it's an on-demand operation, not a scheduled purge.
