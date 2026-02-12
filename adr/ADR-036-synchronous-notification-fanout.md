# ADR-036: Synchronous In-Process Notification Fan-Out

**Status**: Accepted

**Context**: Phase 6.5 introduces an in-app notification system. When a domain event occurs (task assigned, comment added, document uploaded, etc.), the system must determine the set of recipients and create a `Notification` row for each one. This "fan-out" logic -- mapping a single event to multiple notifications -- is the core of the notification system.

The codebase currently has no event infrastructure. All domain services call `AuditService.log()` directly for audit logging. [ADR-032](ADR-032-spring-application-events-for-portal.md) accepted Spring `ApplicationEvent` with `@TransactionalEventListener` as the event mechanism for Phase 7's portal projections, but no events have been implemented yet. Phase 6.5 is the first phase to introduce Spring application events.

The key question is how the notification fan-out executes: synchronously within the request thread, asynchronously via `@Async`, or externally via a message queue. This affects latency (how quickly the API responds), reliability (what happens if notification creation fails), and complexity (infrastructure needed).

**Options Considered**:

1. **Direct service calls in domain services (no events)**
   - Pros:
     - Simplest to implement -- `notificationService.notify(recipients, ...)` called from `TaskService`, `CommentService`, etc.
     - No event infrastructure needed
     - Failures are visible immediately (same transaction)
   - Cons:
     - Tight coupling -- every domain service depends on `NotificationService`
     - Notification logic pollutes domain services
     - Adding new notification types requires modifying domain services
     - Cannot add more consumers (e.g., Phase 7 portal projections) without further coupling

2. **`@TransactionalEventListener(phase = AFTER_COMMIT)` with synchronous processing**
   - Pros:
     - Loose coupling -- domain services publish events, notification handler subscribes independently
     - `AFTER_COMMIT` phase ensures events fire only for committed transactions (no phantom notifications)
     - Event handler runs in the same thread after commit -- predictable, debuggable
     - Aligns with [ADR-032](ADR-032-spring-application-events-for-portal.md) -- same pattern Phase 7 will use for portal projections
     - No infrastructure beyond what Spring provides
     - Event objects are reusable for future consumers (audit migration, portal sync, analytics)
   - Cons:
     - Event handler execution adds latency to the HTTP response (runs after commit but before response returns to client)
     - If notification creation fails, the HTTP response may still succeed (event is post-commit) -- but the error is logged, not propagated
     - No retry mechanism -- failed notification creation must be handled manually or ignored
     - Fan-out to many recipients (e.g., 50 project members for a document upload) adds proportional latency

3. **`@Async` event processing (thread pool)**
   - Pros:
     - HTTP response returns immediately after commit -- no fan-out latency in the response path
     - Thread pool provides some concurrency for fan-out
   - Cons:
     - Requires thread pool configuration and monitoring
     - Async errors are harder to trace and debug
     - `@Async` methods lose the request context (no `RequestScopes`, no MDC, no `HttpServletRequest`)
     - Tenant context must be explicitly passed and re-bound in the async thread
     - Transaction boundaries are different -- async handler runs in its own transaction
     - Adds operational complexity for a small notification volume

4. **External message queue (SQS/RabbitMQ)**
   - Pros:
     - Durable -- messages survive application crashes
     - Built-in retry, dead-letter queue, monitoring
     - Scales to high fan-out volumes
   - Cons:
     - Significant infrastructure complexity (queue provisioning, consumer configuration, IAM policies)
     - Dual-write problem -- DB commit and message publish are not atomic without an outbox pattern
     - Overkill for current scale (tens of notifications per event, not thousands)
     - Operational burden (DLQ monitoring, message ordering, consumer group management)

**Decision**: `@TransactionalEventListener(phase = AFTER_COMMIT)` with synchronous processing (Option 2).

**Rationale**: Option 2 provides the right balance of decoupling, reliability, and simplicity for Phase 6.5. The key advantages are:

1. **Correctness**: `AFTER_COMMIT` ensures notifications are only created for events that actually committed to the database. A rolled-back task assignment never generates a notification. This is critical for user trust.

2. **Alignment with ADR-032**: Phase 7 will use the same event mechanism for portal projections. Introducing events in Phase 6.5 establishes the pattern, event classes, and handler conventions that Phase 7 builds on. The `NotificationEventHandler` and Phase 7's `PortalProjectionHandler` will subscribe to the same events independently.

3. **Simplicity**: No thread pools, no message queues, no outbox tables. The event handler runs in the same thread, same JVM, with full access to Spring's transaction manager for creating notification rows. Debugging is straightforward -- a breakpoint in the handler shows the full call stack from the controller through the domain service to the event handler.

The latency trade-off (fan-out adds time before the HTTP response) is acceptable for a B2B SaaS platform. A typical fan-out creates 1-10 notification rows (task assignee, other commenters, project leads) -- this adds single-digit milliseconds. The worst case (document upload notification to all 50 project members) adds ~50ms, which is well within acceptable response time for a non-real-time action.

If notification volume grows significantly (hundreds of recipients per event, thousands of events per minute), the migration path is to replace `@TransactionalEventListener` with `@Async` event processing or an SQS consumer. The event objects and handler logic remain unchanged -- only the dispatch mechanism changes.

**Consequences**:
- Domain services (CommentService, TaskService, DocumentService, etc.) inject `ApplicationEventPublisher` and call `publishEvent()` after mutations
- Event classes are Java records in a new `event/` package, carrying entity IDs, project ID, actor member ID, tenant ID, and event-specific payload
- `NotificationEventHandler` uses `@TransactionalEventListener(phase = AFTER_COMMIT)` on each handler method
- Each handler method runs in a new transaction (`@Transactional(propagation = REQUIRES_NEW)`) since the original transaction is already committed
- Handler methods catch and log all exceptions -- notification failures must not affect the user-facing response
- Fan-out logic (determining recipients) lives entirely in the handler, not in domain services
- The same events will be consumed by Phase 7's `PortalProjectionHandler` with no changes to publishers
- Monitoring: failed notification creation is logged at WARN level with event details for manual investigation
