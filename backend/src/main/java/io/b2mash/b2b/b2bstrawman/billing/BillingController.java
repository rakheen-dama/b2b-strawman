package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    return ResponseEntity.ok(subscriptionService.getSubscription(RequestScopes.requireOrgId()));
  }

  @PostMapping("/subscribe")
  @RequiresCapability("INVOICING")
  public ResponseEntity<SubscribeResponse> subscribe() {
    return ResponseEntity.ok(subscriptionService.initiateSubscribe(RequestScopes.requireOrgId()));
  }

  @PostMapping("/cancel")
  @RequiresCapability("INVOICING")
  public ResponseEntity<BillingResponse> cancel() {
    return ResponseEntity.ok(subscriptionService.cancelSubscription(RequestScopes.requireOrgId()));
  }

  @GetMapping("/payments")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<Page<PaymentResponse>> getPayments(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    return ResponseEntity.ok(
        subscriptionService.getPayments(RequestScopes.requireOrgId(), page, size));
  }
}
