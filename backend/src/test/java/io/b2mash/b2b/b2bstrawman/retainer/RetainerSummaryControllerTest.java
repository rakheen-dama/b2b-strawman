package io.b2mash.b2b.b2bstrawman.retainer;

import static org.hamcrest.Matchers.nullValue;
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
class RetainerSummaryControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_retainer_summary_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String customerHourBank;
  private String customerFixedFee;
  private String customerNoRetainer;
  private String customerTerminated;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Retainer Summary Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember(ORG_ID, "user_rst_owner", "rst_owner@test.com", "RST Owner", "owner");
    syncMember(ORG_ID, "user_rst_member", "rst_member@test.com", "RST Member", "member");

    // Create customers
    customerHourBank = createCustomer("HourBank Corp", "hourbank@test.com");
    customerFixedFee = createCustomer("FixedFee Corp", "fixedfee@test.com");
    customerNoRetainer = createCustomer("NoRetainer Corp", "noretainer@test.com");
    customerTerminated = createCustomer("Terminated Corp", "terminated@test.com");

    // Create HOUR_BANK retainer for customerHourBank
    mockMvc
        .perform(
            post("/api/retainers")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "name": "Monthly Hour Bank",
                      "type": "HOUR_BANK",
                      "frequency": "MONTHLY",
                      "startDate": "2026-03-01",
                      "allocatedHours": 50.00,
                      "periodFee": 25000.00,
                      "rolloverPolicy": "FORFEIT"
                    }
                    """
                        .formatted(customerHourBank)))
        .andExpect(status().isCreated());

    // Create FIXED_FEE retainer for customerFixedFee
    mockMvc
        .perform(
            post("/api/retainers")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "name": "Monthly Fixed Fee",
                      "type": "FIXED_FEE",
                      "frequency": "MONTHLY",
                      "startDate": "2026-03-01",
                      "periodFee": 10000.00,
                      "rolloverPolicy": "FORFEIT"
                    }
                    """
                        .formatted(customerFixedFee)))
        .andExpect(status().isCreated());

    // Create and then terminate a retainer for customerTerminated
    var terminateResult =
        mockMvc
            .perform(
                post("/api/retainers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "name": "Terminated Retainer",
                          "type": "HOUR_BANK",
                          "frequency": "MONTHLY",
                          "startDate": "2026-03-01",
                          "allocatedHours": 20.00,
                          "periodFee": 10000.00,
                          "rolloverPolicy": "FORFEIT"
                        }
                        """
                            .formatted(customerTerminated)))
            .andExpect(status().isCreated())
            .andReturn();

    String terminatedRetainerId =
        JsonPath.read(terminateResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(post("/api/retainers/" + terminatedRetainerId + "/terminate").with(ownerJwt()))
        .andExpect(status().isOk());
  }

  @Test
  @Order(1)
  void getSummary_activeHourBankRetainer_returns200WithAllFields() throws Exception {
    mockMvc
        .perform(get("/api/customers/" + customerHourBank + "/retainer-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasActiveRetainer").value(true))
        .andExpect(jsonPath("$.agreementId").exists())
        .andExpect(jsonPath("$.agreementName").value("Monthly Hour Bank"))
        .andExpect(jsonPath("$.type").value("HOUR_BANK"))
        .andExpect(jsonPath("$.allocatedHours").value(50.0))
        .andExpect(jsonPath("$.consumedHours").value(0.0))
        .andExpect(jsonPath("$.remainingHours").value(50.0))
        .andExpect(jsonPath("$.percentConsumed").value(0.0))
        .andExpect(jsonPath("$.isOverage").value(false));
  }

  @Test
  @Order(2)
  void getSummary_activeFixedFeeRetainer_returns200WithTypeSpecificFields() throws Exception {
    mockMvc
        .perform(get("/api/customers/" + customerFixedFee + "/retainer-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasActiveRetainer").value(true))
        .andExpect(jsonPath("$.agreementId").exists())
        .andExpect(jsonPath("$.agreementName").value("Monthly Fixed Fee"))
        .andExpect(jsonPath("$.type").value("FIXED_FEE"))
        .andExpect(jsonPath("$.allocatedHours").value(nullValue()))
        .andExpect(jsonPath("$.consumedHours").value(0.0))
        .andExpect(jsonPath("$.remainingHours").value(nullValue()))
        .andExpect(jsonPath("$.percentConsumed").value(nullValue()))
        .andExpect(jsonPath("$.isOverage").value(false));
  }

  @Test
  @Order(3)
  void getSummary_customerWithNoRetainer_returns200WithHasActiveRetainerFalse() throws Exception {
    mockMvc
        .perform(get("/api/customers/" + customerNoRetainer + "/retainer-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasActiveRetainer").value(false));
  }

  @Test
  @Order(4)
  void getSummary_customerWithTerminatedRetainerOnly_returns200WithHasActiveRetainerFalse()
      throws Exception {
    mockMvc
        .perform(get("/api/customers/" + customerTerminated + "/retainer-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasActiveRetainer").value(false));
  }

  @Test
  @Order(5)
  void getSummary_asMember_returns200() throws Exception {
    mockMvc
        .perform(get("/api/customers/" + customerHourBank + "/retainer-summary").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasActiveRetainer").value(true));
  }

  @Test
  @Order(6)
  void getSummary_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/customers/" + customerHourBank + "/retainer-summary"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(7)
  void getSummary_nonExistentCustomer_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/00000000-0000-0000-0000-000000000099/retainer-summary")
                .with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- Helpers ---

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
    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
    transitionCustomerToActive(id);
    return id;
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
        .jwt(j -> j.subject("user_rst_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_rst_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
