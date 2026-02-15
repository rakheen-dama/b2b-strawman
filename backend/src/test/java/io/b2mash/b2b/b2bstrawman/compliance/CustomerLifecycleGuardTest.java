package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import org.junit.jupiter.api.Test;

class CustomerLifecycleGuardTest {

  private final CustomerLifecycleGuard guard = new CustomerLifecycleGuard();

  // --- Action Gating Tests ---

  @Test
  void prospectCannotCreateProject() {
    var customer = customerWithStatus("PROSPECT");
    assertThatThrownBy(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_PROJECT))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void activeCanCreateInvoice() {
    var customer = customerWithStatus("ACTIVE");
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE))
        .doesNotThrowAnyException();
  }

  @Test
  void onboardingCannotCreateInvoice() {
    var customer = customerWithStatus("ONBOARDING");
    assertThatThrownBy(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void offboardedCannotEditCustomer() {
    var customer = customerWithStatus("OFFBOARDED");
    assertThatThrownBy(() -> guard.requireActionPermitted(customer, LifecycleAction.EDIT_CUSTOMER))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void dormantHasNoRestrictions() {
    var customer = customerWithStatus("DORMANT");
    for (LifecycleAction action : LifecycleAction.values()) {
      assertThatCode(() -> guard.requireActionPermitted(customer, action))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void prospectCanUploadDocument() {
    var customer = customerWithStatus("PROSPECT");
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.UPLOAD_DOCUMENT))
        .doesNotThrowAnyException();
  }

  @Test
  void offboardedCannotUploadDocument() {
    var customer = customerWithStatus("OFFBOARDED");
    assertThatThrownBy(
            () -> guard.requireActionPermitted(customer, LifecycleAction.UPLOAD_DOCUMENT))
        .isInstanceOf(ResourceConflictException.class);
  }

  // --- Transition Validation Tests ---

  @Test
  void prospectToOnboardingIsValid() {
    var customer = customerWithStatus("PROSPECT");
    assertThatCode(() -> guard.requireTransitionValid(customer, "ONBOARDING", null))
        .doesNotThrowAnyException();
  }

  @Test
  void prospectToActiveIsInvalid() {
    var customer = customerWithStatus("PROSPECT");
    assertThatThrownBy(() -> guard.requireTransitionValid(customer, "ACTIVE", null))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void onboardingToActiveIsValid() {
    var customer = customerWithStatus("ONBOARDING");
    assertThatCode(() -> guard.requireTransitionValid(customer, "ACTIVE", null))
        .doesNotThrowAnyException();
  }

  @Test
  void offboardedToActiveIsValidWithNotes() {
    var customer = customerWithStatus("OFFBOARDED");
    assertThatCode(
            () -> guard.requireTransitionValid(customer, "ACTIVE", "Reactivation justification"))
        .doesNotThrowAnyException();
  }

  @Test
  void offboardedToActiveIsInvalidWithoutNotes() {
    var customer = customerWithStatus("OFFBOARDED");
    assertThatThrownBy(() -> guard.requireTransitionValid(customer, "ACTIVE", null))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void offboardedToActiveIsInvalidWithBlankNotes() {
    var customer = customerWithStatus("OFFBOARDED");
    assertThatThrownBy(() -> guard.requireTransitionValid(customer, "ACTIVE", "  "))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void prospectToDormantIsInvalid() {
    var customer = customerWithStatus("PROSPECT");
    assertThatThrownBy(() -> guard.requireTransitionValid(customer, "DORMANT", null))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void activeToDormantIsValid() {
    var customer = customerWithStatus("ACTIVE");
    assertThatCode(() -> guard.requireTransitionValid(customer, "DORMANT", null))
        .doesNotThrowAnyException();
  }

  // --- Helper ---

  private Customer customerWithStatus(String lifecycleStatus) {
    var customer = mock(Customer.class);
    when(customer.getLifecycleStatus()).thenReturn(lifecycleStatus);
    return customer;
  }
}
