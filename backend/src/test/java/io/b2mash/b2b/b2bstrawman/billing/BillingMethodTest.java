package io.b2mash.b2b.b2bstrawman.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.billing.Subscription.SubscriptionStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

class BillingMethodTest {

  // --- Unit tests for enum methods ---

  @Test
  void newSubscription_defaultsBillingMethodToManual() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    assertThat(sub.getBillingMethod()).isEqualTo(BillingMethod.MANUAL);
  }

  @Test
  void isAdminManaged_trueForDebitOrder() {
    assertThat(BillingMethod.DEBIT_ORDER.isAdminManaged()).isTrue();
  }

  @Test
  void isAdminManaged_trueForPilot() {
    assertThat(BillingMethod.PILOT.isAdminManaged()).isTrue();
  }

  @Test
  void isAdminManaged_trueForComplimentary() {
    assertThat(BillingMethod.COMPLIMENTARY.isAdminManaged()).isTrue();
  }

  @Test
  void isAdminManaged_trueForManual() {
    assertThat(BillingMethod.MANUAL.isAdminManaged()).isTrue();
  }

  @Test
  void isAdminManaged_falseForPayfast() {
    assertThat(BillingMethod.PAYFAST.isAdminManaged()).isFalse();
  }

  @Test
  void isTrialAutoExpiring_trueForPayfast() {
    assertThat(BillingMethod.PAYFAST.isTrialAutoExpiring()).isTrue();
  }

  @Test
  void isTrialAutoExpiring_trueForManual() {
    assertThat(BillingMethod.MANUAL.isTrialAutoExpiring()).isTrue();
  }

  @Test
  void isTrialAutoExpiring_falseForDebitOrder() {
    assertThat(BillingMethod.DEBIT_ORDER.isTrialAutoExpiring()).isFalse();
  }

  @Test
  void isTrialAutoExpiring_falseForPilot() {
    assertThat(BillingMethod.PILOT.isTrialAutoExpiring()).isFalse();
  }

  @Test
  void isTrialAutoExpiring_falseForComplimentary() {
    assertThat(BillingMethod.COMPLIMENTARY.isTrialAutoExpiring()).isFalse();
  }

  @Test
  void isCleanupEligible_trueForPilot() {
    assertThat(BillingMethod.PILOT.isCleanupEligible()).isTrue();
  }

  @Test
  void isCleanupEligible_trueForComplimentary() {
    assertThat(BillingMethod.COMPLIMENTARY.isCleanupEligible()).isTrue();
  }

  @Test
  void isCleanupEligible_falseForPayfast() {
    assertThat(BillingMethod.PAYFAST.isCleanupEligible()).isFalse();
  }

  @Test
  void isCleanupEligible_falseForDebitOrder() {
    assertThat(BillingMethod.DEBIT_ORDER.isCleanupEligible()).isFalse();
  }

  @Test
  void isCleanupEligible_falseForManual() {
    assertThat(BillingMethod.MANUAL.isCleanupEligible()).isFalse();
  }

  // --- Admin transition tests ---

  @Test
  void adminTransitionTo_active_succeeds() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.adminTransitionTo(SubscriptionStatus.ACTIVE);
    assertThat(sub.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
  }

  @Test
  void adminTransitionTo_pendingCancellation_throwsInvalidStateException() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    assertThatThrownBy(() -> sub.adminTransitionTo(SubscriptionStatus.PENDING_CANCELLATION))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void adminTransitionTo_pastDue_throwsInvalidStateException() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    assertThatThrownBy(() -> sub.adminTransitionTo(SubscriptionStatus.PAST_DUE))
        .isInstanceOf(InvalidStateException.class);
  }

  // --- Integration tests for persistence ---

  @Nested
  @SpringBootTest
  @AutoConfigureMockMvc
  @Import(TestPostgresConfiguration.class)
  @ActiveProfiles("test")
  @Transactional
  class PersistenceTests {

    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private OrganizationRepository organizationRepository;

    private UUID createOrganization(String externalOrgId) {
      var org = new Organization(externalOrgId, "Test Org " + externalOrgId);
      return organizationRepository.saveAndFlush(org).getId();
    }

    @Test
    void billingMethod_persistsAndRetrievesCorrectly() {
      var orgId = createOrganization("bm-test-persist-" + UUID.randomUUID());
      var sub = new Subscription(orgId);
      sub.setBillingMethod(BillingMethod.PILOT);

      var saved = subscriptionRepository.saveAndFlush(sub);
      subscriptionRepository.flush();

      var found = subscriptionRepository.findById(saved.getId()).orElseThrow();
      assertThat(found.getBillingMethod()).isEqualTo(BillingMethod.PILOT);
    }

    @Test
    void adminNote_persistsAndRetrievesCorrectly() {
      var orgId = createOrganization("bm-test-note-" + UUID.randomUUID());
      var sub = new Subscription(orgId);
      sub.setAdminNote("Complimentary for strategic partner");

      var saved = subscriptionRepository.saveAndFlush(sub);

      var found = subscriptionRepository.findById(saved.getId()).orElseThrow();
      assertThat(found.getAdminNote()).isEqualTo("Complimentary for strategic partner");
    }

    @Test
    void billingMethodPersistsCorrectly_payfast() {
      // Verify PAYFAST billing method and payfast_token persist and round-trip correctly
      var orgId = createOrganization("bm-test-migration-" + UUID.randomUUID());
      var sub = new Subscription(orgId);
      sub.setPayfastToken("tok_test_migration");
      sub.setBillingMethod(BillingMethod.PAYFAST);

      var saved = subscriptionRepository.saveAndFlush(sub);

      var found = subscriptionRepository.findById(saved.getId()).orElseThrow();
      assertThat(found.getBillingMethod()).isEqualTo(BillingMethod.PAYFAST);
      assertThat(found.getPayfastToken()).isEqualTo("tok_test_migration");
    }
  }
}
