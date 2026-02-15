package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerLifecycleController {

  private final CustomerLifecycleService customerLifecycleService;

  public CustomerLifecycleController(CustomerLifecycleService customerLifecycleService) {
    this.customerLifecycleService = customerLifecycleService;
  }

  @PostMapping("/{id}/transition")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CustomerLifecycleResponse> transitionCustomer(
      @PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
    var customer =
        customerLifecycleService.transitionCustomer(id, request.targetStatus(), request.notes());
    return ResponseEntity.ok(CustomerLifecycleResponse.from(customer));
  }

  @GetMapping("/dormancy-check")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<DormancyCheckResponse> checkDormancy() {
    int thresholdDays = customerLifecycleService.getDormancyThresholdDays();
    var candidates = customerLifecycleService.checkDormancy();
    Instant now = Instant.now();

    var candidateResponses =
        candidates.stream()
            .map(
                c -> {
                  long daysSince = ChronoUnit.DAYS.between(c.getUpdatedAt(), now);
                  return new DormancyCandidate(
                      c.getId(), c.getName(), c.getUpdatedAt(), daysSince, c.getLifecycleStatus());
                })
            .toList();

    return ResponseEntity.ok(new DormancyCheckResponse(thresholdDays, candidateResponses));
  }

  public record TransitionRequest(@NotBlank String targetStatus, @Size(max = 1000) String notes) {}

  public record CustomerLifecycleResponse(
      UUID id,
      String name,
      String email,
      String lifecycleStatus,
      Instant lifecycleStatusChangedAt,
      UUID lifecycleStatusChangedBy,
      Instant offboardedAt) {

    public static CustomerLifecycleResponse from(Customer customer) {
      return new CustomerLifecycleResponse(
          customer.getId(),
          customer.getName(),
          customer.getEmail(),
          customer.getLifecycleStatus(),
          customer.getLifecycleStatusChangedAt(),
          customer.getLifecycleStatusChangedBy(),
          customer.getOffboardedAt());
    }
  }

  public record DormancyCheckResponse(int thresholdDays, List<DormancyCandidate> candidates) {}

  public record DormancyCandidate(
      UUID id, String name, Instant lastActivityAt, long daysSinceActivity, String currentStatus) {}
}
