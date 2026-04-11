import { z } from "zod";

const feeModelEnum = z.enum(["FIXED", "HOURLY", "RETAINER", "CONTINGENCY"]);

export const createProposalSchema = z.object({
  title: z.string().min(1, "Title is required").max(200, "Title must be 200 characters or fewer"),
  customerId: z.string().min(1, "Customer is required"),
  feeModel: feeModelEnum,
  fixedFeeAmount: z.number().positive("Amount must be greater than 0").optional(),
  fixedFeeCurrency: z.string().optional(),
  hourlyRateNote: z.string().max(500).optional().or(z.literal("")),
  retainerAmount: z.number().positive("Amount must be greater than 0").optional(),
  retainerCurrency: z.string().optional(),
  retainerHoursIncluded: z.number().min(0).optional(),
  // Contingency fee fields (Contingency Fees Act 66 of 1997 — 25% statutory cap).
  contingencyPercent: z
    .number()
    .min(0, "Percent must be 0 or more")
    .max(25, "Contingency fee capped at 25% per Contingency Fees Act 66 of 1997")
    .optional(),
  contingencyCapPercent: z
    .number()
    .min(0, "Cap must be 0 or more")
    .max(25, "Contingency fee capped at 25% per Contingency Fees Act 66 of 1997")
    .optional(),
  contingencyDescription: z
    .string()
    .max(500, "Description must be 500 characters or fewer")
    .optional()
    .or(z.literal("")),
  expiresAt: z.string().optional().or(z.literal("")),
});

export type CreateProposalFormData = z.infer<typeof createProposalSchema>;
