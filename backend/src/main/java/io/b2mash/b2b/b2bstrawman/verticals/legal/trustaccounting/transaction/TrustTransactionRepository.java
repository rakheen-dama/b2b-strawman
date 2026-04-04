package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrustTransactionRepository extends JpaRepository<TrustTransaction, UUID> {

  Page<TrustTransaction> findByTrustAccountIdOrderByTransactionDateDesc(
      UUID trustAccountId, Pageable pageable);

  List<TrustTransaction> findByCustomerIdAndTrustAccountIdOrderByTransactionDateDesc(
      UUID customerId, UUID trustAccountId);

  Page<TrustTransaction> findByCustomerIdAndTrustAccountIdOrderByTransactionDateDesc(
      UUID customerId, UUID trustAccountId, Pageable pageable);

  List<TrustTransaction> findByStatusAndTrustAccountId(String status, UUID trustAccountId);

  /**
   * Computes the cashbook balance for a trust account. Cashbook-positive: DEPOSIT, INTEREST_CREDIT.
   * Cashbook-negative: PAYMENT, FEE_TRANSFER, REFUND, INTEREST_LPFF. Cashbook-neutral: TRANSFER_IN,
   * TRANSFER_OUT. REVERSAL with RECORDED/APPROVED status treated as cashbook-positive (debit
   * reversals add money back).
   */
  @Query(
      """
      SELECT COALESCE(SUM(
        CASE
          WHEN t.transactionType IN ('DEPOSIT', 'INTEREST_CREDIT', 'REVERSAL') THEN t.amount
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
   * TRANSFER_OUT, FEE_TRANSFER, REFUND) subtract from balance. REVERSAL treated as credit (adds
   * money back for debit reversals which are the only immediate ones).
   */
  @Query(
      """
      SELECT COALESCE(SUM(
        CASE
          WHEN t.transactionType IN ('DEPOSIT', 'TRANSFER_IN', 'INTEREST_CREDIT', 'REVERSAL') THEN t.amount
          WHEN t.transactionType IN ('PAYMENT', 'TRANSFER_OUT', 'FEE_TRANSFER', 'REFUND') THEN -t.amount
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
      ORDER BY t.transactionDate ASC, t.createdAt ASC
      """)
  List<TrustTransaction> findForStatement(
      @Param("customerId") UUID customerId,
      @Param("trustAccountId") UUID trustAccountId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);
}
