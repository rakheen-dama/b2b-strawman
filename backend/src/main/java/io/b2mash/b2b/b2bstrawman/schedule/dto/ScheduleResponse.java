package io.b2mash.b2b.b2bstrawman.schedule.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleResponse(
    UUID id,
    UUID templateId,
    String templateName,
    UUID customerId,
    String customerName,
    String frequency,
    LocalDate startDate,
    LocalDate endDate,
    int leadTimeDays,
    String status,
    LocalDate nextExecutionDate,
    Instant lastExecutedAt,
    int executionCount,
    UUID projectLeadMemberId,
    String projectLeadName,
    String nameOverride,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt) {}
