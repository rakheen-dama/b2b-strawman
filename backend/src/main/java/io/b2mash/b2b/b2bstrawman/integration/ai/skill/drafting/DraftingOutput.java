package io.b2mash.b2b.b2bstrawman.integration.ai.skill.drafting;

import java.util.List;
import java.util.UUID;

/**
 * Structured output from the template-guided drafting AI skill. Parsed from JSON via Jackson
 * ObjectMapper. All fields correspond to the output-schema.json on the classpath.
 */
public record DraftingOutput(
    UUID templateId,
    List<VariableFill> variableFills,
    List<NarrativeSection> narrativeSections,
    List<ClauseRecommendation> clauseRecommendations,
    List<String> warnings,
    List<RecommendedAction> recommendedActions) {

  public record VariableFill(
      String variableName, String value, String source, String confidence, String flag) {}

  public record NarrativeSection(String sectionName, String content, String notes) {}

  public record ClauseRecommendation(UUID clauseId, String clauseName, String reasoning) {}

  public record RecommendedAction(String action, String reasoning) {}
}
