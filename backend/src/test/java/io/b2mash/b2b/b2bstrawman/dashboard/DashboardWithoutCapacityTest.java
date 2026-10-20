package io.b2mash.b2b.b2bstrawman.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.orgrole.Capability;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.report.ReportController;
import io.b2mash.b2b.b2bstrawman.settings.dto.SettingsResponse;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Replacement-path test for Epic 599 (Phase 86 slice 5): resource planning (Phase 38 capacity,
 * allocations, leave, allocation-driven profitability projections) is gone. Every surviving
 * dashboard endpoint still answers 200 for an owner on a fresh legal-za tenant with no capacity
 * keys, the removed capacity routes are plain 404s (unmapped, not 403), and the seeded {@code
 * RESOURCE_PLANNING} capability rows are gone (V140) so owner role loads still succeed.
 *
 * <p>Per decision D3 (2026-10-16) the <em>time-based</em> utilization surfaces are kept: the
 * personal dashboard's {@code utilization} summary and {@code GET /api/reports/utilization}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardWithoutCapacityTest {
  private static final String ORG_ID = "org_dash_without_capacity";
  private static final String OWNER = "user_dwc_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private DataSource dataSource;

  private final LocalDate today = LocalDate.now();
  private final LocalDate thirtyDaysAgo = today.minusDays(30);
  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Dashboard Without Capacity Org", "legal-za")
            .schemaName();
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "dwc_owner@test.com", "DWC Owner", "owner");
  }

  @Test
  void kpisStillServedAndCarryNoCapacityKey() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/kpis")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeMatterCount").isNumber())
        .andExpect(jsonPath("$.capacity").doesNotExist())
        .andExpect(jsonPath("$.teamUtilization").doesNotExist());
  }

  @Test
  void personalDashboardStillServedWithTimeBasedUtilizationOnly() throws Exception {
    // $.utilization is the time-based UtilizationSummary (kept, D3) — not capacity.
    mockMvc
        .perform(
            get("/api/dashboard/personal")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.utilization").exists())
        .andExpect(jsonPath("$.capacity").doesNotExist())
        .andExpect(jsonPath("$.allocations").doesNotExist());
  }

  @Test
  void projectHealthListStillServed() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/matter-health").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void teamWorkloadStillServed() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/team-workload")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void activityStillServed() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/activity").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void capacityRoutesAreGone() throws Exception {
    // Were CapacityController, ResourceAllocationController, LeaveBlockController,
    // UtilizationController, MemberCapacityController (capacity package) and
    // OrgSettingsController @PatchMapping("/capacity"). Unmapped → 404, not 403.
    for (String path :
        List.of(
            "/api/capacity/team",
            "/api/resource-allocations",
            "/api/leave",
            "/api/utilization/team",
            "/api/members/" + UUID.randomUUID() + "/capacity")) {
      mockMvc
          .perform(get(path).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
          .andExpect(status().isNotFound());
    }
    mockMvc
        .perform(
            patch("/api/settings/capacity")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"defaultWeeklyCapacityHours\":40}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void timeBasedUtilizationReportIsKept() throws Exception {
    // Decision D3: GET /api/reports/utilization reads time entries only and feeds the kept
    // profitability page.
    mockMvc
        .perform(
            get("/api/reports/utilization")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members").isArray());
  }

  @Test
  void profitabilityCarriesNoProjections() throws Exception {
    // includeProjections was removed; an unknown query param is simply ignored.
    mockMvc
        .perform(
            get("/api/reports/profitability")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .param("includeProjections", "true")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matters").isArray())
        .andExpect(jsonPath("$.projections").doesNotExist());
  }

  @Test
  void reportCatalogueListsNoUtilizationReport() throws Exception {
    mockMvc
        .perform(get("/api/report-definitions").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[*].reports[*].slug", not(hasItem("utilization"))));
  }

  @Test
  void resourcePlanningCapabilityAndModuleAreGone() throws Exception {
    assertThat(Capability.ALL_NAMES).doesNotContain("RESOURCE_PLANNING");

    // V140 must have deleted the V67-seeded rows: without it OrgRole.capabilities cannot map
    // 'RESOURCE_PLANNING' to an enum constant and every owner/admin role load throws.
    Integer seededRows =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT count(*) FROM "
                    + tenantSchema
                    + ".org_role_capabilities WHERE capability = 'RESOURCE_PLANNING'",
                Integer.class);
    assertThat(seededRows).isZero();

    mockMvc
        .perform(get("/api/org-roles").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.slug == 'owner')]").isNotEmpty())
        .andExpect(jsonPath("$[*].capabilities[*]", not(hasItem("RESOURCE_PLANNING"))));

    mockMvc
        .perform(get("/api/me/capabilities").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities", hasItem("TEAM_OVERSIGHT")))
        .andExpect(jsonPath("$.capabilities", not(hasItem("RESOURCE_PLANNING"))));

    mockMvc
        .perform(get("/api/modules").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem("bulk_billing")))
        .andExpect(jsonPath("$[*].id", not(hasItem("resource_planning"))));
  }

  @Test
  void capacitySchemaObjectsAreDropped() {
    var jdbc = new JdbcTemplate(dataSource);
    for (String table : List.of("resource_allocations", "leave_blocks", "member_capacities")) {
      String regclass =
          jdbc.queryForObject(
              "SELECT to_regclass(?)::text", String.class, tenantSchema + "." + table);
      assertThat(regclass).as(table).isNull();
    }
    Integer capacityColumn =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = ?"
                + " AND table_name = 'org_settings'"
                + " AND column_name = 'default_weekly_capacity_hours'",
            Integer.class,
            tenantSchema);
    assertThat(capacityColumn).isZero();
  }

  @Test
  void capacityPackageIsAbsentFromTheClasspath() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.b2mash.b2b.b2bstrawman");
    // Non-vacuous: the importer must actually see production classes (the report package).
    assertThat(classes.stream().map(c -> c.getPackageName()))
        .contains("io.b2mash.b2b.b2bstrawman.report")
        .noneMatch(
            p ->
                p.equals("io.b2mash.b2b.b2bstrawman.capacity")
                    || p.startsWith("io.b2mash.b2b.b2bstrawman.capacity."));
    assertThatThrownBy(() -> Class.forName("io.b2mash.b2b.b2bstrawman.capacity.ResourceAllocation"))
        .isInstanceOf(ClassNotFoundException.class);
    assertThatThrownBy(() -> Class.forName("io.b2mash.b2b.b2bstrawman.settings.CapacitySettings"))
        .isInstanceOf(ClassNotFoundException.class);

    // DTO contract: no profitability response carries projections; settings carry no capacity.
    assertThat(componentNames(ReportController.OrgProfitabilityResponse.class))
        .contains("matters")
        .doesNotContain("projections");
    assertThat(componentNames(ReportController.MatterProfitabilityResponse.class))
        .contains("currencies")
        .doesNotContain("projections");
    assertThat(componentNames(ReportController.ClientProfitabilityResponse.class))
        .contains("currencies")
        .doesNotContain("projections");
    assertThat(componentNames(SettingsResponse.class))
        .contains("defaultExpenseMarkupPercent")
        .doesNotContain("defaultWeeklyCapacityHours");
  }

  private static List<String> componentNames(Class<? extends Record> recordType) {
    return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
  }
}
