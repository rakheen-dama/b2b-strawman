package io.b2mash.b2b.b2bstrawman.retention;

import java.util.List;

/** Response DTO for the retention evaluation (preview) endpoint. */
public record RetentionEvaluationResult(
    int totalPoliciesEvaluated, int entitiesEligibleForPurge, List<PolicySummary> policySummaries) {

  public static RetentionEvaluationResult from(
      RetentionCheckResult checkResult, int totalPoliciesEvaluated) {
    var summaries =
        checkResult.getFlagged().values().stream()
            .map(f -> new PolicySummary(f.recordType(), f.triggerEvent(), f.action(), f.count()))
            .toList();
    return new RetentionEvaluationResult(
        totalPoliciesEvaluated, checkResult.getTotalFlagged(), summaries);
  }

  /** Per-policy summary included in evaluation results. */
  public record PolicySummary(
      String recordType, String triggerEvent, String action, int eligibleCount) {}
}
