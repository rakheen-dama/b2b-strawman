package io.b2mash.b2b.b2bstrawman.billingrun;

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
class BillingRunModuleGuardTest {

  private static final String ORG_ID = "org_billing_run_guard";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Billing Run Guard Test", null);
    TestMemberHelper.syncMember(mockMvc, ORG_ID, "user_br_owner", "br@test.com", "Owner", "owner");
  }

  @BeforeEach
  void disableAllHorizontalModules() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_br_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_br_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabledModules\": [\"" + moduleId + "\"]}"))
        .andExpect(status().isOk());
  }

  @Test
  void getBillingRuns_returns403_whenBulkBillingDisabled() throws Exception {
    mockMvc
        .perform(get("/api/billing-runs").with(TestJwtFactory.ownerJwt(ORG_ID, "user_br_owner")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "This feature is not enabled for your organization. "
                        + "An admin can enable it in Settings → Features."))
        .andExpect(jsonPath("$.moduleId").value("bulk_billing"));
  }

  @Test
  void getBillingRuns_returns200_whenBulkBillingEnabled() throws Exception {
    enableModule("bulk_billing");
    mockMvc
        .perform(get("/api/billing-runs").with(TestJwtFactory.ownerJwt(ORG_ID, "user_br_owner")))
        .andExpect(status().isOk());
  }
}
