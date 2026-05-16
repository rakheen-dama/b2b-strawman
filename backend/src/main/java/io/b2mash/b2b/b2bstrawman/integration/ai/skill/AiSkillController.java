package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final Map<String, AiSkill> skillMap;

  public AiSkillController(AiSkillExecutionService executionService, List<AiSkill> skills) {
    this.executionService = executionService;
    this.skillMap = skills.stream().collect(Collectors.toMap(AiSkill::skillId, s -> s));
  }

  @PostMapping("/fica-verification")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_EXECUTE")
  public ResponseEntity<SkillExecutionResponse> executeFicaVerification(
      @RequestBody FicaVerificationRequest request) {
    return executeSkill(
        "fica-verification",
        request.customerId(),
        "CUSTOMER",
        "FICA verification for customer " + request.customerId(),
        Map.of());
  }

  @PostMapping("/matter-intake")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_EXECUTE")
  public ResponseEntity<SkillExecutionResponse> executeMatterIntake(
      @RequestBody MatterIntakeRequest request) {
    return executeSkill(
        "matter-intake",
        request.customerId(),
        "CUSTOMER",
        request.description(),
        Map.of("description", request.description()));
  }

  private ResponseEntity<SkillExecutionResponse> executeSkill(
      String skillId,
      UUID entityId,
      String entityType,
      String description,
      Map<String, Object> additionalContext) {
    AiSkill skill = skillMap.get(skillId);
    if (skill == null) {
      return ResponseEntity.notFound().build();
    }
    UUID invokedBy = RequestScopes.requireMemberId();
    var context = new SkillContext(entityId, entityType, description, additionalContext);
    var executionRequest = new SkillExecutionRequest(skill, context, invokedBy, List.of());
    SkillExecutionResult result = executionService.executeSkill(executionRequest);
    return ResponseEntity.ok(SkillExecutionResponse.from(result.execution(), result.gates()));
  }

  // ── DTOs ────────────────────────────────────────────────────────────────

  public record FicaVerificationRequest(UUID customerId) {}

  public record MatterIntakeRequest(UUID customerId, String description) {}

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
