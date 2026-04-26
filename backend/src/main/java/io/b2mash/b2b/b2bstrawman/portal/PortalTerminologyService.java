package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the terminology namespace for an authenticated portal contact (GAP-L-65, E3.1).
 *
 * <p>The returned {@code namespace} is the firm's vertical-profile id (e.g. {@code "legal-za"}),
 * which is the lookup key into the portal's {@code TERMINOLOGY} TS map. We deliberately surface
 * {@code verticalProfile} rather than {@link OrgSettings#getTerminologyNamespace()} (which is the
 * locale-prefixed key {@code "en-ZA-legal"}) to keep portal and firm-side TS terminology maps keyed
 * identically -- see {@code frontend/lib/terminology-map.ts} where the firm-side {@code
 * <TerminologyProvider verticalProfile=...>} wires up the same lookup.
 *
 * <p>Tenant context is bound by {@code CustomerAuthFilter}; {@link
 * RequestScopes#requireCustomerId()} is called for defensive sanity so the endpoint refuses to
 * serve non-portal callers.
 */
@Service
public class PortalTerminologyService {

  private final OrgSettingsRepository orgSettingsRepository;

  public PortalTerminologyService(OrgSettingsRepository orgSettingsRepository) {
    this.orgSettingsRepository = orgSettingsRepository;
  }

  @Transactional(readOnly = true)
  public PortalTerminologyResponse getTerminology() {
    // Portal-only sanity guard -- non-portal callers will never have CUSTOMER_ID bound.
    RequestScopes.requireCustomerId();
    String namespace =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(OrgSettings::getVerticalProfile)
            .orElse(null);
    return new PortalTerminologyResponse(namespace);
  }

  public record PortalTerminologyResponse(String namespace) {}
}
