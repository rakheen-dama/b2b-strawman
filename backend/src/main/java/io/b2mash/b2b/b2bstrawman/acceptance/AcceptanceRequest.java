package io.b2mash.b2b.b2bstrawman.acceptance;

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
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A request for a portal contact to accept a generated document. */
@Entity
@Table(name = "acceptance_requests")
public class AcceptanceRequest {

  private static final Set<AcceptanceStatus> TERMINAL_STATUSES =
      Set.of(AcceptanceStatus.ACCEPTED, AcceptanceStatus.EXPIRED, AcceptanceStatus.REVOKED);

  private static final Set<AcceptanceStatus> ACTIVE_STATUSES =
      Set.of(AcceptanceStatus.PENDING, AcceptanceStatus.SENT, AcceptanceStatus.VIEWED);

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "generated_document_id", nullable = false)
  private UUID generatedDocumentId;

  @Column(name = "portal_contact_id", nullable = false)
  private UUID portalContactId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private AcceptanceStatus status;

  @Column(name = "request_token", nullable = false, length = 255)
  private String requestToken;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "viewed_at")
  private Instant viewedAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "acceptor_name", length = 255)
  private String acceptorName;

  @Column(name = "acceptor_ip_address", length = 45)
  private String acceptorIpAddress;

  @Column(name = "acceptor_user_agent", length = 500)
  private String acceptorUserAgent;

  @Column(name = "certificate_s3_key", length = 1000)
  private String certificateS3Key;

  @Column(name = "certificate_file_name", length = 255)
  private String certificateFileName;

  @Column(name = "sent_by_member_id", nullable = false)
  private UUID sentByMemberId;

  @Column(name = "revoked_by_member_id")
  private UUID revokedByMemberId;

  @Column(name = "reminder_count", nullable = false)
  private int reminderCount;

  @Column(name = "last_reminded_at")
  private Instant lastRemindedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA-required no-arg constructor. */
  protected AcceptanceRequest() {}

  public AcceptanceRequest(
      UUID generatedDocumentId,
      UUID portalContactId,
      UUID customerId,
      String requestToken,
      Instant expiresAt,
      UUID sentByMemberId) {
    this.generatedDocumentId =
        Objects.requireNonNull(generatedDocumentId, "generatedDocumentId must not be null");
    this.portalContactId =
        Objects.requireNonNull(portalContactId, "portalContactId must not be null");
    this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
    this.requestToken = Objects.requireNonNull(requestToken, "requestToken must not be null");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    this.sentByMemberId = Objects.requireNonNull(sentByMemberId, "sentByMemberId must not be null");
    this.status = AcceptanceStatus.PENDING;
    this.reminderCount = 0;
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

  // --- Lifecycle methods ---

  /** Marks the request as sent (email delivery success). Only valid from PENDING. */
  public void markSent() {
    requireStatus(Set.of(AcceptanceStatus.PENDING), "mark as sent");
    this.status = AcceptanceStatus.SENT;
    this.sentAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Marks the request as viewed (recipient opened the acceptance page). Only valid from SENT. */
  public void markViewed(Instant viewedAt) {
    if (this.status == AcceptanceStatus.VIEWED) {
      return; // idempotent
    }
    requireStatus(Set.of(AcceptanceStatus.SENT), "mark as viewed");
    this.status = AcceptanceStatus.VIEWED;
    this.viewedAt = Objects.requireNonNull(viewedAt, "viewedAt must not be null");
    this.updatedAt = Instant.now();
  }

  /** Marks the request as accepted. Only valid from SENT or VIEWED. */
  public void markAccepted(String name, String ip, String ua) {
    requireStatus(Set.of(AcceptanceStatus.SENT, AcceptanceStatus.VIEWED), "mark as accepted");
    this.status = AcceptanceStatus.ACCEPTED;
    this.acceptorName = name;
    this.acceptorIpAddress = ip;
    this.acceptorUserAgent = ua;
    this.acceptedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Revokes the request. Valid from PENDING, SENT, or VIEWED. */
  public void markRevoked(UUID revokedBy) {
    requireStatus(ACTIVE_STATUSES, "revoke");
    this.status = AcceptanceStatus.REVOKED;
    this.revokedByMemberId = Objects.requireNonNull(revokedBy, "revokedBy must not be null");
    this.revokedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Marks the request as expired. Valid from PENDING, SENT, or VIEWED. */
  public void markExpired() {
    requireStatus(ACTIVE_STATUSES, "mark as expired");
    this.status = AcceptanceStatus.EXPIRED;
    this.updatedAt = Instant.now();
  }

  /** Records a reminder sent for this request. Only valid on active requests. */
  public void recordReminder() {
    requireStatus(ACTIVE_STATUSES, "record reminder");
    this.reminderCount++;
    this.lastRemindedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Returns true if the request is in an active (non-terminal) status. */
  public boolean isActive() {
    return ACTIVE_STATUSES.contains(this.status);
  }

  /** Returns true if the current time is past the expiry deadline. */
  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getGeneratedDocumentId() {
    return generatedDocumentId;
  }

  public UUID getPortalContactId() {
    return portalContactId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public AcceptanceStatus getStatus() {
    return status;
  }

  public String getRequestToken() {
    return requestToken;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public Instant getViewedAt() {
    return viewedAt;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public String getAcceptorName() {
    return acceptorName;
  }

  public String getAcceptorIpAddress() {
    return acceptorIpAddress;
  }

  public String getAcceptorUserAgent() {
    return acceptorUserAgent;
  }

  public String getCertificateS3Key() {
    return certificateS3Key;
  }

  public void setCertificateS3Key(String certificateS3Key) {
    this.certificateS3Key = certificateS3Key;
    this.updatedAt = Instant.now();
  }

  public String getCertificateFileName() {
    return certificateFileName;
  }

  public void setCertificateFileName(String certificateFileName) {
    this.certificateFileName = certificateFileName;
    this.updatedAt = Instant.now();
  }

  public UUID getSentByMemberId() {
    return sentByMemberId;
  }

  public UUID getRevokedByMemberId() {
    return revokedByMemberId;
  }

  public int getReminderCount() {
    return reminderCount;
  }

  public Instant getLastRemindedAt() {
    return lastRemindedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // --- Private helpers ---

  private void requireStatus(Set<AcceptanceStatus> allowedStatuses, String action) {
    if (!allowedStatuses.contains(this.status)) {
      throw new InvalidStateException(
          "Invalid acceptance request state",
          "Cannot " + action + " acceptance request in status " + this.status);
    }
  }
}
