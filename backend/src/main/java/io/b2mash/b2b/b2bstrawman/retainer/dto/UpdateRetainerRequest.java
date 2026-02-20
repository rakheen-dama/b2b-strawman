package io.b2mash.b2b.b2bstrawman.retainer.dto;

import io.b2mash.b2b.b2bstrawman.retainer.RolloverPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateRetainerRequest(
    @NotBlank String name,
    @Positive BigDecimal allocatedHours,
    @Positive BigDecimal periodFee,
    RolloverPolicy rolloverPolicy,
    @Positive BigDecimal rolloverCapHours,
    LocalDate endDate,
    String notes) {}
