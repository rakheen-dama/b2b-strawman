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
    return ResponseEntity.ok(
        SkillExecutionResponse.from(
            executionService.executeFicaVerification(
                request.customerId(), RequestScopes.requireMemberId())));
  }

  @PostMapping("/matter-intake")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_EXECUTE")
  public ResponseEntity<SkillExecutionResponse> executeMatterIntake(
      @RequestBody MatterIntakeRequest request) {
    return ResponseEntity.ok(
        SkillExecutionResponse.from(
            executionService.executeMatterIntake(
                request.customerId(), request.description(), RequestScopes.requireMemberId())));
  }

  @PostMapping("/contract-review")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_EXECUTE")
  public ResponseEntity<SkillExecutionResponse> executeContractReview(
      @RequestBody ContractReviewRequest request) {
    return ResponseEntity.ok(
        SkillExecutionResponse.from(
            executionService.executeContractReview(
                request.documentId(), request.projectId(), RequestScopes.requireMemberId())));
  }

  @PostMapping("/drafting")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_EXECUTE")
  public ResponseEntity<SkillExecutionResponse> executeDrafting(
      @RequestBody DraftingRequest request) {
    return ResponseEntity.ok(
        SkillExecutionResponse.from(
            executionService.executeDrafting(
                request.templateId(), request.projectId(), RequestScopes.requireMemberId())));
  }

  @PostMapping("/compliance-audit")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_EXECUTE")
  public ResponseEntity<SkillExecutionResponse> executeComplianceAudit(
      @RequestBody(required = false) ComplianceAuditRequest request) {
    return ResponseEntity.ok(
        SkillExecutionResponse.from(
            executionService.executeComplianceAudit(RequestScopes.requireMemberId())));
  }

  // ── DTOs ────────────────────────────────────────────────────────────────

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

    public static SkillExecutionResponse from(SkillExecutionResult result) {
      return from(result.execution(), result.gates());
    }

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
