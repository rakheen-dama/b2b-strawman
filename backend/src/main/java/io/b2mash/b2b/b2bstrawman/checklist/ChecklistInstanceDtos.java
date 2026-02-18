package io.b2mash.b2b.b2bstrawman.checklist;

import java.time.Instant;
import java.util.List;
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
      String notes,
      UUID documentId,
      UUID dependsOnItemId,
      Instant createdAt,
      Instant updatedAt) {

    public static ChecklistInstanceItemResponse from(ChecklistInstanceItem item) {
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
          item.getNotes(),
          item.getDocumentId(),
          item.getDependsOnItemId(),
          item.getCreatedAt(),
          item.getUpdatedAt());
    }
  }

  public record ChecklistInstanceResponse(
      UUID id,
      UUID templateId,
      UUID customerId,
      String status,
      Instant startedAt,
      Instant completedAt,
      UUID completedBy,
      List<ChecklistInstanceItemResponse> items,
      Instant createdAt,
      Instant updatedAt) {

    public static ChecklistInstanceResponse from(
        ChecklistInstance instance, List<ChecklistInstanceItem> items) {
      return new ChecklistInstanceResponse(
          instance.getId(),
          instance.getTemplateId(),
          instance.getCustomerId(),
          instance.getStatus(),
          instance.getStartedAt(),
          instance.getCompletedAt(),
          instance.getCompletedBy(),
          items.stream().map(ChecklistInstanceItemResponse::from).toList(),
          instance.getCreatedAt(),
          instance.getUpdatedAt());
    }
  }
}
