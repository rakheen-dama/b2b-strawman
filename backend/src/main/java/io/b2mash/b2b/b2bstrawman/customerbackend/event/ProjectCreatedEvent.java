package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import java.util.UUID;

/** Published when a project is created. */
public final class ProjectCreatedEvent extends PortalDomainEvent {

  private final UUID projectId;
  private final String name;
  private final String description;
  private final String status;

  public ProjectCreatedEvent(
      UUID projectId,
      String name,
      String description,
      String status,
      String orgId,
      String tenantId) {
    super(orgId, tenantId);
    this.projectId = projectId;
    this.name = name;
    this.description = description;
    this.status = status;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getStatus() {
    return status;
  }
}
