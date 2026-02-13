package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published when time entry aggregation data changes for a project. */
public final class TimeEntryAggregatedEvent extends PortalDomainEvent {

  private final UUID projectId;
  private final BigDecimal totalHours;
  private final BigDecimal billableHours;
  private final Instant lastActivityAt;

  public TimeEntryAggregatedEvent(
      UUID projectId,
      BigDecimal totalHours,
      BigDecimal billableHours,
      Instant lastActivityAt,
      String orgId,
      String tenantId) {
    super(orgId, tenantId);
    this.projectId = projectId;
    this.totalHours = totalHours;
    this.billableHours = billableHours;
    this.lastActivityAt = lastActivityAt;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public BigDecimal getTotalHours() {
    return totalHours;
  }

  public BigDecimal getBillableHours() {
    return billableHours;
  }

  public Instant getLastActivityAt() {
    return lastActivityAt;
  }
}
