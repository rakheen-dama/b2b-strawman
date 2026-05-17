package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountingSyncEntryRepository extends JpaRepository<AccountingSyncEntry, UUID> {

  /** Standard findOneById following Kazi JPQL convention. */
  @Query("SELECT e FROM AccountingSyncEntry e WHERE e.id = :id")
  Optional<AccountingSyncEntry> findOneById(@Param("id") UUID id);

  /** Worker drain query: actionable entries ordered by next_attempt_at. */
  @Query(
      """
      SELECT e FROM AccountingSyncEntry e
      WHERE e.state IN (
          io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncState.PENDING,
          io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncState.FAILED_RETRYING
      )
      AND e.nextAttemptAt <= :now
      ORDER BY e.nextAttemptAt ASC
      """)
  List<AccountingSyncEntry> findDrainableEntries(@Param("now") Instant now, Pageable pageable);

  /** Lookup: latest sync entry for an entity (invoice/customer status chip). */
  @Query(
      """
      SELECT e FROM AccountingSyncEntry e
      WHERE e.entityType = :entityType AND e.entityId = :entityId
      ORDER BY e.createdAt DESC
      """)
  List<AccountingSyncEntry> findByEntity(
      @Param("entityType") SyncEntityType entityType, @Param("entityId") UUID entityId);

  /** Check for existing pending/in-flight entry (idempotent enqueue guard). */
  @Query(
      """
      SELECT e FROM AccountingSyncEntry e
      WHERE e.entityType = :entityType
      AND e.entityId = :entityId
      AND e.state IN (
          io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncState.PENDING,
          io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncState.IN_FLIGHT,
          io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncState.FAILED_RETRYING
      )
      """)
  Optional<AccountingSyncEntry> findActiveEntryForEntity(
      @Param("entityType") SyncEntityType entityType, @Param("entityId") UUID entityId);

  /** Match by external_reference for payment pull (most recently completed). */
  @Query(
      """
      SELECT e FROM AccountingSyncEntry e
      WHERE e.externalReference = :ref
      AND e.state = io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncState.COMPLETED
      AND e.direction = io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncDirection.PUSH
      ORDER BY e.completedAt DESC
      FETCH FIRST 1 ROW ONLY
      """)
  Optional<AccountingSyncEntry> findCompletedPushByExternalReference(
      @Param("ref") String externalReference);

  /** Paginated sync log with state filter. */
  @Query(
      """
      SELECT e FROM AccountingSyncEntry e
      WHERE (:state IS NULL OR e.state = :state)
      AND (:entityType IS NULL OR e.entityType = :entityType)
      ORDER BY e.createdAt DESC
      """)
  Page<AccountingSyncEntry> findFiltered(
      @Param("state") SyncState state,
      @Param("entityType") SyncEntityType entityType,
      Pageable pageable);

  /** Summary counts by state. */
  @Query(
      """
      SELECT e.state, COUNT(e) FROM AccountingSyncEntry e
      GROUP BY e.state
      """)
  List<Object[]> countByState();
}
