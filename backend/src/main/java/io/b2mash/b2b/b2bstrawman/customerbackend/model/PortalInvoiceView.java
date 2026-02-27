package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PortalInvoiceView(
    UUID id,
    String orgId,
    UUID customerId,
    String invoiceNumber,
    String status,
    LocalDate issueDate,
    LocalDate dueDate,
    BigDecimal subtotal,
    BigDecimal taxAmount,
    BigDecimal total,
    String currency,
    String notes,
    String paymentUrl,
    String paymentSessionId,
    Instant paidAt,
    Instant syncedAt,
    String taxBreakdownJson,
    String taxRegistrationNumber,
    String taxRegistrationLabel,
    String taxLabel,
    boolean taxInclusive,
    boolean hasPerLineTax) {}
