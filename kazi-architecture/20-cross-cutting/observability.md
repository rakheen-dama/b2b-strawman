# Observability

## What this concern covers

Operational visibility into a running Kazi: structured logging, metrics, tracing, health checks, and the runtime status of the dozen-plus scheduled jobs that drive lifecycle automation. This page is **honest about a gap**: today, observability is essentially "structured logs to stdout, hope CloudWatch picks them up". There is no metrics export, no distributed tracing, no per-tenant resource accounting, and no scheduler success/failure surface beyond log lines. The system runs and logs; it is not yet *observed* in the SRE sense.

The page documents what the codebase actually does so a future remediation phase has a baseline.

## What exists today

### Logging — structured (ECS) with MDC

Spring Boot's default logging stack (SLF4J + Logback) is wired with the ECS (Elastic Common Schema) JSON format and a per-request MDC populated by a dedicated filter. There is **no** custom `logback.xml` or `logback-spring.xml` in either `backend/src/main/resources/` or `gateway/src/main/resources/` — logging configuration is yaml-only.

`→ backend/src/main/resources/application.yml:63` — `logging.structured.format.console: ecs` and `logging.level.io.b2mash.b2b.b2bstrawman: INFO`.
`→ backend/src/main/resources/application-prod.yml:69` — same ECS console format under prod profile, root level `WARN`, app level `INFO`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantLoggingFilter.java:19` — MDC keys: `tenantId`, `userId`, `memberId`, `requestId`. Line 29 generates a fresh `requestId` per request; lines 46–49 clear the MDC in `finally` (load-bearing for virtual threads + servlet pool reuse).

Per-tenant log tagging is the one observability primitive the system does well: every log line emitted inside a request carries `tenantId` and `memberId` automatically.

### Health checks — `/actuator/health` only

Spring Boot Actuator is on the classpath, but only `health` (and `info`) is exposed. No Prometheus scrape endpoint is configured.

`→ backend/src/main/resources/application.yml:51` — `management.endpoints.web.exposure.include: health,info`.
`→ backend/src/main/resources/application-prod.yml:57` — `management.endpoints.web.exposure.include: health,info,metrics` (prod adds `metrics` but **no scrape format** — the raw JSON Spring endpoint, not Prometheus text).
`→ gateway/src/main/resources/application.yml:65` — gateway exposes only `health`. JMX explicitly disabled (line 55).
`→ gateway/src/main/resources/application-production.yml:20` — production gateway re-enables Redis health (`management.health.redis.enabled: true`) for the session store.
`→ gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java:49` — `/actuator/health` is `permitAll` through the gateway; no other actuator endpoint is publicly reachable.

`compose/scripts/svc.sh` polls `/actuator/health` to gate "is the service ready" during agent stack-up; that's the entire production-shaped use of actuator today.

### Metrics — none custom, no exporter

Spring Boot 4 transitively pulls in Micrometer's core API, but:

- No `MeterRegistry` is configured beyond the default in-memory simple registry.
- No `micrometer-registry-prometheus` dependency in either `backend/pom.xml` or `gateway/pom.xml` (verified by `grep -E "micrometer|prometheus" pom.xml` returning empty).
- No `@Timed` / `@Counted` / `@Observed` annotations in `backend/src/main/java` or `gateway/src/main/java` (grep returns empty).
- No custom `MeterRegistry` injection — no per-tenant counters, no per-job timers, no per-adapter latency histograms.

The `metrics` actuator endpoint exposed in prod (`application-prod.yml:61`) returns the default JVM/HTTP timers in Spring's bespoke JSON format. No system is configured to scrape it.

### Tracing — none

No Spring Cloud Sleuth, no Brave, no OpenTelemetry, no Zipkin/Jaeger. Distributed tracing across `frontend → gateway → backend` does not exist; the only correlation is the `requestId` MDC field set by `TenantLoggingFilter` (which is **not propagated** to or from the gateway — each service generates its own).

### Database observability

No slow-query log integration. No `spring.jpa.properties.hibernate.generate_statistics`. No HikariCP metrics export. Connection pool saturation, lock waits, and per-tenant query volume are invisible at runtime.

### Scheduled-job observability

Job execution is visible only as log lines. There is no shared `@Scheduled` wrapper that records start/end/error to a metric or to a `job_execution` table. Each scheduler logs free-form. If a scheduler thread dies or `forEachTenant` throws, the only signal is absence of the periodic log line.

## The pattern: scheduled job + tenant iteration

Every `@Scheduled` method in the backend follows the same shape: `forEachTenant` over `OrgSchemaMappingRepository.findAll()`, bind tenant scope, do work, log progress.

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java:37` — `forEachTenant(BiConsumer<String, String>)` — the single iteration primitive.

Inventory of `@Scheduled` jobs (verified via `grep -rn "@Scheduled" backend/src/main/java`):

| Job | Cadence | Anchor |
|---|---|---|
| `AutomationScheduler` (delayed actions) | fixedDelay 15min | `→ backend/.../automation/AutomationScheduler.java:61` |
| `AutomationScheduler` (cron triggers) | fixedDelay 60s | `→ backend/.../automation/AutomationScheduler.java:78` |
| `FieldDateScannerJob` | cron `0 0 6 * * *` (daily 06:00 UTC) | `→ backend/.../automation/FieldDateScannerJob.java:60` |
| `AcceptanceExpiryProcessor` | fixedDelay 1h | `→ backend/.../acceptance/AcceptanceExpiryProcessor.java:27` |
| `ProposalExpiryProcessor` | fixedRate 1h | `→ backend/.../proposal/ProposalExpiryProcessor.java:57` |
| `RecurringScheduleExecutor` (project schedules) | daily 02:00 UTC | `→ backend/.../schedule/RecurringScheduleExecutor.java` |
| `RequestReminderScheduler` (information requests) | ~6h fixedRate | `→ backend/.../informationrequest/RequestReminderScheduler.java` |
| `TimeReminderScheduler` | weekly per `OrgSettings.timeReminderDay` | `→ backend/.../schedule/TimeReminderScheduler.java` |
| `DormancyScheduledJob` | daily 02:00 | `→ backend/.../compliance/DormancyScheduledJob.java:38` |
| `SubscriptionExpiryJob` (three jobs) | cron 03:00 / 03:05 / 03:10 daily | `→ backend/.../billing/SubscriptionExpiryJob.java:54` |
| `MagicLinkCleanupService` | hourly | `→ backend/.../portal/MagicLinkCleanupService.java:34` |
| `PortalDigestScheduler` | per-org cadence | `→ backend/.../portal/notification/PortalDigestScheduler.java` |
| `AiInvocationExpirySweeper` (reaper) | cron `0 0 3 * * *` | `→ backend/.../assistant/invocation/AiInvocationExpirySweeper.java:64` |
| `AiInvocationReaper` | reaper companion | `→ backend/.../assistant/invocation/AiInvocationReaper.java` |
| `CourtDateReminderJob` (legal vertical) | daily | `→ backend/.../verticals/legal/courtcalendar/CourtDateReminderJob.java` |

The reaper sub-pattern (ADR-271 — scheduled trigger extension) is the explicit decision *not* to cascade-delete: idempotent sweeps clean up aged-out invocation/token rows. See `30-modules/automation.md` and `30-modules/ai-assistant.md` for the per-module detail.

## Where the gap is

Stated plainly. None of the following exist:

- **SLO definitions** — no documented latency, availability, or freshness target for any user-facing flow.
- **Per-tenant business metrics** — no invoice-emission rate per tenant, no time-entry submission rate, no document-upload volume. Multi-tenant noisy-neighbour is invisible.
- **Integration adapter health/latency** — Xero (Phase 71), SendGrid, S3, the AI specialists — none expose a per-call duration histogram or error counter. ADR-277's polling reconciler runs blind.
- **Scheduler success/failure metric** — if `AutomationScheduler` skips a beat or `forEachTenant` throws on tenant N, there is no alert. The next user-facing signal is "my automation didn't fire."
- **Audit-event volume monitor** — append-only audit means audit table growth is unbounded in principle; no metric tracks per-tenant audit-event rate (the input to retention/cost decisions).
- **Error-rate dashboard** — no aggregation; errors are individual log lines in CloudWatch.
- **Per-tenant resource accounting** — no per-tenant CPU / DB-connection / S3-bandwidth attribution.
- **Tenant schema reachability check** — `/actuator/health` reports the *pool* is up; it does not validate that any tenant schema's `search_path` works or that Flyway has baselined every tenant.
- **AI specialist token-cost tracking** — flagged in `30-modules/ai-assistant.md`; per-invocation token counts are not aggregated to a metric, only logged.
- **Email rate-limit observability** — `30-modules/notifications.md` flags this; SMTP/BYOAK/platform-aggregate rate limits in `application.yml:78–81` exist as configured ceilings without a meter showing approach.

The first sign of trouble is a user report. That is the operational reality.

## Modules affected

All of them — observability is universally thin. The modules where the gap *bites hardest*:

- **`30-modules/automation.md`** — schedulers are the heart of automation; lateness is invisible.
- **`30-modules/notifications.md`** — email send rate limits and bounce/complaint feedback have no surface beyond logs.
- **`30-modules/audit.md`** — append-only growth has no monitor.
- **`30-modules/ai-assistant.md`** — token costs are per-tenant cost centres flying blind.
- **Integration ports (`20-cross-cutting/integration-ports.md`)** — every external adapter (Xero, SendGrid, S3, AI specialists) lacks a latency / error-rate metric.

## ADRs

No ADR directly governs observability — this is a gap in the decision record, not a documented choice. The only adjacent ADRs:

- **ADR-271** — scheduled trigger extension and reaper pattern. Establishes the scheduler shape but does not require execution metrics.
- **ADR-067** (audit emission style) — chooses explicit audit calls over interceptor magic. Indirectly relevant: audit-event rate is the closest thing to a per-tenant business meter the system has.

A future "production observability" ADR is a logical next step.

## Known fragilities / open questions

- **First-incident discoverability is user-driven.** Without metrics or alerts, a degraded scheduler or a stuck integration is found by support tickets, not by SRE.
- **`requestId` does not cross the gateway boundary.** The gateway's `TokenRelay=` filter forwards the JWT but no correlation header. The backend's `TenantLoggingFilter` (`TenantLoggingFilter.java:29`) generates a fresh UUID. Cross-service trace stitching is not possible today.
- **Scheduled-job lateness is invisible.** A `fixedDelay` job that hangs on tenant N stops processing tenants N+1..M. There is no per-tenant per-job last-success-at projection that an alert could compare to "now − 2× period".
- **Health checks are coarse.** `/actuator/health` reports DataSource UP, but does not validate `search_path` resolution for any specific tenant — a half-provisioned schema (Flyway baseline missing on a tenant) is not detected by health.
- **Audit growth is unmonitored.** With per-tenant append-only audit and no retention sweep specific to audit (only DSAR-driven anonymization), table size is a silent cost.
- **AI invocation cost is per-tenant but not metered.** The AI invocation table is sweepable (`AiInvocationExpirySweeper`) but the dollar cost of LLM calls is not aggregated to any per-tenant counter.
- **No DB-level slow-query log integration.** Hibernate `generate_statistics` is off; HikariCP metrics not exported. A slow-rendering report or a missing index is found by user complaint, not by query log.

## What a future plan would look like

Sign-posts only — actual proposals belong in an ADR + phase doc, not here.

- **Micrometer + Prometheus exporter, with `tenantId` as a tag.** Naturally fits the existing MDC; `MeterFilter` can attach the scope. A bounded set of high-value meters (request rate, request latency, error rate, integration-adapter latency, scheduled-job duration) keeps cardinality manageable.
- **`@Scheduled` wrapper that emits start / end / error metrics.** A small `MeasuredScheduledRunner` around `TenantScopedRunner.forEachTenant` would give per-job per-tenant success counters and last-run-at gauges.
- **Per-integration-adapter timer + error counter.** The adapter resolution point (`IntegrationRegistry.resolve(...)`, see `20-cross-cutting/integration-ports.md`) is the natural cross-cut: one decorator covers all adapters.
- **Custom `HealthIndicator`s.** At minimum: tenant-schema reachability sample, Flyway baseline-applied check, integration-adapter ping (where the adapter supports a cheap probe), Redis session store reachability (gateway).
- **Structured JSON logs + correlation header propagation.** Add a `X-Request-Id` (or `traceparent`) at the gateway, forward it through `TokenRelay=` via a filter, accept it in the backend filter — replaces the per-service-fresh `requestId`.

These are signposts, not a roadmap. The honest current posture is: observability is the standout gap in the cross-cutting concerns of the system.
