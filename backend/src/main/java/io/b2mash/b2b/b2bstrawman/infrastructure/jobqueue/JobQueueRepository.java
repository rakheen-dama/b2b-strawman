package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobQueueRepository extends JpaRepository<JobQueue, UUID> {

  /**
   * Claims up to {@code limit} pending jobs that are ready for execution. Uses {@code FOR UPDATE
   * SKIP LOCKED} to distribute work across multiple worker pods without contention.
   */
  @Query(
      value =
          """
      SELECT * FROM public.job_queue
      WHERE status = 'PENDING'
        AND next_attempt_at <= NOW()
      ORDER BY priority DESC, next_attempt_at ASC
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """,
      nativeQuery = true)
  List<JobQueue> findClaimable(@Param("limit") int limit);

  /**
   * Finds jobs that were claimed but not completed within the expected time window. Used by the
   * stale job recovery task to reset abandoned jobs back to PENDING.
   */
  @Query("SELECT j FROM JobQueue j WHERE j.status = 'CLAIMED' AND j.claimedAt < :threshold")
  List<JobQueue> findStaleClaimed(@Param("threshold") Instant threshold);

  /** Returns the count of jobs grouped by status (for admin dashboard / monitoring). */
  @Query("SELECT j.status, COUNT(j) FROM JobQueue j GROUP BY j.status")
  List<Object[]> countByStatus();

  /** Paginated query for the admin API — filter by status and optionally by job type. */
  @Query(
      """
      SELECT j FROM JobQueue j
      WHERE j.status = :status
        AND (:jobType IS NULL OR j.jobType = :jobType)
      """)
  Page<JobQueue> findByStatusAndJobType(
      @Param("status") JobStatus status, @Param("jobType") String jobType, Pageable pageable);

  /** Finds active (PENDING or CLAIMED) jobs for a given job type — used for dedup pre-filter. */
  @Query(
      "SELECT j.tenantId FROM JobQueue j WHERE j.jobType = :jobType AND j.status IN ('PENDING', 'CLAIMED')")
  Set<String> findActiveTenantIdsByJobType(@Param("jobType") String jobType);
}
