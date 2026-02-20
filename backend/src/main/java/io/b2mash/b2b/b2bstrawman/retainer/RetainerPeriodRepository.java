package io.b2mash.b2b.b2bstrawman.retainer;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetainerPeriodRepository extends JpaRepository<RetainerPeriod, UUID> {

  Optional<RetainerPeriod> findByAgreementIdAndStatus(UUID agreementId, PeriodStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT rp FROM RetainerPeriod rp WHERE rp.agreementId = :agreementId AND rp.status = :status")
  Optional<RetainerPeriod> findByAgreementIdAndStatusForUpdate(
      @Param("agreementId") UUID agreementId, @Param("status") PeriodStatus status);

  List<RetainerPeriod> findByAgreementIdInAndStatus(List<UUID> agreementIds, PeriodStatus status);

  Page<RetainerPeriod> findByAgreementIdOrderByPeriodStartDesc(UUID agreementId, Pageable pageable);

  @Query(
      "SELECT rp FROM RetainerPeriod rp WHERE rp.status = 'OPEN' AND rp.periodEnd <= CURRENT_DATE")
  List<RetainerPeriod> findPeriodsReadyToClose();

  @Query(
      nativeQuery = true,
      value =
          """
          SELECT COALESCE(SUM(te.duration_minutes), 0)
          FROM time_entries te
            JOIN tasks t ON t.id = te.task_id
            JOIN customer_projects cp ON cp.project_id = t.project_id
          WHERE cp.customer_id = :customerId
            AND te.billable = true
            AND te.date >= :periodStart
            AND te.date < :periodEnd
          """)
  long sumConsumedMinutes(
      @Param("customerId") UUID customerId,
      @Param("periodStart") LocalDate periodStart,
      @Param("periodEnd") LocalDate periodEnd);
}
