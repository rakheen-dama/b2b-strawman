package io.b2mash.b2b.b2bstrawman.integration.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Value object for creating a checkout session with a PSP. The {@code metadata} map must include
 * {@code "tenantSchema"} for webhook-based tenant identification (see ADR-099).
 */
public record CheckoutRequest(
    UUID invoiceId,
    String invoiceNumber,
    BigDecimal amount,
    String currency,
    String customerEmail,
    String customerName,
    String successUrl,
    String cancelUrl,
    Map<String, String> metadata) {}
