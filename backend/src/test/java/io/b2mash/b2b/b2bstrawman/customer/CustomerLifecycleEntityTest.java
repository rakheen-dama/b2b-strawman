package io.b2mash.b2b.b2bstrawman.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerLifecycleEntityTest {

  @Test
  void transitionLifecycleStatus_setsAllFields() {
    var customer = new Customer("Test Co", "test@test.com", null, null, null, UUID.randomUUID());
    UUID actorId = UUID.randomUUID();

    customer.transitionLifecycleStatus("ONBOARDING", actorId);

    assertThat(customer.getLifecycleStatus()).isEqualTo("ONBOARDING");
    assertThat(customer.getLifecycleStatusChangedAt()).isNotNull();
    assertThat(customer.getLifecycleStatusChangedBy()).isEqualTo(actorId);
  }

  @Test
  void anonymize_replacesPII() {
    var customer =
        new Customer("Jane Doe", "jane@example.com", "+27821234567", null, null, UUID.randomUUID());

    customer.anonymize("Anonymized Customer abc123");

    assertThat(customer.getName()).isEqualTo("Anonymized Customer abc123");
    assertThat(customer.getEmail()).doesNotContain("jane");
    assertThat(customer.getEmail()).endsWith("@anonymized.invalid");
    assertThat(customer.getPhone()).isNull();
  }

  @Test
  void defaultLifecycleIsProspect() {
    var customer = new Customer("New Co", "new@co.com", null, null, null, UUID.randomUUID());
    assertThat(customer.getLifecycleStatus()).isEqualTo("PROSPECT");
  }

  @Test
  void customerTypeDefaultsToIndividual() {
    var customer = new Customer("New Co", "new@co.com", null, null, null, UUID.randomUUID());
    assertThat(customer.getCustomerType()).isEqualTo("INDIVIDUAL");
  }

  @Test
  void offboardedAtIsNullByDefault() {
    var customer = new Customer("New Co", "new@co.com", null, null, null, UUID.randomUUID());
    assertThat(customer.getOffboardedAt()).isNull();
  }
}
