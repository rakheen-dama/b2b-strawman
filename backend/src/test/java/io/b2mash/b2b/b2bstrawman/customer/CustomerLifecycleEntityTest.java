package io.b2mash.b2b.b2bstrawman.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerLifecycleEntityTest {

  @Test
  void transitionLifecycleStatus_setsAllFields() {
    var customer = new Customer("Test Co", "test@test.com", null, null, null, UUID.randomUUID());
    UUID actorId = UUID.randomUUID();

    customer.transitionLifecycleStatus(LifecycleStatus.ONBOARDING, actorId);

    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.ONBOARDING);
    assertThat(customer.getLifecycleStatusChangedAt()).isNotNull();
    assertThat(customer.getLifecycleStatusChangedBy()).isEqualTo(actorId);
  }

  @Test
  void transitionLifecycleStatus_rejectsInvalidTransition() {
    var customer = new Customer("Test Co", "test@test.com", null, null, null, UUID.randomUUID());
    // PROSPECT cannot go to DORMANT
    UUID actorId = UUID.randomUUID();

    assertThatThrownBy(() -> customer.transitionLifecycleStatus(LifecycleStatus.DORMANT, actorId))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Cannot transition from PROSPECT to DORMANT");
  }

  @Test
  void transitionLifecycleStatus_offboardedCannotGoBack() {
    var customer = new Customer("Test Co", "test@test.com", null, null, null, UUID.randomUUID());
    UUID actorId = UUID.randomUUID();
    customer.transitionLifecycleStatus(LifecycleStatus.ACTIVE, actorId);
    customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDING, actorId);
    customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDED, actorId);

    assertThatThrownBy(() -> customer.transitionLifecycleStatus(LifecycleStatus.PROSPECT, actorId))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void transitionLifecycleStatus_setsOffboardedAtWhenOffboarded() {
    var customer = new Customer("Test Co", "test@test.com", null, null, null, UUID.randomUUID());
    UUID actorId = UUID.randomUUID();
    customer.transitionLifecycleStatus(LifecycleStatus.ACTIVE, actorId);
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

  @Test
  void defaultLifecycleIsProspect() {
    var customer = new Customer("New Co", "new@co.com", null, null, null, UUID.randomUUID());
    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.PROSPECT);
  }

  @Test
  void customerTypeDefaultsToIndividual() {
    var customer = new Customer("New Co", "new@co.com", null, null, null, UUID.randomUUID());
    assertThat(customer.getCustomerType()).isEqualTo(CustomerType.INDIVIDUAL);
  }

  @Test
  void customerTypeConstructorAcceptsEnum() {
    var customer =
        new Customer(
            "Corp Co", "corp@co.com", null, null, null, UUID.randomUUID(), CustomerType.COMPANY);
    assertThat(customer.getCustomerType()).isEqualTo(CustomerType.COMPANY);
  }

  @Test
  void offboardedAtIsNullByDefault() {
    var customer = new Customer("New Co", "new@co.com", null, null, null, UUID.randomUUID());
    assertThat(customer.getOffboardedAt()).isNull();
  }
}
