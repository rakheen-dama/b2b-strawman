package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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
 * Verifies the final schema state after V48 + V49 migrations. V48 adds content_json/body_json JSONB
 * columns alongside the original TEXT columns. V49 drops the old TEXT columns and renames the JSONB
 * columns to content/body.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V48MigrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_v48_migration_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DataSource dataSource;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "V48 Migration Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember(ORG_ID, "user_v48_owner", "v48_owner@test.com", "V48 Owner", "owner");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void contentColumnIsJsonbAfterSwap() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      // After V49 swap: content column should be JSONB (renamed from content_json)
      ResultSet rs =
          stmt.executeQuery(
              "SELECT column_name, data_type FROM information_schema.columns "
                  + "WHERE table_schema = '"
                  + tenantSchema
                  + "' AND table_name = 'document_templates' "
                  + "AND column_name = 'content'");
      assertThat(rs.next()).as("content column should exist").isTrue();
      assertThat(rs.getString("data_type"))
          .as("content column should be jsonb after V49 swap")
          .isEqualTo("jsonb");

      // legacy_content column should exist as TEXT
      rs =
          stmt.executeQuery(
              "SELECT column_name, data_type FROM information_schema.columns "
                  + "WHERE table_schema = '"
                  + tenantSchema
                  + "' AND table_name = 'document_templates' "
                  + "AND column_name = 'legacy_content'");
      assertThat(rs.next()).as("legacy_content column should exist").isTrue();
      assertThat(rs.getString("data_type")).isEqualTo("text");

      // body column on clauses should be JSONB (renamed from body_json)
      rs =
          stmt.executeQuery(
              "SELECT column_name, data_type FROM information_schema.columns "
                  + "WHERE table_schema = '"
                  + tenantSchema
                  + "' AND table_name = 'clauses' "
                  + "AND column_name = 'body'");
      assertThat(rs.next()).as("body column should exist").isTrue();
      assertThat(rs.getString("data_type"))
          .as("body column should be jsonb after V49 swap")
          .isEqualTo("jsonb");

      // legacy_body column should exist as TEXT
      rs =
          stmt.executeQuery(
              "SELECT column_name, data_type FROM information_schema.columns "
                  + "WHERE table_schema = '"
                  + tenantSchema
                  + "' AND table_name = 'clauses' "
                  + "AND column_name = 'legacy_body'");
      assertThat(rs.next()).as("legacy_body column should exist").isTrue();
      assertThat(rs.getString("data_type")).isEqualTo("text");
    }
  }

  @Test
  void oldIntermediateColumnsDropped() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      // After V49: content_json should no longer exist (renamed to content)
      ResultSet rs =
          stmt.executeQuery(
              "SELECT column_name FROM information_schema.columns "
                  + "WHERE table_schema = '"
                  + tenantSchema
                  + "' AND table_name = 'document_templates' "
                  + "AND column_name = 'content_json'");
      assertThat(rs.next()).as("content_json should not exist after V49 swap").isFalse();

      // After V49: body_json should no longer exist (renamed to body)
      rs =
          stmt.executeQuery(
              "SELECT column_name FROM information_schema.columns "
                  + "WHERE table_schema = '"
                  + tenantSchema
                  + "' AND table_name = 'clauses' "
                  + "AND column_name = 'body_json'");
      assertThat(rs.next()).as("body_json should not exist after V49 swap").isFalse();
    }
  }

  @Test
  void platformTemplatesHaveJsonbContent() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("SET search_path TO " + tenantSchema);

      // PLATFORM templates seeded after V48+V49 should have content (JSONB) populated
      ResultSet rs =
          stmt.executeQuery(
              "SELECT content, legacy_content FROM document_templates"
                  + " WHERE source = 'PLATFORM'");

      boolean foundAny = false;
      while (rs.next()) {
        foundAny = true;
        assertThat(rs.getObject("content"))
            .as("PLATFORM template content (JSONB) should be populated by seeder")
            .isNotNull();
      }
      assertThat(foundAny).as("Should have at least one PLATFORM template").isTrue();
    }
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }

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
