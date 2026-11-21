package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, UUID> {

  List<BankStatementLine> findByBankStatementIdOrderByLineNumber(UUID bankStatementId);

  List<BankStatementLine> findByMatchStatus(String matchStatus);

  /**
   * Locks the line row (PESSIMISTIC_WRITE) before its match state is read, for every state change
   * on one line — record from statement, manual match, exclude and unmatch (647A). Two members
   * acting on the same line are serialised: the second waits for the first's commit and then sees
   * its state (and is refused if the line is no longer {@code UNMATCHED} / matched), so no stale
   * write overwrites a recorded, matched or excluded line. Taken before the statement lock. Auto-
   * match locks all of its statement's {@code UNMATCHED} lines at once instead ({@link
   * #findUnmatchedByStatementIdForUpdate}).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT l FROM BankStatementLine l WHERE l.id = :id")
  Optional<BankStatementLine> findByIdForUpdate(@Param("id") UUID id);

  /**
   * Auto-match's first lock (CF-3): every {@code UNMATCHED} line of the statement, row-locked
   * (PESSIMISTIC_WRITE) in one query, in matching order (date, then id — a fixed order, so two
   * auto-matches of one statement lock its lines in the same order). Must be the first read of
   * these lines in the transaction. Postgres re-checks the predicate on a row it waited for, so a
   * line a concurrent record, manual match or exclude took while auto-match waited is not returned.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT l FROM BankStatementLine l
      WHERE l.bankStatementId = :statementId AND l.matchStatus = 'UNMATCHED'
      ORDER BY l.transactionDate ASC, l.id ASC
      """)
  List<BankStatementLine> findUnmatchedByStatementIdForUpdate(
      @Param("statementId") UUID statementId);

  /**
   * FU-6b replace / discard: every line of the statement, row-locked (PESSIMISTIC_WRITE) in one
   * query, in auto-match's order (date, then id — {@link #findUnmatchedByStatementIdForUpdate}
   * locks a subset of these rows in the same order, so the two never lock two lines crosswise).
   * Taken after the reconciliation rows and before any trust transaction and the statement (line →
   * transaction → statement). Must be the first read of these lines in the transaction.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT l FROM BankStatementLine l
      WHERE l.bankStatementId = :statementId
      ORDER BY l.transactionDate ASC, l.id ASC
      """)
  List<BankStatementLine> findByStatementIdForUpdate(@Param("statementId") UUID statementId);

  /** The date of the statement's last line; empty for a statement with no lines (646C). */
  @Query(
      "SELECT MAX(l.transactionDate) FROM BankStatementLine l WHERE l.bankStatementId ="
          + " :statementId")
  Optional<LocalDate> findLastLineDate(@Param("statementId") UUID statementId);

  /**
   * File names of the account's other statements with a line dated in {@code (after, upTo]}, oldest
   * import first — §16.3.4's "another imported statement covers the gap" rule (646C). A RETIRED
   * statement (replaced or discarded, FU-6b) never covers a gap.
   */
  @Query(
      """
      SELECT s.fileName FROM BankStatement s
      WHERE s.trustAccountId = :trustAccountId AND s.id <> :statementId
        AND s.status <> 'RETIRED'
        AND EXISTS (SELECT l.id FROM BankStatementLine l WHERE l.bankStatementId = s.id
                      AND l.transactionDate > :after AND l.transactionDate <= :upTo)
      ORDER BY s.createdAt, s.id
      """)
  List<String> findOtherStatementsWithLinesBetween(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("statementId") UUID statementId,
      @Param("after") LocalDate after,
      @Param("upTo") LocalDate upTo);

  long countByBankStatementIdAndMatchStatus(UUID bankStatementId, String matchStatus);

  /** The signed total of the statement's excluded lines (an explanatory figure; 646C). */
  @Query(
      """
      SELECT COALESCE(SUM(l.amount), 0) FROM BankStatementLine l
      WHERE l.bankStatementId = :statementId AND l.matchStatus = 'EXCLUDED'
      """)
  BigDecimal sumExcludedAmount(@Param("statementId") UUID statementId);

  /**
   * The account's imported statements not yet {@code RECONCILED} that hold {@code UNMATCHED} lines,
   * with the count of such lines, oldest import first — {@code UNRESOLVED_STATEMENT_LINES} on the
   * dashboard (649C, §16.3.9). A RETIRED statement (replaced or discarded, FU-6b) is not counted.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT s.id AS statementId, s.file_name AS fileName, COUNT(l.id) AS unresolvedCount
            FROM bank_statements s
            JOIN bank_statement_lines l ON l.bank_statement_id = s.id
           WHERE s.trust_account_id = :trustAccountId AND s.status NOT IN ('RECONCILED', 'RETIRED')
             AND l.match_status = 'UNMATCHED'
           GROUP BY s.id, s.file_name, s.created_at
           ORDER BY s.created_at, s.id""")
  List<UnresolvedStatementRow> findUnresolvedInUnreconciledStatements(
      @Param("trustAccountId") UUID trustAccountId);
}
