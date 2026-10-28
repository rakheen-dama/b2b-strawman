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
 *
 * <p>{@code categoryLabel} is the client-facing {@link DisbursementCategory#label()} ("Sheriff
 * fees") the SoA Category column prints (Phase 88 626E, G-C12); the SoA builder converts rows to
 * maps, so the record component name is the template key.
 */
public record DisbursementStatementDto(
    UUID id,
    LocalDate incurredDate,
    DisbursementCategory category,
    String categoryLabel,
    String description,
    BigDecimal amount,
    BigDecimal vatAmount,
    String supplierName,
    String supplierReference) {

  /** Convenience constructor deriving {@code categoryLabel} from {@code category}. */
  public DisbursementStatementDto(
      UUID id,
      LocalDate incurredDate,
      DisbursementCategory category,
      String description,
      BigDecimal amount,
      BigDecimal vatAmount,
      String supplierName,
      String supplierReference) {
    this(
        id,
        incurredDate,
        category,
        category != null ? category.label() : null,
        description,
        amount,
        vatAmount,
        supplierName,
        supplierReference);
  }

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
