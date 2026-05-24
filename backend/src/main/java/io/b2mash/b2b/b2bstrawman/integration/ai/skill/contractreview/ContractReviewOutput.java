package io.b2mash.b2b.b2bstrawman.integration.ai.skill.contractreview;

import java.util.List;

/**
 * Structured output from the contract review AI skill. Parsed from JSON via Jackson ObjectMapper.
 * All fields correspond to the output-schema.json on the classpath.
 */
public record ContractReviewOutput(
    DocumentClassification documentClassification,
    String executiveSummary,
    List<Finding> findings,
    List<MissingProtection> missingProtections,
    String overallRiskAssessment,
    List<RecommendedAction> recommendedActions) {

  public record DocumentClassification(
      String type, String subtype, List<String> partiesIdentified) {}

  public record Finding(
      String severity,
      String category,
      String clauseReference,
      String title,
      String description,
      String riskExplanation,
      String recommendation,
      String statutoryReference) {}

  public record MissingProtection(
      String protection, String reasoning, String recommendation, String priority) {}

  public record RecommendedAction(String action, String reasoning) {}
}
