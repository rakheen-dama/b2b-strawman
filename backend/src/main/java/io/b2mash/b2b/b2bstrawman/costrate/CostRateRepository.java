package io.b2mash.b2b.b2bstrawman.costrate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CostRateRepository extends JpaRepository<CostRate, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT cr FROM CostRate cr WHERE cr.id = :id")
  Optional<CostRate> findOneById(@Param("id") UUID id);

  /**
   * Finds cost rates for a member effective on the given date. Returns results ordered by
   * effectiveFrom DESC so the most recent applicable rate is first.
   */
  @Query(
      """
      SELECT cr FROM CostRate cr
      WHERE cr.memberId = :memberId
        AND cr.effectiveFrom <= :date
        AND (cr.effectiveTo IS NULL OR cr.effectiveTo >= :date)
      ORDER BY cr.effectiveFrom DESC
      """)
  List<CostRate> findByMemberIdAndDate(
      @Param("memberId") UUID memberId, @Param("date") LocalDate date);

  /**
   * Finds cost rates that overlap with the given date range for the same member. Used for overlap
   * validation before create/update.
   *
   * <p>For create operations, pass a random UUID as excludeId. For update operations, pass the
   * existing rate's ID to exclude it from the overlap check.
   */
  @Query(
      """
      SELECT cr FROM CostRate cr
      WHERE cr.memberId = :memberId
        AND cr.id != :excludeId
        AND cr.effectiveFrom <= :endDate
        AND (cr.effectiveTo IS NULL OR cr.effectiveTo >= :startDate)
      """)
  List<CostRate> findOverlapping(
      @Param("memberId") UUID memberId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("excludeId") UUID excludeId);

  /** Lists all cost rates for a member, ordered by effectiveFrom DESC. */
  @Query(
      """
      SELECT cr FROM CostRate cr
      WHERE cr.memberId = :memberId
      ORDER BY cr.effectiveFrom DESC
      """)
  List<CostRate> findByMemberId(@Param("memberId") UUID memberId);

  /** Lists all cost rates, ordered by effectiveFrom DESC. */
  @Query(
      """
      SELECT cr FROM CostRate cr
      ORDER BY cr.effectiveFrom DESC
      """)
  List<CostRate> findAllOrderByEffectiveFromDesc();
}
