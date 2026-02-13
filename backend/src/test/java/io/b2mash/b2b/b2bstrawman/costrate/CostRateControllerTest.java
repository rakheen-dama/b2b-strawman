package io.b2mash.b2b.b2bstrawman.costrate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class CostRateControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cost_ctrl_test";
  private static final String ORG_ID_B = "org_cost_ctrl_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;

  // Tenant B for isolation tests
  private String memberIdOwnerB;

  // Stored rate ID for subsequent tests
  private String createdCostRateId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    // Provision tenant A
    provisioningService.provisionTenant(ORG_ID, "Cost Ctrl Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(ORG_ID, "user_crc_owner", "crc_owner@test.com", "CRC Owner", "owner");
    memberIdAdmin =
        syncMember(ORG_ID, "user_crc_admin", "crc_admin@test.com", "CRC Admin", "admin");
    memberIdMember =
        syncMember(ORG_ID, "user_crc_member", "crc_member@test.com", "CRC Member", "member");

    // Provision tenant B for isolation tests
    provisioningService.provisionTenant(ORG_ID_B, "Cost Ctrl Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");
    memberIdOwnerB =
        syncMember(ORG_ID_B, "user_crc_owner_b", "crc_owner_b@test.com", "CRC Owner B", "owner");
  }

  // --- CRUD Tests ---

  @Test
  @Order(1)
  void postCreatesCostRateAndReturns201() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/cost-rates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "memberId": "%s",
                          "currency": "USD",
                          "hourlyCost": 75.00,
                          "effectiveFrom": "2025-01-01"
                        }
                        """
                            .formatted(memberIdMember)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.memberId").value(memberIdMember))
            .andExpect(jsonPath("$.memberName").value("CRC Member"))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.hourlyCost").value(75.00))
            .andExpect(jsonPath("$.effectiveFrom").value("2025-01-01"))
            .andExpect(jsonPath("$.effectiveTo").isEmpty())
            .andReturn();

    createdCostRateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(2)
  void getListsCostRates() throws Exception {
    mockMvc
        .perform(get("/api/cost-rates").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].memberName").value("CRC Member"));
  }

  @Test
  @Order(3)
  void getListsFiltersByMemberId() throws Exception {
    // Create a cost rate for the admin member
    mockMvc
        .perform(
            post("/api/cost-rates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "currency": "EUR",
                      "hourlyCost": 90.00,
                      "effectiveFrom": "2025-01-01"
                    }
                    """
                        .formatted(memberIdAdmin)))
        .andExpect(status().isCreated());

    // Filter by the original member — should only return 1
    mockMvc
        .perform(get("/api/cost-rates").param("memberId", memberIdMember).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].memberId").value(memberIdMember));
  }

  @Test
  @Order(4)
  void putUpdatesCostRate() throws Exception {
    mockMvc
        .perform(
            put("/api/cost-rates/" + createdCostRateId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currency": "GBP",
                      "hourlyCost": 85.00,
                      "effectiveFrom": "2025-01-01",
                      "effectiveTo": "2025-12-31"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.hourlyCost").value(85.00))
        .andExpect(jsonPath("$.effectiveTo").value("2025-12-31"));
  }

  @Test
  @Order(5)
  void deleteRemovesCostRate() throws Exception {
    // Create a rate to delete
    var result =
        mockMvc
            .perform(
                post("/api/cost-rates")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "memberId": "%s",
                          "currency": "CAD",
                          "hourlyCost": 60.00,
                          "effectiveFrom": "2026-01-01"
                        }
                        """
                            .formatted(memberIdOwner)))
            .andExpect(status().isCreated())
            .andReturn();

    var rateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();

    mockMvc
        .perform(delete("/api/cost-rates/" + rateId).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  // --- Permission Tests ---

  @Test
  @Order(6)
  void memberCannotCreateCostRate() throws Exception {
    mockMvc
        .perform(
            post("/api/cost-rates")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "currency": "USD",
                      "hourlyCost": 50.00,
                      "effectiveFrom": "2025-01-01"
                    }
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(7)
  void memberCannotListCostRates() throws Exception {
    mockMvc.perform(get("/api/cost-rates").with(memberJwt())).andExpect(status().isForbidden());
  }

  @Test
  @Order(8)
  void memberCannotUpdateCostRate() throws Exception {
    mockMvc
        .perform(
            put("/api/cost-rates/" + createdCostRateId)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currency": "CAD",
                      "hourlyCost": 99.00,
                      "effectiveFrom": "2025-01-01"
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(9)
  void memberCannotDeleteCostRate() throws Exception {
    mockMvc
        .perform(delete("/api/cost-rates/" + createdCostRateId).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- Overlap Test ---

  @Test
  @Order(10)
  void postOverlappingCostRateReturns409() throws Exception {
    mockMvc
        .perform(
            post("/api/cost-rates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "currency": "USD",
                      "hourlyCost": 80.00,
                      "effectiveFrom": "2025-06-01"
                    }
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isConflict());
  }

  // --- Tenant Isolation Tests ---

  @Test
  @Order(11)
  void costRateInTenantAIsInvisibleInTenantB() throws Exception {
    mockMvc
        .perform(get("/api/cost-rates").with(ownerJwtTenantB()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
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
        .jwt(j -> j.subject("user_crc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_crc_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_crc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor ownerJwtTenantB() {
    return jwt()
        .jwt(j -> j.subject("user_crc_owner_b").claim("o", Map.of("id", ORG_ID_B, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
