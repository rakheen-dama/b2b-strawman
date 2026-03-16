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
  void shouldProvisionStarterTenantWithDedicatedSchema() {
    String clerkOrgId = "org_provision_test";
    String orgName = "Provision Test Org";

    var result = provisioningService.provisionTenant(clerkOrgId, orgName, null);

    assertThat(result.success()).isTrue();
    assertThat(result.alreadyProvisioned()).isFalse();
    assertThat(result.schemaName()).matches("tenant_[0-9a-f]{12}");

    var org = organizationRepository.findByClerkOrgId(clerkOrgId);
    assertThat(org).isPresent();
    assertThat(org.get().getProvisioningStatus())
        .isEqualTo(Organization.ProvisioningStatus.COMPLETED);
    assertThat(org.get().getTier()).isEqualTo(Tier.STARTER);

    var mapping = mappingRepository.findByClerkOrgId(clerkOrgId);
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getSchemaName()).matches("tenant_[0-9a-f]{12}");
  }

  @Test
  void shouldBeIdempotent() {
    String clerkOrgId = "org_idempotent_test";
    String orgName = "Idempotent Test Org";

    var result1 = provisioningService.provisionTenant(clerkOrgId, orgName, null);
    var result2 = provisioningService.provisionTenant(clerkOrgId, orgName, null);

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

    var result = provisioningService.provisionTenant("org_pro_test", "Pro Org", null);

    assertThat(result.success()).isTrue();
    assertThat(result.alreadyProvisioned()).isFalse();
    assertThat(result.schemaName()).matches("tenant_[0-9a-f]{12}");
    assertThat(schemaExists(result.schemaName())).isTrue();
    assertThat(tableExists(result.schemaName(), "projects")).isTrue();
    assertThat(tableExists(result.schemaName(), "documents")).isTrue();
  }

  @Test
  void shouldSetCurrencyFromVerticalProfileWhenProvisioningWithAccountingZa() throws SQLException {
    String clerkOrgId = "org_vertical_za_test";
    String orgName = "SA Accounting Firm";

    var result = provisioningService.provisionTenant(clerkOrgId, orgName, "accounting-za");

    assertThat(result.success()).isTrue();
    String currency = getOrgSettingsCurrency(result.schemaName());
    assertThat(currency).isEqualTo("ZAR");
  }

  @Test
  void shouldDefaultToUsdWhenProvisionedWithoutVerticalProfile() throws SQLException {
    String clerkOrgId = "org_no_vertical_test";
    String orgName = "Generic Org";

    var result = provisioningService.provisionTenant(clerkOrgId, orgName, null);

    assertThat(result.success()).isTrue();
    // Without vertical profile, OrgSettings may not exist (created lazily)
    // or if it exists, the default currency should be USD
    String currency = getOrgSettingsCurrency(result.schemaName());
    if (currency != null) {
      assertThat(currency).isEqualTo("USD");
    }
  }

  @Test
  void starterOrgsGetSeparateDedicatedSchemas() {
    var result1 = provisioningService.provisionTenant("org_diff_a", "Org A", null);
    var result2 = provisioningService.provisionTenant("org_diff_b", "Org B", null);

    assertThat(result1.schemaName()).matches("tenant_[0-9a-f]{12}");
    assertThat(result2.schemaName()).matches("tenant_[0-9a-f]{12}");
    assertThat(result1.schemaName()).isNotEqualTo(result2.schemaName());
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

  private String getOrgSettingsCurrency(String schemaName) throws SQLException {
    try (var conn = migrationDataSource.getConnection();
        var stmt =
            conn.prepareStatement(
                "SELECT default_currency FROM \"" + schemaName + "\".org_settings LIMIT 1")) {
      var rs = stmt.executeQuery();
      if (rs.next()) {
        return rs.getString(1);
      }
      return null;
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
