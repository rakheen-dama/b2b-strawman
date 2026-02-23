package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import java.util.UUID;

/** Published when a task is updated in a customer-linked project. */
public final class PortalTaskUpdatedEvent extends PortalDomainEvent {

  private final UUID taskId;
  private final UUID projectId;
  private final String name;
  private final String status;
  private final String assigneeName;
  private final int sortOrder;

  public PortalTaskUpdatedEvent(
      UUID taskId,
      UUID projectId,
      String name,
      String status,
      String assigneeName,
      int sortOrder,
      String orgId,
      String tenantId) {
    super(orgId, tenantId);
    this.taskId = taskId;
    this.projectId = projectId;
    this.name = name;
    this.status = status;
    this.assigneeName = assigneeName;
    this.sortOrder = sortOrder;
  }

  public UUID getTaskId() {
    return taskId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public String getAssigneeName() {
    return assigneeName;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
