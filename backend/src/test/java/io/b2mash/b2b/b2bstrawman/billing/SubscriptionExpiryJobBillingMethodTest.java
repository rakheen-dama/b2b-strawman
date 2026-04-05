package io.b2mash.b2b.b2bstrawman.billing;

import static org.assertj.core.api.Assertions.assertThat;

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
class SubscriptionExpiryJobBillingMethodTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_expiry_bm_test";

  @Autowired private SubscriptionExpiryJob expiryJob;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private SubscriptionStatusCache statusCache;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private MockMvc mockMvc;

  private Organization organization;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Expiry BM Test Org", null);
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_expiry_bm", "expiry_bm@test.com", "Expiry BM User", "owner");
    organization = organizationRepository.findByExternalOrgId(ORG_ID).orElseThrow();
  }

  @BeforeEach
  void evictCache() {
    statusCache.evict(organization.getId());
  }

  @Test
  void trialExpiry_processesManualSubscriptions() {
    var sub = createSubscriptionInTrialing(BillingMethod.MANUAL);
    sub.setTrialEndsAt(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processTrialExpiry();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.EXPIRED);
    assertThat(updated.getGraceEndsAt()).isNotNull();
  }

  @Test
  void trialExpiry_processesPayfastSubscriptions() {
    var sub = createSubscriptionInTrialing(BillingMethod.PAYFAST);
    sub.setTrialEndsAt(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processTrialExpiry();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.EXPIRED);
    assertThat(updated.getGraceEndsAt()).isNotNull();
  }

  @Test
  void trialExpiry_skipsPilotSubscriptions() {
    var sub = createSubscriptionInTrialing(BillingMethod.PILOT);
    sub.setTrialEndsAt(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processTrialExpiry();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.TRIALING);
  }

  @Test
  void trialExpiry_skipsComplimentarySubscriptions() {
    var sub = createSubscriptionInTrialing(BillingMethod.COMPLIMENTARY);
    sub.setTrialEndsAt(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processTrialExpiry();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.TRIALING);
  }

  @Test
  void trialExpiry_skipsDebitOrderSubscriptions() {
    var sub = createSubscriptionInTrialing(BillingMethod.DEBIT_ORDER);
    sub.setTrialEndsAt(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processTrialExpiry();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.TRIALING);
  }

  @Test
  void graceExpiry_appliesToAllBillingMethods() {
    // Set up a PILOT subscription in GRACE_PERIOD with expired grace
    var sub = createSubscriptionInGracePeriod(BillingMethod.PILOT);
    sub.setGraceEndsAt(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(sub);

    expiryJob.processGraceExpiry();

    var updated = subscriptionRepository.findByOrganizationId(organization.getId()).orElseThrow();
    assertThat(updated.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.LOCKED);
  }

  private Subscription createSubscriptionInTrialing(BillingMethod billingMethod) {
    subscriptionRepository
        .findByOrganizationId(organization.getId())
        .ifPresent(
            s -> {
              subscriptionRepository.delete(s);
              subscriptionRepository.flush();
            });

    var fresh = new Subscription(organization.getId());
    fresh.setBillingMethod(billingMethod);
    return subscriptionRepository.saveAndFlush(fresh);
  }

  private Subscription createSubscriptionInGracePeriod(BillingMethod billingMethod) {
    subscriptionRepository
        .findByOrganizationId(organization.getId())
        .ifPresent(
            s -> {
              subscriptionRepository.delete(s);
              subscriptionRepository.flush();
            });

    var fresh = new Subscription(organization.getId());
    fresh.setBillingMethod(billingMethod);
    fresh.transitionTo(Subscription.SubscriptionStatus.ACTIVE);
    fresh.transitionTo(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
    fresh.transitionTo(Subscription.SubscriptionStatus.GRACE_PERIOD);
    return subscriptionRepository.saveAndFlush(fresh);
  }
}
