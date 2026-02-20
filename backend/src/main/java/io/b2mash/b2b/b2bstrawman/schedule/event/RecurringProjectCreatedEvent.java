package io.b2mash.b2b.b2bstrawman.schedule.event;

import java.time.Instant;
import java.util.UUID;

public record RecurringProjectCreatedEvent(
    UUID scheduleId,
    UUID projectId,
    String projectName,
    String customerName,
    String templateName,
    UUID projectLeadMemberId,
    UUID actorMemberId,
    String actorName,
    String tenantId,
    String orgId,
    Instant occurredAt) {}
