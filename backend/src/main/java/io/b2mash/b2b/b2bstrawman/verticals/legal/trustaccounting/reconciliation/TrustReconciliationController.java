package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.CreateReconciliationRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.MatchResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.TrustReconciliationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Controller for bank statement import and reconciliation endpoints. */
@RestController
public class TrustReconciliationController {

  private final TrustReconciliationService trustReconciliationService;

  public TrustReconciliationController(TrustReconciliationService trustReconciliationService) {
    this.trustReconciliationService = trustReconciliationService;
  }

  @PostMapping(
      value = "/api/trust-accounts/{accountId}/bank-statements",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<BankStatementResponse> importBankStatement(
      @PathVariable UUID accountId, @RequestParam("file") MultipartFile file) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustReconciliationService.importBankStatement(accountId, file));
  }

  @GetMapping("/api/trust-accounts/{accountId}/bank-statements")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<BankStatementResponse>> listBankStatements(
      @PathVariable UUID accountId) {
    return ResponseEntity.ok(trustReconciliationService.listBankStatements(accountId));
  }

  @GetMapping("/api/bank-statements/{statementId}")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<BankStatementResponse> getBankStatement(@PathVariable UUID statementId) {
    return ResponseEntity.ok(trustReconciliationService.getBankStatement(statementId));
  }

  // --- Matching Endpoints ---

  @PostMapping("/api/bank-statements/{statementId}/auto-match")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<MatchResult> autoMatchStatement(@PathVariable UUID statementId) {
    return ResponseEntity.ok(trustReconciliationService.autoMatchStatement(statementId));
  }

  @PostMapping("/api/bank-statement-lines/{lineId}/match")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<Void> manualMatch(
      @PathVariable UUID lineId, @Valid @RequestBody ManualMatchRequest request) {
    trustReconciliationService.manualMatch(lineId, request.transactionId());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/api/bank-statement-lines/{lineId}/unmatch")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<Void> unmatch(@PathVariable UUID lineId) {
    trustReconciliationService.unmatch(lineId);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/api/bank-statement-lines/{lineId}/exclude")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<Void> excludeLine(
      @PathVariable UUID lineId, @Valid @RequestBody ExcludeLineRequest request) {
    trustReconciliationService.excludeLine(lineId, request.reason());
    return ResponseEntity.ok().build();
  }

  // --- Reconciliation Endpoints ---

  @PostMapping("/api/trust-accounts/{accountId}/reconciliations")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustReconciliationResponse> createReconciliation(
      @PathVariable UUID accountId, @Valid @RequestBody CreateReconciliationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            trustReconciliationService.createReconciliation(
                accountId, request.periodEnd(), request.bankStatementId()));
  }

  @GetMapping("/api/trust-accounts/{accountId}/reconciliations")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<TrustReconciliationResponse>> listReconciliations(
      @PathVariable UUID accountId) {
    return ResponseEntity.ok(trustReconciliationService.listReconciliations(accountId));
  }

  @GetMapping("/api/trust-reconciliations/{reconciliationId}")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<TrustReconciliationResponse> getReconciliation(
      @PathVariable UUID reconciliationId) {
    return ResponseEntity.ok(trustReconciliationService.getReconciliation(reconciliationId));
  }

  @PostMapping("/api/trust-reconciliations/{reconciliationId}/calculate")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustReconciliationResponse> calculateReconciliation(
      @PathVariable UUID reconciliationId) {
    return ResponseEntity.ok(trustReconciliationService.calculateReconciliation(reconciliationId));
  }

  @PostMapping("/api/trust-reconciliations/{reconciliationId}/complete")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustReconciliationResponse> completeReconciliation(
      @PathVariable UUID reconciliationId) {
    return ResponseEntity.ok(trustReconciliationService.completeReconciliation(reconciliationId));
  }

  // --- Request Records ---

  record ManualMatchRequest(@NotNull UUID transactionId) {}

  record ExcludeLineRequest(@NotBlank String reason) {}
}
