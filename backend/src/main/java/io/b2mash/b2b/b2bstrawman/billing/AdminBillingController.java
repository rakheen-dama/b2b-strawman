package io.b2mash.b2b.b2bstrawman.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/billing")
public class AdminBillingController {

  private static final Logger log = LoggerFactory.getLogger(AdminBillingController.class);

  private final SubscriptionService subscriptionService;

  public AdminBillingController(SubscriptionService subscriptionService) {
    this.subscriptionService = subscriptionService;
  }

  @PostMapping("/set-plan")
  public ResponseEntity<Void> setPlan(@Valid @RequestBody SetPlanRequest request) {
    log.info(
        "Received set-plan: clerkOrgId={}, planSlug={} (no-op — Tier model removed)",
        request.clerkOrgId(),
        request.planSlug());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/extend-trial")
  public ResponseEntity<BillingResponse> extendTrial(
      @Valid @RequestBody ExtendTrialRequest request) {
    return ResponseEntity.ok(
        subscriptionService.extendTrial(request.organizationId(), request.additionalDays()));
  }

  @PostMapping("/activate")
  public ResponseEntity<BillingResponse> activate(@Valid @RequestBody ActivateRequest request) {
    return ResponseEntity.ok(subscriptionService.activate(request.organizationId()));
  }

  public record SetPlanRequest(
      @NotBlank(message = "clerkOrgId is required") String clerkOrgId,
      @NotBlank(message = "planSlug is required") String planSlug) {}

  public record ExtendTrialRequest(
      @NotNull(message = "organizationId is required") UUID organizationId,
      @Positive(message = "additionalDays must be positive") int additionalDays) {}

  public record ActivateRequest(
      @NotNull(message = "organizationId is required") UUID organizationId) {}
}
