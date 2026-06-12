package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Data-protection / POPIA settings group (Wave 3.5 embeddable refactor). Holds the firm's
 * privacy-and-retention identity: the operating jurisdiction, the retention-policy master toggle
 * and its default/financial/legal-matter retention windows, and the designated information
 * officer's name and email. These are the fields surfaced and atomically updated by the
 * data-protection settings panel (see {@code OrgSettingsService.updateDataProtectionSettings} and
 * the {@link #updateDataProtectionSettings} domain mutator below).
 *
 * <p>The operational request-handling knobs (data-request deadline, request-reminder cadence,
 * customer dormancy threshold) are deliberately NOT in this group — they drive schedulers and the
 * separate {@code updateComplianceSettings} endpoint rather than the POPIA officer panel, and live
 * in {@link DataRequestSettings}.
 *
 * <p>Persisted inline on the {@code org_settings} table via {@code @Embedded} +
 * {@code @AttributeOverride} on {@link OrgSettings}. Column names, types, and nullability are
 * UNCHANGED from when these fields lived directly on the entity (zero schema change — see {@code
 * OrgSettingsSchemaSnapshotTest}). {@code retention_policy_enabled} is NOT NULL, so it is modelled
 * as a primitive {@code boolean} (defaults to {@code false}) and the embedded never fully
 * materialises as NULL on reload; the remaining columns are nullable. {@code
 * financial_retention_months} reproduces its former field-initialiser default (60) so a fresh
 * embeddable persists the same value the entity used to.
 *
 * <p>Field-level setters here intentionally do NOT bump {@code OrgSettings.updatedAt}; the
 * embeddable has no reference to the owning entity's timestamp. Hibernate dirty-checks the embedded
 * columns and persists changes regardless.
 */
@Embeddable
public class DataProtectionSettings {

  /** Default retention window (in years) for CLOSED legal matters (Phase 67, ADR-249). */
  public static final int DEFAULT_LEGAL_MATTER_RETENTION_YEARS = 5;

  @Column(name = "data_protection_jurisdiction", length = 10)
  private String dataProtectionJurisdiction;

  @Column(name = "retention_policy_enabled", nullable = false)
  private boolean retentionPolicyEnabled;

  @Column(name = "default_retention_months")
  private Integer defaultRetentionMonths;

  @Column(name = "financial_retention_months")
  private Integer financialRetentionMonths = 60;

  @Column(name = "information_officer_name", length = 255)
  private String informationOfficerName;

  @Column(name = "information_officer_email", length = 255)
  private String informationOfficerEmail;

  /**
   * Retention period (in years) for CLOSED legal matters (Phase 67, ADR-249). When null, defaults
   * to {@link #DEFAULT_LEGAL_MATTER_RETENTION_YEARS} — expose via {@link
   * #getEffectiveLegalMatterRetentionYears()}.
   */
  @Column(name = "legal_matter_retention_years")
  @Min(value = 1, message = "legalMatterRetentionYears must be at least 1")
  @Max(value = 100, message = "legalMatterRetentionYears must be at most 100")
  private Integer legalMatterRetentionYears;

  protected DataProtectionSettings() {}

  public String getDataProtectionJurisdiction() {
    return dataProtectionJurisdiction;
  }

  public void setDataProtectionJurisdiction(String dataProtectionJurisdiction) {
    this.dataProtectionJurisdiction = dataProtectionJurisdiction;
  }

  public boolean isRetentionPolicyEnabled() {
    return retentionPolicyEnabled;
  }

  public void setRetentionPolicyEnabled(boolean retentionPolicyEnabled) {
    this.retentionPolicyEnabled = retentionPolicyEnabled;
  }

  public Integer getDefaultRetentionMonths() {
    return defaultRetentionMonths;
  }

  public void setDefaultRetentionMonths(Integer defaultRetentionMonths) {
    this.defaultRetentionMonths = defaultRetentionMonths;
  }

  public Integer getFinancialRetentionMonths() {
    return financialRetentionMonths;
  }

  public void setFinancialRetentionMonths(Integer financialRetentionMonths) {
    this.financialRetentionMonths = financialRetentionMonths;
  }

  public String getInformationOfficerName() {
    return informationOfficerName;
  }

  public void setInformationOfficerName(String informationOfficerName) {
    this.informationOfficerName = informationOfficerName;
  }

  public String getInformationOfficerEmail() {
    return informationOfficerEmail;
  }

  public void setInformationOfficerEmail(String informationOfficerEmail) {
    this.informationOfficerEmail = informationOfficerEmail;
  }

  public Integer getLegalMatterRetentionYears() {
    return legalMatterRetentionYears;
  }

  /**
   * Returns the effective legal-matter retention years, falling back to {@link
   * #DEFAULT_LEGAL_MATTER_RETENTION_YEARS} when unset. Used by the retention-clock machinery
   * (ADR-249) when anchoring a retention policy on matter closure.
   */
  public int getEffectiveLegalMatterRetentionYears() {
    return legalMatterRetentionYears != null
        ? legalMatterRetentionYears
        : DEFAULT_LEGAL_MATTER_RETENTION_YEARS;
  }

  public void setLegalMatterRetentionYears(Integer legalMatterRetentionYears) {
    if (legalMatterRetentionYears != null
        && (legalMatterRetentionYears < 1 || legalMatterRetentionYears > 100)) {
      throw new IllegalArgumentException(
          "legalMatterRetentionYears must be between 1 and 100 (got "
              + legalMatterRetentionYears
              + ")");
    }
    this.legalMatterRetentionYears = legalMatterRetentionYears;
  }

  /** Updates all data protection panel settings atomically. */
  public void updateDataProtectionSettings(
      String jurisdiction,
      boolean retentionEnabled,
      Integer defaultRetentionMonths,
      Integer financialRetentionMonths,
      String officerName,
      String officerEmail) {
    this.dataProtectionJurisdiction = jurisdiction;
    this.retentionPolicyEnabled = retentionEnabled;
    this.defaultRetentionMonths = defaultRetentionMonths;
    this.financialRetentionMonths = financialRetentionMonths;
    this.informationOfficerName = officerName;
    this.informationOfficerEmail = officerEmail;
  }
}
