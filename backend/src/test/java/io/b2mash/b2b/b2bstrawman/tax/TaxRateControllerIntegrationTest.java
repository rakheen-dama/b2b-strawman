package io.b2mash.b2b.b2bstrawman.tax;

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
class TaxRateControllerIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_tax_ctrl_test";
  private static final String ORG_ID_B = "org_tax_ctrl_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String createdRateId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Tax Rate Ctrl Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Sync members
    syncMember(ORG_ID, "user_tax_owner", "tax_owner@test.com", "Tax Owner", "owner");
    syncMember(ORG_ID, "user_tax_admin", "tax_admin@test.com", "Tax Admin", "admin");
    syncMember(ORG_ID, "user_tax_member", "tax_member@test.com", "Tax Member", "member");

    // Provision Tenant B for isolation test
    provisioningService.provisionTenant(ORG_ID_B, "Tax Rate Ctrl Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");
    syncMember(ORG_ID_B, "user_tax_owner_b", "tax_owner_b@test.com", "Tax Owner B", "owner");
  }

  @Test
  @Order(1)
  void get_listsTaxRates_returns200() throws Exception {
    mockMvc
        .perform(get("/api/tax-rates").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(3)); // seed data: Standard, Zero-rated, Exempt
  }

  @Test
  @Order(2)
  void post_createsTaxRate_returns201_asAdmin() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/tax-rates")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Test VAT",
                          "rate": 14.00,
                          "isDefault": false,
                          "isExempt": false,
                          "sortOrder": 10
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Test VAT"))
            .andExpect(jsonPath("$.rate").value(14.00))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn();

    createdRateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(3)
  void post_withMemberRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/tax-rates")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Member Rate",
                      "rate": 5.00,
                      "isDefault": false,
                      "isExempt": false,
                      "sortOrder": 20
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(4)
  void post_withInvalidData_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/tax-rates")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "",
                      "rate": -5.00,
                      "isDefault": false,
                      "isExempt": false,
                      "sortOrder": 0
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(5)
  void put_updatesTaxRate_returns200() throws Exception {
    mockMvc
        .perform(
            put("/api/tax-rates/" + createdRateId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Updated VAT",
                      "rate": 15.00,
                      "isDefault": false,
                      "isExempt": false,
                      "active": true,
                      "sortOrder": 11
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated VAT"))
        .andExpect(jsonPath("$.rate").value(15.00));
  }

  @Test
  @Order(6)
  void delete_deactivatesTaxRate_returns204() throws Exception {
    // Create a rate to deactivate
    var result =
        mockMvc
            .perform(
                post("/api/tax-rates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Rate To Deactivate",
                          "rate": 7.00,
                          "isDefault": false,
                          "isExempt": false,
                          "sortOrder": 30
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var rateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();

    mockMvc
        .perform(delete("/api/tax-rates/" + rateId).with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  @Order(7)
  void tenantIsolation_taxRateInTenantANotVisibleInTenantB() throws Exception {
    // Tenant B should have its own seed rates, not Tenant A's custom rates
    mockMvc
        .perform(get("/api/tax-rates").with(ownerJwtTenantB()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        // Tenant B has its own 3 seed rates; the "Updated VAT" from Tenant A is not visible
        .andExpect(jsonPath("$[?(@.name == 'Updated VAT')]").isEmpty());
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
        .jwt(j -> j.subject("user_tax_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_tax_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_tax_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor ownerJwtTenantB() {
    return jwt()
        .jwt(j -> j.subject("user_tax_owner_b").claim("o", Map.of("id", ORG_ID_B, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
