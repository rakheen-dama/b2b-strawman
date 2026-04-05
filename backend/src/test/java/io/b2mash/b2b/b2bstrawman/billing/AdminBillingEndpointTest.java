package io.b2mash.b2b.b2bstrawman.billing;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminBillingEndpointTest {

  private static final String BASE_PATH = "/api/platform-admin/billing";
  private static final String ORG_ID = "org_admin_billing_test";
  private static final String ORG_ID_2 = "org_admin_billing_test_2";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private SubscriptionRepository subscriptionRepository;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Admin Billing Test Org", null);
    provisioningService.provisionTenant(ORG_ID_2, "Admin Billing Test Org 2", null);
  }

  // --- GET /api/platform-admin/billing/tenants ---

  @Test
  void listTenants_platformAdmin_returns200() throws Exception {
    mockMvc
        .perform(get(BASE_PATH + "/tenants").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
  }

  @Test
  void listTenants_filterByStatus_returnsFiltered() throws Exception {
    mockMvc
        .perform(get(BASE_PATH + "/tenants").param("status", "TRIALING").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
  }

  @Test
  void listTenants_filterByBillingMethod_returnsFiltered() throws Exception {
    mockMvc
        .perform(get(BASE_PATH + "/tenants").param("billingMethod", "MANUAL").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
  }

  @Test
  void listTenants_searchByName_returnsMatching() throws Exception {
    mockMvc
        .perform(
            get(BASE_PATH + "/tenants").param("search", "Admin Billing Test Org").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
  }

  @Test
  void listTenants_nonAdmin_returns403() throws Exception {
    mockMvc
        .perform(get(BASE_PATH + "/tenants").with(regularJwt()))
        .andExpect(status().isForbidden());
  }

  // --- GET /api/platform-admin/billing/tenants/{orgId} ---

  @Test
  void getTenant_platformAdmin_returns200() throws Exception {
    // Use ORG_ID_2 which is not mutated by override tests
    var org = organizationRepository.findByClerkOrgId(ORG_ID_2).orElseThrow();

    mockMvc
        .perform(get(BASE_PATH + "/tenants/" + org.getId()).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organizationId").value(org.getId().toString()))
        .andExpect(jsonPath("$.organizationName").value("Admin Billing Test Org 2"))
        .andExpect(jsonPath("$.subscriptionStatus").value("TRIALING"))
        .andExpect(jsonPath("$.billingMethod").value("MANUAL"));
  }

  @Test
  void getTenant_nonAdmin_returns403() throws Exception {
    var org = organizationRepository.findByClerkOrgId(ORG_ID).orElseThrow();

    mockMvc
        .perform(get(BASE_PATH + "/tenants/" + org.getId()).with(regularJwt()))
        .andExpect(status().isForbidden());
  }

  // --- PUT /api/platform-admin/billing/tenants/{orgId}/status ---

  @Test
  void overrideBilling_changeStatus_returns200() throws Exception {
    var org = organizationRepository.findByClerkOrgId(ORG_ID).orElseThrow();

    mockMvc
        .perform(
            put(BASE_PATH + "/tenants/" + org.getId() + "/status")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "status": "ACTIVE",
                      "adminNote": "Activated for demo"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subscriptionStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.adminNote").value("Activated for demo"));
  }

  @Test
  void overrideBilling_changeBillingMethod_returns200() throws Exception {
    var org = organizationRepository.findByClerkOrgId(ORG_ID).orElseThrow();

    mockMvc
        .perform(
            put(BASE_PATH + "/tenants/" + org.getId() + "/status")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "billingMethod": "PILOT",
                      "adminNote": "Converted to pilot"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billingMethod").value("PILOT"))
        .andExpect(jsonPath("$.isDemoTenant").value(true));
  }

  @Test
  void overrideBilling_missingAdminNote_returns400() throws Exception {
    var org = organizationRepository.findByClerkOrgId(ORG_ID).orElseThrow();

    mockMvc
        .perform(
            put(BASE_PATH + "/tenants/" + org.getId() + "/status")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "status": "ACTIVE"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void overrideBilling_nonAdmin_returns403() throws Exception {
    var org = organizationRepository.findByClerkOrgId(ORG_ID).orElseThrow();

    mockMvc
        .perform(
            put(BASE_PATH + "/tenants/" + org.getId() + "/status")
                .with(regularJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "status": "ACTIVE",
                      "adminNote": "Should fail"
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  // --- POST /api/platform-admin/billing/tenants/{orgId}/extend-trial ---

  @Test
  void extendTrial_platformAdmin_returns200() throws Exception {
    // Use a fresh org to ensure TRIALING status
    String freshOrg = "org_admin_extend_trial_test";
    provisioningService.provisionTenant(freshOrg, "Extend Trial Test Org", null);
    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();

    mockMvc
        .perform(
            post(BASE_PATH + "/tenants/" + org.getId() + "/extend-trial")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "days": 14
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subscriptionStatus").value("TRIALING"));
  }

  @Test
  void extendTrial_nonAdmin_returns403() throws Exception {
    var org = organizationRepository.findByClerkOrgId(ORG_ID).orElseThrow();

    mockMvc
        .perform(
            post(BASE_PATH + "/tenants/" + org.getId() + "/extend-trial")
                .with(regularJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "days": 14
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  // --- Helpers ---

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_platform_admin").claim("groups", List.of("platform-admins")));
  }

  private JwtRequestPostProcessor regularJwt() {
    return jwt().jwt(j -> j.subject("user_regular"));
  }
}
