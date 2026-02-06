package io.b2mash.b2b.b2bstrawman.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_webhooks", schema = "public")
public class ProcessedWebhook {

  @Id
  @Column(name = "svix_id")
  private String svixId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt = Instant.now();

  protected ProcessedWebhook() {}

  public ProcessedWebhook(String svixId, String eventType) {
    this.svixId = svixId;
    this.eventType = eventType;
  }

  public String getSvixId() {
    return svixId;
  }

  public String getEventType() {
    return eventType;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
