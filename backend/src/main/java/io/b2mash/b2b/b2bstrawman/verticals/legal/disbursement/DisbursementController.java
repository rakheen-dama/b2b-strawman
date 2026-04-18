package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.ApprovalDecisionRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.CreateDisbursementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.UpdateDisbursementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.WriteOffRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for legal disbursements. Every endpoint delegates to {@link DisbursementService}
 * in a one-liner; module-gating and capability checks run inside the service.
 */
@RestController
@RequestMapping("/api/legal/disbursements")
public class DisbursementController {

  private final DisbursementService disbursementService;

  public DisbursementController(DisbursementService disbursementService) {
    this.disbursementService = disbursementService;
  }

  @PostMapping
  @RequiresCapability("MANAGE_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> create(
      @Valid @RequestBody CreateDisbursementRequest request) {
    var response = disbursementService.create(request, RequestScopes.requireMemberId());
    return ResponseEntity.created(URI.create("/api/legal/disbursements/" + response.id()))
        .body(response);
  }

  @GetMapping("/{id}")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<DisbursementResponse> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(disbursementService.getById(id));
  }

  @PatchMapping("/{id}")
  @RequiresCapability("MANAGE_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateDisbursementRequest request) {
    return ResponseEntity.ok(disbursementService.update(id, request));
  }

  @GetMapping
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<Page<DisbursementResponse>> list(
      @RequestParam(required = false) UUID projectId,
      @RequestParam(required = false) DisbursementApprovalStatus approvalStatus,
      @RequestParam(required = false) DisbursementBillingStatus billingStatus,
      @RequestParam(required = false) DisbursementCategory category,
      Pageable pageable) {
    return ResponseEntity.ok(
        disbursementService.list(projectId, approvalStatus, billingStatus, category, pageable));
  }

  @PostMapping("/{id}/submit")
  @RequiresCapability("MANAGE_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> submit(@PathVariable UUID id) {
    return ResponseEntity.ok(disbursementService.submitForApproval(id));
  }

  @PostMapping("/{id}/approve")
  @RequiresCapability("APPROVE_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> approve(
      @PathVariable UUID id, @Valid @RequestBody ApprovalDecisionRequest request) {
    return ResponseEntity.ok(
        disbursementService.approve(id, RequestScopes.requireMemberId(), request.notes()));
  }

  @PostMapping("/{id}/reject")
  @RequiresCapability("APPROVE_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> reject(
      @PathVariable UUID id, @Valid @RequestBody ApprovalDecisionRequest request) {
    return ResponseEntity.ok(
        disbursementService.reject(id, RequestScopes.requireMemberId(), request.notes()));
  }

  @PostMapping("/{id}/write-off")
  @RequiresCapability("WRITE_OFF_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> writeOff(
      @PathVariable UUID id, @Valid @RequestBody WriteOffRequest request) {
    return ResponseEntity.ok(disbursementService.writeOff(id, request.reason()));
  }

  @PostMapping(value = "/{id}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresCapability("MANAGE_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> attachReceipt(
      @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(disbursementService.attachReceipt(id, file));
  }
}
