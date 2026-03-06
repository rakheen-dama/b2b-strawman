package io.b2mash.b2b.b2bstrawman.multitenancy;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgSchemaMappingRepository extends JpaRepository<OrgSchemaMapping, UUID> {
  Optional<OrgSchemaMapping> findByExternalOrgId(String externalOrgId);

  default Optional<OrgSchemaMapping> findByClerkOrgId(String clerkOrgId) {
    return findByExternalOrgId(clerkOrgId);
  }

  Optional<OrgSchemaMapping> findBySchemaName(String schemaName);
}
