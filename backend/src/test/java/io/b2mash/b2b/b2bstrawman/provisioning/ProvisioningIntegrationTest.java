package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProvisioningIntegrationTest {

  @Autowired private TenantProvisioningService provisioningService;

  @Autowired private OrganizationRepository organizationRepository;

  @Autowired private OrgSchemaMappingRepository mappingRepository;

  @Autowired
  @Qualifier("migrationDataSource")
  private DataSource migrationDataSource;

  @Test
  void shouldProvisionStarterTenantToSharedSchema() {
    String clerkOrgId = "org_provision_test";
    String orgName = "Provision Test Org";

    var result = provisioningService.provisionTenant(clerkOrgId, orgName);

    assertThat(result.success()).isTrue();
    assertThat(result.alreadyProvisioned()).isFalse();
    assertThat(result.schemaName()).isEqualTo("tenant_shared");

    var org = organizationRepository.findByClerkOrgId(clerkOrgId);
    assertThat(org).isPresent();
    assertThat(org.get().getProvisioningStatus())
        .isEqualTo(Organization.ProvisioningStatus.COMPLETED);
    assertThat(org.get().getTier()).isEqualTo(Tier.STARTER);

    var mapping = mappingRepository.findByClerkOrgId(clerkOrgId);
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getSchemaName()).isEqualTo("tenant_shared");
  }

  @Test
  void shouldBeIdempotent() {
    String clerkOrgId = "org_idempotent_test";
    String orgName = "Idempotent Test Org";

    var result1 = provisioningService.provisionTenant(clerkOrgId, orgName);
    var result2 = provisioningService.provisionTenant(clerkOrgId, orgName);

    assertThat(result1.success()).isTrue();
    assertThat(result1.alreadyProvisioned()).isFalse();
    assertThat(result2.success()).isTrue();
    assertThat(result2.alreadyProvisioned()).isTrue();
    assertThat(result1.schemaName()).isEqualTo(result2.schemaName());
  }

  @Test
  void shouldProvisionProTenantWithDedicatedSchema() throws SQLException {
    // Set up a Pro org first
    var org = new Organization("org_pro_test", "Pro Org");
    org.updatePlan(Tier.PRO, "pro_plan");
    organizationRepository.save(org);

    var result = provisioningService.provisionTenant("org_pro_test", "Pro Org");

    assertThat(result.success()).isTrue();
    assertThat(result.alreadyProvisioned()).isFalse();
    assertThat(result.schemaName()).matches("tenant_[0-9a-f]{12}");
    assertThat(schemaExists(result.schemaName())).isTrue();
    assertThat(tableExists(result.schemaName(), "projects")).isTrue();
    assertThat(tableExists(result.schemaName(), "documents")).isTrue();
  }

  @Test
  void starterOrgsShouldShareSchema() {
    var result1 = provisioningService.provisionTenant("org_diff_a", "Org A");
    var result2 = provisioningService.provisionTenant("org_diff_b", "Org B");

    assertThat(result1.schemaName()).isEqualTo("tenant_shared");
    assertThat(result2.schemaName()).isEqualTo("tenant_shared");
  }

  private boolean schemaExists(String schemaName) throws SQLException {
    try (var conn = migrationDataSource.getConnection();
        var stmt =
            conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)")) {
      stmt.setString(1, schemaName);
      var rs = stmt.executeQuery();
      return rs.next() && rs.getBoolean(1);
    }
  }

  private boolean tableExists(String schemaName, String tableName) throws SQLException {
    try (var conn = migrationDataSource.getConnection();
        var stmt =
            conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM information_schema.tables "
                    + "WHERE table_schema = ? AND table_name = ?)")) {
      stmt.setString(1, schemaName);
      stmt.setString(2, tableName);
      var rs = stmt.executeQuery();
      return rs.next() && rs.getBoolean(1);
    }
  }
}
