import { z } from "zod";

const isoDateString = (label: string) =>
  z
    .string()
    .min(1, `${label} is required`)
    .refine(
      (value) =>
        /^\d{4}-\d{2}-\d{2}$/.test(value) &&
        !Number.isNaN(Date.parse(`${value}T00:00:00Z`)),
      "Invalid date"
    );

export const generateStatementSchema = z
  .object({
    periodStart: isoDateString("Period start"),
    periodEnd: isoDateString("Period end"),
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
