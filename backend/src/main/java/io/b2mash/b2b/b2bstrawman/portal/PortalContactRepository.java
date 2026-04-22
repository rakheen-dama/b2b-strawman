package io.b2mash.b2b.b2bstrawman.portal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortalContactRepository extends JpaRepository<PortalContact, UUID> {
  @Query("SELECT pc FROM PortalContact pc WHERE pc.email = :email AND pc.orgId = :orgId")
  Optional<PortalContact> findByEmailAndOrgId(
      @Param("email") String email, @Param("orgId") String orgId);

  @Query("SELECT pc FROM PortalContact pc WHERE pc.customerId = :customerId")
  List<PortalContact> findByCustomerId(@Param("customerId") UUID customerId);

  /** Counts portal contacts for a customer. Used by anonymization preview. */
  @Query("SELECT COUNT(pc) FROM PortalContact pc WHERE pc.customerId = :customerId")
  long countByCustomerId(@Param("customerId") UUID customerId);

  boolean existsByEmailAndCustomerId(String email, UUID customerId);

  @Query("SELECT pc FROM PortalContact pc WHERE pc.customerId = :customerId AND pc.orgId = :orgId")
  Optional<PortalContact> findByCustomerIdAndOrgId(
      @Param("customerId") UUID customerId, @Param("orgId") String orgId);

  /**
   * Resolve the best-matching portal contact for a customer in an org. When multiple contacts exist
   * (e.g. one auto-provisioned GENERAL + one manually created PRIMARY), return the PRIMARY first,
   * then BILLING, then GENERAL, falling back to the oldest active contact within the same role.
   * Used by {@code CustomerAuthFilter} so JWT-based portal session resolution is deterministic even
   * after GAP-L-34 auto-provisioning.
   */
  @Query(
      "SELECT pc FROM PortalContact pc WHERE pc.customerId = :customerId AND pc.orgId = :orgId AND"
          + " pc.status = 'ACTIVE' ORDER BY CASE pc.role WHEN 'PRIMARY' THEN 0 WHEN 'BILLING' THEN"
          + " 1 ELSE 2 END ASC, pc.createdAt ASC LIMIT 1")
  Optional<PortalContact> findPreferredByCustomerIdAndOrgId(
      @Param("customerId") UUID customerId, @Param("orgId") String orgId);

  @Query(
      "SELECT pc FROM PortalContact pc WHERE pc.customerId = :customerId AND pc.role = :role AND"
          + " pc.status = 'ACTIVE' ORDER BY pc.createdAt ASC LIMIT 1")
  Optional<PortalContact> findFirstByCustomerIdAndRoleAndStatusActive(
      @Param("customerId") UUID customerId, @Param("role") PortalContact.ContactRole role);
}
