package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankStatementRepository extends JpaRepository<BankStatement, UUID> {

  List<BankStatement> findByTrustAccountIdOrderByPeriodEndDesc(UUID trustAccountId);

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
}
