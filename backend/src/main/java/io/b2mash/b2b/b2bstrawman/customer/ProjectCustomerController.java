package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleGuard;
import io.b2mash.b2b.b2bstrawman.compliance.LifecycleAction;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/customers")
public class ProjectCustomerController {

  private final CustomerProjectService customerProjectService;
  private final CustomerRepository customerRepository;
  private final CustomerLifecycleGuard customerLifecycleGuard;

  public ProjectCustomerController(
      CustomerProjectService customerProjectService,
      CustomerRepository customerRepository,
      CustomerLifecycleGuard customerLifecycleGuard) {
    this.customerProjectService = customerProjectService;
    this.customerRepository = customerRepository;
    this.customerLifecycleGuard = customerLifecycleGuard;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<LinkedCustomerResponse>> listCustomersForProject(
      @PathVariable UUID projectId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var customers = customerProjectService.listCustomersForProject(projectId, memberId, orgRole);
    return ResponseEntity.ok(customers.stream().map(LinkedCustomerResponse::from).toList());
  }

  @PostMapping("/{customerId}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CustomerController.CustomerProjectResponse> linkCustomerToProject(
      @PathVariable UUID projectId, @PathVariable UUID customerId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    // Lifecycle guard: block linking project to customer in restricted statuses
    var customer =
        customerRepository
            .findOneById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    customerLifecycleGuard.requireActionPermitted(customer, LifecycleAction.CREATE_PROJECT);

    var link =
        customerProjectService.linkCustomerToProject(
            customerId, projectId, memberId, memberId, orgRole);
    return ResponseEntity.created(
            URI.create("/api/projects/" + projectId + "/customers/" + customerId))
        .body(CustomerController.CustomerProjectResponse.from(link));
  }

  @DeleteMapping("/{customerId}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> unlinkCustomerFromProject(
      @PathVariable UUID projectId, @PathVariable UUID customerId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    customerProjectService.unlinkCustomerFromProject(customerId, projectId, memberId, orgRole);
    return ResponseEntity.noContent().build();
  }

  public record LinkedCustomerResponse(
      UUID id, String name, String email, String phone, String status, Instant createdAt) {

    public static LinkedCustomerResponse from(Customer customer) {
      return new LinkedCustomerResponse(
          customer.getId(),
          customer.getName(),
          customer.getEmail(),
          customer.getPhone(),
          customer.getStatus(),
          customer.getCreatedAt());
    }
  }
}
