package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Collections / dunning policy settings group (Phase 83). Holds the firm's collection-chase
 * configuration: the master enable switch plus the four days-overdue thresholds that drive the
 * dunning ladder — stage 1 (gentle nudge), stage 2 (firm reminder), stage 3 (final demand), and the
 * escalation flag (flag for a partner call; no email). Consumed by the collections scan/handler
 * (589A) and the settings API (588B).
 *
 * <p>Persisted inline on the {@code org_settings} table via {@code @Embedded} +
 * {@code @AttributeOverride} on {@link OrgSettings} (added by Flyway tenant migration V133 — this
 * is a deliberate {@code OrgSettingsSchemaSnapshotTest} pin update, not a zero-schema-change
 * refactor). {@code collections_enabled} is NOT NULL, so it is modelled as a primitive {@code
 * boolean} (defaults to {@code false}) and the embedded group never fully materialises as NULL on
 * reload — therefore no new {@code OrgSettingsEmbeddableNullReloadTest} case is needed. The four
 * threshold columns are nullable; their default policy (7/21/45/60) is reproduced via field
 * initialisers so a fresh embeddable persists sensible non-null thresholds, while an existing DB
 * row reloads whatever is stored (Hibernate overwrites the initialisers on load, so persisted NULLs
 * stay NULL).
 *
 * <p>Field-level setters here intentionally do NOT bump {@code OrgSettings.updatedAt}; the
 * embeddable has no reference to the owning entity's timestamp. Hibernate dirty-checks the embedded
 * columns and persists changes regardless, and {@code OrgSettings}'s {@code @PreUpdate} callback
 * refreshes {@code updatedAt} on any dirty flush. The {@link #updateCollectionsSettings} domain
 * mutator provides an atomic five-field update.
 */
@Embeddable
public class CollectionsSettings {

  @Column(name = "collections_enabled", nullable = false)
  private boolean collectionsEnabled;

  @Column(name = "collections_stage1_days")
  private Integer stage1DaysOverdue = 7;

  @Column(name = "collections_stage2_days")
  private Integer stage2DaysOverdue = 21;

  @Column(name = "collections_stage3_days")
  private Integer stage3DaysOverdue = 45;

  @Column(name = "collections_escalate_days")
  private Integer escalateDaysOverdue = 60;

  protected CollectionsSettings() {}

  public boolean isCollectionsEnabled() {
    return collectionsEnabled;
  }

  public void setCollectionsEnabled(boolean collectionsEnabled) {
    this.collectionsEnabled = collectionsEnabled;
  }

  public Integer getStage1DaysOverdue() {
    return stage1DaysOverdue;
  }

  public void setStage1DaysOverdue(Integer stage1DaysOverdue) {
    this.stage1DaysOverdue = stage1DaysOverdue;
  }

  public Integer getStage2DaysOverdue() {
    return stage2DaysOverdue;
  }

  public void setStage2DaysOverdue(Integer stage2DaysOverdue) {
    this.stage2DaysOverdue = stage2DaysOverdue;
  }

  public Integer getStage3DaysOverdue() {
    return stage3DaysOverdue;
  }

  public void setStage3DaysOverdue(Integer stage3DaysOverdue) {
    this.stage3DaysOverdue = stage3DaysOverdue;
  }

  public Integer getEscalateDaysOverdue() {
    return escalateDaysOverdue;
  }

  public void setEscalateDaysOverdue(Integer escalateDaysOverdue) {
    this.escalateDaysOverdue = escalateDaysOverdue;
  }

  /** Updates all collections policy fields atomically. */
  public void updateCollectionsSettings(
      boolean enabled, Integer stage1, Integer stage2, Integer stage3, Integer escalate) {
    this.collectionsEnabled = enabled;
    this.stage1DaysOverdue = stage1;
    this.stage2DaysOverdue = stage2;
    this.stage3DaysOverdue = stage3;
    this.escalateDaysOverdue = escalate;
  }
}
