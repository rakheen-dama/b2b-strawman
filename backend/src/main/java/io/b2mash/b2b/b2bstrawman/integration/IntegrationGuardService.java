package io.b2mash.b2b.b2bstrawman.integration;

import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks whether an integration domain is enabled for the current tenant. Throws 403
 * IntegrationDisabledException if the domain is disabled.
 */
@Service
public class IntegrationGuardService {

  private final OrgSettingsRepository orgSettingsRepository;

  public IntegrationGuardService(OrgSettingsRepository orgSettingsRepository) {
    this.orgSettingsRepository = orgSettingsRepository;
  }

  /**
   * Throws 403 if the given integration domain is not enabled for the current tenant. PAYMENT is
   * always allowed (core functionality, not gated).
   */
  @Transactional(readOnly = true)
  public void requireEnabled(IntegrationDomain domain) {
    if (domain == IntegrationDomain.PAYMENT) {
      return; // Always available
    }

    var settings = orgSettingsRepository.findForCurrentTenant();

    boolean enabled =
        settings
            .map(
                s ->
                    switch (domain) {
                      case ACCOUNTING -> s.isAccountingEnabled();
                      case AI -> s.isAiEnabled();
                      case DOCUMENT_SIGNING -> s.isDocumentSigningEnabled();
                      case PAYMENT -> true; // unreachable due to early return
                    })
            .orElse(false); // No settings row = all flags disabled

    if (!enabled) {
      throw new IntegrationDisabledException(domain);
    }
  }
}
