# ADR-215: Keycloak Deployment Strategy

**Status**: Proposed
**Date**: 2026-04-01
**Phase**: 56

## Context

Keycloak 26.5 is the identity provider for HeyKazi, handling OAuth2/OIDC authentication, organization membership, and user management. Locally, it runs as a Docker container on port 8180 with a PostgreSQL database. For production, we need a reliable deployment strategy that balances cost, control, and operational complexity at the 5-20 tenant scale.

Keycloak requires:
- PostgreSQL database for its internal data (realms, users, sessions, events)
- Custom theme (Keycloakify JAR) for branded login pages
- Realm configuration import (protocol mappers, client registrations, organization settings)
- HTTPS termination (can be handled by ALB)
- Stable hostname for OIDC discovery (`auth.heykazi.com`)

## Options Considered

### Option 1: ECS Fargate with Shared RDS Instance, Separate Database (Selected)

Run Keycloak as an ECS Fargate task. Create a separate PostgreSQL **database** (`kazi_keycloak`) within the same RDS instance that hosts the application database (`kazi`). This is a separate database (distinct JDBC URL: `jdbc:postgresql://host:5432/kazi_keycloak`), not a schema within the `kazi` database — providing full logical isolation while sharing the RDS instance cost.

- **Pros:** Cost-efficient -- no additional RDS instance ($30-60/month saved). Keycloak manages its own schema via Liquibase (built-in). Full control over Keycloak configuration, theme, and realm. Same deployment model as other services (ECS + ALB + CloudWatch). Single RDS instance simplifies backups and monitoring. Keycloak's database usage is lightweight at 5-20 tenants (few MB of data, low query volume).
- **Cons:** Shared RDS means Keycloak database issues could theoretically affect application database (connection exhaustion, CPU spikes). Keycloak upgrades require careful planning (Liquibase migrations may lock tables). Single point of failure if RDS goes down (both app and auth fail). Keycloak's connection pool competes with the application's pool.

### Option 2: ECS Fargate with Dedicated RDS Instance

Run Keycloak on ECS Fargate with its own dedicated RDS PostgreSQL instance.

- **Pros:** Complete isolation between Keycloak and application databases. Independent scaling, backup, and maintenance windows. Keycloak issues cannot affect application database. Independent RDS instance class (can use `db.t4g.micro` since Keycloak's DB usage is minimal).
- **Cons:** Additional RDS cost ($15-30/month for `db.t4g.micro`). Two RDS instances to monitor, patch, and back up. More complex Terraform configuration. At 5-20 tenants, this isolation is over-engineering -- Keycloak's database load is negligible.

### Option 3: Managed Keycloak Service (Cloud-IAM, Phase Two, etc.)

Use a managed Keycloak hosting provider instead of self-hosting.

- **Pros:** Zero operational overhead for Keycloak infrastructure. Managed upgrades, backups, and HA. Professional support available.
- **Cons:** Monthly cost ($50-200/month depending on provider, often per-realm or per-user pricing). Loss of full control over Keycloak configuration (some providers restrict SPI plugins, custom themes). Vendor lock-in to a specific Keycloak hosting provider. Custom Keycloakify theme may not be supported. Realm import/export may be restricted. Provider may not support the exact Keycloak version (26.5) or features we need (organizations, protocol mappers).

## Decision

**Option 1 -- ECS Fargate with a separate database in the shared RDS instance.**

## Rationale

1. **Cost-efficient at this scale.** A second RDS instance costs $15-30/month with no proportional benefit at 5-20 tenants. Keycloak's database usage is minimal -- a few tables with realm config, user records, and sessions. The shared RDS has ample headroom (`db.t4g.medium` with 4 GB RAM, 100 max connections).
2. **Database isolation via separate database name.** Creating a `kazi_keycloak` database (not just schema) within the same RDS instance provides logical separation. Keycloak connects with its own credentials that only have access to `kazi_keycloak`. The application connects to `kazi`. Neither can access the other's data.
3. **Full control over Keycloak.** Self-hosting means we control the exact version (26.5), custom theme (Keycloakify JAR), realm configuration (organizations, protocol mappers for org claims), and SPI providers. Managed services often restrict these customizations.
4. **Consistent deployment model.** All 5 services use the same ECS Fargate + ALB + CloudWatch pattern. Operations, monitoring, and deployment procedures are uniform.
5. **Upgrade path exists.** If Keycloak's database needs change significantly at scale (100+ tenants), migrating to a dedicated RDS instance is straightforward -- change the `KC_DB_URL` environment variable and provision a new instance.

## Consequences

- **Positive:** No additional RDS cost. Single instance to monitor and back up. Consistent deployment model across all services. Full control over Keycloak configuration and theming.
- **Negative:** Shared RDS failure takes down both application and authentication simultaneously. Keycloak Liquibase migrations during upgrades may briefly lock the RDS instance. Connection pool planning must account for both application (max 10) and Keycloak (default 20, configurable) connections.
- **Mitigations:** RDS Multi-AZ (production) provides failover for the shared instance. Keycloak connection pool can be limited via `KC_DB_POOL_INITIAL_SIZE` and `KC_DB_POOL_MAX_SIZE` (set to 5 initially, matching the low tenant count). Keycloak upgrades are performed during maintenance windows with the application in maintenance mode. Monitor RDS `DatabaseConnections` metric with a CloudWatch alarm (threshold: 80 connections out of ~100 available on `db.t4g.medium`).
