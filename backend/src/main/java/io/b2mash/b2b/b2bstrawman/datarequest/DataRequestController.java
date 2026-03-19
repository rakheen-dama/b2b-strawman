package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-requests")
public class DataRequestController {

  private final DataSubjectRequestService dataSubjectRequestService;
  private final DataExportService dataExportService;
  private final DataAnonymizationService dataAnonymizationService;
  private final StorageService storageService;

  public DataRequestController(
      DataSubjectRequestService dataSubjectRequestService,
      DataExportService dataExportService,
      DataAnonymizationService dataAnonymizationService,
      StorageService storageService) {
    this.dataSubjectRequestService = dataSubjectRequestService;
    this.dataExportService = dataExportService;
    this.dataAnonymizationService = dataAnonymizationService;
    this.storageService = storageService;
  }

  @GetMapping
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<List<DataSubjectRequestService.DataSubjectRequestSummary>> listRequests(
      @RequestParam(required = false) String status) {
    var requests =
        status != null && !status.isBlank()
            ? dataSubjectRequestService.listByStatus(status)
            : dataSubjectRequestService.listAll();
    return ResponseEntity.ok(dataSubjectRequestService.toSummaries(requests));
  }

  @GetMapping("/{id}")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<DataSubjectRequestService.DataSubjectRequestSummary> getRequest(
      @PathVariable UUID id) {
    var request = dataSubjectRequestService.getById(id);
    return ResponseEntity.ok(dataSubjectRequestService.toSummary(request));
  }

  @PostMapping
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<DataSubjectRequestService.DataSubjectRequestSummary> createRequest(
      @Valid @RequestBody CreateDataRequestBody body) {
    var actorId = RequestScopes.requireMemberId();
    var request =
        dataSubjectRequestService.createRequest(
            body.customerId(), body.requestType(), body.description(), actorId);
    var response = dataSubjectRequestService.toSummary(request);
    return ResponseEntity.created(URI.create("/api/data-requests/" + request.getId()))
        .body(response);
  }

  @PutMapping("/{id}/status")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<DataSubjectRequestService.DataSubjectRequestSummary> updateStatus(
      @PathVariable UUID id, @Valid @RequestBody StatusTransitionBody body) {
    var actorId = RequestScopes.requireMemberId();
    DataSubjectRequest request =
        switch (body.action()) {
          case "START_PROCESSING" -> dataSubjectRequestService.startProcessing(id, actorId);
          case "COMPLETE" -> dataSubjectRequestService.completeRequest(id, actorId);
          case "REJECT" -> dataSubjectRequestService.rejectRequest(id, body.reason(), actorId);
          default ->
              throw new InvalidStateException(
                  "Unknown action", "Action must be START_PROCESSING, COMPLETE, or REJECT");
        };
    return ResponseEntity.ok(dataSubjectRequestService.toSummary(request));
  }

  @PostMapping("/{id}/export")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<ExportResponse> generateExport(@PathVariable UUID id) {
    var actorId = RequestScopes.requireMemberId();
    var s3Key = dataExportService.generateExport(id, actorId);
    return ResponseEntity.ok(new ExportResponse(s3Key));
  }

  @GetMapping("/{id}/export/download")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<DownloadResponse> downloadExport(@PathVariable UUID id) {
    var request = dataSubjectRequestService.getById(id);
    if (request.getExportFileKey() == null) {
      throw new ResourceNotFoundException("Export", id);
    }
    var expiry = Duration.ofHours(1);
    var presigned = storageService.generateDownloadUrl(request.getExportFileKey(), expiry);
    return ResponseEntity.ok(new DownloadResponse(presigned.url(), expiry.toSeconds()));
  }

  @PostMapping("/{id}/execute-deletion")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<Map<String, Object>> executeDeletion(
      @PathVariable UUID id, @Valid @RequestBody ExecuteDeletionBody body) {
    var actorId = RequestScopes.requireMemberId();
    var result =
        dataAnonymizationService.executeAnonymization(id, body.confirmCustomerName(), actorId);
    return ResponseEntity.ok(
        Map.of(
            "status",
            "COMPLETED",
            "anonymizationSummary",
            Map.of(
                "customerAnonymized", result.customerAnonymized(),
                "documentsDeleted", result.documentsDeleted(),
                "commentsRedacted", result.commentsRedacted(),
                "portalContactsAnonymized", result.portalContactsAnonymized(),
                "invoicesPreserved", result.invoicesPreserved())));
  }

  @PostMapping("/check-deadlines")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<Map<String, Object>> checkDeadlines() {
    int flagged = dataSubjectRequestService.checkDeadlines();
    return ResponseEntity.ok(Map.of("flagged", flagged));
  }

  // DTOs
  public record CreateDataRequestBody(
      @NotNull UUID customerId, @NotBlank String requestType, @NotBlank String description) {}

  public record StatusTransitionBody(@NotBlank String action, String reason) {}

  public record ExecuteDeletionBody(@NotBlank String confirmCustomerName) {}

  public record ExportResponse(String exportFileKey) {}

  public record DownloadResponse(String url, long expiresInSeconds) {}
}
