package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import java.util.UUID;

/** Published when a customer is created. */
public final class CustomerCreatedEvent extends PortalDomainEvent {

  private final UUID customerId;
  private final String name;
  private final String email;

  public CustomerCreatedEvent(
      UUID customerId, String name, String email, String orgId, String tenantId) {
    super(orgId, tenantId);
    this.customerId = customerId;
    this.name = name;
    this.email = email;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }
}
