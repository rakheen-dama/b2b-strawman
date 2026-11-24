package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.AMOUNT_NEGATIVE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.AMOUNT_NEGATIVE_MESSAGE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.AMOUNT_NOT_POSITIVE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.AMOUNT_NOT_POSITIVE_MESSAGE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.CLIENT_IN_DEBIT;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.CLIENT_MATTER_MISMATCH;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.CLIENT_MATTER_REQUIRED;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.CLIENT_MATTER_UNRESOLVED;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.CONFIRMATION_MISMATCH;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.CUTOVER_NOT_MONTH_END;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.CUTOVER_NOT_MONTH_END_MESSAGE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.DATE_AFTER_CUTOVER;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.FIRM_INVESTMENT_HAS_CLIENT;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.FIRM_INVESTMENT_HAS_CLIENT_MESSAGE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.FUND_LEDGER_IN_DEBIT;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.INSTRUCTION_REQUIRED;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.INSTRUCTION_REQUIRED_MESSAGE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.INVESTMENT_ACCOUNT_UNRESOLVED;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.INVESTMENT_BASIS_MISMATCH;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.INVESTMENT_IN_TRANSIT;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.INVESTMENT_IN_TRANSIT_MESSAGE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.MATTER_UNRESOLVED;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.POSSIBLE_DUPLICATE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.RATE_NEGATIVE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.RATE_NEGATIVE_MESSAGE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.REFERENCE_OR_DESCRIPTION_REQUIRED;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.REFERENCE_OR_DESCRIPTION_REQUIRED_MESSAGE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.UNIDENTIFIED_DATE_MISSING;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.UNIDENTIFIED_DATE_MISSING_MESSAGE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.UNIDENTIFIED_OVER_ONE_YEAR;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.UNIDENTIFIED_OVER_ONE_YEAR_MESSAGE;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.clientInDebit;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.confirmationMismatch;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.date;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.dateAfterCutover;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.fundLedgerInDebit;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.investmentAccountUnresolved;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.investmentBasisMismatch;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.matterMismatch;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.matterRequired;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.matterUnresolved;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.possibleDuplicateOutstanding;
import static io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages.possibleDuplicateUnidentified;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRow;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRowRepository;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSection;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSource;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnFieldCatalogue.FieldSpec;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionEquation.TrustEquationSummary;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionEquation.TrustPositionMessage;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionEquation.TrustPositionSummary;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustTakeOnPreconditions;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustClientMatterRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The trust position dataset (Phase 93 657B, §17.3.7, §17.3.9): the dry run of one trust banking
 * account's opening position at the cutover date, on top of the generic checks. Never posts and
 * never changes a client, matter, account or trust row; it is not a {@link TakeOnRowCommitter} (the
 * commit is 657C), so a trust batch is never offered COMMIT yet.
 *
 * <p>Extension model (D-103): delegation to {@link GenericTakeOnDatasetHandler} and a {@link
 * ValidationContext} subclass with every lookup made once in {@link #loadContext}.
 *
 * <ol>
 *   <li><b>Account checks first</b> (§17.3.9, {@link TrustTakeOnPreconditions}, then the month
 *       end): a failure is a batch-level error in {@code summary.accountErrors}, shown above the
 *       table, and stops further checks — the rows get the generic checks only.
 *   <li><b>Row checks per section</b>: client and matter resolve (§17.3.6, {@link
 *       TrustClientMatterRule#requireClientMatter}); the statutory row blocks (client in debit,
 *       unidentified deposit without its date, a client balance without the client's matter); the
 *       dedup keys of §17.2.3; nil balances are validated and listed (FD-33).
 *   <li><b>Batch level</b>: the account checks of every investment account the rows name, then
 *       {@link TrustPositionEquation} — EQ-1, the items tie, EQ-2, the investments tie and the
 *       deficit test, whose failures are batch-level hard blocks ({@link #batchBlockCount}); a Fund
 *       ledger in debit and unidentified deposits over a year old are flags, not blocks.
 * </ol>
 *
 * <p>Never lets an exception escape through a {@code @Transactional} proxy (D-25): the
 * client-matter rule's refusal is caught at the call.
 */
@Component
public class TrustPositionDatasetHandler implements TakeOnDatasetHandler {

  /** Derived values added to a row's mapped values (the D-159 shape). */
  public static final String CLIENT_ID = MattersDatasetHandler.CLIENT_ID;

  public static final String CLIENT_RESOLVED_BY = MattersDatasetHandler.CLIENT_RESOLVED_BY;
  public static final String MATTER_ID = "matterId";
  public static final String INVESTMENT_ACCOUNT_ID = "investmentAccountId";

  private static final String DEPOSIT_IN_TRANSIT = "DEPOSIT_IN_TRANSIT";
  private static final String UNPRESENTED_PAYMENT = "UNPRESENTED_PAYMENT";
  private static final String INVESTMENT_IN_TRANSIT_KIND = "INVESTMENT_IN_TRANSIT";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** The trust dry-run context: the account checks, the lookups and the running sums. */
  static final class TrustPositionValidationContext extends ValidationContext {

    LocalDate cutover;
    UUID accountId;
    boolean accountChecksPassed;
    final List<TrustPositionMessage> accountErrors = new ArrayList<>();

    // clients (live: not anonymised)
    final Map<String, UUID> clientByEmail = new HashMap<>();
    final Map<String, List<UUID>> clientsByIdNumber = new HashMap<>();
    final Set<UUID> liveClients = new HashSet<>();
    final Map<String, Optional<UUID>> clientBySourceRef = new HashMap<>();
    final Map<UUID, Optional<Customer>> customerById = new HashMap<>();

    // matters
    final Map<String, UUID> matterByRef = new HashMap<>();
    final Set<UUID> matters = new HashSet<>();
    final Map<String, Optional<UUID>> matterBySourceRef = new HashMap<>();
    final Map<String, Boolean> clientsMatter = new HashMap<>();

    // investment accounts
    final Map<String, TrustAccount> investmentByNumber = new HashMap<>();
    final Map<String, TrustAccount> investmentByName = new HashMap<>();
    final Map<UUID, TrustAccount> namedInvestmentAccounts = new TreeMap<>();

    // possible duplicates (warnings, never errors)
    final Map<String, Integer> unidentifiedSeen = new HashMap<>();
    final Map<String, Integer> outstandingSeen = new HashMap<>();

    // sums over every row whose amount typed (D-184)
    BigDecimal clients = BigDecimal.ZERO;
    BigDecimal unidentified = BigDecimal.ZERO;
    BigDecimal section863 = BigDecimal.ZERO;
    BigDecimal section864 = BigDecimal.ZERO;
    BigDecimal fundAmounts = BigDecimal.ZERO;
    BigDecimal confirmations = BigDecimal.ZERO;
    BigDecimal listedDeposits = BigDecimal.ZERO;
    BigDecimal listedPayments = BigDecimal.ZERO;
    boolean unidentifiedOverOneYear;

    // batch-level result (after validateBatchLevel)
    List<TrustPositionMessage> blocks = List.of();
    final List<TrustPositionMessage> warnings = new ArrayList<>();
    final List<String> flags = new ArrayList<>();
    TrustEquationSummary equation;

    TrustPositionValidationContext(ValidationContext base) {
      super(base);
    }

    TrustPositionEquation.Sums sums() {
      return new TrustPositionEquation.Sums(
          clients,
          unidentified,
          section863,
          section864,
          fundAmounts,
          confirmations,
          listedDeposits,
          listedPayments);
    }
  }

  private final GenericTakeOnDatasetHandler generic;
  private final TrustAccountRepository trustAccounts;
  private final TrustTakeOnPreconditions preconditions;
  private final CustomerRepository customers;
  private final ProjectRepository projects;
  private final TakeOnRowRepository takeOnRows;
  private final TrustClientMatterRule clientMatterRule;

  public TrustPositionDatasetHandler(
      GenericTakeOnDatasetHandler generic,
      TrustAccountRepository trustAccounts,
      TrustTakeOnPreconditions preconditions,
      CustomerRepository customers,
      ProjectRepository projects,
      TakeOnRowRepository takeOnRows,
      TrustClientMatterRule clientMatterRule) {
    this.generic = generic;
    this.trustAccounts = trustAccounts;
    this.preconditions = preconditions;
    this.customers = customers;
    this.projects = projects;
    this.takeOnRows = takeOnRows;
    this.clientMatterRule = clientMatterRule;
  }

  @Override
  public TakeOnDataset dataset() {
    return TakeOnDataset.TRUST_POSITION;
  }

  @Override
  public List<FieldSpec> fields(TakeOnDataset dataset, TakeOnSection section) {
    return generic.fields(dataset, section);
  }

  // --- loadContext: the account checks on the banking account, then the lookups ---

  @Override
  public ValidationContext loadContext(TakeOnBatch batch, List<TakeOnSource> sources) {
    var context = new TrustPositionValidationContext(generic.loadContext(batch, sources));
    LocalDate cutover = batch.getCutoverDate(); // never null: the dry run refused a missing date
    context.cutover = cutover;
    context.accountId = batch.getTrustAccountId();
    var account =
        trustAccounts
            .findById(batch.getTrustAccountId())
            .orElseThrow(
                () -> new IllegalStateException("A trust take-on names no trust account."));

    preconditions
        .firstFailure(account, cutover, TrustTakeOnPreconditions.Kind.BANKING, batch.getId())
        .ifPresent(f -> context.accountErrors.add(new TrustPositionMessage(f.code(), f.message())));
    if (context.accountErrors.isEmpty()
        && !cutover.equals(YearMonth.from(cutover).atEndOfMonth())
        && !batch.isMidMonthAcknowledged()) {
      context.accountErrors.add(
          new TrustPositionMessage(CUTOVER_NOT_MONTH_END, CUTOVER_NOT_MONTH_END_MESSAGE));
    }
    context.accountChecksPassed = context.accountErrors.isEmpty();
    if (!context.accountChecksPassed) {
      return context; // stopping further checks: no lookups needed
    }

    for (var c : customers.findTakeOnDedupCandidates()) {
      context.liveClients.add(c.getId());
      if (c.getEmail() != null) {
        context.clientByEmail.putIfAbsent(lower(c.getEmail()), c.getId());
      }
      if (c.getIdNumber() != null && !c.getIdNumber().isBlank()) {
        context
            .clientsByIdNumber
            .computeIfAbsent(lower(c.getIdNumber()), k -> new ArrayList<>())
            .add(c.getId());
      }
    }
    for (var m : projects.findTakeOnDedupCandidates()) {
      context.matters.add(m.getId());
      if (m.getReferenceNumber() != null && !m.getReferenceNumber().isBlank()) {
        context.matterByRef.putIfAbsent(lower(m.getReferenceNumber()), m.getId());
      }
    }
    for (var a : trustAccounts.findAll()) {
      if (a.getAccountType() != TrustAccountType.INVESTMENT) {
        continue;
      }
      if (a.getAccountNumber() != null && !a.getAccountNumber().isBlank()) {
        context.investmentByNumber.putIfAbsent(lower(a.getAccountNumber()), a);
      }
      if (a.getAccountName() != null && !a.getAccountName().isBlank()) {
        context.investmentByName.putIfAbsent(lower(a.getAccountName()), a);
      }
    }
    return context;
  }

  // --- mapAndValidate: the row checks per section ---

  @Override
  public void mapAndValidate(TakeOnRow row, ValidationContext base) {
    var context = (TrustPositionValidationContext) base;
    var check = generic.checkGeneric(row, context);
    if (context.accountChecksPassed) {
      switch (row.getSection()) {
        case CLIENT_LEDGER -> clientLedger(check, context);
        case UNIDENTIFIED_DEPOSITS -> unidentifiedDeposit(row, check, context);
        case INVESTMENTS -> investment(row, check, context);
        case OUTSTANDING_ITEMS -> outstandingItem(row, check, context);
        default -> {
          // MAIN is not a trust section (the upload refuses it)
        }
      }
    }
    GenericTakeOnDatasetHandler.finish(row, check, context);
  }

  /** Client and matter balances: the matter is mandatory and the client's; never in debit. */
  private void clientLedger(TakeOnRowCheck check, TrustPositionValidationContext context) {
    var mapped = check.mapped();
    String clientRef = text(mapped, "clientRef");
    String matterRef = text(mapped, "matterRef");
    UUID clientId = clientRef == null ? null : resolveClientOrError(check, context, clientRef);

    UUID matterId = null;
    boolean clientsMatter = false;
    if (matterRef == null) {
      if (removeErrors(check, "matterRef", GenericTakeOnDatasetHandler.REQUIRED)) {
        check.addBlock(CLIENT_MATTER_REQUIRED, "matterRef", matterRequired(clientRef));
      }
    } else {
      matterId = resolveMatter(context, matterRef);
      if (matterId == null) {
        check.addBlock(CLIENT_MATTER_UNRESOLVED, "matterRef", matterUnresolved(matterRef));
      } else {
        mapped.put(MATTER_ID, matterId.toString());
        if (clientId != null) {
          clientsMatter = isClientsMatter(context, clientId, matterId);
          if (!clientsMatter) {
            check.addBlock(
                CLIENT_MATTER_MISMATCH, "matterRef", matterMismatch(matterRef, clientRef));
          }
        }
      }
    }

    BigDecimal balance = amount(mapped, "balance");
    if (balance != null) {
      if (balance.signum() < 0) {
        check.addBlock(CLIENT_IN_DEBIT, "balance", clientInDebit(balance));
      }
      context.clients = context.clients.add(balance); // nil: validated and listed (FD-33)
    }

    if (clientsMatter) {
      check.dedupKey(
          new DedupKey(
              "matterRef",
              TakeOnDedupKeys.trustClientLedger(context.accountId, clientId, matterId)));
    }
  }

  /** Held unidentified deposits, with the date the money was received (section 87(4)). */
  private void unidentifiedDeposit(
      TakeOnRow row, TakeOnRowCheck check, TrustPositionValidationContext context) {
    var mapped = check.mapped();
    LocalDate received = date(mapped, "receivedDate");
    if (received == null) {
      // blank, unparsable or an Excel error: the statutory block replaces the typing error
      removeErrors(check, "receivedDate", null);
      check.addBlock(UNIDENTIFIED_DATE_MISSING, "receivedDate", UNIDENTIFIED_DATE_MISSING_MESSAGE);
    } else {
      if (received.isAfter(context.cutover)) {
        check.addError(
            DATE_AFTER_CUTOVER, "receivedDate", dateAfterCutover(received, context.cutover));
      }
      if (received.isBefore(context.cutover.minusYears(1))) {
        check.addWarning(
            UNIDENTIFIED_OVER_ONE_YEAR, "receivedDate", UNIDENTIFIED_OVER_ONE_YEAR_MESSAGE);
        context.unidentifiedOverOneYear = true;
      }
    }

    BigDecimal amount = amount(mapped, "amount");
    if (amount != null) {
      if (amount.signum() <= 0) {
        check.addError(AMOUNT_NOT_POSITIVE, "amount", AMOUNT_NOT_POSITIVE_MESSAGE);
      }
      context.unidentified = context.unidentified.add(amount);
    }

    String reference = text(mapped, "bankReference");
    if (reference == null
        && text(mapped, "description") == null
        && !hasError(check, "bankReference")
        && !hasError(check, "description")) {
      check.addError(
          REFERENCE_OR_DESCRIPTION_REQUIRED,
          "bankReference",
          REFERENCE_OR_DESCRIPTION_REQUIRED_MESSAGE);
    }

    check.dedupKey(
        new DedupKey(
            TakeOnFieldCatalogue.SOURCE_REF,
            TakeOnDedupKeys.trustUnidentified(
                context.accountId,
                text(mapped, TakeOnFieldCatalogue.SOURCE_REF),
                row.getBatchId(),
                row.getRowNumber())));

    if (received != null && amount != null) {
      String key =
          received + "|" + plain(amount) + "|" + (reference == null ? "" : lower(reference));
      Integer first = context.unidentifiedSeen.putIfAbsent(key, row.getRowNumber());
      if (first != null) {
        check.addWarning(POSSIBLE_DUPLICATE, "bankReference", possibleDuplicateUnidentified(first));
      }
    }
  }

  /** Section 86(3) and 86(4) investments, each in its investment account. */
  private void investment(
      TakeOnRow row, TakeOnRowCheck check, TrustPositionValidationContext context) {
    var mapped = check.mapped();
    String basis = text(mapped, "basis");

    String accountRef = text(mapped, "investmentAccount");
    TrustAccount account =
        accountRef == null ? null : resolveInvestmentAccount(context, accountRef);
    if (accountRef != null && account == null) {
      check.addError(
          INVESTMENT_ACCOUNT_UNRESOLVED,
          "investmentAccount",
          investmentAccountUnresolved(accountRef));
    }
    if (account != null) {
      mapped.put(INVESTMENT_ACCOUNT_ID, account.getId().toString());
      context.namedInvestmentAccounts.put(account.getId(), account);
      InvestmentBasis held = account.getInvestmentBasis();
      if (basis != null && held != null && !held.name().equals(basis)) {
        check.addError(
            INVESTMENT_BASIS_MISMATCH,
            "basis",
            investmentBasisMismatch(
                account.getAccountName(), section(held.name()), section(basis)));
      }
    }

    BigDecimal principal = amount(mapped, "principal");
    if (principal != null && principal.signum() <= 0) {
      check.addError(AMOUNT_NOT_POSITIVE, "principal", AMOUNT_NOT_POSITIVE_MESSAGE);
    }
    LocalDate deposited = date(mapped, "depositDate");
    if (deposited != null && deposited.isAfter(context.cutover)) {
      check.addError(
          DATE_AFTER_CUTOVER, "depositDate", dateAfterCutover(deposited, context.cutover));
    }
    BigDecimal fundAmount = amount(mapped, "fundAmount");
    if (fundAmount != null && fundAmount.signum() < 0) {
      check.addError(AMOUNT_NEGATIVE, "fundAmount", AMOUNT_NEGATIVE_MESSAGE);
    }
    BigDecimal accrued = amount(mapped, "accruedInterest");
    if (accrued != null && accrued.signum() < 0) {
      check.addError(AMOUNT_NEGATIVE, "accruedInterest", AMOUNT_NEGATIVE_MESSAGE);
    }
    BigDecimal rate = amount(mapped, "interestRate");
    if (rate != null && rate.signum() < 0) {
      check.addError(RATE_NEGATIVE, "interestRate", RATE_NEGATIVE_MESSAGE);
    }

    String clientRef = text(mapped, "clientRef");
    String matterRef = text(mapped, "matterRef");
    if (InvestmentBasis.CLIENT_INSTRUCTION.name().equals(basis)) {
      // s86(4): the client's own investment — client and matter resolve (errors, not blocks)
      UUID clientId = null;
      if (clientRef == null) {
        requiredError(row, check, context, "clientRef");
      } else {
        clientId = resolveClientOrError(check, context, clientRef);
      }
      if (matterRef == null) {
        requiredError(row, check, context, "matterRef");
      } else {
        UUID matterId = resolveMatter(context, matterRef);
        if (matterId == null) {
          check.addError(MATTER_UNRESOLVED, "matterRef", matterUnresolved(matterRef));
        } else {
          mapped.put(MATTER_ID, matterId.toString());
          if (clientId != null && !isClientsMatter(context, clientId, matterId)) {
            check.addError(
                CLIENT_MATTER_MISMATCH, "matterRef", matterMismatch(matterRef, clientRef));
          }
        }
      }
      if (text(mapped, "instructionReference") == null
          || date(mapped, "instructionDate") == null
          || !Boolean.TRUE.equals(mapped.get("endorsementConfirmed"))) {
        check.addError(INSTRUCTION_REQUIRED, "instructionReference", INSTRUCTION_REQUIRED_MESSAGE);
      }
    } else if (InvestmentBasis.FIRM_DISCRETION.name().equals(basis)
        && (clientRef != null || matterRef != null)) {
      // s86(3): the firm's own placement names no client (OP-7)
      check.addError(
          FIRM_INVESTMENT_HAS_CLIENT,
          clientRef != null ? "clientRef" : "matterRef",
          FIRM_INVESTMENT_HAS_CLIENT_MESSAGE);
    }

    BigDecimal confirmation = amount(mapped, "balancePerConfirmation");
    BigDecimal fund = fundAmount == null ? BigDecimal.ZERO : fundAmount;
    if (principal != null
        && confirmation != null
        && principal.add(fund).compareTo(confirmation) != 0) {
      // accrued, uncapitalised interest is a memo and not part of the balance (A5, FD-13)
      check.addError(
          CONFIRMATION_MISMATCH,
          "balancePerConfirmation",
          confirmationMismatch(principal, fund, confirmation));
    }

    String institution = text(mapped, "institution");
    String accountNumber = text(mapped, "accountNumber");
    if (account != null && institution != null && accountNumber != null) {
      check.dedupKey(
          new DedupKey(
              "accountNumber",
              TakeOnDedupKeys.trustInvestment(account.getId(), institution, accountNumber)));
    }

    if (confirmation != null) {
      context.confirmations = context.confirmations.add(confirmation);
    }
    if (fundAmount != null) {
      context.fundAmounts = context.fundAmounts.add(fundAmount);
    }
    if (principal != null && InvestmentBasis.FIRM_DISCRETION.name().equals(basis)) {
      context.section863 = context.section863.add(principal);
    } else if (principal != null && InvestmentBasis.CLIENT_INSTRUCTION.name().equals(basis)) {
      context.section864 = context.section864.add(principal);
    }
  }

  /**
   * A deposit not yet cleared or a payment not yet presented at cutover, on the client's matter.
   */
  private void outstandingItem(
      TakeOnRow row, TakeOnRowCheck check, TrustPositionValidationContext context) {
    var mapped = check.mapped();
    String kind = text(mapped, "kind");
    if (INVESTMENT_IN_TRANSIT_KIND.equals(kind)) {
      check.addError(INVESTMENT_IN_TRANSIT, "kind", INVESTMENT_IN_TRANSIT_MESSAGE);
    }
    LocalDate original = date(mapped, "originalDate");
    if (original != null && original.isAfter(context.cutover)) {
      check.addError(
          DATE_AFTER_CUTOVER, "originalDate", dateAfterCutover(original, context.cutover));
    }
    BigDecimal amount = amount(mapped, "amount");
    if (amount != null && amount.signum() <= 0) {
      check.addError(AMOUNT_NOT_POSITIVE, "amount", AMOUNT_NOT_POSITIVE_MESSAGE);
    }

    // FD-18: every item names the client and one of the client's matters (errors, not blocks)
    String clientRef = text(mapped, "clientRef");
    String matterRef = text(mapped, "matterRef");
    UUID clientId = clientRef == null ? null : resolveClientOrError(check, context, clientRef);
    if (matterRef != null) {
      UUID matterId = resolveMatter(context, matterRef);
      if (matterId == null) {
        check.addError(MATTER_UNRESOLVED, "matterRef", matterUnresolved(matterRef));
      } else {
        mapped.put(MATTER_ID, matterId.toString());
        if (clientId != null && !isClientsMatter(context, clientId, matterId)) {
          check.addError(CLIENT_MATTER_MISMATCH, "matterRef", matterMismatch(matterRef, clientRef));
        }
      }
    }

    if (kind != null) {
      check.dedupKey(
          new DedupKey(
              TakeOnFieldCatalogue.SOURCE_REF,
              TakeOnDedupKeys.trustOutstanding(
                  context.accountId,
                  kind,
                  text(mapped, TakeOnFieldCatalogue.SOURCE_REF),
                  row.getBatchId(),
                  row.getRowNumber())));
      String reference = text(mapped, "reference");
      if (reference != null && amount != null) {
        String key = kind + "|" + lower(reference) + "|" + plain(amount);
        Integer first = context.outstandingSeen.putIfAbsent(key, row.getRowNumber());
        if (first != null) {
          check.addWarning(POSSIBLE_DUPLICATE, "reference", possibleDuplicateOutstanding(first));
        }
      }
    }

    if (amount != null && DEPOSIT_IN_TRANSIT.equals(kind)) {
      context.listedDeposits = context.listedDeposits.add(amount);
    } else if (amount != null && UNPRESENTED_PAYMENT.equals(kind)) {
      context.listedPayments = context.listedPayments.add(amount);
    }
  }

  // --- validateBatchLevel: the investment accounts, then the equation ---

  @Override
  public DedupKey dedupKey(TakeOnDataset dataset, Map<String, Object> mapped) {
    return generic.dedupKey(dataset, mapped); // trust keys need resolved ids: set per row above
  }

  @Override
  public void validateBatchLevel(TakeOnBatch batch, ValidationContext base) {
    var context = (TrustPositionValidationContext) base;
    generic.validateBatchLevel(batch, context); // in-file duplicates
    if (!context.accountChecksPassed) {
      return;
    }
    for (TrustAccount account : context.namedInvestmentAccounts.values()) {
      preconditions
          .firstFailure(
              account, context.cutover, TrustTakeOnPreconditions.Kind.INVESTMENT, batch.getId())
          .ifPresent(
              f -> context.accountErrors.add(new TrustPositionMessage(f.code(), f.message())));
    }
    if (!context.accountErrors.isEmpty()) {
      return;
    }

    var result =
        TrustPositionEquation.compute(
            new TrustPositionEquation.Inputs(
                batch.getBankBalance(),
                batch.getOutstandingDepositsTotal(),
                batch.getOutstandingPaymentsTotal(),
                batch.getCashbookBalance(),
                batch.getFundLedgerBalance(),
                batch.getInvestedTotal()),
            context.sums());
    context.equation = result.equation();
    context.blocks = result.blocks();
    if (result.fundInDebit()) {
      context.warnings.add(
          new TrustPositionMessage(
              FUND_LEDGER_IN_DEBIT, fundLedgerInDebit(batch.getFundLedgerBalance())));
      context.flags.add(FUND_LEDGER_IN_DEBIT);
    }
    if (context.unidentifiedOverOneYear) {
      context.flags.add(UNIDENTIFIED_OVER_ONE_YEAR);
    }
  }

  @Override
  public int batchBlockCount(ValidationContext base) {
    return ((TrustPositionValidationContext) base).blocks.size();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> summary(ValidationContext base) {
    var context = (TrustPositionValidationContext) base;
    var summary =
        new TrustPositionSummary(
            List.copyOf(context.accountErrors),
            List.copyOf(context.blocks),
            List.copyOf(context.warnings),
            List.copyOf(context.flags),
            context.equation);
    return JSON.convertValue(summary, Map.class);
  }

  // --- resolution ---

  /**
   * The client of {@code clientRef}: email (when it holds an {@code @}) → the previous system's id
   * of a client taken on by an earlier clients batch → ID number (exactly one live client); the
   * first that resolves wins (§17.3.6, D-157). None → an ERROR with the matters dry run's text.
   */
  private UUID resolveClientOrError(
      TakeOnRowCheck check, TrustPositionValidationContext context, String clientRef) {
    String key = lower(clientRef);
    if (clientRef.contains("@")) {
      UUID byEmail = context.clientByEmail.get(key);
      if (byEmail != null) {
        return resolved(check, byEmail, "email");
      }
    }
    UUID bySource =
        context
            .clientBySourceRef
            .computeIfAbsent(
                key,
                k ->
                    takeOnRows
                        .clientBySourceRef(clientRef.strip())
                        .filter(context.liveClients::contains))
            .orElse(null);
    if (bySource != null) {
      return resolved(check, bySource, "previousSystemId");
    }
    var byIdNumber = context.clientsByIdNumber.getOrDefault(key, List.of());
    if (byIdNumber.size() == 1) {
      return resolved(check, byIdNumber.getFirst(), "idNumber");
    }
    if (byIdNumber.size() > 1) {
      check.addError(
          MattersDatasetHandler.CLIENT_AMBIGUOUS,
          "clientRef",
          MattersDatasetHandler.ambiguousIdNumberMessage(clientRef));
    } else {
      check.addError(
          MattersDatasetHandler.CLIENT_UNRESOLVED,
          "clientRef",
          MattersDatasetHandler.orphanMessage(clientRef));
    }
    return null;
  }

  private static UUID resolved(TakeOnRowCheck check, UUID clientId, String by) {
    check.mapped().put(CLIENT_ID, clientId.toString());
    check.mapped().put(CLIENT_RESOLVED_BY, by);
    return clientId;
  }

  /**
   * The matter of {@code matterRef}: its reference number, else the previous system's id of a
   * matter taken on by an earlier matters batch (that still exists).
   */
  private UUID resolveMatter(TrustPositionValidationContext context, String matterRef) {
    String key = lower(matterRef);
    UUID byRef = context.matterByRef.get(key);
    if (byRef != null) {
      return byRef;
    }
    return context
        .matterBySourceRef
        .computeIfAbsent(
            key,
            k -> takeOnRows.matterBySourceRef(matterRef.strip()).filter(context.matters::contains))
        .orElse(null);
  }

  /**
   * Whether the matter is one of the client's ({@link TrustClientMatterRule#requireClientMatter}:
   * the matter's client, or a client linked to it). The rule is a plain component that throws its
   * refusal; caught here, so nothing escapes a transactional proxy (D-25).
   */
  private boolean isClientsMatter(
      TrustPositionValidationContext context, UUID clientId, UUID matterId) {
    return context.clientsMatter.computeIfAbsent(
        clientId + "|" + matterId,
        k -> {
          var client =
              context.customerById.computeIfAbsent(clientId, customers::findById).orElse(null);
          if (client == null) {
            return false;
          }
          try {
            clientMatterRule.requireClientMatter(client, matterId);
            return true;
          } catch (InvalidStateException | ResourceNotFoundException e) {
            return false;
          }
        });
  }

  /** An INVESTMENT trust account by account number, else by name. */
  private static TrustAccount resolveInvestmentAccount(
      TrustPositionValidationContext context, String reference) {
    String key = lower(reference);
    var byNumber = context.investmentByNumber.get(key);
    return byNumber != null ? byNumber : context.investmentByName.get(key);
  }

  // --- helpers ---

  /** The generic "{Field} is required." on a field the catalogue keeps optional for s86(3). */
  private static void requiredError(
      TakeOnRow row, TakeOnRowCheck check, TrustPositionValidationContext context, String field) {
    if (hasError(check, field)) {
      return; // a typing error is already there
    }
    boolean xlsx =
        context.source(row.getSourceId()).source().getFileFormat() == TakeOnSource.FileFormat.XLSX;
    var spec =
        TakeOnFieldCatalogue.field(TakeOnDataset.TRUST_POSITION, TakeOnSection.INVESTMENTS, field)
            .orElseThrow();
    check.addError(
        GenericTakeOnDatasetHandler.REQUIRED,
        field,
        GenericTakeOnDatasetHandler.requiredMessage(spec, xlsx));
  }

  /** Removes the errors on {@code field} (of {@code code}, or every code when null). */
  private static boolean removeErrors(TakeOnRowCheck check, String field, String code) {
    return check
        .errors()
        .removeIf(
            e -> field.equals(e.get("field")) && (code == null || code.equals(e.get("code"))));
  }

  private static boolean hasError(TakeOnRowCheck check, String field) {
    return check.errors().stream().anyMatch(e -> field.equals(e.get("field")));
  }

  private static String section(String basis) {
    return InvestmentBasis.FIRM_DISCRETION.name().equals(basis) ? "86(3)" : "86(4)";
  }

  private static String text(Map<String, Object> mapped, String key) {
    return mapped.get(key) instanceof String s && !s.isBlank() ? s.strip() : null;
  }

  private static BigDecimal amount(Map<String, Object> mapped, String key) {
    return mapped.get(key) instanceof String s && !s.isBlank() ? new BigDecimal(s) : null;
  }

  private static LocalDate date(Map<String, Object> mapped, String key) {
    return mapped.get(key) instanceof String s && !s.isBlank() ? LocalDate.parse(s) : null;
  }

  private static String plain(BigDecimal amount) {
    return amount.stripTrailingZeros().toPlainString();
  }

  private static String lower(String value) {
    return value.strip().toLowerCase(Locale.ROOT);
  }
}
