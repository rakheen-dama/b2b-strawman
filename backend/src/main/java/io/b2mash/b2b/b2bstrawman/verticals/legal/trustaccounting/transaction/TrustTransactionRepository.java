package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrustTransactionRepository extends JpaRepository<TrustTransaction, UUID> {

  Page<TrustTransaction> findByTrustAccountIdOrderByTransactionDateDesc(
      UUID trustAccountId, Pageable pageable);

  /**
   * Account-scoped transaction list with optional filters (Phase 88 Epic 621A). Every filter is
   * optional — {@code null} (or {@code excludeLinked=false}) disables its predicate, so a call with
   * no filters returns exactly what {@link #findByTrustAccountIdOrderByTransactionDateDesc} does.
   * {@code projectId} is the matter (wire {@code matterId}). {@code excludeLinked} hides trust
   * transactions already referenced by a disbursement ({@code legal_disbursements
   * .trust_transaction_id}, index {@code idx_legal_disbursements_trust_tx}) so the disbursement
   * trust-link picker cannot offer a used payment. JPQL (not native) so the callers' Pageable
   * {@code sort=transactionDate,desc} resolves against the entity property.
   */
  @Query(
      """
      SELECT t FROM TrustTransaction t
      WHERE t.trustAccountId = :trustAccountId
        AND (:projectId IS NULL OR t.projectId = :projectId)
        AND (:transactionType IS NULL OR t.transactionType = :transactionType)
        AND (:status IS NULL OR t.status = :status)
        AND (:excludeLinked = false
             OR NOT EXISTS (
               SELECT d.id FROM LegalDisbursement d WHERE d.trustTransactionId = t.id))
      ORDER BY t.transactionDate DESC
      """)
  Page<TrustTransaction> findFiltered(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("projectId") UUID projectId,
      @Param("transactionType") String transactionType,
      @Param("status") String status,
      @Param("excludeLinked") boolean excludeLinked,
      Pageable pageable);

  List<TrustTransaction> findByCustomerIdAndTrustAccountIdOrderByTransactionDateDesc(
      UUID customerId, UUID trustAccountId);

  /**
   * Returns every trust transaction for the customer regardless of trust account. Used by DSAR
   * audit-trail collection (Epic 505A) to resolve child-entity IDs.
   */
  List<TrustTransaction> findByCustomerId(UUID customerId);

  Page<TrustTransaction> findByCustomerIdAndTrustAccountIdOrderByTransactionDateDesc(
      UUID customerId, UUID trustAccountId, Pageable pageable);

  List<TrustTransaction> findByStatusAndTrustAccountId(String status, UUID trustAccountId);

  /**
   * Fetches every trust transaction for a matter (project) in ascending transaction date / created
   * order. Used by the portal trust sync service to walk a matter's history forward and assign a
   * progressive running balance per row (Epic 495A).
   */
  @Query(
      """
      SELECT t FROM TrustTransaction t
      WHERE t.projectId = :projectId
      ORDER BY t.transactionDate ASC, t.createdAt ASC, t.id ASC
      """)
  List<TrustTransaction> findByProjectIdOrderByTransactionDateAsc(
      @Param("projectId") UUID projectId);

  /** Acquires a pessimistic write lock on the transaction row to prevent concurrent reversals. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM TrustTransaction t WHERE t.id = :id")
  Optional<TrustTransaction> findByIdForUpdate(@Param("id") UUID id);

  /**
   * Finds the paired transfer transaction (e.g., TRANSFER_IN paired with a TRANSFER_OUT). The pair
   * shares the same reference, trust account, and transaction date but has the opposite transfer
   * type and targets the counterparty customer.
   */
  @Query(
      """
      SELECT t FROM TrustTransaction t
      WHERE t.trustAccountId = :trustAccountId
        AND t.reference = :reference
        AND t.transactionType = :transactionType
        AND t.customerId = :customerId
      """)
  Optional<TrustTransaction> findPairedTransfer(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("reference") String reference,
      @Param("transactionType") String transactionType,
      @Param("customerId") UUID customerId);

  /** Checks whether a reversal transaction already exists for the given original transaction. */
  boolean existsByReversalOf(UUID reversalOf);

  /**
   * Computes the cashbook balance for a trust account. Cashbook-positive: DEPOSIT, INTEREST_CREDIT.
   * Cashbook-negative: PAYMENT, FEE_TRANSFER, REFUND, INTEREST_LPFF. Cashbook-neutral: TRANSFER_IN,
   * TRANSFER_OUT. REVERSAL is excluded — debit reversals are already reflected in the client ledger
   * card balances (updated inline), and credit reversals only affect balances when approved (Epic
   * 441 scope).
   */
  @Query(
      """
      SELECT COALESCE(SUM(
        CASE
          WHEN t.transactionType IN ('DEPOSIT', 'INTEREST_CREDIT') THEN t.amount
          WHEN t.transactionType IN ('PAYMENT', 'FEE_TRANSFER', 'REFUND', 'INTEREST_LPFF') THEN -t.amount
          ELSE 0
        END
      ), 0)
      FROM TrustTransaction t
      WHERE t.trustAccountId = :trustAccountId
        AND t.status IN ('RECORDED', 'APPROVED')
      """)
  BigDecimal calculateCashbookBalance(@Param("trustAccountId") UUID trustAccountId);

  /**
   * Sums transactions for a specific customer up to a given date, applying credit/debit logic.
   * Credit types (DEPOSIT, TRANSFER_IN, INTEREST_CREDIT) add to balance. Debit types (PAYMENT,
   * TRANSFER_OUT, FEE_TRANSFER, REFUND, INTEREST_LPFF) subtract from balance. REVERSAL is excluded
   * — debit reversals are reflected via ledger card updates (inline), and credit reversals only
   * affect balances when approved (Epic 441 scope).
   */
  @Query(
      """
      SELECT COALESCE(SUM(
        CASE
          WHEN t.transactionType IN ('DEPOSIT', 'TRANSFER_IN', 'INTEREST_CREDIT') THEN t.amount
          WHEN t.transactionType IN ('PAYMENT', 'TRANSFER_OUT', 'FEE_TRANSFER', 'REFUND', 'INTEREST_LPFF') THEN -t.amount
          ELSE 0
        END
      ), 0)
      FROM TrustTransaction t
      WHERE t.customerId = :customerId
        AND t.trustAccountId = :trustAccountId
        AND t.transactionDate <= :asOfDate
        AND t.status IN ('RECORDED', 'APPROVED')
      """)
  BigDecimal calculateClientBalanceAsOfDate(
      @Param("customerId") UUID customerId,
      @Param("trustAccountId") UUID trustAccountId,
      @Param("asOfDate") LocalDate asOfDate);

  /**
   * Sums the per-matter trust balance by projectId, applying the same credit/debit sign rules as
   * {@link #calculateClientBalanceAsOfDate}. Used by the matter closure gate {@code
   * TRUST_BALANCE_ZERO} (Phase 67 §67.3.4 gate 1) — a matter cannot close while it holds client
   * funds.
   */
  @Query(
      """
      SELECT COALESCE(SUM(
        CASE
          WHEN t.transactionType IN ('DEPOSIT', 'TRANSFER_IN', 'INTEREST_CREDIT') THEN t.amount
          WHEN t.transactionType IN ('PAYMENT', 'TRANSFER_OUT', 'FEE_TRANSFER', 'REFUND', 'INTEREST_LPFF') THEN -t.amount
          ELSE 0
        END
      ), 0)
      FROM TrustTransaction t
      WHERE t.projectId = :projectId
        AND t.status IN ('RECORDED', 'APPROVED')
      """)
  BigDecimal calculateBalanceByProjectId(@Param("projectId") UUID projectId);

  /**
   * Finds unmatched candidate transactions for bank reconciliation auto-matching. Candidates are
   * RECORDED or APPROVED transactions that have not yet been matched to a bank statement line,
   * within the specified date range.
   */
  @Query(
      """
      SELECT t FROM TrustTransaction t
      WHERE t.trustAccountId = :trustAccountId
        AND t.status IN ('RECORDED', 'APPROVED')
        AND t.bankStatementLineId IS NULL
        AND t.transactionDate >= :startDate
        AND t.transactionDate <= :endDate
      """)
  List<TrustTransaction> findUnmatchedCandidates(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  /**
   * Sums outstanding deposits: approved credit-type transactions not matched to bank statements.
   */
  @Query(
      """
      SELECT COALESCE(SUM(t.amount), 0)
      FROM TrustTransaction t
      WHERE t.trustAccountId = :trustAccountId
        AND t.status IN ('RECORDED', 'APPROVED')
        AND t.bankStatementLineId IS NULL
        AND t.transactionType IN ('DEPOSIT', 'INTEREST_CREDIT')
      """)
  BigDecimal calculateOutstandingDeposits(@Param("trustAccountId") UUID trustAccountId);

  /** Sums outstanding payments: approved debit-type transactions not matched to bank statements. */
  @Query(
      """
      SELECT COALESCE(SUM(t.amount), 0)
      FROM TrustTransaction t
      WHERE t.trustAccountId = :trustAccountId
        AND t.status IN ('RECORDED', 'APPROVED')
        AND t.bankStatementLineId IS NULL
        AND t.transactionType IN ('PAYMENT', 'FEE_TRANSFER', 'REFUND', 'INTEREST_LPFF')
      """)
  BigDecimal calculateOutstandingPayments(@Param("trustAccountId") UUID trustAccountId);

  /**
   * Fetches all approved/recorded non-reversal transactions for a trust account within a date
   * range, ordered by date ASC. Used by the Receipts & Payments report.
   */
  @Query(
      """
      SELECT t FROM TrustTransaction t
      WHERE t.trustAccountId = :trustAccountId
        AND t.transactionDate >= :startDate
        AND t.transactionDate <= :endDate
        AND t.status IN ('RECORDED', 'APPROVED')
        AND t.transactionType <> 'REVERSAL'
      ORDER BY t.transactionDate ASC, t.createdAt ASC
      """)
  List<TrustTransaction> findForReceiptsPayments(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  /** Fetches transactions for a customer in date range, ordered by date ASC for statement. */
  @Query(
      """
      SELECT t FROM TrustTransaction t
      WHERE t.customerId = :customerId
        AND t.trustAccountId = :trustAccountId
        AND t.transactionDate >= :startDate
        AND t.transactionDate <= :endDate
        AND t.status IN ('RECORDED', 'APPROVED')
        AND t.transactionType <> 'REVERSAL'
      ORDER BY t.transactionDate ASC, t.createdAt ASC
      """)
  List<TrustTransaction> findForStatement(
      @Param("customerId") UUID customerId,
      @Param("trustAccountId") UUID trustAccountId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  /**
   * Returns the distinct trust account ids on which a customer has any RECORDED/APPROVED activity.
   * Used by the Statement of Account context builder to resolve the correct trust account for the
   * SoA "Trust Activity" section even when the customer's deposits are not on the primary GENERAL
   * account (GAP-L-94). Excludes REVERSAL because reversals do not by themselves indicate that the
   * customer ever held funds on the account.
   */
  @Query(
      """
      SELECT DISTINCT t.trustAccountId FROM TrustTransaction t
      WHERE t.customerId = :customerId
        AND t.status IN ('RECORDED', 'APPROVED')
        AND t.transactionType <> 'REVERSAL'
      ORDER BY t.trustAccountId ASC
      """)
  List<UUID> findDistinctTrustAccountIdsByCustomerId(@Param("customerId") UUID customerId);
}
