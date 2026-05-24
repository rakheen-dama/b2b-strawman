package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit.ComplianceAuditOutput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceAuditReportService {

  private static final Logger log = LoggerFactory.getLogger(ComplianceAuditReportService.class);

  private final ComplianceAuditReportRepository reportRepository;
  private final ComplianceAuditFindingRepository findingRepository;
  private final AiExecutionRepository executionRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public ComplianceAuditReportService(
      ComplianceAuditReportRepository reportRepository,
      ComplianceAuditFindingRepository findingRepository,
      AiExecutionRepository executionRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher) {
    this.reportRepository = reportRepository;
    this.findingRepository = findingRepository;
    this.executionRepository = executionRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public ComplianceAuditReport publishReport(
      ComplianceAuditOutput output, UUID executionId, UUID memberId) {

    AiExecution execution =
        executionRepository
            .findById(executionId)
            .orElseThrow(() -> new ResourceNotFoundException("AiExecution", executionId));

    // Archive all existing PUBLISHED reports
    Page<ComplianceAuditReport> publishedReports =
        reportRepository.findByStatusOrderByCreatedAtDesc(
            ReportStatus.PUBLISHED.name(), Pageable.unpaged());
    for (ComplianceAuditReport previous : publishedReports) {
      previous.archive(memberId);
      reportRepository.save(previous);
      log.info("Archived previous published report id={}", previous.getId());
    }

    // Convert categoryScores to Map<String, Object> for JSONB storage
    Map<String, Object> categoryScoresMap = convertCategoryScores(output.categoryScores());

    // Create the new report
    var report =
        new ComplianceAuditReport(
            execution,
            output.overallGrade(),
            output.overallAssessment(),
            categoryScoresMap,
            memberId);
    report.publish(memberId);
    report = reportRepository.save(report);

    // Create findings
    List<ComplianceAuditFinding> findings = new ArrayList<>();
    for (ComplianceAuditOutput.AuditFinding auditFinding : output.findings()) {
      String entityType = null;
      UUID entityId = null;
      if (auditFinding.entityReferences() != null && !auditFinding.entityReferences().isEmpty()) {
        ComplianceAuditOutput.EntityReference firstRef = auditFinding.entityReferences().getFirst();
        entityType = firstRef.type();
        entityId = firstRef.id();
      }

      var finding =
          new ComplianceAuditFinding(
              report,
              auditFinding.id(),
              auditFinding.severity(),
              auditFinding.category(),
              auditFinding.title(),
              auditFinding.description(),
              auditFinding.regulatoryBasis(),
              auditFinding.remediation(),
              entityType,
              entityId,
              memberId);
      findings.add(finding);
    }
    findingRepository.saveAll(findings);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("compliance.report.published")
            .entityType("compliance_audit_report")
            .entityId(report.getId())
            .details(
                Map.of(
                    "overallGrade",
                    report.getOverallGrade(),
                    "executionId",
                    executionId.toString(),
                    "findingCount",
                    String.valueOf(findings.size())))
            .build());

    log.info(
        "Published compliance audit report id={} with {} findings",
        report.getId(),
        findings.size());

    return report;
  }

  @Transactional(readOnly = true)
  public Page<ComplianceAuditReport> findReports(Pageable pageable) {
    return reportRepository.findByStatusOrderByCreatedAtDesc(
        ReportStatus.PUBLISHED.name(), pageable);
  }

  @Transactional(readOnly = true)
  public ComplianceAuditReport findReport(UUID id) {
    return reportRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ComplianceAuditReport", id));
  }

  @Transactional(readOnly = true)
  public Page<ComplianceAuditFinding> findFindings(
      UUID reportId, FindingFilterParams filterParams, Pageable pageable) {
    // Verify report exists
    if (!reportRepository.existsById(reportId)) {
      throw new ResourceNotFoundException("ComplianceAuditReport", reportId);
    }

    boolean hasFilter =
        filterParams != null
            && (filterParams.severities() != null
                || filterParams.categories() != null
                || filterParams.statuses() != null);

    if (!hasFilter) {
      return findingRepository.findByReportIdOrderBySeverity(reportId, pageable);
    }

    return findingRepository.findByReportIdFiltered(
        reportId,
        nullIfEmpty(filterParams.severities()),
        nullIfEmpty(filterParams.categories()),
        nullIfEmpty(filterParams.statuses()),
        pageable);
  }

  @Transactional
  public ComplianceAuditFinding updateFindingStatus(
      UUID reportId, UUID findingId, String newStatus, String resolutionNotes, UUID memberId) {

    var finding =
        findingRepository
            .findById(findingId)
            .orElseThrow(() -> new ResourceNotFoundException("ComplianceAuditFinding", findingId));

    // Verify finding belongs to the specified report
    if (!finding.getReport().getId().equals(reportId)) {
      throw new ResourceNotFoundException("ComplianceAuditFinding", findingId);
    }

    String oldStatus = finding.getStatus();
    FindingStatus targetStatus;
    try {
      targetStatus = FindingStatus.valueOf(newStatus);
    } catch (IllegalArgumentException e) {
      throw new io.b2mash.b2b.b2bstrawman.exception.InvalidStateException(
          "Invalid target status", "Unknown finding status: " + newStatus);
    }

    switch (targetStatus) {
      case ACKNOWLEDGED -> finding.acknowledge(memberId);
      case IN_PROGRESS -> finding.startProgress(memberId);
      case RESOLVED -> finding.resolve(memberId, resolutionNotes);
      case FALSE_POSITIVE -> finding.markFalsePositive(memberId, resolutionNotes);
      default ->
          throw new io.b2mash.b2b.b2bstrawman.exception.InvalidStateException(
              "Invalid target status", "Cannot transition to " + newStatus);
    }

    finding = findingRepository.save(finding);

    eventPublisher.publishEvent(
        new ComplianceFindingStatusChangedEvent(
            finding.getId(), reportId, finding.getFindingId(), oldStatus, newStatus, memberId));

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("compliance.finding.status_changed")
            .entityType("compliance_audit_finding")
            .entityId(finding.getId())
            .details(
                Map.of(
                    "findingId",
                    finding.getFindingId(),
                    "reportId",
                    reportId.toString(),
                    "oldStatus",
                    oldStatus,
                    "newStatus",
                    newStatus))
            .build());

    return finding;
  }

  private static List<String> nullIfEmpty(List<String> list) {
    return (list == null || list.isEmpty()) ? null : list;
  }

  private static Map<String, Object> convertCategoryScores(
      Map<String, ComplianceAuditOutput.CategoryScore> scores) {
    Map<String, Object> result = new HashMap<>();
    if (scores != null) {
      scores.forEach(
          (key, score) ->
              result.put(
                  key,
                  Map.of(
                      "grade", score.grade(),
                      "compliant", score.compliant(),
                      "nonCompliant", score.nonCompliant(),
                      "critical", score.critical())));
    }
    return result;
  }
}
