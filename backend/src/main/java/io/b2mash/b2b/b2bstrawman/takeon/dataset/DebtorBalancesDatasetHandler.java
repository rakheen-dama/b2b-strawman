package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.invoice.BroughtForwardFeeNoteService;
import io.b2mash.b2b.b2bstrawman.invoice.BroughtForwardFeeNoteService.BroughtForwardFeeNote;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceNumberService;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteContext;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteService;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.settings.BillingSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRow;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRowRepository;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSection;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOff;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSource;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnFieldCatalogue.FieldSpec;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustClientMatterRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The debtor opening balances dataset (Phase 93 658A, §17.3.10, ADR-390): fee notes the previous
 * system issued and the client still owes at the cutover date, brought forward into Kazi quietly
 * through {@link BroughtForwardFeeNoteService#issue}, one row per transaction.
 *
 * <p>Extension model (D-103): delegation to {@link GenericTakeOnDatasetHandler}, plus a {@link
 * ValidationContext} subclass holding the client, matter and firm lookups. The fee-note number is
 * looked up per row ({@code InvoiceRepository.findByInvoiceNumberIgnoreCase}), at the dry run and
 * again at the commit.
 *
 * <p>Validation: a client reference (email → previous-system ID → ID number, as the trust dataset
 * resolves it), a fee-note number, an issue date on or before the cutover date and an outstanding
 * amount above zero. An optional matter must be the client's; a blank due date defaults to the
 * issue date plus the firm's payment terms (warning); a currency must be the firm's. The source
 * number is kept (FD-20): one already in Kazi is an error, except an earlier brought-forward fee
 * note of the same client and amount, which is a duplicate (a VOID one is not: its number stays
 * with it, D-254). A number in Kazi's own {@code INV-n} format is a warning: Kazi's own numbers
 * continue after it (D-253). A client without a billing address or email is only a warning — {@code
 * INVOICE_GENERATION} is not applied to a brought-forward fee note (FD-21). The client's legacy
 * status and lifecycle are checked as every fee note's are.
 *
 * <p>No sign-off: {@link #requiredSignOff()} is not overridden (the matters shape, D-155).
 */
@Component
public class DebtorBalancesDatasetHandler
    implements TakeOnDatasetHandler,
        TakeOnRowCommitter<DebtorBalancesDatasetHandler.DebtorsCommitContext> {

  /** The {@code take_on_rows.result_entity_type} of a fee note (ADR-342 storage key). */
  public static final String RESULT_ENTITY_TYPE = "invoice";

  /** Derived values added to a row's mapped values (the D-159 shape). */
  public static final String CLIENT_ID = MattersDatasetHandler.CLIENT_ID;

  public static final String CLIENT_RESOLVED_BY = MattersDatasetHandler.CLIENT_RESOLVED_BY;
  public static final String MATTER_ID = TrustPositionDatasetHandler.MATTER_ID;

  public static final String CLIENT_NOT_ACTIVE = "CLIENT_NOT_ACTIVE";
  public static final String CLIENT_LIFECYCLE = MattersDatasetHandler.CLIENT_LIFECYCLE;
  public static final String FEE_NOTE_NUMBER_EXISTS = "FEE_NOTE_NUMBER_EXISTS";
  public static final String CURRENCY_MISMATCH = "CURRENCY_MISMATCH";
  public static final String DUE_DATE_DEFAULTED = "DUE_DATE_DEFAULTED";
  public static final String CLIENT_DETAILS_MISSING = "CLIENT_DETAILS_MISSING";
  public static final String NUMBER_CONTINUES_SEQUENCE = "NUMBER_CONTINUES_SEQUENCE";

  /** FD-21: the address / email warning (copy, RG). */
  public static final String CLIENT_DETAILS_MISSING_MESSAGE =
      "This client has no billing address or email in Kazi. The brought-forward fee note is taken"
          + " on as it is; add them before generating a new fee note for this client.";

  /**
   * The client resolved at validation is gone (deleted or anonymised) by the commit. Fixed text — a
   * row's failure reason never quotes a cell value (D-102, D-171).
   */
  public static final String CLIENT_GONE_MESSAGE =
      "The client of this fee note is no longer in Kazi. Take the client on first, then take this"
          + " fee note on in a new batch.";

  /** A fee note with the row's number appeared in Kazi after the dry run (fixed text, D-102). */
  public static final String NUMBER_TAKEN_AT_COMMIT_MESSAGE =
      "A fee note with this number is already in Kazi. Correct it in the previous system, then take"
          + " it on in a new batch.";

  /**
   * The row's matter is no longer the client's at the commit (it was moved to another client after
   * the dry run). Fixed text (D-102, D-256).
   */
  public static final String MATTER_MOVED_MESSAGE =
      "The matter of this fee note is no longer a matter of its client in Kazi. Correct the matter,"
          + " then take this fee note on in a new batch.";

  /** The firm's currency changed between the dry run and the commit. Fixed text (D-102, D-255). */
  public static final String CURRENCY_CHANGED_MESSAGE =
      "The firm's currency changed after the dry run. Take this fee note on in a new batch.";

  /** D-253 (copy): a source number in Kazi's own format. */
  public static String numberContinuesSequence(String number) {
    return "Kazi's own fee-note numbers will continue after %s.".formatted(number);
  }

  /** FD-20, §17.3.10 verbatim. */
  public static String numberExists(String number) {
    return "Fee-note number %s already exists in Kazi".formatted(number);
  }

  /** S-4 (copy, RG). */
  public static String matterNotTheClients(String matterRef, String clientRef) {
    return "Matter %s is not a matter of client %s.".formatted(matterRef, clientRef);
  }

  /** S-8 (copy, RG). */
  public static String currencyMismatch(String firmCurrency, String given) {
    return "Take on fee notes in %s only. This fee note is in %s.".formatted(firmCurrency, given);
  }

  /** FD-26 (copy, RG). */
  public static String dueDateDefaulted(LocalDate dueDate, int termsDays) {
    return "No due date was given. It is set to %s, the issue date plus the firm's payment terms of %d days."
        .formatted(TrustPositionMessages.date(dueDate), termsDays);
  }

  /** The values a row keeps once its client is anonymised ({@code TakeOnRowRepository}). */
  private static final String REDACTED = "redacted";

  /** A refusal of the client checks every fee note needs: its code and the service's own text. */
  record ClientRefusal(String code, String message) {}

  /** The debtors dry-run context: the firm, client and matter lookups. */
  static final class DebtorsValidationContext extends ValidationContext {

    LocalDate cutover;
    String firmCurrency;
    int termsDays;

    /** lower(email) → client (live: not anonymised). */
    final Map<String, UUID> clientByEmail = new HashMap<>();

    /** lower(strip(ID number)) → clients. */
    final Map<String, List<UUID>> clientsByIdNumber = new HashMap<>();

    final Set<UUID> liveClients = new HashSet<>();
    final Map<String, Optional<UUID>> clientBySourceRef = new HashMap<>();

    /** lower(strip(reference number)) → matter. */
    final Map<String, UUID> matterByRef = new HashMap<>();

    final Set<UUID> matters = new HashSet<>();
    final Map<String, Optional<UUID>> matterBySourceRef = new HashMap<>();
    final Map<String, Boolean> clientsMatter = new HashMap<>();
    final Map<UUID, Optional<Customer>> customerById = new HashMap<>();
    final Map<UUID, Optional<ClientRefusal>> clientRefusal = new HashMap<>();
    final Map<UUID, Boolean> detailsMissing = new HashMap<>();

    DebtorsValidationContext(ValidationContext base) {
      super(base);
    }
  }

  /** What every row of one debtors commit run reads; {@code asAt} is the batch cutover date. */
  public record DebtorsCommitContext(UUID batchId, UUID actorMemberId, LocalDate asAt) {}

  private final GenericTakeOnDatasetHandler generic;
  private final CustomerRepository customers;
  private final ProjectRepository projects;
  private final TakeOnRowRepository takeOnRows;
  private final TrustClientMatterRule clientMatterRule;
  private final PrerequisiteService prerequisites;
  private final OrgSettingsRepository orgSettings;
  private final InvoiceRepository invoices;
  private final BroughtForwardFeeNoteService broughtForwardFeeNotes;

  public DebtorBalancesDatasetHandler(
      GenericTakeOnDatasetHandler generic,
      CustomerRepository customers,
      ProjectRepository projects,
      TakeOnRowRepository takeOnRows,
      TrustClientMatterRule clientMatterRule,
      PrerequisiteService prerequisites,
      OrgSettingsRepository orgSettings,
      InvoiceRepository invoices,
      BroughtForwardFeeNoteService broughtForwardFeeNotes) {
    this.generic = generic;
    this.customers = customers;
    this.projects = projects;
    this.takeOnRows = takeOnRows;
    this.clientMatterRule = clientMatterRule;
    this.prerequisites = prerequisites;
    this.orgSettings = orgSettings;
    this.invoices = invoices;
    this.broughtForwardFeeNotes = broughtForwardFeeNotes;
  }

  @Override
  public TakeOnDataset dataset() {
    return TakeOnDataset.DEBTOR_BALANCES;
  }

  @Override
  public List<FieldSpec> fields(TakeOnDataset dataset, TakeOnSection section) {
    return generic.fields(dataset, section);
  }

  @Override
  public ValidationContext loadContext(TakeOnBatch batch, List<TakeOnSource> sources) {
    var context = new DebtorsValidationContext(generic.loadContext(batch, sources));
    context.cutover = batch.getCutoverDate(); // never null: the dry run refused a missing date
    var settings = orgSettings.findForCurrentTenant();
    context.firmCurrency =
        settings.map(OrgSettings::getDefaultCurrency).orElse(OrgSettingsService.DEFAULT_CURRENCY);
    // The expression InvoiceTransitionService.approve uses for the firm's payment terms.
    context.termsDays =
        settings
            .map(s -> s.getBilling().getDefaultPaymentTermsDays())
            .orElse(BillingSettings.DEFAULT_PAYMENT_TERMS_DAYS);
    for (var c : customers.findTakeOnDedupCandidates()) {
      context.liveClients.add(c.getId());
      if (c.getEmail() != null) {
        context.clientByEmail.putIfAbsent(lower(c.getEmail().strip()), c.getId());
      }
      if (c.getIdNumber() != null && !c.getIdNumber().isBlank()) {
        context
            .clientsByIdNumber
            .computeIfAbsent(lower(c.getIdNumber().strip()), k -> new ArrayList<>())
            .add(c.getId());
      }
    }
    for (var m : projects.findTakeOnDedupCandidates()) {
      context.matters.add(m.getId());
      if (m.getReferenceNumber() != null && !m.getReferenceNumber().isBlank()) {
        context.matterByRef.putIfAbsent(lower(m.getReferenceNumber().strip()), m.getId());
      }
    }
    return context;
  }

  @Override
  public void mapAndValidate(TakeOnRow row, ValidationContext base) {
    var context = (DebtorsValidationContext) base;
    if (Boolean.TRUE.equals(row.getRawValues().get(REDACTED))) {
      // S-14: its client was anonymised after the upload — nothing left to check; one clean error
      var redacted = new TakeOnRowCheck(new LinkedHashMap<>(Map.of(REDACTED, true)), null);
      redacted.addError(
          TrustPositionMessages.ROW_REDACTED, null, TrustPositionMessages.ROW_REDACTED_MESSAGE);
      GenericTakeOnDatasetHandler.finish(row, redacted, context);
      return;
    }
    var check = generic.checkGeneric(row, context);
    var mapped = check.mapped();

    // 1. The client, and the checks every fee note's client must pass (not INVOICE_GENERATION).
    String clientRef = text(mapped, "clientRef");
    UUID clientId = clientRef == null ? null : resolveClientOrError(check, context, clientRef);
    if (clientId != null) {
      var refusal = context.clientRefusal.computeIfAbsent(clientId, this::clientRefusal);
      if (refusal.isPresent()) {
        check.addError(refusal.get().code(), "clientRef", refusal.get().message());
      } else if (context.detailsMissing.computeIfAbsent(clientId, this::detailsMissing)) {
        check.addWarning(CLIENT_DETAILS_MISSING, "clientRef", CLIENT_DETAILS_MISSING_MESSAGE);
      }
    }

    // 2. The matter (optional): it must exist and be the client's.
    String matterRef = text(mapped, "matterRef");
    if (matterRef != null) {
      UUID matterId = resolveMatter(context, matterRef);
      if (matterId == null) {
        check.addError(
            TrustPositionMessages.MATTER_UNRESOLVED,
            "matterRef",
            TrustPositionMessages.matterUnresolved(matterRef));
      } else {
        mapped.put(MATTER_ID, matterId.toString());
        if (clientId != null && !isClientsMatter(context, clientId, matterId)) {
          check.addError(
              TrustPositionMessages.CLIENT_MATTER_MISMATCH,
              "matterRef",
              matterNotTheClients(matterRef, clientRef));
        }
      }
    }

    // 3. The amount is the balance still owed: more than nil.
    BigDecimal amount = amount(mapped, "outstandingAmount");
    if (amount != null && amount.signum() <= 0) {
      check.addError(
          TrustPositionMessages.AMOUNT_NOT_POSITIVE,
          "outstandingAmount",
          TrustPositionMessages.AMOUNT_NOT_POSITIVE_MESSAGE);
    }

    // 4. The fee note was issued by the previous system, on or before the cutover date.
    LocalDate issueDate = date(mapped, "issueDate");
    if (issueDate != null && issueDate.isAfter(context.cutover)) {
      check.addError(
          TrustPositionMessages.DATE_AFTER_CUTOVER,
          "issueDate",
          TrustPositionMessages.dateAfterCutover(issueDate, context.cutover));
    }

    // 5. The firm's currency only (blank means the firm's).
    String currency = text(mapped, "currency");
    if (currency == null) {
      mapped.put("currency", context.firmCurrency);
    } else if (!currency.equalsIgnoreCase(context.firmCurrency)) {
      check.addError(
          CURRENCY_MISMATCH, "currency", currencyMismatch(context.firmCurrency, currency));
    } else {
      mapped.put("currency", context.firmCurrency);
    }

    // 6. A blank due date: the issue date plus the firm's payment terms (FD-26), stored so the
    //    hash covers it and the commit reads it.
    if (mapped.get("dueDate") == null && issueDate != null) {
      LocalDate due = issueDate.plusDays(context.termsDays);
      mapped.put("dueDate", due.toString());
      check.addWarning(DUE_DATE_DEFAULTED, "dueDate", dueDateDefaulted(due, context.termsDays));
    }

    // 7. Dedup: "number:" + lower(number); a number already in Kazi (FD-20).
    check.dedupKey(generic.dedupKey(TakeOnDataset.DEBTOR_BALANCES, mapped));
    String number = text(mapped, "feeNoteNumber");
    if (number != null) {
      var existing = invoices.findByInvoiceNumberIgnoreCase(number);
      if (!existing.isEmpty()) {
        var duplicate =
            existing.stream()
                .filter(i -> isBroughtForwardDuplicate(i, clientId, amount))
                .findFirst();
        if (duplicate.isPresent()) {
          if (check.errors().isEmpty()) {
            check.duplicateOf(duplicate.get().getId());
          }
        } else {
          check.addError(FEE_NOTE_NUMBER_EXISTS, "feeNoteNumber", numberExists(number));
        }
      } else if (InvoiceNumberService.sequenceValueOf(number).isPresent()) {
        check.addWarning(
            NUMBER_CONTINUES_SEQUENCE, "feeNoteNumber", numberContinuesSequence(number));
      }
    }

    GenericTakeOnDatasetHandler.finish(row, check, context);
  }

  /**
   * An earlier brought-forward fee note of the same client and amount (FD-20's duplicate) — not a
   * VOID one: its number stays with it, so a re-import is the number-exists error (D-254).
   */
  private static boolean isBroughtForwardDuplicate(
      Invoice invoice, UUID clientId, BigDecimal amount) {
    return invoice.getTakeOnBatchId() != null
        && invoice.getStatus() != InvoiceStatus.VOID
        && clientId != null
        && clientId.equals(invoice.getCustomerId())
        && amount != null
        && invoice.getTotal() != null
        && invoice.getTotal().compareTo(amount) == 0;
  }

  /**
   * The legacy-status and lifecycle checks of {@code validateInvoicePrerequisites} (§17.3.10 step
   * 1), as a refusal instead of a throw — the invoice domain's one rule and text ({@link
   * BroughtForwardFeeNoteService#clientRefusal}), so nothing escapes a transactional proxy (D-25).
   */
  private Optional<ClientRefusal> clientRefusal(UUID clientId) {
    var client = customers.findById(clientId).orElse(null);
    if (client == null) {
      return Optional.empty(); // a live candidate of this snapshot; never absent here
    }
    return refusalOf(client);
  }

  private Optional<ClientRefusal> refusalOf(Customer client) {
    return broughtForwardFeeNotes
        .clientRefusal(client)
        .map(
            r ->
                new ClientRefusal(
                    r.notActive() ? CLIENT_NOT_ACTIVE : CLIENT_LIFECYCLE, r.message()));
  }

  /**
   * FD-21: whether the client would fail the {@code INVOICE_GENERATION} prerequisite — a warning
   * only. Called for a client this snapshot resolved (the check throws only for a missing client).
   */
  private boolean detailsMissing(UUID clientId) {
    return !prerequisites
        .checkForContext(PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, clientId)
        .passed();
  }

  // --- resolution (one rule across take-on: TrustPositionDatasetHandler, D-157) ---

  /**
   * The client of {@code clientRef}: email (when it holds an {@code @}) → the previous system's id
   * of a client taken on by an earlier clients batch → ID number (exactly one live client); the
   * first that resolves wins. None → an ERROR with the matters dry run's text.
   */
  private UUID resolveClientOrError(
      TakeOnRowCheck check, DebtorsValidationContext context, String clientRef) {
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

  /** The matter's reference number, else the previous system's id of a matter taken on earlier. */
  private UUID resolveMatter(DebtorsValidationContext context, String matterRef) {
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

  /** {@link TrustClientMatterRule#requireClientMatter}, its refusal caught (D-25). */
  private boolean isClientsMatter(DebtorsValidationContext context, UUID clientId, UUID matterId) {
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

  @Override
  public DedupKey dedupKey(TakeOnDataset dataset, Map<String, Object> mapped) {
    return generic.dedupKey(dataset, mapped);
  }

  @Override
  public void validateBatchLevel(TakeOnBatch batch, ValidationContext context) {
    generic.validateBatchLevel(batch, context);
  }

  @Override
  public Map<String, Object> summary(ValidationContext context) {
    return generic.summary(context);
  }

  // --- Commit (§17.3.10; no sign-off: requiredSignOff() stays null) ---

  @Override
  public DebtorsCommitContext prepare(TakeOnBatch batch, TakeOnSignOff signOff) {
    return new DebtorsCommitContext(
        batch.getId(), RequestScopes.requireMemberId(), batch.getCutoverDate());
  }

  /**
   * One brought-forward fee note. Every refusal is checked first and recorded without throwing: an
   * exception thrown through a {@code @Transactional} proxy marks the row's transaction
   * rollback-only (C-4, D-164). A residual exception (e.g. the unique number index losing a race)
   * rolls the row back and the engine marks it FAILED with {@link #failureMessage}.
   */
  @Override
  public void commitRow(TakeOnRow row, DebtorsCommitContext context) {
    Map<String, Object> v = row.getMappedValues();
    UUID clientId = uuid(v.get(CLIENT_ID));
    if (clientId == null) {
      // S-14: the row was redacted because its client was anonymised after the dry run — read
      // nothing else from its values.
      row.markFailed(CLIENT_GONE_MESSAGE);
      return;
    }
    String number = text(v, "feeNoteNumber");
    BigDecimal amount = amount(v, "outstandingAmount");

    // 1. The number again, by the dry run's rule (FD-20).
    var existing = invoices.findByInvoiceNumberIgnoreCase(number);
    if (!existing.isEmpty()) {
      var duplicate =
          existing.stream().filter(i -> isBroughtForwardDuplicate(i, clientId, amount)).findFirst();
      if (duplicate.isPresent()) {
        row.markSkippedDuplicate(RESULT_ENTITY_TYPE, duplicate.get().getId());
      } else {
        row.markFailed(NUMBER_TAKEN_AT_COMMIT_MESSAGE);
      }
      return;
    }

    // 2. The client still exists and may still take a fee note.
    var client =
        customers
            .findById(clientId)
            .filter(c -> c.getLifecycleStatus() != LifecycleStatus.ANONYMIZED)
            .orElse(null);
    if (client == null) {
      row.markFailed(CLIENT_GONE_MESSAGE);
      return;
    }
    var refusal = refusalOf(client);
    if (refusal.isPresent()) {
      row.markFailed(refusal.get().message());
      return;
    }

    // 3. The matter is still the client's (D-256): it may have moved since the dry run.
    UUID matterId = uuid(v.get(MATTER_ID));
    if (matterId != null) {
      try {
        clientMatterRule.requireClientMatter(client, matterId);
      } catch (InvalidStateException | ResourceNotFoundException e) {
        row.markFailed(MATTER_MOVED_MESSAGE);
        return;
      }
    }

    // 4. The firm's currency is still the one the row was validated in (D-255).
    String validatedCurrency = text(v, "currency");
    if (validatedCurrency != null
        && !validatedCurrency.equalsIgnoreCase(broughtForwardFeeNotes.firmCurrency())) {
      row.markFailed(CURRENCY_CHANGED_MESSAGE);
      return;
    }

    // 5. The fee note, issued quietly.
    var spec =
        new BroughtForwardFeeNote(
            clientId,
            matterId,
            number,
            date(v, "issueDate"),
            date(v, "dueDate"),
            amount,
            text(v, TakeOnFieldCatalogue.SOURCE_REF),
            context.asAt(),
            text(v, "description"),
            map(v.get(GenericTakeOnDatasetHandler.CUSTOM_FIELDS_KEY)),
            map(v.get(GenericTakeOnDatasetHandler.PARKED_KEY)));
    Invoice invoice =
        broughtForwardFeeNotes.issue(spec, context.batchId(), context.actorMemberId());
    row.markCreated(RESULT_ENTITY_TYPE, invoice.getId(), null);
  }

  /** S-24: always the fixed text — a lifecycle refusal is not a custom-field refusal. */
  @Override
  public String failureMessage(RuntimeException failure) {
    return GenericTakeOnDatasetHandler.ROW_FAILED_MESSAGE;
  }

  // --- helpers ---

  private static String lower(String s) {
    return s.strip().toLowerCase(Locale.ROOT);
  }

  private static String text(Map<String, Object> mapped, String key) {
    return mapped.get(key) instanceof String s && !s.isBlank() ? s.strip() : null;
  }

  private static BigDecimal amount(Map<String, Object> mapped, String key) {
    return mapped.get(key) instanceof String s && !s.isBlank() ? new BigDecimal(s.strip()) : null;
  }

  private static LocalDate date(Map<String, Object> mapped, String key) {
    return mapped.get(key) instanceof String s && !s.isBlank() ? LocalDate.parse(s.strip()) : null;
  }

  private static UUID uuid(Object value) {
    return value instanceof String s && !s.isBlank() ? UUID.fromString(s.strip()) : null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : null;
  }
}
