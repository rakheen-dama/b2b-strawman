package io.b2mash.b2b.b2bstrawman.automation.template;

import io.b2mash.b2b.b2bstrawman.automation.AutomationAction;
import io.b2mash.b2b.b2bstrawman.automation.AutomationActionRepository;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRule;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.RuleSource;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.AutomationTemplatePack;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class AutomationTemplateSeeder extends AbstractPackSeeder<AutomationTemplatePack> {

  private static final String PACK_LOCATION = "classpath:automation-templates/*.json";
  private static final UUID SYSTEM_USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  private final AutomationRuleRepository ruleRepository;
  private final AutomationActionRepository actionRepository;

  public AutomationTemplateSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      AutomationRuleRepository ruleRepository,
      AutomationActionRepository actionRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper) {
    super(resourceResolver, objectMapper, orgSettingsRepository, tenantTransactionHelper);
    this.ruleRepository = ruleRepository;
    this.actionRepository = actionRepository;
  }

  @Override
  protected String getPackResourcePattern() {
    return PACK_LOCATION;
  }

  @Override
  protected Class<AutomationTemplatePack> getPackDefinitionType() {
    return AutomationTemplatePack.class;
  }

  @Override
  protected String getPackTypeName() {
    return "automation";
  }

  @Override
  protected String getPackId(AutomationTemplatePack pack) {
    return pack.packId();
  }

  @Override
  protected String getPackVersion(AutomationTemplatePack pack) {
    return String.valueOf(pack.version());
  }

  @Override
  protected boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    return settings.isAutomationPackApplied(packId);
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, AutomationTemplatePack pack) {
    settings.recordAutomationPackApplication(pack.packId(), pack.version());
  }

  @Override
  protected void applyPack(AutomationTemplatePack pack, Resource packResource, String tenantId) {
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
