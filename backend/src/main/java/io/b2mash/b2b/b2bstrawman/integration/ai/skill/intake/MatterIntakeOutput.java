package io.b2mash.b2b.b2bstrawman.integration.ai.skill.intake;

import java.util.List;
import java.util.UUID;

/**
 * Structured output from the matter intake AI skill. Parsed from JSON via Jackson ObjectMapper. All
 * fields correspond to the output-schema.json on the classpath.
 */
public record MatterIntakeOutput(
    MatterClassification matterClassification,
    TemplateRecommendation templateRecommendation,
    List<RequiredDocument> requiredDocuments,
    FeeEstimate feeEstimate,
    ConflictScreening conflictScreening,
    List<String> riskFlags) {

  public record MatterClassification(String recommendedType, double confidence, String reasoning) {}

  public record TemplateRecommendation(
      UUID templateId, String templateName, String reasoning, String customisationNotes) {}

  public record RequiredDocument(String documentType, String reasoning, String priority) {}

  public record FeeEstimate(
      String tariffBasis,
      long estimatedRangeMinCents,
      long estimatedRangeMaxCents,
      String reasoning,
      List<String> assumptions) {}

  public record ConflictScreening(String status, List<ConflictMatch> matches) {}

  public record ConflictMatch(
      String existingMatterName, String customerName, String matchType, String reasoning) {}
}
