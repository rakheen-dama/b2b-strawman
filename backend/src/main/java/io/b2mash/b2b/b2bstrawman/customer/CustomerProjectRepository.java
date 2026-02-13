package io.b2mash.b2b.b2bstrawman.customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerProjectRepository extends JpaRepository<CustomerProject, UUID> {

  List<CustomerProject> findByCustomerId(UUID customerId);

  List<CustomerProject> findByProjectId(UUID projectId);

  boolean existsByCustomerIdAndProjectId(UUID customerId, UUID projectId);

  @Modifying
  @Query(
      "DELETE FROM CustomerProject cp WHERE cp.customerId = :customerId AND cp.projectId = :projectId AND cp.tenantId = :tenantId")
  void deleteByCustomerIdAndProjectId(
      @Param("customerId") UUID customerId,
      @Param("projectId") UUID projectId,
      @Param("tenantId") String tenantId);

  /**
   * Returns the first customer linked to the project, ordered by creation date (ASC). Used by
   * BillingRateService for customer-level rate resolution. Per ADR-039, most projects have one
   * customer; for multi-customer projects the first-linked customer determines the fallback rate.
   */
  @Query(
      """
      SELECT cp.customerId FROM CustomerProject cp
      WHERE cp.projectId = :projectId
      ORDER BY cp.createdAt ASC
      LIMIT 1
      """)
  Optional<UUID> findFirstCustomerByProjectId(@Param("projectId") UUID projectId);
}
