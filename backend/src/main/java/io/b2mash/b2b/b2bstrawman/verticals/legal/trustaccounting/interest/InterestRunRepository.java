package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterestRunRepository extends JpaRepository<InterestRun, UUID> {

  List<InterestRun> findByTrustAccountIdOrderByPeriodEndDesc(UUID trustAccountId);

  /**
   * SELECT ... FOR UPDATE: the DRAFT check in deleteInterestRun must not race a concurrent approve
   * / calculate / post (each UPDATEs this row, so they serialise on the lock).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM InterestRun r WHERE r.id = :id")
  Optional<InterestRun> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      """
      SELECT COUNT(r) > 0 FROM InterestRun r
      WHERE r.trustAccountId = :trustAccountId
        AND r.status IN ('DRAFT', 'APPROVED', 'POSTED')
        AND r.periodStart <= :periodEnd
        AND r.periodEnd >= :periodStart
      """)
  boolean existsOverlappingRun(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("periodStart") LocalDate periodStart,
      @Param("periodEnd") LocalDate periodEnd);
}
