package io.b2mash.b2b.b2bstrawman.invoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

  @Query("SELECT i FROM Invoice i WHERE i.customerId = :customerId ORDER BY i.createdAt DESC")
  List<Invoice> findByCustomerId(@Param("customerId") UUID customerId);

  @Query("SELECT i FROM Invoice i WHERE i.status = :status ORDER BY i.createdAt DESC")
  List<Invoice> findByStatus(@Param("status") String status);
}
