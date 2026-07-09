package io.b2mash.b2b.b2bstrawman.collections;

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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A single chase-ledger row: one row per (invoice, stage), transitioning in place through the
 * {@link CollectionActivityStatus} state machine. Records the dunning stage, its current status,
 * the {@code AiExecutionGate} carrying the draft, the resulting email-delivery log, a days-overdue
 * snapshot at action time, and a machine-readable reason for skip/cancel outcomes.
 *
 * <p>Linkage is flat by raw UUID ({@code invoiceId} / {@code customerId} / {@code gateId} / {@code
 * emailDeliveryLogId}) — no {@code @ManyToOne} across aggregates. {@code customerId} is
 * denormalised from the invoice at creation so the customer chase-history page and debtor-book
 * aggregation can query without joining invoices.
 *
 * <p>Pure schema-per-tenant isolation — no {@code tenant_id} column, no {@code @Filter}. The {@code
 * ux_collection_activity_invoice_stage} unique index is the idempotency backbone (at most one
 * activity per invoice per stage, ever), and {@code @Version} guards the
 * scan-vs-approval-vs-payment race.
 */
@Entity
@Table(name = "collection_activities")
public class CollectionActivity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId; // raw UUID FK, not @ManyToOne

  @Column(name = "customer_id", nullable = false)
  private UUID customerId; // raw UUID FK; denormalised from the invoice at creation

  @Enumerated(EnumType.STRING)
  @Column(name = "stage", nullable = false, length = 20)
  private CollectionStage stage;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CollectionActivityStatus status;

  @Column(name = "gate_id")
  private UUID gateId; // AiExecutionGate carrying the current/last draft

  @Column(name = "email_delivery_log_id")
  private UUID emailDeliveryLogId; // set on SENT/SEND_FAILED by the executor (590B)

  @Column(name = "days_overdue_at_action", nullable = false)
  private int daysOverdueAtAction;

  @Column(name = "reason", length = 255)
  private String reason; // machine-readable cause for SKIPPED/CANCELLED_PAYMENT/SEND_FAILED/FLAGGED

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private int version;

  /** JPA-required no-arg constructor. */
  protected CollectionActivity() {}

  /**
   * Construct a chase-ledger row. Timestamps are set by {@link #onCreate()} — do not set them here.
   */
  public CollectionActivity(
      UUID invoiceId,
      UUID customerId,
      CollectionStage stage,
      CollectionActivityStatus status,
      int daysOverdueAtAction,
      String reason) {
    this.invoiceId = invoiceId;
    this.customerId = customerId;
    this.stage = stage;
    this.status = status;
    this.daysOverdueAtAction = daysOverdueAtAction;
    this.reason = reason;
  }

  @PrePersist
  void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  /**
   * Transition to {@link CollectionActivityStatus#PROPOSED} with a fresh gate. Used for the first
   * proposal AND for re-proposal of a retryable ({@code SKIPPED}/{@code SEND_FAILED}) row: replaces
   * the gate, snapshots the days-overdue at proposal time, and clears any prior skip/cancel reason.
   */
  public void markProposed(UUID gateId, int daysOverdue) {
    this.status = CollectionActivityStatus.PROPOSED;
    this.gateId = gateId;
    this.daysOverdueAtAction = daysOverdue;
    this.reason = null;
  }

  /** Transition to {@link CollectionActivityStatus#SENT}, recording the email-delivery log. */
  public void markSent(UUID emailDeliveryLogId) {
    this.status = CollectionActivityStatus.SENT;
    this.emailDeliveryLogId = emailDeliveryLogId;
  }

  /**
   * Transition to {@link CollectionActivityStatus#SEND_FAILED} (approved but the mail provider
   * failed; no email left). Scan-retryable back to {@link CollectionActivityStatus#PROPOSED}.
   * Records the failed attempt's email-delivery log (nullable — the executor may fail before a log
   * row exists) and a machine-readable reason.
   */
  public void markSendFailed(UUID emailDeliveryLogId, String reason) {
    this.status = CollectionActivityStatus.SEND_FAILED;
    this.emailDeliveryLogId = emailDeliveryLogId;
    this.reason = reason;
  }

  /**
   * Transition to {@link CollectionActivityStatus#SKIPPED} with a machine-readable reason. The
   * {@code gateId} is intentionally retained (e.g. on {@code gate_expired}) so the last-known draft
   * stays traceable until a later re-proposal overwrites it.
   */
  public void markSkipped(String reason) {
    this.status = CollectionActivityStatus.SKIPPED;
    this.reason = reason;
  }

  /**
   * Transition to {@link CollectionActivityStatus#CANCELLED_PAYMENT} (invoice PAID/VOID) with a
   * machine-readable reason.
   */
  public void markCancelled(String reason) {
    this.status = CollectionActivityStatus.CANCELLED_PAYMENT;
    this.reason = reason;
  }

  /** Transition to {@link CollectionActivityStatus#REJECTED} (a human declined to chase). */
  public void markRejected() {
    this.status = CollectionActivityStatus.REJECTED;
  }

  /**
   * Transition to {@link CollectionActivityStatus#FLAGGED} (ESCALATION stage only; terminal, no
   * email sent — raised for a partner call) with a machine-readable reason.
   */
  public void markFlagged(String reason) {
    this.status = CollectionActivityStatus.FLAGGED;
    this.reason = reason;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public CollectionStage getStage() {
    return stage;
  }

  public CollectionActivityStatus getStatus() {
    return status;
  }

  public UUID getGateId() {
    return gateId;
  }

  public UUID getEmailDeliveryLogId() {
    return emailDeliveryLogId;
  }

  public int getDaysOverdueAtAction() {
    return daysOverdueAtAction;
  }

  public String getReason() {
    return reason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public int getVersion() {
    return version;
  }
}
