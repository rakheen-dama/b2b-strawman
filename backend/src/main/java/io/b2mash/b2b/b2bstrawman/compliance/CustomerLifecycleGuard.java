package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import org.springframework.stereotype.Component;

@Component
public class CustomerLifecycleGuard {

  /**
   * Checks whether the given action is permitted for the customer's current lifecycle status.
   * Throws InvalidStateException if the action is blocked.
   */
  public void requireActionPermitted(Customer customer, LifecycleAction action) {
    var status = customer.getLifecycleStatus();

    switch (action) {
      case CREATE_PROJECT, CREATE_TASK -> {
        if (status == LifecycleStatus.PROSPECT
            || status == LifecycleStatus.OFFBOARDING
            || status == LifecycleStatus.OFFBOARDED) {
          throwBlocked(action, status);
        }
      }
      case UPDATE_CUSTOM_FIELDS -> {
        // GAP-L-35: saving custom fields on an already-linked matter is a
        // pure metadata write and must not be re-gated by the CREATE_PROJECT
        // rule that blocks PROSPECT / OFFBOARDING. The customer-project link
        // existed prior to this request; re-validating it as if it were a
        // fresh engagement blocks routine UI flows (the "Save Custom Fields"
        // button on matter detail re-PUTs the existing customerId). Mirrors
        // L-56 / L-60: only OFFBOARDED (terminal) blocks — once a customer is
        // fully off-boarded their matters are read-only.
        if (status == LifecycleStatus.OFFBOARDED) {
          throwBlocked(action, status);
        }
      }
      case CREATE_TIME_ENTRY -> {
        // Time entries are record-keeping on work already performed. They are
        // permitted against PROSPECT and OFFBOARDING customers (e.g. consultation
        // hours logged before client-activation, or final billing hours after
        // lifecycle close initiated). Only OFFBOARDED (terminal) is blocked —
        // after a customer is fully off-boarded, time tracking is closed.
        if (status == LifecycleStatus.OFFBOARDED) {
          throwBlocked(action, status);
        }
      }
      case CREATE_INVOICE -> {
        // GAP-L-60: invoice / fee note generation is a billing record-keeping
        // operation that fires across the entire engagement. Billable time and
        // disbursements accumulate on PROSPECT customers (initial consultation,
        // sheriff-fee deposits paid before formal activation) and must be
        // bill-able immediately. ONBOARDING and OFFBOARDING must also permit
        // billing (engagement in progress / close-out in progress). Only
        // OFFBOARDED (terminal) blocks — after a customer is fully off-boarded,
        // billing is closed and final invoices have already been issued.
        if (status == LifecycleStatus.OFFBOARDED) {
          throwBlocked(action, status);
        }
      }
      case CREATE_DOCUMENT -> {
        if (status == LifecycleStatus.OFFBOARDED) {
          throwBlocked(action, status);
        }
      }
      case CREATE_COMMENT -> {
        // Always allowed
      }
    }
  }

  private void throwBlocked(LifecycleAction action, LifecycleStatus status) {
    throw new InvalidStateException(
        "Lifecycle action blocked",
        "Cannot create " + action.label() + " for customer in " + status + " lifecycle status");
  }
}
