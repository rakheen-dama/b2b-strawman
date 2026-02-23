package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import java.util.UUID;

/** Published when a task is deleted from a customer-linked project. */
public final class PortalTaskDeletedEvent extends PortalDomainEvent {

  private final UUID taskId;

  public PortalTaskDeletedEvent(UUID taskId, String orgId, String tenantId) {
    super(orgId, tenantId);
    this.taskId = taskId;
  }

  public UUID getTaskId() {
    return taskId;
  }
}
