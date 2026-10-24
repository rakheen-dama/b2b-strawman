package io.b2mash.b2b.b2bstrawman.invoice.dto;

import io.b2mash.b2b.b2bstrawman.invoice.PaymentEvent;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentEventResponse(
    UUID id,
    String providerSlug,
    String sessionId,
    String paymentReference,
    PaymentEventStatus status,
    BigDecimal amount,
    String currency,
    String paymentDestination,
    /** Phase 88 U15: NULL for events recorded before the payment_method column existed. */
    PaymentMethod paymentMethod,
    Instant createdAt,
    Instant updatedAt) {

  public static PaymentEventResponse from(PaymentEvent event) {
    return new PaymentEventResponse(
        event.getId(),
        event.getProviderSlug(),
        event.getSessionId(),
        event.getPaymentReference(),
        event.getStatus(),
        event.getAmount(),
        event.getCurrency(),
        event.getPaymentDestination(),
        event.getPaymentMethod(),
        event.getCreatedAt(),
        event.getUpdatedAt());
  }
}
