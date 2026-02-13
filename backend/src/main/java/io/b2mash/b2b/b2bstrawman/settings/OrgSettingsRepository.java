package io.b2mash.b2b.b2bstrawman.settings;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgSettingsRepository extends JpaRepository<OrgSettings, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT s FROM OrgSettings s WHERE s.id = :id")
  Optional<OrgSettings> findOneById(@Param("id") UUID id);

  /** Find the org settings row for the current tenant (unique per tenant). */
  Optional<OrgSettings> findByTenantId(String tenantId);

  /**
   * Find the org settings for the current tenant using only the Hibernate @Filter for isolation.
   * More efficient than findAll() when we know there's at most one row per tenant.
   */
  @Query("SELECT s FROM OrgSettings s")
  Optional<OrgSettings> findForCurrentTenant();
}
