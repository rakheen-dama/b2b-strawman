package io.b2mash.b2b.b2bstrawman.invoice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A line added to a draft fee note. Tax: {@code taxRateId} applies that rate; otherwise {@code
 * noTaxRate = true} leaves the line untaxed (the line form's "None"); otherwise the org default
 * rate applies, so clients that send neither keep the default-rate behaviour.
 */
public record AddLineItemRequest(
    UUID matterId,
    String description,
    @NotNull @Positive BigDecimal quantity,
    @PositiveOrZero BigDecimal unitPrice,
    @PositiveOrZero Integer sortOrder,
    UUID taxRateId,
    UUID tariffItemId,
    Boolean noTaxRate) {}
