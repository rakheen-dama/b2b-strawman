package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a trust transaction is created directly in {@code RECORDED} status —
 * i.e., via the no-approval-threshold paths in {@code TrustTransactionService.recordDeposit} and
 * {@code TrustTransactionService.recordTransfer}. These paths do NOT flow through the
 * awaiting-approval / approved / rejected lifecycle, so {@link TrustTransactionApprovalEvent} is
 * not published for them.
 *
 * <p>The portal trust-ledger read-model listens for this event to sync firm-side deposits/transfers
 * into {@code portal.portal_trust_transaction} + {@code portal.portal_trust_balance}. Without this
 * event the portal {@code /trust} view renders empty even though the firm-side tenant schema has
 * the row.
 *
 * <p>This is NOT part of the sealed DomainEvent hierarchy — it is a standalone event record used
 * with {@link org.springframework.context.ApplicationEventPublisher} and {@link
 * org.springframework.transaction.event.TransactionalEventListener}, mirroring {@link
 * TrustTransactionApprovalEvent}.
 */
public record TrustTransactionRecordedEvent(
    String eventType,
    UUID transactionId,
    UUID trustAccountId,
    String transactionType,
    BigDecimal amount,
    UUID customerId,
    UUID recordedBy,
    String tenantId,
    String orgId,
    Instant occurredAt) {

  /** Factory for the {@code trust_transaction.recorded} event emitted on direct-RECORDED paths. */
  public static TrustTransactionRecordedEvent recorded(
      UUID transactionId,
      UUID trustAccountId,
      String transactionType,
      BigDecimal amount,
      UUID customerId,
      UUID recordedBy,
      String tenantId,
      String orgId) {
    return new TrustTransactionRecordedEvent(
        "trust_transaction.recorded",
        transactionId,
        trustAccountId,
        transactionType,
        amount,
        customerId,
        recordedBy,
        tenantId,
        orgId,
        Instant.now());
  }
}
