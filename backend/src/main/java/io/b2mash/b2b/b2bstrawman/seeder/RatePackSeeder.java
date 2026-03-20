package io.b2mash.b2b.b2bstrawman.seeder;

import io.b2mash.b2b.b2bstrawman.billingrate.BillingRate;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.LocalDate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds org-level billing rates from rate pack JSON definitions. Each rate entry creates a
 * BillingRate with null memberId (rates represent role-based rate tiers, not individual members).
 * Tenants can assign these rates to specific members after onboarding.
 */
@Service
public class RatePackSeeder extends AbstractPackSeeder<RatePackDefinition> {

  private static final String PACK_LOCATION = "classpath:rate-packs/*.json";

  private final BillingRateRepository billingRateRepository;

  public RatePackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper,
      BillingRateRepository billingRateRepository) {
    super(resourceResolver, objectMapper, orgSettingsRepository, tenantTransactionHelper);
    this.billingRateRepository = billingRateRepository;
  }

  @Override
  protected String getPackResourcePattern() {
    return PACK_LOCATION;
  }

  @Override
  protected Class<RatePackDefinition> getPackDefinitionType() {
    return RatePackDefinition.class;
  }

  @Override
  protected String getPackTypeName() {
    return "rate";
  }

  @Override
  protected String getPackId(RatePackDefinition pack) {
    return pack.packId();
  }

  @Override
  protected String getPackVersion(RatePackDefinition pack) {
    return String.valueOf(pack.version());
  }

  @Override
  protected String getVerticalProfile(RatePackDefinition pack) {
    return pack.verticalProfile();
  }

  @Override
  protected boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getRatePackStatus() == null) {
      return false;
    }
    return settings.getRatePackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, RatePackDefinition pack) {
    settings.recordRatePackApplication(pack.packId(), pack.version());
  }

  @Override
  protected void applyPack(RatePackDefinition pack, Resource packResource, String tenantId) {
    for (RatePackDefinition.RateEntry entry : pack.rates()) {
      var rate =
          new BillingRate(
              null, // memberId — null for seeded role-tier rates
              null, // projectId — org-level
              null, // customerId — org-level
              entry.currency(),
              entry.hourlyRate(),
              LocalDate.now(),
              null); // effectiveTo — open-ended
      billingRateRepository.save(rate);
      log.debug(
          "Created seeded billing rate: {} {} {} for tenant {}",
          entry.description(),
          entry.hourlyRate(),
          entry.currency(),
          tenantId);
    }
  }
}
