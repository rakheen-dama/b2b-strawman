import { z } from "zod";

export const riskCalibrationEnum = z.enum(["CONSERVATIVE", "MODERATE", "AGGRESSIVE"]);

export const preferredModelEnum = z.enum(["claude-sonnet-4-6", "claude-opus-4-6"]);

export const aiProfileSchema = z.object({
  practiceAreas: z.array(z.string().min(1)).min(1, "At least one practice area is required"),
  jurisdiction: z.string().min(1, "Jurisdiction is required"),
  riskCalibration: riskCalibrationEnum,
  houseStyleNotes: z.string().max(2000).optional().or(z.literal("")),
  ficaRequirements: z
    .object({
      enhancedDueDiligence: z.boolean().optional(),
      pepScreening: z.boolean().optional(),
      sourceOfFundsRequired: z.boolean().optional(),
    })
    .optional(),
  feeEstimationNotes: z.string().max(2000).optional().or(z.literal("")),
  preferredModel: preferredModelEnum,
  monthlyBudgetCents: z.coerce.number().int().min(0, "Budget must be zero or positive").optional(),
  coldStartCompleted: z.boolean().optional(),
});

export type AiProfileFormData = z.infer<typeof aiProfileSchema>;
