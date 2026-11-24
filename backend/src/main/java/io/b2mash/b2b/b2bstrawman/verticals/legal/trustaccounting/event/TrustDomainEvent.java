package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sealed interface for trust accounting domain events beyond transaction approvals. These events
 * are NOT part of the main {@link io.b2mash.b2b.b2bstrawman.event.DomainEvent} hierarchy — they are
 * standalone event types published via {@link
 * org.springframework.context.ApplicationEventPublisher} and consumed by {@link
 * TrustNotificationHandler} using {@link
 * org.springframework.transaction.event.TransactionalEventListener}.
 *
 * <p>Each record carries enough context for the notification handler to act without additional DB
 * queries. The {@code tenantId} and {@code orgId} fields enable tenant-scoped processing.
 */
public sealed interface TrustDomainEvent {

  String tenantId();

  String orgId();

  Instant occurredAt();

  /**
   * Published when a PAYMENT, FEE_TRANSFER, or REFUND enters AWAITING_APPROVAL status. Triggers
   * notification to members with approval capability.
   */
  record PaymentAwaitingApproval(
      UUID transactionId,
      UUID trustAccountId,
      String transactionType,
      BigDecimal amount,
      UUID customerId,
      UUID recordedBy,
      String tenantId,
      String orgId,
      Instant occurredAt)
      implements TrustDomainEvent {

    public static PaymentAwaitingApproval of(
        UUID transactionId,
        UUID trustAccountId,
        String transactionType,
        BigDecimal amount,
        UUID customerId,
        UUID recordedBy,
        String tenantId,
        String orgId) {
      return new PaymentAwaitingApproval(
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

  /**
   * Published when a trust transaction is approved. Triggers notification to the member who
   * recorded the transaction.
   */
  record PaymentApproved(
      UUID transactionId,
      UUID trustAccountId,
      String transactionType,
      BigDecimal amount,
      UUID customerId,
      UUID recordedBy,
      UUID approvedBy,
      String tenantId,
      String orgId,
      Instant occurredAt)
      implements TrustDomainEvent {

    public static PaymentApproved of(
        UUID transactionId,
        UUID trustAccountId,
        String transactionType,
        BigDecimal amount,
        UUID customerId,
        UUID recordedBy,
        UUID approvedBy,
        String tenantId,
        String orgId) {
      return new PaymentApproved(
          transactionId,
          trustAccountId,
          transactionType,
          amount,
          customerId,
          recordedBy,
          approvedBy,
          tenantId,
          orgId,
          Instant.now());
    }
  }

  /**
   * Published when a trust transaction is rejected. Triggers notification to the member who
   * recorded the transaction, including the rejection reason.
   */
  record PaymentRejected(
      UUID transactionId,
      UUID trustAccountId,
      String transactionType,
      BigDecimal amount,
      UUID customerId,
      UUID recordedBy,
      UUID rejectedBy,
      String rejectionReason,
      String tenantId,
      String orgId,
      Instant occurredAt)
      implements TrustDomainEvent {

    public static PaymentRejected of(
        UUID transactionId,
        UUID trustAccountId,
        String transactionType,
        BigDecimal amount,
        UUID customerId,
        UUID recordedBy,
        UUID rejectedBy,
        String rejectionReason,
        String tenantId,
        String orgId) {
      return new PaymentRejected(
          transactionId,
          trustAccountId,
          transactionType,
          amount,
          customerId,
          recordedBy,
          rejectedBy,
          rejectionReason,
          tenantId,
          orgId,
          Instant.now());
    }
  }

  /**
   * Published when a trust account reconciliation is finalized (status = COMPLETED). Triggers
   * notification to admins/owners.
   */
  record ReconciliationCompleted(
      UUID reconciliationId,
      UUID trustAccountId,
      LocalDate periodEnd,
      UUID completedBy,
      String tenantId,
      String orgId,
      Instant occurredAt)
      implements TrustDomainEvent {

    public static ReconciliationCompleted of(
        UUID reconciliationId,
        UUID trustAccountId,
        LocalDate periodEnd,
        UUID completedBy,
        String tenantId,
        String orgId) {
      return new ReconciliationCompleted(
          reconciliationId, trustAccountId, periodEnd, completedBy, tenantId, orgId, Instant.now());
    }
  }

  /**
   * Published when a trust investment is approaching its maturity date. Triggers notification to
   * the member who placed the investment.
   */
  record InvestmentMaturing(
      UUID investmentId,
      UUID trustAccountId,
      UUID customerId,
      String institution,
      BigDecimal principal,
      LocalDate maturityDate,
      int daysUntilMaturity,
      String tenantId,
      String orgId,
      Instant occurredAt)
      implements TrustDomainEvent {

    public static InvestmentMaturing of(
        UUID investmentId,
        UUID trustAccountId,
        UUID customerId,
        String institution,
        BigDecimal principal,
        LocalDate maturityDate,
        int daysUntilMaturity,
        String tenantId,
        String orgId) {
      return new InvestmentMaturing(
          investmentId,
          trustAccountId,
          customerId,
          institution,
          principal,
          maturityDate,
          daysUntilMaturity,
          tenantId,
          orgId,
          Instant.now());
    }
  }

  /**
   * Published once when a trust take-on is committed (Phase 93 657C, §17.3.9, §17.6.6): the batch,
   * every account it took on (the banking account and its investment accounts), its take-on report
   * and the member who confirmed the commit. Its AFTER_COMMIT listener in {@code
   * TrustLedgerPortalSyncService} recomputes the portal once per matter of those accounts. No
   * per-row event and no email accompany a take-on.
   */
  record TakeOnPositionCommitted(
      UUID batchId,
      List<UUID> accountIds,
      UUID reportId,
      UUID committedBy,
      String tenantId,
      String orgId,
      Instant occurredAt)
      implements TrustDomainEvent {

    public TakeOnPositionCommitted {
      accountIds = List.copyOf(accountIds);
    }

    public static TakeOnPositionCommitted of(
        UUID batchId,
        List<UUID> accountIds,
        UUID reportId,
        UUID committedBy,
        String tenantId,
        String orgId) {
      return new TakeOnPositionCommitted(
          batchId, accountIds, reportId, committedBy, tenantId, orgId, Instant.now());
    }
  }
}
