package io.b2mash.b2b.b2bstrawman.invoice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

public record AddLineItemRequest(
    UUID projectId,
    String description,
    @NotNull @Positive BigDecimal quantity,
    @PositiveOrZero BigDecimal unitPrice,
    @PositiveOrZero Integer sortOrder,
    UUID taxRateId,
    UUID tariffItemId) {}
