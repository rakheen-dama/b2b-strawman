package io.b2mash.b2b.b2bstrawman.customer;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V89MigrationTest {
  private static final String ORG_ID = "org_v89_migration_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DataSource dataSource;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "V89 Migration Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_v89_owner", "v89_owner@test.com", "V89 Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void v89AddsAllNewColumnsWithCorrectTypes() throws Exception {
    // Customer columns: 13 new
    List<String[]> expectedColumns =
        List.of(
            new String[] {"customers", "registration_number", "character varying", "100"},
            new String[] {"customers", "address_line1", "character varying", "255"},
            new String[] {"customers", "address_line2", "character varying", "255"},
            new String[] {"customers", "city", "character varying", "100"},
            new String[] {"customers", "state_province", "character varying", "100"},
            new String[] {"customers", "postal_code", "character varying", "20"},
            new String[] {"customers", "country", "character varying", "2"},
            new String[] {"customers", "tax_number", "character varying", "100"},
            new String[] {"customers", "contact_name", "character varying", "255"},
            new String[] {"customers", "contact_email", "character varying", "255"},
            new String[] {"customers", "contact_phone", "character varying", "50"},
            new String[] {"customers", "entity_type", "character varying", "30"},
            new String[] {"customers", "financial_year_end", "date", null},
            // Project columns: 3 new
            new String[] {"projects", "reference_number", "character varying", "100"},
            new String[] {"projects", "priority", "character varying", "20"},
            new String[] {"projects", "work_type", "character varying", "50"},
            // Task columns: 1 new
            new String[] {"tasks", "estimated_hours", "numeric", null},
            // Invoice columns: 4 new
            new String[] {"invoices", "po_number", "character varying", "100"},
            new String[] {"invoices", "tax_type", "character varying", "20"},
            new String[] {"invoices", "billing_period_start", "date", null},
            new String[] {"invoices", "billing_period_end", "date", null});

    try (Connection conn = dataSource.getConnection()) {
      for (String[] spec : expectedColumns) {
        String tableName = spec[0];
        String columnName = spec[1];
        String expectedType = spec[2];
        String expectedLength = spec[3];

        try (PreparedStatement ps =
            conn.prepareStatement(
                "SELECT data_type, character_maximum_length FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
          ps.setString(1, tenantSchema);
          ps.setString(2, tableName);
          ps.setString(3, columnName);
          ResultSet rs = ps.executeQuery();
          assertThat(rs.next())
              .as("Column %s.%s should exist in schema %s", tableName, columnName, tenantSchema)
              .isTrue();
          assertThat(rs.getString("data_type"))
              .as("Column %s.%s data type", tableName, columnName)
              .isEqualTo(expectedType);
          if (expectedLength != null) {
            assertThat(rs.getString("character_maximum_length"))
                .as("Column %s.%s max length", tableName, columnName)
                .isEqualTo(expectedLength);
          }
        }
      }
    }
  }

  @Test
  void v89CreatesAllIndexes() throws Exception {
    List<String[]> expectedIndexes =
        List.of(
            new String[] {"customers", "idx_customers_registration_number"},
            new String[] {"customers", "idx_customers_tax_number"},
            new String[] {"customers", "idx_customers_entity_type"},
            new String[] {"projects", "idx_projects_work_type"});

    try (Connection conn = dataSource.getConnection()) {
      for (String[] spec : expectedIndexes) {
        String tableName = spec[0];
        String indexName = spec[1];

        try (PreparedStatement ps =
            conn.prepareStatement(
                "SELECT COUNT(*) FROM pg_indexes "
                    + "WHERE schemaname = ? AND tablename = ? AND indexname = ?")) {
          ps.setString(1, tenantSchema);
          ps.setString(2, tableName);
          ps.setString(3, indexName);
          ResultSet rs = ps.executeQuery();
          assertThat(rs.next()).isTrue();
          assertThat(rs.getInt(1))
              .as("Index %s on %s.%s should exist", indexName, tenantSchema, tableName)
              .isEqualTo(1);
        }
      }
    }
  }
}
