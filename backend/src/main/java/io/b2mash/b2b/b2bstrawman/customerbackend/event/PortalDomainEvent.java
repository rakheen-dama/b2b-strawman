package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import java.time.Instant;

/**
 * Base class for portal domain events. These events are published by core services and consumed by
 * PortalEventHandler to update the portal read-model schema.
 *
 * <p>All subclasses carry only serializable primitives (String, UUID, Instant, BigDecimal) -- no
 * JPA entity references. This enables future migration to SQS/SNS messaging.
 */
public abstract sealed class PortalDomainEvent
    permits CustomerCreatedEvent,
        CustomerUpdatedEvent,
        ProjectCreatedEvent,
        ProjectUpdatedEvent,
        CustomerProjectLinkedEvent,
        CustomerProjectUnlinkedEvent,
        DocumentCreatedEvent,
        DocumentVisibilityChangedEvent,
        DocumentDeletedEvent,
        TimeEntryAggregatedEvent,
        InvoiceSyncEvent {

  private final String orgId;
  private final String tenantId;
  private final Instant occurredAt;

  protected PortalDomainEvent(String orgId, String tenantId) {
    this.orgId = orgId;
    this.tenantId = tenantId;
    this.occurredAt = Instant.now();
  }

  public String getOrgId() {
    return orgId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
