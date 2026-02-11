# ADR-032: Spring Application Events for Portal Sync

**Status**: Accepted

**Context**: The portal read-model schema (see [ADR-031](ADR-031-separate-portal-read-model-schema.md)) needs to be kept in sync with the core domain. When staff create projects, upload documents, link customers, or add comments, corresponding read-model entities must be created or updated in the `portal` schema.

The codebase currently has no event infrastructure — services call repositories directly and return. Introducing an event mechanism requires choosing between in-process Spring events and out-of-process messaging (SQS, Kafka, etc.).

**Options Considered**:

1. **Direct service calls from core to portal (no events)**
   - Pros:
     - Simplest to implement — just call `portalReadModelRepo.upsert(...)` from core services
     - Synchronous — no eventual consistency concerns
     - No new abstractions
   - Cons:
     - Tight coupling — core services depend on portal package
     - Core domain imports portal classes, violating package boundaries
     - Every new portal feature requires modifying core services
     - Cannot extract portal to a separate service without rewriting the integration

2. **Spring `ApplicationEvent` with `@TransactionalEventListener`**
   - Pros:
     - Loose coupling — core publishes events, portal subscribes independently
     - `AFTER_COMMIT` phase ensures events fire only on successful transactions
     - Events are plain records — easy to serialize for future message bus migration
     - No external infrastructure needed
     - Spring-native — no additional dependencies
   - Cons:
     - In-process only — does not scale to separate service deployments
     - Lost on application crash between commit and event processing (very narrow window)
     - No built-in retry or dead-letter queue
     - Event handler errors must be caught manually (no framework retry)

3. **Out-of-process messaging (SQS/SNS or Kafka)**
   - Pros:
     - Durable — messages survive application crashes
     - Scales to separate service deployments
     - Built-in retry, dead-letter queues, monitoring
     - Decouples deployment lifecycles
   - Cons:
     - Significant infrastructure complexity (SQS queues, IAM policies, consumer configuration)
     - Overkill for a single-deployable prototype
     - Requires serialization/deserialization, message schema management
     - Dual-write problem: DB commit + message publish are not atomic without outbox pattern
     - Operational burden (monitoring, DLQ processing, message ordering)

**Decision**: Use Spring `ApplicationEvent` with `@TransactionalEventListener` (Option 2).

**Rationale**: In-process Spring events provide the right level of decoupling for Phase 7. The portal and core domain are in the same deployable — there is no need for durable, cross-service messaging yet. Spring events are zero-infrastructure, zero-dependency, and the `@TransactionalEventListener(phase = AFTER_COMMIT)` pattern ensures events fire only when the core transaction succeeds.

The critical design choice is to **shape events as serializable records** with primitive fields (no entity references, no lazy-loading proxies). This means the event payloads can be trivially serialized to JSON for future SQS/SNS migration. When the portal is extracted to a separate service, the migration path is:
1. Replace `ApplicationEventPublisher.publishEvent()` with `SqsTemplate.send()`
2. Replace `@TransactionalEventListener` with `@SqsListener`
3. No changes to event payload shapes or handler logic

Direct service calls (Option 1) were rejected because they create a dependency from core → portal that makes extraction impossible without rewriting the integration layer.

Out-of-process messaging (Option 3) is the eventual target but introduces unnecessary complexity for a prototype where both producer and consumer are in the same JVM. The outbox pattern (needed for atomic DB+message writes) alone would add more complexity than the entire event system.

**Consequences**:
- Core services gain `ApplicationEventPublisher` injection and `publishEvent()` calls after mutations
- Event classes are records in the `customerbackend.event` package — sealed hierarchy under `PortalDomainEvent`
- `PortalEventHandler` in the `customerbackend.handler` package handles all event types
- Each handler method catches and logs exceptions — a failed projection must not affect the user-facing response
- `synced_at` timestamps on read-model entities enable staleness detection
- A manual resync endpoint covers the edge case of missed events
- Future migration to SQS/SNS requires changing transport code only — event shapes and handler logic remain unchanged
