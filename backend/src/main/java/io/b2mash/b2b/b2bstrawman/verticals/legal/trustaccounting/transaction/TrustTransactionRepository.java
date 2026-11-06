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
   * balance; non-posted and REVERSAL rows get a null balance. Type sets and status filter are
   * identical to {@link #calculateClientBalanceAsOfDate}. Callers MUST pass a sort-free {@link
   * Pageable} — the ORDER BY lives in the SQL and Spring Data appends only the page window (R6.7).
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
                   SUM(CASE WHEN t.transaction_type IN ('DEPOSIT','TRANSFER_IN','INTEREST_CREDIT') THEN  t.amount
                            WHEN t.transaction_type IN ('PAYMENT','TRANSFER_OUT','FEE_TRANSFER','REFUND','INTEREST_LPFF') THEN -t.amount
                            ELSE 0 END)
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
   * One client's money on one matter in one trust account — the matter sub-ledger cell (G89-F12).
   * Same sign rules and RECORDED/APPROVED filter as {@link #calculateClientBalanceAsOfDate}. A
   * debit on a matter must be covered by this cell, not merely by the client card: the closure gate
   * reads the matter, and a co-client must never spend another client's money on a shared matter.
   * The caller holds the client card's row lock ({@code findByAccountAndCustomerForUpdate}), which
   * serialises every posting for the client, so the sum cannot race.
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
      WHERE t.trustAccountId = :trustAccountId
        AND t.customerId = :customerId
        AND t.projectId = :projectId
        AND t.status IN ('RECORDED', 'APPROVED')
      """)
  BigDecimal calculateMatterClientBalance(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("customerId") UUID customerId,
      @Param("projectId") UUID projectId);

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
