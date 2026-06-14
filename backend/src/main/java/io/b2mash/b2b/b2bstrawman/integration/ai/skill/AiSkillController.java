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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
      JsonNode output,
      List<GateDto> gates,
      long costCents,
      String model,
      Long durationMs) {

    // Stateless helpers for turning the stored raw LLM output into a structured JSON object for the
    // API. The LLM response is frequently wrapped in a markdown ```json fence; returning it
    // verbatim
    // as a string forces every frontend to JSON.parse a code-fenced string (and several skills omit
    // that parse entirely, crashing the UI — AIVERIFY-001). Returning a parsed JsonNode gives all
    // skills a single, consistent object contract.
    private static final ObjectMapper OUTPUT_MAPPER = new ObjectMapper();
    private static final LlmJsonParser OUTPUT_PARSER = new LlmJsonParser();

    public static SkillExecutionResponse from(SkillExecutionResult result) {
      return from(result.execution(), result.gates());
    }

    public static SkillExecutionResponse from(AiExecution execution, List<AiExecutionGate> gates) {
      return new SkillExecutionResponse(
          execution.getId(),
          execution.getStatus(),
          parseOutput(execution.getOutputContent()),
          gates.stream().map(GateDto::from).toList(),
          execution.getCostCents(),
          execution.getModel(),
          execution.getDurationMs());
    }

    /**
     * Strip any markdown fence / prose preamble and parse the LLM output into a JSON object.
     * Returns {@code null} for blank or unparseable content (e.g. a FAILED execution) — the
     * frontend renders the failure state from {@code status}, not {@code output}.
     */
    private static JsonNode parseOutput(String rawOutput) {
      if (rawOutput == null || rawOutput.isBlank()) {
        return null;
      }
      try {
        return OUTPUT_MAPPER.readTree(OUTPUT_PARSER.extractJson(rawOutput));
      } catch (RuntimeException e) {
        return null;
      }
    }
  }

  public record GateDto(
      UUID id,
      UUID executionId,
      String gateType,
      String status,
      Map<String, Object> proposedAction,
      String aiReasoning,
      Instant createdAt,
      Instant expiresAt) {

    public static GateDto from(AiExecutionGate gate) {
      return new GateDto(
          gate.getId(),
          gate.getExecution().getId(),
          gate.getGateType(),
          gate.getStatus(),
          gate.getProposedAction(),
          gate.getAiReasoning(),
          gate.getCreatedAt(),
          gate.getExpiresAt());
    }
  }
}
