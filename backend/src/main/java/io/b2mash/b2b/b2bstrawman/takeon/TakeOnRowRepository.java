package io.b2mash.b2b.b2bstrawman.takeon;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

  /**
   * As above, narrowed to the rows that match an existing record ({@code duplicate_of_id} set) —
   * with {@code VALIDATED}, exactly the rows the Duplicates card counts (OBS-655A-1).
   */
  Page<TakeOnRow> findByBatchIdAndStatusInAndSectionInAndBlockInAndDuplicateOfIdIsNotNull(
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

  /**
   * Cancel / abandoned purge: the row shells stay, their values go (§17.10 X4) — the cells, the
   * previous system's id ({@code source_ref}), the dedup key (a lower-cased email or reference) and
   * the message text of every error and warning (it may quote a cell); only {code, field} remain.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          """
          UPDATE take_on_rows SET raw_values = '{"purged": true}'::jsonb,
                 mapped_values = '{"purged": true}'::jsonb,
                 source_ref = NULL, dedup_key = NULL,
                 errors = COALESCE((SELECT jsonb_agg(jsonb_build_object(
                                      'code', e -> 'code', 'field', e -> 'field'))
                                      FROM jsonb_array_elements(errors) e), '[]'::jsonb),
                 warnings = COALESCE((SELECT jsonb_agg(jsonb_build_object(
                                        'code', w -> 'code', 'field', w -> 'field'))
                                        FROM jsonb_array_elements(warnings) w), '[]'::jsonb),
                 updated_at = now()
           WHERE batch_id = :batchId""")
  int purgeValues(@Param("batchId") UUID batchId);

  /**
   * The summary counts, read in the same transaction that wrote the statuses (§17.10 X2). {@code
   * duplicate} counts the valid rows that match an existing record (they commit as skipped) — an
   * error row keeps its {@code duplicate_of_id} (the anonymisation link, D-102) but is not counted.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT count(*)                                            AS total,
                 count(*) FILTER (WHERE status = 'VALIDATED')        AS valid,
                 count(*) FILTER (WHERE status = 'ERROR')            AS error,
                 count(*) FILTER (WHERE status = 'VALIDATED'
                                    AND duplicate_of_id IS NOT NULL) AS duplicate,
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

  /**
   * A matter taken on earlier, by the previous system's id (657B: the trust position finds a matter
   * by it when the file gives no matter reference number).
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT r.result_entity_id FROM take_on_rows r
           WHERE r.dataset = 'MATTERS' AND r.status IN ('CREATED', 'SKIPPED_DUPLICATE')
             AND lower(r.source_ref) = lower(:sourceRef)
           ORDER BY r.updated_at DESC LIMIT 1""")
  Optional<UUID> matterBySourceRef(@Param("sourceRef") String sourceRef);

  // --- Commit (655A) ---

  /** Native-query projection of the commit progress (aliases must match). */
  interface CommitCounts {
    long getCreated();

    long getSkipped();

    long getFailed();

    long getPending();
  }

  /**
   * Claims the next row to commit (§17.3.3 step 3): a row another worker holds is skipped, never
   * waited for. The caller re-checks the status under the lock.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT * FROM take_on_rows WHERE batch_id = :batchId AND status = 'VALIDATED'
           ORDER BY row_number LIMIT 1 FOR UPDATE SKIP LOCKED""")
  Optional<TakeOnRow> claimNextValidated(@Param("batchId") UUID batchId);

  /** One row, locked (blocking) — marks a row FAILED after its own transaction rolled back. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from TakeOnRow r where r.id = :id")
  Optional<TakeOnRow> findByIdForUpdate(@Param("id") UUID id);

  /**
   * Whether any row is still to commit — a plain, <b>non-locking</b> read (C-6): under READ
   * COMMITTED it still sees a row another worker is committing as {@code VALIDATED}, so a second
   * worker never completes the batch while the first is mid-row.
   */
  @Query(
      nativeQuery = true,
      value =
          "SELECT EXISTS (SELECT 1 FROM take_on_rows WHERE batch_id = :batchId"
              + " AND status = 'VALIDATED')")
  boolean anyToCommit(@Param("batchId") UUID batchId);

  /** Whether any row of the batch was committed (a terminal status). */
  @Query(
      nativeQuery = true,
      value =
          "SELECT EXISTS (SELECT 1 FROM take_on_rows WHERE batch_id = :batchId"
              + " AND status IN ('CREATED', 'SKIPPED_DUPLICATE', 'SKIPPED_ZERO', 'FAILED'))")
  boolean anyCommitted(@Param("batchId") UUID batchId);

  /** Live commit progress and the completion counts (C-5): read from the rows themselves. */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT count(*) FILTER (WHERE status = 'CREATED')                                AS created,
                 count(*) FILTER (WHERE status IN ('SKIPPED_DUPLICATE', 'SKIPPED_ZERO'))  AS skipped,
                 count(*) FILTER (WHERE status = 'FAILED')                                 AS failed,
                 count(*) FILTER (WHERE status = 'VALIDATED')                              AS pending
            FROM take_on_rows WHERE batch_id = :batchId""")
  CommitCounts commitCountsFor(@Param("batchId") UUID batchId);

  List<TakeOnRow> findByBatchIdAndStatusOrderByRowNumberAsc(UUID batchId, TakeOnRowStatus status);

  /**
   * POPIA (§17.6.5, FD-19, C-23): when a client is anonymised, every take-on row that produced it
   * or matched it — by {@code result_entity_id} or {@code duplicate_of_id}, or by the dedup key of
   * any email the client has held ({@code dedupKeys}, from {@code TakeOnDedupKeys.client}: a row
   * that FAILED on an email clash with it, an in-file repeat of it, a row keyed by an address it
   * has since changed; taken <b>before</b> the email is overwritten; a row keyed by such an email
   * but linked to <b>another</b> client — one who holds that address now — is that client's and
   * stays, delta review M-1) — loses its values: {@code {"redacted": true}}, the previous system's
   * id ({@code source_ref}, free text that may be an email or a name), the dedup key and the
   * message texts (they may quote a cell); only {code, field} stay. A matters row resolved to the
   * client (its mapped {@code clientId}, D-159; the SQL literal {@code 'clientId'} repeats {@code
   * MattersDatasetHandler.CLIENT_ID}) is redacted the same way: it links to the matter and is keyed
   * by the matter, but holds the client reference the file gave (email, previous system id, ID
   * number — D-178). The row shell (number, status, result link) stays; the archived file is not
   * rewritten while a legal retention period runs. Pass at least one key.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          """
          UPDATE take_on_rows SET raw_values = '{"redacted": true}'::jsonb,
                 mapped_values = '{"redacted": true}'::jsonb,
                 source_ref = NULL, dedup_key = NULL,
                 errors = COALESCE((SELECT jsonb_agg(jsonb_build_object(
                                      'code', e -> 'code', 'field', e -> 'field'))
                                      FROM jsonb_array_elements(errors) e), '[]'::jsonb),
                 warnings = COALESCE((SELECT jsonb_agg(jsonb_build_object(
                                        'code', w -> 'code', 'field', w -> 'field'))
                                        FROM jsonb_array_elements(warnings) w), '[]'::jsonb),
                 updated_at = now()
           WHERE result_entity_id = :clientId OR duplicate_of_id = :clientId
              OR (dataset = 'MATTERS'
                  AND mapped_values ->> 'clientId' = CAST(:clientId AS varchar))
              OR (dedup_key IN (:dedupKeys)
                  AND (result_entity_id IS NULL OR result_entity_id = :clientId)
                  AND (duplicate_of_id IS NULL OR duplicate_of_id = :clientId))""")
  int redactForClient(
      @Param("clientId") UUID clientId, @Param("dedupKeys") Collection<String> dedupKeys);
}
