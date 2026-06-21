package io.b2mash.b2b.b2bstrawman.crm;

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
import java.util.UUID;

/**
 * A configurable stage in the sales pipeline (Phase 80, §11.2.1). Stages are org-configurable and
 * vertical-seeded. Every pipeline must always retain at least one stage of each {@link StageType}
 * (OPEN / WON / LOST) — enforced by {@code PipelineStageService}.
 *
 * <p>Pure schema-per-tenant: no {@code tenant_id} column, no {@code @Filter}. {@code pipelineId} is
 * reserved for a future multi-pipeline feature and is always {@code null} in v1.
 */
@Entity
@Table(name = "pipeline_stages")
public class PipelineStage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "pipeline_id")
  private UUID pipelineId; // reserved: always NULL in v1

  @Column(name = "name", nullable = false, length = 80)
  private String name;

  @Column(name = "position", nullable = false)
  private int position;

  @Column(name = "default_probability_pct", nullable = false)
  private int defaultProbabilityPct;

  @Enumerated(EnumType.STRING)
  @Column(name = "stage_type", nullable = false, length = 10)
  private StageType stageType;

  @Column(name = "archived", nullable = false)
  private boolean archived = false;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA-required no-arg constructor. */
  protected PipelineStage() {}

  public PipelineStage(
      String name, int position, int defaultProbabilityPct, StageType stageType, UUID createdBy) {
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.position = position;
    this.defaultProbabilityPct = defaultProbabilityPct;
    this.stageType = Objects.requireNonNull(stageType, "stageType must not be null");
    this.createdBy = createdBy;
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

  // --- Guarded mutators for the config service ---

  public void rename(String name) {
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.updatedAt = Instant.now();
  }

  public void changeDefaultProbability(int defaultProbabilityPct) {
    this.defaultProbabilityPct = defaultProbabilityPct;
    this.updatedAt = Instant.now();
  }

  public void changeStageType(StageType stageType) {
    this.stageType = Objects.requireNonNull(stageType, "stageType must not be null");
    this.updatedAt = Instant.now();
  }

  public void changePosition(int position) {
    this.position = position;
    this.updatedAt = Instant.now();
  }

  public void archive() {
    this.archived = true;
    this.updatedAt = Instant.now();
  }

  public void unarchive() {
    this.archived = false;
    this.updatedAt = Instant.now();
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getPipelineId() {
    return pipelineId;
  }

  public String getName() {
    return name;
  }

  public int getPosition() {
    return position;
  }

  public int getDefaultProbabilityPct() {
    return defaultProbabilityPct;
  }

  public StageType getStageType() {
    return stageType;
  }

  public boolean isArchived() {
    return archived;
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
