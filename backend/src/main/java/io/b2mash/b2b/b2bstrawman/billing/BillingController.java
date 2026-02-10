package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.billing.SubscriptionService.BillingResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<BillingResponse> getSubscription() {
    String clerkOrgId = RequestScopes.requireOrgId();
    return ResponseEntity.ok(subscriptionService.getSubscription(clerkOrgId));
  }
}
