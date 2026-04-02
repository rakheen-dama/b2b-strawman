package io.b2mash.b2b.b2bstrawman.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.billing.Subscription.SubscriptionStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionLifecycleTest {

  // --- Valid transitions ---

  @Test
  void trialing_to_active_isValid() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.transitionTo(SubscriptionStatus.ACTIVE);
    assertThat(sub.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
  }

  @Test
  void trialing_to_expired_isValid() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.transitionTo(SubscriptionStatus.EXPIRED);
    assertThat(sub.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
  }

  @Test
  void active_to_pendingCancellation_isValid() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.transitionTo(SubscriptionStatus.ACTIVE);
    sub.transitionTo(SubscriptionStatus.PENDING_CANCELLATION);
    assertThat(sub.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.PENDING_CANCELLATION);
  }

  @Test
  void active_to_pastDue_isValid() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.transitionTo(SubscriptionStatus.ACTIVE);
    sub.transitionTo(SubscriptionStatus.PAST_DUE);
    assertThat(sub.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
  }

  @Test
  void pendingCancellation_to_gracePeriod_isValid() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.transitionTo(SubscriptionStatus.ACTIVE);
    sub.transitionTo(SubscriptionStatus.PENDING_CANCELLATION);
    sub.transitionTo(SubscriptionStatus.GRACE_PERIOD);
    assertThat(sub.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
  }

  @Test
  void pendingCancellation_to_active_isValid() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.transitionTo(SubscriptionStatus.ACTIVE);
    sub.transitionTo(SubscriptionStatus.PENDING_CANCELLATION);
    sub.transitionTo(SubscriptionStatus.ACTIVE);
    assertThat(sub.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
  }

  @Test
  void gracePeriod_to_locked_isValid() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.transitionTo(SubscriptionStatus.ACTIVE);
    sub.transitionTo(SubscriptionStatus.PENDING_CANCELLATION);
    sub.transitionTo(SubscriptionStatus.GRACE_PERIOD);
    sub.transitionTo(SubscriptionStatus.LOCKED);
    assertThat(sub.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.LOCKED);
  }

  @Test
  void expired_to_active_isValid() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.transitionTo(SubscriptionStatus.EXPIRED);
    sub.transitionTo(SubscriptionStatus.ACTIVE);
    assertThat(sub.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
  }

  @Test
  void locked_to_active_isValid() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.transitionTo(SubscriptionStatus.EXPIRED);
    sub.transitionTo(SubscriptionStatus.LOCKED);
    sub.transitionTo(SubscriptionStatus.ACTIVE);
    assertThat(sub.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
  }

  @Test
  void pastDue_to_suspended_isValid() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.transitionTo(SubscriptionStatus.ACTIVE);
    sub.transitionTo(SubscriptionStatus.PAST_DUE);
    sub.transitionTo(SubscriptionStatus.SUSPENDED);
    assertThat(sub.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
  }

  @Test
  void suspended_to_locked_isValid() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.transitionTo(SubscriptionStatus.ACTIVE);
    sub.transitionTo(SubscriptionStatus.PAST_DUE);
    sub.transitionTo(SubscriptionStatus.SUSPENDED);
    sub.transitionTo(SubscriptionStatus.LOCKED);
    assertThat(sub.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.LOCKED);
  }

  // --- Invalid transitions ---

  @Test
  void trialing_to_locked_throwsInvalidStateException() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    assertThatThrownBy(() -> sub.transitionTo(SubscriptionStatus.LOCKED))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void active_to_expired_throwsInvalidStateException() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.transitionTo(SubscriptionStatus.ACTIVE);
    assertThatThrownBy(() -> sub.transitionTo(SubscriptionStatus.EXPIRED))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void locked_to_trialing_throwsInvalidStateException() {
    var sub = new Subscription(UUID.randomUUID(), 14);
    sub.transitionTo(SubscriptionStatus.EXPIRED);
    sub.transitionTo(SubscriptionStatus.LOCKED);
    assertThatThrownBy(() -> sub.transitionTo(SubscriptionStatus.TRIALING))
        .isInstanceOf(InvalidStateException.class);
  }
}
