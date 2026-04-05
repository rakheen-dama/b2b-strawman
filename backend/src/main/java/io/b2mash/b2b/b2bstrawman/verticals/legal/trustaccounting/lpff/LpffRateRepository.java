package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.lpff;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LpffRateRepository extends JpaRepository<LpffRate, UUID> {

  List<LpffRate> findByTrustAccountIdOrderByEffectiveFromDesc(UUID trustAccountId);

  Optional<LpffRate> findFirstByTrustAccountIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
      UUID trustAccountId, LocalDate asOfDate);

  @Query(
      """
      SELECT r FROM LpffRate r
      WHERE r.trustAccountId = :trustAccountId
        AND r.effectiveFrom > :periodStart
        AND r.effectiveFrom <= :periodEnd
      ORDER BY r.effectiveFrom ASC
      """)
  List<LpffRate> findRateChangesInPeriod(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("periodStart") LocalDate periodStart,
      @Param("periodEnd") LocalDate periodEnd);
}
