package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationService.ApproveResult;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationService.BulkApproveResult;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationService.InvocationFilter;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.OutputPayload;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the AI specialist invocation review queue. */
@RestController
@RequestMapping("/api/assistant/invocations")
public class AiSpecialistInvocationController {

  private static final int MAX_PAGE_SIZE = 200;

  private final AiSpecialistInvocationService service;

  public AiSpecialistInvocationController(AiSpecialistInvocationService service) {
    this.service = service;
  }

  @GetMapping
  @RequiresCapability("AI_ASSISTANT_USE")
  public ResponseEntity<Page<InvocationListItemDto>> list(
      @RequestParam(required = false) InvocationStatus status,
      @RequestParam(required = false) String specialistId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) String contextEntityType,
      @RequestParam(required = false) UUID contextEntityId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    var filter =
        new InvocationFilter(
            status, specialistId, from, to, contextEntityType, contextEntityId, actorId);
    var pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
    return ResponseEntity.ok(
        service.findByFilter(filter, pageable).map(InvocationListItemDto::from));
  }

  @GetMapping("/{id}")
  @RequiresCapability("AI_ASSISTANT_USE")
  public ResponseEntity<InvocationDetailDto> get(@PathVariable UUID id) {
    return ResponseEntity.ok(InvocationDetailDto.from(service.findById(id)));
  }

  @PostMapping("/{id}/approve")
  @RequiresCapability("AI_ASSISTANT_USE")
  public ResponseEntity<ApproveResponse> approve(
      @PathVariable UUID id, @RequestBody(required = false) ApproveRequest body) {
    OutputPayload edited = body == null ? null : body.appliedOutput();
    ApproveResult result = service.approve(id, edited);
    return ResponseEntity.ok(new ApproveResponse(result.id(), result.status(), result.appliedAt()));
  }

  @PostMapping("/{id}/reject")
  @RequiresCapability("AI_ASSISTANT_USE")
  public ResponseEntity<Void> reject(
      @PathVariable UUID id, @RequestBody @Valid RejectRequest body) {
    service.reject(id, body.rejectReason());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/retry")
  @RequiresCapability("AI_ASSISTANT_USE")
  public ResponseEntity<Void> retry(@PathVariable UUID id) {
    service.retry(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/bulk-approve")
  @RequiresCapability("AI_ASSISTANT_USE")
  public ResponseEntity<BulkApproveResult> bulkApprove(@RequestBody BulkApproveRequest body) {
    return ResponseEntity.ok(service.bulkApprove(body == null ? List.of() : body.ids()));
  }

  // ----- request DTOs -----

  public record ApproveRequest(OutputPayload appliedOutput) {}

  public record RejectRequest(@NotBlank String rejectReason) {}

  public record BulkApproveRequest(List<UUID> ids) {}

  // ----- response DTOs -----

  public record ApproveResponse(UUID id, InvocationStatus status, Instant appliedAt) {}

  public record InvocationListItemDto(
      UUID id,
      String specialistId,
      InvocationSource invokedBy,
      InvocationStatus status,
      String contextEntityType,
      UUID contextEntityId,
      Instant createdAt,
      String proposedOutputSummary,
      UUID automationActionExecutionId) {

    public static InvocationListItemDto from(AiSpecialistInvocation inv) {
      String summary =
          inv.getProposedOutput() == null
              ? null
              : inv.getProposedOutput().getClass().getSimpleName();
      return new InvocationListItemDto(
          inv.getId(),
          inv.getSpecialistId(),
          inv.getInvokedBy(),
          inv.getStatus(),
          inv.getContextEntityType(),
          inv.getContextEntityId(),
          inv.getCreatedAt(),
          summary,
          inv.getAutomationActionExecutionId());
    }
  }

  public record InvocationDetailDto(
      UUID id,
      String specialistId,
      InvocationSource invokedBy,
      UUID actorId,
      UUID automationActionExecutionId,
      String contextEntityType,
      UUID contextEntityId,
      InvocationStatus status,
      OutputPayload proposedOutput,
      OutputPayload appliedOutput,
      Instant createdAt,
      Instant reviewedAt,
      UUID reviewedById,
      String rejectReason,
      String errorMessage,
      String promptVersion,
      int version) {

    public static InvocationDetailDto from(AiSpecialistInvocation inv) {
      return new InvocationDetailDto(
          inv.getId(),
          inv.getSpecialistId(),
          inv.getInvokedBy(),
          inv.getActorId(),
          inv.getAutomationActionExecutionId(),
          inv.getContextEntityType(),
          inv.getContextEntityId(),
          inv.getStatus(),
          inv.getProposedOutput(),
          inv.getAppliedOutput(),
          inv.getCreatedAt(),
          inv.getReviewedAt(),
          inv.getReviewedById(),
          inv.getRejectReason(),
          inv.getErrorMessage(),
          inv.getPromptVersion(),
          inv.getVersion());
    }
  }
}
