package io.b2mash.b2b.b2bstrawman.informationrequest.dto;

import io.b2mash.b2b.b2bstrawman.informationrequest.RequestTemplate;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestTemplateItem;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RequestTemplateDtos {

  private RequestTemplateDtos() {}

  public record CreateRequestTemplateRequest(
      @NotBlank String name, String description, List<RequestTemplateItemRequest> items) {}

  public record UpdateRequestTemplateRequest(
      @NotBlank String name, String description, List<RequestTemplateItemRequest> items) {}

  public record RequestTemplateItemRequest(
      @NotBlank String name,
      String description,
      @NotBlank String responseType,
      boolean required,
      String fileTypeHints,
      int sortOrder) {}

  public record RequestTemplateResponse(
      UUID id,
      String name,
      String description,
      String source,
      String packId,
      boolean active,
      List<RequestTemplateItemResponse> items,
      Instant createdAt,
      Instant updatedAt) {

    public static RequestTemplateResponse from(RequestTemplate t, List<RequestTemplateItem> items) {
      return new RequestTemplateResponse(
          t.getId(),
          t.getName(),
          t.getDescription(),
          t.getSource().name(),
          t.getPackId(),
          t.isActive(),
          items.stream().map(RequestTemplateItemResponse::from).toList(),
          t.getCreatedAt(),
          t.getUpdatedAt());
    }
  }

  public record RequestTemplateItemResponse(
      UUID id,
      UUID templateId,
      String name,
      String description,
      String responseType,
      boolean required,
      String fileTypeHints,
      int sortOrder,
      Instant createdAt) {

    public static RequestTemplateItemResponse from(RequestTemplateItem item) {
      return new RequestTemplateItemResponse(
          item.getId(),
          item.getTemplateId(),
          item.getName(),
          item.getDescription(),
          item.getResponseType().name(),
          item.isRequired(),
          item.getFileTypeHints(),
          item.getSortOrder(),
          item.getCreatedAt());
    }
  }
}
