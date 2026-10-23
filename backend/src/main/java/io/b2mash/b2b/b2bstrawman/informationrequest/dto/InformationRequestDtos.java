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
      @NotNull UUID clientId,
      UUID matterId,
      @NotNull UUID portalContactId,
      Integer reminderIntervalDays,
      LocalDate dueDate,
      @Valid List<AdHocItemRequest> items) {}

  public record UpdateInformationRequestRequest(
      Integer reminderIntervalDays, UUID matterId, LocalDate dueDate) {}

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
      UUID clientId,
      String clientName,
      UUID matterId,
      String matterName,
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
        String portalContactEmail,
        Map<UUID, String> documentFileNames) {
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
          items.stream()
              .map(
                  i ->
                      RequestItemResponse.from(
                          i,
                          i.getDocumentId() == null
                              ? null
                              : documentFileNames.getOrDefault(i.getDocumentId(), null)))
              .toList(),
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
      String documentFileName,
      String textResponse,
      String rejectionReason,
      Instant submittedAt,
      Instant reviewedAt,
      UUID reviewedBy,
      Instant createdAt,
      Instant updatedAt) {

    public static RequestItemResponse from(RequestItem item, String documentFileName) {
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
          documentFileName,
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

  /**
   * FICA onboarding status for a client (GAP-L-46; checklist-first since Phase 88, ADR-348).
   * Derived, never stored: the client's onboarding checklist instances (templates with {@code
   * auto_instantiate = true}) decide first; the client's {@code fica-onboarding-pack} information
   * requests are the fallback. A standalone DTO rather than a column on Customer, so it can expand
   * into a composite signal later without a schema change.
   *
   * <ul>
   *   <li>{@code NOT_STARTED} — no live onboarding checklist and no FICA request.
   *   <li>{@code IN_PROGRESS} — an onboarding checklist is open, or (no checklist) a FICA request
   *       exists but not every item is ACCEPTED.
   *   <li>{@code DONE} — the newest live onboarding checklist is COMPLETED (none open), or (no
   *       checklist) the newest COMPLETED FICA request has every item ACCEPTED; {@code
   *       lastVerifiedAt} is that checklist's / request's completedAt.
   * </ul>
   *
   * {@code checklistInstanceId} is set when the checklist decided; {@code requestId} keeps its
   * information-request meaning and is set only when a request decided.
   */
  public record FicaStatusResponse(
      UUID clientId,
      String status,
      Instant lastVerifiedAt,
      UUID requestId,
      UUID checklistInstanceId) {}
}
