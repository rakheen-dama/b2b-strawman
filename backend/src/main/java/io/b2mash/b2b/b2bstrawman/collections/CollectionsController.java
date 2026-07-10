package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.collections.CollectionsReadService.ActivityResponse;
import io.b2mash.b2b.b2bstrawman.collections.CollectionsReadService.DebtorDetailResponse;
import io.b2mash.b2b.b2bstrawman.collections.CollectionsReadService.DebtorResponse;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Collections read surface (Phase 83, §4.1). Debtor book, per-customer chase drill-in, and the
 * per-invoice activity ledger. Reads mirror the invoice-list guard exactly
 * ({@code @RequiresCapability("INVOICING")}, §9) — same data sensitivity, no new capability. Pure
 * delegation to {@link CollectionsReadService}.
 */
@RestController
@RequestMapping("/api/collections")
public class CollectionsController {

  private final CollectionsReadService collectionsReadService;

  public CollectionsController(CollectionsReadService collectionsReadService) {
    this.collectionsReadService = collectionsReadService;
  }

  @GetMapping("/debtors")
  @RequiresCapability("INVOICING")
  public ResponseEntity<Page<DebtorResponse>> listDebtors(Pageable pageable) {
    return ResponseEntity.ok(collectionsReadService.getDebtors(pageable));
  }

  @GetMapping("/debtors/{customerId}")
  @RequiresCapability("INVOICING")
  public ResponseEntity<DebtorDetailResponse> getDebtor(
      @PathVariable UUID customerId, Pageable pageable) {
    return ResponseEntity.ok(collectionsReadService.getDebtor(customerId, pageable));
  }

  @GetMapping("/activities")
  @RequiresCapability("INVOICING")
  public ResponseEntity<List<ActivityResponse>> listActivities(@RequestParam UUID invoiceId) {
    return ResponseEntity.ok(collectionsReadService.getInvoiceActivities(invoiceId));
  }
}
