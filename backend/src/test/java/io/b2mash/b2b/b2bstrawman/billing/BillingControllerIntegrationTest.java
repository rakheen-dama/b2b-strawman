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
class BillingControllerIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_billing_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private SubscriptionRepository subscriptionRepository;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Billing Ctrl Test Org", null);
    syncMember(ORG_ID, "user_ctrl_owner", "owner@test.com", "Owner", "owner");
  }

  // --- GET /api/billing/subscription ---

  @Test
  void getSubscription_returnsTrialingStatus() throws Exception {
    mockMvc
        .perform(get("/api/billing/subscription").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TRIALING"))
        .andExpect(jsonPath("$.canSubscribe").value(true))
        .andExpect(jsonPath("$.canCancel").value(false))
        .andExpect(jsonPath("$.billingMethod").value("MANUAL"))
        .andExpect(jsonPath("$.adminManaged").value(true))
        .andExpect(jsonPath("$.adminNote").isEmpty());
  }

  @Test
  void getSubscription_withoutJwt_returns401() throws Exception {
    mockMvc.perform(get("/api/billing/subscription")).andExpect(status().isUnauthorized());
  }

  @Test
  void getSubscription_pilotCannotSubscribe() throws Exception {
    // Set billing method to PILOT — canSubscribe should be false
    var org = organizationRepository.findByClerkOrgId(ORG_ID).orElseThrow();
    var sub = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    var originalMethod = sub.getBillingMethod();
    sub.setBillingMethod(BillingMethod.PILOT);
    subscriptionRepository.saveAndFlush(sub);

    try {
      mockMvc
          .perform(get("/api/billing/subscription").with(ownerJwt()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.billingMethod").value("PILOT"))
          .andExpect(jsonPath("$.adminManaged").value(true))
          .andExpect(jsonPath("$.canSubscribe").value(false));
    } finally {
      // Restore original billing method
      sub.setBillingMethod(originalMethod);
      subscriptionRepository.saveAndFlush(sub);
    }
  }

  // --- POST /api/billing/subscribe ---

  @Test
  void subscribe_withOwner_returns200() throws Exception {
    mockMvc.perform(post("/api/billing/subscribe").with(ownerJwt())).andExpect(status().isOk());
  }

  @Test
  void subscribe_withMember_returns403() throws Exception {
    mockMvc
        .perform(post("/api/billing/subscribe").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- POST /api/billing/cancel ---

  @Test
  void cancel_whenTrialing_returns400() throws Exception {
    // Subscription starts TRIALING — cancel should fail
    mockMvc
        .perform(post("/api/billing/cancel").with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  // --- GET /api/billing/payments ---

  @Test
  void getPayments_withOwner_returnsEmptyPage() throws Exception {
    mockMvc
        .perform(get("/api/billing/payments").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  // --- POST /internal/billing/extend-trial ---

  @Test
  void extendTrial_withApiKey_extendsTrialEndsAt() throws Exception {
    var org = organizationRepository.findByClerkOrgId(ORG_ID).orElseThrow();
    var subBefore = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    var originalTrialEnd = subBefore.getTrialEndsAt();

    mockMvc
        .perform(
            post("/internal/billing/extend-trial")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "organizationId": "%s",
                      "additionalDays": 14
                    }
                    """
                        .formatted(org.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TRIALING"));

    var subAfter = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    assertThat(subAfter.getTrialEndsAt()).isAfter(originalTrialEnd);
  }

  @Test
  void extendTrial_withoutApiKey_returns401() throws Exception {
    var org = organizationRepository.findByClerkOrgId(ORG_ID).orElseThrow();
    mockMvc
        .perform(
            post("/internal/billing/extend-trial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "organizationId": "%s",
                      "additionalDays": 14
                    }
                    """
                        .formatted(org.getId())))
        .andExpect(status().isUnauthorized());
  }

  // --- POST /internal/billing/activate ---

  @Test
  void activate_withApiKey_transitionsToActive() throws Exception {
    // Provision a fresh org so we don't pollute shared ORG_ID state
    String freshOrg = "org_billing_activate_test";
    provisioningService.provisionTenant(freshOrg, "Activate Test Org", null);
    var org = organizationRepository.findByClerkOrgId(freshOrg).orElseThrow();

    mockMvc
        .perform(
            post("/internal/billing/activate")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "organizationId": "%s"
                    }
                    """
                        .formatted(org.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
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

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ctrl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ctrl_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }
}
