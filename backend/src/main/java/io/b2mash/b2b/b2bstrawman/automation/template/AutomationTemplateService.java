package io.b2mash.b2b.b2bstrawman.automation.template;

import io.b2mash.b2b.b2bstrawman.automation.AutomationAction;
import io.b2mash.b2b.b2bstrawman.automation.AutomationActionRepository;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRule;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.RuleSource;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationActionResponse;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationRuleResponse;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.AutomationTemplatePack;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.TemplateDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class AutomationTemplateService {

  private static final Logger log = LoggerFactory.getLogger(AutomationTemplateService.class);
  private static final String MODULE_ID = "automation_builder";
  private static final String PACK_LOCATION = "classpath:automation-templates/*.json";

  private final ResourcePatternResolver resourceResolver;
  private final ObjectMapper objectMapper;
  private final AutomationRuleRepository ruleRepository;
  private final AutomationActionRepository actionRepository;
  private final VerticalModuleGuard moduleGuard;

  public AutomationTemplateService(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      AutomationRuleRepository ruleRepository,
      AutomationActionRepository actionRepository,
      VerticalModuleGuard moduleGuard) {
    this.resourceResolver = resourceResolver;
    this.objectMapper = objectMapper;
    this.ruleRepository = ruleRepository;
    this.actionRepository = actionRepository;
    this.moduleGuard = moduleGuard;
  }

  @Transactional(readOnly = true)
  public List<TemplateDefinitionResponse> listTemplates() {
    moduleGuard.requireModule(MODULE_ID);

    List<AutomationTemplateDefinition> allTemplates = loadAllTemplateDefinitions();
    return allTemplates.stream()
        .map(
            t ->
                new TemplateDefinitionResponse(
                    t.slug(),
                    t.name(),
                    t.description(),
                    t.category(),
                    t.triggerType().name(),
                    t.triggerConfig(),
                    t.actions().size()))
        .toList();
  }

  public AutomationRuleResponse activateTemplate(String slug) {
    moduleGuard.requireModule(MODULE_ID);

    UUID memberId = RequestScopes.requireMemberId();
    var template =
        loadAllTemplateDefinitions().stream()
            .filter(t -> t.slug().equals(slug))
            .findFirst()
            .orElseThrow(
                () ->
                    ResourceNotFoundException.withDetail(
                        "Template not found",
                        "No automation template found with slug '" + slug + "'"));

    var rule =
        new AutomationRule(
            template.name(),
            template.description(),
            template.triggerType(),
            template.triggerConfig(),
            template.conditions(),
            RuleSource.TEMPLATE,
            template.slug(),
            memberId);
    rule = ruleRepository.save(rule);

    List<AutomationAction> savedActions = new ArrayList<>();
    for (var actionDef : template.actions()) {
      var action =
          new AutomationAction(
              rule.getId(),
              actionDef.sortOrder(),
              actionDef.actionType(),
              actionDef.actionConfig(),
              actionDef.delayDuration(),
              actionDef.delayUnit());
      savedActions.add(actionRepository.save(action));
    }

    return toRuleResponse(rule, savedActions);
  }

  private List<AutomationTemplateDefinition> loadAllTemplateDefinitions() {
    try {
      Resource[] resources = resourceResolver.getResources(PACK_LOCATION);
      List<AutomationTemplateDefinition> allTemplates = new ArrayList<>();
      for (Resource resource : resources) {
        try {
          String content = resource.getContentAsString(StandardCharsets.UTF_8);
          var pack = objectMapper.readValue(content, AutomationTemplatePack.class);
          allTemplates.addAll(pack.templates());
        } catch (Exception e) {
          log.warn("Failed to parse automation template pack: {}", resource.getFilename(), e);
        }
      }
      return allTemplates;
    } catch (IOException e) {
      log.warn("Failed to scan for automation template packs at {}", PACK_LOCATION, e);
      return List.of();
    }
  }

  private AutomationRuleResponse toRuleResponse(
      AutomationRule rule, List<AutomationAction> actions) {
    return new AutomationRuleResponse(
        rule.getId(),
        rule.getName(),
        rule.getDescription(),
        rule.isEnabled(),
        rule.getTriggerType(),
        rule.getTriggerConfig(),
        rule.getConditions(),
        rule.getSource(),
        rule.getTemplateSlug(),
        rule.getCreatedBy(),
        rule.getCreatedAt(),
        rule.getUpdatedAt(),
        actions.stream().map(this::toActionResponse).toList());
  }

  private AutomationActionResponse toActionResponse(AutomationAction action) {
    return new AutomationActionResponse(
        action.getId(),
        action.getRuleId(),
        action.getSortOrder(),
        action.getActionType(),
        action.getActionConfig(),
        action.getDelayDuration(),
        action.getDelayUnit(),
        action.getCreatedAt(),
        action.getUpdatedAt());
  }
}
