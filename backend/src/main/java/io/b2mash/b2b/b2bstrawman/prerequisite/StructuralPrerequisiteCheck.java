package io.b2mash.b2b.b2bstrawman.prerequisite;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Evaluates structural prerequisite checks for promoted entity columns. Maps {@link
 * PrerequisiteContext} values to a list of entity field null-checks on promoted columns. Unlike
 * custom field checks (which read from JSONB), structural checks verify that required entity
 * columns are populated.
 *
 * <p>For backward compatibility during the JSONB-to-entity migration, each field is checked in BOTH
 * the entity column AND the JSONB custom fields map. A violation is only produced if NEITHER source
 * has a non-blank value.
 */
public final class StructuralPrerequisiteCheck {

  private StructuralPrerequisiteCheck() {}

  /**
   * A single field check definition: the field slug, a display name for error messages, and a
   * getter to extract the value from the entity.
   */
  private record FieldCheck(
      String fieldSlug, String displayName, Function<Customer, Object> getter) {}

  /**
   * Required fields for INVOICE_GENERATION context.
   *
   * <p><strong>GAP-L-62 (hybrid B + C):</strong> {@code tax_number} is NOT a hard-block
   * prerequisite for draft creation. It is collected as a soft warning during draft creation
   * (logged + surfaced to the caller as a warning code) and hard-enforced only at invoice-send time
   * via {@link io.b2mash.b2b.b2bstrawman.invoice.InvoiceValidationService#validateInvoiceSend}.
   * This matches SARS semantics (the tax number is needed on the issued invoice, not the draft) and
   * lets firms capture time/billing against prospects without first chasing a tax number.
   */
  private static final List<FieldCheck> INVOICE_GENERATION_FIELDS =
      List.of(
          new FieldCheck("address_line1", "Address Line 1", Customer::getAddressLine1),
          new FieldCheck("city", "City", Customer::getCity),
          new FieldCheck("country", "Country", Customer::getCountry));

  /** Required fields for PROPOSAL_SEND context. */
  private static final List<FieldCheck> PROPOSAL_SEND_FIELDS =
      List.of(
          new FieldCheck("contact_name", "Contact Name", Customer::getContactName),
          new FieldCheck("contact_email", "Contact Email", Customer::getContactEmail),
          new FieldCheck("address_line1", "Address Line 1", Customer::getAddressLine1));

  /**
   * Required fields for LIFECYCLE_ACTIVATION context. An active customer must have a full billing
   * address AND a tax number — the tax number is still mandatory for activation because an ACTIVE
   * customer is invoiceable and the tax-number prerequisite is strictly enforced at send time. Not
   * aliased to {@link #INVOICE_GENERATION_FIELDS} so that relaxing draft creation (GAP-L-62) does
   * not leak into activation semantics.
   */
  private static final List<FieldCheck> LIFECYCLE_ACTIVATION_FIELDS =
      List.of(
          new FieldCheck("address_line1", "Address Line 1", Customer::getAddressLine1),
          new FieldCheck("city", "City", Customer::getCity),
          new FieldCheck("country", "Country", Customer::getCountry),
          new FieldCheck("tax_number", "Tax Number", Customer::getTaxNumber));

  /**
   * Fields that are enforced only at <em>invoice send</em> (not at draft creation). Today this is a
   * single-element list (tax number); kept as a list so future send-only prerequisites can be added
   * without changing call sites. Soft-warned at draft, hard-blocked at send. See GAP-L-62.
   */
  private static final List<FieldCheck> INVOICE_SEND_ONLY_FIELDS =
      List.of(new FieldCheck("tax_number", "Tax Number", Customer::getTaxNumber));

  /** Maps prerequisite contexts to their required field checks. */
  private static final Map<PrerequisiteContext, List<FieldCheck>> CONTEXT_FIELDS =
      Map.of(
          PrerequisiteContext.INVOICE_GENERATION, INVOICE_GENERATION_FIELDS,
          PrerequisiteContext.PROPOSAL_SEND, PROPOSAL_SEND_FIELDS,
          PrerequisiteContext.LIFECYCLE_ACTIVATION, LIFECYCLE_ACTIVATION_FIELDS);

  /**
   * Checks the customer entity for null promoted fields required by the given context. For backward
   * compatibility, checks both the entity column and the JSONB custom fields map. A violation is
   * only produced if neither source has a non-blank value.
   *
   * @param customer the customer entity to check
   * @param context the prerequisite context
   * @return a list of violations for null fields, empty if all required fields are populated
   */
  public static List<PrerequisiteViolation> check(Customer customer, PrerequisiteContext context) {
    return check(customer, context, false);
  }

  /**
   * Like {@link #check(Customer, PrerequisiteContext)}, but allows the caller to declare that an
   * alternative source of recipient identity exists (e.g. an ACTIVE portal_contact with email) so
   * that the {@code contact_name}/{@code contact_email} structural checks for PROPOSAL_SEND can be
   * skipped. Other field checks (address, etc.) are unaffected.
   *
   * <p>Rationale: for legal-za INDIVIDUAL customers, recipient identity is held in {@code
   * portal_contact}, not on the {@code Customer} entity columns. Hard-requiring those columns is a
   * stale invariant from before {@code portal_contact} became the canonical proposal recipient. See
   * BUG-CYCLE26-06.
   */
  public static List<PrerequisiteViolation> check(
      Customer customer, PrerequisiteContext context, boolean portalContactIdentitySatisfied) {
    List<FieldCheck> requiredFields = CONTEXT_FIELDS.get(context);
    if (requiredFields == null) {
      return List.of();
    }

    Map<String, Object> customFields = customer.getCustomFields();
    List<PrerequisiteViolation> violations = new ArrayList<>();

    for (FieldCheck field : requiredFields) {
      if (portalContactIdentitySatisfied
          && context == PrerequisiteContext.PROPOSAL_SEND
          && (field.fieldSlug().equals("contact_name")
              || field.fieldSlug().equals("contact_email"))) {
        continue;
      }
      if (!hasValue(field, customer, customFields)) {
        violations.add(
            new PrerequisiteViolation(
                "STRUCTURAL",
                field.displayName() + " is required for " + context.getDisplayLabel(),
                "CUSTOMER",
                customer.getId(),
                field.fieldSlug(),
                null,
                "Fill the " + field.displayName() + " field on the customer profile"));
      }
    }
    return violations;
  }

  /**
   * Returns the set of slugs that this structural check would enforce for the given context. Used
   * by {@link PrerequisiteService} to dedup FieldDefinition-based violations <em>only</em> for
   * slugs that are already covered structurally — so promoted slugs in contexts without structural
   * coverage (historically, any non-INVOICE/PROPOSAL/LIFECYCLE context) still get checked by the
   * custom-field path and never silently disappear.
   */
  public static Set<String> coveredSlugs(PrerequisiteContext context) {
    List<FieldCheck> fields = CONTEXT_FIELDS.get(context);
    if (fields == null) {
      return Set.of();
    }
    Set<String> slugs = new HashSet<>();
    for (FieldCheck field : fields) {
      slugs.add(field.fieldSlug());
    }
    return Set.copyOf(slugs);
  }

  /**
   * Evaluates the send-only prerequisite fields (currently just {@code tax_number}) and returns the
   * list of violations. Used by {@link io.b2mash.b2b.b2bstrawman.invoice.InvoiceValidationService}
   * to hard-block invoice send when the customer is still missing a tax number — see GAP-L-62.
   */
  public static List<PrerequisiteViolation> checkInvoiceSendOnly(Customer customer) {
    Map<String, Object> customFields = customer.getCustomFields();
    List<PrerequisiteViolation> violations = new ArrayList<>();
    for (FieldCheck field : INVOICE_SEND_ONLY_FIELDS) {
      if (!hasValue(field, customer, customFields)) {
        violations.add(
            new PrerequisiteViolation(
                "STRUCTURAL",
                field.displayName() + " is required to send an invoice",
                "CUSTOMER",
                customer.getId(),
                field.fieldSlug(),
                null,
                "Fill the " + field.displayName() + " field on the customer profile"));
      }
    }
    return violations;
  }

  /**
   * Returns {@code true} if the customer is missing the tax number on both the entity column and
   * JSONB. Used by the draft-creation path to emit a soft warning (GAP-L-62). A dedicated helper —
   * rather than re-using {@link #checkInvoiceSendOnly} — because callers want the boolean, not the
   * violation payload.
   */
  public static boolean isTaxNumberMissing(Customer customer) {
    return !hasCustomerFieldValue(customer, "tax_number", customer.getCustomFields());
  }

  /**
   * Shared helper for "does the customer have a value for this promoted slug, falling back to
   * JSONB?". Callers that need to evaluate an arbitrary slug (e.g. {@link PrerequisiteService}'s
   * template-driven engagement checks) should prefer this over re-reading the JSONB map so that
   * tenants whose data has been migrated to entity columns still pass the check.
   */
  public static boolean hasCustomerFieldValue(
      Customer customer, String slug, Map<String, Object> customFields) {
    Object entityValue = readPromotedColumn(customer, slug);
    if (isNonBlank(entityValue)) {
      return true;
    }
    if (customFields != null) {
      return isNonBlank(customFields.get(slug));
    }
    return false;
  }

  /**
   * Reads the promoted column for a given slug on the Customer entity. Returns {@code null} if the
   * slug is not promoted on Customer (callers should fall through to JSONB). Centralised so new
   * promoted columns only need one edit.
   */
  private static Object readPromotedColumn(Customer customer, String slug) {
    return switch (slug) {
      case "registration_number" -> customer.getRegistrationNumber();
      case "address_line1" -> customer.getAddressLine1();
      case "address_line2" -> customer.getAddressLine2();
      case "city" -> customer.getCity();
      case "state_province" -> customer.getStateProvince();
      case "postal_code" -> customer.getPostalCode();
      case "country" -> customer.getCountry();
      case "tax_number" -> customer.getTaxNumber();
      case "contact_name" -> customer.getContactName();
      case "contact_email" -> customer.getContactEmail();
      case "contact_phone" -> customer.getContactPhone();
      case "entity_type" -> customer.getEntityType();
      case "financial_year_end" -> customer.getFinancialYearEnd();
      default -> null;
    };
  }

  /**
   * Checks whether a field has a non-blank value in either the entity column or the JSONB custom
   * fields map.
   */
  private static boolean hasValue(
      FieldCheck field, Customer customer, Map<String, Object> customFields) {
    // Check entity column first
    Object entityValue = field.getter().apply(customer);
    if (isNonBlank(entityValue)) {
      return true;
    }
    // Fall back to JSONB custom fields
    if (customFields != null) {
      Object jsonbValue = customFields.get(field.fieldSlug());
      return isNonBlank(jsonbValue);
    }
    return false;
  }

  /**
   * A value is "non-blank" when it is non-null AND (for strings) contains non-whitespace content.
   * Explicitly whitelists scalar types we know represent real prerequisite data today so that a
   * future promoted field storing e.g. numeric zero or {@code Boolean.FALSE} isn't silently treated
   * as "filled".
   */
  private static boolean isNonBlank(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof String s) {
      return !s.isBlank();
    }
    if (value instanceof LocalDate) {
      return true;
    }
    // Any other type currently represents a custom-field JSONB payload (Map/List/Number/Boolean)
    // that we do NOT yet treat as structurally filled. Keep this conservative — callers that want
    // numeric or boolean "presence" semantics should widen this whitelist explicitly.
    return false;
  }
}
