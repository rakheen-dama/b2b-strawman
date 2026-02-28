package io.b2mash.b2b.b2bstrawman.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

/**
 * Verifies that V50 migration adds the task recurrence columns correctly. All three new columns
 * (recurrence_rule, recurrence_end_date, parent_task_id) must be nullable so existing tasks are
 * unaffected (backward compatibility guarantee).
 *
 * <p>NOTE: V50 also covers expenses, invoice_lines, and org_settings changes — those are verified
 * by their respective epic tests (218B, 221A, 226A).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V50TaskRecurrenceMigrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_v50_migration_test";

  private final MockMvc mockMvc;
  private final TenantProvisioningService provisioningService;
  private final PlanSyncService planSyncService;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final DataSource dataSource;

  private String tenantSchema;

  @Autowired
  V50TaskRecurrenceMigrationTest(
      MockMvc mockMvc,
      TenantProvisioningService provisioningService,
      PlanSyncService planSyncService,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      DataSource dataSource) {
    this.mockMvc = mockMvc;
    this.provisioningService = provisioningService;
    this.planSyncService = planSyncService;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.dataSource = dataSource;
  }

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "V50 Migration Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Return value intentionally discarded — we only need the member to exist
    syncMember(ORG_ID, "user_v50_owner", "v50_owner@test.com", "V50 Owner", "owner");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void recurrenceRuleColumnExistsAndIsNullable() throws Exception {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT column_name, data_type, is_nullable, character_maximum_length "
                    + "FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = 'tasks' "
                    + "AND column_name = 'recurrence_rule'")) {
      ps.setString(1, tenantSchema);
      ResultSet rs = ps.executeQuery();
      assertThat(rs.next()).as("recurrence_rule column should exist").isTrue();
      assertThat(rs.getString("data_type"))
          .as("recurrence_rule should be character varying")
          .isEqualTo("character varying");
      assertThat(rs.getInt("character_maximum_length"))
          .as("recurrence_rule should have max length 100")
          .isEqualTo(100);
      assertThat(rs.getString("is_nullable"))
          .as("recurrence_rule must be nullable for backward compatibility")
          .isEqualTo("YES");
    }
  }

  @Test
  void recurrenceEndDateColumnExistsAndIsNullable() throws Exception {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT column_name, data_type, is_nullable "
                    + "FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = 'tasks' "
                    + "AND column_name = 'recurrence_end_date'")) {
      ps.setString(1, tenantSchema);
      ResultSet rs = ps.executeQuery();
      assertThat(rs.next()).as("recurrence_end_date column should exist").isTrue();
      assertThat(rs.getString("data_type"))
          .as("recurrence_end_date should be date type")
          .isEqualTo("date");
      assertThat(rs.getString("is_nullable"))
          .as("recurrence_end_date must be nullable for backward compatibility")
          .isEqualTo("YES");
    }
  }

  @Test
  void parentTaskIdColumnExistsAndIsNullable() throws Exception {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT column_name, data_type, is_nullable "
                    + "FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = 'tasks' "
                    + "AND column_name = 'parent_task_id'")) {
      ps.setString(1, tenantSchema);
      ResultSet rs = ps.executeQuery();
      assertThat(rs.next()).as("parent_task_id column should exist").isTrue();
      assertThat(rs.getString("data_type"))
          .as("parent_task_id should be uuid type")
          .isEqualTo("uuid");
      assertThat(rs.getString("is_nullable"))
          .as("parent_task_id must be nullable for backward compatibility")
          .isEqualTo("YES");
    }
  }

  @Test
  void parentTaskIdIndexExists() throws Exception {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT indexname, indexdef FROM pg_indexes "
                    + "WHERE schemaname = ? AND tablename = 'tasks' "
                    + "AND indexname = 'idx_tasks_parent_task_id'")) {
      ps.setString(1, tenantSchema);
      ResultSet rs = ps.executeQuery();
      assertThat(rs.next()).as("idx_tasks_parent_task_id partial index should exist").isTrue();
      assertThat(rs.getString("indexdef"))
          .as("index should be a partial index filtering on non-null parent_task_id")
          .containsIgnoringCase("WHERE (parent_task_id IS NOT NULL)");
    }
  }

  // --- Helpers ---

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
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
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
