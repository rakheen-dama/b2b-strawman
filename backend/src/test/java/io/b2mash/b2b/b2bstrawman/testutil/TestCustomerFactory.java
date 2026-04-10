package io.b2mash.b2b.b2bstrawman.testutil;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared test utility for creating customers with explicit lifecycle status. */
public final class TestCustomerFactory {

  private TestCustomerFactory() {}

  /** Creates a customer with ACTIVE lifecycle status — safe for guard-checked operations. */
  public static Customer createActiveCustomer(String name, String email, UUID createdBy) {
    return new Customer(
        name, email, null, null, null, createdBy, CustomerType.INDIVIDUAL, LifecycleStatus.ACTIVE);
  }

  /** Creates a customer with ACTIVE lifecycle status and specified type. */
  public static Customer createActiveCustomer(
      String name, String email, UUID createdBy, CustomerType type) {
    return new Customer(name, email, null, null, null, createdBy, type, LifecycleStatus.ACTIVE);
  }

  /** Creates a customer with the specified lifecycle status. */
  public static Customer createCustomerWithStatus(
      String name, String email, UUID createdBy, LifecycleStatus status) {
    return new Customer(name, email, null, null, null, createdBy, CustomerType.INDIVIDUAL, status);
  }

  /**
   * Creates an ACTIVE customer with customFields pre-filled for every required field definition.
   * Returns an unsaved Customer object — caller must save via repository.
   */
  public static Customer createActiveCustomerWithPrerequisites(
      String name, String email, UUID createdBy, List<FieldDefinition> requiredFields) {
    var customer =
        new Customer(
            name,
            email,
            null,
            null,
            null,
            createdBy,
            CustomerType.INDIVIDUAL,
            LifecycleStatus.ACTIVE);
    customer.setCustomFields(buildFieldValues(requiredFields));
    return customer;
  }

  /**
   * Creates an ONBOARDING customer with customFields pre-filled for every required field
   * definition. Returns an unsaved Customer object — caller must save via repository.
   */
  public static Customer createOnboardingCustomerWithPrerequisites(
      String name, String email, UUID createdBy, List<FieldDefinition> requiredFields) {
    var customer =
        new Customer(
            name,
            email,
            null,
            null,
            null,
            createdBy,
            CustomerType.INDIVIDUAL,
            LifecycleStatus.ONBOARDING);
    customer.setCustomFields(buildFieldValues(requiredFields));
    return customer;
  }

  /**
   * Returns a custom fields map with the minimum required fields for passing prerequisite checks
   * (INVOICE_GENERATION, PROPOSAL_SEND). Matches the seeded common-customer field pack slugs.
   */
  public static Map<String, Object> prerequisiteCustomFields() {
    return Map.of(
        "address_line1", "123 Test Street",
        "city", "Test City",
        "country", "ZA",
        "tax_number", "VAT123456",
        "contact_name", "Test Contact",
        "contact_email", "contact@test.com");
  }

  /**
   * Creates an ACTIVE customer with prerequisite fields pre-filled on both entity columns and
   * JSONB. Safe for invoice creation and proposal sending flows.
   */
  public static Customer createActiveCustomerWithPrerequisiteFields(
      String name, String email, UUID createdBy) {
    var customer =
        new Customer(
            name,
            email,
            null,
            null,
            null,
            createdBy,
            CustomerType.INDIVIDUAL,
            LifecycleStatus.ACTIVE);
    customer.setCustomFields(new HashMap<>(prerequisiteCustomFields()));
    // Also set promoted entity columns
    customer.setAddressLine1("123 Test Street");
    customer.setCity("Test City");
    customer.setCountry("ZA");
    customer.setTaxNumber("VAT123456");
    customer.setContactName("Test Contact");
    customer.setContactEmail("contact@test.com");
    return customer;
  }

  /**
   * Builds a customFields map with appropriate test values for each field definition based on its
   * FieldType.
   */
  private static Map<String, Object> buildFieldValues(List<FieldDefinition> fields) {
    var values = new HashMap<String, Object>();
    for (var fd : fields) {
      values.put(fd.getSlug(), testValueFor(fd.getFieldType()));
    }
    return values;
  }

  /**
   * Fills prerequisite fields directly in the database via JdbcTemplate. Sets both JSONB custom
   * fields and promoted entity columns to satisfy structural prerequisite checks for
   * INVOICE_GENERATION, PROPOSAL_SEND, and other contexts.
   */
  public static void fillPrerequisiteFields(
      org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
      String schemaName,
      String customerIdStr) {
    // Canonical test values — align with prerequisiteCustomFields() so that exact-value assertions
    // in both helper paths stay consistent.
    jdbcTemplate.update(
        ("UPDATE \"%s\".customers SET"
                + " custom_fields = '{\"address_line1\":\"123 Test Street\",\"city\":\"Test City\","
                + "\"country\":\"ZA\",\"tax_number\":\"VAT123456\","
                + "\"contact_name\":\"Test Contact\",\"contact_email\":\"contact@test.com\"}'::jsonb,"
                + " address_line1 = '123 Test Street',"
                + " city = 'Test City',"
                + " country = 'ZA',"
                + " tax_number = 'VAT123456',"
                + " contact_name = 'Test Contact',"
                + " contact_email = 'contact@test.com'"
                + " WHERE id = ?::uuid")
            .formatted(schemaName),
        customerIdStr);
  }

  private static Object testValueFor(FieldType fieldType) {
    return switch (fieldType) {
      case TEXT -> "test_value";
      case NUMBER -> 42;
      case DATE -> "2026-01-01";
      case DROPDOWN -> "option_1";
      case BOOLEAN -> true;
      case CURRENCY -> Map.of("amount", 100, "currency", "ZAR");
      case URL -> "https://example.com";
      case EMAIL -> "test@example.com";
      case PHONE -> "+27123456789";
    };
  }
}
