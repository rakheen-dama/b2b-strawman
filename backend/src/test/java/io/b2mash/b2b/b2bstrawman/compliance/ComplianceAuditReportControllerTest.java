package io.b2mash.b2b.b2bstrawman.compliance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit.ComplianceAuditOutput;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplianceAuditReportControllerTest {

  private static final String ORG_ID = "org_compliance_rpt_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ComplianceAuditReportService reportService;
  @Autowired private ComplianceAuditFindingRepository findingRepository;
  @Autowired private AiExecutionRepository executionRepository;

  @MockitoBean private ChecklistInstanceService checklistInstanceService;
  @MockitoBean private ConflictCheckService conflictCheckService;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID reportId;
  private UUID findingUuid;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Compliance Report Controller Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_comp_ctrl_owner", "comp_ctrl_owner@test.com", "Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_comp_ctrl_member", "comp_ctrl_member@test.com", "Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Publish a report with findings for testing
    runInTenantScope(
        () -> {
          var report = publishReportWithFindings();
          reportId = report.getId();
          findingUuid = getFirstFinding(reportId).getId();
        });
  }

  @Test
  void listReports_returnsPaginatedList() throws Exception {
    mockMvc
        .perform(
            get("/api/compliance/audit-reports")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_comp_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].overallGrade").value("B"))
        .andExpect(jsonPath("$.content[0].status").value("PUBLISHED"));
  }

  @Test
  void getReport_returnsReportWithCategoryScoresAndFindingCounts() throws Exception {
    mockMvc
        .perform(
            get("/api/compliance/audit-reports/" + reportId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_comp_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(reportId.toString()))
        .andExpect(jsonPath("$.overallGrade").value("B"))
        .andExpect(jsonPath("$.overallAssessment").exists())
        .andExpect(jsonPath("$.categoryScores").isMap())
        .andExpect(jsonPath("$.findingCounts.critical").value(1))
        .andExpect(jsonPath("$.findingCounts.high").value(1));
  }

  @Test
  void listFindings_returnsFilterableList() throws Exception {
    mockMvc
        .perform(
            get("/api/compliance/audit-reports/" + reportId + "/findings")
                .param("severity", "CRITICAL")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_comp_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].severity").value("CRITICAL"))
        .andExpect(jsonPath("$.content[0].findingId").value("F-001"));
  }

  @Test
  void patchFindingStatus_transitionsCorrectly() throws Exception {
    // Create a fresh report so we get an OPEN finding to transition
    UUID[] ids = new UUID[2];
    runInTenantScope(
        () -> {
          var report = publishReportWithFindings();
          ids[0] = report.getId();
          ids[1] = getFirstFinding(report.getId()).getId();
        });

    mockMvc
        .perform(
            patch("/api/compliance/audit-reports/" + ids[0] + "/findings/" + ids[1])
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_comp_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"status": "ACKNOWLEDGED"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
  }

  @Test
  void patchFindingStatus_invalidTransitionReturns400() throws Exception {
    // Create a fresh report so we get an OPEN finding
    UUID[] ids = new UUID[2];
    runInTenantScope(
        () -> {
          var report = publishReportWithFindings();
          ids[0] = report.getId();
          ids[1] = getFirstFinding(report.getId()).getId();
        });

    // Attempt to skip directly from OPEN to RESOLVED (invalid: must go OPEN -> ACKNOWLEDGED -> ...)
    mockMvc
        .perform(
            patch("/api/compliance/audit-reports/" + ids[0] + "/findings/" + ids[1])
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_comp_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"status": "RESOLVED", "resolutionNotes": "Fixed"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listReports_requiresAiManageCapability() throws Exception {
    mockMvc
        .perform(
            get("/api/compliance/audit-reports")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_comp_ctrl_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void patchFindingStatus_requiresAiReviewCapability() throws Exception {
    mockMvc
        .perform(
            patch("/api/compliance/audit-reports/" + reportId + "/findings/" + findingUuid)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_comp_ctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"status": "ACKNOWLEDGED"}
                    """))
        .andExpect(status().isForbidden());
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

  private ComplianceAuditFinding getFirstFinding(UUID rptId) {
    return findingRepository
        .findByReportIdOrderBySeverity(rptId, org.springframework.data.domain.PageRequest.of(0, 1))
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
