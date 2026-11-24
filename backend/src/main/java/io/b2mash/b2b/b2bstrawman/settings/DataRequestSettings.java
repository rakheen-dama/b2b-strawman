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
 * OrgSettingsSchemaSnapshotTest}). The three original columns are nullable; since tenant V34 (Phase
 * 93 655A) the group also holds the three {@code NOT NULL} FICA review intervals, so it no longer
 * materialises as a NULL embedded on reload — the lazy-fallback {@link
 * OrgSettings#getDataRequest()} getter stays as a guard (see {@code
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

  /**
   * FICA review intervals by risk rating, in months (Phase 93 655A, §17.2.5, FD-7): the firm's RMCP
   * frequency, not law. {@code NOT NULL DEFAULT 36/24/12} with CHECK 1–120 (tenant V34), so they
   * are {@code int} with the same defaults here — a new {@code org_settings} row must never insert
   * NULL into them. Edited through {@link #setFicaReviewMonths} (655B), which guards the same 1–120
   * range as {@code chk_org_settings_fica_review_months}.
   */
  @Column(name = "fica_review_months_low", nullable = false)
  private int ficaReviewMonthsLow = DEFAULT_FICA_REVIEW_MONTHS_LOW;

  @Column(name = "fica_review_months_medium", nullable = false)
  private int ficaReviewMonthsMedium = DEFAULT_FICA_REVIEW_MONTHS_MEDIUM;

  @Column(name = "fica_review_months_high", nullable = false)
  private int ficaReviewMonthsHigh = DEFAULT_FICA_REVIEW_MONTHS_HIGH;

  public static final int DEFAULT_FICA_REVIEW_MONTHS_LOW = 36;
  public static final int DEFAULT_FICA_REVIEW_MONTHS_MEDIUM = 24;
  public static final int DEFAULT_FICA_REVIEW_MONTHS_HIGH = 12;

  /** The CHECK range of the review intervals ({@code chk_org_settings_fica_review_months}). */
  public static final int MIN_FICA_REVIEW_MONTHS = 1;

  public static final int MAX_FICA_REVIEW_MONTHS = 120;

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

  public int getFicaReviewMonthsLow() {
    return ficaReviewMonthsLow;
  }

  public int getFicaReviewMonthsMedium() {
    return ficaReviewMonthsMedium;
  }

  public int getFicaReviewMonthsHigh() {
    return ficaReviewMonthsHigh;
  }

  /**
   * Sets the FICA review intervals (Phase 93 655B, FD-7); a null argument keeps that interval.
   * Validates all three before changing any. Affects attestations created afterwards only ({@code
   * next_review_date} is stored at take-on).
   *
   * @throws IllegalArgumentException when a non-null interval is outside 1–120 months
   */
  public void setFicaReviewMonths(Integer low, Integer medium, Integer high) {
    requireReviewMonths("ficaReviewMonthsLow", low);
    requireReviewMonths("ficaReviewMonthsMedium", medium);
    requireReviewMonths("ficaReviewMonthsHigh", high);
    if (low != null) {
      this.ficaReviewMonthsLow = low;
    }
    if (medium != null) {
      this.ficaReviewMonthsMedium = medium;
    }
    if (high != null) {
      this.ficaReviewMonthsHigh = high;
    }
  }

  private static void requireReviewMonths(String name, Integer months) {
    if (months != null && (months < MIN_FICA_REVIEW_MONTHS || months > MAX_FICA_REVIEW_MONTHS)) {
      throw new IllegalArgumentException(
          name
              + " must be between "
              + MIN_FICA_REVIEW_MONTHS
              + " and "
              + MAX_FICA_REVIEW_MONTHS
              + " months, was "
              + months);
    }
  }
}
