import { z } from "zod";

function isValidIsoDate(value: string): boolean {
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) return false;
  const date = new Date(Date.UTC(year, month - 1, day));
  return (
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
  );
}

const isoDateString = (label: string) =>
  z
    .string()
    .min(1, `${label} is required`)
    .refine(
      (value) => /^\d{4}-\d{2}-\d{2}$/.test(value) && isValidIsoDate(value),
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
