package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
   * Published when an interest run is posted (status = POSTED). Triggers notification to
   * admins/owners with the total interest and client share amounts.
   */
  record InterestPosted(
      UUID interestRunId,
      UUID trustAccountId,
      LocalDate periodStart,
      LocalDate periodEnd,
      BigDecimal totalInterest,
      BigDecimal totalClientShare,
      UUID approvedBy,
      String tenantId,
      String orgId,
      Instant occurredAt)
      implements TrustDomainEvent {

    public static InterestPosted of(
        UUID interestRunId,
        UUID trustAccountId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalInterest,
        BigDecimal totalClientShare,
        UUID approvedBy,
        String tenantId,
        String orgId) {
      return new InterestPosted(
          interestRunId,
          trustAccountId,
          periodStart,
          periodEnd,
          totalInterest,
          totalClientShare,
          approvedBy,
          tenantId,
          orgId,
          Instant.now());
    }
  }
}
