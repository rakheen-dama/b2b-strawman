package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/customers")
public class ProjectCustomerController {

  private final CustomerProjectService customerProjectService;

  public ProjectCustomerController(CustomerProjectService customerProjectService) {
    this.customerProjectService = customerProjectService;
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
