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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM TrustTransaction t WHERE t.id = :id")
  Optional<TrustTransaction> findByIdForUpdate(@Param("id") UUID id);

  Page<TrustTransaction> findByTrustAccountIdOrderByTransactionDateDesc(
      UUID trustAccountId, Pageable pageable);

  List<TrustTransaction> findByCustomerIdAndTrustAccountIdOrderByTransactionDateDesc(
      UUID customerId, UUID trustAccountId);

  Page<TrustTransaction> findByCustomerIdAndTrustAccountIdOrderByTransactionDateDesc(
      UUID customerId, UUID trustAccountId, Pageable pageable);

  List<TrustTransaction> findByStatusAndTrustAccountId(String status, UUID trustAccountId);

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
}
