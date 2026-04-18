package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.ApprovalDecisionRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.CreateDisbursementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementStatementDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.MarkBilledRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.UpdateDisbursementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.WriteOffRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
 * REST layer for legal disbursements. Thin delegation to {@link DisbursementService}; capability
 * enforcement via {@link RequiresCapability}; vertical-module gating is handled imperatively inside
 * the service.
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
    return ResponseEntity.status(HttpStatus.CREATED).body(disbursementService.create(request));
  }

  @PatchMapping("/{id}")
  @RequiresCapability("MANAGE_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateDisbursementRequest request) {
    return ResponseEntity.ok(disbursementService.update(id, request));
  }

  @PostMapping("/{id}/submit")
  @RequiresCapability("MANAGE_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> submitForApproval(@PathVariable UUID id) {
    return ResponseEntity.ok(disbursementService.submitForApproval(id));
  }

  @PostMapping("/{id}/approve")
  @RequiresCapability("APPROVE_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> approve(
      @PathVariable UUID id, @Valid @RequestBody(required = false) ApprovalDecisionRequest body) {
    return ResponseEntity.ok(disbursementService.approve(id, body));
  }

  @PostMapping("/{id}/reject")
  @RequiresCapability("APPROVE_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> reject(
      @PathVariable UUID id, @Valid @RequestBody(required = false) ApprovalDecisionRequest body) {
    return ResponseEntity.ok(disbursementService.reject(id, body));
  }

  @PostMapping("/{id}/write-off")
  @RequiresCapability("WRITE_OFF_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> writeOff(
      @PathVariable UUID id, @Valid @RequestBody WriteOffRequest body) {
    return ResponseEntity.ok(disbursementService.writeOff(id, body));
  }

  @PostMapping("/{id}/bill")
  @RequiresCapability("MANAGE_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> markBilled(
      @PathVariable UUID id, @Valid @RequestBody MarkBilledRequest body) {
    return ResponseEntity.ok(disbursementService.markBilled(id, body.invoiceLineId()));
  }

  @PostMapping(value = "/{id}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresCapability("MANAGE_DISBURSEMENTS")
  public ResponseEntity<DisbursementResponse> uploadReceipt(
      @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new InvalidStateException("Invalid file", "Receipt file must not be empty");
    }
    try {
      return ResponseEntity.ok(
          disbursementService.uploadReceipt(
              id,
              file.getInputStream(),
              file.getOriginalFilename(),
              file.getContentType(),
              file.getSize()));
    } catch (IOException e) {
      throw new InvalidStateException(
          "Upload failed", "Could not read receipt bytes: " + e.getMessage());
    }
  }

  @GetMapping("/{id}")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<DisbursementResponse> get(@PathVariable UUID id) {
    return ResponseEntity.ok(disbursementService.get(id));
  }

  @GetMapping
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<List<DisbursementResponse>> list(
      @RequestParam(required = false) UUID projectId,
      @RequestParam(required = false) String billingStatus,
      @RequestParam(required = false) String approvalStatus) {
    return ResponseEntity.ok(disbursementService.list(projectId, billingStatus, approvalStatus));
  }

  @GetMapping("/statement")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<List<DisbursementStatementDto>> statement(
      @RequestParam UUID projectId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(disbursementService.listForStatement(projectId, from, to));
  }
}
