package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/gates")
public class AiExecutionGateController {

  private final AiExecutionGateService gateService;

  public AiExecutionGateController(AiExecutionGateService gateService) {
    this.gateService = gateService;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_REVIEW")
  public ResponseEntity<Page<GateListResponse>> listGates(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String gateType,
      Pageable pageable) {
    return ResponseEntity.ok(
        gateService.listGates(status, gateType, pageable).map(GateListResponse::from));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_REVIEW")
  public ResponseEntity<GateDetailResponse> getGate(@PathVariable UUID id) {
    return ResponseEntity.ok(GateDetailResponse.from(gateService.getGate(id)));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_REVIEW")
  public ResponseEntity<GateDetailResponse> approveGate(
      @PathVariable UUID id, @RequestBody(required = false) GateReviewRequest request) {
    UUID reviewerId = RequestScopes.requireMemberId();
    String notes = request != null ? request.notes() : null;
    return ResponseEntity.ok(GateDetailResponse.from(gateService.approve(id, reviewerId, notes)));
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_REVIEW")
  public ResponseEntity<GateDetailResponse> rejectGate(
      @PathVariable UUID id, @RequestBody(required = false) GateReviewRequest request) {
    UUID reviewerId = RequestScopes.requireMemberId();
    String notes = request != null ? request.notes() : null;
    return ResponseEntity.ok(GateDetailResponse.from(gateService.reject(id, reviewerId, notes)));
  }

  @PostMapping("/batch-approve")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_REVIEW")
  public ResponseEntity<AiExecutionGateService.BatchApproveResult> batchApprove(
      @RequestBody BatchApproveRequest request) {
    UUID reviewerId = RequestScopes.requireMemberId();
    return ResponseEntity.ok(
        gateService.batchApprove(request.gateIds(), reviewerId, request.notes()));
  }

  // ── DTOs ────────────────────────────────────────────────────────────────────

  public record GateReviewRequest(String notes) {}

  public record BatchApproveRequest(List<UUID> gateIds, String notes) {}

  public record GateListResponse(
      UUID id,
      String gateType,
      String status,
      String aiReasoning,
      Instant expiresAt,
      Instant createdAt,
      UUID executionId) {
    public static GateListResponse from(AiExecutionGate gate) {
      return new GateListResponse(
          gate.getId(),
          gate.getGateType(),
          gate.getStatus(),
          gate.getAiReasoning(),
          gate.getExpiresAt(),
          gate.getCreatedAt(),
          gate.getExecution().getId());
    }
  }

  public record GateDetailResponse(
      UUID id,
      String gateType,
      String status,
      Map<String, Object> proposedAction,
      String aiReasoning,
      UUID reviewedBy,
      Instant reviewedAt,
      String reviewNotes,
      Instant expiresAt,
      Instant createdAt,
      UUID executionId) {
    public static GateDetailResponse from(AiExecutionGate gate) {
      return new GateDetailResponse(
          gate.getId(),
          gate.getGateType(),
          gate.getStatus(),
          gate.getProposedAction(),
          gate.getAiReasoning(),
          gate.getReviewedBy(),
          gate.getReviewedAt(),
          gate.getReviewNotes(),
          gate.getExpiresAt(),
          gate.getCreatedAt(),
          gate.getExecution().getId());
    }
  }
}
