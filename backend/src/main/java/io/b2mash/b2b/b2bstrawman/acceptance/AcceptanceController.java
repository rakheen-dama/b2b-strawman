package io.b2mash.b2b.b2bstrawman.acceptance;

import io.b2mash.b2b.b2bstrawman.acceptance.dto.AcceptanceRequestResponse;
import io.b2mash.b2b.b2bstrawman.acceptance.dto.CreateAcceptanceRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for firm-facing document acceptance workflow. */
@RestController
@RequestMapping("/api/acceptance-requests")
public class AcceptanceController {

  private final AcceptanceService acceptanceService;

  public AcceptanceController(AcceptanceService acceptanceService) {
    this.acceptanceService = acceptanceService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AcceptanceRequestResponse> create(
      @Valid @RequestBody CreateAcceptanceRequest request) {
    var response =
        acceptanceService.createAndSendResponse(
            request.generatedDocumentId(), request.portalContactId(), request.expiryDays());
    return ResponseEntity.created(URI.create("/api/acceptance-requests/" + response.id()))
        .body(response);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<AcceptanceRequestResponse>> list(
      @RequestParam(required = false) UUID documentId,
      @RequestParam(required = false) UUID customerId) {
    if (documentId != null) {
      return ResponseEntity.ok(acceptanceService.listByDocument(documentId));
    }
    if (customerId != null) {
      return ResponseEntity.ok(acceptanceService.listByCustomer(customerId));
    }
    return ResponseEntity.ok(List.of());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AcceptanceRequestResponse> getDetail(@PathVariable UUID id) {
    return ResponseEntity.ok(acceptanceService.getDetail(id));
  }

  @PostMapping("/{id}/remind")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AcceptanceRequestResponse> remind(@PathVariable UUID id) {
    return ResponseEntity.ok(acceptanceService.remindResponse(id));
  }

  @PostMapping("/{id}/revoke")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AcceptanceRequestResponse> revoke(@PathVariable UUID id) {
    return ResponseEntity.ok(acceptanceService.revokeResponse(id));
  }

  @GetMapping("/{id}/certificate")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<byte[]> downloadCertificate(@PathVariable UUID id) {
    var download = acceptanceService.downloadCertificate(id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
        .body(download.bytes());
  }
}
