package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The target fields of every dataset and trust section (Phase 93 653A, C-13): what the mapping step
 * offers and the generic checks type. Keys are wire values (pinned by the frontend) — renaming one
 * later is a wire change. Labels are X3 / legal words, shown as they are.
 *
 * <p>Dataset semantics (lookups, statutory blocks, commit) arrive with each dataset's slice (655A,
 * 656A, 657B, 658A); a slice may add fields, never rename them.
 */
public final class TakeOnFieldCatalogue {

  /** How a field's cell text is typed by the generic checks. */
  public enum FieldType {
    TEXT,
    EMAIL,
    DATE,
    AMOUNT,
    DECIMAL,
    ENUM,
    BOOLEAN
  }

  /**
   * One target field. {@code values} are the enum names (ENUM only); {@code aliases} map an
   * accepted spelling to an enum name; {@code headerAliases} help the preview suggest a mapping.
   */
  public record FieldSpec(
      String key,
      String label,
      FieldType type,
      boolean required,
      Integer maxLength,
      List<String> values,
      Map<String, String> aliases,
      List<String> headerAliases) {}

  /** Every section carries the previous system's own id for the record (optional). */
  public static final String SOURCE_REF = "sourceRef";

  private static final FieldSpec SOURCE_REF_FIELD =
      text(SOURCE_REF, "Previous system ID", false, 200, "id", "reference", "ref", "code");

  private static final Map<TakeOnSection, List<FieldSpec>> TRUST_SECTIONS =
      Map.of(
          TakeOnSection.CLIENT_LEDGER,
          List.of(
              text("clientRef", "Client reference", true, 200, "client", "client code"),
              text("matterRef", "Matter reference", true, 200, "matter", "matter code"),
              amount("balance", "Balance at cutover", true, "balance", "amount")),
          TakeOnSection.UNIDENTIFIED_DEPOSITS,
          List.of(
              date("receivedDate", "Date received", true, "date", "deposit date"),
              amount("amount", "Amount", true),
              text("bankReference", "Bank reference", false, 200),
              text("description", "Description", false, 500)),
          TakeOnSection.INVESTMENTS,
          List.of(
              text("investmentAccount", "Investment account (number or name)", true, 200),
              enumField(
                  "basis",
                  "Basis",
                  true,
                  List.of("CLIENT_INSTRUCTION", "FIRM_DISCRETION"),
                  Map.of(
                      "S86(4)", "CLIENT_INSTRUCTION",
                      "86(4)", "CLIENT_INSTRUCTION",
                      "S86(3)", "FIRM_DISCRETION",
                      "86(3)", "FIRM_DISCRETION")),
              text("institution", "Institution", true, 200, "bank"),
              text("accountNumber", "Account number", true, 100),
              amount("principal", "Principal at cutover", true),
              new FieldSpec(
                  "interestRate",
                  "Interest rate",
                  FieldType.DECIMAL,
                  true,
                  null,
                  List.of(),
                  Map.of(),
                  List.of("rate")),
              date("depositDate", "Original deposit date", true),
              text("clientRef", "Client reference", false, 200),
              text("matterRef", "Matter reference", false, 200),
              text("instructionReference", "Instruction reference", false, 200),
              date("instructionDate", "Instruction date", false),
              new FieldSpec(
                  "endorsementConfirmed",
                  "Endorsed under section 86(4)",
                  FieldType.BOOLEAN,
                  false,
                  null,
                  List.of(),
                  Map.of(),
                  List.of("endorsed")),
              amount("accruedInterest", "Accrued interest not capitalised", false),
              amount("fundAmount", "Legal Practitioners' Fidelity Fund amount held", false),
              amount("balancePerConfirmation", "Balance per confirmation", true)),
          TakeOnSection.OUTSTANDING_ITEMS,
          List.of(
              enumField(
                  "kind",
                  "Item type",
                  true,
                  // 657B (OP-6): INVESTMENT_IN_TRANSIT is accepted only to be refused with the
                  // §17.3.7 text — investment movements are settled before the cutover date.
                  List.of("DEPOSIT_IN_TRANSIT", "UNPRESENTED_PAYMENT", "INVESTMENT_IN_TRANSIT"),
                  Map.of(
                      "DEPOSIT", "DEPOSIT_IN_TRANSIT",
                      "CHEQUE", "UNPRESENTED_PAYMENT",
                      "PAYMENT", "UNPRESENTED_PAYMENT",
                      "INVESTMENT", "INVESTMENT_IN_TRANSIT",
                      "PLACEMENT", "INVESTMENT_IN_TRANSIT",
                      "WITHDRAWAL", "INVESTMENT_IN_TRANSIT")),
              date("originalDate", "Original date", true, "date"),
              amount("amount", "Amount", true),
              text("reference", "Reference", true, 200),
              text("clientRef", "Client reference", true, 200),
              text("matterRef", "Matter reference", true, 200)));

  private static final List<FieldSpec> CLIENTS =
      List.of(
          text("name", "Client name", true, 255, "client", "full name"),
          email("email", "Email", true, "email address", "e-mail"),
          text("phone", "Phone", false, 50, "telephone", "mobile", "cell"),
          text("idNumber", "ID number", false, 100, "id no", "identity number"),
          text("registrationNumber", "Registration number", false, 100, "reg no"),
          text("taxNumber", "Tax number", false, 100, "vat number", "income tax number"),
          enumField(
              "ficaClientType",
              "Client type",
              true,
              List.of(
                  "NATURAL_PERSON",
                  "COMPANY",
                  "CLOSE_CORPORATION",
                  "TRUST",
                  "PARTNERSHIP",
                  "OTHER"),
              Map.of(
                  "INDIVIDUAL", "NATURAL_PERSON",
                  "PERSON", "NATURAL_PERSON",
                  "CC", "CLOSE_CORPORATION",
                  "PTY_LTD", "COMPANY")),
          text("addressLine1", "Address line 1", false, 255, "address", "street"),
          text("addressLine2", "Address line 2", false, 255),
          text("city", "City", false, 100, "town"),
          text("postalCode", "Postal code", false, 20, "postcode"),
          text("country", "Country", false, 2),
          date("cddCompletedDate", "CDD completed date", true, "cdd date"),
          date("lastReviewDate", "Last review date", false),
          enumField(
              "riskRating",
              "Risk rating",
              false,
              List.of("LOW", "MEDIUM", "HIGH", "UNRATED"),
              null),
          enumField(
              "beneficialOwnershipStatus",
              "Beneficial ownership",
              true,
              List.of("DONE", "NOT_APPLICABLE", "OUTSTANDING"),
              Map.of("N/A", "NOT_APPLICABLE")),
          enumField(
              "screeningStatus",
              "Screening",
              true,
              List.of("SCREENED_CLEAR", "SCREENED_MATCH", "NOT_SCREENED"),
              null),
          date("screeningDate", "Screening date", false),
          enumField(
              "sourceOfFundsStatus",
              "Source of funds",
              false,
              List.of("DONE", "NOT_REQUIRED", "OUTSTANDING"),
              null),
          text("cddRecordsLocation", "CDD records location", true, 500));

  private static final List<FieldSpec> MATTERS =
      List.of(
          text("name", "Matter name", true, 255, "matter", "matter description"),
          email("clientEmail", "Client email", false),
          text("clientRef", "Client previous-system ID", false, 200, "client code"),
          text("clientIdNumber", "Client ID number", false, 100),
          text("referenceNumber", "Matter reference", false, 100, "matter number", "file number"),
          text("description", "Description", false, 2000),
          date("dueDate", "Due date", false),
          text("workType", "Work type", false, 50, "type"),
          enumField("priority", "Priority", false, List.of("LOW", "MEDIUM", "HIGH"), null),
          email("leadMemberEmail", "Responsible attorney email", false, "attorney email"));

  private static final List<FieldSpec> DEBTOR_BALANCES =
      List.of(
          text("clientRef", "Client reference", true, 200, "client", "client code"),
          text("matterRef", "Matter reference", false, 200, "matter"),
          text("feeNoteNumber", "Fee note number", true, 20, "fee note", "number"),
          date("issueDate", "Issue date", true, "date"),
          date("dueDate", "Due date", false),
          amount("outstandingAmount", "Outstanding amount", true, "outstanding", "balance"),
          text("currency", "Currency", false, 3),
          text("description", "Description", false, 500));

  private TakeOnFieldCatalogue() {}

  /** The fields of {@code section} in catalogue order, the previous-system id last. */
  public static List<FieldSpec> fields(TakeOnDataset dataset, TakeOnSection section) {
    List<FieldSpec> base =
        switch (dataset) {
          case CLIENTS -> section == TakeOnSection.MAIN ? CLIENTS : null;
          case MATTERS -> section == TakeOnSection.MAIN ? MATTERS : null;
          case DEBTOR_BALANCES -> section == TakeOnSection.MAIN ? DEBTOR_BALANCES : null;
          case TRUST_POSITION -> TRUST_SECTIONS.get(section);
        };
    if (base == null) {
      return List.of();
    }
    var all = new ArrayList<>(base);
    all.add(SOURCE_REF_FIELD);
    return List.copyOf(all);
  }

  /** The field {@code key} of {@code section}, if the section has it. */
  public static Optional<FieldSpec> field(
      TakeOnDataset dataset, TakeOnSection section, String key) {
    return fields(dataset, section).stream().filter(f -> f.key().equals(key)).findFirst();
  }

  // --- builders ---

  private static FieldSpec text(
      String key, String label, boolean required, int maxLength, String... headerAliases) {
    return new FieldSpec(
        key,
        label,
        FieldType.TEXT,
        required,
        maxLength,
        List.of(),
        Map.of(),
        List.of(headerAliases));
  }

  private static FieldSpec email(
      String key, String label, boolean required, String... headerAliases) {
    return new FieldSpec(
        key, label, FieldType.EMAIL, required, 255, List.of(), Map.of(), List.of(headerAliases));
  }

  private static FieldSpec date(
      String key, String label, boolean required, String... headerAliases) {
    return new FieldSpec(
        key, label, FieldType.DATE, required, null, List.of(), Map.of(), List.of(headerAliases));
  }

  private static FieldSpec amount(
      String key, String label, boolean required, String... headerAliases) {
    return new FieldSpec(
        key, label, FieldType.AMOUNT, required, null, List.of(), Map.of(), List.of(headerAliases));
  }

  private static FieldSpec enumField(
      String key,
      String label,
      boolean required,
      List<String> values,
      Map<String, String> aliases) {
    return new FieldSpec(
        key,
        label,
        FieldType.ENUM,
        required,
        null,
        List.copyOf(values),
        aliases == null ? Map.of() : Map.copyOf(aliases),
        List.of());
  }
}
