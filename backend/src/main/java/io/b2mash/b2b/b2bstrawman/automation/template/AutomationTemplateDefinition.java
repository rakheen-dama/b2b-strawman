package io.b2mash.b2b.b2bstrawman.automation.template;

import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.DelayUnit;
import io.b2mash.b2b.b2bstrawman.automation.TriggerType;
import java.util.List;
import java.util.Map;

/** DTO records for deserializing automation template pack JSON files from the classpath. */
public record AutomationTemplateDefinition(
    String slug,
    String name,
    String description,
    String category,
    TriggerType triggerType,
    Map<String, Object> triggerConfig,
    List<Map<String, Object>> conditions,
    List<TemplateActionDefinition> actions) {

  public record TemplateActionDefinition(
      ActionType actionType,
      Map<String, Object> actionConfig,
      Integer delayDuration,
      DelayUnit delayUnit,
      int sortOrder) {}

  /** Top-level pack wrapper matching the JSON structure. */
  public record AutomationTemplatePack(
      String packId, int version, List<AutomationTemplateDefinition> templates) {}

  /** Response DTO for listing available templates. */
  public record TemplateDefinitionResponse(
      String slug,
      String name,
      String description,
      String category,
      String triggerType,
      Map<String, Object> triggerConfig,
      int actionCount) {}
}
