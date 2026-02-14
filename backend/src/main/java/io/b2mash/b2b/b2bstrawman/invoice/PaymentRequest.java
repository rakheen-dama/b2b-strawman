package io.b2mash.b2b.b2bstrawman.invoice;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(
    UUID invoiceId, BigDecimal amount, String currency, String description) {}
