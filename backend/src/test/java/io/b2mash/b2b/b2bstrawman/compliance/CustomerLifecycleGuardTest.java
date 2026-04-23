package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerLifecycleGuardTest {

  private final CustomerLifecycleGuard guard = new CustomerLifecycleGuard();

  @Test
  void createInvoiceAllowedForProspect() {
    // GAP-L-60: invoices must be permitted on PROSPECT customers so
    // consultation hours and up-front disbursements can be billed before
    // formal activation.
    var customer = createCustomerWithStatus(LifecycleStatus.PROSPECT);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE))
        .doesNotThrowAnyException();
  }

  @Test
  void createInvoiceAllowedForOnboarding() {
    // GAP-L-60: engagement is in progress — billing must be permitted.
    var customer = createCustomerWithStatus(LifecycleStatus.ONBOARDING);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE))
        .doesNotThrowAnyException();
  }

  @Test
  void createInvoiceAllowedForOffboarding() {
    // GAP-L-60: final bill must be issuable while close-out is in progress.
    var customer = createCustomerWithStatus(LifecycleStatus.OFFBOARDING);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE))
        .doesNotThrowAnyException();
  }

  @Test
  void createInvoiceBlockedForOffboarded() {
    // GAP-L-60: terminal state — billing is closed once off-boarding completes.
    var customer = createCustomerWithStatus(LifecycleStatus.OFFBOARDED);
    assertThatThrownBy(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE))
        .isInstanceOf(InvalidStateException.class)
        // GAP-L-60 copy fix: error uses vertical-neutral "bill" (not "invoice")
        // so legal-za tenants see a label that matches their fee-note UI.
        .hasMessageContaining("bill")
        .hasMessageNotContaining("invoice");
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
  void createTaskBlockedForOffboarding() {
    var customer = createCustomerWithStatus(LifecycleStatus.OFFBOARDING);
    assertThatThrownBy(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_TASK))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void createTimeEntryAllowedForProspect() {
    // GAP-L-56: time entries are record-keeping and must be permitted on PROSPECT
    // customers (e.g. consultation hours logged before client activation).
    var customer = createCustomerWithStatus(LifecycleStatus.PROSPECT);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_TIME_ENTRY))
        .doesNotThrowAnyException();
  }

  @Test
  void createTimeEntryAllowedForActive() {
    var customer = createCustomerWithStatus(LifecycleStatus.ACTIVE);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_TIME_ENTRY))
        .doesNotThrowAnyException();
  }

  @Test
  void createTimeEntryAllowedForOnboarding() {
    var customer = createCustomerWithStatus(LifecycleStatus.ONBOARDING);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_TIME_ENTRY))
        .doesNotThrowAnyException();
  }

  @Test
  void createTimeEntryAllowedForDormant() {
    var customer = createCustomerWithStatus(LifecycleStatus.DORMANT);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_TIME_ENTRY))
        .doesNotThrowAnyException();
  }

  @Test
  void createTimeEntryAllowedForOffboarding() {
    // GAP-L-56: final billing hours can still be logged while close-out is in progress.
    var customer = createCustomerWithStatus(LifecycleStatus.OFFBOARDING);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_TIME_ENTRY))
        .doesNotThrowAnyException();
  }

  @Test
  void createTimeEntryBlockedForOffboarded() {
    // GAP-L-56: terminal state — time tracking is closed once off-boarding completes.
    var customer = createCustomerWithStatus(LifecycleStatus.OFFBOARDED);
    assertThatThrownBy(
            () -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_TIME_ENTRY))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void updateProjectAllowedForProspect() {
    // GAP-L-35: project updates (e.g. Save Custom Fields on a matter) must
    // succeed against a PROSPECT customer — the matter was already created
    // and routine edits must not be re-gated by the CREATE_PROJECT rule.
    var customer = createCustomerWithStatus(LifecycleStatus.PROSPECT);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.UPDATE_PROJECT))
        .doesNotThrowAnyException();
  }

  @Test
  void updateProjectAllowedForOnboarding() {
    var customer = createCustomerWithStatus(LifecycleStatus.ONBOARDING);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.UPDATE_PROJECT))
        .doesNotThrowAnyException();
  }

  @Test
  void updateProjectAllowedForActive() {
    var customer = createCustomerWithStatus(LifecycleStatus.ACTIVE);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.UPDATE_PROJECT))
        .doesNotThrowAnyException();
  }

  @Test
  void updateProjectAllowedForDormant() {
    var customer = createCustomerWithStatus(LifecycleStatus.DORMANT);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.UPDATE_PROJECT))
        .doesNotThrowAnyException();
  }

  @Test
  void updateProjectAllowedForOffboarding() {
    // GAP-L-35: close-out is in progress — routine edits (final-bill notes,
    // due-date tweaks) must still be permitted.
    var customer = createCustomerWithStatus(LifecycleStatus.OFFBOARDING);
    assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.UPDATE_PROJECT))
        .doesNotThrowAnyException();
  }

  @Test
  void updateProjectBlockedForOffboarded() {
    // GAP-L-35: terminal state — once a customer is fully off-boarded their
    // matters are read-only.
    var customer = createCustomerWithStatus(LifecycleStatus.OFFBOARDED);
    assertThatThrownBy(() -> guard.requireActionPermitted(customer, LifecycleAction.UPDATE_PROJECT))
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
    return TestCustomerFactory.createCustomerWithStatus(
        "Test Corp", "test@test.com", UUID.randomUUID(), status);
  }
}
