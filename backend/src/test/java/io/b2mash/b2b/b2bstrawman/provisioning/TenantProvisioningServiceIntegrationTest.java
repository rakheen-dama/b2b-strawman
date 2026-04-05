package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.sql.SQLException;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for tenant provisioning with vertical profiles. Covers task 372.2: provisioning
 * with legal-za profile sets correct modules, terminology, and currency, and the trust accounting
 * stub endpoint is accessible for the provisioned tenant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantProvisioningServiceIntegrationTest {
  private static final String ORG_ID = "org_tpsi_legal_za";
  private static final Set<String> ALLOWED_COLUMNS =
      Set.of("enabled_modules", "terminology_namespace", "default_currency", "vertical_profile");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MockMvc mockMvc;

  @Autowired
  @Qualifier("migrationDataSource")
  private DataSource migrationDataSource;

  private String tenantSchema;

  @BeforeAll
  void provisionTenantWithLegalProfile() throws Exception {
    var result = provisioningService.provisionTenant(ORG_ID, "Legal ZA Firm", "legal-za");
    assertThat(result.success()).isTrue();
    tenantSchema = result.schemaName();

    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_tpsi_owner", "tpsi_owner@test.com", "TPSI Owner", "owner");
  }

  @Test
  void provisionWithLegalZa_setsEnabledModulesTerminologyAndCurrency() throws SQLException {
    // Verify enabled_modules via direct SQL
    String enabledModules = getOrgSettingsColumn(tenantSchema, "enabled_modules");
    assertThat(enabledModules).isNotNull();
    assertThat(enabledModules).contains("court_calendar");
    assertThat(enabledModules).contains("conflict_check");
    assertThat(enabledModules).contains("lssa_tariff");
    assertThat(enabledModules).contains("trust_accounting");

    // Verify terminology_namespace via direct SQL
    String terminologyNamespace = getOrgSettingsColumn(tenantSchema, "terminology_namespace");
    assertThat(terminologyNamespace).isEqualTo("en-ZA-legal");

    // Verify currency via direct SQL
    String currency = getOrgSettingsColumn(tenantSchema, "default_currency");
    assertThat(currency).isEqualTo("ZAR");
  }

  @Test
  void provisionWithLegalZa_courtCalendarListReturns200() throws Exception {
    mockMvc
        .perform(get("/api/court-dates").with(TestJwtFactory.ownerJwt(ORG_ID, "user_tpsi_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  // --- Helpers ---

  private String getOrgSettingsColumn(String schemaName, String columnName) throws SQLException {
    if (!ALLOWED_COLUMNS.contains(columnName)) {
      throw new IllegalArgumentException("Invalid column: " + columnName);
    }
    try (var conn = migrationDataSource.getConnection();
        var stmt =
            conn.prepareStatement(
                "SELECT " + columnName + " FROM \"" + schemaName + "\".org_settings LIMIT 1")) {
      var rs = stmt.executeQuery();
      if (rs.next()) {
        return rs.getString(1);
      }
      return null;
    }
  }
}
