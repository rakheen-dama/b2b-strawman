package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.EnrichedTrustTransactionResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordFeeTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordRefundRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RejectRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.ReverseRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.TransactionFilters;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller for trust account transaction recording, approval, and query endpoints. */
@RestController
public class TrustTransactionController {

  private final TrustTransactionService trustTransactionService;

  public TrustTransactionController(TrustTransactionService trustTransactionService) {
    this.trustTransactionService = trustTransactionService;
  }

  // --- Recording endpoints (nested under trust-accounts) ---

  @GetMapping("/api/trust-accounts/{accountId}/transactions")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<Page<EnrichedTrustTransactionResponse>> listTransactions(
      @PathVariable UUID accountId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateTo,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) UUID projectId,
      Pageable pageable) {
    return ResponseEntity.ok(
        trustTransactionService.listTransactions(
            accountId,
            new TransactionFilters(dateFrom, dateTo, type, status, customerId, projectId),
            pageable));
  }

  @GetMapping("/api/trust-accounts/{accountId}/transactions/{id}")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<EnrichedTrustTransactionResponse> getTransaction(
      @PathVariable UUID accountId, @PathVariable UUID id) {
    return ResponseEntity.ok(trustTransactionService.getTransaction(accountId, id));
  }

  @PostMapping("/api/trust-accounts/{accountId}/transactions/deposit")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<EnrichedTrustTransactionResponse> recordDeposit(
      @PathVariable UUID accountId, @Valid @RequestBody RecordDepositRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustTransactionService.recordDepositEnriched(accountId, request));
  }

  @PostMapping("/api/trust-accounts/{accountId}/transactions/payment")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<EnrichedTrustTransactionResponse> recordPayment(
      @PathVariable UUID accountId, @Valid @RequestBody RecordPaymentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustTransactionService.recordPaymentEnriched(accountId, request));
  }

  @PostMapping("/api/trust-accounts/{accountId}/transactions/transfer")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<List<EnrichedTrustTransactionResponse>> recordTransfer(
      @PathVariable UUID accountId, @Valid @RequestBody RecordTransferRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustTransactionService.recordTransferEnriched(accountId, request));
  }

  @PostMapping("/api/trust-accounts/{accountId}/transactions/fee-transfer")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<EnrichedTrustTransactionResponse> recordFeeTransfer(
      @PathVariable UUID accountId, @Valid @RequestBody RecordFeeTransferRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustTransactionService.recordFeeTransferEnriched(accountId, request));
  }

  @PostMapping("/api/trust-accounts/{accountId}/transactions/refund")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<EnrichedTrustTransactionResponse> recordRefund(
      @PathVariable UUID accountId, @Valid @RequestBody RecordRefundRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustTransactionService.recordRefundEnriched(accountId, request));
  }

  // --- Approval endpoints (flat trust-transactions path) ---

  @PostMapping("/api/trust-transactions/{id}/approve")
  @RequiresCapability("APPROVE_TRUST_PAYMENT")
  public ResponseEntity<EnrichedTrustTransactionResponse> approve(@PathVariable UUID id) {
    return ResponseEntity.ok(trustTransactionService.approveTransactionEnriched(id));
  }

  @PostMapping("/api/trust-transactions/{id}/reject")
  @RequiresCapability("APPROVE_TRUST_PAYMENT")
  public ResponseEntity<EnrichedTrustTransactionResponse> reject(
      @PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
    return ResponseEntity.ok(
        trustTransactionService.rejectTransactionEnriched(id, request.reason()));
  }

  @PostMapping("/api/trust-transactions/{id}/reverse")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<EnrichedTrustTransactionResponse> reverse(
      @PathVariable UUID id, @Valid @RequestBody ReverseRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustTransactionService.reverseTransactionEnriched(id, request.reason()));
  }

  // --- Query endpoints ---

  @GetMapping("/api/trust-accounts/{accountId}/pending-approvals")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<EnrichedTrustTransactionResponse>> pendingApprovals(
      @PathVariable UUID accountId) {
    return ResponseEntity.ok(trustTransactionService.getPendingApprovals(accountId));
  }

  @GetMapping("/api/trust-accounts/{accountId}/cashbook-balance")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<BigDecimal> cashbookBalance(@PathVariable UUID accountId) {
    return ResponseEntity.ok(trustTransactionService.getCashbookBalance(accountId));
  }
}
