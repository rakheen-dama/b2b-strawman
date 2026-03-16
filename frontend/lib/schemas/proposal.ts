import { z } from "zod";

const feeModelEnum = z.enum(["FIXED", "HOURLY", "RETAINER"]);

export const createProposalSchema = z.object({
  title: z
    .string()
    .min(1, "Title is required")
    .max(200, "Title must be 200 characters or fewer"),
  customerId: z.string().min(1, "Customer is required"),
  feeModel: feeModelEnum,
  fixedFeeAmount: z.number().positive("Amount must be greater than 0").optional(),
  fixedFeeCurrency: z.string().optional(),
  hourlyRateNote: z.string().max(500).optional().or(z.literal("")),
  retainerAmount: z.number().positive("Amount must be greater than 0").optional(),
  retainerCurrency: z.string().optional(),
  retainerHoursIncluded: z.number().min(0).optional(),
  expiresAt: z.string().optional().or(z.literal("")),
});

export type CreateProposalFormData = z.infer<typeof createProposalSchema>;
