package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalRequestItemView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalRequestView;
import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalInformationRequestService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal endpoints for information requests. Enables portal contacts to list their requests, view
 * details, upload files, and submit responses. All endpoints require a valid portal JWT (enforced
 * by CustomerAuthFilter).
 */
@RestController
@RequestMapping("/portal/requests")
public class PortalInformationRequestController {

  private final PortalInformationRequestService portalInformationRequestService;

  public PortalInformationRequestController(
      PortalInformationRequestService portalInformationRequestService) {
    this.portalInformationRequestService = portalInformationRequestService;
  }

  /** Lists all information requests for the authenticated portal contact. */
  @GetMapping
  public ResponseEntity<List<PortalRequestListResponse>> listRequests() {
    UUID portalContactId = RequestScopes.requirePortalContactId();
    var requests = portalInformationRequestService.listRequests(portalContactId);

    var response =
        requests.stream().map(PortalInformationRequestController::toListResponse).toList();

    return ResponseEntity.ok(response);
  }

  /** Returns request detail with items for the authenticated portal contact. */
  @GetMapping("/{id}")
  public ResponseEntity<PortalRequestDetailResponse> getRequestDetail(@PathVariable UUID id) {
    UUID portalContactId = RequestScopes.requirePortalContactId();
    var detail = portalInformationRequestService.getRequest(id, portalContactId);

    var itemResponses =
        detail.items().stream().map(PortalInformationRequestController::toItemResponse).toList();

    return ResponseEntity.ok(
        new PortalRequestDetailResponse(
            detail.request().id(),
            detail.request().requestNumber(),
            detail.request().status(),
            detail.request().projectId(),
            detail.request().projectName(),
            detail.request().totalItems(),
            detail.request().submittedItems(),
            detail.request().acceptedItems(),
            detail.request().rejectedItems(),
            detail.request().sentAt(),
            detail.request().completedAt(),
            itemResponses));
  }

  /** Initiates a file upload for a request item. Returns a presigned S3 URL. */
  @PostMapping("/{id}/items/{itemId}/upload")
  public ResponseEntity<UploadInitiationResponse> initiateUpload(
      @PathVariable UUID id,
      @PathVariable UUID itemId,
      @Valid @RequestBody UploadInitiationRequest request) {
    UUID portalContactId = RequestScopes.requirePortalContactId();
    var result =
        portalInformationRequestService.initiateUpload(
            id, itemId, request.fileName(), request.contentType(), request.size(), portalContactId);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            new UploadInitiationResponse(
                result.documentId(), result.uploadUrl(), result.expiresAt()));
  }

  /** Submits a file or text response for a request item. */
  @PostMapping("/{id}/items/{itemId}/submit")
  public ResponseEntity<Void> submitItem(
      @PathVariable UUID id,
      @PathVariable UUID itemId,
      @Valid @RequestBody SubmitItemRequest request) {
    UUID portalContactId = RequestScopes.requirePortalContactId();

    portalInformationRequestService.submitResponse(
        id, itemId, request.documentId(), request.textResponse(), portalContactId);

    return ResponseEntity.ok().build();
  }

  // ── DTO mapping helpers ──────────────────────────────────────────────

  private static PortalRequestListResponse toListResponse(PortalRequestView v) {
    return new PortalRequestListResponse(
        v.id(),
        v.requestNumber(),
        v.status(),
        v.projectId(),
        v.projectName(),
        v.totalItems(),
        v.submittedItems(),
        v.acceptedItems(),
        v.rejectedItems(),
        v.sentAt(),
        v.completedAt());
  }

  private static PortalRequestItemResponse toItemResponse(PortalRequestItemView v) {
    return new PortalRequestItemResponse(
        v.id(),
        v.name(),
        v.description(),
        v.responseType(),
        v.required(),
        v.fileTypeHints(),
        v.sortOrder(),
        v.status(),
        v.rejectionReason(),
        v.documentId(),
        v.textResponse());
  }

  // ── DTOs ─────────────────────────────────────────────────────────────

  public record PortalRequestListResponse(
      UUID id,
      String requestNumber,
      String status,
      UUID projectId,
      String projectName,
      int totalItems,
      int submittedItems,
      int acceptedItems,
      int rejectedItems,
      Instant sentAt,
      Instant completedAt) {}

  public record PortalRequestDetailResponse(
      UUID id,
      String requestNumber,
      String status,
      UUID projectId,
      String projectName,
      int totalItems,
      int submittedItems,
      int acceptedItems,
      int rejectedItems,
      Instant sentAt,
      Instant completedAt,
      List<PortalRequestItemResponse> items) {}

  public record PortalRequestItemResponse(
      UUID id,
      String name,
      String description,
      String responseType,
      boolean required,
      String fileTypeHints,
      int sortOrder,
      String status,
      String rejectionReason,
      UUID documentId,
      String textResponse) {}

  public record UploadInitiationRequest(
      @NotBlank String fileName, @NotBlank String contentType, @Positive long size) {}

  public record UploadInitiationResponse(UUID documentId, String uploadUrl, Instant expiresAt) {}

  public record SubmitItemRequest(UUID documentId, String textResponse) {}
}
