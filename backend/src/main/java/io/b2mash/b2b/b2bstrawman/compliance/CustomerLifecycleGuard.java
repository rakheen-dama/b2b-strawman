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
        if (status != LifecycleStatus.ACTIVE && status != LifecycleStatus.DORMANT) {
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
