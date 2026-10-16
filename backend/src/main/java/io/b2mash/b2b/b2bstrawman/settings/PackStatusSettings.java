package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Pack-application status group (Wave 3.5 embeddable refactor). Holds the ten JSONB pack-status
 * lists that record which content packs (field, template, compliance, report, clause, request,
 * automation, rate, schedule, project-template) have been seeded into the tenant, together with the
 * idempotency record/remove/is-applied helpers each pack seeder uses. Each list is a {@code
 * List<Map<String, Object>>} of {@code {packId, version, appliedAt}} entries.
 *
 * <p>Persisted inline on the {@code org_settings} table via {@code @Embedded} +
 * {@code @AttributeOverride} on {@link OrgSettings}. Column names and the {@code jsonb} type are
 * UNCHANGED from when these fields lived directly on the entity (zero schema change — see {@code
 * OrgSettingsSchemaSnapshotTest}). All ten columns are nullable, so an all-null group materialises
 * as a NULL embedded object on reload — the lazy-fallback {@link OrgSettings#getPackStatus()}
 * getter keeps callers NPE-safe (see {@code OrgSettingsEmbeddableNullReloadTest}).
 *
 * <p>The {@code record*}/{@code remove*} helpers here intentionally do NOT bump {@code
 * OrgSettings.updatedAt}; the embeddable has no reference to the owning entity's timestamp. The
 * entity-level {@code @PreUpdate} callback refreshes the timestamp on any dirty flush, so
 * persisting a pack-status mutation still updates {@code updated_at}.
 */
@Embeddable
public class PackStatusSettings {

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "field_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> fieldPackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "template_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> templatePackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "compliance_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> compliancePackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "report_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> reportPackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "clause_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> clausePackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> requestPackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "automation_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> automationPackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rate_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> ratePackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "schedule_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> schedulePackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "project_template_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> projectTemplatePackStatus;

  protected PackStatusSettings() {}

  private static Map<String, Object> newEntry(String packId, Object version) {
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    return entry;
  }

  // --- Field pack ---

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
    this.fieldPackStatus.add(newEntry(packId, version));
  }

  // --- Template pack ---

  public List<Map<String, Object>> getTemplatePackStatus() {
    return templatePackStatus;
  }

  public void setTemplatePackStatus(List<Map<String, Object>> status) {
    this.templatePackStatus = status;
  }

  /** Removes a template pack entry from the status list by packId. */
  public void removeTemplatePackEntry(String packId) {
    if (this.templatePackStatus != null) {
      this.templatePackStatus.removeIf(entry -> packId.equals(entry.get("packId")));
    }
  }

  /**
   * Records a template pack application in the status list. Idempotent -- removes any existing
   * entry for the same packId before appending, preventing duplicates on re-install.
   */
  public void recordTemplatePackApplication(String packId, int version) {
    if (this.templatePackStatus == null) {
      this.templatePackStatus = new ArrayList<>();
    }
    this.templatePackStatus.removeIf(e -> packId.equals(e.get("packId")));
    this.templatePackStatus.add(newEntry(packId, version));
  }

  // --- Compliance pack ---

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
    this.compliancePackStatus.add(newEntry(packId, version));
  }

  // --- Report pack ---

  public List<Map<String, Object>> getReportPackStatus() {
    return reportPackStatus;
  }

  /** Records a report pack application in the status list. */
  public void recordReportPackApplication(String packId, int version) {
    if (this.reportPackStatus == null) {
      this.reportPackStatus = new ArrayList<>();
    }
    this.reportPackStatus.add(newEntry(packId, version));
  }

  // --- Clause pack ---

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
    this.clausePackStatus.add(newEntry(packId, version));
  }

  // --- Request pack ---

  public List<Map<String, Object>> getRequestPackStatus() {
    return requestPackStatus;
  }

  public void setRequestPackStatus(List<Map<String, Object>> status) {
    this.requestPackStatus = status;
  }

  /** Records a request pack application in the status list. */
  public void recordRequestPackApplication(String packId, int version) {
    if (this.requestPackStatus == null) {
      this.requestPackStatus = new ArrayList<>();
    }
    this.requestPackStatus.add(newEntry(packId, version));
  }

  // --- Automation pack ---

  public List<Map<String, Object>> getAutomationPackStatus() {
    return automationPackStatus;
  }

  public void setAutomationPackStatus(List<Map<String, Object>> status) {
    this.automationPackStatus = status;
  }

  /** Removes an automation pack entry from the status list by packId. */
  public void removeAutomationPackEntry(String packId) {
    if (this.automationPackStatus != null) {
      this.automationPackStatus.removeIf(entry -> packId.equals(entry.get("packId")));
    }
  }

  /**
   * Records an automation pack application in the status list. Idempotent -- removes any existing
   * entry for the same packId before appending, preventing duplicates on re-install.
   */
  public void recordAutomationPackApplication(String packId, int version) {
    if (this.automationPackStatus == null) {
      this.automationPackStatus = new ArrayList<>();
    }
    this.automationPackStatus.removeIf(e -> packId.equals(e.get("packId")));
    this.automationPackStatus.add(newEntry(packId, version));
  }

  /** Checks whether an automation pack has already been applied. */
  public boolean isAutomationPackApplied(String packId) {
    if (this.automationPackStatus == null) {
      return false;
    }
    return this.automationPackStatus.stream().anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  // --- Rate pack ---

  public List<Map<String, Object>> getRatePackStatus() {
    return ratePackStatus;
  }

  /** Records a rate pack application. Idempotent -- skips if already applied. */
  public void recordRatePackApplication(String packId, int version) {
    if (isRatePackApplied(packId, version)) {
      return;
    }
    if (this.ratePackStatus == null) {
      this.ratePackStatus = new ArrayList<>();
    }
    this.ratePackStatus.add(newEntry(packId, version));
  }

  /** Returns true if the given rate pack (specific version) has been applied. */
  public boolean isRatePackApplied(String packId, int version) {
    if (this.ratePackStatus == null) {
      return false;
    }
    return this.ratePackStatus.stream()
        .anyMatch(
            entry ->
                packId.equals(entry.get("packId"))
                    && ((Number) entry.get("version")).intValue() == version);
  }

  // --- Schedule pack ---

  public List<Map<String, Object>> getSchedulePackStatus() {
    return schedulePackStatus;
  }

  /** Records a schedule pack application. Idempotent -- skips if already applied. */
  public void recordSchedulePackApplication(String packId, int version) {
    if (isSchedulePackApplied(packId, version)) {
      return;
    }
    if (this.schedulePackStatus == null) {
      this.schedulePackStatus = new ArrayList<>();
    }
    this.schedulePackStatus.add(newEntry(packId, version));
  }

  /** Returns true if the given schedule pack (specific version) has been applied. */
  public boolean isSchedulePackApplied(String packId, int version) {
    if (this.schedulePackStatus == null) {
      return false;
    }
    return this.schedulePackStatus.stream()
        .anyMatch(
            entry ->
                packId.equals(entry.get("packId"))
                    && ((Number) entry.get("version")).intValue() == version);
  }

  // --- Project template pack ---

  public List<Map<String, Object>> getProjectTemplatePackStatus() {
    return projectTemplatePackStatus;
  }

  /** Records a project template pack application. Idempotent -- skips if already applied. */
  public void recordProjectTemplatePackApplication(String packId, int version) {
    if (isProjectTemplatePackApplied(packId, version)) {
      return;
    }
    if (this.projectTemplatePackStatus == null) {
      this.projectTemplatePackStatus = new ArrayList<>();
    }
    this.projectTemplatePackStatus.add(newEntry(packId, version));
  }

  /** Returns true if the given project template pack (specific version) has been applied. */
  public boolean isProjectTemplatePackApplied(String packId, int version) {
    if (this.projectTemplatePackStatus == null) {
      return false;
    }
    return this.projectTemplatePackStatus.stream()
        .anyMatch(
            entry ->
                packId.equals(entry.get("packId"))
                    && ((Number) entry.get("version")).intValue() == version);
  }
}
