package io.b2mash.b2b.b2bstrawman.checklist;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<List<InstanceResponse>> listForCustomer(@PathVariable UUID customerId) {
    var instances = checklistInstanceService.listByCustomer(customerId);
    return ResponseEntity.ok(instances.stream().map(InstanceResponse::from).toList());
  }

  @GetMapping("/api/checklist-instances/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<InstanceWithItemsResponse> getInstance(@PathVariable UUID id) {
    var instance = checklistInstanceService.findById(id);
    var items = checklistInstanceService.getItems(id);
    return ResponseEntity.ok(
        new InstanceWithItemsResponse(
            InstanceResponse.from(instance),
            items.stream().map(InstanceItemResponse::from).toList()));
  }

  @PostMapping("/api/customers/{customerId}/checklists")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<InstanceResponse> instantiate(
      @PathVariable UUID customerId, @Valid @RequestBody InstantiateRequest request) {
    var instance = checklistInstanceService.instantiate(customerId, request.templateId());
    return ResponseEntity.created(URI.create("/api/checklist-instances/" + instance.getId()))
        .body(InstanceResponse.from(instance));
  }

  @PutMapping("/api/checklist-items/{id}/complete")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<InstanceItemResponse> completeItem(
      @PathVariable UUID id, @Valid @RequestBody CompleteItemRequest request) {
    var item = checklistInstanceService.completeItem(id, request.notes(), request.documentId());
    return ResponseEntity.ok(InstanceItemResponse.from(item));
  }

  @PutMapping("/api/checklist-items/{id}/skip")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<InstanceItemResponse> skipItem(
      @PathVariable UUID id, @Valid @RequestBody SkipItemRequest request) {
    var item = checklistInstanceService.skipItem(id, request.reason());
    return ResponseEntity.ok(InstanceItemResponse.from(item));
  }

  @PutMapping("/api/checklist-items/{id}/reopen")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<InstanceItemResponse> reopenItem(@PathVariable UUID id) {
    var item = checklistInstanceService.reopenItem(id);
    return ResponseEntity.ok(InstanceItemResponse.from(item));
  }

  // --- DTOs ---

  public record InstantiateRequest(@NotNull UUID templateId) {}

  public record CompleteItemRequest(@Size(max = 2000) String notes, UUID documentId) {}

  public record SkipItemRequest(@Size(max = 2000) String reason) {}

  public record InstanceResponse(
      UUID id,
      UUID templateId,
      UUID customerId,
      String status,
      Instant startedAt,
      Instant completedAt,
      UUID completedBy,
      Instant createdAt,
      Instant updatedAt) {

    public static InstanceResponse from(ChecklistInstance i) {
      return new InstanceResponse(
          i.getId(),
          i.getTemplateId(),
          i.getCustomerId(),
          i.getStatus(),
          i.getStartedAt(),
          i.getCompletedAt(),
          i.getCompletedBy(),
          i.getCreatedAt(),
          i.getUpdatedAt());
    }
  }

  public record InstanceItemResponse(
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
      UUID dependsOnItemId,
      Instant createdAt,
      Instant updatedAt) {

    public static InstanceItemResponse from(ChecklistInstanceItem item) {
      return new InstanceItemResponse(
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
          item.getDependsOnItemId(),
          item.getCreatedAt(),
          item.getUpdatedAt());
    }
  }

  public record InstanceWithItemsResponse(
      InstanceResponse instance, List<InstanceItemResponse> items) {}
}
