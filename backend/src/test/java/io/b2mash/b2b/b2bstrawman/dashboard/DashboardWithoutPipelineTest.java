package io.b2mash.b2b.b2bstrawman.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Replacement-path test for Epic 596 (Phase 86 slice 2): the CRM pipeline is gone. Every surviving
 * dashboard endpoint still answers 200 for an owner on a fresh legal-za tenant, no dashboard
 * payload carries a {@code pipeline} key, and the removed {@code GET
 * /api/dashboard/pipeline-summary} route is a plain 404 (no handler), not a 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardWithoutPipelineTest {
  private static final String ORG_ID = "org_dash_without_pipeline";
  private static final String OWNER = "user_dwp_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private final LocalDate today = LocalDate.now();
  private final LocalDate thirtyDaysAgo = today.minusDays(30);

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Dashboard Without Pipeline Org", "legal-za");
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "dwp_owner@test.com", "DWP Owner", "owner");
  }

  @Test
  void kpisStillServedAndCarryNoPipelineKey() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/kpis")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeProjectCount").isNumber())
        .andExpect(jsonPath("$.pipeline").doesNotExist());
  }

  @Test
  void projectHealthListStillServed() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/project-health").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
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
  void personalDashboardStillServedAndCarryNoPipelineKey() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/personal")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.utilization").exists())
        .andExpect(jsonPath("$.pipeline").doesNotExist());
  }

  @Test
  void pipelineSummaryRouteIsGone() throws Exception {
    // Was @GetMapping("/api/dashboard/pipeline-summary") @RequiresCapability("VIEW_DEALS")
    // (DashboardController, Epic 578A). With no handler the dispatcher answers 404 — asserting
    // 404 (not 403) proves the route is unmapped rather than merely capability-gated.
    mockMvc
        .perform(
            get("/api/dashboard/pipeline-summary").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isNotFound());
  }
}
