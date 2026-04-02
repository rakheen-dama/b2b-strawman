package io.b2mash.b2b.b2bstrawman.billing;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class SubscriptionEntityTest {

  @Autowired private SubscriptionRepository subscriptionRepository;

  @Autowired private SubscriptionPaymentRepository subscriptionPaymentRepository;

  @Autowired private OrganizationRepository organizationRepository;

  /** Creates a persisted Organization and returns its database-generated UUID. */
  private UUID createOrganization(String externalOrgId) {
    var org = new Organization(externalOrgId, "Test Org " + externalOrgId);
    return organizationRepository.saveAndFlush(org).getId();
  }

  @Test
  void shouldPersistAndRetrieveSubscriptionWithTrialingStatus() {
    var orgId = createOrganization("entity-test-persist-" + UUID.randomUUID());
    var subscription = new Subscription(orgId);

    var saved = subscriptionRepository.saveAndFlush(subscription);

    var found = subscriptionRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getOrganizationId()).isEqualTo(orgId);
    assertThat(found.getSubscriptionStatus()).isEqualTo(Subscription.SubscriptionStatus.TRIALING);
    assertThat(found.getTrialEndsAt()).isNotNull();
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isNotNull();
  }

  @Test
  void shouldPersistSubscriptionPaymentAndRetrieve() {
    var orgId = createOrganization("entity-test-payment-" + UUID.randomUUID());
    var subscription = subscriptionRepository.saveAndFlush(new Subscription(orgId));

    var payment =
        new SubscriptionPayment(
            subscription.getId(),
            "pf-pay-123",
            49900,
            "ZAR",
            SubscriptionPayment.PaymentStatus.COMPLETE,
            Instant.now(),
            null);

    var saved = subscriptionPaymentRepository.saveAndFlush(payment);

    var found = subscriptionPaymentRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getSubscriptionId()).isEqualTo(subscription.getId());
    assertThat(found.getPayfastPaymentId()).isEqualTo("pf-pay-123");
    assertThat(found.getAmountCents()).isEqualTo(49900);
    assertThat(found.getCurrency()).isEqualTo("ZAR");
    assertThat(found.getStatus()).isEqualTo(SubscriptionPayment.PaymentStatus.COMPLETE);
    assertThat(found.getPaymentDate()).isNotNull();
    assertThat(found.getCreatedAt()).isNotNull();
  }

  @Test
  void shouldRoundTripSubscriptionPaymentRawItnJsonb() {
    var orgId = createOrganization("entity-test-itn-" + UUID.randomUUID());
    var subscription = subscriptionRepository.saveAndFlush(new Subscription(orgId));

    var itnData =
        Map.of("m_payment_id", "12345", "pf_payment_id", "67890", "amount_gross", "499.00");
    var payment =
        new SubscriptionPayment(
            subscription.getId(),
            "pf-pay-456",
            49900,
            "ZAR",
            SubscriptionPayment.PaymentStatus.COMPLETE,
            Instant.now(),
            itnData);

    var saved = subscriptionPaymentRepository.saveAndFlush(payment);

    var found = subscriptionPaymentRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getRawItn()).isNotNull();
    assertThat(found.getRawItn()).containsEntry("m_payment_id", "12345");
    assertThat(found.getRawItn()).containsEntry("pf_payment_id", "67890");
    assertThat(found.getRawItn()).containsEntry("amount_gross", "499.00");
  }

  @Test
  void shouldFindByOrganizationIdReturnsCorrectSubscription() {
    var orgId1 = createOrganization("entity-test-find-1-" + UUID.randomUUID());
    var orgId2 = createOrganization("entity-test-find-2-" + UUID.randomUUID());
    subscriptionRepository.saveAndFlush(new Subscription(orgId1));
    subscriptionRepository.saveAndFlush(new Subscription(orgId2));

    var found = subscriptionRepository.findByOrganizationId(orgId1);

    assertThat(found).isPresent();
    assertThat(found.get().getOrganizationId()).isEqualTo(orgId1);
  }

  @Test
  void shouldFindBySubscriptionStatusAndTrialEndsAtBefore() {
    // Subscription with trial expired (past)
    var orgExpired = createOrganization("entity-test-trial-expired-" + UUID.randomUUID());
    var expired = new Subscription(orgExpired);
    expired.setTrialEndsAt(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(expired);

    // Subscription with trial still active (future)
    var orgActive = createOrganization("entity-test-trial-active-" + UUID.randomUUID());
    var active = new Subscription(orgActive);
    active.setTrialEndsAt(Instant.now().plus(Duration.ofDays(7)));
    subscriptionRepository.saveAndFlush(active);

    var results =
        subscriptionRepository.findBySubscriptionStatusAndTrialEndsAtBefore(
            Subscription.SubscriptionStatus.TRIALING, Instant.now());

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getOrganizationId()).isEqualTo(orgExpired);
  }

  @Test
  void shouldFindBySubscriptionStatusInAndGraceEndsAtBefore() {
    // Subscription in GRACE_PERIOD with grace expired
    var orgGrace = createOrganization("entity-test-grace-" + UUID.randomUUID());
    var graceSub = new Subscription(orgGrace);
    graceSub.transitionTo(Subscription.SubscriptionStatus.ACTIVE);
    graceSub.transitionTo(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
    graceSub.transitionTo(Subscription.SubscriptionStatus.GRACE_PERIOD);
    graceSub.setGraceEndsAt(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(graceSub);

    // Subscription in SUSPENDED with grace expired
    var orgSuspended = createOrganization("entity-test-suspended-" + UUID.randomUUID());
    var suspendedSub = new Subscription(orgSuspended);
    suspendedSub.transitionTo(Subscription.SubscriptionStatus.ACTIVE);
    suspendedSub.transitionTo(Subscription.SubscriptionStatus.PAST_DUE);
    suspendedSub.transitionTo(Subscription.SubscriptionStatus.SUSPENDED);
    suspendedSub.setGraceEndsAt(Instant.now().minus(Duration.ofDays(2)));
    subscriptionRepository.saveAndFlush(suspendedSub);

    var results =
        subscriptionRepository.findBySubscriptionStatusInAndGraceEndsAtBefore(
            List.of(
                Subscription.SubscriptionStatus.GRACE_PERIOD,
                Subscription.SubscriptionStatus.SUSPENDED),
            Instant.now());

    assertThat(results).hasSize(2);
    assertThat(results)
        .extracting(Subscription::getOrganizationId)
        .containsExactlyInAnyOrder(orgGrace, orgSuspended);
  }

  @Test
  void shouldFindBySubscriptionStatusAndCurrentPeriodEndBefore() {
    var orgPending = createOrganization("entity-test-pending-" + UUID.randomUUID());
    var pendingSub = new Subscription(orgPending);
    pendingSub.transitionTo(Subscription.SubscriptionStatus.ACTIVE);
    pendingSub.transitionTo(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
    pendingSub.setCurrentPeriodEnd(Instant.now().minus(Duration.ofDays(1)));
    subscriptionRepository.saveAndFlush(pendingSub);

    // Subscription with future period end — should NOT appear
    var orgFuture = createOrganization("entity-test-future-" + UUID.randomUUID());
    var futureSub = new Subscription(orgFuture);
    futureSub.transitionTo(Subscription.SubscriptionStatus.ACTIVE);
    futureSub.transitionTo(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
    futureSub.setCurrentPeriodEnd(Instant.now().plus(Duration.ofDays(30)));
    subscriptionRepository.saveAndFlush(futureSub);

    var results =
        subscriptionRepository.findBySubscriptionStatusAndCurrentPeriodEndBefore(
            Subscription.SubscriptionStatus.PENDING_CANCELLATION, Instant.now());

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getOrganizationId()).isEqualTo(orgPending);
  }

  @Test
  void shouldPersistSubscriptionWithNullOptionalFields() {
    var orgId = createOrganization("entity-test-null-fields-" + UUID.randomUUID());
    var subscription = new Subscription(orgId);
    // Do not set any optional fields — they should remain null

    var saved = subscriptionRepository.saveAndFlush(subscription);

    var found = subscriptionRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getPayfastToken()).isNull();
    assertThat(found.getGraceEndsAt()).isNull();
    assertThat(found.getMonthlyAmountCents()).isNull();
    assertThat(found.getCurrency()).isNull();
    assertThat(found.getLastPaymentAt()).isNull();
    assertThat(found.getNextBillingAt()).isNull();
    assertThat(found.getPayfastPaymentId()).isNull();
    assertThat(found.getCurrentPeriodStart()).isNull();
    assertThat(found.getCurrentPeriodEnd()).isNull();
    assertThat(found.getCancelledAt()).isNull();
  }
}
