package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Base interface for all domain events published via Spring ApplicationEventPublisher. All
 * implementations must be records with primitive/UUID fields only — no JPA entity references, no
 * lazy proxies. This ensures events remain valid after the publishing transaction commits and the
 * persistence context closes.
 *
 * <p>Events carry enough context for any consumer to act without additional DB queries. The {@code
 * tenantId} field enables tenant-scoped processing in event handlers.
 */
public sealed interface DomainEvent
    permits BudgetThresholdEvent,
        CommentCreatedEvent,
        CommentUpdatedEvent,
        CommentDeletedEvent,
        CommentVisibilityChangedEvent,
        TaskAssignedEvent,
        TaskClaimedEvent,
        TaskStatusChangedEvent,
        DocumentUploadedEvent,
        MemberAddedToProjectEvent,
        InvoiceApprovedEvent,
        InvoiceSentEvent,
        InvoicePaidEvent,
        InvoiceVoidedEvent {

  String eventType();

  String entityType();

  UUID entityId();

  UUID projectId();

  UUID actorMemberId();

  String actorName();

  String tenantId();

  /** Clerk org ID for shared-schema ORG_ID binding. May be null for dedicated tenants. */
  String orgId();

  Instant occurredAt();

  Map<String, Object> details();
}
