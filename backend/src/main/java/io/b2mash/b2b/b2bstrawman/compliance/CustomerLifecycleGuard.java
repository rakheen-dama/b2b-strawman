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
      case CREATE_PROJECT, CREATE_TASK, CREATE_TIME_ENTRY -> {
        if (status == LifecycleStatus.PROSPECT || status == LifecycleStatus.OFFBOARDED) {
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
