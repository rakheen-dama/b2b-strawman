package io.b2mash.b2b.b2bstrawman.testutil;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
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
}
