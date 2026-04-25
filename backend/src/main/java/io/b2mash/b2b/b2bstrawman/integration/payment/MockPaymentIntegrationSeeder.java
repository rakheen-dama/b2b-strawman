package io.b2mash.b2b.b2bstrawman.integration.payment;

import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Dev-only seeder that ensures the {@code legal-za} vertical has a usable {@code mock} payment
 * adapter wired into {@code org_integrations} so {@link
 * io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry#resolve} returns {@link
 * MockPaymentGateway} instead of falling back to the {@code noop} default.
 *
 * <p>Profile-gated: this bean is loaded under all profiles, but its {@link #seedForTenant(String,
 * String)} method is a no-op unless the active profile is one of {@code local}, {@code dev}, {@code
 * keycloak}, or {@code test}. Production tenants must configure their PSP through the integrations
 * UI.
 *
 * <p>Idempotent: only inserts a row when no {@code PAYMENT} integration exists for the tenant.
 * Never overwrites a configured PSP (e.g., a real PayFast row).
 */
@Service
public class MockPaymentIntegrationSeeder {

  private static final Logger log = LoggerFactory.getLogger(MockPaymentIntegrationSeeder.class);
  private static final String[] DEV_PROFILES = {"local", "dev", "keycloak", "test"};
  private static final String LEGAL_ZA_PROFILE = "legal-za";

  private final OrgIntegrationRepository orgIntegrationRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TenantTransactionHelper tenantTransactionHelper;
  private final Environment environment;

  public MockPaymentIntegrationSeeder(
      OrgIntegrationRepository orgIntegrationRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper,
      Environment environment) {
    this.orgIntegrationRepository = orgIntegrationRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.tenantTransactionHelper = tenantTransactionHelper;
    this.environment = environment;
  }

  /**
   * Seeds the {@code mock} PAYMENT adapter for the given tenant if (a) the active profile is
   * non-prod and (b) the tenant has the {@code legal-za} vertical profile. Idempotent.
   */
  public void seedForTenant(String tenantSchema, String orgId) {
    if (!isDevProfile()) {
      return;
    }
    tenantTransactionHelper.executeInTenantTransaction(tenantSchema, orgId, t -> doSeed(t));
  }

  private void doSeed(String tenantSchema) {
    var settings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    if (settings == null || !LEGAL_ZA_PROFILE.equals(settings.getVerticalProfile())) {
      log.debug(
          "Skipping mock payment integration seed for tenant {} (not legal-za profile)",
          tenantSchema);
      return;
    }

    var existing = orgIntegrationRepository.findByDomain(IntegrationDomain.PAYMENT);
    if (existing.isPresent()) {
      log.debug(
          "PAYMENT integration already configured for tenant {} (provider={}); skipping mock seed",
          tenantSchema,
          existing.get().getProviderSlug());
      return;
    }

    var integration = new OrgIntegration(IntegrationDomain.PAYMENT, "mock");
    integration.enable();
    integration.updateProvider("mock", "{}");
    orgIntegrationRepository.save(integration);
    log.info(
        "Seeded mock PAYMENT integration for legal-za tenant {} (dev profile only)", tenantSchema);
  }

  private boolean isDevProfile() {
    return environment.acceptsProfiles(
        org.springframework.core.env.Profiles.of(String.join(" | ", DEV_PROFILES)));
  }
}
