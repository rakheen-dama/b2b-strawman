package io.b2mash.b2b.b2bstrawman.billing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
class SubscriptionGuardFilterTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_guard_filter_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private SubscriptionStatusCache statusCache;

  private Organization organization;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Guard Filter Test Org", null);
    syncMember(ORG_ID, "user_guard_owner", "guard-owner@test.com", "Guard Owner", "owner");
    organization = organizationRepository.findByExternalOrgId(ORG_ID).orElseThrow();
  }

  @BeforeEach
  void evictCache() {
    // Evict cache before each test to ensure status changes are reflected
    statusCache.evict(organization.getId());
  }

  // --- TRIALING ---

  @Test
  void trialing_get_passes() throws Exception {
    setSubscriptionStatus(Subscription.SubscriptionStatus.TRIALING);
    mockMvc.perform(get("/api/billing/subscription").with(ownerJwt())).andExpect(status().isOk());
  }

  @Test
  void trialing_post_passes() throws Exception {
    setSubscriptionStatus(Subscription.SubscriptionStatus.TRIALING);
    // POST to billing endpoint — should pass (TRIALING allows writes)
    mockMvc
        .perform(post("/api/billing/subscribe").with(ownerJwt()))
        .andExpect(
            result -> {
              int s = result.getResponse().getStatus();
              // Should NOT be 403 from the guard filter
              assert s != 403
                  || !result.getResponse().getContentAsString().contains("subscription_required");
            });
  }

  // --- ACTIVE ---

  @Test
  void active_post_passes() throws Exception {
    setSubscriptionStatus(Subscription.SubscriptionStatus.ACTIVE);
    mockMvc
        .perform(post("/api/billing/subscribe").with(ownerJwt()))
        .andExpect(
            result -> {
              int s = result.getResponse().getStatus();
              assert s != 403
                  || !result.getResponse().getContentAsString().contains("subscription_required");
            });
  }

  // --- GRACE_PERIOD ---

  @Test
  void gracePeriod_get_passes() throws Exception {
    setSubscriptionStatus(Subscription.SubscriptionStatus.GRACE_PERIOD);
    // GET should pass through (read-only state allows reads)
    mockMvc.perform(get("/api/billing/subscription").with(ownerJwt())).andExpect(status().isOk());
  }

  @Test
  void gracePeriod_post_blocked() throws Exception {
    setSubscriptionStatus(Subscription.SubscriptionStatus.GRACE_PERIOD);
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Test\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("subscription_required"))
        .andExpect(jsonPath("$.resubscribeUrl").value("/settings/billing"));
  }

  @Test
  void gracePeriod_put_blocked() throws Exception {
    setSubscriptionStatus(Subscription.SubscriptionStatus.GRACE_PERIOD);
    mockMvc
        .perform(
            put("/api/projects/00000000-0000-0000-0000-000000000001")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Test\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("subscription_required"));
  }

  @Test
  void gracePeriod_delete_blocked() throws Exception {
    setSubscriptionStatus(Subscription.SubscriptionStatus.GRACE_PERIOD);
    mockMvc
        .perform(delete("/api/projects/00000000-0000-0000-0000-000000000001").with(ownerJwt()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("subscription_required"));
  }

  @Test
  void gracePeriod_billingPath_passes() throws Exception {
    setSubscriptionStatus(Subscription.SubscriptionStatus.GRACE_PERIOD);
    // POST to billing path should pass even in GRACE_PERIOD
    mockMvc
        .perform(post("/api/billing/subscribe").with(ownerJwt()))
        .andExpect(
            result -> {
              int s = result.getResponse().getStatus();
              assert s != 403
                  || !result.getResponse().getContentAsString().contains("subscription_required");
            });
  }

  // --- SUSPENDED ---

  @Test
  void suspended_post_blocked() throws Exception {
    setSubscriptionStatus(Subscription.SubscriptionStatus.SUSPENDED);
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Test\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("subscription_required"));
  }

  // --- EXPIRED ---

  @Test
  void expired_post_blocked() throws Exception {
    setSubscriptionStatus(Subscription.SubscriptionStatus.EXPIRED);
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Test\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("subscription_required"));
  }

  // --- LOCKED ---

  @Test
  void locked_get_blocked() throws Exception {
    setSubscriptionStatus(Subscription.SubscriptionStatus.LOCKED);
    mockMvc
        .perform(get("/api/projects").with(ownerJwt()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("subscription_locked"))
        .andExpect(jsonPath("$.resubscribeUrl").value("/settings/billing"));
  }

  @Test
  void locked_post_blocked() throws Exception {
    setSubscriptionStatus(Subscription.SubscriptionStatus.LOCKED);
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Test\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("subscription_locked"));
  }

  @Test
  void locked_billingPath_passes() throws Exception {
    setSubscriptionStatus(Subscription.SubscriptionStatus.LOCKED);
    // GET billing path should pass even when LOCKED
    mockMvc.perform(get("/api/billing/subscription").with(ownerJwt())).andExpect(status().isOk());
  }

  // --- No org context ---

  @Test
  void noOrgContext_passes() throws Exception {
    // Request without JWT — no org context bound, guard filter skips
    mockMvc
        .perform(get("/api/projects"))
        .andExpect(
            result -> {
              int s = result.getResponse().getStatus();
              // Should be 401 (unauthenticated) not 403 from guard
              assert s != 403
                  || !result.getResponse().getContentAsString().contains("subscription_");
            });
  }

  // --- Helpers ---

  /**
   * Sets the subscription to the target status by transitioning through valid state paths. Uses
   * direct field manipulation via the repository since the entity enforces valid transitions.
   */
  private void setSubscriptionStatus(Subscription.SubscriptionStatus targetStatus) {
    var subscription =
        subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();

    // Reset to TRIALING first by deleting and recreating
    if (subscription.getSubscriptionStatus() != targetStatus) {
      subscriptionRepository.delete(subscription);
      subscriptionRepository.flush();

      var fresh = new Subscription(organization.getId());
      // Now transition through valid paths to reach the target status
      switch (targetStatus) {
        case TRIALING -> {
          // Already TRIALING
        }
        case ACTIVE -> {
          fresh.transitionTo(Subscription.SubscriptionStatus.ACTIVE);
        }
        case PENDING_CANCELLATION -> {
          fresh.transitionTo(Subscription.SubscriptionStatus.ACTIVE);
          fresh.transitionTo(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
        }
        case PAST_DUE -> {
          fresh.transitionTo(Subscription.SubscriptionStatus.ACTIVE);
          fresh.transitionTo(Subscription.SubscriptionStatus.PAST_DUE);
        }
        case GRACE_PERIOD -> {
          fresh.transitionTo(Subscription.SubscriptionStatus.ACTIVE);
          fresh.transitionTo(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
          fresh.transitionTo(Subscription.SubscriptionStatus.GRACE_PERIOD);
        }
        case SUSPENDED -> {
          fresh.transitionTo(Subscription.SubscriptionStatus.ACTIVE);
          fresh.transitionTo(Subscription.SubscriptionStatus.PAST_DUE);
          fresh.transitionTo(Subscription.SubscriptionStatus.SUSPENDED);
        }
        case EXPIRED -> {
          fresh.transitionTo(Subscription.SubscriptionStatus.EXPIRED);
        }
        case LOCKED -> {
          fresh.transitionTo(Subscription.SubscriptionStatus.EXPIRED);
          fresh.transitionTo(Subscription.SubscriptionStatus.LOCKED);
        }
      }
      subscriptionRepository.saveAndFlush(fresh);
    }

    // Evict cache so the new status is picked up
    statusCache.evict(organization.getId());
  }

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
        .jwt(j -> j.subject("user_guard_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }
}
