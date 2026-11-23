package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.compliance.BeneficialOwnershipStatus;
import io.b2mash.b2b.b2bstrawman.compliance.ClientFicaAttestationService;
import io.b2mash.b2b.b2bstrawman.compliance.ClientFicaAttestationService.AttestedCdd;
import io.b2mash.b2b.b2bstrawman.compliance.ClientFicaAttestationService.BatchAttestation;
import io.b2mash.b2b.b2bstrawman.compliance.ClientFicaAttestationService.ReviewIntervals;
import io.b2mash.b2b.b2bstrawman.compliance.FicaClientType;
import io.b2mash.b2b.b2bstrawman.compliance.FicaRiskRating;
import io.b2mash.b2b.b2bstrawman.compliance.FicaScreeningStatus;
import io.b2mash.b2b.b2bstrawman.compliance.SourceOfFundsStatus;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.TakenOnCustomerService;
import io.b2mash.b2b.b2bstrawman.customer.TakenOnCustomerService.TakenOnClient;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRow;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRowStatus;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSection;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOff;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOffKind;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSource;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnFieldCatalogue.FieldSpec;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * The clients dataset (Phase 93 655A, §17.3.3): the clients dry run on top of the generic checks,
 * and the per-row commit through the one documented ACTIVE entry path ({@link
 * TakenOnCustomerService#createTakenOn}).
 *
 * <p>Extension model (653A F-15): <b>delegation</b> to {@link GenericTakeOnDatasetHandler} for the
 * generic checks, plus a {@link ValidationContext} subclass built with the copy constructor that
 * holds the existing-client lookups and the in-file name keys.
 *
 * <p>Validation: email required (FD-22 text) and not a placeholder; the screening date unless not
 * screened; ISO 3166-1 alpha-2 country; an address warning; dedup (1) email, case-insensitive, (2)
 * name + tax number, (3) name + ID number — against existing clients ({@code duplicate_of_id}) and
 * against earlier rows of the file ({@code DUPLICATE_IN_FILE}, D-29, C-19). Blank risk rating ⇒
 * {@code UNRATED}; blank source of funds ⇒ {@code NOT_REQUIRED}; a refused value is not replaced by
 * either (OBS-655A-2).
 */
@Component
public class ClientsDatasetHandler
    implements TakeOnDatasetHandler,
        TakeOnRowCommitter<ClientsDatasetHandler.ClientsCommitContext> {

  /** FD-22 (X3): a client without an email address cannot be created in Kazi. */
  public static final String EMAIL_REQUIRED_MESSAGE =
      "An email address is required to create a client in Kazi";

  public static final String ADDRESS_MESSAGE =
      "This client has no address; fee notes cannot be generated for them until one is added"
          + " (statements and brought-forward balances are unaffected)";

  public static final String EMAIL_TAKEN_MESSAGE =
      "A client with this email address already exists";

  public static final String EMAIL_PLACEHOLDER = "EMAIL_PLACEHOLDER";
  public static final String COUNTRY_INVALID = "COUNTRY_INVALID";
  public static final String ADDRESS_MISSING = "ADDRESS_MISSING";

  static final String CUSTOM_FIELD_REFUSED_MESSAGE =
      "A custom field value was refused. Correct it in the previous system, then take it on in a"
          + " new batch.";

  static final String ROW_FAILED_MESSAGE =
      "This row could not be committed. Correct it in the previous system, then take it on in a"
          + " new batch.";

  /** The {@code take_on_rows.result_entity_type} of a client (ADR-342 storage key). */
  public static final String RESULT_ENTITY_TYPE = "customer";

  /** The ISO 3166-1 alpha-2 country codes (no user-assigned code such as {@code ZZ}). */
  private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

  /**
   * The FICA columns the attestation step summarises (catalogue keys), counted at validation into
   * {@code take_on_batches.summary} so the browser never pages the rows (§17.3.3).
   */
  static final List<String> FICA_SUMMARY_FIELDS =
      List.of(
          "ficaClientType",
          "riskRating",
          "beneficialOwnershipStatus",
          "screeningStatus",
          "sourceOfFundsStatus");

  /** Local parts that stand in for "no email" in practice-management exports (FD-22). */
  private static final Set<String> PLACEHOLDER_LOCAL_PARTS =
      Set.of(
          "noemail",
          "no-email",
          "no.email",
          "no_email",
          "nomail",
          "no-mail",
          "none",
          "na",
          "n.a",
          "unknown",
          "noreply",
          "no-reply",
          "placeholder");

  /** The clients dry-run context: existing-client lookups and the in-file name keys. */
  static final class ClientsValidationContext extends ValidationContext {

    final Map<String, UUID> clientByEmail = new HashMap<>();
    final Map<String, UUID> clientByNameAndTax = new HashMap<>();
    final Map<String, UUID> clientByNameAndId = new HashMap<>();
    final Map<String, Integer> firstRowByNameKey = new HashMap<>();
    final List<InFileDuplicate> nameDuplicates = new ArrayList<>();
    final List<TakeOnRow> rows = new ArrayList<>();

    ClientsValidationContext(ValidationContext base) {
      super(base);
    }
  }

  /** What every row of one clients commit run reads (loaded under the batch lock). */
  public record ClientsCommitContext(
      UUID batchId, UUID actorMemberId, BatchAttestation attestation, ReviewIntervals intervals) {}

  private final GenericTakeOnDatasetHandler generic;
  private final CustomerRepository customers;
  private final TakenOnCustomerService takenOnCustomers;
  private final ClientFicaAttestationService attestations;

  public ClientsDatasetHandler(
      GenericTakeOnDatasetHandler generic,
      CustomerRepository customers,
      TakenOnCustomerService takenOnCustomers,
      ClientFicaAttestationService attestations) {
    this.generic = generic;
    this.customers = customers;
    this.takenOnCustomers = takenOnCustomers;
    this.attestations = attestations;
  }

  @Override
  public TakeOnDataset dataset() {
    return TakeOnDataset.CLIENTS;
  }

  @Override
  public List<FieldSpec> fields(TakeOnDataset dataset, TakeOnSection section) {
    return generic.fields(dataset, section);
  }

  @Override
  public ValidationContext loadContext(TakeOnBatch batch, List<TakeOnSource> sources) {
    var context = new ClientsValidationContext(generic.loadContext(batch, sources));
    for (var c : customers.findTakeOnDedupCandidates()) {
      if (c.getEmail() != null) {
        context.clientByEmail.putIfAbsent(lower(c.getEmail()), c.getId());
      }
      if (c.getName() != null && c.getTaxNumber() != null && !c.getTaxNumber().isBlank()) {
        context.clientByNameAndTax.putIfAbsent(nameKey(c.getName(), c.getTaxNumber()), c.getId());
      }
      if (c.getName() != null && c.getIdNumber() != null && !c.getIdNumber().isBlank()) {
        context.clientByNameAndId.putIfAbsent(nameKey(c.getName(), c.getIdNumber()), c.getId());
      }
    }
    return context;
  }

  @Override
  public void mapAndValidate(TakeOnRow row, ValidationContext base) {
    var context = (ClientsValidationContext) base;
    context.rows.add(row);
    var check = generic.checkGeneric(row, context);
    var mapped = check.mapped();

    // FD-22: the email message is the take-on's own, and placeholders are refused.
    check.errors().stream()
        .filter(e -> GenericTakeOnDatasetHandler.REQUIRED.equals(e.get("code")))
        .filter(e -> "email".equals(e.get("field")))
        .forEach(e -> e.put("message", EMAIL_REQUIRED_MESSAGE));
    if (mapped.get("email") instanceof String email && isPlaceholder(email)) {
      mapped.remove("email");
      check.addError(EMAIL_PLACEHOLDER, "email", EMAIL_REQUIRED_MESSAGE);
    }

    if (mapped.get("country") instanceof String country) {
      String iso = country.strip().toUpperCase(Locale.ROOT);
      if (ISO_COUNTRIES.contains(iso)) {
        mapped.put("country", iso);
      } else {
        check.addError(
            COUNTRY_INVALID,
            "country",
            "Enter the country as its two-letter ISO code (for example ZA), or leave it blank.");
      }
    }

    Object screening = mapped.get("screeningStatus");
    boolean screeningDateRefused =
        check.errors().stream().anyMatch(e -> "screeningDate".equals(e.get("field")));
    if (screening != null
        && !FicaScreeningStatus.NOT_SCREENED.name().equals(screening)
        && mapped.get("screeningDate") == null
        && !screeningDateRefused) {
      check.addError(
          GenericTakeOnDatasetHandler.REQUIRED,
          "screeningDate",
          "Screening date is required unless the client was not screened.");
    }
    defaultIfBlank(row, context, mapped, "riskRating", FicaRiskRating.UNRATED.name());
    defaultIfBlank(
        row, context, mapped, "sourceOfFundsStatus", SourceOfFundsStatus.NOT_REQUIRED.name());

    if (blank(mapped.get("addressLine1"))
        || blank(mapped.get("city"))
        || blank(mapped.get("country"))) {
      check.addWarning(ADDRESS_MISSING, "addressLine1", ADDRESS_MESSAGE);
    }

    var dedup = generic.dedupKey(TakeOnDataset.CLIENTS, mapped);
    check.dedupKey(dedup);
    String name = mapped.get("name") instanceof String n ? n : null;
    String tax = text(mapped.get("taxNumber"));
    String idNumber = text(mapped.get("idNumber"));
    if (check.errors().isEmpty()) {
      UUID existing = null;
      if (mapped.get("email") instanceof String email) {
        existing = context.clientByEmail.get(lower(email));
      }
      if (existing == null && name != null && tax != null) {
        existing = context.clientByNameAndTax.get(nameKey(name, tax));
      }
      if (existing == null && name != null && idNumber != null) {
        existing = context.clientByNameAndId.get(nameKey(name, idNumber));
      }
      if (existing != null) {
        check.duplicateOf(existing);
      }
    }

    // Record once (applyValidation), then the in-file keys: email first, then name + tax / ID.
    check.applyTo(row);
    boolean emailRepeat =
        dedup != null && context.recordDedupKey(dedup.key(), row, dedup.field()) != null;
    if (name != null) {
      boolean repeated = emailRepeat;
      if (tax != null) {
        repeated |=
            recordNameKey(context, row, "nametax:" + nameKey(name, tax), "taxNumber", repeated);
      }
      if (idNumber != null) {
        recordNameKey(context, row, "nameid:" + nameKey(name, idNumber), "idNumber", repeated);
      }
    }
  }

  /**
   * The documented default of an optional FICA field, applied only when the file gave nothing — the
   * cell blank and no batch default. A value the generic checks refused stays absent (the row is an
   * error that quotes it), so the row never shows a substituted value the file did not hold
   * (OBS-655A-2). The other FICA fields are required and have no default.
   */
  private static void defaultIfBlank(
      TakeOnRow row,
      ValidationContext context,
      Map<String, Object> mapped,
      String key,
      String fallback) {
    if (mapped.containsKey(key)) {
      return;
    }
    var source = context.source(row.getSourceId());
    String header = source.targets().get(key);
    if (header != null && !blank(row.getRawValues().get(header))) {
      return; // refused
    }
    if (!blank(source.defaults().get(key))) {
      return; // the batch default was refused
    }
    mapped.put(key, fallback);
  }

  /** Records an in-file name key; a repeat is reported once per row (D-29 semantics). */
  private static boolean recordNameKey(
      ClientsValidationContext context,
      TakeOnRow row,
      String key,
      String field,
      boolean alreadyReported) {
    Integer first = context.firstRowByNameKey.putIfAbsent(key, row.getRowNumber());
    if (first == null) {
      return false;
    }
    if (!alreadyReported) {
      context.nameDuplicates.add(new ValidationContext.InFileDuplicate(row, field, first));
    }
    return true;
  }

  @Override
  public DedupKey dedupKey(TakeOnDataset dataset, Map<String, Object> mapped) {
    return generic.dedupKey(dataset, mapped);
  }

  @Override
  public void validateBatchLevel(TakeOnBatch batch, ValidationContext base) {
    generic.validateBatchLevel(batch, base);
    var context = (ClientsValidationContext) base;
    for (var duplicate : context.nameDuplicates) {
      duplicate
          .row()
          .addError(
              GenericTakeOnDatasetHandler.DUPLICATE_IN_FILE,
              duplicate.field(),
              "Duplicate of row %d in this file".formatted(duplicate.firstRowNumber()));
    }
  }

  /**
   * {@code {clients: n, fica: {field: {value: rows}}}} over the rows that will become clients —
   * {@code VALIDATED} and not matching an existing client — for the attestation step (B-H2).
   * Computed after the batch-level checks, so the statuses are final.
   */
  @Override
  public Map<String, Object> summary(ValidationContext base) {
    var context = (ClientsValidationContext) base;
    var fica = new LinkedHashMap<String, Map<String, Integer>>();
    FICA_SUMMARY_FIELDS.forEach(field -> fica.put(field, new LinkedHashMap<>()));
    int clients = 0;
    for (TakeOnRow row : context.rows) {
      if (row.getStatus() != TakeOnRowStatus.VALIDATED || row.getDuplicateOfId() != null) {
        continue;
      }
      clients++;
      for (String field : FICA_SUMMARY_FIELDS) {
        String value = text(row.getMappedValues().get(field));
        if (value != null) {
          fica.get(field).merge(value, 1, Integer::sum);
        }
      }
    }
    var summary = new LinkedHashMap<String, Object>(generic.summary(context));
    summary.put("clients", clients);
    summary.put("fica", fica);
    return summary;
  }

  // --- Commit (§17.3.3 per-row steps 1–4) ---

  @Override
  public TakeOnSignOffKind requiredSignOff() {
    return TakeOnSignOffKind.FICA_ATTESTATION;
  }

  @Override
  public ClientsCommitContext prepare(TakeOnBatch batch, TakeOnSignOff signOff) {
    Objects.requireNonNull(signOff, "signOff");
    return new ClientsCommitContext(
        batch.getId(),
        RequestScopes.requireMemberId(),
        new BatchAttestation(
            batch.getId(), signOff.getId(), signOff.getMemberId(), signOff.getSignedAt()),
        attestations.reviewIntervals());
  }

  @Override
  public void commitRow(TakeOnRow row, ClientsCommitContext context) {
    Map<String, Object> v = row.getMappedValues();
    String name = text(v.get("name"));
    // 1. Duplicates: the validated match (if it still exists), then name + tax / name + ID again —
    //    a client may have been created in the UI since the dry run. An email clash is step 2
    // (C-7).
    UUID duplicate = row.getDuplicateOfId();
    if (duplicate != null && customers.existsById(duplicate)) {
      row.markSkippedDuplicate(RESULT_ENTITY_TYPE, duplicate);
      return;
    }
    String tax = text(v.get("taxNumber"));
    String idNumber = text(v.get("idNumber"));
    var byName =
        tax != null ? customers.findIdByNameAndTaxNumber(name, tax) : Optional.<UUID>empty();
    if (byName.isEmpty() && idNumber != null) {
      byName = customers.findIdByNameAndIdNumber(name, idNumber);
    }
    if (byName.isPresent()) {
      row.markSkippedDuplicate(RESULT_ENTITY_TYPE, byName.get());
      return;
    }

    // 2. The client, born ACTIVE through the one documented entry path.
    var ficaType = FicaClientType.valueOf(text(v.get("ficaClientType")));
    var outcome =
        takenOnCustomers.createTakenOn(
            new TakenOnClient(
                name,
                text(v.get("email")),
                text(v.get("phone")),
                idNumber,
                ficaType.customerType(),
                text(v.get("registrationNumber")),
                tax,
                text(v.get("addressLine1")),
                text(v.get("addressLine2")),
                text(v.get("city")),
                text(v.get("postalCode")),
                text(v.get("country")),
                map(v.get(GenericTakeOnDatasetHandler.CUSTOM_FIELDS_KEY)),
                map(v.get(GenericTakeOnDatasetHandler.PARKED_KEY))),
            context.batchId(),
            context.actorMemberId());
    if (outcome.emailTaken()) {
      row.markFailed(EMAIL_TAKEN_MESSAGE);
      return;
    }
    var client = outcome.created();

    // 3. The client's FICA attestation, from the row's columns and the batch attestation.
    var attestation =
        attestations.attest(
            client.getId(),
            new AttestedCdd(
                date(v.get("cddCompletedDate")),
                date(v.get("lastReviewDate")),
                FicaRiskRating.valueOf(text(v.get("riskRating"))),
                ficaType,
                BeneficialOwnershipStatus.valueOf(text(v.get("beneficialOwnershipStatus"))),
                FicaScreeningStatus.valueOf(text(v.get("screeningStatus"))),
                date(v.get("screeningDate")),
                SourceOfFundsStatus.valueOf(text(v.get("sourceOfFundsStatus"))),
                text(v.get("cddRecordsLocation")),
                sourceSystemRef(row)),
            context.attestation(),
            row.getId(),
            context.intervals());

    // 4. The row is done.
    row.markCreated(
        RESULT_ENTITY_TYPE,
        client.getId(),
        Map.of("attestationId", attestation.getId().toString()));
  }

  @Override
  public String failureMessage(RuntimeException failure) {
    if (failure instanceof DataIntegrityViolationException integrity
        && String.valueOf(integrity.getMostSpecificCause().getMessage())
            .contains("uq_customers_email")) {
      return EMAIL_TAKEN_MESSAGE; // created in the UI while this row was committing
    }
    if (failure instanceof InvalidStateException) {
      // Fixed text: the validator's detail may quote the cell, and a row's failure reason is not
      // redacted on anonymisation (it must stay value-free).
      return CUSTOM_FIELD_REFUSED_MESSAGE;
    }
    return ROW_FAILED_MESSAGE;
  }

  // --- helpers ---

  /** The previous system's own id, when the file gave one (not the "row:n" fallback). */
  private static String sourceSystemRef(TakeOnRow row) {
    Object ref = row.getMappedValues().get(TakeOnFieldCatalogue.SOURCE_REF);
    return ref instanceof String s && !s.isBlank() ? s : null;
  }

  static boolean isPlaceholder(String email) {
    int at = email.indexOf('@');
    String local = (at < 0 ? email : email.substring(0, at)).toLowerCase(Locale.ROOT);
    return PLACEHOLDER_LOCAL_PARTS.contains(local);
  }

  private static String nameKey(String name, String number) {
    return lower(name.strip()) + "|" + number.strip().toLowerCase(Locale.ROOT);
  }

  private static String lower(String s) {
    return s.toLowerCase(Locale.ROOT);
  }

  private static boolean blank(Object value) {
    return value == null || value.toString().isBlank();
  }

  private static String text(Object value) {
    return value == null || value.toString().isBlank() ? null : value.toString();
  }

  private static LocalDate date(Object value) {
    String s = text(value);
    return s == null ? null : LocalDate.parse(s);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : null;
  }
}
