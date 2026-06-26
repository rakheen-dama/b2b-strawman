package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sealed hierarchy of gate actions that can be executed upon attorney approval. The gate_type
 * column on ai_execution_gates discriminates which record to parse the proposed_action JSONB into.
 */
public sealed interface GateAction
    permits GateAction.MarkKycCompleteAction,
        GateAction.SelectMatterTemplateAction,
        GateAction.ClearConflictAction,
        GateAction.CreateReviewReportAction,
        GateAction.CreateDraftDocumentAction,
        GateAction.PublishComplianceReportAction,
        GateAction.CreateTaskFromCorrespondenceAction {

  record MarkKycCompleteAction(List<UUID> checklistItemIds, String completionNotes)
      implements GateAction {}

  record SelectMatterTemplateAction(UUID templateId, String customisationNotes)
      implements GateAction {}

  record ClearConflictAction(UUID conflictCheckId, String clearanceNotes) implements GateAction {}

  record CreateReviewReportAction(UUID projectId, UUID documentId, Map<String, Object> reviewOutput)
      implements GateAction {}

  record CreateDraftDocumentAction(UUID templateId, UUID projectId, Map<String, Object> draftOutput)
      implements GateAction {}

  record PublishComplianceReportAction(Map<String, Object> auditOutput) implements GateAction {}

  /**
   * Epic 585 (ADR-322): create a Task from a filed inbound email, proposed over MCP by the firm's
   * own Claude and approved in-product. There is no FK from a Task to a correspondence, so the
   * back-link to {@code correspondenceId} is best-effort traceability on the created Task; the
   * authoritative link is the {@code correspondence_id} stored in the gate's {@code
   * proposed_action} JSONB.
   */
  record CreateTaskFromCorrespondenceAction(
      UUID correspondenceId,
      UUID projectId,
      String title,
      String description,
      LocalDate dueDate,
      UUID assigneeId)
      implements GateAction {}
}
