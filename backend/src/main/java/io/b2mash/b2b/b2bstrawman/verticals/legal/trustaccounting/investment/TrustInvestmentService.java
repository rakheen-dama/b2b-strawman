package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import io.b2mash.b2b.b2bstrawman.audit.AuditDetails;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionApprovalEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionRecordedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.InvestmentBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.InvestmentPlacementHooks;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustAmounts;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustClientMatterRule;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trust investments on the books (Phase 92 648A, ADR-379, architecture §16.3.7). An investment is
 * placed from a funding trust banking account {@code G} into an {@code INVESTMENT} trust account
 * {@code I} whose basis is the investment's, and both moves are rows on the books:
 *
 * <ul>
 *   <li><b>Section 86(4)</b> ({@code CLIENT_INSTRUCTION}) — {@code INV_PLACE} on G (client and
 *       matter) awaits approval; on approval it posts and {@code INV_RECEIVE} posts on I (the
 *       client's investment ledger), so the client's total is unchanged. Withdrawal: {@code
 *       INV_WITHDRAW} on I and {@code INV_RETURN} on G (the placement's matter), both RECORDED, for
 *       the investment's balance (capital plus net interest).
 *   <li><b>Section 86(3)</b> ({@code FIRM_DISCRETION}) — the {@code FIRM_INV_*} twins on the
 *       inter-account ledger of both accounts (the terms cancel firm-wide); no client or matter.
 *       Withdrawal returns the principal (the interest is the Fund's).
 * </ul>
 *
 * <p>Insert order (one transaction): the investment first ({@code deposit_transaction_id} null),
 * then its reference, then the placement row carrying {@code investment_id}, then {@code
 * deposit_transaction_id}. References are Kazi's: {@code INV-} and the first 8 hex digits of the
 * investment's id on the placement pair, {@code INV-WD-} and the same digits on the withdrawal
 * pair. Every posting goes through {@link TrustPostingService}; the transaction repository is read
 * and locked only. Implements {@link InvestmentPlacementHooks} (no dependency on {@code
 * TrustTransactionService}, so no bean cycle; its amount rule is {@link TrustAmounts}). Lock order:
 * a placement request takes the funding account, then the investment account (then the client's
 * lock anchor); its approval, the placement row, then the funding account (section 86(3)) or the
 * client's lock anchor (section 86(4)), then the investment row; a withdrawal, the placement row,
 * then the investment row (then the client's lock anchor).
 */
@Service
public class TrustInvestmentService implements InvestmentPlacementHooks {

  private static final String MODULE_ID = "trust_accounting";
  private static final String PLACE_TITLE = "Cannot place investment";
  private static final String WITHDRAW_TITLE = "Cannot withdraw investment";
  private static final String ACTIVE = "ACTIVE";
  private static final String MATURED = "MATURED";
  private static final Set<String> HELD = Set.of(ACTIVE, MATURED);

  /** Trust money is rand: refusal messages use the house en-ZA format (G89-F40). */
  private static final Locale TRUST_LOCALE = VariableFormatter.resolveLocale("ZAR");

  private final TrustInvestmentRepository investmentRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final CustomerRepository customerRepository;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final TrustTransactionRepository transactionRepository;
  private final TrustPostingService postingService;
  private final TrustClientMatterRule clientMatterRule;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public TrustInvestmentService(
      TrustInvestmentRepository investmentRepository,
      TrustAccountRepository trustAccountRepository,
      CustomerRepository customerRepository,
      TrustLedgerQueryRepository ledgerQueries,
      TrustTransactionRepository transactionRepository,
      TrustPostingService postingService,
      TrustClientMatterRule clientMatterRule,
      VerticalModuleGuard moduleGuard,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.investmentRepository = investmentRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.customerRepository = customerRepository;
    this.ledgerQueries = ledgerQueries;
    this.transactionRepository = transactionRepository;
    this.postingService = postingService;
    this.clientMatterRule = clientMatterRule;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  // --- DTO Records ---

  /**
   * A placement from the funding account (the path's {@code accountId}) into {@code
   * investmentAccountId}, whose basis must equal {@code investmentBasis}. {@code clientId} and
   * {@code matterId} are required for section 86(4) and refused for section 86(3). No reference:
   * Kazi generates it.
   */
  public record PlaceInvestmentRequest(
      @NotNull UUID investmentAccountId,
      UUID clientId,
      UUID matterId,
      @NotBlank String institution,
      @NotBlank String accountNumber,
      @NotNull @Positive BigDecimal principal,
      @NotNull @PositiveOrZero BigDecimal interestRate,
      @NotNull LocalDate depositDate,
      LocalDate maturityDate,
      String notes,
      @NotNull InvestmentBasis investmentBasis) {}

  /** The whole investment returns on {@code withdrawalDate}; the amount is derived, not typed. */
  public record WithdrawInvestmentRequest(@NotNull LocalDate withdrawalDate) {}

  /**
   * An investment. {@code trustAccountId} is the funding account; {@code clientId}, {@code
   * clientName} and {@code matterId} are null for section 86(3); {@code balance} is what a
   * withdrawal would return now (zero until the placement is approved and after the withdrawal);
   * {@code awaitingApproval} is true while the placement row awaits approval (nothing has moved).
   */
  public record TrustInvestmentResponse(
      UUID id,
      UUID trustAccountId,
      UUID investmentAccountId,
      String investmentAccountName,
      UUID clientId,
      String clientName,
      UUID matterId,
      String institution,
      String accountNumber,
      BigDecimal principal,
      BigDecimal interestRate,
      LocalDate depositDate,
      LocalDate maturityDate,
      BigDecimal interestEarned,
      BigDecimal balance,
      String status,
      boolean awaitingApproval,
      LocalDate withdrawalDate,
      BigDecimal withdrawalAmount,
      UUID depositTransactionId,
      UUID withdrawalTransactionId,
      String notes,
      InvestmentBasis investmentBasis,
      Instant createdAt,
      Instant updatedAt) {}

  // --- Placement ---

  /**
   * Requests a placement from {@code fundingAccountId} (§16.3.7): the investment is saved ACTIVE
   * and its placement row ({@code INV_PLACE} / {@code FIRM_INV_PLACE}) awaits approval under the
   * funding account's mode; nothing moves until it is approved ({@link #onPlacementApproved}).
   */
  @Transactional
  public TrustInvestmentResponse placeInvestment(
      UUID fundingAccountId, PlaceInvestmentRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    TrustAmounts.requireValid(request.principal());
    // Never future-dated (CodeRabbit cycle 3): the placement row carries the deposit date, and both
    // bases' cover is read as at today, so a later-dated row would escape every cover check
    if (request.depositDate().isAfter(LocalDate.now(clock))) {
      throw new InvalidStateException(PLACE_TITLE, "The deposit date cannot be in the future.");
    }

    // Locked (648A review F-3/F-4): serialises the section 86(3) cashbook read with a placement's
    // approval (which re-reads it under this lock) and the placement with the account's closing.
    // Lock order: the funding account, then the investment account (a banking account is never an
    // investment account, so two placements cannot lock them in opposite orders).
    var funding =
        trustAccountRepository
            .findByIdForUpdate(fundingAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", fundingAccountId));
    if (funding.getAccountType() == TrustAccountType.INVESTMENT) {
      throw new InvalidStateException(
          PLACE_TITLE,
          "An investment is placed from a trust banking account, not from an investment account.");
    }
    if (funding.getStatus() != TrustAccountStatus.ACTIVE) {
      throw new InvalidStateException(
          PLACE_TITLE, "The trust account must be active to place an investment.");
    }
    // Locked: serialises with a basis change of the investment account (648C found.md F-2)
    var investmentAccount =
        trustAccountRepository
            .findByIdForUpdate(request.investmentAccountId())
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustAccount", request.investmentAccountId()));
    if (investmentAccount.getAccountType() != TrustAccountType.INVESTMENT) {
      throw new InvalidStateException(
          PLACE_TITLE, "The money must be placed in a trust investment account.");
    }
    if (investmentAccount.getStatus() != TrustAccountStatus.ACTIVE) {
      throw new InvalidStateException(
          PLACE_TITLE, "The investment account must be active to place an investment.");
    }
    var basis = request.investmentBasis();
    if (investmentAccount.getInvestmentBasis() != basis) {
      throw new InvalidStateException(
          PLACE_TITLE,
          "The investment account holds "
              + section(investmentAccount.getInvestmentBasis())
              + " investments; this placement is a "
              + section(basis)
              + " investment. Choose an investment account of the same basis.");
    }
    if (request.maturityDate() != null && request.maturityDate().isBefore(request.depositDate())) {
      throw new InvalidStateException(
          PLACE_TITLE, "The maturity date cannot be before the deposit date.");
    }

    Customer client = null;
    Project matter = null;
    if (basis == InvestmentBasis.CLIENT_INSTRUCTION) {
      if (request.clientId() == null) {
        throw new InvalidStateException(
            "Client required",
            "A section 86(4) investment names the client and the matter whose money is"
                + " invested.");
      }
      client =
          customerRepository
              .findById(request.clientId())
              .orElseThrow(
                  () -> new ResourceNotFoundException(EntityNoun.CLIENT, request.clientId()));
      matter = clientMatterRule.requireClientMatter(client, request.matterId());
      // The client's money on the matter must cover the placement — read under the client's lock
      // anchor (it is checked again at approval, TrustTransactionService.requireClientCovers)
      postingService.lockClient(fundingAccountId, client.getId());
      var available =
          ledgerQueries.matterCellBalance(fundingAccountId, client.getId(), matter.getId(), null);
      if (available.compareTo(request.principal()) < 0) {
        throw new InvalidStateException(
            "Insufficient trust balance",
            "Insufficient trust balance on matter "
                + matter.getName()
                + " for "
                + client.getName()
                + ". Available on matter: "
                + rand(available)
                + ", Requested: "
                + rand(request.principal()));
      }
    } else {
      if (request.clientId() != null || request.matterId() != null) {
        throw new InvalidStateException(
            PLACE_TITLE,
            "A section 86(3) investment is the firm's placement of surplus trust money and names"
                + " no client or matter.");
      }
      // Read under the funding account's lock; checked again at approval under the same lock
      // (onPlacementApproving — review F-3)
      requireCashbookCovers(fundingAccountId, request.principal());
    }

    // Insert order (§16.3.7): investment → reference → placement row with investment_id → link
    var investment =
        investmentRepository.saveAndFlush(
            new TrustInvestment(
                fundingAccountId,
                investmentAccount.getId(),
                client == null ? null : client.getId(),
                matter == null ? null : matter.getId(),
                request.institution(),
                request.accountNumber(),
                request.principal(),
                request.interestRate(),
                request.depositDate(),
                request.maturityDate(),
                request.notes(),
                basis));
    var reference = "INV-" + hex(investment);
    var memberId = RequestScopes.requireMemberId();
    var draft =
        new TrustTransaction(
            fundingAccountId,
            basis == InvestmentBasis.CLIENT_INSTRUCTION ? "INV_PLACE" : "FIRM_INV_PLACE",
            request.principal(),
            investment.getCustomerId(),
            investment.getProjectId(),
            null,
            reference,
            "Placed in "
                + section(basis)
                + " investment at "
                + request.institution()
                + " ("
                + investmentAccount.getAccountName()
                + ")",
            request.depositDate(),
            "AWAITING_APPROVAL",
            memberId);
    draft.setInvestmentId(investment.getId()); // chk_trust_txn_ledger_target
    var placement = postingService.recordAwaitingApproval(draft);
    investment.setDepositTransactionId(placement.getId());
    investment = investmentRepository.save(investment);

    var details = baseDetails(investment);
    details.put("institution", request.institution());
    details.put("principal", request.principal().toPlainString());
    details.put("interest_rate", request.interestRate().toPlainString());
    details.put("deposit_date", request.depositDate().toString());
    details.put("reference", reference);
    details.put("placement_transaction_id", placement.getId().toString());
    audit("trust_investment.placed", investment, details);

    eventPublisher.publishEvent(
        TrustTransactionApprovalEvent.awaitingApproval(
            placement.getId(),
            fundingAccountId,
            placement.getProjectId(),
            placement.getTransactionType(),
            placement.getAmount(),
            placement.getTransactionDate(),
            placement.getCustomerId(),
            memberId,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull()));

    return toResponse(investment);
  }

  /**
   * The placement row is about to post (final approval; 648A review F-3): a section 86(3)
   * placement's cover is the funding account's cashbook, which the request read but a pending
   * placement does not reserve — so it is read again here under the funding account's lock (the
   * lock {@link #placeInvestment} takes), which serialises section 86(3) approvals on the account
   * with each other and with a new placement request. The row is still AWAITING_APPROVAL, so the
   * cashbook excludes it. A section 86(4) placement's cover is its client's, checked by the caller
   * under the client's lock anchor. Lock order: the placement row (caller), then the funding
   * account.
   */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void onPlacementApproving(TrustTransaction placement) {
    moduleGuard.requireModule(MODULE_ID);
    if (!"FIRM_INV_PLACE".equals(placement.getTransactionType())) {
      return;
    }
    trustAccountRepository
        .findByIdForUpdate(placement.getTrustAccountId())
        .orElseThrow(
            () -> new ResourceNotFoundException("TrustAccount", placement.getTrustAccountId()));
    requireCashbookCovers(placement.getTrustAccountId(), placement.getAmount());
  }

  /**
   * The placement row has just posted on the funding account (final approval): the receive leg
   * posts on the investment account — {@code INV_RECEIVE} (the client's investment ledger) or
   * {@code FIRM_INV_RECEIVE} (inter-account) — for the same amount, date and reference, with its
   * pair's approvers. Lock order: the placement row (caller), then the investment row.
   */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void onPlacementApproved(TrustTransaction placement) {
    moduleGuard.requireModule(MODULE_ID);
    var investment = lockInvestmentOf(placement);
    var receive =
        new TrustTransaction(
            investment.getInvestmentAccountId(),
            "INV_PLACE".equals(placement.getTransactionType()) ? "INV_RECEIVE" : "FIRM_INV_RECEIVE",
            placement.getAmount(),
            placement.getCustomerId(),
            placement.getProjectId(),
            null,
            placement.getReference(),
            "Investment capital received from the trust account ("
                + investment.getInstitution()
                + ")",
            placement.getTransactionDate(),
            "APPROVED",
            placement.getRecordedBy());
    receive.setInvestmentId(investment.getId());
    postingService.recordWithPair(receive, placement);
  }

  /**
   * The placement row was rejected: nothing was invested, so the investment is {@code CANCELLED}
   * (terminal) in the same transaction — otherwise it would stay on the register, in the maturing
   * list and in the Section 35 data pack with nothing behind it (G89-F87).
   */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void onPlacementRejected(TrustTransaction placement, String reason) {
    moduleGuard.requireModule(MODULE_ID);
    var investment = lockInvestmentOf(placement);
    if (!HELD.contains(investment.getStatus())) {
      return;
    }
    investment.setStatus("CANCELLED");
    investment = investmentRepository.save(investment);
    var details = baseDetails(investment);
    details.put("placement_transaction_id", placement.getId().toString());
    details.put("reason", reason);
    audit("trust_investment.cancelled", investment, details);
  }

  // --- Withdrawal ---

  /**
   * Withdraws the whole investment on {@code withdrawalDate} (§16.3.7): {@code INV_WITHDRAW} /
   * {@code FIRM_INV_WITHDRAW} on the investment account and {@code INV_RETURN} / {@code
   * FIRM_INV_RETURN} on the funding account (section 86(4): the client and the placement's matter),
   * both RECORDED with reference {@code INV-WD-…}. The amount is the investment's balance — capital
   * plus net interest for section 86(4); the principal for section 86(3). No approval: the money
   * returns to the trust banking account.
   */
  @Transactional
  public TrustInvestmentResponse withdrawInvestment(
      UUID investmentId, WithdrawInvestmentRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    // PR #1757: lock the placement row, then the investment row, and check under both locks. The
    // placement id comes from a scalar query so no stale investment is loaded before its lock.
    var placementId =
        investmentRepository
            .findPlacementTransactionIdById(investmentId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustInvestment", investmentId));
    var placement =
        transactionRepository
            .findByIdForUpdate(placementId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustTransaction", placementId));
    var investment =
        investmentRepository
            .findByIdForUpdate(investmentId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustInvestment", investmentId));

    if (!HELD.contains(investment.getStatus())) {
      throw new InvalidStateException(
          WITHDRAW_TITLE, "Only an active investment can be withdrawn.");
    }
    if (!"APPROVED".equals(placement.getStatus()) || placement.getPostedAt() == null) {
      throw new InvalidStateException(
          WITHDRAW_TITLE,
          "The investment's placement must be approved before the investment can be withdrawn"
              + " (the placement is "
              + placement.getStatus().toLowerCase(Locale.ROOT).replace('_', ' ')
              + ").");
    }
    var date = request.withdrawalDate();
    if (date.isBefore(investment.getDepositDate())) {
      throw new InvalidStateException(
          WITHDRAW_TITLE, "The withdrawal date cannot be before the investment's deposit date.");
    }
    if (date.isAfter(LocalDate.now(clock))) {
      throw new InvalidStateException(
          WITHDRAW_TITLE, "The withdrawal date cannot be in the future.");
    }
    // 648A review F-4: both legs post, so both accounts must be open (closing either is refused
    // while the investment is held; this covers an account closed before that guard)
    for (var accountId :
        List.of(investment.getTrustAccountId(), investment.getInvestmentAccountId())) {
      var account =
          trustAccountRepository
              .findById(accountId)
              .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));
      if (account.getStatus() != TrustAccountStatus.ACTIVE) {
        throw new InvalidStateException(
            WITHDRAW_TITLE,
            "The trust account the investment was placed from and the investment account holding"
                + " it must both be active to withdraw it.");
      }
    }

    boolean forClient = investment.getInvestmentBasis() == InvestmentBasis.CLIENT_INSTRUCTION;
    if (forClient) {
      postingService.lockClient(investment.getTrustAccountId(), investment.getCustomerId());
    }
    var amount =
        forClient
            ? ledgerQueries.investmentBalance(
                investment.getId(), investment.getInvestmentAccountId())
            : investment.getPrincipal();
    if (amount.signum() <= 0) {
      throw new InvalidStateException(WITHDRAW_TITLE, "The investment holds nothing to withdraw.");
    }

    var reference = "INV-WD-" + hex(investment);
    var memberId = RequestScopes.requireMemberId();
    var out =
        new TrustTransaction(
            investment.getInvestmentAccountId(),
            forClient ? "INV_WITHDRAW" : "FIRM_INV_WITHDRAW",
            amount,
            investment.getCustomerId(),
            placement.getProjectId(),
            null,
            reference,
            "Investment withdrawn to the trust account (" + investment.getInstitution() + ")",
            date,
            "RECORDED",
            memberId);
    out.setInvestmentId(investment.getId());
    out = postingService.record(out);
    var back =
        new TrustTransaction(
            investment.getTrustAccountId(),
            forClient ? "INV_RETURN" : "FIRM_INV_RETURN",
            amount,
            investment.getCustomerId(),
            placement.getProjectId(),
            null,
            reference,
            "Investment returned to trust from " + investment.getInstitution(),
            date,
            "RECORDED",
            memberId);
    back.setInvestmentId(investment.getId());
    back = postingService.recordWithPair(back, out);

    investment.setStatus("WITHDRAWN");
    investment.setWithdrawalDate(date);
    investment.setWithdrawalAmount(amount);
    investment.setWithdrawalTransactionId(back.getId());
    investment = investmentRepository.save(investment);

    var details = baseDetails(investment);
    details.put("withdrawal_amount", amount.toPlainString());
    details.put("principal", investment.getPrincipal().toPlainString());
    details.put("withdrawal_date", date.toString());
    details.put("reference", reference);
    details.put("withdrawal_transaction_id", back.getId().toString());
    audit("trust_investment.withdrawn", investment, details);

    // The CLIENT-ledger return re-projects the client's matter in the portal (648A: the
    // investment-ledger leg never reaches it)
    if (forClient) {
      eventPublisher.publishEvent(
          TrustTransactionRecordedEvent.recorded(
              back.getId(),
              back.getTrustAccountId(),
              back.getProjectId(),
              back.getTransactionType(),
              amount,
              date,
              back.getCustomerId(),
              memberId,
              RequestScopes.getTenantIdOrNull(),
              RequestScopes.getOrgIdOrNull()));
    }

    return toResponse(investment);
  }

  // --- Interest (until 648B) ---

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

    if (!ACTIVE.equals(investment.getStatus())) {
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

  // --- Reads ---

  /** Active investments funded from, or held in, the account, maturing within {@code daysAhead}. */
  @Transactional(readOnly = true)
  public List<TrustInvestmentResponse> getMaturing(UUID accountId, int daysAhead) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    var today = LocalDate.now(clock);
    var investments =
        investmentRepository.findMaturingByFundingOrInvestmentAccount(
            accountId, ACTIVE, today, today.plusDays(daysAhead));
    return toResponses(investments);
  }

  /** The investments funded from, or held in, the account (either may be given), by basis. */
  @Transactional(readOnly = true)
  public Page<TrustInvestmentResponse> listInvestments(
      UUID accountId, InvestmentBasis investmentBasis, Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    var page =
        investmentRepository.findByFundingOrInvestmentAccount(accountId, investmentBasis, pageable);
    Map<UUID, TrustInvestmentResponse> byId =
        toResponses(page.getContent()).stream()
            .collect(Collectors.toMap(TrustInvestmentResponse::id, Function.identity()));
    return page.map(inv -> byId.get(inv.getId()));
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

  private TrustInvestment lockInvestmentOf(TrustTransaction placement) {
    var investmentId = placement.getInvestmentId();
    if (investmentId == null) {
      throw new IllegalStateException(
          "Placement " + placement.getId() + " names no investment (chk_trust_txn_ledger_target)");
    }
    return investmentRepository
        .findByIdForUpdate(investmentId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustInvestment", investmentId));
  }

  /**
   * A section 86(3) placement's cover: the funding account's cashbook as at today must cover {@code
   * principal}. The caller holds the funding account's lock. The one refusal for the request and
   * the approval.
   */
  private void requireCashbookCovers(UUID fundingAccountId, BigDecimal principal) {
    var cashbook = ledgerQueries.cashbook(fundingAccountId, LocalDate.now(clock));
    if (cashbook.compareTo(principal) < 0) {
      throw new InvalidStateException(
          "Insufficient trust balance",
          "The trust account's cashbook does not cover this section 86(3) placement. Available: "
              + rand(cashbook)
              + ", Requested: "
              + rand(principal));
    }
  }

  /** The first 8 hex digits of the investment's id, upper case (both legs of a pair share it). */
  private static String hex(TrustInvestment investment) {
    return investment.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }

  /** The Act's term for a basis. */
  private static String section(InvestmentBasis basis) {
    return basis == InvestmentBasis.CLIENT_INSTRUCTION ? "section 86(4)" : "section 86(3)";
  }

  /** A rand amount for a refusal message, in the house en-ZA format ("R10 055,21"; G89-F40). */
  private static String rand(BigDecimal amount) {
    return VariableFormatter.formatCurrencyText(amount, TRUST_LOCALE);
  }

  /** The account ids and basis every investment audit event carries (648A AC-11). */
  private static LinkedHashMap<String, Object> baseDetails(TrustInvestment investment) {
    var details = new LinkedHashMap<String, Object>();
    details.put("trust_account_id", investment.getTrustAccountId().toString());
    details.put("funding_account_id", investment.getTrustAccountId().toString());
    details.put("investment_account_id", investment.getInvestmentAccountId().toString());
    details.put("investment_basis", investment.getInvestmentBasis().name());
    return details;
  }

  /** Section 86(4) events are matter-scoped (client and matter); section 86(3) names neither. */
  private void audit(String eventType, TrustInvestment investment, Map<String, Object> details) {
    var scoped =
        investment.getCustomerId() == null
            ? details
            : AuditDetails.matterScoped(
                investment.getProjectId(), investment.getCustomerId(), details);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType(eventType)
            .entityType("trust_investment")
            .entityId(investment.getId())
            .details(scoped)
            .build());
  }

  private TrustInvestmentResponse toResponse(TrustInvestment investment) {
    return toResponses(List.of(investment)).getFirst();
  }

  /**
   * Batch-loaded names, balances and placement statuses for a page: four queries at most, never one
   * per row. A section 86(3) investment has no client ({@code clientName} null); a deleted client
   * reads "(deleted customer)".
   */
  private List<TrustInvestmentResponse> toResponses(List<TrustInvestment> investments) {
    if (investments.isEmpty()) {
      return List.of();
    }
    var customerIds =
        ids(investments, TrustInvestment::getCustomerId).stream().filter(Objects::nonNull).toList();
    Map<UUID, String> clientNames =
        customerIds.isEmpty()
            ? Map.of()
            : customerRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));
    Map<UUID, String> accountNames =
        trustAccountRepository
            .findAllById(ids(investments, TrustInvestment::getInvestmentAccountId))
            .stream()
            .collect(
                Collectors.toMap(TrustAccount::getId, TrustAccount::getAccountName, (a, b) -> a));
    // The placement rows' statuses: awaitingApproval is the server's answer, not a FE inference
    var placementIds =
        investments.stream()
            .map(TrustInvestment::getDepositTransactionId)
            .filter(Objects::nonNull)
            .toList();
    Set<UUID> awaiting =
        placementIds.isEmpty()
            ? Set.of()
            : transactionRepository.findAllById(placementIds).stream()
                .filter(t -> "AWAITING_APPROVAL".equals(t.getStatus()))
                .map(TrustTransaction::getId)
                .collect(Collectors.toSet());
    Map<UUID, BigDecimal> balances =
        ledgerQueries.investmentBalances(ids(investments, TrustInvestment::getId)).stream()
            .collect(
                Collectors.toMap(
                    InvestmentBalanceRow::getInvestmentId,
                    InvestmentBalanceRow::getBalance,
                    (a, b) -> a));
    return investments.stream()
        .map(
            inv ->
                new TrustInvestmentResponse(
                    inv.getId(),
                    inv.getTrustAccountId(),
                    inv.getInvestmentAccountId(),
                    accountNames.get(inv.getInvestmentAccountId()),
                    inv.getCustomerId(),
                    inv.getCustomerId() == null
                        ? null
                        : clientNames.getOrDefault(inv.getCustomerId(), "(deleted customer)"),
                    inv.getProjectId(),
                    inv.getInstitution(),
                    inv.getAccountNumber(),
                    inv.getPrincipal(),
                    inv.getInterestRate(),
                    inv.getDepositDate(),
                    inv.getMaturityDate(),
                    inv.getInterestEarned(),
                    balances.getOrDefault(inv.getId(), BigDecimal.ZERO),
                    inv.getStatus(),
                    inv.getDepositTransactionId() != null
                        && awaiting.contains(inv.getDepositTransactionId()),
                    inv.getWithdrawalDate(),
                    inv.getWithdrawalAmount(),
                    inv.getDepositTransactionId(),
                    inv.getWithdrawalTransactionId(),
                    inv.getNotes(),
                    inv.getInvestmentBasis(),
                    inv.getCreatedAt(),
                    inv.getUpdatedAt()))
        .toList();
  }

  private static Collection<UUID> ids(
      List<TrustInvestment> investments, Function<TrustInvestment, UUID> id) {
    var ids = new HashSet<UUID>();
    for (var investment : investments) {
      ids.add(id.apply(investment));
    }
    return ids;
  }
}
