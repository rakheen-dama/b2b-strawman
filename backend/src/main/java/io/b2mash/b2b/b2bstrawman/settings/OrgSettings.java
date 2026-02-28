package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "org_settings")
public class OrgSettings {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "default_currency", nullable = false, length = 3)
  private String defaultCurrency;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "field_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> fieldPackStatus;

  @Column(name = "logo_s3_key", length = 500)
  private String logoS3Key;

  @Column(name = "brand_color", length = 7)
  private String brandColor;

  @Column(name = "document_footer_text", columnDefinition = "TEXT")
  private String documentFooterText;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "template_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> templatePackStatus;

  @Column(name = "dormancy_threshold_days")
  private Integer dormancyThresholdDays;

  @Column(name = "data_request_deadline_days")
  private Integer dataRequestDeadlineDays;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "compliance_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> compliancePackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "report_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> reportPackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "clause_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> clausePackStatus;

  @Column(name = "acceptance_expiry_days")
  private Integer acceptanceExpiryDays;

  @Column(name = "accounting_enabled", nullable = false)
  private boolean accountingEnabled;

  @Column(name = "ai_enabled", nullable = false)
  private boolean aiEnabled;

  @Column(name = "document_signing_enabled", nullable = false)
  private boolean documentSigningEnabled;

  @Column(name = "tax_inclusive", nullable = false)
  private boolean taxInclusive;

  @Column(name = "tax_registration_number", length = 50)
  private String taxRegistrationNumber;

  @Column(name = "tax_registration_label", length = 30)
  private String taxRegistrationLabel;

  @Column(name = "tax_label", length = 20)
  private String taxLabel;

  @Column(name = "default_expense_markup_percent", precision = 5, scale = 2)
  private BigDecimal defaultExpenseMarkupPercent;

  protected OrgSettings() {}

  public OrgSettings(String defaultCurrency) {
    this.defaultCurrency = defaultCurrency;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
    this.accountingEnabled = false;
    this.aiEnabled = false;
    this.documentSigningEnabled = false;
    this.taxInclusive = false;
  }

  public void updateCurrency(String currency) {
    this.defaultCurrency = currency;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getDefaultCurrency() {
    return defaultCurrency;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public List<Map<String, Object>> getFieldPackStatus() {
    return fieldPackStatus;
  }

  public void setFieldPackStatus(List<Map<String, Object>> fieldPackStatus) {
    this.fieldPackStatus = fieldPackStatus;
  }

  /** Records a field pack application in the status list. */
  public void recordPackApplication(String packId, int version) {
    if (this.fieldPackStatus == null) {
      this.fieldPackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.fieldPackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  public String getLogoS3Key() {
    return logoS3Key;
  }

  public void setLogoS3Key(String logoS3Key) {
    this.logoS3Key = logoS3Key;
    this.updatedAt = Instant.now();
  }

  public String getBrandColor() {
    return brandColor;
  }

  public void setBrandColor(String brandColor) {
    this.brandColor = brandColor;
    this.updatedAt = Instant.now();
  }

  public String getDocumentFooterText() {
    return documentFooterText;
  }

  public void setDocumentFooterText(String text) {
    this.documentFooterText = text;
    this.updatedAt = Instant.now();
  }

  public List<Map<String, Object>> getTemplatePackStatus() {
    return templatePackStatus;
  }

  public void setTemplatePackStatus(List<Map<String, Object>> status) {
    this.templatePackStatus = status;
  }

  /** Records a template pack application in the status list. */
  public void recordTemplatePackApplication(String packId, int version) {
    if (this.templatePackStatus == null) {
      this.templatePackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.templatePackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  public Integer getDormancyThresholdDays() {
    return dormancyThresholdDays;
  }

  public void setDormancyThresholdDays(Integer dormancyThresholdDays) {
    this.dormancyThresholdDays = dormancyThresholdDays;
    this.updatedAt = Instant.now();
  }

  public Integer getDataRequestDeadlineDays() {
    return dataRequestDeadlineDays;
  }

  public void setDataRequestDeadlineDays(Integer dataRequestDeadlineDays) {
    this.dataRequestDeadlineDays = dataRequestDeadlineDays;
    this.updatedAt = Instant.now();
  }

  public List<Map<String, Object>> getCompliancePackStatus() {
    return compliancePackStatus;
  }

  /**
   * Records a compliance pack application in the status list. Uses String version (not int) because
   * compliance packs use semantic versioning (e.g. "1.0.0"), unlike field/template packs which use
   * sequential integers.
   */
  public void recordCompliancePackApplication(String packId, String version) {
    if (this.compliancePackStatus == null) {
      this.compliancePackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.compliancePackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  public List<Map<String, Object>> getReportPackStatus() {
    return reportPackStatus;
  }

  /** Records a report pack application in the status list. */
  public void recordReportPackApplication(String packId, int version) {
    if (this.reportPackStatus == null) {
      this.reportPackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.reportPackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  public List<Map<String, Object>> getClausePackStatus() {
    return clausePackStatus;
  }

  public void setClausePackStatus(List<Map<String, Object>> status) {
    this.clausePackStatus = status;
  }

  /** Records a clause pack application in the status list. */
  public void recordClausePackApplication(String packId, int version) {
    if (this.clausePackStatus == null) {
      this.clausePackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.clausePackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  public Integer getAcceptanceExpiryDays() {
    return acceptanceExpiryDays;
  }

  public void setAcceptanceExpiryDays(Integer acceptanceExpiryDays) {
    this.acceptanceExpiryDays = acceptanceExpiryDays;
    this.updatedAt = Instant.now();
  }

  /** Returns the configured acceptance expiry days, defaulting to 30 if not set. */
  public int getEffectiveAcceptanceExpiryDays() {
    return acceptanceExpiryDays != null ? acceptanceExpiryDays : 30;
  }

  public boolean isAccountingEnabled() {
    return accountingEnabled;
  }

  public boolean isAiEnabled() {
    return aiEnabled;
  }

  public boolean isDocumentSigningEnabled() {
    return documentSigningEnabled;
  }

  public boolean isTaxInclusive() {
    return taxInclusive;
  }

  public void setTaxInclusive(boolean taxInclusive) {
    this.taxInclusive = taxInclusive;
    this.updatedAt = Instant.now();
  }

  public String getTaxRegistrationNumber() {
    return taxRegistrationNumber;
  }

  public void setTaxRegistrationNumber(String taxRegistrationNumber) {
    this.taxRegistrationNumber = taxRegistrationNumber;
    this.updatedAt = Instant.now();
  }

  public String getTaxRegistrationLabel() {
    return taxRegistrationLabel;
  }

  public void setTaxRegistrationLabel(String taxRegistrationLabel) {
    this.taxRegistrationLabel = taxRegistrationLabel;
    this.updatedAt = Instant.now();
  }

  public String getTaxLabel() {
    return taxLabel;
  }

  public void setTaxLabel(String taxLabel) {
    this.taxLabel = taxLabel;
    this.updatedAt = Instant.now();
  }

  public BigDecimal getDefaultExpenseMarkupPercent() {
    return defaultExpenseMarkupPercent;
  }

  public void setDefaultExpenseMarkupPercent(BigDecimal defaultExpenseMarkupPercent) {
    this.defaultExpenseMarkupPercent = defaultExpenseMarkupPercent;
    this.updatedAt = Instant.now();
  }

  /** Updates all four tax configuration fields and the timestamp. */
  public void updateTaxSettings(
      String taxRegistrationNumber,
      String taxRegistrationLabel,
      String taxLabel,
      boolean taxInclusive) {
    this.taxRegistrationNumber = taxRegistrationNumber;
    this.taxRegistrationLabel = taxRegistrationLabel;
    this.taxLabel = taxLabel;
    this.taxInclusive = taxInclusive;
    this.updatedAt = Instant.now();
  }

  /** Updates all three integration domain flags and the timestamp. */
  public void updateIntegrationFlags(boolean accounting, boolean ai, boolean documentSigning) {
    this.accountingEnabled = accounting;
    this.aiEnabled = ai;
    this.documentSigningEnabled = documentSigning;
    this.updatedAt = Instant.now();
  }
}
