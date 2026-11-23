package io.b2mash.b2b.b2bstrawman.takeon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One act of responsibility over a validated take-on batch (§17.2.4, ADR-388, ADR-391): the FICA
 * attestation for a clients batch, or the partner/director sign-off for a trust position. It binds
 * to the batch's validation hash; a re-validation that changes the hash revokes it. At most one
 * live sign-off of each kind per batch ({@code uq_take_on_sign_offs_live}). Written from 655A.
 */
@Entity
@Table(name = "take_on_sign_offs")
public class TakeOnSignOff {

  /** Stored in {@code take_on_sign_offs.revoked_reason} (CHECK chk_take_on_sign_off_reason). */
  public enum RevokedReason {
    REVALIDATED,
    REVOKED,
    CANCELLED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "batch_id", nullable = false, updatable = false)
  private UUID batchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 20, updatable = false)
  private TakeOnSignOffKind kind;

  @Column(name = "member_id", nullable = false, updatable = false)
  private UUID memberId;

  @Column(name = "member_name", nullable = false, length = 255, updatable = false)
  private String memberName;

  @Column(name = "role_name", nullable = false, length = 100, updatable = false)
  private String roleName;

  @Column(name = "statement_version", nullable = false, length = 40, updatable = false)
  private String statementVersion;

  @Column(name = "statement_text", nullable = false, updatable = false, columnDefinition = "text")
  private String statementText;

  @Column(name = "validation_hash", nullable = false, length = 64, updatable = false)
  private String validationHash;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "batch_defaults", columnDefinition = "jsonb", updatable = false)
  private Map<String, Object> batchDefaults;

  @Column(name = "signed_at", nullable = false, updatable = false)
  private Instant signedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "revoked_reason", length = 30)
  private RevokedReason revokedReason;

  protected TakeOnSignOff() {}

  public TakeOnSignOff(
      UUID batchId,
      TakeOnSignOffKind kind,
      UUID memberId,
      String memberName,
      String roleName,
      String statementVersion,
      String statementText,
      String validationHash,
      Map<String, Object> batchDefaults) {
    this.batchId = Objects.requireNonNull(batchId, "batchId");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.memberId = Objects.requireNonNull(memberId, "memberId");
    this.memberName = Objects.requireNonNull(memberName, "memberName");
    this.roleName = Objects.requireNonNull(roleName, "roleName");
    this.statementVersion = Objects.requireNonNull(statementVersion, "statementVersion");
    this.statementText = Objects.requireNonNull(statementText, "statementText");
    this.validationHash = Objects.requireNonNull(validationHash, "validationHash");
    this.batchDefaults = batchDefaults;
    this.signedAt = Instant.now();
  }

  /**
   * A stand-in for datasets that need no sign-off (matters, debtors — 655A+): it carries only the
   * validation hash so {@link TakeOnBatch#claimCommit} can check the batch hash alone. It is an
   * <strong>unpersisted</strong> instance and must never be saved (it has no batch, kind or member,
   * so the NOT NULL columns would refuse it anyway).
   */
  public static TakeOnSignOff notRequired(String hash) {
    var stand = new TakeOnSignOff();
    stand.validationHash = Objects.requireNonNull(hash, "hash");
    return stand;
  }

  /** Withdraws the sign-off; a revoked one cannot be revoked again. */
  public void revoke(RevokedReason reason) {
    if (revokedAt != null) {
      throw new IllegalStateException("This sign-off has already been withdrawn.");
    }
    this.revokedReason = Objects.requireNonNull(reason, "reason");
    this.revokedAt = Instant.now();
  }

  public boolean isLive() {
    return revokedAt == null;
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getBatchId() {
    return batchId;
  }

  public TakeOnSignOffKind getKind() {
    return kind;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public String getMemberName() {
    return memberName;
  }

  public String getRoleName() {
    return roleName;
  }

  public String getStatementVersion() {
    return statementVersion;
  }

  public String getStatementText() {
    return statementText;
  }

  public String getValidationHash() {
    return validationHash;
  }

  public Map<String, Object> getBatchDefaults() {
    return batchDefaults;
  }

  public Instant getSignedAt() {
    return signedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public RevokedReason getRevokedReason() {
    return revokedReason;
  }
}
