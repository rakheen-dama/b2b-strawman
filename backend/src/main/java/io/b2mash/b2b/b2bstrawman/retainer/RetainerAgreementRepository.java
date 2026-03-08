package io.b2mash.b2b.b2bstrawman.retainer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetainerAgreementRepository extends JpaRepository<RetainerAgreement, UUID> {

  List<RetainerAgreement> findByStatus(RetainerStatus status);

  List<RetainerAgreement> findByCustomerId(UUID customerId);

  @Query(
      "SELECT ra FROM RetainerAgreement ra WHERE ra.customerId = :customerId"
          + " AND ra.status IN ('ACTIVE', 'PAUSED')")
  Optional<RetainerAgreement> findActiveOrPausedByCustomerId(@Param("customerId") UUID customerId);

  List<RetainerAgreement> findByCustomerIdAndStatus(UUID customerId, RetainerStatus status);

  /** Counts retainer agreements for a customer. Used by customer archive protection guard. */
  long countByCustomerId(UUID customerId);

  /**
   * Finds ACTIVE retainer agreements that have an OPEN period whose endDate falls within
   * [periodFrom, periodTo]. Used by billing run retainer preview.
   */
  @Query(
      "SELECT ra FROM RetainerAgreement ra WHERE ra.status = 'ACTIVE'"
          + " AND EXISTS (SELECT rp FROM RetainerPeriod rp"
          + " WHERE rp.agreementId = ra.id AND rp.status = 'OPEN'"
          + " AND rp.periodEnd >= :periodFrom AND rp.periodEnd <= :periodTo)")
  List<RetainerAgreement> findActiveWithDuePeriodsInRange(
      @Param("periodFrom") LocalDate periodFrom, @Param("periodTo") LocalDate periodTo);
}
