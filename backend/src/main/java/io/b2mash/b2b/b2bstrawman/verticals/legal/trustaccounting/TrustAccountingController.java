package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateLpffRateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.LpffRateResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.TrustAccountResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.UpdateTrustAccountRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller for the trust accounting module. */
@RestController
@RequestMapping("/api/trust-accounts")
public class TrustAccountingController {

  private final TrustAccountService trustAccountService;

  public TrustAccountingController(TrustAccountService trustAccountService) {
    this.trustAccountService = trustAccountService;
  }

  @GetMapping
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<TrustAccountResponse>> list() {
    return ResponseEntity.ok(trustAccountService.listTrustAccounts());
  }

  @GetMapping("/{id}")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<TrustAccountResponse> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(trustAccountService.getTrustAccount(id));
  }

  @PostMapping
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustAccountResponse> create(
      @Valid @RequestBody CreateTrustAccountRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustAccountService.createTrustAccount(request));
  }

  @PutMapping("/{id}")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustAccountResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateTrustAccountRequest request) {
    return ResponseEntity.ok(trustAccountService.updateTrustAccount(id, request));
  }

  @PostMapping("/{id}/close")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustAccountResponse> close(@PathVariable UUID id) {
    return ResponseEntity.ok(trustAccountService.closeTrustAccount(id));
  }

  @GetMapping("/{id}/lpff-rates")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<LpffRateResponse>> listLpffRates(@PathVariable UUID id) {
    return ResponseEntity.ok(trustAccountService.listLpffRates(id));
  }

  @PostMapping("/{id}/lpff-rates")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<LpffRateResponse> addLpffRate(
      @PathVariable UUID id, @Valid @RequestBody CreateLpffRateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustAccountService.addLpffRate(id, request));
  }
}
