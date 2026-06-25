package io.b2mash.b2b.b2bstrawman.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "file_name", nullable = false, length = 500)
  private String fileName;

  @Column(name = "content_type", length = 100)
  private String contentType;

  @Column(name = "size", nullable = false)
  private long size;

  @Column(name = "s3_key", nullable = false, length = 1000)
  private String s3Key;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 50)
  private Status status;

  @Column(name = "uploaded_by", nullable = false)
  private UUID uploadedBy;

  @Column(name = "uploaded_at")
  private Instant uploadedAt;

  @Column(name = "scope", nullable = false, length = 20)
  private String scope;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "visibility", nullable = false, length = 20)
  private String visibility;

  @Column(name = "source", nullable = false, length = 30)
  private String source = Source.MANUAL;

  @Column(name = "ai_execution_id")
  private UUID aiExecutionId;

  @Column(name = "correspondence_id")
  private UUID correspondenceId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Document() {}

  /** Constructor for PROJECT-scoped documents (backward compatible). */
  public Document(UUID projectId, String fileName, String contentType, long size, UUID uploadedBy) {
    this.projectId = projectId;
    this.fileName = fileName;
    this.contentType = contentType;
    this.size = size;
    this.s3Key = "pending";
    this.status = Status.PENDING;
    this.uploadedBy = uploadedBy;
    this.scope = Scope.PROJECT;
    this.visibility = Visibility.INTERNAL;
    this.source = Source.MANUAL;
    this.createdAt = Instant.now();
  }

  /** Constructor for scope-aware document creation. */
  public Document(
      String scope,
      UUID projectId,
      UUID customerId,
      String fileName,
      String contentType,
      long size,
      UUID uploadedBy,
      String visibility) {
    this.scope = scope;
    this.projectId = projectId;
    this.customerId = customerId;
    this.fileName = fileName;
    this.contentType = contentType;
    this.size = size;
    this.s3Key = "pending";
    this.status = Status.PENDING;
    this.uploadedBy = uploadedBy;
    this.visibility = visibility;
    this.source = Source.MANUAL;
    this.createdAt = Instant.now();
  }

  public void assignS3Key(String s3Key) {
    this.s3Key = s3Key;
  }

  public void confirmUpload() {
    this.status = Status.UPLOADED;
    this.uploadedAt = Instant.now();
  }

  public boolean isOrgScoped() {
    return Scope.ORG.equals(scope);
  }

  public boolean isProjectScoped() {
    return Scope.PROJECT.equals(scope);
  }

  public boolean isCustomerScoped() {
    return Scope.CUSTOMER.equals(scope);
  }

  /** Mark this document as AI-generated and link to the execution that produced it. */
  public void markAsAiGenerated(UUID executionId) {
    this.source = Source.AI_GENERATED;
    this.aiExecutionId = executionId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getFileName() {
    return fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSize() {
    return size;
  }

  public String getS3Key() {
    return s3Key;
  }

  public Status getStatus() {
    return status;
  }

  public UUID getUploadedBy() {
    return uploadedBy;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public String getScope() {
    return scope;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getVisibility() {
    return visibility;
  }

  public void setVisibility(String visibility) {
    this.visibility = visibility;
  }

  public String getSource() {
    return source;
  }

  /**
   * Set the document source label (e.g. {@link Source#EMAIL_INGEST}). Used by the inbound
   * correspondence write path to mark a document attached via {@code attach_document} (Phase 81).
   */
  public void setSource(String source) {
    this.source = source;
  }

  public UUID getAiExecutionId() {
    return aiExecutionId;
  }

  public UUID getCorrespondenceId() {
    return correspondenceId;
  }

  public void setCorrespondenceId(UUID correspondenceId) {
    this.correspondenceId = correspondenceId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public enum Status {
    PENDING,
    UPLOADED,
    FAILED
  }

  /** Document scope constants. */
  public static final class Scope {
    public static final String PROJECT = "PROJECT";
    public static final String ORG = "ORG";
    public static final String CUSTOMER = "CUSTOMER";

    private Scope() {}
  }

  /**
   * Document visibility constants.
   *
   * <p>Three values, two semantic groups:
   *
   * <ul>
   *   <li>{@code INTERNAL} — firm-only; not visible on the portal.
   *   <li>{@code SHARED} — manually shared with the client (firm clicked a "share with portal"
   *       toggle). Visible on the portal.
   *   <li>{@code PORTAL} — system-auto-shared on a generated artefact (closure-pack letter,
   *       statement of account, etc.). Visible on the portal. Distinct from {@code SHARED} so audit
   *       + analytics can tell manual shares from system shares.
   * </ul>
   *
   * Both {@code SHARED} and {@code PORTAL} render on the portal — see {@link
   * io.b2mash.b2b.b2bstrawman.portal.PortalQueryService#listProjectDocuments}.
   */
  public static final class Visibility {
    public static final String INTERNAL = "INTERNAL";
    public static final String SHARED = "SHARED";
    public static final String PORTAL = "PORTAL";

    private Visibility() {}

    /**
     * Returns true if the visibility makes the document visible to portal contacts. Both manual
     * shares ({@link #SHARED}) and system-auto-shares ({@link #PORTAL}) are portal-visible.
     */
    public static boolean isPortalVisible(String visibility) {
      return SHARED.equals(visibility) || PORTAL.equals(visibility);
    }
  }

  /** Document source constants (ADR-292). */
  public static final class Source {
    public static final String MANUAL = "MANUAL";
    public static final String AI_GENERATED = "AI_GENERATED";
    public static final String TEMPLATE_GENERATED = "TEMPLATE_GENERATED";
    public static final String EMAIL_INGEST = "EMAIL_INGEST";

    private Source() {}
  }
}
