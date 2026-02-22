package io.b2mash.b2b.b2bstrawman.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_integration_ctrl_test";
  private static final String TEST_API_KEY = "sk_live_abc123def456xyz789";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;

  @BeforeAll
  void provisionTenant() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Integration Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(ORG_ID, "user_intctrl_owner", "intctrl_owner@test.com", "Owner", "owner");
    memberIdAdmin =
        syncMember(ORG_ID, "user_intctrl_admin", "intctrl_admin@test.com", "Admin", "admin");
    memberIdMember =
        syncMember(ORG_ID, "user_intctrl_member", "intctrl_member@test.com", "Member", "member");
  }

  @Test
  @Order(1)
  void listIntegrations_returnsAllFourDomains() throws Exception {
    mockMvc
        .perform(get("/api/integrations").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(4)))
        .andExpect(jsonPath("$[0].domain").value("ACCOUNTING"))
        .andExpect(jsonPath("$[0].providerSlug").value(nullValue()))
        .andExpect(jsonPath("$[0].enabled").value(false))
        .andExpect(jsonPath("$[1].domain").value("AI"))
        .andExpect(jsonPath("$[2].domain").value("DOCUMENT_SIGNING"))
        .andExpect(jsonPath("$[3].domain").value("PAYMENT"));
  }

  @Test
  @Order(2)
  void listProviders_returnsNoopForAllDomains() throws Exception {
    mockMvc
        .perform(get("/api/integrations/providers").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ACCOUNTING").isArray())
        .andExpect(jsonPath("$.AI").isArray())
        .andExpect(jsonPath("$.DOCUMENT_SIGNING").isArray())
        .andExpect(jsonPath("$.PAYMENT").isArray());
  }

  @Test
  @Order(3)
  void memberCannotAccessIntegrationsApi() throws Exception {
    mockMvc.perform(get("/api/integrations").with(memberJwt())).andExpect(status().isForbidden());
  }

  @Test
  @Order(4)
  void adminCanAccessIntegrationsApi() throws Exception {
    mockMvc
        .perform(get("/api/integrations").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(4)));
  }

  @Test
  @Order(10)
  void putUpsertsIntegrationConfig() throws Exception {
    mockMvc
        .perform(
            put("/api/integrations/ACCOUNTING")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"providerSlug": "noop", "configJson": "{\\"region\\": \\"za\\"}"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.domain").value("ACCOUNTING"))
        .andExpect(jsonPath("$.providerSlug").value("noop"))
        .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  @Order(20)
  void postSetKeyReturns204() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/ACCOUNTING/set-key")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"apiKey": "%s"}
                    """
                        .formatted(TEST_API_KEY)))
        .andExpect(status().isNoContent());
  }

  @Test
  @Order(21)
  void apiKeyNeverReturnedInListResponse() throws Exception {
    var result =
        mockMvc
            .perform(get("/api/integrations").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    // keySuffix should be last 6 chars of TEST_API_KEY
    String expectedSuffix = TEST_API_KEY.substring(TEST_API_KEY.length() - 6);
    assert body.contains(expectedSuffix) : "Response should contain keySuffix";
    assert !body.contains(TEST_API_KEY) : "Response must NOT contain the full API key";
  }

  @Test
  @Order(25)
  void setKeyBlankReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/ACCOUNTING/set-key")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"apiKey": ""}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(30)
  void postTestConnectionReturnsSuccess() throws Exception {
    mockMvc
        .perform(post("/api/integrations/ACCOUNTING/test").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.providerName").value("noop"));
  }

  @Test
  @Order(40)
  void patchToggleEnablesIntegration() throws Exception {
    mockMvc
        .perform(
            patch("/api/integrations/ACCOUNTING/toggle")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabled": true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.domain").value("ACCOUNTING"))
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  @Order(50)
  void deleteKeyRemovesKeySuffix() throws Exception {
    // Delete the key
    mockMvc
        .perform(delete("/api/integrations/ACCOUNTING/key").with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify keySuffix is now null
    mockMvc
        .perform(get("/api/integrations").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].domain").value("ACCOUNTING"))
        .andExpect(jsonPath("$[0].keySuffix").value(nullValue()));
  }

  @Test
  @Order(60)
  void memberCannotToggleIntegration() throws Exception {
    mockMvc
        .perform(
            patch("/api/integrations/ACCOUNTING/toggle")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabled": true}
                    """))
        .andExpect(status().isForbidden());
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

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_intctrl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_intctrl_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_intctrl_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
