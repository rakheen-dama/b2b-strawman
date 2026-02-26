package io.b2mash.b2b.b2bstrawman.tax.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateTaxRateRequest(
    @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must not exceed 100 characters")
        String name,
    @NotNull(message = "rate is required")
        @DecimalMin(value = "0.00", message = "rate must be at least 0.00")
        @DecimalMax(value = "99.99", message = "rate must not exceed 99.99")
        BigDecimal rate,
    boolean isDefault,
    boolean isExempt,
    int sortOrder) {}
