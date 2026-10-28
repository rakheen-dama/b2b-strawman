import { z } from "zod";

/**
 * Firm default payment terms in days (Phase 88 U3, ADR-347): a fee note approved without a due
 * date gets issue date + these days. Mirrors the backend `UpdateBatchBillingSettingsRequest`
 * `@Min(0) @Max(365)` and the V4 §2 CHECK constraint.
 */
export const paymentTermsDaysSchema = z
  .number({ error: "Enter the number of days." })
  .int("Payment terms must be a whole number of days.")
  .min(0, "Payment terms must be between 0 and 365 days.")
  .max(365, "Payment terms must be between 0 and 365 days.");

/**
 * Matter retention period in whole years (Phase 88 U17, ADR-354): a closed matter's scheduled
 * deletion date is the close date plus these years, stamped at close. Mirrors the backend
 * `DataProtectionSettingsRequest.legalMatterRetentionYears` `@Min(1) @Max(30)`.
 */
export const matterRetentionYearsSchema = z
  .number({ error: "Enter the number of years." })
  .int("Retention period must be a whole number of years.")
  .min(1, "Retention period must be between 1 and 30 years.")
  .max(30, "Retention period must be between 1 and 30 years.");
