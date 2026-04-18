package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Compact projection of a disbursement for the unbilled panel consumed by the invoice-generation
 * flow (Epic 487A).
 */
public record UnbilledDisbursementDto(
    UUID id,
    LocalDate incurredDate,
    DisbursementCategory category,
    String description,
    BigDecimal amount,
    VatTreatment vatTreatment,
    BigDecimal vatAmount,
    String supplierName) {

  /** Maps a LegalDisbursement entity to the unbilled projection. */
  public static UnbilledDisbursementDto from(LegalDisbursement d) {
    return new UnbilledDisbursementDto(
        d.getId(),
        d.getIncurredDate(),
        DisbursementCategory.valueOf(d.getCategory()),
        d.getDescription(),
        d.getAmount(),
        VatTreatment.valueOf(d.getVatTreatment()),
        d.getVatAmount(),
        d.getSupplierName());
  }
}
