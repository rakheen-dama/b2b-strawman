package io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ComplianceAuditOutput(
    String auditDate,
    String overallGrade,
    String overallAssessment,
    Map<String, CategoryScore> categoryScores,
    List<AuditFinding> findings,
    List<Recommendation> recommendations) {

  public record CategoryScore(String grade, int compliant, int nonCompliant, int critical) {}

  public record AuditFinding(
      String id,
      String severity,
      String category,
      String title,
      String description,
      String regulatoryBasis,
      String remediation,
      List<EntityReference> entityReferences) {}

  public record EntityReference(String type, UUID id, String name) {}

  public record Recommendation(String priority, String recommendation, String estimatedEffort) {}
}
