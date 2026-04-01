# Domain Events Without a Message Broker: The In-Process Event Bus

*Part 4 of "Modern Java for SaaS" — practical patterns from building production B2B software with Java 25, Spring Boot 4, and Hibernate 7.*

---

DocTeams has audit logging, notifications, activity feeds, and email sending — all triggered when domain events happen (invoice sent, task assigned, comment created, customer activated). The standard advice is: use Kafka. Or RabbitMQ. Or SQS. A message broker that decouples producers from consumers.

I don't use any of these. I use Spring's `ApplicationEventPublisher` — an in-process event bus that's built into every Spring application. No infrastructure. No deployment. No operational overhead. And it works.

## The Pattern

**Publishing an event (in a service method):**

```java
@Service
public class InvoiceService {

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Invoice send(UUID invoiceId) {
        var invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

        invoice.markSent(RequestScopes.requireMemberId());
        invoiceRepository.save(invoice);

        // Publish domain event — listeners handle side effects
        eventPublisher.publishEvent(new InvoiceSentEvent(
            "invoice.sent",
            invoice.getId(),
            RequestScopes.requireMemberId(),
            RequestScopes.getTenantIdOrNull(),
            Instant.now(),
            Map.of("invoice_number", invoice.getInvoiceNumber(),
                   "customer_id", invoice.getCustomerId().toString())));

        return invoice;
    }
}
```

**Handling the event (in a separate component):**

```java
@Component
public class NotificationEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceSent(InvoiceSentEvent event) {
        handleInTenantScope(event.tenantId(), event.orgId(), () -> {
            try {
                var notifications = notificationService.handleInvoiceSent(event);
                dispatchAll(notifications);
            } catch (Exception e) {
                log.warn("Failed to create notifications for invoice.sent: {}",
                    event.entityId(), e);
            }
        });
    }

    private void handleInTenantScope(String tenantId, String orgId, Runnable action) {
        if (tenantId != null) {
            ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
                .run(action);
        } else {
            action.run();
        }
    }
}
```

Two key details:

**`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`** — the handler runs *after* the domain transaction commits. If the invoice save fails, no notification is sent. If the notification fails, the invoice is still saved. The side effect doesn't affect the domain operation.

**`handleInTenantScope()`** — the handler runs in a new transaction (implicit from Spring). But `ScopedValue` bindings from the original request are gone — the event carries `tenantId` and `orgId` explicitly, and the handler rebinds them. This ensures the notification service's queries hit the correct tenant schema.

## Events as Records

Events are immutable records with all the context a handler might need:

```java
public record InvoiceSentEvent(
    String eventType,
    UUID entityId,
    UUID actorMemberId,
    String tenantId,
    Instant occurredAt,
    Map<String, Object> details) implements DomainEvent {}
```

The `DomainEvent` marker interface is just that — a marker. No methods. Its purpose is to make it easy to find all events in the codebase (`grep "implements DomainEvent"`).

Events carry enough data that handlers don't need to fetch the entity again. `InvoiceSentEvent` includes the invoice number, customer ID, actor, and tenant. The notification handler can create a notification record without querying the invoice table.

This matters for `AFTER_COMMIT` handlers — the original transaction is done, so lazy-loaded relationships from the service's entity are detached. If the handler tried to call `invoice.getCustomer().getName()`, it would fail with a `LazyInitializationException`. The event carries the data it needs.

## What Listens for Events

DocTeams has four event consumers:

**1. Notification handler** — creates `Notification` records and dispatches them via channels (in-app, email):

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onTaskAssigned(TaskAssignedEvent event) { ... }

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCommentCreated(CommentCreatedEvent event) { ... }

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onInvoiceSent(InvoiceSentEvent event) { ... }
```

**2. Audit handler** — creates `AuditEvent` records for compliance:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCustomerActivated(CustomerActivatedEvent event) {
    auditService.log(AuditEventBuilder.builder()
        .eventType("customer.activated")
        .entityType("customer")
        .entityId(event.entityId())
        .details(event.details())
        .build());
}
```

**3. Activity feed handler** — creates activity entries for the project activity tab:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onDocumentUploaded(DocumentUploadedEvent event) {
    activityService.record(event);
}
```

**4. Portal sync handler** — pushes changes to the customer portal's read model:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onProjectUpdated(ProjectUpdatedEvent event) {
    portalSyncService.syncProject(event.entityId());
}
```

Four independent consumers, all decoupled from the domain service. The `InvoiceService` doesn't know that notifications, audit logs, activity entries, and portal sync happen when an invoice is sent. It publishes the event and moves on.

## Why Not Kafka/RabbitMQ

**Operational overhead.** Kafka needs ZooKeeper (or KRaft), brokers, topic management, consumer group coordination, and monitoring. RabbitMQ needs exchange configuration, queue binding, dead-letter handling. For a B2B SaaS with dozens of tenants, this infrastructure costs more than the product earns.

**The failure mode is acceptable.** If an in-process event handler fails, the notification doesn't get created. The invoice is still sent. The user can check the activity log. For a 3-person accounting firm, a missing notification is a minor inconvenience, not a data loss event.

If I needed **guaranteed delivery** (e.g., billing events that must be processed exactly once), I'd add a message broker. I don't — all my events are notifications/side effects where "best effort" is sufficient.

**Testability.** Testing in-process events is trivial — it's just Spring `@SpringBootTest` with real beans. Testing Kafka consumers requires a Kafka Testcontainer, topic setup, consumer polling, and timeout handling. The complexity isn't worth it when in-process events solve the problem.

## When to Upgrade to a Broker

The in-process event bus has limits:

- **Cross-service events.** If you split into microservices, in-process events can't cross JVM boundaries. You need a broker (or HTTP webhooks).
- **Replay.** In-process events are fire-and-forget. If a handler was down when the event fired, it's lost. Kafka's log retention allows replaying events.
- **Scaling consumers independently.** If notification sending becomes a bottleneck, you can't scale just the notification handler — it runs in the same JVM as everything else.
- **Event sourcing.** If you want the event log to be the source of truth (not the database), you need persistent event storage.

None of these apply to DocTeams today. The architecture is ready for the upgrade — events already carry full context including tenant ID, and handlers already rebind `ScopedValue`. Moving to Kafka would mean changing the transport layer, not the event structure.

## The Design Principle

Domain events implement a simple principle: **services produce facts, handlers react to facts.**

`InvoiceService.send()` produces the fact "invoice sent." It doesn't care who reacts. The notification handler, audit handler, activity handler, and portal sync handler each react independently.

Adding a new reaction (say, sending a Slack message when an invoice is sent) means adding one new handler method. No changes to `InvoiceService`. No changes to existing handlers. The service doesn't even know the new handler exists.

This is the Open/Closed Principle in practice: open for extension (add new handlers), closed for modification (existing services don't change).

For a codebase with 155 services and 104 controllers, this separation keeps changes local. New features add handlers. They don't modify existing service methods.

---

*Next in this series: [Presigned URLs and S3 in Multi-Tenant SaaS](05-presigned-urls-s3.md)*

*Previous: [The One-Service-Call Controller](03-one-service-call-controller.md)*
