package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/**
 * Expense configuration settings group (Wave 3.4 embeddable refactor). Holds the firm-wide default
 * expense markup percent applied when converting billable expenses into invoice line items ({@code
 * ExpenseService}, {@code UnbilledTimeService}, {@code InvoiceCreationService}).
 *
 * <p>Single-field group: extracted (rather than left top-level) for consistency with the wave-3
 * embeddable grouping. The single column ({@code default_expense_markup_percent} numeric(5,2)) is
 * nullable.
 *
 * <p>Persisted inline on the {@code org_settings} table via {@code @Embedded} +
 * {@code @AttributeOverride} on {@link OrgSettings}. Column name, type, and nullability are
 * UNCHANGED from when this field lived directly on the entity (zero schema change — see {@code
 * OrgSettingsSchemaSnapshotTest}); the {@code precision = 5, scale = 2} and the entity-level
 * {@code @DecimalMin} guard are reproduced exactly. (Request-level range validation — non-negative
 * / ≤ 999.99 — lives on {@code UpdateExpenseSettingsRequest}.)
 *
 * <p>Setter here intentionally does NOT bump {@code OrgSettings.updatedAt}; Hibernate dirty-checks
 * the embedded column and persists changes regardless.
 */
@Embeddable
public class ExpenseSettings {

  @DecimalMin(value = "0.00", message = "Default expense markup percent must be non-negative")
  @Column(name = "default_expense_markup_percent", precision = 5, scale = 2)
  private BigDecimal defaultExpenseMarkupPercent;

  protected ExpenseSettings() {}

  public BigDecimal getDefaultExpenseMarkupPercent() {
    return defaultExpenseMarkupPercent;
  }

  public void setDefaultExpenseMarkupPercent(BigDecimal defaultExpenseMarkupPercent) {
    this.defaultExpenseMarkupPercent = defaultExpenseMarkupPercent;
  }
}
