package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Operational request-handling settings group (Wave 3.5 embeddable refactor). Holds the three
 * scheduler-driven compliance knobs that are operational tunables rather than POPIA-officer
 * identity: the data-subject-request response deadline (days), the information-request reminder
 * cadence (days), and the customer dormancy threshold (days) above which an inactive customer is
 * flagged dormant.
 *
 * <p>These are intentionally split out from {@link DataProtectionSettings} (jurisdiction /
 * retention / information-officer panel): they are consumed by {@code RequestReminderScheduler},
 * {@code CustomerLifecycleService}, and the DSAR deadline resolver, and are mutated through the
 * separate {@code updateComplianceSettings} endpoint — not the atomic data-protection panel update.
 *
 * <p>Persisted inline on the {@code org_settings} table via {@code @Embedded} +
 * {@code @AttributeOverride} on {@link OrgSettings}. Column names, types, and nullability are
 * UNCHANGED from when these fields lived directly on the entity (zero schema change — see {@code
 * OrgSettingsSchemaSnapshotTest}). All three columns are nullable, so when they are all NULL
 * Hibernate materialises a NULL embedded object on reload — the lazy-fallback {@link
 * OrgSettings#getDataRequest()} getter is what keeps callers NPE-safe (see {@code
 * OrgSettingsEmbeddableNullReloadTest}).
 *
 * <p>Field-level setters here intentionally do NOT bump {@code OrgSettings.updatedAt}; the
 * embeddable has no reference to the owning entity's timestamp. Hibernate dirty-checks the embedded
 * columns and persists changes regardless.
 */
@Embeddable
public class DataRequestSettings {

  @Column(name = "data_request_deadline_days")
  private Integer dataRequestDeadlineDays;

  @Column(name = "default_request_reminder_days")
  private Integer defaultRequestReminderDays;

  @Column(name = "dormancy_threshold_days")
  private Integer dormancyThresholdDays;

  protected DataRequestSettings() {}

  public Integer getDataRequestDeadlineDays() {
    return dataRequestDeadlineDays;
  }

  public void setDataRequestDeadlineDays(Integer dataRequestDeadlineDays) {
    this.dataRequestDeadlineDays = dataRequestDeadlineDays;
  }

  public Integer getDefaultRequestReminderDays() {
    return defaultRequestReminderDays;
  }

  public void setDefaultRequestReminderDays(Integer defaultRequestReminderDays) {
    this.defaultRequestReminderDays = defaultRequestReminderDays;
  }

  public Integer getDormancyThresholdDays() {
    return dormancyThresholdDays;
  }

  public void setDormancyThresholdDays(Integer dormancyThresholdDays) {
    this.dormancyThresholdDays = dormancyThresholdDays;
  }
}
