package io.b2mash.b2b.b2bstrawman.integration.accounting;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a payment event pulled from an external accounting system. Status values are strings
 * because they originate from the external system: "PAID", "PARTIALLY_PAID", "VOIDED".
 */
public record ExternalPaymentEvent(
    String externalInvoiceReference,
    String externalPaymentId,
    BigDecimal amount,
    String currency,
    Instant paidAt,
    String status) {}
