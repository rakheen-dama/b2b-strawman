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

  @Query("SELECT c FROM Correspondence c WHERE c.projectId = :pid ORDER BY c.receivedAt DESC")
  Page<Correspondence> findByProjectId(@Param("pid") UUID projectId, Pageable pageable);

  @Query("SELECT c FROM Correspondence c WHERE c.customerId = :cid ORDER BY c.receivedAt DESC")
  Page<Correspondence> findByCustomerId(@Param("cid") UUID customerId, Pageable pageable);
}
