package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Compact view of an unbilled, approved disbursement — used by 487A's invoice-generation flow and
 * exposed via {@code GET /api/legal/disbursements/unbilled} (owned by slice 487A). Declared here so
 * the projection can be reused by 486B's {@code listForStatement} pipeline and kept in sync with
 * the approved-unbilled selection criteria.
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

  public static UnbilledDisbursementDto from(LegalDisbursement d) {
    return new UnbilledDisbursementDto(
        d.getId(),
        d.getIncurredDate(),
        DisbursementCategory.fromString(d.getCategory()),
        d.getDescription(),
        d.getAmount(),
        VatTreatment.valueOf(d.getVatTreatment()),
        d.getVatAmount(),
        d.getSupplierName());
  }
}
