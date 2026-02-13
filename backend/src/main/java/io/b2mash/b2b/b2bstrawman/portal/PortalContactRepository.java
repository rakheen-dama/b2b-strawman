package io.b2mash.b2b.b2bstrawman.portal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortalContactRepository extends JpaRepository<PortalContact, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT pc FROM PortalContact pc WHERE pc.id = :id")
  Optional<PortalContact> findOneById(@Param("id") UUID id);

  @Query("SELECT pc FROM PortalContact pc WHERE pc.email = :email AND pc.orgId = :orgId")
  Optional<PortalContact> findByEmailAndOrgId(
      @Param("email") String email, @Param("orgId") String orgId);

  @Query("SELECT pc FROM PortalContact pc WHERE pc.customerId = :customerId")
  List<PortalContact> findByCustomerId(@Param("customerId") UUID customerId);

  boolean existsByEmailAndCustomerId(String email, UUID customerId);
}
