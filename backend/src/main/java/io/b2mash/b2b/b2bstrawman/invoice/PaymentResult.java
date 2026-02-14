package io.b2mash.b2b.b2bstrawman.invoice;

public record PaymentResult(boolean success, String paymentReference, String errorMessage) {}
