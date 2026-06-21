package io.b2mash.b2b.b2bstrawman.crm;

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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A sales opportunity (Phase 80, §11.8). Always linked to a Customer. Rich-domain entity: lifecycle
 * transitions (won / lost / reopen / move) are guarded methods; editable fields use ungated setters
 * (editable in any status).
 *
 * <p>Pure schema-per-tenant: no {@code tenant_id}, no {@code @Filter}. Cross-aggregate references
 * ({@code customerId}/{@code stageId}/{@code ownerId}/{@code pipelineId}) are raw UUIDs, not
 * {@code @ManyToOne}. {@code pipelineId} is reserved and always {@code null} in v1.
 */
@Entity
@Table(name = "deals")
public class Deal {

  private static final Set<DealStatus> TERMINAL = DealStatus.TERMINAL_STATUSES;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "deal_number", nullable = false, unique = true, length = 40)
  private String dealNumber;

  @Column(name = "pipeline_id")
  private UUID pipelineId; // reserved: always NULL in v1

  @Column(name = "customer_id", nullable = false)
  private UUID customerId; // raw UUID ref, NOT @ManyToOne

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "stage_id", nullable = false)
  private UUID stageId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private DealStatus status = DealStatus.OPEN;

  @Column(name = "value_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal valueAmount = BigDecimal.ZERO;

  @Column(name = "value_currency", nullable = false, length = 3)
  private String valueCurrency;

  @Column(name = "probability_pct")
  private Integer probabilityPct; // nullable override

  @Column(name = "expected_close_date")
  private LocalDate expectedCloseDate;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "source", length = 40)
  private String source;

  @Column(name = "won_at")
  private Instant wonAt;

  @Column(name = "lost_at")
  private Instant lostAt;

  @Column(name = "lost_reason", length = 500)
  private String lostReason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "custom_fields", columnDefinition = "jsonb")
  private Map<String, Object> customFields = new HashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "applied_field_groups", columnDefinition = "jsonb")
  private List<UUID> appliedFieldGroups = new ArrayList<>();

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA-required no-arg constructor. */
  protected Deal() {}

  private Deal(
      String dealNumber,
      UUID customerId,
      String title,
      UUID stageId,
      BigDecimal valueAmount,
      String valueCurrency,
      UUID ownerId,
      String source,
      UUID createdBy) {
    this.dealNumber = Objects.requireNonNull(dealNumber, "dealNumber must not be null");
    this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
    this.title = Objects.requireNonNull(title, "title must not be null");
    this.stageId = Objects.requireNonNull(stageId, "stageId must not be null");
    this.valueAmount = valueAmount != null ? valueAmount : BigDecimal.ZERO;
    this.valueCurrency = Objects.requireNonNull(valueCurrency, "valueCurrency must not be null");
    this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
    this.source = source;
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
    this.status = DealStatus.OPEN;
  }

  /**
   * Factory for a new OPEN deal placed at the given stage. The {@code dealNumber} is allocated by
   * {@code DealNumberService} and passed in (the column is NOT NULL).
   */
  public static Deal create(
      String dealNumber,
      UUID customerId,
      String title,
      PipelineStage stage,
      BigDecimal valueAmount,
      String currency,
      UUID ownerId,
      String source,
      UUID createdBy) {
    Objects.requireNonNull(stage, "stage must not be null");
    return new Deal(
        dealNumber,
        customerId,
        title,
        stage.getId(),
        valueAmount,
        currency,
        ownerId,
        source,
        createdBy);
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

  // --- Probability / weighted value ---

  /** WON ⇒ 100, LOST ⇒ 0, else override ?? stage default ({@code resolvedStageDefault}). */
  public int effectiveProbabilityPct(int resolvedStageDefault) {
    return switch (status) {
      case WON -> 100;
      case LOST -> 0;
      case OPEN -> probabilityPct != null ? probabilityPct : resolvedStageDefault;
    };
  }

  public BigDecimal weightedValue(int effectiveProb) {
    return valueAmount
        .multiply(BigDecimal.valueOf(effectiveProb))
        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  // --- Lifecycle transitions (guarded) ---

  public void markWon(UUID wonStageId, Instant now) {
    requireStatus(EnumSet.of(DealStatus.OPEN), "win");
    this.stageId = Objects.requireNonNull(wonStageId, "wonStageId must not be null");
    this.status = DealStatus.WON;
    this.wonAt = now;
    this.lostAt = null;
    this.updatedAt = Instant.now();
  }

  public void markLost(UUID lostStageId, String reason, Instant now) {
    requireStatus(EnumSet.of(DealStatus.OPEN), "lose");
    if (reason == null || reason.isBlank()) {
      throw new InvalidStateException("Invalid deal state", "lostReason required");
    }
    this.stageId = Objects.requireNonNull(lostStageId, "lostStageId must not be null");
    this.status = DealStatus.LOST;
    this.lostReason = reason;
    this.lostAt = now;
    this.updatedAt = Instant.now();
  }

  public void reopen(UUID openStageId) {
    if (!TERMINAL.contains(status)) {
      throw new InvalidStateException("Invalid deal state", "deal is not closed");
    }
    this.stageId = Objects.requireNonNull(openStageId, "openStageId must not be null");
    this.status = DealStatus.OPEN;
    this.wonAt = null;
    this.lostAt = null;
    this.lostReason = null;
    this.updatedAt = Instant.now();
  }

  /** Moves an OPEN deal to another OPEN stage, optionally overriding the probability. */
  public void moveToOpenStage(UUID newStageId, Integer newProbabilityOverride) {
    requireStatus(EnumSet.of(DealStatus.OPEN), "move");
    this.stageId = Objects.requireNonNull(newStageId, "newStageId must not be null");
    this.probabilityPct = newProbabilityOverride;
    this.updatedAt = Instant.now();
  }

  // --- Editable-in-any-status setters (ungated) ---

  public void updateTitle(String title) {
    this.title = Objects.requireNonNull(title, "title must not be null");
    this.updatedAt = Instant.now();
  }

  public void updateValue(BigDecimal valueAmount, String valueCurrency) {
    this.valueAmount = valueAmount != null ? valueAmount : BigDecimal.ZERO;
    this.valueCurrency = Objects.requireNonNull(valueCurrency, "valueCurrency must not be null");
    this.updatedAt = Instant.now();
  }

  public void updateOwner(UUID ownerId) {
    this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
    this.updatedAt = Instant.now();
  }

  public void updateExpectedClose(LocalDate expectedCloseDate) {
    this.expectedCloseDate = expectedCloseDate;
    this.updatedAt = Instant.now();
  }

  public void updateProbabilityOverride(Integer probabilityPct) {
    this.probabilityPct = probabilityPct;
    this.updatedAt = Instant.now();
  }

  public void updateSource(String source) {
    this.source = source;
    this.updatedAt = Instant.now();
  }

  public void setCustomFields(Map<String, Object> customFields) {
    this.customFields = customFields != null ? customFields : new HashMap<>();
    this.updatedAt = Instant.now();
  }

  public void setAppliedFieldGroups(List<UUID> appliedFieldGroups) {
    this.appliedFieldGroups = appliedFieldGroups != null ? appliedFieldGroups : new ArrayList<>();
    this.updatedAt = Instant.now();
  }

  // --- Guards ---

  private void requireStatus(Set<DealStatus> allowed, String action) {
    if (!allowed.contains(status)) {
      throw new InvalidStateException(
          "Invalid deal state", "Cannot %s a deal in status %s".formatted(action, status));
    }
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public String getDealNumber() {
    return dealNumber;
  }

  public UUID getPipelineId() {
    return pipelineId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getTitle() {
    return title;
  }

  public UUID getStageId() {
    return stageId;
  }

  public DealStatus getStatus() {
    return status;
  }

  public BigDecimal getValueAmount() {
    return valueAmount;
  }

  public String getValueCurrency() {
    return valueCurrency;
  }

  public Integer getProbabilityPct() {
    return probabilityPct;
  }

  public LocalDate getExpectedCloseDate() {
    return expectedCloseDate;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getSource() {
    return source;
  }

  public Instant getWonAt() {
    return wonAt;
  }

  public Instant getLostAt() {
    return lostAt;
  }

  public String getLostReason() {
    return lostReason;
  }

  public Map<String, Object> getCustomFields() {
    return customFields;
  }

  public List<UUID> getAppliedFieldGroups() {
    return appliedFieldGroups;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
