package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleGuard;
import io.b2mash.b2b.b2bstrawman.compliance.LifecycleAction;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectLifecycleGuard;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Validates project lifecycle and customer lifecycle eligibility before time entry creation.
 * Extracted from TimeEntryService to reduce constructor bloat.
 */
@Service
class TimeEntryValidationService {

  private final ProjectLifecycleGuard projectLifecycleGuard;
  private final CustomerProjectRepository customerProjectRepository;
  private final CustomerRepository customerRepository;
  private final CustomerLifecycleGuard customerLifecycleGuard;

  TimeEntryValidationService(
      ProjectLifecycleGuard projectLifecycleGuard,
      CustomerProjectRepository customerProjectRepository,
      CustomerRepository customerRepository,
      CustomerLifecycleGuard customerLifecycleGuard) {
    this.projectLifecycleGuard = projectLifecycleGuard;
    this.customerProjectRepository = customerProjectRepository;
    this.customerRepository = customerRepository;
    this.customerLifecycleGuard = customerLifecycleGuard;
  }

  /**
   * Validates that the project is not archived and that any linked customer permits time entry
   * creation.
   */
  void validateProjectAndCustomer(UUID projectId) {
    // Check project is not archived
    projectLifecycleGuard.requireNotReadOnly(projectId);

    // Check lifecycle guard if project is linked to a customer
    customerProjectRepository
        .findFirstCustomerByProjectId(projectId)
        .ifPresent(
            custId ->
                customerRepository
                    .findById(custId)
                    .ifPresent(
                        customer ->
                            customerLifecycleGuard.requireActionPermitted(
                                customer, LifecycleAction.CREATE_TIME_ENTRY)));
  }
}
