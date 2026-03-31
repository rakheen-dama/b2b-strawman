package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourtDateRepository extends JpaRepository<CourtDate, UUID> {

  List<CourtDate> findByProjectIdOrderByScheduledDateAsc(UUID projectId);

  List<CourtDate> findByCustomerIdOrderByScheduledDateAsc(UUID customerId);

  @Query(
      """
      SELECT c FROM CourtDate c
      WHERE (CAST(:dateFrom AS date) IS NULL OR c.scheduledDate >= :dateFrom)
        AND (CAST(:dateTo AS date) IS NULL OR c.scheduledDate <= :dateTo)
        AND (:dateType IS NULL OR c.dateType = :dateType)
        AND (:status IS NULL OR c.status = :status)
        AND (:customerId IS NULL OR c.customerId = :customerId)
        AND (:projectId IS NULL OR c.projectId = :projectId)
      ORDER BY c.scheduledDate ASC
      """)
  Page<CourtDate> findByFilters(
      @Param("dateFrom") LocalDate dateFrom,
      @Param("dateTo") LocalDate dateTo,
      @Param("dateType") String dateType,
      @Param("status") String status,
      @Param("customerId") UUID customerId,
      @Param("projectId") UUID projectId,
      Pageable pageable);

  List<CourtDate> findByStatusInAndScheduledDateBetween(
      List<String> statuses, LocalDate from, LocalDate to);
}
