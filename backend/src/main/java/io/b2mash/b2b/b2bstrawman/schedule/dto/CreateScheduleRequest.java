package io.b2mash.b2b.b2bstrawman.schedule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.UUID;

public record CreateScheduleRequest(
    @NotNull UUID templateId,
    @NotNull UUID customerId,
    @NotNull String frequency,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    @PositiveOrZero int leadTimeDays,
    UUID projectLeadMemberId,
    String nameOverride) {}
