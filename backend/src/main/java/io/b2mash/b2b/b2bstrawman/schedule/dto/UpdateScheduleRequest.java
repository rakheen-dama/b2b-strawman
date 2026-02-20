package io.b2mash.b2b.b2bstrawman.schedule.dto;

import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateScheduleRequest(
    String nameOverride,
    LocalDate endDate,
    @PositiveOrZero int leadTimeDays,
    UUID projectLeadMemberId) {}
