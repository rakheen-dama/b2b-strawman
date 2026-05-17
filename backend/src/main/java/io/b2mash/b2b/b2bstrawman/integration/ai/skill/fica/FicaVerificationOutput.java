package io.b2mash.b2b.b2bstrawman.integration.ai.skill.fica;

import java.util.List;
import java.util.UUID;

/**
 * Structured output from the FICA verification AI skill. Parsed from JSON via Jackson ObjectMapper.
 * All fields correspond to the output-schema.json on the classpath.
 */
public record FicaVerificationOutput(
    String overallAssessment,
    String riskLevel,
    List<ChecklistReview> checklistReview,
    List<String> missingDocuments,
    List<String> riskFlags,
    List<RecommendedAction> recommendedActions) {

  public record ChecklistReview(
      UUID checklistItemId,
      String itemName,
      String status,
      String evidenceDocument,
      String reasoning,
      List<String> flags) {}

  public record RecommendedAction(
      String action, List<UUID> items, String reasoning, String documentType) {}
}
