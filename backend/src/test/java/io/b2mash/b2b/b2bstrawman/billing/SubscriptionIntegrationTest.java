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
import io.b2mash.b2b.b2bstrawman.provisioning.Tier;
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
class SubscriptionIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_A = "org_billing_test_a";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private OrganizationRepository organizationRepository;

  @BeforeAll
  void provisionTenants() throws Exception {
    provisioningService.provisionTenant(ORG_A, "Billing Test Org A");

    // Sync a member so member count is > 0
    syncMember(ORG_A, "user_billing_owner", "billing_owner@test.com", "Owner", "owner");
  }

  // --- 1. Subscription created on provisioning ---

  @Test
  void shouldCreateSubscriptionOnProvisioning() {
    // Use a fresh org to verify initial provisioning state (other tests mutate ORG_A)
    String freshOrg = "org_billing_provision_check";
    provisioningService.provisionTenant(freshOrg, "Provision Check Org");

    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();
    var subscription = subscriptionRepository.findByOrganizationId(org.getId());

    assertThat(subscription).isPresent();
    assertThat(subscription.get().getPlanSlug()).isEqualTo("starter");
    assertThat(subscription.get().getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
  }

  // --- 2. GET /api/billing/subscription returns correct plan + limits ---

  @Test
  void shouldReturnStarterPlanAndLimits() throws Exception {
    mockMvc
        .perform(get("/api/billing/subscription").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planSlug").value("starter"))
        .andExpect(jsonPath("$.tier").value("STARTER"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.limits.maxMembers").value(2))
        .andExpect(jsonPath("$.limits.currentMembers").value(1));
  }

  // --- 3. POST /internal/billing/set-plan updates subscription + org tier ---

  @Test
  void shouldUpdatePlanToProViaBillingEndpoint() throws Exception {
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

    var org = organizationRepository.findByClerkOrgId(ORG_A).orElseThrow();
    assertThat(org.getTier()).isEqualTo(Tier.PRO);
    assertThat(org.getPlanSlug()).isEqualTo("pro");

    var subscription = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    assertThat(subscription.getPlanSlug()).isEqualTo("pro");
  }

  // --- 4. GET /api/billing/subscription reflects PRO after plan change ---

  @Test
  void shouldReflectProAfterPlanChange() throws Exception {
    // Ensure plan is PRO (may already be from test 3, but tests should be independent)
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

    mockMvc
        .perform(get("/api/billing/subscription").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planSlug").value("pro"))
        .andExpect(jsonPath("$.tier").value("PRO"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.limits.maxMembers").value(10));
  }

  // --- 5. Admin set-plan triggers Starter→Pro upgrade ---

  @Test
  void shouldTriggerUpgradeOnStarterToProTransition() throws Exception {
    String upgradeOrg = "org_billing_upgrade";
    provisioningService.provisionTenant(upgradeOrg, "Upgrade Test Org");

    // Verify starts as STARTER
    var org = organizationRepository.findByClerkOrgId(upgradeOrg).orElseThrow();
    assertThat(org.getTier()).isEqualTo(Tier.STARTER);

    // Trigger plan change → should trigger upgrade (Starter→Pro schema migration)
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
                        .formatted(upgradeOrg)))
        .andExpect(status().isOk());

    // After upgrade, org should be PRO
    org = organizationRepository.findByClerkOrgId(upgradeOrg).orElseThrow();
    assertThat(org.getTier()).isEqualTo(Tier.PRO);
  }

  // --- 6. Set-plan for unknown org returns 404 ---

  @Test
  void shouldReturn404ForUnknownOrg() throws Exception {
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
        .andExpect(status().isNotFound());
  }

  // --- 7. API key required for internal endpoint ---

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
        .jwt(j -> j.subject("user_billing_owner").claim("o", Map.of("id", ORG_A, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
