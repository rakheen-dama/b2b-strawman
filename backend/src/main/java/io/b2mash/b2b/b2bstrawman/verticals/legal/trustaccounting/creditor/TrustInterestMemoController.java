package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustInterestMemoService.InterestMemoResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustInterestMemoService.SaveInterestMemoRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency.IdempotentTrustWrite;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * At-source memo records of a trust account (Phase 92 650B, ADR-384, §16.4.2): the year's memos
 * ({@code VIEW_TRUST}) and the upsert of one month's memo ({@code MANAGE_TRUST}). {@code month} is
 * {@code yyyy-MM}.
 */
@RestController
public class TrustInterestMemoController {

  private final TrustInterestMemoService memoService;

  public TrustInterestMemoController(TrustInterestMemoService memoService) {
    this.memoService = memoService;
  }

  @GetMapping("/api/trust-accounts/{accountId}/interest-memos")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<InterestMemoResponse>> list(
      @PathVariable UUID accountId, @RequestParam(required = false) Integer year) {
    return ResponseEntity.ok(memoService.list(accountId, year));
  }

  @PutMapping("/api/trust-accounts/{accountId}/interest-memos/{month}")
  @IdempotentTrustWrite
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<InterestMemoResponse> save(
      @PathVariable UUID accountId,
      @PathVariable YearMonth month,
      @Valid @RequestBody SaveInterestMemoRequest request) {
    return ResponseEntity.ok(memoService.save(accountId, month, request));
  }
}
