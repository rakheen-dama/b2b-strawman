package io.b2mash.b2b.b2bstrawman.compliance;

import java.util.UUID;
import org.springframework.context.ApplicationEvent;

public class CustomerStatusChangedEvent extends ApplicationEvent {

  private final UUID customerId;
  private final String oldStatus;
  private final String newStatus;

  public CustomerStatusChangedEvent(
      Object source, UUID customerId, String oldStatus, String newStatus) {
    super(source);
    this.customerId = customerId;
    this.oldStatus = oldStatus;
    this.newStatus = newStatus;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getOldStatus() {
    return oldStatus;
  }

  public String getNewStatus() {
    return newStatus;
  }
}
