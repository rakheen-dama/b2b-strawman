package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Every derived trust balance (Phase 92, ADR-378, architecture §16.3.2): sums over the
 * posting-effect columns of posted rows ({@code posted_at IS NOT NULL}). No type name appears in
 * this SQL — a row's sign and ledger were fixed by {@link TrustPostingService} from {@link
 * TrustTransactionType} when it was posted.
 *
 * <p>{@code asAt} is nullable: null = every posted row regardless of date (the "current" figure the
 * stored card and the undated queries gave on HEAD, future-dated rows included — T2), a date = as
 * at that SA business date. The bind is cast on both occurrences so Postgres can type a null.
 * Callers pass {@code LocalDate.now(clock)} when they mean today, never {@code CURRENT_DATE}.
 * Index: {@code idx_trust_txn_ledger_sum} (V16).
 */
public interface TrustLedgerQueryRepository extends Repository<TrustTransaction, UUID> {

  /** The optional as-at bound, appended to a WHERE clause. */
  String AS_AT = " AND (CAST(:asAt AS date) IS NULL OR t.transaction_date <= CAST(:asAt AS date))";

  /** The optional as-at bound as a bare predicate (inside a FILTER clause). */
  String IN_RANGE = "(CAST(:asAt AS date) IS NULL OR t.transaction_date <= CAST(:asAt AS date))";

  /** The account's cashbook: what the bank should hold for it. */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT COALESCE(SUM(t.cashbook_effect), 0) FROM trust_transactions t
           WHERE t.trust_account_id = :trustAccountId AND t.posted_at IS NOT NULL"""
              + AS_AT)
  BigDecimal cashbook(@Param("trustAccountId") UUID trustAccountId, @Param("asAt") LocalDate asAt);

  /** The total of the client ledgers on the account ({@code CLIENT} ledger). */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT COALESCE(SUM(t.ledger_effect), 0) FROM trust_transactions t
           WHERE t.trust_account_id = :trustAccountId AND t.ledger = 'CLIENT'
             AND t.posted_at IS NOT NULL"""
              + AS_AT)
  BigDecimal clientLedgerTotal(
      @Param("trustAccountId") UUID trustAccountId, @Param("asAt") LocalDate asAt);

  /** One client's balance on one account. */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT COALESCE(SUM(t.ledger_effect), 0) FROM trust_transactions t
           WHERE t.trust_account_id = :trustAccountId AND t.customer_id = :customerId
             AND t.ledger = 'CLIENT' AND t.posted_at IS NOT NULL"""
              + AS_AT)
  BigDecimal clientBalance(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("customerId") UUID customerId,
      @Param("asAt") LocalDate asAt);

  /**
   * One non-client creditor account's balance (ADR-376): SUM(ledger_effect) over its posted {@code
   * NON_CLIENT} rows. A Fund ledger may be in debit (founder decision 9, ADR-381). Index: {@code
   * idx_trust_txn_creditor_account} (V18).
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT COALESCE(SUM(t.ledger_effect), 0) FROM trust_transactions t
           WHERE t.creditor_account_id = :creditorAccountId AND t.ledger = 'NON_CLIENT'
             AND t.posted_at IS NOT NULL"""
              + AS_AT)
  BigDecimal creditorAccountBalance(
      @Param("creditorAccountId") UUID creditorAccountId, @Param("asAt") LocalDate asAt);

  /**
   * One non-client creditor account's rows, newest first ({@code transaction_date DESC, created_at
   * DESC, id DESC}), each carrying its running balance (Phase 92 / 647C, §16.4.2; ADR-361 window,
   * ADR-378 effects): SUM(ledger_effect) over the account's POSTED {@code NON_CLIENT} rows ({@code
   * posted_at IS NOT NULL} — the same row set as {@link #creditorAccountBalance}, so a REVERSED
   * original and its REVERSAL contra each count on their own date and the newest row's balance is
   * the ledger balance) in posting order {@code (transaction_date, created_at, id)}, computed in a
   * MATERIALIZED CTE before the page is cut (see {@code
   * TrustTransactionRepository#findClientHistoryWithRunningBalance} for why it is materialised);
   * unposted rows (awaiting approval, rejected) carry null. Callers pass a sort-free {@link
   * Pageable} — the ORDER BY lives here. Index: {@code idx_trust_txn_creditor_account} (V18).
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
             WHERE t.creditor_account_id = :creditorAccountId AND t.ledger = 'NON_CLIENT'
               AND t.posted_at IS NOT NULL
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
                              AND r.reversal_of IS NOT NULL)) AS reversalPending,
                 t.charge_recoverable AS chargeRecoverable, t.charge_vat_amount AS chargeVatAmount
            FROM trust_transactions t
            LEFT JOIN posted p ON p.id = t.id
            LEFT JOIN trust_transactions o ON o.id = t.reversal_of
           WHERE t.creditor_account_id = :creditorAccountId AND t.ledger = 'NON_CLIENT'
           ORDER BY t.transaction_date DESC, t.created_at DESC, t.id DESC""",
      countQuery =
          """
          SELECT count(*) FROM trust_transactions t
           WHERE t.creditor_account_id = :creditorAccountId AND t.ledger = 'NON_CLIENT'""")
  Page<CreditorLedgerRow> creditorLedgerRows(
      @Param("creditorAccountId") UUID creditorAccountId, Pageable pageable);

  /**
   * The matter sub-ledger cell (G89-F12): one client's money on one matter in one account. The
   * caller holds the client's lock anchor, which serialises every posting for the client.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT COALESCE(SUM(t.ledger_effect), 0) FROM trust_transactions t
           WHERE t.trust_account_id = :trustAccountId AND t.customer_id = :customerId
             AND t.project_id = :projectId AND t.ledger = 'CLIENT'
             AND t.posted_at IS NOT NULL"""
              + AS_AT)
  BigDecimal matterCellBalance(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("customerId") UUID customerId,
      @Param("projectId") UUID projectId,
      @Param("asAt") LocalDate asAt);

  /**
   * One client's balance per matter (a null {@code projectId} = the unmattered rows); the rows sum
   * exactly to {@link #clientBalance}. The opening balances of the per-matter interest buckets
   * (ADR-366).
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT t.project_id AS projectId, COALESCE(SUM(t.ledger_effect), 0) AS balance
            FROM trust_transactions t
           WHERE t.trust_account_id = :trustAccountId AND t.customer_id = :customerId
             AND t.ledger = 'CLIENT' AND t.posted_at IS NOT NULL"""
              + AS_AT
              + """

           GROUP BY t.project_id""")
  List<MatterBalanceRow> clientMatterBalances(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("customerId") UUID customerId,
      @Param("asAt") LocalDate asAt);

  /**
   * Every client with a posted {@code CLIENT} row on the account — membership is date-free, so a
   * client is listed exactly when it would have had a card (T7) — with its balance and last posted
   * date as at {@code asAt}. The client-trust-balances report.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT t.customer_id AS customerId,
                 COALESCE(SUM(t.ledger_effect) FILTER (WHERE """
              + IN_RANGE
              + """
                 ), 0) AS balance,
                 MAX(t.transaction_date) FILTER (WHERE """
              + IN_RANGE
              + """
                 ) AS lastTransactionDate
            FROM trust_transactions t
           WHERE t.trust_account_id = :trustAccountId AND t.ledger = 'CLIENT'
             AND t.customer_id IS NOT NULL AND t.posted_at IS NOT NULL
           GROUP BY t.customer_id
           ORDER BY MIN(t.created_at), t.customer_id""")
  List<ClientBalanceRow> clientBalances(
      @Param("trustAccountId") UUID trustAccountId, @Param("asAt") LocalDate asAt);

  /**
   * The client-ledgers list page: balance descending (the UI's {@code sort=balance,desc} — T4),
   * paged in SQL. Callers pass a sort-free {@code PageRequest.of(page, size)}: the ORDER BY lives
   * here.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT t.customer_id AS customerId, COALESCE(SUM(t.ledger_effect), 0) AS balance,
                 MAX(t.transaction_date) AS lastTransactionDate
            FROM trust_transactions t
           WHERE t.trust_account_id = :trustAccountId AND t.ledger = 'CLIENT'
             AND t.customer_id IS NOT NULL AND t.posted_at IS NOT NULL
           GROUP BY t.customer_id
           ORDER BY balance DESC, t.customer_id""",
      countQuery =
          """
          SELECT count(DISTINCT t.customer_id) FROM trust_transactions t
           WHERE t.trust_account_id = :trustAccountId AND t.ledger = 'CLIENT'
             AND t.customer_id IS NOT NULL AND t.posted_at IS NOT NULL""")
  Page<ClientBalanceRow> clientBalancePage(
      @Param("trustAccountId") UUID trustAccountId, Pageable pageable);

  /** One client on one account (the client-ledger card): empty when it has no posted row. */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT t.customer_id AS customerId, COALESCE(SUM(t.ledger_effect), 0) AS balance,
                 MAX(t.transaction_date) AS lastTransactionDate
            FROM trust_transactions t
           WHERE t.trust_account_id = :trustAccountId AND t.customer_id = :customerId
             AND t.ledger = 'CLIENT' AND t.posted_at IS NOT NULL
           GROUP BY t.customer_id""")
  Optional<ClientBalanceRow> clientSummary(
      @Param("trustAccountId") UUID trustAccountId, @Param("customerId") UUID customerId);

  /**
   * Posted {@code CLIENT}-ledger effect per client and transaction type on one account, as at
   * {@code asAt} (G89-F13): the rows {@code LedgerTotals} folds into the client-ledger totals. A
   * REVERSAL row is grouped under the type of the row it reverses, so a reversal nets against the
   * total it undoes; per client the rows therefore sum exactly to {@link #clientBalance} for the
   * same {@code asAt}. (646A: a reversed original and its REVERSAL both carry zero effect, so the
   * reversed amount drops out of its type's total, as under HEAD's status filter.) {@code total} is
   * the signed {@code ledger_effect} sum. Callers short-circuit an empty collection.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT t.customer_id AS customerId,
                 COALESCE(o.transaction_type, t.transaction_type) AS transactionType,
                 COALESCE(SUM(t.ledger_effect), 0) AS total
            FROM trust_transactions t
            LEFT JOIN trust_transactions o ON o.id = t.reversal_of
           WHERE t.trust_account_id = :trustAccountId AND t.customer_id IN (:customerIds)
             AND t.ledger = 'CLIENT' AND t.posted_at IS NOT NULL"""
              + AS_AT
              + """

           GROUP BY t.customer_id, COALESCE(o.transaction_type, t.transaction_type)""")
  List<CustomerTypeTotalRow> clientTotalsByType(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("customerIds") Collection<UUID> customerIds,
      @Param("asAt") LocalDate asAt);

  /**
   * 648E (§16.2.8, ADR-379 L7): per client, what is held for them in section 86(4) investments
   * placed <em>from</em> {@code fundingAccountId} — the posted {@code CLIENT_INVESTMENT} effect on
   * the investment account holding each such investment (the {@link #investmentBalance} of every
   * client investment funded from the account). With the client's card on the funding account it
   * makes the client's total held in trust: general ({@code CLIENT}) + invested, unchanged by a
   * placement. Nothing is placed from an investment account, so there it is empty. Undated, like
   * {@link #clientSummary}. Callers short-circuit an empty collection ({@code IN ()} is invalid
   * SQL).
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT t.customer_id AS customerId, COALESCE(SUM(t.ledger_effect), 0) AS balance,
                 MAX(t.transaction_date) AS lastTransactionDate
            FROM trust_transactions t
            JOIN trust_investments i ON i.id = t.investment_id
           WHERE i.trust_account_id = :fundingAccountId
             AND t.trust_account_id = i.investment_account_id
             AND t.customer_id IN (:customerIds)
             AND t.ledger = 'CLIENT_INVESTMENT' AND t.posted_at IS NOT NULL
           GROUP BY t.customer_id""")
  List<ClientBalanceRow> investedBalancesByClient(
      @Param("fundingAccountId") UUID fundingAccountId,
      @Param("customerIds") Collection<UUID> customerIds);

  /**
   * The matters and clients lists: each client's general ({@code CLIENT} ledger) balance across
   * every trust account. Callers short-circuit an empty collection ({@code IN ()} is invalid SQL).
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT t.customer_id AS customerId, COALESCE(SUM(t.ledger_effect), 0) AS balance,
                 MAX(t.transaction_date) AS lastTransactionDate
            FROM trust_transactions t
           WHERE t.customer_id IN (:customerIds) AND t.ledger = 'CLIENT'
             AND t.posted_at IS NOT NULL
           GROUP BY t.customer_id""")
  List<ClientBalanceRow> generalBalancesByClient(
      @Param("customerIds") Collection<UUID> customerIds);

  /**
   * One client's general ({@code CLIENT} ledger) balance across every trust account — {@code
   * TrustBoundaryGuard} and {@code TrustAwareCollectionsAdvisor}. Invested money is not available
   * to pay a fee note, so it is not added (§16.3.2 consumer table).
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT COALESCE(SUM(t.ledger_effect), 0) FROM trust_transactions t
           WHERE t.customer_id = :customerId AND t.ledger = 'CLIENT'
             AND t.posted_at IS NOT NULL""")
  BigDecimal generalBalance(@Param("customerId") UUID customerId);

  /**
   * The money held for a matter: {@code CLIENT} + {@code CLIENT_INVESTMENT} (§16.3.2) — the matter
   * closure gate {@code TRUST_BALANCE_ZERO} and the matter trust tile. A matter with money still
   * invested cannot be closed as "nil trust balance".
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT COALESCE(SUM(t.ledger_effect), 0) FROM trust_transactions t
           WHERE t.project_id = :projectId AND t.ledger IN ('CLIENT', 'CLIENT_INVESTMENT')
             AND t.posted_at IS NOT NULL""")
  BigDecimal matterBalance(@Param("projectId") UUID projectId);

  /**
   * Every book-side term of EQ-2 for one account as at {@code asAt} (Phase 92 646C, §16.3.3,
   * ADR-377): the cashbook and the six creditor terms. {@code otherNonClient} is always zero in
   * this phase; it is a term so a future creditor type is counted from its first row. {@code asAt}
   * is required. Index: {@code idx_trust_txn_ledger_sum} (V16).
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT
            COALESCE(SUM(t.cashbook_effect), 0) AS cashbook,
            COALESCE(SUM(t.ledger_effect) FILTER (WHERE t.ledger = 'CLIENT'), 0) AS clientLedgers,
            COALESCE(SUM(t.ledger_effect) FILTER (WHERE t.ledger = 'CLIENT_INVESTMENT'), 0)
              AS investmentCreditors,
            COALESCE(SUM(t.ledger_effect) FILTER (WHERE t.ledger = 'NON_CLIENT'
                                                    AND ca.account_type = 'FUND_INTEREST_AND_CHARGES'), 0)
              AS fundLedger,
            COALESCE(SUM(t.ledger_effect) FILTER (WHERE t.ledger = 'NON_CLIENT'
                                                    AND ca.account_type = 'UNIDENTIFIED_DEPOSITS'), 0)
              AS unidentified,
            COALESCE(SUM(t.ledger_effect) FILTER (WHERE t.ledger = 'NON_CLIENT'
                                                    AND ca.account_type NOT IN ('FUND_INTEREST_AND_CHARGES',
                                                                                'UNIDENTIFIED_DEPOSITS')), 0)
              AS otherNonClient,
            COALESCE(SUM(t.ledger_effect) FILTER (WHERE t.ledger = 'INTER_ACCOUNT'), 0) AS interAccount
          FROM trust_transactions t
          LEFT JOIN trust_creditor_accounts ca ON ca.id = t.creditor_account_id
          WHERE t.trust_account_id = :trustAccountId
            AND t.posted_at IS NOT NULL
            AND t.transaction_date <= CAST(:asAt AS date)
          """)
  TrustPositionRow position(
      @Param("trustAccountId") UUID trustAccountId, @Param("asAt") LocalDate asAt);

  /**
   * Outstanding items as at {@code asAt} (646C, §16.3.3, ADR-377): posted rows that move the
   * cashbook, dated on or before {@code asAt}, not matched to a statement line dated on or before
   * {@code asAt} — a match is dated by the LINE's date, never by when it was made. A reversed row
   * and its posted reversal, neither on a statement line, cancel and are not outstanding.
   * Cashbook-neutral rows never reach the bank and are never outstanding. Replaces the old
   * status/type-list pair (649B F4). {@code asAt} is required.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT
            COALESCE( SUM(t.cashbook_effect) FILTER (WHERE t.cashbook_effect > 0), 0) AS outstandingDeposits,
            COALESCE(-SUM(t.cashbook_effect) FILTER (WHERE t.cashbook_effect < 0), 0) AS outstandingPayments
          FROM trust_transactions t
          LEFT JOIN bank_statement_lines l ON l.id = t.bank_statement_line_id
          WHERE t.trust_account_id = :trustAccountId
            AND t.posted_at IS NOT NULL
            AND t.cashbook_effect <> 0
            AND t.transaction_date <= CAST(:asAt AS date)
            AND (t.bank_statement_line_id IS NULL OR l.transaction_date > CAST(:asAt AS date))
            AND NOT (t.bank_statement_line_id IS NULL AND t.reversed_by_id IS NOT NULL AND EXISTS (
                       SELECT 1 FROM trust_transactions r
                        WHERE r.id = t.reversed_by_id AND r.posted_at IS NOT NULL
                          AND r.bank_statement_line_id IS NULL
                          AND r.transaction_date <= CAST(:asAt AS date)))
            AND NOT (t.bank_statement_line_id IS NULL AND t.reversal_of IS NOT NULL AND EXISTS (
                       SELECT 1 FROM trust_transactions o
                        WHERE o.id = t.reversal_of AND o.bank_statement_line_id IS NULL))
          """)
  OutstandingItemsRow outstanding(
      @Param("trustAccountId") UUID trustAccountId, @Param("asAt") LocalDate asAt);

  /**
   * An investment's balance (648A, §16.3.7): its posted rows on the account holding it — the {@code
   * CLIENT_INVESTMENT} ledger for a section 86(4) investment (capital plus every net interest
   * entry, less the withdrawal), the {@code INTER_ACCOUNT} ledger for a section 86(3) one (its
   * principal while held). The Fund's share ({@code NON_CLIENT}) is not the investment's. What a
   * withdrawal returns; zero once withdrawn.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT COALESCE(SUM(t.ledger_effect), 0) FROM trust_transactions t
           WHERE t.investment_id = :investmentId AND t.trust_account_id = :investmentAccountId
             AND t.ledger IN ('CLIENT_INVESTMENT', 'INTER_ACCOUNT') AND t.posted_at IS NOT NULL""")
  BigDecimal investmentBalance(
      @Param("investmentId") UUID investmentId,
      @Param("investmentAccountId") UUID investmentAccountId);

  /**
   * {@link #investmentBalance} for a page of investments at once (an investment with no row: 0).
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT i.id AS investmentId, COALESCE(SUM(t.ledger_effect), 0) AS balance
            FROM trust_investments i
            LEFT JOIN trust_transactions t ON t.investment_id = i.id
                  AND t.trust_account_id = i.investment_account_id
                  AND t.ledger IN ('CLIENT_INVESTMENT', 'INTER_ACCOUNT')
                  AND t.posted_at IS NOT NULL
           WHERE i.id IN (:investmentIds)
           GROUP BY i.id""")
  List<InvestmentBalanceRow> investmentBalances(
      @Param("investmentIds") Collection<UUID> investmentIds);
}
