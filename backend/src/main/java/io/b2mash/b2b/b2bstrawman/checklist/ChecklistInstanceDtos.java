package io.b2mash.b2b.b2bstrawman.checklist;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChecklistInstanceDtos {

  private ChecklistInstanceDtos() {}

  public record ChecklistProgressDto(
      int completed, int total, int requiredCompleted, int requiredTotal) {}

  public record ChecklistInstanceItemResponse(
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
      String completedByName,
      String notes,
      UUID documentId,
      UUID dependsOnItemId,
      Instant createdAt,
      Instant updatedAt) {

    public static ChecklistInstanceItemResponse from(
        ChecklistInstanceItem item, Map<UUID, String> memberNames) {
      return new ChecklistInstanceItemResponse(
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
          item.getCompletedBy() != null ? memberNames.get(item.getCompletedBy()) : null,
          item.getNotes(),
          item.getDocumentId(),
          item.getDependsOnItemId(),
          item.getCreatedAt(),
          item.getUpdatedAt());
    }
  }

  public record CompleteItemRequest(String notes, UUID documentId) {}

  public record SkipItemRequest(String reason) {}

  public record InstantiateChecklistRequest(@NotNull UUID templateId) {}

  public record ChecklistInstanceResponse(
      UUID id,
      UUID templateId,
      UUID customerId,
      String status,
      Instant startedAt,
      Instant completedAt,
      UUID completedBy,
      String completedByName,
      List<ChecklistInstanceItemResponse> items,
      Instant createdAt,
      Instant updatedAt) {

    public static ChecklistInstanceResponse from(
        ChecklistInstance instance,
        List<ChecklistInstanceItem> items,
        Map<UUID, String> memberNames) {
      return new ChecklistInstanceResponse(
          instance.getId(),
          instance.getTemplateId(),
          instance.getCustomerId(),
          instance.getStatus(),
          instance.getStartedAt(),
          instance.getCompletedAt(),
          instance.getCompletedBy(),
          instance.getCompletedBy() != null ? memberNames.get(instance.getCompletedBy()) : null,
          items.stream()
              .map(item -> ChecklistInstanceItemResponse.from(item, memberNames))
              .toList(),
          instance.getCreatedAt(),
          instance.getUpdatedAt());
    }
  }
}
