package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnAudit;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchRepository;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchStatus;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRow;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRowRepository;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRowStatus;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOffKind;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOffRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDeposit;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustDomainEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestment;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustOpeningPlan.Investment;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustOpeningPlan.OutstandingKind;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustTakeOnPreconditions.Kind;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionTypeLabels;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The trust take-on commit (Phase 93 657C, §17.3.9, ADR-386, ADR-387, ADR-391): one transaction,
 * all or nothing, under the Phase 92 global lock order ({@code TrustPostingService} class Javadoc)
 * — the path's own row (the batch, {@code FOR UPDATE SKIP LOCKED}), then the client lock anchors,
 * then the trust accounts {@code FOR UPDATE} (the banking account, then the investment accounts in
 * UUID order). Under those locks it re-checks the live trust sign-off on the batch's hash, the
 * redacted rows (D-194), every reference (the planner) and the §17.3.9 account checks (D-191);
 * opens the held unidentified deposits and the investments; posts every opening row through {@code
 * recordOpening} ({@code APPROVED} by the signer at the sign-off time, {@code cleared_at_cutover}
 * from the registry); marks every account taken on; and proves the books tie to the cent ({@link
 * TrustOpeningSelfCheck}) before the take-on report is snapshotted and the batch completes. A
 * refusal ({@link TakeOnCommitRefusal}) or any other exception rolls everything back; {@link
 * TrustTakeOnRunner} then returns the batch to {@code VALIDATED} in a separate transaction.
 *
 * <p>Nothing here publishes {@code TrustTransactionRecordedEvent} (no portal email can fire): the
 * commit publishes one {@link TrustDomainEvent.TakeOnPositionCommitted}, whose AFTER_COMMIT
 * listener recomputes the portal per account. Callable only from {@code ..takeon..} ({@code
 * TakeOnEntryPointsArchTest}). Runs in the drain job: {@code MEMBER_ID} is the member who confirmed
 * the commit; no capability is checked.
 */
@Service
public class TrustOpeningPositionService {

  /** How the commit ended when it did not throw. */
  public enum Result {
    /** This call committed the batch. */
    COMMITTED,
    /** The batch is no longer {@code COMMITTING} (completed, or returned, by another worker). */
    NOT_COMMITTING,
    /** Another worker holds the batch row (a redelivered drain): skipped, never waited for. */
    HELD_ELSEWHERE
  }

  /** The result entity of a posted take-on row ({@code take_on_rows.result_entity_type}). */
  static final String TRUST_TRANSACTION = "TRUST_TRANSACTION";

  private final TakeOnBatchRepository batches;
  private final TakeOnSignOffRepository signOffs;
  private final TakeOnRowRepository rows;
  private final TrustOpeningPositionPlanner planner;
  private final TrustPostingService posting;
  private final TrustAccountRepository accounts;
  private final TrustTakeOnPreconditions preconditions;
  private final TrustCreditorAccountRepository creditorAccounts;
  private final UnidentifiedDepositRepository unidentifiedDeposits;
  private final TrustInvestmentRepository investments;
  private final TrustOpeningSelfCheck selfCheck;
  private final TakeOnReportService reports;
  private final TakeOnAudit audit;
  private final ApplicationEventPublisher events;
  private final EntityManager entityManager;

  public TrustOpeningPositionService(
      TakeOnBatchRepository batches,
      TakeOnSignOffRepository signOffs,
      TakeOnRowRepository rows,
      TrustOpeningPositionPlanner planner,
      TrustPostingService posting,
      TrustAccountRepository accounts,
      TrustTakeOnPreconditions preconditions,
      TrustCreditorAccountRepository creditorAccounts,
      UnidentifiedDepositRepository unidentifiedDeposits,
      TrustInvestmentRepository investments,
      TrustOpeningSelfCheck selfCheck,
      TakeOnReportService reports,
      TakeOnAudit audit,
      ApplicationEventPublisher events,
      EntityManager entityManager) {
    this.batches = batches;
    this.signOffs = signOffs;
    this.rows = rows;
    this.planner = planner;
    this.posting = posting;
    this.accounts = accounts;
    this.preconditions = preconditions;
    this.creditorAccounts = creditorAccounts;
    this.unidentifiedDeposits = unidentifiedDeposits;
    this.investments = investments;
    this.selfCheck = selfCheck;
    this.reports = reports;
    this.audit = audit;
    this.events = events;
    this.entityManager = entityManager;
  }

  /** What a posted row records as its result: the main transaction and the other ids. */
  private record Posted(UUID entityId, Map<String, Object> refs) {}

  @Transactional
  public Result commit(UUID batchId) {
    // 1. The path's own row; empty ⇒ another delivery holds it.
    var locked = batches.findByIdForUpdateSkipLocked(batchId);
    if (locked.isEmpty()) {
      return Result.HELD_ELSEWHERE;
    }
    TakeOnBatch batch = locked.get();
    if (batch.getStatus() != TakeOnBatchStatus.COMMITTING) {
      return Result.NOT_COMMITTING;
    }
    var signOff =
        signOffs
            .findByBatchIdAndKindAndRevokedAtIsNull(batchId, TakeOnSignOffKind.TRUST_SIGN_OFF)
            .filter(s -> s.getValidationHash().equals(batch.getValidationHash()))
            .orElseThrow(TakeOnCommitRefusal::signOffMissing);
    if (rows.anyRedacted(batchId)) {
      throw TakeOnCommitRefusal.rowRedacted(); // D-194, R-3
    }
    List<TakeOnRow> validated =
        rows.findByBatchIdAndStatusOrderByRowNumberAsc(batchId, TakeOnRowStatus.VALIDATED);
    var plan = planner.plan(batch, validated); // re-resolves the clients and matters (R-3)

    // 2. Client lock anchors, in order.
    plan.clientAnchors().forEach(a -> posting.lockClient(a.accountId(), a.clientId()));

    // 3. The accounts FOR UPDATE: the banking account, then the investment accounts (UUID order).
    var lockedAccounts = new LinkedHashMap<UUID, TrustAccount>();
    for (UUID accountId : plan.accountIds()) {
      lockedAccounts.put(
          accountId,
          accounts
              .findByIdForUpdate(accountId)
              .orElseThrow(
                  () ->
                      TakeOnCommitRefusal.referenceUnresolved(
                          TrustOpeningPositionPlanner.INVESTMENT_ACCOUNT_CHANGED_MESSAGE)));
    }
    planner.checkInvestmentAccounts(plan, lockedAccounts);
    recheckPreconditions(batch, plan, lockedAccounts); // D-191: the account checks under lock

    var before = new HashMap<UUID, TrustOpeningSelfCheck.Books>();
    for (UUID i : plan.investmentAccountIds()) {
      before.put(i, selfCheck.read(i, plan.cutoverDate())); // a shared account's own take-on
    }

    var authority = OpeningAuthority.forBatch(batch, signOff); // the only key to recordOpening
    var posted = new HashMap<UUID, Posted>();
    UUID g = plan.bankingAccountId();

    // Held unidentified deposits: item → TAKEON_UNIDENTIFIED naming it → attach.
    UUID unidentifiedCreditor = creditor(g, TrustCreditorAccountType.UNIDENTIFIED_DEPOSITS);
    for (var item : plan.unidentifiedItems()) {
      var deposit =
          unidentifiedDeposits.saveAndFlush(
              UnidentifiedDeposit.takenOn(
                  g,
                  unidentifiedCreditor,
                  batch.getId(),
                  item.receivedDate(),
                  item.description(),
                  item.bankReference(),
                  item.amount(),
                  null));
      var draft =
          draft(
              authority,
              g,
              "TAKEON_UNIDENTIFIED",
              item.amount(),
              null,
              null,
              item.bankReference() != null ? item.bankReference() : reference("UD", item.row()));
      draft.setCreditorAccountId(unidentifiedCreditor);
      draft.setUnidentifiedDepositId(deposit.getId());
      var row = posting.recordOpening(draft, authority);
      deposit.attachDepositTransaction(row.getId());
      unidentifiedDeposits.save(deposit);
      posted.put(
          item.row().getId(),
          new Posted(row.getId(), Map.of("unidentifiedDepositId", deposit.getId())));
    }

    // Investments: the investment → its placement row(s) → its deposit_transaction_id.
    for (var investment : plan.investments()) {
      posted.put(investment.row().getId(), openInvestment(authority, g, investment));
    }

    // Client and matter balances (nil: nothing posted, FD-33).
    for (var balance : plan.clientBalances()) {
      if (balance.nil()) {
        continue;
      }
      var row =
          posting.recordOpening(
              draft(
                  authority,
                  g,
                  "TAKEON_CLIENT",
                  balance.balance(),
                  balance.clientId(),
                  balance.matterId(),
                  reference("CL", balance.row())),
              authority);
      posted.put(balance.row().getId(), new Posted(row.getId(), Map.of()));
    }

    // The Fund ledger (F > 0: TAKEON_FUND; F < 0: TAKEON_FUND_DEBIT of |F|; 0: nothing).
    BigDecimal fund = plan.fundLedger();
    if (fund.signum() != 0) {
      var draft =
          draft(
              authority,
              g,
              fund.signum() > 0 ? "TAKEON_FUND" : "TAKEON_FUND_DEBIT",
              fund.abs(),
              null,
              null,
              "TAKEON-FUND");
      draft.setCreditorAccountId(creditor(g, TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES));
      posting.recordOpening(draft, authority);
    }

    // Items outstanding at cutover: the outstanding, matchable row (the file's reference) and its
    // cleared offset, both on the client's matter (§17.3.8).
    for (var item : plan.outstandingItems()) {
      boolean deposit = item.kind() == OutstandingKind.DEPOSIT_IN_TRANSIT;
      var outstanding =
          posting.recordOpening(
              draft(
                  authority,
                  g,
                  deposit ? "TAKEON_DEP_TRANSIT" : "TAKEON_PAY_UNPRES",
                  item.amount(),
                  item.clientId(),
                  item.matterId(),
                  item.reference()),
              authority);
      var offset =
          posting.recordOpening(
              draft(
                  authority,
                  g,
                  deposit ? "TAKEON_DEP_OFFSET" : "TAKEON_PAY_OFFSET",
                  item.amount(),
                  item.clientId(),
                  item.matterId(),
                  reference("OI", item.row())),
              authority);
      posted.put(
          item.row().getId(),
          new Posted(outstanding.getId(), Map.of("offsetTransactionId", offset.getId())));
    }

    lockedAccounts.values().forEach(a -> a.markTakenOn(batch.getCutoverDate(), batch.getId()));
    entityManager.flush(); // the native reads of the self-check must see the rows just posted

    var figures = selfCheck.assertTies(plan, batch, before); // else SELF_CHECK_FAILED → rollback
    var report = reports.snapshot(batch, signOff, plan, figures); // audits report.generated

    for (TakeOnRow row : validated) {
      var result = posted.get(row.getId());
      if (result == null) {
        row.markSkippedZero(); // a nil client balance (FD-33)
      } else {
        row.markCreated(TRUST_TRANSACTION, result.entityId(), result.refs());
      }
    }
    entityManager.flush();
    var counts = rows.commitCountsFor(batchId);
    batch.complete(
        Math.toIntExact(counts.getCreated()),
        Math.toIntExact(counts.getSkipped()),
        Math.toIntExact(counts.getFailed()));
    audit.committed(batch);
    events.publishEvent(
        TrustDomainEvent.TakeOnPositionCommitted.of(
            batch.getId(),
            plan.accountIds(),
            report.getId(),
            batch.getCommitRequestedBy(),
            RequestScopes.requireTenantId(),
            RequestScopes.requireOrgId()));
    return Result.COMMITTED;
  }

  /** §17.3.9 account checks on every locked account (D-191), the batch never counting itself. */
  private void recheckPreconditions(
      TakeOnBatch batch, TrustOpeningPlan plan, Map<UUID, TrustAccount> lockedAccounts) {
    for (var entry : lockedAccounts.entrySet()) {
      Kind kind = entry.getKey().equals(plan.bankingAccountId()) ? Kind.BANKING : Kind.INVESTMENT;
      preconditions
          .firstFailure(entry.getValue(), batch.getCutoverDate(), kind, batch.getId())
          .ifPresent(
              f -> {
                throw TakeOnCommitRefusal.preconditionFailed(f.message());
              });
    }
  }

  /**
   * An investment brought forward (ADR-387): saved first, then its placement row — {@code
   * TAKEON_INVESTMENT} on the investment account for the client (s86(4)), or {@code
   * TAKEON_FIRM_INV_OUT} on the banking account and {@code TAKEON_FIRM_INV_IN} on the investment
   * account (s86(3)) — and the Fund's money held on it ({@code TAKEON_FUND}); the placement row
   * becomes its {@code deposit_transaction_id}, what the withdrawal and the interest entry read.
   */
  private Posted openInvestment(OpeningAuthority authority, UUID g, Investment i) {
    boolean s864 = i.clientInstruction();
    var terms =
        s864
            ? new TrustInvestment.TakeOnTerms(
                authority.batchId(),
                i.instructionReference(),
                i.instructionDate(),
                i.endorsementConfirmed(),
                i.accruedInterest(),
                i.fundAmount())
            : new TrustInvestment.TakeOnTerms(
                authority.batchId(), null, null, false, i.accruedInterest(), i.fundAmount());
    TrustInvestment investment;
    try {
      investment =
          investments.saveAndFlush(
              TrustInvestment.takenOn(
                  g,
                  i.investmentAccountId(),
                  i.clientId(),
                  i.matterId(),
                  i.institution(),
                  i.accountNumber(),
                  i.principal(),
                  i.interestRate(),
                  i.depositDate(),
                  null,
                  i.basis(),
                  terms));
    } catch (IllegalArgumentException e) {
      throw TakeOnCommitRefusal.referenceUnresolved(
          TrustOpeningPositionPlanner.ROW_CHANGED_MESSAGE); // s86(4) without its instruction
    }
    var refs = new LinkedHashMap<String, Object>();
    refs.put("investmentId", investment.getId());
    TrustTransaction placement;
    String reference = reference("IN", i.row());
    if (s864) {
      var draft =
          draft(
              authority,
              i.investmentAccountId(),
              "TAKEON_INVESTMENT",
              i.principal(),
              i.clientId(),
              i.matterId(),
              reference);
      draft.setInvestmentId(investment.getId());
      placement = posting.recordOpening(draft, authority);
    } else {
      var out = draft(authority, g, "TAKEON_FIRM_INV_OUT", i.principal(), null, null, reference);
      out.setInvestmentId(investment.getId());
      var funding = posting.recordOpening(out, authority);
      var in =
          draft(
              authority,
              i.investmentAccountId(),
              "TAKEON_FIRM_INV_IN",
              i.principal(),
              null,
              null,
              reference);
      in.setInvestmentId(investment.getId());
      placement = posting.recordOpening(in, authority);
      refs.put("fundingTransactionId", funding.getId());
    }
    if (i.fundAmount().signum() > 0) {
      var fund =
          draft(
              authority,
              i.investmentAccountId(),
              "TAKEON_FUND",
              i.fundAmount(),
              null,
              null,
              reference);
      fund.setCreditorAccountId(
          creditor(i.investmentAccountId(), TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES));
      refs.put("fundTransactionId", posting.recordOpening(fund, authority).getId());
    }
    investment.setDepositTransactionId(placement.getId());
    investments.save(investment);
    return new Posted(placement.getId(), refs);
  }

  /** An opening row dated at the cutover date under the batch, recorded by the commit's member. */
  private static TrustTransaction draft(
      OpeningAuthority authority,
      UUID accountId,
      String type,
      BigDecimal amount,
      UUID customerId,
      UUID projectId,
      String reference) {
    var draft =
        new TrustTransaction(
            accountId,
            type,
            amount,
            customerId,
            projectId,
            null,
            reference,
            TrustTransactionTypeLabels.firmLabel(type),
            authority.cutoverDate(),
            null,
            RequestScopes.requireMemberId());
    draft.setTakeOnBatchId(authority.batchId());
    return draft;
  }

  /** A stable reference for a row that has none in the file: {@code TAKEON-CL-7}. */
  private static String reference(String section, TakeOnRow row) {
    return "TAKEON-" + section + "-" + row.getRowNumber();
  }

  /** The account's default creditor account of {@code type} (every trust account has both). */
  private UUID creditor(UUID accountId, TrustCreditorAccountType type) {
    return creditorAccounts
        .findByTrustAccountIdAndAccountType(accountId, type)
        .map(TrustCreditorAccount::getId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Trust account " + accountId + " has no " + type + " creditor account"));
  }
}
