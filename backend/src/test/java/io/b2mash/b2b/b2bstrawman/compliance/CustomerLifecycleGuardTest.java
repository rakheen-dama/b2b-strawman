package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerLifecycleGuardTest {

  private final CustomerLifecycleGuard guard = new CustomerLifecycleGuard();

  @Test
  void createInvoiceBlockedForProspect() {
    var customer = createCustomerWithStatus(LifecycleStatus.PROSPECT);
    assertThatThrownBy(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void createInvoiceBlockedForOnboarding() {
    var customer = createCustomerWithStatus(LifecycleStatus.ONBOARDING);
    assertThatThrownBy(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void createInvoiceBlockedForOffboarded() {
    var customer = createCustomerWithStatus(LifecycleStatus.OFFBOARDED);
    assertThatThrownBy(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void createInvoiceAllowedForActiveAndDormant() {
    var activeCustomer = createCustomerWithStatus(LifecycleStatus.ACTIVE);
    assertThatCode(
            () -> guard.requireActionPermitted(activeCustomer, LifecycleAction.CREATE_INVOICE))
        .doesNotThrowAnyException();

    var dormantCustomer = createCustomerWithStatus(LifecycleStatus.DORMANT);
    assertThatCode(
            () -> guard.requireActionPermitted(dormantCustomer, LifecycleAction.CREATE_INVOICE))
        .doesNotThrowAnyException();
  }

  @Test
  void createCommentAlwaysAllowed() {
    for (LifecycleStatus status : LifecycleStatus.values()) {
      var customer = createCustomerWithStatus(status);
      assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_COMMENT))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void createProjectBlockedForOffboarding() {
    var customer = createCustomerWithStatus(LifecycleStatus.OFFBOARDING);
    assertThatThrownBy(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_PROJECT))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void createProjectAllowedForActive() {
    var customer = createCustomerWithStatus(LifecycleStatus.ACTIVE);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_PROJECT))
        .doesNotThrowAnyException();
  }

  @Test
  void createProjectAllowedForOnboarding() {
    var customer = createCustomerWithStatus(LifecycleStatus.ONBOARDING);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_PROJECT))
        .doesNotThrowAnyException();
  }

  @Test
  void createProjectAllowedForDormant() {
    var customer = createCustomerWithStatus(LifecycleStatus.DORMANT);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_PROJECT))
        .doesNotThrowAnyException();
  }

  private Customer createCustomerWithStatus(LifecycleStatus status) {
    // Use the constructor that accepts explicit lifecycle status
    var customer =
        new Customer(
            "Test Corp", "test@test.com", null, null, null, UUID.randomUUID(), null, status);
    return customer;
  }
}
