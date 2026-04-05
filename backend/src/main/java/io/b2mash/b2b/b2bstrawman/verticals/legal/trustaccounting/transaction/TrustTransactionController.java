package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.CashbookBalanceResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordFeeTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordRefundRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RejectRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.ReverseRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.TrustTransactionResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrustTransactionController {

  private final TrustTransactionService trustTransactionService;

  public TrustTransactionController(TrustTransactionService trustTransactionService) {
    this.trustTransactionService = trustTransactionService;
  }

  // --- Transaction Endpoints (under /api/trust-accounts/{accountId}/transactions) ---

  @GetMapping("/api/trust-accounts/{accountId}/transactions")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<Page<TrustTransactionResponse>> listTransactions(
      @PathVariable UUID accountId, Pageable pageable) {
    return ResponseEntity.ok(trustTransactionService.listTransactions(accountId, pageable));
  }

  @GetMapping("/api/trust-accounts/{accountId}/transactions/{id}")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<TrustTransactionResponse> getTransaction(
      @PathVariable UUID accountId, @PathVariable UUID id) {
    return ResponseEntity.ok(trustTransactionService.getTransactionById(accountId, id));
  }

  @PostMapping("/api/trust-accounts/{accountId}/transactions/deposit")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustTransactionResponse> recordDeposit(
      @PathVariable UUID accountId, @Valid @RequestBody RecordDepositRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustTransactionService.recordDeposit(accountId, request));
  }

  @PostMapping("/api/trust-accounts/{accountId}/transactions/payment")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustTransactionResponse> recordPayment(
      @PathVariable UUID accountId, @Valid @RequestBody RecordPaymentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustTransactionService.recordPayment(accountId, request));
  }

  @PostMapping("/api/trust-accounts/{accountId}/transactions/transfer")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<List<TrustTransactionResponse>> recordTransfer(
      @PathVariable UUID accountId, @Valid @RequestBody RecordTransferRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustTransactionService.recordTransfer(accountId, request));
  }

  @PostMapping("/api/trust-accounts/{accountId}/transactions/fee-transfer")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustTransactionResponse> recordFeeTransfer(
      @PathVariable UUID accountId, @Valid @RequestBody RecordFeeTransferRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustTransactionService.recordFeeTransfer(accountId, request));
  }

  @PostMapping("/api/trust-accounts/{accountId}/transactions/refund")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustTransactionResponse> recordRefund(
      @PathVariable UUID accountId, @Valid @RequestBody RecordRefundRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustTransactionService.recordRefund(accountId, request));
  }

  // --- Approval Endpoints (under /api/trust-transactions/{id}) ---

  @PostMapping("/api/trust-transactions/{id}/approve")
  @RequiresCapability("APPROVE_TRUST_PAYMENT")
  public ResponseEntity<TrustTransactionResponse> approveTransaction(@PathVariable UUID id) {
    return ResponseEntity.ok(
        trustTransactionService.approveTransaction(id, RequestScopes.requireMemberId()));
  }

  @PostMapping("/api/trust-transactions/{id}/reject")
  @RequiresCapability("APPROVE_TRUST_PAYMENT")
  public ResponseEntity<TrustTransactionResponse> rejectTransaction(
      @PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
    return ResponseEntity.ok(
        trustTransactionService.rejectTransaction(
            id, RequestScopes.requireMemberId(), request.reason()));
  }

  @PostMapping("/api/trust-transactions/{id}/reverse")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustTransactionResponse> reverseTransaction(
      @PathVariable UUID id, @Valid @RequestBody ReverseRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trustTransactionService.reverseTransaction(id, request.reason()));
  }

  // --- Query Endpoints (under /api/trust-accounts/{accountId}) ---

  @GetMapping("/api/trust-accounts/{accountId}/pending-approvals")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<TrustTransactionResponse>> getPendingApprovals(
      @PathVariable UUID accountId) {
    return ResponseEntity.ok(trustTransactionService.getPendingApprovals(accountId));
  }

  @GetMapping("/api/trust-accounts/{accountId}/cashbook-balance")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<CashbookBalanceResponse> getCashbookBalance(@PathVariable UUID accountId) {
    return ResponseEntity.ok(trustTransactionService.getCashbookBalance(accountId));
  }
}
