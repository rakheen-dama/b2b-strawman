package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event published when a trust transaction requires approval or has been approved/rejected.
 * This is NOT part of the sealed DomainEvent hierarchy — it is a standalone event record used with
 * {@link org.springframework.context.ApplicationEventPublisher} and {@link
 * org.springframework.transaction.event.TransactionalEventListener}.
 *
 * <p>{@code transactionDate} is the transaction's SA business date (the ledger date the client
 * sees); {@code occurredAt} is when the event was published (G89-F39).
 */
public record TrustTransactionApprovalEvent(
    String eventType,
    UUID transactionId,
    UUID trustAccountId,
    UUID projectId,
    String transactionType,
    BigDecimal amount,
    LocalDate transactionDate,
    UUID customerId,
    UUID recordedBy,
    UUID approvedBy,
    UUID rejectedBy,
    String tenantId,
    String orgId,
    Instant occurredAt) {

  public static TrustTransactionApprovalEvent awaitingApproval(
      UUID transactionId,
      UUID trustAccountId,
      UUID projectId,
      String transactionType,
      BigDecimal amount,
      LocalDate transactionDate,
      UUID customerId,
      UUID recordedBy,
      String tenantId,
      String orgId) {
    return new TrustTransactionApprovalEvent(
        "trust_transaction.awaiting_approval",
        transactionId,
        trustAccountId,
        projectId,
        transactionType,
        amount,
        transactionDate,
        customerId,
        recordedBy,
        null,
        null,
        tenantId,
        orgId,
        Instant.now());
  }

  public static TrustTransactionApprovalEvent approved(
      UUID transactionId,
      UUID trustAccountId,
      UUID projectId,
      String transactionType,
      BigDecimal amount,
      LocalDate transactionDate,
      UUID customerId,
      UUID recordedBy,
      UUID approvedBy,
      String tenantId,
      String orgId) {
    return new TrustTransactionApprovalEvent(
        "trust_transaction.approved",
        transactionId,
        trustAccountId,
        projectId,
        transactionType,
        amount,
        transactionDate,
        customerId,
        recordedBy,
        approvedBy,
        null,
        tenantId,
        orgId,
        Instant.now());
  }

  public static TrustTransactionApprovalEvent rejected(
      UUID transactionId,
      UUID trustAccountId,
      UUID projectId,
      String transactionType,
      BigDecimal amount,
      LocalDate transactionDate,
      UUID customerId,
      UUID recordedBy,
      UUID rejectedBy,
      String tenantId,
      String orgId) {
    return new TrustTransactionApprovalEvent(
        "trust_transaction.rejected",
        transactionId,
        trustAccountId,
        projectId,
        transactionType,
        amount,
        transactionDate,
        customerId,
        recordedBy,
        null,
        rejectedBy,
        tenantId,
        orgId,
        Instant.now());
  }
}
