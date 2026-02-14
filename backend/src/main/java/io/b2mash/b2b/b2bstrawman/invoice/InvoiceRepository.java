package io.b2mash.b2b.b2bstrawman.invoice;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT i FROM Invoice i WHERE i.id = :id")
  Optional<Invoice> findOneById(@Param("id") UUID id);

  /**
   * Finds invoices matching optional filters, with pagination. All filter parameters are matched
   * exactly when non-null, or ignored when null.
   *
   * @param customerId optional customer ID filter
   * @param status optional status filter (exact match)
   * @param from optional date range start (inclusive) — filters by issueDate
   * @param to optional date range end (inclusive) — filters by issueDate
   * @param pageable pagination and sorting parameters
   * @return page of invoices matching the filters
   */
  @Query(
      """
      SELECT i FROM Invoice i
      WHERE (:customerId IS NULL OR i.customerId = :customerId)
        AND (:status IS NULL OR i.status = :status)
        AND (CAST(:from AS DATE) IS NULL OR i.issueDate >= :from)
        AND (CAST(:to AS DATE) IS NULL OR i.issueDate <= :to)
      ORDER BY i.createdAt DESC
      """)
  Page<Invoice> findByFilters(
      @Param("customerId") UUID customerId,
      @Param("status") InvoiceStatus status,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      Pageable pageable);
}
