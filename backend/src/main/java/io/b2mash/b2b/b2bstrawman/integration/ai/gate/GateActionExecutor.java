package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.compliance.ComplianceAuditReportService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit.ComplianceAuditOutput;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.contractreview.AiReviewReportGenerator;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.contractreview.ContractReviewOutput;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.drafting.AiDraftDocumentGenerator;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.drafting.DraftingOutput;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService.ResolveRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class GateActionExecutor {

  private static final Logger log = LoggerFactory.getLogger(GateActionExecutor.class);

  private final ChecklistInstanceService checklistInstanceService;
  private final ConflictCheckService conflictCheckService;
  private final AiReviewReportGenerator aiReviewReportGenerator;
  private final AiDraftDocumentGenerator aiDraftDocumentGenerator;
  private final ComplianceAuditReportService complianceAuditReportService;
  private final ObjectMapper objectMapper;

  public GateActionExecutor(
      ChecklistInstanceService checklistInstanceService,
      ConflictCheckService conflictCheckService,
      AiReviewReportGenerator aiReviewReportGenerator,
      AiDraftDocumentGenerator aiDraftDocumentGenerator,
      ComplianceAuditReportService complianceAuditReportService,
      ObjectMapper objectMapper) {
    this.checklistInstanceService = checklistInstanceService;
    this.conflictCheckService = conflictCheckService;
    this.aiReviewReportGenerator = aiReviewReportGenerator;
    this.aiDraftDocumentGenerator = aiDraftDocumentGenerator;
    this.complianceAuditReportService = complianceAuditReportService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void execute(AiExecutionGate gate) {
    GateAction action = parseAction(gate.getGateType(), gate.getProposedAction());
    UUID reviewerId = gate.getReviewedBy();

    switch (action) {
      case GateAction.MarkKycCompleteAction a -> executeMarkKycComplete(a, reviewerId);
      case GateAction.SelectMatterTemplateAction a -> {
        // No-op: frontend pre-fill only, no entity mutation
        log.info("Gate {} approved: template selection (frontend pre-fill)", gate.getId());
      }
      case GateAction.ClearConflictAction a -> executeClearConflict(a, reviewerId);
      case GateAction.CreateReviewReportAction a ->
          executeCreateReviewReport(a, gate.getExecution().getId(), reviewerId);
      case GateAction.CreateDraftDocumentAction a ->
          executeCreateDraftDocument(a, gate.getExecution().getId(), reviewerId);
      case GateAction.PublishComplianceReportAction a ->
          executePublishComplianceReport(a, gate.getExecution().getId(), reviewerId);
    }
  }

  private void executeMarkKycComplete(GateAction.MarkKycCompleteAction action, UUID reviewerId) {
    for (UUID itemId : action.checklistItemIds()) {
      checklistInstanceService.completeItem(itemId, action.completionNotes(), null, reviewerId);
    }
  }

  private void executeClearConflict(GateAction.ClearConflictAction action, UUID reviewerId) {
    var request = new ResolveRequest("CLEARED", action.clearanceNotes(), null);
    conflictCheckService.resolve(action.conflictCheckId(), request, reviewerId);
  }

  private void executeCreateReviewReport(
      GateAction.CreateReviewReportAction action, UUID executionId, UUID reviewerId) {
    ContractReviewOutput output =
        objectMapper.convertValue(action.reviewOutput(), ContractReviewOutput.class);
    aiReviewReportGenerator.generateReviewReport(
        output, action.projectId(), executionId, reviewerId);
    log.info(
        "Gate approved: created review report for project={} execution={}",
        action.projectId(),
        executionId);
  }

  private void executeCreateDraftDocument(
      GateAction.CreateDraftDocumentAction action, UUID executionId, UUID reviewerId) {
    DraftingOutput output = objectMapper.convertValue(action.draftOutput(), DraftingOutput.class);
    aiDraftDocumentGenerator.generateDraft(
        output, action.templateId(), action.projectId(), executionId, reviewerId);
    log.info(
        "Gate approved: created draft document for template={} project={} execution={}",
        action.templateId(),
        action.projectId(),
        executionId);
  }

  private void executePublishComplianceReport(
      GateAction.PublishComplianceReportAction action, UUID executionId, UUID reviewerId) {
    ComplianceAuditOutput output =
        objectMapper.convertValue(action.auditOutput(), ComplianceAuditOutput.class);
    complianceAuditReportService.publishReport(output, executionId, reviewerId);
    log.info("Gate approved: published compliance report for execution={}", executionId);
  }

  @SuppressWarnings("unchecked")
  private GateAction parseAction(String gateType, Map<String, Object> proposedAction) {
    try {
      return switch (gateType) {
        case "MARK_KYC_COMPLETE" -> {
          List<String> itemIdStrings = (List<String>) proposedAction.get("checklist_item_ids");
          List<UUID> itemIds = itemIdStrings.stream().map(UUID::fromString).toList();
          String notes = (String) proposedAction.get("completion_notes");
          yield new GateAction.MarkKycCompleteAction(itemIds, notes);
        }
        case "SELECT_MATTER_TEMPLATE" -> {
          UUID templateId = UUID.fromString((String) proposedAction.get("template_id"));
          String notes = (String) proposedAction.get("customisation_notes");
          yield new GateAction.SelectMatterTemplateAction(templateId, notes);
        }
        case "CONFIRM_CONFLICT_SCREEN" -> {
          UUID conflictCheckId = UUID.fromString((String) proposedAction.get("conflict_check_id"));
          String notes = (String) proposedAction.get("clearance_notes");
          yield new GateAction.ClearConflictAction(conflictCheckId, notes);
        }
        case "CREATE_REVIEW_REPORT" -> {
          UUID projectId = UUID.fromString((String) proposedAction.get("project_id"));
          UUID documentId = UUID.fromString((String) proposedAction.get("document_id"));
          Map<String, Object> reviewOutput =
              (Map<String, Object>) proposedAction.get("review_output");
          yield new GateAction.CreateReviewReportAction(projectId, documentId, reviewOutput);
        }
        case "CREATE_DRAFT_DOCUMENT" -> {
          UUID draftTemplateId = UUID.fromString((String) proposedAction.get("template_id"));
          UUID projectId = UUID.fromString((String) proposedAction.get("project_id"));
          Map<String, Object> draftOutput =
              (Map<String, Object>) proposedAction.get("draft_output");
          yield new GateAction.CreateDraftDocumentAction(draftTemplateId, projectId, draftOutput);
        }
        case "PUBLISH_COMPLIANCE_REPORT" -> {
          Map<String, Object> auditOutput =
              (Map<String, Object>) proposedAction.get("audit_output");
          yield new GateAction.PublishComplianceReportAction(auditOutput);
        }
        default -> throw new IllegalArgumentException("Unknown gate type: " + gateType);
      };
    } catch (NullPointerException | ClassCastException | IllegalArgumentException e) {
      if (e instanceof IllegalArgumentException && e.getMessage().startsWith("Unknown gate type")) {
        throw e;
      }
      throw new IllegalStateException(
          "Invalid gate action data for type " + gateType + ": " + e.getMessage(), e);
    }
  }
}
