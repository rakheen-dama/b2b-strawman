package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import jakarta.persistence.LockModeType;
import java.time.Instant;
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
   * {@code projectId} is the matter (wire {@code matterId}); {@code ledger} the creditor ledger the
   * row moves (646E). {@code excludeLinked} hides trust transactions already referenced by a
   * disbursement ({@code legal_disbursements .trust_transaction_id}, index {@code
   * idx_legal_disbursements_trust_tx}) so the disbursement trust-link picker cannot offer a used
   * payment. JPQL (not native) so the callers' Pageable {@code sort=transactionDate,desc} resolves
   * against the entity property.
   */
  @Query(
      """
      SELECT t FROM TrustTransaction t
      WHERE t.trustAccountId = :trustAccountId
        AND (:projectId IS NULL OR t.projectId = :projectId)
        AND (:transactionType IS NULL OR t.transactionType = :transactionType)
        AND (:status IS NULL OR t.status = :status)
        AND (:ledger IS NULL OR t.ledger = :ledger)
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
      @Param("ledger") TrustLedger ledger,
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
   * <p>646E: each row also carries its original's type ({@code reversedType}, a join to the unique
   * id — the count query needs no join) for a REVERSAL's direction, and {@code reversalPending},
   * the same predicate as {@link #findPendingReversalOriginalIds} as one hashed subplan.
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
                 p.running_balance AS runningBalance,
                 t.ledger AS ledger, t.creditor_account_id AS creditorAccountId,
                 t.investment_id AS investmentId, t.posted_at AS postedAt,
                 o.transaction_type AS reversedType,
                 (t.id IN (SELECT r.reversal_of FROM trust_transactions r
                            WHERE r.status = 'AWAITING_APPROVAL'
                              AND r.reversal_of IS NOT NULL)) AS reversalPending
            FROM trust_transactions t
            LEFT JOIN posted p ON p.id = t.id
            LEFT JOIN trust_transactions o ON o.id = t.reversal_of
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

  /**
   * Whether a reversal of the given original exists other than in {@code status} — called with
   * {@code "REJECTED"}: a rejected contra never posted, so it leaves the original reversible (H-c,
   * 649B); a posted or pending contra still blocks a second reversal.
   */
  boolean existsByReversalOfAndStatusNot(UUID reversalOf, String status);

  /**
   * Of {@code ids}, the originals that have a contra awaiting approval (646E {@code
   * reversalPending}). With {@code reversedById} this is the whole of the reversal guard's "already
   * reversed" test ({@link #existsByReversalOfAndStatusNot} with {@code "REJECTED"}): a posted
   * contra sets {@code reversedById}, a rejected one never blocks, so a pending one is the only
   * blocking state the row's own columns do not show.
   */
  @Query(
      """
      SELECT r.reversalOf FROM TrustTransaction r
      WHERE r.reversalOf IN :ids AND r.status = 'AWAITING_APPROVAL'
      """)
  List<UUID> findPendingReversalOriginalIds(@Param("ids") Collection<UUID> ids);

  /**
   * Whether a resolution of the unidentified deposit ({@code UNIDENT_ALLOC_OUT} / {@code
   * UNIDENT_FUND_PAY}) ever posted — reversed since or not (647D, F-647D-1). A rejected or pending
   * one never posted ({@code posted_at} null) and does not count.
   */
  @Query(
      """
      SELECT COUNT(t) > 0 FROM TrustTransaction t
      WHERE t.unidentifiedDepositId = :itemId
        AND t.transactionType IN ('UNIDENT_ALLOC_OUT', 'UNIDENT_FUND_PAY')
        AND t.postedAt IS NOT NULL
      """)
  boolean existsPostedResolutionOfItem(@Param("itemId") UUID itemId);

  /**
   * The date of the latest posted contra of a resolution of the unidentified deposit — the day the
   * item was last reopened by an undo (647D fix cycle 1); empty when it never was.
   */
  @Query(
      """
      SELECT MAX(c.transactionDate) FROM TrustTransaction c
      WHERE c.unidentifiedDepositId = :itemId
        AND c.transactionType = 'REVERSAL'
        AND c.postedAt IS NOT NULL
        AND c.reversalOf IN (
          SELECT r.id FROM TrustTransaction r
          WHERE r.unidentifiedDepositId = :itemId
            AND r.transactionType IN ('UNIDENT_ALLOC_OUT', 'UNIDENT_FUND_PAY'))
      """)
  Optional<LocalDate> findLatestReopeningDate(@Param("itemId") UUID itemId);

  /**
   * The non-{@code REVERSAL} rows in {@code statuses} linked to a bank statement line — the posted
   * originals a statement-line unmatch reverses ({@code reverseForUnmatch}, 649B for 647A). Oldest
   * first, so the contras are written in posting order.
   */
  @Query(
      """
      SELECT t FROM TrustTransaction t
      WHERE t.bankStatementLineId = :bankStatementLineId
        AND t.status IN :statuses
        AND t.transactionType <> 'REVERSAL'
      ORDER BY t.transactionDate ASC, t.createdAt ASC, t.id ASC
      """)
  List<TrustTransaction> findPostedOriginalsByBankStatementLineId(
      @Param("bankStatementLineId") UUID bankStatementLineId,
      @Param("statuses") List<String> statuses);

  /**
   * Every row linked to a bank statement line, whatever its status (647A): the rows a recorded line
   * created, unlinked when it is unmatched. Oldest first.
   */
  @Query(
      """
      SELECT t FROM TrustTransaction t
      WHERE t.bankStatementLineId = :bankStatementLineId
      ORDER BY t.createdAt ASC, t.id ASC
      """)
  List<TrustTransaction> findByBankStatementLineId(
      @Param("bankStatementLineId") UUID bankStatementLineId);

  /**
   * Every row linked to any of {@code bankStatementLineIds}, oldest first — the statement GET's
   * batched "created transactions" of its recorded lines (647A).
   */
  @Query(
      """
      SELECT t FROM TrustTransaction t
      WHERE t.bankStatementLineId IN :bankStatementLineIds
      ORDER BY t.createdAt ASC, t.id ASC
      """)
  List<TrustTransaction> findByBankStatementLineIdIn(
      @Param("bankStatementLineIds") Collection<UUID> bankStatementLineIds);

  /**
   * Monthly net movements on one trust account for the trust-balance trend (Phase 90 643A, ADR-372,
   * arch §15.3.7): one row per SA calendar month from {@code windowStart} to {@code today}, plus
   * one row with a NULL {@code monthStart} holding everything dated before {@code windowStart} (the
   * opening balance). {@code cashbookNet} sums {@code cashbook_effect} (as {@link
   * TrustLedgerQueryRepository#cashbook}); {@code ledgerNet} sums {@code ledger_effect} over every
   * ledger — client, investment, non-client and inter-account — so it is the monthly movement of
   * {@code TrustBooksService.position(…).creditorTotal()} (646D, §16.4.4). Posted rows only (Phase
   * 92, ADR-378) — a REVERSED original counts in its own month and its REVERSAL (the contra, 649B)
   * in the reversal date's month. {@code transaction_date} is already an SA business date
   * (G88-F48), so months need no zone conversion: the date is truncated as a zone-less {@code
   * timestamp}. Rows after {@code today} are excluded.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT CASE WHEN t.transaction_date < :windowStart THEN NULL
                      ELSE CAST(date_trunc('month', CAST(t.transaction_date AS timestamp)) AS date)
                 END AS monthStart,
                 COALESCE(SUM(t.cashbook_effect), 0) AS cashbookNet,
                 COALESCE(SUM(t.ledger_effect), 0) AS ledgerNet
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
   * Finds unmatched candidate transactions for bank reconciliation auto-matching: posted rows
   * ({@code posted_at} set — RECORDED, APPROVED or REVERSED, {@code chk_trust_txn_posting}) not yet
   * matched to a bank statement line, within the specified date range. A REVERSED original is still
   * posted with its own cashbook effect (649B), so it matches the bank line that shows it like any
   * other posted row (652H); an unposted row (AWAITING_APPROVAL, REJECTED) never does. The caller
   * drops a REVERSED row recorded from a statement line ({@link
   * TrustTransactionType#recordedOnlyFromStatementLine}).
   */
  @Query(
      """
      SELECT t FROM TrustTransaction t
      WHERE t.trustAccountId = :trustAccountId
        AND t.postedAt IS NOT NULL
        AND t.bankStatementLineId IS NULL
        AND t.transactionDate >= :startDate
        AND t.transactionDate <= :endDate
      """)
  List<TrustTransaction> findUnmatchedCandidates(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

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

  /**
   * A customer's posted transactions on the client ledger in a date range, oldest first, for a
   * statement (and the interest walk): every row with {@code posted_at} set — a reversed original
   * and its {@code REVERSAL} contra included, each listed on its own date with its own effect
   * (649B, ADR-378). The callers open at the derived {@code clientBalance(start − 1)}, which sums
   * the same rows, so the closing balance always equals the client's balance at the end date.
   */
  @Query(
      """
      SELECT t FROM TrustTransaction t
      WHERE t.customerId = :customerId
        AND t.trustAccountId = :trustAccountId
        AND t.transactionDate >= :startDate
        AND t.transactionDate <= :endDate
        AND t.ledger = io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedger.CLIENT
        AND t.postedAt IS NOT NULL
      ORDER BY t.transactionDate ASC, t.createdAt ASC
      """)
  List<TrustTransaction> findForStatement(
      @Param("customerId") UUID customerId,
      @Param("trustAccountId") UUID trustAccountId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  /**
   * Returns the distinct trust account ids on which a customer has any RECORDED/APPROVED client
   * ledger activity. Used by the Statement of Account context builder to resolve the correct trust
   * account for the SoA "Trust Activity" section even when the customer's deposits are not on the
   * primary GENERAL account (GAP-L-94). Excludes REVERSAL because reversals do not by themselves
   * indicate that the customer ever held funds on the account. Client ledger only — the same ledger
   * {@link #findForStatement} reads: an approved section 86(4) placement's receive leg carries the
   * customer's id on the investment account's {@code CLIENT_INVESTMENT} ledger, and counting it
   * would let the builders select the investment account and print it with R0 and no lines (648A
   * review cycle 4).
   */
  @Query(
      """
      SELECT DISTINCT t.trustAccountId FROM TrustTransaction t
      WHERE t.customerId = :customerId
        AND t.status IN ('RECORDED', 'APPROVED')
        AND t.transactionType <> 'REVERSAL'
        AND t.ledger = io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedger.CLIENT
      ORDER BY t.trustAccountId ASC
      """)
  List<UUID> findDistinctTrustAccountIdsByCustomerId(@Param("customerId") UUID customerId);

  /**
   * 650A (§16.3.10, ADR-382): the rows entered after a monthly list was locked and dated on or
   * before its period end — posted after {@code lockedAt}, oldest posting first. The list's
   * "Entered after this list was locked" schedule.
   */
  @Query(
      """
      SELECT t FROM TrustTransaction t
      WHERE t.trustAccountId = :trustAccountId
        AND t.transactionDate <= :periodEnd
        AND t.postedAt IS NOT NULL
        AND t.postedAt > :lockedAt
      ORDER BY t.postedAt ASC, t.id ASC
      """)
  List<TrustTransaction> findPostedAfterLock(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("periodEnd") LocalDate periodEnd,
      @Param("lockedAt") Instant lockedAt);
}
