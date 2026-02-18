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

  boolean existsByEmailAndCustomerId(String email, UUID customerId);

  @Query("SELECT pc FROM PortalContact pc WHERE pc.customerId = :customerId AND pc.orgId = :orgId")
  Optional<PortalContact> findByCustomerIdAndOrgId(
      @Param("customerId") UUID customerId, @Param("orgId") String orgId);
}
