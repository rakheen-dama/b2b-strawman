package io.b2mash.b2b.b2bstrawman.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerLifecycleEntityTest {

  @Test
  void transitionLifecycleStatus_setsAllFields() {
    // Start as PROSPECT to test transition to ONBOARDING
    var customer =
        new Customer(
            "Test Co",
            "test@test.com",
            null,
            null,
            null,
            UUID.randomUUID(),
            null,
            LifecycleStatus.PROSPECT);
    UUID actorId = UUID.randomUUID();

    customer.transitionLifecycleStatus(LifecycleStatus.ONBOARDING, actorId);

    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.ONBOARDING);
    assertThat(customer.getLifecycleStatusChangedAt()).isNotNull();
    assertThat(customer.getLifecycleStatusChangedBy()).isEqualTo(actorId);
  }

  @Test
  void transitionLifecycleStatus_rejectsInvalidTransition() {
    // PROSPECT cannot go directly to DORMANT
    var customer = new Customer("Test Co", "test@test.com", null, null, null, UUID.randomUUID());
    UUID actorId = UUID.randomUUID();

    assertThatThrownBy(() -> customer.transitionLifecycleStatus(LifecycleStatus.DORMANT, actorId))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Cannot transition from PROSPECT to DORMANT");
  }

  @Test
  void transitionLifecycleStatus_offboardedCanReactivateToActive() {
    var customer =
        new Customer(
            "Test Co",
            "test@test.com",
            null,
            null,
            null,
            UUID.randomUUID(),
            null,
            LifecycleStatus.ACTIVE);
    UUID actorId = UUID.randomUUID();
    customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDING, actorId);
    customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDED, actorId);

    // OFFBOARDED -> ACTIVE is now allowed (reactivation)
    customer.transitionLifecycleStatus(LifecycleStatus.ACTIVE, actorId);
    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
  }

  @Test
  void transitionLifecycleStatus_offboardedCannotGoToProspect() {
    var customer =
        new Customer(
            "Test Co",
            "test@test.com",
            null,
            null,
            null,
            UUID.randomUUID(),
            null,
            LifecycleStatus.ACTIVE);
    UUID actorId = UUID.randomUUID();
    customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDING, actorId);
    customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDED, actorId);

    assertThatThrownBy(() -> customer.transitionLifecycleStatus(LifecycleStatus.PROSPECT, actorId))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void transitionLifecycleStatus_prospectCannotTransitionDirectlyToActive() {
    var customer = new Customer("Test Co", "test@test.com", null, null, null, UUID.randomUUID());
    UUID actorId = UUID.randomUUID();

    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.PROSPECT);
    assertThatThrownBy(() -> customer.transitionLifecycleStatus(LifecycleStatus.ACTIVE, actorId))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Cannot transition from PROSPECT to ACTIVE");
  }

  @Test
  void transitionLifecycleStatus_setsOffboardedAtWhenOffboarded() {
    var customer =
        new Customer(
            "Test Co",
            "test@test.com",
            null,
            null,
            null,
            UUID.randomUUID(),
            null,
            LifecycleStatus.ACTIVE);
    UUID actorId = UUID.randomUUID();
    customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDING, actorId);

    assertThat(customer.getOffboardedAt()).isNull();
    customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDED, actorId);
    assertThat(customer.getOffboardedAt()).isNotNull();
  }

  @Test
  void anonymize_replacesPII() {
    var customer =
        new Customer(
            "Jane Doe", "jane@example.com", "+27821234567", "SA1234", null, UUID.randomUUID());

    customer.anonymize("Anonymized Customer abc123");

    assertThat(customer.getName()).isEqualTo("Anonymized Customer abc123");
    assertThat(customer.getEmail()).doesNotContain("jane");
    assertThat(customer.getEmail()).endsWith("@anonymized.invalid");
    assertThat(customer.getEmail()).startsWith("anon-");
    assertThat(customer.getPhone()).isNull();
    assertThat(customer.getIdNumber()).isNull();
  }
}
