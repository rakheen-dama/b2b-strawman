package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.TrustTransactionTypeResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The trust transaction type registry as data (Phase 92 646E, §16.4.1). */
@RestController
public class TrustTransactionTypeController {

  private final TrustTransactionService trustTransactionService;

  public TrustTransactionTypeController(TrustTransactionService trustTransactionService) {
    this.trustTransactionService = trustTransactionService;
  }

  @GetMapping("/api/trust-transaction-types")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<TrustTransactionTypeResponse>> list() {
    return ResponseEntity.ok(trustTransactionService.listTransactionTypes());
  }
}
