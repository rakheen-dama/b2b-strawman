package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
}
