package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Statement-of-account line projection of a {@code LegalDisbursement}, ordered by category then
 * incurred date by the backing repository query. Consumed by slice 491A's Statement of Account
 * generation; returned by {@code DisbursementService.listForStatement(...)}.
 */
public record DisbursementStatementDto(
    UUID id,
    LocalDate incurredDate,
    DisbursementCategory category,
    String description,
    BigDecimal amount,
    BigDecimal vatAmount,
    String supplierName,
    String supplierReference) {

  public static DisbursementStatementDto from(LegalDisbursement d) {
    return new DisbursementStatementDto(
        d.getId(),
        d.getIncurredDate(),
        DisbursementCategory.fromString(d.getCategory()),
        d.getDescription(),
        d.getAmount(),
        d.getVatAmount(),
        d.getSupplierName(),
        d.getSupplierReference());
  }
}
