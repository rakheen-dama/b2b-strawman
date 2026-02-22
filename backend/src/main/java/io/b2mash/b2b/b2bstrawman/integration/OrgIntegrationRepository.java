package io.b2mash.b2b.b2bstrawman.integration;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgIntegrationRepository extends JpaRepository<OrgIntegration, UUID> {

  Optional<OrgIntegration> findByDomain(IntegrationDomain domain);
}
