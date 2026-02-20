package io.b2mash.b2b.b2bstrawman.retainer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RetainerPeriodRepository extends JpaRepository<RetainerPeriod, UUID> {

  Optional<RetainerPeriod> findByAgreementIdAndStatus(UUID agreementId, PeriodStatus status);

  List<RetainerPeriod> findByAgreementIdInAndStatus(List<UUID> agreementIds, PeriodStatus status);

  Page<RetainerPeriod> findByAgreementIdOrderByPeriodStartDesc(UUID agreementId, Pageable pageable);

  @Query(
      "SELECT rp FROM RetainerPeriod rp WHERE rp.status = 'OPEN' AND rp.periodEnd <= CURRENT_DATE")
  List<RetainerPeriod> findPeriodsReadyToClose();
}
