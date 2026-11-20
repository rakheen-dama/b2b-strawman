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
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountingConstants;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionApprovalEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionRecordedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentInterestRepository.InterestTotals;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatementLine;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatementLineRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLineClassification;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLines;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.InvestmentBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.InvestmentPlacementHooks;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustAmounts;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustClientMatterRule;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * TrustTransactionService}, so no bean cycle; its amount rule is {@link TrustAmounts}). Lock order
 * (the global order in {@link TrustPostingService}, 650A F-10/F-11): a placement request takes the
 * client's lock anchor (section 86(4)), then the funding account, then the investment account; its
 * approval, the placement row, then the funding account (section 86(3)) or the client's lock anchor
 * (section 86(4)), then the investment row, posting on the funding account before the investment
 * account; a withdrawal, the placement row, then the investment row, then the client's lock anchor
 * (section 86(4)), then the funding account, then the investment account — both accounts locked
 * before either leg posts.
 *
 * <p>648B — section 86(4) interest entries ({@link TrustInvestmentInterest}, §16.3.8): {@link
 * #recordInterest} from a bank advice, or from a statement line (here and through {@code
 * StatementLinePostingService}, which delegates its {@code INVESTMENT_INTEREST} classification to
 * {@link #recordInterestFromLine} — a one-way dependency; the line rules are the static {@link
 * StatementLines}). Lock orders: a bank-advice entry, the investment row; from a line, the line,
 * then the investment row, then the statement; {@link #reverseInterest}, the investment row, then
 * the entry, then its rows (no client anchor: a debit of credited interest cannot take the
 * investment ledger below its principal); an {@code INVESTMENT_INTEREST} line's unmatch, the line,
 * then the investment row, then the entry ({@link #lockEntryForUnmatch}), then the statement, then
 * its rows and the client's anchor on the investment account. The contra drafts come from {@link
 * TrustPostingService#contraDraft} (no {@code TrustTransactionService} dependency).
 */
@Service
public class TrustInvestmentService implements InvestmentPlacementHooks {

  private static final String MODULE_ID = "trust_accounting";
  private static final String PLACE_TITLE = "Cannot place investment";
  private static final String WITHDRAW_TITLE = "Cannot withdraw investment";
  private static final String INTEREST_TITLE = "Cannot record investment interest";
  private static final String REVERSE_INTEREST_TITLE = "Cannot reverse investment interest";
  private static final String UNMATCH_TITLE = "Cannot unmatch statement line";
  private static final String INTEREST_REVERSAL_REASON = "Section 86(4) interest entry reversed";
  private static final String WITHDRAWN_WITH_INTEREST =
      "The investment has been withdrawn with this interest; its interest entries can no longer be"
          + " reversed.";
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
  private final TrustInvestmentInterestRepository interestRepository;
  private final BankStatementLineRepository lineRepository;
  private final BankStatementRepository statementRepository;
  private final TrustCreditorAccountRepository creditorAccountRepository;

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
      Clock clock,
      TrustInvestmentInterestRepository interestRepository,
      BankStatementLineRepository lineRepository,
      BankStatementRepository statementRepository,
      TrustCreditorAccountRepository creditorAccountRepository) {
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
    this.interestRepository = interestRepository;
    this.lineRepository = lineRepository;
    this.statementRepository = statementRepository;
    this.creditorAccountRepository = creditorAccountRepository;
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
   * A section 86(4) interest entry (648B, §16.3.8): {@code grossAmount} and {@code fundShareAmount}
   * are required only as noted — {@code fundShareAmount} null means the 5% is computed (half-up to
   * the cent); a typed share must be within R0,01 of it (OL-5). {@code bankStatementLineId} names
   * the investment account's statement line the credit is recorded from (its date and amount must
   * agree); null for a bank advice, which is {@code NET} only (OQ-4).
   */
  public record RecordInvestmentInterestRequest(
      @NotNull LocalDate entryDate,
      @NotNull BigDecimal grossAmount,
      BigDecimal fundShareAmount,
      @NotNull InterestPresentation presentation,
      UUID bankStatementLineId) {}

  /** One interest entry: gross, the Fund's 5% and the client's 95% (A2, A3). */
  public record InvestmentInterestResponse(
      UUID id,
      UUID investmentId,
      LocalDate entryDate,
      BigDecimal grossAmount,
      BigDecimal fundShareAmount,
      BigDecimal netAmount,
      String presentation,
      String status,
      UUID netTransactionId,
      UUID fundShareTransactionId,
      UUID bankStatementLineId,
      String clientTaxReference) {

    static InvestmentInterestResponse of(TrustInvestmentInterest entry) {
      return new InvestmentInterestResponse(
          entry.getId(),
          entry.getInvestmentId(),
          entry.getEntryDate(),
          entry.getGrossAmount(),
          entry.getFundShareAmount(),
          entry.getNetAmount(),
          entry.getPresentation().name(),
          entry.getStatus(),
          entry.getNetTransactionId(),
          entry.getFundShareTransactionId(),
          entry.getBankStatementLineId(),
          entry.getClientTaxReference());
    }
  }

  /**
   * What {@link #recordInterestFromLine} recorded: the line (now {@code RECORDED}), the rows it
   * created ({@code INV_INTEREST} first) and the entry's id — mapped by {@code
   * StatementLinePostingService} into its record response.
   */
  public record RecordedFromLine(
      BankStatementLine line, List<TrustTransaction> rows, UUID investmentInterestId) {}

  /**
   * An investment. {@code trustAccountId} is the funding account; {@code clientId}, {@code
   * clientName} and {@code matterId} are null for section 86(3); {@code balance} is what a
   * withdrawal would return now (zero until the placement is approved and after the withdrawal);
   * {@code awaitingApproval} is true while the placement row awaits approval (nothing has moved).
   * {@code grossInterest}, {@code fundShare} and {@code netInterest} (648B, replacing the typed
   * {@code interestEarned}) sum the investment's POSTED interest entries — zero for section 86(3),
   * whose interest is the Fund's.
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
      BigDecimal grossInterest,
      BigDecimal fundShare,
      BigDecimal netInterest,
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

    // Section 86(4): the client's lock anchor first (650A F-11) — the global lock order
    // (TrustPostingService) puts a client's anchor before the account rows, and a posting for the
    // client takes the anchor and then the account's posting lock. Taken only when the account and
    // the client exist; otherwise the checks below refuse the request.
    if (request.investmentBasis() == InvestmentBasis.CLIENT_INSTRUCTION
        && request.clientId() != null
        && trustAccountRepository.existsById(fundingAccountId)
        && customerRepository.existsById(request.clientId())) {
      postingService.lockClient(fundingAccountId, request.clientId());
    }

    // Locked (648A review F-3/F-4): serialises the section 86(3) cashbook read with a placement's
    // approval (which re-reads it under this lock) and the placement with the account's closing.
    // Lock order: the funding account, then the investment account (the global order puts the
    // trust banking account before the investment account; a banking account is never an
    // investment account).
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
      // anchor, taken above (it is checked again at approval,
      // TrustTransactionService.requireClientCovers)
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
    // 648B OQ-1 (a): the balance withdrawn is undated, so no posted interest entry may be dated
    // after the withdrawal — read under the investment's lock (recordInterest takes the same lock
    // and refuses once the investment is withdrawn)
    var lastEntry = interestRepository.findLatestPostedEntryDate(investment.getId());
    if (lastEntry.isPresent() && date.isBefore(lastEntry.get())) {
      throw new InvalidStateException(
          WITHDRAW_TITLE,
          "The withdrawal date cannot be before the investment's last interest entry ("
              + lastEntry.get()
              + ").");
    }
    boolean forClient = investment.getInvestmentBasis() == InvestmentBasis.CLIENT_INSTRUCTION;
    if (forClient) {
      postingService.lockClient(investment.getTrustAccountId(), investment.getCustomerId());
    }
    // 648A review F-4: both legs post, so both accounts must be open (closing either is refused
    // while the investment is held; this covers an account closed before that guard). 650A F-10:
    // read under both accounts' posting locks, taken here in the global order — the client's
    // anchor (above), the funding (banking) account, then the investment account — before the
    // withdrawal leg posts on the investment account; posting it first would take the investment
    // account before the funding account, the reverse of a placement's order.
    for (var accountId :
        List.of(investment.getTrustAccountId(), investment.getInvestmentAccountId())) {
      var account =
          trustAccountRepository
              .findByIdForShare(accountId)
              .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));
      if (account.getStatus() != TrustAccountStatus.ACTIVE) {
        throw new InvalidStateException(
            WITHDRAW_TITLE,
            "The trust account the investment was placed from and the investment account holding"
                + " it must both be active to withdraw it.");
      }
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

  // --- Interest (648B, §16.3.8) ---

  /**
   * Records a section 86(4) interest entry on the investment (§16.3.8): from a bank advice ({@code
   * NET} only — OQ-4), or from the investment account's statement line {@code request} names (its
   * date and amount must agree). The investment must be {@code ACTIVE}, {@code CLIENT_INSTRUCTION}
   * and its placement approved; {@code FIRM_DISCRETION} is refused (its interest is the Fund's).
   * {@code NET} posts {@code INV_INTEREST} for the client's 95%; {@code GROSS} also posts {@code
   * FUND_INV_SHARE} for the 5% on the account's Fund ledger. No approval: the bank credited it.
   */
  @Transactional
  public InvestmentInterestResponse recordInterest(
      UUID investmentId, RecordInvestmentInterestRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    BankStatementLine line = null;
    BankStatement statement = null;
    if (request.bankStatementLineId() != null) {
      line = lockUnmatchedLine(request.bankStatementLineId());
    }
    var investment = lockInvestment(investmentId);
    if (line != null) {
      statement = lockStatement(line);
    }
    var recorded =
        record(
            investment,
            line,
            statement,
            request.entryDate(),
            request.grossAmount(),
            request.fundShareAmount(),
            request.presentation(),
            null);
    return InvestmentInterestResponse.of(recorded);
  }

  /**
   * Records the {@code UNMATCHED} statement line {@code lineId} as section 86(4) interest on {@code
   * investmentId} — {@code StatementLinePostingService}'s {@code INVESTMENT_INTEREST}
   * classification (§16.3.5): the entry is dated the line's date; for {@code GROSS} the gross is
   * the line ({@code grossAmount} may be omitted), for {@code NET} the gross is required and its
   * net must be the line. Lock order: the line, the investment row, the statement.
   */
  @Transactional
  public RecordedFromLine recordInterestFromLine(
      UUID lineId,
      UUID investmentId,
      BigDecimal grossAmount,
      BigDecimal fundShareAmount,
      InterestPresentation presentation,
      String notes) {
    moduleGuard.requireModule(MODULE_ID);
    var line = lockUnmatchedLine(lineId);
    var investment = lockInvestment(investmentId);
    var statement = lockStatement(line);
    var recorded =
        record(
            investment,
            line,
            statement,
            line.getTransactionDate(),
            grossAmount,
            fundShareAmount,
            presentation,
            notes);
    var rows = new ArrayList<TrustTransaction>();
    rows.add(transactionRepository.findById(recorded.getNetTransactionId()).orElseThrow());
    if (recorded.getFundShareTransactionId() != null) {
      rows.add(transactionRepository.findById(recorded.getFundShareTransactionId()).orElseThrow());
    }
    return new RecordedFromLine(line, rows, recorded.getId());
  }

  /** The investment's interest entries, oldest first (bare list). */
  @Transactional(readOnly = true)
  public List<InvestmentInterestResponse> listInterest(UUID investmentId) {
    moduleGuard.requireModule(MODULE_ID);
    var investment =
        investmentRepository
            .findById(investmentId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustInvestment", investmentId));
    trustAccountRepository
        .findById(investment.getTrustAccountId())
        .orElseThrow(
            () -> new ResourceNotFoundException("TrustAccount", investment.getTrustAccountId()));
    return interestRepository
        .findByInvestmentIdOrderByEntryDateAscCreatedAtAsc(investmentId)
        .stream()
        .map(InvestmentInterestResponse::of)
        .toList();
  }

  /**
   * Reverses a bank-advice interest entry (§16.3.8): each of its rows by a contra dated the entry's
   * date (so the pair leaves no trace in any as-at balance), and the entry becomes {@code
   * REVERSED}. Refused for an entry recorded from a statement line (unmatch the line — OQ-3) or
   * whose row has since been matched to one, and once the investment is withdrawn. Lock order: the
   * investment row, the entry, its rows; no client anchor (OQ-17).
   */
  @Transactional
  public InvestmentInterestResponse reverseInterest(UUID interestId) {
    moduleGuard.requireModule(MODULE_ID);
    var investmentId =
        interestRepository
            .findInvestmentIdById(interestId)
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustInvestmentInterest", interestId));
    var investment = lockInvestment(investmentId);
    var entry =
        interestRepository
            .findByIdForUpdate(interestId)
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustInvestmentInterest", interestId));
    if (!entry.isPosted()) {
      throw new InvalidStateException(
          REVERSE_INTEREST_TITLE, "This interest entry has already been reversed.");
    }
    if (entry.getBankStatementLineId() != null) {
      throw new InvalidStateException(
          REVERSE_INTEREST_TITLE,
          "This interest was recorded from a bank statement line. Unmatch the line to reverse it.");
    }
    if (!HELD.contains(investment.getStatus())) {
      throw new InvalidStateException(REVERSE_INTEREST_TITLE, WITHDRAWN_WITH_INTEREST);
    }
    var account =
        trustAccountRepository
            .findById(investment.getInvestmentAccountId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "TrustAccount", investment.getInvestmentAccountId()));
    if (account.getStatus() == TrustAccountStatus.CLOSED) {
      throw new InvalidStateException(REVERSE_INTEREST_TITLE, "Trust account is closed");
    }

    // Lock and check every row before the first contra, so a refusal leaves nothing half-reversed
    var originals = new ArrayList<TrustTransaction>();
    for (var rowId : entryRowIds(entry)) {
      var row =
          transactionRepository
              .findByIdForUpdate(rowId)
              .orElseThrow(() -> new ResourceNotFoundException("TrustTransaction", rowId));
      if (row.getBankStatementLineId() != null) {
        throw new InvalidStateException(
            REVERSE_INTEREST_TITLE,
            "This interest is matched to a bank statement line. Unmatch the line before reversing"
                + " it.");
      }
      if (!"RECORDED".equals(row.getStatus()) || row.getReversedById() != null) {
        throw new InvalidStateException(
            REVERSE_INTEREST_TITLE, "This interest entry has already been reversed.");
      }
      originals.add(row);
    }

    var memberId = RequestScopes.requireMemberId();
    var contras = new ArrayList<TrustTransaction>();
    for (var original : originals) {
      var contra =
          postingService.postReversal(
              TrustPostingService.contraDraft(
                  original, INTEREST_REVERSAL_REASON, entry.getEntryDate(), memberId),
              original);
      contras.add(contra);
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("trust_transaction.reversed")
              .entityType("trust_transaction")
              .entityId(contra.getId())
              .details(
                  AuditDetails.matterScoped(
                      contra.getProjectId(),
                      original.getCustomerId(),
                      Map.of(
                          "original_transaction_id", original.getId().toString(),
                          "original_type", original.getTransactionType(),
                          "amount", original.getAmount().toString(),
                          "reversal_status", contra.getStatus(),
                          "reason", INTEREST_REVERSAL_REASON,
                          "reversal_date", entry.getEntryDate().toString())))
              .build());
      if (contra.getCustomerId() != null) {
        publishRecorded(contra, "REVERSAL", memberId);
      }
    }
    entry.markReversed();
    entry = interestRepository.save(entry);
    auditInterestReversed(investment, entry, contras);
    return InvestmentInterestResponse.of(entry);
  }

  /**
   * The unmatch of an {@code INVESTMENT_INTEREST} line, before its rows are reversed ({@code
   * StatementLinePostingService.unmatch}, the caller holding the line's lock): locks the investment
   * row, then the line's POSTED entry, and refuses once the investment is withdrawn — its interest
   * left with it, so a contra would take the investment ledger below zero.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public TrustInvestmentInterest lockEntryForUnmatch(UUID bankStatementLineId) {
    moduleGuard.requireModule(MODULE_ID);
    var interestId =
        interestRepository
            .findPostedIdByBankStatementLineId(bankStatementLineId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Statement line "
                            + bankStatementLineId
                            + " is recorded as section 86(4) interest but has no posted entry"));
    var investmentId = interestRepository.findInvestmentIdById(interestId).orElseThrow();
    var investment = lockInvestment(investmentId);
    var entry = interestRepository.findByIdForUpdate(interestId).orElseThrow();
    if (!entry.isPosted()) {
      throw new IllegalStateException("Interest entry " + interestId + " is no longer posted");
    }
    if (!HELD.contains(investment.getStatus())) {
      throw new InvalidStateException(UNMATCH_TITLE, WITHDRAWN_WITH_INTEREST);
    }
    return entry;
  }

  /** The unmatch reversed the entry's rows ({@code contras}): the entry becomes REVERSED. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void markReversedByUnmatch(TrustInvestmentInterest entry, List<TrustTransaction> contras) {
    var investment =
        investmentRepository
            .findById(entry.getInvestmentId())
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustInvestment", entry.getInvestmentId()));
    entry.markReversed();
    var saved = interestRepository.save(entry);
    auditInterestReversed(investment, saved, contras);
  }

  /**
   * The one recording path. {@code line}/{@code statement} are locked (or both null for a bank
   * advice); {@code gross} may be null only for {@code GROSS} from a line (it is then the line).
   * Every check runs before the first write, so a refusal moves nothing.
   */
  private TrustInvestmentInterest record(
      TrustInvestment investment,
      BankStatementLine line,
      BankStatement statement,
      LocalDate entryDate,
      BigDecimal grossAmount,
      BigDecimal fundShareAmount,
      InterestPresentation presentation,
      String notes) {
    // The investment (§16.3.8, OQ-7)
    if (investment.getInvestmentBasis() != InvestmentBasis.CLIENT_INSTRUCTION) {
      throw new InvalidStateException(
          INTEREST_TITLE,
          "Interest on a section 86(3) investment belongs to the Legal Practitioners' Fidelity Fund"
              + " — record it from the investment account's statement as interest received");
    }
    if (!ACTIVE.equals(investment.getStatus())) {
      throw new InvalidStateException(
          INTEREST_TITLE, "Interest is recorded on an active section 86(4) investment only.");
    }
    var placement =
        transactionRepository
            .findById(investment.getDepositTransactionId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "TrustTransaction", investment.getDepositTransactionId()));
    if (!"APPROVED".equals(placement.getStatus()) || placement.getPostedAt() == null) {
      throw new InvalidStateException(
          INTEREST_TITLE,
          "The investment's placement must be approved before interest is recorded (the placement"
              + " is "
              + placement.getStatus().toLowerCase(Locale.ROOT).replace('_', ' ')
              + ").");
    }
    var accountId = investment.getInvestmentAccountId();

    // The line (§16.3.5 rules 1–3; OQ-6)
    var lineAmount = line == null ? null : line.getAmount().abs();
    if (line != null) {
      if (!accountId.equals(statement.getTrustAccountId())) {
        throw new InvalidStateException(
            StatementLines.RECORD_TITLE,
            "This statement line is not on the investment account that holds the investment.");
      }
      StatementLines.requireSignAgrees(
          StatementLineClassification.INVESTMENT_INTEREST, line.getAmount());
      if (!line.getTransactionDate().equals(entryDate)) {
        throw new InvalidStateException(
            StatementLines.RECORD_TITLE,
            "The entry date is the statement line's date (" + line.getTransactionDate() + ").");
      }
    }
    var account =
        trustAccountRepository
            .findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));
    if (account.getStatus() != TrustAccountStatus.ACTIVE) {
      throw new InvalidStateException(
          INTEREST_TITLE, "The investment account must be active to record interest on it.");
    }
    if (entryDate.isBefore(investment.getDepositDate())) {
      throw new InvalidStateException(
          INTEREST_TITLE, "The entry date cannot be before the investment's deposit date.");
    }
    if (entryDate.isAfter(LocalDate.now(clock))) {
      throw new InvalidStateException(INTEREST_TITLE, "The entry date cannot be in the future.");
    }

    // The amounts: 5% half-up, a typed share within the tolerance (OL-5, OQ-7, OQ-18)
    var gross = grossAmount;
    if (gross == null && line != null && presentation == InterestPresentation.GROSS) {
      gross = lineAmount;
    }
    if (gross == null) {
      throw new InvalidStateException(
          INTEREST_TITLE,
          "A net interest credit needs the gross interest the bank advised (the statement line is"
              + " the client's 95%).");
    }
    TrustAmounts.requireValid(gross);
    gross = gross.setScale(2, RoundingMode.UNNECESSARY);
    if (presentation == InterestPresentation.GROSS && line == null) {
      throw new InvalidStateException(
          INTEREST_TITLE,
          "A gross interest credit is recorded from the investment account's statement line (the"
              + " Fund's 5% leaves in a separate debit).");
    }
    var computed =
        gross
            .multiply(TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT)
            .setScale(2, RoundingMode.HALF_UP);
    var fund = computed;
    if (fundShareAmount != null) {
      if (fundShareAmount.signum() < 0 || fundShareAmount.stripTrailingZeros().scale() > 2) {
        throw new InvalidStateException(
            INTEREST_TITLE, "The Fund's share must be zero or more, in rand and cents.");
      }
      if (fundShareAmount
              .subtract(computed)
              .abs()
              .compareTo(TrustAccountingConstants.STATUTORY_LPFF_SHARE_TOLERANCE)
          > 0) {
        throw new InvalidStateException(
            INTEREST_TITLE,
            "The Fund's share must be within R0,01 of 5% of the gross interest ("
                + rand(computed)
                + ").");
      }
      fund = fundShareAmount.setScale(2, RoundingMode.UNNECESSARY);
    }
    var net = gross.subtract(fund);
    if (net.signum() <= 0) {
      throw new InvalidStateException(
          INTEREST_TITLE, "The client's share of the interest must be more than R0,00.");
    }
    if (line != null) {
      if (presentation == InterestPresentation.GROSS && gross.compareTo(lineAmount) != 0) {
        throw new InvalidStateException(
            StatementLines.RECORD_TITLE,
            "The statement line is "
                + rand(lineAmount)
                + "; a gross presentation records the line's whole amount as gross.");
      }
      if (presentation == InterestPresentation.NET && net.compareTo(lineAmount) != 0) {
        throw new InvalidStateException(
            StatementLines.RECORD_TITLE,
            "The statement line is "
                + rand(lineAmount)
                + "; gross "
                + rand(gross)
                + " less the Fund's 5% ("
                + rand(fund)
                + ") is "
                + rand(net)
                + ".");
      }
    }
    if (presentation == InterestPresentation.GROSS && fund.signum() == 0) {
      throw new InvalidStateException(
          INTEREST_TITLE,
          "A gross interest credit this small has no Fund share to record separately; record it as"
              + " net of the Fund's 5%.");
    }

    // The rows (§16.3.5 rule 3 from a line; Kazi's reference for a bank advice — INV-INT-{hex}-{n},
    // n the entry's ordinal on the investment, safe because every caller holds the investment's
    // lock)
    var memberId = RequestScopes.requireMemberId();
    var reference =
        line != null
            ? StatementLines.referenceFor(line)
            : "INV-INT-"
                + hex(investment)
                + "-"
                + (interestRepository.countByInvestmentId(investment.getId()) + 1);
    var description =
        line != null
            ? line.getDescription()
            : "Section 86(4) investment interest (" + investment.getInstitution() + ")";
    var netDraft =
        new TrustTransaction(
            accountId,
            "INV_INTEREST",
            net,
            investment.getCustomerId(),
            investment.getProjectId(),
            null,
            reference,
            description,
            entryDate,
            "RECORDED",
            memberId);
    netDraft.setInvestmentId(investment.getId());
    netDraft.setBankStatementLineId(line == null ? null : line.getId());
    var netRow = postingService.record(netDraft);
    var rows = new ArrayList<TrustTransaction>(List.of(netRow));
    TrustTransaction fundRow = null;
    if (presentation == InterestPresentation.GROSS) {
      var fundLedger =
          creditorAccountRepository
              .findByTrustAccountIdAndAccountType(
                  accountId, TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Trust account "
                              + accountId
                              + " has no FUND_INTEREST_AND_CHARGES account"));
      var fundDraft =
          new TrustTransaction(
              accountId,
              "FUND_INV_SHARE",
              fund,
              null,
              null,
              null,
              reference,
              description,
              entryDate,
              "RECORDED",
              memberId);
      fundDraft.setCreditorAccountId(fundLedger.getId());
      fundDraft.setInvestmentId(investment.getId());
      fundDraft.setBankStatementLineId(line == null ? null : line.getId());
      fundRow = postingService.record(fundDraft);
      rows.add(fundRow);
    }

    // The entry, with the client's tax reference where held (a snapshot — A3)
    var taxReference =
        customerRepository
            .findById(investment.getCustomerId())
            .map(Customer::getTaxNumber)
            .filter(t -> !t.isBlank())
            .orElse(null);
    var entry =
        interestRepository.save(
            new TrustInvestmentInterest(
                investment.getId(),
                entryDate,
                gross,
                fund,
                net,
                presentation,
                netRow.getId(),
                fundRow == null ? null : fundRow.getId(),
                line == null ? null : line.getId(),
                taxReference,
                memberId));

    // The line (rule 4) and the audit trail
    if (line != null) {
      StatementLines.markRecorded(
          line, statement, StatementLineClassification.INVESTMENT_INTEREST, netRow);
      lineRepository.save(line);
      statementRepository.save(statement);
      StatementLines.auditRecorded(
          auditService,
          line,
          StatementLineClassification.INVESTMENT_INTEREST,
          lineAmount,
          rows,
          notes);
    } else {
      for (var row : rows) {
        auditService.log(
            AuditEventBuilder.builder()
                .eventType(
                    TrustTransactionType.of(row.getTransactionType()).auditKey() + ".recorded")
                .entityType("trust_transaction")
                .entityId(row.getId())
                .details(StatementLines.rowDetails(row))
                .build());
      }
    }
    var details = baseDetails(investment);
    details.put("amount", gross.toPlainString());
    details.put("gross", gross.toPlainString());
    details.put("fund_share", fund.toPlainString());
    details.put("net", net.toPlainString());
    details.put("presentation", presentation.name());
    details.put("entry_date", entryDate.toString());
    details.put("interest_id", entry.getId().toString());
    details.put("net_transaction_id", netRow.getId().toString());
    if (line != null) {
      details.put("bank_statement_line_id", line.getId().toString());
    }
    audit("trust_investment.interest_recorded", investment, details);

    // The client's investment-ledger row (portal sync admits CLIENT rows only until 648D)
    publishRecorded(netRow, netRow.getTransactionType(), memberId);
    return entry;
  }

  private void auditInterestReversed(
      TrustInvestment investment, TrustInvestmentInterest entry, List<TrustTransaction> contras) {
    var details = baseDetails(investment);
    details.put("amount", entry.getGrossAmount().toPlainString());
    details.put("gross", entry.getGrossAmount().toPlainString());
    details.put("fund_share", entry.getFundShareAmount().toPlainString());
    details.put("net", entry.getNetAmount().toPlainString());
    details.put("presentation", entry.getPresentation().name());
    details.put("interest_id", entry.getId().toString());
    details.put(
        "reversed_transaction_ids",
        contras.stream().map(contra -> contra.getReversalOf().toString()).toList());
    if (entry.getBankStatementLineId() != null) {
      details.put("bank_statement_line_id", entry.getBankStatementLineId().toString());
    }
    audit("trust_investment.interest_reversed", investment, details);
  }

  /** The entry's rows in order: the net row, then the Fund share (GROSS only). */
  private static List<UUID> entryRowIds(TrustInvestmentInterest entry) {
    return entry.getFundShareTransactionId() == null
        ? List.of(entry.getNetTransactionId())
        : List.of(entry.getNetTransactionId(), entry.getFundShareTransactionId());
  }

  private TrustInvestment lockInvestment(UUID investmentId) {
    return investmentRepository
        .findByIdForUpdate(investmentId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustInvestment", investmentId));
  }

  /** Locks the line (rule 1 — first in every interest lock order) and checks it is UNMATCHED. */
  private BankStatementLine lockUnmatchedLine(UUID lineId) {
    var line =
        lineRepository
            .findByIdForUpdate(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatementLine", lineId));
    StatementLines.requireUnmatched(line);
    return line;
  }

  /** Locks the line's statement (it serialises the matched_count update). */
  private BankStatement lockStatement(BankStatementLine line) {
    return statementRepository
        .findByIdForUpdate(line.getBankStatementId())
        .orElseThrow(
            () -> new ResourceNotFoundException("BankStatement", line.getBankStatementId()));
  }

  /** A client row posted: the portal read-model re-projects (CLIENT rows only until 648D). */
  private void publishRecorded(TrustTransaction row, String type, UUID memberId) {
    eventPublisher.publishEvent(
        TrustTransactionRecordedEvent.recorded(
            row.getId(),
            row.getTrustAccountId(),
            row.getProjectId(),
            type,
            row.getAmount(),
            row.getTransactionDate(),
            row.getCustomerId(),
            memberId,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull()));
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
   * principal} — and so must the cashbook as at every later date a posted row is dated (FU-9 R-2: a
   * payment approved ahead of its date is counted). The caller holds the funding account's lock.
   * The one refusal for the request and the approval.
   */
  private void requireCashbookCovers(UUID fundingAccountId, BigDecimal principal) {
    var cashbook = ledgerQueries.lowestCashbookFrom(fundingAccountId, LocalDate.now(clock));
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
   * Batch-loaded names, balances, interest totals and placement statuses for a page: five queries
   * at most, never one per row. A section 86(3) investment has no client ({@code clientName} null);
   * a deleted client reads "(deleted customer)".
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
    // 648B: the POSTED interest entries' totals, one query for the page
    Map<UUID, InterestTotals> interest =
        interestRepository
            .sumPostedByInvestmentIds(ids(investments, TrustInvestment::getId))
            .stream()
            .collect(
                Collectors.toMap(
                    InterestTotals::getInvestmentId, Function.identity(), (a, b) -> a));
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
                    totalOr0(interest.get(inv.getId()), InterestTotals::getGross),
                    totalOr0(interest.get(inv.getId()), InterestTotals::getFundShare),
                    totalOr0(interest.get(inv.getId()), InterestTotals::getNet),
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

  private static BigDecimal totalOr0(
      InterestTotals totals, Function<InterestTotals, BigDecimal> field) {
    return totals == null || field.apply(totals) == null ? BigDecimal.ZERO : field.apply(totals);
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
