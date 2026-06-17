package io.b2mash.b2b.b2bstrawman.mcp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpEnablementControllerTest {

  private static final String ORG_ID = "org_mcp_enablement_ctrl_test";
  private static final String ORG_B_ID = "org_mcp_enablement_ctrl_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void provisionTenants() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP Enablement Controller Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_mcpctrl_owner", "mcpctrl_owner@test.com", "Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_mcpctrl_member", "mcpctrl_member@test.com", "Member", "member");

    provisioningService.provisionTenant(ORG_B_ID, "MCP Enablement Controller Test Org B", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_B_ID, "user_mcpctrl_b_owner", "mcpctrl_b_owner@test.com", "Owner B", "owner");
  }

  @Test
  @Order(1)
  void getStatus_freshTenant_isDisabled() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations/mcp/status")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_mcpctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effectivelyEnabled").value(false))
        .andExpect(jsonPath("$.integrationEnabled").value(false))
        .andExpect(jsonPath("$.serverUrl").isNotEmpty())
        .andExpect(jsonPath("$.consent.granted").value(false));
  }

  @Test
  @Order(2)
  void enableBlankConsentVersion_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/mcp/enable")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_mcpctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"consentVersion": ""}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(3)
  void memberLackingCapability_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations/mcp/status")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mcpctrl_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(10)
  void enable_thenStatusGrantedAndEnabled() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/mcp/enable")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_mcpctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"consentVersion": "popia-egress-v1"}
                    """))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/integrations/mcp/status")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_mcpctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effectivelyEnabled").value(true))
        .andExpect(jsonPath("$.integrationEnabled").value(true))
        .andExpect(jsonPath("$.consent.granted").value(true))
        .andExpect(jsonPath("$.consent.version").value("popia-egress-v1"))
        .andExpect(jsonPath("$.consent.consentedBy").isNotEmpty());
  }

  @Test
  @Order(11)
  void enabledTenant_doesNotLeakIntoOtherTenant() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations/mcp/status")
                .with(TestJwtFactory.ownerJwt(ORG_B_ID, "user_mcpctrl_b_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effectivelyEnabled").value(false))
        .andExpect(jsonPath("$.integrationEnabled").value(false))
        .andExpect(jsonPath("$.consent.granted").value(false));
  }

  @Test
  @Order(20)
  void revoke_thenStatusDisabledAndRevoked() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/mcp/revoke")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_mcpctrl_owner")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/integrations/mcp/status")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_mcpctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effectivelyEnabled").value(false))
        .andExpect(jsonPath("$.consent.action").value("REVOKED"));
  }
}
