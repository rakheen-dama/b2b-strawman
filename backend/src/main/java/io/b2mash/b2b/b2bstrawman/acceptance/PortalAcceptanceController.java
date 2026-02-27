package io.b2mash.b2b.b2bstrawman.acceptance;

import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceService.PortalAcceptResponse;
import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceService.PortalPageData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Portal-facing REST controller for the document acceptance workflow. Token-based auth only. */
@RestController
@RequestMapping("/api/portal/acceptance")
public class PortalAcceptanceController {

  private final AcceptanceService acceptanceService;

  public PortalAcceptanceController(AcceptanceService acceptanceService) {
    this.acceptanceService = acceptanceService;
  }

  @GetMapping("/{token}")
  public ResponseEntity<PortalPageData> getPageData(
      @PathVariable String token, HttpServletRequest request) {
    return ResponseEntity.ok(acceptanceService.getPageData(token, extractClientIp(request)));
  }

  @GetMapping("/{token}/pdf")
  public ResponseEntity<byte[]> streamPdf(@PathVariable String token) {
    var download = acceptanceService.streamPdf(token);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + download.fileName() + "\"")
        .body(download.bytes());
  }

  @PostMapping("/{token}/accept")
  public ResponseEntity<PortalAcceptResponse> accept(
      @PathVariable String token,
      @Valid @RequestBody AcceptanceSubmission submission,
      HttpServletRequest request) {
    String ipAddress = extractClientIp(request);
    String userAgent = request.getHeader("User-Agent");
    return ResponseEntity.ok(
        acceptanceService.acceptFromPortal(token, submission, ipAddress, userAgent));
  }

  private String extractClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return xForwardedFor.split(",")[0].trim();
    }
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isBlank()) {
      return xRealIp.trim();
    }
    return request.getRemoteAddr();
  }
}
