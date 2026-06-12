package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Billing-run configuration settings group (Wave 3.4 embeddable refactor). Holds the batch-billing
 * async threshold (member count above which a billing run is processed asynchronously), the per-run
 * email rate limit, and the optional default currency override applied to generated billing runs.
 *
 * <p>Note: the org's BASE currency ({@code default_currency}, NOT NULL) remains top-level on {@link
 * OrgSettings} as org identity — it is used pervasively for invoice/quote/expense currency
 * resolution and accounting-sync currency matching, and has its own {@code updateCurrency()}
 * timestamp-bumping mutator. Only {@code default_billing_run_currency} (a billing-run-specific,
 * nullable override) belongs to this group.
 *
 * <p>Persisted inline on the {@code org_settings} table via {@code @Embedded} +
 * {@code @AttributeOverride} on {@link OrgSettings}. Column names, types, and nullability are
 * UNCHANGED from when these fields lived directly on the entity (zero schema change — see {@code
 * OrgSettingsSchemaSnapshotTest}). {@code billing_batch_async_threshold} and {@code
 * billing_email_rate_limit} are NOT NULL; their constructor defaults (50 and 5) are reproduced via
 * field initialisers so a fresh embeddable persists the same non-null values the entity constructor
 * used to set.
 *
 * <p>Field-level setters here intentionally do NOT bump {@code OrgSettings.updatedAt}; Hibernate
 * dirty-checks the embedded columns and persists changes regardless.
 */
@Embeddable
public class BillingSettings {

  @Column(name = "billing_batch_async_threshold")
  private Integer billingBatchAsyncThreshold = 50;

  @Column(name = "billing_email_rate_limit")
  private Integer billingEmailRateLimit = 5;

  @Column(name = "default_billing_run_currency", length = 3)
  private String defaultBillingRunCurrency;

  protected BillingSettings() {}

  public Integer getBillingBatchAsyncThreshold() {
    return billingBatchAsyncThreshold;
  }

  public void setBillingBatchAsyncThreshold(Integer billingBatchAsyncThreshold) {
    this.billingBatchAsyncThreshold = billingBatchAsyncThreshold;
  }

  public Integer getBillingEmailRateLimit() {
    return billingEmailRateLimit;
  }

  public void setBillingEmailRateLimit(Integer billingEmailRateLimit) {
    this.billingEmailRateLimit = billingEmailRateLimit;
  }

  public String getDefaultBillingRunCurrency() {
    return defaultBillingRunCurrency;
  }

  public void setDefaultBillingRunCurrency(String defaultBillingRunCurrency) {
    this.defaultBillingRunCurrency = defaultBillingRunCurrency;
  }
}
