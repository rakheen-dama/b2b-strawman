package io.b2mash.b2b.b2bstrawman.integration.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Value object for recording a manual payment via {@link
 * PaymentGateway#recordManualPayment(PaymentRequest)}. Relocated from {@code invoice/} package per
 * ADR-098.
 */
public record PaymentRequest(
    UUID invoiceId, BigDecimal amount, String currency, String description) {}
