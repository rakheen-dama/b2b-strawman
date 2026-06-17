package io.b2mash.b2b.b2bstrawman.mcp.consent;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records and reads the firm's POPIA data-egress consent for the MCP connector (Epic 565B, §11.7).
 *
 * <p>Append-only: {@link #grant(String)} and {@link #revoke()} each insert a new {@link
 * McpEgressConsent} row attributed to the current member ({@link RequestScopes#requireMemberId()});
 * neither mutates prior history. {@link #currentState()} returns the latest-decision view used by
 * {@code McpEnablementService} to compute the effective enablement state.
 */
@Service
public class McpConsentService {

  /** Default version stamped on a REVOKED row when no prior consent row exists to inherit from. */
  static final String DEFAULT_CONSENT_VERSION = "popia-egress-v1";

  private final McpEgressConsentRepository repository;

  public McpConsentService(McpEgressConsentRepository repository) {
    this.repository = repository;
  }

  /** Appends a GRANTED consent row for {@code consentVersion} by the current member. */
  @Transactional
  public McpEgressConsent grant(String consentVersion) {
    return repository.save(McpEgressConsent.grant(RequestScopes.requireMemberId(), consentVersion));
  }

  /**
   * Appends a REVOKED consent row by the current member. The {@code consent_version} column is NOT
   * NULL, so the latest recorded version is reused (falling back to {@link
   * #DEFAULT_CONSENT_VERSION} when no prior row exists).
   */
  @Transactional
  public McpEgressConsent revoke() {
    String version =
        repository
            .findTopByOrderByConsentedAtDescCreatedAtDesc()
            .map(McpEgressConsent::getConsentVersion)
            .orElse(DEFAULT_CONSENT_VERSION);
    return repository.save(McpEgressConsent.revoke(RequestScopes.requireMemberId(), version));
  }

  /** The firm's current consent state — the latest decision, or an absent/not-granted default. */
  @Transactional(readOnly = true)
  public ConsentState currentState() {
    return repository
        .findTopByOrderByConsentedAtDescCreatedAtDesc()
        .map(
            c ->
                new ConsentState(
                    c.isGranted(),
                    c.getAction(),
                    c.getConsentVersion(),
                    c.getConsentedBy(),
                    c.getConsentedAt()))
        .orElse(ConsentState.none());
  }

  /** True when the firm's latest consent decision is GRANTED. */
  @Transactional(readOnly = true)
  public boolean isCurrentlyGranted() {
    return repository
        .findTopByOrderByConsentedAtDescCreatedAtDesc()
        .map(McpEgressConsent::isGranted)
        .orElse(false);
  }

  /**
   * Minimal latest-decision view of the consent history. The frontend metadata shape (Epic 566) can
   * extend this; 565B keeps it lean.
   */
  public record ConsentState(
      boolean granted, String action, String version, UUID consentedBy, Instant consentedAt) {

    static ConsentState none() {
      return new ConsentState(false, null, null, null, null);
    }
  }
}
