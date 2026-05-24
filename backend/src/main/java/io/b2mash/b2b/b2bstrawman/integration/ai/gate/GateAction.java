package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

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
        GateAction.PublishComplianceReportAction {

  record MarkKycCompleteAction(List<UUID> checklistItemIds, String completionNotes)
      implements GateAction {}

  record SelectMatterTemplateAction(UUID templateId, String customisationNotes)
      implements GateAction {}

  record ClearConflictAction(UUID conflictCheckId, String clearanceNotes) implements GateAction {}

  record CreateReviewReportAction(UUID projectId, Map<String, Object> reviewOutput)
      implements GateAction {}

  record CreateDraftDocumentAction(UUID templateId, UUID projectId, Map<String, Object> draftOutput)
      implements GateAction {}

  record PublishComplianceReportAction(Map<String, Object> auditOutput) implements GateAction {}
}
