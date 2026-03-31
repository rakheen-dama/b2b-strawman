package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrescriptionTrackerRepository extends JpaRepository<PrescriptionTracker, UUID> {

  List<PrescriptionTracker> findByProjectIdOrderByPrescriptionDateAsc(UUID projectId);

  List<PrescriptionTracker> findByStatusInAndPrescriptionDateBetween(
      List<String> statuses, LocalDate from, LocalDate to);

  List<PrescriptionTracker> findByStatusInAndPrescriptionDateLessThanEqual(
      List<String> statuses, LocalDate to);

  @Query(
      """
      SELECT p FROM PrescriptionTracker p
      WHERE (:status IS NULL OR p.status = :status)
        AND (:customerId IS NULL OR p.customerId = :customerId)
        AND (:projectId IS NULL OR p.projectId = :projectId)
      ORDER BY p.prescriptionDate ASC
      """)
  Page<PrescriptionTracker> findByFilters(
      @Param("status") String status,
      @Param("customerId") UUID customerId,
      @Param("projectId") UUID projectId,
      Pageable pageable);
}
