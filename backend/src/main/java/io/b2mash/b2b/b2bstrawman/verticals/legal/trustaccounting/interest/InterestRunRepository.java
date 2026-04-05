package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterestRunRepository extends JpaRepository<InterestRun, UUID> {

  List<InterestRun> findByTrustAccountIdOrderByPeriodEndDesc(UUID trustAccountId);

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
