package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Tax configuration settings group (Wave 3.4 embeddable refactor). Holds the firm's tax-inclusive
 * pricing toggle and the tax-registration identity fields (number, its display label, and the
 * line-item tax label) applied to invoices, quotes, and generated documents.
 *
 * <p>Persisted inline on the {@code org_settings} table via {@code @Embedded} +
 * {@code @AttributeOverride} on {@link OrgSettings}. Column names, types, and nullability are
 * UNCHANGED from when these fields lived directly on the entity (zero schema change — see {@code
 * OrgSettingsSchemaSnapshotTest}). {@code tax_inclusive} is NOT NULL, so it is modelled as a
 * primitive {@code boolean} (defaults to {@code false}); the three label/number columns are
 * nullable.
 *
 * <p>Field-level setters here intentionally do NOT bump {@code OrgSettings.updatedAt}; the
 * embeddable has no reference to the owning entity's timestamp. Hibernate dirty-checks the embedded
 * columns and persists changes regardless. The {@link #updateTaxSettings} domain mutator preserves
 * the same atomic four-field update semantics the entity method used to provide.
 */
@Embeddable
public class TaxSettings {

  @Column(name = "tax_inclusive", nullable = false)
  private boolean taxInclusive;

  @Column(name = "tax_registration_number", length = 50)
  private String taxRegistrationNumber;

  @Column(name = "tax_registration_label", length = 30)
  private String taxRegistrationLabel;

  @Column(name = "tax_label", length = 20)
  private String taxLabel;

  protected TaxSettings() {}

  public boolean isTaxInclusive() {
    return taxInclusive;
  }

  public void setTaxInclusive(boolean taxInclusive) {
    this.taxInclusive = taxInclusive;
  }

  public String getTaxRegistrationNumber() {
    return taxRegistrationNumber;
  }

  public void setTaxRegistrationNumber(String taxRegistrationNumber) {
    this.taxRegistrationNumber = taxRegistrationNumber;
  }

  public String getTaxRegistrationLabel() {
    return taxRegistrationLabel;
  }

  public void setTaxRegistrationLabel(String taxRegistrationLabel) {
    this.taxRegistrationLabel = taxRegistrationLabel;
  }

  public String getTaxLabel() {
    return taxLabel;
  }

  public void setTaxLabel(String taxLabel) {
    this.taxLabel = taxLabel;
  }

  /** Updates all four tax configuration fields atomically. */
  public void updateTaxSettings(
      String taxRegistrationNumber,
      String taxRegistrationLabel,
      String taxLabel,
      boolean taxInclusive) {
    this.taxRegistrationNumber = taxRegistrationNumber;
    this.taxRegistrationLabel = taxRegistrationLabel;
    this.taxLabel = taxLabel;
    this.taxInclusive = taxInclusive;
  }
}
