package io.b2mash.b2b.b2bstrawman.settings;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrgSettingsRepository extends JpaRepository<OrgSettings, UUID> {
  /**
   * Find the org settings for the current tenant schema. More efficient than findAll() when we know
   * there is at most one row per tenant.
   */
  @Query("SELECT s FROM OrgSettings s")
  Optional<OrgSettings> findForCurrentTenant();
}
