package io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit;

import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of firm-wide compliance data aggregated across multiple services. Designed for
 * token-efficient AI prompt assembly (~15K tokens target).
 */
public record ComplianceSnapshot(
    FicaCddSummary ficaCdd,
    PopiaSummary popia,
    TrustAccountingSummary trustAccounting,
    PrescriptionSummary prescription,
    RetentionSummary retention,
    int totalActiveCustomers,
    String dataCollectionNotes) {

  /** FICA/CDD compliance summary with flagged customers for AI attention. */
  public record FicaCddSummary(
      int compliant,
      int nonCompliant,
      int criticallyOverdue,
      List<FlaggedCustomer> flaggedCustomers) {}

  /** POPIA processing activity and DSAR status summary. */
  public record PopiaSummary(
      int registeredActivities, int unregisteredActivities, int pendingDsars, int overdueDsars) {}

  /** Trust accounting module summary (module-gated). */
  public record TrustAccountingSummary(
      boolean moduleEnabled,
      int accountCount,
      int unreconciledItems,
      List<String> boundaryViolations) {}

  /** Prescription tracking module summary (module-gated). */
  public record PrescriptionSummary(
      boolean moduleEnabled,
      int approachingCount,
      int expiredCount,
      List<FlaggedMatter> flaggedMatters) {}

  /** Retention policy evaluation summary. */
  public record RetentionSummary(int approachingExpiry, int pastExpiry) {}

  /** A customer flagged for compliance attention. */
  public record FlaggedCustomer(UUID id, String name, String issue) {}

  /** A matter flagged for prescription concern. */
  public record FlaggedMatter(UUID id, String name, String prescriptionDate, String issue) {}
}
