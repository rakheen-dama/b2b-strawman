package io.b2mash.b2b.b2bstrawman.prerequisite;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

  /** Required fields for INVOICE_GENERATION context. */
  private static final List<FieldCheck> INVOICE_GENERATION_FIELDS =
      List.of(
          new FieldCheck("address_line1", "Address Line 1", Customer::getAddressLine1),
          new FieldCheck("city", "City", Customer::getCity),
          new FieldCheck("country", "Country", Customer::getCountry),
          new FieldCheck("tax_number", "Tax Number", Customer::getTaxNumber));

  /** Required fields for PROPOSAL_SEND context. */
  private static final List<FieldCheck> PROPOSAL_SEND_FIELDS =
      List.of(
          new FieldCheck("contact_name", "Contact Name", Customer::getContactName),
          new FieldCheck("contact_email", "Contact Email", Customer::getContactEmail),
          new FieldCheck("address_line1", "Address Line 1", Customer::getAddressLine1));

  /** Maps prerequisite contexts to their required field checks. */
  private static final Map<PrerequisiteContext, List<FieldCheck>> CONTEXT_FIELDS =
      Map.of(
          PrerequisiteContext.INVOICE_GENERATION, INVOICE_GENERATION_FIELDS,
          PrerequisiteContext.PROPOSAL_SEND, PROPOSAL_SEND_FIELDS);

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
    List<FieldCheck> requiredFields = CONTEXT_FIELDS.get(context);
    if (requiredFields == null) {
      return List.of();
    }

    Map<String, Object> customFields = customer.getCustomFields();
    List<PrerequisiteViolation> violations = new ArrayList<>();

    for (FieldCheck field : requiredFields) {
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

  private static boolean isNonBlank(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof String s) {
      return !s.isBlank();
    }
    return true; // Non-null, non-string values (e.g., LocalDate) are considered present
  }
}
