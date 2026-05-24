package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/skills")
public class AiSkillController {

  private final AiSkillExecutionService executionService;

  public AiSkillController(AiSkillExecutionService executionService) {
    this.executionService = executionService;
  }

  @PostMapping("/fica-verification")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_EXECUTE")
  public ResponseEntity<SkillExecutionResponse> executeFicaVerification(
      @RequestBody FicaVerificationRequest request) {
    var context =
        new SkillContext(
            request.customerId(),
            "CUSTOMER",
            "FICA verification for customer " + request.customerId(),
            Map.of());
    SkillExecutionResult result =
        executionService.executeSkill(
            "fica-verification", context, RequestScopes.requireMemberId(), List.of());
    return ResponseEntity.ok(SkillExecutionResponse.from(result.execution(), result.gates()));
  }

  @PostMapping("/matter-intake")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_EXECUTE")
  public ResponseEntity<SkillExecutionResponse> executeMatterIntake(
      @RequestBody MatterIntakeRequest request) {
    var context =
        new SkillContext(
            request.customerId(),
            "CUSTOMER",
            request.description(),
            Map.of("description", request.description()));
    SkillExecutionResult result =
        executionService.executeSkill(
            "matter-intake", context, RequestScopes.requireMemberId(), List.of());
    return ResponseEntity.ok(SkillExecutionResponse.from(result.execution(), result.gates()));
  }

  @PostMapping("/contract-review")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_EXECUTE")
  public ResponseEntity<SkillExecutionResponse> executeContractReview(
      @RequestBody ContractReviewRequest request) {
    var context =
        new SkillContext(
            request.documentId(),
            "DOCUMENT",
            "Contract review for document " + request.documentId(),
            Map.of("projectId", request.projectId()));
    SkillExecutionResult result =
        executionService.executeSkill(
            "contract-review", context, RequestScopes.requireMemberId(), List.of());
    return ResponseEntity.ok(SkillExecutionResponse.from(result.execution(), result.gates()));
  }

  @PostMapping("/drafting")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_EXECUTE")
  public ResponseEntity<SkillExecutionResponse> executeDrafting(
      @RequestBody DraftingRequest request) {
    var context =
        new SkillContext(
            request.projectId(),
            "PROJECT",
            "AI drafting for project " + request.projectId(),
            Map.of("templateId", request.templateId()));
    SkillExecutionResult result =
        executionService.executeSkill(
            "drafting", context, RequestScopes.requireMemberId(), List.of());
    return ResponseEntity.ok(SkillExecutionResponse.from(result.execution(), result.gates()));
  }

  @PostMapping("/compliance-audit")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_EXECUTE")
  public ResponseEntity<SkillExecutionResponse> executeComplianceAudit(
      @RequestBody(required = false) ComplianceAuditRequest request) {
    var context = new SkillContext(FIRM_SENTINEL_ID, "FIRM", "Compliance audit for firm", Map.of());
    SkillExecutionResult result =
        executionService.executeSkill(
            "compliance-audit", context, RequestScopes.requireMemberId(), List.of());
    return ResponseEntity.ok(SkillExecutionResponse.from(result.execution(), result.gates()));
  }

  // ── DTOs ────────────────────────────────────────────────────────────────

  private static final UUID FIRM_SENTINEL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  public record FicaVerificationRequest(UUID customerId) {}

  public record MatterIntakeRequest(UUID customerId, String description) {}

  public record ContractReviewRequest(UUID documentId, UUID projectId) {}

  public record DraftingRequest(UUID templateId, UUID projectId) {}

  public record ComplianceAuditRequest() {}

  public record SkillExecutionResponse(
      UUID executionId,
      String status,
      String output,
      List<GateDto> gates,
      long costCents,
      String model,
      Long durationMs) {

    public static SkillExecutionResponse from(AiExecution execution, List<AiExecutionGate> gates) {
      return new SkillExecutionResponse(
          execution.getId(),
          execution.getStatus(),
          execution.getOutputContent(),
          gates.stream().map(GateDto::from).toList(),
          execution.getCostCents(),
          execution.getModel(),
          execution.getDurationMs());
    }
  }

  public record GateDto(
      UUID id,
      String gateType,
      String status,
      Map<String, Object> proposedAction,
      String aiReasoning,
      Instant expiresAt) {

    public static GateDto from(AiExecutionGate gate) {
      return new GateDto(
          gate.getId(),
          gate.getGateType(),
          gate.getStatus(),
          gate.getProposedAction(),
          gate.getAiReasoning(),
          gate.getExpiresAt());
    }
  }
}
