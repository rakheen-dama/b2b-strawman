package io.b2mash.b2b.b2bstrawman.automation.template;

import io.b2mash.b2b.b2bstrawman.automation.AutomationAction;
import io.b2mash.b2b.b2bstrawman.automation.AutomationActionRepository;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRule;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.RuleSource;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.AutomationTemplatePack;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class AutomationTemplateSeeder {

  private static final Logger log = LoggerFactory.getLogger(AutomationTemplateSeeder.class);
  private static final String PACK_LOCATION = "classpath:automation-templates/*.json";
  private static final UUID SYSTEM_USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  private final ResourcePatternResolver resourceResolver;
  private final ObjectMapper objectMapper;
  private final AutomationRuleRepository ruleRepository;
  private final AutomationActionRepository actionRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TenantTransactionHelper tenantTransactionHelper;

  public AutomationTemplateSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      AutomationRuleRepository ruleRepository,
      AutomationActionRepository actionRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper) {
    this.resourceResolver = resourceResolver;
    this.objectMapper = objectMapper;
    this.ruleRepository = ruleRepository;
    this.actionRepository = actionRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.tenantTransactionHelper = tenantTransactionHelper;
  }

  public void seedPacksForTenant(String tenantId, String orgId) {
    tenantTransactionHelper.executeInTenantTransaction(tenantId, orgId, t -> doSeedPacks(t));
  }

  private void doSeedPacks(String tenantId) {
    List<AutomationTemplatePack> packs = loadPacks();
    if (packs.isEmpty()) {
      log.info("No automation template packs found on classpath for tenant {}", tenantId);
      return;
    }

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings("USD");
                  return orgSettingsRepository.save(newSettings);
                });

    for (AutomationTemplatePack pack : packs) {
      if (settings.isAutomationPackApplied(pack.packId())) {
        log.info(
            "Automation pack {} already applied for tenant {}, skipping", pack.packId(), tenantId);
        continue;
      }

      applyPack(pack);
      settings.recordAutomationPackApplication(pack.packId(), pack.version());
      log.info(
          "Applied automation pack {} v{} for tenant {}", pack.packId(), pack.version(), tenantId);
    }

    orgSettingsRepository.save(settings);
  }

  private List<AutomationTemplatePack> loadPacks() {
    try {
      Resource[] resources = resourceResolver.getResources(PACK_LOCATION);
      return Arrays.stream(resources)
          .map(
              resource -> {
                try {
                  String content = resource.getContentAsString(StandardCharsets.UTF_8);
                  return objectMapper.readValue(content, AutomationTemplatePack.class);
                } catch (Exception e) {
                  throw new IllegalStateException(
                      "Failed to parse automation template pack: " + resource.getFilename(), e);
                }
              })
          .toList();
    } catch (IOException e) {
      log.warn("Failed to scan for automation template packs at {}", PACK_LOCATION, e);
      return List.of();
    }
  }

  private void applyPack(AutomationTemplatePack pack) {
    for (var template : pack.templates()) {
      var rule =
          new AutomationRule(
              template.name(),
              template.description(),
              template.triggerType(),
              template.triggerConfig(),
              template.conditions(),
              RuleSource.TEMPLATE,
              template.slug(),
              SYSTEM_USER_ID);
      // Constructor sets enabled=true; toggle to false for seeded templates
      rule.toggle();
      rule = ruleRepository.save(rule);

      for (var actionDef : template.actions()) {
        var action =
            new AutomationAction(
                rule.getId(),
                actionDef.sortOrder(),
                actionDef.actionType(),
                actionDef.actionConfig(),
                actionDef.delayDuration(),
                actionDef.delayUnit());
        actionRepository.save(action);
      }
    }
  }
}
