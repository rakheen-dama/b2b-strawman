# `backend/` repo

The Spring Boot core. All domain logic, persistence, scheduled jobs, the multitenancy machinery, and the REST surface live here.

## Role

Spring Boot 4 application: ~40 feature packages, ~60 entities, ~280 REST endpoints, sealed `DomainEvent` bus wiring all modules, schema-per-tenant via Java 25 `ScopedValue`. Single deployable monolith — no microservice split. Internal API on `/internal/*` (API-key) for provisioning + member sync; public API on `/api/**` (JWT + capability-gated); customer-facing API on `/portal/**` (portal-JWT, bypasses gateway).

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/BackendApplication.java`

## Tech stack

- **Framework**: Spring Boot 4.0.2, Spring Security OAuth2 Resource Server. `→ backend/pom.xml`
- **Language**: Java 25 (records, sealed interfaces, pattern matching, `ScopedValue`).
- **ORM**: Hibernate 7 with `MultiTenantConnectionProvider<String>` (auto-detected — no `hibernate.multiTenancy` property).
- **Build**: Maven via `./mvnw` wrapper. `→ backend/mvnw`
- **Database**: PostgreSQL 16, Flyway migrations split global vs tenant. `→ backend/src/main/resources/db/migration/`
- **Test DB**: zonky embedded Postgres (real Postgres binary, no Docker).
- **No Lombok**, no Testcontainers (enforced by ArchUnit `TestConventionsTest`).

## Discovery report

`→ kazi-architecture/_discovery/A1-backend-map.md`

## Top-level directory map

```
backend/
├── src/main/java/io/b2mash/b2b/b2bstrawman/   — code root, ~40 feature packages
├── src/main/resources/                         — application*.yml, Flyway SQL, vertical-profile JSON, pack seed JSON, Thymeleaf dev-portal templates
├── src/test/java/                              — JUnit 5 tests (integration, service, architecture)
├── src/test/resources/                         — application-test.yml, test fixtures
├── archunit_store/                             — ArchUnit baseline files for `TestConventionsTest`
├── target/                                     — Maven build output (ignored)
├── pom.xml                                     — single-module Maven POM (no children)
├── mvnw / .mvn/                                — Maven wrapper
└── CLAUDE.md                                   — anti-patterns + filter chain order + test conventions
```

`→ backend/CLAUDE.md` is required reading before touching anything in this tree.

## Build & run commands

| Command | Purpose |
|---|---|
| `./mvnw verify` | **Full build + tests — the merge bar.** See `CLAUDE.md` quality gates §1. Targeted `-Dtest='*Foo*'` runs are inner-loop only. |
| `./mvnw spring-boot:run` | Dev server on port 8080, `local` profile (Docker-Compose Postgres at `b2mash.local:5432`). |
| `./mvnw spring-boot:test-run` | Dev server backed by **embedded Postgres** — no Docker needed. Useful when the compose stack is down. |
| `./mvnw -Pcoverage test` | Run tests with JaCoCo. Coverage is opt-in only — never re-add the plugin to the default `<build>` block. |
| `bash compose/scripts/dev-up.sh` | Start the supporting infra (Postgres, LocalStack, Mailpit, Keycloak) before `spring-boot:run`. |
| `bash compose/scripts/svc.sh restart backend` | Agent-managed restart with PID tracking + health-check wait. Use after Java changes (no hot-reload). |

`→ backend/pom.xml` for the `coverage` profile and `embedded-postgres-binaries-bom` pinning.

## Test stack

- **Framework**: JUnit 5, Mockito, Spring Boot Test, MockMvc, Spring REST Docs.
- **DB**: zonky embedded Postgres via `TestcontainersConfiguration.java` (despite the legacy class name, it does **not** use Testcontainers).
- **Storage**: `InMemoryStorageService` replaces S3/LocalStack — auto-registered as `@Primary`.
- **Email**: GreenMail singleton on `:13025` (set in `application-test.yml`). Use `GreenMailTestSupport.getInstance()` — never `new GreenMail(...)`.
- **No Testcontainers**: enforced by ArchUnit (`TestConventionsTest` fails the build on imports of `PostgreSQLContainer`, `LocalStackContainer`, `org.testcontainers.containers..`). Background: Docker-based containers caused cascading HikariPool failures.
- **Shared utilities**: `testutil/` — `TestMemberHelper`, `TestJwtFactory`, `TestEntityHelper`, `TestCustomerFactory`, `TestChecklistHelper`, `TestIds`. Private helpers for member sync / JWT / entity creation are forbidden.
- **Coverage**: JaCoCo gated behind `-Pcoverage` (per `feedback_test_speed_conventions`).

## Deployment unit

Single Docker image built from this repo. In the compose stack runs on port **8080**. In Keycloak mode the backend sits behind the gateway on **8443** (gateway does Keycloak OIDC login → SESSION cookie → TokenRelay). The portal (port 3002) calls `/portal/**` directly — it does **not** transit the gateway. `→ compose/docker-compose.yml`

The E2E mock-auth stack runs the same image with the `e2e` profile on port **8081** for Playwright testing — orgs are pre-provisioned, JWTs come from the in-stack mock IDP. `→ compose/docker-compose.e2e.yml`

Production: standalone JAR launched via `org.springframework.boot.loader.launch.JarLauncher` (never `java -jar`).

## Most-edited / hottest areas

Recent epic activity (per `TASKS.md` and `90-adr-index.md`):

| Area | Why it's hot |
|---|---|
| `automation/` | Phase 37 (rules engine) → ongoing — most recent: 515B/515C automation queue UI + executor + reapers. |
| `assistant/` | Phase 45 → 52 → 70 — LLM provider abstraction, tool framework, specialist personas, AI-write approval queue. |
| `verticals/` (esp. `verticals.legal`) | Phases 49 / 55 / 60 / 64 / 66 / 67 — vertical-profile system, legal tariffs, trust accounting, terminology overrides. |
| `integration/` | Phase 21 (port registry) → Phase 71 (Xero accounting — ADRs 272–279). |
| `audit/` | Phase 6 foundational; still active — every new module emits via `AuditEventBuilder`. |
| `multitenancy/` | Foundational, low edit-rate — `RequestScopes`, `TenantFilter`, schema connection provider. Touch with caution. |
| `event/` | Slow-evolving but **every** new module adds a sealed permit to `DomainEvent`. |
| `compliance/` | Customer lifecycle (PROSPECT→ACTIVE→DORMANT→OFFBOARDED), DSAR/PAIA, dataprotection. |
| `portal/` | Phase 39+ — magic-link auth, read-model event listeners. |

## Profile / environment quirks

| Profile | Where | DB | Auth | Notes |
|---|---|---|---|---|
| `local` | `./mvnw spring-boot:run` (developer machine) | Docker-Compose Postgres @ `b2mash.local:5432` | Keycloak / Clerk dev URLs | Full security chain. Dev portal Thymeleaf harness gated to `local`/`dev` (`@Profile({"local","dev"})`). |
| `test` | `./mvnw verify` | zonky embedded Postgres | Mock JWTs via `TestJwtFactory` | Never use `@ActiveProfiles("local")` in tests. |
| `keycloak` | `bash compose/scripts/svc.sh start all` | Compose Postgres | Real Keycloak realm | Backend sits behind gateway (8443). |
| `e2e` | `bash compose/scripts/e2e-up.sh` | Dedicated `e2e-postgres:5433` | Mock IDP @ `:8090` | Backend exposed on 8081 for Playwright. Pre-seeded `e2e-test-org`. |
| `dev` | Cloud | Neon dev branch | Clerk dev | AWS S3, structured logs. |
| `prod` | Cloud | Neon main | Clerk prod | Full observability, ECS logging. |

Embedded Postgres `:test-run` is the fastest local feedback loop — **no Docker needed**.

## Reference modules

Every page in `30-modules/` corresponds to one or more packages here. Cross-link map:

- Tenancy & provisioning → `multitenancy/`, `provisioning/`
- Identity & access → `security/`, `member/`, `orgrole/`, `invitation/`
- Projects / Tasks / Time → `project/`, `task/`, `timeentry/`, `expense/`, `capacity/`
- Customer lifecycle → `customer/`, `compliance/`, `checklist/`, `prerequisite/`
- Documents & templates → `document/`, `template/`, `s3/`
- Custom fields & views → `fielddefinition/`, `tag/`, `view/`
- Invoicing & billing → `invoice/`, `tax/`, `billingrun/`, `retainer/`
- Notifications & activity → `notification/`, `comment/`, `audit/`, `event/`
- Reporting → `reporting/`
- Automation → `automation/`
- Integrations & packs → `integration/`, `packs/`, `seeder/`
- Proposals & acceptance → `proposal/`, `acceptance/`
- Information requests → `informationrequest/`
- AI assistant → `assistant/`
- Vertical profiles → `verticals/`
- Portal → `portal/`
- Platform admin → `accessrequest/`, `billing/`

See `kazi-architecture/10-bounded-contexts.md` for the canonical 20-module map and ownership matrix.

## Key cross-cutting touchpoints

- Multi-tenancy: `kazi-architecture/20-cross-cutting/multitenancy.md` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java`).
- Multi-vertical: `kazi-architecture/20-cross-cutting/multi-vertical.md` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java`).
- Domain event bus: `kazi-architecture/30-modules/domain-events.md` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java`).
- Audit (in-transaction, not via bus): `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java`.
- Capability-based RBAC: `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/RequiresCapability.java`.

## Active ADRs touching repo-wide concerns

- ADR-272–279 — Phase 71 Xero accounting integration cluster (one-way push, polling, sibling payment-source port).
- ADR-033 — dev portal harness profile gate.
- See `kazi-architecture/90-adr-index.md` for the full clustered list.
