package io.b2mash.b2b.b2bstrawman.integration.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_delivery_log")
public class EmailDeliveryLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "recipient_email", nullable = false, length = 320)
  private String recipientEmail;

  @Column(name = "template_name", nullable = false, length = 100)
  private String templateName;

  @Column(name = "reference_type", nullable = false, length = 30)
  private String referenceType;

  @Column(name = "reference_id")
  private UUID referenceId;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "provider_message_id", length = 200)
  private String providerMessageId;

  @Column(name = "provider_slug", nullable = false, length = 50)
  private String providerSlug;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected EmailDeliveryLog() {}

  public EmailDeliveryLog(
      String recipientEmail,
      String templateName,
      String referenceType,
      UUID referenceId,
      String status,
      String providerMessageId,
      String providerSlug,
      String errorMessage) {
    this.recipientEmail = recipientEmail;
    this.templateName = templateName;
    this.referenceType = referenceType;
    this.referenceId = referenceId;
    this.status = status;
    this.providerMessageId = providerMessageId;
    this.providerSlug = providerSlug;
    this.errorMessage = errorMessage;
  }

  @PrePersist
  void onPrePersist() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onPreUpdate() {
    this.updatedAt = Instant.now();
  }

  public void updateDeliveryStatus(EmailDeliveryStatus newStatus, String errorMessage) {
    this.status = newStatus.name();
    this.errorMessage = errorMessage;
  }

  public UUID getId() {
    return id;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public String getTemplateName() {
    return templateName;
  }

  public String getReferenceType() {
    return referenceType;
  }

  public UUID getReferenceId() {
    return referenceId;
  }

  public String getStatus() {
    return status;
  }

  public String getProviderMessageId() {
    return providerMessageId;
  }

  public String getProviderSlug() {
    return providerSlug;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
