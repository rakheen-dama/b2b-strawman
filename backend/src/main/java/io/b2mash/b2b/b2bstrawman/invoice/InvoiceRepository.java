package io.b2mash.b2b.b2bstrawman.invoice;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
  @Query("SELECT i FROM Invoice i WHERE i.customerId = :customerId ORDER BY i.createdAt DESC")
  List<Invoice> findByCustomerId(@Param("customerId") UUID customerId);

  @Query("SELECT i FROM Invoice i WHERE i.status = :status ORDER BY i.createdAt DESC")
  List<Invoice> findByStatus(@Param("status") InvoiceStatus status);

  @Query("SELECT i FROM Invoice i ORDER BY i.createdAt DESC")
  List<Invoice> findAllOrdered();

  @Query(
      """
      SELECT i FROM Invoice i WHERE i.id IN (
        SELECT DISTINCT il.invoiceId FROM InvoiceLine il WHERE il.projectId = :projectId
      ) ORDER BY i.createdAt DESC
      """)
  List<Invoice> findByProjectId(@Param("projectId") UUID projectId);

  /** Counts invoices linked to a project via invoice lines. Used by delete protection guard. */
  @Query(
      """
      SELECT COUNT(DISTINCT i) FROM Invoice i WHERE i.id IN (
        SELECT DISTINCT il.invoiceId FROM InvoiceLine il WHERE il.projectId = :projectId
      )
      """)
  long countByProjectId(@Param("projectId") UUID projectId);

  /** Counts invoices for a customer. Used by customer archive protection guard. */
  @Query("SELECT COUNT(i) FROM Invoice i WHERE i.customerId = :customerId")
  long countByCustomerId(@Param("customerId") UUID customerId);

  /**
   * JPQL-based batch lookup scoped to the current tenant schema (search_path isolation), unlike
   * JpaRepository.findAllById which uses EntityManager.find directly.
   */
}
