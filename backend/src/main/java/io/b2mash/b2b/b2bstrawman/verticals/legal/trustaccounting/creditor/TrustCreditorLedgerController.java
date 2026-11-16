package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorLedgerService.TrustCreditorAccountResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrustCreditorLedgerController {

  private final TrustCreditorLedgerService creditorLedgerService;

  public TrustCreditorLedgerController(TrustCreditorLedgerService creditorLedgerService) {
    this.creditorLedgerService = creditorLedgerService;
  }

  @GetMapping("/api/trust-accounts/{accountId}/creditor-accounts")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<TrustCreditorAccountResponse>> list(
      @PathVariable UUID accountId, @RequestParam(required = false) LocalDate asAt) {
    return ResponseEntity.ok(creditorLedgerService.listCreditorAccounts(accountId, asAt));
  }
}
