package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only SQL of the Phase 92 650C trust reports (architecture §16.8.3 – §16.8.5): a Fund
 * ledger's posted movements grouped by month, section 86(4) interest entries by month, and Fund
 * sweeps with their statement line. Balances (brought forward, carried forward, items, ties) come
 * from {@code TrustLedgerQueryRepository}. Reads only — no locks, nothing modifying (report-local,
 * D-23).
 */
public interface TrustReportQueryRepository extends Repository<TrustTransaction, UUID> {

  /**
   * One (month, type, recoverable) group of a Fund ledger's posted rows. A {@code REVERSAL} contra
   * is grouped under its original's type (and, for a bank charge, its original's recoverable flag
   * and VAT, negated) so a pair in one month nets to zero (D-8).
   */
  interface FundLedgerMonthRow {
    /** The first day of the month. */
    LocalDate getPeriodMonth();

    /**
     * FUND_INTEREST | FUND_SWEEP | BANK_CHARGE | CHARGE_REFUND | FUND_INV_SHARE (never printed).
     */
    String getTxType();

    /** BANK_CHARGE only (the original's flag for a contra); null otherwise. */
    Boolean getRecoverable();

    /** Signed Σ ledger_effect. */
    BigDecimal getLedgerEffect();

    /** Signed Σ VAT included in the charges (BANK_CHARGE only; a contra counts negative). */
    BigDecimal getVat();
  }

  @Query(
      nativeQuery = true,
      value =
          """
          SELECT CAST(date_trunc('month', t.transaction_date) AS date) AS periodMonth,
                 CASE WHEN t.transaction_type = 'REVERSAL' THEN o.transaction_type
                      ELSE t.transaction_type END AS txType,
                 CASE WHEN t.transaction_type = 'REVERSAL' THEN o.charge_recoverable
                      ELSE t.charge_recoverable END AS recoverable,
                 COALESCE(SUM(t.ledger_effect), 0) AS ledgerEffect,
                 COALESCE(SUM(CASE WHEN t.transaction_type = 'REVERSAL'
                                   THEN -COALESCE(o.charge_vat_amount, 0)
                                   ELSE COALESCE(t.charge_vat_amount, 0) END), 0) AS vat
            FROM trust_transactions t
            LEFT JOIN trust_transactions o ON o.id = t.reversal_of
           WHERE t.creditor_account_id = :fundAccountId AND t.ledger = 'NON_CLIENT'
             AND t.posted_at IS NOT NULL
             AND t.transaction_date >= CAST(:fromDate AS date)
             AND t.transaction_date <= CAST(:toDate AS date)
           GROUP BY 1, 2, 3""")
  List<FundLedgerMonthRow> fundLedgerByMonth(
      @Param("fundAccountId") UUID fundAccountId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

  /**
   * POSTED section 86(4) interest entries of the investments held in an account ({@code
   * trust_investments.investment_account_id}), by month and presentation.
   */
  interface InterestEntryMonthRow {
    LocalDate getPeriodMonth();

    /** GROSS | NET (never printed). */
    String getPresentation();

    BigDecimal getGross();

    BigDecimal getFundShare();

    BigDecimal getNet();
  }

  @Query(
      nativeQuery = true,
      value =
          """
          SELECT CAST(date_trunc('month', e.entry_date) AS date) AS periodMonth,
                 e.presentation AS presentation,
                 COALESCE(SUM(e.gross_amount), 0) AS gross,
                 COALESCE(SUM(e.fund_share_amount), 0) AS fundShare,
                 COALESCE(SUM(e.net_amount), 0) AS net
            FROM trust_investment_interest e
            JOIN trust_investments i ON i.id = e.investment_id
           WHERE i.investment_account_id = :accountId AND e.status = 'POSTED'
             AND e.entry_date >= CAST(:fromDate AS date)
             AND e.entry_date <= CAST(:toDate AS date)
           GROUP BY 1, 2""")
  List<InterestEntryMonthRow> investmentInterestByMonth(
      @Param("accountId") UUID accountId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

  /**
   * A posted {@code FUND_SWEEP} row not reversed as at {@code toDate} (a contra dated after it
   * leaves the sweep in the period), with its statement line (null when none) — Rule 54.14.16
   * evidence for the Section 35 pack.
   */
  interface FundSweepRow {
    UUID getTransactionId();

    UUID getTrustAccountId();

    LocalDate getTransactionDate();

    BigDecimal getAmount();

    String getReference();

    /** Null when the sweep has no statement line. */
    Integer getLineNumber();

    LocalDate getLineDate();

    /** The imported statement's file name ({@code bank_statements.file_name}). */
    String getFileName();
  }

  /** {@code accountIds} must not be empty ({@code IN ()} is invalid SQL). */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT t.id AS transactionId, t.trust_account_id AS trustAccountId,
                 t.transaction_date AS transactionDate, t.amount AS amount,
                 t.reference AS reference, l.line_number AS lineNumber,
                 l.transaction_date AS lineDate, s.file_name AS fileName
            FROM trust_transactions t
            LEFT JOIN bank_statement_lines l ON l.id = t.bank_statement_line_id
            LEFT JOIN bank_statements s ON s.id = l.bank_statement_id
           WHERE t.trust_account_id IN (:accountIds) AND t.transaction_type = 'FUND_SWEEP'
             AND t.posted_at IS NOT NULL
             AND NOT EXISTS (SELECT 1 FROM trust_transactions r
                              WHERE r.reversal_of = t.id AND r.posted_at IS NOT NULL
                                AND r.transaction_date <= CAST(:toDate AS date))
             AND t.transaction_date >= CAST(:fromDate AS date)
             AND t.transaction_date <= CAST(:toDate AS date)
           ORDER BY t.transaction_date, t.created_at, t.id""")
  List<FundSweepRow> fundSweeps(
      @Param("accountIds") Collection<UUID> accountIds,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

  /**
   * The resolution of an unidentified deposit in effect as at {@code asAt}, from its posted rows
   * (CR-8): its {@code UNIDENT_ALLOC_OUT} or {@code UNIDENT_FUND_PAY} dated on or before the date
   * with no contra dated on or before it, and for an allocation the client of its {@code
   * UNIDENT_ALLOC_IN} leg (same item, likewise unreversed as at the date). Never the item's current
   * status: a reversal after the date reopens the item (HELD, resolution cleared) but the item was
   * resolved as at the date.
   */
  interface UnidentifiedResolutionRow {
    UUID getUnidentifiedDepositId();

    /** UNIDENT_ALLOC_OUT | UNIDENT_FUND_PAY (never printed). */
    String getTxType();

    LocalDate getResolvedDate();

    /** The allocated client; null for a payment to the Fund. */
    UUID getCustomerId();
  }

  /** {@code depositIds} must not be empty ({@code IN ()} is invalid SQL). */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT t.unidentified_deposit_id AS unidentifiedDepositId,
                 t.transaction_type AS txType, t.transaction_date AS resolvedDate,
                 (SELECT c.customer_id FROM trust_transactions c
                   WHERE c.unidentified_deposit_id = t.unidentified_deposit_id
                     AND c.transaction_type = 'UNIDENT_ALLOC_IN' AND c.posted_at IS NOT NULL
                     AND c.transaction_date <= CAST(:asAt AS date)
                     AND NOT EXISTS (SELECT 1 FROM trust_transactions cr
                                      WHERE cr.reversal_of = c.id AND cr.posted_at IS NOT NULL
                                        AND cr.transaction_date <= CAST(:asAt AS date))
                   ORDER BY c.transaction_date DESC, c.created_at DESC, c.id DESC
                   LIMIT 1) AS customerId
            FROM trust_transactions t
           WHERE t.unidentified_deposit_id IN (:depositIds)
             AND t.transaction_type IN ('UNIDENT_ALLOC_OUT', 'UNIDENT_FUND_PAY')
             AND t.posted_at IS NOT NULL
             AND t.transaction_date <= CAST(:asAt AS date)
             AND NOT EXISTS (SELECT 1 FROM trust_transactions r
                              WHERE r.reversal_of = t.id AND r.posted_at IS NOT NULL
                                AND r.transaction_date <= CAST(:asAt AS date))
           ORDER BY t.transaction_date, t.created_at, t.id""")
  List<UnidentifiedResolutionRow> unidentifiedResolutions(
      @Param("depositIds") Collection<UUID> depositIds, @Param("asAt") LocalDate asAt);
}
