package io.b2mash.b2b.b2bstrawman.billingrate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingRateRepository extends JpaRepository<BillingRate, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT br FROM BillingRate br WHERE br.id = :id")
  Optional<BillingRate> findOneById(@Param("id") UUID id);

  /**
   * Finds project-level override rates for a member on a specific project, effective on the given
   * date. Returns results ordered by effectiveFrom DESC so the most recent applicable rate is
   * first.
   */
  @Query(
      """
      SELECT br FROM BillingRate br
      WHERE br.memberId = :memberId
        AND br.projectId = :projectId
        AND br.customerId IS NULL
        AND br.effectiveFrom <= :date
        AND (br.effectiveTo IS NULL OR br.effectiveTo >= :date)
      ORDER BY br.effectiveFrom DESC
      """)
  List<BillingRate> findProjectOverride(
      @Param("memberId") UUID memberId,
      @Param("projectId") UUID projectId,
      @Param("date") LocalDate date);

  /**
   * Finds customer-level override rates for a member for a specific customer, effective on the
   * given date. Returns results ordered by effectiveFrom DESC so the most recent applicable rate is
   * first.
   */
  @Query(
      """
      SELECT br FROM BillingRate br
      WHERE br.memberId = :memberId
        AND br.customerId = :customerId
        AND br.projectId IS NULL
        AND br.effectiveFrom <= :date
        AND (br.effectiveTo IS NULL OR br.effectiveTo >= :date)
      ORDER BY br.effectiveFrom DESC
      """)
  List<BillingRate> findCustomerOverride(
      @Param("memberId") UUID memberId,
      @Param("customerId") UUID customerId,
      @Param("date") LocalDate date);

  /**
   * Finds the member-default rate (no project or customer scope), effective on the given date.
   * Returns results ordered by effectiveFrom DESC so the most recent applicable rate is first.
   */
  @Query(
      """
      SELECT br FROM BillingRate br
      WHERE br.memberId = :memberId
        AND br.projectId IS NULL
        AND br.customerId IS NULL
        AND br.effectiveFrom <= :date
        AND (br.effectiveTo IS NULL OR br.effectiveTo >= :date)
      ORDER BY br.effectiveFrom DESC
      """)
  List<BillingRate> findMemberDefault(
      @Param("memberId") UUID memberId, @Param("date") LocalDate date);

  /**
   * Finds billing rates that overlap with the given date range at the same scope level (same
   * member, project, and customer combination). Used for overlap validation before create/update.
   *
   * <p>For create operations, pass a random UUID as excludeId. For update operations, pass the
   * existing rate's ID to exclude it from the overlap check.
   */
  @Query(
      """
      SELECT br FROM BillingRate br
      WHERE br.memberId = :memberId
        AND (br.projectId = :projectId OR (br.projectId IS NULL AND :projectId IS NULL))
        AND (br.customerId = :customerId OR (br.customerId IS NULL AND :customerId IS NULL))
        AND br.id != :excludeId
        AND br.effectiveFrom <= :endDate
        AND (br.effectiveTo IS NULL OR br.effectiveTo >= :startDate)
      """)
  List<BillingRate> findOverlapping(
      @Param("memberId") UUID memberId,
      @Param("projectId") UUID projectId,
      @Param("customerId") UUID customerId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("excludeId") UUID excludeId);

  /**
   * Lists billing rates with optional filters. All filter parameters are matched exactly when
   * non-null, or ignored when null.
   */
  @Query(
      """
      SELECT br FROM BillingRate br
      WHERE (:memberId IS NULL OR br.memberId = :memberId)
        AND (:projectId IS NULL OR br.projectId = :projectId)
        AND (:customerId IS NULL OR br.customerId = :customerId)
      ORDER BY br.effectiveFrom DESC
      """)
  List<BillingRate> findByFilters(
      @Param("memberId") UUID memberId,
      @Param("projectId") UUID projectId,
      @Param("customerId") UUID customerId);
}
