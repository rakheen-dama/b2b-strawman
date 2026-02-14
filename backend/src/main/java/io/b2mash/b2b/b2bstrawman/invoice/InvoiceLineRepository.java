package io.b2mash.b2b.b2bstrawman.invoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT il FROM InvoiceLine il WHERE il.id = :id")
  Optional<InvoiceLine> findOneById(@Param("id") UUID id);

  /**
   * Finds all invoice lines for the given invoice, ordered by sortOrder ascending.
   *
   * @param invoiceId the invoice ID
   * @return list of invoice lines, ordered by sortOrder
   */
  List<InvoiceLine> findByInvoiceIdOrderBySortOrder(UUID invoiceId);

  /**
   * Deletes all invoice lines for the given invoice. Used when voiding or deleting a draft invoice.
   *
   * @param invoiceId the invoice ID
   */
  @Modifying
  @Query("DELETE FROM InvoiceLine il WHERE il.invoiceId = :invoiceId")
  void deleteByInvoiceId(@Param("invoiceId") UUID invoiceId);

  /**
   * Finds the invoice line linked to a specific time entry. Returns empty if no line exists (time
   * entry not yet billed).
   *
   * @param timeEntryId the time entry ID
   * @return the invoice line linked to this time entry, or empty
   */
  Optional<InvoiceLine> findByTimeEntryId(UUID timeEntryId);
}
