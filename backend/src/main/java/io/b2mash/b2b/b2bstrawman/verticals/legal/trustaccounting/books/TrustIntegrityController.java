package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService.FirmIntegrityReport;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService.TrustIntegrityReport;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The trust integrity check (Phase 92 649C, architecture §16.3.9, §16.4.4): one account, or the
 * whole firm, as at {@code asAt} (today when absent). Read-only; {@code VIEW_TRUST}.
 */
@RestController
public class TrustIntegrityController {

  private final TrustIntegrityService trustIntegrityService;

  public TrustIntegrityController(TrustIntegrityService trustIntegrityService) {
    this.trustIntegrityService = trustIntegrityService;
  }

  @GetMapping("/api/trust-accounts/{accountId}/integrity")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<TrustIntegrityReport> check(
      @PathVariable UUID accountId, @RequestParam(required = false) LocalDate asAt) {
    return ResponseEntity.ok(trustIntegrityService.check(accountId, asAt, null));
  }

  @GetMapping("/api/trust-integrity")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<FirmIntegrityReport> checkFirm(
      @RequestParam(required = false) LocalDate asAt) {
    return ResponseEntity.ok(trustIntegrityService.checkFirm(asAt));
  }
}
