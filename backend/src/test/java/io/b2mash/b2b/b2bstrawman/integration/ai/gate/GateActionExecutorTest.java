package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.compliance.ComplianceAuditReportService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.contractreview.AiReviewReportGenerator;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.drafting.AiDraftDocumentGenerator;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService.ResolveRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GateActionExecutorTest {

  private ChecklistInstanceService checklistInstanceService;
  private ConflictCheckService conflictCheckService;
  private GateActionExecutor executor;

  @BeforeEach
  void setUp() {
    checklistInstanceService = mock(ChecklistInstanceService.class);
    conflictCheckService = mock(ConflictCheckService.class);
    executor =
        new GateActionExecutor(
            checklistInstanceService,
            conflictCheckService,
            mock(AiReviewReportGenerator.class),
            mock(AiDraftDocumentGenerator.class),
            mock(ComplianceAuditReportService.class),
            JsonMapper.builder().findAndAddModules().build());
  }

  @Test
  void execute_markKycComplete_completesAllItems() {
    UUID itemId1 = UUID.randomUUID();
    UUID itemId2 = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();

    var gate = mock(AiExecutionGate.class);
    when(gate.getGateType()).thenReturn("MARK_KYC_COMPLETE");
    when(gate.getReviewedBy()).thenReturn(reviewerId);
    when(gate.getProposedAction())
        .thenReturn(
            Map.of(
                "checklist_item_ids",
                List.of(itemId1.toString(), itemId2.toString()),
                "completion_notes",
                "AI verified documents"));

    executor.execute(gate);

    verify(checklistInstanceService)
        .completeItem(itemId1, "AI verified documents", null, reviewerId);
    verify(checklistInstanceService)
        .completeItem(itemId2, "AI verified documents", null, reviewerId);
    verifyNoInteractions(conflictCheckService);
  }

  @Test
  void execute_selectMatterTemplate_noOpNoServiceInteraction() {
    UUID gateId = UUID.randomUUID();

    var gate = mock(AiExecutionGate.class);
    when(gate.getId()).thenReturn(gateId);
    when(gate.getGateType()).thenReturn("SELECT_MATTER_TEMPLATE");
    when(gate.getReviewedBy()).thenReturn(UUID.randomUUID());
    when(gate.getProposedAction())
        .thenReturn(
            Map.of(
                "template_id",
                UUID.randomUUID().toString(),
                "customisation_notes",
                "Standard litigation template"));

    executor.execute(gate);

    verifyNoInteractions(checklistInstanceService);
    verifyNoInteractions(conflictCheckService);
  }

  @Test
  void execute_clearConflict_callsConflictCheckService() {
    UUID conflictCheckId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();

    var gate = mock(AiExecutionGate.class);
    when(gate.getGateType()).thenReturn("CONFIRM_CONFLICT_SCREEN");
    when(gate.getReviewedBy()).thenReturn(reviewerId);
    when(gate.getProposedAction())
        .thenReturn(
            Map.of(
                "conflict_check_id",
                conflictCheckId.toString(),
                "clearance_notes",
                "No material conflict"));

    executor.execute(gate);

    verify(conflictCheckService)
        .resolve(
            conflictCheckId,
            new ResolveRequest("CLEARED", "No material conflict", null),
            reviewerId);
    verifyNoInteractions(checklistInstanceService);
  }

  @Test
  void execute_unknownGateType_throwsIllegalArgument() {
    var gate = mock(AiExecutionGate.class);
    when(gate.getGateType()).thenReturn("UNKNOWN_TYPE");
    when(gate.getProposedAction()).thenReturn(Map.of());

    assertThatThrownBy(() -> executor.execute(gate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown gate type: UNKNOWN_TYPE");
  }
}
