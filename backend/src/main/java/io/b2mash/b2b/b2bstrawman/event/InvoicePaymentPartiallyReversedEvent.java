package io.b2mash.b2b.b2bstrawman.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when one of multiple recorded payments on an invoice is reversed but at least one other
 * COMPLETED payment event remains. The invoice stays in PAID status (the books still tie out via
 * the surviving payment events) — only the named payment_event row is deleted.
 *
 * <p>Carries enough context (invoice ID, payment event ID, customer ID, project ID, amount) for
 * downstream consumers (matter activity feed, portal /activity page) to render the partial reversal
 * without further DB lookups.
 */
public record InvoicePaymentPartiallyReversedEvent(
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
