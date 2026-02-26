package io.b2mash.b2b.b2bstrawman.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tracks individual payment activities against an invoice. */
@Entity
@Table(name = "payment_events")
public class PaymentEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "provider_slug", nullable = false, length = 50)
  private String providerSlug;

  @Column(name = "session_id", length = 255)
  private String sessionId;

  @Column(name = "payment_reference", length = 255)
  private String paymentReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PaymentEventStatus status;

  @Column(name = "amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "payment_destination", nullable = false, length = 50)
  private String paymentDestination;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "provider_payload", columnDefinition = "jsonb")
  private String providerPayload;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA-required no-arg constructor. */
  protected PaymentEvent() {}

  /**
   * Creates a new payment event.
   *
   * @param invoiceId the invoice this payment is for
   * @param providerSlug the payment provider identifier (e.g. "stripe", "payfast", "manual")
   * @param sessionId the provider checkout session ID (null for manual payments)
   * @param status the initial status
   * @param amount the payment amount
   * @param currency the ISO 4217 currency code
   * @param paymentDestination the destination account (e.g. "OPERATING", "TRUST")
   */
  public PaymentEvent(
      UUID invoiceId,
      String providerSlug,
      String sessionId,
      PaymentEventStatus status,
      BigDecimal amount,
      String currency,
      String paymentDestination) {
    this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId must not be null");
    this.providerSlug = Objects.requireNonNull(providerSlug, "providerSlug must not be null");
    this.sessionId = sessionId;
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.amount = Objects.requireNonNull(amount, "amount must not be null");
    this.currency = Objects.requireNonNull(currency, "currency must not be null");
    this.paymentDestination =
        Objects.requireNonNull(paymentDestination, "paymentDestination must not be null");
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

  /** Updates the status of this payment event. */
  public void updateStatus(PaymentEventStatus newStatus) {
    this.status = newStatus;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public String getProviderSlug() {
    return providerSlug;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public void setPaymentReference(String paymentReference) {
    this.paymentReference = paymentReference;
  }

  public PaymentEventStatus getStatus() {
    return status;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public String getPaymentDestination() {
    return paymentDestination;
  }

  public String getProviderPayload() {
    return providerPayload;
  }

  public void setProviderPayload(String providerPayload) {
    this.providerPayload = providerPayload;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
