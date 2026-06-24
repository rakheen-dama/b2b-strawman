import { z } from "zod";

const feeModelEnum = z.enum(["FIXED", "HOURLY", "RETAINER", "CONTINGENCY"]);

/**
 * Schema for the "new proposal" dialog on the deal-detail Proposals panel.
 * Distinct from `lib/schemas/proposal.ts` (the standalone proposal-builder form):
 * this is the lightweight deal-scoped create form — title + fee model + an
 * optional string amount (only meaningful for FIXED / RETAINER fee models).
 */
export const createDealProposalSchema = z.object({
  title: z.string().min(1, "Title is required").max(200, "Title must be 200 characters or fewer"),
  feeModel: feeModelEnum,
  amount: z.string().optional(),
});

export type CreateDealProposalFormData = z.infer<typeof createDealProposalSchema>;
