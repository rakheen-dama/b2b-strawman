package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustReconciliationRepository extends JpaRepository<TrustReconciliation, UUID> {

  List<TrustReconciliation> findByTrustAccountIdOrderByPeriodEndDesc(UUID trustAccountId);

  /**
   * Latest reconciliation for an account by {@code period_end} (createdAt breaks ties — a DRAFT and
   * a COMPLETED can share a period end). Used by the dashboard trust position (Phase 87, 614A).
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
}
