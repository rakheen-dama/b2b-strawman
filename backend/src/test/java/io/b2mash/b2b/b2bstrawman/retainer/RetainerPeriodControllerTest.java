package io.b2mash.b2b.b2bstrawman.retainer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.LocalDate;
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
class RetainerPeriodControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_retainer_period_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String pastRetainerId;
  private String futureRetainerId;
  private String overageRetainerId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Retainer Period Ctrl Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember(ORG_ID, "user_rpc_owner", "rpc_owner@test.com", "RPC Owner", "owner");
    syncMember(ORG_ID, "user_rpc_admin", "rpc_admin@test.com", "RPC Admin", "admin");
    syncMember(ORG_ID, "user_rpc_member", "rpc_member@test.com", "RPC Member", "member");

    // Create separate customers (one active retainer per customer constraint)
    String customerId1 = createCustomer("Past Retainer Customer", "past-ret@test.com");
    String customerId2 = createCustomer("Future Retainer Customer", "future-ret@test.com");
    String customerId3 = createCustomer("Invoice Line Customer", "invoice-line@test.com");

    // Create retainer with past start date (period end is in the past — closeable)
    LocalDate pastStart = LocalDate.now().minusMonths(2);
    pastRetainerId =
        createRetainer(
            customerId1,
            "Past Period Retainer",
            "HOUR_BANK",
            pastStart,
            "\"allocatedHours\": 40.00, \"periodFee\": 20000.00");

    // Create retainer with future start date (period end in the future — not closeable)
    LocalDate futureStart = LocalDate.now().plusMonths(1);
    futureRetainerId =
        createRetainer(
            customerId2,
            "Future Period Retainer",
            "FIXED_FEE",
            futureStart,
            "\"periodFee\": 10000.00");

    // Create retainer for invoice line verification (past start, closeable)
    overageRetainerId =
        createRetainer(
            customerId3,
            "Invoice Line Check Retainer",
            "HOUR_BANK",
            pastStart,
            "\"allocatedHours\": 40.00, \"periodFee\": 5000.00");
  }

  private String createCustomer(String name, String email) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"%s\",\"email\":\"%s\"}".formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private String createRetainer(
      String customerId, String name, String type, LocalDate startDate, String extraFields)
      throws Exception {
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
                      "name": "%s",
                      "type": "%s",
                      "frequency": "MONTHLY",
                      "startDate": "%s",
                      %s,
                      "rolloverPolicy": "FORFEIT"
                    }
                    """
                            .formatted(customerId, name, type, startDate, extraFields)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(1)
  void listPeriods_asMember_returns200WithPaginatedContent() throws Exception {
    mockMvc
        .perform(get("/api/retainers/" + pastRetainerId + "/periods").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].id").exists())
        .andExpect(jsonPath("$.content[0].periodStart").exists())
        .andExpect(jsonPath("$.content[0].status").value("OPEN"))
        .andExpect(jsonPath("$.page.totalElements").exists());
  }

  @Test
  @Order(2)
  void getCurrentPeriod_openPeriodExists_returns200WithPeriodData() throws Exception {
    mockMvc
        .perform(get("/api/retainers/" + pastRetainerId + "/periods/current").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.periodStart").exists())
        .andExpect(jsonPath("$.periodEnd").exists())
        .andExpect(jsonPath("$.allocatedHours").value(40.0));
  }

  @Test
  @Order(3)
  void listPeriods_nonExistentRetainer_returns404() throws Exception {
    mockMvc
        .perform(get("/api/retainers/" + UUID.randomUUID() + "/periods").with(memberJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(4)
  void closePeriod_asMember_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/retainers/" + pastRetainerId + "/periods/current/close").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(5)
  void closePeriod_beforeEndDate_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/retainers/" + futureRetainerId + "/periods/current/close").with(adminJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(6)
  void closePeriod_asAdmin_returns200WithAllThreeTopLevelFields() throws Exception {
    mockMvc
        .perform(
            post("/api/retainers/" + pastRetainerId + "/periods/current/close").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closedPeriod").exists())
        .andExpect(jsonPath("$.closedPeriod.status").value("CLOSED"))
        .andExpect(jsonPath("$.generatedInvoice").exists())
        .andExpect(jsonPath("$.generatedInvoice.id").exists())
        .andExpect(jsonPath("$.generatedInvoice.status").value("DRAFT"))
        .andExpect(jsonPath("$.generatedInvoice.lines").isArray());
  }

  @Test
  @Order(7)
  void getCurrentPeriod_afterClose_returnsNextOpenPeriod() throws Exception {
    // After Order 6 closed the period, a new OPEN period should have been created
    // (retainer is active with no endDate)
    mockMvc
        .perform(get("/api/retainers/" + pastRetainerId + "/periods/current").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"));
  }

  @Test
  @Order(8)
  void closePeriod_withInvoiceLines_shows1BaseLine() throws Exception {
    // Close the overageRetainerId (no time entries => 0 consumed, 1 base line)
    mockMvc
        .perform(
            post("/api/retainers/" + overageRetainerId + "/periods/current/close").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generatedInvoice.lines").isArray())
        .andExpect(jsonPath("$.generatedInvoice.lines[0].description").exists())
        .andExpect(jsonPath("$.generatedInvoice.total").value(5000.0));
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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_rpc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_rpc_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_rpc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
