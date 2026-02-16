package io.b2mash.b2b.b2bstrawman.checklist;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChecklistInstanceController {

  private final ChecklistInstanceService checklistInstanceService;

  public ChecklistInstanceController(ChecklistInstanceService checklistInstanceService) {
    this.checklistInstanceService = checklistInstanceService;
  }

  @GetMapping("/api/customers/{customerId}/checklists")
  public ResponseEntity<List<InstanceResponse>> listForCustomer(@PathVariable UUID customerId) {
    return ResponseEntity.ok(checklistInstanceService.findByCustomerId(customerId));
  }

  @GetMapping("/api/checklist-instances/{id}")
  public ResponseEntity<InstanceWithItemsResponse> getInstanceWithItems(@PathVariable UUID id) {
    return ResponseEntity.ok(checklistInstanceService.findByIdWithItems(id));
  }

  @PostMapping("/api/customers/{customerId}/checklists")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InstanceWithItemsResponse> instantiate(
      @PathVariable UUID customerId, @Valid @RequestBody InstantiateRequest request) {
    var response = checklistInstanceService.instantiate(customerId, request.templateId());
    return ResponseEntity.created(
            URI.create("/api/checklist-instances/" + response.instance().id()))
        .body(response);
  }

  @PutMapping("/api/checklist-items/{id}/complete")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ItemResponse> completeItem(
      @PathVariable UUID id, @Valid @RequestBody CompleteItemRequest request) {
    return ResponseEntity.ok(
        checklistInstanceService.completeItem(id, request.notes(), request.documentId()));
  }

  @PutMapping("/api/checklist-items/{id}/skip")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ItemResponse> skipItem(
      @PathVariable UUID id, @Valid @RequestBody SkipItemRequest request) {
    return ResponseEntity.ok(checklistInstanceService.skipItem(id, request.reason()));
  }

  @PutMapping("/api/checklist-items/{id}/reopen")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ItemResponse> reopenItem(@PathVariable UUID id) {
    return ResponseEntity.ok(checklistInstanceService.reopenItem(id));
  }

  // --- DTOs ---

  public record InstantiateRequest(@NotNull UUID templateId) {}

  public record CompleteItemRequest(String notes, UUID documentId) {}

  public record SkipItemRequest(@NotBlank String reason) {}

  public record InstanceResponse(
      UUID id,
      UUID templateId,
      String templateName,
      UUID customerId,
      String status,
      Instant startedAt,
      Instant completedAt,
      UUID completedBy,
      int itemCount,
      int completedCount,
      int requiredCount,
      int requiredCompletedCount) {

    public static InstanceResponse from(
        ChecklistInstance ci, String templateName, InstanceProgress progress) {
      return new InstanceResponse(
          ci.getId(),
          ci.getTemplateId(),
          templateName,
          ci.getCustomerId(),
          ci.getStatus(),
          ci.getStartedAt(),
          ci.getCompletedAt(),
          ci.getCompletedBy(),
          progress.total(),
          progress.completed(),
          progress.required(),
          progress.requiredCompleted());
    }
  }

  public record InstanceWithItemsResponse(InstanceResponse instance, List<ItemResponse> items) {}

  public record ItemResponse(
      UUID id,
      UUID instanceId,
      UUID templateItemId,
      String name,
      String description,
      int sortOrder,
      boolean required,
      boolean requiresDocument,
      String requiredDocumentLabel,
      String status,
      Instant completedAt,
      UUID completedBy,
      String notes,
      UUID documentId,
      UUID dependsOnItemId) {

    public static ItemResponse from(ChecklistInstanceItem item) {
      return new ItemResponse(
          item.getId(),
          item.getInstanceId(),
          item.getTemplateItemId(),
          item.getName(),
          item.getDescription(),
          item.getSortOrder(),
          item.isRequired(),
          item.isRequiresDocument(),
          item.getRequiredDocumentLabel(),
          item.getStatus(),
          item.getCompletedAt(),
          item.getCompletedBy(),
          item.getNotes(),
          item.getDocumentId(),
          item.getDependsOnItemId());
    }
  }

  public record InstanceProgress(int total, int completed, int required, int requiredCompleted) {}
}
