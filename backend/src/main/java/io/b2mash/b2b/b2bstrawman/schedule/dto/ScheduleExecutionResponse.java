package io.b2mash.b2b.b2bstrawman.schedule.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleExecutionResponse(
    UUID id,
    UUID projectId,
    String projectName,
    LocalDate periodStart,
    LocalDate periodEnd,
    Instant executedAt) {}
