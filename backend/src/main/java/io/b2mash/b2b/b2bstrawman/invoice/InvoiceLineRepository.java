package io.b2mash.b2b.b2bstrawman.invoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {
  /** JPQL-based query scoped to the current tenant schema via search_path. */
  @Query("SELECT il FROM InvoiceLine il WHERE il.invoiceId = :invoiceId ORDER BY il.sortOrder")
  List<InvoiceLine> findByInvoiceIdOrderBySortOrder(@Param("invoiceId") UUID invoiceId);

  /** Finds a line item by time entry ID for double-billing prevention checks. */
  @Query("SELECT il FROM InvoiceLine il WHERE il.timeEntryId = :timeEntryId")
  Optional<InvoiceLine> findByTimeEntryId(@Param("timeEntryId") UUID timeEntryId);

  /** Checks whether any line on the invoice has a per-line tax rate applied. */
  @Query(
      "SELECT CASE WHEN COUNT(il) > 0 THEN true ELSE false END FROM InvoiceLine il"
          + " WHERE il.invoiceId = :invoiceId AND il.taxRateId IS NOT NULL")
  boolean existsByInvoiceIdAndTaxRateIdIsNotNull(@Param("invoiceId") UUID invoiceId);

  /**
   * Finds all invoice lines referencing a tax rate where the parent invoice has the given status.
   */
  @Query(
      "SELECT il FROM InvoiceLine il WHERE il.taxRateId = :taxRateId"
          + " AND il.invoiceId IN (SELECT i.id FROM Invoice i WHERE i.status = :status)")
  List<InvoiceLine> findByTaxRateIdAndInvoice_Status(
      @Param("taxRateId") UUID taxRateId, @Param("status") InvoiceStatus status);
}
