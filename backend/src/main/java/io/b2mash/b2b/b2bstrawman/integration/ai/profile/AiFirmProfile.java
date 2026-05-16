package io.b2mash.b2b.b2bstrawman.integration.ai.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_firm_profiles")
public class AiFirmProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "practice_areas", columnDefinition = "jsonb", nullable = false)
  private List<String> practiceAreas = new ArrayList<>();

  @Column(name = "jurisdiction", nullable = false, length = 10)
  private String jurisdiction = "ZA";

  @Column(name = "risk_calibration", nullable = false, length = 20)
  private String riskCalibration = "CONSERVATIVE";

  @Column(name = "house_style_notes")
  private String houseStyleNotes;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "fica_requirements", columnDefinition = "jsonb")
  private Map<String, Object> ficaRequirements = new HashMap<>();

  @Column(name = "fee_estimation_notes")
  private String feeEstimationNotes;

  @Column(name = "preferred_model", nullable = false, length = 40)
  private String preferredModel = "claude-sonnet-4-6";

  @Column(name = "monthly_budget_cents")
  private Long monthlyBudgetCents;

  @Column(name = "profile_version", nullable = false)
  private int profileVersion = 1;

  @Column(name = "cold_start_completed", nullable = false)
  private boolean coldStartCompleted = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "updated_by", nullable = false)
  private UUID updatedBy;

  protected AiFirmProfile() {}

  public AiFirmProfile(UUID createdBy) {
    this.createdBy = createdBy;
    this.updatedBy = createdBy;
  }

  @PrePersist
  void onPrePersist() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @PreUpdate
  void onPreUpdate() {
    this.updatedAt = Instant.now();
  }

  public void updateProfile(
      List<String> practiceAreas,
      String jurisdiction,
      String riskCalibration,
      String houseStyleNotes,
      Map<String, Object> ficaRequirements,
      String feeEstimationNotes,
      String preferredModel,
      Long monthlyBudgetCents,
      Boolean coldStartCompleted,
      UUID updatedBy) {
    this.practiceAreas = practiceAreas != null ? practiceAreas : this.practiceAreas;
    this.jurisdiction = jurisdiction != null ? jurisdiction : this.jurisdiction;
    this.riskCalibration = riskCalibration != null ? riskCalibration : this.riskCalibration;
    this.houseStyleNotes = houseStyleNotes != null ? houseStyleNotes : this.houseStyleNotes;
    this.ficaRequirements = ficaRequirements != null ? ficaRequirements : this.ficaRequirements;
    this.feeEstimationNotes =
        feeEstimationNotes != null ? feeEstimationNotes : this.feeEstimationNotes;
    this.preferredModel = preferredModel != null ? preferredModel : this.preferredModel;
    this.monthlyBudgetCents =
        monthlyBudgetCents != null ? monthlyBudgetCents : this.monthlyBudgetCents;
    if (coldStartCompleted != null) {
      this.coldStartCompleted = coldStartCompleted;
    }
    this.updatedBy = updatedBy;
    this.profileVersion++;
  }

  public void markColdStartCompleted() {
    this.coldStartCompleted = true;
  }

  // ── Getters ───────────────────────────────────────────────────────────────

  public UUID getId() {
    return id;
  }

  public List<String> getPracticeAreas() {
    return practiceAreas;
  }

  public String getJurisdiction() {
    return jurisdiction;
  }

  public String getRiskCalibration() {
    return riskCalibration;
  }

  public String getHouseStyleNotes() {
    return houseStyleNotes;
  }

  public Map<String, Object> getFicaRequirements() {
    return ficaRequirements;
  }

  public String getFeeEstimationNotes() {
    return feeEstimationNotes;
  }

  public String getPreferredModel() {
    return preferredModel;
  }

  public Long getMonthlyBudgetCents() {
    return monthlyBudgetCents;
  }

  public int getProfileVersion() {
    return profileVersion;
  }

  public boolean isColdStartCompleted() {
    return coldStartCompleted;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }
}
