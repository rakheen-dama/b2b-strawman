import { z } from "zod";

export const generateStatementSchema = z
  .object({
    periodStart: z.string().min(1, "Period start is required"),
    periodEnd: z.string().min(1, "Period end is required"),
    templateId: z.string().uuid().optional().or(z.literal("")),
  })
  .refine(
    (d) => !d.periodEnd || !d.periodStart || d.periodEnd >= d.periodStart,
    {
      path: ["periodEnd"],
      message: "Period end must be on or after period start",
    }
  );

export type GenerateStatementFormData = z.infer<typeof generateStatementSchema>;
