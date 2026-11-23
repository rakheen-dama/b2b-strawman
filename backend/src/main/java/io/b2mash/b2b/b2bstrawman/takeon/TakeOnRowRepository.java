package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Take-on rows. Native queries use unqualified table names: the tenant {@code search_path} resolves
 * them.
 */
public interface TakeOnRowRepository extends JpaRepository<TakeOnRow, UUID> {

  /** Native-query projection for the X2 counts (interface projection; aliases must match). */
  interface RowCounts {
    long getTotal();

    long getValid();

    long getError();

    long getDuplicate();

    long getBlocks();
  }

  List<TakeOnRow> findByBatchIdOrderBySourceIdAscRowNumberAsc(UUID batchId);

  /**
   * The row table. Pass ALL values of a filter when it is not set (avoids Hibernate null-enum
   * parameter typing); sort the {@code Pageable} by section, rowNumber.
   */
  Page<TakeOnRow> findByBatchIdAndStatusInAndSectionInAndBlockIn(
      UUID batchId,
      Collection<TakeOnRowStatus> statuses,
      Collection<TakeOnSection> sections,
      Collection<Boolean> blocks,
      Pageable pageable);

  /** The first row of a source — its raw key set is the header set for mapping checks. */
  Optional<TakeOnRow> findFirstBySourceIdOrderByRowNumberAsc(UUID sourceId);

  Optional<TakeOnRow> findFirstByBatchIdAndStatusInOrderByRowNumberAsc(
      UUID batchId, Collection<TakeOnRowStatus> open);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from TakeOnRow r where r.sourceId = :sourceId")
  int deleteBySourceId(@Param("sourceId") UUID sourceId);

  /** Cancel / abandoned purge: the row shells stay, their values go (§17.10 X4). */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          """
          UPDATE take_on_rows SET raw_values = '{"purged": true}'::jsonb,
                 mapped_values = '{"purged": true}'::jsonb, updated_at = now()
           WHERE batch_id = :batchId""")
  int purgeValues(@Param("batchId") UUID batchId);

  /** The summary counts, read in the same transaction that wrote the statuses (§17.10 X2). */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT count(*)                                            AS total,
                 count(*) FILTER (WHERE status = 'VALIDATED')        AS valid,
                 count(*) FILTER (WHERE status = 'ERROR')            AS error,
                 count(*) FILTER (WHERE duplicate_of_id IS NOT NULL) AS duplicate,
                 count(*) FILTER (WHERE block)                       AS blocks
            FROM take_on_rows WHERE batch_id = :batchId""")
  RowCounts countsFor(@Param("batchId") UUID batchId);

  /** A client taken on earlier, by the previous system's id (later datasets resolve by it). */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT r.result_entity_id FROM take_on_rows r
           WHERE r.dataset = 'CLIENTS' AND r.status IN ('CREATED', 'SKIPPED_DUPLICATE')
             AND lower(r.source_ref) = lower(:sourceRef)
           ORDER BY r.updated_at DESC LIMIT 1""")
  Optional<UUID> clientBySourceRef(@Param("sourceRef") String sourceRef);
}
