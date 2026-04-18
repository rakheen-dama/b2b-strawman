# ADR-253: Portal Vertical Surfaces Are Read-Model Extensions, Not New Entities

**Status**: Accepted

**Context**:

Phase 68 introduces four new portal surfaces — trust balance, retainer hour-bank, deadlines, notification preferences — that display data originating firm-side (trust transactions, retainer agreements, filing statuses, etc.). The natural question is where that data should live as the portal reads it. The codebase already answered this question for the first four portal surfaces (projects, documents, invoices, proposals) with [ADR-030](ADR-030-portal-read-model.md) ("portal read-model") and [ADR-031](ADR-031-separate-portal-read-model-schema.md) ("separate portal read-model schema"): a distinct `portal.*` schema, populated by after-commit sync handlers, read via a separate `JdbcClient`. Phase 68 has to choose whether to continue that pattern or diverge from it for the new surfaces.

Two alternative shapes are plausible. The first is creating new JPA entities in the portal schema — effectively turning the portal schema into a first-class ORM citizen alongside the tenant schemas. The second is live cross-schema queries from portal controllers directly into firm schemas (no read model at all — controllers join across `tenant_acme.trust_transactions` etc.). Both shapes have surface appeal (JPA would keep Java code consistent; live queries would keep data fresh without sync latency) but break isolation invariants the codebase depends on.

**Options Considered**:

1. **Read-model extension via `JdbcClient`** — Continue the existing pattern. Six new raw SQL tables in `portal.*` schema (global Flyway V19), populated by sync services listening to firm-side domain events, read by `PortalTrust/Retainer/DeadlineReadModelService` via `@Qualifier("portalJdbcClient") JdbcClient`.
   - Pros:
     - Matches the pattern in use since Phase 7 — zero new architectural surface.
     - Portal data source is already configured with `search_path portal, public`; queries cannot accidentally touch firm schemas.
     - Reads are fast (denormalised columns, purpose-built indexes, no cross-schema joins).
     - Portal can be taken down or scaled independently of firm schemas.
     - Firm-side refactors don't risk breaking portal controllers — the sync handler is the only coupling point.
   - Cons:
     - Sync lag (milliseconds — after-commit handler latency) — acceptable for client-facing data.
     - Duplicate storage (~10% of firm-side row size for the portal-relevant subset).
     - Per-entity sync service is boilerplate-y.

2. **New JPA entities in the portal schema** — Introduce `@Entity` classes mapped to `portal.portal_trust_balance` etc., managed by a secondary Hibernate `SessionFactory`.
   - Pros:
     - Java consistency (`TrustTransactionRepository` everywhere vs. `JdbcClient` in one place).
     - Hibernate caching.
   - Cons:
     - Multi-`SessionFactory` Hibernate setups are fragile — Phase 13 specifically eliminated multi-schema Hibernate entanglement.
     - The read-model is literally a denormalised projection — entity relationships (`@ManyToOne` etc.) don't apply; JPA bloats the shape for no benefit.
     - Tenant `search_path` binding interferes with portal `search_path` in ways that are hard to debug.

3. **Live cross-schema queries from portal controllers** — No read model at all. Portal controllers join from the portal schema (for `portal_contacts`) into tenant schemas (for `trust_transactions` etc.).
   - Pros:
     - Always-fresh data.
     - Zero storage duplication.
   - Cons:
     - Breaks [ADR-031](ADR-031-separate-portal-read-model-schema.md) — the portal data source would need access to every tenant schema.
     - Cross-schema joins on Postgres require CTE-ish workarounds; not clean.
     - Every firm-side schema change risks breaking portal queries.
     - Portal availability is now coupled to every tenant schema's availability.
     - SQL path from portal user → any tenant's firm data is the opposite of the isolation property the portal is designed around.

**Decision**: Option 1 — read-model extension via `JdbcClient`. No new portal-only JPA entities. No cross-schema queries.

**Rationale**:

**Tenant isolation is the foundational property.** The portal schema is a distinct data source with a distinct `search_path`. This property is non-negotiable: a compromised portal controller must not be able to reach firm-side data. Options 2 and 3 weaken or invert that property.

**Firm-side authoritative, portal read-only.** Clients can't mutate trust balances or retainer consumption — the firm is the system of record. A read-model projection is the natural shape for this authority direction. Writes happen firm-side; sync pushes the projection; portal reads.

**Sync lag is acceptable.** The longest after-commit delay we've observed on the existing surfaces is ~100ms. For a client staring at a trust balance, 100ms lag after a firm user approves a transaction is below perceptual threshold.

**Pattern reuse is the dominant consideration.** The existing code base has five years of surface area built around the portal read-model pattern — event handlers, sync services, JdbcClient beans, test helpers. Adding new entity types to this pattern is cheap; inventing a new pattern is expensive and creates two ways of doing the same thing.

**Consequences**:

- Phase 68 ships zero new JPA `@Entity` classes. Every new backing table is raw SQL in `V19__portal_vertical_parity.sql` under `global/`.
- Every new portal controller injects `@Qualifier("portalJdbcClient") JdbcClient` (directly or via a read-model service).
- Every new firm-side event that needs portal visibility requires a new `@TransactionalEventListener(phase = AFTER_COMMIT)` handler that calls the sync service.
- Read-model is idempotent: sync services use `INSERT ... ON CONFLICT ... DO UPDATE`. Replaying events is safe.
- Backfill is first-class: any new module activation triggers a one-shot populate. Admin-accessible via `POST /internal/portal-resync/*`.
- Future portal surfaces follow the same pattern — this ADR sets the rule for Phase 69 and beyond.

**Related**:

- [ADR-030](ADR-030-portal-read-model.md) — Original portal read-model.
- [ADR-031](ADR-031-separate-portal-read-model-schema.md) — Separate schema (baseline invariant).
- [ADR-064](ADR-064-dedicated-schema-for-all-tenants.md) — Phase 13 dedicated-schema-only (why Hibernate-across-schemas is off-limits).
- [ADR-109](ADR-109-portal-read-model-sync-granularity.md) — Sync granularity.
- [ADR-254](ADR-254-portal-description-sanitisation.md) — Content sanitisation inside the sync pipeline.
