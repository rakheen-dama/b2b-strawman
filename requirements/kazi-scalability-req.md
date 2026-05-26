# Kazi Backend Scalability & Resilience Requirements

## Background

The Kazi backend is a Spring Boot 4 / Java 25 monolith with schema-per-tenant multitenancy, 60+ domain packages, 95+ controllers, 100+ tenant-scoped tables, and 20 scheduled jobs running in a single JVM.

A comprehensive scalability assessment identified three tiers of improvement:
- **Tier A** (committed `fbd163823`): Pool sizing, Prometheus metrics, bounded AI executor, ShedLock leader election — all shipped.
- **Tier B** (this document): Monolith hardening — resilience patterns, distributed caching, async processing, event reliability.
- **Tier C** (this document): Selective microservice extraction via the strangler pattern.

This document specifies the requirements for Tier B and Tier C.

---

## Current State (Post Tier A)

| Dimension | Before Tier A | After Tier A |
|-----------|--------------|--------------|
| **Connection pool** | 10 max (5 concurrent AI users = exhaustion) | 25 max |
| **AI concurrency** | Unbounded (OOM risk) | Semaphore-capped at 20, 429 when full |
| **SSE timeout** | 300s (orphaned emitters) | 150s with idempotent release |
| **Unbounded queries** | 7× `Pageable.unpaged()` | Capped at 10,000 rows |
| **Observability** | health + info only | Prometheus metrics endpoint |
| **Health checks** | Default Spring Boot | Connection pool utilization + S3 reachability |
| **Scheduled jobs** | All replicas run all jobs | ShedLock leader election on 19 jobs |

### Remaining Gaps

| Gap | Impact | Addressed By |
|-----|--------|-------------|
| Zero circuit breakers for external services | Cascade failures from LLM/Xero/Email outages | B2 |
| Sequential tenant iteration in scheduled jobs | O(N tenants) per sweep; 1000 tenants = 30+ min sweeps | B3 |
| Fire-and-forget domain events (no retry, no dead letter) | Silent data loss on Xero sync, email, portal sync | B1 |
| In-process Caffeine cache only | N replicas = N cold caches; rate limits reset on deploy | B5 |
| In-memory AI confirmations (`ConcurrentHashMap`) | Lost on pod crash; wrong-pod routing in multi-pod | B6 |
| Synchronous PDF generation on request threads | CPU-intensive, blocks interactive traffic | B4 |
| In-memory ZIP for data exports | 50-100 MiB per export, OOM risk | B7 |
| No message broker for cross-service events | Blocks all service extractions | C (prereq) |

---

## Tier B: Monolith Hardening

### B1. Transactional Outbox Pattern

**Objective**: Replace fire-and-forget `@TransactionalEventListener(AFTER_COMMIT)` with a durable, retryable event delivery mechanism.

**Problem**: 44 domain events are published via Spring's `ApplicationEventPublisher` and consumed by `AFTER_COMMIT` listeners. If a listener fails (e.g., Xero API down when invoice approved, SMTP unavailable when proposal sent), the event is silently lost. There is no retry, no dead-letter queue, and no visibility into failed deliveries.

**Requirements**:

1. Create an `outbox_events` table in each tenant schema:
   - Columns: `id (UUID)`, `event_type (VARCHAR)`, `payload (JSONB)`, `status (VARCHAR: PENDING, PROCESSING, COMPLETED, DEAD_LETTER)`, `retry_count (INT)`, `created_at`, `processed_at`, `error_message (TEXT)`
   - Index on `(status, created_at) WHERE status = 'PENDING'`

2. Provide an `OutboxEventPublisher` that writes events into `outbox_events` within the same transaction as the domain state change (atomicity guarantee).

3. Implement an outbox poller (ShedLock-protected `@Scheduled` job) that:
   - Reads PENDING events in batch (configurable batch size, default 100)
   - Dispatches each to the appropriate handler
   - Marks COMPLETED on success
   - Increments `retry_count` and schedules retry with exponential backoff on failure
   - Marks DEAD_LETTER after configurable max retries (default 5)

4. Migration path must be gradual:
   - Phase 1: Dual-write (both outbox and existing AFTER_COMMIT listeners active)
   - Phase 2: Cutover one listener at a time, starting with `AccountingSyncEventListener` and `NotificationEventHandler`
   - Phase 3: Remove legacy AFTER_COMMIT listeners after parity verification

5. Dead-letter events must be queryable via an admin API for manual retry/inspection.

**Effort**: 4-6 weeks | **Impact**: CRITICAL (reliability + foundation for Tier C)
**Dependencies**: A7 ShedLock (done)

---

### B2. Circuit Breakers via Resilience4j

**Objective**: Prevent cascade failures when external services are unavailable.

**Problem**: Zero circuit breakers exist. The only `@Retryable` is on tenant provisioning. If Anthropic goes down, every AI request blocks for 120 seconds consuming virtual threads and DB connections. If Xero rate-limits, the sync worker retries every 30 seconds and hammers a dead API.

**Requirements**:

1. Add `resilience4j-spring-boot3` dependency.

2. Wrap the following external service calls with circuit breakers:
   - **Anthropic LLM** (`AnthropicLlmProvider`): 5 failures in 60s opens circuit, 30s half-open. Fallback: return "AI service temporarily unavailable" SSE event.
   - **Xero API** (`XeroApiClient` / `XeroAccountingProvider`): 3 failures in 60s opens circuit, 60s half-open. Fallback: mark sync entry as FAILED_RETRYING.
   - **S3 Storage** (`S3StorageAdapter`): 5 failures in 60s opens circuit, 30s half-open. Fallback: throw `StorageUnavailableException`.
   - **SMTP/SendGrid** (`SmtpEmailProvider`, `SendGridEmailProvider`): 5 failures in 60s opens circuit, 60s half-open. Fallback: log delivery failure, return error to caller.

3. Expose circuit breaker state via Prometheus metrics (Resilience4j + Micrometer integration).

4. Circuit breaker configuration must be externalized in `application.yml`, not hardcoded.

**Effort**: 2-3 weeks | **Impact**: HIGH
**Dependencies**: A2 Prometheus metrics (done)

---

### B3. Parallelize TenantScopedRunner

**Objective**: Transform scheduled job tenant iteration from O(N) sequential to O(N/P) parallel.

**Problem**: `TenantScopedRunner.forEachTenant()` iterates all tenants in a sequential `for` loop. At 1000 tenants with a 60-second automation CRON, if one tenant's automation takes 30s (LLM call), the sweep takes 30+ minutes. Sweeps can stack.

**Requirements**:

1. Add a new method `forEachTenantParallel(BiConsumer<String, String> action, int parallelism)` to `TenantScopedRunner`.

2. Implementation must use Java 25 `StructuredTaskScope` or a bounded virtual thread executor with `ScopedValue` carriers to ensure tenant isolation per parallel branch.

3. The existing sequential `forEachTenant()` must remain unchanged — callers opt in to the parallel variant.

4. Per-tenant exception isolation must be preserved: a failure in one tenant must not abort other tenants.

5. Parallelism should be configurable per caller (default 10).

6. Callers to migrate (opt-in, one at a time):
   - `AutomationScheduler.pollScheduledTriggers()` (60s interval, highest priority)
   - `AutomationScheduler.pollDelayedActions()` (15 min interval)
   - `DormancyScheduledJob` (daily)
   - Other daily CRON jobs as needed

**Effort**: 2 weeks | **Impact**: HIGH (critical for scaling past 100 tenants)

---

### B4. Async PDF/Document Generation

**Objective**: Move CPU-intensive PDF generation off request threads.

**Problem**: `PdfRenderingService.htmlToPdf()` uses OpenHTMLToPDF which is CPU-intensive and blocks the request thread. 10 concurrent PDF generations = 10 blocked virtual threads + 10 in-memory PDF buffers. This directly impacts API response times for all users.

**Requirements**:

1. PDF generation endpoints return `202 Accepted` with a job ID instead of blocking until the PDF is ready.

2. PDF generation runs on a bounded background queue (configurable max concurrent, default 5).

3. Generated PDFs are uploaded to S3. Clients poll a status endpoint or use SSE for completion notification.

4. Keep the synchronous endpoint alive behind a feature flag during migration (`kazi.pdf.async.enabled`, default false). Frontend migrates to async flow, then flag is removed.

5. Apply the same pattern to `DataExportService.generateExport()` (ZIP generation).

**Effort**: 2-3 weeks | **Impact**: Medium
**Frontend dependency**: Frontend must handle 202 + polling flow.

---

### B5. Redis-Backed Cache and Rate Limiting

**Objective**: Replace in-process Caffeine caches with Redis for multi-pod consistency.

**Problem**: Running N replicas means N separate Caffeine caches. Rate limits reset on deploy. Dashboard data computed N times instead of once. Redis (ElastiCache) is already provisioned in infrastructure (`REDIS_HOST` env var wired to ECS task definition) but unused by the backend.

**Requirements**:

1. Add `spring-boot-starter-data-redis` dependency. Connect to existing ElastiCache using `REDIS_HOST` and `REDIS_AUTH_TOKEN` env vars.

2. Migrate the following to Redis-backed storage:
   - **`EmailRateLimiter`**: Currently in-memory Caffeine, resets on restart, doesn't work across pods. Move to Redis with tenant-prefixed keys.
   - **`MemberFilter` capability cache**: Member capabilities looked up on every request. Use Redis as L2 behind Caffeine L1.
   - **`SubscriptionStatusCache`**: Subscription status checked frequently. Redis with 5-minute TTL.
   - **`DashboardService` org-level cache**: Dashboard aggregations. Redis with 3-minute TTL.

3. Redis must be optional — if unavailable, fall back to Caffeine gracefully. Test profile uses Caffeine only (`spring.data.redis.enabled=false` or absent Redis bean).

4. All cache keys must be tenant-prefixed to maintain isolation: `{tenantId}:{cacheType}:{key}`.

5. Redis connection must use TLS (ElastiCache in-transit encryption is enabled in infra).

**Effort**: 3-4 weeks | **Impact**: HIGH (enables safe horizontal scaling)

---

### B6. Redis-Backed AI Pending Confirmations

**Objective**: Make AI tool confirmations work correctly across replicas and survive restarts.

**Problem**: `AssistantService.java` stores pending tool confirmations in a `ConcurrentHashMap<String, PendingConfirmation>`. If the pod handling the AI stream crashes during a tool confirmation wait, the confirmation is lost. With multi-pod deployment, the confirmation POST may route to a different pod than the one holding the `CompletableFuture`.

**Requirements**:

1. Replace `ConcurrentHashMap<String, PendingConfirmation>` with Redis-backed state.

2. When a tool confirmation is requested: write a pending confirmation entry to Redis with the `toolCallId` as key, TTL matching the confirmation timeout (120s).

3. When the user confirms/denies: write the decision to Redis.

4. The streaming thread polls Redis for the confirmation decision (replacing `CompletableFuture.get(timeout)`). Alternatively, use Redis Pub/Sub for instant notification.

5. Fallback to in-memory `ConcurrentHashMap` when Redis is unavailable (single-pod degraded mode).

**Effort**: 1-2 weeks | **Impact**: Medium
**Dependencies**: B5 (Redis connectivity)

---

### B7. Streaming Data Export

**Objective**: Eliminate in-memory buffering for data exports.

**Problem**: `DataExportService` loads all customer data + audit events into memory, builds a ZIP in a `ByteArrayOutputStream`, then uploads to S3. For a customer with 50k audit events and rich history, this is 50-100 MiB per export. Multiple concurrent exports can cause OOM.

**Requirements**:

1. Replace in-memory ZIP creation with streaming: use `PipedInputStream`/`PipedOutputStream` or a temporary file, writing ZIP entries incrementally.

2. Upload to S3 using multipart upload API (streaming, not buffered).

3. Load data in paginated batches (1000 rows per query) instead of loading all records at once.

4. The export output must be byte-identical to the current implementation for datasets under 10,000 rows (verified by a characterization test).

5. Handle failure mid-stream: abort partial S3 multipart uploads, clean up temporary resources.

**Effort**: 2-3 weeks | **Impact**: Medium

---

## Tier B Execution Order

```
Phase 2 — Resilience (Week 5-10 from project start)
  B2: Circuit breakers          [independent]
  B3: Parallelize TenantRunner  [independent]

Phase 3 — Infrastructure Patterns (Week 8-16)
  B5: Redis cache/rate limiting [independent, infra exists]
  B1: Outbox pattern            [depends on A7 ShedLock ✓]
  B4: Async PDF generation      [independent]
  B6: Redis AI confirmations    [depends on B5]
  B7: Streaming data export     [independent]
```

B2 and B3 can run in parallel. B5 should start before B6. B1 is the longest item and the foundation for all Tier C work, so it should start early even though it takes 4-6 weeks.

---

## Tier C: Strangler Pattern — Microservice Extraction

### Prerequisites (before ANY extraction)

All of the following must be in place before extracting the first service:

1. **Message broker**: AWS SNS + SQS (not Kafka — operational overhead is unjustified at this scale). SNS topics per bounded context, SQS queues per consumer.

2. **API gateway routing**: Extend the existing ALB with path-based routing rules to route extracted service endpoints. The gateway (port 8443) handles only OIDC session/CSRF for the frontend — it does NOT proxy API calls today.

3. **Shared auth library**: Package `ClerkJwtAuthenticationConverter`, `TenantFilter`, `MemberFilter`, and `RequestScopes` as an internal Maven artifact (`io.b2mash:kazi-auth-common`). Extracted services depend on this library for consistent JWT validation and tenant context binding.

4. **Tenant context propagation**: Inter-service calls pass `X-Tenant-Id` and `X-Org-Id` headers. Receiving services validate these against the JWT `o.id` claim before binding `ScopedValue`. This prevents header spoofing.

5. **Outbox pattern (B1) must be complete**: Cross-service events flowing through SNS/SQS need guaranteed delivery. Without the outbox, extracting a service that publishes events (like notification) means silently lost events whenever the broker is unavailable.

6. **Contract testing framework**: Spring Cloud Contract or Pact for consumer-driven contract tests. These are written against the monolith first (proving the contract matches current behavior), then the extracted service must honor the same contracts.

### Event Backbone Design

**Topology**: SNS fan-out + SQS per consumer.

Each domain publishes to its own SNS topic. Consuming services subscribe to the topics they need via SQS queues. One queue per consumer per topic.

**Topics** (by bounded context):

| Topic | Events | Publishers | Consumers |
|-------|--------|-----------|-----------|
| `kazi.invoice.lifecycle` | Approved, Sent, Paid, Voided | Monolith | Notification, Accounting Sync, Portal |
| `kazi.customer.lifecycle` | Created, Updated, StatusChanged | Monolith | Notification, Portal, Accounting Sync |
| `kazi.project.lifecycle` | Created, Completed, Archived | Monolith | Notification, Portal |
| `kazi.document.lifecycle` | Uploaded, Generated, VisibilityChanged | Monolith | Notification, Portal |
| `kazi.proposal.lifecycle` | Sent, Accepted, Expired | Monolith | Notification, Portal |

**Message envelope** (standard for all topics):

```json
{
  "eventId": "uuid",
  "eventType": "InvoiceApprovedEvent",
  "tenantId": "tenant_abc123",
  "orgId": "org_xyz",
  "entityId": "uuid",
  "timestamp": "2026-05-25T10:00:00Z",
  "version": 1,
  "payload": { }
}
```

**Guarantees**: At-least-once delivery. Consumers must be idempotent. The `eventId` is the deduplication key (consumers track processed event IDs in a local `processed_events` table).

**Dead-letter queues**: Each SQS queue has a DLQ with `maxReceiveCount: 5`. Failed messages land in the DLQ for manual inspection/replay via an admin API.

---

### C1. Extract Email/Notification Service

**Timeline**: 3 months | **Risk**: LOW

**Rationale**: Notification is a leaf node in the dependency graph — nothing depends on it. The `EmailProvider` interface already exists with 3 implementations (`SmtpEmailProvider`, `SendGridEmailProvider`, `NoOpEmailProvider`). The `NotificationEventHandler` has 14+ `@TransactionalEventListener` methods that all follow the same pattern: receive domain event → build notification → dispatch via email/in-app. This is the safest first extraction.

**Scope — what moves to the new service**:
- `notification/*` — notification entity, preferences, dispatch logic
- `integration/email/*` — provider abstraction, SMTP/SendGrid implementations, rate limiting, delivery log, bounce/webhook handling
- Email templates

**Scope — what stays in the monolith**:
- Domain event publishing (via outbox → SNS)
- In-app notification read/mark-read API (remains in monolith until a dedicated notification API is warranted)

**Interface**: Purely event-driven. The notification service subscribes to SNS topics (`kazi.invoice.lifecycle`, `kazi.customer.lifecycle`, `kazi.proposal.lifecycle`, etc.) via SQS queues. No synchronous HTTP API between monolith and notification service. The frontend does not call the notification service directly.

**Tenant multitenancy**: The notification service needs read-only access to org settings (sender email, branding) but does NOT need per-tenant schemas. A simple Redis-cached lookup from the monolith's settings API suffices.

**Migration path**:
1. Deploy notification service subscribing to SNS topics
2. Monolith publishes via outbox (B1) → outbox poller publishes to SNS
3. Run monolith AFTER_COMMIT listeners in parallel as fallback (dual-write phase)
4. Monitor: compare delivery rates between monolith listeners and notification service for 1 week
5. Disable monolith listeners one at a time (start with `AccountingSyncEventListener` — separate from notification but same pattern)
6. Delete monolith notification code after 2 weeks of stable operation

**Rollback**: Re-enable monolith AFTER_COMMIT listeners. The outbox poller continues to publish to SNS but the monolith listeners handle delivery directly.

---

### C2. Extract Document/PDF Generation Service

**Timeline**: 3 months | **Risk**: LOW

**Rationale**: `PdfRenderingService`, `DocxMergeService`, and `PdfConversionService` are CPU-intensive and completely stateless. They receive input (template content + variable context) and produce output (PDF bytes uploaded to S3). The heaviest JVM dependencies (OpenHTMLToPDF, docx4j, PDFBox, Apache POI) move out of the monolith, reducing its memory footprint and eliminating CPU contention between PDF generation and interactive API traffic.

**Scope — what moves**:
- Template rendering (Tiptap JSON → HTML)
- HTML → PDF conversion (OpenHTMLToPDF)
- DOCX template merge (docx4j / Apache POI)
- DOCX → PDF conversion
- S3 upload of generated documents

**Scope — what stays**:
- Document template CRUD (entity, repository, controller)
- Document metadata management
- Presigned URL generation for direct browser upload/download

**Interface**: Synchronous HTTP. The monolith POSTs template content + context variables to the PDF service. The PDF service renders, uploads to S3, and returns the S3 key.

```
POST /api/render
Content-Type: application/json

{
  "templateHtml": "<html>...",
  "variables": { "customerName": "...", "invoiceNumber": "..." },
  "outputFormat": "pdf",
  "s3Bucket": "kazi-documents",
  "s3Key": "org/tenant_abc/documents/invoice-123.pdf"
}

Response: { "s3Key": "...", "sizeBytes": 45230 }
```

**Tenant multitenancy**: Not needed. The service is stateless — template content and context are passed in the request. Tenant isolation is enforced by the monolith before calling. The PDF service has no database.

**Autoscaling**: CPU-bound profile (scale on CPU utilization, not request count). Separate from the monolith's I/O-bound profile.

**Migration path**:
1. Deploy PDF service behind internal ALB (same VPC, not publicly routable)
2. `PdfRenderingService.htmlToPdf()` becomes a thin HTTP client calling the PDF service
3. Feature flag (`kazi.pdf.external-service.enabled`) controls routing: false = local rendering (current), true = HTTP call to PDF service
4. Verify output byte-equivalence for a reference set of templates
5. Enable flag in production, monitor error rates and latency
6. Remove OpenHTMLToPDF/docx4j/PDFBox from monolith `pom.xml` (significant JAR size reduction)

---

### C3. Extract AI/Assistant Service

**Timeline**: 4-6 months | **Risk**: MEDIUM

**Rationale**: The assistant package is 75+ files with its own provider abstraction (`LlmChatProvider`), tool registry (`AssistantToolRegistry`), and specialist system. It has the most extreme resource profile: 120s LLM streaming calls, unbounded tool execution loops, SSE connections held open for minutes. It benefits enormously from independent scaling (LLM-bound, not CPU/DB-bound).

**Coupling point**: The tool registry contains 25+ tools (`GetProjectTool`, `CreateTaskTool`, `LogTimeEntryTool`, etc.) that inject domain repositories directly. This is the primary extraction challenge.

**Scope — what moves**:
- `assistant/*` — controller, service, chat context, provider abstraction
- `assistant/provider/*` — Anthropic LLM provider, model configuration
- `assistant/specialist/*` — specialist registry, non-interactive runners
- `integration/ai/*` — AI settings, execution gates, profile, cost metering

**Scope — what stays**:
- Domain entities and repositories (Project, Task, TimeEntry, etc.)
- REST API endpoints that tools will call

**Tool execution strategy**: Each assistant tool currently injects repositories (`@Autowired ProjectRepository`). After extraction, tools become thin HTTP clients that call the monolith's existing REST API using the same JWT for authentication. The monolith's tenant-isolated API handles authorization and data access.

Example: `GetProjectTool` currently calls `projectRepository.findById(id)`. After extraction, it calls `GET /api/projects/{id}` on the monolith with the user's JWT.

**Interface**: Frontend calls the AI service directly for `/api/assistant/*`. The ALB routes these paths to the AI service instead of the monolith.

**Tenant multitenancy**: JWT validation for tenant context. No tenant schemas needed — the AI service is stateless except for Redis-backed confirmations (B6). All tenant data access goes through the monolith's tenant-isolated REST API.

**Autoscaling**: Scale on concurrent SSE connections, not CPU. A pod handling 20 concurrent AI chats is at capacity even at 5% CPU utilization.

**Migration path**:
1. Refactor tools to use an interface (`ToolDataSource`) with two implementations: `RepositoryToolDataSource` (current, direct DB) and `HttpToolDataSource` (REST client). Toggle via config.
2. Move `pendingConfirmations` to Redis (B6 — prerequisite)
3. Deploy AI service with `HttpToolDataSource`, route `/api/assistant/*` via ALB
4. Monitor: tool execution latency (added network hop), error rates, confirmation flow
5. Remove `assistant/*` and `integration/ai/*` from monolith

**Rollback**: Re-route `/api/assistant/*` to monolith at ALB level. Monolith still has the full assistant package until step 5.

---

### C4. Extract Customer Portal Service

**Timeline**: 4-6 months | **Risk**: MEDIUM

**Rationale**: The portal has the strongest existing extraction seam in the codebase. It already operates with:
- Separate datasource (`portalDataSource`, `portalJdbcClient`, `portalTransactionManager` in `PortalDataSourceConfig.java`)
- Separate auth system (magic link JWT, not Keycloak/Clerk)
- Read-model schema populated via Spring events (`PortalEventHandler`, `PortalDocumentNotificationHandler`)
- Separate Next.js frontend app (`portal/`)

The portal is effectively already a CQRS read-model — extracting it into its own service formalizes the existing boundary.

**Scope — what moves**:
- `portal/*` (37 files) — portal auth, query services, portal contacts, magic links
- `customerbackend/*` — portal read-model sync handlers, portal event listeners
- Portal schema tables (contacts, sessions, activity feed, branding, notification preferences)
- `portal/` Next.js frontend (already separate)

**Scope — what stays**:
- Domain event publishing (via outbox → SNS)
- Customer entity and lifecycle management
- `PortalDataSourceConfig.java` removal (datasource moves to the portal service)

**Interface**:
- **Inbound (events)**: Portal service subscribes to SNS topics (`kazi.customer.lifecycle`, `kazi.project.lifecycle`, `kazi.document.lifecycle`, `kazi.invoice.lifecycle`) via SQS to populate its read-model.
- **Outbound (HTTP)**: Portal frontend calls the portal service directly for all portal API endpoints (`/portal/api/*`).
- **Magic link generation**: The monolith calls the portal service's internal API to generate magic links (or the portal service exposes a `POST /internal/magic-links` endpoint).

**Tenant multitenancy**: The portal currently operates with a global `portal` schema (data segregated by `org_id` columns, not schema-per-tenant). In the extracted service, this becomes its own database. Tenant isolation is enforced by `org_id` filtering, consistent with the current model.

**Migration path**:
1. Portal service subscribes to domain events via SNS/SQS to populate its read model
2. Run dual-write: monolith portal event listeners + portal service both populate the read model. Compare for parity.
3. Portal frontend switches API calls from monolith to portal service
4. Magic link generation moves to portal service
5. Monolith stops writing to portal schema directly
6. Remove `portal/*`, `customerbackend/*` from monolith. Remove `portalDataSource` bean.

---

### What NOT to Extract

| Domain | Coupling | Verdict |
|--------|---------|---------|
| **Customer + Project** | CustomerRepository: 64 injections across billing, invoicing, proposals, projects, retention, portal. ProjectRepository: 48 injections. | Too central. Extraction requires rewriting 60% of the codebase into API calls. The cost of distributed transactions and network hops outweighs any scaling benefit. |
| **Invoice / Billing / TimeEntry** | `BillingRunLifecycleService` touches `TimeEntryRepository`, `ExpenseRepository`, `InvoiceRepository`, `InvoiceLineRepository` in a single `@Transactional`. Circular dependency. | Revenue-critical path. Distributed transaction semantics would introduce unacceptable complexity and risk. Modularize internally instead. |
| **Audit** | 99 injections across every domain. Woven into every state change via `AuditService.log()`. | Extracting makes audit the single point of failure for all writes. Every service write would require a synchronous call to an audit service. Keep as cross-cutting concern within each service. |
| **Legal Vertical** | Behind `VerticalModuleGuard` but FK-coupled to `projects` and `customers` tables. Trust accounting has its own event hierarchy but shares database schemas with core entities. | Modularize internally first (enforce package boundaries via ArchUnit). Consider extraction only after Customer/Project extraction — which is not recommended. |

---

## Tier C Execution Order

```
Prerequisites (Month 3-5, parallel with late Tier B)
  - B1 Outbox pattern complete
  - SNS/SQS infrastructure provisioned (Terraform)
  - Shared auth library published
  - Contract testing framework in place

Phase 4 — First Extraction (Month 5-7)
  C1: Email/Notification
  - Lowest risk, leaf node, purely event-driven
  - Validates the entire extraction pipeline (outbox → SNS → SQS → service)

Phase 5 — Stateless Extraction (Month 7-9)
  C2: PDF Generation
  - No event backbone needed (sync HTTP only)
  - Removes heaviest JVM dependencies from monolith

Phase 6 — AI Extraction (Month 9-14)
  C3: AI/Assistant
  - Depends on B6 Redis confirmations
  - Most complex extraction (tool → HTTP client refactor)

Phase 7 — Portal Extraction (Month 12-18)
  C4: Customer Portal
  - Depends on B1 outbox + event backbone
  - Strongest existing seam, but large surface area (37 files)
```

---

## Success Metrics

| Metric | Current | After Tier B | After Tier C |
|--------|---------|-------------|-------------|
| **Max concurrent users before pool exhaustion** | ~25 (post Tier A) | 100+ (Redis, circuit breakers) | 500+ (independent scaling) |
| **Scheduled job sweep time (1000 tenants)** | 30+ minutes | <2 minutes (parallel) | Same |
| **Event delivery reliability** | Best-effort (silent loss) | Guaranteed (outbox + retry) | Guaranteed (SNS/SQS) |
| **PDF generation impact on API latency** | Direct (shared threads) | None (async queue) | None (separate service) |
| **Cache consistency across replicas** | None (per-JVM Caffeine) | Strong (Redis) | Same |
| **Circuit breaker coverage** | 0 external services | 4 services | Same per service |
| **Deployment independence** | Monolith only | Monolith only | 4 independent services |
| **Monolith JAR size** | ~120 MiB (est.) | Same | ~80 MiB (OpenHTMLToPDF/docx4j removed) |
