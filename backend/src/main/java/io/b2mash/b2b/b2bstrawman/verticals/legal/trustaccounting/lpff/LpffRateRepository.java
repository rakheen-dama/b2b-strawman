package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.lpff;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LpffRateRepository extends JpaRepository<LpffRate, UUID> {

  List<LpffRate> findByTrustAccountIdOrderByEffectiveFromDesc(UUID trustAccountId);

  Optional<LpffRate> findFirstByTrustAccountIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
      UUID trustAccountId, LocalDate asOfDate);
}
