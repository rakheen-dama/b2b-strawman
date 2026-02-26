package io.b2mash.b2b.b2bstrawman.integration.payment;

/**
 * Result of a {@link PaymentGateway#recordManualPayment(PaymentRequest)} call. Relocated from
 * {@code invoice/} package per ADR-098.
 */
public record PaymentResult(boolean success, String paymentReference, String errorMessage) {}
