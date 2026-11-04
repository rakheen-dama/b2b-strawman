package io.b2mash.b2b.b2bstrawman.customerbackend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One COMPLETED payment on a portal fee note (Phase 89 632B, ADR-364, §14.2.6). {@code date} is the
 * SA business date of the payment ({@code created_at} via {@code KaziTime.ZONE}); {@code
 * methodLabel} is {@code "Payment"} for events recorded before the method column existed; {@code
 * receiptAvailable} is true once the receipt document exists (the download generates it lazily
 * otherwise).
 */
public record PortalInvoicePayment(
    UUID paymentEventId,
    LocalDate date,
    String methodLabel,
    BigDecimal amount,
    String reference,
    boolean receiptAvailable) {}
