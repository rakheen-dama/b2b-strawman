package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<CustomerResponse>> listCustomers() {
    var customers = customerService.listCustomers().stream().map(CustomerResponse::from).toList();
    return ResponseEntity.ok(customers);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CustomerResponse> getCustomer(@PathVariable UUID id) {
    var customer = customerService.getCustomer(id);
    return ResponseEntity.ok(CustomerResponse.from(customer));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CustomerResponse> createCustomer(
      @Valid @RequestBody CreateCustomerRequest request) {
    UUID createdBy = RequestScopes.requireMemberId();
    var customer =
        customerService.createCustomer(
            request.name(),
            request.email(),
            request.phone(),
            request.idNumber(),
            request.notes(),
            createdBy);
    return ResponseEntity.created(URI.create("/api/customers/" + customer.getId()))
        .body(CustomerResponse.from(customer));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CustomerResponse> updateCustomer(
      @PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
    var customer =
        customerService.updateCustomer(
            id,
            request.name(),
            request.email(),
            request.phone(),
            request.idNumber(),
            request.notes());
    return ResponseEntity.ok(CustomerResponse.from(customer));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CustomerResponse> archiveCustomer(@PathVariable UUID id) {
    var customer = customerService.archiveCustomer(id);
    return ResponseEntity.ok(CustomerResponse.from(customer));
  }

  public record CreateCustomerRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @NotBlank(message = "email is required")
          @Email(message = "email must be a valid email address")
          @Size(max = 255, message = "email must be at most 255 characters")
          String email,
      @Size(max = 50, message = "phone must be at most 50 characters") String phone,
      @Size(max = 100, message = "idNumber must be at most 100 characters") String idNumber,
      String notes) {}

  public record UpdateCustomerRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @NotBlank(message = "email is required")
          @Email(message = "email must be a valid email address")
          @Size(max = 255, message = "email must be at most 255 characters")
          String email,
      @Size(max = 50, message = "phone must be at most 50 characters") String phone,
      @Size(max = 100, message = "idNumber must be at most 100 characters") String idNumber,
      String notes) {}

  public record CustomerResponse(
      UUID id,
      String name,
      String email,
      String phone,
      String idNumber,
      String status,
      String notes,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt) {

    public static CustomerResponse from(Customer customer) {
      return new CustomerResponse(
          customer.getId(),
          customer.getName(),
          customer.getEmail(),
          customer.getPhone(),
          customer.getIdNumber(),
          customer.getStatus(),
          customer.getNotes(),
          customer.getCreatedBy(),
          customer.getCreatedAt(),
          customer.getUpdatedAt());
    }
  }
}
