package io.b2mash.b2b.b2bstrawman.compliance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
class CustomerLifecycleControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_lifecycle_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private int emailCounter = 0;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Lifecycle Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_lc_owner", "lc_owner@test.com", "LC Owner", "owner");
    syncMember(ORG_ID, "user_lc_member", "lc_member@test.com", "LC Member", "member");
  }

  @Test
  void shouldTransitionLifecycleStatus() throws Exception {
    // Customer defaults to PROSPECT; transition to ONBOARDING -> ACTIVE then DORMANT
    String customerId = createCustomer("Transition Test Corp", nextEmail());

    // PROSPECT -> ONBOARDING
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ONBOARDING"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("ONBOARDING"));

    // Complete all auto-instantiated checklist items — this auto-transitions to ACTIVE
    completeChecklistItems(customerId, ownerJwt());

    // Verify customer is now ACTIVE (auto-transitioned by checklist completion)
    mockMvc
        .perform(get("/api/customers/" + customerId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"));

    // ACTIVE -> DORMANT
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "DORMANT", "notes": "Going dormant"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("DORMANT"));
  }

  @Test
  void shouldReturn400ForInvalidStatusString() throws Exception {
    String customerId = createCustomer("Bad Status Corp", nextEmail());

    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "BADVALUE"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn400ForInvalidTransition() throws Exception {
    // Customer defaults to PROSPECT; PROSPECT -> DORMANT is not a valid transition
    String customerId = createCustomer("Invalid Transition Corp", nextEmail());

    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "DORMANT"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnLifecycleHistory() throws Exception {
    String customerId = createCustomer("History Test Corp", nextEmail());

    // Perform transitions to create audit events (PROSPECT -> ONBOARDING -> ACTIVE)
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ONBOARDING"}
                    """))
        .andExpect(status().isOk());

    // Complete checklists — auto-transitions to ACTIVE
    completeChecklistItems(customerId, ownerJwt());

    // Get lifecycle history
    mockMvc
        .perform(get("/api/customers/" + customerId + "/lifecycle").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void shouldReturnDormancyCheckResult() throws Exception {
    mockMvc
        .perform(post("/api/customers/dormancy-check").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.thresholdDays").isNumber())
        .andExpect(jsonPath("$.candidates").isArray());
  }

  @Test
  void shouldReturn403ForMemberOnDormancyCheck() throws Exception {
    mockMvc
        .perform(post("/api/customers/dormancy-check").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldReturn403ForMemberOnTransition() throws Exception {
    String customerId = createCustomer("Member Block Corp", nextEmail());

    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ONBOARDING"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldReturn200ForMemberOnLifecycleHistory() throws Exception {
    String customerId = createCustomer("Member View Corp", nextEmail());

    mockMvc
        .perform(get("/api/customers/" + customerId + "/lifecycle").with(memberJwt()))
        .andExpect(status().isOk());
  }

  @Test
  void shouldReturnLifecycleSummaryForOwner() throws Exception {
    createCustomer("Summary Test Corp", nextEmail());

    mockMvc
        .perform(get("/api/customers/lifecycle-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isMap());
  }

  @Test
  void shouldReturn403ForMemberOnLifecycleSummary() throws Exception {
    mockMvc
        .perform(get("/api/customers/lifecycle-summary").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- Helpers ---

  private String nextEmail() {
    return "lc_ctrl_" + (++emailCounter) + "@test.com";
  }

  private String createCustomer(String name, String email) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "email": "%s"}
                        """
                            .formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @SuppressWarnings("unchecked")
  private void completeChecklistItems(String customerId, JwtRequestPostProcessor jwt)
      throws Exception {
    var result =
        mockMvc
            .perform(get("/api/customers/" + customerId + "/checklists").with(jwt))
            .andExpect(status().isOk())
            .andReturn();
    String json = result.getResponse().getContentAsString();
    List<Map<String, Object>> instances = JsonPath.read(json, "$[*]");
    for (Map<String, Object> instance : instances) {
      List<Map<String, Object>> items = (List<Map<String, Object>>) instance.get("items");
      if (items == null) continue;
      for (Map<String, Object> item : items) {
        String itemId = (String) item.get("id");
        boolean requiresDocument = Boolean.TRUE.equals(item.get("requiresDocument"));
        if (requiresDocument) {
          mockMvc
              .perform(
                  put("/api/checklist-items/" + itemId + "/skip")
                      .with(jwt)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"reason\": \"skipped for test\"}"))
              .andExpect(status().isOk());
        } else {
          mockMvc
              .perform(
                  put("/api/checklist-items/" + itemId + "/complete")
                      .with(jwt)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"notes\": \"auto-completed for test\"}"))
              .andExpect(status().isOk());
        }
      }
    }
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_lc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_lc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
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
