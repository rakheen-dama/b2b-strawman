package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.UnidentifiedDepositResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UnidentifiedDepositController {

  private final UnidentifiedDepositService unidentifiedDepositService;

  public UnidentifiedDepositController(UnidentifiedDepositService unidentifiedDepositService) {
    this.unidentifiedDepositService = unidentifiedDepositService;
  }

  @GetMapping("/api/trust-accounts/{accountId}/unidentified-deposits")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<Page<UnidentifiedDepositResponse>> list(
      @PathVariable UUID accountId,
      @RequestParam(required = false) UnidentifiedDepositStatus status,
      Pageable pageable) {
    return ResponseEntity.ok(unidentifiedDepositService.list(accountId, status, pageable));
  }
}
