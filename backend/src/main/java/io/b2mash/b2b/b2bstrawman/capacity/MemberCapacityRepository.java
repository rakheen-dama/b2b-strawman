package io.b2mash.b2b.b2bstrawman.capacity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberCapacityRepository extends JpaRepository<MemberCapacity, UUID> {

  @Query(
      """
      SELECT mc FROM MemberCapacity mc
      WHERE mc.memberId = :memberId
        AND mc.effectiveFrom <= :date
        AND (mc.effectiveTo IS NULL OR mc.effectiveTo >= :date)
      ORDER BY mc.effectiveFrom DESC
      """)
  List<MemberCapacity> findEffectiveCapacity(
      @Param("memberId") UUID memberId, @Param("date") LocalDate date);

  List<MemberCapacity> findByMemberIdOrderByEffectiveFromDesc(UUID memberId);

  /** Batch-fetches all capacity records effective within the given date range. */
  @Query(
      """
      SELECT mc FROM MemberCapacity mc
      WHERE mc.effectiveFrom <= :end
        AND (mc.effectiveTo IS NULL OR mc.effectiveTo >= :start)
      ORDER BY mc.memberId, mc.effectiveFrom DESC
      """)
  List<MemberCapacity> findAllEffectiveInRange(
      @Param("start") LocalDate start, @Param("end") LocalDate end);
}
