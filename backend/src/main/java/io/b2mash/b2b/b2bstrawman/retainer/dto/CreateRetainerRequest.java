package io.b2mash.b2b.b2bstrawman.retainer.dto;

import io.b2mash.b2b.b2bstrawman.retainer.RetainerFrequency;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerType;
import io.b2mash.b2b.b2bstrawman.retainer.RolloverPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateRetainerRequest(
    @NotNull UUID customerId,
    UUID scheduleId,
    @NotBlank String name,
    @NotNull RetainerType type,
    @NotNull RetainerFrequency frequency,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    @Positive BigDecimal allocatedHours,
    @Positive BigDecimal periodFee,
    RolloverPolicy rolloverPolicy,
    @Positive BigDecimal rolloverCapHours,
    String notes) {}
