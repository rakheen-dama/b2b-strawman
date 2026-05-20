package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.dto.SyncEntryResponse;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.dto.SyncSummaryResponse;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for accounting sync observability: sync summary, entry log, retry, resync, and
 * reconciliation. All read endpoints are gated by {@code INTEGRATION_VIEW_SYNC_STATUS}; the
 * reconcile endpoint requires {@code FINANCIAL_RECONCILE}.
 */
@RestController
@RequestMapping("/api/integrations/sync")
public class AccountingSyncController {

  private final AccountingSyncService syncService;

  public AccountingSyncController(AccountingSyncService syncService) {
    this.syncService = syncService;
  }

  @GetMapping("/summary")
  @RequiresCapability("INTEGRATION_VIEW_SYNC_STATUS")
  public ResponseEntity<SyncSummaryResponse> getSummary() {
    return ResponseEntity.ok(syncService.getSyncSummaryResponse());
  }

  @GetMapping("/entries")
  @RequiresCapability("INTEGRATION_VIEW_SYNC_STATUS")
  public ResponseEntity<Page<SyncEntryResponse>> getEntries(
      @RequestParam(required = false) SyncState state,
      @RequestParam(required = false) SyncEntityType entityType,
      @RequestParam(required = false) SyncDirection direction,
      Pageable pageable) {
    return ResponseEntity.ok(syncService.getEntryResponses(state, entityType, direction, pageable));
  }

  @GetMapping("/entries/{id}")
  @RequiresCapability("INTEGRATION_VIEW_SYNC_STATUS")
  public ResponseEntity<SyncEntryResponse> getEntry(@PathVariable UUID id) {
    return ResponseEntity.ok(syncService.getEntryResponseById(id));
  }

  @GetMapping("/invoice/{invoiceId}/status")
  @RequiresCapability("INTEGRATION_VIEW_SYNC_STATUS")
  public ResponseEntity<List<SyncEntryResponse>> getInvoiceSyncStatus(
      @PathVariable UUID invoiceId) {
    return ResponseEntity.ok(syncService.getInvoiceSyncStatusResponses(invoiceId));
  }

  @PostMapping("/{entryId}/retry")
  @RequiresCapability("INTEGRATION_MANAGE")
  public ResponseEntity<Void> retry(@PathVariable UUID entryId) {
    syncService.retryFromDeadLetter(entryId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/invoice/{invoiceId}/resync")
  @RequiresCapability("INTEGRATION_MANAGE")
  public ResponseEntity<Void> resync(@PathVariable UUID invoiceId) {
    syncService.enqueueInvoicePush(invoiceId, SyncTrigger.FORCE_RESYNC);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{entryId}/reconcile")
  @RequiresCapability("FINANCIAL_RECONCILE")
  public ResponseEntity<Void> reconcile(
      @PathVariable UUID entryId, @RequestBody(required = false) @Valid ReconcileRequest request) {
    syncService.resolveReconcileDrift(entryId, request);
    return ResponseEntity.noContent().build();
  }

  /** Request body for the reconcile endpoint. */
  public record ReconcileRequest(@Size(max = 2000) String resolution) {}
}
