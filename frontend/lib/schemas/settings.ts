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

/**
 * The seeded firm matter retention period in years (backend
 * `DEFAULT_LEGAL_MATTER_RETENTION_YEARS`); `GET /api/settings` returns it when the firm has not
 * changed the setting.
 */
export const DEFAULT_MATTER_RETENTION_YEARS = 5;

/** The matter retention period is owner-only on the backend (data-protection settings PATCH). */
export const MATTER_RETENTION_OWNER_ONLY_MESSAGE =
  "Only the firm owner can change the matter retention period.";
