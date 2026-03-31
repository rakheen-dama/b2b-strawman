package io.b2mash.b2b.b2bstrawman.verticals.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegalMigrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_v83_migration_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DataSource dataSource;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "V83 Migration Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember(ORG_ID, "user_v83_owner", "v83_owner@test.com", "V83 Owner", "owner");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void v83CreatesAllSevenTables() throws Exception {
    List<String> expectedTables =
        List.of(
            "court_dates",
            "prescription_trackers",
            "adverse_parties",
            "adverse_party_links",
            "conflict_checks",
            "tariff_schedules",
            "tariff_items");

    try (Connection conn = dataSource.getConnection()) {
      for (String tableName : expectedTables) {
        try (PreparedStatement ps =
            conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = ? AND table_name = ?")) {
          ps.setString(1, tenantSchema);
          ps.setString(2, tableName);
          ResultSet rs = ps.executeQuery();
          assertThat(rs.next()).as("ResultSet should have a row for " + tableName).isTrue();
          assertThat(rs.getInt(1))
              .as("Table %s should exist in schema %s", tableName, tenantSchema)
              .isEqualTo(1);
        }
      }
    }
  }

  @Test
  void v83AddsInvoiceLineColumns() throws Exception {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = 'invoice_lines' "
                    + "AND column_name IN ('tariff_item_id', 'line_source')")) {
      ps.setString(1, tenantSchema);
      List<String> columns = new ArrayList<>();
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        columns.add(rs.getString("column_name"));
      }
      assertThat(columns).containsExactlyInAnyOrder("tariff_item_id", "line_source");
    }
  }

  @Test
  void v16EnablesPgTrgmExtension() throws Exception {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement("SELECT COUNT(*) FROM pg_extension WHERE extname = ?")) {
      ps.setString(1, "pg_trgm");
      ResultSet rs = ps.executeQuery();
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1)).as("pg_trgm extension should be installed").isEqualTo(1);
    }
  }

  private void syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "%s",
                      "email": "%s",
                      "name": "%s",
                      "avatarUrl": null,
                      "orgRole": "%s"
                    }
                    """
                        .formatted(orgId, clerkUserId, email, name, orgRole)))
        .andExpect(status().isCreated());
  }
}
