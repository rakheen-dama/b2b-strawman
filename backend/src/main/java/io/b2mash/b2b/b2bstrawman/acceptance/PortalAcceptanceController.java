package io.b2mash.b2b.b2bstrawman.acceptance;

import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceService.PortalAcceptResponse;
import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceService.PortalPageData;
import io.b2mash.b2b.b2bstrawman.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
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
    return ResponseEntity.ok(
        acceptanceService.getPageData(token, ClientIpResolver.resolve(request)));
  }

  @GetMapping("/{token}/pdf")
  public ResponseEntity<byte[]> streamPdf(@PathVariable String token) {
    var download = acceptanceService.streamPdf(token);
    ContentDisposition disposition =
        ContentDisposition.inline()
            .filename(sanitizeFilename(download.fileName()), StandardCharsets.UTF_8)
            .build();
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .body(download.bytes());
  }

  @PostMapping("/{token}/accept")
  public ResponseEntity<PortalAcceptResponse> accept(
      @PathVariable String token,
      @Valid @RequestBody AcceptanceSubmission submission,
      HttpServletRequest request) {
    String ipAddress = ClientIpResolver.resolve(request);
    String userAgent = request.getHeader("User-Agent");
    return ResponseEntity.ok(
        acceptanceService.acceptFromPortal(token, submission, ipAddress, userAgent));
  }

  /** Strips characters that could be used for header injection or path traversal. */
  private static String sanitizeFilename(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "document.pdf";
    }
    // Remove path separators, control characters, and header-injection characters
    return fileName.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
  }
}
