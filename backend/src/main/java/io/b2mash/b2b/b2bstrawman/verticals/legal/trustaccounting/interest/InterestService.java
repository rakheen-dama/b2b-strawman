package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountingConstants;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.lpff.LpffRate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.lpff.LpffRateRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterestService {

  private static final Logger log = LoggerFactory.getLogger(InterestService.class);

  private static final String MODULE_ID = "trust_accounting";

  private static final Set<String> CREDIT_TYPES =
      Set.of("DEPOSIT", "TRANSFER_IN", "INTEREST_CREDIT");

  private static final Set<String> DEBIT_TYPES =
      Set.of("PAYMENT", "TRANSFER_OUT", "FEE_TRANSFER", "REFUND", "INTEREST_LPFF");

  private final InterestRunRepository interestRunRepository;
  private final InterestAllocationRepository interestAllocationRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final LpffRateRepository lpffRateRepository;
  private final TrustTransactionRepository trustTransactionRepository;
  private final ClientLedgerCardRepository clientLedgerCardRepository;
  private final TrustInvestmentRepository investmentRepository;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;

  public InterestService(
      InterestRunRepository interestRunRepository,
      InterestAllocationRepository interestAllocationRepository,
      TrustAccountRepository trustAccountRepository,
      LpffRateRepository lpffRateRepository,
      TrustTransactionRepository trustTransactionRepository,
      ClientLedgerCardRepository clientLedgerCardRepository,
      TrustInvestmentRepository investmentRepository,
      VerticalModuleGuard moduleGuard,
      AuditService auditService) {
    this.interestRunRepository = interestRunRepository;
    this.interestAllocationRepository = interestAllocationRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.lpffRateRepository = lpffRateRepository;
    this.trustTransactionRepository = trustTransactionRepository;
    this.clientLedgerCardRepository = clientLedgerCardRepository;
    this.investmentRepository = investmentRepository;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
  }

  // --- DTO Records ---

  public record InterestRunResponse(
      UUID id,
      UUID trustAccountId,
      LocalDate periodStart,
      LocalDate periodEnd,
      UUID lpffRateId,
      BigDecimal totalInterest,
      BigDecimal totalLpffShare,
      BigDecimal totalClientShare,
      String status,
      UUID createdBy,
      UUID approvedBy,
      Instant postedAt,
      Instant createdAt,
      Instant updatedAt) {}

  public record InterestRunDetailResponse(
      InterestRunResponse run, List<InterestAllocationResponse> allocations) {}

  public record InterestAllocationResponse(
      UUID id,
      UUID interestRunId,
      UUID customerId,
      BigDecimal averageDailyBalance,
      int daysInPeriod,
      BigDecimal grossInterest,
      BigDecimal lpffShare,
      BigDecimal clientShare,
      UUID trustTransactionId,
      UUID lpffRateId,
      boolean statutoryRateApplied,
      Instant createdAt) {}

  // --- Service Methods ---

  @Transactional
  public InterestRunResponse createInterestRun(
      UUID accountId, LocalDate periodStart, LocalDate periodEnd) {
    moduleGuard.requireModule(MODULE_ID);

    var account =
        trustAccountRepository
            .findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    if (account.getStatus() != TrustAccountStatus.ACTIVE) {
      throw new InvalidStateException(
          "Invalid account state", "Trust account must be ACTIVE to create an interest run");
    }

    if (periodEnd.isBefore(periodStart)) {
      throw new InvalidStateException(
          "Invalid period", "period_end must be on or after period_start");
    }

    if (interestRunRepository.existsOverlappingRun(accountId, periodStart, periodEnd)) {
      throw new ResourceConflictException(
          "Overlapping interest run",
          "An interest run already exists that overlaps the requested period");
    }

    var rate =
        lpffRateRepository
            .findFirstByTrustAccountIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                accountId, periodStart)
            .orElseThrow(
                () ->
                    new InvalidStateException(
                        "No LPFF rate configured", "No LPFF rate is effective on " + periodStart));

    var run = new InterestRun(accountId, periodStart, periodEnd, rate.getId());
    run.setCreatedBy(RequestScopes.requireMemberId());
    run = interestRunRepository.save(run);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("interest_run.created")
            .entityType("interest_run")
            .entityId(run.getId())
            .details(
                Map.of(
                    "trust_account_id", accountId.toString(),
                    "period_start", periodStart.toString(),
                    "period_end", periodEnd.toString(),
                    "lpff_rate_id", rate.getId().toString()))
            .build());

    return toRunResponse(run);
  }

  @Transactional
  public InterestRunResponse calculateInterest(UUID runId) {
    moduleGuard.requireModule(MODULE_ID);

    var run =
        interestRunRepository
            .findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("InterestRun", runId));

    if (!"DRAFT".equals(run.getStatus())) {
      throw new InvalidStateException(
          "Invalid interest run state", "Cannot calculate interest for a non-DRAFT run");
    }

    var accountId = run.getTrustAccountId();
    var periodStart = run.getPeriodStart();
    var periodEnd = run.getPeriodEnd();
    long daysInPeriod = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;

    // Get effective rate at period start
    var baseRate =
        lpffRateRepository
            .findFirstByTrustAccountIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                accountId, periodStart)
            .orElseThrow(
                () ->
                    new InvalidStateException(
                        "No LPFF rate configured", "No LPFF rate is effective on " + periodStart));

    // Check for mid-period rate changes
    var midPeriodRates =
        lpffRateRepository.findRateChangesInPeriod(accountId, periodStart, periodEnd);

    // Build rate segments
    var segments = buildRateSegments(periodStart, periodEnd, baseRate, midPeriodRates);

    // Get all clients with ledger cards
    var ledgerCards =
        clientLedgerCardRepository.findByTrustAccountId(accountId, Pageable.unpaged()).getContent();

    // Remove any existing allocations from a previous calculation of this draft run
    var existingAllocations = interestAllocationRepository.findByInterestRunId(runId);
    if (!existingAllocations.isEmpty()) {
      interestAllocationRepository.deleteAll(existingAllocations);
    }

    var totalInterest = BigDecimal.ZERO;
    var totalLpffShare = BigDecimal.ZERO;
    var totalClientShare = BigDecimal.ZERO;
    var allocations = new ArrayList<InterestAllocation>();

    for (var ledgerCard : ledgerCards) {
      var customerId = ledgerCard.getCustomerId();
      var clientGrossInterest = BigDecimal.ZERO;
      var clientLpffShareSum = BigDecimal.ZERO;
      var clientTotalBalanceDays = BigDecimal.ZERO;

      for (var segment : segments) {
        var segStart = segment.startDate();
        var segEnd = segment.endDate();
        long segDays = ChronoUnit.DAYS.between(segStart, segEnd) + 1;

        // Get opening balance at day before segment start
        var openingBalance =
            trustTransactionRepository.calculateClientBalanceAsOfDate(
                customerId, accountId, segStart.minusDays(1));
        if (openingBalance == null) {
          openingBalance = BigDecimal.ZERO;
        }

        // Get transactions in segment
        var transactions =
            trustTransactionRepository.findForStatement(customerId, accountId, segStart, segEnd);

        // Transaction-weighted balance-days calculation
        var balanceDays = BigDecimal.ZERO;
        var currentBalance = openingBalance;
        var currentDate = segStart;

        for (var txn : transactions) {
          var txnDate = txn.getTransactionDate();
          long daysBetween = ChronoUnit.DAYS.between(currentDate, txnDate);
          balanceDays = balanceDays.add(currentBalance.multiply(BigDecimal.valueOf(daysBetween)));

          // Apply transaction effect
          if (CREDIT_TYPES.contains(txn.getTransactionType())) {
            currentBalance = currentBalance.add(txn.getAmount());
          } else if (DEBIT_TYPES.contains(txn.getTransactionType())) {
            currentBalance = currentBalance.subtract(txn.getAmount());
          } else {
            log.warn(
                "Unrecognized transaction type '{}' for transaction {} — ignored in interest balance calculation",
                txn.getTransactionType(),
                txn.getId());
          }

          currentDate = txnDate;
        }

        // Remaining days after last transaction
        long remainingDays = ChronoUnit.DAYS.between(currentDate, segEnd) + 1;
        balanceDays = balanceDays.add(currentBalance.multiply(BigDecimal.valueOf(remainingDays)));

        clientTotalBalanceDays = clientTotalBalanceDays.add(balanceDays);

        // Calculate interest for this segment
        var avgDailyBalance =
            balanceDays.divide(BigDecimal.valueOf(segDays), 2, RoundingMode.HALF_UP);
        var segGross =
            avgDailyBalance
                .multiply(segment.ratePercent())
                .multiply(BigDecimal.valueOf(segDays))
                .divide(BigDecimal.valueOf(365), 10, RoundingMode.HALF_UP);
        clientGrossInterest = clientGrossInterest.add(segGross);

        // Compute LPFF share per-segment using the segment's own lpffSharePercent
        var segLpffShare =
            segGross
                .setScale(2, RoundingMode.HALF_UP)
                .multiply(segment.lpffSharePercent())
                .setScale(2, RoundingMode.HALF_UP);
        clientLpffShareSum = clientLpffShareSum.add(segLpffShare);
      }

      // Round gross interest
      var grossInterest = clientGrossInterest.setScale(2, RoundingMode.HALF_UP);

      if (BigDecimal.ZERO.compareTo(grossInterest) < 0) {
        // Check if the client has CLIENT_INSTRUCTION investments for this trust account
        var clientInvestments =
            investmentRepository.findByTrustAccountIdAndCustomerId(accountId, customerId);
        boolean hasClientInstructionInvestment =
            clientInvestments.stream()
                .anyMatch(
                    inv ->
                        inv.getInvestmentBasis() == InvestmentBasis.CLIENT_INSTRUCTION
                            && !inv.getDepositDate().isAfter(periodEnd)
                            && (inv.getWithdrawalDate() == null
                                || !inv.getWithdrawalDate().isBefore(periodStart)));

        // Determine LPFF share based on investment basis
        UUID allocationLpffRateId;
        boolean allocationStatutoryRateApplied;
        BigDecimal lpffShare;

        if (hasClientInstructionInvestment) {
          // Section 86(5): statutory 5% LPFF share for client-instructed investments
          lpffShare =
              grossInterest
                  .multiply(TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT)
                  .setScale(2, RoundingMode.HALF_UP);
          allocationLpffRateId = null;
          allocationStatutoryRateApplied = true;
        } else {
          // General arrangement: use configured LpffRate table rate
          lpffShare = clientLpffShareSum;
          allocationLpffRateId = baseRate.getId();
          allocationStatutoryRateApplied = false;
        }

        var clientShareAmount = grossInterest.subtract(lpffShare);

        var avgDailyBalance =
            clientTotalBalanceDays.divide(
                BigDecimal.valueOf(daysInPeriod), 2, RoundingMode.HALF_UP);

        var allocation =
            new InterestAllocation(
                runId,
                customerId,
                avgDailyBalance,
                (int) daysInPeriod,
                grossInterest,
                lpffShare,
                clientShareAmount,
                allocationLpffRateId,
                allocationStatutoryRateApplied);
        allocations.add(allocation);

        totalInterest = totalInterest.add(grossInterest);
        totalLpffShare = totalLpffShare.add(lpffShare);
        totalClientShare = totalClientShare.add(clientShareAmount);
      }
    }

    // Save allocations
    interestAllocationRepository.saveAll(allocations);

    // Update run totals
    run.setTotalInterest(totalInterest);
    run.setTotalLpffShare(totalLpffShare);
    run.setTotalClientShare(totalClientShare);
    run = interestRunRepository.save(run);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("interest_run.calculated")
            .entityType("interest_run")
            .entityId(run.getId())
            .details(
                Map.of(
                    "total_interest", totalInterest.toString(),
                    "total_lpff_share", totalLpffShare.toString(),
                    "total_client_share", totalClientShare.toString(),
                    "allocation_count", allocations.size()))
            .build());

    return toRunResponse(run);
  }

  @Transactional(readOnly = true)
  public List<InterestAllocationResponse> getAllocations(UUID runId) {
    moduleGuard.requireModule(MODULE_ID);

    interestRunRepository
        .findById(runId)
        .orElseThrow(() -> new ResourceNotFoundException("InterestRun", runId));

    return interestAllocationRepository.findByInterestRunId(runId).stream()
        .map(this::toAllocationResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<InterestRunResponse> listInterestRuns(UUID accountId) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    return interestRunRepository.findByTrustAccountIdOrderByPeriodEndDesc(accountId).stream()
        .map(this::toRunResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public InterestRunDetailResponse getInterestRunDetail(UUID runId) {
    moduleGuard.requireModule(MODULE_ID);

    var run =
        interestRunRepository
            .findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("InterestRun", runId));

    var allocations =
        interestAllocationRepository.findByInterestRunId(runId).stream()
            .map(this::toAllocationResponse)
            .toList();

    return new InterestRunDetailResponse(toRunResponse(run), allocations);
  }

  @Transactional
  public InterestRunResponse approveInterestRun(UUID runId) {
    moduleGuard.requireModule(MODULE_ID);

    var approverId = RequestScopes.requireMemberId();

    var run =
        interestRunRepository
            .findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("InterestRun", runId));

    if (!"DRAFT".equals(run.getStatus())) {
      throw new InvalidStateException(
          "Invalid interest run state", "Interest run must be in DRAFT status to be approved");
    }

    // Prevent approval of uncalculated runs (no allocations = nothing to post)
    var allocations = interestAllocationRepository.findByInterestRunId(runId);
    if (allocations.isEmpty()) {
      throw new InvalidStateException(
          "Interest run not calculated",
          "Cannot approve an interest run with no calculated allocations");
    }

    // Self-approval prevention: approver cannot be the creator
    if (run.getCreatedBy() != null && approverId.equals(run.getCreatedBy())) {
      throw new InvalidStateException(
          "Self-approval not allowed",
          "The interest run creator cannot approve their own interest run");
    }

    run.setStatus("APPROVED");
    run.setApprovedBy(approverId);
    run = interestRunRepository.save(run);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("interest_run.approved")
            .entityType("interest_run")
            .entityId(run.getId())
            .details(
                Map.of(
                    "approved_by", approverId.toString(),
                    "total_interest", run.getTotalInterest().toString()))
            .build());

    return toRunResponse(run);
  }

  @Transactional
  public InterestRunResponse postInterestRun(UUID runId) {
    moduleGuard.requireModule(MODULE_ID);

    var memberId = RequestScopes.requireMemberId();

    var run =
        interestRunRepository
            .findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("InterestRun", runId));

    if (!"APPROVED".equals(run.getStatus())) {
      throw new InvalidStateException(
          "Invalid interest run state", "Interest run must be in APPROVED status to be posted");
    }

    // Re-validate trust account is still ACTIVE (may have been frozen/closed since approval)
    var trustAccountIdForCheck = run.getTrustAccountId();
    var account =
        trustAccountRepository
            .findById(trustAccountIdForCheck)
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustAccount", trustAccountIdForCheck));
    if (account.getStatus() != TrustAccountStatus.ACTIVE) {
      throw new InvalidStateException(
          "Invalid account state", "Trust account must be ACTIVE to post an interest run");
    }

    var allocations = interestAllocationRepository.findByInterestRunId(runId);
    var accountId = run.getTrustAccountId();
    var transactionDate = run.getPeriodEnd();

    // Create INTEREST_CREDIT transactions for each client allocation with client_share > 0
    for (var allocation : allocations) {
      if (allocation.getClientShare().compareTo(BigDecimal.ZERO) > 0) {
        var creditTxn =
            new TrustTransaction(
                accountId,
                "INTEREST_CREDIT",
                allocation.getClientShare(),
                allocation.getCustomerId(),
                null,
                null,
                "INT-CREDIT-" + runId + "-" + allocation.getCustomerId(),
                "Interest credit for period " + run.getPeriodStart() + " to " + run.getPeriodEnd(),
                transactionDate,
                "RECORDED",
                memberId);
        var savedTxn = trustTransactionRepository.save(creditTxn);
        allocation.setTrustTransactionId(savedTxn.getId());

        // Update client ledger card
        var ledgerCard =
            clientLedgerCardRepository
                .findByAccountAndCustomerForUpdate(accountId, allocation.getCustomerId())
                .orElseThrow(
                    () ->
                        new InvalidStateException(
                            "Ledger card not found",
                            "No client ledger card found for customer "
                                + allocation.getCustomerId()));
        ledgerCard.addInterestCredit(allocation.getClientShare(), transactionDate);
        clientLedgerCardRepository.save(ledgerCard);
      }
    }

    interestAllocationRepository.saveAll(allocations);

    // Create single INTEREST_LPFF transaction if total LPFF share > 0
    if (run.getTotalLpffShare().compareTo(BigDecimal.ZERO) > 0) {
      var lpffTxn =
          new TrustTransaction(
              accountId,
              "INTEREST_LPFF",
              run.getTotalLpffShare(),
              null, // No customer for LPFF — firm-level outflow
              null,
              null,
              "INT-LPFF-" + runId,
              "LPFF interest share for period "
                  + run.getPeriodStart()
                  + " to "
                  + run.getPeriodEnd(),
              transactionDate,
              "RECORDED",
              memberId);
      trustTransactionRepository.save(lpffTxn);
    }

    run.setStatus("POSTED");
    run.setPostedAt(Instant.now());
    run = interestRunRepository.save(run);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("interest_run.posted")
            .entityType("interest_run")
            .entityId(run.getId())
            .details(
                Map.of(
                    "posted_by", memberId.toString(),
                    "total_interest", run.getTotalInterest().toString(),
                    "total_lpff_share", run.getTotalLpffShare().toString(),
                    "total_client_share", run.getTotalClientShare().toString()))
            .build());

    return toRunResponse(run);
  }

  // --- Private Helpers ---

  private record RateSegment(
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal ratePercent,
      BigDecimal lpffSharePercent) {}

  private List<RateSegment> buildRateSegments(
      LocalDate periodStart,
      LocalDate periodEnd,
      LpffRate baseRate,
      List<LpffRate> midPeriodRates) {
    var segments = new ArrayList<RateSegment>();

    if (midPeriodRates.isEmpty()) {
      segments.add(
          new RateSegment(
              periodStart, periodEnd, baseRate.getRatePercent(), baseRate.getLpffSharePercent()));
    } else {
      // First segment: from period_start to day before first mid-period rate change
      var firstChange = midPeriodRates.getFirst();
      segments.add(
          new RateSegment(
              periodStart,
              firstChange.getEffectiveFrom().minusDays(1),
              baseRate.getRatePercent(),
              baseRate.getLpffSharePercent()));

      // Middle segments (if more than one mid-period rate change)
      for (int i = 0; i < midPeriodRates.size(); i++) {
        var rate = midPeriodRates.get(i);
        var segEnd =
            (i + 1 < midPeriodRates.size())
                ? midPeriodRates.get(i + 1).getEffectiveFrom().minusDays(1)
                : periodEnd;
        segments.add(
            new RateSegment(
                rate.getEffectiveFrom(),
                segEnd,
                rate.getRatePercent(),
                rate.getLpffSharePercent()));
      }
    }

    return segments;
  }

  private InterestRunResponse toRunResponse(InterestRun run) {
    return new InterestRunResponse(
        run.getId(),
        run.getTrustAccountId(),
        run.getPeriodStart(),
        run.getPeriodEnd(),
        run.getLpffRateId(),
        run.getTotalInterest(),
        run.getTotalLpffShare(),
        run.getTotalClientShare(),
        run.getStatus(),
        run.getCreatedBy(),
        run.getApprovedBy(),
        run.getPostedAt(),
        run.getCreatedAt(),
        run.getUpdatedAt());
  }

  private InterestAllocationResponse toAllocationResponse(InterestAllocation alloc) {
    return new InterestAllocationResponse(
        alloc.getId(),
        alloc.getInterestRunId(),
        alloc.getCustomerId(),
        alloc.getAverageDailyBalance(),
        alloc.getDaysInPeriod(),
        alloc.getGrossInterest(),
        alloc.getLpffShare(),
        alloc.getClientShare(),
        alloc.getTrustTransactionId(),
        alloc.getLpffRateId(),
        alloc.isStatutoryRateApplied(),
        alloc.getCreatedAt());
  }
}
