package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountingConstants;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustDomainEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.lpff.LpffRate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.lpff.LpffRateRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
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
  private final InterestAllocationLineRepository interestAllocationLineRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final LpffRateRepository lpffRateRepository;
  private final TrustTransactionRepository trustTransactionRepository;
  private final ClientLedgerCardRepository clientLedgerCardRepository;
  private final TrustInvestmentRepository investmentRepository;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public InterestService(
      InterestRunRepository interestRunRepository,
      InterestAllocationRepository interestAllocationRepository,
      InterestAllocationLineRepository interestAllocationLineRepository,
      TrustAccountRepository trustAccountRepository,
      LpffRateRepository lpffRateRepository,
      TrustTransactionRepository trustTransactionRepository,
      ClientLedgerCardRepository clientLedgerCardRepository,
      TrustInvestmentRepository investmentRepository,
      VerticalModuleGuard moduleGuard,
      AuditService auditService,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.interestRunRepository = interestRunRepository;
    this.interestAllocationRepository = interestAllocationRepository;
    this.interestAllocationLineRepository = interestAllocationLineRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.lpffRateRepository = lpffRateRepository;
    this.trustTransactionRepository = trustTransactionRepository;
    this.clientLedgerCardRepository = clientLedgerCardRepository;
    this.investmentRepository = investmentRepository;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
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
      Instant updatedAt,
      // Count of interest_allocations: 0 = never calculated (or nothing to allocate) — the only
      // honest "calculated" discriminator, since a calculated run can total 0 (630B, ADR-362).
      int allocationCount) {}

  public record InterestRunDetailResponse(
      InterestRunResponse run, List<InterestAllocationResponse> allocations) {}

  public record InterestAllocationResponse(
      UUID id,
      UUID interestRunId,
      UUID clientId,
      // Null only when the client row is gone; the wizard then falls back to a short id (G89-F10)
      String clientName,
      BigDecimal averageDailyBalance,
      int daysInPeriod,
      BigDecimal grossInterest,
      BigDecimal lpffShare,
      BigDecimal clientShare,
      UUID trustTransactionId,
      UUID lpffRateId,
      boolean statutoryRateApplied,
      // LPFF share fraction applied (0.05 statutory, else the run's LPFF rate share; G89-F10)
      BigDecimal lpffSharePercent,
      Instant createdAt,
      // The client share split across matters (ADR-366); empty for a run calculated before V12
      List<InterestAllocationLineResponse> lines) {}

  /** One matter's share of a client's allocation; {@code matterId} null = unmattered bucket. */
  public record InterestAllocationLineResponse(
      UUID id,
      UUID matterId,
      String matterName,
      BigDecimal balanceDays,
      BigDecimal clientShare,
      UUID trustTransactionId) {}

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

    requirePeriodEnded(periodEnd);

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
        clientLedgerCardRepository
            .findByTrustAccountId(accountId, PageRequest.of(0, 10_000))
            .getContent();

    // Remove any existing allocations (matter lines first: no ON DELETE CASCADE) from a previous
    // calculation of this draft run, and flush so the deletes precede the new inserts.
    var existingAllocations = interestAllocationRepository.findByInterestRunId(runId);
    if (!existingAllocations.isEmpty()) {
      interestAllocationLineRepository.deleteByInterestAllocationIdIn(
          existingAllocations.stream().map(InterestAllocation::getId).toList());
      interestAllocationRepository.deleteAll(existingAllocations);
      interestAllocationRepository.flush();
    }

    var totalInterest = BigDecimal.ZERO;
    var totalLpffShare = BigDecimal.ZERO;
    var totalClientShare = BigDecimal.ZERO;
    var allocations = new ArrayList<InterestAllocation>();
    var pendingLines = new LinkedHashMap<InterestAllocation, List<PendingLine>>();

    for (var ledgerCard : ledgerCards) {
      var customerId = ledgerCard.getCustomerId();
      var clientGrossInterest = BigDecimal.ZERO;
      var clientLpffShareSum = BigDecimal.ZERO;
      var clientTotalBalanceDays = BigDecimal.ZERO;
      // Per-matter buckets of this client (ADR-366): balance-days and allocation weights
      var buckets = new LinkedHashMap<Bucket, BucketTotals>();

      for (var segment : segments) {
        long segDays = ChronoUnit.DAYS.between(segment.startDate(), segment.endDate()) + 1;

        // The client's segment balance-days is the exact sum of its matter buckets' balance-days
        var balanceDays = accumulateSegment(customerId, accountId, segment, buckets);

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
        pendingLines.put(
            allocation,
            splitAcrossMatters(
                customerId, clientShareAmount, buckets, allocationStatutoryRateApplied));

        totalInterest = totalInterest.add(grossInterest);
        totalLpffShare = totalLpffShare.add(lpffShare);
        totalClientShare = totalClientShare.add(clientShareAmount);
      }
    }

    // Save allocations, then their matter lines (which reference the allocation ids)
    interestAllocationRepository.saveAll(allocations);
    var lines = new ArrayList<InterestAllocationLine>();
    pendingLines.forEach(
        (allocation, pending) ->
            pending.forEach(
                line ->
                    lines.add(
                        new InterestAllocationLine(
                            allocation.getId(),
                            line.projectId(),
                            line.balanceDays(),
                            line.weight().setScale(10, RoundingMode.HALF_UP),
                            line.clientShare()))));
    interestAllocationLineRepository.saveAll(lines);

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

    return toAllocationResponses(interestAllocationRepository.findByInterestRunId(runId));
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
        toAllocationResponses(interestAllocationRepository.findByInterestRunId(runId));

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

    // A DRAFT created before the create-time guard existed (G89-F11) must not be approved either
    requirePeriodEnded(run.getPeriodEnd());

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

    // Posting dates the INTEREST_CREDIT / INTEREST_LPFF rows on period_end: never in the future
    requirePeriodEnded(run.getPeriodEnd());

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

    var linesByAllocation = linesByAllocation(allocations);
    var description =
        "Interest credit for period " + run.getPeriodStart() + " to " + run.getPeriodEnd();

    // ADR-366: one INTEREST_CREDIT per matter line, carrying the line's matter, so the matter
    // sub-ledger (closure gate, matter Trust tab, portal) holds the interest it earned. A run with
    // no lines (calculated before V12, D6) posts one client-level credit as before.
    for (var allocation : allocations) {
      if (allocation.getClientShare().compareTo(BigDecimal.ZERO) > 0) {
        var customerId = allocation.getCustomerId();

        // Lock the client card once, before writing any of the client's credits
        var ledgerCard =
            clientLedgerCardRepository
                .findByAccountAndCustomerForUpdate(accountId, customerId)
                .orElseThrow(
                    () ->
                        new InvalidStateException(
                            "Ledger card not found",
                            "No client ledger card found for client " + customerId));

        var lines = linesByAllocation.getOrDefault(allocation.getId(), List.of());
        if (lines.isEmpty()) {
          var savedTxn =
              trustTransactionRepository.save(
                  interestCredit(
                      accountId,
                      allocation.getClientShare(),
                      customerId,
                      null,
                      interestCreditReference(runId, customerId, null),
                      description,
                      transactionDate,
                      memberId));
          allocation.setTrustTransactionId(savedTxn.getId());
        } else {
          for (var line : lines) {
            var reference = interestCreditReference(runId, customerId, line.getProjectId());
            var savedTxn =
                trustTransactionRepository.save(
                    interestCredit(
                        accountId,
                        line.getClientShare(),
                        customerId,
                        line.getProjectId(),
                        reference,
                        description,
                        transactionDate,
                        memberId));
            line.setTrustTransactionId(savedTxn.getId());
          }
          interestAllocationLineRepository.saveAll(lines);
          // The allocation points at its credit only when there is exactly one
          allocation.setTrustTransactionId(
              lines.size() == 1 ? lines.getFirst().getTrustTransactionId() : null);
        }

        // The card takes the client share once (the lines sum to it)
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
    run.setPostedAt(clock.instant());
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

    // G89-F22: both listeners run AFTER_COMMIT — the portal trust read-model re-projects every
    // matter on the account (the matter interest credits), and firm admins/owners are notified.
    eventPublisher.publishEvent(
        TrustDomainEvent.InterestPosted.of(
            run.getId(),
            accountId,
            run.getPeriodStart(),
            run.getPeriodEnd(),
            run.getTotalInterest(),
            run.getTotalClientShare(),
            run.getApprovedBy(),
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull()));

    return toRunResponse(run);
  }

  /**
   * Deletes a mistaken DRAFT run and its allocations (founder ruling §9.2, ADR-362). No financial
   * effect: nothing reaches trust_transactions before approval and posting, so there is no reversal
   * and no status value. APPROVED / POSTED runs are refused.
   */
  @Transactional
  public void deleteInterestRun(UUID runId) {
    moduleGuard.requireModule(MODULE_ID);

    // Row lock: a concurrent approve that commits first is seen here as APPROVED (refused); one
    // that comes second blocks, then fails on the deleted row.
    var run =
        interestRunRepository
            .findByIdForUpdate(runId)
            .orElseThrow(() -> new ResourceNotFoundException("InterestRun", runId));

    if (!"DRAFT".equals(run.getStatus())) {
      throw new InvalidStateException(
          "Invalid interest run state", "Only DRAFT interest runs can be deleted");
    }

    // interest_allocations.interest_run_id has no ON DELETE CASCADE (V1 baseline) and no JPA
    // relationship: children first, in this transaction — the recalculation path's pattern.
    // The allocations' matter lines go first (ADR-366), likewise without a cascade.
    var allocations = interestAllocationRepository.findByInterestRunId(runId);
    if (!allocations.isEmpty()) {
      interestAllocationLineRepository.deleteByInterestAllocationIdIn(
          allocations.stream().map(InterestAllocation::getId).toList());
    }
    interestAllocationRepository.deleteAll(allocations);
    interestRunRepository.delete(run);

    // LinkedHashMap, not Map.of: created_by is nullable on legacy rows and Map.of rejects nulls.
    var details = new LinkedHashMap<String, Object>();
    details.put("trust_account_id", run.getTrustAccountId().toString());
    details.put("period_start", run.getPeriodStart().toString());
    details.put("period_end", run.getPeriodEnd().toString());
    details.put("total_interest", run.getTotalInterest().toString());
    details.put("total_lpff_share", run.getTotalLpffShare().toString());
    details.put("total_client_share", run.getTotalClientShare().toString());
    details.put("allocation_count", allocations.size());
    if (run.getCreatedBy() != null) {
      details.put("created_by", run.getCreatedBy().toString());
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("interest_run.deleted")
            .entityType("interest_run")
            .entityId(runId)
            .details(details)
            .build());
  }

  // --- Private Helpers ---

  /**
   * Interest is earned over a period that has ended: posting dates the ledger rows on period_end,
   * so a period ending after today (South African time, via the injected {@link Clock}) would book
   * future-dated interest (G89-F11). A period ending today is allowed — firms run interest on the
   * last day of the month.
   */
  private void requirePeriodEnded(LocalDate periodEnd) {
    var today = LocalDate.now(clock);
    if (periodEnd.isAfter(today)) {
      throw new InvalidStateException(
          "Interest period has not ended",
          "The interest period ends on "
              + periodEnd
              + ", which is after today ("
              + today
              + ", South African time). Run interest only for a period that has ended.");
    }
  }

  private record RateSegment(
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal ratePercent,
      BigDecimal lpffSharePercent) {}

  // --- Matter allocation (ADR-366) ---

  /**
   * A client's matter bucket; {@code projectId == null} is the unmattered (client-level) bucket.
   */
  private record Bucket(UUID projectId) {}

  /** Running totals of one bucket across the run's rate segments. */
  private static final class BucketTotals {
    private BigDecimal balanceDays = BigDecimal.ZERO;
    // Σ balanceDays × rate × (1 − LPFF share): the bucket's weight under the LPFF rate table
    private BigDecimal generalWeight = BigDecimal.ZERO;
    // Σ balanceDays × rate: its weight when the statutory 5 % applies (a flat LPFF fraction)
    private BigDecimal statutoryWeight = BigDecimal.ZERO;
  }

  /** A matter line before its allocation is saved. */
  private record PendingLine(
      UUID projectId, BigDecimal balanceDays, BigDecimal weight, BigDecimal clientShare) {}

  /** Lines first by matter id, the unmattered bucket last. */
  private static final Comparator<UUID> PROJECT_ID_NULLS_LAST =
      Comparator.nullsLast(Comparator.naturalOrder());

  /**
   * Walks one rate segment for one client, bucket by bucket: each bucket opens at its balance on
   * the day before the segment and each transaction moves its own bucket, while the intervals
   * between transactions are the client's (ADR-234's transaction-weighted method). Adds the
   * segment's balance-days and weights to {@code buckets} and returns the client's segment
   * balance-days — the exact sum of the buckets' (BigDecimal addition and multiplication are
   * exact), hence identical to the client-level walk.
   */
  private BigDecimal accumulateSegment(
      UUID customerId, UUID accountId, RateSegment segment, Map<Bucket, BucketTotals> buckets) {
    var segStart = segment.startDate();
    var segEnd = segment.endDate();

    var balances = new LinkedHashMap<Bucket, BigDecimal>();
    for (var row :
        trustTransactionRepository.calculateClientMatterBalancesAsOfDate(
            customerId, accountId, segStart.minusDays(1))) {
      balances.put(
          new Bucket(row.getProjectId()),
          row.getBalance() != null ? row.getBalance() : BigDecimal.ZERO);
    }

    var segBalanceDays = new LinkedHashMap<Bucket, BigDecimal>();
    var currentDate = segStart;
    for (var txn :
        trustTransactionRepository.findForStatement(customerId, accountId, segStart, segEnd)) {
      var txnDate = txn.getTransactionDate();
      accrue(balances, segBalanceDays, ChronoUnit.DAYS.between(currentDate, txnDate));

      // Apply transaction effect to its own bucket
      var bucket = new Bucket(txn.getProjectId());
      if (CREDIT_TYPES.contains(txn.getTransactionType())) {
        balances.merge(bucket, txn.getAmount(), BigDecimal::add);
      } else if (DEBIT_TYPES.contains(txn.getTransactionType())) {
        balances.merge(bucket, txn.getAmount().negate(), BigDecimal::add);
      } else {
        log.warn(
            "Unrecognized transaction type '{}' for transaction {} — ignored in interest balance calculation",
            txn.getTransactionType(),
            txn.getId());
      }

      currentDate = txnDate;
    }

    // Remaining days after last transaction
    accrue(balances, segBalanceDays, ChronoUnit.DAYS.between(currentDate, segEnd) + 1);

    var clientFraction = BigDecimal.ONE.subtract(segment.lpffSharePercent());
    var clientBalanceDays = BigDecimal.ZERO;
    for (var entry : segBalanceDays.entrySet()) {
      var balanceDays = entry.getValue();
      clientBalanceDays = clientBalanceDays.add(balanceDays);
      var totals = buckets.computeIfAbsent(entry.getKey(), b -> new BucketTotals());
      var rated = balanceDays.multiply(segment.ratePercent());
      totals.balanceDays = totals.balanceDays.add(balanceDays);
      totals.statutoryWeight = totals.statutoryWeight.add(rated);
      totals.generalWeight = totals.generalWeight.add(rated.multiply(clientFraction));
    }
    return clientBalanceDays;
  }

  /** Adds {@code balance × days} to every bucket's segment balance-days. */
  private static void accrue(
      Map<Bucket, BigDecimal> balances, Map<Bucket, BigDecimal> segBalanceDays, long days) {
    var dayCount = BigDecimal.valueOf(days);
    balances.forEach(
        (bucket, balance) ->
            segBalanceDays.merge(bucket, balance.multiply(dayCount), BigDecimal::add));
  }

  /**
   * Splits a client's share across its matter buckets in proportion to their weights, to the cent
   * by largest remainder (ADR-366). Only buckets with a positive weight take part. Each exact share
   * is rounded down to the cent; the leftover cents go one each by larger fractional remainder,
   * then larger weight, then matter id ascending with the unmattered bucket last — deterministic
   * for a given ledger. The lines always sum to {@code clientShare}; lines of R0,00 are dropped.
   */
  private List<PendingLine> splitAcrossMatters(
      UUID customerId,
      BigDecimal clientShare,
      Map<Bucket, BucketTotals> buckets,
      boolean statutoryRateApplied) {
    if (clientShare.signum() <= 0) {
      return List.of();
    }

    record Candidate(
        Bucket bucket,
        BigDecimal balanceDays,
        BigDecimal weight,
        BigDecimal floor,
        BigDecimal remainder) {}

    var weighted = new ArrayList<Map.Entry<Bucket, BucketTotals>>();
    var totalWeight = BigDecimal.ZERO;
    for (var entry : buckets.entrySet()) {
      var weight = weightOf(entry.getValue(), statutoryRateApplied);
      if (weight.signum() > 0) {
        weighted.add(entry);
        totalWeight = totalWeight.add(weight);
      }
    }
    if (weighted.isEmpty()) {
      // Unreachable for a positive share (it needs positive balance-days at a positive rate); if
      // it ever happens the post falls back to one client-level credit, as for a pre-V12 run.
      log.warn(
          "Interest client share {} for client {} has no positively weighted matter bucket — no"
              + " matter lines; posting falls back to a single client-level credit",
          clientShare,
          customerId);
      return List.of();
    }

    var candidates = new ArrayList<Candidate>();
    var allocated = BigDecimal.ZERO;
    for (var entry : weighted) {
      var weight = weightOf(entry.getValue(), statutoryRateApplied);
      var exact = clientShare.multiply(weight).divide(totalWeight, 12, RoundingMode.DOWN);
      var floor = exact.setScale(2, RoundingMode.DOWN);
      allocated = allocated.add(floor);
      candidates.add(
          new Candidate(
              entry.getKey(), entry.getValue().balanceDays, weight, floor, exact.subtract(floor)));
    }

    candidates.sort(
        Comparator.comparing(Candidate::remainder, Comparator.reverseOrder())
            .thenComparing(Candidate::weight, Comparator.reverseOrder())
            .thenComparing(c -> c.bucket().projectId(), PROJECT_ID_NULLS_LAST));

    int leftoverCents = clientShare.subtract(allocated).movePointRight(2).intValueExact();
    if (leftoverCents < 0 || leftoverCents > candidates.size()) {
      throw new IllegalStateException(
          "Interest matter allocation for client "
              + customerId
              + " left "
              + leftoverCents
              + " cents over "
              + candidates.size()
              + " matter buckets");
    }

    var cent = new BigDecimal("0.01");
    var lines = new ArrayList<PendingLine>();
    var total = BigDecimal.ZERO;
    for (int i = 0; i < candidates.size(); i++) {
      var candidate = candidates.get(i);
      var share = i < leftoverCents ? candidate.floor().add(cent) : candidate.floor();
      total = total.add(share);
      if (share.signum() > 0) {
        lines.add(
            new PendingLine(
                candidate.bucket().projectId(),
                candidate.balanceDays(),
                candidate.weight(),
                share));
      }
    }
    if (total.compareTo(clientShare) != 0) {
      throw new IllegalStateException(
          "Interest matter lines for client "
              + customerId
              + " sum to "
              + total
              + ", not the client share "
              + clientShare);
    }
    lines.sort(Comparator.comparing(PendingLine::projectId, PROJECT_ID_NULLS_LAST));
    return lines;
  }

  private static BigDecimal weightOf(BucketTotals totals, boolean statutoryRateApplied) {
    return statutoryRateApplied ? totals.statutoryWeight : totals.generalWeight;
  }

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
        run.getUpdatedAt(),
        Math.toIntExact(interestAllocationRepository.countByInterestRunId(run.getId())));
  }

  /**
   * Maps allocations with the client name and applied LPFF share: one query for the names and one
   * for the rates, whatever the allocation count (no N+1).
   */
  private List<InterestAllocationResponse> toAllocationResponses(
      List<InterestAllocation> allocations) {
    var customerIds =
        allocations.stream().map(InterestAllocation::getCustomerId).distinct().toList();
    Map<UUID, String> clientNames =
        customerRepository.findAllById(customerIds).stream()
            .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));

    var rateIds =
        allocations.stream()
            .map(InterestAllocation::getLpffRateId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<UUID, LpffRate> rates =
        lpffRateRepository.findAllById(rateIds).stream()
            .collect(Collectors.toMap(LpffRate::getId, Function.identity()));

    // Matter lines (ADR-366) and their matter names: one query each
    var linesByAllocation = linesByAllocation(allocations);
    var projectIds =
        linesByAllocation.values().stream()
            .flatMap(List::stream)
            .map(InterestAllocationLine::getProjectId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<UUID, String> projectNames =
        projectIds.isEmpty()
            ? Map.of()
            : projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName, (a, b) -> a));

    return allocations.stream()
        .map(
            alloc -> {
              BigDecimal lpffSharePercent;
              if (alloc.isStatutoryRateApplied()) {
                lpffSharePercent = TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT;
              } else {
                var rate = rates.get(alloc.getLpffRateId());
                lpffSharePercent = rate != null ? rate.getLpffSharePercent() : null;
              }
              var lines =
                  linesByAllocation.getOrDefault(alloc.getId(), List.of()).stream()
                      .map(
                          line ->
                              new InterestAllocationLineResponse(
                                  line.getId(),
                                  line.getProjectId(),
                                  line.getProjectId() != null
                                      ? projectNames.get(line.getProjectId())
                                      : null,
                                  line.getBalanceDays(),
                                  line.getClientShare(),
                                  line.getTrustTransactionId()))
                      .toList();
              return toAllocationResponse(
                  alloc, clientNames.get(alloc.getCustomerId()), lpffSharePercent, lines);
            })
        .toList();
  }

  /** The allocations' matter lines (one query), each list by matter id, unmattered last. */
  private Map<UUID, List<InterestAllocationLine>> linesByAllocation(
      List<InterestAllocation> allocations) {
    if (allocations.isEmpty()) {
      return Map.of();
    }
    var allocationIds = allocations.stream().map(InterestAllocation::getId).toList();
    var byAllocation = new LinkedHashMap<UUID, List<InterestAllocationLine>>();
    interestAllocationLineRepository.findByInterestAllocationIdIn(allocationIds).stream()
        .sorted(Comparator.comparing(InterestAllocationLine::getProjectId, PROJECT_ID_NULLS_LAST))
        .forEach(
            line ->
                byAllocation
                    .computeIfAbsent(line.getInterestAllocationId(), id -> new ArrayList<>())
                    .add(line));
    return byAllocation;
  }

  /**
   * The INTEREST_CREDIT reference (ADR-366). A matter line is run + matter + the client's first 8
   * hex characters, so co-clients on one matter in one run get distinct references (reference-based
   * bank matching); 93 characters. The unmattered line keeps run + client (84). Run + client +
   * matter in full would be 121 characters, past the portal read-model's {@code reference
   * varchar(100)}, which would abort the portal fan-out for the whole account.
   */
  static String interestCreditReference(UUID runId, UUID customerId, UUID projectId) {
    if (projectId == null) {
      return "INT-CREDIT-" + runId + "-" + customerId;
    }
    return "INT-CREDIT-" + runId + "-" + projectId + "-" + customerId.toString().substring(0, 8);
  }

  private static TrustTransaction interestCredit(
      UUID accountId,
      BigDecimal amount,
      UUID customerId,
      UUID projectId,
      String reference,
      String description,
      LocalDate transactionDate,
      UUID memberId) {
    return new TrustTransaction(
        accountId,
        "INTEREST_CREDIT",
        amount,
        customerId,
        projectId,
        null,
        reference,
        description,
        transactionDate,
        "RECORDED",
        memberId);
  }

  private InterestAllocationResponse toAllocationResponse(
      InterestAllocation alloc,
      String clientName,
      BigDecimal lpffSharePercent,
      List<InterestAllocationLineResponse> lines) {
    return new InterestAllocationResponse(
        alloc.getId(),
        alloc.getInterestRunId(),
        alloc.getCustomerId(),
        clientName,
        alloc.getAverageDailyBalance(),
        alloc.getDaysInPeriod(),
        alloc.getGrossInterest(),
        alloc.getLpffShare(),
        alloc.getClientShare(),
        alloc.getTrustTransactionId(),
        alloc.getLpffRateId(),
        alloc.isStatutoryRateApplied(),
        lpffSharePercent,
        alloc.getCreatedAt(),
        lines);
  }
}
