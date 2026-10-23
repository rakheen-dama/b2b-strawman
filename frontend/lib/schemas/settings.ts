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
