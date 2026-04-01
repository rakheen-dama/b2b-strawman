# Series 2: "Multi-Tenant from Scratch"

Production multi-tenant architecture with PostgreSQL, Spring Boot 4, and Hibernate 7. Real code from a real system.

## Posts

| # | Title | Status | Words |
|---|-------|--------|-------|
| 01 | [Schema-Per-Tenant vs. RLS](01-schema-per-tenant-vs-rls.md) | Draft | ~2,800 |
| 02 | [ScopedValue Over ThreadLocal](02-scopedvalue-over-threadlocal.md) | Draft | ~2,500 |
| 03 | [Tenant Provisioning](03-tenant-provisioning.md) | Draft | ~2,400 |
| 04 | [83 Entities, Zero Tenant Columns](04-zero-tenant-columns.md) | Draft | ~2,200 |
| 05 | [Flyway Dual-Path Migrations](05-flyway-dual-path-migrations.md) | Draft | ~2,400 |
| 06 | [Keycloak Identity Layer](06-keycloak-identity-layer.md) | Draft | ~2,300 |
| 07 | [Customer Lifecycle State Machine](07-customer-lifecycle-state-machine.md) | Draft | ~2,400 |
| 08 | [Neon Serverless Postgres](08-neon-serverless-postgres.md) | Draft | ~2,200 |

**Total: ~19,200 words across 8 posts**

## Series Arc

- **01**: The anchor post. RLS vs. schema-per-tenant, tried both, why schema won. High SEO value.
- **02**: Modern Java deep-dive. ScopedValue replaces ThreadLocal, virtual-thread ready.
- **03**: The provisioning pipeline. Webhook to working schema in 2s. Idempotency, retry, seeding.
- **04**: The developer experience payoff. Zero boilerplate entities, safe findById(), native queries.
- **05**: Migration management. Dual paths, startup runner, the trigger bug, renumbering story.
- **06**: Auth integration. Keycloak JWT, capability RBAC, mock IDP for testing, Clerk migration lessons.
- **07**: Domain modeling. State machines, action gating, compliance checklists, vertical-specific packs.
- **08**: Infrastructure. Neon + PgBouncer + HikariCP tuning. What works, what doesn't, pricing reality.

## Code Examples Source

All code examples are taken from the actual codebase:
- `RequestScopes.java` — ScopedValue declarations
- `TenantFilter.java` — JWT to schema resolution
- `SchemaMultiTenantConnectionProvider.java` — search_path management
- `TenantProvisioningService.java` — full provisioning pipeline
- `CustomerLifecycleGuard.java` — action gating
- `LifecycleStatus.java` — state machine transitions
- `FlywayConfig.java` — dual migration paths
- `DataSourceConfig.java` — dual datasource setup

## Publishing Plan

- Frequency: Biweekly (alternating with Series 1)
- Launch with Post 01 alongside Series 1 Post 01
- Cross-post to Dev.to, Hashnode (high search traffic for multi-tenant topics)

## Before Publishing Checklist

- [ ] Verify all code examples match current codebase
- [ ] Add architecture diagrams (data flow, filter chain, provisioning pipeline)
- [ ] Add schema count benchmarks if available
- [ ] Verify Neon pricing/feature claims against current docs
- [ ] Review for accuracy: HikariCP settings, Flyway behavior, PgBouncer modes
