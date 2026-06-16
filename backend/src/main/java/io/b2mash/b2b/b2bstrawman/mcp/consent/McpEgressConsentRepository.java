package io.b2mash.b2b.b2bstrawman.mcp.consent;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tenant-scoped repository over the append-only {@link McpEgressConsent} history. The derived
 * {@link #findTopByOrderByConsentedAtDesc()} query resolves the firm's current consent state (the
 * newest decision by {@code consented_at}).
 */
public interface McpEgressConsentRepository extends JpaRepository<McpEgressConsent, UUID> {

  /** The latest consent decision (newest by {@code consented_at}); empty when none recorded. */
  Optional<McpEgressConsent> findTopByOrderByConsentedAtDesc();
}
