package io.b2mash.b2b.b2bstrawman.schedule.event;

import java.time.Instant;
import java.util.UUID;

public record ScheduleCompletedEvent(
    UUID scheduleId,
    String templateName,
    String customerName,
    int executionCount,
    String tenantId,
    String orgId,
    Instant occurredAt) {}
