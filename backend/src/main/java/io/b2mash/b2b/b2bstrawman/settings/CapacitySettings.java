package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/**
 * Capacity-planning settings group (Wave 3.4 embeddable refactor). Holds the firm-wide default
 * weekly capacity hours used by {@code CapacityService} as the per-member fallback when no member
 * override is set.
 *
 * <p>Single-field group: extracted (rather than left top-level) for consistency with the wave-3
 * embeddable grouping so capacity configuration has a stable home as more capacity knobs are added.
 * The single column ({@code default_weekly_capacity_hours} numeric(5,2)) is nullable.
 *
 * <p>Persisted inline on the {@code org_settings} table via {@code @Embedded} +
 * {@code @AttributeOverride} on {@link OrgSettings}. Column name, type, and nullability are
 * UNCHANGED from when this field lived directly on the entity (zero schema change — see {@code
 * OrgSettingsSchemaSnapshotTest}); the {@code precision = 5, scale = 2} is reproduced exactly.
 *
 * <p>Setter here intentionally does NOT bump {@code OrgSettings.updatedAt}; Hibernate dirty-checks
 * the embedded column and persists changes regardless.
 */
@Embeddable
public class CapacitySettings {

  @Column(name = "default_weekly_capacity_hours", precision = 5, scale = 2)
  private BigDecimal defaultWeeklyCapacityHours;

  protected CapacitySettings() {}

  public BigDecimal getDefaultWeeklyCapacityHours() {
    return defaultWeeklyCapacityHours;
  }

  public void setDefaultWeeklyCapacityHours(BigDecimal defaultWeeklyCapacityHours) {
    this.defaultWeeklyCapacityHours = defaultWeeklyCapacityHours;
  }
}
