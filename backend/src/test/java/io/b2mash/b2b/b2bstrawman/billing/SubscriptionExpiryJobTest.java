package io.b2mash.b2b.b2bstrawman.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionExpiryJobTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_expiry_test";

  @Autowired private SubscriptionExpiryJob expiryJob;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private SubscriptionStatusCache statusCache;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private MockMvc mockMvc;

  private Organization organization;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Expiry Test Org", null);
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_expiry", "expiry@test.com", "Expiry User", "owner");
    organization = organizationRepository.findByExternalOrgId(ORG_ID).orElseThrow();
  }

  @BeforeEach
  void evictCache() {
    statusCache.evict(organization.getId());
  }

  // --- Trial Expiry Tests ---

  @Test
  void processTrialExpiry_transitionsExpiredTrialToExpired() {
    var sub = createSubscriptionInStatus(Subscription.SubscriptionStatus.TRIALING);
    sub.setTrialEndsAt(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processTrialExpiry();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.EXPIRED);
    assertThat(updated.getGraceEndsAt()).isNotNull();
  }

  @Test
  void processTrialExpiry_ignoresFutureTrials() {
    var sub = createSubscriptionInStatus(Subscription.SubscriptionStatus.TRIALING);
    sub.setTrialEndsAt(Instant.now().plus(Duration.ofDays(7)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processTrialExpiry();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.TRIALING);
  }

  // --- Grace Period Expiry Tests ---

  @Test
  void processGraceExpiry_transitionsGracePeriodToLocked() {
    var sub = createSubscriptionInStatus(Subscription.SubscriptionStatus.GRACE_PERIOD);
    sub.setGraceEndsAt(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processGraceExpiry();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.LOCKED);
  }

  @Test
  void processGraceExpiry_transitionsExpiredToLocked() {
    var sub = createSubscriptionInStatus(Subscription.SubscriptionStatus.EXPIRED);
    sub.setGraceEndsAt(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processGraceExpiry();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.LOCKED);
  }

  @Test
  void processGraceExpiry_transitionsSuspendedToLocked() {
    var sub = createSubscriptionInStatus(Subscription.SubscriptionStatus.SUSPENDED);
    sub.setGraceEndsAt(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processGraceExpiry();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.LOCKED);
  }

  @Test
  void processGraceExpiry_ignoresFutureGracePeriods() {
    var sub = createSubscriptionInStatus(Subscription.SubscriptionStatus.GRACE_PERIOD);
    sub.setGraceEndsAt(Instant.now().plus(Duration.ofDays(30)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processGraceExpiry();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus())
        .isEqualTo(Subscription.SubscriptionStatus.GRACE_PERIOD);
  }

  // --- Pending Cancellation End Tests ---

  @Test
  void processPendingCancellationEnd_transitionsToGracePeriod() {
    var sub = createSubscriptionInStatus(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
    sub.setCurrentPeriodEnd(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processPendingCancellationEnd();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus())
        .isEqualTo(Subscription.SubscriptionStatus.GRACE_PERIOD);
    assertThat(updated.getGraceEndsAt()).isNotNull();
  }

  @Test
  void processPendingCancellationEnd_ignoresFuturePeriodEnd() {
    var sub = createSubscriptionInStatus(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
    sub.setCurrentPeriodEnd(Instant.now().plus(Duration.ofDays(15)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processPendingCancellationEnd();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus())
        .isEqualTo(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
  }

  // --- Helper Methods ---

  private Subscription createSubscriptionInStatus(Subscription.SubscriptionStatus status) {
    subscriptionRepository
        .findByOrganizationId(organization.getId())
        .ifPresent(
            s -> {
              subscriptionRepository.delete(s);
              subscriptionRepository.flush();
            });

    var fresh = new Subscription(organization.getId());
    switch (status) {
      case TRIALING -> {
        /* already TRIALING */
      }
      case EXPIRED -> fresh.transitionTo(Subscription.SubscriptionStatus.EXPIRED);
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
      case PENDING_CANCELLATION -> {
        fresh.transitionTo(Subscription.SubscriptionStatus.ACTIVE);
        fresh.transitionTo(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
      }
      default -> throw new IllegalArgumentException("Unsupported test status: " + status);
    }
    return subscriptionRepository.saveAndFlush(fresh);
  }
}
