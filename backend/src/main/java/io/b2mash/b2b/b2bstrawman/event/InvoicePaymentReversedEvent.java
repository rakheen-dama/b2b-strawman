package io.b2mash.b2b.b2bstrawman.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when a previously-recorded invoice payment is fully reversed and the invoice flips back
 * from PAID to SENT. Emitted by {@code InvoiceTransitionService.reversePayment} when no other
 * COMPLETED payment events remain on the invoice.
 *
 * <p>Carries enough context (invoice ID, payment event ID, customer ID, project ID, amount) for
 * downstream consumers (matter activity feed, portal /activity page) to render the reversal without
 * further DB lookups.
 */
public record InvoicePaymentReversedEvent(
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
    UUID customerId,
    UUID paymentEventId,
    BigDecimal amount)
    implements DomainEvent {}
