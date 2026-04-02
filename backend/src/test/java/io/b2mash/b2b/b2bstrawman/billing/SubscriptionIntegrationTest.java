package io.b2mash.b2b.b2bstrawman.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Map;
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
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_A = "org_billing_test_a";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private OrganizationRepository organizationRepository;

  @BeforeAll
  void provisionTenants() throws Exception {
    provisioningService.provisionTenant(ORG_A, "Billing Test Org A", null);

    // Sync a member so member count is > 0
    syncMember(ORG_A, "user_billing_owner", "billing_owner@test.com", "Owner", "owner");
  }

  // --- 1. Subscription created on provisioning ---

  @Test
  void shouldCreateSubscriptionOnProvisioning() {
    // Use a fresh org to verify initial provisioning state (other tests mutate ORG_A)
    String freshOrg = "org_billing_provision_check";
    provisioningService.provisionTenant(freshOrg, "Provision Check Org", null);

    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();
    var subscription = subscriptionRepository.findByOrganizationId(org.getId());

    assertThat(subscription).isPresent();
    assertThat(subscription.get().getSubscriptionStatus())
        .isEqualTo(Subscription.SubscriptionStatus.TRIALING);
  }

  // --- 2. GET /api/billing/subscription returns correct status + limits ---

  @Test
  void shouldReturnTrialingStatusAndLimits() throws Exception {
    mockMvc
        .perform(get("/api/billing/subscription").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TRIALING"))
        .andExpect(jsonPath("$.limits.maxMembers").value(10))
        .andExpect(jsonPath("$.limits.currentMembers").value(1));
  }

  // --- 3. POST /internal/billing/set-plan is accepted (no-op after Tier removal) ---

  @Test
  void shouldAcceptSetPlanAsNoOp() throws Exception {
    mockMvc
        .perform(
            post("/internal/billing/set-plan")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "planSlug": "pro"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isOk());
  }

  // --- 4. Set-plan for unknown org still returns 200 (endpoint is now a no-op) ---

  @Test
  void shouldReturn200ForSetPlanUnknownOrg() throws Exception {
    mockMvc
        .perform(
            post("/internal/billing/set-plan")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "org_nonexistent",
                      "planSlug": "pro"
                    }
                    """))
        .andExpect(status().isOk());
  }

  // --- 5. API key required for internal endpoint ---

  @Test
  void shouldReject401WithoutApiKey() throws Exception {
    mockMvc
        .perform(
            post("/internal/billing/set-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "planSlug": "pro"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isUnauthorized());
  }

  // --- Helpers ---

  private void syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
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
        .andExpect(status().isCreated());
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_billing_owner").claim("o", Map.of("id", ORG_A, "rol", "member")));
  }
}
