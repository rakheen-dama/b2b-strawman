package io.b2mash.b2b.b2bstrawman.provisioning;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
  Optional<Organization> findByExternalOrgId(String externalOrgId);
}
