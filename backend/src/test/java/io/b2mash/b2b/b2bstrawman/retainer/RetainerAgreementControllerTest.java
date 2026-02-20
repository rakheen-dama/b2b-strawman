package io.b2mash.b2b.b2bstrawman.retainer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.UUID;
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
class RetainerAgreementControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_retainer_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;
  private String customerId;
  private String retainerId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Retainer Ctrl Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(ORG_ID, "user_rct_owner", "rct_owner@test.com", "RCT Owner", "owner");
    memberIdAdmin =
        syncMember(ORG_ID, "user_rct_admin", "rct_admin@test.com", "RCT Admin", "admin");
    memberIdMember =
        syncMember(ORG_ID, "user_rct_member", "rct_member@test.com", "RCT Member", "member");

    var customerResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Retainer Test Customer", "email": "rct_customer@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    customerId =
        JsonPath.read(customerResult.getResponse().getContentAsString(), "$.id").toString();

    // Transition customer from PROSPECT to ACTIVE (lifecycle guard blocks PROSPECT)
    transitionCustomerToActive(customerId);
  }

  private void transitionCustomerToActive(String custId) throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + custId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    // Completing all checklist items auto-transitions ONBOARDING -> ACTIVE
    completeChecklistItems(custId, ownerJwt());
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

  // --- CRUD Tests ---

  @Test
  @Order(1)
  void createHourBankRetainer_returns201WithFirstPeriod() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/retainers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "name": "Monthly Hour Bank Retainer",
                          "type": "HOUR_BANK",
                          "frequency": "MONTHLY",
                          "startDate": "2026-03-01",
                          "allocatedHours": 40.00,
                          "periodFee": 20000.00,
                          "rolloverPolicy": "FORFEIT"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Monthly Hour Bank Retainer"))
            .andExpect(jsonPath("$.type").value("HOUR_BANK"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.customerName").value("Retainer Test Customer"))
            .andExpect(jsonPath("$.allocatedHours").value(40.0))
            .andExpect(jsonPath("$.currentPeriod").exists())
            .andExpect(jsonPath("$.currentPeriod.periodStart").value("2026-03-01"))
            .andExpect(jsonPath("$.currentPeriod.periodEnd").value("2026-04-01"))
            .andExpect(jsonPath("$.currentPeriod.status").value("OPEN"))
            .andExpect(jsonPath("$.currentPeriod.allocatedHours").value(40.0))
            .andExpect(jsonPath("$.currentPeriod.consumedHours").value(0.0))
            .andReturn();

    retainerId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(2)
  void listRetainers_asOwner_returns200() throws Exception {
    mockMvc
        .perform(get("/api/retainers").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));
  }

  @Test
  @Order(3)
  void getRetainer_asMember_returns200WithPeriodData() throws Exception {
    assertNotNull(retainerId, "retainerId must be set by Order 1");
    mockMvc
        .perform(get("/api/retainers/" + retainerId).with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(retainerId))
        .andExpect(jsonPath("$.currentPeriod").exists())
        .andExpect(jsonPath("$.recentPeriods").isArray());
  }

  @Test
  @Order(4)
  void getRetainer_nonExistent_returns404() throws Exception {
    mockMvc
        .perform(get("/api/retainers/" + UUID.randomUUID()).with(memberJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(5)
  void createRetainer_missingRequiredFields_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/retainers")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(6)
  void updateRetainer_asOwner_returns200WithUpdatedFields() throws Exception {
    assertNotNull(retainerId, "retainerId must be set by Order 1");
    mockMvc
        .perform(
            put("/api/retainers/" + retainerId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Updated Retainer Name",
                      "allocatedHours": 50.00,
                      "periodFee": 25000.00,
                      "rolloverPolicy": "FORFEIT"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Retainer Name"))
        .andExpect(jsonPath("$.allocatedHours").value(50.0))
        .andExpect(jsonPath("$.periodFee").value(25000.0));
  }

  // --- Lifecycle Tests ---

  @Test
  @Order(7)
  void pauseRetainer_activeRetainer_returns200WithPausedStatus() throws Exception {
    assertNotNull(retainerId, "retainerId must be set by Order 1");
    mockMvc
        .perform(post("/api/retainers/" + retainerId + "/pause").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAUSED"));
  }

  @Test
  @Order(8)
  void pauseRetainer_alreadyPaused_returns400() throws Exception {
    assertNotNull(retainerId, "retainerId must be set by Order 1");
    mockMvc
        .perform(post("/api/retainers/" + retainerId + "/pause").with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(9)
  void resumeRetainer_pausedRetainer_returns200WithActiveStatus() throws Exception {
    assertNotNull(retainerId, "retainerId must be set by Order 1");
    mockMvc
        .perform(post("/api/retainers/" + retainerId + "/resume").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  @Order(10)
  void terminateRetainer_returns200WithTerminatedStatus() throws Exception {
    assertNotNull(retainerId, "retainerId must be set by Order 1");
    mockMvc
        .perform(post("/api/retainers/" + retainerId + "/terminate").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TERMINATED"));
  }

  // --- Permission Tests ---

  @Test
  @Order(11)
  void createRetainer_asMember_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/retainers")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "name": "Member Retainer",
                      "type": "HOUR_BANK",
                      "frequency": "MONTHLY",
                      "startDate": "2026-04-01",
                      "allocatedHours": 20.00,
                      "periodFee": 10000.00,
                      "rolloverPolicy": "FORFEIT"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(12)
  void listRetainers_asMember_returns403() throws Exception {
    mockMvc.perform(get("/api/retainers").with(memberJwt())).andExpect(status().isForbidden());
  }

  @Test
  @Order(13)
  void listRetainers_asAdmin_returns200() throws Exception {
    mockMvc
        .perform(get("/api/retainers").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  @Order(14)
  void updateRetainer_asMember_returns403() throws Exception {
    var dummyId = UUID.randomUUID();
    mockMvc
        .perform(
            put("/api/retainers/" + dummyId)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Should Not Work",
                      "allocatedHours": 10.00,
                      "periodFee": 5000.00,
                      "rolloverPolicy": "FORFEIT"
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(15)
  void pauseRetainer_asMember_returns403() throws Exception {
    var dummyId = UUID.randomUUID();
    mockMvc
        .perform(post("/api/retainers/" + dummyId + "/pause").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(16)
  void resumeRetainer_asMember_returns403() throws Exception {
    var dummyId = UUID.randomUUID();
    mockMvc
        .perform(post("/api/retainers/" + dummyId + "/resume").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(17)
  void terminateRetainer_asMember_returns403() throws Exception {
    var dummyId = UUID.randomUUID();
    mockMvc
        .perform(post("/api/retainers/" + dummyId + "/terminate").with(memberJwt()))
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
        .jwt(j -> j.subject("user_rct_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_rct_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_rct_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
