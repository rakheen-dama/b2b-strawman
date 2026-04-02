package io.b2mash.b2b.b2bstrawman.billing;

import java.time.Instant;
import java.util.UUID;

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
