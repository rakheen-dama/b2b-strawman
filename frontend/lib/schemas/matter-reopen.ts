import { z } from "zod";

export const reopenMatterSchema = z.object({
  notes: z
    .string()
    .trim()
    .min(10, "Reopen notes must be at least 10 characters")
    .max(5000, "Notes cannot exceed 5000 characters"),
});

export type ReopenMatterFormData = z.infer<typeof reopenMatterSchema>;
