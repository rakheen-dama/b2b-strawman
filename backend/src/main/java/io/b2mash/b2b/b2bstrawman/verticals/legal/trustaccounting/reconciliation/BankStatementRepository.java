package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankStatementRepository extends JpaRepository<BankStatement, UUID> {

  List<BankStatement> findByTrustAccountIdOrderByPeriodEndDesc(UUID trustAccountId);

  /** The account's statements not in {@code status}, latest period end first (FU-6b: live list). */
  List<BankStatement> findByTrustAccountIdAndStatusNotOrderByPeriodEndDesc(
      UUID trustAccountId, String status);

  /**
   * The account's non-RETIRED statements whose end date is {@code endDate}, oldest import first
   * (FU-6b, ruling F-646C-1). A statement's end date is the one the period-fit rule uses ({@code
   * TrustReconciliationService.requireStatementFitsPeriodEnd}): its last line's date, or its {@code
   * period_end} when it has no lines. Two statements of one account with the same end date both
   * pass rule 3 ({@link BankStatementLineRepository#findOtherStatementsWithLinesBetween} looks for
   * lines strictly after the selected statement's last line), so the import refuses the second
   * unless it replaces the first.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT s.id AS statementId, s.file_name AS fileName
            FROM bank_statements s
           WHERE s.trust_account_id = :trustAccountId AND s.status <> 'RETIRED'
             AND COALESCE((SELECT MAX(l.transaction_date) FROM bank_statement_lines l
                            WHERE l.bank_statement_id = s.id), s.period_end) = :endDate
           ORDER BY s.created_at, s.id""")
  List<StatementRef> findLiveStatementsEndingOn(
      @Param("trustAccountId") UUID trustAccountId, @Param("endDate") LocalDate endDate);

  /**
   * FU-6b: serialises the statement imports of one trust account until the caller's transaction
   * ends ({@code pg_advisory_xact_lock}, keyed by tenant schema and account). The import takes it
   * just before its same-end-date check, so two imports of one account cannot both pass the check;
   * nothing but an import takes it, so no posting waits on an import (the account row is not
   * locked). Must run inside the import's transaction. Returns 1.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(
              'bank_stmt_import:' || current_schema() || ':' || CAST(:accountId AS text), 0))
          """)
  int lockImportsOfAccount(@Param("accountId") UUID accountId);

  /** A statement's id and file name (a row of {@link #findLiveStatementsEndingOn}). */
  interface StatementRef {
    UUID getStatementId();

    String getFileName();
  }

  /**
   * Locks the statement row (PESSIMISTIC_WRITE) for a read-modify-write of its {@code
   * matched_count}: two members matching, recording or unmatching lines of one statement are
   * serialised, so neither overwrites the other's count. Must be the first read of the statement in
   * the transaction (a later locking read does not refresh an already-loaded row) and is always
   * taken after the line lock (line-then-statement order). Auto-match, which needs the statement's
   * period before it locks anything, reads it plainly and later locks it with a locking {@code
   * refresh} instead (CF-3).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM BankStatement s WHERE s.id = :id")
  Optional<BankStatement> findByIdForUpdate(@Param("id") UUID id);

  /**
   * Share-locks the statement row (PESSIMISTIC_READ, {@code FOR SHARE}) — createReconciliation's
   * read of the statement it points a reconciliation at (FU-6b), taken after the reconciliation
   * row. A replace or discard locks the row {@code FOR UPDATE}, so the two are serialised: a create
   * that commits first is seen by the retire, which then refuses; a create that waits finds the
   * statement RETIRED. Two creates (which change nothing on the statement) do not wait on each
   * other here. Must be the first read of the statement in the transaction.
   */
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("SELECT s FROM BankStatement s WHERE s.id = :id")
  Optional<BankStatement> findByIdForShare(@Param("id") UUID id);
}
