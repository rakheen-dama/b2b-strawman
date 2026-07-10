package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.collections.CollectionReminderSendService;
import io.b2mash.b2b.b2bstrawman.compliance.ComplianceAuditReportService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit.ComplianceAuditOutput;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.contractreview.AiReviewReportGenerator;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.contractreview.ContractReviewOutput;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.drafting.AiDraftDocumentGenerator;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.drafting.DraftingOutput;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService.ResolveRequest;
import java.time.DateTimeException;
import java.time.LocalDate;
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
  private final TaskService taskService;
  private final CollectionReminderSendService collectionReminderSendService;
  private final ObjectMapper objectMapper;

  public GateActionExecutor(
      ChecklistInstanceService checklistInstanceService,
      ConflictCheckService conflictCheckService,
      AiReviewReportGenerator aiReviewReportGenerator,
      AiDraftDocumentGenerator aiDraftDocumentGenerator,
      ComplianceAuditReportService complianceAuditReportService,
      TaskService taskService,
      CollectionReminderSendService collectionReminderSendService,
      ObjectMapper objectMapper) {
    this.checklistInstanceService = checklistInstanceService;
    this.conflictCheckService = conflictCheckService;
    this.aiReviewReportGenerator = aiReviewReportGenerator;
    this.aiDraftDocumentGenerator = aiDraftDocumentGenerator;
    this.complianceAuditReportService = complianceAuditReportService;
    this.taskService = taskService;
    this.collectionReminderSendService = collectionReminderSendService;
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
      case GateAction.CreateTaskFromCorrespondenceAction a -> executeCreateTask(a, reviewerId);
      case GateAction.SendCollectionReminderAction a -> executeSendCollectionReminder(a);
    }
  }

  /**
   * Phase 83 (ADR-326): the ONLY send path for collection reminders. Thin delegation — the send
   * mechanics (provider resolve, frame-owns-facts render, rate limit, delivery log, activity
   * transition, audit) live in {@link CollectionReminderSendService}.
   */
  private void executeSendCollectionReminder(GateAction.SendCollectionReminderAction action) {
    collectionReminderSendService.sendReminder(action);
    log.info(
        "Gate approved: dispatched collection reminder for activity={} invoice={} stage={}",
        action.collectionActivityId(),
        action.invoiceId(),
        action.stage());
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

  /**
   * Epic 585 (ADR-322): on approval, create the proposed Task. The task is created
   * <b>unassigned</b> (v1): {@code TaskService.createTask} silently ignores {@code assigneeId}
   * unless the actor's org-role is admin/owner, and the reviewer's live org-role is not in scope
   * inside the executor — so passing it here would be misleading. Assignment happens in-product
   * after creation; any proposed {@code assigneeId} is therefore intentionally dropped.
   *
   * <p>A Task has no foreign key to a correspondence, and unknown custom-field keys are silently
   * stripped by {@code CustomFieldValidator}, so the back-link cannot live in {@code customFields}.
   * It is instead prepended to the description as a human-readable {@code "[From correspondence
   * <id>]"} line — best-effort traceability only. The authoritative link is the {@code
   * correspondence_id} already stored in the gate's {@code proposed_action} JSONB.
   */
  private void executeCreateTask(
      GateAction.CreateTaskFromCorrespondenceAction action, UUID reviewerId) {
    // orgRole is null on purpose: the reviewer's live role is not available here, and a null role
    // means createTask creates the task unassigned (the v1 boundary — see javadoc).
    var actor = new ActorContext(reviewerId, null);
    String backLink = "[From correspondence " + action.correspondenceId() + "]";
    String description =
        action.description() == null || action.description().isBlank()
            ? backLink
            : backLink + "\n\n" + action.description();
    var task =
        taskService.createTask(
            action.projectId(),
            action.title(),
            description,
            "MEDIUM",
            null,
            action.dueDate(),
            actor,
            Map.of(),
            null,
            null);
    log.info(
        "Gate approved: created task {} from correspondence {} in project {}",
        task.getId(),
        action.correspondenceId(),
        action.projectId());
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
        case "CREATE_TASK_FROM_CORRESPONDENCE" -> {
          UUID correspondenceId = UUID.fromString((String) proposedAction.get("correspondence_id"));
          UUID projectId = UUID.fromString((String) proposedAction.get("project_id"));
          String title = (String) proposedAction.get("title");
          String description = (String) proposedAction.get("description");
          Object dueDateRaw = proposedAction.get("due_date");
          LocalDate dueDate = dueDateRaw == null ? null : LocalDate.parse((String) dueDateRaw);
          Object assigneeRaw = proposedAction.get("assignee_id");
          UUID assigneeId = assigneeRaw == null ? null : UUID.fromString((String) assigneeRaw);
          yield new GateAction.CreateTaskFromCorrespondenceAction(
              correspondenceId, projectId, title, description, dueDate, assigneeId);
        }
        case "SEND_COLLECTION_REMINDER" -> {
          UUID activityId = UUID.fromString((String) proposedAction.get("collection_activity_id"));
          UUID invoiceId = UUID.fromString((String) proposedAction.get("invoice_id"));
          UUID customerId = UUID.fromString((String) proposedAction.get("customer_id"));
          String stage = (String) proposedAction.get("stage");
          String subject = (String) proposedAction.get("subject");
          String bodyHtml = (String) proposedAction.get("body_html");
          String bodyText = (String) proposedAction.get("body_text");
          yield new GateAction.SendCollectionReminderAction(
              activityId, invoiceId, customerId, stage, subject, bodyHtml, bodyText);
        }
        default -> throw new IllegalArgumentException("Unknown gate type: " + gateType);
      };
    } catch (NullPointerException
        | ClassCastException
        | IllegalArgumentException
        | DateTimeException e) {
      // DateTimeException covers DateTimeParseException from a malformed due_date — it does NOT
      // extend IllegalArgumentException, so without this it would leak as a raw parse exception
      // instead of the normalized IllegalStateException used for every other invalid payload field.
      if (e instanceof IllegalArgumentException && e.getMessage().startsWith("Unknown gate type")) {
        throw e;
      }
      throw new IllegalStateException(
          "Invalid gate action data for type " + gateType + ": " + e.getMessage(), e);
    }
  }
}
