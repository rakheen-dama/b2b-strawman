package io.b2mash.b2b.b2bstrawman.informationrequest;

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

@Entity
@Table(name = "request_items")
public class RequestItem {

  private static final Set<ItemStatus> SUBMITTABLE_STATUSES =
      Set.of(ItemStatus.PENDING, ItemStatus.REJECTED);

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column(name = "template_item_id")
  private UUID templateItemId;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "description", length = 1000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "response_type", nullable = false, length = 20)
  private ResponseType responseType;

  @Column(name = "required", nullable = false)
  private boolean required;

  @Column(name = "file_type_hints", length = 200)
  private String fileTypeHints;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ItemStatus status;

  @Column(name = "document_id")
  private UUID documentId;

  @Column(name = "text_response", columnDefinition = "TEXT")
  private String textResponse;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected RequestItem() {}

  public RequestItem(
      UUID requestId,
      String name,
      String description,
      ResponseType responseType,
      boolean required,
      String fileTypeHints,
      int sortOrder) {
    this.requestId = Objects.requireNonNull(requestId, "requestId must not be null");
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.description = description;
    this.responseType = Objects.requireNonNull(responseType, "responseType must not be null");
    this.required = required;
    this.fileTypeHints = fileTypeHints;
    this.sortOrder = sortOrder;
    this.status = ItemStatus.PENDING;
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

  // ── Submission methods ─────────────────────────────────────────────

  /** Submit a file upload response. PENDING/REJECTED -> SUBMITTED. */
  public void submit(UUID documentId) {
    requireStatus(SUBMITTABLE_STATUSES, "submit");
    this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
    this.status = ItemStatus.SUBMITTED;
    this.submittedAt = Instant.now();
    this.rejectionReason = null;
    this.updatedAt = Instant.now();
  }

  /** Submit a text response. PENDING/REJECTED -> SUBMITTED. */
  public void submitText(String text) {
    requireStatus(SUBMITTABLE_STATUSES, "submit text for");
    this.textResponse = Objects.requireNonNull(text, "text must not be null");
    this.status = ItemStatus.SUBMITTED;
    this.submittedAt = Instant.now();
    this.rejectionReason = null;
    this.updatedAt = Instant.now();
  }

  // ── Review methods ─────────────────────────────────────────────────

  /** Accept the submitted response. SUBMITTED -> ACCEPTED. */
  public void accept(UUID reviewedBy) {
    requireStatus(Set.of(ItemStatus.SUBMITTED), "accept");
    this.reviewedBy = Objects.requireNonNull(reviewedBy, "reviewedBy must not be null");
    this.status = ItemStatus.ACCEPTED;
    this.reviewedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Reject the submitted response. SUBMITTED -> REJECTED. Clears documentId and textResponse. */
  public void reject(String reason, UUID reviewedBy) {
    requireStatus(Set.of(ItemStatus.SUBMITTED), "reject");
    this.rejectionReason = Objects.requireNonNull(reason, "reason must not be null");
    this.reviewedBy = Objects.requireNonNull(reviewedBy, "reviewedBy must not be null");
    this.status = ItemStatus.REJECTED;
    this.reviewedAt = Instant.now();
    this.documentId = null;
    this.textResponse = null;
    this.updatedAt = Instant.now();
  }

  // ── Getters ────────────────────────────────────────────────────────

  public UUID getId() {
    return id;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public UUID getTemplateItemId() {
    return templateItemId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public ResponseType getResponseType() {
    return responseType;
  }

  public boolean isRequired() {
    return required;
  }

  public String getFileTypeHints() {
    return fileTypeHints;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public ItemStatus getStatus() {
    return status;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public String getTextResponse() {
    return textResponse;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // ── Setters for optional fields ────────────────────────────────────

  public void setTemplateItemId(UUID templateItemId) {
    this.templateItemId = templateItemId;
  }

  // ── Private helpers ────────────────────────────────────────────────

  private void requireStatus(Set<ItemStatus> allowedStatuses, String action) {
    if (!allowedStatuses.contains(this.status)) {
      throw new InvalidStateException(
          "Invalid item state", "Cannot " + action + " item in status " + this.status);
    }
  }
}
