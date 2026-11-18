package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustDataVisibility;
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
 * <p>The account is chosen exactly as {@code StatementOfAccountContextBuilder.buildTrustBlock} does
 * (GAP-L-94): the trust account(s) the client actually has activity on; with several, the primary
 * GENERAL account when the client is on it, else the first. Lines and balances come from {@link
 * ClientLedgerService#getClientLedgerStatement}, so opening + lines = closing by construction.
 *
 * <p>648F (§16.6.4, ADR-379 L7): a client with section 86(4) investments placed from that account
 * also gets {@code invested} ({@link #investedBlock}) — the block the Statement of Account prints
 * too — over all history to today.
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
  private final Clock clock;

  public ClientTrustStatementContext(
      TrustDataVisibility trustDataVisibility,
      TrustTransactionRepository trustTransactionRepository,
      TrustAccountRepository trustAccountRepository,
      ClientLedgerService clientLedgerService,
      Clock clock) {
    this.trustDataVisibility = trustDataVisibility;
    this.trustTransactionRepository = trustTransactionRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.clientLedgerService = clientLedgerService;
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
    Optional<TrustAccount> account = selectAccount(clientId);
    if (account.isEmpty()) {
      return Optional.of(Map.of("status", STATUS_NONE));
    }
    LocalDate today = LocalDate.now(clock);
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
    return Optional.of(trust);
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

  /** Mirrors {@code StatementOfAccountContextBuilder.buildTrustBlock}'s account selection. */
  private Optional<TrustAccount> selectAccount(UUID clientId) {
    List<UUID> accountIds =
        trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(clientId);
    if (accountIds.isEmpty()) {
      return Optional.empty();
    }
    UUID accountId = accountIds.getFirst();
    if (accountIds.size() > 1) {
      accountId =
          trustAccountRepository
              .findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL)
              .map(TrustAccount::getId)
              .filter(accountIds::contains)
              .orElse(accountIds.getFirst());
      log.warn(
          "Client {} has activity on {} trust accounts; using {} for the client trust statement",
          clientId,
          accountIds.size(),
          accountId);
    }
    return trustAccountRepository.findById(accountId);
  }
}
