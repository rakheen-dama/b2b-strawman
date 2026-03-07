package io.b2mash.b2b.b2bstrawman.capacity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceAllocationRepository extends JpaRepository<ResourceAllocation, UUID> {

  List<ResourceAllocation> findByMemberIdAndWeekStartBetween(
      UUID memberId, LocalDate start, LocalDate end);

  List<ResourceAllocation> findByProjectIdAndWeekStartBetween(
      UUID projectId, LocalDate start, LocalDate end);

  List<ResourceAllocation> findByWeekStartBetween(LocalDate start, LocalDate end);

  @Query(
      """
      SELECT COALESCE(SUM(ra.allocatedHours), 0)
      FROM ResourceAllocation ra
      WHERE ra.memberId = :memberId
        AND ra.weekStart = :weekStart
      """)
  BigDecimal sumAllocatedHoursForMemberWeek(
      @Param("memberId") UUID memberId, @Param("weekStart") LocalDate weekStart);

  Optional<ResourceAllocation> findByMemberIdAndProjectIdAndWeekStart(
      UUID memberId, UUID projectId, LocalDate weekStart);
}
