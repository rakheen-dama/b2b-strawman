package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V31MigrationTest {

  private static final String ORG_PRO_ID = "org_v31_migration_pro";
  private static final String ORG_STARTER_ID = "org_v31_migration_starter";
  private static final String SHARED_SCHEMA = "tenant_shared";

  private static final List<String> EXPECTED_TABLES =
      List.of(
          "checklist_templates",
          "checklist_template_items",
          "checklist_instances",
          "checklist_instance_items",
          "data_subject_requests",
          "retention_policies");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String proTenantSchema;

  @BeforeAll
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);

    // Provision a PRO tenant (dedicated schema)
    provisioningService.provisionTenant(ORG_PRO_ID, "V31 Migration Pro Org");
    planSyncService.syncPlan(ORG_PRO_ID, "pro-plan");

    proTenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_PRO_ID).orElseThrow().getSchemaName();

    // Provision a STARTER tenant (shared schema) — triggers migration on tenant_shared
    provisioningService.provisionTenant(ORG_STARTER_ID, "V31 Migration Starter Org");
  }

  @Test
  void migrationCreates6TablesInDedicatedSchema() {
    for (String table : EXPECTED_TABLES) {
      var count =
          jdbc.queryForObject(
              """
              SELECT COUNT(*) FROM information_schema.tables
              WHERE table_schema = ? AND table_name = ?
              """,
              Integer.class,
              proTenantSchema,
              table);
      assertThat(count)
          .as("Table %s should exist in dedicated schema %s", table, proTenantSchema)
          .isEqualTo(1);
    }
  }

  @Test
  void migrationCreates6TablesInSharedSchema() {
    for (String table : EXPECTED_TABLES) {
      var count =
          jdbc.queryForObject(
              """
              SELECT COUNT(*) FROM information_schema.tables
              WHERE table_schema = ? AND table_name = ?
              """,
              Integer.class,
              SHARED_SCHEMA,
              table);
      assertThat(count)
          .as("Table %s should exist in shared schema %s", table, SHARED_SCHEMA)
          .isEqualTo(1);
    }
  }

  @Test
  void customerTableHasLifecycleColumns() {
    var columns =
        jdbc.queryForList(
            """
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = ? AND table_name = 'customers'
            AND column_name IN ('lifecycle_status', 'lifecycle_status_changed_at',
                                'lifecycle_status_changed_by', 'offboarded_at')
            ORDER BY column_name
            """,
            String.class,
            proTenantSchema);

    assertThat(columns)
        .containsExactlyInAnyOrder(
            "lifecycle_status",
            "lifecycle_status_changed_at",
            "lifecycle_status_changed_by",
            "offboarded_at");
  }

  @Test
  void orgSettingsTableHasComplianceColumns() {
    var columns =
        jdbc.queryForList(
            """
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = ? AND table_name = 'org_settings'
            AND column_name IN ('dormancy_threshold_days', 'data_request_deadline_days',
                                'compliance_pack_status')
            ORDER BY column_name
            """,
            String.class,
            proTenantSchema);

    assertThat(columns)
        .containsExactlyInAnyOrder(
            "compliance_pack_status", "data_request_deadline_days", "dormancy_threshold_days");
  }

  @Test
  void rlsPoliciesExistInDedicatedSchema() {
    // Check that RLS policies exist for the 6 new tables
    List<String> expectedPolicies =
        List.of(
            "checklist_templates_tenant_isolation",
            "checklist_template_items_tenant_isolation",
            "checklist_instances_tenant_isolation",
            "checklist_instance_items_tenant_isolation",
            "data_subject_requests_tenant_isolation",
            "retention_policies_tenant_isolation");

    for (String policyName : expectedPolicies) {
      // RLS policies are stored per-schema in pg_policies
      // Set search path to the tenant schema to find them
      jdbc.execute("SET search_path TO " + proTenantSchema + ", public");
      var count =
          jdbc.queryForObject(
              """
              SELECT COUNT(*) FROM pg_policies WHERE policyname = ?
              """,
              Integer.class,
              policyName);
      assertThat(count).as("RLS policy %s should exist", policyName).isGreaterThanOrEqualTo(1);
    }
    jdbc.execute("SET search_path TO public");
  }
}
