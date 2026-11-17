package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.AllocateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.PayToFundRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.UnidentifiedDepositResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

  @PostMapping("/api/unidentified-deposits/{depositId}/allocate")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<UnidentifiedDepositResponse> allocate(
      @PathVariable UUID depositId, @Valid @RequestBody AllocateRequest request) {
    return ResponseEntity.ok(unidentifiedDepositService.allocate(depositId, request));
  }

  @PostMapping("/api/unidentified-deposits/{depositId}/pay-to-fund")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<UnidentifiedDepositResponse> payToFund(
      @PathVariable UUID depositId, @Valid @RequestBody PayToFundRequest request) {
    return ResponseEntity.ok(unidentifiedDepositService.payToFund(depositId, request));
  }
}
