package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Stateless validation service that enforces lifecycle action gating and transition validation for
 * customers. Throws {@link ResourceConflictException} (409) when an action or transition is
 * blocked.
 *
 * <p>Action gating table (Section 13.3.3):
 *
 * <ul>
 *   <li>PROSPECT: can only EDIT_CUSTOMER and UPLOAD_DOCUMENT
 *   <li>ONBOARDING: can do everything except CREATE_INVOICE
 *   <li>ACTIVE: no restrictions
 *   <li>DORMANT: no restrictions
 *   <li>OFFBOARDED: all gated actions blocked (read-only)
 * </ul>
 */
@Service
public class CustomerLifecycleGuard {

  /**
   * Actions blocked per lifecycle status. If a status is not in this map, no actions are blocked.
   * If an action is in the set for the current status, it is blocked.
   */
  private static final Map<String, Set<LifecycleAction>> BLOCKED_ACTIONS =
      Map.of(
          "PROSPECT",
              Set.of(
                  LifecycleAction.CREATE_PROJECT,
                  LifecycleAction.CREATE_TASK,
                  LifecycleAction.LOG_TIME,
                  LifecycleAction.CREATE_INVOICE),
          "ONBOARDING", Set.of(LifecycleAction.CREATE_INVOICE),
          "OFFBOARDED",
              Set.of(
                  LifecycleAction.CREATE_PROJECT,
                  LifecycleAction.CREATE_TASK,
                  LifecycleAction.LOG_TIME,
                  LifecycleAction.CREATE_INVOICE,
                  LifecycleAction.UPLOAD_DOCUMENT,
                  LifecycleAction.EDIT_CUSTOMER));

  /**
   * Valid transitions: from-status -> set of valid target statuses. If a from-status is not in this
   * map, no transitions are allowed from it (except those explicitly listed).
   */
  private static final Map<String, Set<String>> VALID_TRANSITIONS =
      Map.of(
          "PROSPECT", Set.of("ONBOARDING"),
          "ONBOARDING", Set.of("ACTIVE"),
          "ACTIVE", Set.of("DORMANT", "OFFBOARDED"),
          "DORMANT", Set.of("ACTIVE", "OFFBOARDED"),
          "OFFBOARDED", Set.of("ACTIVE"));

  /**
   * Checks whether the given action is permitted for a customer's current lifecycle status. Throws
   * {@link ResourceConflictException} (409) if the action is blocked.
   */
  public void requireActionPermitted(Customer customer, LifecycleAction action) {
    String status = customer.getLifecycleStatus();
    Set<LifecycleAction> blocked = BLOCKED_ACTIONS.getOrDefault(status, Set.of());
    if (blocked.contains(action)) {
      throw new ResourceConflictException(
          "Action not permitted",
          "Action '%s' is not permitted for customers in '%s' status"
              .formatted(action.name(), status));
    }
  }

  /**
   * Validates that a lifecycle transition is valid from the customer's current status to the target
   * status. Throws {@link ResourceConflictException} (409) for invalid transitions or missing
   * required notes.
   *
   * @param customer the customer being transitioned
   * @param targetStatus the desired target status
   * @param notes optional notes (required for OFFBOARDED -> ACTIVE reactivation)
   */
  public void requireTransitionValid(Customer customer, String targetStatus, String notes) {
    String currentStatus = customer.getLifecycleStatus();

    // Check transition is in the valid transitions map
    Set<String> validTargets = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
    if (!validTargets.contains(targetStatus)) {
      throw new ResourceConflictException(
          "Invalid transition",
          "Transition from '%s' to '%s' is not allowed".formatted(currentStatus, targetStatus));
    }

    // Check notes required: OFFBOARDED -> ACTIVE requires justification
    if ("OFFBOARDED".equals(currentStatus)
        && "ACTIVE".equals(targetStatus)
        && (notes == null || notes.isBlank())) {
      throw new ResourceConflictException(
          "Notes required",
          "Reactivating an offboarded customer requires notes with audit justification");
    }
  }
}
