import { z } from "zod";

export const closureReasonEnum = z.enum(
  ["CONCLUDED", "CLIENT_TERMINATED", "REFERRED_OUT", "OTHER"],
  { message: "Closure reason is required" }
);

export const closeMatterSchema = z
  .object({
    reason: closureReasonEnum,
    notes: z.string().trim().max(5000).optional(),
    generateClosureLetter: z.boolean(),
    override: z.boolean(),
    overrideJustification: z.string().trim().max(5000).optional(),
  })
  .refine(
    (data) => !data.override || (data.overrideJustification?.trim().length ?? 0) >= 20,
    {
      message: "Override justification must be at least 20 characters",
      path: ["overrideJustification"],
    }
  );

export type CloseMatterFormData = z.infer<typeof closeMatterSchema>;
