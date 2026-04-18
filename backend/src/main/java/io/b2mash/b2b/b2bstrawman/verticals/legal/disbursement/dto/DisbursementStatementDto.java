package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Compact projection of an approved disbursement for the Statement of Account PDF (Epic 491A).
 * Ordered by category then incurred date when fetched via the repository.
 */
public record DisbursementStatementDto(
    UUID id,
    LocalDate incurredDate,
    DisbursementCategory category,
    String description,
    BigDecimal amount,
    VatTreatment vatTreatment,
    BigDecimal vatAmount,
    String supplierName) {

  /** Maps a LegalDisbursement entity to the statement projection. */
  public static DisbursementStatementDto from(LegalDisbursement d) {
    return new DisbursementStatementDto(
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
