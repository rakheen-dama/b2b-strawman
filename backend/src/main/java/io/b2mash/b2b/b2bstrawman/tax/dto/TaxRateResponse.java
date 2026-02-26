package io.b2mash.b2b.b2bstrawman.tax.dto;

import io.b2mash.b2b.b2bstrawman.tax.TaxRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TaxRateResponse(
    UUID id,
    String name,
    BigDecimal rate,
    boolean isDefault,
    boolean isExempt,
    boolean active,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt) {

  public static TaxRateResponse from(TaxRate taxRate) {
    return new TaxRateResponse(
        taxRate.getId(),
        taxRate.getName(),
        taxRate.getRate(),
        taxRate.isDefault(),
        taxRate.isExempt(),
        taxRate.isActive(),
        taxRate.getSortOrder(),
        taxRate.getCreatedAt(),
        taxRate.getUpdatedAt());
  }
}
