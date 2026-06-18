package io.b2mash.b2b.b2bstrawman.mcp;

import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.mcp.consent.McpConsentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes and toggles the per-tenant effective enablement state of the Kazi MCP connector (Epic
 * 565B, §11.7).
 *
 * <p>The connector is <b>effectively enabled</b> only when BOTH:
 *
 * <ol>
 *   <li>an {@link OrgIntegration} row for {@link IntegrationDomain#MCP} exists and is enabled, AND
 *   <li>the firm's latest POPIA data-egress consent decision is GRANTED.
 * </ol>
 *
 * <p>{@link #effectiveState()} is re-evaluated on every call (no caching) so a {@link #revoke()}
 * takes effect on the very next MCP {@code tools/call} / {@code resources/read}. The MCP tool and
 * resource methods call {@link #effectiveState()} as their first statement and refuse with a
 * non-leaking {@code not_enabled} error when it is false.
 */
@Service
public class McpEnablementService {

  /** Provider slug for the self-hosted Kazi MCP connector. */
  private static final String PROVIDER_SLUG = "kazi";

  private final OrgIntegrationRepository orgIntegrationRepository;
  private final McpConsentService consentService;
  private final String resourceUrl;

  public McpEnablementService(
      OrgIntegrationRepository orgIntegrationRepository,
      McpConsentService consentService,
      @Value("${kazi.mcp.resource-url:http://localhost:8080/mcp}") String resourceUrl) {
    this.orgIntegrationRepository = orgIntegrationRepository;
    this.consentService = consentService;
    this.resourceUrl = resourceUrl;
  }

  /**
   * True when the MCP connector is effectively enabled for the current tenant: the {@link
   * IntegrationDomain#MCP} integration is present and enabled AND the latest consent is GRANTED.
   * Absent integration row → disabled. Re-checked per request.
   */
  @Transactional(readOnly = true)
  public boolean effectiveState() {
    boolean integrationEnabled =
        orgIntegrationRepository
            .findByDomain(IntegrationDomain.MCP)
            .map(OrgIntegration::isEnabled)
            .orElse(false);
    return integrationEnabled && consentService.isCurrentlyGranted();
  }

  /**
   * Enables the connector for the current tenant: records the POPIA consent FIRST, then upserts and
   * enables the {@link IntegrationDomain#MCP} {@link OrgIntegration} row. Atomic — consent and
   * enablement commit together.
   *
   * @param consentVersion the consent document version the member agreed to (e.g. {@code
   *     popia-egress-v1})
   */
  @Transactional
  public void enable(String consentVersion) {
    consentService.grant(consentVersion);
    OrgIntegration integration =
        orgIntegrationRepository
            .findByDomain(IntegrationDomain.MCP)
            .orElseGet(() -> new OrgIntegration(IntegrationDomain.MCP, PROVIDER_SLUG));
    integration.enable();
    orgIntegrationRepository.save(integration);
  }

  /**
   * Revokes the connector for the current tenant: disables the {@link IntegrationDomain#MCP}
   * integration (if a row exists) AND appends a REVOKED consent row. Atomic. Takes effect on the
   * next MCP request since {@link #effectiveState()} is not cached.
   */
  @Transactional
  public void revoke() {
    orgIntegrationRepository
        .findByDomain(IntegrationDomain.MCP)
        .ifPresent(
            integration -> {
              integration.disable();
              orgIntegrationRepository.save(integration);
            });
    consentService.revoke();
  }

  /**
   * Assembles the current per-tenant MCP enablement status for the settings UI (Epic 566). Returns
   * the effective state, the raw {@link IntegrationDomain#MCP} enabled flag (so the UI can surface
   * the "enabled-but-consent-revoked" edge), the connector's server URL, and the latest consent
   * decision.
   */
  @Transactional(readOnly = true)
  public McpEnablementStatus status() {
    boolean integrationEnabled =
        orgIntegrationRepository
            .findByDomain(IntegrationDomain.MCP)
            .map(OrgIntegration::isEnabled)
            .orElse(false);
    McpConsentService.ConsentState consent = consentService.currentState();
    boolean effectivelyEnabled = integrationEnabled && consent.granted();
    return new McpEnablementStatus(effectivelyEnabled, integrationEnabled, resourceUrl, consent);
  }

  /**
   * Settings-UI view of the connector's enablement state.
   *
   * @param effectivelyEnabled true when the integration is enabled AND consent is granted
   * @param integrationEnabled the raw {@link IntegrationDomain#MCP} enabled flag alone
   * @param serverUrl the MCP connector server URL
   * @param consent the firm's latest POPIA data-egress consent decision
   */
  public record McpEnablementStatus(
      boolean effectivelyEnabled,
      boolean integrationEnabled,
      String serverUrl,
      McpConsentService.ConsentState consent) {}
}
