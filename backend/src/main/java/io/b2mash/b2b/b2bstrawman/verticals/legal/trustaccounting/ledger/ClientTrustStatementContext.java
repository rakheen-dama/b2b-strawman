package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustDataVisibility;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustSnapshotReads;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionTypeLabels;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The {@code trust} block of the generic client document context (G89 D11): what the legal-za
 * Client Trust Statement prints — the trust account's name, the client's opening balance (zero: the
 * statement covers all history), every ledger line to today with its running balance, and the
 * closing balance, with {@code status} "available". A member who may see trust money but whose
 * client has no trust activity gets {@code status} "none" only. Empty — no trust data at all — when
 * {@link TrustDataVisibility} says no: the {@code trust_accounting} module is off, or the member
 * lacks {@code VIEW_TRUST} (security review, PR #1749; the generic generate endpoint does not
 * require it). The template prints "Trust details are not available." then.
 *
 * <p>The account is chosen by {@link #statementAccountId}, the one rule the Statement of Account
 * uses too (GAP-L-94, 652D): of the trust account(s) the client actually has activity on, the one
 * holding the client's money (general plus section 86(4) invested) as at today; a sole account is
 * used as-is, so a client whose only account is closed still gets its history. Lines and balances
 * come from {@link ClientLedgerService#getClientLedgerStatement}, so opening + lines = closing by
 * construction.
 *
 * <p>648F (§16.6.4, ADR-379 L7): a client with section 86(4) investments placed from that account
 * also gets {@code invested} ({@link #investedBlock}) — the block the Statement of Account prints
 * too — over all history to today.
 *
 * <p>Phase 92 FU-3 (R-1): the account choice, the lines and both balances are read in one {@link
 * TrustSnapshotReads} call, so a posting committed while a document is generated is on the block
 * whole or not at all.
 */
@Component
public class ClientTrustStatementContext {

  private static final Logger log = LoggerFactory.getLogger(ClientTrustStatementContext.class);

  static final String STATUS_AVAILABLE = "available";
  static final String STATUS_NONE = "none";

  /** "All history": no ledger line predates this, so the opening balance is zero. */
  static final LocalDate HISTORY_START = LocalDate.EPOCH;

  private final TrustDataVisibility trustDataVisibility;
  private final TrustTransactionRepository trustTransactionRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final ClientLedgerService clientLedgerService;
  private final TrustSnapshotReads snapshotReads;
  private final Clock clock;

  public ClientTrustStatementContext(
      TrustDataVisibility trustDataVisibility,
      TrustTransactionRepository trustTransactionRepository,
      TrustAccountRepository trustAccountRepository,
      ClientLedgerService clientLedgerService,
      TrustSnapshotReads snapshotReads,
      Clock clock) {
    this.trustDataVisibility = trustDataVisibility;
    this.trustTransactionRepository = trustTransactionRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.clientLedgerService = clientLedgerService;
    this.snapshotReads = snapshotReads;
    this.clock = clock;
  }

  /**
   * The client's {@code trust} block: {@code account_name}, {@code opening_balance}, {@code
   * closing_balance}, {@code statement_date} and {@code lines} (each {@code transactionDate},
   * {@code type} — the registry's client label ({@link TrustTransactionTypeLabels#label}), never
   * the enum —, {@code transactionType}, {@code reference}, {@code description} — {@link
   * TrustTransactionTypeLabels#clientDescription}, so a reversal never prints the firm's reason —,
   * {@code amount}, {@code runningBalance}), {@code status} and, for a client with a section 86(4)
   * investment, {@code invested} ({@link #investedBlock}); {@code status} "none" alone when the
   * client has no trust activity; empty when the member may not see trust money ({@link
   * TrustDataVisibility}).
   */
  public Optional<Map<String, Object>> buildFor(UUID clientId) {
    if (!trustDataVisibility.visibleToCurrentMember()) {
      return Optional.empty();
    }
    // FU-3 (R-1): the account choice, the ledger lines and both balances on one snapshot — a
    // placement or return committing mid-build cannot be counted twice, or empty the account
    return Optional.of(snapshotReads.read(() -> trustBlock(clientId)));
  }

  /** The {@code trust} block's reads and values; runs inside {@link TrustSnapshotReads}. */
  private Map<String, Object> trustBlock(UUID clientId) {
    LocalDate today = LocalDate.now(clock);
    Optional<TrustAccount> account = selectAccount(clientId, today);
    if (account.isEmpty()) {
      return Map.of("status", STATUS_NONE);
    }
    var statement =
        clientLedgerService.getClientLedgerStatement(
            clientId, account.get().getId(), HISTORY_START, today);

    var lines = new ArrayList<Map<String, Object>>();
    for (var line : statement.transactions()) {
      var row = new LinkedHashMap<String, Object>();
      row.put("transactionDate", line.transactionDate());
      // N-1 (648E C-4): the registry's client label, as on the SoA and the portal
      row.put("type", TrustTransactionTypeLabels.label(line.transactionType()));
      row.put("transactionType", line.transactionType());
      row.put("reference", line.reference());
      // A REVERSAL line reads "Reversal — <reference>", never the firm user's reason (PR #1789)
      row.put(
          "description",
          TrustTransactionTypeLabels.clientDescription(
              line.transactionType(), line.description(), line.reference()));
      row.put("amount", line.amount());
      row.put("runningBalance", line.runningBalance());
      lines.add(row);
    }

    var trust = new LinkedHashMap<String, Object>();
    trust.put("status", STATUS_AVAILABLE);
    trust.put("account_name", account.get().getAccountName());
    trust.put("statement_date", today);
    trust.put("opening_balance", statement.openingBalance());
    trust.put("closing_balance", statement.closingBalance());
    trust.put("lines", List.copyOf(lines));
    var invested =
        clientLedgerService.getClientInvestedStatement(
            clientId, account.get().getId(), HISTORY_START, today);
    if (!invested.isEmpty()) {
      trust.put("invested", investedBlock(invested, statement.closingBalance()));
    }
    return trust;
  }

  /**
   * The {@code invested} block both client documents print under "Section 86(4) investment" (648F):
   * snake_case keys bound by the legal-za v16 bodies and registered as currency in {@code
   * VariableMetadataRegistry} (client trust statement) / {@code
   * StatementOfAccountContextBuilder.FORMAT_HINTS} (statement of account). {@code interest_entries}
   * is present only when non-empty: the renderer's {@code isNotEmpty} reads an empty list ("[]") as
   * non-empty and would print an empty table. {@code total_held} is the client's general balance on
   * the account plus what is invested — the portal's "Held in trust".
   */
  public static Map<String, Object> investedBlock(
      ClientLedgerService.InvestedStatement invested, BigDecimal generalClosing) {
    var block = new LinkedHashMap<String, Object>();
    block.put("opening_balance", invested.openingBalance());
    block.put("capital_placed", invested.capitalPlaced());
    block.put("interest_gross", invested.interestGross());
    block.put("interest_fund_share", invested.interestFundShare());
    block.put("interest_net", invested.interestNet());
    block.put("returned_to_trust", invested.returnedToTrust());
    block.put("closing_balance", invested.closingBalance());
    block.put("total_held", generalClosing.add(invested.closingBalance()));
    if (!invested.interestLines().isEmpty()) {
      var rows = new ArrayList<Map<String, Object>>();
      for (var line : invested.interestLines()) {
        var row = new LinkedHashMap<String, Object>();
        row.put("entryDate", line.entryDate());
        row.put("grossAmount", line.grossAmount());
        row.put("fundShareAmount", line.fundShareAmount());
        row.put("netAmount", line.netAmount());
        rows.add(row);
      }
      block.put("interest_entries", List.copyOf(rows));
    }
    return block;
  }

  /**
   * The account a client document prints (GAP-L-94, 652D D-1/D-2): of the accounts the client has
   * client-ledger activity on ({@code candidates}, in id order), those holding money for the client
   * as at {@code asAt} — general balance plus what is invested under section 86(4) from the
   * account, non-zero (D-2: a placement takes the money off the general ledger, so money that is
   * all invested still counts) — or all of them when none does; among those the primary GENERAL
   * account, else the first id. A closed account holds nothing after its close (TrustAccountService
   * close guard), so it is never chosen over an account that holds the client's money; a sole
   * account is used as-is. Balances are read only when there are several candidates. Shared by this
   * context (as at today) and {@code StatementOfAccountContextBuilder.buildTrustBlock} (as at the
   * period end).
   */
  public static Optional<UUID> statementAccountId(
      UUID clientId,
      List<UUID> candidates,
      LocalDate asAt,
      ClientLedgerService ledger,
      TrustAccountRepository accounts) {
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    if (candidates.size() == 1) {
      return Optional.of(candidates.getFirst());
    }
    List<UUID> holding =
        candidates.stream().filter(id -> heldOn(clientId, id, asAt, ledger).signum() != 0).toList();
    List<UUID> pool = holding.isEmpty() ? candidates : holding;
    return Optional.of(
        accounts
            .findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL)
            .map(TrustAccount::getId)
            .filter(pool::contains)
            .orElse(pool.getFirst()));
  }

  /** The client's general balance on the account plus what is invested from it, as at asAt. */
  private static BigDecimal heldOn(
      UUID clientId, UUID accountId, LocalDate asAt, ClientLedgerService ledger) {
    return ledger
        .getClientBalanceAsOfDate(clientId, accountId, asAt)
        .add(ledger.getClientInvestedBalanceAsOfDate(clientId, accountId, asAt));
  }

  private Optional<TrustAccount> selectAccount(UUID clientId, LocalDate today) {
    List<UUID> accountIds =
        trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(clientId);
    Optional<UUID> accountId =
        statementAccountId(
            clientId, accountIds, today, clientLedgerService, trustAccountRepository);
    if (accountId.isEmpty()) {
      return Optional.empty();
    }
    if (accountIds.size() > 1) {
      log.warn(
          "Client {} has activity on {} trust accounts; using {} for the client trust statement",
          clientId,
          accountIds.size(),
          accountId.get());
    }
    return trustAccountRepository.findById(accountId.get());
  }
}
