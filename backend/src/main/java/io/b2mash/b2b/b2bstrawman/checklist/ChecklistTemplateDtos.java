package io.b2mash.b2b.b2bstrawman.checklist;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChecklistTemplateDtos {

  private ChecklistTemplateDtos() {}

  public record ChecklistTemplateItemRequest(
      @NotBlank String name,
      String description,
      int sortOrder,
      boolean required,
      boolean requiresDocument,
      String requiredDocumentLabel,
      UUID dependsOnItemId) {}

  public record CreateChecklistTemplateRequest(
      @NotBlank String name,
      String description,
      @NotBlank String customerType,
      boolean autoInstantiate,
      String slug,
      Integer sortOrder,
      List<ChecklistTemplateItemRequest> items) {}

  public record UpdateChecklistTemplateRequest(
      @NotBlank String name,
      String description,
      boolean autoInstantiate,
      Integer sortOrder,
      List<ChecklistTemplateItemRequest> items) {}

  public record ChecklistTemplateItemResponse(
      UUID id,
      UUID templateId,
      String name,
      String description,
      int sortOrder,
      boolean required,
      boolean requiresDocument,
      String requiredDocumentLabel,
      UUID dependsOnItemId,
      Instant createdAt,
      Instant updatedAt) {

    public static ChecklistTemplateItemResponse from(ChecklistTemplateItem item) {
      return new ChecklistTemplateItemResponse(
          item.getId(),
          item.getTemplateId(),
          item.getName(),
          item.getDescription(),
          item.getSortOrder(),
          item.isRequired(),
          item.isRequiresDocument(),
          item.getRequiredDocumentLabel(),
          item.getDependsOnItemId(),
          item.getCreatedAt(),
          item.getUpdatedAt());
    }
  }

  public record ChecklistTemplateResponse(
      UUID id,
      String name,
      String slug,
      String description,
      String customerType,
      String source,
      String packId,
      boolean active,
      boolean autoInstantiate,
      int sortOrder,
      List<ChecklistTemplateItemResponse> items,
      Instant createdAt,
      Instant updatedAt) {

    public static ChecklistTemplateResponse from(
        ChecklistTemplate t, List<ChecklistTemplateItem> items) {
      return new ChecklistTemplateResponse(
          t.getId(),
          t.getName(),
          t.getSlug(),
          t.getDescription(),
          t.getCustomerType(),
          t.getSource(),
          t.getPackId(),
          t.isActive(),
          t.isAutoInstantiate(),
          t.getSortOrder(),
          items.stream().map(ChecklistTemplateItemResponse::from).toList(),
          t.getCreatedAt(),
          t.getUpdatedAt());
    }
  }
}
