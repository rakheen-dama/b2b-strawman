# ADR-029: Audit Logging Abstraction

**Status**: Accepted

**Context**: Domain and security events need to be captured and persisted as audit records. The question is how these events are captured in the code: should we use a cross-cutting mechanism like Spring AOP that automatically intercepts service methods, or should each service method explicitly call an `AuditService` to record its event?

The choice affects code coupling, event detail quality, testability, and how easy it is to add or modify audited operations as the domain evolves. It also must work reliably within the existing transaction model (Hibernate sessions, `TenantFilterTransactionManager`, `ScopedValue`-based request context).

**Options Considered**:

1. **Spring AOP aspect on service methods** — An `@AuditLogged` annotation on service methods. An AOP aspect intercepts annotated methods, inspects parameters and return values, and records an audit event.
   - Pros: Low coupling — service methods don't need to know about auditing. Adding audit to a new method is one annotation. Centralizes audit logic in one aspect class.
   - Cons: **Historically unreliable in this codebase** — the `TenantFilterTransactionManager` lesson showed that AOP aspects on `@Transactional` methods have unpredictable Hibernate Session boundaries. The aspect would fire either before or after the transaction, but not necessarily within the same Session that performed the domain operation. Cannot distinguish between different kinds of mutations on the same method (e.g., `updateTask()` might change status, priority, or both — the aspect sees only the method call, not which fields changed). Cannot easily capture before/after deltas for the `details` JSONB. Would require complex reflection to extract `entityId`, `entityType`, etc. from varied method signatures.

2. **Hibernate event listeners** — Use Hibernate's `PostInsertEventListener`, `PostUpdateEventListener`, `PostDeleteEventListener` to capture entity changes at the ORM level.
   - Pros: Automatic for all entities — any entity save triggers the listener. Full before/after state available via Hibernate's dirty checking.
   - Cons: Fires for ALL entity saves, including internal framework operations (e.g., `TenantAwareEntityListener` setting `tenantId`). Would produce noise events for non-audited fields. Cannot capture semantic context (was this an "archive" or an "update"? Hibernate sees both as an `UPDATE`). Cannot capture non-entity events (security events, document access). Runs inside Hibernate's flush cycle — complex error handling if audit persistence fails. Cannot easily capture actor/source metadata (Hibernate listeners don't have access to `HttpServletRequest`).

3. **Explicit `AuditService.log()` calls in service methods** — Each service method that performs an auditable operation calls `AuditService.log()` with the appropriate event type, entity reference, and details. A convenience builder (`AuditEventBuilder`) auto-populates actor, source, and request metadata from `RequestScopes` and `RequestContextHolder`.
   - Pros: **Full control over what's logged** — each call specifies exactly the event type and details. Before/after deltas are trivially available (the service method has the entity in its local scope). Runs within the same transaction as the domain operation (consistency guarantee). Testable — each audit call is visible and assertable in tests. Works for both domain events (in service methods) and security events (in exception handlers and filters). No magic — engineers see the audit call and understand what's logged. Compatible with the existing transaction model.
   - Cons: Coupling — every audited service method has an `AuditService` dependency. Boilerplate — each audited method needs 3-5 lines for the audit call. Risk of forgetting to add audit calls to new methods (mitigated by test coverage and code review). More lines of code than annotation-based approaches.

4. **Spring Application Events** — Service methods publish domain events via `ApplicationEventPublisher`. A listener (`AuditEventListener`) subscribes to these events and persists audit records.
   - Pros: Decoupled — service methods don't depend on `AuditService`, only on `ApplicationEventPublisher`. Multiple listeners can consume the same event (audit, notifications, analytics). Follows Spring's idiomatic event model.
   - Cons: Events are fired asynchronously by default — need `@TransactionalEventListener(phase = BEFORE_COMMIT)` for same-transaction guarantees, which adds configuration complexity. Event classes proliferate (one per event type × entity type = ~30 classes). The listener needs to map Spring events to `AuditEvent` entities, adding a translation layer. [ADR-024](ADR-024-portal-task-time-seams.md) already decided against building event infrastructure in the current phase. Events without listeners are untested dead code; events with only one listener (audit) are equivalent to a direct service call with extra indirection.

**Decision**: Explicit `AuditService.log()` calls in service methods (Option 3).

**Rationale**: The codebase has a clear pattern for service-layer operations: service methods handle business logic, validation, and persistence within a `@Transactional` context. Adding explicit `AuditService.log()` calls at the end of each mutating method is the simplest and most reliable approach.

Key advantages specific to this codebase:

1. **Transaction reliability**: This codebase uses a custom `TenantFilterTransactionManager` that enables Hibernate `@Filter` in `doBegin()`. AOP aspects (Option 1) and Hibernate listeners (Option 2) have historically been unreliable with this custom transaction manager — the Session available to the aspect/listener may not be the same Session that executed the domain query. Explicit calls within the service method execute in the known, correct Session.

2. **Detail capture quality**: The most valuable part of an audit event is the `details` JSONB — "status changed from OPEN to DONE". Explicit calls have the entity in local scope with access to both old and new values. AOP aspects would need to diff parameters or return values (lossy); Hibernate listeners would need to access dirty-tracking state (fragile).

3. **Selective logging**: Not all saves should be audited. For example, `MemberSyncService.syncMembers()` does multiple entity updates in a loop — some are "member added" (auditable), some are "avatar URL updated" (not auditable). Explicit calls let the service method choose exactly which changes to audit.

4. **Non-entity events**: Security events (`security.access_denied`, `security.auth_failed`) are captured in `GlobalExceptionHandler` and `JwtAuthFilter` — neither is a service method with entity access. Only explicit calls (Option 3) work naturally in these locations.

The `AuditEventBuilder` mitigates the boilerplate concern:

```java
// 4 lines per audit call — acceptable for the value provided
auditService.log(AuditEventBuilder.builder()
    .eventType("task.claimed")
    .entityType("task")
    .entityId(taskId)
    .details(Map.of("assignee_id", memberId.toString()))
    .build());
```

The builder automatically reads `RequestScopes.MEMBER_ID`, `RequestContextHolder.getRequestAttributes()`, and derives `actorType`/`source` — so service methods only specify event-specific fields.

**Consequences**:
- Every audited service gains an `AuditService` dependency (constructor injection).
- ~30 `auditService.log()` calls added across 8 service classes.
- New auditable operations require a manual `auditService.log()` call — enforced by code review and integration tests that verify event production.
- The `AuditService` interface is the single abstraction point for future log forwarding — switching from DB-only to DB+CloudWatch requires changing only the `AuditService` implementation, not the 30+ call sites.
- Spring Application Events (Option 4) remain available for future use (e.g., real-time notifications) but are not used for audit persistence.
- No AOP aspects, no Hibernate listeners, no annotation-based magic — the audit system is fully explicit and debuggable.
