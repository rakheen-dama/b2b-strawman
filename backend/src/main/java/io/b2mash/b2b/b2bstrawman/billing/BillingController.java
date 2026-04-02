package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    var pageable =
        PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "paymentDate"));
    var payments = subscriptionService.getPayments(RequestScopes.requireOrgId(), pageable);
    return ResponseEntity.ok(payments.map(PaymentResponse::from));
  }

  public record PaymentResponse(
      UUID id,
      String payfastPaymentId,
      int amountCents,
      String currency,
      String status,
      Instant paymentDate) {

    public static PaymentResponse from(SubscriptionPayment payment) {
      return new PaymentResponse(
          payment.getId(),
          payment.getPayfastPaymentId(),
          payment.getAmountCents(),
          payment.getCurrency(),
          payment.getStatus().name(),
          payment.getPaymentDate());
    }
  }
}
