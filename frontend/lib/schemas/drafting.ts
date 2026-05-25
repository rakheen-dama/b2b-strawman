import { z } from "zod";

export const confidenceEnum = z.enum(["HIGH", "MEDIUM", "LOW", "UNDETERMINED"]);

export const variableFillSchema = z.object({
  variableName: z.string().min(1, "Variable name is required"),
  value: z.string().nullable(),
  source: z.string(),
  confidence: confidenceEnum,
});

export const draftingVariableFillsSchema = z.object({
  variableFills: z.array(variableFillSchema),
});

export type VariableFillFormData = z.infer<typeof variableFillSchema>;
export type DraftingVariableFillsFormData = z.infer<typeof draftingVariableFillsSchema>;
