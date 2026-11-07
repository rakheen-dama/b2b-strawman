package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * closing balance. Empty when the {@code trust_accounting} module is off (the ledger service would
 * refuse) or the client has no trust activity.
 *
 * <p>The account is chosen exactly as {@code StatementOfAccountContextBuilder.buildTrustBlock} does
 * (GAP-L-94): the trust account(s) the client actually has activity on; with several, the primary
 * GENERAL account when the client is on it, else the first. Lines and balances come from {@link
 * ClientLedgerService#getClientLedgerStatement}, so opening + lines = closing by construction.
 */
@Component
public class ClientTrustStatementContext {

  private static final Logger log = LoggerFactory.getLogger(ClientTrustStatementContext.class);

  static final String TRUST_ACCOUNTING_MODULE = "trust_accounting";

  /** "All history": no ledger line predates this, so the opening balance is zero. */
  static final LocalDate HISTORY_START = LocalDate.EPOCH;

  private final VerticalModuleGuard moduleGuard;
  private final TrustTransactionRepository trustTransactionRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final ClientLedgerService clientLedgerService;
  private final Clock clock;

  public ClientTrustStatementContext(
      VerticalModuleGuard moduleGuard,
      TrustTransactionRepository trustTransactionRepository,
      TrustAccountRepository trustAccountRepository,
      ClientLedgerService clientLedgerService,
      Clock clock) {
    this.moduleGuard = moduleGuard;
    this.trustTransactionRepository = trustTransactionRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.clientLedgerService = clientLedgerService;
    this.clock = clock;
  }

  /**
   * The client's {@code trust} block: {@code account_name}, {@code opening_balance}, {@code
   * closing_balance}, {@code statement_date} and {@code lines} (each {@code transactionDate},
   * {@code type} — a readable label —, {@code transactionType}, {@code reference}, {@code
   * description}, {@code amount}, {@code runningBalance}); empty when the module is off or the
   * client has no trust activity.
   */
  public Optional<Map<String, Object>> buildFor(UUID clientId) {
    if (!moduleGuard.isModuleEnabled(TRUST_ACCOUNTING_MODULE)) {
      return Optional.empty();
    }
    Optional<TrustAccount> account = selectAccount(clientId);
    if (account.isEmpty()) {
      return Optional.empty();
    }
    LocalDate today = LocalDate.now(clock);
    var statement =
        clientLedgerService.getClientLedgerStatement(
            clientId, account.get().getId(), HISTORY_START, today);

    var lines = new ArrayList<Map<String, Object>>();
    for (var line : statement.transactions()) {
      var row = new LinkedHashMap<String, Object>();
      row.put("transactionDate", line.transactionDate());
      row.put("type", typeLabel(line.transactionType()));
      row.put("transactionType", line.transactionType());
      row.put("reference", line.reference());
      row.put("description", line.description());
      row.put("amount", line.amount());
      row.put("runningBalance", line.runningBalance());
      lines.add(row);
    }

    var trust = new LinkedHashMap<String, Object>();
    trust.put("account_name", account.get().getAccountName());
    trust.put("statement_date", today);
    trust.put("opening_balance", statement.openingBalance());
    trust.put("closing_balance", statement.closingBalance());
    trust.put("lines", List.copyOf(lines));
    return Optional.of(trust);
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

  /** {@code FEE_TRANSFER} → "Fee transfer": a client document never prints the enum. */
  static String typeLabel(String transactionType) {
    if (transactionType == null || transactionType.isBlank()) {
      return "";
    }
    String words = transactionType.replace('_', ' ').toLowerCase(Locale.ROOT);
    return Character.toUpperCase(words.charAt(0)) + words.substring(1);
  }
}
