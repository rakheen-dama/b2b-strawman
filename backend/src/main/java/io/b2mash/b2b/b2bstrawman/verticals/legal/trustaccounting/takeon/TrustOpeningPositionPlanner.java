package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRow;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionDatasetHandler;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustOpeningPlan.ClientBalance;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustOpeningPlan.Investment;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustOpeningPlan.OutstandingItem;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustOpeningPlan.OutstandingKind;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustOpeningPlan.UnidentifiedItem;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustClientMatterRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link TrustOpeningPlan} of a committing trust take-on from its {@code VALIDATED} rows
 * (Phase 93 657C, §17.3.9, R-3), inside the commit transaction and under the batch lock: the dry
 * run's mapped values (strings: decimals and ISO dates; ids as UUID text) typed once, and every
 * reference re-resolved — the client exists and is not anonymised, the matter is still one of the
 * client's ({@link TrustClientMatterRule#requireClientMatter}), and (after the accounts are locked,
 * {@link #checkInvestmentAccounts}) each investment account is still an investment account of the
 * row's basis. A failure is a {@link TakeOnCommitRefusal} ({@code REFERENCE_UNRESOLVED}): nothing
 * is posted. The client's lifecycle status is not checked (R-13, D-187: the trust posting path has
 * no lifecycle guard; a taken-on client is active).
 *
 * <p>The interest rate: the dry run reads "7.25%" as the percent {@code 7.25}, while {@code
 * trust_investments.interest_rate} is a fraction ({@code numeric(5,4)}, 0..1). The planner stores
 * {@code rate / 100} and refuses — never rounds — a rate that does not fit (more than two decimals
 * or above 100%; §1A #4 of the slice brief, F-657C-1).
 */
@Component
public class TrustOpeningPositionPlanner {

  static final String CLIENT_GONE_MESSAGE =
      "A client named in this trust take-on can no longer be found. Validate the take-on again.";

  static final String MATTER_CHANGED_MESSAGE =
      "A balance in this trust take-on is no longer on one of its client's matters. Validate the"
          + " take-on again.";

  static final String INVESTMENT_ACCOUNT_CHANGED_MESSAGE =
      "An investment account named in this trust take-on is no longer an investment account of"
          + " that kind. Validate the take-on again.";

  static final String ROW_CHANGED_MESSAGE =
      "A row of this trust take-on no longer holds what was validated. Validate the take-on"
          + " again.";

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final CustomerRepository customers;
  private final TrustClientMatterRule clientMatterRule;

  public TrustOpeningPositionPlanner(
      CustomerRepository customers, TrustClientMatterRule clientMatterRule) {
    this.customers = customers;
    this.clientMatterRule = clientMatterRule;
  }

  /** The plan of {@code batch} from its validated {@code rows}; refuses an unresolved reference. */
  public TrustOpeningPlan plan(TakeOnBatch batch, List<TakeOnRow> rows) {
    var clientsById = new HashMap<UUID, Customer>();
    var matters = new HashMap<String, Project>();
    var clientBalances = new ArrayList<ClientBalance>();
    var unidentified = new ArrayList<UnidentifiedItem>();
    var investments = new ArrayList<Investment>();
    var outstanding = new ArrayList<OutstandingItem>();
    for (TakeOnRow row : rows) {
      Map<String, Object> v = row.getMappedValues();
      if (v == null) {
        throw TakeOnCommitRefusal.referenceUnresolved(ROW_CHANGED_MESSAGE);
      }
      switch (row.getSection()) {
        case CLIENT_LEDGER -> {
          var client = client(v, clientsById);
          var matter = matter(client, v, matters);
          clientBalances.add(
              new ClientBalance(
                  row,
                  client.getId(),
                  client.getName(),
                  matter.getId(),
                  matterName(matter),
                  requiredAmount(v, "balance")));
        }
        case UNIDENTIFIED_DEPOSITS ->
            unidentified.add(
                new UnidentifiedItem(
                    row,
                    requiredDate(v, "receivedDate"),
                    text(v, "description"),
                    text(v, "bankReference"),
                    requiredAmount(v, "amount")));
        case INVESTMENTS -> investments.add(investment(row, v, clientsById, matters));
        case OUTSTANDING_ITEMS -> {
          var client = client(v, clientsById);
          var matter = matter(client, v, matters);
          OutstandingKind kind;
          try {
            kind = OutstandingKind.valueOf(String.valueOf(v.get("kind")));
          } catch (IllegalArgumentException e) {
            throw TakeOnCommitRefusal.referenceUnresolved(ROW_CHANGED_MESSAGE);
          }
          outstanding.add(
              new OutstandingItem(
                  row,
                  kind,
                  requiredDate(v, "originalDate"),
                  requiredAmount(v, "amount"),
                  requiredText(v, "reference"),
                  client.getId(),
                  client.getName(),
                  matter.getId(),
                  matterName(matter)));
        }
        default -> throw TakeOnCommitRefusal.referenceUnresolved(ROW_CHANGED_MESSAGE);
      }
    }
    return new TrustOpeningPlan(
        batch.getTrustAccountId(),
        batch.getCutoverDate(),
        batch.getFundLedgerBalance(),
        clientBalances,
        unidentified,
        investments,
        outstanding);
  }

  /**
   * After the accounts are locked (R-3): each investment account the plan names is still an {@code
   * INVESTMENT} trust account of the row's basis. {@code locked}: account id → the locked row.
   */
  public void checkInvestmentAccounts(TrustOpeningPlan plan, Map<UUID, TrustAccount> locked) {
    for (Investment investment : plan.investments()) {
      TrustAccount account = locked.get(investment.investmentAccountId());
      if (account == null
          || account.getAccountType() != TrustAccountType.INVESTMENT
          || account.getInvestmentBasis() != investment.basis()) {
        throw TakeOnCommitRefusal.referenceUnresolved(INVESTMENT_ACCOUNT_CHANGED_MESSAGE);
      }
    }
  }

  // --- rows ---

  private Investment investment(
      TakeOnRow row,
      Map<String, Object> v,
      Map<UUID, Customer> clientsById,
      Map<String, Project> matters) {
    InvestmentBasis basis;
    try {
      basis = InvestmentBasis.valueOf(String.valueOf(v.get("basis")));
    } catch (IllegalArgumentException e) {
      throw TakeOnCommitRefusal.referenceUnresolved(ROW_CHANGED_MESSAGE);
    }
    UUID accountId = uuid(v, TrustPositionDatasetHandler.INVESTMENT_ACCOUNT_ID);
    if (accountId == null) {
      throw TakeOnCommitRefusal.referenceUnresolved(INVESTMENT_ACCOUNT_CHANGED_MESSAGE);
    }
    Customer client = null;
    Project matter = null;
    if (basis == InvestmentBasis.CLIENT_INSTRUCTION) {
      client = client(v, clientsById);
      matter = matter(client, v, matters);
    }
    String accountNumber = requiredText(v, "accountNumber");
    BigDecimal percent = requiredAmount(v, "interestRate");
    BigDecimal principal = requiredAmount(v, "principal");
    BigDecimal fund = amount(v, "fundAmount");
    return new Investment(
        row,
        accountId,
        basis,
        client == null ? null : client.getId(),
        client == null ? null : client.getName(),
        matter == null ? null : matter.getId(),
        matter == null ? null : matterName(matter),
        requiredText(v, "institution"),
        accountNumber,
        principal,
        percent,
        fraction(percent, accountNumber),
        requiredDate(v, "depositDate"),
        text(v, "instructionReference"),
        date(v, "instructionDate"),
        Boolean.TRUE.equals(v.get("endorsementConfirmed")),
        amount(v, "accruedInterest"),
        fund == null ? BigDecimal.ZERO : fund,
        requiredAmount(v, "balancePerConfirmation"));
  }

  /**
   * The stored fraction of a percent rate: {@code 7.25} → {@code 0.0725}. Refused when it needs
   * more than four decimals or is above 1 (100%) — {@code numeric(5,4)}, CHECK 0..1 — never
   * rounded.
   */
  static BigDecimal fraction(BigDecimal percent, String accountNumber) {
    BigDecimal fraction = percent.divide(HUNDRED);
    if (fraction.signum() < 0
        || fraction.compareTo(BigDecimal.ONE) > 0
        || fraction.stripTrailingZeros().scale() > 4) {
      throw TakeOnCommitRefusal.referenceUnresolved(
          ("The interest rate of investment %s (%s%%) cannot be stored. Use at most two decimals"
                  + " and no more than 100%%.")
              .formatted(accountNumber, percent.stripTrailingZeros().toPlainString()));
    }
    return fraction.setScale(4, RoundingMode.UNNECESSARY);
  }

  // --- references ---

  /** The row's client: exists and is not anonymised (R-3). */
  private Customer client(Map<String, Object> v, Map<UUID, Customer> clientsById) {
    UUID clientId = uuid(v, TrustPositionDatasetHandler.CLIENT_ID);
    if (clientId == null) {
      throw TakeOnCommitRefusal.referenceUnresolved(CLIENT_GONE_MESSAGE);
    }
    Customer client =
        clientsById.computeIfAbsent(clientId, id -> customers.findById(id).orElse(null));
    if (client == null || client.getLifecycleStatus() == LifecycleStatus.ANONYMIZED) {
      throw TakeOnCommitRefusal.referenceUnresolved(CLIENT_GONE_MESSAGE);
    }
    return client;
  }

  /** The row's matter, still one of the client's (the client-matter rule's refusal is caught). */
  private Project matter(Customer client, Map<String, Object> v, Map<String, Project> matters) {
    UUID matterId = uuid(v, TrustPositionDatasetHandler.MATTER_ID);
    if (matterId == null) {
      throw TakeOnCommitRefusal.referenceUnresolved(MATTER_CHANGED_MESSAGE);
    }
    String key = client.getId() + "|" + matterId;
    Project matter = matters.get(key);
    if (matter == null) {
      try {
        matter = clientMatterRule.requireClientMatter(client, matterId);
      } catch (InvalidStateException | ResourceNotFoundException e) {
        throw TakeOnCommitRefusal.referenceUnresolved(MATTER_CHANGED_MESSAGE);
      }
      matters.put(key, matter);
    }
    return matter;
  }

  private static String matterName(Project matter) {
    return matter.getReferenceNumber() == null || matter.getReferenceNumber().isBlank()
        ? matter.getName()
        : matter.getReferenceNumber() + " " + matter.getName();
  }

  // --- typed mapped values ---

  private static UUID uuid(Map<String, Object> v, String key) {
    String s = text(v, key);
    if (s == null) {
      return null;
    }
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String text(Map<String, Object> v, String key) {
    return v.get(key) instanceof String s && !s.isBlank() ? s.strip() : null;
  }

  private static String requiredText(Map<String, Object> v, String key) {
    String s = text(v, key);
    if (s == null) {
      throw TakeOnCommitRefusal.referenceUnresolved(ROW_CHANGED_MESSAGE);
    }
    return s;
  }

  private static BigDecimal amount(Map<String, Object> v, String key) {
    String s = text(v, key);
    try {
      return s == null ? null : new BigDecimal(s);
    } catch (NumberFormatException e) {
      throw TakeOnCommitRefusal.referenceUnresolved(ROW_CHANGED_MESSAGE);
    }
  }

  private static BigDecimal requiredAmount(Map<String, Object> v, String key) {
    BigDecimal amount = amount(v, key);
    if (amount == null) {
      throw TakeOnCommitRefusal.referenceUnresolved(ROW_CHANGED_MESSAGE);
    }
    return amount;
  }

  private static LocalDate date(Map<String, Object> v, String key) {
    String s = text(v, key);
    try {
      return s == null ? null : LocalDate.parse(s);
    } catch (java.time.format.DateTimeParseException e) {
      throw TakeOnCommitRefusal.referenceUnresolved(ROW_CHANGED_MESSAGE);
    }
  }

  private static LocalDate requiredDate(Map<String, Object> v, String key) {
    LocalDate date = date(v, key);
    if (date == null) {
      throw TakeOnCommitRefusal.referenceUnresolved(ROW_CHANGED_MESSAGE);
    }
    return date;
  }
}
