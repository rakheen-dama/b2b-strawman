package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the Starter → Pro tier upgrade flow. Provisions a Starter org with test
 * data, upgrades it to Pro, and verifies data is migrated to a dedicated schema with shared data
 * cleaned up.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TierUpgradeIntegrationTest {

  private static final String ORG_ID = "org_upgrade_test";
  private static final String API_KEY = "test-api-key";
  private static final String SHARED_SCHEMA = "tenant_shared";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TenantUpgradeService upgradeService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private OrgSchemaMappingRepository mappingRepository;

  @Autowired
  @Qualifier("migrationDataSource")
  private DataSource migrationDataSource;

  private JdbcTemplate jdbc;
  private String dedicatedSchema;

  @BeforeAll
  void setUp() throws Exception {
    jdbc = new JdbcTemplate(migrationDataSource);

    // 1. Provision as Starter (maps to tenant_shared)
    provisioningService.provisionTenant(ORG_ID, "Upgrade Test Org");

    // 2. Sync 2 members
    syncMember(ORG_ID, "user_upgrade_admin", "admin@upgrade.test", "Upgrade Admin", "admin");
    syncMember(ORG_ID, "user_upgrade_member", "member@upgrade.test", "Upgrade Member", "member");

    // 3. Create projects via API (auto-creates project_member with lead role)
    createProject("Upgrade Project Alpha");
    createProject("Upgrade Project Beta");

    // 4. Insert a document directly via JDBC (avoids S3 dependency)
    var projectId =
        jdbc.queryForObject(
            "SELECT id FROM tenant_shared.projects WHERE tenant_id = ? LIMIT 1",
            UUID.class,
            ORG_ID);
    var memberId =
        jdbc.queryForObject(
            "SELECT id FROM tenant_shared.members WHERE tenant_id = ? LIMIT 1", UUID.class, ORG_ID);
    jdbc.update(
        """
        INSERT INTO tenant_shared.documents
          (id, project_id, file_name, content_type, size, s3_key, status, uploaded_by,
           uploaded_at, created_at, tenant_id)
        VALUES (gen_random_uuid(), ?, 'test.pdf', 'application/pdf', 1024, 'uploads/test.pdf',
                'CONFIRMED', ?, now(), now(), ?)
        """,
        projectId,
        memberId,
        ORG_ID);

    // Compute expected dedicated schema name
    dedicatedSchema = SchemaNameGenerator.generateSchemaName(ORG_ID);
  }

  // --- Task 27.5: Full upgrade round-trip ---

  @Test
  @Order(1)
  void verifyStarterDataBeforeUpgrade() throws SQLException {
    assertThat(countRows(SHARED_SCHEMA, "members", ORG_ID)).isEqualTo(2);
    assertThat(countRows(SHARED_SCHEMA, "projects", ORG_ID)).isEqualTo(2);
    assertThat(countRows(SHARED_SCHEMA, "documents", ORG_ID)).isEqualTo(1);
    assertThat(countRows(SHARED_SCHEMA, "project_members", ORG_ID)).isEqualTo(2); // 2 auto-leads

    var mapping = mappingRepository.findByClerkOrgId(ORG_ID).orElseThrow();
    assertThat(mapping.getSchemaName()).isEqualTo(SHARED_SCHEMA);
  }

  @Test
  @Order(2)
  void upgradeShouldMigrateDataToDedicatedSchema() throws Exception {
    // Update tier to PRO (simulates plan-sync webhook)
    var org = organizationRepository.findByClerkOrgId(ORG_ID).orElseThrow();
    org.updatePlan(Tier.PRO, "pro");
    organizationRepository.save(org);

    // Trigger upgrade
    upgradeService.upgrade(ORG_ID);

    // Verify dedicated schema exists
    assertThat(schemaExists(dedicatedSchema)).isTrue();

    // Verify all tables exist in dedicated schema
    assertThat(tableExists(dedicatedSchema, "members")).isTrue();
    assertThat(tableExists(dedicatedSchema, "projects")).isTrue();
    assertThat(tableExists(dedicatedSchema, "documents")).isTrue();
    assertThat(tableExists(dedicatedSchema, "project_members")).isTrue();
  }

  @Test
  @Order(3)
  void upgradedDataShouldExistInDedicatedSchema() {
    // Dedicated schema rows have NULL tenant_id
    assertThat(countAllRows(dedicatedSchema, "members")).isEqualTo(2);
    assertThat(countAllRows(dedicatedSchema, "projects")).isEqualTo(2);
    assertThat(countAllRows(dedicatedSchema, "documents")).isEqualTo(1);
    assertThat(countAllRows(dedicatedSchema, "project_members")).isEqualTo(2);

    // Verify tenant_id is NULL in dedicated schema
    var tenantIds =
        jdbc.queryForList(
            "SELECT DISTINCT tenant_id FROM \"" + dedicatedSchema + "\".members", String.class);
    assertThat(tenantIds).containsExactly((String) null);
  }

  @Test
  @Order(4)
  void sharedSchemaDataShouldBeDeleted() {
    assertThat(countRows(SHARED_SCHEMA, "members", ORG_ID)).isZero();
    assertThat(countRows(SHARED_SCHEMA, "projects", ORG_ID)).isZero();
    assertThat(countRows(SHARED_SCHEMA, "documents", ORG_ID)).isZero();
    assertThat(countRows(SHARED_SCHEMA, "project_members", ORG_ID)).isZero();
  }

  @Test
  @Order(5)
  void orgSchemaMappingShouldPointToDedicatedSchema() {
    var mapping = mappingRepository.findByClerkOrgId(ORG_ID).orElseThrow();
    assertThat(mapping.getSchemaName()).isEqualTo(dedicatedSchema);
  }

  @Test
  @Order(6)
  void organizationShouldBeProAndCompleted() {
    var org = organizationRepository.findByClerkOrgId(ORG_ID).orElseThrow();
    assertThat(org.getTier()).isEqualTo(Tier.PRO);
    assertThat(org.getProvisioningStatus()).isEqualTo(Organization.ProvisioningStatus.COMPLETED);
  }

  @Test
  @Order(7)
  void subsequentRequestsShouldResolveToDedicatedSchema() throws Exception {
    // Create a project via API after upgrade — it should land in the dedicated schema
    mockMvc
        .perform(
            post("/api/projects")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Post-Upgrade Project", "description": "Created after upgrade"}
                    """))
        .andExpect(status().isCreated());

    // Verify the new project is in the dedicated schema (now 3 total projects)
    assertThat(countAllRows(dedicatedSchema, "projects")).isEqualTo(3);

    // And NOT in the shared schema
    assertThat(countRows(SHARED_SCHEMA, "projects", ORG_ID)).isZero();
  }

  // --- Task 27.6: Idempotency ---

  @Test
  @Order(8)
  void upgradeOfAlreadyUpgradedOrgShouldBeIdempotent() {
    // The org is already upgraded. Running upgrade again should succeed (all steps are idempotent).
    upgradeService.upgrade(ORG_ID);

    // Verify state is still correct
    var org = organizationRepository.findByClerkOrgId(ORG_ID).orElseThrow();
    assertThat(org.getTier()).isEqualTo(Tier.PRO);
    assertThat(org.getProvisioningStatus()).isEqualTo(Organization.ProvisioningStatus.COMPLETED);
    assertThat(countAllRows(dedicatedSchema, "members")).isGreaterThanOrEqualTo(2);
  }

  // --- Task 27.6: Rollback ---

  @Test
  @Order(9)
  void upgradeOfNonProOrgShouldFail() {
    // Provision a separate Starter org that has NOT been updated to PRO
    provisioningService.provisionTenant("org_still_starter", "Still Starter Org");

    assertThatThrownBy(() -> upgradeService.upgrade("org_still_starter"))
        .isInstanceOf(io.b2mash.b2b.b2bstrawman.exception.InvalidStateException.class);

    // Verify the org is still on Starter with data intact
    var org = organizationRepository.findByClerkOrgId("org_still_starter").orElseThrow();
    assertThat(org.getTier()).isEqualTo(Tier.STARTER);
  }

  @Test
  @Order(10)
  void planSyncEndpointShouldTriggerUpgrade() throws Exception {
    // Provision a fresh Starter org with a member
    String freshOrgId = "org_plansync_upgrade";
    provisioningService.provisionTenant(freshOrgId, "PlanSync Upgrade Org");
    syncMember(freshOrgId, "user_plansync_admin", "ps@upgrade.test", "PS Admin", "admin");

    // Call plan-sync endpoint with "pro" slug
    mockMvc
        .perform(
            post("/internal/orgs/plan-sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId": "%s", "planSlug": "pro"}
                    """
                        .formatted(freshOrgId)))
        .andExpect(status().isOk());

    // Verify the org was upgraded
    var org = organizationRepository.findByClerkOrgId(freshOrgId).orElseThrow();
    assertThat(org.getTier()).isEqualTo(Tier.PRO);
    assertThat(org.getProvisioningStatus()).isEqualTo(Organization.ProvisioningStatus.COMPLETED);

    // Verify mapping points to dedicated schema
    String expectedSchema = SchemaNameGenerator.generateSchemaName(freshOrgId);
    var mapping = mappingRepository.findByClerkOrgId(freshOrgId).orElseThrow();
    assertThat(mapping.getSchemaName()).isEqualTo(expectedSchema);
    assertThat(schemaExists(expectedSchema)).isTrue();

    // Verify member was migrated
    assertThat(countAllRows(expectedSchema, "members")).isEqualTo(1);
    assertThat(countRows(SHARED_SCHEMA, "members", freshOrgId)).isZero();
  }

  // --- Helpers ---

  private int countRows(String schema, String table, String tenantId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM \"" + schema + "\".\"" + table + "\" WHERE tenant_id = ?",
        Integer.class,
        tenantId);
  }

  private int countAllRows(String schema, String table) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM \"" + schema + "\".\"" + table + "\"", Integer.class);
  }

  private boolean schemaExists(String schemaName) throws SQLException {
    return jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)",
        Boolean.class,
        schemaName);
  }

  private boolean tableExists(String schemaName, String tableName) throws SQLException {
    return jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM information_schema.tables "
            + "WHERE table_schema = ? AND table_name = ?)",
        Boolean.class,
        schemaName,
        tableName);
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

  private void createProject(String name) throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "%s", "description": "Test project for upgrade"}
                    """
                        .formatted(name)))
        .andExpect(status().isCreated());
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_upgrade_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }
}
