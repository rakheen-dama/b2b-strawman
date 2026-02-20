package io.b2mash.b2b.b2bstrawman.retainer;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.retainer.dto.CreateRetainerRequest;
import io.b2mash.b2b.b2bstrawman.retainer.dto.RetainerResponse;
import io.b2mash.b2b.b2bstrawman.retainer.dto.UpdateRetainerRequest;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/api/retainers")
public class RetainerAgreementController {

  private final RetainerAgreementService retainerAgreementService;
  private final RetainerPeriodService retainerPeriodService;

  public RetainerAgreementController(
      RetainerAgreementService retainerAgreementService,
      RetainerPeriodService retainerPeriodService) {
    this.retainerAgreementService = retainerAgreementService;
    this.retainerPeriodService = retainerPeriodService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<RetainerResponse>> listRetainers(
      @RequestParam(required = false) RetainerStatus status,
      @RequestParam(required = false) UUID customerId) {
    // Trigger ready-to-close notifications (deduped, safe to call repeatedly)
    retainerPeriodService.checkAndNotifyReadyToClose();
    return ResponseEntity.ok(retainerAgreementService.listRetainers(status, customerId));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<RetainerResponse> getRetainer(@PathVariable UUID id) {
    return ResponseEntity.ok(retainerAgreementService.getRetainer(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<RetainerResponse> createRetainer(
      @Valid @RequestBody CreateRetainerRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    var response = retainerAgreementService.createRetainer(request, memberId);
    return ResponseEntity.created(URI.create("/api/retainers/" + response.id())).body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<RetainerResponse> updateRetainer(
      @PathVariable UUID id, @Valid @RequestBody UpdateRetainerRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    return ResponseEntity.ok(retainerAgreementService.updateRetainer(id, request, memberId));
  }

  @PostMapping("/{id}/pause")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<RetainerResponse> pauseRetainer(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    return ResponseEntity.ok(retainerAgreementService.pauseRetainer(id, memberId));
  }

  @PostMapping("/{id}/resume")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<RetainerResponse> resumeRetainer(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    return ResponseEntity.ok(retainerAgreementService.resumeRetainer(id, memberId));
  }

  @PostMapping("/{id}/terminate")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<RetainerResponse> terminateRetainer(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    return ResponseEntity.ok(retainerAgreementService.terminateRetainer(id, memberId));
  }
}
