# Observability

## What this concern covers

Operational visibility into a running Kazi: structured logging, metrics, tracing, health checks, and the runtime status of the dozen-plus scheduled jobs that drive lifecycle automation. This page is **honest about a gap**: today, observability is mostly "structured logs to stdout, hope CloudWatch picks them up". A Prometheus exporter and a handful of custom Micrometer meters exist (job-queue and per-shard pool metrics — see below), but there is no distributed tracing, **no per-tenant metric tagging**, no integration-adapter latency, and no dashboards or alerting on top of what is exported. The system runs, logs, and emits a thin slice of metrics; it is not yet *observed* in the SRE sense.

The page documents what the codebase actually does so a future remediation phase has a baseline.

## What exists today

### Logging — structured (ECS) with MDC

Spring Boot's default logging stack (SLF4J + Logback) is wired with the ECS (Elastic Common Schema) JSON format and a per-request MDC populated by a dedicated filter. There is **no** custom `logback.xml` or `logback-spring.xml` in either `backend/src/main/resources/` or `gateway/src/main/resources/` — logging configuration is yaml-only.

`→ backend/src/main/resources/application.yml:63` — `logging.structured.format.console: ecs` and `logging.level.io.b2mash.b2b.b2bstrawman: INFO`.
`→ backend/src/main/resources/application-prod.yml:69` — same ECS console format under prod profile, root level `WARN`, app level `INFO`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantLoggingFilter.java:19` — MDC keys: `tenantId`, `userId`, `memberId`, `requestId`. Line 29 generates a fresh `requestId` per request; lines 46–49 clear the MDC in `finally` (load-bearing for virtual threads + servlet pool reuse).

Per-tenant log tagging is the one observability primitive the system does well: every log line emitted inside a request carries `tenantId` and `memberId` automatically.

### Health checks — `/actuator/health` only

Spring Boot Actuator is on the classpath. The **default** profile exposes only `health` and `info`; the **prod** profile also exposes `metrics` and `prometheus`, so a Prometheus scrape endpoint *is* configured in production (it just has nothing scraping it).

`→ backend/src/main/resources/application.yml:58` — `management.endpoints.web.exposure.include: health,info` (default profile — no metrics).
`→ backend/src/main/resources/application-prod.yml:61` — `management.endpoints.web.exposure.include: health,info,metrics,prometheus` (prod exposes both the JSON `metrics` endpoint and the `/actuator/prometheus` scrape endpoint in OpenMetrics text format).
`→ gateway/src/main/resources/application.yml:65` — gateway exposes only `health`. JMX explicitly disabled (line 55).
`→ gateway/src/main/resources/application-production.yml:20` — production gateway re-enables Redis health (`management.health.redis.enabled: true`) for the session store.
`→ gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java:49` — `/actuator/health` is `permitAll` through the gateway; no other actuator endpoint is publicly reachable.

`compose/scripts/svc.sh` polls `/actuator/health` to gate "is the service ready" during agent stack-up; that's the entire production-shaped use of actuator today.

### Metrics — Prometheus exporter present, custom meters thin, no per-tenant tagging

Spring Boot 4 pulls in Micrometer's core API, and there *is* a Prometheus registry plus two hand-rolled meter classes. The gap is breadth and tenant-dimensioning, not total absence:

- `micrometer-registry-prometheus` **is** a dependency in `backend/pom.xml` (`→ backend/pom.xml:57–59`), so the backend has a real `PrometheusMeterRegistry`. It is **not** in `gateway/pom.xml` — the gateway still has no metrics export.
- Two custom `MeterRegistry`-backed classes exist:
  - `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueMetrics.java` — per-job-type counters (`kazi_job_queue_enqueued_total` etc.) and execution/claim-wait `Timer`s, tagged by `job_type`.
  - `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardMetrics.java` — per-shard HikariCP pool gauges and tenant-count gauges, tagged by `shard_id`.
- No `@Timed` / `@Counted` / `@Observed` annotations in `backend/src/main/java` or `gateway/src/main/java` — instrumentation is the two explicit classes above only.
- **No per-tenant metric tagging.** Custom meters dimension by `job_type` and `shard_id`, never by `tenantId`. The MDC carries `tenantId` for *logs*, but it is not applied to the metric registry, so a noisy-neighbour tenant is invisible in metrics. There are also no request-rate / error-rate / integration-adapter-latency meters.

The `metrics` and `prometheus` actuator endpoints exposed in prod (`application-prod.yml:61`) surface the default JVM/HTTP timers plus the two custom meter sets. No system is configured to scrape them, and there are no dashboards or alerts built on top.

### Tracing — none

No Spring Cloud Sleuth, no Brave, no OpenTelemetry, no Zipkin/Jaeger. Distributed tracing across `frontend → gateway → backend` does not exist; the only correlation is the `requestId` MDC field set by `TenantLoggingFilter` (which is **not propagated** to or from the gateway — each service generates its own).

### Database observability

No slow-query log integration. No `spring.jpa.properties.hibernate.generate_statistics`. HikariCP pool state **is** exported per shard by `ShardMetrics` (`kazi_shard_connection_pool_active`/`_idle`/`_pending`, tagged by `shard_id`), so pool saturation is visible at the shard level — but lock waits and per-tenant query volume remain invisible at runtime.

### Scheduled-job observability

Work dispatched through the **job queue** *is* metered: `JobQueueMetrics` records per-`job_type` completed/failed/dead-letter counters and execution-duration timers, driven by `JobWorker` (a `SmartLifecycle` background worker). What remains unobserved are the **`@Scheduled` tenant-iteration pollers** themselves (24 of them) — the lifecycle-automation methods that run `forEachTenant` directly rather than enqueuing a job. Those are visible only as log lines: there is no shared `@Scheduled` wrapper that records start/end/error to a metric or to a `job_execution` table. If such a scheduler thread dies or `forEachTenant` throws, the only signal is absence of the periodic log line.

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
- **No DB-level slow-query log integration.** Hibernate `generate_statistics` is off. (HikariCP pool gauges *are* exported per shard by `ShardMetrics`, but there is no query-level timing.) A slow-rendering report or a missing index is found by user complaint, not by query log.

## What a future plan would look like

Sign-posts only — actual proposals belong in an ADR + phase doc, not here.

- **Extend the existing Prometheus exporter with `tenantId` as a tag, and broaden meter coverage.** The Prometheus registry already exists; the gap is dimensioning and breadth. A `MeterFilter` could attach the request scope (`tenantId`) from the existing MDC, and a bounded set of additional high-value meters (request rate, request latency, error rate, integration-adapter latency) would round out what the current job-queue and shard meters start. Keep cardinality manageable.
- **`@Scheduled` wrapper that emits start / end / error metrics.** A small `MeasuredScheduledRunner` around `TenantScopedRunner.forEachTenant` would give per-job per-tenant success counters and last-run-at gauges.
- **Per-integration-adapter timer + error counter.** The adapter resolution point (`IntegrationRegistry.resolve(...)`, see `20-cross-cutting/integration-ports.md`) is the natural cross-cut: one decorator covers all adapters.
- **Custom `HealthIndicator`s.** At minimum: tenant-schema reachability sample, Flyway baseline-applied check, integration-adapter ping (where the adapter supports a cheap probe), Redis session store reachability (gateway).
- **Structured JSON logs + correlation header propagation.** Add a `X-Request-Id` (or `traceparent`) at the gateway, forward it through `TokenRelay=` via a filter, accept it in the backend filter — replaces the per-service-fresh `requestId`.

These are signposts, not a roadmap. The honest current posture is: observability is the standout gap in the cross-cutting concerns of the system.
