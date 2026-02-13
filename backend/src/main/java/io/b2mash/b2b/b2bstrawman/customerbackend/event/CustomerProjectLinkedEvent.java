package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import java.util.UUID;

/** Published when a customer is linked to a project. */
public final class CustomerProjectLinkedEvent extends PortalDomainEvent {

  private final UUID customerId;
  private final UUID projectId;

  public CustomerProjectLinkedEvent(
      UUID customerId, UUID projectId, String orgId, String tenantId) {
    super(orgId, tenantId);
    this.customerId = customerId;
    this.projectId = projectId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public UUID getProjectId() {
    return projectId;
  }
}
