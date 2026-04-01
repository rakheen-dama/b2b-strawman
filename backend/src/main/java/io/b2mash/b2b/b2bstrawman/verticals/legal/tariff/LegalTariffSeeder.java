package io.b2mash.b2b.b2bstrawman.verticals.legal.tariff;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds LSSA tariff schedule data for legal-za tenants. Unlike pack seeders that extend {@link
 * io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder}, tariff schedules are standalone entities
 * with their own idempotency check (by schedule name + isSystem flag).
 */
@Service
public class LegalTariffSeeder {

  private static final Logger log = LoggerFactory.getLogger(LegalTariffSeeder.class);
  private static final String TARIFF_SEED_PATTERN = "classpath:tariff-seed/*.json";

  private final TariffScheduleRepository scheduleRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TenantTransactionHelper tenantTransactionHelper;
  private final ObjectMapper objectMapper;
  private final ResourcePatternResolver resourceResolver;

  public LegalTariffSeeder(
      TariffScheduleRepository scheduleRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper,
      ObjectMapper objectMapper,
      ResourcePatternResolver resourceResolver) {
    this.scheduleRepository = scheduleRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.tenantTransactionHelper = tenantTransactionHelper;
    this.objectMapper = objectMapper;
    this.resourceResolver = resourceResolver;
  }

  public void seedForTenant(String tenantId, String orgId) {
    tenantTransactionHelper.executeInTenantTransaction(tenantId, orgId, t -> doSeed(t));
  }

  private void doSeed(String tenantId) {
    var settings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    if (settings == null || !"legal-za".equals(settings.getVerticalProfile())) {
      log.debug("Skipping tariff seeding for tenant {} (not legal-za profile)", tenantId);
      return;
    }

    try {
      Resource[] resources = resourceResolver.getResources(TARIFF_SEED_PATTERN);
      for (Resource resource : resources) {
        seedFromResource(resource, tenantId);
      }
    } catch (IOException e) {
      log.error("Failed to load tariff seed resources for tenant {}", tenantId, e);
    }
  }

  private void seedFromResource(Resource resource, String tenantId) {
    try {
      var seedData = objectMapper.readValue(resource.getInputStream(), TariffSeedData.class);

      // Idempotency: skip if a system schedule with this name already exists
      var existing = scheduleRepository.findByNameAndIsSystemTrue(seedData.name());
      if (existing.isPresent()) {
        log.info(
            "Tariff schedule '{}' already exists for tenant {}, skipping",
            seedData.name(),
            tenantId);
        return;
      }

      var schedule =
          new TariffSchedule(
              seedData.name(),
              seedData.category(),
              seedData.courtLevel(),
              LocalDate.parse(seedData.effectiveFrom()),
              null,
              seedData.source());
      schedule.setSystem(true);
      schedule.setActive(true);

      for (var itemData : seedData.items()) {
        var item =
            new TariffItem(
                schedule,
                itemData.itemNumber(),
                itemData.section(),
                itemData.description(),
                BigDecimal.valueOf(itemData.amount()),
                itemData.unit(),
                itemData.notes(),
                itemData.sortOrder());
        schedule.getItems().add(item);
      }

      scheduleRepository.save(schedule);
      log.info(
          "Seeded tariff schedule '{}' with {} items for tenant {}",
          seedData.name(),
          seedData.items().size(),
          tenantId);

    } catch (IOException e) {
      log.error(
          "Failed to parse tariff seed file {} for tenant {}", resource.getFilename(), tenantId, e);
    }
  }

  record TariffSeedData(
      String name,
      String category,
      String courtLevel,
      String effectiveFrom,
      String source,
      List<TariffSeedItem> items) {}

  record TariffSeedItem(
      String itemNumber,
      String section,
      String description,
      double amount,
      String unit,
      String notes,
      int sortOrder) {}
}
