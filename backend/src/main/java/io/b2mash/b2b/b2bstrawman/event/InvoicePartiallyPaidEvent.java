package io.b2mash.b2b.b2bstrawman.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when a payment leaves a fee note with a balance still due (Phase 88 U15, ADR-347) — the
 * PARTIALLY_PAID sibling of {@link InvoicePaidEvent}. No listener is required in 622B.
 */
public record InvoicePartiallyPaidEvent(
    String eventType,
    String entityType,
    UUID entityId,
    UUID projectId,
    UUID actorMemberId,
    String actorName,
    String tenantId,
    String orgId,
    Instant occurredAt,
    Map<String, Object> details,
    UUID createdByMemberId,
    String invoiceNumber,
    String customerName,
    String paymentReference,
    BigDecimal amount,
    BigDecimal amountPaid,
    BigDecimal balanceDue)
    implements DomainEvent {}
