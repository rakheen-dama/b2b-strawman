package io.b2mash.b2b.b2bstrawman.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "automation_rules")
public class AutomationRule {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "description", length = 1000)
  private String description;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_type", nullable = false, length = 50)
  private TriggerType triggerType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "trigger_config", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> triggerConfig;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "conditions", columnDefinition = "jsonb")
  private List<Map<String, Object>> conditions;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 20)
  private RuleSource source;

  @Column(name = "template_slug", length = 100)
  private String templateSlug;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "source_pack_install_id")
  private UUID sourcePackInstallId;

  @Column(name = "content_hash", length = 64)
  private String contentHash;

  protected AutomationRule() {}

  public AutomationRule(
      String name,
      String description,
      TriggerType triggerType,
      Map<String, Object> triggerConfig,
      List<Map<String, Object>> conditions,
      RuleSource source,
      String templateSlug,
      UUID createdBy) {
    this.name = name;
    this.description = description;
    this.enabled = true;
    this.triggerType = triggerType;
    this.triggerConfig = triggerConfig;
    this.conditions = conditions;
    this.source = source;
    this.templateSlug = templateSlug;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void toggle() {
    this.enabled = !this.enabled;
    this.updatedAt = Instant.now();
  }

  public void update(
      String name,
      String description,
      TriggerType triggerType,
      Map<String, Object> triggerConfig,
      List<Map<String, Object>> conditions) {
    this.name = name;
    this.description = description;
    this.triggerType = triggerType;
    this.triggerConfig = triggerConfig;
    this.conditions = conditions;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public TriggerType getTriggerType() {
    return triggerType;
  }

  public Map<String, Object> getTriggerConfig() {
    return triggerConfig;
  }

  public List<Map<String, Object>> getConditions() {
    return conditions;
  }

  public RuleSource getSource() {
    return source;
  }

  public String getTemplateSlug() {
    return templateSlug;
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

  public UUID getSourcePackInstallId() {
    return sourcePackInstallId;
  }

  public void setSourcePackInstallId(UUID sourcePackInstallId) {
    this.sourcePackInstallId = sourcePackInstallId;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }
}
