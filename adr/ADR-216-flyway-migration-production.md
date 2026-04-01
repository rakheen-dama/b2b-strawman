# ADR-216: Flyway Migration Strategy for Production

**Status**: Proposed
**Date**: 2026-04-01
**Phase**: 56

## Context

The HeyKazi backend uses Flyway to manage database migrations across multiple tenant schemas. Each tenant gets a dedicated PostgreSQL schema (`tenant_<hash>`), and Flyway runs the full migration set (currently V1--V84) against each schema. On backend startup, the application:

1. Runs global migrations against the `public` schema (V1--V16)
2. Discovers all tenant schemas via the `org_schema_mapping` table
3. Runs tenant migrations against each schema sequentially

The production profile (`application-prod.yml`) currently has `spring.flyway.enabled: false`. This means migrations are not automated -- they must be handled manually or via a separate process. This was likely set when the infrastructure was first scaffolded (before multi-tenant Flyway was implemented) and never updated.

At the 5-20 tenant scale, running V1--V84 against all schemas takes approximately 30-120 seconds depending on how many schemas exist and how many new migrations need to run (most will be no-ops after the first deploy).

## Options Considered

### Option 1: Re-enable Flyway on App Startup (Selected)

Set `spring.flyway.enabled: true` in the production profile. Flyway runs on every backend startup. The ECS health check grace period is extended to allow time for migrations to complete before the task is considered healthy.

- **Pros:** Simplest approach -- no additional infrastructure or orchestration. Migrations are guaranteed to run before the application serves traffic (health check only passes after Flyway completes). Follows the standard Spring Boot pattern. Works at 5-20 tenant scale without performance concerns. Rolling ECS deployments ensure zero downtime (old task serves traffic while new task migrates).
- **Cons:** Backend startup time increases with the number of tenants. At 20 tenants with 84 migrations each, worst-case startup is ~2 minutes (most migrations are no-ops). If a migration fails, the task fails to start and ECS circuit breaker triggers. Migrations hold database connections during startup.

### Option 2: Separate ECS Migration Task

Run Flyway as a standalone ECS task (one-off, not a service) before deploying the new backend. The deploy workflow: (1) run migration task, (2) wait for completion, (3) deploy new backend image.

- **Pros:** Separates migration from application startup. Migration failures are visible as a distinct step in the deploy pipeline. Backend starts faster (no Flyway overhead). Can use a dedicated task with higher timeout.
- **Cons:** Adds complexity to the CI/CD pipeline (new ECS task definition, orchestration logic in GitHub Actions). Requires the migration task to have the same environment variables and database access as the backend. Race condition risk: if backend starts before migration task completes (misconfigured dependency). Two task definitions to maintain for the backend (app + migration). Over-engineering at 5-20 tenant scale.

### Option 3: Init Container Pattern

Use ECS "essential" container ordering -- a migration container runs first, and the backend container starts only after it exits successfully.

- **Pros:** Migration runs within the same task as the application. Clear sequencing (init container exits, then app container starts). No CI/CD pipeline changes.
- **Cons:** ECS Fargate does not natively support init containers in the same way as Kubernetes. The workaround (non-essential container with `dependsOn` ordering) is fragile and poorly documented. Adds complexity to the task definition for minimal benefit. If the init container fails, the entire task fails (same behavior as Option 1, but with more moving parts).

## Decision

**Option 1 -- Re-enable Flyway on app startup with extended health check grace period.**

## Rationale

1. **Simplicity wins at this scale.** With 5-20 tenants and 84 migration files, Flyway runs in under 2 minutes. This is well within ECS health check grace period tolerances. Adding a separate migration task or init container is over-engineering for a problem that doesn't exist yet.
2. **Zero-downtime deployments are built-in.** ECS rolling updates with `minimumHealthyPercent=100` and `maximumPercent=200` mean the old task continues serving traffic while the new task starts and runs migrations. Traffic only shifts to the new task after it passes health checks.
3. **Forward-compatible migrations ensure safety.** All Flyway migrations in the codebase are additive -- new tables, new columns (nullable or with defaults), new indexes. Old code running against a new schema works correctly. This means the transition window (old task + new schema) is safe.
4. **Health check grace period provides adequate buffer.** Setting the ALB health check grace period to 180 seconds gives Flyway ample time to migrate all schemas before the first health check. The backend only registers as healthy (`/actuator/health` returns 200) after Flyway completes.
5. **Failure handling is automatic.** If a migration fails, the backend fails to start, the health check never passes, and the ECS deployment circuit breaker triggers automatic rollback to the previous task definition revision. No manual intervention needed.

## Consequences

- **Positive:** No additional infrastructure or CI/CD complexity. Migrations run automatically on every deploy. Zero-downtime deployments. Automatic rollback on migration failure. Standard Spring Boot pattern that any developer understands.
- **Negative:** Backend startup time grows linearly with tenant count. At 100+ tenants (future), startup could exceed 5 minutes and require a different approach. Migrations hold 2 database connections during startup (from the migration datasource pool).
- **Mitigations:** Monitor startup time via CloudWatch (track time from task start to first healthy health check). Set a threshold alarm if startup exceeds 3 minutes. When the tenant count approaches 50+, re-evaluate with Option 2 (separate migration task). The migration datasource has `maximum-pool-size: 2` to limit connection usage during Flyway runs. Consider parallel schema migration (Flyway's `spring.flyway.group` or custom executor) if startup time becomes an issue.
