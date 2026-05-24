package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit.ComplianceAuditOutput;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplianceAuditReportServiceTest {

  private static final String ORG_ID = "org_compliance_rpt_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ComplianceAuditReportService reportService;
  @Autowired private ComplianceAuditReportRepository reportRepository;
  @Autowired private ComplianceAuditFindingRepository findingRepository;
  @Autowired private AiExecutionRepository executionRepository;

  @MockitoBean private ChecklistInstanceService checklistInstanceService;
  @MockitoBean private ConflictCheckService conflictCheckService;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Compliance Report Service Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_comp_rpt_owner", "comp_rpt_owner@test.com", "Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void publishReport_createsReportWithPublishedStatus() {
    runInTenantScope(
        () -> {
          var execution = createExecution();
          var output = createMockOutput();

          var report = reportService.publishReport(output, execution.getId(), ownerMemberId);

          assertThat(report.getId()).isNotNull();
          assertThat(report.getStatus()).isEqualTo("PUBLISHED");
          assertThat(report.getOverallGrade()).isEqualTo("B");
          assertThat(report.getOverallAssessment()).startsWith("Overall compliance posture");
          assertThat(report.getPublishedBy()).isEqualTo(ownerMemberId);
          assertThat(report.getPublishedAt()).isNotNull();
        });
  }

  @Test
  void publishReport_createsFindingsFromOutput() {
    runInTenantScope(
        () -> {
          var execution = createExecution();
          var output = createMockOutput();

          var report = reportService.publishReport(output, execution.getId(), ownerMemberId);

          var findings =
              findingRepository.findByReportIdOrderBySeverity(
                  report.getId(), org.springframework.data.domain.PageRequest.of(0, 10));

          assertThat(findings.getTotalElements()).isEqualTo(2);
          var findingList = findings.getContent();

          // Findings are ordered by severity ASC (alphabetical: CRITICAL < HIGH)
          var criticalFinding =
              findingList.stream().filter(f -> "CRITICAL".equals(f.getSeverity())).findFirst();
          assertThat(criticalFinding).isPresent();
          assertThat(criticalFinding.get().getFindingId()).isEqualTo("F-001");
          assertThat(criticalFinding.get().getCategory()).isEqualTo("FICA_CDD");
          assertThat(criticalFinding.get().getTitle()).isEqualTo("Missing CDD documents");
          assertThat(criticalFinding.get().getStatus()).isEqualTo("OPEN");
          assertThat(criticalFinding.get().getEntityType()).isEqualTo("customer");
          assertThat(criticalFinding.get().getEntityId()).isNotNull();
        });
  }

  @Test
  void publishReport_archivesPreviousReport() {
    runInTenantScope(
        () -> {
          var execution1 = createExecution();
          var output1 = createMockOutput();
          var firstReport = reportService.publishReport(output1, execution1.getId(), ownerMemberId);
          UUID firstReportId = firstReport.getId();

          var execution2 = createExecution();
          var output2 = createMockOutput();
          reportService.publishReport(output2, execution2.getId(), ownerMemberId);

          var archivedReport = reportRepository.findById(firstReportId).orElseThrow();
          assertThat(archivedReport.getStatus()).isEqualTo("ARCHIVED");
        });
  }

  @Test
  void findingTransition_openToAcknowledged_succeeds() {
    runInTenantScope(
        () -> {
          var report = publishReportWithFindings();
          var finding = getFirstFinding(report.getId());

          var updated =
              reportService.updateFindingStatus(
                  report.getId(), finding.getId(), "ACKNOWLEDGED", null, ownerMemberId);

          assertThat(updated.getStatus()).isEqualTo("ACKNOWLEDGED");
        });
  }

  @Test
  void findingTransition_acknowledgedToInProgress_succeeds() {
    runInTenantScope(
        () -> {
          var report = publishReportWithFindings();
          var finding = getFirstFinding(report.getId());

          reportService.updateFindingStatus(
              report.getId(), finding.getId(), "ACKNOWLEDGED", null, ownerMemberId);
          var updated =
              reportService.updateFindingStatus(
                  report.getId(), finding.getId(), "IN_PROGRESS", null, ownerMemberId);

          assertThat(updated.getStatus()).isEqualTo("IN_PROGRESS");
        });
  }

  @Test
  void findingTransition_inProgressToResolved_requiresResolutionNotes() {
    runInTenantScope(
        () -> {
          var report = publishReportWithFindings();
          var finding = getFirstFinding(report.getId());

          reportService.updateFindingStatus(
              report.getId(), finding.getId(), "ACKNOWLEDGED", null, ownerMemberId);
          reportService.updateFindingStatus(
              report.getId(), finding.getId(), "IN_PROGRESS", null, ownerMemberId);

          var resolved =
              reportService.updateFindingStatus(
                  report.getId(),
                  finding.getId(),
                  "RESOLVED",
                  "Issue remediated by uploading documents",
                  ownerMemberId);

          assertThat(resolved.getStatus()).isEqualTo("RESOLVED");
          assertThat(resolved.getResolvedBy()).isEqualTo(ownerMemberId);
          assertThat(resolved.getResolvedAt()).isNotNull();
          assertThat(resolved.getResolutionNotes())
              .isEqualTo("Issue remediated by uploading documents");
        });
  }

  @Test
  void findingTransition_resolveWithNullNotes_throwsInvalidState() {
    runInTenantScope(
        () -> {
          var report = publishReportWithFindings();
          var finding = getFirstFinding(report.getId());

          reportService.updateFindingStatus(
              report.getId(), finding.getId(), "ACKNOWLEDGED", null, ownerMemberId);
          reportService.updateFindingStatus(
              report.getId(), finding.getId(), "IN_PROGRESS", null, ownerMemberId);

          UUID reportId = report.getId();
          UUID findingUuid = finding.getId();
          assertThatThrownBy(
                  () ->
                      reportService.updateFindingStatus(
                          reportId, findingUuid, "RESOLVED", null, ownerMemberId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void findingTransition_resolveWithBlankNotes_throwsInvalidState() {
    runInTenantScope(
        () -> {
          var report = publishReportWithFindings();
          var finding = getFirstFinding(report.getId());

          reportService.updateFindingStatus(
              report.getId(), finding.getId(), "ACKNOWLEDGED", null, ownerMemberId);
          reportService.updateFindingStatus(
              report.getId(), finding.getId(), "IN_PROGRESS", null, ownerMemberId);

          UUID reportId = report.getId();
          UUID findingUuid = finding.getId();
          assertThatThrownBy(
                  () ->
                      reportService.updateFindingStatus(
                          reportId, findingUuid, "RESOLVED", "  ", ownerMemberId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void findingTransition_backwardTransition_throwsInvalidState() {
    runInTenantScope(
        () -> {
          var report = publishReportWithFindings();
          var finding = getFirstFinding(report.getId());

          reportService.updateFindingStatus(
              report.getId(), finding.getId(), "ACKNOWLEDGED", null, ownerMemberId);
          reportService.updateFindingStatus(
              report.getId(), finding.getId(), "IN_PROGRESS", null, ownerMemberId);
          reportService.updateFindingStatus(
              report.getId(), finding.getId(), "RESOLVED", "Fixed", ownerMemberId);

          UUID reportId = report.getId();
          UUID findingUuid = finding.getId();
          assertThatThrownBy(
                  () ->
                      reportService.updateFindingStatus(
                          reportId, findingUuid, "OPEN", null, ownerMemberId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void findingTransition_inProgressToFalsePositive_setsResolvedFields() {
    runInTenantScope(
        () -> {
          var report = publishReportWithFindings();
          var finding = getFirstFinding(report.getId());

          reportService.updateFindingStatus(
              report.getId(), finding.getId(), "ACKNOWLEDGED", null, ownerMemberId);
          reportService.updateFindingStatus(
              report.getId(), finding.getId(), "IN_PROGRESS", null, ownerMemberId);

          var falsePositive =
              reportService.updateFindingStatus(
                  report.getId(),
                  finding.getId(),
                  "FALSE_POSITIVE",
                  "Customer was already verified under different record",
                  ownerMemberId);

          assertThat(falsePositive.getStatus()).isEqualTo("FALSE_POSITIVE");
          assertThat(falsePositive.getResolvedBy()).isEqualTo(ownerMemberId);
          assertThat(falsePositive.getResolvedAt()).isNotNull();
          assertThat(falsePositive.getResolutionNotes())
              .isEqualTo("Customer was already verified under different record");
        });
  }

  // -- Helpers --

  private void runInTenantScope(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE", "AI_REVIEW", "AI_MANAGE"))
        .run(action);
  }

  private ComplianceAuditReport publishReportWithFindings() {
    var execution = createExecution();
    var output = createMockOutput();
    return reportService.publishReport(output, execution.getId(), ownerMemberId);
  }

  private ComplianceAuditFinding getFirstFinding(UUID reportId) {
    return findingRepository
        .findByReportIdOrderBySeverity(
            reportId, org.springframework.data.domain.PageRequest.of(0, 1))
        .getContent()
        .getFirst();
  }

  private AiExecution createExecution() {
    var execution =
        new AiExecution(
            "compliance-audit", "FIRM", UUID.randomUUID(), ownerMemberId, "claude-sonnet-4-6", 1);
    execution.markCompleted(
        new AiCompletionResponse(
            "output", "claude-sonnet-4-6", 2000, 800, 1500, 0, "end_turn", 5000L),
        4250L);
    return executionRepository.save(execution);
  }

  private ComplianceAuditOutput createMockOutput() {
    var entityRef =
        new ComplianceAuditOutput.EntityReference("customer", UUID.randomUUID(), "Test Customer");
    var finding1 =
        new ComplianceAuditOutput.AuditFinding(
            "F-001",
            "CRITICAL",
            "FICA_CDD",
            "Missing CDD documents",
            "Customer does not have verified identity documents on file",
            "FICA Section 21",
            "Upload and verify identity documents",
            List.of(entityRef));
    var finding2 =
        new ComplianceAuditOutput.AuditFinding(
            "F-002",
            "HIGH",
            "POPIA",
            "Unregistered processing activity",
            "Email marketing processing activity not registered with POPIA officer",
            "POPIA Section 18",
            "Register processing activity",
            List.of());
    var categoryScore = new ComplianceAuditOutput.CategoryScore("C", 5, 3, 1);
    var recommendation =
        new ComplianceAuditOutput.Recommendation(
            "HIGH", "Conduct CDD review for all flagged customers", "2 weeks");
    return new ComplianceAuditOutput(
        "2026-05-24",
        "B",
        "Overall compliance posture is adequate with critical gaps in FICA CDD",
        Map.of("FICA_CDD", categoryScore),
        List.of(finding1, finding2),
        List.of(recommendation));
  }
}
