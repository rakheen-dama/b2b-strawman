package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrustInvestmentService {

  private static final Logger log = LoggerFactory.getLogger(TrustInvestmentService.class);
  private static final String MODULE_ID = "trust_accounting";

  private final TrustInvestmentRepository investmentRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final CustomerRepository customerRepository;
  private final ClientLedgerCardRepository ledgerCardRepository;
  private final TrustTransactionService transactionService;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;

  public TrustInvestmentService(
      TrustInvestmentRepository investmentRepository,
      TrustAccountRepository trustAccountRepository,
      CustomerRepository customerRepository,
      ClientLedgerCardRepository ledgerCardRepository,
      TrustTransactionService transactionService,
      VerticalModuleGuard moduleGuard,
      AuditService auditService) {
    this.investmentRepository = investmentRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.customerRepository = customerRepository;
    this.ledgerCardRepository = ledgerCardRepository;
    this.transactionService = transactionService;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
  }

  // --- DTO Records ---

  public record PlaceInvestmentRequest(
      @NotNull UUID customerId,
      @NotBlank String institution,
      @NotBlank String accountNumber,
      @Positive BigDecimal principal,
      @PositiveOrZero BigDecimal interestRate,
      @NotNull LocalDate depositDate,
      LocalDate maturityDate,
      String notes) {}

  public record TrustInvestmentResponse(
      UUID id,
      UUID trustAccountId,
      UUID customerId,
      String institution,
      String accountNumber,
      BigDecimal principal,
      BigDecimal interestRate,
      LocalDate depositDate,
      LocalDate maturityDate,
      BigDecimal interestEarned,
      String status,
      LocalDate withdrawalDate,
      BigDecimal withdrawalAmount,
      UUID depositTransactionId,
      UUID withdrawalTransactionId,
      String notes,
      Instant createdAt,
      Instant updatedAt) {}

  // --- Service Methods ---

  @Transactional
  public TrustInvestmentResponse placeInvestment(UUID accountId, PlaceInvestmentRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var account =
        trustAccountRepository
            .findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    if (account.getStatus() != TrustAccountStatus.ACTIVE) {
      throw new InvalidStateException(
          "Invalid account state", "Trust account must be ACTIVE to place an investment");
    }

    customerRepository
        .findById(request.customerId())
        .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

    // Check client has sufficient balance
    var ledgerCard =
        ledgerCardRepository
            .findByTrustAccountIdAndCustomerId(accountId, request.customerId())
            .orElse(null);

    if (ledgerCard == null || ledgerCard.getBalance().compareTo(request.principal()) < 0) {
      throw new InvalidStateException(
          "Insufficient balance",
          "Client does not have sufficient trust balance to place this investment");
    }

    // Create PAYMENT transaction (goes through normal approval workflow)
    var paymentRequest =
        new TrustTransactionService.RecordPaymentRequest(
            request.customerId(),
            null, // no project for investments
            request.principal(),
            "INV-" + request.institution() + "-" + request.accountNumber(),
            "Trust investment placement at " + request.institution(),
            request.depositDate());

    var paymentResponse = transactionService.recordPayment(accountId, paymentRequest);

    // Create investment record with ACTIVE status
    var investment =
        new TrustInvestment(
            accountId,
            request.customerId(),
            request.institution(),
            request.accountNumber(),
            request.principal(),
            request.interestRate(),
            request.depositDate(),
            request.maturityDate(),
            paymentResponse.id(),
            request.notes());

    investment = investmentRepository.save(investment);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_investment.placed")
            .entityType("trust_investment")
            .entityId(investment.getId())
            .details(
                Map.of(
                    "trust_account_id", accountId.toString(),
                    "customer_id", request.customerId().toString(),
                    "institution", request.institution(),
                    "principal", request.principal().toString(),
                    "interest_rate", request.interestRate().toString(),
                    "deposit_date", request.depositDate().toString()))
            .build());

    return toResponse(investment);
  }

  @Transactional
  public TrustInvestmentResponse recordInterestEarned(UUID investmentId, BigDecimal amount) {
    moduleGuard.requireModule(MODULE_ID);

    var investment =
        investmentRepository
            .findById(investmentId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustInvestment", investmentId));

    // Verify caller has access to the associated trust account
    var trustAccountId = investment.getTrustAccountId();
    trustAccountRepository
        .findById(trustAccountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", trustAccountId));

    if (!"ACTIVE".equals(investment.getStatus())) {
      throw new InvalidStateException(
          "Invalid investment state", "Investment must be ACTIVE to record interest earned");
    }

    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidStateException(
          "Invalid amount", "Interest amount must be greater than zero");
    }

    investment.setInterestEarned(investment.getInterestEarned().add(amount));
    investment = investmentRepository.save(investment);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_investment.interest_recorded")
            .entityType("trust_investment")
            .entityId(investment.getId())
            .details(
                Map.of(
                    "amount", amount.toString(),
                    "total_interest_earned", investment.getInterestEarned().toString()))
            .build());

    return toResponse(investment);
  }

  @Transactional
  public TrustInvestmentResponse withdrawInvestment(UUID investmentId) {
    moduleGuard.requireModule(MODULE_ID);

    var investment =
        investmentRepository
            .findById(investmentId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustInvestment", investmentId));

    // Verify caller has access to the associated trust account
    var investmentTrustAccountId = investment.getTrustAccountId();
    trustAccountRepository
        .findById(investmentTrustAccountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", investmentTrustAccountId));

    if (!"ACTIVE".equals(investment.getStatus()) && !"MATURED".equals(investment.getStatus())) {
      throw new InvalidStateException(
          "Invalid investment state", "Investment must be ACTIVE or MATURED to withdraw");
    }

    var today = LocalDate.now();
    var withdrawalAmount = investment.getPrincipal().add(investment.getInterestEarned());

    // Create DEPOSIT transaction (immediately credits client ledger)
    var depositRequest =
        new TrustTransactionService.RecordDepositRequest(
            investment.getCustomerId(),
            null, // no project for investments
            withdrawalAmount,
            "INV-WD-" + investment.getInstitution() + "-" + investment.getAccountNumber(),
            "Trust investment withdrawal from " + investment.getInstitution(),
            today);

    var depositResponse =
        transactionService.recordDeposit(investment.getTrustAccountId(), depositRequest);

    investment.setStatus("WITHDRAWN");
    investment.setWithdrawalDate(today);
    investment.setWithdrawalAmount(withdrawalAmount);
    investment.setWithdrawalTransactionId(depositResponse.id());
    investment = investmentRepository.save(investment);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_investment.withdrawn")
            .entityType("trust_investment")
            .entityId(investment.getId())
            .details(
                Map.of(
                    "withdrawal_amount", withdrawalAmount.toString(),
                    "principal", investment.getPrincipal().toString(),
                    "interest_earned", investment.getInterestEarned().toString(),
                    "deposit_transaction_id", depositResponse.id().toString()))
            .build());

    return toResponse(investment);
  }

  @Transactional(readOnly = true)
  public List<TrustInvestmentResponse> getMaturing(UUID accountId, int daysAhead) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    var today = LocalDate.now();
    var endDate = today.plusDays(daysAhead);

    return investmentRepository
        .findByTrustAccountIdAndStatusAndMaturityDateBetween(accountId, "ACTIVE", today, endDate)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<TrustInvestmentResponse> listInvestments(UUID accountId, Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    return investmentRepository
        .findByTrustAccountIdOrderByDepositDateDesc(accountId, pageable)
        .map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public TrustInvestmentResponse getInvestment(UUID investmentId) {
    moduleGuard.requireModule(MODULE_ID);

    var investment =
        investmentRepository
            .findById(investmentId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustInvestment", investmentId));

    // Verify caller has access to the associated trust account
    trustAccountRepository
        .findById(investment.getTrustAccountId())
        .orElseThrow(
            () -> new ResourceNotFoundException("TrustAccount", investment.getTrustAccountId()));

    return toResponse(investment);
  }

  // --- Private Helpers ---

  private TrustInvestmentResponse toResponse(TrustInvestment investment) {
    return new TrustInvestmentResponse(
        investment.getId(),
        investment.getTrustAccountId(),
        investment.getCustomerId(),
        investment.getInstitution(),
        investment.getAccountNumber(),
        investment.getPrincipal(),
        investment.getInterestRate(),
        investment.getDepositDate(),
        investment.getMaturityDate(),
        investment.getInterestEarned(),
        investment.getStatus(),
        investment.getWithdrawalDate(),
        investment.getWithdrawalAmount(),
        investment.getDepositTransactionId(),
        investment.getWithdrawalTransactionId(),
        investment.getNotes(),
        investment.getCreatedAt(),
        investment.getUpdatedAt());
  }
}
