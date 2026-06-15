import { z } from "zod";

export const riskCalibrationEnum = z.enum(["CONSERVATIVE", "MODERATE", "AGGRESSIVE"]);

export const preferredModelEnum = z.enum(["claude-sonnet-4-6", "claude-opus-4-6"]);

export const aiProfileSchema = z.object({
  practiceAreas: z.array(z.string().min(1)).min(1, "At least one practice area is required"),
  jurisdiction: z.string().min(1, "Jurisdiction is required"),
  riskCalibration: riskCalibrationEnum,
  // The form coerces an empty textarea to `null` (`value || null`) and the backend column
  // is nullable, so the schema must accept `null` here — otherwise the server action's
  // re-validation rejects the payload and silently drops the save (no PUT). Keep the
  // .max(2000) cap intact. (AIVERIFY-010)
  houseStyleNotes: z.string().max(2000).nullable().optional().or(z.literal("")),
  ficaRequirements: z
    .object({
      enhancedDueDiligence: z.boolean().optional(),
      pepScreening: z.boolean().optional(),
      sourceOfFundsRequired: z.boolean().optional(),
    })
    .optional(),
  feeEstimationNotes: z.string().max(2000).nullable().optional().or(z.literal("")),
  preferredModel: preferredModelEnum,
  // A cleared budget arrives as undefined/null/NaN/"" from the number field — all mean "no cap".
  // Normalise those to undefined BEFORE validating so an emptied field never trips a silent
  // "expected number, received NaN" failure that aborts the whole form submit (AIVERIFY-010).
  monthlyBudgetCents: z.preprocess((value) => {
    if (value === undefined || value === null || value === "") return undefined;
    const n = typeof value === "string" ? Number(value) : value;
    return typeof n === "number" && Number.isFinite(n) ? n : undefined;
  }, z.number().int().min(0, "Budget must be zero or positive").optional()),
  coldStartCompleted: z.boolean().optional(),
});

export type AiProfileFormData = z.infer<typeof aiProfileSchema>;
