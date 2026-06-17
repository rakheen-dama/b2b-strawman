package io.b2mash.b2b.b2bstrawman.mcp.consent;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tenant-scoped repository over the append-only {@link McpEgressConsent} history. The derived
 * {@link #findTopByOrderByConsentedAtDescCreatedAtDesc()} query resolves the firm's current consent
 * state (the newest decision by {@code consented_at}, tie-broken by {@code created_at}).
 */
public interface McpEgressConsentRepository extends JpaRepository<McpEgressConsent, UUID> {

  /**
   * The latest consent decision; empty when none recorded. Ordered by {@code consented_at} then
   * {@code created_at} (the {@code @PrePersist} DB-write time) so that two decisions sharing the
   * same {@code consented_at} instant resolve deterministically to the most recently inserted row.
   */
  Optional<McpEgressConsent> findTopByOrderByConsentedAtDescCreatedAtDesc();
}
