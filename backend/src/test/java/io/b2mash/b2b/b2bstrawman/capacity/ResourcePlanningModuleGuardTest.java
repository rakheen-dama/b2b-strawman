package io.b2mash.b2b.b2bstrawman.capacity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
class ResourcePlanningModuleGuardTest {

  private static final String ORG_ID = "org_resource_planning_guard";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Resource Planning Guard Test", null);
    TestMemberHelper.syncMember(mockMvc, ORG_ID, "user_rp_owner", "rp@test.com", "Owner", "owner");
  }

  @BeforeEach
  void disableAllHorizontalModules() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rp_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabledModules": []}
                    """))
        .andExpect(status().isOk());
  }

  private void enableModule(String moduleId) throws Exception {
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rp_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabledModules\": [\"" + moduleId + "\"]}"))
        .andExpect(status().isOk());
  }

  @Test
  void getResourceAllocations_returns403_whenResourcePlanningDisabled() throws Exception {
    mockMvc
        .perform(
            get("/api/resource-allocations").with(TestJwtFactory.ownerJwt(ORG_ID, "user_rp_owner")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("resource_planning"));
  }

  @Test
  void getResourceAllocations_returns200_whenResourcePlanningEnabled() throws Exception {
    enableModule("resource_planning");
    mockMvc
        .perform(
            get("/api/resource-allocations").with(TestJwtFactory.ownerJwt(ORG_ID, "user_rp_owner")))
        .andExpect(status().isOk());
  }

  @Test
  void getTeamUtilization_returns403_whenResourcePlanningDisabled() throws Exception {
    mockMvc
        .perform(
            get("/api/utilization/team")
                .param("weekStart", "2026-04-06") // Monday
                .param("weekEnd", "2026-04-13")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rp_owner")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("resource_planning"));
  }

  @Test
  void getBillingRuns_returns403_whenBulkBillingDisabled() throws Exception {
    mockMvc
        .perform(get("/api/billing-runs").with(TestJwtFactory.ownerJwt(ORG_ID, "user_rp_owner")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("bulk_billing"));
  }

  @Test
  void getBillingRuns_returns200_whenBulkBillingEnabled() throws Exception {
    enableModule("bulk_billing");
    mockMvc
        .perform(get("/api/billing-runs").with(TestJwtFactory.ownerJwt(ORG_ID, "user_rp_owner")))
        .andExpect(status().isOk());
  }
}
