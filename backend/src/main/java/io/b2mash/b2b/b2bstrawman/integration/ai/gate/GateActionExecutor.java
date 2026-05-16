package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService.ResolveRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GateActionExecutor {

  private static final Logger log = LoggerFactory.getLogger(GateActionExecutor.class);

  private final ChecklistInstanceService checklistInstanceService;
  private final ConflictCheckService conflictCheckService;

  public GateActionExecutor(
      ChecklistInstanceService checklistInstanceService,
      ConflictCheckService conflictCheckService) {
    this.checklistInstanceService = checklistInstanceService;
    this.conflictCheckService = conflictCheckService;
  }

  public void execute(AiExecutionGate gate) {
    GateAction action = parseAction(gate.getGateType(), gate.getProposedAction());
    switch (action) {
      case GateAction.MarkKycCompleteAction a -> executeMarkKycComplete(a, gate.getReviewedBy());
      case GateAction.SelectMatterTemplateAction a -> {
        // No-op: frontend pre-fill only, no entity mutation
        log.info("Gate {} approved: template selection (frontend pre-fill)", gate.getId());
      }
      case GateAction.ClearConflictAction a -> executeClearConflict(a, gate.getReviewedBy());
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

  @SuppressWarnings("unchecked")
  private GateAction parseAction(String gateType, Map<String, Object> proposedAction) {
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
      default -> throw new IllegalArgumentException("Unknown gate type: " + gateType);
    };
  }
}
