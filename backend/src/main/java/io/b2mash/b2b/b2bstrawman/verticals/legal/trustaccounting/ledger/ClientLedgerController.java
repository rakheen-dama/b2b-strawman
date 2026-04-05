package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService.ClientLedgerCardResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService.LedgerStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService.TotalBalanceResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.TrustTransactionResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientLedgerController {

  private final ClientLedgerService clientLedgerService;

  public ClientLedgerController(ClientLedgerService clientLedgerService) {
    this.clientLedgerService = clientLedgerService;
  }

  @GetMapping("/api/trust-accounts/{accountId}/client-ledgers")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<Page<ClientLedgerCardResponse>> listClientLedgers(
      @PathVariable UUID accountId, Pageable pageable) {
    return ResponseEntity.ok(clientLedgerService.listClientLedgers(accountId, pageable));
  }

  @GetMapping("/api/trust-accounts/{accountId}/client-ledgers/{customerId}")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<ClientLedgerCardResponse> getByCustomer(
      @PathVariable UUID accountId, @PathVariable UUID customerId) {
    return ResponseEntity.ok(clientLedgerService.getClientLedger(customerId, accountId));
  }

  @GetMapping("/api/trust-accounts/{accountId}/client-ledgers/{customerId}/history")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<Page<TrustTransactionResponse>> getHistory(
      @PathVariable UUID accountId, @PathVariable UUID customerId, Pageable pageable) {
    return ResponseEntity.ok(
        clientLedgerService.getClientTransactionHistory(customerId, accountId, pageable));
  }

  @GetMapping("/api/trust-accounts/{accountId}/client-ledgers/{customerId}/statement")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<LedgerStatementResponse> getStatement(
      @PathVariable UUID accountId,
      @PathVariable UUID customerId,
      @RequestParam LocalDate startDate,
      @RequestParam LocalDate endDate) {
    return ResponseEntity.ok(
        clientLedgerService.getClientLedgerStatement(customerId, accountId, startDate, endDate));
  }

  @GetMapping("/api/trust-accounts/{accountId}/total-balance")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<TotalBalanceResponse> getTotalBalance(@PathVariable UUID accountId) {
    return ResponseEntity.ok(clientLedgerService.getTotalTrustBalance(accountId));
  }
}
