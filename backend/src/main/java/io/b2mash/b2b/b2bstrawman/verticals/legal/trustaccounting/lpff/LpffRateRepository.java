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

  @Query(
      """
      SELECT r FROM LpffRate r
      WHERE r.trustAccountId = :accountId
        AND r.effectiveFrom <= :asOfDate
      ORDER BY r.effectiveFrom DESC
      LIMIT 1
      """)
  Optional<LpffRate> findEffectiveRate(
      @Param("accountId") UUID accountId, @Param("asOfDate") LocalDate asOfDate);
}
