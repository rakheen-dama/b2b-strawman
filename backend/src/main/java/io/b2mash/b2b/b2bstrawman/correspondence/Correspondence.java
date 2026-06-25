package io.b2mash.b2b.b2bstrawman.correspondence;

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
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A single filed email/correspondence (one row per filed message). Linkage is flat with at least
 * one of {@code customerId} / {@code projectId} non-null (enforced by the table CHECK and by
 * service validation). Idempotency is keyed on {@code messageId} (UNIQUE index).
 *
 * <p>Pure schema-per-tenant isolation — no {@code tenant_id} column, no {@code @Filter}.
 */
@Entity
@Table(name = "correspondence")
public class Correspondence {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id")
  private UUID customerId; // raw UUID FK, not @ManyToOne

  @Column(name = "project_id")
  private UUID projectId; // raw UUID FK (the matter)

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 10)
  private Direction direction = Direction.INBOUND;

  @Column(name = "subject", length = 500)
  private String subject;

  @Column(name = "body_text", columnDefinition = "TEXT")
  private String bodyText;

  @Column(name = "body_html", columnDefinition = "TEXT")
  private String bodyHtml;

  @Column(name = "from_address", nullable = false, length = 320)
  private String fromAddress;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "to_addresses", columnDefinition = "jsonb")
  private List<String> toAddresses;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "cc_addresses", columnDefinition = "jsonb")
  private List<String> ccAddresses;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "received_at")
  private Instant receivedAt;

  @Column(name = "thread_key", length = 255)
  private String threadKey;

  @Column(name = "message_id", nullable = false, length = 512)
  private String messageId; // idempotency key

  @Column(name = "source", nullable = false, length = 30)
  private String source = "MCP";

  @Column(name = "filed_by_member_id", nullable = false)
  private UUID filedByMemberId;

  @Column(name = "filed_at", nullable = false)
  private Instant filedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private int version;

  /** JPA-required no-arg constructor. */
  protected Correspondence() {}

  /**
   * Construct an inbound correspondence ready to be filed. Timestamps are set by {@link
   * #onCreate()} — do not set them here.
   */
  public Correspondence(
      UUID customerId,
      UUID projectId,
      String subject,
      String bodyText,
      String bodyHtml,
      String fromAddress,
      List<String> toAddresses,
      List<String> ccAddresses,
      Instant sentAt,
      Instant receivedAt,
      String threadKey,
      String messageId,
      String source,
      UUID filedByMemberId) {
    this.customerId = customerId;
    this.projectId = projectId;
    this.direction = Direction.INBOUND;
    this.subject = subject;
    this.bodyText = bodyText;
    this.bodyHtml = bodyHtml;
    this.fromAddress = fromAddress;
    this.toAddresses = toAddresses;
    this.ccAddresses = ccAddresses;
    this.sentAt = sentAt;
    this.receivedAt = receivedAt;
    this.threadKey = threadKey;
    this.messageId = messageId;
    this.source = (source == null || source.isBlank()) ? "MCP" : source;
    this.filedByMemberId = filedByMemberId;
  }

  @PrePersist
  void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
    if (this.filedAt == null) {
      this.filedAt = now;
    }
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public Direction getDirection() {
    return direction;
  }

  public String getSubject() {
    return subject;
  }

  public String getBodyText() {
    return bodyText;
  }

  public String getBodyHtml() {
    return bodyHtml;
  }

  public String getFromAddress() {
    return fromAddress;
  }

  public List<String> getToAddresses() {
    return toAddresses;
  }

  public List<String> getCcAddresses() {
    return ccAddresses;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public String getThreadKey() {
    return threadKey;
  }

  public String getMessageId() {
    return messageId;
  }

  public String getSource() {
    return source;
  }

  public UUID getFiledByMemberId() {
    return filedByMemberId;
  }

  public Instant getFiledAt() {
    return filedAt;
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
