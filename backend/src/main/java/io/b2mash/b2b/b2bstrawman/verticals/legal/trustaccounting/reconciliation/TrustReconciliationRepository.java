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

public interface TrustReconciliationRepository extends JpaRepository<TrustReconciliation, UUID> {

  List<TrustReconciliation> findByTrustAccountIdOrderByPeriodEndDesc(UUID trustAccountId);

  /**
   * Latest reconciliation for an account by {@code period_end} (one reconciliation per period end
   * since V29). Used by the dashboard trust position (Phase 87, 614A).
   */
  Optional<TrustReconciliation> findFirstByTrustAccountIdOrderByPeriodEndDescCreatedAtDesc(
      UUID trustAccountId);

  /**
   * The latest reconciliation of the account with period end {@code periodEnd} — the integrity
   * check's bank side when no statement is given (649C, §16.3.9).
   */
  Optional<TrustReconciliation> findFirstByTrustAccountIdAndPeriodEndOrderByCreatedAtDesc(
      UUID trustAccountId, LocalDate periodEnd);

  /**
   * The account's last reconciliation of {@code status} by period end (completedAt breaks ties) —
   * the integrity report's last completed reconciliation (649C).
   */
  Optional<TrustReconciliation>
      findFirstByTrustAccountIdAndStatusOrderByPeriodEndDescCompletedAtDesc(
          UUID trustAccountId, ReconciliationStatus status);

  /**
   * The account's reconciliation to {@code periodEnd} (at most one — uq_trust_recon_account_period,
   * V29), row-locked (PESSIMISTIC_WRITE). Must be the first read of the row in the transaction, so
   * the entity is fresh (a later locking read does not refresh an already-loaded row):
   * createReconciliation (652C) serialises two creates for one draft, and a create waiting on a
   * completion sees it COMPLETED.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT r FROM TrustReconciliation r"
          + " WHERE r.trustAccountId = :trustAccountId AND r.periodEnd = :periodEnd")
  Optional<TrustReconciliation> findByTrustAccountIdAndPeriodEndForUpdate(
      @Param("trustAccountId") UUID trustAccountId, @Param("periodEnd") LocalDate periodEnd);

  /**
   * The reconciliation row, locked (PESSIMISTIC_WRITE). Every writer of a reconciliation — create's
   * continuation (by account and period end, above), calculate and complete — takes this row lock
   * as its FIRST lock and its first read of the row (a later locking read does not refresh an
   * already-loaded entity), so a re-point and a calculate or complete of the same draft are
   * serialised and none saves over the other's statement (652C CodeRabbit round 1). Lock order:
   * reconciliation → bank statement → trust account (D-13).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM TrustReconciliation r WHERE r.id = :id")
  Optional<TrustReconciliation> findByIdForUpdate(@Param("id") UUID id);

  /**
   * The ids of the reconciliations on statement {@code bankStatementId}, in id order (FU-6b). Ids
   * only, so no entity is loaded before the caller locks each row with {@link #findByIdForUpdate}
   * (a later locking read does not refresh an already-loaded row).
   */
  @Query(
      "SELECT r.id FROM TrustReconciliation r WHERE r.bankStatementId = :bankStatementId"
          + " ORDER BY r.id")
  List<UUID> findIdsByBankStatementId(@Param("bankStatementId") UUID bankStatementId);
}
