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
