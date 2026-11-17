package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The trust equation as at a date (Phase 92 646C, architecture §16.1.2, §16.3.3, ADR-377). Every
 * term is a sum over posted rows ({@code posted_at IS NOT NULL}) dated on or before the date — "as
 * at D" means the books as now known for dates up to D. Read-only: nothing here posts.
 *
 * <ul>
 *   <li>{@link #position} — EQ-2 for one account: the cashbook against the six creditor terms.
 *   <li>{@link #firmPosition} — every trust account of the firm (open or closed) and the firm
 *       totals; EQ-3 holds when the summed inter-account term is zero.
 *   <li>{@link #bankPosition} — EQ-1's bank side at a reconciliation's period end: the statement's
 *       closing balance carried to the date (sound because {@code createReconciliation} refuses a
 *       statement that does not fit the period end, §16.3.4) and the outstanding items dated by the
 *       statement line's date.
 * </ul>
 *
 * <p>"Today" is the caller's {@code LocalDate.now(clock)}; {@code asAt} is never null.
 */
@Service
public class TrustBooksService {

  private static final String MODULE_ID = "trust_accounting";

  private final TrustLedgerQueryRepository ledgerQueries;
  private final TrustAccountRepository trustAccountRepository;
  private final BankStatementRepository bankStatementRepository;
  private final VerticalModuleGuard moduleGuard;

  public TrustBooksService(
      TrustLedgerQueryRepository ledgerQueries,
      TrustAccountRepository trustAccountRepository,
      BankStatementRepository bankStatementRepository,
      VerticalModuleGuard moduleGuard) {
    this.ledgerQueries = ledgerQueries;
    this.trustAccountRepository = trustAccountRepository;
    this.bankStatementRepository = bankStatementRepository;
    this.moduleGuard = moduleGuard;
  }

  /**
   * EQ-2 terms for one account as at a date; {@code creditorTotal} is the six-term creditor side
   * and {@code difference = cashbook − creditorTotal} (zero when EQ-2 holds).
   */
  public record TrustPosition(
      UUID trustAccountId,
      LocalDate asAt,
      BigDecimal cashbook,
      BigDecimal clientLedgers,
      BigDecimal investmentCreditors,
      BigDecimal fundLedger,
      BigDecimal unidentifiedDeposits,
      BigDecimal otherNonClient,
      BigDecimal interAccount,
      BigDecimal creditorTotal,
      BigDecimal difference) {}

  /**
   * EQ-1 terms as at a reconciliation's period end: the statement's closing balance carried to the
   * date, the outstanding items, and {@code adjustedBankBalance = bank + deposits − payments}.
   */
  public record BankPosition(
      BigDecimal bankBalance,
      BigDecimal outstandingDeposits,
      BigDecimal outstandingPayments,
      BigDecimal adjustedBankBalance) {}

  /**
   * Every trust account of the firm (open or closed, ordered by id) as at a date, and the firm
   * totals of each term. EQ-3: {@code interAccount} is zero firm-wide (money placed in a section
   * 86(3) investment account by one account is held by another).
   */
  public record FirmTrustPosition(
      LocalDate asAt,
      List<TrustPosition> accounts,
      BigDecimal cashbook,
      BigDecimal clientLedgers,
      BigDecimal investmentCreditors,
      BigDecimal fundLedger,
      BigDecimal unidentifiedDeposits,
      BigDecimal otherNonClient,
      BigDecimal interAccount,
      BigDecimal creditorTotal,
      BigDecimal difference) {}

  @Transactional(readOnly = true)
  public TrustPosition position(UUID trustAccountId, LocalDate asAt) {
    moduleGuard.requireModule(MODULE_ID);
    Objects.requireNonNull(asAt, "asAt");
    if (!trustAccountRepository.existsById(trustAccountId)) {
      throw new ResourceNotFoundException("TrustAccount", trustAccountId);
    }
    return positionOf(trustAccountId, asAt);
  }

  @Transactional(readOnly = true)
  public FirmTrustPosition firmPosition(LocalDate asAt) {
    moduleGuard.requireModule(MODULE_ID);
    Objects.requireNonNull(asAt, "asAt");
    var accounts =
        trustAccountRepository.findAll().stream()
            .map(TrustAccount::getId)
            .sorted(Comparator.naturalOrder())
            .map(id -> positionOf(id, asAt))
            .toList();
    var cashbook = sum(accounts, TrustPosition::cashbook);
    var creditorTotal = sum(accounts, TrustPosition::creditorTotal);
    return new FirmTrustPosition(
        asAt,
        accounts,
        cashbook,
        sum(accounts, TrustPosition::clientLedgers),
        sum(accounts, TrustPosition::investmentCreditors),
        sum(accounts, TrustPosition::fundLedger),
        sum(accounts, TrustPosition::unidentifiedDeposits),
        sum(accounts, TrustPosition::otherNonClient),
        sum(accounts, TrustPosition::interAccount),
        creditorTotal,
        cashbook.subtract(creditorTotal));
  }

  @Transactional(readOnly = true)
  public BankPosition bankPosition(UUID trustAccountId, UUID bankStatementId, LocalDate asAt) {
    moduleGuard.requireModule(MODULE_ID);
    Objects.requireNonNull(asAt, "asAt");
    var statement =
        bankStatementRepository
            .findById(bankStatementId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatement", bankStatementId));
    if (!statement.getTrustAccountId().equals(trustAccountId)) {
      throw new InvalidStateException(
          "Trust account mismatch",
          "Bank statement does not belong to the specified trust account");
    }
    var bank = statement.getClosingBalance();
    var outstanding = ledgerQueries.outstanding(trustAccountId, asAt);
    var deposits = outstanding.getOutstandingDeposits();
    var payments = outstanding.getOutstandingPayments();
    return new BankPosition(bank, deposits, payments, bank.add(deposits).subtract(payments));
  }

  private TrustPosition positionOf(UUID trustAccountId, LocalDate asAt) {
    var row = ledgerQueries.position(trustAccountId, asAt);
    var creditorTotal =
        row.getClientLedgers()
            .add(row.getInvestmentCreditors())
            .add(row.getFundLedger())
            .add(row.getUnidentified())
            .add(row.getOtherNonClient())
            .add(row.getInterAccount());
    return new TrustPosition(
        trustAccountId,
        asAt,
        row.getCashbook(),
        row.getClientLedgers(),
        row.getInvestmentCreditors(),
        row.getFundLedger(),
        row.getUnidentified(),
        row.getOtherNonClient(),
        row.getInterAccount(),
        creditorTotal,
        row.getCashbook().subtract(creditorTotal));
  }

  private static BigDecimal sum(
      List<TrustPosition> accounts, Function<TrustPosition, BigDecimal> term) {
    return accounts.stream().map(term).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
