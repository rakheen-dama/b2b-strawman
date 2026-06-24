package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link Deal}. Schema-per-tenant means {@code findById} is already tenant-isolated.
 *
 * <p>{@code findByLinkedProposalId} (correlated to {@code Proposal.dealId}) resolves the deal
 * linked to an accepted proposal — the win-loop entry point (576A).
 */
public interface DealRepository extends JpaRepository<Deal, UUID> {

  /** Tenant-safe single fetch — throws {@link ResourceNotFoundException}, matching convention. */
  default Deal findOneById(UUID id) {
    return findById(id).orElseThrow(() -> new ResourceNotFoundException("Deal", id));
  }

  @Query("SELECT d FROM Deal d WHERE d.customerId = :customerId ORDER BY d.updatedAt DESC")
  List<Deal> findByCustomerId(UUID customerId);

  /** Used by {@code PipelineStageService} to block deleting a stage that still has deals. */
  boolean existsByStageId(UUID stageId);

  /**
   * Win-loop (576A): resolve a deal from an accepted proposal via the mapped {@code
   * proposals.deal_id} column. Returns empty when the proposal has no linked deal.
   */
  @Query(
      "SELECT d FROM Deal d WHERE d.id = (SELECT p.dealId FROM Proposal p WHERE p.id = :proposalId)")
  Optional<Deal> findByLinkedProposalId(@Param("proposalId") UUID proposalId);

  /**
   * Paged, nullable-guarded filtered list (Phase 80, §11.3d; tag/saved-view composition 574B). Each
   * direct filter is ignored when its parameter is null. The {@code fromDate}/{@code toDate} window
   * applies to {@code expectedCloseDate}. Ordered by most-recently-updated first.
   *
   * <p>Tag filtering (574B): when {@code tagCount > 0}, a correlated subquery requires the deal to
   * carry <b>ALL</b> requested tag slugs (ALL/AND semantics, matching the customers list and the
   * native {@code TagFilterHandler}). {@code entity_tags} is a polymorphic join keyed by {@code
   * (entityType, entityId)} with no FK mapped on {@link Deal}, so the slug match is expressed as an
   * explicit {@code EntityTag}/{@code Tag} {@code ON}-join counted to {@code tagCount}.
   *
   * <p>Saved-view filtering (574B): {@code viewFilterActive} guards the restriction (mirroring the
   * tag {@code tagCount} flag) so the query never relies on {@code IS NULL} against a
   * collection-valued parameter, which is fragile/non-portable in HQL. When {@code
   * viewFilterActive} is {@code true} the page is restricted to {@code viewIds} — the non-empty set
   * of deal ids the saved view resolved server-side (via {@link
   * io.b2mash.b2b.b2bstrawman.view.ViewFilterHelper}). When {@code false} the restriction is
   * short-circuited and {@code viewIds} is a never-evaluated throwaway placeholder, so the pure
   * direct-filter path is unchanged. Keeping the restriction inside this single JPQL query
   * preserves correct {@link Page} totals (no in-memory post-paging).
   */
  @Query(
      """
      SELECT d FROM Deal d
      WHERE (:stageId    IS NULL OR d.stageId = :stageId)
        AND (:ownerId    IS NULL OR d.ownerId = :ownerId)
        AND (:customerId IS NULL OR d.customerId = :customerId)
        AND (:status     IS NULL OR d.status = :status)
        AND (:source     IS NULL OR d.source = :source)
        AND (:fromDate   IS NULL OR d.expectedCloseDate >= :fromDate)
        AND (:toDate     IS NULL OR d.expectedCloseDate <= :toDate)
        AND (:viewFilterActive = FALSE OR d.id IN :viewIds)
        AND (:tagCount = 0 OR (
          SELECT COUNT(DISTINCT t.slug)
          FROM EntityTag et JOIN Tag t ON t.id = et.tagId
          WHERE et.entityType = 'DEAL' AND et.entityId = d.id AND t.slug IN :tagSlugs
        ) = :tagCount)
      ORDER BY d.updatedAt DESC
      """)
  Page<Deal> findFiltered(
      @Param("stageId") UUID stageId,
      @Param("ownerId") UUID ownerId,
      @Param("customerId") UUID customerId,
      @Param("status") DealStatus status,
      @Param("source") String source,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate,
      @Param("viewFilterActive") boolean viewFilterActive,
      @Param("viewIds") List<UUID> viewIds,
      @Param("tagSlugs") List<String> tagSlugs,
      @Param("tagCount") long tagCount,
      Pageable pageable);

  // === Pipeline summary aggregation (Epic 578A, ADR-318 §11.3d) ===
  // Schema-per-tenant: the connection provider sets search_path to the bound tenant schema, so
  // plain
  // FROM deals / pipeline_stages already resolves to this tenant's tables — no tenant_id predicate.

  /**
   * Per-stage breakdown of OPEN deals, one row per active OPEN stage (empty stages included via
   * LEFT JOIN with COALESCE → zero counts). Effective probability is computed in SQL as {@code
   * COALESCE(probability_pct, default_probability_pct)}. The optional {@code ownerId} predicate
   * sits on the JOIN, not the WHERE, so empty stages still appear when an owner is filtered.
   * Ordered by stage position. {@code weightedValue} summed across rows gives the open weighted
   * pipeline value.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT s.id            AS stageId,
                 s.name          AS stageName,
                 s.position      AS stagePosition,
                 COUNT(d.id)     AS dealCount,
                 COALESCE(SUM(d.value_amount), 0) AS totalValue,
                 COALESCE(SUM(
                   d.value_amount *
                   COALESCE(d.probability_pct, s.default_probability_pct) / 100.0
                 ), 0)           AS weightedValue
          FROM pipeline_stages s
          LEFT JOIN deals d
                 ON d.stage_id = s.id
                AND d.status   = 'OPEN'
                AND (CAST(:ownerId AS UUID) IS NULL OR d.owner_id = :ownerId)
          WHERE s.stage_type = 'OPEN'
            AND s.archived = false
          GROUP BY s.id, s.name, s.position
          ORDER BY s.position
          """)
  List<StageBreakdownProjection> stageBreakdown(@Param("ownerId") UUID ownerId);

  /**
   * Win-rate window counts plus average days-to-close in a single aggregate row. WON/LOST are
   * counted by {@code won_at}/{@code lost_at} falling inside the half-open window {@code
   * [windowStart, windowEnd)} — deals closed on or after {@code windowEnd} are excluded. {@code
   * avgDaysToClose} is the mean {@code (won_at - created_at)} in days over WON deals in the same
   * window (NULL when none). The win rate ({@code wonCount / (wonCount + lostCount)}) is computed
   * in Java with a zero-divisor guard.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT
            COUNT(*) FILTER (WHERE status = 'WON')  AS wonCount,
            COUNT(*) FILTER (WHERE status = 'LOST') AS lostCount,
            AVG(EXTRACT(EPOCH FROM (won_at - created_at)) / 86400.0)
                 FILTER (WHERE status = 'WON')      AS avgDaysToClose
          FROM deals
          WHERE (CAST(:ownerId AS UUID) IS NULL OR owner_id = :ownerId)
            AND (
                  (status = 'WON'  AND won_at  >= :windowStart AND won_at  < :windowEnd)
               OR (status = 'LOST' AND lost_at >= :windowStart AND lost_at < :windowEnd)
                )
          """)
  WinRateProjection winRate(
      @Param("ownerId") UUID ownerId,
      @Param("windowStart") Instant windowStart,
      @Param("windowEnd") Instant windowEnd);
}
