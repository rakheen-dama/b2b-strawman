package io.b2mash.b2b.b2bstrawman.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "subscription_payments", schema = "public")
public class SubscriptionPayment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  private UUID id;

  @Column(name = "subscription_id", nullable = false)
  private UUID subscriptionId;

  @Column(name = "payfast_payment_id", nullable = false)
  private String payfastPaymentId;

  @Column(name = "amount_cents", nullable = false)
  private int amountCents;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private PaymentStatus status;

  @Column(name = "payment_date", nullable = false)
  private Instant paymentDate;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_itn", columnDefinition = "jsonb")
  private Map<String, String> rawItn;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected SubscriptionPayment() {}

  public SubscriptionPayment(
      UUID subscriptionId,
      String payfastPaymentId,
      int amountCents,
      String currency,
      PaymentStatus status,
      Instant paymentDate,
      Map<String, String> rawItn) {
    this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId must not be null");
    this.payfastPaymentId =
        Objects.requireNonNull(payfastPaymentId, "payfastPaymentId must not be null");
    this.amountCents = amountCents;
    this.currency = Objects.requireNonNull(currency, "currency must not be null");
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.paymentDate = Objects.requireNonNull(paymentDate, "paymentDate must not be null");
    this.rawItn = rawItn;
    this.createdAt = Instant.now();
  }

  // --- Getters (no setters — append-only record) ---

  public UUID getId() {
    return id;
  }

  public UUID getSubscriptionId() {
    return subscriptionId;
  }

  public String getPayfastPaymentId() {
    return payfastPaymentId;
  }

  public int getAmountCents() {
    return amountCents;
  }

  public String getCurrency() {
    return currency;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public Instant getPaymentDate() {
    return paymentDate;
  }

  public Map<String, String> getRawItn() {
    return rawItn == null ? null : Collections.unmodifiableMap(rawItn);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public enum PaymentStatus {
    COMPLETE,
    FAILED,
    REFUNDED
  }
}
