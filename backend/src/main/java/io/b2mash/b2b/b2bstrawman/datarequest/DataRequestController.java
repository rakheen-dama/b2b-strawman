package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
  private final CustomerRepository customerRepository;
  private final S3PresignedUrlService s3PresignedUrlService;

  public DataRequestController(
      DataSubjectRequestService dataSubjectRequestService,
      DataExportService dataExportService,
      CustomerRepository customerRepository,
      S3PresignedUrlService s3PresignedUrlService) {
    this.dataSubjectRequestService = dataSubjectRequestService;
    this.dataExportService = dataExportService;
    this.customerRepository = customerRepository;
    this.s3PresignedUrlService = s3PresignedUrlService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<DataRequestResponse>> listRequests(
      @RequestParam(required = false) String status) {
    var requests =
        status != null && !status.isBlank()
            ? dataSubjectRequestService.listByStatus(status)
            : dataSubjectRequestService.listAll();
    var responses =
        requests.stream()
            .map(req -> DataRequestResponse.from(req, resolveCustomerName(req.getCustomerId())))
            .toList();
    return ResponseEntity.ok(responses);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<DataRequestResponse> getRequest(@PathVariable UUID id) {
    var request = dataSubjectRequestService.getById(id);
    return ResponseEntity.ok(
        DataRequestResponse.from(request, resolveCustomerName(request.getCustomerId())));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<DataRequestResponse> createRequest(
      @RequestBody CreateDataRequestBody body) {
    var actorId = RequestScopes.requireMemberId();
    var request =
        dataSubjectRequestService.createRequest(
            body.customerId(), body.requestType(), body.description(), actorId);
    var response = DataRequestResponse.from(request, resolveCustomerName(request.getCustomerId()));
    return ResponseEntity.created(URI.create("/api/data-requests/" + request.getId()))
        .body(response);
  }

  @PutMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<DataRequestResponse> updateStatus(
      @PathVariable UUID id, @RequestBody StatusTransitionBody body) {
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
    return ResponseEntity.ok(
        DataRequestResponse.from(request, resolveCustomerName(request.getCustomerId())));
  }

  @PostMapping("/{id}/export")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ExportResponse> generateExport(@PathVariable UUID id) {
    var actorId = RequestScopes.requireMemberId();
    var s3Key = dataExportService.generateExport(id, actorId);
    return ResponseEntity.ok(new ExportResponse(s3Key));
  }

  @GetMapping("/{id}/export/download")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<DownloadResponse> downloadExport(@PathVariable UUID id) {
    var request = dataSubjectRequestService.getById(id);
    if (request.getExportFileKey() == null) {
      throw new ResourceNotFoundException("Export", id);
    }
    var result = s3PresignedUrlService.generateDownloadUrl(request.getExportFileKey());
    return ResponseEntity.ok(new DownloadResponse(result.url(), result.expiresInSeconds()));
  }

  private String resolveCustomerName(UUID customerId) {
    return customerRepository.findById(customerId).map(c -> c.getName()).orElse("Unknown");
  }

  // DTOs
  public record CreateDataRequestBody(UUID customerId, String requestType, String description) {}

  public record StatusTransitionBody(String action, String reason) {}

  public record DataRequestResponse(
      UUID id,
      UUID customerId,
      String customerName,
      String requestType,
      String status,
      String description,
      String rejectionReason,
      LocalDate deadline,
      Instant requestedAt,
      UUID requestedBy,
      Instant completedAt,
      UUID completedBy,
      String exportFileKey,
      String notes,
      Instant createdAt) {

    public static DataRequestResponse from(DataSubjectRequest req, String customerName) {
      return new DataRequestResponse(
          req.getId(),
          req.getCustomerId(),
          customerName,
          req.getRequestType(),
          req.getStatus(),
          req.getDescription(),
          req.getRejectionReason(),
          req.getDeadline(),
          req.getRequestedAt(),
          req.getRequestedBy(),
          req.getCompletedAt(),
          req.getCompletedBy(),
          req.getExportFileKey(),
          req.getNotes(),
          req.getCreatedAt());
    }
  }

  public record ExportResponse(String exportFileKey) {}

  public record DownloadResponse(String url, long expiresInSeconds) {}
}
