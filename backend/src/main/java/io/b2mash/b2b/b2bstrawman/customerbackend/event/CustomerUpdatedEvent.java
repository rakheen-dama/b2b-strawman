package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import java.util.UUID;

/** Published when a customer is updated or archived. */
public final class CustomerUpdatedEvent extends PortalDomainEvent {

  private final UUID customerId;
  private final String name;
  private final String email;
  private final String status;

  public CustomerUpdatedEvent(
      UUID customerId, String name, String email, String status, String orgId, String tenantId) {
    super(orgId, tenantId);
    this.customerId = customerId;
    this.name = name;
    this.email = email;
    this.status = status;
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

  public String getStatus() {
    return status;
  }
}
