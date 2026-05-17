package io.b2mash.b2b.b2bstrawman.integration.ai.execution;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/executions")
public class AiExecutionController {

  private final AiExecutionRepository repository;

  public AiExecutionController(AiExecutionRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_MANAGE")
  public ResponseEntity<Page<ExecutionListResponse>> listExecutions(
      @RequestParam(required = false) String skillId,
      @RequestParam(required = false) String status,
      Pageable pageable) {
    Page<AiExecution> page;
    if (skillId != null && status != null) {
      page = repository.findBySkillIdAndStatusOrderByCreatedAtDesc(skillId, status, pageable);
    } else if (skillId != null) {
      page = repository.findBySkillIdOrderByCreatedAtDesc(skillId, pageable);
    } else if (status != null) {
      page = repository.findByStatusOrderByCreatedAtDesc(status, pageable);
    } else {
      page = repository.findAllByOrderByCreatedAtDesc(pageable);
    }
    return ResponseEntity.ok(page.map(ExecutionListResponse::from));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_MANAGE")
  public ResponseEntity<ExecutionDetailResponse> getExecution(@PathVariable UUID id) {
    return repository
        .findById(id)
        .map(e -> ResponseEntity.ok(ExecutionDetailResponse.from(e)))
        .orElse(ResponseEntity.notFound().build());
  }

  // ── DTOs ──────────────────────────────────────────────────────────────────────

  public record ExecutionListResponse(
      UUID id,
      String skillId,
      String entityType,
      UUID entityId,
      String status,
      String inputSummary,
      String model,
      int inputTokens,
      int outputTokens,
      long costCents,
      Long durationMs,
      Instant createdAt) {
    public static ExecutionListResponse from(AiExecution e) {
      return new ExecutionListResponse(
          e.getId(),
          e.getSkillId(),
          e.getEntityType(),
          e.getEntityId(),
          e.getStatus(),
          e.getInputSummary(),
          e.getModel(),
          e.getInputTokens(),
          e.getOutputTokens(),
          e.getCostCents(),
          e.getDurationMs(),
          e.getCreatedAt());
    }
  }

  public record ExecutionDetailResponse(
      UUID id,
      String skillId,
      String entityType,
      UUID entityId,
      String status,
      String inputSummary,
      String outputContent,
      String model,
      int inputTokens,
      int outputTokens,
      int cacheReadInputTokens,
      int cacheCreationInputTokens,
      long costCents,
      Long durationMs,
      UUID invokedBy,
      Integer firmProfileVersion,
      String errorMessage,
      Instant createdAt) {
    public static ExecutionDetailResponse from(AiExecution e) {
      return new ExecutionDetailResponse(
          e.getId(),
          e.getSkillId(),
          e.getEntityType(),
          e.getEntityId(),
          e.getStatus(),
          e.getInputSummary(),
          e.getOutputContent(),
          e.getModel(),
          e.getInputTokens(),
          e.getOutputTokens(),
          e.getCacheReadInputTokens(),
          e.getCacheCreationInputTokens(),
          e.getCostCents(),
          e.getDurationMs(),
          e.getInvokedBy(),
          e.getFirmProfileVersion(),
          e.getErrorMessage(),
          e.getCreatedAt());
    }
  }
}
