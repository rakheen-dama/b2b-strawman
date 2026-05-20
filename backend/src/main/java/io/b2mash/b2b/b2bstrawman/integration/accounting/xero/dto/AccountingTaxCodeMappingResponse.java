package io.b2mash.b2b.b2bstrawman.integration.accounting.xero.dto;

import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingTaxCodeMapping;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for tax code mappings. Prevents JPA entity leakage through the REST API by mapping
 * from {@link AccountingTaxCodeMapping} entity fields.
 */
public record AccountingTaxCodeMappingResponse(
    UUID id,
    String providerId,
    String kaziTaxMode,
    String externalTaxCode,
    String displayLabel,
    boolean isDefault,
    Instant createdAt,
    Instant updatedAt) {

  public static AccountingTaxCodeMappingResponse from(AccountingTaxCodeMapping entity) {
    return new AccountingTaxCodeMappingResponse(
        entity.getId(),
        entity.getProviderId(),
        entity.getKaziTaxMode(),
        entity.getExternalTaxCode(),
        entity.getDisplayLabel(),
        entity.isDefault(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
