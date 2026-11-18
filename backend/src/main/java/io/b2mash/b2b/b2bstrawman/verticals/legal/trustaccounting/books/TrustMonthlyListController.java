package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListService.MonthlyListResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListService.MonthlyListSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Locked Rule 54.15 monthly trust creditor lists (Phase 92 650A, architecture §16.3.10, §16.4.4).
 * Read-only, {@code VIEW_TRUST}; a list is locked only by completing a month-end reconciliation.
 */
@RestController
public class TrustMonthlyListController {

  private final TrustMonthlyListService trustMonthlyListService;

  public TrustMonthlyListController(TrustMonthlyListService trustMonthlyListService) {
    this.trustMonthlyListService = trustMonthlyListService;
  }

  @GetMapping("/api/trust-accounts/{accountId}/monthly-lists")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<MonthlyListSummary>> list(@PathVariable UUID accountId) {
    return ResponseEntity.ok(trustMonthlyListService.list(accountId));
  }

  @GetMapping("/api/trust-monthly-lists/{monthlyListId}")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<MonthlyListResponse> get(@PathVariable UUID monthlyListId) {
    return ResponseEntity.ok(trustMonthlyListService.get(monthlyListId));
  }
}
