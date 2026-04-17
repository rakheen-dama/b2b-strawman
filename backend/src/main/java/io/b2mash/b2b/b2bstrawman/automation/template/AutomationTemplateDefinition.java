package io.b2mash.b2b.b2bstrawman.automation.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.DelayUnit;
import io.b2mash.b2b.b2bstrawman.automation.TriggerType;
import java.util.List;
import java.util.Map;

/**
 * DTO records for deserializing automation template pack JSON files from the classpath.
 *
 * <p>{@link JsonIgnoreProperties}({@code ignoreUnknown = true}) is required so that pack authors
 * can attach documentation fields (e.g. {@code _comment}) to individual rules to flag known trigger
 * gaps (ADR-244) without breaking the loader. See {@code consulting-za.json} for the canonical use
 * of {@code _comment}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AutomationTemplateDefinition(
    String slug,
    String name,
    String description,
    String category,
    TriggerType triggerType,
    Map<String, Object> triggerConfig,
    List<Map<String, Object>> conditions,
    List<TemplateActionDefinition> actions) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TemplateActionDefinition(
      ActionType actionType,
      Map<String, Object> actionConfig,
      Integer delayDuration,
      DelayUnit delayUnit,
      int sortOrder) {}

  /** Top-level pack wrapper matching the JSON structure. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AutomationTemplatePack(
      String packId,
      int version,
      String verticalProfile,
      List<AutomationTemplateDefinition> templates) {}

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
