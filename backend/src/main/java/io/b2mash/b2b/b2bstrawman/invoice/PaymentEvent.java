package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
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

  /** How the money arrived (Phase 88 U15, ADR-347 A5). NULL for pre-622B rows (CHECK allows it). */
  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", length = 20)
  private PaymentMethod paymentMethod;

  /**
   * The payment receipt for this event (Phase 89 632B, ADR-364, tenant V10 §1). NULL until a
   * receipt is generated; the FK is {@code ON DELETE SET NULL}, so a deleted receipt reads NULL
   * again and the lazy path regenerates it. Set only through {@link #attachReceipt}.
   */
  @Column(name = "receipt_generated_document_id")
  private UUID receiptGeneratedDocumentId;

  /** {@code {invoiceNumber}/R{seq}}, stored (R6.2); NULL until a receipt exists. Never reused. */
  @Column(name = "receipt_number", length = 40)
  private String receiptNumber;

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

  /**
   * A COMPLETED event recording the real amount of a posted payment (Phase 88 U15). {@code
   * currency} and {@code invoicePaymentDestination} come from the invoice (both NOT NULL columns);
   * the posting's own destination wins when set (e.g. {@code "TRUST"} for a trust fee transfer).
   */
  public static PaymentEvent completed(
      UUID invoiceId, String currency, String invoicePaymentDestination, PaymentPosting posting) {
    var event =
        new PaymentEvent(
            invoiceId,
            posting.providerSlug(),
            posting.sessionId(),
            PaymentEventStatus.COMPLETED,
            posting.amount(),
            currency,
            posting.destination() != null ? posting.destination() : invoicePaymentDestination);
    event.paymentMethod = posting.method();
    event.paymentReference = posting.reference();
    return event;
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

  /**
   * Links the receipt document (the only setter, ADR-364). Idempotent: the same document id again
   * is a no-op (the listener and the lazy path may race, R6.3); a different id while one is
   * attached is refused. After the receipt row is deleted (FK ON DELETE SET NULL) the pointer reads
   * NULL again and a new receipt with a new number may be attached.
   */
  public void attachReceipt(UUID documentId, String receiptNumber) {
    Objects.requireNonNull(documentId, "documentId must not be null");
    if (documentId.equals(this.receiptGeneratedDocumentId)) {
      return;
    }
    if (this.receiptGeneratedDocumentId != null) {
      throw new InvalidStateException(
          "Receipt already generated",
          "This payment already has receipt " + this.receiptNumber + ".");
    }
    this.receiptGeneratedDocumentId = documentId;
    this.receiptNumber = Objects.requireNonNull(receiptNumber, "receiptNumber must not be null");
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

  public PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public UUID getReceiptGeneratedDocumentId() {
    return receiptGeneratedDocumentId;
  }

  public String getReceiptNumber() {
    return receiptNumber;
  }
}
