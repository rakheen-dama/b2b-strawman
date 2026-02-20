package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

  protected OrgSettings() {}

  public OrgSettings(String defaultCurrency) {
    this.defaultCurrency = defaultCurrency;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
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
}
