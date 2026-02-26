package io.b2mash.b2b.b2bstrawman.integration.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Value object for recording a manual (out-of-band) payment against an invoice via {@link
 * PaymentGateway#recordManualPayment(PaymentRequest)}.
 */
public record PaymentRequest(
    UUID invoiceId, BigDecimal amount, String currency, String description) {}
