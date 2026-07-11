package io.b2mash.b2b.b2bstrawman.collections;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Tenant-isolated via {@code search_path} (schema-per-tenant). The explicit JPQL {@code
 * findOneById} follows the repo's find-by-id convention (used in preference to the inherited
 * derived {@code findById}).
 */
public interface CollectionActivityRepository extends JpaRepository<CollectionActivity, UUID> {

  /** Explicit-JPQL find by id (repo convention — not the derived {@code findById}). */
  @Query("SELECT a FROM CollectionActivity a WHERE a.id = :id")
  Optional<CollectionActivity> findOneById(@Param("id") UUID id);

  /** Idempotency / stage lookup: the single row for this (invoice, stage), if any. */
  @Query("SELECT a FROM CollectionActivity a WHERE a.invoiceId = :invoiceId AND a.stage = :stage")
  Optional<CollectionActivity> findByInvoiceIdAndStage(
      @Param("invoiceId") UUID invoiceId, @Param("stage") CollectionStage stage);

  /** Scan / payment-listener lookup: activities for an invoice in a given status. */
  @Query("SELECT a FROM CollectionActivity a WHERE a.invoiceId = :invoiceId AND a.status = :status")
  List<CollectionActivity> findByInvoiceIdAndStatus(
      @Param("invoiceId") UUID invoiceId, @Param("status") CollectionActivityStatus status);

  /** All chase activity for an invoice (across stages/statuses). */
  @Query("SELECT a FROM CollectionActivity a WHERE a.invoiceId = :invoiceId")
  List<CollectionActivity> findByInvoiceId(@Param("invoiceId") UUID invoiceId);

  /** Gate-lifecycle listener lookup: the activity carrying this gate as its current/last draft. */
  @Query("SELECT a FROM CollectionActivity a WHERE a.gateId = :gateId")
  Optional<CollectionActivity> findByGateId(@Param("gateId") UUID gateId);

  /** Customer chase-history page, newest-first ({@code id} as deterministic tie-breaker). */
  @Query(
      "SELECT a FROM CollectionActivity a WHERE a.customerId = :customerId"
          + " ORDER BY a.createdAt DESC, a.id DESC")
  Page<CollectionActivity> findByCustomerId(
      @Param("customerId") UUID customerId, Pageable pageable);

  /**
   * Reminder-activity counts grouped by status over the trailing window (Phase 83, 593A cash
   * digest). Windowed on {@code updatedAt} — status transitions happen in place on the single
   * (invoice, stage) row, so {@code updatedAt} reflects the latest transition where {@code
   * createdAt} would miss re-proposals.
   */
  @Query(
      "SELECT a.status AS status, COUNT(a) AS count FROM CollectionActivity a"
          + " WHERE a.updatedAt >= :since GROUP BY a.status")
  List<CollectionActivityStatusCount> countByStatusSince(@Param("since") Instant since);
}
