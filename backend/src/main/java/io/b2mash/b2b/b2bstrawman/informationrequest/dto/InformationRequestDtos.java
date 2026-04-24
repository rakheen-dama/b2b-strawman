package io.b2mash.b2b.b2bstrawman.informationrequest.dto;

import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestItem;
import io.b2mash.b2b.b2bstrawman.informationrequest.ResponseType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InformationRequestDtos {

  private InformationRequestDtos() {}

  public record CreateInformationRequestRequest(
      UUID requestTemplateId,
      @NotNull UUID customerId,
      UUID projectId,
      @NotNull UUID portalContactId,
      Integer reminderIntervalDays,
      LocalDate dueDate,
      @Valid List<AdHocItemRequest> items) {}

  public record UpdateInformationRequestRequest(
      Integer reminderIntervalDays, UUID projectId, LocalDate dueDate) {}

  public record AdHocItemRequest(
      @NotBlank String name,
      String description,
      @NotNull ResponseType responseType,
      boolean required,
      String fileTypeHints,
      int sortOrder) {}

  public record RejectItemRequest(@NotBlank String reason) {}

  public record AddItemRequest(
      @NotBlank String name,
      String description,
      @NotNull ResponseType responseType,
      boolean required,
      String fileTypeHints,
      int sortOrder) {}

  public record InformationRequestResponse(
      UUID id,
      String requestNumber,
      UUID requestTemplateId,
      UUID customerId,
      String customerName,
      UUID projectId,
      String projectName,
      UUID portalContactId,
      String portalContactName,
      String portalContactEmail,
      String status,
      Integer reminderIntervalDays,
      LocalDate dueDate,
      Instant sentAt,
      Instant completedAt,
      Instant cancelledAt,
      int totalItems,
      long submittedItems,
      long acceptedItems,
      long rejectedItems,
      List<RequestItemResponse> items,
      Instant createdAt,
      Instant updatedAt) {

    public static InformationRequestResponse from(
        InformationRequest r,
        List<RequestItem> items,
        String customerName,
        String projectName,
        String portalContactName,
        String portalContactEmail) {
      long submitted =
          items.stream()
              .filter(
                  i ->
                      i.getStatus()
                          == io.b2mash.b2b.b2bstrawman.informationrequest.ItemStatus.SUBMITTED)
              .count();
      long accepted =
          items.stream()
              .filter(
                  i ->
                      i.getStatus()
                          == io.b2mash.b2b.b2bstrawman.informationrequest.ItemStatus.ACCEPTED)
              .count();
      long rejected =
          items.stream()
              .filter(
                  i ->
                      i.getStatus()
                          == io.b2mash.b2b.b2bstrawman.informationrequest.ItemStatus.REJECTED)
              .count();
      return new InformationRequestResponse(
          r.getId(),
          r.getRequestNumber(),
          r.getRequestTemplateId(),
          r.getCustomerId(),
          customerName,
          r.getProjectId(),
          projectName,
          r.getPortalContactId(),
          portalContactName,
          portalContactEmail,
          r.getStatus().name(),
          r.getReminderIntervalDays(),
          r.getDueDate(),
          r.getSentAt(),
          r.getCompletedAt(),
          r.getCancelledAt(),
          items.size(),
          submitted,
          accepted,
          rejected,
          items.stream().map(RequestItemResponse::from).toList(),
          r.getCreatedAt(),
          r.getUpdatedAt());
    }
  }

  public record RequestItemResponse(
      UUID id,
      UUID requestId,
      UUID templateItemId,
      String name,
      String description,
      String responseType,
      boolean required,
      String fileTypeHints,
      int sortOrder,
      String status,
      UUID documentId,
      String textResponse,
      String rejectionReason,
      Instant submittedAt,
      Instant reviewedAt,
      UUID reviewedBy,
      Instant createdAt,
      Instant updatedAt) {

    public static RequestItemResponse from(RequestItem item) {
      return new RequestItemResponse(
          item.getId(),
          item.getRequestId(),
          item.getTemplateItemId(),
          item.getName(),
          item.getDescription(),
          item.getResponseType().name(),
          item.isRequired(),
          item.getFileTypeHints(),
          item.getSortOrder(),
          item.getStatus().name(),
          item.getDocumentId(),
          item.getTextResponse(),
          item.getRejectionReason(),
          item.getSubmittedAt(),
          item.getReviewedAt(),
          item.getReviewedBy(),
          item.getCreatedAt(),
          item.getUpdatedAt());
    }
  }

  public record DashboardSummaryResponse(
      long total,
      Map<String, Long> byStatus,
      long itemsPendingReview,
      long overdueRequests,
      double completionRateLast30Days) {}
}
