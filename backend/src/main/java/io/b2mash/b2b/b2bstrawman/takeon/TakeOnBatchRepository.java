package io.b2mash.b2b.b2bstrawman.takeon;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Take-on batches. Native queries use unqualified table names: the tenant {@code search_path}
 * resolves them.
 */
public interface TakeOnBatchRepository extends JpaRepository<TakeOnBatch, UUID> {

  /** Serialises validation, sign-off and the commit claim on one batch (§17.10 X5). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from TakeOnBatch b where b.id = :id")
  Optional<TakeOnBatch> findByIdForUpdate(@Param("id") UUID id);

  Page<TakeOnBatch> findByDatasetInOrderByCreatedAtDesc(
      Collection<TakeOnDataset> datasets, Pageable pageable);

  Page<TakeOnBatch> findByDatasetInAndStatusInOrderByCreatedAtDesc(
      Collection<TakeOnDataset> datasets,
      Collection<TakeOnBatchStatus> statuses,
      Pageable pageable);

  /** The dataset catalogue's "n completed". */
  long countByDatasetAndStatus(TakeOnDataset dataset, TakeOnBatchStatus status);

  /**
   * Abandoned-batch purge (§17.3.14, FD-24): open batches created before {@code cutoff}, oldest
   * first. Ids only — each batch is then claimed in its own transaction.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT id FROM take_on_batches
           WHERE status IN ('UPLOADED','MAPPED','VALIDATED') AND created_at < :cutoff
           ORDER BY created_at LIMIT 500""")
  List<UUID> findAbandonedIds(@Param("cutoff") Instant cutoff);

  /** Claims one batch for the purge; empty if another transaction holds it. */
  @Query(
      nativeQuery = true,
      value = "SELECT * FROM take_on_batches WHERE id = :id FOR UPDATE SKIP LOCKED")
  Optional<TakeOnBatch> findByIdForUpdateSkipLocked(@Param("id") UUID id);
}
