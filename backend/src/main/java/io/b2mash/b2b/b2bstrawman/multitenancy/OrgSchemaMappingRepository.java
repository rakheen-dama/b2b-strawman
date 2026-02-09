package io.b2mash.b2b.b2bstrawman.multitenancy;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrgSchemaMappingRepository extends JpaRepository<OrgSchemaMapping, UUID> {
  Optional<OrgSchemaMapping> findByClerkOrgId(String clerkOrgId);

  @Query(
      """
      SELECT new io.b2mash.b2b.b2bstrawman.multitenancy.TenantInfo(m.schemaName, o.tier)
      FROM OrgSchemaMapping m JOIN io.b2mash.b2b.b2bstrawman.provisioning.Organization o
        ON m.clerkOrgId = o.clerkOrgId
      WHERE m.clerkOrgId = :clerkOrgId
      """)
  Optional<TenantInfo> findTenantInfoByClerkOrgId(String clerkOrgId);
}
