package io.b2mash.b2b.b2bstrawman.accessrequest;

import io.b2mash.b2b.b2bstrawman.accessrequest.dto.AccessRequestDtos.AccessRequestResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for platform admin operations on access requests. */
@RestController
@RequestMapping("/api/platform-admin/access-requests")
@PreAuthorize("@platformSecurityService.isPlatformAdmin()")
public class PlatformAdminController {

  private final AccessRequestApprovalService approvalService;

  public PlatformAdminController(AccessRequestApprovalService approvalService) {
    this.approvalService = approvalService;
  }

  @GetMapping
  public ResponseEntity<List<AccessRequestResponse>> listRequests(
      @RequestParam(value = "status", required = false) AccessRequestStatus status) {
    return ResponseEntity.ok(
        approvalService.listRequests(status).stream().map(AccessRequestResponse::from).toList());
  }

  @PostMapping("/{id}/approve")
  public ResponseEntity<AccessRequestResponse> approve(
      @PathVariable UUID id, JwtAuthenticationToken auth) {
    return ResponseEntity.ok(
        AccessRequestResponse.from(approvalService.approve(id, auth.getToken().getSubject())));
  }

  @PostMapping("/{id}/reject")
  public ResponseEntity<AccessRequestResponse> reject(
      @PathVariable UUID id, JwtAuthenticationToken auth) {
    return ResponseEntity.ok(
        AccessRequestResponse.from(approvalService.reject(id, auth.getToken().getSubject())));
  }
}
