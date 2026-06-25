package io.b2mash.b2b.b2bstrawman.correspondence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Tenant-isolated via {@code search_path} (schema-per-tenant). Inherited {@code findById} is
 * tenant-safe; no {@code findOneById} convention here.
 */
public interface CorrespondenceRepository extends JpaRepository<Correspondence, UUID> {

  /** Idempotency lookup: re-filing the same email finds the existing row. */
  Optional<Correspondence> findByMessageId(String messageId);

  // receivedAt is NULLABLE; PostgreSQL sorts NULLs FIRST under DESC, so undated rows would jump
  // ahead of genuinely recent mail. Fall back to filedAt (NOT NULL, @PrePersist-set) and break ties
  // on id for a stable, deterministic order.
  @Query(
      "SELECT c FROM Correspondence c WHERE c.projectId = :pid"
          + " ORDER BY COALESCE(c.receivedAt, c.filedAt) DESC, c.filedAt DESC, c.id DESC")
  Page<Correspondence> findByProjectId(@Param("pid") UUID projectId, Pageable pageable);

  @Query(
      "SELECT c FROM Correspondence c WHERE c.customerId = :cid"
          + " ORDER BY COALESCE(c.receivedAt, c.filedAt) DESC, c.filedAt DESC, c.id DESC")
  Page<Correspondence> findByCustomerId(@Param("cid") UUID customerId, Pageable pageable);

  /**
   * Count Documents filed to a correspondence (via {@code correspondence_id}). Querying the
   * Document entity from JPQL keeps the attachment count inside this repository — no cross-context
   * DocumentRepository injection in CorrespondenceService.
   */
  @Query(
      "SELECT COUNT(d) FROM io.b2mash.b2b.b2bstrawman.document.Document d"
          + " WHERE d.correspondenceId = :correspondenceId")
  long countAttachments(@Param("correspondenceId") UUID correspondenceId);
}
