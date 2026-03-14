package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.billing.SubscriptionService.BillingResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

  private final SubscriptionService subscriptionService;

  public BillingController(SubscriptionService subscriptionService) {
    this.subscriptionService = subscriptionService;
  }

  @GetMapping("/subscription")
  public ResponseEntity<BillingResponse> getSubscription() {
    String clerkOrgId = RequestScopes.requireOrgId();
    return ResponseEntity.ok(subscriptionService.getSubscription(clerkOrgId));
  }

  @PostMapping("/upgrade")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<BillingResponse> upgrade(@Valid @RequestBody UpgradeRequest request) {
    String clerkOrgId = RequestScopes.requireOrgId();
    return ResponseEntity.ok(subscriptionService.upgradePlan(clerkOrgId, request.planSlug()));
  }

  public record UpgradeRequest(@NotBlank(message = "planSlug is required") String planSlug) {}
}
