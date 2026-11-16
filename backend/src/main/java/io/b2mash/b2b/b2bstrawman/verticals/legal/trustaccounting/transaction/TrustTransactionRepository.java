package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
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

  /**
   * Filters of {@link #findClientHistoryWithRunningBalance} — shared by its page and count queries.
   * Every nullable parameter is cast on both occurrences so Postgres can type a null bind.
   */
  String CLIENT_HISTORY_WHERE =
      """
       WHERE t.customer_id = :customerId AND t.trust_account_id = :accountId
         AND (CAST(:type AS varchar)   IS NULL OR t.transaction_type = CAST(:type AS varchar))
         AND (CAST(:status AS varchar) IS NULL OR t.status = CAST(:status AS varchar))
         AND (CAST(:dateFrom AS date)  IS NULL OR t.transaction_date >= CAST(:dateFrom AS date))
         AND (CAST(:dateTo AS date)    IS NULL OR t.transaction_date <= CAST(:dateTo AS date))
      """;

  /**
   * A client's trust history on one account, newest first ({@code transaction_date DESC, created_at
   * DESC, id DESC}), each row carrying its running balance (ADR-361, arch §14.3.3). The window runs
   * over POSTED rows only ({@code status IN ('RECORDED','APPROVED')}, REVERSAL excluded) in posting
   * order and BEFORE the filters narrow the page, so a type/status/date filter never changes a
   * balance; non-posted, REVERSED and REVERSAL rows get a null balance. Each row adds its own
   * {@code ledger_effect} (Phase 92, ADR-378) — the same figure as {@link
   * TrustLedgerQueryRepository#clientBalance}. Callers MUST pass a sort-free {@link Pageable} — the
   * ORDER BY lives in the SQL and Spring Data appends only the page window (R6.7).
   *
   * <p>The CTE is {@code MATERIALIZED} on purpose: Postgres inlines a once-referenced,
   * side-effect-free CTE, and with cold statistics (a freshly provisioned tenant) the planner then
   * re-executed the window subquery once per outer row (WindowAgg loops=N → O(n²) per client).
   * Materialising forces a single evaluation; it is result-neutral because the CTE has no side
   * effects and all of its predicates are internal to it.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          WITH posted AS MATERIALIZED (
            SELECT t.id,
                   SUM(t.ledger_effect)
                     OVER (ORDER BY t.transaction_date, t.created_at, t.id
                           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_balance
              FROM trust_transactions t
             WHERE t.customer_id = :customerId AND t.trust_account_id = :accountId
               AND t.status IN ('RECORDED','APPROVED')
               AND t.transaction_type <> 'REVERSAL'
          )
          SELECT t.id AS id, t.trust_account_id AS trustAccountId,
                 t.transaction_type AS transactionType, t.amount AS amount,
                 t.customer_id AS customerId, t.project_id AS projectId,
                 t.counterparty_customer_id AS counterpartyCustomerId, t.invoice_id AS invoiceId,
                 t.reference AS reference, t.description AS description,
                 t.transaction_date AS transactionDate, t.status AS status,
                 t.approved_by AS approvedBy, t.approved_at AS approvedAt,
                 t.second_approved_by AS secondApprovedBy, t.second_approved_at AS secondApprovedAt,
                 t.rejected_by AS rejectedBy, t.rejected_at AS rejectedAt,
                 t.rejection_reason AS rejectionReason, t.reversal_of AS reversalOf,
                 t.reversed_by_id AS reversedById, t.bank_statement_line_id AS bankStatementLineId,
                 t.recorded_by AS recordedBy, t.created_at AS createdAt,
                 p.running_balance AS runningBalance
            FROM trust_transactions t
            LEFT JOIN posted p ON p.id = t.id
          """
              + CLIENT_HISTORY_WHERE
              + " ORDER BY t.transaction_date DESC, t.created_at DESC, t.id DESC",
      countQuery = "SELECT count(*) FROM trust_transactions t " + CLIENT_HISTORY_WHERE)
  Page<ClientHistoryRow> findClientHistoryWithRunningBalance(
      @Param("customerId") UUID customerId,
      @Param("accountId") UUID accountId,
      @Param("type") String type,
      @Param("status") String status,
      @Param("dateFrom") LocalDate dateFrom,
      @Param("dateTo") LocalDate dateTo,
      Pageable pageable);

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
   * Monthly net movements on one trust account for the trust-balance trend (Phase 90 643A, ADR-372,
   * arch §15.3.7): one row per SA calendar month from {@code windowStart} to {@code today}, plus
   * one row with a NULL {@code monthStart} holding everything dated before {@code windowStart} (the
   * opening balance). {@code cashbookNet} sums {@code cashbook_effect} (as {@link
   * TrustLedgerQueryRepository#cashbook}); {@code ledgerNet} sums {@code ledger_effect} on the
   * {@code CLIENT} ledger (as {@link TrustLedgerQueryRepository#clientLedgerTotal}). Posted rows
   * only (Phase 92, ADR-378) — a REVERSED original and its REVERSAL carry zero effect in 646A.
   * {@code transaction_date} is already an SA business date (G88-F48), so months need no zone
   * conversion: the date is truncated as a zone-less {@code timestamp}. Rows after {@code today}
   * are excluded.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT CASE WHEN t.transaction_date < :windowStart THEN NULL
                      ELSE CAST(date_trunc('month', CAST(t.transaction_date AS timestamp)) AS date)
                 END AS monthStart,
                 COALESCE(SUM(t.cashbook_effect), 0) AS cashbookNet,
                 COALESCE(SUM(t.ledger_effect)
                   FILTER (WHERE t.ledger = 'CLIENT' AND t.customer_id IS NOT NULL), 0) AS ledgerNet
          FROM trust_transactions t
          WHERE t.trust_account_id = :trustAccountId
            AND t.posted_at IS NOT NULL
            AND t.transaction_date <= :today
          GROUP BY 1
          ORDER BY 1 NULLS FIRST
          """)
  List<TrustMonthlyMovementProjection> findMonthlyBalanceMovements(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("windowStart") LocalDate windowStart,
      @Param("today") LocalDate today);

  /**
   * Posted totals per client and transaction type on one trust account (G89-F13): RECORDED/APPROVED
   * rows only, REVERSAL excluded — a reversed debit is itself marked REVERSED, so it drops out of
   * its type's total, matching the balance. Feeds {@code LedgerTotals}, the client-ledger totals
   * that reconcile to the balance. One query for any number of clients.
   */
  @Query(
      """
      SELECT t.customerId AS customerId, t.transactionType AS transactionType,
             COALESCE(SUM(t.amount), 0) AS total
      FROM TrustTransaction t
      WHERE t.trustAccountId = :trustAccountId
        AND t.customerId IN :customerIds
        AND t.status IN ('RECORDED', 'APPROVED')
        AND t.transactionType <> 'REVERSAL'
      GROUP BY t.customerId, t.transactionType
      """)
  List<CustomerTypeTotalRow> sumPostedByCustomerAndType(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("customerIds") Collection<UUID> customerIds);

  /**
   * {@link #sumPostedByCustomerAndType} up to and including {@code asOfDate} — the totals that
   * reconcile to {@link TrustLedgerQueryRepository#clientBalance} for the same date (the Client
   * Trust Balances report with an as-of date).
   */
  @Query(
      """
      SELECT t.customerId AS customerId, t.transactionType AS transactionType,
             COALESCE(SUM(t.amount), 0) AS total
      FROM TrustTransaction t
      WHERE t.trustAccountId = :trustAccountId
        AND t.customerId IN :customerIds
        AND t.transactionDate <= :asOfDate
        AND t.status IN ('RECORDED', 'APPROVED')
        AND t.transactionType <> 'REVERSAL'
      GROUP BY t.customerId, t.transactionType
      """)
  List<CustomerTypeTotalRow> sumPostedByCustomerAndTypeAsOf(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("customerIds") Collection<UUID> customerIds,
      @Param("asOfDate") LocalDate asOfDate);

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
        AND t.transactionType = 'DEPOSIT'
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
        AND t.transactionType IN ('PAYMENT', 'FEE_TRANSFER', 'REFUND')
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
